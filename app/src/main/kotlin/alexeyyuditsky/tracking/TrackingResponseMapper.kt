package alexeyyuditsky.tracking

import alexeyyuditsky.feature.tracking.domain.PricePoint
import alexeyyuditsky.feature.tracking.domain.TrackedProduct
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class TrackingResponseMapper {
    fun product(product: TrackedProduct): JsonObject = buildJsonObject {
        put("id", product.id.toString())
        put("url", product.url)
        put("title", product.title)
        put("imageUrl", product.imageUrl)
        put("latestPrice", product.latestPrice?.let(::pricePoint) ?: JsonNull)
    }

    fun products(products: List<TrackedProduct>): JsonObject = buildJsonObject {
        put("products", buildJsonArray { products.forEach { add(product(it)) } })
    }

    fun history(points: List<PricePoint>): JsonObject = buildJsonObject {
        put("points", JsonArray(points.map(::pricePoint)))
    }

    private fun pricePoint(point: PricePoint): JsonObject = buildJsonObject {
        put("checkedAt", point.checkedAt.toString())
        put("amount", JsonPrimitive(point.amount))
        put("currency", point.currency)
    }
}