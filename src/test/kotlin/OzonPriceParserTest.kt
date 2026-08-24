import alexeyyuditsky.ozon.OzonPriceParser
import alexeyyuditsky.ozon.ProductPrice
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class OzonPriceParserTest {

    @Test
    fun `parses product price from JSON-LD`() {
        val html = """
            <html>
            <script type="application/ld+json">
                {"@type":"BreadcrumbList"}
            </script>
            <script nonce="nonce" type="application/ld+json">
                {
                    "@context": "http://schema.org",
                    "@type": "Product",
                    "offers": {
                        "@type": "Offer",
                        "availability": "http://schema.org/InStock",
                        "price": "2490",
                        "priceCurrency": "RUB"
                    },
                    "sku": "3013490077"
                }
            </script>
            <div id="state-webPrice-3121879-default-1"
                 data-state="{&quot;cardPrice&quot;:&quot;2 465 ₽&quot;,&quot;price&quot;:&quot;2 490 ₽&quot;}"></div>
            </html>
        """.trimIndent()

        val result = OzonPriceParser().parse(html)

        assertEquals(
            ProductPrice(
                productId = "3013490077",
                regularAmount = BigDecimal("2490"),
                bankAmount = BigDecimal("2465"),
                currency = "RUB",
                availability = "http://schema.org/InStock"
            ),
            result
        )
        assertEquals(BigDecimal("2465"), result.amount)
    }
}