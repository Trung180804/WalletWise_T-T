package com.example.walletwise.presentation.transaction

import com.example.walletwise.domain.repository.TransactionRepository
import com.example.walletwise.domain.repository.CategoryRepository
import com.example.walletwise.data.repository.CallbackCategoryRepository
import com.example.walletwise.presentation.category.CategorySessionController
import com.example.walletwise.presentation.auth.ConnectedAuthPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class TransactionHomeState(
    val userId: String? = null,
    val displayLabel: String = "",
    val list: TransactionListUiState = TransactionListUiState(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val selectedTransactionId: String? = null,
    val editor: TransactionEditorState = TransactionEditorState(),
    val writableTransactionIds: Set<String> = emptySet()
) {
    val balance: Double get() = totalIncome - totalExpense
}

/** Controller-owned bridge. Auth is authoritative; snapshots mask all data with a mismatched owner. */
class TransactionHomeSession(
    scope: CoroutineScope,
    private val auth: ConnectedAuthPresenter,
    repository: TransactionRepository,
    val dateTimeProvider: TransactionDateTimeProvider,
    categoryRepository: CategoryRepository = CallbackCategoryRepository(null)
) {
    val transactions = TransactionSessionController(scope, repository)
    val presenter = TransactionListPresenter(scope, transactions.state, dateTimeProvider)
    val editor = TransactionEditorPresenter(scope, repository, CategorySessionController(scope, categoryRepository), { auth.snapshot.user?.uid })
    private val mutableState = MutableStateFlow(TransactionHomeState())
    val state: StateFlow<TransactionHomeState> = mutableState.asStateFlow()
    private var selectedId: String? = null
    private var selectionOwner: String? = null
    private var disposed = false
    val snapshot: TransactionHomeState get() = buildState()
    private val authJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        auth.state.collect { value ->
            // SDK session identity is authoritative even while the form completion is pending.
            // Loading/success UI transitions for the same SDK user must not restart observation.
            val uid = value.user?.uid
            if (selectionOwner != uid) { selectedId = null; selectionOwner = uid }
            transactions.setUserId(uid)
            editor.setUserId(uid)
            mutableState.value = buildState()
        }
    }
    private val presentationJob = scope.launch {
        combine(transactions.state, presenter.state, editor.state, editor.categories.state) { _, _, _, _ -> Unit }.collect {
            mutableState.value = buildState()
        }
    }

    fun retry() { if (!disposed) transactions.refresh() }
    fun select(transactionId: String) {
        if (disposed || snapshot.list.rows.none { it.id == transactionId }) return
        selectionOwner = snapshot.userId
        selectedId = transactionId
        presenter.onRowSelected(transactionId)
        mutableState.value = buildState()
    }
    fun dismissDetail() { selectedId = null; mutableState.value = buildState() }
    fun openAdd() { if (!disposed) { selectedId = null; editor.openAdd() } }
    fun edit(transactionId: String) {
        val item = transaction(transactionId)?.takeUnless { it.isLegacy } ?: return
        if (!disposed) editor.openEdit(item)
    }
    fun requestDelete(transactionId: String) {
        val item = transaction(transactionId)?.takeUnless { it.isLegacy } ?: return
        if (!disposed) { selectedId = null; editor.requestDelete(item); mutableState.value = buildState() }
    }
    fun transaction(transactionId: String) = transactions.state.value.transactions.firstOrNull { !disposed && it.id == transactionId && it.userId == auth.snapshot.user?.uid }
    fun logout() {
        if (disposed) return
        auth.logout()
        if (auth.snapshot.user == null) {
            selectedId = null
            transactions.setUserId(null)
            mutableState.value = buildState()
        }
    }
    fun dispose() {
        if (disposed) return
        disposed = true
        authJob.cancel()
        presentationJob.cancel()
        presenter.close()
        editor.dispose()
        transactions.close()
        selectedId = null
        mutableState.value = TransactionHomeState()
    }

    private fun buildState(): TransactionHomeState {
        if (disposed) return TransactionHomeState()
        val user = auth.snapshot.user.takeIf { auth.snapshot.isAuthenticated } ?: return TransactionHomeState()
        val source = transactions.state.value.takeIf { it.userId == user.uid }
        val list = presenter.state.value.takeIf { it.userId == user.uid && source?.isLoading != true }
            ?: TransactionListUiState(userId = user.uid, isLoading = true)
        val owned = source?.transactions.orEmpty()
        return TransactionHomeState(
            userId = user.uid, displayLabel = user.displayLabel, list = list,
            totalIncome = owned.filter { it.type.trim().equals("Thu", true) }.sumOf { it.amount.takeIf(Double::isFinite) ?: 0.0 },
            totalExpense = owned.filter { it.type.trim().equals("Chi", true) }.sumOf { it.amount.takeIf(Double::isFinite) ?: 0.0 },
            selectedTransactionId = selectedId.takeIf { selectionOwner == user.uid && list.rows.any { row -> row.id == it } },
            editor = editor.snapshot,
            writableTransactionIds = owned.filterNot { it.isLegacy }.map { it.id }.toSet()
        )
    }
}

interface TransactionHomeObserver { fun createdHome(session: TransactionHomeSession) }
