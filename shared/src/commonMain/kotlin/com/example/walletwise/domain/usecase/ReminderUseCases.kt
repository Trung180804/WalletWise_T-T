package com.example.walletwise.domain.usecase

import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.repository.ReminderRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.validation.ReminderInputException
import com.example.walletwise.domain.validation.ReminderValidationError
import com.example.walletwise.domain.validation.ReminderValidator
import kotlinx.coroutines.flow.Flow

sealed interface ReminderUseCaseResult<out T> {
    data class Success<T>(val value: T) : ReminderUseCaseResult<T>
    data class ValidationFailure(val error: ReminderValidationError) : ReminderUseCaseResult<Nothing>
    data class RepositoryFailure(val error: RepositoryError) : ReminderUseCaseResult<Nothing>
}

class ObserveRemindersUseCase(private val repository: ReminderRepository) {
    operator fun invoke(userId: String): Flow<RepositoryResult<List<Reminder>>> =
        repository.observeReminders(userId)
}

class AddReminderUseCase(private val repository: ReminderRepository) {
    suspend operator fun invoke(userId: String, draft: Reminder): ReminderUseCaseResult<Reminder> {
        val validated = validate(draft.copy(userId = userId), requireId = true)
            ?: return validationFailure(draft.copy(userId = userId), true)
        return repository.addReminder(userId, validated).toUseCaseResult(validated)
    }
}

class UpdateReminderUseCase(private val repository: ReminderRepository) {
    suspend operator fun invoke(userId: String, reminder: Reminder): ReminderUseCaseResult<Reminder> {
        val candidate = reminder.copy(userId = userId)
        val validated = validate(candidate, requireId = true)
            ?: return validationFailure(candidate, true)
        return repository.updateReminder(userId, validated).toUseCaseResult(validated)
    }
}

class DeleteReminderUseCase(private val repository: ReminderRepository) {
    suspend operator fun invoke(userId: String, reminderId: String): ReminderUseCaseResult<Unit> {
        if (userId.isBlank()) return ReminderUseCaseResult.ValidationFailure(ReminderValidationError.USER_ID_REQUIRED)
        if (reminderId.isBlank()) return ReminderUseCaseResult.ValidationFailure(ReminderValidationError.ID_REQUIRED)
        return repository.deleteReminder(userId, reminderId).toUseCaseResult(Unit)
    }
}

class SetReminderEnabledUseCase(private val repository: ReminderRepository) {
    suspend operator fun invoke(
        userId: String,
        reminderId: String,
        isEnabled: Boolean
    ): ReminderUseCaseResult<Unit> {
        if (userId.isBlank()) return ReminderUseCaseResult.ValidationFailure(ReminderValidationError.USER_ID_REQUIRED)
        if (reminderId.isBlank()) return ReminderUseCaseResult.ValidationFailure(ReminderValidationError.ID_REQUIRED)
        return repository.setReminderEnabled(userId, reminderId, isEnabled).toUseCaseResult(Unit)
    }
}

private fun validate(reminder: Reminder, requireId: Boolean): Reminder? =
    ReminderValidator.validate(reminder, requireId).getOrNull()

private fun validationFailure(reminder: Reminder, requireId: Boolean): ReminderUseCaseResult.ValidationFailure {
    val error = ReminderValidator.validate(reminder, requireId).exceptionOrNull()
    return ReminderUseCaseResult.ValidationFailure(
        (error as? ReminderInputException)?.reason ?: ReminderValidationError.DATE_INVALID
    )
}

private fun <T> RepositoryResult<Unit>.toUseCaseResult(value: T): ReminderUseCaseResult<T> = when (this) {
    is RepositoryResult.Success -> ReminderUseCaseResult.Success(value)
    is RepositoryResult.Failure -> ReminderUseCaseResult.RepositoryFailure(error)
}
