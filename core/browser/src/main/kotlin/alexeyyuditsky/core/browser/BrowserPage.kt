package alexeyyuditsky.core.browser

data class BrowserPage(
    val url: String,
    val status: Int?,
    val title: String,
    val html: String
)