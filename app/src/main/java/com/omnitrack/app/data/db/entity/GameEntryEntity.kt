package com.omnitrack.app.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Immutable
@Entity(
    tableName = "game_entries",
    indices = [Index("mediaId")]
)
data class GameEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: Long,
    val entryName: String,
    val entryType: String = "PLAYTHROUGH",
    val igdbExpansionId: Long? = null,
    val coverUrl: String? = null,
    val platform: String? = null,
    val completionTier: String = "NONE",
    val difficulty: String? = null,
    val hoursSpent: Double? = null,
    val startedAtMs: Long? = null,
    val completedAtMs: Long? = null,
    val isCompleted: Boolean = false,
    val userRating: Float? = null,
    val userNote: String? = null,
    val isBookmarked: Boolean = false,
    val sortOrder: Int = 0
)
