package com.downflow.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.downflow.app.aria2.DownloadItem
import com.downflow.app.aria2.DownloadState
import com.downflow.app.aria2.EngineState
import com.downflow.app.ui.DownloadsViewModel
import com.downflow.app.ui.components.DownloadCard
import com.downflow.app.ui.components.SpeedBadge

@Composable
fun DownloadsScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var urlInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Snackbar for user messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                actions = {
                    SpeedBadge(speedBps = state.globalSpeedBps)
                    EngineStatusIcon(state.engineState)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── URL input row ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("URL / Magnet") },
                    singleLine = true,
                    trailingIcon = {
                        if (urlInput.isNotBlank()) {
                            IconButton(onClick = { urlInput = "" }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    }
                )
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val pasted = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    urlInput = pasted
                }) {
                    Icon(Icons.Default.ContentPaste, "Paste")
                }
                FilledIconButton(onClick = {
                    viewModel.addDownload(urlInput)
                    urlInput = ""
                }) {
                    Icon(Icons.Default.Add, "Add download")
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            val allEmpty = state.active.isEmpty() && state.waiting.isEmpty() &&
                    state.paused.isEmpty() && state.completed.isEmpty() && state.failed.isEmpty()

            if (allEmpty) {
                EmptyState()
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.active.isNotEmpty()) {
                    item { SectionHeader("Active (${state.active.size})") }
                    items(state.active, key = { it.gid }) { item ->
                        DownloadCard(
                            item = item,
                            onPause = { viewModel.pause(item.gid) },
                            onResume = { viewModel.resume(item.gid) },
                            onRemove = { viewModel.remove(item.gid) },
                            onDetails = { onNavigateToDetail(item.gid) },
                        )
                    }
                }
                if (state.waiting.isNotEmpty()) {
                    item { SectionHeader("Queued (${state.waiting.size})") }
                    items(state.waiting, key = { it.gid }) { item ->
                        DownloadCard(item = item,
                            onPause = { viewModel.pause(item.gid) },
                            onResume = { viewModel.resume(item.gid) },
                            onRemove = { viewModel.remove(item.gid) },
                            onDetails = { onNavigateToDetail(item.gid) })
                    }
                }
                if (state.paused.isNotEmpty()) {
                    item { SectionHeader("Paused (${state.paused.size})") }
                    items(state.paused, key = { it.gid }) { item ->
                        DownloadCard(item = item,
                            onPause = { viewModel.pause(item.gid) },
                            onResume = { viewModel.resume(item.gid) },
                            onRemove = { viewModel.remove(item.gid) },
                            onDetails = { onNavigateToDetail(item.gid) })
                    }
                }
                if (state.completed.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader("Completed (${state.completed.size})", Modifier.weight(1f))
                            TextButton(onClick = { viewModel.purgeCompleted() }) {
                                Text("Clear All")
                            }
                        }
                    }
                    items(state.completed, key = { it.gid }) { item ->
                        DownloadCard(item = item,
                            onPause = {},
                            onResume = {},
                            onRemove = { viewModel.removeResult(item.gid) },
                            onDetails = { onNavigateToDetail(item.gid) })
                    }
                }
                if (state.failed.isNotEmpty()) {
                    item { SectionHeader("Failed (${state.failed.size})") }
                    items(state.failed, key = { it.gid }) { item ->
                        DownloadCard(item = item,
                            onPause = {},
                            onResume = { viewModel.addDownload(item.url) }, // retry
                            onRemove = { viewModel.removeResult(item.gid) },
                            onDetails = { onNavigateToDetail(item.gid) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun EngineStatusIcon(state: EngineState) {
    val (icon, tint, desc) = when (state) {
        is EngineState.Running  -> Triple(Icons.Default.CheckCircle,  MaterialTheme.colorScheme.primary, "Engine running")
        is EngineState.Starting -> Triple(Icons.Default.HourglassTop, MaterialTheme.colorScheme.secondary, "Engine starting")
        is EngineState.Error    -> Triple(Icons.Default.Error,         MaterialTheme.colorScheme.error, "Engine error")
        is EngineState.Stopped  -> Triple(Icons.Default.Cancel,        MaterialTheme.colorScheme.outline, "Engine stopped")
    }
    Icon(icon, contentDescription = desc, tint = tint,
        modifier = Modifier.padding(end = 8.dp))
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))
            Text("No downloads yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Paste a URL above or browse to a file",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline)
        }
    }
}
