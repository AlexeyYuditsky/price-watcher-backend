package alexeyyuditsky.ozon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OzonProductUrlTest {

    @Test
    fun `accepts Ozon product URL`() {
        val url = "https://www.ozon.ru/product/example-3013490077/?from_sku=3013490077"

        assertEquals(url, requireOzonProductUrl(url))
        assertEquals("3013490077", extractOzonProductId(url))
    }

    @Test
    fun `rejects non Ozon URL`() {
        assertFailsWith<IllegalArgumentException> {
            requireOzonProductUrl("https://example.com/product/3013490077")
        }
    }
}