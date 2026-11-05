package com.omnitrack.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val mediaItems: List<MediaItemEntity> = emptyList(),
    val allMediaItems: List<MediaItemEntity> = emptyList(),
    val selectedMediaType: MediaType = MediaType.ALL,
    val selectedStatus: TrackingStatus = TrackingStatus.ALL,
    val isLoading: Boolean = true,
    val isSeeded: Boolean = false
)

class HomeViewModel(private val repository: MediaRepository) : ViewModel() {

    private val _selectedMediaType = MutableStateFlow(MediaType.ALL)
    private val _selectedStatus = MutableStateFlow(TrackingStatus.ALL)

    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<HomeUiState> = combine(
        _selectedMediaType,
        _selectedStatus,
        _isLoading,
        repository.getAllMedia()
    ) { type, status, loading, allItems ->
        val filtered = allItems.filter { item ->
            (type == MediaType.ALL || item.mediaType == type) &&
            (status == TrackingStatus.ALL || item.status == status)
        }
        HomeUiState(
            mediaItems = filtered,
            allMediaItems = allItems,
            selectedMediaType = type,
            selectedStatus = status,
            isLoading = loading && allItems.isEmpty(),
            isSeeded = allItems.isNotEmpty()
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
            repository.getAllMedia().collect { items ->
                if (items.isEmpty()) {
                    repository.insertAllMedia(MockData.sampleMedia)
                    repository.insertAllCategories(MockData.categories)
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

    fun incrementProgress(item: MediaItemEntity) {
        viewModelScope.launch {
            val increment = when (item.mediaType) {
                MediaType.GAME, MediaType.VISUAL_NOVEL -> 0.5
                else -> 1.0
            }
            val newProgress = item.currentProgress + increment
            val clamped = if (item.totalProgress != null)
                newProgress.coerceAtMost(item.totalProgress) else newProgress
            repository.updateProgress(item.id, clamped)
        }
    }

    companion object {
        fun factory(repository: MediaRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(repository) as T
                }
            }
        }
    }
}
