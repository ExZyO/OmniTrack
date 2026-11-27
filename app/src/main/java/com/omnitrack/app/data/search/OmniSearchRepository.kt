package com.omnitrack.app.data.search

import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.data.sync.TrackerAccountVerifier
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
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

data class StreamingProviderChip(
    val providerId: Int,
    val name: String,
    val logoUrl: String
)

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
        private val discoverShelfCache = java.util.concurrent.ConcurrentHashMap<String, List<SearchResultItem>>()
        private val wikidataAliasCache = java.util.concurrent.ConcurrentHashMap<String, List<Pair<String, String>>>()
        private val streamingProvidersCache = java.util.concurrent.ConcurrentHashMap<String, List<StreamingProviderChip>>()
        private val cachedCriticsPickByKey = java.util.concurrent.ConcurrentHashMap<String, SearchResultItem>()

        fun cleanSynopsis(raw: String?): String? {
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

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val providerPriority = mapOf(
        "AniList" to 1,
        "TVDB" to 2,
        "TMDb" to 3,
        "MAL" to 4,
        "Kitsu" to 5,
        "Cinemeta" to 5,
        "RanobeDB" to 6,
        "Goodreads" to 7,
        "MangaDex" to 7,
        "MangaUpdates" to 8,
        "IGDB" to 8,
        "Steam" to 9,
        "VNDB" to 10,
        "Bangumi" to 11,
        "Hardcover" to 6,
        "Comic Vine" to 6,
        "Metron" to 7,
        "Open Library" to 8,
        "OpenLibrary" to 8,
        "Apple Books" to 9,
        "Apple Audiobooks" to 9,
        "PoetryDB" to 10,
        "Syosetu" to 12,
        "WLNUpdates" to 14,
        "Google Books" to 15
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
            val subtypeKey = when (item.mediaType) {
                MediaType.NOVEL -> ":${item.platform ?: ""}"
                MediaType.BOOK -> ":${com.omnitrack.app.data.model.normalizeBookFormatGroup(item.bookFormat)}"
                MediaType.COMIC -> ":${com.omnitrack.app.data.model.normalizeComicFormatGroup(item.comicFormat)}"
                else -> ""
            }
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
                    igdbId = existing.igdbId ?: item.igdbId,
                    steamAppId = existing.steamAppId ?: item.steamAppId,
                    openLibraryId = existing.openLibraryId ?: item.openLibraryId,
                    hardcoverId = existing.hardcoverId ?: item.hardcoverId,
                    isbn = existing.isbn ?: item.isbn,
                    bookAuthor = existing.bookAuthor ?: item.bookAuthor,
                    bookFormat = existing.bookFormat ?: item.bookFormat,
                    comicVineId = existing.comicVineId ?: item.comicVineId,
                    metronId = existing.metronId ?: item.metronId,
                    comicPublisher = existing.comicPublisher ?: item.comicPublisher,
                    comicFormat = existing.comicFormat ?: item.comicFormat,
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
                    deferreds.add(launchProvider { searchGoodreads(trimmed, MediaType.NOVEL) })
                    deferreds.add(launchProvider { searchKitsuNovels(trimmed) })
                    deferreds.add(launchProvider { searchMangaUpdatesNovels(trimmed) })
                    deferreds.add(launchProvider { searchOpenLibraryNovels(trimmed) })
                    deferreds.add(launchProvider { searchShowsAndMovies(trimmed, tmdbApiKey) })
                    deferreds.add(launchProvider { searchIgdbGames(trimmed) })
                    deferreds.add(launchProvider { searchSteam(trimmed) })
                    deferreds.add(launchProvider { searchVndb(trimmed) })
                    deferreds.add(launchProvider { searchBooks(trimmed) })
                    deferreds.add(launchProvider { searchComics(trimmed) })
                }
                MediaType.BOOK -> {
                    deferreds.add(launchProvider { searchBooks(trimmed) })
                }
                MediaType.COMIC -> {
                    deferreds.add(launchProvider { searchComics(trimmed) })
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
                    deferreds.add(launchProvider { searchGoodreads(trimmed, MediaType.NOVEL) })
                    deferreds.add(launchProvider { searchSyosetu(trimmed) })
                    deferreds.add(launchProvider { searchOpenLibraryNovels(trimmed) })
                    deferreds.add(launchProvider {
                        val wpCookie = runCatching {
                            com.omnitrack.app.OmniTrackApplication.instance.trackerTokenManager
                                .getFanficCookie(com.omnitrack.app.data.model.FanficSite.WATTPAD)
                        }.getOrNull()
                        com.omnitrack.app.data.fanfic.FanficSiteEngine.searchWattpad(
                            query = trimmed,
                            cookie = wpCookie,
                            targetMediaType = MediaType.FANFICTION
                        )
                    })
                }
                MediaType.SHOW -> {
                    deferreds.add(launchProvider { searchShowsAndMovies(trimmed, tmdbApiKey, preferType = MediaType.SHOW) })
                }
                MediaType.MOVIE -> {
                    deferreds.add(launchProvider { searchShowsAndMovies(trimmed, tmdbApiKey, preferType = MediaType.MOVIE) })
                }
                MediaType.GAME -> {
                    deferreds.add(launchProvider { searchIgdbGames(trimmed) })
                    deferreds.add(launchProvider { searchSteam(trimmed) })
                    deferreds.add(launchProvider { searchBangumi(trimmed, type = 4, defaultType = MediaType.GAME) })
                }
                MediaType.VISUAL_NOVEL -> {
                    deferreds.add(launchProvider { searchVndb(trimmed) })
                    deferreds.add(launchProvider { searchBangumi(trimmed, type = 4, defaultType = MediaType.VISUAL_NOVEL) })
                }
                MediaType.FANFICTION -> {
                    deferreds.add(launchProvider {
                        val ao3Cookie = runCatching {
                            com.omnitrack.app.OmniTrackApplication.instance.trackerTokenManager
                                .getFanficCookie(com.omnitrack.app.data.model.FanficSite.AO3)
                        }.getOrNull()
                        com.omnitrack.app.data.fanfic.FanficSiteEngine.searchAo3(
                            query = trimmed,
                            cookie = ao3Cookie
                        )
                    })
                    deferreds.add(launchProvider {
                        val wpCookie = runCatching {
                            com.omnitrack.app.OmniTrackApplication.instance.trackerTokenManager
                                .getFanficCookie(com.omnitrack.app.data.model.FanficSite.WATTPAD)
                        }.getOrNull()
                        com.omnitrack.app.data.fanfic.FanficSiteEngine.searchWattpad(
                            query = trimmed,
                            cookie = wpCookie,
                            targetMediaType = MediaType.FANFICTION
                        )
                    })
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
        val effectiveTmdbKey = tmdbApiKey?.trim()?.takeIf { it.isNotBlank() } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
        results.addAll(searchTmdb(query, effectiveTmdbKey, preferType))
        if (results.isEmpty()) {
            // Keyless Public Fallbacks if TMDb is unreachable:
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
                val root = json.parseToJsonElement(body) as? JsonObject ?: return@withSteamPacing emptyList()
                val items = root["items"] as? JsonArray ?: return@withSteamPacing emptyList()

                items.mapNotNull { node ->
                    val obj = node as? JsonObject ?: return@mapNotNull null
                    val appId = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
                    val name = obj["name"].cleanString() ?: return@mapNotNull null

                    // High-res Steam 600x900 vertical capsule artwork
                    val cover = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/library_600x900_2x.jpg"
                    val banner = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/library_hero.jpg"

                    SearchResultItem(
                        title = name,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                        bannerUrl = LosslessCoverMapper.toLosslessUrl(banner),
                        mediaType = MediaType.GAME,
                        sourceProvider = "Steam",
                        steamAppId = appId,
                        platform = "PC"
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Search exception for $query: ${e.message}")
            emptyList()
        }
    }

    private suspend fun searchIgdbGames(query: String): List<SearchResultItem> {
        val id = com.omnitrack.app.data.preferences.PreferenceManager.cachedIgdbClientId
        val secret = com.omnitrack.app.data.preferences.PreferenceManager.cachedIgdbClientSecret
        if (id.isBlank() || secret.isBlank()) return emptyList()
        return com.omnitrack.app.data.enrichment.IgdbApiClient(client).searchGames(id, secret, query, limit = 16)
    }

    suspend fun fetchDiscoverGamesShelves(shelf: String, page: Int = 1): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val id = com.omnitrack.app.data.preferences.PreferenceManager.cachedIgdbClientId
        val secret = com.omnitrack.app.data.preferences.PreferenceManager.cachedIgdbClientSecret
        if (id.isNotBlank() && secret.isNotBlank()) {
            val igdbItems = com.omnitrack.app.data.enrichment.IgdbApiClient(client)
                .fetchDiscoverGames(id, secret, shelf, limit = 16, offset = (safePage - 1) * 16)
            if (igdbItems.isNotEmpty()) return@withContext igdbItems
        }
        if (safePage > 1) return@withContext emptyList()
        // Keyless Steam Featured Categories fallback
        NetworkPacer.withSteamPacing {
            try {
                val req = Request.Builder()
                    .url("https://store.steampowered.com/api/featuredcategories/?l=english&cc=US")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withSteamPacing emptyList()
                    val body = resp.body?.string() ?: return@withSteamPacing emptyList()
                    val root = json.parseToJsonElement(body) as? JsonObject ?: return@withSteamPacing emptyList()
                    val catKey = when (shelf.lowercase()) {
                        "top_rated" -> "top_sellers"
                        "upcoming" -> "coming_soon"
                        else -> "new_releases"
                    }
                    val arr = (root[catKey] as? JsonObject)?.get("items") as? JsonArray ?: return@withSteamPacing emptyList()
                    arr.mapNotNull { el ->
                        val o = el as? JsonObject ?: return@mapNotNull null
                        val appId = o["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
                        val name = o["name"].cleanString() ?: return@mapNotNull null
                        SearchResultItem(
                            title = name,
                            coverUrl = LosslessCoverMapper.toLosslessUrl("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/library_600x900_2x.jpg"),
                            bannerUrl = LosslessCoverMapper.toLosslessUrl("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/library_hero.jpg"),
                            mediaType = MediaType.GAME,
                            sourceProvider = "Steam",
                            steamAppId = appId,
                            platform = "PC"
                        )
                    }.distinctBy { it.steamAppId }.take(16)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }
    }

    suspend fun fetchDiscoverBooksShelves(shelf: String, page: Int = 1): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val cacheKey = "books:${shelf.lowercase()}_p$safePage"
        discoverShelfCache[cacheKey]?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        suspend fun queryAppleBooks(term: String, media: String = "ebook", formatLabel: String = "HARDCOVER"): List<SearchResultItem> =
            NetworkPacer.withGeneralPacing {
                try {
                    val reqLimit = (safePage * 16).coerceAtMost(128)
                    val encodedTerm = URLEncoder.encode(term, "UTF-8")
                    val req = Request.Builder()
                        .url("https://itunes.apple.com/search?term=$encodedTerm&media=$media&limit=$reqLimit")
                        .header("User-Agent", "OmniTrack/1.0")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                        val results = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                            ?.get("results") as? JsonArray ?: return@withGeneralPacing emptyList()
                        results.mapNotNull { el ->
                            val o = el as? JsonObject ?: return@mapNotNull null
                            val rawTitle = (if (media == "audiobook") {
                                o["collectionName"].cleanString() ?: o["trackName"].cleanString()
                            } else {
                                o["trackName"].cleanString() ?: o["collectionName"].cleanString()
                            }) ?: return@mapNotNull null
                            val t = rawTitle.replace(Regex("""\s*\(Unabridged\)\s*$""", RegexOption.IGNORE_CASE), "").trim()
                            val cov = (o["artworkUrl100"].cleanString() ?: o["artworkUrl60"].cleanString())
                                ?.replace("100x100bb", "1200x1200bb")
                                ?.replace("60x60bb", "1200x1200bb")
                                ?.replace("http://", "https://")
                                ?: return@mapNotNull null
                            SearchResultItem(
                                title = t,
                                coverUrl = LosslessCoverMapper.toLosslessUrl(cov),
                                releaseYear = o["releaseDate"].cleanString()?.take(4),
                                synopsis = o["description"].cleanString()?.let { cleanSynopsis(it) },
                                mediaType = MediaType.BOOK,
                                sourceProvider = "Apple Books",
                                communityScore = (o["averageUserRating"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull?.let { it * 2f },
                                bookAuthor = o["artistName"].cleanString(),
                                bookFormat = formatLabel
                            )
                        }.distinctBy { it.title.lowercase() }.drop((safePage - 1) * 16).take(16)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    emptyList()
                }
            }

        suspend fun queryGoogleBooksShelf(q: String): List<SearchResultItem> =
            NetworkPacer.withGeneralPacing {
                try {
                    val startIndex = ((safePage - 1) * 16).coerceAtLeast(0)
                    val encoded = URLEncoder.encode(q, "UTF-8")
                    val req = Request.Builder()
                        .url("https://www.googleapis.com/books/v1/volumes?q=$encoded&printType=books&maxResults=16&startIndex=$startIndex")
                        .header("User-Agent", "OmniTrack/1.0")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                        val items = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                            ?.get("items") as? JsonArray ?: return@withGeneralPacing emptyList()
                        items.mapNotNull { el ->
                            val o = (el as? JsonObject)?.get("volumeInfo") as? JsonObject ?: return@mapNotNull null
                            val t = o["title"].cleanString() ?: return@mapNotNull null
                            val imgLinks = o["imageLinks"] as? JsonObject
                            val rawCov = (imgLinks?.get("extraLarge").cleanString()
                                ?: imgLinks?.get("large").cleanString()
                                ?: imgLinks?.get("medium").cleanString()
                                ?: imgLinks?.get("thumbnail").cleanString())
                                ?.replace("http://", "https://")
                                ?.replace("&edge=curl", "")
                                ?.replace("zoom=1", "zoom=2")
                                ?: return@mapNotNull null
                            val author = (o["authors"] as? JsonArray)?.firstOrNull()?.cleanString()
                            val pages = (o["pageCount"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
                            val rating5 = (o["averageRating"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull
                            SearchResultItem(
                                title = t,
                                coverUrl = LosslessCoverMapper.toLosslessUrl(rawCov),
                                releaseYear = o["publishedDate"].cleanString()?.take(4),
                                synopsis = o["description"].cleanString()?.let { cleanSynopsis(it) },
                                mediaType = MediaType.BOOK,
                                sourceProvider = "Google Books",
                                totalProgress = pages?.toDouble(),
                                communityScore = rating5?.let { it * 2f },
                                bookAuthor = author,
                                bookFormat = "HARDCOVER"
                            )
                        }.distinctBy { it.title.lowercase() }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    emptyList()
                }
            }

        suspend fun queryOpenLibrarySearch(q: String, subject: String? = null): List<SearchResultItem> =
            NetworkPacer.withOpenLibraryPacing {
                try {
                    val subjectPart = subject?.let { "&subject=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
                    val qPart = if (q.isNotBlank()) "q=${URLEncoder.encode(q, "UTF-8")}" else "q=*"
                    val fields = "key,title,author_name,first_publish_year,number_of_pages_median,ratings_average,cover_i,isbn"
                    val url = "https://openlibrary.org/search.json?$qPart$subjectPart&sort=rating&limit=16&page=$safePage&fields=$fields"
                    val req = Request.Builder().url(url).header("User-Agent", "OmniTrack/1.0").get().build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withOpenLibraryPacing emptyList()
                        val docs = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                            ?.get("docs") as? JsonArray ?: return@withOpenLibraryPacing emptyList()
                        docs.mapNotNull { el ->
                            val o = el as? JsonObject ?: return@mapNotNull null
                            val t = o["title"].cleanString() ?: return@mapNotNull null
                            val covId = (o["cover_i"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
                                ?.takeIf { it > 0 } ?: return@mapNotNull null
                            val workId = o["key"].cleanString()?.substringAfterLast("/")
                            val author = (o["author_name"] as? JsonArray)?.firstOrNull()?.cleanString()
                            val year = (o["first_publish_year"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull?.toString()
                            val pages = (o["number_of_pages_median"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
                            val r5 = (o["ratings_average"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull
                            val isbn = (o["isbn"] as? JsonArray)?.firstOrNull()?.cleanString()
                            SearchResultItem(
                                title = t,
                                coverUrl = LosslessCoverMapper.toLosslessUrl("https://covers.openlibrary.org/b/id/$covId-L.jpg"),
                                releaseYear = year,
                                mediaType = MediaType.BOOK,
                                sourceProvider = "OpenLibrary",
                                totalProgress = pages?.toDouble(),
                                communityScore = r5?.let { it * 2f },
                                openLibraryId = workId,
                                isbn = isbn,
                                bookAuthor = author,
                                bookFormat = "HARDCOVER"
                            )
                        }.distinctBy { it.openLibraryId ?: it.title.lowercase() }.take(16)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    emptyList()
                }
            }

        val resolved = when (shelf.lowercase()) {
            "audiobooks" -> queryAppleBooks(term = "bestseller fiction fantasy", media = "audiobook", formatLabel = "AUDIOBOOK")
            "classics_poetry" -> {
                val apple = queryAppleBooks(term = "classic literature homer dante shakespeare dostoevsky")
                val google = if (apple.size < 8) queryGoogleBooksShelf("subject:classics") else emptyList()
                val ol = if (apple.isEmpty() && google.isEmpty()) queryOpenLibrarySearch(q = "classic", subject = "classic literature") else emptyList()
                (apple + google + ol).distinctBy { it.title.lowercase() }.take(16)
            }
            "scifi_fantasy" -> {
                val apple = queryAppleBooks(term = "science fiction fantasy dune brandon sanderson tolkien")
                val google = if (apple.size < 8) queryGoogleBooksShelf("subject:fantasy+science+fiction") else emptyList()
                val ol = if (apple.isEmpty() && google.isEmpty()) queryOpenLibrarySearch(q = "fantasy", subject = "science fiction") else emptyList()
                (apple + google + ol).distinctBy { it.title.lowercase() }.take(16)
            }
            "philosophy_nonfiction" -> {
                val apple = queryAppleBooks(term = "philosophy stoicism history nonfiction meditations")
                val google = if (apple.size < 8) queryGoogleBooksShelf("subject:philosophy") else emptyList()
                val ol = if (apple.isEmpty() && google.isEmpty()) queryOpenLibrarySearch(q = "philosophy", subject = "philosophy") else emptyList()
                (apple + google + ol).distinctBy { it.title.lowercase() }.take(16)
            }
            else -> {
                val apple = queryAppleBooks(term = "nytimes bestseller fiction")
                val google = if (apple.size < 8) queryGoogleBooksShelf("subject:fiction") else emptyList()
                val ol = if (apple.isEmpty() && google.isEmpty()) {
                    NetworkPacer.withOpenLibraryPacing {
                        try {
                            val req = Request.Builder()
                                .url("https://openlibrary.org/trending/weekly.json?limit=16&page=$safePage")
                                .header("User-Agent", "OmniTrack/1.0")
                                .get()
                                .build()
                            client.newCall(req).execute().use { resp ->
                                if (!resp.isSuccessful) return@withOpenLibraryPacing emptyList()
                                val works = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                                    ?.get("works") as? JsonArray ?: return@withOpenLibraryPacing emptyList()
                                works.mapNotNull { el ->
                                    val o = el as? JsonObject ?: return@mapNotNull null
                                    val t = o["title"].cleanString() ?: return@mapNotNull null
                                    val covId = (o["cover_i"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
                                        ?.takeIf { it > 0 } ?: return@mapNotNull null
                                    val workId = o["key"].cleanString()?.substringAfterLast("/")
                                    val author = (o["author_name"] as? JsonArray)?.firstOrNull()?.cleanString()
                                    val year = (o["first_publish_year"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull?.toString()
                                    SearchResultItem(
                                        title = t,
                                        coverUrl = LosslessCoverMapper.toLosslessUrl("https://covers.openlibrary.org/b/id/$covId-L.jpg"),
                                        releaseYear = year,
                                        mediaType = MediaType.BOOK,
                                        sourceProvider = "OpenLibrary",
                                        openLibraryId = workId,
                                        bookAuthor = author,
                                        bookFormat = "HARDCOVER"
                                    )
                                }.distinctBy { it.openLibraryId ?: it.title.lowercase() }.take(16)
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            emptyList()
                        }
                    }
                } else emptyList()
                (apple + google + ol).distinctBy { it.title.lowercase() }.take(16)
            }
        }
        if (resolved.isNotEmpty()) discoverShelfCache[cacheKey] = resolved
        resolved
    }

    private suspend fun searchVndb(query: String): List<SearchResultItem> = NetworkPacer.withVndbPacing {
        try {
            val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
            val payload = buildJsonObject {
                put("filters", json.parseToJsonElement("[\"search\", \"=\", ${JsonPrimitive(query)}]"))
                put("fields", "id, title, alttitle, titles{lang,title,latin,official,main}, olang, length, length_minutes, released, rating, votecount, description, image{url,sexual,violence}, screenshots{url,sexual,violence}")
                put("sort", "votecount")
                put("reverse", true)
                put("results", 20)
            }

            val request = Request.Builder()
                .url("https://api.vndb.org/kana/vn")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withVndbPacing emptyList()
                val body = response.body?.string() ?: return@withVndbPacing emptyList()
                val root = json.parseToJsonElement(body) as? JsonObject ?: return@withVndbPacing emptyList()
                val results = root["results"] as? JsonArray ?: return@withVndbPacing emptyList()

                results.mapNotNull { node ->
                    val obj = node as? JsonObject ?: return@mapNotNull null
                    val id = obj["id"].cleanString() ?: return@mapNotNull null
                    val romajiTitle = obj["title"].cleanString() ?: return@mapNotNull null
                    val altTitle = obj["alttitle"].cleanString()
                    val olang = obj["olang"].cleanString()
                    val titlesArray = obj["titles"] as? JsonArray
                    val englishTitle = (titlesArray?.firstOrNull {
                        val o = it as? JsonObject ?: return@firstOrNull false
                        val isOfficial = o["official"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
                        val isMain = o["main"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
                        o["lang"].cleanString() == "en" && (isOfficial || isMain)
                    } as? JsonObject)?.get("title").cleanString()
                        ?: (titlesArray?.firstOrNull {
                            (it as? JsonObject)?.get("lang").cleanString() == "en"
                        } as? JsonObject)?.get("title").cleanString()
                    val nativeFromTitles = (titlesArray?.firstOrNull {
                        val o = it as? JsonObject ?: return@firstOrNull false
                        val isMain = o["main"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
                        isMain || o["lang"].cleanString() == olang
                    } as? JsonObject)?.get("title").cleanString()
                    val nativeTitle = altTitle ?: nativeFromTitles
                    val resolvedTitle = when (pref) {
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.ENGLISH -> englishTitle ?: romajiTitle
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.ROMAJI -> romajiTitle
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE -> nativeTitle ?: romajiTitle
                    }
                    val origin = when (olang?.lowercase()) {
                        "ja" -> "JP"
                        "en" -> "EN"
                        "zh", "zh-hans", "zh-hant" -> "CN"
                        "ko" -> "KR"
                        else -> olang?.uppercase()?.take(2)
                    }
                    val lengthMinutes = obj["length_minutes"]?.jsonPrimitive?.intOrNull
                    val releaseYear = obj["released"].cleanString()?.take(4)?.takeIf { y -> y.length == 4 && y.all { it.isDigit() } }
                    val imageObj = obj["image"] as? JsonObject
                    val cover = imageObj?.get("url").cleanString() ?: return@mapNotNull null
                    val rating = obj["rating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.let { it / 10f }
                    val desc = cleanSynopsis(
                        obj["description"].cleanString()
                            ?.replace(Regex("\\[spoiler].*?\\[/spoiler]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                            ?.replace(Regex("\\[[^\\]]*]"), "")
                    )

                    SearchResultItem(
                        title = resolvedTitle,
                        nativeTitle = nativeTitle,
                        synopsis = desc,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                        // Clean art only for auto banners (never auto-pick VN gameplay screenshots)
                        bannerUrl = null,
                        mediaType = MediaType.VISUAL_NOVEL,
                        releaseYear = releaseYear,
                        communityScore = rating,
                        sourceProvider = "VNDB",
                        vndbId = id,
                        originCountry = origin,
                        runtimeMinutes = lengthMinutes
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
                    val obj = node as? JsonObject ?: return@mapNotNull null
                    val title = obj["title"]?.cleanString()
                        ?: obj["title_orig"]?.cleanString()
                        ?: return@mapNotNull null
                    val nativeTitle = obj["title_orig"]?.cleanString()
                    val book = obj["book"] as? JsonObject
                    val filename = (book?.get("image") as? JsonObject)?.get("filename")?.cleanString()
                    val cover = if (filename != null) "https://images.ranobedb.org/$filename" else return@mapNotNull null
                    val volCount = (obj["volumes"] as? JsonObject)?.get("count")?.let { it as? JsonPrimitive }?.contentOrNull?.toDoubleOrNull()
                        ?: (obj["c_num_books"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                    val origin = obj["olang"]?.cleanString()?.uppercase() ?: "JP"
                    val startDateInt = (obj["c_start_date"] as? JsonPrimitive)?.contentOrNull
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
        val hcToken = com.omnitrack.app.data.sync.TrackerTokenManager.cachedHardcoverToken
        val deferreds = listOf(
            async {
                if (hcToken.isNotBlank()) {
                    com.omnitrack.app.data.enrichment.HardcoverApiClient(client)
                        .searchBooks(hcToken, query, limit = 8)
                } else emptyList()
            },
            async { searchOpenLibraryBooks(query) },
            async { searchGoodreads(query, MediaType.BOOK) },
            async { searchAppleBooks(query) },
            async { searchAppleAudiobooks(query) },
            async { searchPoetryDbBooks(query) },
            async { searchGoogleBooks(query) }
        )
        deferreds.awaitAll().flatten()
    }

    private suspend fun searchAppleBooks(query: String): List<SearchResultItem> = searchAppleStoreBooks(
        query = query,
        mediaParam = "ebook",
        bookFormat = "EBOOK",
        sourceProvider = "Apple Books",
        limit = 8
    )

    private suspend fun searchAppleAudiobooks(query: String): List<SearchResultItem> = searchAppleStoreBooks(
        query = query,
        mediaParam = "audiobook",
        bookFormat = "AUDIOBOOK",
        sourceProvider = "Apple Audiobooks",
        limit = 15
    )

    private suspend fun searchAppleStoreBooks(
        query: String,
        mediaParam: String,
        bookFormat: String,
        sourceProvider: String,
        limit: Int
    ): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encoded&media=$mediaParam&limit=$limit"
            val request = Request.Builder().url(url).header("User-Agent", "OmniTrack/1.0").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val results = (json.parseToJsonElement(body) as? JsonObject)
                    ?.get("results") as? JsonArray ?: return@withGeneralPacing emptyList()
                results.mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    val rawTitle = if (mediaParam == "audiobook") {
                        o["collectionName"].cleanString() ?: o["trackName"].cleanString()
                    } else {
                        o["trackName"].cleanString() ?: o["collectionName"].cleanString()
                    } ?: return@mapNotNull null
                    val title = rawTitle.replace(Regex("""\s*\(Unabridged\)\s*$""", RegexOption.IGNORE_CASE), "").trim()
                    val rawCov = o["artworkUrl100"].cleanString() ?: o["artworkUrl60"].cleanString() ?: return@mapNotNull null
                    val hiResCov = rawCov.replace("100x100bb", "1200x1200bb")
                        .replace("60x60bb", "1200x1200bb")
                        .replace("http://", "https://")
                    val author = o["artistName"].cleanString()
                    val desc = cleanSynopsis(o["description"].cleanString())
                    val durMins = ((o["collectionTimeMillis"] ?: o["trackTimeMillis"]) as? JsonPrimitive)
                        ?.takeIf { it !is JsonNull }?.contentOrNull?.toLongOrNull()
                        ?.takeIf { it > 60_000L }?.let { (it / 60_000L).toInt() }
                    val durLabel = durMins?.let { com.omnitrack.app.data.model.formatBookDuration(it) }
                    val synopsisHeader = listOfNotNull(
                        author?.let { if (mediaParam == "audiobook") "Narrated / By $it" else "By $it" },
                        durLabel?.let { "Duration: $it" }
                    ).joinToString(" · ").takeIf { it.isNotBlank() }
                    val fullSynopsis = listOfNotNull(synopsisHeader, desc).joinToString("\n\n").ifBlank { null }
                    val score = (o["averageUserRating"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull?.let { it * 2f }
                    SearchResultItem(
                        title = title,
                        synopsis = fullSynopsis,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(hiResCov),
                        mediaType = MediaType.BOOK,
                        releaseYear = o["releaseDate"].cleanString()?.take(4),
                        communityScore = score,
                        bookAuthor = author,
                        bookFormat = bookFormat,
                        sourceProvider = sourceProvider
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private suspend fun searchPoetryDbBooks(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val cleanQ = query.trim()
            if (cleanQ.length < 3 || cleanQ.length > 64) return@withGeneralPacing emptyList()
            val encoded = URLEncoder.encode(cleanQ, "UTF-8").replace("+", "%20")
            val request = Request.Builder()
                .url("https://poetrydb.org/title/$encoded/title,author,linecount")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val arr = json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonArray
                    ?: return@withGeneralPacing emptyList()
                arr.take(4).mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    val title = o["title"].cleanString() ?: return@mapNotNull null
                    val author = o["author"].cleanString()
                    val lineCount = o["linecount"].cleanString()
                    SearchResultItem(
                        title = title,
                        synopsis = listOfNotNull(
                            author?.let { "Poem by $it" },
                            lineCount?.let { "$it lines (PoetryDB)" }
                        ).joinToString(" · ").ifBlank { null },
                        coverUrl = "",
                        mediaType = MediaType.BOOK,
                        bookAuthor = author,
                        bookFormat = "PAPERBACK",
                        sourceProvider = "PoetryDB"
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private suspend fun searchGoogleBooks(query: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=10"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withGeneralPacing emptyList()
                val body = response.body?.string() ?: return@withGeneralPacing emptyList()
                val root = json.parseToJsonElement(body) as? JsonObject ?: return@withGeneralPacing emptyList()
                val items = root["items"] as? JsonArray ?: return@withGeneralPacing emptyList()

                items.mapNotNull { item ->
                    val volumeInfo = (item as? JsonObject)?.get("volumeInfo") as? JsonObject ?: return@mapNotNull null
                    val title = volumeInfo["title"].cleanString() ?: return@mapNotNull null
                    val authors = (volumeInfo["authors"] as? JsonArray)?.mapNotNull { it.cleanString() }?.joinToString(", ")
                    val desc = cleanSynopsis(volumeInfo["description"].cleanString())
                    val date = volumeInfo["publishedDate"].cleanString()
                    val year = date?.split("-")?.firstOrNull()?.cleanString()
                    val pages = (volumeInfo["pageCount"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.toDoubleOrNull()
                    val score = (volumeInfo["averageRating"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.toFloatOrNull()?.let { it * 2f }
                    val isbn = (volumeInfo["industryIdentifiers"] as? JsonArray)?.firstNotNullOfOrNull { idEl ->
                        (idEl as? JsonObject)?.get("identifier").cleanString()
                    }

                    val imageLinks = volumeInfo["imageLinks"] as? JsonObject
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
                        isbn = isbn,
                        bookAuthor = authors,
                        bookFormat = "HARDCOVER",
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

    private suspend fun searchOpenLibraryBooks(query: String): List<SearchResultItem> = NetworkPacer.withOpenLibraryPacing {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val fields = "key,title,author_name,first_publish_year,number_of_pages_median,ratings_average,cover_i,isbn"
            val url = "https://openlibrary.org/search.json?q=$encoded&limit=12&fields=$fields"
            val request = Request.Builder().url(url).header("User-Agent", "OmniTrack/1.0").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withOpenLibraryPacing emptyList()
                val body = response.body?.string() ?: return@withOpenLibraryPacing emptyList()
                val root = json.parseToJsonElement(body) as? JsonObject ?: return@withOpenLibraryPacing emptyList()
                val docs = root["docs"] as? JsonArray ?: return@withOpenLibraryPacing emptyList()

                docs.mapNotNull { node ->
                    val obj = node as? JsonObject ?: return@mapNotNull null
                    val title = obj["title"].cleanString() ?: return@mapNotNull null
                    val coverId = obj["cover_i"].cleanString() ?: return@mapNotNull null
                    val coverUrl = "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
                    val workId = obj["key"].cleanString()?.substringAfterLast("/")
                    val year = obj["first_publish_year"].cleanString()
                    val author = (obj["author_name"] as? JsonArray)?.firstOrNull()?.cleanString()
                    val pages = (obj["number_of_pages_median"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.toDoubleOrNull()
                    val score = (obj["ratings_average"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull?.let { it * 2f }
                    val isbn = (obj["isbn"] as? JsonArray)?.firstOrNull()?.cleanString()

                    SearchResultItem(
                        title = title,
                        synopsis = author?.let { "By $it" },
                        coverUrl = LosslessCoverMapper.toLosslessUrl(coverUrl),
                        mediaType = MediaType.BOOK,
                        releaseYear = year,
                        communityScore = score,
                        totalProgress = pages,
                        openLibraryId = workId,
                        isbn = isbn,
                        bookAuthor = author,
                        bookFormat = "HARDCOVER",
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

    private suspend fun searchGoodreads(query: String, targetType: MediaType): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        val cleanQ = query.trim()
        if (cleanQ.length < 2) return@withGeneralPacing emptyList()
        val cacheKey = "gr:${targetType.name}:${cleanQ.lowercase()}"
        providerSearchCache[cacheKey]?.let { return@withGeneralPacing it }
        try {
            val encoded = URLEncoder.encode(cleanQ, "UTF-8")
            val req = Request.Builder()
                .url("https://www.goodreads.com/book/auto_complete?format=json&q=$encoded")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                val arr = json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonArray
                    ?: return@withGeneralPacing emptyList()
                val results = arr.mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    val rawTitle = o["bookTitleBare"].cleanString() ?: o["title"].cleanString() ?: return@mapNotNull null
                    val rawCover = o["imageUrl"].cleanString() ?: return@mapNotNull null
                    if (rawCover.contains("nophoto", ignoreCase = true)) return@mapNotNull null
                    val hiResCover = rawCover.replace(Regex("""\._(?:S[XY]|U[XY]|CR[\d,]+)\d*_"""), "")
                    val author = (o["author"] as? JsonObject)?.get("name").cleanString()
                    val rating5 = (o["avgRating"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.toFloatOrNull()
                    val score10 = rating5?.takeIf { it > 0f }?.let { (it * 2f).coerceIn(0f, 10f) }
                    val pages = (o["numPages"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.toDoubleOrNull()?.takeIf { it > 0.0 }
                    val rawDesc = (o["description"] as? JsonObject)?.get("html").cleanString()
                    val cleanedDesc = cleanSynopsis(rawDesc)
                    val synopsisHeader = author?.let { "By $it" }
                    val fullSynopsis = listOfNotNull(synopsisHeader, cleanedDesc).distinct().joinToString("\n\n").ifBlank { null }
                    SearchResultItem(
                        title = rawTitle,
                        synopsis = fullSynopsis,
                        coverUrl = LosslessCoverMapper.toLosslessUrl(hiResCover),
                        mediaType = targetType,
                        communityScore = score10,
                        totalProgress = if (targetType == MediaType.BOOK) pages else null,
                        bookAuthor = author,
                        bookFormat = if (targetType == MediaType.BOOK) "Book" else null,
                        platform = if (targetType == MediaType.NOVEL) "WEB_NOVEL" else null,
                        sourceProvider = "Goodreads"
                    )
                }
                if (results.isNotEmpty()) {
                    providerSearchCache[cacheKey] = results
                }
                results
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("OmniSearchRepository", "Goodreads search exception for $cleanQ: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchAniListDiscoverShelf(
        cacheKey: String,
        aniType: String,
        sort: String,
        targetType: MediaType,
        formatFilter: String? = null,
        formatNotFilter: String? = null,
        countryOfOrigin: String? = null,
        page: Int = 1
    ): List<SearchResultItem> {
        val safePage = page.coerceAtLeast(1)
        val pagedCacheKey = "${cacheKey}_p$safePage"
        discoverShelfCache[pagedCacheKey]?.takeIf { it.isNotEmpty() }?.let { return it }
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        val args = buildList {
            add("type: $aniType")
            add("sort: [$sort]")
            add("isAdult: false")
            formatFilter?.let { add("format: $it") }
            formatNotFilter?.let { add("format_not: $it") }
            countryOfOrigin?.let { add("countryOfOrigin: \"$it\"") }
        }.joinToString(", ")
        val gql = """
            query {
                Page(page: $safePage, perPage: 16) {
                    media($args) {
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
        return NetworkPacer.withAniListSearchPacing {
            try {
                val payload = buildJsonObject { put("query", gql) }
                val req = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .header("User-Agent", "OmniTrack/1.0 (Android; Linux)")
                    .header("Accept", "application/json")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withAniListSearchPacing emptyList()
                    val mediaList = json.parseToJsonElement(resp.body?.string().orEmpty())
                        .jsonObject["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
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
                        val cover = obj["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                            ?: obj["coverImage"]?.jsonObject?.get("large").cleanString()
                            ?: return@mapNotNull null
                        val banner = obj["bannerImage"].cleanString()
                        val origin = obj["countryOfOrigin"].cleanString()
                        val score = obj["averageScore"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.let { it / 10f }
                        val year = obj["startDate"]?.jsonObject?.get("year").cleanString()
                        val anilistId = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        val malId = obj["idMal"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        val totalProgress = when (targetType) {
                            MediaType.ANIME -> obj["episodes"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                            MediaType.NOVEL -> obj["volumes"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                                ?: obj["chapters"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                            else -> obj["chapters"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                        }
                        val novelPlatform = if (targetType == MediaType.NOVEL) {
                            if (origin == null || origin.equals("JP", ignoreCase = true)) "LIGHT_NOVEL" else "WEB_NOVEL"
                        } else null
                        SearchResultItem(
                            title = title,
                            nativeTitle = nat,
                            synopsis = cleanSynopsis(obj["description"].cleanString()),
                            coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                            bannerUrl = banner?.let { LosslessCoverMapper.toLosslessUrl(it) },
                            mediaType = targetType,
                            releaseYear = year,
                            communityScore = score,
                            sourceProvider = "AniList",
                            anilistId = anilistId,
                            malId = malId,
                            totalProgress = totalProgress,
                            originCountry = origin,
                            platform = novelPlatform
                        )
                    }
                    if (parsed.isNotEmpty()) discoverShelfCache[pagedCacheKey] = parsed
                    parsed
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }
    }

    suspend fun fetchDiscoverAnimeShelves(shelf: String, page: Int = 1): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val sort = when (shelf.lowercase()) {
            "top_rated" -> "SCORE_DESC"
            "popular" -> "POPULARITY_DESC"
            else -> "TRENDING_DESC"
        }
        fetchAniListDiscoverShelf("anime:$sort", "ANIME", sort, MediaType.ANIME, page = page)
    }

    suspend fun fetchDiscoverMangaShelves(shelf: String, page: Int = 1): List<SearchResultItem> = withContext(Dispatchers.IO) {
        when (shelf.lowercase()) {
            "manhwa" -> fetchAniListDiscoverShelf(
                cacheKey = "manga:manhwa",
                aniType = "MANGA",
                sort = "POPULARITY_DESC",
                targetType = MediaType.MANGA,
                formatNotFilter = "NOVEL",
                countryOfOrigin = "KR",
                page = page
            )
            "top_rated" -> fetchAniListDiscoverShelf(
                cacheKey = "manga:top_rated",
                aniType = "MANGA",
                sort = "SCORE_DESC",
                targetType = MediaType.MANGA,
                formatNotFilter = "NOVEL",
                page = page
            )
            else -> fetchAniListDiscoverShelf(
                cacheKey = "manga:trending",
                aniType = "MANGA",
                sort = "TRENDING_DESC",
                targetType = MediaType.MANGA,
                formatNotFilter = "NOVEL",
                page = page
            )
        }
    }

    private suspend fun fetchGoodreadsWebFictionShelf(shelfSlug: String, page: Int): List<SearchResultItem> =
        NetworkPacer.withGeneralPacing {
            val safePage = page.coerceAtLeast(1)
            val cacheKey = "novel:gr_${shelfSlug}_p$safePage"
            discoverShelfCache[cacheKey]?.takeIf { it.isNotEmpty() }?.let { return@withGeneralPacing it }
            try {
                val req = Request.Builder()
                    .url("https://www.goodreads.com/shelf/show/$shelfSlug?page=$safePage")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                    val html = resp.body?.string().orEmpty()
                    val blocks = html.split("class=\"elementList\"").drop(1)
                    if (blocks.isEmpty()) return@withGeneralPacing emptyList()
                    val imgRegex = Regex("""<img[^>]+src="([^"]+)"""")
                    val titleRegex = Regex("""<a class="bookTitle"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
                    val authorRegex = Regex("""class="authorName"[^>]*><span[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
                    val ratingRegex = Regex("""avg rating\s+([0-9.]+)""")
                    val yearRegex = Regex("""published\s+(\d{4})""")
                    val coverSizeRegex = Regex("""\._(?:S[XY]|U[XY]|CR[\d,]+)\d*_""")
                    fun unescapeHtml(raw: String): String =
                        android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()

                    val parsed = blocks.take(50).mapNotNull { rawBlock ->
                        val block = rawBlock.substringBefore("<div class=\"right\"")
                        val rawTitle = titleRegex.find(block)?.groupValues?.getOrNull(1)?.let(::unescapeHtml)
                            ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val rawImg = imgRegex.find(block)?.groupValues?.getOrNull(1).orEmpty()
                        if (rawImg.isBlank() || "nophoto" in rawImg) return@mapNotNull null
                        val hdCover = coverSizeRegex.replace(rawImg, "")
                        val author = authorRegex.find(block)?.groupValues?.getOrNull(1)?.let(::unescapeHtml)
                        val r5 = ratingRegex.find(block)?.groupValues?.getOrNull(1)?.toFloatOrNull()
                        val year = yearRegex.find(block)?.groupValues?.getOrNull(1)
                        SearchResultItem(
                            title = rawTitle,
                            coverUrl = LosslessCoverMapper.toLosslessUrl(hdCover),
                            mediaType = MediaType.NOVEL,
                            releaseYear = year,
                            communityScore = r5?.let { it * 2f },
                            sourceProvider = "Goodreads",
                            bookAuthor = author,
                            platform = "WEB_NOVEL"
                        )
                    }.distinctBy { it.title.trim().lowercase() }
                    if (parsed.isNotEmpty()) discoverShelfCache[cacheKey] = parsed
                    parsed
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.w("OmniSearchRepository", "Goodreads shelf ($shelfSlug p$safePage) error: ${e.message}")
                emptyList()
            }
        }

    suspend fun fetchDiscoverNovelsShelves(shelf: String, page: Int = 1): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        when (shelf.lowercase()) {
            "top_webnovels" -> {
                val live = fetchGoodreadsWebFictionShelf("web-fiction", safePage)
                if (live.isNotEmpty() || safePage > 1) live
                else fetchGoodreadsWebFictionShelf("web-novels", 1)
            }
            "wattpad_webnovels" -> {
                val wpCookie = runCatching {
                    com.omnitrack.app.OmniTrackApplication.instance.trackerTokenManager
                        .getFanficCookie(com.omnitrack.app.data.model.FanficSite.WATTPAD)
                }.getOrNull()
                com.omnitrack.app.data.fanfic.FanficSiteEngine.fetchWattpadDiscoverShelf("wattpad_webnovels", safePage, wpCookie)
            }
            "progression_webnovels" -> fetchGoodreadsWebFictionShelf("progression-fantasy", safePage)
            "top_rated_ln" -> fetchAniListDiscoverShelf(
                cacheKey = "novel:top_rated_ln",
                aniType = "MANGA",
                sort = "SCORE_DESC",
                targetType = MediaType.NOVEL,
                formatFilter = "NOVEL",
                page = safePage
            )
            else -> fetchAniListDiscoverShelf(
                cacheKey = "novel:trending_ln",
                aniType = "MANGA",
                sort = "TRENDING_DESC",
                targetType = MediaType.NOVEL,
                formatFilter = "NOVEL",
                page = safePage
            )
        }
    }

    private suspend fun fetchTmdbOrCinemetaShelf(
        cacheKey: String,
        targetType: MediaType,
        shelf: String,
        tmdbApiKey: String?,
        page: Int = 1
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val pagedCacheKey = "${cacheKey}_p$safePage"
        discoverShelfCache[pagedCacheKey]?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        val isMovie = targetType == MediaType.MOVIE
        val key = tmdbApiKey?.trim()?.takeIf { it.isNotBlank() } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
        if (key.isNotBlank()) {
            val tmdbItems = NetworkPacer.withTmdbPacing {
                try {
                    val seg = if (isMovie) "movie" else "tv"
                    val url = when (shelf.lowercase()) {
                        "top_rated" -> "https://api.themoviedb.org/3/$seg/top_rated?api_key=$key&page=$safePage"
                        "popular" -> "https://api.themoviedb.org/3/$seg/popular?api_key=$key&page=$safePage"
                        else -> "https://api.themoviedb.org/3/trending/$seg/week?api_key=$key&page=$safePage"
                    }
                    val req = Request.Builder().url(url).get().build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withTmdbPacing emptyList()
                        val results = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                            ?.get("results") as? JsonArray ?: return@withTmdbPacing emptyList()
                        results.take(20).mapNotNull { el ->
                            val o = el as? JsonObject ?: return@mapNotNull null
                            val title = o["name"].cleanString() ?: o["title"].cleanString() ?: return@mapNotNull null
                            val posterPath = o["poster_path"].cleanString() ?: return@mapNotNull null
                            val backdropPath = o["backdrop_path"].cleanString()
                            val date = o["first_air_date"].cleanString() ?: o["release_date"].cleanString()
                            SearchResultItem(
                                title = title,
                                nativeTitle = o["original_name"].cleanString() ?: o["original_title"].cleanString(),
                                synopsis = cleanSynopsis(o["overview"].cleanString()),
                                coverUrl = LosslessCoverMapper.toLosslessUrl("https://image.tmdb.org/t/p/original$posterPath"),
                                bannerUrl = backdropPath?.let { LosslessCoverMapper.toLosslessUrl("https://image.tmdb.org/t/p/original$it") },
                                mediaType = targetType,
                                releaseYear = date?.split("-")?.firstOrNull()?.cleanString(),
                                communityScore = (o["vote_average"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull,
                                sourceProvider = "TMDb",
                                tmdbId = (o["id"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.toLongOrNull()
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    emptyList()
                }
            }
            if (tmdbItems.isNotEmpty()) {
                discoverShelfCache[pagedCacheKey] = tmdbItems
                return@withContext tmdbItems
            }
        }
        // Keyless Cinemeta fallback (supports skip= pagination)
        NetworkPacer.withGeneralPacing {
            try {
                val cType = if (isMovie) "movie" else "series"
                val catalog = if (shelf.equals("top_rated", ignoreCase = true)) "imdbRating" else "top"
                val skip = (safePage - 1) * 20
                val cinUrl = if (skip > 0) {
                    "https://v3-cinemeta.strem.io/catalog/$cType/$catalog/skip=$skip.json"
                } else {
                    "https://v3-cinemeta.strem.io/catalog/$cType/$catalog.json"
                }
                val req = Request.Builder()
                    .url(cinUrl)
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                    val metas = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                        ?.get("metas") as? JsonArray ?: return@withGeneralPacing emptyList()
                    val parsed = metas.take(20).mapNotNull { el ->
                        val o = el as? JsonObject ?: return@mapNotNull null
                        val title = o["name"].cleanString() ?: return@mapNotNull null
                        val rawPoster = o["poster"].cleanString() ?: return@mapNotNull null
                        val rawBanner = o["background"].cleanString()
                        SearchResultItem(
                            title = title,
                            synopsis = cleanSynopsis(o["description"].cleanString()),
                            coverUrl = LosslessCoverMapper.toLosslessUrl(rawPoster.replace("/small/", "/medium/")),
                            bannerUrl = rawBanner?.replace("/small/", "/medium/")?.let { LosslessCoverMapper.toLosslessUrl(it) },
                            mediaType = targetType,
                            releaseYear = o["releaseInfo"].cleanString() ?: o["year"].cleanString(),
                            communityScore = (o["imdbRating"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.toFloatOrNull(),
                            sourceProvider = "Cinemeta",
                            imdbId = o["imdb_id"].cleanString() ?: o["id"].cleanString()?.takeIf { it.startsWith("tt") }
                        )
                    }
                    if (parsed.isNotEmpty()) discoverShelfCache[pagedCacheKey] = parsed
                    parsed
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }
    }

    suspend fun fetchDiscoverShowsShelves(shelf: String, tmdbApiKey: String? = null, page: Int = 1): List<SearchResultItem> =
        fetchTmdbOrCinemetaShelf("show:$shelf:${!tmdbApiKey.isNullOrBlank()}", MediaType.SHOW, shelf, tmdbApiKey, page)

    suspend fun fetchDiscoverMoviesShelves(shelf: String, tmdbApiKey: String? = null, page: Int = 1): List<SearchResultItem> =
        fetchTmdbOrCinemetaShelf("movie:$shelf:${!tmdbApiKey.isNullOrBlank()}", MediaType.MOVIE, shelf, tmdbApiKey, page)

    suspend fun fetchDiscoverVisualNovelsShelves(shelf: String, page: Int = 1): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val cacheKey = "vn:${shelf.lowercase()}_p$safePage"
        discoverShelfCache[cacheKey]?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        val hideNsfw = com.omnitrack.app.data.preferences.PreferenceManager.cachedVnNsfwFilterMode ==
            com.omnitrack.app.data.model.VnNsfwFilterMode.SAFE_HIDE
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        NetworkPacer.withVndbPacing {
            try {
                val payload = buildJsonObject {
                    put("filters", json.parseToJsonElement("[\"votecount\", \">=\", 250]"))
                    put("fields", "id, title, alttitle, titles{lang,title,latin,official,main}, olang, length_minutes, released, rating, votecount, description, image{url,sexual,violence}, screenshots{url,sexual,violence}")
                    put("sort", if (shelf.equals("top_rated", ignoreCase = true)) "rating" else "votecount")
                    put("reverse", true)
                    put("results", 16)
                    put("page", safePage)
                }
                val req = Request.Builder()
                    .url("https://api.vndb.org/kana/vn")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withVndbPacing emptyList()
                    val results = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                        ?.get("results") as? JsonArray ?: return@withVndbPacing emptyList()
                    val parsed = results.mapNotNull { node ->
                        val obj = node as? JsonObject ?: return@mapNotNull null
                        val id = obj["id"].cleanString() ?: return@mapNotNull null
                        val romajiTitle = obj["title"].cleanString() ?: return@mapNotNull null
                        val altTitle = obj["alttitle"].cleanString()
                        val olang = obj["olang"].cleanString()
                        val titlesArray = obj["titles"] as? JsonArray
                        val englishTitle = (titlesArray?.firstOrNull {
                            val o = it as? JsonObject ?: return@firstOrNull false
                            val isOfficial = o["official"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
                            val isMain = o["main"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
                            o["lang"].cleanString() == "en" && (isOfficial || isMain)
                        } as? JsonObject)?.get("title").cleanString()
                            ?: (titlesArray?.firstOrNull { (it as? JsonObject)?.get("lang").cleanString() == "en" } as? JsonObject)?.get("title").cleanString()
                        val resolvedTitle = when (pref) {
                            com.omnitrack.app.data.preferences.PreferredTitleLanguage.ENGLISH -> englishTitle ?: romajiTitle
                            com.omnitrack.app.data.preferences.PreferredTitleLanguage.ROMAJI -> romajiTitle
                            com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE -> altTitle ?: romajiTitle
                        }
                        val imageObj = obj["image"] as? JsonObject ?: return@mapNotNull null
                        val imgSexual = imageObj["sexual"]?.jsonPrimitive?.floatOrNull ?: 0f
                        val imgViol = imageObj["violence"]?.jsonPrimitive?.floatOrNull ?: 0f
                        if (hideNsfw && (imgSexual >= 1.0f || imgViol >= 1.5f)) return@mapNotNull null
                        val cover = imageObj["url"].cleanString() ?: return@mapNotNull null
                        val origin = when (olang?.lowercase()) {
                            "ja" -> "JP"
                            "en" -> "EN"
                            "zh", "zh-hans", "zh-hant" -> "CN"
                            "ko" -> "KR"
                            else -> olang?.uppercase()?.take(2)
                        }
                        SearchResultItem(
                            title = resolvedTitle,
                            nativeTitle = altTitle,
                            synopsis = cleanSynopsis(obj["description"].cleanString()?.replace(Regex("\\[[^\\]]*]"), "")),
                            coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                            // Clean art only for Discover VN hero/spotlight (no gameplay screenshots)
                            bannerUrl = null,
                            mediaType = MediaType.VISUAL_NOVEL,
                            releaseYear = obj["released"].cleanString()?.take(4)?.takeIf { y -> y.length == 4 && y.all { it.isDigit() } },
                            communityScore = obj["rating"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.let { it / 10f },
                            sourceProvider = "VNDB",
                            vndbId = id,
                            originCountry = origin,
                            runtimeMinutes = obj["length_minutes"]?.jsonPrimitive?.intOrNull
                        )
                    }
                    if (parsed.isNotEmpty()) discoverShelfCache[cacheKey] = parsed
                    parsed
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }
    }

    suspend fun fetchDiscoverComicsShelves(shelf: String, page: Int = 1): List<SearchResultItem> =
        com.omnitrack.app.data.enrichment.ComicEnrichmentSource(client).fetchDiscoverComicsShelves(shelf, page)

    private suspend fun searchComics(query: String): List<SearchResultItem> =
        com.omnitrack.app.data.enrichment.ComicEnrichmentSource(client).searchComics(query)

    private fun buildTmdbRequest(pathAndQuery: String, tmdbApiKey: String?): Request {
        val effectiveKey = tmdbApiKey?.trim()?.takeIf { it.isNotBlank() } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
        return if (TrackerAccountVerifier.isTmdbV4Jwt(effectiveKey)) {
            Request.Builder()
                .url("https://api.themoviedb.org/3/$pathAndQuery")
                .header("Authorization", "Bearer $effectiveKey")
                .header("Accept", "application/json")
                .get()
                .build()
        } else {
            val sep = if ('?' in pathAndQuery) '&' else '?'
            Request.Builder()
                .url("https://api.themoviedb.org/3/$pathAndQuery${sep}api_key=$effectiveKey")
                .header("Accept", "application/json")
                .get()
                .build()
        }
    }

    private fun parseTmdbDiscoverItems(
        results: JsonArray,
        forcedMediaType: MediaType? = null
    ): List<SearchResultItem> {
        return results.take(20).mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val mediaStr = o["media_type"].cleanString()?.lowercase()
            val title = o["name"].cleanString() ?: o["title"].cleanString() ?: return@mapNotNull null
            val posterPath = o["poster_path"].cleanString() ?: return@mapNotNull null
            val backdropPath = o["backdrop_path"].cleanString()
            val date = o["first_air_date"].cleanString() ?: o["release_date"].cleanString()
            val genreIds = o["genre_ids"] as? JsonArray
            val isAnimeGenre = genreIds?.any { (it as? JsonPrimitive)?.intOrNull == 16 } == true &&
                o["original_language"].cleanString().equals("ja", ignoreCase = true)
            val inferredType = when {
                forcedMediaType != null -> forcedMediaType
                mediaStr == "movie" -> MediaType.MOVIE
                isAnimeGenre -> MediaType.ANIME
                mediaStr == "tv" || o["first_air_date"].cleanString() != null -> MediaType.SHOW
                else -> MediaType.MOVIE
            }
            SearchResultItem(
                title = title,
                nativeTitle = o["original_name"].cleanString() ?: o["original_title"].cleanString(),
                synopsis = cleanSynopsis(o["overview"].cleanString()),
                coverUrl = LosslessCoverMapper.toLosslessUrl("https://image.tmdb.org/t/p/original$posterPath"),
                bannerUrl = backdropPath?.let { LosslessCoverMapper.toLosslessUrl("https://image.tmdb.org/t/p/original$it") },
                mediaType = inferredType,
                releaseYear = date?.split("-")?.firstOrNull()?.cleanString(),
                communityScore = (o["vote_average"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull,
                sourceProvider = "TMDb",
                tmdbId = (o["id"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.toLongOrNull()
            )
        }
    }

    suspend fun fetchPopularStreamingProviders(
        regionCode: String,
        tmdbApiKey: String? = null
    ): List<StreamingProviderChip> = withContext(Dispatchers.IO) {
        val normRegion = regionCode.trim().uppercase().ifBlank { "US" }
        streamingProvidersCache[normRegion]?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        NetworkPacer.withTmdbPacing {
            try {
                val req = buildTmdbRequest("watch/providers/tv?language=en-US&watch_region=$normRegion", tmdbApiKey)
                val parsed = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val results = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                        ?.get("results") as? JsonArray ?: return@use emptyList()
                    val preferredIds = setOf(8, 283, 337, 9, 119, 350, 1899, 384, 15, 531, 386, 2, 3, 29, 387)
                    results.mapNotNull { el ->
                        val o = el as? JsonObject ?: return@mapNotNull null
                        val id = (o["provider_id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                        val name = o["provider_name"].cleanString() ?: return@mapNotNull null
                        val logo = o["logo_path"].cleanString() ?: return@mapNotNull null
                        val priority = (o["display_priority"] as? JsonPrimitive)?.intOrNull ?: 999
                        Triple(
                            StreamingProviderChip(id, name, "https://image.tmdb.org/t/p/w154$logo"),
                            if (id in preferredIds) 0 else 1,
                            priority
                        )
                    }.sortedWith(compareBy({ it.second }, { it.third }))
                        .map { it.first }
                        .distinctBy { it.providerId }
                        .take(14)
                }
                if (parsed.isNotEmpty()) streamingProvidersCache[normRegion] = parsed
                parsed
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }
    }

    suspend fun fetchStreamingServiceTitles(
        providerId: Int,
        regionCode: String,
        mediaFilter: MediaType,
        tmdbApiKey: String? = null
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val normRegion = regionCode.trim().uppercase().ifBlank { "US" }
        val cacheKey = "stream_${providerId}_${normRegion}_${mediaFilter.name}"
        discoverShelfCache[cacheKey]?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        val wantMovie = mediaFilter == MediaType.ALL || mediaFilter == MediaType.MOVIE
        val wantTv = mediaFilter == MediaType.ALL || mediaFilter == MediaType.SHOW || mediaFilter == MediaType.ANIME
        val out = coroutineScope {
            val movieDef: kotlinx.coroutines.Deferred<List<SearchResultItem>>? = if (wantMovie) {
                async<List<SearchResultItem>> {
                    NetworkPacer.withTmdbPacing {
                        runCatching {
                            val req = buildTmdbRequest(
                                "discover/movie?with_watch_providers=$providerId&watch_region=$normRegion&sort_by=popularity.desc&page=1",
                                tmdbApiKey
                            )
                            client.newCall(req).execute().use { resp ->
                                if (!resp.isSuccessful) emptyList()
                                else {
                                    val arr = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                                        ?.get("results") as? JsonArray ?: return@use emptyList()
                                    parseTmdbDiscoverItems(arr, MediaType.MOVIE)
                                }
                            }
                        }.getOrDefault(emptyList())
                    }
                }
            } else null
            val tvDef: kotlinx.coroutines.Deferred<List<SearchResultItem>>? = if (wantTv) {
                async<List<SearchResultItem>> {
                    NetworkPacer.withTmdbPacing {
                        runCatching {
                            val genreClause = if (mediaFilter == MediaType.ANIME) "&with_genres=16&with_original_language=ja" else ""
                            val req = buildTmdbRequest(
                                "discover/tv?with_watch_providers=$providerId&watch_region=$normRegion&sort_by=popularity.desc&page=1$genreClause",
                                tmdbApiKey
                            )
                            client.newCall(req).execute().use { resp ->
                                if (!resp.isSuccessful) emptyList()
                                else {
                                    val arr = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                                        ?.get("results") as? JsonArray ?: return@use emptyList()
                                    parseTmdbDiscoverItems(arr, if (mediaFilter == MediaType.ANIME) MediaType.ANIME else MediaType.SHOW)
                                }
                            }
                        }.getOrDefault(emptyList())
                    }
                }
            } else null
            val movies = movieDef?.await().orEmpty()
            val shows = tvDef?.await().orEmpty()
            val interleaved = mutableListOf<SearchResultItem>()
            val maxLen = maxOf(movies.size, shows.size)
            for (i in 0 until maxLen) {
                if (i < shows.size) interleaved.add(shows[i])
                if (i < movies.size) interleaved.add(movies[i])
            }
            interleaved.distinctBy { "${it.mediaType}_${it.title.lowercase()}" }.take(20)
        }
        if (out.isNotEmpty()) discoverShelfCache[cacheKey] = out
        out
    }

    suspend fun fetchTop10Today(
        mediaFilter: MediaType,
        tmdbApiKey: String? = null
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val cacheKey = "top10_today_${mediaFilter.name}"
        discoverShelfCache[cacheKey]?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        val items = when (mediaFilter) {
            MediaType.ANIME -> fetchDiscoverAnimeShelves("trending", 1).take(10)
            MediaType.MANGA -> fetchDiscoverMangaShelves("trending", 1).take(10)
            MediaType.NOVEL -> fetchDiscoverNovelsShelves("trending_ln", 1).take(10)
            MediaType.BOOK -> fetchDiscoverBooksShelves("trending", 1).take(10)
            MediaType.GAME -> fetchDiscoverGamesShelves("top_rated", 1).take(10)
            MediaType.VISUAL_NOVEL -> fetchDiscoverVisualNovelsShelves("popular", 1).take(10)
            MediaType.COMIC -> fetchDiscoverComicsShelves("trending", 1).take(10)
            else -> NetworkPacer.withTmdbPacing {
                try {
                    val seg = when (mediaFilter) {
                        MediaType.MOVIE -> "movie"
                        MediaType.SHOW -> "tv"
                        else -> "all"
                    }
                    val req = buildTmdbRequest("trending/$seg/day?language=en-US", tmdbApiKey)
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) emptyList()
                        else {
                            val arr = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                                ?.get("results") as? JsonArray ?: return@use emptyList()
                            parseTmdbDiscoverItems(arr, mediaFilter.takeIf { it != MediaType.ALL }).take(10)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    emptyList()
                }
            }
        }
        if (items.isNotEmpty()) discoverShelfCache[cacheKey] = items
        items
    }

    suspend fun fetchCuratedDiscoverRow(
        rowKey: String,
        tmdbApiKey: String? = null
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val cacheKey = "curated_$rowKey"
        discoverShelfCache[cacheKey]?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        val (endpoint, forcedType) = when (rowKey) {
            "under_90" -> "discover/movie?with_runtime.lte=90&with_runtime.gte=55&vote_average.gte=7.0&vote_count.gte=250&sort_by=popularity.desc&page=1" to MediaType.MOVIE
            "hidden_gems" -> "discover/movie?vote_average.gte=7.8&vote_count.gte=120&vote_count.lte=1200&sort_by=vote_average.desc&page=1" to MediaType.MOVIE
            "limited_series" -> "discover/tv?with_type=2&vote_average.gte=7.5&vote_count.gte=150&sort_by=popularity.desc&page=1" to MediaType.SHOW
            "decade_2020s" -> "discover/movie?primary_release_date.gte=2020-01-01&primary_release_date.lte=2029-12-31&vote_count.gte=500&sort_by=vote_average.desc&page=1" to MediaType.MOVIE
            "decade_2010s" -> "discover/movie?primary_release_date.gte=2010-01-01&primary_release_date.lte=2019-12-31&vote_count.gte=800&sort_by=vote_average.desc&page=1" to MediaType.MOVIE
            "decade_2000s" -> "discover/movie?primary_release_date.gte=2000-01-01&primary_release_date.lte=2009-12-31&vote_count.gte=800&sort_by=vote_average.desc&page=1" to MediaType.MOVIE
            else -> "discover/movie?primary_release_date.gte=1990-01-01&primary_release_date.lte=1999-12-31&vote_count.gte=600&sort_by=vote_average.desc&page=1" to MediaType.MOVIE
        }
        NetworkPacer.withTmdbPacing {
            try {
                val req = buildTmdbRequest(endpoint, tmdbApiKey)
                val items = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) emptyList()
                    else {
                        val arr = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                            ?.get("results") as? JsonArray ?: return@use emptyList()
                        parseTmdbDiscoverItems(arr, forcedType)
                    }
                }
                if (items.isNotEmpty()) discoverShelfCache[cacheKey] = items
                items
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }
    }

    suspend fun fetchTasteMatchedPicks(
        seed: MediaCardSummary,
        tmdbApiKey: String? = null
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val cacheKey = "taste_${seed.id}_${seed.mediaType.name}_${seed.tmdbId}_${seed.anilistId}"
        discoverShelfCache[cacheKey]?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        val items = when {
            seed.tmdbId != null && seed.tmdbId > 0L && (seed.mediaType == MediaType.MOVIE || seed.mediaType == MediaType.SHOW) -> {
                NetworkPacer.withTmdbPacing {
                    runCatching {
                        val seg = if (seed.mediaType == MediaType.MOVIE) "movie" else "tv"
                        val req = buildTmdbRequest("$seg/${seed.tmdbId}/recommendations?language=en-US&page=1", tmdbApiKey)
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) emptyList()
                            else {
                                val arr = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                                    ?.get("results") as? JsonArray ?: return@use emptyList()
                                parseTmdbDiscoverItems(arr, seed.mediaType)
                            }
                        }
                    }.getOrDefault(emptyList())
                }
            }
            seed.anilistId != null && seed.anilistId > 0L -> {
                NetworkPacer.withAniListSearchPacing {
                    runCatching {
                        val gql = """
                            query {
                                Media(id: ${seed.anilistId}) {
                                    recommendations(sort: [RATING_DESC], perPage: 12) {
                                        nodes {
                                            mediaRecommendation {
                                                id idMal type format
                                                title { romaji english native }
                                                coverImage { extraLarge large }
                                                bannerImage averageScore
                                                startDate { year }
                                                episodes chapters
                                            }
                                        }
                                    }
                                }
                            }
                        """.trimIndent()
                        val payload = buildJsonObject { put("query", gql) }
                        val req = Request.Builder()
                            .url("https://graphql.anilist.co")
                            .header("User-Agent", "OmniTrack/1.0")
                            .post(payload.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) emptyList()
                            else {
                                val nodes = json.parseToJsonElement(resp.body?.string().orEmpty())
                                    .jsonObject["data"]?.jsonObject?.get("Media")?.jsonObject
                                    ?.get("recommendations")?.jsonObject?.get("nodes")?.jsonArray
                                    ?: return@use emptyList()
                                nodes.mapNotNull { n ->
                                    val rec = (n as? JsonObject)?.get("mediaRecommendation") as? JsonObject ?: return@mapNotNull null
                                    val titleObj = rec["title"]?.jsonObject
                                    val t = titleObj?.get("english").cleanString() ?: titleObj?.get("romaji").cleanString() ?: return@mapNotNull null
                                    val cov = rec["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                                        ?: rec["coverImage"]?.jsonObject?.get("large").cleanString()
                                        ?: return@mapNotNull null
                                    val fmt = rec["format"].cleanString()
                                    val tp = rec["type"].cleanString()
                                    val mType = when {
                                        tp == "MANGA" && fmt == "NOVEL" -> MediaType.NOVEL
                                        tp == "MANGA" -> MediaType.MANGA
                                        else -> MediaType.ANIME
                                    }
                                    SearchResultItem(
                                        title = t,
                                        nativeTitle = titleObj?.get("native").cleanString(),
                                        coverUrl = LosslessCoverMapper.toLosslessUrl(cov),
                                        bannerUrl = rec["bannerImage"].cleanString()?.let { LosslessCoverMapper.toLosslessUrl(it) },
                                        mediaType = mType,
                                        releaseYear = rec["startDate"]?.jsonObject?.get("year").cleanString(),
                                        communityScore = rec["averageScore"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.let { it / 10f },
                                        sourceProvider = "AniList",
                                        anilistId = rec["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                                        malId = rec["idMal"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                    )
                                }
                            }
                        }
                    }.getOrDefault(emptyList())
                }
            }
            else -> emptyList()
        }
        if (items.isNotEmpty()) discoverShelfCache[cacheKey] = items
        items
    }

    suspend fun fetchDailyCriticsPick(
        tmdbApiKey: String? = null,
        mediaType: MediaType = MediaType.ALL
    ): SearchResultItem? = withContext(Dispatchers.IO) {
        if (mediaType == MediaType.FANFICTION) return@withContext null
        val dayKey = java.time.LocalDate.now().toEpochDay()
        val cacheKey = "${dayKey}_${mediaType.name}"
        cachedCriticsPickByKey[cacheKey]?.let { return@withContext it }

        val rawPool: List<SearchResultItem> = when (mediaType) {
            MediaType.ALL -> {
                coroutineScope {
                    val moviesDef = async {
                        NetworkPacer.withTmdbPacing {
                            runCatching {
                                val req = buildTmdbRequest("movie/top_rated?language=en-US&page=1", tmdbApiKey)
                                client.newCall(req).execute().use { resp ->
                                    if (!resp.isSuccessful) emptyList()
                                    else {
                                        val arr = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                                            ?.get("results") as? JsonArray ?: return@use emptyList()
                                        parseTmdbDiscoverItems(arr, MediaType.MOVIE)
                                    }
                                }
                            }.getOrDefault(emptyList())
                        }
                    }
                    val showsDef = async {
                        NetworkPacer.withTmdbPacing {
                            runCatching {
                                val req = buildTmdbRequest("tv/top_rated?language=en-US&page=1", tmdbApiKey)
                                client.newCall(req).execute().use { resp ->
                                    if (!resp.isSuccessful) emptyList()
                                    else {
                                        val arr = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                                            ?.get("results") as? JsonArray ?: return@use emptyList()
                                        parseTmdbDiscoverItems(arr, MediaType.SHOW)
                                    }
                                }
                            }.getOrDefault(emptyList())
                        }
                    }
                    val movies = moviesDef.await()
                    val shows = showsDef.await()
                    buildList {
                        val maxLen = maxOf(movies.size, shows.size)
                        for (i in 0 until maxLen) {
                            movies.getOrNull(i)?.let { add(it) }
                            shows.getOrNull(i)?.let { add(it) }
                        }
                    }
                }
            }
            MediaType.MOVIE -> fetchDiscoverMoviesShelves("top_rated", tmdbApiKey)
            MediaType.SHOW -> fetchDiscoverShowsShelves("top_rated", tmdbApiKey)
            MediaType.ANIME -> fetchDiscoverAnimeShelves("top_rated")
            MediaType.MANGA -> fetchDiscoverMangaShelves("top_rated")
            MediaType.NOVEL -> fetchDiscoverNovelsShelves("top_rated_ln")
            MediaType.VISUAL_NOVEL -> fetchDiscoverVisualNovelsShelves("top_rated")
            MediaType.GAME -> fetchDiscoverGamesShelves("top_rated")
            MediaType.BOOK -> fetchDiscoverBooksShelves("classics_poetry")
            MediaType.COMIC -> fetchDiscoverComicsShelves("indie")
            MediaType.FANFICTION -> emptyList()
        }

        val withCleanBanner = rawPool.filter {
            !it.bannerUrl.isNullOrBlank() &&
                it.bannerUrl != it.coverUrl &&
                !com.omnitrack.app.data.artwork.ArtworkModelResolver.isAutoScreenshotUrl(it.bannerUrl)
        }
        val pool = withCleanBanner.ifEmpty { rawPool.filter { it.coverUrl.isNotBlank() } }
        val pick = if (pool.isNotEmpty()) {
            val offset = ((dayKey + mediaType.ordinal * 7L) % pool.size).toInt().let {
                if (it < 0) it + pool.size else it
            }
            pool[offset]
        } else null
        if (pick != null) {
            cachedCriticsPickByKey[cacheKey] = pick
        }
        pick
    }

    private fun cleanSynopsis(raw: String?): String? = Companion.cleanSynopsis(raw)
}
