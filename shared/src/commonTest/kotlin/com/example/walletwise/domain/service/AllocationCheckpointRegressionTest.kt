package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.*
import com.example.walletwise.presentation.transaction.*
import com.example.walletwise.data.mapper.FirestoreWireMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AllocationCheckpointRegressionTest {
    @Test fun overBudgetMoneyKeepsMinusOutsideDigitGrouping() {
        assertEquals("-500.000 ₫", com.example.walletwise.presentation.budget.formatBudgetMoney(-500000.0))
        assertEquals("-1.000 ₫", com.example.walletwise.presentation.budget.formatBudgetMoney(-1000.0))
        assertEquals("5.450.000 ₫", com.example.walletwise.presentation.budget.formatBudgetMoney(5450000.0))
    }
    private val dates = object : BudgetDateProvider {
        override fun currentLocalDate() = BudgetDate(2026, 9, 17)
        override fun localDateAt(epochMilliseconds: Long) = if (epochMilliseconds == 1L) currentLocalDate() else BudgetDate(2026, 8, 31)
    }
    private val categories = listOf(Category("food-id", "Ăn uống"), Category("home-id", "Nhà cửa"))
    private fun tx(category: String = "Ăn uống") = Transaction("t", "u", "Chi", "Tiền mặt", 50000.0, category, "", 1L)
    private fun calc(txs: List<Transaction>, mappings: Map<String, String> = emptyMap(), rule: BudgetRule = BudgetRule.FIFTY_THIRTY_TWENTY,
        cats: List<Category> = categories) = LiveFinancialCalculator.calculate(BudgetCalculator.allocateExactly(10000000L, rule), txs, "u", "09-2026", dates, mappings, cats)

    @Test fun namesAliasesAndLegacyCategoryIdResolveNeedsForBothMethods() {
        for (name in listOf("Ăn uống", "  AN UONG  ", "Nhà cửa", " NHA CUA ", "HÓA ĐƠN", "Điện/nước/internet", "Chi phí sinh hoạt bắt buộc", "food-id", "home-id")) {
            assertEquals(4950000L, calc(listOf(tx(name))).buckets.first().remaining, name)
            assertEquals(5450000L, calc(listOf(tx(name)), rule = BudgetRule.JARS).buckets.first().remaining, name)
        }
    }
    @Test fun idMappingHasPriorityAndSurvivesRenameAndWireRoundTrip() {
        val renamed = categories.map { if (it.id == "food-id") it.copy(name = "Bữa ăn mới") else it }
        val transaction = tx().copy(categoryId = "food-id")
        val mapping = mapOf("id:food-id" to "needs", "Ăn uống" to "wants")
        assertEquals(50000L, calc(listOf(transaction), mapping, cats = renamed).buckets.first().spent)
        val data = FirestoreWireMapper.transactionToMap(transaction)
        assertEquals("food-id", data["categoryId"])
        assertEquals(transaction, FirestoreWireMapper.transactionFromMap("t", "u", data))
        assertFalse(FirestoreWireMapper.transactionToMap(tx()).containsKey("categoryId"))
    }
    @Test fun legacyOverrideNormalizesAccentCaseAndTrim() {
        assertEquals(50000L, calc(listOf(tx(" an uong ")), mapOf(" ĂN UỐNG " to "wants")).buckets[1].spent)
        assertEquals(50000L, calc(listOf(tx()), mapOf("id:food-id" to "unassigned")).buckets.first().spent)
        assertEquals(50000L, calc(listOf(tx("Danh mục mới"))).buckets[1].spent)
        assertEquals(50000L, calc(listOf(tx("Danh mục mới")), mapOf("Danh mục mới" to "needs")).buckets.first().spent)
    }
    @Test fun filteringPrecedesIdDedupAndRecurringSnapshotsDoNotSubtractTwice() {
        val recurring = tx().copy(id = "recurring:stable")
        assertEquals(50000L, calc(listOf(recurring.copy(userId = "other"), recurring, recurring)).totalSpent)
        assertEquals(0L, calc(listOf(tx().copy(type = "Thu"), tx().copy(timestamp = 2L), tx().copy(userId = "other"))).totalSpent)
        assertEquals(50000L, calc(listOf(tx().copy(type = " chi "))).totalSpent)
    }
    @Test fun progressZeroHalfFullAndOverflowPreservesNegativeRemaining() {
        for ((amount, fraction) in listOf(0.0 to 0f, 2500000.0 to 0.5f, 5000000.0 to 1f, 6000000.0 to 1f)) {
            val usage = calc(listOf(tx().copy(amount = amount))).buckets.first()
            assertEquals(fraction, usage.usedFraction)
            assertEquals(5000000L - amount.toLong(), usage.remaining)
            if (amount >= 5000000.0) assertEquals(BudgetProgressStatus.EXCEEDED, usage.status)
        }
        val usage = calc(listOf(tx())).buckets.first()
        assertEquals("1%", formatFinancialPercent(usage.usedPercent))
        assertEquals("99%", formatFinancialPercent(usage.remainingPercent))
        assertEquals("49,5%", formatFinancialPercent(usage.remainingIncomePercent(10000000L)))
    }
    @Test fun categoryChoicesUseSettingsOrderAndKeepEditOutsideEightOrDeleted() {
        val input = (1..12).reversed().map { Category("c$it", "Mục $it", sortOrder = it) }
        val editing = tx("Mục 12").copy(categoryId = "c12")
        val choices = transactionCategoryChoices(input + input.first(), "Chi", editing.category, editing)
        assertEquals((1..8).map { "c$it" }, choices.firstEight.map { it.id })
        assertEquals("c12", choices.currentOutside?.id)
        assertEquals(3, transactionCategoryChoices(input.take(3), "Chi", "", null).firstEight.size)
        assertEquals("c12", transactionCategoryChoices(input.filter { it.id != "c12" }, "Chi", editing.category, editing).currentOutside?.id)
    }
    @Test fun ocrDraftJoinsPreviewDoesNotSaveAndDoubleConfirmWritesOneStableId() = runTest {
        val writes = mutableListOf<Transaction>()
        val presenter = TransactionDraftPresenter(this, LocalTransactionTextAnalyzer(DraftTestClock), MutableStateFlow(categories), { writes += it; kotlin.Result.success(true) })
        presenter.setUserId("u")
        presenter.acceptReceipt(ReceiptTransactionDraft(TransactionDraft("receipt-id", "u", 50000L, "Chi", "Ăn uống", "QUAN AN TEST", 1L, "Tiền mặt")))
        assertTrue(writes.isEmpty())
        assertEquals(DraftSource.RECEIPT, presenter.state.value.draft?.source)
        presenter.confirm(); presenter.confirm(); runCurrent(); presenter.confirm(); runCurrent()
        assertEquals(1, writes.size); assertEquals("receipt-id", writes.single().id); assertEquals("food-id", writes.single().categoryId)
        presenter.setUserId(null); assertNull(presenter.state.value.draft)
        presenter.acceptReceipt(ReceiptTransactionDraft(TransactionDraft(userId = "u"))); assertNull(presenter.state.value.draft)
        presenter.close()
    }
}
