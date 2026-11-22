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
import com.omnitrack.app.data.preferences.DiscoverShelfCatalog
import com.omnitrack.app.data.preferences.DiscoverShelfDef
import com.omnitrack.app.data.preferences.HomeRowKey
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.search.OmniSearchRepository
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.util.MockData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
        val byTmdb = library.filter { it.tmdbId != null }.associateBy { it.tmdbId }
        val byImdb = library.filter { !it.imdbId.isNullOrBlank() }.associateBy { it.imdbId }
        val bySteam = library.filter { it.steamAppId != null }.associateBy { it.steamAppId }
        val byMetron = library.filter { it.metronId != null }.associateBy { it.metronId }
        val byTypedTitle = library.associateBy { "${it.mediaType.name}_${it.title.trim().lowercase()}" }
        val byTitle = library.associateBy { it.title.trim().lowercase() }
        raw.map { rel ->
            val matched = (rel.anilistId?.let { byAnilist[it] })
                ?: (rel.malId?.let { byMal[it] })
                ?: (rel.tmdbId?.let { byTmdb[it] })
                ?: (rel.imdbId?.let { byImdb[it] })
                ?: (rel.steamAppId?.let { bySteam[it] })
                ?: (rel.metronId?.let { byMetron[it] })
                ?: byTypedTitle["${rel.mediaType.name}_${rel.title.trim().lowercase()}"]
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
                    val comicsDeferred = async {
                        runCatching {
                            com.omnitrack.app.data.enrichment.ComicEnrichmentSource()
                                .fetchDiscoverComicsShelves("trending")
                                .take(6)
                        }.getOrDefault(emptyList())
                    }
                    val ani = aniDeferred.await()
                    val books = booksDeferred.await()
                    val games = gamesDeferred.await()
                    val comics = comicsDeferred.await()
                    val merged = mutableListOf<SearchResultItem>()
                    val maxLen = maxOf(ani.size, books.size, games.size, comics.size)
                    for (i in 0 until maxLen) {
                        if (i < ani.size) merged.add(ani[i])
                        if (i < books.size) merged.add(books[i])
                        if (i < games.size) merged.add(games[i])
                        if (i < comics.size) merged.add(comics[i])
                    }
                    _trendingItems.value = merged
                        .distinctBy { "${it.mediaType}_${it.title.trim().lowercase()}" }
                        .take(24)
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
                deltaLabel = status.displayName,
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
            repository.insertMedia(entity)
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

    fun createCustomMediaEntry(entity: MediaItemEntity, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val prepared = entity.copy(
                isCustomEntry = true,
                lastUpdated = now
            )
            val insertedId = repository.insertMedia(prepared)
            if (coverCache != null && diskCache != null && prepared.coverUrl.startsWith("http")) {
                coverCache.promoteFromDiskCache(prepared.coverUrl, diskCache)
            }
            onCreated(insertedId)
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
