package com.omnitrack.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

data class PosterCardStyle(
    val widthDp: Int = 126,
    val cornerRadiusDp: Int = 12,
    val isLandscape: Boolean = false,
    val hideLabels: Boolean = false
)

enum class DetailsPagePresentation(val displayName: String, val description: String) {
    HERO("Hero Layout", "Full-bleed edge-to-edge banner, pure AMOLED canvas"),
    CLASSIC("Classic Poster", "Overlaid Poster, Floating Information");

    companion object {
        fun fromName(name: String?): DetailsPagePresentation = when (name) {
            "CLASSIC" -> CLASSIC
            "HERO", "UNBLOCKED_HERO" -> HERO
            else -> HERO
        }
    }
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
    val detailsPagePresentation: DetailsPagePresentation = DetailsPagePresentation.HERO,
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

        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        val FANART_API_KEY = stringPreferencesKey("fanart_api_key")
        val MDBLIST_API_KEY = stringPreferencesKey("mdblist_api_key")
        val AI_SEMANTIC_SEARCH_ENABLED = booleanPreferencesKey("ai_semantic_search_enabled")

        val POSTER_WIDTH_DP = intPreferencesKey("poster_card_width_dp")
        val POSTER_CORNER_RADIUS_DP = intPreferencesKey("poster_card_corner_radius_dp")
        val POSTER_LANDSCAPE = booleanPreferencesKey("poster_card_landscape")
        val POSTER_HIDE_LABELS = booleanPreferencesKey("poster_card_hide_labels")

        fun priorityKey(mediaType: MediaType) = stringPreferencesKey("priority_${mediaType.name.lowercase()}")
        fun disabledSourcesKey(mediaType: MediaType) = stringPreferencesKey("disabled_sources_${mediaType.name.lowercase()}")
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
                    DetailsPagePresentation.fromName(it)
                } ?: DetailsPagePresentation.HERO,
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

    val geminiApiKey: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.GEMINI_API_KEY] ?: "" }

    suspend fun updateGeminiApiKey(key: String) {
        context.dataStore.edit { it[PreferencesKeys.GEMINI_API_KEY] = key }
    }

    val openRouterApiKey: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.OPENROUTER_API_KEY] ?: "" }

    suspend fun updateOpenRouterApiKey(key: String) {
        context.dataStore.edit { it[PreferencesKeys.OPENROUTER_API_KEY] = key }
    }

    val tmdbApiKey: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.TMDB_API_KEY] ?: "" }

    suspend fun updateTmdbApiKey(key: String) {
        context.dataStore.edit { it[PreferencesKeys.TMDB_API_KEY] = key }
    }

    val fanartApiKey: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.FANART_API_KEY] ?: "" }

    suspend fun updateFanartApiKey(key: String) {
        context.dataStore.edit { it[PreferencesKeys.FANART_API_KEY] = key }
    }

    val mdblistApiKey: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.MDBLIST_API_KEY] ?: "" }

    suspend fun updateMdblistApiKey(key: String) {
        context.dataStore.edit { it[PreferencesKeys.MDBLIST_API_KEY] = key }
    }

    val aiSemanticSearchEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.AI_SEMANTIC_SEARCH_ENABLED] ?: false }

    suspend fun updateAiSemanticSearchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AI_SEMANTIC_SEARCH_ENABLED] = enabled }
    }

    val posterCardStyle: Flow<PosterCardStyle> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            PosterCardStyle(
                widthDp = prefs[PreferencesKeys.POSTER_WIDTH_DP] ?: 126,
                cornerRadiusDp = prefs[PreferencesKeys.POSTER_CORNER_RADIUS_DP] ?: 12,
                isLandscape = prefs[PreferencesKeys.POSTER_LANDSCAPE] ?: false,
                hideLabels = prefs[PreferencesKeys.POSTER_HIDE_LABELS] ?: false
            )
        }

    suspend fun updatePosterWidthDp(width: Int) {
        context.dataStore.edit { it[PreferencesKeys.POSTER_WIDTH_DP] = width }
    }

    suspend fun updatePosterCornerRadiusDp(radius: Int) {
        context.dataStore.edit { it[PreferencesKeys.POSTER_CORNER_RADIUS_DP] = radius }
    }

    suspend fun updatePosterLandscape(landscape: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.POSTER_LANDSCAPE] = landscape }
    }

    suspend fun updatePosterHideLabels(hide: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.POSTER_HIDE_LABELS] = hide }
    }

    suspend fun resetPosterStyle() {
        context.dataStore.edit {
            it.remove(PreferencesKeys.POSTER_WIDTH_DP)
            it.remove(PreferencesKeys.POSTER_CORNER_RADIUS_DP)
            it.remove(PreferencesKeys.POSTER_LANDSCAPE)
            it.remove(PreferencesKeys.POSTER_HIDE_LABELS)
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

    fun getDisabledArtSources(mediaType: MediaType): Flow<Set<String>> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .map { prefs ->
                val raw = prefs[PreferencesKeys.disabledSourcesKey(mediaType)]
                if (raw.isNullOrBlank()) {
                    emptySet()
                } else {
                    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                }
            }
    }

    suspend fun updateDisabledArtSources(mediaType: MediaType, disabled: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.disabledSourcesKey(mediaType)] = disabled.joinToString(",")
        }
    }

    companion object {
        fun getDefaultSourcesFor(mediaType: MediaType): List<String> = when (mediaType) {
            MediaType.ANIME -> listOf("AniList GraphQL", "Bangumi", "Kitsu")
            MediaType.MANGA -> listOf("MangaDex API", "AniList", "Comick")
            MediaType.NOVEL -> listOf("Bangumi", "AniList", "NovelUpdates")
            MediaType.BOOK -> listOf("Google Books", "Open Library", "Hardcover")
            MediaType.SHOW -> listOf("TMDb", "TVMaze", "TheTVDB")
            MediaType.MOVIE -> listOf("TMDb", "RPDB")
            MediaType.GAME -> listOf("IGDB", "Steam")
            MediaType.VISUAL_NOVEL -> listOf("VNDB Kana", "Bangumi")
            MediaType.ALL -> listOf("Default")
        }
    }
}
