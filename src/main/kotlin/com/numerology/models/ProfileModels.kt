package com.numerology.models

import kotlinx.serialization.Serializable

/**
 * The client computes the actual numerology numbers (life path, expression,
 * soul urge, personality, ...) per the spec — this backend just caches
 * whatever the client sends so it doesn't have to be resent on every
 * daily-insight generation.
 */
@Serializable
data class ComputedNumbersDto(
    val lifePath: Int? = null,
    val expression: Int? = null,
    val soulUrge: Int? = null,
    val personality: Int? = null,
    val birthDay: Int? = null,
    val healthCode: Int? = null,
    val businessCode: Int? = null,
)

@Serializable
data class SaveProfileRequest(
    val name: String? = null,
    val birthDate: String? = null, // "yyyy-MM-dd"
    val language: String? = null,
    val timezone: String? = null,
    val computedNumbers: ComputedNumbersDto? = null,
)

@Serializable
data class ProfileResponse(
    val userId: String,
    val name: String?,
    val birthDate: String?,
    val language: String,
    val timezone: String?,
    val computedNumbers: ComputedNumbersDto?,
)
