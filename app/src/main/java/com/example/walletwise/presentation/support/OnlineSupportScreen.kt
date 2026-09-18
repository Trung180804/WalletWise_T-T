package com.example.walletwise.presentation.support

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.walletwise.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportTopBar(title: String, onBack: () -> Unit, subtitle: String? = null) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại")
            }
        }
    )
}

@Composable
fun OnlineSupportScreen(model: SupportViewModel, onBack: () -> Unit, actionsOverride: SupportActions? = null) {
    val context = LocalContext.current
    val actions = actionsOverride ?: remember(context) { AndroidSupportActions(context) }
    val emailState by model.email.state.collectAsStateWithLifecycle()
    val uid = emailState.userId
    var destinationName by rememberSaveable(uid) { mutableStateOf(SupportDestination.HUB.name) }
    val destination = SupportDestination.valueOf(destinationName)
    var phoneVisible by rememberSaveable(uid) { mutableStateOf(false) }
    var missingEmail by rememberSaveable(uid) { mutableStateOf(false) }
    val snackbar = remember(uid) { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun notify(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    fun copy(value: String, message: String) { notify(if (actions.copy(value)) message else "Không thể sao chép. Vui lòng thử lại.") }
    val goBack = { if (destination == SupportDestination.HUB) onBack() else destinationName = SupportDestination.HUB.name }
    BackHandler { when { phoneVisible -> phoneVisible = false; missingEmail -> missingEmail = false; else -> goBack() } }

    Scaffold(
        topBar = {
            SupportTopBar(
                title = when (destination) {
                    SupportDestination.CHAT -> "Chăm sóc khách hàng"
                    SupportDestination.EMAIL -> "Hỗ trợ qua email"
                    else -> "Hỗ trợ trực tuyến"
                },
                onBack = goBack
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                SupportDestination.HUB -> SupportHub(
                    onChat = { destinationName = SupportDestination.CHAT.name },
                    onPhone = { phoneVisible = true },
                    onEmail = { destinationName = SupportDestination.EMAIL.name }
                )
                SupportDestination.CHAT -> SupportChat(model.chat)
                SupportDestination.EMAIL -> SupportEmail(model.email) { draft ->
                    if (actions.openEmail(draft)) notify("Đã mở ứng dụng email. Vui lòng kiểm tra và gửi trong ứng dụng đó.")
                    else missingEmail = true
                }
            }
        }
    }
    if (phoneVisible) AlertDialog(
        onDismissRequest = { phoneVisible = false },
        icon = { Icon(Icons.Default.Phone, null) },
        title = { Text("Liên hệ tổng đài") }, text = { Text(SupportContact.PHONE, style = MaterialTheme.typography.headlineSmall) },
        confirmButton = { TextButton(onClick = {
            phoneVisible = false
            if (!actions.dial()) notify("Không tìm thấy ứng dụng gọi điện. Bạn có thể sao chép số tổng đài.")
        }) { Text("Gọi ngay") } },
        dismissButton = { Row {
            TextButton(onClick = { copy(SupportContact.PHONE, "Đã sao chép số tổng đài.") }) { Text("Sao chép số") }
            TextButton(onClick = { phoneVisible = false }) { Text("Đóng") }
        } }
    )
    if (missingEmail) AlertDialog(
        onDismissRequest = { missingEmail = false },
        title = { Text("Hỗ trợ qua email") },
        text = { Text("Không tìm thấy ứng dụng email. Bạn có thể sao chép địa chỉ hỗ trợ.") },
        confirmButton = { TextButton(onClick = {
            missingEmail = false; copy(SupportContact.EMAIL, "Đã sao chép địa chỉ hỗ trợ.")
        }) { Text("Sao chép địa chỉ") } },
        dismissButton = { TextButton(onClick = { missingEmail = false }) { Text("Đóng") } }
    )
}

@Composable
fun SupportHub(onChat: () -> Unit, onPhone: () -> Unit, onEmail: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(76.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.HeadsetMic, "Hỗ trợ khách hàng", Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Text("Hỗ trợ ngay", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Text("Liên hệ với chúng tôi bằng hình thức phù hợp với bạn.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SupportOption("Hỗ trợ qua chat", "Gửi tin nhắn để Chăm sóc khách hàng phản hồi khi có thể.", Icons.Default.ChatBubbleOutline, onChat)
        SupportOption("Hỗ trợ qua điện thoại", "Mở ứng dụng Điện thoại để liên hệ tổng đài.", Icons.Default.Phone, onPhone)
        SupportOption("Hỗ trợ qua email", "Soạn email và đính kèm tệp bạn muốn chia sẻ.", Icons.Default.Email, onEmail)
    }
}

@Composable
private fun SupportOption(title: String, description: String, icon: ImageVector, click: () -> Unit) {
    Card(
        onClick = click,
        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).semantics { contentDescription = title },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(icon, null, Modifier.padding(12.dp).size(26.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
fun SupportChat(presenter: SupportChatPresenter) {
    val state by presenter.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    DisposableEffect(presenter, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> presenter.enter()
                Lifecycle.Event.ON_STOP -> presenter.leave()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) presenter.enter()
        onDispose { owner.lifecycle.removeObserver(observer); presenter.leave() }
    }

    val list = rememberLazyListState()
    val atBottom by remember { derivedStateOf { !list.canScrollForward } }
    var followNew by remember(state.userId) { mutableStateOf(true) }
    LaunchedEffect(list, state.userId) { snapshotFlow { atBottom }.collect { followNew = it } }
    var previousCount by remember(state.userId) { mutableStateOf(0) }
    LaunchedEffect(state.userId, state.messages.lastOrNull()?.id, state.messages.size) {
        val added = state.messages.size > previousCount
        if (state.messages.isNotEmpty() && (previousCount == 0 || (added && followNew))) list.scrollToItem(state.messages.lastIndex)
        previousCount = state.messages.size
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Icons.Default.SupportAgent, "Chăm sóc khách hàng", Modifier.padding(12.dp).size(28.dp))
            }
            Column {
                Text("Chăm sóc khách hàng", style = MaterialTheme.typography.titleMedium)
                Text(state.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            if (state.messages.any { it.senderRole == SupportSenderRole.USER && it.delivery == SupportDelivery.SENT })
                "Tin nhắn đã được gửi. Chăm sóc khách hàng sẽ phản hồi khi có thể."
            else "Gửi nội dung bạn cần hỗ trợ. Chăm sóc khách hàng sẽ phản hồi khi có thể.",
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        if (state.fromCache) Text("Dữ liệu lưu trên máy. Chưa xác nhận kết nối server; cần mạng để gửi tin.", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.error != null) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.error!!, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = presenter::reconnect) { Text("Kết nối lại") }
        }
        if (state.connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.userId == null) Text("Vui lòng đăng nhập để liên hệ hỗ trợ.", Modifier.padding(20.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("support-messages"),
            state = list,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.connecting && state.messages.isEmpty()) item {
                Text("Chưa có tin nhắn. Bạn cần chúng tôi hỗ trợ điều gì?", style = MaterialTheme.typography.bodyLarge)
            }
            items(state.messages, key = { it.id }) { message ->
                val isUser = message.senderRole == SupportSenderRole.USER
                val imageUriString = remember(message.content) {
                    val match = Regex("\\[image:(.*?)]").find(message.content)
                    match?.groupValues?.get(1)
                }
                val textOnly = remember(message.content) {
                    message.content.replace(Regex("\\[image:.*?]\n?"), "").trim()
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isUser) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.widthIn(max = 300.dp).testTag("support-message-${message.id}")
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (imageUriString != null) {
                                AsyncImage(
                                    model = imageUriString,
                                    contentDescription = "Hình ảnh đính kèm",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            if (textOnly.isNotEmpty()) {
                                Text(textOnly, style = MaterialTheme.typography.bodyLarge)
                            }
                            val time = message.createdAt ?: message.localCreatedAt
                            Text(
                                listOfNotNull(
                                    time?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) },
                                    if (isUser) when (message.delivery) {
                                        SupportDelivery.PENDING -> "Đang gửi"
                                        SupportDelivery.SENT -> "Đã gửi"
                                        SupportDelivery.FAILED -> "Gửi thất bại"
                                    } else null
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (message.delivery == SupportDelivery.FAILED) {
                                TextButton(enabled = !state.sending, onClick = { presenter.retry(message.id) }) {
                                    Text("Thử lại")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Preview thumbnail when image is picked
        if (selectedImageUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(72.dp)) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Ảnh đính kèm xem trước",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { selectedImageUri = null },
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.TopEnd)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Xóa ảnh",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { imagePicker.launch("image/*") },
                enabled = state.userId != null,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Đính kèm ảnh",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = state.input,
                onValueChange = presenter::input,
                modifier = Modifier.weight(1f).testTag("support-chat-input"),
                placeholder = { Text("Tin nhắn") },
                label = { Text("Tin nhắn") },
                maxLines = 4,
                enabled = state.userId != null
            )

            val canSubmit = state.userId != null && !state.sending &&
                (state.input.trim().isNotEmpty() || selectedImageUri != null)

            FilledIconButton(
                onClick = {
                    val currentUri = selectedImageUri
                    if (currentUri != null) {
                        val text = state.input.trim()
                        val combined = if (text.isEmpty()) "[image:$currentUri]" else "[image:$currentUri]\n$text"
                        presenter.input(combined)
                        selectedImageUri = null
                    }
                    presenter.submit()
                },
                enabled = canSubmit,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Gửi tin nhắn")
            }
        }
    }
}

@Composable
fun SupportEmail(presenter: SupportEmailPresenter, openEmail: (SupportEmailDraft) -> Unit) {
    val state by presenter.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picking by remember(state.userId) { mutableStateOf(false) }
    var pickerGeneration by remember { mutableLongStateOf(-1L) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val requestedGeneration = pickerGeneration
        if (uris.isNotEmpty() && requestedGeneration == presenter.generation) scope.launch {
            picking = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    require(uris.size <= SupportContact.MAX_ATTACHMENTS)
                    uris.map { readSupportAttachment(context, it) }
                }
            }
            if (presenter.generation == requestedGeneration) {
                result.fold(
                    onSuccess = presenter::attachments,
                    onFailure = {
                        presenter.error("Không thể dùng tệp đã chọn. Chọn tối đa 3 tệp PDF, JPG, PNG hoặc TXT, mỗi tệp tối đa 10 MB, tổng tối đa 20 MB.")
                    }
                )
            }
            picking = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Soạn yêu cầu hỗ trợ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Bạn sẽ kiểm tra và gửi yêu cầu trong ứng dụng email của mình.", style = MaterialTheme.typography.bodyLarge)

        OutlinedTextField(
            value = state.senderEmail,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().testTag("support-sender-email"),
            readOnly = true,
            label = { Text("Email người gửi") },
            singleLine = true,
            isError = !state.validSender
        )
        if (!state.validSender) {
            Text("Tài khoản chưa có email hợp lệ. Vui lòng cập nhật email tài khoản trước khi liên hệ.", color = MaterialTheme.colorScheme.error)
        }

        OutlinedTextField(
            value = state.subject,
            onValueChange = presenter::subject,
            modifier = Modifier.fillMaxWidth().testTag("support-email-subject"),
            label = { Text("Tiêu đề *") },
            singleLine = true,
            supportingText = { Text("${state.subject.length}/${SupportContact.MAX_SUBJECT}") }
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = presenter::description,
            modifier = Modifier.fillMaxWidth().testTag("support-email-description"),
            label = { Text("Mô tả *") },
            minLines = 4,
            maxLines = 8,
            supportingText = { Text("${state.description.length}/${SupportContact.MAX_DESCRIPTION}") }
        )

        OutlinedButton(
            onClick = {
                try {
                    pickerGeneration = presenter.generation
                    picker.launch(SupportContact.attachmentTypes.toTypedArray())
                } catch (_: android.content.ActivityNotFoundException) {
                    presenter.error("Không tìm thấy ứng dụng chọn tệp.")
                } catch (_: SecurityException) {
                    presenter.error("Không thể mở ứng dụng chọn tệp.")
                }
            },
            enabled = !picking && state.userId != null,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.AttachFile, null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.attachments.isEmpty()) "Chọn tệp" else "Thay tệp")
        }

        Text("Tối đa 3 tệp PDF, JPG, PNG hoặc TXT · 10 MB/tệp · 20 MB tổng.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        state.attachments.forEach { file ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.bodyLarge)
                        Text("${file.mimeType} · ${formatFileSize(file.sizeBytes)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { presenter.remove(file.uri) }) { Icon(Icons.Default.Close, "Xóa tệp ${file.name}") }
                }
            }
        }

        if (picking) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.error != null) Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("support-email-error"))

        Button(
            onClick = { presenter.prepare()?.let(openEmail) },
            enabled = state.validSender && state.userId != null && !picking,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        ) {
            Text("Mở ứng dụng email")
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes byte"
}
