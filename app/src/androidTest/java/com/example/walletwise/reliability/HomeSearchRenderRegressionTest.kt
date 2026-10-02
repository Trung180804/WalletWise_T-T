package com.example.walletwise.reliability

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.walletwise.presentation.transaction.TransactionListContent
import com.example.walletwise.presentation.transaction.TransactionListUiState
import com.example.walletwise.presentation.transaction.TransactionRowViewData
import org.junit.Rule
import org.junit.Test

class HomeSearchRenderRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun compactSearchResultsKeepNotesAmountsDetailsAndEmptyStateVisible() {
        val state = mutableStateOf(TransactionListUiState(userId = "isolated", rows = listOf(
            TransactionRowViewData("meal", "Chi", false, "100000", "-100.000 ₫", "Ăn uống", "Tiền mặt",
                "Ăn sáng", "08:00", "16/09/2026 - 08:00", "", false)
        )))
        compose.setContent {
            MaterialTheme {
                TransactionListContent(state.value, {}, {}, {}, {}, compactRows = true,
                    emptyMessage = "Không tìm thấy giao dịch phù hợp")
            }
        }
        compose.onNodeWithText("Ăn sáng").assertIsDisplayed().performClick()
        compose.onNodeWithText("Chi tiết").assertIsDisplayed()
        compose.onNodeWithText("Sửa").assertIsDisplayed()
        compose.onNodeWithContentDescription("Đóng").performClick()
        compose.onNodeWithText("-100.000 ₫").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(rows = emptyList()) }
        compose.onNodeWithText("Không tìm thấy giao dịch phù hợp").assertIsDisplayed()
    }
}
