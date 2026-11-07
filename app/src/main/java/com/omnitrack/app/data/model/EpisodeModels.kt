package com.omnitrack.app.data.model

data class MediaSeason(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val posterUrl: String? = null,
    val airDate: String? = null
)

data class MediaEpisode(
    val id: Long,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val title: String,
    val overview: String? = null,
    val stillUrl: String? = null,
    val airDate: String? = null,
    val runtimeMinutes: Int? = null,
    val voteAverage: Float? = null,
    val isWatched: Boolean = false
)
