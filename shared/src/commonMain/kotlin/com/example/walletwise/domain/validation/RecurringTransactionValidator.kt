package com.example.walletwise.domain.validation

import com.example.walletwise.domain.model.RECURRING_TIMES_COUNT_WIRE_VALUES
import com.example.walletwise.domain.model.RecurringFrequency
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.model.TRANSACTION_TYPE_EXPENSE
import com.example.walletwise.domain.model.TRANSACTION_TYPE_INCOME
import com.example.walletwise.domain.service.RecurringScheduleCalculator

enum class RecurringValidationError {
    USER_ID_REQUIRED,
    ID_REQUIRED,
    TITLE_REQUIRED,
    AMOUNT_INVALID,
    TYPE_INVALID,
    CATEGORY_REQUIRED,
    PAYMENT_METHOD_REQUIRED,
    FREQUENCY_INVALID,
    TIMES_COUNT_INVALID,
    DATE_INVALID,
    TIME_INVALID
}

class RecurringInputException(val reason: RecurringValidationError) :
    IllegalArgumentException(reason.name)

object RecurringTransactionValidator {
    fun validate(recurring: RecurringTransaction, requireId: Boolean): Result<RecurringTransaction> {
        val normalized = recurring.copy(
            id = recurring.id.trim(),
            userId = recurring.userId.trim(),
            title = recurring.title.trim(),
            type = recurring.type.trim(),
            category = recurring.category.trim(),
            paymentMethod = recurring.paymentMethod.trim(),
            frequency = recurring.frequency.trim(),
            timesCount = recurring.timesCount.trim(),
            startDate = recurring.startDate.trim(),
            time = recurring.time.trim(),
            note = recurring.note.trim(),
            lastExecutedDate = recurring.lastExecutedDate.trim()
        )
        val error = when {
            normalized.userId.isBlank() -> RecurringValidationError.USER_ID_REQUIRED
            requireId && normalized.id.isBlank() -> RecurringValidationError.ID_REQUIRED
            normalized.title.isBlank() -> RecurringValidationError.TITLE_REQUIRED
            !normalized.amount.isFinite() || normalized.amount <= 0.0 -> RecurringValidationError.AMOUNT_INVALID
            normalized.type != TRANSACTION_TYPE_INCOME && normalized.type != TRANSACTION_TYPE_EXPENSE ->
                RecurringValidationError.TYPE_INVALID
            normalized.category.isBlank() -> RecurringValidationError.CATEGORY_REQUIRED
            normalized.paymentMethod.isBlank() -> RecurringValidationError.PAYMENT_METHOD_REQUIRED
            RecurringFrequency.fromWireValue(normalized.frequency) == null ->
                RecurringValidationError.FREQUENCY_INVALID
            normalized.timesCount !in RECURRING_TIMES_COUNT_WIRE_VALUES ->
                RecurringValidationError.TIMES_COUNT_INVALID
            RecurringScheduleCalculator.parseStartDate(normalized.startDate) == null ->
                RecurringValidationError.DATE_INVALID
            RecurringScheduleCalculator.parseTime(normalized.time) == null ->
                RecurringValidationError.TIME_INVALID
            else -> null
        }
        return if (error == null) Result.success(normalized) else Result.failure(RecurringInputException(error))
    }

    fun validateIdentity(userId: String, recurringId: String): Result<Unit> = when {
        userId.isBlank() -> Result.failure(RecurringInputException(RecurringValidationError.USER_ID_REQUIRED))
        recurringId.isBlank() -> Result.failure(RecurringInputException(RecurringValidationError.ID_REQUIRED))
        else -> Result.success(Unit)
    }
}
