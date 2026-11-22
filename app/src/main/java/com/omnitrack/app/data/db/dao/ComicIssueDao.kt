package com.omnitrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.omnitrack.app.data.db.entity.ComicIssueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicIssueDao {
    @Query("SELECT * FROM comic_issues WHERE mediaId = :mediaId ORDER BY sortOrder ASC, id ASC")
    fun getIssuesForMedia(mediaId: Long): Flow<List<ComicIssueEntity>>

    @Query("SELECT * FROM comic_issues WHERE mediaId = :mediaId ORDER BY sortOrder ASC, id ASC")
    suspend fun getIssuesForMediaOnce(mediaId: Long): List<ComicIssueEntity>

    @Query("SELECT * FROM comic_issues")
    suspend fun getAllIssuesOnce(): List<ComicIssueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssue(issue: ComicIssueEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssues(issues: List<ComicIssueEntity>)

    @Update
    suspend fun updateIssue(issue: ComicIssueEntity)

    @Query("DELETE FROM comic_issues WHERE id = :issueId")
    suspend fun deleteIssueById(issueId: Long)

    @Query("DELETE FROM comic_issues")
    suspend fun deleteAllIssues()
}
