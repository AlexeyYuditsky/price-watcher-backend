package alexeyyuditsky

import alexeyyuditsky.auth.AuthRateLimiter
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
import alexeyyuditsky.feature.tracking.data.PostgresTrackingRepository
import alexeyyuditsky.feature.tracking.domain.TrackingService
import alexeyyuditsky.tracking.TrackingScheduler
import alexeyyuditsky.tracking.configureTrackingRouting
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import java.security.SecureRandom
import java.time.Clock

fun Application.rootModule(config: BackendConfig, database: DatabaseConnection) {
    val clock = Clock.systemUTC()
    val pool = DatabasePool(database)
    monitor.subscribe(ApplicationStopped) { if (it === this) pool.close() }
    val random = SecureRandom()
    val authService = AuthService(
        PostgresAuthRepository(pool),
        Pbkdf2PasswordHasher(random),
        SecureTokenGenerator(random),
        clock
    )
    configureAuthRouting(authService, clock, trustedProxies = config.trustedProxies)
    val launcher = when (config.profile) {
        "local" -> LocalChromeLauncher(config.chromeProfile)
        "vps" -> VpsChromeLauncher(config.chromeProfile)
        else -> error("Unsupported backend profile: ${config.profile}")
    }
    val priceWatcher = OzonPriceWatcher(
        chromeLauncher = launcher,
        browserClient = OzonBrowserClient(ChromeBrowserClient(), OzonPriceParser())
    )
    monitor.subscribe(ApplicationStopped) { if (it === this) priceWatcher.close() }
    val authenticator = CallAuthenticator(authService)
    val guard = PriceRequestGuard(
        AuthRateLimiter(clock, maxRequests = 30),
        PriceRequestQueue(capacity = 15)
    )
    configureRouting(priceWatcher, authenticator, guard)
    val trackingService = TrackingService(
        repository = PostgresTrackingRepository(pool),
        priceSource = priceWatcher,
        clock = clock,
        marketplace = "ozon",
        productUrl = { externalId -> "https://www.ozon.ru/product/$externalId/" }
    )
    configureTrackingRouting(trackingService, authenticator, guard)
    val scheduler = TrackingScheduler(trackingService)
    monitor.subscribe(ApplicationStarted) { if (it === this) scheduler.start() }
    monitor.subscribe(ApplicationStopped) { if (it === this) scheduler.stop() }
    configureHealthRouting(pool::isHealthy, priceWatcher::ensureBrowserReady)
}