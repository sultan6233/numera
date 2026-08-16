package com.numerology.routes

import com.numerology.models.SaveProfileRequest
import com.numerology.plugins.authenticated
import com.numerology.plugins.requireUserId
import com.numerology.services.ProfileService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.profileRoutes(profileService: ProfileService) {
    authenticated {
        post("/profile") {
            val userId = call.requireUserId()
            val request = call.receive<SaveProfileRequest>()
            val response = profileService.saveProfile(userId, request)
            call.respond(HttpStatusCode.OK, response)
        }

        get("/profile") {
            val userId = call.requireUserId()
            call.respond(HttpStatusCode.OK, profileService.getProfile(userId))
        }
    }
}
