package alexeyyuditsky.config

import alexeyyuditsky.core.database.DatabaseConnection
import java.io.File
import java.util.Properties

object DatabaseConfig {
    fun load(
        profile: String,
        environment: Map<String, String> = System.getenv(),
        userHome: String = System.getProperty("user.home")
    ): DatabaseConnection? {
        fun variable(name: String): String? =
            environment[name] ?: environment.entries.firstOrNull {
                it.key.equals(name, ignoreCase = true)
            }?.value

        val properties = Properties()
        val explicitFile = variable("DB_CONFIG_FILE")
        val candidates = if (profile == "local") {
            listOfNotNull(
                variable("LOCALAPPDATA")?.let(::File),
                File(userHome, "AppData/Local")
            ).map { File(it, "PriceWatcher/postgresql/backend-database.properties") }
        } else {
            emptyList()
        }
        val file = explicitFile?.let(::File) ?: candidates.firstOrNull(File::isFile)
        if (explicitFile != null || file != null) {
            checkNotNull(file).inputStream().use(properties::load)
        }
        val url = variable("DB_URL") ?: properties.getProperty("db.url")
        val user = variable("DB_USER") ?: properties.getProperty("db.user")
        val password = variable("DB_PASSWORD") ?: properties.getProperty("db.password")
        if (url == null && user == null && password == null) {
            check(profile != "local") {
                "Local database configuration is missing. Set DB_CONFIG_FILE or DB_URL, DB_USER and DB_PASSWORD."
            }
            return null
        }
        require(!url.isNullOrBlank() && url.startsWith("jdbc:postgresql:")) {
            "DB_URL must be a PostgreSQL JDBC URL"
        }
        require(!user.isNullOrBlank()) { "DB_USER is required" }
        require(!password.isNullOrBlank()) { "DB_PASSWORD is required" }
        return DatabaseConnection(url, user, password)
    }
}