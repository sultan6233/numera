package com.numerology.models

import kotlinx.serialization.Serializable

@Serializable
data class EntitlementResponse(
    val active: Boolean,
    val platform: String? = null,
    val productId: String? = null,
    val status: String? = null,
    val expiresAt: String? = null,
)
