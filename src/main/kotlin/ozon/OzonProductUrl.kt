package alexeyyuditsky.ozon

import java.net.URI

data class OzonProductUrl(val url: String, val productId: String?)

fun parseOzonProductUrl(value: String): OzonProductUrl {
    val uri = URI(value.trim())
    require(
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.lowercase() in setOf("ozon.ru", "www.ozon.ru") &&
            uri.port in setOf(-1, 443) && uri.rawUserInfo == null
    ) { "Expected an HTTPS Ozon URL" }
    val path = uri.rawPath.orEmpty()
    return when {
        PRODUCT_PATH_REGEX.matches(path) -> OzonProductUrl(
            url = "https://www.ozon.ru${path.trimEnd('/')}/",
            productId = extractOzonProductId(path)
        )
        SHORT_PATH_REGEX.matches(path) -> OzonProductUrl(
            url = "https://ozon.ru${path.trimEnd('/')}",
            productId = null
        )
        else -> throw IllegalArgumentException("Expected an Ozon product or short URL")
    }
}

fun extractOzonProductId(productUrl: String): String =
    PRODUCT_ID_REGEX.find(productUrl)?.groupValues?.get(1)
        ?: error("Cannot extract Ozon product ID from URL: $productUrl")

private val PRODUCT_ID_REGEX = Regex("[-/](\\d{6,})(?:/|\\?|$)")
private val PRODUCT_PATH_REGEX = Regex("^/product/[^/]*\\d{6,}/?$")
private val SHORT_PATH_REGEX = Regex("^/t/[A-Za-z0-9]+/?$")