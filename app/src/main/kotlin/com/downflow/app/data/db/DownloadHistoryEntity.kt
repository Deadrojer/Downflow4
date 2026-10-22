package com.downflow.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted record for completed / failed downloads.
 * aria2 is the source of truth for live tasks; this table only records
 * the terminal state so the user has a persistent history.
 */
@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey val gid: String,
    val fileName: String,
    val url: String,
    val destinationPath: String,
    val totalBytes: Long,
    val completedBytes: Long,
    val stateOrdinal: Int,        // maps to DownloadState enum
    val errorMessage: String?,
    val isTorrent: Boolean,
    val torrentName: String?,
    val completedAtMs: Long = System.currentTimeMillis(),
)
