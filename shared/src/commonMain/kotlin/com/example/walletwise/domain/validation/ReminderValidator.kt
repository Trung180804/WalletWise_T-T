package com.example.walletwise.domain.validation

import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.model.ReminderFrequency
import com.example.walletwise.domain.service.ReminderScheduleCalculator

enum class ReminderValidationError {
    USER_ID_REQUIRED,
    ID_REQUIRED,
    TITLE_REQUIRED,
    NOTE_REQUIRED,
    DATE_INVALID,
    TIME_INVALID,
    FREQUENCY_INVALID
}

class ReminderInputException(val reason: ReminderValidationError) : IllegalArgumentException(reason.name)

object ReminderValidator {
    fun validate(reminder: Reminder, requireId: Boolean): Result<Reminder> {
        val normalized = reminder.copy(
            id = reminder.id.trim(),
            userId = reminder.userId.trim(),
            title = reminder.title.trim(),
            note = reminder.note.trim(),
            startDate = reminder.startDate.trim(),
            time = reminder.time.trim()
        )
        val error = when {
            normalized.userId.isBlank() -> ReminderValidationError.USER_ID_REQUIRED
            requireId && normalized.id.isBlank() -> ReminderValidationError.ID_REQUIRED
            normalized.title.isBlank() -> ReminderValidationError.TITLE_REQUIRED
            normalized.note.isBlank() -> ReminderValidationError.NOTE_REQUIRED
            ReminderFrequency.fromWireValue(normalized.frequency) == null ->
                ReminderValidationError.FREQUENCY_INVALID
            ReminderScheduleCalculator.parseStartDate(normalized.startDate) == null ->
                ReminderValidationError.DATE_INVALID
            ReminderScheduleCalculator.parseTime(normalized.time) == null ->
                ReminderValidationError.TIME_INVALID
            else -> null
        }
        return if (error == null) Result.success(normalized) else Result.failure(ReminderInputException(error))
    }
}
