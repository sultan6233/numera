package com.numerology.routes

import com.numerology.services.RemoteConfigService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put

fun Route.configRoutes(remoteConfigService: RemoteConfigService, adminToken: String) {
    // Public and unauthenticated on purpose: the client fetches paywall/feature-flag
    // config at startup, before an anonymous session necessarily exists.
    get("/config") {
        call.respond(HttpStatusCode.OK, remoteConfigService.getConfig())
    }

    // Simple bearer-token-protected admin write, so paywall variants/feature flags can be
    // tweaked without a redeploy. Not in the original spec's endpoint list, but "Remote config
    // for A/B tests without an app release" implies *someone* server-side needs a way to edit it;
    // for MVP this is a curl-able endpoint instead of a full admin CMS (explicitly Phase 2 in §7).
    put("/admin/config/{key}") {
        val token = call.request.headers["X-Admin-Token"]
        if (token != adminToken) {
            return@put call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid admin token"))
        }
        val key = call.parameters["key"] ?: return@put call.respond(HttpStatusCode.BadRequest)
        val body = call.receiveText()
        remoteConfigService.updateConfig(key, body)
        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
    }
}
