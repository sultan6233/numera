package com.numerology.services

import com.numerology.models.LlmInsightPayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

@Serializable
private data class FallbackEntry(
    val headline: String,
    val greeting: String,
    val body: List<String>,
    val focus_area: String,
    val suggested_action: String,
    val affirmation: String,
    val lucky_number: Int,
)

@Serializable
private data class FallbackBankFile(val version: Int, val bank: Map<String, List<FallbackEntry>>)

/**
 * Local, pre-written bank of insights (~5 per personal_day_number, 1-9) used
 * whenever the LLM call fails or times out. The user should never see a
 * generic error — see spec §5 "Fallback на случай сбоя LLM".
 */
class FallbackBank {
    private val json = Json { ignoreUnknownKeys = true }
    private val bank: Map<Int, List<FallbackEntry>>

    init {
        val text = requireNotNull(javaClass.classLoader.getResourceAsStream("fallback_insights.json")) {
            "fallback_insights.json missing from resources"
        }.bufferedReader(Charsets.UTF_8).readText()
        val parsed = json.decodeFromString<FallbackBankFile>(text)
        bank = parsed.bank.mapKeys { it.key.toInt() }
    }

    fun pick(personalDayNumber: Int, userId: UUID, date: LocalDate, userName: String?): LlmInsightPayload {
        val entries = bank[personalDayNumber] ?: error("No fallback entries for personal day $personalDayNumber")
        // Deterministic per user+day rotation so the same user doesn't see the same
        // fallback text on every LLM outage, without needing extra state in the DB.
        val seed = date.toEpochDay() + userId.leastSignificantBits
        val index = (((seed % entries.size) + entries.size) % entries.size).toInt()
        val entry = entries[index]
        val displayName = userName?.takeIf { it.isNotBlank() } ?: "друг"
        return LlmInsightPayload(
            headline = entry.headline,
            greeting = entry.greeting.replace("{{name}}", displayName),
            body = entry.body,
            focus_area = entry.focus_area,
            suggested_action = entry.suggested_action,
            affirmation = entry.affirmation,
            lucky_number = entry.lucky_number,
        )
    }
}
