package alexeyyuditsky.feature.tracking.domain

import java.math.BigDecimal
import java.time.Instant

// The price held from the check that first saw it until the next change or the latest check.
data class PricePeriod(
    val from: Instant,
    val to: Instant,
    val amount: BigDecimal,
    val unitAmount: BigDecimal?,
    val currency: String
)