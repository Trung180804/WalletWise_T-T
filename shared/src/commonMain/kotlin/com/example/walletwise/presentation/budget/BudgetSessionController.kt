package com.example.walletwise.presentation.budget

import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.repository.BudgetPlanRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BudgetSessionState(
    val userId: String? = null,
    val monthKey: String = "",
    val isLoading: Boolean = false,
    val plan: BudgetPlan? = null,
    val error: RepositoryError? = null
)

class BudgetSessionController(
    private val scope: CoroutineScope,
    private val repository: BudgetPlanRepository
) {
    private val mutableState = MutableStateFlow(BudgetSessionState())
    val state: StateFlow<BudgetSessionState> = mutableState.asStateFlow()
    private var observationJob: Job? = null
    private var observedKey: Pair<String, String>? = null

    fun setSession(userId: String?, monthKey: String) {
        val normalizedUserId = userId?.takeIf { it.isNotBlank() }
        val key = normalizedUserId?.let { it to monthKey }
        if (key != null && key == observedKey && observationJob?.isActive == true) return

        observationJob?.cancel()
        observationJob = null
        observedKey = key
        if (key == null) {
            mutableState.value = BudgetSessionState()
            return
        }

        mutableState.value = BudgetSessionState(
            userId = key.first,
            monthKey = key.second,
            isLoading = true
        )
        observationJob = scope.launch {
            repository.observeBudgetPlan(key.first, key.second).collect { result ->
                when (result) {
                    is RepositoryResult.Success -> mutableState.value = BudgetSessionState(
                        userId = key.first,
                        monthKey = key.second,
                        isLoading = false,
                        plan = result.value
                    )
                    is RepositoryResult.Failure -> mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        error = result.error
                    )
                }
            }
        }
    }

    fun close() {
        observationJob?.cancel()
        observationJob = null
        observedKey = null
    }
}
