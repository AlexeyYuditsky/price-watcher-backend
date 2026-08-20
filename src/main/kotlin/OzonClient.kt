package alexeyyuditsky

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

class OzonClient {

    private val client = HttpClient(CIO) {
        followRedirects = false
    }

    suspend fun loadProductPage(url: String): String {
        val response = client.get(url) {
            header(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/151.0.0.0 Safari/537.36"
            )
        }

        println("STATUS = ${response.status}")
        println("LOCATION = ${response.headers[HttpHeaders.Location]}")

        return response.body()
    }
}