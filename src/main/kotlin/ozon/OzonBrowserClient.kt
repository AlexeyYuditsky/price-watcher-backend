package alexeyyuditsky.ozon

import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.options.WaitUntilState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OzonBrowserClient {

    suspend fun loadProductPage(url: String): BrowserPage = withContext(Dispatchers.IO) {
        Playwright.create().use { playwright ->
            playwright.chromium().connectOverCDP(CDP_ENDPOINT).use { browser ->
                val context = browser.contexts().firstOrNull()
                    ?: error("Chrome did not provide a browser context")
                val page = context.pages().firstOrNull() ?: context.newPage()
                val productId = extractOzonProductId(url)
                var documentStatus: Int? = null
                page.onResponse { response ->
                    if (response.request().resourceType() == "document") {
                        documentStatus = response.status()
                    }
                }

                page.navigate(
                    url,
                    Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.LOAD)
                        .setTimeout(NAVIGATION_TIMEOUT_MS)
                )

                try {
                    page.waitForCondition(
                        {
                            val title = page.title()
                            !title.startsWith(LOADING_TITLE_PREFIX) && title.contains(productId)
                        },
                        Page.WaitForConditionOptions().setTimeout(CHALLENGE_TIMEOUT_MS)
                    )
                } catch (_: PlaywrightException) {
                    // Keep the last loaded page for diagnostics.
                }

                BrowserPage(
                    url = page.url(),
                    status = documentStatus,
                    title = page.title(),
                    html = page.content()
                )
            }
        }
    }

    private companion object {
        const val CDP_ENDPOINT = "http://127.0.0.1:9222"
        const val NAVIGATION_TIMEOUT_MS = 60_000.0
        const val CHALLENGE_TIMEOUT_MS = 60_000.0
        const val LOADING_TITLE_PREFIX = "Loading "
    }
}