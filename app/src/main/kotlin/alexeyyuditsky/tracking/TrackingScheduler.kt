package alexeyyuditsky.tracking

import alexeyyuditsky.feature.tracking.domain.TrackingService
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

internal class TrackingScheduler(
    private val service: TrackingService,
    private val random: Random = Random.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logger = LoggerFactory.getLogger(TrackingScheduler::class.java)
    private var consecutiveFailures = 0
    private var backoffLevel = 0

    fun start() {
        scope.launch {
            while (isActive) {
                val due = try {
                    service.dueProducts(BATCH_SIZE)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    logger.error("Could not load products due for a price check", error)
                    emptyList()
                }
                if (due.isEmpty()) {
                    delay(IDLE_DELAY)
                    continue
                }
                for (product in due) {
                    if (service.checkScheduled(product)) {
                        consecutiveFailures = 0
                        backoffLevel = 0
                    } else if (++consecutiveFailures >= FAILURES_BEFORE_BACKOFF) {
                        backOff()
                        break
                    }
                    // Spacing out page loads makes background checks look less like a bot.
                    delay(random.nextLong(MIN_PAUSE.inWholeMilliseconds, MAX_PAUSE.inWholeMilliseconds))
                }
            }
        }
    }

    fun stop() = scope.cancel()

    // Several failures in a row usually mean Ozon throttles the server, so background checks pause.
    private suspend fun backOff() {
        val pause = (BASE_BACKOFF * (1 shl backoffLevel)).coerceAtMost(MAX_BACKOFF)
        logger.warn("Pausing scheduled price checks for {} after {} failures", pause, consecutiveFailures)
        consecutiveFailures = 0
        backoffLevel = (backoffLevel + 1).coerceAtMost(MAX_BACKOFF_LEVEL)
        delay(pause)
    }

    private companion object {
        const val BATCH_SIZE = 20
        const val FAILURES_BEFORE_BACKOFF = 3
        const val MAX_BACKOFF_LEVEL = 3
        val IDLE_DELAY: Duration = 1.minutes
        val MIN_PAUSE: Duration = 10.seconds
        val MAX_PAUSE: Duration = 20.seconds
        val BASE_BACKOFF: Duration = 15.minutes
        val MAX_BACKOFF: Duration = 2.hours
    }
}