package com.omnitrack.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
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
    val hideLabels: Boolean = false,
    val alwaysShowLandscapeClearlogo: Boolean = true,
    val cardDepthEnabled: Boolean = true,
    val cardDepthEdgeStrength: Int = 50,
    val cardDepthSheenStrength: Int = 35,
    val cardDepthEdgeCoverage: Int = 100,
    val cardDepthPostersEnabled: Boolean = true,
    val cardDepthContinueWatchingEnabled: Boolean = true,
    val cardDepthEpisodesEnabled: Boolean = true,
    val cardDepthCastEnabled: Boolean = true,
    val cardDepthTrailersEnabled: Boolean = true
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

enum class EpisodeLayoutMode(val displayName: String, val description: String) {
    HORIZONTAL("Horizontal Carousel", "16:9 widescreen scrolling cards with in-image metadata"),
    LIST("Vertical Compact List", "Classic Nuvio side-by-side list with thumbnail on left"),
    VERTICAL_CARD("Vertical Hero Cards", "Full-width 16:9 vertical cards with in-image metadata");

    companion object {
        fun fromName(name: String?): EpisodeLayoutMode = when (name?.uppercase()) {
            "HORIZONTAL" -> HORIZONTAL
            "VERTICAL_CARD" -> VERTICAL_CARD
            else -> LIST
        }
    }
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

        val CUSTOM_TRAKT_CLIENT_ID = stringPreferencesKey("custom_trakt_client_id")
        val CUSTOM_TRAKT_CLIENT_SECRET = stringPreferencesKey("custom_trakt_client_secret")
        val CUSTOM_MAL_CLIENT_ID = stringPreferencesKey("custom_mal_client_id")
        val CUSTOM_SIMKL_CLIENT_ID = stringPreferencesKey("custom_simkl_client_id")
        val CUSTOM_SIMKL_CLIENT_SECRET = stringPreferencesKey("custom_simkl_client_secret")

        val POSTER_WIDTH_DP = intPreferencesKey("poster_card_width_dp")
        val POSTER_CORNER_RADIUS_DP = intPreferencesKey("poster_card_corner_radius_dp")
        val POSTER_LANDSCAPE = booleanPreferencesKey("poster_card_landscape")
        val POSTER_HIDE_LABELS = booleanPreferencesKey("poster_card_hide_labels")
        val POSTER_ALWAYS_LANDSCAPE_CLEARLOGO = booleanPreferencesKey("poster_always_landscape_clearlogo")
        val POSTER_CARD_DEPTH_ENABLED = booleanPreferencesKey("poster_card_depth_enabled")
        val POSTER_CARD_DEPTH_EDGE_STRENGTH = intPreferencesKey("poster_card_depth_edge_strength")
        val POSTER_CARD_DEPTH_SHEEN_STRENGTH = intPreferencesKey("poster_card_depth_sheen_strength")
        val POSTER_CARD_DEPTH_EDGE_COVERAGE = intPreferencesKey("poster_card_depth_edge_coverage")
        val POSTER_CARD_DEPTH_POSTERS = booleanPreferencesKey("poster_card_depth_posters")
        val POSTER_CARD_DEPTH_CONTINUE_WATCHING = booleanPreferencesKey("poster_card_depth_continue_watching")
        val POSTER_CARD_DEPTH_EPISODES = booleanPreferencesKey("poster_card_depth_episodes")
        val POSTER_CARD_DEPTH_CAST = booleanPreferencesKey("poster_card_depth_cast")
        val POSTER_CARD_DEPTH_TRAILERS = booleanPreferencesKey("poster_card_depth_trailers")

        val EPISODE_LAYOUT_MODE = stringPreferencesKey("episode_layout_mode")
        val EPISODE_LAYOUT_SCALE = floatPreferencesKey("episode_layout_scale")
        val SEASON_VIEW_MODE = stringPreferencesKey("season_view_mode")
        val CUSTOM_POSTER_URL_PATTERN = stringPreferencesKey("custom_poster_url_pattern")

