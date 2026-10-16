package alexeyyuditsky

import alexeyyuditsky.config.BackendConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = BackendConfig.load()
    embeddedServer(
        factory = Netty,
        port = config.port,
        host = config.host
    ) {
        rootModule(config)
    }.start(wait = true)
}