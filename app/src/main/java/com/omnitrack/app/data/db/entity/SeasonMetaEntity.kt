package com.omnitrack.app.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import kotlinx.serialization.Serializable

@Serializable
@Immutable
@Entity(tableName = "season_meta", primaryKeys = ["mediaId", "seasonNumber"])
data class SeasonMetaEntity(
    val mediaId: Long,
    val seasonNumber: Int,
    val userRating: Float? = null,
    val userNote: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)
