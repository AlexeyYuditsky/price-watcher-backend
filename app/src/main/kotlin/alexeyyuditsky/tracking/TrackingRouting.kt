package alexeyyuditsky.tracking

import alexeyyuditsky.CallAuthenticator
import alexeyyuditsky.PriceRequestGuard
import alexeyyuditsky.feature.ozon.parseOzonProductUrl
import alexeyyuditsky.feature.tracking.domain.TrackingService
import alexeyyuditsky.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun Application.configureTrackingRouting(
    service: TrackingService,
    authenticator: CallAuthenticator,
    guard: PriceRequestGuard
) {
    val responses = TrackingResponseMapper()
    routing {
        route("/tracking/products") {
            get {
                val userId = authenticator.userId(call) ?: return@get
                call.respondJson(responses.products(service.list(userId)))
            }
            post {
                val userId = authenticator.userId(call) ?: return@post
                val url = call.productUrl() ?: return@post
                guard.run(call, userId) {
                    call.fetching { call.respondJson(responses.product(service.add(userId, url)), HttpStatusCode.Created) }
                }
            }
            delete("/{id}") {
                val userId = authenticator.userId(call) ?: return@delete
                val productId = call.productId() ?: return@delete
                if (service.remove(userId, productId)) {
                    call.respondText("", status = HttpStatusCode.NoContent)
                } else {
                    call.respondError(HttpStatusCode.NotFound, "Tracked product not found")
                }
            }
            get("/{id}/history") {
                val userId = authenticator.userId(call) ?: return@get
                val productId = call.productId() ?: return@get
                val history = service.history(userId, productId)
                if (history == null) {
                    call.respondError(HttpStatusCode.NotFound, "Tracked product not found")
                } else {
                    call.respondJson(responses.history(history))
                }
            }
            post("/{id}/refresh") {
                val userId = authenticator.userId(call) ?: return@post
                val productId = call.productId() ?: return@post
                guard.run(call, userId) {
                    call.fetching {
                        val product = service.refresh(userId, productId)
                        if (product == null) {
                            call.respondError(HttpStatusCode.NotFound, "Tracked product not found")
                        } else {
                            call.respondJson(responses.product(product))
                        }
                    }
                }
            }
        }
    }
}

private suspend fun RoutingCall.respondJson(body: JsonObject, status: HttpStatusCode = HttpStatusCode.OK) {
    respondText(body.toString(), ContentType.Application.Json, status)
}

private suspend fun RoutingCall.productId(): UUID? =
    runCatching { UUID.fromString(parameters["id"]) }.getOrNull()
        ?: run {
            respondError(HttpStatusCode.NotFound, "Tracked product not found")
            null
        }

private suspend fun RoutingCall.productUrl(): String? = try {
    val body = Json.parseToJsonElement(receiveText()) as? JsonObject
    val value = (body?.get("url") as? JsonPrimitive)
        ?.takeIf { it.isString }?.contentOrNull
        ?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("JSON body must contain a non-empty url string")
    parseOzonProductUrl(value).url
} catch (error: Exception) {
    if (error is CancellationException) throw error
    respondError(HttpStatusCode.BadRequest, "Invalid JSON body or Ozon product URL")
    null
}

private suspend fun RoutingCall.fetching(block: suspend () -> Unit) {
    try {
        block()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        application.log.error("Failed to fetch tracked product price", error)
        respondError(HttpStatusCode.BadGateway, "Could not fetch the product price from Ozon")
    }
}