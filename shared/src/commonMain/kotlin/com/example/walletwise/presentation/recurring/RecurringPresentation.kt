package com.example.walletwise.presentation.recurring

import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.model.DefaultCategories
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_MONTHLY
import com.example.walletwise.domain.model.RECURRING_TIMES_COUNT_WIRE_VALUES
import com.example.walletwise.domain.model.TRANSACTION_TYPE_EXPENSE
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.repository.RecurringTransactionRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.service.RecurringAutomationCoordinator
import com.example.walletwise.domain.service.RecurringDateTimeProvider
import com.example.walletwise.domain.service.RecurringProcessingOutcome
import com.example.walletwise.domain.service.RecurringProcessingStatus
import com.example.walletwise.domain.service.RecurringScheduleCalculator
import com.example.walletwise.domain.service.ReminderCalendar
import com.example.walletwise.domain.service.ReminderLocalDate
import com.example.walletwise.domain.service.ReminderLocalTime
import com.example.walletwise.domain.service.ReminderPermission
import com.example.walletwise.domain.service.ReminderPermissionGateway
import com.example.walletwise.domain.service.ReminderPermissionState
import com.example.walletwise.domain.usecase.AddRecurringTransactionUseCase
import com.example.walletwise.domain.usecase.DeleteRecurringTransactionUseCase
import com.example.walletwise.domain.usecase.RecurringUseCaseResult
import com.example.walletwise.domain.usecase.SetRecurringTransactionEnabledUseCase
import com.example.walletwise.domain.usecase.UpdateRecurringTransactionUseCase
import com.example.walletwise.domain.validation.RecurringValidationError
import com.example.walletwise.presentation.category.CategorySessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RecurringScreenMode { LIST, FORM }

enum class RecurringPicker { NONE, FREQUENCY, TIMES_COUNT, DATE, TIME, CATEGORY, PAYMENT_METHOD }

enum class RecurringMessage {
    ADDED,
    UPDATED,
    DELETED,
    WRITE_ERROR,
    SCHEDULING_ERROR,
    PERMISSION_DENIED
}

sealed interface RecurringUiEvent {
    data object NavigateBack : RecurringUiEvent
    data class Message(val kind: RecurringMessage, val detail: String? = null) : RecurringUiEvent
    data class RequestPermission(val permission: ReminderPermission) : RecurringUiEvent
}

data class RecurringEventEnvelope(val id: Long, val event: RecurringUiEvent)

data class RecurringYearMonth(val year: Int, val month: Int)

data class RecurringUiState(
    val userId: String? = null,
    val isLoading: Boolean = false,
    val recurring: List<RecurringTransaction> = emptyList(),
    val repositoryError: RepositoryError? = null,
    val schedulingError: String? = null,
    val screenMode: RecurringScreenMode = RecurringScreenMode.LIST,
    val editingRecurringId: String? = null,
    val draftRecurringId: String = RecurringTransaction().id,
    val selectedDetail: RecurringTransaction? = null,
    val deletingRecurring: RecurringTransaction? = null,
    val title: String = "",
    val amount: String = "",
    val type: String = TRANSACTION_TYPE_EXPENSE,
    val category: String = "",
    val paymentMethod: String = "Tiền mặt",
    val frequency: String = RECURRING_FREQUENCY_MONTHLY,
    val timesCount: String = "1",
    val startDate: String = "",
    val time: String = "20:15",
    val note: String = "",
    val categories: List<Category> = DefaultCategories,
    val validationError: RecurringValidationError? = null,
    val isSaving: Boolean = false,
    val deletingRecurringId: String? = null,
    val togglingRecurringIds: Set<String> = emptySet(),
    val activePicker: RecurringPicker = RecurringPicker.NONE,
    val datePickerMonth: RecurringYearMonth,
    val datePickerSelectedDay: Int,
    val timePickerHour: Int = 20,
    val timePickerMinute: Int = 15,
    val pendingEvent: RecurringEventEnvelope? = null
) {
    val isEmpty: Boolean get() = !isLoading && recurring.isEmpty()
    val isEditMode: Boolean get() = editingRecurringId != null
    val currentCategories: List<Category> get() = categories.filter { it.type == type }
}

