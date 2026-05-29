package com.example.walletwise.domain.repository

import android.content.Context
import android.net.Uri
import com.example.walletwise.domain.model.Transaction

interface TransactionRepository {
    suspend fun addTransaction(transaction: Transaction, localImageUri: Uri?, context: Context): Result<Boolean>
    suspend fun getTransactions(): Result<List<Transaction>>
}