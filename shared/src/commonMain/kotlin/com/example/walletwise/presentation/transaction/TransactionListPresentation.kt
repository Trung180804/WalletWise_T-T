package com.example.walletwise.presentation.transaction

import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.service.sortedTransactionsNewestFirst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.round

enum class TransactionTypeFilter { ALL, INCOME, EXPENSE }

enum class TransactionListSortOrder { NEWEST_FIRST, OLDEST_FIRST }

data class TransactionDateRange(
    val startEpochMilliseconds: Long,
    val endEpochMilliseconds: Long
) {
    fun contains(timestamp: Long): Boolean =
        startEpochMilliseconds <= endEpochMilliseconds &&
            timestamp in startEpochMilliseconds..endEpochMilliseconds
}

data class TransactionListFilterState(
    val type: TransactionTypeFilter = TransactionTypeFilter.ALL,
    val paymentMethod: String? = null,
    val dateRange: TransactionDateRange? = null,
    val sortOrder: TransactionListSortOrder = TransactionListSortOrder.NEWEST_FIRST
)

data class TransactionLocalDateTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int
)

interface TransactionDateTimeProvider {
    /** Returns null when the timestamp cannot be represented in the configured platform timezone. */
    fun localDateTime(epochMilliseconds: Long): TransactionLocalDateTime?
}

data class TransactionRowViewData(
    val id: String,
    val type: String,
    val isIncome: Boolean,
    val amountText: String,
    val category: String,
    val paymentMethod: String,
    val note: String,
    val timeText: String,
    val fullDateText: String,
    val imageUrl: String,
    val hasImage: Boolean
)

sealed interface TransactionListUiEvent {
    data class RowSelected(val transactionId: String) : TransactionListUiEvent
    data class EditRequested(val transactionId: String) : TransactionListUiEvent
    data class DeleteRequested(val transactionId: String) : TransactionListUiEvent
}

data class TransactionListEventEnvelope(
    val id: Long,
    val event: TransactionListUiEvent
)

data class TransactionListUiState(
    val userId: String? = null,
    val isLoading: Boolean = false,
    val rows: List<TransactionRowViewData> = emptyList(),
    val repositoryError: RepositoryError? = null,
    val filters: TransactionListFilterState = TransactionListFilterState(),
    val pendingEvent: TransactionListEventEnvelope? = null
)

class TransactionListPresenter(
    private val scope: CoroutineScope,
    private val sessionState: StateFlow<TransactionSessionState>,
    private val dateTimeProvider: TransactionDateTimeProvider
) {
    private val mutableFilters = MutableStateFlow(TransactionListFilterState())
    private val mutableState = MutableStateFlow(TransactionListUiState())
    val state: StateFlow<TransactionListUiState> = mutableState.asStateFlow()
    private val sourceJob: Job
    private var nextEventId = 1L

    init {
        sourceJob = scope.launch {
            combine(sessionState, mutableFilters) { session, filters -> session to filters }
                .collect { (session, filters) -> publish(session, filters) }
        }
    }

    fun updateFilters(filters: TransactionListFilterState) {
        mutableFilters.value = filters
    }

    fun onRowSelected(transactionId: String) = emitForExisting(transactionId) {
        TransactionListUiEvent.RowSelected(it)
    }

    fun onEditRequested(transactionId: String) = emitForExisting(transactionId) {
        TransactionListUiEvent.EditRequested(it)
    }

    fun onDeleteRequested(transactionId: String) = emitForExisting(transactionId) {
        TransactionListUiEvent.DeleteRequested(it)
    }

    fun consumeEvent(eventId: Long) {
        if (mutableState.value.pendingEvent?.id == eventId) {
            mutableState.value = mutableState.value.copy(pendingEvent = null)
        }
    }

    fun close() {
        sourceJob.cancel()
    }

    private fun publish(session: TransactionSessionState, filters: TransactionListFilterState) {
        val filtered = session.transactions
            .asSequence()
            .filter { transaction -> filters.type.matches(transaction) }
            .filter { transaction ->
                filters.paymentMethod == null || transaction.paymentMethod == filters.paymentMethod
            }
            .filter { transaction -> filters.dateRange?.contains(transaction.timestamp) != false }
            .toList()
            .let { transactions ->
                when (filters.sortOrder) {
                    TransactionListSortOrder.NEWEST_FIRST -> transactions.sortedTransactionsNewestFirst()
                    TransactionListSortOrder.OLDEST_FIRST -> transactions.sortedWith(
                        compareBy<Transaction> { it.timestamp }.thenBy(Transaction::id)
                    )
                }
            }
        val current = mutableState.value
        mutableState.value = TransactionListUiState(
            userId = session.userId,
            isLoading = session.isLoading,
            rows = filtered.map(::toRow),
            repositoryError = session.error,
            filters = filters,
            pendingEvent = current.pendingEvent.takeIf { current.userId == session.userId }
        )
    }

    private fun toRow(transaction: Transaction): TransactionRowViewData {
        val dateTime = transaction.timestamp
            .takeIf { it > 0L }
            ?.let(dateTimeProvider::localDateTime)
            ?.takeIf(TransactionLocalDateTime::isValid)
        val imageUrl = transaction.imageUrl.trim()
        return TransactionRowViewData(
            id = transaction.id,
            type = transaction.type,
            isIncome = transaction.isIncome(),
            amountText = formatTransactionAmount(transaction.amount, transaction.type),
            category = transaction.category,
            paymentMethod = transaction.paymentMethod,
            note = transaction.note,
            timeText = dateTime?.let { "${pad2(it.hour)}:${pad2(it.minute)}" } ?: "--:--",
            fullDateText = dateTime?.let {
                "${pad2(it.day)}/${pad2(it.month)}/${it.year} - ${pad2(it.hour)}:${pad2(it.minute)}"
            } ?: "Không rõ thời gian",
            imageUrl = imageUrl,
            hasImage = imageUrl.isNotEmpty()
        )
    }

    private fun emitForExisting(
        transactionId: String,
        event: (String) -> TransactionListUiEvent
    ) {
        if (transactionId.isBlank() || mutableState.value.rows.none { it.id == transactionId }) return
        mutableState.value = mutableState.value.copy(
            pendingEvent = TransactionListEventEnvelope(nextEventId++, event(transactionId))
        )
    }
}

fun formatTransactionAmount(amount: Double, type: String): String {
    val safeAmount = if (amount.isFinite()) round(amount.absoluteValue).toLong() else 0L
    val grouped = safeAmount.toString().reversed().chunked(3).joinToString(".").reversed()
    val sign = if (type.trim().equals("Thu", ignoreCase = true)) "+" else "-"
    return "$sign$grouped ₫"
}

private fun TransactionTypeFilter.matches(transaction: Transaction): Boolean = when (this) {
    TransactionTypeFilter.ALL -> true
    TransactionTypeFilter.INCOME -> transaction.isIncome()
    TransactionTypeFilter.EXPENSE -> transaction.type.trim().equals("Chi", ignoreCase = true)
}

private fun Transaction.isIncome(): Boolean = type.trim().equals("Thu", ignoreCase = true)

private fun TransactionLocalDateTime.isValid(): Boolean =
    year in 1..9999 && month in 1..12 && day in 1..31 && hour in 0..23 && minute in 0..59

private fun pad2(value: Int): String = if (value in 0..9) "0$value" else value.toString()
