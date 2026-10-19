package alexeyyuditsky.auth

import alexeyyuditsky.feature.auth.domain.AuthError
import alexeyyuditsky.feature.auth.domain.AuthException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class AuthRequestMapper {
    fun credentials(body: String): CredentialsRequest {
        val json = parse(body)
        return CredentialsRequest(string(json, "email"), string(json, "password"))
    }

    fun refreshToken(body: String): String = string(parse(body), "refreshToken")

    private fun parse(body: String): JsonObject = try {
        Json.parseToJsonElement(body) as? JsonObject ?: invalid()
    } catch (_: IllegalArgumentException) {
        invalid()
    }

    private fun string(json: JsonObject, key: String): String =
        (json[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid()

    private fun invalid(): Nothing = throw AuthException(AuthError.INVALID_INPUT, "Invalid JSON body")
}