        fun priorityKey(mediaType: MediaType) = stringPreferencesKey("priority_${mediaType.name.lowercase()}")
        fun disabledSourcesKey(mediaType: MediaType) = stringPreferencesKey("disabled_sources_${mediaType.name.lowercase()}")
    }

    val presentationSettings: Flow<PresentationSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException || exception is ClassCastException) {
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

    val customTraktClientId: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.CUSTOM_TRAKT_CLIENT_ID] ?: "" }

    val customTraktClientSecret: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.CUSTOM_TRAKT_CLIENT_SECRET] ?: "" }

    val customMalClientId: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { 
            val id = it[PreferencesKeys.CUSTOM_MAL_CLIENT_ID] ?: ""
            if (id == "2db7880d919b9231be3031ec80e77f15") "" else id
        }

    val customSimklClientId: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.CUSTOM_SIMKL_CLIENT_ID] ?: "" }

    val customSimklClientSecret: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.CUSTOM_SIMKL_CLIENT_SECRET] ?: "" }

    suspend fun updateCustomTrackerCredentials(
        traktClientId: String,
        traktClientSecret: String,
        malClientId: String,
        simklClientId: String,
        simklClientSecret: String
    ) {
        context.dataStore.edit {
            it[PreferencesKeys.CUSTOM_TRAKT_CLIENT_ID] = traktClientId.trim()
            it[PreferencesKeys.CUSTOM_TRAKT_CLIENT_SECRET] = traktClientSecret.trim()
            it[PreferencesKeys.CUSTOM_MAL_CLIENT_ID] = malClientId.trim()
            it[PreferencesKeys.CUSTOM_SIMKL_CLIENT_ID] = simklClientId.trim()
            it[PreferencesKeys.CUSTOM_SIMKL_CLIENT_SECRET] = simklClientSecret.trim()
        }
    }

    val posterCardStyle: Flow<PosterCardStyle> = context.dataStore.data
        .catch { if (it is IOException || it is ClassCastException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            try {
                PosterCardStyle(
                    widthDp = prefs[PreferencesKeys.POSTER_WIDTH_DP] ?: 126,
                    cornerRadiusDp = prefs[PreferencesKeys.POSTER_CORNER_RADIUS_DP] ?: 12,
                    isLandscape = prefs[PreferencesKeys.POSTER_LANDSCAPE] ?: false,
                    hideLabels = prefs[PreferencesKeys.POSTER_HIDE_LABELS] ?: false,
                    alwaysShowLandscapeClearlogo = prefs[PreferencesKeys.POSTER_ALWAYS_LANDSCAPE_CLEARLOGO] ?: true,
                    cardDepthEnabled = prefs[PreferencesKeys.POSTER_CARD_DEPTH_ENABLED] ?: true,
                    cardDepthEdgeStrength = prefs[PreferencesKeys.POSTER_CARD_DEPTH_EDGE_STRENGTH] ?: 50,
                    cardDepthSheenStrength = prefs[PreferencesKeys.POSTER_CARD_DEPTH_SHEEN_STRENGTH] ?: 35,
                    cardDepthEdgeCoverage = prefs[PreferencesKeys.POSTER_CARD_DEPTH_EDGE_COVERAGE] ?: 100,
                    cardDepthPostersEnabled = prefs[PreferencesKeys.POSTER_CARD_DEPTH_POSTERS] ?: true,
                    cardDepthContinueWatchingEnabled = prefs[PreferencesKeys.POSTER_CARD_DEPTH_CONTINUE_WATCHING] ?: true,
                    cardDepthEpisodesEnabled = prefs[PreferencesKeys.POSTER_CARD_DEPTH_EPISODES] ?: true,
                    cardDepthCastEnabled = prefs[PreferencesKeys.POSTER_CARD_DEPTH_CAST] ?: true,
                    cardDepthTrailersEnabled = prefs[PreferencesKeys.POSTER_CARD_DEPTH_TRAILERS] ?: true
                )
            } catch (_: Exception) {
                PosterCardStyle()
            }
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

    suspend fun updateAlwaysShowLandscapeClearlogo(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.POSTER_ALWAYS_LANDSCAPE_CLEARLOGO] = enabled }
    }

    suspend fun updateCardDepthEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.POSTER_CARD_DEPTH_ENABLED] = enabled }
    }

    suspend fun updateCardDepthSettings(edgeStrength: Int, sheenStrength: Int) {
        context.dataStore.edit {
            it[PreferencesKeys.POSTER_CARD_DEPTH_EDGE_STRENGTH] = edgeStrength
            it[PreferencesKeys.POSTER_CARD_DEPTH_SHEEN_STRENGTH] = sheenStrength
        }
    }

    suspend fun updateCardDepthCoverage(coverage: Int) {
        context.dataStore.edit {
            it[PreferencesKeys.POSTER_CARD_DEPTH_EDGE_COVERAGE] = coverage
        }
    }

    suspend fun updateCardDepthSurface(surface: String, enabled: Boolean) {
        context.dataStore.edit {
            when (surface.lowercase()) {
                "posters" -> it[PreferencesKeys.POSTER_CARD_DEPTH_POSTERS] = enabled
                "continue_watching", "continuewatching" -> it[PreferencesKeys.POSTER_CARD_DEPTH_CONTINUE_WATCHING] = enabled
                "episodes" -> it[PreferencesKeys.POSTER_CARD_DEPTH_EPISODES] = enabled
                "cast" -> it[PreferencesKeys.POSTER_CARD_DEPTH_CAST] = enabled
                "trailers" -> it[PreferencesKeys.POSTER_CARD_DEPTH_TRAILERS] = enabled
            }
        }
    }

    suspend fun resetPosterStyle() {
        context.dataStore.edit {
            it.remove(PreferencesKeys.POSTER_WIDTH_DP)
            it.remove(PreferencesKeys.POSTER_CORNER_RADIUS_DP)
            it.remove(PreferencesKeys.POSTER_LANDSCAPE)
            it.remove(PreferencesKeys.POSTER_HIDE_LABELS)
            it.remove(PreferencesKeys.POSTER_ALWAYS_LANDSCAPE_CLEARLOGO)
            it.remove(PreferencesKeys.POSTER_CARD_DEPTH_ENABLED)
            it.remove(PreferencesKeys.POSTER_CARD_DEPTH_EDGE_STRENGTH)
            it.remove(PreferencesKeys.POSTER_CARD_DEPTH_SHEEN_STRENGTH)
            it.remove(PreferencesKeys.POSTER_CARD_DEPTH_EDGE_COVERAGE)
            it.remove(PreferencesKeys.POSTER_CARD_DEPTH_POSTERS)
            it.remove(PreferencesKeys.POSTER_CARD_DEPTH_CONTINUE_WATCHING)
            it.remove(PreferencesKeys.POSTER_CARD_DEPTH_EPISODES)
            it.remove(PreferencesKeys.POSTER_CARD_DEPTH_CAST)
            it.remove(PreferencesKeys.POSTER_CARD_DEPTH_TRAILERS)
        }
    }

    val episodeLayoutMode: Flow<EpisodeLayoutMode> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            EpisodeLayoutMode.fromName(prefs[PreferencesKeys.EPISODE_LAYOUT_MODE])
        }

    val episodeLayoutScale: Flow<Float> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            try {
                prefs[PreferencesKeys.EPISODE_LAYOUT_SCALE] ?: 1.0f
            } catch (_: Exception) {
                1.0f
            }
        }

    val seasonViewMode: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[PreferencesKeys.SEASON_VIEW_MODE] ?: "Posters"
        }

    suspend fun updateEpisodeLayoutMode(mode: EpisodeLayoutMode) {
        context.dataStore.edit { it[PreferencesKeys.EPISODE_LAYOUT_MODE] = mode.name }
    }

    suspend fun updateEpisodeLayoutScale(scale: Float) {
        context.dataStore.edit { it[PreferencesKeys.EPISODE_LAYOUT_SCALE] = scale.coerceIn(0.75f, 1.4f) }
    }

    suspend fun updateSeasonViewMode(mode: String) {
        context.dataStore.edit { it[PreferencesKeys.SEASON_VIEW_MODE] = mode }
    }

    val customPosterUrlPattern: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.CUSTOM_POSTER_URL_PATTERN] ?: "" }

    suspend fun updateCustomPosterUrlPattern(pattern: String) {
        context.dataStore.edit { it[PreferencesKeys.CUSTOM_POSTER_URL_PATTERN] = pattern.trim() }
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
