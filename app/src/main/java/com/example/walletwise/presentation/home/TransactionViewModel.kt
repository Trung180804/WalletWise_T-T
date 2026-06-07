package com.example.walletwise.presentation.home

import android.net.Uri
import android.util.Log
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class TransactionViewModel(
    private val repository: TransactionRepository = TransactionRepositoryImpl()
) : ViewModel() {

    // Danh sách giao dịch
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    // 👉 STATE FLOW ĐỂ LƯU SỐ STREAK HIỂN THỊ LÊN UI
    private val _streakCount = MutableStateFlow(0)
    val streakCount: StateFlow<Int> = _streakCount.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    init {
        loadTransactions()
        checkAndGenerateFakeData()

        // Gọi hàm tính toán Streak ngay khi khởi tạo
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
                    // Lần đầu sử dụng app
                    currentStreak = 1
                    updateStreakToFirebase(userRef, todayStr, currentStreak)
                } else {
                    try {
                        val lastActiveDate = LocalDate.parse(lastActiveStr, formatter)
                        val daysBetween = ChronoUnit.DAYS.between(lastActiveDate, today)

                        when {
                            daysBetween == 0L -> {
                                // Đã mở app hôm nay rồi, giữ nguyên streak
                                _streakCount.value = currentStreak
                            }
                            daysBetween == 1L -> {
                                // Mở app liên tiếp, tăng streak
                                currentStreak += 1
                                updateStreakToFirebase(userRef, todayStr, currentStreak)
                            }
                            daysBetween > 1L -> {
                                // Bỏ lỡ 1 ngày trở lên -> Chuỗi đứt, quay về 1
                                currentStreak = 1
                                updateStreakToFirebase(userRef, todayStr, currentStreak)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("StreakLogic", "Lỗi format ngày tháng: ${e.message}")
                        // Bị lỗi format -> Khởi tạo lại chuỗi = 1
                        updateStreakToFirebase(userRef, todayStr, 1)
                    }
                }
            } else {
                // Document USERS chưa tồn tại -> Tạo mới với streak = 1
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
                // Cập nhật StateFlow để UI tự động đổi
                _streakCount.value = streak
            }
    }

    // --- HÀM BƠM DATA GIẢ (Dùng để test giao diện Lịch) ---
    private fun checkAndGenerateFakeData() {
        val currentUser = auth.currentUser

        // Thay đúng email bạn dùng để test
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

        val currentMonth = java.time.YearMonth.now()

        for (i in 1..30) {
            val randomDay = (1..currentMonth.lengthOfMonth()).random()
            val date = currentMonth.atDay(randomDay)
            val timestamp = date.atTime((8..20).random(), (0..59).random())
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val txId = java.util.UUID.randomUUID().toString()

            val fakeTx = hashMapOf(
                "id" to txId,
                "userId" to uid,
                "amount" to (10..500).random() * 1000.0,
                "category" to listOf("Ăn uống", "Mua sắm", "Tiền nhà", "Siêu thị").random(),
                "type" to listOf("Chi", "Thu").random(),
                "paymentMethod" to listOf("Tiền mặt", "Chuyển khoản", "Thẻ tín dụng").random(),
                "timestamp" to timestamp,
                "imageUrl" to if ((1..10).random() > 3) sampleImages.random() else ""
            )

            db.collection("TRANSACTIONS").document(txId).set(fakeTx)
        }
        loadTransactions()
    }

    // Hàm gọi dữ liệu từ Firebase/Offline Cache
    fun loadTransactions() {
        viewModelScope.launch {
            repository.getTransactions()
                .onSuccess { list ->
                    // Sắp xếp giao dịch mới nhất lên đầu
                    _transactions.value = list.sortedByDescending { it.timestamp }
                }
        }
    }

    fun addTransaction(
        amount: Double,
        type: String,
        category: String,
        note: String,
        paymentMethod: String,
        imageUri: Uri?,
        context: android.content.Context,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val trans = Transaction(
                amount = amount,
                type = type,
                category = category,
                note = note,
                paymentMethod = paymentMethod
            )

            repository.addTransaction(trans, imageUri, context)
                .onSuccess {
                    loadTransactions()

                    // Cập nhật lại streak nếu cần (khi user thêm giao dịch cũng là một hành động mở app)
                    calculateAndGetStreak()

                    onSuccess()
                }
        }
    }
}