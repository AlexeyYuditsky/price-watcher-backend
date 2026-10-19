package alexeyyuditsky.feature.auth.domain

import java.time.Clock
import java.time.Duration
import java.util.Locale

class AuthService(
    private val repository: AuthRepository,
    private val passwords: PasswordHasher,
    private val tokens: TokenGenerator,
    private val clock: Clock
) {
    private val dummyHash = passwords.hash(tokens.generate())

    fun register(email: String, password: String): AuthSession {
        val normalized = normalizeEmail(email)
        if (password.length < 4) {
            throw AuthException(AuthError.INVALID_INPUT, "Password must contain at least 4 characters")
        }
        val now = clock.instant()
        val session = newTokens()
        val user = repository.register(normalized, passwords.hash(password), hashes(session), now)
            ?: throw AuthException(AuthError.EMAIL_IN_USE, "Email is already registered")
        return AuthSession(user, session)
    }

    fun login(email: String, password: String): AuthSession {
        val normalized = normalizeEmail(email)
        if (password.length < 4) unauthorized()
        val account = repository.findCredentials(normalized)
        val matches = passwords.verify(password, account?.passwordHash ?: dummyHash)
        if (!matches || account == null) unauthorized()
        val session = newTokens()
        repository.createSession(account.user.id, hashes(session), clock.instant())
        return AuthSession(account.user, session)
    }

    fun refresh(refreshToken: String): AuthSession {
        validateToken(refreshToken)
        val session = newTokens()
        val user = repository.rotateSession(tokens.hash(refreshToken), hashes(session), clock.instant())
            ?: unauthorized()
        return AuthSession(user, session)
    }

    fun currentUser(accessToken: String): AuthUser {
        validateToken(accessToken)
        return repository.findUser(tokens.hash(accessToken), clock.instant()) ?: unauthorized()
    }

    fun logout(accessToken: String) {
        validateToken(accessToken)
        repository.revokeSession(tokens.hash(accessToken), clock.instant())
    }

    private fun newTokens(): SessionTokens {
        val now = clock.instant()
        return SessionTokens(
            accessToken = tokens.generate(),
            refreshToken = tokens.generate(),
            accessExpiresAt = now.plus(Duration.ofHours(1)),
            refreshExpiresAt = now.plus(Duration.ofDays(90))
        )
    }

    private fun hashes(session: SessionTokens): SessionHashes = SessionHashes(
        accessHash = tokens.hash(session.accessToken),
        refreshHash = tokens.hash(session.refreshToken),
        accessExpiresAt = session.accessExpiresAt,
        refreshExpiresAt = session.refreshExpiresAt
    )

    private fun normalizeEmail(email: String): String {
        val normalized = email.trim().lowercase(Locale.ROOT)
        val parts = normalized.split('@')
        if (normalized.length !in 3..254 || parts.size != 2) invalidEmail()
        val local = parts[0]
        val domain = parts[1]
        val atom = Regex("[a-z0-9!#$%&'*+/=?^_{}|~\\x60-]+")
        val label = Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")
        val labels = domain.split('.')
        if (local.length !in 1..64 || local.split('.').any { !atom.matches(it) } ||
            labels.size < 2 || labels.any { it.length !in 1..63 || !label.matches(it) } ||
            !Regex("[a-z]{2,}|xn--[a-z0-9-]+").matches(labels.last())
        ) invalidEmail()
        return normalized
    }

    private fun invalidEmail(): Nothing =
        throw AuthException(AuthError.INVALID_INPUT, "Invalid email")

    private fun validateToken(token: String) {
        if (!Regex("^[A-Za-z0-9_-]{43}$").matches(token)) unauthorized()
    }

    private fun unauthorized(): Nothing =
        throw AuthException(AuthError.UNAUTHORIZED, "Invalid credentials or expired session")
}