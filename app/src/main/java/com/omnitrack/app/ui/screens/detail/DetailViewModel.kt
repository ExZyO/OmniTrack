package com.omnitrack.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.ui.components.Episode
import com.omnitrack.app.ui.components.Season
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
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
    mediaId: Long
) : ViewModel() {

    private val _mediaId = MutableStateFlow(mediaId)

    val uiState: StateFlow<DetailUiState> = _mediaId
        .flatMapLatest { id ->
            repository.getMediaById(id).filterNotNull().flatMapLatest { currentItem ->
                repository.getAllMedia().map { allItems ->
                    val siblings = allItems.filter { it.mediaType == currentItem.mediaType }
                    val currentIndex = siblings.indexOfFirst { it.id == currentItem.id }
                    DetailUiState(
                        item = currentItem,
                        seasons = generateMockSeasons(currentItem),
                        isLoading = false,
                        hasPreviousEntry = currentIndex > 0,
                        hasNextEntry = currentIndex in 0 until siblings.size - 1
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
        val currentItem = uiState.value.item ?: return false
        val currentId = _mediaId.value
        viewModelScope.launch {
            repository.getAllMedia().collect { allItems ->
                val siblings = allItems.filter { it.mediaType == currentItem.mediaType }
                val currentIndex = siblings.indexOfFirst { it.id == currentId }
                if (currentIndex > 0) {
                    _mediaId.value = siblings[currentIndex - 1].id
                }
                return@collect
            }
        }
        return true
    }

    fun navigateToNext(): Boolean {
        val currentItem = uiState.value.item ?: return false
        val currentId = _mediaId.value
        viewModelScope.launch {
            repository.getAllMedia().collect { allItems ->
                val siblings = allItems.filter { it.mediaType == currentItem.mediaType }
                val currentIndex = siblings.indexOfFirst { it.id == currentId }
                if (currentIndex in 0 until siblings.size - 1) {
                    _mediaId.value = siblings[currentIndex + 1].id
                }
                return@collect
            }
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
        fun factory(repository: MediaRepository, mediaId: Long): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DetailViewModel(repository, mediaId) as T
                }
            }
        }
    }
}
