package com.example.walletwise.domain.usecase.auth

import com.example.walletwise.domain.model.AuthSession
import com.example.walletwise.domain.model.ChangePasswordInput
import com.example.walletwise.domain.model.LoginInput
import com.example.walletwise.domain.model.RegisterInput
import com.example.walletwise.domain.model.ResetPasswordInput
import com.example.walletwise.domain.repository.AuthRepository
import com.example.walletwise.domain.result.AuthUseCaseResult
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.validation.AuthValidationException
import com.example.walletwise.domain.validation.AuthValidator
import kotlinx.coroutines.flow.Flow

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(input: LoginInput): AuthUseCaseResult<AuthSession> =
        AuthValidator.validateLogin(input).toAuthResult { repository.login(it) }
}

class RegisterUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(input: RegisterInput): AuthUseCaseResult<AuthSession> =
        AuthValidator.validateRegister(input).toAuthResult { repository.register(it) }
}

class LogoutUseCase(private val repository: AuthRepository) {
    operator fun invoke(): AuthUseCaseResult<Unit> = repository.logout().toAuthResult()
}

class ResetPasswordUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(input: ResetPasswordInput): AuthUseCaseResult<Unit> =
        AuthValidator.validateResetPassword(input).toAuthResult { repository.resetPassword(it) }
}

class ChangePasswordUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(input: ChangePasswordInput): AuthUseCaseResult<Unit> =
        AuthValidator.validateChangePassword(input).toAuthResult { repository.changePassword(it) }
}

class ObserveAuthStateUseCase(private val repository: AuthRepository) {
    operator fun invoke(): Flow<AuthSession?> = repository.observeAuthState()
}

private suspend inline fun <T, R> Result<T>.toAuthResult(
    crossinline block: suspend (T) -> RepositoryResult<R>
): AuthUseCaseResult<R> {
    val validated = getOrElse { failure ->
        return when (failure) {
            is AuthValidationException -> AuthUseCaseResult.ValidationFailure(
                message = failure.message.orEmpty(),
                reason = failure.error.name
            )
            else -> AuthUseCaseResult.ValidationFailure(
                message = failure.message.orEmpty(),
                reason = "UNKNOWN"
            )
        }
    }
    return block(validated).toAuthResult()
}

internal fun <T> RepositoryResult<T>.toAuthResult(): AuthUseCaseResult<T> = when (this) {
    is RepositoryResult.Success -> AuthUseCaseResult.Success(value)
    is RepositoryResult.Failure -> AuthUseCaseResult.RepositoryFailure(error)
}
