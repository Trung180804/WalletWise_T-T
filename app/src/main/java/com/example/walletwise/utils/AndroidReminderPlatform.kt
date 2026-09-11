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
import com.example.walletwise.domain.service.ReminderPermission
import com.example.walletwise.domain.service.ReminderPermissionGateway
import com.example.walletwise.domain.service.ReminderPermissionState
import com.example.walletwise.domain.service.ReminderPlatformScheduleResult
import com.example.walletwise.domain.service.ReminderPlatformScheduler
import com.example.walletwise.domain.service.ReminderScheduleRequest
import java.time.LocalDateTime
import java.time.ZoneId

class AndroidReminderPlatform(
    context: Context? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ReminderPlatformScheduler, ReminderPermissionGateway {
    private var appContext: Context? = context?.applicationContext

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    override suspend fun schedule(request: ReminderScheduleRequest): ReminderPlatformScheduleResult {
        val context = appContext
            ?: return ReminderPlatformScheduleResult.Failure("Android reminder scheduler chưa được khởi tạo")
        val reminder = request.reminder
        if (!reminder.isEnabled || reminder.id.isBlank() || reminder.userId.isBlank()) {
            return ReminderPlatformScheduleResult.Failure("Dữ liệu lời nhắc không hợp lệ")
        }
        val at = request.occurrence.at
        val localDateTime = runCatching {
            LocalDateTime.of(
                at.date.year,
                at.date.month,
                at.date.dayOfMonth,
                at.time.hour,
                at.time.minute
            )
        }.getOrElse {
            return ReminderPlatformScheduleResult.Failure("Thời gian lời nhắc không hợp lệ")
        }
        // java.time resolves a DST gap forward and uses the earlier offset in
        // an overlap. Reconciliation on timezone/time changes recalculates it.
        val triggerAtMillis = localDateTime.atZone(zoneId).toInstant().toEpochMilli()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, request)
        val exactAlarmPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        val canUseExact = AndroidSchedulingContract.shouldUseExactAlarm(
            sdkInt = Build.VERSION.SDK_INT,
            exactAlarmPermissionApi = Build.VERSION_CODES.S,
            canScheduleExactAlarms = exactAlarmPermissionGranted
        )
        val outcome = dispatchAlarm(
            canUseExact = canUseExact,
            setExact = {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            },
            setInexact = {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        )
        return outcome.exact?.let(ReminderPlatformScheduleResult::Scheduled)
            ?: ReminderPlatformScheduleResult.Failure(outcome.error?.message ?: "Không thể đặt lịch nhắc")
    }

    override suspend fun cancel(reminderId: String): ReminderPlatformScheduleResult {
        val context = appContext
            ?: return ReminderPlatformScheduleResult.Failure("Android reminder scheduler chưa được khởi tạo")
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                stableRequestCode(reminderId),
                alarmIntent(context, reminderId),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
            ReminderPlatformScheduleResult.Cancelled
        } catch (error: Throwable) {
            ReminderPlatformScheduleResult.Failure(error.message ?: "Không thể hủy lịch nhắc")
        }
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

    private fun pendingIntent(context: Context, request: ReminderScheduleRequest): PendingIntent {
        val reminder = request.reminder
        return PendingIntent.getBroadcast(
            context,
            stableRequestCode(reminder.id),
            alarmIntent(context, reminder.id).apply {
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
    }

    companion object {
        const val EXTRA_USER_ID = "reminder_user_id"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TITLE = "reminder_title"
        const val EXTRA_MESSAGE = "reminder_message"
        const val EXTRA_FREQUENCY = "reminder_frequency"
        const val EXTRA_START_DATE = "reminder_start_date"
        const val EXTRA_TIME = "reminder_time"

        fun stableRequestCode(reminderId: String): Int =
            AndroidSchedulingContract.stableRequestCode(reminderId)
    }

    private fun alarmIntent(context: Context, reminderId: String): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            action = AndroidSchedulingContract.ACTION_REMINDER_ALARM
            data = Uri.parse(AndroidSchedulingContract.reminderData(reminderId))
        }
}
