package com.example.walletwise.presentation.transaction

import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionListPresenterTest {
    @Test
    fun largeAmountsMatchPlainDigitsEvenWhenDoubleUsesScientificNotation() = runTest {
        val session = MutableStateFlow(TransactionSessionState(userId = "uid", transactions = listOf(
            Transaction("large", "uid", "Thu", "Tiền mặt", 10_000_000.0, "Lương", "", 20L)
        )))
        val presenter = TransactionListPresenter(this, session, FixedTransactionDateTimeProvider)
        advanceUntilIdle()
        listOf("10000000", "10.000.000").forEach { query ->
            presenter.updateSearchQuery(query)
            advanceUntilIdle()
            assertEquals(listOf("large"), presenter.state.value.rows.map { it.id })
        }
        presenter.close()
    }

    @Test
    fun searchIsTrimmedCaseAndAccentInsensitive_acrossFieldsAndLiveUpdates() = runTest {
        val session = MutableStateFlow(
            TransactionSessionState(
                userId = "user-1",
                transactions = listOf(
                    Transaction("meal", "user-1", "Chi", "Tiền mặt", 100_000.0, "Ăn uống", "Ăn sáng", 20L),
                    Transaction("salary", "user-1", "Thu", "Chuyển khoản", 5_000_000.0, "Lương", "Tháng 9", 10L)
                )
            )
        )
        val presenter = TransactionListPresenter(this, session, FixedTransactionDateTimeProvider)
        advanceUntilIdle()
        assertEquals(listOf("meal", "salary"), presenter.state.value.rows.map { it.id })

        fun search(query: String, expected: List<String>) {
            presenter.updateSearchQuery(query)
            advanceUntilIdle()
            assertEquals(expected, presenter.state.value.rows.map { it.id })
        }
        search("  AN SANG", listOf("meal"))
        search("  an sang  ", listOf("meal"))
        search("A\u0306n sa\u0301ng", listOf("meal"))
        search("an uong", listOf("meal"))
        search("chi", listOf("meal"))
        search("THU", listOf("salary"))
        search("chuyen khoan", listOf("salary"))
        search("100000", listOf("meal"))
        search("100.000", listOf("meal"))
        search("  ", listOf("meal", "salary"))
        search("09/09/2026", listOf("meal", "salary"))
        search("không tồn tại", emptyList())

        presenter.updateSearchQuery("thuong")
        session.value = session.value.copy(
            transactions = session.value.transactions +
                Transaction("bonus", "user-1", "Thu", "Tiền mặt", 200_000.0, "Thưởng", "", 30L)
        )
        advanceUntilIdle()
        assertEquals(listOf("bonus"), presenter.state.value.rows.map { it.id })

        session.value = TransactionSessionState(userId = "user-2", transactions = emptyList())
        advanceUntilIdle()
        assertEquals("", presenter.state.value.filters.searchQuery)
        assertTrue(presenter.state.value.rows.isEmpty())
        presenter.updateSearchQuery("old query")
        advanceUntilIdle()
        session.value = TransactionSessionState()
        advanceUntilIdle()
        assertEquals("", presenter.state.value.filters.searchQuery)
        assertTrue(presenter.state.value.rows.isEmpty())
        presenter.close()
    }

    @Test
    fun loadingEmptyErrorAndData_areMappedFromTheSharedSession() = runTest {
        val session = MutableStateFlow(TransactionSessionState(userId = "user-1", isLoading = true))
        val presenter = TransactionListPresenter(this, session, FixedTransactionDateTimeProvider)
        advanceUntilIdle()
        assertTrue(presenter.state.value.isLoading)
        assertTrue(presenter.state.value.rows.isEmpty())

        session.value = TransactionSessionState(userId = "user-1")
        advanceUntilIdle()
        assertFalse(presenter.state.value.isLoading)
        assertTrue(presenter.state.value.rows.isEmpty())

        session.value = TransactionSessionState(
            userId = "user-1",
            error = RepositoryError(RepositoryErrorCode.NETWORK, "offline")
        )
        advanceUntilIdle()
        assertEquals("offline", presenter.state.value.repositoryError?.message)

        session.value = TransactionSessionState(
            userId = "user-1",
            transactions = listOf(transaction("tx-1", 20L, "Thu"))
        )
        advanceUntilIdle()
        assertEquals(listOf("tx-1"), presenter.state.value.rows.map(TransactionRowViewData::id))
        assertNull(presenter.state.value.repositoryError)
        presenter.close()
    }

    @Test
    fun newestSortTypePaymentAndDateFilters_areDeterministic() = runTest {
        val session = MutableStateFlow(
            TransactionSessionState(
                userId = "user-1",
                transactions = listOf(
                    transaction("expense-card", 30L, "Chi", "Thẻ"),
                    transaction("income", 30L, "Thu", "Tiền mặt"),
                    transaction("expense-cash", 10L, "Chi", "Tiền mặt")
                )
            )
        )
        val presenter = TransactionListPresenter(this, session, FixedTransactionDateTimeProvider)
        advanceUntilIdle()
        assertEquals(
            listOf("expense-card", "income", "expense-cash"),
            presenter.state.value.rows.map(TransactionRowViewData::id)
        )

        presenter.updateFilters(
            TransactionListFilterState(
                type = TransactionTypeFilter.EXPENSE,
                paymentMethod = "Tiền mặt",
                dateRange = TransactionDateRange(5L, 20L)
            )
        )
        advanceUntilIdle()
        assertEquals(listOf("expense-cash"), presenter.state.value.rows.map(TransactionRowViewData::id))

        presenter.updateFilters(TransactionListFilterState(type = TransactionTypeFilter.INCOME))
        advanceUntilIdle()
        assertEquals(listOf("income"), presenter.state.value.rows.map(TransactionRowViewData::id))
        presenter.close()
    }

    @Test
    fun amountDateAndImageViewData_matchTheExistingVietnameseDisplay() = runTest {
        val session = MutableStateFlow(
            TransactionSessionState(
                userId = "user-1",
                transactions = listOf(
                    transaction("income", 20L, " Thu ", imageUrl = " https://example.com/a.jpg "),
                    transaction("expense", 0L, "Chi", imageUrl = "   ")
                )
            )
        )
        val presenter = TransactionListPresenter(this, session, FixedTransactionDateTimeProvider)
        advanceUntilIdle()

        val income = presenter.state.value.rows.first { it.id == "income" }
        assertEquals("+125.001 ₫", income.amountText)
        assertEquals("08:05", income.timeText)
        assertEquals("09/09/2026 - 08:05", income.fullDateText)
        assertEquals("https://example.com/a.jpg", income.imageUrl)
        assertTrue(income.hasImage)

        val expense = presenter.state.value.rows.first { it.id == "expense" }
        assertEquals("-125.001 ₫", expense.amountText)
        assertEquals("--:--", expense.timeText)
        assertEquals("Không rõ thời gian", expense.fullDateText)
        assertFalse(expense.hasImage)
        presenter.close()
    }

    @Test
    fun rowEditDeleteEventsCarryTheCorrectId_andConsumedEventsDoNotReplay() = runTest {
        val session = MutableStateFlow(
            TransactionSessionState(
                userId = "user-1",
                transactions = listOf(transaction("tx-1", 20L, "Chi"))
            )
        )
        val presenter = TransactionListPresenter(this, session, FixedTransactionDateTimeProvider)
        advanceUntilIdle()

        presenter.onRowSelected("tx-1")
        val selected = requireNotNull(presenter.state.value.pendingEvent)
        assertEquals("tx-1", assertIs<TransactionListUiEvent.RowSelected>(selected.event).transactionId)
        presenter.consumeEvent(selected.id)
        assertNull(presenter.state.value.pendingEvent)
        presenter.consumeEvent(selected.id)
        assertNull(presenter.state.value.pendingEvent)

        presenter.onEditRequested("tx-1")
        val edit = requireNotNull(presenter.state.value.pendingEvent)
        assertEquals("tx-1", assertIs<TransactionListUiEvent.EditRequested>(edit.event).transactionId)
        presenter.consumeEvent(edit.id)

        presenter.onDeleteRequested("tx-1")
        val delete = requireNotNull(presenter.state.value.pendingEvent)
        assertEquals("tx-1", assertIs<TransactionListUiEvent.DeleteRequested>(delete.event).transactionId)
        presenter.consumeEvent(delete.id)

        presenter.onEditRequested("missing")
        assertNull(presenter.state.value.pendingEvent)
        presenter.close()
    }

    private fun transaction(
        id: String,
        timestamp: Long,
        type: String,
        paymentMethod: String = "Tiền mặt",
        imageUrl: String = ""
    ) = Transaction(
        id = id,
        userId = "user-1",
        type = type,
        paymentMethod = paymentMethod,
        amount = 125_000.6,
        category = "Ăn uống",
        note = "Bữa trưa",
        timestamp = timestamp,
        imageUrl = imageUrl
    )
}

private object FixedTransactionDateTimeProvider : TransactionDateTimeProvider {
    override fun localDateTime(epochMilliseconds: Long): TransactionLocalDateTime? =
        TransactionLocalDateTime(2026, 9, 9, 8, 5)
}
