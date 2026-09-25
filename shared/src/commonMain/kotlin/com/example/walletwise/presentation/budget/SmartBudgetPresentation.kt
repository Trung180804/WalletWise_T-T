package com.example.walletwise.presentation.budget

import com.example.walletwise.domain.model.BUDGET_RULE_50_30_20
import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.model.BudgetRule
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.model.FinancialAllocationPlan
import com.example.walletwise.domain.model.FinancialMethods
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.math.round
import com.example.walletwise.domain.service.MoneyInput
import com.example.walletwise.domain.service.LiveFinancialCalculator
import com.example.walletwise.domain.service.LiveFinancialAllocation
import com.example.walletwise.domain.service.FinancialCategoryMapping
import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.repository.FinancialMappingRepository

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
    val allocationPlan: FinancialAllocationPlan = BudgetCalculator.allocateExactly(0L, FinancialMethods.FiftyThirtyTwenty),
    val isSaving: Boolean = false,
    val validationError: BudgetValidationError? = null,
    val insight: BudgetInsight = BudgetInsight(),
    val pendingEvent: SmartBudgetEventEnvelope? = null,
    val liveAllocation: LiveFinancialAllocation = LiveFinancialAllocation(emptyList()),
    val categoryMappings: Map<String, String> = emptyMap(),
    val mappingCategories: List<String> = emptyList(),
    val mappingCategoryIds: Map<String, String> = emptyMap(),
    val showMappingDialog: Boolean = false,
    val mappingLoading: Boolean = false,
    val mappingSaving: Boolean = false,
    val mappingError: String? = null
) {
    val hasPlan: Boolean get() = plan != null
}

