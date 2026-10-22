package com.downflow.app.browser

import android.webkit.URLUtil
import android.webkit.WebView

/**
 * Classifies a URL as either a direct downloadable file or a web page.
 *
 * Limitation (documented per spec §6):
 * WebView fires DownloadListener only for direct file links whose server
 * sends a Content-Disposition: attachment header or a non-HTML MIME type.
 * Streaming sites, DRM-protected content, and single-page apps that trigger
 * downloads via JavaScript blobs cannot be intercepted this way.
 * Users should copy the direct URL and paste it manually for those cases.
 */
object DownloadInterceptor {

    private val DOWNLOADABLE_EXTENSIONS = setOf(
        "zip", "tar", "gz", "bz2", "xz", "7z", "rar",
        "apk", "exe", "dmg", "pkg", "deb", "rpm",
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm",
        "mp3", "flac", "ogg", "wav", "aac",
        "pdf", "epub", "mobi",
        "iso", "img",
        "torrent",
        "jpg", "jpeg", "png", "gif", "webp", "svg",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx",
    )

    /**
     * Returns true if the URL looks like a direct file download
     * (by extension heuristic) rather than a web page.
     */
    fun isDirectDownload(url: String, mimeType: String? = null): Boolean {
        if (!URLUtil.isNetworkUrl(url)) return false
        if (mimeType != null && mimeType != "text/html" && mimeType != "application/xhtml+xml") return true
        val path = url.substringBefore("?").substringBefore("#").lowercase()
        val ext = path.substringAfterLast('.', "")
        return ext in DOWNLOADABLE_EXTENSIONS
    }

    fun isMagnetLink(url: String) = url.startsWith("magnet:", ignoreCase = true)
    fun isTorrentUrl(url: String) = url.lowercase().endsWith(".torrent")
}
