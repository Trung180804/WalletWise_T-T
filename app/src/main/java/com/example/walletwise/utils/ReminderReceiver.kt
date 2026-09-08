package com.example.walletwise.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.walletwise.domain.model.Reminder
import com.google.firebase.auth.FirebaseAuth

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val userId = intent.getStringExtra(ReminderScheduler.EXTRA_USER_ID).orEmpty()
        val reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID).orEmpty()
        if (userId.isBlank() || reminderId.isBlank()) return

        // An alarm from a previously signed-in account must never be shown to
        // the person currently using the app.
        if (FirebaseAuth.getInstance().currentUser?.uid != userId) return

        val reminder = Reminder(
            id = reminderId,
            userId = userId,
            title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty(),
            note = intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE).orEmpty(),
            frequency = intent.getStringExtra(ReminderScheduler.EXTRA_FREQUENCY).orEmpty(),
            startDate = intent.getStringExtra(ReminderScheduler.EXTRA_START_DATE).orEmpty(),
            time = intent.getStringExtra(ReminderScheduler.EXTRA_TIME).orEmpty(),
            isEnabled = true
        )

        NotificationHelper.showNotification(
            context.applicationContext,
            reminderId.hashCode(),
            "WalletWise - ${reminder.title.ifBlank { "Lời nhắc" }}",
            reminder.note.ifBlank { "Đừng quên ghi lại các khoản thu chi của bạn!" }
        )

        // AlarmManager alarms are one-shot. Register the next occurrence only
        // after this one has fired so daily/weekly/monthly reminders continue.
        ReminderScheduler.schedule(context.applicationContext, reminder)
    }
}
