package com.example.walletwise.reliability

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.walletwise.data.support.SupportImageEncoder
import com.example.walletwise.domain.model.SupportContact
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class SupportImageEncoderTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val createdUris = mutableListOf<Uri>()

    @After
    fun cleanUp() {
        createdUris.forEach { context.contentResolver.delete(it, null, null) }
    }

    @Test
    fun contentUriIsReadAndConvertedToPortableBoundedJpeg() = runBlocking {
        val bitmap = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height step 8) {
            for (x in 0 until bitmap.width step 8) {
                val color = Color.rgb((x * 17 + y) % 255, (x + y * 13) % 255, (x * 7 + y * 3) % 255)
                for (dy in 0 until 8) for (dx in 0 until 8) {
                    if (x + dx < bitmap.width && y + dy < bitmap.height) bitmap.setPixel(x + dx, y + dy, color)
                }
            }
        }
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val uri = insert("support-large.png", "image/png", png)

        val encoded = SupportImageEncoder().encode(context, uri).getOrThrow()

        assertEquals("image/jpeg", encoded.mimeType)
        assertTrue(encoded.fileName.endsWith(".jpg"))
        assertTrue(encoded.bytes.size <= SupportContact.MAX_SUPPORT_IMAGE_UPLOAD_BYTES)
        val bounds = BitmapFactory.Options().also {
            it.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(encoded.bytes, 0, encoded.bytes.size, it)
        }
        assertTrue(maxOf(bounds.outWidth, bounds.outHeight) <= 1600)
    }

    @Test
    fun invalidMimeIsRejectedBeforeUpload() = runBlocking {
        val uri = insert("not-supported.gif", "image/gif", "not an image".encodeToByteArray())
        val failure = SupportImageEncoder().encode(context, uri).exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message.orEmpty().contains("JPEG, PNG hoặc WebP"))
    }

    @Test
    fun sourceLargerThanLimitIsRejectedClearly() = runBlocking {
        val bytes = ByteArray((SupportContact.MAX_SUPPORT_IMAGE_SOURCE_BYTES + 1).toInt())
        val uri = insert("too-large.jpg", "image/jpeg", bytes)
        val failure = SupportImageEncoder().encode(context, uri).exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message.orEmpty().contains("12 MB"))
    }

    private fun insert(name: String, mimeType: String, bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/WalletWiseTests")
        }
        val uri = requireNotNull(
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        )
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        createdUris += uri
        return uri
    }
}
