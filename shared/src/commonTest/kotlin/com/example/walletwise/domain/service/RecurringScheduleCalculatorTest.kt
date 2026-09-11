package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.RECURRING_FREQUENCY_DAILY
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_MONTHLY
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_WEEKLY
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_YEARLY
import com.example.walletwise.domain.model.RECURRING_TIMES_UNLIMITED
import com.example.walletwise.domain.model.RecurringTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurringScheduleCalculatorTest {
    @Test
    fun everyExistingFrequencyKeepsItsCurrentCadence() {
        assertEquals(dateTime(2026, 9, 6, 8), next(RECURRING_FREQUENCY_DAILY, dateTime(2026, 9, 5, 8)).at)
        assertEquals(dateTime(2026, 9, 12, 8), next(RECURRING_FREQUENCY_WEEKLY, dateTime(2026, 9, 5, 8)).at)
        assertEquals(dateTime(2026, 10, 5, 8), next(RECURRING_FREQUENCY_MONTHLY, dateTime(2026, 9, 5, 8)).at)
        assertEquals(dateTime(2027, 9, 5, 8), next(RECURRING_FREQUENCY_YEARLY, dateTime(2026, 9, 5, 8)).at)
    }

    @Test
    fun legacyMojibakeWeeklyLabelStillUsesWeeklyCadence() {
        val legacy = recurring(frequency = "HÃƒÂ ng tuÃ¡ÂºÂ§n")
        val next = RecurringScheduleCalculator.nextOccurrence(legacy, dateTime(2026, 9, 5, 8))

        assertEquals(dateTime(2026, 9, 12, 8), next?.at)
    }

    @Test
    fun monthlyAnchorRestoresTheOriginalDayAfterShortMonths() {
        val rule = recurring(
            frequency = RECURRING_FREQUENCY_MONTHLY,
            startDate = "31 thg 1, 2026"
        )

        assertEquals(dateTime(2026, 2, 28, 8), RecurringScheduleCalculator.nextOccurrence(rule, dateTime(2026, 1, 31, 8))?.at)
        assertEquals(dateTime(2026, 3, 31, 8), RecurringScheduleCalculator.nextOccurrence(rule, dateTime(2026, 2, 28, 8))?.at)
    }

    @Test
    fun monthlyAnchorsCoverDays29And30AcrossLeapFebruary() {
        val day29 = recurring(frequency = RECURRING_FREQUENCY_MONTHLY, startDate = "29 thg 1, 2028")
        val day30 = recurring(frequency = RECURRING_FREQUENCY_MONTHLY, startDate = "30 thg 1, 2028")

        assertEquals(dateTime(2028, 2, 29, 8), RecurringScheduleCalculator.nextOccurrence(day29, dateTime(2028, 1, 29, 8))?.at)
        assertEquals(dateTime(2028, 2, 29, 8), RecurringScheduleCalculator.nextOccurrence(day30, dateTime(2028, 1, 30, 8))?.at)
        assertEquals(dateTime(2028, 3, 30, 8), RecurringScheduleCalculator.nextOccurrence(day30, dateTime(2028, 2, 29, 8))?.at)
    }

    @Test
    fun leapDayYearlyAnchorAndYearBoundaryAreStable() {
        val leap = recurring(
            frequency = RECURRING_FREQUENCY_YEARLY,
            startDate = "29 thg 2, 2028"
        )
        val daily = recurring(startDate = "31 thg 12, 2026")

        assertEquals(dateTime(2029, 2, 28, 8), RecurringScheduleCalculator.nextOccurrence(leap, dateTime(2028, 2, 29, 8))?.at)
        assertEquals(dateTime(2032, 2, 29, 8), RecurringScheduleCalculator.nextOccurrence(leap, dateTime(2031, 2, 28, 8))?.at)
        assertEquals(dateTime(2027, 1, 1, 8), RecurringScheduleCalculator.nextOccurrence(daily, dateTime(2026, 12, 31, 8))?.at)
    }

    @Test
    fun numericTimesCountLimitsRunsAndUnlimitedKeepsScheduling() {
        (1..7).forEach { count ->
            val rule = recurring(timesCount = count.toString())
            assertEquals(count.toLong(), RecurringScheduleCalculator.executionLimit(rule))
            assertTrue(RecurringScheduleCalculator.hasReachedExecutionLimit(rule, count - 1L))
        }
        val unlimited = recurring(timesCount = RECURRING_TIMES_UNLIMITED)
        assertEquals(Long.MAX_VALUE, RecurringScheduleCalculator.executionLimit(unlimited))
        assertFalse(RecurringScheduleCalculator.hasReachedExecutionLimit(unlimited, 100L))
    }

    @Test
    fun finiteOfflineRecoveryReturnsOnlyTheLastAllowedOccurrence() {
        val rule = recurring(timesCount = "2")
        val due = RecurringScheduleCalculator.latestDueOccurrence(rule, dateTime(2026, 9, 20, 9))

        assertEquals(1L, due?.index)
        assertEquals(dateTime(2026, 9, 6, 8), due?.at)
        assertNull(RecurringScheduleCalculator.nextOccurrence(rule, dateTime(2026, 9, 20, 9)))
    }

    @Test
    fun futureOccurrenceIsNeverReportedDue() {
        val rule = recurring(startDate = "6 thg 9, 2026")
        assertNull(RecurringScheduleCalculator.latestDueOccurrence(rule, dateTime(2026, 9, 5, 23, 59)))
    }

    @Test
    fun dailyScheduleOlderThanTenThousandDaysStillSelectsLatestOccurrence() {
        val rule = recurring(startDate = "1 thg 1, 1990")

        val due = RecurringScheduleCalculator.latestDueOccurrence(rule, dateTime(2026, 9, 11, 9))

        assertEquals(ReminderLocalDate(2026, 9, 11), due?.at?.date)
    }

    @Test
    fun aMarkerFromTheSameOrLaterLocalDatePreventsBackwardExecutionAfterClockChange() {
        assertTrue(RecurringScheduleCalculator.hasProcessed("2026-09-06", "2026-09-06"))
        assertTrue(RecurringScheduleCalculator.hasProcessed("2026-09-06", "2026-09-05"))
        assertFalse(RecurringScheduleCalculator.hasProcessed("2026-09-05", "2026-09-06"))
    }

    @Test
    fun parseAndFormatRemainCompatibleWithIsoAndVietnameseDisplayValues() {
        assertEquals(ReminderLocalDate(2026, 9, 7), RecurringScheduleCalculator.parseStartDate("2026-09-07"))
        assertEquals(ReminderLocalDate(2026, 9, 7), RecurringScheduleCalculator.parseStartDate("7 thg 9, 2026"))
        assertEquals("7 thg 9, 2026", RecurringScheduleCalculator.formatStartDate(ReminderLocalDate(2026, 9, 7)))
        assertEquals(ReminderLocalTime(8, 5), RecurringScheduleCalculator.parseTime("08:05"))
        assertEquals("08:05", RecurringScheduleCalculator.formatTime(ReminderLocalTime(8, 5)))
    }

    private fun next(frequency: String, now: ReminderLocalDateTime): RecurringOccurrence =
        requireNotNull(RecurringScheduleCalculator.nextOccurrence(recurring(frequency = frequency), now))

    private fun recurring(
        frequency: String = RECURRING_FREQUENCY_DAILY,
        timesCount: String = RECURRING_TIMES_UNLIMITED,
        startDate: String = "5 thg 9, 2026"
    ) = RecurringTransaction(
        id = "rule",
        userId = "user",
        title = "Rule",
        amount = 1.0,
        frequency = frequency,
        timesCount = timesCount,
        startDate = startDate,
        time = "08:00"
    )

    private fun dateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        ReminderLocalDateTime(ReminderLocalDate(year, month, day), ReminderLocalTime(hour, minute))
}
