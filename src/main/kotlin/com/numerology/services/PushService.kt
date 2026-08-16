package com.numerology.services

import com.numerology.fcm.FcmClient
import com.numerology.repositories.DailyInsightRepository
import com.numerology.repositories.PushTokenRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

private val logger = LoggerFactory.getLogger("PushService")

class PushService(
    private val pushTokenRepository: PushTokenRepository,
    private val dailyInsightRepository: DailyInsightRepository,
    private val fcmClient: FcmClient,
) {
    suspend fun registerToken(userId: UUID, platform: String, token: String) {
        pushTokenRepository.register(userId, platform, token)
    }

    /**
     * 30-minute sweep: push today's headline (in each user's own local day)
     * once it's ready and their local clock hits DAILY_PUSH_HOUR. Who's due
     * is resolved in one SQL query (DailyInsightRepository.findDueForPush)
     * instead of loading every token holder into Kotlin and checking each
     * one here; `pushed_at` makes repeated sweeps within the hour a no-op.
     */
    suspend fun runPushSweep(defaultZoneId: ZoneId, pushHour: Int) {
        if (!fcmClient.isConfigured()) {
            logger.warn("FCM not configured (FIREBASE_SERVICE_ACCOUNT_JSON / GOOGLE_CLOUD_PROJECT_ID missing) — skipping push sweep")
            return
        }

        val defaultOffsetMinutes = defaultZoneId.rules.getOffset(Instant.now()).totalSeconds / 60
        val dueInsights = dailyInsightRepository.findDueForPush(defaultOffsetMinutes, pushHour)

        var sent = 0
        var failed = 0
        for (insight in dueInsights) {
            var anySent = false
            for (t in pushTokenRepository.tokensForUser(insight.userId)) {
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
            if (anySent) dailyInsightRepository.markPushed(insight.userId, insight.date)
        }
        logger.info("Push sweep done: candidates=${dueInsights.size} sent=$sent failed=$failed")
    }
}
