package alexeyyuditsky.feature.auth.domain

import java.time.Instant
import java.util.UUID

interface AuthRepository {
    fun register(email: String, passwordHash: String, session: SessionHashes, now: Instant): AuthUser?
    fun findCredentials(email: String): AccountCredentials?
    fun createSession(userId: UUID, session: SessionHashes, now: Instant)
    fun rotateSession(refreshHash: String, replacement: SessionHashes, now: Instant): AuthUser?
    fun findUser(accessHash: String, now: Instant): AuthUser?
    fun revokeSession(accessHash: String, now: Instant)
}