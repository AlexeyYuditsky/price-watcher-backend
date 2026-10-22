package alexeyyuditsky.feature.ozon

import java.net.URI

// Ozon pages may point images to regional CDN hosts (ir-20.ozone.ru) that some networks cannot
// reach; the main image host serves the same /s3/ paths, so clients always get a reachable URL.
fun normalizeOzonImageUrl(value: String): String {
    val uri = runCatching { URI(value) }.getOrNull() ?: return value
    val host = uri.host?.lowercase() ?: return value
    val path = uri.rawPath.orEmpty()
    if (!uri.scheme.equals("https", ignoreCase = true) || !host.endsWith(".ozone.ru") || !path.startsWith("/s3/")) {
        return value
    }
    return "https://$IMAGE_HOST$path" + uri.rawQuery?.let { "?$it" }.orEmpty()
}

private const val IMAGE_HOST = "ir.ozone.ru"