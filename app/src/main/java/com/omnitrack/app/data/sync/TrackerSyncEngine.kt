package com.omnitrack.app.data.sync

import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.dao.SyncQueueDao
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.SyncQueueEntity
import com.omnitrack.app.data.enrichment.HardcoverApiClient
import com.omnitrack.app.data.enrichment.IgdbApiClient
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class SteamImportSummary(
    val imported: Int,
    val updated: Int,
    val error: String? = null
)

data class TrackerImportResult(
    val imported: Int,
    val updated: Int,
    val skipped: Int,
    val error: String? = null
)

@Serializable
data class TrackerSyncOutcome(
    val trackerKey: String,
    val success: Boolean,
    val httpCode: Int? = null,
    val message: String
)

@Serializable
data class RemoteTrackerEntrySnapshot(
    val trackerKey: String,
    val remoteId: String,
    val title: String,
    val coverUrl: String? = null,
    val format: String? = null,
    val year: String? = null,
    val remoteStatus: TrackingStatus? = null,
    val remoteProgress: Double? = null,
    val remoteTotal: Double? = null,
    val remoteVolumes: Int? = null,
    val remoteScore10: Float? = null,
    val remoteNote: String? = null,
    val isFavorite: Boolean? = null,
    val rewatches: Int? = null,
    val startedAtMs: Long? = null,
    val finishedAtMs: Long? = null,
    val lastSyncedMs: Long = System.currentTimeMillis()
)

@Serializable
data class TrackerOverrideConfig(
    val useCustomNote: Boolean = false,
    val customNote: String? = null,
    val episodeOffset: Int = 0
)

object TrackerMetadataCodec {
    private val codecJson = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    fun decodeMatchedSnapshots(raw: String?): Map<String, RemoteTrackerEntrySnapshot> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { codecJson.decodeFromString<Map<String, RemoteTrackerEntrySnapshot>>(raw) }
            .getOrDefault(emptyMap())
    }

    fun encodeMatchedSnapshots(map: Map<String, RemoteTrackerEntrySnapshot>): String? {
        if (map.isEmpty()) return null
        return runCatching { codecJson.encodeToString(map) }.getOrNull()
    }

    fun decodeOverrides(raw: String?): Map<String, TrackerOverrideConfig> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { codecJson.decodeFromString<Map<String, TrackerOverrideConfig>>(raw) }
            .getOrDefault(emptyMap())
    }

    fun encodeOverrides(map: Map<String, TrackerOverrideConfig>): String? {
        val pruned = map.filterValues { it.useCustomNote || !it.customNote.isNullOrBlank() || it.episodeOffset != 0 }
        if (pruned.isEmpty()) return null
        return runCatching { codecJson.encodeToString(pruned) }.getOrNull()
    }

    fun resolveEffectiveNote(item: MediaItemEntity, trackerKey: String, maxChars: Int): String? {
        val ov = decodeOverrides(item.trackerOverridesJson)[trackerKey.uppercase()]
        val legacy = when (trackerKey.uppercase()) {
            "ANILIST" -> item.anilistNotes
            "MAL" -> item.malComments
            "BANGUMI" -> item.bangumiComment
            "SIMKL" -> item.simklMemo
            else -> null
        }
        val raw = if (ov?.useCustomNote == true) {
            ov.customNote?.takeIf { it.isNotBlank() } ?: legacy
        } else {
            item.personalNotes?.takeIf { it.isNotBlank() } ?: legacy
        }?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (maxChars > 0) raw.take(maxChars) else raw
    }

    fun resolveEffectiveProgress(item: MediaItemEntity, trackerKey: String, overrideProgress: Double? = null): Int {
        val base = overrideProgress ?: item.currentProgress
        val offset = decodeOverrides(item.trackerOverridesJson)[trackerKey.uppercase()]?.episodeOffset ?: 0
        return (base.toInt() + offset).coerceAtLeast(0)
    }
}

/**
 * Background multi-tracker synchronization engine.
 * Drains the offline-first [SyncQueueDao] queue asynchronously through [NetworkPacer].
 */
