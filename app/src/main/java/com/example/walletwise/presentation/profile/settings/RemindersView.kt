package com.example.walletwise.presentation.profile.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walletwise.domain.service.ReminderPermission
import com.example.walletwise.presentation.home.TransactionViewModel
import com.example.walletwise.presentation.reminder.ReminderContent
import com.example.walletwise.presentation.reminder.ReminderMessage
import com.example.walletwise.presentation.reminder.ReminderUiEvent

@Composable
fun RemindersView(viewModel: TransactionViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presenter = remember(viewModel) {
        viewModel.createReminderPresenter(scope, context.applicationContext)
    }
    val state by presenter.state.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        presenter.onPermissionRequestResult(ReminderPermission.NOTIFICATIONS, granted)
    }
    val exactAlarmPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        presenter.onPermissionRequestResult(
            ReminderPermission.EXACT_ALARM,
            canScheduleExactAlarms(context)
        )
    }

    DisposableEffect(presenter) {
        onDispose { presenter.close() }
    }

    BackHandler { presenter.onBack() }

    LaunchedEffect(state.pendingEvent?.id) {
        val envelope = state.pendingEvent ?: return@LaunchedEffect
        when (val event = envelope.event) {
            ReminderUiEvent.NavigateBack -> {
                presenter.consumeEvent(envelope.id)
                onBack()
            }
            is ReminderUiEvent.Message -> {
                Toast.makeText(
                    context,
                    event.message(),
                    if (event.kind == ReminderMessage.WRITE_ERROR ||
                        event.kind == ReminderMessage.SCHEDULING_ERROR
                    ) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                ).show()
                presenter.consumeEvent(envelope.id)
            }
            is ReminderUiEvent.RequestPermission -> when (event.permission) {
                ReminderPermission.NOTIFICATIONS -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        presenter.onPermissionRequestResult(event.permission, granted = true)
                    }
                }
                ReminderPermission.EXACT_ALARM -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        !canScheduleExactAlarms(context)
                    ) {
                        runCatching {
                            exactAlarmPermissionLauncher.launch(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        }.onFailure {
                            presenter.onPermissionRequestResult(event.permission, granted = false)
                        }
                    } else {
                        presenter.onPermissionRequestResult(event.permission, granted = true)
                    }
                }
            }
        }
    }

    ReminderContent(
        state = state,
        onBack = presenter::onBack,
        onOpenAdd = presenter::onOpenAdd,
        onOpenDetail = presenter::onOpenDetail,
        onDismissDetail = presenter::onDismissDetail,
        onOpenEdit = presenter::onOpenEdit,
        onRequestDelete = presenter::onRequestDelete,
        onCancelDelete = presenter::onCancelDelete,
        onConfirmDelete = presenter::onConfirmDelete,
        onToggle = presenter::onToggle,
        onCancelForm = presenter::onCancelForm,
        onSubmit = presenter::onSubmit,
        onTitleChanged = presenter::onTitleChanged,
        onNoteChanged = presenter::onNoteChanged,
        onOpenFrequencyPicker = presenter::onOpenFrequencyPicker,
        onFrequencySelected = presenter::onFrequencySelected,
        onOpenDatePicker = presenter::onOpenDatePicker,
        onPreviousDatePickerMonth = presenter::onPreviousDatePickerMonth,
        onNextDatePickerMonth = presenter::onNextDatePickerMonth,
        onDatePickerDaySelected = presenter::onDatePickerDaySelected,
        onConfirmDatePicker = presenter::onConfirmDatePicker,
        onOpenTimePicker = presenter::onOpenTimePicker,
        onTimePickerHourChanged = presenter::onTimePickerHourChanged,
        onTimePickerMinuteChanged = presenter::onTimePickerMinuteChanged,
        onConfirmTimePicker = presenter::onConfirmTimePicker,
        onDismissPicker = presenter::onDismissPicker
    )
}

private fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

private fun ReminderUiEvent.Message.message(): String = when (kind) {
    ReminderMessage.ADDED -> "Đã lưu lời nhắc nhở!"
    ReminderMessage.UPDATED -> "Đã cập nhật lời nhắc!"
    ReminderMessage.DELETED -> "Đã xóa lời nhắc"
    ReminderMessage.WRITE_ERROR -> detail ?: "Không thể cập nhật lời nhắc. Vui lòng thử lại."
    ReminderMessage.SCHEDULING_ERROR ->
        "Dữ liệu đã được lưu nhưng không thể đặt lịch nhắc. Vui lòng thử cấp quyền hoặc đồng bộ lại."
    ReminderMessage.PERMISSION_DENIED -> "Chưa được cấp quyền cần thiết để lời nhắc hoạt động đầy đủ."
}
