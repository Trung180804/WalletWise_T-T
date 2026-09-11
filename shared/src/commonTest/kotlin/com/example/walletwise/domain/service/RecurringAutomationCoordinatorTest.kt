package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.RECURRING_FREQUENCY_DAILY
import com.example.walletwise.domain.model.RECURRING_TIMES_UNLIMITED
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.usecase.ExecuteRecurringIfDueUseCase
import com.example.walletwise.presentation.recurring.FakeRecurringDateTimeProvider
import com.example.walletwise.presentation.recurring.FakeRecurringPlatform
import com.example.walletwise.presentation.recurring.FakeRecurringRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecurringAutomationCoordinatorTest {
    @Test
    fun repeatedSnapshotDoesNotRegisterTheSameAlarmTwiceButScheduleChangesReplaceIt() = runTest {
        val fixture = fixture(now = dateTime(2026, 9, 5, 7))
        val initial = rule(time = "08:00")
        fixture.repository.rules[initial.id] = initial

        fixture.coordinator.reconcile("user", listOf(initial))
        fixture.coordinator.reconcile("user", listOf(initial))
        assertEquals(1, fixture.platform.schedules.size)

        val updated = initial.copy(time = "09:00")
        fixture.repository.rules[initial.id] = updated
        fixture.coordinator.reconcile("user", listOf(updated))

        assertEquals(2, fixture.platform.schedules.size)
        assertEquals(2, fixture.platform.cancellations.count { it == initial.id })
    }

    @Test
    fun disableDeleteAndLogoutCancelTheStableRecurringId() = runTest {
        val fixture = fixture(now = dateTime(2026, 9, 5, 7))
        val enabled = rule()
        fixture.repository.rules[enabled.id] = enabled
        fixture.coordinator.reconcile("user", listOf(enabled))

        val disabled = enabled.copy(isEnabled = false)
        fixture.repository.rules[enabled.id] = disabled
        fixture.coordinator.reconcile("user", listOf(disabled))
        fixture.coordinator.reconcile("user", emptyList())
        fixture.coordinator.clearUser("user", listOf(disabled))

        assertTrue(fixture.platform.cancellations.all { it == enabled.id })
        assertTrue(fixture.platform.cancellations.size >= 3)
    }

    @Test
    fun receiverAndAppReconcileRacingTheSameOccurrenceCreateOneTransaction() = runTest {
        val fixture = fixture(now = dateTime(2026, 9, 5, 8))
        val due = rule(timesCount = RECURRING_TIMES_UNLIMITED)
        fixture.repository.rules[due.id] = due

        val alarm = async { fixture.coordinator.process("user", due.id) }
        val app = async { fixture.coordinator.reconcile("user", listOf(due)) }
        alarm.await()
        app.await()

        assertEquals(setOf("rule_2026-09-05"), fixture.repository.transactionIds)
        assertEquals(1, fixture.platform.notifications.size)
    }

    @Test
    fun independentCoordinatorsShareTheDurableDuplicateGuardInsteadOfTheirProcessMutex() = runTest {
        val repository = FakeRecurringRepository()
        val provider = FakeRecurringDateTimeProvider(dateTime(2026, 9, 5, 8))
        val platform = FakeRecurringPlatform()
        val due = rule(timesCount = RECURRING_TIMES_UNLIMITED)
        repository.rules[due.id] = due
        val coordinators = List(12) {
            RecurringAutomationCoordinator(
                ExecuteRecurringIfDueUseCase(repository, provider),
                platform,
                platform,
                provider
            )
        }

        coordinators.map { coordinator ->
            async { coordinator.process("user", due.id) }
        }.forEach { it.await() }

        assertEquals(setOf("rule_2026-09-05"), repository.transactionIds)
        assertEquals("2026-09-05", repository.rules.getValue(due.id).lastExecutedDate)
        assertEquals(1, platform.notifications.size)
    }

    @Test
    fun committedWriteWithLostResponseRetriesWithoutCreatingASecondTransaction() = runTest {
        val fixture = fixture(now = dateTime(2026, 9, 5, 8))
        val due = rule()
        fixture.repository.rules[due.id] = due
        fixture.repository.responseFailuresAfterCommit = 1

        val ambiguous = fixture.coordinator.process("user", due.id)
        val retry = fixture.coordinator.process("user", due.id, retryAttempt = 1)

        assertEquals(RecurringProcessingStatus.RETRY_SCHEDULED, ambiguous.status)
        assertEquals(RecurringProcessingStatus.COMPLETE, retry.status)
        assertEquals(setOf("rule_2026-09-05"), fixture.repository.transactionIds)
        assertEquals("2026-09-05", fixture.repository.rules.getValue(due.id).lastExecutedDate)
    }

    @Test
    fun failureBeforeAtomicCommitChangesNeitherMarkerNorTransactionAndSchedulesBoundedRetry() = runTest {
        val fixture = fixture(now = dateTime(2026, 9, 5, 8))
        val due = rule()
        fixture.repository.rules[due.id] = due
        fixture.repository.executeFailuresBeforeCommit = 1

        val failed = fixture.coordinator.process("user", due.id)

        assertEquals(RecurringProcessingStatus.RETRY_SCHEDULED, failed.status)
        assertTrue(fixture.repository.transactionIds.isEmpty())
        assertEquals("", fixture.repository.rules.getValue(due.id).lastExecutedDate)
        assertEquals(listOf(Triple("user", "rule", 1)), fixture.platform.retries)
    }

    @Test
    fun existingDeterministicTransactionRepairsOldMarkerWithoutNewWriteOrNotification() = runTest {
        val fixture = fixture(now = dateTime(2026, 9, 5, 8))
        val due = rule()
        fixture.repository.rules[due.id] = due
        fixture.repository.transactionIds += "rule_2026-09-05"

        val outcome = fixture.coordinator.process("user", due.id)

        assertEquals(RecurringProcessingStatus.COMPLETE, outcome.status)
        assertEquals(false, outcome.execution?.transactionWasCreated)
        assertEquals(setOf("rule_2026-09-05"), fixture.repository.transactionIds)
        assertEquals("2026-09-05", fixture.repository.rules.getValue(due.id).lastExecutedDate)
        assertTrue(fixture.platform.notifications.isEmpty())
    }

    @Test
    fun finalFiniteOccurrenceDisablesRuleAndACompletedRetryDoesNotNotifyTwice() = runTest {
        val fixture = fixture(now = dateTime(2026, 9, 5, 8))
        val due = rule(timesCount = "1")
        fixture.repository.rules[due.id] = due

        val first = fixture.coordinator.process("user", due.id)
        val retry = fixture.coordinator.process("user", due.id, retryAttempt = 1)

        assertEquals(true, first.execution?.transactionWasCreated)
        assertEquals(false, retry.execution?.transactionWasCreated)
        assertFalse(fixture.repository.rules.getValue(due.id).isEnabled)
        assertEquals("2026-09-05", fixture.repository.rules.getValue(due.id).lastExecutedDate)
        assertEquals(setOf("rule_2026-09-05"), fixture.repository.transactionIds)
        assertEquals(1, fixture.platform.notifications.size)
    }

    @Test
    fun retryStopsAfterFiveAttempts() = runTest {
        val fixture = fixture(now = dateTime(2026, 9, 5, 8))
        val due = rule()
        fixture.repository.rules[due.id] = due
        fixture.repository.executeFailuresBeforeCommit = 1

        val outcome = fixture.coordinator.process("user", due.id, retryAttempt = 5)

        assertEquals(RecurringProcessingStatus.FAILED, outcome.status)
        assertTrue(fixture.platform.retries.isEmpty())
    }

    @Test
    fun notificationFailureAfterCommitDoesNotPreventTheNextSchedule() = runTest {
        val fixture = fixture(now = dateTime(2026, 9, 5, 8))
        val due = rule()
        fixture.repository.rules[due.id] = due
        fixture.platform.notificationFailure = true

        val outcome = fixture.coordinator.process("user", due.id)

        assertEquals("notification failed", outcome.notificationError)
        assertEquals(1, fixture.platform.schedules.size)
        assertEquals(setOf("rule_2026-09-05"), fixture.repository.transactionIds)
    }

    @Test
    fun wrongUidAndFutureOccurrenceNeverWriteATransaction() = runTest {
        val fixture = fixture(now = dateTime(2026, 9, 5, 7))
        val rule = rule()
        fixture.repository.rules[rule.id] = rule

        fixture.coordinator.process("other-user", rule.id)
        fixture.coordinator.process("user", rule.id)

        assertTrue(fixture.repository.transactionIds.isEmpty())
        assertFalse(fixture.repository.rules.getValue(rule.id).lastExecutedDate.isNotEmpty())
    }

    private fun fixture(now: ReminderLocalDateTime): Fixture {
        val repository = FakeRecurringRepository()
        val provider = FakeRecurringDateTimeProvider(now)
        val platform = FakeRecurringPlatform()
        val coordinator = RecurringAutomationCoordinator(
            ExecuteRecurringIfDueUseCase(repository, provider),
            platform,
            platform,
            provider
        )
        return Fixture(repository, platform, coordinator)
    }

    private data class Fixture(
        val repository: FakeRecurringRepository,
        val platform: FakeRecurringPlatform,
        val coordinator: RecurringAutomationCoordinator
    )

    private fun rule(
        time: String = "08:00",
        timesCount: String = RECURRING_TIMES_UNLIMITED
    ) = RecurringTransaction(
        id = "rule",
        userId = "user",
        title = "Rent",
        amount = 100.0,
        type = "Chi",
        category = "Hóa đơn",
        paymentMethod = "Tiền mặt",
        frequency = RECURRING_FREQUENCY_DAILY,
        timesCount = timesCount,
        startDate = "5 thg 9, 2026",
        time = time
    )

    private fun dateTime(year: Int, month: Int, day: Int, hour: Int) =
        ReminderLocalDateTime(ReminderLocalDate(year, month, day), ReminderLocalTime(hour, 0))
}
