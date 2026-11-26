package com.omnitrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omnitrack.app.data.db.entity.SeasonMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeasonMetaDao {
    @Query("SELECT * FROM season_meta WHERE mediaId = :mediaId")
    fun observeForMedia(mediaId: Long): Flow<List<SeasonMetaEntity>>

    @Query("SELECT * FROM season_meta WHERE mediaId = :mediaId")
    suspend fun getForMediaOnce(mediaId: Long): List<SeasonMetaEntity>

    @Query("SELECT * FROM season_meta")
    suspend fun getAllOnce(): List<SeasonMetaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: SeasonMetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SeasonMetaEntity>)

    @Query("DELETE FROM season_meta WHERE mediaId = :mediaId")
    suspend fun deleteForMedia(mediaId: Long)

    @Query("DELETE FROM season_meta")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM season_meta WHERE mediaId NOT IN (SELECT id FROM media_items)")
    suspend fun countOrphaned(): Int

    @Query("DELETE FROM season_meta WHERE mediaId NOT IN (SELECT id FROM media_items)")
    suspend fun deleteOrphaned(): Int
}
