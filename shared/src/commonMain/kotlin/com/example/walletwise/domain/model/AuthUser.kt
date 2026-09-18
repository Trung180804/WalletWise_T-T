package com.example.walletwise.domain.model

/** SDK-free, normalized authentication data. No credentials or profile-store fields. */
@ConsistentCopyVisibility
data class AuthUser private constructor(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val emailVerified: Boolean
) {
    val displayLabel: String get() = displayName ?: email ?: "Thành viên"

    override fun toString(): String = "AuthUser(redacted)"

    companion object {
        fun create(uid: String, email: String?, displayName: String?, emailVerified: Boolean): AuthUser? {
            if (uid.isBlank()) return null
            return AuthUser(
                uid = uid,
                email = email?.trim()?.takeIf { it.isNotEmpty() },
                displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
                emailVerified = emailVerified
            )
        }
    }
}
