package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.repository.RecurringExecutionResult
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.usecase.ExecuteRecurringIfDueUseCase
import com.example.walletwise.domain.usecase.RecurringUseCaseResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RecurringScheduleRequest(
    val recurring: RecurringTransaction,
    val occurrence: RecurringOccurrence
)

sealed interface RecurringPlatformScheduleResult {
    data class Scheduled(val exact: Boolean) : RecurringPlatformScheduleResult
    data object Cancelled : RecurringPlatformScheduleResult
    data object NoChange : RecurringPlatformScheduleResult
    data class Failure(val message: String) : RecurringPlatformScheduleResult
}

interface RecurringPlatformScheduler {
    suspend fun schedule(request: RecurringScheduleRequest): RecurringPlatformScheduleResult
    suspend fun scheduleRetry(userId: String, recurringId: String, retryAttempt: Int): RecurringPlatformScheduleResult
    suspend fun cancel(recurringId: String): RecurringPlatformScheduleResult
}

interface RecurringExecutionNotifier {
    suspend fun transactionCreated(recurring: RecurringTransaction)
}

enum class RecurringProcessingStatus {
    COMPLETE,
    NO_CHANGE,
    RETRY_SCHEDULED,
    FAILED,
    REJECTED_IDENTITY
}

data class RecurringProcessingOutcome(
    val status: RecurringProcessingStatus,
    val execution: RecurringExecutionResult? = null,
    val repositoryError: RepositoryError? = null,
    val schedulingError: String? = null,
    val notificationError: String? = null,
    val exactAlarm: Boolean? = null
)

data class RecurringReconcileReport(
    val outcomes: List<RecurringProcessingOutcome> = emptyList()
) {
    val transactionCreationCount: Int = outcomes.count { it.execution?.transactionWasCreated == true }
    val errors: List<String> = outcomes.mapNotNull { outcome ->
        outcome.repositoryError?.message ?: outcome.schedulingError ?: outcome.notificationError
    }
    val hasFailure: Boolean get() = outcomes.any {
        it.status == RecurringProcessingStatus.FAILED ||
            it.status == RecurringProcessingStatus.REJECTED_IDENTITY
    }
}

/**
 * The single application-level recurring orchestration path. Firestore remains
 * the durable duplicate guard; this mutex and signature cache only serialize
 * work inside one coordinator and suppress repeated alarm registration.
 */
