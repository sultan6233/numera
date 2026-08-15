package com.numerology.google

import com.google.auth.oauth2.GoogleCredentials
import org.slf4j.LoggerFactory
import java.io.FileInputStream

private val logger = LoggerFactory.getLogger("GoogleAuth")

/**
 * Loads the Google service-account key and mints short-lived OAuth2 access
 * tokens for both the Android Publisher API (subscription verification) and
 * the Pub/Sub API (RTDN pull worker). One service account JSON, two scopes.
 *
 * Where to get GOOGLE_PLAY_SERVICE_ACCOUNT_JSON:
 *   Google Play Console -> Setup -> API access -> link/create a Google Cloud
 *   service account -> grant it "Financial data / Manage orders and
 *   subscriptions" permission for the app -> then in Google Cloud Console ->
 *   IAM & Admin -> Service Accounts -> Keys -> Add key -> JSON -> download.
 *   The same service account also needs the Pub/Sub Subscriber role on the
 *   RTDN topic if you want the pull worker to fetch notifications.
 */
class GoogleAuth(
    private val serviceAccountJsonPath: String?,
    private val scopes: List<String> = listOf(
        "https://www.googleapis.com/auth/androidpublisher",
        "https://www.googleapis.com/auth/pubsub",
    ),
    private val label: String = "GOOGLE_PLAY_SERVICE_ACCOUNT_JSON",
) {
    private val credentials: GoogleCredentials? by lazy {
        val path = serviceAccountJsonPath
        if (path.isNullOrBlank()) {
            logger.warn("$label not set — calls that need it will be skipped")
            null
        } else {
            runCatching {
                FileInputStream(path).use { GoogleCredentials.fromStream(it).createScoped(scopes) }
            }.onFailure { logger.error("Failed to load Google service account from $path: ${it.message}") }
                .getOrNull()
        }
    }

    fun accessTokenOrNull(): String? {
        val creds = credentials ?: return null
        return runCatching {
            creds.refreshIfExpired()
            creds.accessToken?.tokenValue
        }.onFailure { logger.warn("Failed to refresh Google access token: ${it.message}") }.getOrNull()
    }

    fun isConfigured(): Boolean = credentials != null
}
