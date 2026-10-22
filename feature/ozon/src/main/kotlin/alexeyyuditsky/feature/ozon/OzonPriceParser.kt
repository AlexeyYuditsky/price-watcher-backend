package alexeyyuditsky.feature.ozon

import alexeyyuditsky.core.products.ProductPrice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal
import java.math.RoundingMode

class OzonPriceParser(
    private val json: Json = Json
) {

    fun parse(html: String): ProductPrice {
        val product = SCRIPT_REGEX.findAll(html)
            .mapNotNull { match ->
                runCatching {
                    json.parseToJsonElement(match.groupValues[1])
                }.getOrNull()
            }.firstNotNullOfOrNull { it.findProduct() }
            ?: error("Product JSON-LD was not found")

        val offer = product["offers"].findOffer()
            ?: error("Product offer was not found in JSON-LD")
        val price = offer.requiredString("price").toBigDecimalOrNull()
            ?: error("Product price is not a valid decimal number")
        val priceState = parsePriceState(html)
        // The Ozon Card price is what a buyer pays; without it the regular price is used.
        val amount = (priceState?.optionalString("cardPrice") ?: priceState?.optionalString("price"))
            ?.toDisplayedPrice()
            ?: price
        val title = product.optionalString("name")

        return ProductPrice(
            productId = product.requiredString("sku"),
            amount = amount,
            currency = offer.requiredString("priceCurrency"),
            unitsPerPack = priceState?.let { unitsPerPack(it, amount, title) },
            title = title,
            imageUrl = product["image"].findImageUrl()?.let(::normalizeOzonImageUrl)
        )
    }

    private fun JsonElement?.findImageUrl(): String? = when (this) {
        is JsonPrimitive -> contentOrNull?.takeIf { it.startsWith("https://") }
        is JsonArray -> firstNotNullOfOrNull { it.findImageUrl() }
        is JsonObject -> (get("url") ?: get("contentUrl")).findImageUrl()
        else -> null
    }

    // The price widget is rendered separately from the JSON-LD and holds the green Ozon Card price.
    fun hasPriceWidget(html: String): Boolean = PRICE_STATE_REGEX.containsMatchIn(html)

    private fun parsePriceState(html: String): JsonObject? {
        val encodedState = PRICE_STATE_REGEX.find(html)?.groupValues?.get(1) ?: return null
        return json.parseToJsonElement(encodedState.decodeHtmlAttribute()) as? JsonObject
            ?: error("Ozon price state is not a JSON object")
    }

    // Ozon shows a price per piece rounded to roubles; the pack size is recovered from it,
    // preferring a piece count from the title that agrees with that rounded price.
    private fun unitsPerPack(priceState: JsonObject, amount: BigDecimal, title: String?): Int? {
        val measure = priceState.optionalString("measurePerUnit") ?: return null
        if (!PER_PIECE_REGEX.matches(measure.trim())) return null
        val unitPrice = priceState.optionalString("pricePerUnit")?.toDisplayedPrice()
            ?.takeIf { it.signum() > 0 }
            ?: return null
        val fromTitle = title?.let { PIECES_REGEX.findAll(it) }
            ?.mapNotNull { it.groupValues[1].toIntOrNull() }
            ?.firstOrNull { pieces ->
                pieces > 1 && (amount.divide(pieces.toBigDecimal(), 2, RoundingMode.HALF_UP) - unitPrice).abs() <=
                    BigDecimal.ONE
            }
        val estimate = amount.divide(unitPrice, 3, RoundingMode.HALF_UP)
        val rounded = estimate.setScale(0, RoundingMode.HALF_UP)
        val fromPrice = rounded.toInt().takeIf { (estimate - rounded).abs() <= PIECES_TOLERANCE }
        return (fromTitle ?: fromPrice)?.takeIf { it > 1 }
    }

    private fun String.toDisplayedPrice(): BigDecimal =
        filter { it.isDigit() || it == ',' || it == '.' }
            .replace(',', '.')
            .toBigDecimalOrNull()
            ?: error("Displayed product price is not a valid decimal number: $this")

    private fun String.decodeHtmlAttribute(): String =
        replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")

    private fun JsonElement.findProduct(): JsonObject? = when (this) {
        is JsonObject -> {
            if (optionalString("@type") == PRODUCT_TYPE) {
                this
            } else {
                values.firstNotNullOfOrNull { it.findProduct() }
            }
        }

        is JsonArray -> firstNotNullOfOrNull { it.findProduct() }
        else -> null
    }

    private fun JsonElement?.findOffer(): JsonObject? = when (this) {
        is JsonObject -> takeIf { "price" in it }
            ?: values.firstNotNullOfOrNull { it.findOffer() }

        is JsonArray -> firstNotNullOfOrNull { it.findOffer() }
        else -> null
    }

    private fun JsonObject.requiredString(name: String): String =
        optionalString(name) ?: error("Required JSON-LD field '$name' was not found")

    private fun JsonObject.optionalString(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

    private companion object {
        const val PRODUCT_TYPE = "Product"
        val PIECES_TOLERANCE = BigDecimal("0.1")
        val PER_PIECE_REGEX = Regex("""за[\s ]*1[\s ]*шт\.?""", RegexOption.IGNORE_CASE)
        val PIECES_REGEX = Regex("""(\d+)[\s ]*шт""", RegexOption.IGNORE_CASE)
        val SCRIPT_REGEX = Regex(
            pattern = """<script\b[^>]*\btype=["']application/ld\+json["'][^>]*>(.*?)</script>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val PRICE_STATE_REGEX = Regex(
            pattern = """<div\b[^>]*\bid=["']state-webPrice-[^"']*["'][^>]*\bdata-state=["']([^"']*)["'][^>]*>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
    }
}