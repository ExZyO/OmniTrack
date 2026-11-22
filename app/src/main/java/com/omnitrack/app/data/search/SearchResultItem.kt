package com.omnitrack.app.data.search

import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus

/**
 * Universal search result item returned by OmniSearch across all 7 media verticals.
 */
data class SearchResultItem(
    val title: String,
    val nativeTitle: String? = null,
    val synopsis: String? = null,
    val coverUrl: String,
    val bannerUrl: String? = null,
    val mediaType: MediaType,
    val releaseYear: String? = null,
    val communityScore: Float? = null,
    val sourceProvider: String, // e.g. "AniList", "MangaDex", "Bangumi", "TMDb", "Steam", "VNDB"
    val anilistId: Long? = null,
    val malId: Long? = null,
    val bangumiId: Long? = null,
    val tmdbId: Long? = null,
    val igdbId: Long? = null,
    val steamAppId: Long? = null,
    val vndbId: String? = null,
    val mangadexId: String? = null,
    val openLibraryId: String? = null,
    val hardcoverId: String? = null,
    val isbn: String? = null,
    val bookAuthor: String? = null,
    val bookFormat: String? = null,
    val comicVineId: Long? = null,
    val metronId: Long? = null,
    val comicPublisher: String? = null,
    val comicFormat: String? = null,
    val originCountry: String? = null,
    val platform: String? = null,
    val totalProgress: Double? = null,
    val runtimeMinutes: Int? = null
) {
    fun toMediaItemEntity(status: TrackingStatus = TrackingStatus.PLAN_TO_TRACK): MediaItemEntity {
        return MediaItemEntity(
            title = title,
            nativeTitle = nativeTitle,
            synopsis = synopsis,
            coverUrl = LosslessCoverMapper.toLosslessUrl(coverUrl),
            bannerUrl = bannerUrl?.let { LosslessCoverMapper.toLosslessUrl(it) },
            mediaType = mediaType,
            status = status,
            currentProgress = 0.0,
            totalProgress = totalProgress,
            platform = platform,
            userRating = null,
            anilistId = anilistId,
            malId = malId,
            bangumiId = bangumiId,
            tmdbId = tmdbId,
            igdbId = igdbId,
            steamAppId = steamAppId,
            vndbId = vndbId,
            mangadexId = mangadexId,
            openLibraryId = openLibraryId,
            hardcoverId = hardcoverId,
            isbn = isbn,
            bookAuthor = bookAuthor,
            bookFormat = bookFormat,
            comicVineId = comicVineId,
            metronId = metronId,
            comicPublisher = comicPublisher,
            comicFormat = comicFormat,
            originCountry = originCountry,
            runtimeMinutes = runtimeMinutes,
            releaseYear = releaseYear,
            communityScore = communityScore
        )
    }
}
