package com.numerology.scheduler

import com.numerology.repositories.UserRecord
import com.numerology.repositories.UserRepository
import com.numerology.services.InsightService
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

private val logger = LoggerFactory.getLogger("NightlyBatchJob")

/**
 * Pre-generates tomorrow's daily_insight for every active subscriber, timed
 * to each user's own local clock rather than one server-wide hour (users
 * span many time zones). Per §5 "Когда генерировать": keeps LLM spend
 * proportional to paying subscribers, not app opens, so GET /daily-insight
 * at app-open time is normally just a cache read.
 *
 * Who's due right now is resolved in one SQL query
 * (UserRepository.findActiveSubscribersDueForInsight) instead of loading
 * every active subscriber into Kotlin and checking each one here -- that
 * query already excludes users who've been generated for their local
 * tomorrow, so a 30-minute sweep touching the same user's due hour twice is
 * still just one generation.
 */
class NightlyBatchJob(
    private val userRepository: UserRepository,
    private val insightService: InsightService,
    private val defaultZoneId: ZoneId,
    private val batchHour: Int,
) {
    suspend fun run() {
        val defaultOffsetMinutes = defaultZoneId.rules.getOffset(Instant.now()).totalSeconds / 60
        val dueUsers = userRepository.findActiveSubscribersDueForInsight(defaultOffsetMinutes, batchHour)

        var ok = 0
        var failed = 0
        for (user in dueUsers) {
            val targetDate = ZonedDateTime.now(resolveUserZone(user, defaultZoneId)).toLocalDate().plusDays(1)
            try {
                insightService.generateAndStore(user, targetDate)
                ok++
            } catch (e: Exception) {
                logger.error("Failed to generate insight for user ${user.id}: ${e.message}", e)
                failed++
            }
        }
        logger.info("Nightly sweep done: candidates=${dueUsers.size} ok=$ok failed=$failed")
    }
}

/** A user's own local time zone if they've set one and it parses, else the server default. */
internal fun resolveUserZone(user: UserRecord, default: ZoneId): ZoneId =
    user.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: default
