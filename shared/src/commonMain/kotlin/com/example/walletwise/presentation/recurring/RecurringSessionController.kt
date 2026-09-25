package com.example.walletwise.presentation.recurring

import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.repository.RecurringTransactionRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.RecurringAutomationCoordinator
import com.example.walletwise.domain.service.RecurringReconcileReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecurringSessionState(
    val userId: String? = null,
    val isLoading: Boolean = false,
    val recurring: List<RecurringTransaction> = emptyList(),
    val error: RepositoryError? = null,
    val reconcileReport: RecurringReconcileReport = RecurringReconcileReport()
)

class RecurringSessionController(
    private val scope: CoroutineScope,
    private val repository: RecurringTransactionRepository,
    private val coordinator: RecurringAutomationCoordinator
) {
    private val mutableState = MutableStateFlow(RecurringSessionState())
    val state: StateFlow<RecurringSessionState> = mutableState.asStateFlow()
    private var observationJob: Job? = null
    private var observedUserId: String? = null

    fun setUserId(userId: String?) {
        val normalized = userId?.takeIf { it.isNotBlank() }
        if (normalized == observedUserId && observationJob?.isActive == true) return
        val previous = mutableState.value
        observationJob?.cancel()
        observationJob = null
        observedUserId = normalized
        mutableState.value = if (normalized == null) {
            RecurringSessionState()
        } else {
            RecurringSessionState(userId = normalized, isLoading = true)
        }
        previous.userId?.let { oldUserId ->
            if (oldUserId != normalized) {
                scope.launch { coordinator.clearUser(oldUserId, previous.recurring) }
            }
        }
        if (normalized == null) return
        observationJob = scope.launch {
            repository.observeRecurringTransactions(normalized).collect { result ->
                if (observedUserId != normalized) return@collect
                when (result) {
                    is RepositoryResult.Success -> {
                        val sorted = result.value.sortedBy(RecurringTransaction::id)
                        mutableState.value = RecurringSessionState(
                            userId = normalized,
                            recurring = sorted
                        )
                        val report = coordinator.reconcile(normalized, sorted)
                        if (observedUserId == normalized) {
                            mutableState.value = mutableState.value.copy(reconcileReport = report)
                        }
                    }
                    is RepositoryResult.Failure -> mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        error = result.error
                    )
                }
            }
        }
    }

    fun forceReconcile() {
        val current = mutableState.value
        val userId = current.userId ?: return
        scope.launch {
            val report = coordinator.reconcile(userId, current.recurring, forceSchedule = true)
            if (observedUserId == userId) {
                mutableState.value = mutableState.value.copy(reconcileReport = report)
            }
        }
    }

    fun close() {
        observationJob?.cancel()
        observationJob = null
        observedUserId = null
    }
}
