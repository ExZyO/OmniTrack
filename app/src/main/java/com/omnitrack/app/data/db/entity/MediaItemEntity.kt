package com.omnitrack.app.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import kotlinx.serialization.Serializable

@Serializable
@Immutable
@Entity(
    tableName = "media_items",
    indices = [
        Index("mediaType"),
        Index("status"),
        Index("lastUpdated"),
        Index("title")
    ]
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val nativeTitle: String? = null,
    val synopsis: String? = null,
    val coverUrl: String,
    val bannerUrl: String? = null,
    val mediaType: MediaType,
    val status: TrackingStatus = TrackingStatus.IN_PROGRESS,
    val currentProgress: Double = 0.0,
    val totalProgress: Double? = null,
    val userRating: Float? = null,
    val platform: String? = null,
    val categoryNames: String = "",
    val anilistId: Long? = null,
    val malId: Long? = null,
    val traktId: Long? = null,
    val simklId: Long? = null,
    val tmdbId: Long? = null,
    val vndbId: String? = null,
    val comickId: String? = null,
    val hardcoverId: String? = null,
    val bangumiId: Long? = null,
    val igdbId: Long? = null,
    val customCoverPath: String? = null,
    val customBannerPath: String? = null,
    val artworkRole: String = "DEFAULT",
    val clearartUrl: String? = null,
    val communityScore: Float? = null,
    val imdbScore: Float? = null,
    val rtScore: Int? = null,
    val metacriticScore: Int? = null,
    val simklMemo: String? = null,
    val simklPrivate: Boolean = true,
    val malComments: String? = null,
    val malRewatches: Int = 0,
    val anilistNotes: String? = null,
    val anilistPrivate: Boolean = false,
    val anilistFavorite: Boolean = false,
    val bangumiComment: String? = null,
    val bangumiPrivate: Boolean = false,
    val autoStatusEnabled: Boolean = true,
    val trailerYoutubeKey: String? = null,
    val studioOrNetwork: String? = null,
    val runtimeMinutes: Int? = null,
    val contentRating: String? = null,
    val airStatus: String? = null,
    val isFavorite: Boolean = false,
    val isSimklStatusLocked: Boolean = false,
    val simklStatus: TrackingStatus? = null,
    val isAnilistStatusLocked: Boolean = false,
    val anilistStatus: TrackingStatus? = null,
    val isMalStatusLocked: Boolean = false,
    val malStatus: TrackingStatus? = null,
    val isBangumiStatusLocked: Boolean = false,
    val bangumiStatus: TrackingStatus? = null,
    val isTraktStatusLocked: Boolean = false,
    val traktStatus: TrackingStatus? = null,
    val isComickStatusLocked: Boolean = false,
    val comickStatus: TrackingStatus? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
