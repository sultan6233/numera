package com.numerology.apple

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Base64

@PublishedApi
internal val logger = LoggerFactory.getLogger("AppleJwsDecoder")

/**
 * Decodes the JWS payloads Apple sends (signedPayload, signedTransactionInfo,
 * signedRenewalInfo) WITHOUT verifying the cryptographic signature.
 *
 * TODO once real Apple credentials are available (APPLE_KEY_ID, APPLE_ISSUER_ID,
 * APPLE_BUNDLE_ID, and the .p8 private key from App Store Connect -> Users and
 * Access -> Integrations -> In-App Purchase): verify the x5c certificate chain
 * in the JWS header against Apple's root CA (G3) before trusting the payload.
 * The official `app-store-server-library` (Apple's Java/Kotlin SDK) does this
 * out of the box and is the recommended drop-in replacement for this decoder.
 * Until then, treat data from this endpoint as informational, not authoritative
 * for granting paid entitlements to money-sensitive actions.
 */
object AppleJwsDecoder {
    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true }
    @PublishedApi
    internal val decoder = Base64.getUrlDecoder()

    inline fun <reified T> decodeUnverified(jws: String): T? {
        return try {
            val parts = jws.split(".")
            require(parts.size == 3) { "Not a JWS compact serialization (expected 3 segments)" }
            val payloadJson = String(pad(parts[1]).let { decoder.decode(it) }, Charsets.UTF_8)
            json.decodeFromString<T>(payloadJson)
        } catch (e: Exception) {
            logger.error("Failed to decode Apple JWS payload: ${e.message}", e)
            null
        }
    }

    fun pad(base64Url: String): String {
        val rem = base64Url.length % 4
        return if (rem == 0) base64Url else base64Url + "=".repeat(4 - rem)
    }
}
