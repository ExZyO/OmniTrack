package com.omnitrack.app.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Immutable
@Entity(
    tableName = "vn_routes",
    indices = [Index("mediaId")]
)
data class VnRouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: Long,
    val routeName: String,
    val routeType: String = "HEROINE", // COMMON, HEROINE, TRUE, NORMAL, BAD, EXTRA, CUSTOM
    val characterId: String? = null,
    val characterImageUrl: String? = null,
    val isCompleted: Boolean = false,
    val completedAtMs: Long? = null,
    val userRating: Float? = null,
    val userNote: String? = null,
    val isBookmarked: Boolean = false,
    val isSpoiler: Boolean = false,
    val playtimeHours: Double? = null,
    val sortOrder: Int = 0
)
