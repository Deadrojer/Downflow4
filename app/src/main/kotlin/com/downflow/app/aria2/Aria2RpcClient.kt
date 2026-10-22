package com.downflow.app.aria2

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level aria2 JSON-RPC 2.0 client.
 *
 * Security notes:
 *  - All requests go to 127.0.0.1 (loopback) only.
 *  - Every call prefixes the method params with "token:<secret>" per aria2 docs.
 *  - Secrets are never logged; OkHttp logging interceptor is debug-only and
 *    the secret is redacted in the interceptor configuration.
 */
@Singleton
class Aria2RpcClient @Inject constructor(
    private val gson: Gson,
) {
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    // OkHttp client with generous timeouts — aria2 can be slow on large torrents
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var baseUrl: String = "http://127.0.0.1:6800/jsonrpc"
    private var secret: String = ""

    fun configure(port: Int, rpcSecret: String) {
        baseUrl = "http://127.0.0.1:$port/jsonrpc"
        secret = rpcSecret
    }

    // ─── Generic RPC call ────────────────────────────────────────────────────

    /**
     * Executes a JSON-RPC call.  The secret token is prepended automatically.
     * Returns [Result.success] with the parsed response result, or
     * [Result.failure] with an [Aria2RpcException] / [IOException].
     */
    suspend fun <T> call(
        method: String,
        params: List<Any> = emptyList(),
        responseType: TypeToken<JsonRpcResponse<T>>,
    ): Result<T> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val fullParams = buildList {
            add("token:$secret")
            addAll(params)
        }
        val body = gson.toJson(
            JsonRpcRequest(id = id, method = method, params = fullParams)
        ).toRequestBody(mediaType)

        val request = Request.Builder()
            .url(baseUrl)
            .post(body)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("HTTP ${response.code}: ${response.message}")
                )
            }
            val json = response.body?.string()
                ?: return@withContext Result.failure(IOException("Empty response body"))

            val rpcResp: JsonRpcResponse<T> = gson.fromJson(json, responseType.type)
            if (rpcResp.error != null) {
                return@withContext Result.failure(
                    Aria2RpcException(rpcResp.error.code, rpcResp.error.message)
                )
            }
            val result = rpcResp.result
                ?: return@withContext Result.failure(IOException("Null result"))
            Result.success(result)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    // ─── Typed convenience helpers ───────────────────────────────────────────

    suspend fun addUri(uris: List<String>, options: Map<String, String> = emptyMap()): Result<String> =
        call(
            method = "aria2.addUri",
            params = if (options.isEmpty()) listOf(uris) else listOf(uris, options),
            responseType = object : TypeToken<JsonRpcResponse<String>>() {},
        )

    suspend fun addTorrent(base64Torrent: String, options: Map<String, String> = emptyMap()): Result<String> =
        call(
            method = "aria2.addTorrent",
            params = if (options.isEmpty()) listOf(base64Torrent) else listOf(base64Torrent, emptyList<String>(), options),
            responseType = object : TypeToken<JsonRpcResponse<String>>() {},
        )

    suspend fun tellStatus(gid: String): Result<Aria2DownloadStatus> =
        call(
            method = "aria2.tellStatus",
            params = listOf(gid),
            responseType = object : TypeToken<JsonRpcResponse<Aria2DownloadStatus>>() {},
        )

    suspend fun tellActive(): Result<List<Aria2DownloadStatus>> =
        call(
            method = "aria2.tellActive",
            params = emptyList(),
            responseType = object : TypeToken<JsonRpcResponse<List<Aria2DownloadStatus>>>() {},
        )

    suspend fun tellWaiting(offset: Int = 0, num: Int = 1000): Result<List<Aria2DownloadStatus>> =
        call(
            method = "aria2.tellWaiting",
            params = listOf(offset, num),
            responseType = object : TypeToken<JsonRpcResponse<List<Aria2DownloadStatus>>>() {},
        )

    suspend fun tellStopped(offset: Int = -1, num: Int = 1000): Result<List<Aria2DownloadStatus>> =
        call(
            method = "aria2.tellStopped",
            params = listOf(offset, num),
            responseType = object : TypeToken<JsonRpcResponse<List<Aria2DownloadStatus>>>() {},
        )

    suspend fun pause(gid: String): Result<String> =
        call(
            method = "aria2.pause",
            params = listOf(gid),
            responseType = object : TypeToken<JsonRpcResponse<String>>() {},
        )

    suspend fun pauseAll(): Result<String> =
        call(
            method = "aria2.pauseAll",
            params = emptyList(),
            responseType = object : TypeToken<JsonRpcResponse<String>>() {},
        )

    suspend fun unpause(gid: String): Result<String> =
        call(
            method = "aria2.unpause",
            params = listOf(gid),
            responseType = object : TypeToken<JsonRpcResponse<String>>() {},
        )

    suspend fun remove(gid: String): Result<String> =
        call(
            method = "aria2.remove",
            params = listOf(gid),
            responseType = object : TypeToken<JsonRpcResponse<String>>() {},
        )

    suspend fun forceRemove(gid: String): Result<String> =
        call(
            method = "aria2.forceRemove",
            params = listOf(gid),
            responseType = object : TypeToken<JsonRpcResponse<String>>() {},
        )

    suspend fun removeDownloadResult(gid: String): Result<String> =
        call(
            method = "aria2.removeDownloadResult",
            params = listOf(gid),
            responseType = object : TypeToken<JsonRpcResponse<String>>() {},
        )

    suspend fun purgeDownloadResult(): Result<String> =
        call(
            method = "aria2.purgeDownloadResult",
            params = emptyList(),
            responseType = object : TypeToken<JsonRpcResponse<String>>() {},
        )

    suspend fun getGlobalStat(): Result<Aria2GlobalStat> =
        call(
            method = "aria2.getGlobalStat",
            params = emptyList(),
            responseType = object : TypeToken<JsonRpcResponse<Aria2GlobalStat>>() {},
        )

    suspend fun getVersion(): Result<Aria2Version> =
        call(
            method = "aria2.getVersion",
            params = emptyList(),
            responseType = object : TypeToken<JsonRpcResponse<Aria2Version>>() {},
        )

    suspend fun changeGlobalOption(options: Map<String, String>): Result<String> =
        call(
            method = "aria2.changeGlobalOption",
            params = listOf(options),
            responseType = object : TypeToken<JsonRpcResponse<String>>() {},
        )

    suspend fun shutdown(): Result<String> =
        call(
            method = "aria2.shutdown",
            params = emptyList(),
            responseType = object : TypeToken<JsonRpcResponse<String>>() {},
        )

    /** Ping / health-check — returns true only if a valid version string comes back. */
    suspend fun ping(): Boolean = getVersion().isSuccess
}

class Aria2RpcException(val code: Int, message: String) : Exception("aria2 RPC error $code: $message")
