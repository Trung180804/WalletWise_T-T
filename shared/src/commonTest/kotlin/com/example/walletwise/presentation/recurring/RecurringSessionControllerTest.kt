package com.example.walletwise.presentation.recurring

import com.example.walletwise.domain.model.RECURRING_FREQUENCY_DAILY
import com.example.walletwise.domain.model.RECURRING_TIMES_UNLIMITED
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.service.RecurringAutomationCoordinator
import com.example.walletwise.domain.usecase.ExecuteRecurringIfDueUseCase
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecurringSessionControllerTest {
    @Test
    fun sameUidOwnsOneListenerAndUidChangeOrLogoutCancelsThePreviousListener() = runTest {
        val repository = FakeRecurringRepository()
        val provider = FakeRecurringDateTimeProvider()
        val platform = FakeRecurringPlatform()
        val controller = RecurringSessionController(
            backgroundScope,
            repository,
            RecurringAutomationCoordinator(
                ExecuteRecurringIfDueUseCase(repository, provider),
                platform,
                platform,
                provider
            )
        )

        controller.setUserId("user")
        runCurrent()
        controller.setUserId("user")
        runCurrent()
        assertEquals(1, repository.observeCalls)
        assertEquals(1, repository.activeCollectors)

        repository.sessionUserId = "second"
        controller.setUserId("second")
        repository.emit("second", listOf(rule("second")))
        runCurrent()
        assertEquals(2, repository.observeCalls)
        assertEquals(1, repository.activeCollectors)
        assertEquals("second", controller.state.value.userId)

        controller.setUserId(null)
        runCurrent()
        assertNull(controller.state.value.userId)
        assertTrue(controller.state.value.recurring.isEmpty())
        assertEquals(0, repository.activeCollectors)
        assertEquals(1, repository.maxActiveCollectors)
    }

    @Test
    fun repositoryFailureIsExposedWithoutDiscardingSessionIdentity() = runTest {
        val repository = FakeRecurringRepository()
        val provider = FakeRecurringDateTimeProvider()
        val platform = FakeRecurringPlatform()
        val controller = RecurringSessionController(
            backgroundScope,
            repository,
            RecurringAutomationCoordinator(ExecuteRecurringIfDueUseCase(repository, provider), platform, platform, provider)
        )
        controller.setUserId("user")
        runCurrent()
        repository.emitFailure("user")
        runCurrent()

        assertEquals("user", controller.state.value.userId)
        assertEquals(fakeError, controller.state.value.error)
    }

    private fun rule(userId: String) = RecurringTransaction(
        id = "rule",
        userId = userId,
        title = "Rule",
        amount = 1.0,
        frequency = RECURRING_FREQUENCY_DAILY,
        timesCount = RECURRING_TIMES_UNLIMITED,
        startDate = "6 thg 9, 2026",
        time = "08:00"
    )
}
