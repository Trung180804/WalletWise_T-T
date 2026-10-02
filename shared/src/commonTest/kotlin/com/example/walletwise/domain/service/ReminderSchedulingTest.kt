package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.Reminder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderSchedulingTest {
    @Test
    fun updateCancelsOldAlarmThenSchedulesNewAndSnapshotReplayIsIdempotent() = runTest {
        val platform = FakeReminderPlatform()
        val useCase = useCase(platform)
        val reminder = reminder()

        assertEquals(ReminderSchedulingStatus.COMPLETE, useCase.apply(reminder).status)
        assertEquals(listOf("cancel:id", "schedule:id@2026-9-10T20:15"), platform.actions)

        platform.actions.clear()
        assertEquals(ReminderSchedulingStatus.NO_CHANGE, useCase.apply(reminder).status)
        assertTrue(platform.actions.isEmpty())

        assertEquals(ReminderSchedulingStatus.COMPLETE, useCase.apply(reminder.copy(time = "21:00")).status)
        assertEquals(listOf("cancel:id", "schedule:id@2026-9-10T21:0"), platform.actions)
    }

    @Test
    fun disableDeleteAndRemovedSnapshotCancelAlarm() = runTest {
        val platform = FakeReminderPlatform()
        val useCase = useCase(platform)
        useCase.apply(reminder())
        platform.actions.clear()

        useCase.apply(reminder().copy(isEnabled = false))
        assertEquals(listOf("cancel:id"), platform.actions)
        platform.actions.clear()

        useCase.cancel("user", "id")
        assertEquals(listOf("cancel:id"), platform.actions)
        useCase.apply(reminder())
        platform.actions.clear()
        useCase.reconcile("user", emptyList())
        assertEquals(listOf("cancel:id"), platform.actions)
    }

    @Test
    fun permissionDenialIsReportedWithoutClaimingCompleteSuccess() = runTest {
        val platform = FakeReminderPlatform(exact = false).apply {
            notificationPermission = ReminderPermissionState.DENIED
            exactPermission = ReminderPermissionState.DENIED
        }
        val outcome = useCase(platform).apply(reminder())
        assertEquals(ReminderSchedulingStatus.PERMISSION_REQUIRED, outcome.status)
        assertEquals(setOf(ReminderPermission.NOTIFICATIONS, ReminderPermission.EXACT_ALARM), outcome.permissions)
        assertEquals(1, platform.scheduleCalls)
    }

    @Test
    fun scheduleFailureIsRetriedByReconcile() = runTest {
        val platform = FakeReminderPlatform().apply { failSchedule = true }
        val useCase = useCase(platform)
        assertEquals(ReminderSchedulingStatus.FAILED, useCase.apply(reminder()).status)
        assertEquals(1, platform.scheduleCalls)
        assertEquals(ReminderSchedulingStatus.FAILED, useCase.apply(reminder()).status)
        assertEquals(2, platform.scheduleCalls)
    }

    @Test
    fun forceReconcileReschedulesForRebootOrTimezoneChange() = runTest {
        val platform = FakeReminderPlatform()
        val useCase = useCase(platform)
        useCase.reconcile("user", listOf(reminder()))
        platform.actions.clear()
        useCase.reconcile("user", listOf(reminder()), force = true)
        assertEquals(listOf("cancel:id", "schedule:id@2026-9-10T20:15"), platform.actions)
    }

    @Test
    fun clearUserCancelsKnownAlarms() = runTest {
        val platform = FakeReminderPlatform()
        val useCase = useCase(platform)
        useCase.apply(reminder())
        platform.actions.clear()
        val report = useCase.clearUser("user", listOf(reminder()))
        assertFalse(report.hasFailure)
        assertEquals(listOf("cancel:id"), platform.actions)
    }

    private fun useCase(platform: FakeReminderPlatform) = ReconcileReminderSchedulingUseCase(
        platform,
        platform,
        object : ReminderDateTimeProvider {
            override fun currentLocalDateTime() = ReminderLocalDateTime(
                ReminderLocalDate(2026, 9, 10), ReminderLocalTime(8, 0)
            )
        }
    )

    private fun reminder() = Reminder(
        id = "id", userId = "user", title = "Title", note = "Note",
        frequency = "Hàng ngày", startDate = "9 thg 9, 2026", time = "20:15"
    )
}

private class FakeReminderPlatform(
    private val exact: Boolean = true
) : ReminderPlatformScheduler, ReminderPermissionGateway {
    val actions = mutableListOf<String>()
    var scheduleCalls = 0
    var failSchedule = false
    var failCancel = false
    var notificationPermission = ReminderPermissionState.GRANTED
    var exactPermission = ReminderPermissionState.GRANTED

    override suspend fun schedule(request: ReminderScheduleRequest): ReminderPlatformScheduleResult {
        scheduleCalls++
        val at = request.occurrence.at
        actions += "schedule:${request.reminder.id}@${at.date.year}-${at.date.month}-${at.date.dayOfMonth}T${at.time.hour}:${at.time.minute}"
        return if (failSchedule) ReminderPlatformScheduleResult.Failure("schedule failed")
        else ReminderPlatformScheduleResult.Scheduled(exact)
    }

    override suspend fun cancel(reminderId: String): ReminderPlatformScheduleResult {
        actions += "cancel:$reminderId"
        return if (failCancel) ReminderPlatformScheduleResult.Failure("cancel failed")
        else ReminderPlatformScheduleResult.Cancelled
    }

    override fun permissionState(permission: ReminderPermission): ReminderPermissionState = when (permission) {
        ReminderPermission.NOTIFICATIONS -> notificationPermission
        ReminderPermission.EXACT_ALARM -> exactPermission
    }
}
