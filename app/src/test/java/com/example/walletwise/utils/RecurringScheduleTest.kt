package com.example.walletwise.utils

import com.example.walletwise.domain.model.RecurringTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RecurringScheduleTest {

    @Test
    fun `daily rule is due only after its configured time`() {
        val rule = recurring(frequency = "Hàng ngày")

        assertNull(RecurringSchedule.latestDueOccurrence(rule, LocalDateTime.of(2026, 9, 5, 7, 59)))
        val due = RecurringSchedule.latestDueOccurrence(rule, LocalDateTime.of(2026, 9, 5, 8, 0))

        assertEquals(0L, due?.index)
        assertEquals("2026-09-05", due?.let(RecurringSchedule::occurrenceKey))
    }

    @Test
    fun `rent transaction scheduled at 23 07 is due at 23 08`() {
        val rent = RecurringTransaction(
            id = "rent",
            userId = "user",
            title = "Tiền nhà",
            amount = 2_000_000.0,
            frequency = "Hàng tháng",
            timesCount = "1",
            startDate = "7 thg 9, 2026",
            time = "23:07"
        )

        val due = RecurringSchedule.latestDueOccurrence(
            rent,
            LocalDateTime.of(2026, 9, 7, 23, 8)
        )

        assertEquals(0L, due?.index)
        assertEquals("2026-09-07", due?.let(RecurringSchedule::occurrenceKey))
    }

    @Test
    fun `next occurrence keeps today's future scheduled time`() {
        val rule = recurring(frequency = "Hàng ngày")

        val next = RecurringSchedule.nextOccurrence(rule, LocalDateTime.of(2026, 9, 5, 7, 30))

        assertEquals(LocalDateTime.of(2026, 9, 5, 8, 0), next?.at)
    }

    @Test
    fun `daily rule recovers yesterday before today's configured time`() {
        val rule = recurring(frequency = "Hàng ngày")

        val due = RecurringSchedule.latestDueOccurrence(
            rule,
            LocalDateTime.of(2026, 9, 6, 7, 59)
        )

        assertEquals(LocalDateTime.of(2026, 9, 5, 8, 0), due?.at)
    }

    @Test
    fun `weekly rule recovers prior week before this week's configured time`() {
        val rule = recurring(frequency = "Hàng tuần")

        val due = RecurringSchedule.latestDueOccurrence(
            rule,
            LocalDateTime.of(2026, 9, 12, 7, 59)
        )

        assertEquals(LocalDateTime.of(2026, 9, 5, 8, 0), due?.at)
    }

    @Test
    fun `weekly mojibake label still schedules weekly for existing data`() {
        val rule = recurring(frequency = "HÃ ng tuáº§n")

        val due = RecurringSchedule.latestDueOccurrence(rule, LocalDateTime.of(2026, 9, 19, 9, 0))

        assertEquals(2L, due?.index)
    }

    @Test
    fun `finite number of runs stops scheduling after its limit`() {
        val rule = recurring(timesCount = "2")

        val secondRun = RecurringSchedule.latestDueOccurrence(rule, LocalDateTime.of(2026, 9, 6, 9, 0))
        val afterSecondRun = RecurringSchedule.nextOccurrence(rule, LocalDateTime.of(2026, 9, 6, 9, 0))

        assertEquals(1L, secondRun?.index)
        assertTrue(RecurringSchedule.hasReachedExecutionLimit(rule, secondRun!!.index))
        assertNull(afterSecondRun)
    }

    @Test
    fun `finite rule recovers its last allowed occurrence after phone was offline`() {
        val rule = recurring(timesCount = "1")

        val due = RecurringSchedule.latestDueOccurrence(
            rule,
            LocalDateTime.of(2026, 9, 8, 9, 0)
        )

        assertEquals(0L, due?.index)
        assertEquals(LocalDateTime.of(2026, 9, 5, 8, 0), due?.at)
    }

    @Test
    fun `alarm selection keeps an occurrence that became due but is not processed`() {
        val rule = recurring(timesCount = "1")

        val pending = RecurringSchedule.nextUnprocessedOccurrence(
            rule,
            LocalDateTime.of(2026, 9, 5, 8, 0, 1)
        )

        assertEquals(LocalDateTime.of(2026, 9, 5, 8, 0), pending?.at)
    }

    @Test
    fun `unlimited rule does not have an execution limit`() {
        val rule = recurring(timesCount = "Khác")

        assertFalse(RecurringSchedule.hasReachedExecutionLimit(rule, 100))
    }

    private fun recurring(
        frequency: String = "Hàng ngày",
        timesCount: String = "Khác"
    ) = RecurringTransaction(
        id = "rent",
        userId = "user",
        title = "Rent",
        amount = 1.0,
        frequency = frequency,
        timesCount = timesCount,
        startDate = "5 thg 9, 2026",
        time = "08:00"
    )
}
