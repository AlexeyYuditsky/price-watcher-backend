package alexeyyuditsky.feature.auth.domain

class AccountCredentials(val user: AuthUser, val passwordHash: String)