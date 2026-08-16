package com.numerology.routes

import com.numerology.models.AnonymousAuthRequest
import com.numerology.models.AnonymousAuthResponse
import com.numerology.repositories.UserRepository
import com.numerology.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.authRoutes(userRepository: UserRepository, jwtService: JwtService) {
    post("/auth/anonymous") {
        val request = call.receive<AnonymousAuthRequest>()
        require(request.deviceId.isNotBlank()) { "deviceId is required" }

        val user = userRepository.createAnonymous(request.deviceId)
        val token = jwtService.generateToken(user.id)
        call.respond(HttpStatusCode.OK, AnonymousAuthResponse(userId = user.id.toString(), token = token))
    }
}
