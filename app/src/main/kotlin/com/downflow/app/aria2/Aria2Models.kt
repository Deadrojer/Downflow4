package com.downflow.app.aria2

import com.google.gson.annotations.SerializedName

// ─────────────────────────────────────────────────────────────────────────────
// JSON-RPC envelope types
// ─────────────────────────────────────────────────────────────────────────────

data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: List<Any>
)

data class JsonRpcResponse<T>(
    val id: String?,
    val result: T?,
    val error: JsonRpcError?
)

data class JsonRpcError(
    val code: Int,
    val message: String
)

// ─────────────────────────────────────────────────────────────────────────────
// aria2 status / download info models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Maps to the object returned by aria2.tellStatus / aria2.tellActive etc.
 * Only the fields we actually use are listed; Gson ignores the rest.
 */
data class Aria2DownloadStatus(
    @SerializedName("gid")          val gid: String = "",
    @SerializedName("status")       val status: String = "",       // active|waiting|paused|error|complete|removed
    @SerializedName("totalLength")  val totalLength: String = "0",
    @SerializedName("completedLength") val completedLength: String = "0",
    @SerializedName("downloadSpeed")   val downloadSpeed: String = "0",
    @SerializedName("uploadSpeed")     val uploadSpeed: String = "0",
    @SerializedName("errorCode")    val errorCode: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null,
    @SerializedName("files")        val files: List<Aria2File> = emptyList(),
    @SerializedName("bittorrent")   val bittorrent: Aria2Bittorrent? = null,
    @SerializedName("dir")          val dir: String = "",
    @SerializedName("connections")  val connections: String = "0",
    @SerializedName("numSeeders")   val numSeeders: String? = null,
    @SerializedName("seeder")       val seeder: String? = null,
    @SerializedName("infoHash")     val infoHash: String? = null,
    @SerializedName("eta")          val eta: String? = null,
)

data class Aria2File(
    @SerializedName("index")            val index: String = "0",
    @SerializedName("path")             val path: String = "",
    @SerializedName("length")           val length: String = "0",
    @SerializedName("completedLength")  val completedLength: String = "0",
    @SerializedName("selected")         val selected: String = "true",
    @SerializedName("uris")             val uris: List<Aria2Uri> = emptyList(),
)

data class Aria2Uri(
    @SerializedName("uri")    val uri: String = "",
    @SerializedName("status") val status: String = "",  // used|waiting
)

data class Aria2Bittorrent(
    @SerializedName("info")       val info: Aria2TorrentInfo? = null,
    @SerializedName("announceList") val announceList: List<List<String>> = emptyList(),
    @SerializedName("mode")       val mode: String? = null,
)

data class Aria2TorrentInfo(
    @SerializedName("name") val name: String = "",
)

data class Aria2GlobalStat(
    @SerializedName("downloadSpeed")  val downloadSpeed: String = "0",
    @SerializedName("uploadSpeed")    val uploadSpeed: String = "0",
    @SerializedName("numActive")      val numActive: String = "0",
    @SerializedName("numWaiting")     val numWaiting: String = "0",
    @SerializedName("numStopped")     val numStopped: String = "0",
    @SerializedName("numStoppedTotal") val numStoppedTotal: String = "0",
)

data class Aria2Version(
    @SerializedName("version")          val version: String = "",
    @SerializedName("enabledFeatures")  val enabledFeatures: List<String> = emptyList(),
)

// ─────────────────────────────────────────────────────────────────────────────
// App-level domain model
// ─────────────────────────────────────────────────────────────────────────────

enum class DownloadState {
    ACTIVE, WAITING, PAUSED, COMPLETE, ERROR, REMOVED, UNKNOWN
}

data class DownloadItem(
    val gid: String,
    val state: DownloadState,
    val fileName: String,
    val url: String,
    val dir: String,
    val totalBytes: Long,
    val completedBytes: Long,
    val speedBytesPerSec: Long,
    val errorMessage: String?,
    val isTorrent: Boolean,
    val torrentName: String?,
    val infoHash: String?,
    val connections: Int,
) {
    val progressPercent: Float get() =
        if (totalBytes > 0) completedBytes.toFloat() / totalBytes.toFloat() else 0f

    val etaSeconds: Long? get() =
        if (speedBytesPerSec > 0 && totalBytes > completedBytes)
            (totalBytes - completedBytes) / speedBytesPerSec
        else null
}

fun Aria2DownloadStatus.toDomain(): DownloadItem {
    val state = when (status) {
        "active"   -> DownloadState.ACTIVE
        "waiting"  -> DownloadState.WAITING
        "paused"   -> DownloadState.PAUSED
        "complete" -> DownloadState.COMPLETE
        "error"    -> DownloadState.ERROR
        "removed"  -> DownloadState.REMOVED
        else       -> DownloadState.UNKNOWN
    }
    val firstFile = files.firstOrNull()
    val rawPath = firstFile?.path ?: ""
    val fileName = if (rawPath.isNotBlank()) rawPath.substringAfterLast('/') else gid
    val url = firstFile?.uris?.firstOrNull()?.uri ?: ""
    val isTorrent = bittorrent != null
    return DownloadItem(
        gid = gid,
        state = state,
        fileName = if (isTorrent && bittorrent?.info?.name?.isNotBlank() == true)
            bittorrent.info.name else fileName,
        url = url,
        dir = dir,
        totalBytes = totalLength.toLongOrNull() ?: 0L,
        completedBytes = completedLength.toLongOrNull() ?: 0L,
        speedBytesPerSec = downloadSpeed.toLongOrNull() ?: 0L,
        errorMessage = errorMessage,
        isTorrent = isTorrent,
        torrentName = bittorrent?.info?.name,
        infoHash = infoHash,
        connections = connections.toIntOrNull() ?: 0,
    )
}
