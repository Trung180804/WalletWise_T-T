package com.example.walletwise.presentation.transaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

typealias TransactionImageContent = @Composable (imageUrl: String, modifier: Modifier) -> Unit

/** Reusable transaction grid/card surface. Platform image loading is supplied through [imageContent]. */
@Composable
fun TransactionListContent(
    state: TransactionListUiState,
    onRetry: () -> Unit,
    onRowSelected: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "Không có giao dịch nào.",
    compactRows: Boolean = false,
    imageContent: TransactionImageContent? = null
) {
    var selectedRow by remember(state.userId) { mutableStateOf<TransactionRowViewData?>(null) }
    var pendingDelete by remember(state.userId) { mutableStateOf<TransactionRowViewData?>(null) }

    when {
        state.isLoading && state.rows.isEmpty() -> TransactionLoading(modifier)
        state.repositoryError != null && state.rows.isEmpty() -> TransactionError(
            message = state.repositoryError.message,
            onRetry = onRetry,
            modifier = modifier
        )
        state.rows.isEmpty() -> TransactionEmpty(emptyMessage, modifier)
        else -> Column(modifier = modifier) {
            state.repositoryError?.let { error ->
                Text(
                    text = error.message.ifBlank { "Không thể tải giao dịch." },
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (compactRows) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listItems(state.rows, key = TransactionRowViewData::id) { row ->
                        CompactTransactionRow(row) {
                            selectedRow = row
                            onRowSelected(row.id)
                        }
                    }
                }
            } else LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.rows, key = TransactionRowViewData::id) { row ->
                    TransactionCard(
                        row = row,
                        imageContent = imageContent,
                        onClick = {
                            selectedRow = row
                            onRowSelected(row.id)
                        }
                    )
                }
            }
        }
    }

    selectedRow?.let { row ->
        TransactionDetailDialog(
            row = row,
            imageContent = imageContent,
            onDismiss = { selectedRow = null },
            onEdit = {
                selectedRow = null
                onEdit(row.id)
            },
            onDelete = {
                selectedRow = null
                pendingDelete = row
            }
        )
    }

    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Xác nhận xóa", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc chắn muốn xóa giao dịch này không?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDelete = null
                        onDelete(row.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) {
                    Text("Xóa", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun CompactTransactionRow(row: TransactionRowViewData, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.category.ifBlank { row.type }, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.amountText, fontWeight = FontWeight.Bold,
                    color = if (row.isIncome) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
            }
            if (row.note.isNotBlank()) Text(row.note, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${row.fullDateText} • ${row.paymentMethod}", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TransactionLoading(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun TransactionEmpty(message: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TransactionError(message: String, onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message.ifBlank { "Không thể tải giao dịch." },
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text("Thử lại") }
    }
}

@Composable
private fun TransactionCard(
    row: TransactionRowViewData,
    imageContent: TransactionImageContent?,
    onClick: () -> Unit
) {
    val amountColor = if (row.isIncome) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            TransactionImage(row, imageContent, Modifier.fillMaxWidth().height(80.dp))
            Spacer(Modifier.height(12.dp))
            Text(row.amountText, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = amountColor)
            Spacer(Modifier.height(4.dp))
            Text(
                row.category.ifBlank { "Chưa phân loại" },
                fontSize = 12.sp,
                color = subTextColor,
                maxLines = 1
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(row.timeText, fontSize = 11.sp, color = subTextColor)
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        row.paymentMethod.ifBlank { "Chưa xác định" },
                        fontSize = 10.sp,
                        color = subTextColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionImage(
    row: TransactionRowViewData,
    imageContent: TransactionImageContent?,
    modifier: Modifier
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (row.hasImage && imageContent != null) {
            imageContent(row.imageUrl, Modifier.fillMaxSize())
        } else {
            Text(
                if (row.hasImage) "Có ảnh" else "Không có ảnh",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TransactionDetailDialog(
    row: TransactionRowViewData,
    imageContent: TransactionImageContent?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val amountColor = if (row.isIncome) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chi tiết", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (row.hasImage) {
                    TransactionImage(row, imageContent, Modifier.fillMaxWidth().height(160.dp))
                    Spacer(Modifier.height(20.dp))
                }
                Text(row.amountText, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = amountColor)
                Spacer(Modifier.height(24.dp))
                Column(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    TransactionInfoRow("Loại giao dịch", row.type, amountColor)
                    TransactionInfoRow("Danh mục", row.category)
                    TransactionInfoRow("Nguồn tiền", row.paymentMethod)
                    TransactionInfoRow("Thời gian", row.fullDateText)
                    if (row.note.isNotBlank()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Ghi chú:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(row.note, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        border = BorderStroke(1.dp, Color.Red),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Xóa", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Sửa", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionInfoRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(
            value,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 12.dp)
        )
    }
}
