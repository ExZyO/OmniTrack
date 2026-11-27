package com.omnitrack.app.data.preferences

import com.omnitrack.app.data.model.MediaType
import kotlinx.serialization.Serializable

@Serializable
enum class EditionsSwitcherStyle(val displayName: String) {
    SMART_LABELS("Smart Labels"),
    THUMBNAIL_CARDS("Cover Cards"),
    GROUPED_DROPDOWN("Grouped");

    companion object {
        fun fromName(name: String?): EditionsSwitcherStyle =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SMART_LABELS
    }
}

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

    private val animeApplicable = listOf(
        DetailSectionKey.CROSS_MEDIUM_EDITIONS,
        DetailSectionKey.PERSONAL_NOTES,
        DetailSectionKey.AIRING_STATUS,
        DetailSectionKey.WHERE_TO_WATCH,
        DetailSectionKey.EPISODES,
        DetailSectionKey.FRANCHISE,
        DetailSectionKey.CAST,
        DetailSectionKey.NETWORKS,
        DetailSectionKey.GENRES_TAGS,
        DetailSectionKey.NAMES_SYNONYMS,
        DetailSectionKey.DETAILS_INFO,
        DetailSectionKey.AWARDS,
        DetailSectionKey.EXTERNAL_LINKS,
        DetailSectionKey.TRAILERS,
        DetailSectionKey.REVIEWS,
        DetailSectionKey.RECOMMENDATIONS
    )

    private val showApplicable = listOf(
        DetailSectionKey.CROSS_MEDIUM_EDITIONS,
        DetailSectionKey.PERSONAL_NOTES,
        DetailSectionKey.AIRING_STATUS,
        DetailSectionKey.WHERE_TO_WATCH,
        DetailSectionKey.EPISODES,
        DetailSectionKey.CAST,
        DetailSectionKey.NETWORKS,
        DetailSectionKey.GENRES_TAGS,
        DetailSectionKey.NAMES_SYNONYMS,
        DetailSectionKey.DETAILS_INFO,
        DetailSectionKey.AWARDS,
        DetailSectionKey.EXTERNAL_LINKS,
        DetailSectionKey.TRAILERS,
        DetailSectionKey.REVIEWS,
        DetailSectionKey.RECOMMENDATIONS
    )

    private val movieApplicable = listOf(
        DetailSectionKey.CROSS_MEDIUM_EDITIONS,
        DetailSectionKey.PERSONAL_NOTES,
        DetailSectionKey.WHERE_TO_WATCH,
        DetailSectionKey.CAST,
        DetailSectionKey.NETWORKS,
        DetailSectionKey.GENRES_TAGS,
        DetailSectionKey.NAMES_SYNONYMS,
        DetailSectionKey.DETAILS_INFO,
        DetailSectionKey.AWARDS,
        DetailSectionKey.EXTERNAL_LINKS,
        DetailSectionKey.TRAILERS,
        DetailSectionKey.REVIEWS,
        DetailSectionKey.COLLECTION,
        DetailSectionKey.RECOMMENDATIONS
    )

    private val mangaAndNovelApplicable = listOf(
        DetailSectionKey.CROSS_MEDIUM_EDITIONS,
        DetailSectionKey.PERSONAL_NOTES,
        DetailSectionKey.EPISODES,
        DetailSectionKey.FRANCHISE,
        DetailSectionKey.CAST,
        DetailSectionKey.GENRES_TAGS,
        DetailSectionKey.NAMES_SYNONYMS,
        DetailSectionKey.DETAILS_INFO,
        DetailSectionKey.EXTERNAL_LINKS,
        DetailSectionKey.REVIEWS,
        DetailSectionKey.RECOMMENDATIONS
    )

    private val bookApplicable = listOf(
        DetailSectionKey.CROSS_MEDIUM_EDITIONS,
        DetailSectionKey.PERSONAL_NOTES,
        DetailSectionKey.EPISODES,
        DetailSectionKey.QUOTES,
        DetailSectionKey.SCREENSHOTS,
        DetailSectionKey.EDITIONS,
        DetailSectionKey.CAST,
        DetailSectionKey.GENRES_TAGS,
        DetailSectionKey.NAMES_SYNONYMS,
        DetailSectionKey.DETAILS_INFO,
        DetailSectionKey.AWARDS,
        DetailSectionKey.EXTERNAL_LINKS,
        DetailSectionKey.REVIEWS,
        DetailSectionKey.RECOMMENDATIONS
    )

    private val comicApplicable = listOf(
        DetailSectionKey.CROSS_MEDIUM_EDITIONS,
        DetailSectionKey.PERSONAL_NOTES,
        DetailSectionKey.EPISODES,
        DetailSectionKey.CAST,
        DetailSectionKey.NETWORKS,
        DetailSectionKey.GENRES_TAGS,
        DetailSectionKey.NAMES_SYNONYMS,
        DetailSectionKey.DETAILS_INFO,
        DetailSectionKey.EXTERNAL_LINKS,
        DetailSectionKey.REVIEWS,
        DetailSectionKey.RECOMMENDATIONS
    )

    private val gameApplicable = listOf(
        DetailSectionKey.CROSS_MEDIUM_EDITIONS,
        DetailSectionKey.PERSONAL_NOTES,
        DetailSectionKey.EPISODES,
        DetailSectionKey.ACHIEVEMENTS,
        DetailSectionKey.SCREENSHOTS,
        DetailSectionKey.NETWORKS,
        DetailSectionKey.GENRES_TAGS,
        DetailSectionKey.NAMES_SYNONYMS,
        DetailSectionKey.DETAILS_INFO,
        DetailSectionKey.AWARDS,
        DetailSectionKey.EXTERNAL_LINKS,
        DetailSectionKey.TRAILERS,
        DetailSectionKey.REVIEWS,
        DetailSectionKey.RECOMMENDATIONS
    )

    private val vnApplicable = listOf(
        DetailSectionKey.CROSS_MEDIUM_EDITIONS,
        DetailSectionKey.PERSONAL_NOTES,
        DetailSectionKey.EPISODES,
        DetailSectionKey.SCREENSHOTS,
        DetailSectionKey.RELEASES,
        DetailSectionKey.FRANCHISE,
        DetailSectionKey.CAST,
        DetailSectionKey.GENRES_TAGS,
        DetailSectionKey.NAMES_SYNONYMS,
        DetailSectionKey.DETAILS_INFO,
        DetailSectionKey.EXTERNAL_LINKS,
        DetailSectionKey.REVIEWS,
        DetailSectionKey.RECOMMENDATIONS
    )

    private val fanficApplicable = listOf(
        DetailSectionKey.CROSS_MEDIUM_EDITIONS,
        DetailSectionKey.PERSONAL_NOTES,
        DetailSectionKey.EPISODES,
        DetailSectionKey.NAMES_SYNONYMS,
        DetailSectionKey.DETAILS_INFO,
        DetailSectionKey.REVIEWS
    )

    fun applicableSectionsFor(mediaType: MediaType): List<DetailSectionKey> = when (mediaType) {
        MediaType.ANIME -> animeApplicable
        MediaType.SHOW -> showApplicable
        MediaType.MOVIE -> movieApplicable
        MediaType.MANGA, MediaType.NOVEL -> mangaAndNovelApplicable
        MediaType.BOOK -> bookApplicable
        MediaType.COMIC -> comicApplicable
        MediaType.GAME -> gameApplicable
        MediaType.VISUAL_NOVEL -> vnApplicable
        MediaType.FANFICTION -> fanficApplicable
        MediaType.ALL -> defaultSections.map { it.key }
    }

    fun defaultSectionsFor(mediaType: MediaType): List<DetailSectionItem> =
        applicableSectionsFor(mediaType).map { DetailSectionItem(it, isEnabled = true) }

    fun displayNameFor(key: DetailSectionKey, mediaType: MediaType): String = when (key) {
        DetailSectionKey.EPISODES -> when (mediaType) {
            MediaType.MANGA, MediaType.NOVEL -> "Chapters & Volumes"
            MediaType.BOOK -> "Pages & Reading"
            MediaType.COMIC -> "Issues & Arcs"
            MediaType.GAME -> "Playtime & DLCs"
            MediaType.VISUAL_NOVEL -> "Routes & Playtime"
            MediaType.FANFICTION -> "Chapters & Tags"
            else -> key.displayName
        }
        DetailSectionKey.CAST -> when (mediaType) {
            MediaType.BOOK -> "Author & Works"
            MediaType.COMIC -> "Creators & Characters"
            MediaType.ANIME, MediaType.MANGA, MediaType.NOVEL, MediaType.VISUAL_NOVEL -> "Characters & Cast"
            else -> "Cast & Crew"
        }
        DetailSectionKey.SCREENSHOTS -> when (mediaType) {
            MediaType.BOOK -> "Illustrations & Art"
            MediaType.GAME -> "Media & Screenshots"
            else -> key.displayName
        }
        DetailSectionKey.NETWORKS -> when (mediaType) {
            MediaType.GAME -> "Developers & Publishers"
            MediaType.COMIC -> "Publisher & Imprint"
            else -> key.displayName
        }
        else -> key.displayName
    }

    fun normalizeForMedia(
        mediaType: MediaType,
        saved: List<DetailSectionItem>?
    ): List<DetailSectionItem> {
        val allowed = applicableSectionsFor(mediaType)
        val allowedSet = allowed.toSet()
        if (saved.isNullOrEmpty()) return defaultSectionsFor(mediaType)
        val filtered = saved.filter { it.key in allowedSet }.distinctBy { it.key }
        val existingKeys = filtered.map { it.key }.toSet()
        val missingTop = allowed.filter {
            (it == DetailSectionKey.CROSS_MEDIUM_EDITIONS || it == DetailSectionKey.PERSONAL_NOTES) &&
                it !in existingKeys
        }.map { DetailSectionItem(it, isEnabled = true) }
        val missingTail = allowed.filter {
            it != DetailSectionKey.CROSS_MEDIUM_EDITIONS &&
                it != DetailSectionKey.PERSONAL_NOTES &&
                it !in existingKeys
        }.map { DetailSectionItem(it, isEnabled = true) }
        return missingTop + filtered + missingTail
    }
}
