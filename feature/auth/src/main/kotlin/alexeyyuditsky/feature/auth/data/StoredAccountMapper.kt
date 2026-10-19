package alexeyyuditsky.feature.auth.data

import alexeyyuditsky.feature.auth.domain.AccountCredentials
import alexeyyuditsky.feature.auth.domain.AuthUser
import java.sql.ResultSet
import java.util.UUID

internal class StoredAccountMapper {
    fun read(row: ResultSet): StoredAccount = StoredAccount(
        id = row.getObject("id", UUID::class.java),
        email = row.getString("email"),
        passwordHash = row.getString("password_hash"),
        emailVerifiedAt = row.getTimestamp("email_verified_at")?.toInstant(),
        createdAt = row.getTimestamp("created_at").toInstant()
    )

    fun user(account: StoredAccount): AuthUser =
        AuthUser(account.id, account.email, account.emailVerifiedAt, account.createdAt)

    fun credentials(account: StoredAccount): AccountCredentials =
        AccountCredentials(user(account), account.passwordHash)
}