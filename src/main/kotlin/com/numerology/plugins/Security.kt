package com.numerology.plugins

import com.numerology.models.SupportedLanguages
import com.numerology.repositories.UserRepository
import com.numerology.security.JwtService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import java.time.ZoneId
import java.util.UUID

const val AUTH_JWT = "auth-jwt"

/**
 * Client's current time zone, e.g. "Asia/Tashkent" or "+05:00" (anything
 * java.time.ZoneId accepts). Optional on every authenticated request — see
 * Route.authenticated.
 */
const val TIMEZONE_HEADER = "X-Timezone"

/**
 * Client's current app language as a code from SupportedLanguages (e.g.
 * "en", "pt-BR", "tr"). Optional on every authenticated request — drives
 * what language the LLM writes daily insights in; see OpenAiClient.
 */
const val LANGUAGE_HEADER = "X-Language"

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

/**
 * Convenience wrapper: routes under this block get the userId available via
 * call.requireUserId(). Also keeps the user's stored profile `timezone` and
 * `language` in sync with the X-Timezone / X-Language headers on every
 * request (one cheap indexed upsert) -- NightlyBatchJob, PushService and the
 * LLM prompt all run with no request in flight, so they can only ever read
 * whatever was last persisted here; this is what lets a traveling user's
 * schedule, or someone who switched the app's language, take effect without
 * a separate "update my profile" call.
 */
fun Route.authenticated(userRepository: UserRepository, build: Route.() -> Unit) = authenticate(AUTH_JWT) {
    intercept(ApplicationCallPipeline.Call) {
        val timezoneHeader = call.request.header(TIMEZONE_HEADER)
            ?.takeIf { it.isNotBlank() && runCatching { ZoneId.of(it) }.isSuccess }
        val languageHeader = call.request.header(LANGUAGE_HEADER)
            ?.takeIf { SupportedLanguages.isSupported(it) }

        if (timezoneHeader != null || languageHeader != null) {
            val userId = call.requireUserId()
            runCatching {
                userRepository.updateProfile(userId, name = null, birthDate = null, language = languageHeader, timezone = timezoneHeader)
            }
        }
    }
    build()
}

fun ApplicationCall.requireUserId(): UUID {
    val jwtPrincipal = principal<JWTPrincipal>() ?: error("Missing auth principal")
    val userId = jwtPrincipal.payload.getClaim("userId").asString() ?: error("Token missing userId claim")
    return UUID.fromString(userId)
}
