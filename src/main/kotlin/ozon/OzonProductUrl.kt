package alexeyyuditsky.ozon

fun extractOzonProductId(productUrl: String): String =
    PRODUCT_ID_REGEX.find(productUrl)?.groupValues?.get(1)
        ?: error("Cannot extract Ozon product ID from URL: $productUrl")

private val PRODUCT_ID_REGEX = Regex("[-/](\\d{6,})(?:/|\\?|$)")