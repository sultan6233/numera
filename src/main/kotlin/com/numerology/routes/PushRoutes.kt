package com.numerology.routes

import com.numerology.models.RegisterPushTokenRequest
import com.numerology.plugins.authenticated
import com.numerology.plugins.requireUserId
import com.numerology.services.PushService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.pushRoutes(pushService: PushService) {
    authenticated {
        post("/push/register") {
            val userId = call.requireUserId()
            val request = call.receive<RegisterPushTokenRequest>()
            require(request.platform in setOf("ios", "android")) { "platform must be 'ios' or 'android'" }
            require(request.token.isNotBlank()) { "token is required" }

            pushService.registerToken(userId, request.platform, request.token)
            call.respond(HttpStatusCode.OK, mapOf("status" to "registered"))
        }
    }
}
