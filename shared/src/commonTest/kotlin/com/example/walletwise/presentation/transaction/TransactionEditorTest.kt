package com.example.walletwise.presentation.transaction

import com.example.walletwise.data.repository.CallbackCategoryRepository
import com.example.walletwise.data.repository.CallbackTransactionRepository
import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.repository.*
import com.example.walletwise.presentation.category.CategorySessionController
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionEditorTest {
    @Test fun amountFormatterAndSafeGroupedPaste() {
        assertEquals("1.000", TransactionAmountInput.format("1000"))
        assertEquals("1.000.000", TransactionAmountInput.format("1000000"))
        for (input in listOf("1.000.000", "1,000,000", "1 000 000", "1\u00a0000\u00a0000")) assertEquals("1000000", TransactionAmountInput.normalize(input))
        assertEquals(1000000L, TransactionAmountInput.value("1000000"))
        assertEquals("", TransactionAmountInput.normalize(""))
    }
    @Test fun negativeLettersDecimalAndOverflowAreRejected() {
        for (input in listOf("-1", "1a", "1,5", "1.00", "1.000.", "9223372036854775808", "9007199254740992")) assertNull(TransactionAmountInput.normalize(input), input)
        assertNull(TransactionAmountInput.value("0"))
        assertEquals(TransactionAmountInput.MAX_AMOUNT, TransactionAmountInput.value("9007199254740991"))
    }
    @Test fun cursorMappingRoundTripsAndBackspaceDeletesOneDigit() {
        for (length in 1..16) {
            val digits = "1".repeat(length)
            for (cursor in 0..length) assertEquals(cursor, TransactionAmountInput.formattedToOriginal(TransactionAmountInput.originalToFormatted(cursor, length), digits))
        }
        val digits = "1000000"
        val cursor = TransactionAmountInput.formattedToOriginal(5, digits)
        val deleted = digits.removeRange(cursor - 1, cursor)
        assertEquals(6, deleted.length); assertEquals("100.000", TransactionAmountInput.format(deleted))
    }
    @Test fun invalidAddHasFieldErrorsAndDoesNotWrite() = runTest {
        val h = harness(); h.editor.openAdd(); h.editor.submit(); runCurrent()
        assertTrue(h.editor.snapshot.errors.containsKey("amount")); assertTrue(h.editor.snapshot.errors.containsKey("category")); assertTrue(h.writer.requests.isEmpty())
    }
    @Test fun validAddWaitsForServerAndPreventsDoubleSubmit() = runTest {
        val h = harness(); h.validAdd(); h.editor.submit(); h.editor.submit(); runCurrent()
        assertEquals(1, h.writer.requests.size); assertTrue(h.editor.snapshot.saving); assertNull(h.editor.snapshot.success)
        val tx = h.writer.requests.single().second
        assertEquals("stable-id", tx.id); assertEquals("a", tx.userId); assertEquals(50000.0, tx.amount); assertEquals("", tx.imageUrl)
        h.writer.complete(null); runCurrent()
        assertFalse(h.editor.snapshot.visible); assertNotNull(h.editor.snapshot.success)
        h.writer.complete(null); runCurrent(); assertEquals(1, h.writer.requests.size)
    }
    @Test fun retryUsesSameIdAndKeepsFormAfterError() = runTest {
        val h = harness(); h.validAdd(); h.editor.noteChanged("kept"); val form = h.editor.snapshot.form
        h.editor.submit(); runCurrent(); val old = h.writer.callbacks.last()
        h.writer.complete(TransactionReadFailure.NETWORK); runCurrent()
        assertFalse(h.editor.snapshot.saving); assertEquals(form, h.editor.snapshot.form); assertNotNull(h.editor.snapshot.error)
        h.editor.submit(); runCurrent()
        assertEquals(listOf("stable-id", "stable-id"), h.writer.requests.map { it.second.id }); assertEquals(1, h.idCount)
        old.complete(null); runCurrent(); assertTrue(h.editor.snapshot.saving)
        h.writer.complete(null); runCurrent(); assertNotNull(h.editor.snapshot.success)
    }
    @Test fun editPreservesIdImageAndOldCategoryEvenWhenRemoved() = runTest {
        val h = harness(); val tx = h.primary(image = "https://example.invalid/existing", category = "Old category")
        h.editor.openEdit(tx); h.editor.noteChanged("changed"); h.editor.submit(); runCurrent()
        val changed = h.writer.requests.single().second
        assertEquals(tx.id, changed.id); assertEquals(tx.imageUrl, changed.imageUrl); assertEquals("Old category", changed.category)
        assertEquals(TransactionMutationKind.UPDATE, h.writer.requests.single().first)
    }
    @Test fun fractionalExistingAmountIsPreservedForOtherFieldEdits() = runTest {
        val h = harness(); val tx = h.primary(amount = 12.5)
        h.editor.openEdit(tx); h.editor.noteChanged("only note"); h.editor.submit(); runCurrent()
        assertEquals(12.5, h.writer.requests.single().second.amount); assertNotNull(h.editor.snapshot.fractionalAmountNotice)
    }
    @Test fun editChangingTypeNeedsCategoryOfNewType() = runTest {
        val h = harness(); h.editor.openEdit(h.primary(category = "Old")); h.editor.typeChanged("Thu"); h.editor.submit(); runCurrent()
        assertEquals("", h.editor.snapshot.form.category); assertTrue(h.editor.snapshot.errors.containsKey("category")); assertTrue(h.writer.requests.isEmpty())
    }
    @Test fun replacingFractionalAmountWithIntegerIsDirtyEvenWhenDigitsLookUnchanged() = runTest {
        val h = harness(); h.editor.openEdit(h.primary(amount = 12.5)); h.editor.amountChanged("12")
        assertTrue(h.editor.snapshot.dirty); h.editor.requestBack(); assertTrue(h.editor.snapshot.discardConfirmation)
        h.editor.cancelDiscard(); h.editor.submit(); runCurrent(); assertEquals(12.0, h.writer.requests.single().second.amount)
    }
    @Test fun deleteRequiresConfirmationAndCancelDoesNotWrite() = runTest {
        val h = harness(); val tx = h.primary()
        h.editor.confirmDelete(); runCurrent(); assertTrue(h.writer.requests.isEmpty())
        h.editor.requestDelete(tx); assertNotNull(h.editor.snapshot.pendingDelete)
        h.editor.cancelDelete(); h.editor.confirmDelete(); runCurrent(); assertTrue(h.writer.requests.isEmpty())
        assertEquals(listOf(tx.id), h.transactions.transactions.value.map { it.id })
    }
    @Test fun deleteFailureKeepsDocumentAndConfirmationForRetry() = runTest {
        val h = harness(); val tx = h.primary(); h.editor.requestDelete(tx); h.editor.confirmDelete(); h.editor.confirmDelete(); runCurrent()
        assertEquals(1, h.writer.requests.size)
        h.writer.complete(TransactionReadFailure.PERMISSION_DENIED); runCurrent()
        assertEquals(tx, h.editor.snapshot.pendingDelete); assertNotNull(h.editor.snapshot.error)
        assertEquals(listOf(tx.id), h.transactions.transactions.value.map { it.id })
        h.editor.confirmDelete(); runCurrent(); h.writer.complete(null); runCurrent()
        assertNull(h.editor.snapshot.pendingDelete); assertNotNull(h.editor.snapshot.success)
        h.reader.snapshot(emptyList()); h.reader.legacy!!.complete(emptyList(), null); runCurrent(); assertTrue(h.transactions.transactions.value.isEmpty())
    }
    @Test fun uidMismatchAndUnknownDocumentCannotWrite() = runTest {
        val h = harness(); val tx = h.primary()
        h.editor.openEdit(tx.copy(userId = "b")); h.editor.requestDelete(tx.copy(userId = "b")); assertFalse(h.editor.snapshot.visible)
        assertTrue(h.repository.updateTransaction(tx.copy(userId = "b")).isFailure)
        assertTrue(h.repository.deleteTransaction("unknown").isFailure)
        assertTrue(h.repository.addTransaction(tx.copy(userId = "b")).isFailure)
        assertTrue(h.writer.requests.isEmpty())
    }
    @Test fun lateCallbackAfterDisposeCannotPublishAndCancelIsIdempotent() = runTest {
        val h = harness(); h.validAdd(); h.editor.submit(); runCurrent()
        h.editor.dispose(); h.editor.dispose(); runCurrent(); h.writer.complete(null); runCurrent()
        assertFalse(h.editor.snapshot.visible); assertNull(h.editor.snapshot.success); assertEquals(1, h.writer.cancellations)
        assertEquals(1, h.category.removals)
    }
    @Test fun logoutDuringWriteClearsAAndRejectsLateSuccessForB() = runTest {
        val h = harness(); h.validAdd(); h.editor.noteChanged("A private form"); h.editor.submit(); runCurrent()
        h.uid = "b"
        assertEquals("", h.editor.snapshot.form.note); assertFalse(h.editor.snapshot.visible)
        h.editor.setUserId("b"); runCurrent(); h.writer.complete(null); runCurrent()
        assertEquals("b", h.editor.snapshot.userId); assertNull(h.editor.snapshot.success); assertEquals("", h.editor.snapshot.form.note)
    }
    @Test fun dirtyBackOffersDiscardAndCancelKeepsForm() = runTest {
        val h = harness(); h.editor.openAdd(); h.editor.requestBack(); assertFalse(h.editor.snapshot.visible)
        h.validAdd(); val form = h.editor.snapshot.form; h.editor.requestBack(); assertTrue(h.editor.snapshot.discardConfirmation)
        h.editor.cancelDiscard(); assertEquals(form, h.editor.snapshot.form); assertTrue(h.editor.snapshot.visible)
        h.editor.requestBack(); h.editor.confirmDiscard(); assertFalse(h.editor.snapshot.visible)
    }
    @Test fun formChangesAndBackAreLockedDuringSave() = runTest {
        val h = harness(); h.validAdd(); h.editor.submit(); runCurrent(); val form = h.editor.snapshot.form
        h.editor.amountChanged("75000"); h.editor.noteChanged("ignored"); h.editor.requestBack()
        assertEquals(form, h.editor.snapshot.form); assertTrue(h.editor.snapshot.visible); assertFalse(h.editor.snapshot.discardConfirmation)
    }
    @Test fun legacyObservationIsReadOnlyAtEditorAndRepository() = runTest {
        val h = harness(); h.reader.snapshot(emptyList()); h.reader.legacy!!.complete(listOf(document("legacy")), null); runCurrent()
        val tx = h.transactions.transactions.value.single(); assertTrue(tx.isLegacy)
        h.editor.openEdit(tx); h.editor.requestDelete(tx); assertFalse(h.editor.snapshot.visible); assertNull(h.editor.snapshot.pendingDelete)
        assertTrue(h.repository.updateTransaction(tx).isFailure); assertTrue(h.repository.deleteTransaction(tx.id).isFailure); assertTrue(h.writer.requests.isEmpty())
    }
    @Test fun editorDoesNotRestartHomeOrCategoryListenerAndNoCategoryWrites() = runTest {
        val h = harness(); h.validAdd(); h.editor.requestBack(); h.editor.confirmDiscard(); h.validAdd()
        assertEquals(1, h.reader.registrations); assertEquals(1, h.category.registrations)
        assertEquals(false, h.categoryRepository.ensureDefaultCategories("a", DefaultCategories).let { (it as com.example.walletwise.domain.result.RepositoryResult.Success).value })
    }
    @Test fun emptyCategoryUsesSharedDefaultsAndSameUidIsStable() = runTest {
        val h = harness(); h.category.observer!!.changed(emptyList(), null); runCurrent()
        assertEquals(DefaultCategories, h.editor.categories.state.value.categories)
        h.editor.setUserId("a"); runCurrent(); assertEquals(1, h.category.registrations)
    }
    @Test fun categoryUidSwitchAndDisposeRejectLateUserData() = runTest {
        val h = harness(); val old = h.category.observer!!; h.uid = "b"; h.editor.setUserId("b"); runCurrent()
        old.changed(listOf(Category(name = "A private category")), null); runCurrent()
        assertTrue(h.editor.categories.state.value.categories.none { it.name == "A private category" })
        h.editor.dispose(); runCurrent(); h.category.observer!!.changed(listOf(Category(name = "late")), null); runCurrent()
        assertNull(h.editor.categories.state.value.userId); assertTrue(h.editor.categories.state.value.categories.none { it.name == "late" })
    }
    private fun TestScope.harness(): Harness = Harness(backgroundScope).also { runCurrent() }
    private inner class Harness(scope: CoroutineScope) {
        var uid = "a"; var idCount = 0
        val reader = Reader(); val writer = Writer(); val category = Categories()
        val repository = CallbackTransactionRepository(reader, writer)
        val categoryRepository = CallbackCategoryRepository(category)
        val transactions = TransactionSessionController(scope, repository).also { it.setUserId(uid) }
        val editor = TransactionEditorPresenter(scope, repository, CategorySessionController(scope, categoryRepository), { uid }, { idCount++; "stable-id" }).also { it.setUserId(uid) }
        fun validAdd() { editor.openAdd(); editor.amountChanged("50.000"); editor.categoryChanged("Ăn uống") }
        fun primary(image: String = "", category: String = "Ăn uống", amount: Double = 50000.0): Transaction {
            reader.snapshot(listOf(document("existing").copy(imageUrl = image, category = category, amount = amount)))
            return document("existing").copy(imageUrl = image, category = category, amount = amount).toTransaction(uid)
        }
    }
}

