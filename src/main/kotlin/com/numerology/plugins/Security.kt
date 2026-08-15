package com.numerology.plugins

import com.numerology.security.JwtService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import java.util.UUID

const val AUTH_JWT = "auth-jwt"

fun Application.configureSecurity(jwtService: JwtService) {
    install(Authentication) {
        jwt(AUTH_JWT) {
            verifier(jwtService.verifier)
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                if (userId != null) JWTPrincipal(credential.payload) else null
            }
        }
    }
}

/** Convenience wrapper: routes under this block get the userId available via call.requireUserId(). */
fun Route.authenticated(build: Route.() -> Unit) = authenticate(AUTH_JWT) { build() }

fun ApplicationCall.requireUserId(): UUID {
    val jwtPrincipal = principal<JWTPrincipal>() ?: error("Missing auth principal")
    val userId = jwtPrincipal.payload.getClaim("userId").asString() ?: error("Token missing userId claim")
    return UUID.fromString(userId)
}
