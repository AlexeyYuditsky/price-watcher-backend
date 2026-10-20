package alexeyyuditsky.feature.tracking.domain

import alexeyyuditsky.core.products.ProductPrice
import java.time.Instant
import java.util.UUID

interface TrackingRepository {
    fun saveCheck(product: ProductSnapshot, price: ProductPrice, checkedAt: Instant, nextCheckAt: Instant): UUID
    fun track(userId: UUID, productId: UUID, now: Instant)
    fun untrack(userId: UUID, productId: UUID): Boolean
    fun list(userId: UUID): List<TrackedProduct>
    fun find(userId: UUID, productId: UUID): TrackedProduct?
    fun history(userId: UUID, productId: UUID, since: Instant): List<PricePoint>
    fun dueProducts(now: Instant, limit: Int): List<DueProduct>
    fun scheduleNext(productId: UUID, at: Instant)
}