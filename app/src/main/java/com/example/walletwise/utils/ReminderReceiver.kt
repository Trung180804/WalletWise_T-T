package com.example.walletwise.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.walletwise.data.repository.ReminderRepositoryImpl
import com.example.walletwise.data.time.AndroidReminderDateTimeProvider
import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.ReconcileReminderSchedulingUseCase
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val userId = intent.getStringExtra(AndroidReminderPlatform.EXTRA_USER_ID).orEmpty()
        val reminderId = intent.getStringExtra(AndroidReminderPlatform.EXTRA_REMINDER_ID).orEmpty()
        if (userId.isBlank() || reminderId.isBlank()) return
        if (FirebaseAuth.getInstance().currentUser?.uid != userId) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ReminderRepositoryImpl()
                val platform = AndroidReminderPlatform(appContext)
                val reconcile = ReconcileReminderSchedulingUseCase(
                    platform,
                    platform,
                    AndroidReminderDateTimeProvider()
                )
                when (val result = repository.getReminders(userId)) {
                    is RepositoryResult.Success -> {
                        val reminder = result.value.firstOrNull { it.id == reminderId }
                        if (reminder?.isEnabled == true &&
                            FirebaseAuth.getInstance().currentUser?.uid == userId
                        ) {
                            showReminder(appContext, reminder)
                            reconcile.apply(reminder, force = true)
                        } else {
                            platform.cancel(reminderId)
                        }
                    }
                    is RepositoryResult.Failure -> {
                        // Preserve a future occurrence during a temporary outage,
                        // but do not display unverified Firestore data.
                        fallbackReminder(intent, userId, reminderId)?.let {
                            reconcile.apply(it, force = true)
                        }
                        Log.e("REMINDER_RECEIVER", result.error.message)
                    }
                }
            } catch (error: Throwable) {
                Log.e("REMINDER_RECEIVER", "Unable to process reminder $reminderId", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showReminder(context: Context, reminder: Reminder) {
        NotificationHelper.showNotification(
            context,
            AndroidReminderPlatform.stableRequestCode(reminder.id),
            "WalletWise - ${reminder.title.ifBlank { "Lời nhắc" }}",
            reminder.note.ifBlank { "Đừng quên ghi lại các khoản thu chi của bạn!" }
        )
    }

    private fun fallbackReminder(intent: Intent, userId: String, reminderId: String): Reminder? {
        val reminder = Reminder(
            id = reminderId,
            userId = userId,
            title = intent.getStringExtra(AndroidReminderPlatform.EXTRA_TITLE).orEmpty(),
            note = intent.getStringExtra(AndroidReminderPlatform.EXTRA_MESSAGE).orEmpty(),
            frequency = intent.getStringExtra(AndroidReminderPlatform.EXTRA_FREQUENCY).orEmpty(),
            startDate = intent.getStringExtra(AndroidReminderPlatform.EXTRA_START_DATE).orEmpty(),
            time = intent.getStringExtra(AndroidReminderPlatform.EXTRA_TIME).orEmpty(),
            isEnabled = true
        )
        return reminder.takeIf {
            it.frequency.isNotBlank() && it.startDate.isNotBlank() && it.time.isNotBlank()
        }
    }
}
