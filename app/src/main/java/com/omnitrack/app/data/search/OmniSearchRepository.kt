package com.omnitrack.app.data.search

import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    companion object {
        private val providerSearchCache = java.util.concurrent.ConcurrentHashMap<String, List<SearchResultItem>>()
        private val wikidataAliasCache = java.util.concurrent.ConcurrentHashMap<String, List<Pair<String, String>>>()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val providerPriority = mapOf(
        "AniList" to 1,
        "TVDB" to 2,
        "TMDb" to 3,
        "MAL" to 4,
        "Kitsu" to 5,
        "RanobeDB" to 6,
        "MangaDex" to 7,
        "MangaUpdates" to 8,
        "Steam" to 9,
        "VNDB" to 10,
        "Bangumi" to 11,
        "Syosetu" to 12,
        "OpenLibrary" to 13,
        "WLNUpdates" to 14
    )

    private fun prioritizeAndDedupe(
        rawResults: List<SearchResultItem>,
        query: String = ""
    ): List<SearchResultItem> {
        val q = query.trim().lowercase()
        val prioritized = rawResults.withIndex().sortedWith(compareBy(
            { (_, item) ->
                if (q.isNotEmpty() && (
                        item.title.trim().equals(q, ignoreCase = true) ||
                        item.nativeTitle?.trim()?.equals(q, ignoreCase = true) == true
                    )
                ) 0 else 1
            },
            { (_, item) -> providerPriority[item.sourceProvider] ?: 50 },
            { (_, item) ->
                if (q.isNotEmpty() && (
                        item.title.trim().lowercase().startsWith(q) ||
                        item.nativeTitle?.trim()?.lowercase()?.startsWith(q) == true
                    )
                ) 0 else 1
            },
            { (index, _) -> index }
        )).map { it.value }
        val indexByKey = mutableMapOf<String, Int>()
        val deduped = mutableListOf<SearchResultItem>()
        for (item in prioritized) {
            val subtypeKey = if (item.mediaType == MediaType.NOVEL) ":${item.platform ?: ""}" else ""
            val key = "${item.mediaType.name}$subtypeKey:${item.title.trim().lowercase()}"
            val existingIdx = indexByKey[key]
            if (existingIdx == null) {
                indexByKey[key] = deduped.size
                deduped.add(item)
            } else {
                val existing = deduped[existingIdx]
                deduped[existingIdx] = existing.copy(
                    tmdbId = existing.tmdbId ?: item.tmdbId,
                    anilistId = existing.anilistId ?: item.anilistId,
                    malId = existing.malId ?: item.malId,
                    bangumiId = existing.bangumiId ?: item.bangumiId,
                    mangadexId = existing.mangadexId ?: item.mangadexId,
                    originCountry = existing.originCountry ?: item.originCountry,
                    platform = existing.platform ?: item.platform,
                    bannerUrl = existing.bannerUrl ?: item.bannerUrl,
                    coverUrl = existing.coverUrl.ifBlank { item.coverUrl },
                    synopsis = existing.synopsis ?: item.synopsis,
                    totalProgress = existing.totalProgress ?: item.totalProgress,
                    releaseYear = existing.releaseYear ?: item.releaseYear,
                    communityScore = existing.communityScore ?: item.communityScore
                )
            }
        }
        return deduped
    }

    suspend fun searchAll(
        query: String,
        filter: MediaType = MediaType.ALL,
        tmdbApiKey: String? = null,
        onPartialResults: ((List<SearchResultItem>) -> Unit)? = null
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        coroutineScope {
            val deferreds = mutableListOf<kotlinx.coroutines.Deferred<List<SearchResultItem>>>()
            val accumulated = java.util.Collections.synchronizedList(mutableListOf<SearchResultItem>())
            val emitMutex = Mutex()

            fun launchProvider(block: suspend () -> List<SearchResultItem>) = async {
                currentCoroutineContext().ensureActive()
                val res = block()
                currentCoroutineContext().ensureActive()
                val filteredRes = if (filter == MediaType.ALL) res else res.filter { it.mediaType == filter }
                if (filteredRes.isNotEmpty() && onPartialResults != null) {
                    val snapshot = emitMutex.withLock {
                        currentCoroutineContext().ensureActive()
                        accumulated.addAll(filteredRes)
                        prioritizeAndDedupe(accumulated.toList(), trimmed)
                    }
                    currentCoroutineContext().ensureActive()
                    onPartialResults(snapshot)
                }
                filteredRes
            }

            when (filter) {
                MediaType.ALL -> {
                    deferreds.add(launchProvider { searchAniList(trimmed, isAnime = true) })
                    deferreds.add(launchProvider { searchAniList(trimmed, isAnime = false) })
                    deferreds.add(launchProvider { searchBangumiWithAliases(trimmed, type = 1, defaultType = MediaType.NOVEL) })
                    deferreds.add(launchProvider { searchRanobeDb(trimmed) })
                    deferreds.add(launchProvider { searchKitsuNovels(trimmed) })
                    deferreds.add(launchProvider { searchMangaUpdatesNovels(trimmed) })
                    deferreds.add(launchProvider { searchOpenLibraryNovels(trimmed) })
                    deferreds.add(launchProvider { searchShowsAndMovies(trimmed, tmdbApiKey) })
                    deferreds.add(launchProvider { searchSteam(trimmed) })
                    deferreds.add(launchProvider { searchVndb(trimmed) })
                    deferreds.add(launchProvider { searchBooks(trimmed) })
                }
                MediaType.BOOK -> {
                    deferreds.add(launchProvider { searchBooks(trimmed) })
                }
                MediaType.ANIME -> {
                    deferreds.add(launchProvider { searchAniList(trimmed, isAnime = true) })
                    deferreds.add(launchProvider { searchTvdbSkyHookAnime(trimmed) })
                    deferreds.add(launchProvider { searchTmdbAnime(trimmed, tmdbApiKey) })
                    deferreds.add(launchProvider { searchBangumi(trimmed, type = 2, defaultType = MediaType.ANIME) })
                }
                MediaType.MANGA -> {
                    deferreds.add(launchProvider { searchAniList(trimmed, isAnime = false, formatNovel = false) })
                    deferreds.add(launchProvider { searchJikanMangaMirror(trimmed) })
                    deferreds.add(launchProvider { searchKitsuManga(trimmed) })
                    deferreds.add(launchProvider { searchMangaDex(trimmed) })
                    deferreds.add(launchProvider { searchBangumi(trimmed, type = 1, defaultType = MediaType.MANGA) })
                }
                MediaType.NOVEL -> {
                    // Dual-Engine Novel Discovery:
                    // Light Novels: RanobeDB, AniList NOVEL, Jikan LN
                    // Web Novels: Kitsu subtype=novel, MangaUpdates type=Novel, Wikidata->Bangumi, Syosetu, OpenLibrary
                    deferreds.add(launchProvider { searchRanobeDb(trimmed) })
                    deferreds.add(launchProvider { searchAniList(trimmed, isAnime = false, formatNovel = true) })
                    deferreds.add(launchProvider { searchJikanLightNovels(trimmed) })
                    deferreds.add(launchProvider { searchKitsuNovels(trimmed) })
                    deferreds.add(launchProvider { searchMangaUpdatesNovels(trimmed) })
                    deferreds.add(launchProvider { searchBangumiWithAliases(trimmed, type = 1, defaultType = MediaType.NOVEL) })
                    deferreds.add(launchProvider { searchSyosetu(trimmed) })
                    deferreds.add(launchProvider { searchOpenLibraryNovels(trimmed) })
                }
                MediaType.SHOW -> {
                    deferreds.add(launchProvider { searchShowsAndMovies(trimmed, tmdbApiKey, preferType = MediaType.SHOW) })
                }
                MediaType.MOVIE -> {
                    deferreds.add(launchProvider { searchShowsAndMovies(trimmed, tmdbApiKey, preferType = MediaType.MOVIE) })
                }
                MediaType.GAME -> {
                    deferreds.add(launchProvider { searchSteam(trimmed) })
                    deferreds.add(launchProvider { searchBangumi(trimmed, type = 4, defaultType = MediaType.GAME) })
                }
                MediaType.VISUAL_NOVEL -> {
                    deferreds.add(launchProvider { searchVndb(trimmed) })
                    deferreds.add(launchProvider { searchBangumi(trimmed, type = 4, defaultType = MediaType.VISUAL_NOVEL) })
                }
            }

            currentCoroutineContext().ensureActive()
            prioritizeAndDedupe(deferreds.awaitAll().flatten(), trimmed)
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
    ): List<SearchResultItem> {
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        val cacheKey = "anilist_${query.lowercase()}_${isAnime}_${formatNovel}_$pref"
        providerSearchCache[cacheKey]?.let { return it }

        return NetworkPacer.coalesce(cacheKey) {
            providerSearchCache[cacheKey]?.let { return@coalesce it }
            NetworkPacer.withAniListSearchPacing {
                try {
                    val typeStr = if (isAnime) "ANIME" else "MANGA"
                    val formatParam = if (formatNovel) ", format: NOVEL" else if (!isAnime) ", format_not: NOVEL" else ""
                    val gql = """
                        query (${'$'}search: String) {
                            Page(page: 1, perPage: 25) {
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
                                    volumes
                                    countryOfOrigin
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
                        .header("User-Agent", "OmniTrack/1.0 (Android; Linux; S24 Ultra)")
                        .header("Accept", "application/json")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    var response = client.newCall(request).execute()
                    if (response.code == 429) {
                        response.close()
                        delay(600)
                        response = client.newCall(request).execute()
                    }
                    if (response.code == 429) {
                        response.close()
                        delay(1000)
                        response = client.newCall(request).execute()
                    }

                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            AppLogger.w("OmniSearchRepository", "AniList search returned HTTP ${resp.code} for query: $query")
                            return@withAniListSearchPacing emptyList()
                        }
                        val body = resp.body?.string() ?: return@withAniListSearchPacing emptyList()
                        val root = json.parseToJsonElement(body).jsonObject
                        val mediaList = root["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
                            ?: return@withAniListSearchPacing emptyList()

                        val parsed = mediaList.mapNotNull { node ->
                            val obj = node.jsonObject
                            val titleObj = obj["title"]?.jsonObject
                            val eng = titleObj?.get("english").cleanString()
                            val rom = titleObj?.get("romaji").cleanString()
                            val nat = titleObj?.get("native").cleanString()
                            val title = when (pref) {
                                com.omnitrack.app.data.preferences.PreferredTitleLanguage.ENGLISH -> eng ?: rom ?: nat
                                com.omnitrack.app.data.preferences.PreferredTitleLanguage.ROMAJI -> rom ?: eng ?: nat
                                com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE -> nat ?: rom ?: eng
                            } ?: return@mapNotNull null

                            val nativeTitle = nat
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

                            val totalProgress = when {
                                isAnime -> obj["episodes"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                                formatNovel -> obj["volumes"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                                    ?: obj["chapters"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                                else -> obj["chapters"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                            }
                            val originCountry = obj["countryOfOrigin"].cleanString()
                            val novelPlatform = if (formatNovel) {
                                if (originCountry == null || originCountry.equals("JP", ignoreCase = true)) {
                                    "LIGHT_NOVEL"
                                } else {
                                    "WEB_NOVEL"
                                }
                            } else null

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
                                totalProgress = totalProgress,
                                originCountry = originCountry,
                                platform = novelPlatform
                            )
                        }
                        if (parsed.isNotEmpty()) {
                            providerSearchCache[cacheKey] = parsed
                        }
                        parsed
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    AppLogger.w("OmniSearchRepository", "Search exception for $query: ${e.message}")
                    emptyList()
                }
            }
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
                    val originalLanguage = attrs["originalLanguage"].cleanString()?.lowercase()
                    val originCountry = when (originalLanguage) {
                        "ko" -> "KR"
                        "zh", "zh-hk", "zh-ro" -> "CN"
                        "ja" -> "JP"
                        else -> null
                    }
                    val linksObj = attrs["links"]?.jsonObject
                    val alId = linksObj?.get("al").cleanString()?.toLongOrNull()
                    val malId = linksObj?.get("mal").cleanString()?.toLongOrNull()
                    val lastChapter = attrs["lastChapter"].cleanString()?.toDoubleOrNull()

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
                        sourceProvider = "MangaDex",
                        anilistId = alId,
                        malId = malId,
                        mangadexId = mangaId,
                        originCountry = originCountry,
                        totalProgress = lastChapter
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Search exception for $query: ${e.message}")
            emptyList()
        }
    }

    private val jikanClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(4000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private suspend fun searchJikanMangaMirror(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://jikanfortheweebs.midnightignite.me/v4/manga?q=$encoded&limit=8")
                .header("Accept", "application/json")
                .header("User-Agent", "OmniTrack/1.0 (Android; Linux)")
                .get()
                .build()

            jikanClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonArray ?: return@withGeneralPacing emptyList()

                data.mapNotNull { entry ->
                    val obj = entry.jsonObject
                    val typeStr = obj["type"].cleanString()?.lowercase()
                    if (typeStr == "light novel" || typeStr == "novel") return@mapNotNull null

                    val malId = obj["mal_id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
                    val title = obj["title_english"].cleanString()
                        ?: obj["title"].cleanString()
                        ?: return@mapNotNull null
                    val nativeTitle = obj["title_japanese"].cleanString()

                    val images = obj["images"]?.jsonObject
                    val coverUrl = images?.get("webp")?.jsonObject?.get("large_image_url").cleanString()
                        ?: images?.get("jpg")?.jsonObject?.get("large_image_url").cleanString()
                        ?: images?.get("jpg")?.jsonObject?.get("image_url").cleanString()
                        ?: return@mapNotNull null

                    val synopsis = cleanSynopsis(obj["synopsis"].cleanString())
                    val score = obj["score"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                    val chapters = obj["chapters"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    val year = obj["published"]?.jsonObject?.get("prop")?.jsonObject?.get("from")?.jsonObject?.get("year").cleanString()
                    val originCountry = when (typeStr) {
                        "manhwa" -> "KR"
                        "manhua" -> "CN"
                        "manga", "one-shot", "doujinshi" -> "JP"
                        "oel" -> "US"
                        else -> null
                    }

                    SearchResultItem(
                        title = title,
                        nativeTitle = nativeTitle,
                        synopsis = synopsis,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(coverUrl),
                        mediaType = MediaType.MANGA,
                        releaseYear = year,
                        communityScore = score,
                        sourceProvider = "MAL",
                        malId = malId,
                        totalProgress = chapters,
                        originCountry = originCountry
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Jikan manga mirror search failed for $query: ${e.message}")
            emptyList()
        }
    }

    private suspend fun searchKitsuManga(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://kitsu.io/api/edge/manga?filter[text]=$encoded&page[limit]=8")
                .header("Accept", "application/vnd.api+json")
                .header("User-Agent", "OmniTrack/1.0 (Android; Linux)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonArray ?: return@withGeneralPacing emptyList()

                data.mapNotNull { entry ->
                    val obj = entry.jsonObject
                    val attrs = obj["attributes"]?.jsonObject ?: return@mapNotNull null
                    val subtype = attrs["subtype"].cleanString()?.lowercase()
                    if (subtype == "novel") return@mapNotNull null

                    val titlesObj = attrs["titles"]?.jsonObject
                    val eng = titlesObj?.get("en").cleanString() ?: titlesObj?.get("en_us").cleanString()
                    val rom = titlesObj?.get("en_jp").cleanString() ?: attrs["canonicalTitle"].cleanString()
                    val nat = titlesObj?.get("ja_jp").cleanString() ?: titlesObj?.get("ko_kr").cleanString()
                    val title = when (pref) {
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.ENGLISH -> eng ?: rom ?: nat
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.ROMAJI -> rom ?: eng ?: nat
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE -> nat ?: rom ?: eng
                    } ?: return@mapNotNull null

                    val posterObj = attrs["posterImage"]?.jsonObject
                    val coverUrl = posterObj?.get("large").cleanString()
                        ?: posterObj?.get("original").cleanString()
                        ?: posterObj?.get("medium").cleanString()
                        ?: return@mapNotNull null
                    val bannerUrl = attrs["coverImage"]?.jsonObject?.get("large").cleanString()
                        ?: attrs["coverImage"]?.jsonObject?.get("original").cleanString()

                    val synopsis = cleanSynopsis(attrs["synopsis"].cleanString() ?: attrs["description"].cleanString())
                    val score = attrs["averageRating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.let { it / 10f }
                    val chapters = attrs["chapterCount"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    val year = attrs["startDate"].cleanString()?.take(4)
                    val originCountry = when (subtype) {
                        "manhwa" -> "KR"
                        "manhua" -> "CN"
                        "manga", "oneshot", "doujin" -> "JP"
                        "oel" -> "US"
                        else -> null
                    }

                    SearchResultItem(
                        title = title,
                        nativeTitle = nat,
                        synopsis = synopsis,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(coverUrl),
                        bannerUrl = bannerUrl?.let { LosslessCoverMapper.toLosslessUrl(it) },
                        mediaType = MediaType.MANGA,
                        releaseYear = year,
                        communityScore = score,
                        sourceProvider = "Kitsu",
                        totalProgress = chapters,
                        originCountry = originCountry
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Kitsu manga search failed for $query: ${e.message}")
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
                    val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
                    val (title, nativeTitle) = when (pref) {
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE -> {
                            nameOrig to (if (!nameCn.isNullOrBlank() && nameCn != nameOrig) nameCn else null)
                        }
                        else -> {
                            val enOrOrig = if (!nameOrig.isNullOrBlank()) nameOrig else nameCn ?: "Title"
                            enOrOrig to (if (!nameCn.isNullOrBlank() && nameCn != enOrOrig) nameCn else null)
                        }
                    }

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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Search exception for $query: ${e.message}")
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
            currentCoroutineContext().ensureActive()
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Search exception for $query: ${e.message}")
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Search exception for $query: ${e.message}")
            emptyList()
        }
    }

    private suspend fun searchTvdbSkyHookAnime(query: String): List<SearchResultItem> {
        val cacheKey = "tvdb_anime:${query.lowercase().trim()}"
        providerSearchCache[cacheKey]?.let { return it }
        return NetworkPacer.coalesce(cacheKey) {
            providerSearchCache[cacheKey]?.let { return@coalesce it }
            NetworkPacer.withGeneralPacing {
                try {
                    val encoded = URLEncoder.encode(query, "UTF-8")
                    val url = "https://skyhook.sonarr.tv/v1/tvdb/search/en/?term=$encoded"
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "OmniTrack/1.0")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withGeneralPacing emptyList()
                        val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                        val items = json.parseToJsonElement(body).jsonArray
                        val parsed = items.mapNotNull { node ->
                            val obj = node.jsonObject
                            val title = obj["title"].cleanString() ?: return@mapNotNull null
                            val aniListIds = obj["aniListIds"]?.jsonArray?.mapNotNull {
                                it.jsonPrimitive.contentOrNull?.toLongOrNull()
                            }.orEmpty()
                            val malIds = obj["malIds"]?.jsonArray?.mapNotNull {
                                it.jsonPrimitive.contentOrNull?.toLongOrNull()
                            }.orEmpty()
                            val genres = obj["genres"]?.jsonArray?.mapNotNull {
                                it.jsonPrimitive.contentOrNull
                            }.orEmpty()
                            val country = obj["originalCountry"].cleanString().orEmpty()
                            val isAnime = aniListIds.isNotEmpty() ||
                                malIds.isNotEmpty() ||
                                genres.any { it.equals("Anime", ignoreCase = true) } ||
                                (country.equals("jpn", ignoreCase = true) && genres.any { it.equals("Animation", ignoreCase = true) })
                            if (!isAnime) return@mapNotNull null

                            val images = obj["images"]?.jsonArray.orEmpty()
                            var posterUrl: String? = null
                            var bannerUrl: String? = null
                            for (imgNode in images) {
                                val imgObj = imgNode.jsonObject
                                val cType = imgObj["coverType"].cleanString()
                                val u = imgObj["url"].cleanString() ?: continue
                                if (cType.equals("Poster", ignoreCase = true) && posterUrl == null) {
                                    posterUrl = u
                                } else if (cType.equals("Fanart", ignoreCase = true) && bannerUrl == null) {
                                    bannerUrl = u
                                } else if (cType.equals("Banner", ignoreCase = true) && bannerUrl == null) {
                                    bannerUrl = u
                                }
                            }
                            if (posterUrl.isNullOrBlank()) return@mapNotNull null

                            val tmdbId = obj["tmdbId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                            val synopsis = cleanSynopsis(obj["overview"].cleanString())
                            val releaseYear = obj["firstAired"].cleanString()?.take(4)
                            val score = obj["rating"]?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull?.toFloatOrNull()

                            SearchResultItem(
                                title = title,
                                synopsis = synopsis,
                                coverUrl = LosslessCoverMapper.toLosslessUrl(posterUrl),
                                bannerUrl = bannerUrl?.let { LosslessCoverMapper.toLosslessUrl(it) },
                                mediaType = MediaType.ANIME,
                                releaseYear = releaseYear,
                                communityScore = score,
                                sourceProvider = "TVDB",
                                anilistId = aniListIds.firstOrNull(),
                                malId = malIds.firstOrNull(),
                                tmdbId = tmdbId
                            )
                        }
                        if (parsed.isNotEmpty()) {
                            providerSearchCache[cacheKey] = parsed
                        }
                        parsed
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    AppLogger.w("OmniSearchRepository", "TVDB SkyHook anime search failed for $query: ${e.message}")
                    emptyList()
                }
            }
        }
    }

    private suspend fun searchTmdbAnime(
        query: String,
        customTmdbKey: String? = null
    ): List<SearchResultItem> {
        val cacheKey = "tmdb_anime:${query.lowercase().trim()}"
        providerSearchCache[cacheKey]?.let { return it }
        val apiKey = customTmdbKey?.takeIf { it.isNotBlank() } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
        return NetworkPacer.coalesce(cacheKey) {
            providerSearchCache[cacheKey]?.let { return@coalesce it }
            coroutineScope {
                val encoded = URLEncoder.encode(query, "UTF-8")
                suspend fun queryEndpoint(endpoint: String): List<SearchResultItem> = NetworkPacer.withTmdbPacing {
                    try {
                        val url = "https://api.themoviedb.org/3/search/$endpoint?api_key=$apiKey&query=$encoded"
                        val request = Request.Builder().url(url).get().build()
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) return@withTmdbPacing emptyList()
                            val body = response.body?.string() ?: return@withTmdbPacing emptyList()
                            val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray
                                ?: return@withTmdbPacing emptyList()
                            results.mapNotNull { node ->
                                val obj = node.jsonObject
                                val genres = obj["genre_ids"]?.jsonArray?.mapNotNull {
                                    it.jsonPrimitive.contentOrNull?.toIntOrNull()
                                }.orEmpty()
                                val origLang = obj["original_language"].cleanString()
                                val origCountries = obj["origin_country"]?.jsonArray?.mapNotNull {
                                    it.jsonPrimitive.contentOrNull
                                }.orEmpty()
                                val isAnime = 16 in genres && (origLang == "ja" || origCountries.any { it.equals("JP", ignoreCase = true) })
                                if (!isAnime) return@mapNotNull null

                                val title = obj["name"].cleanString() ?: obj["title"].cleanString() ?: return@mapNotNull null
                                val nativeTitle = obj["original_name"].cleanString() ?: obj["original_title"].cleanString()
                                val posterPath = obj["poster_path"].cleanString() ?: return@mapNotNull null
                                val backdropPath = obj["backdrop_path"].cleanString()
                                val date = obj["first_air_date"].cleanString() ?: obj["release_date"].cleanString()
                                val tmdbId = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

                                SearchResultItem(
                                    title = title,
                                    nativeTitle = nativeTitle,
                                    synopsis = cleanSynopsis(obj["overview"].cleanString()),
                                    coverUrl = LosslessCoverMapper.toLosslessUrl("https://image.tmdb.org/t/p/original$posterPath"),
                                    bannerUrl = backdropPath?.let { LosslessCoverMapper.toLosslessUrl("https://image.tmdb.org/t/p/original$it") },
                                    mediaType = MediaType.ANIME,
                                    releaseYear = date?.split("-")?.firstOrNull()?.cleanString(),
                                    communityScore = obj["vote_average"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull(),
                                    sourceProvider = "TMDb",
                                    tmdbId = tmdbId
                                )
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        emptyList()
                    }
                }
                val combined = listOf(async { queryEndpoint("tv") }, async { queryEndpoint("movie") }).awaitAll().flatten()
                if (combined.isNotEmpty()) {
                    providerSearchCache[cacheKey] = combined
                }
                combined
            }
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Search exception for $query: ${e.message}")
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Search exception for $query: ${e.message}")
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Search exception for $query: ${e.message}")
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

    private suspend fun resolveWikidataAliases(query: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        wikidataAliasCache[key]?.let { return@withContext it }
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val searchReq = Request.Builder()
                .url("https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$encoded&language=en&limit=1&format=json")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()
            val qid = jikanClient.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                json.parseToJsonElement(body).jsonObject["search"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("id").cleanString()
            } ?: return@withContext emptyList()

            val entReq = Request.Builder()
                .url("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$qid&props=labels&languages=zh|zh-hans|ko|ja&format=json")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()
            val pairs = jikanClient.newCall(entReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val labels = json.parseToJsonElement(body).jsonObject["entities"]?.jsonObject
                    ?.get(qid)?.jsonObject?.get("labels")?.jsonObject ?: return@withContext emptyList()
                val list = mutableListOf<Pair<String, String>>()
                val zh = labels["zh-hans"]?.jsonObject?.get("value").cleanString()
                    ?: labels["zh"]?.jsonObject?.get("value").cleanString()
                val ko = labels["ko"]?.jsonObject?.get("value").cleanString()
                val ja = labels["ja"]?.jsonObject?.get("value").cleanString()
                if (!zh.isNullOrBlank()) list.add(zh to "CN")
                if (!ko.isNullOrBlank()) list.add(ko to "KR")
                if (!ja.isNullOrBlank()) list.add(ja to "JP")
                list
            }
            wikidataAliasCache[key] = pairs
            pairs
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private suspend fun searchBangumiWithAliases(
        query: String,
        type: Int,
        defaultType: MediaType
    ): List<SearchResultItem> = coroutineScope {
        val deferreds = mutableListOf(async { searchBangumi(query, type, defaultType) })
        val staticAlias = novelAliasMap[query.trim().lowercase()]
        if (staticAlias != null && !staticAlias.equals(query, ignoreCase = true)) {
            deferreds.add(async { searchBangumi(staticAlias, type, defaultType) })
        } else if (defaultType == MediaType.NOVEL && query.any { it in 'A'..'Z' || it in 'a'..'z' }) {
            val wdAliases = resolveWikidataAliases(query)
            wdAliases.take(2).forEach { (nativeTitle, _) ->
                if (!nativeTitle.equals(query, ignoreCase = true)) {
                    deferreds.add(async { searchBangumi(nativeTitle, type, defaultType) })
                }
            }
        }
        deferreds.awaitAll().flatten().map { item ->
            if (item.mediaType == MediaType.NOVEL) {
                val nat = item.nativeTitle ?: item.title
                val hasHangul = Regex("[\\uAC00-\\uD7AF]").containsMatchIn(nat)
                val hasKana = Regex("[\\u3040-\\u30FF]").containsMatchIn(nat)
                val origin = when {
                    hasHangul -> "KR"
                    hasKana -> "JP"
                    else -> "CN"
                }
                val inferredPlatform = if (origin == "CN" || origin == "KR") "WEB_NOVEL" else item.platform
                item.copy(
                    originCountry = item.originCountry ?: origin,
                    platform = inferredPlatform
                )
            } else {
                item
            }
        }
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
                        platform = "WEB_NOVEL",
                        sourceProvider = "OpenLibrary"
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Search exception for $query: ${e.message}")
            emptyList()
        }
    }

    private suspend fun searchJikanLightNovels(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://jikanfortheweebs.midnightignite.me/v4/manga?q=$encoded&type=lightnovel&limit=6")
                .header("Accept", "application/json")
                .header("User-Agent", "OmniTrack/1.0 (Android; Linux)")
                .get()
                .build()
            jikanClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: return@withGeneralPacing emptyList()
                data.mapNotNull { entry ->
                    val obj = entry.jsonObject
                    val malId = obj["mal_id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
                    val title = obj["title_english"].cleanString() ?: obj["title"].cleanString() ?: return@mapNotNull null
                    val nativeTitle = obj["title_japanese"].cleanString()
                    val images = obj["images"]?.jsonObject
                    val coverUrl = images?.get("webp")?.jsonObject?.get("large_image_url").cleanString()
                        ?: images?.get("jpg")?.jsonObject?.get("large_image_url").cleanString()
                        ?: images?.get("jpg")?.jsonObject?.get("image_url").cleanString()
                        ?: return@mapNotNull null
                    val synopsis = cleanSynopsis(obj["synopsis"].cleanString())
                    val score = obj["score"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                    val volumes = obj["volumes"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    val chapters = obj["chapters"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    val year = obj["published"]?.jsonObject?.get("prop")?.jsonObject?.get("from")?.jsonObject?.get("year").cleanString()
                    SearchResultItem(
                        title = title,
                        nativeTitle = nativeTitle,
                        synopsis = synopsis,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(coverUrl),
                        mediaType = MediaType.NOVEL,
                        releaseYear = year,
                        communityScore = score,
                        sourceProvider = "MAL",
                        malId = malId,
                        totalProgress = volumes ?: chapters,
                        originCountry = "JP",
                        platform = "LIGHT_NOVEL"
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private suspend fun searchKitsuNovels(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://kitsu.io/api/edge/manga?filter[text]=$encoded&filter[subtype]=novel&page[limit]=8")
                .header("Accept", "application/vnd.api+json")
                .header("User-Agent", "OmniTrack/1.0 (Android; Linux)")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: return@withGeneralPacing emptyList()
                data.mapNotNull { entry ->
                    val obj = entry.jsonObject
                    val attrs = obj["attributes"]?.jsonObject ?: return@mapNotNull null
                    val titlesObj = attrs["titles"]?.jsonObject
                    val eng = titlesObj?.get("en").cleanString() ?: titlesObj?.get("en_us").cleanString()
                    val rom = titlesObj?.get("en_jp").cleanString() ?: attrs["canonicalTitle"].cleanString()
                    val nat = titlesObj?.get("ja_jp").cleanString() ?: titlesObj?.get("ko_kr").cleanString()
                    val title = when (pref) {
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.ENGLISH -> eng ?: rom ?: nat ?: attrs["canonicalTitle"].cleanString()
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.ROMAJI -> rom ?: eng ?: nat
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE -> nat ?: rom ?: eng
                    } ?: return@mapNotNull null

                    val posterObj = attrs["posterImage"]?.jsonObject
                    val coverUrl = posterObj?.get("original").cleanString()
                        ?: posterObj?.get("large").cleanString()
                        ?: posterObj?.get("medium").cleanString()
                        ?: return@mapNotNull null
                    val bannerUrl = attrs["coverImage"]?.jsonObject?.get("original").cleanString()
                        ?: attrs["coverImage"]?.jsonObject?.get("large").cleanString()

                    val synopsis = cleanSynopsis(attrs["synopsis"].cleanString() ?: attrs["description"].cleanString())
                    val score = attrs["averageRating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.let { it / 10f }
                    val chapters = attrs["chapterCount"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    val volumes = attrs["volumeCount"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    val year = attrs["startDate"].cleanString()?.take(4)
                    val isWebNovel = (chapters ?: 0.0) > 60.0 || (volumes == null || volumes <= 1.0)
                    val novelPlatform = if (isWebNovel) "WEB_NOVEL" else "LIGHT_NOVEL"
                    SearchResultItem(
                        title = title,
                        nativeTitle = nat,
                        synopsis = synopsis,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(coverUrl),
                        bannerUrl = bannerUrl?.let { LosslessCoverMapper.toLosslessUrl(it) },
                        mediaType = MediaType.NOVEL,
                        releaseYear = year,
                        communityScore = score,
                        sourceProvider = "Kitsu",
                        platform = novelPlatform,
                        totalProgress = if (novelPlatform == "LIGHT_NOVEL") volumes ?: chapters else chapters ?: volumes
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private suspend fun searchMangaUpdatesNovels(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val payload = buildJsonObject {
                put("search", query)
                put("perpage", 8)
            }
            val request = Request.Builder()
                .url("https://api.mangaupdates.com/v1/series/search")
                .header("User-Agent", "OmniTrack/1.0")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return@withGeneralPacing emptyList()
                results.mapNotNull { node ->
                    val rec = node.jsonObject["record"]?.jsonObject ?: return@mapNotNull null
                    val recType = rec["type"].cleanString() ?: return@mapNotNull null
                    if (!recType.equals("Novel", ignoreCase = true)) return@mapNotNull null
                    val rawTitle = rec["title"].cleanString() ?: return@mapNotNull null
                    val cleanMuTitle = rawTitle.replace(Regex("""\s*\(Novel\)\s*$""", RegexOption.IGNORE_CASE), "").trim()
                    val coverUrl = rec["image"]?.jsonObject?.get("url")?.jsonObject?.get("original").cleanString()
                        ?: rec["image"]?.jsonObject?.get("url")?.jsonObject?.get("thumb").cleanString()
                        ?: return@mapNotNull null
                    val rating = rec["bayesian_rating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.takeIf { it > 0f }
                    val year = rec["year"].cleanString()
                    val desc = cleanSynopsis(rec["description"].cleanString())
                    SearchResultItem(
                        title = cleanMuTitle,
                        synopsis = desc,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(coverUrl),
                        mediaType = MediaType.NOVEL,
                        releaseYear = year,
                        communityScore = rating,
                        platform = "WEB_NOVEL",
                        sourceProvider = "MangaUpdates"
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private suspend fun searchSyosetu(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val jpQuery = if (Regex("[\\u3040-\\u30FF\\u4E00-\\u9FFF]").containsMatchIn(query)) {
                query
            } else {
                resolveWikidataAliases(query).firstOrNull { it.second == "JP" }?.first ?: return@withGeneralPacing emptyList()
            }
            val encoded = URLEncoder.encode(jpQuery, "UTF-8")
            val request = Request.Builder()
                .url("https://api.syosetu.com/novelapi/api/?out=json&lim=4&word=$encoded")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val arr = json.parseToJsonElement(body).jsonArray
                if (arr.size <= 1) return@withGeneralPacing emptyList()
                arr.drop(1).mapNotNull { node ->
                    val obj = node.jsonObject
                    val title = obj["title"].cleanString() ?: return@mapNotNull null
                    val chapters = obj["general_all_no"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    val writer = obj["writer"].cleanString()
                    val story = cleanSynopsis(obj["story"].cleanString())
                    val year = obj["general_firstup"].cleanString()?.take(4)
                    SearchResultItem(
                        title = if (jpQuery != query) "$query (Web Novel)" else title,
                        nativeTitle = title,
                        synopsis = if (!writer.isNullOrBlank()) "By $writer\n\n${story.orEmpty()}" else story,
                        coverUrl = "",
                        mediaType = MediaType.NOVEL,
                        releaseYear = year,
                        totalProgress = chapters,
                        originCountry = "JP",
                        platform = "WEB_NOVEL",
                        sourceProvider = "Syosetu"
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private suspend fun searchRanobeDb(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://ranobedb.org/api/v0/series?q=$encoded&limit=8"
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
                    val title = obj["title"]?.cleanString()
                        ?: obj["title_orig"]?.cleanString()
                        ?: return@mapNotNull null
                    val nativeTitle = obj["title_orig"]?.cleanString()
                    val book = obj["book"]?.jsonObject
                    val filename = book?.get("image")?.jsonObject?.get("filename")?.cleanString()
                    val cover = if (filename != null) "https://images.ranobedb.org/$filename" else return@mapNotNull null
                    val volCount = obj["volumes"]?.jsonObject?.get("count")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                        ?: obj["c_num_books"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    val origin = obj["olang"]?.cleanString()?.uppercase() ?: "JP"
                    val startDateInt = obj["c_start_date"]?.jsonPrimitive?.contentOrNull
                    val year = startDateInt?.take(4)?.takeIf { it.length == 4 }

                    SearchResultItem(
                        title = title,
                        nativeTitle = if (nativeTitle != title) nativeTitle else null,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                        mediaType = MediaType.NOVEL,
                        releaseYear = year,
                        totalProgress = volCount,
                        originCountry = origin,
                        platform = "LIGHT_NOVEL",
                        sourceProvider = "RanobeDB"
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Search exception for $query: ${e.message}")
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Search exception for $query: ${e.message}")
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Search exception for $query: ${e.message}")
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
