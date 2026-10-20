package alexeyyuditsky.feature.tracking.domain

data class ProductSnapshot(
    val marketplace: String,
    val externalId: String,
    val url: String,
    val title: String?,
    val imageUrl: String?
)