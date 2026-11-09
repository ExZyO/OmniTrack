package com.omnitrack.app.data.db.dto

import androidx.compose.runtime.Immutable
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus

/**
 * Lightweight projection for high-performance 120Hz library grids.
 * Omits heavy synopsis text, category strings, and external tracker IDs to eliminate heap bloat.
 */
@Immutable
data class MediaCardSummary(
    val id: Long,
    val title: String,
    val nativeTitle: String? = null,
    val coverUrl: String,
    val bannerUrl: String? = null,
    val clearartUrl: String? = null,
    val mediaType: MediaType,
    val status: TrackingStatus = TrackingStatus.IN_PROGRESS,
    val currentProgress: Double = 0.0,
    val totalProgress: Double? = null,
    val userRating: Float? = null,
    val customCoverPath: String? = null,
    val customBannerPath: String? = null,
    val artworkRole: String = "DEFAULT",
    val communityScore: Float? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val imdbId: String? = null,
    val tmdbId: Long? = null,
    val anilistId: Long? = null,
    val malId: Long? = null
)
