package alexeyyuditsky.core.browser

data class BrowserCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/"
)