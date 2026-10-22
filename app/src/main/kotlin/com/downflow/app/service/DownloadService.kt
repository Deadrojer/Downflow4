package com.downflow.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.downflow.app.aria2.Aria2ProcessManager
import com.downflow.app.aria2.Aria2RpcClient
import com.downflow.app.aria2.DownloadItem
import com.downflow.app.aria2.DownloadState
import com.downflow.app.aria2.toDomain
import com.downflow.app.notifications.NOTIF_ID_ENGINE
import com.downflow.app.notifications.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

private const val TAG = "DownloadService"
private const val POLL_INTERVAL_MS = 2_000L

/**
 * Foreground service that:
 * 1. Keeps the aria2 process alive.
 * 2. Polls aria2 for download status every [POLL_INTERVAL_MS].
 * 3. Exposes live download state to the UI via [StateFlow].
 * 4. Updates the persistent notification.
 *
 * Lifecycle notes
 * ───────────────
 * • Started as a foreground service so Android preserves it while downloading.
 * • Bound by the Activity/ViewModel for two-way communication.
 * • stopWithTask=false in the manifest so it keeps running when the user
 *   swipes away the app (active downloads continue).
 * • Android does NOT guarantee unlimited background execution.  If the OS
 *   kills the service, aria2 dies with it and resumable downloads will resume
 *   on next start.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var processManager: Aria2ProcessManager
    @Inject lateinit var rpcClient: Aria2RpcClient
    @Inject lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _globalSpeedBps = MutableStateFlow(0L)
    val globalSpeedBps: StateFlow<Long> = _globalSpeedBps.asStateFlow()

    private var pollJob: Job? = null

    // ─── Service lifecycle ───────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        // On API 34+ we must pass the foreground service type that matches the manifest.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID_ENGINE,
                notificationHelper.buildEngineNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIF_ID_ENGINE, notificationHelper.buildEngineNotification())
        }
        startEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALL -> {
                serviceScope.launch { rpcClient.pauseAll() }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        pollJob?.cancel()
        serviceScope.launch {
            processManager.stop()
            serviceScope.cancel()
        }
    }

    // ─── Binder ──────────────────────────────────────────────────────────────

    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder = binder

    // ─── Engine startup + polling ────────────────────────────────────────────

    private fun startEngine() {
        serviceScope.launch {
            val started = processManager.start()
            if (started) {
                startPolling()
            }
        }
    }

    fun restartEngine() {
        serviceScope.launch {
            processManager.stop()
            pollJob?.cancel()
            val started = processManager.start()
            if (started) startPolling()
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = serviceScope.launch {
            while (isActive) {
                fetchAndEmit()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun fetchAndEmit() {
        val active  = rpcClient.tellActive().getOrDefault(emptyList())
        val waiting = rpcClient.tellWaiting().getOrDefault(emptyList())
        val stopped = rpcClient.tellStopped().getOrDefault(emptyList())
        val all = (active + waiting + stopped).map { it.toDomain() }
        _downloads.value = all

        val stat = rpcClient.getGlobalStat().getOrNull()
        val speed = stat?.downloadSpeed?.toLongOrNull() ?: 0L
        _globalSpeedBps.value = speed

        val activeCount = active.size
        notificationHelper.updateEngineNotification(activeCount, speed)
    }

    // ─── Public API (called from ViewModel via binder) ───────────────────────

    suspend fun addUri(urls: List<String>, options: Map<String, String> = emptyMap()): Result<String> =
        rpcClient.addUri(urls, options)

    suspend fun addTorrent(base64: String, options: Map<String, String> = emptyMap()): Result<String> =
        rpcClient.addTorrent(base64, options)

    suspend fun pause(gid: String): Result<String>   = rpcClient.pause(gid)
    suspend fun unpause(gid: String): Result<String> = rpcClient.unpause(gid)
    suspend fun remove(gid: String): Result<String>  = rpcClient.forceRemove(gid)

    suspend fun removeResult(gid: String): Result<String> = rpcClient.removeDownloadResult(gid)
    suspend fun purgeCompleted(): Result<String>          = rpcClient.purgeDownloadResult()

    val engineState = processManager.engineState

    companion object {
        const val ACTION_STOP_ALL = "com.downflow.app.STOP_ALL"
    }
}

private fun <T> Result<T>.getOrDefault(default: T): T = getOrElse { default }
