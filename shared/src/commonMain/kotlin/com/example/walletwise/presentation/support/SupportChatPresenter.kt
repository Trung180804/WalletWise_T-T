package com.example.walletwise.presentation.support

import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.repository.SupportRepository
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SupportChatState(
    val userId: String? = null,
    val messages: List<SupportMessage> = emptyList(),
    val input: String = "",
    val connecting: Boolean = false,
    val sending: Boolean = false,
    val fromCache: Boolean = false,
    val error: String? = null
) {
    val status: String get() = when {
        connecting -> "Đang kết nối"
        sending || messages.any { it.delivery == SupportDelivery.PENDING } -> "Đang gửi"
        messages.any { it.delivery == SupportDelivery.FAILED } -> "Gửi thất bại – Thử lại"
        messages.any { it.senderRole == SupportSenderRole.AGENT } -> "Nhân viên đã phản hồi"
        messages.any { it.delivery == SupportDelivery.SENT } -> "Đã gửi"
        else -> "Chưa có nhân viên phản hồi"
    }
}

/** One screen listener; generations reject callbacks and writes from a previous account. */
class SupportChatPresenter(
    private val scope: CoroutineScope,
    private val repository: SupportRepository,
    private val newId: () -> String,
    private val now: () -> Long
) {
    private val mutable = MutableStateFlow(SupportChatState())
    val state = mutable.asStateFlow()
    private var session: AuthSession? = null
    private var visible = false
    private var generation = 0L
    private var listener: Job? = null
    private var sendJob: Job? = null

    fun bind(next: AuthSession?) {
        if (session?.userId == next?.userId) { session = next; return }
        stop()
        session = next
        mutable.value = SupportChatState(userId = next?.userId)
        if (visible) listen()
    }

    fun enter() { if (!visible) { visible = true; listen() } }
    fun leave() {
        visible = false
        stop()
        mutable.value = mutable.value.copy(connecting = false, sending = false,
            messages = mutable.value.messages.map { if (it.delivery == SupportDelivery.PENDING) it.copy(delivery = SupportDelivery.FAILED) else it })
    }
    fun reconnect() { if (visible) { listener?.cancel(); listener = null; listen() } }
    fun input(value: String) { mutable.value = mutable.value.copy(input = value.take(SupportContact.MAX_MESSAGE), error = null) }

    private fun listen() {
        val uid = session?.userId ?: return
        if (listener?.isActive == true) return
        val version = generation
        mutable.value = mutable.value.copy(connecting = true, error = null)
        listener = scope.launch {
            try {
                repository.observeMessages(uid).collect { result ->
                    if (version != generation || session?.userId != uid || !visible) return@collect
                    when (result) {
                        is RepositoryResult.Failure -> mutable.value = mutable.value.copy(connecting = false, error = result.error.message)
                        is RepositoryResult.Success -> {
                            val remote = result.value.messages.distinctBy { it.id }
                            val remoteIds = remote.map { it.id }.toSet()
                            val unresolved = mutable.value.messages.filter { it.delivery != SupportDelivery.SENT && it.id !in remoteIds }
                            mutable.value = mutable.value.copy(messages = sorted(remote + unresolved), connecting = false,
                                fromCache = result.value.fromCache, error = null)
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (version == generation && visible) mutable.value = mutable.value.copy(connecting = false, error = "Không thể tải tin nhắn. Vui lòng thử kết nối lại.")
            }
        }
    }

    fun submit() {
        val currentSession = session ?: return
        val content = mutable.value.input.trim()
        if (!visible || mutable.value.sending) return
        if (content.isEmpty()) { mutable.value = mutable.value.copy(error = "Vui lòng nhập tin nhắn."); return }
        val message = SupportMessage(newId(), currentSession.userId, SupportSenderRole.USER, content,
            delivery = SupportDelivery.PENDING, localCreatedAt = now(), messageType = SupportMessageType.TEXT)
        enqueue(currentSession, message)
    }

    /** Receives only an already-uploaded, cross-device image reference from the Android boundary. */
    fun submitImage(image: SupportImagePayload): Boolean {
        val currentSession = session ?: return false
        if (!visible || mutable.value.sending) return false
        if (!image.isValid()) {
            mutable.value = mutable.value.copy(error = "Ảnh chưa được tải lên an toàn. Vui lòng thử lại.")
            return false
        }
        val caption = mutable.value.input.trim()
        val marker = "[image:${image.imageUrl}]"
        val content = if (caption.isEmpty()) marker else "$marker\n$caption"
        if (content.length > SupportContact.MAX_MESSAGE) {
            mutable.value = mutable.value.copy(error = "Tin nhắn kèm ảnh quá dài.")
            return false
        }
        val message = SupportMessage(
            id = newId(),
            senderId = currentSession.userId,
            senderRole = SupportSenderRole.USER,
            content = content,
            delivery = SupportDelivery.PENDING,
            localCreatedAt = now(),
            messageType = SupportMessageType.IMAGE,
            imageUrl = image.imageUrl,
            mimeType = image.mimeType,
            fileName = image.fileName
        )
        enqueue(currentSession, message)
        return true
    }

    private fun enqueue(currentSession: AuthSession, message: SupportMessage) {
        mutable.value = mutable.value.copy(messages = sorted(mutable.value.messages + message), input = "", sending = true, error = null)
        send(currentSession, message)
    }

    fun retry(id: String) {
        val currentSession = session ?: return
        if (!visible || mutable.value.sending) return
        val message = mutable.value.messages.firstOrNull { it.id == id && it.delivery == SupportDelivery.FAILED } ?: return
        mutable.value = mutable.value.copy(sending = true, error = null,
            messages = mutable.value.messages.map { if (it.id == id) it.copy(delivery = SupportDelivery.PENDING) else it })
        send(currentSession, message)
    }

    private fun send(account: AuthSession, message: SupportMessage) {
        val version = generation
        sendJob = scope.launch {
            val result = try { repository.send(account, message) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (version != generation || session?.userId != account.userId) return@launch
            val failed = result !is RepositoryResult.Success
            // Only a server snapshot supplies createdAt. Acknowledgement supplies delivery, never read receipt.
            mutable.value = mutable.value.copy(sending = false,
                messages = mutable.value.messages.map {
                    if (it.id == message.id && it.createdAt == null) it.copy(delivery = if (failed) SupportDelivery.FAILED else SupportDelivery.SENT) else it
                }, error = if (failed) "Gửi thất bại. Kiểm tra kết nối và nhấn Thử lại tại tin nhắn." else null)
        }
    }

    private fun sorted(messages: List<SupportMessage>) = messages.sortedWith(
        compareBy<SupportMessage> { it.createdAt ?: it.localCreatedAt ?: Long.MAX_VALUE }.thenBy { it.id })
    private fun stop() { generation++; listener?.cancel(); listener = null; sendJob?.cancel(); sendJob = null }
    fun close() { visible = false; stop(); session = null; mutable.value = SupportChatState() }
}
