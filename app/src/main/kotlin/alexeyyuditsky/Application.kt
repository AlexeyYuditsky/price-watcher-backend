package alexeyyuditsky

import alexeyyuditsky.config.BackendConfig
import alexeyyuditsky.core.browser.ChromeBrowserClient
import alexeyyuditsky.core.browser.LocalChromeLauncher
import alexeyyuditsky.core.browser.VpsChromeLauncher
import alexeyyuditsky.feature.ozon.OzonBrowserClient
import alexeyyuditsky.feature.ozon.OzonPriceParser
import alexeyyuditsky.feature.ozon.OzonPriceWatcher
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped

fun Application.rootModule(config: BackendConfig) {
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
    monitor.subscribe(ApplicationStopped) { priceWatcher.close() }
    configureRouting(priceWatcher)
}