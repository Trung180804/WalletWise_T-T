package com.example.walletwise.domain.usecase

import com.example.walletwise.domain.model.RECURRING_FREQUENCY_DAILY
import com.example.walletwise.domain.model.RECURRING_TIMES_UNLIMITED
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.validation.RecurringValidationError
import com.example.walletwise.presentation.recurring.FakeRecurringRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecurringTransactionUseCasesTest {
    @Test
    fun addUpdateDeleteAndTogglePropagateSuccessAndRepositoryFailure() = runTest {
        val repository = FakeRecurringRepository()
        val valid = rule()

        assertIs<RecurringUseCaseResult.Success<RecurringTransaction>>(
            AddRecurringTransactionUseCase(repository)("user", valid)
        )
        assertTrue(repository.rules.containsKey(valid.id))
        repository.addFailure = true
        assertIs<RecurringUseCaseResult.RepositoryFailure>(
            AddRecurringTransactionUseCase(repository)("user", valid.copy(id = "second"))
        )
        repository.addFailure = false

        assertIs<RecurringUseCaseResult.Success<RecurringTransaction>>(
            UpdateRecurringTransactionUseCase(repository)("user", valid.copy(title = "Updated"))
        )
        assertEquals("Updated", repository.rules.getValue(valid.id).title)
        repository.updateFailure = true
        assertIs<RecurringUseCaseResult.RepositoryFailure>(
            UpdateRecurringTransactionUseCase(repository)("user", valid.copy(title = "Failed"))
        )
        repository.updateFailure = false

        assertIs<RecurringUseCaseResult.Success<Unit>>(
            SetRecurringTransactionEnabledUseCase(repository)("user", valid.id, false)
        )
        assertEquals(false, repository.rules.getValue(valid.id).isEnabled)
        repository.toggleFailure = true
        assertIs<RecurringUseCaseResult.RepositoryFailure>(
            SetRecurringTransactionEnabledUseCase(repository)("user", valid.id, true)
        )
        repository.toggleFailure = false

        repository.deleteFailure = true
        assertIs<RecurringUseCaseResult.RepositoryFailure>(
            DeleteRecurringTransactionUseCase(repository)("user", valid.id)
        )
        repository.deleteFailure = false
        assertIs<RecurringUseCaseResult.Success<Unit>>(
            DeleteRecurringTransactionUseCase(repository)("user", valid.id)
        )
    }

    @Test
    fun validationCoversEveryRequiredFieldAndRejectsNonFiniteOrNonPositiveAmounts() = runTest {
        val repository = FakeRecurringRepository()
        val add = AddRecurringTransactionUseCase(repository)
        val cases = listOf(
            rule().copy(title = "") to RecurringValidationError.TITLE_REQUIRED,
            rule().copy(amount = 0.0) to RecurringValidationError.AMOUNT_INVALID,
            rule().copy(amount = Double.POSITIVE_INFINITY) to RecurringValidationError.AMOUNT_INVALID,
            rule().copy(type = "Other") to RecurringValidationError.TYPE_INVALID,
            rule().copy(category = "") to RecurringValidationError.CATEGORY_REQUIRED,
            rule().copy(paymentMethod = "") to RecurringValidationError.PAYMENT_METHOD_REQUIRED,
            rule().copy(frequency = "Sometimes") to RecurringValidationError.FREQUENCY_INVALID,
            rule().copy(timesCount = "8") to RecurringValidationError.TIMES_COUNT_INVALID,
            rule().copy(startDate = "31/02/2026") to RecurringValidationError.DATE_INVALID,
            rule().copy(time = "25:00") to RecurringValidationError.TIME_INVALID
        )

        cases.forEach { (candidate, expected) ->
            val result = assertIs<RecurringUseCaseResult.ValidationFailure>(add("user", candidate))
            assertEquals(expected, result.error)
        }
    }

    @Test
    fun updateDeleteAndExecuteRequireValidIdentity() = runTest {
        val repository = FakeRecurringRepository()
        val update = UpdateRecurringTransactionUseCase(repository)
        val delete = DeleteRecurringTransactionUseCase(repository)

        assertEquals(
            RecurringValidationError.ID_REQUIRED,
            assertIs<RecurringUseCaseResult.ValidationFailure>(update("user", rule().copy(id = ""))).error
        )
        assertEquals(
            RecurringValidationError.USER_ID_REQUIRED,
            assertIs<RecurringUseCaseResult.ValidationFailure>(delete("", "rule")).error
        )
    }

    @Test
    fun recurringTransactionWriteUsesManualTransactionValidationContract() {
        val create = CreateRecurringTransactionWriteUseCase()
        val valid = create(rule(), "user", "rule_2026-09-05", 123L).getOrThrow()

        assertEquals("rule_2026-09-05", valid.id)
        assertEquals("user", valid.userId)
        assertEquals("[Định kỳ] Rent", valid.note)
        assertTrue(create(rule().copy(amount = Double.NaN), "user", "id", 123L).isFailure)
    }

    private fun rule() = RecurringTransaction(
        id = "rule",
        userId = "user",
        title = "Rent",
        amount = 100.0,
        type = "Chi",
        category = "Hóa đơn",
        paymentMethod = "Tiền mặt",
        frequency = RECURRING_FREQUENCY_DAILY,
        timesCount = RECURRING_TIMES_UNLIMITED,
        startDate = "5 thg 9, 2026",
        time = "08:00"
    )
}
