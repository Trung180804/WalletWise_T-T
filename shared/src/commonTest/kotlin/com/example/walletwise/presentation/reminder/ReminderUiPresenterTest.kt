package com.example.walletwise.presentation.reminder

import com.example.walletwise.domain.model.DEFAULT_REMINDER_NOTE
import com.example.walletwise.domain.model.DEFAULT_REMINDER_TITLE
import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.repository.ReminderRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.ReconcileReminderSchedulingUseCase
import com.example.walletwise.domain.service.ReminderDateTimeProvider
import com.example.walletwise.domain.service.ReminderLocalDate
import com.example.walletwise.domain.service.ReminderLocalDateTime
import com.example.walletwise.domain.service.ReminderLocalTime
import com.example.walletwise.domain.service.ReminderPermission
import com.example.walletwise.domain.service.ReminderPermissionGateway
import com.example.walletwise.domain.service.ReminderPermissionState
import com.example.walletwise.domain.service.ReminderPlatformScheduleResult
import com.example.walletwise.domain.service.ReminderPlatformScheduler
import com.example.walletwise.domain.service.ReminderScheduleRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderUiPresenterTest {
    @Test
    fun loadingEmptyDataErrorAndSortComeFromSession() = runTest {
        val env = environment(this)
        runCurrent()
        assertTrue(env.presenter.state.value.isEmpty)
        env.repository.source.value = RepositoryResult.Success(listOf(reminder("z"), reminder("a")))
        runCurrent()
        assertEquals(listOf("a", "z"), env.presenter.state.value.reminders.map { it.id })

        env.repository.source.value = RepositoryResult.Failure(error("offline"))
        runCurrent()
        assertEquals(listOf("a", "z"), env.presenter.state.value.reminders.map { it.id })
        assertEquals("offline", env.presenter.state.value.repositoryError?.message)
        env.close()
    }

    @Test
    fun addDefaultsBlankTitleAndNoteAwaitsWriteAndBlocksDuplicateSubmit() = runTest {
        val env = environment(this)
        runCurrent()
        env.presenter.onOpenAdd()
        env.repository.writeGate = CompletableDeferred()
        env.presenter.onSubmit()
        env.presenter.onSubmit()
        runCurrent()
        assertEquals(1, env.repository.addCalls)
        assertTrue(env.presenter.state.value.isSaving)

        env.repository.writeGate?.complete(Unit)
        runCurrent()
        assertEquals(DEFAULT_REMINDER_TITLE, env.repository.lastAdded?.title)
        assertEquals(DEFAULT_REMINDER_NOTE, env.repository.lastAdded?.note)
        assertEquals(ReminderScreenMode.LIST, env.presenter.state.value.screenMode)
        consumeMessage(env.presenter, ReminderMessage.ADDED)
        env.close()
    }

    @Test
    fun invalidFormStaysOpenAndDoesNotWrite() = runTest {
        val env = environment(this)
        runCurrent()
        env.presenter.onOpenAdd()
        env.presenter.onOpenDatePicker()
        env.presenter.onDatePickerDaySelected(9)
        env.presenter.onConfirmDatePicker()
        env.presenter.onOpenTimePicker()
        env.presenter.onTimePickerHourChanged(23)
        env.presenter.onTimePickerMinuteChanged(7)
        env.presenter.onConfirmTimePicker()
        assertEquals("23:07", env.presenter.state.value.time)
        env.presenter.onFrequencySelected("Không hợp lệ")
        env.presenter.onSubmit()
        runCurrent()
        assertEquals(0, env.repository.addCalls)
        assertEquals(ReminderScreenMode.FORM, env.presenter.state.value.screenMode)
        assertTrue(env.presenter.state.value.validationError != null)
        env.close()
    }

    @Test
    fun updateAndDeleteApplyPlatformOrderAndEmitConsumableEvents() = runTest {
        val existing = reminder("id")
        val env = environment(this, listOf(existing))
        runCurrent()
        env.platform.actions.clear()
        env.presenter.onOpenEdit(existing)
        env.presenter.onTitleChanged("Đã sửa")
        env.presenter.onSubmit()
        runCurrent()
        assertEquals("Đã sửa", env.repository.lastUpdated?.title)
        assertEquals(listOf("cancel:id", "schedule:id"), env.platform.actions)
        consumeMessage(env.presenter, ReminderMessage.UPDATED)

        val updated = env.repository.lastUpdated!!
        env.presenter.onRequestDelete(updated)
        env.presenter.onConfirmDelete()
        runCurrent()
        assertEquals("id", env.repository.deletedId)
        assertTrue(env.platform.actions.count { it == "cancel:id" } >= 2)
        consumeMessage(env.presenter, ReminderMessage.DELETED)
        env.close()
    }

    @Test
    fun writeSuccessWithScheduleFailureKeepsDocumentButDoesNotEmitFullSuccess() = runTest {
        val env = environment(this)
        runCurrent()
        env.platform.failSchedule = true
        env.presenter.onOpenAdd()
        env.presenter.onSubmit()
        runCurrent()
        assertEquals(1, env.repository.addCalls)
        assertEquals(1, env.repository.current.size)
        assertEquals(ReminderScreenMode.LIST, env.presenter.state.value.screenMode)
        assertEquals(ReminderSchedulingUiState.ERROR, env.presenter.state.value.schedulingState)
        consumeMessage(env.presenter, ReminderMessage.SCHEDULING_ERROR)
        env.close()
    }

    @Test
    fun updateAndDeleteWriteFailuresKeepOldListAndRetryableUi() = runTest {
        val existing = reminder("id")
        val env = environment(this, listOf(existing))
        runCurrent()
        env.repository.failWrites = true

        env.presenter.onOpenEdit(existing)
        env.presenter.onTitleChanged("Tên mới")
        env.presenter.onSubmit()
        runCurrent()
        assertEquals(1, env.repository.updateCalls)
        assertEquals(ReminderScreenMode.FORM, env.presenter.state.value.screenMode)
        assertEquals(existing, env.presenter.state.value.reminders.single())
        consumeMessage(env.presenter, ReminderMessage.WRITE_ERROR)

        env.presenter.onCancelForm()
        env.platform.actions.clear()
        env.presenter.onRequestDelete(existing)
        env.presenter.onConfirmDelete()
        env.presenter.onConfirmDelete()
        runCurrent()
        assertEquals(1, env.repository.deleteCalls)
        assertEquals(existing, env.presenter.state.value.reminders.single())
        assertEquals(existing, env.presenter.state.value.deletingReminder)
        assertEquals(listOf("cancel:id", "cancel:id", "schedule:id"), env.platform.actions)
        consumeMessage(env.presenter, ReminderMessage.WRITE_ERROR)
        env.close()
    }

    @Test
    fun deniedPermissionsEmitOneRequestAtATimeAndNeverFullSuccess() = runTest {
        val env = environment(this)
        runCurrent()
        env.platform.exact = false
        env.platform.notificationPermission = ReminderPermissionState.DENIED
        env.platform.exactPermission = ReminderPermissionState.DENIED
        env.presenter.onOpenAdd()
        env.presenter.onSubmit()
        runCurrent()

        val first = env.presenter.state.value.pendingEvent!!
        assertEquals(
            ReminderPermission.NOTIFICATIONS,
            assertIs<ReminderUiEvent.RequestPermission>(first.event).permission
        )
        env.presenter.onPermissionRequestResult(ReminderPermission.NOTIFICATIONS, granted = false)
        runCurrent()
        val denied = env.presenter.state.value.pendingEvent!!
        assertEquals(
            ReminderMessage.PERMISSION_DENIED,
            assertIs<ReminderUiEvent.Message>(denied.event).kind
        )
        env.presenter.consumeEvent(denied.id)
        val next = env.presenter.state.value.pendingEvent!!
        assertEquals(ReminderPermission.EXACT_ALARM, assertIs<ReminderUiEvent.RequestPermission>(next.event).permission)
        env.close()
    }

    @Test
    fun toggleIsOptimisticBlocksDuplicateAndRollsBackOnWriteFailure() = runTest {
        val existing = reminder("id", enabled = true)
        val env = environment(this, listOf(existing))
        runCurrent()
        env.repository.failWrites = true
        env.repository.writeGate = CompletableDeferred()
        env.presenter.onToggle(existing, false)
        env.presenter.onToggle(existing, false)
        runCurrent()
        assertFalse(env.presenter.state.value.reminders.single().isEnabled)
        assertEquals(1, env.repository.toggleCalls)
        env.repository.writeGate?.complete(Unit)
        runCurrent()
        assertTrue(env.presenter.state.value.reminders.single().isEnabled)
        consumeMessage(env.presenter, ReminderMessage.WRITE_ERROR)
        env.close()
    }

    @Test
    fun backClosesNestedStateThenNavigatesAndUidChangeClearsForm() = runTest {
        val env = environment(this)
        runCurrent()
        env.presenter.onOpenAdd()
        env.presenter.onOpenFrequencyPicker()
        env.presenter.onBack()
        assertEquals(ReminderPicker.NONE, env.presenter.state.value.activePicker)
        env.presenter.onBack()
        assertEquals(ReminderScreenMode.LIST, env.presenter.state.value.screenMode)
        env.presenter.onBack()
        val back = env.presenter.state.value.pendingEvent!!
        assertIs<ReminderUiEvent.NavigateBack>(back.event)
        env.presenter.consumeEvent(back.id)

        env.presenter.onOpenAdd()
        env.session.setUserId("other")
        runCurrent()
        assertEquals("other", env.presenter.state.value.userId)
        assertEquals(ReminderScreenMode.LIST, env.presenter.state.value.screenMode)
        assertTrue(env.presenter.state.value.reminders.isEmpty())
        env.close()
    }

    private fun environment(
        scope: CoroutineScope,
        initial: List<Reminder> = emptyList()
    ): PresenterEnvironment {
        val repository = PresenterReminderRepository(initial)
        val platform = PresenterReminderPlatform()
        val reconcile = ReconcileReminderSchedulingUseCase(platform, platform, PresenterReminderClock)
        val session = ReminderSessionController(scope, repository, reconcile)
        session.setUserId("user")
        val presenter = ReminderUiPresenter(scope, session, repository, reconcile, PresenterReminderClock)
        return PresenterEnvironment(repository, platform, session, presenter)
    }

    private fun reminder(id: String, enabled: Boolean = true) = Reminder(
        id = id,
        userId = "user",
        title = "Ghi chép",
        frequency = "Hàng ngày",
        startDate = "10 thg 9, 2026",
        time = "20:15",
        note = "Ghi lại chi tiêu",
        isEnabled = enabled
    )

    private fun error(message: String) = RepositoryError(RepositoryErrorCode.NETWORK, message)

    private fun consumeMessage(presenter: ReminderUiPresenter, expected: ReminderMessage) {
        val event = presenter.state.value.pendingEvent!!
        assertEquals(expected, assertIs<ReminderUiEvent.Message>(event.event).kind)
        presenter.consumeEvent(event.id)
        assertNull(presenter.state.value.pendingEvent)
    }
}

