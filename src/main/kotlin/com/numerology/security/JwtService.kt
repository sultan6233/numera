package com.numerology.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.numerology.config.AppConfig
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Issues and validates JWTs for anonymous device-based auth. There is no
 * password: possession of the token (returned once from POST /auth/anonymous)
 * is the credential, same approach apps like this typically use for
 * anonymous-first onboarding with later "restore purchases".
 */
class JwtService(config: AppConfig) {
    private val algorithm: Algorithm = Algorithm.HMAC256(config.jwtSecret)
    val issuer: String = config.jwtIssuer
    val audience: String = config.jwtAudience
    val verifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun generateToken(userId: UUID): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId.toString())
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365)))
            .sign(algorithm)
}
