package com.example.walletwise.presentation.transaction

import com.example.walletwise.data.mapper.FirestoreTimestampValue
import com.example.walletwise.data.repository.CallbackTransactionRepository
import com.example.walletwise.domain.repository.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CallbackTransactionSessionTest {
    @Test fun loadingToEmptyReadsLegacyOnceOnlyAfterServerEmpty() = runTest {
        val service = FakeTransactionService()
        val owner = TransactionSessionController(this, CallbackTransactionRepository(service))
        owner.setUserId("a"); runCurrent()
        assertTrue(owner.state.value.isLoading)
        service.snapshot(emptyList(), cached = true); runCurrent()
        assertEquals(0, service.legacyReads)
        service.snapshot(emptyList()); service.snapshot(emptyList()); runCurrent()
        assertEquals(1, service.legacyReads)
        service.completeLegacy(emptyList()); runCurrent()
        assertFalse(owner.state.value.isLoading); assertTrue(owner.transactions.value.isEmpty())
        service.snapshot(emptyList()); runCurrent()
        assertEquals(1, service.legacyReads)
        owner.close(); runCurrent()
    }
    @Test fun primaryContentNeverReadsLegacyAndRepeatedSnapshotsDoNotDuplicate() = runTest {
        val service = FakeTransactionService()
        val owner = TransactionSessionController(this, CallbackTransactionRepository(service))
        owner.setUserId("a"); runCurrent()
        repeat(2) { service.snapshot(listOf(doc("x"), doc("x"))); runCurrent() }
        assertEquals(listOf("x"), owner.transactions.value.map { it.id })
        assertEquals(0, service.legacyReads); assertFalse(owner.state.value.isLoading)
        owner.close(); runCurrent()
    }
    @Test fun errorAndRetryRemoveOldListenerAndKeepOneNewListener() = runTest {
        val service = FakeTransactionService()
        val owner = TransactionSessionController(this, CallbackTransactionRepository(service))
        owner.setUserId("a"); runCurrent()
        service.fail(TransactionReadFailure.NETWORK); runCurrent()
        assertNotNull(owner.state.value.error); assertFalse(owner.state.value.isLoading)
        owner.refresh(); runCurrent()
        assertEquals(2, service.registrations); assertEquals(1, service.removals)
        assertTrue(owner.state.value.isLoading); assertNull(owner.state.value.error)
        service.snapshot(listOf(doc("ok"))); runCurrent()
        assertEquals("ok", owner.transactions.value.single().id)
        owner.close(); runCurrent(); assertEquals(2, service.removals)
    }
    @Test fun sameUidDoesNotRestartAndSwitchClearsBeforeNewCallback() = runTest {
        val service = FakeTransactionService()
        val owner = TransactionSessionController(this, CallbackTransactionRepository(service))
        owner.setUserId("a"); runCurrent(); service.snapshot(listOf(doc("a"))); runCurrent()
        owner.setUserId("a"); runCurrent(); assertEquals(1, service.registrations)
        val stale = service.observer!!
        owner.setUserId("b")
        assertTrue(owner.transactions.value.isEmpty()); assertEquals("b", owner.state.value.userId)
        runCurrent(); assertEquals(1, service.removals)
        stale.changed(listOf(doc("stale")), false, null); runCurrent()
        assertTrue(owner.transactions.value.isEmpty())
        service.snapshot(listOf(doc("b"))); runCurrent()
        assertEquals("b", owner.transactions.value.single().userId)
        assertEquals("b", owner.transactions.value.single().id)
        owner.close(); runCurrent()
    }
    @Test fun disposeIsIdempotentAndRejectsLateSameUidCallbacksAndRestart() = runTest {
        val service = FakeTransactionService()
        val owner = TransactionSessionController(this, CallbackTransactionRepository(service))
        owner.setUserId("a"); runCurrent(); val stale = service.observer!!
        owner.close(); owner.close(); runCurrent()
        stale.changed(listOf(doc("late")), false, null); owner.setUserId("a"); runCurrent()
        assertEquals(TransactionSessionState(), owner.state.value)
        assertEquals(1, service.removals); assertEquals(1, service.registrations)
    }
    @Test fun lateLegacyCannotOverwritePrimaryAndRefreshReadsLegacyAgain() = runTest {
        val service = FakeTransactionService()
        val owner = TransactionSessionController(this, CallbackTransactionRepository(service))
        owner.setUserId("a"); runCurrent(); service.snapshot(emptyList()); runCurrent()
        service.snapshot(listOf(doc("primary"))); runCurrent()
        service.completeLegacy(listOf(doc("legacy"))); runCurrent()
        assertEquals("primary", owner.transactions.value.single().id)
        owner.refresh(); runCurrent(); service.snapshot(emptyList()); runCurrent()
        assertEquals(2, service.legacyReads)
        service.completeLegacy(listOf(doc("new-legacy"))); runCurrent()
        assertEquals("new-legacy", owner.transactions.value.single().id)
        owner.close(); runCurrent()
    }
    @Test fun snapshotErrorNeverTriggersLegacyAndInvalidDocumentDoesNotDropValidNeighbors() = runTest {
        val service = FakeTransactionService()
        val owner = TransactionSessionController(this, CallbackTransactionRepository(service))
        owner.setUserId("a"); runCurrent(); service.fail(TransactionReadFailure.PERMISSION_DENIED); runCurrent()
        assertEquals(0, service.legacyReads)
        service.snapshot(listOf(doc(""), doc("good"))); runCurrent()
        assertEquals("good", owner.transactions.value.single().id)
        assertNull(owner.state.value.error)
        owner.close(); runCurrent()
    }
    @Test fun orderingIsStableAndTimestampBridgeFallsBackSafely() = runTest {
        val service = FakeTransactionService()
        val owner = TransactionSessionController(this, CallbackTransactionRepository(service))
        owner.setUserId("a"); runCurrent()
        service.snapshot(listOf(doc("b", 100), doc("a", 100), doc("old", 0).copy(legacyTimestamp = FirestoreTimestampValue(1, -1)))); runCurrent()
        assertEquals(listOf("a", "b", "old"), owner.transactions.value.map { it.id })
        assertEquals(0L, owner.transactions.value.last().timestamp)
        assertEquals(1500L, doc("legacy").copy(legacyTimestamp = FirestoreTimestampValue(1, 500_000_000)).toTransaction("a").timestamp)
        owner.close(); runCurrent()
    }
    @Test fun amountsAndNoImageUseSharedMapperAndPresentation() = runTest {
        assertEquals(12.0, com.example.walletwise.data.mapper.FirestoreWireMapper.transactionFromMap("long", "a", mapOf("amount" to 12L)).amount)
        assertEquals(12.5, com.example.walletwise.data.mapper.FirestoreWireMapper.transactionFromMap("double", "a", mapOf("amount" to 12.5)).amount)
        val service = FakeTransactionService()
        val owner = TransactionSessionController(this, CallbackTransactionRepository(service))
        val presenter = TransactionListPresenter(this, owner.state, TestDateTime)
        owner.setUserId("a"); runCurrent()
        service.snapshot(listOf(doc("blank").copy(imageUrl = "  "), doc("image").copy(imageUrl = "https://example.invalid/broken"))); runCurrent()
        assertFalse(presenter.state.value.rows.first { it.id == "blank" }.hasImage)
        assertEquals("", presenter.state.value.rows.first { it.id == "blank" }.imageUrl)
        assertTrue(presenter.state.value.rows.first { it.id == "image" }.hasImage)
        presenter.close(); owner.close(); runCurrent()
    }
    @Test fun homeTotalsSelectionLogoutAndUserSwitchAreIsolated() = runTest {
        val authService = FakeHomeAuthService()
        val auth = com.example.walletwise.presentation.auth.ConnectedAuthPresenter(authService)
        val service = FakeTransactionService()
        val home = TransactionHomeSession(this, auth, CallbackTransactionRepository(service), TestDateTime)
        authService.user("a"); runCurrent()
        service.snapshot(listOf(doc("income").copy(type = "Thu", amount = 100.0), doc("expense").copy(type = "Chi", amount = 30.0))); runCurrent()
        assertEquals(100.0, home.snapshot.totalIncome); assertEquals(30.0, home.snapshot.totalExpense); assertEquals(70.0, home.snapshot.balance)
        home.select("income"); assertEquals("income", home.snapshot.selectedTransactionId)
        home.dismissDetail(); assertNull(home.snapshot.selectedTransactionId)
        authService.user("b")
        assertEquals("b", home.snapshot.userId); assertTrue(home.snapshot.list.rows.isEmpty()); assertEquals(0.0, home.snapshot.balance)
        runCurrent(); service.snapshot(listOf(doc("b").copy(type = "Chi", amount = 9.0))); runCurrent()
        assertEquals(-9.0, home.snapshot.balance); assertEquals(listOf("b"), home.snapshot.list.rows.map { it.id })
        home.logout(); assertNull(home.snapshot.userId); assertTrue(home.snapshot.list.rows.isEmpty())
        home.dispose(); home.dispose(); auth.dispose(); runCurrent()
        assertEquals(TransactionHomeState(), home.snapshot)
    }
    @Test fun controllerCleanupDoesNotLogoutAuthOrAnotherController() = runTest {
        val authService = FakeHomeAuthService()
        val auth = com.example.walletwise.presentation.auth.ConnectedAuthPresenter(authService)
        val home = TransactionHomeSession(this, auth, CallbackTransactionRepository(FakeTransactionService()), TestDateTime)
        val otherAuthService = FakeHomeAuthService()
        val otherAuth = com.example.walletwise.presentation.auth.ConnectedAuthPresenter(otherAuthService)
        val otherService = FakeTransactionService()
        val otherHome = TransactionHomeSession(this, otherAuth, CallbackTransactionRepository(otherService), TestDateTime)
        authService.user("a"); otherAuthService.user("b"); runCurrent()
        otherService.snapshot(listOf(doc("other"))); runCurrent()
        home.dispose(); runCurrent()
        assertEquals("b", otherHome.snapshot.userId)
        assertEquals(listOf("other"), otherHome.snapshot.list.rows.map { it.id })
        otherHome.dispose(); otherAuth.dispose(); runCurrent()
        assertEquals("a", auth.snapshot.user?.uid); assertEquals(0, authService.logoutCount)
        auth.dispose()
    }
    @Test fun sdkUserBeforeLoginCompletionKeepsOneListenerAcrossFormLoadingTransitions() = runTest {
        val authService = FakeHomeAuthService()
        val auth = com.example.walletwise.presentation.auth.ConnectedAuthPresenter(authService)
        val service = FakeTransactionService()
        val home = TransactionHomeSession(this, auth, CallbackTransactionRepository(service), TestDateTime)
        auth.onEmailChanged("a@example.invalid"); auth.onPasswordChanged("StrongPassword123!"); auth.submit()
        authService.user("a"); runCurrent()
        assertEquals(1, service.registrations)
        assertNull(home.snapshot.userId)
        authService.loginCompletion!!.complete(auth.snapshot.user, null); runCurrent()
        assertTrue(auth.snapshot.isAuthenticated)
        assertEquals("a", home.snapshot.userId)
        assertEquals(1, service.registrations); assertEquals(0, service.removals)
        home.dispose(); auth.dispose(); runCurrent()
    }
}

