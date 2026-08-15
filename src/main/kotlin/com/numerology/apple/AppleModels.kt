package com.numerology.apple

import kotlinx.serialization.Serializable

/** Top-level body Apple POSTs to /webhooks/apple (App Store Server Notifications V2). */
@Serializable
data class AppleNotificationEnvelope(val signedPayload: String)

@Serializable
data class AppleNotificationPayload(
    val notificationType: String,
    val subtype: String? = null,
    val notificationUUID: String,
    val data: AppleNotificationData? = null,
    val version: String? = null,
    val signedDate: Long? = null,
)

@Serializable
data class AppleNotificationData(
    val appAppleId: Long? = null,
    val bundleId: String? = null,
    val environment: String? = null,
    val signedTransactionInfo: String? = null,
    val signedRenewalInfo: String? = null,
)

@Serializable
data class AppleTransactionInfo(
    val transactionId: String,
    val originalTransactionId: String,
    val bundleId: String? = null,
    val productId: String? = null,
    val purchaseDate: Long? = null,
    val expiresDate: Long? = null,
    val type: String? = null,
    val environment: String? = null,
)

object AppleNotificationType {
    /** Maps Apple's notificationType/subtype to our internal subscriptions.status vocabulary. */
    fun toInternalStatus(notificationType: String, subtype: String?): String = when (notificationType) {
        "SUBSCRIBED", "DID_RENEW" -> "active"
        "DID_CHANGE_RENEWAL_STATUS" -> if (subtype == "AUTO_RENEW_DISABLED") "cancelled" else "active"
        "GRACE_PERIOD_EXPIRED" -> "expired"
        "EXPIRED" -> "expired"
        "DID_FAIL_TO_RENEW" -> if (subtype == "GRACE_PERIOD") "grace_period" else "expired"
        "REFUND", "REVOKE" -> "refunded"
        "DID_CHANGE_RENEWAL_PREF" -> "active"
        else -> "active"
    }
}
