package com.downflow.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadHistoryDao {

    @Query("SELECT * FROM download_history ORDER BY completedAtMs DESC")
    fun observeAll(): Flow<List<DownloadHistoryEntity>>

    @Query("SELECT * FROM download_history WHERE gid = :gid LIMIT 1")
    suspend fun findByGid(gid: String): DownloadHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadHistoryEntity)

    @Delete
    suspend fun delete(entity: DownloadHistoryEntity)

    @Query("DELETE FROM download_history WHERE gid = :gid")
    suspend fun deleteByGid(gid: String)

    @Query("DELETE FROM download_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM download_history")
    suspend fun count(): Int
}
