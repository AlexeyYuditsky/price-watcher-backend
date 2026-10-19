package alexeyyuditsky.auth

import alexeyyuditsky.feature.auth.domain.AuthSession
import alexeyyuditsky.feature.auth.domain.AuthUser
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class AuthResponseMapper {
    fun user(user: AuthUser): JsonObject = buildJsonObject {
        put("id", user.id.toString())
        put("email", user.email)
        put("emailVerified", user.emailVerifiedAt != null)
        put("createdAt", user.createdAt.toString())
    }

    fun session(session: AuthSession): JsonObject = buildJsonObject {
        put("user", user(session.user))
        put("tokenType", "Bearer")
        put("accessToken", session.tokens.accessToken)
        put("refreshToken", session.tokens.refreshToken)
        put("accessExpiresAt", session.tokens.accessExpiresAt.toString())
        put("refreshExpiresAt", session.tokens.refreshExpiresAt.toString())
    }
}