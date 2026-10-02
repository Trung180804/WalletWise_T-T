package com.example.walletwise.presentation.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorContent(state: TransactionEditorState, presenter: TransactionEditorPresenter, dateTime: TransactionDateTimeProvider) {
    val categoryState by presenter.categories.state.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    var amount by remember(state.userId, state.editing) { mutableStateOf(TextFieldValue(state.form.amountDigits)) }
    var note by remember(state.userId, state.editing) { mutableStateOf(TextFieldValue(state.form.note)) }
    LaunchedEffect(state.form.amountDigits) {
        // Ignore a delayed Flow echo while native input has already advanced the presenter.
        if (state.form.amountDigits == presenter.snapshot.form.amountDigits && amount.text != state.form.amountDigits) amount = TextFieldValue(state.form.amountDigits, androidx.compose.ui.text.TextRange(state.form.amountDigits.length))
    }
    LaunchedEffect(state.form.note) {
        if (state.form.note == presenter.snapshot.form.note && note.text != state.form.note) note = TextFieldValue(state.form.note, androidx.compose.ui.text.TextRange(state.form.note.length))
    }
    var calendar by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var paymentMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = presenter::requestBack, enabled = !state.saving) { Text("Quay lại") }
                Text(if (state.editing) "Sửa giao dịch" else "Thêm giao dịch", style = MaterialTheme.typography.titleLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.form.type == "Chi", onClick = { presenter.typeChanged("Chi") }, enabled = !state.saving, label = { Text("Chi tiêu") })
                FilterChip(selected = state.form.type == "Thu", onClick = { presenter.typeChanged("Thu") }, enabled = !state.saving, label = { Text("Thu nhập") })
            }
            OutlinedTextField(
                value = amount, onValueChange = { next ->
                    val normalized = TransactionAmountInput.normalize(next.text)
                    presenter.amountChanged(next.text)
                    if (normalized != null) {
                        val start = next.text.take(next.selection.start).count { it in '0'..'9' }.coerceAtMost(normalized.length)
                        val end = next.text.take(next.selection.end).count { it in '0'..'9' }.coerceAtMost(normalized.length)
                        amount = TextFieldValue(normalized, androidx.compose.ui.text.TextRange(start, end))
                    }
                }, label = { Text("Số tiền (đ)") }, singleLine = true, enabled = !state.saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = AmountGroupingTransformation,
                isError = state.errors.containsKey("amount"), supportingText = { state.errors["amount"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth()
            )
            state.fractionalAmountNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Box {
                OutlinedButton(onClick = { keyboard?.hide(); categoryMenu = true }, enabled = !state.saving && !categoryState.isLoading, modifier = Modifier.fillMaxWidth()) { Text("Danh mục: ${state.form.category.ifBlank { "Chọn danh mục" }}") }
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    presenter.availableCategories().forEach { category ->
                        DropdownMenuItem(text = { Text("${category.icon} ${category.name}".trim()) }, onClick = { presenter.categoryChanged(category.name); categoryMenu = false })
                    }
                }
            }
            state.errors["category"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (categoryState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            categoryState.error?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = presenter::retryCategories, enabled = !state.saving) { Text("Thử tải lại danh mục") }
            }
            val date = dateTime.localDateTime(state.form.timestamp)
            OutlinedButton(onClick = { keyboard?.hide(); calendar = true }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("Ngày: ${date?.let { "${it.day}/${it.month}/${it.year}" } ?: "Chọn ngày"}") }
            state.errors["date"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(value = note, onValueChange = { next -> note = next; presenter.noteChanged(next.text) }, label = { Text("Nội dung / ghi chú") }, enabled = !state.saving, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
            Box {
                OutlinedButton(onClick = { keyboard?.hide(); paymentMenu = true }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("Phương thức thanh toán: ${state.form.paymentMethod}") }
                DropdownMenu(expanded = paymentMenu, onDismissRequest = { paymentMenu = false }) {
                    listOf("Tiền mặt", "Ngân hàng", "Ví điện tử").forEach { method -> DropdownMenuItem(text = { Text(method) }, onClick = { presenter.paymentChanged(method); paymentMenu = false }) }
                }
            }
            state.errors["payment"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Giao dịch không chọn ảnh mới. Ảnh hiện có được giữ khi sửa.", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = { keyboard?.hide(); presenter.submit() }, enabled = !state.saving, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 48.dp)) {
            if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Lưu giao dịch")
        }
    }
    if (calendar) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = state.form.timestamp.takeIf { it > 0 })
        DatePickerDialog(onDismissRequest = { calendar = false }, confirmButton = {
            TextButton(onClick = { dateState.selectedDateMillis?.let(presenter::dateChanged); calendar = false }, enabled = dateState.selectedDateMillis != null) { Text("Chọn") }
        }, dismissButton = { TextButton(onClick = { calendar = false }) { Text("Hủy") } }) { DatePicker(state = dateState) }
    }
    if (state.discardConfirmation) AlertDialog(onDismissRequest = presenter::cancelDiscard,
        title = { Text("Bỏ thay đổi?") }, text = { Text("Các thay đổi chưa lưu sẽ bị bỏ.") },
        confirmButton = { TextButton(onClick = presenter::confirmDiscard) { Text("Bỏ thay đổi") } },
        dismissButton = { TextButton(onClick = presenter::cancelDiscard) { Text("Tiếp tục sửa") } })
}

@Composable
fun TransactionDeleteConfirmation(state: TransactionEditorState, presenter: TransactionEditorPresenter) {
    if (state.pendingDelete == null) return
    AlertDialog(onDismissRequest = presenter::cancelDelete, title = { Text("Xác nhận xóa") },
        text = { Column { Text("Bạn có chắc chắn muốn xóa giao dịch này không?"); state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { Button(onClick = presenter::confirmDelete, enabled = !state.saving) { if (state.saving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Xóa") } },
        dismissButton = { TextButton(onClick = presenter::cancelDelete, enabled = !state.saving) { Text("Hủy") } })
}

private object AmountGroupingTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(AnnotatedString(TransactionAmountInput.format(text.text)), object : OffsetMapping {
        override fun originalToTransformed(offset: Int) = TransactionAmountInput.originalToFormatted(offset, text.length)
        override fun transformedToOriginal(offset: Int) = TransactionAmountInput.formattedToOriginal(offset, text.text)
    })
}
