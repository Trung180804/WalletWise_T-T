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

    fun addTransaction(amount: Double, type: String, category: String, note: String, imageUri: Uri?, context: android.content.Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val trans = Transaction(amount = amount, type = type, category = category, note = note)
            // Truyền thêm context vào đây
            repository.addTransaction(trans, imageUri, context)
                .onSuccess {
                    loadTransactions()
                    onSuccess()
                }
        }
    }
}