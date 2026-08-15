package com.numerology.models

import kotlinx.serialization.Serializable

@Serializable
data class AnonymousAuthRequest(val deviceId: String)

@Serializable
data class AnonymousAuthResponse(val userId: String, val token: String)
