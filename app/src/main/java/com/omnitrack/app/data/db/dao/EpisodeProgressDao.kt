package com.omnitrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omnitrack.app.data.db.entity.EpisodeProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeProgressDao {
    @Query("SELECT * FROM episode_progress WHERE mediaId = :mediaId")
    fun getProgressForMedia(mediaId: Long): Flow<List<EpisodeProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(progress: EpisodeProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(progressList: List<EpisodeProgressEntity>)

    @Query("DELETE FROM episode_progress WHERE mediaId = :mediaId AND seasonNumber = :seasonNumber AND episodeNumber = :episodeNumber")
    suspend fun deleteEpisodeProgress(mediaId: Long, seasonNumber: Int, episodeNumber: Int)

    @Query("DELETE FROM episode_progress WHERE mediaId = :mediaId")
    suspend fun clearProgressForMedia(mediaId: Long)
}
