package com.omnitrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.omnitrack.app.data.db.entity.FanficChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FanficChapterDao {
    @Query("SELECT * FROM fanfic_chapters WHERE mediaId = :mediaId ORDER BY sortOrder ASC, chapterNumber ASC, id ASC")
    fun getChaptersForMedia(mediaId: Long): Flow<List<FanficChapterEntity>>

    @Query("SELECT * FROM fanfic_chapters WHERE mediaId = :mediaId ORDER BY sortOrder ASC, chapterNumber ASC, id ASC")
    suspend fun getChaptersForMediaOnce(mediaId: Long): List<FanficChapterEntity>

    @Query("SELECT * FROM fanfic_chapters")
    suspend fun getAllChaptersOnce(): List<FanficChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: FanficChapterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<FanficChapterEntity>)

    @Update
    suspend fun updateChapter(chapter: FanficChapterEntity)

    @Query("DELETE FROM fanfic_chapters WHERE id = :chapterId")
    suspend fun deleteChapterById(chapterId: Long)

    @Query("DELETE FROM fanfic_chapters WHERE mediaId = :mediaId")
    suspend fun deleteChaptersForMedia(mediaId: Long)

    @Query("DELETE FROM fanfic_chapters")
    suspend fun deleteAllChapters()
}
