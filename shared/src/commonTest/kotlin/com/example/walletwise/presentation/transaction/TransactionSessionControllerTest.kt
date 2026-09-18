package com.example.walletwise.presentation.transaction

import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.repository.TransactionRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionSessionControllerTest {
    @Test
    fun financialPresenterReusesOneTransactionCollectorDuringMappingAndLiveUpdates() = runTest {
        val repository = SessionTransactionRepository()
        val controller = TransactionSessionController(this, repository)
        val budgetRepository = object : com.example.walletwise.domain.repository.BudgetPlanRepository {
            override fun observeBudgetPlan(userId: String, monthYear: String): Flow<RepositoryResult<com.example.walletwise.domain.model.BudgetPlan?>> = kotlinx.coroutines.flow.emptyFlow()
            override suspend fun getBudgetPlan(userId: String, monthYear: String) = RepositoryResult.Success<com.example.walletwise.domain.model.BudgetPlan?>(null)
            override suspend fun saveBudgetPlan(userId: String, plan: com.example.walletwise.domain.model.BudgetPlan) = RepositoryResult.Success(Unit)
        }
        val date = com.example.walletwise.domain.service.BudgetDate(2026, 9, 16)
        val presenter = com.example.walletwise.presentation.budget.SmartBudgetPresenter(this,
            kotlinx.coroutines.flow.MutableStateFlow(com.example.walletwise.presentation.budget.BudgetSessionState("user-a", "09-2026",
                plan = com.example.walletwise.domain.model.BudgetPlan(totalBudget = 10000000.0, ruleType = "JARS"))), controller.transactions, budgetRepository,
            object : com.example.walletwise.domain.service.BudgetDateProvider {
                override fun currentLocalDate() = date
                override fun localDateAt(epochMilliseconds: Long) = date
            })
        controller.setUserId("user-a"); advanceUntilIdle()
        repository.emit("user-a", success(tx("one", "user-a", 10L).copy(type="Chi", category="Ăn uống", amount=50000.0)))
        advanceUntilIdle(); presenter.onCategoryMapped("Ăn uống", "play"); advanceUntilIdle()
        assertEquals(1, repository.observeCount["user-a"])
        assertEquals(50000L, presenter.state.value.liveAllocation.buckets.last().spent)
        presenter.close(); controller.close()
    }
    @Test
    fun sameUidKeepsOneCollector_andLogoutClearsUserData() = runTest {
        val repository = SessionTransactionRepository()
        val controller = TransactionSessionController(this, repository)

        controller.setUserId("user-a")
        controller.setUserId("user-a")
        advanceUntilIdle()
        repository.emit("user-a", success(tx("a", "user-a", 10L)))
        advanceUntilIdle()

        assertEquals(1, repository.observeCount["user-a"])
        assertEquals(listOf("a"), controller.transactions.value.map(Transaction::id))

        controller.setUserId(null)
        assertEquals(TransactionSessionState(), controller.state.value)
        assertTrue(controller.transactions.value.isEmpty())
        controller.close()
    }

    @Test
    fun uidChangeCancelsOldCollector_andNeverLeaksPreviousUserData() = runTest {
        val repository = SessionTransactionRepository()
        val controller = TransactionSessionController(this, repository)

        controller.setUserId("user-a")
        advanceUntilIdle()
        repository.emit("user-a", success(tx("a", "user-a", 10L)))
        advanceUntilIdle()
        controller.setUserId("user-b")

        assertEquals("user-b", controller.state.value.userId)
        assertTrue(controller.transactions.value.isEmpty())
        assertTrue(controller.state.value.isLoading)

        advanceUntilIdle()
        repository.emit("user-a", success(tx("stale", "user-a", 99L)))
        repository.emit("user-b", success(tx("b", "user-b", 20L)))
        advanceUntilIdle()

        assertEquals(listOf("b"), controller.transactions.value.map(Transaction::id))
        assertEquals(1, repository.observeCount["user-a"])
        assertEquals(1, repository.observeCount["user-b"])
        controller.close()
    }

    @Test
    fun loadingDataEmptyError_andAddEditDeleteSnapshotsArePublished() = runTest {
        val repository = SessionTransactionRepository()
        val controller = TransactionSessionController(this, repository)

        controller.setUserId("user-a")
        assertTrue(controller.state.value.isLoading)
        advanceUntilIdle()

        repository.emit("user-a", success(tx("old", "user-a", 10L), tx("new", "user-a", 30L)))
        advanceUntilIdle()
        assertEquals(listOf("new", "old"), controller.transactions.value.map(Transaction::id))
        assertFalse(controller.state.value.isLoading)

        repository.emit("user-a", success(tx("old", "user-a", 40L), tx("new", "user-a", 30L)))
        advanceUntilIdle()
        assertEquals(40L, controller.transactions.value.first { it.id == "old" }.timestamp)

        repository.emit("user-a", success(tx("old", "user-a", 40L)))
        advanceUntilIdle()
        assertEquals(listOf("old"), controller.transactions.value.map(Transaction::id))

        repository.emit("user-a", success())
        advanceUntilIdle()
        assertTrue(controller.transactions.value.isEmpty())

        repository.emit(
            "user-a",
            RepositoryResult.Failure(RepositoryError(RepositoryErrorCode.NETWORK, "offline"))
        )
        advanceUntilIdle()
        assertEquals("offline", controller.state.value.error?.message)
        assertFalse(controller.state.value.isLoading)
        controller.close()
    }

    @Test
    fun recurringCreatedTransactionAppearsExactlyOnceWhenSnapshotRepeats() = runTest {
        val repository = SessionTransactionRepository()
        val controller = TransactionSessionController(this, repository)
        val recurringTransaction = tx("rule_2026-09-05", "user-a", 50L)

        controller.setUserId("user-a")
        advanceUntilIdle()
        repository.emit("user-a", success(recurringTransaction))
        repository.emit("user-a", success(recurringTransaction))
        advanceUntilIdle()

        assertEquals(listOf("rule_2026-09-05"), controller.transactions.value.map(Transaction::id))
        controller.close()
    }

    private fun tx(id: String, userId: String, timestamp: Long) = Transaction(
        id = id,
        userId = userId,
        type = "Chi",
        amount = 1.0,
        category = "Test",
        timestamp = timestamp
    )

    private fun success(vararg transactions: Transaction) =
        RepositoryResult.Success(transactions.toList())
}

private class SessionTransactionRepository : TransactionRepository {
    private val streams = mutableMapOf<String, MutableSharedFlow<RepositoryResult<List<Transaction>>>>()
    val observeCount = mutableMapOf<String, Int>()

    override fun observeTransactions(userId: String): Flow<RepositoryResult<List<Transaction>>> {
        observeCount[userId] = observeCount.getOrElse(userId) { 0 } + 1
        return streams.getOrPut(userId) { MutableSharedFlow(extraBufferCapacity = 8) }
    }

    suspend fun emit(userId: String, result: RepositoryResult<List<Transaction>>) {
        streams.getOrPut(userId) { MutableSharedFlow(extraBufferCapacity = 8) }.emit(result)
    }

    override suspend fun addTransaction(transaction: Transaction): Result<Boolean> = Result.success(true)
    override suspend fun getTransactions(): Result<List<Transaction>> = Result.success(emptyList())
    override suspend fun deleteTransaction(transactionId: String): Result<Boolean> = Result.success(true)
    override suspend fun updateTransaction(transaction: Transaction): Result<Boolean> = Result.success(true)
}
