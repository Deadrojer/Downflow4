package com.downflow.app.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StorageManager"

/**
 * Handles Android Storage Access Framework integration.
 *
 * ── aria2 filesystem path limitation ────────────────────────────────────────
 * aria2 requires an actual filesystem path for --dir.  Android's SAF returns
 * content:// URIs which cannot be passed directly to aria2.
 *
 * Strategy
 * ────────
 * 1. Default download dir = app-specific external storage
 *    (getExternalFilesDir(null)) — no special permission needed on API 29+.
 * 2. When the user picks a folder with SAF, we record the content:// URI for
 *    display and persist URI permissions.  However, we map it to a usable
 *    path only for paths that resolve under app-specific directories or
 *    /sdcard/Download (primary external storage).  For arbitrary folders we
 *    fall back to the app-specific external storage and explain the limitation.
 *
 * Limitation: aria2 cannot write to arbitrary SAF-managed locations because
 * it has no ContentResolver.  If you need to write to external SD cards or
 * cloud providers you would need to post-download move files via ContentResolver.
 * This version downloads to the app-specific path; moving is a potential
 * future enhancement.
 */
@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Default writable directory aria2 can use directly. */
    fun defaultDownloadDir(): File =
        (context.getExternalFilesDir(null) ?: context.filesDir).also { it.mkdirs() }

    /**
     * Tries to resolve a SAF content:// URI to a real filesystem path.
     * Returns null if the path cannot be resolved reliably.
     */
    fun resolveUriToPath(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path

        if (uri.scheme == "content") {
            // Try the DocumentsContract path extraction heuristic for primary storage
            if (DocumentsContract.isTreeUri(uri)) {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                if (docId.startsWith("primary:")) {
                    val relative = docId.removePrefix("primary:")
                    val path = "/sdcard/$relative"
                    val f = File(path)
                    if (f.exists() || f.mkdirs()) return f.absolutePath
                }
            }
        }
        return null
    }

    /** Persist SAF URI permissions so they survive reboot. */
    fun takePersistablePermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist URI permission for $uri", e)
        }
    }

    /** Returns a FileProvider URI for sharing a downloaded file. */
    fun getShareableUri(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    /** Opens a file with an implicit intent to the appropriate viewer. */
    fun openFileIntent(file: File): Intent = Intent(Intent.ACTION_VIEW).apply {
        val uri = getShareableUri(file)
        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun availableSpaceBytes(dir: File): Long =
        dir.usableSpace

    fun hasEnoughSpace(dir: File, needed: Long, margin: Long = 10 * 1024 * 1024): Boolean =
        availableSpaceBytes(dir) > needed + margin
}
