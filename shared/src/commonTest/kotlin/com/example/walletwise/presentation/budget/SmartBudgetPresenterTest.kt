package com.example.walletwise.presentation.budget

import com.example.walletwise.domain.model.BUDGET_RULE_50_30_20
import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.model.BudgetRule
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.repository.BudgetPlanRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.BudgetDate
import com.example.walletwise.domain.service.BudgetDateProvider
import com.example.walletwise.domain.validation.BudgetValidationError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SmartBudgetPresenterTest {
    @Test
    fun fractionalLegacyIncomeUsesTheSameRoundingForTotalAllocationAndEditor() = runTest {
        val session = MutableStateFlow(BudgetSessionState("uid", "09-2026", plan = plan().copy(totalBudget = 100.9)))
        val presenter = presenter(this, session, MutableStateFlow(emptyList()), PresenterBudgetRepository())
        runCurrent()
        assertEquals(101L, presenter.state.value.allocationPlan.income)
        assertEquals(101L, presenter.state.value.allocationPlan.totalAmount)
        presenter.onOpenSetup()
        assertEquals("101", presenter.state.value.inputAmount)
        assertEquals(101L, presenter.state.value.allocationPlan.totalAmount)
        presenter.close()
    }

    @Test
    fun liveTransactionsAndMonthChangesPreserveDraftButLogoutClearsAllUserData() = runTest {
        val session = MutableStateFlow(BudgetSessionState("uid", "09-2026", plan = plan()))
        val transactions = MutableStateFlow(emptyList<Transaction>())
        val presenter = presenter(this, session, transactions, PresenterBudgetRepository())
        runCurrent()
        presenter.onOpenSetup()
        presenter.onAmountChanged("12000001")
        presenter.onRuleSelected(BudgetRule.JARS)
        transactions.value = listOf(Transaction(type = "Chi", amount = 81.0, timestamp = 1L))
        runCurrent()
        assertEquals(BudgetRule.JARS, presenter.state.value.selectedRule)
        assertEquals(12_000_001L, presenter.state.value.allocationPlan.totalAmount)
        session.value = BudgetSessionState("uid", "10-2026", isLoading = true)
        runCurrent()
        assertTrue(presenter.state.value.showSetupDialog)
        assertEquals("12000001", presenter.state.value.inputAmount)
        assertEquals("10-2026", presenter.state.value.monthKey)
        session.value = BudgetSessionState()
        runCurrent()
        assertEquals("", presenter.state.value.inputAmount)
        assertEquals(0L, presenter.state.value.allocationPlan.totalAmount)
        assertNull(presenter.state.value.pendingEvent)
        session.value = BudgetSessionState("other", "10-2026")
        runCurrent()
        assertEquals(0L, presenter.state.value.allocationPlan.totalAmount)
        presenter.close()
    }

    @Test
    fun cancelAndResetRestoreSavedAllocationAndOversizedInputNeverWrites() = runTest {
        val session = MutableStateFlow(BudgetSessionState("uid", "09-2026", plan = plan()))
        val repository = PresenterBudgetRepository()
        val presenter = presenter(this, session, MutableStateFlow(emptyList()), repository)
        runCurrent()
        presenter.onResetRatios()
        assertEquals(10_000_000L, presenter.state.value.allocationPlan.totalAmount)
        presenter.onOpenSetup()
        presenter.onAmountChanged("9007199254740992")
        presenter.onSave()
        runCurrent()
        assertEquals(BudgetValidationError.AMOUNT_INVALID, presenter.state.value.validationError)
        assertEquals(0, repository.saveCalls)
        presenter.onCancelSetup()
        assertEquals(10_000_000L, presenter.state.value.allocationPlan.totalAmount)
        presenter.close()
    }

    @Test
    fun selectedMethodSixBucketsResetAndMonthNavigationAreDeterministic() = runTest {
        val selectedMonths = mutableListOf<String>()
        val session = MutableStateFlow(BudgetSessionState("uid", "09-2026"))
        val presenter = SmartBudgetPresenter(
            this,
            session,
            MutableStateFlow(emptyList()),
            PresenterBudgetRepository(),
            PresenterDateProvider,
            selectedMonths::add
        )
        runCurrent()
        presenter.onOpenSetup()
        presenter.onAmountChanged("10000000")
        presenter.onRuleSelected(BudgetRule.JARS)
        assertEquals(6, presenter.state.value.allocationPlan.allocations.size)
        assertEquals(10_000_000L, presenter.state.value.allocationPlan.totalAmount)
        presenter.onResetRatios()
        assertEquals(100, presenter.state.value.allocationPlan.totalPercent)
        presenter.onPreviousMonth()
        presenter.onNextMonth()
        assertEquals(listOf("08-2026", "10-2026"), selectedMonths)
        presenter.close()
    }

    @Test
    fun mapsLoadingEmptyDataAndRepositoryError() = runTest {
        val session = MutableStateFlow(BudgetSessionState("uid", "09-2026", isLoading = true))
        val transactions = MutableStateFlow(emptyList<Transaction>())
        val presenter = presenter(this, session, transactions, PresenterBudgetRepository())
        runCurrent()
        assertTrue(presenter.state.value.isLoading)
        assertFalse(presenter.state.value.hasPlan)

        val plan = plan()
        session.value = BudgetSessionState("uid", "09-2026", plan = plan)
        runCurrent()
        assertTrue(presenter.state.value.hasPlan)
        assertSame(plan, session.value.plan)

        val error = RepositoryError(RepositoryErrorCode.NETWORK, "offline")
        session.value = BudgetSessionState("uid", "09-2026", plan = plan, error = error)
        runCurrent()
        assertEquals(plan.totalBudget, presenter.state.value.totalBudget)
        assertSame(error, presenter.state.value.repositoryError)
        presenter.close()
    }

    @Test
    fun setupOpenCancelAndRulePreviewPreserveCurrentBehavior() = runTest {
        val session = MutableStateFlow(BudgetSessionState("uid", "09-2026", plan = plan()))
        val presenter = presenter(this, session, MutableStateFlow(emptyList()), PresenterBudgetRepository())
        runCurrent()

        presenter.onOpenSetup()
        assertTrue(presenter.state.value.showSetupDialog)
        assertEquals("10000000", presenter.state.value.inputAmount)
        presenter.onAmountChanged("20000000")
        presenter.onRuleSelected(BudgetRule.JARS)
        assertEquals(11_000_000.0, presenter.state.value.previewAllocation.needs)
        assertEquals(2_000_000.0, presenter.state.value.previewAllocation.wants)
        assertEquals(7_000_000.0, presenter.state.value.previewAllocation.savings)

        presenter.onAmountChanged("not-a-number")
        assertEquals("20000000", presenter.state.value.inputAmount)
        presenter.onCancelSetup()
        assertFalse(presenter.state.value.showSetupDialog)
        presenter.close()
    }

    @Test
    fun invalidInputKeepsDialogAndDoesNotWrite() = runTest {
        val session = MutableStateFlow(BudgetSessionState("uid", "09-2026"))
        val repository = PresenterBudgetRepository()
        val presenter = presenter(this, session, MutableStateFlow(emptyList()), repository)
        runCurrent()

        presenter.onOpenSetup()
        presenter.onAmountChanged("")
        presenter.onSave()
        runCurrent()

        assertTrue(presenter.state.value.showSetupDialog)
        assertEquals(BudgetValidationError.AMOUNT_REQUIRED, presenter.state.value.validationError)
        assertEquals(0, repository.saveCalls)
        presenter.close()
    }

    @Test
    fun saveSuccessAwaitsWriteClosesDialogAndEventIsConsumable() = runTest {
        val session = MutableStateFlow(BudgetSessionState("uid", "09-2026"))
        val repository = PresenterBudgetRepository()
        val presenter = presenter(this, session, MutableStateFlow(emptyList()), repository)
        runCurrent()
        presenter.onOpenSetup()
        presenter.onAmountChanged("10000000")

        repository.gate = CompletableDeferred()
        presenter.onSave()
        presenter.onSave()
        runCurrent()
        assertEquals(1, repository.saveCalls)
        assertTrue(presenter.state.value.isSaving)
        assertTrue(presenter.state.value.showSetupDialog)
        presenter.onRuleSelected(BudgetRule.JARS)
        presenter.onAmountChanged("20000000")
        assertEquals(BudgetRule.FIFTY_THIRTY_TWENTY, presenter.state.value.selectedRule)
        assertEquals("10000000", presenter.state.value.inputAmount)

        repository.gate?.complete(Unit)
        runCurrent()
        assertFalse(presenter.state.value.isSaving)
        assertFalse(presenter.state.value.showSetupDialog)
        consumeMessage(presenter, SmartBudgetMessage.SAVED)
        presenter.close()
    }

    @Test
    fun saveFailureKeepsOldPlanAndFormAndEmitsErrorOnce() = runTest {
        val oldPlan = plan()
        val session = MutableStateFlow(BudgetSessionState("uid", "09-2026", plan = oldPlan))
        val repository = PresenterBudgetRepository().apply { failSave = true }
        val presenter = presenter(this, session, MutableStateFlow(emptyList()), repository)
        runCurrent()
        presenter.onOpenSetup()
        presenter.onAmountChanged("12000000")
        presenter.onSave()
        runCurrent()

        assertTrue(presenter.state.value.showSetupDialog)
        assertEquals("12000000", presenter.state.value.inputAmount)
        assertEquals(oldPlan.totalBudget, presenter.state.value.totalBudget)
        assertEquals("save failed", presenter.state.value.repositoryError?.message)
        consumeMessage(presenter, SmartBudgetMessage.ERROR)
        presenter.close()
    }

    @Test
    fun transactionChangesRecalculateDerivedSpentAndInsightThreshold() = runTest {
        val budget = plan().copy(wantsLimit = 100.0)
        val session = MutableStateFlow(BudgetSessionState("uid", "09-2026", plan = budget))
        val transactions = MutableStateFlow(emptyList<Transaction>())
        val presenter = presenter(this, session, transactions, PresenterBudgetRepository())
        runCurrent()
        assertEquals(0.0, presenter.state.value.totalSpent)
        assertEquals(BudgetInsightKind.DEFAULT, presenter.state.value.insight.kind)

        transactions.value = listOf(Transaction(userId = "uid", type = "Chi", category = "Giải trí", amount = 81.0, timestamp = 1L))
        runCurrent()
        assertEquals(81.0, presenter.state.value.wants.spent)
        assertEquals(BudgetInsightKind.WANTS_WARNING, presenter.state.value.insight.kind)
        assertEquals(81, presenter.state.value.insight.wantsPercent)
        presenter.close()
    }

    @Test
    fun refreshInsightAndBackUseOneTimeEvents() = runTest {
        val session = MutableStateFlow(BudgetSessionState("uid", "09-2026", plan = plan()))
        val presenter = presenter(this, session, MutableStateFlow(emptyList()), PresenterBudgetRepository())
        runCurrent()

        presenter.onRefreshInsight()
        assertEquals(BudgetInsightKind.REFRESHED, presenter.state.value.insight.kind)
        consumeMessage(presenter, SmartBudgetMessage.INSIGHT_REFRESHED)

        presenter.onBack()
        val event = presenter.state.value.pendingEvent!!
        assertIs<SmartBudgetUiEvent.NavigateBack>(event.event)
        presenter.consumeEvent(event.id)
        assertNull(presenter.state.value.pendingEvent)
        presenter.close()
    }

    @Test
    fun uidChangeAndLogoutClearOldPlanAndDialog() = runTest {
        val session = MutableStateFlow(BudgetSessionState("uid-1", "09-2026", plan = plan()))
        val presenter = presenter(this, session, MutableStateFlow(emptyList()), PresenterBudgetRepository())
        runCurrent()
        presenter.onOpenSetup()
        assertTrue(presenter.state.value.showSetupDialog)

        session.value = BudgetSessionState("uid-2", "09-2026", isLoading = true)
        runCurrent()
        assertFalse(presenter.state.value.hasPlan)
        assertFalse(presenter.state.value.showSetupDialog)
        assertEquals("uid-2", presenter.state.value.userId)
        assertEquals("", presenter.state.value.inputAmount)
        assertEquals(0L, presenter.state.value.allocationPlan.totalAmount)

        session.value = BudgetSessionState()
        runCurrent()
        assertNull(presenter.state.value.userId)
        assertFalse(presenter.state.value.hasPlan)
        presenter.close()
    }

    @Test
    fun saveCompletionFromOldUidDoesNotAffectNewSession() = runTest {
        val session = MutableStateFlow(BudgetSessionState("uid-1", "09-2026"))
        val repository = PresenterBudgetRepository().apply { gate = CompletableDeferred() }
        val presenter = presenter(this, session, MutableStateFlow(emptyList()), repository)
        runCurrent()
        presenter.onOpenSetup()
        presenter.onAmountChanged("10000000")
        presenter.onSave()
        runCurrent()
        assertTrue(presenter.state.value.isSaving)

        session.value = BudgetSessionState("uid-2", "09-2026", isLoading = true)
        runCurrent()
        repository.gate?.complete(Unit)
        runCurrent()

        assertEquals("uid-2", presenter.state.value.userId)
        assertFalse(presenter.state.value.isSaving)
        assertFalse(presenter.state.value.showSetupDialog)
        assertNull(presenter.state.value.pendingEvent)
        presenter.close()
    }

    private fun presenter(
        scope: CoroutineScope,
        session: MutableStateFlow<BudgetSessionState>,
        transactions: MutableStateFlow<List<Transaction>>,
        repository: PresenterBudgetRepository
    ) = SmartBudgetPresenter(scope, session, transactions, repository, PresenterDateProvider)

    private fun plan() = BudgetPlan(
        id = "uid",
        monthYear = "09-2026",
        totalBudget = 10_000_000.0,
        ruleType = BUDGET_RULE_50_30_20,
        needsLimit = 5_000_000.0,
        wantsLimit = 3_000_000.0,
        savingsLimit = 2_000_000.0
    )

    private fun consumeMessage(presenter: SmartBudgetPresenter, expected: SmartBudgetMessage) {
        val envelope = presenter.state.value.pendingEvent!!
        assertEquals(expected, assertIs<SmartBudgetUiEvent.Message>(envelope.event).kind)
        presenter.consumeEvent(envelope.id)
        assertNull(presenter.state.value.pendingEvent)
    }
}

private object PresenterDateProvider : BudgetDateProvider {
    override fun currentLocalDate() = BudgetDate(2026, 9, 15)
    override fun localDateAt(epochMilliseconds: Long): BudgetDate? =
        if (epochMilliseconds == 1L) BudgetDate(2026, 9, 1) else null
}

private class PresenterBudgetRepository : BudgetPlanRepository {
    var saveCalls = 0
    var saved: BudgetPlan? = null
    var failSave = false
    var gate: CompletableDeferred<Unit>? = null

    override fun observeBudgetPlan(userId: String, monthYear: String): Flow<RepositoryResult<BudgetPlan?>> = emptyFlow()
    override suspend fun getBudgetPlan(userId: String, monthYear: String): RepositoryResult<BudgetPlan?> = RepositoryResult.Success(null)
    override suspend fun saveBudgetPlan(userId: String, plan: BudgetPlan): RepositoryResult<Unit> {
        saveCalls++
        saved = plan
        gate?.await()
        return if (failSave) {
            RepositoryResult.Failure(RepositoryError(RepositoryErrorCode.UNKNOWN, "save failed"))
        } else {
            RepositoryResult.Success(Unit)
        }
    }
}
