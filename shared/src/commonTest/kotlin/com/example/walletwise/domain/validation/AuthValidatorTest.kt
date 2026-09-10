package com.example.walletwise.domain.validation

import com.example.walletwise.domain.model.ChangePasswordInput
import com.example.walletwise.domain.model.LoginInput
import com.example.walletwise.domain.model.RegisterInput
import com.example.walletwise.domain.model.ResetPasswordInput
import com.example.walletwise.domain.model.UserProfileUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthValidatorTest {
    @Test
    fun emptyEmail_isRejectedWithExistingLoginMessage() {
        assertValidationError(
            AuthValidationError.REQUIRED_FIELDS,
            AuthValidator.validateLogin(LoginInput(" ", "password"))
        )
    }

    @Test
    fun malformedEmail_isRejected() {
        assertValidationError(
            AuthValidationError.INVALID_EMAIL,
            AuthValidator.validateLogin(LoginInput("not-an-email", "password"))
        )
    }

    @Test
    fun emptyPassword_isRejected() {
        assertValidationError(
            AuthValidationError.REQUIRED_FIELDS,
            AuthValidator.validateLogin(LoginInput("user@example.com", " "))
        )
    }

    @Test
    fun shortRegistrationPassword_isRejectedAtCurrentFirebaseMinimum() {
        assertValidationError(
            AuthValidationError.PASSWORD_TOO_SHORT,
            AuthValidator.validateRegister(registerInput(password = "12345", confirm = "12345"))
        )
    }

    @Test
    fun registrationPasswordConfirmationMustMatch() {
        assertValidationError(
            AuthValidationError.PASSWORD_CONFIRMATION_MISMATCH,
            AuthValidator.validateRegister(registerInput(password = "123456", confirm = "654321"))
        )
    }

    @Test
    fun registrationUsername_isRequired() {
        assertValidationError(
            AuthValidationError.REQUIRED_FIELDS,
            AuthValidator.validateRegister(registerInput(username = "  "))
        )
    }

    @Test
    fun authInputs_areTrimmedLikeExistingFacade() {
        val login = AuthValidator.validateLogin(LoginInput(" user@example.com ", " password ")).getOrThrow()
        val register = AuthValidator.validateRegister(registerInput(username = "  Wallet User  ")).getOrThrow()

        assertEquals("user@example.com", login.email)
        assertEquals("password", login.password)
        assertEquals("Wallet User", register.username)
    }

    @Test
    fun resetPasswordRequiresValidEmail() {
        assertTrue(AuthValidator.validateResetPassword(ResetPasswordInput("user@example.com")).isSuccess)
        assertValidationError(
            AuthValidationError.INVALID_EMAIL,
            AuthValidator.validateResetPassword(ResetPasswordInput("invalid"))
        )
    }

    @Test
    fun profileUsername_isTrimmedAndCannotBeBlank() {
        val valid = AuthValidator.validateProfileUpdate(UserProfileUpdate(username = "  New Name  ")).getOrThrow()
        assertEquals("New Name", valid.username)
        assertValidationError(
            AuthValidationError.REQUIRED_USERNAME,
            AuthValidator.validateProfileUpdate(UserProfileUpdate(username = "  "))
        )
    }

    @Test
    fun changePasswordKeepsExistingSixCharacterRuleAndConfirmation() {
        assertValidationError(
            AuthValidationError.PASSWORD_TOO_SHORT,
            AuthValidator.validateChangePassword(ChangePasswordInput("old-pass", "12345", "12345"))
        )
        assertValidationError(
            AuthValidationError.PASSWORD_CONFIRMATION_MISMATCH,
            AuthValidator.validateChangePassword(ChangePasswordInput("old-pass", "123456", "654321"))
        )
    }

    private fun registerInput(
        password: String = "123456",
        confirm: String = password,
        username: String = "Wallet User"
    ) = RegisterInput("user@example.com", password, confirm, username)

    private fun assertValidationError(expected: AuthValidationError, result: Result<*>) {
        val exception = assertIs<AuthValidationException>(result.exceptionOrNull())
        assertEquals(expected, exception.error)
    }
}
