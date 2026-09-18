package com.example.walletwise.presentation.support

import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.repository.SupportRepository
import com.example.walletwise.domain.result.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SupportPresentersTest {
    private val account = AuthSession("one", "one@example.test")
    private class Repository : SupportRepository {
        var active = 0
        var opened = 0
        var observedUid: String? = null
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        val requests = mutableListOf<Pair<AuthSession, SupportMessage>>()
        val snapshots = MutableSharedFlow<RepositoryResult<SupportSnapshot>>(extraBufferCapacity = 8)
        override fun observeMessages(userId: String) = flow {
            active++; opened++; observedUid = userId
            try { emit(RepositoryResult.Success(SupportSnapshot(emptyList()))); emitAll(snapshots) }
            finally { active-- }
        }
        override suspend fun send(session: AuthSession, message: SupportMessage): RepositoryResult<Unit> {
            requests += session to message
            gate?.await()
            return if (fail) RepositoryResult.Failure(RepositoryError(RepositoryErrorCode.NETWORK, "offline"))
            else RepositoryResult.Success(Unit)
        }
    }
    private fun TestScope.chat(repo: Repository): SupportChatPresenter {
        var ids = 0
        return SupportChatPresenter(this, repo, { "id-${++ids}" }, { 999L }).also { it.bind(account); it.enter() }
    }

    @Test fun opensCorrectUidOnceAndLeavesWithoutListener() = runTest {
        val repo = Repository(); val p = chat(repo); runCurrent()
        p.enter(); p.bind(account.copy(email = "new@example.test")); runCurrent()
        assertEquals("one", repo.observedUid); assertEquals(1, repo.opened); assertEquals(1, repo.active)
        p.leave(); runCurrent(); assertEquals(0, repo.active)
        p.enter(); runCurrent(); assertEquals(1, repo.active); assertEquals(2, repo.opened)
        p.close(); runCurrent(); assertEquals(0, repo.active)
    }
    @Test fun emptyTextBlockedAndSuccessTrimsWithoutInventingServerTime() = runTest {
        val repo = Repository(); val p = chat(repo); runCurrent()
        p.input(" \t\n"); p.submit(); runCurrent(); assertTrue(repo.requests.isEmpty())
        p.input("  Xin hỗ trợ  \n"); p.submit(); runCurrent()
        assertEquals("Xin hỗ trợ", repo.requests.single().second.content)
        assertEquals(SupportDelivery.SENT, p.state.value.messages.single().delivery)
        assertNull(p.state.value.messages.single().createdAt)
        assertEquals("Đã gửi", p.state.value.status); p.close()
    }
    @Test fun failedRetryKeepsStableIdAndDoubleSubmitMakesOneRequest() = runTest {
        val repo = Repository().apply { fail = true; gate = CompletableDeferred() }
        val p = chat(repo); runCurrent(); p.input("test"); p.submit(); p.submit(); runCurrent()
        assertEquals(1, repo.requests.size)
        repo.gate!!.complete(Unit); runCurrent()
        val id = p.state.value.messages.single().id
        assertEquals(SupportDelivery.FAILED, p.state.value.messages.single().delivery)
        repo.fail = false; p.retry(id); p.retry(id); runCurrent()
        assertEquals(listOf(id, id), repo.requests.map { it.second.clientRequestId })
        assertEquals(1, p.state.value.messages.size); assertEquals(SupportDelivery.SENT, p.state.value.messages.single().delivery); p.close()
    }
    @Test fun repeatedSnapshotsDeduplicateAndSortByServerTimeThenId() = runTest {
        val repo = Repository(); val p = chat(repo); runCurrent()
        val first = SupportMessage("a", "one", SupportSenderRole.USER, "first", 100)
        val second = first.copy(id = "b", content = "second", createdAt = 200)
        repeat(2) { repo.snapshots.emit(RepositoryResult.Success(SupportSnapshot(listOf(second, first, second)))); runCurrent() }
        assertEquals(listOf("a", "b"), p.state.value.messages.map { it.id })
        assertFalse(p.state.value.status.contains("trực tuyến")); p.close()
    }
    @Test fun snapshotReplacesPendingWithoutDuplicationAndAgentStatusRequiresRealMessage() = runTest {
        val repo = Repository().apply { gate = CompletableDeferred() }; val p = chat(repo); runCurrent()
        assertEquals("Chưa có nhân viên phản hồi", p.state.value.status)
        p.input("test"); p.submit(); runCurrent()
        val sent = p.state.value.messages.single().copy(createdAt = 50, delivery = SupportDelivery.SENT)
        repo.snapshots.emit(RepositoryResult.Success(SupportSnapshot(listOf(sent)))); runCurrent()
        repo.gate!!.complete(Unit); runCurrent(); assertEquals(1, p.state.value.messages.size)
        assertEquals(50, p.state.value.messages.single().createdAt)
        repo.snapshots.emit(RepositoryResult.Success(SupportSnapshot(listOf(sent, sent.copy(id = "agent", senderId = "staff", senderRole = SupportSenderRole.AGENT))))); runCurrent()
        assertEquals("Nhân viên đã phản hồi", p.state.value.status); p.close()
    }
    @Test fun uidChangeAndLogoutClearMessagesInputAndCancelOldSend() = runTest {
        val repo = Repository().apply { gate = CompletableDeferred() }; val p = chat(repo); runCurrent()
        p.input("private old message"); p.submit(); runCurrent(); p.input("private draft")
        p.bind(AuthSession("two", "two@example.test")); runCurrent()
        assertEquals("two", repo.observedUid); assertEquals(1, repo.active)
        assertTrue(p.state.value.messages.isEmpty()); assertEquals("", p.state.value.input); assertFalse(p.state.value.sending)
        repo.gate!!.complete(Unit); runCurrent(); assertTrue(p.state.value.messages.isEmpty())
        p.bind(null); runCurrent(); assertEquals(0, repo.active); assertNull(p.state.value.userId)
        assertTrue(p.state.value.messages.isEmpty()); p.close()
    }
    @Test fun errorsAndCacheAreExplicitAndReconnectStillHasOneListener() = runTest {
        val repo = Repository(); val p = chat(repo); runCurrent()
        repo.snapshots.emit(RepositoryResult.Success(SupportSnapshot(emptyList(), true))); runCurrent(); assertTrue(p.state.value.fromCache)
        repo.snapshots.emit(RepositoryResult.Failure(RepositoryError(RepositoryErrorCode.PERMISSION_DENIED, "permission denied"))); runCurrent()
        assertEquals("permission denied", p.state.value.error); p.reconnect(); runCurrent(); assertEquals(1, repo.active); p.close()
    }
    @Test fun leavingPendingKeepsRetryIdAndCannotSubmitHiddenScreen() = runTest {
        val repo = Repository().apply { gate = CompletableDeferred() }; val p = chat(repo); runCurrent()
        p.input("test"); p.submit(); runCurrent(); val id = p.state.value.messages.single().id
        p.leave(); runCurrent(); assertEquals(SupportDelivery.FAILED, p.state.value.messages.single().delivery)
        p.input("hidden"); p.submit(); runCurrent(); assertEquals(1, repo.requests.size)
        p.enter(); runCurrent(); repo.gate!!.complete(Unit); p.retry(id); runCurrent()
        assertEquals(listOf(id, id), repo.requests.map { it.second.id }); p.close()
    }
    @Test fun sendingAndFailedRetryStayVisibleAfterEarlierStaffReply() = runTest {
        val repo = Repository().apply { fail = true; gate = CompletableDeferred() }
        val p = chat(repo); runCurrent()
        repo.snapshots.emit(RepositoryResult.Success(SupportSnapshot(listOf(
            SupportMessage("staff-existing", "staff", SupportSenderRole.AGENT, "earlier reply", 100)
        )))); runCurrent()
        p.input("new request"); p.submit(); runCurrent()
        assertEquals("Đang gửi", p.state.value.status)
        repo.gate!!.complete(Unit); runCurrent()
        assertEquals("Gửi thất bại – Thử lại", p.state.value.status)
        val failed = p.state.value.messages.single { it.delivery == SupportDelivery.FAILED }
        repo.fail = false; p.retry(failed.id); runCurrent()
        assertEquals("Nhân viên đã phản hồi", p.state.value.status)
        assertEquals(listOf(failed.id, failed.id), repo.requests.map { it.second.clientRequestId })
        p.close()
    }

    @Test fun emailUsesAuthSessionAndResetsSensitiveFormOnUidChangeAndLogout() {
        val p = SupportEmailPresenter(); p.bind(account); p.subject("old subject"); p.description("old body")
        p.attachments(listOf(SupportAttachment("content://picked/one", "one.pdf", "application/pdf", 1024)))
        val oldGeneration = p.generation
        p.bind(account.copy(email = "updated@example.test")); assertEquals("updated@example.test", p.state.value.senderEmail)
        assertEquals("old subject", p.state.value.subject)
        p.bind(AuthSession("two", "two@example.test")); assertTrue(p.generation > oldGeneration)
        assertEquals("two@example.test", p.state.value.senderEmail); assertEquals("", p.state.value.description); assertTrue(p.state.value.attachments.isEmpty())
        p.bind(null); assertEquals("", p.state.value.senderEmail); assertFalse(p.state.value.validSender)
    }
    @Test fun emailRejectsMissingAuthEmailSubjectDescriptionAndWhitespace() {
        val p = SupportEmailPresenter()
        listOf("", " ", "invalid").forEach { p.bind(account.copy(email = it)); p.subject("test"); p.description("body"); assertNull(p.prepare()) }
        p.bind(account); p.subject(" \n"); p.description("body"); assertNull(p.prepare())
        p.subject("test"); p.description(" \t\n"); assertNull(p.prepare())
        p.subject("header\ninjection"); p.description("body"); assertNull(p.prepare())
    }
    @Test fun emailTrimsHasFixedRecipientAndDoesNotIncludeUnnecessaryUidOrCredentials() {
        val emailAccount = account.copy(userId = "private-auth-uid")
        val p = SupportEmailPresenter(); p.bind(emailAccount); p.subject("  title  "); p.description("  body\n  ")
        val draft = assertNotNull(p.prepare()); assertEquals(SupportContact.EMAIL, draft.recipient)
        assertEquals("title", draft.subject); assertEquals("Email tài khoản: one@example.test\n\nbody", draft.body)
        assertFalse(draft.body.contains(emailAccount.userId)); assertNull(p.state.value.error)
    }
    @Test fun lengthLimitsAndAttachmentsRejectUnsafeUnknownOversizedOrTooManyFiles() {
        val p = SupportEmailPresenter(); p.bind(account)
        p.subject("s".repeat(500)); p.description("d".repeat(9000))
        assertEquals(160, p.state.value.subject.length); assertEquals(8000, p.state.value.description.length)
        val file = SupportAttachment("content://picker/a", "test.pdf", "application/pdf", 1024)
        listOf(file.copy(uri = "file:///private/data"), file.copy(sizeBytes = -1), file.copy(sizeBytes = SupportContact.MAX_FILE_BYTES + 1), file.copy(mimeType = "application/x-executable")).forEach {
            assertNotNull(SupportEmailValidation.attachmentError(listOf(it)))
        }
        assertNotNull(SupportEmailValidation.attachmentError((1..4).map { file.copy(uri = "content://picker/$it") }))
        assertNotNull(SupportEmailValidation.attachmentError((1..3).map { file.copy(uri = "content://picker/$it", sizeBytes = SupportContact.MAX_FILE_BYTES) }))
        p.attachments(listOf(file, file)); assertEquals(1, p.state.value.attachments.size)
        p.attachments(listOf(file.copy(uri = "file:///unsafe"))); assertEquals(listOf(file), p.state.value.attachments)
        p.remove(file.uri); assertTrue(p.state.value.attachments.isEmpty())
    }
}
