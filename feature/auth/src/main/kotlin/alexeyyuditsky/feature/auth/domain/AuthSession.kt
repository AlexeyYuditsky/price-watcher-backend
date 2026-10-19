package alexeyyuditsky.feature.auth.domain

class AuthSession(val user: AuthUser, val tokens: SessionTokens)