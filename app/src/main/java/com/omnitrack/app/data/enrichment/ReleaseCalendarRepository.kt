package com.omnitrack.app.data.enrichment

import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.search.OmniSearchRepository
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.data.sync.TrackerTokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CalendarReleaseItem(
    val id: String,
    val title: String,
    val coverUrl: String,
    val mediaType: MediaType,
    val episodeNumber: Int?,
    val airingAtEpochSec: Long,
    val anilistId: Long? = null,
    val malId: Long? = null,
    val tmdbId: Long? = null,
    val metronId: Long? = null,
    val imdbId: String? = null,
    val vndbId: String? = null,
    val steamAppId: Long? = null,
    val igdbId: Long? = null,
    val hardcoverId: String? = null,
    val mangadexId: String? = null,
    val seasonNumber: Int? = null,
    val chapterLabel: String? = null,
    val isDateOnly: Boolean = false,
    val formatLabel: String = "ANIME",
    val localMediaId: Long? = null,
    val fallbackCoverUrl: String? = null
)

/**
 * Unified 9-vertical 14-day Release Calendar aggregator:
 * Anime (AniList), TV Shows (TVMaze US + Web / TMDb), Movies (TMDb / Cinemeta),
 * Comics (ComicEnrichmentSource), Manga (MangaDex EN), Novels (AniList Releasing LN),
 * Books (Hardcover / Goodreads), Games (IGDB / Steam), Visual Novels (VNDB).
 */
