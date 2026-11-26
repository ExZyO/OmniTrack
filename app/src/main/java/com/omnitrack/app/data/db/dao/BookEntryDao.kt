package com.omnitrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.omnitrack.app.data.db.entity.BookEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookEntryDao {
    @Query("SELECT * FROM book_entries WHERE mediaId = :mediaId ORDER BY sortOrder ASC, id ASC")
    fun getEntriesForMedia(mediaId: Long): Flow<List<BookEntryEntity>>

    @Query("SELECT * FROM book_entries WHERE mediaId = :mediaId ORDER BY sortOrder ASC, id ASC")
    suspend fun getEntriesForMediaOnce(mediaId: Long): List<BookEntryEntity>

    @Query("SELECT * FROM book_entries")
    suspend fun getAllEntriesOnce(): List<BookEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: BookEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<BookEntryEntity>)

    @Update
    suspend fun updateEntry(entry: BookEntryEntity)

    @Query("DELETE FROM book_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM book_entries WHERE mediaId = :mediaId")
    suspend fun deleteEntriesForMedia(mediaId: Long)

    @Query("DELETE FROM book_entries")
    suspend fun deleteAllEntries()

    @Query("SELECT COUNT(*) FROM book_entries WHERE mediaId NOT IN (SELECT id FROM media_items)")
    suspend fun countOrphaned(): Int

    @Query("DELETE FROM book_entries WHERE mediaId NOT IN (SELECT id FROM media_items)")
    suspend fun deleteOrphaned(): Int
}
