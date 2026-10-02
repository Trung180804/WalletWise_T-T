package com.example.walletwise.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSchedulingReliabilityTest {
    @Test
    fun `stable request codes and data URIs keep colliding ids distinct`() {
        // "FB" and "Ea" are a known String.hashCode collision.
        assertEquals(
            AndroidSchedulingContract.stableRequestCode("FB"),
            AndroidSchedulingContract.stableRequestCode("Ea")
        )
        assertNotEquals(
            AndroidSchedulingContract.reminderData("FB"),
            AndroidSchedulingContract.reminderData("Ea")
        )
        assertNotEquals(
            AndroidSchedulingContract.ACTION_REMINDER_ALARM,
            AndroidSchedulingContract.ACTION_RECURRING_ALARM
        )
        assertNotEquals(
            AndroidSchedulingContract.reminderData("item"),
            AndroidSchedulingContract.recurringData("item")
        )
        assertEquals(
            AndroidSchedulingContract.reminderData("stable id"),
            AndroidSchedulingContract.reminderData("stable id")
        )
    }

    @Test
    fun `retry delays are exactly one two four eight and sixteen minutes`() {
        assertEquals(
            listOf(1L, 2L, 4L, 8L, 16L).map { it * 60_000L },
            (1..5).map(AndroidSchedulingContract::retryDelayMillis)
        )
        assertFails { AndroidSchedulingContract.retryDelayMillis(0) }
        assertFails { AndroidSchedulingContract.retryDelayMillis(6) }
    }

    @Test
    fun `exact alarm is used when allowed and denial selects inexact`() {
        var exactCalls = 0
        var inexactCalls = 0
        val exact = dispatchAlarm(
            canUseExact = AndroidSchedulingContract.shouldUseExactAlarm(34, 31, true),
            setExact = { exactCalls++ },
            setInexact = { inexactCalls++ }
        )
        assertEquals(true, exact.exact)
        assertEquals(1, exactCalls)
        assertEquals(0, inexactCalls)

        val inexact = dispatchAlarm(
            canUseExact = AndroidSchedulingContract.shouldUseExactAlarm(34, 31, false),
            setExact = { exactCalls++ },
            setInexact = { inexactCalls++ }
        )
        assertEquals(false, inexact.exact)
        assertEquals(1, exactCalls)
        assertEquals(1, inexactCalls)
    }

    @Test
    fun `security failure from exact alarm falls back once and reports inexact`() {
        var inexactCalls = 0
        val outcome = dispatchAlarm(
            canUseExact = true,
            setExact = { throw SecurityException("denied") },
            setInexact = { inexactCalls++ }
        )
        assertEquals(false, outcome.exact)
        assertEquals(1, inexactCalls)
        assertNull(outcome.error)

        val failure = IllegalStateException("alarm service unavailable")
        val failedOutcome = dispatchAlarm(
            canUseExact = false,
            setExact = {},
            setInexact = { throw failure }
        )
        assertNull(failedOutcome.exact)
        assertSame(failure, failedOutcome.error)
    }

    @Test
    fun `security failure from an inexact alarm is returned without retrying`() {
        var inexactCalls = 0
        val failure = SecurityException("alarm denied")

        val outcome = dispatchAlarm(
            canUseExact = false,
            setExact = {},
            setInexact = {
                inexactCalls++
                throw failure
            }
        )

        assertEquals(1, inexactCalls)
        assertNull(outcome.exact)
        assertSame(failure, outcome.error)
    }

    @Test
    fun `notification denial is a no-op on permission guarded Android versions`() {
        assertTrue(AndroidSchedulingContract.shouldPostNotification(32, 33, false))
        assertFalse(AndroidSchedulingContract.shouldPostNotification(33, 33, false))
        assertTrue(AndroidSchedulingContract.shouldPostNotification(36, 33, true))
    }

    @Test
    fun `broadcast async work always completes pending result`() = runBlocking {
        var finishes = 0
        runBroadcastWork(finish = { finishes++ }) {}
        assertEquals(1, finishes)

        assertFails {
            runBlocking {
                runBroadcastWork(finish = { finishes++ }) {
                    error("receiver failed")
                }
            }
        }
        assertEquals(2, finishes)
    }

    @Test
    fun `every supported system action requests one force reconcile`() {
        val expected = setOf(
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.TIME_SET",
            "android.intent.action.TIMEZONE_CHANGED",
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
        assertEquals(expected, AndroidSchedulingContract.forceReconcileActions)
        expected.forEach { action ->
            assertTrue(AndroidSchedulingContract.isForceReconcileAction(action))
        }
        assertFalse(AndroidSchedulingContract.isForceReconcileAction("unexpected"))
        assertFalse(AndroidSchedulingContract.isForceReconcileAction(null))
    }

    private fun assertFails(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: Throwable) {
            failed = true
        }
        assertTrue(failed)
    }
}
