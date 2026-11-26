package com.omnitrack.app.data.preferences

import kotlinx.serialization.Serializable

@Serializable
enum class DetailSectionKey(val displayName: String, val description: String) {
    CROSS_MEDIUM_EDITIONS("Linked Editions", "Switch between linked Anime, Manga, Light Novel & Web Novel entries"),
    PERSONAL_NOTES("Personal Notes & Log", "Unified note, favorite heart, dates & rewatch log synced to trackers"),
    AIRING_STATUS("Airing Schedule", "Live episode countdown and local broadcast time"),
    WHERE_TO_WATCH("Where to Watch", "Official streaming, rent, and buy service logos for your region"),
    EPISODES("Seasons & Episodes", "Season selector and episode cards"),
    ACHIEVEMENTS("Achievements", "Steam & manual achievements progress and rarity"),
    QUOTES("Quotes & Journal", "Favorite passages, stanzas, highlights, and reading notes"),
    SCREENSHOTS("Screenshots & CGs", "Visual novel CG gallery and banner picker"),
    RELEASES("Releases & Translations", "Official editions, fan patches, voice acting, and platforms"),
    EDITIONS("Editions & Translators", "Compare book translations, publishers, ISBNs, and formats"),
    FRANCHISE("Franchise & Adaptations", "Related movies, spin-offs, manga, and light novels"),
    CAST("Characters & Cast", "Character portraits and voice actors (Seiyuu)"),
    NETWORKS("Studios & Networks", "Animation studios and broadcasting networks"),
    GENRES_TAGS("Genres & Tags", "Clickable genres and anime tag chips"),
    NAMES_SYNONYMS("Alternative Names & Synonyms", "English, Romaji, and native titles with alternative aliases"),
    DETAILS_INFO("Details & Overview", "Status, release date, runtime, popularity, and metadata"),
    AWARDS("Awards & Honors", "Major awards, wins & nominations from Wikidata and OMDb"),
    EXTERNAL_LINKS("External & Streaming Links", "Official streaming platforms, social channels, and database links"),
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
        DetailSectionItem(DetailSectionKey.CROSS_MEDIUM_EDITIONS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.PERSONAL_NOTES, isEnabled = true),
        DetailSectionItem(DetailSectionKey.AIRING_STATUS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.WHERE_TO_WATCH, isEnabled = true),
        DetailSectionItem(DetailSectionKey.EPISODES, isEnabled = true),
        DetailSectionItem(DetailSectionKey.ACHIEVEMENTS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.QUOTES, isEnabled = true),
        DetailSectionItem(DetailSectionKey.SCREENSHOTS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.RELEASES, isEnabled = true),
        DetailSectionItem(DetailSectionKey.EDITIONS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.FRANCHISE, isEnabled = true),
        DetailSectionItem(DetailSectionKey.CAST, isEnabled = true),
        DetailSectionItem(DetailSectionKey.NETWORKS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.GENRES_TAGS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.NAMES_SYNONYMS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.DETAILS_INFO, isEnabled = true),
        DetailSectionItem(DetailSectionKey.AWARDS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.EXTERNAL_LINKS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.TRAILERS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.REVIEWS, isEnabled = true),
        DetailSectionItem(DetailSectionKey.COLLECTION, isEnabled = true),
        DetailSectionItem(DetailSectionKey.RECOMMENDATIONS, isEnabled = true)
    )
}
