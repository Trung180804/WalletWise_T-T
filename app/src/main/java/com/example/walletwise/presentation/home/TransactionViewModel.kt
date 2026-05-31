package com.example.walletwise.presentation.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walletwise.data.repository.TransactionRepositoryImpl
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransactionViewModel(
    private val repository: TransactionRepository = TransactionRepositoryImpl()
) : ViewModel() {

    // Danh sách giao dịch
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    init {
        loadTransactions()
        // 👉 Gọi hàm bơm dữ liệu test
        checkAndGenerateFakeData()
    }

    // --- HÀM BƠM DATA GIẢ (Dùng để test giao diện Lịch) ---
    private fun checkAndGenerateFakeData() {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        // Thay đúng email bạn dùng để test
        val targetEmail = "sniper021003@gmail.com"
        if (currentUser == null || currentUser.email != targetEmail) return

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
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

    private fun generateDataForUser(db: com.google.firebase.firestore.FirebaseFirestore, uid: String) {
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
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

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

    // 👉 1. Thêm tham số paymentMethod: String vào đây
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
            // 👉 2. Nhét paymentMethod vào trong Transaction
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
                    onSuccess()
                }
        }
    }
}