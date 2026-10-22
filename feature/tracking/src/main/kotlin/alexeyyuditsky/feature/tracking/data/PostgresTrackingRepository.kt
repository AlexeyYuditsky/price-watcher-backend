package alexeyyuditsky.feature.tracking.data

import alexeyyuditsky.core.database.DatabasePool
import alexeyyuditsky.core.products.ProductPrice
import alexeyyuditsky.feature.tracking.domain.DueProduct
import alexeyyuditsky.feature.tracking.domain.PricePeriod
import alexeyyuditsky.feature.tracking.domain.ProductSnapshot
import alexeyyuditsky.feature.tracking.domain.TrackedProduct
import alexeyyuditsky.feature.tracking.domain.TrackingRepository
import java.sql.Connection
import java.sql.Timestamp
import java.sql.Types
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
        // The upsert locks the product row, so concurrent checks of one product compare prices in turn.
        val productId = connection.prepareStatement(
            "INSERT INTO products(marketplace, external_id, url, title, image_url, units_per_pack, " +
                "last_checked_at, next_check_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (marketplace, external_id) DO UPDATE SET " +
                "url = EXCLUDED.url, title = COALESCE(EXCLUDED.title, products.title), " +
                "image_url = COALESCE(EXCLUDED.image_url, products.image_url), " +
                "units_per_pack = COALESCE(EXCLUDED.units_per_pack, products.units_per_pack), " +
                "last_checked_at = GREATEST(EXCLUDED.last_checked_at, products.last_checked_at), " +
                "next_check_at = EXCLUDED.next_check_at RETURNING id"
        ).use { statement ->
            statement.setString(1, product.marketplace)
            statement.setString(2, product.externalId)
            statement.setString(3, product.url)
            statement.setString(4, product.title)
            statement.setString(5, product.imageUrl)
            statement.setObject(6, product.unitsPerPack, Types.INTEGER)
            statement.setTimestamp(7, Timestamp.from(checkedAt))
            statement.setTimestamp(8, Timestamp.from(nextCheckAt))
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getObject("id", UUID::class.java)
            }
        }
        // History grows only when the price differs from the latest saved one.
        connection.prepareStatement(
            "INSERT INTO price_changes(product_id, changed_at, amount, currency) " +
                "SELECT ?, ?::timestamptz, ?::numeric, ?::text WHERE NOT EXISTS (" +
                "SELECT 1 FROM (SELECT changed_at, amount, currency FROM price_changes WHERE product_id = ? " +
                "ORDER BY changed_at DESC LIMIT 1) latest " +
                "WHERE (latest.amount = ?::numeric AND latest.currency = ?::text) " +
                "OR latest.changed_at >= ?::timestamptz)"
        ).use { statement ->
            statement.setObject(1, productId)
            statement.setTimestamp(2, Timestamp.from(checkedAt))
            statement.setBigDecimal(3, price.amount)
            statement.setString(4, price.currency)
            statement.setObject(5, productId)
            statement.setBigDecimal(6, price.amount)
            statement.setString(7, price.currency)
            statement.setTimestamp(8, Timestamp.from(checkedAt))
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

    override fun untrack(userId: UUID, productId: UUID): Boolean = transaction { connection ->
        val removed = connection.prepareStatement("DELETE FROM tracked_products WHERE user_id = ? AND product_id = ?")
            .use { statement ->
                statement.setObject(1, userId)
                statement.setObject(2, productId)
                statement.executeUpdate() > 0
            }
        if (removed) deleteUntracked(connection, productId)
        removed
    }

    override fun deleteIfUntracked(productId: UUID) {
        pool.open().use { connection -> deleteUntracked(connection, productId) }
    }

    // The price history goes with the product through ON DELETE CASCADE.
    private fun deleteUntracked(connection: Connection, productId: UUID) {
        connection.prepareStatement(
            "DELETE FROM products p WHERE p.id = ? " +
                "AND NOT EXISTS (SELECT 1 FROM tracked_products t WHERE t.product_id = p.id)"
        ).use { statement ->
            statement.setObject(1, productId)
            statement.executeUpdate()
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

    override fun history(userId: UUID, productId: UUID): List<PricePeriod> =
        pool.open().use { connection ->
            connection.prepareStatement(
                "SELECT c.changed_at AS started_at, COALESCE(lead(c.changed_at) OVER (ORDER BY c.changed_at), " +
                    "p.last_checked_at, c.changed_at) AS ended_at, c.amount, $UNIT_AMOUNT, c.currency " +
                    "FROM price_changes c " +
                    "JOIN products p ON p.id = c.product_id " +
                    "JOIN tracked_products t ON t.product_id = c.product_id " +
                    "WHERE t.user_id = ? AND c.product_id = ? " +
                    "ORDER BY c.changed_at DESC"
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setObject(2, productId)
                statement.executeQuery().use { rows ->
                    buildList { while (rows.next()) add(mapper.pricePeriod(rows)) }
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
        const val UNIT_AMOUNT = "round(c.amount / p.units_per_pack, 2) AS unit_amount"
        // The previous price is the change before the latest one; a price in another currency is not comparable.
        const val TRACKED_PRODUCTS_QUERY =
            "SELECT p.id, p.url, p.title, p.image_url, p.last_checked_at AS checked_at, c.amount, " +
                "$UNIT_AMOUNT, c.currency, " +
                "CASE WHEN prev.currency = c.currency THEN prev.amount END AS previous_amount " +
                "FROM tracked_products t JOIN products p ON p.id = t.product_id " +
                "LEFT JOIN LATERAL (SELECT amount, currency FROM price_changes " +
                "WHERE product_id = p.id ORDER BY changed_at DESC LIMIT 1) c ON TRUE " +
                "LEFT JOIN LATERAL (SELECT amount, currency FROM price_changes " +
                "WHERE product_id = p.id ORDER BY changed_at DESC OFFSET 1 LIMIT 1) prev ON TRUE " +
                "WHERE t.user_id = ?"
    }
}