package com.numerology.services

import com.numerology.apple.AppleJwsDecoder
import com.numerology.apple.AppleNotificationData
import com.numerology.apple.AppleNotificationPayload
import com.numerology.apple.AppleNotificationType
import com.numerology.apple.AppleTransactionInfo
import com.numerology.google.DeveloperNotification
import com.numerology.google.GoogleNotificationType
import com.numerology.google.GooglePlayClient
import com.numerology.models.EntitlementResponse
import com.numerology.repositories.SubscriptionRepository
import com.numerology.repositories.WebhookEventRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private val logger = LoggerFactory.getLogger("SubscriptionService")

class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val webhookEventRepository: WebhookEventRepository,
    private val googlePlayClient: GooglePlayClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ---- Webhooks (server-to-server, keep entitlements fresh over time) ----

    /** Returns false if this exact notification was already processed (Apple redelivers). */
    suspend fun handleAppleWebhook(signedPayload: String): Boolean {
        val payload = AppleJwsDecoder.decodeUnverified<AppleNotificationPayload>(signedPayload)
            ?: run { logger.error("Could not decode Apple notification envelope"); return true }

        if (!webhookEventRepository.tryRecord("apple", payload.notificationUUID)) {
            logger.info("Duplicate Apple notification ${payload.notificationUUID}, skipping")
            return true
        }

        val transactionInfoJws = payload.data?.signedTransactionInfo
        if (transactionInfoJws == null) {
            logger.warn("Apple notification ${payload.notificationUUID} has no signedTransactionInfo (type=${payload.notificationType}), nothing to upsert")
            return true
        }
        val tx = AppleJwsDecoder.decodeUnverified<AppleTransactionInfo>(transactionInfoJws) ?: return true

        val status = AppleNotificationType.toInternalStatus(payload.notificationType, payload.subtype)
        subscriptionRepository.upsert(
            userId = null, // preserved via coalesce if this original_transaction_id is already linked to a user
            platform = "apple",
            productId = tx.productId,
            status = status,
            expiresAt = tx.expiresDate?.toOffsetDateTime(),
            originalTransactionId = tx.originalTransactionId,
            latestTransactionId = tx.transactionId,
            rawPayloadJson = json.encodeToString(payload),
        )
        return true
    }

    /** Processes one decoded Pub/Sub RTDN message. Idempotency is keyed by the Pub/Sub messageId. */
    suspend fun handleGoogleNotification(messageId: String, notification: DeveloperNotification) {
        if (!webhookEventRepository.tryRecord("google", messageId)) {
            logger.info("Duplicate Google Pub/Sub message $messageId, skipping")
            return
        }
        val sub = notification.subscriptionNotification
        if (sub == null) {
            logger.info("Google Pub/Sub message $messageId has no subscriptionNotification (likely a test ping), ignoring")
            return
        }

        val status = GoogleNotificationType.toInternalStatus(sub.notificationType)
        val purchase = googlePlayClient.getSubscriptionPurchase(sub.subscriptionId, sub.purchaseToken)
        val expiresAt = purchase?.expiryTimeMillis?.toLongOrNull()?.toOffsetDateTime()

        subscriptionRepository.upsert(
            userId = null,
            platform = "google",
            productId = sub.subscriptionId,
            status = status,
            expiresAt = expiresAt,
            originalTransactionId = sub.purchaseToken,
            latestTransactionId = purchase?.orderId,
            rawPayloadJson = json.encodeToString(notification),
        )
    }

    /** Parses the push-delivery envelope Google would POST to /webhooks/google if/when a domain+HTTPS is set up. */
    suspend fun handleGooglePushEnvelopeJson(bodyText: String) {
        val envelope = json.decodeFromString<com.numerology.google.PubSubPushEnvelope>(bodyText)
        val decoded = String(java.util.Base64.getDecoder().decode(envelope.message.data), Charsets.UTF_8)
        val notification = json.decodeFromString<DeveloperNotification>(decoded)
        handleGoogleNotification(envelope.message.messageId, notification)
    }

    // ---- Direct verification (called by the client right after a purchase, so entitlement is instant) ----

    suspend fun linkAppleTransaction(userId: UUID, signedTransactionInfo: String): EntitlementResponse {
        val tx = AppleJwsDecoder.decodeUnverified<AppleTransactionInfo>(signedTransactionInfo)
            ?: return EntitlementResponse(active = false)

        val now = Instant.now()
        val expiresAt = tx.expiresDate?.toOffsetDateTime()
        val status = if (expiresAt != null && expiresAt.toInstant().isAfter(now)) "active" else "expired"

        subscriptionRepository.upsert(
            userId = userId,
            platform = "apple",
            productId = tx.productId,
            status = status,
            expiresAt = expiresAt,
            originalTransactionId = tx.originalTransactionId,
            latestTransactionId = tx.transactionId,
            rawPayloadJson = json.encodeToString(tx),
        )
        subscriptionRepository.linkUserByOriginalTransactionId("apple", tx.originalTransactionId, userId)
        return EntitlementResponse(active = status == "active", platform = "apple", productId = tx.productId, status = status, expiresAt = expiresAt?.toString())
    }

    suspend fun linkGooglePurchase(userId: UUID, subscriptionId: String, purchaseToken: String): EntitlementResponse {
        val purchase = googlePlayClient.getSubscriptionPurchase(subscriptionId, purchaseToken)
            ?: return EntitlementResponse(active = false, status = "unknown")

        val expiresAt = purchase.expiryTimeMillis?.toLongOrNull()?.toOffsetDateTime()
        val status = if (expiresAt != null && expiresAt.toInstant().isAfter(Instant.now())) "active" else "expired"

        subscriptionRepository.upsert(
            userId = userId,
            platform = "google",
            productId = subscriptionId,
            status = status,
            expiresAt = expiresAt,
            originalTransactionId = purchaseToken,
            latestTransactionId = purchase.orderId,
            rawPayloadJson = json.encodeToString(purchase),
        )
        subscriptionRepository.linkUserByOriginalTransactionId("google", purchaseToken, userId)
        return EntitlementResponse(active = status == "active", platform = "google", productId = subscriptionId, status = status, expiresAt = expiresAt?.toString())
    }

    // ---- Read path ----

    suspend fun getEntitlement(userId: UUID): EntitlementResponse {
        val active = subscriptionRepository.findActiveForUser(userId).firstOrNull()
        return if (active == null) {
            EntitlementResponse(active = false)
        } else {
            EntitlementResponse(
                active = true,
                platform = active.platform,
                productId = active.productId,
                status = active.status,
                expiresAt = active.expiresAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            )
        }
    }

    private fun Long.toOffsetDateTime(): OffsetDateTime = Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC)
}
