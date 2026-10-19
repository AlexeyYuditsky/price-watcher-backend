package alexeyyuditsky.config

import java.io.File
import java.util.Properties

data class BackendConfig(
    val profile: String,
    val host: String,
    val port: Int,
    val chromeProfile: File,
    val trustedProxies: Set<String> = emptySet()
) {
    companion object {
        fun load(environment: Map<String, String> = System.getenv()): BackendConfig {
            val profile = environment["BACKEND_PROFILE"] ?: when {
                System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "local"
                System.getProperty("os.name").equals("Linux", ignoreCase = true) -> "vps"
                else -> error("Set BACKEND_PROFILE to local or vps")
            }
            require(profile in setOf("local", "vps")) { "BACKEND_PROFILE must be local or vps" }
            val properties = Properties().apply {
                checkNotNull(BackendConfig::class.java.getResourceAsStream("/backend-$profile.properties")) {
                    "Backend profile configuration is missing: $profile"
                }.use { load(it) }
            }
            val port = (environment["PORT"] ?: properties.getProperty("server.port")).toInt()
            require(port in 1..65535) { "PORT must be between 1 and 65535" }
            val profilePath = environment["CHROME_PROFILE"] ?: properties.getProperty("chrome.profile")
            val chromeProfile = if (profilePath.startsWith("~/")) {
                File(System.getProperty("user.home"), profilePath.removePrefix("~/"))
            } else {
                File(profilePath)
            }
            return BackendConfig(
                profile = profile,
                host = environment["HOST"] ?: properties.getProperty("server.host"),
                port = port,
                chromeProfile = chromeProfile,
                trustedProxies = (environment["TRUSTED_PROXIES"]
                    ?: properties.getProperty("server.trusted-proxies", ""))
                    .split(",")
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSet()
            )
        }
    }
}