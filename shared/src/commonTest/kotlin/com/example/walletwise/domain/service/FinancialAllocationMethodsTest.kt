package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.BudgetRule
import com.example.walletwise.domain.model.FinancialMethods
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinancialAllocationMethodsTest {
    @Test
    fun fiftyThirtyTwentyIsExactForTenMillion() {
        val plan = BudgetCalculator.allocateExactly(10_000_000L, BudgetRule.FIFTY_THIRTY_TWENTY)
        assertEquals(100, plan.totalPercent)
        assertEquals(listOf(5_000_000L, 3_000_000L, 2_000_000L), plan.allocations.map { it.amount })
        assertEquals(plan.income, plan.totalAmount)
    }

    @Test
    fun sixJarsIsExactForTenMillion() {
        val plan = BudgetCalculator.allocateExactly(10_000_000L, BudgetRule.JARS)
        assertEquals(100, plan.totalPercent)
        assertEquals(
            listOf(5_500_000L, 1_000_000L, 1_000_000L, 1_000_000L, 500_000L, 1_000_000L),
            plan.allocations.map { it.amount }
        )
        assertEquals(plan.income, plan.totalAmount)
    }

    @Test
    fun zeroNegativeAndRoundingRemainSafeAndExact() {
        BudgetRule.entries.forEach { rule ->
            assertTrue(BudgetCalculator.allocateExactly(0L, rule).allocations.all { it.amount == 0L })
            assertTrue(BudgetCalculator.allocateExactly(-10L, rule).allocations.all { it.amount == 0L })
            listOf(1L, 99L, 101L, 9_999_999L).forEach { income ->
                val plan = BudgetCalculator.allocateExactly(income, rule)
                assertEquals(income, plan.totalAmount)
                assertTrue(plan.allocations.all { it.amount >= 0L })
            }
        }
        assertEquals(100, FinancialMethods.FiftyThirtyTwenty.totalPercent)
        assertEquals(100, FinancialMethods.SixJars.totalPercent)
    }
}