class RecurringUiPresenter(
    private val scope: CoroutineScope,
    private val sessionController: RecurringSessionController,
    private val categorySessionController: CategorySessionController,
    repository: RecurringTransactionRepository,
    private val coordinator: RecurringAutomationCoordinator,
    private val dateTimeProvider: RecurringDateTimeProvider,
    private val permissionGateway: ReminderPermissionGateway
) {
    private val addRecurring = AddRecurringTransactionUseCase(repository)
    private val updateRecurring = UpdateRecurringTransactionUseCase(repository)
    private val deleteRecurring = DeleteRecurringTransactionUseCase(repository)
    private val setEnabled = SetRecurringTransactionEnabledUseCase(repository)
    private val initialDate = dateTimeProvider.currentLocalDateTime().date
    private val mutableState = MutableStateFlow(
        RecurringUiState(
            startDate = RecurringScheduleCalculator.formatStartDate(initialDate),
            datePickerMonth = RecurringYearMonth(initialDate.year, initialDate.month),
            datePickerSelectedDay = initialDate.dayOfMonth
        )
    )
    val state: StateFlow<RecurringUiState> = mutableState.asStateFlow()
    private val sessionJob: Job
    private val categoryJob: Job
    private val optimisticEnabled = mutableMapOf<String, Boolean>()
    private val requestedPermissions = mutableSetOf<ReminderPermission>()
    private val queuedPermissions = mutableListOf<ReminderPermission>()
    private var nextEventId = 1L

    init {
        sessionJob = scope.launch {
            sessionController.state.collect { session ->
                val current = mutableState.value
                val identityChanged = current.userId != session.userId
                if (identityChanged) {
                    optimisticEnabled.clear()
                    requestedPermissions.clear()
                    queuedPermissions.clear()
                }
                session.recurring.forEach { rule ->
                    if (optimisticEnabled[rule.id] == rule.isEnabled) optimisticEnabled.remove(rule.id)
                }
                val displayed = session.recurring.map { rule ->
                    optimisticEnabled[rule.id]?.let { rule.copy(isEnabled = it) } ?: rule
                }
                mutableState.value = current.copy(
                    userId = session.userId,
                    isLoading = session.isLoading,
                    recurring = displayed,
                    repositoryError = session.error,
                    schedulingError = session.reconcileReport.errors.firstOrNull(),
                    screenMode = if (identityChanged) RecurringScreenMode.LIST else current.screenMode,
                    editingRecurringId = if (identityChanged) null else current.editingRecurringId,
                    selectedDetail = if (identityChanged) null else current.selectedDetail,
                    deletingRecurring = if (identityChanged) null else current.deletingRecurring,
                    isSaving = if (identityChanged) false else current.isSaving,
                    deletingRecurringId = if (identityChanged) null else current.deletingRecurringId,
                    togglingRecurringIds = if (identityChanged) emptySet() else current.togglingRecurringIds,
                    validationError = if (identityChanged) null else current.validationError
                )
            }
        }
        categoryJob = scope.launch {
            categorySessionController.state.collect { categoryState ->
                val current = mutableState.value
                val categories = categoryState.categories
                val available = categories.filter { it.type == current.type }
                mutableState.value = current.copy(
                    categories = categories,
                    category = current.category.takeIf { selected -> available.any { it.name == selected } }
                        ?: available.firstOrNull()?.name.orEmpty()
                )
            }
        }
    }

    fun onOpenAdd() {
        val today = dateTimeProvider.currentLocalDateTime().date
        val type = TRANSACTION_TYPE_EXPENSE
        val category = mutableState.value.categories.firstOrNull { it.type == type }?.name.orEmpty()
        mutableState.value = mutableState.value.copy(
            screenMode = RecurringScreenMode.FORM,
            editingRecurringId = null,
            draftRecurringId = RecurringTransaction().id,
            selectedDetail = null,
            title = "",
            amount = "",
            type = type,
            category = category,
            paymentMethod = "Tiền mặt",
            frequency = RECURRING_FREQUENCY_MONTHLY,
            timesCount = "1",
            startDate = RecurringScheduleCalculator.formatStartDate(today),
            time = "20:15",
            note = "",
            validationError = null,
            activePicker = RecurringPicker.NONE,
            datePickerMonth = RecurringYearMonth(today.year, today.month),
            datePickerSelectedDay = today.dayOfMonth,
            timePickerHour = 20,
            timePickerMinute = 15
        )
    }

    fun onOpenDetail(recurring: RecurringTransaction) = update { copy(selectedDetail = recurring) }
    fun onDismissDetail() = update { copy(selectedDetail = null) }

    fun onOpenEdit(recurring: RecurringTransaction) {
        val date = RecurringScheduleCalculator.parseStartDate(recurring.startDate) ?: initialDate
        val time = RecurringScheduleCalculator.parseTime(recurring.time) ?: ReminderLocalTime(20, 15)
        mutableState.value = mutableState.value.copy(
            screenMode = RecurringScreenMode.FORM,
            editingRecurringId = recurring.id,
            selectedDetail = null,
            title = recurring.title,
            amount = amountInput(recurring.amount),
            type = recurring.type,
            category = recurring.category,
            paymentMethod = recurring.paymentMethod,
            frequency = recurring.frequency,
            timesCount = recurring.timesCount,
            startDate = recurring.startDate,
            time = recurring.time,
            note = recurring.note,
            validationError = null,
            activePicker = RecurringPicker.NONE,
            datePickerMonth = RecurringYearMonth(date.year, date.month),
            datePickerSelectedDay = date.dayOfMonth,
            timePickerHour = time.hour,
            timePickerMinute = time.minute
        )
    }

    fun onCancelForm() {
        if (!mutableState.value.isSaving) closeForm()
    }

    fun onTitleChanged(value: String) = updateForm { copy(title = value, validationError = null) }
    fun onAmountChanged(value: String) = updateForm {
        if (value.isEmpty() || value.all(Char::isDigit)) copy(amount = value, validationError = null) else this
    }
    fun onNoteChanged(value: String) = updateForm { copy(note = value, validationError = null) }

    fun onTypeSelected(value: String) = updateForm {
        val available = categories.filter { it.type == value }
        copy(
            type = value,
            category = category.takeIf { selected -> available.any { it.name == selected } }
                ?: available.firstOrNull()?.name.orEmpty(),
            validationError = null
        )
    }

    fun onOpenPicker(picker: RecurringPicker) {
        if (picker == RecurringPicker.DATE) prepareDatePicker()
        else if (picker == RecurringPicker.TIME) prepareTimePicker()
        else updateForm { copy(activePicker = picker) }
    }

    fun onFrequencySelected(value: String) = select { copy(frequency = value) }
    fun onTimesCountSelected(value: String) {
        if (value in RECURRING_TIMES_COUNT_WIRE_VALUES) select { copy(timesCount = value) }
    }
    fun onCategorySelected(value: String) = select { copy(category = value) }
    fun onPaymentMethodSelected(value: String) = select { copy(paymentMethod = value) }
    fun onDismissPicker() = updateForm { copy(activePicker = RecurringPicker.NONE) }

    fun onPreviousDatePickerMonth() = shiftDatePickerMonth(-1)
    fun onNextDatePickerMonth() = shiftDatePickerMonth(1)
    fun onDatePickerDaySelected(day: Int) {
        val month = mutableState.value.datePickerMonth
        if (day in 1..ReminderCalendar.daysInMonth(month.year, month.month)) {
            updateForm { copy(datePickerSelectedDay = day) }
        }
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
                startDate = RecurringScheduleCalculator.formatStartDate(date),
                activePicker = RecurringPicker.NONE,
                validationError = null
            )
        }
    }

    fun onTimePickerHourChanged(value: Int) = updateForm { copy(timePickerHour = value.coerceIn(0, 23)) }
    fun onTimePickerMinuteChanged(value: Int) = updateForm { copy(timePickerMinute = value.coerceIn(0, 59)) }
    fun onConfirmTimePicker() = updateForm {
        copy(
            time = RecurringScheduleCalculator.formatTime(ReminderLocalTime(timePickerHour, timePickerMinute)),
            activePicker = RecurringPicker.NONE,
            validationError = null
        )
    }

    fun onSubmit() {
        val current = mutableState.value
        if (current.isSaving || current.screenMode != RecurringScreenMode.FORM) return
        val userId = current.userId ?: return emitWriteError("Bạn cần đăng nhập để lưu giao dịch định kỳ")
        val existing = current.editingRecurringId?.let { id -> current.recurring.firstOrNull { it.id == id } }
        val amount = current.amount.toDoubleOrNull() ?: Double.NaN
        val candidate = (existing ?: RecurringTransaction(id = current.draftRecurringId)).copy(
            userId = userId,
            title = current.title,
            amount = amount,
            type = current.type,
            category = current.category,
            paymentMethod = current.paymentMethod,
            frequency = current.frequency,
            timesCount = current.timesCount,
            startDate = current.startDate,
            time = current.time,
            note = current.note
        )
        mutableState.value = current.copy(isSaving = true, validationError = null, repositoryError = null)
        scope.launch {
            val result = if (existing == null) addRecurring(userId, candidate) else updateRecurring(userId, candidate)
            if (sessionController.state.value.userId != userId) return@launch
            when (result) {
                is RecurringUseCaseResult.Success -> {
                    val outcome = coordinator.apply(result.value, forceSchedule = true)
                    closeForm()
                    handleSchedulingOutcome(
                        outcome,
                        if (existing == null) RecurringMessage.ADDED else RecurringMessage.UPDATED
                    )
                }
                is RecurringUseCaseResult.ValidationFailure -> mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    validationError = result.error
                )
                is RecurringUseCaseResult.RepositoryFailure -> {
                    mutableState.value = mutableState.value.copy(
                        isSaving = false,
                        repositoryError = result.error
                    )
                    emit(RecurringUiEvent.Message(RecurringMessage.WRITE_ERROR, result.error.message))
                }
            }
        }
    }

    fun onRequestDelete(recurring: RecurringTransaction) {
        if (mutableState.value.deletingRecurringId == null) {
            update { copy(selectedDetail = null, deletingRecurring = recurring) }
        }
    }

    fun onCancelDelete() {
        if (mutableState.value.deletingRecurringId == null) update { copy(deletingRecurring = null) }
    }

    fun onConfirmDelete() {
        val current = mutableState.value
        val recurring = current.deletingRecurring ?: return
        if (current.deletingRecurringId != null) return
        val userId = current.userId ?: return emitWriteError("Bạn cần đăng nhập để xóa giao dịch định kỳ")
        mutableState.value = current.copy(deletingRecurringId = recurring.id, repositoryError = null)
        scope.launch {
            val cancelled = coordinator.cancel(userId, recurring.id)
            if (sessionController.state.value.userId != userId) return@launch
            if (cancelled.status == RecurringProcessingStatus.FAILED) {
                mutableState.value = mutableState.value.copy(
                    deletingRecurringId = null,
                    schedulingError = cancelled.schedulingError
                )
                emit(RecurringUiEvent.Message(RecurringMessage.SCHEDULING_ERROR, cancelled.schedulingError))
                return@launch
            }
            when (val result = deleteRecurring(userId, recurring.id)) {
                is RecurringUseCaseResult.Success -> {
                    mutableState.value = mutableState.value.copy(
                        deletingRecurringId = null,
                        deletingRecurring = null
                    )
                    emit(RecurringUiEvent.Message(RecurringMessage.DELETED))
                }
                is RecurringUseCaseResult.ValidationFailure -> mutableState.value = mutableState.value.copy(
                    deletingRecurringId = null,
                    validationError = result.error
                )
                is RecurringUseCaseResult.RepositoryFailure -> {
                    val restored = if (recurring.isEnabled) coordinator.apply(recurring, forceSchedule = true) else null
                    mutableState.value = mutableState.value.copy(
                        deletingRecurringId = null,
                        repositoryError = result.error,
                        schedulingError = restored?.schedulingError
                    )
                    emit(RecurringUiEvent.Message(RecurringMessage.WRITE_ERROR, result.error.message))
                }
            }
        }
    }

    fun onToggle(recurring: RecurringTransaction, enabled: Boolean) {
        val current = mutableState.value
        val userId = current.userId ?: return
        if (recurring.id in current.togglingRecurringIds) return
        optimisticEnabled[recurring.id] = enabled
        mutableState.value = current.copy(
            recurring = current.recurring.map { if (it.id == recurring.id) it.copy(isEnabled = enabled) else it },
            togglingRecurringIds = current.togglingRecurringIds + recurring.id,
            repositoryError = null
        )
        scope.launch {
            when (val result = setEnabled(userId, recurring.id, enabled)) {
                is RecurringUseCaseResult.Success -> {
                    if (sessionController.state.value.userId != userId) return@launch
                    val outcome = if (enabled) {
                        coordinator.apply(recurring.copy(userId = userId, isEnabled = true), forceSchedule = true)
                    } else {
                        coordinator.cancel(userId, recurring.id)
                    }
                    mutableState.value = mutableState.value.copy(
                        togglingRecurringIds = mutableState.value.togglingRecurringIds - recurring.id
                    )
                    handleSchedulingOutcome(outcome, successMessage = null)
                }
                is RecurringUseCaseResult.ValidationFailure -> rollbackToggle(recurring, result.error)
                is RecurringUseCaseResult.RepositoryFailure -> {
                    optimisticEnabled.remove(recurring.id)
                    mutableState.value = mutableState.value.copy(
                        recurring = mutableState.value.recurring.map { if (it.id == recurring.id) recurring else it },
                        togglingRecurringIds = mutableState.value.togglingRecurringIds - recurring.id,
                        repositoryError = result.error
                    )
                    emit(RecurringUiEvent.Message(RecurringMessage.WRITE_ERROR, result.error.message))
                }
            }
        }
    }

    fun onBack() {
        val current = mutableState.value
        when {
            current.isSaving || current.deletingRecurringId != null -> Unit
            current.screenMode == RecurringScreenMode.FORM -> closeForm()
            current.selectedDetail != null -> onDismissDetail()
            else -> emit(RecurringUiEvent.NavigateBack)
        }
    }

    fun onPermissionRequestResult(permission: ReminderPermission, granted: Boolean) {
        val envelope = mutableState.value.pendingEvent
        if ((envelope?.event as? RecurringUiEvent.RequestPermission)?.permission == permission) {
            mutableState.value = mutableState.value.copy(pendingEvent = null)
        }
        if (!granted) emit(RecurringUiEvent.Message(RecurringMessage.PERMISSION_DENIED))
        else {
            sessionController.forceReconcile()
            emitNextPermissionIfIdle()
        }
    }

    fun consumeEvent(id: Long) {
        if (mutableState.value.pendingEvent?.id == id) {
            mutableState.value = mutableState.value.copy(pendingEvent = null)
            emitNextPermissionIfIdle()
        }
    }

    fun close() {
        sessionJob.cancel()
        categoryJob.cancel()
    }

    private fun handleSchedulingOutcome(
        outcome: RecurringProcessingOutcome,
        successMessage: RecurringMessage?
    ) {
        if (outcome.status == RecurringProcessingStatus.FAILED ||
            outcome.status == RecurringProcessingStatus.REJECTED_IDENTITY
        ) {
            mutableState.value = mutableState.value.copy(
                schedulingError = outcome.schedulingError ?: outcome.repositoryError?.message
            )
            emit(
                RecurringUiEvent.Message(
                    RecurringMessage.SCHEDULING_ERROR,
                    outcome.schedulingError ?: outcome.repositoryError?.message
                )
            )
            return
        }
        successMessage?.let { emit(RecurringUiEvent.Message(it)) }
        requestPermissionIfNeeded(outcome)
    }

    private fun requestPermissionIfNeeded(outcome: RecurringProcessingOutcome) {
        val needed = buildList {
            if (outcome.exactAlarm == false &&
                permissionGateway.permissionState(ReminderPermission.EXACT_ALARM) == ReminderPermissionState.DENIED
            ) add(ReminderPermission.EXACT_ALARM)
            if (permissionGateway.permissionState(ReminderPermission.NOTIFICATIONS) == ReminderPermissionState.DENIED) {
                add(ReminderPermission.NOTIFICATIONS)
            }
        }
        needed.filterNot { it in requestedPermissions }.forEach { permission ->
            requestedPermissions += permission
            queuedPermissions += permission
        }
        emitNextPermissionIfIdle()
    }

    private fun emitNextPermissionIfIdle() {
        if (mutableState.value.pendingEvent != null || queuedPermissions.isEmpty()) return
        emit(RecurringUiEvent.RequestPermission(queuedPermissions.removeAt(0)))
    }

    private fun rollbackToggle(recurring: RecurringTransaction, error: RecurringValidationError) {
        optimisticEnabled.remove(recurring.id)
        mutableState.value = mutableState.value.copy(
            recurring = mutableState.value.recurring.map { if (it.id == recurring.id) recurring else it },
            togglingRecurringIds = mutableState.value.togglingRecurringIds - recurring.id,
            validationError = error
        )
    }

    private fun prepareDatePicker() {
        val date = RecurringScheduleCalculator.parseStartDate(mutableState.value.startDate) ?: initialDate
        updateForm {
            copy(
                activePicker = RecurringPicker.DATE,
                datePickerMonth = RecurringYearMonth(date.year, date.month),
                datePickerSelectedDay = date.dayOfMonth
            )
        }
    }

    private fun prepareTimePicker() {
        val time = RecurringScheduleCalculator.parseTime(mutableState.value.time) ?: ReminderLocalTime(20, 15)
        updateForm {
            copy(
                activePicker = RecurringPicker.TIME,
                timePickerHour = time.hour,
                timePickerMinute = time.minute
            )
        }
    }

    private fun shiftDatePickerMonth(delta: Int) {
        val current = mutableState.value
        val first = ReminderLocalDate(current.datePickerMonth.year, current.datePickerMonth.month, 1)
        val shifted = ReminderCalendar.plusMonthsAnchored(first, delta.toLong()) ?: return
        updateForm {
            copy(
                datePickerMonth = RecurringYearMonth(shifted.year, shifted.month),
                datePickerSelectedDay = datePickerSelectedDay.coerceAtMost(
                    ReminderCalendar.daysInMonth(shifted.year, shifted.month)
                )
            )
        }
    }

    private fun closeForm() {
        mutableState.value = mutableState.value.copy(
            screenMode = RecurringScreenMode.LIST,
            editingRecurringId = null,
            isSaving = false,
            validationError = null,
            activePicker = RecurringPicker.NONE
        )
    }

    private fun select(block: RecurringUiState.() -> RecurringUiState) = updateForm {
        block().copy(activePicker = RecurringPicker.NONE, validationError = null)
    }

    private fun updateForm(block: RecurringUiState.() -> RecurringUiState) {
        if (!mutableState.value.isSaving) update(block)
    }

    private fun update(block: RecurringUiState.() -> RecurringUiState) {
        mutableState.value = mutableState.value.block()
    }

    private fun emitWriteError(message: String) {
        emit(RecurringUiEvent.Message(RecurringMessage.WRITE_ERROR, message))
    }

    private fun emit(event: RecurringUiEvent) {
        mutableState.value = mutableState.value.copy(
            pendingEvent = RecurringEventEnvelope(nextEventId++, event)
        )
    }

    private fun amountInput(amount: Double): String =
        if (amount.isFinite() && amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
}
