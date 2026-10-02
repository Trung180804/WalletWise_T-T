package com.example.walletwise.presentation.support

import com.example.walletwise.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SupportEmailDraft(val recipient: String, val subject: String, val body: String, val attachments: List<SupportAttachment>)

data class SupportEmailState(
    val userId: String? = null,
    val senderEmail: String = "",
    val subject: String = "",
    val description: String = "",
    val attachments: List<SupportAttachment> = emptyList(),
    val error: String? = null
) {
    val validSender: Boolean get() = SupportEmailValidation.validEmail(senderEmail)
    val canOpenEmail: Boolean get() = userId != null && validSender &&
        subject.trim().isNotEmpty() && description.trim().isNotEmpty()
}

object SupportEmailValidation {
    fun validEmail(email: String): Boolean = email.length <= 254 &&
        Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)

    fun error(state: SupportEmailState): String? = when {
        state.userId == null || !state.validSender -> "Tài khoản chưa có email hợp lệ. Vui lòng cập nhật email tài khoản trước khi liên hệ."
        state.subject.trim().isEmpty() -> "Vui lòng nhập tiêu đề."
        state.subject.length > SupportContact.MAX_SUBJECT -> "Tiêu đề tối đa ${SupportContact.MAX_SUBJECT} ký tự."
        state.subject.any { it == '\n' || it == '\r' } -> "Tiêu đề cần nằm trên một dòng."
        state.description.trim().isEmpty() -> "Vui lòng nhập mô tả."
        state.description.length > SupportContact.MAX_DESCRIPTION -> "Mô tả tối đa ${SupportContact.MAX_DESCRIPTION} ký tự."
        else -> attachmentError(state.attachments)
    }

    fun attachmentError(files: List<SupportAttachment>): String? = when {
        files.size > SupportContact.MAX_ATTACHMENTS -> "Chọn tối đa 3 tệp."
        files.any { !it.uri.startsWith("content://") || it.name.isBlank() || it.sizeBytes <= 0 || it.sizeBytes > SupportContact.MAX_FILE_BYTES || it.mimeType !in SupportContact.attachmentTypes } ->
            "Tệp phải là PDF, JPG, PNG hoặc TXT, có dung lượng từ 1 byte đến 10 MB."
        files.sumOf { it.sizeBytes } > SupportContact.MAX_TOTAL_BYTES -> "Tổng dung lượng tệp tối đa 20 MB."
        else -> null
    }
}

class SupportEmailPresenter {
    var generation: Long = 0
        private set
    private val mutable = MutableStateFlow(SupportEmailState())
    val state = mutable.asStateFlow()

    fun bind(session: AuthSession?) {
        val uid = session?.userId?.takeIf { it.isNotBlank() }
        val email = session?.email?.trim().orEmpty()
        if (mutable.value.userId != uid) generation++
        mutable.value = if (mutable.value.userId != uid) SupportEmailState(uid, email)
        else mutable.value.copy(senderEmail = email, error = null)
    }

    fun subject(value: String) { mutable.value = mutable.value.copy(subject = value.take(SupportContact.MAX_SUBJECT), error = null) }
    fun description(value: String) { mutable.value = mutable.value.copy(description = value.take(SupportContact.MAX_DESCRIPTION), error = null) }
    fun attachments(files: List<SupportAttachment>) {
        val unique = files.distinctBy { it.uri }
        val error = SupportEmailValidation.attachmentError(unique)
        mutable.value = if (error == null) mutable.value.copy(attachments = unique, error = null)
        else mutable.value.copy(error = error)
    }
    fun remove(uri: String) { mutable.value = mutable.value.copy(attachments = mutable.value.attachments.filterNot { it.uri == uri }, error = null) }
    fun error(message: String) { mutable.value = mutable.value.copy(error = message) }
    fun prepare(): SupportEmailDraft? {
        val current = mutable.value
        val error = SupportEmailValidation.error(current)
        if (error != null) { mutable.value = current.copy(error = error); return null }
        return SupportEmailDraft(SupportContact.EMAIL, current.subject.trim(),
            "Email tài khoản: ${current.senderEmail}\n\n${current.description.trim()}", current.attachments)
    }
    fun clear() { generation++; mutable.value = SupportEmailState() }
}
