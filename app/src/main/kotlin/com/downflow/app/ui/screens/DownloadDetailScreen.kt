package com.downflow.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.downflow.app.aria2.DownloadItem
import com.downflow.app.aria2.DownloadState
import com.downflow.app.ui.DownloadsViewModel
import com.downflow.app.ui.components.*

@Composable
fun DownloadDetailScreen(
    gid: String,
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val allItems = state.active + state.waiting + state.paused + state.completed + state.failed
    val item = allItems.firstOrNull { it.gid == gid }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (item == null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                Text("Download not found", modifier = Modifier.padding(16.dp))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status + name
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StateChip(item.state)
                        if (item.isTorrent) {
                            SuggestionChip(onClick = {}, label = { Text("Torrent", style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    Text(item.fileName.ifBlank { item.gid }, style = MaterialTheme.typography.titleMedium)
                    if (!item.url.isNullOrBlank()) {
                        Text(item.url, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Progress
            if (item.totalBytes > 0) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Progress", style = MaterialTheme.typography.labelLarge)
                        LinearProgressIndicator(
                            progress = { item.progressPercent },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${(item.progressPercent * 100).toInt()}%")
                            Text("${formatBytes(item.completedBytes)} / ${formatBytes(item.totalBytes)}")
                        }
                        if (item.state == DownloadState.ACTIVE) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                SpeedBadge(speedBps = item.speedBytesPerSec)
                                item.etaSeconds?.let { Text("ETA: ${formatEta(it)}") }
                            }
                        }
                    }
                }
            }

            // Details
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Details", style = MaterialTheme.typography.labelLarge)
                    DetailRow("GID", item.gid)
                    DetailRow("Destination", item.dir)
                    DetailRow("Connections", item.connections.toString())
                    if (item.infoHash != null) DetailRow("Info Hash", item.infoHash)
                    if (item.torrentName != null) DetailRow("Torrent Name", item.torrentName)
                    if (item.errorMessage != null) {
                        DetailRow("Error", item.errorMessage,
                            valueColor = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (item.state) {
                    DownloadState.ACTIVE -> {
                        OutlinedButton(onClick = { viewModel.pause(gid) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Pause, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                    }
                    DownloadState.PAUSED, DownloadState.WAITING -> {
                        Button(onClick = { viewModel.resume(gid) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Resume")
                        }
                    }
                    DownloadState.ERROR -> {
                        Button(onClick = { viewModel.addDownload(item.url); onBack() },
                            modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Retry")
                        }
                    }
                    else -> {}
                }
                OutlinedButton(
                    onClick = {
                        if (item.state in listOf(DownloadState.COMPLETE, DownloadState.ERROR))
                            viewModel.removeResult(gid)
                        else
                            viewModel.remove(gid)
                        onBack()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            modifier = Modifier.weight(0.6f))
    }
}
