package com.example.walletwise.utils

import com.example.walletwise.domain.model.RecurringTransaction
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Converts the values shown in the recurring-transaction form into concrete
 * occurrences. Keeping this logic independent from Android also makes the
 * schedule predictable and testable.
 */
object RecurringSchedule {

    private const val MAX_OCCURRENCE_SEARCH = 10_000L

    fun parseStartDate(value: String): LocalDate? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null

        // Try ISO format yyyy-MM-dd (e.g. "2026-09-07")
        runCatching { LocalDate.parse(trimmed) }.getOrNull()?.let { return it }

        // Try "7 thg 9, 2026" or "07/09/2026" or "7-9-2026"
        val parts = Regex("(\\d{1,4})\\D+(\\d{1,2})\\D+(\\d{1,4})").find(trimmed)?.groupValues
        if (parts != null) {
            val n1 = parts[1].toInt()
            val n2 = parts[2].toInt()
            val n3 = parts[3].toInt()
            return runCatching {
                if (n1 > 31) {
                    LocalDate.of(n1, n2, n3)
                } else {
                    LocalDate.of(n3, n2, n1)
                }
            }.getOrNull()
        }
        return null
    }

    fun parseTime(value: String): LocalTime? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        runCatching { LocalTime.parse(trimmed) }.getOrNull()?.let { return it }
        val parts = trimmed.split(":")
        if (parts.size == 2) {
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return runCatching { LocalTime.of(h, m) }.getOrNull()
        }
        return null
    }

    fun formatStartDate(date: LocalDate): String =
        "${date.dayOfMonth} thg ${date.monthValue}, ${date.year}"

    fun startAt(recurring: RecurringTransaction): LocalDateTime? {
        val date = parseStartDate(recurring.startDate) ?: return null
        val time = parseTime(recurring.time) ?: return null
        return LocalDateTime.of(date, time)
    }

    /** Returns the most recent scheduled occurrence that has arrived. */
    fun latestDueOccurrence(
        recurring: RecurringTransaction,
        now: LocalDateTime
    ): ScheduledOccurrence? {
        val occurrence = latestDueOccurrenceIgnoringLimit(recurring, now) ?: return null
        val limit = executionLimit(recurring)
        if (limit == Long.MAX_VALUE || occurrence.index < limit) return occurrence

        // If the phone was offline/asleep through a finite schedule, recover
        // its last allowed occurrence instead of silently losing every run.
        val lastAllowedIndex = limit - 1
        val start = startAt(recurring) ?: return null
        return ScheduledOccurrence(
            lastAllowedIndex,
            occurrenceAt(start, frequency(recurring.frequency), lastAllowedIndex)
        )
    }

    /** Returns the next occurrence strictly after [now], or null when finished. */
    fun nextOccurrence(
        recurring: RecurringTransaction,
        now: LocalDateTime
    ): ScheduledOccurrence? {
        val start = startAt(recurring) ?: return null
        if (now.isBefore(start)) return withinLimit(recurring, 0, start)

        val latest = latestDueOccurrenceIgnoringLimit(recurring, now) ?: return null
        if (latest.at > now) return withinLimit(recurring, latest.index, latest.at)
        val nextIndex = latest.index + 1
        return withinLimit(
            recurring,
            nextIndex,
            occurrenceAt(start, frequency(recurring.frequency), nextIndex)
        )
    }

    fun occurrenceKey(occurrence: ScheduledOccurrence): String = occurrence.at.toLocalDate().toString()

    /** Returns an overdue unprocessed occurrence first, otherwise the next future one. */
    fun nextUnprocessedOccurrence(
        recurring: RecurringTransaction,
        now: LocalDateTime
    ): ScheduledOccurrence? {
        val overdue = latestDueOccurrence(recurring, now)
        if (overdue != null && recurring.lastExecutedDate != occurrenceKey(overdue)) {
            return overdue
        }
        return nextOccurrence(recurring, now)
    }

    fun hasReachedExecutionLimit(recurring: RecurringTransaction, occurrenceIndex: Long): Boolean {
        val limit = executionLimit(recurring)
        return limit != Long.MAX_VALUE && occurrenceIndex + 1 >= limit
    }

    private fun withinLimit(
        recurring: RecurringTransaction,
        index: Long,
        at: LocalDateTime
    ): ScheduledOccurrence? =
        if (index < executionLimit(recurring)) ScheduledOccurrence(index, at) else null

    private fun latestDueOccurrenceIgnoringLimit(
        recurring: RecurringTransaction,
        now: LocalDateTime
    ): ScheduledOccurrence? {
        val start = startAt(recurring) ?: return null
        if (now.isBefore(start)) return null

        val ruleFrequency = frequency(recurring.frequency)
        var index = when (ruleFrequency) {
            Frequency.DAILY -> ChronoUnit.DAYS.between(start.toLocalDate(), now.toLocalDate())
            Frequency.WEEKLY -> ChronoUnit.DAYS.between(start.toLocalDate(), now.toLocalDate()) / 7
            Frequency.MONTHLY -> latestMonthlyIndex(start, now)
            Frequency.YEARLY -> latestYearlyIndex(start, now)
        }.coerceIn(0, MAX_OCCURRENCE_SEARCH)
        var scheduledAt = occurrenceAt(start, ruleFrequency, index)
        if (scheduledAt > now) {
            index--
            if (index < 0) return null
            scheduledAt = occurrenceAt(start, ruleFrequency, index)
        }
        return ScheduledOccurrence(index, scheduledAt)
    }

    private fun latestMonthlyIndex(start: LocalDateTime, now: LocalDateTime): Long {
        var index = ChronoUnit.MONTHS.between(
            start.toLocalDate().withDayOfMonth(1),
            now.toLocalDate().withDayOfMonth(1)
        ).coerceAtLeast(0)
        if (occurrenceAt(start, Frequency.MONTHLY, index) > now) index--
        return index.coerceAtLeast(0)
    }

    private fun latestYearlyIndex(start: LocalDateTime, now: LocalDateTime): Long {
        var index = (now.year - start.year).toLong().coerceAtLeast(0)
        if (occurrenceAt(start, Frequency.YEARLY, index) > now) index--
        return index.coerceAtLeast(0)
    }

    private fun occurrenceAt(start: LocalDateTime, frequency: Frequency, index: Long): LocalDateTime =
        when (frequency) {
            Frequency.DAILY -> start.plusDays(index)
            Frequency.WEEKLY -> start.plusWeeks(index)
            Frequency.MONTHLY -> start.plusMonths(index)
            Frequency.YEARLY -> start.plusYears(index)
        }

    private fun executionLimit(recurring: RecurringTransaction): Long =
        recurring.timesCount.toLongOrNull()?.takeIf { it > 0 } ?: Long.MAX_VALUE

    private fun frequency(value: String): Frequency {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{M}".toRegex(), "")
            .lowercase()
        return when {
            "tuần" in value || "tuáº§n" in value || "tuan" in normalized || "tua" in normalized -> Frequency.WEEKLY
            "tháng" in value || "thÃ¡ng" in value || "thang" in normalized || "tha" in normalized -> Frequency.MONTHLY
            "năm" in value || "nÄƒm" in value || "nam" in normalized || "nƒm" in normalized -> Frequency.YEARLY
            else -> Frequency.DAILY
        }
    }

    data class ScheduledOccurrence(val index: Long, val at: LocalDateTime)

    private enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }
}
