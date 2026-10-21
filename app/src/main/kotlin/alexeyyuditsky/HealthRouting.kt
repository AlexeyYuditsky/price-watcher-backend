package alexeyyuditsky

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun Application.configureHealthRouting(
    databaseHealthy: () -> Boolean,
    ensureBrowserReady: suspend () -> Unit
) {
    routing {
        get("/health") {
            val database = if (withContext(Dispatchers.IO) { databaseHealthy() }) "ok" else "failed"
            val browser = try {
                withTimeoutOrNull(30_000) { ensureBrowserReady() }?.let { "ok" } ?: "timeout"
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                call.application.log.error("Browser health check failed", error)
                "failed"
            }
            val healthy = database == "ok" && browser == "ok"
            call.response.headers.append("Cache-Control", "no-store")
            call.respondText(
                buildJsonObject {
                    put("status", if (healthy) "ok" else "failed")
                    put("database", database)
                    put("browser", browser)
                }.toString(),
                ContentType.Application.Json,
                if (healthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            )
        }
    }
}