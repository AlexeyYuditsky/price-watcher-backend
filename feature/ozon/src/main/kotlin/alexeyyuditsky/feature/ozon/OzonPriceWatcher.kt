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
    private val browserClient: OzonBrowserClient,
    private val priceParser: OzonPriceParser
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
                val page = browserClient.loadProductPage(url)
                check(
                    page.title.isNotBlank() &&
                        !page.title.startsWith("Loading ") && page.title != "Antibot Challenge Page"
                ) { "Ozon product page loading failed" }
                val parsing = TimeSource.Monotonic.markNow()
                val price = priceParser.parse(page.html)
                val requestedProductId = parseOzonProductUrl(url).productId
                val resolvedProductId = checkNotNull(parseOzonProductUrl(page.url).productId) {
                    "Ozon URL did not resolve to a product"
                }
                check(price.productId == resolvedProductId) { "Unexpected product ID" }
                check(requestedProductId == null || price.productId == requestedProductId) {
                    "Ozon redirected to a different product"
                }
                logger.info("Ozon product={} parseMs={}", price.productId, parsing.elapsedNow().inWholeMilliseconds)
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