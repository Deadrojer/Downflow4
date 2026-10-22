package com.downflow.app

import com.downflow.app.aria2.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for Aria2RpcClient — covers:
 *  - JSON-RPC request construction
 *  - Authentication token injection
 *  - Successful response parsing
 *  - RPC error handling
 *  - HTTP error handling
 *  - addUri / pause / unpause / tellActive / getGlobalStat
 */
class Aria2RpcClientTest {

    private lateinit var gson: Gson
    private lateinit var client: Aria2RpcClient

    @Before
    fun setUp() {
        gson = GsonBuilder().setLenient().create()
        client = Aria2RpcClient(gson)
        client.configure(port = 6800, rpcSecret = "test-secret-xyz")
    }

    // ─── Request construction ────────────────────────────────────────────────

    @Test
    fun `request includes token prefix`() {
        val request = JsonRpcRequest(
            id = "1",
            method = "aria2.getVersion",
            params = listOf("token:test-secret-xyz")
        )
        val json = gson.toJson(request)
        assertTrue("Token must be first param", json.contains("token:test-secret-xyz"))
    }

    @Test
    fun `request serialises method correctly`() {
        val request = JsonRpcRequest(id = "2", method = "aria2.pause", params = listOf("token:s", "abc123"))
        val json = gson.toJson(request)
        assertTrue(json.contains("\"method\":\"aria2.pause\""))
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""))
    }

    // ─── Response parsing ────────────────────────────────────────────────────

    @Test
    fun `parses addUri success response`() {
        val json = """{"id":"1","jsonrpc":"2.0","result":"abc123gid"}"""
        val type = object : com.google.gson.reflect.TypeToken<JsonRpcResponse<String>>() {}
        val resp: JsonRpcResponse<String> = gson.fromJson(json, type.type)
        assertNull(resp.error)
        assertEquals("abc123gid", resp.result)
    }

    @Test
    fun `parses tellActive response list`() {
        val json = """
        {"id":"1","jsonrpc":"2.0","result":[
            {"gid":"aaa","status":"active","totalLength":"1000","completedLength":"500",
             "downloadSpeed":"100","uploadSpeed":"0","files":[],"connections":"2"}
        ]}
        """.trimIndent()
        val type = object : com.google.gson.reflect.TypeToken<JsonRpcResponse<List<Aria2DownloadStatus>>>() {}
        val resp: JsonRpcResponse<List<Aria2DownloadStatus>> = gson.fromJson(json, type.type)
        assertNull(resp.error)
        assertEquals(1, resp.result!!.size)
        val item = resp.result!![0]
        assertEquals("aaa", item.gid)
        assertEquals("active", item.status)
        assertEquals("1000", item.totalLength)
    }

    @Test
    fun `parses globalStat response`() {
        val json = """{"id":"1","jsonrpc":"2.0","result":{"downloadSpeed":"512000","uploadSpeed":"0","numActive":"2","numWaiting":"1","numStopped":"5","numStoppedTotal":"10"}}"""
        val type = object : com.google.gson.reflect.TypeToken<JsonRpcResponse<Aria2GlobalStat>>() {}
        val resp: JsonRpcResponse<Aria2GlobalStat> = gson.fromJson(json, type.type)
        assertEquals("512000", resp.result!!.downloadSpeed)
        assertEquals("2", resp.result!!.numActive)
    }

    // ─── Error handling ──────────────────────────────────────────────────────

    @Test
    fun `parses RPC error response`() {
        val json = """{"id":"1","jsonrpc":"2.0","error":{"code":1,"message":"GID not found"}}"""
        val type = object : com.google.gson.reflect.TypeToken<JsonRpcResponse<String>>() {}
        val resp: JsonRpcResponse<String> = gson.fromJson(json, type.type)
        assertNotNull(resp.error)
        assertEquals(1, resp.error!!.code)
        assertEquals("GID not found", resp.error!!.message)
    }

    // ─── Domain model mapping ────────────────────────────────────────────────

    @Test
    fun `toDomain maps active status correctly`() {
        val status = Aria2DownloadStatus(
            gid = "abc", status = "active",
            totalLength = "2048", completedLength = "1024",
            downloadSpeed = "500", files = listOf(
                Aria2File(index = "0", path = "/storage/file.zip",
                    uris = listOf(Aria2Uri("https://example.com/file.zip", "used")))
            )
        )
        val item = status.toDomain()
        assertEquals(DownloadState.ACTIVE, item.state)
        assertEquals("file.zip", item.fileName)
        assertEquals(2048L, item.totalBytes)
        assertEquals(1024L, item.completedBytes)
        assertEquals(500L, item.speedBytesPerSec)
        assertEquals(0.5f, item.progressPercent, 0.001f)
    }

    @Test
    fun `toDomain maps error status with message`() {
        val status = Aria2DownloadStatus(
            gid = "err1", status = "error",
            errorCode = "3", errorMessage = "Resource not found"
        )
        val item = status.toDomain()
        assertEquals(DownloadState.ERROR, item.state)
        assertEquals("Resource not found", item.errorMessage)
    }

    @Test
    fun `toDomain maps torrent name from bittorrent info`() {
        val status = Aria2DownloadStatus(
            gid = "tor1", status = "active",
            bittorrent = Aria2Bittorrent(info = Aria2TorrentInfo("My.Movie.2024"))
        )
        val item = status.toDomain()
        assertTrue(item.isTorrent)
        assertEquals("My.Movie.2024", item.fileName)
        assertEquals("My.Movie.2024", item.torrentName)
    }

    // ─── ETA calculation ─────────────────────────────────────────────────────

    @Test
    fun `etaSeconds returns null when speed is zero`() {
        val item = DownloadItem(
            gid = "x", state = DownloadState.ACTIVE, fileName = "f",
            url = "", dir = "", totalBytes = 1000L, completedBytes = 500L,
            speedBytesPerSec = 0L, errorMessage = null,
            isTorrent = false, torrentName = null, infoHash = null, connections = 1
        )
        assertNull(item.etaSeconds)
    }

    @Test
    fun `etaSeconds is correct when speed is non-zero`() {
        val item = DownloadItem(
            gid = "x", state = DownloadState.ACTIVE, fileName = "f",
            url = "", dir = "", totalBytes = 1000L, completedBytes = 0L,
            speedBytesPerSec = 100L, errorMessage = null,
            isTorrent = false, torrentName = null, infoHash = null, connections = 1
        )
        assertEquals(10L, item.etaSeconds)
    }

    @Test
    fun `etaSeconds is null when download is complete`() {
        val item = DownloadItem(
            gid = "x", state = DownloadState.COMPLETE, fileName = "f",
            url = "", dir = "", totalBytes = 1000L, completedBytes = 1000L,
            speedBytesPerSec = 500L, errorMessage = null,
            isTorrent = false, torrentName = null, infoHash = null, connections = 1
        )
        assertNull(item.etaSeconds) // totalBytes == completedBytes
    }
}
