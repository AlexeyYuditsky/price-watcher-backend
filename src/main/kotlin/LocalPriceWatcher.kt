package alexeyyuditsky

import alexeyyuditsky.ozon.OzonBrowserClient
import alexeyyuditsky.ozon.OzonPriceParser
import alexeyyuditsky.ozon.model.ProductPrice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalPriceWatcher(
    private val chromeLauncher: LocalChromeLauncher = LocalChromeLauncher(),
    private val browserClient: OzonBrowserClient = OzonBrowserClient(),
    private val priceParser: OzonPriceParser = OzonPriceParser()
) {
    private val mutex = Mutex()

    suspend fun getPrice(url: String): ProductPrice = mutex.withLock {
        withContext(Dispatchers.IO) {
            chromeLauncher.ensureStarted()
        }
        val page = browserClient.loadProductPage(url)
        check(page.isSuccessful) { "Ozon product page loading failed" }
        priceParser.parse(page.html)
    }
}