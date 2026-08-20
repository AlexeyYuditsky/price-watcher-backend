package alexeyyuditsky

import io.ktor.server.engine.*
import io.ktor.server.application.*

fun main() {
    embeddedServer(
        factory = io.ktor.server.netty.Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::rootModule
    ).start(wait = true)
}