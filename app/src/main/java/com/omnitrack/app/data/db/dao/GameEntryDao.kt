package com.omnitrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.omnitrack.app.data.db.entity.GameEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameEntryDao {
    @Query("SELECT * FROM game_entries WHERE mediaId = :mediaId ORDER BY sortOrder ASC, id ASC")
    fun getEntriesForMedia(mediaId: Long): Flow<List<GameEntryEntity>>

    @Query("SELECT * FROM game_entries WHERE mediaId = :mediaId ORDER BY sortOrder ASC, id ASC")
    suspend fun getEntriesForMediaOnce(mediaId: Long): List<GameEntryEntity>

    @Query("SELECT * FROM game_entries")
    suspend fun getAllEntriesOnce(): List<GameEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: GameEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<GameEntryEntity>)

    @Update
    suspend fun updateEntry(entry: GameEntryEntity)

    @Query("DELETE FROM game_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM game_entries WHERE mediaId = :mediaId")
    suspend fun deleteEntriesForMedia(mediaId: Long)

    @Query("DELETE FROM game_entries")
    suspend fun deleteAllEntries()
}
