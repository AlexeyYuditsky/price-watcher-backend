package alexeyyuditsky.feature.auth.domain

import java.time.Instant

class SessionHashes(
    val accessHash: String,
    val refreshHash: String,
    val accessExpiresAt: Instant,
    val refreshExpiresAt: Instant
)