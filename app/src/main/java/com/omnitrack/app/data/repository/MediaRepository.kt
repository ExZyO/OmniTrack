package com.omnitrack.app.data.repository

import com.omnitrack.app.data.db.dao.BookEntryDao
import com.omnitrack.app.data.db.dao.BookQuoteDao
import com.omnitrack.app.data.db.dao.CategoryDao
import com.omnitrack.app.data.db.dao.EpisodeProgressDao
import com.omnitrack.app.data.db.dao.GameEntryDao
import com.omnitrack.app.data.db.dao.MediaDao
import com.omnitrack.app.data.db.dao.SyncQueueDao
import com.omnitrack.app.data.db.dao.VnRouteDao
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.db.entity.BookEntryEntity
import com.omnitrack.app.data.db.entity.BookQuoteEntity
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.db.entity.EpisodeProgressEntity
import com.omnitrack.app.data.db.entity.GameEntryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.SyncQueueEntity
import com.omnitrack.app.data.db.entity.VnRouteEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow

class MediaRepository(
    private val mediaDao: MediaDao,
    private val categoryDao: CategoryDao,
    private val syncQueueDao: SyncQueueDao? = null,
    private val episodeProgressDao: EpisodeProgressDao? = null,
    private val vnRouteDao: VnRouteDao? = null,
    private val gameEntryDao: GameEntryDao? = null,
    private val bookEntryDao: BookEntryDao? = null,
    private val bookQuoteDao: BookQuoteDao? = null
) {
    fun getBookEntries(mediaId: Long): Flow<List<BookEntryEntity>> =
        (bookEntryDao?.getEntriesForMedia(mediaId) ?: emptyFlow()).distinctUntilChanged()

    suspend fun getBookEntriesOnce(mediaId: Long): List<BookEntryEntity> =
        bookEntryDao?.getEntriesForMediaOnce(mediaId) ?: emptyList()

    suspend fun insertBookEntry(entry: BookEntryEntity): Long? = bookEntryDao?.insertEntry(entry)

    suspend fun insertBookEntries(entries: List<BookEntryEntity>) {
        if (entries.isNotEmpty()) bookEntryDao?.insertEntries(entries)
    }

    suspend fun updateBookEntry(entry: BookEntryEntity) {
        bookEntryDao?.updateEntry(entry)
    }

    suspend fun deleteBookEntryById(entryId: Long) {
        bookEntryDao?.deleteEntryById(entryId)
    }

    fun getBookQuotes(mediaId: Long): Flow<List<BookQuoteEntity>> =
        (bookQuoteDao?.getQuotesForMedia(mediaId) ?: emptyFlow()).distinctUntilChanged()

    suspend fun getBookQuotesOnce(mediaId: Long): List<BookQuoteEntity> =
        bookQuoteDao?.getQuotesForMediaOnce(mediaId) ?: emptyList()

    suspend fun insertBookQuote(quote: BookQuoteEntity): Long? = bookQuoteDao?.insertQuote(quote)

    suspend fun insertBookQuotes(quotes: List<BookQuoteEntity>) {
        if (quotes.isNotEmpty()) bookQuoteDao?.insertQuotes(quotes)
    }

    suspend fun updateBookQuote(quote: BookQuoteEntity) {
        bookQuoteDao?.updateQuote(quote)
    }

    suspend fun deleteBookQuoteById(quoteId: Long) {
        bookQuoteDao?.deleteQuoteById(quoteId)
    }
    fun getGameEntries(mediaId: Long): Flow<List<GameEntryEntity>> =
        (gameEntryDao?.getEntriesForMedia(mediaId) ?: emptyFlow()).distinctUntilChanged()

    suspend fun getGameEntriesOnce(mediaId: Long): List<GameEntryEntity> =
        gameEntryDao?.getEntriesForMediaOnce(mediaId) ?: emptyList()

    suspend fun insertGameEntry(entry: GameEntryEntity): Long? = gameEntryDao?.insertEntry(entry)

    suspend fun insertGameEntries(entries: List<GameEntryEntity>) {
        if (entries.isNotEmpty()) gameEntryDao?.insertEntries(entries)
    }

    suspend fun updateGameEntry(entry: GameEntryEntity) {
        gameEntryDao?.updateEntry(entry)
    }

    suspend fun deleteGameEntryById(entryId: Long) {
        gameEntryDao?.deleteEntryById(entryId)
    }

    fun getVnRoutes(mediaId: Long): Flow<List<VnRouteEntity>> =
        (vnRouteDao?.getRoutesForMedia(mediaId) ?: emptyFlow()).distinctUntilChanged()

    suspend fun getVnRoutesOnce(mediaId: Long): List<VnRouteEntity> =
        vnRouteDao?.getRoutesForMediaOnce(mediaId) ?: emptyList()

    suspend fun insertVnRoute(route: VnRouteEntity): Long? = vnRouteDao?.insertRoute(route)

    suspend fun insertVnRoutes(routes: List<VnRouteEntity>) {
        if (routes.isNotEmpty()) vnRouteDao?.insertRoutes(routes)
    }

    suspend fun updateVnRoute(route: VnRouteEntity) {
        vnRouteDao?.updateRoute(route)
    }

    suspend fun deleteVnRouteById(routeId: Long) {
        vnRouteDao?.deleteRouteById(routeId)
    }
    fun getEpisodeProgress(mediaId: Long): Flow<List<EpisodeProgressEntity>> =
        (episodeProgressDao?.getProgressForMedia(mediaId) ?: emptyFlow()).distinctUntilChanged()

    fun getWatchedTimestampsSince(sinceMs: Long): Flow<List<Long>> =
        (episodeProgressDao?.getWatchedTimestampsSince(sinceMs) ?: emptyFlow()).distinctUntilChanged()

    suspend fun setEpisodeWatched(mediaId: Long, seasonNumber: Int, episodeNumber: Int, isWatched: Boolean) {
        if (episodeProgressDao == null) return
        val existing = episodeProgressDao.getEpisodeProgressOnce(mediaId, seasonNumber, episodeNumber)
        val entity = EpisodeProgressEntity(
            mediaId = mediaId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            isWatched = isWatched,
            watchedAtMs = if (isWatched) (existing?.watchedAtMs ?: System.currentTimeMillis()) else null,
            userRating = existing?.userRating,
            userNote = existing?.userNote,
            isBookmarked = existing?.isBookmarked ?: false,
            customLabel = existing?.customLabel
        )
        episodeProgressDao.insertOrUpdate(entity)
    }

    suspend fun setMultipleEpisodesWatched(mediaId: Long, episodes: List<Pair<Int, Int>>, isWatched: Boolean) {
        if (episodeProgressDao == null) return
        val now = System.currentTimeMillis()
        val existingMap = episodeProgressDao.getAllProgressForMediaOnce(mediaId)
            .associateBy { it.seasonNumber to it.episodeNumber }
        val list = episodes.map { (s, e) ->
            val existing = existingMap[s to e]
            EpisodeProgressEntity(
                mediaId = mediaId,
                seasonNumber = s,
                episodeNumber = e,
                isWatched = isWatched,
                watchedAtMs = if (isWatched) (existing?.watchedAtMs ?: now) else null,
                userRating = existing?.userRating,
                userNote = existing?.userNote,
                isBookmarked = existing?.isBookmarked ?: false,
                customLabel = existing?.customLabel
            )
        }
        episodeProgressDao.insertOrUpdateAll(list)
    }

    suspend fun updateEpisodeMetadata(
        mediaId: Long,
        seasonNumber: Int,
        episodeNumber: Int,
        userRating: Float?,
        userNote: String?,
        isBookmarked: Boolean,
        customLabel: String?
    ) {
        if (episodeProgressDao == null || mediaId <= 0L) return
        val existing = episodeProgressDao.getEpisodeProgressOnce(mediaId, seasonNumber, episodeNumber)
        val entity = EpisodeProgressEntity(
            mediaId = mediaId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            isWatched = existing?.isWatched ?: false,
            watchedAtMs = existing?.watchedAtMs,
            userRating = userRating,
            userNote = userNote?.trim()?.takeIf { it.isNotBlank() },
            isBookmarked = isBookmarked,
            customLabel = customLabel?.trim()?.takeIf { it.isNotBlank() }
        )
        episodeProgressDao.insertOrUpdate(entity)
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

    suspend fun findExistingMedia(
        anilistId: Long? = null,
        malId: Long? = null,
        tmdbId: Long? = null,
        vndbId: String? = null,
        igdbId: Long? = null,
        steamAppId: Long? = null,
        openLibraryId: String? = null,
        hardcoverId: String? = null,
        isbn: String? = null,
        title: String? = null,
        mediaType: MediaType? = null
    ): MediaItemEntity? {
        if (!vndbId.isNullOrBlank()) {
            val found = mediaDao.findByVndbId(vndbId.trim())
            if (found != null) return found
        }
        if (mediaType == MediaType.BOOK) {
            if (!hardcoverId.isNullOrBlank()) {
                val found = mediaDao.findByHardcoverId(hardcoverId.trim(), MediaType.BOOK)
                if (found != null) return found
            }
            if (!openLibraryId.isNullOrBlank()) {
                val found = mediaDao.findByOpenLibraryId(openLibraryId.trim(), MediaType.BOOK)
                if (found != null) return found
            }
            if (!isbn.isNullOrBlank()) {
                val found = mediaDao.findByIsbn(isbn.trim(), MediaType.BOOK)
                if (found != null) return found
            }
        }
        if (igdbId != null && igdbId > 0 && mediaType == MediaType.GAME) {
            val found = mediaDao.findByIgdbId(igdbId, MediaType.GAME)
            if (found != null) return found
        }
        if (steamAppId != null && steamAppId > 0 && mediaType == MediaType.GAME) {
            val found = mediaDao.findBySteamAppId(steamAppId, MediaType.GAME)
            if (found != null) return found
        }
        if (anilistId != null && anilistId > 0) {
            val found = mediaDao.findByAnilistId(anilistId)
            if (found != null) return found
        }
        if (malId != null && malId > 0) {
            val found = mediaDao.findByMalId(malId)
            if (found != null) return found
        }
        if (tmdbId != null && tmdbId > 0) {
            val found = mediaDao.findByTmdbId(tmdbId)
            if (found != null) return found
        }
        if (!title.isNullOrBlank() && mediaType != null) {
            val found = mediaDao.findByTitleAndType(title.trim(), mediaType)
            if (found != null) return found
        }
        return null
    }

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
