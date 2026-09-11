package com.example.walletwise.presentation.recurring

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_DAILY
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_MONTHLY
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_WEEKLY
import com.example.walletwise.domain.model.RECURRING_FREQUENCY_YEARLY
import com.example.walletwise.domain.model.RECURRING_TIMES_COUNT_WIRE_VALUES
import com.example.walletwise.domain.model.RECURRING_TIMES_UNLIMITED
import com.example.walletwise.domain.model.TRANSACTION_TYPE_EXPENSE
import com.example.walletwise.domain.model.TRANSACTION_TYPE_INCOME
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.domain.service.ReminderCalendar
import com.example.walletwise.domain.service.ReminderLocalDate
import com.example.walletwise.domain.validation.RecurringValidationError

@Composable
fun RecurringContent(
    state: RecurringUiState,
    onBack: () -> Unit,
    onOpenAdd: () -> Unit,
    onOpenDetail: (RecurringTransaction) -> Unit,
    onDismissDetail: () -> Unit,
    onOpenEdit: (RecurringTransaction) -> Unit,
    onRequestDelete: (RecurringTransaction) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onToggle: (RecurringTransaction, Boolean) -> Unit,
    onCancelForm: () -> Unit,
    onSubmit: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onTypeSelected: (String) -> Unit,
    onOpenPicker: (RecurringPicker) -> Unit,
    onFrequencySelected: (String) -> Unit,
    onTimesCountSelected: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onPaymentMethodSelected: (String) -> Unit,
    onPreviousDatePickerMonth: () -> Unit,
    onNextDatePickerMonth: () -> Unit,
    onDatePickerDaySelected: (Int) -> Unit,
    onConfirmDatePicker: () -> Unit,
    onTimePickerHourChanged: (Int) -> Unit,
    onTimePickerMinuteChanged: (Int) -> Unit,
    onConfirmTimePicker: () -> Unit,
    onDismissPicker: () -> Unit
) {
    if (state.screenMode == RecurringScreenMode.FORM) {
        RecurringForm(
            state,
            onCancelForm,
            onSubmit,
            onTitleChanged,
            onAmountChanged,
            onNoteChanged,
            onTypeSelected,
            onOpenPicker
        )
    } else {
        RecurringList(state, onBack, onOpenAdd, onOpenDetail, onToggle)
    }

    state.selectedDetail?.let { recurring ->
        RecurringDetail(
            recurring = recurring,
            onDismiss = onDismissDetail,
            onEdit = { onOpenEdit(recurring) },
            onDelete = { onRequestDelete(recurring) }
        )
    }
    state.deletingRecurring?.let { recurring ->
        DeleteConfirmation(
            recurring = recurring,
            deleting = state.deletingRecurringId == recurring.id,
            onDismiss = onCancelDelete,
            onConfirm = onConfirmDelete
        )
    }
    when (state.activePicker) {
        RecurringPicker.FREQUENCY -> OptionDialog(
            title = "Tần suất thực hiện",
            options = listOf(
                RECURRING_FREQUENCY_DAILY,
                RECURRING_FREQUENCY_WEEKLY,
                RECURRING_FREQUENCY_MONTHLY,
                RECURRING_FREQUENCY_YEARLY
            ),
            selected = state.frequency,
            label = { it },
            onDismiss = onDismissPicker,
            onSelected = onFrequencySelected
        )
        RecurringPicker.TIMES_COUNT -> OptionDialog(
            title = "Số lần thực hiện",
            options = RECURRING_TIMES_COUNT_WIRE_VALUES,
            selected = state.timesCount,
            label =(::timesLabel),
            onDismiss = onDismissPicker,
            onSelected = onTimesCountSelected
        )
        RecurringPicker.CATEGORY -> OptionDialog(
            title = "Chọn danh mục",
            options = state.currentCategories.map { it.name },
            selected = state.category,
            label = { it },
            onDismiss = onDismissPicker,
            onSelected = onCategorySelected
        )
        RecurringPicker.PAYMENT_METHOD -> OptionDialog(
            title = "Chọn nguồn tiền",
            options = listOf("Tiền mặt", "Chuyển khoản", "Thẻ tín dụng"),
            selected = state.paymentMethod,
            label = { it },
            onDismiss = onDismissPicker,
            onSelected = onPaymentMethodSelected
        )
        RecurringPicker.DATE -> RecurringDatePicker(
            state,
            onDismissPicker,
            onPreviousDatePickerMonth,
            onNextDatePickerMonth,
            onDatePickerDaySelected,
            onConfirmDatePicker
        )
        RecurringPicker.TIME -> RecurringTimePicker(
            state.timePickerHour,
            state.timePickerMinute,
            onDismissPicker,
            onTimePickerHourChanged,
            onTimePickerMinuteChanged,
            onConfirmTimePicker
        )
        RecurringPicker.NONE -> Unit
    }
}

