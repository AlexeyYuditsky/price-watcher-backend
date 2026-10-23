package alexeyyuditsky.feature.ozon

import alexeyyuditsky.core.browser.BrowserCookie
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
        browserClient.loadPage(url = url, cookies = AGE_CONFIRMATION_COOKIES) { resolvedUrl, html ->
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

    private companion object {
        const val OZON_COOKIE_DOMAIN = ".ozon.ru"

        // What Ozon stores after the 18+ dialog; without it adult products show the age check instead of a price.
        val AGE_CONFIRMATION_COOKIES = listOf(
            BrowserCookie(name = "is_adult_confirmed", value = "true", domain = OZON_COOKIE_DOMAIN),
            BrowserCookie(name = "adult_user_birthdate", value = "2000-01-01", domain = OZON_COOKIE_DOMAIN)
        )
    }
}