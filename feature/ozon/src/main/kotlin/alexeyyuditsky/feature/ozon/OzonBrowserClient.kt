package alexeyyuditsky.feature.ozon

import alexeyyuditsky.core.browser.ChromeBrowserClient
import alexeyyuditsky.core.products.ProductPrice

class OzonBrowserClient(
    private val browserClient: ChromeBrowserClient,
    private val parser: OzonPriceParser
) : AutoCloseable {

    suspend fun loadProductPrice(url: String): ProductPrice {
        val productId = parseOzonProductUrl(url).productId
        var price: ProductPrice? = null
        // The page is accepted only after its product JSON matches the requested product,
        // so a title left over from the anti-bot check must not fail the request.
        browserClient.loadPage(url = url) { resolvedUrl, html ->
            val resolvedProductId = parseOzonProductUrl(resolvedUrl).productId
            price = if (
                resolvedProductId != null &&
                (productId == null || resolvedProductId == productId) &&
                parser.hasPriceWidget(html)
            ) {
                parser.parse(html).takeIf { it.productId == resolvedProductId }
            } else {
                null
            }
            price != null
        }
        return checkNotNull(price)
    }

    suspend fun resetConnection() = browserClient.resetConnection()

    override fun close() = browserClient.close()
}