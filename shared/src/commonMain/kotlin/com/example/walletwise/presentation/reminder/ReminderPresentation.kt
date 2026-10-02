package com.example.walletwise.presentation.reminder

import com.example.walletwise.domain.model.DEFAULT_REMINDER_NOTE
import com.example.walletwise.domain.model.DEFAULT_REMINDER_TITLE
import com.example.walletwise.domain.model.REMINDER_FREQUENCY_DAILY
import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.repository.ReminderRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.service.ReconcileReminderSchedulingUseCase
import com.example.walletwise.domain.service.ReminderCalendar
import com.example.walletwise.domain.service.ReminderDateTimeProvider
import com.example.walletwise.domain.service.ReminderLocalDate
import com.example.walletwise.domain.service.ReminderLocalTime
import com.example.walletwise.domain.service.ReminderPermission
import com.example.walletwise.domain.service.ReminderReconcileReport
import com.example.walletwise.domain.service.ReminderScheduleCalculator
import com.example.walletwise.domain.service.ReminderSchedulingOutcome
import com.example.walletwise.domain.service.ReminderSchedulingStatus
import com.example.walletwise.domain.usecase.AddReminderUseCase
import com.example.walletwise.domain.usecase.DeleteReminderUseCase
import com.example.walletwise.domain.usecase.ReminderUseCaseResult
import com.example.walletwise.domain.usecase.SetReminderEnabledUseCase
import com.example.walletwise.domain.usecase.UpdateReminderUseCase
import com.example.walletwise.domain.validation.ReminderValidationError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReminderScreenMode { LIST, FORM }

enum class ReminderPicker { NONE, FREQUENCY, DATE, TIME }

enum class ReminderSchedulingUiState { READY, PERMISSION_REQUIRED, ERROR }

enum class ReminderMessage {
    ADDED,
    UPDATED,
    DELETED,
    WRITE_ERROR,
    SCHEDULING_ERROR,
    PERMISSION_DENIED
}

sealed interface ReminderUiEvent {
    data object NavigateBack : ReminderUiEvent
    data class Message(val kind: ReminderMessage, val detail: String? = null) : ReminderUiEvent
    data class RequestPermission(val permission: ReminderPermission) : ReminderUiEvent
}

data class ReminderEventEnvelope(val id: Long, val event: ReminderUiEvent)

data class ReminderYearMonth(val year: Int, val month: Int)

data class ReminderUiState(
    val userId: String? = null,
    val isLoading: Boolean = false,
    val reminders: List<Reminder> = emptyList(),
    val repositoryError: RepositoryError? = null,
    val schedulingState: ReminderSchedulingUiState = ReminderSchedulingUiState.READY,
    val schedulingError: String? = null,
    val screenMode: ReminderScreenMode = ReminderScreenMode.LIST,
    val editingReminderId: String? = null,
    val selectedDetailReminder: Reminder? = null,
    val deletingReminder: Reminder? = null,
    val title: String = "",
    val frequency: String = REMINDER_FREQUENCY_DAILY,
    val startDate: String = "",
    val time: String = "20:15",
    val note: String = "",
    val formEnabled: Boolean = true,
    val validationError: ReminderValidationError? = null,
    val isSaving: Boolean = false,
    val deletingReminderId: String? = null,
    val togglingReminderIds: Set<String> = emptySet(),
    val activePicker: ReminderPicker = ReminderPicker.NONE,
    val datePickerMonth: ReminderYearMonth,
    val datePickerSelectedDay: Int,
    val timePickerHour: Int = 20,
    val timePickerMinute: Int = 15,
    val pendingEvent: ReminderEventEnvelope? = null
) {
    val isEmpty: Boolean get() = !isLoading && reminders.isEmpty()
    val isEditMode: Boolean get() = editingReminderId != null
}

