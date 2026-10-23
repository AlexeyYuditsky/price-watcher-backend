package alexeyyuditsky.core.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.Response
import com.microsoft.playwright.options.Cookie
import com.microsoft.playwright.options.WaitUntilState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.function.Consumer
import kotlin.time.TimeSource

class ChromeBrowserClient(private val chromeLauncher: ChromeLauncher) : AutoCloseable {

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
        cookies: List<BrowserCookie> = emptyList(),
        isFailure: (BrowserPage) -> Boolean = { false },
        isReady: (String, String) -> Boolean
    ): BrowserPage = withContext(dispatcher) {
        val total = TimeSource.Monotonic.markNow()
        try {
            val connection = TimeSource.Monotonic.markNow()
            val connectedBrowser = browser?.takeIf { it.isConnected }
                ?: connect().also {
                    browser = it
                    page = null
                }
            val context = connectedBrowser.contexts().firstOrNull()
                ?: error("Chrome did not provide a browser context")
            // Set on every load: session cookies are gone after Chrome restarts and sites may reset them.
            if (cookies.isNotEmpty()) {
                context.addCookies(
                    cookies.map { cookie ->
                        Cookie(cookie.name, cookie.value)
                            .setDomain(cookie.domain)
                            .setPath(cookie.path)
                    }
                )
            }
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
            logTargets()
            // A stuck Playwright connection survives a plain reconnect, so the driver is recreated too.
            closeDriver()
            throw error
        } finally {
            logger.info("Browser url={} browserTotalMs={}", url, total.elapsedNow().inWholeMilliseconds)
        }
    }

    private suspend fun connect(): Browser = try {
        connectOnce()
    } catch (error: PlaywrightException) {
        logger.warn("Chrome connection failed, restarting Chrome: {}", error.message)
        logTargets()
        closeDriver()
        chromeLauncher.restart()
        connectOnce()
    }

    private fun connectOnce(): Browser {
        val driver = playwright ?: Playwright.create(
            Playwright.CreateOptions().setEnv(
                System.getenv() + ("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD" to "1")
            )
        ).also { playwright = it }
        return driver.chromium().connectOverCDP(
            CDP_ENDPOINT,
            BrowserType.ConnectOverCDPOptions().setTimeout(CONNECT_TIMEOUT_MS)
        )
    }

    private fun closeDriver() {
        page = null
        browser = null
        val driver = playwright
        playwright = null
        runCatching { driver?.close() }
            .onFailure { logger.warn("Playwright driver did not close cleanly: {}", it.message) }
    }

    // Tabs and service workers show what Chrome was busy with when an operation failed.
    private fun logTargets() {
        val request = HttpRequest.newBuilder(URI("$CDP_ENDPOINT/json/list"))
            .timeout(TARGETS_TIMEOUT)
            .GET()
            .build()
        val targets = try {
            targetsClient.send(request, HttpResponse.BodyHandlers.ofString()).body()
        } catch (error: Exception) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            logger.warn("Chrome targets are unavailable: {}", error.toString())
            return
        }
        val summary = TARGET_REGEX.findAll(targets).joinToString(separator = "; ") { match ->
            val type = TYPE_REGEX.find(match.value)?.groupValues?.get(1)
            val targetUrl = URL_REGEX.find(match.value)?.groupValues?.get(1)?.take(MAX_URL_LENGTH)
            "$type $targetUrl"
        }
        logger.warn("Chrome targets: {}", summary.ifEmpty { "none" })
    }

    suspend fun resetConnection() = withContext(dispatcher) {
        closeDriver()
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
        const val CONNECT_TIMEOUT_MS = 30_000.0
        const val MAX_URL_LENGTH = 150
        val TARGETS_TIMEOUT: Duration = Duration.ofSeconds(3)
        val targetsClient: HttpClient = HttpClient.newBuilder().connectTimeout(TARGETS_TIMEOUT).build()
        val TARGET_REGEX = Regex("\\{[^{}]*\\}")
        val TYPE_REGEX = Regex("\"type\"\\s*:\\s*\"([^\"]*)\"")
        val URL_REGEX = Regex("\"url\"\\s*:\\s*\"([^\"]*)\"")
    }
}