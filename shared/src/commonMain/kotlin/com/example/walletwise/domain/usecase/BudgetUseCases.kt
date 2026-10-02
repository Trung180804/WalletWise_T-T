package com.example.walletwise.domain.usecase

import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.repository.BudgetPlanRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.service.BudgetCalculator
import com.example.walletwise.domain.validation.BudgetInputException
import com.example.walletwise.domain.validation.BudgetValidationError
import com.example.walletwise.domain.validation.BudgetValidator
import kotlinx.coroutines.flow.Flow

sealed interface BudgetUseCaseResult<out T> {
    data class Success<T>(val value: T) : BudgetUseCaseResult<T>
    data class ValidationFailure(val error: BudgetValidationError) : BudgetUseCaseResult<Nothing>
    data class RepositoryFailure(val error: RepositoryError) : BudgetUseCaseResult<Nothing>
}

class ObserveBudgetPlanUseCase(private val repository: BudgetPlanRepository) {
    operator fun invoke(userId: String, monthKey: String): Flow<RepositoryResult<BudgetPlan?>> =
        repository.observeBudgetPlan(userId, monthKey)
}

class GetBudgetPlanUseCase(private val repository: BudgetPlanRepository) {
    suspend operator fun invoke(userId: String, monthKey: String): RepositoryResult<BudgetPlan?> =
        repository.getBudgetPlan(userId, monthKey)
}

class SaveBudgetPlanUseCase(private val repository: BudgetPlanRepository) {
    suspend operator fun invoke(
        userId: String,
        monthKey: String,
        amountText: String,
        ruleWireValue: String
    ): BudgetUseCaseResult<BudgetPlan> {
        if (userId.isBlank()) return BudgetUseCaseResult.ValidationFailure(BudgetValidationError.USER_ID_REQUIRED)
        if (!MONTH_KEY_REGEX.matches(monthKey)) {
            return BudgetUseCaseResult.ValidationFailure(BudgetValidationError.MONTH_KEY_INVALID)
        }
        val validated = BudgetValidator.validateInput(amountText, ruleWireValue).getOrElse { error ->
            return BudgetUseCaseResult.ValidationFailure(
                (error as? BudgetInputException)?.reason ?: BudgetValidationError.AMOUNT_INVALID
            )
        }
        val allocation = BudgetCalculator.allocate(validated.amount, validated.rule)
        val plan = BudgetPlan(
            id = userId,
            monthYear = monthKey,
            totalBudget = validated.amount,
            ruleType = validated.rule.wireValue,
            needsLimit = allocation.needs,
            wantsLimit = allocation.wants,
            savingsLimit = allocation.savings
        )
        return when (val result = repository.saveBudgetPlan(userId, plan)) {
            is RepositoryResult.Success -> BudgetUseCaseResult.Success(plan)
            is RepositoryResult.Failure -> BudgetUseCaseResult.RepositoryFailure(result.error)
        }
    }

    private companion object {
        val MONTH_KEY_REGEX = Regex("^(0[1-9]|1[0-2])-\\d{4}$")
    }
}
