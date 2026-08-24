package alexeyyuditsky

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val server = embeddedServer(
        factory = Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::rootModule
    ).start(wait = false)

    try {
        LocalPriceWatcher().run()
    } finally {
        server.stop(gracePeriodMillis = 1_000, timeoutMillis = 5_000)
    }
}