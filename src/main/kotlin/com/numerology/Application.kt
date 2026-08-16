package com.numerology

import com.numerology.config.AppConfig
import com.numerology.db.DatabaseFactory
import com.numerology.fcm.FcmClient
import com.numerology.google.GoogleAuth
import com.numerology.google.GooglePlayClient
import com.numerology.google.GooglePubSubPuller
import com.numerology.llm.OpenAiClient
import com.numerology.plugins.configureSecurity
import com.numerology.plugins.configureSerialization
import com.numerology.plugins.configureStatusPages
import com.numerology.repositories.CompanionRepository
import com.numerology.repositories.ComputedNumbersRepository
import com.numerology.repositories.DailyInsightRepository
import com.numerology.repositories.PushTokenRepository
import com.numerology.repositories.RemoteConfigRepository
import com.numerology.repositories.SubscriptionRepository
import com.numerology.repositories.UserRepository
import com.numerology.repositories.WebhookEventRepository
import com.numerology.routes.authRoutes
import com.numerology.routes.companionRoutes
import com.numerology.routes.configRoutes
import com.numerology.routes.insightRoutes
import com.numerology.routes.profileRoutes
import com.numerology.routes.pushRoutes
import com.numerology.routes.referenceRoutes
import com.numerology.routes.subscriptionRoutes
import com.numerology.scheduler.NightlyBatchJob
import com.numerology.scheduler.schedulePeriodic
import com.numerology.security.EncryptionService
import com.numerology.security.JwtService
import com.numerology.services.CompanionService
import com.numerology.services.FallbackBank
import com.numerology.services.InsightService
import com.numerology.services.ProfileService
import com.numerology.services.PushService
import com.numerology.services.RemoteConfigService
import com.numerology.services.SubscriptionService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.time.ZoneId

private val logger = LoggerFactory.getLogger("Application")

fun main() {
    val config = AppConfig.fromEnv()
    DatabaseFactory.init(config)

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: AppConfig) {
    configureSerialization()
    configureStatusPages()
    install(CallLogging) { level = Level.INFO }
    install(DefaultHeaders)

    // ---- Wiring (no DI framework needed at this size) ----
    val encryptionService = EncryptionService(config.encryptionKeyBase64)
    val jwtService = JwtService(config)
    configureSecurity(jwtService)

    val userRepository = UserRepository(encryptionService)
    val companionRepository = CompanionRepository(encryptionService)
    val computedNumbersRepository = ComputedNumbersRepository()
    val dailyInsightRepository = DailyInsightRepository()
    val subscriptionRepository = SubscriptionRepository()
    val webhookEventRepository = WebhookEventRepository()
    val pushTokenRepository = PushTokenRepository()
    val remoteConfigRepository = RemoteConfigRepository()

    val openAiClient = OpenAiClient(config)
    val fallbackBank = FallbackBank()

    val googlePlayAuth = GoogleAuth(config.googleServiceAccountJsonPath)
    val googlePlayClient = GooglePlayClient(googlePlayAuth, config.googlePlayPackageName)
    val fcmClient = FcmClient(config.firebaseServiceAccountJsonPath, config.googleCloudProjectId)

    // Fallback zone for users who haven't set a profile `timezone` yet — see InsightService/NightlyBatchJob.
    val zoneId = runCatching { ZoneId.of(config.schedulerTimezone) }.getOrElse {
        logger.warn("Invalid SCHEDULER_TIMEZONE '${config.schedulerTimezone}', falling back to UTC")
        ZoneId.of("UTC")
    }

    val profileService = ProfileService(userRepository, computedNumbersRepository)
    val companionService = CompanionService(companionRepository)
    val insightService = InsightService(userRepository, computedNumbersRepository, dailyInsightRepository, openAiClient, fallbackBank, zoneId)
    val subscriptionService = SubscriptionService(subscriptionRepository, webhookEventRepository, googlePlayClient)
    val pushService = PushService(pushTokenRepository, dailyInsightRepository, fcmClient)
    val remoteConfigService = RemoteConfigService(remoteConfigRepository)

    routing {
        authRoutes(userRepository, jwtService)
        profileRoutes(profileService, userRepository)
        companionRoutes(companionService, userRepository)
        insightRoutes(insightService, userRepository)
        referenceRoutes()
        subscriptionRoutes(subscriptionService, userRepository)
        pushRoutes(pushService, userRepository)
        configRoutes(remoteConfigService, config.adminToken)
    }

    // ---- Background jobs ----
    // Each user's own local time (profile `timezone`, falling back to this server
    // default) decides when they're due, not a single global hour — see NightlyBatchJob.
    val nightlyBatchJob = NightlyBatchJob(userRepository, insightService, zoneId, config.nightlyBatchHour)
    val sweepIntervalMs = 30 * 60_000L
    schedulePeriodic("nightly-insight-sweep", intervalMs = sweepIntervalMs) {
        nightlyBatchJob.run()
    }
    schedulePeriodic("daily-push-sweep", intervalMs = sweepIntervalMs) {
        pushService.runPushSweep(zoneId, config.dailyPushHour)
    }

    val pubSubPuller = GooglePubSubPuller(googlePlayAuth, config.googlePubSubSubscription) { messageId, notification ->
        subscriptionService.handleGoogleNotification(messageId, notification)
    }
    if (pubSubPuller.isConfigured()) {
        schedulePeriodic("google-pubsub-pull", intervalMs = 60_000) { pubSubPuller.pullOnce() }
    } else {
        logger.info("Google Pub/Sub pull worker disabled (GOOGLE_PUBSUB_SUBSCRIPTION or service account not set)")
    }

    logger.info("Numerology backend started on port ${config.port}, scheduler timezone $zoneId")
}
