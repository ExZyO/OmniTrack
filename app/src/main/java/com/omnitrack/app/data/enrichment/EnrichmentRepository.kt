package com.omnitrack.app.data.enrichment

import com.omnitrack.app.util.AppLogger
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.AnimeAiringSchedule
import com.omnitrack.app.data.model.AnimeCharacterDetail
import com.omnitrack.app.data.model.AnimeCharacterAppearance
import com.omnitrack.app.data.model.AnimeCharacterVoiceActor
import com.omnitrack.app.data.model.AnimeCharacterItem
import com.omnitrack.app.data.model.AnimeEnrichedDetails
import com.omnitrack.app.data.model.AnimeRelationItem
import com.omnitrack.app.data.model.AnimeStaffItem
import com.omnitrack.app.data.model.AnimeVoiceActorDetail
import com.omnitrack.app.data.model.StaffProductionCreditItem
import com.omnitrack.app.data.model.VoiceActorRoleItem
import com.omnitrack.app.data.model.UserProfileData
import com.omnitrack.app.data.model.UserFavoriteMedia
import com.omnitrack.app.data.model.UserFavoriteCharacter
import com.omnitrack.app.data.model.UserFavoriteStaff
import com.omnitrack.app.data.model.UserActivityDay
import com.omnitrack.app.data.model.UserStatSet
import com.omnitrack.app.data.model.UserStatEntry
import com.omnitrack.app.data.model.UserMediaListItem
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.data.preferences.CachedMultiRatings
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.sync.TrackerTokenManager
import com.omnitrack.app.ui.screens.detail.components.MediaTrailer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class EnrichmentData(
    val clearartUrl: String? = null,
    val tmdbBackdropUrl: String? = null,
    val communityScore: Float? = null,
    val imdbScore: Float? = null,
    val rtScore: Int? = null,
    val metacriticScore: Int? = null,
    val traktScore: Int? = null,
    val audienceScore: Int? = null,
    val trailerYoutubeKey: String? = null,
    val studioOrNetwork: String? = null,
    val runtimeMinutes: Int? = null,
    val contentRating: String? = null,
    val airStatus: String? = null,
    val totalEpisodes: Int? = null,
    val originCountry: String? = null,
    val originalLanguage: String? = null,
    val releaseDate: String? = null,
    val lastAirDate: String? = null,
    val releaseYear: String? = null,
    val tmdbId: Long? = null,
    val videos: List<MediaTrailer> = emptyList(),
    val resolvedTmdbId: Long? = null,
    val resolvedImdbId: String? = null,
    val belongsToCollectionId: Long? = null,
    val belongsToCollectionName: String? = null
)

/**
 * Metadata Enrichment Waterfall.
 * Resolves transparent clearart title logos (Fanart.tv / TMDb logos) and multi-provider ratings
 * (IMDb, TMDb, Trakt, Rotten Tomatoes, Metacritic) for maximum visual fidelity on the Detail canvas.
 */
