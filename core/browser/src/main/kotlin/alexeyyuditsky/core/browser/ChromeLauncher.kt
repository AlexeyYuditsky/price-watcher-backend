package alexeyyuditsky.core.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

abstract class ChromeLauncher(protected val profileDirectory: File) {

    private var process: Process? = null
    private val logger = LoggerFactory.getLogger(ChromeLauncher::class.java)

    suspend fun ensureStarted(): Boolean {
        if (isCdpAvailable()) return false

        val chrome = findChrome() ?: error("Google Chrome was not found on this computer")
        val profile = profileDirectory.absoluteFile
        profile.mkdirs()

        withContext(Dispatchers.IO) {
            // A Chrome of ours that is alive but deaf to CDP still holds the profile, so it goes first.
            process?.takeIf { it.isAlive }?.let(::stop)
            // Chrome's own log goes to a file: an unread pipe would block Chrome once it fills up.
            val log = File(profile, CHROME_LOG)
            if (log.isFile) log.copyTo(File(profile, CHROME_PREVIOUS_LOG), overwrite = true)
            process = ProcessBuilder(
                chrome.absolutePath,
                "--remote-debugging-port=9222",
                "--remote-debugging-address=127.0.0.1",
                "--user-data-dir=${profile.path}",
                "--no-first-run",
                "--no-default-browser-check",
                "--enable-logging=stderr",
                "--log-level=1"
            ).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(log))
                .start()
        }

        repeat(CDP_START_ATTEMPTS) {
            if (isCdpAvailable()) return true
            delay(CDP_RETRY_DELAY_MS.milliseconds)
        }

        error("Chrome started, but CDP did not become available at 127.0.0.1:9222")
    }

    // Used when Chrome stops answering CDP; only a Chrome started by this backend can be stopped.
    suspend fun restart() {
        withContext(Dispatchers.IO) {
            val running = process
            if (running == null) {
                logger.warn("Chrome was not started by this backend and cannot be restarted automatically")
                return@withContext
            }
            stop(running)
        }
        ensureStarted()
    }

    private fun stop(running: Process) {
        logger.warn("Stopping Chrome pid={}", running.pid())
        running.descendants().forEach { it.destroyForcibly() }
        running.destroyForcibly()
        running.waitFor(CHROME_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        process = null
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

    protected abstract fun findChrome(): File?

    private companion object {
        val CDP_VERSION_URI: URI = URI("http://127.0.0.1:9222/json/version")
        val CDP_REQUEST_TIMEOUT: Duration = Duration.ofSeconds(1)
        val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(CDP_REQUEST_TIMEOUT).build()
        const val CDP_START_ATTEMPTS = 40
        const val CDP_RETRY_DELAY_MS = 250L
        const val CHROME_STOP_TIMEOUT_SECONDS = 10L
        const val CHROME_LOG = "chrome.log"
        const val CHROME_PREVIOUS_LOG = "chrome.previous.log"
    }
}