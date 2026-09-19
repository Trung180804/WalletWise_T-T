package com.example.walletwise.domain.service

import com.example.walletwise.domain.model.*
import com.example.walletwise.presentation.transaction.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

internal object DraftTestClock : DraftDateTimeProvider {
    override fun now() = 200L
    override fun relativeDay(days: Int) = 200L + days * 100L
    override fun date(year: Int, month: Int, day: Int) = if (month in 1..12 && day in 1..BudgetCalendar.daysInMonth(year, month)) year * 10000L + month * 100L + day else null
}

@OptIn(ExperimentalCoroutinesApi::class)
class DraftAndMoneyTest {
    @Test fun offlineAcknowledgementTimeoutUnlocksAndRetriesTheSameDraft() = runTest {
        var online = false
        val ids = mutableListOf<String>()
        val presenter = TransactionDraftPresenter(this,LocalTransactionTextAnalyzer(DraftTestClock),MutableStateFlow(DefaultCategories),
            { ids += it.id; if (!online) awaitCancellation() else Result.success(true) }, writeTimeoutMillis=100L)
        presenter.setUserId("u"); presenter.analyze("mua đồ ăn 50k tiền mặt"); runCurrent()
        val id=presenter.state.value.draft!!.id
        presenter.confirm(); runCurrent(); advanceTimeBy(101); runCurrent()
        assertFalse(presenter.state.value.isSaving); assertNull(presenter.state.value.savedDraftId); assertEquals(id,presenter.state.value.draft?.id)
        online=true; presenter.confirm(); runCurrent()
        assertEquals(listOf(id,id),ids); assertEquals(id,presenter.state.value.savedDraftId); presenter.close()
    }
    @Test fun fakeSpeechResultPermissionAndDisposeUseTheSameTextParser() {
        val gate = VoiceInputGate()
        assertNull(gate.begin("u", MicrophonePermission.DENIED))
        assertNull(gate.begin("u", MicrophonePermission.PERMANENTLY_DENIED))
        val request = gate.begin("u", MicrophonePermission.GRANTED)
        val speech = gate.result(request, "u", " mua đồ ăn 50k tiền mặt ")
        assertEquals(50000L, TransactionDraftParser(DraftTestClock).parse(speech!!, "d", "u", DefaultCategories).amount)
        assertNull(gate.result(request, "other", speech))
        gate.cancel(); assertNull(gate.result(request, "u", speech))
    }
    @Test fun falseRepositoryResultNeverReportsSavedAndLateWriteCannotLeak() = runTest {
        val categories = MutableStateFlow(DefaultCategories)
        val presenter = TransactionDraftPresenter(this, LocalTransactionTextAnalyzer(DraftTestClock), categories, { Result.success(false) })
        presenter.setUserId("u"); presenter.analyze("mua đồ ăn 50k tiền mặt"); runCurrent(); presenter.confirm(); runCurrent()
        assertNull(presenter.state.value.savedDraftId); assertNotNull(presenter.state.value.draft); presenter.close()
        val gate = CompletableDeferred<Unit>()
        val delayed = TransactionDraftPresenter(this, LocalTransactionTextAnalyzer(DraftTestClock), categories, { withContext(NonCancellable) { gate.await(); Result.success(true) } })
        delayed.setUserId("u"); delayed.analyze("mua đồ ăn 50k tiền mặt"); runCurrent(); delayed.confirm(); runCurrent()
        delayed.setUserId("b"); gate.complete(Unit); runCurrent()
        assertNull(delayed.state.value.savedDraftId); assertNull(delayed.state.value.draft); delayed.close()
    }
    @Test fun moneyRoundTripTypingDeletingAndPaste() {
        for (raw in listOf("", "0", "1", "10", "100", "1000", "10000", "100000", "1000000", "9007199254740991")) {
            assertEquals(raw, MoneyInput.raw(MoneyInput.format(raw)))
            for (offset in 0..raw.length) {
                val formatted = MoneyInput.originalToFormatted(raw, offset)
                assertEquals(offset, MoneyInput.formattedToOriginal(raw, formatted))
            }
        }
        assertEquals("1.000.000", MoneyInput.format("1000000"))
        for (paste in listOf("1.000.000", "1,000,000", "1 000 000", "1\u00a0000\u00a0000")) assertEquals(1000000L, MoneyInput.amount(paste))
        for (invalid in listOf("-1", "abc", "10k", "9007199254740992", "999999999999999999999")) assertNull(MoneyInput.raw(invalid))
        assertEquals("100", MoneyInput.raw("1000".dropLast(1)))
        assertEquals(MoneyInput.Edit("12000034", 6, 6), MoneyInput.edit("12.000.034", 8))
        assertEquals(MoneyInput.Edit("1234", 2, 2), MoneyInput.edit("12 34", 3))
    }
    @Test fun everyFormattedCursorOffsetIsValidAndMonotonic() {
        val raw = "1000000"
        val offsets = (0..MoneyInput.format(raw).length).map { MoneyInput.formattedToOriginal(raw, it) }
        assertTrue(offsets.all { it in 0..raw.length })
        assertEquals(offsets.sorted(), offsets)
        assertEquals(0, offsets.first()); assertEquals(raw.length, offsets.last())
    }
    @Test fun fallbackVietnameseAmountsCategoriesIncomeAndDates() {
        val parser = TransactionDraftParser(DraftTestClock)
        for (input in listOf("Hôm nay tôi mua đồ ăn hết 50 nghìn", "mua đồ ăn 50k")) {
            val draft = parser.parse(input, "stable", "u", DefaultCategories)
            assertEquals(50000L, draft.amount); assertEquals("Chi", draft.type); assertEquals("Ăn uống", draft.category)
            assertEquals(200L, draft.timestamp); assertNull(draft.paymentMethod)
            assertTrue(DraftField.PAYMENT_METHOD in draft.missingFields)
        }
        for (input in listOf("nhận lương 1tr hôm qua chuyển khoản", "nhận lương 1 triệu hôm qua chuyển khoản")) {
            val draft = parser.parse(input, "stable", "u", DefaultCategories)
            assertEquals(1000000L, draft.amount); assertEquals("Thu", draft.type); assertEquals("Lương", draft.category)
            assertEquals(100L, draft.timestamp); assertEquals("Chuyển khoản", draft.paymentMethod)
        }
    }
    @Test fun ambiguousMissingUnknownCategoryAndOverflowNeedCorrection() {
        val parser = TransactionDraftParser(DraftTestClock)
        assertNull(parser.parse("mua đồ ăn", "d", "u", DefaultCategories).amount)
        assertNull(parser.parse("mua đồ ăn 50k hoặc 60k", "d", "u", DefaultCategories).amount)
        assertNull(parser.parse("50k", "d", "u", DefaultCategories).type)
        assertNull(parser.parse("mua đồ ăn 50k", "d", "u", emptyList()).category)
        assertNull(parser.parse("mua đồ ăn 999999999999999999tr", "d", "u", DefaultCategories).amount)
        assertNull(parser.parse("mua đồ ăn -50k", "d", "u", DefaultCategories).amount)
        assertNull(parser.parse("mua đồ ăn 50k và đổ xăng 50k", "d", "u", DefaultCategories).amount)
        assertEquals(1500000L, parser.parse("mua đồ ăn 1,5tr", "d", "u", DefaultCategories).amount)
    }
    @Test fun draftConfirmLocksDuplicateAndRetriesWithSameIdAfterFailure() = runTest {
        var writes = 0
        var fail = true
        val ids = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val presenter = TransactionDraftPresenter(this, LocalTransactionTextAnalyzer(DraftTestClock), MutableStateFlow(DefaultCategories), {
            writes++; ids += it.id
            if (fail) Result.failure(IllegalStateException()) else { gate.await(); Result.success(true) }
        })
        presenter.setUserId("u"); presenter.analyze("mua đồ ăn 50k tiền mặt"); runCurrent()
        assertEquals(0, writes)
        val id = presenter.state.value.draft!!.id
        presenter.confirm(); runCurrent()
        assertEquals(id, presenter.state.value.draft?.id); assertNull(presenter.state.value.savedDraftId)
        fail = false; presenter.confirm(); presenter.confirm(); runCurrent(); assertEquals(2, writes)
        gate.complete(Unit); runCurrent(); presenter.confirm(); runCurrent()
        assertEquals(2, writes); assertEquals(listOf(id, id), ids); assertEquals(id, presenter.state.value.savedDraftId)
        presenter.close()
    }
    @Test fun incompleteDraftNeverWritesOrReportsSuccess() = runTest {
        var writes = 0
        val presenter = TransactionDraftPresenter(this, LocalTransactionTextAnalyzer(DraftTestClock), MutableStateFlow(DefaultCategories), { writes++; Result.success(false) })
        presenter.setUserId("u"); presenter.analyze("mua đồ ăn"); runCurrent(); presenter.confirm(); runCurrent()
        assertEquals(0, writes); assertNull(presenter.state.value.savedDraftId)
        presenter.close()
    }
    @Test fun uidChangeAndDisposeRejectLateAnalysisAndWriteCallbacks() = runTest {
        val gate = CompletableDeferred<Unit>()
        val analyzer = object : TransactionTextAnalyzer {
            override suspend fun analyze(text: String, draftId: String, userId: String, categories: List<Category>) = withContext(NonCancellable) {
                gate.await(); TransactionDraftParser(DraftTestClock).parse(text, draftId, userId, categories)
            }
        }
        val presenter = TransactionDraftPresenter(this, analyzer, MutableStateFlow(DefaultCategories), { Result.success(true) })
        presenter.setUserId("a"); presenter.analyze("mua đồ ăn 50k tiền mặt"); runCurrent()
        presenter.setUserId("b"); gate.complete(Unit); runCurrent()
        assertNull(presenter.state.value.draft); assertEquals("b", presenter.state.value.userId)
        presenter.close(); assertNull(presenter.state.value.userId)
    }
}
