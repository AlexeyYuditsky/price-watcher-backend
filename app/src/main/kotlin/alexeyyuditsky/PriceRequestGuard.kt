package alexeyyuditsky

import alexeyyuditsky.auth.AuthRateLimiter
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.RoutingCall
import java.util.UUID

internal class PriceRequestGuard(
    private val userLimiter: AuthRateLimiter,
    private val queue: PriceRequestQueue
) {

    suspend fun run(call: RoutingCall, userId: UUID, block: suspend () -> Unit) {
        if (!userLimiter.allow(userId.toString())) {
            call.response.headers.append("Retry-After", "60")
            call.respondError(HttpStatusCode.TooManyRequests, "Too many price requests")
            return
        }
        if (!queue.tryEnter()) {
            call.response.headers.append("Retry-After", "10")
            call.respondError(HttpStatusCode.ServiceUnavailable, "Server is busy, try again later")
            return
        }
        try {
            block()
        } finally {
            queue.leave()
        }
    }
}