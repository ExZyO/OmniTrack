package com.omnitrack.app.data.artwork

import kotlinx.serialization.Serializable

@Serializable
data class ArtworkCandidate(
    val url: String,
    val sourceName: String,
    val description: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val isBanner: Boolean = false
)
