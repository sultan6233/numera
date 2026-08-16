package com.numerology.google

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("GooglePlayClient")

@Serializable
data class GooglePlaySubscriptionPurchase(
    val startTimeMillis: String? = null,
    val expiryTimeMillis: String? = null,
    val autoRenewing: Boolean? = null,
    val paymentState: Int? = null,           // 0 pending, 1 received, 2 free trial, 3 pending deferred
    val cancelReason: Int? = null,
    val orderId: String? = null,
    val linkedPurchaseToken: String? = null,
)

/**
 * Thin wrapper over the Google Play Developer API (Android Publisher v3) used
 * to verify subscription purchase tokens server-side. Requires
 * GOOGLE_PLAY_SERVICE_ACCOUNT_JSON + GOOGLE_PLAY_PACKAGE_NAME to be set; until
 * then every call returns null and callers should treat that as "unknown",
 * not "inactive".
 */
class GooglePlayClient(
    private val auth: GoogleAuth,
    private val packageName: String?,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) { install(ContentNegotiation) { json(json) } }

    suspend fun getSubscriptionPurchase(subscriptionId: String, purchaseToken: String): GooglePlaySubscriptionPurchase? {
        val token = auth.accessTokenOrNull() ?: return null
        val pkg = packageName ?: return null
        val url =
            "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$pkg/purchases/subscriptions/$subscriptionId/tokens/$purchaseToken"
        return try {
            val response = client.get(url) { header("Authorization", "Bearer $token") }
            if (!response.status.isSuccess()) {
                logger.warn("Google Play purchase lookup failed: HTTP {} — {}", response.status, response.bodyAsText())
                return null
            }
            json.decodeFromString(response.bodyAsText())
        } catch (e: Exception) {
            logger.warn("Google Play purchase lookup threw: {}", e.message)
            null
        }
    }
}
