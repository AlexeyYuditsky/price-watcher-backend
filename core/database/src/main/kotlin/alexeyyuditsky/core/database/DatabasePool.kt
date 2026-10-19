package alexeyyuditsky.core.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection

class DatabasePool(connection: DatabaseConnection) : AutoCloseable {
    private val source = HikariDataSource(HikariConfig().apply {
        jdbcUrl = connection.url
        username = connection.user
        password = connection.password
        maximumPoolSize = 4
        minimumIdle = 1
        connectionTimeout = 5_000
        validationTimeout = 2_000
        poolName = "price-watcher-database"
    })

    fun open(): Connection = source.connection

    fun isHealthy(): Boolean = runCatching { open().use { it.isValid(2) } }.getOrDefault(false)

    override fun close() = source.close()
}