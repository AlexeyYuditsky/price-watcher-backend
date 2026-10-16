package alexeyyuditsky.core.products

interface ProductPriceSource {
    suspend fun getPrice(url: String): ProductPrice
}