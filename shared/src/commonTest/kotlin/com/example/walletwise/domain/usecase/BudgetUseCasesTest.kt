package com.example.walletwise.domain.usecase

import com.example.walletwise.domain.model.BUDGET_RULE_50_30_20
import com.example.walletwise.domain.model.BUDGET_RULE_JARS
import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.repository.BudgetPlanRepository
import com.example.walletwise.domain.result.RepositoryError
import com.example.walletwise.domain.result.RepositoryErrorCode
import com.example.walletwise.domain.result.RepositoryResult
import com.example.walletwise.domain.validation.BudgetValidationError
import com.example.walletwise.domain.validation.BudgetValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BudgetUseCasesTest {
    @Test
    fun inputValidationCoversBlankInvalidNonFiniteNonPositiveAndRules() {
        assertValidation("", BUDGET_RULE_50_30_20, BudgetValidationError.AMOUNT_REQUIRED)
        assertValidation("abc", BUDGET_RULE_50_30_20, BudgetValidationError.AMOUNT_INVALID)
        assertValidation("1e999", BUDGET_RULE_50_30_20, BudgetValidationError.AMOUNT_INVALID)
        assertValidation("0", BUDGET_RULE_50_30_20, BudgetValidationError.AMOUNT_MUST_BE_POSITIVE)
        assertValidation("-1", BUDGET_RULE_50_30_20, BudgetValidationError.AMOUNT_MUST_BE_POSITIVE)
        assertValidation("100", "UNKNOWN", BudgetValidationError.RULE_INVALID)
        assertEquals(100.0, BudgetValidator.validateInput("100", BUDGET_RULE_JARS).getOrThrow().amount)
    }

    @Test
    fun saveBuildsExistingWirePlanAndWaitsForRepositorySuccess() = runTest {
        val repository = RecordingBudgetRepository()
        val result = SaveBudgetPlanUseCase(repository)("uid-1", "09-2026", "10000000", BUDGET_RULE_JARS)

        val plan = assertIs<BudgetUseCaseResult.Success<BudgetPlan>>(result).value
        assertEquals("uid-1", plan.id)
        assertEquals("09-2026", plan.monthYear)
        assertEquals(5_500_000.0, plan.needsLimit)
        assertEquals(1_000_000.0, plan.wantsLimit)
        assertEquals(3_500_000.0, plan.savingsLimit)
        assertEquals(plan, repository.saved)
    }

    @Test
    fun invalidMonthOrRepositoryFailureDoesNotPretendSuccess() = runTest {
        val repository = RecordingBudgetRepository()
        val invalid = SaveBudgetPlanUseCase(repository)("uid", "9-2026", "100", BUDGET_RULE_50_30_20)
        assertEquals(BudgetValidationError.MONTH_KEY_INVALID, assertIs<BudgetUseCaseResult.ValidationFailure>(invalid).error)
        assertNull(repository.saved)

        repository.failSave = true
        val failed = SaveBudgetPlanUseCase(repository)("uid", "09-2026", "100", BUDGET_RULE_50_30_20)
        assertIs<BudgetUseCaseResult.RepositoryFailure>(failed)
    }

    private fun assertValidation(text: String, rule: String, expected: BudgetValidationError) {
        val error = BudgetValidator.validateInput(text, rule).exceptionOrNull()
        assertEquals(expected, (error as com.example.walletwise.domain.validation.BudgetInputException).reason)
    }
}

private class RecordingBudgetRepository : BudgetPlanRepository {
    var saved: BudgetPlan? = null
    var failSave = false
    private val failure = RepositoryResult.Failure(RepositoryError(RepositoryErrorCode.UNKNOWN, "failed"))

    override fun observeBudgetPlan(userId: String, monthYear: String): Flow<RepositoryResult<BudgetPlan?>> =
        flowOf(RepositoryResult.Success(null))
    override suspend fun getBudgetPlan(userId: String, monthYear: String): RepositoryResult<BudgetPlan?> =
        RepositoryResult.Success(null)
    override suspend fun saveBudgetPlan(userId: String, plan: BudgetPlan): RepositoryResult<Unit> {
        if (failSave) return failure
        saved = plan
        return RepositoryResult.Success(Unit)
    }
}
