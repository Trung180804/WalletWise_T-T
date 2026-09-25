package com.example.walletwise.data.image

import android.util.Base64
import com.example.walletwise.domain.model.ImageUpload
import com.example.walletwise.domain.repository.ImageUploader
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ImgBbImageUploader(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient()
) : ImageUploader {
    override suspend fun upload(image: ImageUpload): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (apiKey.isBlank()) {
                    throw IOException("Dịch vụ tải ảnh chưa được cấu hình.")
                }
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("key", apiKey)
                    .addFormDataPart(
                        "image",
                        Base64.encodeToString(image.bytes, Base64.DEFAULT)
                    )
                    .build()

                val request = Request.Builder()
                    .url("https://api.imgbb.com/1/upload")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Tải ảnh lên thất bại (${response.code}).")
                    }

                    val responseBody = response.body?.string()
                        ?: throw IOException("ImgBB trả về phản hồi rỗng.")
                    JSONObject(responseBody)
                        .getJSONObject("data")
                        .getString("url")
                        .takeIf { it.isNotBlank() }
                        ?: throw IOException("ImgBB không trả về URL ảnh.")
                }
            }
        }
}
