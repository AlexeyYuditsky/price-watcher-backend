package alexeyyuditsky.core.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.Response
import com.microsoft.playwright.options.WaitUntilState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.function.Consumer
import kotlin.time.TimeSource

class ChromeBrowserClient : AutoCloseable {

    private val dispatcher = Executors.newSingleThreadExecutor { task ->
        Thread(task, "chrome-browser")
            .apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private var playwright: Playwright? = null
    private var browser: Browser? = null
    private var page: Page? = null
    private val logger = LoggerFactory.getLogger(ChromeBrowserClient::class.java)

    suspend fun loadPage(
        url: String,
        isFailure: (BrowserPage) -> Boolean = { false },
        isReady: (String, String) -> Boolean
    ): BrowserPage = withContext(dispatcher) {
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
            logger.info("Browser url={} connectMs={}", url, connection.elapsedNow().inWholeMilliseconds)

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
                    Page.NavigateOptions().setWaitUntil(WaitUntilState.COMMIT)
                        .setTimeout(60_000.0)
                )
                logger.info("Browser url={} navigateMs={}", url, navigation.elapsedNow().inWholeMilliseconds)
                val ready = TimeSource.Monotonic.markNow()
                var loadedPage: BrowserPage? = null
                productPage.waitForCondition(
                    {
                        // Anti-bot checks answer 403 first and reload the page once the browser passes them.
                        val snapshot = try {
                            BrowserPage(productPage.url(), documentStatus, productPage.title(), productPage.content())
                        } catch (error: PlaywrightException) {
                            if (productPage.isClosed) throw error
                            null
                        }
                        if (snapshot == null) {
                            false
                        } else {
                            check(!isFailure(snapshot)) { "Product page reported a terminal error" }
                            val readyToParse = try {
                                isReady(snapshot.url, snapshot.html)
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                false
                            }
                            if (readyToParse) loadedPage = snapshot
                            readyToParse
                        }
                    },
                    Page.WaitForConditionOptions().setTimeout(15_000.0)
                )
                logger.info(
                    "Browser url={} readyMs={} status={}",
                    url,
                    ready.elapsedNow().inWholeMilliseconds,
                    documentStatus
                )
                checkNotNull(loadedPage)
            } finally {
                productPage.offResponse(responseHandler)
            }
        } catch (error: PlaywrightException) {
            logger.warn("Browser url={} browser operation failed: {}", url, error.message)
            page = null
            browser = null
            throw error
        } finally {
            logger.info("Browser url={} browserTotalMs={}", url, total.elapsedNow().inWholeMilliseconds)
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
        dispatcher.use { dispatcher ->
            runBlocking {
                withContext(dispatcher) {
                    playwright.use {
                        page?.close()
                    }
                }
            }
        }
    }

    private companion object {
        const val CDP_ENDPOINT = "http://127.0.0.1:9222"
    }
}