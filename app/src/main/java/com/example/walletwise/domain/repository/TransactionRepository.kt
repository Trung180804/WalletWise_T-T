package com.example.walletwise.domain.repository

import android.content.Context
import android.net.Uri
import com.example.walletwise.domain.model.Transaction

interface TransactionRepository {
    // Thêm mới
    suspend fun addTransaction(transaction: Transaction, localImageUri: Uri?, context: Context): Result<Boolean>

    // Lấy danh sách
    suspend fun getTransactions(): Result<List<Transaction>>

    suspend fun deleteTransaction(transactionId: String): Result<Boolean>

    suspend fun updateTransaction(
        transaction: Transaction,
        localImageUri: Uri?,
        context: Context
    ): Result<Boolean>
}