package alexeyyuditsky.feature.auth.data

import alexeyyuditsky.feature.auth.domain.PasswordHasher
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class Pbkdf2PasswordHasher(private val random: SecureRandom) : PasswordHasher {
    override fun hash(password: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val encoder = Base64.getEncoder()
        return listOf(
            "pbkdf2-sha256",
            "600000",
            encoder.encodeToString(salt),
            encoder.encodeToString(derive(password, salt, 600_000))
        ).joinToString(":")
    }

    override fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split(":")
        if (parts.size != 4 || parts[0] != "pbkdf2-sha256") return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it in 600_000..2_000_000 } ?: return false
        return try {
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            salt.size == 16 && expected.size == 32 &&
                MessageDigest.isEqual(expected, derive(password, salt, iterations))
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}