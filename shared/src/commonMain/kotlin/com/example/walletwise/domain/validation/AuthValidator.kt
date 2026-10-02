package com.example.walletwise.domain.validation

import com.example.walletwise.domain.model.ChangePasswordInput
import com.example.walletwise.domain.model.LoginInput
import com.example.walletwise.domain.model.RegisterInput
import com.example.walletwise.domain.model.ResetPasswordInput
import com.example.walletwise.domain.model.UserProfileUpdate

enum class AuthValidationError {
    REQUIRED_FIELDS,
    INVALID_EMAIL,
    PASSWORD_TOO_SHORT,
    PASSWORD_CONFIRMATION_MISMATCH,
    REQUIRED_USERNAME,
    REQUIRED_CURRENT_PASSWORD,
    EMPTY_PROFILE_UPDATE,
    EMPTY_AVATAR_URL
}

class AuthValidationException(
    val error: AuthValidationError,
    message: String
) : IllegalArgumentException(message)

object AuthValidator {
    const val MIN_PASSWORD_LENGTH = 6

    private val emailPattern = Regex(
        "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"
    )

    fun validateLogin(input: LoginInput): Result<LoginInput> {
        val normalized = input.copy(
            email = input.email.trim(),
            password = input.password.trim()
        )
        if (normalized.email.isBlank() || normalized.password.isBlank()) {
            return invalid(
                AuthValidationError.REQUIRED_FIELDS,
                "Email và mật khẩu không được để trống!"
            )
        }
        return validateEmail(normalized.email).map { normalized }
    }

    fun validateRegister(input: RegisterInput): Result<RegisterInput> {
        val normalized = input.copy(
            email = input.email.trim(),
            password = input.password.trim(),
            confirmPassword = input.confirmPassword.trim(),
            username = input.username.trim()
        )
        if (
            normalized.email.isBlank() ||
            normalized.password.isBlank() ||
            normalized.confirmPassword.isBlank() ||
            normalized.username.isBlank()
        ) {
            return invalid(
                AuthValidationError.REQUIRED_FIELDS,
                "Vui lòng nhập đầy đủ thông tin!"
            )
        }
        validateEmail(normalized.email).exceptionOrNull()?.let { return Result.failure(it) }
        if (normalized.password.length < MIN_PASSWORD_LENGTH) {
            return invalid(
                AuthValidationError.PASSWORD_TOO_SHORT,
                "Mật khẩu phải có ít nhất 6 ký tự!"
            )
        }
        if (normalized.password != normalized.confirmPassword) {
            return invalid(
                AuthValidationError.PASSWORD_CONFIRMATION_MISMATCH,
                "Mật khẩu xác nhận không trùng khớp!"
            )
        }
        return Result.success(normalized)
    }

    fun validateResetPassword(input: ResetPasswordInput): Result<ResetPasswordInput> {
        val normalized = input.copy(email = input.email.trim())
        if (normalized.email.isBlank()) {
            return invalid(AuthValidationError.REQUIRED_FIELDS, "Vui lòng nhập Email!")
        }
        return validateEmail(normalized.email).map { normalized }
    }

    fun validateChangePassword(input: ChangePasswordInput): Result<ChangePasswordInput> {
        if (input.currentPassword.isBlank()) {
            return invalid(
                AuthValidationError.REQUIRED_CURRENT_PASSWORD,
                "Vui lòng nhập mật khẩu hiện tại!"
            )
        }
        if (input.newPassword.length < MIN_PASSWORD_LENGTH) {
            return invalid(
                AuthValidationError.PASSWORD_TOO_SHORT,
                "Mật khẩu mới phải có ít nhất 6 ký tự!"
            )
        }
        if (input.newPassword != input.confirmPassword) {
            return invalid(
                AuthValidationError.PASSWORD_CONFIRMATION_MISMATCH,
                "Mật khẩu nhắc lại không trùng khớp!"
            )
        }
        return Result.success(input)
    }

    fun validateProfileUpdate(update: UserProfileUpdate): Result<UserProfileUpdate> {
        if (update.isEmpty) {
            return invalid(AuthValidationError.EMPTY_PROFILE_UPDATE, "Không có thay đổi để cập nhật.")
        }
        val username = update.username?.trim()
        if (username != null && username.isBlank()) {
            return invalid(AuthValidationError.REQUIRED_USERNAME, "Họ và tên không được để trống!")
        }
        if (update.avatarUrl != null && update.avatarUrl.isBlank()) {
            return invalid(AuthValidationError.EMPTY_AVATAR_URL, "URL ảnh đại diện không hợp lệ.")
        }
        return Result.success(update.copy(username = username))
    }

    private fun validateEmail(email: String): Result<Unit> =
        if (emailPattern.matches(email)) {
            Result.success(Unit)
        } else {
            invalid(AuthValidationError.INVALID_EMAIL, "Email không hợp lệ!")
        }

    private fun <T> invalid(error: AuthValidationError, message: String): Result<T> =
        Result.failure(AuthValidationException(error, message))
}
