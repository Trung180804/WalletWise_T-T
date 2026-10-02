package com.example.walletwise.domain.result

enum class RepositoryErrorCode {
    NOT_AUTHENTICATED,
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
    NETWORK,
    PERMISSION_DENIED,
    NOT_FOUND,
    UNKNOWN
}

data class RepositoryError(
    val code: RepositoryErrorCode,
    val message: String
)

sealed interface RepositoryResult<out T> {
    data class Success<T>(val value: T) : RepositoryResult<T>

    data class Failure(val error: RepositoryError) : RepositoryResult<Nothing>
}

inline fun <T, R> RepositoryResult<T>.map(transform: (T) -> R): RepositoryResult<R> =
    when (this) {
        is RepositoryResult.Success -> RepositoryResult.Success(transform(value))
        is RepositoryResult.Failure -> this
    }

sealed interface AuthUseCaseResult<out T> {
    data class Success<T>(val value: T) : AuthUseCaseResult<T>

    data class ValidationFailure(
        val message: String,
        val reason: String
    ) : AuthUseCaseResult<Nothing>

    data class RepositoryFailure(val error: RepositoryError) : AuthUseCaseResult<Nothing>
}