class ReminderUiPresenter(
    private val scope: CoroutineScope,
    private val sessionController: ReminderSessionController,
    repository: ReminderRepository,
    private val reconcileScheduling: ReconcileReminderSchedulingUseCase,
    private val dateTimeProvider: ReminderDateTimeProvider
) {
    private val addReminder = AddReminderUseCase(repository)
    private val updateReminder = UpdateReminderUseCase(repository)
    private val deleteReminder = DeleteReminderUseCase(repository)
    private val setReminderEnabled = SetReminderEnabledUseCase(repository)
    private val initialDate = dateTimeProvider.currentLocalDateTime().date
    private val mutableState = MutableStateFlow(
        ReminderUiState(
            startDate = ReminderScheduleCalculator.formatStartDate(initialDate),
            datePickerMonth = ReminderYearMonth(initialDate.year, initialDate.month),
            datePickerSelectedDay = initialDate.dayOfMonth
        )
    )
    val state: StateFlow<ReminderUiState> = mutableState.asStateFlow()
    private val sourceJob: Job
    private val optimisticEnabled = mutableMapOf<String, Boolean>()
    private val requestedPermissions = mutableSetOf<ReminderPermission>()
    private var nextEventId = 1L

    init {
        sourceJob = scope.launch {
            sessionController.state.collect { session ->
                val current = mutableState.value
                val identityChanged = current.userId != session.userId
                if (identityChanged) {
                    optimisticEnabled.clear()
                    requestedPermissions.clear()
                }
                session.reminders.forEach { reminder ->
                    if (optimisticEnabled[reminder.id] == reminder.isEnabled) {
                        optimisticEnabled.remove(reminder.id)
                    }
                }
                val displayed = session.reminders.map { reminder ->
                    optimisticEnabled[reminder.id]?.let { reminder.copy(isEnabled = it) } ?: reminder
                }
                mutableState.value = current.copy(
                    userId = session.userId,
                    isLoading = session.isLoading,
                    reminders = displayed,
                    repositoryError = session.error,
                    schedulingState = schedulingState(session.schedulingReport),
                    schedulingError = session.schedulingReport.errors.firstOrNull(),
                    screenMode = if (identityChanged) ReminderScreenMode.LIST else current.screenMode,
                    editingReminderId = if (identityChanged) null else current.editingReminderId,
                    selectedDetailReminder = if (identityChanged) null else current.selectedDetailReminder,
                    deletingReminder = if (identityChanged) null else current.deletingReminder,
                    isSaving = if (identityChanged) false else current.isSaving,
                    deletingReminderId = if (identityChanged) null else current.deletingReminderId,
                    togglingReminderIds = if (identityChanged) emptySet() else current.togglingReminderIds,
                    validationError = if (identityChanged) null else current.validationError
                )
                requestNextPermission(session.schedulingReport.permissions)
            }
        }
    }

    fun onOpenAdd() {
        val today = dateTimeProvider.currentLocalDateTime().date
        mutableState.value = mutableState.value.copy(
            screenMode = ReminderScreenMode.FORM,
            editingReminderId = null,
            selectedDetailReminder = null,
            title = "",
            frequency = REMINDER_FREQUENCY_DAILY,
            startDate = ReminderScheduleCalculator.formatStartDate(today),
            time = "20:15",
            note = "",
            formEnabled = true,
            validationError = null,
            activePicker = ReminderPicker.NONE,
            datePickerMonth = ReminderYearMonth(today.year, today.month),
            datePickerSelectedDay = today.dayOfMonth,
            timePickerHour = 20,
            timePickerMinute = 15
        )
    }

    fun onOpenDetail(reminder: Reminder) {
        mutableState.value = mutableState.value.copy(selectedDetailReminder = reminder)
    }

    fun onDismissDetail() {
        mutableState.value = mutableState.value.copy(selectedDetailReminder = null)
    }

    fun onOpenEdit(reminder: Reminder) {
        val parsedDate = ReminderScheduleCalculator.parseStartDate(reminder.startDate) ?: initialDate
        val parsedTime = ReminderScheduleCalculator.parseTime(reminder.time) ?: ReminderLocalTime(20, 15)
        mutableState.value = mutableState.value.copy(
            screenMode = ReminderScreenMode.FORM,
            editingReminderId = reminder.id,
            selectedDetailReminder = null,
            title = reminder.title,
            frequency = reminder.frequency,
            startDate = reminder.startDate,
            time = reminder.time,
            note = reminder.note,
            formEnabled = reminder.isEnabled,
            validationError = null,
            activePicker = ReminderPicker.NONE,
            datePickerMonth = ReminderYearMonth(parsedDate.year, parsedDate.month),
            datePickerSelectedDay = parsedDate.dayOfMonth,
            timePickerHour = parsedTime.hour,
            timePickerMinute = parsedTime.minute
        )
    }

    fun onCancelForm() {
        if (mutableState.value.isSaving) return
        closeForm()
    }

    fun onTitleChanged(value: String) = updateForm { copy(title = value, validationError = null) }
    fun onNoteChanged(value: String) = updateForm { copy(note = value, validationError = null) }

    fun onOpenFrequencyPicker() = updateForm { copy(activePicker = ReminderPicker.FREQUENCY) }

    fun onFrequencySelected(value: String) = updateForm {
        copy(frequency = value, activePicker = ReminderPicker.NONE, validationError = null)
    }

    fun onOpenDatePicker() {
        val parsed = ReminderScheduleCalculator.parseStartDate(mutableState.value.startDate) ?: initialDate
        updateForm {
            copy(
                activePicker = ReminderPicker.DATE,
                datePickerMonth = ReminderYearMonth(parsed.year, parsed.month),
                datePickerSelectedDay = parsed.dayOfMonth
            )
        }
    }

    fun onPreviousDatePickerMonth() = shiftDatePickerMonth(-1)
    fun onNextDatePickerMonth() = shiftDatePickerMonth(1)

    fun onDatePickerDaySelected(day: Int) {
        val month = mutableState.value.datePickerMonth
        if (day !in 1..ReminderCalendar.daysInMonth(month.year, month.month)) return
        updateForm { copy(datePickerSelectedDay = day) }
    }

    fun onConfirmDatePicker() {
        val current = mutableState.value
        val day = current.datePickerSelectedDay.coerceIn(
            1,
            ReminderCalendar.daysInMonth(current.datePickerMonth.year, current.datePickerMonth.month)
        )
        val date = ReminderLocalDate(current.datePickerMonth.year, current.datePickerMonth.month, day)
        updateForm {
            copy(
                startDate = ReminderScheduleCalculator.formatStartDate(date),
                activePicker = ReminderPicker.NONE,
                validationError = null
            )
        }
    }

    fun onOpenTimePicker() {
        val parsed = ReminderScheduleCalculator.parseTime(mutableState.value.time) ?: ReminderLocalTime(20, 15)
        updateForm {
            copy(
                activePicker = ReminderPicker.TIME,
                timePickerHour = parsed.hour,
                timePickerMinute = parsed.minute
            )
        }
    }

    fun onTimePickerHourChanged(value: Int) = updateForm { copy(timePickerHour = value.coerceIn(0, 23)) }
    fun onTimePickerMinuteChanged(value: Int) = updateForm { copy(timePickerMinute = value.coerceIn(0, 59)) }

    fun onConfirmTimePicker() = updateForm {
        copy(
            time = ReminderScheduleCalculator.formatTime(ReminderLocalTime(timePickerHour, timePickerMinute)),
            activePicker = ReminderPicker.NONE,
            validationError = null
        )
    }

    fun onDismissPicker() = updateForm { copy(activePicker = ReminderPicker.NONE) }

    fun onSubmit() {
        val current = mutableState.value
        if (current.isSaving || current.screenMode != ReminderScreenMode.FORM) return
        val userId = current.userId ?: return emitWriteError("Bạn cần đăng nhập để lưu lời nhắc")
        val existing = current.editingReminderId?.let { id -> current.reminders.firstOrNull { it.id == id } }
        val candidate = (existing ?: Reminder()).copy(
            userId = userId,
            title = current.title.ifBlank { DEFAULT_REMINDER_TITLE },
            frequency = current.frequency,
            startDate = current.startDate,
            time = current.time,
            note = current.note.ifBlank { DEFAULT_REMINDER_NOTE },
            isEnabled = existing?.isEnabled ?: current.formEnabled
        )
        mutableState.value = current.copy(isSaving = true, validationError = null, repositoryError = null)
        scope.launch {
            val result = if (existing == null) addReminder(userId, candidate) else updateReminder(userId, candidate)
            if (sessionController.state.value.userId != userId) return@launch
            when (result) {
                is ReminderUseCaseResult.Success -> {
                    val outcome = reconcileScheduling.apply(result.value)
                    closeForm()
                    handleSchedulingOutcome(
                        outcome,
                        if (existing == null) ReminderMessage.ADDED else ReminderMessage.UPDATED
                    )
                }
                is ReminderUseCaseResult.ValidationFailure -> mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    validationError = result.error
                )
                is ReminderUseCaseResult.RepositoryFailure -> {
                    mutableState.value = mutableState.value.copy(
                        isSaving = false,
                        repositoryError = result.error
                    )
                    emit(ReminderUiEvent.Message(ReminderMessage.WRITE_ERROR, result.error.message))
                }
            }
        }
    }

    fun onRequestDelete(reminder: Reminder) {
        if (mutableState.value.deletingReminderId != null) return
        mutableState.value = mutableState.value.copy(
            selectedDetailReminder = null,
            deletingReminder = reminder
        )
    }

    fun onCancelDelete() {
        if (mutableState.value.deletingReminderId != null) return
        mutableState.value = mutableState.value.copy(deletingReminder = null)
    }

    fun onConfirmDelete() {
        val current = mutableState.value
        val reminder = current.deletingReminder ?: return
        if (current.deletingReminderId != null) return
        val userId = current.userId ?: return emitWriteError("Bạn cần đăng nhập để xóa lời nhắc")
        mutableState.value = current.copy(deletingReminderId = reminder.id, repositoryError = null)
        scope.launch {
            val cancelOutcome = reconcileScheduling.cancel(userId, reminder.id)
            if (sessionController.state.value.userId != userId) return@launch
            if (cancelOutcome.status == ReminderSchedulingStatus.FAILED) {
                mutableState.value = mutableState.value.copy(
                    deletingReminderId = null,
                    schedulingState = ReminderSchedulingUiState.ERROR,
                    schedulingError = cancelOutcome.errorMessage
                )
                emit(ReminderUiEvent.Message(ReminderMessage.SCHEDULING_ERROR, cancelOutcome.errorMessage))
                return@launch
            }
            when (val result = deleteReminder(userId, reminder.id)) {
                is ReminderUseCaseResult.Success -> {
                    mutableState.value = mutableState.value.copy(
                        deletingReminderId = null,
                        deletingReminder = null
                    )
                    emit(ReminderUiEvent.Message(ReminderMessage.DELETED))
                }
                is ReminderUseCaseResult.ValidationFailure -> mutableState.value = mutableState.value.copy(
                    deletingReminderId = null,
                    validationError = result.error
                )
                is ReminderUseCaseResult.RepositoryFailure -> {
                    val restoreOutcome = reconcileScheduling.apply(reminder, force = true)
                    mutableState.value = mutableState.value.copy(
                        deletingReminderId = null,
                        repositoryError = result.error,
                        schedulingState = if (restoreOutcome.status == ReminderSchedulingStatus.FAILED) {
                            ReminderSchedulingUiState.ERROR
                        } else {
                            mutableState.value.schedulingState
                        }
                    )
                    emit(ReminderUiEvent.Message(ReminderMessage.WRITE_ERROR, result.error.message))
                }
            }
        }
    }

    fun onToggle(reminder: Reminder, enabled: Boolean) {
        val current = mutableState.value
        if (reminder.id in current.togglingReminderIds || current.userId == null) return
        val userId = current.userId
        optimisticEnabled[reminder.id] = enabled
        mutableState.value = current.copy(
            reminders = current.reminders.map { if (it.id == reminder.id) it.copy(isEnabled = enabled) else it },
            togglingReminderIds = current.togglingReminderIds + reminder.id,
            repositoryError = null
        )
        scope.launch {
            when (val result = setReminderEnabled(userId, reminder.id, enabled)) {
                is ReminderUseCaseResult.Success -> {
                    if (sessionController.state.value.userId != userId) return@launch
                    val outcome = reconcileScheduling.apply(reminder.copy(userId = userId, isEnabled = enabled))
                    mutableState.value = mutableState.value.copy(
                        togglingReminderIds = mutableState.value.togglingReminderIds - reminder.id
                    )
                    handleSchedulingOutcome(outcome, successMessage = null)
                }
                is ReminderUseCaseResult.ValidationFailure -> rollbackToggle(reminder, result.error)
                is ReminderUseCaseResult.RepositoryFailure -> {
                    optimisticEnabled.remove(reminder.id)
                    mutableState.value = mutableState.value.copy(
                        reminders = mutableState.value.reminders.map {
                            if (it.id == reminder.id) reminder else it
                        },
                        togglingReminderIds = mutableState.value.togglingReminderIds - reminder.id,
                        repositoryError = result.error
                    )
                    emit(ReminderUiEvent.Message(ReminderMessage.WRITE_ERROR, result.error.message))
                }
            }
        }
    }

    fun onPermissionRequestResult(permission: ReminderPermission, granted: Boolean) {
        val currentEvent = mutableState.value.pendingEvent
        if ((currentEvent?.event as? ReminderUiEvent.RequestPermission)?.permission == permission) {
            mutableState.value = mutableState.value.copy(pendingEvent = null)
        }
        requestedPermissions += permission
        if (!granted) {
            emit(ReminderUiEvent.Message(ReminderMessage.PERMISSION_DENIED))
        }
        sessionController.forceReconcile()
    }

    fun onBack() {
        val current = mutableState.value
        when {
            current.activePicker != ReminderPicker.NONE -> onDismissPicker()
            current.deletingReminder != null -> onCancelDelete()
            current.selectedDetailReminder != null -> onDismissDetail()
            current.screenMode == ReminderScreenMode.FORM -> onCancelForm()
            else -> emit(ReminderUiEvent.NavigateBack)
        }
    }

    fun consumeEvent(id: Long) {
        val current = mutableState.value
        if (current.pendingEvent?.id == id) {
            val consumedEvent = current.pendingEvent.event
            mutableState.value = current.copy(pendingEvent = null)
            if (consumedEvent !is ReminderUiEvent.RequestPermission) {
                requestNextPermission(sessionController.state.value.schedulingReport.permissions)
            }
        }
    }

    fun close() {
        sourceJob.cancel()
    }

    private fun updateForm(transform: ReminderUiState.() -> ReminderUiState) {
        if (mutableState.value.isSaving) return
        mutableState.value = mutableState.value.transform()
    }

    private fun shiftDatePickerMonth(delta: Int) {
        val current = mutableState.value
        val totalMonths = current.datePickerMonth.year * 12L + current.datePickerMonth.month - 1L + delta
        if (totalMonths < 12L || totalMonths > 9999L * 12L + 11L) return
        updateForm {
            copy(datePickerMonth = ReminderYearMonth((totalMonths / 12L).toInt(), (totalMonths % 12L).toInt() + 1))
        }
    }

    private fun closeForm() {
        mutableState.value = mutableState.value.copy(
            screenMode = ReminderScreenMode.LIST,
            editingReminderId = null,
            isSaving = false,
            validationError = null,
            activePicker = ReminderPicker.NONE
        )
    }

    private fun rollbackToggle(reminder: Reminder, error: ReminderValidationError) {
        optimisticEnabled.remove(reminder.id)
        mutableState.value = mutableState.value.copy(
            reminders = mutableState.value.reminders.map { if (it.id == reminder.id) reminder else it },
            togglingReminderIds = mutableState.value.togglingReminderIds - reminder.id,
            validationError = error
        )
    }

    private fun handleSchedulingOutcome(outcome: ReminderSchedulingOutcome, successMessage: ReminderMessage?) {
        when (outcome.status) {
            ReminderSchedulingStatus.COMPLETE,
            ReminderSchedulingStatus.NO_CHANGE -> {
                mutableState.value = mutableState.value.copy(
                    schedulingState = ReminderSchedulingUiState.READY,
                    schedulingError = null
                )
                successMessage?.let { emit(ReminderUiEvent.Message(it)) }
            }
            ReminderSchedulingStatus.PERMISSION_REQUIRED -> {
                mutableState.value = mutableState.value.copy(
                    schedulingState = ReminderSchedulingUiState.PERMISSION_REQUIRED,
                    schedulingError = null
                )
                requestNextPermission(outcome.permissions)
            }
            ReminderSchedulingStatus.FAILED -> {
                mutableState.value = mutableState.value.copy(
                    schedulingState = ReminderSchedulingUiState.ERROR,
                    schedulingError = outcome.errorMessage
                )
                emit(ReminderUiEvent.Message(ReminderMessage.SCHEDULING_ERROR, outcome.errorMessage))
            }
        }
    }

    private fun requestNextPermission(permissions: Set<ReminderPermission>) {
        if (mutableState.value.pendingEvent != null) return
        val permission = listOf(ReminderPermission.NOTIFICATIONS, ReminderPermission.EXACT_ALARM)
            .firstOrNull { it in permissions && it !in requestedPermissions }
            ?: return
        requestedPermissions += permission
        emit(ReminderUiEvent.RequestPermission(permission))
    }

    private fun schedulingState(report: ReminderReconcileReport): ReminderSchedulingUiState = when {
        report.hasFailure -> ReminderSchedulingUiState.ERROR
        report.permissions.isNotEmpty() -> ReminderSchedulingUiState.PERMISSION_REQUIRED
        else -> ReminderSchedulingUiState.READY
    }

    private fun emitWriteError(detail: String) {
        emit(ReminderUiEvent.Message(ReminderMessage.WRITE_ERROR, detail))
    }

    private fun emit(event: ReminderUiEvent) {
        mutableState.value = mutableState.value.copy(
            pendingEvent = ReminderEventEnvelope(nextEventId++, event)
        )
    }
}
