package com.omnitrack.app.data.db.entity

import androidx.room.Entity

@Entity(
    tableName = "episode_progress",
    primaryKeys = ["mediaId", "seasonNumber", "episodeNumber"]
)
data class EpisodeProgressEntity(
    val mediaId: Long,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val isWatched: Boolean = false,
    val watchedAtMs: Long? = null
)
