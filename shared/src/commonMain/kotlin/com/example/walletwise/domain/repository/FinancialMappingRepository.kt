package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.BudgetRule

interface FinancialMappingRepository {
    suspend fun load(userId: String, rule: BudgetRule): Result<Map<String, String>>
    suspend fun save(userId: String, rule: BudgetRule, mapping: Map<String, String>): Result<Boolean>
}
