package com.numerology.services

import com.numerology.fcm.FcmClient
import com.numerology.repositories.DailyInsightRepository
import com.numerology.repositories.PushTokenRepository
import com.numerology.repositories.UserRepository
import com.numerology.scheduler.resolveUserZone
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

private val logger = LoggerFactory.getLogger("PushService")

class PushService(
    private val pushTokenRepository: PushTokenRepository,
    private val dailyInsightRepository: DailyInsightRepository,
    private val userRepository: UserRepository,
    private val fcmClient: FcmClient,
) {
    suspend fun registerToken(userId: UUID, platform: String, token: String) {
        pushTokenRepository.register(userId, platform, token)
    }

    /**
     * 30-minute sweep: for each user with a push token, once their local
     * clock hits DAILY_PUSH_HOUR, push today's (their local today's) headline
     * if it's ready and hasn't been pushed yet. `pushed_at` on daily_insights
     * makes this idempotent across repeated sweeps within the hour window.
     */
    suspend fun runPushSweep(defaultZoneId: ZoneId, pushHour: Int) {
        if (!fcmClient.isConfigured()) {
            logger.warn("FCM not configured (FIREBASE_SERVICE_ACCOUNT_JSON / GOOGLE_CLOUD_PROJECT_ID missing) — skipping push sweep")
            return
        }

        val userIds = pushTokenRepository.allUserIdsWithTokens()
        var sent = 0
        var failed = 0
        var notDue = 0
        var notReady = 0

        for (userId in userIds) {
            val user = userRepository.findById(userId) ?: continue
            val nowLocal = ZonedDateTime.now(resolveUserZone(user, defaultZoneId))
            if (nowLocal.hour != pushHour) {
                notDue++
                continue
            }

            val insight = dailyInsightRepository.findByUserAndDate(userId, nowLocal.toLocalDate())
            if (insight == null || insight.pushedAt != null) {
                notReady++
                continue
            }

            var anySent = false
            for (t in pushTokenRepository.tokensForUser(userId)) {
                when (fcmClient.send(t.token, title = insight.headline, body = insight.greeting ?: insight.headline)) {
                    FcmClient.SendResult.OK -> {
                        sent++
                        anySent = true
                    }
                    FcmClient.SendResult.INVALID_TOKEN -> {
                        pushTokenRepository.deleteToken(t.token)
                        failed++
                    }
                    else -> failed++
                }
            }
            if (anySent) dailyInsightRepository.markPushed(userId, nowLocal.toLocalDate())
        }
        logger.info("Push sweep done: sent=$sent failed=$failed not_due=$notDue not_ready=$notReady")
    }
}
