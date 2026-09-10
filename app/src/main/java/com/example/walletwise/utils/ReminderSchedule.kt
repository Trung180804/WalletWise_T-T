package com.example.walletwise.utils

import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.model.Reminder
import java.time.LocalDateTime

/** Pure reminder schedule calculations shared by the UI and AlarmManager code. */
object ReminderSchedule {

    /** Returns the first reminder occurrence strictly after [now]. */
    fun nextOccurrence(
        reminder: Reminder,
        now: LocalDateTime
    ): RecurringSchedule.ScheduledOccurrence? =
        RecurringSchedule.nextOccurrence(reminder.asRecurringRule(), now)

    private fun Reminder.asRecurringRule() = RecurringTransaction(
        id = id,
        userId = userId,
        title = title,
        frequency = frequency,
        timesCount = "Khác",
        startDate = startDate,
        time = time,
        note = note,
        isEnabled = isEnabled
    )
}
