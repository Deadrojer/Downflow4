package com.downflow.app.ui

import android.net.Uri
import android.util.Base64
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.downflow.app.aria2.DownloadItem
import com.downflow.app.aria2.DownloadState
import com.downflow.app.aria2.EngineState
import com.downflow.app.data.repository.DownloadRepository
import com.downflow.app.settings.AppSettings
import com.downflow.app.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val active: List<DownloadItem> = emptyList(),
    val waiting: List<DownloadItem> = emptyList(),
    val paused: List<DownloadItem> = emptyList(),
    val completed: List<DownloadItem> = emptyList(),
    val failed: List<DownloadItem> = emptyList(),
    val globalSpeedBps: Long = 0L,
    val engineState: EngineState = EngineState.Stopped,
    val isLoading: Boolean = false,
    val userMessage: String? = null,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val appSettings: AppSettings,
    private val storageManager: StorageManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState(isLoading = true))
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        observeDownloads()
        observeGlobalSpeed()
        observeEngineState()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            repository.liveDownloads.collect { items ->
                // Persist terminal-state items to history
                items.filter { it.state in listOf(DownloadState.COMPLETE, DownloadState.ERROR) }
                    .forEach { repository.recordHistory(it) }

                _uiState.update { state ->
                    state.copy(
                        active    = items.filter { it.state == DownloadState.ACTIVE },
                        waiting   = items.filter { it.state == DownloadState.WAITING },
                        paused    = items.filter { it.state == DownloadState.PAUSED },
                        completed = items.filter { it.state == DownloadState.COMPLETE },
                        failed    = items.filter { it.state == DownloadState.ERROR },
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun observeGlobalSpeed() {
        viewModelScope.launch {
            repository.globalSpeedBps.collect { speed ->
                _uiState.update { it.copy(globalSpeedBps = speed) }
            }
        }
    }

    private fun observeEngineState() {
        viewModelScope.launch {
            repository.engineState.collect { state ->
                _uiState.update { it.copy(engineState = state) }
            }
        }
    }

    // ─── Actions ─────────────────────────────────────────────────────────────

    fun addDownload(input: String) {
        viewModelScope.launch {
            val trimmed = input.trim()
            if (trimmed.isBlank()) {
                showMessage("Please enter a URL or magnet link")
                return@launch
            }

            val dir = appSettings.downloadDir.first()
            val options = mapOf("dir" to dir)

            when {
                trimmed.startsWith("magnet:") -> {
                    repository.addUri(listOf(trimmed), options)
                        .onFailure { showMessage("Failed to add magnet: ${it.message}") }
                }
                trimmed.endsWith(".torrent", ignoreCase = true) &&
                        (trimmed.startsWith("http") || trimmed.startsWith("ftp")) -> {
                    repository.addUri(listOf(trimmed), options)
                        .onFailure { showMessage("Failed to add torrent URL: ${it.message}") }
                }
                isValidUrl(trimmed) -> {
                    repository.addUri(listOf(trimmed), options)
                        .onFailure { showMessage("Failed to add: ${it.message}") }
                }
                else -> showMessage("Invalid URL: $trimmed")
            }
        }
    }

    fun addTorrentFile(bytes: ByteArray) {
        viewModelScope.launch {
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val dir = appSettings.downloadDir.first()
            repository.addTorrent(base64, mapOf("dir" to dir))
                .onFailure { showMessage("Failed to add torrent: ${it.message}") }
        }
    }

    fun pause(gid: String) = viewModelScope.launch {
        repository.pause(gid).onFailure { showMessage(it.message ?: "Pause failed") }
    }

    fun resume(gid: String) = viewModelScope.launch {
        repository.resume(gid).onFailure { showMessage(it.message ?: "Resume failed") }
    }

    fun remove(gid: String) = viewModelScope.launch {
        repository.remove(gid).onFailure { showMessage(it.message ?: "Remove failed") }
    }

    fun removeResult(gid: String) = viewModelScope.launch {
        repository.removeResult(gid)
    }

    fun purgeCompleted() = viewModelScope.launch {
        repository.purgeCompleted()
    }

    fun restartEngine() = repository.restartEngine()

    fun clearMessage() = _uiState.update { it.copy(userMessage = null) }

    private fun showMessage(msg: String) =
        _uiState.update { it.copy(userMessage = msg) }

    private fun isValidUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://") ||
                url.startsWith("ftp://") || Patterns.WEB_URL.matcher(url).matches()
}
