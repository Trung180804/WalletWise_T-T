package com.example.walletwise.presentation.support

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.example.walletwise.domain.model.SupportContact
import com.example.walletwise.domain.model.SupportImageUrlPolicy
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val MAX_REMOTE_IMAGE_BYTES = SupportContact.MAX_SUPPORT_IMAGE_SOURCE_BYTES
private val imageCache = mutableMapOf<String, UIImage>()

@OptIn(ExperimentalForeignApi::class)
@Composable
fun IosSupportImage(
    reference: String,
    baseUrl: String,
    allowLocalHttp: Boolean,
    modifier: Modifier = Modifier
) {
    val resolved = remember(reference, baseUrl, allowLocalHttp) {
        SupportImageUrlPolicy.resolve(reference, baseUrl, allowLocalHttp)
    }
    var image by remember(resolved) { mutableStateOf(resolved?.let(imageCache::get)) }
    var failed by remember(resolved) { mutableStateOf(resolved == null) }
    DisposableEffect(resolved) {
        if (resolved == null || image != null) return@DisposableEffect onDispose { }
        val loader = IosRemoteImageLoader(resolved) { loaded ->
            if (loaded == null) {
                failed = true
            } else {
                imageCache[resolved] = loaded
                while (imageCache.size > 24) imageCache.remove(imageCache.keys.first())
                image = loaded
            }
        }
        loader.start()
        onDispose { loader.cancel() }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            image != null -> UIKitView(
                factory = {
                    UIImageView().apply {
                        contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
                        clipsToBounds = true
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { it.image = image }
            )
            failed -> Text(
                "Ảnh không khả dụng",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            else -> CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosRemoteImageLoader(
    private val value: String,
    private val completion: (UIImage?) -> Unit
) {
    private var task: NSURLSessionDownloadTask? = null
    private var active = true

    fun start() {
        val url = NSURL.URLWithString(value) ?: return finish(null)
        val request = NSMutableURLRequest(
            uRL = url,
            cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
            timeoutInterval = 20.0
        ).apply {
            HTTPMethod = "GET"
            setValue("image/jpeg,image/png,image/webp", forHTTPHeaderField = "Accept")
        }
        task = NSURLSession.sharedSession.downloadTaskWithRequest(request) { location, response, error ->
            val http = response as? NSHTTPURLResponse
            val expected = response?.expectedContentLength ?: -1L
            val mime = response?.MIMEType?.lowercase()
            val finalUrl = response?.URL
            val sameOrigin = finalUrl?.scheme == url.scheme && finalUrl?.host == url.host && finalUrl?.port == url.port
            val status = http?.statusCode ?: 0L
            val validResponse = error == null && location != null && sameOrigin && status in 200L..299L &&
                mime in setOf("image/jpeg", "image/png", "image/webp") &&
                (expected < 0 || expected <= MAX_REMOTE_IMAGE_BYTES)
            val loaded = if (!validResponse) null else runCatching {
                val handle = NSFileHandle.fileHandleForReadingFromURL(location, null)
                    ?: error("Không thể đọc ảnh tải về.")
                val data = try {
                    handle.readDataOfLength((MAX_REMOTE_IMAGE_BYTES + 1).toULong())
                } finally {
                    handle.closeFile()
                }
                require(data.length.toLong() in 1..MAX_REMOTE_IMAGE_BYTES)
                UIImage.imageWithData(data)
            }.getOrNull()
            finish(loaded)
        }.also { it.resume() }
    }

    fun cancel() {
        active = false
        task?.cancel()
        task = null
    }

    private fun finish(image: UIImage?) {
        dispatch_async(dispatch_get_main_queue()) {
            if (active) completion(image)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
fun formatIosSupportTime(milliseconds: Long): String {
    val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(milliseconds.toDouble() / 1000.0))
}
