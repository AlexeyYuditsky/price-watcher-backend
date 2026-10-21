package alexeyyuditsky

import alexeyyuditsky.feature.auth.domain.AuthException
import alexeyyuditsky.feature.auth.domain.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.routing.RoutingCall
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CallAuthenticator(private val authService: AuthService) {

    suspend fun userId(call: RoutingCall): UUID? {
        call.response.headers.append("Cache-Control", "no-store")
        val token = call.request.headers["Authorization"]
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring(7)
        return try {
            checkNotNull(token) { "Bearer token is required" }
            withContext(Dispatchers.IO) { authService.currentUser(token) }.id
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error !is AuthException && error !is IllegalStateException) {
                call.application.log.error("Request authentication failed: {}", error.javaClass.simpleName)
                call.respondError(HttpStatusCode.ServiceUnavailable, "Authentication is temporarily unavailable")
            } else {
                call.respondError(HttpStatusCode.Unauthorized, "Invalid credentials or expired session")
            }
            null
        }
    }
}