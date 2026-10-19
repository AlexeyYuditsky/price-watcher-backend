package alexeyyuditsky.feature.auth.domain

interface TokenGenerator {
    fun generate(): String
    fun hash(token: String): String
}