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
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.walletwise.BuildConfig
import com.example.walletwise.data.mapper.FirestoreSchema
import com.example.walletwise.data.repository.RecurringTransactionRepositoryImpl
import com.example.walletwise.data.repository.ReminderRepositoryImpl
import com.example.walletwise.data.time.AndroidRecurringDateTimeProvider
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_DAILY
import com.example.walletwise.domain.model.RECURRING_TIMES_UNLIMITED
import com.example.walletwise.domain.model.REMINDER_FREQUENCY_DAILY
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.RecurringPlatformScheduleResult
import com.example.walletwise.domain.service.RecurringScheduleCalculator
import com.example.walletwise.domain.service.RecurringScheduleRequest
import com.example.walletwise.domain.service.ReminderLocalDate
import com.example.walletwise.domain.service.ReminderLocalDateTime
import com.example.walletwise.domain.service.ReminderLocalTime
import com.example.walletwise.domain.service.ReminderOccurrence
import com.example.walletwise.domain.service.ReminderPlatformScheduleResult
import com.example.walletwise.domain.service.ReminderScheduleCalculator
import com.example.walletwise.domain.service.ReminderScheduleRequest
import com.example.walletwise.testing.DebugFirebaseBootstrap
import com.example.walletwise.utils.AndroidRecurringPlatform
import com.example.walletwise.utils.AndroidReminderPlatform
import com.example.walletwise.utils.AndroidSchedulingContract
import com.example.walletwise.utils.RecurringTransactionReceiver
import com.example.walletwise.utils.ReminderReceiver
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TimeZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Multi-process/runtime phases orchestrated from the host against one disposable AVD. */
class RuntimeRecoveryEmulatorTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()
    private val state
        get() = context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)

    @Before
    fun assertSafeDefaultFirebaseBootstrap() {
        assertTrue("Runtime recovery requires the explicit emulator build flag", BuildConfig.USE_FIREBASE_EMULATOR)
        assertTrue(BuildConfig.DEBUG)
        assertEquals(DebugFirebaseBootstrap.DEMO_PROJECT_ID, FirebaseApp.getInstance().options.projectId)
        val endpoint = DebugFirebaseBootstrap.endpoint()
        assertNotNull("Debug provider must configure Firebase before instrumentation", endpoint)
        assertEquals(DebugFirebaseBootstrap.EMULATOR_HOST, endpoint?.host)
        Log.i(
            LOG_TAG,
            "project=${FirebaseApp.getInstance().options.projectId} " +
                "Auth=${endpoint?.host}:${endpoint?.authPort} Firestore=${endpoint?.host}:${endpoint?.firestorePort}"
        )
    }

    @Test
    fun a_prepareRealRebootRecovery(): Unit = runBlocking {
        resetProjectAndSession()
        val userId = requireNotNull(auth.signInAnonymously().await().user).uid
        val now = LocalDateTime.now()
        val markedOccurrence = now.minusHours(1).withSecond(0).withNano(0)
        val nextOccurrence = markedOccurrence.plusDays(1)
        val marker = markedOccurrence.toLocalDate().toString()
        val reminder = reminder(REBOOT_REMINDER_ID, userId, nextOccurrence)
        val recurring = recurring(
            id = REBOOT_RECURRING_ID,
            userId = userId,
            firstOccurrence = markedOccurrence,
            lastExecutedDate = marker
        )

        assertSuccess(ReminderRepositoryImpl().addReminder(userId, reminder))
        assertSuccess(RecurringTransactionRepositoryImpl().addRecurringTransaction(userId, recurring))

        val reminderOccurrence = requireNotNull(
            ReminderScheduleCalculator.nextOccurrence(reminder, shared(now))
        )
        val recurringOccurrence = requireNotNull(
            RecurringScheduleCalculator.nextUnprocessedOccurrence(recurring, shared(now))
        )
        val reminderResult = AndroidReminderPlatform(context).schedule(
            ReminderScheduleRequest(reminder, reminderOccurrence)
        )
        val dateTimeProvider = AndroidRecurringDateTimeProvider()
        val recurringResult = AndroidRecurringPlatform(dateTimeProvider, context).schedule(
            RecurringScheduleRequest(recurring, recurringOccurrence)
        )
        assertTrue(reminderResult is ReminderPlatformScheduleResult.Scheduled)
        assertTrue(recurringResult is RecurringPlatformScheduleResult.Scheduled)
        assertNotNull(reminderPendingIntent(REBOOT_REMINDER_ID))
        assertNotNull(recurringPendingIntent(REBOOT_RECURRING_ID))

        state.edit()
            .putString(KEY_UID, userId)
            .putString(KEY_MARKER, marker)
            .putLong(KEY_INITIAL_RECURRING_EPOCH, dateTimeProvider.toEpochMilliseconds(recurringOccurrence.at))
            .putString(KEY_INITIAL_TIMEZONE, TimeZone.getDefault().id)
            .commit()
        Log.i(LOG_TAG, "Prepared reboot uid-preserved alarms without due transactions")
    }

    @Test
    fun b_verifyRealRebootRecovery(): Unit = runBlocking {
        val expectedUid = requiredStateString(KEY_UID)
        assertTrue("Firebase Auth session must survive reboot", auth.currentUser?.uid == expectedUid)
        waitForPendingIntent { reminderPendingIntent(REBOOT_REMINDER_ID) }
        waitForPendingIntent { recurringPendingIntent(REBOOT_RECURRING_ID) }
        assertEquals(0, transactions(expectedUid).get(Source.SERVER).await().size())
        assertEquals(
            requiredStateString(KEY_MARKER),
            recurringDocument(expectedUid, REBOOT_RECURRING_ID).get(Source.SERVER).await()
                .getString("lastExecutedDate")
        )
        Log.i(LOG_TAG, "Verified real reboot recovery with preserved session and transactionCount=0")
    }

    @Test
    fun c_verifyRealTimezoneRecovery(): Unit = runBlocking {
        val expectedTimezone = InstrumentationRegistry.getArguments().getString("expectedTimezone")
        assertFalse("expectedTimezone argument is required", expectedTimezone.isNullOrBlank())
        assertEquals(expectedTimezone, TimeZone.getDefault().id)
        val userId = requiredStateString(KEY_UID)
        assertTrue("Firebase Auth session must match the prepared test session", auth.currentUser?.uid == userId)
        waitForPendingIntent { reminderPendingIntent(REBOOT_REMINDER_ID) }
        waitForPendingIntent { recurringPendingIntent(REBOOT_RECURRING_ID) }

        val recurring = assertSuccess(RecurringTransactionRepositoryImpl().getRecurringTransactions(userId))
            .single { it.id == REBOOT_RECURRING_ID }
        val occurrence = requireNotNull(
            RecurringScheduleCalculator.nextUnprocessedOccurrence(
                recurring,
                AndroidRecurringDateTimeProvider().currentLocalDateTime()
            )
        )
        val shiftedEpoch = AndroidRecurringDateTimeProvider().toEpochMilliseconds(occurrence.at)
        assertNotEquals(state.getLong(KEY_INITIAL_RECURRING_EPOCH, Long.MIN_VALUE), shiftedEpoch)
        assertEquals(0, transactions(userId).get(Source.SERVER).await().size())
        assertEquals(
            requiredStateString(KEY_MARKER),
            recurringDocument(userId, REBOOT_RECURRING_ID).get(Source.SERVER).await()
                .getString("lastExecutedDate")
        )
        Log.i(LOG_TAG, "Verified timezone=$expectedTimezone shiftedEpoch=$shiftedEpoch without duplicate execution")
    }

    @Test
    fun c2_verifyRealTimeChangeRecovery(): Unit = runBlocking {
        val expectedMinimumEpoch = InstrumentationRegistry.getArguments()
            .getString("expectedMinimumEpochMillis")
            ?.toLongOrNull()
        assertNotNull("expectedMinimumEpochMillis argument is required", expectedMinimumEpoch)
        assertTrue(System.currentTimeMillis() >= requireNotNull(expectedMinimumEpoch))
        val userId = requiredStateString(KEY_UID)
        assertTrue("Firebase Auth session must match the prepared test session", auth.currentUser?.uid == userId)
        waitForPendingIntent { reminderPendingIntent(REBOOT_REMINDER_ID) }
        waitForPendingIntent { recurringPendingIntent(REBOOT_RECURRING_ID) }
        assertEquals(0, transactions(userId).get(Source.SERVER).await().size())
        assertEquals(
            requiredStateString(KEY_MARKER),
            recurringDocument(userId, REBOOT_RECURRING_ID).get(Source.SERVER).await()
                .getString("lastExecutedDate")
        )
        Log.i(LOG_TAG, "Verified real TIME_SET reconcile without duplicate execution")
    }

    @Test
    fun d_prepareDeniedNotificationReceiverAlarm(): Unit = runBlocking {
        clearScheduledRecoveryData()
        resetProjectAndSession()
        val userId = requireNotNull(auth.signInAnonymously().await().user).uid
        val recurring = dueRecurring(DENIED_RECURRING_ID, userId)
        assertSuccess(RecurringTransactionRepositoryImpl().addRecurringTransaction(userId, recurring))
        notificationManager().cancel(AndroidRecurringPlatform.stableRequestCode(recurring.id))
        scheduleRealReceiverAlarm(userId, recurring.id, delayMillis = 5_000L)
        state.edit()
            .putString(KEY_UID, userId)
            .putString(KEY_DENIED_OCCURRENCE, occurrenceKey(recurring))
            .commit()
        Log.i(LOG_TAG, "Scheduled denied-permission receiver alarm")
    }

    @Test
    fun e_verifyDeniedNotificationReceiverResult(): Unit = runBlocking {
        assertFalse(notificationPermissionGranted())
        val userId = requiredStateString(KEY_UID)
        val occurrence = requiredStateString(KEY_DENIED_OCCURRENCE)
        val transaction = waitForTransaction(userId, "${DENIED_RECURRING_ID}_$occurrence")
        assertNotNull(transaction)
        assertEquals(
            occurrence,
            recurringDocument(userId, DENIED_RECURRING_ID).get(Source.SERVER).await()
                .getString("lastExecutedDate")
        )
        assertNull(activeNotification(DENIED_RECURRING_ID))
        AndroidRecurringPlatform(AndroidRecurringDateTimeProvider(), context).cancel(DENIED_RECURRING_ID)
        Log.i(LOG_TAG, "Denied notification did not crash or roll back the transaction")
    }

    @Test
    fun f_prepareDozeReceiverAlarm(): Unit = runBlocking {
        assertTrue("POST_NOTIFICATIONS must be granted for display verification", notificationPermissionGranted())
        resetProjectAndSession()
        val userId = requireNotNull(auth.signInAnonymously().await().user).uid
        val recurring = dueRecurring(DOZE_RECURRING_ID, userId)
        assertSuccess(RecurringTransactionRepositoryImpl().addRecurringTransaction(userId, recurring))
        notificationManager().cancel(AndroidRecurringPlatform.stableRequestCode(recurring.id))
        scheduleRealReceiverAlarm(userId, recurring.id, delayMillis = 15_000L)
        state.edit()
            .putString(KEY_UID, userId)
            .putString(KEY_DOZE_OCCURRENCE, occurrenceKey(recurring))
            .commit()
        Log.i(LOG_TAG, "Scheduled exact receiver alarm for real Doze")
    }

    @Test
    fun g_verifyDozeCommitThenNotification(): Unit = runBlocking {
        val userId = requiredStateString(KEY_UID)
        val occurrence = requiredStateString(KEY_DOZE_OCCURRENCE)
        val transactionId = "${DOZE_RECURRING_ID}_$occurrence"
        val transaction = waitForTransaction(userId, transactionId)
        val marker = recurringDocument(userId, DOZE_RECURRING_ID).get(Source.SERVER).await()
            .getString("lastExecutedDate")
        assertEquals(occurrence, marker)
        assertEquals(1, transactions(userId).whereEqualTo("id", transactionId).get(Source.SERVER).await().size())
        val notification = requireNotNull(activeNotification(DOZE_RECURRING_ID))
        val transactionTimestamp = requireNotNull(transaction.getLong("timestamp"))
        assertTrue("Notification must be posted after the transaction timestamp", notification.postTime >= transactionTimestamp)
        state.edit().putLong(KEY_NOTIFICATION_POST_TIME, notification.postTime).commit()
        Log.i(LOG_TAG, "Verified atomic commit before notification")
    }

    @Test
    fun h_scheduleActualRetryForCompletedOccurrence() {
        scheduleRealReceiverAlarm(requiredStateString(KEY_UID), DOZE_RECURRING_ID, delayMillis = 5_000L)
    }

    @Test
    fun i_verifyRetryIsIdempotentAndDoesNotNotifyAgain(): Unit = runBlocking {
        val userId = requiredStateString(KEY_UID)
        val occurrence = requiredStateString(KEY_DOZE_OCCURRENCE)
        val transactionId = "${DOZE_RECURRING_ID}_$occurrence"
        delay(1_000L)
        assertEquals(1, transactions(userId).whereEqualTo("id", transactionId).get(Source.SERVER).await().size())
        assertEquals(
            occurrence,
            recurringDocument(userId, DOZE_RECURRING_ID).get(Source.SERVER).await()
                .getString("lastExecutedDate")
        )
        assertEquals(state.getLong(KEY_NOTIFICATION_POST_TIME, -1L), activeNotification(DOZE_RECURRING_ID)?.postTime)
        Log.i(LOG_TAG, "Verified retry kept one transaction and did not repost notification")
    }

    @Test
    fun j_prepareCommitFailureReceiverAlarm(): Unit = runBlocking {
        val userId = requiredStateString(KEY_UID)
        val recurring = dueRecurring(FAILURE_RECURRING_ID, userId)
        assertSuccess(RecurringTransactionRepositoryImpl().addRecurringTransaction(userId, recurring))
        notificationManager().cancel(AndroidRecurringPlatform.stableRequestCode(recurring.id))
        scheduleRealReceiverAlarm(userId, recurring.id, delayMillis = 5_000L)
        state.edit().putString(KEY_FAILURE_OCCURRENCE, occurrenceKey(recurring)).commit()
        Log.i(LOG_TAG, "Scheduled receiver alarm whose emulator transaction is denied by test rules")
    }

    @Test
    fun k_verifyCommitFailureHasNoNotificationAndSchedulesRetry(): Unit = runBlocking {
        val userId = requiredStateString(KEY_UID)
        val occurrence = requiredStateString(KEY_FAILURE_OCCURRENCE)
        delay(1_000L)
        assertFalse(
            transactions(userId).document("${FAILURE_RECURRING_ID}_$occurrence")
                .get(Source.SERVER).await().exists()
        )
        assertEquals(
            "",
            recurringDocument(userId, FAILURE_RECURRING_ID).get(Source.SERVER).await()
                .getString("lastExecutedDate")
        )
        assertNull(activeNotification(FAILURE_RECURRING_ID))
        assertNotNull("Repository failure must schedule the bounded retry", recurringPendingIntent(FAILURE_RECURRING_ID))
        AndroidRecurringPlatform(AndroidRecurringDateTimeProvider(), context).cancel(FAILURE_RECURRING_ID)
        Log.i(LOG_TAG, "Verified commit failure: no marker, transaction, or success notification; retry existed")
    }

    @Test
    fun z_cleanupRuntimeRecoveryData(): Unit = runBlocking {
        clearScheduledRecoveryData()
        notificationManager().cancelAll()
        auth.signOut()
        clearProjectData()
        state.edit().clear().commit()
        Log.i(LOG_TAG, "Runtime recovery data cleaned")
    }

    private suspend fun resetProjectAndSession() {
        auth.signOut()
        clearProjectData()
        notificationManager().cancelAll()
    }

    private suspend fun clearScheduledRecoveryData() {
        AndroidReminderPlatform(context).cancel(REBOOT_REMINDER_ID)
        val recurringPlatform = AndroidRecurringPlatform(AndroidRecurringDateTimeProvider(), context)
        listOf(REBOOT_RECURRING_ID, DENIED_RECURRING_ID, DOZE_RECURRING_ID, FAILURE_RECURRING_ID)
            .forEach { recurringPlatform.cancel(it) }
    }

    private fun scheduleRealReceiverAlarm(userId: String, recurringId: String, delayMillis: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertTrue("Exact-alarm access is required for deterministic runtime phases", manager.canScheduleExactAlarms())
        manager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + delayMillis,
            recurringPendingIntent(userId, recurringId, retryAttempt = 0)
        )
    }

    private fun recurringPendingIntent(userId: String, recurringId: String, retryAttempt: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            AndroidRecurringPlatform.stableRequestCode(recurringId),
            Intent(context, RecurringTransactionReceiver::class.java).apply {
                action = AndroidSchedulingContract.ACTION_RECURRING_ALARM
                data = Uri.parse(AndroidSchedulingContract.recurringData(recurringId))
                putExtra(AndroidRecurringPlatform.EXTRA_USER_ID, userId)
                putExtra(AndroidRecurringPlatform.EXTRA_RECURRING_ID, recurringId)
                putExtra(AndroidRecurringPlatform.EXTRA_RETRY_ATTEMPT, retryAttempt)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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

    private fun reminderPendingIntent(reminderId: String): PendingIntent? = PendingIntent.getBroadcast(
        context,
        AndroidReminderPlatform.stableRequestCode(reminderId),
        Intent(context, ReminderReceiver::class.java).apply {
            action = AndroidSchedulingContract.ACTION_REMINDER_ALARM
            data = Uri.parse(AndroidSchedulingContract.reminderData(reminderId))
        },
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )

    private suspend fun waitForPendingIntent(block: () -> PendingIntent?) {
        withTimeout(20_000L) {
            while (block() == null) delay(100L)
        }
    }

    private suspend fun waitForTransaction(userId: String, transactionId: String): DocumentSnapshot =
        withTimeout(20_000L) {
            var snapshot = transactions(userId).document(transactionId).get(Source.SERVER).await()
            while (!snapshot.exists()) {
                delay(200L)
                snapshot = transactions(userId).document(transactionId).get(Source.SERVER).await()
            }
            snapshot
        }

    private fun activeNotification(recurringId: String) = notificationManager().activeNotifications
        .firstOrNull { it.id == AndroidRecurringPlatform.stableRequestCode(recurringId) }

    private fun notificationManager() =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun notificationPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun transactions(userId: String) = firestore.collection(FirestoreSchema.USERS)
        .document(userId)
        .collection(FirestoreSchema.TRANSACTIONS)

    private fun recurringDocument(userId: String, recurringId: String) = firestore
        .collection(FirestoreSchema.USERS)
        .document(userId)
        .collection(FirestoreSchema.RECURRING_TRANSACTIONS)
        .document(recurringId)

    private fun reminder(id: String, userId: String, occurrence: LocalDateTime) = Reminder(
        id = id,
        userId = userId,
        title = "4J reboot reminder",
        frequency = REMINDER_FREQUENCY_DAILY,
        startDate = formatDate(occurrence.toLocalDate()),
        time = formatTime(occurrence),
        note = "disposable AVD only",
        isEnabled = true
    )

    private fun recurring(
        id: String,
        userId: String,
        firstOccurrence: LocalDateTime,
        lastExecutedDate: String = ""
    ) = RecurringTransaction(
        id = id,
        userId = userId,
        title = "4J runtime recurring",
        amount = 4.0,
        type = "Chi",
        category = "Runtime",
        paymentMethod = "Cash",
        frequency = RECURRING_FREQUENCY_DAILY,
        timesCount = RECURRING_TIMES_UNLIMITED,
        startDate = formatDate(firstOccurrence.toLocalDate()),
        time = formatTime(firstOccurrence),
        note = "disposable AVD only",
        lastExecutedDate = lastExecutedDate,
        isEnabled = true
    )

    private fun dueRecurring(id: String, userId: String): RecurringTransaction =
        recurring(id, userId, LocalDateTime.now().minusMinutes(1).withSecond(0).withNano(0))

    private fun occurrenceKey(recurring: RecurringTransaction): String {
        val occurrence = requireNotNull(
            RecurringScheduleCalculator.latestDueOccurrence(
                recurring,
                AndroidRecurringDateTimeProvider().currentLocalDateTime()
            )
        )
        return RecurringScheduleCalculator.occurrenceKey(occurrence)
    }

    private fun formatDate(date: LocalDate): String = ReminderScheduleCalculator.formatStartDate(
        ReminderLocalDate(date.year, date.monthValue, date.dayOfMonth)
    )

    private fun formatTime(dateTime: LocalDateTime): String = ReminderScheduleCalculator.formatTime(
        ReminderLocalTime(dateTime.hour, dateTime.minute)
    )

    private fun shared(dateTime: LocalDateTime) = ReminderLocalDateTime(
        ReminderLocalDate(dateTime.year, dateTime.monthValue, dateTime.dayOfMonth),
        ReminderLocalTime(dateTime.hour, dateTime.minute)
    )

    private fun requiredStateString(key: String): String =
        requireNotNull(state.getString(key, null)) { "Missing runtime state: $key" }

    private fun clearProjectData() {
        delete("http://${DebugFirebaseBootstrap.EMULATOR_HOST}:${DebugFirebaseBootstrap.FIRESTORE_PORT}/emulator/v1/projects/${DebugFirebaseBootstrap.DEMO_PROJECT_ID}/databases/(default)/documents")
        delete("http://${DebugFirebaseBootstrap.EMULATOR_HOST}:${DebugFirebaseBootstrap.AUTH_PORT}/emulator/v1/projects/${DebugFirebaseBootstrap.DEMO_PROJECT_ID}/accounts")
    }

    private fun delete(endpoint: String) {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "DELETE"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            val status = connection.responseCode
            assertTrue("Unable to clear emulator endpoint: HTTP $status", status in 200..299)
        } finally {
            connection.disconnect()
        }
    }

    private fun <T> assertSuccess(result: RepositoryResult<T>): T {
        assertTrue("Expected repository success, got $result", result is RepositoryResult.Success)
        return (result as RepositoryResult.Success).value
    }

    private companion object {
        const val LOG_TAG = "RUNTIME_RECOVERY"
        const val STATE_FILE = "checkpoint_04j_runtime"
        const val REBOOT_REMINDER_ID = "runtime-reboot-reminder"
        const val REBOOT_RECURRING_ID = "runtime-reboot-recurring"
        const val DENIED_RECURRING_ID = "runtime-denied"
        const val DOZE_RECURRING_ID = "runtime-doze"
        const val FAILURE_RECURRING_ID = "runtime-failure"
        const val KEY_UID = "uid"
        const val KEY_MARKER = "marker"
        const val KEY_INITIAL_RECURRING_EPOCH = "initial_recurring_epoch"
        const val KEY_INITIAL_TIMEZONE = "initial_timezone"
        const val KEY_DENIED_OCCURRENCE = "denied_occurrence"
        const val KEY_DOZE_OCCURRENCE = "doze_occurrence"
        const val KEY_FAILURE_OCCURRENCE = "failure_occurrence"
        const val KEY_NOTIFICATION_POST_TIME = "notification_post_time"
    }
}
