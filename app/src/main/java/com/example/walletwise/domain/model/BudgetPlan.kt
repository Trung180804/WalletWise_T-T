package com.example.walletwise.domain.model

data class BudgetPlan(
    val id: String = "",
    val monthYear: String = "",
    val totalBudget: Double = 0.0,
    val ruleType: String = "50_30_20", // "50_30_20" or "JARS"
    
    val needsLimit: Double = 0.0,
    val wantsLimit: Double = 0.0,
    val savingsLimit: Double = 0.0,

    val needsSpent: Double = 0.0,
    val wantsSpent: Double = 0.0,
    val savingsSpent: Double = 0.0
)
