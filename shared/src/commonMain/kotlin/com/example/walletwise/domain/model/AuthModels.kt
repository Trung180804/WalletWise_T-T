package com.example.walletwise.domain.model

data class AuthSession(
    val userId: String,
    val email: String,
    val displayName: String = "",
    val emailVerified: Boolean = false
) {
    val user: AuthUser? get() = AuthUser.create(userId, email, displayName, emailVerified)

    override fun toString(): String = "AuthSession(redacted)"

    companion object {
        fun fromUser(user: AuthUser): AuthSession = AuthSession(
            userId = user.uid,
            email = user.email.orEmpty(),
            displayName = user.displayName.orEmpty(),
            emailVerified = user.emailVerified
        )
    }
}

data class LoginInput(
    val email: String,
    val password: String
)

data class RegisterInput(
    val email: String,
    val password: String,
    val confirmPassword: String,
    val username: String
)

data class ResetPasswordInput(val email: String)

data class ChangePasswordInput(
    val currentPassword: String,
    val newPassword: String,
    val confirmPassword: String
)

data class UserProfileUpdate(
    val username: String? = null,
    val gender: String? = null,
    val avatarUrl: String? = null
) {
    val isEmpty: Boolean
        get() = username == null && gender == null && avatarUrl == null
}

fun AuthSession.defaultUser(): User {
    val resolvedUsername = displayName.takeIf { it.isNotBlank() }
        ?: email.substringBefore("@").takeIf { it.isNotBlank() }
        ?: "Thành viên"
    return User(
        id = userId,
        email = email,
        username = resolvedUsername
    )
}
