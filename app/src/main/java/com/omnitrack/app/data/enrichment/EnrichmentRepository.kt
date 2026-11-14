package com.omnitrack.app.data.enrichment

import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.AnimeAiringSchedule
import com.omnitrack.app.data.model.AnimeCharacterItem
import com.omnitrack.app.data.model.AnimeEnrichedDetails
import com.omnitrack.app.data.model.AnimeRelationItem
import com.omnitrack.app.data.model.AnimeVoiceActorDetail
import com.omnitrack.app.data.model.VoiceActorRoleItem
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.ui.screens.detail.components.MediaTrailer
import kotlinx.coroutines.Dispatchers
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
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val req = original.newBuilder().apply {
                if (original.header("User-Agent") == null) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                }
                if (original.header("Accept") == null) {
                    header("Accept", "application/json")
                }
            }.build()
            chain.proceed(req)
        }
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val cinemetaVideosCache = java.util.concurrent.ConcurrentHashMap<String, List<com.omnitrack.app.data.model.MediaEpisode>>()
    private val tmdbSeason1StillsCache = java.util.concurrent.ConcurrentHashMap<Long, List<com.omnitrack.app.data.model.MediaEpisode>>()
    private val aniListSeasonPosterCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val kitsuSeasonDataCache = java.util.concurrent.ConcurrentHashMap<String, KitsuSeasonData>()

    suspend fun enrich(
        item: MediaItemEntity,
        fanartApiKey: String? = null,
        tmdbApiKey: String? = null,
        mdblistApiKey: String? = null
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

        // Auto-resolve TMDb ID for Anime if missing so Anime receives Clearart, Trailers, and MDBList scores
        if (effectiveTmdbId == null && item.mediaType == MediaType.ANIME && item.title.isNotBlank()) {
            val animeTmdb = searchTmdbForAnime(item.title, effectiveTmdbKey, item.nativeTitle)
            if (animeTmdb != null) {
                effectiveTmdbId = animeTmdb.first
                isMovie = animeTmdb.second
                isShow = !animeTmdb.second
            }
        }

        // 1. Resolve Transparent Clearart Logo
        if (clearart.isNullOrBlank() && effectiveTmdbId != null) {
            if (!fanartApiKey.isNullOrBlank()) {
                clearart = fetchFanartClearart(effectiveTmdbId, isMovie, fanartApiKey)
            }
            if (clearart.isNullOrBlank() && !tmdbApiKey.isNullOrBlank()) {
                clearart = fetchTmdbClearart(effectiveTmdbId, isMovie, effectiveTmdbKey)
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
                        communityScore = fetchJikanScore(item.malId)
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

                    val best = logos.firstOrNull {
                        it.jsonObject["lang"]?.jsonPrimitive?.content == "en"
                    } ?: logos.firstOrNull()

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
                val url = "https://api.themoviedb.org/3/$typeStr/$tmdbId/images?api_key=$apiKey"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withTmdbPacing null
                    val body = response.body?.string() ?: return@withTmdbPacing null
                    val root = json.parseToJsonElement(body).jsonObject
                    val logos = root["logos"]?.jsonArray ?: return@withTmdbPacing null
                    val best = logos.firstOrNull {
                        it.jsonObject["iso_639_1"]?.jsonPrimitive?.content == "en"
                    } ?: logos.firstOrNull()

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

    private suspend fun fetchJikanScore(malId: Long): Float? {
        return try {
            val url = "https://api.jikan.moe/v4/anime/$malId"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { response ->
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
        try {
            val url = "https://v3-cinemeta.strem.io/meta/series/$imdbId.json"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val meta = root["meta"]?.jsonObject ?: return@withContext emptyList()
                val videos = meta["videos"]?.jsonArray ?: return@withContext emptyList()
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchTvSeasons(
        tmdbId: Long,
        apiKey: String,
        showTitle: String? = null,
        imdbId: String? = null
    ): List<com.omnitrack.app.data.model.MediaSeason> = NetworkPacer.withTmdbPacing {
        val tmdbSeasons = try {
            val url = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$apiKey"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) emptyList()
                else {
                    val body = response.body?.string() ?: return@use emptyList()
                    val root = json.parseToJsonElement(body).jsonObject
                    val seasons = root["seasons"]?.jsonArray ?: return@use emptyList()
                    seasons.mapNotNull { node ->
                        val obj = node.jsonObject
                        val seasonNumber = obj["season_number"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                        val cleanName = if (seasonNumber <= 0) "Specials" else "Season $seasonNumber"
                        val epCount = obj["episode_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val posterPath = obj["poster_path"]?.jsonPrimitive?.content
                        val airDate = obj["air_date"]?.jsonPrimitive?.content
                        // Retain upcoming/announced seasons even before episode counts are locked in TMDb
                        if (seasonNumber == 0 && epCount <= 0) return@mapNotNull null
                        com.omnitrack.app.data.model.MediaSeason(
                            seasonNumber = seasonNumber,
                            name = cleanName,
                            episodeCount = epCount,
                            posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                            airDate = airDate
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }

        // Cinemeta fallback & merge for missing anime seasons (e.g. Frieren Season 2 with 10 announced/ongoing episodes)
        val effectiveImdbId = imdbId ?: fetchTmdbImdbId(tmdbId, false, apiKey)
        if (!effectiveImdbId.isNullOrBlank()) {
            val cinemetaVideos = fetchCinemetaVideos(effectiveImdbId)
            if (cinemetaVideos.isNotEmpty()) {
                val cinemetaSeasons = cinemetaVideos.groupBy { it.seasonNumber }
                val mergedList = tmdbSeasons.toMutableList()
                cinemetaSeasons.forEach { (sNum, eps) ->
                    val existing = mergedList.find { it.seasonNumber == sNum }
                    if (existing == null) {
                        // Season not in TMDb (e.g. Frieren Season 2 or Apothecary Diaries Season 2/3 in Cinemeta)
                        val kitsuData = if (!showTitle.isNullOrBlank()) fetchKitsuSeasonData(showTitle, sNum) else null
                        val aniListPoster = if (!showTitle.isNullOrBlank()) fetchAniListSeasonPoster(showTitle, sNum) else null
                        val s2Poster = mergedList.find { it.seasonNumber == 2 }?.posterUrl

                        val resolvedPoster = when {
                            // For Season 3+, prioritize dedicated official key visual (e.g. Kitsu TVアニメ 第3期)
                            // or prevent AniList from duplicating Season 2's cropped visual
                            sNum >= 3 && !kitsuData?.posterUrl.isNullOrBlank() -> kitsuData.posterUrl
                            sNum >= 3 && aniListPoster != null && aniListPoster == s2Poster -> kitsuData?.posterUrl ?: aniListPoster
                            else -> aniListPoster ?: kitsuData?.posterUrl
                        }

                        val posterUrl = if (sNum <= 0) {
                            mergedList.find { it.seasonNumber == 0 }?.posterUrl ?: resolvedPoster
                        } else if (sNum == 1) {
                            mergedList.find { it.seasonNumber == 1 }?.posterUrl ?: resolvedPoster
                        } else {
                            // Distinct season poster for Season 2, Season 3, etc. NEVER fall back to Season 1!
                            resolvedPoster
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
                    } else if (existing.episodeCount <= 0 && eps.isNotEmpty()) {
                        val idx = mergedList.indexOf(existing)
                        val kitsuData = if (!showTitle.isNullOrBlank()) fetchKitsuSeasonData(showTitle, sNum) else null
                        val aniListPoster = if (!showTitle.isNullOrBlank()) fetchAniListSeasonPoster(showTitle, sNum) else null
                        val resolvedPoster = if (sNum >= 3 && !kitsuData?.posterUrl.isNullOrBlank()) {
                            kitsuData.posterUrl
                        } else {
                            aniListPoster ?: kitsuData?.posterUrl ?: existing.posterUrl
                        }
                        val enhancedPoster = if (existing.posterUrl.isNullOrBlank()) resolvedPoster else existing.posterUrl
                        mergedList[idx] = existing.copy(episodeCount = eps.size, posterUrl = enhancedPoster)
                    }
                }
                return@withTmdbPacing mergedList
                    .distinctBy { it.seasonNumber }
                    .sortedWith(compareBy({ it.seasonNumber == 0 }, { it.seasonNumber }))
            }
        }

        return@withTmdbPacing tmdbSeasons
            .distinctBy { it.seasonNumber }
            .sortedWith(compareBy({ it.seasonNumber == 0 }, { it.seasonNumber }))
    }

    suspend fun fetchTvSeasonEpisodes(
        tmdbId: Long,
        seasonNumber: Int,
        apiKey: String,
        imdbId: String? = null,
        showTitle: String? = null
    ): List<com.omnitrack.app.data.model.MediaEpisode> = NetworkPacer.withTmdbPacing {
        val tmdbEpisodes = try {
            val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber?api_key=$apiKey"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) emptyList()
                else {
                    val body = response.body?.string() ?: return@use emptyList()
                    val root = json.parseToJsonElement(body).jsonObject
                    val episodes = root["episodes"]?.jsonArray ?: return@use emptyList()
                    episodes.mapNotNull { node ->
                        val obj = node.jsonObject
                        val epNumber = obj["episode_number"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                        val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: (seasonNumber * 1000L + epNumber)
                        val name = obj["name"]?.jsonPrimitive?.content ?: "Episode $epNumber"
                        val overview = obj["overview"]?.jsonPrimitive?.content
                        val stillPath = obj["still_path"]?.jsonPrimitive?.content
                        val airDate = obj["air_date"]?.jsonPrimitive?.content
                        val runtime = obj["runtime"]?.jsonPrimitive?.content?.toIntOrNull()
                        val voteAverage = obj["vote_average"]?.jsonPrimitive?.content?.toFloatOrNull()
                        com.omnitrack.app.data.model.MediaEpisode(
                            id = id,
                            episodeNumber = epNumber,
                            seasonNumber = seasonNumber,
                            title = name,
                            overview = overview,
                            stillUrl = stillPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                            airDate = airDate,
                            runtimeMinutes = runtime,
                            voteAverage = voteAverage
                        )
                    }.sortedBy { it.episodeNumber }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }

        if (tmdbEpisodes.isNotEmpty()) {
            return@withTmdbPacing tmdbEpisodes
        }

        // If TMDb has no episodes for this season (e.g. anime split seasons or Frieren Season 2), fallback to Cinemeta!
        val effectiveImdbId = imdbId ?: fetchTmdbImdbId(tmdbId, false, apiKey)
        if (!effectiveImdbId.isNullOrBlank()) {
            val cinemetaVideos = fetchCinemetaVideos(effectiveImdbId)
            val seasonEps = cinemetaVideos.filter { it.seasonNumber == seasonNumber }.sortedBy { it.episodeNumber }
            if (seasonEps.isNotEmpty()) {
                // Cross-reference with TMDb Season 1 for anime using continuous absolute numbering (e.g. Apothecary Diaries S2/S3, Attack on Titan, etc.)
                val tmdbSeason1Eps = tmdbSeason1StillsCache[tmdbId] ?: try {
                    val s1Url = "https://api.themoviedb.org/3/tv/$tmdbId/season/1?api_key=$apiKey"
                    val s1Req = Request.Builder().url(s1Url).get().build()
                    client.newCall(s1Req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val s1Body = resp.body?.string() ?: return@use emptyList()
                            val s1Root = json.parseToJsonElement(s1Body).jsonObject
                            val eps = s1Root["episodes"]?.jsonArray ?: return@use emptyList()
                            val mapped = eps.mapNotNull { node ->
                                val o = node.jsonObject
                                val name = o["name"]?.jsonPrimitive?.content
                                val still = o["still_path"]?.jsonPrimitive?.content?.let { "https://image.tmdb.org/t/p/w500$it" }
                                val air = o["air_date"]?.jsonPrimitive?.content
                                val overview = o["overview"]?.jsonPrimitive?.content
                                val rating = o["vote_average"]?.jsonPrimitive?.content?.toFloatOrNull()
                                if (name != null || air != null) {
                                    com.omnitrack.app.data.model.MediaEpisode(
                                        id = 0L,
                                        episodeNumber = o["episode_number"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                        seasonNumber = 1,
                                        title = name ?: "",
                                        overview = overview,
                                        stillUrl = still,
                                        airDate = air,
                                        runtimeMinutes = null,
                                        voteAverage = rating
                                    )
                                } else null
                            }
                            if (mapped.isNotEmpty()) {
                                tmdbSeason1StillsCache[tmdbId] = mapped
                            }
                            mapped
                        } else emptyList()
                    }
                } catch (_: Exception) {
                    emptyList()
                }

                val kitsuData = if (!showTitle.isNullOrBlank()) fetchKitsuSeasonData(showTitle, seasonNumber) else null
                val enrichedEps = seasonEps.map { ep ->
                    // 1. Cross-reference TMDb absolute numbering by exact title or air date
                    val tmdbMatch = tmdbSeason1Eps.firstOrNull { t ->
                        !t.title.isBlank() && t.title.trim().equals(ep.title.trim(), ignoreCase = true)
                    } ?: tmdbSeason1Eps.firstOrNull { t ->
                        !t.airDate.isNullOrBlank() && !ep.airDate.isNullOrBlank() && t.airDate == ep.airDate
                    }

                    val kitsuStill = kitsuData?.stills?.get(ep.episodeNumber)

                    val resolvedStill = tmdbMatch?.stillUrl
                        ?: kitsuStill
                        ?: if (ep.stillUrl?.contains("episodes.metahub.space") == true) null else ep.stillUrl

                    val resolvedOverview = if (ep.overview.isNullOrBlank()) tmdbMatch?.overview else ep.overview
                    val resolvedVote = tmdbMatch?.voteAverage ?: ep.voteAverage

                    ep.copy(
                        stillUrl = resolvedStill,
                        overview = resolvedOverview,
                        voteAverage = resolvedVote
                    )
                }
                return@withTmdbPacing enrichedEps
            }
        }

        return@withTmdbPacing emptyList()
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

            val stills = mutableMapOf<Int, String>()
            var offset = 0
            while (offset < 60) {
                val epUrl = "https://kitsu.io/api/edge/anime/$animeId/episodes?page[limit]=20&page[offset]=$offset"
                val epReq = Request.Builder()
                    .url(epUrl)
                    .header("Accept", "application/vnd.api+json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val items = client.newCall(epReq).execute().use { epResp ->
                    if (!epResp.isSuccessful) return@use emptyList()
                    val epBody = epResp.body?.string() ?: return@use emptyList()
                    val epRoot = json.parseToJsonElement(epBody).jsonObject
                    epRoot["data"]?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
                }
                if (items.isEmpty()) break
                for (ep in items) {
                    val attrs = ep["attributes"]?.jsonObject
                    val epNum = attrs?.get("number")?.jsonPrimitive?.content?.toIntOrNull()
                    val thumbObj = attrs?.get("thumbnail")?.jsonObject
                    val thumbUrl = thumbObj?.get("large")?.jsonPrimitive?.content
                        ?: thumbObj?.get("original")?.jsonPrimitive?.content
                        ?: thumbObj?.get("medium")?.jsonPrimitive?.content
                    if (epNum != null && !thumbUrl.isNullOrBlank()) {
                        stills[epNum] = thumbUrl
                    }
                }
                if (items.size < 20) break
                offset += 20
            }

            val result = KitsuSeasonData(posterUrl = posterUrl, stills = stills)
            kitsuSeasonDataCache[cacheKey] = result
            result
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchAniListSeasonPoster(title: String, seasonNumber: Int): String? = withContext(Dispatchers.IO) {
        val strippedTitle = title
            .replace(Regex("(?i)\\s*(?:season|\\d+(?:st|nd|rd|th)\\s+season|part)\\s*\\d*.*"), "")
            .split(":", "-", "–").firstOrNull()?.trim()
            ?.takeIf { it.isNotBlank() } ?: title
        val cacheKey = "$strippedTitle:$seasonNumber"
        aniListSeasonPosterCache[cacheKey]?.let { return@withContext it }

        try {
            val query = """
                query (${'$'}search: String) {
                  Media(search: ${'$'}search, type: ANIME) {
                    coverImage { extraLarge large }
                  }
                }
            """.trimIndent()
            val searchQueries = if (seasonNumber <= 1) {
                listOf(strippedTitle)
            } else {
                listOf(
                    "$strippedTitle Season $seasonNumber",
                    "$strippedTitle ${seasonNumber}nd Season",
                    "$strippedTitle ${seasonNumber}rd Season"
                )
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
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val root = json.parseToJsonElement(body).jsonObject
                        val media = root["data"]?.jsonObject?.get("Media")?.jsonObject
                        val cover = media?.get("coverImage")?.jsonObject
                        val img = cover?.get("extraLarge")?.jsonPrimitive?.content
                            ?: cover?.get("large")?.jsonPrimitive?.content
                        if (!img.isNullOrBlank()) {
                            aniListSeasonPosterCache[cacheKey] = img
                            return@withContext img
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
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

    suspend fun fetchAnimeDetails(
        anilistId: Long? = null,
        malId: Long? = null,
        title: String? = null,
        existingSeasonsCount: Int = 1
    ): AnimeEnrichedDetails? = withContext(Dispatchers.IO) {
        try {
            val query = """
                query (${'$'}id: Int, ${'$'}idMal: Int, ${'$'}search: String) {
                  Media(id: ${'$'}id, idMal: ${'$'}idMal, search: ${'$'}search) {
                    id
                    idMal
                    title {
                      romaji
                      english
                      native
                    }
                    format
                    status
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
                          }
                          coverImage {
                            large
                            extraLarge
                          }
                        }
                      }
                    }
                    studios(isMain: true) {
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

            // Safe lookup: Only send ONE mutually exclusive variable to avoid AND-filtering in AniList
            val candidateVars = mutableListOf<JsonObject>()
            if (anilistId != null && anilistId > 0) {
                candidateVars.add(buildJsonObject { put("id", anilistId) })
            }
            if (malId != null && malId > 0) {
                candidateVars.add(buildJsonObject { put("idMal", malId) })
            }
            if (!title.isNullOrBlank()) {
                val cleanTitle = title.replace(Regex("[:\\-_–]"), " ").trim()
                val baseTitle = title.split(":", "-", "–").firstOrNull()?.trim() ?: title
                candidateVars.add(buildJsonObject { put("search", title.trim()) })
                if (cleanTitle != title.trim()) candidateVars.add(buildJsonObject { put("search", cleanTitle) })
                if (baseTitle != title.trim() && baseTitle.length >= 3) candidateVars.add(buildJsonObject { put("search", baseTitle) })
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

                    val mediaSynopsis = media["description"].cleanString()
                    val mediaBanner = media["bannerImage"].cleanString()
                    val mediaCover = media["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                        ?: media["coverImage"]?.jsonObject?.get("large").cleanString()
                    val mediaScore = media["averageScore"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.let { it / 10f }
                    val mediaEpisodes = media["episodes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val mediaChapters = media["chapters"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val mediaGenres = media["genres"]?.jsonArray.orEmpty().mapNotNull { it.cleanString() }

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

                    // 3. Franchise & Relations with smart season deduplication
                    val relationList = mutableListOf<AnimeRelationItem>()
                    val relEdges = media["relations"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                    val baseTitleNorm = (title ?: "").lowercase().replace(Regex("[^a-z0-9]"), "")

                    for (edgeElem in relEdges) {
                        val edge = edgeElem.jsonObject
                        val relType = edge["relationType"].cleanString() ?: continue
                        val node = edge["node"]?.jsonObject ?: continue
                        val relId = node["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                        val relFormat = node["format"].cleanString() ?: "TV"
                        val relTypeCategory = node["type"].cleanString() ?: "ANIME"
                        val english = node["title"]?.jsonObject?.get("english").cleanString()
                        val romaji = node["title"]?.jsonObject?.get("romaji").cleanString()
                        val relTitle = english ?: romaji ?: "Unknown Title"
                        val cover = node["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                            ?: node["coverImage"]?.jsonObject?.get("large").cleanString()
                        val year = node["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()

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
                                year = year,
                                mediaType = targetMediaType
                            )
                        )
                    }

                    // 4. Studios (Filtered by isAnimationStudio == true per Safeguard 6)
                    val studioList = mutableListOf<String>()
                    val studioEdges = media["studios"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                    for (edgeElem in studioEdges) {
                        val node = edgeElem.jsonObject["node"]?.jsonObject ?: continue
                        val isAnim = node["isAnimationStudio"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                        val studioName = node["name"].cleanString()
                        if (isAnim && !studioName.isNullOrBlank()) {
                            studioList.add(studioName)
                        }
                    }

                    AnimeEnrichedDetails(
                        anilistId = resolvedId,
                        malId = resolvedMalId,
                        characters = charList,
                        relations = relationList,
                        nextAiring = airingSchedule,
                        animationStudios = studioList,
                        synopsis = mediaSynopsis,
                        bannerUrl = mediaBanner,
                        coverUrl = mediaCover,
                        averageScore = mediaScore,
                        totalEpisodes = mediaEpisodes,
                        totalChapters = mediaChapters,
                        genres = mediaGenres
                    )
                }

                if (result != null) return@withContext result
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchVoiceActorDetails(staffId: Long): AnimeVoiceActorDetail? = withContext(Dispatchers.IO) {
        try {
            val query = """
                query (${'$'}id: Int) {
                  Staff(id: ${'$'}id) {
                    id
                    name {
                      full
                      native
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
                    homeTown
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
                val img = staff["image"]?.jsonObject?.get("large").cleanString()
                val rawBio = staff["description"].cleanString()
                val cleanBio = rawBio
                    ?.replace(Regex("""__.*?__""")) { it.value.removeSurrounding("__") }
                    ?.replace(Regex("""\*\*.*?\*\*""")) { it.value.removeSurrounding("**") }
                    ?.replace(Regex("""~{2,}.*?~{2,}"""), "")
                    ?.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                    ?.replace(Regex("""<.*?>"""), "")
                    ?.trim()

                val age = staff["age"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val dobObj = staff["dateOfBirth"]?.let { if (it is JsonObject) it else null }
                val y = dobObj?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val m = dobObj?.get("month")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val d = dobObj?.get("day")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val dobStr = if (y != null && m != null && d != null) "$y-$m-$d" else if (m != null && d != null) "$m-$d" else null
                val home = staff["homeTown"].cleanString()
                val occupations = staff["primaryOccupations"]?.jsonArray.orEmpty().mapNotNull { it.cleanString() }

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
                    val mTitle = eng ?: rom ?: "Media"
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

                AnimeVoiceActorDetail(
                    staffId = sId,
                    name = name,
                    nativeName = nativeName,
                    imageUrl = img,
                    biography = cleanBio,
                    age = age,
                    dateOfBirth = dobStr,
                    homeTown = home,
                    primaryOccupations = occupations,
                    roles = rolesList
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
