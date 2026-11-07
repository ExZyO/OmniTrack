package com.omnitrack.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.util.MockData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    }.stateIn(
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

    fun incrementProgress(id: Long, mediaType: MediaType, currentProgress: Double, totalProgress: Double?) {
        viewModelScope.launch {
            val increment = when (mediaType) {
                MediaType.GAME, MediaType.VISUAL_NOVEL -> 0.5
                else -> 1.0
            }
            val newProgress = currentProgress + increment
            val clamped = if (totalProgress != null)
                newProgress.coerceAtMost(totalProgress) else newProgress
            repository.updateProgress(id, clamped)
        }
    }

    fun incrementProgress(summary: MediaCardSummary) {
        incrementProgress(summary.id, summary.mediaType, summary.currentProgress, summary.totalProgress)
    }

    fun incrementProgress(item: MediaItemEntity) {
        incrementProgress(item.id, item.mediaType, item.currentProgress, item.totalProgress)
    }

    fun addMediaFromSearch(searchResult: com.omnitrack.app.data.search.SearchResultItem, status: TrackingStatus = TrackingStatus.PLAN_TO_TRACK) {
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
