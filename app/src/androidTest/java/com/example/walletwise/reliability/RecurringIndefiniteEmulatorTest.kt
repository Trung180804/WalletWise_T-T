package com.example.walletwise.reliability

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.walletwise.BuildConfig
import com.example.walletwise.data.mapper.FirestoreWireMapper
import com.example.walletwise.data.repository.*
import com.example.walletwise.data.time.AndroidRecurringDateTimeProvider
import com.example.walletwise.data.time.AndroidBudgetDateProvider
import com.example.walletwise.domain.repository.TransactionRepository
import com.example.walletwise.presentation.budget.*
import com.example.walletwise.presentation.transaction.TransactionSessionController
import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.*
import com.example.walletwise.domain.usecase.ExecuteRecurringIfDueUseCase
import com.example.walletwise.presentation.category.CategorySessionController
import com.example.walletwise.presentation.recurring.*
import com.example.walletwise.utils.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Real repository, real Switch callback and real AlarmManager, with an injected wall clock. */
class RecurringIndefiniteEmulatorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun dailyAndWeeklyStayEnabledRetryOnceAndToggleRealAlarms(): Unit = runBlocking {
        assertTrue(BuildConfig.USE_FIREBASE_EMULATOR)
        assertEquals("demo-walletwise", FirebaseApp.getInstance().options.projectId)
        val auth = FirebaseAuth.getInstance(); auth.signOut()
        val uid = requireNotNull(auth.signInAnonymously().await().user).uid
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val realClock = AndroidRecurringDateTimeProvider()
        val now = realClock.currentLocalDateTime()
        val clock = object : RecurringDateTimeProvider by realClock {
            override fun currentLocalDateTime() = now
        }
        val repository = RecurringTransactionRepositoryImpl(dateTimeProvider = clock)
        val android = AndroidRecurringPlatform(clock, context)
        val scheduled = CopyOnWriteArrayList<RecurringScheduleRequest>()
        val scheduler = object : RecurringPlatformScheduler by android {
            override suspend fun schedule(request: RecurringScheduleRequest): RecurringPlatformScheduleResult {
                val result = android.schedule(request)
                if (result is RecurringPlatformScheduleResult.Scheduled) scheduled += request
                return result
            }
        }
        val coordinator = RecurringAutomationCoordinator(ExecuteRecurringIfDueUseCase(repository, clock), scheduler, android, clock)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val session = RecurringSessionController(scope, repository, coordinator)
        val categorySession = CategorySessionController(scope, CategoryRepositoryImpl())
        val presenter = RecurringUiPresenter(scope, session, categorySession, repository, coordinator, clock, android)
        var transactionListeners = 0
        val baseTransactions = TransactionRepositoryImpl()
        val transactionRepository = object : TransactionRepository by baseTransactions {
            override fun observeTransactions(userId: String): Flow<RepositoryResult<List<Transaction>>> {
                transactionListeners++; return baseTransactions.observeTransactions(userId)
            }
        }
        val transactionSession = TransactionSessionController(scope, transactionRepository)
        val dates = AndroidBudgetDateProvider()
        val month = BudgetCalendar.monthKey(dates.currentLocalDate())
        val plans = BudgetPlanRepositoryImpl()
        val plan = BudgetPlan(monthYear = month, totalBudget = 10000000.0, needsLimit = 5000000.0, wantsLimit = 3000000.0, savingsLimit = 2000000.0)
        assertTrue(plans.saveBudgetPlan(uid, plan) is RepositoryResult.Success)
        val savedPlan = (plans.getBudgetPlan(uid, month) as RepositoryResult.Success).value
        val allocation = SmartBudgetPresenter(scope, MutableStateFlow(BudgetSessionState(uid, month, plan = savedPlan)),
            transactionSession.transactions, plans, dates, mappingRepository = FinancialMappingRepositoryImpl())
        withContext(Dispatchers.Main) { transactionSession.setUserId(uid) }
        val db = FirebaseFirestore.getInstance()
        val collection = db.collection("users").document(uid).collection("recurring_transactions")
        val transactions = db.collection("users").document(uid).collection("transactions")
        val rules = listOf(RECURRING_FREQUENCY_DAILY, RECURRING_FREQUENCY_WEEKLY).mapIndexed { index, frequency ->
            RecurringTransaction(id = "priority-${index}-${java.util.UUID.randomUUID()}", userId = uid, title = if (index == 0) "Daily TEST" else "Weekly TEST",
                amount = 50000.0, category = "Ăn uống", frequency = frequency, timesCount = if (index == 0) RECURRING_TIMES_UNLIMITED else "1",
                startDate = RecurringScheduleCalculator.formatStartDate(now.date), time = RecurringScheduleCalculator.formatTime(now.time))
        }
        try {
            rules.forEach { rule ->
                assertTrue(repository.addRecurringTransaction(uid, rule) is RepositoryResult.Success)
                collection.document(rule.id).update("isEnabled", false).await()
                assertTrue(coordinator.process(uid, rule.id).execution?.transactionWasCreated == true)
                assertFalse(coordinator.process(uid, rule.id).execution?.transactionWasCreated ?: true)
                val stored = collection.document(rule.id).get(Source.SERVER).await()
                assertEquals(true, stored.getBoolean("enabled")); assertEquals(false, stored.getBoolean("isEnabled"))
                assertEquals(rule.timesCount, stored.getString("timesCount")); assertEquals(RecurringScheduleCalculator.occurrenceKey(RecurringOccurrence(0, now)), stored.getString("lastExecutedDate"))
                val next = scheduled.last { it.recurring.id == rule.id }
                assertEquals(ReminderCalendar.plusDays(now.date, if (rule.frequency == RECURRING_FREQUENCY_WEEKLY) 7L else 1L), next.occurrence.at.date)
                assertEquals(now.time, next.occurrence.at.time); assertNotNull(pending(rule.id))
                assertEquals(1, transactions.get(Source.SERVER).await().documents.count { it.id.startsWith(rule.id + "_") })
                Log.i("RECURRING_PRIORITY", "${rule.frequency} first=true retryCreated=false enabled=true next=${RecurringScheduleCalculator.occurrenceKey(next.occurrence)} anchorPreserved=true alarm=true")
            }
            withContext(Dispatchers.Main) { categorySession.setUserId(uid); session.setUserId(uid) }
            withTimeout(20000) { presenter.state.first { it.recurring.size == 2 && !it.isLoading } }
            compose.setContent { MaterialTheme {
                val state by presenter.state.collectAsState()
                RecurringContent(state, presenter::onBack, presenter::onOpenAdd, presenter::onOpenDetail, presenter::onDismissDetail,
                    presenter::onOpenEdit, presenter::onRequestDelete, presenter::onCancelDelete, presenter::onConfirmDelete, presenter::onToggle,
                    presenter::onCancelForm, presenter::onSubmit, presenter::onTitleChanged, presenter::onAmountChanged, presenter::onNoteChanged,
                    presenter::onTypeSelected, presenter::onOpenPicker, presenter::onFrequencySelected, presenter::onTimesCountSelected,
                    presenter::onCategorySelected, presenter::onPaymentMethodSelected, presenter::onPreviousDatePickerMonth,
                    presenter::onNextDatePickerMonth, presenter::onDatePickerDaySelected, presenter::onConfirmDatePicker,
                    presenter::onTimePickerHourChanged, presenter::onTimePickerMinuteChanged, presenter::onConfirmTimePicker, presenter::onDismissPicker)
            } }
            val daily = rules.first()
            compose.onNodeWithContentDescription("Lịch định kỳ: Daily TEST").assertIsOn()
            proof("recurring-first-enabled")
            compose.onNodeWithContentDescription("Lịch định kỳ: Daily TEST").performClick()
            withTimeout(20000) { presenter.state.first { it.recurring.first { item -> item.id == daily.id }.isEnabled == false && it.togglingRecurringIds.isEmpty() } }
            compose.onNodeWithContentDescription("Lịch định kỳ: Daily TEST").assertIsOff()
            assertNull(pending(daily.id)); assertEquals(false, collection.document(daily.id).get(Source.SERVER).await().getBoolean("enabled"))
            proof("recurring-toggle-off"); Log.i("RECURRING_PRIORITY", "toggleOff enabled=false alarm=false")
            compose.onNodeWithContentDescription("Lịch định kỳ: Daily TEST").performClick()
            withTimeout(20000) { presenter.state.first { it.recurring.first { item -> item.id == daily.id }.isEnabled && it.togglingRecurringIds.isEmpty() } }
            compose.onNodeWithContentDescription("Lịch định kỳ: Daily TEST").assertIsOn()
            assertNotNull(pending(daily.id)); assertEquals(true, collection.document(daily.id).get(Source.SERVER).await().getBoolean("enabled"))
            assertEquals(2, transactions.get(Source.SERVER).await().size())
            withTimeout(20000) { allocation.state.first { it.liveAllocation.buckets.firstOrNull()?.spent == 100000L } }
            assertEquals(4900000L, allocation.state.value.liveAllocation.buckets.first().remaining)
            assertEquals(2, transactionSession.transactions.value.size); assertEquals(1, transactionListeners)
            Log.i("RECURRING_PRIORITY", "allocation source=2 listeners=1 spent=100000 remaining=4900000 retryDeduplicated=true")
            proof("recurring-toggle-on"); Log.i("RECURRING_PRIORITY", "toggleOn enabled=true alarm=true transactionCount=2")
            val before = scheduled.size
            coordinator.reconcile(uid, rules); assertEquals(before, scheduled.size)
            // A fresh coordinator (process/boot recovery) keeps the saved choices and schedules once.
            val restarted = RecurringAutomationCoordinator(ExecuteRecurringIfDueUseCase(repository, clock), scheduler, android, clock)
            restarted.reconcile(uid, (repository.getRecurringTransactions(uid) as RepositoryResult.Success).value)
            assertTrue((repository.getRecurringTransactions(uid) as RepositoryResult.Success).value.all { it.isEnabled })
            assertEquals(2, transactions.get(Source.SERVER).await().size())
            restarted.clearUser(uid, rules); rules.forEach { assertNull(pending(it.id)) }
            assertTrue((repository.getRecurringTransactions(uid) as RepositoryResult.Success).value.all { it.isEnabled })
        } finally {
            withContext(Dispatchers.Main) { allocation.close(); transactionSession.close(); presenter.close(); session.setUserId(null); session.close(); categorySession.close() }
            coordinator.clearUser(uid, rules); scope.cancel(); auth.signOut()
        }
    }
    private fun pending(id: String): PendingIntent? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return PendingIntent.getBroadcast(context, AndroidRecurringPlatform.stableRequestCode(id), Intent(context, RecurringTransactionReceiver::class.java).apply {
            action = AndroidSchedulingContract.ACTION_RECURRING_ALARM; data = Uri.parse(AndroidSchedulingContract.recurringData(id))
        }, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun proof(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "priority-proof").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
