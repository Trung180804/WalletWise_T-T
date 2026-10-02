package com.example.walletwise.reliability

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.walletwise.domain.model.BudgetPlan
import com.example.walletwise.domain.model.BudgetRule
import com.example.walletwise.domain.service.BudgetCalculator
import com.example.walletwise.domain.service.BudgetDate
import com.example.walletwise.presentation.budget.SmartBudgetContent
import com.example.walletwise.presentation.budget.SmartBudgetUiState
import org.junit.Rule
import org.junit.Test

class FinancialRenderRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun bothMethodsRenderExactAmountsAndAccessibleMonthControls() {
        val state = mutableStateOf(SmartBudgetUiState(currentDate = BudgetDate(2026, 9, 16)))
        compose.setContent {
            MaterialTheme {
                SmartBudgetContent(state.value, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Quy tắc 50/30/20").assertExists()
        compose.onNodeWithText("Quy tắc 6 chiếc lọ").assertExists()
        compose.onNodeWithContentDescription("Tháng trước").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tháng sau").assertIsDisplayed()
        fun result(rule: BudgetRule) {
            compose.runOnIdle {
                state.value = state.value.copy(
                    plan = BudgetPlan(totalBudget = 10_000_000.0, ruleType = rule.wireValue),
                    totalBudget = 10_000_000.0,
                    allocationPlan = BudgetCalculator.allocateExactly(10_000_000L, rule)
                    ,liveAllocation = com.example.walletwise.domain.service.LiveFinancialAllocation(
                        BudgetCalculator.allocateExactly(10_000_000L, rule).allocations.map {
                            com.example.walletwise.domain.service.FinancialBucketUsage(it, 0L)
                        }
                    )
                )
            }
        }
        result(BudgetRule.FIFTY_THIRTY_TWENTY)
        compose.onNodeWithText("Được cấp: 5.000.000 ₫").assertExists()
        compose.onNodeWithText("Được cấp: 3.000.000 ₫").assertExists()
        compose.onNodeWithText("Được cấp: 2.000.000 ₫").assertExists()
        result(BudgetRule.JARS)
        compose.onNodeWithText("Được cấp: 5.500.000 ₫").assertExists()
        compose.onNodeWithText("Tự do tài chính • 10%").assertExists()
        compose.onNodeWithContentDescription("Nhu cầu thiết yếu: 55%").assertExists()
    }
}
