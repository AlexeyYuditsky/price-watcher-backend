package alexeyyuditsky.auth

import alexeyyuditsky.feature.auth.domain.AuthError
import alexeyyuditsky.feature.auth.domain.AuthException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class AuthHttpHandler(
    private val limiter: AuthRateLimiter,
    private val clientAddress: AuthClientAddress
) {
    private val concurrent = Semaphore(4)

    suspend fun handle(
        call: ApplicationCall,
        readBody: Boolean = false,
        action: suspend (String) -> Unit
    ) {
        call.response.headers.append("Cache-Control", "no-store")
        val address = clientAddress.resolve(
            call.request.local.remoteAddress,
            call.request.headers["X-Forwarded-For"]
        )
        if (!limiter.allow(address)) {
            tooManyRequests(call)
            return
        }
        try {
            val body = if (readBody) {
                withTimeoutOrNull(5_000) { body(call) }
            } else {
                ""
            }
            if (body == null) {
                respondError(call, HttpStatusCode.RequestTimeout, "Request body read timed out")
                return
            }
            if (!concurrent.tryAcquire()) {
                tooManyRequests(call)
                return
            }
            try {
                withContext(Dispatchers.IO) { action(body) }
            } finally {
                concurrent.release()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: AuthException) {
            val status = when (error.error) {
                AuthError.INVALID_INPUT -> HttpStatusCode.BadRequest
                AuthError.EMAIL_IN_USE -> HttpStatusCode.Conflict
                AuthError.UNAUTHORIZED -> HttpStatusCode.Unauthorized
            }
            respondError(call, status, error.message ?: "Authentication failed")
        } catch (error: Exception) {
            call.application.log.error("Authentication operation failed: {}", error.javaClass.simpleName)
            respondError(call, HttpStatusCode.InternalServerError, "Authentication is temporarily unavailable")
        }
    }

    private suspend fun body(call: ApplicationCall): String {
        val body = call.receiveChannel().readRemaining(8_193).readText()
        if (body.toByteArray(Charsets.UTF_8).size > 8_192) {
            throw AuthException(AuthError.INVALID_INPUT, "Request body is too large")
        }
        return body
    }

    fun bearer(call: ApplicationCall): String {
        val value = call.request.headers["Authorization"] ?: ""
        if (!value.startsWith("Bearer ", ignoreCase = true)) {
            throw AuthException(AuthError.UNAUTHORIZED, "Bearer token is required")
        }
        return value.substring(7)
    }

    private suspend fun tooManyRequests(call: ApplicationCall) {
        call.response.headers.append("Retry-After", "60")
        respondError(call, HttpStatusCode.TooManyRequests, "Too many requests")
    }

    private suspend fun respondError(call: ApplicationCall, status: HttpStatusCode, message: String) {
        call.respondText(
            buildJsonObject { put("error", message) }.toString(),
            ContentType.Application.Json,
            status
        )
    }
}