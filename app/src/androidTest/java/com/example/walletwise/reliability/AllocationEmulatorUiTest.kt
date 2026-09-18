package com.example.walletwise.reliability

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.walletwise.BuildConfig
import com.example.walletwise.data.repository.*
import com.example.walletwise.data.time.AndroidBudgetDateProvider
import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.repository.TransactionRepository
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.*
import com.example.walletwise.presentation.budget.*
import com.example.walletwise.presentation.transaction.TransactionSessionController
import com.example.walletwise.presentation.category.CategorySessionController
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Firebase ACK -> exactly one live transaction source -> presenter -> rendered card. */
class AllocationEmulatorUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun bothMethodsRenderLiveAddEditDeleteIdMappingAndLogoutFromOneListener(): Unit = runBlocking {
        assertTrue(BuildConfig.USE_FIREBASE_EMULATOR)
        assertEquals("demo-walletwise", FirebaseApp.getInstance().options.projectId)
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val uid = requireNotNull(auth.signInAnonymously().await().user).uid
        val dates = AndroidBudgetDateProvider()
        val month = BudgetCalendar.monthKey(dates.currentLocalDate())
        val base = TransactionRepositoryImpl()
        var listeners = 0
        val repository = object : TransactionRepository by base {
            override fun observeTransactions(userId: String): Flow<RepositoryResult<List<Transaction>>> { listeners++; return base.observeTransactions(userId) }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val session = TransactionSessionController(scope, repository)
        val plans = BudgetPlanRepositoryImpl()
        val budget = MutableStateFlow(BudgetSessionState(uid, month))
        val categoryRepository = CategoryRepositoryImpl()
        val defaults = listOf(Category("food-stable", "Ăn uống"), Category("home-stable", "Nhà cửa"))
        assertTrue(categoryRepository.ensureDefaultCategories(uid, defaults) is RepositoryResult.Success)
        val categorySession = CategorySessionController(scope, categoryRepository)
        val categories = categorySession.state.map { it.categories }.stateIn(scope, SharingStarted.Eagerly, emptyList())
        withContext(Dispatchers.Main) { categorySession.setUserId(uid) }
        withTimeout(20000) { categories.first { it.any { category -> category.id == "food-stable" } } }
        val savedMappings = FinancialMappingRepositoryImpl()
        val mappingSource = object : com.example.walletwise.domain.repository.FinancialMappingRepository by savedMappings {
            override suspend fun load(userId: String, rule: BudgetRule): Result<Map<String, String>> =
                if (rule == BudgetRule.FIFTY_THIRTY_TWENTY) Result.failure(IllegalStateException("Isolated mapping failure")) else savedMappings.load(userId, rule)
        }
        val presenter = SmartBudgetPresenter(scope, budget, session.transactions, plans, dates, mappingRepository = mappingSource, categories = categories)
        val transaction = Transaction("checkpoint-deterministic-meal", uid, "Chi", "Tiền mặt", 50000.0, "Ăn uống", "QUAN AN TEST", System.currentTimeMillis(), categoryId = "food-stable")
        try {
            withContext(Dispatchers.Main) { session.setUserId(uid) }
            compose.setContent { MaterialTheme {
                val state by presenter.state.collectAsState()
                SmartBudgetContent(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            } }
            for (rule in listOf(BudgetRule.FIFTY_THIRTY_TWENTY, BudgetRule.JARS)) {
                val allocated = if (rule == BudgetRule.JARS) 5500000L else 5000000L
                val plan = BudgetPlan(uid, month, 10000000.0, rule.wireValue, allocated.toDouble(), if (rule == BudgetRule.JARS) 1000000.0 else 3000000.0, if (rule == BudgetRule.JARS) 3500000.0 else 2000000.0)
                assertTrue(plans.saveBudgetPlan(uid, plan) is RepositoryResult.Success)
                val read = (plans.getBudgetPlan(uid, month) as RepositoryResult.Success).value!!
                withContext(Dispatchers.Main) { budget.value = BudgetSessionState(uid, month, plan = read) }
                withTimeout(20000) { presenter.state.first { it.plan?.ruleType == rule.wireValue && !it.mappingLoading } }
                assertTrue(base.addTransaction(transaction).getOrThrow())
                assertTrue(base.addTransaction(transaction).getOrThrow())
                withTimeout(20000) { presenter.state.first { it.liveAllocation.buckets.firstOrNull()?.spent == 50000L } }
                assertEquals(1, session.transactions.value.size)
                assertEquals(allocated - 50000L, presenter.state.value.liveAllocation.buckets.first().remaining)
                val usage = presenter.state.value.liveAllocation.buckets.first()
                assertEquals(allocated, usage.allocation.amount)
                assertEquals(if (rule == BudgetRule.JARS) "0,91%" else "1%", formatFinancialPercent(usage.usedPercent))
                assertEquals(if (rule == BudgetRule.JARS) "99,09%" else "99%", formatFinancialPercent(usage.remainingPercent))
                compose.onNodeWithText("Đã chi: 50.000 ₫").assertExists()
                compose.onNodeWithText("Đã dùng của quỹ: " + formatFinancialPercent(usage.usedPercent)).assertExists()
                compose.onNodeWithText("Còn lại của quỹ: " + formatFinancialPercent(usage.remainingPercent)).assertExists()
                if (rule == BudgetRule.FIFTY_THIRTY_TWENTY) assertNotNull(presenter.state.value.mappingError)
                compose.onNodeWithText("Chi tiêu chưa phân nhóm", substring = true).assertDoesNotExist()
                compose.onNodeWithText("Còn lại: ${formatBudgetMoney((allocated - 50000L).toDouble())}").assertExists()
                proof("${rule.wireValue}-add-50000")
                trace("add", presenter.state.value, session.transactions.value.size, listeners)
                assertTrue(base.updateTransaction(transaction.copy(amount = 100000.0)).getOrThrow())
                withTimeout(20000) { presenter.state.first { it.liveAllocation.buckets.firstOrNull()?.spent == 100000L } }
                compose.onNodeWithText("Còn lại: ${formatBudgetMoney((allocated - 100000L).toDouble())}").assertExists()
                proof("${rule.wireValue}-edit-100000")
                trace("edit", presenter.state.value, session.transactions.value.size, listeners)
                assertTrue(base.updateTransaction(transaction.copy(amount = 100000.0, category = "Giải trí", categoryId = "")).getOrThrow())
                withTimeout(20000) { presenter.state.first { it.liveAllocation.buckets.firstOrNull()?.spent == 0L && it.liveAllocation.buckets.firstOrNull { bucket -> bucket.allocation.bucket.key == if (rule == BudgetRule.JARS) "play" else "wants" }?.spent == 100000L } }
                assertEquals(100000L, presenter.state.value.liveAllocation.totalSpent)
                trace("category-edit", presenter.state.value, session.transactions.value.size, listeners)
                assertTrue(base.deleteTransaction(transaction.id).getOrThrow())
                withTimeout(20000) { presenter.state.first { it.liveAllocation.totalSpent == 0L && session.transactions.value.isEmpty() } }
                compose.onNodeWithText("Còn lại: ${formatBudgetMoney(allocated.toDouble())}").assertExists()
                trace("delete", presenter.state.value, session.transactions.value.size, listeners)
                proof("${rule.wireValue}-delete")
            }
            // Legacy category field containing the repository ID is resolved through the same category source.
            assertTrue(base.addTransaction(transaction.copy(category = "home-stable", categoryId = "")).getOrThrow())
            withTimeout(20000) { presenter.state.first { it.liveAllocation.buckets.firstOrNull()?.spent == 50000L } }
            withContext(Dispatchers.Main) { presenter.onCategoryMapped("Nhà cửa", "play") }
            withTimeout(20000) { presenter.state.first { !it.mappingSaving && it.categoryMappings["id:home-stable"] == "play" } }
            assertEquals(50000L, presenter.state.value.liveAllocation.buckets.last().spent)
            assertTrue(categoryRepository.updateCategory(uid, categories.value.first { it.id == "home-stable" }.copy(name = "Chỗ ở mới")) is RepositoryResult.Success)
            withTimeout(20000) { presenter.state.first { "Chỗ ở mới" in it.mappingCategories } }
            assertEquals(50000L, presenter.state.value.liveAllocation.buckets.last().spent)
            assertEquals(1, listeners)
            for ((amount, suffix) in listOf(2750000.0 to "50percent", 5500000.0 to "100percent", 6000000.0 to "over-budget")) {
                assertTrue(base.updateTransaction(transaction.copy(amount = amount)).getOrThrow())
                withTimeout(20000) { presenter.state.first { it.liveAllocation.buckets.firstOrNull()?.spent == amount.toLong() } }
                val usage = presenter.state.value.liveAllocation.buckets.first()
                compose.waitForIdle()
                compose.onNodeWithContentDescription("Nhu cầu thiết yếu: 55%")
                    .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
                        androidx.compose.ui.semantics.ProgressBarRangeInfo(usage.usedFraction, 0f..1f)))
                if (amount > 5500000.0) {
                    compose.onNodeWithText("Còn lại: -500.000 ₫").assertExists()
                    compose.onNodeWithText("Vượt ngân sách").assertExists()
                }
                proof("JARS-$suffix")
            }
            // Restore the synthetic meal once more to check a later snapshot.
            assertTrue(base.updateTransaction(transaction).getOrThrow())
            withTimeout(20000) { presenter.state.first { it.liveAllocation.buckets.firstOrNull()?.spent == 50000L } }
            withContext(Dispatchers.Main) { budget.value = BudgetSessionState(); session.setUserId(null) }
            withTimeout(10000) { presenter.state.first { it.userId == null } }
            assertEquals(0L, presenter.state.value.liveAllocation.totalSpent)
            assertTrue(presenter.state.value.categoryMappings.isEmpty())
        } finally { withContext(Dispatchers.Main) { presenter.close(); session.close(); categorySession.close() }; scope.cancel(); auth.signOut() }
    }
    private fun proof(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "checkpoint-proof").apply { mkdirs() }
        // PixelCopy can time out after a correct frame on a busy emulator. Capture the displayed window directly.
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try { File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { screenshot.recycle() }
    }
    private fun trace(phase: String, state: SmartBudgetUiState, transactions: Int, listeners: Int) {
        val bucket = state.liveAllocation.buckets.first()
        Log.i("ALLOCATION_CHECKPOINT", "$phase source=$transactions listeners=$listeners uidMatch=true month=${state.monthKey} type=Chi mapping=stable-ID spent=${bucket.spent} remaining=${bucket.remaining} used=${formatFinancialPercent(bucket.usedPercent)}")
    }
}
