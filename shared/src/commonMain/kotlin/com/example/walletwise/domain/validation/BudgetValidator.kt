package com.example.walletwise.domain.validation

import com.example.walletwise.domain.model.BudgetRule

enum class BudgetValidationError {
    AMOUNT_REQUIRED,
    AMOUNT_INVALID,
    AMOUNT_MUST_BE_POSITIVE,
    RULE_INVALID,
    USER_ID_REQUIRED,
    MONTH_KEY_INVALID
}

data class ValidBudgetInput(
    val amount: Double,
    val rule: BudgetRule
)

object BudgetValidator {
    fun validateInput(amountText: String, ruleWireValue: String): Result<ValidBudgetInput> {
        if (amountText.isBlank()) return Result.failure(BudgetInputException(BudgetValidationError.AMOUNT_REQUIRED))
        val amount = amountText.toDoubleOrNull()
            ?: return Result.failure(BudgetInputException(BudgetValidationError.AMOUNT_INVALID))
        if (!amount.isFinite()) return Result.failure(BudgetInputException(BudgetValidationError.AMOUNT_INVALID))
        if (amount <= 0.0) return Result.failure(BudgetInputException(BudgetValidationError.AMOUNT_MUST_BE_POSITIVE))
        val rule = BudgetRule.fromWireValue(ruleWireValue)
            ?: return Result.failure(BudgetInputException(BudgetValidationError.RULE_INVALID))
        return Result.success(ValidBudgetInput(amount, rule))
    }
}

class BudgetInputException(val reason: BudgetValidationError) : IllegalArgumentException(reason.name)
