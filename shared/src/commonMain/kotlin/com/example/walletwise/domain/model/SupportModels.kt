package com.example.walletwise.domain.model

enum class SupportDestination { HUB, CHAT, EMAIL }
enum class SupportSenderRole { USER, AGENT }
enum class SupportDelivery { PENDING, SENT, FAILED }

object SupportContact {
    const val PHONE = "0942135300"
    const val EMAIL = "atrungvlogs1808@gmail.com"
    const val MAX_MESSAGE = 4000
    const val MAX_SUBJECT = 160
    const val MAX_DESCRIPTION = 8000
    const val MAX_ATTACHMENTS = 3
    const val MAX_FILE_BYTES = 10L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 20L * 1024 * 1024
    val attachmentTypes = listOf("application/pdf", "image/jpeg", "image/png", "text/plain")
}

data class SupportConversation(
    val userId: String,
    val userEmail: String,
    val status: String = "open",
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val lastMessagePreview: String = "",
    val lastSenderRole: String = "user"
)

data class SupportMessage(
    val id: String,
    val senderId: String,
    val senderRole: SupportSenderRole,
    val content: String,
    val createdAt: Long? = null,
    val clientRequestId: String = id,
    val delivery: SupportDelivery = SupportDelivery.SENT,
    val localCreatedAt: Long? = null
)

data class SupportSnapshot(val messages: List<SupportMessage>, val fromCache: Boolean = false)

data class SupportAttachment(val uri: String, val name: String, val mimeType: String, val sizeBytes: Long)
