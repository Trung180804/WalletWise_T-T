package com.example.walletwise.presentation.reminder

import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.repository.ReminderRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.ReconcileReminderSchedulingUseCase
import com.example.walletwise.domain.service.ReminderReconcileReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReminderSessionState(
    val userId: String? = null,
    val isLoading: Boolean = false,
    val reminders: List<Reminder> = emptyList(),
    val error: RepositoryError? = null,
    val schedulingReport: ReminderReconcileReport = ReminderReconcileReport()
)

class ReminderSessionController(
    private val scope: CoroutineScope,
    private val repository: ReminderRepository,
    private val reconcileScheduling: ReconcileReminderSchedulingUseCase
) {
    private val mutableState = MutableStateFlow(ReminderSessionState())
    val state: StateFlow<ReminderSessionState> = mutableState.asStateFlow()
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
            ReminderSessionState()
        } else {
            ReminderSessionState(userId = normalized, isLoading = true)
        }
        previous.userId?.let { oldUserId ->
            if (oldUserId != normalized) {
                scope.launch { reconcileScheduling.clearUser(oldUserId, previous.reminders) }
            }
        }
        if (normalized == null) return
        observationJob = scope.launch {
            repository.observeReminders(normalized).collect { result ->
                if (observedUserId != normalized) return@collect
                when (result) {
                    is RepositoryResult.Success -> {
                        val sorted = result.value.sortedBy(Reminder::id)
                        mutableState.value = ReminderSessionState(
                            userId = normalized,
                            reminders = sorted
                        )
                        val report = reconcileScheduling.reconcile(normalized, sorted)
                        if (observedUserId == normalized) {
                            mutableState.value = mutableState.value.copy(schedulingReport = report)
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
            val report = reconcileScheduling.reconcile(userId, current.reminders, force = true)
            if (observedUserId == userId) {
                mutableState.value = mutableState.value.copy(schedulingReport = report)
            }
        }
    }

    fun close() {
        observationJob?.cancel()
        observationJob = null
        observedUserId = null
    }
}
