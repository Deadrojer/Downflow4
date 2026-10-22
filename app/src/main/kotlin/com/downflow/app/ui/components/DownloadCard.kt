package com.downflow.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.downflow.app.aria2.DownloadItem
import com.downflow.app.aria2.DownloadState

@Composable
fun DownloadCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onDetails: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetails),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // File name + status chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (item.isTorrent) Icons.Default.Lan else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.fileName.ifBlank { item.gid },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StateChip(item.state)
            }

            // Progress bar (only meaningful when we know total size)
            if (item.totalBytes > 0 || item.state == DownloadState.ACTIVE) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.progressPercent },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${formatBytes(item.completedBytes)} / ${formatBytes(item.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.state == DownloadState.ACTIVE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SpeedBadge(speedBps = item.speedBytesPerSec)
                            item.etaSeconds?.let { eta ->
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "ETA ${formatEta(eta)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Error message
            if (item.state == DownloadState.ERROR && item.errorMessage != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Action row
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                when (item.state) {
                    DownloadState.ACTIVE -> {
                        TextButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                    }
                    DownloadState.PAUSED, DownloadState.WAITING -> {
                        TextButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume")
                        }
                    }
                    DownloadState.ERROR -> {
                        TextButton(onClick = onResume) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry")
                        }
                    }
                    else -> {}
                }
                TextButton(onClick = onRemove, colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.DeleteOutline, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
fun StateChip(state: DownloadState) {
    val (label, color) = when (state) {
        DownloadState.ACTIVE   -> "Active"   to MaterialTheme.colorScheme.primary
        DownloadState.WAITING  -> "Queued"   to MaterialTheme.colorScheme.secondary
        DownloadState.PAUSED   -> "Paused"   to MaterialTheme.colorScheme.outline
        DownloadState.COMPLETE -> "Done"     to MaterialTheme.colorScheme.tertiary
        DownloadState.ERROR    -> "Error"    to MaterialTheme.colorScheme.error
        DownloadState.REMOVED  -> "Removed"  to MaterialTheme.colorScheme.outline
        DownloadState.UNKNOWN  -> "Unknown"  to MaterialTheme.colorScheme.outline
    }
    SuggestionChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            labelColor = color,
        ),
        modifier = Modifier.height(24.dp),
    )
}

@Composable
fun SpeedBadge(speedBps: Long) {
    if (speedBps <= 0) return
    Text(
        text = formatSpeed(speedBps),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024         -> "%.0f KB".format(bytes / 1_024.0)
    else                   -> "$bytes B"
}

fun formatSpeed(bps: Long): String = when {
    bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
    bps >= 1_000     -> "%.0f KB/s".format(bps / 1_000.0)
    else             -> "$bps B/s"
}

fun formatEta(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0  -> "${h}h ${m}m"
        m > 0  -> "${m}m ${s}s"
        else   -> "${s}s"
    }
}
