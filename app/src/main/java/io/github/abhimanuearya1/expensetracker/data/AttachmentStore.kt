package io.github.abhimanuearya1.expensetracker.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Keeps the files attached to expenses (photos, PDFs, anything) as private
 * copies inside the app's own storage, so they stay available even if the
 * original is moved or deleted. No storage permission is needed.
 */
class AttachmentStore(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, DIRECTORY)
    private val authority = appContext.packageName + ".files"

    /** Copies the file the user picked into the app and describes it. */
    fun copyIn(uri: Uri): AttachmentDraft {
        val resolver = appContext.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        var displayName: String? = null
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) displayName = cursor.getString(0)
            }
        } catch (e: Exception) {
            // Some file providers cannot be asked for a name; fall back below.
        }
        val name = displayName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Attachment"
        val extension = name.substringAfterLast('.', "").takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        val fileName = UUID.randomUUID().toString() + (if (extension != null) ".$extension" else "")

        dir.mkdirs()
        val target = File(dir, fileName)
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open the file")
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        return AttachmentDraft(id = null, fileName = fileName, displayName = name, mimeType = mimeType)
    }

    /** Reserves a file for a photo about to be taken and returns its name. */
    fun newPhotoFileName(): String {
        dir.mkdirs()
        return UUID.randomUUID().toString() + ".jpg"
    }

    fun exists(fileName: String): Boolean = File(dir, fileName).length() > 0

    /** An address other apps (camera, viewers) may be given temporary access to. */
    fun uriFor(fileName: String): Uri = FileProvider.getUriForFile(appContext, authority, File(dir, fileName))

    fun delete(fileName: String) {
        File(dir, fileName).delete()
    }

    /**
     * Removes copies that no expense refers to, e.g. files added to a form that
     * was never saved because the app was closed. Recent files are left alone
     * in case a form is using them right now.
     */
    fun deleteUnreferenced(referenced: Set<String>) {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        dir.listFiles()?.forEach { file ->
            if (file.name !in referenced && file.lastModified() < cutoff) file.delete()
        }
    }

    companion object {
        /** Must match res/xml/file_paths.xml. */
        const val DIRECTORY = "attachments"
    }
}
