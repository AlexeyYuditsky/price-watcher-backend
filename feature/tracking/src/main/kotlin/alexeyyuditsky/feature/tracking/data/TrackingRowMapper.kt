package alexeyyuditsky.feature.tracking.data

import alexeyyuditsky.feature.tracking.domain.PricePeriod
import alexeyyuditsky.feature.tracking.domain.PricePoint
import alexeyyuditsky.feature.tracking.domain.TrackedProduct
import java.sql.ResultSet
import java.util.UUID

internal class TrackingRowMapper {
    fun trackedProduct(row: ResultSet): TrackedProduct = TrackedProduct(
        id = row.getObject("id", UUID::class.java),
        url = row.getString("url"),
        title = row.getString("title"),
        imageUrl = row.getString("image_url"),
        latestPrice = row.getTimestamp("checked_at")?.let { pricePoint(row) }
    )

    fun pricePeriod(row: ResultSet): PricePeriod = PricePeriod(
        from = row.getTimestamp("started_at").toInstant(),
        to = row.getTimestamp("ended_at").toInstant(),
        amount = row.getBigDecimal("amount"),
        unitAmount = row.getBigDecimal("unit_amount"),
        currency = row.getString("currency")
    )

    private fun pricePoint(row: ResultSet): PricePoint = PricePoint(
        checkedAt = row.getTimestamp("checked_at").toInstant(),
        amount = row.getBigDecimal("amount"),
        unitAmount = row.getBigDecimal("unit_amount"),
        currency = row.getString("currency"),
        previousAmount = row.getBigDecimal("previous_amount")
    )
}