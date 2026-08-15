package com.numerology.models

import kotlinx.serialization.Serializable

@Serializable
data class RegisterPushTokenRequest(
    val platform: String, // "ios" | "android"
    val token: String,
)
