package com.omnitrack.app.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Offline-first persistent dirty queue for tracker synchronization.
 * Every progress increment or status change logs here immediately, allowing
 * instant local UI response while background workers flush upstream with pacing.
 */
@Serializable
@Immutable
@Entity(
    tableName = "sync_queue",
    indices = [
        Index("mediaId"),
        Index("timestamp")
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: Long,
    val trackerType: String, // "ANILIST", "MAL", "TRAKT", "SIMKL", "COMICK", "VNDB", "STEAM"
    val action: String, // "PROGRESS", "STATUS", "SCORE"
    val progressValue: Double = 0.0,
    val statusValue: String? = null,
    val scoreValue: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
