package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.RecurringFrequency
import com.example.walletwise.domain.model.RecurringTransaction

data class RecurringOccurrence(
    val index: Long,
    val at: ReminderLocalDateTime
)

interface RecurringDateTimeProvider {
    /** Local wall-clock time in the device's current timezone. */
    fun currentLocalDateTime(): ReminderLocalDateTime

    /** Converts a local occurrence with the device's current timezone rules. */
    fun toEpochMilliseconds(dateTime: ReminderLocalDateTime): Long

    fun currentEpochMilliseconds(): Long
}

object RecurringScheduleCalculator {
    fun parseStartDate(value: String): ReminderLocalDate? =
        ReminderScheduleCalculator.parseStartDate(value)

    fun parseTime(value: String): ReminderLocalTime? =
        ReminderScheduleCalculator.parseTime(value)

    fun formatStartDate(date: ReminderLocalDate): String =
        ReminderScheduleCalculator.formatStartDate(date)

    fun formatTime(time: ReminderLocalTime): String =
        ReminderScheduleCalculator.formatTime(time)

    fun startAt(recurring: RecurringTransaction): ReminderLocalDateTime? {
        val date = parseStartDate(recurring.startDate) ?: return null
        val time = parseTime(recurring.time) ?: return null
        return ReminderLocalDateTime(date, time)
    }

    /** Returns the most recent arrived occurrence, preserving the existing one-run recovery rule. */
    fun latestDueOccurrence(
        recurring: RecurringTransaction,
        now: ReminderLocalDateTime
    ): RecurringOccurrence? {
        return latestDueOccurrenceIgnoringLimit(recurring, now)
    }

    /** Legacy timesCount is readable metadata; an enabled schedule always continues. */
    fun nextOccurrence(
        recurring: RecurringTransaction,
        now: ReminderLocalDateTime
    ): RecurringOccurrence? {
        val start = startAt(recurring) ?: return null
        if (now < start) return RecurringOccurrence(0L, start)
        val latest = latestDueOccurrenceIgnoringLimit(recurring, now) ?: return null
        val nextIndex = latest.index + 1L
        val next = occurrenceAt(start, frequency(recurring.frequency), nextIndex) ?: return null
        return RecurringOccurrence(nextIndex, next)
    }

    fun occurrenceKey(occurrence: RecurringOccurrence): String = occurrence.at.date.toIsoString()

    /** Returns one overdue unprocessed occurrence; missed periods are not backfilled in bulk. */
    fun nextUnprocessedOccurrence(
        recurring: RecurringTransaction,
        now: ReminderLocalDateTime
    ): RecurringOccurrence? {
        val overdue = latestDueOccurrence(recurring, now)
        if (overdue != null && !hasProcessed(recurring.lastExecutedDate, occurrenceKey(overdue))) {
            return overdue
        }
        return nextOccurrence(recurring, now)
    }

    fun hasProcessed(lastExecutedDate: String, occurrenceKey: String): Boolean {
        val marker = parseIsoDate(lastExecutedDate) ?: return false
        val occurrence = parseIsoDate(occurrenceKey) ?: return false
        return marker >= occurrence
    }

    private fun latestDueOccurrenceIgnoringLimit(
        recurring: RecurringTransaction,
        now: ReminderLocalDateTime
    ): RecurringOccurrence? {
        val start = startAt(recurring) ?: return null
        if (now < start) return null
        val frequency = frequency(recurring.frequency)
        var index = when (frequency) {
            RecurringFrequency.DAILY -> ReminderCalendar.daysBetween(start.date, now.date) ?: return null
            RecurringFrequency.WEEKLY -> (ReminderCalendar.daysBetween(start.date, now.date) ?: return null) / 7L
            RecurringFrequency.MONTHLY ->
                ((now.date.year - start.date.year) * 12L + now.date.month - start.date.month)
                    .coerceAtLeast(0L)
            RecurringFrequency.YEARLY -> (now.date.year - start.date.year).toLong().coerceAtLeast(0L)
        }
        var at = occurrenceAt(start, frequency, index) ?: return null
        if (at > now) {
            index--
            if (index < 0L) return null
            at = occurrenceAt(start, frequency, index) ?: return null
        }
        return RecurringOccurrence(index, at)
    }

    private fun occurrenceAt(
        start: ReminderLocalDateTime,
        frequency: RecurringFrequency,
        index: Long
    ): ReminderLocalDateTime? {
        val date = when (frequency) {
            RecurringFrequency.DAILY -> ReminderCalendar.plusDays(start.date, index)
            RecurringFrequency.WEEKLY -> ReminderCalendar.plusDays(start.date, index * 7L)
            RecurringFrequency.MONTHLY -> ReminderCalendar.plusMonthsAnchored(start.date, index)
            RecurringFrequency.YEARLY -> ReminderCalendar.plusYearsAnchored(start.date, index)
        } ?: return null
        return ReminderLocalDateTime(date, start.time)
    }

    private fun frequency(value: String): RecurringFrequency =
        RecurringFrequency.fromWireValueOrDaily(value)

    private fun parseIsoDate(value: String): ReminderLocalDate? {
        val match = Regex("(\\d{4})-(\\d{2})-(\\d{2})").matchEntire(value.trim()) ?: return null
        return ReminderLocalDate(
            match.groupValues[1].toIntOrNull() ?: return null,
            match.groupValues[2].toIntOrNull() ?: return null,
            match.groupValues[3].toIntOrNull() ?: return null
        ).takeIf(ReminderCalendar::isValid)
    }

    private fun ReminderLocalDate.toIsoString(): String =
        "$year-${pad2(month)}-${pad2(dayOfMonth)}"

    private fun pad2(value: Int): String = if (value in 0..9) "0$value" else value.toString()
}
