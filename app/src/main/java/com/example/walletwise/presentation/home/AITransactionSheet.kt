package com.example.walletwise.presentation.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.unit.dp
import com.example.walletwise.domain.service.MoneyInput
import com.example.walletwise.domain.model.TransactionDraft
import com.example.walletwise.presentation.transaction.GroupedMoneyTransformation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AITransactionSheet(viewModel: TransactionViewModel) {
    val context = LocalContext.current
    SideEffect { viewModel.attachDraftContext(context) }
    val state by viewModel.aiDraftState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val receipt by viewModel.receiptRecognition.state.collectAsState()
    val preview by viewModel.receiptRecognition.preview.collectAsState()
    val decoding by viewModel.receiptRecognition.isDecoding.collectAsState()
    val busy = state.isAnalyzing || state.isSaving || receipt.isRecognizing || decoding
    var captureUserId by remember(state.userId) { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (captureUserId != null && captureUserId == state.userId) viewModel.receiptRecognition.cameraResult(context, success)
        else viewModel.receiptRecognition.cancel()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (captureUserId != null && captureUserId == state.userId) viewModel.receiptRecognition.galleryResult(context, uri)
    }
    LaunchedEffect(receipt.draft?.transaction?.id) { receipt.draft?.let(viewModel::acceptReceiptDraft) }
    DisposableEffect(viewModel) { onDispose { if ((context as? Activity)?.isChangingConfigurations != true) viewModel.resetAIState() } }
    var input by rememberSaveable(state.userId) { mutableStateOf("") }
    var voiceMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceUserId by remember(state.userId) { mutableStateOf<String?>(null) }
    val voiceGate = remember(state.userId) { com.example.walletwise.presentation.transaction.VoiceInputGate() }
    var voiceRequest by remember(state.userId) { mutableStateOf<com.example.walletwise.presentation.transaction.VoiceInputGate.Request?>(null) }
    DisposableEffect(voiceGate) { onDispose { voiceGate.cancel() } }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (voiceUserId != state.userId) return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) voiceGate.result(voiceRequest, state.userId,
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull())?.let { input = ""; viewModel.processAITransaction(it, com.example.walletwise.domain.model.DraftSource.VOICE) }
        else voiceMessage = "Đã hủy nhập giọng nói. Bạn có thể nhập chữ."
    }
    fun launchVoice() {
        voiceUserId = state.userId
        voiceRequest = voiceGate.begin(state.userId, com.example.walletwise.presentation.transaction.MicrophonePermission.GRANTED)
        try {
            voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        } catch (_: ActivityNotFoundException) { voiceMessage = "Thiết bị chưa có nhận diện giọng nói. Hãy nhập chữ." }
    }
    val sheetMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.60f).dp
    val density = LocalDensity.current
    var titleHeight by remember { mutableStateOf(48.dp) }
    var composerHeight by remember { mutableStateOf(104.dp) }
    BoxWithConstraints(Modifier.fillMaxWidth().imePadding().heightIn(max = sheetMaxHeight)
        .padding(horizontal = 16.dp).testTag("assistant-sheet")) {
    val conversationMaxHeight = (maxHeight - titleHeight - composerHeight - 28.dp).coerceAtLeast(0.dp)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("✨ Trợ lý Tài chính WalletWise", style = MaterialTheme.typography.titleMedium, color = Color(0xFF25232A),
            modifier = Modifier.onSizeChanged { titleHeight = with(density) { it.height.toDp() } })
        Column(Modifier.fillMaxWidth().heightIn(max = conversationMaxHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(state.message, color = Color(0xFF494650))
        if (state.draft == null && !busy) Text("Hóa đơn rõ sẽ được ghi ngay. Thiếu thông tin, trợ lý sẽ hỏi thêm.", color = Color(0xFF79737C))
        if (state.isAnalyzing || state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFFFA3B70))
        preview?.let { Image(it.asImageBitmap(), "Ảnh hóa đơn", Modifier.fillMaxWidth().height(140.dp)) }
        if (receipt.isRecognizing || decoding) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFFFA3B70)); Text("Đang nhận diện hóa đơn…") }
        receipt.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        receipt.draft?.let { recognized ->
            recognized.merchant?.let { Text("Cửa hàng: $it") }
            if (recognized.totalCandidates.size > 1) recognized.totalCandidates.forEach { candidate ->
                TextButton({ state.draft?.let { viewModel.editAIDraft(it.copy(amount = candidate)) } }, enabled = !busy && state.savedDraftId == null) {
                    Text("Chọn tổng ${MoneyInput.format(candidate.toString())} đ")
                }
            }
        }
        state.draft?.takeIf { state.savedDraftId == null && it.missingFields.isNotEmpty() }?.let { draft ->
            MissingFieldInputs(draft, categories.filter { it.type == draft.type }.map { it.name }, !busy,
                viewModel::editAIDraft)
        }
        if (!busy && state.savedDraftId == null && state.draft?.missingFields?.isEmpty() == true) TextButton(viewModel::confirmAIDraft) { Text("Thử lưu lại") }
        if (state.savedDraftId != null) Button({ viewModel.newAIDraft(); viewModel.receiptRecognition.cancel() }) { Text("Giao dịch tiếp theo") }
        voiceMessage?.let {
            Text(it)
        }
        }
        AssistantComposer(input, { input = it }, !busy,
            onCamera = {
                captureUserId = state.userId
                try { camera.launch(viewModel.receiptRecognition.cameraUri(context)) }
                catch (_: Exception) { viewModel.receiptRecognition.cancel(); viewModel.receiptRecognition.permissionDenied() }
            },
            onPicker = {
                captureUserId = state.userId
                try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                catch (_: Exception) { viewModel.receiptRecognition.permissionDenied() }
            },
            // RecognizerIntent delegates recording to the installed speech service; this app
            // does not record audio itself and therefore must not request RECORD_AUDIO.
            onMicrophone = ::launchVoice,
            onSend = { val submitted = input; input = ""; viewModel.receiptRecognition.cancel(); viewModel.processAITransaction(submitted) },
            modifier = Modifier.onSizeChanged { composerHeight = with(density) { it.height.toDp() } })
        Spacer(Modifier.height(4.dp))
    }
    }
}

