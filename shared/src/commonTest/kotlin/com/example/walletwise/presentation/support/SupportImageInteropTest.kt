package com.example.walletwise.presentation.support

import com.example.walletwise.data.repository.CallbackSupportRepository
import com.example.walletwise.domain.model.*
import com.example.walletwise.domain.repository.*
import com.example.walletwise.domain.result.RepositoryResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SupportImageInteropTest {
    @Test
    fun portableUrlPolicyAcceptsUploadPathAndHttpsButRejectsLocalSchemes() {
        assertTrue(SupportImageUrlPolicy.isRelativeUploadPath("/uploads/photo-1.jpg"))
        assertTrue(SupportImageUrlPolicy.isPortable("https://cdn.example.test/a.webp"))
        listOf("content://picker/a", "file:///tmp/a.jpg", "blob:abc", "javascript:alert(1)",
            "/uploads/a/b.jpg", "/uploads/..", "/uploads/...", "https://user@example.test/a.jpg",
            "https://example.test:/a.jpg", "https://example.test:99999/a.jpg",
            " /uploads/photo.jpg").forEach {
            assertFalse(SupportImageUrlPolicy.isPortable(it), it)
        }
        assertEquals(
            "http://127.0.0.1:3000/uploads/photo-1.jpg",
            SupportImageUrlPolicy.resolve("/uploads/photo-1.jpg", "http://127.0.0.1:3000", allowLocalHttp = true)
        )
        assertNull(SupportImageUrlPolicy.resolve("/uploads/photo-1.jpg", "http://127.0.0.1:3000"))
        assertNull(SupportImageUrlPolicy.resolve("file:///tmp/a.jpg", "https://api.example.test"))
    }

    @Test
    fun previewAndLegacyTextFallbackAreStable() {
        val text = SupportMessage("t", "u", SupportSenderRole.USER, "hello")
        assertEquals("hello", SupportMessagePresentation.preview(text))
        assertNull(SupportMessagePresentation.imageReference(text))

        val image = text.copy(
            id = "i", content = "[image:/uploads/a.jpg]\ncaption",
            messageType = SupportMessageType.IMAGE, imageUrl = "/uploads/a.jpg"
        )
        assertEquals("[Hình ảnh]", SupportMessagePresentation.preview(image))
        assertEquals("caption", SupportMessagePresentation.text(image))
        assertEquals("/uploads/a.jpg", SupportMessagePresentation.imageReference(image))
    }

    @Test
    fun callbackRepositoryMapsStructuredAndLegacyImagesWithoutTrustingLocalUri() = runTest {
        val service = FakeSupportService()
        val repository = CallbackSupportRepository(service)
        val deferred = backgroundScope.async {
            val result = repository.observeMessages("owner").first { it is RepositoryResult.Success }
            (result as RepositoryResult.Success).value
        }
        runCurrent()
        service.observer!!.supportChanged(
            listOf(
                document("structured", "image", "/uploads/one.jpg"),
                document("legacy", "text", null, "[image:https://cdn.example.test/two.webp]\nlegacy"),
                document("local", "image", "content://picker/private")
            ),
            fromCache = false,
            failure = null
        )
        val snapshot = deferred.await()
        assertEquals(3, snapshot.messages.size)
        assertTrue(snapshot.messages.all { it.messageType == SupportMessageType.IMAGE })
        assertEquals("legacy", SupportMessagePresentation.text(snapshot.messages.first { it.id == "legacy" }))
        assertFalse(SupportImageUrlPolicy.isPortable(snapshot.messages.first { it.id == "local" }.imageUrl))
    }

    @Test
    fun callbackRepositoryRejectsOutgoingLocalImageBeforeNativeWrite() = runTest {
        val service = FakeSupportService()
        val repository = CallbackSupportRepository(service)
        val message = SupportMessage(
            id = "one", senderId = "owner", senderRole = SupportSenderRole.USER,
            content = "[image:content://picker/private]", clientRequestId = "one",
            messageType = SupportMessageType.IMAGE, imageUrl = "content://picker/private",
            mimeType = "image/jpeg", fileName = "one.jpg"
        )
        assertTrue(repository.send(AuthSession("owner", "owner@example.test"), message) is RepositoryResult.Failure)
        assertEquals(0, service.sendCount)
    }

    @Test
    fun pickerCancelAndDoubleOpenNeverCreateASelection() {
        val picker = FakePicker()
        val presenter = SupportImagePickerPresenter(picker) { "selection-1" }
        presenter.pick()
        presenter.pick()
        assertEquals(1, picker.openCount)
        picker.completion!!.imagePicked(null, SupportImagePickFailure.CANCELLED)
        assertFalse(presenter.state.value.selecting)
        assertNull(presenter.state.value.image)
        assertNull(presenter.state.value.error)
    }

    @Test
    fun encodedPickerMetadataStaysNativeAndUploadRemainsAuthBlocked() {
        val picker = FakePicker()
        val presenter = SupportImagePickerPresenter(picker) { "selection-1" }
        presenter.pick()
        picker.completion!!.imagePicked(
            PreparedSupportImage("selection-1", "photo.jpg", "image/jpeg", 500_000, 1600, 900),
            null
        )
        assertEquals(500_000, presenter.state.value.image?.byteCount)
        assertContains(presenter.state.value.error.orEmpty(), "Không thể kết nối")
        presenter.clear()
        assertNull(presenter.state.value.image)
    }

    private fun document(
        id: String,
        type: String,
        imageUrl: String?,
        content: String = "[image:${imageUrl.orEmpty()}]"
    ) = SupportMessageDocument(
        documentId = id,
        senderId = "owner",
        senderRole = "user",
        content = content,
        createdAtMilliseconds = 100,
        hasCreatedAt = true,
        clientRequestId = id,
        pendingWrites = false,
        messageType = type,
        imageUrl = imageUrl,
        mimeType = "image/jpeg",
        fileName = "$id.jpg"
    )

    private class FakeCancellation : SupportCancellation {
        var cancelled = false
        override fun cancel() { cancelled = true }
    }

    private class FakeSupportService : CallbackSupportService {
        var observer: SupportSnapshotObserver? = null
        var sendCount = 0
        override fun observeMessages(userId: String, observer: SupportSnapshotObserver): SupportCancellation {
            this.observer = observer
            return FakeCancellation()
        }
        override fun send(session: AuthSession, message: SupportMessage, completion: SupportWriteCompletion): SupportCancellation {
            sendCount++
            completion.supportCompleted(null)
            return FakeCancellation()
        }
    }

    private class FakePicker : CallbackSupportImagePicker {
        var openCount = 0
        var completion: SupportImagePickCompletion? = null
        override fun pick(selectionId: String, completion: SupportImagePickCompletion): SupportCancellation {
            openCount++
            this.completion = completion
            return FakeCancellation()
        }
    }
}