class ReleaseCalendarRepository(
    private val client: OkHttpClient = NetworkClient.sharedClient
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        @Volatile
        private var cachedItems: List<CalendarReleaseItem> = emptyList()

        @Volatile
        private var cachedAtMs: Long = 0L

        @Volatile
        private var cachedTrendingItems: List<SearchResultItem> = emptyList()

        @Volatile
        private var cachedTrendingAtMs: Long = 0L

        private const val CACHE_TTL_MS = 30 * 60 * 1000L
    }

    suspend fun fetchWeeklyAiringSchedule(force: Boolean = false): List<CalendarReleaseItem> = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        if (!force && cachedItems.isNotEmpty() && (nowMs - cachedAtMs) < CACHE_TTL_MS) {
            return@withContext cachedItems
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfTodaySec = today.atStartOfDay(zone).toEpochSecond()
        val endOfWindowSec = today.plusDays(14).atStartOfDay(zone).toEpochSecond()

        val collected = coroutineScope {
            listOf(
                async { runCatching { fetchAniListSchedule(startOfTodaySec, endOfWindowSec) }.getOrDefault(emptyList()) },
                async { runCatching { ComicEnrichmentSource(client).fetchWeeklyComicReleases(force = force) }.getOrDefault(emptyList()) },
                async { runCatching { fetchTvMazeSchedule(today, zone) }.getOrDefault(emptyList()) },
                async { runCatching { fetchMovieReleases(today, zone) }.getOrDefault(emptyList()) },
                async { runCatching { fetchMangaDexRecentChapters(today, zone) }.getOrDefault(emptyList()) },
                async { runCatching { fetchReleasingNovels(today, zone) }.getOrDefault(emptyList()) },
                async { runCatching { fetchUpcomingBooksReleases(today, zone) }.getOrDefault(emptyList()) },
                async { runCatching { fetchGamesReleases(today, startOfTodaySec, endOfWindowSec, zone) }.getOrDefault(emptyList()) },
                async { runCatching { fetchVndbUpcoming(today, zone) }.getOrDefault(emptyList()) }
            ).awaitAll().flatten()
        }

        val deduped = collected
            .distinctBy {
                "${it.mediaType}_${it.id}_${it.anilistId}_${it.tmdbId}_${it.steamAppId}_${it.vndbId}_${it.metronId}_${it.episodeNumber}_${it.airingAtEpochSec / 3600L}"
            }
            .sortedBy { it.airingAtEpochSec }
        val resolved = backfillMissingCalendarCovers(deduped)

        if (resolved.isNotEmpty()) {
            cachedItems = resolved
            cachedAtMs = System.currentTimeMillis()
        }
        resolved
    }

    private suspend fun backfillMissingCalendarCovers(
        items: List<CalendarReleaseItem>
    ): List<CalendarReleaseItem> = coroutineScope {
        val candidates = items.withIndex()
            .filter { (_, item) -> item.coverUrl.isBlank() && item.fallbackCoverUrl.isNullOrBlank() }
            .take(15)
        if (candidates.isEmpty()) return@coroutineScope items

        val fetchedByIndex = candidates.map { (idx, item) ->
            async {
                val resolved = runCatching { resolveSecondaryCalendarCover(item) }.getOrNull()
                idx to resolved
            }
        }.awaitAll().toMap()

        items.mapIndexed { idx, item ->
            val found = fetchedByIndex[idx]
            if (!found.isNullOrBlank() && item.coverUrl.isBlank()) {
                item.copy(coverUrl = LosslessCoverMapper.toLosslessUrl(found))
            } else {
                item
            }
        }
    }

    private suspend fun resolveSecondaryCalendarCover(item: CalendarReleaseItem): String? {
        val cleanTitle = item.title.trim()
        if (cleanTitle.isBlank()) return null
        return when (item.mediaType) {
            MediaType.MANGA, MediaType.ANIME, MediaType.NOVEL -> NetworkPacer.withAniListPacing {
                val alType = if (item.mediaType == MediaType.ANIME) "ANIME" else "MANGA"
                val gql = if (item.mediaType == MediaType.NOVEL) {
                    """
                        query (${'$'}search: String, ${'$'}type: MediaType) {
                            Media(search: ${'$'}search, type: ${'$'}type, format: NOVEL) {
                                coverImage { extraLarge large }
                            }
                        }
                    """.trimIndent()
                } else {
                    """
                        query (${'$'}search: String, ${'$'}type: MediaType) {
                            Media(search: ${'$'}search, type: ${'$'}type) {
                                coverImage { extraLarge large }
                            }
                        }
                    """.trimIndent()
                }
                val payload = buildJsonObject {
                    put("query", gql)
                    putJsonObject("variables") {
                        put("search", cleanTitle)
                        put("type", alType)
                    }
                }.toString()
                val req = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .header("User-Agent", "OmniTrack/1.0")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withAniListPacing null
                    val cov = ((json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                        ?.get("data") as? JsonObject)
                        ?.get("Media")?.let { it as? JsonObject }
                        ?.get("coverImage")?.let { it as? JsonObject }
                    listOf("extraLarge", "large").firstNotNullOfOrNull { k ->
                        (cov?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
                    }
                }
            }
            MediaType.BOOK, MediaType.COMIC -> NetworkPacer.withGeneralPacing {
                val encoded = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
                val req = Request.Builder()
                    .url("https://itunes.apple.com/search?term=$encoded&media=ebook&limit=1")
                    .header("User-Agent", "OmniTrack/1.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withGeneralPacing null
                    val arr = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                        ?.get("results") as? JsonArray ?: return@withGeneralPacing null
                    val first = arr.firstOrNull() as? JsonObject ?: return@withGeneralPacing null
                    val art = (first["artworkUrl100"] as? JsonPrimitive)?.contentOrNull
                        ?: (first["artworkUrl60"] as? JsonPrimitive)?.contentOrNull
                    art?.replace(Regex("\\d+x\\d+bb"), "600x600bb")
                }
            }
            MediaType.GAME -> NetworkPacer.withSteamPacing {
                val encoded = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
                val req = Request.Builder()
                    .url("https://store.steampowered.com/api/storesearch/?term=$encoded&l=en&cc=us")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withSteamPacing null
                    val arr = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                        ?.get("items") as? JsonArray ?: return@withSteamPacing null
                    val first = arr.firstOrNull() as? JsonObject ?: return@withSteamPacing null
                    val appId = (first["id"] as? JsonPrimitive)?.longOrNull
                    val tiny = (first["tiny_image"] as? JsonPrimitive)?.contentOrNull
                    appId?.let { "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$it/header.jpg" }
                        ?: tiny
                }
            }
            MediaType.SHOW, MediaType.MOVIE -> item.imdbId?.takeIf { it.isNotBlank() }?.let { imdb ->
                NetworkPacer.withGeneralPacing {
                    val cinType = if (item.mediaType == MediaType.MOVIE) "movie" else "series"
                    val req = Request.Builder()
                        .url("https://v3-cinemeta.strem.io/meta/$cinType/$imdb.json")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withGeneralPacing null
                        val meta = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                            ?.get("meta") as? JsonObject
                        (meta?.get("poster") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    }
                }
            }
            else -> null
        }
    }

    private suspend fun fetchAniListSchedule(startSec: Long, endSec: Long): List<CalendarReleaseItem> {
        val out = mutableListOf<CalendarReleaseItem>()
        for (page in 1..2) {
            val (items, hasNext) = fetchAniListPage(page, startSec, endSec)
            out.addAll(items)
            if (!hasNext || out.size >= 80) break
        }
        return out
    }

    private suspend fun fetchAniListPage(
        page: Int,
        startSec: Long,
        endSec: Long
    ): Pair<List<CalendarReleaseItem>, Boolean> = NetworkPacer.withAniListPacing {
        try {
            val query = """
                query (${'$'}page: Int, ${'$'}start: Int, ${'$'}end: Int) {
                    Page(page: ${'$'}page, perPage: 50) {
                        pageInfo {
                            hasNextPage
                        }
                        airingSchedules(airingAt_greater: ${'$'}start, airingAt_lesser: ${'$'}end, sort: TIME) {
                            id
                            airingAt
                            episode
                            media {
                                id
                                idMal
                                isAdult
                                format
                                title {
                                    english
                                    romaji
                                    native
                                }
                                coverImage {
                                    large
                                }
                            }
                        }
                    }
                }
            """.trimIndent()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("page", page)
                    put("start", startSec.toInt())
                    put("end", endSec.toInt())
                }
            }.toString()

            val req = Request.Builder()
                .url("https://graphql.anilist.co")
                .header("User-Agent", "OmniTrack/1.0")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withAniListPacing emptyList<CalendarReleaseItem>() to false
                val root = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                    ?.get("data") as? JsonObject ?: return@withAniListPacing emptyList<CalendarReleaseItem>() to false
                val pageObj = root["Page"] as? JsonObject ?: return@withAniListPacing emptyList<CalendarReleaseItem>() to false
                val hasNext = ((pageObj["pageInfo"] as? JsonObject)?.get("hasNextPage") as? JsonPrimitive)
                    ?.takeIf { it !is JsonNull }?.booleanOrNull ?: false
                val schedules = pageObj["airingSchedules"] as? JsonArray ?: return@withAniListPacing emptyList<CalendarReleaseItem>() to false

                val parsed = schedules.mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    val media = o["media"] as? JsonObject ?: return@mapNotNull null
                    val isAdult = (media["isAdult"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull ?: false
                    if (isAdult) return@mapNotNull null

                    val scheduleId = (o["id"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull ?: return@mapNotNull null
                    val airingAt = (o["airingAt"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull ?: return@mapNotNull null
                    val ep = (o["episode"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
                    val alId = (media["id"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
                    val malId = (media["idMal"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
                    val format = (media["format"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull ?: "TV"
                    val titleObj = media["title"] as? JsonObject
                    val title = listOf("english", "romaji", "native").firstNotNullOfOrNull { k ->
                        (titleObj?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                    } ?: return@mapNotNull null
                    val cover = ((media["coverImage"] as? JsonObject)?.get("large") as? JsonPrimitive)
                        ?.takeIf { it !is JsonNull }?.contentOrNull.orEmpty()
                        .let { if (it.isNotBlank()) LosslessCoverMapper.toLosslessUrl(it) else "" }

                    CalendarReleaseItem(
                        id = "al_sched_$scheduleId",
                        title = title,
                        coverUrl = cover,
                        mediaType = MediaType.ANIME,
                        episodeNumber = ep,
                        airingAtEpochSec = airingAt,
                        anilistId = alId,
                        malId = malId,
                        formatLabel = format
                    )
                }
                parsed to hasNext
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList<CalendarReleaseItem>() to false
        }
    }

    private suspend fun fetchTvMazeSchedule(today: LocalDate, zone: ZoneId): List<CalendarReleaseItem> {
        val dates = (0L..3L).map { today.plusDays(it) }
        val results = mutableListOf<CalendarReleaseItem>()
        for (day in dates) {
            val dateStr = day.format(DateTimeFormatter.ISO_LOCAL_DATE)
            listOf(
                "https://api.tvmaze.com/schedule?country=US&date=$dateStr",
                "https://api.tvmaze.com/schedule/web?date=$dateStr"
            ).forEach { url ->
                val batch = NetworkPacer.withGeneralPacing {
                    try {
                        val req = Request.Builder().url(url).header("User-Agent", "OmniTrack/1.0").get().build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                            val arr = json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonArray
                                ?: return@withGeneralPacing emptyList()
                            arr.take(25).mapNotNull { el ->
                                val epObj = el as? JsonObject ?: return@mapNotNull null
                                val showObj = (epObj["show"] as? JsonObject)
                                    ?: ((epObj["_embedded"] as? JsonObject)?.get("show") as? JsonObject)
                                    ?: return@mapNotNull null
                                val showName = (showObj["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                                    ?: return@mapNotNull null
                                val showImg = ((showObj["image"] as? JsonObject)?.get("original") as? JsonPrimitive)?.contentOrNull
                                    ?: ((showObj["image"] as? JsonObject)?.get("medium") as? JsonPrimitive)?.contentOrNull
                                    ?: return@mapNotNull null
                                val season = (epObj["season"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
                                val number = (epObj["number"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
                                val airstamp = (epObj["airstamp"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
                                val epochSec = airstamp?.let {
                                    runCatching { Instant.parse(it).epochSecond }.getOrNull()
                                } ?: day.atTime(20, 0).atZone(zone).toEpochSecond()
                                val imdb = ((showObj["externals"] as? JsonObject)?.get("imdb") as? JsonPrimitive)?.contentOrNull
                                val epId = (epObj["id"] as? JsonPrimitive)?.contentOrNull ?: "${showName}_$dateStr"
                                CalendarReleaseItem(
                                    id = "tvm_$epId",
                                    title = showName,
                                    coverUrl = LosslessCoverMapper.toLosslessUrl(showImg),
                                    mediaType = MediaType.SHOW,
                                    episodeNumber = number,
                                    seasonNumber = season,
                                    airingAtEpochSec = epochSec,
                                    imdbId = imdb,
                                    isDateOnly = airstamp == null,
                                    formatLabel = (showObj["type"] as? JsonPrimitive)?.contentOrNull ?: "Series"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        emptyList()
                    }
                }
                results.addAll(batch)
            }
        }
        return results.distinctBy { "${it.title.lowercase()}_${it.seasonNumber}_${it.episodeNumber}" }.take(40)
    }

    private suspend fun fetchMovieReleases(today: LocalDate, zone: ZoneId): List<CalendarReleaseItem> {
        val tmdbKey = PreferenceManager.cachedTmdbApiKey
        if (tmdbKey.isNotBlank()) {
            val startIso = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val endIso = today.plusDays(13).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val tmdbMovies = NetworkPacer.withTmdbPacing {
                try {
                    val req = Request.Builder()
                        .url("https://api.themoviedb.org/3/discover/movie?api_key=$tmdbKey&primary_release_date.gte=$startIso&primary_release_date.lte=$endIso&sort_by=popularity.desc&page=1")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withTmdbPacing emptyList()
                        val arr = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                            ?.get("results") as? JsonArray ?: return@withTmdbPacing emptyList()
                        arr.take(20).mapNotNull { el ->
                            val o = el as? JsonObject ?: return@mapNotNull null
                            val id = (o["id"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull ?: return@mapNotNull null
                            val title = (o["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            val poster = (o["poster_path"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            val relDateStr = (o["release_date"] as? JsonPrimitive)?.contentOrNull
                            val parsedDate = runCatching { LocalDate.parse(relDateStr) }.getOrNull() ?: today
                            CalendarReleaseItem(
                                id = "tmdb_mov_$id",
                                title = title,
                                coverUrl = LosslessCoverMapper.toLosslessUrl("https://image.tmdb.org/t/p/original$poster"),
                                mediaType = MediaType.MOVIE,
                                episodeNumber = null,
                                airingAtEpochSec = parsedDate.atTime(9, 0).atZone(zone).toEpochSecond(),
                                tmdbId = id,
                                isDateOnly = true,
                                formatLabel = "Theaters / Digital"
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    emptyList()
                }
            }
            if (tmdbMovies.isNotEmpty()) return tmdbMovies
        }
        // Keyless Cinemeta fallback
        return NetworkPacer.withGeneralPacing {
            try {
                val req = Request.Builder()
                    .url("https://v3-cinemeta.strem.io/catalog/movie/top.json")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                    val metas = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                        ?.get("metas") as? JsonArray ?: return@withGeneralPacing emptyList()
                    metas.take(12).mapIndexedNotNull { idx, el ->
                        val o = el as? JsonObject ?: return@mapIndexedNotNull null
                        val title = (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapIndexedNotNull null
                        val poster = (o["poster"] as? JsonPrimitive)?.contentOrNull ?: return@mapIndexedNotNull null
                        val imdb = ((o["imdb_id"] as? JsonPrimitive)?.contentOrNull
                            ?: (o["id"] as? JsonPrimitive)?.contentOrNull)?.takeIf { it.startsWith("tt") }
                        val releaseDay = today.plusDays((idx % 7).toLong())
                        CalendarReleaseItem(
                            id = "cin_mov_${imdb ?: idx}",
                            title = title,
                            coverUrl = LosslessCoverMapper.toLosslessUrl(poster.replace("/small/", "/medium/")),
                            mediaType = MediaType.MOVIE,
                            episodeNumber = null,
                            airingAtEpochSec = releaseDay.atTime(10, 0).atZone(zone).toEpochSecond(),
                            imdbId = imdb,
                            isDateOnly = true,
                            formatLabel = (o["releaseInfo"] as? JsonPrimitive)?.contentOrNull ?: "Featured"
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }
    }

    private suspend fun fetchMangaDexRecentChapters(today: LocalDate, zone: ZoneId): List<CalendarReleaseItem> =
        NetworkPacer.withMangaDexPacing {
            try {
                val req = Request.Builder()
                    .url("https://api.mangadex.org/chapter?limit=35&translatedLanguage[]=en&order[readableAt]=desc&includes[]=manga&includes[]=cover_art&contentRating[]=safe&contentRating[]=suggestive")
                    .header("User-Agent", "OmniTrack/1.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withMangaDexPacing emptyList()
                    val data = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                        ?.get("data") as? JsonArray ?: return@withMangaDexPacing emptyList()
                    val rawChapters = data.mapNotNull { el ->
                        val ch = el as? JsonObject ?: return@mapNotNull null
                        val chId = (ch["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                        val attr = ch["attributes"] as? JsonObject ?: return@mapNotNull null
                        val chNumStr = (attr["chapter"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
                        val readableStr = (attr["readableAt"] as? JsonPrimitive)?.contentOrNull
                            ?: (attr["publishAt"] as? JsonPrimitive)?.contentOrNull
                        val epochSec = readableStr?.let {
                            runCatching { Instant.parse(it).epochSecond }.getOrNull()
                        } ?: today.atTime(12, 0).atZone(zone).toEpochSecond()

                        val rels = ch["relationships"] as? JsonArray ?: return@mapNotNull null
                        val mangaRel = rels.firstOrNull { (it as? JsonObject)?.get("type")?.let { t -> (t as? JsonPrimitive)?.contentOrNull } == "manga" } as? JsonObject
                        val mangaId = (mangaRel?.get("id") as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                        val mangaAttr = mangaRel["attributes"] as? JsonObject
                        val titleMap = mangaAttr?.get("title") as? JsonObject
                        val title = titleMap?.let { tm ->
                            listOf("en", "ja-ro", "ja").firstNotNullOfOrNull { k ->
                                (tm[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
                            } ?: tm.values.firstNotNullOfOrNull { v ->
                                (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
                            }
                        } ?: return@mapNotNull null

                        val coverFile = (rels.firstOrNull {
                            ((it as? JsonObject)?.get("type") as? JsonPrimitive)?.contentOrNull == "cover_art"
                        } as? JsonObject)?.get("attributes")?.let { it as? JsonObject }?.get("fileName")?.let { (it as? JsonPrimitive)?.contentOrNull }
                        val coverUrl = if (!coverFile.isNullOrBlank()) {
                            "https://uploads.mangadex.org/covers/$mangaId/$coverFile"
                        } else ""

                        CalendarReleaseItem(
                            id = "md_ch_$chId",
                            title = title,
                            coverUrl = coverUrl,
                            mediaType = MediaType.MANGA,
                            episodeNumber = chNumStr?.toFloatOrNull()?.toInt(),
                            chapterLabel = chNumStr,
                            airingAtEpochSec = epochSec,
                            mangadexId = mangaId,
                            formatLabel = "MangaDex"
                        )
                    }.distinctBy { "${it.mangadexId ?: it.title.lowercase()}_${it.chapterLabel}" }

                    // MangaDex chapter?includes[]=cover_art does not populate manga cover_art relationships;
                    // batch-fetch the manga covers directly for any chapter items missing coverUrl.
                    val missingIds = rawChapters.filter { it.coverUrl.isBlank() && !it.mangadexId.isNullOrBlank() }
                        .mapNotNull { it.mangadexId }
                        .distinct()
                    if (missingIds.isEmpty()) return@withMangaDexPacing rawChapters

                    val coverMap = HashMap<String, String>()
                    missingIds.chunked(40).forEach { chunk ->
                        val idsQuery = chunk.joinToString("&") { "ids[]=$it" }
                        val mangaReq = Request.Builder()
                            .url("https://api.mangadex.org/manga?limit=100&$idsQuery&includes[]=cover_art")
                            .header("User-Agent", "OmniTrack/1.0")
                            .get()
                            .build()
                        runCatching {
                            client.newCall(mangaReq).execute().use { mResp ->
                                if (mResp.isSuccessful) {
                                    val mArr = (json.parseToJsonElement(mResp.body?.string().orEmpty()) as? JsonObject)
                                        ?.get("data") as? JsonArray ?: return@use
                                    mArr.forEach { mEl ->
                                        val mObj = mEl as? JsonObject ?: return@forEach
                                        val mId = (mObj["id"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                                        val mRels = mObj["relationships"] as? JsonArray ?: return@forEach
                                        val fileName = (mRels.firstOrNull {
                                            ((it as? JsonObject)?.get("type") as? JsonPrimitive)?.contentOrNull == "cover_art"
                                        } as? JsonObject)?.get("attributes")?.let { it as? JsonObject }?.get("fileName")?.let {
                                            (it as? JsonPrimitive)?.contentOrNull
                                        }
                                        if (!fileName.isNullOrBlank()) {
                                            coverMap[mId] = "https://uploads.mangadex.org/covers/$mId/$fileName"
                                        }
                                    }
                                }
                            }
                        }
                    }
                    rawChapters.map { item ->
                        val resolved = item.mangadexId?.let { coverMap[it] }
                        if (item.coverUrl.isBlank() && !resolved.isNullOrBlank()) item.copy(coverUrl = resolved) else item
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

    private suspend fun fetchReleasingNovels(today: LocalDate, zone: ZoneId): List<CalendarReleaseItem> =
        NetworkPacer.withAniListPacing {
            try {
                val gql = """
                    query {
                        Page(page: 1, perPage: 16) {
                            media(type: MANGA, format: NOVEL, status: RELEASING, sort: UPDATED_AT_DESC, isAdult: false) {
                                id
                                idMal
                                title { english romaji native }
                                coverImage { extraLarge large }
                            }
                        }
                    }
                """.trimIndent()
                val req = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .header("User-Agent", "OmniTrack/1.0")
                    .post(buildJsonObject { put("query", gql) }.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withAniListPacing emptyList()
                    val arr = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                        ?.get("data")?.let { it as? JsonObject }
                        ?.get("Page")?.let { it as? JsonObject }
                        ?.get("media") as? JsonArray ?: return@withAniListPacing emptyList()
                    arr.mapIndexedNotNull { idx, el ->
                        val m = el as? JsonObject ?: return@mapIndexedNotNull null
                        val alId = (m["id"] as? JsonPrimitive)?.longOrNull ?: return@mapIndexedNotNull null
                        val malId = (m["idMal"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
                        val titleObj = m["title"] as? JsonObject
                        val title = listOf("english", "romaji", "native").firstNotNullOfOrNull { k ->
                            (titleObj?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                        } ?: return@mapIndexedNotNull null
                        val coverObj = m["coverImage"] as? JsonObject
                        val cover = listOf("extraLarge", "large").firstNotNullOfOrNull { k ->
                            (coverObj?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                        }.orEmpty()
                        val day = today.plusDays((idx % 7).toLong())
                        CalendarReleaseItem(
                            id = "al_novel_$alId",
                            title = title,
                            coverUrl = if (cover.isNotBlank()) LosslessCoverMapper.toLosslessUrl(cover) else "",
                            mediaType = MediaType.NOVEL,
                            episodeNumber = null,
                            airingAtEpochSec = day.atTime(9, 0).atZone(zone).toEpochSecond(),
                            anilistId = alId,
                            malId = malId,
                            isDateOnly = true,
                            formatLabel = "Ongoing Novel"
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

    private suspend fun fetchUpcomingBooksReleases(today: LocalDate, zone: ZoneId): List<CalendarReleaseItem> {
        // Parse keyless Goodreads popular_by_date __NEXT_DATA__ JSON for current month's hot releases
        return NetworkPacer.withGeneralPacing {
            try {
                val url = "https://www.goodreads.com/book/popular_by_date/${today.year}/${today.monthValue}"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                    val html = resp.body?.string().orEmpty()
                    val match = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
                        .find(html)?.groupValues?.getOrNull(1) ?: return@withGeneralPacing emptyList()
                    val root = json.parseToJsonElement(match) as? JsonObject ?: return@withGeneralPacing emptyList()
                    val apollo = root["props"]?.let { it as? JsonObject }
                        ?.get("pageProps")?.let { it as? JsonObject }
                        ?.get("apolloState") as? JsonObject ?: return@withGeneralPacing emptyList()
                    apollo.entries
                        .filter { it.key.startsWith("Book:") }
                        .take(16)
                        .mapIndexedNotNull { idx, (key, el) ->
                            val o = el as? JsonObject ?: return@mapIndexedNotNull null
                            val title = (o["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                                ?: return@mapIndexedNotNull null
                            val rawImg = (o["imageUrl"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                            val img = if (rawImg.contains("nophoto", ignoreCase = true)) "" else rawImg
                            val day = today.plusDays((idx % 10).toLong())
                            CalendarReleaseItem(
                                id = "gr_${key.removePrefix("Book:")}",
                                title = title,
                                coverUrl = img,
                                mediaType = MediaType.BOOK,
                                episodeNumber = null,
                                airingAtEpochSec = day.atTime(8, 0).atZone(zone).toEpochSecond(),
                                isDateOnly = true,
                                formatLabel = "Monthly Book Release"
                            )
                        }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }
    }

    private suspend fun fetchGamesReleases(
        today: LocalDate,
        startSec: Long,
        endSec: Long,
        zone: ZoneId
    ): List<CalendarReleaseItem> {
        val igdbId = PreferenceManager.cachedIgdbClientId
        val igdbSecret = PreferenceManager.cachedIgdbClientSecret
        if (igdbId.isNotBlank() && igdbSecret.isNotBlank()) {
            val igdbReleases = runCatching {
                IgdbApiClient(client).fetchUpcomingReleases(igdbId, igdbSecret, startSec, endSec)
            }.getOrDefault(emptyList())
            if (igdbReleases.isNotEmpty()) return igdbReleases
        }
        // Keyless Steam Featured Categories (new_releases + coming_soon)
        return NetworkPacer.withSteamPacing {
            try {
                val req = Request.Builder()
                    .url("https://store.steampowered.com/api/featuredcategories/?cc=us&l=en")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withSteamPacing emptyList()
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject
                        ?: return@withSteamPacing emptyList()
                    val out = mutableListOf<CalendarReleaseItem>()
                    listOf("coming_soon" to true, "new_releases" to false).forEach { (cat, futureOffset) ->
                        val arr = (root[cat] as? JsonObject)?.get("items") as? JsonArray ?: return@forEach
                        arr.take(10).forEachIndexed { idx, el ->
                            val o = el as? JsonObject ?: return@forEachIndexed
                            val appId = (o["id"] as? JsonPrimitive)?.longOrNull ?: return@forEachIndexed
                            val name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@forEachIndexed
                            val fallbackCapsule = (o["large_capsule_image"] as? JsonPrimitive)?.contentOrNull
                                ?: (o["header_image"] as? JsonPrimitive)?.contentOrNull
                                ?: "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg"
                            val day = if (futureOffset) today.plusDays(((idx % 6) + 1).toLong()) else today
                            out.add(
                                CalendarReleaseItem(
                                    id = "steam_${cat}_$appId",
                                    title = name,
                                    coverUrl = LosslessCoverMapper.toLosslessUrl("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/library_600x900_2x.jpg"),
                                    fallbackCoverUrl = fallbackCapsule,
                                    mediaType = MediaType.GAME,
                                    episodeNumber = null,
                                    airingAtEpochSec = day.atTime(10, 0).atZone(zone).toEpochSecond(),
                                    steamAppId = appId,
                                    isDateOnly = true,
                                    formatLabel = if (futureOffset) "Steam Coming Soon" else "Steam New Release"
                                )
                            )
                        }
                    }
                    out.distinctBy { it.steamAppId }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }
    }

    private suspend fun fetchVndbUpcoming(today: LocalDate, zone: ZoneId): List<CalendarReleaseItem> =
        NetworkPacer.withVndbPacing {
            try {
                val startIso = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val endIso = today.plusDays(13).format(DateTimeFormatter.ISO_LOCAL_DATE)
                val payload = buildJsonObject {
                    put("filters", json.parseToJsonElement("""["and", ["released", ">=", "$startIso"], ["released", "<=", "$endIso"]]"""))
                    put("fields", "id, title, released, image{url,sexual,violence}")
                    put("sort", "released")
                    put("results", 16)
                }.toString()
                val req = Request.Builder()
                    .url("https://api.vndb.org/kana/vn")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withVndbPacing emptyList()
                    val results = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                        ?.get("results") as? JsonArray ?: return@withVndbPacing emptyList()
                    results.mapNotNull { el ->
                        val o = el as? JsonObject ?: return@mapNotNull null
                        val vId = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                        val title = (o["title"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                        val relStr = (o["released"] as? JsonPrimitive)?.contentOrNull
                        val relDate = runCatching { LocalDate.parse(relStr) }.getOrNull() ?: today
                        val imgObj = o["image"] as? JsonObject
                        val cover = (imgObj?.get("url") as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull.orEmpty()
                        CalendarReleaseItem(
                            id = "vndb_$vId",
                            title = title,
                            coverUrl = if (cover.isNotBlank()) LosslessCoverMapper.toLosslessUrl(cover) else "",
                            mediaType = MediaType.VISUAL_NOVEL,
                            episodeNumber = null,
                            airingAtEpochSec = relDate.atTime(9, 0).atZone(zone).toEpochSecond(),
                            vndbId = vId,
                            isDateOnly = true,
                            formatLabel = "VNDB Release"
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

    suspend fun fetchTrendingAniList(force: Boolean = false): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        if (!force && cachedTrendingItems.isNotEmpty() && (nowMs - cachedTrendingAtMs) < CACHE_TTL_MS) {
            return@withContext cachedTrendingItems
        }

        val items = NetworkPacer.withAniListPacing {
            try {
                val query = """
                    query {
                        Page(page: 1, perPage: 12) {
                            media(sort: TRENDING_DESC, isAdult: false) {
                                id
                                idMal
                                type
                                format
                                title {
                                    english
                                    romaji
                                    native
                                }
                                coverImage {
                                    extraLarge
                                    large
                                }
                                bannerImage
                                description(asHtml: false)
                                averageScore
                                episodes
                                chapters
                                startDate {
                                    year
                                }
                            }
                        }
                    }
                """.trimIndent()
                val payload = buildJsonObject {
                    put("query", query)
                }.toString()
                val req = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .header("User-Agent", "OmniTrack/1.0")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withAniListPacing emptyList()
                    val root = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                        ?.get("data") as? JsonObject ?: return@withAniListPacing emptyList()
                    val mediaArr = (root["Page"] as? JsonObject)?.get("media") as? JsonArray
                        ?: return@withAniListPacing emptyList()
                    mediaArr.mapNotNull { el ->
                        val m = el as? JsonObject ?: return@mapNotNull null
                        val alId = (m["id"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull ?: return@mapNotNull null
                        val malId = (m["idMal"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
                        val typeStr = (m["type"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
                        val formatStr = (m["format"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
                        val mediaType = when {
                            typeStr == "MANGA" && formatStr == "NOVEL" -> MediaType.NOVEL
                            typeStr == "MANGA" -> MediaType.MANGA
                            else -> MediaType.ANIME
                        }
                        val titleObj = m["title"] as? JsonObject
                        val title = listOf("english", "romaji", "native").firstNotNullOfOrNull { k ->
                            (titleObj?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                        } ?: return@mapNotNull null
                        val nativeTitle = (titleObj?.get("native") as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
                        val coverObj = m["coverImage"] as? JsonObject
                        val coverUrl = listOf("extraLarge", "large").firstNotNullOfOrNull { k ->
                            (coverObj?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                        }?.let { LosslessCoverMapper.toLosslessUrl(it) } ?: return@mapNotNull null
                        val bannerUrl = (m["bannerImage"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
                            ?.takeIf { it.isNotBlank() }?.let { LosslessCoverMapper.toLosslessUrl(it) }
                        val synopsis = (m["description"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
                            ?.let { OmniSearchRepository.cleanSynopsis(it) }
                        val avgScore = (m["averageScore"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
                            ?.let { it / 10f }
                        val total = when (mediaType) {
                            MediaType.ANIME -> (m["episodes"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull?.toDouble()
                            else -> (m["chapters"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull?.toDouble()
                        }
                        val year = ((m["startDate"] as? JsonObject)?.get("year") as? JsonPrimitive)
                            ?.takeIf { it !is JsonNull }?.intOrNull?.toString()
                        SearchResultItem(
                            title = title,
                            nativeTitle = nativeTitle,
                            synopsis = synopsis,
                            coverUrl = coverUrl,
                            bannerUrl = bannerUrl,
                            mediaType = mediaType,
                            releaseYear = year,
                            communityScore = avgScore,
                            sourceProvider = "AniList",
                            anilistId = alId,
                            malId = malId,
                            totalProgress = total
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }
        if (items.isNotEmpty()) {
            cachedTrendingItems = items
            cachedTrendingAtMs = System.currentTimeMillis()
        }
        items
    }
}
