package com.numerology.services

import com.numerology.models.LlmInsightPayload
import com.numerology.models.SupportedLanguages
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
 *
 * One file per SupportedLanguages code under resources/fallback_insights/
 * (e.g. fallback_insights/en.json); a language with no file yet falls back
 * to SupportedLanguages.DEFAULT (ru).
 */
class FallbackBank {
    private val json = Json { ignoreUnknownKeys = true }
    private val bankByLanguage: Map<String, Map<Int, List<FallbackEntry>>>

    init {
        bankByLanguage = SupportedLanguages.CODES.mapNotNull { code ->
            val stream = javaClass.classLoader.getResourceAsStream("fallback_insights/$code.json") ?: return@mapNotNull null
            val parsed = json.decodeFromString<FallbackBankFile>(stream.bufferedReader(Charsets.UTF_8).readText())
            code to parsed.bank.mapKeys { it.key.toInt() }
        }.toMap()
        require(bankByLanguage.containsKey(SupportedLanguages.DEFAULT)) {
            "fallback_insights/${SupportedLanguages.DEFAULT}.json missing from resources"
        }
    }

    fun pick(personalDayNumber: Int, userId: UUID, date: LocalDate, userName: String?, language: String?): LlmInsightPayload {
        val bank = bankByLanguage[language] ?: bankByLanguage.getValue(SupportedLanguages.DEFAULT)
        val entries = bank[personalDayNumber] ?: error("No fallback entries for personal day $personalDayNumber")
        // Deterministic per user+day rotation so the same user doesn't see the same
        // fallback text on every LLM outage, without needing extra state in the DB.
        val seed = date.toEpochDay() + userId.leastSignificantBits
        val index = (((seed % entries.size) + entries.size) % entries.size).toInt()
        val entry = entries[index]
        val displayName = userName?.takeIf { it.isNotBlank() } ?: SupportedLanguages.defaultUserName(language)
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
