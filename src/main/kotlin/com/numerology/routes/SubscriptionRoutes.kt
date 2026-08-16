package com.numerology.routes

import com.numerology.apple.AppleNotificationEnvelope
import com.numerology.plugins.authenticated
import com.numerology.plugins.requireUserId
import com.numerology.services.SubscriptionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("SubscriptionRoutes")

@Serializable
data class AppleVerifyRequest(val signedTransactionInfo: String)

@Serializable
data class GoogleVerifyRequest(val subscriptionId: String, val purchaseToken: String)

fun Route.subscriptionRoutes(subscriptionService: SubscriptionService) {

    // --- Server-to-server webhooks (Apple / Google call these directly, no user auth) ---

    post("/webhooks/apple") {
        val envelope = call.receive<AppleNotificationEnvelope>()
        // Always ack quickly (Apple retries aggressively on non-2xx / timeouts).
        val ok = runCatching { subscriptionService.handleAppleWebhook(envelope.signedPayload) }
            .onFailure { logger.error("Apple webhook processing failed: ${it.message}", it) }
            .getOrDefault(true)
        call.respond(if (ok) HttpStatusCode.OK else HttpStatusCode.InternalServerError)
    }

    post("/webhooks/google") {
        val bodyText = call.receiveText()
        runCatching { subscriptionService.handleGooglePushEnvelopeJson(bodyText) }
            .onFailure { logger.error("Google webhook processing failed: ${it.message}", it) }
        // Ack with 200 either way so Pub/Sub doesn't redeliver storms on our transient errors;
        // real failures are logged and the underlying RTDN will naturally repeat via renewal state anyway.
        call.respond(HttpStatusCode.OK)
    }

    // --- Authenticated endpoints ---
    authenticated {
        get("/entitlement") {
            val userId = call.requireUserId()
            call.respond(HttpStatusCode.OK, subscriptionService.getEntitlement(userId))
        }

        // Not explicitly in the original spec, but required to link a fresh purchase to the
        // anonymous user instantly (rather than waiting for the async webhook) and to support
        // "restore purchases". The client calls this right after StoreKit2 confirms a transaction.
        post("/subscriptions/verify/apple") {
            val userId = call.requireUserId()
            val request = call.receive<AppleVerifyRequest>()
            call.respond(HttpStatusCode.OK, subscriptionService.linkAppleTransaction(userId, request.signedTransactionInfo))
        }

        post("/subscriptions/verify/google") {
            val userId = call.requireUserId()
            val request = call.receive<GoogleVerifyRequest>()
            call.respond(HttpStatusCode.OK, subscriptionService.linkGooglePurchase(userId, request.subscriptionId, request.purchaseToken))
        }
    }
}
