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
}
