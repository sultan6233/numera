package com.numerology.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Scheduler")

/**
 * Minimal cron replacement: no BullMQ/Celery needed at this scale (per the
 * spec's own "no AWS-style overengineering for MVP" guidance in §2). Runs
 * `action` on a fixed interval; callers that need "once a day at hour X"
 * semantics (nightly batch, daily push) do their own per-user hour check
 * inside `action`, since users span many time zones (see NightlyBatchJob,
 * PushService.runPushSweep).
 */
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
