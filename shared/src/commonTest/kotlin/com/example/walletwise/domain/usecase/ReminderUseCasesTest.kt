package com.example.walletwise.domain.usecase

import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.repository.ReminderRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.validation.ReminderValidationError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReminderUseCasesTest {
    @Test
    fun validationRejectsMissingFieldsAndInvalidDateTimeFrequency() = runTest {
        val repository = FakeReminderRepository()
        val add = AddReminderUseCase(repository)
        assertEquals(
            ReminderValidationError.TITLE_REQUIRED,
            assertIs<ReminderUseCaseResult.ValidationFailure>(add("user", valid().copy(title = " "))).error
        )
        assertEquals(
            ReminderValidationError.NOTE_REQUIRED,
            assertIs<ReminderUseCaseResult.ValidationFailure>(add("user", valid().copy(note = ""))).error
        )
        assertEquals(
            ReminderValidationError.DATE_INVALID,
            assertIs<ReminderUseCaseResult.ValidationFailure>(add("user", valid().copy(startDate = "31/02/2026"))).error
        )
        assertEquals(
            ReminderValidationError.TIME_INVALID,
            assertIs<ReminderUseCaseResult.ValidationFailure>(add("user", valid().copy(time = "25:00"))).error
        )
        assertEquals(
            ReminderValidationError.FREQUENCY_INVALID,
            assertIs<ReminderUseCaseResult.ValidationFailure>(add("user", valid().copy(frequency = "Mỗi lúc"))).error
        )
    }

    @Test
    fun addUpdateDeleteAndToggleDelegateExactValues() = runTest {
        val repository = FakeReminderRepository()
        assertIs<ReminderUseCaseResult.Success<Reminder>>(AddReminderUseCase(repository)("user", valid()))
        assertEquals("user", repository.added?.userId)
        assertIs<ReminderUseCaseResult.Success<Reminder>>(UpdateReminderUseCase(repository)("user", valid()))
        assertIs<ReminderUseCaseResult.Success<Unit>>(DeleteReminderUseCase(repository)("user", "id"))
        assertIs<ReminderUseCaseResult.Success<Unit>>(SetReminderEnabledUseCase(repository)("user", "id", false))
        assertEquals(1, repository.addCalls)
        assertEquals(1, repository.updateCalls)
        assertEquals("id", repository.deletedId)
        assertEquals("id" to false, repository.toggle)
    }

    @Test
    fun updateDeleteToggleRequireIdsAndRepositoryFailuresPropagate() = runTest {
        val repository = FakeReminderRepository().apply { fail = true }
        assertEquals(
            ReminderValidationError.ID_REQUIRED,
            assertIs<ReminderUseCaseResult.ValidationFailure>(UpdateReminderUseCase(repository)("user", valid().copy(id = ""))).error
        )
        assertEquals(
            ReminderValidationError.ID_REQUIRED,
            assertIs<ReminderUseCaseResult.ValidationFailure>(DeleteReminderUseCase(repository)("user", "")).error
        )
        assertEquals(
            ReminderValidationError.ID_REQUIRED,
            assertIs<ReminderUseCaseResult.ValidationFailure>(SetReminderEnabledUseCase(repository)("user", "", true)).error
        )
        assertIs<ReminderUseCaseResult.RepositoryFailure>(AddReminderUseCase(repository)("user", valid()))
        assertIs<ReminderUseCaseResult.RepositoryFailure>(UpdateReminderUseCase(repository)("user", valid()))
        assertIs<ReminderUseCaseResult.RepositoryFailure>(DeleteReminderUseCase(repository)("user", "id"))
        assertIs<ReminderUseCaseResult.RepositoryFailure>(SetReminderEnabledUseCase(repository)("user", "id", false))
    }

    private fun valid() = Reminder(
        id = "id", title = "Ghi chép", note = "Ghi lại chi tiêu",
        frequency = "Hàng ngày", startDate = "9 thg 9, 2026", time = "20:15"
    )
}

private class FakeReminderRepository : ReminderRepository {
    var fail = false
    var addCalls = 0
    var updateCalls = 0
    var added: Reminder? = null
    var deletedId: String? = null
    var toggle: Pair<String, Boolean>? = null
    private val failure = RepositoryResult.Failure(RepositoryError(RepositoryErrorCode.UNKNOWN, "failed"))

    override fun observeReminders(userId: String): Flow<RepositoryResult<List<Reminder>>> = emptyFlow()
    override suspend fun getReminders(userId: String): RepositoryResult<List<Reminder>> = RepositoryResult.Success(emptyList())
    override suspend fun addReminder(userId: String, reminder: Reminder): RepositoryResult<Unit> {
        addCalls++
        added = reminder
        return result()
    }
    override suspend fun updateReminder(userId: String, reminder: Reminder): RepositoryResult<Unit> {
        updateCalls++
        return result()
    }
    override suspend fun deleteReminder(userId: String, reminderId: String): RepositoryResult<Unit> {
        deletedId = reminderId
        return result()
    }
    override suspend fun setReminderEnabled(userId: String, reminderId: String, isEnabled: Boolean): RepositoryResult<Unit> {
        toggle = reminderId to isEnabled
        return result()
    }
    private fun result(): RepositoryResult<Unit> = if (fail) failure else RepositoryResult.Success(Unit)
}
