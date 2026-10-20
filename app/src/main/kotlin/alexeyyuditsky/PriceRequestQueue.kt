package alexeyyuditsky

import java.util.concurrent.atomic.AtomicInteger

internal class PriceRequestQueue(private val capacity: Int) {
    private val inFlight = AtomicInteger()

    fun tryEnter(): Boolean {
        while (true) {
            val current = inFlight.get()
            if (current >= capacity) return false
            if (inFlight.compareAndSet(current, current + 1)) return true
        }
    }

    fun leave() {
        inFlight.decrementAndGet()
    }
}