package com.example.walletwise.presentation.transaction

import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.repository.TransactionRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.sortedTransactionsNewestFirst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TransactionSessionState(
    val userId: String? = null,
    val isLoading: Boolean = false,
    val transactions: List<Transaction> = emptyList(),
    val error: RepositoryError? = null
)

/** Owns the single transaction collector and clears user data at every identity boundary. */
class TransactionSessionController(
    private val scope: CoroutineScope,
    private val repository: TransactionRepository
) {
    private val mutableState = MutableStateFlow(TransactionSessionState())
    val state: StateFlow<TransactionSessionState> = mutableState.asStateFlow()

    private val mutableTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = mutableTransactions.asStateFlow()

    private var observedUserId: String? = null
    private var observationJob: Job? = null
    private var generation = 0L
    private var closed = false

    fun setUserId(userId: String?) {
        if (closed) return
        val normalized = userId?.takeIf { it.isNotBlank() }
        if (normalized == observedUserId && observationJob?.isActive == true) return

        observationJob?.cancel()
        val currentGeneration = ++generation
        observationJob = null
        observedUserId = normalized
        publish(
            if (normalized == null) TransactionSessionState()
            else TransactionSessionState(userId = normalized, isLoading = true)
        )
        if (normalized == null) return

        observationJob = scope.launch {
            repository.observeTransactions(normalized).collect { result ->
                if (closed || generation != currentGeneration || observedUserId != normalized) return@collect
                when (result) {
                    is RepositoryResult.Success -> publish(
                        TransactionSessionState(
                            userId = normalized,
                            transactions = result.value.sortedTransactionsNewestFirst()
                        )
                    )
                    is RepositoryResult.Failure -> publish(
                        mutableState.value.copy(
                            userId = normalized,
                            isLoading = false,
                            error = result.error
                        )
                    )
                }
            }
        }
    }

    fun refresh() {
        val userId = observedUserId ?: return
        observationJob?.cancel()
        observationJob = null
        observedUserId = null
        setUserId(userId)
    }

    fun close() {
        if (closed) return
        closed = true
        generation++
        observationJob?.cancel()
        observationJob = null
        observedUserId = null
        publish(TransactionSessionState())
    }

    private fun publish(value: TransactionSessionState) {
        mutableState.value = value
        mutableTransactions.value = value.transactions
    }
}