class TrackerSyncEngine(
    private val repository: MediaRepository,
    private val syncQueueDao: SyncQueueDao,
    private val tokenManager: TrackerTokenManager,
    private val client: OkHttpClient = com.omnitrack.app.data.network.NetworkClient.sharedClient
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val pushMutex = Mutex()

    suspend fun enqueueProgressUpdate(
        item: MediaItemEntity,
        newProgress: Double
    ) = withContext(Dispatchers.IO) {
        if (item.isCustomEntry &&
            item.anilistId == null &&
            item.malId == null &&
            item.traktId == null &&
            item.simklId == null &&
            item.bangumiId == null &&
            item.vndbId == null &&
            item.tmdbId == null &&
            item.hardcoverId.isNullOrBlank()
        ) {
            return@withContext
        }

        val tokens = tokenManager.tokens.firstOrNull() ?: return@withContext

        suspend fun enqueueCoalesced(trackerType: String) {
            syncQueueDao.deletePendingFor(item.id, trackerType)
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    mediaId = item.id,
                    trackerType = trackerType,
                    action = "PROGRESS",
                    progressValue = newProgress
                )
            )
        }

        if (item.anilistId != null && !tokens.anilistToken.isNullOrBlank()) enqueueCoalesced("ANILIST")
        if (item.malId != null && !tokens.malToken.isNullOrBlank()) enqueueCoalesced("MAL")
        if (!tokens.traktToken.isNullOrBlank() && (item.traktId != null || (item.tmdbId != null && (item.mediaType == MediaType.MOVIE || item.mediaType == MediaType.SHOW)))) {
            enqueueCoalesced("TRAKT")
        }
        if (!tokens.simklToken.isNullOrBlank() && (item.simklId != null || item.tmdbId != null || item.malId != null)) {
            enqueueCoalesced("SIMKL")
        }
        if (item.bangumiId != null && !tokens.bangumiToken.isNullOrBlank()) enqueueCoalesced("BANGUMI")
        if (!item.vndbId.isNullOrBlank() && !tokens.vndbToken.isNullOrBlank()) enqueueCoalesced("VNDB")
        if (item.tmdbId != null && !tokens.tmdbToken.isNullOrBlank() && !tokens.tmdbSessionId.isNullOrBlank()) enqueueCoalesced("TMDB")
        if (!item.hardcoverId.isNullOrBlank() && !tokens.hardcoverToken.isNullOrBlank()) enqueueCoalesced("HARDCOVER")
    }

    suspend fun drainQueue() = withContext(Dispatchers.IO) {
        pushMutex.withLock {
            val tasks = syncQueueDao.getPendingTasksSnapshot()
            if (tasks.isEmpty()) return@withLock

            var activeTokens = tokenManager.tokens.firstOrNull() ?: return@withLock
            val refreshableServices = setOf("MAL", "TRAKT", "SIMKL", "BANGUMI", "COMICK")
            val expiredTrackers = mutableSetOf<String>()

            for (task in tasks) {
                val key = task.trackerType.uppercase()
                if (key in expiredTrackers) continue

                if (key == "TMDB" && activeTokens.tmdbSessionId.isNullOrBlank()) {
                    expiredTrackers.add(key)
                    syncQueueDao.deleteAllForTracker(key)
                    AppLogger.w("TrackerSync", "TMDb write session inactive — cleared stale queued TMDB tasks")
                    continue
                }

                val mediaItem = repository.getMediaById(task.mediaId).firstOrNull()
                if (mediaItem == null) {
                    syncQueueDao.deleteTask(task.id)
                    continue
                }

                var outcome = pushSingleTrackerInternal(
                    trackerKey = key,
                    item = mediaItem,
                    tokens = activeTokens,
                    progressOverride = task.progressValue
                )

                if (!outcome.success && (outcome.httpCode == 401 || outcome.httpCode == 403) && key in refreshableServices) {
                    val refreshed = TrackerAccountVerifier.refreshAccessToken(key, tokenManager)
                    if (!refreshed.isNullOrBlank()) {
                        activeTokens = tokenManager.tokens.firstOrNull() ?: activeTokens
                        outcome = pushSingleTrackerInternal(
                            trackerKey = key,
                            item = mediaItem,
                            tokens = activeTokens,
                            progressOverride = task.progressValue
                        )
                    }
                }

                when {
                    outcome.success -> syncQueueDao.deleteTask(task.id)
                    outcome.httpCode == 401 || outcome.httpCode == 403 -> {
                        expiredTrackers.add(key)
                        syncQueueDao.deleteAllForTracker(key)
                        AppLogger.w("TrackerSync", "Auth expired for $key — reconnect in Tracker Accounts")
                    }
                    task.retryCount >= 4 -> {
                        syncQueueDao.deleteTask(task.id)
                        AppLogger.w("TrackerSync", "Dropped queued $key task after max retries (code=${outcome.httpCode})")
                    }
                    else -> syncQueueDao.incrementRetryCount(task.id)
                }
            }
        }
    }

    suspend fun pushItemToTrackers(
        item: MediaItemEntity,
        targetTrackerKey: String? = null
    ): List<TrackerSyncOutcome> = withContext(Dispatchers.IO) {
        pushMutex.withLock {
            val tokens = tokenManager.tokens.firstOrNull() ?: TrackerTokens()
            val targets: List<String> = if (!targetTrackerKey.isNullOrBlank()) {
                listOf(targetTrackerKey.uppercase())
            } else {
                buildList {
                    if (item.anilistId != null) add("ANILIST")
                    if (item.malId != null) add("MAL")
                    if (item.traktId != null || (item.tmdbId != null && (item.mediaType == MediaType.MOVIE || item.mediaType == MediaType.SHOW))) add("TRAKT")
                    if (item.simklId != null || item.tmdbId != null || item.malId != null) add("SIMKL")
                    if (item.bangumiId != null) add("BANGUMI")
                    if (!item.vndbId.isNullOrBlank()) add("VNDB")
                    if (item.tmdbId != null && item.userRating != null && !tokens.tmdbSessionId.isNullOrBlank()) add("TMDB")
                    if (!item.hardcoverId.isNullOrBlank()) add("HARDCOVER")
                    if (!item.comickId.isNullOrBlank()) add("COMICK")
                }
            }
            if (targets.isEmpty()) {
                return@withLock listOf(TrackerSyncOutcome("NONE", false, null, "No remote trackers bound"))
            }
            targets.map { key ->
                pushSingleTrackerInternal(key, item, tokens, null)
            }
        }
    }

    private suspend fun pushSingleTrackerInternal(
        trackerKey: String,
        item: MediaItemEntity,
        tokens: TrackerTokens,
        progressOverride: Double?
    ): TrackerSyncOutcome = when (trackerKey.uppercase()) {
        "ANILIST" -> pushAniList(item, tokens.anilistToken, progressOverride)
        "MAL" -> pushMal(item, tokens.malToken, progressOverride)
        "TRAKT" -> pushTrakt(item, tokens.traktToken)
        "SIMKL" -> pushSimkl(item, tokens.simklToken)
        "BANGUMI" -> pushBangumi(item, tokens.bangumiToken, progressOverride)
        "VNDB" -> pushVndb(item, tokens.vndbToken)
        "TMDB" -> pushTmdb(item, tokens.tmdbToken, tokens.tmdbSessionId)
        "HARDCOVER" -> pushHardcover(item, tokens.hardcoverToken)
        "COMICK" -> if (tokens.comickToken.isNullOrBlank()) {
            TrackerSyncOutcome("COMICK", false, null, "Comick not signed in")
        } else {
            TrackerSyncOutcome("COMICK", true, 200, "Comick linked (pull/read-only API)")
        }
        else -> TrackerSyncOutcome(trackerKey.uppercase(), false, null, "Unsupported tracker: $trackerKey")
    }

    private fun epochMsToYmd(ms: Long?): String? {
        if (ms == null || ms <= 0L) return null
        return runCatching {
            Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrNull()
    }

    private fun epochMsToYmdTriple(ms: Long?): Triple<Int, Int, Int>? {
        if (ms == null || ms <= 0L) return null
        return runCatching {
            val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
            Triple(d.year, d.monthValue, d.dayOfMonth)
        }.getOrNull()
    }

    private fun parseYmdToEpochMs(ymd: String?): Long? {
        val clean = ymd?.trim()?.takeIf { it.length >= 10 }?.take(10) ?: return null
        return runCatching {
            LocalDate.parse(clean, DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun parseFuzzyDateToEpochMs(obj: JsonObject?): Long? {
        val safeObj = obj ?: return null
        val y = safeObj["year"].asSafeInt() ?: return null
        val m = (safeObj["month"].asSafeInt() ?: 1).coerceIn(1, 12)
        val d = (safeObj["day"].asSafeInt() ?: 1).coerceIn(1, 28)
        return runCatching {
            LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private suspend fun buildWatchedSeasonsArray(item: MediaItemEntity): JsonArray? {
        val watchedEps = if (item.id > 0L) {
            repository.getAllEpisodeProgressOnce(item.id).filter { it.isWatched && it.episodeNumber > 0 }
        } else {
            emptyList()
        }
        return if (watchedEps.isNotEmpty()) {
            val grouped = watchedEps.groupBy { it.seasonNumber.coerceAtLeast(1) }
            buildJsonArray {
                grouped.toSortedMap().forEach { (seasonNum, eps) ->
                    add(buildJsonObject {
                        put("number", seasonNum)
                        put("episodes", buildJsonArray {
                            eps.map { it.episodeNumber }.distinct().sorted().forEach { epNum ->
                                add(buildJsonObject { put("number", epNum) })
                            }
                        })
                    })
                }
            }
        } else {
            val count = item.currentProgress.toInt().coerceIn(0, 500)
            if (count <= 0) null else buildJsonArray {
                add(buildJsonObject {
                    put("number", 1)
                    put("episodes", buildJsonArray {
                        for (ep in 1..count) {
                            add(buildJsonObject { put("number", ep) })
                        }
                    })
                })
            }
        }
    }

    private suspend fun pushAniList(
        item: MediaItemEntity,
        token: String?,
        progressOverride: Double?
    ): TrackerSyncOutcome = NetworkPacer.withAniListPacing {
        if (token.isNullOrBlank()) return@withAniListPacing TrackerSyncOutcome("ANILIST", false, null, "AniList not signed in")
        val alId = item.anilistId ?: return@withAniListPacing TrackerSyncOutcome("ANILIST", false, null, "AniList ID not bound")
        try {
            val query = """
                mutation (
                    ${'$'}mediaId: Int,
                    ${'$'}progress: Int,
                    ${'$'}progressVolumes: Int,
                    ${'$'}status: MediaListStatus,
                    ${'$'}scoreRaw: Int,
                    ${'$'}repeat: Int,
                    ${'$'}notes: String,
                    ${'$'}private: Boolean,
                    ${'$'}hiddenFromStatusLists: Boolean,
                    ${'$'}startedAt: FuzzyDateInput,
                    ${'$'}completedAt: FuzzyDateInput,
                    ${'$'}customLists: [String]
                ) {
                    SaveMediaListEntry(
                        mediaId: ${'$'}mediaId,
                        progress: ${'$'}progress,
                        progressVolumes: ${'$'}progressVolumes,
                        status: ${'$'}status,
                        scoreRaw: ${'$'}scoreRaw,
                        repeat: ${'$'}repeat,
                        notes: ${'$'}notes,
                        private: ${'$'}private,
                        hiddenFromStatusLists: ${'$'}hiddenFromStatusLists,
                        startedAt: ${'$'}startedAt,
                        completedAt: ${'$'}completedAt,
                        customLists: ${'$'}customLists
                    ) {
                        id
                        progress
                        progressVolumes
                        status
                        score
                    }
                }
            """.trimIndent()

            val effectiveStatus = if (item.isAnilistStatusLocked && item.anilistStatus != null) item.anilistStatus else item.status
            val alStatus = when (effectiveStatus) {
                TrackingStatus.IN_PROGRESS -> "CURRENT"
                TrackingStatus.PLAN_TO_TRACK -> "PLANNING"
                TrackingStatus.COMPLETED -> "COMPLETED"
                TrackingStatus.ON_HOLD -> "PAUSED"
                TrackingStatus.DROPPED -> "DROPPED"
                else -> "CURRENT"
            }
            val effProg = TrackerMetadataCodec.resolveEffectiveProgress(item, "ANILIST", progressOverride)
            val rawScore = item.userRating?.let { (it * 10f).roundToInt().coerceIn(0, 100) }
            val effNote = TrackerMetadataCodec.resolveEffectiveNote(item, "ANILIST", 2000)
            val startTriple = epochMsToYmdTriple(item.startedAtMs)
            val endTriple = epochMsToYmdTriple(item.finishedAtMs)
            val customLists = item.anilistCustomLists?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("mediaId", alId.toInt())
                    put("progress", effProg)
                    if (item.volumesProgress > 0 && item.mediaType != MediaType.ANIME) {
                        put("progressVolumes", item.volumesProgress)
                    }
                    put("status", alStatus)
                    if (rawScore != null) put("scoreRaw", rawScore)
                    put("repeat", item.rewatchCount.coerceAtLeast(0))
                    if (effNote != null) put("notes", effNote) else put("notes", "")
                    put("private", item.anilistPrivate)
                    put("hiddenFromStatusLists", item.anilistHiddenFromStatus)
                    if (startTriple != null) {
                        putJsonObject("startedAt") {
                            put("year", startTriple.first)
                            put("month", startTriple.second)
                            put("day", startTriple.third)
                        }
                    }
                    if (endTriple != null) {
                        putJsonObject("completedAt") {
                            put("year", endTriple.first)
                            put("month", endTriple.second)
                            put("day", endTriple.third)
                        }
                    }
                    if (!customLists.isNullOrEmpty()) {
                        put("customLists", buildJsonArray { customLists.forEach { add(JsonPrimitive(it)) } })
                    }
                }
            }

            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .header("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val mainOutcome = client.newCall(request).execute().use { response ->
                val code = response.code
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerSync", "service=ANILIST op=push http=$code")
                    TrackerSyncOutcome("ANILIST", false, code, "AniList HTTP $code")
                } else {
                    TrackerSyncOutcome("ANILIST", true, code, "AniList synced")
                }
            }

            // Best-effort favorite sync (handles both favoriting and unfavoriting cleanly)
            val nextSnaps = TrackerMetadataCodec.decodeMatchedSnapshots(item.matchedTrackerMetaJson).toMutableMap()
            val prevSnap = nextSnaps["ANILIST"]
            if (mainOutcome.success && item.anilistFavorite != (prevSnap?.isFavorite == true)) {
                val idField = if (item.mediaType == MediaType.ANIME) "animeId" else "mangaId"
                val favPayload = buildJsonObject {
                    put("query", "mutation(${'$'}id: Int) { ToggleFavourite($idField: ${'$'}id) { anime { nodes { id } } manga { nodes { id } } } }")
                    putJsonObject("variables") { put("id", alId.toInt()) }
                }
                val favOk = runCatching {
                    client.newCall(
                        Request.Builder()
                            .url("https://graphql.anilist.co")
                            .header("Authorization", "Bearer $token")
                            .post(favPayload.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                    ).execute().use { it.isSuccessful }
                }.getOrDefault(false)
                if (favOk) {
                    val baseSnap = prevSnap ?: RemoteTrackerEntrySnapshot(
                        trackerKey = "ANILIST",
                        remoteId = alId.toString(),
                        title = item.title
                    )
                    nextSnaps["ANILIST"] = baseSnap.copy(
                        isFavorite = item.anilistFavorite,
                        lastSyncedMs = System.currentTimeMillis()
                    )
                    if (item.id > 0L) {
                        repository.updateMedia(
                            item.copy(matchedTrackerMetaJson = TrackerMetadataCodec.encodeMatchedSnapshots(nextSnaps))
                        )
                    }
                }
            }
            mainOutcome
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=ANILIST op=push ex=${e.javaClass.simpleName}")
            TrackerSyncOutcome("ANILIST", false, null, "AniList error: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun pushMal(
        item: MediaItemEntity,
        token: String?,
        progressOverride: Double?
    ): TrackerSyncOutcome = NetworkPacer.withGeneralPacing {
        if (token.isNullOrBlank()) return@withGeneralPacing TrackerSyncOutcome("MAL", false, null, "MAL not signed in")
        val malId = item.malId ?: return@withGeneralPacing TrackerSyncOutcome("MAL", false, null, "MAL ID not bound")
        try {
            val isAnime = item.mediaType == MediaType.ANIME
            val endpoint = if (isAnime) "anime" else "manga"
            val progressField = if (isAnime) "num_watched_episodes" else "num_chapters_read"
            val rewatchField = if (isAnime) "num_times_rewatched" else "num_times_reread"
            val rewatchingFlagField = if (isAnime) "is_rewatching" else "is_rereading"

            val effectiveStatus = if (item.isMalStatusLocked && item.malStatus != null) item.malStatus else item.status
            val malStatus = when (effectiveStatus) {
                TrackingStatus.IN_PROGRESS -> if (isAnime) "watching" else "reading"
                TrackingStatus.PLAN_TO_TRACK -> if (isAnime) "plan_to_watch" else "plan_to_read"
                TrackingStatus.COMPLETED -> "completed"
                TrackingStatus.ON_HOLD -> "on_hold"
                TrackingStatus.DROPPED -> "dropped"
                else -> if (isAnime) "watching" else "reading"
            }

            val effProg = TrackerMetadataCodec.resolveEffectiveProgress(item, "MAL", progressOverride)
            val effNote = TrackerMetadataCodec.resolveEffectiveNote(item, "MAL", 1000)

            val url = "https://api.myanimelist.net/v2/$endpoint/$malId/my_list_status"
            val bodyBuilder = okhttp3.FormBody.Builder()
                .add(progressField, effProg.toString())
                .add("status", malStatus)
                .add(rewatchingFlagField, item.malRewatching.toString())
                .add("priority", item.malPriority.coerceIn(0, 2).toString())

            if (!isAnime && item.volumesProgress > 0) {
                bodyBuilder.add("num_volumes_read", item.volumesProgress.toString())
            }
            item.userRating?.let {
                bodyBuilder.add("score", it.roundToInt().coerceIn(0, 10).toString())
            }
            effNote?.let { bodyBuilder.add("comments", it) }
            val effRewatch = maxOf(item.malRewatches, item.rewatchCount)
            if (effRewatch > 0) bodyBuilder.add(rewatchField, effRewatch.toString())
            item.malTags?.takeIf { it.isNotBlank() }?.let { bodyBuilder.add("tags", it) }
            epochMsToYmd(item.startedAtMs)?.let { bodyBuilder.add("start_date", it) }
            epochMsToYmd(item.finishedAtMs)?.let { bodyBuilder.add("finish_date", it) }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .put(bodyBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerSync", "service=MAL op=push http=$code")
                    TrackerSyncOutcome("MAL", false, code, "MAL HTTP $code")
                } else {
                    TrackerSyncOutcome("MAL", true, code, "MAL synced")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=MAL op=push ex=${e.javaClass.simpleName}")
            TrackerSyncOutcome("MAL", false, null, "MAL error: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun pushTrakt(
        item: MediaItemEntity,
        token: String?
    ): TrackerSyncOutcome = NetworkPacer.withGeneralPacing {
        if (token.isNullOrBlank()) return@withGeneralPacing TrackerSyncOutcome("TRAKT", false, null, "Trakt not signed in")
        if (item.tmdbId == null && item.traktId == null && item.imdbId.isNullOrBlank()) {
            return@withGeneralPacing TrackerSyncOutcome("TRAKT", false, null, "Trakt/TMDb/IMDb ID not bound")
        }
        try {
            val effectiveStatus = if (item.isTraktStatusLocked && item.traktStatus != null) item.traktStatus else item.status
            val arrayKey = if (item.mediaType == MediaType.MOVIE) "movies" else "shows"
            val idObj = buildJsonObject {
                item.traktId?.let { put("trakt", it.toInt()) }
                item.tmdbId?.let { put("tmdb", it.toInt()) }
                item.imdbId?.takeIf { it.isNotBlank() }?.let { put("imdb", it) }
            }
            val seasonsArr = if (effectiveStatus == TrackingStatus.IN_PROGRESS && item.mediaType != MediaType.MOVIE) {
                buildWatchedSeasonsArray(item)
            } else null

            val useHistory = when {
                effectiveStatus == TrackingStatus.COMPLETED -> true
                effectiveStatus == TrackingStatus.IN_PROGRESS && item.mediaType != MediaType.MOVIE -> seasonsArr != null
                else -> false
            }
            val endpointPath = if (useHistory) "sync/history" else "sync/watchlist"
            val payload = buildJsonObject {
                put(arrayKey, buildJsonArray {
                    add(buildJsonObject {
                        put("ids", idObj)
                        if (useHistory && seasonsArr != null) {
                            put("seasons", seasonsArr)
                        }
                    })
                })
            }

            val request = Request.Builder()
                .url("https://api.trakt.tv/$endpointPath")
                .header("Authorization", "Bearer $token")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", TrackerAccountVerifier.TRAKT_CLIENT_ID)
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val mainOutcome = client.newCall(request).execute().use { response ->
                val code = response.code
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerSync", "service=TRAKT op=push http=$code")
                    TrackerSyncOutcome("TRAKT", false, code, "Trakt HTTP $code")
                } else {
                    TrackerSyncOutcome("TRAKT", true, code, "Trakt synced")
                }
            }

            // Also sync Trakt rating when rated
            if (mainOutcome.success && item.userRating != null && item.userRating > 0f) {
                val rPayload = buildJsonObject {
                    put(arrayKey, buildJsonArray {
                        add(buildJsonObject {
                            put("ids", idObj)
                            put("rating", item.userRating.roundToInt().coerceIn(1, 10))
                        })
                    })
                }
                runCatching {
                    client.newCall(
                        Request.Builder()
                            .url("https://api.trakt.tv/sync/ratings")
                            .header("Authorization", "Bearer $token")
                            .header("trakt-api-version", "2")
                            .header("trakt-api-key", TrackerAccountVerifier.TRAKT_CLIENT_ID)
                            .header("User-Agent", "OmniTrack/1.0 (Android)")
                            .post(rPayload.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                    ).execute().close()
                }
            }
            mainOutcome
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=TRAKT op=push ex=${e.javaClass.simpleName}")
            TrackerSyncOutcome("TRAKT", false, null, "Trakt error: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun pushSimkl(
        item: MediaItemEntity,
        token: String?
    ): TrackerSyncOutcome = NetworkPacer.withGeneralPacing {
        if (token.isNullOrBlank()) return@withGeneralPacing TrackerSyncOutcome("SIMKL", false, null, "SIMKL not signed in")
        if (item.simklId == null && item.tmdbId == null && item.malId == null) {
            return@withGeneralPacing TrackerSyncOutcome("SIMKL", false, null, "SIMKL/TMDb/MAL ID not bound")
        }
        try {
            val arrayName = when (item.mediaType) {
                MediaType.MOVIE -> "movies"
                MediaType.ANIME -> "shows"
                else -> "shows"
            }
            val effectiveStatus = if (item.isSimklStatusLocked && item.simklStatus != null) item.simklStatus else item.status
            val simklTo = when (effectiveStatus) {
                TrackingStatus.COMPLETED -> "completed"
                TrackingStatus.IN_PROGRESS -> "watching"
                TrackingStatus.ON_HOLD -> "hold"
                TrackingStatus.DROPPED -> "dropped"
                else -> "plantowatch"
            }
            val idObj = buildJsonObject {
                item.simklId?.let { put("simkl", it.toInt()) }
                item.tmdbId?.let { put("tmdb", it.toInt()) }
                item.malId?.let { put("mal", it.toInt()) }
                item.anilistId?.let { put("anilist", it.toInt()) }
                item.imdbId?.takeIf { it.isNotBlank() }?.let { put("imdb", it) }
            }
            val effMemo = TrackerMetadataCodec.resolveEffectiveNote(item, "SIMKL", 140)
            val listPayload = buildJsonObject {
                put(arrayName, buildJsonArray {
                    add(buildJsonObject {
                        put("to", simklTo)
                        put("ids", idObj)
                        effMemo?.let { put("memo", it) }
                        put("private", item.simklPrivate)
                    })
                })
            }

            suspend fun postSimklWrite(url: String, jsonBody: String): Int {
                for (attempt in 0..1) {
                    val req = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .header("simkl-api-key", TrackerAccountVerifier.SIMKL_CLIENT_ID)
                        .header("User-Agent", "OmniTrack/1.0 (Android)")
                        .header("Content-Type", "application/json")
                        .post(jsonBody.toRequestBody("application/json".toMediaType()))
                        .build()
                    val (code, bodyStr) = client.newCall(req).execute().use { resp ->
                        resp.code to resp.body?.string().orEmpty()
                    }
                    if ((code == 400 && bodyStr.contains("rate_limit", ignoreCase = true)) || code == 429) {
                        if (attempt == 0) {
                            kotlinx.coroutines.delay(3000L)
                            continue
                        }
                    }
                    return code
                }
                return 400
            }

            val listCode = postSimklWrite("https://api.simkl.com/sync/add-to-list", listPayload.toString())
            val mainOutcome = if (listCode !in 200..299) {
                AppLogger.w("TrackerSync", "service=SIMKL op=push http=$listCode")
                TrackerSyncOutcome("SIMKL", false, listCode, "SIMKL HTTP $listCode")
            } else {
                TrackerSyncOutcome("SIMKL", true, listCode, "SIMKL synced")
            }

            // Push watched episodes & remove unwatched episodes > currentProgress on SIMKL (NuvioMobile-Enhanced parity)
            if (mainOutcome.success) {
                val curEp = item.currentProgress.toInt().coerceIn(0, 500)
                val totalEp = item.totalProgress?.toInt()?.takeIf { it > 0 }?.coerceAtMost(500)
                val isAnime = item.mediaType == MediaType.ANIME
                val allDbEps = if (item.id > 0L && item.mediaType != MediaType.MOVIE) {
                    repository.getAllEpisodeProgressOnce(item.id).filter { it.episodeNumber > 0 }
                } else emptyList()
                val watchedDbEps = allDbEps.filter { it.isWatched }
                val hasMultiSeasonDb = allDbEps.any { it.seasonNumber > 1 }

                // 1) Mark watched episodes (1..curEp or watchedDbEps) via /sync/history
                val historySeasons = if (item.mediaType != MediaType.MOVIE && curEp >= 1) {
                    buildWatchedSeasonsArray(item)
                } else null
                val flatWatchedEpisodes = if (isAnime && !hasMultiSeasonDb && curEp >= 1) {
                    val epNums = if (watchedDbEps.isNotEmpty()) {
                        watchedDbEps.map { it.episodeNumber }.distinct().sorted()
                    } else {
                        (1..curEp).toList()
                    }
                    buildJsonArray {
                        epNums.forEach { epNum -> add(buildJsonObject { put("number", epNum) }) }
                    }
                } else null

                val shouldPushHistory = (item.mediaType == MediaType.MOVIE && effectiveStatus == TrackingStatus.COMPLETED) ||
                    (item.mediaType != MediaType.MOVIE && (historySeasons != null || flatWatchedEpisodes != null || effectiveStatus == TrackingStatus.COMPLETED))
                if (shouldPushHistory) {
                    val histPayload = buildJsonObject {
                        put(arrayName, buildJsonArray {
                            add(buildJsonObject {
                                put("ids", idObj)
                                if (flatWatchedEpisodes != null) {
                                    put("episodes", flatWatchedEpisodes)
                                } else if (historySeasons != null) {
                                    put("seasons", historySeasons)
                                }
                            })
                        })
                    }
                    runCatching {
                        kotlinx.coroutines.delay(1000L)
                        postSimklWrite("https://api.simkl.com/sync/history", histPayload.toString())
                    }
                }

                // 2) Remove unwatched episodes (> curEp or explicitly unchecked in DB) via /sync/history/remove
                //    so decreasing episode progress (e.g. Ep 10 -> Ep 5 or 0) updates Simkl immediately!
                if (item.mediaType != MediaType.MOVIE && effectiveStatus != TrackingStatus.COMPLETED) {
                    val maxUpper = totalEp ?: (curEp + 36).coerceAtMost(200)
                    if (curEp < maxUpper || allDbEps.any { !it.isWatched }) {
                        val removePayload = if (isAnime && !hasMultiSeasonDb) {
                            val unwatchedNums = buildSet {
                                allDbEps.filter { !it.isWatched && it.episodeNumber > curEp }.forEach { add(it.episodeNumber) }
                                for (ep in (curEp + 1)..maxUpper) add(ep)
                            }.sorted()
                            if (unwatchedNums.isNotEmpty()) {
                                buildJsonObject {
                                    put("shows", buildJsonArray {
                                        add(buildJsonObject {
                                            put("ids", idObj)
                                            put("episodes", buildJsonArray {
                                                unwatchedNums.forEach { epNum ->
                                                    add(buildJsonObject { put("number", epNum) })
                                                }
                                            })
                                        })
                                    })
                                }
                            } else null
                        } else {
                            val seasonsToRemove = if (allDbEps.isNotEmpty()) {
                                val unwatchedBySeason = allDbEps
                                    .filter { !it.isWatched }
                                    .groupBy { it.seasonNumber.coerceAtLeast(1) }
                                    .toMutableMap()
                                if (unwatchedBySeason.isEmpty() && curEp < maxUpper) {
                                    unwatchedBySeason[1] = ((curEp + 1)..maxUpper).map { epNum ->
                                        com.omnitrack.app.data.db.entity.EpisodeProgressEntity(
                                            mediaId = item.id,
                                            seasonNumber = 1,
                                            episodeNumber = epNum,
                                            isWatched = false
                                        )
                                    }
                                }
                                if (unwatchedBySeason.isNotEmpty()) {
                                    buildJsonArray {
                                        unwatchedBySeason.toSortedMap().forEach { (sNum, eps) ->
                                            add(buildJsonObject {
                                                put("number", sNum)
                                                put("episodes", buildJsonArray {
                                                    eps.map { it.episodeNumber }.distinct().sorted().forEach { epNum ->
                                                        add(buildJsonObject { put("number", epNum) })
                                                    }
                                                })
                                            })
                                        }
                                    }
                                } else null
                            } else if (curEp < maxUpper) {
                                buildJsonArray {
                                    add(buildJsonObject {
                                        put("number", 1)
                                        put("episodes", buildJsonArray {
                                            for (epNum in (curEp + 1)..maxUpper) {
                                                add(buildJsonObject { put("number", epNum) })
                                            }
                                        })
                                    })
                                }
                            } else null

                            if (seasonsToRemove != null) {
                                buildJsonObject {
                                    put("shows", buildJsonArray {
                                        add(buildJsonObject {
                                            put("ids", idObj)
                                            put("seasons", seasonsToRemove)
                                        })
                                    })
                                }
                            } else null
                        }

                        if (removePayload != null) {
                            runCatching {
                                kotlinx.coroutines.delay(1000L)
                                postSimklWrite("https://api.simkl.com/sync/history/remove", removePayload.toString())
                                if (curEp == 0) {
                                    // Re-assert list membership after clearing episode 1..maxUpper
                                    kotlinx.coroutines.delay(1000L)
                                    postSimklWrite("https://api.simkl.com/sync/add-to-list", listPayload.toString())
                                }
                            }
                        }
                    }
                }
            }

            if (mainOutcome.success && item.userRating != null && item.userRating > 0f) {
                val rPayload = buildJsonObject {
                    put(arrayName, buildJsonArray {
                        add(buildJsonObject {
                            put("ids", idObj)
                            put("rating", item.userRating.roundToInt().coerceIn(1, 10))
                        })
                    })
                }
                runCatching {
                    kotlinx.coroutines.delay(1000L)
                    postSimklWrite("https://api.simkl.com/sync/ratings", rPayload.toString())
                }
            }
            mainOutcome
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=SIMKL op=push ex=${e.javaClass.simpleName}")
            TrackerSyncOutcome("SIMKL", false, null, "SIMKL error: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun pushBangumi(
        item: MediaItemEntity,
        token: String?,
        progressOverride: Double?
    ): TrackerSyncOutcome = NetworkPacer.withBangumiPacing {
        if (token.isNullOrBlank()) return@withBangumiPacing TrackerSyncOutcome("BANGUMI", false, null, "Bangumi not signed in")
        val bgmId = item.bangumiId ?: return@withBangumiPacing TrackerSyncOutcome("BANGUMI", false, null, "Bangumi ID not bound")
        try {
            val effectiveStatus = if (item.isBangumiStatusLocked && item.bangumiStatus != null) item.bangumiStatus else item.status
            val bgmType = when (effectiveStatus) {
                TrackingStatus.PLAN_TO_TRACK -> 1
                TrackingStatus.COMPLETED -> 2
                TrackingStatus.IN_PROGRESS -> 3
                TrackingStatus.ON_HOLD -> 4
                TrackingStatus.DROPPED -> 5
                else -> 3
            }
            val effNote = TrackerMetadataCodec.resolveEffectiveNote(item, "BANGUMI", 380)
            val effProg = TrackerMetadataCodec.resolveEffectiveProgress(item, "BANGUMI", progressOverride)
            val tagsList = item.bangumiTags
                ?.split(",")
                ?.map { it.trim().replace(Regex("\\s+"), "_") }
                ?.filter { it.isNotEmpty() }
                .orEmpty()

            // Step 1: Upsert collection status/rating/comment/tags (never include ep_status/vol_status here,
            // because Bangumi v0 rejects ep_status/vol_status with HTTP 400 on non-book subjects or uncollected items)
            val basePayload = buildJsonObject {
                put("type", bgmType)
                item.userRating?.takeIf { it > 0f }?.let { put("rate", it.roundToInt().coerceIn(1, 10)) }
                effNote?.takeIf { it.isNotBlank() }?.let { put("comment", it) }
                put("private", item.bangumiPrivate)
                if (tagsList.isNotEmpty()) {
                    put("tags", buildJsonArray { tagsList.forEach { add(JsonPrimitive(it)) } })
                }
            }

            val jsonMediaType = "application/json".toMediaType()
            val request = Request.Builder()
                .url("https://api.bgm.tv/v0/users/-/collections/$bgmId")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                .header("Accept", "application/json")
                .post(basePayload.toString().toByteArray(Charsets.UTF_8).toRequestBody(jsonMediaType))
                .build()

            val mainOutcome = client.newCall(request).execute().use { response ->
                val code = response.code
                if (!response.isSuccessful) {
                    val errDetail = runCatching { response.body?.string().orEmpty().take(180) }.getOrDefault("")
                    AppLogger.w("TrackerSync", "service=BANGUMI op=push http=$code detail=$errDetail")
                    TrackerSyncOutcome("BANGUMI", false, code, "Bangumi HTTP $code")
                } else {
                    TrackerSyncOutcome("BANGUMI", true, code, "Bangumi synced")
                }
            }

            if (!mainOutcome.success) return@withBangumiPacing mainOutcome

            // Step 2: Sync progress according to Bangumi v0 subject rules
            val isBookLike = item.mediaType == com.omnitrack.app.data.model.MediaType.MANGA ||
                item.mediaType == com.omnitrack.app.data.model.MediaType.NOVEL ||
                item.mediaType == com.omnitrack.app.data.model.MediaType.BOOK ||
                item.mediaType == com.omnitrack.app.data.model.MediaType.COMIC

            if (isBookLike && (effProg > 0 || item.volumesProgress > 0)) {
                runCatching {
                    val bookProgPayload = buildJsonObject {
                        if (effProg > 0) put("ep_status", effProg)
                        if (item.volumesProgress > 0) put("vol_status", item.volumesProgress)
                    }
                    val patchReq = Request.Builder()
                        .url("https://api.bgm.tv/v0/users/-/collections/$bgmId")
                        .header("Authorization", "Bearer $token")
                        .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                        .header("Accept", "application/json")
                        .patch(bookProgPayload.toString().toByteArray(Charsets.UTF_8).toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(patchReq).execute().close()
                }
            } else if ((item.mediaType == com.omnitrack.app.data.model.MediaType.ANIME ||
                    item.mediaType == com.omnitrack.app.data.model.MediaType.SHOW) && effProg > 0
            ) {
                runCatching {
                    val epGetReq = Request.Builder()
                        .url("https://api.bgm.tv/v0/users/-/collections/$bgmId/episodes?episode_type=0&limit=300")
                        .header("Authorization", "Bearer $token")
                        .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                        .header("Accept", "application/json")
                        .get()
                        .build()
                    val epIdsToMark: List<Long> = client.newCall(epGetReq).execute().use { epResp ->
                        if (!epResp.isSuccessful) emptyList()
                        else {
                            val rootObj = json.parseToJsonElement(epResp.body?.string().orEmpty()).asSafeObj()
                            val dataArr = rootObj?.get("data").asSafeArr().orEmpty()
                            val result = mutableListOf<Long>()
                            for ((idx, el) in dataArr.withIndex()) {
                                val row = el.asSafeObj() ?: continue
                                val epObj = row["episode"].asSafeObj() ?: continue
                                val epId = epObj["id"].asSafeLong() ?: continue
                                val sortNum = epObj["sort"].asSafeStr()?.toDoubleOrNull()
                                    ?: epObj["ep"].asSafeStr()?.toDoubleOrNull()
                                val curType = row["type"].asSafeInt() ?: 0
                                val isWithinProgress = if (sortNum != null && sortNum > 0.0) {
                                    sortNum <= effProg.toDouble()
                                } else {
                                    (idx + 1) <= effProg
                                }
                                if (isWithinProgress && curType != 2) {
                                    result.add(epId)
                                }
                            }
                            result
                        }
                    }
                    if (epIdsToMark.isNotEmpty()) {
                        val epPatchPayload = buildJsonObject {
                            put("episode_id", buildJsonArray { epIdsToMark.forEach { add(JsonPrimitive(it)) } })
                            put("type", 2)
                        }
                        val epPatchReq = Request.Builder()
                            .url("https://api.bgm.tv/v0/users/-/collections/$bgmId/episodes")
                            .header("Authorization", "Bearer $token")
                            .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                            .header("Accept", "application/json")
                            .patch(epPatchPayload.toString().toByteArray(Charsets.UTF_8).toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(epPatchReq).execute().close()
                    }
                }
            }

            mainOutcome
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=BANGUMI op=push ex=${e.javaClass.simpleName}")
            TrackerSyncOutcome("BANGUMI", false, null, "Bangumi error: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun pushVndb(
        item: MediaItemEntity,
        token: String?
    ): TrackerSyncOutcome = NetworkPacer.withVndbPacing {
        if (token.isNullOrBlank()) return@withVndbPacing TrackerSyncOutcome("VNDB", false, null, "VNDB not signed in")
        val vId = item.vndbId?.takeIf { it.isNotBlank() } ?: return@withVndbPacing TrackerSyncOutcome("VNDB", false, null, "VNDB ID not bound")
        try {
            val activeLabelId = when (item.status) {
                TrackingStatus.IN_PROGRESS -> 1
                TrackingStatus.COMPLETED -> 2
                TrackingStatus.ON_HOLD -> 3
                TrackingStatus.DROPPED -> 4
                TrackingStatus.PLAN_TO_TRACK -> 5
                else -> null
            }
            val effNote = TrackerMetadataCodec.resolveEffectiveNote(item, "VNDB", 2000)
            val payload = buildJsonObject {
                if (activeLabelId != null) {
                    put("labels_set", buildJsonArray { add(JsonPrimitive(activeLabelId)) })
                    put("labels_unset", buildJsonArray {
                        listOf(1, 2, 3, 4, 5).filter { it != activeLabelId }.forEach { add(JsonPrimitive(it)) }
                    })
                }
                val vote = item.userRating?.let { (it * 10f).roundToInt().coerceIn(10, 100) }
                if (vote != null) put("vote", vote)
                effNote?.let { put("notes", it) }
                epochMsToYmd(item.startedAtMs)?.let { put("started", it) }
                val finDate = epochMsToYmd(item.finishedAtMs)
                    ?: if (item.status == TrackingStatus.COMPLETED) epochMsToYmd(System.currentTimeMillis()) else null
                if (finDate != null) put("finished", finDate)
            }
            val request = Request.Builder()
                .url("https://api.vndb.org/kana/ulist/$vId")
                .header("Authorization", "token $token")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .patch(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerSync", "service=VNDB op=push http=$code")
                    TrackerSyncOutcome("VNDB", false, code, "VNDB HTTP $code")
                } else {
                    TrackerSyncOutcome("VNDB", true, code, "VNDB synced")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=VNDB op=push ex=${e.javaClass.simpleName}")
            TrackerSyncOutcome("VNDB", false, null, "VNDB error: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun pushTmdb(
        item: MediaItemEntity,
        token: String?,
        sessionId: String?
    ): TrackerSyncOutcome = NetworkPacer.withTmdbPacing {
        val clean = token?.trim().orEmpty()
        if (clean.isBlank()) return@withTmdbPacing TrackerSyncOutcome("TMDB", false, null, "TMDb account token not connected")
        val sid = sessionId?.trim().orEmpty()
        if (sid.isBlank()) {
            return@withTmdbPacing TrackerSyncOutcome("TMDB", false, null, "Sign in to TMDb in Tracker Accounts to enable rating sync")
        }
        val tId = item.tmdbId ?: return@withTmdbPacing TrackerSyncOutcome("TMDB", false, null, "TMDb ID not bound")
        val rating = item.userRating ?: return@withTmdbPacing TrackerSyncOutcome("TMDB", true, null, "TMDb linked (rate entry to push score)")
        try {
            val type = if (item.mediaType == MediaType.MOVIE) "movie" else "tv"
            val halfStarRating = ((rating * 2f).roundToInt() / 2.0).coerceIn(0.5, 10.0)
            val payload = buildJsonObject { put("value", halfStarRating) }
            val isJwt = TrackerAccountVerifier.isTmdbV4Jwt(clean)
            val encSid = URLEncoder.encode(sid, "UTF-8")
            val url = if (isJwt) {
                "https://api.themoviedb.org/3/$type/$tId/rating?session_id=$encSid"
            } else {
                "https://api.themoviedb.org/3/$type/$tId/rating?api_key=${URLEncoder.encode(clean, "UTF-8")}&session_id=$encSid"
            }

            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
            if (isJwt) {
                reqBuilder.header("Authorization", "Bearer $clean")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                val code = response.code
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerSync", "service=TMDB op=push http=$code")
                    TrackerSyncOutcome("TMDB", false, code, "TMDb HTTP $code")
                } else {
                    TrackerSyncOutcome("TMDB", true, code, "TMDb rating synced")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=TMDB op=push ex=${e.javaClass.simpleName}")
            TrackerSyncOutcome("TMDB", false, null, "TMDb error: ${e.javaClass.simpleName}")
        }
    }

    suspend fun pullRemoteLibraries(onStatus: (String) -> Unit): Int = withContext(Dispatchers.IO) {
        var totalImported = 0
        val tokens = tokenManager.tokens.firstOrNull() ?: return@withContext 0

        // 1. Drain offline queue first
        onStatus("Syncing local changes upstream...")
        drainQueue()

        // 2. AniList Library Pull
        if (!tokens.anilistToken.isNullOrBlank()) {
            onStatus("Pulling AniList anime & manga library...")
            val res = importAniListLibrary(onStatus)
            totalImported += res.imported + res.updated
        }

        // 3. MyAnimeList Library Pull
        if (!tokens.malToken.isNullOrBlank()) {
            onStatus("Pulling MyAnimeList library...")
            val res = importMalLibrary(onStatus)
            totalImported += res.imported + res.updated
        }

        // 4. Trakt History & Watchlist Pull
        if (!tokens.traktToken.isNullOrBlank()) {
            onStatus("Pulling Trakt library...")
            val res = importTraktLibrary(onStatus)
            totalImported += res.imported + res.updated
        }

        // 5. SIMKL Library Pull
        if (!tokens.simklToken.isNullOrBlank()) {
            onStatus("Pulling SIMKL library...")
            val res = importSimklLibrary(onStatus)
            totalImported += res.imported + res.updated
        }

        // 6. Bangumi Library Pull
        if (!tokens.bangumiToken.isNullOrBlank()) {
            onStatus("Pulling Bangumi collections...")
            val res = importBangumiLibrary(onStatus)
            totalImported += res.imported + res.updated
        }

        // 7. VNDB List Pull
        if (!tokens.vndbToken.isNullOrBlank()) {
            onStatus("Pulling VNDB visual novel list...")
            val res = importVndbLibrary(onStatus)
            totalImported += res.imported + res.updated
        }

        // 8. Steam Owned Games Library Pull
        if (!tokens.steamApiKey.isNullOrBlank() && !tokens.steamId64.isNullOrBlank()) {
            onStatus("Syncing Steam library & playtime...")
            val steamSummary = importSteamLibrary(onStatus)
            totalImported += steamSummary.imported + steamSummary.updated
        }

        // 9. Hardcover Books Library Pull
        if (!tokens.hardcoverToken.isNullOrBlank()) {
            onStatus("Pulling Hardcover reading library...")
            val hcSummary = importHardcoverLibrary(onStatus)
            totalImported += hcSummary.imported + hcSummary.updated
        }

        onStatus("Sync complete! $totalImported items imported/updated.")
        totalImported
    }

    private suspend fun pushHardcover(
        item: MediaItemEntity,
        token: String?
    ): TrackerSyncOutcome {
        val cleanToken = token?.trim().orEmpty()
        if (cleanToken.isBlank()) return TrackerSyncOutcome("HARDCOVER", false, null, "Hardcover not signed in")
        val hcId = item.hardcoverId?.trim().orEmpty()
        if (hcId.isBlank()) return TrackerSyncOutcome("HARDCOVER", false, null, "Hardcover ID not bound")
        val hcClient = HardcoverApiClient(client)
        val effNote = TrackerMetadataCodec.resolveEffectiveNote(item, "HARDCOVER", 0)
        val (ok, code) = hcClient.syncBookStatusDetailed(
            token = cleanToken,
            hardcoverId = hcId,
            status = item.status,
            rating10 = item.userRating,
            currentPage = item.currentProgress.toInt().takeIf { it > 0 },
            reviewNote = effNote,
            isPrivate = item.anilistPrivate
        )
        return if (ok) {
            TrackerSyncOutcome("HARDCOVER", true, code ?: 200, "Hardcover synced")
        } else {
            TrackerSyncOutcome("HARDCOVER", false, code, hcClient.lastError ?: "Hardcover sync failed")
        }
    }

    suspend fun importHardcoverLibrary(
        onStatus: (String) -> Unit = {}
    ): BookImportSummary = withContext(Dispatchers.IO) {
        val token = tokenManager.tokens.firstOrNull()?.hardcoverToken?.trim().orEmpty()
        if (token.isBlank()) {
            return@withContext BookImportSummary(0, 0, "Connect your Hardcover API token first")
        }
        onStatus("Fetching Hardcover library...")
        val hcClient = com.omnitrack.app.data.enrichment.HardcoverApiClient(client)
        val records = hcClient.pullUserBooks(token)
        if (records.isEmpty()) {
            return@withContext BookImportSummary(0, 0, hcClient.lastError ?: "0 books returned from Hardcover")
        }
        var imported = 0
        var updated = 0
        for (rec in records) {
            val existing = repository.findExistingMedia(
                hardcoverId = rec.hardcoverId,
                isbn = rec.isbn13,
                title = rec.title,
                mediaType = com.omnitrack.app.data.model.MediaType.BOOK
            )
            if (existing != null) {
                val mergedStatus = if (existing.status == com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK &&
                    rec.status != com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK
                ) {
                    rec.status
                } else existing.status
                val nextProgress = if (existing.currentProgress <= 0.0 && rec.currentPage > 0.0) {
                    rec.currentPage
                } else existing.currentProgress
                val next = existing.copy(
                    hardcoverId = existing.hardcoverId ?: rec.hardcoverId,
                    isbn = existing.isbn ?: rec.isbn13,
                    bookAuthor = existing.bookAuthor ?: rec.author,
                    coverUrl = existing.coverUrl.takeIf { it.isNotBlank() } ?: rec.coverUrl.orEmpty(),
                    releaseYear = existing.releaseYear ?: rec.releaseYear,
                    totalProgress = existing.totalProgress ?: rec.pages?.toDouble(),
                    currentProgress = nextProgress,
                    userRating = existing.userRating ?: rec.userRating10,
                    status = mergedStatus
                )
                if (next != existing) {
                    repository.updateMedia(next)
                    updated++
                }
            } else {
                repository.insertMedia(
                    MediaItemEntity(
                        title = rec.title,
                        coverUrl = rec.coverUrl.orEmpty(),
                        releaseYear = rec.releaseYear,
                        mediaType = com.omnitrack.app.data.model.MediaType.BOOK,
                        status = rec.status,
                        currentProgress = rec.currentPage,
                        totalProgress = rec.pages?.toDouble(),
                        userRating = rec.userRating10,
                        hardcoverId = rec.hardcoverId,
                        isbn = rec.isbn13,
                        bookAuthor = rec.author,
                        bookFormat = "HARDCOVER"
                    )
                )
                imported++
            }
        }
        onStatus("Hardcover: imported $imported, updated $updated books")
        BookImportSummary(imported = imported, updated = updated)
    }

    private fun JsonElement?.asSafeObj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.asSafeArr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.asSafeStr(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonElement?.asSafeLong(): Long? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
    private fun JsonElement?.asSafeInt(): Int? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull

    suspend fun importSteamLibrary(
        onStatus: (String) -> Unit = {}
    ): SteamImportSummary = withContext(Dispatchers.IO) {
        val tokens = tokenManager.tokens.firstOrNull()
        val apiKey = tokens?.steamApiKey?.trim().orEmpty()
        val rawSteamId = tokens?.steamId64?.trim().orEmpty()
        if (apiKey.isBlank() || rawSteamId.isBlank()) {
            return@withContext SteamImportSummary(0, 0, "Connect Steam API Key & SteamID64 first")
        }

        val resolvedSteamId = TrackerAccountVerifier.resolveSteamId(rawSteamId, apiKey) ?: rawSteamId
        if (resolvedSteamId.isBlank()) {
            return@withContext SteamImportSummary(0, 0, "Could not resolve SteamID64")
        }

        data class SteamOwnedGame(
            val appId: Long,
            val name: String,
            val playtimeMinutes: Int
        )

        onStatus("Fetching Steam owned games...")
        val fetchResult: Pair<List<SteamOwnedGame>, String?> = NetworkPacer.withGeneralPacing {
            try {
                val url = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/" +
                    "?key=${URLEncoder.encode(apiKey, "UTF-8")}" +
                    "&steamid=${URLEncoder.encode(resolvedSteamId, "UTF-8")}" +
                    "&include_appinfo=1&include_played_free_games=1&format=json"

                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withGeneralPacing emptyList<SteamOwnedGame>() to "Steam API error HTTP ${response.code}"
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return@withGeneralPacing emptyList<SteamOwnedGame>() to "Empty response from Steam API"
                    }
                    val root = json.parseToJsonElement(body).asSafeObj()
                    val gamesArr = root?.get("response").asSafeObj()?.get("games").asSafeArr()
                        ?: return@withGeneralPacing emptyList<SteamOwnedGame>() to
                            "No Steam games returned — ensure Steam Game Details privacy is Public"

                    val parsed = gamesArr.mapNotNull { elem ->
                        val obj = elem.asSafeObj() ?: return@mapNotNull null
                        val appId = obj["appid"].asSafeLong()?.takeIf { it > 0L } ?: return@mapNotNull null
                        val name = obj["name"].asSafeStr() ?: return@mapNotNull null
                        val mins = obj["playtime_forever"].asSafeInt() ?: 0
                        SteamOwnedGame(appId = appId, name = name, playtimeMinutes = mins)
                    }.sortedByDescending { it.playtimeMinutes }

                    parsed to null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList<SteamOwnedGame>() to (e.message ?: "Steam import failed")
            }
        }

        val (ownedGames, fetchError) = fetchResult
        if (fetchError != null) {
            return@withContext SteamImportSummary(0, 0, fetchError)
        }
        if (ownedGames.isEmpty()) {
            return@withContext SteamImportSummary(0, 0, "0 owned games found on Steam account")
        }

        var imported = 0
        var updated = 0

        for (g in ownedGames) {
            val steamHours = ((g.playtimeMinutes / 6.0).roundToInt() / 10.0)
            val existing = repository.findExistingMedia(
                steamAppId = g.appId,
                title = g.name,
                mediaType = com.omnitrack.app.data.model.MediaType.GAME
            )
            if (existing != null) {
                val nextSteamId = existing.steamAppId ?: g.appId
                val nextHours = maxOf(existing.playtimeHours, steamHours)
                val nextProgress = maxOf(existing.currentProgress, steamHours)
                val existingPlatforms = existing.platforms?.split(",")
                    ?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                val nextPlatforms = if (existingPlatforms.isEmpty()) {
                    "PC"
                } else if (existingPlatforms.none { it.equals("PC", ignoreCase = true) }) {
                    (existingPlatforms + "PC").joinToString(", ")
                } else {
                    existing.platforms
                }
                val nextStatus = if (nextHours > 0.0 &&
                    existing.status == com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK
                ) {
                    com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS
                } else {
                    existing.status
                }
                val changed = existing.copy(
                    steamAppId = nextSteamId,
                    playtimeHours = nextHours,
                    currentProgress = nextProgress,
                    platforms = nextPlatforms,
                    platform = existing.platform ?: "PC",
                    status = nextStatus
                )
                if (changed != existing) {
                    repository.updateMedia(changed)
                    updated++
                }
            } else {
                val cover = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${g.appId}/library_600x900_2x.jpg"
                val hero = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${g.appId}/library_hero.jpg"
                val status = if (steamHours > 0.0) {
                    com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS
                } else {
                    com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK
                }
                repository.insertMedia(
                    MediaItemEntity(
                        title = g.name,
                        coverUrl = cover,
                        bannerUrl = hero,
                        mediaType = com.omnitrack.app.data.model.MediaType.GAME,
                        status = status,
                        playtimeHours = steamHours,
                        currentProgress = steamHours,
                        platform = "PC",
                        platforms = "PC",
                        steamAppId = g.appId
                    )
                )
                imported++
            }
        }

        onStatus("Steam: imported $imported, updated $updated games")
        SteamImportSummary(imported = imported, updated = updated)
    }

    private enum class MergeOutcome { IMPORTED, UPDATED, SKIPPED }

    private suspend fun smartMergeIncomingItem(incoming: MediaItemEntity): MergeOutcome {
        val existing = repository.findExistingMedia(
            anilistId = incoming.anilistId,
            malId = incoming.malId,
            tmdbId = incoming.tmdbId,
            vndbId = incoming.vndbId,
            igdbId = incoming.igdbId,
            steamAppId = incoming.steamAppId,
            openLibraryId = incoming.openLibraryId,
            hardcoverId = incoming.hardcoverId,
            isbn = incoming.isbn,
            title = incoming.title,
            mediaType = incoming.mediaType
        )
        if (existing == null) {
            repository.insertMedia(incoming)
            return MergeOutcome.IMPORTED
        }

        val bannerDisabled = existing.artworkRole == "DISABLE_BANNER" || existing.customBannerPath == "NONE"
        val nextCover = if ((existing.coverUrl.isBlank() || existing.coverUrl.contains("placeholder")) && incoming.coverUrl.isNotBlank()) {
            incoming.coverUrl
        } else {
            existing.coverUrl
        }
        val nextBanner = if (!bannerDisabled && existing.bannerUrl.isNullOrBlank() && !incoming.bannerUrl.isNullOrBlank()) {
            incoming.bannerUrl
        } else {
            existing.bannerUrl
        }
        val nextProgress = maxOf(existing.currentProgress, incoming.currentProgress)
        val nextPlaytime = maxOf(existing.playtimeHours, incoming.playtimeHours)
        val nextTotal = existing.totalProgress?.takeIf { it > 0.0 } ?: incoming.totalProgress
        val nextRating = existing.userRating ?: incoming.userRating
        val nextStatus = when {
            incoming.status == com.omnitrack.app.data.model.TrackingStatus.COMPLETED &&
                (existing.status == com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK ||
                    (existing.status == com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS &&
                        nextProgress >= (nextTotal ?: 1.0))) -> com.omnitrack.app.data.model.TrackingStatus.COMPLETED
            existing.status == com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK &&
                incoming.status != com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK -> incoming.status
            else -> existing.status
        }

        val changed = existing.copy(
            nativeTitle = existing.nativeTitle ?: incoming.nativeTitle,
            synopsis = existing.synopsis?.takeIf { it.isNotBlank() } ?: incoming.synopsis,
            coverUrl = nextCover,
            bannerUrl = nextBanner,
            releaseYear = existing.releaseYear ?: incoming.releaseYear,
            bookAuthor = existing.bookAuthor ?: incoming.bookAuthor,
            platform = existing.platform ?: incoming.platform,
            platforms = existing.platforms ?: incoming.platforms,
            anilistId = existing.anilistId ?: incoming.anilistId,
            malId = existing.malId ?: incoming.malId,
            tmdbId = existing.tmdbId ?: incoming.tmdbId,
            traktId = existing.traktId ?: incoming.traktId,
            simklId = existing.simklId ?: incoming.simklId,
            bangumiId = existing.bangumiId ?: incoming.bangumiId,
            comickId = existing.comickId ?: incoming.comickId,
            vndbId = existing.vndbId ?: incoming.vndbId,
            igdbId = existing.igdbId ?: incoming.igdbId,
            steamAppId = existing.steamAppId ?: incoming.steamAppId,
            openLibraryId = existing.openLibraryId ?: incoming.openLibraryId,
            hardcoverId = existing.hardcoverId ?: incoming.hardcoverId,
            isbn = existing.isbn ?: incoming.isbn,
            currentProgress = nextProgress,
            totalProgress = nextTotal,
            volumesProgress = maxOf(existing.volumesProgress, incoming.volumesProgress),
            rewatchCount = maxOf(existing.rewatchCount, incoming.rewatchCount),
            personalNotes = existing.personalNotes?.takeIf { it.isNotBlank() } ?: incoming.personalNotes,
            startedAtMs = existing.startedAtMs ?: incoming.startedAtMs,
            finishedAtMs = existing.finishedAtMs ?: incoming.finishedAtMs,
            playtimeHours = nextPlaytime,
            userRating = nextRating,
            status = nextStatus
        )
        return if (changed != existing) {
            repository.updateMedia(changed)
            MergeOutcome.UPDATED
        } else {
            MergeOutcome.SKIPPED
        }
    }

    suspend fun importSteamLibraryDetailed(
        onStatus: (String) -> Unit = {}
    ): TrackerImportResult {
        val summary = importSteamLibrary(onStatus)
        return TrackerImportResult(
            imported = summary.imported,
            updated = summary.updated,
            skipped = 0,
            error = summary.error
        )
    }

    suspend fun importHardcoverLibraryDetailed(
        onStatus: (String) -> Unit = {}
    ): TrackerImportResult {
        val summary = importHardcoverLibrary(onStatus)
        return TrackerImportResult(
            imported = summary.imported,
            updated = summary.updated,
            skipped = 0,
            error = summary.error
        )
    }

    suspend fun importAniListLibrary(
        onStatus: (String) -> Unit = {}
    ): TrackerImportResult = withContext(Dispatchers.IO) {
        val token = tokenManager.tokens.firstOrNull()?.anilistToken?.trim().orEmpty()
        if (token.isBlank()) return@withContext TrackerImportResult(0, 0, 0, "Connect AniList first")
        onStatus("Fetching AniList library...")
        var imported = 0
        var updated = 0
        var skipped = 0
        try {
            val viewerId = NetworkPacer.withAniListPacing {
                val viewerReq = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .header("Authorization", "Bearer $token")
                    .post(buildJsonObject { put("query", "query { Viewer { id name } }") }.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(viewerReq).execute().use { response ->
                    if (!response.isSuccessful) return@withAniListPacing null
                    val body = response.body?.string() ?: return@withAniListPacing null
                    json.parseToJsonElement(body).asSafeObj()?.get("data").asSafeObj()?.get("Viewer").asSafeObj()?.get("id").asSafeInt()
                }
            } ?: return@withContext TrackerImportResult(0, 0, 0, "Could not fetch AniList viewer profile")

            for (mediaType in listOf("ANIME", "MANGA")) {
                val listQuery = """
                    query (${'$'}userId: Int, ${'$'}type: MediaType) {
                        MediaListCollection(userId: ${'$'}userId, type: ${'$'}type) {
                            lists {
                                entries {
                                    progress
                                    progressVolumes
                                    repeat
                                    notes
                                    startedAt { year month day }
                                    completedAt { year month day }
                                    score(format: POINT_10_DECIMAL)
                                    status
                                    media {
                                        id
                                        idMal
                                        title { romaji english native }
                                        coverImage { extraLarge }
                                        bannerImage
                                        episodes
                                        chapters
                                        description
                                        format
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()
                val listPayload = buildJsonObject {
                    put("query", listQuery)
                    putJsonObject("variables") {
                        put("userId", viewerId)
                        put("type", mediaType)
                    }
                }
                val lists = NetworkPacer.withAniListPacing {
                    val listReq = Request.Builder()
                        .url("https://graphql.anilist.co")
                        .header("Authorization", "Bearer $token")
                        .post(listPayload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(listReq).execute().use { response ->
                        if (!response.isSuccessful) return@withAniListPacing null
                        val root = json.parseToJsonElement(response.body?.string().orEmpty()).asSafeObj()
                        root?.get("data").asSafeObj()?.get("MediaListCollection").asSafeObj()?.get("lists").asSafeArr()
                    }
                } ?: continue

                for (listElem in lists) {
                    val entries = listElem.asSafeObj()?.get("entries").asSafeArr() ?: continue
                    for (entryElem in entries) {
                        val entryObj = entryElem.asSafeObj() ?: continue
                        val mediaObj = entryObj["media"].asSafeObj() ?: continue
                        val alId = mediaObj["id"].asSafeLong() ?: continue
                        val titleObj = mediaObj["title"].asSafeObj()
                        val title = titleObj?.get("english").asSafeStr()
                            ?: titleObj?.get("romaji").asSafeStr()
                            ?: titleObj?.get("native").asSafeStr()
                            ?: continue
                        val nativeTitle = titleObj?.get("native").asSafeStr()
                        val cover = mediaObj["coverImage"].asSafeObj()?.get("extraLarge").asSafeStr().orEmpty()
                        val banner = mediaObj["bannerImage"].asSafeStr()
                        val desc = mediaObj["description"].asSafeStr()?.replace(Regex("<[^>]*>"), "")?.take(400)
                        val format = mediaObj["format"].asSafeStr()
                        val resolvedType = when {
                            format == "NOVEL" -> com.omnitrack.app.data.model.MediaType.NOVEL
                            mediaType == "ANIME" -> com.omnitrack.app.data.model.MediaType.ANIME
                            else -> com.omnitrack.app.data.model.MediaType.MANGA
                        }
                        val progress = entryObj["progress"].asSafeStr()?.toDoubleOrNull() ?: 0.0
                        val totalProg = if (mediaType == "ANIME") {
                            mediaObj["episodes"].asSafeStr()?.toDoubleOrNull()
                        } else {
                            mediaObj["chapters"].asSafeStr()?.toDoubleOrNull()
                        }
                        val score = entryObj["score"].asSafeStr()?.toFloatOrNull()?.let { if (it > 0f) it else null }
                        val trackingStatus = when (entryObj["status"].asSafeStr()) {
                            "CURRENT" -> com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS
                            "PLANNING" -> com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK
                            "COMPLETED" -> com.omnitrack.app.data.model.TrackingStatus.COMPLETED
                            "PAUSED" -> com.omnitrack.app.data.model.TrackingStatus.ON_HOLD
                            "DROPPED" -> com.omnitrack.app.data.model.TrackingStatus.DROPPED
                            else -> com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS
                        }
                        val incoming = MediaItemEntity(
                            title = title,
                            nativeTitle = nativeTitle,
                            synopsis = desc,
                            coverUrl = cover,
                            bannerUrl = banner,
                            mediaType = resolvedType,
                            status = trackingStatus,
                            currentProgress = progress,
                            totalProgress = totalProg,
                            volumesProgress = entryObj["progressVolumes"]?.asSafeInt() ?: 0,
                            rewatchCount = entryObj["repeat"]?.asSafeInt() ?: 0,
                            personalNotes = entryObj["notes"]?.asSafeStr(),
                            startedAtMs = parseFuzzyDateToEpochMs(entryObj["startedAt"].asSafeObj()),
                            finishedAtMs = parseFuzzyDateToEpochMs(entryObj["completedAt"].asSafeObj()),
                            userRating = score,
                            anilistId = alId,
                            malId = mediaObj["idMal"].asSafeLong()
                        )
                        when (smartMergeIncomingItem(incoming)) {
                            MergeOutcome.IMPORTED -> imported++
                            MergeOutcome.UPDATED -> updated++
                            MergeOutcome.SKIPPED -> skipped++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return@withContext TrackerImportResult(imported, updated, skipped, e.message ?: "AniList import failed")
        }
        onStatus("AniList: imported $imported, updated $updated")
        TrackerImportResult(imported, updated, skipped)
    }

    suspend fun importMalLibrary(
        onStatus: (String) -> Unit = {}
    ): TrackerImportResult = withContext(Dispatchers.IO) {
        val token = tokenManager.tokens.firstOrNull()?.malToken?.trim().orEmpty()
        if (token.isBlank()) return@withContext TrackerImportResult(0, 0, 0, "Connect MyAnimeList first")
        onStatus("Fetching MyAnimeList library...")
        var imported = 0
        var updated = 0
        var skipped = 0
        var lastHttpError: Int? = null
        try {
            for (isAnime in listOf(true, false)) {
                val endpoint = if (isAnime) {
                    "https://api.myanimelist.net/v2/users/@me/animelist?fields=list_status{status,score,num_episodes_watched,num_times_rewatched,comments,start_date,finish_date},num_episodes,main_picture,start_date,media_type&limit=200"
                } else {
                    "https://api.myanimelist.net/v2/users/@me/mangalist?fields=list_status{status,score,num_chapters_read,num_volumes_read,num_times_reread,comments,start_date,finish_date},num_chapters,main_picture,start_date,media_type&limit=200"
                }
                var nextUrl: String? = endpoint
                var page = 0
                while (nextUrl != null && page < 20) {
                    page++
                    val (nodes, pagingNext) = NetworkPacer.withGeneralPacing {
                        val req = Request.Builder()
                            .url(nextUrl!!)
                            .header("Authorization", "Bearer $token")
                            .header("User-Agent", "OmniTrack/1.0 (Android)")
                            .get()
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                lastHttpError = resp.code
                                AppLogger.w("TrackerSync", "service=MAL op=import http=${resp.code}")
                                return@withGeneralPacing null to null
                            }
                            val root = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                            root?.get("data").asSafeArr() to root?.get("paging").asSafeObj()?.get("next").asSafeStr()
                        }
                    }
                    if (nodes == null) break
                    for (el in nodes) {
                        val wrapper = el.asSafeObj() ?: continue
                        val node = wrapper["node"].asSafeObj() ?: continue
                        val listStatus = wrapper["list_status"].asSafeObj()
                        val malId = node["id"].asSafeLong() ?: continue
                        val title = node["title"].asSafeStr() ?: continue
                        val pic = node["main_picture"].asSafeObj()
                        val cover = (pic?.get("large").asSafeStr() ?: pic?.get("medium").asSafeStr()).orEmpty()
                        val year = node["start_date"].asSafeStr()?.take(4)
                        val fmt = node["media_type"].asSafeStr()?.lowercase().orEmpty()
                        val resolvedType = when {
                            isAnime -> com.omnitrack.app.data.model.MediaType.ANIME
                            "novel" in fmt -> com.omnitrack.app.data.model.MediaType.NOVEL
                            else -> com.omnitrack.app.data.model.MediaType.MANGA
                        }
                        val statusRaw = listStatus?.get("status").asSafeStr()?.lowercase().orEmpty()
                        val status = when (statusRaw) {
                            "watching", "reading" -> com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS
                            "completed" -> com.omnitrack.app.data.model.TrackingStatus.COMPLETED
                            "on_hold" -> com.omnitrack.app.data.model.TrackingStatus.ON_HOLD
                            "dropped" -> com.omnitrack.app.data.model.TrackingStatus.DROPPED
                            else -> com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK
                        }
                        val prog = (if (isAnime) listStatus?.get("num_episodes_watched") else listStatus?.get("num_chapters_read"))
                            ?.asSafeInt()?.toDouble() ?: 0.0
                        val total = (if (isAnime) node["num_episodes"] else node["num_chapters"])
                            ?.asSafeInt()?.takeIf { it > 0 }?.toDouble()
                        val score = listStatus?.get("score")?.asSafeInt()?.takeIf { it > 0 }?.toFloat()
                        val vols = if (!isAnime) listStatus?.get("num_volumes_read")?.asSafeInt() ?: 0 else 0
                        val rewatches = (if (isAnime) listStatus?.get("num_times_rewatched") else listStatus?.get("num_times_reread"))?.asSafeInt() ?: 0
                        val note = listStatus?.get("comments")?.asSafeStr()
                        val startMs = parseYmdToEpochMs(listStatus?.get("start_date")?.asSafeStr())
                        val finishMs = parseYmdToEpochMs(listStatus?.get("finish_date")?.asSafeStr())
                        when (smartMergeIncomingItem(
                            MediaItemEntity(
                                title = title,
                                coverUrl = cover,
                                mediaType = resolvedType,
                                status = status,
                                currentProgress = prog,
                                totalProgress = total,
                                volumesProgress = vols,
                                rewatchCount = rewatches,
                                personalNotes = note,
                                startedAtMs = startMs,
                                finishedAtMs = finishMs,
                                userRating = score,
                                releaseYear = year,
                                malId = malId
                            )
                        )) {
                            MergeOutcome.IMPORTED -> imported++
                            MergeOutcome.UPDATED -> updated++
                            MergeOutcome.SKIPPED -> skipped++
                        }
                    }
                    nextUrl = pagingNext
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=MAL op=import ex=${e.javaClass.simpleName}")
            return@withContext TrackerImportResult(imported, updated, skipped, "MAL import failed (${e.javaClass.simpleName})")
        }
        if (imported == 0 && updated == 0 && lastHttpError != null) {
            return@withContext TrackerImportResult(0, 0, skipped, "MyAnimeList HTTP $lastHttpError")
        }
        onStatus("MyAnimeList: imported $imported, updated $updated")
        TrackerImportResult(imported, updated, skipped)
    }

    suspend fun importTraktLibrary(
        onStatus: (String) -> Unit = {}
    ): TrackerImportResult = withContext(Dispatchers.IO) {
        val token = tokenManager.tokens.firstOrNull()?.traktToken?.trim().orEmpty()
        if (token.isBlank()) return@withContext TrackerImportResult(0, 0, 0, "Connect Trakt first")
        onStatus("Fetching Trakt library...")
        var imported = 0
        var updated = 0
        var skipped = 0
        var lastHttpError: Int? = null
        val clientId = TrackerAccountVerifier.TRAKT_CLIENT_ID
        try {
            suspend fun fetchTraktArr(path: String): JsonArray? = NetworkPacer.withGeneralPacing {
                val req = Request.Builder()
                    .url("https://api.trakt.tv/$path")
                    .header("Authorization", "Bearer $token")
                    .header("trakt-api-version", "2")
                    .header("trakt-api-key", clientId)
                    .header("User-Agent", "OmniTrack/1.0 (Android)")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code == 204) return@withGeneralPacing JsonArray(emptyList())
                    if (!resp.isSuccessful) {
                        lastHttpError = resp.code
                        AppLogger.w("TrackerSync", "service=TRAKT op=import http=${resp.code}")
                        return@withGeneralPacing null
                    }
                    json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeArr()
                }
            }

            val movieRatings = mutableMapOf<Long, Float>()
            val showRatings = mutableMapOf<Long, Float>()
            fetchTraktArr("sync/ratings/movies")?.forEach { elem ->
                val obj = elem.asSafeObj() ?: return@forEach
                val r = obj["rating"]?.asSafeInt()?.takeIf { it > 0 }?.toFloat() ?: return@forEach
                val mIds = obj["movie"].asSafeObj()?.get("ids").asSafeObj()
                mIds?.get("trakt").asSafeLong()?.let { movieRatings[it] = r }
                mIds?.get("tmdb").asSafeLong()?.let { movieRatings[-it] = r }
            }
            fetchTraktArr("sync/ratings/shows")?.forEach { elem ->
                val obj = elem.asSafeObj() ?: return@forEach
                val r = obj["rating"]?.asSafeInt()?.takeIf { it > 0 }?.toFloat() ?: return@forEach
                val sIds = obj["show"].asSafeObj()?.get("ids").asSafeObj()
                sIds?.get("trakt").asSafeLong()?.let { showRatings[it] = r }
                sIds?.get("tmdb").asSafeLong()?.let { showRatings[-it] = r }
            }

            fetchTraktArr("sync/watched/movies")?.forEach { elem ->
                val movieObj = elem.asSafeObj()?.get("movie").asSafeObj() ?: return@forEach
                val title = movieObj["title"].asSafeStr() ?: return@forEach
                val ids = movieObj["ids"].asSafeObj()
                val year = movieObj["year"].asSafeStr()
                val trId = ids?.get("trakt").asSafeLong()
                val tmId = ids?.get("tmdb").asSafeLong()
                val rating = (trId?.let { movieRatings[it] }) ?: (tmId?.let { movieRatings[-it] })
                when (smartMergeIncomingItem(
                    MediaItemEntity(
                        title = title,
                        coverUrl = "",
                        mediaType = com.omnitrack.app.data.model.MediaType.MOVIE,
                        status = com.omnitrack.app.data.model.TrackingStatus.COMPLETED,
                        currentProgress = 1.0,
                        totalProgress = 1.0,
                        userRating = rating,
                        releaseYear = year,
                        tmdbId = tmId,
                        traktId = trId,
                        imdbId = ids?.get("imdb").asSafeStr()
                    )
                )) {
                    MergeOutcome.IMPORTED -> imported++
                    MergeOutcome.UPDATED -> updated++
                    MergeOutcome.SKIPPED -> skipped++
                }
            }

            fetchTraktArr("sync/watched/shows")?.forEach { elem ->
                val obj = elem.asSafeObj() ?: return@forEach
                val showObj = obj["show"].asSafeObj() ?: return@forEach
                val title = showObj["title"].asSafeStr() ?: return@forEach
                val ids = showObj["ids"].asSafeObj()
                val year = showObj["year"].asSafeStr()
                val aired = showObj["aired_episodes"].asSafeInt()?.takeIf { it > 0 }?.toDouble()
                val watchedCount = obj["seasons"].asSafeArr()?.sumOf { sEl ->
                    sEl.asSafeObj()?.get("episodes").asSafeArr()?.size ?: 0
                }?.toDouble() ?: 0.0
                val status = if (aired != null && watchedCount >= aired) {
                    com.omnitrack.app.data.model.TrackingStatus.COMPLETED
                } else {
                    com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS
                }
                val trId = ids?.get("trakt").asSafeLong()
                val tmId = ids?.get("tmdb").asSafeLong()
                val rating = (trId?.let { showRatings[it] }) ?: (tmId?.let { showRatings[-it] })
                when (smartMergeIncomingItem(
                    MediaItemEntity(
                        title = title,
                        coverUrl = "",
                        mediaType = com.omnitrack.app.data.model.MediaType.SHOW,
                        status = status,
                        currentProgress = watchedCount,
                        totalProgress = aired,
                        userRating = rating,
                        releaseYear = year,
                        tmdbId = tmId,
                        traktId = trId,
                        imdbId = ids?.get("imdb").asSafeStr()
                    )
                )) {
                    MergeOutcome.IMPORTED -> imported++
                    MergeOutcome.UPDATED -> updated++
                    MergeOutcome.SKIPPED -> skipped++
                }
            }

            fetchTraktArr("sync/watchlist")?.forEach { elem ->
                val obj = elem.asSafeObj() ?: return@forEach
                val typeStr = obj["type"].asSafeStr()?.lowercase()
                val mediaObj = when (typeStr) {
                    "movie" -> obj["movie"].asSafeObj()
                    "show" -> obj["show"].asSafeObj()
                    else -> null
                } ?: return@forEach
                val title = mediaObj["title"].asSafeStr() ?: return@forEach
                val ids = mediaObj["ids"].asSafeObj()
                val resolvedType = if (typeStr == "movie") com.omnitrack.app.data.model.MediaType.MOVIE else com.omnitrack.app.data.model.MediaType.SHOW
                when (smartMergeIncomingItem(
                    MediaItemEntity(
                        title = title,
                        coverUrl = "",
                        mediaType = resolvedType,
                        status = com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK,
                        releaseYear = mediaObj["year"].asSafeStr(),
                        tmdbId = ids?.get("tmdb").asSafeLong(),
                        traktId = ids?.get("trakt").asSafeLong(),
                        imdbId = ids?.get("imdb").asSafeStr()
                    )
                )) {
                    MergeOutcome.IMPORTED -> imported++
                    MergeOutcome.UPDATED -> updated++
                    MergeOutcome.SKIPPED -> skipped++
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=TRAKT op=import ex=${e.javaClass.simpleName}")
            return@withContext TrackerImportResult(imported, updated, skipped, "Trakt import failed (${e.javaClass.simpleName})")
        }
        if (imported == 0 && updated == 0 && lastHttpError != null) {
            return@withContext TrackerImportResult(0, 0, skipped, "Trakt HTTP $lastHttpError")
        }
        onStatus("Trakt: imported $imported, updated $updated")
        TrackerImportResult(imported, updated, skipped)
    }

    suspend fun importSimklLibrary(
        onStatus: (String) -> Unit = {}
    ): TrackerImportResult = withContext(Dispatchers.IO) {
        val token = tokenManager.tokens.firstOrNull()?.simklToken?.trim().orEmpty()
        if (token.isBlank()) return@withContext TrackerImportResult(0, 0, 0, "Connect SIMKL first")
        onStatus("Fetching SIMKL library...")
        var imported = 0
        var updated = 0
        var skipped = 0
        var httpError: Int? = null
        var isEmpty204 = false
        try {
            val root = NetworkPacer.withGeneralPacing {
                val req = Request.Builder()
                    .url("https://api.simkl.com/sync/all-items/")
                    .header("Authorization", "Bearer $token")
                    .header("simkl-api-key", TrackerAccountVerifier.SIMKL_CLIENT_ID)
                    .header("User-Agent", "OmniTrack/1.0 (Android)")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code == 204) {
                        isEmpty204 = true
                        return@withGeneralPacing null
                    }
                    if (!resp.isSuccessful) {
                        httpError = resp.code
                        AppLogger.w("TrackerSync", "service=SIMKL op=import http=${resp.code}")
                        return@withGeneralPacing null
                    }
                    val rawBody = resp.body?.string().orEmpty()
                    if (rawBody.isBlank()) null else json.parseToJsonElement(rawBody).asSafeObj()
                }
            }
            if (isEmpty204) {
                onStatus("SIMKL: 0 items in library")
                return@withContext TrackerImportResult(0, 0, 0)
            }
            if (httpError != null) {
                return@withContext TrackerImportResult(0, 0, 0, "SIMKL HTTP $httpError")
            }
            if (root == null) {
                return@withContext TrackerImportResult(0, 0, 0, "Failed to read SIMKL library")
            }

            suspend fun processCategory(key: String, mediaObjField: String, defaultType: com.omnitrack.app.data.model.MediaType) {
                root[key].asSafeArr()?.forEach { el ->
                    val entry = el.asSafeObj() ?: return@forEach
                    val mObj = entry[mediaObjField].asSafeObj() ?: return@forEach
                    val title = mObj["title"].asSafeStr() ?: return@forEach
                    val ids = mObj["ids"].asSafeObj()
                    val poster = mObj["poster"].asSafeStr()?.let { "https://simkl.in/posters/${it}_m.jpg" }.orEmpty()
                    val year = mObj["year"].asSafeStr()
                    val simklStatus = entry["status"].asSafeStr()?.lowercase().orEmpty()
                    val status = when (simklStatus) {
                        "watching" -> com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS
                        "completed" -> com.omnitrack.app.data.model.TrackingStatus.COMPLETED
                        "hold" -> com.omnitrack.app.data.model.TrackingStatus.ON_HOLD
                        "dropped" -> com.omnitrack.app.data.model.TrackingStatus.DROPPED
                        else -> com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK
                    }
                    val watchedEps = entry["watched_episodes_count"].asSafeInt()?.toDouble()
                        ?: if (defaultType == com.omnitrack.app.data.model.MediaType.MOVIE && status == com.omnitrack.app.data.model.TrackingStatus.COMPLETED) 1.0 else 0.0
                    val totalEps = entry["total_episodes_count"].asSafeInt()?.takeIf { it > 0 }?.toDouble()
                    val rating = entry["user_rating"].asSafeInt()?.takeIf { it > 0 }?.toFloat()
                    when (smartMergeIncomingItem(
                        MediaItemEntity(
                            title = title,
                            coverUrl = poster,
                            mediaType = defaultType,
                            status = status,
                            currentProgress = watchedEps,
                            totalProgress = totalEps,
                            userRating = rating,
                            releaseYear = year,
                            simklId = ids?.get("simkl").asSafeLong(),
                            tmdbId = ids?.get("tmdb").asSafeLong(),
                            malId = ids?.get("mal").asSafeLong(),
                            anilistId = ids?.get("anilist").asSafeLong(),
                            imdbId = ids?.get("imdb").asSafeStr()
                        )
                    )) {
                        MergeOutcome.IMPORTED -> imported++
                        MergeOutcome.UPDATED -> updated++
                        MergeOutcome.SKIPPED -> skipped++
                    }
                }
            }

            processCategory("movies", "movie", com.omnitrack.app.data.model.MediaType.MOVIE)
            processCategory("shows", "show", com.omnitrack.app.data.model.MediaType.SHOW)
            processCategory("anime", "show", com.omnitrack.app.data.model.MediaType.ANIME)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=SIMKL op=import ex=${e.javaClass.simpleName}")
            return@withContext TrackerImportResult(imported, updated, skipped, "SIMKL import failed (${e.javaClass.simpleName})")
        }
        onStatus("SIMKL: imported $imported, updated $updated")
        TrackerImportResult(imported, updated, skipped)
    }

    suspend fun importBangumiLibrary(
        onStatus: (String) -> Unit = {}
    ): TrackerImportResult = withContext(Dispatchers.IO) {
        val token = tokenManager.tokens.firstOrNull()?.bangumiToken?.trim().orEmpty()
        if (token.isBlank()) return@withContext TrackerImportResult(0, 0, 0, "Connect Bangumi first")
        onStatus("Fetching Bangumi library...")
        var imported = 0
        var updated = 0
        var skipped = 0
        var httpError: Int? = null
        try {
            val username = NetworkPacer.withBangumiPacing {
                val meReq = Request.Builder()
                    .url("https://api.bgm.tv/v0/me")
                    .header("Authorization", "Bearer $token")
                    .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                    .get()
                    .build()
                client.newCall(meReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        httpError = resp.code
                        AppLogger.w("TrackerSync", "service=BANGUMI op=me http=${resp.code}")
                        return@withBangumiPacing null
                    }
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                    obj?.get("username").asSafeStr() ?: obj?.get("id").asSafeStr()
                }
            } ?: return@withContext TrackerImportResult(
                0,
                0,
                0,
                httpError?.let { "Bangumi HTTP $it" } ?: "Could not fetch Bangumi profile"
            )

            var offset = 0
            while (offset < 1000) {
                val dataArr = NetworkPacer.withBangumiPacing {
                    val colReq = Request.Builder()
                        .url("https://api.bgm.tv/v0/users/$username/collections?limit=50&offset=$offset")
                        .header("Authorization", "Bearer $token")
                        .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                        .get()
                        .build()
                    client.newCall(colReq).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            httpError = resp.code
                            AppLogger.w("TrackerSync", "service=BANGUMI op=import http=${resp.code}")
                            return@withBangumiPacing null
                        }
                        json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()?.get("data").asSafeArr()
                    }
                } ?: break
                if (dataArr.isEmpty()) break

                for (el in dataArr) {
                    val obj = el.asSafeObj() ?: continue
                    val subj = obj["subject"].asSafeObj() ?: continue
                    val bgmId = (obj["subject_id"].asSafeLong() ?: subj["id"].asSafeLong()) ?: continue
                    val title = subj["name_cn"].asSafeStr() ?: subj["name"].asSafeStr() ?: continue
                    val native = subj["name"].asSafeStr()
                    val cover = subj["images"].asSafeObj()?.get("large").asSafeStr()
                        ?: subj["images"].asSafeObj()?.get("common").asSafeStr().orEmpty()
                    val resolvedType = when (obj["subject_type"].asSafeInt() ?: subj["type"].asSafeInt()) {
                        1 -> com.omnitrack.app.data.model.MediaType.MANGA
                        4 -> com.omnitrack.app.data.model.MediaType.GAME
                        else -> com.omnitrack.app.data.model.MediaType.ANIME
                    }
                    val status = when (obj["type"].asSafeInt()) {
                        2 -> com.omnitrack.app.data.model.TrackingStatus.COMPLETED
                        3 -> com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS
                        4 -> com.omnitrack.app.data.model.TrackingStatus.ON_HOLD
                        5 -> com.omnitrack.app.data.model.TrackingStatus.DROPPED
                        else -> com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK
                    }
                    val progress = obj["ep_status"].asSafeInt()?.toDouble() ?: 0.0
                    val total = subj["eps"].asSafeInt()?.takeIf { it > 0 }?.toDouble()
                    val rating = obj["rate"].asSafeInt()?.takeIf { it > 0 }?.toFloat()
                    when (smartMergeIncomingItem(
                        MediaItemEntity(
                            title = title,
                            nativeTitle = native.takeIf { it != title },
                            coverUrl = cover,
                            mediaType = resolvedType,
                            status = status,
                            currentProgress = progress,
                            totalProgress = total,
                            userRating = rating,
                            releaseYear = subj["date"].asSafeStr()?.take(4),
                            bangumiId = bgmId
                        )
                    )) {
                        MergeOutcome.IMPORTED -> imported++
                        MergeOutcome.UPDATED -> updated++
                        MergeOutcome.SKIPPED -> skipped++
                    }
                }
                if (dataArr.size < 50) break
                offset += 50
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=BANGUMI op=import ex=${e.javaClass.simpleName}")
            return@withContext TrackerImportResult(imported, updated, skipped, "Bangumi import failed (${e.javaClass.simpleName})")
        }
        if (imported == 0 && updated == 0 && httpError != null) {
            return@withContext TrackerImportResult(0, 0, skipped, "Bangumi HTTP $httpError")
        }
        onStatus("Bangumi: imported $imported, updated $updated")
        TrackerImportResult(imported, updated, skipped)
    }

    suspend fun importVndbLibrary(
        onStatus: (String) -> Unit = {}
    ): TrackerImportResult = withContext(Dispatchers.IO) {
        val token = tokenManager.tokens.firstOrNull()?.vndbToken?.trim().orEmpty()
        if (token.isBlank()) return@withContext TrackerImportResult(0, 0, 0, "Connect VNDB token first")
        onStatus("Fetching VNDB visual novel list...")
        var imported = 0
        var updated = 0
        var skipped = 0
        var httpError: Int? = null
        try {
            val userId = NetworkPacer.withVndbPacing {
                val req = Request.Builder()
                    .url("https://api.vndb.org/kana/authinfo")
                    .header("Authorization", "Token $token")
                    .header("User-Agent", "OmniTrack/1.0 (Android)")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        httpError = resp.code
                        AppLogger.w("TrackerSync", "service=VNDB op=me http=${resp.code}")
                        return@withVndbPacing null
                    }
                    json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()?.get("id").asSafeStr()
                }
            } ?: return@withContext TrackerImportResult(
                0,
                0,
                0,
                httpError?.let { "VNDB HTTP $it" } ?: "Could not verify VNDB user"
            )

            var page = 1
            while (page <= 20) {
                val (resultsArr, hasMore) = NetworkPacer.withVndbPacing {
                    val payload = buildJsonObject {
                        put("user", userId)
                        put("fields", "id,vote,labels.label,vn{id,title,released,image{url}}")
                        put("results", 100)
                        put("page", page)
                    }.toString()
                    val req = Request.Builder()
                        .url("https://api.vndb.org/kana/ulist")
                        .header("Authorization", "Token $token")
                        .header("User-Agent", "OmniTrack/1.0 (Android)")
                        .post(payload.toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            httpError = resp.code
                            AppLogger.w("TrackerSync", "service=VNDB op=import http=${resp.code}")
                            return@withVndbPacing null to false
                        }
                        val root = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                        val arr = root?.get("results").asSafeArr()
                        val more = root?.get("more")?.jsonPrimitive?.booleanOrNull == true
                        arr to more
                    }
                }
                if (resultsArr == null) {
                    if (page == 1 && imported == 0 && updated == 0) {
                        return@withContext TrackerImportResult(
                            0,
                            0,
                            skipped,
                            httpError?.let { "VNDB HTTP $it" } ?: "Failed to read VNDB list"
                        )
                    }
                    break
                }

                for (el in resultsArr) {
                    val obj = el.asSafeObj() ?: continue
                    val vn = obj["vn"].asSafeObj() ?: continue
                    val vndbId = vn["id"].asSafeStr() ?: obj["id"].asSafeStr() ?: continue
                    val title = vn["title"].asSafeStr() ?: continue
                    val cover = vn["image"].asSafeObj()?.get("url").asSafeStr().orEmpty()
                    val year = vn["released"].asSafeStr()?.take(4)
                    val labels = obj["labels"].asSafeArr()?.mapNotNull { it.asSafeObj()?.get("label").asSafeStr()?.lowercase() }.orEmpty()
                    val status = when {
                        labels.any { "finished" in it } -> com.omnitrack.app.data.model.TrackingStatus.COMPLETED
                        labels.any { "playing" in it } -> com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS
                        labels.any { "stalled" in it || "hold" in it } -> com.omnitrack.app.data.model.TrackingStatus.ON_HOLD
                        labels.any { "dropped" in it } -> com.omnitrack.app.data.model.TrackingStatus.DROPPED
                        else -> com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK
                    }
                    val rating = obj["vote"].asSafeInt()?.takeIf { it > 0 }?.let { it / 10f }
                    when (smartMergeIncomingItem(
                        MediaItemEntity(
                            title = title,
                            coverUrl = cover,
                            mediaType = com.omnitrack.app.data.model.MediaType.VISUAL_NOVEL,
                            status = status,
                            userRating = rating,
                            releaseYear = year,
                            vndbId = vndbId
                        )
                    )) {
                        MergeOutcome.IMPORTED -> imported++
                        MergeOutcome.UPDATED -> updated++
                        MergeOutcome.SKIPPED -> skipped++
                    }
                }
                if (!hasMore || resultsArr.isEmpty()) break
                page++
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=VNDB op=import ex=${e.javaClass.simpleName}")
            return@withContext TrackerImportResult(imported, updated, skipped, "VNDB import failed (${e.javaClass.simpleName})")
        }
        onStatus("VNDB: imported $imported, updated $updated")
        TrackerImportResult(imported, updated, skipped)
    }

    /**
     * Queries a single tracker directly via its native API so Search & Rebind
     * shows real covers, formats, years, and genuine provider IDs (never cross-provider guesses).
     */
    suspend fun searchTrackerDirectly(
        trackerKey: String,
        query: String,
        mediaType: MediaType
    ): List<RemoteTrackerEntrySnapshot> = withContext(Dispatchers.IO) {
        val cleanQ = query.trim()
        if (cleanQ.length < 2) return@withContext emptyList()
        val tokens = tokenManager.tokens.firstOrNull() ?: TrackerTokens()
        when (trackerKey.uppercase()) {
            "ANILIST" -> searchAniListDirect(cleanQ, mediaType)
            "MAL" -> searchMalDirect(cleanQ, mediaType)
            "BANGUMI" -> searchBangumiDirect(cleanQ, mediaType)
            "TRAKT" -> searchTraktDirect(cleanQ, mediaType)
            "SIMKL" -> searchSimklDirect(cleanQ, mediaType)
            "TMDB" -> searchTmdbDirect(cleanQ, mediaType)
            "HARDCOVER" -> {
                val hcToken = tokens.hardcoverToken?.trim().orEmpty()
                if (hcToken.isBlank()) emptyList()
                else HardcoverApiClient(client).searchBooks(hcToken, cleanQ, limit = 12).mapNotNull { r ->
                    val id = r.hardcoverId ?: return@mapNotNull null
                    RemoteTrackerEntrySnapshot(
                        trackerKey = "HARDCOVER",
                        remoteId = id,
                        title = r.title,
                        coverUrl = r.coverUrl.takeIf { it.isNotBlank() },
                        format = r.bookAuthor ?: "Book",
                        year = r.releaseYear,
                        remoteTotal = r.totalProgress,
                        remoteScore10 = r.communityScore
                    )
                }
            }
            "VNDB" -> searchVndbDirect(cleanQ)
            "COMICK" -> searchComickDirect(cleanQ)
            "STEAM" -> searchSteamDirect(cleanQ)
            "IGDB" -> {
                val id = PreferenceManager.cachedIgdbClientId
                val sec = PreferenceManager.cachedIgdbClientSecret
                if (id.isBlank() || sec.isBlank()) emptyList()
                else IgdbApiClient(client).searchGamesList(id, sec, cleanQ, limit = 12).mapNotNull { r ->
                    val gId = r.igdbId?.toString() ?: return@mapNotNull null
                    RemoteTrackerEntrySnapshot(
                        trackerKey = "IGDB",
                        remoteId = gId,
                        title = r.title,
                        coverUrl = r.coverUrl.takeIf { it.isNotBlank() },
                        format = "Game",
                        year = r.releaseYear,
                        remoteScore10 = r.communityScore
                    )
                }
            }
            else -> emptyList()
        }
    }

    private suspend fun searchAniListDirect(query: String, mediaType: MediaType): List<RemoteTrackerEntrySnapshot> =
        NetworkPacer.withAniListSearchPacing {
            try {
                val alType = when (mediaType) {
                    MediaType.MANGA, MediaType.NOVEL, MediaType.COMIC, MediaType.BOOK -> "MANGA"
                    else -> "ANIME"
                }
                val gql = """
                    query (${'$'}q: String, ${'$'}type: MediaType) {
                      Page(page: 1, perPage: 12) {
                        media(search: ${'$'}q, type: ${'$'}type, sort: [SEARCH_MATCH]) {
                          id
                          format
                          episodes
                          chapters
                          averageScore
                          startDate { year }
                          title { english romaji native }
                          coverImage { extraLarge large }
                        }
                      }
                    }
                """.trimIndent()
                val payload = buildJsonObject {
                    put("query", gql)
                    putJsonObject("variables") {
                        put("q", query)
                        put("type", alType)
                    }
                }.toString()
                client.newCall(
                    Request.Builder()
                        .url("https://graphql.anilist.co")
                        .post(payload.toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@withAniListSearchPacing emptyList()
                    val arr = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                        ?.get("data").asSafeObj()?.get("Page").asSafeObj()?.get("media").asSafeArr()
                        ?: return@withAniListSearchPacing emptyList()
                    arr.mapNotNull { el ->
                        val o = el.asSafeObj() ?: return@mapNotNull null
                        val id = o["id"].asSafeStr() ?: return@mapNotNull null
                        val tObj = o["title"].asSafeObj()
                        val title = tObj?.get("english").asSafeStr() ?: tObj?.get("romaji").asSafeStr()
                            ?: tObj?.get("native").asSafeStr() ?: return@mapNotNull null
                        val covObj = o["coverImage"].asSafeObj()
                        val cov = (covObj?.get("extraLarge").asSafeStr() ?: covObj?.get("large").asSafeStr())
                            ?.let { LosslessCoverMapper.toLosslessUrl(it) }
                        val tot = (o["episodes"].asSafeInt() ?: o["chapters"].asSafeInt())?.toDouble()
                        RemoteTrackerEntrySnapshot(
                            trackerKey = "ANILIST",
                            remoteId = id,
                            title = title,
                            coverUrl = cov,
                            format = o["format"].asSafeStr(),
                            year = o["startDate"].asSafeObj()?.get("year").asSafeStr(),
                            remoteTotal = tot,
                            remoteScore10 = o["averageScore"].asSafeInt()?.let { it / 10f }
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

    private suspend fun searchMalDirect(query: String, mediaType: MediaType): List<RemoteTrackerEntrySnapshot> =
        NetworkPacer.withJikanPacing {
            try {
                val seg = if (mediaType == MediaType.MANGA || mediaType == MediaType.NOVEL) "manga" else "anime"
                val enc = URLEncoder.encode(query, "UTF-8")
                client.newCall(
                    Request.Builder()
                        .url("https://api.jikan.moe/v4/$seg?q=$enc&limit=12")
                        .header("User-Agent", "OmniTrack/1.0")
                        .get()
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@withJikanPacing emptyList()
                    val arr = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                        ?.get("data").asSafeArr() ?: return@withJikanPacing emptyList()
                    arr.mapNotNull { el ->
                        val o = el.asSafeObj() ?: return@mapNotNull null
                        val id = o["mal_id"].asSafeStr() ?: return@mapNotNull null
                        val title = o["title_english"].asSafeStr() ?: o["title"].asSafeStr() ?: return@mapNotNull null
                        val imgObj = o["images"].asSafeObj()?.get("jpg").asSafeObj()
                        val cov = (imgObj?.get("large_image_url").asSafeStr() ?: imgObj?.get("image_url").asSafeStr())
                            ?.let { LosslessCoverMapper.toLosslessUrl(it) }
                        val year = o["year"].asSafeStr()
                            ?: o["aired"].asSafeObj()?.get("prop").asSafeObj()?.get("from").asSafeObj()?.get("year").asSafeStr()
                        val tot = (o["episodes"].asSafeInt() ?: o["chapters"].asSafeInt())?.toDouble()
                        RemoteTrackerEntrySnapshot(
                            trackerKey = "MAL",
                            remoteId = id,
                            title = title,
                            coverUrl = cov,
                            format = o["type"].asSafeStr(),
                            year = year,
                            remoteTotal = tot,
                            remoteScore10 = (o["score"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

    private suspend fun searchBangumiDirect(query: String, mediaType: MediaType): List<RemoteTrackerEntrySnapshot> =
        NetworkPacer.withBangumiPacing {
            try {
                val bgmType = when (mediaType) {
                    MediaType.MANGA, MediaType.NOVEL, MediaType.BOOK -> 1
                    MediaType.GAME, MediaType.VISUAL_NOVEL -> 4
                    else -> 2
                }
                val enc = URLEncoder.encode(query, "UTF-8")
                client.newCall(
                    Request.Builder()
                        .url("https://api.bgm.tv/search/subject/$enc?type=$bgmType&responseGroup=medium&max_results=10")
                        .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                        .get()
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@withBangumiPacing emptyList()
                    val arr = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                        ?.get("list").asSafeArr() ?: return@withBangumiPacing emptyList()
                    arr.mapNotNull { el ->
                        val o = el.asSafeObj() ?: return@mapNotNull null
                        val id = o["id"].asSafeStr() ?: return@mapNotNull null
                        val title = o["name_cn"].asSafeStr() ?: o["name"].asSafeStr() ?: return@mapNotNull null
                        val imgObj = o["images"].asSafeObj()
                        val cov = (imgObj?.get("large").asSafeStr() ?: imgObj?.get("common").asSafeStr())
                            ?.let { LosslessCoverMapper.toLosslessUrl(it) }
                        RemoteTrackerEntrySnapshot(
                            trackerKey = "BANGUMI",
                            remoteId = id,
                            title = title,
                            coverUrl = cov,
                            format = when (o["type"].asSafeInt()) {
                                1 -> "Book/Manga"
                                4 -> "Game"
                                2 -> "Anime"
                                else -> "Subject"
                            },
                            year = o["air_date"].asSafeStr()?.take(4),
                            remoteTotal = o["eps_count"].asSafeInt()?.toDouble(),
                            remoteScore10 = o["rating"].asSafeObj()?.get("score")?.let {
                                (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.floatOrNull
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

    private suspend fun searchTraktDirect(query: String, mediaType: MediaType): List<RemoteTrackerEntrySnapshot> =
        NetworkPacer.withGeneralPacing {
            try {
                val traktType = if (mediaType == MediaType.MOVIE) "movie" else "show"
                val enc = URLEncoder.encode(query, "UTF-8")
                client.newCall(
                    Request.Builder()
                        .url("https://api.trakt.tv/search/$traktType?query=$enc&limit=10")
                        .header("trakt-api-version", "2")
                        .header("trakt-api-key", TrackerAccountVerifier.TRAKT_CLIENT_ID)
                        .header("User-Agent", "OmniTrack/1.0 (Android)")
                        .get()
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                    val arr = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeArr()
                        ?: return@withGeneralPacing emptyList()
                    arr.mapNotNull { el ->
                        val wrapper = el.asSafeObj() ?: return@mapNotNull null
                        val m = wrapper[traktType].asSafeObj() ?: return@mapNotNull null
                        val ids = m["ids"].asSafeObj()
                        val traktId = ids?.get("trakt").asSafeStr() ?: return@mapNotNull null
                        val title = m["title"].asSafeStr() ?: return@mapNotNull null
                        val tmdbId = ids?.get("tmdb").asSafeLong()
                        val cov = fetchTmdbPosterCached(if (mediaType == MediaType.MOVIE) "movie" else "tv", tmdbId)
                        RemoteTrackerEntrySnapshot(
                            trackerKey = "TRAKT",
                            remoteId = traktId,
                            title = title,
                            coverUrl = cov,
                            format = if (traktType == "movie") "Movie" else "Series",
                            year = m["year"].asSafeStr()
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

    private suspend fun searchSimklDirect(query: String, mediaType: MediaType): List<RemoteTrackerEntrySnapshot> =
        NetworkPacer.withGeneralPacing {
            try {
                val simklType = when (mediaType) {
                    MediaType.MOVIE -> "movie"
                    MediaType.ANIME -> "anime"
                    else -> "tv"
                }
                val enc = URLEncoder.encode(query, "UTF-8")
                client.newCall(
                    Request.Builder()
                        .url("https://api.simkl.com/search/$simklType?q=$enc&limit=10")
                        .header("simkl-api-key", TrackerAccountVerifier.SIMKL_CLIENT_ID)
                        .header("User-Agent", "OmniTrack/1.0 (Android)")
                        .get()
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                    val arr = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeArr()
                        ?: return@withGeneralPacing emptyList()
                    arr.mapNotNull { el ->
                        val o = el.asSafeObj() ?: return@mapNotNull null
                        val id = o["ids"].asSafeObj()?.get("simkl_id").asSafeStr()
                            ?: o["ids"].asSafeObj()?.get("simkl").asSafeStr()
                            ?: return@mapNotNull null
                        val title = o["title"].asSafeStr() ?: return@mapNotNull null
                        val poster = o["poster"].asSafeStr()?.let { "https://simkl.in/posters/${it}_m.jpg" }
                        RemoteTrackerEntrySnapshot(
                            trackerKey = "SIMKL",
                            remoteId = id,
                            title = title,
                            coverUrl = poster,
                            format = simklType.uppercase(),
                            year = o["year"].asSafeStr(),
                            remoteTotal = o["ep_count"].asSafeInt()?.toDouble()
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

    private suspend fun searchTmdbDirect(query: String, mediaType: MediaType): List<RemoteTrackerEntrySnapshot> =
        NetworkPacer.withTmdbPacing {
            val key = PreferenceManager.cachedTmdbApiKey
            if (key.isBlank()) return@withTmdbPacing emptyList()
            try {
                val seg = if (mediaType == MediaType.MOVIE) "movie" else "tv"
                val enc = URLEncoder.encode(query, "UTF-8")
                client.newCall(
                    Request.Builder()
                        .url("https://api.themoviedb.org/3/search/$seg?api_key=$key&query=$enc")
                        .get()
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@withTmdbPacing emptyList()
                    val arr = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                        ?.get("results").asSafeArr() ?: return@withTmdbPacing emptyList()
                    arr.take(12).mapNotNull { el ->
                        val o = el.asSafeObj() ?: return@mapNotNull null
                        val id = o["id"].asSafeStr() ?: return@mapNotNull null
                        val title = o["title"].asSafeStr() ?: o["name"].asSafeStr() ?: return@mapNotNull null
                        val poster = o["poster_path"].asSafeStr()?.let {
                            LosslessCoverMapper.toLosslessUrl("https://image.tmdb.org/t/p/w500$it")
                        }
                        val year = (o["release_date"].asSafeStr() ?: o["first_air_date"].asSafeStr())?.take(4)
                        val score = (o["vote_average"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull
                        RemoteTrackerEntrySnapshot(
                            trackerKey = "TMDB",
                            remoteId = id,
                            title = title,
                            coverUrl = poster,
                            format = if (seg == "movie") "Movie" else "TV Series",
                            year = year,
                            remoteScore10 = score
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

    private suspend fun searchVndbDirect(query: String): List<RemoteTrackerEntrySnapshot> =
        NetworkPacer.withVndbPacing {
            try {
                val payload = buildJsonObject {
                    put("filters", buildJsonArray { add(JsonPrimitive("search")); add(JsonPrimitive("=")); add(JsonPrimitive(query)) })
                    put("fields", "id,title,released,rating,image{url}")
                    put("results", 12)
                }.toString()
                client.newCall(
                    Request.Builder()
                        .url("https://api.vndb.org/kana/vn")
                        .header("User-Agent", "OmniTrack/1.0 (Android)")
                        .post(payload.toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@withVndbPacing emptyList()
                    val arr = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                        ?.get("results").asSafeArr() ?: return@withVndbPacing emptyList()
                    arr.mapNotNull { el ->
                        val o = el.asSafeObj() ?: return@mapNotNull null
                        val id = o["id"].asSafeStr() ?: return@mapNotNull null
                        val title = o["title"].asSafeStr() ?: return@mapNotNull null
                        val cov = o["image"].asSafeObj()?.get("url").asSafeStr()
                        RemoteTrackerEntrySnapshot(
                            trackerKey = "VNDB",
                            remoteId = id,
                            title = title,
                            coverUrl = cov,
                            format = "Visual Novel",
                            year = o["released"].asSafeStr()?.take(4),
                            remoteScore10 = o["rating"].asSafeInt()?.let { it / 10f }
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

    private suspend fun searchComickDirect(query: String): List<RemoteTrackerEntrySnapshot> =
        NetworkPacer.withGeneralPacing {
            try {
                val enc = URLEncoder.encode(query, "UTF-8")
                client.newCall(
                    Request.Builder()
                        .url("https://api.comick.io/v1.0/search?q=$enc&limit=10")
                        .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                        .header("Referer", "https://comick.io")
                        .get()
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                    val arr = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeArr()
                        ?: return@withGeneralPacing emptyList()
                    arr.mapNotNull { el ->
                        val o = el.asSafeObj() ?: return@mapNotNull null
                        val slug = o["slug"].asSafeStr() ?: o["hid"].asSafeStr() ?: return@mapNotNull null
                        val title = o["title"].asSafeStr() ?: return@mapNotNull null
                        val b2key = o["md_covers"].asSafeArr()?.firstOrNull()?.asSafeObj()?.get("b2key").asSafeStr()
                        RemoteTrackerEntrySnapshot(
                            trackerKey = "COMICK",
                            remoteId = slug,
                            title = title,
                            coverUrl = b2key?.let { "https://meo.comick.picture/$it" },
                            format = o["country"].asSafeStr()?.uppercase() ?: "Manga",
                            year = o["year"].asSafeStr(),
                            remoteTotal = o["last_chapter"].asSafeStr()?.toDoubleOrNull()
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

    private suspend fun searchSteamDirect(query: String): List<RemoteTrackerEntrySnapshot> =
        NetworkPacer.withSteamPacing {
            try {
                val enc = URLEncoder.encode(query, "UTF-8")
                client.newCall(
                    Request.Builder()
                        .url("https://store.steampowered.com/api/storesearch/?term=$enc&l=english&cc=US")
                        .header("User-Agent", "OmniTrack/1.0 (Android)")
                        .get()
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@withSteamPacing emptyList()
                    val arr = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                        ?.get("items").asSafeArr() ?: return@withSteamPacing emptyList()
                    arr.take(12).mapNotNull { el ->
                        val o = el.asSafeObj() ?: return@mapNotNull null
                        val id = o["id"].asSafeStr() ?: return@mapNotNull null
                        val name = o["name"].asSafeStr() ?: return@mapNotNull null
                        RemoteTrackerEntrySnapshot(
                            trackerKey = "STEAM",
                            remoteId = id,
                            title = name,
                            coverUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$id/library_600x900_2x.jpg",
                            format = "Steam Game"
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

    private fun fetchTmdbPosterCached(seg: String, tmdbId: Long?): String? {
        val key = PreferenceManager.cachedTmdbApiKey
        if (key.isBlank() || tmdbId == null) return null
        return runCatching {
            client.newCall(
                Request.Builder()
                    .url("https://api.themoviedb.org/3/$seg/$tmdbId?api_key=$key")
                    .get()
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val p = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                    ?.get("poster_path").asSafeStr() ?: return@use null
                LosslessCoverMapper.toLosslessUrl("https://image.tmdb.org/t/p/w500$p")
            }
        }.getOrNull()
    }

    /**
     * Fetches the live remote entry metadata (matched poster art, title, format, year, remote progress/status/score/note)
     * for displaying authentic per-tracker matched cards & detecting sync diffs.
     */
    suspend fun fetchMatchedTrackerSnapshot(
        trackerKey: String,
        item: MediaItemEntity
    ): RemoteTrackerEntrySnapshot? = withContext(Dispatchers.IO) {
        val tokens = tokenManager.tokens.firstOrNull() ?: TrackerTokens()
        when (trackerKey.uppercase()) {
            "ANILIST" -> {
                val alId = item.anilistId ?: return@withContext null
                val gql = """
                    query (${'$'}id: Int) {
                      Media(id: ${'$'}id) {
                        id
                        format
                        episodes
                        chapters
                        isFavourite
                        startDate { year }
                        title { english romaji native }
                        coverImage { extraLarge large }
                        mediaListEntry {
                          status
                          progress
                          progressVolumes
                          score(format: POINT_10_DECIMAL)
                          repeat
                          notes
                        }
                      }
                    }
                """.trimIndent()
                val payload = buildJsonObject {
                    put("query", gql)
                    putJsonObject("variables") { put("id", alId.toInt()) }
                }.toString()
                NetworkPacer.withAniListPacing {
                    runCatching {
                        val b = Request.Builder()
                            .url("https://graphql.anilist.co")
                            .post(payload.toRequestBody("application/json".toMediaType()))
                        if (!tokens.anilistToken.isNullOrBlank()) {
                            b.header("Authorization", "Bearer ${tokens.anilistToken}")
                        }
                        client.newCall(b.build()).execute().use { resp ->
                            if (!resp.isSuccessful) return@use null
                            val m = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                                ?.get("data").asSafeObj()?.get("Media").asSafeObj() ?: return@use null
                            val tObj = m["title"].asSafeObj()
                            val title = tObj?.get("english").asSafeStr() ?: tObj?.get("romaji").asSafeStr()
                                ?: tObj?.get("native").asSafeStr() ?: item.title
                            val cov = (m["coverImage"].asSafeObj()?.get("extraLarge").asSafeStr()
                                ?: m["coverImage"].asSafeObj()?.get("large").asSafeStr())
                                ?.let { LosslessCoverMapper.toLosslessUrl(it) }
                            val entry = m["mediaListEntry"].asSafeObj()
                            val st = when (entry?.get("status").asSafeStr()) {
                                "CURRENT" -> TrackingStatus.IN_PROGRESS
                                "PLANNING" -> TrackingStatus.PLAN_TO_TRACK
                                "COMPLETED" -> TrackingStatus.COMPLETED
                                "PAUSED" -> TrackingStatus.ON_HOLD
                                "DROPPED" -> TrackingStatus.DROPPED
                                else -> null
                            }
                            RemoteTrackerEntrySnapshot(
                                trackerKey = "ANILIST",
                                remoteId = alId.toString(),
                                title = title,
                                coverUrl = cov,
                                format = m["format"].asSafeStr(),
                                year = m["startDate"].asSafeObj()?.get("year").asSafeStr(),
                                remoteStatus = st,
                                remoteProgress = entry?.get("progress").asSafeInt()?.toDouble(),
                                remoteTotal = (m["episodes"].asSafeInt() ?: m["chapters"].asSafeInt())?.toDouble(),
                                remoteVolumes = entry?.get("progressVolumes").asSafeInt(),
                                remoteScore10 = (entry?.get("score") as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull,
                                remoteNote = entry?.get("notes").asSafeStr(),
                                isFavorite = m["isFavourite"]?.jsonPrimitive?.booleanOrNull,
                                rewatches = entry?.get("repeat").asSafeInt()
                            )
                        }
                    }.getOrNull()
                }
            }
            "MAL" -> {
                val malId = item.malId ?: return@withContext null
                val seg = if (item.mediaType == MediaType.ANIME) "anime" else "manga"
                if (!tokens.malToken.isNullOrBlank()) {
                    NetworkPacer.withGeneralPacing {
                        runCatching {
                            client.newCall(
                                Request.Builder()
                                    .url("https://api.myanimelist.net/v2/$seg/$malId?fields=id,title,main_picture,media_type,start_date,num_episodes,num_chapters,my_list_status{status,score,num_episodes_watched,num_chapters_read,num_volumes_read,num_times_rewatched,num_times_reread,comments}")
                                    .header("Authorization", "Bearer ${tokens.malToken}")
                                    .header("User-Agent", "OmniTrack/1.0")
                                    .get()
                                    .build()
                            ).execute().use { resp ->
                                if (!resp.isSuccessful) return@use null
                                val o = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj() ?: return@use null
                                val ls = o["my_list_status"].asSafeObj()
                                val pic = o["main_picture"].asSafeObj()
                                val cov = (pic?.get("large").asSafeStr() ?: pic?.get("medium").asSafeStr())
                                    ?.let { LosslessCoverMapper.toLosslessUrl(it) }
                                val st = when (ls?.get("status").asSafeStr()) {
                                    "watching", "reading" -> TrackingStatus.IN_PROGRESS
                                    "completed" -> TrackingStatus.COMPLETED
                                    "on_hold" -> TrackingStatus.ON_HOLD
                                    "dropped" -> TrackingStatus.DROPPED
                                    "plan_to_watch", "plan_to_read" -> TrackingStatus.PLAN_TO_TRACK
                                    else -> null
                                }
                                val prog = (ls?.get("num_episodes_watched") ?: ls?.get("num_chapters_read"))?.asSafeInt()?.toDouble()
                                val tot = (o["num_episodes"] ?: o["num_chapters"])?.asSafeInt()?.takeIf { it > 0 }?.toDouble()
                                RemoteTrackerEntrySnapshot(
                                    trackerKey = "MAL",
                                    remoteId = malId.toString(),
                                    title = o["title"].asSafeStr() ?: item.title,
                                    coverUrl = cov,
                                    format = o["media_type"].asSafeStr()?.uppercase(),
                                    year = o["start_date"].asSafeStr()?.take(4),
                                    remoteStatus = st,
                                    remoteProgress = prog,
                                    remoteTotal = tot,
                                    remoteVolumes = ls?.get("num_volumes_read").asSafeInt(),
                                    remoteScore10 = ls?.get("score").asSafeInt()?.takeIf { it > 0 }?.toFloat(),
                                    remoteNote = ls?.get("comments").asSafeStr(),
                                    rewatches = (ls?.get("num_times_rewatched") ?: ls?.get("num_times_reread"))?.asSafeInt()
                                )
                            }
                        }.getOrNull()
                    }
                } else {
                    NetworkPacer.withJikanPacing {
                        runCatching {
                            client.newCall(
                                Request.Builder()
                                    .url("https://api.jikan.moe/v4/$seg/$malId")
                                    .header("User-Agent", "OmniTrack/1.0")
                                    .get()
                                    .build()
                            ).execute().use { resp ->
                                if (!resp.isSuccessful) return@use null
                                val o = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                                    ?.get("data").asSafeObj() ?: return@use null
                                val jpg = o["images"].asSafeObj()?.get("jpg").asSafeObj()
                                RemoteTrackerEntrySnapshot(
                                    trackerKey = "MAL",
                                    remoteId = malId.toString(),
                                    title = o["title_english"].asSafeStr() ?: o["title"].asSafeStr() ?: item.title,
                                    coverUrl = (jpg?.get("large_image_url").asSafeStr() ?: jpg?.get("image_url").asSafeStr())
                                        ?.let { LosslessCoverMapper.toLosslessUrl(it) },
                                    format = o["type"].asSafeStr(),
                                    year = o["year"].asSafeStr(),
                                    remoteTotal = (o["episodes"] ?: o["chapters"])?.asSafeInt()?.toDouble()
                                )
                            }
                        }.getOrNull()
                    }
                }
            }
            "BANGUMI" -> {
                val bgmId = item.bangumiId ?: return@withContext null
                NetworkPacer.withBangumiPacing {
                    runCatching {
                        val b = Request.Builder()
                            .url("https://api.bgm.tv/v0/subjects/$bgmId")
                            .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                        if (!tokens.bangumiToken.isNullOrBlank()) {
                            b.header("Authorization", "Bearer ${tokens.bangumiToken}")
                        }
                        client.newCall(b.build()).execute().use { resp ->
                            if (!resp.isSuccessful) return@use null
                            val o = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj() ?: return@use null
                            val imgs = o["images"].asSafeObj()
                            RemoteTrackerEntrySnapshot(
                                trackerKey = "BANGUMI",
                                remoteId = bgmId.toString(),
                                title = o["name_cn"].asSafeStr() ?: o["name"].asSafeStr() ?: item.title,
                                coverUrl = (imgs?.get("large").asSafeStr() ?: imgs?.get("common").asSafeStr())
                                    ?.let { LosslessCoverMapper.toLosslessUrl(it) },
                                format = "Bangumi",
                                year = o["date"].asSafeStr()?.take(4),
                                remoteTotal = o["eps"].asSafeInt()?.takeIf { it > 0 }?.toDouble()
                            )
                        }
                    }.getOrNull()
                }
            }
            "TMDB" -> {
                val tId = item.tmdbId ?: return@withContext null
                val seg = if (item.mediaType == MediaType.MOVIE) "movie" else "tv"
                val key = PreferenceManager.cachedTmdbApiKey
                if (key.isBlank()) return@withContext null
                NetworkPacer.withTmdbPacing {
                    runCatching {
                        client.newCall(
                            Request.Builder()
                                .url("https://api.themoviedb.org/3/$seg/$tId?api_key=$key")
                                .get()
                                .build()
                        ).execute().use { resp ->
                            if (!resp.isSuccessful) return@use null
                            val o = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj() ?: return@use null
                            val p = o["poster_path"].asSafeStr()?.let {
                                LosslessCoverMapper.toLosslessUrl("https://image.tmdb.org/t/p/w500$it")
                            }
                            RemoteTrackerEntrySnapshot(
                                trackerKey = "TMDB",
                                remoteId = tId.toString(),
                                title = o["title"].asSafeStr() ?: o["name"].asSafeStr() ?: item.title,
                                coverUrl = p,
                                format = if (seg == "movie") "Movie" else "TV Series",
                                year = (o["release_date"].asSafeStr() ?: o["first_air_date"].asSafeStr())?.take(4),
                                remoteTotal = o["number_of_episodes"].asSafeInt()?.toDouble()
                            )
                        }
                    }.getOrNull()
                }
            }
            "TRAKT" -> {
                val trId = item.traktId ?: return@withContext null
                val seg = if (item.mediaType == MediaType.MOVIE) "movies" else "shows"
                NetworkPacer.withGeneralPacing {
                    runCatching {
                        client.newCall(
                            Request.Builder()
                                .url("https://api.trakt.tv/$seg/$trId?extended=full")
                                .header("trakt-api-version", "2")
                                .header("trakt-api-key", TrackerAccountVerifier.TRAKT_CLIENT_ID)
                                .header("User-Agent", "OmniTrack/1.0 (Android)")
                                .get()
                                .build()
                        ).execute().use { resp ->
                            if (!resp.isSuccessful) return@use null
                            val o = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj() ?: return@use null
                            val tmdbId = o["ids"].asSafeObj()?.get("tmdb").asSafeLong() ?: item.tmdbId
                            RemoteTrackerEntrySnapshot(
                                trackerKey = "TRAKT",
                                remoteId = trId.toString(),
                                title = o["title"].asSafeStr() ?: item.title,
                                coverUrl = fetchTmdbPosterCached(if (item.mediaType == MediaType.MOVIE) "movie" else "tv", tmdbId),
                                format = if (seg == "movies") "Movie" else "TV Series",
                                year = o["year"].asSafeStr(),
                                remoteTotal = o["aired_episodes"].asSafeInt()?.toDouble()
                            )
                        }
                    }.getOrNull()
                }
            }
            "SIMKL" -> {
                val sId = item.simklId ?: return@withContext null
                val seg = when (item.mediaType) {
                    MediaType.MOVIE -> "movies"
                    MediaType.ANIME -> "anime"
                    else -> "tv"
                }
                NetworkPacer.withGeneralPacing {
                    runCatching {
                        client.newCall(
                            Request.Builder()
                                .url("https://api.simkl.com/$seg/$sId?client_id=${TrackerAccountVerifier.SIMKL_CLIENT_ID}&extended=full")
                                .header("User-Agent", "OmniTrack/1.0 (Android)")
                                .get()
                                .build()
                        ).execute().use { resp ->
                            if (!resp.isSuccessful) return@use null
                            val o = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj() ?: return@use null
                            val poster = o["poster"].asSafeStr()?.let { "https://simkl.in/posters/${it}_m.jpg" }
                            RemoteTrackerEntrySnapshot(
                                trackerKey = "SIMKL",
                                remoteId = sId.toString(),
                                title = o["title"].asSafeStr() ?: item.title,
                                coverUrl = poster,
                                format = seg.uppercase(),
                                year = o["year"].asSafeStr(),
                                remoteTotal = o["total_episodes"].asSafeInt()?.toDouble()
                            )
                        }
                    }.getOrNull()
                }
            }
            "HARDCOVER" -> {
                val hcId = item.hardcoverId?.takeIf { it.isNotBlank() } ?: return@withContext null
                val hcToken = tokens.hardcoverToken?.trim().orEmpty()
                if (hcToken.isBlank()) return@withContext null
                val hc = HardcoverApiClient(client)
                val userRec = hc.fetchUserBookEntry(hcToken, hcId)
                if (userRec != null) {
                    RemoteTrackerEntrySnapshot(
                        trackerKey = "HARDCOVER",
                        remoteId = hcId,
                        title = userRec.title,
                        coverUrl = userRec.coverUrl?.let { LosslessCoverMapper.toLosslessUrl(it) },
                        format = userRec.author ?: "Hardcover Book",
                        year = userRec.releaseYear,
                        remoteStatus = userRec.status,
                        remoteProgress = userRec.currentPage,
                        remoteTotal = userRec.pages?.toDouble(),
                        remoteScore10 = userRec.userRating10
                    )
                } else {
                    val details = hc.fetchBookDetails(hcToken, hcId, item.title, item.bookAuthor) ?: return@withContext null
                    RemoteTrackerEntrySnapshot(
                        trackerKey = "HARDCOVER",
                        remoteId = details.hardcoverId,
                        title = details.title,
                        coverUrl = details.coverUrl?.let { LosslessCoverMapper.toLosslessUrl(it) },
                        format = details.authorName ?: "Book",
                        year = details.releaseYear,
                        remoteTotal = details.pages?.toDouble()
                    )
                }
            }
            "VNDB" -> {
                val vId = item.vndbId?.takeIf { it.isNotBlank() } ?: return@withContext null
                NetworkPacer.withVndbPacing {
                    runCatching {
                        val payload = buildJsonObject {
                            put("filters", buildJsonArray { add(JsonPrimitive("id")); add(JsonPrimitive("=")); add(JsonPrimitive(vId)) })
                            put("fields", "id,title,released,rating,image{url}")
                            put("results", 1)
                        }.toString()
                        val baseSnap = client.newCall(
                            Request.Builder()
                                .url("https://api.vndb.org/kana/vn")
                                .header("User-Agent", "OmniTrack/1.0")
                                .post(payload.toRequestBody("application/json".toMediaType()))
                                .build()
                        ).execute().use { resp ->
                            if (!resp.isSuccessful) return@use null
                            val o = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                                ?.get("results").asSafeArr()?.firstOrNull()?.asSafeObj() ?: return@use null
                            RemoteTrackerEntrySnapshot(
                                trackerKey = "VNDB",
                                remoteId = vId,
                                title = o["title"].asSafeStr() ?: item.title,
                                coverUrl = o["image"].asSafeObj()?.get("url").asSafeStr(),
                                format = "Visual Novel",
                                year = o["released"].asSafeStr()?.take(4),
                                remoteScore10 = o["rating"].asSafeInt()?.let { it / 10f }
                            )
                        } ?: return@runCatching null

                        val vndbTok = tokens.vndbToken?.trim().orEmpty()
                        if (vndbTok.isBlank()) return@runCatching baseSnap

                        val userId = runCatching {
                            client.newCall(
                                Request.Builder()
                                    .url("https://api.vndb.org/kana/authinfo")
                                    .header("Authorization", "Token $vndbTok")
                                    .header("User-Agent", "OmniTrack/1.0")
                                    .get()
                                    .build()
                            ).execute().use { r ->
                                if (r.isSuccessful) json.parseToJsonElement(r.body?.string().orEmpty()).asSafeObj()?.get("id").asSafeStr() else null
                            }
                        }.getOrNull() ?: return@runCatching baseSnap

                        val ulistPayload = buildJsonObject {
                            put("user", userId)
                            put("filters", buildJsonArray { add(JsonPrimitive("id")); add(JsonPrimitive("=")); add(JsonPrimitive(vId)) })
                            put("fields", "id,vote,notes,started,finished,labels.label")
                            put("results", 1)
                        }.toString()

                        val uEntry = runCatching {
                            client.newCall(
                                Request.Builder()
                                    .url("https://api.vndb.org/kana/ulist")
                                    .header("Authorization", "Token $vndbTok")
                                    .header("User-Agent", "OmniTrack/1.0")
                                    .post(ulistPayload.toRequestBody("application/json".toMediaType()))
                                    .build()
                            ).execute().use { r ->
                                if (!r.isSuccessful) return@use null
                                json.parseToJsonElement(r.body?.string().orEmpty()).asSafeObj()
                                    ?.get("results").asSafeArr()?.firstOrNull()?.asSafeObj()
                            }
                        }.getOrNull() ?: return@runCatching baseSnap

                        val labels = uEntry["labels"].asSafeArr()?.mapNotNull { it.asSafeObj()?.get("label").asSafeStr()?.lowercase() }.orEmpty()
                        val st = when {
                            labels.any { "finished" in it } -> TrackingStatus.COMPLETED
                            labels.any { "playing" in it } -> TrackingStatus.IN_PROGRESS
                            labels.any { "stalled" in it || "hold" in it } -> TrackingStatus.ON_HOLD
                            labels.any { "dropped" in it } -> TrackingStatus.DROPPED
                            labels.any { "wishlist" in it } -> TrackingStatus.PLAN_TO_TRACK
                            else -> null
                        }
                        val userScore = uEntry["vote"].asSafeInt()?.takeIf { it > 0 }?.let { it / 10f }
                        baseSnap.copy(
                            remoteStatus = st ?: baseSnap.remoteStatus,
                            remoteScore10 = userScore ?: baseSnap.remoteScore10,
                            remoteNote = uEntry["notes"].asSafeStr() ?: baseSnap.remoteNote,
                            startedAtMs = parseYmdToEpochMs(uEntry["started"].asSafeStr()) ?: baseSnap.startedAtMs,
                            finishedAtMs = parseYmdToEpochMs(uEntry["finished"].asSafeStr()) ?: baseSnap.finishedAtMs
                        )
                    }.getOrNull()
                }
            }
            "COMICK" -> {
                val cid = item.comickId?.takeIf { it.isNotBlank() } ?: return@withContext null
                NetworkPacer.withGeneralPacing {
                    runCatching {
                        val enc = URLEncoder.encode(cid, "UTF-8")
                        client.newCall(
                            Request.Builder()
                                .url("https://api.comick.io/comic/$enc")
                                .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                                .header("Referer", "https://comick.io")
                                .get()
                                .build()
                        ).execute().use { resp ->
                            if (!resp.isSuccessful) return@use null
                            val comic = json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                                ?.get("comic").asSafeObj() ?: return@use null
                            val b2 = comic["md_covers"].asSafeArr()?.firstOrNull()?.asSafeObj()?.get("b2key").asSafeStr()
                            RemoteTrackerEntrySnapshot(
                                trackerKey = "COMICK",
                                remoteId = cid,
                                title = comic["title"].asSafeStr() ?: item.title,
                                coverUrl = b2?.let { "https://meo.comick.picture/$it" },
                                format = comic["country"].asSafeStr()?.uppercase() ?: "Manga",
                                year = comic["year"].asSafeStr(),
                                remoteTotal = comic["last_chapter"].asSafeStr()?.toDoubleOrNull()
                            )
                        }
                    }.getOrNull()
                }
            }
            "STEAM" -> {
                val sId = item.steamAppId ?: return@withContext null
                RemoteTrackerEntrySnapshot(
                    trackerKey = "STEAM",
                    remoteId = sId.toString(),
                    title = item.title,
                    coverUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$sId/library_600x900_2x.jpg",
                    format = "Steam Game",
                    remoteProgress = item.playtimeHours
                )
            }
            "IGDB" -> {
                val igId = item.igdbId ?: return@withContext null
                val cid = PreferenceManager.cachedIgdbClientId
                val sec = PreferenceManager.cachedIgdbClientSecret
                val g = if (cid.isNotBlank() && sec.isNotBlank()) {
                    IgdbApiClient(client).searchGamesList(cid, sec, item.title, 5)
                        .firstOrNull { it.igdbId == igId }
                } else null
                RemoteTrackerEntrySnapshot(
                    trackerKey = "IGDB",
                    remoteId = igId.toString(),
                    title = g?.title ?: item.title,
                    coverUrl = g?.coverUrl?.takeIf { it.isNotBlank() } ?: item.coverUrl.takeIf { it.isNotBlank() },
                    format = "IGDB Game",
                    year = g?.releaseYear ?: item.releaseYear
                )
            }
            else -> null
        }
    }

    /**
     * Pulls remote progress/status/score/note/cover snapshot from [trackerKey], persists updated
     * snapshot metadata in [MediaItemEntity.matchedTrackerMetaJson], and merges remote progress/rating/notes.
     */
    suspend fun pullFromTracker(
        trackerKey: String,
        item: MediaItemEntity
    ): Pair<MediaItemEntity?, TrackerSyncOutcome> = withContext(Dispatchers.IO) {
        val key = trackerKey.uppercase()
        val snap = fetchMatchedTrackerSnapshot(key, item)
            ?: return@withContext null to TrackerSyncOutcome(key, false, null, "Could not fetch $key entry")
        val nextSnaps = TrackerMetadataCodec.decodeMatchedSnapshots(item.matchedTrackerMetaJson).toMutableMap()
        nextSnaps[key] = snap

        var updated = item.copy(
            matchedTrackerMetaJson = TrackerMetadataCodec.encodeMatchedSnapshots(nextSnaps),
            totalProgress = item.totalProgress?.takeIf { it > 0.0 } ?: snap.remoteTotal,
            currentProgress = snap.remoteProgress ?: item.currentProgress,
            volumesProgress = snap.remoteVolumes ?: item.volumesProgress,
            userRating = snap.remoteScore10?.takeIf { it > 0f } ?: item.userRating,
            personalNotes = item.personalNotes?.takeIf { it.isNotBlank() } ?: snap.remoteNote,
            rewatchCount = maxOf(item.rewatchCount, snap.rewatches ?: 0),
            status = snap.remoteStatus ?: item.status
        )
        if (key == "ANILIST" && snap.isFavorite != null) {
            updated = updated.copy(anilistFavorite = snap.isFavorite, isFavorite = item.isFavorite || snap.isFavorite)
        }
        if (item.id > 0L) {
            repository.updateMedia(updated)
        }
        updated to TrackerSyncOutcome(key, true, 200, "Pulled latest state from $key")
    }

    /**
     * Removes an entry from a remote tracker's cloud list.
     * Supports AniList, MyAnimeList, Trakt, SIMKL, VNDB, TMDb rating, and Hardcover.
     */
    suspend fun deleteRemoteEntry(
        trackerKey: String,
        item: MediaItemEntity
    ): TrackerSyncOutcome = withContext(Dispatchers.IO) {
        val tokens = tokenManager.tokens.firstOrNull() ?: TrackerTokens()
        when (val key = trackerKey.uppercase()) {
            "ANILIST" -> {
                val tok = tokens.anilistToken?.trim().orEmpty()
                val alId = item.anilistId
                if (tok.isBlank()) return@withContext TrackerSyncOutcome(key, false, null, "AniList not signed in")
                if (alId == null) return@withContext TrackerSyncOutcome(key, false, null, "AniList ID not bound")
                NetworkPacer.withAniListPacing {
                    runCatching {
                        val findPayload = buildJsonObject {
                            put("query", "query(${'$'}mId: Int) { Media(id: ${'$'}mId) { mediaListEntry { id } } }")
                            putJsonObject("variables") { put("mId", alId.toInt()) }
                        }.toString()
                        val entryId = client.newCall(
                            Request.Builder()
                                .url("https://graphql.anilist.co")
                                .header("Authorization", "Bearer $tok")
                                .post(findPayload.toRequestBody("application/json".toMediaType()))
                                .build()
                        ).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                return@withAniListPacing TrackerSyncOutcome(key, false, resp.code, "AniList HTTP ${resp.code}")
                            }
                            json.parseToJsonElement(resp.body?.string().orEmpty()).asSafeObj()
                                ?.get("data").asSafeObj()?.get("Media").asSafeObj()
                                ?.get("mediaListEntry").asSafeObj()?.get("id").asSafeInt()
                        } ?: return@withAniListPacing TrackerSyncOutcome(key, true, 200, "Already removed from AniList")

                        val delPayload = buildJsonObject {
                            put("query", "mutation(${'$'}id: Int) { DeleteMediaListEntry(id: ${'$'}id) { deleted } }")
                            putJsonObject("variables") { put("id", entryId) }
                        }.toString()
                        client.newCall(
                            Request.Builder()
                                .url("https://graphql.anilist.co")
                                .header("Authorization", "Bearer $tok")
                                .post(delPayload.toRequestBody("application/json".toMediaType()))
                                .build()
                        ).execute().use { resp ->
                            val code = resp.code
                            if (!resp.isSuccessful) {
                                AppLogger.w("TrackerSync", "service=ANILIST op=delete http=$code")
                                TrackerSyncOutcome(key, false, code, "AniList delete HTTP $code")
                            } else {
                                TrackerSyncOutcome(key, true, code, "Deleted from AniList")
                            }
                        }
                    }.getOrElse {
                        if (it is CancellationException) throw it
                        TrackerSyncOutcome(key, false, null, "AniList delete failed (${it.javaClass.simpleName})")
                    }
                }
            }
            "MAL" -> {
                val tok = tokens.malToken?.trim().orEmpty()
                val malId = item.malId
                if (tok.isBlank()) return@withContext TrackerSyncOutcome(key, false, null, "MAL not signed in")
                if (malId == null) return@withContext TrackerSyncOutcome(key, false, null, "MAL ID not bound")
                val seg = if (item.mediaType == MediaType.ANIME) "anime" else "manga"
                NetworkPacer.withGeneralPacing {
                    runCatching {
                        client.newCall(
                            Request.Builder()
                                .url("https://api.myanimelist.net/v2/$seg/$malId/my_list_status")
                                .header("Authorization", "Bearer $tok")
                                .header("User-Agent", "OmniTrack/1.0")
                                .delete()
                                .build()
                        ).execute().use { resp ->
                            val code = resp.code
                            if (resp.isSuccessful || code == 404) {
                                TrackerSyncOutcome(key, true, code, "Deleted from MyAnimeList")
                            } else {
                                AppLogger.w("TrackerSync", "service=MAL op=delete http=$code")
                                TrackerSyncOutcome(key, false, code, "MAL delete HTTP $code")
                            }
                        }
                    }.getOrElse {
                        if (it is CancellationException) throw it
                        TrackerSyncOutcome(key, false, null, "MAL delete failed (${it.javaClass.simpleName})")
                    }
                }
            }
            "TRAKT" -> {
                val tok = tokens.traktToken?.trim().orEmpty()
                if (tok.isBlank()) return@withContext TrackerSyncOutcome(key, false, null, "Trakt not signed in")
                if (item.traktId == null && item.tmdbId == null && item.imdbId.isNullOrBlank()) {
                    return@withContext TrackerSyncOutcome(key, false, null, "No Trakt/TMDb ID bound")
                }
                val arrayKey = if (item.mediaType == MediaType.MOVIE) "movies" else "shows"
                val payload = buildJsonObject {
                    put(arrayKey, buildJsonArray {
                        add(buildJsonObject {
                            putJsonObject("ids") {
                                item.traktId?.let { put("trakt", it.toInt()) }
                                item.tmdbId?.let { put("tmdb", it.toInt()) }
                                item.imdbId?.takeIf { it.isNotBlank() }?.let { put("imdb", it) }
                            }
                        })
                    })
                }.toString()
                NetworkPacer.withGeneralPacing {
                    runCatching {
                        val r1 = client.newCall(
                            Request.Builder()
                                .url("https://api.trakt.tv/sync/watchlist/remove")
                                .header("Authorization", "Bearer $tok")
                                .header("trakt-api-version", "2")
                                .header("trakt-api-key", TrackerAccountVerifier.TRAKT_CLIENT_ID)
                                .post(payload.toRequestBody("application/json".toMediaType()))
                                .build()
                        ).execute().use { it.code }
                        val r2 = client.newCall(
                            Request.Builder()
                                .url("https://api.trakt.tv/sync/history/remove")
                                .header("Authorization", "Bearer $tok")
                                .header("trakt-api-version", "2")
                                .header("trakt-api-key", TrackerAccountVerifier.TRAKT_CLIENT_ID)
                                .post(payload.toRequestBody("application/json".toMediaType()))
                                .build()
                        ).execute().use { it.code }
                        val ok = r1 in 200..299 || r2 in 200..299
                        if (ok) TrackerSyncOutcome(key, true, r2, "Removed from Trakt")
                        else TrackerSyncOutcome(key, false, r2, "Trakt remove HTTP $r2")
                    }.getOrElse {
                        if (it is CancellationException) throw it
                        TrackerSyncOutcome(key, false, null, "Trakt remove failed")
                    }
                }
            }
            "SIMKL" -> {
                val tok = tokens.simklToken?.trim().orEmpty()
                if (tok.isBlank()) return@withContext TrackerSyncOutcome(key, false, null, "SIMKL not signed in")
                val arrayName = if (item.mediaType == MediaType.MOVIE) "movies" else "shows"
                val payload = buildJsonObject {
                    put(arrayName, buildJsonArray {
                        add(buildJsonObject {
                            putJsonObject("ids") {
                                item.simklId?.let { put("simkl", it.toInt()) }
                                item.tmdbId?.let { put("tmdb", it.toInt()) }
                                item.malId?.let { put("mal", it.toInt()) }
                            }
                        })
                    })
                }.toString()
                NetworkPacer.withGeneralPacing {
                    runCatching {
                        client.newCall(
                            Request.Builder()
                                .url("https://api.simkl.com/sync/history/remove")
                                .header("Authorization", "Bearer $tok")
                                .header("simkl-api-key", TrackerAccountVerifier.SIMKL_CLIENT_ID)
                                .header("Content-Type", "application/json")
                                .post(payload.toRequestBody("application/json".toMediaType()))
                                .build()
                        ).execute().use { resp ->
                            if (resp.isSuccessful) TrackerSyncOutcome(key, true, resp.code, "Removed from SIMKL")
                            else TrackerSyncOutcome(key, false, resp.code, "SIMKL remove HTTP ${resp.code}")
                        }
                    }.getOrElse {
                        if (it is CancellationException) throw it
                        TrackerSyncOutcome(key, false, null, "SIMKL remove failed")
                    }
                }
            }
            "VNDB" -> {
                val tok = tokens.vndbToken?.trim().orEmpty()
                val vId = item.vndbId?.takeIf { it.isNotBlank() }
                if (tok.isBlank()) return@withContext TrackerSyncOutcome(key, false, null, "VNDB not signed in")
                if (vId == null) return@withContext TrackerSyncOutcome(key, false, null, "VNDB ID not bound")
                NetworkPacer.withVndbPacing {
                    runCatching {
                        client.newCall(
                            Request.Builder()
                                .url("https://api.vndb.org/kana/ulist/$vId")
                                .header("Authorization", "token $tok")
                                .header("User-Agent", "OmniTrack/1.0")
                                .delete()
                                .build()
                        ).execute().use { resp ->
                            if (resp.isSuccessful || resp.code == 404) TrackerSyncOutcome(key, true, resp.code, "Removed from VNDB")
                            else TrackerSyncOutcome(key, false, resp.code, "VNDB delete HTTP ${resp.code}")
                        }
                    }.getOrElse {
                        if (it is CancellationException) throw it
                        TrackerSyncOutcome(key, false, null, "VNDB remove failed")
                    }
                }
            }
            "TMDB" -> {
                val tok = tokens.tmdbToken?.trim().orEmpty()
                val sid = tokens.tmdbSessionId?.trim().orEmpty()
                val tId = item.tmdbId
                if (tok.isBlank()) return@withContext TrackerSyncOutcome(key, false, null, "TMDb not signed in")
                if (sid.isBlank()) return@withContext TrackerSyncOutcome(key, false, null, "Sign in to TMDb in Tracker Accounts to remove remote rating")
                if (tId == null) return@withContext TrackerSyncOutcome(key, false, null, "TMDb ID not bound")
                val seg = if (item.mediaType == MediaType.MOVIE) "movie" else "tv"
                val isJwt = TrackerAccountVerifier.isTmdbV4Jwt(tok)
                val encSid = URLEncoder.encode(sid, "UTF-8")
                val url = if (isJwt) {
                    "https://api.themoviedb.org/3/$seg/$tId/rating?session_id=$encSid"
                } else {
                    "https://api.themoviedb.org/3/$seg/$tId/rating?api_key=${URLEncoder.encode(tok, "UTF-8")}&session_id=$encSid"
                }
                NetworkPacer.withTmdbPacing {
                    runCatching {
                        val reqBuilder = Request.Builder()
                            .url(url)
                            .delete()
                        if (isJwt) reqBuilder.header("Authorization", "Bearer $tok")
                        client.newCall(reqBuilder.build()).execute().use { resp ->
                            if (resp.isSuccessful || resp.code == 404) TrackerSyncOutcome(key, true, resp.code, "TMDb rating removed")
                            else TrackerSyncOutcome(key, false, resp.code, "TMDb delete HTTP ${resp.code}")
                        }
                    }.getOrElse {
                        if (it is CancellationException) throw it
                        TrackerSyncOutcome(key, false, null, "TMDb remove failed")
                    }
                }
            }
            "HARDCOVER" -> {
                val tok = tokens.hardcoverToken?.trim().orEmpty()
                val hcId = item.hardcoverId?.takeIf { it.isNotBlank() }
                if (tok.isBlank()) return@withContext TrackerSyncOutcome(key, false, null, "Hardcover not signed in")
                if (hcId == null) return@withContext TrackerSyncOutcome(key, false, null, "Hardcover ID not bound")
                val hcClient = HardcoverApiClient(client)
                val (ok, code) = hcClient.deleteUserBook(tok, hcId)
                if (ok) TrackerSyncOutcome(key, true, code ?: 200, "Removed from Hardcover")
                else TrackerSyncOutcome(key, false, code, hcClient.lastError ?: "Hardcover remove failed")
            }
            else -> TrackerSyncOutcome(key, true, null, "$key unlinked locally (no remote delete endpoint)")
        }
    }
}
