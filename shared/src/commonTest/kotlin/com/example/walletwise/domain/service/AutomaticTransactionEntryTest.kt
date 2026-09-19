package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.*
import com.example.walletwise.presentation.transaction.TransactionDraftPresenter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticTransactionEntryTest {
    @Test fun sameAmountAnswerCanCompleteTwoDifferentEntriesWithoutManualReset() = runTest {
        val writes = mutableListOf<Transaction>()
        val presenter = TransactionDraftPresenter(this, LocalTransactionTextAnalyzer(DraftTestClock), MutableStateFlow(DefaultCategories), { writes += it; Result.success(true) })
        presenter.setUserId("u")
        presenter.submitAutomatically("Hôm nay mua đồ ăn"); runCurrent(); presenter.submitAutomatically("50 nghìn"); runCurrent()
        presenter.submitAutomatically("Hôm nay chi Giải trí"); runCurrent(); presenter.submitAutomatically("50 nghìn"); runCurrent()
        assertEquals(2, writes.size); assertEquals(2, writes.map { it.id }.distinct().size)
        assertEquals(listOf("Ăn uống", "Giải trí"), writes.map { it.category }); assertTrue(writes.all { it.amount == 50000.0 })
        presenter.submitAutomatically("Hôm nay mua đồ ăn"); runCurrent(); assertEquals(2, writes.size); presenter.close()
    }
    @Test fun lowConfidenceCategoryAsksInsteadOfAutomaticallyWriting() = runTest {
        var writes = 0
        val uncertainAnalyzer = object : TransactionTextAnalyzer {
            override suspend fun analyze(text: String, draftId: String, userId: String, categories: List<Category>) =
                analyzer.analyze(text, draftId, userId, categories).let { it.copy(confidence = it.confidence + (DraftField.CATEGORY to 0.3f)) }
        }
        val presenter = TransactionDraftPresenter(this, uncertainAnalyzer, MutableStateFlow(DefaultCategories), { writes++; Result.success(true) })
        presenter.setUserId("u"); presenter.submitAutomatically("mua đồ ăn 50k"); runCurrent()
        assertEquals(0, writes); assertNull(presenter.state.value.draft?.category)
        assertTrue(presenter.state.value.message.contains("danh mục")); presenter.close()
    }
    @Test fun logoutCancelsAutomaticWriteEvenWhenAnalysisRunsInline() = runTest {
        val gate = CompletableDeferred<Unit>(); var cancelled = false; var persisted = false
        val inline = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val presenter = TransactionDraftPresenter(inline, analyzer, MutableStateFlow(DefaultCategories), {
            try { gate.await(); persisted = true; Result.success(true) }
            catch (failure: CancellationException) { cancelled = true; throw failure }
        })
        presenter.setUserId("u"); presenter.submitAutomatically("mua đồ ăn 50k")
        assertTrue(presenter.state.value.isSaving)
        presenter.setUserId(null); gate.complete(Unit); runCurrent()
        assertTrue(cancelled); assertFalse(persisted); assertNull(presenter.state.value.draft); presenter.close()
    }
    private val analyzer = LocalTransactionTextAnalyzer(DraftTestClock)
    @Test fun completeTextSavesImmediatelyOnceAndVoiceUsesTheSamePath() = runTest {
        val writes = mutableListOf<Transaction>()
        val presenter = TransactionDraftPresenter(this, analyzer, MutableStateFlow(DefaultCategories), { writes += it; Result.success(true) })
        presenter.setUserId("u")
        presenter.submitAutomatically("Hôm nay mua đồ ăn 50 nghìn")
        presenter.submitAutomatically("Hôm nay mua đồ ăn 50 nghìn")
        runCurrent()
        presenter.submitAutomatically("Hôm nay mua đồ ăn 50 nghìn"); runCurrent()
        assertEquals(1, writes.size); assertEquals(50000.0, writes.single().amount)
        assertEquals("Ăn uống", writes.single().category); assertEquals("Chi", writes.single().type)
        assertEquals(DraftTestClock.now(), writes.single().timestamp); assertEquals("Tiền mặt", writes.single().paymentMethod)
        assertNotNull(presenter.state.value.savedDraftId)
        presenter.newDraft(); presenter.submitAutomatically("Hôm nay mua đồ ăn 50 nghìn", DraftSource.VOICE); runCurrent()
        assertEquals(2, writes.size); assertEquals(DraftSource.VOICE, presenter.state.value.draft?.source); presenter.close()
    }
    @Test fun missingAmountIsAskedThenSupplementSavesWithoutConfirmation() = runTest {
        val writes = mutableListOf<Transaction>()
        val presenter = TransactionDraftPresenter(this, analyzer, MutableStateFlow(DefaultCategories), { writes += it; Result.success(true) })
        presenter.setUserId("u"); presenter.submitAutomatically("Hôm nay mua đồ ăn"); runCurrent()
        assertTrue(writes.isEmpty()); assertTrue(presenter.state.value.message.contains("Số tiền"))
        val id = presenter.state.value.draft!!.id
        presenter.submitAutomatically("50 nghìn"); runCurrent()
        assertEquals(1, writes.size); assertEquals(id, writes.single().id); assertEquals(50000.0, writes.single().amount)
        presenter.submitAutomatically("Hôm nay mua đồ ăn"); runCurrent(); assertEquals(1, writes.size); presenter.close()
    }
    @Test fun ambiguousCategoryNeverChoosesAnArbitraryCategory() = runTest {
        var writes = 0
        val presenter = TransactionDraftPresenter(this, analyzer, MutableStateFlow(DefaultCategories), { writes++; Result.success(true) })
        presenter.setUserId("u"); presenter.submitAutomatically("Chi 50 nghìn Ăn uống hoặc Giải trí"); runCurrent()
        assertEquals(0, writes); assertNull(presenter.state.value.draft?.category)
        presenter.submitAutomatically("50 nghìn"); runCurrent(); assertEquals(0, writes)
        presenter.submitAutomatically("Ăn uống"); runCurrent(); assertEquals(1, writes); presenter.close()
    }
    @Test fun highConfidenceReceiptSavesOnceAndAmbiguousReceiptAsks() = runTest {
        val writes = mutableListOf<Transaction>()
        val presenter = TransactionDraftPresenter(this, analyzer, MutableStateFlow(DefaultCategories), { writes += it; Result.success(true) })
        presenter.setUserId("u")
        val parser = ReceiptTextParser(DraftTestClock)
        val certain = parser.parse("QUAN AN TEST\n17/09/2026\nTong cong 50.000", "receipt", "u", DefaultCategories)
        presenter.acceptReceiptAutomatically(certain); presenter.acceptReceiptAutomatically(certain); runCurrent()
        assertEquals(1, writes.size); assertEquals("receipt", writes.single().id)
        val uncertain = parser.parse("QUAN AN TEST\n17/09/2026\nTong cong 50.000\nThanh toan 100.000", "uncertain", "u", DefaultCategories)
        presenter.acceptReceiptAutomatically(uncertain); runCurrent(); assertEquals(1, writes.size)
        presenter.edit(presenter.state.value.draft!!.copy(amount = 100000L)); runCurrent()
        assertEquals(2, writes.size); assertEquals(100000.0, writes.last().amount); presenter.close()
    }
    @Test fun failedAcknowledgementAndRetryKeepOneStableId() = runTest {
        val ids = mutableListOf<String>(); var acknowledged = false
        val presenter = TransactionDraftPresenter(this, analyzer, MutableStateFlow(DefaultCategories), { ids += it.id; Result.success(acknowledged) })
        presenter.setUserId("u"); presenter.submitAutomatically("mua đồ ăn 50k"); runCurrent()
        assertNull(presenter.state.value.savedDraftId); assertTrue(presenter.state.value.message.contains("Chưa nhận"))
        acknowledged = true; presenter.submitAutomatically("mua đồ ăn 50k"); runCurrent()
        assertEquals(2, ids.size); assertEquals(1, ids.distinct().size); assertNotNull(presenter.state.value.savedDraftId); presenter.close()
    }
    @Test fun missingReceiptDateIsNotInventedWhenAnsweringAnotherField() = runTest {
        var writes = 0
        val presenter = TransactionDraftPresenter(this, analyzer, MutableStateFlow(DefaultCategories), { writes++; Result.success(true) })
        presenter.setUserId("u")
        val receipt = ReceiptTextParser(DraftTestClock).parse("QUAN AN TEST\nTong cong 50.000\nThanh toan 100.000", "r", "u", DefaultCategories)
        presenter.acceptReceiptAutomatically(receipt); presenter.submitAutomatically("50 nghìn"); runCurrent()
        assertEquals(0, writes); assertNull(presenter.state.value.draft?.timestamp)
        presenter.submitAutomatically("17/09/2026"); runCurrent(); assertEquals(1, writes); presenter.close()
    }
}
