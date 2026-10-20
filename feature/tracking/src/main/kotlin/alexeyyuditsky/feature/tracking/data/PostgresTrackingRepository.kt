package alexeyyuditsky.feature.tracking.data

import alexeyyuditsky.core.database.DatabasePool
import alexeyyuditsky.core.products.ProductPrice
import alexeyyuditsky.feature.tracking.domain.DueProduct
import alexeyyuditsky.feature.tracking.domain.PricePoint
import alexeyyuditsky.feature.tracking.domain.ProductSnapshot
import alexeyyuditsky.feature.tracking.domain.TrackedProduct
import alexeyyuditsky.feature.tracking.domain.TrackingRepository
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresTrackingRepository(private val pool: DatabasePool) : TrackingRepository {
    private val mapper = TrackingRowMapper()

    override fun saveCheck(
        product: ProductSnapshot,
        price: ProductPrice,
        checkedAt: Instant,
        nextCheckAt: Instant
    ): UUID = transaction { connection ->
        val productId = connection.prepareStatement(
            "INSERT INTO products(marketplace, external_id, url, title, image_url, next_check_at) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (marketplace, external_id) DO UPDATE SET " +
                "url = EXCLUDED.url, title = COALESCE(EXCLUDED.title, products.title), " +
                "image_url = COALESCE(EXCLUDED.image_url, products.image_url), " +
                "next_check_at = EXCLUDED.next_check_at RETURNING id"
        ).use { statement ->
            statement.setString(1, product.marketplace)
            statement.setString(2, product.externalId)
            statement.setString(3, product.url)
            statement.setString(4, product.title)
            statement.setString(5, product.imageUrl)
            statement.setTimestamp(6, Timestamp.from(nextCheckAt))
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getObject("id", UUID::class.java)
            }
        }
        connection.prepareStatement(
            "INSERT INTO price_checks(product_id, checked_at, amount, regular_amount, bank_amount, " +
                "currency, availability) VALUES (?, ?, ?, ?, ?, ?, ?)"
        ).use { statement ->
            statement.setObject(1, productId)
            statement.setTimestamp(2, Timestamp.from(checkedAt))
            statement.setBigDecimal(3, price.amount)
            statement.setBigDecimal(4, price.regularAmount)
            statement.setBigDecimal(5, price.bankAmount)
            statement.setString(6, price.currency)
            statement.setString(7, price.availability)
            statement.executeUpdate()
        }
        productId
    }

    override fun track(userId: UUID, productId: UUID, now: Instant) {
        pool.open().use { connection ->
            connection.prepareStatement(
                "INSERT INTO tracked_products(user_id, product_id, created_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT DO NOTHING"
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setObject(2, productId)
                statement.setTimestamp(3, Timestamp.from(now))
                statement.executeUpdate()
            }
        }
    }

    override fun untrack(userId: UUID, productId: UUID): Boolean = pool.open().use { connection ->
        connection.prepareStatement("DELETE FROM tracked_products WHERE user_id = ? AND product_id = ?")
            .use { statement ->
                statement.setObject(1, userId)
                statement.setObject(2, productId)
                statement.executeUpdate() > 0
            }
    }

    override fun list(userId: UUID): List<TrackedProduct> = pool.open().use { connection ->
        connection.prepareStatement("$TRACKED_PRODUCTS_QUERY ORDER BY t.created_at DESC").use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(mapper.trackedProduct(rows)) }
            }
        }
    }

    override fun find(userId: UUID, productId: UUID): TrackedProduct? = pool.open().use { connection ->
        connection.prepareStatement("$TRACKED_PRODUCTS_QUERY AND p.id = ?").use { statement ->
            statement.setObject(1, userId)
            statement.setObject(2, productId)
            statement.executeQuery().use { rows ->
                if (rows.next()) mapper.trackedProduct(rows) else null
            }
        }
    }

    override fun history(userId: UUID, productId: UUID, since: Instant): List<PricePoint> =
        pool.open().use { connection ->
            connection.prepareStatement(
                "SELECT c.checked_at, c.amount, c.currency FROM price_checks c " +
                    "JOIN tracked_products t ON t.product_id = c.product_id " +
                    "WHERE t.user_id = ? AND c.product_id = ? AND c.checked_at >= ? " +
                    "ORDER BY c.checked_at DESC"
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setObject(2, productId)
                statement.setTimestamp(3, Timestamp.from(since))
                statement.executeQuery().use { rows ->
                    buildList { while (rows.next()) add(mapper.pricePoint(rows)) }
                }
            }
        }

    override fun dueProducts(now: Instant, limit: Int): List<DueProduct> = pool.open().use { connection ->
        connection.prepareStatement(
            "SELECT p.id, p.url FROM products p WHERE p.next_check_at <= ? " +
                "AND EXISTS (SELECT 1 FROM tracked_products t WHERE t.product_id = p.id) " +
                "ORDER BY p.next_check_at LIMIT ?"
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(now))
            statement.setInt(2, limit)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(DueProduct(rows.getObject("id", UUID::class.java), rows.getString("url")))
                    }
                }
            }
        }
    }

    override fun scheduleNext(productId: UUID, at: Instant) {
        pool.open().use { connection ->
            connection.prepareStatement("UPDATE products SET next_check_at = ? WHERE id = ?").use { statement ->
                statement.setTimestamp(1, Timestamp.from(at))
                statement.setObject(2, productId)
                statement.executeUpdate()
            }
        }
    }

    private fun <T> transaction(block: (Connection) -> T): T = pool.open().use { connection ->
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (error: Exception) {
            connection.rollback()
            throw error
        }
    }

    private companion object {
        const val TRACKED_PRODUCTS_QUERY =
            "SELECT p.id, p.url, p.title, p.image_url, c.checked_at, c.amount, c.currency " +
                "FROM tracked_products t JOIN products p ON p.id = t.product_id " +
                "LEFT JOIN LATERAL (SELECT checked_at, amount, currency FROM price_checks " +
                "WHERE product_id = p.id ORDER BY checked_at DESC LIMIT 1) c ON TRUE " +
                "WHERE t.user_id = ?"
    }
}