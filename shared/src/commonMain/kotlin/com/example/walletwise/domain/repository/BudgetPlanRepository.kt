package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.flow.Flow

interface BudgetPlanRepository {
    fun observeBudgetPlan(
        userId: String,
        monthYear: String
    ): Flow<RepositoryResult<BudgetPlan?>>

    suspend fun getBudgetPlan(
        userId: String,
        monthYear: String
    ): RepositoryResult<BudgetPlan?>

    suspend fun saveBudgetPlan(
        userId: String,
        plan: BudgetPlan
    ): RepositoryResult<Unit>
}
