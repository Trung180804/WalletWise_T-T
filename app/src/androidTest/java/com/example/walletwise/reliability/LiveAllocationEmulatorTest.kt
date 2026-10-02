package com.example.walletwise.reliability

import com.example.walletwise.BuildConfig
import com.example.walletwise.data.repository.BudgetPlanRepositoryImpl
import com.example.walletwise.data.repository.TransactionRepositoryImpl
import com.example.walletwise.data.repository.FinancialMappingRepositoryImpl
import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.*
import com.example.walletwise.presentation.budget.*
import com.example.walletwise.presentation.transaction.TransactionSessionController
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test

class LiveAllocationEmulatorTest {
    @Test fun saveReadAndLiveAddEditDeleteRecalculateFromOneSession(): Unit = runBlocking {
        assertTrue(BuildConfig.USE_FIREBASE_EMULATOR)
        assertEquals("demo-walletwise", FirebaseApp.getInstance().options.projectId)
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val uid = requireNotNull(auth.signInAnonymously().await().user).uid
        val now = System.currentTimeMillis()
        val localDate = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val dates = object : BudgetDateProvider {
            override fun currentLocalDate() = BudgetDate(localDate.year, localDate.monthValue, localDate.dayOfMonth)
            override fun localDateAt(epochMilliseconds: Long) = java.time.Instant.ofEpochMilli(epochMilliseconds).atZone(java.time.ZoneId.systemDefault()).toLocalDate().let { BudgetDate(it.year,it.monthValue,it.dayOfMonth) }
        }
        val month = BudgetCalendar.monthKey(dates.currentLocalDate())
        val budgetRepository = BudgetPlanRepositoryImpl()
        val plan = BudgetPlan(monthYear=month,totalBudget=10000000.0,ruleType="JARS",needsLimit=5500000.0,wantsLimit=1000000.0,savingsLimit=3500000.0)
        assertTrue(budgetRepository.saveBudgetPlan(uid,plan) is RepositoryResult.Success)
        val reread = (budgetRepository.getBudgetPlan(uid,month) as RepositoryResult.Success).value
        assertNotNull(reread)
        val transactions = TransactionRepositoryImpl()
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main)
        val session = TransactionSessionController(scope,transactions)
        val budgetState = MutableStateFlow(BudgetSessionState(uid,month,plan=reread))
        val presenter = SmartBudgetPresenter(scope,budgetState,session.transactions,budgetRepository,dates,mappingRepository=FinancialMappingRepositoryImpl())
        val id = java.util.UUID.randomUUID().toString()
        val transaction = Transaction(id,uid,"Chi","Tiền mặt",50000.0,"Ăn uống","Checkpoint synthetic",now)
        try {
            withContext(Dispatchers.Main) { session.setUserId(uid) }
            assertTrue(transactions.addTransaction(transaction).getOrThrow())
            assertTrue(transactions.addTransaction(transaction).getOrThrow())
            val first = withTimeout(20000) { presenter.state.first { it.liveAllocation.buckets.firstOrNull()?.spent==50000L } }.liveAllocation.buckets.first()
            assertEquals(5450000L,first.remaining); assertEquals("0,91%",formatFinancialPercent(first.usedPercent))
            assertEquals("54,5%",formatFinancialPercent(first.remainingIncomePercent(10000000L)))
            assertTrue(transactions.updateTransaction(transaction.copy(amount=100000.0)).getOrThrow())
            withTimeout(20000) { presenter.state.first { it.liveAllocation.buckets.firstOrNull()?.remaining==5400000L } }
            assertTrue(transactions.deleteTransaction(id).getOrThrow())
            withTimeout(20000) { presenter.state.first { it.liveAllocation.buckets.firstOrNull()?.spent==0L } }
            assertEquals(5500000L,presenter.state.value.liveAllocation.buckets.first().remaining)
            // Leave one synthetic expense for the manual Home/plan runtime check.
            assertTrue(transactions.addTransaction(transaction).getOrThrow())
            withTimeout(20000) { presenter.state.first { it.liveAllocation.buckets.firstOrNull()?.spent==50000L } }
            withContext(Dispatchers.Main) { budgetState.value=BudgetSessionState(); session.setUserId(null) }
            withTimeout(10000) { presenter.state.first { it.userId==null } }
            assertEquals(0L,presenter.state.value.liveAllocation.totalSpent)
        } finally { withContext(Dispatchers.Main) { presenter.close(); session.close() }; scope.cancel() }
    }
}
