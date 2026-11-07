package com.omnitrack.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaType(
    val displayName: String,
    val unitLabel: String,
    val isProgressNumeric: Boolean = true
) {
    ALL("All", ""),
    ANIME("Anime", "Ep"),
    MANGA("Manga", "Ch"),
    NOVEL("Novels", "Ch"),
    SHOW("Shows", "Ep"),
    MOVIE("Movies", "Watched", isProgressNumeric = false),
    VISUAL_NOVEL("Visual Novels", "Hrs"),
    GAME("Games", "Hrs");

    companion object {
        val filterTabs = listOf(
            ALL,
            ANIME,
            MANGA,
            NOVEL,
            SHOW,
            MOVIE,
            VISUAL_NOVEL,
            GAME
        )
    }
}
