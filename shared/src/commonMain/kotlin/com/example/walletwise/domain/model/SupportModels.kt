package com.example.walletwise.domain.model

enum class SupportDestination { HUB, CHAT, EMAIL }
enum class SupportSenderRole { USER, AGENT }
enum class SupportDelivery { PENDING, SENT, FAILED }
enum class SupportMessageType { TEXT, IMAGE }

object SupportContact {
    const val PHONE = "0942135300"
    const val EMAIL = "atrungvlogs1808@gmail.com"
    const val MAX_MESSAGE = 4000
    const val MAX_SUPPORT_IMAGE_SOURCE_BYTES = 12L * 1024 * 1024
    const val MAX_SUPPORT_IMAGE_UPLOAD_BYTES = 512 * 1024
    const val MAX_SUPPORT_IMAGE_FILE_NAME = 128
    const val MAX_SUBJECT = 160
    const val MAX_DESCRIPTION = 8000
    const val MAX_ATTACHMENTS = 3
    const val MAX_FILE_BYTES = 10L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 20L * 1024 * 1024
    val attachmentTypes = listOf("application/pdf", "image/jpeg", "image/png", "text/plain")
}

data class SupportImagePayload(
    val imageUrl: String,
    val mimeType: String,
    val fileName: String
) {
    fun isValid(): Boolean =
        SupportImageUrlPolicy.isPortable(imageUrl) &&
            mimeType in setOf("image/jpeg", "image/png", "image/webp") &&
            fileName.isNotBlank() && fileName.length <= SupportContact.MAX_SUPPORT_IMAGE_FILE_NAME &&
            '/' !in fileName && '\\' !in fileName
}

/** Pure KMP policy. Platform URL loaders still perform their own URL parsing and transport checks. */
object SupportImageUrlPolicy {
    private val uploadPath = Regex("^/uploads/[A-Za-z0-9._-]{1,180}$")

    fun isRelativeUploadPath(value: String?): Boolean {
        val candidate = value ?: return false
        if (candidate != candidate.trim() || !candidate.matches(uploadPath)) return false
        val fileName = candidate.substringAfterLast('/')
        return fileName != "." && fileName != ".." && fileName.any(Char::isLetterOrDigit)
    }

    fun isPortable(value: String?): Boolean {
        val candidate = value.orEmpty()
        if (candidate != candidate.trim()) return false
        return isRelativeUploadPath(candidate) || isSafeHttps(candidate)
    }

    fun resolve(value: String?, baseUrl: String, allowLocalHttp: Boolean = false): String? {
        val candidate = value.orEmpty()
        if (candidate != candidate.trim()) return null
        if (isSafeHttps(candidate)) return candidate
        if (!isRelativeUploadPath(candidate)) return null
        val origin = baseUrl.trim().trimEnd('/')
        val validOrigin = isSafeHttps(origin) || (allowLocalHttp && isSafeHttpOrigin(origin))
        return if (validOrigin) "$origin$candidate" else null
    }

    private fun isSafeHttps(value: String): Boolean {
        if (!value.startsWith("https://") || value.length !in 9..2048 ||
            value.any { it.isWhitespace() || it.code < 0x20 } || '\\' in value) return false
        val remainder = value.removePrefix("https://")
        val authority = remainder.substringBefore('/').substringBefore('?').substringBefore('#')
        return isSafeAuthority(authority)
    }

    private fun isSafeHttpOrigin(value: String): Boolean {
        if (!value.startsWith("http://") || value.length !in 8..256 ||
            value.any { it.isWhitespace() || it.code < 0x20 } || '\\' in value) return false
        val authority = value.removePrefix("http://")
        return '/' !in authority && '?' !in authority && '#' !in authority && isSafeAuthority(authority)
    }

    private fun isSafeAuthority(authority: String): Boolean {
        if (authority.isBlank() || '@' in authority || authority.startsWith('.') ||
            authority.endsWith('.') || authority.endsWith(':')) return false
        if (authority.count { it == ':' } > 1) return false
        val host = authority.substringBefore(':')
        val port = authority.substringAfter(':', "")
        if (port.isNotEmpty() && (port.toIntOrNull() ?: 0) !in 1..65535) return false
        return host.isNotBlank() && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
    }
}

object SupportMessagePresentation {
    private val legacyImage = Regex("\\[image:(.*?)]")

    fun imageReference(message: SupportMessage): String? =
        message.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: legacyImage.find(message.content)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    fun text(message: SupportMessage): String = message.content.replace(legacyImage, "").trim()

    fun preview(message: SupportMessage): String =
        if (message.messageType == SupportMessageType.IMAGE || imageReference(message) != null) "[Hình ảnh]"
        else text(message).take(160)
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
    val localCreatedAt: Long? = null,
    val messageType: SupportMessageType = SupportMessageType.TEXT,
    val imageUrl: String? = null,
    val mimeType: String? = null,
    val fileName: String? = null
)

data class SupportSnapshot(val messages: List<SupportMessage>, val fromCache: Boolean = false)

data class SupportAttachment(val uri: String, val name: String, val mimeType: String, val sizeBytes: Long)
