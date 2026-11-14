package com.omnitrack.app.data.sync

import com.omnitrack.app.data.db.dao.SyncQueueDao
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.SyncQueueEntity
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

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
                response.isSuccessful
            }
        } catch (_: Exception) {
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
                .put(bodyBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun syncTrakt(
        task: SyncQueueEntity,
        item: MediaItemEntity,
        token: String?
    ): Boolean = NetworkPacer.withGeneralPacing {
        if (token.isNullOrBlank() || item.tmdbId == null) return@withGeneralPacing true
        try {
            val payload = buildJsonObject {
                put("movies", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject {
                        putJsonObject("ids") {
                            put("tmdb", item.tmdbId.toInt())
                        }
                    })
                })
            }

            val request = Request.Builder()
                .url("https://api.trakt.tv/sync/history")
                .header("Authorization", "Bearer $token")
                .header("trakt-api-version", "2")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun syncSimkl(
        task: SyncQueueEntity,
        item: MediaItemEntity,
        token: String?
    ): Boolean = NetworkPacer.withGeneralPacing {
        if (token.isNullOrBlank()) return@withGeneralPacing true
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
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
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

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
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
            val payload = buildJsonObject {
                val vote = item.userRating?.let { (it * 10f).toInt().coerceIn(10, 100) }
                if (vote != null) put("vote", vote)
                item.anilistNotes?.take(2000)?.let { put("notes", it) }
            }
            val request = Request.Builder()
                .url("https://api.vndb.org/kana/ulist/${item.vndbId}")
                .header("Authorization", "token $token")
                .patch(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
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
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
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
            totalImported += pullAniListLibrary(tokens.anilistToken)
        }

        // 3. Trakt History Pull
        if (!tokens.traktToken.isNullOrBlank()) {
            onStatus("Pulling Trakt watched library...")
            totalImported += pullTraktLibrary(tokens.traktToken)
        }

        onStatus("Sync complete! $totalImported items imported/updated.")
        totalImported
    }

    private suspend fun pullAniListLibrary(token: String): Int = NetworkPacer.withAniListPacing {
        var count = 0
        try {
            val viewerReq = Request.Builder()
                .url("https://graphql.anilist.co")
                .header("Authorization", "Bearer $token")
                .post(buildJsonObject {
                    put("query", "query { Viewer { id name } }")
                }.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val viewerId = client.newCall(viewerReq).execute().use { response ->
                if (!response.isSuccessful) return@withAniListPacing 0
                val body = response.body?.string() ?: return@withAniListPacing 0
                json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("Viewer")?.jsonObject?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
            } ?: return@withAniListPacing 0

            for (mediaType in listOf("ANIME", "MANGA")) {
                val listQuery = """
                    query (${'$'}userId: Int, ${'$'}type: MediaType) {
                        MediaListCollection(userId: ${'$'}userId, type: ${'$'}type) {
                            lists {
                                entries {
                                    progress
                                    score
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

                val listReq = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .header("Authorization", "Bearer $token")
                    .post(listPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(listReq).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val root = json.parseToJsonElement(body).jsonObject
                    val lists = root["data"]?.jsonObject?.get("MediaListCollection")?.jsonObject?.get("lists")?.jsonArray ?: return@use

                    val existingMedia = repository.getAllMedia().firstOrNull() ?: emptyList()
                    val existingAnilistIds = existingMedia.mapNotNull { it.anilistId }.toSet()

                    for (listElem in lists) {
                        val entries = listElem.jsonObject["entries"]?.jsonArray ?: continue
                        for (entryElem in entries) {
                            val entryObj = entryElem.jsonObject
                            val mediaObj = entryObj["media"]?.jsonObject ?: continue
                            val alId = mediaObj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: continue

                            if (alId in existingAnilistIds) continue

                            val titleObj = mediaObj["title"]?.jsonObject
                            val title = titleObj?.get("english")?.jsonPrimitive?.content
                                ?: titleObj?.get("romaji")?.jsonPrimitive?.content
                                ?: titleObj?.get("native")?.jsonPrimitive?.content
                                ?: continue

                            val nativeTitle = titleObj?.get("native")?.jsonPrimitive?.content
                            val cover = mediaObj["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.content ?: ""
                            val banner = mediaObj["bannerImage"]?.jsonPrimitive?.content
                            val desc = mediaObj["description"]?.jsonPrimitive?.content
                                ?.replace(Regex("<[^>]*>"), "")?.take(400)
                            val format = mediaObj["format"]?.jsonPrimitive?.content
                            val isNovel = format == "NOVEL"

                            val resolvedType = when {
                                isNovel -> com.omnitrack.app.data.model.MediaType.NOVEL
                                mediaType == "ANIME" -> com.omnitrack.app.data.model.MediaType.ANIME
                                else -> com.omnitrack.app.data.model.MediaType.MANGA
                            }

                            val progress = entryObj["progress"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                            val totalProg = if (mediaType == "ANIME") {
                                mediaObj["episodes"]?.jsonPrimitive?.content?.toDoubleOrNull()
                            } else {
                                mediaObj["chapters"]?.jsonPrimitive?.content?.toDoubleOrNull()
                            }
                            val score = entryObj["score"]?.jsonPrimitive?.content?.toFloatOrNull()?.let { if (it > 0) it / 10f else null }
                            val alStatus = entryObj["status"]?.jsonPrimitive?.content
                            val trackingStatus = when (alStatus) {
                                "CURRENT" -> com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS
                                "PLANNING" -> com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK
                                "COMPLETED" -> com.omnitrack.app.data.model.TrackingStatus.COMPLETED
                                "PAUSED" -> com.omnitrack.app.data.model.TrackingStatus.ON_HOLD
                                "DROPPED" -> com.omnitrack.app.data.model.TrackingStatus.DROPPED
                                else -> com.omnitrack.app.data.model.TrackingStatus.IN_PROGRESS
                            }

                            val newEntity = MediaItemEntity(
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
                                malId = mediaObj["idMal"]?.jsonPrimitive?.content?.toLongOrNull()
                            )
                            repository.insertMedia(newEntity)
                            count++
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        count
    }

    private suspend fun pullTraktLibrary(token: String): Int = NetworkPacer.withGeneralPacing {
        var count = 0
        try {
            val req = Request.Builder()
                .url("https://api.trakt.tv/sync/watched/movies")
                .header("Authorization", "Bearer $token")
                .header("trakt-api-version", "2")
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string() ?: return@use
                val array = json.parseToJsonElement(body).jsonArray
                val existingMedia = repository.getAllMedia().firstOrNull() ?: emptyList()
                val existingTmdbIds = existingMedia.mapNotNull { it.tmdbId }.toSet()

                for (elem in array) {
                    val obj = elem.jsonObject
                    val movieObj = obj["movie"]?.jsonObject ?: continue
                    val title = movieObj["title"]?.jsonPrimitive?.content ?: continue
                    val ids = movieObj["ids"]?.jsonObject
                    val tmdbId = ids?.get("tmdb")?.jsonPrimitive?.content?.toLongOrNull()
                    val traktId = ids?.get("trakt")?.jsonPrimitive?.content?.toLongOrNull()

                    if (tmdbId != null && tmdbId in existingTmdbIds) continue

                    val newEntity = MediaItemEntity(
                        title = title,
                        coverUrl = "https://image.tmdb.org/t/p/original/placeholder.jpg",
                        mediaType = com.omnitrack.app.data.model.MediaType.MOVIE,
                        status = com.omnitrack.app.data.model.TrackingStatus.COMPLETED,
                        currentProgress = 1.0,
                        totalProgress = 1.0,
                        tmdbId = tmdbId,
                        traktId = traktId
                    )
                    repository.insertMedia(newEntity)
                    count++
                }
            }
        } catch (_: Exception) {}
        count
    }
}
