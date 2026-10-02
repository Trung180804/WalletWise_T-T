package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.*
import kotlin.test.*

class LiveFinancialAllocationTest {
    private val dates = object : BudgetDateProvider {
        override fun currentLocalDate() = BudgetDate(2026, 9, 16)
        override fun localDateAt(epochMilliseconds: Long) = if (epochMilliseconds == 1L) currentLocalDate() else BudgetDate(2026, 8, 16)
    }
    private val plan = BudgetCalculator.allocateExactly(10000000L, BudgetRule.JARS)
    private fun tx(id: String = "meal", amount: Double = 50000.0, type: String = "Chi", uid: String = "u", category: String = "Ăn uống", timestamp: Long = 1L) =
        Transaction(id, uid, type, "Tiền mặt", amount, category, "", timestamp)
    private fun calc(transactions: List<Transaction>, mapping: Map<String, String> = emptyMap()) =
        LiveFinancialCalculator.calculate(plan, transactions, "u", "09-2026", dates, mapping)
    @Test fun exactTenMillionAndFiftyThousandExample() {
        assertEquals(listOf(5500000L,1000000L,1000000L,1000000L,500000L,1000000L), plan.allocations.map { it.amount })
        val needs = calc(listOf(tx())).buckets.first()
        assertEquals(50000L, needs.spent); assertEquals(5450000L, needs.remaining)
        assertEquals("0,91%", formatFinancialPercent(needs.usedPercent))
        assertEquals("99,09%", formatFinancialPercent(needs.remainingPercent))
        assertEquals("54,5%", formatFinancialPercent(needs.remainingIncomePercent(plan.income)))
    }
    @Test fun addEditDeleteAndRepeatedSnapshotsDeriveWithoutDoubleSubtraction() {
        assertEquals(100000L, calc(listOf(tx(), tx("second"))).totalSpent)
        assertEquals(70000L, calc(listOf(tx(amount=70000.0))).totalSpent)
        assertEquals(0L, calc(emptyList()).totalSpent)
        assertEquals(50000L, calc(listOf(tx(), tx())).totalSpent)
        assertEquals(100.0, calc(emptyList()).buckets.first().remainingPercent)
    }
    @Test fun excludesIncomeOtherUidAndOtherMonth() {
        assertEquals(0L, calc(listOf(tx(type="Thu"), tx(uid="b"), tx(timestamp=2L))).totalSpent)
    }
    @Test fun customFallbackAndChangingMappingRecalculateWithoutExcludingSpend() {
        val unknown = tx(category="Mua sắm")
        assertEquals(50000L, calc(listOf(unknown)).buckets.last().spent)
        assertEquals(50000L, calc(listOf(unknown), mapOf("Mua sắm" to "play")).buckets.last().spent)
        assertEquals(50000L, calc(listOf(tx()), mapOf("Ăn uống" to "unassigned")).buckets.first().spent)
        assertEquals("play", FinancialCategoryMapping.bucket("Mới", BudgetRule.JARS, emptyMap()))
        assertEquals("wants", FinancialCategoryMapping.bucket("Mới", BudgetRule.FIFTY_THIRTY_TWENTY, emptyMap()))
    }
    @Test fun overBudgetKeepsNegativeRemainingAndPercentages() {
        val needs = calc(listOf(tx(amount=6000000.0))).buckets.first()
        assertEquals(-500000L, needs.remaining); assertEquals(BudgetProgressStatus.EXCEEDED, needs.status)
        assertTrue(needs.usedPercent > 100); assertTrue(needs.remainingPercent < 0)
    }
    @Test fun fiftyThirtyTwentyAndRoundingPreserveIncome() {
        val simple = BudgetCalculator.allocateExactly(101L, BudgetRule.FIFTY_THIRTY_TWENTY)
        assertEquals(101L, simple.totalAmount); assertEquals(100, simple.totalPercent)
        assertEquals("needs", FinancialCategoryMapping.bucket("Ăn uống", BudgetRule.FIFTY_THIRTY_TWENTY, emptyMap()))
        assertEquals("savings", FinancialCategoryMapping.bucket("Đầu tư", BudgetRule.FIFTY_THIRTY_TWENTY, emptyMap()))
    }

    @Test fun everyExpenseCategoryHasAValidBucketForBothMethods() {
        val expected = mapOf("Ăn uống" to "necessities", "Nhà cửa" to "necessities",
            "Hóa đơn" to "necessities", "Y tế" to "necessities", "Di chuyển" to "necessities",
            "Giải trí" to "play", "Du lịch" to "play", "Mua sắm" to "play",
            "Tiết kiệm" to "long_term", "Đầu tư" to "freedom", "Giáo dục" to "education", "Từ thiện" to "giving")
        for ((name, jarsKey) in expected) {
            assertEquals(jarsKey, FinancialCategoryMapping.bucket(name, BudgetRule.JARS, emptyMap()), name)
            val key = when (jarsKey) { "necessities" -> "needs"; "long_term", "freedom" -> "savings"; else -> "wants" }
            assertEquals(key, FinancialCategoryMapping.bucket(name, BudgetRule.FIFTY_THIRTY_TWENTY, emptyMap()), name)
        }
        for (rule in BudgetRule.entries) {
            val live = LiveFinancialCalculator.calculate(BudgetCalculator.allocateExactly(10000000L, rule),
                listOf(tx(category = "Danh mục tự tạo")), "u", "09-2026", dates, emptyMap())
            assertEquals(50000L, live.buckets.sumOf { it.spent })
            assertFalse(live.buckets.any { it.allocation.bucket.key == "unassigned" })
        }
    }

    @Test fun fiftyThirtyTwentyFiftyThousandUsesOnePercentOfNeeds() {
        val live = LiveFinancialCalculator.calculate(BudgetCalculator.allocateExactly(10000000L, BudgetRule.FIFTY_THIRTY_TWENTY),
            listOf(tx()), "u", "09-2026", dates, emptyMap())
        val needs = live.buckets.first()
        assertEquals(5000000L, needs.allocation.amount)
        assertEquals(50000L, needs.spent)
        assertEquals(4950000L, needs.remaining)
        assertEquals("1%", formatFinancialPercent(needs.usedPercent))
        assertEquals("99%", formatFinancialPercent(needs.remainingPercent))
    }

    @Test fun editingCategoryDateAndTypeMovesOrRestoresDerivedMoney() {
        assertEquals(50000L, calc(listOf(tx())).buckets.first().spent)
        val edited = tx(amount = 100000.0)
        assertEquals(5400000L, calc(listOf(edited)).buckets.first().remaining)
        val moved = calc(listOf(edited.copy(category = "Giải trí")))
        assertEquals(0L, moved.buckets.first().spent)
        assertEquals(100000L, moved.buckets.last().spent)
        assertEquals(0L, calc(listOf(edited.copy(timestamp = 2L))).totalSpent)
        assertEquals(0L, calc(listOf(edited.copy(type = "Thu"))).totalSpent)
        assertEquals(5500000L, calc(emptyList()).buckets.first().remaining)
    }

}
