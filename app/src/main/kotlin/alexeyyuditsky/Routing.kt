package alexeyyuditsky

import alexeyyuditsky.auth.AuthRateLimiter
import alexeyyuditsky.core.products.ProductPriceSource
import alexeyyuditsky.feature.auth.domain.AuthException
import alexeyyuditsky.feature.auth.domain.AuthService
import alexeyyuditsky.feature.ozon.parseOzonProductUrl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.Instant
import java.util.Properties

fun Application.configureRouting(
    priceWatcher: ProductPriceSource,
    authService: AuthService?,
    clock: Clock
) {
    val userLimiter = AuthRateLimiter(clock, maxRequests = 30)
    val queue = PriceRequestQueue(capacity = 15)
    routing {
        get("/version") {
            val version = Properties().apply {
                Application::class.java.getResourceAsStream("/gradle.properties").use(::load)
            }.getProperty("backendVersion")
            call.respondText(
                buildJsonObject {
                    put("version", version)
                }.toString(),
                ContentType.Application.Json
            )
        }

        post("/products/price") {
            call.response.headers.append("Cache-Control", "no-store")
            if (authService == null) {
                call.respondError(HttpStatusCode.ServiceUnavailable, "Authentication is not configured")
                return@post
            }
            val token = call.request.headers["Authorization"]
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.substring(7)
            val userId = try {
                checkNotNull(token) { "Bearer token is required" }
                withContext(Dispatchers.IO) { authService.currentUser(token) }.id
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (error !is AuthException && error !is IllegalStateException) {
                    call.application.log.error("Price authentication failed: {}", error.javaClass.simpleName)
                    call.respondError(HttpStatusCode.ServiceUnavailable, "Authentication is temporarily unavailable")
                } else {
                    call.respondError(HttpStatusCode.Unauthorized, "Invalid credentials or expired session")
                }
                return@post
            }
            if (!userLimiter.allow(userId.toString())) {
                call.response.headers.append("Retry-After", "60")
                call.respondError(HttpStatusCode.TooManyRequests, "Too many price requests")
                return@post
            }
            if (!queue.tryEnter()) {
                call.response.headers.append("Retry-After", "10")
                call.respondError(HttpStatusCode.ServiceUnavailable, "Server is busy, try again later")
                return@post
            }
            try {
                fetchPrice(priceWatcher)
            } finally {
                queue.leave()
            }
        }
    }
}

private suspend fun RoutingCall.respondError(status: HttpStatusCode, message: String) {
    respondText(buildJsonObject { put("error", message) }.toString(), ContentType.Application.Json, status)
}

private suspend fun RoutingContext.fetchPrice(priceWatcher: ProductPriceSource) {
    val url = try {
        val body = Json.parseToJsonElement(call.receiveText()) as? JsonObject
        val value = (body?.get("url") as? JsonPrimitive)
            ?.takeIf { it.isString }?.contentOrNull
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("JSON body must contain a non-empty url string")
        parseOzonProductUrl(value).url
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        call.respondText(
            buildJsonObject { put("error", "Invalid JSON body or Ozon product URL") }.toString(),
            ContentType.Application.Json,
            HttpStatusCode.BadRequest
        )
        return
    }

    try {
        val price = priceWatcher.getPrice(url)
        val response = buildJsonObject {
            put("productId", price.productId)
            put("amount", JsonPrimitive(price.amount))
            put("regularAmount", JsonPrimitive(price.regularAmount))
            put("bankAmount", price.bankAmount?.let { JsonPrimitive(it) } ?: JsonNull)
            put("currency", price.currency)
            put("availability", price.availability)
            put("fetchedAt", Instant.now().toString())
        }
        call.respondText(response.toString(), ContentType.Application.Json)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        call.application.log.error("Failed to fetch Ozon product price", error)
        call.respondText(
            buildJsonObject { put("error", "Could not fetch the product price from Ozon") }.toString(),
            ContentType.Application.Json,
            HttpStatusCode.BadGateway
        )
    }
}