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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.VnNsfwFilterMode
import com.omnitrack.app.data.model.VnSpoilerLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "omnitrack_settings")

data class GeminiModelOption(
    val id: String,
    val name: String,
    val description: String
)

val DEFAULT_GEMINI_MODELS = listOf(
    GeminiModelOption("gemini-3.8-flash", "Gemini 3.8 Flash", "Official Flagship · Fast & High Quota"),
    GeminiModelOption("gemini-3.7-flash", "Gemini 3.7 Flash", "Flagship Hybrid & Fast"),
    GeminiModelOption("gemini-3.5-flash", "Gemini 3.5 Flash", "Ultra Fast"),
    GeminiModelOption("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Highest Free Quota"),
    GeminiModelOption("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite", "High Quota & Fast"),
    GeminiModelOption("gemini-3.1-pro-preview", "Gemini 3.1 Pro", "Deep Reasoning"),
    GeminiModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", "Recommended · Fast & Intelligent"),
    GeminiModelOption("gemini-2.5-pro", "Gemini 2.5 Pro", "Deep Reasoning & Analysis"),
    GeminiModelOption("gemini-2.0-flash", "Gemini 2.0 Flash", "Fast Hybrid"),
    GeminiModelOption("gemini-2.0-flash-lite", "Gemini 2.0 Flash-Lite", "High Quota")
)

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
    VERTICAL_CARD("Vertical Hero Cards", "Full-width 16:9 vertical cards with in-image metadata"),
    POSTER_GRID("Poster Grid", "3-column cover art poster grid for Light Novels & Volumes");

    companion object {
        fun fromName(name: String?): EpisodeLayoutMode = when (name?.uppercase()) {
            "HORIZONTAL" -> HORIZONTAL
            "VERTICAL_CARD" -> VERTICAL_CARD
            "POSTER_GRID" -> POSTER_GRID
            else -> LIST
        }
    }
}

@Serializable
enum class HomeRowKey(val displayName: String, val description: String) {
    CONTINUE("Continue Tracking", "Active items you are watching, reading, or playing"),
    UP_NEXT("Up Next & Release Radar", "Live airing episodes & upcoming calendar releases"),
    STATS("Compact Stats Strip", "Active count, streak & tap for 90-day insights"),
    RECENT("Recently Updated", "Latest additions and progress updates"),
    PLAN("Plan to Track", "Your queued backlog across all media"),
    FAVORITES("Favorites & Top Rated", "Your highest-rated and favorite titles"),
    TRENDING("Trending Now", "Live trending anime, shows, movies & books"),
    DISCOVER_ANIME("Trending Anime", "Live trending anime from AniList"),
    DISCOVER_MANGA("Trending Manga", "Live trending manga & manhwa"),
    DISCOVER_COMICS("Trending Comics", "Acclaimed comic runs & weekly pull list"),
    DISCOVER_NOVELS("Top Web & Light Novels", "Popular web novels & light novels"),
    DISCOVER_BOOKS("Trending Books", "Trending fiction, classics & sci-fi"),
    DISCOVER_SHOWS("Trending TV Shows", "Popular & top-rated TV series"),
    DISCOVER_MOVIES("Trending Movies", "Popular & top-rated films"),
    DISCOVER_GAMES("Trending Games", "Trending & acclaimed games"),
    DISCOVER_VNS("Top Visual Novels", "Popular & top-rated visual novels")
}

@Serializable
data class HomeRowItem(
    val key: HomeRowKey,
    val isEnabled: Boolean = true
)

object HomeLayoutDefaults {
    val defaultRows: List<HomeRowItem> = HomeRowKey.entries.map { key ->
        HomeRowItem(key = key, isEnabled = !key.name.startsWith("DISCOVER_"))
    }
}

enum class HomeShelfPosterSize(val displayName: String, val widthDp: Int, val heightDp: Int) {
    COMPACT("Compact", 96, 144),
    STANDARD("Standard", 114, 170),
    LARGE("Large", 136, 204);

    companion object {
        fun fromName(name: String?): HomeShelfPosterSize = when (name?.uppercase()) {
            "COMPACT" -> COMPACT
            "LARGE" -> LARGE
            else -> STANDARD
        }
    }
}

