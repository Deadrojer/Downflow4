package com.downflow.app.aria2

import android.content.Context
import android.util.Log
import com.downflow.app.settings.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Aria2ProcessManager"
private const val BINARY_ASSET_NAME = "aria2c"
private const val STARTUP_TIMEOUT_MS = 10_000L
private const val PING_INTERVAL_MS = 2_000L
private const val MAX_RESTART_ATTEMPTS = 3
private const val RESTART_BACKOFF_MS = 5_000L

sealed class EngineState {
    object Stopped    : EngineState()
    object Starting   : EngineState()
    object Running    : EngineState()
    data class Error(val message: String) : EngineState()
}

/**
 * Manages the lifecycle of the embedded aria2 process.
 *
 * Binary delivery
 * ───────────────
 * The aria2c ARM64 binary is stored in src/main/assets/aria2c.
 * On first run (or if the binary is missing / outdated) it is copied to
 * [Context.getFilesDir]/bin/aria2c and made executable.
 *
 * The binary is NOT included as a .so in jniLibs because it is not a
 * shared library — it is a standalone executable.  Android's dynamic
 * linker is not involved.  The executable must be compiled against
 * Android's Bionic libc (not glibc) — see scripts/build_aria2_android.sh.
 *
 * Security
 * ────────
 * • RPC is bound to 127.0.0.1 only.
 * • The RPC secret is generated at first run, stored in EncryptedSharedPreferences
 *   backed by Android Keystore.
 * • Arguments are passed as a structured list — no shell interpolation.
 */
@Singleton
class Aria2ProcessManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rpcClient: Aria2RpcClient,
    private val appSettings: AppSettings,
) {
    private val _engineState = MutableStateFlow<EngineState>(EngineState.Stopped)
    val engineState: StateFlow<EngineState> = _engineState

    private var process: Process? = null
    private var watchdogJob: Job? = null
    private var restartAttempts = 0

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── Binary management ───────────────────────────────────────────────────

    private fun binaryFile(): File =
        File(context.filesDir, "bin/aria2c")

    /**
     * Copies the asset binary to app-private storage on first run or version bump.
     * Returns false if the asset is absent (the build is missing the binary).
     */
    fun installBinaryIfNeeded(): Boolean {
        val dest = binaryFile()
        dest.parentFile?.mkdirs()

        // Always reinstall to pick up APK updates
        return try {
            context.assets.open(BINARY_ASSET_NAME).use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            dest.setExecutable(true, true)
            Log.i(TAG, "aria2c binary installed to ${dest.absolutePath}")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to install aria2c binary — asset '$BINARY_ASSET_NAME' not found. " +
                    "Build the binary with scripts/build_aria2_android.sh and place it in " +
                    "app/src/main/assets/aria2c", e)
            false
        }
    }

    // ─── Startup ─────────────────────────────────────────────────────────────

    suspend fun start(): Boolean {
        if (_engineState.value is EngineState.Running) {
            Log.d(TAG, "Engine already running, skipping start")
            return true
        }
        if (_engineState.value is EngineState.Starting) {
            Log.d(TAG, "Engine already starting")
            return false
        }

        _engineState.value = EngineState.Starting

        if (!installBinaryIfNeeded()) {
            _engineState.value = EngineState.Error("aria2c binary not found in APK assets. " +
                    "Build it with scripts/build_aria2_android.sh.")
            return false
        }

        val binary = binaryFile()
        if (!binary.canExecute()) {
            _engineState.value = EngineState.Error("aria2c binary is not executable.")
            return false
        }

        val settings = appSettings.snapshot()
        val port = settings.rpcPort
        val secret = settings.rpcSecret
        val downloadDir = settings.downloadDir

        rpcClient.configure(port, secret)

        val sessionFile = File(context.filesDir, "aria2/aria2.session").also {
            it.parentFile?.mkdirs()
            if (!it.exists()) it.createNewFile()
        }

        // Structured argument list — no shell; no injection risk
        val args = buildList {
            add(binary.absolutePath)
            add("--enable-rpc=true")
            add("--rpc-listen-all=false")              // loopback only
            add("--rpc-listen-port=$port")
            add("--rpc-secret=$secret")                // never logged
            add("--rpc-allow-origin-all=false")
            add("--dir=$downloadDir")
            add("--max-concurrent-downloads=${settings.maxConcurrentDownloads}")
            add("--max-connection-per-server=${settings.maxConnectionsPerServer}")
            if (settings.globalSpeedLimit.isNotBlank() && settings.globalSpeedLimit != "0") {
                add("--max-overall-download-limit=${settings.globalSpeedLimit}")
            }
            add("--save-session=${sessionFile.absolutePath}")
            add("--save-session-interval=60")
            add("--input-file=${sessionFile.absolutePath}")
            add("--continue=true")
            add("--auto-file-renaming=true")
            add("--log-level=warn")
            add("--daemon=false")            // we manage the process ourselves
        }

        return try {
            process = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()

            // Wait for RPC to become available
            val ready = waitForReady()
            if (ready) {
                _engineState.value = EngineState.Running
                restartAttempts = 0
                startWatchdog()
                Log.i(TAG, "aria2 engine started on port $port")
            } else {
                _engineState.value = EngineState.Error("Engine did not become ready within timeout")
                process?.destroyForcibly()
                process = null
            }
            ready
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start aria2", e)
            _engineState.value = EngineState.Error(e.message ?: "Unknown error")
            false
        }
    }

    private suspend fun waitForReady(): Boolean {
        val deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (rpcClient.ping()) return true
            delay(500)
        }
        return false
    }

    // ─── Watchdog ────────────────────────────────────────────────────────────

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                if (_engineState.value !is EngineState.Running) break

                val alive = rpcClient.ping()
                if (!alive) {
                    Log.w(TAG, "aria2 ping failed — process may have died")
                    val procAlive = try { process?.exitValue(); false } catch (_: IllegalThreadStateException) { true }
                    if (!procAlive) {
                        Log.e(TAG, "aria2 process exited unexpectedly")
                        _engineState.value = EngineState.Error("Engine exited unexpectedly")
                        attemptRestart()
                        break
                    }
                }
            }
        }
    }

    private suspend fun attemptRestart() {
        if (restartAttempts >= MAX_RESTART_ATTEMPTS) {
            Log.e(TAG, "Max restart attempts reached — not restarting aria2")
            _engineState.value = EngineState.Error("Engine crashed repeatedly. Tap Restart in Settings.")
            return
        }
        restartAttempts++
        Log.w(TAG, "Attempting aria2 restart ($restartAttempts/$MAX_RESTART_ATTEMPTS)")
        delay(RESTART_BACKOFF_MS * restartAttempts)
        start()
    }

    // ─── Shutdown ────────────────────────────────────────────────────────────

    suspend fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null

        // Graceful: ask aria2 to save session and shutdown
        try {
            rpcClient.shutdown()
            delay(2_000)
        } catch (_: Exception) {}

        // Force-kill if still alive
        process?.let { p ->
            try {
                p.exitValue() // throws if still alive
            } catch (_: IllegalThreadStateException) {
                p.destroyForcibly()
            }
        }
        process = null
        _engineState.value = EngineState.Stopped
        Log.i(TAG, "aria2 engine stopped")
    }

    fun isRunning(): Boolean = _engineState.value is EngineState.Running
}
