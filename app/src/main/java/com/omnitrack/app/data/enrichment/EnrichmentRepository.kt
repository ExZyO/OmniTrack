package com.omnitrack.app.data.enrichment

import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.network.NetworkPacer
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
    val metacriticScore: Int? = null
)

/**
 * Metadata Enrichment Waterfall.
 * Resolves transparent clearart title logos (Fanart.tv / TMDb logos) and multi-provider ratings
 * (IMDb, Rotten Tomatoes, Metacritic, Trakt) for maximum visual fidelity on the Detail canvas.
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

        // 1. Resolve Transparent Clearart Logo
        if (clearart.isNullOrBlank()) {
            if (!fanartApiKey.isNullOrBlank() && item.tmdbId != null) {
                clearart = fetchFanartClearart(item.tmdbId, item.mediaType == MediaType.MOVIE, fanartApiKey)
            }
            if (clearart.isNullOrBlank() && !tmdbApiKey.isNullOrBlank() && item.tmdbId != null) {
                clearart = fetchTmdbClearart(item.tmdbId, item.mediaType == MediaType.MOVIE, tmdbApiKey)
            }
        }

        // 2. Resolve Multi-Score Aggregation (IMDb, Rotten Tomatoes, Metacritic)
        if (imdbScore == null || rtScore == null || metacriticScore == null) {
            if (!mdblistApiKey.isNullOrBlank() && (item.tmdbId != null || item.title.isNotBlank())) {
                val mdbRatings = fetchMdbListRatings(item.tmdbId, item.title, mdblistApiKey)
                if (mdbRatings != null) {
                    if (imdbScore == null) imdbScore = mdbRatings.imdbScore
                    if (rtScore == null) rtScore = mdbRatings.rtScore
                    if (metacriticScore == null) metacriticScore = mdbRatings.metacriticScore
                }
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
                MediaType.NOVEL, MediaType.ANIME, MediaType.MANGA -> {
                    if (item.bangumiId != null) {
                        communityScore = fetchBangumiScore(item.bangumiId)
                    }
                }
                else -> {}
            }
        }

        EnrichmentData(
            clearartUrl = clearart,
            communityScore = communityScore,
            imdbScore = imdbScore,
            rtScore = rtScore,
            metacriticScore = metacriticScore
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

                    // Find English logo first, fallback to first entry
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

                    val bestLogo = logos.firstOrNull {
                        it.jsonObject["iso_639_1"]?.jsonPrimitive?.content == "en"
                    } ?: logos.firstOrNull()

                    val filePath = bestLogo?.jsonObject?.get("file_path")?.jsonPrimitive?.content
                    if (filePath != null) {
                        "https://image.tmdb.org/t/p/w500$filePath"
                    } else null
                }
            } catch (_: Exception) {
                null
            }
        }

    private data class MdbRatings(
        val imdbScore: Float? = null,
        val rtScore: Int? = null,
        val metacriticScore: Int? = null
    )

    private suspend fun fetchMdbListRatings(tmdbId: Long?, title: String, apiKey: String): MdbRatings? =
        NetworkPacer.withGeneralPacing {
            try {
                val queryParam = if (tmdbId != null) "tm=$tmdbId" else "m=${URLEncoder.encode(title, "UTF-8")}"
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

                    for (node in ratings) {
                        val obj = node.jsonObject
                        val source = obj["source"]?.jsonPrimitive?.content?.lowercase() ?: continue
                        val value = obj["value"]?.jsonPrimitive?.content?.toFloatOrNull() ?: continue
                        when (source) {
                            "imdb" -> imdb = value
                            "tomatoes" -> rt = value.toInt()
                            "metacritic" -> meta = value.toInt()
                        }
                    }
                    MdbRatings(imdbScore = imdb, rtScore = rt, metacriticScore = meta)
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
}
