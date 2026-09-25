package com.example.walletwise.reliability

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_DAILY
import com.example.walletwise.domain.model.RECURRING_TIMES_UNLIMITED
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.service.RecurringDateTimeProvider
import com.example.walletwise.domain.service.RecurringOccurrence
import com.example.walletwise.domain.service.RecurringPlatformScheduleResult
import com.example.walletwise.domain.service.RecurringScheduleRequest
import com.example.walletwise.domain.service.ReminderLocalDate
import com.example.walletwise.domain.service.ReminderLocalDateTime
import com.example.walletwise.domain.service.ReminderLocalTime
import com.example.walletwise.domain.service.ReminderOccurrence
import com.example.walletwise.domain.service.ReminderPlatformScheduleResult
import com.example.walletwise.domain.service.ReminderScheduleRequest
import com.example.walletwise.utils.AndroidRecurringPlatform
import com.example.walletwise.utils.AndroidReminderPlatform
import com.example.walletwise.utils.AndroidSchedulingContract
import com.example.walletwise.utils.NotificationHelper
import com.example.walletwise.utils.RecurringTransactionReceiver
import com.example.walletwise.utils.ReminderReceiver
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Device-side checks for the real AlarmManager and NotificationManager adapters. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AndroidRuntimeReliabilityTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun a_reminderAlarmMatchesExactPermissionAndCancellationIdentity() = runBlocking {
        val reminderId = "runtime-reminder"
        val platform = AndroidReminderPlatform(context)
        val outcome = platform.schedule(
            ReminderScheduleRequest(
                reminder = reminder(reminderId),
                occurrence = ReminderOccurrence(0L, futureLocalDateTime())
            )
        )

        assertTrue(outcome is ReminderPlatformScheduleResult.Scheduled)
        val exact = (outcome as ReminderPlatformScheduleResult.Scheduled).exact
        assertEquals(canScheduleExactAlarms(), exact)
        assertNotNull(reminderPendingIntent(reminderId))

        assertEquals(ReminderPlatformScheduleResult.Cancelled, platform.cancel(reminderId))
        assertNull(reminderPendingIntent(reminderId))
    }

    @Test
    fun b_recurringRescheduleKeepsOneStablePendingIntentAndCancelsIt() = runBlocking {
        val recurringId = "runtime-recurring"
        val at = futureLocalDateTime()
        val provider = object : RecurringDateTimeProvider {
            override fun currentLocalDateTime(): ReminderLocalDateTime = at

            override fun toEpochMilliseconds(dateTime: ReminderLocalDateTime): Long =
                LocalDateTime.of(
                    dateTime.date.year,
                    dateTime.date.month,
                    dateTime.date.dayOfMonth,
                    dateTime.time.hour,
                    dateTime.time.minute
                ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            override fun currentEpochMilliseconds(): Long = System.currentTimeMillis()
        }
        val platform = AndroidRecurringPlatform(provider, context)
        val request = RecurringScheduleRequest(recurring(recurringId), RecurringOccurrence(0L, at))

        val first = platform.schedule(request)
        val second = platform.schedule(request)
        assertTrue(first is RecurringPlatformScheduleResult.Scheduled)
        assertTrue(second is RecurringPlatformScheduleResult.Scheduled)
        assertEquals(canScheduleExactAlarms(), (first as RecurringPlatformScheduleResult.Scheduled).exact)
        assertEquals(first.exact, (second as RecurringPlatformScheduleResult.Scheduled).exact)
        assertNotNull(recurringPendingIntent(recurringId))

        assertEquals(RecurringPlatformScheduleResult.Cancelled, platform.cancel(recurringId))
        assertNull(recurringPendingIntent(recurringId))
    }

    @Test
    fun c_notificationPostingMatchesRuntimePermission() {
        val notificationId = 40_401
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)

        NotificationHelper.showNotification(context, notificationId, "WalletWise runtime test", "local AVD only")

        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        val wasPosted = manager.activeNotifications.any { it.id == notificationId }
        assertEquals(permissionGranted, wasPosted)
        manager.cancel(notificationId)
    }

    @Test
    fun p_scheduleAlarmForProcessRecreation() = runBlocking {
        val platform = AndroidReminderPlatform(context)
        val outcome = platform.schedule(
            ReminderScheduleRequest(
                reminder = reminder(PROCESS_RECREATION_REMINDER_ID),
                occurrence = ReminderOccurrence(0L, futureLocalDateTime())
            )
        )
        assertTrue(outcome is ReminderPlatformScheduleResult.Scheduled)
        assertNotNull(reminderPendingIntent(PROCESS_RECREATION_REMINDER_ID))
    }

    @Test
    fun z_cleanupProcessRecreationAlarm() = runBlocking {
        val platform = AndroidReminderPlatform(context)
        platform.cancel(PROCESS_RECREATION_REMINDER_ID)
        assertNull(reminderPendingIntent(PROCESS_RECREATION_REMINDER_ID))
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }

    private fun reminderPendingIntent(reminderId: String): PendingIntent? = PendingIntent.getBroadcast(
        context,
        AndroidReminderPlatform.stableRequestCode(reminderId),
        Intent(context, ReminderReceiver::class.java).apply {
            action = AndroidSchedulingContract.ACTION_REMINDER_ALARM
            data = Uri.parse(AndroidSchedulingContract.reminderData(reminderId))
        },
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )

    private fun recurringPendingIntent(recurringId: String): PendingIntent? = PendingIntent.getBroadcast(
        context,
        AndroidRecurringPlatform.stableRequestCode(recurringId),
        Intent(context, RecurringTransactionReceiver::class.java).apply {
            action = AndroidSchedulingContract.ACTION_RECURRING_ALARM
            data = Uri.parse(AndroidSchedulingContract.recurringData(recurringId))
        },
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )

    private fun futureLocalDateTime(): ReminderLocalDateTime {
        val future = LocalDateTime.now().plusHours(6)
        return ReminderLocalDateTime(
            ReminderLocalDate(future.year, future.monthValue, future.dayOfMonth),
            ReminderLocalTime(future.hour, future.minute)
        )
    }

    private fun reminder(id: String) = Reminder(
        id = id,
        userId = "runtime-user",
        title = "Runtime reminder",
        frequency = "Hàng ngày",
        startDate = "11 thg 9, 2026",
        time = "20:15",
        note = "AVD-only reliability check",
        isEnabled = true
    )

    private fun recurring(id: String) = RecurringTransaction(
        id = id,
        userId = "runtime-user",
        title = "Runtime recurring",
        amount = 1.0,
        type = "Chi",
        category = "Hóa đơn",
        paymentMethod = "Tiền mặt",
        frequency = RECURRING_FREQUENCY_DAILY,
        timesCount = RECURRING_TIMES_UNLIMITED,
        startDate = "11 thg 9, 2026",
        time = "20:15",
        note = "AVD-only reliability check"
    )

    private companion object {
        const val PROCESS_RECREATION_REMINDER_ID = "runtime-process-recreation"
    }
}
