package com.numerology.routes

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Static, versioned reference JSON (values.n meanings for each numerology
 * number) — served straight from the packaged resource so it can be updated
 * with a redeploy of the backend, without an app store release. The `version`
 * field inside the JSON lets the client cache it locally and only refetch
 * when it changes.
 */
private val referenceNumbersJson: String by lazy {
    val stream = object {}.javaClass.classLoader.getResourceAsStream("reference_numbers.json")
        ?: error("reference_numbers.json missing from resources")
    stream.bufferedReader(Charsets.UTF_8).readText()
}

fun Route.referenceRoutes() {
    get("/reference/numbers") {
        call.respondText(referenceNumbersJson, ContentType.Application.Json)
    }
}
