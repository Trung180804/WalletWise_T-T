package com.example.walletwise.reliability

import android.app.Activity
import android.graphics.*
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.*
import androidx.activity.result.contract.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.walletwise.BuildConfig
import com.example.walletwise.data.image.*
import com.example.walletwise.data.repository.TransactionRepositoryImpl
import com.example.walletwise.domain.repository.ImageUploader
import com.example.walletwise.domain.model.ImageUpload
import com.example.walletwise.presentation.home.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Real Compose routes and ML Kit; external activity results are injected, never cloud OCR. */
class ReceiptAttachmentFlowRenderTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var model: TransactionViewModel
    private val store = ViewModelStore()
    private var uploads = 0
    private var uploadFails = false
    private var captures = 0
    private var picks = 0
    private var cancelled = false
    private var speech: String? = null
    private val file get() = File(context.cacheDir, "checkpoint_receipt_fixture.png")
    private val registry = object : ActivityResultRegistry() {
        @Suppress("UNCHECKED_CAST")
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            val result: Any? = when (contract) {
                is ActivityResultContracts.TakePicture -> {
                    captures++
                    if (!cancelled) context.contentResolver.openOutputStream(input as Uri)!!.use { receipt().compress(Bitmap.CompressFormat.PNG, 100, it) }
                    !cancelled
                }
                is ActivityResultContracts.PickVisualMedia -> { picks++; if (cancelled) null else Uri.fromFile(file) }
                is ActivityResultContracts.RequestPermission -> speech != null
                is ActivityResultContracts.StartActivityForResult -> ActivityResult(if (speech == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                    speech?.let { android.content.Intent().putStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS, arrayListOf(it)) })
                else -> throw AssertionError("Unexpected platform contract")
            }
            dispatchResult(requestCode, result as O)
        }
    }
    private val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry get() = registry }
    private fun receipt(): Bitmap {
        val bitmap = Bitmap.createBitmap(1400, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 65f }
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        listOf("QUAN AN TEST", date, "Subtotal 40.000", "VAT 10.000", "TOTAL 50.000", "TIEN MAT").forEachIndexed { i, text -> canvas.drawText(text, 70f, 100f + i * 125f, paint) }
        return bitmap
    }
    @Before fun prepare(): Unit = runBlocking {
        assertTrue(BuildConfig.USE_FIREBASE_EMULATOR)
        assertEquals("demo-walletwise", FirebaseApp.getInstance().options.projectId)
        val auth = FirebaseAuth.getInstance()
        auth.signOut(); auth.signInAnonymously().await()
        val uid = requireNotNull(auth.currentUser).uid
        assertTrue(com.example.walletwise.data.repository.CategoryRepositoryImpl().ensureDefaultCategories(uid,
            com.example.walletwise.domain.model.DefaultCategories) is com.example.walletwise.domain.result.RepositoryResult.Success)
        receipt().let { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
        val repo = TransactionRepositoryImpl()
        val writer = AndroidTransactionWriter(repo, ContentResolverImageReader(), object : ImageUploader {
            override suspend fun upload(image: ImageUpload): Result<String> {
                uploads++
                if (uploadFails) return Result.failure(IllegalStateException("Synthetic upload failure"))
                return Result.success("https://invalid.example/synthetic-attachment.png")
            }
        })
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            model = TransactionViewModel(repo, writer)
            model.attachDraftContext(context)
            store.put("checkpoint", model)
        }
        compose.waitUntil(20000) { model.categorySessionState.value.userId == uid && !model.categorySessionState.value.isLoading &&
            model.categories.value.count { it.type == "Chi" } >= 8 && model.transactionSessionState.value.userId == uid }
    }
    @After fun cleanup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        file.delete()
    }
    private fun renderAI() { compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { MaterialTheme(colorScheme = lightColorScheme()) { AITransactionSheet(model) } } } }
    @Test fun receiptInSmallAvailableHeightKeepsAllActionsAndInputVisible() {
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { MaterialTheme(colorScheme = lightColorScheme()) {
            Box(Modifier.width(320.dp).height(260.dp)) { AITransactionSheet(model) }
        } } }
        compose.onNodeWithContentDescription("Chụp hóa đơn").performClick()
        compose.waitUntil(45000) { model.aiDraftState.value.savedDraftId != null }
        listOf("Chụp hóa đơn", "Chọn hóa đơn từ thư viện", "Nhập giọng nói").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        compose.onNodeWithTag("assistant-input").assertIsDisplayed()
        compose.onNodeWithText("Ghi giao dịch").assertIsDisplayed()
        assertEquals(1, documents().size)
        assertTrue(compose.onNodeWithTag("assistant-sheet").fetchSemanticsNode().boundsInRoot.height <= 260 * compose.density.density + 1f)
    }
    @Test fun voiceResultAutomaticallySavesThroughTheSameWriteUseCase() {
        speech = "Hôm nay mua đồ ăn 50 nghìn"
        renderAI()
        compose.onNodeWithContentDescription("Nhập giọng nói").performClick()
        compose.waitUntil(20000) { model.aiDraftState.value.savedDraftId != null }
        assertEquals(com.example.walletwise.domain.model.DraftSource.VOICE, model.aiDraftState.value.draft?.source)
        assertEquals(1, documents().size); assertEquals(50000.0, documents().single().getDouble("amount"))
        compose.onNodeWithText("Xác nhận lưu").assertDoesNotExist(); assertEquals(0, uploads)
    }
    @Test fun compactEmptySheetExpandsForReceiptAndTextSavesWithoutConfirmation() {
        renderAI()
        val empty = compose.onNodeWithTag("assistant-sheet").fetchSemanticsNode().boundsInRoot.height
        val screenHeight = context.resources.configuration.screenHeightDp * compose.density.density
        assertTrue(empty < screenHeight * 0.42f)
        compose.onNodeWithText("Xác nhận lưu").assertDoesNotExist()
        compose.onNodeWithTag("assistant-input").performTextInput("Hôm nay mua đồ ăn 50 nghìn")
        compose.onNodeWithContentDescription("Ghi giao dịch").performClick()
        compose.waitUntil(20000) { model.aiDraftState.value.savedDraftId != null }
        assertEquals(1, documents().size)
        compose.onNodeWithText("Xác nhận lưu").assertDoesNotExist()
        compose.onNodeWithText("Giao dịch tiếp theo").performClick()
        compose.onNodeWithTag("assistant-input").performTextInput("Hôm nay mua đồ ăn")
        compose.onNodeWithContentDescription("Ghi giao dịch").performClick()
        compose.waitUntil(15000) { model.aiDraftState.value.message.contains("Số tiền") }
        assertEquals(1, documents().size)
        compose.onNodeWithTag("assistant-input").performTextInput("50 nghìn")
        compose.onNodeWithContentDescription("Ghi giao dịch").performClick()
        compose.waitUntil(20000) { model.aiDraftState.value.savedDraftId != null }
        assertEquals(2, documents().size)
        compose.onNodeWithContentDescription("Chụp hóa đơn").performClick()
        compose.waitUntil(45000) { model.aiDraftState.value.draft?.source == com.example.walletwise.domain.model.DraftSource.RECEIPT && model.aiDraftState.value.savedDraftId != null }
        val expanded = compose.onNodeWithTag("assistant-sheet").fetchSemanticsNode().boundsInRoot.height
        assertTrue(expanded > empty); assertTrue(expanded <= screenHeight * 0.60f + 1f)
        assertEquals(3, documents().size)
    }
    private fun documents() = runBlocking { FirebaseFirestore.getInstance().collection("users").document(requireNotNull(FirebaseAuth.getInstance().currentUser).uid).collection("transactions").get().await().documents }

    @Test fun aiCameraAndPickerAutomaticallyWriteCertainReceiptsOnceWithoutImageUpload() {
        renderAI()
        compose.onNodeWithContentDescription("Chụp hóa đơn").performClick()
        compose.waitUntil(45000) { model.aiDraftState.value.draft != null }
        assertEquals(1, captures); assertEquals(50000L, model.aiDraftState.value.draft?.amount)
        compose.waitUntil(20000) { model.aiDraftState.value.savedDraftId != null }
        assertEquals(1, documents().size); assertEquals(0, uploads)
        compose.onNodeWithContentDescription("Chọn hóa đơn từ thư viện").performClick()
        compose.waitUntil(45000) { !model.receiptRecognition.isDecoding.value && !model.receiptRecognition.state.value.isRecognizing && model.receiptRecognition.state.value.draft?.transaction?.id == model.aiDraftState.value.draft?.id }
        assertEquals(1, picks)
        val id = model.aiDraftState.value.draft!!.id
        compose.waitUntil(20000) { model.aiDraftState.value.savedDraftId == id }
        compose.runOnIdle { model.acceptReceiptDraft(model.receiptRecognition.state.value.draft!!); model.confirmAIDraft() }
        assertEquals(2, documents().size); assertEquals(1, documents().count { it.id == id }); assertEquals(0, uploads)
        assertTrue(File(context.cacheDir, "walletwise_receipt_capture").listFiles().orEmpty().isEmpty())
    }
    @Test fun addCameraAndPickerOnlyAttachImageAndUploadFailureKeepsFormWithoutSuccess() {
        var navigations = 0
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { MaterialTheme { AddTransactionScreen(model) { navigations++ } } } }
        compose.onNodeWithText("Chụp ảnh").performClick()
        compose.onNodeWithContentDescription("Ảnh đính kèm giao dịch").assertIsDisplayed()
        compose.onNodeWithText("Thư viện").performClick()
        assertEquals(1, captures); assertEquals(1, picks)
        assertNull(model.receiptRecognition.state.value.draft); assertNull(model.receiptRecognition.preview.value)
        assertNull(model.aiDraftState.value.draft)
        compose.onNodeWithTag("transaction-amount").performTextInput("50000")
        uploadFails = true
        compose.onNodeWithText("Xác nhận lưu").performScrollTo().performClick()
        compose.waitUntil(20000) { uploads == 1 && !model.isLoading.value }
        assertEquals(0, navigations); assertTrue(documents().isEmpty())
        uploadFails = false
        compose.onNodeWithText("Xác nhận lưu").performClick()
        compose.waitUntil(20000) { navigations == 1 }
        assertEquals(1, documents().size); assertEquals(2, uploads)
        assertNull(model.receiptRecognition.state.value.draft)
    }
    @Test fun cameraPickerCancelAndMicrophoneDenialKeepAssistantUsable() {
        cancelled = true
        renderAI()
        compose.onNodeWithContentDescription("Chụp hóa đơn").performClick()
        compose.onNodeWithContentDescription("Chọn hóa đơn từ thư viện").performClick()
        compose.onNodeWithContentDescription("Nhập giọng nói").performClick()
        compose.onNodeWithTag("assistant-input").assertIsDisplayed().performTextInput("mua do an 50k")
        assertNull(model.aiDraftState.value.draft); assertTrue(documents().isEmpty())
        assertTrue(File(context.cacheDir, "walletwise_receipt_capture").listFiles().orEmpty().isEmpty())
    }
    @Test fun editingCategoryOutsideFirstEightRendersAndSavesOriginalSelection() {
        val uid = requireNotNull(FirebaseAuth.getInstance().currentUser).uid
        val category = com.example.walletwise.domain.model.Category("outside-eight", "Danh mục test ngoài tám", sortOrder = 100)
        runBlocking { com.example.walletwise.data.repository.CategoryRepositoryImpl().addCategory(uid, category) }
        compose.waitUntil(20000) { model.categories.value.any { it.id == category.id } }
        val original = com.example.walletwise.domain.model.Transaction("outside-category-tx", uid, "Chi", "Tiền mặt", 50000.0, category.name, "Synthetic test", System.currentTimeMillis(), categoryId = category.id)
        runBlocking { assertTrue(TransactionRepositoryImpl().addTransaction(original).getOrThrow()) }
        compose.runOnIdle { model.transactionToEdit = original }
        var saved = false
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { MaterialTheme { AddTransactionScreen(model) { saved = true } } } }
        compose.onNodeWithText("Đang chọn: ${category.name}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cập nhật giao dịch").performScrollTo().performClick()
        compose.waitUntil(20000) { saved }
        assertEquals(category.name, documents().single().getString("category"))
        assertEquals(category.id, documents().single().getString("categoryId"))
    }
}
