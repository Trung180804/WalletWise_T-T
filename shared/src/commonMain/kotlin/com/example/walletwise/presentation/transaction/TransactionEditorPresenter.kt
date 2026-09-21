package com.example.walletwise.presentation.transaction

import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.repository.*
import com.example.walletwise.domain.usecase.*
import com.example.walletwise.domain.validation.*
import com.example.walletwise.foundation.currentEpochMilliseconds
import com.example.walletwise.foundation.randomUuidString
import com.example.walletwise.presentation.category.CategorySessionController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class TransactionForm(
    val type: String = "Chi", val amountDigits: String = "", val category: String = "",
    val timestamp: Long = 0L, val note: String = "", val paymentMethod: String = "Tiền mặt"
) { val amount: Long? get() = TransactionAmountInput.value(amountDigits) }

data class TransactionEditorState(
    val userId: String? = null, val visible: Boolean = false, val editing: Boolean = false,
    val form: TransactionForm = TransactionForm(), val dirty: Boolean = false,
    val saving: Boolean = false, val errors: Map<String, String> = emptyMap(),
    val error: String? = null, val success: String? = null, val discardConfirmation: Boolean = false,
    val pendingDelete: Transaction? = null, val fractionalAmountNotice: String? = null
)

/** One editor per Home/controller. No optimistic mutation; only the existing Home listener updates data. */
class TransactionEditorPresenter(
    private val scope: CoroutineScope,
    private val repository: TransactionRepository,
    val categories: CategorySessionController,
    private val authoritativeUid: () -> String?,
    private val newId: () -> String = ::randomUuidString
) {
    private val mutableState = MutableStateFlow(TransactionEditorState())
    val state: StateFlow<TransactionEditorState> = mutableState.asStateFlow()
    val snapshot: TransactionEditorState get() = mutableState.value.takeIf { it.userId == authoritativeUid() && !disposed } ?: TransactionEditorState()
    private var original: Transaction? = null
    private var initial = TransactionForm()
    private var amountTouched = false
    private var requestId: String? = null
    private var generation = 0L
    private var job: Job? = null
    private var disposed = false
    private val noUpload = object : ImageUploader {
        override suspend fun upload(image: ImageUpload): Result<String> = Result.failure(IllegalStateException("Ảnh chưa được hỗ trợ trên iOS"))
    }
    private val add = AddTransactionUseCase(repository, noUpload)
    private val update = UpdateTransactionUseCase(repository, noUpload)

    fun setUserId(uid: String?) {
        if (disposed || mutableState.value.userId == uid) return
        generation++; job?.cancel(); job = null
        original = null; requestId = null; amountTouched = false
        initial = TransactionForm()
        mutableState.value = TransactionEditorState(userId = uid)
        categories.setUserId(uid)
    }
    fun openAdd() {
        val uid = authoritativeUid()?.takeIf { it.isNotBlank() } ?: return
        if (disposed || snapshot.saving) return
        setUserId(uid); original = null; requestId = null; amountTouched = false
        initial = TransactionForm(timestamp = currentEpochMilliseconds())
        mutableState.value = TransactionEditorState(userId = uid, visible = true, form = initial)
    }
    fun openEdit(transaction: Transaction) {
        val uid = authoritativeUid() ?: return
        if (disposed || snapshot.saving || transaction.userId != uid || transaction.isLegacy) return
        setUserId(uid); original = transaction; requestId = transaction.id; amountTouched = false
        initial = TransactionForm(transaction.type, transaction.amount.takeIf { it.isFinite() && it > 0 && it <= TransactionAmountInput.MAX_AMOUNT.toDouble() }?.toLong()?.toString().orEmpty(), transaction.category, transaction.timestamp, transaction.note, transaction.paymentMethod)
        mutableState.value = TransactionEditorState(userId = uid, visible = true, editing = true, form = initial,
            fractionalAmountNotice = transaction.amount.takeIf { it.isFinite() && it % 1.0 != 0.0 }?.let { "Số tiền gốc $it đ được giữ nguyên nếu bạn không sửa ô số tiền." })
    }
    private fun change(transform: (TransactionForm) -> TransactionForm) {
        val s = snapshot
        if (!s.visible || s.saving || disposed) return
        val form = transform(s.form)
        val amountChanged = amountTouched && original != null && form.amount?.toDouble() != original?.amount
        mutableState.value = s.copy(form = form, dirty = form != initial || amountChanged, errors = emptyMap(), error = null, success = null)
    }
    fun amountChanged(text: String) {
        if (!snapshot.visible || snapshot.saving || disposed) return
        val digits = TransactionAmountInput.normalize(text)
        if (digits == null) { mutableState.value = snapshot.copy(errors = mapOf("amount" to "Nhập số tiền nguyên không âm, đúng định dạng và trong giới hạn.")); return }
        amountTouched = true
        change { it.copy(amountDigits = digits) }
    }
    fun typeChanged(type: String) = change { it.copy(type = type, category = if (it.type == type) it.category else "") }
    fun categoryChanged(category: String) = change { it.copy(category = category) }
    fun dateChanged(timestamp: Long) = change { it.copy(timestamp = timestamp) }
    fun noteChanged(note: String) = change { it.copy(note = note) }
    fun paymentChanged(method: String) = change { it.copy(paymentMethod = method) }
    fun requestBack() {
        if (snapshot.saving) return
        if (snapshot.dirty) mutableState.value = snapshot.copy(discardConfirmation = true)
        else dismiss()
    }
    fun cancelDiscard() { mutableState.value = snapshot.copy(discardConfirmation = false) }
    fun confirmDiscard() { if (!snapshot.saving) dismiss() }
    private fun dismiss() { original = null; requestId = null; amountTouched = false; mutableState.value = TransactionEditorState(userId = authoritativeUid()) }
    fun availableCategories(): List<Category> {
        val s = snapshot
        val current = categories.state.value.takeIf { it.userId == s.userId }?.categories.orEmpty().filter { it.type == s.form.type }
        val old = original?.takeIf { it.type == s.form.type && it.category.isNotBlank() }
        return if (old != null && current.none { it.name == old.category }) current + Category(name = old.category, type = old.type) else current
    }
    fun retryCategories() = categories.refresh()
    fun submit() {
        val s = snapshot
        val uid = authoritativeUid()
        if (disposed || !s.visible || s.saving || uid.isNullOrBlank() || s.userId != uid) return
        val errors = mutableMapOf<String, String>()
        val amount = if (original != null && !amountTouched) original!!.amount else s.form.amount?.toDouble() ?: 0.0
        if (!amount.isFinite() || amount <= 0) errors["amount"] = "Số tiền phải lớn hơn 0."
        if (s.form.type !in setOf("Thu", "Chi")) errors["type"] = "Chọn Thu nhập hoặc Chi tiêu."
        val oldCategoryAllowed = original?.let { it.type == s.form.type && it.category == s.form.category && it.category.isNotBlank() } == true
        val catState = categories.state.value
        if (!oldCategoryAllowed && (catState.userId != uid || catState.isLoading || catState.error != null || availableCategories().none { it.name == s.form.category })) errors["category"] = "Vui lòng chọn danh mục hợp lệ."
        if (s.form.timestamp <= 0) errors["date"] = "Ngày không hợp lệ."
        if (s.form.paymentMethod.isBlank()) errors["payment"] = "Chọn phương thức thanh toán."
        if (errors.isNotEmpty()) { mutableState.value = s.copy(errors = errors); return }
        val id = requestId ?: newId().also { requestId = it }
        val transaction = (original ?: Transaction()).copy(id = id, userId = uid, type = s.form.type, amount = amount,
            category = s.form.category, timestamp = s.form.timestamp, note = s.form.note, paymentMethod = s.form.paymentMethod)
        val ticket = ++generation
        mutableState.value = s.copy(saving = true, error = null, errors = emptyMap(), success = null)
        job = scope.launch {
            val result = if (original == null) add(AddTransactionInput(transaction)) else update(UpdateTransactionInput(transaction))
            if (disposed || ticket != generation || authoritativeUid() != uid) return@launch
            if (result.getOrNull() == true) {
                original = null; requestId = null
                mutableState.value = TransactionEditorState(userId = uid, success = "Đã lưu giao dịch.")
            } else mutableState.value = mutableState.value.copy(saving = false, error = safeError(result.exceptionOrNull()))
        }
    }
    fun requestDelete(transaction: Transaction) {
        val uid = authoritativeUid() ?: return
        if (disposed || snapshot.saving || transaction.userId != uid || transaction.isLegacy) return
        mutableState.value = TransactionEditorState(userId = uid, pendingDelete = transaction)
    }
    fun cancelDelete() { if (!snapshot.saving) mutableState.value = TransactionEditorState(userId = authoritativeUid()) }
    fun confirmDelete() {
        val s = snapshot
        val transaction = s.pendingDelete ?: return
        val uid = authoritativeUid()
        if (disposed || s.saving || uid == null || transaction.userId != uid) return
        val ticket = ++generation
        mutableState.value = s.copy(saving = true, error = null)
        job = scope.launch {
            val result = repository.deleteTransaction(transaction.id)
            if (disposed || ticket != generation || authoritativeUid() != uid) return@launch
            mutableState.value = if (result.getOrNull() == true) TransactionEditorState(userId = uid, success = "Đã xóa giao dịch.")
                else s.copy(saving = false, error = safeError(result.exceptionOrNull()))
        }
    }
    fun dispose() {
        if (disposed) return
        disposed = true; generation++; job?.cancel(); categories.close()
        original = null; requestId = null; initial = TransactionForm(); mutableState.value = TransactionEditorState()
    }
    private fun safeError(error: Throwable?): String = (error as? TransactionWriteException)?.message ?: "Không thể hoàn tất giao dịch. Vui lòng thử lại."
}
