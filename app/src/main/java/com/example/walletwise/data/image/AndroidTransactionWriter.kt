package com.example.walletwise.data.image

import android.content.Context
import android.net.Uri
import com.example.walletwise.domain.model.AddTransactionInput
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.model.UpdateTransactionInput
import com.example.walletwise.domain.repository.ImageUploader
import com.example.walletwise.domain.repository.TransactionRepository
import com.example.walletwise.domain.usecase.AddTransactionUseCase
import com.example.walletwise.domain.usecase.UpdateTransactionUseCase

class AndroidTransactionWriter(
    repository: TransactionRepository,
    private val imageReader: AndroidImageReader,
    imageUploader: ImageUploader
) {
    private val addTransaction = AddTransactionUseCase(repository, imageUploader)
    private val updateTransaction = UpdateTransactionUseCase(repository, imageUploader)

    suspend fun add(
        transaction: Transaction,
        imageUri: Uri?,
        context: Context
    ): Result<Boolean> {
        val image = readImage(imageUri, context).getOrElse { return Result.failure(it) }
        return addTransaction(AddTransactionInput(transaction, image))
    }

    suspend fun update(
        transaction: Transaction,
        replacementImageUri: Uri?,
        context: Context
    ): Result<Boolean> {
        val image = readImage(replacementImageUri, context).getOrElse { return Result.failure(it) }
        return updateTransaction(UpdateTransactionInput(transaction, image))
    }

    private suspend fun readImage(
        imageUri: Uri?,
        context: Context
    ) = if (imageUri == null) {
        Result.success(null)
    } else {
        imageReader.read(imageUri, context)
    }
}
