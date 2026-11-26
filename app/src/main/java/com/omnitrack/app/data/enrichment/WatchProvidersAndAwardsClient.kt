package com.omnitrack.app.data.enrichment

import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.data.sync.TrackerAccountVerifier
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class WatchProviderItem(
    val providerId: Int,
    val providerName: String,
    val logoUrl: String,
    val category: String, // "Stream", "Rent", "Buy", "Free", "Ads"
    val displayPriority: Int
)

data class WatchProviderBundle(
    val regionCode: String,
    val regionName: String,
    val isAutoDetected: Boolean,
    val link: String? = null,
    val flatrate: List<WatchProviderItem> = emptyList(),
    val rent: List<WatchProviderItem> = emptyList(),
    val buy: List<WatchProviderItem> = emptyList(),
    val free: List<WatchProviderItem> = emptyList(),
    val ads: List<WatchProviderItem> = emptyList(),
    val availableRegions: List<String> = emptyList()
) {
    fun isEmpty(): Boolean =
        flatrate.isEmpty() && rent.isEmpty() && buy.isEmpty() && free.isEmpty() && ads.isEmpty()

    fun primaryLogos(): List<WatchProviderItem> =
        (flatrate + free + ads + rent + buy).distinctBy { it.providerId }
}

data class WatchRegionOption(
    val code: String,
    val displayName: String,
    val flagEmoji: String
)

data class AwardHonorItem(
    val id: String,
    val awardQid: String? = null,
    val awardName: String,
    val categoryName: String? = null,
    val year: Int? = null,
    val isWinner: Boolean,
    val source: String, // "Wikidata" | "OMDb"
    val summaryText: String? = null
)

data class AwardWinnerEntry(
    val title: String,
    val workQid: String? = null,
    val imdbId: String? = null,
    val tmdbId: Long? = null,
    val year: Int? = null,
    val categoryName: String? = null,
    val posterUrl: String? = null,
    val mediaType: MediaType = MediaType.MOVIE
)

object WatchProvidersAndAwardsClient {

    private const val DEFAULT_TMDB_V3_KEY = "844dba0bfd8f3a4f3799f6130ef9e335"
    private const val PROVIDER_TTL_MS = 15 * 60 * 1000L

    private data class TimedWatchProviders(val bundle: WatchProviderBundle, val cachedAtMs: Long)
    private data class TimedAwards(val items: List<AwardHonorItem>, val cachedAtMs: Long)
    private data class TimedWinners(val items: List<AwardWinnerEntry>, val cachedAtMs: Long)

    private val providerCache = ConcurrentHashMap<String, TimedWatchProviders>()
    private val awardsCache = ConcurrentHashMap<String, TimedAwards>()
    private val awardWinnersCache = ConcurrentHashMap<String, TimedWinners>()
    private val resolvedImdbCache = ConcurrentHashMap<String, String>()

    val SUPPORTED_WATCH_REGIONS: List<WatchRegionOption> = listOf(
        "AE", "AR", "AT", "AU", "BE", "BG", "BH", "BO", "BR", "CA",
        "CH", "CL", "CO", "CR", "CZ", "DE", "DK", "DO", "EC", "EE",
        "EG", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HR",
        "HU", "ID", "IE", "IL", "IN", "IS", "IT", "JM", "JO", "JP",
        "KR", "KW", "LB", "LT", "LV", "MA", "MX", "MY", "NG", "NL",
        "NO", "NZ", "OM", "PA", "PE", "PH", "PK", "PL", "PT", "PY",
        "QA", "RO", "RS", "RU", "SA", "SE", "SG", "SI", "SK", "TH",
        "TR", "TW", "UA", "US", "UY", "VE", "VN", "ZA"
    ).map { code ->
        WatchRegionOption(
            code = code,
            displayName = regionDisplayName(code),
            flagEmoji = flagForCountry(code)
        )
    }.sortedBy { it.displayName }

    fun regionDisplayName(code: String): String {
        val upper = code.trim().uppercase(Locale.US)
        if (upper.length != 2) return upper
        val name = Locale("", upper).getDisplayCountry(Locale.ENGLISH)
        return if (name.isNullOrBlank()) upper else name
    }

