package com.omnitrack.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT id, title, nativeTitle, coverUrl, bannerUrl, mediaType, status, currentProgress, totalProgress, userRating, lastUpdated, customCoverPath, customBannerPath, artworkRole, communityScore FROM media_items ORDER BY lastUpdated DESC")
    fun getAllMediaSummaries(): Flow<List<MediaCardSummary>>

    @Query("SELECT * FROM media_items ORDER BY lastUpdated DESC")
    fun getAllMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    fun getMediaById(id: Long): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items WHERE mediaType = :type ORDER BY lastUpdated DESC")
    fun getMediaByType(type: MediaType): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE (:type = 'ALL' OR mediaType = :type) AND (:status = 'ALL' OR status = :status) ORDER BY lastUpdated DESC")
    fun getFilteredMedia(type: MediaType, status: TrackingStatus): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE title LIKE '%' || :query || '%' OR nativeTitle LIKE '%' || :query || '%'")
    fun searchMedia(query: String): Flow<List<MediaItemEntity>>

    @Query("UPDATE media_items SET currentProgress = :progress, lastUpdated = :updated WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Double, updated: Long = System.currentTimeMillis())

    @Query("UPDATE media_items SET coverUrl = :coverUrl, lastUpdated = :updated WHERE id = :id")
    suspend fun updateCover(id: Long, coverUrl: String, updated: Long = System.currentTimeMillis())

    @Query("UPDATE media_items SET bannerUrl = :bannerUrl, lastUpdated = :updated WHERE id = :id")
    suspend fun updateBanner(id: Long, bannerUrl: String?, updated: Long = System.currentTimeMillis())

    @Query("UPDATE media_items SET customCoverPath = :path, lastUpdated = :updated WHERE id = :id")
    suspend fun updateCustomCoverPath(id: Long, path: String?, updated: Long = System.currentTimeMillis())

    @Query("UPDATE media_items SET customBannerPath = :path, lastUpdated = :updated WHERE id = :id")
    suspend fun updateCustomBannerPath(id: Long, path: String?, updated: Long = System.currentTimeMillis())

    @Query("UPDATE media_items SET artworkRole = :role, lastUpdated = :updated WHERE id = :id")
    suspend fun updateArtworkRole(id: Long, role: String, updated: Long = System.currentTimeMillis())

    @Query("UPDATE media_items SET clearartUrl = :clearart, communityScore = :score, imdbScore = :imdb, rtScore = :rt, metacriticScore = :meta WHERE id = :id")
    suspend fun updateEnrichment(id: Long, clearart: String?, score: Float?, imdb: Float?, rt: Int?, meta: Int?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(item: MediaItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaItemEntity>)

    @Update
    suspend fun updateMedia(item: MediaItemEntity)

    @Delete
    suspend fun deleteMedia(item: MediaItemEntity)

    @Query("DELETE FROM media_items")
    suspend fun deleteAllMedia()
}
