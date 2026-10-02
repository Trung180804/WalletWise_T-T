package com.example.walletwise.data.mapper

import com.example.walletwise.domain.model.BUDGET_RULE_50_30_20
import com.example.walletwise.domain.model.BUDGET_RULE_JARS
import com.example.walletwise.domain.model.BudgetPlan
import kotlin.test.Test
import kotlin.test.assertEquals

class BudgetFirestoreCompatibilityTest {
    @Test
    fun pathUsesUidAndZeroPaddedMonthDocument() {
        assertEquals("users/uid-1/budgets", FirestoreSchema.budgetsCollection("uid-1"))
        assertEquals("users/uid-1/budgets/09-2026", FirestoreSchema.budgetDocument("uid-1", "09-2026"))
    }

    @Test
    fun currentDocumentRoundTripsAllTenFieldsAndBothRules() {
        listOf(BUDGET_RULE_50_30_20, BUDGET_RULE_JARS).forEach { rule ->
            val plan = BudgetPlan(
                id = "uid-1",
                monthYear = "09-2026",
                totalBudget = 10_000_000.0,
                ruleType = rule,
                needsLimit = 5_000_000.0,
                wantsLimit = 3_000_000.0,
                savingsLimit = 2_000_000.0,
                needsSpent = 1.0,
                wantsSpent = 2.0,
                savingsSpent = 3.0
            )
            val map = FirestoreWireMapper.budgetPlanToMap(plan)
            assertEquals(
                setOf("id", "monthYear", "totalBudget", "ruleType", "needsLimit", "wantsLimit", "savingsLimit", "needsSpent", "wantsSpent", "savingsSpent"),
                map.keys
            )
            assertEquals(plan, FirestoreWireMapper.budgetPlanFromMap("09-2026", map))
        }
    }

    @Test
    fun firestoreLongAndDoubleAreReadAsDouble() {
        val plan = FirestoreWireMapper.budgetPlanFromMap(
            "09-2026",
            mapOf("totalBudget" to 10_000_000L, "needsLimit" to 5_500_000.5)
        )
        assertEquals(10_000_000.0, plan.totalBudget)
        assertEquals(5_500_000.5, plan.needsLimit)
    }

    @Test
    fun legacyMissingFieldsAndInvalidRuleUseCompatibleDefaults() {
        val missing = FirestoreWireMapper.budgetPlanFromMap("09-2026", emptyMap())
        val invalid = FirestoreWireMapper.budgetPlanFromMap("09-2026", mapOf("ruleType" to "UNKNOWN"))

        assertEquals("09-2026", missing.id)
        assertEquals("", missing.monthYear)
        assertEquals(0.0, missing.totalBudget)
        assertEquals(BUDGET_RULE_50_30_20, missing.ruleType)
        assertEquals(BUDGET_RULE_50_30_20, invalid.ruleType)
    }
}
