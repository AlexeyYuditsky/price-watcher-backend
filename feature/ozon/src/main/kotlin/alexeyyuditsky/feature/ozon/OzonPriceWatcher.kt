package alexeyyuditsky.feature.ozon

import alexeyyuditsky.core.browser.ChromeLauncher
import alexeyyuditsky.core.products.ProductPrice
import alexeyyuditsky.core.products.ProductPriceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.time.TimeSource

class OzonPriceWatcher(
    private val chromeLauncher: ChromeLauncher,
    private val browserClient: OzonBrowserClient
) : ProductPriceSource, AutoCloseable {
    private val mutex = Mutex()
    private val logger = LoggerFactory.getLogger(OzonPriceWatcher::class.java)

    override suspend fun getPrice(url: String): ProductPrice {
        val total = TimeSource.Monotonic.markNow()
        try {
            return mutex.withLock {
                logger.info("Price request queueMs={}", total.elapsedNow().inWholeMilliseconds)
                val startup = TimeSource.Monotonic.markNow()
                val chromeStarted = withContext(Dispatchers.IO) { chromeLauncher.ensureStarted() }
                if (chromeStarted) browserClient.resetConnection()
                logger.info("Chrome startupMs={}", startup.elapsedNow().inWholeMilliseconds)
                val price = browserClient.loadProductPrice(url)
                logger.info("Ozon product={}", price.productId)
                price
            }
        } finally {
            logger.info("Price request totalMs={}", total.elapsedNow().inWholeMilliseconds)
        }
    }

    suspend fun ensureBrowserReady() {
        mutex.withLock {
            val chromeStarted = withContext(Dispatchers.IO) { chromeLauncher.ensureStarted() }
            if (chromeStarted) browserClient.resetConnection()
        }
    }

    override fun close() = browserClient.close()
}