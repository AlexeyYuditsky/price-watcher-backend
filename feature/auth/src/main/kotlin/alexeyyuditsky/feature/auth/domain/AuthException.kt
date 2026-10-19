package alexeyyuditsky.feature.auth.domain

class AuthException(val error: AuthError, message: String) : RuntimeException(message)