package com.numerology.scheduler

import com.numerology.repositories.SubscriptionRepository
import com.numerology.repositories.UserRepository
import com.numerology.services.InsightService
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId

private val logger = LoggerFactory.getLogger("NightlyBatchJob")

/**
 * Pre-generates tomorrow's daily_insight for every user with an active (or
 * grace-period) subscription — per §5 "Когда генерировать": this keeps LLM
 * spend proportional to paying users, not to app opens, and means
 * GET /daily-insight at app-open time is just a cache read.
 */
class NightlyBatchJob(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val insightService: InsightService,
    private val zoneId: ZoneId,
) {
    suspend fun run() {
        val targetDate = LocalDate.now(zoneId).plusDays(1)
        val userIds = subscriptionRepository.findActiveSubscriberUserIds()
        logger.info("Nightly batch: generating insights for $targetDate, ${userIds.size} active subscribers")

        var ok = 0
        var skippedNoBirthDate = 0
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
            try {
                insightService.generateAndStore(user, targetDate)
                ok++
            } catch (e: Exception) {
                logger.error("Failed to generate insight for user $userId: ${e.message}", e)
                failed++
            }
        }
        logger.info("Nightly batch done: ok=$ok skipped_no_birth_date=$skippedNoBirthDate failed=$failed")
    }
}
