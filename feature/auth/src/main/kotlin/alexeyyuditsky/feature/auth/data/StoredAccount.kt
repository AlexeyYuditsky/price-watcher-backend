package alexeyyuditsky.feature.auth.data

import java.time.Instant
import java.util.UUID

internal class StoredAccount(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val emailVerifiedAt: Instant?,
    val createdAt: Instant
)