package alexeyyuditsky

import alexeyyuditsky.auth.configureAuthRouting
import alexeyyuditsky.config.BackendConfig
import alexeyyuditsky.core.browser.ChromeBrowserClient
import alexeyyuditsky.core.browser.LocalChromeLauncher
import alexeyyuditsky.core.browser.VpsChromeLauncher
import alexeyyuditsky.core.database.DatabaseConnection
import alexeyyuditsky.core.database.DatabasePool
import alexeyyuditsky.feature.auth.data.Pbkdf2PasswordHasher
import alexeyyuditsky.feature.auth.data.PostgresAuthRepository
import alexeyyuditsky.feature.auth.data.SecureTokenGenerator
import alexeyyuditsky.feature.auth.domain.AuthService
import alexeyyuditsky.feature.ozon.OzonBrowserClient
import alexeyyuditsky.feature.ozon.OzonPriceParser
import alexeyyuditsky.feature.ozon.OzonPriceWatcher
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import java.security.SecureRandom
import java.time.Clock

fun Application.rootModule(config: BackendConfig, database: DatabaseConnection? = null) {
    val clock = Clock.systemUTC()
    val pool = database?.let(::DatabasePool)
    val authService = pool?.let {
        monitor.subscribe(ApplicationStopped) { app -> if (app === this) pool.close() }
        val random = SecureRandom()
        AuthService(
            PostgresAuthRepository(pool),
            Pbkdf2PasswordHasher(random),
            SecureTokenGenerator(random),
            clock
        )
    }
    if (authService != null) {
        configureAuthRouting(authService, clock, trustedProxies = config.trustedProxies)
    }
    val launcher = when (config.profile) {
        "local" -> LocalChromeLauncher(config.chromeProfile)
        "vps" -> VpsChromeLauncher(config.chromeProfile)
        else -> error("Unsupported backend profile: ${config.profile}")
    }
    val parser = OzonPriceParser()
    val priceWatcher = OzonPriceWatcher(
        chromeLauncher = launcher,
        browserClient = OzonBrowserClient(ChromeBrowserClient(config.chromeProfile), parser),
        priceParser = parser
    )
    monitor.subscribe(ApplicationStopped) { if (it === this) priceWatcher.close() }
    configureRouting(priceWatcher, authService, clock)
    configureHealthRouting(pool?.let { it::isHealthy }, priceWatcher::ensureBrowserReady)
}