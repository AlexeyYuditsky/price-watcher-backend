package alexeyyuditsky

import alexeyyuditsky.config.BackendConfig
import alexeyyuditsky.config.DatabaseConfig
import alexeyyuditsky.core.database.DatabaseMigrator
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main(args: Array<String>) {
    require(args.isEmpty() || args.contentEquals(arrayOf("--migrate"))) { "Unsupported arguments" }
    val config = BackendConfig.load()
    val database = DatabaseConfig.load(config.profile)
    if (database != null) {
        val applied = DatabaseMigrator().migrate(database)
        println("Database migrations applied: $applied")
    } else {
        check(args.isEmpty()) { "Database configuration is required for --migrate" }
        println("Database is not configured; continuing without persistence")
    }
    if (args.contentEquals(arrayOf("--migrate"))) {
        return
    }
    embeddedServer(
        factory = Netty,
        port = config.port,
        host = config.host
    ) {
        rootModule(config, database)
    }.start(wait = true)
}