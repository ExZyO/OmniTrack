package com.omnitrack.app.data.search

import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.network.NetworkPacer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Universal Multi-Vertical OmniSearch Engine.
 * Runs parallel async queries across AniList, MangaDex, Bangumi, TMDb/TVMaze, Steam, and VNDB.
 * Every call is strictly paced through [NetworkPacer] to avoid HTTP 429 rate limits.
 */
class OmniSearchRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "OmniTrack/1.0.0 (Android; native AMOLED tracker)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(req)
        }
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun searchAll(
        query: String,
        filter: MediaType = MediaType.ALL,
        tmdbApiKey: String? = null
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        coroutineScope {
            val deferreds = mutableListOf<kotlinx.coroutines.Deferred<List<SearchResultItem>>>()

            when (filter) {
                MediaType.ALL -> {
                    deferreds.add(async { searchAniList(trimmed, isAnime = true) })
                    deferreds.add(async { searchAniList(trimmed, isAnime = false) })
                    deferreds.add(async { searchBangumi(trimmed, type = 1, defaultType = MediaType.NOVEL) })
                    deferreds.add(async { searchShowsAndMovies(trimmed, tmdbApiKey) })
                    deferreds.add(async { searchSteam(trimmed) })
                    deferreds.add(async { searchVndb(trimmed) })
                }
                MediaType.ANIME -> {
                    deferreds.add(async { searchAniList(trimmed, isAnime = true) })
                    deferreds.add(async { searchBangumi(trimmed, type = 2, defaultType = MediaType.ANIME) })
                }
                MediaType.MANGA -> {
                    deferreds.add(async { searchMangaDex(trimmed) })
                    deferreds.add(async { searchAniList(trimmed, isAnime = false, formatNovel = false) })
                    deferreds.add(async { searchBangumi(trimmed, type = 1, defaultType = MediaType.MANGA) })
                }
                MediaType.NOVEL -> {
                    // Bangumi is king for CN/KR/JP web novels & light novels (e.g. LOTM, ORV, Reverend Insanity)
                    deferreds.add(async { searchBangumi(trimmed, type = 1, defaultType = MediaType.NOVEL) })
                    deferreds.add(async { searchAniList(trimmed, isAnime = false, formatNovel = true) })
                }
                MediaType.SHOW -> {
                    deferreds.add(async { searchShowsAndMovies(trimmed, tmdbApiKey, preferType = MediaType.SHOW) })
                }
                MediaType.MOVIE -> {
                    deferreds.add(async { searchShowsAndMovies(trimmed, tmdbApiKey, preferType = MediaType.MOVIE) })
                }
                MediaType.GAME -> {
                    deferreds.add(async { searchSteam(trimmed) })
                    deferreds.add(async { searchBangumi(trimmed, type = 4, defaultType = MediaType.GAME) })
                }
                MediaType.VISUAL_NOVEL -> {
                    deferreds.add(async { searchVndb(trimmed) })
                    deferreds.add(async { searchBangumi(trimmed, type = 4, defaultType = MediaType.VISUAL_NOVEL) })
                }
            }

            val rawResults = deferreds.awaitAll().flatten()

            // Deduplicate by clean lower-case title and media vertical
            val seen = mutableSetOf<String>()
            val deduped = mutableListOf<SearchResultItem>()
            for (item in rawResults) {
                val key = "${item.mediaType.name}:${item.title.trim().lowercase()}"
                if (seen.add(key)) {
                    deduped.add(item)
                }
            }
            deduped
        }
    }

    private suspend fun searchAniList(
        query: String,
        isAnime: Boolean,
        formatNovel: Boolean = false
    ): List<SearchResultItem> = NetworkPacer.withAniListPacing {
        try {
            val typeStr = if (isAnime) "ANIME" else "MANGA"
            val formatParam = if (formatNovel) ", format: NOVEL" else if (!isAnime) ", format_not: NOVEL" else ""
            val gql = """
                query (${'$'}search: String) {
                    Page(page: 1, perPage: 8) {
                        media(search: ${'$'}search, type: $typeStr$formatParam) {
                            id
                            idMal
                            title { romaji english native }
                            coverImage { extraLarge large }
                            bannerImage
                            description(asHtml: false)
                            averageScore
                            startDate { year }
                            episodes
                            chapters
                        }
                    }
                }
            """.trimIndent()

            val payload = buildJsonObject {
                put("query", gql)
                putJsonObject("variables") {
                    put("search", query)
                }
            }

            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withAniListPacing emptyList()
                val body = response.body?.string() ?: return@withAniListPacing emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val mediaList = root["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
                    ?: return@withAniListPacing emptyList()

                mediaList.mapNotNull { node ->
                    val obj = node.jsonObject
                    val titleObj = obj["title"]?.jsonObject
                    val title = titleObj?.get("romaji")?.jsonPrimitive?.content
                        ?: titleObj?.get("english")?.jsonPrimitive?.content
                        ?: titleObj?.get("native")?.jsonPrimitive?.content
                        ?: return@mapNotNull null

                    val nativeTitle = titleObj?.get("native")?.jsonPrimitive?.content
                    val cover = obj["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.content
                        ?: obj["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content
                        ?: return@mapNotNull null
                    val banner = obj["bannerImage"]?.jsonPrimitive?.content
                    val synopsis = obj["description"]?.jsonPrimitive?.content
                        ?.replace(Regex("<[^>]*>"), "")
                        ?.take(400)
                    val score = obj["averageScore"]?.jsonPrimitive?.content?.toFloatOrNull()?.let { it / 10f }
                    val year = obj["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.content
                    val anilistId = obj["id"]?.jsonPrimitive?.content?.toLongOrNull()
                    val malId = obj["idMal"]?.jsonPrimitive?.content?.toLongOrNull()

                    val targetType = when {
                        formatNovel -> MediaType.NOVEL
                        isAnime -> MediaType.ANIME
                        else -> MediaType.MANGA
                    }

                    val totalProgress = if (isAnime) {
                        obj["episodes"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    } else {
                        obj["chapters"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    }

                    SearchResultItem(
                        title = title,
                        nativeTitle = nativeTitle,
                        synopsis = synopsis,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                        bannerUrl = banner?.let { LosslessCoverMapper.toLosslessUrl(it) },
                        mediaType = targetType,
                        releaseYear = year,
                        communityScore = score,
                        sourceProvider = "AniList",
                        anilistId = anilistId,
                        malId = malId,
                        totalProgress = totalProgress
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchMangaDex(query: String): List<SearchResultItem> = NetworkPacer.withMangaDexPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://api.mangadex.org/manga?title=$encoded&limit=8&includes[]=cover_art")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withMangaDexPacing emptyList()
                val body = response.body?.string() ?: return@withMangaDexPacing emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonArray ?: return@withMangaDexPacing emptyList()

                data.mapNotNull { item ->
                    val obj = item.jsonObject
                    val mangaId = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val attrs = obj["attributes"]?.jsonObject ?: return@mapNotNull null

                    val titleMap = attrs["title"]?.jsonObject
                    val title = titleMap?.get("en")?.jsonPrimitive?.content
                        ?: titleMap?.values?.firstOrNull()?.jsonPrimitive?.content
                        ?: return@mapNotNull null

                    val altTitles = attrs["altTitles"]?.jsonArray
                    val nativeTitle = altTitles?.firstOrNull()?.jsonObject?.values?.firstOrNull()?.jsonPrimitive?.content

                    val synopsis = attrs["description"]?.jsonObject?.get("en")?.jsonPrimitive?.content
                        ?.replace(Regex("<[^>]*>"), "")
                        ?.take(400)
                    val year = attrs["year"]?.jsonPrimitive?.content

                    var coverFile: String? = null
                    val relationships = obj["relationships"]?.jsonArray ?: emptyList()
                    for (rel in relationships) {
                        if (rel.jsonObject["type"]?.jsonPrimitive?.content == "cover_art") {
                            coverFile = rel.jsonObject["attributes"]?.jsonObject?.get("fileName")?.jsonPrimitive?.content
                            if (coverFile != null) break
                        }
                    }

                    val coverUrl = if (coverFile != null) {
                        "https://uploads.mangadex.org/covers/$mangaId/$coverFile"
                    } else return@mapNotNull null

                    SearchResultItem(
                        title = title,
                        nativeTitle = nativeTitle,
                        synopsis = synopsis,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(coverUrl),
                        mediaType = MediaType.MANGA,
                        releaseYear = year,
                        sourceProvider = "MangaDex"
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchBangumi(
        query: String,
        type: Int,
        defaultType: MediaType
    ): List<SearchResultItem> = NetworkPacer.withBangumiPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://api.bgm.tv/search/subject/$encoded?type=$type&responseGroup=medium&max_results=8")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withBangumiPacing emptyList()
                val body = response.body?.string() ?: return@withBangumiPacing emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val list = root["list"]?.jsonArray ?: return@withBangumiPacing emptyList()

                list.mapNotNull { node ->
                    val obj = node.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
                    val nameCn = obj["name_cn"]?.jsonPrimitive?.content
                    val nameOrig = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val title = if (!nameCn.isNullOrBlank()) nameCn else nameOrig
                    val nativeTitle = if (nameCn != null && nameCn != nameOrig) nameOrig else null

                    val images = obj["images"]?.jsonObject
                    val cover = images?.get("large")?.jsonPrimitive?.content
                        ?: images?.get("common")?.jsonPrimitive?.content
                        ?: return@mapNotNull null

                    val summary = obj["summary"]?.jsonPrimitive?.content?.take(400)
                    val score = obj["rating"]?.jsonObject?.get("score")?.jsonPrimitive?.content?.toFloatOrNull()
                    val airDate = obj["air_date"]?.jsonPrimitive?.content
                    val year = airDate?.split("-")?.firstOrNull()

                    SearchResultItem(
                        title = title,
                        nativeTitle = nativeTitle,
                        synopsis = summary,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                        mediaType = defaultType,
                        releaseYear = year,
                        communityScore = score,
                        sourceProvider = "Bangumi",
                        bangumiId = id
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchShowsAndMovies(
        query: String,
        tmdbApiKey: String?,
        preferType: MediaType? = null
    ): List<SearchResultItem> {
        // If TMDB API key is available, use TMDb multi-search; otherwise fallback to TVMaze
        return if (!tmdbApiKey.isNullOrBlank()) {
            searchTmdb(query, tmdbApiKey, preferType)
        } else {
            searchTvMaze(query)
        }
    }

    private suspend fun searchTmdb(
        query: String,
        apiKey: String,
        preferType: MediaType? = null
    ): List<SearchResultItem> = NetworkPacer.withTmdbPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = if (preferType == MediaType.MOVIE) {
                "https://api.themoviedb.org/3/search/movie?api_key=$apiKey&query=$encoded"
            } else if (preferType == MediaType.SHOW) {
                "https://api.themoviedb.org/3/search/tv?api_key=$apiKey&query=$encoded"
            } else {
                "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&query=$encoded"
            }

            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withTmdbPacing emptyList()
                val body = response.body?.string() ?: return@withTmdbPacing emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val results = root["results"]?.jsonArray ?: return@withTmdbPacing emptyList()

                results.mapNotNull { node ->
                    val obj = node.jsonObject
                    val mediaTypeStr = obj["media_type"]?.jsonPrimitive?.content
                    val type = when {
                        preferType != null -> preferType
                        mediaTypeStr == "tv" -> MediaType.SHOW
                        mediaTypeStr == "movie" -> MediaType.MOVIE
                        else -> MediaType.SHOW
                    }

                    val title = obj["name"]?.jsonPrimitive?.content
                        ?: obj["title"]?.jsonPrimitive?.content
                        ?: return@mapNotNull null

                    val nativeTitle = obj["original_name"]?.jsonPrimitive?.content
                        ?: obj["original_title"]?.jsonPrimitive?.content

                    val posterPath = obj["poster_path"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.content
                    val cover = "https://image.tmdb.org/t/p/original$posterPath"
                    val banner = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }

                    val synopsis = obj["overview"]?.jsonPrimitive?.content?.take(400)
                    val score = obj["vote_average"]?.jsonPrimitive?.content?.toFloatOrNull()
                    val date = obj["first_air_date"]?.jsonPrimitive?.content
                        ?: obj["release_date"]?.jsonPrimitive?.content
                    val year = date?.split("-")?.firstOrNull()
                    val tmdbId = obj["id"]?.jsonPrimitive?.content?.toLongOrNull()

                    SearchResultItem(
                        title = title,
                        nativeTitle = nativeTitle,
                        synopsis = synopsis,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                        bannerUrl = banner?.let { LosslessCoverMapper.toLosslessUrl(it) },
                        mediaType = type,
                        releaseYear = year,
                        communityScore = score,
                        sourceProvider = "TMDb",
                        tmdbId = tmdbId
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchTvMaze(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://api.tvmaze.com/search/shows?q=$encoded")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val list = json.parseToJsonElement(body).jsonArray

                list.mapNotNull { node ->
                    val show = node.jsonObject["show"]?.jsonObject ?: return@mapNotNull null
                    val title = show["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val image = show["image"]?.jsonObject
                    val cover = image?.get("original")?.jsonPrimitive?.content
                        ?: image?.get("medium")?.jsonPrimitive?.content
                        ?: return@mapNotNull null

                    val summary = show["summary"]?.jsonPrimitive?.content
                        ?.replace(Regex("<[^>]*>"), "")
                        ?.take(400)
                    val score = show["rating"]?.jsonObject?.get("average")?.jsonPrimitive?.content?.toFloatOrNull()
                    val premiered = show["premiered"]?.jsonPrimitive?.content
                    val year = premiered?.split("-")?.firstOrNull()

                    SearchResultItem(
                        title = title,
                        synopsis = summary,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                        mediaType = MediaType.SHOW,
                        releaseYear = year,
                        communityScore = score,
                        sourceProvider = "TVMaze"
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchSteam(query: String): List<SearchResultItem> = NetworkPacer.withSteamPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://store.steampowered.com/api/storesearch/?term=$encoded&l=english&cc=US")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withSteamPacing emptyList()
                val body = response.body?.string() ?: return@withSteamPacing emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val items = root["items"]?.jsonArray ?: return@withSteamPacing emptyList()

                items.mapNotNull { node ->
                    val obj = node.jsonObject
                    val appId = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null

                    // High-res Steam 600x900 vertical capsule artwork
                    val cover = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/library_600x900_2x.jpg"
                    val banner = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg"

                    SearchResultItem(
                        title = name,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                        bannerUrl = LosslessCoverMapper.toLosslessUrl(banner),
                        mediaType = MediaType.GAME,
                        sourceProvider = "Steam",
                        igdbId = appId
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchVndb(query: String): List<SearchResultItem> = NetworkPacer.withVndbPacing {
        try {
            val payload = buildJsonObject {
                put("filters", json.parseToJsonElement("[\"search\", \"=\", \"$query\"]"))
                put("fields", "id, title, alttitle, rating, description, image.url")
            }

            val request = Request.Builder()
                .url("https://api.vndb.org/kana/vn")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withVndbPacing emptyList()
                val body = response.body?.string() ?: return@withVndbPacing emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val results = root["results"]?.jsonArray ?: return@withVndbPacing emptyList()

                results.mapNotNull { node ->
                    val obj = node.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val altTitle = obj["alttitle"]?.jsonPrimitive?.content
                    val imageObj = obj["image"]?.jsonObject
                    val cover = imageObj?.get("url")?.jsonPrimitive?.content ?: return@mapNotNull null

                    val rating = obj["rating"]?.jsonPrimitive?.content?.toFloatOrNull()?.let { it / 10f }
                    val desc = obj["description"]?.jsonPrimitive?.content
                        ?.replace(Regex("\\[[^\\]]*\\]"), "")
                        ?.take(400)

                    SearchResultItem(
                        title = title,
                        nativeTitle = altTitle,
                        synopsis = desc,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                        mediaType = MediaType.VISUAL_NOVEL,
                        communityScore = rating,
                        sourceProvider = "VNDB",
                        vndbId = id
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