class SmartBudgetPresenter(
    private val scope: CoroutineScope,
    private val budgetSession: StateFlow<BudgetSessionState>,
    private val transactions: StateFlow<List<Transaction>>,
    repository: BudgetPlanRepository,
    private val dateProvider: BudgetDateProvider,
    private val onMonthSelected: (String) -> Unit = {},
    private val mappingRepository: FinancialMappingRepository? = null,
    private val categories: StateFlow<List<Category>>? = null,
    private val categorySession: StateFlow<com.example.walletwise.presentation.category.CategorySessionState>? = null
) {
    private val saveBudgetPlan = SaveBudgetPlanUseCase(repository)
    private val initialDate = dateProvider.currentLocalDate()
    private val mutableState = MutableStateFlow(SmartBudgetUiState(currentDate = initialDate))
    val state: StateFlow<SmartBudgetUiState> = mutableState.asStateFlow()
    private val sourceJob: Job
    private var insightWasRefreshed = false
    private var nextEventId = 1L
    private var sessionGeneration = 0L
    private var saveJob: Job? = null
    private data class MappingState(val key: Pair<String, BudgetRule>? = null, val values: Map<String, String> = emptyMap(),
        val loading: Boolean = false, val saving: Boolean = false, val error: String? = null)
    private val mappingState = MutableStateFlow(MappingState())
    private var mappingJob: Job? = null
    private var mappingVersion = 0L

    init {
        sourceJob = scope.launch {
            combine(budgetSession, transactions, mappingState, categories ?: MutableStateFlow(emptyList<Category>()),
                categorySession ?: MutableStateFlow(com.example.walletwise.presentation.category.CategorySessionState())) { session, transactionList, _, _, _ -> session to transactionList }
                .collect { (session, transactionList) -> updateDerivedState(session, transactionList) }
        }
    }

    fun onOpenSetup() {
        val current = mutableState.value
        val input = current.plan?.totalBudget
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { round(it).toLong() }
            ?.toString()
            ?: current.inputAmount
        val rule = BudgetRule.fromWireValueOrDefault(current.plan?.ruleType ?: BUDGET_RULE_50_30_20)
        mutableState.value = current.copy(
            showSetupDialog = true,
            inputAmount = input,
            selectedRule = rule,
            previewAllocation = preview(input, rule),
            allocationPlan = exactPreview(input, rule),
            validationError = null,
            repositoryError = null
        )
    }

    fun onCancelSetup() {
        if (mutableState.value.isSaving) return
        mutableState.value = mutableState.value.copy(
            showSetupDialog = false,
            validationError = null,
            allocationPlan = savedAllocation(mutableState.value.plan)
        )
    }

    fun onAmountChanged(value: String) {
        if (mutableState.value.isSaving) return
        val raw = MoneyInput.raw(value) ?: run { mutableState.value = mutableState.value.copy(validationError = BudgetValidationError.AMOUNT_INVALID); return }
        val current = mutableState.value
        mutableState.value = current.copy(
            inputAmount = raw,
            previewAllocation = preview(raw, current.selectedRule),
            allocationPlan = exactPreview(raw, current.selectedRule),
            validationError = null
        )
    }

    fun onRuleSelected(rule: BudgetRule) {
        if (mutableState.value.isSaving) return
        val current = mutableState.value
        mutableState.value = current.copy(
            selectedRule = rule,
            previewAllocation = preview(current.inputAmount, rule),
            allocationPlan = exactPreview(current.inputAmount, rule),
            validationError = null
        )
    }

    fun onSave() {
        val current = mutableState.value
        if (current.isSaving || !current.showSetupDialog || current.validationError == BudgetValidationError.AMOUNT_INVALID) return
        if (current.inputAmount.isNotBlank() &&
            (current.inputAmount.toLongOrNull() == null ||
                (current.inputAmount.toLongOrNull() ?: 0L) > 9_007_199_254_740_991L)) {
            mutableState.value = current.copy(validationError = BudgetValidationError.AMOUNT_INVALID)
            return
        }
        val userId = budgetSession.value.userId ?: return emitAuthenticationError()
        val monthKey = budgetSession.value.monthKey.takeIf { it.isNotBlank() }
            ?: BudgetCalendar.monthKey(dateProvider.currentLocalDate())
        mutableState.value = current.copy(isSaving = true, validationError = null, repositoryError = null)
        val generation = sessionGeneration
        saveJob = scope.launch {
            val result = withTimeoutOrNull(15_000L) { saveBudgetPlan(
                userId = userId,
                monthKey = monthKey,
                amountText = current.inputAmount,
                ruleWireValue = current.selectedRule.wireValue
            ) } ?: BudgetUseCaseResult.RepositoryFailure(RepositoryError(RepositoryErrorCode.NETWORK,"Chưa nhận được xác nhận lưu kế hoạch. Hãy thử lại."))
            coroutineContext.ensureActive()
            val activeSession = budgetSession.value
            if (generation != sessionGeneration || activeSession.userId != userId || activeSession.monthKey != monthKey) return@launch
            when (result) {
                is BudgetUseCaseResult.Success -> {
                    mutableState.value = mutableState.value.copy(
                        isSaving = false,
                        showSetupDialog = false,
                        allocationPlan = savedAllocation(budgetSession.value.plan),
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

    fun onPreviousMonth() = selectMonth(BudgetCalendar.previousMonth(selectedMonth()))

    fun onNextMonth() = selectMonth(BudgetCalendar.nextMonth(selectedMonth()))

    fun onResetRatios() {
        val current = mutableState.value
        if (current.showSetupDialog) onRuleSelected(current.selectedRule)
        else mutableState.value = current.copy(allocationPlan = savedAllocation(current.plan))
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
        sessionGeneration++
        mappingVersion++
        mappingJob?.cancel()
        saveJob?.cancel()
        sourceJob.cancel()
        mutableState.value = SmartBudgetUiState(currentDate = initialDate)
    }

    private fun updateDerivedState(
        session: BudgetSessionState,
        transactionList: List<Transaction>
    ) {
        val previous = mutableState.value
        val userChanged = previous.userId != session.userId
        val current = if (userChanged) SmartBudgetUiState(currentDate = initialDate) else previous
        val identityChanged = current.userId != session.userId ||
            (current.monthKey.isNotBlank() && session.monthKey.isNotBlank() && current.monthKey != session.monthKey)
        if (userChanged || identityChanged) {
            sessionGeneration++
            saveJob?.cancel()
            insightWasRefreshed = false
        }
        val date = BudgetCalendar.dateFromMonthKey(session.monthKey) ?: dateProvider.currentLocalDate()
        val plan = session.plan
        val activeRule = BudgetRule.fromWireValueOrDefault(plan?.ruleType ?: BUDGET_RULE_50_30_20)
        val mappingKey = session.userId?.let { it to activeRule }
        if (mappingState.value.key != mappingKey) loadMapping(mappingKey)
        val mapping = mappingState.value.takeIf { it.key == mappingKey } ?: MappingState()
        val allocation = savedAllocation(plan)
        val availableCategories = if (session.userId.isNullOrBlank()) emptyList() else if (categorySession == null) categories?.value.orEmpty() else
            categorySession.value.takeIf { it.userId == session.userId }?.categories.orEmpty()
        val defaultIds = if (plan != null && !mapping.loading && mapping.error == null &&
            (categorySession == null || !categorySession.value.isLoading)) availableCategories.filter {
            it.type.trim().equals("Chi", true) && it.id.isNotBlank() && mapping.values[FinancialCategoryMapping.idKey(it.id)] !in allocation.method.buckets.map { bucket -> bucket.key }
        }.associate { FinancialCategoryMapping.idKey(it.id) to FinancialCategoryMapping.bucket(it.name, activeRule, mapping.values, it.id) } else emptyMap()
        val effectiveMappings = mapping.values + defaultIds
        if (defaultIds.isNotEmpty() && !mapping.saving && mappingKey != null) saveMappings(mappingKey, effectiveMappings)
        val categoryNames = (availableCategories.filter { it.type == "Chi" }.map { it.name } +
            transactionList.filter { it.userId == session.userId && it.type.trim().equals("Chi", true) }.map {
                FinancialCategoryMapping.resolveCategory(it, availableCategories)?.name ?: it.category
            }).distinct().sorted()
        val categoryIds = categoryNames.mapNotNull { name -> availableCategories.filter { it.type == "Chi" && it.name == name }
            .singleOrNull()?.let { name to it.id } }.toMap()
        val live = LiveFinancialCalculator.calculate(allocation, transactionList, session.userId, session.monthKey, dateProvider, effectiveMappings,
            availableCategories)
        val metrics = plan?.let { LiveFinancialCalculator.metrics(it, live, date) }
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
            totalSpent = if (plan != null) live.totalSpent.toDouble() else 0.0,
            totalRemaining = if (plan != null) allocation.income.toDouble() - live.totalSpent else 0.0,
            dailySafeLimit = metrics?.dailySafeLimit ?: 0.0,
            breakfastLimit = metrics?.breakfastLimit ?: 0.0,
            lunchLimit = metrics?.lunchLimit ?: 0.0,
            dinnerLimit = metrics?.dinnerLimit ?: 0.0,
            needs = metrics?.needs ?: EmptyGroupProgress,
            wants = metrics?.wants ?: EmptyGroupProgress,
            savings = metrics?.savings ?: EmptyGroupProgress,
            selectedRule = if (current.showSetupDialog) current.selectedRule else
                plan?.let { BudgetRule.fromWireValueOrDefault(it.ruleType) } ?: BudgetRule.FIFTY_THIRTY_TWENTY,
            allocationPlan = if (current.showSetupDialog) exactPreview(current.inputAmount, current.selectedRule)
                else savedAllocation(plan),
            showSetupDialog = current.showSetupDialog,
            isSaving = if (identityChanged) false else current.isSaving,
            validationError = if (identityChanged) null else current.validationError,
            insight = insight,
            liveAllocation = live,
            categoryMappings = effectiveMappings,
            mappingCategories = categoryNames,
            mappingCategoryIds = categoryIds,
            mappingLoading = mapping.loading,
            mappingSaving = mappingState.value.saving,
            mappingError = mapping.error,
            showMappingDialog = !userChanged && current.showMappingDialog
        )
    }

    fun onOpenMapping() { mutableState.value = mutableState.value.copy(showMappingDialog = true) }
    fun onCloseMapping() { mutableState.value = mutableState.value.copy(showMappingDialog = false) }
    fun onRetryMapping() = loadMapping(mappingState.value.key)

    fun onCategoryMapped(category: String, bucket: String) {
        val current = mutableState.value
        val key = mappingState.value.key ?: return
        if (current.mappingLoading || current.mappingSaving || current.mappingError != null || category !in current.mappingCategories) return
        if (FinancialMethods.forRule(key.second).buckets.none { it.key == bucket }) return
        val mappingCategory = current.mappingCategoryIds[category]?.let(FinancialCategoryMapping::idKey) ?: category
        val proposed = mappingState.value.values + (mappingCategory to bucket)
        saveMappings(key, proposed)
    }

    private fun saveMappings(key: Pair<String, BudgetRule>, proposed: Map<String, String>) {
        val version = ++mappingVersion
        val previousValues = mappingState.value.values
        mappingState.value = mappingState.value.copy(values = proposed, saving = true)
        mappingJob = scope.launch {
            val result = if (mappingRepository == null) Result.success(true) else withTimeoutOrNull(15_000L) {
                mappingRepository.save(key.first, key.second, proposed)
            } ?: Result.failure(IllegalStateException("Acknowledgement timeout"))
            coroutineContext.ensureActive()
            if (version != mappingVersion || mappingState.value.key != key) return@launch
            mappingState.value = if (result.getOrNull() == true) MappingState(key, proposed) else
                mappingState.value.copy(values = previousValues, saving = false, error = "Chưa lưu được phân nhóm. Hãy thử lại.")
        }
    }

    private fun loadMapping(key: Pair<String, BudgetRule>?) {
        val version = ++mappingVersion
        mappingJob?.cancel()
        mappingState.value = MappingState(key = key, loading = key != null && mappingRepository != null)
        if (key == null || mappingRepository == null) return
        mappingJob = scope.launch {
            val result = withTimeoutOrNull(15_000L) { mappingRepository.load(key.first, key.second) }
                ?: Result.failure(IllegalStateException("Mapping timeout"))
            coroutineContext.ensureActive()
            if (version != mappingVersion || mappingState.value.key != key) return@launch
            mappingState.value = MappingState(key, result.getOrDefault(emptyMap()), error = if (result.isFailure) "Không tải được cấu hình danh mục. Đang dùng phân bổ mặc định; hãy thử tải lại." else null)
        }
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

    private fun exactPreview(value: String, rule: BudgetRule): FinancialAllocationPlan =
        BudgetCalculator.allocateExactly(value.toLongOrNull() ?: 0L, rule)

    private fun savedAllocation(plan: BudgetPlan?): FinancialAllocationPlan =
        BudgetCalculator.allocateExactly(
            plan?.totalBudget?.takeIf { it.isFinite() }?.let { round(it).toLong() } ?: 0L,
            BudgetRule.fromWireValueOrDefault(plan?.ruleType ?: BUDGET_RULE_50_30_20)
        )

    private fun selectedMonth(): BudgetDate =
        BudgetCalendar.dateFromMonthKey(mutableState.value.monthKey) ?: dateProvider.currentLocalDate()

    private fun selectMonth(date: BudgetDate) {
        onMonthSelected(BudgetCalendar.monthKey(date))
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
