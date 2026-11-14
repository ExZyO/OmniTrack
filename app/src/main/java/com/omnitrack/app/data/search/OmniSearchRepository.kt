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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
    private val client: OkHttpClient = com.omnitrack.app.data.network.NetworkClient.getClient()
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
                    deferreds.add(async { searchBangumiWithAliases(trimmed, type = 1, defaultType = MediaType.NOVEL) })
                    deferreds.add(async { searchRanobeDb(trimmed) })
                    deferreds.add(async { searchOpenLibraryNovels(trimmed) })
                    deferreds.add(async { searchWlnUpdates(trimmed) })
                    deferreds.add(async { searchShowsAndMovies(trimmed, tmdbApiKey) })
                    deferreds.add(async { searchSteam(trimmed) })
                    deferreds.add(async { searchVndb(trimmed) })
                    deferreds.add(async { searchBooks(trimmed) })
                }
                MediaType.BOOK -> {
                    deferreds.add(async { searchBooks(trimmed) })
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
                    // Multi-source novel discovery: Bangumi with native aliases, RanobeDB, AniList LN, OpenLibrary & WLNUpdates
                    deferreds.add(async { searchBangumiWithAliases(trimmed, type = 1, defaultType = MediaType.NOVEL) })
                    deferreds.add(async { searchRanobeDb(trimmed) })
                    deferreds.add(async { searchAniList(trimmed, isAnime = false, formatNovel = true) })
                    deferreds.add(async { searchOpenLibraryNovels(trimmed) })
                    deferreds.add(async { searchWlnUpdates(trimmed) })
                    deferreds.add(async { searchNovelUpdates(trimmed) })
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
                    val title = titleObj?.get("english").cleanString()
                        ?: titleObj?.get("romaji").cleanString()
                        ?: titleObj?.get("native").cleanString()
                        ?: return@mapNotNull null

                    val nativeTitle = titleObj?.get("native").cleanString()
                    val cover = obj["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                        ?: obj["coverImage"]?.jsonObject?.get("large").cleanString()
                        ?: return@mapNotNull null
                    val banner = obj["bannerImage"].cleanString()
                    val synopsis = cleanSynopsis(obj["description"].cleanString())
                    val score = obj["averageScore"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.let { it / 10f }
                    val year = obj["startDate"]?.jsonObject?.get("year").cleanString()
                    val anilistId = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    val malId = obj["idMal"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

                    val targetType = when {
                        formatNovel -> MediaType.NOVEL
                        isAnime -> MediaType.ANIME
                        else -> MediaType.MANGA
                    }

                    val totalProgress = if (isAnime) {
                        obj["episodes"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    } else {
                        obj["chapters"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
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
                    val mangaId = obj["id"].cleanString() ?: return@mapNotNull null
                    val attrs = obj["attributes"]?.jsonObject ?: return@mapNotNull null

                    val titleMap = attrs["title"]?.jsonObject
                    val title = titleMap?.get("en").cleanString()
                        ?: titleMap?.values?.firstNotNullOfOrNull { it.cleanString() }
                        ?: return@mapNotNull null

                    val altTitles = attrs["altTitles"]?.jsonArray
                    val nativeTitle = altTitles?.firstNotNullOfOrNull { it.jsonObject.values.firstNotNullOfOrNull { v -> v.cleanString() } }

                    val synopsis = cleanSynopsis(attrs["description"]?.jsonObject?.get("en").cleanString())
                    val year = attrs["year"].cleanString()

                    var coverFile: String? = null
                    val relationships = obj["relationships"]?.jsonArray ?: emptyList()
                    for (rel in relationships) {
                        if (rel.jsonObject["type"].cleanString() == "cover_art") {
                            coverFile = rel.jsonObject["attributes"]?.jsonObject?.get("fileName").cleanString()
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
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
                    val nameCn = obj["name_cn"].cleanString()
                    val nameOrig = obj["name"].cleanString() ?: return@mapNotNull null
                    val title = if (!nameCn.isNullOrBlank()) nameCn else nameOrig
                    val nativeTitle = if (nameCn != null && nameCn != nameOrig) nameOrig else null

                    val images = obj["images"]?.jsonObject
                    val cover = images?.get("large").cleanString()
                        ?: images?.get("common").cleanString()
                        ?: return@mapNotNull null

                    val summary = cleanSynopsis(obj["summary"].cleanString())
                    val score = obj["rating"]?.jsonObject?.get("score")?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                    val airDate = obj["air_date"].cleanString()
                    val year = airDate?.split("-")?.firstOrNull()?.cleanString()

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
        val results = mutableListOf<SearchResultItem>()
        if (!tmdbApiKey.isNullOrBlank()) {
            results.addAll(searchTmdb(query, tmdbApiKey, preferType))
        } else {
            // Keyless Public Fallbacks:
            // TV Shows -> TVMaze (zero-key open API)
            if (preferType == null || preferType == MediaType.SHOW || preferType == MediaType.ALL) {
                results.addAll(searchTvMaze(query))
            }
            // Movies -> Cinemeta (zero-key public IMDb catalog)
            if (preferType == null || preferType == MediaType.MOVIE || preferType == MediaType.ALL) {
                results.addAll(searchCinemetaMovies(query))
            }
        }
        return results
    }

    private suspend fun searchCinemetaMovies(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://v3-cinemeta.strem.io/catalog/movie/top/search=$encoded.json"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val metas = root["metas"]?.jsonArray ?: return@withGeneralPacing emptyList()

                metas.mapNotNull { node ->
                    val obj = node.jsonObject
                    val title = obj["name"].cleanString() ?: return@mapNotNull null
                    val rawPoster = obj["poster"].cleanString() ?: return@mapNotNull null
                    val rawBanner = obj["background"].cleanString()
                    // Upgrade metahub /small/ thumbnails to /medium/ for crystal clear poster resolution
                    val poster = rawPoster.replace("/small/", "/medium/")
                    val banner = rawBanner?.replace("/small/", "/medium/")
                    val year = obj["releaseInfo"].cleanString() ?: obj["year"].cleanString()
                    val score = obj["imdbRating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                    val desc = cleanSynopsis(obj["description"].cleanString())

                    SearchResultItem(
                        title = title,
                        synopsis = desc,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(poster),
                        bannerUrl = banner?.let { LosslessCoverMapper.toLosslessUrl(it) },
                        mediaType = MediaType.MOVIE,
                        releaseYear = year,
                        communityScore = score,
                        sourceProvider = "Cinemeta"
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
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
                    val mediaTypeStr = obj["media_type"].cleanString()
                    val type = when {
                        preferType != null -> preferType
                        mediaTypeStr == "tv" -> MediaType.SHOW
                        mediaTypeStr == "movie" -> MediaType.MOVIE
                        else -> MediaType.SHOW
                    }

                    val title = obj["name"].cleanString()
                        ?: obj["title"].cleanString()
                        ?: return@mapNotNull null

                    val nativeTitle = obj["original_name"].cleanString()
                        ?: obj["original_title"].cleanString()

                    val posterPath = obj["poster_path"].cleanString() ?: return@mapNotNull null
                    val backdropPath = obj["backdrop_path"].cleanString()
                    val cover = "https://image.tmdb.org/t/p/original$posterPath"
                    val banner = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }

                    val synopsis = cleanSynopsis(obj["overview"].cleanString())
                    val score = obj["vote_average"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                    val date = obj["first_air_date"].cleanString()
                        ?: obj["release_date"].cleanString()
                    val year = date?.split("-")?.firstOrNull()?.cleanString()
                    val tmdbId = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

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
                    val title = show["name"].cleanString() ?: return@mapNotNull null
                    val image = show["image"]?.jsonObject
                    val cover = image?.get("original").cleanString()
                        ?: image?.get("medium").cleanString()
                        ?: return@mapNotNull null

                    val summary = cleanSynopsis(show["summary"].cleanString())
                    val score = show["rating"]?.jsonObject?.get("average")?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                    val premiered = show["premiered"].cleanString()
                    val year = premiered?.split("-")?.firstOrNull()?.cleanString()

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
                    val appId = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
                    val name = obj["name"].cleanString() ?: return@mapNotNull null

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
                put("fields", "id, title, alttitle, rating, description, image.url, screenshots.url")
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
                    val id = obj["id"].cleanString() ?: return@mapNotNull null
                    val title = obj["title"].cleanString() ?: return@mapNotNull null
                    val altTitle = obj["alttitle"].cleanString()
                    val imageObj = obj["image"]?.jsonObject
                    val cover = imageObj?.get("url").cleanString() ?: return@mapNotNull null
                    val screenshots = obj["screenshots"]?.jsonArray
                    val firstScreenshot = screenshots?.firstOrNull()?.jsonObject?.get("url").cleanString()

                    val rating = obj["rating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.let { it / 10f }
                    val desc = cleanSynopsis(obj["description"].cleanString()?.replace(Regex("\\[[^\\]]*\\]"), ""))

                    SearchResultItem(
                        title = title,
                        nativeTitle = altTitle,
                        synopsis = desc,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                        bannerUrl = firstScreenshot?.let { LosslessCoverMapper.toLosslessUrl(it) },
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

    private val novelAliasMap = mapOf(
        "reverend insanity" to "蛊真人",
        "lord of the mysteries" to "诡秘之主",
        "omniscient reader" to "全知读者视角",
        "omniscient reader's viewpoint" to "全知读者视角",
        "coiling dragon" to "盘龙",
        "against the gods" to "逆天邪神",
        "martial world" to "武极天下",
        "renegade immortal" to "仙逆",
        "a will eternal" to "一念永恒",
        "battle through the heavens" to "斗破苍穹",
        "perfect world" to "完美世界",
        "shrouding the heavens" to "遮天",
        "swallowed star" to "吞噬星空",
        "i shall seal the heavens" to "我欲封天",
        "tales of demons and gods" to "妖神记",
        "solo leveling" to "나 혼자만 레벨업",
        "the beginning after the end" to "The Beginning After the End"
    )

    private suspend fun searchBangumiWithAliases(
        query: String,
        type: Int,
        defaultType: MediaType
    ): List<SearchResultItem> = coroutineScope {
        val deferreds = mutableListOf(async { searchBangumi(query, type, defaultType) })
        val alias = novelAliasMap[query.trim().lowercase()]
        if (alias != null && !alias.equals(query, ignoreCase = true)) {
            deferreds.add(async { searchBangumi(alias, type, defaultType) })
        }
        deferreds.awaitAll().flatten()
    }

    private suspend fun searchOpenLibraryNovels(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://openlibrary.org/search.json?q=$encoded&limit=6"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val docs = root["docs"]?.jsonArray ?: return@withGeneralPacing emptyList()

                docs.mapNotNull { node ->
                    val obj = node.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val coverId = obj["cover_i"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val coverUrl = "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
                    val year = obj["first_publish_year"]?.jsonPrimitive?.content
                    val author = obj["author_name"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content

                    SearchResultItem(
                        title = title,
                        synopsis = author?.let { "By $it" },
                        coverUrl = LosslessCoverMapper.toLosslessUrl(coverUrl),
                        mediaType = MediaType.NOVEL,
                        releaseYear = year,
                        sourceProvider = "OpenLibrary"
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchWlnUpdates(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val payload = buildJsonObject {
                put("mode", "search-title")
                put("title", query)
            }
            val request = Request.Builder()
                .url("https://www.wlnupdates.com/api")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val error = root["error"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
                if (error) return@withGeneralPacing emptyList()

                val data = root["data"]?.jsonArray ?: return@withGeneralPacing emptyList()
                data.mapNotNull { node ->
                    val obj = node.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val covers = obj["covers"]?.jsonArray
                    val coverUrl = covers?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                        ?: covers?.firstOrNull()?.jsonPrimitive?.content
                        ?: return@mapNotNull null
                    val chp = obj["tot_chapters"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    val rating = obj["rating"]?.jsonPrimitive?.content?.toFloatOrNull()

                    SearchResultItem(
                        title = title,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(coverUrl),
                        mediaType = MediaType.NOVEL,
                        communityScore = rating,
                        totalProgress = chp,
                        sourceProvider = "WLNUpdates"
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchNovelUpdates(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.novelupdates.com/?s=$encoded&post_type=seriesposts"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Referer", "https://www.novelupdates.com/")
                .build()

            client.newCall(request).execute().use { response ->
                // Cloudflare fast-skip: if 403, 503, or challenge detected, skip instantly without blocking
                if (response.code in listOf(403, 503)) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                if (body.contains("Just a moment...") || body.contains("cf-chl-opt")) {
                    return@withGeneralPacing emptyList() // Fast-skip Cloudflare
                }
                val results = mutableListOf<SearchResultItem>()
                val matches = Regex("""class="search_title_nu"><a href="[^"]*">([^<]+)</a>.*?(?:src="([^"]+)").*?(?:class="search_ratings_nu">\((\d+(?:\.\d+)?)\))?""", RegexOption.DOT_MATCHES_ALL).findAll(body)
                for (match in matches.take(6)) {
                    val title = match.groupValues.getOrNull(1)?.trim() ?: continue
                    val cover = match.groupValues.getOrNull(2) ?: continue
                    val rating = match.groupValues.getOrNull(3)?.toFloatOrNull()
                    results.add(
                        SearchResultItem(
                            title = title,
                            coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                            mediaType = MediaType.NOVEL,
                            communityScore = rating,
                            sourceProvider = "NovelUpdates"
                        )
                    )
                }
                results
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchRanobeDb(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://ranobedb.org/api/v0/series?q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val series = root["series"]?.jsonArray ?: return@withGeneralPacing emptyList()

                series.mapNotNull { node ->
                    val obj = node.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content
                        ?: obj["title_orig"]?.jsonPrimitive?.content
                        ?: return@mapNotNull null
                    val nativeTitle = obj["title_orig"]?.jsonPrimitive?.content
                    val book = obj["book"]?.jsonObject
                    val filename = book?.get("image")?.jsonObject?.get("filename")?.jsonPrimitive?.content
                    val cover = if (filename != null) "https://images.ranobedb.org/$filename" else null
                    val volCount = obj["volumes"]?.jsonObject?.get("count")?.jsonPrimitive?.content?.toDoubleOrNull()
                        ?: obj["c_num_books"]?.jsonPrimitive?.content?.toDoubleOrNull()

                    SearchResultItem(
                        title = title,
                        nativeTitle = if (nativeTitle != title) nativeTitle else null,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(cover ?: "https://images.ranobedb.org/default.jpg"),
                        mediaType = MediaType.NOVEL,
                        totalProgress = volCount,
                        sourceProvider = "RanobeDB"
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchBooks(query: String): List<SearchResultItem> = coroutineScope {
        val deferreds = listOf(
            async { searchGoogleBooks(query) },
            async { searchOpenLibraryBooks(query) }
        )
        deferreds.awaitAll().flatten()
    }

    private suspend fun searchGoogleBooks(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=10"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val items = root["items"]?.jsonArray ?: return@withGeneralPacing emptyList()

                items.mapNotNull { item ->
                    val volumeInfo = item.jsonObject["volumeInfo"]?.jsonObject ?: return@mapNotNull null
                    val title = volumeInfo["title"].cleanString() ?: return@mapNotNull null
                    val authors = volumeInfo["authors"]?.jsonArray?.mapNotNull { it.cleanString() }?.joinToString(", ")
                    val desc = cleanSynopsis(volumeInfo["description"].cleanString())
                    val date = volumeInfo["publishedDate"].cleanString()
                    val year = date?.split("-")?.firstOrNull()?.cleanString()
                    val pages = volumeInfo["pageCount"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    val score = volumeInfo["averageRating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.let { it * 2f }

                    val imageLinks = volumeInfo["imageLinks"]?.jsonObject
                    val thumb = imageLinks?.get("extraLarge").cleanString()
                        ?: imageLinks?.get("large").cleanString()
                        ?: imageLinks?.get("medium").cleanString()
                        ?: imageLinks?.get("thumbnail").cleanString()
                        ?: return@mapNotNull null

                    val highRes = thumb.replace("http://", "https://").replace(Regex("&zoom=[1-5]"), "&zoom=0")

                    SearchResultItem(
                        title = title,
                        synopsis = if (!authors.isNullOrBlank()) "By $authors\n\n${desc.orEmpty()}" else desc,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(highRes),
                        mediaType = MediaType.BOOK,
                        releaseYear = year,
                        communityScore = score,
                        totalProgress = pages,
                        sourceProvider = "Google Books"
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchOpenLibraryBooks(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://openlibrary.org/search.json?q=$encoded&limit=8"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val docs = root["docs"]?.jsonArray ?: return@withGeneralPacing emptyList()

                docs.mapNotNull { node ->
                    val obj = node.jsonObject
                    val title = obj["title"].cleanString() ?: return@mapNotNull null
                    val coverId = obj["cover_i"].cleanString() ?: return@mapNotNull null
                    val coverUrl = "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
                    val year = obj["first_publish_year"].cleanString()
                    val author = obj["author_name"]?.jsonArray?.firstOrNull().cleanString()
                    val pages = obj["number_of_pages_median"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

                    SearchResultItem(
                        title = title,
                        synopsis = author?.let { "By $it" },
                        coverUrl = LosslessCoverMapper.toLosslessUrl(coverUrl),
                        mediaType = MediaType.BOOK,
                        releaseYear = year,
                        totalProgress = pages,
                        sourceProvider = "Open Library"
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun cleanSynopsis(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]*>"""), "")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .replace(Regex("""\s*[\(\[]\s*$"""), "")
            .trim()
            .ifBlank { null }
    }
}
