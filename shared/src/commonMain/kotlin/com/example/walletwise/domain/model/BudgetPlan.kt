package com.example.walletwise.domain.model

const val BUDGET_RULE_50_30_20 = "50_30_20"
const val BUDGET_RULE_JARS = "JARS"

enum class BudgetRule(val wireValue: String) {
    FIFTY_THIRTY_TWENTY(BUDGET_RULE_50_30_20),
    JARS(BUDGET_RULE_JARS);

    companion object {
        fun fromWireValue(value: String): BudgetRule? = entries.firstOrNull { it.wireValue == value }
        fun fromWireValueOrDefault(value: String): BudgetRule =
            fromWireValue(value) ?: FIFTY_THIRTY_TWENTY
    }
}

data class BudgetPlan(
    val id: String = "",
    val monthYear: String = "",
    val totalBudget: Double = 0.0,
    val ruleType: String = BUDGET_RULE_50_30_20,
    val needsLimit: Double = 0.0,
    val wantsLimit: Double = 0.0,
    val savingsLimit: Double = 0.0,
    val needsSpent: Double = 0.0,
    val wantsSpent: Double = 0.0,
    val savingsSpent: Double = 0.0
)
