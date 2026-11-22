package com.omnitrack.app.data.sync

import com.omnitrack.app.data.db.dao.SyncQueueDao
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.SyncQueueEntity
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
        // Enqueue tasks for each active remote tracker ID
        if (item.anilistId != null) {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    mediaId = item.id,
                    trackerType = "ANILIST",
                    action = "PROGRESS",
                    progressValue = newProgress
                )
            )
        }
        if (item.malId != null) {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    mediaId = item.id,
                    trackerType = "MAL",
                    action = "PROGRESS",
                    progressValue = newProgress
                )
            )
        }
        if (item.traktId != null || (item.tmdbId != null && (item.mediaType == com.omnitrack.app.data.model.MediaType.MOVIE || item.mediaType == com.omnitrack.app.data.model.MediaType.SHOW))) {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    mediaId = item.id,
                    trackerType = "TRAKT",
                    action = "PROGRESS",
                    progressValue = newProgress
                )
            )
        }
        if (item.simklId != null || item.tmdbId != null || item.malId != null) {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    mediaId = item.id,
                    trackerType = "SIMKL",
                    action = "PROGRESS",
                    progressValue = newProgress
                )
            )
        }
        if (item.bangumiId != null) {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    mediaId = item.id,
                    trackerType = "BANGUMI",
                    action = "PROGRESS",
                    progressValue = newProgress
                )
            )
        }
        if (item.vndbId != null) {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    mediaId = item.id,
                    trackerType = "VNDB",
                    action = "PROGRESS",
                    progressValue = newProgress
                )
            )
        }
        if (item.tmdbId != null) {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    mediaId = item.id,
                    trackerType = "TMDB",
                    action = "PROGRESS",
                    progressValue = newProgress
                )
            )
        }
        if (item.mediaType == com.omnitrack.app.data.model.MediaType.BOOK && !item.hardcoverId.isNullOrBlank()) {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    mediaId = item.id,
                    trackerType = "HARDCOVER",
                    action = "PROGRESS",
                    progressValue = newProgress
                )
            )
        }
    }

    suspend fun drainQueue() = withContext(Dispatchers.IO) {
        val tasks = syncQueueDao.getPendingTasksSnapshot()
        if (tasks.isEmpty()) return@withContext

        val tokens = tokenManager.tokens.firstOrNull() ?: return@withContext

        for (task in tasks) {
            val mediaItem = repository.getMediaById(task.mediaId).firstOrNull()
            if (mediaItem == null) {
                syncQueueDao.deleteTask(task.id)
                continue
            }

            val success = when (task.trackerType) {
                "ANILIST" -> syncAniList(task, mediaItem, tokens.anilistToken)
                "MAL" -> syncMal(task, mediaItem, tokens.malToken)
                "TRAKT" -> syncTrakt(task, mediaItem, tokens.traktToken)
                "SIMKL" -> syncSimkl(task, mediaItem, tokens.simklToken)
                "BANGUMI" -> syncBangumi(task, mediaItem, tokens.bangumiToken)
                "VNDB" -> syncVndb(task, mediaItem, tokens.vndbToken)
                "TMDB" -> syncTmdb(task, mediaItem, tokens.tmdbToken)
                "HARDCOVER" -> syncHardcover(task, mediaItem, tokens.hardcoverToken)
                else -> true
            }

            if (success) {
                syncQueueDao.deleteTask(task.id)
            }
        }
    }

    private suspend fun syncAniList(
        task: SyncQueueEntity,
        item: MediaItemEntity,
        token: String?
    ): Boolean = NetworkPacer.withAniListPacing {
        if (token.isNullOrBlank() || item.anilistId == null) return@withAniListPacing true
        try {
            val query = """
                mutation (${'$'}mediaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus, ${'$'}scoreRaw: Int, ${'$'}notes: String, ${'$'}private: Boolean) {
                    SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress, status: ${'$'}status, scoreRaw: ${'$'}scoreRaw, notes: ${'$'}notes, private: ${'$'}private) {
                        id
                        progress
                        status
                        score
                    }
                }
            """.trimIndent()

            val effectiveStatus = if (item.isAnilistStatusLocked && item.anilistStatus != null) item.anilistStatus else item.status
            val alStatus = when (effectiveStatus) {
                com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS -> "CURRENT"
                com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK -> "PLANNING"
                com.omnitrack.app.data.model.TrackingStatus.COMPLETED -> "COMPLETED"
                com.omnitrack.app.data.model.TrackingStatus.ON_HOLD -> "PAUSED"
                com.omnitrack.app.data.model.TrackingStatus.DROPPED -> "DROPPED"
                else -> "CURRENT"
            }

            val rawScore = item.userRating?.let { (it * 10f).toInt().coerceIn(0, 100) }

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("mediaId", item.anilistId.toInt())
                    put("progress", task.progressValue.toInt())
                    put("status", alStatus)
                    if (rawScore != null) put("scoreRaw", rawScore)
                    item.anilistNotes?.take(2000)?.let { put("notes", it) }
                    put("private", item.anilistPrivate)
                }
            }

            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .header("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerSync", "service=ANILIST op=push http=${response.code}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=ANILIST op=push ex=${e.javaClass.simpleName}")
            false
        }
    }

    private suspend fun syncMal(
        task: SyncQueueEntity,
        item: MediaItemEntity,
        token: String?
    ): Boolean = NetworkPacer.withGeneralPacing {
        if (token.isNullOrBlank() || item.malId == null) return@withGeneralPacing true
        try {
            val isAnime = item.mediaType == com.omnitrack.app.data.model.MediaType.ANIME
            val endpoint = if (isAnime) "anime" else "manga"
            val progressField = if (isAnime) "num_watched_episodes" else "num_chapters_read"

            val effectiveStatus = if (item.isMalStatusLocked && item.malStatus != null) item.malStatus else item.status
            val malStatus = when (effectiveStatus) {
                com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS -> if (isAnime) "watching" else "reading"
                com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK -> if (isAnime) "plan_to_watch" else "plan_to_read"
                com.omnitrack.app.data.model.TrackingStatus.COMPLETED -> "completed"
                com.omnitrack.app.data.model.TrackingStatus.ON_HOLD -> "on_hold"
                com.omnitrack.app.data.model.TrackingStatus.DROPPED -> "dropped"
                else -> if (isAnime) "watching" else "reading"
            }

            val url = "https://api.myanimelist.net/v2/$endpoint/${item.malId}/my_list_status"
            val bodyBuilder = okhttp3.FormBody.Builder()
                .add(progressField, task.progressValue.toInt().toString())
                .add("status", malStatus)

            item.userRating?.let {
                val intScore = it.toInt().coerceIn(0, 10)
                bodyBuilder.add("score", intScore.toString())
            }
            item.malComments?.take(1000)?.let {
                bodyBuilder.add("comments", it)
            }
            if (item.malRewatches > 0) {
                bodyBuilder.add("num_times_rewatched", item.malRewatches.toString())
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .put(bodyBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerSync", "service=MAL op=push http=${response.code}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=MAL op=push ex=${e.javaClass.simpleName}")
            false
        }
    }

    private suspend fun syncTrakt(
        task: SyncQueueEntity,
        item: MediaItemEntity,
        token: String?
    ): Boolean = NetworkPacer.withGeneralPacing {
        if (token.isNullOrBlank() || (item.tmdbId == null && item.traktId == null && item.imdbId.isNullOrBlank())) {
            return@withGeneralPacing true
        }
        try {
            val arrayKey = if (item.mediaType == com.omnitrack.app.data.model.MediaType.MOVIE) "movies" else "shows"
            val payload = buildJsonObject {
                put(arrayKey, kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject {
                        putJsonObject("ids") {
                            item.traktId?.let { put("trakt", it.toInt()) }
                            item.tmdbId?.let { put("tmdb", it.toInt()) }
                            item.imdbId?.takeIf { it.isNotBlank() }?.let { put("imdb", it) }
                        }
                    })
                })
            }

            val request = Request.Builder()
                .url("https://api.trakt.tv/sync/history")
                .header("Authorization", "Bearer $token")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", TrackerAccountVerifier.TRAKT_CLIENT_ID)
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerSync", "service=TRAKT op=push http=${response.code}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=TRAKT op=push ex=${e.javaClass.simpleName}")
            false
        }
    }

    private suspend fun syncSimkl(
        task: SyncQueueEntity,
        item: MediaItemEntity,
        token: String?
    ): Boolean = NetworkPacer.withGeneralPacing {
        if (token.isNullOrBlank() || (item.simklId == null && item.tmdbId == null && item.malId == null)) {
            return@withGeneralPacing true
        }
        try {
            val arrayName = if (item.mediaType == com.omnitrack.app.data.model.MediaType.MOVIE) "movies" else "shows"
            val idObj = buildJsonObject {
                item.simklId?.let { put("simkl", it.toInt()) }
                item.tmdbId?.let { put("tmdb", it.toInt()) }
                item.malId?.let { put("mal", it.toInt()) }
            }

            val payload = buildJsonObject {
                put(arrayName, kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject {
                        put("ids", idObj)
                        item.simklMemo?.take(140)?.let { put("memo", it) }
                        put("private", item.simklPrivate)
                    })
                })
            }

            val request = Request.Builder()
                .url("https://api.simkl.com/sync/history")
                .header("Authorization", "Bearer $token")
                .header("simkl-api-key", TrackerAccountVerifier.SIMKL_CLIENT_ID)
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerSync", "service=SIMKL op=push http=${response.code}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=SIMKL op=push ex=${e.javaClass.simpleName}")
            false
        }
    }

    private suspend fun syncBangumi(
        task: SyncQueueEntity,
        item: MediaItemEntity,
        token: String?
    ): Boolean = NetworkPacer.withGeneralPacing {
        if (token.isNullOrBlank() || item.bangumiId == null) return@withGeneralPacing true
        try {
            val effectiveStatus = if (item.isBangumiStatusLocked && item.bangumiStatus != null) item.bangumiStatus else item.status
            val bgmType = when (effectiveStatus) {
                com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK -> 1
                com.omnitrack.app.data.model.TrackingStatus.COMPLETED -> 2
                com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS -> 3
                com.omnitrack.app.data.model.TrackingStatus.ON_HOLD -> 4
                com.omnitrack.app.data.model.TrackingStatus.DROPPED -> 5
                else -> 3
            }

            val payload = buildJsonObject {
                put("type", bgmType)
                item.userRating?.let { put("rate", it.toInt().coerceIn(1, 10)) }
                item.bangumiComment?.take(380)?.let { put("comment", it) }
                put("private", item.bangumiPrivate)
            }

            val request = Request.Builder()
                .url("https://api.bgm.tv/v0/users/-/collections/${item.bangumiId}")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerSync", "service=BANGUMI op=push http=${response.code}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=BANGUMI op=push ex=${e.javaClass.simpleName}")
            false
        }
    }

    private suspend fun syncVndb(
        task: SyncQueueEntity,
        item: MediaItemEntity,
        token: String?
    ): Boolean = NetworkPacer.withVndbPacing {
        if (token.isNullOrBlank() || item.vndbId == null) return@withVndbPacing true
        try {
            val activeLabelId = when (item.status) {
                com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS -> 1
                com.omnitrack.app.data.model.TrackingStatus.COMPLETED -> 2
                com.omnitrack.app.data.model.TrackingStatus.ON_HOLD -> 3
                com.omnitrack.app.data.model.TrackingStatus.DROPPED -> 4
                com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK -> 5
                else -> null
            }
            val payload = buildJsonObject {
                if (activeLabelId != null) {
                    put("labels_set", kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive(activeLabelId))
                    })
                    put("labels_unset", kotlinx.serialization.json.buildJsonArray {
                        listOf(1, 2, 3, 4, 5).filter { it != activeLabelId }.forEach {
                            add(kotlinx.serialization.json.JsonPrimitive(it))
                        }
                    })
                }
                val vote = item.userRating?.let { (it * 10f).toInt().coerceIn(10, 100) }
                if (vote != null) put("vote", vote)
                item.anilistNotes?.take(2000)?.let { put("notes", it) }
                if (item.status == com.omnitrack.app.data.model.TrackingStatus.COMPLETED) {
                    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                    put("finished", today)
                }
            }
            val request = Request.Builder()
                .url("https://api.vndb.org/kana/ulist/${item.vndbId}")
                .header("Authorization", "token $token")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .patch(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerSync", "service=VNDB op=push http=${response.code}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=VNDB op=push ex=${e.javaClass.simpleName}")
            false
        }
    }

    private suspend fun syncTmdb(
        task: SyncQueueEntity,
        item: MediaItemEntity,
        token: String?
    ): Boolean = NetworkPacer.withGeneralPacing {
        if (token.isNullOrBlank() || item.tmdbId == null || item.userRating == null) return@withGeneralPacing true
        try {
            val type = if (item.mediaType == com.omnitrack.app.data.model.MediaType.MOVIE) "movie" else "tv"
            val halfStarRating = ((item.userRating * 2f).toInt() / 2.0).coerceIn(0.5, 10.0)

            val payload = buildJsonObject {
                put("value", halfStarRating)
            }

            val request = Request.Builder()
                .url("https://api.themoviedb.org/3/$type/${item.tmdbId}/rating")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerSync", "service=TMDB op=push http=${response.code}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerSync", "service=TMDB op=push ex=${e.javaClass.simpleName}")
            false
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

    private suspend fun syncHardcover(
        task: SyncQueueEntity,
        item: MediaItemEntity,
        token: String?
    ): Boolean {
        val cleanToken = token?.trim().orEmpty()
        val hcId = item.hardcoverId?.trim().orEmpty()
        if (cleanToken.isBlank() || hcId.isBlank()) return true
        return com.omnitrack.app.data.enrichment.HardcoverApiClient(client)
            .syncBookStatus(cleanToken, hcId, item.status, item.userRating)
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
                    "https://api.myanimelist.net/v2/users/@me/animelist?fields=list_status,num_episodes,main_picture,start_date,media_type&limit=200"
                } else {
                    "https://api.myanimelist.net/v2/users/@me/mangalist?fields=list_status,num_chapters,main_picture,start_date,media_type&limit=200"
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
                        when (smartMergeIncomingItem(
                            MediaItemEntity(
                                title = title,
                                coverUrl = cover,
                                mediaType = resolvedType,
                                status = status,
                                currentProgress = prog,
                                totalProgress = total,
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

            fetchTraktArr("sync/watched/movies")?.forEach { elem ->
                val movieObj = elem.asSafeObj()?.get("movie").asSafeObj() ?: return@forEach
                val title = movieObj["title"].asSafeStr() ?: return@forEach
                val ids = movieObj["ids"].asSafeObj()
                val year = movieObj["year"].asSafeStr()
                when (smartMergeIncomingItem(
                    MediaItemEntity(
                        title = title,
                        coverUrl = "",
                        mediaType = com.omnitrack.app.data.model.MediaType.MOVIE,
                        status = com.omnitrack.app.data.model.TrackingStatus.COMPLETED,
                        currentProgress = 1.0,
                        totalProgress = 1.0,
                        releaseYear = year,
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
                when (smartMergeIncomingItem(
                    MediaItemEntity(
                        title = title,
                        coverUrl = "",
                        mediaType = com.omnitrack.app.data.model.MediaType.SHOW,
                        status = status,
                        currentProgress = watchedCount,
                        totalProgress = aired,
                        releaseYear = year,
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
}
