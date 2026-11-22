package com.omnitrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omnitrack.app.data.db.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue ORDER BY timestamp ASC")
    fun getPendingTasks(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue ORDER BY timestamp ASC")
    suspend fun getPendingTasksSnapshot(): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(task: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE mediaId = :mediaId AND trackerType = :trackerType")
    suspend fun deletePendingFor(mediaId: Long, trackerType: String)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long)

    @Query("DELETE FROM sync_queue WHERE trackerType = :trackerType")
    suspend fun deleteAllForTracker(trackerType: String)

    @Query("DELETE FROM sync_queue")
    suspend fun clearQueue()
}
