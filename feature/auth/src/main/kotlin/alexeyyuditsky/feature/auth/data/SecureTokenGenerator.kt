package alexeyyuditsky.feature.auth.data

import alexeyyuditsky.feature.auth.domain.TokenGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

class SecureTokenGenerator(private val random: SecureRandom) : TokenGenerator {
    override fun generate(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    override fun hash(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)))
}