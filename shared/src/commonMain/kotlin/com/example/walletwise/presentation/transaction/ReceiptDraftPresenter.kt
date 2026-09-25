package com.example.walletwise.presentation.transaction

import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.service.ReceiptTextParser
import com.example.walletwise.foundation.randomUuidString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.coroutineContext

fun interface ReceiptOcrEngine { suspend fun recognize(imageToken: String): String }
data class ReceiptDraftState(val userId: String? = null, val isRecognizing: Boolean = false,
    val draft: ReceiptTransactionDraft? = null, val error: String? = null)

/** OCR only produces a draft. This presenter has no write repository or image uploader. */
class ReceiptDraftPresenter(private val scope: CoroutineScope, private val engine: ReceiptOcrEngine,
    private val parser: ReceiptTextParser, private val categories: StateFlow<List<Category>>) {
    private val mutableState = MutableStateFlow(ReceiptDraftState())
    val state = mutableState.asStateFlow()
    private var version = 0L
    private var job: Job? = null
    fun setUserId(userId: String?) {
        if (mutableState.value.userId == userId) return
        cancel()
        mutableState.value = ReceiptDraftState(userId)
    }
    fun analyze(imageToken: String) {
        val current = mutableState.value
        val uid = current.userId ?: return
        if (current.isRecognizing) return
        val generation = ++version
        mutableState.value = current.copy(isRecognizing = true, draft = null, error = null)
        job = scope.launch {
            try {
                val text = engine.recognize(imageToken)
                coroutineContext.ensureActive()
                if (generation != version || mutableState.value.userId != uid) return@launch
                val parsed = parser.parse(text, randomUuidString(), uid, categories.value)
                mutableState.value = mutableState.value.copy(isRecognizing = false, draft = parsed)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == version) mutableState.value = mutableState.value.copy(isRecognizing = false,
                    error = "Không nhận diện được ảnh. Hãy thử ảnh rõ hơn hoặc nhập tay.")
            }
        }
    }
    fun permissionDenied() { mutableState.value = mutableState.value.copy(error = "Chưa được phép mở ảnh/camera. Bạn vẫn có thể nhập tay.") }
    fun imageUnreadable() { mutableState.value = mutableState.value.copy(error = "Không đọc được ảnh. Hãy chọn ảnh rõ hơn hoặc nhập tay.") }
    fun cancel() { version++; job?.cancel(); mutableState.value = ReceiptDraftState(userId = mutableState.value.userId) }
    fun close() { cancel(); mutableState.value = ReceiptDraftState() }
}
