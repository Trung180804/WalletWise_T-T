package com.example.walletwise.domain.service

data class BudgetDate(
    val year: Int,
    val month: Int,
    val dayOfMonth: Int
)

interface BudgetDateProvider {
    /** Local calendar date in the device timezone. */
    fun currentLocalDate(): BudgetDate

    /** Local calendar date for an epoch timestamp in the same device timezone. */
    fun localDateAt(epochMilliseconds: Long): BudgetDate?
}

object BudgetCalendar {
    fun monthKey(date: BudgetDate): String {
        val month = date.month.coerceIn(1, 12)
        val paddedMonth = if (month < 10) "0$month" else month.toString()
        return "$paddedMonth-${date.year}"
    }

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 30
    }

    fun remainingDays(date: BudgetDate): Int {
        val days = daysInMonth(date.year, date.month)
        val currentDay = date.dayOfMonth.coerceIn(1, days)
        return days - currentDay + 1
    }

    fun nextMonth(date: BudgetDate): BudgetDate = if (date.month == 12) {
        BudgetDate(date.year + 1, 1, 1)
    } else {
        BudgetDate(date.year, (date.month + 1).coerceIn(1, 12), 1)
    }

    fun isLeapYear(year: Int): Boolean =
        year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
}
