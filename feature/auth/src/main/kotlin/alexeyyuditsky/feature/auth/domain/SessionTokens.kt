package alexeyyuditsky.feature.auth.domain

import java.time.Instant

class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Instant,
    val refreshExpiresAt: Instant
)