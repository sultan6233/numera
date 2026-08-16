package com.numerology.routes

import com.numerology.models.CompanionRequest
import com.numerology.plugins.authenticated
import com.numerology.plugins.requireUserId
import com.numerology.repositories.UserRepository
import com.numerology.services.CompanionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

fun Route.companionRoutes(companionService: CompanionService, userRepository: UserRepository) {
    authenticated(userRepository) {
        post("/profile/companions") {
            val userId = call.requireUserId()
            val request = call.receive<CompanionRequest>()
            call.respond(HttpStatusCode.Created, companionService.create(userId, request))
        }

        get("/profile/companions") {
            val userId = call.requireUserId()
            call.respond(HttpStatusCode.OK, companionService.list(userId))
        }

        delete("/profile/companions/{id}") {
            val userId = call.requireUserId()
            val companionId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid companion id"))

            val deleted = companionService.delete(userId, companionId)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
        }
    }
}
