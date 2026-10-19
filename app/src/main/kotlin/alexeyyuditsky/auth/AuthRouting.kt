package alexeyyuditsky.auth

import alexeyyuditsky.feature.auth.domain.AuthService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.time.Clock

fun Application.configureAuthRouting(
    service: AuthService,
    clock: Clock,
    trustedProxies: Set<String> = emptySet()
) {
    val requests = AuthRequestMapper()
    val responses = AuthResponseMapper()
    val handler = AuthHttpHandler(AuthRateLimiter(clock), AuthClientAddress(trustedProxies))
    routing {
        post("/auth/register") {
            handler.handle(call, readBody = true) { body ->
                val request = requests.credentials(body)
                val session = service.register(request.email, request.password)
                call.respondText(
                    responses.session(session).toString(),
                    ContentType.Application.Json,
                    HttpStatusCode.Created
                )
            }
        }
        post("/auth/login") {
            handler.handle(call, readBody = true) { body ->
                val request = requests.credentials(body)
                val session = service.login(request.email, request.password)
                call.respondText(responses.session(session).toString(), ContentType.Application.Json)
            }
        }
        post("/auth/refresh") {
            handler.handle(call, readBody = true) { body ->
                val session = service.refresh(requests.refreshToken(body))
                call.respondText(responses.session(session).toString(), ContentType.Application.Json)
            }
        }
        get("/auth/me") {
            handler.handle(call) {
                val user = service.currentUser(handler.bearer(call))
                call.respondText(responses.user(user).toString(), ContentType.Application.Json)
            }
        }
        post("/auth/logout") {
            handler.handle(call) {
                service.logout(handler.bearer(call))
                call.respondText("", status = HttpStatusCode.NoContent)
            }
        }
    }
}