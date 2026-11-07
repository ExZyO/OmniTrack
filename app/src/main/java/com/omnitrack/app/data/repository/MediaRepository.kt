package com.omnitrack.app.data.repository

import com.omnitrack.app.data.db.dao.CategoryDao
import com.omnitrack.app.data.db.dao.MediaDao
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import kotlinx.coroutines.flow.Flow

class MediaRepository(
    private val mediaDao: MediaDao,
    private val categoryDao: CategoryDao
) {
    fun getAllMedia(): Flow<List<MediaItemEntity>> = mediaDao.getAllMedia()

    fun getAllMediaSummaries(): Flow<List<MediaCardSummary>> = mediaDao.getAllMediaSummaries()

    fun getMediaById(id: Long): Flow<MediaItemEntity?> = mediaDao.getMediaById(id)

    fun getFilteredMedia(type: MediaType, status: TrackingStatus): Flow<List<MediaItemEntity>> =
        if (type == MediaType.ALL && status == TrackingStatus.ALL) {
            mediaDao.getAllMedia()
        } else {
            mediaDao.getFilteredMedia(type, status)
        }

    fun searchMedia(query: String): Flow<List<MediaItemEntity>> = mediaDao.searchMedia(query)

    fun getVisibleCategories(): Flow<List<CategoryEntity>> = categoryDao.getVisibleCategories()

    suspend fun insertMedia(item: MediaItemEntity): Long = mediaDao.insertMedia(item)

    suspend fun insertAllMedia(items: List<MediaItemEntity>) = mediaDao.insertAll(items)

    suspend fun updateMedia(item: MediaItemEntity) = mediaDao.updateMedia(item)

    suspend fun updateProgress(id: Long, progress: Double) = mediaDao.updateProgress(id, progress)

    suspend fun updateCover(id: Long, coverUrl: String) = mediaDao.updateCover(id, coverUrl)

    suspend fun updateCustomCover(id: Long, path: String?) = mediaDao.updateCustomCoverPath(id, path)

    suspend fun updateArtworkRole(id: Long, role: String) = mediaDao.updateArtworkRole(id, role)

    suspend fun updateEnrichment(id: Long, clearart: String?, score: Float?, imdb: Float?, rt: Int?, meta: Int?) =
        mediaDao.updateEnrichment(id, clearart, score, imdb, rt, meta)

    suspend fun deleteMedia(item: MediaItemEntity) = mediaDao.deleteMedia(item)

    suspend fun insertCategory(category: CategoryEntity): Long = categoryDao.insertCategory(category)

    suspend fun insertAllCategories(categories: List<CategoryEntity>) = categoryDao.insertAll(categories)
}
