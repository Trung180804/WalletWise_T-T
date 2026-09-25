package com.example.walletwise.data.support

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.walletwise.BuildConfig
import com.example.walletwise.domain.model.SupportContact
import com.example.walletwise.domain.model.SupportImagePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

internal data class EncodedSupportImage(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String
)

internal class SupportImageEncoder {
    suspend fun encode(context: Context, uri: Uri): Result<EncodedSupportImage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri)?.substringBefore(';')?.lowercase()
                    ?: throw IllegalArgumentException("Không xác định được định dạng ảnh.")
                require(mimeType in ACCEPTED_MIME_TYPES) {
                    "Chỉ hỗ trợ ảnh JPEG, PNG hoặc WebP."
                }

                val metadata = resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) null
                    else {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                        name to size
                    }
                }
                val declaredSize = metadata?.second
                require(declaredSize == null || declaredSize in 1..SupportContact.MAX_SUPPORT_IMAGE_SOURCE_BYTES) {
                    "Ảnh vượt quá giới hạn 12 MB."
                }
                val source = resolver.openInputStream(uri)?.use {
                    readBounded(it, SupportContact.MAX_SUPPORT_IMAGE_SOURCE_BYTES)
                } ?: throw IllegalArgumentException("Không thể đọc ảnh đã chọn.")
                require(source.isNotEmpty()) { "Ảnh đã chọn không có dữ liệu." }

                val bounds = BitmapFactory.Options().also {
                    it.inJustDecodeBounds = true
                    BitmapFactory.decodeByteArray(source, 0, source.size, it)
                }
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Tệp đã chọn không phải ảnh hợp lệ." }
                require(bounds.outWidth.toLong() * bounds.outHeight.toLong() <= MAX_SOURCE_PIXELS) {
                    "Ảnh có độ phân giải quá lớn."
                }

                var sample = 1
                while (max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_DIMENSION * 2) {
                    sample *= 2
                }
                val decoded = BitmapFactory.decodeByteArray(
                    source,
                    0,
                    source.size,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                ) ?: throw IllegalArgumentException("Không thể giải mã ảnh đã chọn.")
                val scaled = scaleToFit(decoded, MAX_DIMENSION)
                val rgb = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
                Canvas(rgb).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(scaled, 0f, 0f, null)
                }
                val encoded = compressWithinLimit(rgb)
                require(encoded.size <= SupportContact.MAX_SUPPORT_IMAGE_UPLOAD_BYTES) {
                    "Không thể nén ảnh xuống dưới 512 KB."
                }
                EncodedSupportImage(
                    bytes = encoded,
                    mimeType = "image/jpeg",
                    fileName = safeJpegName(metadata?.first)
                )
            }
        }

    private fun readBounded(input: java.io.InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Ảnh vượt quá giới hạn 12 MB." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun scaleToFit(source: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= maxDimension) return source
        val ratio = maxDimension.toFloat() / largest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).roundToInt().coerceAtLeast(1),
            (source.height * ratio).roundToInt().coerceAtLeast(1),
            true
        )
    }

    private fun compressWithinLimit(source: Bitmap): ByteArray {
        var current = source
        var quality = 88
        repeat(14) {
            val output = ByteArrayOutputStream()
            check(current.compress(Bitmap.CompressFormat.JPEG, quality, output))
            val bytes = output.toByteArray()
            if (bytes.size <= SupportContact.MAX_SUPPORT_IMAGE_UPLOAD_BYTES) return bytes
            if (quality > 58) {
                quality -= 10
            } else {
                val nextWidth = (current.width * 0.8f).roundToInt().coerceAtLeast(320)
                val nextHeight = (current.height * 0.8f).roundToInt().coerceAtLeast(320)
                require(nextWidth < current.width || nextHeight < current.height) {
                    "Không thể nén ảnh xuống dưới 512 KB."
                }
                current = Bitmap.createScaledBitmap(current, nextWidth, nextHeight, true)
                quality = 82
            }
        }
        throw IllegalArgumentException("Không thể nén ảnh xuống dưới 512 KB.")
    }

    private fun safeJpegName(original: String?): String {
        val stem = original.orEmpty().substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_', '-')
            .take(96)
            .ifBlank { "support-image" }
        return "$stem.jpg"
    }

    private companion object {
        val ACCEPTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        const val MAX_DIMENSION = 1600
        const val MAX_SOURCE_PIXELS = 40_000_000L
    }
}

internal class SupportImageUploader(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    suspend fun upload(image: EncodedSupportImage): Result<SupportImagePayload> =
        withContext(Dispatchers.IO) {
            runCatching {
                val origin = baseUrl.trim().trimEnd('/')
                require(origin.isNotEmpty()) { "Chưa cấu hình máy chủ tải ảnh hỗ trợ." }
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "File",
                        image.fileName,
                        image.bytes.toRequestBody(image.mimeType.toMediaType())
                    )
                    .build()
                val request = Request.Builder()
                    .url("$origin/api/upload/image")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    require(response.isSuccessful) {
                        "Tải ảnh thất bại (${response.code})."
                    }
                    val imageUrl = JSONObject(responseBody).optString("url").trim()
                    require(imageUrl.matches(Regex("^/uploads/[A-Za-z0-9._-]{1,180}$"))) {
                        "Máy chủ trả về đường dẫn ảnh không hợp lệ."
                    }
                    SupportImagePayload(
                        imageUrl = imageUrl,
                        mimeType = image.mimeType,
                        fileName = image.fileName
                    )
                }
            }
        }
}

internal class SupportImageTransport(
    private val context: Context,
    private val encoder: SupportImageEncoder = SupportImageEncoder(),
    private val uploader: SupportImageUploader = SupportImageUploader(BuildConfig.SUPPORT_UPLOAD_BASE_URL)
) {
    suspend fun prepare(uri: Uri): Result<SupportImagePayload> {
        val encoded = encoder.encode(context, uri).getOrElse { return Result.failure(it) }
        return uploader.upload(encoded)
    }
}

fun supportImageModel(rawValue: String?): Any? {
    val value = rawValue?.trim().orEmpty()
    if (value.isEmpty() || value.startsWith("content://") || value.startsWith("file://") ||
        value.startsWith("blob:")) return null
    if (value.startsWith("/uploads/")) {
        val base = BuildConfig.SUPPORT_UPLOAD_BASE_URL.trimEnd('/')
        return if (base.isEmpty()) null else "$base$value"
    }
    if (value.startsWith("https://")) return value
    val match = DATA_IMAGE.matchEntire(value) ?: return null
    return runCatching {
        val bytes = Base64.decode(match.groupValues[2], Base64.DEFAULT)
        bytes.takeIf { it.size <= SupportContact.MAX_SUPPORT_IMAGE_UPLOAD_BYTES }
    }.getOrNull()
}

private val DATA_IMAGE = Regex("^data:image/(jpeg|png|webp);base64,([A-Za-z0-9+/=\\r\\n]+)$")
