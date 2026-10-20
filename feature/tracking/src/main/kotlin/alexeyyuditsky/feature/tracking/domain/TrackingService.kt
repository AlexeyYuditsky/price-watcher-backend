package alexeyyuditsky.feature.tracking.domain

import alexeyyuditsky.core.products.ProductPrice
import alexeyyuditsky.core.products.ProductPriceSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class TrackingService(
    private val repository: TrackingRepository,
    private val priceSource: ProductPriceSource,
    private val clock: Clock,
    private val marketplace: String,
    private val productUrl: (externalId: String) -> String,
    private val random: Random = Random.Default
) {
    private val logger = LoggerFactory.getLogger(TrackingService::class.java)

    suspend fun add(userId: UUID, url: String): TrackedProduct {
        val productId = check(url)
        return io {
            repository.track(userId, productId, clock.instant())
            checkNotNull(repository.find(userId, productId)) { "Tracked product disappeared" }
        }
    }

    suspend fun list(userId: UUID): List<TrackedProduct> = io { repository.list(userId) }

    suspend fun remove(userId: UUID, productId: UUID): Boolean = io { repository.untrack(userId, productId) }

    suspend fun history(userId: UUID, productId: UUID): List<PricePoint>? = io {
        repository.find(userId, productId)?.let {
            repository.history(userId, productId, clock.instant().minus(HISTORY_PERIOD))
        }
    }

    suspend fun refresh(userId: UUID, productId: UUID): TrackedProduct? {
        val product = io { repository.find(userId, productId) } ?: return null
        check(product.url)
        return io { repository.find(userId, productId) }
    }

    suspend fun dueProducts(limit: Int): List<DueProduct> = io { repository.dueProducts(clock.instant(), limit) }

    // Returns false when the check failed; the product is then retried after a normal interval.
    suspend fun checkScheduled(product: DueProduct): Boolean = try {
        check(product.url)
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        logger.warn("Scheduled price check failed product={}: {}", product.id, error.message)
        io { repository.scheduleNext(product.id, nextCheckAt()) }
        false
    }

    private suspend fun check(url: String): UUID {
        val price = priceSource.getPrice(url)
        val now = clock.instant()
        return io { repository.saveCheck(snapshot(price), price, now, nextCheckAt()) }
    }

    // A random spread keeps products from being checked in bursts at fixed times.
    private fun nextCheckAt(): Instant = clock.instant()
        .plus(MIN_CHECK_INTERVAL)
        .plusSeconds(random.nextLong(CHECK_SPREAD.seconds + 1))

    private fun snapshot(price: ProductPrice) = ProductSnapshot(
        marketplace = marketplace,
        externalId = price.productId,
        url = productUrl(price.productId),
        title = price.title,
        imageUrl = price.imageUrl
    )

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        val MIN_CHECK_INTERVAL: Duration = Duration.ofMinutes(30)
        val CHECK_SPREAD: Duration = Duration.ofMinutes(15)
        val HISTORY_PERIOD: Duration = Duration.ofDays(30)
    }
}