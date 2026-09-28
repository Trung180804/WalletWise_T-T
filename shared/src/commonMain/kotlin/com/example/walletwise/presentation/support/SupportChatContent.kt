package com.example.walletwise.presentation.support

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.walletwise.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportChatContent(
    presenter: SupportChatPresenter,
    images: SupportImagePickerPresenter,
    onBack: () -> Unit,
    timeFormatter: (Long) -> String = { "" },
    imageContent: (@Composable (String, Modifier) -> Unit)? = null
) {
    val state by presenter.state.collectAsState()
    val pickerState by images.state.collectAsState()
    DisposableEffect(presenter) {
        presenter.enter()
        onDispose { presenter.leave(); images.clear() }
    }

    val list = rememberLazyListState()
    var previousCount by remember(state.userId) { mutableStateOf(0) }
    LaunchedEffect(state.userId, state.messages.lastOrNull()?.id, state.messages.size) {
        if (state.messages.isNotEmpty() && (previousCount == 0 || !list.canScrollForward)) {
            list.scrollToItem(state.messages.lastIndex)
        }
        previousCount = state.messages.size
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chăm sóc khách hàng", fontWeight = FontWeight.SemiBold)
                        Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.SupportAgent, "Chăm sóc khách hàng", tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Gửi nội dung bạn cần hỗ trợ. Nhân viên sẽ phản hồi khi có thể.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (state.fromCache) {
                Text(
                    "Đang hiển thị dữ liệu lưu trên máy.",
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            state.error?.let { error ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = presenter::reconnect) { Text("Kết nối lại") }
                }
            }
            if (state.connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.userId == null) {
                Text("Vui lòng đăng nhập để liên hệ hỗ trợ.", Modifier.padding(16.dp))
            }

            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!state.connecting && state.messages.isEmpty()) item {
                    Text("Chưa có tin nhắn. Bạn cần chúng tôi hỗ trợ điều gì?")
                }
                items(state.messages, key = { it.id }) { message ->
                    SupportMessageBubble(message, state.sending, presenter::retry, timeFormatter, imageContent)
                }
            }

            pickerState.image?.let { image ->
                ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(image.fileName, fontWeight = FontWeight.SemiBold)
                            Text(
                                "JPEG · ${image.pixelWidth}×${image.pixelHeight} · ${formatSupportBytes(image.byteCount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = images::clear) { Icon(Icons.Default.Close, "Xóa ảnh đã chọn") }
                    }
                }
            }
            if (pickerState.selecting) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            pickerState.error?.takeIf { it.isNotBlank() }?.let {
                Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = images::pick,
                    enabled = state.userId != null && !pickerState.selecting,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.AttachFile, "Đính kèm ảnh", tint = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(
                    value = state.input,
                    onValueChange = presenter::input,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Tin nhắn") },
                    maxLines = 4,
                    enabled = state.userId != null
                )
                val image = pickerState.image
                val canSubmit = state.userId != null && !state.sending && !pickerState.selecting &&
                    (state.input.trim().isNotEmpty() || (image != null && image.uploadUrl.isNotBlank()))

                FilledIconButton(
                    onClick = {
                        val image = pickerState.image
                        if (image != null && image.uploadUrl.isNotBlank()) {
                            if (presenter.submitImage(SupportImagePayload(image.uploadUrl, image.mimeType, image.fileName))) {
                                images.clear()
                            }
                        } else {
                            presenter.submit()
                        }
                    },
                    enabled = canSubmit,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Gửi tin nhắn")
                }
            }
        }
    }
}

@Composable
private fun SupportMessageBubble(
    message: SupportMessage,
    sending: Boolean,
    retry: (String) -> Unit,
    timeFormatter: (Long) -> String,
    imageContent: (@Composable (String, Modifier) -> Unit)?
) {
    val isUser = message.senderRole == SupportSenderRole.USER
    val imageReference = remember(message.content, message.imageUrl) { SupportMessagePresentation.imageReference(message) }
    val text = remember(message.content) { SupportMessagePresentation.text(message) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isUser) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (message.messageType == SupportMessageType.IMAGE || imageReference != null) {
                    if (imageReference != null && SupportImageUrlPolicy.isPortable(imageReference) && imageContent != null) {
                        imageContent(
                            imageReference,
                            Modifier.fillMaxWidth().heightIn(min = 88.dp, max = 220.dp).clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        UnavailableSupportImage()
                    }
                }
                if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyLarge)
                val time = (message.createdAt ?: message.localCreatedAt)?.let(timeFormatter).orEmpty()
                val status = if (isUser) when (message.delivery) {
                    SupportDelivery.PENDING -> "Đang gửi"
                    SupportDelivery.SENT -> "Đã gửi"
                    SupportDelivery.FAILED -> "Gửi thất bại"
                } else ""
                Text(
                    listOf(time, status).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (message.delivery == SupportDelivery.FAILED) {
                    TextButton(enabled = !sending, onClick = { retry(message.id) }) { Text("Thử lại") }
                }
            }
        }
    }
}

@Composable
private fun UnavailableSupportImage() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).semantics { contentDescription = "Ảnh không khả dụng" }
    ) {
        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text("Ảnh không khả dụng", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatSupportBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes byte"
}
