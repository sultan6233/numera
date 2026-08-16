package com.numerology.routes

import com.numerology.plugins.authenticated
import com.numerology.plugins.requireUserId
import com.numerology.services.InsightService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.LocalDate

fun Route.insightRoutes(insightService: InsightService) {
    authenticated {
        get("/daily-insight") {
            val userId = call.requireUserId()
            val dateParam = call.request.queryParameters["date"]
            val date = dateParam?.let {
                runCatching { LocalDate.parse(it) }.getOrElse {
                    return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "date must be yyyy-MM-dd"))
                }
            } ?: LocalDate.now()

            val response = insightService.getOrGenerate(userId, date)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}
