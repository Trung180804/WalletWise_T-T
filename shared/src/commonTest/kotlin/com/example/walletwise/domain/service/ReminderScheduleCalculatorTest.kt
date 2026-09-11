package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.Reminder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReminderScheduleCalculatorTest {
    @Test
    fun parsesExistingDateAndTimeWireFormats() {
        assertEquals(ReminderLocalDate(2026, 9, 7), ReminderScheduleCalculator.parseStartDate("7 thg 9, 2026"))
        assertEquals(ReminderLocalDate(2026, 9, 7), ReminderScheduleCalculator.parseStartDate("07/09/2026"))
        assertEquals(ReminderLocalDate(2026, 9, 7), ReminderScheduleCalculator.parseStartDate("2026-09-07"))
        assertEquals(ReminderLocalTime(8, 5), ReminderScheduleCalculator.parseTime("08:05"))
        assertNull(ReminderScheduleCalculator.parseStartDate("31 thg 2, 2026"))
        assertNull(ReminderScheduleCalculator.parseTime("24:00"))
    }

    @Test
    fun dailyIsStrictlyFutureAndMovesAcrossYear() {
        val reminder = reminder("Hàng ngày", "31 thg 12, 2026", "08:00")
        assertEquals(
            ReminderLocalDateTime(ReminderLocalDate(2026, 12, 31), ReminderLocalTime(8, 0)),
            ReminderScheduleCalculator.nextOccurrence(reminder, at(2026, 12, 31, 7, 59))?.at
        )
        assertEquals(
            ReminderLocalDateTime(ReminderLocalDate(2027, 1, 1), ReminderLocalTime(8, 0)),
            ReminderScheduleCalculator.nextOccurrence(reminder, at(2026, 12, 31, 8, 0))?.at
        )
    }

    @Test
    fun weeklyPreservesWeekdayAndTime() {
        val reminder = reminder("Hàng tuần", "5 thg 9, 2026", "08:00")
        assertEquals(
            at(2026, 9, 12, 8, 0),
            ReminderScheduleCalculator.nextOccurrence(reminder, at(2026, 9, 6, 9, 0))?.at
        )
    }

    @Test
    fun monthlyKeepsOriginalAnchorAndClampsMissingDays() {
        val reminder = reminder("Hàng tháng", "31 thg 1, 2024", "08:00")
        assertEquals(
            at(2024, 2, 29, 8, 0),
            ReminderScheduleCalculator.nextOccurrence(reminder, at(2024, 1, 31, 8, 0))?.at
        )
        assertEquals(
            at(2024, 3, 31, 8, 0),
            ReminderScheduleCalculator.nextOccurrence(reminder, at(2024, 2, 29, 8, 0))?.at
        )
        val nonLeap = reminder("Hàng tháng", "30 thg 1, 2025", "08:00")
        assertEquals(
            at(2025, 2, 28, 8, 0),
            ReminderScheduleCalculator.nextOccurrence(nonLeap, at(2025, 1, 31, 9, 0))?.at
        )
    }

    @Test
    fun calendarHandlesLeapYearsAndSundayBasedWeekday() {
        assertEquals(29, ReminderCalendar.daysInMonth(2024, 2))
        assertEquals(28, ReminderCalendar.daysInMonth(2100, 2))
        assertEquals(0, ReminderCalendar.sundayBasedDayOfWeek(ReminderLocalDate(2026, 9, 6)))
    }

    @Test
    fun providerControlsLocalTimezoneWallClock() {
        val provider = FakeReminderDateTimeProvider(at(2027, 1, 1, 0, 5))
        val reminder = reminder("Hàng ngày", "1 thg 1, 2027", "08:00")
        assertEquals(
            at(2027, 1, 1, 8, 0),
            ReminderScheduleCalculator.nextOccurrence(reminder, provider.currentLocalDateTime())?.at
        )
    }

    private fun reminder(frequency: String, date: String, time: String) = Reminder(
        id = "id", userId = "user", title = "Title", note = "Note",
        frequency = frequency, startDate = date, time = time
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        ReminderLocalDateTime(ReminderLocalDate(year, month, day), ReminderLocalTime(hour, minute))
}

private class FakeReminderDateTimeProvider(private val now: ReminderLocalDateTime) : ReminderDateTimeProvider {
    override fun currentLocalDateTime(): ReminderLocalDateTime = now
}