enum class HomeHeroSource(val displayName: String) {
    CONTINUE("Continue Tracking"),
    FAVORITES("Favorites"),
    PLAN_TO_TRACK("Plan to Track"),
    TRENDING("Trending");

    companion object {
        fun fromName(name: String?): HomeHeroSource = when (name?.uppercase()) {
            "FAVORITES" -> FAVORITES
            "PLAN_TO_TRACK" -> PLAN_TO_TRACK
            "TRENDING" -> TRENDING
            else -> CONTINUE
        }
    }
}

enum class ContinueTrackingStyle(val displayName: String, val description: String) {
    CARD("16:9 Card", "Full-bleed widescreen artwork with overlay & bottom bar"),
    WIDE("Wide Split", "Left artwork strip with right-side details & progress"),
    POSTER("Vertical Poster", "2:3 poster with floating progress pill & title below");

    companion object {
        fun fromName(name: String?): ContinueTrackingStyle = when (name?.uppercase()) {
            "WIDE" -> WIDE
            "POSTER" -> POSTER
            else -> CARD
        }
    }
}

data class HomeHeroSettings(
    val showHero: Boolean = true,
    val heroSource: HomeHeroSource = HomeHeroSource.CONTINUE,
    val autoRotate: Boolean = true,
    val dimBanner: Boolean = true,
    val allowCroppedFallback: Boolean = false,
    val strictMode: Boolean = false,
    val strictRequirements: Set<String> = setOf("banner", "cover"),
    val metadataLayout: Int = 0, // 0 = Clean Separation, 1 = Stacked, 2 = Compact, 3 = Button-Integrated
    val showRatings: Boolean = true,
    val ratingFormat: Int = 0, // 0 = Golden Star, 1 = Percentage, 2 = Star + Source, 3 = Multi-Source
    val showUnreadBadge: Boolean = true,
    val statusFilters: Set<String> = emptySet(),
    val continueTrackingStyle: ContinueTrackingStyle = ContinueTrackingStyle.CARD,
    val shelfPosterSize: HomeShelfPosterSize = HomeShelfPosterSize.STANDARD,
    val shelfItemLimit: Int = 12,
    val defaultCategory: MediaType = MediaType.ALL
)

enum class PreferredTitleLanguage(val displayName: String, val description: String) {
    ENGLISH("English", "Prefer official English localization (e.g. The Apothecary Diaries)"),
    ROMAJI("Romaji", "Prefer Japanese romanized titles (e.g. Kusuriya no Hitorigoto)"),
    NATIVE("Native Japanese", "Prefer original Japanese kanji / kana script")
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
        val DETAILS_PAGE_SECTION_ORDER = stringPreferencesKey("details_page_section_order")
        val HOME_ROW_ORDER = stringPreferencesKey("home_row_order")
        val HOME_HERO_SOURCE = stringPreferencesKey("home_hero_source")
        val HOME_HERO_ENABLED = booleanPreferencesKey("home_hero_enabled")
        val HOME_HERO_AUTO_ROTATE = booleanPreferencesKey("home_hero_auto_rotate")
        val HOME_HERO_DIM_BANNER = booleanPreferencesKey("home_hero_dim_banner")
        val HOME_HERO_ALLOW_CROPPED_FALLBACK = booleanPreferencesKey("home_hero_allow_cropped_fallback")
        val HOME_HERO_STRICT_MODE = booleanPreferencesKey("home_hero_strict_mode")
        val HOME_HERO_STRICT_REQS = stringSetPreferencesKey("home_hero_strict_reqs")
        val HOME_HERO_METADATA_LAYOUT = intPreferencesKey("home_hero_metadata_layout")
        val HOME_HERO_SHOW_RATINGS = booleanPreferencesKey("home_hero_show_ratings")
        val HOME_HERO_RATING_FORMAT = intPreferencesKey("home_hero_rating_format")
        val HOME_HERO_SHOW_UNREAD_BADGE = booleanPreferencesKey("home_hero_show_unread_badge")
        val HOME_HERO_STATUS_FILTERS = stringSetPreferencesKey("home_hero_status_filters")
        val HOME_CONTINUE_STYLE = stringPreferencesKey("home_continue_style")
        val HOME_SHELF_POSTER_SIZE = stringPreferencesKey("home_shelf_poster_size")
        val HOME_SHELF_ITEM_LIMIT = intPreferencesKey("home_shelf_item_limit")
        val HOME_DEFAULT_CATEGORY = stringPreferencesKey("home_default_category")
        val LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
        val LIBRARY_SORT_MODE = stringPreferencesKey("library_sort_mode")

        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val CUSTOM_GEMINI_MODEL = stringPreferencesKey("custom_gemini_model")
        val USE_CUSTOM_GEMINI_MODEL = booleanPreferencesKey("use_custom_gemini_model")
        val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        val FANART_API_KEY = stringPreferencesKey("fanart_api_key")
        val STEAMGRIDDB_API_KEY = stringPreferencesKey("steamgriddb_api_key")
        val IGDB_CLIENT_ID = stringPreferencesKey("igdb_client_id")
        val IGDB_CLIENT_SECRET = stringPreferencesKey("igdb_client_secret")
        val MDBLIST_API_KEY = stringPreferencesKey("mdblist_api_key")
        val COMIC_VINE_API_KEY = stringPreferencesKey("comic_vine_api_key")
        val METRON_USERNAME = stringPreferencesKey("metron_username")
        val METRON_PASSWORD = stringPreferencesKey("metron_password")
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
        val PREFERRED_TITLE_LANGUAGE = stringPreferencesKey("preferred_title_language")
        val VN_SPOILER_LEVEL = intPreferencesKey("vn_spoiler_level")
        val VN_NSFW_FILTER_MODE = stringPreferencesKey("vn_nsfw_filter_mode")

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

