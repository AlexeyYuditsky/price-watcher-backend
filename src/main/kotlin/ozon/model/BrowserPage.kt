package alexeyyuditsky.ozon.model

data class BrowserPage(
    val url: String,
    val status: Int?,
    val title: String,
    val html: String
) {
    val isSuccessful: Boolean
        get() = status in 200..299 &&
                title.isNotBlank() &&
                !title.startsWith(LOADING_TITLE_PREFIX) &&
                title != ANTIBOT_TITLE

    private companion object {
        const val ANTIBOT_TITLE = "Antibot Challenge Page"
        const val LOADING_TITLE_PREFIX = "Loading "
    }
}