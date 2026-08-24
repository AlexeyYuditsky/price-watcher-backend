package alexeyyuditsky.ozon

import java.net.URI

fun requireOzonProductUrl(rawUrl: String): String {
    val url = runCatching { URI(rawUrl) }
        .getOrElse { error("Invalid product URL: $rawUrl") }
    val host = url.host?.lowercase()

    require(url.scheme.equals(HTTPS_SCHEME, ignoreCase = true)) {
        "Ozon product URL must use HTTPS"
    }
    require(host == OZON_HOST || host?.endsWith(".$OZON_HOST") == true) {
        "Only ozon.ru URLs are allowed"
    }
    require(url.path.startsWith(PRODUCT_PATH)) {
        "URL must point to an Ozon product page"
    }
    extractOzonProductId(rawUrl)

    return rawUrl
}

fun extractOzonProductId(productUrl: String): String =
    PRODUCT_ID_REGEX.find(productUrl)?.groupValues?.get(1)
        ?: error("Cannot extract Ozon product ID from URL: $productUrl")

private const val HTTPS_SCHEME = "https"
private const val OZON_HOST = "ozon.ru"
private const val PRODUCT_PATH = "/product/"
private val PRODUCT_ID_REGEX = Regex("[-/](\\d{6,})(?:/|\\?|$)")