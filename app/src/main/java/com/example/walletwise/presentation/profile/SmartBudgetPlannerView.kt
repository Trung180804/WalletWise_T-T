package com.example.walletwise.presentation.profile

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walletwise.presentation.budget.SmartBudgetContent
import com.example.walletwise.presentation.budget.SmartBudgetMessage
import com.example.walletwise.presentation.budget.SmartBudgetUiEvent
import com.example.walletwise.presentation.home.TransactionViewModel

/** Android route/lifecycle/platform feedback boundary for the shared budget planner. */
@Composable
fun SmartBudgetPlannerView(
    viewModel: TransactionViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presenter = remember(viewModel, scope) { viewModel.createSmartBudgetPresenter(scope) }
    val state by presenter.state.collectAsStateWithLifecycle()

    DisposableEffect(presenter) {
        onDispose { presenter.close() }
    }

    val event = state.pendingEvent
    LaunchedEffect(event?.id) {
        val envelope = event ?: return@LaunchedEffect
        when (val value = envelope.event) {
            SmartBudgetUiEvent.NavigateBack -> onBack()
            is SmartBudgetUiEvent.Message -> {
                val message = when (value.kind) {
                    SmartBudgetMessage.SAVED -> "Đã lưu kế hoạch ngân sách!"
                    SmartBudgetMessage.INSIGHT_REFRESHED -> "Đã cập nhật phân tích AI!"
                    SmartBudgetMessage.ERROR -> value.detail ?: "Không thể cập nhật kế hoạch ngân sách"
                }
                Toast.makeText(
                    context,
                    message,
                    if (value.kind == SmartBudgetMessage.ERROR) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                ).show()
            }
        }
        presenter.consumeEvent(envelope.id)
    }

    SmartBudgetContent(
        state = state,
        onBack = presenter::onBack,
        onOpenSetup = presenter::onOpenSetup,
        onCancelSetup = presenter::onCancelSetup,
        onAmountChanged = presenter::onAmountChanged,
        onRuleSelected = presenter::onRuleSelected,
        onSave = presenter::onSave,
        onRefreshInsight = presenter::onRefreshInsight,
        onPreviousMonth = presenter::onPreviousMonth,
        onNextMonth = presenter::onNextMonth,
        onResetRatios = presenter::onResetRatios,
        onOpenMapping = presenter::onOpenMapping,
        onCloseMapping = presenter::onCloseMapping,
        onCategoryMapped = presenter::onCategoryMapped,
        onRetryMapping = presenter::onRetryMapping
    )
}
