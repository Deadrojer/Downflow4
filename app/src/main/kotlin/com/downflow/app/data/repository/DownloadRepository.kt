package com.downflow.app.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.downflow.app.aria2.DownloadItem
import com.downflow.app.aria2.DownloadState
import com.downflow.app.data.db.AppDatabase
import com.downflow.app.data.db.DownloadHistoryEntity
import com.downflow.app.service.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for download data consumed by the UI.
 *
 * Live (in-flight) downloads come from [DownloadService] via its bound
 * service interface.  Terminal downloads (complete / error) are persisted
 * in Room and exposed as a Flow so history survives app restarts.
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    private val _service = MutableStateFlow<DownloadService?>(null)

    val liveDownloads: Flow<List<DownloadItem>> = _service
        .filterNotNull()
        .flatMapLatest { it.downloads }
        .distinctUntilChanged()

    val globalSpeedBps: Flow<Long> = _service
        .filterNotNull()
        .flatMapLatest { it.globalSpeedBps }

    val engineState = _service.filterNotNull().flatMapLatest { it.engineState }

    val history = db.historyDao().observeAll()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            _service.value = (binder as DownloadService.LocalBinder).getService()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            _service.value = null
        }
    }

    fun bindService() {
        val intent = Intent(context, DownloadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        try { context.unbindService(connection) } catch (_: Exception) {}
    }

    // ─── Delegation to service ───────────────────────────────────────────────

    suspend fun addUri(urls: List<String>, options: Map<String, String> = emptyMap()): Result<String> =
        _service.value?.addUri(urls, options) ?: Result.failure(Exception("Service not connected"))

    suspend fun addTorrent(base64: String, options: Map<String, String> = emptyMap()): Result<String> =
        _service.value?.addTorrent(base64, options) ?: Result.failure(Exception("Service not connected"))

    suspend fun pause(gid: String): Result<String>   = _service.value?.pause(gid) ?: notConnected()
    suspend fun resume(gid: String): Result<String>  = _service.value?.unpause(gid) ?: notConnected()
    suspend fun remove(gid: String): Result<String>  = _service.value?.remove(gid) ?: notConnected()
    suspend fun removeResult(gid: String): Result<String> = _service.value?.removeResult(gid) ?: notConnected()
    suspend fun purgeCompleted(): Result<String>          = _service.value?.purgeCompleted() ?: notConnected()
    fun restartEngine()                              { _service.value?.restartEngine() }

    // ─── History helpers ─────────────────────────────────────────────────────

    suspend fun recordHistory(item: DownloadItem) {
        if (item.state !in listOf(DownloadState.COMPLETE, DownloadState.ERROR)) return
        db.historyDao().upsert(
            DownloadHistoryEntity(
                gid = item.gid,
                fileName = item.fileName,
                url = item.url,
                destinationPath = "${item.dir}/${item.fileName}",
                totalBytes = item.totalBytes,
                completedBytes = item.completedBytes,
                stateOrdinal = item.state.ordinal,
                errorMessage = item.errorMessage,
                isTorrent = item.isTorrent,
                torrentName = item.torrentName,
            )
        )
    }

    suspend fun deleteHistoryEntry(gid: String) =
        db.historyDao().deleteByGid(gid)

    suspend fun clearAllHistory() =
        db.historyDao().deleteAll()

    private fun <T> notConnected(): Result<T> =
        Result.failure(Exception("Service not connected"))
}