    val geminiModel: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.GEMINI_MODEL] ?: "gemini-2.5-flash" }

    suspend fun updateGeminiModel(model: String) {
        context.dataStore.edit { it[PreferencesKeys.GEMINI_MODEL] = model }
    }

    val customGeminiModel: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.CUSTOM_GEMINI_MODEL] ?: "" }

    suspend fun updateCustomGeminiModel(customModel: String) {
        context.dataStore.edit { it[PreferencesKeys.CUSTOM_GEMINI_MODEL] = customModel }
    }

    val useCustomGeminiModel: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.USE_CUSTOM_GEMINI_MODEL] ?: false }

    suspend fun updateUseCustomGeminiModel(useCustom: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.USE_CUSTOM_GEMINI_MODEL] = useCustom }
    }

    val activeGeminiModel: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val useCustom = prefs[PreferencesKeys.USE_CUSTOM_GEMINI_MODEL] ?: false
            val custom = prefs[PreferencesKeys.CUSTOM_GEMINI_MODEL] ?: ""
            val preset = prefs[PreferencesKeys.GEMINI_MODEL] ?: "gemini-2.5-flash"
            if (useCustom && custom.isNotBlank()) custom.trim() else preset
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
        cachedTmdbApiKey = key
        context.dataStore.edit { it[PreferencesKeys.TMDB_API_KEY] = key }
    }

    val fanartApiKey: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.FANART_API_KEY] ?: "" }

    suspend fun updateFanartApiKey(key: String) {
        context.dataStore.edit { it[PreferencesKeys.FANART_API_KEY] = key }
    }

    val steamGridDbApiKey: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.STEAMGRIDDB_API_KEY] ?: "" }

    suspend fun updateSteamGridDbApiKey(key: String) {
        cachedSteamGridDbApiKey = key
        context.dataStore.edit { it[PreferencesKeys.STEAMGRIDDB_API_KEY] = key }
    }

    val igdbClientId: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.IGDB_CLIENT_ID] ?: "" }

    val igdbClientSecret: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.IGDB_CLIENT_SECRET] ?: "" }

    val comicVineApiKey: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.COMIC_VINE_API_KEY] ?: "" }

    suspend fun updateComicVineApiKey(key: String) {
        cachedComicVineApiKey = key
        context.dataStore.edit { it[PreferencesKeys.COMIC_VINE_API_KEY] = key }
    }

    val metronUsername: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.METRON_USERNAME] ?: "" }

    val metronPassword: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.METRON_PASSWORD] ?: "" }

    suspend fun updateMetronCredentials(username: String, password: String) {
        cachedMetronUsername = username
        cachedMetronPassword = password
        context.dataStore.edit {
            it[PreferencesKeys.METRON_USERNAME] = username
            it[PreferencesKeys.METRON_PASSWORD] = password
        }
    }

    suspend fun updateIgdbCredentials(clientId: String, clientSecret: String) {
        cachedIgdbClientId = clientId
        cachedIgdbClientSecret = clientSecret
        context.dataStore.edit {
            it[PreferencesKeys.IGDB_CLIENT_ID] = clientId
            it[PreferencesKeys.IGDB_CLIENT_SECRET] = clientSecret
        }
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

    val preferredTitleLanguage: Flow<PreferredTitleLanguage> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val raw = prefs[PreferencesKeys.PREFERRED_TITLE_LANGUAGE]
            val resolved = try {
                if (raw != null) PreferredTitleLanguage.valueOf(raw) else PreferredTitleLanguage.ROMAJI
            } catch (_: Exception) {
                PreferredTitleLanguage.ROMAJI
            }
            cachedPreferredTitleLanguage = resolved
            resolved
        }

    suspend fun updatePreferredTitleLanguage(language: PreferredTitleLanguage) {
        cachedPreferredTitleLanguage = language
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.PREFERRED_TITLE_LANGUAGE] = language.name
        }
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

    val detailsPageSectionOrder: Flow<List<DetailSectionItem>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val raw = prefs[PreferencesKeys.DETAILS_PAGE_SECTION_ORDER]
            if (raw.isNullOrBlank()) {
                DetailsPageLayoutDefaults.defaultSections
            } else {
                try {
                    val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<List<DetailSectionItem>>(raw)
                    val existingKeys = parsed.map { it.key }.toSet()
                    val missing = DetailsPageLayoutDefaults.defaultSections.filter { it.key !in existingKeys }
                    parsed + missing
                } catch (_: Exception) {
                    DetailsPageLayoutDefaults.defaultSections
                }
            }
        }

    suspend fun setDetailsPageSectionOrder(items: List<DetailSectionItem>) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DETAILS_PAGE_SECTION_ORDER] = Json.encodeToString(items)
        }
    }

    val homeRowOrder: Flow<List<HomeRowItem>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val raw = prefs[PreferencesKeys.HOME_ROW_ORDER]
            if (raw.isNullOrBlank()) {
                HomeLayoutDefaults.defaultRows
            } else {
                try {
                    val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<List<HomeRowItem>>(raw)
                    val existingKeys = parsed.map { it.key }.toSet()
                    val missing = HomeLayoutDefaults.defaultRows.filter { it.key !in existingKeys }
                    parsed + missing
                } catch (_: Exception) {
                    HomeLayoutDefaults.defaultRows
                }
            }
        }

    suspend fun setHomeRowOrder(items: List<HomeRowItem>) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.HOME_ROW_ORDER] = Json.encodeToString(items)
        }
    }

    val homeHeroSource: Flow<HomeHeroSource> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> HomeHeroSource.fromName(prefs[PreferencesKeys.HOME_HERO_SOURCE]) }

    suspend fun setHomeHeroSource(source: HomeHeroSource) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.HOME_HERO_SOURCE] = source.name
        }
    }

    val continueTrackingStyle: Flow<ContinueTrackingStyle> = context.dataStore.data
        .catch { if (it is IOException || it is ClassCastException) emit(emptyPreferences()) else throw it }
        .map { prefs -> ContinueTrackingStyle.fromName(prefs[PreferencesKeys.HOME_CONTINUE_STYLE]) }

    suspend fun setContinueTrackingStyle(style: ContinueTrackingStyle) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.HOME_CONTINUE_STYLE] = style.name
        }
    }

    val homeHeroSettings: Flow<HomeHeroSettings> = context.dataStore.data
        .catch { if (it is IOException || it is ClassCastException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            HomeHeroSettings(
                showHero = prefs[PreferencesKeys.HOME_HERO_ENABLED] ?: true,
                heroSource = HomeHeroSource.fromName(prefs[PreferencesKeys.HOME_HERO_SOURCE]),
                autoRotate = prefs[PreferencesKeys.HOME_HERO_AUTO_ROTATE] ?: true,
                dimBanner = prefs[PreferencesKeys.HOME_HERO_DIM_BANNER] ?: true,
                allowCroppedFallback = prefs[PreferencesKeys.HOME_HERO_ALLOW_CROPPED_FALLBACK] ?: false,
                strictMode = prefs[PreferencesKeys.HOME_HERO_STRICT_MODE] ?: false,
                strictRequirements = prefs[PreferencesKeys.HOME_HERO_STRICT_REQS] ?: setOf("banner", "cover"),
                metadataLayout = prefs[PreferencesKeys.HOME_HERO_METADATA_LAYOUT] ?: 0,
                showRatings = prefs[PreferencesKeys.HOME_HERO_SHOW_RATINGS] ?: true,
                ratingFormat = prefs[PreferencesKeys.HOME_HERO_RATING_FORMAT] ?: 0,
                showUnreadBadge = prefs[PreferencesKeys.HOME_HERO_SHOW_UNREAD_BADGE] ?: true,
                statusFilters = prefs[PreferencesKeys.HOME_HERO_STATUS_FILTERS] ?: emptySet(),
                continueTrackingStyle = ContinueTrackingStyle.fromName(prefs[PreferencesKeys.HOME_CONTINUE_STYLE]),
                shelfPosterSize = HomeShelfPosterSize.fromName(prefs[PreferencesKeys.HOME_SHELF_POSTER_SIZE]),
                shelfItemLimit = (prefs[PreferencesKeys.HOME_SHELF_ITEM_LIMIT] ?: 12).let {
                    if (it in listOf(8, 12, 20)) it else 12
                },
                defaultCategory = runCatching {
                    prefs[PreferencesKeys.HOME_DEFAULT_CATEGORY]?.let { MediaType.valueOf(it) } ?: MediaType.ALL
                }.getOrDefault(MediaType.ALL)
            )
        }

    suspend fun setHomeHeroEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HOME_HERO_ENABLED] = enabled }
    }

    suspend fun setHomeHeroAutoRotate(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HOME_HERO_AUTO_ROTATE] = enabled }
    }

    suspend fun setHomeHeroDimBanner(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HOME_HERO_DIM_BANNER] = enabled }
    }

    suspend fun setHomeHeroAllowCroppedFallback(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HOME_HERO_ALLOW_CROPPED_FALLBACK] = enabled }
    }

    suspend fun setHomeHeroStrictMode(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HOME_HERO_STRICT_MODE] = enabled }
    }

    suspend fun setHomeHeroStrictRequirements(reqs: Set<String>) {
        context.dataStore.edit { it[PreferencesKeys.HOME_HERO_STRICT_REQS] = reqs }
    }

    suspend fun setHomeHeroMetadataLayout(layout: Int) {
        context.dataStore.edit { it[PreferencesKeys.HOME_HERO_METADATA_LAYOUT] = layout.coerceIn(0, 3) }
    }

    suspend fun setHomeHeroShowRatings(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HOME_HERO_SHOW_RATINGS] = enabled }
    }

    suspend fun setHomeHeroRatingFormat(format: Int) {
        context.dataStore.edit { it[PreferencesKeys.HOME_HERO_RATING_FORMAT] = format.coerceIn(0, 3) }
    }

    suspend fun setHomeHeroShowUnreadBadge(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HOME_HERO_SHOW_UNREAD_BADGE] = enabled }
    }

    suspend fun setHomeHeroStatusFilters(statuses: Set<String>) {
        context.dataStore.edit { it[PreferencesKeys.HOME_HERO_STATUS_FILTERS] = statuses }
    }

    suspend fun setHomeShelfPosterSize(size: HomeShelfPosterSize) {
        context.dataStore.edit { it[PreferencesKeys.HOME_SHELF_POSTER_SIZE] = size.name }
    }

    suspend fun setHomeShelfItemLimit(limit: Int) {
        val safe = if (limit in listOf(8, 12, 20)) limit else 12
        context.dataStore.edit { it[PreferencesKeys.HOME_SHELF_ITEM_LIMIT] = safe }
    }

    suspend fun setHomeDefaultCategory(category: MediaType) {
        context.dataStore.edit { it[PreferencesKeys.HOME_DEFAULT_CATEGORY] = category.name }
    }

    suspend fun resetHomeAndHeroDefaults() {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.HOME_ROW_ORDER] = Json.encodeToString(HomeLayoutDefaults.defaultRows)
            prefs[PreferencesKeys.HOME_HERO_SOURCE] = HomeHeroSource.CONTINUE.name
            prefs[PreferencesKeys.HOME_HERO_ENABLED] = true
            prefs[PreferencesKeys.HOME_HERO_AUTO_ROTATE] = true
            prefs[PreferencesKeys.HOME_HERO_DIM_BANNER] = true
            prefs[PreferencesKeys.HOME_HERO_ALLOW_CROPPED_FALLBACK] = false
            prefs[PreferencesKeys.HOME_HERO_STRICT_MODE] = false
            prefs[PreferencesKeys.HOME_HERO_STRICT_REQS] = setOf("banner", "cover")
            prefs[PreferencesKeys.HOME_HERO_METADATA_LAYOUT] = 0
            prefs[PreferencesKeys.HOME_HERO_SHOW_RATINGS] = true
            prefs[PreferencesKeys.HOME_HERO_RATING_FORMAT] = 0
            prefs[PreferencesKeys.HOME_HERO_SHOW_UNREAD_BADGE] = true
            prefs[PreferencesKeys.HOME_HERO_STATUS_FILTERS] = emptySet()
            prefs[PreferencesKeys.HOME_CONTINUE_STYLE] = ContinueTrackingStyle.CARD.name
            prefs[PreferencesKeys.HOME_SHELF_POSTER_SIZE] = HomeShelfPosterSize.STANDARD.name
            prefs[PreferencesKeys.HOME_SHELF_ITEM_LIMIT] = 12
            prefs[PreferencesKeys.HOME_DEFAULT_CATEGORY] = MediaType.ALL.name
        }
    }

    val libraryViewMode: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[PreferencesKeys.LIBRARY_VIEW_MODE] ?: "GRID_3" }

    suspend fun setLibraryViewMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LIBRARY_VIEW_MODE] = mode
        }
    }

    val librarySortMode: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[PreferencesKeys.LIBRARY_SORT_MODE] ?: "RECENT" }

    suspend fun setLibrarySortMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LIBRARY_SORT_MODE] = mode
        }
    }

    val vnSpoilerLevel: Flow<VnSpoilerLevel> = context.dataStore.data
        .catch { if (it is IOException || it is ClassCastException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            VnSpoilerLevel.fromLevel(prefs[PreferencesKeys.VN_SPOILER_LEVEL])
        }

    suspend fun updateVnSpoilerLevel(level: VnSpoilerLevel) {
        cachedVnSpoilerLevel = level
        context.dataStore.edit { it[PreferencesKeys.VN_SPOILER_LEVEL] = level.level }
    }

    val vnNsfwFilterMode: Flow<VnNsfwFilterMode> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            VnNsfwFilterMode.fromName(prefs[PreferencesKeys.VN_NSFW_FILTER_MODE])
        }

    suspend fun updateVnNsfwFilterMode(mode: VnNsfwFilterMode) {
        cachedVnNsfwFilterMode = mode
        context.dataStore.edit { it[PreferencesKeys.VN_NSFW_FILTER_MODE] = mode.name }
    }

    companion object {
        @Volatile
        var cachedPreferredTitleLanguage: PreferredTitleLanguage = PreferredTitleLanguage.ROMAJI

        @Volatile
        var cachedVnSpoilerLevel: VnSpoilerLevel = VnSpoilerLevel.MINOR

        @Volatile
        var cachedVnNsfwFilterMode: VnNsfwFilterMode = VnNsfwFilterMode.BLUR_TAP

        @Volatile
        var cachedSteamGridDbApiKey: String = ""

        @Volatile
        var cachedTmdbApiKey: String = ""

        @Volatile
        var cachedIgdbClientId: String = ""

        @Volatile
        var cachedIgdbClientSecret: String = ""

        @Volatile
        var cachedComicVineApiKey: String = ""

        @Volatile
        var cachedMetronUsername: String = ""

        @Volatile
        var cachedMetronPassword: String = ""

        fun getDefaultSourcesFor(mediaType: MediaType): List<String> = when (mediaType) {
            MediaType.ANIME -> listOf("AniList GraphQL", "Bangumi", "Kitsu")
            MediaType.MANGA -> listOf("MangaDex API", "AniList", "Comick")
            MediaType.COMIC -> listOf("Comic Vine", "Metron", "Apple Books", "Open Library")
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
