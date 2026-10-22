package alexeyyuditsky.tracking

import alexeyyuditsky.feature.tracking.domain.PricePeriod
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

    fun history(periods: List<PricePeriod>): JsonObject = buildJsonObject {
        put("periods", JsonArray(periods.map(::pricePeriod)))
    }

    private fun pricePeriod(period: PricePeriod): JsonObject = buildJsonObject {
        put("from", period.from.toString())
        put("to", period.to.toString())
        put("amount", JsonPrimitive(period.amount))
        put("unitAmount", period.unitAmount?.let(::JsonPrimitive) ?: JsonNull)
        put("currency", period.currency)
    }

    private fun pricePoint(point: PricePoint): JsonObject = buildJsonObject {
        put("checkedAt", point.checkedAt.toString())
        put("amount", JsonPrimitive(point.amount))
        put("unitAmount", point.unitAmount?.let(::JsonPrimitive) ?: JsonNull)
        put("currency", point.currency)
        put("previousAmount", point.previousAmount?.let(::JsonPrimitive) ?: JsonNull)
    }
}