package com.example.walletwise.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.walletwise.domain.model.RecurringTransaction
import java.time.LocalDateTime
import java.time.ZoneId

object RecurringTransactionScheduler {
    const val EXTRA_USER_ID = "recurring_user_id"
    const val EXTRA_RECURRING_ID = "recurring_id"
    const val EXTRA_RETRY_ATTEMPT = "recurring_retry_attempt"

    private const val MAX_RETRY_ATTEMPTS = 5

    fun schedule(context: Context, recurring: RecurringTransaction) {
        cancel(context, recurring.id)
        if (!recurring.isEnabled || recurring.userId.isBlank()) return

        val next = RecurringSchedule.nextUnprocessedOccurrence(
            recurring,
            LocalDateTime.now()
        ) ?: return
        val occurrenceMillis = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // A due time can pass in the few milliseconds between the Firestore
        // check and alarm registration. Schedule it immediately in that case.
        val triggerAtMillis = maxOf(occurrenceMillis, System.currentTimeMillis() + 250L)
        setAlarm(
            context,
            triggerAtMillis,
            pendingIntent(context, recurring.userId, recurring.id, retryAttempt = 0)
        )
    }

    /** Retries a due write after a transient Firebase/network failure. */
    fun scheduleRetry(
        context: Context,
        userId: String,
        recurringId: String,
        retryAttempt: Int
    ) {
        if (userId.isBlank() || recurringId.isBlank() || retryAttempt !in 1..MAX_RETRY_ATTEMPTS) return
        val delayMinutes = 1L shl (retryAttempt - 1)
        val triggerAtMillis = System.currentTimeMillis() + delayMinutes * 60_000L
        setAlarm(
            context,
            triggerAtMillis,
            pendingIntent(context, userId, recurringId, retryAttempt)
        )
    }

    fun cancel(context: Context, recurringId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RecurringTransactionReceiver::class.java).apply {
            putExtra(EXTRA_RECURRING_ID, recurringId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            recurringId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun pendingIntent(
        context: Context,
        userId: String,
        recurringId: String,
        retryAttempt: Int
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            recurringId.hashCode(),
            Intent(context, RecurringTransactionReceiver::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_RECURRING_ID, recurringId)
                putExtra(EXTRA_RETRY_ATTEMPT, retryAttempt)
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
                // Android 12+ requires user-approved special access for exact
                // alarms. Keep an inexact fallback until that access is granted;
                // the permission-change receiver will then restore an exact one.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (error: SecurityException) {
            android.util.Log.w("RECURRING_ALARM", "Exact alarm access is unavailable", error)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }
}
