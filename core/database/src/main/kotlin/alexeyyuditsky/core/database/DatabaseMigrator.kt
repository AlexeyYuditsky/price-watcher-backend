package alexeyyuditsky.core.database

import org.flywaydb.core.Flyway

class DatabaseMigrator {
    fun migrate(connection: DatabaseConnection): Int =
        Flyway.configure()
            .dataSource(connection.url, connection.user, connection.password)
            .locations("classpath:db/migration")
            .cleanDisabled(true)
            .validateMigrationNaming(true)
            .load()
            .migrate()
            .migrationsExecuted
}