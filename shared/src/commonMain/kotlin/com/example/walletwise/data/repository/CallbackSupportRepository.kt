package com.example.walletwise.data.repository

import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.repository.*
import com.example.walletwise.domain.result.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Adapts Swift callbacks to the existing shared support presenter contract. */
class CallbackSupportRepository(
    private val service: CallbackSupportService?
) : SupportRepository {
    override fun observeMessages(userId: String) = callbackFlow<RepositoryResult<SupportSnapshot>> {
        if (service == null || userId.isBlank()) {
            trySend(SupportFailure.NOT_AUTHENTICATED.result())
            awaitClose { }
            return@callbackFlow
        }
        var active = true
        val cancellation = service.observeMessages(userId, object : SupportSnapshotObserver {
            override fun supportChanged(
                documents: List<SupportMessageDocument>?,
                fromCache: Boolean,
                failure: SupportFailure?
            ) {
                if (!active) return
                if (failure != null || documents == null) {
                    trySend((failure ?: SupportFailure.UNKNOWN).result())
                    return
                }
                val messages = documents.mapNotNull { it.toMessage(userId) }
                    .distinctBy { it.id }
                    .sortedWith(compareBy<SupportMessage> { it.createdAt ?: Long.MAX_VALUE }.thenBy { it.id })
                trySend(RepositoryResult.Success(SupportSnapshot(messages, fromCache)))
            }
        })
        awaitClose { active = false; cancellation.cancel() }
    }.buffer(Channel.CONFLATED)

    override suspend fun send(session: AuthSession, message: SupportMessage): RepositoryResult<Unit> {
        val gateway = service ?: return SupportFailure.NOT_AUTHENTICATED.result()
        if (!message.isValidOutgoing(session)) return SupportFailure.INVALID_DATA.result()
        return suspendCancellableCoroutine { continuation ->
            val cancellation = gateway.send(session, message, object : SupportWriteCompletion {
                override fun supportCompleted(failure: SupportFailure?) {
                    if (continuation.isActive) continuation.resume(
                        if (failure == null) RepositoryResult.Success(Unit) else failure.result()
                    )
                }
            })
            continuation.invokeOnCancellation { cancellation.cancel() }
        }
    }
}

private val legacyImage = Regex("\\[image:(.*?)]")

private fun SupportMessageDocument.toMessage(ownerUserId: String): SupportMessage? {
    if (documentId.isBlank() || '/' in documentId || content.isBlank() || content.length > SupportContact.MAX_MESSAGE) return null
    val role = when (senderRole) {
        "user" -> SupportSenderRole.USER
        "staff", "agent" -> SupportSenderRole.AGENT
        else -> return null
    }
    if (role == SupportSenderRole.USER && senderId != ownerUserId) return null
    val legacyUrl = legacyImage.find(content)?.groupValues?.getOrNull(1)
    val resolvedUrl = imageUrl ?: legacyUrl
    val type = if (messageType == "image" || resolvedUrl != null) SupportMessageType.IMAGE else SupportMessageType.TEXT
    return SupportMessage(
        id = documentId,
        senderId = senderId,
        senderRole = role,
        content = content,
        createdAt = createdAtMilliseconds.takeIf { hasCreatedAt },
        clientRequestId = clientRequestId.ifBlank { documentId },
        delivery = if (pendingWrites) SupportDelivery.PENDING else SupportDelivery.SENT,
        messageType = type,
        imageUrl = resolvedUrl,
        mimeType = mimeType,
        fileName = fileName
    )
}

private fun SupportMessage.isValidOutgoing(session: AuthSession): Boolean {
    if (senderId != session.userId || senderRole != SupportSenderRole.USER || id.isBlank() || '/' in id ||
        clientRequestId != id || content.isBlank() || content.length > SupportContact.MAX_MESSAGE ||
        listOf("content://", "file://", "blob:").any(content::contains)) return false
    return when (messageType) {
        SupportMessageType.TEXT -> listOf(imageUrl, mimeType, fileName).all { it == null }
        SupportMessageType.IMAGE -> {
            val url = imageUrl ?: return false
            val type = mimeType ?: return false
            val name = fileName ?: return false
            SupportImagePayload(url, type, name).isValid()
        }
    }
}

private fun SupportFailure.result(): RepositoryResult.Failure = RepositoryResult.Failure(
    RepositoryError(
        when (this) {
            SupportFailure.NOT_AUTHENTICATED -> RepositoryErrorCode.NOT_AUTHENTICATED
            SupportFailure.NETWORK -> RepositoryErrorCode.NETWORK
            SupportFailure.PERMISSION_DENIED -> RepositoryErrorCode.PERMISSION_DENIED
            SupportFailure.INVALID_DATA -> RepositoryErrorCode.UNKNOWN
            SupportFailure.UNKNOWN -> RepositoryErrorCode.UNKNOWN
        },
        when (this) {
            SupportFailure.NOT_AUTHENTICATED -> "Vui lòng đăng nhập để liên hệ hỗ trợ."
            SupportFailure.NETWORK -> "Không thể kết nối dịch vụ hỗ trợ. Vui lòng thử lại."
            SupportFailure.PERMISSION_DENIED -> "Bạn không có quyền truy cập cuộc trò chuyện này."
            SupportFailure.INVALID_DATA -> "Tin nhắn hỗ trợ không hợp lệ."
            SupportFailure.UNKNOWN -> "Không thể hoàn tất yêu cầu hỗ trợ."
        }
    )
)
