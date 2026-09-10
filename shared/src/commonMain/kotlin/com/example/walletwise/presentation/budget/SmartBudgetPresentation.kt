package com.example.walletwise.presentation.budget

import com.example.walletwise.domain.model.BUDGET_RULE_50_30_20
import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.model.BudgetRule
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.repository.BudgetPlanRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.service.BudgetAllocation
import com.example.walletwise.domain.service.BudgetCalculator
import com.example.walletwise.domain.service.BudgetCalendar
import com.example.walletwise.domain.service.BudgetDate
import com.example.walletwise.domain.service.BudgetDateProvider
import com.example.walletwise.domain.service.BudgetGroupProgress
import com.example.walletwise.domain.service.BudgetProgressStatus
import com.example.walletwise.domain.usecase.BudgetUseCaseResult
import com.example.walletwise.domain.usecase.SaveBudgetPlanUseCase
import com.example.walletwise.domain.validation.BudgetInputException
import com.example.walletwise.domain.validation.BudgetValidationError
import com.example.walletwise.domain.validation.BudgetValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class BudgetInsightKind { DEFAULT, WANTS_WARNING, REFRESHED }

data class BudgetInsight(
    val kind: BudgetInsightKind = BudgetInsightKind.DEFAULT,
    val wantsPercent: Int = 0,
    val remainingDays: Int = 1,
    val suggestedDailySaving: Double = 0.0
)

enum class SmartBudgetMessage { SAVED, INSIGHT_REFRESHED, ERROR }

sealed interface SmartBudgetUiEvent {
    data object NavigateBack : SmartBudgetUiEvent
    data class Message(val kind: SmartBudgetMessage, val detail: String? = null) : SmartBudgetUiEvent
}

data class SmartBudgetEventEnvelope(
    val id: Long,
    val event: SmartBudgetUiEvent
)

private val EmptyGroupProgress = BudgetGroupProgress(
    limit = 0.0,
    spent = 0.0,
    remaining = 0.0,
    fraction = 0f,
    percentUsed = 0,
    status = BudgetProgressStatus.SAFE
)

data class SmartBudgetUiState(
    val userId: String? = null,
    val isLoading: Boolean = false,
    val plan: BudgetPlan? = null,
    val repositoryError: RepositoryError? = null,
    val currentDate: BudgetDate,
    val monthKey: String = BudgetCalendar.monthKey(currentDate),
    val remainingDays: Int = BudgetCalendar.remainingDays(currentDate),
    val totalBudget: Double = 0.0,
    val totalSpent: Double = 0.0,
    val totalRemaining: Double = 0.0,
    val dailySafeLimit: Double = 0.0,
    val breakfastLimit: Double = 0.0,
    val lunchLimit: Double = 0.0,
    val dinnerLimit: Double = 0.0,
    val needs: BudgetGroupProgress = EmptyGroupProgress,
    val wants: BudgetGroupProgress = EmptyGroupProgress,
    val savings: BudgetGroupProgress = EmptyGroupProgress,
    val showSetupDialog: Boolean = false,
    val inputAmount: String = "",
    val selectedRule: BudgetRule = BudgetRule.FIFTY_THIRTY_TWENTY,
    val previewAllocation: BudgetAllocation = BudgetAllocation(0.0, 0.0, 0.0),
    val isSaving: Boolean = false,
    val validationError: BudgetValidationError? = null,
    val insight: BudgetInsight = BudgetInsight(),
    val pendingEvent: SmartBudgetEventEnvelope? = null
) {
    val hasPlan: Boolean get() = plan != null
}

