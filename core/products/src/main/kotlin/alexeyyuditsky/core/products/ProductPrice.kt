package alexeyyuditsky.core.products

import java.math.BigDecimal

data class ProductPrice(
    val productId: String,
    val amount: BigDecimal,
    val currency: String,
    // Set only for packs of several pieces; the price per piece is amount divided by it.
    val unitsPerPack: Int? = null,
    val title: String? = null,
    val imageUrl: String? = null
)