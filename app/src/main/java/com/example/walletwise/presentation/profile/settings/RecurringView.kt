package com.example.walletwise.presentation.profile.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walletwise.domain.model.RecurringTransaction
import com.example.walletwise.presentation.home.TransactionViewModel
import com.example.walletwise.presentation.profile.FormInputBlock
import com.example.walletwise.presentation.profile.FormStaticBlock
import com.example.walletwise.presentation.profile.ThemedDivider
import com.example.walletwise.presentation.profile.TopHeader
import com.example.walletwise.utils.RecurringSchedule
import java.text.DecimalFormat
import java.time.LocalDate

@Composable
fun RecurringView(viewModel: TransactionViewModel, onAdd: () -> Unit, onBack: () -> Unit) {
    val recurringList by viewModel.recurringTransactions.collectAsState()
    var editingRecurring by remember { mutableStateOf<RecurringTransaction?>(null) }
    var selectedDetailRecurring by remember { mutableStateOf<RecurringTransaction?>(null) }
    var deletingRecurring by remember { mutableStateOf<RecurringTransaction?>(null) }

    val context = LocalContext.current

    if (editingRecurring != null) {
        AddRecurringView(
            viewModel = viewModel,
            recurringToEdit = editingRecurring,
            onBack = { editingRecurring = null }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Giao dịch định kỳ", onBack)

        if (recurringList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Chưa có giao dịch định kỳ nào", color = Color.Gray, fontSize = 15.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = recurringList, key = { it.id }) { item ->
                    RecurringItemCard(
                        item = item,
                        onClick = { selectedDetailRecurring = item },
                        onToggle = { enabled ->
                            viewModel.setRecurringTransactionEnabled(item, enabled, context) { result ->
                                result.onFailure {
                                    Toast.makeText(context, "Không thể đổi trạng thái giao dịch định kỳ.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Thêm giao dịch định kỳ", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }

    // DIALOG CHI TIẾT GIAO DỊCH ĐỊNH KỲ (DETAIL DIALOG)
    if (selectedDetailRecurring != null) {
        val rec = selectedDetailRecurring!!
        val df = remember { DecimalFormat("#,##0") }
        val isIncome = rec.type == "Thu"
        val sign = if (isIncome) "+" else "-"
        val amountColor = if (isIncome) Color(0xFF4CAF50) else Color(0xFFFA3B70)
        val timesText = if (rec.timesCount == "Khác") "Khác" else "${rec.timesCount} lần"

        Dialog(onDismissRequest = { selectedDetailRecurring = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chi tiết định kỳ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        IconButton(onClick = { selectedDetailRecurring = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("$sign${df.format(rec.amount)} đ", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = amountColor)

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        InfoDetailRow("Tên giao dịch", rec.title)
                        InfoDetailRow("Loại giao dịch", rec.type)
                        InfoDetailRow("Tần suất", rec.frequency)
                        InfoDetailRow("Số lần", timesText)
                        InfoDetailRow("Danh mục", rec.category)
                        InfoDetailRow("Nguồn tiền", rec.paymentMethod)
                        InfoDetailRow("Ngày bắt đầu", rec.startDate)
                        InfoDetailRow("Thời gian", rec.time)
                        if (rec.note.isNotBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Text("Ghi chú:", color = Color.Gray, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(rec.note, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                deletingRecurring = rec
                                selectedDetailRecurring = null
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                            border = BorderStroke(1.dp, Color.Red),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Xóa", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                editingRecurring = rec
                                selectedDetailRecurring = null
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Sửa", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // DIALOG XÁC NHẬN XÓA GIAO DỊCH ĐỊNH KỲ
    if (deletingRecurring != null) {
        AlertDialog(
            onDismissRequest = { deletingRecurring = null },
            title = { Text("Xác nhận xóa", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Bạn có chắc chắn muốn xóa giao dịch định kỳ '${deletingRecurring?.title}' không?", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                Button(
                    onClick = {
                        deletingRecurring?.let { recurring ->
                            viewModel.deleteRecurringTransaction(recurring.id, context) { result ->
                                result.onSuccess {
                                    Toast.makeText(
                                        context,
                                        "Đã xóa giao dịch định kỳ",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    deletingRecurring = null
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "Không thể xóa giao dịch định kỳ. Vui lòng thử lại.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA3B70))
                ) {
                    Text("Xóa", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRecurring = null }) {
                    Text("Hủy", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun RecurringItemCard(
    item: RecurringTransaction,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val df = remember { DecimalFormat("#,##0") }
    val isIncome = item.type == "Thu"
    val iconColor = if (isIncome) Color(0xFF4CAF50) else Color(0xFFFA3B70)
    val sign = if (isIncome) "+" else "-"
    val timesText = if (item.timesCount == "Khác") "Khác" else "${item.timesCount} lần"

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("$timesText / ${item.frequency} • ${item.category}", color = Color.Gray, fontSize = 12.sp)
                Text("$sign${df.format(item.amount)} đ (${item.time})", color = iconColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Switch(
                checked = item.isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
fun AddRecurringView(
    viewModel: TransactionViewModel,
    recurringToEdit: RecurringTransaction? = null,
    onBack: () -> Unit
) {
    val isEditMode = recurringToEdit != null
    var title by remember { mutableStateOf(recurringToEdit?.title ?: "") }
    var amount by remember { mutableStateOf(if (isEditMode) recurringToEdit!!.amount.toLong().toString() else "") }
    var type by remember { mutableStateOf(recurringToEdit?.type ?: "Chi") }

    val categories by viewModel.categories.collectAsState()
    val currentCategories = categories.filter { it.type == type }
    var category by remember { mutableStateOf(recurringToEdit?.category ?: currentCategories.firstOrNull()?.name ?: "Hóa đơn") }

    var paymentMethod by remember { mutableStateOf(recurringToEdit?.paymentMethod ?: "Tiền mặt") }
    var frequency by remember { mutableStateOf(recurringToEdit?.frequency ?: "Hàng tháng") }
    var timesCount by remember { mutableStateOf(recurringToEdit?.timesCount ?: "1") }
    var startDate by remember {
        mutableStateOf(recurringToEdit?.startDate ?: RecurringSchedule.formatStartDate(LocalDate.now()))
    }
    var time by remember { mutableStateOf(recurringToEdit?.time ?: "20:15") }
    var note by remember { mutableStateOf(recurringToEdit?.note ?: "") }

    var showTimePicker by remember { mutableStateOf(false) }
    var showFrequencyDialog by remember { mutableStateOf(false) }
    var showTimesDialog by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val context = LocalContext.current

    BackHandler { if (!isSaving) onBack() }

    LaunchedEffect(type, currentCategories) {
        if (currentCategories.isNotEmpty() && currentCategories.none { it.name == category }) {
            category = currentCategories[0].name
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Hủy",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                modifier = Modifier.clickable(enabled = !isSaving) { onBack() }
            )
            Text(if (isEditMode) "Sửa định kỳ" else "Thêm định kỳ", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                val canSave = title.isNotBlank() && amount.toDoubleOrNull() != null
                Icon(
                    Icons.Default.Check, "Lưu",
                    tint = if (canSave) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.clickable(enabled = canSave) {
                        if (isSaving) return@clickable
                        val amountVal = amount.toDoubleOrNull() ?: return@clickable
                        val recurring = recurringToEdit?.copy(
                            title = title,
                            amount = amountVal,
                            type = type,
                            category = category,
                            paymentMethod = paymentMethod,
                            frequency = frequency,
                            timesCount = timesCount,
                            startDate = startDate,
                            time = time,
                            note = note
                        ) ?: RecurringTransaction(
                            title = title,
                            amount = amountVal,
                            type = type,
                            category = category,
                            paymentMethod = paymentMethod,
                            frequency = frequency,
                            timesCount = timesCount,
                            startDate = startDate,
                            time = time,
                            note = note
                        )
                        isSaving = true
                        val onComplete: (Result<Unit>) -> Unit = { result ->
                            isSaving = false
                            result.onSuccess {
                                Toast.makeText(
                                    context,
                                    if (isEditMode) "Đã cập nhật giao dịch định kỳ!" else "Đã lưu giao dịch định kỳ!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onBack()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    if (isEditMode) "Không thể cập nhật giao dịch định kỳ. Vui lòng thử lại."
                                    else "Không thể lưu giao dịch định kỳ. Vui lòng thử lại.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        if (isEditMode) {
                            viewModel.updateRecurringTransaction(recurring, context, onComplete)
                        } else {
                            viewModel.addRecurringTransaction(recurring, context, onComplete)
                        }
                    }
                )
            }
        }
        ThemedDivider()

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            FormInputBlock("Tên giao dịch", title, { title = it }, "Vd: Tiền nhà, Netflix")

            Text("Loại giao dịch", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Chi" to "Chi tiêu", "Thu" to "Thu nhập").forEach { (t, label) ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (t == "Chi") Color(0xFFFA3B70) else Color(0xFF4CAF50),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            FormInputBlock("Số tiền (đ)", amount, { if (it.all { c -> c.isDigit() }) amount = it }, "0 đ")

            FormStaticBlock("Tần suất", frequency, isDropdown = true, onClick = { showFrequencyDialog = true })

            val displayTimes = if (timesCount == "Khác") "Khác" else "$timesCount lần"
            FormStaticBlock("Số lần", displayTimes, isDropdown = true, onClick = { showTimesDialog = true })

            FormStaticBlock("Ngày bắt đầu", startDate, isDropdown = true, onClick = { showDateDialog = true })

            FormStaticBlock("Thời gian", time, isDropdown = true, onClick = { showTimePicker = true })

            FormStaticBlock("Danh mục", category, isDropdown = true, onClick = { showCategoryDialog = true })

            FormInputBlock("Ghi chú", note, { note = it }, "Nhập ghi chú...")

            Text(
                "Dữ liệu sẽ tự động thêm vào danh sách giao dịch theo lịch cài đặt.",
                color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(100.dp))
        }
    }

    if (showTimePicker) {
        CustomTimePickerDialog(
            initialTime = time,
            onDismiss = { showTimePicker = false },
            onConfirm = { selectedTime ->
                time = selectedTime
                showTimePicker = false
            }
        )
    }

    if (showDateDialog) {
        CustomDatePickerDialog(
            initialDate = startDate,
            onDismiss = { showDateDialog = false },
            onConfirm = { dateStr ->
                startDate = dateStr
                showDateDialog = false
            }
        )
    }

    if (showFrequencyDialog) {
        val freqOptions = listOf("Hàng ngày", "Hàng tuần", "Hàng tháng", "Hàng năm")
        Dialog(onDismissRequest = { showFrequencyDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tần suất thực hiện", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    freqOptions.forEach { option ->
                        Text(
                            text = option,
                            color = if (frequency == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    frequency = option
                                    showFrequencyDialog = false
                                }
                                .padding(vertical = 12.dp),
                            fontSize = 15.sp,
                            fontWeight = if (frequency == option) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }

    if (showTimesDialog) {
        val timesOptions = listOf("1", "2", "3", "4", "5", "6", "7", "Khác")
        Dialog(onDismissRequest = { showTimesDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Số lần thực hiện", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    timesOptions.forEach { option ->
                        val label = if (option == "Khác") "Khác" else "$option lần"
                        Text(
                            text = label,
                            color = if (timesCount == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    timesCount = option
                                    showTimesDialog = false
                                }
                                .padding(vertical = 12.dp),
                            fontSize = 15.sp,
                            fontWeight = if (timesCount == option) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }

    if (showCategoryDialog) {
        Dialog(onDismissRequest = { showCategoryDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp).heightIn(max = 400.dp)) {
                    Text("Chọn danh mục", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(currentCategories) { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (category == cat.name) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable {
                                        category = cat.name
                                        showCategoryDialog = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(cat.icon, fontSize = 18.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = cat.name,
                                    color = if (category == cat.name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (category == cat.name) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
