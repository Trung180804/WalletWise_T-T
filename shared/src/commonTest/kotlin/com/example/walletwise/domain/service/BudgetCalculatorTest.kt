package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.model.BudgetRule
import com.example.walletwise.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BudgetCalculatorTest {
    @Test
    fun allocationPreservesBothRulesAndTotal() {
        val standard = BudgetCalculator.allocate(10_000_000.0, BudgetRule.FIFTY_THIRTY_TWENTY)
        assertEquals(5_000_000.0, standard.needs)
        assertEquals(3_000_000.0, standard.wants)
        assertEquals(2_000_000.0, standard.savings)
        assertEquals(10_000_000.0, standard.total, 0.001)

        val jars = BudgetCalculator.allocate(10_000_000.0, BudgetRule.JARS)
        assertEquals(5_500_000.0, jars.needs)
        assertEquals(1_000_000.0, jars.wants)
        assertEquals(3_500_000.0, jars.savings)
        assertEquals(10_000_000.0, jars.total, 0.001)
    }

    @Test
    fun invalidAmountsProduceSafeZeroAllocationAndLargeFiniteAmountStaysFinite() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { amount ->
            assertEquals(BudgetAllocation(0.0, 0.0, 0.0), BudgetCalculator.allocate(amount, BudgetRule.JARS))
        }
        val large = BudgetCalculator.allocate(1.0e300, BudgetRule.FIFTY_THIRTY_TWENTY)
        assertTrue(large.needs.isFinite())
        assertTrue(large.wants.isFinite())
        assertTrue(large.savings.isFinite())
    }

    @Test
    fun spentUsesOnlyExpenseInSelectedLocalMonthAndExactCategoryGroups() {
        val provider = TokenDateProvider(
            mapOf(
                1L to BudgetDate(2026, 9, 1),
                2L to BudgetDate(2026, 9, 2),
                3L to BudgetDate(2026, 9, 3),
                4L to BudgetDate(2026, 8, 31),
                5L to BudgetDate(2026, 9, 4)
            )
        )
        val transactions = listOf(
            tx(1L, "Chi", "Ăn uống", 100.0),
            tx(2L, "Chi", "Đầu tư", 200.0),
            tx(3L, "Chi", "Không biết", 300.0),
            tx(4L, "Chi", "Ăn uống", 400.0),
            tx(5L, "Thu", "Ăn uống", 500.0),
            tx(999L, "Chi", "Ăn uống", 600.0),
            tx(1L, "Chi", "Ăn uống", Double.NaN),
            tx(1L, "Chi", "Ăn uống", -50.0)
        )

        val spent = BudgetCalculator.calculateSpent(transactions, BudgetDate(2026, 9, 15), provider)

        assertEquals(100.0, spent.needs)
        assertEquals(200.0, spent.savings)
        assertEquals(300.0, spent.wants)
    }

    @Test
    fun platformDateMappingControlsMonthInsteadOfUtcAssumption() {
        val provider = TokenDateProvider(mapOf(1L to BudgetDate(2026, 9, 1)))
        val spent = BudgetCalculator.calculateSpent(
            listOf(tx(1L, "Chi", "Hóa đơn", 100.0)),
            BudgetDate(2026, 9, 1),
            provider
        )
        assertEquals(100.0, spent.needs)
    }

    @Test
    fun metricsClampRemainingAndProgressButKeepExceededAmountAndMealRatios() {
        val provider = TokenDateProvider(mapOf(1L to BudgetDate(2026, 9, 30)))
        val plan = BudgetPlan(
            totalBudget = 100.0,
            needsLimit = 50.0,
            wantsLimit = 30.0,
            savingsLimit = 20.0
        )
        val metrics = BudgetCalculator.metrics(
            plan,
            listOf(tx(1L, "Chi", "Ăn uống", 120.0)),
            BudgetDate(2026, 9, 30),
            provider
        )

        assertEquals(0.0, metrics.totalRemaining)
        assertEquals(0.0, metrics.dailySafeLimit)
        assertEquals(1f, metrics.needs.fraction)
        assertEquals(BudgetProgressStatus.EXCEEDED, metrics.needs.status)
        assertEquals(-70.0, metrics.needs.remaining)
        assertEquals(0.0, metrics.breakfastLimit + metrics.lunchLimit + metrics.dinnerLimit)
    }

    @Test
    fun emptyTransactionsAndZeroLimitAreSafe() {
        val provider = TokenDateProvider(emptyMap())
        val metrics = BudgetCalculator.metrics(BudgetPlan(), emptyList(), BudgetDate(2026, 9, 1), provider)
        assertEquals(0.0, metrics.totalSpent)
        assertEquals(0f, metrics.needs.fraction)
        assertEquals(BudgetProgressStatus.SAFE, metrics.needs.status)
    }

    private fun tx(timestamp: Long, type: String, category: String, amount: Double) = Transaction(
        timestamp = timestamp,
        type = type,
        category = category,
        amount = amount
    )
}

private class TokenDateProvider(
    private val dates: Map<Long, BudgetDate>,
    private val current: BudgetDate = BudgetDate(2026, 9, 15)
) : BudgetDateProvider {
    override fun currentLocalDate(): BudgetDate = current
    override fun localDateAt(epochMilliseconds: Long): BudgetDate? = dates[epochMilliseconds]
}