class RecurringAutomationCoordinator(
    private val executeIfDue: ExecuteRecurringIfDueUseCase,
    private val scheduler: RecurringPlatformScheduler,
    private val notifier: RecurringExecutionNotifier,
    private val dateTimeProvider: RecurringDateTimeProvider
) {
    private val mutex = Mutex()
    private val appliedSignatures = mutableMapOf<RecurringKey, String>()

    suspend fun process(
        userId: String,
        recurringId: String,
        retryAttempt: Int = 0,
        forceSchedule: Boolean = false
    ): RecurringProcessingOutcome = mutex.withLock {
        processLocked(userId, recurringId, retryAttempt, forceSchedule)
    }

    suspend fun apply(
        recurring: RecurringTransaction,
        forceSchedule: Boolean = false
    ): RecurringProcessingOutcome = process(
        userId = recurring.userId,
        recurringId = recurring.id,
        forceSchedule = forceSchedule
    )

    suspend fun reconcile(
        userId: String,
        recurring: List<RecurringTransaction>,
        forceSchedule: Boolean = false
    ): RecurringReconcileReport = mutex.withLock {
        val currentIds = recurring.mapTo(mutableSetOf(), RecurringTransaction::id)
        val removed = appliedSignatures.keys.filter { it.userId == userId && it.recurringId !in currentIds }
        val outcomes = mutableListOf<RecurringProcessingOutcome>()
        removed.forEach { outcomes += cancelLocked(it) }
        recurring.forEach { rule ->
            outcomes += if (rule.userId != userId || rule.id.isBlank()) {
                RecurringProcessingOutcome(RecurringProcessingStatus.REJECTED_IDENTITY)
            } else {
                // Re-read canonical enabled atomically; a stale disabled snapshot cannot cancel a re-enabled rule.
                processLocked(userId, rule.id, retryAttempt = 0, forceSchedule = forceSchedule)
            }
        }
        RecurringReconcileReport(outcomes)
    }

    suspend fun cancel(userId: String, recurringId: String): RecurringProcessingOutcome = mutex.withLock {
        if (userId.isBlank() || recurringId.isBlank()) {
            RecurringProcessingOutcome(RecurringProcessingStatus.REJECTED_IDENTITY)
        } else {
            cancelLocked(RecurringKey(userId, recurringId))
        }
    }

    suspend fun clearUser(
        userId: String,
        recurring: List<RecurringTransaction>
    ): RecurringReconcileReport = mutex.withLock {
        val keys = buildSet {
            recurring.filter { it.userId == userId }.forEach { add(RecurringKey(userId, it.id)) }
            appliedSignatures.keys.filterTo(this) { it.userId == userId }
        }
        RecurringReconcileReport(keys.map { cancelLocked(it) })
    }

    private suspend fun processLocked(
        userId: String,
        recurringId: String,
        retryAttempt: Int,
        forceSchedule: Boolean
    ): RecurringProcessingOutcome {
        if (userId.isBlank() || recurringId.isBlank()) {
            return RecurringProcessingOutcome(RecurringProcessingStatus.REJECTED_IDENTITY)
        }
        return when (val result = executeIfDue(userId, recurringId)) {
            is RecurringUseCaseResult.ValidationFailure ->
                RecurringProcessingOutcome(RecurringProcessingStatus.REJECTED_IDENTITY)
            is RecurringUseCaseResult.RepositoryFailure -> {
                val retry = if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                    scheduler.scheduleRetry(userId, recurringId, retryAttempt + 1)
                } else {
                    RecurringPlatformScheduleResult.Failure("Recurring retry limit reached")
                }
                if (retry is RecurringPlatformScheduleResult.Scheduled) {
                    RecurringProcessingOutcome(
                        status = RecurringProcessingStatus.RETRY_SCHEDULED,
                        repositoryError = result.error,
                        exactAlarm = retry.exact
                    )
                } else {
                    RecurringProcessingOutcome(
                        status = RecurringProcessingStatus.FAILED,
                        repositoryError = result.error,
                        schedulingError = (retry as? RecurringPlatformScheduleResult.Failure)?.message
                    )
                }
            }
            is RecurringUseCaseResult.Success -> applyExecutionLocked(
                userId,
                recurringId,
                result.value,
                forceSchedule
            )
        }
    }

    private suspend fun applyExecutionLocked(
        userId: String,
        recurringId: String,
        execution: RecurringExecutionResult,
        forceSchedule: Boolean
    ): RecurringProcessingOutcome {
        val recurring = execution.recurring
        if (recurring == null) {
            val cancelled = cancelLocked(RecurringKey(userId, recurringId))
            return cancelled.copy(execution = execution)
        }
        if (recurring.userId != userId || recurring.id != recurringId) {
            return RecurringProcessingOutcome(
                status = RecurringProcessingStatus.REJECTED_IDENTITY,
                execution = execution
            )
        }
        if (!recurring.isEnabled) {
            return finalizeNotification(
                recurring,
                execution,
                cancelLocked(RecurringKey(userId, recurringId)).copy(execution = execution)
            )
        }
        val occurrence = RecurringScheduleCalculator.nextUnprocessedOccurrence(
            recurring,
            dateTimeProvider.currentLocalDateTime()
        ) ?: return finalizeNotification(
            recurring,
            execution,
            cancelLocked(RecurringKey(userId, recurringId)).copy(execution = execution)
        )
        val key = RecurringKey(userId, recurringId)
        val signature = scheduleSignature(recurring, occurrence)
        if (!forceSchedule && appliedSignatures[key] == signature) {
            return finalizeNotification(recurring, execution, RecurringProcessingOutcome(
                status = RecurringProcessingStatus.NO_CHANGE,
                execution = execution
            ))
        }
        val cancelled = scheduler.cancel(recurringId)
        if (cancelled is RecurringPlatformScheduleResult.Failure) {
            appliedSignatures.remove(key)
            return finalizeNotification(recurring, execution, RecurringProcessingOutcome(
                status = RecurringProcessingStatus.FAILED,
                execution = execution,
                schedulingError = cancelled.message
            ))
        }
        val outcome = when (val scheduled = scheduler.schedule(RecurringScheduleRequest(recurring, occurrence))) {
            is RecurringPlatformScheduleResult.Scheduled -> {
                appliedSignatures[key] = signature
                RecurringProcessingOutcome(
                    status = RecurringProcessingStatus.COMPLETE,
                    execution = execution,
                    exactAlarm = scheduled.exact
                )
            }
            is RecurringPlatformScheduleResult.Failure -> {
                appliedSignatures.remove(key)
                RecurringProcessingOutcome(
                    status = RecurringProcessingStatus.FAILED,
                    execution = execution,
                    schedulingError = scheduled.message
                )
            }
            else -> {
                appliedSignatures.remove(key)
                RecurringProcessingOutcome(
                    status = RecurringProcessingStatus.FAILED,
                    execution = execution,
                    schedulingError = "Enabled recurring transaction was not scheduled"
                )
            }
        }
        return finalizeNotification(recurring, execution, outcome)
    }

    private suspend fun finalizeNotification(
        recurring: RecurringTransaction,
        execution: RecurringExecutionResult,
        outcome: RecurringProcessingOutcome
    ): RecurringProcessingOutcome {
        if (!execution.transactionWasCreated) return outcome
        return try {
            notifier.transactionCreated(recurring)
            outcome
        } catch (error: Throwable) {
            outcome.copy(notificationError = error.message ?: "Unable to show recurring notification")
        }
    }

    private suspend fun cancelLocked(key: RecurringKey): RecurringProcessingOutcome {
        return when (val result = scheduler.cancel(key.recurringId)) {
            is RecurringPlatformScheduleResult.Failure -> RecurringProcessingOutcome(
                status = RecurringProcessingStatus.FAILED,
                schedulingError = result.message
            )
            else -> {
                appliedSignatures.remove(key)
                RecurringProcessingOutcome(RecurringProcessingStatus.COMPLETE)
            }
        }
    }

    private fun scheduleSignature(recurring: RecurringTransaction, occurrence: RecurringOccurrence): String =
        listOf(
            recurring.id,
            recurring.userId,
            recurring.isEnabled.toString(),
            recurring.frequency,
            recurring.timesCount,
            recurring.startDate,
            recurring.time,
            recurring.lastExecutedDate,
            occurrence.index.toString(),
            RecurringScheduleCalculator.occurrenceKey(occurrence)
        ).joinToString("|")

    private data class RecurringKey(val userId: String, val recurringId: String)

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 5
    }
}
