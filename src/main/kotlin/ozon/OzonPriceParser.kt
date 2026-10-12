package alexeyyuditsky.ozon

import alexeyyuditsky.ozon.model.ProductPrice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal

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
        val bankAmount = parseBankAmount(html)

        return ProductPrice(
            productId = product.requiredString("sku"),
            regularAmount = price,
            bankAmount = bankAmount,
            currency = offer.requiredString("priceCurrency"),
            availability = offer.optionalString("availability")
        )
    }

    private fun parseBankAmount(html: String): BigDecimal? {
        val encodedState = PRICE_STATE_REGEX.find(html)?.groupValues?.get(1) ?: return null
        val state = json.parseToJsonElement(encodedState.decodeHtmlAttribute()) as? JsonObject
            ?: error("Ozon price state is not a JSON object")

        return state.optionalString("cardPrice")?.toDisplayedPrice()
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