/** Ordered platform actions; each callback is wired by the Android route above. */
@Composable
fun AssistantComposer(input: String, onInput: (String) -> Unit, enabled: Boolean,
    onCamera: () -> Unit, onPicker: () -> Unit, onMicrophone: () -> Unit, onSend: () -> Unit,
    modifier: Modifier = Modifier) {
    val pink = Color(0xFFFA3B70)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth < 350.dp
        val actionsWidth = (maxWidth * 0.44f).coerceAtLeast(144.dp)
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.width(actionsWidth), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(Triple(Icons.Default.PhotoCamera, "Chụp hóa đơn", onCamera),
                        Triple(Icons.Default.PhotoLibrary, "Chọn hóa đơn từ thư viện", onPicker),
                        Triple(Icons.Default.Mic, "Nhập giọng nói", onMicrophone)).forEach { (icon, label, action) ->
                        IconButton(action, enabled = enabled, modifier = Modifier.size(48.dp)) {
                            Icon(icon, label, tint = if (enabled) pink else pink.copy(alpha = 0.38f))
                        }
                    }
                }
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(28.dp), color = Color(0xFFFAFAFA), border = BorderStroke(1.dp, pink)) {
                    Row(Modifier.heightIn(min = 56.dp).padding(start = 12.dp, end = if (compact) 12.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(input, onInput, enabled = enabled, singleLine = true,
                            textStyle = TextStyle(color = Color(0xFF25232A), fontSize = 14.sp), cursorBrush = SolidColor(pink),
                            modifier = Modifier.weight(1f).testTag("assistant-input"), decorationBox = { inner ->
                                Box { if (input.isEmpty()) Text("Nhập nội dung", fontSize = 14.sp, color = Color(0xFF79737C)); inner() }
                            })
                        if (!compact) IconButton(onSend, enabled = enabled && input.isNotBlank(), modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Ghi giao dịch", tint = if (enabled && input.isNotBlank()) pink else pink.copy(alpha = 0.38f))
                        }
                    }
                }
            }
            if (compact) TextButton(onSend, enabled = enabled && input.isNotBlank(), modifier = Modifier.align(Alignment.End)) { Text("Ghi giao dịch", color = pink) }
        }
    }
}

@Composable
private fun MissingFieldInputs(draft: TransactionDraft, categories: List<String>, enabled: Boolean, onEdit: (TransactionDraft) -> Unit) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT) }
    var dateInput by rememberSaveable(draft.id) { mutableStateOf(draft.timestamp?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter) }.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (draft.missingFields.isNotEmpty()) Text("Cần bổ sung: " + draft.missingFields.joinToString { field -> when (field) {
            com.example.walletwise.domain.model.DraftField.AMOUNT -> "số tiền"; com.example.walletwise.domain.model.DraftField.TYPE -> "loại";
            com.example.walletwise.domain.model.DraftField.CATEGORY -> "danh mục"; com.example.walletwise.domain.model.DraftField.DATE -> "ngày";
            com.example.walletwise.domain.model.DraftField.PAYMENT_METHOD -> "thanh toán"
        } })
        if (com.example.walletwise.domain.model.DraftField.TYPE in draft.missingFields) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Chi", "Thu").forEach { type -> FilterChip(draft.type == type, { onEdit(draft.copy(type = type, category = null)) },
                label = { Text(if (type == "Chi") "Chi tiêu" else "Thu nhập") }, enabled = enabled) }
        }
        if (com.example.walletwise.domain.model.DraftField.AMOUNT in draft.missingFields) Text("Nhập số tiền ở thanh bên dưới rồi gửi.", style = MaterialTheme.typography.bodyMedium)
        var expanded by remember { mutableStateOf(false) }
        if (com.example.walletwise.domain.model.DraftField.CATEGORY in draft.missingFields) Box {
            OutlinedButton({ expanded = true }, enabled = enabled) { Text(draft.category ?: "Chọn danh mục") }
            DropdownMenu(expanded, { expanded = false }) { categories.forEach { category ->
                DropdownMenuItem({ Text(category) }, { onEdit(draft.copy(category = category)); expanded = false })
            } }
        }
        if (com.example.walletwise.domain.model.DraftField.DATE in draft.missingFields) OutlinedTextField(dateInput, { value ->
            dateInput = value
            val date = try { LocalDate.parse(value, dateFormatter).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (_: java.time.DateTimeException) { null }
            onEdit(draft.copy(timestamp = date))
        }, label = { Text("Ngày dd/MM/yyyy") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        if (com.example.walletwise.domain.model.DraftField.PAYMENT_METHOD in draft.missingFields) listOf("Tiền mặt", "Chuyển khoản", "Thẻ tín dụng").forEach { payment ->
            FilterChip(draft.paymentMethod == payment, { onEdit(draft.copy(paymentMethod = payment)) }, label = { Text(payment) }, enabled = enabled)
        }
    }
}
