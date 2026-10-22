package com.downflow.app.ui.screens

import android.content.Intent
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.downflow.app.aria2.DownloadState
import com.downflow.app.data.db.DownloadHistoryEntity
import com.downflow.app.data.repository.DownloadRepository
import com.downflow.app.storage.StorageManager
import com.downflow.app.ui.components.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val storageManager: StorageManager,
) : ViewModel() {
    val history = repository.history.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    fun deleteEntry(gid: String) = viewModelScope.launch { repository.deleteHistoryEntry(gid) }
    fun clearAll() = viewModelScope.launch { repository.clearAllHistory() }
    fun getShareableUri(path: String) = storageManager.getShareableUri(File(path))
    fun openFileIntent(path: String) = storageManager.openFileIntent(File(path))
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsState()
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all history?") },
            text = { Text("This removes history records. Downloaded files are NOT deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearDialog = false }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, "Clear history")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text("No download history", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp) + padding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(history, key = { it.gid }) { entry ->
                    HistoryCard(
                        entry = entry,
                        onDelete = { viewModel.deleteEntry(entry.gid) },
                        onOpenFile = {
                            val file = File(entry.destinationPath)
                            if (file.exists()) {
                                val intent = viewModel.openFileIntent(entry.destinationPath)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: DownloadHistoryEntity,
    onDelete: () -> Unit,
    onOpenFile: () -> Unit,
) {
    val isSuccess = entry.stateOrdinal == DownloadState.COMPLETE.ordinal
    val dateStr = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        .format(Date(entry.completedAtMs))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dateStr, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (entry.totalBytes > 0)
                    Text(formatBytes(entry.totalBytes), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isSuccess && entry.errorMessage != null) {
                Text(entry.errorMessage, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (isSuccess) {
                    TextButton(onClick = onOpenFile) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open")
                    }
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteOutline, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove")
                }
            }
        }
    }
}

// PaddingValues operator to add
private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues =
    object : PaddingValues {
        override fun calculateLeftPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection) =
            this@plus.calculateLeftPadding(layoutDirection) + other.calculateLeftPadding(layoutDirection)
        override fun calculateTopPadding() =
            this@plus.calculateTopPadding() + other.calculateTopPadding()
        override fun calculateRightPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection) =
            this@plus.calculateRightPadding(layoutDirection) + other.calculateRightPadding(layoutDirection)
        override fun calculateBottomPadding() =
            this@plus.calculateBottomPadding() + other.calculateBottomPadding()
    }
