package com.omnitrack.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.enrichment.EnrichmentRepository
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.ui.components.Episode
import com.omnitrack.app.ui.components.Season
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val item: MediaItemEntity? = null,
    val seasons: List<Season> = emptyList(),
    val isLoading: Boolean = true,
    val hasPreviousEntry: Boolean = false,
    val hasNextEntry: Boolean = false
)

class DetailViewModel(
    private val repository: MediaRepository,
    mediaId: Long,
    private val enrichmentRepository: EnrichmentRepository = EnrichmentRepository(),
    private val preferenceManager: PreferenceManager? = null
) : ViewModel() {

    private val _mediaId = MutableStateFlow(mediaId)
    private val enrichedIds = mutableSetOf<Long>()

    private fun maybeEnrich(item: MediaItemEntity) {
        if (!enrichedIds.add(item.id)) return
        if (item.clearartUrl == null || item.communityScore == null || item.imdbScore == null) {
            viewModelScope.launch(Dispatchers.IO) {
                val fanartKey = preferenceManager?.fanartApiKey?.firstOrNull()?.ifBlank { null }
                val tmdbKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null }
                val mdbKey = preferenceManager?.mdblistApiKey?.firstOrNull()?.ifBlank { null }

                val enrichment = enrichmentRepository.enrich(
                    item = item,
                    fanartApiKey = fanartKey,
                    tmdbApiKey = tmdbKey,
                    mdblistApiKey = mdbKey
                )
                repository.updateEnrichment(
                    id = item.id,
                    clearart = enrichment.clearartUrl ?: item.clearartUrl,
                    score = enrichment.communityScore ?: item.communityScore,
                    imdb = enrichment.imdbScore ?: item.imdbScore,
                    rt = enrichment.rtScore ?: item.rtScore,
                    meta = enrichment.metacriticScore ?: item.metacriticScore
                )
            }
        }
    }

    val uiState: StateFlow<DetailUiState> = _mediaId
        .flatMapLatest { id ->
            repository.getMediaById(id).filterNotNull().flatMapLatest { currentItem ->
                maybeEnrich(currentItem)
                repository.getAllMedia().map { allItems ->
                    val sameType = allItems.filter { it.mediaType == currentItem.mediaType }
                    val siblings = if (sameType.size > 1) sameType else allItems
                    DetailUiState(
                        item = currentItem,
                        seasons = generateMockSeasons(currentItem),
                        isLoading = false,
                        hasPreviousEntry = siblings.size > 1,
                        hasNextEntry = siblings.size > 1
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetailUiState()
        )

    fun navigateToPrevious(): Boolean {
        val currentId = _mediaId.value
        viewModelScope.launch {
            val allItems = repository.getAllMedia().first()
            val currentItem = allItems.firstOrNull { it.id == currentId } ?: return@launch
            val sameType = allItems.filter { it.mediaType == currentItem.mediaType }
            val siblings = if (sameType.size > 1) sameType else allItems
            if (siblings.size <= 1) return@launch
            val currentIndex = siblings.indexOfFirst { it.id == currentId }
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else siblings.size - 1
            _mediaId.value = siblings[prevIndex].id
        }
        return true
    }

    fun navigateToNext(): Boolean {
        val currentId = _mediaId.value
        viewModelScope.launch {
            val allItems = repository.getAllMedia().first()
            val currentItem = allItems.firstOrNull { it.id == currentId } ?: return@launch
            val sameType = allItems.filter { it.mediaType == currentItem.mediaType }
            val siblings = if (sameType.size > 1) sameType else allItems
            if (siblings.size <= 1) return@launch
            val currentIndex = siblings.indexOfFirst { it.id == currentId }
            val nextIndex = if (currentIndex in 0 until siblings.size - 1) currentIndex + 1 else 0
            _mediaId.value = siblings[nextIndex].id
        }
        return true
    }

    fun updateProgress(newProgress: Double) {
        viewModelScope.launch {
            repository.updateProgress(_mediaId.value, newProgress)
        }
    }

    fun updateCover(newCoverUrl: String) {
        viewModelScope.launch {
            repository.updateCover(_mediaId.value, newCoverUrl)
        }
    }

    fun updateBanner(newBannerUrl: String) {
        viewModelScope.launch {
            repository.updateBanner(_mediaId.value, newBannerUrl)
        }
    }

    fun updateCustomCoverPath(path: String?, context: android.content.Context? = null) {
        viewModelScope.launch {
            repository.updateCustomCover(_mediaId.value, path)
            if (context != null && path != null) {
                val file = java.io.File(path)
                val loader = coil3.SingletonImageLoader.get(context)
                loader.memoryCache?.remove(coil3.memory.MemoryCache.Key(file.absolutePath))
                loader.diskCache?.remove(file.absolutePath)
            }
        }
    }

    fun updateCustomBannerPath(path: String?, context: android.content.Context? = null) {
        viewModelScope.launch {
            repository.updateCustomBanner(_mediaId.value, path)
            if (context != null && path != null && path != "NONE") {
                val file = java.io.File(path)
                val loader = coil3.SingletonImageLoader.get(context)
                loader.memoryCache?.remove(coil3.memory.MemoryCache.Key(file.absolutePath))
                loader.diskCache?.remove(file.absolutePath)
            }
        }
    }

    fun resetCover(context: android.content.Context? = null) {
        viewModelScope.launch {
            val mediaId = _mediaId.value
            if (context != null) {
                val coverCache = (context.applicationContext as? com.omnitrack.app.OmniTrackApplication)?.coverCache
                    ?: com.omnitrack.app.data.cache.CoverCache(context)
                coverCache.deleteCustomCover(mediaId)
            }
            repository.updateCustomCover(mediaId, null)
        }
    }

    fun resetBanner(context: android.content.Context? = null) {
        viewModelScope.launch {
            val mediaId = _mediaId.value
            if (context != null) {
                val coverCache = (context.applicationContext as? com.omnitrack.app.OmniTrackApplication)?.coverCache
                    ?: com.omnitrack.app.data.cache.CoverCache(context)
                coverCache.deleteCustomBanner(mediaId)
            }
            repository.updateCustomBanner(mediaId, null)
        }
    }

    fun toggleDisableBanner() {
        viewModelScope.launch {
            val currentItem = uiState.value.item ?: return@launch
            val nextPath = if (currentItem.customBannerPath == "NONE") null else "NONE"
            repository.updateCustomBanner(_mediaId.value, nextPath)
        }
    }

    fun updateArtworkRole(role: String) {
        viewModelScope.launch {
            repository.updateArtworkRole(_mediaId.value, role)
        }
    }

    fun updateRating(rating: Float?) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.updateMedia(item.copy(userRating = rating))
        }
    }

    fun updateStatus(status: TrackingStatus) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.updateMedia(item.copy(status = status))
        }
    }

    fun updatePlatform(platform: String) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.updateMedia(item.copy(platform = platform))
        }
    }

    fun deleteItem() {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.deleteMedia(item)
        }
    }

    /**
     * Generate mock season/episode data from the item's progress
     */
    private fun generateMockSeasons(item: MediaItemEntity): List<Season> {
        val total = item.totalProgress?.toInt() ?: return emptyList()
        val watched = item.currentProgress.toInt()

        return if (total <= 26) {
            listOf(
                Season(
                    name = "Season 1",
                    episodes = (1..total).map { ep ->
                        Episode(
                            number = ep,
                            title = "Episode $ep",
                            isWatched = ep <= watched
                        )
                    }
                )
            )
        } else {
            val seasons = (total + 11) / 12
            (1..seasons).map { s ->
                val start = (s - 1) * 12 + 1
                val end = minOf(s * 12, total)
                Season(
                    name = "Season $s",
                    episodes = (start..end).map { ep ->
                        Episode(
                            number = ep,
                            title = "Episode $ep",
                            isWatched = ep <= watched
                        )
                    }
                )
            }
        }
    }

    companion object {
        fun factory(
            repository: MediaRepository,
            mediaId: Long,
            enrichmentRepository: EnrichmentRepository = EnrichmentRepository(),
            preferenceManager: PreferenceManager? = null
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DetailViewModel(repository, mediaId, enrichmentRepository, preferenceManager) as T
                }
            }
        }
    }
}
