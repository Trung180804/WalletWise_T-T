package com.example.walletwise.domain.usecase

import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.repository.RecurringExecutionResult
import com.example.walletwise.domain.repository.RecurringTransactionRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.RecurringDateTimeProvider
import com.example.walletwise.domain.validation.RecurringInputException
import com.example.walletwise.domain.validation.RecurringTransactionValidator
import com.example.walletwise.domain.validation.RecurringValidationError

sealed interface RecurringUseCaseResult<out T> {
    data class Success<T>(val value: T) : RecurringUseCaseResult<T>
    data class ValidationFailure(val error: RecurringValidationError) : RecurringUseCaseResult<Nothing>
    data class RepositoryFailure(val error: RepositoryError) : RecurringUseCaseResult<Nothing>
}

class AddRecurringTransactionUseCase(private val repository: RecurringTransactionRepository) {
    suspend operator fun invoke(
        userId: String,
        recurring: RecurringTransaction
    ): RecurringUseCaseResult<RecurringTransaction> {
        val validated = RecurringTransactionValidator.validate(recurring.copy(userId = userId), requireId = true)
            .getOrElse { return it.validationFailure() }
        return repository.addRecurringTransaction(userId, validated).withValue(validated)
    }
}

class UpdateRecurringTransactionUseCase(private val repository: RecurringTransactionRepository) {
    suspend operator fun invoke(
        userId: String,
        recurring: RecurringTransaction
    ): RecurringUseCaseResult<RecurringTransaction> {
        val validated = RecurringTransactionValidator.validate(recurring.copy(userId = userId), requireId = true)
            .getOrElse { return it.validationFailure() }
        return repository.updateRecurringTransaction(userId, validated).withValue(validated)
    }
}

class DeleteRecurringTransactionUseCase(private val repository: RecurringTransactionRepository) {
    suspend operator fun invoke(userId: String, recurringId: String): RecurringUseCaseResult<Unit> {
        RecurringTransactionValidator.validateIdentity(userId, recurringId).exceptionOrNull()?.let {
            return it.validationFailure()
        }
        return repository.deleteRecurringTransaction(userId, recurringId).asUseCaseResult()
    }
}

class SetRecurringTransactionEnabledUseCase(private val repository: RecurringTransactionRepository) {
    suspend operator fun invoke(
        userId: String,
        recurringId: String,
        enabled: Boolean
    ): RecurringUseCaseResult<Unit> {
        RecurringTransactionValidator.validateIdentity(userId, recurringId).exceptionOrNull()?.let {
            return it.validationFailure()
        }
        return repository.setRecurringTransactionEnabled(userId, recurringId, enabled).asUseCaseResult()
    }
}

class UpdateRecurringLastExecutedDateUseCase(private val repository: RecurringTransactionRepository) {
    suspend operator fun invoke(
        userId: String,
        recurringId: String,
        marker: String
    ): RecurringUseCaseResult<Unit> {
        RecurringTransactionValidator.validateIdentity(userId, recurringId).exceptionOrNull()?.let {
            return it.validationFailure()
        }
        if (marker.isBlank()) return RecurringUseCaseResult.ValidationFailure(RecurringValidationError.DATE_INVALID)
        return repository.updateLastExecutedDate(userId, recurringId, marker).asUseCaseResult()
    }
}

class ExecuteRecurringIfDueUseCase(
    private val repository: RecurringTransactionRepository,
    private val dateTimeProvider: RecurringDateTimeProvider
) {
    suspend operator fun invoke(
        userId: String,
        recurringId: String
    ): RecurringUseCaseResult<RecurringExecutionResult> {
        RecurringTransactionValidator.validateIdentity(userId, recurringId).exceptionOrNull()?.let {
            return it.validationFailure()
        }
        return repository.executeIfDue(
            userId = userId,
            recurringId = recurringId,
            now = dateTimeProvider.currentLocalDateTime(),
            executedAtEpochMilliseconds = dateTimeProvider.currentEpochMilliseconds()
        ).asUseCaseResult()
    }
}

private fun Throwable.validationFailure(): RecurringUseCaseResult.ValidationFailure =
    RecurringUseCaseResult.ValidationFailure(
        (this as? RecurringInputException)?.reason ?: RecurringValidationError.ID_REQUIRED
    )

private fun <T> RepositoryResult<T>.asUseCaseResult(): RecurringUseCaseResult<T> = when (this) {
    is RepositoryResult.Success -> RecurringUseCaseResult.Success(value)
    is RepositoryResult.Failure -> RecurringUseCaseResult.RepositoryFailure(error)
}

private fun <T> RepositoryResult<Unit>.withValue(value: T): RecurringUseCaseResult<T> = when (this) {
    is RepositoryResult.Success -> RecurringUseCaseResult.Success(value)
    is RepositoryResult.Failure -> RecurringUseCaseResult.RepositoryFailure(error)
}
