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
import com.omnitrack.app.ui.screens.detail.components.MediaTrailer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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

    private val cinemetaVideosCache = java.util.concurrent.ConcurrentHashMap<String, List<com.omnitrack.app.data.model.MediaEpisode>>()
    private val cinemetaTvdbIdCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val tmdbTvdbIdCache = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private val skyHookShowCache = java.util.concurrent.ConcurrentHashMap<Long, SkyHookShowData>()
    private val tmdbSeason1StillsCache = java.util.concurrent.ConcurrentHashMap<Long, List<com.omnitrack.app.data.model.MediaEpisode>>()
    private val tmdbSeasonMetaCache = java.util.concurrent.ConcurrentHashMap<Long, List<TmdbRawSeasonMeta>>()
    private val tmdbSeasonRawEpisodesCache = java.util.concurrent.ConcurrentHashMap<String, List<com.omnitrack.app.data.model.MediaEpisode>>()
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
                effectiveTmdbId = fetchAniZipTmdbId(item.anilistId)
            }
            if (effectiveTmdbId == null && item.title.isNotBlank()) {
                val animeTmdb = searchTmdbForAnime(item.title, effectiveTmdbKey, item.nativeTitle)
                if (animeTmdb != null) {
                    effectiveTmdbId = animeTmdb.first
                    isMovie = animeTmdb.second
                }
            }
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
        onClearartResolved: (suspend (String, Long?) -> Unit)? = null
    ): EnrichmentData = withContext(Dispatchers.IO) {
        var clearart: String? = item.clearartUrl
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
                effectiveTmdbId = fetchAniZipTmdbId(item.anilistId)
            }
            if (effectiveTmdbId == null && item.title.isNotBlank()) {
                val animeTmdb = searchTmdbForAnime(item.title, effectiveTmdbKey, item.nativeTitle)
                if (animeTmdb != null) {
                    effectiveTmdbId = animeTmdb.first
                    isMovie = animeTmdb.second
                    isShow = !animeTmdb.second
                }
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
        if (clearart.isNullOrBlank()) {
            var newClearart: String? = null
            if (effectiveTmdbId != null) {
                if (!effectiveTmdbKey.isNullOrBlank()) {
                    newClearart = fetchTmdbClearart(effectiveTmdbId, isMovie, effectiveTmdbKey)
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
                MediaType.ANIME, MediaType.MANGA -> {
                    if (item.malId != null) {
                        communityScore = fetchJikanScore(item.malId, isManga = item.mediaType == MediaType.MANGA)
                    } else if (item.bangumiId != null) {
                        communityScore = fetchBangumiScore(item.bangumiId)
                    }
                }
                MediaType.NOVEL -> {
                    if (item.bangumiId != null) {
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

    private suspend fun fetchTmdbClearart(tmdbId: Long, isMovie: Boolean, apiKey: String): String? =
        NetworkPacer.withTmdbPacing {
            try {
                val typeStr = if (isMovie) "movie" else "tv"
                val url = "https://api.themoviedb.org/3/$typeStr/$tmdbId/images?api_key=$apiKey&include_image_language=en,null,ja"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withTmdbPacing null
                    val body = response.body?.string() ?: return@withTmdbPacing null
                    val root = json.parseToJsonElement(body).jsonObject
                    val logos = root["logos"]?.jsonArray ?: return@withTmdbPacing null

                    val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
                    val best = when (pref) {
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE -> {
                            logos.firstOrNull { it.jsonObject["iso_639_1"]?.jsonPrimitive?.content == "ja" }
                                ?: logos.firstOrNull { it.jsonObject["iso_639_1"]?.jsonPrimitive?.content == "en" }
                                ?: logos.firstOrNull { it.jsonObject["iso_639_1"]?.jsonPrimitive?.content == null }
                                ?: logos.firstOrNull()
                        }
                        else -> {
                            logos.firstOrNull { it.jsonObject["iso_639_1"]?.jsonPrimitive?.content == "en" }
                                ?: logos.firstOrNull { it.jsonObject["iso_639_1"]?.jsonPrimitive?.content == null }
                                ?: logos.firstOrNull { it.jsonObject["iso_639_1"]?.jsonPrimitive?.content == "ja" }
                                ?: logos.firstOrNull()
                        }
                    }

                    val filePath = best?.jsonObject?.get("file_path")?.jsonPrimitive?.content ?: return@withTmdbPacing null
                    "https://image.tmdb.org/t/p/w500$filePath"
                }
            } catch (_: Exception) {
                null
            }
        }

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
                    // 1. Search TV (most anime are series)
                    val tvUrl = "https://api.themoviedb.org/3/search/tv?api_key=$apiKey&query=${URLEncoder.encode(searchQ, "UTF-8")}"
                    val tvReq = Request.Builder().url(tvUrl).get().build()
                    val tvId = client.newCall(tvReq).execute().use { resp ->
                        if (!resp.isSuccessful) null
                        else {
                            val body = resp.body?.string() ?: return@use null
                            val root = json.parseToJsonElement(body).jsonObject
                            val first = root["results"]?.jsonArray?.firstOrNull()?.jsonObject
                            first?.get("id")?.jsonPrimitive?.content?.toLongOrNull()
                        }
                    }
                    if (tvId != null) return@withTmdbPacing Pair(tvId, false) // false = isMovie (is TV)

                    // 2. Search Movie (for anime movies)
                    val movieUrl = "https://api.themoviedb.org/3/search/movie?api_key=$apiKey&query=${URLEncoder.encode(searchQ, "UTF-8")}"
                    val movieReq = Request.Builder().url(movieUrl).get().build()
                    val movieId = client.newCall(movieReq).execute().use { resp ->
                        if (!resp.isSuccessful) null
                        else {
                            val body = resp.body?.string() ?: return@use null
                            val root = json.parseToJsonElement(body).jsonObject
                            val first = root["results"]?.jsonArray?.firstOrNull()?.jsonObject
                            first?.get("id")?.jsonPrimitive?.content?.toLongOrNull()
                        }
                    }
                    if (movieId != null) return@withTmdbPacing Pair(movieId, true)
                }
                null
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
        val belongsToCollectionName: String? = null
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
                    belongsToCollectionName = belongsToCollName
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
                            voteAverage = voteAvg
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
                            val voteAverage = obj["vote_average"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                            com.omnitrack.app.data.model.MediaEpisode(
                                id = id,
                                episodeNumber = epNumber,
                                seasonNumber = rawSeasonNumber,
                                title = name,
                                overview = overview,
                                stillUrl = stillPath?.let { "https://image.tmdb.org/t/p/w780$it" },
                                airDate = airDate,
                                runtimeMinutes = runtime,
                                voteAverage = voteAverage
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

    suspend fun fetchTvSeasons(
        tmdbId: Long,
        apiKey: String,
        showTitle: String? = null,
        imdbId: String? = null
    ): List<com.omnitrack.app.data.model.MediaSeason> {
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
        // If TMDb only returned <= 1 regular season or Season 1 has > 26 episodes (lumped anime runs like Bleach / The Apothecary Diaries), consult Cinemeta for split seasons!
        val hasMultiSeasonTmdb = tmdbSeasons.count { it.seasonNumber > 0 && it.episodeCount > 0 } > 1
        val hasLumpedSeason1 = tmdbSeasons.any { it.seasonNumber == 1 && it.episodeCount > 26 }
        if (hasMultiSeasonTmdb && !hasLumpedSeason1) {
            return tmdbSeasons
                .distinctBy { it.seasonNumber }
                .sortedWith(compareBy({ it.seasonNumber == 0 }, { it.seasonNumber }))
        }

        // Cinemeta + TVDB (SkyHook) merge for multi-season anime (e.g. Frieren S2, Bleach S1-16, Apothecary Diaries S2/3)
        val effectiveImdbId = imdbId ?: fetchTmdbImdbId(tmdbId, false, apiKey)
        if (!effectiveImdbId.isNullOrBlank()) {
            val cinemetaVideos = fetchCinemetaVideos(effectiveImdbId)
            val tvdbId = cinemetaTvdbIdCache[effectiveImdbId] ?: tmdbTvdbIdCache[tmdbId]
            val skyHook = if (tvdbId != null && tvdbId > 0L) fetchSkyHookShow(tvdbId) else null

            if (cinemetaVideos.isNotEmpty()) {
                val cinemetaSeasons = cinemetaVideos.groupBy { it.seasonNumber }
                val mergedList = tmdbSeasons.toMutableList()
                val tmdbSeason1Poster = tmdbSeasons.find { it.seasonNumber == 1 }?.posterUrl

                // Parallel AniList/Kitsu poster discovery ONLY for seasons still missing a SkyHook TVDB poster (and <= 5)
                val seasonsNeedingPosters = cinemetaSeasons.keys.filter { sNum ->
                    sNum > 0 &&
                        mergedList.find { it.seasonNumber == sNum }?.posterUrl.isNullOrBlank() &&
                        skyHook?.seasonPosters?.get(sNum).isNullOrBlank()
                }
                val seasonPosters: Map<Int, String?> = if (!showTitle.isNullOrBlank() && seasonsNeedingPosters.isNotEmpty() && seasonsNeedingPosters.size <= 5) {
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

    suspend fun fetchTvSeasonEpisodes(
        tmdbId: Long,
        seasonNumber: Int,
        apiKey: String,
        imdbId: String? = null,
        showTitle: String? = null
    ): List<com.omnitrack.app.data.model.MediaEpisode> {
        val rawSeasons = fetchTmdbRawSeasonMeta(tmdbId, apiKey)

        // Sub-Series Detection (e.g. "BLEACH: Thousand-Year Blood War" cours 1..4 inside TMDb Season 2)
        val matchedSubSeason = findMatchedSubSeason(rawSeasons, showTitle)
        if (matchedSubSeason != null && seasonNumber > 0) {
            val rawEps = fetchTmdbRawSeasonEpisodes(tmdbId, matchedSubSeason.seasonNumber, apiKey)
            val cours = splitEpisodesIntoCours(rawEps)
            val targetCour = cours.getOrNull(seasonNumber - 1) ?: return emptyList()
            return targetCour.mapIndexed { idx, ep ->
                ep.copy(
                    seasonNumber = seasonNumber,
                    episodeNumber = idx + 1
                )
            }
        }

        val effectiveImdbId = imdbId ?: fetchTmdbImdbId(tmdbId, false, apiKey)
        val cinemetaVideos = if (!effectiveImdbId.isNullOrBlank()) fetchCinemetaVideos(effectiveImdbId) else emptyList()
        val tvdbId = (effectiveImdbId?.let { cinemetaTvdbIdCache[it] }) ?: tmdbTvdbIdCache[tmdbId]
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
                    return sliced.map { ep ->
                        val shMatch = skyHookEps.firstOrNull { it.episodeNumber == ep.episodeNumber }
                        ep.copy(
                            stillUrl = ep.stillUrl ?: shMatch?.stillUrl,
                            overview = ep.overview?.takeIf { it.isNotBlank() } ?: shMatch?.overview,
                            runtimeMinutes = ep.runtimeMinutes ?: shMatch?.runtimeMinutes
                        )
                    }
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

            return cmSeasonEps.mapIndexed { idx, ep ->
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
                val resolvedVote = tmdbMatch?.voteAverage ?: ep.voteAverage
                val resolvedRuntime = ep.runtimeMinutes ?: tmdbMatch?.runtimeMinutes ?: shMatch?.runtimeMinutes

                ep.copy(
                    stillUrl = resolvedStill,
                    overview = resolvedOverview,
                    runtimeMinutes = resolvedRuntime,
                    voteAverage = resolvedVote
                )
            }
        }

        if (skyHookEps.isNotEmpty()) {
            return skyHookEps
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
        return if (str.equals("null", ignoreCase = true) || str.isBlank()) null else str
    }

    private fun String?.cleanString(): String? {
        if (this == null) return null
        val trimmed = this.trim()
        return if (trimmed.equals("null", ignoreCase = true) || trimmed.isBlank()) null else trimmed
    }

    suspend fun fetchAnimeDetails(
        anilistId: Long? = null,
        malId: Long? = null,
        title: String? = null,
        existingSeasonsCount: Int = 1,
        mediaType: MediaType = MediaType.ANIME
    ): AnimeEnrichedDetails? = withContext(Dispatchers.IO) {
        try {
            val query = """
                query (${'$'}id: Int, ${'$'}idMal: Int, ${'$'}search: String, ${'$'}type: MediaType) {
                  Media(id: ${'$'}id, idMal: ${'$'}idMal, search: ${'$'}search, type: ${'$'}type) {
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
                val cleanTitle = title.replace(Regex("[:\\-_–]"), " ").trim()
                val baseTitle = title.split(":", "-", "–").firstOrNull()?.trim() ?: title
                candidateVars.add(buildJsonObject {
                    put("search", title.trim())
                    put("type", anilistMediaType)
                })
                if (cleanTitle != title.trim()) {
                    candidateVars.add(buildJsonObject {
                        put("search", cleanTitle)
                        put("type", anilistMediaType)
                    })
                }
                if (baseTitle != title.trim() && baseTitle.length >= 3) {
                    candidateVars.add(buildJsonObject {
                        put("search", baseTitle)
                        put("type", anilistMediaType)
                    })
                }
            }

            for (vars in candidateVars) {
                val jsonPayload = buildJsonObject {
                    put("query", query)
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
        val publishers: List<String>
    )

    data class MangaSeriesContent(
        val seasons: List<com.omnitrack.app.data.model.MediaSeason>,
        val episodesBySeason: Map<Int, List<com.omnitrack.app.data.model.MediaEpisode>>,
        val isSeasonGrouped: Boolean,
        val resolvedMangaDexId: String? = null,
        val totalChapters: Int? = null,
        val publishers: List<String> = emptyList()
    )

    private val mangaSeriesContentCache = java.util.concurrent.ConcurrentHashMap<String, MangaSeriesContent>()

    private suspend fun fetchMangaUpdatesInfo(title: String): MangaUpdatesSeriesInfo? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        try {
            val payload = buildJsonObject {
                put("search", title.trim())
                put("perpage", 5)
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
                val exact = results.firstOrNull { node ->
                    val t = node.jsonObject["record"]?.jsonObject?.get("title").cleanString()
                    t != null && t.equals(title.trim(), ignoreCase = true)
                } ?: results.firstOrNull()
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
                val statusText = root["status"].cleanString().orEmpty()
                val totalVols = Regex("""(\d+)\s+Volumes""", RegexOption.IGNORE_CASE)
                    .find(statusText)?.groupValues?.getOrNull(1)?.toIntOrNull()

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
                    publishers = publishers
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
                        val sCover = volumeCovers[sNum] ?: fallbackCoverUrl
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
                        val vCover = volumeCovers[vol] ?: fallbackCoverUrl
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
                        val vCover = volumeCovers[mappedVol] ?: fallbackCoverUrl
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
                    val sCover = volumeCovers[1] ?: fallbackCoverUrl
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

    suspend fun fetchAniZipTmdbId(anilistId: Long): Long? = withContext(Dispatchers.IO) {
        try {
            val root = fetchAniZipRoot(anilistId) ?: return@withContext null
            root["mappings"]?.jsonObject?.get("themoviedb_id")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
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
                val rating = o["rating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()

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
                        voteAverage = rating
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
}