    fun flagForCountry(code: String): String {
        val upper = code.trim().uppercase(Locale.US)
        if (upper.length != 2) return ""
        val first = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
        val second = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    private fun buildTmdbRequest(pathAndQuery: String, tmdbApiKey: String): Request {
        val effectiveKey = tmdbApiKey.trim().ifBlank { DEFAULT_TMDB_V3_KEY }
        return if (TrackerAccountVerifier.isTmdbV4Jwt(effectiveKey)) {
            Request.Builder()
                .url("https://api.themoviedb.org/3/$pathAndQuery")
                .addHeader("Authorization", "Bearer $effectiveKey")
                .addHeader("Accept", "application/json")
                .build()
        } else {
            val sep = if ('?' in pathAndQuery) '&' else '?'
            Request.Builder()
                .url("https://api.themoviedb.org/3/$pathAndQuery${sep}api_key=$effectiveKey")
                .addHeader("Accept", "application/json")
                .build()
        }
    }

    private fun parseProviderList(arr: JSONArray?, category: String): List<WatchProviderItem> {
        if (arr == null) return emptyList()
        val out = ArrayList<WatchProviderItem>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optInt("provider_id", -1)
            val name = obj.optString("provider_name").trim()
            val logoPath = obj.optString("logo_path").trim()
            if (id <= 0 || name.isBlank()) continue
            val logoUrl = if (logoPath.startsWith("/")) {
                "https://image.tmdb.org/t/p/w154$logoPath"
            } else {
                logoPath
            }
            out += WatchProviderItem(
                providerId = id,
                providerName = name,
                logoUrl = logoUrl,
                category = category,
                displayPriority = obj.optInt("display_priority", 999)
            )
        }
        return out.sortedBy { it.displayPriority }
    }

