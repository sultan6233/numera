package com.numerology.fcm

import com.numerology.google.GoogleAuth
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("FcmClient")

@Serializable
private data class FcmNotification(val title: String, val body: String)

@Serializable
private data class FcmMessage(val token: String, val notification: FcmNotification, val data: Map<String, String>? = null)

@Serializable
private data class FcmSendRequest(val message: FcmMessage)

/**
 * Sends push notifications via Firebase Cloud Messaging HTTP v1 API — one
 * endpoint for both iOS and Android tokens, per the spec.
 *
 * Where to get FIREBASE_SERVICE_ACCOUNT_JSON and GOOGLE_CLOUD_PROJECT_ID:
 *   Firebase Console -> Project settings (gear icon) -> Service accounts tab
 *   -> "Generate new private key" (downloads the JSON). The project id shown
 *   on that same page is GOOGLE_CLOUD_PROJECT_ID. Also register the iOS/Android
 *   apps in that Firebase project so their push tokens are valid FCM targets.
 */
class FcmClient(
    firebaseServiceAccountJsonPath: String?,
    private val projectId: String?,
) {
    private val auth = GoogleAuth(
        serviceAccountJsonPath = firebaseServiceAccountJsonPath,
        scopes = listOf("https://www.googleapis.com/auth/firebase.messaging"),
        label = "FIREBASE_SERVICE_ACCOUNT_JSON",
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) { install(ContentNegotiation) { json(json) } }

    fun isConfigured(): Boolean = auth.isConfigured() && !projectId.isNullOrBlank()

    /** Returns true if the token looks invalid/unregistered and should be deleted by the caller. */
    suspend fun send(token: String, title: String, body: String, data: Map<String, String> = emptyMap()): SendResult {
        val accessToken = auth.accessTokenOrNull() ?: return SendResult.SKIPPED_NOT_CONFIGURED
        val pid = projectId ?: return SendResult.SKIPPED_NOT_CONFIGURED

        return try {
            val response = client.post("https://fcm.googleapis.com/v1/projects/$pid/messages:send") {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(FcmSendRequest(FcmMessage(token = token, notification = FcmNotification(title, body), data = data)))
            }
            when {
                response.status.isSuccess() -> SendResult.OK
                response.status.value == 404 || response.status.value == 400 -> {
                    logger.info("FCM token looks invalid (HTTP {}): {}", response.status, response.bodyAsText())
                    SendResult.INVALID_TOKEN
                }
                else -> {
                    logger.warn("FCM send failed: HTTP {} — {}", response.status, response.bodyAsText())
                    SendResult.FAILED
                }
            }
        } catch (e: Exception) {
            logger.warn("FCM send threw: {}", e.message)
            SendResult.FAILED
        }
    }

    enum class SendResult { OK, INVALID_TOKEN, FAILED, SKIPPED_NOT_CONFIGURED }
}
