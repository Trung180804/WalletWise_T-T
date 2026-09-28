package com.example.walletwise.domain.repository

import com.example.walletwise.domain.model.AuthSession
import com.example.walletwise.domain.model.SupportMessage

enum class SupportFailure { NOT_AUTHENTICATED, NETWORK, PERMISSION_DENIED, INVALID_DATA, UNKNOWN }

interface SupportCancellation { fun cancel() }

data class SupportMessageDocument(
    val documentId: String,
    val senderId: String,
    val senderRole: String,
    val content: String,
    val createdAtMilliseconds: Long,
    val hasCreatedAt: Boolean,
    val clientRequestId: String,
    val pendingWrites: Boolean,
    val messageType: String,
    val imageUrl: String?,
    val mimeType: String?,
    val fileName: String?
)

interface SupportSnapshotObserver {
    /** Null documents represent an error, not an empty conversation. */
    fun supportChanged(documents: List<SupportMessageDocument>?, fromCache: Boolean, failure: SupportFailure?)
}

interface SupportWriteCompletion { fun supportCompleted(failure: SupportFailure?) }

/** UI-thread callback boundary used by the native Apple Firebase adapter. */
interface CallbackSupportService {
    fun observeMessages(userId: String, observer: SupportSnapshotObserver): SupportCancellation
    fun send(session: AuthSession, message: SupportMessage, completion: SupportWriteCompletion): SupportCancellation
}