    suspend fun fetchWatchProviders(
        tmdbId: Long,
        isMovie: Boolean,
        tmdbApiKey: String,
        regionCode: String,
        isAutoDetected: Boolean
    ): WatchProviderBundle? = withContext(Dispatchers.IO) {
        if (tmdbId <= 0L) return@withContext null
        val normalizedRegion = regionCode.trim().uppercase(Locale.US).ifBlank { "US" }
        val kind = if (isMovie) "movie" else "tv"
        val cacheKey = "${kind}_${tmdbId}_$normalizedRegion"
        providerCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.cachedAtMs < PROVIDER_TTL_MS) {
                return@withContext cached.bundle.copy(isAutoDetected = isAutoDetected)
            }
        }
        return@withContext runCatching {
            val req = buildTmdbRequest("$kind/$tmdbId/watch/providers", tmdbApiKey)
            val resp = NetworkClient.sharedClient.newCall(req).execute()
            resp.use { r ->
                if (!r.isSuccessful) return@runCatching null
                val raw = r.body?.string().orEmpty()
                if (raw.isBlank()) return@runCatching null
                val resultsObj = JSONObject(raw).optJSONObject("results") ?: return@runCatching null
                val availCodes = resultsObj.keys().asSequence().toList().sorted()
                val regionObj = resultsObj.optJSONObject(normalizedRegion)
                val bundle = WatchProviderBundle(
                    regionCode = normalizedRegion,
                    regionName = regionDisplayName(normalizedRegion),
                    isAutoDetected = isAutoDetected,
                    link = regionObj?.optString("link")?.takeIf { it.isNotBlank() },
                    flatrate = parseProviderList(regionObj?.optJSONArray("flatrate"), "Stream"),
                    rent = parseProviderList(regionObj?.optJSONArray("rent"), "Rent"),
                    buy = parseProviderList(regionObj?.optJSONArray("buy"), "Buy"),
                    free = parseProviderList(regionObj?.optJSONArray("free"), "Free"),
                    ads = parseProviderList(regionObj?.optJSONArray("ads"), "Ads"),
                    availableRegions = availCodes
                )
                providerCache[cacheKey] = TimedWatchProviders(bundle, System.currentTimeMillis())
                bundle
            }
        }.getOrNull()
    }

    suspend fun resolveImdbIdForItem(
        item: MediaItemEntity,
        tmdbApiKey: String
    ): String? = withContext(Dispatchers.IO) {
        item.imdbId?.takeIf { it.startsWith("tt") }?.let { return@withContext it }
        val tmdbId = item.tmdbId?.takeIf { it > 0L } ?: return@withContext null
        val kind = if (item.mediaType == MediaType.MOVIE) "movie" else "tv"
        val key = "${kind}_$tmdbId"
        resolvedImdbCache[key]?.let { return@withContext it }
        return@withContext runCatching {
            val req = buildTmdbRequest("$kind/$tmdbId/external_ids", tmdbApiKey)
            NetworkClient.sharedClient.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                val body = r.body?.string().orEmpty()
                val imdb = JSONObject(body).optString("imdb_id").trim()
                if (imdb.startsWith("tt")) {
                    resolvedImdbCache[key] = imdb
                    imdb
                } else null
            }
        }.getOrNull()
    }

    suspend fun fetchAwardsForItem(
        item: MediaItemEntity,
        tmdbApiKey: String,
        omdbApiKey: String?
    ): List<AwardHonorItem> = withContext(Dispatchers.IO) {
        val cacheKey = "${item.mediaType.name}_${item.id}_${item.tmdbId}_${item.imdbId}_${item.title.lowercase(Locale.US)}"
        awardsCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.cachedAtMs < PROVIDER_TTL_MS * 4) {
                return@withContext cached.items
            }
        }
        val imdbId = resolveImdbIdForItem(item, tmdbApiKey)
        val result = coroutineScope {
            val wikiDeferred = async {
                if (!imdbId.isNullOrBlank()) {
                    queryWikidataAwardsByImdb(imdbId)
                } else {
                    queryWikidataAwardsByTitle(item.title, item.releaseYear)
                }
            }
            val omdbDeferred = async {
                val key = omdbApiKey?.trim().orEmpty()
                if (key.isNotBlank() && (!imdbId.isNullOrBlank() || item.title.isNotBlank())) {
                    queryOmdbAwards(imdbId, item.title, item.releaseYear, key)
                } else emptyList()
            }
            val wikiAwards = wikiDeferred.await()
            val omdbAwards = omdbDeferred.await()
            val merged = (wikiAwards + omdbAwards)
                .distinctBy { "${it.awardName.lowercase(Locale.US)}|${it.year ?: -1}|${it.categoryName?.lowercase(Locale.US).orEmpty()}|${it.isWinner}" }
                .sortedWith(
                    compareByDescending<AwardHonorItem> { it.isWinner }
                        .thenByDescending { it.year ?: 0 }
                        .thenBy { it.awardName }
                )
            merged
        }
        if (result.isNotEmpty()) {
            awardsCache[cacheKey] = TimedAwards(result, System.currentTimeMillis())
        }
        result
    }

    private fun queryWikidataAwardsByImdb(imdbId: String): List<AwardHonorItem> {
        val sparql = """
            SELECT DISTINCT ?award ?awardLabel ?year ?forWorkLabel ?kind WHERE {
              ?work wdt:P345 "$imdbId" .
              {
                ?work p:P166 ?stmt .
                ?stmt ps:P166 ?award .
                BIND("win" AS ?kind)
              } UNION {
                ?work p:P1411 ?stmt .
                ?stmt ps:P1411 ?award .
                BIND("nom" AS ?kind)
              }
              OPTIONAL { ?stmt pq:P585 ?date . BIND(YEAR(?date) AS ?year) }
              OPTIONAL { ?stmt pq:P1686 ?forWork . }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
            } LIMIT 40
        """.trimIndent()
        return executeWikidataAwardsSparql(sparql)
    }

    private fun queryWikidataAwardsByTitle(title: String, year: String?): List<AwardHonorItem> {
        val safeTitle = title.replace("\"", "").trim()
        if (safeTitle.length < 2) return emptyList()
        val yearFilter = year?.take(4)?.toIntOrNull()?.let { y ->
            "OPTIONAL { ?work wdt:P577 ?pub . } FILTER(!BOUND(?pub) || ABS(YEAR(?pub) - $y) <= 2)"
        }.orEmpty()
        val sparql = """
            SELECT DISTINCT ?award ?awardLabel ?year ?forWorkLabel ?kind WHERE {
              ?work rdfs:label "$safeTitle"@en .
              ?work p:P166 ?stmt .
              ?stmt ps:P166 ?award .
              BIND("win" AS ?kind)
              $yearFilter
              OPTIONAL { ?stmt pq:P585 ?date . BIND(YEAR(?date) AS ?year) }
              OPTIONAL { ?stmt pq:P1686 ?forWork . }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
            } LIMIT 25
        """.trimIndent()
        return executeWikidataAwardsSparql(sparql)
    }

    private fun executeWikidataAwardsSparql(sparql: String): List<AwardHonorItem> {
        return runCatching {
            val encoded = URLEncoder.encode(sparql, "UTF-8")
            val req = Request.Builder()
                .url("https://query.wikidata.org/sparql?format=json&query=$encoded")
                .addHeader("Accept", "application/sparql-results+json")
                .addHeader("User-Agent", "OmniTrackAndroid/1.0")
                .build()
            NetworkClient.sharedClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val raw = resp.body?.string().orEmpty()
                val bindings = JSONObject(raw)
                    .optJSONObject("results")
                    ?.optJSONArray("bindings") ?: return@runCatching emptyList()
                val out = ArrayList<AwardHonorItem>(bindings.length())
                for (i in 0 until bindings.length()) {
                    val b = bindings.optJSONObject(i) ?: continue
                    val awardUri = b.optJSONObject("award")?.optString("value").orEmpty()
                    val qid = awardUri.substringAfterLast("/").takeIf { it.startsWith("Q") }
                    val label = b.optJSONObject("awardLabel")?.optString("value").orEmpty().trim()
                    if (label.isBlank() || label.startsWith("Q")) continue
                    val year = b.optJSONObject("year")?.optString("value")?.toIntOrNull()
                    val forWork = b.optJSONObject("forWorkLabel")?.optString("value")?.takeIf {
                        it.isNotBlank() && !it.startsWith("Q")
                    }
                    val isWinner = b.optJSONObject("kind")?.optString("value") == "win"
                    out += AwardHonorItem(
                        id = "${qid ?: label}_${year ?: 0}_$isWinner",
                        awardQid = qid,
                        awardName = label,
                        categoryName = forWork,
                        year = year,
                        isWinner = isWinner,
                        source = "Wikidata"
                    )
                }
                out
            }
        }.getOrDefault(emptyList())
    }

    private fun queryOmdbAwards(
        imdbId: String?,
        title: String,
        year: String?,
        omdbApiKey: String
    ): List<AwardHonorItem> {
        return runCatching {
            val queryParam = if (!imdbId.isNullOrBlank()) {
                "i=${URLEncoder.encode(imdbId, "UTF-8")}"
            } else {
                val yr = year?.take(4)?.toIntOrNull()?.let { "&y=$it" }.orEmpty()
                "t=${URLEncoder.encode(title, "UTF-8")}$yr"
            }
            val req = Request.Builder()
                .url("https://www.omdbapi.com/?$queryParam&apikey=${URLEncoder.encode(omdbApiKey, "UTF-8")}")
                .build()
            NetworkClient.sharedClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val json = JSONObject(resp.body?.string().orEmpty())
                if (json.optString("Response") != "True") return@runCatching emptyList()
                val awardsStr = json.optString("Awards").trim()
                if (awardsStr.isBlank() || awardsStr.equals("N/A", ignoreCase = true)) {
                    return@runCatching emptyList()
                }
                val out = ArrayList<AwardHonorItem>()
                val headlineMatch = Regex("""(Won|Nominated for)\s+(\d+)\s+([A-Za-z& ]+)\.?""").find(awardsStr)
                if (headlineMatch != null) {
                    val isWin = headlineMatch.groupValues[1].equals("Won", ignoreCase = true)
                    val count = headlineMatch.groupValues[2].toIntOrNull() ?: 1
                    val rawAward = headlineMatch.groupValues[3].trim()
                    val qid = guessWellKnownAwardQid(rawAward)
                    out += AwardHonorItem(
                        id = "omdb_head_$rawAward",
                        awardQid = qid,
                        awardName = if (rawAward.contains("Oscar", ignoreCase = true)) "Academy Award" else rawAward,
                        categoryName = "${headlineMatch.groupValues[1]} $count $rawAward",
                        year = null,
                        isWinner = isWin,
                        source = "OMDb",
                        summaryText = awardsStr
                    )
                }
                out += AwardHonorItem(
                    id = "omdb_summary_${awardsStr.hashCode()}",
                    awardQid = null,
                    awardName = "OMDb Awards Summary",
                    categoryName = awardsStr,
                    year = null,
                    isWinner = awardsStr.contains("win", ignoreCase = true) || awardsStr.contains("Won", ignoreCase = true),
                    source = "OMDb",
                    summaryText = awardsStr
                )
                out
            }
        }.getOrDefault(emptyList())
    }

    private fun guessWellKnownAwardQid(name: String): String? {
        val lower = name.lowercase(Locale.US)
        return when {
            "oscar" in lower || "academy" in lower -> "Q19020"
            "emmy" in lower -> "Q123737"
            "golden globe" in lower -> "Q1011547"
            "bafta" in lower -> "Q732997"
            "crunchyroll" in lower -> "Q65083770"
            "palme" in lower || "cannes" in lower -> "Q179808"
            else -> null
        }
    }

    suspend fun fetchAwardWinners(
        awardQid: String?,
        awardName: String,
        preferredMediaType: MediaType,
        tmdbApiKey: String
    ): List<AwardWinnerEntry> = withContext(Dispatchers.IO) {
        val qid = awardQid ?: guessWellKnownAwardQid(awardName) ?: return@withContext emptyList()
        awardWinnersCache[qid]?.let { cached ->
            if (System.currentTimeMillis() - cached.cachedAtMs < PROVIDER_TTL_MS * 6) {
                return@withContext cached.items
            }
        }
        val rawEntries = runCatching {
            val sparql = """
                SELECT DISTINCT ?work ?workLabel ?imdb ?year WHERE {
                  ?work p:P166 ?stmt .
                  ?stmt ps:P166 wd:$qid .
                  ?work wdt:P345 ?imdb .
                  OPTIONAL { ?stmt pq:P585 ?date . BIND(YEAR(?date) AS ?year) }
                  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
                }
                ORDER BY DESC(?year)
                LIMIT 24
            """.trimIndent()
            val encoded = URLEncoder.encode(sparql, "UTF-8")
            val req = Request.Builder()
                .url("https://query.wikidata.org/sparql?format=json&query=$encoded")
                .addHeader("Accept", "application/sparql-results+json")
                .addHeader("User-Agent", "OmniTrackAndroid/1.0")
                .build()
            NetworkClient.sharedClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val bindings = JSONObject(resp.body?.string().orEmpty())
                    .optJSONObject("results")
                    ?.optJSONArray("bindings") ?: return@runCatching emptyList()
                val list = ArrayList<AwardWinnerEntry>(bindings.length())
                for (i in 0 until bindings.length()) {
                    val b = bindings.optJSONObject(i) ?: continue
                    val title = b.optJSONObject("workLabel")?.optString("value").orEmpty().trim()
                    val imdb = b.optJSONObject("imdb")?.optString("value").orEmpty().trim()
                    if (title.isBlank() || title.startsWith("Q") || !imdb.startsWith("tt")) continue
                    val workQid = b.optJSONObject("work")?.optString("value")?.substringAfterLast("/")
                    val year = b.optJSONObject("year")?.optString("value")?.toIntOrNull()
                    list += AwardWinnerEntry(
                        title = title,
                        workQid = workQid,
                        imdbId = imdb,
                        year = year,
                        mediaType = preferredMediaType
                    )
                }
                list.distinctBy { it.imdbId ?: it.title }
            }
        }.getOrDefault(emptyList())

        val enriched = coroutineScope {
            rawEntries.take(18).map { entry ->
                async {
                    val imdb = entry.imdbId ?: return@async entry
                    runCatching {
                        val req = buildTmdbRequest("find/$imdb?external_source=imdb_id", tmdbApiKey)
                        NetworkClient.sharedClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@use entry
                            val obj = JSONObject(resp.body?.string().orEmpty())
                            val movieArr = obj.optJSONArray("movie_results")
                            val tvArr = obj.optJSONArray("tv_results")
                            val m = movieArr?.optJSONObject(0)
                            val tv = tvArr?.optJSONObject(0)
                            when {
                                m != null -> {
                                    val p = m.optString("poster_path").trim()
                                    entry.copy(
                                        tmdbId = m.optLong("id").takeIf { it > 0L },
                                        posterUrl = if (p.startsWith("/")) "https://image.tmdb.org/t/p/w342$p" else null,
                                        mediaType = MediaType.MOVIE,
                                        year = entry.year ?: m.optString("release_date").take(4).toIntOrNull()
                                    )
                                }
                                tv != null -> {
                                    val p = tv.optString("poster_path").trim()
                                    val genres = tv.optJSONArray("genre_ids")
                                    val isAnime = (0 until (genres?.length() ?: 0)).any { genres?.optInt(it) == 16 } &&
                                        tv.optString("original_language") == "ja"
                                    entry.copy(
                                        tmdbId = tv.optLong("id").takeIf { it > 0L },
                                        posterUrl = if (p.startsWith("/")) "https://image.tmdb.org/t/p/w342$p" else null,
                                        mediaType = if (isAnime) MediaType.ANIME else MediaType.SHOW,
                                        year = entry.year ?: tv.optString("first_air_date").take(4).toIntOrNull()
                                    )
                                }
                                else -> entry
                            }
                        }
                    }.getOrDefault(entry)
                }
            }.awaitAll()
        }
        if (enriched.isNotEmpty()) {
            awardWinnersCache[qid] = TimedWinners(enriched, System.currentTimeMillis())
        }
        enriched
    }
}
