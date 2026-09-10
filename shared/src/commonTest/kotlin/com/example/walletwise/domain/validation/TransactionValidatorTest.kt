package com.example.walletwise.domain.validation

import com.example.walletwise.domain.model.Transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransactionValidatorTest {
    @Test
    fun positiveFiniteAmount_isAccepted() {
        assertTrue(TransactionValidator.validateForAdd(validTransaction()).isSuccess)
    }

    @Test
    fun zeroNegativeNanAndInfiniteAmounts_areRejected() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { amount ->
            assertValidationError(
                TransactionValidationError.INVALID_AMOUNT,
                TransactionValidator.validateForAdd(validTransaction().copy(amount = amount))
            )
        }
    }

    @Test
    fun onlyEstablishedThuAndChiWireValues_areAccepted() {
        assertTrue(TransactionValidator.validateForAdd(validTransaction().copy(type = "Thu")).isSuccess)
        assertTrue(TransactionValidator.validateForAdd(validTransaction().copy(type = "Chi")).isSuccess)
        assertValidationError(
            TransactionValidationError.INVALID_TYPE,
            TransactionValidator.validateForAdd(validTransaction().copy(type = "Income"))
        )
    }

    @Test
    fun paymentMethod_isRequiredContent() {
        assertValidationError(
            TransactionValidationError.MISSING_PAYMENT_METHOD,
            TransactionValidator.validateForAdd(validTransaction().copy(paymentMethod = "  "))
        )
    }

    @Test
    fun category_isRequired() {
        assertValidationError(
            TransactionValidationError.MISSING_CATEGORY,
            TransactionValidator.validateForAdd(validTransaction().copy(category = ""))
        )
    }

    @Test
    fun timestampMustBePositive() {
        assertValidationError(
            TransactionValidationError.INVALID_TIMESTAMP,
            TransactionValidator.validateForAdd(validTransaction().copy(timestamp = 0L))
        )
    }

    @Test
    fun noteRemainsOptionalForAndroidCompatibility() {
        assertTrue(TransactionValidator.validateForAdd(validTransaction().copy(note = "")).isSuccess)
    }

    @Test
    fun updateRequiresExistingTransactionId() {
        assertValidationError(
            TransactionValidationError.MISSING_TRANSACTION_ID,
            TransactionValidator.validateForUpdate(validTransaction().copy(id = ""))
        )
    }

    private fun validTransaction() = Transaction(
        id = "tx-1",
        userId = "user-1",
        type = "Chi",
        paymentMethod = "Tiền mặt",
        amount = 100_000.0,
        category = "Ăn uống",
        note = "",
        timestamp = 1_725_840_000_000L,
        imageUrl = ""
    )

    private fun assertValidationError(
        expected: TransactionValidationError,
        result: Result<Unit>
    ) {
        val exception = assertIs<TransactionValidationException>(result.exceptionOrNull())
        assertEquals(expected, exception.error)
    }
}
