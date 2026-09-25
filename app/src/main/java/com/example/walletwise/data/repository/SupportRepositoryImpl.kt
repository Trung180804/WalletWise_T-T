package com.example.walletwise.data.repository

import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.repository.SupportRepository
import com.example.walletwise.domain.result.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

class SupportRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : SupportRepository {
    private val listenerCount = AtomicInteger()
    private val snapshotCount = AtomicInteger()
    internal val snapshotEventCount: Int get() = snapshotCount.get()
    internal val activeListenerCount: Int get() = listenerCount.get()
    private fun conversation(uid: String) = firestore.collection("supportConversations").document(uid)
    private val legacyImage = Regex("\\[image:(.*?)]")

    override fun observeMessages(userId: String) = callbackFlow<RepositoryResult<SupportSnapshot>> {
        if (auth.currentUser?.uid != userId) {
            trySend(RepositoryResult.Failure(RepositoryError(RepositoryErrorCode.NOT_AUTHENTICATED, "Vui lòng đăng nhập để liên hệ hỗ trợ.")))
            close(); return@callbackFlow
        }
        val registration = conversation(userId).collection("messages").orderBy("createdAt")
            .limitToLast(100).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (auth.currentUser?.uid != userId) return@addSnapshotListener
                if (error != null) {
                    trySend(RepositoryResult.Failure(error.toRepositoryError("Không thể tải tin nhắn. Vui lòng thử kết nối lại.")))
                } else if (snapshot != null) {
                    snapshotCount.incrementAndGet()
                    val messages = snapshot.documents.mapNotNull { document ->
                        val role = when (document.getString("senderRole")) {
                            "user" -> SupportSenderRole.USER
                            "staff", "agent" -> SupportSenderRole.AGENT
                            else -> return@mapNotNull null
                        }
                        val sender = document.getString("senderId") ?: return@mapNotNull null
                        if (role == SupportSenderRole.USER && sender != userId) return@mapNotNull null
                        val content = document.getString("content") ?: return@mapNotNull null
                        val explicitImage = document.getString("messageType") == "image"
                        val structuredImageUrl = document.getString("imageUrl")
                        val legacyImageUrl = legacyImage.find(content)?.groupValues?.getOrNull(1)
                        val messageType = if (explicitImage || structuredImageUrl != null || legacyImageUrl != null) {
                            SupportMessageType.IMAGE
                        } else {
                            SupportMessageType.TEXT
                        }
                        SupportMessage(document.id, sender, role, content,
                            createdAt = document.getTimestamp("createdAt")?.toDate()?.time,
                            clientRequestId = document.getString("clientRequestId") ?: document.id,
                            delivery = if (document.metadata.hasPendingWrites()) SupportDelivery.PENDING else SupportDelivery.SENT,
                            messageType = messageType,
                            imageUrl = structuredImageUrl ?: legacyImageUrl,
                            mimeType = document.getString("mimeType"),
                            fileName = document.getString("fileName"))
                    }
                    trySend(RepositoryResult.Success(SupportSnapshot(messages, snapshot.metadata.isFromCache)))
                }
            }
        listenerCount.incrementAndGet()
        awaitClose { registration.remove(); listenerCount.decrementAndGet() }
    }

    override suspend fun send(session: AuthSession, message: SupportMessage): RepositoryResult<Unit> {
        val imagePayload = if (message.messageType == SupportMessageType.IMAGE) {
            val imageUrl = message.imageUrl
            val mimeType = message.mimeType
            val fileName = message.fileName
            if (imageUrl == null || mimeType == null || fileName == null) null
            else SupportImagePayload(imageUrl, mimeType, fileName)
        } else null
        if (auth.currentUser?.uid != session.userId || message.senderId != session.userId ||
            message.senderRole != SupportSenderRole.USER || message.id.isBlank() || '/' in message.id ||
            message.clientRequestId != message.id || message.content.trim().isEmpty() ||
            message.content.length > SupportContact.MAX_MESSAGE ||
            listOf("content://", "file://", "blob:").any(message.content::contains) ||
            (message.messageType == SupportMessageType.IMAGE && imagePayload?.isValid() != true) ||
            (message.messageType == SupportMessageType.TEXT &&
                listOf(message.imageUrl, message.mimeType, message.fileName).any { it != null })) {
            return RepositoryResult.Failure(RepositoryError(RepositoryErrorCode.NOT_AUTHENTICATED, "Phiên hoặc tin nhắn không hợp lệ."))
        }
        return try {
            withTimeout(15_000) {
                val parentRef = conversation(session.userId)
                val messageRef = parentRef.collection("messages").document(message.id)
                firestore.runTransaction { transaction ->
                    check(auth.currentUser?.uid == session.userId)
                    val existing = transaction.get(messageRef)
                    if (!existing.exists()) {
                        val parent = transaction.get(parentRef)
                        check(!parent.exists() || parent.getString("status") != "closed")
                        val fields = mutableMapOf<String, Any>(
                            "userId" to session.userId, "userEmail" to auth.currentUser?.email?.trim().orEmpty(),
                            "status" to "waiting_staff", "updatedAt" to FieldValue.serverTimestamp(),
                            "lastMessagePreview" to if (message.messageType == SupportMessageType.IMAGE) "[Hình ảnh]"
                                else message.content.trim().take(160),
                            "lastSenderRole" to "user"
                        )
                        if (!parent.exists()) fields["createdAt"] = FieldValue.serverTimestamp()
                        transaction.set(parentRef, fields, SetOptions.merge())
                        val messageFields = mutableMapOf<String, Any>(
                            "id" to message.id, "senderId" to session.userId, "senderRole" to "user",
                            "content" to message.content.trim(), "createdAt" to FieldValue.serverTimestamp(),
                            "clientRequestId" to message.clientRequestId, "status" to "sent",
                            "messageType" to if (message.messageType == SupportMessageType.IMAGE) "image" else "text"
                        )
                        if (imagePayload != null) {
                            messageFields["imageUrl"] = imagePayload.imageUrl
                            messageFields["mimeType"] = imagePayload.mimeType
                            messageFields["fileName"] = imagePayload.fileName
                        }
                        transaction.set(messageRef, messageFields)
                    } else {
                        check(existing.getString("senderId") == session.userId &&
                            existing.getString("senderRole") == "user" &&
                            existing.getString("clientRequestId") == message.clientRequestId)
                    }
                    Unit
                }.await()
            }
            RepositoryResult.Success(Unit)
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            RepositoryResult.Failure(RepositoryError(RepositoryErrorCode.NETWORK, "Không thể gửi khi mất kết nối. Vui lòng thử lại."))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            RepositoryResult.Failure(error.toRepositoryError("Không thể gửi tin nhắn. Vui lòng thử lại."))
        }
    }
}
