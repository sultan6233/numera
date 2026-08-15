package com.numerology.services

import com.numerology.llm.InsightGenerationContext
import com.numerology.llm.OpenAiClient
import com.numerology.models.DailyInsightResponse
import com.numerology.models.LlmInsightPayload
import com.numerology.numerology.FocusArea
import com.numerology.numerology.PersonalDayCalculator
import com.numerology.repositories.ComputedNumbersRepository
import com.numerology.repositories.DailyInsightRecord
import com.numerology.repositories.DailyInsightRepository
import com.numerology.repositories.UserRecord
import com.numerology.repositories.UserRepository
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val logger = LoggerFactory.getLogger("InsightService")

class InsightService(
    private val userRepository: UserRepository,
    private val computedNumbersRepository: ComputedNumbersRepository,
    private val dailyInsightRepository: DailyInsightRepository,
    private val openAiClient: OpenAiClient,
    private val fallbackBank: FallbackBank,
) {
    /**
     * Main read path: return the cached insight for user+date, generating it
     * synchronously as a fallback if the nightly batch hasn't produced one yet
     * (e.g. user just subscribed, or batch hasn't run for this date range).
     */
    suspend fun getOrGenerate(userId: UUID, date: LocalDate): DailyInsightResponse {
        dailyInsightRepository.findByUserAndDate(userId, date)?.let { return it.toResponse() }

        val user = userRepository.findById(userId) ?: error("User not found: $userId")
        val record = generateAndStore(user, date)
        return record.toResponse()
    }

    /** Used by the nightly batch job to pre-generate tomorrow's insight for every eligible user. */
    suspend fun generateAndStore(user: UserRecord, date: LocalDate): DailyInsightRecord {
        val birthDate = user.birthDate
            ?: error("User ${user.id} has no birth date on file yet — cannot compute Personal Day Number")

        val personalDayNumber = PersonalDayCalculator.calculate(birthDate, date)
        val focusArea = FocusArea.forDate(date).label
        val computedNumbers = computedNumbersRepository.findByUserId(user.id)
        val recentTitles = dailyInsightRepository.recentTitles(user.id, date, limit = 5)
            .map { "${it.date} — ${it.headline} (${it.focusArea})" }

        val ctx = InsightGenerationContext(
            userName = user.name?.takeIf { it.isNotBlank() } ?: "друг",
            lifePathNumber = computedNumbers?.lifePath,
            expressionNumber = computedNumbers?.expression,
            soulUrgeNumber = computedNumbers?.soulUrge,
            personalityNumber = computedNumbers?.personality,
            todayDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            personalDayNumber = personalDayNumber,
            focusTheme = focusArea,
            recentTitles = recentTitles,
        )

        val (payload, source) = generatePayloadWithFallback(ctx, personalDayNumber, user, date)

        return dailyInsightRepository.upsert(
            userId = user.id,
            date = date,
            personalDayNumber = personalDayNumber,
            focusArea = payload.focus_area.ifBlank { focusArea },
            headline = payload.headline,
            greeting = payload.greeting,
            body = payload.body,
            suggestedAction = payload.suggested_action,
            affirmation = payload.affirmation,
            luckyNumber = payload.lucky_number,
            source = source,
        )
    }

    private suspend fun generatePayloadWithFallback(
        ctx: InsightGenerationContext,
        personalDayNumber: Int,
        user: UserRecord,
        date: LocalDate,
    ): Pair<LlmInsightPayload, String> {
        val llmResult = runCatching { openAiClient.generateInsight(ctx) }.getOrElse {
            logger.warn("LLM generation threw for user ${user.id}: ${it.message}")
            null
        }
        if (llmResult != null) return llmResult to "llm"

        logger.info("Falling back to static bank for user ${user.id}, personal day $personalDayNumber")
        val fallback = fallbackBank.pick(personalDayNumber, user.id, date, user.name)
        return fallback to "fallback"
    }

    private fun DailyInsightRecord.toResponse() = DailyInsightResponse(
        date = date.toString(),
        personalDayNumber = personalDayNumber,
        focusArea = focusArea,
        headline = headline,
        greeting = greeting,
        body = body,
        suggestedAction = suggestedAction,
        affirmation = affirmation,
        luckyNumber = luckyNumber,
        source = source,
    )
}