private fun document(id: String) = TransactionDocument(id, "Chi", 50000.0, "Ăn uống", "Tiền mặt", "", 1700000000000, null, "")
private class Reader : CallbackTransactionService {
    var observer: TransactionSnapshotObserver? = null; var legacy: TransactionReadCompletion? = null; var registrations = 0
    override fun observePrimary(userId: String, observer: TransactionSnapshotObserver): TransactionCancellation { registrations++; this.observer = observer; return object : TransactionCancellation { override fun cancel() {} } }
    override fun readLegacy(userId: String, completion: TransactionReadCompletion): TransactionCancellation { legacy = completion; return object : TransactionCancellation { override fun cancel() {} } }
    fun snapshot(documents: List<TransactionDocument>) { observer!!.changed(documents, false, null) }
}
private class Writer : CallbackTransactionWriteService {
    val requests = mutableListOf<Pair<TransactionMutationKind, Transaction>>(); val callbacks = mutableListOf<TransactionWriteCompletion>(); var cancellations = 0
    override fun mutate(kind: TransactionMutationKind, transaction: Transaction, completion: TransactionWriteCompletion): TransactionCancellation {
        requests += kind to transaction; callbacks += completion
        return object : TransactionCancellation { var active = true; override fun cancel() { if (active) { active = false; cancellations++ } } }
    }
    fun complete(failure: TransactionReadFailure?) { callbacks.last().complete(failure) }
}
private class Categories : CallbackCategoryService {
    var observer: CategorySnapshotObserver? = null; var registrations = 0; var removals = 0
    override fun observeCategories(userId: String, observer: CategorySnapshotObserver): TransactionCancellation {
        registrations++; this.observer = observer; observer.changed(DefaultCategories, null)
        return object : TransactionCancellation { var active = true; override fun cancel() { if (active) { active = false; removals++ } } }
    }
}
