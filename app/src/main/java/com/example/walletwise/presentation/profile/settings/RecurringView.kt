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
import com.example.walletwise.presentation.recurring.RecurringContent
import com.example.walletwise.presentation.recurring.RecurringMessage
import com.example.walletwise.presentation.recurring.RecurringUiEvent

@Composable
fun RecurringView(viewModel: TransactionViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presenter = remember(viewModel) {
        viewModel.createRecurringPresenter(scope, context.applicationContext)
    }
    val state by presenter.state.collectAsStateWithLifecycle()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> presenter.onPermissionRequestResult(ReminderPermission.NOTIFICATIONS, granted) }
    val exactAlarmPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        presenter.onPermissionRequestResult(ReminderPermission.EXACT_ALARM, canScheduleExactAlarms(context))
    }

    DisposableEffect(presenter) { onDispose { presenter.close() } }
    BackHandler { presenter.onBack() }

    LaunchedEffect(state.pendingEvent?.id) {
        val envelope = state.pendingEvent ?: return@LaunchedEffect
        when (val event = envelope.event) {
            RecurringUiEvent.NavigateBack -> {
                presenter.consumeEvent(envelope.id)
                onBack()
            }
            is RecurringUiEvent.Message -> {
                Toast.makeText(
                    context,
                    event.message(),
                    if (event.kind == RecurringMessage.WRITE_ERROR ||
                        event.kind == RecurringMessage.SCHEDULING_ERROR
                    ) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                ).show()
                presenter.consumeEvent(envelope.id)
            }
            is RecurringUiEvent.RequestPermission -> when (event.permission) {
                ReminderPermission.NOTIFICATIONS -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        presenter.onPermissionRequestResult(event.permission, granted = true)
                    }
                }
                ReminderPermission.EXACT_ALARM -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms(context)) {
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

    RecurringContent(
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
        onAmountChanged = presenter::onAmountChanged,
        onNoteChanged = presenter::onNoteChanged,
        onTypeSelected = presenter::onTypeSelected,
        onOpenPicker = presenter::onOpenPicker,
        onFrequencySelected = presenter::onFrequencySelected,
        onTimesCountSelected = presenter::onTimesCountSelected,
        onCategorySelected = presenter::onCategorySelected,
        onPaymentMethodSelected = presenter::onPaymentMethodSelected,
        onPreviousDatePickerMonth = presenter::onPreviousDatePickerMonth,
        onNextDatePickerMonth = presenter::onNextDatePickerMonth,
        onDatePickerDaySelected = presenter::onDatePickerDaySelected,
        onConfirmDatePicker = presenter::onConfirmDatePicker,
        onTimePickerHourChanged = presenter::onTimePickerHourChanged,
        onTimePickerMinuteChanged = presenter::onTimePickerMinuteChanged,
        onConfirmTimePicker = presenter::onConfirmTimePicker,
        onDismissPicker = presenter::onDismissPicker
    )
}

private fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

private fun RecurringUiEvent.Message.message(): String = when (kind) {
    RecurringMessage.ADDED -> "Đã lưu giao dịch định kỳ!"
    RecurringMessage.UPDATED -> "Đã cập nhật giao dịch định kỳ!"
    RecurringMessage.DELETED -> "Đã xóa giao dịch định kỳ."
    RecurringMessage.WRITE_ERROR -> detail ?: "Không thể cập nhật giao dịch định kỳ. Vui lòng thử lại."
    RecurringMessage.SCHEDULING_ERROR -> detail ?: "Dữ liệu đã lưu nhưng không thể đặt lịch."
    RecurringMessage.PERMISSION_DENIED -> "Chưa được cấp quyền cần thiết; lịch vẫn được giữ với khả năng Android cho phép."
}
