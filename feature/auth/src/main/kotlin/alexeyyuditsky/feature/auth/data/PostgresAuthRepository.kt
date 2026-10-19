package alexeyyuditsky.feature.auth.data

import alexeyyuditsky.core.database.DatabasePool
import alexeyyuditsky.feature.auth.domain.AccountCredentials
import alexeyyuditsky.feature.auth.domain.AuthRepository
import alexeyyuditsky.feature.auth.domain.AuthUser
import alexeyyuditsky.feature.auth.domain.SessionHashes
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresAuthRepository(private val pool: DatabasePool) : AuthRepository {
    private val mapper = StoredAccountMapper()

    override fun register(email: String, passwordHash: String, session: SessionHashes, now: Instant): AuthUser? =
        transaction { connection ->
            val user = connection.prepareStatement(
                "INSERT INTO users(email, password_hash, created_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT (email) DO NOTHING RETURNING *"
            ).use { statement ->
                statement.setString(1, email)
                statement.setString(2, passwordHash)
                statement.setTimestamp(3, Timestamp.from(now))
                statement.executeQuery().use { rows ->
                    if (rows.next()) mapper.user(mapper.read(rows)) else null
                }
            }
            if (user != null) insertSession(connection, user.id, session, now)
            user
        }

    override fun findCredentials(email: String): AccountCredentials? = pool.open().use { connection ->
        connection.prepareStatement("SELECT * FROM users WHERE email = ?").use { statement ->
            statement.setString(1, email)
            statement.executeQuery().use { rows ->
                if (rows.next()) mapper.credentials(mapper.read(rows)) else null
            }
        }
    }

    override fun createSession(userId: UUID, session: SessionHashes, now: Instant) {
        pool.open().use { insertSession(it, userId, session, now) }
    }

    override fun rotateSession(refreshHash: String, replacement: SessionHashes, now: Instant): AuthUser? =
        transaction { connection ->
            val userId = connection.prepareStatement(
                "UPDATE sessions SET access_token_hash = ?, refresh_token_hash = ?, access_expires_at = ?, " +
                    "expires_at = ? WHERE refresh_token_hash = ? AND revoked_at IS NULL AND expires_at > ? " +
                    "RETURNING user_id"
            ).use { statement ->
                statement.setString(1, replacement.accessHash)
                statement.setString(2, replacement.refreshHash)
                statement.setTimestamp(3, Timestamp.from(replacement.accessExpiresAt))
                statement.setTimestamp(4, Timestamp.from(replacement.refreshExpiresAt))
                statement.setString(5, refreshHash)
                statement.setTimestamp(6, Timestamp.from(now))
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.getObject("user_id", UUID::class.java) else null
                }
            } ?: return@transaction null
            connection.prepareStatement("SELECT * FROM users WHERE id = ?").use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { rows ->
                    if (rows.next()) mapper.user(mapper.read(rows)) else null
                }
            }
        }

    override fun findUser(accessHash: String, now: Instant): AuthUser? = pool.open().use { connection ->
        connection.prepareStatement(
            "SELECT u.* FROM users u JOIN sessions s ON s.user_id = u.id " +
                "WHERE s.access_token_hash = ? AND s.revoked_at IS NULL " +
                "AND s.access_expires_at > ? AND s.expires_at > ?"
        ).use { statement ->
            statement.setString(1, accessHash)
            statement.setTimestamp(2, Timestamp.from(now))
            statement.setTimestamp(3, Timestamp.from(now))
            statement.executeQuery().use { rows ->
                if (rows.next()) mapper.user(mapper.read(rows)) else null
            }
        }
    }

    override fun revokeSession(accessHash: String, now: Instant) {
        pool.open().use { connection ->
            connection.prepareStatement(
                "UPDATE sessions SET revoked_at = ? WHERE access_token_hash = ? AND revoked_at IS NULL"
            ).use { statement ->
                statement.setTimestamp(1, Timestamp.from(now))
                statement.setString(2, accessHash)
                statement.executeUpdate()
            }
        }
    }

    private fun insertSession(connection: Connection, userId: UUID, session: SessionHashes, now: Instant) {
        connection.prepareStatement(
            "DELETE FROM sessions WHERE user_id = ? AND (expires_at <= ? OR revoked_at IS NOT NULL)"
        ).use { statement ->
            statement.setObject(1, userId)
            statement.setTimestamp(2, Timestamp.from(now))
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO sessions(user_id, refresh_token_hash, access_token_hash, access_expires_at, " +
                "created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)"
        ).use { statement ->
            statement.setObject(1, userId)
            statement.setString(2, session.refreshHash)
            statement.setString(3, session.accessHash)
            statement.setTimestamp(4, Timestamp.from(session.accessExpiresAt))
            statement.setTimestamp(5, Timestamp.from(now))
            statement.setTimestamp(6, Timestamp.from(session.refreshExpiresAt))
            statement.executeUpdate()
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
}