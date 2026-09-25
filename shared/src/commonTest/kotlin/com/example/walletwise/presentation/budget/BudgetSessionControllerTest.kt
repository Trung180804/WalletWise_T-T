package com.example.walletwise.presentation.budget

import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.repository.BudgetPlanRepository
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetSessionControllerTest {
    @Test
    fun sameUidAndMonthUseOneCollector() = runTest {
        val repository = CountingBudgetRepository()
        val controller = BudgetSessionController(this, repository)

        controller.setSession("uid-1", "09-2026")
        controller.setSession("uid-1", "09-2026")
        runCurrent()

        assertEquals(1, repository.observeCalls)
        assertEquals(1, repository.activeCollectors)
        controller.close()
        runCurrent()
        assertEquals(0, repository.activeCollectors)
    }

    @Test
    fun uidMonthChangeAndLogoutCancelOldCollectorAndClearPlan() = runTest {
        val repository = CountingBudgetRepository()
        val controller = BudgetSessionController(this, repository)
        controller.setSession("uid-1", "09-2026")
        runCurrent()

        controller.setSession("uid-1", "10-2026")
        runCurrent()
        assertEquals(2, repository.observeCalls)
        assertEquals(1, repository.activeCollectors)
        assertEquals("10-2026", controller.state.value.monthKey)

        controller.setSession("uid-2", "10-2026")
        runCurrent()
        assertEquals(3, repository.observeCalls)
        assertEquals("uid-2", controller.state.value.userId)

        controller.setSession(null, "")
        runCurrent()
        assertEquals(0, repository.activeCollectors)
        assertNull(controller.state.value.plan)
        assertNull(controller.state.value.userId)
        controller.close()
    }
}

private class CountingBudgetRepository : BudgetPlanRepository {
    private val source = MutableStateFlow<RepositoryResult<BudgetPlan?>>(
        RepositoryResult.Success(BudgetPlan(id = "uid-1", monthYear = "09-2026"))
    )
    var observeCalls = 0
    var activeCollectors = 0

    override fun observeBudgetPlan(userId: String, monthYear: String): Flow<RepositoryResult<BudgetPlan?>> {
        observeCalls++
        return flow {
            activeCollectors++
            try {
                source.collect { emit(it) }
            } finally {
                activeCollectors--
            }
        }
    }

    override suspend fun getBudgetPlan(userId: String, monthYear: String) = RepositoryResult.Success(null)
    override suspend fun saveBudgetPlan(userId: String, plan: BudgetPlan) = RepositoryResult.Success(Unit)
}
