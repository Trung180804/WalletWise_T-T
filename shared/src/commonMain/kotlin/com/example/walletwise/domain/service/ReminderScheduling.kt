package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.Reminder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ReminderPermission { NOTIFICATIONS, EXACT_ALARM }

enum class ReminderPermissionState { GRANTED, DENIED, NOT_REQUIRED }

interface ReminderPermissionGateway {
    fun permissionState(permission: ReminderPermission): ReminderPermissionState
}

data class ReminderScheduleRequest(
    val reminder: Reminder,
    val occurrence: ReminderOccurrence
)

sealed interface ReminderPlatformScheduleResult {
    data class Scheduled(val exact: Boolean) : ReminderPlatformScheduleResult
    data object Cancelled : ReminderPlatformScheduleResult
    data class Failure(val message: String) : ReminderPlatformScheduleResult
}

interface ReminderPlatformScheduler {
    suspend fun schedule(request: ReminderScheduleRequest): ReminderPlatformScheduleResult
    suspend fun cancel(reminderId: String): ReminderPlatformScheduleResult
}

enum class ReminderSchedulingStatus { COMPLETE, PERMISSION_REQUIRED, FAILED, NO_CHANGE }

data class ReminderSchedulingOutcome(
    val status: ReminderSchedulingStatus,
    val permissions: Set<ReminderPermission> = emptySet(),
    val errorMessage: String? = null
)

data class ReminderReconcileReport(
    val outcomes: List<ReminderSchedulingOutcome> = emptyList()
) {
    val permissions: Set<ReminderPermission> = outcomes.flatMapTo(mutableSetOf()) { it.permissions }
    val errors: List<String> = outcomes.mapNotNull { it.errorMessage }
    val hasFailure: Boolean get() = errors.isNotEmpty()
}

/**
 * Applies Firestore reminder state to the platform scheduler. Local wall-clock
 * values are resolved in the device timezone only by the platform adapter;
 * forcing reconciliation after timezone/DST changes recalculates the instant.
 */
