package com.example.walletwise.domain.validation

import com.example.walletwise.domain.model.Transaction

enum class TransactionValidationError {
    INVALID_AMOUNT,
    INVALID_TYPE,
    MISSING_PAYMENT_METHOD,
    MISSING_CATEGORY,
    INVALID_TIMESTAMP,
    MISSING_TRANSACTION_ID
}

class TransactionValidationException(
    val error: TransactionValidationError,
    message: String
) : IllegalArgumentException(message)

object TransactionValidator {
    private val supportedTypes = setOf("Thu", "Chi")

    fun validateForAdd(transaction: Transaction): Result<Unit> = validate(transaction)

    fun validateForUpdate(transaction: Transaction): Result<Unit> {
        if (transaction.id.isBlank()) {
            return invalid(
                TransactionValidationError.MISSING_TRANSACTION_ID,
                "Thiếu mã giao dịch cần cập nhật."
            )
        }
        return validate(transaction)
    }

    private fun validate(transaction: Transaction): Result<Unit> {
        if (!transaction.amount.isFinite() || transaction.amount <= 0.0) {
            return invalid(
                TransactionValidationError.INVALID_AMOUNT,
                "Số tiền giao dịch phải lớn hơn 0!"
            )
        }
        if (transaction.type !in supportedTypes) {
            return invalid(
                TransactionValidationError.INVALID_TYPE,
                "Loại giao dịch phải là Thu hoặc Chi."
            )
        }
        if (transaction.paymentMethod.isBlank()) {
            return invalid(
                TransactionValidationError.MISSING_PAYMENT_METHOD,
                "Vui lòng chọn phương thức thanh toán."
            )
        }
        if (transaction.category.isBlank()) {
            return invalid(
                TransactionValidationError.MISSING_CATEGORY,
                "Vui lòng chọn danh mục."
            )
        }
        if (transaction.timestamp <= 0L) {
            return invalid(
                TransactionValidationError.INVALID_TIMESTAMP,
                "Thời gian giao dịch không hợp lệ."
            )
        }
        return Result.success(Unit)
    }

    private fun invalid(
        error: TransactionValidationError,
        message: String
    ): Result<Unit> = Result.failure(TransactionValidationException(error, message))
}
