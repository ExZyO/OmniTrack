package com.omnitrack.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omnitrack.app.data.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "omnitrack_settings")

enum class DetailsPagePresentation(val displayName: String, val description: String) {
    NUVIO_HERO("Nuvio Hero", "Unblocked Banner, Centered Floating Tiles"),
    CLASSIC("Classic Poster", "Overlaid Poster, Floating Information")
}

enum class BannerSizing(val displayName: String, val description: String) {
    CINEMA_SHOWCASE("Cinema Showcase Card", "Widescreen 16:9 + Ambient Aura"),
    FULL_BLEED("Full-Bleed Hero", "Edge-to-edge tall showcase")
}

enum class DetailsBackdropStyle(val displayName: String, val description: String) {
    CINEMATIC_FADE("Cinematic Fade", "17-stop quintic smoothstep curve"),
    SOLID_AMOLED("Solid AMOLED", "Direct transition into pure black")
}

enum class FallbackBackgroundStyle(val displayName: String, val description: String) {
    CINEMATIC_BLUR("Cinematic Blur", "Gentle ambient micro-blurred glow"),
    DOMINANT_COLOR("Ambient Palette Glow", "Dynamic dominant color extraction"),
    SOLID_BLACK("Solid AMOLED", "Pure OLED black without aura")
}

data class PresentationSettings(
    val detailsPagePresentation: DetailsPagePresentation = DetailsPagePresentation.NUVIO_HERO,
    val bannerSizing: BannerSizing = BannerSizing.CINEMA_SHOWCASE,
    val detailsBackdropStyle: DetailsBackdropStyle = DetailsBackdropStyle.CINEMATIC_FADE,
    val fallbackBackgroundStyle: FallbackBackgroundStyle = FallbackBackgroundStyle.CINEMATIC_BLUR
)

class PreferenceManager(private val context: Context) {

    private object PreferencesKeys {
        val DETAILS_PAGE_PRESENTATION = stringPreferencesKey("details_page_presentation")
        val BANNER_SIZING = stringPreferencesKey("banner_sizing")
        val DETAILS_BACKDROP_STYLE = stringPreferencesKey("details_backdrop_style")
        val FALLBACK_BACKGROUND_STYLE = stringPreferencesKey("fallback_background_style")

        fun priorityKey(mediaType: MediaType) = stringPreferencesKey("priority_${mediaType.name.lowercase()}")
    }

    val presentationSettings: Flow<PresentationSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            PresentationSettings(
                detailsPagePresentation = prefs[PreferencesKeys.DETAILS_PAGE_PRESENTATION]?.let {
                    runCatching { DetailsPagePresentation.valueOf(it) }.getOrNull()
                } ?: DetailsPagePresentation.NUVIO_HERO,
                bannerSizing = prefs[PreferencesKeys.BANNER_SIZING]?.let {
                    runCatching { BannerSizing.valueOf(it) }.getOrNull()
                } ?: BannerSizing.CINEMA_SHOWCASE,
                detailsBackdropStyle = prefs[PreferencesKeys.DETAILS_BACKDROP_STYLE]?.let {
                    runCatching { DetailsBackdropStyle.valueOf(it) }.getOrNull()
                } ?: DetailsBackdropStyle.CINEMATIC_FADE,
                fallbackBackgroundStyle = prefs[PreferencesKeys.FALLBACK_BACKGROUND_STYLE]?.let {
                    runCatching { FallbackBackgroundStyle.valueOf(it) }.getOrNull()
                } ?: FallbackBackgroundStyle.CINEMATIC_BLUR
            )
        }

    suspend fun updateDetailsPagePresentation(presentation: DetailsPagePresentation) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DETAILS_PAGE_PRESENTATION] = presentation.name
        }
    }

    suspend fun updateBannerSizing(sizing: BannerSizing) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.BANNER_SIZING] = sizing.name
        }
    }

    suspend fun updateDetailsBackdropStyle(style: DetailsBackdropStyle) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DETAILS_BACKDROP_STYLE] = style.name
        }
    }

    suspend fun updateFallbackBackgroundStyle(style: FallbackBackgroundStyle) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.FALLBACK_BACKGROUND_STYLE] = style.name
        }
    }

    fun getArtSourcePriority(mediaType: MediaType): Flow<List<String>> {
        val defaultSources = getDefaultSourcesFor(mediaType)
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .map { prefs ->
                val raw = prefs[PreferencesKeys.priorityKey(mediaType)]
                if (raw.isNullOrBlank()) {
                    defaultSources
                } else {
                    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                }
            }
    }

    suspend fun updateArtSourcePriority(mediaType: MediaType, sources: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.priorityKey(mediaType)] = sources.joinToString(",")
        }
    }

    companion object {
        fun getDefaultSourcesFor(mediaType: MediaType): List<String> = when (mediaType) {
            MediaType.ANIME -> listOf("AniList GraphQL", "Bangumi", "Kitsu")
            MediaType.MANGA -> listOf("MangaDex API", "AniList", "Comick")
            MediaType.NOVEL -> listOf("Bangumi", "AniList", "NovelUpdates")
            MediaType.SHOW -> listOf("TMDb", "TVMaze", "TheTVDB")
            MediaType.MOVIE -> listOf("TMDb", "RPDB")
            MediaType.GAME -> listOf("IGDB", "Steam")
            MediaType.VISUAL_NOVEL -> listOf("VNDB Kana", "Bangumi")
            MediaType.ALL -> listOf("Default")
        }
    }
}