class SmartBudgetPresenter(
    private val scope: CoroutineScope,
    private val budgetSession: StateFlow<BudgetSessionState>,
    private val transactions: StateFlow<List<Transaction>>,
    repository: BudgetPlanRepository,
    private val dateProvider: BudgetDateProvider
) {
    private val saveBudgetPlan = SaveBudgetPlanUseCase(repository)
    private val initialDate = dateProvider.currentLocalDate()
    private val mutableState = MutableStateFlow(SmartBudgetUiState(currentDate = initialDate))
    val state: StateFlow<SmartBudgetUiState> = mutableState.asStateFlow()
    private val sourceJob: Job
    private var insightWasRefreshed = false
    private var nextEventId = 1L

    init {
        sourceJob = scope.launch {
            combine(budgetSession, transactions) { session, transactionList -> session to transactionList }
                .collect { (session, transactionList) -> updateDerivedState(session, transactionList) }
        }
    }

    fun onOpenSetup() {
        val current = mutableState.value
        val input = current.plan?.totalBudget
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.toLong()
            ?.toString()
            ?: "10000000"
        val rule = BudgetRule.fromWireValueOrDefault(current.plan?.ruleType ?: BUDGET_RULE_50_30_20)
        mutableState.value = current.copy(
            showSetupDialog = true,
            inputAmount = input,
            selectedRule = rule,
            previewAllocation = preview(input, rule),
            validationError = null,
            repositoryError = null
        )
    }

    fun onCancelSetup() {
        if (mutableState.value.isSaving) return
        mutableState.value = mutableState.value.copy(
            showSetupDialog = false,
            validationError = null
        )
    }

    fun onAmountChanged(value: String) {
        if (value.isNotEmpty() && !value.all { it.isDigit() }) return
        val current = mutableState.value
        mutableState.value = current.copy(
            inputAmount = value,
            previewAllocation = preview(value, current.selectedRule),
            validationError = null
        )
    }

    fun onRuleSelected(rule: BudgetRule) {
        val current = mutableState.value
        mutableState.value = current.copy(
            selectedRule = rule,
            previewAllocation = preview(current.inputAmount, rule),
            validationError = null
        )
    }

    fun onSave() {
        val current = mutableState.value
        if (current.isSaving || !current.showSetupDialog) return
        val userId = budgetSession.value.userId ?: return emitAuthenticationError()
        val monthKey = budgetSession.value.monthKey.takeIf { it.isNotBlank() }
            ?: BudgetCalendar.monthKey(dateProvider.currentLocalDate())
        mutableState.value = current.copy(isSaving = true, validationError = null, repositoryError = null)
        scope.launch {
            val result = saveBudgetPlan(
                userId = userId,
                monthKey = monthKey,
                amountText = current.inputAmount,
                ruleWireValue = current.selectedRule.wireValue
            )
            val activeSession = budgetSession.value
            if (activeSession.userId != userId || activeSession.monthKey != monthKey) return@launch
            when (result) {
                is BudgetUseCaseResult.Success -> {
                    mutableState.value = mutableState.value.copy(
                        isSaving = false,
                        showSetupDialog = false,
                        validationError = null
                    )
                    emitMessage(SmartBudgetMessage.SAVED)
                }
                is BudgetUseCaseResult.ValidationFailure -> mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    validationError = result.error
                )
                is BudgetUseCaseResult.RepositoryFailure -> {
                    mutableState.value = mutableState.value.copy(
                        isSaving = false,
                        repositoryError = result.error
                    )
                    emitMessage(SmartBudgetMessage.ERROR, result.error.message)
                }
            }
        }
    }

    fun onRefreshInsight() {
        insightWasRefreshed = true
        mutableState.value = mutableState.value.copy(
            insight = refreshedInsight(mutableState.value.remainingDays)
        )
        emitMessage(SmartBudgetMessage.INSIGHT_REFRESHED)
    }

    fun onBack() {
        emit(SmartBudgetUiEvent.NavigateBack)
    }

    fun consumeEvent(id: Long) {
        val current = mutableState.value
        if (current.pendingEvent?.id == id) {
            mutableState.value = current.copy(pendingEvent = null)
        }
    }

    fun close() {
        sourceJob.cancel()
    }

    private fun updateDerivedState(
        session: BudgetSessionState,
        transactionList: List<Transaction>
    ) {
        val current = mutableState.value
        val identityChanged = current.userId != session.userId ||
            (current.monthKey.isNotBlank() && session.monthKey.isNotBlank() && current.monthKey != session.monthKey)
        if (identityChanged) insightWasRefreshed = false
        val date = dateProvider.currentLocalDate()
        val plan = session.plan
        val metrics = plan?.let { BudgetCalculator.metrics(it, transactionList, date, dateProvider) }
        val insight = if (insightWasRefreshed) {
            refreshedInsight(metrics?.remainingDays ?: BudgetCalendar.remainingDays(date))
        } else {
            defaultInsight(metrics)
        }
        mutableState.value = current.copy(
            userId = session.userId,
            isLoading = session.isLoading,
            plan = metrics?.plan,
            repositoryError = session.error,
            currentDate = date,
            monthKey = session.monthKey.ifBlank { BudgetCalendar.monthKey(date) },
            remainingDays = metrics?.remainingDays ?: BudgetCalendar.remainingDays(date),
            totalBudget = metrics?.plan?.totalBudget ?: 0.0,
            totalSpent = metrics?.totalSpent ?: 0.0,
            totalRemaining = metrics?.totalRemaining ?: 0.0,
            dailySafeLimit = metrics?.dailySafeLimit ?: 0.0,
            breakfastLimit = metrics?.breakfastLimit ?: 0.0,
            lunchLimit = metrics?.lunchLimit ?: 0.0,
            dinnerLimit = metrics?.dinnerLimit ?: 0.0,
            needs = metrics?.needs ?: EmptyGroupProgress,
            wants = metrics?.wants ?: EmptyGroupProgress,
            savings = metrics?.savings ?: EmptyGroupProgress,
            showSetupDialog = if (identityChanged) false else current.showSetupDialog,
            isSaving = if (identityChanged) false else current.isSaving,
            validationError = if (identityChanged) null else current.validationError,
            insight = insight
        )
    }

    private fun defaultInsight(metrics: com.example.walletwise.domain.service.BudgetMetrics?): BudgetInsight {
        val wantsPercent = metrics?.wants?.percentUsed ?: 0
        return BudgetInsight(
            kind = if (wantsPercent > 80) BudgetInsightKind.WANTS_WARNING else BudgetInsightKind.DEFAULT,
            wantsPercent = wantsPercent,
            remainingDays = metrics?.remainingDays ?: BudgetCalendar.remainingDays(dateProvider.currentLocalDate()),
            suggestedDailySaving = (metrics?.dailySafeLimit ?: 0.0) * 0.20
        )
    }

    private fun refreshedInsight(remainingDays: Int) = BudgetInsight(
        kind = BudgetInsightKind.REFRESHED,
        remainingDays = remainingDays
    )

    private fun preview(value: String, rule: BudgetRule): BudgetAllocation {
        val amount = value.toDoubleOrNull() ?: 0.0
        return BudgetCalculator.allocate(amount, rule)
    }

    private fun emitAuthenticationError() {
        val error = RepositoryError(
            RepositoryErrorCode.NOT_AUTHENTICATED,
            "Bạn cần đăng nhập để lưu kế hoạch ngân sách"
        )
        mutableState.value = mutableState.value.copy(repositoryError = error)
        emitMessage(SmartBudgetMessage.ERROR, error.message)
    }

    private fun emitMessage(kind: SmartBudgetMessage, detail: String? = null) {
        emit(SmartBudgetUiEvent.Message(kind, detail))
    }

    private fun emit(event: SmartBudgetUiEvent) {
        mutableState.value = mutableState.value.copy(
            pendingEvent = SmartBudgetEventEnvelope(nextEventId++, event)
        )
    }
}
