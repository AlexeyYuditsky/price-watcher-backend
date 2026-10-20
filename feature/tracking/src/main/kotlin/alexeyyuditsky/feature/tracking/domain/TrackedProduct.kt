package alexeyyuditsky.feature.tracking.domain

import java.util.UUID

data class TrackedProduct(
    val id: UUID,
    val url: String,
    val title: String?,
    val imageUrl: String?,
    val latestPrice: PricePoint?
)