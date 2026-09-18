package com.example.walletwise.presentation.support

import android.content.*
import android.net.Uri
import android.provider.OpenableColumns
import com.example.walletwise.domain.model.*

object SupportIntents {
    fun dial() = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${SupportContact.PHONE}"))

    fun email(draft: SupportEmailDraft): Intent {
        require(draft.recipient == SupportContact.EMAIL)
        require(SupportEmailValidation.attachmentError(draft.attachments) == null)
        return if (draft.attachments.isEmpty()) {
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${SupportContact.EMAIL}").buildUpon()
                .appendQueryParameter("subject", draft.subject).appendQueryParameter("body", draft.body).build())
        } else {
            val uris = ArrayList(draft.attachments.map { Uri.parse(it.uri) })
            Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                type = draft.attachments.map { it.mimeType }.distinct().singleOrNull() ?: "*/*"
                if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
                else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                clipData = ClipData.newRawUri("Tệp hỗ trợ", uris.first()).also { data ->
                    uris.drop(1).forEach { data.addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SupportContact.EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, draft.subject)
            putExtra(Intent.EXTRA_TEXT, draft.body)
        }
    }
}

interface SupportActions {
    fun dial(): Boolean
    fun openEmail(draft: SupportEmailDraft): Boolean
    fun copy(text: String): Boolean
}

class AndroidSupportActions(private val context: Context) : SupportActions {
    override fun dial() = safeStart(SupportIntents.dial())

    @Suppress("DEPRECATION")
    override fun openEmail(draft: SupportEmailDraft): Boolean {
        val intent = SupportIntents.email(draft)
        if (draft.attachments.isEmpty()) return safeStart(intent)
        // Restrict the share chooser to installed email apps that also accept these attachments.
        val manager = context.packageManager
        val emailPackages = manager.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${SupportContact.EMAIL}")), 0)
            .map { it.activityInfo.packageName }.toSet()
        val candidates = manager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName in emailPackages }
            .map { Intent(intent).setComponent(ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
        if (candidates.isEmpty()) return false
        return safeStart(Intent.createChooser(candidates.first(), "Chọn ứng dụng email").apply {
            if (candidates.size > 1) putExtra(Intent.EXTRA_INITIAL_INTENTS, candidates.drop(1).toTypedArray())
        })
    }

    private fun safeStart(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) { false }
    catch (_: SecurityException) { false }

    override fun copy(text: String): Boolean = try {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Liên hệ hỗ trợ", text))
        true
    } catch (_: RuntimeException) { false }
}

/** Metadata only: no file content read, upload or persisted grant. */
fun readSupportAttachment(context: Context, uri: Uri): SupportAttachment {
    require(uri.scheme == "content")
    val resolver = context.contentResolver
    val type = resolver.getType(uri).orEmpty()
    require(type in SupportContact.attachmentTypes)
    val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    return resolver.query(uri, columns, null, null, null)!!.use { cursor ->
        require(cursor.moveToFirst())
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        require(nameIndex >= 0 && sizeIndex >= 0 && !cursor.isNull(sizeIndex))
        val file = SupportAttachment(uri.toString(), cursor.getString(nameIndex).orEmpty().replace('\n', ' ').replace('\r', ' ').take(180), type, cursor.getLong(sizeIndex))
        require(SupportEmailValidation.attachmentError(listOf(file)) == null)
        file
    }
}
