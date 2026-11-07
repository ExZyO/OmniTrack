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
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
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
        if (item.traktId != null) {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    mediaId = item.id,
                    trackerType = "TRAKT",
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
                "VNDB" -> syncVndb(task, mediaItem, tokens.vndbToken)
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
        if (token.isNullOrBlank() || item.anilistId == null) return@withAniListPacing true // Skip if no token
        try {
            val query = """
                mutation (${'$'}mediaId: Int, ${'$'}progress: Int) {
                    SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress) {
                        id
                        progress
                    }
                }
            """.trimIndent()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("mediaId", item.anilistId.toInt())
                    put("progress", task.progressValue.toInt())
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
            val field = if (isAnime) "num_watched_episodes" else "num_chapters_read"

            val url = "https://api.myanimelist.net/v2/$endpoint/${item.malId}/my_list_status"
            val body = okhttp3.FormBody.Builder()
                .add(field, task.progressValue.toInt().toString())
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .put(body)
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
        if (token.isNullOrBlank() || item.tmdbId == null) return@withGeneralPacing true
        try {
            val payload = buildJsonObject {
                val arrayName = if (item.mediaType == com.omnitrack.app.data.model.MediaType.MOVIE) "movies" else "shows"
                put(arrayName, kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject {
                        putJsonObject("ids") {
                            put("tmdb", item.tmdbId.toInt())
                        }
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

    private suspend fun syncVndb(
        task: SyncQueueEntity,
        item: MediaItemEntity,
        token: String?
    ): Boolean = NetworkPacer.withVndbPacing {
        if (token.isNullOrBlank() || item.vndbId == null) return@withVndbPacing true
        try {
            val payload = buildJsonObject {
                val vote = item.userRating?.let { (it * 10).toInt() }
                if (vote != null) put("vote", vote)
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
