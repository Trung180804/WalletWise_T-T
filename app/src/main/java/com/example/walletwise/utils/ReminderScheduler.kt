package com.example.walletwise.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.walletwise.domain.model.Reminder
import java.time.LocalDateTime
import java.time.ZoneId

/** Registers one alarm at a time; [ReminderReceiver] registers the following one. */
object ReminderScheduler {
    const val EXTRA_USER_ID = "reminder_user_id"
    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_TITLE = "reminder_title"
    const val EXTRA_MESSAGE = "reminder_message"
    const val EXTRA_FREQUENCY = "reminder_frequency"
    const val EXTRA_START_DATE = "reminder_start_date"
    const val EXTRA_TIME = "reminder_time"

    fun schedule(context: Context, reminder: Reminder) {
        cancel(context, reminder.id)
        if (!reminder.isEnabled || reminder.userId.isBlank()) return

        val next = ReminderSchedule.nextOccurrence(reminder, LocalDateTime.now()) ?: return
        val triggerAtMillis = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAlarm(context, triggerAtMillis, pendingIntent(context, reminder))
    }

    fun cancel(context: Context, reminderId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun pendingIntent(context: Context, reminder: Reminder): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            Intent(context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_USER_ID, reminder.userId)
                putExtra(EXTRA_REMINDER_ID, reminder.id)
                putExtra(EXTRA_TITLE, reminder.title)
                putExtra(EXTRA_MESSAGE, reminder.note)
                putExtra(EXTRA_FREQUENCY, reminder.frequency)
                putExtra(EXTRA_START_DATE, reminder.startDate)
                putExtra(EXTRA_TIME, reminder.time)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setAlarm(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canUseExactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        try {
            if (canUseExactAlarm) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (error: SecurityException) {
            android.util.Log.w("REMINDER_ALARM", "Exact alarm access is unavailable", error)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }
}
