package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.model.ReminderFrequency

data class ReminderLocalDate(
    val year: Int,
    val month: Int,
    val dayOfMonth: Int
) : Comparable<ReminderLocalDate> {
    override fun compareTo(other: ReminderLocalDate): Int =
        compareValuesBy(this, other, ReminderLocalDate::year, ReminderLocalDate::month, ReminderLocalDate::dayOfMonth)
}

data class ReminderLocalTime(
    val hour: Int,
    val minute: Int
) : Comparable<ReminderLocalTime> {
    override fun compareTo(other: ReminderLocalTime): Int =
        compareValuesBy(this, other, ReminderLocalTime::hour, ReminderLocalTime::minute)
}

data class ReminderLocalDateTime(
    val date: ReminderLocalDate,
    val time: ReminderLocalTime
) : Comparable<ReminderLocalDateTime> {
    override fun compareTo(other: ReminderLocalDateTime): Int {
        val dateComparison = date.compareTo(other.date)
        return if (dateComparison != 0) dateComparison else time.compareTo(other.time)
    }
}

data class ReminderOccurrence(
    val index: Long,
    val at: ReminderLocalDateTime
)

interface ReminderDateTimeProvider {
    /** Local wall-clock time in the device's current timezone. */
    fun currentLocalDateTime(): ReminderLocalDateTime
}

object ReminderCalendar {
    fun isLeapYear(year: Int): Boolean =
        year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 0
    }

    fun isValid(date: ReminderLocalDate): Boolean =
        date.year in 1..9999 &&
            date.month in 1..12 &&
            date.dayOfMonth in 1..daysInMonth(date.year, date.month)

    fun plusDays(date: ReminderLocalDate, days: Long): ReminderLocalDate? {
        if (!isValid(date)) return null
        var remaining = days
        var current = date
        if (remaining >= 0) {
            while (remaining > 0) {
                val daysLeft = daysInMonth(current.year, current.month) - current.dayOfMonth
                if (remaining <= daysLeft) {
                    return current.copy(dayOfMonth = current.dayOfMonth + remaining.toInt())
                }
                remaining -= daysLeft + 1L
                current = nextMonth(current) ?: return null
            }
        } else {
            while (remaining < 0) {
                val before = current.dayOfMonth - 1
                if (-remaining <= before) {
                    return current.copy(dayOfMonth = current.dayOfMonth + remaining.toInt())
                }
                remaining += before + 1L
                current = previousMonth(current) ?: return null
            }
        }
        return current
    }

    fun plusMonthsAnchored(date: ReminderLocalDate, months: Long): ReminderLocalDate? {
        if (!isValid(date)) return null
        val totalMonths = date.year.toLong() * 12L + date.month - 1L + months
        if (totalMonths < 12L || totalMonths > 9999L * 12L + 11L) return null
        val year = (totalMonths / 12L).toInt()
        val month = (totalMonths % 12L).toInt() + 1
        return ReminderLocalDate(year, month, date.dayOfMonth.coerceAtMost(daysInMonth(year, month)))
    }

    fun plusYearsAnchored(date: ReminderLocalDate, years: Long): ReminderLocalDate? {
        if (!isValid(date)) return null
        val year = date.year.toLong() + years
        if (year !in 1L..9999L) return null
        return ReminderLocalDate(
            year = year.toInt(),
            month = date.month,
            dayOfMonth = date.dayOfMonth.coerceAtMost(daysInMonth(year.toInt(), date.month))
        )
    }

    fun daysBetween(start: ReminderLocalDate, end: ReminderLocalDate): Long? {
        if (!isValid(start) || !isValid(end)) return null
        return epochDay(end) - epochDay(start)
    }

    /** Sunday = 0, Monday = 1, ... Saturday = 6. */
    fun sundayBasedDayOfWeek(date: ReminderLocalDate): Int? {
        if (!isValid(date)) return null
        return floorMod(epochDay(date) + 4L, 7L).toInt()
    }

    private fun nextMonth(date: ReminderLocalDate): ReminderLocalDate? = when {
        date.month < 12 -> ReminderLocalDate(date.year, date.month + 1, 1)
        date.year < 9999 -> ReminderLocalDate(date.year + 1, 1, 1)
        else -> null
    }

    private fun previousMonth(date: ReminderLocalDate): ReminderLocalDate? = when {
        date.month > 1 -> {
            val month = date.month - 1
            ReminderLocalDate(date.year, month, daysInMonth(date.year, month))
        }
        date.year > 1 -> ReminderLocalDate(date.year - 1, 12, 31)
        else -> null
    }

    private fun epochDay(date: ReminderLocalDate): Long {
        var year = date.year.toLong()
        val month = date.month.toLong()
        year -= if (month <= 2L) 1L else 0L
        val era = floorDiv(year, 400L)
        val yearOfEra = year - era * 400L
        val adjustedMonth = month + if (month > 2L) -3L else 9L
        val dayOfYear = (153L * adjustedMonth + 2L) / 5L + date.dayOfMonth - 1L
        val dayOfEra = yearOfEra * 365L + yearOfEra / 4L - yearOfEra / 100L + dayOfYear
        return era * 146097L + dayOfEra - 719468L
    }

    private fun floorDiv(value: Long, divisor: Long): Long {
        val quotient = value / divisor
        val remainder = value % divisor
        return if (remainder != 0L && (value xor divisor) < 0L) quotient - 1L else quotient
    }

    private fun floorMod(value: Long, divisor: Long): Long = value - floorDiv(value, divisor) * divisor
}

