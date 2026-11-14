package com.omnitrack.app.data.repository

import com.omnitrack.app.data.db.dao.CategoryDao
import com.omnitrack.app.data.db.dao.EpisodeProgressDao
import com.omnitrack.app.data.db.dao.MediaDao
import com.omnitrack.app.data.db.dao.SyncQueueDao
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.db.entity.EpisodeProgressEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.SyncQueueEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow

class MediaRepository(
    private val mediaDao: MediaDao,
    private val categoryDao: CategoryDao,
    private val syncQueueDao: SyncQueueDao? = null,
    private val episodeProgressDao: EpisodeProgressDao? = null
) {
    fun getEpisodeProgress(mediaId: Long): Flow<List<EpisodeProgressEntity>> =
        (episodeProgressDao?.getProgressForMedia(mediaId) ?: emptyFlow()).distinctUntilChanged()

    suspend fun setEpisodeWatched(mediaId: Long, seasonNumber: Int, episodeNumber: Int, isWatched: Boolean) {
        if (episodeProgressDao == null) return
        val entity = EpisodeProgressEntity(
            mediaId = mediaId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            isWatched = isWatched,
            watchedAtMs = if (isWatched) System.currentTimeMillis() else null
        )
        episodeProgressDao.insertOrUpdate(entity)
    }

    suspend fun setMultipleEpisodesWatched(mediaId: Long, episodes: List<Pair<Int, Int>>, isWatched: Boolean) {
        if (episodeProgressDao == null) return
        val now = System.currentTimeMillis()
        val list = episodes.map { (s, e) ->
            EpisodeProgressEntity(
                mediaId = mediaId,
                seasonNumber = s,
                episodeNumber = e,
                isWatched = isWatched,
                watchedAtMs = if (isWatched) now else null
            )
        }
        episodeProgressDao.insertOrUpdateAll(list)
    }
    fun getAllMedia(): Flow<List<MediaItemEntity>> = mediaDao.getAllMedia().distinctUntilChanged()

    fun getAllMediaSummaries(): Flow<List<MediaCardSummary>> = mediaDao.getAllMediaSummaries().distinctUntilChanged()

    fun getMediaById(id: Long): Flow<MediaItemEntity?> = mediaDao.getMediaById(id).distinctUntilChanged()

    fun getFilteredMedia(type: MediaType, status: TrackingStatus): Flow<List<MediaItemEntity>> =
        (if (type == MediaType.ALL && status == TrackingStatus.ALL) {
            mediaDao.getAllMedia()
        } else {
            mediaDao.getFilteredMedia(type, status)
        }).distinctUntilChanged()

    fun searchMedia(query: String): Flow<List<MediaItemEntity>> = mediaDao.searchMedia(query).distinctUntilChanged()

    fun getVisibleCategories(): Flow<List<CategoryEntity>> = categoryDao.getVisibleCategories().distinctUntilChanged()

    suspend fun insertMedia(item: MediaItemEntity): Long = mediaDao.insertMedia(item)

    suspend fun insertAllMedia(items: List<MediaItemEntity>) = mediaDao.insertAll(items)

    suspend fun updateMedia(item: MediaItemEntity) = mediaDao.updateMedia(item)

    suspend fun updateProgress(id: Long, progress: Double) = mediaDao.updateProgress(id, progress)

    suspend fun updateCover(id: Long, coverUrl: String) = mediaDao.updateCover(id, coverUrl)

    suspend fun updateBanner(id: Long, bannerUrl: String?) = mediaDao.updateBanner(id, bannerUrl)

    suspend fun updateCustomCover(id: Long, path: String?) = mediaDao.updateCustomCoverPath(id, path)

    suspend fun updateCustomBanner(id: Long, path: String?) = mediaDao.updateCustomBannerPath(id, path)

    suspend fun updateArtworkRole(id: Long, role: String) = mediaDao.updateArtworkRole(id, role)

    suspend fun updateEnrichment(id: Long, clearart: String?, score: Float?, imdb: Float?, rt: Int?, meta: Int?, trakt: Int? = null, audience: Int? = null) =
        mediaDao.updateEnrichment(id, clearart, score, imdb, rt, meta, trakt, audience)

    suspend fun deleteMedia(item: MediaItemEntity) = mediaDao.deleteMedia(item)

    suspend fun insertCategory(category: CategoryEntity): Long = categoryDao.insertCategory(category)

    suspend fun insertAllCategories(categories: List<CategoryEntity>) = categoryDao.insertAll(categories)

    fun getPendingSyncTasks(): Flow<List<SyncQueueEntity>> = syncQueueDao?.getPendingTasks() ?: emptyFlow()

    suspend fun getPendingSyncTasksSnapshot(): List<SyncQueueEntity> = syncQueueDao?.getPendingTasksSnapshot() ?: emptyList()

    suspend fun enqueueSyncTask(task: SyncQueueEntity): Long? = syncQueueDao?.enqueue(task)

    suspend fun deleteSyncTask(id: Long) = syncQueueDao?.deleteTask(id)

    suspend fun clearSyncQueue() = syncQueueDao?.clearQueue()
}
