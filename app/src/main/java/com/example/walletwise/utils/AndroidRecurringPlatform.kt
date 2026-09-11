package com.example.walletwise.utils

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.service.RecurringDateTimeProvider
import com.example.walletwise.domain.service.RecurringExecutionNotifier
import com.example.walletwise.domain.service.RecurringPlatformScheduleResult
import com.example.walletwise.domain.service.RecurringPlatformScheduler
import com.example.walletwise.domain.service.RecurringScheduleRequest
import com.example.walletwise.domain.service.ReminderPermission
import com.example.walletwise.domain.service.ReminderPermissionGateway
import com.example.walletwise.domain.service.ReminderPermissionState

class AndroidRecurringPlatform(
    private val dateTimeProvider: RecurringDateTimeProvider,
    context: Context? = null
) : RecurringPlatformScheduler, RecurringExecutionNotifier, ReminderPermissionGateway {
    private var appContext: Context? = context?.applicationContext

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    override suspend fun schedule(request: RecurringScheduleRequest): RecurringPlatformScheduleResult {
        val context = appContext
            ?: return RecurringPlatformScheduleResult.Failure("Android recurring scheduler chưa được khởi tạo")
        val recurring = request.recurring
        if (!recurring.isEnabled || recurring.id.isBlank() || recurring.userId.isBlank()) {
            return RecurringPlatformScheduleResult.Failure("Dữ liệu giao dịch định kỳ không hợp lệ")
        }
        val occurrenceMillis = runCatching {
            dateTimeProvider.toEpochMilliseconds(request.occurrence.at)
        }.getOrElse {
            return RecurringPlatformScheduleResult.Failure("Thời gian giao dịch định kỳ không hợp lệ")
        }
        val triggerAtMillis = maxOf(occurrenceMillis, System.currentTimeMillis() + IMMEDIATE_DELAY_MILLIS)
        return setAlarm(
            context,
            triggerAtMillis,
            pendingIntent(context, recurring.userId, recurring.id, retryAttempt = 0)
        )
    }

    override suspend fun scheduleRetry(
        userId: String,
        recurringId: String,
        retryAttempt: Int
    ): RecurringPlatformScheduleResult {
        val context = appContext
            ?: return RecurringPlatformScheduleResult.Failure("Android recurring scheduler chưa được khởi tạo")
        if (userId.isBlank() || recurringId.isBlank() || retryAttempt !in 1..MAX_RETRY_ATTEMPTS) {
            return RecurringPlatformScheduleResult.Failure("Yêu cầu retry giao dịch định kỳ không hợp lệ")
        }
        val delayMinutes = 1L shl (retryAttempt - 1)
        return setAlarm(
            context,
            System.currentTimeMillis() + delayMinutes * 60_000L,
            pendingIntent(context, userId, recurringId, retryAttempt)
        )
    }

    override suspend fun cancel(recurringId: String): RecurringPlatformScheduleResult {
        val context = appContext
            ?: return RecurringPlatformScheduleResult.Failure("Android recurring scheduler chưa được khởi tạo")
        if (recurringId.isBlank()) {
            return RecurringPlatformScheduleResult.Failure("Recurring id is required")
        }
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                stableRequestCode(recurringId),
                alarmIntent(context, recurringId),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
            RecurringPlatformScheduleResult.Cancelled
        } catch (error: Throwable) {
            RecurringPlatformScheduleResult.Failure(error.message ?: "Không thể hủy lịch giao dịch định kỳ")
        }
    }

    override suspend fun transactionCreated(recurring: RecurringTransaction) {
        val context = appContext ?: return
        NotificationHelper.showNotification(
            context,
            stableRequestCode(recurring.id),
            "WalletWise - Giao dịch định kỳ",
            "Đã tự động thêm giao dịch ${recurring.type}: ${recurring.title} (${recurring.amount.toLong()}đ)"
        )
    }

    override fun permissionState(permission: ReminderPermission): ReminderPermissionState {
        val context = appContext ?: return ReminderPermissionState.DENIED
        return when (permission) {
            ReminderPermission.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    ReminderPermissionState.NOT_REQUIRED
                } else if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    ReminderPermissionState.GRANTED
                } else {
                    ReminderPermissionState.DENIED
                }
            }
            ReminderPermission.EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    ReminderPermissionState.NOT_REQUIRED
                } else {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    if (alarmManager.canScheduleExactAlarms()) {
                        ReminderPermissionState.GRANTED
                    } else {
                        ReminderPermissionState.DENIED
                    }
                }
            }
        }
    }

    private fun setAlarm(
        context: Context,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ): RecurringPlatformScheduleResult {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        return try {
            if (canUseExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                RecurringPlatformScheduleResult.Scheduled(exact = true)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                RecurringPlatformScheduleResult.Scheduled(exact = false)
            }
        } catch (exactError: SecurityException) {
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }.fold(
                onSuccess = { RecurringPlatformScheduleResult.Scheduled(exact = false) },
                onFailure = {
                    RecurringPlatformScheduleResult.Failure(
                        it.message ?: "Không thể đặt lịch giao dịch định kỳ"
                    )
                }
            )
        } catch (error: Throwable) {
            RecurringPlatformScheduleResult.Failure(error.message ?: "Không thể đặt lịch giao dịch định kỳ")
        }
    }

    private fun pendingIntent(
        context: Context,
        userId: String,
        recurringId: String,
        retryAttempt: Int
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        stableRequestCode(recurringId),
        alarmIntent(context, recurringId).apply {
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_RECURRING_ID, recurringId)
            putExtra(EXTRA_RETRY_ATTEMPT, retryAttempt)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val EXTRA_USER_ID = "recurring_user_id"
        const val EXTRA_RECURRING_ID = "recurring_id"
        const val EXTRA_RETRY_ATTEMPT = "recurring_retry_attempt"

        private const val MAX_RETRY_ATTEMPTS = 5
        private const val IMMEDIATE_DELAY_MILLIS = 250L
        private const val ACTION_RECURRING_ALARM = "com.example.walletwise.action.RECURRING_ALARM"

        fun stableRequestCode(recurringId: String): Int = recurringId.hashCode()
    }

    private fun alarmIntent(context: Context, recurringId: String): Intent =
        Intent(context, RecurringTransactionReceiver::class.java).apply {
            action = ACTION_RECURRING_ALARM
            data = Uri.parse("walletwise://recurring/${Uri.encode(recurringId)}")
        }
}
