package alexeyyuditsky.feature.ozon

import alexeyyuditsky.core.browser.BrowserPage
import alexeyyuditsky.core.browser.ChromeBrowserClient

class OzonBrowserClient(
    private val browserClient: ChromeBrowserClient,
    private val parser: OzonPriceParser
) : AutoCloseable {

    suspend fun loadProductPage(url: String): BrowserPage {
        val productId = parseOzonProductUrl(url).productId
        return browserClient.loadPage(url = url) { resolvedUrl, html ->
            val resolvedProductId = parseOzonProductUrl(resolvedUrl).productId
            resolvedProductId != null &&
                (productId == null || resolvedProductId == productId) &&
                parser.parse(html).productId == resolvedProductId
        }
    }

    suspend fun resetConnection() = browserClient.resetConnection()

    override fun close() = browserClient.close()
}