private fun doc(id: String, timestamp: Long = 100) = TransactionDocument(id, "Thu", 10.0, "Test", "Tiền mặt", "", timestamp, null, "")
private object TestDateTime : TransactionDateTimeProvider { override fun localDateTime(epochMilliseconds: Long): TransactionLocalDateTime? = null }
private class FakeTransactionService : CallbackTransactionService {
    var observer: TransactionSnapshotObserver? = null
    var completion: TransactionReadCompletion? = null
    var registrations = 0; var removals = 0; var legacyReads = 0
    override fun observePrimary(userId: String, observer: TransactionSnapshotObserver): TransactionCancellation {
        this.observer = observer; registrations++
        return object : TransactionCancellation { var active = true; override fun cancel() { if (active) { active = false; removals++ } } }
    }
    override fun readLegacy(userId: String, completion: TransactionReadCompletion): TransactionCancellation {
        this.completion = completion; legacyReads++
        return object : TransactionCancellation { override fun cancel() {} }
    }
    fun snapshot(documents: List<TransactionDocument>, cached: Boolean = false) { observer!!.changed(documents, cached, null) }
    fun completeLegacy(documents: List<TransactionDocument>) { completion!!.complete(documents, null) }
    fun fail(failure: TransactionReadFailure) { observer!!.changed(null, false, failure) }
}
private class FakeHomeAuthService : CallbackAuthService {
    var observer: AuthStateObserver? = null; var logoutCount = 0
    var loginCompletion: AuthCompletion? = null
    fun user(id: String) { observer!!.changed(com.example.walletwise.domain.model.AuthUser.create(id, "$id@example.invalid", "Member $id", false)) }
    override fun observe(observer: AuthStateObserver): AuthCancellation { this.observer = observer; observer.changed(null); return object : AuthCancellation { override fun cancel() {} } }
    override fun logout(): AuthFailure? { logoutCount++; observer!!.changed(null); return null }
    override fun login(email: String, password: String, completion: AuthCompletion): AuthCancellation {
        loginCompletion = completion
        return object : AuthCancellation { override fun cancel() {} }
    }
    override fun register(email: String, password: String, displayName: String, completion: AuthCompletion): AuthCancellation = error("unused")
    override fun resetPassword(email: String, completion: AuthCompletion): AuthCancellation = error("unused")
}
