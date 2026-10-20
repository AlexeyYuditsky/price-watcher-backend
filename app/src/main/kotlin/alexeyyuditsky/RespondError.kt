package alexeyyuditsky

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal suspend fun RoutingCall.respondError(status: HttpStatusCode, message: String) {
    respondText(buildJsonObject { put("error", message) }.toString(), ContentType.Application.Json, status)
}