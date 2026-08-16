package com.numerology.google

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

private val logger = LoggerFactory.getLogger("GooglePubSubPuller")

@Serializable
private data class PullRequest(val maxMessages: Int)

@Serializable
private data class ReceivedMessage(val ackId: String, val message: PubSubMessage)

@Serializable
private data class PullResponse(val receivedMessages: List<ReceivedMessage>? = null)

@Serializable
private data class AcknowledgeRequest(val ackIds: List<String>)

/**
 * Pulls Google Play Real-time Developer Notifications from a Cloud Pub/Sub
 * *pull* subscription instead of relying on an inbound push webhook — this
 * works even though the server currently only has a plain HTTP endpoint
 * (Pub/Sub push delivery requires a publicly reachable HTTPS endpoint).
 *
 * Setup once you have a domain: create the topic in Play Console's
 * "Real-time developer notifications" and a Pub/Sub subscription
 * (`GOOGLE_PUBSUB_SUBSCRIPTION`, format `projects/<project>/subscriptions/<name>`)
 * on that topic; the service account from GoogleAuth needs the
 * "Pub/Sub Subscriber" role on it.
 */
class GooglePubSubPuller(
    private val auth: GoogleAuth,
    private val subscriptionName: String?,
    private val onNotification: suspend (messageId: String, notification: DeveloperNotification) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) { install(ContentNegotiation) { json(json) } }

    fun isConfigured(): Boolean = !subscriptionName.isNullOrBlank() && auth.isConfigured()

    suspend fun pullOnce(maxMessages: Int = 20) {
        val subscription = subscriptionName ?: return
        val token = auth.accessTokenOrNull() ?: return

        val pullResponse = try {
            val response = client.post("https://pubsub.googleapis.com/v1/$subscription:pull") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(PullRequest(maxMessages))
            }
            if (!response.status.isSuccess()) {
                logger.warn("Pub/Sub pull failed: HTTP {} — {}", response.status, response.bodyAsText())
                return
            }
            json.decodeFromString<PullResponse>(response.bodyAsText())
        } catch (e: Exception) {
            logger.warn("Pub/Sub pull threw: {}", e.message)
            return
        }

        val received = pullResponse.receivedMessages.orEmpty()
        if (received.isEmpty()) return

        val ackIds = mutableListOf<String>()
        for (msg in received) {
            try {
                val decoded = String(java.util.Base64.getDecoder().decode(msg.message.data), Charsets.UTF_8)
                val notification = json.decodeFromString<DeveloperNotification>(decoded)
                onNotification(msg.message.messageId, notification)
            } catch (e: Exception) {
                logger.error("Failed to process Pub/Sub message ${msg.message.messageId}: ${e.message}", e)
                // Don't ack — let it redeliver / expire so we don't silently lose it.
                continue
            }
            ackIds.add(msg.ackId)
        }

        if (ackIds.isNotEmpty()) {
            try {
                client.post("https://pubsub.googleapis.com/v1/$subscription:acknowledge") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcknowledgeRequest(ackIds))
                }
            } catch (e: Exception) {
                logger.warn("Pub/Sub acknowledge failed: ${e.message}")
            }
        }
    }
}
