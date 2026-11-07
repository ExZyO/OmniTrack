package com.omnitrack.app.data.enrichment

import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.ui.screens.detail.components.MediaTrailer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
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
    val videos: List<MediaTrailer> = emptyList()
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
            val req = chain.request().newBuilder()
                .header("User-Agent", "OmniTrack/1.0.0 (EnrichmentEngine)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(req)
        }
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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

        // 1. Resolve Transparent Clearart Logo
        if (clearart.isNullOrBlank()) {
            if (!fanartApiKey.isNullOrBlank() && item.tmdbId != null) {
                clearart = fetchFanartClearart(item.tmdbId, item.mediaType == MediaType.MOVIE, fanartApiKey)
            }
            if (clearart.isNullOrBlank() && !tmdbApiKey.isNullOrBlank() && item.tmdbId != null) {
                clearart = fetchTmdbClearart(item.tmdbId, item.mediaType == MediaType.MOVIE, effectiveTmdbKey)
            }
        }

        val isMovie = item.mediaType == MediaType.MOVIE
        val isShow = item.mediaType == MediaType.SHOW

        var resolvedImdbId: String? = null
        if ((isMovie || isShow) && item.tmdbId != null) {
            resolvedImdbId = fetchTmdbImdbId(item.tmdbId, isMovie, effectiveTmdbKey)
        }

        // 2. Resolve Multi-Score Aggregation (IMDb, Rotten Tomatoes, Metacritic, Trakt, Audience)
        // Check if cached scores are stale or missing (e.g. The Mentalist movie collision with imdb=6.7)
        val hasStaleScores = imdbScore == 6.7f || (imdbScore != null && rtScore != null && traktScore == null)
        val needsRatings = imdbScore == null || rtScore == null || metacriticScore == null || traktScore == null || audienceScore == null || hasStaleScores

        if (!mdblistApiKey.isNullOrBlank() && needsRatings && (resolvedImdbId != null || item.tmdbId != null || item.title.isNotBlank())) {
            val mdbRatings = fetchMdbListRatings(resolvedImdbId, item.tmdbId, isMovie, item.title, mdblistApiKey)
            if (mdbRatings != null) {
                if (mdbRatings.imdbScore != null) imdbScore = mdbRatings.imdbScore
                if (mdbRatings.rtScore != null) rtScore = mdbRatings.rtScore
                if (mdbRatings.metacriticScore != null) metacriticScore = mdbRatings.metacriticScore
                if (mdbRatings.traktScore != null) traktScore = mdbRatings.traktScore
                if (mdbRatings.audienceScore != null) audienceScore = mdbRatings.audienceScore
                if (mdbRatings.tmdbScore != null && (isMovie || isShow)) communityScore = mdbRatings.tmdbScore
            }
        }

        // Zero-Key Fallback for IMDb (Cinemeta public Stremio endpoint)
        if (imdbScore == null && (resolvedImdbId != null || item.tmdbId != null)) {
            val finalImdbId = resolvedImdbId ?: item.tmdbId?.let { fetchTmdbImdbId(it, isMovie, effectiveTmdbKey) }
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

        if (item.tmdbId != null) {
            val details = fetchTmdbDetails(item.tmdbId, item.mediaType == MediaType.MOVIE, effectiveTmdbKey)
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
            videos = videoList
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

    private suspend fun fetchTmdbImdbId(tmdbId: Long, isMovie: Boolean, apiKey: String): String? =
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

    private suspend fun fetchMdbListRatings(
        imdbId: String?,
        tmdbId: Long?,
        isMovie: Boolean,
        title: String,
        apiKey: String
    ): MdbRatings? = NetworkPacer.withGeneralPacing {
        try {
            val mediaType = if (isMovie) "movie" else "show"
            val queryParam = if (!imdbId.isNullOrBlank()) {
                "i=$imdbId&m=$mediaType"
            } else if (tmdbId != null) {
                "tm=$tmdbId&m=$mediaType"
            } else {
                "m=${URLEncoder.encode(title, "UTF-8")}&s=$mediaType"
            }
            val url = "https://mdblist.com/api/?apikey=$apiKey&$queryParam"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing null
                val body = response.body?.string() ?: return@withGeneralPacing null
                val root = json.parseToJsonElement(body).jsonObject
                val ratings = root["ratings"]?.jsonArray ?: return@withGeneralPacing null

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
        } catch (_: Exception) {
            null
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
        val videos: List<MediaTrailer> = emptyList()
    )

    private suspend fun fetchTmdbDetails(tmdbId: Long, isMovie: Boolean, tmdbKey: String): ExtraDetails? {
        return try {
            val apiKey = tmdbKey
            val type = if (isMovie) "movie" else "tv"
            val append = if (isMovie) "videos,release_dates" else "videos,content_ratings"
            val url = "https://api.themoviedb.org/3/$type/$tmdbId?api_key=$apiKey&append_to_response=$append"
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
                    videos = videoList
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
