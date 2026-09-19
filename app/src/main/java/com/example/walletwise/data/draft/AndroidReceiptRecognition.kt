package com.example.walletwise.data.draft

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.walletwise.domain.model.Category
import com.example.walletwise.domain.service.ReceiptTextParser
import com.example.walletwise.presentation.transaction.ReceiptDraftPresenter
import com.example.walletwise.presentation.transaction.ReceiptOcrEngine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class AndroidReceiptRecognition(private val scope: CoroutineScope, categories: StateFlow<List<Category>>) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val bitmapState = MutableStateFlow<Bitmap?>(null)
    val preview = bitmapState.asStateFlow()
    private val decodingState = MutableStateFlow(false)
    val isDecoding = decodingState.asStateFlow()
    private var pendingPhoto: File? = null
    private var decoding: Job? = null
    private var generation = 0L
    private var uid: String? = null
    private var activeToken: String? = null
    private val presenter = ReceiptDraftPresenter(scope, ReceiptOcrEngine { token ->
        require(token == activeToken)
        val bitmap = requireNotNull(bitmapState.value)
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
    }, ReceiptTextParser(AndroidDraftDateTimeProvider()), categories)
    val state = presenter.state
    fun setUserId(userId: String?) { if (uid != userId) { cancel(); uid = userId; presenter.setUserId(userId) } }

    fun cameraUri(context: Context): Uri {
        pendingPhoto?.delete()
        val directory = File(context.cacheDir, "walletwise_receipt_capture").apply { mkdirs() }
        directory.listFiles()?.filter { it.isFile && it.name.matches(Regex("[0-9a-fA-F-]{36}\\.jpg")) && it.canonicalFile.parentFile == directory.canonicalFile }
            ?.forEach { it.delete() }
        pendingPhoto = File(directory, "${UUID.randomUUID()}.jpg").apply { createNewFile() }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", requireNotNull(pendingPhoto))
    }
    fun cameraResult(context: Context, success: Boolean) {
        val file = pendingPhoto ?: return
        pendingPhoto = null
        if (!success) { file.delete(); return }
        load(context, Uri.fromFile(file), file)
    }
    fun galleryResult(context: Context, uri: Uri?) { if (uri != null) load(context, uri, null) }
    fun permissionDenied() = presenter.permissionDenied()

    private fun load(context: Context, uri: Uri, ownedFile: File?) {
        presenter.cancel()
        decoding?.cancel()
        val version = ++generation
        bitmapState.value = null; activeToken = null; decodingState.value = true
        decoding = scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0)
                    var sample = 1
                    while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
                    val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null,
                        BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("No image")
                    val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                        android.media.ExifInterface(stream).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                    } ?: 1
                    val matrix = android.graphics.Matrix()
                    when (orientation) { 3 -> matrix.postRotate(180f); 6 -> matrix.postRotate(90f); 8 -> matrix.postRotate(270f);
                        2 -> matrix.postScale(-1f, 1f); 4 -> matrix.postScale(1f, -1f); 5 -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }; 7 -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) } }
                    if (matrix.isIdentity) decoded else {
                        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { rotated ->
                            if (rotated !== decoded) decoded.recycle()
                        }
                    }
                }
                ensureActive()
                if (version != generation) return@launch
                bitmapState.value = bitmap
                activeToken = UUID.randomUUID().toString()
                recognize()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (version == generation) {
                if (error is SecurityException) presenter.permissionDenied() else presenter.imageUnreadable()
            } }
            finally { ownedFile?.delete(); if (version == generation) decodingState.value = false }
        }
    }
    fun recognize() { activeToken?.let(presenter::analyze) }
    fun cancel() { generation++; decoding?.cancel(); pendingPhoto?.delete(); pendingPhoto = null;
        activeToken = null; bitmapState.value = null; decodingState.value = false; presenter.cancel() }
    fun close() { cancel(); presenter.close(); recognizer.close() }
}
