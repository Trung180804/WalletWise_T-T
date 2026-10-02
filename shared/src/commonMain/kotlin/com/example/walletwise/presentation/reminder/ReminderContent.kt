package com.example.walletwise.presentation.reminder

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
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
import com.example.walletwise.domain.model.REMINDER_FREQUENCY_DAILY
import com.example.walletwise.domain.model.REMINDER_FREQUENCY_MONTHLY
import com.example.walletwise.domain.model.REMINDER_FREQUENCY_WEEKLY
import com.example.walletwise.domain.model.Reminder
import com.example.walletwise.domain.service.ReminderCalendar
import com.example.walletwise.domain.service.ReminderLocalDate
import com.example.walletwise.domain.validation.ReminderValidationError
import com.example.walletwise.presentation.profile.FormInputBlock
import com.example.walletwise.presentation.profile.FormStaticBlock
import com.example.walletwise.presentation.profile.ThemedDivider
import com.example.walletwise.presentation.profile.TopHeader
import com.example.walletwise.shared.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ReminderContent(
    state: ReminderUiState,
    onBack: () -> Unit,
    onOpenAdd: () -> Unit,
    onOpenDetail: (Reminder) -> Unit,
    onDismissDetail: () -> Unit,
    onOpenEdit: (Reminder) -> Unit,
    onRequestDelete: (Reminder) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onToggle: (Reminder, Boolean) -> Unit,
    onCancelForm: () -> Unit,
    onSubmit: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onOpenFrequencyPicker: () -> Unit,
    onFrequencySelected: (String) -> Unit,
    onOpenDatePicker: () -> Unit,
    onPreviousDatePickerMonth: () -> Unit,
    onNextDatePickerMonth: () -> Unit,
    onDatePickerDaySelected: (Int) -> Unit,
    onConfirmDatePicker: () -> Unit,
    onOpenTimePicker: () -> Unit,
    onTimePickerHourChanged: (Int) -> Unit,
    onTimePickerMinuteChanged: (Int) -> Unit,
    onConfirmTimePicker: () -> Unit,
    onDismissPicker: () -> Unit
) {
    if (state.screenMode == ReminderScreenMode.FORM) {
        ReminderFormContent(
            state = state,
            onBack = onCancelForm,
            onSubmit = onSubmit,
            onTitleChanged = onTitleChanged,
            onNoteChanged = onNoteChanged,
            onOpenFrequencyPicker = onOpenFrequencyPicker,
            onOpenDatePicker = onOpenDatePicker,
            onOpenTimePicker = onOpenTimePicker
        )
    } else {
        ReminderListContent(
            state = state,
            onBack = onBack,
            onOpenAdd = onOpenAdd,
            onOpenDetail = onOpenDetail,
            onToggle = onToggle
        )
    }

    state.selectedDetailReminder?.let { reminder ->
        ReminderDetailDialog(
            reminder = reminder,
            onDismiss = onDismissDetail,
            onDelete = { onRequestDelete(reminder) },
            onEdit = { onOpenEdit(reminder) }
        )
    }

    state.deletingReminder?.let { reminder ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = {
                Text(
                    stringResource(Res.string.reminder_delete_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    stringResource(Res.string.reminder_delete_question, reminder.title),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDelete,
                    enabled = state.deletingReminderId == null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA3B70))
                ) {
                    if (state.deletingReminderId != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            stringResource(Res.string.reminder_delete),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onCancelDelete,
                    enabled = state.deletingReminderId == null
                ) {
                    Text(
                        stringResource(Res.string.reminder_cancel),
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    when (state.activePicker) {
        ReminderPicker.FREQUENCY -> FrequencyDialog(
            selected = state.frequency,
            onDismiss = onDismissPicker,
            onSelected = onFrequencySelected
        )
        ReminderPicker.DATE -> ReminderDatePickerDialog(
            state = state,
            onDismiss = onDismissPicker,
            onPreviousMonth = onPreviousDatePickerMonth,
            onNextMonth = onNextDatePickerMonth,
            onDaySelected = onDatePickerDaySelected,
            onConfirm = onConfirmDatePicker
        )
        ReminderPicker.TIME -> ReminderTimePickerDialog(
            hour = state.timePickerHour,
            minute = state.timePickerMinute,
            onDismiss = onDismissPicker,
            onHourChanged = onTimePickerHourChanged,
            onMinuteChanged = onTimePickerMinuteChanged,
            onConfirm = onConfirmTimePicker
        )
        ReminderPicker.NONE -> Unit
    }
}

@Composable
private fun ReminderListContent(
    state: ReminderUiState,
    onBack: () -> Unit,
    onOpenAdd: () -> Unit,
    onOpenDetail: (Reminder) -> Unit,
    onToggle: (Reminder, Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader(stringResource(Res.string.reminder_title), onBack)

        if (state.repositoryError != null || state.schedulingState == ReminderSchedulingUiState.ERROR) {
            Text(
                text = if (state.schedulingState == ReminderSchedulingUiState.ERROR) {
                    stringResource(Res.string.reminder_schedule_error)
                } else {
                    stringResource(Res.string.reminder_repository_error)
                },
                color = Color(0xFFFA3B70),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        when {
            state.isLoading && state.reminders.isEmpty() -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.reminders.isEmpty() -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(Res.string.reminder_empty),
                    color = Color.Gray,
                    fontSize = 15.sp
                )
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = state.reminders, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        toggleEnabled = reminder.id !in state.togglingReminderIds,
                        onClick = { onOpenDetail(reminder) },
                        onToggle = { enabled -> onToggle(reminder, enabled) }
                    )
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onOpenAdd,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.reminder_add),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    toggleEnabled: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
                Icon(
                    Icons.Default.Notifications,
                    null,
                    tint = Color(0xFF2196F3),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reminder.title.ifBlank { stringResource(Res.string.reminder_default_title) },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (reminder.note.isNotBlank()) {
                    Text(reminder.note, color = Color.Gray, fontSize = 13.sp)
                }
                Text(
                    "${reminder.frequency} • ${reminder.time} (${reminder.startDate})",
                    color = Color(0xFF2196F3),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Switch(
                checked = reminder.isEnabled,
                onCheckedChange = onToggle,
                enabled = toggleEnabled
            )
        }
    }
}

@Composable
private fun ReminderDetailDialog(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
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
                    Text(
                        stringResource(Res.string.reminder_detail_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(Res.string.reminder_close),
                            tint = Color.Gray
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    ReminderInfoRow(
                        stringResource(Res.string.reminder_detail_name),
                        reminder.title.ifBlank { stringResource(Res.string.reminder_default_title) }
                    )
                    ReminderInfoRow(stringResource(Res.string.reminder_frequency), reminder.frequency)
                    ReminderInfoRow(stringResource(Res.string.reminder_start_date), reminder.startDate)
                    ReminderInfoRow(stringResource(Res.string.reminder_time), reminder.time)
                    if (reminder.note.isNotBlank()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(stringResource(Res.string.reminder_note), color = Color.Gray, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(reminder.note, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        border = BorderStroke(1.dp, Color.Red),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(Res.string.reminder_delete), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(Res.string.reminder_edit),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ReminderFormContent(
    state: ReminderUiState,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onOpenFrequencyPicker: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onOpenTimePicker: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.reminder_cancel),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                modifier = Modifier.clickable(enabled = !state.isSaving, onClick = onBack)
            )
            Text(
                if (state.isEditMode) {
                    stringResource(Res.string.reminder_edit_title)
                } else {
                    stringResource(Res.string.reminder_add_title)
                },
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.Check,
                    stringResource(Res.string.reminder_save),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onSubmit)
                )
            }
        }
        ThemedDivider()
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            FormInputBlock(
                stringResource(Res.string.reminder_form_title),
                state.title,
                onTitleChanged,
                stringResource(Res.string.reminder_default_title)
            )
            FormStaticBlock(
                stringResource(Res.string.reminder_form_frequency),
                state.frequency,
                isDropdown = true,
                onClick = onOpenFrequencyPicker
            )
            FormStaticBlock(
                stringResource(Res.string.reminder_form_start_date),
                state.startDate,
                isDropdown = true,
                onClick = onOpenDatePicker
            )
            FormStaticBlock(
                stringResource(Res.string.reminder_time),
                state.time,
                isDropdown = true,
                onClick = onOpenTimePicker
            )
            FormInputBlock(
                stringResource(Res.string.reminder_form_note),
                state.note,
                onNoteChanged,
                stringResource(Res.string.reminder_default_note)
            )
            state.validationError?.let {
                Text(
                    validationMessage(it),
                    color = Color(0xFFFA3B70),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            state.repositoryError?.let {
                Text(
                    it.message.ifBlank { stringResource(Res.string.reminder_repository_error) },
                    color = Color(0xFFFA3B70),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun validationMessage(error: ReminderValidationError): String = when (error) {
    ReminderValidationError.TITLE_REQUIRED -> stringResource(Res.string.reminder_validation_title)
    ReminderValidationError.NOTE_REQUIRED -> stringResource(Res.string.reminder_validation_note)
    ReminderValidationError.DATE_INVALID -> stringResource(Res.string.reminder_validation_date)
    ReminderValidationError.TIME_INVALID -> stringResource(Res.string.reminder_validation_time)
    ReminderValidationError.FREQUENCY_INVALID -> stringResource(Res.string.reminder_validation_frequency)
    else -> stringResource(Res.string.reminder_validation_general)
}

@Composable
private fun FrequencyDialog(
    selected: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(Res.string.reminder_form_frequency),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(12.dp))
                listOf(
                    REMINDER_FREQUENCY_DAILY to stringResource(Res.string.reminder_daily),
                    REMINDER_FREQUENCY_WEEKLY to stringResource(Res.string.reminder_weekly),
                    REMINDER_FREQUENCY_MONTHLY to stringResource(Res.string.reminder_monthly)
                ).forEach { (wireValue, label) ->
                    Text(
                        text = label,
                        color = if (selected == wireValue) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onSelected(wireValue) }
                            .padding(vertical = 12.dp),
                        fontSize = 15.sp,
                        fontWeight = if (selected == wireValue) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderDatePickerDialog(
    state: ReminderUiState,
    onDismiss: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    val month = state.datePickerMonth
    val daysInMonth = ReminderCalendar.daysInMonth(month.year, month.month)
    val selectedDay = state.datePickerSelectedDay.coerceIn(1, daysInMonth)
    val selectedDate = ReminderLocalDate(month.year, month.month, selectedDay)
    val firstDayOfWeek = ReminderCalendar.sundayBasedDayOfWeek(
        ReminderLocalDate(month.year, month.month, 1)
    ) ?: 0
    val weekdayLabels = reminderWeekdayLabels()
    val selectedWeekday = ReminderCalendar.sundayBasedDayOfWeek(selectedDate) ?: 0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                Text(
                    stringResource(
                        Res.string.reminder_date_header,
                        weekdayLabels[selectedWeekday],
                        selectedDate.dayOfMonth,
                        selectedDate.month,
                        selectedDate.year
                    ),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.reminder_calendar_month, month.month, month.year),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row {
                        IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "<", tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, ">", tint = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    weekdayLabels.forEach { dayName ->
                        Text(
                            dayName,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val rowsCount = (firstDayOfWeek + daysInMonth + 6) / 7
                Column {
                    for (row in 0 until rowsCount) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            for (column in 0 until 7) {
                                val day = row * 7 + column - firstDayOfWeek + 1
                                if (day in 1..daysInMonth) {
                                    val isSelected = day == selectedDay
                                    Box(
                                        modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Color(0xFFFFD54F) else Color.Transparent
                                            )
                                            .clickable { onDaySelected(day) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            day.toString(),
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                PickerButtons(onDismiss = onDismiss, onConfirm = onConfirm)
            }
        }
    }
}

@Composable
private fun reminderWeekdayLabels(): List<String> = listOf(
    stringResource(Res.string.reminder_weekday_sunday),
    stringResource(Res.string.reminder_weekday_monday),
    stringResource(Res.string.reminder_weekday_tuesday),
    stringResource(Res.string.reminder_weekday_wednesday),
    stringResource(Res.string.reminder_weekday_thursday),
    stringResource(Res.string.reminder_weekday_friday),
    stringResource(Res.string.reminder_weekday_saturday)
)

@Composable
private fun ReminderTimePickerDialog(
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onHourChanged: (Int) -> Unit,
    onMinuteChanged: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${twoDigits(hour)}:${twoDigits(minute)}",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimeColumnBox(hour, 0, 23, onHourChanged)
                    Text(
                        ":",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    TimeColumnBox(minute, 0, 59, onMinuteChanged)
                }
                Spacer(Modifier.height(28.dp))
                PickerButtons(onDismiss = onDismiss, onConfirm = onConfirm)
            }
        }
    }
}

@Composable
private fun PickerButtons(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onDismiss) {
            Text(
                stringResource(Res.string.reminder_cancel),
                color = Color(0xFFFFD54F),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        TextButton(onClick = onConfirm) {
            Text(
                stringResource(Res.string.reminder_confirm),
                color = Color(0xFFFFD54F),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TimeColumnBox(
    value: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit
) {
    val previous = if (value - 1 < minValue) maxValue else value - 1
    val next = if (value + 1 > maxValue) minValue else value + 1
    Box(
        modifier = Modifier.width(100.dp).height(130.dp)
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
                twoDigits(previous),
                color = Color.Gray,
                fontSize = 20.sp,
                modifier = Modifier.clickable { onValueChange(previous) }
            )
            Text(
                twoDigits(value),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                twoDigits(next),
                color = Color.Gray,
                fontSize = 20.sp,
                modifier = Modifier.clickable { onValueChange(next) }
            )
        }
    }
}

private fun twoDigits(value: Int): String = if (value in 0..9) "0$value" else value.toString()
