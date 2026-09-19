package com.example.walletwise.presentation.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.walletwise.presentation.transaction.TransactionListContent
import com.example.walletwise.presentation.transaction.TransactionListUiEvent

/** Keeps lifecycle, navigation, writes and Coil on Android around the shared list surface. */
@Composable
fun AndroidTransactionList(
    viewModel: TransactionViewModel,
    onNavigateToAdd: () -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "Không có giao dịch nào.",
    compactRows: Boolean = false
) {
    val state by viewModel.transactionListState.collectAsState()
    val event = state.pendingEvent

    LaunchedEffect(event?.id) {
        val pending = event ?: return@LaunchedEffect
        try {
            when (val value = pending.event) {
                is TransactionListUiEvent.RowSelected -> Unit
                is TransactionListUiEvent.EditRequested -> {
                    viewModel.transactions.value.firstOrNull { it.id == value.transactionId }?.let {
                        viewModel.transactionToEdit = it
                        onNavigateToAdd()
                    }
                }
                is TransactionListUiEvent.DeleteRequested ->
                    viewModel.deleteTransaction(value.transactionId)
            }
        } finally {
            viewModel.consumeTransactionListEvent(pending.id)
        }
    }

    TransactionListContent(
        state = state,
        onRetry = viewModel::refreshTransactionList,
        onRowSelected = viewModel::onTransactionRowSelected,
        onEdit = viewModel::onTransactionEditRequested,
        onDelete = viewModel::onTransactionDeleteRequested,
        modifier = modifier,
        emptyMessage = emptyMessage,
        compactRows = compactRows,
        imageContent = { imageUrl, imageModifier ->
            TransactionPhoto(imageUrl, imageModifier.fillMaxSize())
        }
    )
}
