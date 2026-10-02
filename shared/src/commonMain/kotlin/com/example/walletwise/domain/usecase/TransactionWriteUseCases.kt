package com.example.walletwise.domain.usecase

import com.example.walletwise.domain.model.AddTransactionInput
import com.example.walletwise.domain.model.ImageUpload
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.model.UpdateTransactionInput
import com.example.walletwise.domain.repository.ImageUploader
import com.example.walletwise.domain.repository.TransactionRepository
import com.example.walletwise.domain.validation.TransactionValidator

class TransactionImageUploadException(message: String) : IllegalStateException(message)

/** Builds automatic writes through the same validation contract used by manual transaction adds. */
class CreateRecurringTransactionWriteUseCase {
    operator fun invoke(
        recurring: RecurringTransaction,
        userId: String,
        transactionId: String,
        executedAtEpochMilliseconds: Long
    ): Result<Transaction> {
        val transaction = Transaction(
            id = transactionId,
            userId = userId,
            amount = recurring.amount,
            type = recurring.type,
            category = recurring.category,
            paymentMethod = recurring.paymentMethod,
            note = "[Định kỳ] ${recurring.title}" +
                if (recurring.note.isBlank()) "" else " - ${recurring.note}",
            timestamp = executedAtEpochMilliseconds
        )
        TransactionValidator.validateForAdd(transaction).exceptionOrNull()?.let {
            return Result.failure(it)
        }
        return Result.success(transaction)
    }
}

class AddTransactionUseCase(
    private val repository: TransactionRepository,
    private val imageUploader: ImageUploader
) {
    suspend operator fun invoke(input: AddTransactionInput): Result<Boolean> {
        TransactionValidator.validateForAdd(input.transaction).exceptionOrNull()?.let {
            return Result.failure(it)
        }

        val transaction = attachUploadedImage(input.transaction, input.image)
            .getOrElse { return Result.failure(it) }

        return repository.addTransaction(transaction)
    }

    private suspend fun attachUploadedImage(
        transaction: Transaction,
        image: ImageUpload?
    ): Result<Transaction> {
        if (image == null) return Result.success(transaction)
        if (image.bytes.isEmpty()) {
            return Result.failure(TransactionImageUploadException("Dữ liệu ảnh trống."))
        }

        val imageUrl = imageUploader.upload(image)
            .getOrElse { return Result.failure(it) }
        if (imageUrl.isBlank()) {
            return Result.failure(TransactionImageUploadException("Không nhận được URL ảnh sau khi tải lên."))
        }
        return Result.success(transaction.copy(imageUrl = imageUrl))
    }
}

class UpdateTransactionUseCase(
    private val repository: TransactionRepository,
    private val imageUploader: ImageUploader
) {
    suspend operator fun invoke(input: UpdateTransactionInput): Result<Boolean> {
        TransactionValidator.validateForUpdate(input.transaction).exceptionOrNull()?.let {
            return Result.failure(it)
        }

        val transaction = attachReplacementImage(input.transaction, input.replacementImage)
            .getOrElse { return Result.failure(it) }

        return repository.updateTransaction(transaction)
    }

    private suspend fun attachReplacementImage(
        transaction: Transaction,
        image: ImageUpload?
    ): Result<Transaction> {
        if (image == null) return Result.success(transaction)
        if (image.bytes.isEmpty()) {
            return Result.failure(TransactionImageUploadException("Dữ liệu ảnh trống."))
        }

        val imageUrl = imageUploader.upload(image)
            .getOrElse { return Result.failure(it) }
        if (imageUrl.isBlank()) {
            return Result.failure(TransactionImageUploadException("Không nhận được URL ảnh sau khi tải lên."))
        }
        return Result.success(transaction.copy(imageUrl = imageUrl))
    }
}
