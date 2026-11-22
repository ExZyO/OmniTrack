package com.omnitrack.app.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Immutable
@Entity(
    tableName = "fanfic_chapters",
    indices = [
        Index("mediaId"),
        Index("category")
    ]
)
data class FanficChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: Long,
    val chapterNumber: Int,
    val title: String,
    val category: String = "MAIN",
    val sourceSite: String = "AO3",
    val chapterUrl: String? = null,
    val wordCount: Int? = null,
    val publishedDate: String? = null,
    val isRead: Boolean = false,
    val readAtMs: Long? = null,
    val userRating: Float? = null,
    val userNote: String? = null,
    val isBookmarked: Boolean = false,
    val sortOrder: Int = 0
)
