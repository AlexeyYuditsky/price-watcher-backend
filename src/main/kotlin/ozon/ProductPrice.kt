package alexeyyuditsky.ozon

import java.math.BigDecimal

data class ProductPrice(
    val productId: String,
    val regularAmount: BigDecimal,
    val bankAmount: BigDecimal?,
    val currency: String,
    val availability: String?
) {
    val amount: BigDecimal
        get() = bankAmount ?: regularAmount
}