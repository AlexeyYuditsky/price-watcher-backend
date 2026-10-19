package alexeyyuditsky.auth

import java.net.InetAddress

internal class AuthClientAddress(trustedProxies: Set<String>) {
    private val trusted = trustedProxies.mapNotNull(::numericAddress).toSet()

    fun resolve(remoteAddress: String, forwardedFor: String?): String {
        val remote = numericAddress(remoteAddress) ?: remoteAddress
        if (remote !in trusted) return remote
        val forwarded = forwardedFor?.substringAfterLast(',')?.trim()
        return forwarded?.let(::numericAddress) ?: remote
    }

    private fun numericAddress(value: String): String? {
        if (value.length !in 2..45 || !value.all { it in "0123456789abcdefABCDEF:." }) return null
        if (':' !in value) {
            val octets = value.split('.')
            if (octets.size != 4 || octets.any { it.isEmpty() || it.length > 3 || it.toIntOrNull() !in 0..255 }) {
                return null
            }
        }
        return try {
            InetAddress.getByName(value).hostAddress
        } catch (_: java.net.UnknownHostException) {
            null
        }
    }
}