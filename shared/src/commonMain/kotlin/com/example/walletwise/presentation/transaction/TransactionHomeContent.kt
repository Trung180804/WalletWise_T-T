package com.example.walletwise.presentation.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TransactionHomeContent(state: TransactionHomeState, session: TransactionHomeSession, logoutError: String? = null) {
    if (state.editor.visible) {
        TransactionEditorContent(state.editor, session.editor, session.dateTimeProvider)
        return
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("WalletWise", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = session::logout) { Text("Đăng xuất") }
        }
        Text("Xin chào, ${state.displayLabel}", style = MaterialTheme.typography.titleMedium)
        logoutError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.editor.success?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Summary("Tổng Thu", state.totalIncome, "Thu", Modifier.weight(1f))
            Summary("Tổng Chi", state.totalExpense, "Chi", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Summary("Số dư", state.balance, if (state.balance >= 0) "Thu" else "Chi", Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Giao dịch", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = session::openAdd, enabled = !state.editor.saving) { Text("+ Thêm") }
            TextButton(onClick = session::retry) { Text("Làm mới") }
        }
        TransactionListContent(
            state = state.list, onRetry = session::retry, onRowSelected = session::select,
            onEdit = session::edit, onDelete = {}, modifier = Modifier.weight(1f),
            imageContent = { _, modifier -> NoTransactionImage(modifier) },
            externalSelection = true, selectedTransactionId = state.selectedTransactionId,
            onDetailDismissed = session::dismissDetail, emptyImageLabel = "No image",
            canModify = { it in state.writableTransactionIds }, onDeleteRequested = session::requestDelete,
            readOnlyReason = "Giao dịch cũ chỉ được xem. Chưa hỗ trợ sửa hoặc xóa.", actionsEnabled = !state.editor.saving
        )
    }
    TransactionDeleteConfirmation(state.editor, session.editor)
}

@Composable
private fun Summary(label: String, amount: Double, type: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(formatTransactionAmount(amount, type), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NoTransactionImage(modifier: Modifier) {
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("No image", style = MaterialTheme.typography.labelSmall)
    }
}
