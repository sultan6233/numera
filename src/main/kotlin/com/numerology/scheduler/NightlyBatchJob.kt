package com.numerology.scheduler

import com.numerology.repositories.DailyInsightRepository
import com.numerology.repositories.SubscriptionRepository
import com.numerology.repositories.UserRecord
import com.numerology.repositories.UserRepository
import com.numerology.services.InsightService
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZonedDateTime

private val logger = LoggerFactory.getLogger("NightlyBatchJob")

/**
 * Pre-generates tomorrow's daily_insight for every active subscriber, timed
 * to each user's own local clock (via their profile's `timezone`, falling
 * back to SCHEDULER_TIMEZONE if unset/invalid) rather than one server-wide
 * hour — the app has users across many time zones. Per §5 "Когда
 * генерировать": keeps LLM spend proportional to paying subscribers, not app
 * opens, so GET /daily-insight at app-open time is normally just a cache read.
 *
 * Driven by a 30-minute sweep (see Application.kt), so a user's target hour
 * gets checked up to twice; the daily_insights (user_id, date) uniqueness
 * plus the "already generated" skip below make re-checking a no-op.
 */
class NightlyBatchJob(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val dailyInsightRepository: DailyInsightRepository,
    private val insightService: InsightService,
    private val defaultZoneId: ZoneId,
    private val batchHour: Int,
) {
    suspend fun run() {
        val userIds = subscriptionRepository.findActiveSubscriberUserIds()

        var ok = 0
        var skippedNoBirthDate = 0
        var skippedNotDue = 0
        var skippedAlreadyDone = 0
        var failed = 0

        for (userId in userIds) {
            val user = userRepository.findById(userId)
            if (user == null) {
                failed++
                continue
            }
            if (user.birthDate == null) {
                skippedNoBirthDate++
                continue
            }

            val nowLocal = ZonedDateTime.now(resolveUserZone(user, defaultZoneId))
            if (nowLocal.hour != batchHour) {
                skippedNotDue++
                continue
            }

            val targetDate = nowLocal.toLocalDate().plusDays(1)
            if (dailyInsightRepository.findByUserAndDate(userId, targetDate) != null) {
                skippedAlreadyDone++
                continue
            }

            try {
                insightService.generateAndStore(user, targetDate)
                ok++
            } catch (e: Exception) {
                logger.error("Failed to generate insight for user $userId: ${e.message}", e)
                failed++
            }
        }
        logger.info(
            "Nightly sweep done: ok=$ok skipped_no_birth_date=$skippedNoBirthDate " +
                "skipped_not_due=$skippedNotDue skipped_already_done=$skippedAlreadyDone failed=$failed"
        )
    }
}

/** A user's own local time zone if they've set one and it parses, else the server default. */
internal fun resolveUserZone(user: UserRecord, default: ZoneId): ZoneId =
    user.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: default
