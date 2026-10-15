package alexeyyuditsky

import alexeyyuditsky.ozon.parseOzonProductUrl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.time.Instant

fun Application.configureRouting() {
    val priceWatcher = LocalPriceWatcher()
    monitor.subscribe(ApplicationStopped) { priceWatcher.close() }

    routing {
        post("/products/price") {
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
                return@post
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
    }
}