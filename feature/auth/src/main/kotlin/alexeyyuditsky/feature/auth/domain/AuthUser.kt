package alexeyyuditsky.feature.auth.domain

import java.time.Instant
import java.util.UUID

data class AuthUser(
    val id: UUID,
    val email: String,
    val emailVerifiedAt: Instant?,
    val createdAt: Instant
)