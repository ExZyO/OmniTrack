package com.omnitrack.app.ui.screens.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnitrack.app.data.artwork.ArtworkModelResolver
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.data.enrichment.CalendarReleaseItem
import com.omnitrack.app.data.enrichment.EnrichmentRepository
import com.omnitrack.app.data.enrichment.ReleaseCalendarRepository
import com.omnitrack.app.data.model.BookTrackingMode
import com.omnitrack.app.data.preferences.CachedMultiRatings
import com.omnitrack.app.data.preferences.DiscoverShelfCatalog
import com.omnitrack.app.data.preferences.DiscoverShelfDef
import com.omnitrack.app.data.preferences.HomeRowKey
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.search.OmniSearchRepository
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.data.search.StreamingProviderChip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
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

@Immutable
data class HomeUiState(
    val mediaSummaries: List<MediaCardSummary> = emptyList(),
    val allMediaSummaries: List<MediaCardSummary> = emptyList(),
    val selectedMediaType: MediaType = MediaType.ALL,
    val selectedStatus: TrackingStatus = TrackingStatus.ALL,
    val isLoading: Boolean = true,
    val isSeeded: Boolean = false
)

@Immutable
data class DiscoverShelfState(
    val items: List<SearchResultItem> = emptyList(),
    val page: Int = 1,
    val isLoading: Boolean = false,
    val hasMore: Boolean = true
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
    private val heroEnrichmentRepository = EnrichmentRepository()
    private val heroPrefetchedKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val _rawCalendarReleases = MutableStateFlow<List<CalendarReleaseItem>>(emptyList())
    private val _isLoadingCalendar = MutableStateFlow(false)
    val isLoadingCalendar: StateFlow<Boolean> = _isLoadingCalendar.asStateFlow()

    private val _trendingItems = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val trendingItems: StateFlow<List<SearchResultItem>> = _trendingItems.asStateFlow()
    private val _isLoadingTrending = MutableStateFlow(false)
    val isLoadingTrending: StateFlow<Boolean> = _isLoadingTrending.asStateFlow()

    private val _categoryDiscoverShelves = MutableStateFlow<Map<HomeRowKey, List<SearchResultItem>>>(emptyMap())
    val categoryDiscoverShelves: StateFlow<Map<HomeRowKey, List<SearchResultItem>>> = _categoryDiscoverShelves.asStateFlow()
    private val loadingDiscoverShelves = java.util.Collections.synchronizedSet(mutableSetOf<HomeRowKey>())
    private val homeDiscoverPages = java.util.concurrent.ConcurrentHashMap<HomeRowKey, Int>()

    private val _discoverShelves = MutableStateFlow<Map<String, DiscoverShelfState>>(emptyMap())
    val discoverShelves: StateFlow<Map<String, DiscoverShelfState>> = _discoverShelves.asStateFlow()
    private val loadingDiscoverIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // Discover Overhaul state flows (Critics' Pick, Streaming Services, Top 10 Today, Taste-Matched, Curated Rows, Swipe Queue)
    private val _streamingProviders = MutableStateFlow<List<StreamingProviderChip>>(emptyList())
    val streamingProviders: StateFlow<List<StreamingProviderChip>> = _streamingProviders.asStateFlow()
    private val _selectedStreamingProviderId = MutableStateFlow<Int?>(null)
    val selectedStreamingProviderId: StateFlow<Int?> = _selectedStreamingProviderId.asStateFlow()
    private val _streamingServiceTitles = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val streamingServiceTitles: StateFlow<List<SearchResultItem>> = _streamingServiceTitles.asStateFlow()
    private val _isLoadingStreamingTitles = MutableStateFlow(false)
    val isLoadingStreamingTitles: StateFlow<Boolean> = _isLoadingStreamingTitles.asStateFlow()
    private val _top10TodayItems = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val top10TodayItems: StateFlow<List<SearchResultItem>> = _top10TodayItems.asStateFlow()
    private val _tasteMatchedSeed = MutableStateFlow<MediaCardSummary?>(null)
    val tasteMatchedSeed: StateFlow<MediaCardSummary?> = _tasteMatchedSeed.asStateFlow()
    private val _tasteMatchedItems = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val tasteMatchedItems: StateFlow<List<SearchResultItem>> = _tasteMatchedItems.asStateFlow()
    private val _selectedCuratedRowKey = MutableStateFlow("under_90")
    val selectedCuratedRowKey: StateFlow<String> = _selectedCuratedRowKey.asStateFlow()
    private val _curatedRowItems = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val curatedRowItems: StateFlow<List<SearchResultItem>> = _curatedRowItems.asStateFlow()
    private val _isLoadingCuratedRow = MutableStateFlow(false)
    val isLoadingCuratedRow: StateFlow<Boolean> = _isLoadingCuratedRow.asStateFlow()
    private val _dailyCriticsPick = MutableStateFlow<SearchResultItem?>(null)
    val dailyCriticsPick: StateFlow<SearchResultItem?> = _dailyCriticsPick.asStateFlow()
    private val _skippedSwipeKeys = MutableStateFlow<Set<String>>(emptySet())
    private var loadedHighlightsKey: String? = null
    private var loadedCalendarWindowDays: Int = 14

    private val _undoEvent = MutableStateFlow<ProgressUndoEvent?>(null)
    val undoEvent: StateFlow<ProgressUndoEvent?> = _undoEvent.asStateFlow()

    val watchedTimestamps90d: StateFlow<List<Long>> = repository
        .getWatchedTimestampsSince(System.currentTimeMillis() - 90L * 24L * 60L * 60L * 1000L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCategories: StateFlow<List<CategoryEntity>> = repository
        .getAllCategories()
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
        initializeLibraryMaintenance()
    }

    private fun initializeLibraryMaintenance() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (_rawCalendarReleases.value.isEmpty()) {
                    val diskCached = releaseCalendarRepository.loadCachedScheduleFromDisk(
                        com.omnitrack.app.OmniTrackApplication.instance
                    )
                    if (diskCached.isNotEmpty()) {
                        _rawCalendarReleases.value = diskCached
                    }
                }
                val items = repository.getAllMediaSummaries().first()
                _isLoading.value = false
                if (items.isNotEmpty()) {
                    // Smart sync canonical ID updates without requiring data wipe
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

                    // Background backfill of 16:9 TMDb wide banners for existing library TV Shows & Movies
                    val needsWideBanner = allItems.filter {
                        (it.mediaType == MediaType.SHOW || it.mediaType == MediaType.MOVIE) &&
                            (it.bannerUrl.isNullOrBlank() || it.bannerUrl == it.coverUrl)
                    }
                    for (showOrMovie in needsWideBanner) {
                        runCatching {
                            val matches = homeSearchRepository.searchAll(
                                query = showOrMovie.title,
                                filter = showOrMovie.mediaType,
                                tmdbApiKey = PreferenceManager.cachedTmdbApiKey
                            )
                            val best = matches.firstOrNull { m ->
                                !m.bannerUrl.isNullOrBlank() && m.bannerUrl != m.coverUrl &&
                                    ((showOrMovie.tmdbId != null && m.tmdbId == showOrMovie.tmdbId) ||
                                        m.title.equals(showOrMovie.title, ignoreCase = true))
                            } ?: matches.firstOrNull { !it.bannerUrl.isNullOrBlank() && it.bannerUrl != it.coverUrl }
                            if (best != null && !best.bannerUrl.isNullOrBlank()) {
                                repository.updateMedia(
                                    showOrMovie.copy(
                                        bannerUrl = best.bannerUrl,
                                        tmdbId = showOrMovie.tmdbId ?: best.tmdbId
                                    )
                                )
                            }
                        }
                    }
                }
            } finally {
                _isLoading.value = false
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
        val byAnilist = library.filter { it.anilistId != null }.associateBy { it.mediaType to it.anilistId!! }
        val byMal = library.filter { it.malId != null }.associateBy { it.mediaType to it.malId!! }
        val byTmdb = library.filter { it.tmdbId != null }.associateBy { it.mediaType to it.tmdbId!! }
        val byImdb = library.filter { !it.imdbId.isNullOrBlank() }.associateBy { it.mediaType to it.imdbId!!.trim().lowercase() }
        val bySteam = library.filter { it.steamAppId != null }.associateBy { it.mediaType to it.steamAppId!! }
        val byMetron = library.filter { it.metronId != null }.associateBy { it.mediaType to it.metronId!! }
        val byTypedTitle = library.associateBy { it.mediaType to it.title.trim().lowercase() }
        val byTypedNativeTitle = library.filter { !it.nativeTitle.isNullOrBlank() }.associateBy { it.mediaType to it.nativeTitle!!.trim().lowercase() }
        raw.map { rel ->
            val titleKey = rel.title.trim().lowercase()
            val matched = (rel.anilistId?.let { byAnilist[rel.mediaType to it] })
                ?: (rel.malId?.let { byMal[rel.mediaType to it] })
                ?: (rel.tmdbId?.let { byTmdb[rel.mediaType to it] })
                ?: (rel.imdbId?.trim()?.takeIf { it.isNotBlank() }?.lowercase()?.let { byImdb[rel.mediaType to it] })
                ?: (rel.steamAppId?.let { bySteam[rel.mediaType to it] })
                ?: (rel.metronId?.let { byMetron[rel.mediaType to it] })
                ?: byTypedTitle[rel.mediaType to titleKey]
                ?: byTypedNativeTitle[rel.mediaType to titleKey]
            if (matched != null) {
                rel.copy(
                    localMediaId = matched.id,
                    coverUrl = ArtworkModelResolver.preferredCover(matched).ifBlank { rel.coverUrl }
                )
            } else rel
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadReleaseCalendar(force: Boolean = false, daysAhead: Int = 14) {
        if (_isLoadingCalendar.value) return
        if (!force && _rawCalendarReleases.value.isNotEmpty() && loadedCalendarWindowDays >= daysAhead) return
        viewModelScope.launch {
            _isLoadingCalendar.value = true
            try {
                val fetched = releaseCalendarRepository.fetchWeeklyAiringSchedule(force = force, daysAhead = daysAhead)
                _rawCalendarReleases.value = fetched
                loadedCalendarWindowDays = maxOf(loadedCalendarWindowDays, daysAhead)
                releaseCalendarRepository.saveCachedScheduleToDisk(
                    com.omnitrack.app.OmniTrackApplication.instance,
                    fetched
                )
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
                    val showsDeferred = async { runCatching { homeSearchRepository.fetchDiscoverShowsShelves("trending", PreferenceManager.cachedTmdbApiKey).take(6) }.getOrDefault(emptyList()) }
                    val moviesDeferred = async { runCatching { homeSearchRepository.fetchDiscoverMoviesShelves("trending", PreferenceManager.cachedTmdbApiKey).take(4) }.getOrDefault(emptyList()) }
                    val booksDeferred = async { runCatching { homeSearchRepository.fetchDiscoverBooksShelves("trending").take(6) }.getOrDefault(emptyList()) }
                    val gamesDeferred = async { runCatching { homeSearchRepository.fetchDiscoverGamesShelves("top_rated").take(6) }.getOrDefault(emptyList()) }
                    val vnsDeferred = async { runCatching { homeSearchRepository.fetchDiscoverVisualNovelsShelves("popular").take(4) }.getOrDefault(emptyList()) }
                    val comicsDeferred = async {
                        runCatching {
                            com.omnitrack.app.data.enrichment.ComicEnrichmentSource()
                                .fetchDiscoverComicsShelves("trending")
                                .take(6)
                        }.getOrDefault(emptyList())
                    }
                    val ani = aniDeferred.await()
                    val shows = showsDeferred.await()
                    val movies = moviesDeferred.await()
                    val books = booksDeferred.await()
                    val games = gamesDeferred.await()
                    val vns = vnsDeferred.await()
                    val comics = comicsDeferred.await()
                    val merged = mutableListOf<SearchResultItem>()
                    val maxLen = listOf(ani.size, shows.size, movies.size, books.size, games.size, vns.size, comics.size).maxOrNull() ?: 0
                    for (i in 0 until maxLen) {
                        if (i < ani.size) merged.add(ani[i])
                        if (i < shows.size) merged.add(shows[i])
                        if (i < movies.size) merged.add(movies[i])
                        if (i < books.size) merged.add(books[i])
                        if (i < games.size) merged.add(games[i])
                        if (i < vns.size) merged.add(vns[i])
                        if (i < comics.size) merged.add(comics[i])
                    }
                    _trendingItems.value = merged
                        .distinctBy { "${it.mediaType}_${it.title.trim().lowercase()}" }
                        .take(32)
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
                            MediaType.COMIC -> "+1 Issue"
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

    fun setMediaStatus(summary: MediaCardSummary, status: TrackingStatus) {
        viewModelScope.launch {
            val entity = repository.getMediaById(summary.id).firstOrNull() ?: return@launch
            val now = System.currentTimeMillis()
            val nextProgress = if (status == TrackingStatus.COMPLETED && entity.totalProgress != null && entity.totalProgress > 0.0) {
                entity.totalProgress
            } else {
                entity.currentProgress
            }
            repository.updateMedia(
                entity.copy(
                    status = status,
                    currentProgress = nextProgress,
                    lastUpdated = now
                )
            )
            _undoEvent.value = ProgressUndoEvent(
                mediaId = entity.id,
                title = entity.title,
                deltaLabel = status.labelFor(entity.mediaType),
                previousProgress = entity.currentProgress,
                previousStatus = entity.status,
                previousAudioMinutes = entity.bookAudioMinutes,
                previousPlaytimeHours = entity.playtimeHours,
                createdAtMs = now
            )
        }
    }

    fun markCompleted(summary: MediaCardSummary) {
        setMediaStatus(summary, TrackingStatus.COMPLETED)
    }

    fun addMediaFromSearch(searchResult: SearchResultItem, status: TrackingStatus = TrackingStatus.PLAN_TO_TRACK) {
        viewModelScope.launch {
            val entity = searchResult.toMediaItemEntity(status)
            val newId = repository.insertMedia(entity)
            if (newId > 0L) {
                runCatching {
                    val pref = com.omnitrack.app.OmniTrackApplication.instance.preferenceManager
                    val defCat = pref.chimanewLibrarySettings.firstOrNull()?.defaultCategoryId
                    if (defCat != null && defCat > 0L) {
                        pref.setMediaCategories(newId, setOf(defCat))
                    }
                }
            }
            if (coverCache != null && diskCache != null) {
                coverCache.promoteFromDiskCache(entity.coverUrl, diskCache)
            }
        }
    }

    fun loadHomeDiscoverShelf(rowKey: HomeRowKey, tmdbApiKey: String? = null) {
        if (!rowKey.name.startsWith("DISCOVER_")) return
        if (_categoryDiscoverShelves.value.containsKey(rowKey)) return
        if (!loadingDiscoverShelves.add(rowKey)) return
        viewModelScope.launch {
            try {
                val effectiveTmdbKey = tmdbApiKey?.takeIf { it.isNotBlank() } ?: PreferenceManager.cachedTmdbApiKey
                val items = when (rowKey) {
                    HomeRowKey.DISCOVER_ANIME -> homeSearchRepository.fetchDiscoverAnimeShelves("trending")
                    HomeRowKey.DISCOVER_MANGA -> homeSearchRepository.fetchDiscoverMangaShelves("trending")
                    HomeRowKey.DISCOVER_COMICS -> com.omnitrack.app.data.enrichment.ComicEnrichmentSource()
                        .fetchDiscoverComicsShelves("trending")
                    HomeRowKey.DISCOVER_NOVELS -> coroutineScope {
                        val wnDef = async { runCatching { homeSearchRepository.fetchDiscoverNovelsShelves("top_webnovels") }.getOrDefault(emptyList()) }
                        val lnDef = async { runCatching { homeSearchRepository.fetchDiscoverNovelsShelves("trending_ln") }.getOrDefault(emptyList()) }
                        val wn = wnDef.await()
                        val ln = lnDef.await()
                        val combined = mutableListOf<SearchResultItem>()
                        val maxLen = maxOf(wn.size, ln.size)
                        for (i in 0 until maxLen) {
                            if (i < wn.size) combined.add(wn[i])
                            if (i < ln.size) combined.add(ln[i])
                        }
                        combined.distinctBy { it.title.trim().lowercase() }
                    }
                    HomeRowKey.DISCOVER_BOOKS -> homeSearchRepository.fetchDiscoverBooksShelves("trending")
                    HomeRowKey.DISCOVER_SHOWS -> homeSearchRepository.fetchDiscoverShowsShelves("trending", effectiveTmdbKey)
                    HomeRowKey.DISCOVER_MOVIES -> homeSearchRepository.fetchDiscoverMoviesShelves("trending", effectiveTmdbKey)
                    HomeRowKey.DISCOVER_GAMES -> homeSearchRepository.fetchDiscoverGamesShelves("trending")
                    HomeRowKey.DISCOVER_VNS -> homeSearchRepository.fetchDiscoverVisualNovelsShelves("popular")
                    HomeRowKey.DISCOVER_FANFICTION -> {
                        val ao3Cookie = com.omnitrack.app.OmniTrackApplication.instance.trackerTokenManager
                            .getFanficCookie(com.omnitrack.app.data.model.FanficSite.AO3)
                        com.omnitrack.app.data.fanfic.FanficSiteEngine.fetchAo3DiscoverShelf("ao3_kudos", page = 1, cookie = ao3Cookie)
                    }
                    else -> emptyList()
                }
                if (items.isNotEmpty()) {
                    _categoryDiscoverShelves.value = _categoryDiscoverShelves.value + (rowKey to items)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                loadingDiscoverShelves.remove(rowKey)
            }
        }
    }

    private suspend fun fetchShelfByDef(
        def: DiscoverShelfDef,
        page: Int,
        effectiveTmdbKey: String
    ): List<SearchResultItem> {
        return when (def.category) {
            MediaType.ALL -> when (def.shelfKey) {
                "anime_trending" -> homeSearchRepository.fetchDiscoverAnimeShelves("trending", page)
                "manga_trending" -> homeSearchRepository.fetchDiscoverMangaShelves("trending", page)
                "comic_trending" -> com.omnitrack.app.data.enrichment.ComicEnrichmentSource()
                    .fetchDiscoverComicsShelves("trending", page)
                "novel_webnovels" -> homeSearchRepository.fetchDiscoverNovelsShelves("top_webnovels", page)
                "show_trending" -> homeSearchRepository.fetchDiscoverShowsShelves("trending", effectiveTmdbKey, page)
                "movie_trending" -> homeSearchRepository.fetchDiscoverMoviesShelves("trending", effectiveTmdbKey, page)
                "book_trending" -> homeSearchRepository.fetchDiscoverBooksShelves("trending", page)
                "game_trending" -> homeSearchRepository.fetchDiscoverGamesShelves("trending", page)
                "vn_popular" -> homeSearchRepository.fetchDiscoverVisualNovelsShelves("popular", page)
                "fanfic_trending" -> {
                    val ao3Cookie = com.omnitrack.app.OmniTrackApplication.instance.trackerTokenManager
                        .getFanficCookie(com.omnitrack.app.data.model.FanficSite.AO3)
                    com.omnitrack.app.data.fanfic.FanficSiteEngine.fetchAo3DiscoverShelf("ao3_kudos", page, ao3Cookie)
                }
                else -> emptyList()
            }
            MediaType.ANIME -> homeSearchRepository.fetchDiscoverAnimeShelves(def.shelfKey, page)
            MediaType.MANGA -> homeSearchRepository.fetchDiscoverMangaShelves(def.shelfKey, page)
            MediaType.COMIC -> com.omnitrack.app.data.enrichment.ComicEnrichmentSource()
                .fetchDiscoverComicsShelves(def.shelfKey, page)
            MediaType.NOVEL -> homeSearchRepository.fetchDiscoverNovelsShelves(def.shelfKey, page)
            MediaType.BOOK -> homeSearchRepository.fetchDiscoverBooksShelves(def.shelfKey, page)
            MediaType.SHOW -> homeSearchRepository.fetchDiscoverShowsShelves(def.shelfKey, effectiveTmdbKey, page)
            MediaType.MOVIE -> homeSearchRepository.fetchDiscoverMoviesShelves(def.shelfKey, effectiveTmdbKey, page)
            MediaType.GAME -> homeSearchRepository.fetchDiscoverGamesShelves(def.shelfKey, page)
            MediaType.VISUAL_NOVEL -> homeSearchRepository.fetchDiscoverVisualNovelsShelves(def.shelfKey, page)
            MediaType.FANFICTION -> {
                if (def.shelfKey.startsWith("wattpad_")) {
                    val wpCookie = com.omnitrack.app.OmniTrackApplication.instance.trackerTokenManager
                        .getFanficCookie(com.omnitrack.app.data.model.FanficSite.WATTPAD)
                    com.omnitrack.app.data.fanfic.FanficSiteEngine.fetchWattpadDiscoverShelf(def.shelfKey, page, wpCookie)
                } else {
                    val ao3Cookie = com.omnitrack.app.OmniTrackApplication.instance.trackerTokenManager
                        .getFanficCookie(com.omnitrack.app.data.model.FanficSite.AO3)
                    com.omnitrack.app.data.fanfic.FanficSiteEngine.fetchAo3DiscoverShelf(def.shelfKey, page, ao3Cookie)
                }
            }
        }
    }

    private fun overviewShelfCategoryForAllId(shelfId: String): MediaType? = when (shelfId) {
        "ALL:anime_trending" -> MediaType.ANIME
        "ALL:manga_trending" -> MediaType.MANGA
        "ALL:comic_trending" -> MediaType.COMIC
        "ALL:novel_webnovels" -> MediaType.NOVEL
        "ALL:show_trending" -> MediaType.SHOW
        "ALL:movie_trending" -> MediaType.MOVIE
        "ALL:book_trending" -> MediaType.BOOK
        "ALL:game_trending" -> MediaType.GAME
        "ALL:vn_popular" -> MediaType.VISUAL_NOVEL
        "ALL:fanfic_trending" -> MediaType.FANFICTION
        else -> null
    }

    fun loadDiscoverCategoryShelves(
        filter: MediaType,
        tmdbApiKey: String? = null,
        disabledCategories: Set<String> = emptySet(),
        disabledShelves: Set<String> = emptySet()
    ) {
        val targetDefs = DiscoverShelfCatalog.shelvesForCategory(filter).filter { def ->
            if (def.id in disabledShelves) return@filter false
            val mapped = if (filter == MediaType.ALL) overviewShelfCategoryForAllId(def.id) else null
            mapped == null || mapped.name !in disabledCategories
        }
        if (targetDefs.isEmpty()) return

        val effectiveTmdbKey = tmdbApiKey?.takeIf { it.isNotBlank() } ?: PreferenceManager.cachedTmdbApiKey
        viewModelScope.launch {
            val pendingDefs = targetDefs.filter { def ->
                val cur = _discoverShelves.value[def.id]
                (cur == null || cur.items.isEmpty()) && loadingDiscoverIds.add(def.id)
            }
            if (pendingDefs.isEmpty()) return@launch
            val loadingUpdates = pendingDefs.associate { def ->
                val prev = _discoverShelves.value[def.id] ?: DiscoverShelfState()
                def.id to prev.copy(isLoading = true)
            }
            _discoverShelves.value = _discoverShelves.value + loadingUpdates

            try {
                coroutineScope {
                    pendingDefs.map { def ->
                        async {
                            try {
                                val fetched = fetchShelfByDef(def, 1, effectiveTmdbKey)
                                    .distinctBy { "${it.mediaType}_${it.title.trim().lowercase()}" }
                                _discoverShelves.value = _discoverShelves.value + (
                                    def.id to DiscoverShelfState(
                                        items = fetched,
                                        page = 1,
                                        isLoading = false,
                                        hasMore = fetched.size >= 8
                                    )
                                )
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                val prev = _discoverShelves.value[def.id] ?: DiscoverShelfState()
                                _discoverShelves.value = _discoverShelves.value + (
                                    def.id to prev.copy(isLoading = false)
                                )
                            } finally {
                                loadingDiscoverIds.remove(def.id)
                            }
                        }
                    }.awaitAll()
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    pendingDefs.forEach { loadingDiscoverIds.remove(it.id) }
                    throw e
                }
            }
        }
    }

    fun loadMoreDiscoverShelf(shelfId: String, tmdbApiKey: String? = null) {
        val def = DiscoverShelfCatalog.allShelves.firstOrNull { it.id == shelfId } ?: return
        val current = _discoverShelves.value[shelfId] ?: DiscoverShelfState()
        if (!current.hasMore || current.isLoading) return
        if (!loadingDiscoverIds.add(shelfId)) return

        val nextPage = current.page + 1
        val effectiveTmdbKey = tmdbApiKey?.takeIf { it.isNotBlank() } ?: PreferenceManager.cachedTmdbApiKey
        _discoverShelves.value = _discoverShelves.value + (shelfId to current.copy(isLoading = true))

        viewModelScope.launch {
            try {
                val more = fetchShelfByDef(def, nextPage, effectiveTmdbKey)
                val existingKeys = current.items.mapTo(HashSet()) { "${it.mediaType}_${it.title.trim().lowercase()}" }
                val uniqueNew = more.filter { "${it.mediaType}_${it.title.trim().lowercase()}" !in existingKeys }
                _discoverShelves.value = _discoverShelves.value + (
                    shelfId to DiscoverShelfState(
                        items = current.items + uniqueNew,
                        page = nextPage,
                        isLoading = false,
                        hasMore = uniqueNew.isNotEmpty()
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _discoverShelves.value = _discoverShelves.value + (shelfId to current.copy(isLoading = false))
            } finally {
                loadingDiscoverIds.remove(shelfId)
            }
        }
    }

    fun loadMoreHomeDiscoverShelf(rowKey: HomeRowKey, tmdbApiKey: String? = null) {
        if (!rowKey.name.startsWith("DISCOVER_")) return
        if (!loadingDiscoverShelves.add(rowKey)) return
        val nextPage = (homeDiscoverPages[rowKey] ?: 1) + 1
        viewModelScope.launch {
            try {
                val effectiveTmdbKey = tmdbApiKey?.takeIf { it.isNotBlank() } ?: PreferenceManager.cachedTmdbApiKey
                val more = when (rowKey) {
                    HomeRowKey.DISCOVER_ANIME -> homeSearchRepository.fetchDiscoverAnimeShelves("trending", nextPage)
                    HomeRowKey.DISCOVER_MANGA -> homeSearchRepository.fetchDiscoverMangaShelves("trending", nextPage)
                    HomeRowKey.DISCOVER_COMICS -> com.omnitrack.app.data.enrichment.ComicEnrichmentSource()
                        .fetchDiscoverComicsShelves("trending", nextPage)
                    HomeRowKey.DISCOVER_NOVELS -> homeSearchRepository.fetchDiscoverNovelsShelves("top_webnovels", nextPage)
                    HomeRowKey.DISCOVER_BOOKS -> homeSearchRepository.fetchDiscoverBooksShelves("trending", nextPage)
                    HomeRowKey.DISCOVER_SHOWS -> homeSearchRepository.fetchDiscoverShowsShelves("trending", effectiveTmdbKey, nextPage)
                    HomeRowKey.DISCOVER_MOVIES -> homeSearchRepository.fetchDiscoverMoviesShelves("trending", effectiveTmdbKey, nextPage)
                    HomeRowKey.DISCOVER_GAMES -> homeSearchRepository.fetchDiscoverGamesShelves("trending", nextPage)
                    HomeRowKey.DISCOVER_VNS -> homeSearchRepository.fetchDiscoverVisualNovelsShelves("popular", nextPage)
                    HomeRowKey.DISCOVER_FANFICTION -> {
                        val ao3Cookie = com.omnitrack.app.OmniTrackApplication.instance.trackerTokenManager
                            .getFanficCookie(com.omnitrack.app.data.model.FanficSite.AO3)
                        com.omnitrack.app.data.fanfic.FanficSiteEngine.fetchAo3DiscoverShelf("ao3_kudos", nextPage, ao3Cookie)
                    }
                    else -> emptyList()
                }
                if (more.isNotEmpty()) {
                    homeDiscoverPages[rowKey] = nextPage
                    val existing = _categoryDiscoverShelves.value[rowKey].orEmpty()
                    val seen = existing.mapTo(HashSet()) { "${it.mediaType}_${it.title.trim().lowercase()}" }
                    val merged = existing + more.filter { "${it.mediaType}_${it.title.trim().lowercase()}" !in seen }
                    _categoryDiscoverShelves.value = _categoryDiscoverShelves.value + (rowKey to merged)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                loadingDiscoverShelves.remove(rowKey)
            }
        }
    }

    fun removeFromHistory(mediaId: Long) {
        viewModelScope.launch {
            val entity = repository.getMediaByIdOnce(mediaId) ?: return@launch
            repository.updateMedia(entity.copy(lastUpdated = 0L))
        }
    }

    fun deleteMediaItem(mediaId: Long) {
        viewModelScope.launch {
            repository.deleteMediaById(mediaId)
        }
    }

    fun batchMarkCompleted(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            for (id in ids) {
                val entity = repository.getMediaByIdOnce(id) ?: continue
                val nextProg = if (entity.totalProgress != null && entity.totalProgress > 0.0) {
                    entity.totalProgress
                } else if (entity.mediaType == MediaType.MOVIE) {
                    1.0
                } else {
                    entity.currentProgress
                }
                if (entity.mediaType == MediaType.FANFICTION) {
                    runCatching {
                        repository.getFanficChaptersOnce(id).forEach { ch ->
                            if (!ch.isRead) {
                                repository.updateFanficChapter(ch.copy(isRead = true, readAtMs = now))
                            }
                        }
                    }
                }
                repository.updateMedia(
                    entity.copy(
                        status = TrackingStatus.COMPLETED,
                        currentProgress = nextProg,
                        lastUpdated = now
                    )
                )
            }
        }
    }

    fun batchMarkUnread(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            for (id in ids) {
                val entity = repository.getMediaByIdOnce(id) ?: continue
                if (entity.mediaType == MediaType.FANFICTION) {
                    runCatching {
                        repository.getFanficChaptersOnce(id).forEach { ch ->
                            if (ch.isRead) {
                                repository.updateFanficChapter(ch.copy(isRead = false, readAtMs = null))
                            }
                        }
                    }
                }
                repository.updateMedia(
                    entity.copy(
                        status = TrackingStatus.PLAN_TO_TRACK,
                        currentProgress = 0.0,
                        playtimeHours = 0.0,
                        bookAudioMinutes = if (entity.bookAudioMinutes != null) 0 else null,
                        lastUpdated = now
                    )
                )
            }
        }
    }

    fun batchIncrementProgress(summaries: Collection<MediaCardSummary>) {
        if (summaries.isEmpty()) return
        summaries.forEach { incrementProgress(it) }
    }

    fun batchSetStatus(ids: Collection<Long>, status: TrackingStatus) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            for (id in ids) {
                val entity = repository.getMediaByIdOnce(id) ?: continue
                val nextProg = if (status == TrackingStatus.COMPLETED && entity.totalProgress != null && entity.totalProgress > 0.0) {
                    entity.totalProgress
                } else {
                    entity.currentProgress
                }
                repository.updateMedia(
                    entity.copy(
                        status = status,
                        currentProgress = nextProg,
                        lastUpdated = now
                    )
                )
            }
        }
    }

    fun batchSyncToTrackers(ids: Collection<Long>, onDone: (Int) -> Unit = {}) {
        if (ids.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var syncedCount = 0
            val engine = com.omnitrack.app.OmniTrackApplication.instance.trackerSyncEngine
            for (id in ids) {
                val entity = repository.getMediaByIdOnce(id) ?: continue
                val outcomes = runCatching { engine.pushItemToTrackers(entity) }.getOrDefault(emptyList())
                if (outcomes.any { it.success }) syncedCount++
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onDone(syncedCount)
            }
        }
    }

    fun batchRemoveFromHistory(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            for (id in ids) {
                val entity = repository.getMediaByIdOnce(id) ?: continue
                repository.updateMedia(entity.copy(lastUpdated = 0L))
            }
        }
    }

    fun batchDeleteItems(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            for (id in ids) {
                repository.deleteMediaById(id)
            }
        }
    }

    fun createCategory(name: String, onCreated: (Long) -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val nextOrder = (allCategories.value.maxOfOrNull { it.orderIndex } ?: -1) + 1
            val insertedId = repository.insertCategory(
                CategoryEntity(name = trimmed, orderIndex = nextOrder, isVisible = true)
            )
            onCreated(insertedId)
        }
    }

    fun renameCategory(category: CategoryEntity, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.updateCategory(category.copy(name = trimmed))
        }
    }

    fun toggleCategoryVisibility(category: CategoryEntity) {
        viewModelScope.launch {
            repository.updateCategory(category.copy(isVisible = !category.isVisible))
        }
    }

    fun reorderCategories(ordered: List<CategoryEntity>) {
        viewModelScope.launch {
            val updated = ordered.mapIndexed { idx, cat -> cat.copy(orderIndex = idx) }
            repository.insertAllCategories(updated)
        }
    }

    fun deleteCategory(category: CategoryEntity, prefManager: PreferenceManager) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            prefManager.removeCategoryFromAllMedia(category.id)
        }
    }

    fun createCustomMediaEntry(
        entity: MediaItemEntity,
        categoryIds: Set<Long> = emptySet(),
        prefManager: PreferenceManager? = null,
        vnRoutes: List<com.omnitrack.app.ui.components.CustomVnRouteDraft> = emptyList(),
        onCreated: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val prepared = entity.copy(
                isCustomEntry = true,
                lastUpdated = now
            )
            val insertedId = repository.insertMedia(prepared)
            val pref = prefManager ?: runCatching { com.omnitrack.app.OmniTrackApplication.instance.preferenceManager }.getOrNull()
            if (insertedId > 0L && pref != null) {
                val targetCats = if (categoryIds.isNotEmpty()) {
                    categoryIds
                } else {
                    pref.chimanewLibrarySettings.firstOrNull()?.defaultCategoryId?.let { setOf(it) } ?: emptySet()
                }
                if (targetCats.isNotEmpty()) {
                    pref.setMediaCategories(insertedId, targetCats)
                }
            }
            if (insertedId > 0L && prepared.mediaType == MediaType.VISUAL_NOVEL && vnRoutes.isNotEmpty()) {
                val routeEntities = vnRoutes.mapIndexedNotNull { idx, draft ->
                    val clean = draft.routeName.trim()
                    if (clean.isBlank()) null
                    else com.omnitrack.app.data.db.entity.VnRouteEntity(
                        mediaId = insertedId,
                        routeName = clean,
                        routeType = draft.routeType,
                        characterImageUrl = draft.imageUrl,
                        imageFocalX = draft.imageFocalX,
                        imageFocalY = draft.imageFocalY,
                        imageZoom = draft.imageZoom,
                        isCompleted = draft.isCompleted,
                        completedAtMs = if (draft.isCompleted) now else null,
                        isSpoiler = draft.isSpoiler,
                        playtimeHours = draft.playtimeHours?.takeIf { it > 0.0 },
                        userRating = draft.userRating,
                        userNote = draft.userNote,
                        isBookmarked = draft.isBookmarked,
                        sortOrder = idx
                    )
                }
                if (routeEntities.isNotEmpty()) {
                    repository.insertVnRoutes(routeEntities)
                }
            }
            if (coverCache != null && diskCache != null && prepared.coverUrl.startsWith("http")) {
                coverCache.promoteFromDiskCache(prepared.coverUrl, diskCache)
            }
            onCreated(insertedId)
        }
    }

    fun prefetchLocalHeroRatings(
        item: MediaCardSummary,
        existingCached: CachedMultiRatings?,
        preferenceManager: PreferenceManager
    ) {
        val key = com.omnitrack.app.data.preferences.heroRatingsCacheKey(item.id, item.mediaType, item.title)
        prefetchHeroRatingsCore(
            cacheKey = key,
            mediaId = item.id,
            mediaType = item.mediaType,
            title = item.title,
            nativeTitle = item.nativeTitle,
            anilistId = item.anilistId,
            malId = item.malId,
            tmdbId = item.tmdbId,
            imdbId = item.imdbId,
            bangumiId = item.bangumiId,
            steamAppId = item.steamAppId,
            igdbId = item.igdbId,
            vndbId = item.vndbId,
            hardcoverId = item.hardcoverId,
            openLibraryId = item.openLibraryId,
            bookAuthor = item.bookAuthor,
            existingCached = existingCached,
            preferenceManager = preferenceManager
        )
    }

    fun prefetchSearchHeroRatings(
        item: SearchResultItem,
        existingCached: CachedMultiRatings?,
        preferenceManager: PreferenceManager
    ) {
        val key = com.omnitrack.app.data.preferences.heroRatingsCacheKey(null, item.mediaType, item.title)
        prefetchHeroRatingsCore(
            cacheKey = key,
            mediaId = null,
            mediaType = item.mediaType,
            title = item.title,
            nativeTitle = item.nativeTitle,
            anilistId = item.anilistId,
            malId = item.malId,
            tmdbId = item.tmdbId,
            imdbId = item.imdbId,
            bangumiId = item.bangumiId,
            steamAppId = item.steamAppId,
            igdbId = null,
            vndbId = item.vndbId,
            hardcoverId = null,
            openLibraryId = item.openLibraryId,
            bookAuthor = item.bookAuthor,
            existingCached = existingCached,
            preferenceManager = preferenceManager
        )
    }

    private fun prefetchHeroRatingsCore(
        cacheKey: String,
        mediaId: Long?,
        mediaType: MediaType,
        title: String,
        nativeTitle: String?,
        anilistId: Long?,
        malId: Long?,
        tmdbId: Long?,
        imdbId: String?,
        bangumiId: Long?,
        steamAppId: Long?,
        igdbId: Long?,
        vndbId: String?,
        hardcoverId: String?,
        openLibraryId: String?,
        bookAuthor: String?,
        existingCached: CachedMultiRatings?,
        preferenceManager: PreferenceManager
    ) {
        if (mediaType == MediaType.FANFICTION || title.isBlank()) return
        val freshWindowMs = 7L * 24 * 60 * 60 * 1000L
        if (existingCached != null && existingCached.hasAnyScore() &&
            (System.currentTimeMillis() - existingCached.updatedAtMs) < freshWindowMs
        ) {
            return
        }
        if (!heroPrefetchedKeys.add(cacheKey)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tmdbKey = preferenceManager.tmdbApiKey.firstOrNull().orEmpty()
                val mdbKey = preferenceManager.mdblistApiKey.firstOrNull().orEmpty()
                val fetched = heroEnrichmentRepository.fetchHeroMultiRatings(
                    mediaType = mediaType,
                    title = title,
                    nativeTitle = nativeTitle,
                    anilistId = anilistId,
                    malId = malId,
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    bangumiId = bangumiId,
                    steamAppId = steamAppId,
                    igdbId = igdbId,
                    vndbId = vndbId,
                    hardcoverId = hardcoverId,
                    openLibraryId = openLibraryId,
                    bookAuthor = bookAuthor,
                    tmdbApiKey = tmdbKey.ifBlank { PreferenceManager.cachedTmdbApiKey },
                    mdblistApiKey = mdbKey.ifBlank { PreferenceManager.cachedMdblistApiKey }
                )
                if (fetched.hasAnyScore()) {
                    preferenceManager.mergeCachedMultiRatings(cacheKey, fetched)
                    if (mediaId != null && mediaId > 0L && (mediaType == MediaType.SHOW || mediaType == MediaType.MOVIE)) {
                        val current = repository.getMediaByIdOnce(mediaId)
                        if (current != null) {
                            val updated = current.copy(
                                imdbScore = current.imdbScore ?: fetched.imdbScore,
                                communityScore = current.communityScore ?: fetched.tmdbScore,
                                traktScore = current.traktScore ?: fetched.traktScore,
                                audienceScore = current.audienceScore ?: fetched.audienceScore,
                                rtScore = current.rtScore ?: fetched.rtScore,
                                metacriticScore = current.metacriticScore ?: fetched.metacriticScore
                            )
                            if (updated != current) {
                                repository.updateMedia(updated)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun observeMediaById(id: Long): Flow<MediaItemEntity?> = repository.getMediaById(id)

    private fun deleteLocalArtFileIfNeeded(path: String?) {
        if (path.isNullOrBlank() || path == "NONE" || path.startsWith("http://") || path.startsWith("https://")) return
        try {
            java.io.File(path).delete()
        } catch (_: Exception) {
        }
    }

    fun updateItemCover(mediaId: Long, newCoverUrl: String) {
        viewModelScope.launch {
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            if (!latest.customCoverPath.isNullOrBlank() && latest.customCoverPath != newCoverUrl) {
                deleteLocalArtFileIfNeeded(latest.customCoverPath)
            }
            val nextRole = if (latest.artworkRole == "BANNER_AS_COVER") "DEFAULT" else latest.artworkRole
            repository.updateMedia(
                latest.copy(
                    coverUrl = newCoverUrl,
                    customCoverPath = newCoverUrl,
                    artworkRole = nextRole
                )
            )
        }
    }

    fun updateItemCustomCoverPath(mediaId: Long, path: String?) {
        viewModelScope.launch {
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            if (!latest.customCoverPath.isNullOrBlank() && latest.customCoverPath != path) {
                deleteLocalArtFileIfNeeded(latest.customCoverPath)
            }
            val nextRole = if (path != null && latest.artworkRole == "BANNER_AS_COVER") "DEFAULT" else latest.artworkRole
            repository.updateMedia(latest.copy(customCoverPath = path, artworkRole = nextRole))
        }
    }

    fun updateItemBanner(mediaId: Long, newBannerUrl: String) {
        viewModelScope.launch {
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            if (!latest.customBannerPath.isNullOrBlank() && latest.customBannerPath != newBannerUrl) {
                deleteLocalArtFileIfNeeded(latest.customBannerPath)
            }
            val nextRole = if (latest.artworkRole == "DISABLE_BANNER" || latest.artworkRole == "COVER_AS_BANNER") {
                "DEFAULT"
            } else {
                latest.artworkRole
            }
            repository.updateMedia(
                latest.copy(
                    bannerUrl = newBannerUrl,
                    customBannerPath = newBannerUrl,
                    artworkRole = nextRole
                )
            )
        }
    }

    fun updateItemCustomBannerPath(mediaId: Long, path: String?) {
        viewModelScope.launch {
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            if (!latest.customBannerPath.isNullOrBlank() && latest.customBannerPath != path) {
                deleteLocalArtFileIfNeeded(latest.customBannerPath)
            }
            val nextRole = when {
                path == "NONE" -> "DISABLE_BANNER"
                path != null && (latest.artworkRole == "DISABLE_BANNER" || latest.artworkRole == "COVER_AS_BANNER") -> "DEFAULT"
                path == null && latest.artworkRole == "DISABLE_BANNER" -> "DEFAULT"
                else -> latest.artworkRole
            }
            repository.updateMedia(latest.copy(customBannerPath = path, artworkRole = nextRole))
        }
    }

    fun resetItemCover(mediaId: Long) {
        viewModelScope.launch {
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            deleteLocalArtFileIfNeeded(latest.customCoverPath)
            val nextRole = if (latest.artworkRole == "BANNER_AS_COVER") "DEFAULT" else latest.artworkRole
            repository.updateMedia(latest.copy(customCoverPath = null, artworkRole = nextRole))
        }
    }

    fun resetItemBanner(mediaId: Long) {
        viewModelScope.launch {
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            deleteLocalArtFileIfNeeded(latest.customBannerPath)
            val nextRole = if (latest.artworkRole == "DISABLE_BANNER" || latest.artworkRole == "COVER_AS_BANNER") {
                "DEFAULT"
            } else {
                latest.artworkRole
            }
            repository.updateMedia(latest.copy(bannerUrl = null, customBannerPath = null, artworkRole = nextRole))
        }
    }

    fun toggleItemDisableBanner(mediaId: Long) {
        viewModelScope.launch {
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val currentlyDisabled = latest.artworkRole == "DISABLE_BANNER" || latest.customBannerPath == "NONE"
            val newRole = if (currentlyDisabled) "DEFAULT" else "DISABLE_BANNER"
            val newCustomBanner = if (currentlyDisabled) {
                latest.customBannerPath?.takeUnless { it == "NONE" }
            } else {
                "NONE"
            }
            repository.updateMedia(latest.copy(artworkRole = newRole, customBannerPath = newCustomBanner))
        }
    }

    fun updateItemArtworkRole(mediaId: Long, role: String) {
        viewModelScope.launch {
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val normalizedBannerPath = when {
                role == "DISABLE_BANNER" -> "NONE"
                latest.customBannerPath == "NONE" -> null
                else -> latest.customBannerPath
            }
            repository.updateMedia(latest.copy(artworkRole = role, customBannerPath = normalizedBannerPath))
        }
    }

    fun updateItemBannerFraming(mediaId: Long, focalX: Float, focalY: Float, zoom: Float) {
        val fx = focalX.coerceIn(-1f, 1f)
        val fy = focalY.coerceIn(-1f, 1f)
        val z = zoom.coerceIn(1f, 2.5f)
        viewModelScope.launch {
            repository.updateBannerFraming(mediaId, fx, fy, z)
        }
    }

    fun updateItemExcludedArtSources(mediaId: Long, sources: Set<String>) {
        viewModelScope.launch {
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val serialized = sources
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .joinToString(",")
                .takeIf { it.isNotBlank() }
            repository.updateMedia(latest.copy(excludedArtSources = serialized))
        }
    }

    fun loadDiscoverHighlights(mediaFilter: MediaType, regionPref: String, tmdbApiKey: String? = null) {
        val effectiveRegion = if (regionPref.equals("AUTO", ignoreCase = true) || regionPref.isBlank()) {
            PreferenceManager.resolveEffectiveWatchRegion()
        } else {
            regionPref.trim().uppercase()
        }
        val key = "${mediaFilter.name}_$effectiveRegion"
        if (loadedHighlightsKey == key && _top10TodayItems.value.isNotEmpty()) return
        loadedHighlightsKey = key
        val tmdbKey = tmdbApiKey?.takeIf { it.isNotBlank() } ?: PreferenceManager.cachedTmdbApiKey

        viewModelScope.launch(Dispatchers.IO) {
            coroutineScope {
                launch {
                    val pick = runCatching { homeSearchRepository.fetchDailyCriticsPick(tmdbKey) }.getOrNull()
                    if (pick != null) _dailyCriticsPick.value = pick
                }
                launch {
                    val top10 = runCatching { homeSearchRepository.fetchTop10Today(mediaFilter, tmdbKey) }.getOrDefault(emptyList())
                    _top10TodayItems.value = top10
                }
                launch {
                    val providers = runCatching {
                        homeSearchRepository.fetchPopularStreamingProviders(effectiveRegion, tmdbKey)
                    }.getOrDefault(emptyList())
                    _streamingProviders.value = providers
                    val activeId = _selectedStreamingProviderId.value?.takeIf { cur ->
                        providers.any { it.providerId == cur }
                    } ?: providers.firstOrNull()?.providerId
                    _selectedStreamingProviderId.value = activeId
                    if (activeId != null) {
                        _isLoadingStreamingTitles.value = true
                        _streamingServiceTitles.value = runCatching {
                            homeSearchRepository.fetchStreamingServiceTitles(activeId, effectiveRegion, mediaFilter, tmdbKey)
                        }.getOrDefault(emptyList())
                        _isLoadingStreamingTitles.value = false
                    }
                }
                launch {
                    val rowKey = _selectedCuratedRowKey.value
                    _isLoadingCuratedRow.value = true
                    _curatedRowItems.value = runCatching {
                        homeSearchRepository.fetchCuratedDiscoverRow(rowKey, tmdbKey)
                    }.getOrDefault(emptyList())
                    _isLoadingCuratedRow.value = false
                }
                launch {
                    val library = uiState.value.allMediaSummaries.ifEmpty {
                        repository.getAllMediaSummaries().firstOrNull().orEmpty()
                    }
                    val candidateSeed = library.filter { summary ->
                        val matchFilter = mediaFilter == MediaType.ALL || summary.mediaType == mediaFilter
                        val hasId = (summary.tmdbId ?: 0L) > 0L || (summary.anilistId ?: 0L) > 0L
                        matchFilter && hasId && ((summary.userRating ?: 0f) >= 7.5f || summary.isFavorite)
                    }.sortedWith(
                        compareByDescending<MediaCardSummary> { it.isFavorite }
                            .thenByDescending { it.userRating ?: 0f }
                            .thenByDescending { it.lastUpdated }
                    ).firstOrNull()
                    _tasteMatchedSeed.value = candidateSeed
                    _tasteMatchedItems.value = if (candidateSeed != null) {
                        runCatching { homeSearchRepository.fetchTasteMatchedPicks(candidateSeed, tmdbKey) }.getOrDefault(emptyList())
                    } else emptyList()
                }
            }
        }
    }

    fun selectStreamingProvider(providerId: Int, regionPref: String, mediaFilter: MediaType, tmdbApiKey: String? = null) {
        _selectedStreamingProviderId.value = providerId
        val effectiveRegion = if (regionPref.equals("AUTO", ignoreCase = true) || regionPref.isBlank()) {
            PreferenceManager.resolveEffectiveWatchRegion()
        } else {
            regionPref.trim().uppercase()
        }
        val tmdbKey = tmdbApiKey?.takeIf { it.isNotBlank() } ?: PreferenceManager.cachedTmdbApiKey
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingStreamingTitles.value = true
            try {
                _streamingServiceTitles.value = runCatching {
                    homeSearchRepository.fetchStreamingServiceTitles(providerId, effectiveRegion, mediaFilter, tmdbKey)
                }.getOrDefault(emptyList())
            } finally {
                _isLoadingStreamingTitles.value = false
            }
        }
    }

    fun selectCuratedRow(rowKey: String, tmdbApiKey: String? = null) {
        _selectedCuratedRowKey.value = rowKey
        val tmdbKey = tmdbApiKey?.takeIf { it.isNotBlank() } ?: PreferenceManager.cachedTmdbApiKey
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingCuratedRow.value = true
            try {
                _curatedRowItems.value = runCatching {
                    homeSearchRepository.fetchCuratedDiscoverRow(rowKey, tmdbKey)
                }.getOrDefault(emptyList())
            } finally {
                _isLoadingCuratedRow.value = false
            }
        }
    }

    fun skipSwipeQueueItem(item: SearchResultItem) {
        val key = "${item.mediaType.name}_${item.tmdbId}_${item.anilistId}_${item.title.lowercase()}"
        _skippedSwipeKeys.value = _skippedSwipeKeys.value + key
    }

    fun buildSwipeQueue(allMedia: List<MediaCardSummary>): List<SearchResultItem> {
        val skipped = _skippedSwipeKeys.value
        val combined = (_tasteMatchedItems.value + _top10TodayItems.value + _curatedRowItems.value + _streamingServiceTitles.value +
            _discoverShelves.value.values.flatMap { it.items })
        return combined
            .distinctBy { "${it.mediaType.name}_${it.title.trim().lowercase()}" }
            .filter { item ->
                val key = "${item.mediaType.name}_${item.tmdbId}_${item.anilistId}_${item.title.lowercase()}"
                key !in skipped && ArtworkModelResolver.findLibraryMatch(
                    library = allMedia,
                    mediaType = item.mediaType,
                    anilistId = item.anilistId,
                    malId = item.malId,
                    tmdbId = item.tmdbId,
                    openLibraryId = item.openLibraryId,
                    title = item.title
                ) == null
            }
            .take(30)
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
