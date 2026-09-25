package com.example.walletwise.reliability

import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.example.walletwise.BuildConfig
import com.example.walletwise.data.mapper.FirestoreSchema
import com.example.walletwise.data.mapper.FirestoreWireMapper
import com.example.walletwise.data.repository.RecurringTransactionRepositoryImpl
import com.example.walletwise.data.repository.ReminderRepositoryImpl
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_DAILY
import com.example.walletwise.domain.model.RECURRING_TIMES_UNLIMITED
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.repository.RecurringExecutionResult
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.ReminderLocalDate
import com.example.walletwise.domain.service.ReminderLocalDateTime
import com.example.walletwise.domain.service.ReminderLocalTime
import com.example.walletwise.testing.FirebaseEmulatorEndpoint
import com.example.walletwise.testing.FirebaseEmulatorSafety
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Source
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Real Firebase SDK integration tests. They are skipped unless the debug APK
 * was built with USE_FIREBASE_EMULATOR=true. The suite uses its own named app;
 * the debug provider also guarantees that any default app is the demo project.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FirebaseReminderRecurringEmulatorTest {
    private var endpoint: FirebaseEmulatorEndpoint? = null
    private var firebaseApp: FirebaseApp? = null
    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var userId: String = ""

    @Before
    fun setUp() = runBlocking {
        assumeTrue("Build with USE_FIREBASE_EMULATOR=true", BuildConfig.USE_FIREBASE_EMULATOR)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val explicitHost = arguments.getString("firebaseEmulatorHost")?.takeIf(String::isNotBlank)
        val runningOnEmulator = isAndroidEmulator()
        val physicalDeviceWasApproved = arguments.getString("allowPhysicalTestDevice")
            ?.equals("true", ignoreCase = true) == true
        assumeTrue(
            "Physical-device execution requires an explicit host and allowPhysicalTestDevice=true",
            runningOnEmulator || (physicalDeviceWasApproved && explicitHost != null)
        )
        endpoint = FirebaseEmulatorSafety.resolve(
            requested = BuildConfig.USE_FIREBASE_EMULATOR,
            debuggable = BuildConfig.DEBUG,
            projectId = PROJECT_ID,
            host = explicitHost ?: "10.0.2.2",
            firestorePort = arguments.getString("firestoreEmulatorPort")?.toIntOrNull() ?: 8080,
            authPort = arguments.getString("authEmulatorPort")?.toIntOrNull() ?: 9099,
            runningOnAndroidEmulator = runningOnEmulator,
            hostWasExplicitlyConfigured = explicitHost != null
        )
        clearProjectData(requireNotNull(endpoint))

        firebaseApp = FirebaseApp.initializeApp(
            instrumentation.targetContext,
            firebaseOptions(),
            "checkpoint-04i-${System.nanoTime()}"
        )
        auth = FirebaseAuth.getInstance(requireNotNull(firebaseApp)).apply {
            useEmulator(requireNotNull(endpoint).host, requireNotNull(endpoint).authPort)
        }
        Log.i(
            EMULATOR_LOG_TAG,
            "Firebase Auth emulator=${requireNotNull(endpoint).host}:${requireNotNull(endpoint).authPort} " +
                "project=${requireNotNull(endpoint).projectId}"
        )
        firestore = FirebaseFirestore.getInstance(requireNotNull(firebaseApp)).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build()
            useEmulator(requireNotNull(endpoint).host, requireNotNull(endpoint).firestorePort)
        }
        Log.i(
            EMULATOR_LOG_TAG,
            "Firestore emulator=${requireNotNull(endpoint).host}:${requireNotNull(endpoint).firestorePort} " +
                "project=${requireNotNull(endpoint).projectId}"
        )
        userId = requireNotNull(requireAuth().signInAnonymously().await().user).uid
    }

    @After
    fun tearDown() = runBlocking {
        endpoint?.let { runCatching { clearProjectData(it) } }
        auth?.signOut()
        firebaseApp?.delete()
        firestore = null
        auth = null
        firebaseApp = null
        endpoint = null
        userId = ""
    }

    @Test
    fun a_concurrentClientsCreateOneDeterministicTransactionAndRetryIsIdempotent() = runBlocking {
        val recurring = recurring("atomic", RECURRING_TIMES_UNLIMITED)
        recurringRef(userId, recurring.id).set(FirestoreWireMapper.recurringToMap(recurring)).await()
        val repositories = List(12) {
            RecurringTransactionRepositoryImpl(requireFirestore(), requireAuth())
        }

        val outcomes = coroutineScope {
            repositories.map { repository ->
                async(Dispatchers.IO) {
                    repository.executeIfDue(userId, recurring.id, DUE_NOW, EXECUTED_AT).successValue()
                }
            }.awaitAll()
        }

        assertEquals(1, outcomes.count(RecurringExecutionResult::transactionWasCreated))
        assertTrue(outcomes.all { it.occurrenceKey == OCCURRENCE_KEY })
        val transactions = transactions(userId).get(Source.SERVER).await().documents
        assertEquals(listOf("atomic_$OCCURRENCE_KEY"), transactions.map { it.id })
        assertEquals(EXPECTED_TRANSACTION_FIELDS, transactions.single().data?.keys)
        assertEquals(userId, transactions.single().getString("userId"))
        assertEquals("atomic_$OCCURRENCE_KEY", transactions.single().getString("id"))
        val storedRecurring = recurringRef(userId, recurring.id).get(Source.SERVER).await()
        assertEquals(OCCURRENCE_KEY, storedRecurring.getString("lastExecutedDate"))

        val retry = repositories.first()
            .executeIfDue(userId, recurring.id, DUE_NOW, EXECUTED_AT)
            .successValue()
        assertFalse(retry.transactionWasCreated)
        assertEquals(1, transactions(userId).get(Source.SERVER).await().size())
    }

    @Test
    fun b_legacySingleOccurrenceStaysEnabledAndExistingTransactionRepairsMarkerAtomically() = runBlocking {
        val finite = recurring("finite", "1")
        recurringRef(userId, finite.id).set(FirestoreWireMapper.recurringToMap(finite)).await()
        val repository = RecurringTransactionRepositoryImpl(requireFirestore(), requireAuth())

        val finiteResult = repository.executeIfDue(userId, finite.id, DUE_NOW, EXECUTED_AT).successValue()
        assertTrue(finiteResult.transactionWasCreated)
        assertTrue(requireNotNull(finiteResult.recurring).isEnabled)
        val finiteStored = recurringRef(userId, finite.id).get(Source.SERVER).await()
        assertEquals(true, finiteStored.getBoolean("enabled"))
        assertEquals(OCCURRENCE_KEY, finiteStored.getString("lastExecutedDate"))

        val repair = recurring("repair", RECURRING_TIMES_UNLIMITED)
        recurringRef(userId, repair.id).set(FirestoreWireMapper.recurringToMap(repair)).await()
        val transactionId = "repair_$OCCURRENCE_KEY"
        transactions(userId).document(transactionId).set(
            FirestoreWireMapper.transactionToMap(
                Transaction(
                    id = transactionId,
                    userId = userId,
                    type = repair.type,
                    paymentMethod = repair.paymentMethod,
                    amount = repair.amount,
                    category = repair.category,
                    note = repair.note,
                    timestamp = EXECUTED_AT
                )
            )
        ).await()

        val repaired = repository.executeIfDue(userId, repair.id, DUE_NOW, EXECUTED_AT).successValue()
        assertFalse(repaired.transactionWasCreated)
        assertEquals(OCCURRENCE_KEY, repaired.occurrenceKey)
        assertEquals(
            OCCURRENCE_KEY,
            recurringRef(userId, repair.id).get(Source.SERVER).await().getString("lastExecutedDate")
        )
        assertEquals(1, transactions(userId).whereEqualTo("id", transactionId).get(Source.SERVER).await().size())
    }

    @Test
    fun c_disabledFutureAndChangedUidNeverWrite() = runBlocking {
        val repository = RecurringTransactionRepositoryImpl(requireFirestore(), requireAuth())
        val disabled = recurring("disabled", RECURRING_TIMES_UNLIMITED).copy(isEnabled = false)
        val future = recurring("future", RECURRING_TIMES_UNLIMITED).copy(startDate = "12 thg 9, 2026")
        recurringRef(userId, disabled.id).set(FirestoreWireMapper.recurringToMap(disabled)).await()
        recurringRef(userId, future.id).set(FirestoreWireMapper.recurringToMap(future)).await()

        assertFalse(repository.executeIfDue(userId, disabled.id, DUE_NOW, EXECUTED_AT).successValue().transactionWasCreated)
        assertFalse(repository.executeIfDue(userId, future.id, DUE_NOW, EXECUTED_AT).successValue().transactionWasCreated)
        assertEquals(0, transactions(userId).get(Source.SERVER).await().size())

        val oldUserId = userId
        requireAuth().signOut()
        userId = requireNotNull(requireAuth().signInAnonymously().await().user).uid
        val rejected = repository.executeIfDue(oldUserId, disabled.id, DUE_NOW, EXECUTED_AT)
        assertTrue(rejected is RepositoryResult.Failure)
        assertEquals(
            RepositoryErrorCode.NOT_AUTHENTICATED,
            (rejected as RepositoryResult.Failure).error.code
        )
        assertEquals(0, transactions(userId).get(Source.SERVER).await().size())
    }

    @Test
    fun d_offlineFailureBeforeCommitLeavesNoTransactionOrMarker() = runBlocking {
        val recurring = recurring("offline", RECURRING_TIMES_UNLIMITED)
        recurringRef(userId, recurring.id).set(FirestoreWireMapper.recurringToMap(recurring)).await()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val offlineApp = FirebaseApp.initializeApp(
            instrumentation.targetContext,
            firebaseOptions(),
            "checkpoint-04i-offline-${System.nanoTime()}"
        )
        val offlineFirestore = FirebaseFirestore.getInstance(requireNotNull(offlineApp)).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build()
            // A closed host-only port is a deterministic failure-before-commit.
            // It also makes falling through to a production endpoint impossible.
            useEmulator(requireNotNull(endpoint).host, UNREACHABLE_FIRESTORE_PORT)
        }
        val repository = RecurringTransactionRepositoryImpl(offlineFirestore, requireAuth())
        val result = try {
            repository.executeIfDue(userId, recurring.id, DUE_NOW, EXECUTED_AT)
        } finally {
            runCatching { offlineFirestore.terminate().await() }
            offlineApp.delete()
        }

        assertTrue("Expected offline failure, got $result", result is RepositoryResult.Failure)
        assertEquals(0, transactions(userId).get(Source.SERVER).await().size())
        assertEquals(
            "",
            recurringRef(userId, recurring.id).get(Source.SERVER).await().getString("lastExecutedDate")
        )
    }

    @Test
    fun e_reminderCrudToggleLegacyReadAndListenerCancellationUseCurrentSchema() = runBlocking {
        val repository = ReminderRepositoryImpl(requireFirestore())
        val reminder = reminder("reminder")
        assertTrue(repository.addReminder(userId, reminder) is RepositoryResult.Success)
        val stored = reminderRef(userId, reminder.id).get(Source.SERVER).await()
        assertEquals(EXPECTED_REMINDER_FIELDS, stored.data?.keys)
        assertEquals(true, stored.getBoolean("enabled"))
        assertFalse(stored.data.orEmpty().containsKey("isEnabled"))

        val updated = reminder.copy(title = "Updated reminder", note = "Updated note")
        assertTrue(repository.updateReminder(userId, updated) is RepositoryResult.Success)
        assertEquals(
            "Updated reminder",
            reminderRef(userId, reminder.id).get(Source.SERVER).await().getString("title")
        )

        assertTrue(repository.setReminderEnabled(userId, reminder.id, false) is RepositoryResult.Success)
        assertEquals(false, reminderRef(userId, reminder.id).get(Source.SERVER).await().getBoolean("enabled"))
        assertTrue(repository.setReminderEnabled(userId, reminder.id, true) is RepositoryResult.Success)
        assertEquals(true, reminderRef(userId, reminder.id).get(Source.SERVER).await().getBoolean("enabled"))

        reminderRef(userId, reminder.id).set(
            mapOf(
                "id" to reminder.id,
                "userId" to userId,
                "title" to reminder.title,
                "frequency" to reminder.frequency,
                "startDate" to reminder.startDate,
                "time" to reminder.time,
                "note" to reminder.note,
                "isEnabled" to true
            )
        ).await()
        assertTrue(repository.getReminders(userId).successValue().single().isEnabled)

        val emissions = AtomicInteger()
        val listener = launch(Dispatchers.IO) {
            repository.observeReminders(userId).collect { emissions.incrementAndGet() }
        }
        withTimeout(5_000L) {
            while (emissions.get() == 0) delay(25L)
        }
        listener.cancelAndJoin()
        val emissionsAfterCancel = emissions.get()
        reminderRef(userId, reminder.id).update("title", "after-cancel").await()
        delay(300L)
        assertEquals(emissionsAfterCancel, emissions.get())

        assertTrue(repository.deleteReminder(userId, reminder.id) is RepositoryResult.Success)
        assertFalse(reminderRef(userId, reminder.id).get(Source.SERVER).await().exists())
    }

    @Test
    fun f_deniedReminderWriteReturnsFailureInsteadOfFalseSuccess() = runBlocking {
        val repository = ReminderRepositoryImpl(requireFirestore())
        val signedOutUser = userId
        requireAuth().signOut()

        val result = repository.addReminder(signedOutUser, reminder("denied"))

        assertTrue(result is RepositoryResult.Failure)
        assertNotNull((result as RepositoryResult.Failure).error.message)
    }

    @Test
    fun g_recurringCrudToggleAndSchemaUseCurrentCollection() = runBlocking {
        val repository = RecurringTransactionRepositoryImpl(requireFirestore(), requireAuth())
        val recurring = recurring("recurring-crud", RECURRING_TIMES_UNLIMITED)

        assertTrue(repository.addRecurringTransaction(userId, recurring) is RepositoryResult.Success)
        val stored = recurringRef(userId, recurring.id).get(Source.SERVER).await()
        assertEquals(EXPECTED_RECURRING_FIELDS, stored.data?.keys)
        assertEquals(true, stored.getBoolean("enabled"))
        assertFalse(stored.data.orEmpty().containsKey("isEnabled"))

        val updated = recurring.copy(title = "Updated rent", amount = 150.0)
        assertTrue(repository.updateRecurringTransaction(userId, updated) is RepositoryResult.Success)
        val updatedStored = recurringRef(userId, recurring.id).get(Source.SERVER).await()
        assertEquals("Updated rent", updatedStored.getString("title"))
        assertEquals(150.0, updatedStored.getDouble("amount") ?: 0.0, 0.0)

        assertTrue(
            repository.setRecurringTransactionEnabled(userId, recurring.id, false) is RepositoryResult.Success
        )
        assertEquals(false, recurringRef(userId, recurring.id).get(Source.SERVER).await().getBoolean("enabled"))
        assertTrue(
            repository.setRecurringTransactionEnabled(userId, recurring.id, true) is RepositoryResult.Success
        )
        assertEquals(true, recurringRef(userId, recurring.id).get(Source.SERVER).await().getBoolean("enabled"))
        assertEquals(listOf(recurring.id), repository.getRecurringTransactions(userId).successValue().map { it.id })

        assertTrue(repository.deleteRecurringTransaction(userId, recurring.id) is RepositoryResult.Success)
        assertFalse(recurringRef(userId, recurring.id).get(Source.SERVER).await().exists())
    }

    private fun recurring(id: String, timesCount: String) = RecurringTransaction(
        id = id,
        userId = userId,
        title = "Rent",
        amount = 125.5,
        type = "Chi",
        category = "Hóa đơn",
        paymentMethod = "Tiền mặt",
        frequency = RECURRING_FREQUENCY_DAILY,
        timesCount = timesCount,
        startDate = "11 thg 9, 2026",
        time = "08:00",
        note = "Checkpoint 4I"
    )

    private fun reminder(id: String) = Reminder(
        id = id,
        userId = userId,
        title = "Record expense",
        frequency = "Hàng ngày",
        startDate = "11 thg 9, 2026",
        time = "20:15",
        note = "Checkpoint 4I",
        isEnabled = true
    )

    private fun recurringRef(owner: String, id: String) = requireFirestore()
        .collection(FirestoreSchema.USERS)
        .document(owner)
        .collection(FirestoreSchema.RECURRING_TRANSACTIONS)
        .document(id)

    private fun reminderRef(owner: String, id: String) = requireFirestore()
        .collection(FirestoreSchema.USERS)
        .document(owner)
        .collection(FirestoreSchema.REMINDERS)
        .document(id)

    private fun transactions(owner: String) = requireFirestore()
        .collection(FirestoreSchema.USERS)
        .document(owner)
        .collection(FirestoreSchema.TRANSACTIONS)

    private fun requireFirestore(): FirebaseFirestore = requireNotNull(firestore)
    private fun requireAuth(): FirebaseAuth = requireNotNull(auth)

    private fun <T> RepositoryResult<T>.successValue(): T = when (this) {
        is RepositoryResult.Success -> value
        is RepositoryResult.Failure -> throw AssertionError("Expected success, got ${error.code}: ${error.message}")
    }

    private fun clearProjectData(endpoint: FirebaseEmulatorEndpoint) {
        val connection = URL(
            "http://${endpoint.host}:${endpoint.firestorePort}/emulator/v1/projects/" +
                "${endpoint.projectId}/databases/(default)/documents"
        ).openConnection() as HttpURLConnection
        connection.requestMethod = "DELETE"
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            require(connection.responseCode in 200..299) {
                "Firestore Emulator clear failed with HTTP ${connection.responseCode}"
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun firebaseOptions(): FirebaseOptions = FirebaseOptions.Builder()
        .setProjectId(PROJECT_ID)
        .setApplicationId("1:1234567890:android:checkpoint04i")
        .setApiKey("fake-checkpoint-04i-key")
        .build()

    private fun isAndroidEmulator(): Boolean =
        FirebaseEmulatorSafety.isAndroidEmulator(
            fingerprint = Build.FINGERPRINT,
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            hardware = Build.HARDWARE,
            product = Build.PRODUCT,
            device = Build.DEVICE
        )

    private companion object {
        const val EMULATOR_LOG_TAG = "FIREBASE_EMULATOR_E2E"
        const val PROJECT_ID = "demo-walletwise"
        const val OCCURRENCE_KEY = "2026-09-11"
        const val EXECUTED_AT = 1_789_098_000_000L
        const val UNREACHABLE_FIRESTORE_PORT = 18_080
        val DUE_NOW = ReminderLocalDateTime(
            ReminderLocalDate(2026, 9, 11),
            ReminderLocalTime(8, 0)
        )
        val EXPECTED_TRANSACTION_FIELDS = setOf(
            "id", "userId", "type", "paymentMethod", "amount", "category", "note", "timestamp", "imageUrl"
        )
        val EXPECTED_REMINDER_FIELDS = setOf(
            "id", "userId", "title", "frequency", "startDate", "time", "note", "enabled"
        )
        val EXPECTED_RECURRING_FIELDS = setOf(
            "id", "userId", "title", "amount", "type", "category", "paymentMethod",
            "frequency", "timesCount", "startDate", "time", "note", "lastExecutedDate", "enabled"
        )
    }
}
