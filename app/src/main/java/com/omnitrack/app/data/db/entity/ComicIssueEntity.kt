package com.omnitrack.app.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Immutable
@Entity(
    tableName = "comic_issues",
    indices = [Index("mediaId")]
)
data class ComicIssueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: Long,
    val issueNumber: String,
    val issueTitle: String? = null,
    val entryType: String = "ISSUE",
    val storyArc: String? = null,
    val coverUrl: String? = null,
    val storeDate: String? = null,
    val coverDate: String? = null,
    val isRead: Boolean = false,
    val readAtMs: Long? = null,
    val userRating: Float? = null,
    val userNote: String? = null,
    val isBookmarked: Boolean = false,
    val sortOrder: Int = 0
)
