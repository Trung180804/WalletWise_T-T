package com.example.walletwise.presentation.budget

import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.repository.*
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class FinancialMappingPresenterTest {
    @Test fun everyActiveCategoryDefaultPersistsByIdAndSurvivesRenameAndReload() = runTest {
        var stored = emptyMap<String, String>(); var saves = 0
        val mappings = object : FinancialMappingRepository {
            override suspend fun load(userId: String, rule: BudgetRule) = Result.success(stored)
            override suspend fun save(userId: String, rule: BudgetRule, mapping: Map<String, String>): Result<Boolean> { saves++; stored = mapping; return Result.success(true) }
        }
        val session = MutableStateFlow(BudgetSessionState("u", "09-2026", plan = BudgetPlan(totalBudget = 10000000.0)))
        val categories = MutableStateFlow(listOf(Category("food", "Ăn uống"), Category("custom", "Danh mục riêng")))
        val tx = Transaction("t", "u", "Chi", "Tiền mặt", 50000.0, "Ăn uống", "", 1L, categoryId = "food")
        val txs = MutableStateFlow(listOf(tx))
        val first = SmartBudgetPresenter(this, session, txs, repository, dates, mappingRepository = mappings, categories = categories)
        runCurrent(); assertEquals(mapOf("id:food" to "needs", "id:custom" to "wants"), stored); assertEquals(1, saves)
        categories.value = categories.value.map { if (it.id == "food") it.copy(name = "Bữa ăn mới") else it }; runCurrent()
        assertEquals(50000L, first.state.value.liveAllocation.buckets.first().spent); first.close()
        val reloaded = SmartBudgetPresenter(this, session, txs, repository, dates, mappingRepository = mappings, categories = categories)
        runCurrent(); assertEquals(4950000L, reloaded.state.value.liveAllocation.buckets.first().remaining)
        assertEquals(1, saves); reloaded.close()
    }
    @Test fun amountDateTypeCategoryMappingMonthAndLogoutRecalculateFromOneSource() = runTest {
        val clock = object : BudgetDateProvider {
            override fun currentLocalDate() = BudgetDate(2026, 9, 17)
            override fun localDateAt(epochMilliseconds: Long) = if (epochMilliseconds == 1L) currentLocalDate() else BudgetDate(2026, 8, 31)
        }
        val session = MutableStateFlow(BudgetSessionState("u", "09-2026", plan = BudgetPlan(totalBudget = 10000000.0)))
        val tx = Transaction("recurring:stable", "u", "Chi", "Tiền mặt", 50000.0, "Ăn uống", "", 1L, categoryId = "food")
        val transactions = MutableStateFlow(listOf(tx))
        val categories = MutableStateFlow(listOf(Category("food", "Ăn uống"), Category("home", "Nhà cửa")))
        val presenter = SmartBudgetPresenter(this, session, transactions, repository, clock, categories = categories)
        fun remaining() = presenter.state.value.liveAllocation.buckets.first().remaining
        runCurrent(); assertEquals(4950000L, remaining())
        transactions.value = listOf(tx.copy(amount = 100000.0)); runCurrent(); assertEquals(4900000L, remaining())
        transactions.value = listOf(tx.copy(type = "Thu")); runCurrent(); assertEquals(5000000L, remaining())
        transactions.value = listOf(tx.copy(timestamp = 2L)); runCurrent(); assertEquals(5000000L, remaining())
        transactions.value = listOf(tx.copy(category = "Nhà cửa", categoryId = "home")); runCurrent(); assertEquals(4950000L, remaining())
        presenter.onCategoryMapped("Nhà cửa", "wants"); runCurrent(); assertEquals(5000000L, remaining())
        assertEquals(50000L, presenter.state.value.liveAllocation.buckets[1].spent)
        transactions.value = listOf(tx, tx); runCurrent(); assertEquals(4950000L, remaining())
        transactions.value = emptyList(); runCurrent(); assertEquals(5000000L, remaining())
        transactions.value = listOf(tx); session.value = session.value.copy(monthKey = "08-2026"); runCurrent(); assertEquals(5000000L, remaining())
        session.value = BudgetSessionState(); runCurrent(); assertEquals(0L, presenter.state.value.liveAllocation.totalSpent)
        assertTrue(presenter.state.value.mappingCategories.isEmpty()); assertTrue(presenter.state.value.mappingCategoryIds.isEmpty())
        assertTrue(presenter.state.value.categoryMappings.isEmpty()); presenter.close()
    }
    private val dates = object : BudgetDateProvider {
        override fun currentLocalDate() = BudgetDate(2026, 9, 16)
        override fun localDateAt(epochMilliseconds: Long) = currentLocalDate()
    }
    private val repository = object : BudgetPlanRepository {
        override fun observeBudgetPlan(userId: String, monthYear: String): Flow<RepositoryResult<BudgetPlan?>> = emptyFlow()
        override suspend fun getBudgetPlan(userId: String, monthYear: String) = RepositoryResult.Success<BudgetPlan?>(null)
        override suspend fun saveBudgetPlan(userId: String, plan: BudgetPlan) = RepositoryResult.Success(Unit)
    }
    @Test fun onboardingNeverUsesBalanceAndMappingIsLoadedOnceThenRecalculates() = runTest {
        var loads = 0; var saves = 0
        val mappingRepository = object : FinancialMappingRepository {
            override suspend fun load(userId: String, rule: BudgetRule): Result<Map<String,String>> { loads++; return Result.success(emptyMap()) }
            override suspend fun save(userId: String, rule: BudgetRule, mapping: Map<String,String>): Result<Boolean> { saves++; return Result.success(true) }
        }
        val session = MutableStateFlow(BudgetSessionState("u", "09-2026"))
        val transactions = MutableStateFlow(listOf(Transaction("meal", "u", "Chi", "Tiền mặt",50000.0,"Ăn uống","",1L)))
        val presenter = SmartBudgetPresenter(this, session, transactions, repository, dates, mappingRepository = mappingRepository)
        runCurrent(); presenter.onOpenSetup(); assertEquals("", presenter.state.value.inputAmount)
        presenter.onAmountChanged("10.000.000"); assertEquals("10000000", presenter.state.value.inputAmount)
        presenter.onCancelSetup()
        session.value = session.value.copy(plan = BudgetPlan(totalBudget=10000000.0, ruleType="JARS")); runCurrent()
        assertEquals(5450000L, presenter.state.value.liveAllocation.buckets.first().remaining)
        val loadCount = loads
        transactions.value = transactions.value.map { it.copy(amount=60000.0) }; runCurrent()
        assertEquals(loadCount, loads)
        presenter.onCategoryMapped("Ăn uống", "play"); runCurrent()
        assertEquals(1, saves); assertEquals(0L, presenter.state.value.liveAllocation.buckets.first().spent)
        assertEquals(60000L, presenter.state.value.liveAllocation.buckets.last().spent)
        session.value = BudgetSessionState("b", "09-2026"); runCurrent()
        assertEquals("", presenter.state.value.inputAmount); assertEquals(0L, presenter.state.value.liveAllocation.totalSpent)
        assertEquals(emptyMap(), presenter.state.value.categoryMappings)
        presenter.close()
    }
    @Test fun delayedMappingCannotLeakAcrossUidAndLoadFailureStillSubtractsRealFoodSpend() = runTest {
        val gate = CompletableDeferred<Unit>()
        val mappings = object : FinancialMappingRepository {
            override suspend fun load(userId: String, rule: BudgetRule): Result<Map<String,String>> = if (userId == "a") withContext(NonCancellable) {
                gate.await(); Result.success(mapOf("Ăn uống" to "play"))
            } else Result.failure(IllegalStateException())
            override suspend fun save(userId: String, rule: BudgetRule, mapping: Map<String,String>) = Result.success(true)
        }
        val session = MutableStateFlow(BudgetSessionState("a","09-2026", plan=BudgetPlan(totalBudget=10000000.0, ruleType="JARS")))
        val txs = MutableStateFlow(listOf(Transaction("t","b","Chi","Tiền mặt",50000.0,"Ăn uống","",1L)))
        val presenter = SmartBudgetPresenter(this, session, txs, repository, dates, mappingRepository=mappings)
        runCurrent(); session.value = session.value.copy(userId="b"); runCurrent(); gate.complete(Unit); runCurrent()
        assertEquals(emptyMap(), presenter.state.value.categoryMappings)
        assertNotNull(presenter.state.value.mappingError)
        assertEquals(50000L, presenter.state.value.liveAllocation.buckets.first().spent)
        assertEquals(5450000L, presenter.state.value.liveAllocation.buckets.first().remaining)
        assertEquals(presenter.state.value.liveAllocation.totalSpent, presenter.state.value.liveAllocation.buckets.sumOf { it.spent })
        presenter.close()
    }
}
