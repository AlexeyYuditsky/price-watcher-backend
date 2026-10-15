package alexeyyuditsky

import alexeyyuditsky.ozon.OzonBrowserClient
import alexeyyuditsky.ozon.OzonPriceParser
import alexeyyuditsky.ozon.parseOzonProductUrl
import alexeyyuditsky.ozon.model.ProductPrice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.time.TimeSource

class LocalPriceWatcher(
    private val chromeLauncher: ChromeLauncher = when {
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> LocalChromeLauncher()
        System.getProperty("os.name").equals("Linux", ignoreCase = true) -> VpsChromeLauncher()
        else -> error("Unsupported operating system")
    },
    private val browserClient: OzonBrowserClient = OzonBrowserClient(),
    private val priceParser: OzonPriceParser = OzonPriceParser()
) : AutoCloseable {
    private val mutex = Mutex()
    private val logger = LoggerFactory.getLogger(LocalPriceWatcher::class.java)

    suspend fun getPrice(url: String): ProductPrice {
        val total = TimeSource.Monotonic.markNow()
        try {
            return mutex.withLock {
                logger.info("Price request queueMs={}", total.elapsedNow().inWholeMilliseconds)
                val startup = TimeSource.Monotonic.markNow()
                val chromeStarted = withContext(Dispatchers.IO) { chromeLauncher.ensureStarted() }
                if (chromeStarted) browserClient.resetConnection()
                logger.info("Chrome startupMs={}", startup.elapsedNow().inWholeMilliseconds)
                val page = browserClient.loadProductPage(url)
                check(page.isSuccessful) { "Ozon product page loading failed" }
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

    override fun close() = browserClient.close()
}