package com.example.walletwise.presentation.reminder

import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.repository.ReminderRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.ReconcileReminderSchedulingUseCase
import com.example.walletwise.domain.service.ReminderDateTimeProvider
import com.example.walletwise.domain.service.ReminderLocalDate
import com.example.walletwise.domain.service.ReminderLocalDateTime
import com.example.walletwise.domain.service.ReminderLocalTime
import com.example.walletwise.domain.service.ReminderPermission
import com.example.walletwise.domain.service.ReminderPermissionGateway
import com.example.walletwise.domain.service.ReminderPermissionState
import com.example.walletwise.domain.service.ReminderPlatformScheduleResult
import com.example.walletwise.domain.service.ReminderPlatformScheduler
import com.example.walletwise.domain.service.ReminderScheduleRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSessionControllerTest {
    @Test
    fun usesOneCollectorAndSortsLikeFirestoreDocumentOrder() = runTest {
        val repository = SessionReminderRepository()
        val platform = SessionReminderPlatform()
        val controller = ReminderSessionController(this, repository, reconciler(platform))
        controller.setUserId("user")
        controller.setUserId("user")
        runCurrent()
        assertEquals(1, repository.activeCollectors)
        repository.emit(
            RepositoryResult.Success(
                listOf(reminder("z"), reminder("a"))
            )
        )
        runCurrent()
        assertEquals(listOf("a", "z"), controller.state.value.reminders.map { it.id })
        assertEquals(2, platform.scheduleCalls)

        repository.emit(RepositoryResult.Success(listOf(reminder("z"), reminder("a"))))
        runCurrent()
        assertEquals(2, platform.scheduleCalls)
        controller.close()
    }

    @Test
    fun changingUidAndLogoutCancelCollectorAlarmAndOldState() = runTest {
        val repository = SessionReminderRepository()
        val platform = SessionReminderPlatform()
        val controller = ReminderSessionController(this, repository, reconciler(platform))
        controller.setUserId("one")
        runCurrent()
        repository.emit(RepositoryResult.Success(listOf(reminder("old", "one"))))
        runCurrent()
        controller.setUserId("two")
        runCurrent()
        assertEquals(1, repository.activeCollectors)
        assertTrue("old" in platform.cancelled)
        assertEquals("two", controller.state.value.userId)
        assertTrue(controller.state.value.reminders.isEmpty())

        controller.setUserId(null)
        runCurrent()
        assertEquals(0, repository.activeCollectors)
        assertNull(controller.state.value.userId)
        assertTrue(controller.state.value.reminders.isEmpty())
        controller.close()
    }

    @Test
    fun repositoryErrorKeepsCurrentList() = runTest {
        val repository = SessionReminderRepository()
        val controller = ReminderSessionController(this, repository, reconciler(SessionReminderPlatform()))
        controller.setUserId("user")
        runCurrent()
        repository.emit(RepositoryResult.Success(listOf(reminder("id"))))
        runCurrent()
        repository.emit(RepositoryResult.Failure(RepositoryError(RepositoryErrorCode.NETWORK, "offline")))
        runCurrent()
        assertEquals(listOf("id"), controller.state.value.reminders.map { it.id })
        assertEquals("offline", controller.state.value.error?.message)
        controller.close()
    }

    private fun reconciler(platform: SessionReminderPlatform) = ReconcileReminderSchedulingUseCase(
        platform,
        platform,
        object : ReminderDateTimeProvider {
            override fun currentLocalDateTime() = ReminderLocalDateTime(
                ReminderLocalDate(2026, 9, 10), ReminderLocalTime(8, 0)
            )
        }
    )

    private fun reminder(id: String, userId: String = "user") = Reminder(
        id = id, userId = userId, title = "Title", note = "Note",
        frequency = "Hàng ngày", startDate = "10 thg 9, 2026", time = "20:15"
    )
}

private class SessionReminderRepository : ReminderRepository {
    private var sender: (RepositoryResult<List<Reminder>>) -> Unit = {}
    var activeCollectors = 0
    override fun observeReminders(userId: String): Flow<RepositoryResult<List<Reminder>>> = callbackFlow {
        activeCollectors++
        sender = { trySend(it) }
        awaitClose { activeCollectors-- }
    }
    fun emit(value: RepositoryResult<List<Reminder>>) = sender(value)
    override suspend fun getReminders(userId: String) = RepositoryResult.Success(emptyList<Reminder>())
    override suspend fun addReminder(userId: String, reminder: Reminder) = RepositoryResult.Success(Unit)
    override suspend fun updateReminder(userId: String, reminder: Reminder) = RepositoryResult.Success(Unit)
    override suspend fun deleteReminder(userId: String, reminderId: String) = RepositoryResult.Success(Unit)
    override suspend fun setReminderEnabled(userId: String, reminderId: String, isEnabled: Boolean) = RepositoryResult.Success(Unit)
}

private class SessionReminderPlatform : ReminderPlatformScheduler, ReminderPermissionGateway {
    var scheduleCalls = 0
    val cancelled = mutableListOf<String>()
    override suspend fun schedule(request: ReminderScheduleRequest): ReminderPlatformScheduleResult {
        scheduleCalls++
        return ReminderPlatformScheduleResult.Scheduled(true)
    }
    override suspend fun cancel(reminderId: String): ReminderPlatformScheduleResult {
        cancelled += reminderId
        return ReminderPlatformScheduleResult.Cancelled
    }
    override fun permissionState(permission: ReminderPermission) = ReminderPermissionState.GRANTED
}
