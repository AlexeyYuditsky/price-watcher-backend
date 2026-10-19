package alexeyyuditsky.auth

import java.time.Clock
import java.time.Duration

internal class AuthRateLimiter(private val clock: Clock) {
    private val windows = mutableMapOf<String, MutableList<Long>>()

    @Synchronized
    fun allow(address: String): Boolean {
        val cutoff = clock.millis() - Duration.ofMinutes(1).toMillis()
        windows.entries.removeIf { entry ->
            entry.value.removeIf { it <= cutoff }
            entry.value.isEmpty()
        }
        val attempts = windows[address]
        if (attempts == null && windows.size >= 10_000) return false
        val current = attempts ?: mutableListOf<Long>().also { windows[address] = it }
        if (current.size >= 20) return false
        current.add(clock.millis())
        return true
    }
}