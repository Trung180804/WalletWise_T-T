package com.example.walletwise.reliability

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.walletwise.domain.model.Transaction
import com.example.walletwise.presentation.transaction.TransactionDateTimeProvider
import com.example.walletwise.presentation.transaction.TransactionListContent
import com.example.walletwise.presentation.transaction.TransactionListPresenter
import com.example.walletwise.presentation.transaction.TransactionLocalDateTime
import com.example.walletwise.presentation.transaction.TransactionSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class HomeSearchNormalizationRuntimeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun accentedUnaccentedEmptyAndClearQueriesUseTheRealPresenter() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val session = MutableStateFlow(TransactionSessionState(userId = "runtime-search", transactions = listOf(
            Transaction("meal", "runtime-search", "Chi", "Tiền mặt", 100_000.0, "Ăn uống", "Ăn sáng", 20L),
            Transaction("salary", "runtime-search", "Thu", "Chuyển khoản", 5_000_000.0, "Lương", "Tháng 9", 10L)
        )))
        val dateTime = object : TransactionDateTimeProvider {
            override fun localDateTime(epochMilliseconds: Long) =
                TransactionLocalDateTime(2026, 9, 19, 16, 30)
        }
        val presenter = TransactionListPresenter(scope, session, dateTime)
        try {
            compose.setContent {
                val state by presenter.state.collectAsState()
                MaterialTheme {
                    Column {
                        OutlinedTextField(
                            value = state.filters.searchQuery,
                            onValueChange = presenter::updateSearchQuery,
                            modifier = Modifier.fillMaxWidth().testTag("runtime-search-input")
                        )
                        TransactionListContent(
                            state = state,
                            onRetry = {},
                            onRowSelected = {},
                            onEdit = {},
                            onDelete = {},
                            compactRows = true,
                            emptyMessage = "Không tìm thấy giao dịch phù hợp"
                        )
                    }
                }
            }
            compose.waitUntil(5_000) { presenter.state.value.rows.size == 2 }
            val input = compose.onNodeWithTag("runtime-search-input")
            input.performTextInput("Ăn sáng")
            compose.waitUntil(5_000) { presenter.state.value.rows.map { it.id } == listOf("meal") }
            compose.onNodeWithText("-100.000 ₫").assertIsDisplayed()
            input.performTextClearance()
            compose.waitUntil(5_000) { presenter.state.value.rows.size == 2 }
            input.performTextInput("an uong")
            compose.waitUntil(5_000) { presenter.state.value.rows.map { it.id } == listOf("meal") }
            input.performTextClearance()
            input.performTextInput("không tồn tại")
            compose.waitUntil(5_000) { presenter.state.value.rows.isEmpty() }
            compose.onNodeWithText("Không tìm thấy giao dịch phù hợp").assertIsDisplayed()
        } finally {
            presenter.close()
            scope.cancel()
        }
    }
}