@Composable
private fun RecurringList(
    state: RecurringUiState,
    onBack: () -> Unit,
    onOpenAdd: () -> Unit,
    onOpenDetail: (RecurringTransaction) -> Unit,
    onToggle: (RecurringTransaction, Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        RecurringHeader("Giao dịch định kỳ", onBack)
        when {
            state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.repositoryError != null && state.recurring.isEmpty() -> Box(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(state.repositoryError.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            state.isEmpty -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Chưa có giao dịch định kỳ nào", color = Color.Gray)
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.recurring, key = RecurringTransaction::id) { recurring ->
                    RecurringCard(
                        recurring,
                        recurring.id in state.togglingRecurringIds,
                        { onOpenDetail(recurring) },
                        { onToggle(recurring, it) }
                    )
                }
            }
        }
        state.schedulingError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Button(
            onClick = onOpenAdd,
            enabled = state.userId != null,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Thêm giao dịch định kỳ", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RecurringCard(
    recurring: RecurringTransaction,
    busy: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val income = recurring.type == TRANSACTION_TYPE_INCOME
    val color = if (income) Color(0xFF4CAF50) else Color(0xFFFA3B70)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, null, tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(recurring.title, fontWeight = FontWeight.Bold)
                Text("${timesLabel(recurring.timesCount)} / ${recurring.frequency} • ${recurring.category}", color = Color.Gray, fontSize = 12.sp)
                Text(
                    "${if (income) "+" else "-"}${formatAmount(recurring.amount)} đ (${recurring.time})",
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Switch(checked = recurring.isEnabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun RecurringDetail(
    recurring: RecurringTransaction,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val income = recurring.type == TRANSACTION_TYPE_INCOME
    val amountColor = if (income) Color(0xFF4CAF50) else Color(0xFFFA3B70)
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Chi tiết định kỳ", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Đóng")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "${if (income) "+" else "-"}${formatAmount(recurring.amount)} đ",
                    color = amountColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(16.dp)
                ) {
                    DetailRow("Tên giao dịch", recurring.title)
                    DetailRow("Loại giao dịch", recurring.type)
                    DetailRow("Tần suất", recurring.frequency)
                    DetailRow("Số lần", timesLabel(recurring.timesCount))
                    DetailRow("Danh mục", recurring.category)
                    DetailRow("Nguồn tiền", recurring.paymentMethod)
                    DetailRow("Ngày bắt đầu", recurring.startDate)
                    DetailRow("Thời gian", recurring.time)
                    if (recurring.note.isNotBlank()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Ghi chú:", color = Color.Gray, fontSize = 13.sp)
                        Text(recurring.note, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        border = BorderStroke(1.dp, Color.Red)
                    ) { Text("Xóa") }
                    Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Sửa") }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DeleteConfirmation(
    recurring: RecurringTransaction,
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("Xác nhận xóa", fontWeight = FontWeight.Bold) },
        text = { Text("Bạn có chắc chắn muốn xóa giao dịch định kỳ '${recurring.title}' không?") },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !deleting, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA3B70))) {
                if (deleting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Xóa", color = Color.White)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !deleting) { Text("Hủy") } }
    )
}

@Composable
private fun RecurringForm(
    state: RecurringUiState,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onTypeSelected: (String) -> Unit,
    onOpenPicker: (RecurringPicker) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Hủy", modifier = Modifier.clickable(enabled = !state.isSaving, onClick = onCancel))
            Text(if (state.isEditMode) "Sửa định kỳ" else "Thêm định kỳ", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (state.isSaving) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Check, "Lưu", modifier = Modifier.clickable(onClick = onSubmit))
        }
        HorizontalDivider()
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))
            FormInput("Tên giao dịch", state.title, onTitleChanged, "Ví dụ: Tiền nhà, Netflix")
            Text("Loại giao dịch", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(TRANSACTION_TYPE_EXPENSE to "Chi tiêu", TRANSACTION_TYPE_INCOME to "Thu nhập").forEach { (type, label) ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { onTypeSelected(type) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (type == TRANSACTION_TYPE_EXPENSE) Color(0xFFFA3B70) else Color(0xFF4CAF50),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FormInput("Số tiền (đ)", state.amount, onAmountChanged, "0")
            FormChoice("Tần suất", state.frequency) { onOpenPicker(RecurringPicker.FREQUENCY) }
            FormChoice("Số lần", timesLabel(state.timesCount)) { onOpenPicker(RecurringPicker.TIMES_COUNT) }
            FormChoice("Ngày bắt đầu", state.startDate) { onOpenPicker(RecurringPicker.DATE) }
            FormChoice("Thời gian", state.time) { onOpenPicker(RecurringPicker.TIME) }
            FormChoice("Danh mục", state.category.ifBlank { "Chọn danh mục" }) { onOpenPicker(RecurringPicker.CATEGORY) }
            FormChoice("Nguồn tiền", state.paymentMethod.ifBlank { "Chọn nguồn tiền" }) { onOpenPicker(RecurringPicker.PAYMENT_METHOD) }
            FormInput("Ghi chú", state.note, onNoteChanged, "Nhập ghi chú...")
            state.validationError?.let {
                Text(validationMessage(it), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Text(
                "Dữ liệu sẽ tự động được thêm vào danh sách giao dịch theo lịch cài đặt.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun FormInput(label: String, value: String, onChanged: (String) -> Unit, placeholder: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(placeholder) },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun FormChoice(label: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(value, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun RecurringHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
    HorizontalDivider()
}

@Composable
private fun OptionDialog(
    title: String,
    options: List<String>,
    selected: String,
    label: (String) -> String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp).heightIn(max = 420.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                LazyColumn {
                    items(options) { option ->
                        Text(
                            label(option),
                            color = if (selected == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected == option) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().clickable { onSelected(option) }.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringDatePicker(
    state: RecurringUiState,
    onDismiss: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    val month = state.datePickerMonth
    val days = ReminderCalendar.daysInMonth(month.year, month.month)
    val selectedDay = state.datePickerSelectedDay.coerceIn(1, days)
    val firstDay = ReminderCalendar.sundayBasedDayOfWeek(ReminderLocalDate(month.year, month.month, 1)) ?: 0
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text(
                    "$selectedDay thg ${month.month}, ${month.year}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("tháng ${month.month} năm ${month.year}", color = Color.White)
                    Row {
                        IconButton(onClick = onPreviousMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Tháng trước", tint = Color.White) }
                        IconButton(onClick = onNextMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Tháng sau", tint = Color.White) }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("CN", "T2", "T3", "T4", "T5", "T6", "T7").forEach {
                        Text(it, color = Color.Gray, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                }
                val rowCount = (firstDay + days + 6) / 7
                repeat(rowCount) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val day = row * 7 + column - firstDay + 1
                            if (day in 1..days) {
                                val selected = day == selectedDay
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                                        .background(if (selected) Color(0xFFFFD54F) else Color.Transparent)
                                        .clickable { onDaySelected(day) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(day.toString(), color = if (selected) Color.Black else Color.White)
                                }
                            } else Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
                PickerButtons(onDismiss, onConfirm)
            }
        }
    }
}

@Composable
private fun RecurringTimePicker(
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onHourChanged: (Int) -> Unit,
    onMinuteChanged: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
            Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${twoDigits(hour)}:${twoDigits(minute)}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeColumn(hour, 0, 23, onHourChanged)
                    Text(":", color = Color.White, fontSize = 32.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    TimeColumn(minute, 0, 59, onMinuteChanged)
                }
                Spacer(Modifier.height(20.dp))
                PickerButtons(onDismiss, onConfirm)
            }
        }
    }
}

@Composable
private fun TimeColumn(value: Int, min: Int, max: Int, onChanged: (Int) -> Unit) {
    val previous = if (value == min) max else value - 1
    val next = if (value == max) min else value + 1
    Column(
        Modifier.width(100.dp).height(130.dp).border(1.dp, Color.White, RoundedCornerShape(12.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(twoDigits(previous), color = Color.Gray, modifier = Modifier.clickable { onChanged(previous) })
        Text(twoDigits(value), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(twoDigits(next), color = Color.Gray, modifier = Modifier.clickable { onChanged(next) })
    }
}

@Composable
private fun PickerButtons(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onDismiss) { Text("Hủy", color = Color(0xFFFFD54F)) }
        TextButton(onClick = onConfirm) { Text("Xác nhận", color = Color(0xFFFFD54F)) }
    }
}

private fun timesLabel(value: String): String =
    if (value == RECURRING_TIMES_UNLIMITED) value else "$value lần"

private fun twoDigits(value: Int): String = if (value in 0..9) "0$value" else value.toString()

private fun formatAmount(value: Double): String {
    if (!value.isFinite()) return value.toString()
    if (value % 1.0 != 0.0) return value.toString()
    val digits = value.toLong().toString()
    return digits.reversed().chunked(3).joinToString(".").reversed()
}

private fun validationMessage(error: RecurringValidationError): String = when (error) {
    RecurringValidationError.USER_ID_REQUIRED -> "Bạn cần đăng nhập."
    RecurringValidationError.ID_REQUIRED -> "Mã giao dịch định kỳ không hợp lệ."
    RecurringValidationError.TITLE_REQUIRED -> "Tên giao dịch là bắt buộc."
    RecurringValidationError.AMOUNT_INVALID -> "Số tiền phải hữu hạn và lớn hơn 0."
    RecurringValidationError.TYPE_INVALID -> "Loại giao dịch phải là Thu hoặc Chi."
    RecurringValidationError.CATEGORY_REQUIRED -> "Vui lòng chọn danh mục."
    RecurringValidationError.PAYMENT_METHOD_REQUIRED -> "Vui lòng chọn nguồn tiền."
    RecurringValidationError.FREQUENCY_INVALID -> "Tần suất không hợp lệ."
    RecurringValidationError.TIMES_COUNT_INVALID -> "Số lần thực hiện không hợp lệ."
    RecurringValidationError.DATE_INVALID -> "Ngày bắt đầu không hợp lệ."
    RecurringValidationError.TIME_INVALID -> "Thời gian không hợp lệ."
}
