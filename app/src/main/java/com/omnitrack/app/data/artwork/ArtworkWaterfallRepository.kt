package com.omnitrack.app.data.artwork

import com.omnitrack.app.data.enrichment.IgdbApiClient
import com.omnitrack.app.data.enrichment.toArtworkCandidates
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.preferences.PreferenceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ArtworkWaterfallRepository(
    private val preferenceManager: PreferenceManager? = null,
    private val client: OkHttpClient = com.omnitrack.app.data.network.NetworkClient.sharedClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain: okhttp3.Interceptor.Chain ->
            val orig = chain.request()
            val builder = orig.newBuilder()
            if (orig.header("User-Agent") == null) {
                builder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                )
            }
            if (orig.header("Accept") == null) {
                builder.header("Accept", "application/json")
            }
            chain.proceed(builder.build())
        }
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val jikanClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private data class AniListBridgeData(
        val anilistId: Long? = null,
        val malId: Long? = null,
        val countryOfOrigin: String? = null,
        val nativeTitle: String? = null,
        val romajiTitle: String? = null,
        val englishTitle: String? = null,
        val synonyms: List<String> = emptyList(),
        val candidates: List<ArtworkCandidate> = emptyList()
    )

    private fun JsonElement?.cleanString(): String? {
        if (this == null || this is JsonNull) return null
        val str = (this as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
        return str.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) && it != "N/A" }
    }

    private fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

    private fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

    suspend fun searchArtworkCandidates(
        title: String,
        mediaType: MediaType,
        anilistId: Long? = null,
        malId: Long? = null,
        mangadexId: String? = null,
        nativeTitle: String? = null,
        romajiTitle: String? = null,
        originCountry: String? = null,
        defaultCoverUrl: String? = null,
        forceRefresh: Boolean = false,
        vndbId: String? = null,
        englishTitle: String? = null,
        steamAppId: Long? = null,
        initialCandidates: List<ArtworkCandidate> = emptyList(),
        onPartialResults: ((List<ArtworkCandidate>) -> Unit)? = null
    ): List<ArtworkCandidate> = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext emptyList()
        val clean = cleanTitle(title)
        val cacheKey = "${mediaType.name}:${clean.lowercase()}:${anilistId ?: 0}:${malId ?: 0}:${mangadexId.orEmpty()}:${vndbId.orEmpty()}:${englishTitle.orEmpty()}:${steamAppId ?: 0}"

        fun cleanArtBannerRank(candidate: ArtworkCandidate): Int {
            if (!candidate.isBanner) return 0
            if (candidate.isScreenshot) return 10
            return when (candidate.sourceName.lowercase()) {
                "steamgriddb" -> 0
                "igdb artwork" -> 1
                "steam hero" -> 2
                "steam background" -> 3
                "anilist" -> 4
                "steam" -> 5
                "default" -> 6
                else -> 7
            }
        }

        fun sortForType(list: List<ArtworkCandidate>): List<ArtworkCandidate> {
            return when (mediaType) {
                MediaType.MANGA, MediaType.NOVEL -> list.sortedWith(
                    compareBy { candidate ->
                        if (candidate.isBanner) {
                            MANGA_BANNER_PRIORITY[candidate.sourceName] ?: 99
                        } else {
                            MANGA_COVER_PRIORITY[candidate.sourceName] ?: 99
                        }
                    }
                )
                MediaType.VISUAL_NOVEL, MediaType.GAME -> list.sortedWith(
                    compareBy<ArtworkCandidate> { it.isBanner }
                        .thenBy { cleanArtBannerRank(it) }
                )
                else -> list
            }
        }

        val lock = Any()
        val accumulated = LinkedHashMap<String, ArtworkCandidate>()

        fun mergeAndEmit(batch: List<ArtworkCandidate>): List<ArtworkCandidate> {
            val cleaned = batch
                .map { it.copy(url = LosslessCoverMapper.toLosslessUrl(it.url)) }
                .filter { it.url.isNotBlank() }
            if (cleaned.isEmpty()) return synchronized(lock) { sortForType(accumulated.values.toList()) }
            val snapshot = synchronized(lock) {
                for (cand in cleaned) {
                    val prev = accumulated[cand.url]
                    if (prev == null || (cand.sourceName != "Default" && prev.sourceName == "Default")) {
                        accumulated[cand.url] = cand
                    }
                }
                sortForType(accumulated.values.toList())
            }
            onPartialResults?.invoke(snapshot)
            return snapshot
        }

        if (initialCandidates.isNotEmpty()) {
            mergeAndEmit(initialCandidates)
        }

        if (!forceRefresh) {
            candidateCache[cacheKey]?.takeIf { it.isNotEmpty() }?.let { cached ->
                return@withContext mergeAndEmit(cached)
            }
        }

        if (!defaultCoverUrl.isNullOrBlank()) {
            mergeAndEmit(
                listOf(
                    ArtworkCandidate(
                        url = defaultCoverUrl,
                        sourceName = "Default",
                        description = "Source Cover",
                        isBanner = false
                    )
                )
            )
        }

        val sgdbKey = preferenceManager?.steamGridDbApiKey?.firstOrNull()?.ifBlank { null }
        val igdbId = preferenceManager?.igdbClientId?.firstOrNull()?.ifBlank { null }
        val igdbSecret = preferenceManager?.igdbClientSecret?.firstOrNull()?.ifBlank { null }

        fun CoroutineScope.launchProvider(
            timeoutMs: Long = 4500L,
            block: suspend () -> List<ArtworkCandidate>
        ) = launch {
            val batch = withTimeoutOrNull(timeoutMs) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            }.orEmpty()
            if (batch.isNotEmpty()) {
                mergeAndEmit(batch)
            }
        }

        coroutineScope {
            when (mediaType) {
                MediaType.MANGA, MediaType.NOVEL -> {
                    val bridge = withTimeoutOrNull(3500L) {
                        fetchAniListRosettaBridge(
                            rawTitle = clean,
                            isAnime = false,
                            anilistId = anilistId,
                            malId = malId
                        )
                    } ?: AniListBridgeData()
                    if (bridge.candidates.isNotEmpty()) {
                        mergeAndEmit(bridge.candidates)
                    }

                    val effAnilistId = anilistId ?: bridge.anilistId
                    val effMalId = malId ?: bridge.malId
                    val wdNative = if (mediaType == MediaType.NOVEL && bridge.nativeTitle == null && nativeTitle.isNullOrBlank()) {
                        withTimeoutOrNull(2000L) { fetchWikidataNativeTitlesForArt(clean) }
                    } else null
                    val effOrigin = (originCountry ?: bridge.countryOfOrigin)?.uppercase()
                    val effNative = bridge.nativeTitle ?: nativeTitle?.takeIf { it.isNotBlank() } ?: wdNative?.firstOrNull()
                    val effRomaji = bridge.romajiTitle ?: romajiTitle?.takeIf { it.isNotBlank() }
                    val effSynonyms = (bridge.synonyms + wdNative.orEmpty()).distinct()

                    val koreanQuery = listOfNotNull(
                        effNative?.takeIf { containsHangul(it) },
                        effSynonyms.firstOrNull { containsHangul(it) },
                        clean.takeIf { containsHangul(it) }
                    ).firstOrNull() ?: if (effOrigin != "JP" && !containsJapanese(clean)) clean else null

                    val isChinese = effOrigin == "CN" ||
                        effOrigin == "TW" ||
                        containsHanzi(clean) ||
                        (effNative != null && containsHanzi(effNative)) ||
                        effSynonyms.any { containsHanzi(it) }
                    val chineseQuery = listOfNotNull(
                        effSynonyms.firstOrNull { containsHanzi(it) && !containsJapanese(it) },
                        effNative?.takeIf { containsHanzi(it) },
                        effSynonyms.firstOrNull { containsHanzi(it) },
                        clean.takeIf { containsHanzi(it) }
                    ).firstOrNull() ?: if (isChinese) clean else null

                    val japaneseQuery = listOfNotNull(
                        effNative?.takeIf { containsJapanese(it) },
                        effSynonyms.firstOrNull { containsJapanese(it) },
                        clean.takeIf { containsJapanese(it) },
                        effRomaji?.takeIf { !containsHangul(it) },
                        clean.takeIf { !containsHangul(it) }
                    ).firstOrNull()

                    val altMangaDexTitle = listOfNotNull(
                        effRomaji,
                        effNative,
                        romajiTitle,
                        nativeTitle
                    ).firstOrNull { it.isNotBlank() && !it.equals(clean, ignoreCase = true) }

                    if (mediaType == MediaType.NOVEL) {
                        launchProvider { fetchRanobeDbCovers(clean, effNative) }
                        if (bridge.candidates.none { it.isBanner }) {
                            launchProvider { fetchAniListAdaptationBanners(clean, effNative ?: wdNative?.firstOrNull()) }
                        }
                    }
                    launchProvider {
                        fetchMangaDexCovers(
                            rawTitle = clean,
                            altTitle = altMangaDexTitle,
                            anilistId = effAnilistId,
                            malId = effMalId,
                            mangadexId = mangadexId
                        )
                    }
                    if (japaneseQuery != null && !containsHangul(japaneseQuery) && effOrigin != "KR" && effOrigin != "CN") {
                        launchProvider { fetchBookWalkerCovers(japaneseQuery) }
                    }
                    launchProvider { fetchBangumiCovers(japaneseQuery ?: chineseQuery ?: koreanQuery ?: clean, type = 1) }
                    if (koreanQuery != null) {
                        val globalQuery = listOfNotNull(
                            bridge.englishTitle,
                            effRomaji,
                            clean
                        ).firstOrNull { !containsHangul(it) }
                        launchProvider { fetchNaverWebtoonCovers(koreanQuery, globalQuery) }
                    }
                    if (chineseQuery != null) {
                        launchProvider { fetchKuaikanCovers(chineseQuery) }
                    }
                    launchProvider { fetchKitsuCovers(clean, isAnime = false) }
                    launchProvider { fetchComickCovers(clean) }
                    if (effMalId != null && effMalId > 0) {
                        launchProvider { fetchJikanMangaPictures(effMalId) }
                    }
                }

                MediaType.ANIME -> {
                    val bridge = withTimeoutOrNull(3500L) {
                        fetchAniListRosettaBridge(
                            rawTitle = clean,
                            isAnime = true,
                            anilistId = anilistId,
                            malId = malId
                        )
                    } ?: AniListBridgeData()
                    if (bridge.candidates.isNotEmpty()) {
                        mergeAndEmit(bridge.candidates)
                    }
                    launchProvider { fetchBangumiCovers(bridge.nativeTitle ?: bridge.romajiTitle ?: clean, type = 2) }
                    launchProvider { fetchKitsuCovers(clean, isAnime = true) }
                    launchProvider {
                        fetchMangaDexCovers(
                            rawTitle = clean,
                            altTitle = bridge.romajiTitle ?: romajiTitle,
                            anilistId = anilistId ?: bridge.anilistId,
                            malId = malId ?: bridge.malId,
                            mangadexId = mangadexId
                        )
                    }
                    launchProvider { fetchTmdbArtwork(clean, isMovie = false) }
                }

                MediaType.BOOK -> {
                    launchProvider { fetchGoogleBooksCovers(title) }
                    launchProvider { fetchOpenLibraryCovers(title) }
                }

                MediaType.SHOW, MediaType.MOVIE -> {
                    launchProvider { fetchTvMazeCovers(title) }
                    launchProvider { fetchBangumiCovers(title, type = 2) }
                    launchProvider { fetchTmdbArtwork(title, isMovie = (mediaType == MediaType.MOVIE)) }
                }

                MediaType.GAME -> {
                    launchProvider { fetchSteamCovers(title, steamAppId) }
                    launchProvider { fetchBangumiCovers(nativeTitle ?: romajiTitle ?: title, type = 4) }
                    if (!sgdbKey.isNullOrBlank()) {
                        launchProvider { fetchSteamGridDbArtwork(title, steamAppId, sgdbKey) }
                    }
                    if (!igdbId.isNullOrBlank() && !igdbSecret.isNullOrBlank()) {
                        launchProvider {
                            IgdbApiClient(client).fetchGameArtwork(igdbId, igdbSecret, title, null)
                                ?.toArtworkCandidates().orEmpty()
                        }
                    }
                }

                MediaType.VISUAL_NOVEL -> {
                    val vndbBundle = withTimeoutOrNull(4500L) {
                        fetchVndbArtwork(clean, vndbId, englishTitle) { partial ->
                            mergeAndEmit(partial)
                        }
                    }
                    val bestEnTitle = englishTitle?.takeIf { it.isNotBlank() }
                        ?: vndbBundle?.englishTitle?.takeIf { it.isNotBlank() }
                        ?: clean
                    val bestNative = nativeTitle?.takeIf { it.isNotBlank() } ?: vndbBundle?.nativeTitle

                    launchProvider { fetchSteamCovers(bestEnTitle) }
                    launchProvider { fetchAniListAdaptationBanners(bestEnTitle, bestNative) }
                    launchProvider { fetchBangumiCovers(bestNative ?: romajiTitle ?: clean, type = 4) }
                    if (!sgdbKey.isNullOrBlank()) {
                        launchProvider { fetchSteamGridDbArtwork(bestEnTitle, vndbBundle?.steamAppId, sgdbKey) }
                    }
                    if (!igdbId.isNullOrBlank() && !igdbSecret.isNullOrBlank()) {
                        launchProvider {
                            IgdbApiClient(client).fetchGameArtwork(igdbId, igdbSecret, bestEnTitle, vndbBundle?.igdbSlug)
                                ?.toArtworkCandidates().orEmpty()
                        }
                    }
                }

                MediaType.ALL -> {
                    launchProvider { fetchAniListCovers(title, isAnime = true) }
                    launchProvider { fetchTvMazeCovers(title) }
                    launchProvider { fetchSteamCovers(title) }
                    launchProvider { fetchBangumiCovers(title, type = 1) }
                    launchProvider { fetchTmdbArtwork(title, isMovie = true) }
                }
            }
        }

        val normalized = synchronized(lock) { sortForType(accumulated.values.toList()) }
        if (normalized.isNotEmpty()) {
            candidateCache[cacheKey] = normalized
        }
        normalized
    }

    private fun fetchAniListRosettaBridge(
        rawTitle: String,
        isAnime: Boolean,
        anilistId: Long? = null,
        malId: Long? = null
    ): AniListBridgeData {
        val typeStr = if (isAnime) "ANIME" else "MANGA"
        var resolvedAnilistId: Long? = anilistId?.takeIf { it > 0 }
        var resolvedMalId: Long? = malId?.takeIf { it > 0 }
        var countryOfOrigin: String? = null
        var nativeTitle: String? = null
        var romajiTitle: String? = null
        var englishTitle: String? = null
        var synonyms: List<String> = emptyList()
        val candidates = mutableListOf<ArtworkCandidate>()

        fun extractMetadata(obj: JsonObject, primary: Boolean) {
            val idVal = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            val idMalVal = obj["idMal"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            val country = obj["countryOfOrigin"].cleanString()
            val titleObj = obj["title"] as? JsonObject
            val eng = titleObj?.get("english").cleanString()
            val rom = titleObj?.get("romaji").cleanString()
            val nat = titleObj?.get("native").cleanString()
            val synList = (obj["synonyms"] as? JsonArray)?.mapNotNull { it.cleanString() } ?: emptyList()

            if (primary) {
                resolvedAnilistId = resolvedAnilistId ?: idVal
                resolvedMalId = resolvedMalId ?: idMalVal
                countryOfOrigin = countryOfOrigin ?: country
                nativeTitle = nativeTitle ?: nat
                romajiTitle = romajiTitle ?: rom
                englishTitle = englishTitle ?: eng
                if (synonyms.isEmpty()) synonyms = synList
            }

            val displayTitle = eng ?: rom ?: nat ?: rawTitle
            val coverObj = obj["coverImage"] as? JsonObject
            val coverUrl = coverObj?.get("extraLarge").cleanString() ?: coverObj?.get("large").cleanString()
            if (!coverUrl.isNullOrBlank()) {
                candidates.add(
                    ArtworkCandidate(
                        url = coverUrl,
                        sourceName = "AniList",
                        description = if (primary) "HD Poster" else displayTitle,
                        isBanner = false
                    )
                )
            }
            val bannerUrl = obj["bannerImage"].cleanString()
            if (!bannerUrl.isNullOrBlank()) {
                candidates.add(
                    ArtworkCandidate(
                        url = bannerUrl,
                        sourceName = "AniList",
                        description = "$displayTitle (Banner)",
                        isBanner = true
                    )
                )
            }
        }

        // 1. Direct lookup by AniList ID or MAL ID
        try {
            if (resolvedAnilistId != null || resolvedMalId != null) {
                val directGql = if (resolvedAnilistId != null) {
                    """
                    query (${'$'}id: Int) {
                        Media(id: ${'$'}id, type: $typeStr) {
                            id
                            idMal
                            countryOfOrigin
                            title { romaji english native }
                            synonyms
                            coverImage { extraLarge large }
                            bannerImage
                        }
                    }
                    """.trimIndent()
                } else {
                    """
                    query (${'$'}idMal: Int) {
                        Media(idMal: ${'$'}idMal, type: $typeStr) {
                            id
                            idMal
                            countryOfOrigin
                            title { romaji english native }
                            synonyms
                            coverImage { extraLarge large }
                            bannerImage
                        }
                    }
                    """.trimIndent()
                }
                val directPayload = buildJsonObject {
                    put("query", directGql)
                    putJsonObject("variables") {
                        if (resolvedAnilistId != null) put("id", resolvedAnilistId!!)
                        else put("idMal", resolvedMalId!!)
                    }
                }
                val directReq = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .post(directPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(directReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val mediaObj = ((json.parseToJsonElement(body) as? JsonObject)
                            ?.get("data") as? JsonObject)
                            ?.get("Media") as? JsonObject
                        if (mediaObj != null) {
                            extractMetadata(mediaObj, primary = true)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }

        // 2. GraphQL search to gather all candidate variants and fill Rosetta bridge if direct ID was absent
        try {
            val searchGql = """
                query (${'$'}search: String, ${'$'}type: MediaType) {
                    Page(page: 1, perPage: 8) {
                        media(search: ${'$'}search, type: ${'$'}type) {
                            id
                            idMal
                            countryOfOrigin
                            title { romaji english native }
                            synonyms
                            coverImage { extraLarge large }
                            bannerImage
                        }
                    }
                }
            """.trimIndent()
            val searchPayload = buildJsonObject {
                put("query", searchGql)
                putJsonObject("variables") {
                    put("search", rawTitle)
                    put("type", typeStr)
                }
            }
            val searchReq = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(searchPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(searchReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val mediaList = (((json.parseToJsonElement(body) as? JsonObject)
                        ?.get("data") as? JsonObject)
                        ?.get("Page") as? JsonObject)
                        ?.get("media") as? JsonArray

                    if (!mediaList.isNullOrEmpty()) {
                        val needPrimaryMeta = nativeTitle == null && romajiTitle == null && countryOfOrigin == null
                        val bestMatchIdx = if (needPrimaryMeta) {
                            val idx = mediaList.indexOfFirst { elem ->
                                val obj = elem as? JsonObject ?: return@indexOfFirst false
                                val tObj = obj["title"] as? JsonObject
                                val syns = (obj["synonyms"] as? JsonArray)?.mapNotNull { it.cleanString() } ?: emptyList()
                                val titles = listOfNotNull(
                                    tObj?.get("english").cleanString(),
                                    tObj?.get("romaji").cleanString(),
                                    tObj?.get("native").cleanString()
                                ) + syns
                                isTitleMatch(rawTitle, titles)
                            }
                            if (idx >= 0) idx else 0
                        } else -1

                        mediaList.forEachIndexed { idx, elem ->
                            val obj = elem as? JsonObject ?: return@forEachIndexed
                            extractMetadata(obj, primary = (idx == bestMatchIdx))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }

        return AniListBridgeData(
            anilistId = resolvedAnilistId,
            malId = resolvedMalId,
            countryOfOrigin = countryOfOrigin,
            nativeTitle = nativeTitle,
            romajiTitle = romajiTitle,
            englishTitle = englishTitle,
            synonyms = synonyms,
            candidates = candidates
        )
    }

    private fun fetchAniListCovers(title: String, isAnime: Boolean): List<ArtworkCandidate> {
        return fetchAniListRosettaBridge(rawTitle = cleanTitle(title), isAnime = isAnime).candidates
    }

    private fun fetchMangaDexCoversByMangaId(mangaId: String): List<ArtworkCandidate> {
        return try {
            val request = Request.Builder()
                .url("https://api.mangadex.org/cover?manga[]=$mangaId&limit=100&order[volume]=desc")
                .header("User-Agent", "OmniTrack/1.0 (Android; https://github.com/ExZyO/OmniTrack)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
                val data = root["data"] as? JsonArray ?: return emptyList()

                data.mapNotNull { elem ->
                    val attrs = (elem as? JsonObject)?.get("attributes") as? JsonObject ?: return@mapNotNull null
                    val fileName = attrs["fileName"].cleanString() ?: return@mapNotNull null
                    val volume = attrs["volume"].cleanString()
                    val locale = attrs["locale"].cleanString()
                    val volPart = volume?.takeIf { !it.equals("none", ignoreCase = true) }?.let { "Vol. $it" }
                    val locPart = locale?.uppercase()
                    val subtitle = listOfNotNull(volPart, locPart).joinToString(" ").ifBlank { "Official Cover" }
                    ArtworkCandidate(
                        url = "https://uploads.mangadex.org/covers/$mangaId/$fileName",
                        sourceName = "MangaDex",
                        description = subtitle,
                        isBanner = false
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private fun fetchMangaDexCovers(
        rawTitle: String,
        altTitle: String? = null,
        anilistId: Long? = null,
        malId: Long? = null,
        mangadexId: String? = null
    ): List<ArtworkCandidate> {
        // 1. Direct UUID lookup if known
        if (!mangadexId.isNullOrBlank()) {
            val direct = fetchMangaDexCoversByMangaId(mangadexId)
            if (direct.isNotEmpty()) return direct
        }

        fun searchAndFetchVolumes(query: String): List<ArtworkCandidate> {
            val clean = cleanTitle(query)
            if (clean.isBlank()) return emptyList()

            fun executeSearch(q: String): JsonArray? {
                val encoded = URLEncoder.encode(q, "UTF-8")
                val url = "https://api.mangadex.org/manga?title=$encoded&limit=15&includes[]=cover_art" +
                    "&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica&contentRating[]=pornographic&order[relevance]=desc"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "OmniTrack/1.0 (Android; https://github.com/ExZyO/OmniTrack)")
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val body = resp.body?.string() ?: return null
                    val root = json.parseToJsonElement(body) as? JsonObject ?: return null
                    return root["data"] as? JsonArray
                }
            }

            var dataArr = executeSearch(clean)
            if (dataArr.isNullOrEmpty()) {
                val simplified = clean.replace(Regex("[^a-zA-Z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
                if (simplified != clean && simplified.length >= 2) {
                    dataArr = executeSearch(simplified)
                }
            }
            if (dataArr.isNullOrEmpty()) return emptyList()

            data class ScoredCandidate(
                val id: String,
                val score: Int,
                val embeddedCoverUrl: String?
            )

            val qNorm = normalizeForComparison(clean)
            val qTokens = qNorm.split(" ").filter { it.isNotBlank() }.toSet()
            val queryHasDoujin = clean.contains("doujin", ignoreCase = true)
            val scoredCandidates = mutableListOf<ScoredCandidate>()

            for (item in dataArr) {
                val obj = item as? JsonObject ?: continue
                val id = obj["id"].cleanString() ?: continue
                val attrs = obj["attributes"] as? JsonObject ?: continue
                val links = attrs["links"] as? JsonObject

                var score = 0
                val alLink = links?.get("al").cleanString()
                val malLink = links?.get("mal").cleanString()
                if (anilistId != null && alLink == anilistId.toString()) score += 1000
                if (malId != null && malLink == malId.toString()) score += 1000

                val titleMap = attrs["title"] as? JsonObject
                val mainTitles = titleMap?.values?.mapNotNull { it.cleanString() } ?: emptyList()
                val altTitlesList = (attrs["altTitles"] as? JsonArray)?.flatMap { altObj ->
                    (altObj as? JsonObject)?.values?.mapNotNull { it.cleanString() } ?: emptyList()
                } ?: emptyList()
                val allCandidateTitles = mainTitles + altTitlesList

                var bestTitleScore = 0
                for (cTitle in allCandidateTitles) {
                    val cNorm = normalizeForComparison(cTitle)
                    if (cNorm.isBlank()) continue
                    if (cNorm == qNorm) {
                        bestTitleScore = maxOf(bestTitleScore, 500)
                    } else if (cNorm.startsWith(qNorm) || qNorm.startsWith(cNorm)) {
                        bestTitleScore = maxOf(bestTitleScore, 300)
                    } else if (cNorm.contains(qNorm) || qNorm.contains(cNorm)) {
                        bestTitleScore = maxOf(bestTitleScore, 250)
                    } else if (qTokens.isNotEmpty()) {
                        val cTokens = cNorm.split(" ").filter { it.isNotBlank() }.toSet()
                        if (cTokens.isNotEmpty()) {
                            val intersection = qTokens.intersect(cTokens).size
                            val ratio = intersection.toFloat() / qTokens.size
                            if (ratio >= 0.7f) {
                                bestTitleScore = maxOf(bestTitleScore, (ratio * 200).toInt())
                            }
                        }
                    }
                }
                score += bestTitleScore

                val tags = (attrs["tags"] as? JsonArray)?.mapNotNull { tagElem ->
                    ((tagElem as? JsonObject)?.get("attributes") as? JsonObject)
                        ?.get("name")?.jsonObject?.get("en").cleanString()?.lowercase()
                } ?: emptyList()
                val isDoujinshi = "doujinshi" in tags || allCandidateTitles.any { it.contains("doujinshi", ignoreCase = true) }
                if (isDoujinshi && !queryHasDoujin) {
                    score = maxOf(1, score - 100)
                }

                val isFanOrColored = allCandidateTitles.any {
                    it.contains("fan colored", ignoreCase = true) ||
                        it.contains("official colored", ignoreCase = true) ||
                        it.contains("side story", ignoreCase = true)
                }
                if (isFanOrColored) {
                    score = maxOf(1, score - 50)
                }

                val relationships = obj["relationships"] as? JsonArray
                val coverArtRel = relationships?.firstOrNull {
                    (it as? JsonObject)?.get("type").cleanString() == "cover_art"
                } as? JsonObject
                val fileName = (coverArtRel?.get("attributes") as? JsonObject)?.get("fileName").cleanString()
                val embeddedCoverUrl = fileName?.let { "https://uploads.mangadex.org/covers/$id/$it" }

                if (score >= 1000 || (score > 0 && bestTitleScore > 0)) {
                    scoredCandidates.add(ScoredCandidate(id, score, embeddedCoverUrl))
                }
            }

            if (scoredCandidates.isEmpty()) return emptyList()
            scoredCandidates.sortByDescending { it.score }

            var fallbackCover: List<ArtworkCandidate> = emptyList()
            for (cand in scoredCandidates.take(3)) {
                val volumeCovers = fetchMangaDexCoversByMangaId(cand.id)
                if (volumeCovers.isNotEmpty()) return volumeCovers
                if (cand.embeddedCoverUrl != null && fallbackCover.isEmpty()) {
                    fallbackCover = listOf(
                        ArtworkCandidate(
                            url = cand.embeddedCoverUrl,
                            sourceName = "MangaDex",
                            description = "Official Cover",
                            isBanner = false
                        )
                    )
                }
            }
            return fallbackCover
        }

        return try {
            val primary = searchAndFetchVolumes(rawTitle)
            if (primary.isNotEmpty()) return primary
            if (!altTitle.isNullOrBlank() && !altTitle.equals(rawTitle, ignoreCase = true)) {
                return searchAndFetchVolumes(altTitle)
            }
            emptyList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private fun fetchNaverWebtoonCovers(query: String, globalQuery: String? = null): List<ArtworkCandidate> {
        val results = mutableListOf<ArtworkCandidate>()
        val uaDesktop = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val uaMobile = "Mozilla/5.0 (Linux; Android 14; Galaxy S24 Ultra) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun queryNaverComic(q: String): List<ArtworkCandidate> {
            return try {
                val encoded = URLEncoder.encode(q.trim(), "UTF-8")
                val request = Request.Builder()
                    .url("https://comic.naver.com/api/search/all?keyword=$encoded")
                    .header("Referer", "https://comic.naver.com/webtoon")
                    .header("User-Agent", uaDesktop)
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return emptyList()
                    val body = resp.body?.string() ?: return emptyList()
                    val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
                    val webtoonList = (root["searchWebtoonResult"] as? JsonObject)?.get("searchViewList") as? JsonArray
                        ?: return emptyList()

                    val qClean = q.replace(" ", "").lowercase()
                    val matchedObj = webtoonList.firstNotNullOfOrNull { elem ->
                        val obj = elem as? JsonObject ?: return@firstNotNullOfOrNull null
                        val name = obj["titleName"].cleanString().orEmpty().replace(" ", "").lowercase()
                        if (name == qClean || (qClean.length >= 4 && (name.startsWith(qClean) || qClean.startsWith(name)))) obj else null
                    } ?: if (containsHangul(q)) {
                        webtoonList.firstNotNullOfOrNull { elem ->
                            val obj = elem as? JsonObject ?: return@firstNotNullOfOrNull null
                            val name = obj["titleName"].cleanString().orEmpty()
                            if (name.contains(q.trim())) obj else null
                        }
                    } else null

                    if (matchedObj == null) return emptyList()

                    val titleId = matchedObj["titleId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    val thumbnailUrl = matchedObj["thumbnailUrl"].cleanString()?.replace("http://", "https://")
                    val matchedTitle = matchedObj["titleName"].cleanString() ?: q
                    val out = mutableListOf<ArtworkCandidate>()
                    if (!thumbnailUrl.isNullOrBlank()) {
                        out.add(
                            ArtworkCandidate(
                                url = thumbnailUrl,
                                sourceName = "Naver",
                                description = "Official Korean Art",
                                isBanner = false
                            )
                        )
                    }
                    if (titleId != null) {
                        val headerReq = Request.Builder()
                            .url("https://m.comic.naver.com/webtoon/list?titleId=$titleId")
                            .header("User-Agent", uaMobile)
                            .header("Referer", "https://m.comic.naver.com/")
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .get()
                            .build()
                        client.newCall(headerReq).execute().use { hResp ->
                            if (hResp.isSuccessful) {
                                val html = hResp.body?.string().orEmpty()
                                val banner = (NAVER_BACK_IMAGE_REGEX.find(html)?.groupValues?.get(1)
                                    ?: OG_IMAGE_REGEX.find(html)?.groupValues?.get(1))
                                    ?.replace("http://", "https://")
                                if (!banner.isNullOrBlank()) {
                                    out.add(
                                        ArtworkCandidate(
                                            url = banner,
                                            sourceName = "Naver",
                                            description = "$matchedTitle (Banner)",
                                            isBanner = true
                                        )
                                    )
                                }
                            }
                        }
                    }
                    out
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

        fun queryWebtoonsGlobal(q: String): List<ArtworkCandidate> {
            return try {
                val encoded = URLEncoder.encode(q.trim(), "UTF-8")
                val searchReq = Request.Builder()
                    .url("https://www.webtoons.com/en/search?keyword=$encoded")
                    .header("User-Agent", uaDesktop)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .get()
                    .build()
                val html = client.newCall(searchReq).execute().use { resp ->
                    if (!resp.isSuccessful) return emptyList()
                    resp.body?.string().orEmpty()
                }
                val cardMatches = WEBTOONS_CARD_LINK_REGEX.findAll(html).toList()
                if (cardMatches.isEmpty()) return emptyList()

                val qClean = q.replace(" ", "").lowercase()
                val queryWords = q.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
                val seriesUrl = cardMatches.map { it.groupValues[1] }.firstOrNull { u ->
                    val slug = u.substringAfter("/en/").substringAfter("/").substringBefore("/list")
                    val cleanSlug = slug.replace("-", "").lowercase()
                    cleanSlug == qClean ||
                        (queryWords.size >= 2 && queryWords.count { slug.contains(it) } >= (queryWords.size * 0.7).toInt().coerceAtLeast(2)) ||
                        (queryWords.size == 1 && cleanSlug == queryWords[0])
                } ?: return emptyList()

                val seriesReq = Request.Builder()
                    .url(seriesUrl)
                    .header("User-Agent", uaDesktop)
                    .header("Referer", "https://www.webtoons.com/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .get()
                    .build()
                val seriesHtml = client.newCall(seriesReq).execute().use { resp ->
                    if (!resp.isSuccessful) return emptyList()
                    resp.body?.string().orEmpty()
                }
                val out = mutableListOf<ArtworkCandidate>()
                val bannerUrl = WEBTOONS_BG_REGEX.find(seriesHtml)?.groupValues?.get(1)?.replace("http://", "https://")
                val coverUrl = OG_IMAGE_REGEX.find(seriesHtml)?.groupValues?.get(1)
                    ?.replace(Regex("\\?type=crop\\d+_\\d+"), "?type=q90")
                    ?.replace("http://", "https://")
                if (!coverUrl.isNullOrBlank()) {
                    out.add(ArtworkCandidate(url = coverUrl, sourceName = "Naver", description = "Webtoons Global Art", isBanner = false))
                }
                if (!bannerUrl.isNullOrBlank()) {
                    out.add(ArtworkCandidate(url = bannerUrl, sourceName = "Naver", description = "$q (Webtoons Banner)", isBanner = true))
                }
                out
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

        if (containsHangul(query)) {
            results.addAll(queryNaverComic(query))
            val fallbackQ = globalQuery?.takeIf { it.isNotBlank() && !containsHangul(it) }
            if (fallbackQ != null) {
                results.addAll(queryWebtoonsGlobal(fallbackQ))
            } else if (results.isEmpty()) {
                results.addAll(queryWebtoonsGlobal(query))
            }
        } else {
            results.addAll(queryWebtoonsGlobal(query))
            if (results.isEmpty()) {
                results.addAll(queryNaverComic(query))
            }
        }
        return results
    }

    private fun fetchKuaikanCovers(query: String): List<ArtworkCandidate> {
        return try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val request = Request.Builder()
                .url("https://api.kuaikanmanhua.com/v1/search/topic?q=$encoded&size=5")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
                val hits = (root["data"] as? JsonObject)?.get("hit") as? JsonArray ?: return emptyList()

                val qClean = query.replace(" ", "").lowercase()
                val matchedObj = hits.firstNotNullOfOrNull { elem ->
                    val obj = elem as? JsonObject ?: return@firstNotNullOfOrNull null
                    val rawTitle = obj["title"].cleanString().orEmpty()
                    val name = rawTitle.replace(" ", "").lowercase()
                    if (name == qClean || name.contains(qClean) || qClean.contains(name) || isTitleMatch(query, listOf(rawTitle))) {
                        obj
                    } else null
                } ?: return emptyList()

                val matchedTitle = matchedObj["title"].cleanString() ?: query
                val verticalCover = matchedObj["vertical_image_url"].cleanString()
                    ?.replace(Regex("-w\\d+(\\.w)?$"), "")
                val wideBanner = matchedObj["cover_image_url"].cleanString()
                    ?.replace(Regex("-w\\d+(\\.w)?$"), "")

                val out = mutableListOf<ArtworkCandidate>()
                if (!verticalCover.isNullOrBlank()) {
                    out.add(ArtworkCandidate(url = verticalCover, sourceName = "Kuaikan", description = "Official Chinese Art", isBanner = false))
                }
                if (!wideBanner.isNullOrBlank()) {
                    out.add(ArtworkCandidate(url = wideBanner, sourceName = "Kuaikan", description = "$matchedTitle (Banner)", isBanner = true))
                }
                out
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private fun fetchBookWalkerCovers(query: String): List<ArtworkCandidate> {
        if (query.isBlank() || containsHangul(query)) return emptyList()
        return try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val request = Request.Builder()
                .url("https://bookwalker.jp/search/?word=$encoded")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ja,en-US;q=0.7,en;q=0.3")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val html = response.body?.string().orEmpty()
                if (html.isBlank()) return emptyList()

                val thumbUrls = BOOKWALKER_THUMB_ID_REGEX.findAll(html)
                    .map { "https://c.bookwalker.jp/thumbnailImage_${it.groupValues[1]}.jpg" }
                    .distinct()
                    .take(6)
                    .toList()

                val finalUrls = if (thumbUrls.isNotEmpty()) {
                    thumbUrls
                } else {
                    BOOKWALKER_DATA_ORIGINAL_REGEX.findAll(html)
                        .map { it.groupValues[1] }
                        .distinct()
                        .take(6)
                        .toList()
                }

                finalUrls.mapIndexed { idx, coverUrl ->
                    ArtworkCandidate(
                        url = coverUrl,
                        sourceName = "BookWalker",
                        description = if (idx == 0) "Official Tankōbon Art" else "Tankōbon Edition ${idx + 1}",
                        isBanner = false
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private fun fetchBangumiCovers(title: String, type: Int): List<ArtworkCandidate> {
        return try {
            val payload = buildJsonObject {
                put("keyword", title.trim())
                putJsonObject("filter") {
                    putJsonArray("type") {
                        add(type)
                    }
                }
            }

            val request = Request.Builder()
                .url("https://api.bgm.tv/v0/search/subjects")
                .header("User-Agent", "OmniTrack/1.0.0 (Android; https://github.com/ExZyO/OmniTrack)")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val parsed = json.parseToJsonElement(responseStr) as? JsonObject ?: return emptyList()
                val data = parsed["data"] as? JsonArray ?: return emptyList()

                val matchedSubjects = data.mapNotNull { elem ->
                    val obj = elem as? JsonObject ?: return@mapNotNull null
                    val name = obj["name"].cleanString()
                    val nameCn = obj["name_cn"].cleanString()
                    val candidateTitles = listOfNotNull(name, nameCn)
                    if (isTitleMatch(title, candidateTitles)) obj else null
                }
                val subjectsToUse = matchedSubjects.ifEmpty { data.take(2).mapNotNull { it as? JsonObject } }

                val candidates = mutableListOf<ArtworkCandidate>()
                subjectsToUse.take(4).forEach { item ->
                    val images = item["images"] as? JsonObject
                    val rawUrl = images?.get("large").cleanString()
                        ?: images?.get("common").cleanString()
                        ?: item["image"].cleanString()
                    val url = rawUrl?.let { if (it.startsWith("http://")) it.replaceFirst("http://", "https://") else it }
                    val name = item["name_cn"].cleanString() ?: item["name"].cleanString() ?: title

                    if (!url.isNullOrBlank()) {
                        candidates.add(
                            ArtworkCandidate(
                                url = url,
                                sourceName = "Bangumi",
                                description = if (matchedSubjects.isNotEmpty()) "Official Series Art" else name,
                                isBanner = false
                            )
                        )
                    }
                }

                // Fetch related anime adaptation key visual as wide banner candidate for manga
                if (type == 1) {
                    val primarySubjectId = (matchedSubjects.firstOrNull() ?: subjectsToUse.firstOrNull())
                        ?.get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    if (primarySubjectId != null) {
                        try {
                            val relReq = Request.Builder()
                                .url("https://api.bgm.tv/v0/subjects/$primarySubjectId/subjects")
                                .header("User-Agent", "OmniTrack/1.0.0 (Android; https://github.com/ExZyO/OmniTrack)")
                                .get()
                                .build()
                            client.newCall(relReq).execute().use { relResp ->
                                if (relResp.isSuccessful) {
                                    val relBody = relResp.body?.string().orEmpty()
                                    val relArr = json.parseToJsonElement(relBody) as? JsonArray
                                    val animeSubject = relArr?.firstNotNullOfOrNull { elem ->
                                        val obj = elem as? JsonObject
                                        if (obj?.get("type").cleanString() == "2") obj else null
                                    }
                                    val animeImages = animeSubject?.get("images") as? JsonObject
                                    val rawAnimeImg = animeImages?.get("large").cleanString()
                                        ?: animeImages?.get("common").cleanString()
                                    val animeImg = rawAnimeImg?.let { if (it.startsWith("http://")) it.replaceFirst("http://", "https://") else it }
                                    val animeName = animeSubject?.get("name_cn").cleanString()
                                        ?: animeSubject?.get("name").cleanString()
                                        ?: title
                                    if (!animeImg.isNullOrBlank()) {
                                        candidates.add(
                                            ArtworkCandidate(
                                                url = animeImg,
                                                sourceName = "Bangumi",
                                                description = "$animeName (Anime Visual Banner)",
                                                isBanner = true
                                            )
                                        )
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                candidates
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private fun fetchKitsuCovers(title: String, isAnime: Boolean): List<ArtworkCandidate> {
        return try {
            val endpoint = if (isAnime) "anime" else "manga"
            val encoded = URLEncoder.encode(title, "UTF-8")
            val request = Request.Builder()
                .url("https://kitsu.io/api/edge/$endpoint?filter[text]=$encoded&page[limit]=5")
                .header("Accept", "application/vnd.api+json")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val parsed = json.parseToJsonElement(responseStr) as? JsonObject ?: return emptyList()
                val data = parsed["data"] as? JsonArray ?: return emptyList()

                val candidates = mutableListOf<ArtworkCandidate>()
                data.forEach { elem ->
                    val attrs = (elem as? JsonObject)?.get("attributes") as? JsonObject ?: return@forEach
                    val posterImage = attrs["posterImage"] as? JsonObject
                    val url = posterImage?.get("original").cleanString()
                        ?: posterImage?.get("large").cleanString()
                    val canonicalTitle = attrs["canonicalTitle"].cleanString() ?: title
                    if (!url.isNullOrBlank()) {
                        candidates.add(
                            ArtworkCandidate(
                                url = url,
                                sourceName = "Kitsu",
                                description = canonicalTitle,
                                isBanner = false
                            )
                        )
                    }
                    val coverImageObj = attrs["coverImage"] as? JsonObject
                    val coverImage = coverImageObj?.get("original").cleanString()
                        ?: coverImageObj?.get("large").cleanString()
                    if (!coverImage.isNullOrBlank()) {
                        candidates.add(
                            ArtworkCandidate(
                                url = coverImage,
                                sourceName = "Kitsu",
                                description = "$canonicalTitle (Banner)",
                                isBanner = true
                            )
                        )
                    }
                }
                candidates
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private fun fetchJikanMangaPictures(malId: Long): List<ArtworkCandidate> {
        if (malId <= 0) return emptyList()
        return try {
            val request = Request.Builder()
                .url("https://jikanfortheweebs.midnightignite.me/v4/manga/$malId/pictures")
                .header("Accept", "application/json")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()

            jikanClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
                val data = root["data"] as? JsonArray ?: return emptyList()

                data.mapIndexedNotNull { idx, elem ->
                    val obj = elem as? JsonObject ?: return@mapIndexedNotNull null
                    val url = (obj["jpg"] as? JsonObject)?.get("large_image_url").cleanString()
                        ?: (obj["webp"] as? JsonObject)?.get("large_image_url").cleanString()
                        ?: (obj["jpg"] as? JsonObject)?.get("image_url").cleanString()
                        ?: return@mapIndexedNotNull null
                    ArtworkCandidate(
                        url = url,
                        sourceName = "MyAnimeList",
                        description = "MAL Picture #${idx + 1}",
                        isBanner = false
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private fun fetchTvMazeCovers(title: String): List<ArtworkCandidate> {
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val request = Request.Builder()
                .url("https://api.tvmaze.com/search/shows?q=$encoded")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val list = json.parseToJsonElement(responseStr).jsonArray

                list.mapNotNull { item ->
                    val show = item.jsonObject["show"]?.jsonObject
                    val image = show?.get("image")?.jsonObject
                    val url = image?.get("original").cleanString()
                        ?: image?.get("medium").cleanString()
                    val showName = show?.get("name").cleanString() ?: title

                    if (url != null) {
                        ArtworkCandidate(
                            url = url,
                            sourceName = "TVMaze",
                            description = showName
                        )
                    } else null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private data class VndbArtworkBundle(
        val candidates: List<ArtworkCandidate>,
        val steamAppId: Long? = null,
        val englishTitle: String? = null,
        val nativeTitle: String? = null,
        val igdbSlug: String? = null
    )

    private suspend fun fetchSteamCovers(title: String, knownSteamAppId: Long? = null): List<ArtworkCandidate> = coroutineScope {
        try {
            val matchedApps: List<Pair<String, String>> = buildList {
                if (knownSteamAppId != null && knownSteamAppId > 0L) {
                    add(knownSteamAppId.toString() to title)
                }
                val encoded = URLEncoder.encode(title, "UTF-8")
                val request = Request.Builder()
                    .url("https://store.steampowered.com/api/storesearch/?term=$encoded&l=english&cc=US")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseStr = response.body?.string()
                        if (!responseStr.isNullOrBlank()) {
                            val parsed = json.parseToJsonElement(responseStr).asObj()
                            val items = parsed?.get("items").asArr()
                            items?.take(3)?.forEach { elem ->
                                val o = elem.asObj() ?: return@forEach
                                val id = o["id"].cleanString() ?: return@forEach
                                val name = o["name"].cleanString() ?: title
                                if (none { it.first == id }) {
                                    add(id to name)
                                }
                            }
                        }
                    }
                }
            }
            if (matchedApps.isEmpty()) return@coroutineScope emptyList()

            val candidates = mutableListOf<ArtworkCandidate>()
            for ((id, name) in matchedApps.take(3)) {
                candidates.add(ArtworkCandidate(url = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/library_600x900_2x.jpg", sourceName = "Steam", description = "$name (Library 2x)", isBanner = false))
                candidates.add(ArtworkCandidate(url = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/library_600x900.jpg", sourceName = "Steam", description = name, isBanner = false))
                candidates.add(ArtworkCandidate(url = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/library_hero.jpg", sourceName = "Steam Hero", description = "$name (Hero Banner)", isBanner = true, isScreenshot = false))
                candidates.add(ArtworkCandidate(url = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/header.jpg", sourceName = "Steam", description = "$name (Header)", isBanner = true, isScreenshot = false))
            }

            val detailBatches = matchedApps.take(2).map { (id, name) ->
                async {
                    withTimeoutOrNull(2500L) {
                        val batch = mutableListOf<ArtworkCandidate>()
                        try {
                            val dReq = Request.Builder().url("https://store.steampowered.com/api/appdetails?appids=$id").get().build()
                            client.newCall(dReq).execute().use { dResp ->
                                if (dResp.isSuccessful) {
                                    val dBody = dResp.body?.string().orEmpty()
                                    val dParsed = json.parseToJsonElement(dBody).asObj()
                                    val appData = dParsed?.get(id).asObj()?.get("data").asObj()
                                    if (appData != null) {
                                        val bgRaw = appData["background_raw"].cleanString() ?: appData["background"].cleanString()
                                        if (!bgRaw.isNullOrBlank()) {
                                            batch.add(ArtworkCandidate(url = bgRaw, sourceName = "Steam Background", description = "$name (Key Backdrop)", isBanner = true, isScreenshot = false))
                                        }
                                        val headerImg = appData["header_image"].cleanString()
                                        if (!headerImg.isNullOrBlank()) {
                                            batch.add(ArtworkCandidate(url = headerImg, sourceName = "Steam", description = "$name (Store Header)", isBanner = true, isScreenshot = false))
                                        }
                                        val screenshots = appData["screenshots"].asArr()
                                        screenshots?.take(10)?.forEachIndexed { idx, s ->
                                            val sUrl = s.asObj()?.get("path_full").cleanString()
                                            if (!sUrl.isNullOrBlank()) {
                                                batch.add(ArtworkCandidate(url = sUrl, sourceName = "Steam", description = "$name (Screenshot ${idx + 1})", isBanner = true, isScreenshot = true))
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                        batch
                    }.orEmpty()
                }
            }.awaitAll().flatten()

            candidates.addAll(detailBatches)
            candidates
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private suspend fun fetchSteamGridDbArtwork(
        title: String,
        steamAppId: Long?,
        apiKey: String
    ): List<ArtworkCandidate> = coroutineScope {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return@coroutineScope emptyList()
        try {
            var sgdbGameId: Long? = null
            if (steamAppId != null && steamAppId > 0L) {
                val req = Request.Builder()
                    .url("https://www.steamgriddb.com/api/v2/games/steam/$steamAppId")
                    .header("Authorization", "Bearer $cleanKey")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val obj = json.parseToJsonElement(resp.body?.string() ?: "{}").asObj()
                        if (obj?.get("success")?.jsonPrimitive?.booleanOrNull == true) {
                            sgdbGameId = obj["data"].asObj()?.get("id")?.jsonPrimitive?.longOrNull
                        }
                    }
                }
            }
            if (sgdbGameId == null && title.isNotBlank()) {
                val encoded = URLEncoder.encode(title.trim(), "UTF-8")
                val req = Request.Builder()
                    .url("https://www.steamgriddb.com/api/v2/search/autocomplete/$encoded")
                    .header("Authorization", "Bearer $cleanKey")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val obj = json.parseToJsonElement(resp.body?.string() ?: "{}").asObj()
                        if (obj?.get("success")?.jsonPrimitive?.booleanOrNull == true) {
                            sgdbGameId = obj["data"].asArr()?.firstOrNull().asObj()?.get("id")?.jsonPrimitive?.longOrNull
                        }
                    }
                }
            }
            val targetId = sgdbGameId ?: return@coroutineScope emptyList()

            val heroesDeferred = async {
                withTimeoutOrNull(2500L) {
                    val req = Request.Builder()
                        .url("https://www.steamgriddb.com/api/v2/heroes/game/$targetId")
                        .header("Authorization", "Bearer $cleanKey")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withTimeoutOrNull emptyList()
                        val obj = json.parseToJsonElement(resp.body?.string() ?: "{}").asObj() ?: return@withTimeoutOrNull emptyList()
                        if (obj["success"]?.jsonPrimitive?.booleanOrNull != true) return@withTimeoutOrNull emptyList()
                        val items = obj["data"].asArr() ?: return@withTimeoutOrNull emptyList()
                        val sorted = items.mapNotNull { it.asObj() }.sortedBy { it["humor"]?.jsonPrimitive?.booleanOrNull == true }
                        sorted.take(10).mapNotNull { o ->
                            val u = o["url"].cleanString() ?: return@mapNotNull null
                            val isNsfw = o["nsfw"]?.jsonPrimitive?.booleanOrNull == true
                            ArtworkCandidate(
                                url = u,
                                sourceName = "SteamGridDB",
                                description = "Textless Hero Artwork",
                                isBanner = true,
                                isNsfw = isNsfw,
                                isScreenshot = false
                            )
                        }
                    }
                }.orEmpty()
            }

            val gridsDeferred = async {
                withTimeoutOrNull(2500L) {
                    val req = Request.Builder()
                        .url("https://www.steamgriddb.com/api/v2/grids/game/$targetId?dimensions=600x900")
                        .header("Authorization", "Bearer $cleanKey")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withTimeoutOrNull emptyList()
                        val obj = json.parseToJsonElement(resp.body?.string() ?: "{}").asObj() ?: return@withTimeoutOrNull emptyList()
                        if (obj["success"]?.jsonPrimitive?.booleanOrNull != true) return@withTimeoutOrNull emptyList()
                        val items = obj["data"].asArr() ?: return@withTimeoutOrNull emptyList()
                        items.take(8).mapNotNull { el ->
                            val o = el.asObj() ?: return@mapNotNull null
                            val u = o["url"].cleanString() ?: return@mapNotNull null
                            val isNsfw = o["nsfw"]?.jsonPrimitive?.booleanOrNull == true
                            ArtworkCandidate(
                                url = u,
                                sourceName = "SteamGridDB",
                                description = "600x900 Grid Cover",
                                isBanner = false,
                                isNsfw = isNsfw
                            )
                        }
                    }
                }.orEmpty()
            }

            heroesDeferred.await() + gridsDeferred.await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private suspend fun fetchVndbArtwork(
        title: String,
        vndbId: String?,
        englishTitleHint: String?,
        onBatch: ((List<ArtworkCandidate>) -> Unit)? = null
    ): VndbArtworkBundle? = coroutineScope {
        try {
            val payload = buildJsonObject {
                if (!vndbId.isNullOrBlank()) {
                    put("filters", json.parseToJsonElement("[\"id\", \"=\", ${JsonPrimitive(vndbId)}]"))
                    put("results", 1)
                } else {
                    val q = englishTitleHint?.takeIf { it.isNotBlank() } ?: title
                    put("filters", json.parseToJsonElement("[\"search\", \"=\", ${JsonPrimitive(q)}]"))
                    put("sort", "votecount")
                    put("reverse", true)
                    put("results", 3)
                }
                put("fields", "id, title, alttitle, titles{lang,title,official,main}, olang, image{url,sexual,violence}, screenshots{url,sexual,violence}, extlinks{name,id}")
            }

            val request = Request.Builder()
                .url("https://api.vndb.org/kana/vn")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val results = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@coroutineScope null
                val responseStr = response.body?.string() ?: return@coroutineScope null
                val parsed = json.parseToJsonElement(responseStr).asObj() ?: return@coroutineScope null
                parsed["results"].asArr() ?: return@coroutineScope null
            }

            val immediateCandidates = mutableListOf<ArtworkCandidate>()
            var resolvedVnId: String? = null
            var resolvedEnTitle: String? = null
            var resolvedNativeTitle: String? = null
            var resolvedIgdbSlug: String? = null

            results.forEachIndexed { resIdx, node ->
                val obj = node.asObj() ?: return@forEachIndexed
                val vnId = obj["id"].cleanString()
                val vnTitle = obj["title"].cleanString() ?: title
                val olang = obj["olang"].cleanString()
                if (resIdx == 0) {
                    resolvedVnId = vnId
                    val titlesArr = obj["titles"].asArr()
                    resolvedEnTitle = titlesArr?.firstOrNull { el ->
                        val o = el.asObj() ?: return@firstOrNull false
                        val isOff = o["official"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
                        val isMain = o["main"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
                        o["lang"].cleanString() == "en" && (isOff || isMain)
                    }.asObj()?.get("title").cleanString()
                        ?: titlesArr?.firstOrNull { it.asObj()?.get("lang").cleanString() == "en" }.asObj()?.get("title").cleanString()
                    resolvedNativeTitle = obj["alttitle"].cleanString()
                        ?: titlesArr?.firstOrNull { it.asObj()?.get("lang").cleanString() == olang }.asObj()?.get("title").cleanString()
                    resolvedIgdbSlug = obj["extlinks"].asArr()?.firstOrNull {
                        it.asObj()?.get("name").cleanString() == "igdb_game"
                    }.asObj()?.get("id").cleanString()
                }

                val imageObj = obj["image"].asObj()
                val coverUrl = imageObj?.get("url").cleanString()
                val coverSex = imageObj?.get("sexual")?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
                val coverViol = imageObj?.get("violence")?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
                if (!coverUrl.isNullOrBlank()) {
                    immediateCandidates.add(
                        ArtworkCandidate(
                            url = coverUrl,
                            sourceName = "VNDB",
                            description = vnTitle,
                            isBanner = false,
                            isNsfw = coverSex > 0.45f || coverViol > 0.9f
                        )
                    )
                }

                val screenshots = obj["screenshots"].asArr()
                screenshots?.forEachIndexed { idx, shot ->
                    val shotObj = shot.asObj()
                    val shotUrl = shotObj?.get("url").cleanString()
                    val sSex = shotObj?.get("sexual")?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
                    val sViol = shotObj?.get("violence")?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
                    if (!shotUrl.isNullOrBlank()) {
                        immediateCandidates.add(
                            ArtworkCandidate(
                                url = shotUrl,
                                sourceName = "VNDB Screenshot",
                                description = "$vnTitle (Screenshot ${idx + 1})",
                                isBanner = true,
                                isNsfw = sSex > 0.45f || sViol > 0.9f,
                                isScreenshot = true
                            )
                        )
                    }
                }
            }

            if (immediateCandidates.isNotEmpty()) {
                onBatch?.invoke(immediateCandidates)
            }

            val topId = resolvedVnId ?: return@coroutineScope VndbArtworkBundle(
                candidates = immediateCandidates,
                englishTitle = resolvedEnTitle,
                nativeTitle = resolvedNativeTitle,
                igdbSlug = resolvedIgdbSlug
            )

            // Parallel VNDB /release queries: (1) Steam AppID lookup via extlink="steam", (2) Official package edition covers
            val steamDeferred = async {
                withTimeoutOrNull(2500L) {
                    val relPayload = buildJsonObject {
                        put("filters", json.parseToJsonElement("[\"and\",[\"vn\",\"=\",[\"id\",\"=\",\"$topId\"]],[\"extlink\",\"=\",\"steam\"]]"))
                        put("fields", "title,released,official,patch,extlinks{name,id}")
                        put("results", 15)
                        put("sort", "released")
                    }.toString()
                    val relReq = Request.Builder()
                        .url("https://api.vndb.org/kana/release")
                        .post(relPayload.toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(relReq).execute().use { relResp ->
                        val relBody = relResp.body?.string()
                        if (!relResp.isSuccessful || relBody.isNullOrBlank()) return@withTimeoutOrNull null
                        val relArr = json.parseToJsonElement(relBody).asObj()?.get("results").asArr()
                        relArr?.firstOrNull { rEl ->
                            val rObj = rEl.asObj() ?: return@firstOrNull false
                            val rDate = rObj["released"].cleanString()
                            val isOfficial = rObj["official"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
                            val isPatch = rObj["patch"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                            val hasSteam = rObj["extlinks"].asArr()?.any { it.asObj()?.get("name").cleanString() == "steam" } == true
                            hasSteam && isOfficial && !isPatch && !rDate.isNullOrBlank() && !rDate.equals("TBA", true) && rDate <= "2099-01-01" && !rObj["title"].cleanString().orEmpty().contains("reboot", true)
                        }.asObj()?.get("extlinks").asArr()?.firstOrNull { it.asObj()?.get("name").cleanString() == "steam" }.asObj()?.get("id").cleanString()?.toLongOrNull()
                            ?: relArr?.firstNotNullOfOrNull { rEl ->
                                val rObj = rEl.asObj() ?: return@firstNotNullOfOrNull null
                                if (rObj["title"].cleanString().orEmpty().contains("reboot", true)) null
                                else rObj["extlinks"].asArr()?.firstOrNull { it.asObj()?.get("name").cleanString() == "steam" }.asObj()?.get("id").cleanString()?.toLongOrNull()
                            }
                    }
                }
            }

            val editionsDeferred = async {
                withTimeoutOrNull(2500L) {
                    val imgPayload = buildJsonObject {
                        put("filters", json.parseToJsonElement("[\"vn\",\"=\",[\"id\",\"=\",\"$topId\"]]"))
                        put("fields", "title,images{url,type,sexual,violence}")
                        put("results", 20)
                    }.toString()
                    val imgReq = Request.Builder()
                        .url("https://api.vndb.org/kana/release")
                        .post(imgPayload.toRequestBody("application/json".toMediaType()))
                        .build()
                    val edList = mutableListOf<ArtworkCandidate>()
                    client.newCall(imgReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val arr = json.parseToJsonElement(body).asObj()?.get("results").asArr()
                            arr?.forEach { relEl ->
                                val rObj = relEl.asObj() ?: return@forEach
                                val relTitle = rObj["title"].cleanString() ?: "Release Edition"
                                rObj["images"].asArr()?.forEach { imgEl ->
                                    val io = imgEl.asObj() ?: return@forEach
                                    val iType = io["type"].cleanString()?.lowercase()
                                    if (iType == "pkgfront" || iType == "dig") {
                                        val u = io["url"].cleanString() ?: return@forEach
                                        val iSex = io["sexual"]?.jsonPrimitive?.floatOrNull ?: 0f
                                        val iViol = io["violence"]?.jsonPrimitive?.floatOrNull ?: 0f
                                        edList.add(
                                            ArtworkCandidate(
                                                url = u,
                                                sourceName = "VNDB Edition",
                                                description = relTitle,
                                                isBanner = false,
                                                isNsfw = iSex > 0.45f || iViol > 0.9f
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    edList.distinctBy { it.url }.take(10)
                }.orEmpty()
            }

            val steamAppId = steamDeferred.await()
            val editionCovers = editionsDeferred.await()
            val releaseBatch = mutableListOf<ArtworkCandidate>()
            releaseBatch.addAll(editionCovers)

            if (steamAppId != null) {
                val labelTitle = resolvedEnTitle ?: title
                releaseBatch.add(
                    ArtworkCandidate(
                        url = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$steamAppId/library_600x900_2x.jpg",
                        sourceName = "Steam",
                        description = "$labelTitle (Steam 600x900 Capsule)",
                        isBanner = false
                    )
                )
                releaseBatch.add(
                    ArtworkCandidate(
                        url = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$steamAppId/library_hero.jpg",
                        sourceName = "Steam Hero",
                        description = "$labelTitle (Steam Hero Banner)",
                        isBanner = true,
                        isScreenshot = false
                    )
                )
                withTimeoutOrNull(2000L) {
                    try {
                        val dReq = Request.Builder().url("https://store.steampowered.com/api/appdetails?appids=$steamAppId").get().build()
                        client.newCall(dReq).execute().use { dResp ->
                            if (dResp.isSuccessful) {
                                val dParsed = json.parseToJsonElement(dResp.body?.string().orEmpty()).asObj()
                                val appData = dParsed?.get(steamAppId.toString()).asObj()?.get("data").asObj()
                                val bgRaw = appData?.get("background_raw").cleanString() ?: appData?.get("background").cleanString()
                                if (!bgRaw.isNullOrBlank()) {
                                    releaseBatch.add(
                                        ArtworkCandidate(
                                            url = bgRaw,
                                            sourceName = "Steam Background",
                                            description = "$labelTitle (Key Backdrop)",
                                            isBanner = true,
                                            isScreenshot = false
                                        )
                                    )
                                }
                                val hdr = appData?.get("header_image").cleanString()
                                if (!hdr.isNullOrBlank()) {
                                    releaseBatch.add(
                                        ArtworkCandidate(
                                            url = hdr,
                                            sourceName = "Steam",
                                            description = "$labelTitle (Store Header)",
                                            isBanner = true,
                                            isScreenshot = false
                                        )
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            if (releaseBatch.isNotEmpty()) {
                onBatch?.invoke(releaseBatch)
            }

            VndbArtworkBundle(
                candidates = immediateCandidates + releaseBatch,
                steamAppId = steamAppId,
                englishTitle = resolvedEnTitle,
                nativeTitle = resolvedNativeTitle,
                igdbSlug = resolvedIgdbSlug
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun fetchTmdbArtwork(title: String, isMovie: Boolean): List<ArtworkCandidate> {
        return try {
            val apiKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
            val cleanTitle = title.replace(Regex("[:\\-_–—]"), " ").replace(Regex("\\s+"), " ").trim()
            val encoded = URLEncoder.encode(cleanTitle.ifBlank { title }, "UTF-8")
            val endpoint = if (isMovie) "movie" else "tv"
            val url = "https://api.themoviedb.org/3/search/$endpoint?api_key=$apiKey&query=$encoded"

            val request = Request.Builder().url(url).get().build()
            val candidates = mutableListOf<ArtworkCandidate>()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val parsed = json.parseToJsonElement(responseStr).jsonObject
                val results = parsed["results"]?.jsonArray ?: return emptyList()

                for (elem in results.take(4)) {
                    val obj = elem.jsonObject
                    val tmdbId = obj["id"].cleanString()
                    val itemTitle = obj["title"].cleanString()
                        ?: obj["name"].cleanString()
                        ?: title

                    val posterPath = obj["poster_path"].cleanString()
                    if (!posterPath.isNullOrBlank()) {
                        candidates.add(
                            ArtworkCandidate(
                                url = "https://image.tmdb.org/t/p/original$posterPath",
                                sourceName = "TMDb",
                                description = itemTitle,
                                isBanner = false
                            )
                        )
                    }
                    val backdropPath = obj["backdrop_path"].cleanString()
                    if (!backdropPath.isNullOrBlank()) {
                        candidates.add(
                            ArtworkCandidate(
                                url = "https://image.tmdb.org/t/p/original$backdropPath",
                                sourceName = "TMDb",
                                description = "$itemTitle (Backdrop)",
                                isBanner = true
                            )
                        )
                    }

                    if (!tmdbId.isNullOrBlank()) {
                        try {
                            val imagesUrl = "https://api.themoviedb.org/3/$endpoint/$tmdbId/images?api_key=$apiKey&include_image_language=en,ja,null"
                            val imgRequest = Request.Builder().url(imagesUrl).get().build()
                            client.newCall(imgRequest).execute().use { imgResponse ->
                                if (imgResponse.isSuccessful) {
                                    val imgBody = imgResponse.body?.string().orEmpty()
                                    val imgObj = json.parseToJsonElement(imgBody).jsonObject
                                    imgObj["posters"]?.jsonArray?.take(6)?.forEach { p ->
                                        val path = p.jsonObject["file_path"].cleanString()
                                        if (!path.isNullOrBlank()) {
                                            candidates.add(
                                                ArtworkCandidate(
                                                    url = "https://image.tmdb.org/t/p/original$path",
                                                    sourceName = "TMDb",
                                                    description = "$itemTitle (Poster)",
                                                    isBanner = false
                                                )
                                            )
                                        }
                                    }
                                    imgObj["backdrops"]?.jsonArray?.take(6)?.forEach { b ->
                                        val path = b.jsonObject["file_path"].cleanString()
                                        if (!path.isNullOrBlank()) {
                                            candidates.add(
                                                ArtworkCandidate(
                                                    url = "https://image.tmdb.org/t/p/original$path",
                                                    sourceName = "TMDb",
                                                    description = "$itemTitle (Backdrop)",
                                                    isBanner = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            candidates.distinctBy { it.url }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchGoogleBooksCovers(title: String): List<ArtworkCandidate> {
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val url = "https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=8"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string() ?: return emptyList()
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val items = root["items"]?.jsonArray ?: return emptyList()
                val candidates = mutableListOf<ArtworkCandidate>()
                for (item in items) {
                    val volumeInfo = item.jsonObject["volumeInfo"]?.jsonObject ?: continue
                    val bookTitle = volumeInfo["title"].cleanString() ?: title
                    val imageLinks = volumeInfo["imageLinks"]?.jsonObject ?: continue
                    val thumbnail = imageLinks["extraLarge"].cleanString()
                        ?: imageLinks["large"].cleanString()
                        ?: imageLinks["medium"].cleanString()
                        ?: imageLinks["thumbnail"].cleanString()
                    if (!thumbnail.isNullOrBlank()) {
                        val highRes = thumbnail.replace("http://", "https://")
                            .replace(Regex("&zoom=[1-5]"), "&zoom=0")
                        candidates.add(
                            ArtworkCandidate(
                                url = highRes,
                                sourceName = "Google Books",
                                description = bookTitle,
                                isBanner = false
                            )
                        )
                    }
                }
                candidates
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchOpenLibraryCovers(title: String): List<ArtworkCandidate> {
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val url = "https://openlibrary.org/search.json?title=$encoded&limit=8"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string() ?: return emptyList()
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val docs = root["docs"]?.jsonArray ?: return emptyList()
                val candidates = mutableListOf<ArtworkCandidate>()
                for (doc in docs) {
                    val docObj = doc.jsonObject
                    val bookTitle = docObj["title"].cleanString() ?: title
                    val coverId = docObj["cover_i"].cleanString()
                    if (!coverId.isNullOrBlank()) {
                        candidates.add(
                            ArtworkCandidate(
                                url = "https://covers.openlibrary.org/b/id/$coverId-L.jpg",
                                sourceName = "Open Library",
                                description = bookTitle,
                                isBanner = false
                            )
                        )
                    }
                }
                candidates
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchComickCovers(title: String): List<ArtworkCandidate> {
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val url = "https://api.comick.io/v1.0/search?q=$encoded&limit=6"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                .header("Referer", "https://comick.io")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val list = json.parseToJsonElement(responseStr) as? JsonArray ?: return emptyList()
                val candidates = mutableListOf<ArtworkCandidate>()
                for (elem in list) {
                    val obj = elem as? JsonObject ?: continue
                    val comicTitle = obj["title"].cleanString() ?: title
                    val b2key = obj["b2key"].cleanString()
                    if (!b2key.isNullOrBlank()) {
                        candidates.add(
                            ArtworkCandidate(
                                url = "https://meo.comick.picture/$b2key",
                                sourceName = "Comick",
                                description = comicTitle,
                                isBanner = false
                            )
                        )
                    }
                }
                candidates
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private fun fetchWikidataNativeTitlesForArt(title: String): List<String> {
        return try {
            val encoded = URLEncoder.encode(title.trim(), "UTF-8")
            val req = Request.Builder()
                .url("https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$encoded&language=en&limit=1&format=json")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()
            val qid = jikanClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                (json.parseToJsonElement(body) as? JsonObject)?.get("search")?.let { it as? JsonArray }
                    ?.firstOrNull()?.jsonObject?.get("id").cleanString()
            } ?: return emptyList()

            val entReq = Request.Builder()
                .url("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$qid&props=labels&languages=zh|zh-hans|ko|ja&format=json")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()
            jikanClient.newCall(entReq).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val labels = (json.parseToJsonElement(body) as? JsonObject)
                    ?.get("entities")?.let { it as? JsonObject }
                    ?.get(qid)?.let { it as? JsonObject }
                    ?.get("labels")?.let { it as? JsonObject } ?: return emptyList()
                listOfNotNull(
                    labels["zh-hans"]?.jsonObject?.get("value").cleanString() ?: labels["zh"]?.jsonObject?.get("value").cleanString(),
                    labels["ko"]?.jsonObject?.get("value").cleanString(),
                    labels["ja"]?.jsonObject?.get("value").cleanString()
                ).distinct()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private fun fetchRanobeDbCovers(title: String, nativeTitle: String?): List<ArtworkCandidate> {
        return try {
            val cleanQuery = title.trim()
            if (cleanQuery.isBlank()) return emptyList()
            val subSeriesRegex = Regex(
                """\s*[:\-–—]\s*(?:Year|Part|Act)\s*\d+.*$|\s*[:\-–—]\s*\d+-nensei.*$""",
                RegexOption.IGNORE_CASE
            )
            val baseQuery = cleanQuery.replace(subSeriesRegex, "").trim()
                .takeIf { it.isNotBlank() && it != cleanQuery }

            fun querySeriesList(q: String): List<JsonObject> {
                val encoded = URLEncoder.encode(q, "UTF-8")
                val req = Request.Builder()
                    .url("https://ranobedb.org/api/v0/series?q=$encoded&limit=8")
                    .header("User-Agent", "OmniTrack/1.0 (Android)")
                    .get()
                    .build()
                return client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return emptyList()
                    val body = resp.body?.string() ?: return emptyList()
                    val arr = (json.parseToJsonElement(body) as? JsonObject)?.get("series") as? JsonArray
                        ?: return emptyList()
                    arr.mapNotNull { it as? JsonObject }
                }
            }

            fun scoreNode(o: JsonObject): Int? {
                val candNames = listOfNotNull(
                    o["title"].cleanString(),
                    o["romaji"].cleanString(),
                    o["romaji_orig"].cleanString(),
                    o["title_orig"].cleanString()
                )
                if (candNames.isEmpty()) return null
                val queries = listOfNotNull(cleanQuery, baseQuery, nativeTitle).distinct()
                val matches = queries.any { q ->
                    isTitleMatch(q, candNames) || candNames.any { it.equals(q, ignoreCase = true) }
                }
                if (!matches) return null

                fun normSimple(s: String): String = s.lowercase().replace(Regex("""[^\p{L}\p{N}]"""), "")
                val qNorms = queries.map(::normSimple).filter { it.isNotBlank() }
                var score = if (candNames.any { normSimple(it) in qNorms }) 1000 else 0
                val qLower = cleanQuery.lowercase()
                val candJoinedLower = candNames.joinToString(" ").lowercase()
                val spinoffTerms = listOf(
                    "guidebook", "character book", "special book", "recollection",
                    "short story", "artbook", "fanbook", "anthology", "side stor"
                )
                if (spinoffTerms.any { it in candJoinedLower && it !in qLower }) {
                    score -= 600
                }
                val cNumBooks = (o["c_num_books"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                    ?: (o["volumes"] as? JsonObject)?.get("count")?.let { it as? JsonPrimitive }?.contentOrNull?.toIntOrNull()
                    ?: 0
                score += minOf(cNumBooks, 50) * 6
                return score
            }

            var list = querySeriesList(cleanQuery)
            var bestNode = list.mapNotNull { o -> scoreNode(o)?.let { it to o } }.maxByOrNull { it.first }?.second
            if (bestNode == null && baseQuery != null) {
                list = querySeriesList(baseQuery)
                bestNode = list.mapNotNull { o -> scoreNode(o)?.let { it to o } }.maxByOrNull { it.first }?.second
            }
            val seriesId = (bestNode?.get("id") as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                ?: return emptyList()

            val detailReq = Request.Builder()
                .url("https://ranobedb.org/api/v0/series/$seriesId")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()
            client.newCall(detailReq).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val seriesObj = (json.parseToJsonElement(body) as? JsonObject)
                    ?.get("series") as? JsonObject ?: return emptyList()
                val books = seriesObj["books"] as? JsonArray ?: return emptyList()
                val candidates = mutableListOf<ArtworkCandidate>()
                val seenUrls = mutableSetOf<String>()
                books.reversed().forEachIndexed { revIdx, elem ->
                    val b = elem as? JsonObject ?: return@forEachIndexed
                    val filename = (b["image"] as? JsonObject)?.get("filename").cleanString() ?: return@forEachIndexed
                    val coverUrl = "https://images.ranobedb.org/$filename"
                    if (!seenUrls.add(coverUrl)) return@forEachIndexed
                    val bTitle = b["title"].cleanString()
                    val romajiOrig = b["romaji_orig"].cleanString()
                    val volNum = bTitle?.let {
                        Regex("""(?:Vol\.?|Volume)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1)
                    } ?: romajiOrig?.let {
                        Regex("""(?:Vol\.?|Volume)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1)
                    } ?: (books.size - revIdx).toString()
                    candidates.add(
                        ArtworkCandidate(
                            url = coverUrl,
                            sourceName = "RanobeDB",
                            description = "Vol. $volNum LN",
                            isBanner = false
                        )
                    )
                }
                candidates
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private fun fetchAniListAdaptationBanners(title: String, nativeTitle: String?): List<ArtworkCandidate> {
        return try {
            val gql = """
                query (${'$'}search: String) {
                  Page(page: 1, perPage: 5) {
                    media(search: ${'$'}search, sort: [POPULARITY_DESC]) {
                      title { english romaji native }
                      format
                      bannerImage
                      coverImage { extraLarge large }
                    }
                  }
                }
            """.trimIndent()
            val candidates = mutableListOf<ArtworkCandidate>()
            for (sq in listOfNotNull(title, nativeTitle).distinct()) {
                val payload = buildJsonObject {
                    put("query", gql)
                    putJsonObject("variables") { put("search", sq) }
                }.toString()
                val req = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", "OmniTrack/1.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val list = (json.parseToJsonElement(body) as? JsonObject)
                        ?.get("data")?.let { it as? JsonObject }
                        ?.get("Page")?.let { it as? JsonObject }
                        ?.get("media")?.let { it as? JsonArray } ?: return@use
                    for (node in list) {
                        val o = node.jsonObject
                        val tObj = o["title"] as? JsonObject
                        val names = listOfNotNull(
                            tObj?.get("english").cleanString(),
                            tObj?.get("romaji").cleanString(),
                            tObj?.get("native").cleanString()
                        )
                        if (!isTitleMatch(title, names) && (nativeTitle == null || names.none { it.equals(nativeTitle, ignoreCase = true) })) {
                            continue
                        }
                        val fmt = o["format"].cleanString() ?: "Adaptation"
                        o["bannerImage"].cleanString()?.let { banner ->
                            candidates.add(
                                ArtworkCandidate(
                                    url = banner,
                                    sourceName = "AniList",
                                    description = "$fmt Adaptation Banner",
                                    isBanner = true
                                )
                            )
                        }
                    }
                }
                if (candidates.isNotEmpty()) break
            }
            candidates.distinctBy { it.url }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    companion object {
        private val candidateCache = ConcurrentHashMap<String, List<ArtworkCandidate>>()

        private val MANGA_COVER_PRIORITY = mapOf(
            "RanobeDB" to 1,
            "MangaDex" to 2,
            "BookWalker" to 3,
            "Bangumi" to 4,
            "AniList" to 5,
            "Naver" to 6,
            "Kuaikan" to 7,
            "Kitsu" to 8,
            "MyAnimeList" to 9,
            "Comick" to 10,
            "Default" to 11
        )

        private val MANGA_BANNER_PRIORITY = mapOf(
            "Naver" to 1,
            "Kuaikan" to 2,
            "Bangumi" to 3,
            "AniList" to 4,
            "Kitsu" to 5,
            "Default" to 6
        )

        private val HANGUL_REGEX = Regex("[\\uAC00-\\uD7AF\\u1100-\\u11FF\\u3130-\\u318F]")
        private val HANZI_REGEX = Regex("[\\u4E00-\\u9FFF\\u3400-\\u4DBF]")
        private val JAPANESE_REGEX = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FFF]")

        private val BOOKWALKER_THUMB_ID_REGEX = Regex("thumbnailImage_([a-zA-Z0-9]+)\\.jpg")
        private val BOOKWALKER_DATA_ORIGINAL_REGEX = Regex("data-original=[\"'](https://c\\.bookwalker\\.jp/[^\"']+)[\"']")
        private val NAVER_BACK_IMAGE_REGEX = Regex("src=[\"'](https://[^\"']*backImage_[^\"']+)[\"']")
        private val OG_IMAGE_REGEX = Regex("<meta\\s+property=[\"']og:image[\"']\\s+content=[\"']([^\"']+)[\"']")
        private val WEBTOONS_CARD_LINK_REGEX = Regex("<a\\s+href=[\"'](https://www\\.webtoons\\.com/en/[^\"']+/list\\?title_no=\\d+)[\"']")
        private val WEBTOONS_BG_REGEX = Regex("class=[\"']detail_bg[\"']\\s+style=[\"'][^\"']*url\\(['\"]?([^'\")]+)['\"]?\\)")

        private val TITLE_STOP_WORDS = setOf(
            "the", "a", "an", "and", "or", "of", "in", "to", "for", "with",
            "on", "at", "by", "from", "is", "it", "s", "no", "not", "i", "me", "my"
        )

        fun containsHangul(text: String): Boolean = HANGUL_REGEX.containsMatchIn(text)
        fun containsHanzi(text: String): Boolean = HANZI_REGEX.containsMatchIn(text)
        fun containsJapanese(text: String): Boolean = JAPANESE_REGEX.containsMatchIn(text)

        fun cleanTitle(title: String): String {
            val cleaned = title
                .replace(Regex("\\s*[(\\[{].*?[)\\]}]"), " ")
                .replace(Regex("(?i)\\s*-\\s*(webtoon|manhwa|manga|comic|scanlations?|scan).*"), " ")
                .replace(Regex("(?i)\\s+(?:season|s)\\s*\\d+.*"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            return cleaned.ifBlank { title.trim() }
        }

        fun normalizeForComparison(s: String): String {
            return s.lowercase()
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun isTitleMatch(queryTitle: String, candidateTitles: List<String>): Boolean {
            val qNorm = normalizeForComparison(queryTitle)
            if (qNorm.isBlank()) return false
            val qTokens = qNorm.split(" ").filter { it.isNotBlank() && it !in TITLE_STOP_WORDS }.toSet()

            for (candidate in candidateTitles) {
                val cNorm = normalizeForComparison(candidate)
                if (cNorm.isBlank()) continue
                if (qNorm == cNorm) return true
                val cTokens = cNorm.split(" ").filter { it.isNotBlank() && it !in TITLE_STOP_WORDS }.toSet()
                if (qTokens.isNotEmpty() && cTokens.isNotEmpty()) {
                    if (qTokens == cTokens) return true
                    val intersection = qTokens.intersect(cTokens).size
                    val shorterLen = minOf(qTokens.size, cTokens.size)
                    val longerLen = maxOf(qTokens.size, cTokens.size)
                    if (intersection.toFloat() / shorterLen >= 0.75f && intersection.toFloat() / longerLen >= 0.50f) {
                        return true
                    }
                } else if (qNorm.length >= 4 && cNorm.length >= 4 && qNorm == cNorm) {
                    return true
                }
            }
            return false
        }
    }
}
