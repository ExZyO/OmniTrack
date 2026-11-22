package com.omnitrack.app.data.enrichment

import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.data.search.OmniSearchRepository
import com.omnitrack.app.data.search.SearchResultItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import java.time.LocalDate
import java.time.ZoneId

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
    val formatLabel: String = "ANIME",
    val localMediaId: Long? = null
)

/**
 * Fetches live 7-day airing schedule from AniList GraphQL (safe non-adult, 30-minute memory cache).
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
        val startOfTodaySec = LocalDate.now(zone).atStartOfDay(zone).toEpochSecond()
        val endOfWeekSec = LocalDate.now(zone).plusDays(7).atStartOfDay(zone).toEpochSecond()

        val allItems = mutableListOf<CalendarReleaseItem>()
        for (page in 1..2) {
            val (items, hasNext) = fetchPage(page, startOfTodaySec, endOfWeekSec)
            allItems.addAll(items)
            if (!hasNext || allItems.size >= 80) break
        }
        val comicReleases = runCatching {
            ComicEnrichmentSource(client).fetchWeeklyComicReleases(force = force)
        }.getOrDefault(emptyList())
        allItems.addAll(comicReleases)

        val deduped = allItems
            .distinctBy { "${it.mediaType}_${it.id}_${it.anilistId}_${it.metronId}_${it.episodeNumber}_${it.airingAtEpochSec}" }
            .sortedBy { it.airingAtEpochSec }

        if (deduped.isNotEmpty()) {
            cachedItems = deduped
            cachedAtMs = System.currentTimeMillis()
        }
        deduped
    }

    private suspend fun fetchPage(
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
