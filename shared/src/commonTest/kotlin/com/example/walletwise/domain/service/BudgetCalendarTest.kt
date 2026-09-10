package com.example.walletwise.domain.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetCalendarTest {
    @Test
    fun monthKeysPadJanuaryAndSeptember() {
        assertEquals("01-2026", BudgetCalendar.monthKey(BudgetDate(2026, 1, 1)))
        assertEquals("09-2026", BudgetCalendar.monthKey(BudgetDate(2026, 9, 1)))
    }

    @Test
    fun decemberRollsToJanuaryOfNextYear() {
        assertEquals(BudgetDate(2027, 1, 1), BudgetCalendar.nextMonth(BudgetDate(2026, 12, 31)))
    }

    @Test
    fun leapYearAndMonthLengthsAreCorrect() {
        assertTrue(BudgetCalendar.isLeapYear(2024))
        assertFalse(BudgetCalendar.isLeapYear(2100))
        assertEquals(29, BudgetCalendar.daysInMonth(2024, 2))
        assertEquals(28, BudgetCalendar.daysInMonth(2025, 2))
    }

    @Test
    fun currentDayIsIncludedInRemainingDays() {
        assertEquals(30, BudgetCalendar.remainingDays(BudgetDate(2026, 9, 1)))
        assertEquals(1, BudgetCalendar.remainingDays(BudgetDate(2026, 9, 30)))
    }
}
