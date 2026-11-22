package com.omnitrack.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.data.enrichment.CalendarReleaseItem
import com.omnitrack.app.data.enrichment.ReleaseCalendarRepository
import com.omnitrack.app.data.model.BookTrackingMode
import com.omnitrack.app.data.search.OmniSearchRepository
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.util.MockData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProgressUndoEvent(
    val mediaId: Long,
    val title: String,
    val deltaLabel: String,
    val previousProgress: Double,
    val previousStatus: TrackingStatus,
    val previousAudioMinutes: Int? = null,
    val previousPlaytimeHours: Double = 0.0,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class HomeUiState(
    val mediaSummaries: List<MediaCardSummary> = emptyList(),
    val allMediaSummaries: List<MediaCardSummary> = emptyList(),
    val selectedMediaType: MediaType = MediaType.ALL,
    val selectedStatus: TrackingStatus = TrackingStatus.ALL,
    val isLoading: Boolean = true,
    val isSeeded: Boolean = false
)

class HomeViewModel(
    private val repository: MediaRepository,
    private val coverCache: com.omnitrack.app.data.cache.CoverCache? = null,
    private val diskCache: coil3.disk.DiskCache? = null
) : ViewModel() {

    private val _selectedMediaType = MutableStateFlow(MediaType.ALL)
    private val _selectedStatus = MutableStateFlow(TrackingStatus.ALL)
    private val _isLoading = MutableStateFlow(true)

    // Persistent Discover Search State (survives navigation to detail screen & back)
    val discoverSearchQuery = MutableStateFlow("")
    val discoverSelectedFilter = MutableStateFlow(MediaType.ALL)
    val discoverSearchResults = MutableStateFlow<List<com.omnitrack.app.data.search.SearchResultItem>>(emptyList())
    val discoverIsSearching = MutableStateFlow(false)
    val discoverAiResolutionBanner = MutableStateFlow<String?>(null)
    val discoverUseAiSearch = MutableStateFlow<Boolean?>(null)

    private val releaseCalendarRepository = ReleaseCalendarRepository()
    private val homeSearchRepository = OmniSearchRepository()

    private val _rawCalendarReleases = MutableStateFlow<List<CalendarReleaseItem>>(emptyList())
    private val _isLoadingCalendar = MutableStateFlow(false)
    val isLoadingCalendar: StateFlow<Boolean> = _isLoadingCalendar.asStateFlow()

    private val _trendingItems = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val trendingItems: StateFlow<List<SearchResultItem>> = _trendingItems.asStateFlow()
    private val _isLoadingTrending = MutableStateFlow(false)
    val isLoadingTrending: StateFlow<Boolean> = _isLoadingTrending.asStateFlow()

    private val _undoEvent = MutableStateFlow<ProgressUndoEvent?>(null)
    val undoEvent: StateFlow<ProgressUndoEvent?> = _undoEvent.asStateFlow()

    val watchedTimestamps90d: StateFlow<List<Long>> = repository
        .getWatchedTimestampsSince(System.currentTimeMillis() - 90L * 24L * 60L * 60L * 1000L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var discoverLastDebounceQuery: String? = null
    var discoverLastExecutedQuery: String? = null
    var discoverLastExecutedFilter: MediaType? = null
    var discoverLastExecutedAi: Boolean? = null
    var discoverLastEffectiveQuery: String? = null
    var discoverLastEffectiveFilter: MediaType? = null
    var discoverGridScrollIndex: Int = 0
    var discoverGridScrollOffset: Int = 0

    val uiState: StateFlow<HomeUiState> = combine(
        _selectedMediaType,
        _selectedStatus,
        _isLoading,
        repository.getAllMediaSummaries()
    ) { type, status, loading, allSummaries ->
        val filtered = allSummaries.filter { item ->
            (type == MediaType.ALL || item.mediaType == type) &&
            (status == TrackingStatus.ALL || item.status == status)
        }
        HomeUiState(
            mediaSummaries = filtered,
            allMediaSummaries = allSummaries,
            selectedMediaType = type,
            selectedStatus = status,
            isLoading = loading && allSummaries.isEmpty(),
            isSeeded = allSummaries.isNotEmpty()
        )
    }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    init {
        seedIfEmpty()
    }

    private fun seedIfEmpty() {
        viewModelScope.launch {
            repository.getAllMediaSummaries().collect { items ->
                if (items.isEmpty()) {
                    repository.insertAllMedia(MockData.sampleMedia)
                    repository.insertAllCategories(MockData.categories)
                } else {
                    // Smart sync canonical seed updates without requiring data wipe
                    val allItems = repository.getAllMedia().first()
                    for (existing in allItems) {
                        if (existing.title == "Omniscient Reader's Viewpoint" && existing.totalProgress != 551.0) {
                            repository.updateMedia(existing.copy(mediaType = MediaType.NOVEL, totalProgress = 551.0, bangumiId = 295483))
                        } else if (existing.title == "Lord of the Mysteries" && existing.bangumiId == null) {
                            repository.updateMedia(existing.copy(bangumiId = 243916))
                        } else if (existing.title == "Baldur's Gate 3" && existing.igdbId == 119171L) {
                            repository.updateMedia(existing.copy(igdbId = 26955L))
                        }
                    }
                    if (coverCache != null) {
                        val activeUrls = items.map { it.coverUrl }.toSet()
                        val activeIds = items.map { it.id }.toSet()
                        coverCache.pruneOrphans(activeUrls, activeIds)
                        if (diskCache != null) {
                            for (url in activeUrls) {
                                coverCache.promoteFromDiskCache(url, diskCache)
                            }
                        }
                    }
                }
                _isLoading.value = false
                return@collect
            }
        }
    }

    fun selectMediaType(type: MediaType) {
        _selectedMediaType.value = type
    }

    fun selectStatus(status: TrackingStatus) {
        _selectedStatus.value = status
    }

    val calendarReleases: StateFlow<List<CalendarReleaseItem>> = combine(
        _rawCalendarReleases,
        uiState
    ) { raw, state ->
        val library = state.allMediaSummaries
        val byAnilist = library.filter { it.anilistId != null }.associateBy { it.anilistId }
        val byMal = library.filter { it.malId != null }.associateBy { it.malId }
        val byTitle = library.associateBy { it.title.trim().lowercase() }
        raw.map { rel ->
            val matched = (rel.anilistId?.let { byAnilist[it] })
                ?: (rel.malId?.let { byMal[it] })
                ?: byTitle[rel.title.trim().lowercase()]
            if (matched != null) rel.copy(localMediaId = matched.id) else rel
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadReleaseCalendar(force: Boolean = false) {
        if (_isLoadingCalendar.value) return
        if (!force && _rawCalendarReleases.value.isNotEmpty()) return
        viewModelScope.launch {
            _isLoadingCalendar.value = true
            try {
                _rawCalendarReleases.value = releaseCalendarRepository.fetchWeeklyAiringSchedule(force = force)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                _isLoadingCalendar.value = false
            }
        }
    }

    fun loadHomeTrending(force: Boolean = false) {
        if (_isLoadingTrending.value) return
        if (!force && _trendingItems.value.isNotEmpty()) return
        viewModelScope.launch {
            _isLoadingTrending.value = true
            try {
                coroutineScope {
                    val aniDeferred = async { runCatching { releaseCalendarRepository.fetchTrendingAniList(force) }.getOrDefault(emptyList()) }
                    val booksDeferred = async { runCatching { homeSearchRepository.fetchDiscoverBooksShelves("trending").take(6) }.getOrDefault(emptyList()) }
                    val gamesDeferred = async { runCatching { homeSearchRepository.fetchDiscoverGamesShelves("top_rated").take(6) }.getOrDefault(emptyList()) }
                    val ani = aniDeferred.await()
                    val books = booksDeferred.await()
                    val games = gamesDeferred.await()
                    val merged = mutableListOf<SearchResultItem>()
                    val maxLen = maxOf(ani.size, books.size, games.size)
                    for (i in 0 until maxLen) {
                        if (i < ani.size) merged.add(ani[i])
                        if (i < books.size) merged.add(books[i])
                        if (i < games.size) merged.add(games[i])
                    }
                    _trendingItems.value = merged
                        .distinctBy { "${it.mediaType}_${it.title.trim().lowercase()}" }
                        .take(18)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                _isLoadingTrending.value = false
            }
        }
    }

    fun incrementProgress(id: Long, mediaType: MediaType, currentProgress: Double, totalProgress: Double?) {
        viewModelScope.launch {
            val entity = repository.getMediaById(id).firstOrNull()
            if (entity != null) {
                val now = System.currentTimeMillis()
                when {
                    mediaType == MediaType.BOOK && BookTrackingMode.fromName(entity.bookTrackingMode) == BookTrackingMode.AUDIO_MINUTES -> {
                        val prevMins = entity.bookAudioMinutes ?: 0
                        val totalMins = entity.bookTotalAudioMinutes
                        val nextMins = if (totalMins != null && totalMins > 0) (prevMins + 15).coerceAtMost(totalMins) else (prevMins + 15)
                        val newStatus = when {
                            totalMins != null && totalMins > 0 && nextMins >= totalMins -> TrackingStatus.COMPLETED
                            entity.status == TrackingStatus.PLAN_TO_TRACK -> TrackingStatus.IN_PROGRESS
                            else -> entity.status
                        }
                        repository.updateMedia(
                            entity.copy(
                                bookAudioMinutes = nextMins,
                                status = newStatus,
                                lastUpdated = now
                            )
                        )
                        _undoEvent.value = ProgressUndoEvent(
                            mediaId = id,
                            title = entity.title,
                            deltaLabel = "+15m",
                            previousProgress = entity.currentProgress,
                            previousStatus = entity.status,
                            previousAudioMinutes = entity.bookAudioMinutes,
                            previousPlaytimeHours = entity.playtimeHours,
                            createdAtMs = now
                        )
                    }
                    else -> {
                        val isHoursMedia = mediaType == MediaType.GAME || mediaType == MediaType.VISUAL_NOVEL
                        val baseValue = if (isHoursMedia && entity.playtimeHours > 0.0) {
                            entity.playtimeHours
                        } else {
                            entity.currentProgress
                        }
                        val increment = when (mediaType) {
                            MediaType.GAME, MediaType.VISUAL_NOVEL -> 0.5
                            MediaType.BOOK -> {
                                if (BookTrackingMode.fromName(entity.bookTrackingMode) == BookTrackingMode.PERCENT) 5.0 else 10.0
                            }
                            else -> 1.0
                        }
                        val cap = if (mediaType == MediaType.BOOK && BookTrackingMode.fromName(entity.bookTrackingMode) == BookTrackingMode.PERCENT) {
                            100.0
                        } else {
                            entity.totalProgress
                        }
                        val nextRaw = baseValue + increment
                        val clamped = if (cap != null && cap > 0.0) nextRaw.coerceAtMost(cap) else nextRaw
                        val newStatus = when {
                            mediaType == MediaType.MOVIE -> TrackingStatus.COMPLETED
                            cap != null && cap > 0.0 && clamped >= cap -> TrackingStatus.COMPLETED
                            entity.status == TrackingStatus.PLAN_TO_TRACK -> TrackingStatus.IN_PROGRESS
                            else -> entity.status
                        }
                        repository.updateMedia(
                            entity.copy(
                                currentProgress = clamped,
                                playtimeHours = if (isHoursMedia) clamped else entity.playtimeHours,
                                status = newStatus,
                                lastUpdated = now
                            )
                        )
                        val deltaLabel = when (mediaType) {
                            MediaType.GAME, MediaType.VISUAL_NOVEL -> "+0.5h"
                            MediaType.BOOK -> if (BookTrackingMode.fromName(entity.bookTrackingMode) == BookTrackingMode.PERCENT) "+5%" else "+10p"
                            MediaType.MOVIE -> "Watched"
                            else -> "+1 ${mediaType.unitLabel}"
                        }
                        _undoEvent.value = ProgressUndoEvent(
                            mediaId = id,
                            title = entity.title,
                            deltaLabel = deltaLabel,
                            previousProgress = entity.currentProgress,
                            previousStatus = entity.status,
                            previousAudioMinutes = entity.bookAudioMinutes,
                            previousPlaytimeHours = entity.playtimeHours,
                            createdAtMs = now
                        )
                    }
                }
            } else {
                val increment = when (mediaType) {
                    MediaType.GAME, MediaType.VISUAL_NOVEL -> 0.5
                    MediaType.BOOK -> 10.0
                    else -> 1.0
                }
                val newProgress = currentProgress + increment
                val clamped = if (totalProgress != null) newProgress.coerceAtMost(totalProgress) else newProgress
                repository.updateProgress(id, clamped)
            }
        }
    }

    fun undoLastProgress() {
        val event = _undoEvent.value ?: return
        _undoEvent.value = null
        viewModelScope.launch {
            val entity = repository.getMediaById(event.mediaId).firstOrNull() ?: return@launch
            repository.updateMedia(
                entity.copy(
                    currentProgress = event.previousProgress,
                    status = event.previousStatus,
                    bookAudioMinutes = event.previousAudioMinutes,
                    playtimeHours = event.previousPlaytimeHours,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }

    fun dismissUndoEvent() {
        _undoEvent.value = null
    }

    fun incrementProgress(summary: MediaCardSummary) {
        incrementProgress(summary.id, summary.mediaType, summary.currentProgress, summary.totalProgress)
    }

    fun incrementProgress(item: MediaItemEntity) {
        incrementProgress(item.id, item.mediaType, item.currentProgress, item.totalProgress)
    }

    fun addMediaFromSearch(searchResult: SearchResultItem, status: TrackingStatus = TrackingStatus.PLAN_TO_TRACK) {
        viewModelScope.launch {
            val entity = searchResult.toMediaItemEntity(status)
            repository.insertMedia(entity)
            if (coverCache != null && diskCache != null) {
                coverCache.promoteFromDiskCache(entity.coverUrl, diskCache)
            }
        }
    }

    companion object {
        fun factory(
            repository: MediaRepository,
            coverCache: com.omnitrack.app.data.cache.CoverCache? = null,
            diskCache: coil3.disk.DiskCache? = null
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(repository, coverCache, diskCache) as T
                }
            }
        }
    }
}
