package com.omnitrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.omnitrack.app.data.db.entity.BookQuoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookQuoteDao {
    @Query("SELECT * FROM book_quotes WHERE mediaId = :mediaId ORDER BY isFavorite DESC, createdAtMs DESC, id DESC")
    fun getQuotesForMedia(mediaId: Long): Flow<List<BookQuoteEntity>>

    @Query("SELECT * FROM book_quotes WHERE mediaId = :mediaId ORDER BY isFavorite DESC, createdAtMs DESC, id DESC")
    suspend fun getQuotesForMediaOnce(mediaId: Long): List<BookQuoteEntity>

    @Query("SELECT * FROM book_quotes")
    suspend fun getAllQuotesOnce(): List<BookQuoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: BookQuoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuotes(quotes: List<BookQuoteEntity>)

    @Update
    suspend fun updateQuote(quote: BookQuoteEntity)

    @Query("DELETE FROM book_quotes WHERE id = :id")
    suspend fun deleteQuoteById(id: Long)

    @Query("DELETE FROM book_quotes WHERE mediaId = :mediaId")
    suspend fun deleteQuotesForMedia(mediaId: Long)

    @Query("DELETE FROM book_quotes")
    suspend fun deleteAllQuotes()

    @Query("SELECT COUNT(*) FROM book_quotes WHERE mediaId NOT IN (SELECT id FROM media_items)")
    suspend fun countOrphaned(): Int

    @Query("DELETE FROM book_quotes WHERE mediaId NOT IN (SELECT id FROM media_items)")
    suspend fun deleteOrphaned(): Int
}
