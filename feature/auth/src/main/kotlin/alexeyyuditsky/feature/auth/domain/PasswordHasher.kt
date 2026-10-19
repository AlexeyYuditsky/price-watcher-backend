package alexeyyuditsky.feature.auth.domain

interface PasswordHasher {
    fun hash(password: String): String
    fun verify(password: String, encoded: String): Boolean
}