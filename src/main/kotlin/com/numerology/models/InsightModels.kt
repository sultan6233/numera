package com.numerology.models

import kotlinx.serialization.Serializable

@Serializable
data class DailyInsightResponse(
    val date: String,
    val personalDayNumber: Int,
    val focusArea: String,
    val headline: String,
    val greeting: String?,
    val body: List<String>,
    val suggestedAction: String?,
    val affirmation: String?,
    val luckyNumber: Int?,
    val source: String,
)

/** Shape the LLM is instructed to return, per the spec's JSON schema. */
@Serializable
data class LlmInsightPayload(
    val headline: String,
    val greeting: String,
    val body: List<String>,
    val focus_area: String,
    val suggested_action: String,
    val affirmation: String,
    val lucky_number: Int,
)
