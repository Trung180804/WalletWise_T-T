package com.example.walletwise.utils

import com.example.walletwise.domain.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderScheduleTest {

    @Test
    fun `daily reminder keeps today's future time`() {
        val reminder = reminder(frequency = "Hàng ngày")

        val next = ReminderSchedule.nextOccurrence(
            reminder,
            LocalDateTime.of(2026, 9, 5, 7, 59)
        )

        assertEquals(LocalDateTime.of(2026, 9, 5, 8, 0), next?.at)
    }

    @Test
    fun `daily reminder moves to tomorrow after firing`() {
        val reminder = reminder(frequency = "Hàng ngày")

        val next = ReminderSchedule.nextOccurrence(
            reminder,
            LocalDateTime.of(2026, 9, 5, 8, 0)
        )

        assertEquals(LocalDateTime.of(2026, 9, 6, 8, 0), next?.at)
    }

    @Test
    fun `weekly reminder preserves weekday and time`() {
        val reminder = reminder(frequency = "Hàng tuần")

        val next = ReminderSchedule.nextOccurrence(
            reminder,
            LocalDateTime.of(2026, 9, 6, 9, 0)
        )

        assertEquals(LocalDateTime.of(2026, 9, 12, 8, 0), next?.at)
    }

    private fun reminder(frequency: String) = Reminder(
        id = "reminder",
        userId = "user",
        title = "Ghi chép chi tiêu",
        frequency = frequency,
        startDate = "5 thg 9, 2026",
        time = "08:00"
    )
}
