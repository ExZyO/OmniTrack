package com.omnitrack.app.data.model

enum class TrackingStatus(val displayName: String) {
    ALL("All"),
    IN_PROGRESS("In Progress"),
    PLAN_TO_TRACK("Plan to Track"),
    COMPLETED("Completed"),
    ON_HOLD("On Hold"),
    DROPPED("Dropped");

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