private data class PresenterEnvironment(
    val repository: PresenterReminderRepository,
    val platform: PresenterReminderPlatform,
    val session: ReminderSessionController,
    val presenter: ReminderUiPresenter
) {
    fun close() {
        presenter.close()
        session.close()
    }
}

private object PresenterReminderClock : ReminderDateTimeProvider {
    override fun currentLocalDateTime() = ReminderLocalDateTime(
        ReminderLocalDate(2026, 9, 10), ReminderLocalTime(8, 0)
    )
}

private class PresenterReminderRepository(initial: List<Reminder>) : ReminderRepository {
    val source = MutableStateFlow<RepositoryResult<List<Reminder>>>(RepositoryResult.Success(initial))
    var current = initial
    var failWrites = false
    var writeGate: CompletableDeferred<Unit>? = null
    var addCalls = 0
    var updateCalls = 0
    var deleteCalls = 0
    var toggleCalls = 0
    var lastAdded: Reminder? = null
    var lastUpdated: Reminder? = null
    var deletedId: String? = null

    override fun observeReminders(userId: String): Flow<RepositoryResult<List<Reminder>>> = source
    override suspend fun getReminders(userId: String) = RepositoryResult.Success(current)
    override suspend fun addReminder(userId: String, reminder: Reminder): RepositoryResult<Unit> {
        addCalls++
        writeGate?.await()
        if (failWrites) return RepositoryResult.Failure(error())
        lastAdded = reminder
        current = current + reminder
        source.value = RepositoryResult.Success(current)
        return RepositoryResult.Success(Unit)
    }
    override suspend fun updateReminder(userId: String, reminder: Reminder): RepositoryResult<Unit> {
        updateCalls++
        writeGate?.await()
        if (failWrites) return RepositoryResult.Failure(error())
        lastUpdated = reminder
        current = current.map { if (it.id == reminder.id) reminder else it }
        source.value = RepositoryResult.Success(current)
        return RepositoryResult.Success(Unit)
    }
    override suspend fun deleteReminder(userId: String, reminderId: String): RepositoryResult<Unit> {
        deleteCalls++
        writeGate?.await()
        if (failWrites) return RepositoryResult.Failure(error())
        deletedId = reminderId
        current = current.filterNot { it.id == reminderId }
        source.value = RepositoryResult.Success(current)
        return RepositoryResult.Success(Unit)
    }
    override suspend fun setReminderEnabled(userId: String, reminderId: String, isEnabled: Boolean): RepositoryResult<Unit> {
        toggleCalls++
        writeGate?.await()
        if (failWrites) return RepositoryResult.Failure(error())
        current = current.map { if (it.id == reminderId) it.copy(isEnabled = isEnabled) else it }
        source.value = RepositoryResult.Success(current)
        return RepositoryResult.Success(Unit)
    }
    private fun error() = RepositoryError(RepositoryErrorCode.UNKNOWN, "write failed")
}

private class PresenterReminderPlatform : ReminderPlatformScheduler, ReminderPermissionGateway {
    val actions = mutableListOf<String>()
    var failSchedule = false
    var exact = true
    var notificationPermission = ReminderPermissionState.GRANTED
    var exactPermission = ReminderPermissionState.GRANTED
    override suspend fun schedule(request: ReminderScheduleRequest): ReminderPlatformScheduleResult {
        actions += "schedule:${request.reminder.id}"
        return if (failSchedule) ReminderPlatformScheduleResult.Failure("schedule failed")
        else ReminderPlatformScheduleResult.Scheduled(exact)
    }
    override suspend fun cancel(reminderId: String): ReminderPlatformScheduleResult {
        actions += "cancel:$reminderId"
        return ReminderPlatformScheduleResult.Cancelled
    }
    override fun permissionState(permission: ReminderPermission): ReminderPermissionState = when (permission) {
        ReminderPermission.NOTIFICATIONS -> notificationPermission
        ReminderPermission.EXACT_ALARM -> exactPermission
    }
}
