package com.omnitrack.app.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Immutable
@Entity(
    tableName = "book_entries",
    indices = [Index("mediaId")]
)
data class BookEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: Long,
    val entryName: String,
    val entryType: String = "BOOK_PART",
    val pagesOrLines: Int? = null,
    val translator: String? = null,
    val editionLabel: String? = null,
    val isCompleted: Boolean = false,
    val completedAtMs: Long? = null,
    val userRating: Float? = null,
    val userNote: String? = null,
    val isBookmarked: Boolean = false,
    val sortOrder: Int = 0
)
