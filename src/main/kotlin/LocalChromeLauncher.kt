package alexeyyuditsky

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class LocalChromeLauncher {

    suspend fun ensureStarted() {
        if (isCdpAvailable()) {
            return
        }

        val chrome = findChrome()
            ?: error("Google Chrome was not found on this computer")
        val profile = File("files/ozon-cdp-profile").absoluteFile
        profile.mkdirs()

        withContext(Dispatchers.IO) {
            ProcessBuilder(
                chrome.absolutePath,
                "--remote-debugging-port=9222",
                "--remote-debugging-address=127.0.0.1",
                "--user-data-dir=${profile.path}",
                "--no-first-run",
                "--no-default-browser-check"
            ).start()
        }

        repeat(CDP_START_ATTEMPTS) {
            if (isCdpAvailable()) {
                return
            }
            delay(CDP_RETRY_DELAY_MS.milliseconds)
        }

        error("Chrome started, but CDP did not become available at 127.0.0.1:9222")
    }

    private fun isCdpAvailable(): Boolean {
        val request = HttpRequest.newBuilder(CDP_VERSION_URI)
            .timeout(CDP_REQUEST_TIMEOUT)
            .GET()
            .build()

        return runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200
        }.getOrDefault(false)
    }

    private fun findChrome(): File? = chromeCandidates()
        .firstOrNull(File::isFile)

    private fun chromeCandidates(): Sequence<File> = sequenceOf(
        System.getenv("PROGRAMFILES"),
        System.getenv("PROGRAMFILES(X86)"),
        System.getenv("LOCALAPPDATA")
    ).filterNotNull()
        .map { File(it, "Google/Chrome/Application/chrome.exe") }

    private companion object {
        val CDP_VERSION_URI: URI = URI("http://127.0.0.1:9222/json/version")
        val CDP_REQUEST_TIMEOUT: Duration = Duration.ofSeconds(1)
        val httpClient: HttpClient = HttpClient.newBuilder()
            .connectTimeout(CDP_REQUEST_TIMEOUT)
            .build()
        const val CDP_START_ATTEMPTS = 40
        const val CDP_RETRY_DELAY_MS = 250L
    }
}