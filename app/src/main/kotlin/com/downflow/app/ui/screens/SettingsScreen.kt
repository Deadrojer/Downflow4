package com.downflow.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.downflow.app.aria2.EngineState
import com.downflow.app.data.repository.DownloadRepository
import com.downflow.app.settings.AppSettings
import com.downflow.app.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettings,
    private val repository: DownloadRepository,
    private val storageManager: StorageManager,
) : ViewModel() {

    val maxConcurrent = settings.maxConcurrentDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)
    val maxConnections = settings.maxConnectionsPerServer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 16)
    val speedLimit = settings.globalSpeedLimit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0")
    val downloadDir = settings.downloadDir
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val downloadUri = settings.downloadUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val engineState = repository.engineState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EngineState.Stopped)

    fun setMaxConcurrent(v: Int) = viewModelScope.launch { settings.setMaxConcurrentDownloads(v) }
    fun setMaxConnections(v: Int) = viewModelScope.launch { settings.setMaxConnectionsPerServer(v) }
    fun setSpeedLimit(v: String) = viewModelScope.launch { settings.setGlobalSpeedLimit(v) }
    fun restartEngine() = repository.restartEngine()

    fun setDownloadFolder(uri: Uri) = viewModelScope.launch {
        storageManager.takePersistablePermission(uri)
        settings.setDownloadUri(uri.toString())
        val resolved = storageManager.resolveUriToPath(uri)
            ?: storageManager.defaultDownloadDir().absolutePath
        settings.setDownloadDir(resolved)
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val maxConcurrent by vm.maxConcurrent.collectAsState()
    val maxConnections by vm.maxConnections.collectAsState()
    val speedLimit by vm.speedLimit.collectAsState()
    val downloadDir by vm.downloadDir.collectAsState()
    val engineState by vm.engineState.collectAsState()

    var speedInput by remember(speedLimit) { mutableStateOf(speedLimit) }
    var showVpnDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.setDownloadFolder(it) } }

    if (showVpnDialog) VpnInfoDialog(
        onDismiss = { showVpnDialog = false },
        onOpenVpnSettings = {
            context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        onOpenProton = {
            val intent = context.packageManager
                .getLaunchIntentForPackage("ch.protonvpn.android")
            if (intent != null) context.startActivity(intent)
        }
    )
    if (showAboutDialog) AboutDialog(onDismiss = { showAboutDialog = false })

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Download Folder ──────────────────────────────────────────
            SettingsSection("Storage") {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Download folder", style = MaterialTheme.typography.bodyLarge)
                        Text(downloadDir.ifBlank { "Default app storage" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, "Choose folder")
                    }
                }
                Text(
                    "⚠ aria2 requires a direct filesystem path. Folders on external SD cards " +
                    "or cloud providers may fall back to app-internal storage.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Concurrency ──────────────────────────────────────────────
            SettingsSection("Concurrency") {
                LabeledSlider(
                    label = "Max simultaneous downloads",
                    value = maxConcurrent,
                    range = 1..10,
                    onValueChange = { vm.setMaxConcurrent(it) },
                )
                LabeledSlider(
                    label = "Max connections per server",
                    value = maxConnections,
                    range = 1..16,
                    onValueChange = { vm.setMaxConnections(it) },
                )
                OutlinedTextField(
                    value = speedInput,
                    onValueChange = { speedInput = it },
                    label = { Text("Global download speed limit") },
                    placeholder = { Text("0 = unlimited (e.g. 5M, 500K)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (speedInput != speedLimit) {
                            TextButton(onClick = { vm.setSpeedLimit(speedInput) }) {
                                Text("Apply")
                            }
                        }
                    }
                )
            }

            // ── Engine ───────────────────────────────────────────────────
            SettingsSection("Engine") {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Engine status", style = MaterialTheme.typography.bodyLarge)
                        val (stateText, stateColor) = when (engineState) {
                            is EngineState.Running  -> "Running"  to MaterialTheme.colorScheme.tertiary
                            is EngineState.Starting -> "Starting…" to MaterialTheme.colorScheme.secondary
                            is EngineState.Error    -> "Error: ${(engineState as EngineState.Error).message}" to MaterialTheme.colorScheme.error
                            is EngineState.Stopped  -> "Stopped" to MaterialTheme.colorScheme.outline
                        }
                        Text(stateText, style = MaterialTheme.typography.bodySmall, color = stateColor)
                    }
                    OutlinedButton(onClick = { vm.restartEngine() }) {
                        Icon(Icons.Default.RestartAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Restart")
                    }
                }
            }

            // ── VPN ──────────────────────────────────────────────────────
            SettingsSection("VPN") {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("VPN & routing", style = MaterialTheme.typography.bodyLarge)
                        Text("Downloads follow Android's active VPN",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showVpnDialog = true }) {
                        Icon(Icons.Default.Info, "VPN info")
                    }
                }
            }

            // ── About ────────────────────────────────────────────────────
            SettingsSection("About") {
                TextButton(onClick = { showAboutDialog = true },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("About DownFlow & Licenses")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$value", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

@Composable
private fun VpnInfoDialog(onDismiss: () -> Unit, onOpenVpnSettings: () -> Unit, onOpenProton: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.VpnKey, null) },
        title = { Text("VPN & Routing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Downloads follow Android's active VPN routing. If Proton VPN is connected and routing all traffic, downloads pass through the VPN tunnel automatically.")
                Text("For reliable VPN coverage, enable Always-on VPN and Block connections without VPN in Android VPN settings.")
                Text("DownFlow does not control or configure Proton VPN directly.")
            }
        },
        confirmButton = {
            TextButton(onClick = { onOpenVpnSettings(); onDismiss() }) { Text("VPN Settings") }
        },
        dismissButton = {
            TextButton(onClick = { onOpenProton(); onDismiss() }) { Text("Open Proton VPN") }
        }
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About DownFlow") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DownFlow v1.0.0")
                Divider()
                Text("Embedded aria2 Engine", style = MaterialTheme.typography.labelLarge)
                Text("This app embeds aria2, which is licensed under the GNU General Public License v2 (GPLv2+). Source: https://github.com/aria2/aria2")
                Divider()
                Text("RPC Security", style = MaterialTheme.typography.labelLarge)
                Text("The aria2 JSON-RPC server is bound exclusively to 127.0.0.1. The RPC secret is generated cryptographically at runtime and stored in Android Keystore–backed encrypted storage.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
