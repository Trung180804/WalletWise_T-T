package com.example.walletwise.presentation.home

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walletwise.data.image.AndroidTransactionWriter
import com.example.walletwise.data.repository.CategoryRepositoryImpl
import com.example.walletwise.data.repository.BudgetPlanRepositoryImpl
import com.example.walletwise.data.repository.ReminderRepositoryImpl
import com.example.walletwise.data.repository.RecurringTransactionRepositoryImpl
import com.example.walletwise.data.repository.TransactionRepositoryImpl
import com.example.walletwise.data.time.AndroidBudgetDateProvider
import com.example.walletwise.data.time.AndroidReminderDateTimeProvider
import com.example.walletwise.data.time.AndroidRecurringDateTimeProvider
import com.example.walletwise.data.time.AndroidTransactionDateTimeProvider
import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.model.DefaultCategories
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.repository.CategoryRepository
import com.example.walletwise.domain.repository.BudgetPlanRepository
import com.example.walletwise.domain.repository.ReminderRepository
import com.example.walletwise.domain.repository.RecurringTransactionRepository
import com.example.walletwise.domain.repository.TransactionRepository
import com.example.walletwise.domain.service.BudgetCalendar
import com.example.walletwise.domain.service.BudgetDateProvider
import com.example.walletwise.domain.service.ReconcileReminderSchedulingUseCase
import com.example.walletwise.domain.service.RecurringAutomationCoordinator
import com.example.walletwise.domain.usecase.ExecuteRecurringIfDueUseCase
import com.example.walletwise.presentation.budget.BudgetSessionController
import com.example.walletwise.presentation.budget.SmartBudgetPresenter
import com.example.walletwise.presentation.category.CategorySessionController
import com.example.walletwise.presentation.category.CategoryUiPresenter
import com.example.walletwise.presentation.reminder.ReminderSessionController
import com.example.walletwise.presentation.reminder.ReminderUiPresenter
import com.example.walletwise.presentation.recurring.RecurringSessionController
import com.example.walletwise.presentation.recurring.RecurringUiPresenter
import com.example.walletwise.presentation.transaction.TransactionDateRange
import com.example.walletwise.presentation.transaction.TransactionDateTimeProvider
import com.example.walletwise.presentation.transaction.TransactionListFilterState
import com.example.walletwise.presentation.transaction.TransactionListPresenter
import com.example.walletwise.presentation.transaction.TransactionListSortOrder
import com.example.walletwise.presentation.transaction.TransactionSessionController
import com.example.walletwise.presentation.transaction.TransactionTypeFilter
import com.example.walletwise.utils.AndroidReminderPlatform
import com.example.walletwise.utils.AndroidRecurringPlatform
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

