package com.numerology.google

import kotlinx.serialization.Serializable

/** Envelope Google Cloud Pub/Sub wraps every message in (both push delivery and pull responses). */
@Serializable
data class PubSubPushEnvelope(val message: PubSubMessage, val subscription: String? = null)

@Serializable
data class PubSubMessage(
    val data: String,          // base64-encoded DeveloperNotification JSON
    val messageId: String,
    val publishTime: String? = null,
    val attributes: Map<String, String>? = null,
)

/** Body of the base64-decoded `data` field — see Play's Real-time Developer Notifications reference. */
@Serializable
data class DeveloperNotification(
    val version: String? = null,
    val packageName: String? = null,
    val eventTimeMillis: String? = null,
    val subscriptionNotification: SubscriptionNotification? = null,
    val testNotification: TestNotification? = null,
)

@Serializable
data class SubscriptionNotification(
    val version: String? = null,
    val notificationType: Int,
    val purchaseToken: String,
    val subscriptionId: String,
)

@Serializable
data class TestNotification(val version: String? = null)

object GoogleNotificationType {
    const val SUBSCRIPTION_RECOVERED = 1
    const val SUBSCRIPTION_RENEWED = 2
    const val SUBSCRIPTION_CANCELED = 3
    const val SUBSCRIPTION_PURCHASED = 4
    const val SUBSCRIPTION_ON_HOLD = 5
    const val SUBSCRIPTION_IN_GRACE_PERIOD = 6
    const val SUBSCRIPTION_RESTARTED = 7
    const val SUBSCRIPTION_PRICE_CHANGE_CONFIRMED = 8
    const val SUBSCRIPTION_DEFERRED = 9
    const val SUBSCRIPTION_PAUSED = 10
    const val SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED = 11
    const val SUBSCRIPTION_REVOKED = 12
    const val SUBSCRIPTION_EXPIRED = 13

    /** Maps a Google notificationType to our internal subscriptions.status vocabulary. */
    fun toInternalStatus(notificationType: Int): String = when (notificationType) {
        SUBSCRIPTION_RECOVERED, SUBSCRIPTION_RENEWED, SUBSCRIPTION_PURCHASED, SUBSCRIPTION_RESTARTED -> "active"
        SUBSCRIPTION_IN_GRACE_PERIOD -> "grace_period"
        SUBSCRIPTION_ON_HOLD, SUBSCRIPTION_PAUSED -> "on_hold"
        SUBSCRIPTION_CANCELED -> "cancelled"
        SUBSCRIPTION_REVOKED -> "refunded"
        SUBSCRIPTION_EXPIRED -> "expired"
        else -> "active"
    }
}
