package alexeyyuditsky.core.products

import java.math.BigDecimal

data class ProductPrice(
    val productId: String,
    val regularAmount: BigDecimal,
    val bankAmount: BigDecimal?,
    val currency: String,
    val availability: String?,
    val title: String? = null,
    val imageUrl: String? = null
) {
    val amount: BigDecimal
        get() = bankAmount ?: regularAmount
}