package com.example.walletwise.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Restores reminders and recurring transactions after time/device changes. */
class RecurringBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                restoreRecurringTransactions(context.applicationContext, userId)
                restoreReminders(context.applicationContext, userId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun restoreRecurringTransactions(context: Context, userId: String) {
        try {
            val recurring = FirebaseFirestore.getInstance().collection("users").document(userId)
                .collection("recurring_transactions").get().await()
                .documents
                .map { document ->
                    SettingsFirestoreMapper.recurringFromMap(
                        documentId = document.id,
                        ownerUserId = userId,
                        data = document.data.orEmpty()
                    )
                }
            recurring.filter { it.isEnabled }.forEach { rule ->
                try {
                    val result = RecurringTransactionExecutor.executeIfDue(context, userId, rule.id)
                    result.recurring?.let { RecurringTransactionScheduler.schedule(context, it) }
                    if (result.transactionWasCreated) {
                        NotificationHelper.showNotification(
                            context,
                            rule.id.hashCode(),
                            "WalletWise - Giao dịch định kỳ",
                            "Đã tự động thêm giao dịch: ${rule.title}"
                        )
                    }
                } catch (error: Exception) {
                    // Do not permanently lose a due occurrence when Firestore
                    // is temporarily offline during a boot/time-change event.
                    RecurringTransactionScheduler.scheduleRetry(
                        context,
                        userId,
                        rule.id,
                        retryAttempt = 1
                    )
                    android.util.Log.e(
                        "RECURRING_BOOT",
                        "Unable to reconcile recurring transaction ${rule.id}",
                        error
                    )
                }
            }
        } catch (error: Exception) {
            android.util.Log.e("RECURRING_BOOT", "Unable to restore recurring transactions", error)
        }
    }

    private suspend fun restoreReminders(context: Context, userId: String) {
        try {
            FirebaseFirestore.getInstance().collection("users").document(userId)
                .collection("reminders").get().await()
                .documents
                .map { document ->
                    SettingsFirestoreMapper.reminderFromMap(
                        documentId = document.id,
                        ownerUserId = userId,
                        data = document.data.orEmpty()
                    )
                }
                .forEach { reminder ->
                    if (reminder.isEnabled) {
                        ReminderScheduler.schedule(context, reminder)
                    } else {
                        ReminderScheduler.cancel(context, reminder.id)
                    }
                }
        } catch (error: Exception) {
            android.util.Log.e("REMINDER_BOOT", "Unable to restore reminders", error)
        }
    }

    private companion object {
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED
        )
    }
}
