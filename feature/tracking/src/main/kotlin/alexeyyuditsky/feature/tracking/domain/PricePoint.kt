package alexeyyuditsky.feature.tracking.domain

import java.math.BigDecimal
import java.time.Instant

data class PricePoint(
    val checkedAt: Instant,
    val amount: BigDecimal,
    // The price per piece for packs of several pieces.
    val unitAmount: BigDecimal?,
    val currency: String,
    // The price before the latest change, absent until the price has changed at least once.
    val previousAmount: BigDecimal?
)