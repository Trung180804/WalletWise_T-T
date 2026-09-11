package com.example.walletwise.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.walletwise.data.repository.RecurringTransactionRepositoryImpl
import com.example.walletwise.data.time.AndroidRecurringDateTimeProvider
import com.example.walletwise.domain.service.RecurringAutomationCoordinator
import com.example.walletwise.domain.service.RecurringProcessingStatus
import com.example.walletwise.domain.usecase.ExecuteRecurringIfDueUseCase
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecurringTransactionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val userId = intent.getStringExtra(AndroidRecurringPlatform.EXTRA_USER_ID).orEmpty()
        val recurringId = intent.getStringExtra(AndroidRecurringPlatform.EXTRA_RECURRING_ID).orEmpty()
        val retryAttempt = intent.getIntExtra(AndroidRecurringPlatform.EXTRA_RETRY_ATTEMPT, 0)
        if (userId.isBlank() || recurringId.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runBroadcastWork(finish = pendingResult::finish) {
                try {
                    // Do not let an alarm created for a previous signed-in account
                    // write into that account after someone else signs in.
                    if (FirebaseAuth.getInstance().currentUser?.uid != userId) return@runBroadcastWork

                    val repository = RecurringTransactionRepositoryImpl()
                    val dateTimeProvider = AndroidRecurringDateTimeProvider()
                    val platform = AndroidRecurringPlatform(dateTimeProvider, context.applicationContext)
                    val coordinator = RecurringAutomationCoordinator(
                        executeIfDue = ExecuteRecurringIfDueUseCase(repository, dateTimeProvider),
                        scheduler = platform,
                        notifier = platform,
                        dateTimeProvider = dateTimeProvider
                    )
                    val outcome = coordinator.process(userId, recurringId, retryAttempt)
                    if (outcome.status == RecurringProcessingStatus.FAILED) {
                        android.util.Log.e(
                            "RECURRING_RECEIVER",
                            outcome.repositoryError?.message ?: outcome.schedulingError ?: "Recurring processing failed"
                        )
                    }
                    outcome.notificationError?.let {
                        android.util.Log.w("RECURRING_RECEIVER", it)
                    }
                } catch (error: Exception) {
                    android.util.Log.e("RECURRING_RECEIVER", "Unable to run recurring transaction", error)
                }
            }
        }
    }
}
