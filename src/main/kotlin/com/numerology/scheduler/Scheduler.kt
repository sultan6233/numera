package com.numerology.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val logger = LoggerFactory.getLogger("Scheduler")

/**
 * Minimal cron replacement: no BullMQ/Celery needed for a single nightly job
 * and a single daily push job at this scale (per the spec's own "no AWS-style
 * overengineering for MVP" guidance in §2). Computes the delay to the next
 * occurrence of hour:minute in the given zone, runs the action, repeats.
 */
fun CoroutineScope.scheduleDaily(
    name: String,
    hour: Int,
    minute: Int,
    zoneId: ZoneId,
    action: suspend () -> Unit,
) = launch {
    while (true) {
        val now = ZonedDateTime.now(zoneId)
        var next = now.toLocalDate().atTime(LocalTime.of(hour, minute)).atZone(zoneId)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMs = java.time.Duration.between(now, next).toMillis()
        logger.info("[$name] next run at $next (in ${delayMs / 1000}s)")
        delay(delayMs)
        try {
            logger.info("[$name] starting run for ${LocalDate.now(zoneId)}")
            action()
            logger.info("[$name] run finished")
        } catch (e: Exception) {
            logger.error("[$name] run failed: ${e.message}", e)
        }
    }
}

fun CoroutineScope.schedulePeriodic(
    name: String,
    intervalMs: Long,
    initialDelayMs: Long = intervalMs,
    action: suspend () -> Unit,
) = launch {
    delay(initialDelayMs)
    while (true) {
        try {
            action()
        } catch (e: Exception) {
            logger.error("[$name] run failed: ${e.message}", e)
        }
        delay(intervalMs)
    }
}
