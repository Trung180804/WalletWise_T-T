package com.example.walletwise.data.image

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Attachment selection has no OCR dependency. Only app-owned capture files are deleted. */
class TransactionAttachmentState(private val directory: File, initialUrl: String = "") {
    var selectedUri by mutableStateOf<Uri?>(null)
        private set
    var existingUrl by mutableStateOf(initialUrl)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var pending: File? = null
    private var selectedFile: File? = null
    val previewModel: Any? get() = selectedUri ?: existingUrl.takeIf { it.isNotBlank() }

    fun cameraUri(context: Context): Uri {
        pending?.delete()
        directory.mkdirs()
        val file = File(directory, "${UUID.randomUUID()}.jpg").apply { createNewFile() }
        pending = file
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    fun cameraResult(success: Boolean) {
        val file = pending ?: return
        pending = null
        if (!success || file.length() == 0L) { file.delete(); return }
        selectedFile?.delete()
        selectedFile = file
        selectedUri = Uri.fromFile(file)
        error = null
    }
    fun pickerResult(uri: Uri?) {
        if (uri == null) return
        selectedFile?.delete(); selectedFile = null
        selectedUri = uri; error = null
    }
    fun launchFailed() { pending?.delete(); pending = null; error = "Không mở được ảnh/camera. Bạn vẫn có thể nhập giao dịch." }
    fun remove() { selectedFile?.delete(); selectedFile = null; selectedUri = null; existingUrl = ""; error = null }
    fun close() { pending?.delete(); pending = null; selectedFile?.delete(); selectedFile = null; selectedUri = null }

    fun snapshot(): List<String> = listOf(existingUrl, selectedUri?.toString().orEmpty(), pending?.name.orEmpty(), selectedFile?.name.orEmpty())
    fun restore(snapshot: List<String>) {
        existingUrl = snapshot[0]
        fun owned(name: String): File? = name.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}\\.jpg")) }
            ?.let { File(directory, it) }?.takeIf { it.exists() && it.canonicalFile.parentFile == directory.canonicalFile }
        pending = owned(snapshot[2]); selectedFile = owned(snapshot[3])
        selectedUri = snapshot[1].takeIf { it.isNotBlank() && (snapshot[3].isBlank() || selectedFile != null) }?.let(Uri::parse)
    }
}