object ReminderScheduleCalculator {
    private const val MAX_OCCURRENCE_SEARCH = 10_000L

    fun parseStartDate(value: String): ReminderLocalDate? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        val numericParts = Regex("(\\d{1,4})\\D+(\\d{1,2})\\D+(\\d{1,4})")
            .find(trimmed)
            ?.groupValues
            ?: return null
        val first = numericParts[1].toIntOrNull() ?: return null
        val second = numericParts[2].toIntOrNull() ?: return null
        val third = numericParts[3].toIntOrNull() ?: return null
        val date = if (first > 31) {
            ReminderLocalDate(first, second, third)
        } else {
            ReminderLocalDate(third, second, first)
        }
        return date.takeIf(ReminderCalendar::isValid)
    }

    fun parseTime(value: String): ReminderLocalTime? {
        val parts = value.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return ReminderLocalTime(hour, minute).takeIf { hour in 0..23 && minute in 0..59 }
    }

    fun formatStartDate(date: ReminderLocalDate): String =
        "${date.dayOfMonth} thg ${date.month}, ${date.year}"

    fun formatTime(time: ReminderLocalTime): String = "${pad2(time.hour)}:${pad2(time.minute)}"

    fun startAt(reminder: Reminder): ReminderLocalDateTime? {
        val date = parseStartDate(reminder.startDate) ?: return null
        val time = parseTime(reminder.time) ?: return null
        return ReminderLocalDateTime(date, time)
    }

    /** Returns the first occurrence strictly after [now], matching the former java.time implementation. */
    fun nextOccurrence(reminder: Reminder, now: ReminderLocalDateTime): ReminderOccurrence? {
        val start = startAt(reminder) ?: return null
        if (now < start) return ReminderOccurrence(0L, start)
        val frequency = ReminderFrequency.fromWireValueOrDaily(reminder.frequency)
        var index = when (frequency) {
            ReminderFrequency.DAILY -> ReminderCalendar.daysBetween(start.date, now.date) ?: return null
            ReminderFrequency.WEEKLY -> (ReminderCalendar.daysBetween(start.date, now.date) ?: return null) / 7L
            ReminderFrequency.MONTHLY ->
                ((now.date.year - start.date.year) * 12L + now.date.month - start.date.month).coerceAtLeast(0L)
        }.coerceIn(0L, MAX_OCCURRENCE_SEARCH)
        var occurrence = occurrenceAt(start, frequency, index) ?: return null
        if (occurrence <= now) {
            index++
            if (index > MAX_OCCURRENCE_SEARCH) return null
            occurrence = occurrenceAt(start, frequency, index) ?: return null
        }
        return ReminderOccurrence(index, occurrence)
    }

    private fun occurrenceAt(
        start: ReminderLocalDateTime,
        frequency: ReminderFrequency,
        index: Long
    ): ReminderLocalDateTime? {
        val date = when (frequency) {
            ReminderFrequency.DAILY -> ReminderCalendar.plusDays(start.date, index)
            ReminderFrequency.WEEKLY -> ReminderCalendar.plusDays(start.date, index * 7L)
            ReminderFrequency.MONTHLY -> ReminderCalendar.plusMonthsAnchored(start.date, index)
        } ?: return null
        return ReminderLocalDateTime(date, start.time)
    }

    private fun pad2(value: Int): String = if (value in 0..9) "0$value" else value.toString()
}
