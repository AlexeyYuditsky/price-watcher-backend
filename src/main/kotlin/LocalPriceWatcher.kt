package alexeyyuditsky

import alexeyyuditsky.ozon.OzonBrowserClient
import alexeyyuditsky.ozon.OzonPriceParser
import alexeyyuditsky.ozon.extractOzonProductId
import alexeyyuditsky.ozon.requireOzonProductUrl
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LocalPriceWatcher(
    private val chromeLauncher: LocalChromeLauncher = LocalChromeLauncher(),
    private val browserClient: OzonBrowserClient = OzonBrowserClient(),
    private val priceParser: OzonPriceParser = OzonPriceParser()
) {

    suspend fun run() {
        chromeLauncher.ensureStarted()

        println("Local Ozon price watcher")
        println("Chrome CDP is available at 127.0.0.1:9222")
        println("Enter 'exit' or 'выход' to stop")

        while (true) {
            print("Ozon product URL: ")
            System.out.flush()

            val input = readlnOrNull()?.trim() ?: break
            if (input.equals("exit", ignoreCase = true) ||
                input.equals("выход", ignoreCase = true)
            ) {
                break
            }
            if (input.isEmpty()) {
                continue
            }

            process(input)
        }
    }

    private suspend fun process(input: String) {
        val productUrl = try {
            requireOzonProductUrl(input)
        } catch (error: IllegalArgumentException) {
            println("Invalid URL: ${error.message}")
            return
        }

        try {
            val page = browserClient.loadProductPage(productUrl)

            println("Final URL: ${page.url}")
            println("Status: ${page.status}")
            println("Title: ${page.title}")

            if (!page.isSuccessful) {
                println("Product page loading failed")
                return
            }

            val price = priceParser.parse(page.html)
            val outputFile = createOutputFile(extractOzonProductId(productUrl))
            outputFile.parentFile.mkdirs()
            outputFile.writeText(page.html)

            println("Product price: ${price.amount} ${price.currency}")
            println("Regular price: ${price.regularAmount} ${price.currency}")
            println("HTML saved: ${outputFile.path}")
        } catch (error: Exception) {
            println("Product processing failed: ${error.message}")
        }
    }

    private fun createOutputFile(productId: String): File {
        val timestamp = FILE_TIMESTAMP_FORMAT.format(LocalDateTime.now())
        return File("files/ozon-$productId-$timestamp.html")
    }

    private companion object {
        val FILE_TIMESTAMP_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    }
}