package com.omnitrack.app.data.preferences

import kotlinx.serialization.Serializable

@Serializable
enum class DetailSectionKey(val displayName: String, val description: String) {
    AIRING_STATUS("Airing Schedule", "Live episode countdown and local broadcast time"),
    EPISODES("Seasons & Episodes", "Season selector and episode cards"),
    FRANCHISE("Franchise & Adaptations", "Related movies, spin-offs, manga, and light novels"),
    CAST("Characters & Cast", "Character portraits and voice actors (Seiyuu)"),
    NETWORKS("Studios & Networks", "Animation studios and broadcasting networks"),
    DETAILS_INFO("Details & Overview", "Status, release date, runtime, and language metadata"),
    TRAILERS("Trailers & Clips", "Official YouTube trailers and video teasers"),
    REVIEWS("Reviews & Discussions", "Community ratings and user reviews"),
    COLLECTION("Part of Collection", "Franchise collection boxset"),
    RECOMMENDATIONS("More Like This", "Similar recommendations and suggestions")
}

@Serializable
data class DetailSectionItem(
    val key: DetailSectionKey,
    val isEnabled: Boolean = true
)

object DetailsPageLayoutDefaults {
    val defaultSections: List<DetailSectionItem> = listOf(
        DetailSectionItem(DetailSectionKey.AIRING_STATUS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.EPISODES, isEnabled = true),
        DetailSectionItem(DetailSectionKey.FRANCHISE, isEnabled = true),
        DetailSectionItem(DetailSectionKey.CAST, isEnabled = true),
        DetailSectionItem(DetailSectionKey.NETWORKS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.DETAILS_INFO, isEnabled = true),
        DetailSectionItem(DetailSectionKey.TRAILERS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.REVIEWS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.COLLECTION, isEnabled = true),
        DetailSectionItem(DetailSectionKey.RECOMMENDATIONS, isEnabled = true)
    )
}
