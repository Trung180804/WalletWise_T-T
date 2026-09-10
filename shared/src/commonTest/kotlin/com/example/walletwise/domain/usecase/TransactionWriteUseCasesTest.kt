package com.example.walletwise.domain.usecase

import com.example.walletwise.domain.model.AddTransactionInput
import com.example.walletwise.domain.model.ImageUpload
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.domain.model.UpdateTransactionInput
import com.example.walletwise.domain.repository.ImageUploader
import com.example.walletwise.domain.repository.TransactionRepository
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TransactionWriteUseCasesTest {
    @Test
    fun addWithoutImage_doesNotUploadAndPersistsTransaction() = runSuspend {
        val repository = RecordingTransactionRepository()
        val uploader = RecordingImageUploader(Result.success("https://example.com/new.jpg"))
        val transaction = validTransaction(id = "", imageUrl = "")

        val result = AddTransactionUseCase(repository, uploader)(AddTransactionInput(transaction))

        assertTrue(result.isSuccess)
        assertEquals(0, uploader.callCount)
        assertSame(transaction, repository.added)
    }

    @Test
    fun addWithImage_uploadsBeforePersistingUrl() = runSuspend {
        val repository = RecordingTransactionRepository()
        val uploader = RecordingImageUploader(Result.success("https://example.com/new.jpg"))

        val result = AddTransactionUseCase(repository, uploader)(
            AddTransactionInput(validTransaction(id = ""), sampleImage())
        )

        assertTrue(result.isSuccess)
        assertEquals(1, uploader.callCount)
        assertEquals("https://example.com/new.jpg", repository.added?.imageUrl)
    }

    @Test
    fun addUploadFailure_neverCreatesRepositoryData() = runSuspend {
        val repository = RecordingTransactionRepository()
        val failure = IllegalStateException("upload failed")
        val uploader = RecordingImageUploader(Result.failure(failure))

        val result = AddTransactionUseCase(repository, uploader)(
            AddTransactionInput(validTransaction(id = ""), sampleImage())
        )

        assertSame(failure, result.exceptionOrNull())
        assertNull(repository.added)
    }

    @Test
    fun blankUploadUrl_isFailureAndNeverCreatesRepositoryData() = runSuspend {
        val repository = RecordingTransactionRepository()
        val uploader = RecordingImageUploader(Result.success("  "))

        val result = AddTransactionUseCase(repository, uploader)(
            AddTransactionInput(validTransaction(id = ""), sampleImage())
        )

        assertTrue(result.isFailure)
        assertNull(repository.added)
    }

    @Test
    fun updateWithoutImage_preservesExistingImageUrl() = runSuspend {
        val repository = RecordingTransactionRepository()
        val uploader = RecordingImageUploader(Result.success("https://example.com/new.jpg"))
        val transaction = validTransaction(imageUrl = "https://example.com/existing.jpg")

        val result = UpdateTransactionUseCase(repository, uploader)(UpdateTransactionInput(transaction))

        assertTrue(result.isSuccess)
        assertEquals(0, uploader.callCount)
        assertEquals("https://example.com/existing.jpg", repository.updated?.imageUrl)
    }

    @Test
    fun updateWithImage_replacesUrlOnlyAfterSuccessfulUpload() = runSuspend {
        val repository = RecordingTransactionRepository()
        val uploader = RecordingImageUploader(Result.success("https://example.com/new.jpg"))

        val result = UpdateTransactionUseCase(repository, uploader)(
            UpdateTransactionInput(
                validTransaction(imageUrl = "https://example.com/existing.jpg"),
                sampleImage()
            )
        )

        assertTrue(result.isSuccess)
        assertEquals("https://example.com/new.jpg", repository.updated?.imageUrl)
    }

    @Test
    fun updateUploadFailure_neverWritesOrLosesExistingTransaction() = runSuspend {
        val repository = RecordingTransactionRepository()
        val failure = IllegalStateException("upload failed")
        val uploader = RecordingImageUploader(Result.failure(failure))
        val existing = validTransaction(imageUrl = "https://example.com/existing.jpg")

        val result = UpdateTransactionUseCase(repository, uploader)(
            UpdateTransactionInput(existing, sampleImage())
        )

        assertSame(failure, result.exceptionOrNull())
        assertNull(repository.updated)
        assertEquals("https://example.com/existing.jpg", existing.imageUrl)
    }

    @Test
    fun validationFailure_happensBeforeUploadAndRepository() = runSuspend {
        val repository = RecordingTransactionRepository()
        val uploader = RecordingImageUploader(Result.success("https://example.com/new.jpg"))

        val result = AddTransactionUseCase(repository, uploader)(
            AddTransactionInput(validTransaction(id = "", amount = 0.0), sampleImage())
        )

        assertTrue(result.isFailure)
        assertEquals(0, uploader.callCount)
        assertNull(repository.added)
    }

    private fun validTransaction(
        id: String = "tx-1",
        amount: Double = 100_000.0,
        imageUrl: String = ""
    ) = Transaction(
        id = id,
        userId = "user-1",
        type = "Chi",
        paymentMethod = "Tiền mặt",
        amount = amount,
        category = "Ăn uống",
        note = "",
        timestamp = 1_725_840_000_000L,
        imageUrl = imageUrl
    )

    private fun sampleImage() = ImageUpload(
        bytes = byteArrayOf(1, 2, 3),
        contentType = "image/jpeg",
        fileName = "receipt.jpg"
    )
}

private class RecordingImageUploader(
    private val uploadResult: Result<String>
) : ImageUploader {
    var callCount = 0

    override suspend fun upload(image: ImageUpload): Result<String> {
        callCount += 1
        return uploadResult
    }
}

private class RecordingTransactionRepository : TransactionRepository {
    var added: Transaction? = null
    var updated: Transaction? = null

    override suspend fun addTransaction(transaction: Transaction): Result<Boolean> {
        added = transaction
        return Result.success(true)
    }

    override suspend fun getTransactions(): Result<List<Transaction>> = Result.success(emptyList())

    override suspend fun deleteTransaction(transactionId: String): Result<Boolean> = Result.success(true)

    override suspend fun updateTransaction(transaction: Transaction): Result<Boolean> {
        updated = transaction
        return Result.success(true)
    }
}

private fun runSuspend(block: suspend () -> Unit) {
    var failure: Throwable? = null
    block.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
            failure = result.exceptionOrNull()
        }
    })
    failure?.let { throw it }
}