class ReconcileReminderSchedulingUseCase(
    private val scheduler: ReminderPlatformScheduler,
    private val permissionGateway: ReminderPermissionGateway,
    private val dateTimeProvider: ReminderDateTimeProvider
) {
    private val mutex = Mutex()
    private val appliedSignatures = mutableMapOf<ReminderKey, String>()

    suspend fun apply(reminder: Reminder, force: Boolean = false): ReminderSchedulingOutcome =
        mutex.withLock { applyLocked(reminder, force) }

    suspend fun cancel(userId: String, reminderId: String): ReminderSchedulingOutcome = mutex.withLock {
        cancelLocked(ReminderKey(userId, reminderId))
    }

    suspend fun reconcile(
        userId: String,
        reminders: List<Reminder>,
        force: Boolean = false
    ): ReminderReconcileReport = mutex.withLock {
        val currentIds = reminders.mapTo(mutableSetOf()) { it.id }
        val removed = appliedSignatures.keys.filter { it.userId == userId && it.reminderId !in currentIds }
        val outcomes = mutableListOf<ReminderSchedulingOutcome>()
        removed.forEach { outcomes += cancelLocked(it) }
        reminders.forEach { outcomes += applyLocked(it.copy(userId = userId), force) }
        ReminderReconcileReport(outcomes)
    }

    suspend fun clearUser(userId: String, reminders: List<Reminder>): ReminderReconcileReport = mutex.withLock {
        val keys = buildSet {
            reminders.forEach { add(ReminderKey(userId, it.id)) }
            appliedSignatures.keys.filterTo(this) { it.userId == userId }
        }
        ReminderReconcileReport(keys.map { cancelLocked(it) })
    }

    private suspend fun applyLocked(reminder: Reminder, force: Boolean): ReminderSchedulingOutcome {
        if (reminder.id.isBlank() || reminder.userId.isBlank()) {
            return ReminderSchedulingOutcome(
                ReminderSchedulingStatus.FAILED,
                errorMessage = "Reminder id and user id are required"
            )
        }
        val key = ReminderKey(reminder.userId, reminder.id)
        if (!reminder.isEnabled) {
            val signature = "disabled"
            if (!force && appliedSignatures[key] == signature) {
                return ReminderSchedulingOutcome(ReminderSchedulingStatus.NO_CHANGE)
            }
            val cancelled = scheduler.cancel(reminder.id)
            return if (cancelled is ReminderPlatformScheduleResult.Failure) {
                appliedSignatures.remove(key)
                ReminderSchedulingOutcome(ReminderSchedulingStatus.FAILED, errorMessage = cancelled.message)
            } else {
                appliedSignatures[key] = signature
                ReminderSchedulingOutcome(ReminderSchedulingStatus.COMPLETE)
            }
        }

        val occurrence = ReminderScheduleCalculator.nextOccurrence(
            reminder,
            dateTimeProvider.currentLocalDateTime()
        )
        if (occurrence == null) {
            scheduler.cancel(reminder.id)
            appliedSignatures.remove(key)
            return ReminderSchedulingOutcome(
                ReminderSchedulingStatus.FAILED,
                errorMessage = "Reminder schedule is invalid"
            )
        }
        val signature = scheduleSignature(reminder, occurrence)
        if (!force && appliedSignatures[key] == signature) {
            return ReminderSchedulingOutcome(ReminderSchedulingStatus.NO_CHANGE)
        }
        val cancelled = scheduler.cancel(reminder.id)
        if (cancelled is ReminderPlatformScheduleResult.Failure) {
            appliedSignatures.remove(key)
            return ReminderSchedulingOutcome(ReminderSchedulingStatus.FAILED, errorMessage = cancelled.message)
        }
        return when (val result = scheduler.schedule(ReminderScheduleRequest(reminder, occurrence))) {
            is ReminderPlatformScheduleResult.Failure -> {
                appliedSignatures.remove(key)
                ReminderSchedulingOutcome(ReminderSchedulingStatus.FAILED, errorMessage = result.message)
            }
            is ReminderPlatformScheduleResult.Scheduled -> {
                appliedSignatures[key] = signature
                val permissions = buildSet {
                    if (permissionGateway.permissionState(ReminderPermission.NOTIFICATIONS) == ReminderPermissionState.DENIED) {
                        add(ReminderPermission.NOTIFICATIONS)
                    }
                    if (!result.exact &&
                        permissionGateway.permissionState(ReminderPermission.EXACT_ALARM) == ReminderPermissionState.DENIED
                    ) {
                        add(ReminderPermission.EXACT_ALARM)
                    }
                }
                ReminderSchedulingOutcome(
                    status = if (permissions.isEmpty()) {
                        ReminderSchedulingStatus.COMPLETE
                    } else {
                        ReminderSchedulingStatus.PERMISSION_REQUIRED
                    },
                    permissions = permissions
                )
            }
            ReminderPlatformScheduleResult.Cancelled -> {
                appliedSignatures.remove(key)
                ReminderSchedulingOutcome(
                    ReminderSchedulingStatus.FAILED,
                    errorMessage = "Enabled reminder was not scheduled"
                )
            }
        }
    }

    private suspend fun cancelLocked(key: ReminderKey): ReminderSchedulingOutcome {
        return when (val result = scheduler.cancel(key.reminderId)) {
            is ReminderPlatformScheduleResult.Failure ->
                ReminderSchedulingOutcome(ReminderSchedulingStatus.FAILED, errorMessage = result.message)
            else -> {
                appliedSignatures.remove(key)
                ReminderSchedulingOutcome(ReminderSchedulingStatus.COMPLETE)
            }
        }
    }

    private fun scheduleSignature(reminder: Reminder, occurrence: ReminderOccurrence): String = listOf(
        reminder.id,
        reminder.userId,
        reminder.title,
        reminder.frequency,
        reminder.startDate,
        reminder.time,
        reminder.note,
        reminder.isEnabled.toString(),
        occurrence.index.toString(),
        occurrence.at.date.year.toString(),
        occurrence.at.date.month.toString(),
        occurrence.at.date.dayOfMonth.toString(),
        occurrence.at.time.hour.toString(),
        occurrence.at.time.minute.toString()
    ).joinToString("|")

    private data class ReminderKey(val userId: String, val reminderId: String)
}
