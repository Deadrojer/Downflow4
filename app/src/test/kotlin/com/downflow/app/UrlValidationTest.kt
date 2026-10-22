package com.downflow.app

import com.downflow.app.browser.DownloadInterceptor
import org.junit.Assert.*
import org.junit.Test

/**
 * URL validation and download interception tests.
 */
class UrlValidationTest {

    // ─── Direct download detection ───────────────────────────────────────────

    @Test fun `zip URL is a direct download`() =
        assertTrue(DownloadInterceptor.isDirectDownload("https://example.com/file.zip"))

    @Test fun `mkv URL is a direct download`() =
        assertTrue(DownloadInterceptor.isDirectDownload("https://example.com/movie.mkv"))

    @Test fun `torrent URL is a direct download`() =
        assertTrue(DownloadInterceptor.isDirectDownload("https://tracker.example.com/ubuntu.torrent"))

    @Test fun `apk URL is a direct download`() =
        assertTrue(DownloadInterceptor.isDirectDownload("https://example.com/app-release.apk"))

    @Test fun `html page is NOT a direct download`() =
        assertFalse(DownloadInterceptor.isDirectDownload("https://example.com/index.html"))

    @Test fun `root page is NOT a direct download`() =
        assertFalse(DownloadInterceptor.isDirectDownload("https://example.com/"))

    @Test fun `non-network URL returns false`() =
        assertFalse(DownloadInterceptor.isDirectDownload("file:///sdcard/test.txt"))

    @Test fun `content-disposition mime overrides extension`() =
        assertTrue(DownloadInterceptor.isDirectDownload("https://example.com/download", "application/octet-stream"))

    @Test fun `html mime returns false even with zip path`() {
        // mime type takes precedence
        assertFalse(DownloadInterceptor.isDirectDownload("https://example.com/file.zip", "text/html"))
    }

    // ─── Magnet links ────────────────────────────────────────────────────────

    @Test fun `magnet link is detected`() =
        assertTrue(DownloadInterceptor.isMagnetLink("magnet:?xt=urn:btih:abc123"))

    @Test fun `https URL is not a magnet link`() =
        assertFalse(DownloadInterceptor.isMagnetLink("https://example.com"))

    @Test fun `magnet detection is case insensitive`() =
        assertTrue(DownloadInterceptor.isMagnetLink("MAGNET:?xt=urn:btih:abc"))

    // ─── Torrent URL ─────────────────────────────────────────────────────────

    @Test fun `torrent URL is detected`() =
        assertTrue(DownloadInterceptor.isTorrentUrl("https://example.com/ubuntu.torrent"))

    @Test fun `non-torrent URL not detected`() =
        assertFalse(DownloadInterceptor.isTorrentUrl("https://example.com/file.zip"))

    @Test fun `torrent detection is case insensitive`() =
        assertTrue(DownloadInterceptor.isTorrentUrl("https://example.com/UBUNTU.TORRENT"))

    // ─── Query string handling ───────────────────────────────────────────────

    @Test fun `zip with query params is direct download`() =
        assertTrue(DownloadInterceptor.isDirectDownload("https://cdn.example.com/file.zip?token=abc&v=2"))

    @Test fun `html with query params is not a direct download`() =
        assertFalse(DownloadInterceptor.isDirectDownload("https://example.com/page?id=5"))
}