class EnrichmentRepository(
    private val client: OkHttpClient = com.omnitrack.app.data.network.NetworkClient.getClient()
) {
    companion object {
        private val clearartMemoryCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val aniZipLogoMemoryCache = java.util.concurrent.ConcurrentHashMap<Long, String>()
        private val aniZipRootCache = java.util.concurrent.ConcurrentHashMap<Long, JsonObject>()
        private val episodeFillerMemoryCache = java.util.concurrent.ConcurrentHashMap<Long, Map<Int, com.omnitrack.app.data.model.EpisodeFillerInfo>>()
        @Volatile private var jikanCircuitBreakerUntil = 0L

        fun getCachedFillerInfo(malId: Long): Map<Int, com.omnitrack.app.data.model.EpisodeFillerInfo>? {
            return episodeFillerMemoryCache[malId]
        }

        fun cacheKeyFor(item: MediaItemEntity): String {
            return when {
                item.tmdbId != null -> "tmdb_${item.mediaType}_${item.tmdbId}"
                item.anilistId != null -> "anilist_${item.mediaType}_${item.anilistId}"
                item.malId != null -> "mal_${item.mediaType}_${item.malId}"
                item.id > 0 -> "id_${item.id}"
                else -> "title_${item.mediaType}_${item.title.lowercase().trim()}"
            }
        }

        fun getCachedClearart(item: MediaItemEntity): String? {
            val key = cacheKeyFor(item)
            return clearartMemoryCache[key]
        }

        fun putCachedClearart(item: MediaItemEntity, url: String) {
            if (url.isNotBlank()) {
                val key = cacheKeyFor(item)
                clearartMemoryCache[key] = url
            }
        }

        fun clearMemoryCaches() {
            clearartMemoryCache.clear()
            aniZipLogoMemoryCache.clear()
            aniZipRootCache.clear()
            episodeFillerMemoryCache.clear()
        }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val jikanClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(4000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private data class TmdbRawSeasonMeta(
        val seasonNumber: Int,
        val rawName: String,
        val episodeCount: Int,
        val posterUrl: String?,
        val airDate: String?
    )

    private data class SkyHookShowData(
        val tvdbId: Long,
        val seasonPosters: Map<Int, String>,
        val episodesBySeason: Map<Int, List<com.omnitrack.app.data.model.MediaEpisode>>
    )

    private data class TmdbEpisodeGroupSeason(
        val seasonNumber: Int,
        val name: String,
        val airDate: String?,
        val episodes: List<com.omnitrack.app.data.model.MediaEpisode>
    )

    private data class ExternalEpisodeRating(
        val season: Int,
        val episode: Int,
        val title: String,
        val airDate: String?,
        val rating: Float,
        val votes: Int?,
        val source: String
    )

    private val cinemetaVideosCache = java.util.concurrent.ConcurrentHashMap<String, List<com.omnitrack.app.data.model.MediaEpisode>>()
    private val cinemetaTvdbIdCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val tmdbTvdbIdCache = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private val skyHookShowCache = java.util.concurrent.ConcurrentHashMap<Long, SkyHookShowData>()
    private val tmdbSeason1StillsCache = java.util.concurrent.ConcurrentHashMap<Long, List<com.omnitrack.app.data.model.MediaEpisode>>()
    private val tmdbSeasonMetaCache = java.util.concurrent.ConcurrentHashMap<Long, List<TmdbRawSeasonMeta>>()
    private val tmdbSeasonRawEpisodesCache = java.util.concurrent.ConcurrentHashMap<String, List<com.omnitrack.app.data.model.MediaEpisode>>()
    private val tmdbAnimeEpisodeGroupCache = java.util.concurrent.ConcurrentHashMap<Long, List<TmdbEpisodeGroupSeason>>()
    private val omdbSeasonRatingsCache = java.util.concurrent.ConcurrentHashMap<String, List<ExternalEpisodeRating>>()
    private val traktSeasonRatingsCache = java.util.concurrent.ConcurrentHashMap<String, List<ExternalEpisodeRating>>()
    private val aniListSeasonPosterCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val aniListSubSeriesCoursCache = java.util.concurrent.ConcurrentHashMap<String, List<Pair<Long, String?>>>()
    private val kitsuSeasonDataCache = java.util.concurrent.ConcurrentHashMap<String, KitsuSeasonData>()
    private val userMediaListCache = java.util.concurrent.ConcurrentHashMap<String, List<UserMediaListItem>>()

    suspend fun resolveClearartFast(
        item: MediaItemEntity,
        fanartApiKey: String? = null,
        tmdbApiKey: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        val isStaleTvdbLogo = item.clearartUrl?.contains("thetvdb.com") == true && !item.clearartUrl.contains("tmdb.org")
        if (!item.clearartUrl.isNullOrBlank() && (!isStaleTvdbLogo || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE)) {
            putCachedClearart(item, item.clearartUrl)
            return@withContext item.clearartUrl
        }
        val cached = getCachedClearart(item)
        if (!cached.isNullOrBlank() && (!cached.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE)) {
            return@withContext cached
        }

        val effectiveTmdbKey = tmdbApiKey?.ifBlank { null } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
        var effectiveTmdbId = item.tmdbId
        var isMovie = item.mediaType == MediaType.MOVIE

        if (effectiveTmdbId == null && item.mediaType == MediaType.ANIME) {
            if (item.anilistId != null && item.anilistId > 0) {
                val azMap = fetchAniZipMapping(item.anilistId)
                if (azMap?.tmdbId != null) {
                    effectiveTmdbId = azMap.tmdbId
                    isMovie = azMap.isMovie
                }
            }
            if (effectiveTmdbId == null && item.title.isNotBlank()) {
                val animeTmdb = searchTmdbForAnime(item.title, effectiveTmdbKey, item.nativeTitle)
                if (animeTmdb != null) {
                    effectiveTmdbId = animeTmdb.first
                    isMovie = animeTmdb.second
                }
            }
        } else if (item.mediaType == MediaType.ANIME && item.anilistId != null && item.anilistId > 0) {
            val azMap = fetchAniZipMapping(item.anilistId)
            if (azMap != null && azMap.isMovie) isMovie = true
        }

        var clearart: String? = null
        if (effectiveTmdbId != null) {
            if (!effectiveTmdbKey.isNullOrBlank()) {
                clearart = fetchTmdbClearart(effectiveTmdbId, isMovie, effectiveTmdbKey)
            }
            if (clearart.isNullOrBlank() && !fanartApiKey.isNullOrBlank()) {
                clearart = fetchFanartClearart(effectiveTmdbId, isMovie, fanartApiKey)
            }
        }
        if (clearart.isNullOrBlank() && item.anilistId != null && item.anilistId > 0) {
            clearart = fetchAniZipClearlogo(item.anilistId, effectiveTmdbKey)
        }

        if (!clearart.isNullOrBlank()) {
            putCachedClearart(item, clearart)
        }
        clearart
    }

    suspend fun enrich(
        item: MediaItemEntity,
        fanartApiKey: String? = null,
        tmdbApiKey: String? = null,
        mdblistApiKey: String? = null,
        omdbApiKey: String? = null,
        onClearartResolved: (suspend (String, Long?) -> Unit)? = null
    ): EnrichmentData = withContext(Dispatchers.IO) {
        if (item.mediaType !in setOf(MediaType.ANIME, MediaType.SHOW, MediaType.MOVIE)) {
            return@withContext EnrichmentData(
                clearartUrl = item.clearartUrl,
                communityScore = item.communityScore,
                releaseYear = item.releaseYear
            )
        }
        var clearart: String? = item.clearartUrl
        var tmdbBackdropUrl: String? = null
        var communityScore: Float? = item.communityScore
        var imdbScore: Float? = item.imdbScore
        var rtScore: Int? = item.rtScore
        var metacriticScore: Int? = item.metacriticScore
        var traktScore: Int? = item.traktScore
        var audienceScore: Int? = item.audienceScore
        val effectiveTmdbKey = tmdbApiKey?.ifBlank { null } ?: "844dba0bfd8f3a4f3799f6130ef9e335"

        var effectiveTmdbId = item.tmdbId
        var isMovie = item.mediaType == MediaType.MOVIE
        var isShow = item.mediaType == MediaType.SHOW

        // Auto-resolve TMDb ID for Anime if missing via AniZip or TMDb search
        if (effectiveTmdbId == null && item.mediaType == MediaType.ANIME) {
            if (item.anilistId != null && item.anilistId > 0) {
                val azMap = fetchAniZipMapping(item.anilistId)
                if (azMap?.tmdbId != null) {
                    effectiveTmdbId = azMap.tmdbId
                    isMovie = azMap.isMovie
                    isShow = !azMap.isMovie
                }
            }
            if (effectiveTmdbId == null && item.title.isNotBlank()) {
                val animeTmdb = searchTmdbForAnime(item.title, effectiveTmdbKey, item.nativeTitle)
                if (animeTmdb != null) {
                    effectiveTmdbId = animeTmdb.first
                    isMovie = animeTmdb.second
                    isShow = !animeTmdb.second
                }
            }
        } else if (item.mediaType == MediaType.ANIME && item.anilistId != null && item.anilistId > 0) {
            val azMap = fetchAniZipMapping(item.anilistId)
            if (azMap != null && azMap.isMovie) {
                isMovie = true
                isShow = false
            }
        }

        // 1. Resolve Transparent Clearart Logo (TMDb / Fanart first with language matching; replace stale TVDB logos)
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        val isStaleTvdbLogo = clearart?.contains("thetvdb.com") == true &&
            !clearart.contains("tmdb.org") &&
            pref != com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
        if (isStaleTvdbLogo) {
            clearart = null
        }
        val shouldQueryTmdbImages = effectiveTmdbId != null && !effectiveTmdbKey.isNullOrBlank() && (
            clearart.isNullOrBlank() || (
                item.mediaType in setOf(MediaType.ANIME, MediaType.SHOW, MediaType.MOVIE) &&
                    item.customBannerPath.isNullOrBlank() &&
                    item.artworkRole == "DEFAULT"
                )
            )
        val tmdbImages = if (shouldQueryTmdbImages && effectiveTmdbId != null) {
            fetchTmdbImages(effectiveTmdbId, isMovie, effectiveTmdbKey)
        } else {
            null
        }
        if (tmdbImages?.second != null) {
            tmdbBackdropUrl = tmdbImages.second
        }
        if (clearart.isNullOrBlank()) {
            var newClearart: String? = null
            if (effectiveTmdbId != null) {
                if (!effectiveTmdbKey.isNullOrBlank()) {
                    newClearart = tmdbImages?.first
                }
                if (newClearart.isNullOrBlank() && !fanartApiKey.isNullOrBlank()) {
                    newClearart = fetchFanartClearart(effectiveTmdbId, isMovie, fanartApiKey)
                }
            }
            if (newClearart.isNullOrBlank() && item.anilistId != null && item.anilistId > 0) {
                newClearart = fetchAniZipClearlogo(item.anilistId)
            }
            if (!newClearart.isNullOrBlank()) {
                clearart = newClearart
            }
        }

        if (!clearart.isNullOrBlank()) {
            val isAllowedClearart = !clearart.contains("thetvdb.com") ||
                pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
            if (isAllowedClearart) {
                putCachedClearart(item, clearart)
                if (clearart != item.clearartUrl) {
                    onClearartResolved?.invoke(clearart, effectiveTmdbId)
                }
            }
        }

        var resolvedImdbId: String? = null
        if (effectiveTmdbId != null) {
            resolvedImdbId = fetchTmdbImdbId(effectiveTmdbId, isMovie, effectiveTmdbKey)
        }

        // 2. Resolve Multi-Score Aggregation (IMDb, Rotten Tomatoes, Metacritic, Trakt, Audience)
        // Check if cached scores are stale or missing (e.g. The Mentalist movie collision with imdb=6.7)
        val hasStaleScores = imdbScore == 6.7f || (imdbScore != null && rtScore != null && traktScore == null)
        val needsRatings = imdbScore == null || rtScore == null || metacriticScore == null || traktScore == null || audienceScore == null || hasStaleScores

        if (!mdblistApiKey.isNullOrBlank() && needsRatings && (resolvedImdbId != null || effectiveTmdbId != null || item.title.isNotBlank())) {
            val mdbRatings = fetchMdbListRatings(resolvedImdbId, effectiveTmdbId, isMovie, item.title, mdblistApiKey, item.nativeTitle)
            if (mdbRatings != null) {
                if (mdbRatings.imdbScore != null) imdbScore = mdbRatings.imdbScore
                if (mdbRatings.rtScore != null) rtScore = mdbRatings.rtScore
                if (mdbRatings.metacriticScore != null) metacriticScore = mdbRatings.metacriticScore
                if (mdbRatings.traktScore != null) traktScore = mdbRatings.traktScore
                if (mdbRatings.audienceScore != null) audienceScore = mdbRatings.audienceScore
                if (mdbRatings.tmdbScore != null && (isMovie || isShow || item.mediaType == MediaType.ANIME)) communityScore = mdbRatings.tmdbScore
            }
        }

        // Optional Free OMDb Backup (IMDb, Rotten Tomatoes & Metascore)
        val effectiveOmdbKey = omdbApiKey?.ifBlank { null }
            ?: PreferenceManager.cachedOmdbApiKey.ifBlank { null }
        if (effectiveOmdbKey != null &&
            (imdbScore == null || rtScore == null || metacriticScore == null) &&
            (resolvedImdbId != null || effectiveTmdbId != null)
        ) {
            val omdbImdbId = resolvedImdbId ?: effectiveTmdbId?.let { fetchTmdbImdbId(it, isMovie, effectiveTmdbKey) }
            if (omdbImdbId != null) {
                resolvedImdbId = omdbImdbId
                val omdb = fetchOmdbRatings(omdbImdbId, effectiveOmdbKey)
                if (omdb != null) {
                    if (imdbScore == null && omdb.imdbScore != null) imdbScore = omdb.imdbScore
                    if (rtScore == null && omdb.rtScore != null) rtScore = omdb.rtScore
                    if (metacriticScore == null && omdb.metacriticScore != null) metacriticScore = omdb.metacriticScore
                }
            }
        }

        // Zero-Key Fallback for IMDb (Cinemeta public Stremio endpoint)
        if (imdbScore == null && (resolvedImdbId != null || effectiveTmdbId != null)) {
            val finalImdbId = resolvedImdbId ?: effectiveTmdbId?.let { fetchTmdbImdbId(it, isMovie, effectiveTmdbKey) }
            if (finalImdbId != null) {
                imdbScore = fetchCinemetaImdbById(finalImdbId, isMovie)
            }
        }

        // 3. Fallback / Native vertical rating enrichment
        if (communityScore == null) {
            when (item.mediaType) {
                MediaType.GAME -> {
                    if (item.igdbId != null) {
                        val steamData = fetchSteamAppDetails(item.igdbId)
                        if (steamData != null) {
                            if (metacriticScore == null) metacriticScore = steamData.metacriticScore
                            if (communityScore == null) communityScore = steamData.communityScore
                        }
                    }
                }
                MediaType.ANIME, MediaType.MANGA, MediaType.NOVEL -> {
                    if (item.malId != null) {
                        communityScore = fetchJikanScore(item.malId, isManga = item.mediaType != MediaType.ANIME)
                    }
                    if (communityScore == null && item.bangumiId != null) {
                        communityScore = fetchBangumiScore(item.bangumiId)
                    }
                }
                MediaType.SHOW, MediaType.MOVIE -> {
                    if (item.tmdbId != null) {
                        communityScore = fetchTmdbVote(item.tmdbId, item.mediaType == MediaType.MOVIE, effectiveTmdbKey)
                    }
                }
                else -> {}
            }
        }

        var trailerKey = item.trailerYoutubeKey
        var studio = item.studioOrNetwork
        var runtime = item.runtimeMinutes
        var airStatus = item.airStatus
        var contentRating = item.contentRating
        var totalEpisodes: Int? = null
        var originCountry: String? = null
        var originalLanguage: String? = null
        var releaseDate: String? = null
        var lastAirDate: String? = null
        var releaseYear: String? = item.releaseYear
        var videoList: List<MediaTrailer> = emptyList()
        var belongsToCollectionId: Long? = null
        var belongsToCollectionName: String? = null

        if (effectiveTmdbId != null) {
            val details = fetchTmdbDetails(effectiveTmdbId, isMovie, effectiveTmdbKey)
            if (details != null) {
                if (tmdbBackdropUrl == null && !details.backdropPath.isNullOrBlank()) {
                    tmdbBackdropUrl = "https://image.tmdb.org/t/p/original${details.backdropPath}"
                }
                if (trailerKey == null) trailerKey = details.trailerKey
                if (studio == null) studio = details.studio
                if (runtime == null) runtime = details.runtime
                if (airStatus == null) airStatus = details.status
                if (contentRating == null) contentRating = details.contentRating
                totalEpisodes = details.totalEpisodes
                originCountry = details.originCountry
                originalLanguage = details.originalLanguage
                releaseDate = details.releaseDate
                lastAirDate = details.lastAirDate
                if (releaseYear == null) releaseYear = details.releaseYear
                videoList = details.videos
                belongsToCollectionId = details.belongsToCollectionId
                belongsToCollectionName = details.belongsToCollectionName
            }
        }

        EnrichmentData(
            clearartUrl = clearart,
            tmdbBackdropUrl = tmdbBackdropUrl,
            communityScore = communityScore,
            imdbScore = imdbScore,
            rtScore = rtScore,
            metacriticScore = metacriticScore,
            traktScore = traktScore,
            audienceScore = audienceScore,
            trailerYoutubeKey = trailerKey,
            studioOrNetwork = studio,
            runtimeMinutes = runtime,
            contentRating = contentRating,
            airStatus = airStatus,
            totalEpisodes = totalEpisodes,
            originCountry = originCountry,
            originalLanguage = originalLanguage,
            releaseDate = releaseDate,
            lastAirDate = lastAirDate,
            releaseYear = releaseYear,
            videos = videoList,
            resolvedTmdbId = effectiveTmdbId,
            resolvedImdbId = resolvedImdbId,
            belongsToCollectionId = belongsToCollectionId,
            belongsToCollectionName = belongsToCollectionName
        )
    }

    private suspend fun fetchFanartClearart(tmdbId: Long, isMovie: Boolean, apiKey: String): String? =
        NetworkPacer.withGeneralPacing {
            try {
                val endpoint = if (isMovie) "movies" else "tv"
                val url = "https://webservice.fanart.tv/v3/$endpoint/$tmdbId?api_key=$apiKey"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withGeneralPacing null
                    val body = response.body?.string() ?: return@withGeneralPacing null
                    val root = json.parseToJsonElement(body).jsonObject
                    val logos = if (isMovie) {
                        root["hdmovielogo"]?.jsonArray ?: root["movielogo"]?.jsonArray
                    } else {
                        root["hdtvlogo"]?.jsonArray ?: root["clearlogo"]?.jsonArray
                    } ?: return@withGeneralPacing null

                    val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
                    val best = when (pref) {
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE -> {
                            logos.firstOrNull { it.jsonObject["lang"]?.jsonPrimitive?.content == "ja" }
                                ?: logos.firstOrNull { it.jsonObject["lang"]?.jsonPrimitive?.content == "en" }
                                ?: logos.firstOrNull()
                        }
                        else -> {
                            logos.firstOrNull { it.jsonObject["lang"]?.jsonPrimitive?.content == "en" }
                                ?: logos.firstOrNull { it.jsonObject["lang"]?.jsonPrimitive?.content == "ja" }
                                ?: logos.firstOrNull()
                        }
                    }

                    best?.jsonObject?.get("url")?.jsonPrimitive?.content
                }
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun fetchTmdbImages(tmdbId: Long, isMovie: Boolean, apiKey: String): Pair<String?, String?> =
        NetworkPacer.withTmdbPacing {
            try {
                val typeStr = if (isMovie) "movie" else "tv"
                val url = "https://api.themoviedb.org/3/$typeStr/$tmdbId/images?api_key=$apiKey&include_image_language=en,null,xx,ja"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withTmdbPacing (null to null)
                    val body = response.body?.string() ?: return@withTmdbPacing (null to null)
                    val root = json.parseToJsonElement(body).jsonObject

                    val logos = root["logos"]?.jsonArray
                    val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
                    val bestLogo = if (logos != null) {
                        when (pref) {
                            com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE -> {
                                logos.firstOrNull { it.jsonObject["iso_639_1"]?.takeUnless { n -> n is JsonNull }?.jsonPrimitive?.contentOrNull == "ja" }
                                    ?: logos.firstOrNull { it.jsonObject["iso_639_1"]?.takeUnless { n -> n is JsonNull }?.jsonPrimitive?.contentOrNull == "en" }
                                    ?: logos.firstOrNull { it.jsonObject["iso_639_1"] == null || it.jsonObject["iso_639_1"] is JsonNull }
                                    ?: logos.firstOrNull()
                            }
                            else -> {
                                logos.firstOrNull { it.jsonObject["iso_639_1"]?.takeUnless { n -> n is JsonNull }?.jsonPrimitive?.contentOrNull == "en" }
                                    ?: logos.firstOrNull { it.jsonObject["iso_639_1"] == null || it.jsonObject["iso_639_1"] is JsonNull }
                                    ?: logos.firstOrNull { it.jsonObject["iso_639_1"]?.takeUnless { n -> n is JsonNull }?.jsonPrimitive?.contentOrNull == "ja" }
                                    ?: logos.firstOrNull()
                            }
                        }
                    } else null
                    val logoPath = bestLogo?.jsonObject?.get("file_path")?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull?.trim()
                    val logoUrl = if (!logoPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$logoPath" else null

                    val backdrops = root["backdrops"]?.jsonArray
                    val bestBackdropPath = backdrops
                        ?.mapNotNull { elem ->
                            val obj = elem.jsonObject
                            val path = obj["file_path"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull?.trim()
                            if (path.isNullOrBlank()) return@mapNotNull null
                            val iso = obj["iso_639_1"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
                            val isTextless = iso.isNullOrBlank() || iso.equals("null", ignoreCase = true) || iso.equals("xx", ignoreCase = true)
                            val aspect = obj["aspect_ratio"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
                            val width = obj["width"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                            val voteAvg = obj["vote_average"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
                            val voteCount = obj["vote_count"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                            Triple(
                                path,
                                Triple(isTextless && aspect >= 1.5f && width >= 1280, isTextless && aspect >= 1.4f, aspect >= 1.4f),
                                Triple(voteAvg, voteCount, width)
                            )
                        }
                        ?.sortedWith(
                            compareByDescending<Triple<String, Triple<Boolean, Boolean, Boolean>, Triple<Float, Int, Int>>> { it.second.first }
                                .thenByDescending { it.second.second }
                                .thenByDescending { it.second.third }
                                .thenByDescending { it.third.first }
                                .thenByDescending { it.third.second }
                                .thenByDescending { it.third.third }
                        )
                        ?.firstOrNull()
                        ?.first
                    val backdropUrl = if (!bestBackdropPath.isNullOrBlank()) "https://image.tmdb.org/t/p/original$bestBackdropPath" else null

                    logoUrl to backdropUrl
                }
            } catch (_: Exception) {
                null to null
            }
        }

    private suspend fun fetchTmdbClearart(tmdbId: Long, isMovie: Boolean, apiKey: String): String? =
        fetchTmdbImages(tmdbId, isMovie, apiKey).first

    suspend fun fetchTmdbImdbId(tmdbId: Long, isMovie: Boolean, apiKey: String): String? =
        NetworkPacer.withTmdbPacing {
            try {
                val typeStr = if (isMovie) "movie" else "tv"
                val endpoint = if (isMovie) "external_ids" else "external_ids"
                val url = "https://api.themoviedb.org/3/$typeStr/$tmdbId/$endpoint?api_key=$apiKey"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withTmdbPacing null
                    val body = response.body?.string() ?: return@withTmdbPacing null
                    val root = json.parseToJsonElement(body).jsonObject
                    root["tvdb_id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.takeIf { it > 0L }?.let {
                        tmdbTvdbIdCache[tmdbId] = it
                    }
                    root["imdb_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                }
            } catch (_: Exception) {
                null
            }
        }

    private data class MdbRatings(
        val imdbScore: Float? = null,
        val rtScore: Int? = null,
        val metacriticScore: Int? = null,
        val traktScore: Int? = null,
        val audienceScore: Int? = null,
        val tmdbScore: Float? = null
    )

    suspend fun searchTmdbForAnime(title: String, apiKey: String, nativeTitle: String? = null): Pair<Long, Boolean>? =
        NetworkPacer.withTmdbPacing {
            try {
                val cleanTitle = title
                    .replace(Regex("(?i)\\b(season|part|cour|act)\\s*\\d+.*$"), "")
                    .replace(Regex("(?i)\\s*-\\s*(season|part|cour)\\s*\\d+.*$"), "")
                    .trim()
                val queryTitle = cleanTitle.ifBlank { title }

                val searchTitles = listOfNotNull(
                    queryTitle,
                    nativeTitle?.takeIf { it.isNotBlank() && it != queryTitle }
                )

                for (searchQ in searchTitles) {
                    // 1. Search TV & Movie — prefer animated (genre 16) matches so anime movies never bind to unrelated live-action TV
                    val tvUrl = "https://api.themoviedb.org/3/search/tv?api_key=$apiKey&query=${URLEncoder.encode(searchQ, "UTF-8")}"
                    val tvReq = Request.Builder().url(tvUrl).get().build()
                    val bestAnimatedTvId = client.newCall(tvReq).execute().use { resp ->
                        if (!resp.isSuccessful) null
                        else {
                            val body = resp.body?.string() ?: return@use null
                            val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return@use null
                            val candidates = results.mapNotNull { it as? JsonObject }
                            val animated = candidates.firstOrNull { cand ->
                                val gIds = cand["genre_ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull?.toIntOrNull() }.orEmpty()
                                16 in gIds
                            }
                            animated?.get("id")?.jsonPrimitive?.content?.toLongOrNull()
                        }
                    }
                    if (bestAnimatedTvId != null) return@withTmdbPacing Pair(bestAnimatedTvId, false)

                    // 2. Search Movie (for anime movies) — prefer animated movie matches
                    val movieUrl = "https://api.themoviedb.org/3/search/movie?api_key=$apiKey&query=${URLEncoder.encode(searchQ, "UTF-8")}"
                    val movieReq = Request.Builder().url(movieUrl).get().build()
                    val bestAnimatedMovieId = client.newCall(movieReq).execute().use { resp ->
                        if (!resp.isSuccessful) null
                        else {
                            val body = resp.body?.string() ?: return@use null
                            val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return@use null
                            val candidates = results.mapNotNull { it as? JsonObject }
                            val animated = candidates.firstOrNull { cand ->
                                val gIds = cand["genre_ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull?.toIntOrNull() }.orEmpty()
                                16 in gIds
                            } ?: candidates.firstOrNull()
                            animated?.get("id")?.jsonPrimitive?.content?.toLongOrNull()
                        }
                    }
                    if (bestAnimatedMovieId != null) return@withTmdbPacing Pair(bestAnimatedMovieId, true)
                }
                null
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun fetchOmdbRatings(
        imdbId: String,
        apiKey: String
    ): MdbRatings? = NetworkPacer.withGeneralPacing {
        try {
            val url = "https://www.omdbapi.com/?i=${URLEncoder.encode(imdbId, "UTF-8")}&apikey=${URLEncoder.encode(apiKey, "UTF-8")}"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing null
                val body = response.body?.string() ?: return@withGeneralPacing null
                val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                    ?: return@withGeneralPacing null
                if (!root["Response"]?.jsonPrimitive?.contentOrNull.equals("True", ignoreCase = true)) {
                    return@withGeneralPacing null
                }
                val imdb = root["imdbRating"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { !it.equals("N/A", ignoreCase = true) }
                    ?.toFloatOrNull()
                    ?.takeIf { it > 0f }
                var rt: Int? = null
                var meta: Int? = root["Metascore"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { !it.equals("N/A", ignoreCase = true) }
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
                val ratingsArr = root["Ratings"] as? JsonArray
                ratingsArr?.forEach { node ->
                    val obj = node as? JsonObject ?: return@forEach
                    val source = obj["Source"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val valStr = obj["Value"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    when {
                        source.equals("Rotten Tomatoes", ignoreCase = true) -> {
                            rt = valStr.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 1..100 }
                        }
                        source.equals("Metacritic", ignoreCase = true) && meta == null -> {
                            meta = valStr.substringBefore("/").trim().toIntOrNull()?.takeIf { it in 1..100 }
                        }
                    }
                }
                if (imdb == null && rt == null && meta == null) null
                else MdbRatings(imdbScore = imdb, rtScore = rt, metacriticScore = meta)
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchMdbListRatings(
        imdbId: String?,
        tmdbId: Long?,
        isMovie: Boolean,
        title: String,
        apiKey: String,
        nativeTitle: String? = null
    ): MdbRatings? = NetworkPacer.withGeneralPacing {
        try {
            val mediaType = if (isMovie) "movie" else "show"
            val queryParam = if (!imdbId.isNullOrBlank()) {
                "i=$imdbId&m=$mediaType"
            } else if (tmdbId != null) {
                "tm=$tmdbId&m=$mediaType"
            } else {
                "s=${URLEncoder.encode(title, "UTF-8")}&m=$mediaType"
            }
            var res = parseMdbListUrl("https://mdblist.com/api/?apikey=$apiKey&$queryParam")
            if (res == null && tmdbId != null) {
                // Try without &m= constraint in case type was mismatched
                res = parseMdbListUrl("https://mdblist.com/api/?apikey=$apiKey&tm=$tmdbId")
            }
            if (res == null) {
                // Fallback to title search without media type constraint
                res = parseMdbListUrl("https://mdblist.com/api/?apikey=$apiKey&s=${URLEncoder.encode(title, "UTF-8")}")
                if (res == null && !nativeTitle.isNullOrBlank()) {
                    res = parseMdbListUrl("https://mdblist.com/api/?apikey=$apiKey&s=${URLEncoder.encode(nativeTitle, "UTF-8")}")
                }
            }
            res
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMdbListUrl(url: String): MdbRatings? {
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = try {
                json.parseToJsonElement(body).jsonObject
            } catch (_: Exception) {
                return null
            }

            // If MDBList returned a search result wrapper {"search": [...]}, resolve the top candidate directly
            if (root.containsKey("search")) {
                val searchList = root["search"]?.jsonArray
                val firstMatch = searchList?.firstOrNull()?.jsonObject
                if (firstMatch != null) {
                    val foundImdb = firstMatch["imdbid"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    val foundTmdb = firstMatch["tmdbid"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    val foundId = firstMatch["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    val apiKey = url.substringAfter("apikey=").substringBefore("&")
                    val subParam = when {
                        foundImdb != null -> "i=$foundImdb"
                        foundTmdb != null -> "tm=$foundTmdb"
                        foundId != null -> "id=$foundId"
                        else -> null
                    }
                    if (subParam != null) {
                        return parseMdbListUrl("https://mdblist.com/api/?apikey=$apiKey&$subParam")
                    }
                }
                return null
            }

            val ratings = root["ratings"]?.jsonArray ?: return null

            var imdb: Float? = null
            var rt: Int? = null
            var meta: Int? = null
            var trakt: Int? = null
            var audience: Int? = null
            var tmdb: Float? = null

            for (node in ratings) {
                val obj = node.jsonObject
                val source = obj["source"]?.jsonPrimitive?.content?.lowercase() ?: continue
                val value = obj["value"]?.jsonPrimitive?.content?.toFloatOrNull() ?: continue
                when (source) {
                    "imdb" -> imdb = value
                    "tomatoes" -> rt = value.toInt()
                    "metacritic" -> meta = value.toInt()
                    "trakt" -> trakt = value.toInt()
                    "tomatoesaudience", "audience", "popcorn" -> audience = value.toInt()
                    "tmdb" -> tmdb = if (value > 10f) value / 10f else value
                }
            }
            MdbRatings(
                imdbScore = imdb,
                rtScore = rt,
                metacriticScore = meta,
                traktScore = trakt,
                audienceScore = audience,
                tmdbScore = tmdb
            )
        }
    }

    private data class SteamDetails(
        val communityScore: Float? = null,
        val metacriticScore: Int? = null
    )

    private suspend fun fetchSteamAppDetails(appId: Long): SteamDetails? = NetworkPacer.withSteamPacing {
        try {
            val url = "https://store.steampowered.com/api/appdetails?appids=$appId"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withSteamPacing null
                val body = response.body?.string() ?: return@withSteamPacing null
                val root = json.parseToJsonElement(body).jsonObject
                val appNode = root[appId.toString()]?.jsonObject
                val success = appNode?.get("success")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                if (!success) return@withSteamPacing null

                val data = appNode["data"]?.jsonObject ?: return@withSteamPacing null
                val metaScore = data["metacritic"]?.jsonObject?.get("score")?.jsonPrimitive?.content?.toIntOrNull()

                SteamDetails(metacriticScore = metaScore)
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchBangumiScore(bangumiId: Long): Float? = NetworkPacer.withBangumiPacing {
        try {
            val url = "https://api.bgm.tv/v0/subjects/$bangumiId"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withBangumiPacing null
                val body = response.body?.string() ?: return@withBangumiPacing null
                val root = json.parseToJsonElement(body).jsonObject
                val score = root["rating"]?.jsonObject?.get("score")?.jsonPrimitive?.content?.toFloatOrNull()
                score
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchCinemetaImdbById(imdbId: String, isMovie: Boolean): Float? {
        return try {
            val cinemetaType = if (isMovie) "movie" else "series"
            val cinemetaUrl = "https://v3-cinemeta.strem.io/meta/$cinemetaType/$imdbId.json"
            val req2 = Request.Builder().url(cinemetaUrl).get().build()
            client.newCall(req2).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = json.parseToJsonElement(body).jsonObject
                val meta = root["meta"]?.jsonObject
                meta?.get("imdbRating")?.jsonPrimitive?.content?.toFloatOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchJikanScore(malId: Long, isManga: Boolean = false): Float? {
        return try {
            val host = "https://jikanfortheweebs.midnightignite.me/v4"
            val path = if (isManga) "manga" else "anime"
            val url = "$host/$path/$malId"
            val req = Request.Builder().url(url).get().build()
            jikanClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = json.parseToJsonElement(body).jsonObject
                root["data"]?.jsonObject?.get("score")?.jsonPrimitive?.content?.toFloatOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchTmdbVote(tmdbId: Long, isMovie: Boolean, tmdbKey: String): Float? {
        return try {
            val apiKey = tmdbKey
            val type = if (isMovie) "movie" else "tv"
            val url = "https://api.themoviedb.org/3/$type/$tmdbId?api_key=$apiKey"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = json.parseToJsonElement(body).jsonObject
                root["vote_average"]?.jsonPrimitive?.content?.toFloatOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    data class ExtraDetails(
        val trailerKey: String? = null,
        val studio: String? = null,
        val runtime: Int? = null,
        val status: String? = null,
        val totalEpisodes: Int? = null,
        val originCountry: String? = null,
        val originalLanguage: String? = null,
        val releaseDate: String? = null,
        val lastAirDate: String? = null,
        val releaseYear: String? = null,
        val contentRating: String? = null,
        val videos: List<MediaTrailer> = emptyList(),
        val belongsToCollectionId: Long? = null,
        val belongsToCollectionName: String? = null,
        val backdropPath: String? = null
    )

    data class TmdbCollectionData(
        val name: String,
        val parts: List<com.omnitrack.app.ui.screens.detail.components.RecommendationItem>
    )

    suspend fun fetchTmdbCollection(collectionId: Long, tmdbKey: String): TmdbCollectionData? = withContext(Dispatchers.IO) {
        NetworkPacer.withTmdbPacing {
            try {
                val url = "https://api.themoviedb.org/3/collection/$collectionId?api_key=$tmdbKey"
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) return@withTmdbPacing null
                    val body = response.body?.string() ?: return@withTmdbPacing null
                    val root = json.parseToJsonElement(body).jsonObject
                    val name = root["name"]?.jsonPrimitive?.content ?: "Collection"
                    val partsArray = root["parts"]?.jsonArray ?: return@withTmdbPacing null
                    val parts = partsArray.mapNotNull { node ->
                        val obj = node.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
                        val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val poster = obj["poster_path"]?.jsonPrimitive?.content
                        val release = obj["release_date"]?.jsonPrimitive?.content
                        val rating = obj["vote_average"]?.jsonPrimitive?.content?.toFloatOrNull()
                        val backdrop = obj["backdrop_path"]?.jsonPrimitive?.content
                        val synopsis = obj["overview"]?.jsonPrimitive?.content
                        com.omnitrack.app.ui.screens.detail.components.RecommendationItem(
                            title = title,
                            coverUrl = if (!poster.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$poster" else "",
                            releaseYear = release?.take(4),
                            rating = rating,
                            mediaType = com.omnitrack.app.data.model.MediaType.MOVIE,
                            synopsis = synopsis,
                            tmdbId = id,
                            bannerUrl = backdrop?.let { "https://image.tmdb.org/t/p/original$it" }
                        )
                    }.sortedBy { it.releaseYear ?: "" }
                    TmdbCollectionData(name = name, parts = parts)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun fetchTmdbDetails(tmdbId: Long, isMovie: Boolean, tmdbKey: String): ExtraDetails? {
        return try {
            val apiKey = tmdbKey
            val type = if (isMovie) "movie" else "tv"
            val append = if (isMovie) "videos,release_dates" else "videos,content_ratings"
            val url = "https://api.themoviedb.org/3/$type/$tmdbId?api_key=$apiKey&append_to_response=$append&include_video_language=en,ja,null"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = json.parseToJsonElement(body).jsonObject

                val runtime = if (isMovie) {
                    root["runtime"]?.jsonPrimitive?.content?.toIntOrNull()
                } else {
                    root["episode_run_time"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content?.toIntOrNull()
                }
                val status = root["status"]?.jsonPrimitive?.content
                val studio = root["production_companies"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.content
                    ?: root["networks"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.content

                val totalEpisodes = if (!isMovie) {
                    root["number_of_episodes"]?.jsonPrimitive?.content?.toIntOrNull()
                } else null

                val originCountry = root["origin_country"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                    ?: root["production_countries"]?.jsonArray?.firstOrNull()?.jsonObject?.get("iso_3166_1")?.jsonPrimitive?.content

                val originalLanguage = root["original_language"]?.jsonPrimitive?.content

                val releaseDate = root["first_air_date"]?.jsonPrimitive?.content
                    ?: root["release_date"]?.jsonPrimitive?.content

                val lastAirDate = root["last_air_date"]?.jsonPrimitive?.content

                // Parse US content rating certification
                val contentRating = if (isMovie) {
                    val releaseDates = root["release_dates"]?.jsonObject?.get("results")?.jsonArray
                    releaseDates?.firstOrNull { it.jsonObject["iso_3166_1"]?.jsonPrimitive?.content == "US" }
                        ?.jsonObject?.get("release_dates")?.jsonArray?.firstNotNullOfOrNull {
                            it.jsonObject["certification"]?.jsonPrimitive?.content?.takeIf { c -> c.isNotBlank() }
                        }
                } else {
                    val contentRatings = root["content_ratings"]?.jsonObject?.get("results")?.jsonArray
                    contentRatings?.firstOrNull { it.jsonObject["iso_3166_1"]?.jsonPrimitive?.content == "US" }
                        ?.jsonObject?.get("rating")?.jsonPrimitive?.content?.takeIf { r -> r.isNotBlank() }
                }

                // Parse rich video items
                val videos = root["videos"]?.jsonObject?.get("results")?.jsonArray
                val videoList = mutableListOf<MediaTrailer>()
                var trailerKey: String? = null
                if (videos != null) {
                    for (v in videos) {
                        val site = v.jsonObject["site"]?.jsonPrimitive?.content
                        val typeStr = v.jsonObject["type"]?.jsonPrimitive?.content ?: "Trailer"
                        val name = v.jsonObject["name"]?.jsonPrimitive?.content ?: "Trailer"
                        val key = v.jsonObject["key"]?.jsonPrimitive?.content
                        if (site == "YouTube" && !key.isNullOrBlank()) {
                            videoList.add(MediaTrailer(title = name, youtubeKey = key, type = typeStr))
                            if (trailerKey == null && (typeStr == "Trailer" || typeStr == "Teaser")) {
                                trailerKey = key
                            }
                        }
                    }
                }
                if (trailerKey == null && videoList.isNotEmpty()) {
                    trailerKey = videoList.first().youtubeKey
                }

                val releaseYear = when {
                    !isMovie && releaseDate != null && lastAirDate != null && status?.lowercase() in listOf("ended", "canceled", "cancelled") -> {
                        val start = releaseDate.take(4)
                        val end = lastAirDate.take(4)
                        if (start == end) start else "$start-$end"
                    }
                    !isMovie && releaseDate != null && status?.lowercase() !in listOf("ended", "canceled", "cancelled") -> {
                        "${releaseDate.take(4)}-"
                    }
                    releaseDate != null -> releaseDate.take(4)
                    else -> null
                }

                val belongsToCollObj = if (isMovie) root["belongs_to_collection"]?.let { if (it is kotlinx.serialization.json.JsonObject) it else null } else null
                val belongsToCollId = belongsToCollObj?.get("id")?.jsonPrimitive?.content?.toLongOrNull()
                val belongsToCollName = belongsToCollObj?.get("name")?.jsonPrimitive?.content
                val backdropPath = root["backdrop_path"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

                ExtraDetails(
                    trailerKey = trailerKey,
                    studio = studio,
                    runtime = runtime,
                    status = status,
                    totalEpisodes = totalEpisodes,
                    originCountry = originCountry,
                    originalLanguage = originalLanguage,
                    releaseDate = releaseDate,
                    lastAirDate = lastAirDate,
                    releaseYear = releaseYear,
                    contentRating = contentRating,
                    videos = videoList,
                    belongsToCollectionId = belongsToCollId,
                    belongsToCollectionName = belongsToCollName,
                    backdropPath = backdropPath
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchCinemetaVideos(imdbId: String): List<com.omnitrack.app.data.model.MediaEpisode> = withContext(Dispatchers.IO) {
        cinemetaVideosCache[imdbId]?.let { return@withContext it }
        NetworkPacer.coalesce("cinemeta_$imdbId") {
            cinemetaVideosCache[imdbId]?.let { return@coalesce it }
            try {
                val url = "https://v3-cinemeta.strem.io/meta/series/$imdbId.json"
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) return@coalesce emptyList()
                    val body = response.body?.string() ?: return@coalesce emptyList()
                    val root = json.parseToJsonElement(body).jsonObject
                    val meta = root["meta"]?.jsonObject ?: return@coalesce emptyList()
                    meta["tvdb_id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.takeIf { it > 0L }?.let {
                        cinemetaTvdbIdCache[imdbId] = it
                    }
                    val videos = meta["videos"]?.jsonArray ?: return@coalesce emptyList()
                    val result = videos.mapNotNull { node ->
                        val obj = node.jsonObject
                        val sNum = obj["season"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                        val epNum = obj["episode"]?.jsonPrimitive?.content?.toIntOrNull()
                            ?: obj["number"]?.jsonPrimitive?.content?.toIntOrNull()
                            ?: return@mapNotNull null
                        val title = obj["name"]?.jsonPrimitive?.content
                            ?: obj["title"]?.jsonPrimitive?.content
                            ?: "Episode $epNum"
                        val desc = obj["overview"]?.jsonPrimitive?.content
                            ?: obj["description"]?.jsonPrimitive?.content
                        val thumb = obj["thumbnail"]?.jsonPrimitive?.content
                        val rel = obj["released"]?.jsonPrimitive?.content
                            ?: obj["firstAired"]?.jsonPrimitive?.content
                        val dateOnly = rel?.take(10)
                        val rawRating = obj["rating"]?.jsonPrimitive?.content?.toFloatOrNull()
                        val voteAvg = if (rawRating != null && rawRating > 0f) rawRating else null

                        com.omnitrack.app.data.model.MediaEpisode(
                            id = sNum * 1000L + epNum,
                            episodeNumber = epNum,
                            seasonNumber = sNum,
                            title = title,
                            overview = desc,
                            stillUrl = thumb,
                            airDate = dateOnly,
                            runtimeMinutes = null,
                            voteAverage = voteAvg,
                            ratingSource = if (voteAvg != null) "Cinemeta" else null
                        )
                    }
                    if (result.isNotEmpty()) {
                        cinemetaVideosCache[imdbId] = result
                    }
                    result
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emptyList()
            }
        }
    }

    private suspend fun fetchSkyHookShow(tvdbId: Long): SkyHookShowData? = withContext(Dispatchers.IO) {
        if (tvdbId <= 0L) return@withContext null
        skyHookShowCache[tvdbId]?.let { return@withContext it }
        NetworkPacer.coalesce("skyhook_show_$tvdbId") {
            skyHookShowCache[tvdbId]?.let { return@coalesce it }
            try {
                val url = "https://skyhook.sonarr.tv/v1/tvdb/shows/en/$tvdbId"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "OmniTrack/1.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@coalesce null
                    val body = resp.body?.string() ?: return@coalesce null
                    val root = json.parseToJsonElement(body).jsonObject

                    val seasonPosters = mutableMapOf<Int, String>()
                    root["seasons"]?.jsonArray?.forEach { node ->
                        val obj = node.jsonObject
                        val sNum = obj["seasonNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                            ?: return@forEach
                        val posterUrl = obj["images"]?.jsonArray?.firstNotNullOfOrNull { imgNode ->
                            val imgObj = imgNode.jsonObject
                            val coverType = imgObj["coverType"].cleanString()
                            if (coverType.equals("Poster", ignoreCase = true)) {
                                imgObj["url"].cleanString()
                            } else null
                        }
                        if (!posterUrl.isNullOrBlank()) {
                            seasonPosters[sNum] = posterUrl
                        }
                    }

                    val episodesBySeason = mutableMapOf<Int, MutableList<com.omnitrack.app.data.model.MediaEpisode>>()
                    root["episodes"]?.jsonArray?.forEach { node ->
                        val obj = node.jsonObject
                        val sNum = obj["seasonNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                            ?: return@forEach
                        val epNum = obj["episodeNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                            ?: return@forEach
                        val title = obj["title"].cleanString() ?: "Episode $epNum"
                        val overview = obj["overview"].cleanString()
                        val stillUrl = obj["image"].cleanString()
                        val airDate = obj["airDate"].cleanString()?.take(10)
                        val runtime = obj["runtime"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        val list = episodesBySeason.getOrPut(sNum) { mutableListOf() }
                        list.add(
                            com.omnitrack.app.data.model.MediaEpisode(
                                id = sNum * 1000L + epNum,
                                episodeNumber = epNum,
                                seasonNumber = sNum,
                                title = title,
                                overview = overview,
                                stillUrl = stillUrl,
                                airDate = airDate,
                                runtimeMinutes = runtime
                            )
                        )
                    }
                    val sortedEpisodes = episodesBySeason.mapValues { (_, list) ->
                        list.sortedBy { it.episodeNumber }
                    }
                    val data = SkyHookShowData(
                        tvdbId = tvdbId,
                        seasonPosters = seasonPosters,
                        episodesBySeason = sortedEpisodes
                    )
                    if (seasonPosters.isNotEmpty() || sortedEpisodes.isNotEmpty()) {
                        skyHookShowCache[tvdbId] = data
                    }
                    data
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }
    }

    private suspend fun fetchTmdbRawSeasonMeta(tmdbId: Long, apiKey: String): List<TmdbRawSeasonMeta> {
        tmdbSeasonMetaCache[tmdbId]?.let { return it }
        return NetworkPacer.coalesce("tmdb_tv_meta_$tmdbId") {
            tmdbSeasonMetaCache[tmdbId]?.let { return@coalesce it }
            NetworkPacer.withTmdbPacing {
                try {
                    val url = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$apiKey"
                    val req = Request.Builder().url(url).get().build()
                    client.newCall(req).execute().use { response ->
                        if (!response.isSuccessful) return@withTmdbPacing emptyList()
                        val body = response.body?.string() ?: return@withTmdbPacing emptyList()
                        val root = json.parseToJsonElement(body).jsonObject
                        val seasons = root["seasons"]?.jsonArray ?: return@withTmdbPacing emptyList()
                        val parsed = seasons.mapNotNull { node ->
                            val obj = node.jsonObject
                            val seasonNumber = obj["season_number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                ?: return@mapNotNull null
                            val rawName = obj["name"].cleanString().orEmpty()
                            val epCount = obj["episode_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                            val posterPath = obj["poster_path"].cleanString()
                            val airDate = obj["air_date"].cleanString()
                            TmdbRawSeasonMeta(
                                seasonNumber = seasonNumber,
                                rawName = rawName,
                                episodeCount = epCount,
                                posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                                airDate = airDate
                            )
                        }
                        if (parsed.isNotEmpty()) {
                            tmdbSeasonMetaCache[tmdbId] = parsed
                        }
                        parsed
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
            }
        }
    }

    private suspend fun fetchTmdbRawSeasonEpisodes(
        tmdbId: Long,
        rawSeasonNumber: Int,
        apiKey: String
    ): List<com.omnitrack.app.data.model.MediaEpisode> {
        val cacheKey = "$tmdbId:$rawSeasonNumber"
        tmdbSeasonRawEpisodesCache[cacheKey]?.let { return it }
        return NetworkPacer.coalesce("tmdb_raw_season_${tmdbId}_$rawSeasonNumber") {
            tmdbSeasonRawEpisodesCache[cacheKey]?.let { return@coalesce it }
            NetworkPacer.withTmdbPacing {
                try {
                    val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$rawSeasonNumber?api_key=$apiKey"
                    val req = Request.Builder().url(url).get().build()
                    client.newCall(req).execute().use { response ->
                        if (!response.isSuccessful) return@withTmdbPacing emptyList()
                        val body = response.body?.string() ?: return@withTmdbPacing emptyList()
                        val root = json.parseToJsonElement(body).jsonObject
                        val episodes = root["episodes"]?.jsonArray ?: return@withTmdbPacing emptyList()
                        val parsed = episodes.mapNotNull { node ->
                            val obj = node.jsonObject
                            val epNumber = obj["episode_number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                ?: return@mapNotNull null
                            val id = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                ?: (rawSeasonNumber * 1000L + epNumber)
                            val name = obj["name"].cleanString() ?: "Episode $epNumber"
                            val overview = obj["overview"].cleanString()
                            val stillPath = obj["still_path"].cleanString()
                            val airDate = obj["air_date"].cleanString()
                            val runtime = obj["runtime"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                            val rawVoteAvg = obj["vote_average"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                            val voteCount = obj["vote_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                            val trustedVote = rawVoteAvg?.takeIf {
                                it > 0f && (voteCount == null || voteCount >= 5)
                            }
                            com.omnitrack.app.data.model.MediaEpisode(
                                id = id,
                                episodeNumber = epNumber,
                                seasonNumber = rawSeasonNumber,
                                title = name,
                                overview = overview,
                                stillUrl = stillPath?.let { "https://image.tmdb.org/t/p/w780$it" },
                                airDate = airDate,
                                runtimeMinutes = runtime,
                                voteAverage = trustedVote,
                                voteCount = voteCount,
                                ratingSource = if (trustedVote != null) "TMDb" else null
                            )
                        }.sortedBy { it.episodeNumber }
                        if (parsed.isNotEmpty()) {
                            tmdbSeasonRawEpisodesCache[cacheKey] = parsed
                            if (rawSeasonNumber == 1) {
                                tmdbSeason1StillsCache[tmdbId] = parsed
                            }
                        }
                        parsed
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
            }
        }
    }

    private fun findMatchedSubSeason(rawSeasons: List<TmdbRawSeasonMeta>, showTitle: String?): TmdbRawSeasonMeta? {
        if (showTitle.isNullOrBlank()) return null
        return rawSeasons.firstOrNull { s ->
            s.seasonNumber >= 2 &&
                s.rawName.length >= 5 &&
                !s.rawName.startsWith("Season", ignoreCase = true) &&
                showTitle.contains(s.rawName, ignoreCase = true)
        }
    }

    private fun splitEpisodesIntoCours(rawEps: List<com.omnitrack.app.data.model.MediaEpisode>): List<List<com.omnitrack.app.data.model.MediaEpisode>> {
        if (rawEps.isEmpty()) return emptyList()
        val sorted = rawEps.sortedBy { it.episodeNumber }
        val cours = mutableListOf<MutableList<com.omnitrack.app.data.model.MediaEpisode>>()
        var current = mutableListOf<com.omnitrack.app.data.model.MediaEpisode>()
        var prevDate: java.time.LocalDate? = null
        for (ep in sorted) {
            val curDate = ep.airDate?.take(10)?.let {
                runCatching { java.time.LocalDate.parse(it) }.getOrNull()
            }
            if (prevDate != null && curDate != null && java.time.temporal.ChronoUnit.DAYS.between(prevDate, curDate) > 90L && current.isNotEmpty()) {
                cours.add(current)
                current = mutableListOf()
            }
            current.add(ep)
            if (curDate != null) prevDate = curDate
        }
        if (current.isNotEmpty()) cours.add(current)
        return cours
    }

    private suspend fun fetchAniListSubSeriesCours(subSeriesTitle: String): List<Pair<Long, String?>> = withContext(Dispatchers.IO) {
        val normKey = subSeriesTitle.lowercase().trim()
        aniListSubSeriesCoursCache[normKey]?.let { return@withContext it }
        NetworkPacer.coalesce("anilist_subseries_$normKey") {
            aniListSubSeriesCoursCache[normKey]?.let { return@coalesce it }
            NetworkPacer.withAniListPacing {
                try {
                    val query = """
                        query (${'$'}search: String) {
                          Page(page: 1, perPage: 10) {
                            media(search: ${'$'}search, type: ANIME, format: TV) {
                              id
                              title { romaji english }
                              startDate { year month }
                              coverImage { extraLarge large }
                            }
                          }
                        }
                    """.trimIndent()
                    val jsonPayload = buildJsonObject {
                        put("query", query)
                        putJsonObject("variables") { put("search", subSeriesTitle.trim()) }
                    }.toString()
                    val req = Request.Builder()
                        .url("https://graphql.anilist.co")
                        .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                        .header("User-Agent", "OmniTrack/1.0")
                        .build()
                    val subPart = subSeriesTitle.substringAfter(":", "").substringBefore(" - ").trim().lowercase()
                    data class CourEntry(val id: Long, val year: Int, val month: Int, val posterUrl: String?)
                    val entries = client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withAniListPacing emptyList<Pair<Long, String?>>()
                        val body = resp.body?.string() ?: return@withAniListPacing emptyList<Pair<Long, String?>>()
                        val root = json.parseToJsonElement(body).jsonObject
                        val mediaList = root["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray.orEmpty()
                        mediaList.mapNotNull { elem ->
                            val obj = elem.jsonObject
                            val id = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
                            val tObj = obj["title"]?.jsonObject
                            val eng = tObj?.get("english").cleanString()
                            val rom = tObj?.get("romaji").cleanString()
                            val matchesPrefix = eng?.lowercase()?.startsWith(normKey) == true ||
                                rom?.lowercase()?.startsWith(normKey) == true ||
                                (subPart.length >= 5 && (eng?.lowercase()?.contains(subPart) == true || rom?.lowercase()?.contains(subPart) == true))
                            if (!matchesPrefix) return@mapNotNull null
                            val year = obj["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: Int.MAX_VALUE
                            val month = obj["startDate"]?.jsonObject?.get("month")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: Int.MAX_VALUE
                            val coverObj = obj["coverImage"]?.jsonObject
                            val poster = coverObj?.get("extraLarge").cleanString() ?: coverObj?.get("large").cleanString()
                            CourEntry(id, year, month, poster)
                        }.sortedWith(compareBy<CourEntry> { it.year }.thenBy { it.month }.thenBy { it.id })
                            .map { it.id to it.posterUrl }
                    }
                    if (entries.isNotEmpty()) {
                        aniListSubSeriesCoursCache[normKey] = entries
                    }
                    entries
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
            }
        }
    }

    private fun isAirDateWithinDays(dateA: String?, dateB: String?, maxDays: Long): Boolean {
        if (dateA.isNullOrBlank() || dateB.isNullOrBlank()) return false
        val parsedA = runCatching { java.time.LocalDate.parse(dateA.trim().take(10)) }.getOrNull() ?: return false
        val parsedB = runCatching { java.time.LocalDate.parse(dateB.trim().take(10)) }.getOrNull() ?: return false
        return kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(parsedA, parsedB)) <= maxDays
    }

    private suspend fun resolveTvdbId(
        tmdbId: Long,
        apiKey: String,
        imdbId: String? = null,
        anilistId: Long? = null
    ): Long? {
        tmdbTvdbIdCache[tmdbId]?.takeIf { it > 0L }?.let { return it }
        imdbId?.let { cinemetaTvdbIdCache[it] }?.takeIf { it > 0L }?.let {
            tmdbTvdbIdCache[tmdbId] = it
            return it
        }
        if (anilistId != null && anilistId > 0L) {
            fetchAniZipTvdbId(anilistId)?.takeIf { it > 0L }?.let {
                tmdbTvdbIdCache[tmdbId] = it
                return it
            }
        }
        fetchTmdbImdbId(tmdbId, false, apiKey)
        tmdbTvdbIdCache[tmdbId]?.takeIf { it > 0L }?.let { return it }
        return null
    }

    private suspend fun fetchBestTmdbAnimeEpisodeGroup(
        tmdbId: Long,
        apiKey: String
    ): List<TmdbEpisodeGroupSeason> {
        tmdbAnimeEpisodeGroupCache[tmdbId]?.let { return it }
        return NetworkPacer.coalesce("tmdb_anime_eg_$tmdbId") {
            tmdbAnimeEpisodeGroupCache[tmdbId]?.let { return@coalesce it }
            val result = NetworkPacer.withTmdbPacing {
                try {
                    val listUrl = "https://api.themoviedb.org/3/tv/$tmdbId/episode_groups?api_key=$apiKey"
                    val listReq = Request.Builder().url(listUrl).get().build()
                    val bestGroupId = client.newCall(listReq).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val body = resp.body?.string() ?: return@use null
                        val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray
                            ?: return@use null
                        data class CandidateGroup(val id: String, val rank: Int, val groupCount: Int)
                        val rejectKeywords = listOf(
                            "arc", "saga", "filler", "chronolog", "dvd", "blu-ray", "bluray",
                            "absolute", "production", "netflix", "order"
                        )
                        results.mapNotNull { node ->
                            val obj = node.jsonObject
                            val id = obj["id"].cleanString() ?: return@mapNotNull null
                            val name = obj["name"].cleanString().orEmpty()
                            val lower = name.lowercase()
                            val type = obj["type"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                            val gCount = obj["group_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                            val epCount = obj["episode_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                            if (gCount !in 2..20 || epCount < 12) return@mapNotNull null
                            if (rejectKeywords.any { lower.contains(it) }) return@mapNotNull null
                            val rank = when {
                                type == 7 || lower.contains("season") -> 0
                                type == 6 || lower.contains("cour") -> 1
                                type == 1 || lower.contains("wikipedia") -> 2
                                else -> return@mapNotNull null
                            }
                            CandidateGroup(id, rank, gCount)
                        }.sortedWith(compareBy<CandidateGroup> { it.rank }.thenByDescending { it.groupCount })
                            .firstOrNull()?.id
                    } ?: return@withTmdbPacing emptyList()

                    val detailUrl = "https://api.themoviedb.org/3/tv/episode_group/$bestGroupId?api_key=$apiKey"
                    val detailReq = Request.Builder().url(detailUrl).get().build()
                    client.newCall(detailReq).execute().use { resp ->
                        if (!resp.isSuccessful) return@use emptyList()
                        val body = resp.body?.string() ?: return@use emptyList()
                        val groupsArr = json.parseToJsonElement(body).jsonObject["groups"]?.jsonArray
                            ?: return@use emptyList()
                        val sortedGroups = groupsArr.mapNotNull { node ->
                            val obj = node as? JsonObject ?: return@mapNotNull null
                            val order = obj["order"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: Int.MAX_VALUE
                            order to obj
                        }.sortedBy { it.first }.map { it.second }

                        val nonAsciiOrGenericSeasonRegex = Regex("(?i)^(?:cour|season|part)\\s*\\d+$")
                        val out = mutableListOf<TmdbEpisodeGroupSeason>()
                        sortedGroups.forEachIndexed { idx, grpObj ->
                            val rawEps = grpObj["episodes"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
                            if (rawEps.isEmpty()) return@forEachIndexed
                            val regularEps = rawEps.filter { ep ->
                                (ep["season_number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1) != 0
                            }.ifEmpty { rawEps }

                            val sNum = out.size + 1
                            val rawName = grpObj["name"].cleanString().orEmpty()
                            val hasNonAscii = rawName.any { it.code > 127 }
                            val seasonName = if (rawName.isBlank() || hasNonAscii || nonAsciiOrGenericSeasonRegex.matches(rawName)) {
                                "Season $sNum"
                            } else {
                                rawName
                            }
                            val mappedEps = regularEps.mapIndexedNotNull { epIdx, obj ->
                                val epNum = epIdx + 1
                                val id = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                    ?: (sNum * 1000L + epNum)
                                val title = obj["name"].cleanString() ?: "Episode $epNum"
                                val overview = obj["overview"].cleanString()
                                val stillPath = obj["still_path"].cleanString()
                                val airDate = obj["air_date"].cleanString()?.take(10)
                                val runtime = obj["runtime"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val rawVote = obj["vote_average"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                                val voteCount = obj["vote_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val trustedVote = rawVote?.takeIf { it > 0f && (voteCount == null || voteCount >= 5) }
                                com.omnitrack.app.data.model.MediaEpisode(
                                    id = id,
                                    episodeNumber = epNum,
                                    seasonNumber = sNum,
                                    title = title,
                                    overview = overview,
                                    stillUrl = stillPath?.let { "https://image.tmdb.org/t/p/w780$it" },
                                    airDate = airDate,
                                    runtimeMinutes = runtime,
                                    voteAverage = trustedVote,
                                    voteCount = voteCount,
                                    ratingSource = if (trustedVote != null) "TMDb" else null
                                )
                            }
                            if (mappedEps.isNotEmpty()) {
                                out.add(
                                    TmdbEpisodeGroupSeason(
                                        seasonNumber = sNum,
                                        name = seasonName,
                                        airDate = mappedEps.firstOrNull()?.airDate,
                                        episodes = mappedEps
                                    )
                                )
                            }
                        }
                        out
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
            }
            tmdbAnimeEpisodeGroupCache[tmdbId] = result
            result
        }
    }

    private suspend fun fetchOmdbSeasonRatings(
        imdbId: String,
        seasonNumber: Int,
        apiKey: String
    ): List<ExternalEpisodeRating> {
        val cacheKey = "${imdbId.lowercase()}_s$seasonNumber"
        omdbSeasonRatingsCache[cacheKey]?.let { return it }
        return NetworkPacer.coalesce("omdb_ep_$cacheKey") {
            omdbSeasonRatingsCache[cacheKey]?.let { return@coalesce it }
            val result = NetworkPacer.withGeneralPacing {
                try {
                    val url = "https://www.omdbapi.com/?i=${URLEncoder.encode(imdbId, "UTF-8")}&Season=$seasonNumber&apikey=${URLEncoder.encode(apiKey, "UTF-8")}"
                    val req = Request.Builder().url(url).get().build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                        val body = resp.body?.string() ?: return@withGeneralPacing emptyList()
                        val root = json.parseToJsonElement(body).jsonObject
                        if (!root["Response"].cleanString().equals("True", ignoreCase = true)) {
                            return@withGeneralPacing emptyList()
                        }
                        root["Episodes"]?.jsonArray.orEmpty().mapNotNull { node ->
                            val o = node as? JsonObject ?: return@mapNotNull null
                            val epNum = o["Episode"].cleanString()?.toIntOrNull() ?: return@mapNotNull null
                            val epTitle = o["Title"].cleanString().orEmpty()
                            val released = o["Released"].cleanString()
                                ?.takeUnless { it.equals("N/A", ignoreCase = true) }
                                ?.take(10)
                            val rating = o["imdbRating"].cleanString()
                                ?.takeUnless { it.equals("N/A", ignoreCase = true) }
                                ?.toFloatOrNull()
                                ?.takeIf { it > 0f }
                                ?: return@mapNotNull null
                            ExternalEpisodeRating(
                                season = seasonNumber,
                                episode = epNum,
                                title = epTitle,
                                airDate = released,
                                rating = kotlin.math.round(rating * 10f) / 10f,
                                votes = null,
                                source = "IMDb"
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
            }
            omdbSeasonRatingsCache[cacheKey] = result
            result
        }
    }

    private suspend fun fetchTraktSeasonRatings(
        imdbId: String,
        seasonNumber: Int
    ): List<ExternalEpisodeRating> {
        val cacheKey = "${imdbId.lowercase()}_s$seasonNumber"
        traktSeasonRatingsCache[cacheKey]?.let { return it }
        return NetworkPacer.coalesce("trakt_ep_$cacheKey") {
            traktSeasonRatingsCache[cacheKey]?.let { return@coalesce it }
            val result = NetworkPacer.withGeneralPacing {
                try {
                    val url = "https://api.trakt.tv/shows/${URLEncoder.encode(imdbId, "UTF-8")}/seasons/$seasonNumber?extended=full"
                    val req = Request.Builder()
                        .url(url)
                        .header("Content-Type", "application/json")
                        .header("trakt-api-version", "2")
                        .header("trakt-api-key", com.omnitrack.app.data.sync.TrackerAccountVerifier.TRAKT_CLIENT_ID)
                        .header("User-Agent", "OmniTrack/1.0")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                        val body = resp.body?.string() ?: return@withGeneralPacing emptyList()
                        val arr = json.parseToJsonElement(body).jsonArray
                        arr.mapNotNull { node ->
                            val o = node as? JsonObject ?: return@mapNotNull null
                            val epNum = o["number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
                            val sNum = o["season"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: seasonNumber
                            val epTitle = o["title"].cleanString().orEmpty()
                            val airDate = o["first_aired"].cleanString()?.take(10)
                            val rawRating = o["rating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                                ?.takeIf { it > 0f }
                                ?: return@mapNotNull null
                            val votes = o["votes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                            if (votes < 5) return@mapNotNull null
                            ExternalEpisodeRating(
                                season = sNum,
                                episode = epNum,
                                title = epTitle,
                                airDate = airDate,
                                rating = kotlin.math.round(rawRating * 10f) / 10f,
                                votes = votes,
                                source = "Trakt"
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
            }
            traktSeasonRatingsCache[cacheKey] = result
            result
        }
    }

    suspend fun enrichEpisodesWithAccurateRatings(
        episodes: List<com.omnitrack.app.data.model.MediaEpisode>,
        imdbId: String?,
        seasonNumber: Int
    ): List<com.omnitrack.app.data.model.MediaEpisode> = withContext(Dispatchers.IO) {
        if (episodes.isEmpty()) return@withContext episodes
        val cleanImdb = imdbId?.trim()?.takeIf { it.isNotBlank() }

        fun cleanFallbackEp(ep: com.omnitrack.app.data.model.MediaEpisode): com.omnitrack.app.data.model.MediaEpisode {
            // Drop noisy 1-4 voter TMDb outliers (e.g. 6.0 / 6.5 on anime episodes) unless corroborated
            val isUnreliableTmdb = ep.ratingSource == "TMDb" && (ep.voteCount ?: 0) < 10
            val isUnreliableCinemeta = ep.ratingSource == "Cinemeta"
            return if (isUnreliableTmdb || isUnreliableCinemeta) {
                ep.copy(voteAverage = null, ratingSource = null)
            } else {
                ep
            }
        }

        if (cleanImdb == null || seasonNumber < 0) {
            return@withContext episodes.map(::cleanFallbackEp)
        }

        fun normTitle(raw: String): String = raw.lowercase().replace(Regex("[^a-z0-9]"), "")

        fun findMatch(
            ep: com.omnitrack.app.data.model.MediaEpisode,
            pool: List<ExternalEpisodeRating>,
            allowExactNumberMatch: Boolean
        ): ExternalEpisodeRating? {
            if (pool.isEmpty()) return null
            val epTitleNorm = normTitle(ep.title)
            if (epTitleNorm.length >= 3) {
                pool.firstOrNull { normTitle(it.title) == epTitleNorm }?.let { return it }
            }
            pool.firstOrNull { isAirDateWithinDays(ep.airDate, it.airDate, 2L) }?.let { return it }
            if (allowExactNumberMatch) {
                pool.firstOrNull { it.season == ep.seasonNumber && it.episode == ep.episodeNumber }?.let { return it }
            }
            return null
        }

        val omdbKey = PreferenceManager.cachedOmdbApiKey.trim().takeIf { it.isNotBlank() }
        val omdbDirect = if (omdbKey != null) fetchOmdbSeasonRatings(cleanImdb, seasonNumber, omdbKey) else emptyList()
        val omdbHasContentMatch = omdbDirect.isNotEmpty() && episodes.any { findMatch(it, omdbDirect, allowExactNumberMatch = false) != null }
        val omdbPool: List<ExternalEpisodeRating> = when {
            omdbKey == null -> emptyList()
            omdbHasContentMatch -> omdbDirect
            else -> {
                val collected = mutableListOf<ExternalEpisodeRating>()
                if (omdbDirect.isNotEmpty()) collected.addAll(omdbDirect)
                // Multi-cour anime splits may map OmniTrack S2..S5 across OMDb/IMDb S1..S4; check up to 5 seasons
                for (sIdx in 1..5) {
                    if (sIdx == seasonNumber) continue
                    val list = fetchOmdbSeasonRatings(cleanImdb, sIdx, omdbKey)
                    if (list.isEmpty()) break
                    collected.addAll(list)
                }
                collected
            }
        }
        val omdbAllowExact = omdbDirect.isNotEmpty() && (omdbHasContentMatch || omdbPool.size == omdbDirect.size && seasonNumber == 1)

        // Zero-key high-accuracy Trakt season ratings (always queried when OMDb lacks IMDb coverage)
        val needTrakt = episodes.any { findMatch(it, omdbPool, allowExactNumberMatch = omdbAllowExact) == null }
        val traktDirect = if (needTrakt) fetchTraktSeasonRatings(cleanImdb, seasonNumber) else emptyList()
        val traktHasContentMatch = traktDirect.isNotEmpty() && episodes.any { findMatch(it, traktDirect, allowExactNumberMatch = false) != null }
        val traktPool: List<ExternalEpisodeRating> = when {
            !needTrakt -> emptyList()
            traktHasContentMatch -> traktDirect
            seasonNumber > 1 -> {
                val traktS1 = fetchTraktSeasonRatings(cleanImdb, 1)
                (traktDirect + traktS1)
            }
            else -> traktDirect
        }
        val traktAllowExact = traktDirect.isNotEmpty() && (traktHasContentMatch || (seasonNumber == 1 && traktDirect.size <= episodes.size + 2))

        episodes.map { ep ->
            val omdbMatch = findMatch(ep, omdbPool, allowExactNumberMatch = omdbAllowExact)
            if (omdbMatch != null) {
                ep.copy(
                    voteAverage = omdbMatch.rating,
                    voteCount = omdbMatch.votes ?: ep.voteCount,
                    ratingSource = "IMDb"
                )
            } else {
                val traktMatch = findMatch(ep, traktPool, allowExactNumberMatch = traktAllowExact)
                if (traktMatch != null) {
                    ep.copy(
                        voteAverage = traktMatch.rating,
                        voteCount = traktMatch.votes ?: ep.voteCount,
                        ratingSource = "Trakt"
                    )
                } else {
                    cleanFallbackEp(ep)
                }
            }
        }
    }

    suspend fun fetchTvSeasons(
        tmdbId: Long,
        apiKey: String,
        showTitle: String? = null,
        imdbId: String? = null,
        anilistId: Long? = null
    ): List<com.omnitrack.app.data.model.MediaSeason> {
        if (anilistId != null && anilistId > 0) {
            val azMap = fetchAniZipMapping(anilistId)
            if (azMap?.isMovie == true) return emptyList()
            if (!isTmdbTvCompatibleWithAnime(tmdbId, apiKey, showTitle)) return emptyList()
        }
        val rawSeasons = fetchTmdbRawSeasonMeta(tmdbId, apiKey)
        val tmdbSeasons = rawSeasons.mapNotNull { meta ->
            if (meta.seasonNumber == 0 && meta.episodeCount <= 0) return@mapNotNull null
            com.omnitrack.app.data.model.MediaSeason(
                seasonNumber = meta.seasonNumber,
                name = if (meta.seasonNumber <= 0) "Specials" else "Season ${meta.seasonNumber}",
                episodeCount = meta.episodeCount,
                posterUrl = meta.posterUrl,
                airDate = meta.airDate
            )
        }

        // Sub-Series Detection (e.g. "BLEACH: Thousand-Year Blood War" matching TMDb Season 2 on umbrella tmdbId=30984)
        val matchedSubSeason = findMatchedSubSeason(rawSeasons, showTitle)
        if (matchedSubSeason != null && !showTitle.isNullOrBlank()) {
            val (rawEps, aniListCours) = coroutineScope {
                val epsDeferred = async { fetchTmdbRawSeasonEpisodes(tmdbId, matchedSubSeason.seasonNumber, apiKey) }
                val coursDeferred = async { fetchAniListSubSeriesCours(showTitle) }
                epsDeferred.await() to coursDeferred.await()
            }
            val cours = splitEpisodesIntoCours(rawEps)
            if (cours.isNotEmpty()) {
                return cours.mapIndexed { idx, courEps ->
                    val courNum = idx + 1
                    com.omnitrack.app.data.model.MediaSeason(
                        seasonNumber = courNum,
                        name = "Season $courNum",
                        episodeCount = courEps.size,
                        posterUrl = aniListCours.getOrNull(idx)?.second ?: matchedSubSeason.posterUrl,
                        airDate = courEps.firstOrNull()?.airDate
                    )
                }
            }
        }

        // Fast path: if TMDb returned multiple regular seasons with episodes (e.g. standard multi-season TV shows like Breaking Bad), return immediately.
        // If TMDb only returned <= 1 regular season or Season 1 has > 26 episodes (lumped anime runs like Bungo Stray Dogs / Bleach / The Apothecary Diaries),
        // consult TMDb Episode Groups (Seasons / TV Cours) and Cinemeta + TVDB!
        val hasMultiSeasonTmdb = tmdbSeasons.count { it.seasonNumber > 0 && it.episodeCount > 0 } > 1
        val hasLumpedSeason1 = tmdbSeasons.any { it.seasonNumber == 1 && it.episodeCount > 26 }
        if (hasMultiSeasonTmdb && !hasLumpedSeason1) {
            return tmdbSeasons
                .distinctBy { it.seasonNumber }
                .sortedWith(compareBy({ it.seasonNumber == 0 }, { it.seasonNumber }))
        }

        val effectiveImdbId = imdbId ?: fetchTmdbImdbId(tmdbId, false, apiKey)
        val isAnimeLookup = anilistId != null && anilistId > 0L
        val (epGroupSeasons, tvdbId, cinemetaVideos) = coroutineScope {
            val egDeferred = async {
                if (isAnimeLookup) fetchBestTmdbAnimeEpisodeGroup(tmdbId, apiKey) else emptyList()
            }
            val tvdbDeferred = async { resolveTvdbId(tmdbId, apiKey, effectiveImdbId, anilistId) }
            val cmDeferred = async {
                if (!effectiveImdbId.isNullOrBlank()) fetchCinemetaVideos(effectiveImdbId) else emptyList()
            }
            Triple(egDeferred.await(), tvdbDeferred.await(), cmDeferred.await())
        }
        val skyHook = if (tvdbId != null && tvdbId > 0L) fetchSkyHookShow(tvdbId) else null
        val tmdbSeason1Poster = tmdbSeasons.find { it.seasonNumber == 1 }?.posterUrl
        val tmdbRegularCount = tmdbSeasons.count { it.seasonNumber > 0 && it.episodeCount > 0 }
        val cinemetaRegularCount = cinemetaVideos.map { it.seasonNumber }.filter { it > 0 }.distinct().size

        // Prefer curated TMDb "Seasons"/"TV Cours" episode groups when Cinemeta/TVDB still collapses multi-cour runs (e.g. Bungo Stray Dogs 3 -> 5 seasons)
        if (epGroupSeasons.size >= 2 && epGroupSeasons.size > maxOf(cinemetaRegularCount, tmdbRegularCount)) {
            val postersBySeason: Map<Int, String?> = if (!showTitle.isNullOrBlank()) {
                coroutineScope {
                    epGroupSeasons.map { s ->
                        async {
                            val poster = skyHook?.seasonPosters?.get(s.seasonNumber)?.takeIf { epGroupSeasons.size == cinemetaRegularCount }
                                ?: fetchAniListSeasonPoster(showTitle, s.seasonNumber)
                                ?: fetchKitsuSeasonData(showTitle, s.seasonNumber)?.posterUrl
                                ?: skyHook?.seasonPosters?.get(s.seasonNumber)
                                ?: tmdbSeason1Poster
                            s.seasonNumber to poster
                        }
                    }.awaitAll().toMap()
                }
            } else emptyMap()

            val splitSeasons = epGroupSeasons.map { grp ->
                com.omnitrack.app.data.model.MediaSeason(
                    seasonNumber = grp.seasonNumber,
                    name = grp.name,
                    episodeCount = grp.episodes.size,
                    posterUrl = postersBySeason[grp.seasonNumber] ?: tmdbSeason1Poster,
                    airDate = grp.airDate
                )
            }.toMutableList()
            tmdbSeasons.find { it.seasonNumber == 0 && it.episodeCount > 0 }?.let { specials ->
                splitSeasons.add(specials)
            }
            return splitSeasons
                .distinctBy { it.seasonNumber }
                .sortedWith(compareBy({ it.seasonNumber == 0 }, { it.seasonNumber }))
        }

        // Cinemeta + TVDB (SkyHook) merge for multi-season anime (e.g. Frieren S2, Bleach S1-16, Apothecary Diaries S2/3)
        if (!effectiveImdbId.isNullOrBlank()) {
            if (cinemetaVideos.isNotEmpty()) {
                val cinemetaSeasons = cinemetaVideos.groupBy { it.seasonNumber }
                val mergedList = tmdbSeasons.toMutableList()

                // Parallel AniList/Kitsu poster discovery for any seasons still missing a SkyHook TVDB poster (up to 20 seasons)
                val seasonsNeedingPosters = cinemetaSeasons.keys.filter { sNum ->
                    sNum > 0 &&
                        mergedList.find { it.seasonNumber == sNum }?.posterUrl.isNullOrBlank() &&
                        skyHook?.seasonPosters?.get(sNum).isNullOrBlank()
                }
                val seasonPosters: Map<Int, String?> = if (!showTitle.isNullOrBlank() && seasonsNeedingPosters.isNotEmpty() && seasonsNeedingPosters.size <= 20) {
                    coroutineScope {
                        seasonsNeedingPosters.map { sNum ->
                            async {
                                val poster = fetchAniListSeasonPoster(showTitle, sNum)
                                    ?: fetchKitsuSeasonData(showTitle, sNum)?.posterUrl
                                sNum to poster
                            }
                        }.awaitAll().toMap()
                    }
                } else emptyMap()

                cinemetaSeasons.forEach { (sNum, eps) ->
                    // 1. Sanity check: Discard empty or unreleased dummy phantom seasons
                    val isPhantom = eps.isEmpty() || (
                        eps.size <= 1 &&
                        eps.all { it.airDate.isNullOrBlank() } &&
                        (eps.firstOrNull()?.title.isNullOrBlank() || eps.firstOrNull()?.title.equals("Episode 1", ignoreCase = true))
                    )
                    if (isPhantom) return@forEach

                    // 2. Specials (sNum <= 0) Sanity Check
                    if (sNum <= 0) {
                        val tmdbHasSpecials = tmdbSeasons.any { it.seasonNumber == 0 && it.episodeCount > 0 }
                        val isPromoOnly = eps.all { ep ->
                            val lower = ep.title.lowercase()
                            lower.contains("interview") || lower.contains("breakdown") || lower.contains("screening") || lower.contains("commemorative")
                        }
                        if (!tmdbHasSpecials && (isPromoOnly || eps.all { it.airDate.isNullOrBlank() })) return@forEach
                    }

                    val existing = mergedList.find { it.seasonNumber == sNum }
                    val existingYear = existing?.airDate?.take(4)?.toIntOrNull()
                    val cmYear = eps.firstOrNull()?.airDate?.take(4)?.toIntOrNull()
                    val isYearMismatch = existing != null && sNum >= 2 && existingYear != null && cmYear != null &&
                        kotlin.math.abs(existingYear - cmYear) > 1
                    val skyHookPoster = skyHook?.seasonPosters?.get(sNum)

                    if (existing == null) {
                        val resolvedPoster = skyHookPoster ?: seasonPosters[sNum]
                        val posterUrl = if (sNum <= 0) {
                            mergedList.find { it.seasonNumber == 0 }?.posterUrl ?: resolvedPoster
                        } else if (sNum == 1) {
                            mergedList.find { it.seasonNumber == 1 }?.posterUrl ?: resolvedPoster
                        } else {
                            // Prefer distinct TVDB/AniList season poster; only fall back to Season 1 poster if none exists
                            resolvedPoster ?: if (hasLumpedSeason1) tmdbSeason1Poster else null
                        }
                        mergedList.add(
                            com.omnitrack.app.data.model.MediaSeason(
                                seasonNumber = sNum,
                                name = if (sNum <= 0) "Specials" else "Season $sNum",
                                episodeCount = eps.size,
                                posterUrl = posterUrl,
                                airDate = eps.firstOrNull()?.airDate
                            )
                        )
                    } else if (isYearMismatch) {
                        // Replace colliding TMDb sequel sub-season (e.g. 2022 TYBW stored as Season 2 on 2004 Bleach) with Cinemeta Season 2 (2005)
                        val idx = mergedList.indexOf(existing)
                        val resolvedPoster = skyHookPoster ?: seasonPosters[sNum] ?: if (hasLumpedSeason1) tmdbSeason1Poster else existing.posterUrl
                        mergedList[idx] = existing.copy(
                            name = "Season $sNum",
                            episodeCount = eps.size,
                            posterUrl = resolvedPoster,
                            airDate = eps.firstOrNull()?.airDate
                        )
                    } else if (sNum == 1 && cinemetaSeasons.keys.any { it > 1 } && existing.episodeCount > eps.size && eps.isNotEmpty()) {
                        // TMDb lumped Season 1 and Season 2+ together (e.g. Apothecary Diaries 60 eps vs Cinemeta 24 eps)
                        val idx = mergedList.indexOf(existing)
                        val resolvedPoster = skyHookPoster ?: existing.posterUrl
                        mergedList[idx] = existing.copy(episodeCount = eps.size, posterUrl = resolvedPoster)
                    } else {
                        val idx = mergedList.indexOf(existing)
                        val nextCount = if (existing.episodeCount <= 0 && eps.isNotEmpty()) eps.size else existing.episodeCount
                        val nextPoster = skyHookPoster ?: existing.posterUrl?.takeIf { it.isNotBlank() } ?: seasonPosters[sNum]
                        if (nextCount != existing.episodeCount || nextPoster != existing.posterUrl) {
                            mergedList[idx] = existing.copy(episodeCount = nextCount, posterUrl = nextPoster)
                        }
                    }
                }
                return mergedList
                    .distinctBy { it.seasonNumber }
                    .sortedWith(compareBy({ it.seasonNumber == 0 }, { it.seasonNumber }))
            }
        }

        return tmdbSeasons
            .distinctBy { it.seasonNumber }
            .sortedWith(compareBy({ it.seasonNumber == 0 }, { it.seasonNumber }))
    }

    private val tmdbTvAnimeCompatCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private suspend fun isTmdbTvCompatibleWithAnime(tmdbId: Long, apiKey: String, animeTitle: String?): Boolean {
        val key = "$tmdbId:${animeTitle?.lowercase()?.trim().orEmpty()}"
        tmdbTvAnimeCompatCache[key]?.let { return it }
        val result = NetworkPacer.withTmdbPacing {
            try {
                val url = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$apiKey"
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withTmdbPacing false
                    val body = resp.body?.string() ?: return@withTmdbPacing false
                    val root = json.parseToJsonElement(body).jsonObject
                    val genreIds = root["genres"]?.jsonArray?.mapNotNull {
                        it.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    }.orEmpty()
                    if (16 in genreIds) return@withTmdbPacing true
                    val tvName = root["name"].cleanString().orEmpty().lowercase()
                    val tvOrig = root["original_name"].cleanString().orEmpty().lowercase()
                    val normQuery = animeTitle?.lowercase()?.replace(Regex("[^a-z0-9 ]"), " ")?.trim().orEmpty()
                    val normTokens = normQuery.split(Regex("\\s+")).filter { it.length >= 3 }
                    val titleMatches = normTokens.isNotEmpty() && normTokens.all { tok ->
                        tvName.contains(tok) || tvOrig.contains(tok)
                    }
                    titleMatches && genreIds.isEmpty()
                }
            } catch (_: Exception) {
                true
            }
        }
        tmdbTvAnimeCompatCache[key] = result
        return result
    }

    suspend fun fetchTvSeasonEpisodes(
        tmdbId: Long,
        seasonNumber: Int,
        apiKey: String,
        imdbId: String? = null,
        showTitle: String? = null,
        anilistId: Long? = null
    ): List<com.omnitrack.app.data.model.MediaEpisode> {
        if (anilistId != null && anilistId > 0) {
            val azMap = fetchAniZipMapping(anilistId)
            if (azMap?.isMovie == true) return emptyList()
            if (!isTmdbTvCompatibleWithAnime(tmdbId, apiKey, showTitle)) return emptyList()
        }
        val rawSeasons = fetchTmdbRawSeasonMeta(tmdbId, apiKey)
        val effectiveImdbId = imdbId ?: fetchTmdbImdbId(tmdbId, false, apiKey)

        // Sub-Series Detection (e.g. "BLEACH: Thousand-Year Blood War" cours 1..4 inside TMDb Season 2)
        val matchedSubSeason = findMatchedSubSeason(rawSeasons, showTitle)
        if (matchedSubSeason != null && seasonNumber > 0) {
            val rawEps = fetchTmdbRawSeasonEpisodes(tmdbId, matchedSubSeason.seasonNumber, apiKey)
            val cours = splitEpisodesIntoCours(rawEps)
            val targetCour = cours.getOrNull(seasonNumber - 1) ?: return emptyList()
            val mapped = targetCour.mapIndexed { idx, ep ->
                ep.copy(
                    seasonNumber = seasonNumber,
                    episodeNumber = idx + 1
                )
            }
            return enrichEpisodesWithAccurateRatings(mapped, effectiveImdbId, seasonNumber)
        }

        val cinemetaVideos = if (!effectiveImdbId.isNullOrBlank()) fetchCinemetaVideos(effectiveImdbId) else emptyList()
        val hasMultiSeasonTmdb = rawSeasons.count { it.seasonNumber > 0 && it.episodeCount > 0 } > 1
        val hasLumpedSeason1 = rawSeasons.any { it.seasonNumber == 1 && it.episodeCount > 26 }
        val isAnimeLookup = anilistId != null && anilistId > 0L

        if (isAnimeLookup && seasonNumber > 0 && (hasLumpedSeason1 || !hasMultiSeasonTmdb)) {
            val epGroupSeasons = fetchBestTmdbAnimeEpisodeGroup(tmdbId, apiKey)
            val tmdbRegularCount = rawSeasons.count { it.seasonNumber > 0 && it.episodeCount > 0 }
            val cinemetaRegularCount = cinemetaVideos.map { it.seasonNumber }.filter { it > 0 }.distinct().size
            if (epGroupSeasons.size >= 2 && epGroupSeasons.size > maxOf(cinemetaRegularCount, tmdbRegularCount)) {
                val matchedGroup = epGroupSeasons.firstOrNull { it.seasonNumber == seasonNumber }
                if (matchedGroup != null && matchedGroup.episodes.isNotEmpty()) {
                    return enrichEpisodesWithAccurateRatings(matchedGroup.episodes, effectiveImdbId, seasonNumber)
                }
            }
        }

        val tvdbId = resolveTvdbId(tmdbId, apiKey, effectiveImdbId, anilistId)
        val skyHookEps = if (tvdbId != null && tvdbId > 0L) {
            (skyHookShowCache[tvdbId] ?: fetchSkyHookShow(tvdbId))?.episodesBySeason?.get(seasonNumber).orEmpty()
        } else emptyList()
        val cmSeasonEps = cinemetaVideos.filter { it.seasonNumber == seasonNumber }.sortedBy { it.episodeNumber }
        val hasCinemetaS2 = cinemetaVideos.any { it.seasonNumber == 2 }

        val tmdbRawMeta = rawSeasons.firstOrNull { it.seasonNumber == seasonNumber }
        val metaYear = tmdbRawMeta?.airDate?.take(4)?.toIntOrNull()
        val cmSeasonYear = cmSeasonEps.firstOrNull()?.airDate?.take(4)?.toIntOrNull()
        val isMetaYearMismatch = seasonNumber >= 2 && hasCinemetaS2 && cmSeasonEps.isNotEmpty() &&
            metaYear != null && cmSeasonYear != null && kotlin.math.abs(metaYear - cmSeasonYear) > 1
        val hasTmdbSeasonInMeta = (rawSeasons.isEmpty() || (tmdbRawMeta != null && tmdbRawMeta.episodeCount > 0)) && !isMetaYearMismatch

        if (hasTmdbSeasonInMeta) {
            val tmdbEpisodes = fetchTmdbRawSeasonEpisodes(tmdbId, seasonNumber, apiKey)
            if (tmdbEpisodes.isNotEmpty()) {
                val sliced = if (seasonNumber == 1 && cinemetaVideos.isNotEmpty()) {
                    val cinemetaS1Count = cinemetaVideos.count { it.seasonNumber == 1 }
                    if (hasCinemetaS2 && cinemetaS1Count in 1 until tmdbEpisodes.size) {
                        tmdbEpisodes.filter { it.episodeNumber <= cinemetaS1Count }
                    } else tmdbEpisodes
                } else {
                    val epYear = tmdbEpisodes.firstOrNull()?.airDate?.take(4)?.toIntOrNull()
                    val isEpYearMismatch = seasonNumber >= 2 && hasCinemetaS2 && cmSeasonEps.isNotEmpty() &&
                        epYear != null && cmSeasonYear != null && kotlin.math.abs(epYear - cmSeasonYear) > 1
                    if (!isEpYearMismatch) tmdbEpisodes else null
                }
                if (sliced != null) {
                    val base = sliced.map { ep ->
                        val shMatch = skyHookEps.firstOrNull { it.episodeNumber == ep.episodeNumber }
                        ep.copy(
                            stillUrl = ep.stillUrl ?: shMatch?.stillUrl,
                            overview = ep.overview?.takeIf { it.isNotBlank() } ?: shMatch?.overview,
                            runtimeMinutes = ep.runtimeMinutes ?: shMatch?.runtimeMinutes
                        )
                    }
                    return enrichEpisodesWithAccurateRatings(base, effectiveImdbId, seasonNumber)
                }
            }
        }

        // Cinemeta + TMDb Season 1 + TVDB (SkyHook) cumulative & title/date enrichment (e.g. Bleach S2-16, Apothecary Diaries S2/3)
        if (cmSeasonEps.isNotEmpty()) {
            val tmdbSeason1Eps = tmdbSeason1StillsCache[tmdbId] ?: fetchTmdbRawSeasonEpisodes(tmdbId, 1, apiKey)
            val cinemetaS1Count = cinemetaVideos.count { it.seasonNumber == 1 }
            val isLumpedSeries = tmdbSeason1Eps.size > cinemetaS1Count && cinemetaS1Count > 0
            val priorCinemetaEpsCount = if (seasonNumber > 0) {
                cinemetaVideos.count { it.seasonNumber in 1 until seasonNumber }
            } else 0

            val merged = cmSeasonEps.mapIndexed { idx, ep ->
                val cumulativeEpNum = if (seasonNumber > 0) priorCinemetaEpsCount + idx + 1 else -1
                val byCumulative = if (isLumpedSeries && cumulativeEpNum in 1..tmdbSeason1Eps.size) {
                    tmdbSeason1Eps.firstOrNull { it.episodeNumber == cumulativeEpNum }
                } else null
                val byTitle = if (byCumulative == null) {
                    tmdbSeason1Eps.firstOrNull { t ->
                        t.title.isNotBlank() && t.title.trim().equals(ep.title.trim(), ignoreCase = true)
                    }
                } else null
                val byDate = if (byCumulative == null && byTitle == null) {
                    tmdbSeason1Eps.firstOrNull { t -> isAirDateWithinDays(t.airDate, ep.airDate, 3L) }
                } else null

                val tmdbMatch = byCumulative ?: byTitle ?: byDate
                val shMatch = skyHookEps.firstOrNull { it.episodeNumber == ep.episodeNumber }
                val resolvedStill = tmdbMatch?.stillUrl ?: shMatch?.stillUrl ?: ep.stillUrl
                val resolvedOverview = if (ep.overview.isNullOrBlank()) (tmdbMatch?.overview ?: shMatch?.overview) else ep.overview
                val resolvedRuntime = ep.runtimeMinutes ?: tmdbMatch?.runtimeMinutes ?: shMatch?.runtimeMinutes

                ep.copy(
                    stillUrl = resolvedStill,
                    overview = resolvedOverview,
                    runtimeMinutes = resolvedRuntime,
                    voteAverage = tmdbMatch?.voteAverage ?: ep.voteAverage,
                    voteCount = tmdbMatch?.voteCount ?: ep.voteCount,
                    ratingSource = if (tmdbMatch?.voteAverage != null) tmdbMatch.ratingSource else ep.ratingSource
                )
            }
            return enrichEpisodesWithAccurateRatings(merged, effectiveImdbId, seasonNumber)
        }

        if (skyHookEps.isNotEmpty()) {
            return enrichEpisodesWithAccurateRatings(skyHookEps, effectiveImdbId, seasonNumber)
        }

        return emptyList()
    }

    data class KitsuSeasonData(
        val posterUrl: String? = null,
        val stills: Map<Int, String> = emptyMap()
    )

    suspend fun fetchKitsuSeasonData(title: String, seasonNumber: Int): KitsuSeasonData? = withContext(Dispatchers.IO) {
        val cacheKey = "$title:$seasonNumber"
        kitsuSeasonDataCache[cacheKey]?.let { return@withContext it }
        try {
            val baseTitle = title.split(":", "-", "–").firstOrNull()?.trim() ?: title
            val candidateQueries = listOfNotNull(
                baseTitle,
                if (seasonNumber > 1) "$baseTitle Season $seasonNumber" else null,
                if (seasonNumber > 1) "$baseTitle $seasonNumber" else null,
                title
            ).distinct()

            var matchedAnime: kotlinx.serialization.json.JsonObject? = null

            for (query in candidateQueries) {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://kitsu.io/api/edge/anime?filter[text]=$encoded&page[limit]=10"
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.api+json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val items = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val body = resp.body?.string() ?: return@use emptyList()
                    val root = json.parseToJsonElement(body).jsonObject
                    root["data"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
                }

                if (items.isEmpty()) continue

                if (seasonNumber <= 1) {
                    matchedAnime = items.firstOrNull()
                    if (matchedAnime != null) break
                } else {
                    for (item in items) {
                        val attrs = item["attributes"]?.jsonObject
                        val canonical = attrs?.get("canonicalTitle")?.jsonPrimitive?.content?.lowercase() ?: ""
                        val titlesObj = attrs?.get("titles")?.jsonObject
                        val allTitles = (titlesObj?.values?.mapNotNull { it.jsonPrimitive.content.lowercase() }.orEmpty() + canonical)
                        val posterObj = attrs?.get("posterImage")?.jsonObject
                        val hasPoster = posterObj?.get("large")?.jsonPrimitive?.content != null ||
                            posterObj?.get("original")?.jsonPrimitive?.content != null
                        val isSeasonMatch = allTitles.any { t ->
                            t.contains("${seasonNumber}nd") ||
                            t.contains("${seasonNumber}rd") ||
                            t.contains("${seasonNumber}th") ||
                            t.contains("season $seasonNumber") ||
                            t.contains(" $seasonNumber") ||
                            t.contains("第${seasonNumber}期") ||
                            t.contains("s$seasonNumber")
                        }
                        if (isSeasonMatch && hasPoster) {
                            matchedAnime = item
                            break
                        } else if (isSeasonMatch && matchedAnime == null) {
                            matchedAnime = item
                        }
                    }
                    if (matchedAnime != null) break
                }
            }

            if (matchedAnime == null) return@withContext null

            val animeId = matchedAnime["id"]?.jsonPrimitive?.content ?: return@withContext null
            val posterObj = matchedAnime["attributes"]?.jsonObject?.get("posterImage")?.jsonObject
            val posterUrl = posterObj?.get("large")?.jsonPrimitive?.content
                ?: posterObj?.get("original")?.jsonPrimitive?.content
                ?: posterObj?.get("medium")?.jsonPrimitive?.content

            val result = KitsuSeasonData(posterUrl = posterUrl, stills = emptyMap())
            kitsuSeasonDataCache[cacheKey] = result
            result
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchAniListSeasonPoster(title: String, seasonNumber: Int): String? = withContext(Dispatchers.IO) {
        val afterColon = title.substringAfter(":", "").trim()
        val strippedBase = if (afterColon.contains("season", ignoreCase = true) || afterColon.contains("part", ignoreCase = true)) {
            title.substringBefore(":").trim()
        } else {
            title
        }
        val strippedTitle = strippedBase
            .replace(Regex("(?i)\\s*(?:season|\\d+(?:st|nd|rd|th)\\s+season|part)\\s*\\d*.*"), "")
            .trim()
            .takeIf { it.isNotBlank() } ?: title
        val cacheKey = "$strippedTitle:$seasonNumber"
        aniListSeasonPosterCache[cacheKey]?.let { return@withContext it }

        try {
            val query = """
                query (${'$'}search: String) {
                  Media(search: ${'$'}search, type: ANIME) {
                    title { romaji english }
                    coverImage { extraLarge large }
                  }
                }
            """.trimIndent()
            val suffix = if (seasonNumber in 11..13) "th" else when (seasonNumber % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
            val searchQueries = if (seasonNumber <= 1) {
                listOf(strippedTitle)
            } else {
                listOf(
                    "$strippedTitle Season $seasonNumber",
                    "$strippedTitle $seasonNumber$suffix Season"
                ).distinct()
            }
            for (sq in searchQueries) {
                val jsonPayload = buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        put("search", sq)
                    }
                }.toString()
                val req = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", "OmniTrack/1.0")
                    .build()
                val foundImg = NetworkPacer.withAniListPacing {
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withAniListPacing null
                        val body = resp.body?.string() ?: return@withAniListPacing null
                        val root = json.parseToJsonElement(body).jsonObject
                        val media = root["data"]?.jsonObject?.get("Media")?.jsonObject
                        if (seasonNumber >= 2) {
                            val titleObj = media?.get("title")?.jsonObject
                            val rTitle = titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull.orEmpty()
                            val eTitle = titleObj?.get("english")?.jsonPrimitive?.contentOrNull.orEmpty()
                            val combined = "$rTitle $eTitle".lowercase()
                            val hasSeason = combined.contains("season $seasonNumber") ||
                                combined.contains("${seasonNumber}nd") ||
                                combined.contains("${seasonNumber}rd") ||
                                combined.contains("${seasonNumber}th") ||
                                combined.contains("part $seasonNumber") ||
                                combined.contains(" $seasonNumber")
                            if (!hasSeason) return@withAniListPacing null
                        }
                        val cover = media?.get("coverImage")?.jsonObject
                        cover?.get("extraLarge")?.jsonPrimitive?.contentOrNull
                            ?: cover?.get("large")?.jsonPrimitive?.contentOrNull
                    }
                }
                if (!foundImg.isNullOrBlank()) {
                    aniListSeasonPosterCache[cacheKey] = foundImg
                    return@withContext foundImg
                }
            }
            null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    suspend fun fetchAniListNextAiring(title: String, seasonNumber: Int? = null): com.omnitrack.app.data.model.NextAiringEpisode? = withContext(Dispatchers.IO) {
        try {
            val query = """
                query (${'$'}search: String) {
                  Page(page: 1, perPage: 8) {
                    media(search: ${'$'}search, type: ANIME) {
                      id
                      title { romaji english }
                      status
                      episodes
                      nextAiringEpisode {
                        airingAt
                        timeUntilAiring
                        episode
                      }
                    }
                  }
                }
            """.trimIndent()

            val baseTitle = title.split(":", "-", "–").firstOrNull()?.trim() ?: title
            val cleanTitle = title.replace(Regex("[:\\-_–]"), " ").trim()
            val searchQueries = mutableListOf<String>()
            if (seasonNumber != null && seasonNumber > 1) {
                searchQueries.add("$baseTitle Season $seasonNumber")
                searchQueries.add("$cleanTitle Season $seasonNumber")
            }
            searchQueries.add(title)
            searchQueries.add(cleanTitle)
            searchQueries.add(baseTitle)

            for (sq in searchQueries.distinct()) {
                val jsonPayload = buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        put("search", sq)
                    }
                }.toString()
                val req = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", "OmniTrack/1.0")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val root = json.parseToJsonElement(body).jsonObject
                        val mediaList = root["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray.orEmpty()
                        for (itemElem in mediaList) {
                            val mediaObj = itemElem.jsonObject
                            val nextAiring = mediaObj["nextAiringEpisode"]?.let { if (it is kotlinx.serialization.json.JsonObject) it else null }
                            if (nextAiring != null) {
                                val ep = nextAiring["episode"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                                val airingAt = nextAiring["airingAt"]?.jsonPrimitive?.content?.toLongOrNull()
                                val timeUntil = nextAiring["timeUntilAiring"]?.jsonPrimitive?.content?.toLongOrNull()
                                val mediaTitle = mediaObj["title"]?.jsonObject?.get("english")?.jsonPrimitive?.content
                                    ?: mediaObj["title"]?.jsonObject?.get("romaji")?.jsonPrimitive?.content

                                return@withContext com.omnitrack.app.data.model.NextAiringEpisode(
                                    seasonNumber = seasonNumber,
                                    episodeNumber = ep,
                                    title = mediaTitle,
                                    airingAtEpochSec = airingAt,
                                    timeUntilAiringSec = timeUntil
                                )
                            }
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonElement?.cleanString(): String? {
        if (this == null || this is JsonNull) return null
        val str = try { this.jsonPrimitive.contentOrNull?.trim() } catch (_: Exception) { null } ?: return null
        return if (str.equals("null", ignoreCase = true) || str.equals("N/A", ignoreCase = true) || str.isBlank()) null else str
    }

    private fun String?.cleanString(): String? {
        if (this == null) return null
        val trimmed = this.trim()
        return if (trimmed.equals("null", ignoreCase = true) || trimmed.equals("N/A", ignoreCase = true) || trimmed.isBlank()) null else trimmed
    }

    suspend fun fetchAnimeDetails(
        anilistId: Long? = null,
        malId: Long? = null,
        title: String? = null,
        existingSeasonsCount: Int = 1,
        mediaType: MediaType = MediaType.ANIME
    ): AnimeEnrichedDetails? = withContext(Dispatchers.IO) {
        try {
            fun buildMediaQuery(withNovelFormat: Boolean): String {
                val formatClause = if (withNovelFormat) ", format: NOVEL" else ""
                return """
                query (${'$'}id: Int, ${'$'}idMal: Int, ${'$'}search: String, ${'$'}type: MediaType) {
                  Media(id: ${'$'}id, idMal: ${'$'}idMal, search: ${'$'}search, type: ${'$'}type$formatClause) {
                    id
                    idMal
                    title {
                      romaji
                      english
                      native
                    }
                    format
                    status
                    season
                    seasonYear
                    source
                    countryOfOrigin
                    startDate {
                      year
                      month
                      day
                    }
                    endDate {
                      year
                      month
                      day
                    }
                    externalLinks {
                      id
                      url
                      site
                      type
                      icon
                      color
                    }
                    synonyms
                    duration
                    popularity
                    favourites
                    description(asHtml: false)
                    bannerImage
                    coverImage {
                      large
                      extraLarge
                    }
                    averageScore
                    episodes
                    chapters
                    volumes
                    genres
                    tags {
                      id
                      name
                      rank
                      isMediaSpoiler
                      isGeneralSpoiler
                    }
                    nextAiringEpisode {
                      episode
                      airingAt
                      timeUntilAiring
                    }
                    characters(sort: [ROLE, RELEVANCE, ID], perPage: 25) {
                      edges {
                        role
                        node {
                          id
                          name {
                            full
                          }
                          image {
                            large
                          }
                        }
                        voiceActors(language: JAPANESE) {
                          id
                          name {
                            full
                          }
                          image {
                            large
                          }
                          languageV2
                        }
                      }
                    }
                    staff(sort: [RELEVANCE, ID], perPage: 25) {
                      edges {
                        role
                        node {
                          id
                          name {
                            full
                            native
                          }
                          image {
                            large
                          }
                        }
                      }
                    }
                    relations {
                      edges {
                        relationType
                        node {
                          id
                          idMal
                          title {
                            romaji
                            english
                          }
                          format
                          type
                          status
                          startDate {
                            year
                            month
                          }
                          bannerImage
                          coverImage {
                            large
                            extraLarge
                          }
                          relations {
                            edges {
                              relationType
                              node {
                                id
                                idMal
                                title {
                                  romaji
                                  english
                                }
                                format
                                type
                                status
                                startDate {
                                  year
                                  month
                                }
                                bannerImage
                                coverImage {
                                  large
                                  extraLarge
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                    studios {
                      edges {
                        isMain
                        node {
                          id
                          name
                          isAnimationStudio
                        }
                      }
                    }
                  }
                }
            """.trimIndent()
            }

            val anilistMediaType = if (mediaType == MediaType.MANGA || mediaType == MediaType.NOVEL) "MANGA" else "ANIME"

            // Safe lookup: Only send ONE mutually exclusive variable to avoid AND-filtering in AniList
            val candidateVars = mutableListOf<JsonObject>()
            if (anilistId != null && anilistId > 0) {
                candidateVars.add(buildJsonObject {
                    put("id", anilistId)
                    put("type", anilistMediaType)
                })
            }
            if (malId != null && malId > 0) {
                candidateVars.add(buildJsonObject {
                    put("idMal", malId)
                    put("type", anilistMediaType)
                })
            }
            if (!title.isNullOrBlank()) {
                val strippedTitle = title.replace(Regex("""\s*\((Light Novel|Web Novel|Novel|LN|WN)\)\s*$""", RegexOption.IGNORE_CASE), "").trim()
                val cleanTitle = strippedTitle.replace(Regex("[:\\-_–]"), " ").trim()
                val baseTitle = strippedTitle.split(":", "-", "–").firstOrNull()?.trim() ?: strippedTitle
                candidateVars.add(buildJsonObject {
                    put("search", strippedTitle)
                    put("type", anilistMediaType)
                })
                if (cleanTitle != strippedTitle) {
                    candidateVars.add(buildJsonObject {
                        put("search", cleanTitle)
                        put("type", anilistMediaType)
                    })
                }
                if (baseTitle != strippedTitle && baseTitle.length >= 3) {
                    candidateVars.add(buildJsonObject {
                        put("search", baseTitle)
                        put("type", anilistMediaType)
                    })
                }
            }

            for (vars in candidateVars) {
                val useNovelFormat = mediaType == MediaType.NOVEL && vars.containsKey("search")
                val jsonPayload = buildJsonObject {
                    put("query", buildMediaQuery(useNovelFormat))
                    put("variables", vars)
                }.toString()

                val req = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", "OmniTrack/1.0")
                    .build()

                val result = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string() ?: return@use null
                    val root = json.parseToJsonElement(body).jsonObject
                    val media = root["data"]?.jsonObject?.get("Media")?.jsonObject ?: return@use null

                    val resolvedId = media["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    val resolvedMalId = media["idMal"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

                    val mediaTitleObj = media["title"]?.jsonObject
                    val mediaEnglishTitle = mediaTitleObj?.get("english").cleanString()
                    val mediaRomajiTitle = mediaTitleObj?.get("romaji").cleanString()
                    val mediaNativeTitle = mediaTitleObj?.get("native").cleanString()

                    val mediaSynopsis = media["description"].cleanString()
                    val mediaBanner = media["bannerImage"].cleanString()
                    val mediaCover = media["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                        ?: media["coverImage"]?.jsonObject?.get("large").cleanString()
                    val mediaScore = media["averageScore"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.let { it / 10f }
                    val mediaEpisodes = media["episodes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val mediaChapters = media["chapters"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val mediaVolumes = media["volumes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val mediaGenres = media["genres"]?.jsonArray.orEmpty().mapNotNull { it.cleanString() }
                    val mediaSynonyms = media["synonyms"]?.jsonArray.orEmpty().mapNotNull { it.cleanString() }
                    val mediaPopularity = media["popularity"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val mediaFavourites = media["favourites"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val mediaDuration = media["duration"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val mediaFormat = media["format"].cleanString()
                    val mediaStatus = media["status"].cleanString()
                    val mediaSeason = media["season"].cleanString()
                    val mediaSeasonYear = media["seasonYear"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val mediaSource = media["source"].cleanString()
                    val mediaCountryOfOrigin = media["countryOfOrigin"].cleanString()

                    val startObj = media["startDate"]?.jsonObject
                    val sYear = startObj?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val sMonth = startObj?.get("month")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val sDay = startObj?.get("day")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val mediaStartDate = formatAniListDate(sYear, sMonth, sDay)

                    val endObj = media["endDate"]?.jsonObject
                    val eYear = endObj?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val eMonth = endObj?.get("month")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val eDay = endObj?.get("day")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val mediaEndDate = formatAniListDate(eYear, eMonth, eDay)

                    val externalLinkList = mutableListOf<com.omnitrack.app.data.model.ExternalLinkItem>()
                    val extEdges = media["externalLinks"]?.jsonArray.orEmpty()
                    for (elem in extEdges) {
                        val obj = elem.jsonObject
                        val eId = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                        val eUrl = obj["url"].cleanString() ?: continue
                        val eSite = obj["site"].cleanString() ?: continue
                        val eType = obj["type"].cleanString()
                        val eIcon = obj["icon"].cleanString()
                        val eColor = obj["color"].cleanString()
                        externalLinkList.add(
                            com.omnitrack.app.data.model.ExternalLinkItem(
                                id = eId,
                                url = eUrl,
                                site = eSite,
                                type = eType,
                                icon = eIcon,
                                color = eColor
                            )
                        )
                    }

                    val tagList = mutableListOf<com.omnitrack.app.data.model.AnimeTagItem>()
                    val tagsArray = media["tags"]?.jsonArray.orEmpty()
                    for (tElem in tagsArray) {
                        val tObj = tElem.jsonObject
                        val tId = tObj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                        val tName = tObj["name"].cleanString() ?: continue
                        val tRank = tObj["rank"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        val isMediaSpoiler = tObj["isMediaSpoiler"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                        val isGeneralSpoiler = tObj["isGeneralSpoiler"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                        tagList.add(
                            com.omnitrack.app.data.model.AnimeTagItem(
                                id = tId,
                                name = tName,
                                rank = tRank,
                                isMediaSpoiler = isMediaSpoiler,
                                isGeneralSpoiler = isGeneralSpoiler
                            )
                        )
                    }

                    // 1. Next Airing
                    val nextAiringObj = media["nextAiringEpisode"]?.let { if (it is JsonObject) it else null }
                    val airingSchedule = if (nextAiringObj != null) {
                        val ep = nextAiringObj["episode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        val at = nextAiringObj["airingAt"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        if (ep != null && at != null) {
                            AnimeAiringSchedule(episodeNumber = ep, airingAtEpochSeconds = at)
                        } else null
                    } else null

                    // 2. Characters & Seiyuu (sort: [ROLE, RELEVANCE, ID])
                    val charList = mutableListOf<AnimeCharacterItem>()
                    val charEdges = media["characters"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                    for (edgeElem in charEdges) {
                        val edge = edgeElem.jsonObject
                        val role = edge["role"].cleanString() ?: "SUPPORT"
                        val node = edge["node"]?.jsonObject ?: continue
                        val charId = node["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                        val charName = node["name"]?.jsonObject?.get("full").cleanString() ?: continue
                        val charImg = node["image"]?.jsonObject?.get("large").cleanString()

                        val vaEdges = edge["voiceActors"]?.jsonArray.orEmpty()
                        val primaryVa = vaEdges.firstOrNull()?.jsonObject
                        val vaId = primaryVa?.get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        val vaName = primaryVa?.get("name")?.jsonObject?.get("full").cleanString()
                        val vaImg = primaryVa?.get("image")?.jsonObject?.get("large").cleanString()
                        val vaLang = primaryVa?.get("languageV2").cleanString() ?: "Japanese"

                        charList.add(
                            AnimeCharacterItem(
                                characterId = charId,
                                characterName = charName,
                                characterRole = role,
                                characterImageUrl = charImg,
                                voiceActorId = vaId,
                                voiceActorName = vaName,
                                voiceActorImageUrl = vaImg,
                                voiceActorLanguage = if (vaLang.contains("Japan", ignoreCase = true)) "JP" else vaLang.take(2).uppercase()
                            )
                        )
                    }

                    // 2b. Production Staff (Director, Original Creator, Music, Character Design, etc.)
                    val staffList = mutableListOf<AnimeStaffItem>()
                    val staffEdges = media["staff"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                    for (edgeElem in staffEdges) {
                        val edge = edgeElem.jsonObject
                        val sRole = edge["role"].cleanString() ?: continue
                        val node = edge["node"]?.jsonObject ?: continue
                        val sId = node["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                        val sName = node["name"]?.jsonObject?.get("full").cleanString() ?: continue
                        val sNative = node["name"]?.jsonObject?.get("native").cleanString()
                        val sImg = node["image"]?.jsonObject?.get("large").cleanString()

                        staffList.add(
                            AnimeStaffItem(
                                staffId = sId,
                                name = sName,
                                nativeName = sNative,
                                role = sRole,
                                imageUrl = sImg
                            )
                        )
                    }

                    // 3. Franchise & Relations with smart season deduplication and multi-season mapping
                    val relationList = mutableListOf<AnimeRelationItem>()
                    val relEdges = media["relations"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                    val baseTitleNorm = (title ?: "").lowercase().replace(Regex("[^a-z0-9]"), "")
                    val seasonAniListMap = mutableMapOf<Int, Long>()
                    val seasonMalMap = mutableMapOf<Int, Long>()

                    fun parseSeasonNumber(text: String?): Int? {
                        if (text.isNullOrBlank()) return null
                        val m1 = Regex("""(?i)\bseason\s*(\d+)\b""").find(text)
                        if (m1 != null) return m1.groupValues[1].toIntOrNull()
                        val m2 = Regex("""(?i)\b(\d+)(?:st|nd|rd|th)\s*season\b""").find(text)
                        if (m2 != null) return m2.groupValues[1].toIntOrNull()
                        return null
                    }

                    val rootTitle = mediaEnglishTitle ?: mediaRomajiTitle ?: title ?: ""
                    val rootSeason = parseSeasonNumber(rootTitle) ?: 1
                    if (resolvedId != null && resolvedId > 0) {
                        seasonAniListMap[rootSeason] = resolvedId
                    }
                    if (resolvedMalId != null && resolvedMalId > 0) {
                        seasonMalMap[rootSeason] = resolvedMalId
                    }

                    for (edgeElem in relEdges) {
                        val edge = edgeElem.jsonObject
                        val relType = edge["relationType"].cleanString() ?: continue
                        val node = edge["node"]?.jsonObject ?: continue
                        val relId = node["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                        val relIdMal = node["idMal"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        val relFormat = node["format"].cleanString() ?: "TV"
                        val relTypeCategory = node["type"].cleanString() ?: "ANIME"
                        val english = node["title"]?.jsonObject?.get("english").cleanString()
                        val romaji = node["title"]?.jsonObject?.get("romaji").cleanString()
                        val relTitle = english ?: romaji ?: "Unknown Title"
                        val relBanner = node["bannerImage"].cleanString()
                        val cover = node["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                            ?: node["coverImage"]?.jsonObject?.get("large").cleanString()
                        val year = node["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()

                        // Extract per-season AniList ID from Level 1 relation
                        val sNum = parseSeasonNumber(relTitle)
                        var assignedParentSeason: Int? = sNum
                        if (sNum != null && sNum > 0) {
                            if (!seasonAniListMap.containsKey(sNum)) {
                                seasonAniListMap[sNum] = relId
                            }
                            if (relIdMal != null && relIdMal > 0 && !seasonMalMap.containsKey(sNum)) {
                                seasonMalMap[sNum] = relIdMal
                            }
                        } else if (relFormat in listOf("TV", "TV_SHORT", "ONA") && relType == "SEQUEL") {
                            val seqNum = if (!seasonAniListMap.containsKey(2)) 2 else ((seasonAniListMap.keys.maxOrNull() ?: 1) + 1)
                            assignedParentSeason = seqNum
                            seasonAniListMap[seqNum] = relId
                            if (relIdMal != null && relIdMal > 0 && !seasonMalMap.containsKey(seqNum)) {
                                seasonMalMap[seqNum] = relIdMal
                            }
                        } else if (relType in listOf("SIDE_STORY", "SPECIAL", "OVA")) {
                            if (!seasonAniListMap.containsKey(0)) {
                                seasonAniListMap[0] = relId
                            }
                            if (relIdMal != null && relIdMal > 0 && !seasonMalMap.containsKey(0)) {
                                seasonMalMap[0] = relIdMal
                            }
                        }

                        // Inspect Level 2 nested relations for multi-season TV anime:
                        // Traversed on ADAPTATION (e.g. Manga/Novel linking all seasons) or SEQUEL/PREQUEL/PARENT
                        if (relType in listOf("ADAPTATION", "SEQUEL", "PREQUEL", "PARENT")) {
                            val childEdges = node["relations"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                            for (childElem in childEdges) {
                                val cEdge = childElem.jsonObject
                                val cRelType = cEdge["relationType"].cleanString() ?: continue
                                val cNode = cEdge["node"]?.jsonObject ?: continue
                                val cId = cNode["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                                val cIdMal = cNode["idMal"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                val cFormat = cNode["format"].cleanString() ?: "TV"
                                val cType = cNode["type"].cleanString() ?: "ANIME"
                                val cEng = cNode["title"]?.jsonObject?.get("english").cleanString()
                                val cRom = cNode["title"]?.jsonObject?.get("romaji").cleanString()
                                val cTitle = cEng ?: cRom ?: ""

                                if (cType.equals("ANIME", ignoreCase = true) && cFormat in listOf("TV", "TV_SHORT", "ONA")) {
                                    val cNum = parseSeasonNumber(cTitle)
                                    if (cNum != null && cNum > 0) {
                                        if (!seasonAniListMap.containsKey(cNum)) {
                                            seasonAniListMap[cNum] = cId
                                        }
                                        if (cIdMal != null && cIdMal > 0 && !seasonMalMap.containsKey(cNum)) {
                                            seasonMalMap[cNum] = cIdMal
                                        }
                                    } else if (cRelType == "SEQUEL" && relType == "SEQUEL" && assignedParentSeason != null) {
                                        val nextSeqSeason = assignedParentSeason + 1
                                        if (!seasonAniListMap.containsKey(nextSeqSeason)) {
                                            seasonAniListMap[nextSeqSeason] = cId
                                        }
                                        if (cIdMal != null && cIdMal > 0 && !seasonMalMap.containsKey(nextSeqSeason)) {
                                            seasonMalMap[nextSeqSeason] = cIdMal
                                        }
                                    } else if (cRelType in listOf("ADAPTATION", "PREQUEL") && !seasonAniListMap.containsKey(1)) {
                                        val cYear = cNode["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                        if (cYear == null || (year != null && cYear <= year)) {
                                            seasonAniListMap[1] = cId
                                            if (cIdMal != null && cIdMal > 0 && !seasonMalMap.containsKey(1)) {
                                                seasonMalMap[1] = cIdMal
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Determine Target MediaType
                        val targetMediaType = when (relTypeCategory.uppercase()) {
                            "MANGA" -> if (relFormat.equals("NOVEL", ignoreCase = true)) MediaType.NOVEL else MediaType.MANGA
                            else -> if (relFormat.equals("MOVIE", ignoreCase = true)) MediaType.MOVIE else MediaType.ANIME
                        }

                        // Smart Season Deduplication (Safeguard 3):
                        // If multi-season TMDb data exists (existingSeasonsCount > 1), filter out TV relations where
                        // relType is SEQUEL, PREQUEL, or PARENT UNLESS the base franchise title differs (e.g. Naruto -> Boruto).
                        if (existingSeasonsCount > 1 && relFormat.equals("TV", ignoreCase = true)) {
                            val isStandardSeasonRel = relType in listOf("SEQUEL", "PREQUEL", "PARENT")
                            if (isStandardSeasonRel) {
                                val relTitleNorm = relTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
                                val isSameFranchiseBase = relTitleNorm.startsWith(baseTitleNorm) || baseTitleNorm.startsWith(relTitleNorm)
                                if (isSameFranchiseBase) {
                                    // Skip redundant season already handled by seasons dropdown
                                    continue
                                }
                            }
                        }

                        relationList.add(
                            AnimeRelationItem(
                                id = relId,
                                title = relTitle,
                                relationType = relType,
                                format = relFormat,
                                coverUrl = cover,
                                bannerUrl = relBanner,
                                year = year,
                                mediaType = targetMediaType
                            )
                        )
                    }

                    AppLogger.d("EnrichmentRepository", "Discovered seasonAniListIds for '$title': $seasonAniListMap, seasonMalIds: $seasonMalMap")

                    // 4. Studios & Networks
                    val studioList = mutableListOf<String>()
                    val allStudioList = mutableListOf<com.omnitrack.app.data.model.AnimeStudioItem>()
                    val studioEdges = media["studios"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                    for (edgeElem in studioEdges) {
                        val edgeObj = edgeElem.jsonObject
                        val isMain = edgeObj["isMain"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                        val node = edgeObj["node"]?.jsonObject ?: continue
                        val stId = node["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                        val isAnim = node["isAnimationStudio"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                        val studioName = node["name"].cleanString() ?: continue
                        if (isAnim && studioName.isNotBlank()) {
                            studioList.add(studioName)
                        }
                        allStudioList.add(
                            com.omnitrack.app.data.model.AnimeStudioItem(
                                id = stId,
                                name = studioName,
                                isAnimationStudio = isAnim,
                                isMain = isMain
                            )
                        )
                    }

                    AnimeEnrichedDetails(
                        anilistId = resolvedId,
                        malId = resolvedMalId,
                        englishTitle = mediaEnglishTitle,
                        romajiTitle = mediaRomajiTitle,
                        nativeTitle = mediaNativeTitle,
                        synonyms = mediaSynonyms,
                        characters = charList,
                        staff = staffList,
                        relations = relationList,
                        nextAiring = airingSchedule,
                        animationStudios = studioList,
                        allStudios = allStudioList,
                        synopsis = mediaSynopsis,
                        bannerUrl = mediaBanner,
                        coverUrl = mediaCover,
                        averageScore = mediaScore,
                        popularity = mediaPopularity,
                        favourites = mediaFavourites,
                        durationMinutes = mediaDuration,
                        totalEpisodes = mediaEpisodes,
                        totalChapters = mediaChapters,
                        totalVolumes = mediaVolumes,
                        genres = mediaGenres,
                        tags = tagList,
                        seasonAniListIds = seasonAniListMap,
                        seasonMalIds = seasonMalMap,
                        format = mediaFormat,
                        season = mediaSeason,
                        seasonYear = mediaSeasonYear,
                        source = mediaSource,
                        countryOfOrigin = mediaCountryOfOrigin,
                        startDate = mediaStartDate,
                        endDate = mediaEndDate,
                        externalLinks = externalLinkList
                    )
                }

                if (result != null) return@withContext result
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    data class MangaUpdatesSeriesInfo(
        val seriesId: Long,
        val latestChapter: Int?,
        val totalVolumes: Int?,
        val seasons: List<Triple<Int, String, IntRange>>,
        val publishers: List<String>,
        val bayesianRating: Float? = null,
        val totalStatusChapters: Int? = null
    )

    data class MangaSeriesContent(
        val seasons: List<com.omnitrack.app.data.model.MediaSeason>,
        val episodesBySeason: Map<Int, List<com.omnitrack.app.data.model.MediaEpisode>>,
        val isSeasonGrouped: Boolean,
        val resolvedMangaDexId: String? = null,
        val totalChapters: Int? = null,
        val publishers: List<String> = emptyList()
    )

    data class WikidataNativeTitles(
        val zh: String? = null,
        val ko: String? = null,
        val ja: String? = null
    ) {
        fun allDistinct(): List<String> = listOfNotNull(zh, ko, ja).distinct()
    }

    data class RanobeDbVolume(
        val number: Int,
        val title: String,
        val coverUrl: String?,
        val releaseDate: String?,
        val synopsis: String?,
        val label: String? = null,
        val titleOrig: String? = null,
        val romajiOrig: String? = null,
        val bookType: String? = null,
        val bookId: Long? = null
    )

    data class RanobeDbBookLink(
        val label: String,
        val url: String
    )

    data class RanobeDbBookDetail(
        val bookId: Long,
        val title: String,
        val titleOrig: String?,
        val synopsis: String?,
        val ratingScore: Float?,
        val ratingCount: Int?,
        val jpReleaseDate: String?,
        val enReleaseDate: String?,
        val printPages: Int?,
        val digitalPages: Int?,
        val isbn13: String?,
        val publishers: List<String>,
        val staff: List<AnimeStaffItem>,
        val links: List<RanobeDbBookLink>
    )

    data class RanobeDbSeriesDetails(
        val seriesId: Long,
        val title: String,
        val nativeTitle: String?,
        val synopsis: String?,
        val anilistId: Long?,
        val malId: Long?,
        val rating: Float?,
        val originCountry: String?,
        val volumes: List<RanobeDbVolume>,
        val publishers: List<String>,
        val staff: List<AnimeStaffItem>
    )

    data class KitsuNovelDetails(
        val kitsuId: String,
        val title: String,
        val synopsis: String?,
        val chapterCount: Int?,
        val volumeCount: Int?,
        val status: String?,
        val score: Float?,
        val posterUrl: String?,
        val bannerUrl: String?,
        val categories: List<String>
    )

    data class NovelSeriesContent(
        val isLightNovel: Boolean,
        val seasons: List<com.omnitrack.app.data.model.MediaSeason>,
        val episodesBySeason: Map<Int, List<com.omnitrack.app.data.model.MediaEpisode>>,
        val hasBookSplits: Boolean,
        val totalCount: Int?,
        val publishers: List<String> = emptyList(),
        val ranobeDetails: RanobeDbSeriesDetails? = null,
        val kitsuDetails: KitsuNovelDetails? = null,
        val mangaUpdatesRating: Float? = null,
        val bangumiScore: Float? = null,
        val originCountry: String? = null
    )

    private val mangaSeriesContentCache = java.util.concurrent.ConcurrentHashMap<String, MangaSeriesContent>()
    private val novelSeriesContentCache = java.util.concurrent.ConcurrentHashMap<String, NovelSeriesContent>()
    private val wikidataNativeCache = java.util.concurrent.ConcurrentHashMap<String, WikidataNativeTitles>()
    private val ranobeDbSeriesCache = java.util.concurrent.ConcurrentHashMap<String, RanobeDbSeriesDetails>()
    private val ranobeDbBookCache = java.util.concurrent.ConcurrentHashMap<Long, RanobeDbBookDetail>()
    private val kitsuNovelDetailsCache = java.util.concurrent.ConcurrentHashMap<String, KitsuNovelDetails>()

    private fun stripNovelEditionSuffix(title: String): String {
        return title.replace(Regex("""\s*[\(\[](Light Novel|Web Novel|Novel|LN|WN)[\)\]]\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(Novel\)\s*""", RegexOption.IGNORE_CASE), " ")
            .trim()
    }

    private fun isNovelTitleMatch(query: String, candidates: List<String?>): Boolean {
        val normQuery = stripNovelEditionSuffix(query).lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (normQuery.isBlank()) return false
        val qTokens = normQuery.split(" ").filter { it.length >= 2 }.toSet()

        for (raw in candidates) {
            val c = raw.cleanString() ?: continue
            val normCand = stripNovelEditionSuffix(c).lowercase()
                .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (normCand.isBlank()) continue
            if (normCand == normQuery) return true
            if (normQuery.length >= 5 && (normCand.contains(normQuery) || normQuery.contains(normCand))) {
                // Ensure lengths are reasonably aligned to avoid "Lord" matching "Lord of..."
                val ratio = minOf(normCand.length, normQuery.length).toFloat() / maxOf(normCand.length, normQuery.length).toFloat()
                if (ratio >= 0.48f) return true
            }
            if (qTokens.isNotEmpty()) {
                val cTokens = normCand.split(" ").filter { it.length >= 2 }.toSet()
                val overlap = qTokens.intersect(cTokens).size
                val coverage = overlap.toFloat() / maxOf(qTokens.size, 1).toFloat()
                if (coverage >= 0.65f) return true
            }
        }
        return false
    }

    private suspend fun fetchMangaUpdatesInfo(title: String, preferNovel: Boolean = false): MangaUpdatesSeriesInfo? = withContext(Dispatchers.IO) {
        val cleanQuery = stripNovelEditionSuffix(title)
        if (cleanQuery.isBlank()) return@withContext null
        try {
            val payload = buildJsonObject {
                put("search", cleanQuery)
                put("perpage", if (preferNovel) 8 else 5)
            }.toString()
            val req = Request.Builder()
                .url("https://api.mangaupdates.com/v1/series/search")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "OmniTrack/1.0")
                .build()
            val seriesId = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray.orEmpty()
                val filtered = if (preferNovel) {
                    results.filter {
                        it.jsonObject["record"]?.jsonObject?.get("type").cleanString().equals("Novel", ignoreCase = true)
                    }.ifEmpty { results }
                } else {
                    results
                }
                val exact = filtered.firstOrNull { node ->
                    val t = node.jsonObject["record"]?.jsonObject?.get("title").cleanString() ?: return@firstOrNull false
                    stripNovelEditionSuffix(t).equals(cleanQuery, ignoreCase = true)
                } ?: filtered.firstOrNull { node ->
                    val t = node.jsonObject["record"]?.jsonObject?.get("title").cleanString() ?: return@firstOrNull false
                    isNovelTitleMatch(cleanQuery, listOf(t))
                } ?: filtered.firstOrNull()
                exact?.jsonObject?.get("record")?.jsonObject?.get("series_id")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            } ?: return@withContext null

            val detReq = Request.Builder()
                .url("https://api.mangaupdates.com/v1/series/$seriesId")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(detReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val root = json.parseToJsonElement(body).jsonObject
                val latestCh = root["latest_chapter"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val bayesianRating = root["bayesian_rating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.takeIf { it > 0f }
                val statusText = root["status"].cleanString().orEmpty()
                val totalVols = Regex("""(\d+)\s+Volumes""", RegexOption.IGNORE_CASE)
                    .find(statusText)?.groupValues?.getOrNull(1)?.toIntOrNull()

                val mainPlusSide = Regex("""(\d+)\s+Main Story Chapters\s*\+\s*(\d+)\s+Side Stor""", RegexOption.IGNORE_CASE)
                    .find(statusText)?.let { m ->
                        (m.groupValues[1].toIntOrNull() ?: 0) + (m.groupValues[2].toIntOrNull() ?: 0)
                    }
                val explicitTotalCh = mainPlusSide
                    ?: Regex("""(\d+)\s+Chapters""", RegexOption.IGNORE_CASE).find(statusText)?.groupValues?.getOrNull(1)?.toIntOrNull()

                val seasons = mutableListOf<Triple<Int, String, IntRange>>()
                var runningStart = 1
                val sMatches = Regex("""S(\d+):\s*[^\n]*?(?:(\d+)\s+Chapters)?[^\n]*?(?:\((\d+)\s*-\s*(\d+)\))?""", RegexOption.IGNORE_CASE)
                    .findAll(statusText).toList()
                for (m in sMatches) {
                    val sNum = m.groupValues[1].toIntOrNull() ?: continue
                    val count = m.groupValues[2].toIntOrNull()
                    val rStart = m.groupValues[3].toIntOrNull()
                    val rEnd = m.groupValues[4].toIntOrNull()
                    val range = when {
                        rStart != null && rEnd != null && rEnd >= rStart -> {
                            runningStart = rEnd + 1
                            rStart..rEnd
                        }
                        count != null && count > 0 -> {
                            val end = runningStart + count - 1
                            val r = runningStart..end
                            runningStart = end + 1
                            r
                        }
                        else -> continue
                    }
                    seasons.add(Triple(sNum, "Season $sNum", range))
                }
                Regex("""Epilogue:\s*(\d+)\s+Chapters""", RegexOption.IGNORE_CASE).find(statusText)?.let { epM ->
                    val epCount = epM.groupValues[1].toIntOrNull()
                    if (epCount != null && epCount > 0 && seasons.isNotEmpty()) {
                        val nextNum = (seasons.maxOfOrNull { it.first } ?: 1) + 1
                        seasons.add(Triple(nextNum, "Epilogue", runningStart..(runningStart + epCount - 1)))
                    }
                }

                val publishers = root["publishers"]?.jsonArray?.mapNotNull { node ->
                    node.jsonObject["publisher_name"].cleanString()
                }.orEmpty().distinct()

                MangaUpdatesSeriesInfo(
                    seriesId = seriesId,
                    latestChapter = latestCh,
                    totalVolumes = totalVols,
                    seasons = seasons,
                    publishers = publishers,
                    bayesianRating = bayesianRating,
                    totalStatusChapters = explicitTotalCh
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    private suspend fun resolveMangaDexManga(
        title: String,
        anilistId: Long?,
        malId: Long?,
        existingMdId: String?
    ): Pair<String, kotlinx.serialization.json.JsonObject>? = NetworkPacer.withMangaDexPacing {
        try {
            if (!existingMdId.isNullOrBlank()) {
                val req = Request.Builder()
                    .url("https://api.mangadex.org/manga/$existingMdId?includes[]=cover_art")
                    .header("User-Agent", "OmniTrack/1.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        val dataObj = body?.let { json.parseToJsonElement(it).jsonObject["data"]?.jsonObject }
                        if (dataObj != null) return@withMangaDexPacing existingMdId to dataObj
                    }
                }
            }

            if (title.isBlank()) return@withMangaDexPacing null
            val encoded = URLEncoder.encode(title.trim(), "UTF-8")
            val req = Request.Builder()
                .url("https://api.mangadex.org/manga?title=$encoded&limit=10&includes[]=cover_art")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withMangaDexPacing null
                val body = resp.body?.string() ?: return@withMangaDexPacing null
                val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray.orEmpty()
                if (data.isEmpty()) return@withMangaDexPacing null

                val alStr = anilistId?.toString()
                val malStr = malId?.toString()
                val matched = (if (alStr != null) {
                    data.firstOrNull { node ->
                        node.jsonObject["attributes"]?.jsonObject?.get("links")?.jsonObject?.get("al").cleanString() == alStr
                    }
                } else null) ?: (if (malStr != null) {
                    data.firstOrNull { node ->
                        node.jsonObject["attributes"]?.jsonObject?.get("links")?.jsonObject?.get("mal").cleanString() == malStr
                    }
                } else null) ?: data.firstOrNull { node ->
                    val tObj = node.jsonObject["attributes"]?.jsonObject?.get("title")?.jsonObject
                    val tEn = tObj?.get("en").cleanString()
                    tEn != null && tEn.equals(title.trim(), ignoreCase = true)
                } ?: data.firstOrNull()

                val obj = matched?.jsonObject ?: return@withMangaDexPacing null
                val id = obj["id"].cleanString() ?: return@withMangaDexPacing null
                id to obj
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    private suspend fun fetchKitsuMangaChapters(
        title: String,
        limit: Int = 20
    ): Map<Int, Pair<Int?, String>> = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext emptyMap()
        try {
            val encoded = URLEncoder.encode(title.trim(), "UTF-8")
            val searchReq = Request.Builder()
                .url("https://kitsu.io/api/edge/manga?filter[text]=$encoded&page[limit]=3")
                .header("Accept", "application/vnd.api+json")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            val kitsuId = client.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyMap()
                val body = resp.body?.string() ?: return@withContext emptyMap()
                json.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("id").cleanString()
            } ?: return@withContext emptyMap()

            val chapReq = Request.Builder()
                .url("https://kitsu.io/api/edge/manga/$kitsuId/chapters?page[limit]=$limit")
                .header("Accept", "application/vnd.api+json")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(chapReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyMap()
                val body = resp.body?.string() ?: return@withContext emptyMap()
                val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray.orEmpty()
                val map = mutableMapOf<Int, Pair<Int?, String>>()
                for (node in data) {
                    val attrs = node.jsonObject["attributes"]?.jsonObject ?: continue
                    val chNum = attrs["number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: continue
                    val volNum = attrs["volumeNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val cTitle = attrs["canonicalTitle"].cleanString() ?: continue
                    map[chNum] = volNum to cTitle
                }
                map
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptyMap()
        }
    }

    suspend fun fetchMangaVolumesAndChapters(
        title: String,
        anilistId: Long? = null,
        malId: Long? = null,
        mangadexId: String? = null,
        knownTotalChapters: Int? = null,
        knownTotalVolumes: Int? = null,
        fallbackCoverUrl: String? = null
    ): MangaSeriesContent = withContext(Dispatchers.IO) {
        val cacheKey = "${anilistId ?: malId ?: mangadexId ?: title.lowercase().trim()}"
        mangaSeriesContentCache[cacheKey]?.let { return@withContext it }

        NetworkPacer.coalesce("manga_vols_chaps_$cacheKey") {
            mangaSeriesContentCache[cacheKey]?.let { return@coalesce it }

            val (mdPair, muInfo, kitsuChaps) = coroutineScope {
                val mdDef = async { resolveMangaDexManga(title, anilistId, malId, mangadexId) }
                val muDef = async { fetchMangaUpdatesInfo(title) }
                val kitsuDef = async { fetchKitsuMangaChapters(title, limit = 20) }
                Triple(mdDef.await(), muDef.await(), kitsuDef.await())
            }

            val resolvedMdId = mdPair?.first
            val mdAttrs = mdPair?.second?.get("attributes")?.jsonObject
            val mdLastCh = mdAttrs?.get("lastChapter")?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.toInt()
            val mdLastVol = mdAttrs?.get("lastVolume")?.jsonPrimitive?.contentOrNull?.toIntOrNull()

            val volumeCovers = mutableMapOf<Int, String>()
            data class RawFeedChap(val volNum: Int?, val chNum: Int, val title: String?, val date: String?, val pages: Int?)
            val feedChaps = mutableMapOf<Int, RawFeedChap>()

            if (resolvedMdId != null) {
                coroutineScope {
                    val covDef = async {
                        NetworkPacer.withMangaDexPacing {
                            try {
                                val req = Request.Builder()
                                    .url("https://api.mangadex.org/cover?manga[]=$resolvedMdId&limit=100&order[volume]=asc")
                                    .header("User-Agent", "OmniTrack/1.0")
                                    .get()
                                    .build()
                                client.newCall(req).execute().use { resp ->
                                    if (!resp.isSuccessful) return@withMangaDexPacing
                                    val body = resp.body?.string() ?: return@withMangaDexPacing
                                    val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray.orEmpty()
                                    for (node in data) {
                                        val attrs = node.jsonObject["attributes"]?.jsonObject ?: continue
                                        val volNum = attrs["volume"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.toInt()
                                            ?: continue
                                        val fileName = attrs["fileName"].cleanString() ?: continue
                                        if (volNum > 0 && !volumeCovers.containsKey(volNum)) {
                                            volumeCovers[volNum] = "https://uploads.mangadex.org/covers/$resolvedMdId/$fileName.256.jpg"
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                            }
                        }
                    }
                    val feedDef = async {
                        NetworkPacer.withMangaDexPacing {
                            try {
                                val req = Request.Builder()
                                    .url("https://api.mangadex.org/manga/$resolvedMdId/feed?translatedLanguage[]=en&limit=500&order[chapter]=asc")
                                    .header("User-Agent", "OmniTrack/1.0")
                                    .get()
                                    .build()
                                client.newCall(req).execute().use { resp ->
                                    if (!resp.isSuccessful) return@withMangaDexPacing
                                    val body = resp.body?.string() ?: return@withMangaDexPacing
                                    val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray.orEmpty()
                                    for (node in data) {
                                        val attrs = node.jsonObject["attributes"]?.jsonObject ?: continue
                                        val chNum = attrs["chapter"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.toInt()
                                            ?: continue
                                        if (chNum <= 0) continue
                                        val volNum = attrs["volume"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.toInt()
                                        val cTitle = attrs["title"].cleanString()
                                        val date = attrs["publishAt"].cleanString()?.take(10)
                                        val pages = attrs["pages"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                        val existing = feedChaps[chNum]
                                        if (existing == null || (existing.title.isNullOrBlank() && !cTitle.isNullOrBlank())) {
                                            feedChaps[chNum] = RawFeedChap(volNum ?: existing?.volNum, chNum, cTitle ?: existing?.title, date ?: existing?.date, pages ?: existing?.pages)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                            }
                        }
                    }
                    covDef.await()
                    feedDef.await()
                }
            }

            val totalChapters = maxOf(
                knownTotalChapters ?: 0,
                mdLastCh ?: 0,
                muInfo?.latestChapter ?: 0,
                feedChaps.keys.maxOrNull() ?: 0
            ).coerceIn(0, 2000)

            val totalVolCount = maxOf(
                volumeCovers.keys.maxOrNull() ?: 0,
                knownTotalVolumes ?: 0,
                mdLastVol ?: 0,
                muInfo?.totalVolumes ?: 0
            )

            val seasonsList = mutableListOf<com.omnitrack.app.data.model.MediaSeason>()
            val epMap = mutableMapOf<Int, MutableList<com.omnitrack.app.data.model.MediaEpisode>>()
            val useMuSeasons = totalVolCount < 2 && (muInfo?.seasons?.isNotEmpty() == true)

            if (totalChapters > 0) {
                if (useMuSeasons && muInfo != null) {
                    val muSeasons = muInfo.seasons.sortedBy { it.first }
                    for ((sNum, sLabel, range) in muSeasons) {
                        val validRangeEnd = range.last.coerceAtMost(totalChapters)
                        if (range.first > totalChapters) continue
                        val sCover = volumeCovers[sNum]
                        seasonsList.add(
                            com.omnitrack.app.data.model.MediaSeason(
                                seasonNumber = sNum,
                                name = sLabel,
                                episodeCount = (validRangeEnd - range.first + 1).coerceAtLeast(1),
                                posterUrl = sCover
                            )
                        )
                        val list = mutableListOf<com.omnitrack.app.data.model.MediaEpisode>()
                        for (ch in range.first..validRangeEnd) {
                            val feed = feedChaps[ch]
                            val kitsu = kitsuChaps[ch]
                            val chTitle = feed?.title ?: kitsu?.second ?: "Chapter $ch"
                            list.add(
                                com.omnitrack.app.data.model.MediaEpisode(
                                    id = ch.toLong(),
                                    episodeNumber = ch,
                                    seasonNumber = sNum,
                                    title = chTitle,
                                    overview = feed?.pages?.let { "$it pages" },
                                    stillUrl = sCover,
                                    airDate = feed?.date,
                                    runtimeMinutes = null
                                )
                            )
                        }
                        epMap[sNum] = list
                    }
                } else if (totalVolCount >= 2) {
                    val approxPerVol = (totalChapters.toDouble() / totalVolCount.toDouble()).coerceAtLeast(1.0)
                    for (vol in 1..totalVolCount) {
                        val vCover = volumeCovers[vol]
                        seasonsList.add(
                            com.omnitrack.app.data.model.MediaSeason(
                                seasonNumber = vol,
                                name = "Volume $vol",
                                episodeCount = 0,
                                posterUrl = vCover
                            )
                        )
                        epMap[vol] = mutableListOf()
                    }
                    for (ch in 1..totalChapters) {
                        val feed = feedChaps[ch]
                        val kitsu = kitsuChaps[ch]
                        val mappedVol = (feed?.volNum ?: kitsu?.first)
                            ?.takeIf { it in 1..totalVolCount }
                            ?: kotlin.math.ceil(ch / approxPerVol).toInt().coerceIn(1, totalVolCount)
                        val vCover = volumeCovers[mappedVol]
                        val chTitle = feed?.title ?: kitsu?.second ?: "Chapter $ch"
                        epMap[mappedVol]?.add(
                            com.omnitrack.app.data.model.MediaEpisode(
                                id = ch.toLong(),
                                episodeNumber = ch,
                                seasonNumber = mappedVol,
                                title = chTitle,
                                overview = feed?.pages?.let { "$it pages" },
                                stillUrl = vCover,
                                airDate = feed?.date,
                                runtimeMinutes = null
                            )
                        )
                    }
                    // Sync accurate chapter counts & drop empty tail volumes if any
                    for (idx in seasonsList.indices) {
                        val s = seasonsList[idx]
                        val cnt = epMap[s.seasonNumber]?.size ?: 0
                        seasonsList[idx] = s.copy(episodeCount = cnt)
                    }
                    seasonsList.removeAll { (epMap[it.seasonNumber]?.isEmpty()) == true }
                } else {
                    val sCover = volumeCovers[1]
                    seasonsList.add(
                        com.omnitrack.app.data.model.MediaSeason(
                            seasonNumber = 1,
                            name = "Chapters",
                            episodeCount = totalChapters,
                            posterUrl = sCover
                        )
                    )
                    val list = mutableListOf<com.omnitrack.app.data.model.MediaEpisode>()
                    for (ch in 1..totalChapters) {
                        val feed = feedChaps[ch]
                        val kitsu = kitsuChaps[ch]
                        val chTitle = feed?.title ?: kitsu?.second ?: "Chapter $ch"
                        list.add(
                            com.omnitrack.app.data.model.MediaEpisode(
                                id = ch.toLong(),
                                episodeNumber = ch,
                                seasonNumber = 1,
                                title = chTitle,
                                overview = feed?.pages?.let { "$it pages" },
                                stillUrl = sCover,
                                airDate = feed?.date,
                                runtimeMinutes = null
                            )
                        )
                    }
                    epMap[1] = list
                }
            }

            val result = MangaSeriesContent(
                seasons = seasonsList,
                episodesBySeason = epMap,
                isSeasonGrouped = useMuSeasons,
                resolvedMangaDexId = resolvedMdId,
                totalChapters = totalChapters.takeIf { it > 0 },
                publishers = muInfo?.publishers.orEmpty()
            )
            if (seasonsList.isNotEmpty() || totalChapters > 0) {
                mangaSeriesContentCache[cacheKey] = result
            }
            result
        }
    }

    suspend fun resolveWikidataNativeTitles(title: String): WikidataNativeTitles? = withContext(Dispatchers.IO) {
        val cleanQuery = stripNovelEditionSuffix(title)
        if (cleanQuery.isBlank()) return@withContext null
        val cacheKey = cleanQuery.lowercase()
        wikidataNativeCache[cacheKey]?.let { return@withContext it }

        NetworkPacer.coalesce("wikidata_$cacheKey") {
            wikidataNativeCache[cacheKey]?.let { return@coalesce it }
            try {
                val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
                val searchReq = Request.Builder()
                    .url("https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$encoded&language=en&limit=1&format=json")
                    .header("User-Agent", "OmniTrack/1.0 (Android)")
                    .get()
                    .build()
                val qid = jikanClient.newCall(searchReq).execute().use { resp ->
                    if (!resp.isSuccessful) return@coalesce null
                    val body = resp.body?.string() ?: return@coalesce null
                    json.parseToJsonElement(body).jsonObject["search"]?.jsonArray
                        ?.firstOrNull()?.jsonObject?.get("id").cleanString()
                } ?: return@coalesce null

                val entReq = Request.Builder()
                    .url("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$qid&props=labels&languages=zh|zh-hans|ko|ja&format=json")
                    .header("User-Agent", "OmniTrack/1.0 (Android)")
                    .get()
                    .build()
                jikanClient.newCall(entReq).execute().use { resp ->
                    if (!resp.isSuccessful) return@coalesce null
                    val body = resp.body?.string() ?: return@coalesce null
                    val labels = json.parseToJsonElement(body).jsonObject["entities"]?.jsonObject
                        ?.get(qid)?.jsonObject?.get("labels")?.jsonObject ?: return@coalesce null
                    val res = WikidataNativeTitles(
                        zh = labels["zh-hans"]?.jsonObject?.get("value").cleanString()
                            ?: labels["zh"]?.jsonObject?.get("value").cleanString(),
                        ko = labels["ko"]?.jsonObject?.get("value").cleanString(),
                        ja = labels["ja"]?.jsonObject?.get("value").cleanString()
                    )
                    wikidataNativeCache[cacheKey] = res
                    res
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }
    }

    private fun scoreRanobeSearchCandidate(
        query: String,
        baseQuery: String?,
        nativeTitle: String?,
        node: JsonObject
    ): Int? {
        val candNames = listOfNotNull(
            node["title"].cleanString(),
            node["romaji"].cleanString(),
            node["romaji_orig"].cleanString(),
            node["title_orig"].cleanString()
        )
        if (candNames.isEmpty()) return null

        val queriesToCheck = listOfNotNull(query, baseQuery, nativeTitle).distinct()
        val isMatch = queriesToCheck.any { q -> isNovelTitleMatch(q, candNames) }
        if (!isMatch) return null

        fun normSimple(s: String): String = stripNovelEditionSuffix(s).lowercase()
            .replace(Regex("""[^\p{L}\p{N}]"""), "")

        val qNorms = queriesToCheck.map(::normSimple).filter { it.isNotBlank() }
        var score = 0
        if (candNames.any { c -> normSimple(c) in qNorms }) {
            score += 1000
        }
        val queryLower = query.lowercase()
        val spinoffTerms = listOf(
            "guidebook", "character book", "special book", "recollection",
            "short story", "artbook", "fanbook", "anthology", "side stor"
        )
        val candJoinedLower = candNames.joinToString(" ").lowercase()
        if (spinoffTerms.any { term -> term in candJoinedLower && term !in queryLower }) {
            score -= 600
        }
        val cNumBooks = (node["c_num_books"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
            ?: (node["volumes"] as? JsonObject)?.get("count")?.let { it as? JsonPrimitive }?.contentOrNull?.toIntOrNull()
            ?: 0
        score += minOf(cNumBooks, 50) * 6
        return score
    }

    suspend fun fetchRanobeDbSeriesDetails(
        title: String,
        nativeTitle: String? = null
    ): RanobeDbSeriesDetails? = withContext(Dispatchers.IO) {
        val cleanQuery = stripNovelEditionSuffix(title)
        if (cleanQuery.isBlank()) return@withContext null
        val cacheKey = cleanQuery.lowercase()
        ranobeDbSeriesCache[cacheKey]?.let { return@withContext it }

        NetworkPacer.coalesce("ranobedb_$cacheKey") {
            ranobeDbSeriesCache[cacheKey]?.let { return@coalesce it }
            NetworkPacer.withGeneralPacing {
                try {
                    val subSeriesRegex = Regex(
                        """\s*[:\-–—]\s*(?:Year|Part|Act)\s*\d+.*$|\s*[:\-–—]\s*\d+-nensei.*$""",
                        RegexOption.IGNORE_CASE
                    )
                    val baseQuery = cleanQuery.replace(subSeriesRegex, "").trim()
                        .takeIf { it.isNotBlank() && it != cleanQuery }

                    fun searchSeriesCandidates(q: String): List<JsonObject> {
                        val encoded = URLEncoder.encode(q, "UTF-8")
                        val searchReq = Request.Builder()
                            .url("https://ranobedb.org/api/v0/series?q=$encoded&limit=8")
                            .header("User-Agent", "OmniTrack/1.0 (Android)")
                            .get()
                            .build()
                        return client.newCall(searchReq).execute().use { resp ->
                            if (!resp.isSuccessful) return emptyList()
                            val body = resp.body?.string() ?: return emptyList()
                            val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
                            val seriesArr = root["series"] as? JsonArray ?: return emptyList()
                            seriesArr.mapNotNull { it as? JsonObject }
                        }
                    }

                    var candidates = searchSeriesCandidates(cleanQuery)
                    var bestCandidate = candidates
                        .mapNotNull { node ->
                            scoreRanobeSearchCandidate(cleanQuery, baseQuery, nativeTitle, node)?.let { it to node }
                        }
                        .maxByOrNull { it.first }
                        ?.second

                    if (bestCandidate == null && baseQuery != null) {
                        candidates = searchSeriesCandidates(baseQuery)
                        bestCandidate = candidates
                            .mapNotNull { node ->
                                scoreRanobeSearchCandidate(cleanQuery, baseQuery, nativeTitle, node)?.let { it to node }
                            }
                            .maxByOrNull { it.first }
                            ?.second
                    }

                    val matchedSeriesId = (bestCandidate?.get("id") as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                        ?: return@withGeneralPacing null

                    val detailReq = Request.Builder()
                        .url("https://ranobedb.org/api/v0/series/$matchedSeriesId")
                        .header("User-Agent", "OmniTrack/1.0 (Android)")
                        .get()
                        .build()
                    client.newCall(detailReq).execute().use { resp ->
                        if (!resp.isSuccessful) return@withGeneralPacing null
                        val body = resp.body?.string() ?: return@withGeneralPacing null
                        val root = json.parseToJsonElement(body) as? JsonObject ?: return@withGeneralPacing null
                        val series = root["series"] as? JsonObject ?: return@withGeneralPacing null

                        val sTitle = series["title"].cleanString() ?: cleanQuery
                        val sNative = series["title_orig"].cleanString()
                        val sDesc = series["book_description"].cleanString() ?: series["description"].cleanString()
                        val alId = (series["anilist_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                        val malId = (series["mal_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                        val rating = (series["rating"] as? JsonObject)
                            ?.get("score")?.let { it as? JsonPrimitive }?.contentOrNull?.toFloatOrNull()
                            ?: (series["rating"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
                        val olang = series["olang"].cleanString()?.uppercase()

                        data class RawRanobeBook(
                            val bookId: Long?,
                            val uniqueKey: String,
                            val sortOrder: Int,
                            val origIdx: Int,
                            val title: String,
                            val titleOrig: String?,
                            val romajiOrig: String?,
                            val bookType: String?,
                            val coverUrl: String?,
                            val releaseDate: String?,
                            val synopsis: String?,
                            val label: String?
                        )

                        val rawBooks = (series["books"] as? JsonArray).orEmpty().mapIndexedNotNull { idx, elem ->
                            val b = elem as? JsonObject ?: return@mapIndexedNotNull null
                            val bookId = (b["id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                            val sortOrder = (b["sort_order"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: Int.MAX_VALUE
                            val bookType = b["book_type"].cleanString()
                            val titleOrig = b["title_orig"].cleanString()
                            val romajiOrig = b["romaji_orig"].cleanString()
                            val bTitle = b["title"].cleanString() ?: romajiOrig ?: titleOrig ?: return@mapIndexedNotNull null
                            val rawDate = (b["c_release_date"] as? JsonPrimitive)?.contentOrNull?.trim()
                            val formattedDate = if (rawDate != null && rawDate.length == 8 && rawDate.all { it.isDigit() }) {
                                "${rawDate.substring(0, 4)}-${rawDate.substring(4, 6)}-${rawDate.substring(6, 8)}"
                            } else null
                            val imgFile = (b["image"] as? JsonObject)?.get("filename").cleanString()
                            val covUrl = imgFile?.let { "https://images.ranobedb.org/$it" }
                            val bDesc = b["description"].cleanString()

                            val volMatch = Regex("""(?:Vol\.?|Volume)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(bTitle)
                                ?: romajiOrig?.let { Regex("""(?:Vol\.?|Volume)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(it) }
                                ?: Regex("""(?:\d+年生編|第)\s*(\d+(?:\.\d+)?)""").find(bTitle)
                                ?: titleOrig?.let { Regex("""(?:\d+年生編|第)\s*(\d+(?:\.\d+)?)""").find(it) }
                                ?: Regex("""\s(\d+(?:\.\d+)?)\s*$""").find(bTitle)
                            val label = volMatch?.groupValues?.getOrNull(1)?.let { "Vol. $it" }

                            RawRanobeBook(
                                bookId = bookId,
                                uniqueKey = bookId?.toString() ?: "$sortOrder:$bTitle",
                                sortOrder = sortOrder,
                                origIdx = idx,
                                title = bTitle,
                                titleOrig = titleOrig,
                                romajiOrig = romajiOrig,
                                bookType = bookType,
                                coverUrl = covUrl,
                                releaseDate = formattedDate,
                                synopsis = bDesc,
                                label = label
                            )
                        }
                            .sortedWith(compareBy<RawRanobeBook> { it.sortOrder }.thenBy { it.origIdx })
                            .distinctBy { it.uniqueKey }

                        val hasMainBooks = rawBooks.any { it.bookType.equals("main", ignoreCase = true) }
                        val filteredRawBooks = if (hasMainBooks) {
                            rawBooks.filter { it.bookType == null || it.bookType.equals("main", ignoreCase = true) }
                        } else {
                            rawBooks
                        }

                        val books = filteredRawBooks.mapIndexed { idx, rb ->
                            RanobeDbVolume(
                                number = idx + 1,
                                title = rb.title,
                                coverUrl = rb.coverUrl,
                                releaseDate = rb.releaseDate,
                                synopsis = rb.synopsis,
                                label = rb.label ?: "Vol. ${idx + 1}",
                                titleOrig = rb.titleOrig,
                                romajiOrig = rb.romajiOrig,
                                bookType = rb.bookType,
                                bookId = rb.bookId
                            )
                        }

                        val publishers = (series["publishers"] as? JsonArray).orEmpty().mapNotNull { elem ->
                            val p = elem as? JsonObject ?: return@mapNotNull null
                            p["romaji"].cleanString() ?: p["name"].cleanString()
                        }.distinct()

                        val staffSeen = mutableSetOf<String>()
                        val staffList = (series["staff"] as? JsonArray).orEmpty().mapNotNull { elem ->
                            val st = elem as? JsonObject ?: return@mapNotNull null
                            val roleType = st["role_type"].cleanString()?.lowercase() ?: "staff"
                            val roleLabel = when (roleType) {
                                "author" -> "Author"
                                "artist" -> "Illustrator"
                                "translator" -> "Translator"
                                "narrator" -> "Narrator"
                                else -> st["note"].cleanString() ?: "Staff"
                            }
                            val sName = st["romaji"].cleanString() ?: st["name"].cleanString() ?: return@mapNotNull null
                            val dedupeKey = "${sName.lowercase()}:$roleLabel"
                            if (!staffSeen.add(dedupeKey)) return@mapNotNull null
                            val stId = (st["staff_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
                            AnimeStaffItem(
                                staffId = stId,
                                name = sName,
                                nativeName = st["name"].cleanString(),
                                role = roleLabel,
                                imageUrl = null
                            )
                        }

                        val res = RanobeDbSeriesDetails(
                            seriesId = matchedSeriesId,
                            title = sTitle,
                            nativeTitle = sNative,
                            synopsis = sDesc,
                            anilistId = alId,
                            malId = malId,
                            rating = rating,
                            originCountry = olang,
                            volumes = books,
                            publishers = publishers,
                            staff = staffList
                        )
                        ranobeDbSeriesCache[cacheKey] = res
                        res
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    null
                }
            }
        }
    }

    fun getCachedRanobeBookDetail(bookId: Long): RanobeDbBookDetail? = ranobeDbBookCache[bookId]

    suspend fun fetchRanobeDbBookDetails(bookId: Long): RanobeDbBookDetail? = withContext(Dispatchers.IO) {
        if (bookId <= 0L) return@withContext null
        ranobeDbBookCache[bookId]?.let { return@withContext it }

        NetworkPacer.coalesce("ranobedb_book_$bookId") {
            ranobeDbBookCache[bookId]?.let { return@coalesce it }
            NetworkPacer.withGeneralPacing {
                try {
                    val req = Request.Builder()
                        .url("https://ranobedb.org/api/v0/book/$bookId")
                        .header("User-Agent", "OmniTrack/1.0 (Android)")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withGeneralPacing null
                        val body = resp.body?.string() ?: return@withGeneralPacing null
                        val root = json.parseToJsonElement(body) as? JsonObject ?: return@withGeneralPacing null
                        val book = root["book"] as? JsonObject ?: return@withGeneralPacing null

                        val title = book["title"].cleanString()
                            ?: book["romaji_orig"].cleanString()
                            ?: book["title_orig"].cleanString()
                            ?: return@withGeneralPacing null
                        val titleOrig = book["title_orig"].cleanString()
                        val synopsis = book["description"].cleanString()
                            ?: book["description_ja"].cleanString()

                        val ratingObj = book["rating"] as? JsonObject
                        val ratingScore = (ratingObj?.get("score") as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
                            ?: (book["rating"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
                        val ratingCount = (ratingObj?.get("count") as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

                        fun formatYyyymmdd(raw: String?): String? {
                            val trimmed = raw?.trim() ?: return null
                            if (trimmed.length == 8 && trimmed.all { it.isDigit() }) {
                                return "${trimmed.substring(0, 4)}-${trimmed.substring(4, 6)}-${trimmed.substring(6, 8)}"
                            }
                            return trimmed.takeIf { it.isNotBlank() }
                        }

                        val cReleaseDates = book["c_release_dates"] as? JsonObject
                        val jpDate = formatYyyymmdd((cReleaseDates?.get("ja") as? JsonPrimitive)?.contentOrNull)
                            ?: formatYyyymmdd((book["c_release_date"] as? JsonPrimitive)?.contentOrNull)
                        val enDate = formatYyyymmdd((cReleaseDates?.get("en") as? JsonPrimitive)?.contentOrNull)

                        var printPages: Int? = null
                        var digitalPages: Int? = null
                        var enIsbn: String? = null
                        var jaIsbn: String? = null
                        val links = mutableListOf<RanobeDbBookLink>()
                        val seenLinkUrls = mutableSetOf<String>()

                        fun addLink(label: String, rawUrl: String?) {
                            val u = rawUrl.cleanString() ?: return
                            if (u.startsWith("http") && seenLinkUrls.add(u)) {
                                links.add(RanobeDbBookLink(label = label, url = u))
                            }
                        }

                        (book["releases"] as? JsonArray).orEmpty().forEach { elem ->
                            val rel = elem as? JsonObject ?: return@forEach
                            val fmt = rel["format"].cleanString()?.lowercase()
                            val lang = rel["lang"].cleanString()?.lowercase()
                            val pages = (rel["pages"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.takeIf { it > 0 }
                            if (pages != null) {
                                when (fmt) {
                                    "print" -> if (printPages == null || lang == "en" || lang == "ja") printPages = pages
                                    "digital" -> if (digitalPages == null || lang == "en" || lang == "ja") digitalPages = pages
                                }
                            }
                            val isbn = rel["isbn13"].cleanString()
                            if (isbn != null) {
                                if (lang == "en" && enIsbn == null) enIsbn = isbn
                                else if (lang == "ja" && jaIsbn == null) jaIsbn = isbn
                                else if (jaIsbn == null) jaIsbn = isbn
                            }
                            addLink("BookWalker", rel["bookwalker"].cleanString())
                            addLink("Official", rel["website"].cleanString())
                            addLink("Amazon", rel["amazon"].cleanString())
                        }

                        val publishers = (book["publishers"] as? JsonArray).orEmpty().mapNotNull { elem ->
                            val p = elem as? JsonObject ?: return@mapNotNull null
                            p["romaji"].cleanString() ?: p["name"].cleanString()
                        }.distinct()

                        val staffSeen = mutableSetOf<String>()
                        val staffList = mutableListOf<AnimeStaffItem>()
                        (book["editions"] as? JsonArray).orEmpty().forEach { edElem ->
                            val ed = edElem as? JsonObject ?: return@forEach
                            (ed["staff"] as? JsonArray).orEmpty().forEach { stElem ->
                                val st = stElem as? JsonObject ?: return@forEach
                                val roleType = st["role_type"].cleanString()?.lowercase() ?: "staff"
                                val roleLabel = when (roleType) {
                                    "author" -> "Author"
                                    "artist" -> "Illustrator"
                                    "translator" -> "Translator"
                                    "narrator" -> "Narrator"
                                    else -> st["note"].cleanString() ?: "Staff"
                                }
                                val sName = st["romaji"].cleanString() ?: st["name"].cleanString() ?: return@forEach
                                if (!staffSeen.add("${sName.lowercase()}:$roleLabel")) return@forEach
                                val stId = (st["staff_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
                                staffList.add(
                                    AnimeStaffItem(
                                        staffId = stId,
                                        name = sName,
                                        nativeName = st["name"].cleanString(),
                                        role = roleLabel,
                                        imageUrl = null
                                    )
                                )
                            }
                        }

                        val detail = RanobeDbBookDetail(
                            bookId = bookId,
                            title = title,
                            titleOrig = titleOrig,
                            synopsis = synopsis,
                            ratingScore = ratingScore,
                            ratingCount = ratingCount,
                            jpReleaseDate = jpDate,
                            enReleaseDate = enDate,
                            printPages = printPages,
                            digitalPages = digitalPages,
                            isbn13 = enIsbn ?: jaIsbn,
                            publishers = publishers,
                            staff = staffList,
                            links = links.take(5)
                        )
                        ranobeDbBookCache[bookId] = detail
                        detail
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    null
                }
            }
        }
    }

    suspend fun fetchKitsuNovelDetails(title: String): KitsuNovelDetails? = withContext(Dispatchers.IO) {
        val cleanQuery = stripNovelEditionSuffix(title)
        if (cleanQuery.isBlank()) return@withContext null
        val cacheKey = cleanQuery.lowercase()
        kitsuNovelDetailsCache[cacheKey]?.let { return@withContext it }

        NetworkPacer.coalesce("kitsu_novel_$cacheKey") {
            kitsuNovelDetailsCache[cacheKey]?.let { return@coalesce it }
            NetworkPacer.withGeneralPacing {
                try {
                    val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
                    val req = Request.Builder()
                        .url("https://kitsu.io/api/edge/manga?filter[text]=$encoded&filter[subtype]=novel&include=categories&page[limit]=5")
                        .header("Accept", "application/vnd.api+json")
                        .header("User-Agent", "OmniTrack/1.0")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withGeneralPacing null
                        val body = resp.body?.string() ?: return@withGeneralPacing null
                        val root = json.parseToJsonElement(body).jsonObject
                        val data = root["data"]?.jsonArray.orEmpty()
                        val matchedNode = data.firstOrNull { elem ->
                            val attrs = elem.jsonObject["attributes"]?.jsonObject ?: return@firstOrNull false
                            val titles = attrs["titles"]?.jsonObject
                            isNovelTitleMatch(
                                cleanQuery,
                                listOf(
                                    attrs["canonicalTitle"].cleanString(),
                                    titles?.get("en").cleanString(),
                                    titles?.get("en_us").cleanString(),
                                    titles?.get("en_jp").cleanString(),
                                    titles?.get("ja_jp").cleanString()
                                )
                            )
                        }?.jsonObject ?: return@withGeneralPacing null

                        val kitsuId = matchedNode["id"].cleanString() ?: return@withGeneralPacing null
                        val attrs = matchedNode["attributes"]?.jsonObject ?: return@withGeneralPacing null
                        val cats = root["included"]?.jsonArray.orEmpty()
                            .filter { it.jsonObject["type"].cleanString() == "categories" }
                            .mapNotNull { it.jsonObject["attributes"]?.jsonObject?.get("title").cleanString() }
                            .distinct()

                        val res = KitsuNovelDetails(
                            kitsuId = kitsuId,
                            title = attrs["canonicalTitle"].cleanString() ?: cleanQuery,
                            synopsis = attrs["synopsis"].cleanString() ?: attrs["description"].cleanString(),
                            chapterCount = attrs["chapterCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.takeIf { it > 0 },
                            volumeCount = attrs["volumeCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.takeIf { it > 0 },
                            status = attrs["status"].cleanString(),
                            score = attrs["averageRating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.let { it / 10f },
                            posterUrl = attrs["posterImage"]?.jsonObject?.get("original").cleanString()
                                ?: attrs["posterImage"]?.jsonObject?.get("large").cleanString(),
                            bannerUrl = attrs["coverImage"]?.jsonObject?.get("original").cleanString()
                                ?: attrs["coverImage"]?.jsonObject?.get("large").cleanString(),
                            categories = cats
                        )
                        kitsuNovelDetailsCache[cacheKey] = res
                        res
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    null
                }
            }
        }
    }

    private data class BangumiNovelInfo(
        val bgmId: Long,
        val score: Float?,
        val volumes: Int?,
        val chapters: Int?,
        val synopsis: String?,
        val originCountry: String?,
        val publishers: List<String>
    )

    private suspend fun fetchBangumiNovelDetails(
        title: String,
        nativeTitle: String? = null,
        knownBgmId: Long? = null
    ): BangumiNovelInfo? = withContext(Dispatchers.IO) {
        try {
            var targetId = knownBgmId
            if (targetId == null) {
                val cleanQuery = stripNovelEditionSuffix(title)
                val staticAlias = mapOf(
                    "lord of the mysteries" to "诡秘之主",
                    "reverend insanity" to "蛊真人",
                    "omniscient reader" to "全知读者视角",
                    "omniscient reader's viewpoint" to "全知读者视角",
                    "coiling dragon" to "盘龙",
                    "solo leveling" to "나 혼자만 레벨업"
                )[cleanQuery.lowercase()]
                val queries = buildList {
                    nativeTitle.cleanString()?.let { add(it) }
                    staticAlias?.let { add(it) }
                    resolveWikidataNativeTitles(cleanQuery)?.allDistinct()?.let { addAll(it) }
                    add(cleanQuery)
                }.distinct()

                for (sq in queries) {
                    val encoded = URLEncoder.encode(sq, "UTF-8")
                    val req = Request.Builder()
                        .url("https://api.bgm.tv/search/subject/$encoded?type=1&responseGroup=small&max_results=5")
                        .header("User-Agent", "OmniTrack/1.0")
                        .get()
                        .build()
                    val found = NetworkPacer.withBangumiPacing {
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@withBangumiPacing null
                            val body = resp.body?.string() ?: return@withBangumiPacing null
                            val list = json.parseToJsonElement(body).jsonObject["list"]?.jsonArray.orEmpty()
                            val hit = list.firstOrNull { node ->
                                val o = node.jsonObject
                                isNovelTitleMatch(sq, listOf(o["name"].cleanString(), o["name_cn"].cleanString()))
                            }
                            hit?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        }
                    }
                    if (found != null) {
                        targetId = found
                        break
                    }
                }
            }
            if (targetId == null) return@withContext null

            NetworkPacer.withBangumiPacing {
                val req = Request.Builder()
                    .url("https://api.bgm.tv/v0/subjects/$targetId")
                    .header("User-Agent", "OmniTrack/1.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withBangumiPacing null
                    val body = resp.body?.string() ?: return@withBangumiPacing null
                    val root = json.parseToJsonElement(body).jsonObject
                    val score = root["rating"]?.jsonObject?.get("score")?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                    val volumes = root["volumes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.takeIf { it > 0 }
                    val chapters = root["total_episodes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.takeIf { it > 0 }
                    val summary = root["summary"].cleanString()
                    val nameOrig = root["name"].cleanString().orEmpty()
                    val nameCn = root["name_cn"].cleanString().orEmpty()
                    val origin = when {
                        Regex("[\\uAC00-\\uD7AF]").containsMatchIn(nameOrig) -> "KR"
                        Regex("[\\u3040-\\u30FF]").containsMatchIn(nameOrig) -> "JP"
                        nameOrig.isNotBlank() && nameOrig == nameCn -> "CN"
                        else -> null
                    }
                    val pubs = mutableListOf<String>()
                    val infobox = root["infobox"]?.jsonArray.orEmpty()
                    for (entry in infobox) {
                        val k = entry.jsonObject["key"].cleanString() ?: continue
                        if (k in listOf("出版社", "连载平台", "连载网站", "连载杂志", "文库")) {
                            val vEl = entry.jsonObject["value"]
                            if (vEl is kotlinx.serialization.json.JsonPrimitive) {
                                vEl.cleanString()?.let { pubs.add(it) }
                            } else if (vEl is kotlinx.serialization.json.JsonArray) {
                                vEl.forEach { it.jsonObject["v"].cleanString()?.let { s -> pubs.add(s) } }
                            }
                        }
                    }
                    BangumiNovelInfo(
                        bgmId = targetId,
                        score = score,
                        volumes = volumes,
                        chapters = chapters,
                        synopsis = summary,
                        originCountry = origin,
                        publishers = pubs.distinct()
                    )
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    private suspend fun fetchSyosetuChapterCount(title: String, nativeTitle: String? = null): Int? = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = stripNovelEditionSuffix(title)
            val jpQuery = when {
                !nativeTitle.isNullOrBlank() && Regex("[\\u3040-\\u30FF\\u4E00-\\u9FFF]").containsMatchIn(nativeTitle) -> nativeTitle
                else -> resolveWikidataNativeTitles(cleanQuery)?.ja
            } ?: return@withContext null

            val encoded = URLEncoder.encode(jpQuery, "UTF-8")
            val req = Request.Builder()
                .url("https://api.syosetu.com/novelapi/api/?out=json&lim=3&word=$encoded")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val arr = json.parseToJsonElement(body).jsonArray
                if (arr.size <= 1) return@withContext null
                val match = arr.drop(1).firstOrNull { node ->
                    isNovelTitleMatch(jpQuery, listOf(node.jsonObject["title"].cleanString()))
                } ?: return@withContext null
                match.jsonObject["general_all_no"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.takeIf { it > 0 }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    suspend fun fetchAniListAdaptationBridge(
        title: String,
        nativeTitle: String? = null
    ): AnimeEnrichedDetails? = withContext(Dispatchers.IO) {
        val cleanQuery = stripNovelEditionSuffix(title)
        if (cleanQuery.isBlank()) return@withContext null
        try {
            val wd = resolveWikidataNativeTitles(cleanQuery)
            val searchCandidates = listOfNotNull(cleanQuery, wd?.ja, nativeTitle, wd?.zh, wd?.ko).distinct()
            val gql = """
                query (${'$'}search: String) {
                  Page(page: 1, perPage: 6) {
                    media(search: ${'$'}search, sort: [POPULARITY_DESC]) {
                      id
                      type
                      format
                      title { english romaji native }
                      coverImage { extraLarge large }
                      bannerImage
                      startDate { year }
                    }
                  }
                }
            """.trimIndent()

            var matchedId: Long? = null
            var matchedType: MediaType = MediaType.ANIME
            var matchedTitle: String = cleanQuery
            var matchedFormat: String = "TV"
            var matchedCover: String? = null
            var matchedBanner: String? = null
            var matchedYear: Int? = null

            for (sq in searchCandidates) {
                val payload = buildJsonObject {
                    put("query", gql)
                    putJsonObject("variables") { put("search", sq) }
                }.toString()
                val req = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", "OmniTrack/1.0")
                    .build()
                val hit = NetworkPacer.withAniListPacing {
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withAniListPacing null
                        val body = resp.body?.string() ?: return@withAniListPacing null
                        val list = json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
                            ?.get("Page")?.jsonObject?.get("media")?.jsonArray.orEmpty()
                        list.firstOrNull { node ->
                            val o = node.jsonObject
                            val t = o["title"]?.jsonObject
                            isNovelTitleMatch(
                                cleanQuery,
                                listOf(t?.get("english").cleanString(), t?.get("romaji").cleanString(), t?.get("native").cleanString())
                            )
                        }?.jsonObject
                    }
                }
                if (hit != null) {
                    matchedId = hit["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    val tStr = hit["type"].cleanString() ?: "ANIME"
                    matchedFormat = hit["format"].cleanString() ?: "TV"
                    matchedType = if (tStr.equals("MANGA", true)) MediaType.MANGA else MediaType.ANIME
                    matchedTitle = hit["title"]?.jsonObject?.get("english").cleanString()
                        ?: hit["title"]?.jsonObject?.get("romaji").cleanString()
                        ?: cleanQuery
                    matchedCover = hit["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                        ?: hit["coverImage"]?.jsonObject?.get("large").cleanString()
                    matchedBanner = hit["bannerImage"].cleanString()
                    matchedYear = hit["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    break
                }
            }

            if (matchedId == null) return@withContext null
            val full = fetchAnimeDetails(anilistId = matchedId, mediaType = matchedType) ?: return@withContext null

            val authorOnlyStaff = full.staff.filter { st ->
                val r = st.role.lowercase()
                "story" in r || "original" in r || "creator" in r || "author" in r
            }
            val combinedRelations = buildList {
                if (matchedCover != null) {
                    add(
                        AnimeRelationItem(
                            id = matchedId,
                            title = matchedTitle,
                            relationType = "ADAPTATION",
                            format = matchedFormat,
                            coverUrl = matchedCover,
                            bannerUrl = matchedBanner,
                            year = matchedYear,
                            mediaType = matchedType
                        )
                    )
                }
                addAll(full.relations.filter { it.id != matchedId })
            }.distinctBy { it.id }

            full.copy(
                anilistId = null,
                malId = null,
                averageScore = null,
                totalEpisodes = null,
                totalChapters = null,
                totalVolumes = null,
                staff = authorOnlyStaff,
                relations = combinedRelations
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    suspend fun fetchNovelVolumesAndChapters(
        item: MediaItemEntity,
        knownTotalChapters: Int? = null,
        knownTotalVolumes: Int? = null
    ): NovelSeriesContent = withContext(Dispatchers.IO) {
        val cleanTitle = stripNovelEditionSuffix(item.title)
        val cacheKey = "novel:${item.anilistId ?: item.malId ?: item.bangumiId ?: "${item.platform ?: "AUTO"}:${cleanTitle.lowercase()}"}"
        novelSeriesContentCache[cacheKey]?.let { return@withContext it }

        NetworkPacer.coalesce("novel_series_$cacheKey") {
            novelSeriesContentCache[cacheKey]?.let { return@coalesce it }

            val (ranobe, kitsu, mu, bgm, syosetuCh) = coroutineScope {
                val rDef = async { fetchRanobeDbSeriesDetails(item.title, item.nativeTitle) }
                val kDef = async { fetchKitsuNovelDetails(item.title) }
                val mDef = async { fetchMangaUpdatesInfo(item.title, preferNovel = true) }
                val bDef = async { fetchBangumiNovelDetails(item.title, item.nativeTitle, item.bangumiId) }
                val sDef = async { fetchSyosetuChapterCount(item.title, item.nativeTitle) }
                val r = rDef.await()
                val k = kDef.await()
                val m = mDef.await()
                val b = bDef.await()
                val s = sDef.await()
                NovelBundle5(r, k, m, b, s)
            }

            val normPlatform = item.platform?.trim()?.uppercase()
            val lowerTitle = item.title.lowercase()
            val isLightNovel = when {
                normPlatform == "LIGHT_NOVEL" || "(light novel)" in lowerTitle || "(ln)" in lowerTitle -> true
                normPlatform == "WEB_NOVEL" || "(web novel)" in lowerTitle || "(wn)" in lowerTitle -> false
                ranobe != null && ranobe.volumes.isNotEmpty() && ((kitsu?.chapterCount ?: 0) <= 60) -> true
                else -> (item.anilistId != null || item.malId != null) &&
                    (item.originCountry == null || item.originCountry.equals("JP", true)) &&
                    ((item.totalProgress ?: 0.0) <= 80.0)
            }

            val seasonsList = mutableListOf<com.omnitrack.app.data.model.MediaSeason>()
            val epMap = mutableMapOf<Int, MutableList<com.omnitrack.app.data.model.MediaEpisode>>()
            var hasBookSplits = false
            val totalCount: Int?

            if (isLightNovel) {
                val allRanobeVols = ranobe?.volumes.orEmpty()
                val ranobeVols = if (allRanobeVols.isNotEmpty()) {
                    val titleBlob = "${item.title} ${item.nativeTitle.orEmpty()}"
                    val requestedYear = (
                        Regex("""(?:Year|Part)\s*(\d+)""", RegexOption.IGNORE_CASE).find(titleBlob)
                            ?: Regex("""(\d+)(?:年生編|-nensei)""", RegexOption.IGNORE_CASE).find(titleBlob)
                        )?.groupValues?.getOrNull(1)?.toIntOrNull()

                    fun bookMatchesYear(v: RanobeDbVolume, year: Int): Boolean {
                        val yrRegex = Regex("""(?:Year|Part)\s*$year\b|${year}年生編|${year}-nensei""", RegexOption.IGNORE_CASE)
                        return listOfNotNull(v.title, v.titleOrig, v.romajiOrig).any { yrRegex.containsMatchIn(it) }
                    }

                    fun bookHasLaterYear(v: RanobeDbVolume): Boolean {
                        val laterRegex = Regex("""(?:Year|Part)\s*[2-9]\b|[2-9]年生編|[2-9]-nensei""", RegexOption.IGNORE_CASE)
                        return listOfNotNull(v.title, v.titleOrig, v.romajiOrig).any { laterRegex.containsMatchIn(it) }
                    }

                    when {
                        requestedYear != null && requestedYear >= 2 -> {
                            allRanobeVols.filter { bookMatchesYear(it, requestedYear) }.ifEmpty { allRanobeVols }
                        }
                        allRanobeVols.any { bookHasLaterYear(it) } && allRanobeVols.any { !bookHasLaterYear(it) } -> {
                            val expectedTotal = knownTotalVolumes ?: item.totalProgress?.toInt() ?: 0
                            if (item.anilistId != null || item.malId != null || expectedTotal in 1 until allRanobeVols.size) {
                                allRanobeVols.filterNot { bookHasLaterYear(it) }.ifEmpty { allRanobeVols }
                            } else {
                                allRanobeVols
                            }
                        }
                        else -> allRanobeVols
                    }
                } else {
                    emptyList()
                }
                val targetVolCount = maxOf(
                    ranobeVols.size,
                    knownTotalVolumes ?: 0,
                    if ((item.totalProgress ?: 0.0) in 1.0..80.0) item.totalProgress?.toInt() ?: 0 else 0
                )
                totalCount = targetVolCount.takeIf { it > 0 }
                if (ranobeVols.isNotEmpty()) {
                    val volList = ranobeVols.mapIndexed { idx, v ->
                        val volNum = idx + 1
                        com.omnitrack.app.data.model.MediaEpisode(
                            id = volNum.toLong(),
                            episodeNumber = volNum,
                            seasonNumber = 1,
                            title = v.title,
                            overview = v.synopsis,
                            stillUrl = v.coverUrl,
                            airDate = v.releaseDate,
                            ranobeBookId = v.bookId
                        )
                    }.toMutableList()
                    seasonsList.add(
                        com.omnitrack.app.data.model.MediaSeason(
                            seasonNumber = 1,
                            name = "Volumes",
                            episodeCount = volList.size,
                            posterUrl = volList.firstOrNull { !it.stillUrl.isNullOrBlank() }?.stillUrl
                        )
                    )
                    epMap[1] = volList
                } else if (targetVolCount > 0) {
                    val volList = (1..targetVolCount).map { v ->
                        com.omnitrack.app.data.model.MediaEpisode(
                            id = v.toLong(),
                            episodeNumber = v,
                            seasonNumber = 1,
                            title = "Volume $v",
                            overview = null,
                            stillUrl = null,
                            airDate = null
                        )
                    }.toMutableList()
                    seasonsList.add(
                        com.omnitrack.app.data.model.MediaSeason(
                            seasonNumber = 1,
                            name = "Volumes",
                            episodeCount = volList.size,
                            posterUrl = null
                        )
                    )
                    epMap[1] = volList
                }
            } else {
                // WEB NOVEL: Genuine Books/Parts if present, else 1 continuous All Chapters stream (never 100-ch slices)
                val totalCh = maxOf(
                    knownTotalChapters ?: 0,
                    kitsu?.chapterCount ?: 0,
                    mu?.totalStatusChapters ?: 0,
                    mu?.latestChapter ?: 0,
                    bgm?.chapters ?: 0,
                    syosetuCh ?: 0,
                    item.totalProgress?.toInt() ?: 0
                ).coerceIn(0, 5000)
                totalCount = totalCh.takeIf { it > 0 }

                if (totalCh > 0) {
                    val bookCount = maxOf(
                        kitsu?.volumeCount ?: 0,
                        mu?.totalVolumes ?: 0,
                        bgm?.volumes ?: 0
                    )
                    if (mu?.seasons?.isNotEmpty() == true) {
                        hasBookSplits = true
                        for ((sNum, sLabel, range) in mu.seasons.sortedBy { it.first }) {
                            val end = range.last.coerceAtMost(totalCh)
                            if (range.first > totalCh) continue
                            seasonsList.add(
                                com.omnitrack.app.data.model.MediaSeason(
                                    seasonNumber = sNum,
                                    name = sLabel,
                                    episodeCount = (end - range.first + 1).coerceAtLeast(1),
                                    posterUrl = null
                                )
                            )
                            epMap[sNum] = (range.first..end).map { ch ->
                                com.omnitrack.app.data.model.MediaEpisode(
                                    id = ch.toLong(),
                                    episodeNumber = ch,
                                    seasonNumber = sNum,
                                    title = "Chapter $ch",
                                    overview = sLabel,
                                    stillUrl = null,
                                    airDate = null
                                )
                            }.toMutableList()
                        }
                    } else if (bookCount in 2..24 && totalCh >= bookCount * 5) {
                        hasBookSplits = true
                        val perBook = kotlin.math.ceil(totalCh.toDouble() / bookCount.toDouble()).toInt().coerceAtLeast(1)
                        var chCursor = 1
                        for (book in 1..bookCount) {
                            if (chCursor > totalCh) break
                            val end = if (book == bookCount) totalCh else (chCursor + perBook - 1).coerceAtMost(totalCh)
                            seasonsList.add(
                                com.omnitrack.app.data.model.MediaSeason(
                                    seasonNumber = book,
                                    name = "Book $book ($chCursor–$end)",
                                    episodeCount = (end - chCursor + 1).coerceAtLeast(1),
                                    posterUrl = null
                                )
                            )
                            epMap[book] = (chCursor..end).map { ch ->
                                com.omnitrack.app.data.model.MediaEpisode(
                                    id = ch.toLong(),
                                    episodeNumber = ch,
                                    seasonNumber = book,
                                    title = "Chapter $ch",
                                    overview = "Book $book",
                                    stillUrl = null,
                                    airDate = null
                                )
                            }.toMutableList()
                            chCursor = end + 1
                        }
                    } else {
                        seasonsList.add(
                            com.omnitrack.app.data.model.MediaSeason(
                                seasonNumber = 1,
                                name = "All Chapters",
                                episodeCount = totalCh,
                                posterUrl = null
                            )
                        )
                        epMap[1] = (1..totalCh).map { ch ->
                            com.omnitrack.app.data.model.MediaEpisode(
                                id = ch.toLong(),
                                episodeNumber = ch,
                                seasonNumber = 1,
                                title = "Chapter $ch",
                                overview = null,
                                stillUrl = null,
                                airDate = null
                            )
                        }.toMutableList()
                    }
                }
            }

            val publishers = buildList {
                if (isLightNovel) {
                    addAll(ranobe?.publishers.orEmpty())
                    addAll(mu?.publishers.orEmpty())
                    addAll(bgm?.publishers.orEmpty())
                } else {
                    addAll(mu?.publishers.orEmpty())
                    addAll(bgm?.publishers.orEmpty())
                    addAll(ranobe?.publishers.orEmpty())
                }
            }.distinct()

            val result = NovelSeriesContent(
                isLightNovel = isLightNovel,
                seasons = seasonsList,
                episodesBySeason = epMap,
                hasBookSplits = hasBookSplits,
                totalCount = totalCount,
                publishers = publishers,
                ranobeDetails = ranobe,
                kitsuDetails = kitsu,
                mangaUpdatesRating = mu?.bayesianRating,
                bangumiScore = bgm?.score,
                originCountry = ranobe?.originCountry ?: bgm?.originCountry ?: item.originCountry
            )
            if (seasonsList.isNotEmpty() || totalCount != null) {
                novelSeriesContentCache[cacheKey] = result
            }
            result
        }
    }

    private data class NovelBundle5(
        val ranobe: RanobeDbSeriesDetails?,
        val kitsu: KitsuNovelDetails?,
        val mu: MangaUpdatesSeriesInfo?,
        val bgm: BangumiNovelInfo?,
        val syosetuCh: Int?
    )

    data class NovelReviewItem(
        val author: String,
        val source: String,
        val rating: Float?,
        val comment: String,
        val isSpoiler: Boolean = false
    )

    suspend fun fetchKitsuNovelReactions(kitsuId: String): List<NovelReviewItem> = withContext(Dispatchers.IO) {
        if (kitsuId.isBlank()) return@withContext emptyList()
        NetworkPacer.withGeneralPacing {
            try {
                val req = Request.Builder()
                    .url("https://kitsu.io/api/edge/media-reactions?filter[manga_id]=$kitsuId&include=user&page[limit]=10")
                    .header("Accept", "application/vnd.api+json")
                    .header("User-Agent", "OmniTrack/1.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                    val body = resp.body?.string() ?: return@withGeneralPacing emptyList()
                    val root = json.parseToJsonElement(body).jsonObject
                    val usersById = root["included"]?.jsonArray.orEmpty()
                        .filter { it.jsonObject["type"].cleanString() == "users" }
                        .associate {
                            (it.jsonObject["id"].cleanString() ?: "") to
                                (it.jsonObject["attributes"]?.jsonObject?.get("name").cleanString() ?: "Kitsu Reader")
                        }
                    root["data"]?.jsonArray.orEmpty().mapNotNull { elem ->
                        val o = elem.jsonObject
                        val attrs = o["attributes"]?.jsonObject ?: return@mapNotNull null
                        val txt = attrs["reaction"].cleanString() ?: return@mapNotNull null
                        val uid = o["relationships"]?.jsonObject?.get("user")?.jsonObject
                            ?.get("data")?.jsonObject?.get("id").cleanString()
                        val author = uid?.let { usersById[it] } ?: "Kitsu Reader"
                        NovelReviewItem(author = author, source = "Kitsu", rating = null, comment = txt)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emptyList()
            }
        }
    }

    suspend fun fetchJikanMangaReviews(malId: Long): List<NovelReviewItem> = withContext(Dispatchers.IO) {
        if (malId <= 0L) return@withContext emptyList()
        NetworkPacer.withGeneralPacing {
            try {
                val req = Request.Builder()
                    .url("https://jikanfortheweebs.midnightignite.me/v4/manga/$malId/reviews?limit=10")
                    .header("User-Agent", "OmniTrack/1.0")
                    .get()
                    .build()
                jikanClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                    val body = resp.body?.string() ?: return@withGeneralPacing emptyList()
                    val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray.orEmpty()
                    data.take(10).mapNotNull { elem ->
                        val o = elem.jsonObject
                        val reviewText = o["review"].cleanString() ?: return@mapNotNull null
                        val author = o["user"]?.jsonObject?.get("username").cleanString() ?: "MAL Reader"
                        val score = o["score"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                        val spoiler = o["is_spoiler"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                        NovelReviewItem(author = author, source = "MAL", rating = score, comment = reviewText, isSpoiler = spoiler)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emptyList()
            }
        }
    }

    private val characterDetailCache = java.util.concurrent.ConcurrentHashMap<Long, AnimeCharacterDetail>()
    private val voiceActorDetailCache = java.util.concurrent.ConcurrentHashMap<Long, AnimeVoiceActorDetail>()

    private fun formatAniListDate(year: Int?, month: Int?, day: Int?): String? {
        if (year == null && month == null && day == null) return null
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val mStr = if (month != null && month in 1..12) months[month - 1] else null
        return buildList {
            mStr?.let { add(it) }
            day?.let { add(it.toString()) }
            year?.let { add(it.toString()) }
        }.joinToString(" ")
    }

    suspend fun fetchAnimeCharacterDetail(characterId: Long): AnimeCharacterDetail? = withContext(Dispatchers.IO) {
        characterDetailCache[characterId]?.let { return@withContext it }
        NetworkPacer.withAniListPacing {
            try {
                val query = """
                    query (${'$'}id: Int) {
                      Character(id: ${'$'}id) {
                        id
                        name {
                          full
                          native
                          alternative
                          userPreferred
                        }
                        image {
                          large
                          medium
                        }
                        description
                        gender
                        dateOfBirth {
                          year
                          month
                          day
                        }
                        age
                        bloodType
                        favourites
                        anime: media(type: ANIME, sort: [POPULARITY_DESC], page: 1, perPage: 25) {
                          edges {
                            characterRole
                            voiceActors(sort: [RELEVANCE, ID]) {
                              id
                              name {
                                full
                                native
                                userPreferred
                              }
                              image {
                                large
                                medium
                              }
                              languageV2
                            }
                            node {
                              id
                              idMal
                              title {
                                romaji
                                english
                                userPreferred
                              }
                              coverImage {
                                extraLarge
                                large
                              }
                              bannerImage
                              format
                              averageScore
                              startDate {
                                year
                              }
                            }
                          }
                        }
                        manga: media(type: MANGA, sort: [POPULARITY_DESC], page: 1, perPage: 25) {
                          edges {
                            characterRole
                            node {
                              id
                              idMal
                              title {
                                romaji
                                english
                                userPreferred
                              }
                              coverImage {
                                extraLarge
                                large
                              }
                              bannerImage
                              format
                              averageScore
                              startDate {
                                year
                              }
                            }
                          }
                        }
                      }
                    }
                """.trimIndent()

                val jsonPayload = buildJsonObject {
                    put("query", query)
                    put("variables", buildJsonObject { put("id", characterId) })
                }.toString()

                val req = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", "OmniTrack/1.0")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string() ?: return@use null
                    val root = json.parseToJsonElement(body).jsonObject
                    val charObj = root["data"]?.jsonObject?.get("Character")?.jsonObject ?: return@use null

                    val name = charObj["name"]?.jsonObject?.get("userPreferred").cleanString()
                        ?: charObj["name"]?.jsonObject?.get("full").cleanString() ?: "Unknown"
                    val nativeName = charObj["name"]?.jsonObject?.get("native").cleanString()
                    val altNames = charObj["name"]?.jsonObject?.get("alternative")?.jsonArray.orEmpty()
                        .mapNotNull { it.cleanString() }
                    val img = charObj["image"]?.jsonObject?.get("large").cleanString()
                        ?: charObj["image"]?.jsonObject?.get("medium").cleanString()
                    val bio = charObj["description"].cleanString()
                    val gender = charObj["gender"].cleanString()
                    val age = charObj["age"].cleanString()
                    val bloodType = charObj["bloodType"].cleanString()
                    val favourites = charObj["favourites"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

                    val dobObj = charObj["dateOfBirth"]?.let { if (it is JsonObject) it else null }
                    val dy = dobObj?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val dm = dobObj?.get("month")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val dd = dobObj?.get("day")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val dobStr = formatAniListDate(dy, dm, dd)

                    val vaMap = mutableMapOf<Long, AnimeCharacterVoiceActor>()
                    val appearancesList = mutableListOf<AnimeCharacterAppearance>()

                    val animeEdges = charObj["anime"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                    for (edgeElem in animeEdges) {
                        val edge = edgeElem.jsonObject
                        val charRole = edge["characterRole"].cleanString() ?: "SUPPORT"
                        val voiceActors = edge["voiceActors"]?.jsonArray.orEmpty()
                        for (vaElem in voiceActors) {
                            val va = vaElem.jsonObject
                            val vaId = va["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                            if (!vaMap.containsKey(vaId)) {
                                val vaName = va["name"]?.jsonObject?.get("userPreferred").cleanString()
                                    ?: va["name"]?.jsonObject?.get("full").cleanString() ?: "Voice Actor"
                                val vaNative = va["name"]?.jsonObject?.get("native").cleanString()
                                val vaImg = va["image"]?.jsonObject?.get("large").cleanString()
                                    ?: va["image"]?.jsonObject?.get("medium").cleanString()
                                val vaLang = va["languageV2"].cleanString() ?: "Japanese"
                                vaMap[vaId] = AnimeCharacterVoiceActor(
                                    staffId = vaId,
                                    name = vaName,
                                    nativeName = vaNative,
                                    imageUrl = vaImg,
                                    language = vaLang
                                )
                            }
                        }

                        val node = edge["node"]?.jsonObject ?: continue
                        val id = node["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                        val malId = node["idMal"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        val eng = node["title"]?.jsonObject?.get("english").cleanString()
                        val rom = node["title"]?.jsonObject?.get("romaji").cleanString()
                        val usr = node["title"]?.jsonObject?.get("userPreferred").cleanString()
                        val title = eng ?: usr ?: rom ?: continue
                        val cover = node["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                            ?: node["coverImage"]?.jsonObject?.get("large").cleanString() ?: ""
                        val banner = node["bannerImage"].cleanString()
                        val score = node["averageScore"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                        val year = node["startDate"]?.jsonObject?.get("year").cleanString()
                        val fmt = node["format"].cleanString() ?: "TV"

                        appearancesList.add(
                            AnimeCharacterAppearance(
                                id = id,
                                malId = malId,
                                title = title,
                                coverUrl = cover,
                                bannerUrl = banner,
                                mediaType = if (fmt.equals("MOVIE", ignoreCase = true)) MediaType.MOVIE else MediaType.ANIME,
                                format = fmt,
                                score = score?.let { it / 10f },
                                releaseYear = year,
                                characterRole = charRole
                            )
                        )
                    }

                    val mangaEdges = charObj["manga"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                    for (edgeElem in mangaEdges) {
                        val edge = edgeElem.jsonObject
                        val charRole = edge["characterRole"].cleanString() ?: "SUPPORT"
                        val node = edge["node"]?.jsonObject ?: continue
                        val id = node["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                        val malId = node["idMal"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        val eng = node["title"]?.jsonObject?.get("english").cleanString()
                        val rom = node["title"]?.jsonObject?.get("romaji").cleanString()
                        val usr = node["title"]?.jsonObject?.get("userPreferred").cleanString()
                        val title = eng ?: usr ?: rom ?: continue
                        val cover = node["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                            ?: node["coverImage"]?.jsonObject?.get("large").cleanString() ?: ""
                        val banner = node["bannerImage"].cleanString()
                        val score = node["averageScore"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                        val year = node["startDate"]?.jsonObject?.get("year").cleanString()
                        val fmt = node["format"].cleanString() ?: "MANGA"

                        appearancesList.add(
                            AnimeCharacterAppearance(
                                id = id,
                                malId = malId,
                                title = title,
                                coverUrl = cover,
                                bannerUrl = banner,
                                mediaType = if (fmt.equals("NOVEL", ignoreCase = true)) MediaType.NOVEL else MediaType.MANGA,
                                format = fmt,
                                score = score?.let { it / 10f },
                                releaseYear = year,
                                characterRole = charRole
                            )
                        )
                    }

                    val detail = AnimeCharacterDetail(
                        characterId = characterId,
                        name = name,
                        nativeName = nativeName,
                        alternativeNames = altNames,
                        imageUrl = img,
                        biography = bio,
                        gender = gender,
                        age = age,
                        dateOfBirth = dobStr,
                        bloodType = bloodType,
                        favourites = favourites,
                        appearances = appearancesList,
                        voiceActors = vaMap.values.toList()
                    )
                    characterDetailCache[characterId] = detail
                    detail
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun fetchVoiceActorDetails(staffId: Long): AnimeVoiceActorDetail? = withContext(Dispatchers.IO) {
        voiceActorDetailCache[staffId]?.let { return@withContext it }
        NetworkPacer.withAniListPacing {
            try {
                val query = """
                    query (${'$'}id: Int) {
                      Staff(id: ${'$'}id) {
                        id
                        name {
                          full
                          native
                          alternative
                        }
                        image {
                          large
                        }
                        description
                        age
                        dateOfBirth {
                          year
                          month
                          day
                        }
                        dateOfDeath {
                          year
                          month
                          day
                        }
                        gender
                        yearsActive
                        homeTown
                        bloodType
                        languageV2
                        favourites
                        primaryOccupations
                        characterMedia(sort: [FAVOURITES_DESC], page: 1, perPage: 25) {
                          edges {
                            characters {
                              id
                              name {
                                full
                              }
                              image {
                                large
                              }
                            }
                            characterRole
                            node {
                              id
                              title {
                                romaji
                                english
                                userPreferred
                              }
                              coverImage {
                                extraLarge
                                large
                              }
                              format
                              startDate {
                                year
                              }
                              type
                            }
                          }
                        }
                        anime: staffMedia(type: ANIME, sort: [POPULARITY_DESC], page: 1, perPage: 25) {
                          edges {
                            staffRole
                            node {
                              id
                              title {
                                romaji
                                english
                                userPreferred
                              }
                              coverImage {
                                extraLarge
                                large
                              }
                              format
                              startDate {
                                year
                              }
                              type
                            }
                          }
                        }
                        manga: staffMedia(type: MANGA, sort: [POPULARITY_DESC], page: 1, perPage: 25) {
                          edges {
                            staffRole
                            node {
                              id
                              title {
                                romaji
                                english
                                userPreferred
                              }
                              coverImage {
                                extraLarge
                                large
                              }
                              format
                              startDate {
                                year
                              }
                              type
                            }
                          }
                        }
                      }
                    }
                """.trimIndent()

                val jsonPayload = buildJsonObject {
                    put("query", query)
                    put("variables", buildJsonObject { put("id", staffId) })
                }.toString()

                val req = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", "OmniTrack/1.0")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string() ?: return@use null
                    val root = json.parseToJsonElement(body).jsonObject
                    val staff = root["data"]?.jsonObject?.get("Staff")?.jsonObject ?: return@use null

                    val sId = staff["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: staffId
                    val name = staff["name"]?.jsonObject?.get("full").cleanString() ?: "Unknown"
                    val nativeName = staff["name"]?.jsonObject?.get("native").cleanString()
                    val altNames = staff["name"]?.jsonObject?.get("alternative")?.jsonArray.orEmpty().mapNotNull { it.cleanString() }
                    val img = staff["image"]?.jsonObject?.get("large").cleanString()
                    val rawBio = staff["description"].cleanString()

                    val age = staff["age"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val dobObj = staff["dateOfBirth"]?.let { if (it is JsonObject) it else null }
                    val y = dobObj?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val m = dobObj?.get("month")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val d = dobObj?.get("day")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val dobStr = formatAniListDate(y, m, d)

                    val dodObj = staff["dateOfDeath"]?.let { if (it is JsonObject) it else null }
                    val yD = dodObj?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val mD = dodObj?.get("month")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val dD = dodObj?.get("day")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val dodStr = formatAniListDate(yD, mD, dD)
                    val gender = staff["gender"].cleanString()
                    val home = staff["homeTown"].cleanString()
                    val blood = staff["bloodType"].cleanString()
                    val lang = staff["languageV2"].cleanString()
                    val favs = staff["favourites"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val occupations = staff["primaryOccupations"]?.jsonArray.orEmpty().mapNotNull { it.cleanString() }

                    val yearsList = staff["yearsActive"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull?.toIntOrNull() }
                    val yearsStr = when {
                        yearsList.size >= 2 -> "${yearsList.first()} – ${yearsList.last()}"
                        yearsList.size == 1 -> "${yearsList.first()} – present"
                        else -> null
                    }

                    // 1. Voiced Character Roles
                    val rolesList = mutableListOf<VoiceActorRoleItem>()
                    val edges = staff["characterMedia"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                    for (edgeElem in edges) {
                        val edge = edgeElem.jsonObject
                        val role = edge["characterRole"].cleanString() ?: "SUPPORT"
                        val charObj = edge["characters"]?.jsonArray?.firstOrNull()?.jsonObject
                        val charId = charObj?.get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                        val charName = charObj["name"]?.jsonObject?.get("full").cleanString() ?: "Character"
                        val charImg = charObj["image"]?.jsonObject?.get("large").cleanString()

                        val mediaNode = edge["node"]?.jsonObject ?: continue
                        val mId = mediaNode["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                        val eng = mediaNode["title"]?.jsonObject?.get("english").cleanString()
                        val rom = mediaNode["title"]?.jsonObject?.get("romaji").cleanString()
                        val usr = mediaNode["title"]?.jsonObject?.get("userPreferred").cleanString()
                        val mTitle = eng ?: usr ?: rom ?: "Media"
                        val mCover = mediaNode["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                            ?: mediaNode["coverImage"]?.jsonObject?.get("large").cleanString()
                        val mFormat = mediaNode["format"].cleanString() ?: "TV"
                        val mYear = mediaNode["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        val mTypeStr = mediaNode["type"].cleanString() ?: "ANIME"
                        val mType = if (mTypeStr.equals("MANGA", ignoreCase = true)) {
                            if (mFormat.equals("NOVEL", ignoreCase = true)) MediaType.NOVEL else MediaType.MANGA
                        } else {
                            if (mFormat.equals("MOVIE", ignoreCase = true)) MediaType.MOVIE else MediaType.ANIME
                        }

                        rolesList.add(
                            VoiceActorRoleItem(
                                characterId = charId,
                                characterName = charName,
                                characterImageUrl = charImg,
                                characterRole = role,
                                mediaId = mId,
                                mediaTitle = mTitle,
                                mediaCoverUrl = mCover,
                                mediaFormat = mFormat,
                                mediaYear = mYear,
                                mediaType = mType
                            )
                        )
                    }

                    // 2. Production Staff Credits (Anime & Manga)
                    val productionCredits = mutableListOf<StaffProductionCreditItem>()
                    val animeProdEdges = staff["anime"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                    val mangaProdEdges = staff["manga"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                    for (edgeElem in (animeProdEdges + mangaProdEdges)) {
                        val edge = edgeElem.jsonObject
                        val staffRole = edge["staffRole"].cleanString() ?: continue
                        val mediaNode = edge["node"]?.jsonObject ?: continue
                        val mId = mediaNode["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                        val eng = mediaNode["title"]?.jsonObject?.get("english").cleanString()
                        val rom = mediaNode["title"]?.jsonObject?.get("romaji").cleanString()
                        val usr = mediaNode["title"]?.jsonObject?.get("userPreferred").cleanString()
                        val mTitle = eng ?: usr ?: rom ?: "Media"
                        val mCover = mediaNode["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                            ?: mediaNode["coverImage"]?.jsonObject?.get("large").cleanString()
                        val mFormat = mediaNode["format"].cleanString() ?: "TV"
                        val mYear = mediaNode["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        val mTypeStr = mediaNode["type"].cleanString() ?: "ANIME"
                        val mType = if (mTypeStr.equals("MANGA", ignoreCase = true)) {
                            if (mFormat.equals("NOVEL", ignoreCase = true)) MediaType.NOVEL else MediaType.MANGA
                        } else {
                            if (mFormat.equals("MOVIE", ignoreCase = true)) MediaType.MOVIE else MediaType.ANIME
                        }

                        productionCredits.add(
                            StaffProductionCreditItem(
                                mediaId = mId,
                                mediaTitle = mTitle,
                                mediaCoverUrl = mCover,
                                mediaFormat = mFormat,
                                mediaYear = mYear,
                                staffRole = staffRole,
                                mediaType = mType
                            )
                        )
                    }

                    val detail = AnimeVoiceActorDetail(
                        staffId = sId,
                        name = name,
                        nativeName = nativeName,
                        alternativeNames = altNames,
                        imageUrl = img,
                        biography = rawBio,
                        age = age,
                        dateOfBirth = dobStr,
                        dateOfDeath = dodStr,
                        homeTown = home,
                        primaryOccupations = occupations,
                        roles = rolesList,
                        gender = gender,
                        yearsActive = yearsStr,
                        bloodType = blood,
                        language = lang,
                        favourites = favs,
                        productionCredits = productionCredits
                    )
                    voiceActorDetailCache[staffId] = detail
                    detail
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun fetchAniListUserProfile(
        username: String? = null,
        userId: Int? = null,
        userAuthToken: String? = null
    ): UserProfileData? = withContext(Dispatchers.IO) {
        if (username.isNullOrBlank() && (userId == null || userId <= 0)) return@withContext null

        val query = """
            query (${'$'}name: String, ${'$'}id: Int) {
              User(name: ${'$'}name, id: ${'$'}id) {
                id
                name
                about(asHtml: false)
                avatar {
                  large
                }
                bannerImage
                isFollowing
                isFollower
                stats {
                  activityHistory {
                    date
                    amount
                    level
                  }
                }
                statistics {
                  anime {
                    count
                    meanScore
                    standardDeviation
                    minutesWatched
                    episodesWatched
                    scores(limit: 30) { count meanScore minutesWatched chaptersRead score }
                    formats(limit: 20) { count meanScore minutesWatched chaptersRead format }
                    statuses(limit: 10) { count meanScore minutesWatched chaptersRead status }
                    lengths(limit: 20) { count meanScore minutesWatched chaptersRead length }
                    releaseYears(limit: 30) { count meanScore minutesWatched chaptersRead releaseYear }
                    startYears(limit: 30) { count meanScore minutesWatched chaptersRead startYear }
                    genres(limit: 30) { count meanScore minutesWatched chaptersRead genre }
                    tags(limit: 30) { count meanScore minutesWatched chaptersRead tag { id name } }
                    countries(limit: 15) { count meanScore minutesWatched chaptersRead country }
                    voiceActors(limit: 25) { count meanScore minutesWatched chaptersRead voiceActor { id name { userPreferred full } } }
                    staff(limit: 25) { count meanScore minutesWatched chaptersRead staff { id name { userPreferred full } } }
                    studios(limit: 25) { count meanScore minutesWatched chaptersRead studio { id name } }
                  }
                  manga {
                    count
                    meanScore
                    standardDeviation
                    chaptersRead
                    volumesRead
                    scores(limit: 30) { count meanScore minutesWatched chaptersRead score }
                    formats(limit: 20) { count meanScore minutesWatched chaptersRead format }
                    statuses(limit: 10) { count meanScore minutesWatched chaptersRead status }
                    releaseYears(limit: 30) { count meanScore minutesWatched chaptersRead releaseYear }
                    startYears(limit: 30) { count meanScore minutesWatched chaptersRead startYear }
                    genres(limit: 30) { count meanScore minutesWatched chaptersRead genre }
                    tags(limit: 30) { count meanScore minutesWatched chaptersRead tag { id name } }
                    countries(limit: 15) { count meanScore minutesWatched chaptersRead country }
                    staff(limit: 25) { count meanScore minutesWatched chaptersRead staff { id name { userPreferred full } } }
                  }
                }
                favourites {
                  anime {
                    nodes {
                      id
                      title { userPreferred english romaji }
                      coverImage { large extraLarge }
                      format
                    }
                  }
                  manga {
                    nodes {
                      id
                      title { userPreferred english romaji }
                      coverImage { large extraLarge }
                      format
                    }
                  }
                  characters {
                    nodes {
                      id
                      name { full }
                      image { large }
                    }
                  }
                  staff {
                    nodes {
                      id
                      name { full }
                      image { large }
                    }
                  }
                }
              }
              Page(page: 1, perPage: 25) {
                activities(userId: ${'$'}id, sort: ID_DESC) {
                  ... on ListActivity {
                    id
                    type
                    status
                    progress
                    createdAt
                    media {
                      id
                      type
                      title { userPreferred english }
                      coverImage { medium large }
                    }
                  }
                  ... on TextActivity {
                    id
                    type
                    text(asHtml: false)
                    createdAt
                  }
                  ... on MessageActivity {
                    id
                    type
                    message(asHtml: false)
                    createdAt
                    messenger { name }
                  }
                }
              }
            }
        """.trimIndent()

        val vars = buildJsonObject {
            if (userId != null && userId > 0) put("id", userId)
            else if (!username.isNullOrBlank()) put("name", username)
        }

        try {
            val jsonPayload = buildJsonObject {
                put("query", query)
                put("variables", vars)
            }.toString()

            val reqBuilder = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "OmniTrack/1.0")

            if (!userAuthToken.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $userAuthToken")
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val root = json.parseToJsonElement(body).jsonObject
                val userObj = root["data"]?.jsonObject?.get("User")?.jsonObject ?: return@use null

                val id = userObj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@use null
                val name = userObj["name"].cleanString() ?: "User"
                val about = userObj["about"].cleanString()
                val avatar = userObj["avatar"]?.jsonObject?.get("large").cleanString()
                val banner = userObj["bannerImage"].cleanString()
                val isFollowing = userObj["isFollowing"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                val isFollower = userObj["isFollower"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

                val stats = userObj["statistics"]?.jsonObject
                val aStats = stats?.get("anime")?.jsonObject
                val aCount = aStats?.get("count")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val aMean = aStats?.get("meanScore")?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
                val aMins = aStats?.get("minutesWatched")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                val aDays = aMins / (60.0 * 24.0)
                val aEps = aStats?.get("episodesWatched")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

                val mStats = stats?.get("manga")?.jsonObject
                val mCount = mStats?.get("count")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val mMean = mStats?.get("meanScore")?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
                val mChaps = mStats?.get("chaptersRead")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

                val genresList = mutableListOf<Pair<String, Int>>()
                aStats?.get("genres")?.jsonArray.orEmpty().forEach { gElem ->
                    val gObj = gElem.jsonObject
                    val gName = gObj["genre"].cleanString()
                    val gCnt = gObj["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    if (gName != null) genresList.add(gName to gCnt)
                }

                val scoreMap = mutableMapOf<Int, Int>()
                for (i in 1..10) scoreMap[i] = 0
                aStats?.get("scores")?.jsonArray.orEmpty().forEach { sElem ->
                    val sObj = sElem.jsonObject
                    val sVal = sObj["score"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    val sCnt = sObj["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    if (sVal > 0 && sCnt > 0) {
                        val norm = if (sVal > 10) (sVal / 10).coerceIn(1, 10) else sVal.coerceIn(1, 10)
                        scoreMap[norm] = (scoreMap[norm] ?: 0) + sCnt
                    }
                }
                val scoreList = scoreMap.toList().sortedBy { it.first }

                val statusList = mutableListOf<Pair<String, Int>>()
                aStats?.get("statuses")?.jsonArray.orEmpty().forEach { sElem ->
                    val sObj = sElem.jsonObject
                    val sName = sObj["status"].cleanString() ?: return@forEach
                    val sCnt = sObj["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    statusList.add(sName to sCnt)
                }

                val formatList = mutableListOf<Pair<String, Int>>()
                aStats?.get("formats")?.jsonArray.orEmpty().forEach { fElem ->
                    val fObj = fElem.jsonObject
                    val fName = fObj["format"].cleanString() ?: return@forEach
                    val fCnt = fObj["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    formatList.add(fName to fCnt)
                }

                val actList = mutableListOf<com.omnitrack.app.data.model.UserActivityItem>()
                val activitiesJson = root["data"]?.jsonObject?.get("Page")?.jsonObject?.get("activities")?.jsonArray.orEmpty()
                activitiesJson.forEach { aElem ->
                    val aObj = aElem.jsonObject
                    val aId = aObj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@forEach
                    val aType = aObj["type"].cleanString() ?: "ACTIVITY"
                    val aCreated = aObj["createdAt"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                    val diff = (System.currentTimeMillis() / 1000) - aCreated
                    val timeAgo = when {
                        diff < 60 -> "Just now"
                        diff < 3600 -> "${diff / 60}m ago"
                        diff < 86400 -> "${diff / 3600}h ago"
                        diff < 604800 -> "${diff / 86400}d ago"
                        else -> "${diff / 604800}w ago"
                    }
                    val mObj = aObj["media"]?.jsonObject
                    val mId = mObj?.get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    val mTypeStr = mObj?.get("type").cleanString()
                    val mType = if (mTypeStr.equals("MANGA", ignoreCase = true)) com.omnitrack.app.data.model.MediaType.MANGA else com.omnitrack.app.data.model.MediaType.ANIME
                    val mTitle = mObj?.get("title")?.jsonObject?.get("userPreferred").cleanString()
                        ?: mObj?.get("title")?.jsonObject?.get("english").cleanString()
                    val mCover = mObj?.get("coverImage")?.jsonObject?.get("medium").cleanString()
                        ?: mObj?.get("coverImage")?.jsonObject?.get("large").cleanString()
                    val status = aObj["status"].cleanString()
                    val progress = aObj["progress"].cleanString()
                    val textBody = aObj["text"].cleanString() ?: aObj["message"].cleanString()

                    val text = when {
                        !status.isNullOrBlank() && !progress.isNullOrBlank() -> "${status.replaceFirstChar { it.uppercase() }} $progress of ${mTitle ?: "media"}"
                        !status.isNullOrBlank() -> "${status.replaceFirstChar { it.uppercase() }} ${mTitle ?: "media"}"
                        !textBody.isNullOrBlank() -> textBody
                        else -> "Updated library entry"
                    }
                    actList.add(com.omnitrack.app.data.model.UserActivityItem(id = aId, type = aType, mediaTitle = mTitle, coverUrl = mCover, mediaId = mId, mediaType = mType, text = text, timeAgo = timeAgo))
                }

                // If opened by username without userId, fetch activities using the resolved user ID
                if (userId == null && id > 0) {
                    try {
                        val actQuery = """
                            query (${'$'}uid: Int) {
                              Page(page: 1, perPage: 25) {
                                activities(userId: ${'$'}uid, sort: ID_DESC) {
                                  ... on ListActivity {
                                    id
                                    type
                                    status
                                    progress
                                    createdAt
                                    media {
                                      id
                                      type
                                      title { userPreferred english }
                                      coverImage { medium large }
                                    }
                                  }
                                  ... on TextActivity {
                                    id
                                    type
                                    text(asHtml: false)
                                    createdAt
                                  }
                                  ... on MessageActivity {
                                    id
                                    type
                                    message(asHtml: false)
                                    createdAt
                                    messenger { name }
                                  }
                                }
                              }
                            }
                        """.trimIndent()
                        val actPayload = buildJsonObject {
                            put("query", actQuery)
                            put("variables", buildJsonObject { put("uid", id) })
                        }.toString()
                        val actReq = Request.Builder()
                            .url("https://graphql.anilist.co")
                            .post(actPayload.toRequestBody("application/json".toMediaType()))
                            .header("User-Agent", "OmniTrack/1.0")
                            .build()
                        client.newCall(actReq).execute().use { actResp ->
                            if (actResp.isSuccessful) {
                                val actBody = actResp.body?.string()
                                if (!actBody.isNullOrBlank()) {
                                    val actRoot = json.parseToJsonElement(actBody).jsonObject
                                    val newActs = actRoot["data"]?.jsonObject?.get("Page")?.jsonObject?.get("activities")?.jsonArray.orEmpty()
                                    if (newActs.isNotEmpty()) {
                                        actList.clear()
                                        newActs.forEach { aElem ->
                                            val aObj = aElem.jsonObject
                                            val aId = aObj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@forEach
                                            val aType = aObj["type"].cleanString() ?: "ACTIVITY"
                                            val aCreated = aObj["createdAt"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                                            val diff = (System.currentTimeMillis() / 1000) - aCreated
                                            val timeAgo = when {
                                                diff < 60 -> "Just now"
                                                diff < 3600 -> "${diff / 60}m ago"
                                                diff < 86400 -> "${diff / 3600}h ago"
                                                diff < 604800 -> "${diff / 86400}d ago"
                                                else -> "${diff / 604800}w ago"
                                            }
                                            val mObj = aObj["media"]?.jsonObject
                                            val mId = mObj?.get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                            val mTypeStr = mObj?.get("type").cleanString()
                                            val mType = if (mTypeStr.equals("MANGA", ignoreCase = true)) com.omnitrack.app.data.model.MediaType.MANGA else com.omnitrack.app.data.model.MediaType.ANIME
                                            val mTitle = mObj?.get("title")?.jsonObject?.get("userPreferred").cleanString()
                                                ?: mObj?.get("title")?.jsonObject?.get("english").cleanString()
                                            val mCover = mObj?.get("coverImage")?.jsonObject?.get("medium").cleanString()
                                                ?: mObj?.get("coverImage")?.jsonObject?.get("large").cleanString()
                                            val status = aObj["status"].cleanString()
                                            val progress = aObj["progress"].cleanString()
                                            val textBody = aObj["text"].cleanString() ?: aObj["message"].cleanString()

                                            val text = when {
                                                !status.isNullOrBlank() && !progress.isNullOrBlank() -> "${status.replaceFirstChar { it.uppercase() }} $progress of ${mTitle ?: "media"}"
                                                !status.isNullOrBlank() -> "${status.replaceFirstChar { it.uppercase() }} ${mTitle ?: "media"}"
                                                !textBody.isNullOrBlank() -> textBody
                                                else -> "Updated library entry"
                                            }
                                            actList.add(com.omnitrack.app.data.model.UserActivityItem(id = aId, type = aType, mediaTitle = mTitle, coverUrl = mCover, mediaId = mId, mediaType = mType, text = text, timeAgo = timeAgo))
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // ignore and use whatever is in actList
                    }
                }

                val favs = userObj["favourites"]?.jsonObject
                val favAnime = mutableListOf<UserFavoriteMedia>()
                favs?.get("anime")?.jsonObject?.get("nodes")?.jsonArray.orEmpty().forEach { nElem ->
                    val n = nElem.jsonObject
                    val mId = n["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@forEach
                    val tObj = n["title"]?.jsonObject
                    val t = tObj?.get("userPreferred").cleanString() ?: tObj?.get("english").cleanString() ?: tObj?.get("romaji").cleanString() ?: "Anime"
                    val c = n["coverImage"]?.jsonObject?.get("large").cleanString() ?: ""
                    val fmt = n["format"].cleanString()
                    favAnime.add(UserFavoriteMedia(id = mId, title = t, coverUrl = c, format = fmt, mediaType = MediaType.ANIME))
                }

                val favManga = mutableListOf<UserFavoriteMedia>()
                favs?.get("manga")?.jsonObject?.get("nodes")?.jsonArray.orEmpty().forEach { nElem ->
                    val n = nElem.jsonObject
                    val mId = n["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@forEach
                    val tObj = n["title"]?.jsonObject
                    val t = tObj?.get("userPreferred").cleanString() ?: tObj?.get("english").cleanString() ?: tObj?.get("romaji").cleanString() ?: "Manga"
                    val c = n["coverImage"]?.jsonObject?.get("large").cleanString() ?: ""
                    val fmt = n["format"].cleanString()
                    favManga.add(UserFavoriteMedia(id = mId, title = t, coverUrl = c, format = fmt, mediaType = MediaType.MANGA))
                }

                val favChars = mutableListOf<UserFavoriteCharacter>()
                favs?.get("characters")?.jsonObject?.get("nodes")?.jsonArray.orEmpty().forEach { nElem ->
                    val n = nElem.jsonObject
                    val cId = n["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@forEach
                    val cName = n["name"]?.jsonObject?.get("full").cleanString() ?: "Character"
                    val cImg = n["image"]?.jsonObject?.get("large").cleanString()
                    favChars.add(UserFavoriteCharacter(id = cId, name = cName, imageUrl = cImg))
                }

                val favStaff = mutableListOf<UserFavoriteStaff>()
                favs?.get("staff")?.jsonObject?.get("nodes")?.jsonArray.orEmpty().forEach { nElem ->
                    val n = nElem.jsonObject
                    val sId = n["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@forEach
                    val sName = n["name"]?.jsonObject?.get("full").cleanString() ?: "Staff"
                    val sImg = n["image"]?.jsonObject?.get("large").cleanString()
                    favStaff.add(UserFavoriteStaff(id = sId, name = sName, imageUrl = sImg))
                }

                val historyList = mutableListOf<UserActivityDay>()
                val histArray = userObj["stats"]?.jsonObject?.get("activityHistory")?.jsonArray
                histArray.orEmpty().forEach { hElem ->
                    val h = hElem.jsonObject
                    val date = h["date"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                    val amount = h["amount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    val level = h["level"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    historyList.add(UserActivityDay(dateEpochSeconds = date, amount = amount, level = level))
                }

                val animeStats = parseUserStatSet(aStats)
                val mangaStats = parseUserStatSet(mStats)

                UserProfileData(
                    userId = id,
                    username = name,
                    avatarUrl = avatar,
                    bannerUrl = banner,
                    bio = about,
                    isFollowing = isFollowing,
                    isFollower = isFollower,
                    animeCount = aCount,
                    episodesWatched = aEps,
                    animeDaysWatched = aDays,
                    animeMeanScore = aMean,
                    mangaCount = mCount,
                    chaptersRead = mChaps,
                    mangaMeanScore = mMean,
                    topGenres = genresList,
                    scoreDistribution = scoreList,
                    statusBreakdown = statusList,
                    formatBreakdown = formatList,
                    activities = actList,
                    activityHistory = historyList,
                    animeStats = animeStats,
                    mangaStats = mangaStats,
                    favoriteAnime = favAnime,
                    favoriteManga = favManga,
                    favoriteCharacters = favChars,
                    favoriteStaff = favStaff
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseUserStatSet(json: JsonObject?): UserStatSet {
        if (json == null) return UserStatSet()
        val count = json["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val meanScore = json["meanScore"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
        val stdDev = json["standardDeviation"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
        val minutesWatched = json["minutesWatched"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val eps = json["episodesWatched"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val chaps = json["chaptersRead"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val vols = json["volumesRead"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

        val catMap = mutableMapOf<String, List<UserStatEntry>>()
        val categoryKeys = listOf(
            "scores" to "score",
            "formats" to "format",
            "statuses" to "status",
            "lengths" to "length",
            "releaseYears" to "releaseYear",
            "startYears" to "startYear",
            "genres" to "genre",
            "tags" to "tag",
            "countries" to "country",
            "voiceActors" to "voiceActor",
            "staff" to "staff",
            "studios" to "studio"
        )

        for ((key, field) in categoryKeys) {
            val array = json[key]?.jsonArray ?: continue
            val entries = mutableListOf<UserStatEntry>()
            for (elem in array) {
                val obj = elem.jsonObject
                val c = obj["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val mScore = obj["meanScore"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
                val mins = obj["minutesWatched"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val ch = obj["chaptersRead"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

                var label = ""
                var id: String? = null
                val rawVal = obj[field]
                if (rawVal is JsonObject) {
                    id = rawVal["id"]?.jsonPrimitive?.contentOrNull
                    val nameObj = rawVal["name"]
                    if (nameObj is JsonObject) {
                        label = nameObj["userPreferred"].cleanString()
                            ?: nameObj["full"].cleanString()
                            ?: ""
                    } else if (nameObj != null) {
                        label = nameObj.cleanString() ?: ""
                    } else {
                        label = rawVal["name"].cleanString() ?: ""
                    }
                } else {
                    label = rawVal.cleanString() ?: ""
                }

                if (label.isNotBlank()) {
                    entries.add(
                        UserStatEntry(
                            id = id,
                            label = label.replace("_", " "),
                            count = c,
                            meanScore = mScore,
                            minutesWatched = mins,
                            chaptersRead = ch
                        )
                    )
                }
            }
            if (entries.isNotEmpty()) {
                catMap[key] = entries
            }
        }

        return UserStatSet(
            count = count,
            meanScore = meanScore,
            standardDeviation = stdDev,
            minutesWatched = minutesWatched,
            episodesWatched = eps,
            chaptersRead = chaps,
            volumesRead = vols,
            categories = catMap
        )
    }

    suspend fun fetchUserMediaList(userId: Int, isAnime: Boolean): List<UserMediaListItem> = withContext(Dispatchers.IO) {
        if (userId <= 0) return@withContext emptyList()
        val mediaTypeStr = if (isAnime) "ANIME" else "MANGA"
        val cacheKey = "${userId}_$mediaTypeStr"
        val cached = userMediaListCache[cacheKey]
        if (cached != null && cached.isNotEmpty()) {
            return@withContext cached
        }
        val query = """
            query (${'$'}userId: Int, ${'$'}type: MediaType) {
              MediaListCollection(userId: ${'$'}userId, type: ${'$'}type) {
                lists {
                  status
                  entries {
                    id
                    status
                    score(format: POINT_10_DECIMAL)
                    progress
                    mediaId
                    media {
                      id
                      title { userPreferred english romaji }
                      coverImage { extraLarge large }
                      format
                      type
                      episodes
                      chapters
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val jsonPayload = buildJsonObject {
            put("query", query)
            put("variables", buildJsonObject {
                put("userId", userId)
                put("type", mediaTypeStr)
            })
        }.toString()

        try {
            val req = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "OmniTrack/1.0")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body?.string() ?: return@use emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val lists = root["data"]?.jsonObject?.get("MediaListCollection")?.jsonObject?.get("lists")?.jsonArray.orEmpty()
                val result = mutableListOf<UserMediaListItem>()
                for (lElem in lists) {
                    val entries = lElem.jsonObject["entries"]?.jsonArray.orEmpty()
                    for (eElem in entries) {
                        val e = eElem.jsonObject
                        val id = e["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                        val mediaId = e["mediaId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                        val status = e["status"].cleanString() ?: "PLANNING"
                        val score = e["score"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
                        val progress = e["progress"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                        val media = e["media"]?.jsonObject ?: continue
                        val title = media["title"]?.jsonObject?.get("userPreferred").cleanString()
                            ?: media["title"]?.jsonObject?.get("english").cleanString()
                            ?: media["title"]?.jsonObject?.get("romaji").cleanString()
                            ?: "Title"
                        val cover = media["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                            ?: media["coverImage"]?.jsonObject?.get("large").cleanString()
                            ?: ""
                        val format = media["format"].cleanString()
                        val eps = media["episodes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        val chaps = media["chapters"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        result.add(
                            UserMediaListItem(
                                id = id,
                                mediaId = mediaId,
                                title = title,
                                coverUrl = cover,
                                status = status,
                                score = score,
                                progress = progress,
                                totalProgress = if (isAnime) eps else chaps,
                                format = format,
                                mediaType = if (isAnime) MediaType.ANIME else MediaType.MANGA
                            )
                        )
                    }
                }
                val distinct = result.distinctBy { it.mediaId }
                if (distinct.isNotEmpty()) {
                    userMediaListCache[cacheKey] = distinct
                }
                distinct
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun toggleFollowUser(userId: Int, userAuthToken: String): Boolean = withContext(Dispatchers.IO) {
        if (userAuthToken.isBlank() || userId <= 0) return@withContext false
        val mutation = """
            mutation (${'$'}userId: Int) {
              ToggleFollow(userId: ${'$'}userId) {
                id
                isFollowing
              }
            }
        """.trimIndent()
        try {
            val jsonPayload = buildJsonObject {
                put("query", mutation)
                put("variables", buildJsonObject { put("userId", userId) })
            }.toString()

            val req = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $userAuthToken")
                .header("User-Agent", "OmniTrack/1.0")
                .build()

            client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun sendDirectMessage(recipientId: Int, message: String, userAuthToken: String): Boolean = withContext(Dispatchers.IO) {
        if (userAuthToken.isBlank() || recipientId <= 0 || message.isBlank()) return@withContext false
        val mutation = """
            mutation (${'$'}recipientId: Int, ${'$'}message: String) {
              SaveMessageActivity(recipientId: ${'$'}recipientId, message: ${'$'}message, private: true) {
                id
              }
            }
        """.trimIndent()
        try {
            val jsonPayload = buildJsonObject {
                put("query", mutation)
                put("variables", buildJsonObject {
                    put("recipientId", recipientId)
                    put("message", message)
                })
            }.toString()

            val req = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $userAuthToken")
                .header("User-Agent", "OmniTrack/1.0")
                .build()

            client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun fetchAniListSeasonId(
        baseTitle: String,
        seasonNumber: Int,
        rootAniListId: Long? = null
    ): Long? = withContext(Dispatchers.IO) {
        if (seasonNumber == 1 && rootAniListId != null && rootAniListId > 0) {
            return@withContext rootAniListId
        }
        if (baseTitle.isBlank()) return@withContext null

        try {
            val searchQuery = "$baseTitle Season $seasonNumber"
            val query = """
                query (${'$'}search: String) {
                  Page(page: 1, perPage: 6) {
                    media(search: ${'$'}search, type: ANIME) {
                      id
                      format
                      title {
                        romaji
                        english
                      }
                    }
                  }
                }
            """.trimIndent()
            val jsonPayload = buildJsonObject {
                put("query", query)
                put("variables", buildJsonObject {
                    put("search", searchQuery)
                })
            }.toString()

            val req = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "OmniTrack/1.0")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val root = json.parseToJsonElement(body).jsonObject
                val mediaArray = root["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray.orEmpty()
                for (elem in mediaArray) {
                    val obj = elem.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                    val eng = obj["title"]?.jsonObject?.get("english").cleanString() ?: ""
                    val rom = obj["title"]?.jsonObject?.get("romaji").cleanString() ?: ""
                    val combined = "$eng $rom"
                    val hasSeasonNum = combined.contains("Season $seasonNumber", ignoreCase = true) ||
                        combined.contains("${seasonNumber}nd Season", ignoreCase = true) ||
                        combined.contains("${seasonNumber}rd Season", ignoreCase = true) ||
                        combined.contains("${seasonNumber}th Season", ignoreCase = true) ||
                        combined.contains("${seasonNumber}st Season", ignoreCase = true)
                    if (hasSeasonNum) {
                        return@use id
                    }
                }
                // Fallback: first TV result that isn't rootAniListId
                val firstTv = mediaArray.firstOrNull {
                    val f = it.jsonObject["format"].cleanString() ?: ""
                    f.equals("TV", ignoreCase = true)
                }
                val firstId = firstTv?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                if (firstId != null && firstId != rootAniListId) {
                    return@use firstId
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchAniZipRoot(anilistId: Long): JsonObject? = withContext(Dispatchers.IO) {
        aniZipRootCache[anilistId]?.let { return@withContext it }
        NetworkPacer.coalesce("anizip_root_$anilistId") {
            aniZipRootCache[anilistId]?.let { return@coalesce it }
            NetworkPacer.withAniZipPacing {
                try {
                    val url = "https://api.ani.zip/mappings?anilist_id=$anilistId"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "OmniTrack/1.0 (Android; Linux; S24 Ultra)")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withAniZipPacing null
                        val body = resp.body?.string() ?: return@withAniZipPacing null
                        val root = json.parseToJsonElement(body).jsonObject
                        aniZipRootCache[anilistId] = root
                        root
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    null
                }
            }
        }
    }

    suspend fun fetchAniZipClearlogo(anilistId: Long, tmdbKey: String? = null): String? = withContext(Dispatchers.IO) {
        val cached = aniZipLogoMemoryCache[anilistId]
        if (!cached.isNullOrBlank()) return@withContext cached

        try {
            val root = fetchAniZipRoot(anilistId) ?: return@withContext null

            // 1. Language-matched TMDb clearart check first (prevents Chinese TVDB logos)
            val effectiveKey = tmdbKey ?: "844dba0bfd8f3a4f3799f6130ef9e335"
            val tmdbId = root["mappings"]?.jsonObject?.get("themoviedb_id")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            if (tmdbId != null && tmdbId > 0) {
                val tmdbLogo = fetchTmdbClearart(tmdbId, isMovie = false, apiKey = effectiveKey)
                if (!tmdbLogo.isNullOrBlank()) {
                    aniZipLogoMemoryCache[anilistId] = tmdbLogo
                    return@withContext tmdbLogo
                }
            }

            val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
            val images = root["images"]?.jsonArray.orEmpty()
            val clearlogo = images.firstOrNull { elem ->
                val type = elem.jsonObject["coverType"].cleanString() ?: ""
                type.equals("Clearlogo", ignoreCase = true)
            } ?: images.firstOrNull { elem ->
                val type = elem.jsonObject["coverType"].cleanString() ?: ""
                type.equals("Clearart", ignoreCase = true)
            }
            val logoUrl = clearlogo?.jsonObject?.get("url").cleanString()
            if (!logoUrl.isNullOrBlank()) {
                if (pref != com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE && logoUrl.contains("thetvdb.com")) {
                    // In AniZip, TVDB logos are untagged and often Chinese (e.g. Apothecary Diaries).
                    // When English or Romaji is preferred, skip Chinese TVDB logo so clean typography is displayed.
                    return@withContext null
                }
                aniZipLogoMemoryCache[anilistId] = logoUrl
                logoUrl
            } else null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.d("EnrichmentRepository", "AniZip clearlogo lookup failed for $anilistId: ${e.message}")
            null
        }
    }

    suspend fun searchAniListForAnime(title: String, nativeTitle: String? = null): Long? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        NetworkPacer.withAniListPacing {
            try {
                val candidates = listOfNotNull(title, nativeTitle).filter { it.isNotBlank() }
                for (queryStr in candidates) {
                    val gql = """
                        query (${'$'}search: String) {
                            Page(page: 1, perPage: 5) {
                                media(search: ${'$'}search, type: ANIME) {
                                    id
                                }
                            }
                        }
                    """.trimIndent()
                    val payload = buildJsonObject {
                        put("query", gql)
                        putJsonObject("variables") {
                            put("search", queryStr)
                        }
                    }
                    val req = Request.Builder()
                        .url("https://graphql.anilist.co")
                        .header("User-Agent", "OmniTrack/1.0 (Android; Linux; S24 Ultra)")
                        .header("Accept", "application/json")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: return@use
                            val root = json.parseToJsonElement(body).jsonObject
                            val mediaList = root["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
                            val firstId = mediaList?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                            if (firstId != null && firstId > 0) return@withAniListPacing firstId
                        }
                    }
                }
                null
            } catch (e: Exception) {
                AppLogger.d("EnrichmentRepository", "searchAniListForAnime failed for $title: ${e.message}")
                null
            }
        }
    }

    data class AniZipMappingInfo(
        val tmdbId: Long?,
        val isMovie: Boolean,
        val imdbId: String?,
        val tvdbId: Long?,
        val type: String?,
        val episodeCount: Int
    )

    suspend fun fetchAniZipMapping(anilistId: Long): AniZipMappingInfo? = withContext(Dispatchers.IO) {
        try {
            val root = fetchAniZipRoot(anilistId) ?: return@withContext null
            val mappings = root["mappings"]?.jsonObject ?: return@withContext null
            val type = mappings["type"].cleanString()
            val episodeCount = mappings["episodeCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: (root["episodeCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull())
                ?: (root["episodes"]?.jsonObject?.size ?: 0)
            AniZipMappingInfo(
                tmdbId = mappings["themoviedb_id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                isMovie = type.equals("MOVIE", ignoreCase = true),
                imdbId = mappings["imdb_id"].cleanString(),
                tvdbId = mappings["thetvdb_id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    ?: mappings["tvdb_id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                type = type,
                episodeCount = episodeCount
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    suspend fun fetchAniZipTmdbId(anilistId: Long): Long? = withContext(Dispatchers.IO) {
        try {
            val root = fetchAniZipRoot(anilistId) ?: return@withContext null
            root["mappings"]?.jsonObject?.get("themoviedb_id")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    suspend fun fetchAniZipTvdbId(anilistId: Long): Long? = withContext(Dispatchers.IO) {
        try {
            val root = fetchAniZipRoot(anilistId) ?: return@withContext null
            val mappings = root["mappings"]?.jsonObject ?: return@withContext null
            mappings["thetvdb_id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?: mappings["tvdb_id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    suspend fun fetchAniZipImdbId(anilistId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val root = fetchAniZipRoot(anilistId) ?: return@withContext null
            root["mappings"]?.jsonObject?.get("imdb_id").cleanString()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    private val aniZipEpisodesMemoryCache = java.util.concurrent.ConcurrentHashMap<Long, List<com.omnitrack.app.data.model.MediaEpisode>>()

    suspend fun fetchAniZipEpisodes(anilistId: Long): List<com.omnitrack.app.data.model.MediaEpisode> = withContext(Dispatchers.IO) {
        val cached = aniZipEpisodesMemoryCache[anilistId]
        if (!cached.isNullOrEmpty()) return@withContext cached

        try {
            val root = fetchAniZipRoot(anilistId) ?: return@withContext emptyList()
            val episodesObj = root["episodes"]?.jsonObject ?: return@withContext emptyList()

            val epsList = mutableListOf<com.omnitrack.app.data.model.MediaEpisode>()
            val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage

            episodesObj.entries.forEach { (key, elem) ->
                val o = elem.jsonObject
                val epNum = o["episodeNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: key.replace(Regex("[^0-9]"), "").toIntOrNull()
                    ?: return@forEach
                val sNum = o["seasonNumber"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: if (key.startsWith("S", ignoreCase = true)) 0 else 1
                val titlesObj = o["title"]?.jsonObject
                val titleEn = titlesObj?.get("en").cleanString()
                val titleJa = titlesObj?.get("ja").cleanString()
                val titleRom = titlesObj?.get("x-jat").cleanString()
                val resolvedTitle = when (pref) {
                    com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE -> titleJa ?: titleRom ?: titleEn
                    com.omnitrack.app.data.preferences.PreferredTitleLanguage.ROMAJI -> titleRom ?: titleEn ?: titleJa
                    else -> titleEn ?: titleRom ?: titleJa
                } ?: "Episode $epNum"

                val overview = o["overview"].cleanString() ?: o["summary"].cleanString()
                val image = o["image"].cleanString()
                val airDate = (o["airDate"].cleanString() ?: o["airdate"].cleanString())?.take(10)
                val runtime = o["runtime"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: o["length"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val rating = o["rating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.takeIf { it > 0f }

                epsList.add(
                    com.omnitrack.app.data.model.MediaEpisode(
                        id = sNum * 1000L + epNum,
                        episodeNumber = epNum,
                        seasonNumber = sNum,
                        title = resolvedTitle,
                        overview = overview,
                        stillUrl = image,
                        airDate = airDate,
                        runtimeMinutes = runtime,
                        voteAverage = rating,
                        ratingSource = if (rating != null) "AniDB" else null
                    )
                )
            }

            val sorted = epsList.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
            if (sorted.isNotEmpty()) {
                aniZipEpisodesMemoryCache[anilistId] = sorted
            }
            sorted
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.d("EnrichmentRepository", "AniZip episodes lookup failed for $anilistId: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchEpisodeFillers(
        malId: Long,
        showTitle: String? = null
    ): Map<Int, com.omnitrack.app.data.model.EpisodeFillerInfo> = withContext(Dispatchers.IO) {
        val cached = episodeFillerMemoryCache[malId]
        if (!cached.isNullOrEmpty()) return@withContext cached

        NetworkPacer.coalesce("fillers_$malId") {
            episodeFillerMemoryCache[malId]?.takeIf { it.isNotEmpty() }?.let { return@coalesce it }

            val fillerMap = mutableMapOf<Int, com.omnitrack.app.data.model.EpisodeFillerInfo>()
            val mirrorHost = "https://jikanfortheweebs.midnightignite.me/v4"
            val officialHost = "https://api.jikan.moe/v4"
            var activeHost = mirrorHost

            var page1 = queryJikanEpisodesPage(activeHost, malId, 1)
            if (page1 == null && System.currentTimeMillis() >= jikanCircuitBreakerUntil) {
                activeHost = officialHost
                page1 = queryJikanEpisodesPage(activeHost, malId, 1)
            }

            if (page1 != null) {
                fillerMap.putAll(page1.episodes)
                if (page1.hasNext) {
                    val remainingPages = coroutineScope {
                        (2..6).map { p ->
                            async { queryJikanEpisodesPage(activeHost, malId, p) }
                        }.awaitAll()
                    }
                    for (res in remainingPages) {
                        if (res != null) {
                            fillerMap.putAll(res.episodes)
                        }
                    }
                }
            }

            if (fillerMap.isEmpty() && !showTitle.isNullOrBlank()) {
                val aflMap = fetchAnimeFillerListFillers(showTitle)
                if (aflMap.isNotEmpty()) {
                    fillerMap.putAll(aflMap)
                }
            }

            if (fillerMap.isNotEmpty()) {
                episodeFillerMemoryCache[malId] = fillerMap
            }
            fillerMap
        }
    }

    private suspend fun fetchAnimeFillerListFillers(showTitle: String): Map<Int, com.omnitrack.app.data.model.EpisodeFillerInfo> =
        withContext(Dispatchers.IO) {
            try {
                val baseTitle = showTitle
                    .replace(Regex("(?i)\\s*[:\\-]\\s*(season|part|cour).*"), "")
                    .trim()
                val rawSlug = baseTitle.lowercase()
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-')
                if (rawSlug.isBlank()) return@withContext emptyMap()

                val candidates = listOfNotNull(
                    rawSlug,
                    rawSlug.removePrefix("the-").takeIf { it != rawSlug }
                ).distinct()

                for (slug in candidates) {
                    val url = "https://www.animefillerlist.com/shows/$slug"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                        .build()
                    val html = jikanClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) null else resp.body?.string()
                    } ?: continue

                    val fillerSectionMatch = Regex(
                        """class="filler"[^>]*>.*?<span class="Episodes">(.*?)</span>""",
                        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
                    ).find(html) ?: continue

                    val episodesText = fillerSectionMatch.groupValues[1]
                        .replace(Regex("<[^>]+>"), "")
                        .trim()
                    if (episodesText.isBlank()) continue

                    val map = mutableMapOf<Int, com.omnitrack.app.data.model.EpisodeFillerInfo>()
                    episodesText.split(",").forEach { token ->
                        val cleaned = token.trim()
                        if (cleaned.contains("-")) {
                            val bounds = cleaned.split("-")
                            val start = bounds.getOrNull(0)?.trim()?.toIntOrNull()
                            val end = bounds.getOrNull(1)?.trim()?.toIntOrNull()
                            if (start != null && end != null && start <= end && end - start <= 500) {
                                for (ep in start..end) {
                                    map[ep] = com.omnitrack.app.data.model.EpisodeFillerInfo(
                                        episodeNumber = ep,
                                        isFiller = true,
                                        isRecap = false
                                    )
                                }
                            }
                        } else {
                            cleaned.toIntOrNull()?.let { ep ->
                                map[ep] = com.omnitrack.app.data.model.EpisodeFillerInfo(
                                    episodeNumber = ep,
                                    isFiller = true,
                                    isRecap = false
                                )
                            }
                        }
                    }
                    if (map.isNotEmpty()) return@withContext map
                }
                emptyMap()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emptyMap()
            }
        }

    private data class JikanPageResult(
        val episodes: Map<Int, com.omnitrack.app.data.model.EpisodeFillerInfo>,
        val hasNext: Boolean
    )

    private suspend fun queryJikanEpisodesPage(host: String, malId: Long, page: Int): JikanPageResult? =
        NetworkPacer.withJikanPacing {
            try {
                val url = "$host/anime/$malId/episodes?page=$page"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "OmniTrack/1.0 (Android; Linux; S24 Ultra)")
                    .build()
                jikanClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        if (host.contains("api.jikan.moe")) {
                            jikanCircuitBreakerUntil = System.currentTimeMillis() + 30 * 60 * 1000L
                        }
                        return@withJikanPacing null
                    }
                    val body = resp.body?.string() ?: return@withJikanPacing null
                    val root = json.parseToJsonElement(body).jsonObject
                    val pagination = root["pagination"]?.jsonObject
                    val hasNext = pagination?.get("has_next_page")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                    val data = root["data"]?.jsonArray.orEmpty()
                    val map = mutableMapOf<Int, com.omnitrack.app.data.model.EpisodeFillerInfo>()
                    for (elem in data) {
                        val obj = elem.jsonObject
                        val epNum = obj["mal_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: continue
                        val isFiller = obj["filler"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                        val isRecap = obj["recap"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                        map[epNum] = com.omnitrack.app.data.model.EpisodeFillerInfo(
                            episodeNumber = epNum,
                            isFiller = isFiller,
                            isRecap = isRecap
                        )
                    }
                    JikanPageResult(map, hasNext)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (host.contains("api.jikan.moe")) {
                    jikanCircuitBreakerUntil = System.currentTimeMillis() + 30 * 60 * 1000L
                }
                AppLogger.d("EnrichmentRepository", "Jikan query failed on $host for malId=$malId page=$page: ${e.message}")
                null
            }
        }

    suspend fun fetchHeroMultiRatings(
        mediaType: MediaType,
        title: String,
        nativeTitle: String? = null,
        anilistId: Long? = null,
        malId: Long? = null,
        tmdbId: Long? = null,
        imdbId: String? = null,
        bangumiId: Long? = null,
        steamAppId: Long? = null,
        igdbId: Long? = null,
        vndbId: String? = null,
        hardcoverId: String? = null,
        openLibraryId: String? = null,
        bookAuthor: String? = null,
        tmdbApiKey: String = PreferenceManager.cachedTmdbApiKey,
        mdblistApiKey: String = PreferenceManager.cachedMdblistApiKey
    ): CachedMultiRatings = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (mediaType == MediaType.FANFICTION || title.isBlank()) {
            return@withContext CachedMultiRatings(updatedAtMs = now)
        }
        val effTmdbKey = tmdbApiKey.ifBlank { "844dba0bfd8f3a4f3799f6130ef9e335" }
        val effMdbKey = mdblistApiKey.trim()

        when (mediaType) {
            MediaType.SHOW, MediaType.MOVIE -> coroutineScope {
                val isMovie = mediaType == MediaType.MOVIE
                val mdbDef = async {
                    if (effMdbKey.isNotBlank() && (!imdbId.isNullOrBlank() || tmdbId != null || title.isNotBlank())) {
                        fetchMdbListRatings(imdbId, tmdbId, isMovie, title, effMdbKey, nativeTitle)
                    } else null
                }
                val tmdbScoreDef = async {
                    if (tmdbId != null && effTmdbKey.isNotBlank()) {
                        fetchTmdbVote(tmdbId, isMovie, effTmdbKey)
                    } else null
                }
                val resolvedImdbDef = async {
                    imdbId?.takeIf { it.isNotBlank() }
                        ?: tmdbId?.let { fetchTmdbImdbId(it, isMovie, effTmdbKey) }
                }
                val mdb = mdbDef.await()
                val directTmdb = tmdbScoreDef.await()
                val effImdbId = resolvedImdbDef.await()
                val imdb = mdb?.imdbScore ?: effImdbId?.let { fetchCinemetaImdbById(it, isMovie) }
                CachedMultiRatings(
                    imdbScore = imdb,
                    tmdbScore = mdb?.tmdbScore ?: directTmdb,
                    traktScore = mdb?.traktScore,
                    audienceScore = mdb?.audienceScore,
                    rtScore = mdb?.rtScore,
                    metacriticScore = mdb?.metacriticScore,
                    updatedAtMs = now
                )
            }

            MediaType.ANIME -> coroutineScope {
                val aniPair = fetchAniListRatingAndMal(anilistId, title, isAnime = true, preferNovel = false)
                val effMalId = malId ?: aniPair.second
                val jikanDef = async { effMalId?.let { fetchJikanScore(it, isManga = false) } }
                val bgmDef = async {
                    bangumiId?.let { fetchBangumiScore(it) }
                        ?: fetchBangumiSubjectSearchScore(nativeTitle ?: title, subjectType = 2)
                }
                val kitsuDef = async { fetchKitsuMediaScore(title, subtype = "anime") }
                val mdbDef = async {
                    if (effMdbKey.isNotBlank() && (!imdbId.isNullOrBlank() || tmdbId != null)) {
                        fetchMdbListRatings(imdbId, tmdbId, false, title, effMdbKey, nativeTitle)
                    } else null
                }
                val malScore = jikanDef.await()
                val bgmScore = bgmDef.await()
                val kitsuScore = kitsuDef.await()
                val mdb = mdbDef.await()
                val imdbScore = mdb?.imdbScore ?: imdbId?.takeIf { it.isNotBlank() }?.let { fetchCinemetaImdbById(it, false) }
                val tmdbScore = mdb?.tmdbScore ?: tmdbId?.let { fetchTmdbVote(it, false, effTmdbKey) }
                CachedMultiRatings(
                    imdbScore = imdbScore,
                    tmdbScore = tmdbScore,
                    traktScore = mdb?.traktScore,
                    audienceScore = mdb?.audienceScore,
                    rtScore = mdb?.rtScore,
                    metacriticScore = mdb?.metacriticScore,
                    anilistScore = aniPair.first,
                    malScore = malScore,
                    bangumiScore = bgmScore,
                    kitsuScore = kitsuScore,
                    updatedAtMs = now
                )
            }

            MediaType.MANGA, MediaType.NOVEL -> coroutineScope {
                val isNovel = mediaType == MediaType.NOVEL
                val aniPair = fetchAniListRatingAndMal(anilistId, title, isAnime = false, preferNovel = isNovel)
                val effMalId = malId ?: aniPair.second
                val jikanDef = async { effMalId?.let { fetchJikanScore(it, isManga = true) } }
                val kitsuDef = async {
                    if (isNovel) fetchKitsuNovelDetails(title)?.score else fetchKitsuMediaScore(title, subtype = "manga")
                }
                val muDef = async { fetchMangaUpdatesInfo(title, preferNovel = isNovel)?.bayesianRating }
                val bgmDef = async {
                    bangumiId?.let { fetchBangumiScore(it) }
                        ?: fetchBangumiNovelDetails(title, nativeTitle, bangumiId)?.score
                }
                CachedMultiRatings(
                    anilistScore = aniPair.first,
                    malScore = jikanDef.await(),
                    kitsuScore = kitsuDef.await(),
                    mangaUpdatesScore = muDef.await(),
                    bangumiScore = bgmDef.await(),
                    updatedAtMs = now
                )
            }

            MediaType.GAME -> coroutineScope {
                val effSteamId = steamAppId ?: resolveSteamAppIdQuick(title)
                val steamPosDef = async { effSteamId?.let { fetchSteamPositivePct(it) } }
                val steamMetaDef = async { effSteamId?.let { fetchSteamAppDetails(it)?.metacriticScore } }
                val igdbDef = async {
                    val cid = PreferenceManager.cachedIgdbClientId
                    val csec = PreferenceManager.cachedIgdbClientSecret
                    if (cid.isNotBlank() && csec.isNotBlank()) {
                        runCatching {
                            IgdbApiClient(client).fetchFullGameDetails(cid, csec, igdbId, effSteamId, title)
                        }.getOrNull()
                    } else null
                }
                val bgmDef = async { bangumiId?.let { fetchBangumiScore(it) } }
                val igdbData = igdbDef.await()
                val bridgedSteamId = if (effSteamId == null) igdbData?.steamAppId else null
                val steamPct = steamPosDef.await() ?: bridgedSteamId?.let { fetchSteamPositivePct(it) }
                val meta = steamMetaDef.await() ?: bridgedSteamId?.let { fetchSteamAppDetails(it)?.metacriticScore }
                CachedMultiRatings(
                    igdbUserScore = igdbData?.userRating,
                    igdbCriticScore = igdbData?.criticRating,
                    steamPositivePct = steamPct,
                    metacriticScore = meta,
                    bangumiScore = bgmDef.await(),
                    updatedAtMs = now
                )
            }

            MediaType.VISUAL_NOVEL -> coroutineScope {
                val vnQuick = fetchVndbRatingQuick(vndbId, title)
                val effSteamId = steamAppId ?: vnQuick?.second
                val steamDef = async { effSteamId?.let { fetchSteamPositivePct(it) } }
                val metaDef = async { effSteamId?.let { fetchSteamAppDetails(it)?.metacriticScore } }
                val bgmDef = async {
                    bangumiId?.let { fetchBangumiScore(it) }
                        ?: fetchBangumiSubjectSearchScore(nativeTitle ?: title, subjectType = 4)
                }
                CachedMultiRatings(
                    vndbScore = vnQuick?.first,
                    steamPositivePct = steamDef.await(),
                    metacriticScore = metaDef.await(),
                    bangumiScore = bgmDef.await(),
                    updatedAtMs = now
                )
            }

            MediaType.BOOK -> coroutineScope {
                val olDef = async { fetchOpenLibraryQuickRating(title, bookAuthor, openLibraryId) }
                val appleDef = async { fetchAppleBooksQuickRating(title, bookAuthor) }
                val hcDef = async {
                    val hcToken = TrackerTokenManager.cachedHardcoverToken
                    if (hcToken.isNotBlank()) {
                        runCatching {
                            HardcoverApiClient(client).fetchBookDetails(hcToken, hardcoverId, title, bookAuthor)?.rating5
                        }.getOrNull()
                    } else null
                }
                CachedMultiRatings(
                    openLibraryRating = olDef.await(),
                    appleBooksRating = appleDef.await(),
                    hardcoverRating = hcDef.await(),
                    updatedAtMs = now
                )
            }

            MediaType.COMIC -> coroutineScope {
                val appleDef = async {
                    runCatching {
                        ComicEnrichmentSource(client)
                            .searchAppleComics(title, strictComicFilter = true)
                            .firstOrNull()
                            ?.communityScore
                            ?.let { it / 2f }
                    }.getOrNull()
                }
                val olDef = async { fetchOpenLibraryQuickRating(title, bookAuthor, openLibraryId) }
                CachedMultiRatings(
                    appleBooksRating = appleDef.await(),
                    openLibraryRating = olDef.await(),
                    updatedAtMs = now
                )
            }

            else -> CachedMultiRatings(updatedAtMs = now)
        }
    }

    private suspend fun fetchAniListRatingAndMal(
        anilistId: Long?,
        title: String,
        isAnime: Boolean,
        preferNovel: Boolean
    ): Pair<Float?, Long?> = NetworkPacer.withAniListPacing {
        try {
            val typeStr = if (isAnime) "ANIME" else "MANGA"
            val gql = if (anilistId != null && anilistId > 0L) {
                """
                    query (${'$'}id: Int, ${'$'}type: MediaType) {
                        Media(id: ${'$'}id, type: ${'$'}type) {
                            averageScore
                            idMal
                        }
                    }
                """.trimIndent()
            } else if (preferNovel) {
                """
                    query (${'$'}search: String, ${'$'}type: MediaType) {
                        Media(search: ${'$'}search, type: ${'$'}type, format: NOVEL) {
                            averageScore
                            idMal
                        }
                    }
                """.trimIndent()
            } else {
                """
                    query (${'$'}search: String, ${'$'}type: MediaType) {
                        Media(search: ${'$'}search, type: ${'$'}type) {
                            averageScore
                            idMal
                        }
                    }
                """.trimIndent()
            }
            val payload = buildJsonObject {
                put("query", gql)
                putJsonObject("variables") {
                    if (anilistId != null && anilistId > 0L) put("id", anilistId) else put("search", title)
                    put("type", typeStr)
                }
            }.toString()
            val req = Request.Builder()
                .url("https://graphql.anilist.co")
                .header("User-Agent", "OmniTrack/1.0")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withAniListPacing null to null
                val body = resp.body?.string() ?: return@withAniListPacing null to null
                val media = json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("Media")?.jsonObject
                    ?: return@withAniListPacing null to null
                val score = media["averageScore"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.takeIf { it > 0f }?.let { it / 10f }
                val malId = media["idMal"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.takeIf { it > 0L }
                score to malId
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null to null
        }
    }

    private suspend fun fetchKitsuMediaScore(title: String, subtype: String): Float? = NetworkPacer.withGeneralPacing {
        try {
            val encoded = URLEncoder.encode(stripNovelEditionSuffix(title).ifBlank { title }, "UTF-8")
            val endpoint = if (subtype == "anime") "anime" else "manga"
            val req = Request.Builder()
                .url("https://kitsu.io/api/edge/$endpoint?filter[text]=$encoded&page[limit]=1")
                .header("Accept", "application/vnd.api+json")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withGeneralPacing null
                val body = resp.body?.string() ?: return@withGeneralPacing null
                val first = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: return@withGeneralPacing null
                first["attributes"]?.jsonObject?.get("averageRating")?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                    ?.takeIf { it > 0f }?.let { it / 10f }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    private suspend fun fetchBangumiSubjectSearchScore(query: String, subjectType: Int): Float? {
        val clean = query.trim()
        if (clean.isBlank()) return null
        val bgmId = NetworkPacer.withBangumiPacing {
            try {
                val encoded = URLEncoder.encode(clean, "UTF-8")
                val req = Request.Builder()
                    .url("https://api.bgm.tv/search/subject/$encoded?type=$subjectType&responseGroup=small&max_results=3")
                    .header("User-Agent", "OmniTrack/1.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withBangumiPacing null
                    val body = resp.body?.string() ?: return@withBangumiPacing null
                    val list = json.parseToJsonElement(body).jsonObject["list"]?.jsonArray ?: return@withBangumiPacing null
                    val first = list.firstOrNull()?.jsonObject ?: return@withBangumiPacing null
                    first["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        } ?: return null
        return fetchBangumiScore(bgmId)
    }

    private suspend fun resolveSteamAppIdQuick(title: String): Long? = NetworkPacer.withSteamPacing {
        try {
            val encoded = URLEncoder.encode(title.trim(), "UTF-8")
            val req = Request.Builder()
                .url("https://store.steampowered.com/api/storesearch/?term=$encoded&l=en&cc=us")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withSteamPacing null
                val body = resp.body?.string() ?: return@withSteamPacing null
                val items = json.parseToJsonElement(body).jsonObject["items"]?.jsonArray ?: return@withSteamPacing null
                val target = title.trim().lowercase()
                val best = items.firstOrNull { el ->
                    el.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() == target
                } ?: items.firstOrNull()
                best?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    private suspend fun fetchSteamPositivePct(appId: Long): Int? = NetworkPacer.withSteamPacing {
        try {
            val req = Request.Builder()
                .url("https://store.steampowered.com/appreviews/$appId?json=1&language=all&purchase_type=all&num_per_page=0")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withSteamPacing null
                val body = resp.body?.string() ?: return@withSteamPacing null
                val summary = json.parseToJsonElement(body).jsonObject["query_summary"]?.jsonObject ?: return@withSteamPacing null
                val pos = summary["total_positive"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val total = summary["total_reviews"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                if (total > 0) kotlin.math.round((pos * 100.0) / total).toInt().coerceIn(0, 100) else null
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    private suspend fun fetchVndbRatingQuick(vndbId: String?, title: String): Pair<Float?, Long?>? = NetworkPacer.withVndbPacing {
        try {
            val filtersElement = if (!vndbId.isNullOrBlank()) {
                json.parseToJsonElement("""["id", "=", "${vndbId.replace("\"", "")}"]""")
            } else {
                json.parseToJsonElement("""["search", "=", "${title.replace("\"", "").trim()}"]""")
            }
            val payload = buildJsonObject {
                put("filters", filtersElement)
                put("fields", "id, rating, extlinks{name,id}")
                put("results", 1)
            }.toString()
            val req = Request.Builder()
                .url("https://api.vndb.org/kana/vn")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withVndbPacing null
                val body = resp.body?.string() ?: return@withVndbPacing null
                val first = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: return@withVndbPacing null
                val score = first["rating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.takeIf { it > 0f }?.let { it / 10f }
                val steamExt = first["extlinks"]?.jsonArray.orEmpty().firstOrNull { link ->
                    link.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.equals("steam", ignoreCase = true) == true
                }?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                score to steamExt
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    private suspend fun fetchOpenLibraryQuickRating(
        title: String,
        author: String?,
        openLibraryId: String?
    ): Float? = NetworkPacer.withOpenLibraryPacing {
        try {
            val cleanOl = openLibraryId?.trim()?.takeIf { it.isNotBlank() }
            if (cleanOl != null) {
                val path = if (cleanOl.startsWith("/works/")) cleanOl else "/works/$cleanOl"
                val req = Request.Builder()
                    .url("https://openlibrary.org$path/ratings.json")
                    .header("User-Agent", "OmniTrack/1.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val avg = json.parseToJsonElement(resp.body?.string().orEmpty())
                            .jsonObject["summary"]?.jsonObject?.get("average")?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                        if (avg != null && avg > 0f) return@withOpenLibraryPacing avg
                    }
                }
            }
            val encodedTitle = URLEncoder.encode(title.trim(), "UTF-8")
            val authorParam = author?.trim()?.takeIf { it.isNotBlank() }?.let {
                "&author=${URLEncoder.encode(it, "UTF-8")}"
            }.orEmpty()
            val req = Request.Builder()
                .url("https://openlibrary.org/search.json?title=$encodedTitle$authorParam&fields=key,ratings_average&limit=1")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withOpenLibraryPacing null
                val first = json.parseToJsonElement(resp.body?.string().orEmpty())
                    .jsonObject["docs"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@withOpenLibraryPacing null
                first["ratings_average"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.takeIf { it > 0f }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    private suspend fun fetchAppleBooksQuickRating(title: String, author: String?): Float? = NetworkPacer.withGeneralPacing {
        try {
            val q = listOfNotNull(title.trim().takeIf { it.isNotBlank() }, author?.trim()?.takeIf { it.isNotBlank() }).joinToString(" ")
            val encoded = URLEncoder.encode(q, "UTF-8")
            val req = Request.Builder()
                .url("https://itunes.apple.com/search?term=$encoded&media=ebook&limit=2")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withGeneralPacing null
                val arr = json.parseToJsonElement(resp.body?.string().orEmpty())
                    .jsonObject["results"]?.jsonArray ?: return@withGeneralPacing null
                arr.firstNotNullOfOrNull { el ->
                    el.jsonObject["averageUserRating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.takeIf { it > 0f }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }
}
