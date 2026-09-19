package com.example.walletwise.presentation.transaction

import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.service.*
import com.example.walletwise.domain.util.normalizeVietnameseSearchText
import com.example.walletwise.foundation.randomUuidString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.coroutineContext

data class TransactionDraftState(
    val userId: String? = null,
    val draft: TransactionDraft? = null,
    val isAnalyzing: Boolean = false,
    val isSaving: Boolean = false,
    val message: String = "Nhập khoản thu hoặc chi. Ví dụ: Hôm nay mua đồ ăn 50 nghìn. Đủ thông tin sẽ được ghi ngay.",
    val savedDraftId: String? = null
)

class TransactionDraftPresenter(
    private val scope: CoroutineScope,
    private val analyzer: TransactionTextAnalyzer,
    private val categories: StateFlow<List<Category>>,
    private val write: suspend (Transaction) -> Result<Boolean>,
    private val writeTimeoutMillis: Long = 15_000L
) {
    private val mutableState = MutableStateFlow(TransactionDraftState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var job: Job? = null
    private var writeJob: Job? = null
    private var automatic = false
    private var lastAutomaticInput: String? = null
    private val entryInputs = mutableSetOf<String>()
    private val savedInputs = mutableSetOf<String>()

    fun setUserId(userId: String?) {
        if (mutableState.value.userId == userId) return
        generation++
        job?.cancel()
        writeJob?.cancel()
        automatic = false
        lastAutomaticInput = null
        entryInputs.clear()
        savedInputs.clear()
        mutableState.value = TransactionDraftState(userId = userId)
    }

    fun submitAutomatically(text: String, source: DraftSource = DraftSource.TEXT) {
        val current = mutableState.value
        if (text.isBlank() || current.isAnalyzing || current.isSaving || current.userId == null) return
        val signature = normalizeVietnameseSearchText(text)
        val supplement = current.draft?.takeIf { current.savedDraftId == null && it.missingFields.isNotEmpty() }
        // Reusing a field answer (e.g. 50k) for a new entry is valid; completed request callbacks remain blocked.
        if (signature in savedInputs && supplement == null) return
        if (automatic && signature == lastAutomaticInput) {
            if (current.savedDraftId == null && current.draft?.missingFields?.isEmpty() == true) confirm()
            return
        }
        automatic = true
        if (current.savedDraftId != null) entryInputs.clear()
        entryInputs += signature
        lastAutomaticInput = signature
        analyze(text, source, autoSave = true, supplement = supplement)
    }

    fun analyze(text: String, source: DraftSource = DraftSource.TEXT, autoSave: Boolean = false, supplement: TransactionDraft? = null) {
        val current = mutableState.value
        val userId = current.userId ?: return
        if (text.isBlank() || current.isAnalyzing || current.isSaving) return
        val requestId = supplement?.id ?: randomUuidString()
        val version = generation
        mutableState.value = current.copy(isAnalyzing = true, draft = supplement, savedDraftId = null, message = "Đang phân tích…")
        job = scope.launch {
            try {
                var draft = analyzer.analyze(text, requestId, userId, categories.value)
                if (supplement != null) {
                    val fields = supplement.missingFields
                    val answerCategory = categories.value.filter { it.type == supplement.type && normalizeVietnameseSearchText(it.name) == normalizeVietnameseSearchText(text) }.singleOrNull()?.name
                    draft = supplement.copy(
                        amount = if (DraftField.AMOUNT in fields) draft.amount else supplement.amount,
                        type = if (DraftField.TYPE in fields) draft.type ?: text.trim().takeIf { it in listOf("Chi", "Thu") } else supplement.type,
                        category = if (DraftField.CATEGORY in fields) answerCategory ?: draft.category else supplement.category,
                        timestamp = if (DraftField.DATE in fields && (Regex("\\d{1,2}/\\d{1,2}/\\d{4}").containsMatchIn(text) || listOf("hom nay", "hom qua").any { it in normalizeVietnameseSearchText(text) })) draft.timestamp else supplement.timestamp,
                        paymentMethod = if (DraftField.PAYMENT_METHOD in fields) draft.paymentMethod else supplement.paymentMethod,
                        confidence = supplement.confidence + fields.associateWith { 1f }
                    )
                }
                val resolvedSource = supplement?.source ?: source
                if (autoSave) {
                    draft = draft.copy(
                        amount = draft.amount.takeIf { (draft.confidence[DraftField.AMOUNT] ?: 0f) >= 0.8f },
                        type = draft.type.takeIf { (draft.confidence[DraftField.TYPE] ?: 0f) >= 0.8f },
                        category = draft.category.takeIf { (draft.confidence[DraftField.CATEGORY] ?: 0f) >= 0.8f },
                        timestamp = draft.timestamp.takeIf { (draft.confidence[DraftField.DATE] ?: 0f) >= 0.8f }
                    )
                    draft = validated(draft.copy(paymentMethod = draft.paymentMethod ?: "Tiền mặt", source = resolvedSource))
                }
                coroutineContext.ensureActive()
                if (version != generation || mutableState.value.userId != userId) return@launch
                mutableState.value = mutableState.value.copy(isAnalyzing = false, draft = draft.copy(source = resolvedSource),
                    message = if (autoSave) question(draft) else "Bản nháp được phân tích cục bộ. Hãy kiểm tra và bổ sung các ô còn thiếu.")
                if (autoSave && draft.missingFields.isEmpty()) confirm()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (version == generation) mutableState.value = mutableState.value.copy(isAnalyzing = false,
                    message = "Chưa phân tích được. Bạn có thể nhập tay hoặc thử lại.")
            }
        }
    }

    fun edit(draft: TransactionDraft) {
        val current = mutableState.value
        if (current.isSaving || current.savedDraftId != null || draft.id != current.draft?.id || draft.userId != current.userId) return
        val updated = validated(draft)
        mutableState.value = current.copy(draft = updated, message = if (automatic) question(updated) else current.message)
        if (automatic && updated.missingFields.isEmpty()) confirm()
    }

    private fun validated(draft: TransactionDraft): TransactionDraft {
        val missing = buildSet {
            if (draft.amount == null || draft.amount <= 0) add(DraftField.AMOUNT)
            if (draft.type == null) add(DraftField.TYPE)
            if (draft.category == null) add(DraftField.CATEGORY)
            if (draft.timestamp == null) add(DraftField.DATE)
            if (draft.paymentMethod == null) add(DraftField.PAYMENT_METHOD)
        }
        return draft.copy(missingFields = missing)
    }

    private fun question(draft: TransactionDraft): String = when {
        DraftField.AMOUNT in draft.missingFields -> "Số tiền là bao nhiêu? Hãy nhập một số tiền cụ thể."
        DraftField.TYPE in draft.missingFields -> "Đây là khoản Chi hay Thu?"
        DraftField.CATEGORY in draft.missingFields -> "Khoản này thuộc danh mục nào? Hãy chọn hoặc nhập đúng tên danh mục."
        DraftField.DATE in draft.missingFields -> "Giao dịch vào ngày nào? Hãy nhập ngày dd/MM/yyyy."
        DraftField.PAYMENT_METHOD in draft.missingFields -> "Bạn thanh toán bằng cách nào?"
        else -> "Đủ thông tin. Đang ghi giao dịch…"
    }

    fun acceptReceiptAutomatically(receipt: ReceiptTransactionDraft) {
        val current = mutableState.value
        if (current.isSaving || receipt.transaction.userId != current.userId || current.draft?.id == receipt.transaction.id) return
        acceptReceipt(receipt)
        automatic = true
        lastAutomaticInput = null
        var draft = validated(receipt.transaction.copy(paymentMethod = receipt.transaction.paymentMethod ?: "Tiền mặt", source = DraftSource.RECEIPT))
        val uncertain = setOf(DraftField.AMOUNT, DraftField.CATEGORY, DraftField.DATE).filter { (draft.confidence[it] ?: 0f) < 0.8f }.toSet()
        draft = draft.copy(
            amount = draft.amount.takeUnless { DraftField.AMOUNT in uncertain || receipt.totalCandidates.size > 1 },
            category = draft.category.takeUnless { DraftField.CATEGORY in uncertain },
            timestamp = draft.timestamp.takeUnless { DraftField.DATE in uncertain }
        )
        draft = validated(draft)
        mutableState.value = mutableState.value.copy(draft = draft, message = question(draft))
        if (draft.missingFields.isEmpty()) confirm()
    }

    /** Receipt recognition joins the same editable preview and explicit confirmation path. */
    fun acceptReceipt(receipt: ReceiptTransactionDraft) {
        val current = mutableState.value
        if (current.isSaving || receipt.transaction.userId != current.userId) return
        generation++
        job?.cancel()
        mutableState.value = TransactionDraftState(userId = current.userId,
            draft = receipt.transaction.copy(source = DraftSource.RECEIPT),
            message = receipt.warning ?: "Đã nhận diện hóa đơn. Hãy kiểm tra và sửa trước khi xác nhận.")
    }

    fun confirm() {
        val current = mutableState.value
        if (current.isSaving || current.isAnalyzing || current.savedDraftId != null) return
        val draft = current.draft ?: return
        val transaction = draft.transaction()
        if (transaction == null || (draft.amount ?: 0L) > MoneyInput.MAX_EXACT_AMOUNT ||
            categories.value.none { it.type == transaction.type && it.name == transaction.category }) {
            mutableState.value = current.copy(message = "Vui lòng kiểm tra số tiền, loại, danh mục, ngày và thanh toán trước khi lưu.")
            return
        }
        val version = generation
        mutableState.value = current.copy(isSaving = true, message = "Đang lưu…")
        writeJob = scope.launch {
            val result = try { withTimeoutOrNull(writeTimeoutMillis) { write(transaction.copy(categoryId =
                categories.value.firstOrNull { it.type == transaction.type && it.name == transaction.category }?.id.orEmpty())) }
                ?: Result.failure<Boolean>(IllegalStateException("Acknowledgement timeout")) } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { Result.failure<Boolean>(IllegalStateException("Write failed")) }
            coroutineContext.ensureActive()
            if (version != generation || mutableState.value.userId != transaction.userId) return@launch
            if (result.getOrNull() == true && automatic) savedInputs.addAll(entryInputs)
            mutableState.value = mutableState.value.copy(isSaving = false,
                savedDraftId = if (result.getOrNull() == true) draft.id else null,
                message = if (result.getOrNull() == true) "Đã ghi ${transaction.type} ${MoneyInput.format(draft.amount.toString())} đ • ${transaction.category}." else "Chưa nhận được xác nhận lưu. Hãy thử lại; giao dịch giữ cùng mã để tránh ghi trùng.")
        }
    }

    fun cancelAnalysis() {
        if (!mutableState.value.isAnalyzing) return
        generation++
        job?.cancel()
        mutableState.value = mutableState.value.copy(isAnalyzing = false, message = "Đã hủy phân tích.")
    }

    fun newDraft() {
        if (mutableState.value.isSaving) return
        cancelAnalysis()
        automatic = false
        lastAutomaticInput = null
        entryInputs.clear()
        savedInputs.clear()
        mutableState.value = TransactionDraftState(userId = mutableState.value.userId)
    }

    fun close() { generation++; job?.cancel(); writeJob?.cancel(); mutableState.value = TransactionDraftState() }
}
