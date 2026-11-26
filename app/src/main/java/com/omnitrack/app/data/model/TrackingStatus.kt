package com.omnitrack.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class TrackingStatus(val displayName: String) {
    ALL("All"),
    IN_PROGRESS("In Progress"),
    PLAN_TO_TRACK("Plan to Track"),
    COMPLETED("Completed"),
    ON_HOLD("On Hold"),
    DROPPED("Dropped");

    fun labelFor(mediaType: MediaType?): String = when (this) {
        ALL -> "All"
        COMPLETED -> "Completed"
        ON_HOLD -> "On Hold"
        DROPPED -> "Dropped"
        IN_PROGRESS -> when (mediaType) {
            MediaType.ANIME, MediaType.SHOW, MediaType.MOVIE -> "Watching"
            MediaType.MANGA, MediaType.COMIC, MediaType.NOVEL, MediaType.BOOK, MediaType.FANFICTION -> "Reading"
            MediaType.GAME, MediaType.VISUAL_NOVEL -> "Playing"
            MediaType.ALL, null -> "In Progress"
        }
        PLAN_TO_TRACK -> when (mediaType) {
            MediaType.ANIME, MediaType.SHOW, MediaType.MOVIE -> "Plan to Watch"
            MediaType.MANGA, MediaType.COMIC, MediaType.NOVEL, MediaType.BOOK, MediaType.FANFICTION -> "Plan to Read"
            MediaType.GAME, MediaType.VISUAL_NOVEL -> "Plan to Play"
            MediaType.ALL, null -> "Plan to Track"
        }
    }

    companion object {
        val filterChips = listOf(
            ALL,
            IN_PROGRESS,
            PLAN_TO_TRACK,
            COMPLETED,
            ON_HOLD,
            DROPPED
        )
    }
}
