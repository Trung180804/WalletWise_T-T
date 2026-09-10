package com.example.walletwise.presentation.home

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walletwise.data.image.AndroidTransactionWriter
import com.example.walletwise.data.repository.CategoryRepositoryImpl
import com.example.walletwise.data.repository.BudgetPlanRepositoryImpl
import com.example.walletwise.data.repository.TransactionRepositoryImpl
import com.example.walletwise.data.time.AndroidBudgetDateProvider
import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.model.DefaultCategories
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.repository.CategoryRepository
import com.example.walletwise.domain.repository.BudgetPlanRepository
import com.example.walletwise.domain.repository.TransactionRepository
import com.example.walletwise.domain.service.BudgetCalendar
import com.example.walletwise.domain.service.BudgetDateProvider
import com.example.walletwise.presentation.budget.BudgetSessionController
import com.example.walletwise.presentation.budget.SmartBudgetPresenter
import com.example.walletwise.presentation.category.CategorySessionController
import com.example.walletwise.presentation.category.CategoryUiPresenter
import com.example.walletwise.utils.NotificationHelper
import com.example.walletwise.utils.RecurringTransactionExecutor
import com.example.walletwise.utils.RecurringTransactionScheduler
import com.example.walletwise.utils.ReminderScheduler
import com.example.walletwise.utils.SettingsFirestoreMapper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    private val budgetDateProvider: BudgetDateProvider = AndroidBudgetDateProvider()
) : ViewModel() {

    private val transactionWriter = transactionWriter
        ?: (repository as? TransactionRepositoryImpl)?.androidWriter()
        ?: error("AndroidTransactionWriter must be supplied with a custom repository")

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Danh sách giao dịch
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _userProfile = MutableStateFlow<com.example.walletwise.domain.model.User?>(null)
    val userProfile: StateFlow<com.example.walletwise.domain.model.User?> = _userProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

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
    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    private val _recurringTransactions = MutableStateFlow<List<RecurringTransaction>>(emptyList())
    val recurringTransactions: StateFlow<List<RecurringTransaction>> = _recurringTransactions.asStateFlow()
    private var automationAppContext: Context? = null

    // Keep one listener per source. Re-registering these on every auth update
    // causes duplicate background work and can amplify a Firebase failure.
    private var transactionsListener: ListenerRegistration? = null
    private var profileListener: ListenerRegistration? = null
    private var remindersListener: ListenerRegistration? = null
    private var recurringListener: ListenerRegistration? = null
    private var authStateListener: AuthStateListener? = null

    // Biến lưu trữ giao dịch đang được chọn để Sửa
    var transactionToEdit by mutableStateOf<Transaction?>(null)

    // CÁC BIẾN TRẠNG THÁI CHO TRỢ LÝ AI
    val aiAssistant = TransactionAIAssistant()
    var isAIProcessing by mutableStateOf(false)
    var aiFeedbackMessage by mutableStateOf("Xin chào! Bạn vừa chi tiêu gì vậy?")
    var aiPendingTransaction by mutableStateOf<Transaction?>(null)

    init {
        viewModelScope.launch {
            categorySessionState.collect { _categories.value = it.categories }
        }
        loadTransactions()
        loadUserProfile()
        checkAndGenerateFakeData()
        checkCurrentStreakStatus()
        fetchCategories()
        fetchReminders()
        fetchRecurringTransactions()
        refreshBudgetSession()

        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                loadTransactions()
                loadUserProfile()
                checkCurrentStreakStatus()
                fetchCategories()
                fetchReminders()
                fetchRecurringTransactions()
                refreshBudgetSession()
            } else {
                categorySessionController.setUserId(null)
                budgetSessionController.setSession(null, "")
                automationAppContext?.let { context ->
                    _reminders.value.forEach { ReminderScheduler.cancel(context, it.id) }
                    _recurringTransactions.value.forEach {
                        RecurringTransactionScheduler.cancel(context, it.id)
                    }
                }
                remindersListener?.remove()
                recurringListener?.remove()
                remindersListener = null
                recurringListener = null
                _reminders.value = emptyList()
                _recurringTransactions.value = emptyList()
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
        viewModelScope.launch {
            repository.getTransactions()
                .onSuccess { list ->
                    _transactions.value = list.sortedByDescending { it.timestamp }
                }
        }
        listenToTransactions()
    }

    fun listenToTransactions() {
        val uid = auth.currentUser?.uid ?: return
        transactionsListener?.remove()
        transactionsListener = db.collection("users").document(uid).collection("transactions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FIRESTORE_ERROR", "Unable to listen to transactions", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { document ->
                        runCatching { document.toObject(Transaction::class.java) }
                            .onFailure {
                                Log.e("FIRESTORE_ERROR", "Skipping invalid transaction ${document.id}", it)
                            }
                            .getOrNull()
                    }
                    _transactions.value = list.sortedByDescending { it.timestamp }
                }
            }
    }

    fun refreshBudgetSession() {
        val date = budgetDateProvider.currentLocalDate()
        budgetSessionController.setSession(
            userId = auth.currentUser?.uid,
            monthKey = BudgetCalendar.monthKey(date)
        )
    }

    fun createSmartBudgetPresenter(scope: CoroutineScope): SmartBudgetPresenter {
        refreshBudgetSession()
        return SmartBudgetPresenter(
            scope = scope,
            budgetSession = budgetSessionState,
            transactions = transactions,
            repository = budgetRepository,
            dateProvider = budgetDateProvider
        )
    }

    fun processAITransaction(userInput: String) {
        viewModelScope.launch {
            isAIProcessing = true
            aiFeedbackMessage = "Đang suy nghĩ..."
            aiPendingTransaction = null

            val jsonString = aiAssistant.analyzeTransactionText(userInput)

            if (jsonString != null) {
                try {
                    val cleanJson = jsonString.replace("```json", "").replace("```", "").trim()
                    val jsonObject = JSONObject(cleanJson)
                    val missingPrompt = jsonObject.optString("missing_prompt", "")

                    if (missingPrompt.isNotEmpty()) {
                        aiFeedbackMessage = missingPrompt
                    } else {
                        val amount = jsonObject.optDouble("amount", 0.0)
                        val category = jsonObject.optString("category", "Khác")
                        val type = jsonObject.optString("type", "Chi")
                        val paymentMethod = jsonObject.optString("paymentMethod", "Tiền mặt")
                        val note = jsonObject.optString("note", "")

                        aiPendingTransaction = Transaction(
                            amount = amount,
                            category = category,
                            type = type,
                            paymentMethod = paymentMethod,
                            note = note,
                            timestamp = System.currentTimeMillis()
                        )
                        aiFeedbackMessage = "Tôi đã phân tích xong. Bạn xem thông tin đã chính xác chưa nhé!"
                    }
                } catch (e: Exception) {
                    val errorMessage = e.message ?: ""
                    if (errorMessage.contains("high demand") || errorMessage.contains("503")) {
                        aiFeedbackMessage = "AI đang có quá nhiều người sử dụng. Bạn vui lòng thử lại sau vài phút nhé!"
                    } else if (errorMessage.contains("Quota") || errorMessage.contains("limit")) {
                        aiFeedbackMessage = "Đã hết lượt sử dụng AI miễn phí hôm nay."
                    } else {
                        aiFeedbackMessage = "Xin lỗi, tôi chưa hiểu rõ. Bạn nói lại cụ thể khoản tiền và mục đích nhé!"
                    }
                    e.printStackTrace()
                }
            } else {
                aiFeedbackMessage = "Lỗi kết nối AI. Vui lòng thử lại!"
            }
            isAIProcessing = false
        }
    }

    fun resetAIState() {
        aiFeedbackMessage = "Xin chào! Bạn vừa chi tiêu gì vậy?"
        aiPendingTransaction = null
    }

    fun addTransaction(
        amount: Double,
        type: String,
        category: String,
        note: String,
        paymentMethod: String,
        imageUri: Uri?,
        context: Context,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true

            val trans = Transaction(
                amount = amount,
                type = type,
                category = category,
                note = note,
                paymentMethod = paymentMethod
            )

            transactionWriter.add(trans, imageUri, context)
                .onSuccess {
                    calculateAndGetStreak() // Tự động cập nhật lửa
                    loadTransactions()
                    android.widget.Toast.makeText(context, "Thêm giao dịch thành công!", android.widget.Toast.LENGTH_SHORT).show()
                    onSuccess()
                }
                .onFailure {
                    Log.e("ADD_TRANSACTION", "ERROR", it)
                    android.widget.Toast.makeText(context, "Lỗi thêm giao dịch: ${it.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            _isLoading.value = false
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(transactionId)
                .onSuccess {
                    loadTransactions()
                }
        }
    }

    fun updateTransaction(
        transaction: Transaction,
        imageUri: Uri?,
        context: Context,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true

            transactionWriter.update(transaction, imageUri, context)
                .onSuccess {
                    calculateAndGetStreak() // Cập nhật lửa
                    loadTransactions()
                    android.widget.Toast.makeText(context, "Cập nhật giao dịch thành công!", android.widget.Toast.LENGTH_SHORT).show()
                    onSuccess()
                }
                .onFailure {
                    Log.e("UPDATE_TRANSACTION", "ERROR", it)
                    android.widget.Toast.makeText(context, "Lỗi cập nhật: ${it.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            _isLoading.value = false
        }
    }

    fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        profileListener?.remove()
        profileListener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FIRESTORE_ERROR", "Unable to listen to user profile", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    _userProfile.value = runCatching {
                        snapshot.toObject(com.example.walletwise.domain.model.User::class.java)
                    }.onFailure {
                        Log.e("FIRESTORE_ERROR", "Ignoring invalid user profile", it)
                    }.getOrNull()
                }
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
        val uid = auth.currentUser?.uid ?: return
        remindersListener?.remove()
        remindersListener = db.collection("users").document(uid).collection("reminders")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FIRESTORE_ERROR", "Unable to listen to reminders", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { document ->
                        runCatching {
                            SettingsFirestoreMapper.reminderFromMap(
                                documentId = document.id,
                                ownerUserId = uid,
                                data = document.data.orEmpty()
                            )
                        }
                            .onFailure {
                                Log.e("FIRESTORE_ERROR", "Skipping invalid reminder ${document.id}", it)
                            }
                            .getOrNull()
                    }
                    _reminders.value = list
                    synchronizeReminders(list)
                }
            }
    }

    fun addReminder(
        reminder: Reminder,
        context: Context,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onComplete(Result.failure(IllegalStateException("Bạn cần đăng nhập để lưu lời nhắc")))
            return
        }
        val finalReminder = reminder.copy(userId = uid)
        db.collection("users").document(uid).collection("reminders")
            .document(finalReminder.id).set(SettingsFirestoreMapper.reminderToMap(finalReminder))
            .addOnSuccessListener {
                _reminders.value = _reminders.value
                    .filterNot { it.id == finalReminder.id } + finalReminder
                ReminderScheduler.schedule(context.applicationContext, finalReminder)
                requestExactAlarmPermissionIfNeeded(context)
                onComplete(Result.success(Unit))
            }
            .addOnFailureListener {
                Log.e("FIRESTORE_ERROR", "Error adding reminder", it)
                onComplete(Result.failure(it))
            }
    }

    fun deleteReminder(
        reminderId: String,
        context: Context? = automationAppContext,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onComplete(Result.failure(IllegalStateException("Bạn cần đăng nhập để xóa lời nhắc")))
            return
        }
        val existing = _reminders.value.firstOrNull { it.id == reminderId }
        val appContext = context?.applicationContext ?: automationAppContext
        appContext?.let { ReminderScheduler.cancel(it, reminderId) }
        db.collection("users").document(uid).collection("reminders").document(reminderId).delete()
            .addOnSuccessListener {
                _reminders.value = _reminders.value.filterNot { it.id == reminderId }
                onComplete(Result.success(Unit))
            }
            .addOnFailureListener { error ->
                if (appContext != null && existing?.isEnabled == true) {
                    ReminderScheduler.schedule(appContext, existing)
                }
                Log.e("FIRESTORE_ERROR", "Error deleting reminder", error)
                onComplete(Result.failure(error))
            }
    }

    fun setReminderEnabled(
        reminder: Reminder,
        isEnabled: Boolean,
        context: Context? = null,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onComplete(Result.failure(IllegalStateException("Bạn cần đăng nhập để cập nhật lời nhắc")))
            return
        }
        val updated = reminder.copy(userId = uid, isEnabled = isEnabled)
        _reminders.value = _reminders.value.map { if (it.id == updated.id) updated else it }
        db.collection("users").document(uid).collection("reminders").document(updated.id)
            .update(SettingsFirestoreMapper.enabledFields(isEnabled))
            .addOnSuccessListener {
                val appContext = context?.applicationContext ?: automationAppContext
                if (isEnabled && appContext != null) {
                    ReminderScheduler.schedule(appContext, updated)
                    context?.let(::requestExactAlarmPermissionIfNeeded)
                } else if (!isEnabled && appContext != null) {
                    ReminderScheduler.cancel(appContext, updated.id)
                }
                onComplete(Result.success(Unit))
            }
            .addOnFailureListener {
                _reminders.value = _reminders.value.map { current ->
                    if (current.id == reminder.id && current.isEnabled == isEnabled) reminder else current
                }
                Log.e("FIRESTORE_ERROR", "Error toggling reminder", it)
                onComplete(Result.failure(it))
            }
    }

    fun updateReminder(
        reminder: Reminder,
        context: Context,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onComplete(Result.failure(IllegalStateException("Bạn cần đăng nhập để cập nhật lời nhắc")))
            return
        }
        val updated = reminder.copy(userId = uid)
        db.collection("users").document(uid).collection("reminders").document(updated.id)
            .set(SettingsFirestoreMapper.reminderToMap(updated))
            .addOnSuccessListener {
                _reminders.value = _reminders.value.map { if (it.id == updated.id) updated else it }
                if (updated.isEnabled) {
                    ReminderScheduler.schedule(context.applicationContext, updated)
                    requestExactAlarmPermissionIfNeeded(context)
                } else {
                    ReminderScheduler.cancel(context.applicationContext, updated.id)
                }
                onComplete(Result.success(Unit))
            }
            .addOnFailureListener {
                Log.e("FIRESTORE_ERROR", "Error updating reminder", it)
                onComplete(Result.failure(it))
            }
    }

    // =========================================================
    // LOGIC GIAO DỊCH ĐỊNH KỲ (RECURRING TRANSACTIONS)
    // =========================================================
    fun fetchRecurringTransactions(context: Context? = null) {
        context?.let { automationAppContext = it.applicationContext }
        val uid = auth.currentUser?.uid ?: return
        recurringListener?.remove()
        recurringListener = db.collection("users").document(uid).collection("recurring_transactions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FIRESTORE_ERROR", "Unable to listen to recurring transactions", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { document ->
                        runCatching {
                            SettingsFirestoreMapper.recurringFromMap(
                                documentId = document.id,
                                ownerUserId = uid,
                                data = document.data.orEmpty()
                            )
                        }
                            .onFailure {
                                Log.e("FIRESTORE_ERROR", "Skipping invalid recurring transaction ${document.id}", it)
                            }
                            .getOrNull()
                    }
                    _recurringTransactions.value = list
                    synchronizeRecurringTransactions(list)
                }
            }
    }

    fun addRecurringTransaction(
        recurring: RecurringTransaction,
        context: Context,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onComplete(Result.failure(IllegalStateException("Bạn cần đăng nhập để lưu giao dịch định kỳ")))
            return
        }
        val finalRecurring = recurring.copy(userId = uid)
        db.collection("users").document(uid).collection("recurring_transactions")
            .document(finalRecurring.id).set(SettingsFirestoreMapper.recurringToMap(finalRecurring))
            .addOnSuccessListener {
                _recurringTransactions.value = _recurringTransactions.value
                    .filterNot { it.id == finalRecurring.id } + finalRecurring
                NotificationHelper.showNotification(
                    context,
                    finalRecurring.id.hashCode(),
                    "WalletWise - Giao dịch định kỳ",
                    "Đã đặt giao dịch định kỳ '${finalRecurring.title}' (${finalRecurring.amount.toLong()}đ)"
                )
                synchronizeRecurringTransactions(listOf(finalRecurring), context.applicationContext)
                requestExactAlarmPermissionIfNeeded(context)
                onComplete(Result.success(Unit))
            }
            .addOnFailureListener {
                Log.e("FIRESTORE_ERROR", "Error adding recurring tx", it)
                onComplete(Result.failure(it))
            }
    }

    fun updateRecurringTransaction(
        recurring: RecurringTransaction,
        context: Context,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onComplete(Result.failure(IllegalStateException("Bạn cần đăng nhập để cập nhật giao dịch định kỳ")))
            return
        }
        val updated = recurring.copy(userId = uid)
        db.collection("users").document(uid).collection("recurring_transactions")
            .document(updated.id).set(SettingsFirestoreMapper.recurringToMap(updated))
            .addOnSuccessListener {
                _recurringTransactions.value = _recurringTransactions.value.map {
                    if (it.id == updated.id) updated else it
                }
                if (updated.isEnabled) {
                    synchronizeRecurringTransactions(listOf(updated), context.applicationContext)
                    requestExactAlarmPermissionIfNeeded(context)
                } else {
                    RecurringTransactionScheduler.cancel(context.applicationContext, updated.id)
                }
                onComplete(Result.success(Unit))
            }
            .addOnFailureListener {
                Log.e("FIRESTORE_ERROR", "Error updating recurring tx", it)
                onComplete(Result.failure(it))
            }
    }

    fun deleteRecurringTransaction(
        id: String,
        context: Context? = automationAppContext,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onComplete(Result.failure(IllegalStateException("Bạn cần đăng nhập để xóa giao dịch định kỳ")))
            return
        }
        val existing = _recurringTransactions.value.firstOrNull { it.id == id }
        val appContext = context?.applicationContext ?: automationAppContext
        appContext?.let { RecurringTransactionScheduler.cancel(it, id) }
        db.collection("users").document(uid).collection("recurring_transactions").document(id).delete()
            .addOnSuccessListener {
                _recurringTransactions.value = _recurringTransactions.value.filterNot { it.id == id }
                onComplete(Result.success(Unit))
            }
            .addOnFailureListener { error ->
                if (appContext != null && existing?.isEnabled == true) {
                    RecurringTransactionScheduler.schedule(appContext, existing)
                }
                Log.e("FIRESTORE_ERROR", "Error deleting recurring tx", error)
                onComplete(Result.failure(error))
            }
    }

    fun setRecurringTransactionEnabled(
        recurring: RecurringTransaction,
        isEnabled: Boolean,
        context: Context? = null,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onComplete(Result.failure(IllegalStateException("Bạn cần đăng nhập để cập nhật giao dịch định kỳ")))
            return
        }
        val updated = recurring.copy(userId = uid, isEnabled = isEnabled)
        _recurringTransactions.value = _recurringTransactions.value.map { if (it.id == updated.id) updated else it }
        db.collection("users").document(uid).collection("recurring_transactions")
            .document(updated.id).update(SettingsFirestoreMapper.enabledFields(isEnabled))
            .addOnSuccessListener {
                if (isEnabled && context != null) {
                    synchronizeRecurringTransactions(listOf(updated), context.applicationContext)
                    requestExactAlarmPermissionIfNeeded(context)
                } else if (!isEnabled) {
                    val appContext = context?.applicationContext ?: automationAppContext
                    if (appContext != null) RecurringTransactionScheduler.cancel(appContext, updated.id)
                }
                onComplete(Result.success(Unit))
            }
            .addOnFailureListener {
                _recurringTransactions.value = _recurringTransactions.value.map { current ->
                    if (current.id == recurring.id && current.isEnabled == isEnabled) recurring else current
                }
                Log.e("FIRESTORE_ERROR", "Error toggling recurring transaction", it)
                onComplete(Result.failure(it))
            }
    }

    /** Starts reminder and recurring-transaction scheduling once a context is available. */
    fun initializeScheduledAutomation(context: Context) {
        automationAppContext = context.applicationContext
        synchronizeReminders(_reminders.value, context.applicationContext)
        synchronizeRecurringTransactions(_recurringTransactions.value, context.applicationContext)
    }

    /**
     * Reconciles overdue rules while the app is open. The executor uses a
     * Firestore transaction, so this is safe to run beside an alarm callback.
     */
    fun checkAndExecuteRecurringTransactions(
        context: Context,
        list: List<RecurringTransaction> = _recurringTransactions.value
    ) {
        automationAppContext = context.applicationContext
        synchronizeRecurringTransactions(list, context.applicationContext)
    }

    private fun synchronizeReminders(
        list: List<Reminder>,
        context: Context? = automationAppContext
    ) {
        val appContext = context ?: return
        list.forEach { reminder ->
            if (reminder.isEnabled) {
                ReminderScheduler.schedule(appContext, reminder)
            } else {
                ReminderScheduler.cancel(appContext, reminder.id)
            }
        }
    }

    private fun synchronizeRecurringTransactions(
        list: List<RecurringTransaction>,
        context: Context? = automationAppContext
    ) {
        val appContext = context ?: return
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            list.forEach { recurring ->
                try {
                    if (!recurring.isEnabled) {
                        RecurringTransactionScheduler.cancel(appContext, recurring.id)
                        return@forEach
                    }

                    val result = RecurringTransactionExecutor.executeIfDue(appContext, uid, recurring)
                    result.recurring?.let { refreshedRecurring ->
                        RecurringTransactionScheduler.schedule(appContext, refreshedRecurring)
                    }
                    if (result.transactionWasCreated) {
                        NotificationHelper.showNotification(
                            appContext,
                            recurring.id.hashCode(),
                            "WalletWise - Giao dịch định kỳ",
                            "Đã tự động thêm giao dịch ${recurring.type}: ${recurring.title} (${recurring.amount.toLong()}đ)"
                        )
                        loadTransactions()
                    }
                } catch (error: Exception) {
                    // A bad legacy record, network outage, or Firestore rule must
                    // never terminate viewModelScope and close the app.
                    RecurringTransactionScheduler.scheduleRetry(
                        appContext,
                        uid,
                        recurring.id,
                        retryAttempt = 1
                    )
                    Log.e("RECURRING_SYNC", "Unable to process recurring transaction ${recurring.id}", error)
                }
            }
        }
    }

    override fun onCleared() {
        transactionsListener?.remove()
        profileListener?.remove()
        categorySessionController.close()
        budgetSessionController.close()
        remindersListener?.remove()
        recurringListener?.remove()
        authStateListener?.let(auth::removeAuthStateListener)
        super.onCleared()
    }

    private fun requestExactAlarmPermissionIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context !is Activity) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        }
    }

    // Kept only for reference while migrating existing users. All callers use
    // the atomic scheduler-based function above.
    private fun legacyCheckAndExecuteRecurringTransactions(context: Context, list: List<RecurringTransaction> = _recurringTransactions.value) {
        val uid = auth.currentUser?.uid ?: return
        val todayStr = LocalDate.now().toString()

        list.filter { it.isEnabled }.forEach { recurring ->
            if (recurring.lastExecutedDate != todayStr) {
                val newTx = Transaction(
                    userId = uid,
                    amount = recurring.amount,
                    type = recurring.type,
                    category = recurring.category,
                    paymentMethod = recurring.paymentMethod,
                    note = "[Định kỳ] ${recurring.title}${if (recurring.note.isNotBlank()) " - " + recurring.note else ""}",
                    timestamp = System.currentTimeMillis()
                )

                viewModelScope.launch {
                    transactionWriter.add(newTx, null, context)
                        .onSuccess {
                            val updated = recurring.copy(lastExecutedDate = todayStr)
                            db.collection("users").document(uid).collection("recurring_transactions")
                                .document(recurring.id).set(SettingsFirestoreMapper.recurringToMap(updated))

                            NotificationHelper.showNotification(
                                context,
                                recurring.id.hashCode(),
                                "WalletWise - Tự động trích tiền",
                                "Đã tự động thêm giao dịch ${recurring.type}: ${recurring.title} (${recurring.amount.toLong()}đ)"
                            )

                            loadTransactions()
                        }
                }
            }
        }
    }
}
