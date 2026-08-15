package com.numerology.services

import com.numerology.fcm.FcmClient
import com.numerology.repositories.DailyInsightRepository
import com.numerology.repositories.PushTokenRepository
import org.slf4j.LoggerFactory
import java.time.LocalDate
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

    /** Internal cron: once a day, push today's headline to every user who has an insight ready. */
    suspend fun sendDailyPushes(date: LocalDate) {
        if (!fcmClient.isConfigured()) {
            logger.warn("FCM not configured (FIREBASE_SERVICE_ACCOUNT_JSON / GOOGLE_CLOUD_PROJECT_ID missing) — skipping daily push run")
            return
        }
        val insights = dailyInsightRepository.findByDate(date)
        if (insights.isEmpty()) {
            logger.info("No daily_insights for $date yet, nothing to push")
            return
        }
        val tokensByUser = pushTokenRepository.tokensForUsers(insights.map { it.userId })

        var sent = 0
        var failed = 0
        for (insight in insights) {
            val tokens = tokensByUser[insight.userId].orEmpty()
            for (t in tokens) {
                when (fcmClient.send(t.token, title = insight.headline, body = insight.greeting ?: insight.headline)) {
                    FcmClient.SendResult.OK -> sent++
                    FcmClient.SendResult.INVALID_TOKEN -> {
                        pushTokenRepository.deleteToken(t.token)
                        failed++
                    }
                    else -> failed++
                }
            }
        }
        logger.info("Daily push run for $date: sent=$sent failed=$failed users_with_insight=${insights.size}")
    }
}
