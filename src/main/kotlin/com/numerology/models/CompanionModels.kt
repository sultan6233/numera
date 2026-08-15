package com.numerology.models

import kotlinx.serialization.Serializable

@Serializable
data class CompanionRequest(
    val name: String,
    val birthDate: String, // "yyyy-MM-dd"
    val relationLabel: String? = null,
)

@Serializable
data class CompanionResponse(
    val id: String,
    val name: String,
    val birthDate: String,
    val relationLabel: String?,
)
