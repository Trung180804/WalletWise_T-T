package com.example.walletwise.reliability

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.walletwise.data.draft.AndroidReceiptRecognition
import com.example.walletwise.domain.model.DefaultCategories
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReceiptOcrRuntimeTest {
    @Test fun bundledOcrReadsSyntheticReceiptWithoutUploadOrAutomaticSave(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "checkpoint_synthetic_receipt.png")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val controller = AndroidReceiptRecognition(scope, MutableStateFlow(DefaultCategories))
        try {
            val bitmap = Bitmap.createBitmap(1400,1000,Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
            val paint = Paint().apply { color=Color.BLACK; textSize=65f; isAntiAlias=true }
            listOf("QUAN AN TEST", "15/09/2026", "Subtotal 40.000", "VAT 10.000", "TOTAL 50.000", "TIEN MAT").forEachIndexed { i, line ->
                canvas.drawText(line,70f,100f+i*125f,paint)
            }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            withContext(Dispatchers.Main) { controller.setUserId("test-session"); controller.galleryResult(context,Uri.fromFile(file)) }
            withTimeout(30000) { controller.preview.filterNotNull().first() }
            withContext(Dispatchers.Main) { controller.recognize() }
            val draft = withTimeout(45000) { controller.state.first { it.draft != null || it.error != null }.draft }
            assertNotNull(draft)
            assertEquals(50000L,draft?.transaction?.amount)
            assertEquals("Ăn uống",draft?.transaction?.category)
            assertEquals("Tiền mặt",draft?.transaction?.paymentMethod)
            assertTrue(draft?.transaction?.timestamp != null)
            withContext(Dispatchers.Main) { controller.cancel() }
            assertNull(controller.preview.value); assertNull(controller.state.value.draft)
        } finally { withContext(Dispatchers.Main) { controller.close() }; scope.cancel(); file.delete() }
    }
}
