package alexeyyuditsky.ozon

import alexeyyuditsky.ozon.model.BrowserPage
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.Response
import com.microsoft.playwright.options.WaitUntilState
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.function.Consumer
import kotlin.time.TimeSource

class OzonBrowserClient : AutoCloseable {
    private val dispatcher = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ozon-browser").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private var playwright: Playwright? = null
    private var browser: Browser? = null
    private var page: Page? = null
    private val parser = OzonPriceParser()
    private val logger = LoggerFactory.getLogger(OzonBrowserClient::class.java)

    suspend fun loadProductPage(url: String): BrowserPage = withContext(dispatcher) {
        val productId = parseOzonProductUrl(url).productId
        val total = TimeSource.Monotonic.markNow()
        try {
            val connection = TimeSource.Monotonic.markNow()
            val driver = playwright ?: Playwright.create(
                Playwright.CreateOptions().setEnv(
                    System.getenv() + ("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD" to "1")
                )
            ).also { playwright = it }
            val connectedBrowser = browser?.takeIf { it.isConnected }
                ?: driver.chromium().connectOverCDP(CDP_ENDPOINT).also {
                    browser = it
                    page = null
                }
            val context = connectedBrowser.contexts().firstOrNull()
                ?: error("Chrome did not provide a browser context")
            val productPage = page?.takeUnless { it.isClosed }
                ?: (context.pages().firstOrNull {
                    it.url() in setOf("about:blank", "chrome://newtab/", "chrome://new-tab-page/")
                } ?: context.newPage()).also { page = it }
            logger.info("Ozon product={} connectMs={}", productId, connection.elapsedNow().inWholeMilliseconds)

            var documentStatus: Int? = null
            val responseHandler = Consumer<Response> { response ->
                if (response.request().isNavigationRequest && response.frame() == productPage.mainFrame()) {
                    documentStatus = response.status()
                }
            }
            productPage.onResponse(responseHandler)
            try {
                val navigation = TimeSource.Monotonic.markNow()
                productPage.navigate(
                    url,
                    Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD)
                        .setTimeout(60_000.0)
                )
                logger.info("Ozon product={} navigateMs={}", productId, navigation.elapsedNow().inWholeMilliseconds)
                val ready = TimeSource.Monotonic.markNow()
                productPage.waitForCondition(
                    {
                        runCatching {
                            val resolvedProductId = parseOzonProductUrl(productPage.url()).productId
                            resolvedProductId != null &&
                                (productId == null || resolvedProductId == productId) &&
                                parser.parse(productPage.content()).productId == resolvedProductId
                        }.getOrDefault(false)
                    },
                    Page.WaitForConditionOptions().setTimeout(60_000.0)
                )
                logger.info("Ozon product={} readyMs={}", productId, ready.elapsedNow().inWholeMilliseconds)
                BrowserPage(productPage.url(), documentStatus, productPage.title(), productPage.content())
            } finally {
                productPage.offResponse(responseHandler)
            }
        } catch (error: PlaywrightException) {
            logger.warn("Ozon product={} browser operation failed: {}", productId, error.message)
            page = null
            browser = null
            throw error
        } finally {
            logger.info("Ozon product={} browserTotalMs={}", productId, total.elapsedNow().inWholeMilliseconds)
        }
    }

    suspend fun resetConnection() = withContext(dispatcher) {
        page = null
        browser = null
        val driver = playwright
        playwright = null
        driver?.close()
    }

    override fun close() {
        try {
            runBlocking {
                withContext(dispatcher) {
                    try {
                        page?.close()
                    } finally {
                        playwright?.close()
                    }
                }
            }
        } finally {
            dispatcher.close()
        }
    }

    private companion object {
        const val CDP_ENDPOINT = "http://127.0.0.1:9222"
    }
}