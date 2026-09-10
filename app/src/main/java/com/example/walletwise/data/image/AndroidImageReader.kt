package com.example.walletwise.data.image

import android.content.Context
import android.net.Uri
import com.example.walletwise.domain.model.ImageUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AndroidImageReader {
    suspend fun read(uri: Uri, context: Context): Result<ImageUpload>
}

class ContentResolverImageReader : AndroidImageReader {
    override suspend fun read(uri: Uri, context: Context): Result<ImageUpload> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes()
                } ?: throw IllegalArgumentException("Không thể đọc ảnh đã chọn.")

                if (bytes.isEmpty()) {
                    throw IllegalArgumentException("Ảnh đã chọn không có dữ liệu.")
                }

                ImageUpload(
                    bytes = bytes,
                    contentType = context.contentResolver.getType(uri) ?: "image/jpeg",
                    fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "image"
                )
            }
        }
}