class TransactionViewModel(
    private val repository: TransactionRepository = TransactionRepositoryImpl(),
    transactionWriter: AndroidTransactionWriter? = null,
    private val categoryRepository: CategoryRepository = CategoryRepositoryImpl(),
    private val budgetRepository: BudgetPlanRepository = BudgetPlanRepositoryImpl(),
    private val budgetDateProvider: BudgetDateProvider = AndroidBudgetDateProvider(),
    private val reminderRepository: ReminderRepository = ReminderRepositoryImpl(),
    private val recurringRepository: RecurringTransactionRepository = RecurringTransactionRepositoryImpl(),
    transactionDateTimeProvider: TransactionDateTimeProvider = AndroidTransactionDateTimeProvider()
) : ViewModel() {

    private val transactionWriter = transactionWriter
        ?: (repository as? TransactionRepositoryImpl)?.androidWriter()
        ?: error("AndroidTransactionWriter must be supplied with a custom repository")

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Shared read boundary; keep this facade for Android screens not migrated yet.
    private val transactionSessionController = TransactionSessionController(viewModelScope, repository)
    val transactionSessionState = transactionSessionController.state
    val transactions: StateFlow<List<Transaction>> = transactionSessionController.transactions
    private val transactionListPresenter = TransactionListPresenter(
        scope = viewModelScope,
        sessionState = transactionSessionState,
        dateTimeProvider = transactionDateTimeProvider
    )
    val transactionListState = transactionListPresenter.state

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private var transactionWriteJob: kotlinx.coroutines.Job? = null
    private var writeGeneration = 0L
    private var activeWriteUserId: String? = null

    // Lưu số Streak hiển thị lên UI
    private val _streakCount = MutableStateFlow(0)
    val streakCount: StateFlow<Int> = _streakCount.asStateFlow()

    // Biến lưu trữ danh sách danh mục
    private val categorySessionController = CategorySessionController(
        scope = viewModelScope,
        repository = categoryRepository
    )
    val categorySessionState = categorySessionController.state
    private val _categories = MutableStateFlow<List<Category>>(DefaultCategories)
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val budgetSessionController = BudgetSessionController(
        scope = viewModelScope,
        repository = budgetRepository
    )
    val budgetSessionState = budgetSessionController.state

    // Danh sách Lời nhắc nhở & Giao dịch định kỳ
    private val reminderPlatform = AndroidReminderPlatform()
    private val reminderDateTimeProvider = AndroidReminderDateTimeProvider()
    private val reminderReconcile = ReconcileReminderSchedulingUseCase(
        reminderPlatform,
        reminderPlatform,
        reminderDateTimeProvider
    )
    private val reminderSessionController = ReminderSessionController(
        scope = viewModelScope,
        repository = reminderRepository,
        reconcileScheduling = reminderReconcile
    )
    val reminderSessionState = reminderSessionController.state
    val reminders: StateFlow<List<Reminder>> = reminderSessionState
        .map { it.reminders }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val recurringDateTimeProvider = AndroidRecurringDateTimeProvider()
    private val recurringPlatform = AndroidRecurringPlatform(recurringDateTimeProvider)
    private val recurringCoordinator = RecurringAutomationCoordinator(
        executeIfDue = ExecuteRecurringIfDueUseCase(recurringRepository, recurringDateTimeProvider),
        scheduler = recurringPlatform,
        notifier = recurringPlatform,
        dateTimeProvider = recurringDateTimeProvider
    )
    private val recurringSessionController = RecurringSessionController(
        scope = viewModelScope,
        repository = recurringRepository,
        coordinator = recurringCoordinator
    )
    val recurringSessionState = recurringSessionController.state
    val recurringTransactions: StateFlow<List<RecurringTransaction>> = recurringSessionState
        .map { it.recurring }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Keep one listener per source. Re-registering these on every auth update
    // causes duplicate background work and can amplify a Firebase failure.
    private var authStateListener: AuthStateListener? = null

    // Biến lưu trữ giao dịch đang được chọn để Sửa
    var transactionToEdit by mutableStateOf<Transaction?>(null)

    // CÁC BIẾN TRẠNG THÁI CHO TRỢ LÝ AI
    private val draftPresenter = com.example.walletwise.presentation.transaction.TransactionDraftPresenter(
        viewModelScope,
        com.example.walletwise.domain.service.LocalTransactionTextAnalyzer(com.example.walletwise.data.draft.AndroidDraftDateTimeProvider()),
        categories,
        { transaction ->
            val result = this.transactionWriter.add(transaction, null, getApplicationContextForDraft())
            if (result.getOrNull() == true && auth.currentUser?.uid == transaction.userId) calculateAndGetStreak()
            result
        }
    )
    val aiDraftState = draftPresenter.state
    val receiptRecognition = com.example.walletwise.data.draft.AndroidReceiptRecognition(viewModelScope, categories)
    private var draftContext: Context? = null
    fun attachDraftContext(context: Context) { draftContext = context.applicationContext }
    private fun getApplicationContextForDraft(): Context = requireNotNull(draftContext)
    fun editAIDraft(draft: com.example.walletwise.domain.model.TransactionDraft) = draftPresenter.edit(draft)
    fun confirmAIDraft() = draftPresenter.confirm()
    fun newAIDraft() = draftPresenter.newDraft()
    fun acceptReceiptDraft(receipt: com.example.walletwise.domain.model.ReceiptTransactionDraft) = draftPresenter.acceptReceiptAutomatically(receipt)

    init {
        viewModelScope.launch {
            categorySessionState.collect { _categories.value = it.categories }
        }
        loadTransactions()
        checkAndGenerateFakeData()
        checkCurrentStreakStatus()
        fetchCategories()
        fetchReminders()
        fetchRecurringTransactions()
        refreshBudgetSession()

        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            draftPresenter.setUserId(firebaseAuth.currentUser?.uid)
            receiptRecognition.setUserId(firebaseAuth.currentUser?.uid)
            if (activeWriteUserId != firebaseAuth.currentUser?.uid) {
                activeWriteUserId = firebaseAuth.currentUser?.uid
                writeGeneration++
                transactionWriteJob?.cancel()
                _isLoading.value = false
                transactionToEdit = null
            }
            if (firebaseAuth.currentUser != null) {
                loadTransactions()
                checkCurrentStreakStatus()
                fetchCategories()
                fetchReminders()
                fetchRecurringTransactions()
                refreshBudgetSession()
            } else {
                transactionSessionController.setUserId(null)
                categorySessionController.setUserId(null)
                budgetSessionController.setSession(null, "")
                reminderSessionController.setUserId(null)
                recurringSessionController.setUserId(null)
            }
        }
        auth.addAuthStateListener(authStateListener!!)
    }

    // --- LOGIC TÍNH TOÁN STREAK (LỬA) ---
    private fun calculateAndGetStreak() {
        val currentUser = auth.currentUser ?: return

        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val todayStr = today.format(formatter)

        val userRef = db.collection("users")
            .document(currentUser.uid)

        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val lastActiveStr =
                    document.getString("lastRecordDate")

                var currentStreak =
                    document.getLong("currentStreak")?.toInt() ?: 0

                if (lastActiveStr == null) {
                    currentStreak = 1
                    updateStreakToFirebase(userRef, todayStr, currentStreak)
                } else {
                    try {
                        val lastActiveDate = LocalDate.parse(lastActiveStr, formatter)
                        val daysBetween = ChronoUnit.DAYS.between(lastActiveDate, today)

                        when {
                            daysBetween == 0L -> {
                                _streakCount.value = currentStreak
                            }
                            daysBetween == 1L -> {
                                currentStreak += 1
                                updateStreakToFirebase(userRef, todayStr, currentStreak)
                            }
                            daysBetween > 1L -> {
                                currentStreak = 1
                                updateStreakToFirebase(userRef, todayStr, currentStreak)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("StreakLogic", "Lỗi format ngày tháng: ${e.message}")
                        updateStreakToFirebase(userRef, todayStr, 1)
                    }
                }
            } else {
                updateStreakToFirebase(userRef, todayStr, 1)
            }
        }.addOnFailureListener { e ->
            Log.e("StreakLogic", "Lỗi lấy dữ liệu Streak: ${e.message}")
        }
    }

    private fun updateStreakToFirebase(userRef: com.google.firebase.firestore.DocumentReference, todayStr: String, streak: Int) {
        val updates = mapOf(
            "lastRecordDate" to todayStr,
            "currentStreak" to streak
        )
        userRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                _streakCount.value = streak
            }
    }

    // --- HÀM KIỂM TRA STREAK KHI MỞ APP ---
    private fun checkCurrentStreakStatus() {
        val currentUser = auth.currentUser ?: return
        val userRef = db.collection("users").document(currentUser.uid)

        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val lastActiveStr = document.getString("lastRecordDate")
                var currentStreak = document.getLong("currentStreak")?.toInt() ?: 0

                if (lastActiveStr != null) {
                    val today = LocalDate.now()
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    try {
                        val lastActiveDate = LocalDate.parse(lastActiveStr, formatter)
                        val daysBetween = ChronoUnit.DAYS.between(lastActiveDate, today)

                        // Nếu đã quá 1 ngày chưa ghi nhận giao dịch -> mất streak
                        if (daysBetween > 1L) {
                            currentStreak = 0
                            // Cập nhật lại Firebase là đã rớt chuỗi (tùy chọn)
                            updateStreakToFirebase(userRef, lastActiveStr, 0)
                        }
                    } catch (e: Exception) {
                        Log.e("StreakLogic", "Lỗi format ngày tháng: ${e.message}")
                    }
                }
                // Gán giá trị để hiển thị lên UI ngay khi mở app
                _streakCount.value = currentStreak
            }
        }
    }

    // --- HÀM BƠM DATA GIẢ ---
    private fun checkAndGenerateFakeData() {
        val currentUser = auth.currentUser
        val targetEmail = "sniper021003@gmail.com"
        if (currentUser == null || currentUser.email != targetEmail) return

        db.collection("users").document(currentUser.uid).collection("transactions")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    generateDataForUser(db, currentUser.uid)
                }
            }
    }

    private fun generateDataForUser(db: FirebaseFirestore, uid: String) {
        val sampleImages = listOf(
            "https://picsum.photos/id/42/300/300",
            "https://picsum.photos/id/163/300/300",
            "https://picsum.photos/id/225/300/300",
            "https://picsum.photos/id/292/300/300",
            "https://picsum.photos/id/365/300/300"
        )

        val now = java.time.LocalDateTime.now()
        val currentMonth = YearMonth.now()

        for (i in 1..30) {
            val randomDay = (1..currentMonth.lengthOfMonth()).random()
            val date = currentMonth.atDay(randomDay)

            val maxHour = if (date.dayOfMonth == now.dayOfMonth) now.hour else 23
            val randomHour = (0..maxHour).random()
            val maxMinute = if (date.dayOfMonth == now.dayOfMonth && randomHour == now.hour) now.minute else 59
            val randomMinute = (0..maxMinute).random()

            val timestamp = date.atTime(randomHour, randomMinute)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val txId = UUID.randomUUID().toString()
            val fakeTx = hashMapOf(
                "id" to txId,
                "userId" to uid,
                "amount" to (10..500).random() * 1000.0,
                "category" to listOf("Ăn uống", "Mua sắm", "Tiền nhà", "Siêu thị").random(),
                "type" to "Chi",
                "paymentMethod" to "Tiền mặt",
                "timestamp" to timestamp,
                "imageUrl" to sampleImages.random()
            )

            db.collection("users").document(uid).collection("transactions").document(txId).set(fakeTx)
        }
        loadTransactions()
    }

    fun loadTransactions() {
        transactionSessionController.setUserId(auth.currentUser?.uid)
    }

    /** Compatibility facade retained until all Android call sites move to the shared session. */
    fun listenToTransactions() {
        loadTransactions()
    }

    fun updateTransactionListFilters(
        paymentMethod: String?,
        startEpochMilliseconds: Long?,
        endEpochMilliseconds: Long?,
        type: TransactionTypeFilter = TransactionTypeFilter.ALL
    ) {
        transactionListPresenter.updateFilters(
            TransactionListFilterState(
                type = type,
                paymentMethod = paymentMethod?.takeIf(String::isNotBlank),
                dateRange = if (startEpochMilliseconds != null && endEpochMilliseconds != null) {
                    TransactionDateRange(startEpochMilliseconds, endEpochMilliseconds)
                } else {
                    null
                },
                sortOrder = TransactionListSortOrder.NEWEST_FIRST,
                searchQuery = transactionListState.value.filters.searchQuery
            )
        )
    }

    fun updateTransactionSearchQuery(query: String) = transactionListPresenter.updateSearchQuery(query)

    fun refreshTransactionList() = transactionSessionController.refresh()

    fun onTransactionRowSelected(transactionId: String) =
        transactionListPresenter.onRowSelected(transactionId)

    fun onTransactionEditRequested(transactionId: String) =
        transactionListPresenter.onEditRequested(transactionId)

    fun onTransactionDeleteRequested(transactionId: String) =
        transactionListPresenter.onDeleteRequested(transactionId)

    fun consumeTransactionListEvent(eventId: Long) {
        transactionListPresenter.consumeEvent(eventId)
    }

    fun refreshBudgetSession() {
        val date = budgetDateProvider.currentLocalDate()
        budgetSessionController.setSession(
            userId = auth.currentUser?.uid,
            monthKey = BudgetCalendar.monthKey(date)
        )
    }

    fun selectBudgetMonth(monthKey: String) {
        budgetSessionController.setSession(auth.currentUser?.uid, monthKey)
    }

    fun createSmartBudgetPresenter(scope: CoroutineScope): SmartBudgetPresenter {
        refreshBudgetSession()
        return SmartBudgetPresenter(
            scope = scope,
            budgetSession = budgetSessionState,
            transactions = transactions,
            repository = budgetRepository,
            dateProvider = budgetDateProvider,
            onMonthSelected = ::selectBudgetMonth,
            mappingRepository = com.example.walletwise.data.repository.FinancialMappingRepositoryImpl(),
            categories = categories,
            categorySession = categorySessionState
        )
    }

    fun processAITransaction(userInput: String, source: com.example.walletwise.domain.model.DraftSource = com.example.walletwise.domain.model.DraftSource.TEXT) {
        draftPresenter.setUserId(auth.currentUser?.uid)
        draftPresenter.submitAutomatically(userInput, source)
    }

    fun resetAIState() { draftPresenter.cancelAnalysis(); receiptRecognition.cancel() }
    fun addTransaction(
        amount: Double,
        type: String,
        category: String,
        note: String,
        paymentMethod: String,
        imageUri: Uri?,
        context: Context,
        draftId: String = java.util.UUID.randomUUID().toString(),
        transactionTimestamp: Long = System.currentTimeMillis(),
        expectedUserId: String? = auth.currentUser?.uid,
        onSuccess: () -> Unit
    ) {
        if (_isLoading.value || expectedUserId == null || expectedUserId != auth.currentUser?.uid) return
        _isLoading.value = true
        val version = writeGeneration
        transactionWriteJob = viewModelScope.launch {
            val trans = Transaction(
                id = draftId,
                userId = expectedUserId,
                amount = amount,
                type = type,
                category = category,
                note = note,
                paymentMethod = paymentMethod,
                timestamp = transactionTimestamp,
                categoryId = categories.value.firstOrNull { it.type == type && it.name == category }?.id.orEmpty()
            )

            val result = kotlinx.coroutines.withTimeoutOrNull(15_000L) { transactionWriter.add(trans, imageUri, context) }
                ?: Result.failure<Boolean>(IllegalStateException("Acknowledgement timeout"))
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (version != writeGeneration) return@launch
            result
                .onSuccess { written ->
                    if (!written || auth.currentUser?.uid != expectedUserId) return@onSuccess
                    calculateAndGetStreak() // Tự động cập nhật lửa
                    loadTransactions()
                    android.widget.Toast.makeText(context, "Thêm giao dịch thành công!", android.widget.Toast.LENGTH_SHORT).show()
                    onSuccess()
                }
                .onFailure {
                    Log.e("ADD_TRANSACTION", "Write failed (details redacted)")
                    if (auth.currentUser?.uid == expectedUserId) android.widget.Toast.makeText(context, "Chưa nhận được xác nhận lưu. Hãy thử lại cùng bản nháp.", android.widget.Toast.LENGTH_LONG).show()
                }
            _isLoading.value = false
        }
    }

    fun deleteTransaction(transactionId: String) {
        if (transactionId.isBlank()) return
        viewModelScope.launch {
            repository.deleteTransaction(transactionId)
                .onSuccess { deleted ->
                    if (deleted) loadTransactions()
                }
                .onFailure {
                    Log.e("DELETE_TRANSACTION", "Delete failed")
                }
        }
    }

    fun updateTransaction(
        transaction: Transaction,
        imageUri: Uri?,
        context: Context,
        onSuccess: () -> Unit
    ) {
        val expectedUserId = auth.currentUser?.uid
        if (_isLoading.value || expectedUserId == null || transaction.userId != expectedUserId) return
        _isLoading.value = true
        val version = writeGeneration
        transactionWriteJob = viewModelScope.launch {
            val result = kotlinx.coroutines.withTimeoutOrNull(15_000L) { transactionWriter.update(transaction, imageUri, context) }
                ?: Result.failure<Boolean>(IllegalStateException("Acknowledgement timeout"))
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (version != writeGeneration) return@launch
            result
                .onSuccess { written ->
                    if (!written || auth.currentUser?.uid != expectedUserId) return@onSuccess
                    calculateAndGetStreak() // Cập nhật lửa
                    loadTransactions()
                    android.widget.Toast.makeText(context, "Cập nhật giao dịch thành công!", android.widget.Toast.LENGTH_SHORT).show()
                    onSuccess()
                }
                .onFailure {
                    Log.e("UPDATE_TRANSACTION", "Write failed (details redacted)")
                    if (auth.currentUser?.uid == expectedUserId) android.widget.Toast.makeText(context, "Chưa nhận được xác nhận cập nhật. Hãy thử lại.", android.widget.Toast.LENGTH_LONG).show()
                }
            _isLoading.value = false
        }
    }

    private fun fetchCategories() {
        categorySessionController.setUserId(auth.currentUser?.uid)
    }

    fun createCategoryPresenter(scope: CoroutineScope): CategoryUiPresenter =
        CategoryUiPresenter(
            scope = scope,
            sessionState = categorySessionState,
            repository = categoryRepository
        )

    // =========================================================
    // LOGIC LỜI NHẮC NHỞ (REMINDERS)
    // =========================================================
    fun fetchReminders() {
        reminderSessionController.setUserId(auth.currentUser?.uid)
    }

    fun createReminderPresenter(scope: CoroutineScope, context: Context): ReminderUiPresenter {
        reminderPlatform.attach(context.applicationContext)
        reminderSessionController.setUserId(auth.currentUser?.uid)
        reminderSessionController.forceReconcile()
        return ReminderUiPresenter(
            scope = scope,
            sessionController = reminderSessionController,
            repository = reminderRepository,
            reconcileScheduling = reminderReconcile,
            dateTimeProvider = reminderDateTimeProvider
        )
    }

    // =========================================================
    // LOGIC GIAO DỊCH ĐỊNH KỲ (RECURRING TRANSACTIONS)
    // =========================================================
    fun fetchRecurringTransactions() {
        recurringSessionController.setUserId(auth.currentUser?.uid)
    }

    /** Starts reminder and recurring-transaction scheduling once a context is available. */
    fun initializeScheduledAutomation(context: Context) {
        reminderPlatform.attach(context.applicationContext)
        reminderSessionController.forceReconcile()
        recurringPlatform.attach(context.applicationContext)
        recurringSessionController.setUserId(auth.currentUser?.uid)
        recurringSessionController.forceReconcile()
    }

    fun createRecurringPresenter(scope: CoroutineScope, context: Context): RecurringUiPresenter {
        recurringPlatform.attach(context.applicationContext)
        recurringSessionController.setUserId(auth.currentUser?.uid)
        categorySessionController.setUserId(auth.currentUser?.uid)
        return RecurringUiPresenter(
            scope = scope,
            sessionController = recurringSessionController,
            categorySessionController = categorySessionController,
            repository = recurringRepository,
            coordinator = recurringCoordinator,
            dateTimeProvider = recurringDateTimeProvider,
            permissionGateway = recurringPlatform
        )
    }

    override fun onCleared() {
        transactionListPresenter.close()
        transactionSessionController.close()
        categorySessionController.close()
        budgetSessionController.close()
        reminderSessionController.close()
        recurringSessionController.close()
        authStateListener?.let(auth::removeAuthStateListener)
        transactionWriteJob?.cancel()
        draftPresenter.close()
        receiptRecognition.close()
        super.onCleared()
    }

}
