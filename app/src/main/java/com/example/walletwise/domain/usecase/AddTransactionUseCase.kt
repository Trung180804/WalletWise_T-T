package com.example.walletwise.domain.usecase

import android.content.Context
import android.net.Uri
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.repository.TransactionRepository

class AddTransactionUseCase(private val repository: TransactionRepository) {

    suspend operator fun invoke(transaction: Transaction, imageUri: Uri?, context: Context): Result<Boolean> {

        // Kiểm tra logic: Số tiền phải lớn hơn 0
        if (transaction.amount <= 0.0) {
            return Result.failure(Exception("Số tiền giao dịch phải lớn hơn 0!"))
        }

        // Truyền thêm biến context vào hàm addTransaction
        return repository.addTransaction(transaction, localImageUri = imageUri, context = context)
    }
}