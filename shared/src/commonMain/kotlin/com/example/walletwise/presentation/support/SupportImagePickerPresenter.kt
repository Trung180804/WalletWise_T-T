package com.example.walletwise.presentation.support

import com.example.walletwise.domain.repository.SupportCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PreparedSupportImage(
    val selectionId: String,
    val fileName: String,
    val mimeType: String,
    val byteCount: Long,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val uploadUrl: String = ""
)

enum class SupportImagePickFailure { CANCELLED, BUSY, UNAVAILABLE, INVALID_IMAGE, TOO_LARGE, UNKNOWN }

interface SupportImagePickCompletion {
    fun imagePicked(image: PreparedSupportImage?, failure: SupportImagePickFailure?)
}

interface CallbackSupportImagePicker {
    fun pick(selectionId: String, completion: SupportImagePickCompletion): SupportCancellation
}

data class SupportImagePickerState(
    val selecting: Boolean = false,
    val image: PreparedSupportImage? = null,
    val error: String? = null
)

/** Native bytes stay native. This presenter owns only bounded metadata and picker lifecycle. */
class SupportImagePickerPresenter(
    private val picker: CallbackSupportImagePicker?,
    private val newId: () -> String
) {
    private val mutable = MutableStateFlow(SupportImagePickerState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var request: SupportCancellation? = null

    fun pick() {
        if (mutable.value.selecting) return
        val gateway = picker ?: run {
            mutable.value = SupportImagePickerState(error = "Bộ chọn ảnh chưa khả dụng trên thiết bị này.")
            return
        }
        val version = ++generation
        val selectionId = newId()
        mutable.value = SupportImagePickerState(selecting = true)
        var completedSynchronously = false
        val cancellation = gateway.pick(selectionId, object : SupportImagePickCompletion {
            override fun imagePicked(image: PreparedSupportImage?, failure: SupportImagePickFailure?) {
                completedSynchronously = true
                if (version != generation) return
                request?.cancel()
                request = null
                mutable.value = when {
                    failure == SupportImagePickFailure.CANCELLED -> SupportImagePickerState()
                    image != null && failure == null -> SupportImagePickerState(
                        image = image,
                        error = if (image.uploadUrl.isBlank()) "Không thể kết nối máy chủ để tải ảnh lên." else null
                    )
                    else -> SupportImagePickerState(error = failure.message())
                }
            }
        })
        if (completedSynchronously || version != generation) cancellation.cancel() else request = cancellation
    }

    fun clear() {
        generation++
        request?.cancel()
        request = null
        mutable.value = SupportImagePickerState()
    }

    fun close() = clear()
}

private fun SupportImagePickFailure?.message(): String = when (this) {
    SupportImagePickFailure.BUSY -> "Bộ chọn ảnh đang mở."
    SupportImagePickFailure.UNAVAILABLE -> "Không thể mở thư viện ảnh trên thiết bị này."
    SupportImagePickFailure.INVALID_IMAGE -> "Tệp đã chọn không phải ảnh JPEG, PNG hoặc WebP hợp lệ."
    SupportImagePickFailure.TOO_LARGE -> "Ảnh không thể chuẩn hóa xuống dưới 512 KB."
    SupportImagePickFailure.CANCELLED -> ""
    SupportImagePickFailure.UNKNOWN, null -> "Không thể xử lý ảnh đã chọn. Vui lòng thử lại."
}
