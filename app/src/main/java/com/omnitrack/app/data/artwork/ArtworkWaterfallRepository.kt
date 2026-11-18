package com.omnitrack.app.data.artwork

import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.preferences.PreferenceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        forceRefresh: Boolean = false
    ): List<ArtworkCandidate> = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext emptyList()
        val clean = cleanTitle(title)
        val cacheKey = "${mediaType.name}:${clean.lowercase()}:${anilistId ?: 0}:${malId ?: 0}:${mangadexId.orEmpty()}"
        if (!forceRefresh) {
            candidateCache[cacheKey]?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        }

        val results: List<ArtworkCandidate> = coroutineScope {
            when (mediaType) {
                MediaType.MANGA, MediaType.NOVEL -> {
                    val bridge = fetchAniListRosettaBridge(
                        rawTitle = clean,
                        isAnime = false,
                        anilistId = anilistId,
                        malId = malId
                    )
                    val effAnilistId = anilistId ?: bridge.anilistId
                    val effMalId = malId ?: bridge.malId
                    val effOrigin = (originCountry ?: bridge.countryOfOrigin)?.uppercase()
                    val effNative = bridge.nativeTitle ?: nativeTitle?.takeIf { it.isNotBlank() }
                    val effRomaji = bridge.romajiTitle ?: romajiTitle?.takeIf { it.isNotBlank() }
                    val effSynonyms = bridge.synonyms

                    val koreanQuery = listOfNotNull(
                        effNative?.takeIf { containsHangul(it) },
                        effSynonyms.firstOrNull { containsHangul(it) },
                        clean.takeIf { containsHangul(it) }
                    ).firstOrNull() ?: if (effOrigin != "JP" && !containsJapanese(clean)) clean else null

                    val isChinese = effOrigin == "CN" ||
                        effOrigin == "TW" ||
                        containsHanzi(clean) ||
                        (effNative != null && containsHanzi(effNative))
                    val chineseQuery = listOfNotNull(
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

                    val deferreds = mutableListOf<Deferred<List<ArtworkCandidate>>>()
                    deferreds.add(async {
                        fetchMangaDexCovers(
                            rawTitle = clean,
                            altTitle = altMangaDexTitle,
                            anilistId = effAnilistId,
                            malId = effMalId,
                            mangadexId = mangadexId
                        )
                    })
                    if (japaneseQuery != null && !containsHangul(japaneseQuery) && effOrigin != "KR" && effOrigin != "CN") {
                        deferreds.add(async { fetchBookWalkerCovers(japaneseQuery) })
                    }
                    deferreds.add(async { fetchBangumiCovers(japaneseQuery ?: clean, type = 1) })
                    if (koreanQuery != null) {
                        val globalQuery = listOfNotNull(
                            bridge.englishTitle,
                            effRomaji,
                            clean
                        ).firstOrNull { !containsHangul(it) }
                        deferreds.add(async { fetchNaverWebtoonCovers(koreanQuery, globalQuery) })
                    }
                    if (chineseQuery != null) {
                        deferreds.add(async { fetchKuaikanCovers(chineseQuery) })
                    }
                    deferreds.add(async { fetchKitsuCovers(clean, isAnime = false) })
                    deferreds.add(async { fetchComickCovers(clean) })
                    if (effMalId != null && effMalId > 0) {
                        deferreds.add(async { fetchJikanMangaPictures(effMalId) })
                    }

                    val combined = mutableListOf<ArtworkCandidate>()
                    combined.addAll(bridge.candidates)
                    combined.addAll(deferreds.awaitAll().flatten())
                    if (!defaultCoverUrl.isNullOrBlank()) {
                        combined.add(
                            ArtworkCandidate(
                                url = defaultCoverUrl,
                                sourceName = "Default",
                                description = "Source Cover",
                                isBanner = false
                            )
                        )
                    }
                    combined.sortedWith(
                        compareBy { candidate ->
                            if (candidate.isBanner) {
                                MANGA_BANNER_PRIORITY[candidate.sourceName] ?: 99
                            } else {
                                MANGA_COVER_PRIORITY[candidate.sourceName] ?: 99
                            }
                        }
                    )
                }

                MediaType.ANIME -> {
                    val bridge = fetchAniListRosettaBridge(
                        rawTitle = clean,
                        isAnime = true,
                        anilistId = anilistId,
                        malId = malId
                    )
                    val deferreds = mutableListOf<Deferred<List<ArtworkCandidate>>>()
                    deferreds.add(async { fetchBangumiCovers(bridge.nativeTitle ?: bridge.romajiTitle ?: clean, type = 2) })
                    deferreds.add(async { fetchKitsuCovers(clean, isAnime = true) })
                    deferreds.add(async {
                        fetchMangaDexCovers(
                            rawTitle = clean,
                            altTitle = bridge.romajiTitle ?: romajiTitle,
                            anilistId = anilistId ?: bridge.anilistId,
                            malId = malId ?: bridge.malId,
                            mangadexId = mangadexId
                        )
                    })
                    deferreds.add(async { fetchTmdbArtwork(clean, isMovie = false) })
                    val combined = mutableListOf<ArtworkCandidate>()
                    combined.addAll(bridge.candidates)
                    combined.addAll(deferreds.awaitAll().flatten())
                    if (!defaultCoverUrl.isNullOrBlank()) {
                        combined.add(ArtworkCandidate(url = defaultCoverUrl, sourceName = "Default", description = "Source Cover"))
                    }
                    combined
                }

                MediaType.BOOK -> {
                    val deferreds = listOf(
                        async { fetchGoogleBooksCovers(title) },
                        async { fetchOpenLibraryCovers(title) }
                    )
                    deferreds.awaitAll().flatten()
                }

                MediaType.SHOW, MediaType.MOVIE -> {
                    val deferreds = listOf(
                        async { fetchTvMazeCovers(title) },
                        async { fetchBangumiCovers(title, type = 2) },
                        async { fetchTmdbArtwork(title, isMovie = (mediaType == MediaType.MOVIE)) }
                    )
                    deferreds.awaitAll().flatten()
                }

                MediaType.GAME -> {
                    fetchSteamCovers(title)
                }

                MediaType.VISUAL_NOVEL -> {
                    val deferreds = listOf(
                        async { fetchVndbCovers(title) },
                        async { fetchSteamCovers(title) },
                        async { fetchBangumiCovers(title, type = 4) }
                    )
                    deferreds.awaitAll().flatten()
                }

                MediaType.ALL -> {
                    val deferreds = listOf(
                        async { fetchAniListCovers(title, isAnime = true) },
                        async { fetchTvMazeCovers(title) },
                        async { fetchSteamCovers(title) },
                        async { fetchBangumiCovers(title, type = 1) },
                        async { fetchTmdbArtwork(title, isMovie = true) }
                    )
                    deferreds.awaitAll().flatten()
                }
            }
        }

        val normalized = results
            .map { it.copy(url = LosslessCoverMapper.toLosslessUrl(it.url)) }
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }

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

    private fun fetchSteamCovers(title: String): List<ArtworkCandidate> {
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val request = Request.Builder()
                .url("https://store.steampowered.com/api/storesearch/?term=$encoded&l=english&cc=US")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val parsed = json.parseToJsonElement(responseStr).jsonObject
                val items = parsed["items"]?.jsonArray ?: return emptyList()

                val candidates = mutableListOf<ArtworkCandidate>()
                for (elem in items.take(4)) {
                    val id = elem.jsonObject["id"].cleanString() ?: continue
                    val name = elem.jsonObject["name"].cleanString() ?: title

                    val akamai2x = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/library_600x900_2x.jpg"
                    val akamai1x = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/library_600x900.jpg"
                    val heroBanner = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/library_hero.jpg"
                    val headerBanner = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/header.jpg"

                    candidates.add(ArtworkCandidate(url = akamai2x, sourceName = "Steam", description = "$name (Library 2x)", isBanner = false))
                    candidates.add(ArtworkCandidate(url = akamai1x, sourceName = "Steam", description = name, isBanner = false))
                    candidates.add(ArtworkCandidate(url = heroBanner, sourceName = "Steam", description = "$name (Hero Banner)", isBanner = true))
                    candidates.add(ArtworkCandidate(url = headerBanner, sourceName = "Steam", description = "$name (Header)", isBanner = true))

                    try {
                        val dReq = Request.Builder().url("https://store.steampowered.com/api/appdetails?appids=$id").get().build()
                        client.newCall(dReq).execute().use { dResp ->
                            if (dResp.isSuccessful) {
                                val dBody = dResp.body?.string().orEmpty()
                                val dParsed = json.parseToJsonElement(dBody).jsonObject
                                val appData = dParsed[id]?.jsonObject?.get("data")?.jsonObject
                                if (appData != null) {
                                    val headerImg = appData["header_image"].cleanString()
                                    if (!headerImg.isNullOrBlank()) {
                                        candidates.add(ArtworkCandidate(url = headerImg, sourceName = "Steam", description = "$name (Store Header)", isBanner = true))
                                    }
                                    val screenshots = appData["screenshots"]?.jsonArray
                                    screenshots?.take(3)?.forEachIndexed { idx, s ->
                                        val sUrl = s.jsonObject["path_full"].cleanString()
                                        if (!sUrl.isNullOrBlank()) {
                                            candidates.add(ArtworkCandidate(url = sUrl, sourceName = "Steam", description = "$name (Screenshot ${idx + 1})", isBanner = true))
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                candidates
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchVndbCovers(title: String): List<ArtworkCandidate> {
        return try {
            val payload = buildJsonObject {
                put("filters", json.parseToJsonElement("[\"search\", \"=\", \"$title\"]"))
                put("fields", "title, image.url, screenshots.url")
            }

            val request = Request.Builder()
                .url("https://api.vndb.org/kana/vn")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val parsed = json.parseToJsonElement(responseStr).jsonObject
                val results = parsed["results"]?.jsonArray ?: return emptyList()

                val candidates = mutableListOf<ArtworkCandidate>()
                results.forEach { item ->
                    val obj = item.jsonObject
                    val vnTitle = obj["title"].cleanString() ?: title
                    val imageObj = obj["image"] as? JsonObject
                    val coverUrl = imageObj?.get("url").cleanString()
                    if (!coverUrl.isNullOrBlank()) {
                        candidates.add(
                            ArtworkCandidate(
                                url = coverUrl,
                                sourceName = "VNDB",
                                description = vnTitle,
                                isBanner = false
                            )
                        )
                    }

                    val screenshots = obj["screenshots"] as? JsonArray
                    screenshots?.forEachIndexed { idx, shot ->
                        val shotUrl = (shot as? JsonObject)?.get("url").cleanString()
                        if (!shotUrl.isNullOrBlank()) {
                            candidates.add(
                                ArtworkCandidate(
                                    url = shotUrl,
                                    sourceName = "VNDB Screenshot",
                                    description = "$vnTitle (Visual Novel Screenshot ${idx + 1})",
                                    isBanner = true
                                )
                            )
                        }
                    }
                }
                candidates
            }
        } catch (_: Exception) {
            emptyList()
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

    companion object {
        private val candidateCache = ConcurrentHashMap<String, List<ArtworkCandidate>>()

        private val MANGA_COVER_PRIORITY = mapOf(
            "MangaDex" to 1,
            "BookWalker" to 2,
            "Bangumi" to 3,
            "AniList" to 4,
            "Naver" to 5,
            "Kuaikan" to 6,
            "Kitsu" to 7,
            "MyAnimeList" to 8,
            "Comick" to 9,
            "Default" to 10
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
