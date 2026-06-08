package com.example.walletwise.presentation.home

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walletwise.data.repository.TransactionRepositoryImpl
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.repository.TransactionRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

class TransactionViewModel(
    private val repository: TransactionRepository = TransactionRepositoryImpl()
) : ViewModel() {

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

    // Biến lưu trữ giao dịch đang được chọn để Sửa
    var transactionToEdit by mutableStateOf<Transaction?>(null)

    // CÁC BIẾN TRẠNG THÁI CHO TRỢ LÝ AI
    val aiAssistant = TransactionAIAssistant()
    var isAIProcessing by mutableStateOf(false)
    var aiFeedbackMessage by mutableStateOf("Xin chào! Bạn vừa chi tiêu gì vậy?")
    var aiPendingTransaction by mutableStateOf<Transaction?>(null)

    init {
        loadTransactions()
        loadUserProfile()
        checkAndGenerateFakeData()
        calculateAndGetStreak()
    }

    // --- LOGIC TÍNH TOÁN STREAK (LỬA) ---
    private fun calculateAndGetStreak() {
        val currentUser = auth.currentUser ?: return

        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val todayStr = today.format(formatter)

        val userRef = db.collection("USERS").document(currentUser.uid)

        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val lastActiveStr = document.getString("lastActiveDate")
                var currentStreak = document.getLong("streakCount")?.toInt() ?: 0

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
            "lastActiveDate" to todayStr,
            "streakCount" to streak
        )
        userRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                _streakCount.value = streak
            }
    }

    // --- HÀM BƠM DATA GIẢ ---
    private fun checkAndGenerateFakeData() {
        val currentUser = auth.currentUser
        val targetEmail = "sniper021003@gmail.com"
        if (currentUser == null || currentUser.email != targetEmail) return

        db.collection("TRANSACTIONS")
            .whereEqualTo("userId", currentUser.uid)
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

            db.collection("TRANSACTIONS").document(txId).set(fakeTx)
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

            repository.addTransaction(trans, imageUri, context)
                .onSuccess {
                    calculateAndGetStreak() // Tự động cập nhật lửa
                    loadTransactions()
                    onSuccess()
                }
                .onFailure { Log.e("ADD_TRANSACTION", "ERROR", it) }
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

            repository.updateTransaction(transaction, imageUri, context)
                .onSuccess {
                    calculateAndGetStreak() // Cập nhật lửa
                    loadTransactions()
                    onSuccess()
                }
                .onFailure { Log.e("ADD_TRANSACTION", "ERROR", it) }
            _isLoading.value = false
        }
    }

    fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(com.example.walletwise.domain.model.User::class.java)
                    _userProfile.value = user
                }
            }
    }
}