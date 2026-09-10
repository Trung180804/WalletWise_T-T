package com.example.walletwise.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecurringTransactionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val userId = intent.getStringExtra(RecurringTransactionScheduler.EXTRA_USER_ID).orEmpty()
        val recurringId = intent.getStringExtra(RecurringTransactionScheduler.EXTRA_RECURRING_ID).orEmpty()
        val retryAttempt = intent.getIntExtra(RecurringTransactionScheduler.EXTRA_RETRY_ATTEMPT, 0)
        if (userId.isBlank() || recurringId.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Do not let an alarm created for a previous signed-in account
                // write into that account after someone else signs in.
                if (FirebaseAuth.getInstance().currentUser?.uid != userId) return@launch

                val result = RecurringTransactionExecutor.executeIfDue(context.applicationContext, userId, recurringId)
                result.recurring?.let { RecurringTransactionScheduler.schedule(context.applicationContext, it) }
                if (result.transactionWasCreated) {
                    NotificationHelper.showNotification(
                        context.applicationContext,
                        recurringId.hashCode(),
                        "WalletWise - Giao dịch định kỳ",
                        "Đã tự động thêm giao dịch: ${result.recurring?.title.orEmpty()}"
                    )
                }
            } catch (error: Exception) {
                // Do not lose this occurrence when the phone is temporarily
                // offline. Retry after 1, 2, 4, 8 and 16 minutes.
                RecurringTransactionScheduler.scheduleRetry(
                    context.applicationContext,
                    userId,
                    recurringId,
                    retryAttempt + 1
                )
                android.util.Log.e("RECURRING_RECEIVER", "Unable to run recurring transaction", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
