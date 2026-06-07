package com.example.walletwise.presentation.home

import android.content.Context
import android.net.Uri
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
import org.json.JSONObject // 👉 Import xử lý JSON của AI
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

class TransactionViewModel(
    private val repository: TransactionRepository = TransactionRepositoryImpl()
) : ViewModel() {

    // Danh sách giao dịch
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _userProfile = MutableStateFlow<com.example.walletwise.domain.model.User?>(null)
    val userProfile: StateFlow<com.example.walletwise.domain.model.User?> = _userProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Biến lưu trữ giao dịch đang được chọn để Sửa
    var transactionToEdit by mutableStateOf<Transaction?>(null)

    // 👉 1. CÁC BIẾN TRẠNG THÁI CHO TRỢ LÝ AI
    val aiAssistant = TransactionAIAssistant()
    var isAIProcessing by mutableStateOf(false)
    var aiFeedbackMessage by mutableStateOf("Xin chào! Bạn vừa chi tiêu gì vậy?")
    var aiPendingTransaction by mutableStateOf<Transaction?>(null) // Giao dịch chờ xác nhận

    init {
        loadTransactions()
        loadUserProfile()
        // Gọi hàm bơm dữ liệu test
        checkAndGenerateFakeData()
    }

    // --- HÀM BƠM DATA GIẢ ---
    private fun checkAndGenerateFakeData() {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        val targetEmail = "sniper021003@gmail.com"
        if (currentUser == null || currentUser.email != targetEmail) return

        val db = FirebaseFirestore.getInstance()
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
        // Danh sách ảnh mẫu từ Picsum
        val sampleImages = listOf(
            "https://picsum.photos/id/42/300/300",
            "https://picsum.photos/id/163/300/300",
            "https://picsum.photos/id/225/300/300",
            "https://picsum.photos/id/292/300/300",
            "https://picsum.photos/id/365/300/300"
        )
        val streakCount = 10
        val now = java.time.LocalDateTime.now()

        for (i in 0 until streakCount) {
            val date = now.minusDays(i.toLong())

            // Nếu là ngày hôm nay, chỉ được random giờ từ 0 đến giờ hiện tại
            val maxHour = if (i == 0) now.hour else 23
            val randomHour = (0..maxHour).random()

            // Nếu là giờ hiện tại, chỉ được random phút đến phút hiện tại
            val maxMinute = if (i == 0 && randomHour == now.hour) now.minute else 59
            val randomMinute = (0..maxMinute).random()

            val timestamp = date.withHour(randomHour)
                .withMinute(randomMinute)
                .atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli()

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

        val userRef = FirebaseFirestore.getInstance().collection("users").document(uid)
        userRef.update(mapOf(
            "currentStreak" to streakCount,
            "lastRecordDate" to LocalDate.now(ZoneId.systemDefault()).toString()
        )).addOnSuccessListener {
            loadTransactions()
        }
    }

    // Hàm gọi dữ liệu từ Firebase
    fun loadTransactions() {
        viewModelScope.launch {
            repository.getTransactions()
                .onSuccess { list ->
                    _transactions.value = list.sortedByDescending { it.timestamp }
                }
        }
    }

    private fun updateUserStreak() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        val userRef = db.collection("users").document(uid)

        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val lastDate = document.getString("lastRecordDate") ?: ""
                val currentStreak = document.getLong("currentStreak")?.toInt() ?: 0

                // Lấy ngày hôm nay và ngày hôm qua theo định dạng chuẩn "yyyy-MM-dd"
                val today = LocalDate.now(ZoneId.systemDefault())
                val todayStr = today.toString()
                val yesterdayStr = today.minusDays(1).toString()

                val updates = mutableMapOf<String, Any>()

                when (lastDate) {
                    todayStr -> {
                        // Đã ghi chép hôm nay rồi, chuỗi không đổi
                        return@addOnSuccessListener
                    }
                    yesterdayStr -> {
                        // Nối tiếp chuỗi thành công
                        updates["currentStreak"] = currentStreak + 1
                        updates["lastRecordDate"] = todayStr
                    }
                    else -> {
                        // Đứt chuỗi (hoặc ghi chép lần đầu tiên) -> Bắt đầu lại từ 1
                        updates["currentStreak"] = 1
                        updates["lastRecordDate"] = todayStr
                    }
                }

                userRef.update(updates)
            }
        }
    }

    // 👉 2. HÀM XỬ LÝ TEXT GỬI CHO AI
    fun processAITransaction(userInput: String) {
        viewModelScope.launch {
            isAIProcessing = true
            aiFeedbackMessage = "Đang suy nghĩ..."
            aiPendingTransaction = null

            val jsonString = aiAssistant.analyzeTransactionText(userInput)

            if (jsonString != null) {
                try {
                    val cleanJson = jsonString
                        .replace("```json", "")
                        .replace("```", "").trim()
                    val jsonObject = JSONObject(cleanJson)

                    // Kiểm tra trường missing_prompt từ System Instruction
                    val missingPrompt = jsonObject.optString("missing_prompt", "")

                    if (missingPrompt.isNotEmpty()) {
                        aiFeedbackMessage = missingPrompt
                    } else {
                        // Trích xuất các giá trị theo đúng Entity
                        val amount = jsonObject.optDouble("amount", 0.0)
                        val category = jsonObject.optString("category", "Khác")
                        val type = jsonObject.optString("type", "Chi")
                        val paymentMethod = jsonObject.optString("paymentMethod", "Tiền mặt")
                        val note = jsonObject.optString("note", "")

                        // Đưa dữ liệu vào Entity Transaction
                        aiPendingTransaction = Transaction(
                            amount = amount,
                            category = category,
                            type = type,
                            paymentMethod = paymentMethod,
                            note = note,
                            timestamp = System.currentTimeMillis() // Mặc định là giờ hệ thống
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

    // 👉 3. HÀM DỌN DẸP KHI ĐÓNG BẢNG AI
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
                    updateUserStreak()
                    loadTransactions()
                    onSuccess()
                }
                .onFailure {
                    android.util.Log.e(
                        "ADD_TRANSACTION",
                        "ERROR",
                        it
                    )
                }
            _isLoading.value = false
        }
    }

    // Hàm Xóa giao dịch
    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(transactionId)
                .onSuccess {
                    val currentList = _transactions.value.toMutableList()
                    currentList.removeAll { it.id == transactionId }
                    _transactions.value = currentList

                    loadTransactions()
                }
        }
    }

    // Hàm Cập nhật (Sửa) giao dịch (Sửa lại)
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
                    updateUserStreak()
                    loadTransactions()
                    onSuccess()
                }
                .onFailure {
                    android.util.Log.e(
                        "ADD_TRANSACTION",
                        "ERROR",
                        it
                    )
                }
            _isLoading.value = false
        }
    }

    // Hàm lấy dữ liệu User
    fun loadUserProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Dùng addSnapshotListener để app tự cập nhật nếu thông tin thay đổi
        db.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    // Chuyển dữ liệu từ Firebase về model User
                    // Đảm bảo model User của bạn có hàm constructor không tham số
                    val user = snapshot.toObject(com.example.walletwise.domain.model.User::class.java)
                    _userProfile.value = user
                }
            }
    }
}