package com.example.walletwise.presentation.profile.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.presentation.home.TransactionViewModel
import com.example.walletwise.utils.RecurringSchedule
import com.example.walletwise.presentation.profile.FormInputBlock
import com.example.walletwise.presentation.profile.FormStaticBlock
import com.example.walletwise.presentation.profile.ThemedDivider
import com.example.walletwise.presentation.profile.TopHeader

@Composable
fun RemindersView(viewModel: TransactionViewModel, onBack: () -> Unit) {
    val reminders by viewModel.reminders.collectAsState()
    var showAddScreen by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var selectedDetailReminder by remember { mutableStateOf<Reminder?>(null) }
    var deletingReminder by remember { mutableStateOf<Reminder?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val context = LocalContext.current

    if (showAddScreen || editingReminder != null) {
        AddReminderScreen(
            reminderToEdit = editingReminder,
            isSaving = isSaving,
            onBack = {
                if (isSaving) return@AddReminderScreen
                showAddScreen = false
                editingReminder = null
            },
            onSave = { newReminder ->
                if (isSaving) return@AddReminderScreen
                isSaving = true
                if (editingReminder != null) {
                    viewModel.updateReminder(newReminder, context) { result ->
                        isSaving = false
                        result.onSuccess {
                            Toast.makeText(context, "Đã cập nhật lời nhắc!", Toast.LENGTH_SHORT).show()
                            editingReminder = null
                        }.onFailure {
                            Toast.makeText(context, "Không thể cập nhật lời nhắc. Vui lòng thử lại.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    viewModel.addReminder(newReminder, context) { result ->
                        isSaving = false
                        result.onSuccess {
                            Toast.makeText(context, "Đã lưu lời nhắc nhở!", Toast.LENGTH_SHORT).show()
                            showAddScreen = false
                        }.onFailure {
                            Toast.makeText(context, "Không thể lưu lời nhắc. Vui lòng thử lại.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader("Lời nhắc nhở", onBack)

        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Chưa có lời nhắc nào", color = Color.Gray, fontSize = 15.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = reminders, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onClick = { selectedDetailReminder = reminder },
                        onToggle = { enabled ->
                            viewModel.setReminderEnabled(reminder, enabled, context) { result ->
                                result.onFailure {
                                    Toast.makeText(context, "Không thể đổi trạng thái lời nhắc.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Button(
                onClick = { showAddScreen = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Thêm lời nhắc mới", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }

    // DIALOG CHI TIẾT LỜI NHẮC (DETAIL DIALOG)
    if (selectedDetailReminder != null) {
        val rem = selectedDetailReminder!!
        Dialog(onDismissRequest = { selectedDetailReminder = null }) {
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
                        Text("Chi tiết lời nhắc", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        IconButton(onClick = { selectedDetailReminder = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        InfoDetailRow("Tên mục", rem.title.ifBlank { "Lời nhắc nhở" })
                        InfoDetailRow("Tần suất", rem.frequency)
                        InfoDetailRow("Ngày bắt đầu", rem.startDate)
                        InfoDetailRow("Thời gian", rem.time)
                        if (rem.note.isNotBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Text("Ghi chú:", color = Color.Gray, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(rem.note, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                deletingReminder = rem
                                selectedDetailReminder = null
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
                                editingReminder = rem
                                selectedDetailReminder = null
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

    // DIALOG XÁC NHẬN XÓA LỜI NHẮC
    if (deletingReminder != null) {
        AlertDialog(
            onDismissRequest = { deletingReminder = null },
            title = { Text("Xác nhận xóa", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Bạn có chắc chắn muốn xóa lời nhắc '${deletingReminder?.title}' không?", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                Button(
                    onClick = {
                        deletingReminder?.let { reminder ->
                            viewModel.deleteReminder(reminder.id, context) { result ->
                                result.onSuccess {
                                    Toast.makeText(context, "Đã xóa lời nhắc", Toast.LENGTH_SHORT).show()
                                    deletingReminder = null
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "Không thể xóa lời nhắc. Vui lòng thử lại.",
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
                TextButton(onClick = { deletingReminder = null }) {
                    Text("Hủy", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun InfoDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 14.sp)
        Text(text = value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ReminderCard(
    reminder: Reminder,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2196F3).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Notifications, null, tint = Color(0xFF2196F3), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(reminder.title.ifBlank { "Lời nhắc nhở" }, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (reminder.note.isNotBlank()) {
                    Text(reminder.note, color = Color.Gray, fontSize = 13.sp)
                }
                Text("${reminder.frequency} • ${reminder.time} (${reminder.startDate})", color = Color(0xFF2196F3), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Switch(
                checked = reminder.isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
fun AddReminderScreen(
    reminderToEdit: Reminder? = null,
    isSaving: Boolean = false,
    onBack: () -> Unit,
    onSave: (Reminder) -> Unit
) {
    val isEditMode = reminderToEdit != null
    var title by remember { mutableStateOf(reminderToEdit?.title ?: "") }
    var frequency by remember { mutableStateOf(reminderToEdit?.frequency ?: "Hàng ngày") }
    var startDate by remember {
        mutableStateOf(
            reminderToEdit?.startDate ?: RecurringSchedule.formatStartDate(java.time.LocalDate.now())
        )
    }
    var time by remember { mutableStateOf(reminderToEdit?.time ?: "20:15") }
    var note by remember { mutableStateOf(reminderToEdit?.note ?: "") }

    var showTimePicker by remember { mutableStateOf(false) }
    var showFrequencyDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    BackHandler { if (!isSaving) onBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar Header
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
            Text(if (isEditMode) "Sửa lời nhắc" else "Thêm lời nhắc", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.Check, "Lưu",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val finalTitle = title.ifBlank { "Lời nhắc nhở" }
                        val finalNote = note.ifBlank { "Đừng quên ghi lại các khoản chi tiêu của bạn!" }
                        val base = reminderToEdit ?: Reminder()
                        onSave(
                            base.copy(
                                title = finalTitle,
                                frequency = frequency,
                                startDate = startDate,
                                time = time,
                                note = finalNote
                            )
                        )
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

            FormInputBlock("Tên mục nhắc nhở", title, { title = it }, "Lời nhắc nhở")

            FormStaticBlock("Tần suất nhắc nhở", frequency, isDropdown = true, onClick = { showFrequencyDialog = true })

            FormStaticBlock("Ngày bắt đầu nhắc nhở", startDate, isDropdown = true, onClick = { showDatePicker = true })

            FormStaticBlock("Thời gian", time, isDropdown = true, onClick = { showTimePicker = true })

            FormInputBlock("Ghi chú", note, { note = it }, "Đừng quên ghi lại các khoản chi tiêu của bạn!")

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

    if (showDatePicker) {
        CustomDatePickerDialog(
            initialDate = startDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { dateStr ->
                startDate = dateStr
                showDatePicker = false
            }
        )
    }

    if (showFrequencyDialog) {
        Dialog(onDismissRequest = { showFrequencyDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tần suất nhắc nhở", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    listOf("Hàng ngày", "Hàng tuần", "Hàng tháng").forEach { freq ->
                        Text(
                            text = freq,
                            color = if (frequency == freq) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    frequency = freq
                                    showFrequencyDialog = false
                                }
                                .padding(vertical = 12.dp),
                            fontSize = 15.sp,
                            fontWeight = if (frequency == freq) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomDatePickerDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val parsedInitialDate = remember(initialDate) {
        RecurringSchedule.parseStartDate(initialDate) ?: java.time.LocalDate.now()
    }
    var yearMonth by remember { mutableStateOf(java.time.YearMonth.from(parsedInitialDate)) }
    var selectedDay by remember { mutableIntStateOf(parsedInitialDate.dayOfMonth) }

    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value % 7

    val selectedLocalDate = remember(yearMonth, selectedDay) {
        val validDay = selectedDay.coerceIn(1, daysInMonth)
        yearMonth.atDay(validDay)
    }

    val dayOfWeekName = when (selectedLocalDate.dayOfWeek.value) {
        7 -> "CN"
        1 -> "Th 2"
        2 -> "Th 3"
        3 -> "Th 4"
        4 -> "Th 5"
        5 -> "Th 6"
        6 -> "Th 7"
        else -> ""
    }

    val dateHeaderStr = "$dayOfWeekName, ${selectedLocalDate.dayOfMonth} thg ${selectedLocalDate.monthValue}, ${selectedLocalDate.year}"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = dateHeaderStr,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "tháng ${yearMonth.monthValue} năm ${yearMonth.year}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row {
                        IconButton(
                            onClick = { yearMonth = yearMonth.minusMonths(1) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "<", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { yearMonth = yearMonth.plusMonths(1) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, ">", tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("CN", "T2", "T3", "T4", "T5", "T6", "T7").forEach { dayName ->
                        Text(
                            text = dayName,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val totalCells = firstDayOfWeek + daysInMonth
                val rowsCount = (totalCells + 6) / 7

                Column {
                    for (row in 0 until rowsCount) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            for (col in 0 until 7) {
                                val cellIndex = row * 7 + col
                                val dayNum = cellIndex - firstDayOfWeek + 1

                                if (dayNum in 1..daysInMonth) {
                                    val isSelected = dayNum == selectedLocalDate.dayOfMonth
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0xFFFFD54F) else Color.Transparent)
                                            .clickable { selectedDay = dayNum },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$dayNum",
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy", color = Color(0xFFFFD54F), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = {
                        onConfirm("${selectedLocalDate.dayOfMonth} thg ${selectedLocalDate.monthValue}, ${selectedLocalDate.year}")
                    }) {
                        Text("Xác nhận", color = Color(0xFFFFD54F), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomTimePickerDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val parts = initialTime.split(":")
    var hour by remember { mutableIntStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 20) }
    var minute by remember { mutableIntStateOf(parts.getOrNull(1)?.toIntOrNull() ?: 15) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = String.format("%02d:%02d", hour, minute),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimeColumnBox(
                        value = hour,
                        minValue = 0,
                        maxValue = 23,
                        onValueChange = { hour = it }
                    )

                    Text(
                        text = ":",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    TimeColumnBox(
                        value = minute,
                        minValue = 0,
                        maxValue = 59,
                        onValueChange = { minute = it }
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy", color = Color(0xFFFFD54F), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = {
                        onConfirm(String.format("%02d:%02d", hour, minute))
                    }) {
                        Text("Xác nhận", color = Color(0xFFFFD54F), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TimeColumnBox(
    value: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit
) {
    val prev = if (value - 1 < minValue) maxValue else value - 1
    val next = if (value + 1 > maxValue) minValue else value + 1

    Box(
        modifier = Modifier
            .width(100.dp)
            .height(130.dp)
            .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight()
        ) {
            Text(
                text = String.format("%02d", prev),
                color = Color.Gray,
                fontSize = 20.sp,
                modifier = Modifier.clickable { onValueChange(prev) }
            )
            Text(
                text = String.format("%02d", value),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = String.format("%02d", next),
                color = Color.Gray,
                fontSize = 20.sp,
                modifier = Modifier.clickable { onValueChange(next) }
            )
        }
    }
}
