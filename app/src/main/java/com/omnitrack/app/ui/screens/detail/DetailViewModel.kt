package com.omnitrack.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.enrichment.EnrichmentRepository
import com.omnitrack.app.data.model.MediaEpisode
import com.omnitrack.app.data.model.MediaSeason
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.NextAiringEpisode
import com.omnitrack.app.data.model.AnimeAiringSchedule
import com.omnitrack.app.data.model.AnimeCharacterItem
import com.omnitrack.app.data.model.AnimeRelationItem
import com.omnitrack.app.data.model.AnimeStaffItem
import com.omnitrack.app.data.preferences.DetailSectionItem
import com.omnitrack.app.data.preferences.DetailsPageLayoutDefaults
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.data.sync.TrackerSyncEngine
import com.omnitrack.app.ui.components.Episode
import com.omnitrack.app.ui.components.Season
import com.omnitrack.app.util.AirDateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.omnitrack.app.ui.screens.detail.components.CastMember
import com.omnitrack.app.ui.screens.detail.components.CommunityReview
import com.omnitrack.app.ui.screens.detail.components.MediaTrailer
import com.omnitrack.app.ui.screens.detail.components.NetworkProductionCompany
import com.omnitrack.app.ui.screens.detail.components.RecommendationItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request

data class DetailUiState(
    val item: MediaItemEntity? = null,
    val previousItem: MediaItemEntity? = null,
    val nextItem: MediaItemEntity? = null,
    val seasons: List<Season> = emptyList(),
    val realSeasons: List<MediaSeason> = emptyList(),
    val realEpisodes: List<MediaEpisode> = emptyList(),
    val selectedSeasonNumber: Int = 1,
    val episodesBySeason: Map<Int, List<MediaEpisode>> = emptyMap(),
    val watchedEpisodes: Set<Pair<Int, Int>> = emptySet(),
    val recommendations: List<RecommendationItem> = emptyList(),
    val reviews: List<CommunityReview> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val trailers: List<MediaTrailer> = emptyList(),
    val companies: List<NetworkProductionCompany> = emptyList(),
    val collectionName: String? = null,
    val collectionItems: List<RecommendationItem> = emptyList(),
    val isPreviewMode: Boolean = false,
    val isLoading: Boolean = true,
    val hasPreviousEntry: Boolean = false,
    val hasNextEntry: Boolean = false,
    val nextAiringEpisode: NextAiringEpisode? = null,
    val animeCharacters: List<AnimeCharacterItem> = emptyList(),
    val animeStaff: List<AnimeStaffItem> = emptyList(),
    val animeRelations: List<AnimeRelationItem> = emptyList(),
    val animeAiring: AnimeAiringSchedule? = null,
    val sectionOrder: List<DetailSectionItem> = DetailsPageLayoutDefaults.defaultSections
)

private data class PreviewAndAux(
    val previewItem: MediaItemEntity?,
    val recommendations: List<RecommendationItem>,
    val reviews: List<CommunityReview>,
    val cast: List<CastMember>,
    val trailers: List<MediaTrailer>,
    val realSeasons: List<MediaSeason>,
    val realEpisodes: List<MediaEpisode>,
    val selectedSeasonNumber: Int,
    val episodesBySeason: Map<Int, List<MediaEpisode>>,
    val companies: List<NetworkProductionCompany>,
    val collectionName: String? = null,
    val collectionItems: List<RecommendationItem> = emptyList(),
    val previousItem: MediaItemEntity? = null,
    val nextItem: MediaItemEntity? = null,
    val nextAiringEpisode: NextAiringEpisode? = null,
    val animeCharacters: List<AnimeCharacterItem> = emptyList(),
    val animeStaff: List<AnimeStaffItem> = emptyList(),
    val animeRelations: List<AnimeRelationItem> = emptyList(),
    val animeAiring: AnimeAiringSchedule? = null,
    val sectionOrder: List<DetailSectionItem> = DetailsPageLayoutDefaults.defaultSections
)

class DetailViewModel(
    private val repository: MediaRepository,
    mediaId: Long,
    private val enrichmentRepository: EnrichmentRepository = EnrichmentRepository(),
    private val preferenceManager: PreferenceManager? = null,
    private val trackerSyncEngine: TrackerSyncEngine? = null
) : ViewModel() {

    private val _mediaId = MutableStateFlow(mediaId)
    private val _previewStack = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    private val _previewItem = MutableStateFlow<MediaItemEntity?>(null)
    private val _recommendations = MutableStateFlow<List<RecommendationItem>>(emptyList())
    private val _reviews = MutableStateFlow<List<CommunityReview>>(emptyList())
    private val _cast = MutableStateFlow<List<CastMember>>(emptyList())
    private val _trailers = MutableStateFlow<List<MediaTrailer>>(emptyList())
    private val _companies = MutableStateFlow<List<NetworkProductionCompany>>(emptyList())
    private val _realSeasons = MutableStateFlow<List<MediaSeason>>(emptyList())
    private val _realEpisodes = MutableStateFlow<List<MediaEpisode>>(emptyList())
    private val _selectedSeasonNumber = MutableStateFlow(1)
    private val _episodesBySeason = MutableStateFlow<Map<Int, List<MediaEpisode>>>(emptyMap())
    private val _collectionName = MutableStateFlow<String?>(null)
    private val _collectionItems = MutableStateFlow<List<RecommendationItem>>(emptyList())
    private val _previousItem = MutableStateFlow<MediaItemEntity?>(null)
    private val _nextItem = MutableStateFlow<MediaItemEntity?>(null)
    private val _nextAiringEpisode = MutableStateFlow<NextAiringEpisode?>(null)
    private val _animeCharacters = MutableStateFlow<List<AnimeCharacterItem>>(emptyList())
    private val _animeStaff = MutableStateFlow<List<AnimeStaffItem>>(emptyList())
    private val _animeRelations = MutableStateFlow<List<AnimeRelationItem>>(emptyList())
    private val _animeAiring = MutableStateFlow<AnimeAiringSchedule?>(null)
    private val _sectionOrder = MutableStateFlow<List<DetailSectionItem>>(DetailsPageLayoutDefaults.defaultSections)
    private val _stableSiblingIds = MutableStateFlow<List<Long>>(emptyList())
    private val enrichedIds = mutableSetOf<Long>()

    private fun updateSiblings(currentId: Long) {
        val siblings = _stableSiblingIds.value.ifEmpty {
            ActiveSiblingHolder.activeSiblingIds
        }
        if (siblings.size > 1) {
            val currentIndex = siblings.indexOf(currentId)
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else siblings.size - 1
            val nextIndex = if (currentIndex in 0 until siblings.size - 1) currentIndex + 1 else 0
            val prevId = siblings[prevIndex]
            val nextId = siblings[nextIndex]
            viewModelScope.launch(Dispatchers.IO) {
                _previousItem.value = repository.getMediaById(prevId).firstOrNull()
            }
            viewModelScope.launch(Dispatchers.IO) {
                _nextItem.value = repository.getMediaById(nextId).firstOrNull()
            }
        } else {
            _previousItem.value = null
            _nextItem.value = null
        }
    }

    init {
        if (preferenceManager != null) {
            viewModelScope.launch {
                preferenceManager.detailsPageSectionOrder.collect {
                    _sectionOrder.value = it
                }
            }
        }
        if (mediaId == -1L) {
            val ephemeral = EphemeralPreviewHolder.previewItem
            if (ephemeral != null) {
                _previewStack.value = listOf(ephemeral)
                _previewItem.value = ephemeral
            }
        }
        val active = ActiveSiblingHolder.activeSiblingIds
        if (active.isNotEmpty()) {
            _stableSiblingIds.value = active
            if (mediaId > 0L) {
                updateSiblings(mediaId)
            }
        } else if (mediaId > 0L) {
            viewModelScope.launch {
                val all = repository.getAllMedia().firstOrNull()?.map { it.id }.orEmpty()
                if (_stableSiblingIds.value.isEmpty() && all.isNotEmpty()) {
                    _stableSiblingIds.value = all
                    updateSiblings(mediaId)
                }
            }
        }

        // 0ms Synchronous Cache Hit: populate seasons and episodes at millisecond 0!
        if (mediaId > 0L) {
            val cachedTmdbId = SeriesContentMemoryCache.resolvedTmdbIdCache[mediaId]
            val cachedSeasons = SeriesContentMemoryCache.seasonsCache[mediaId]
                ?: cachedTmdbId?.let { SeriesContentMemoryCache.seasonsCache[it] }
            val cachedGrouped = SeriesContentMemoryCache.episodesBySeasonCache[mediaId]
                ?: cachedTmdbId?.let { SeriesContentMemoryCache.episodesBySeasonCache[it] }
            if (!cachedSeasons.isNullOrEmpty()) {
                val validSeasons = cachedSeasons.filter { it.seasonNumber > 0 || it.episodeCount > 0 }
                _realSeasons.value = validSeasons
                if (!cachedGrouped.isNullOrEmpty()) {
                    _episodesBySeason.value = cachedGrouped
                    val target = validSeasons.firstOrNull { it.seasonNumber > 0 }?.seasonNumber ?: validSeasons.first().seasonNumber
                    _selectedSeasonNumber.value = target
                    _realEpisodes.value = cachedGrouped[target].orEmpty()
                    refreshNextAiringEpisode()
                }
            }
        } else if (mediaId == -1L) {
            val ephemeral = EphemeralPreviewHolder.previewItem
            if (ephemeral != null) {
                val tmdbId = ephemeral.tmdbId
                val cachedSeasons = tmdbId?.let { SeriesContentMemoryCache.seasonsCache[it] }
                val cachedGrouped = tmdbId?.let { SeriesContentMemoryCache.episodesBySeasonCache[it] }
                if (!cachedSeasons.isNullOrEmpty()) {
                    val validSeasons = cachedSeasons.filter { it.seasonNumber > 0 || it.episodeCount > 0 }
                    _realSeasons.value = validSeasons
                    if (!cachedGrouped.isNullOrEmpty()) {
                        _episodesBySeason.value = cachedGrouped
                        val target = validSeasons.firstOrNull { it.seasonNumber > 0 }?.seasonNumber ?: validSeasons.first().seasonNumber
                        _selectedSeasonNumber.value = target
                        _realEpisodes.value = cachedGrouped[target].orEmpty()
                        refreshNextAiringEpisode(ephemeral)
                    }
                }
            }
        }

        // 0ms Detail Aux Synchronous Cache Hit (Cast, Trailers, Recommendations, Reviews, Companies, Collection):
        val cachedAux = if (mediaId > 0L) {
            DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getCacheKey(mediaId)]
                ?: SeriesContentMemoryCache.resolvedTmdbIdCache[mediaId]?.let {
                    DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getTmdbCacheKey("tv", it)]
                        ?: DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getTmdbCacheKey("movie", it)]
                }
        } else if (mediaId == -1L) {
            val ephemeral = EphemeralPreviewHolder.previewItem
            if (ephemeral != null) {
                DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getCacheKey(ephemeral)]
                    ?: ephemeral.tmdbId?.let {
                        val t = if (ephemeral.mediaType == MediaType.MOVIE) "movie" else "tv"
                        DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getTmdbCacheKey(t, it)]
                    }
            } else null
        } else null

        if (cachedAux != null) {
            if (cachedAux.recommendations.isNotEmpty()) _recommendations.value = cachedAux.recommendations
            if (cachedAux.reviews.isNotEmpty()) _reviews.value = cachedAux.reviews
            if (cachedAux.cast.isNotEmpty()) _cast.value = cachedAux.cast
            if (cachedAux.trailers.isNotEmpty()) _trailers.value = cachedAux.trailers
            if (cachedAux.companies.isNotEmpty()) _companies.value = cachedAux.companies
            if (cachedAux.collectionName != null) {
                _collectionName.value = cachedAux.collectionName
                _collectionItems.value = cachedAux.collectionItems
            }
            if (cachedAux.animeCharacters.isNotEmpty()) _animeCharacters.value = cachedAux.animeCharacters
            if (cachedAux.animeRelations.isNotEmpty()) _animeRelations.value = cachedAux.animeRelations
            if (cachedAux.animeAiring != null) _animeAiring.value = cachedAux.animeAiring
            if (cachedAux.nextAiringEpisode != null) _nextAiringEpisode.value = cachedAux.nextAiringEpisode
        }

        // 0ms Synchronous Series Seasons & Episodes Cache Hit (Nuvio 0-lag parity):
        val initCachedSeasons = (if (mediaId > 0L) SeriesContentMemoryCache.seasonsCache[mediaId] else null)
            ?: SeriesContentMemoryCache.resolvedTmdbIdCache[mediaId]?.let { SeriesContentMemoryCache.seasonsCache[it] }
            ?: (if (mediaId == -1L) EphemeralPreviewHolder.previewItem?.let { SeriesContentMemoryCache.seasonsCache[it.id] ?: it.tmdbId?.let { tid -> SeriesContentMemoryCache.seasonsCache[tid] } } else null)
        val initCachedGrouped = (if (mediaId > 0L) SeriesContentMemoryCache.episodesBySeasonCache[mediaId] else null)
            ?: SeriesContentMemoryCache.resolvedTmdbIdCache[mediaId]?.let { SeriesContentMemoryCache.episodesBySeasonCache[it] }
            ?: (if (mediaId == -1L) EphemeralPreviewHolder.previewItem?.let { SeriesContentMemoryCache.episodesBySeasonCache[it.id] ?: it.tmdbId?.let { tid -> SeriesContentMemoryCache.episodesBySeasonCache[tid] } } else null)

        if (!initCachedSeasons.isNullOrEmpty()) {
            val valid = initCachedSeasons.filter { it.seasonNumber > 0 || it.episodeCount > 0 }
            _realSeasons.value = valid
            if (!initCachedGrouped.isNullOrEmpty()) {
                _episodesBySeason.value = initCachedGrouped
                val defaultSeason = valid.firstOrNull { it.seasonNumber > 0 }?.seasonNumber ?: valid.first().seasonNumber
                _selectedSeasonNumber.value = defaultSeason
                _realEpisodes.value = initCachedGrouped[defaultSeason].orEmpty()
            }
        }

        if (mediaId == -1L) {
            val ephemeral = EphemeralPreviewHolder.previewItem
            if (ephemeral != null) {
                _previewStack.value = listOf(ephemeral)
                _previewItem.value = ephemeral
                loadRecommendationsAndReviews(ephemeral)
                maybeEnrich(ephemeral)
            }
        } else if (mediaId > 0L) {
            viewModelScope.launch(Dispatchers.IO) {
                val item = repository.getMediaById(mediaId).firstOrNull()
                if (item != null) {
                    loadRecommendationsAndReviews(item)
                }
            }
        }
    }

    private fun maybeEnrich(item: MediaItemEntity, force: Boolean = false) {
        if (!force && enrichedIds.contains(item.id)) return
        enrichedIds.add(item.id)

        val isVideoMedia = item.mediaType == MediaType.SHOW || item.mediaType == MediaType.MOVIE || item.mediaType == MediaType.ANIME
        val hasStaleRatings = isVideoMedia && (item.imdbScore == 6.7f || (item.imdbScore != null && item.traktScore == null))
        val needsEnrichment = force || (
            item.releaseYear == null ||
            item.contentRating == null ||
            (isVideoMedia && item.runtimeMinutes == null) ||
            (isVideoMedia && item.traktScore == null) ||
            hasStaleRatings
        )
        if (!needsEnrichment) return

        viewModelScope.launch(Dispatchers.IO) {
            val fanartKey = preferenceManager?.fanartApiKey?.firstOrNull()?.ifBlank { null }
            val tmdbKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
            val mdbKey = preferenceManager?.mdblistApiKey?.firstOrNull()?.ifBlank { null }

            val enrichment = enrichmentRepository.enrich(
                item = item,
                fanartApiKey = fanartKey,
                tmdbApiKey = tmdbKey,
                mdblistApiKey = mdbKey
            )

            if (item.id > 0) {
                // Fetch the latest state from repository so we never overwrite user-changed cover or banner
                val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                val updated = latest.copy(
                    tmdbId = enrichment.resolvedTmdbId ?: latest.tmdbId,
                    imdbId = enrichment.resolvedImdbId ?: latest.imdbId,
                    clearartUrl = enrichment.clearartUrl ?: latest.clearartUrl,
                    communityScore = enrichment.communityScore ?: latest.communityScore,
                    imdbScore = enrichment.imdbScore ?: latest.imdbScore,
                    rtScore = enrichment.rtScore ?: latest.rtScore,
                    metacriticScore = enrichment.metacriticScore ?: latest.metacriticScore,
                    traktScore = enrichment.traktScore ?: latest.traktScore,
                    audienceScore = enrichment.audienceScore ?: latest.audienceScore,
                    releaseYear = enrichment.releaseYear ?: latest.releaseYear,
                    trailerYoutubeKey = enrichment.trailerYoutubeKey ?: latest.trailerYoutubeKey,
                    studioOrNetwork = enrichment.studioOrNetwork ?: latest.studioOrNetwork,
                    runtimeMinutes = enrichment.runtimeMinutes ?: latest.runtimeMinutes,
                    contentRating = enrichment.contentRating ?: latest.contentRating,
                    airStatus = enrichment.airStatus ?: latest.airStatus,
                    totalProgress = if (latest.totalProgress == null || latest.totalProgress == 0.0) enrichment.totalEpisodes?.toDouble() ?: latest.totalProgress else latest.totalProgress
                )
                if (updated != latest) {
                    repository.updateMedia(updated)
                }
                if (_recommendations.value.isEmpty()) {
                    loadRecommendationsAndReviews(updated)
                }
            } else {
                val updated = item.copy(
                    tmdbId = enrichment.resolvedTmdbId ?: item.tmdbId,
                    imdbId = enrichment.resolvedImdbId ?: item.imdbId,
                    clearartUrl = enrichment.clearartUrl ?: item.clearartUrl,
                    communityScore = enrichment.communityScore ?: item.communityScore,
                    imdbScore = enrichment.imdbScore ?: item.imdbScore,
                    rtScore = enrichment.rtScore ?: item.rtScore,
                    metacriticScore = enrichment.metacriticScore ?: item.metacriticScore,
                    traktScore = enrichment.traktScore ?: item.traktScore,
                    audienceScore = enrichment.audienceScore ?: item.audienceScore,
                    releaseYear = enrichment.releaseYear ?: item.releaseYear,
                    trailerYoutubeKey = enrichment.trailerYoutubeKey ?: item.trailerYoutubeKey,
                    studioOrNetwork = enrichment.studioOrNetwork ?: item.studioOrNetwork,
                    runtimeMinutes = enrichment.runtimeMinutes ?: item.runtimeMinutes,
                    contentRating = enrichment.contentRating ?: item.contentRating,
                    airStatus = enrichment.airStatus ?: item.airStatus,
                    totalProgress = if (item.totalProgress == null || item.totalProgress == 0.0) enrichment.totalEpisodes?.toDouble() ?: item.totalProgress else item.totalProgress
                )
                _previewItem.value = updated
                _previewStack.update { stack ->
                    if (stack.isNotEmpty()) stack.dropLast(1) + updated else listOf(updated)
                }
                loadRecommendationsAndReviews(updated)
            }
            if (enrichment.videos.isNotEmpty()) {
                _trailers.value = enrichment.videos
            }
            if (enrichment.belongsToCollectionId != null && _collectionItems.value.isEmpty()) {
                val collData = enrichmentRepository.fetchTmdbCollection(enrichment.belongsToCollectionId, tmdbKey)
                if (collData != null) {
                    _collectionName.value = collData.name
                    _collectionItems.value = collData.parts
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DetailUiState> = combine(
        listOf(
            _previewItem,
            _recommendations,
            _reviews,
            _cast,
            _trailers,
            _realSeasons,
            _realEpisodes,
            _selectedSeasonNumber,
            _episodesBySeason,
            _companies,
            _collectionName,
            _collectionItems,
            _previousItem,
            _nextItem,
            _nextAiringEpisode,
            _animeCharacters,
            _animeStaff,
            _animeRelations,
            _animeAiring,
            _sectionOrder
        )
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        PreviewAndAux(
            previewItem = args[0] as? MediaItemEntity,
            recommendations = args[1] as List<RecommendationItem>,
            reviews = args[2] as List<CommunityReview>,
            cast = args[3] as List<CastMember>,
            trailers = args[4] as List<MediaTrailer>,
            realSeasons = args[5] as List<MediaSeason>,
            realEpisodes = args[6] as List<MediaEpisode>,
            selectedSeasonNumber = args[7] as Int,
            episodesBySeason = args[8] as Map<Int, List<MediaEpisode>>,
            companies = args[9] as List<NetworkProductionCompany>,
            collectionName = args[10] as? String,
            collectionItems = args[11] as List<RecommendationItem>,
            previousItem = args[12] as? MediaItemEntity,
            nextItem = args[13] as? MediaItemEntity,
            nextAiringEpisode = args[14] as? NextAiringEpisode,
            animeCharacters = args[15] as List<AnimeCharacterItem>,
            animeStaff = args[16] as List<AnimeStaffItem>,
            animeRelations = args[17] as List<AnimeRelationItem>,
            animeAiring = args[18] as? AnimeAiringSchedule,
            sectionOrder = args[19] as List<DetailSectionItem>
        )
    }.flatMapLatest { aux ->
        if (aux.previewItem != null) {
            flowOf(
                DetailUiState(
                    item = aux.previewItem,
                    previousItem = null,
                    nextItem = null,
                    seasons = emptyList(),
                    realSeasons = aux.realSeasons,
                    realEpisodes = aux.realEpisodes,
                    selectedSeasonNumber = aux.selectedSeasonNumber,
                    episodesBySeason = aux.episodesBySeason,
                    watchedEpisodes = emptySet(),
                    recommendations = aux.recommendations,
                    reviews = aux.reviews,
                    cast = aux.cast,
                    trailers = aux.trailers,
                    companies = aux.companies,
                    collectionName = aux.collectionName,
                    collectionItems = aux.collectionItems,
                    isPreviewMode = true,
                    isLoading = false,
                    hasPreviousEntry = false,
                    hasNextEntry = false,
                    nextAiringEpisode = aux.nextAiringEpisode,
                    animeCharacters = aux.animeCharacters,
                    animeStaff = aux.animeStaff,
                    animeRelations = aux.animeRelations,
                    animeAiring = aux.animeAiring,
                    sectionOrder = aux.sectionOrder
                )
            )
        } else {
            _mediaId.flatMapLatest { currentId ->
                val preseededItem = aux.previewItem?.takeIf { it.id == currentId }
                repository.getMediaById(currentId).flatMapLatest { currentItem ->
                    val effectiveItem = currentItem ?: preseededItem
                    if (effectiveItem != null) {
                        maybeEnrich(effectiveItem)
                        if (_realSeasons.value.isEmpty()) {
                            loadRecommendationsAndReviews(effectiveItem)
                        }
                        updateSiblings(effectiveItem.id)
                        repository.getEpisodeProgress(effectiveItem.id).map { progressList ->
                            val siblings = _stableSiblingIds.value.ifEmpty {
                                ActiveSiblingHolder.activeSiblingIds.ifEmpty { listOf(effectiveItem.id) }
                            }
                            val currentIndex = siblings.indexOf(effectiveItem.id)
                            val watchedSet = progressList.filter { it.isWatched }
                                .map { it.seasonNumber to it.episodeNumber }.toSet()
                            DetailUiState(
                                item = effectiveItem,
                                previousItem = aux.previousItem,
                                nextItem = aux.nextItem,
                                seasons = emptyList(),
                                realSeasons = aux.realSeasons,
                                realEpisodes = aux.realEpisodes,
                                selectedSeasonNumber = aux.selectedSeasonNumber,
                                episodesBySeason = aux.episodesBySeason,
                                watchedEpisodes = watchedSet,
                                recommendations = aux.recommendations,
                                reviews = aux.reviews,
                                cast = aux.cast,
                                trailers = if (aux.trailers.isEmpty() && effectiveItem.trailerYoutubeKey != null) {
                                    listOf(MediaTrailer("Official Trailer", effectiveItem.trailerYoutubeKey))
                                } else aux.trailers,
                                companies = aux.companies,
                                collectionName = aux.collectionName,
                                collectionItems = aux.collectionItems,
                                isPreviewMode = false,
                                isLoading = false,
                                hasPreviousEntry = siblings.size > 1,
                                hasNextEntry = siblings.size > 1,
                                nextAiringEpisode = aux.nextAiringEpisode,
                                animeCharacters = aux.animeCharacters,
                                animeStaff = aux.animeStaff,
                                animeRelations = aux.animeRelations,
                                animeAiring = aux.animeAiring,
                                sectionOrder = aux.sectionOrder
                            )
                        }
                    } else {
                        flowOf(DetailUiState(isLoading = false))
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = run {
            val pre = if (mediaId == -1L) EphemeralPreviewHolder.previewItem else null
            if (pre != null) DetailUiState(item = pre, isLoading = false) else DetailUiState()
        }
    )

    private fun resetAuxFields() {
        _collectionName.value = null
        _collectionItems.value = emptyList()
        _realSeasons.value = emptyList()
        _realEpisodes.value = emptyList()
        _episodesBySeason.value = emptyMap()
        _recommendations.value = emptyList()
        _reviews.value = emptyList()
        _cast.value = emptyList()
        _trailers.value = emptyList()
        _companies.value = emptyList()
        _animeCharacters.value = emptyList()
        _animeStaff.value = emptyList()
        _animeRelations.value = emptyList()
        _animeAiring.value = null
        _nextAiringEpisode.value = null
    }

    fun openPreview(rec: RecommendationItem) {
        val previewEntity = MediaItemEntity(
            id = -1L,
            title = rec.title,
            coverUrl = rec.coverUrl,
            bannerUrl = rec.bannerUrl,
            mediaType = rec.mediaType,
            synopsis = rec.synopsis,
            communityScore = rec.rating,
            tmdbId = rec.tmdbId,
            anilistId = rec.anilistId,
            malId = rec.malId,
            status = TrackingStatus.PLAN_TO_TRACK
        )
        openPreview(previewEntity)
    }

    fun openPreview(previewEntity: MediaItemEntity) {
        resetAuxFields()
        val pTmdb = previewEntity.tmdbId
        val pSeasons = (if (pTmdb != null) SeriesContentMemoryCache.seasonsCache[pTmdb] else null) ?: SeriesContentMemoryCache.seasonsCache[previewEntity.id]
        val pGrouped = (if (pTmdb != null) SeriesContentMemoryCache.episodesBySeasonCache[pTmdb] else null) ?: SeriesContentMemoryCache.episodesBySeasonCache[previewEntity.id]
        if (!pSeasons.isNullOrEmpty()) {
            val valid = pSeasons.filter { it.seasonNumber > 0 || it.episodeCount > 0 }
            _realSeasons.value = valid
            if (!pGrouped.isNullOrEmpty()) {
                _episodesBySeason.value = pGrouped
                val defaultSeason = valid.firstOrNull { it.seasonNumber > 0 }?.seasonNumber ?: valid.first().seasonNumber
                _selectedSeasonNumber.value = defaultSeason
                _realEpisodes.value = pGrouped[defaultSeason].orEmpty()
            }
        }
        _previewStack.update { it + previewEntity }
        _previewItem.value = previewEntity
        loadRecommendationsAndReviews(previewEntity)
        maybeEnrich(previewEntity)
        if (previewEntity.mediaType == MediaType.SHOW || previewEntity.mediaType == MediaType.ANIME) {
            launchSeriesFetch(previewEntity)
        }
    }

    fun openRelationPreview(relation: AnimeRelationItem) {
        val format = relation.format.uppercase()
        val targetType = when {
            format == "MANGA" -> MediaType.MANGA
            format == "NOVEL" -> MediaType.NOVEL
            format == "MOVIE" -> MediaType.MOVIE
            else -> MediaType.ANIME
        }
        val previewEntity = MediaItemEntity(
            id = -1L,
            title = relation.title,
            coverUrl = relation.coverUrl ?: "",
            bannerUrl = relation.coverUrl,
            mediaType = targetType,
            anilistId = relation.id,
            releaseYear = relation.year?.toString(),
            status = TrackingStatus.PLAN_TO_TRACK
        )
        openPreview(previewEntity)
    }

    fun closePreview(): Boolean {
        if (_previewStack.value.isEmpty() && _previewItem.value == null && EphemeralPreviewHolder.previewItem == null) {
            return false
        }
        if (_previewStack.value.size > 1) {
            _previewStack.update { it.dropLast(1) }
            val newTop = _previewStack.value.last()
            _previewItem.value = newTop
            resetAuxFields()
            loadRecommendationsAndReviews(newTop)
            maybeEnrich(newTop)
            if (newTop.mediaType == MediaType.SHOW || newTop.mediaType == MediaType.ANIME) {
                launchSeriesFetch(newTop)
            }
            return true
        }
        _previewStack.value = emptyList()
        _previewItem.value = null
        EphemeralPreviewHolder.previewItem = null
        val currentId = _mediaId.value
        resetAuxFields()
        if (currentId > 0L) {
            viewModelScope.launch(Dispatchers.IO) {
                val item = repository.getMediaById(currentId).firstOrNull()
                if (item != null) {
                    loadRecommendationsAndReviews(item)
                    launchSeriesFetch(item)
                }
            }
        }
        return true
    }

    override fun onCleared() {
        super.onCleared()
        EphemeralPreviewHolder.previewItem = null
        _previewStack.value = emptyList()
    }

    fun addToLibrary(item: MediaItemEntity) {
        viewModelScope.launch {
            val toInsert = item.copy(id = 0L)
            val newId = repository.insertMedia(toInsert)
            EphemeralPreviewHolder.previewItem = null
            _previewStack.value = emptyList()
            _previewItem.value = null
            _mediaId.value = newId
        }
    }

    private var recommendationFetchJob: kotlinx.coroutines.Job? = null
    private var seriesFetchJob: kotlinx.coroutines.Job? = null

    private fun loadRecommendationsAndReviews(item: MediaItemEntity) {
        val isShowOrAnime = item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME

        if (isShowOrAnime) {
            launchSeriesFetch(item)
        }

        val cacheKey = DetailAuxMemoryCache.getCacheKey(item)
        val itemTmdbKey = item.tmdbId?.let { DetailAuxMemoryCache.getTmdbCacheKey(if (item.mediaType == MediaType.MOVIE) "movie" else "tv", it) }
        val idKey = if (item.id > 0L) DetailAuxMemoryCache.getCacheKey(item.id) else null

        // 1. Instant 0ms Memory Cache Hit
        val existingCached = DetailAuxMemoryCache.cache[cacheKey]
            ?: (itemTmdbKey?.let { DetailAuxMemoryCache.cache[it] })
            ?: (idKey?.let { DetailAuxMemoryCache.cache[it] })

        if (existingCached != null) {
            if (existingCached.recommendations.isNotEmpty()) _recommendations.value = existingCached.recommendations
            if (existingCached.reviews.isNotEmpty()) _reviews.value = existingCached.reviews
            if (existingCached.cast.isNotEmpty()) _cast.value = existingCached.cast
            if (existingCached.trailers.isNotEmpty()) _trailers.value = existingCached.trailers
            if (existingCached.companies.isNotEmpty()) _companies.value = existingCached.companies
            if (existingCached.collectionName != null) {
                _collectionName.value = existingCached.collectionName
                _collectionItems.value = existingCached.collectionItems
            }
        }

        recommendationFetchJob?.cancel()
        recommendationFetchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250) // Let navigation entrance transition finish smoothly at locked 120 FPS
            val tmdbKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
            val recs = mutableListOf<RecommendationItem>()
            val revs = mutableListOf<CommunityReview>()
            val castMembers = mutableListOf<CastMember>()
            val trailerList = mutableListOf<MediaTrailer>()
            val compList = mutableListOf<NetworkProductionCompany>()
            var fetchedCollName: String? = null
            var fetchedCollParts: List<RecommendationItem> = emptyList()

            existingCached?.let {
                recs.addAll(it.recommendations)
                revs.addAll(it.reviews)
                castMembers.addAll(it.cast)
                trailerList.addAll(it.trailers)
                compList.addAll(it.companies)
                fetchedCollName = it.collectionName
                fetchedCollParts = it.collectionItems
                if (it.animeCharacters.isNotEmpty()) _animeCharacters.value = it.animeCharacters
                if (it.animeStaff.isNotEmpty()) _animeStaff.value = it.animeStaff
                if (it.animeRelations.isNotEmpty()) _animeRelations.value = it.animeRelations
                if (it.animeAiring != null) _animeAiring.value = it.animeAiring
                if (it.nextAiringEpisode != null) _nextAiringEpisode.value = it.nextAiringEpisode
            }

            fun persistAuxCache() {
                val aux = CachedDetailAux(
                    recommendations = _recommendations.value,
                    reviews = _reviews.value,
                    cast = _cast.value,
                    trailers = _trailers.value,
                    companies = _companies.value,
                    collectionName = _collectionName.value,
                    collectionItems = _collectionItems.value,
                    nextAiringEpisode = _nextAiringEpisode.value,
                    animeCharacters = _animeCharacters.value,
                    animeStaff = _animeStaff.value,
                    animeRelations = _animeRelations.value,
                    animeAiring = _animeAiring.value
                )
                DetailAuxMemoryCache.cache[cacheKey] = aux
                if (itemTmdbKey != null) DetailAuxMemoryCache.cache[itemTmdbKey] = aux
                if (idKey != null) DetailAuxMemoryCache.cache[idKey] = aux
            }

            if (item.tmdbId != null) {
                val type = if (item.mediaType == com.omnitrack.app.data.model.MediaType.MOVIE) "movie" else "tv"
                val client = OkHttpClient()

                // Consolidated 1-Shot TMDb append_to_response (~100ms total for ALL details)
                try {
                    val oneShotUrl = "https://api.themoviedb.org/3/$type/${item.tmdbId}?api_key=$tmdbKey&append_to_response=credits,videos,recommendations"
                    val oneShotReq = Request.Builder().url(oneShotUrl).get().build()
                    client.newCall(oneShotReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (body != null) {
                                val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject

                                // 1. Parse Credits (Cast)
                                val castArray = root["credits"]?.jsonObject?.get("cast")?.jsonArray
                                if (castArray != null) {
                                    val newCast = mutableListOf<CastMember>()
                                    for (elem in castArray.take(15)) {
                                        val obj = elem.jsonObject
                                        val name = obj["name"]?.jsonPrimitive?.content ?: continue
                                        val character = obj["character"]?.jsonPrimitive?.content
                                        val profilePath = obj["profile_path"]?.jsonPrimitive?.content
                                        val personId = obj["id"]?.jsonPrimitive?.content?.toLongOrNull()
                                        newCast.add(
                                            CastMember(
                                                name = name,
                                                character = character,
                                                avatarUrl = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" },
                                                tmdbPersonId = personId
                                            )
                                        )
                                    }
                                    if (newCast.isNotEmpty()) {
                                        castMembers.clear()
                                        castMembers.addAll(newCast)
                                        _cast.value = castMembers
                                    }
                                }

                                // 2. Parse Videos (Trailers & Teasers)
                                val videoResults = root["videos"]?.jsonObject?.get("results")?.jsonArray
                                if (videoResults != null) {
                                    val newTrailers = mutableListOf<MediaTrailer>()
                                    for (elem in videoResults) {
                                        val obj = elem.jsonObject
                                        val site = obj["site"]?.jsonPrimitive?.content ?: ""
                                        val key = obj["key"]?.jsonPrimitive?.content ?: continue
                                        val title = obj["name"]?.jsonPrimitive?.content ?: "Official Trailer"
                                        val typeStr = obj["type"]?.jsonPrimitive?.content ?: "Trailer"
                                        if (site.equals("YouTube", ignoreCase = true)) {
                                            newTrailers.add(MediaTrailer(title = title, youtubeKey = key, type = typeStr))
                                        }
                                    }
                                    if (newTrailers.isNotEmpty()) {
                                        trailerList.clear()
                                        trailerList.addAll(newTrailers)
                                        _trailers.value = trailerList
                                    }
                                }

                                // 3. Parse Recommendations
                                val recResults = root["recommendations"]?.jsonObject?.get("results")?.jsonArray
                                if (recResults != null) {
                                    val newRecs = mutableListOf<RecommendationItem>()
                                    for (elem in recResults.take(10)) {
                                        val obj = elem.jsonObject
                                        val title = obj["title"]?.jsonPrimitive?.content ?: obj["name"]?.jsonPrimitive?.content ?: continue
                                        val poster = obj["poster_path"]?.jsonPrimitive?.content ?: continue
                                        val rating = obj["vote_average"]?.jsonPrimitive?.content?.toFloatOrNull()
                                        val year = (obj["release_date"]?.jsonPrimitive?.content ?: obj["first_air_date"]?.jsonPrimitive?.content)?.take(4)
                                        val backdrop = obj["backdrop_path"]?.jsonPrimitive?.content
                                        val synopsis = obj["overview"]?.jsonPrimitive?.content
                                        val recId = obj["id"]?.jsonPrimitive?.content?.toLongOrNull()

                                        newRecs.add(
                                            RecommendationItem(
                                                title = title,
                                                coverUrl = "https://image.tmdb.org/t/p/w500$poster",
                                                releaseYear = year,
                                                rating = rating,
                                                mediaType = item.mediaType,
                                                synopsis = synopsis,
                                                tmdbId = recId,
                                                bannerUrl = backdrop?.let { "https://image.tmdb.org/t/p/original$it" }
                                            )
                                        )
                                    }
                                    if (newRecs.isNotEmpty()) {
                                        recs.clear()
                                        recs.addAll(newRecs)
                                        _recommendations.value = recs
                                    }
                                }

                                // 4. Parse Networks & Production Companies
                                val netList = mutableListOf<NetworkProductionCompany>()
                                val netArray = root["networks"]?.jsonArray
                                netArray?.forEach { nElem ->
                                    val nObj = nElem.jsonObject
                                    val nId = nObj["id"]?.jsonPrimitive?.content?.toLongOrNull()
                                    val nName = nObj["name"]?.jsonPrimitive?.content ?: return@forEach
                                    val nLogo = nObj["logo_path"]?.jsonPrimitive?.content?.let { "https://image.tmdb.org/t/p/w300$it" }
                                    netList.add(NetworkProductionCompany(id = nId, name = nName, logoUrl = nLogo, kind = "network"))
                                }
                                val prodList = mutableListOf<NetworkProductionCompany>()
                                val prodArray = root["production_companies"]?.jsonArray
                                prodArray?.forEach { pElem ->
                                    val pObj = pElem.jsonObject
                                    val pId = pObj["id"]?.jsonPrimitive?.content?.toLongOrNull()
                                    val pName = pObj["name"]?.jsonPrimitive?.content ?: return@forEach
                                    val pLogo = pObj["logo_path"]?.jsonPrimitive?.content?.let { "https://image.tmdb.org/t/p/w300$it" }
                                    prodList.add(NetworkProductionCompany(id = pId, name = pName, logoUrl = pLogo, kind = "company"))
                                }
                                compList.clear()
                                if (netList.isNotEmpty()) {
                                    compList.addAll(netList)
                                } else {
                                    val logosOnly = prodList.filter { !it.logoUrl.isNullOrBlank() }
                                    compList.addAll(if (logosOnly.isNotEmpty()) logosOnly else prodList)
                                }
                                if (compList.isNotEmpty()) {
                                    _companies.value = compList
                                }

                                // 5. Movie Franchise Collection (belongs_to_collection)
                                if (type == "movie") {
                                    val collObj = root["belongs_to_collection"]?.let {
                                        if (it is kotlinx.serialization.json.JsonObject) it else null
                                    }
                                    val collId = collObj?.get("id")?.jsonPrimitive?.content?.toLongOrNull()
                                    if (collId != null) {
                                        launch(Dispatchers.IO) {
                                            val collData = enrichmentRepository.fetchTmdbCollection(collId, tmdbKey)
                                            if (collData != null) {
                                                _collectionName.value = collData.name
                                                _collectionItems.value = collData.parts
                                                persistAuxCache()
                                            }
                                        }
                                    }
                                }

                                persistAuxCache()
                            }
                        }
                    }
                } catch (_: Exception) {}

                // Parallel Background Fetch: Trakt Community Reviews (does NOT block cast or recommendations!)
                launch(Dispatchers.IO) {
                    try {
                        val searchType = if (type == "movie") "movie" else "show"
                        val commentsEndpointType = if (type == "movie") "movies" else "shows"
                        val traktApiKey = com.omnitrack.app.data.sync.TrackerAccountVerifier.TRAKT_CLIENT_ID
                        var targetId: String? = null

                        if (item.tmdbId != null) {
                            val traktSearchUrl = "https://api.trakt.tv/search/tmdb/${item.tmdbId}?type=$searchType"
                            val traktSearchReq = Request.Builder()
                                .url(traktSearchUrl)
                                .header("Content-Type", "application/json")
                                .header("trakt-api-version", "2")
                                .header("trakt-api-key", traktApiKey)
                                .header("User-Agent", "OmniTrack/1.0.0 (Android)")
                                .get()
                                .build()

                            client.newCall(traktSearchReq).execute().use { searchResp ->
                                if (searchResp.isSuccessful) {
                                    val searchBody = searchResp.body?.string().orEmpty()
                                    val searchJson = Json { ignoreUnknownKeys = true }.parseToJsonElement(searchBody).jsonArray
                                    val firstItem = searchJson.firstOrNull()?.jsonObject
                                    val mediaObj = firstItem?.get(if (type == "movie") "movie" else "show")?.jsonObject
                                    val ids = mediaObj?.get("ids")?.jsonObject
                                    targetId = ids?.get("slug")?.jsonPrimitive?.content
                                        ?: ids?.get("trakt")?.jsonPrimitive?.content
                                        ?: ids?.get("imdb")?.jsonPrimitive?.content
                                }
                            }
                        }

                        if (targetId == null && item.title.isNotBlank()) {
                            val titleQueryUrl = "https://api.trakt.tv/search/$searchType?query=" + java.net.URLEncoder.encode(item.title, "UTF-8")
                            val titleSearchReq = Request.Builder()
                                .url(titleQueryUrl)
                                .header("Content-Type", "application/json")
                                .header("trakt-api-version", "2")
                                .header("trakt-api-key", traktApiKey)
                                .header("User-Agent", "OmniTrack/1.0.0 (Android)")
                                .get()
                                .build()

                            client.newCall(titleSearchReq).execute().use { titleResp ->
                                if (titleResp.isSuccessful) {
                                    val titleBody = titleResp.body?.string().orEmpty()
                                    val titleJson = Json { ignoreUnknownKeys = true }.parseToJsonElement(titleBody).jsonArray
                                    val firstItem = titleJson.firstOrNull()?.jsonObject
                                    val mediaObj = firstItem?.get(if (type == "movie") "movie" else "show")?.jsonObject
                                    val ids = mediaObj?.get("ids")?.jsonObject
                                    targetId = ids?.get("slug")?.jsonPrimitive?.content
                                        ?: ids?.get("trakt")?.jsonPrimitive?.content
                                        ?: ids?.get("imdb")?.jsonPrimitive?.content
                                }
                            }
                        }

                        if (targetId != null) {
                            val commentsUrl = "https://api.trakt.tv/$commentsEndpointType/$targetId/comments/likes?page=1&limit=100"
                            val commentsReq = Request.Builder()
                                .url(commentsUrl)
                                .header("Content-Type", "application/json")
                                .header("trakt-api-version", "2")
                                .header("trakt-api-key", traktApiKey)
                                .header("User-Agent", "OmniTrack/1.0.0 (Android)")
                                .get()
                                .build()

                            client.newCall(commentsReq).execute().use { commentsResp ->
                                if (commentsResp.isSuccessful) {
                                    val commentsBody = commentsResp.body?.string().orEmpty()
                                    val commentsArray = Json { ignoreUnknownKeys = true }.parseToJsonElement(commentsBody).jsonArray
                                    val traktRevs = mutableListOf<CommunityReview>()
                                    for (elem in commentsArray) {
                                        val commentObj = elem.jsonObject
                                        val commentText = commentObj["comment"]?.jsonPrimitive?.content ?: continue
                                        val userObj = commentObj["user"]?.jsonObject
                                        val authorName = userObj?.get("name")?.jsonPrimitive?.content
                                            ?: userObj?.get("username")?.jsonPrimitive?.content
                                            ?: "Trakt Member"
                                        val userRating = commentObj["user_rating"]?.jsonPrimitive?.content?.toFloatOrNull()
                                        val likes = commentObj["likes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                                        val spoiler = commentObj["spoiler"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                                        val reviewFlag = commentObj["review"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                                            ?: (commentText.length > 200)
                                        val date = commentObj["created_at"]?.jsonPrimitive?.content?.take(10)

                                        val sanitized = sanitizeCommunityComment(commentText)
                                        if (sanitized.isNotBlank()) {
                                            traktRevs.add(
                                                CommunityReview(
                                                    author = authorName,
                                                    username = userObj?.get("username")?.jsonPrimitive?.content,
                                                    source = "Trakt",
                                                    rating = userRating,
                                                    comment = sanitized,
                                                    date = date,
                                                    hasSpoiler = spoiler || commentText.contains("[spoiler]", ignoreCase = true),
                                                    isReview = reviewFlag,
                                                    likes = likes
                                                )
                                            )
                                        }
                                    }
                                    if (traktRevs.isNotEmpty()) {
                                        revs.clear()
                                        revs.addAll(traktRevs)
                                        _reviews.value = revs
                                        persistAuxCache()
                                    }
                                }
                            }
                        }

                        // Fallback to TMDb Community Reviews if Trakt is empty
                        if (revs.isEmpty()) {
                            val tmdbRevUrl = "https://api.themoviedb.org/3/$type/${item.tmdbId}/reviews?api_key=$tmdbKey"
                            val req = Request.Builder().url(tmdbRevUrl).get().build()
                            client.newCall(req).execute().use { response ->
                                if (response.isSuccessful) {
                                    val body = response.body?.string()
                                    if (body != null) {
                                        val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                        val results = root["results"]?.jsonArray
                                        if (results != null) {
                                            val tmdbRevs = mutableListOf<CommunityReview>()
                                            for (elem in results.take(15)) {
                                                val obj = elem.jsonObject
                                                val author = obj["author"]?.jsonPrimitive?.content ?: "Community Member"
                                                val content = obj["content"]?.jsonPrimitive?.content ?: continue
                                                val rating = obj["author_details"]?.jsonObject?.get("rating")?.jsonPrimitive?.content?.toFloatOrNull()
                                                val hasSpoiler = content.contains("[spoiler]", ignoreCase = true) || content.contains("spoiler alert", ignoreCase = true)
                                                tmdbRevs.add(
                                                    CommunityReview(
                                                        author = author,
                                                        username = obj["author_details"]?.jsonObject?.get("username")?.jsonPrimitive?.content,
                                                        source = "TMDb Community",
                                                        rating = rating,
                                                        comment = sanitizeCommunityComment(content),
                                                        hasSpoiler = hasSpoiler,
                                                        isReview = content.length > 150
                                                    )
                                                )
                                            }
                                            if (tmdbRevs.isNotEmpty()) {
                                                revs.clear()
                                                revs.addAll(tmdbRevs)
                                                _reviews.value = revs
                                                persistAuxCache()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Anime Enrichment: Next Airing, Characters & Seiyuu, Franchise Relations, Animation Studios
            var resolvedAniListId = item.anilistId
            if (item.mediaType == MediaType.ANIME || item.anilistId != null) {
                try {
                    val animeDetails = enrichmentRepository.fetchAnimeDetails(
                        anilistId = item.anilistId,
                        malId = item.malId,
                        title = item.title,
                        existingSeasonsCount = _realSeasons.value.size.coerceAtLeast(1)
                    )
                    if (animeDetails != null) {
                        _animeCharacters.value = animeDetails.characters
                        _animeStaff.value = animeDetails.staff
                        _animeRelations.value = animeDetails.relations
                        _animeAiring.value = animeDetails.nextAiring
                        if (animeDetails.animationStudios.isNotEmpty()) {
                            val studioEntries = animeDetails.animationStudios.map { studioName ->
                                NetworkProductionCompany(
                                    id = null,
                                    name = studioName,
                                    logoUrl = null,
                                    kind = "anime_studio"
                                )
                            }
                            compList.addAll(0, studioEntries)
                            _companies.value = compList.distinctBy { it.name }
                        }
                        if (animeDetails.anilistId != null) {
                            resolvedAniListId = animeDetails.anilistId
                            if (item.anilistId == null) {
                                if (item.id > 0) {
                                    viewModelScope.launch(Dispatchers.IO) {
                                        val currentItem = repository.getMediaById(item.id).firstOrNull()
                                        if (currentItem != null) {
                                            val currentSyn = currentItem.synopsis?.trim()
                                            val needsSynopsisRepair = currentSyn.isNullOrBlank() ||
                                                currentSyn.endsWith("(") ||
                                                currentSyn.endsWith("[") ||
                                                (animeDetails.synopsis != null && animeDetails.synopsis.length > (currentSyn?.length ?: 0))
                                            val updated = currentItem.copy(
                                                anilistId = currentItem.anilistId ?: animeDetails.anilistId,
                                                synopsis = if (needsSynopsisRepair && !animeDetails.synopsis.isNullOrBlank()) animeDetails.synopsis else currentItem.synopsis
                                            )
                                            if (updated != currentItem) {
                                                repository.updateMedia(updated)
                                            }
                                        }
                                    }
                                } else if (_previewItem.value != null) {
                                    val currentTop = _previewItem.value!!
                                    val enriched = currentTop.copy(
                                        anilistId = animeDetails.anilistId ?: currentTop.anilistId,
                                        synopsis = animeDetails.synopsis ?: currentTop.synopsis,
                                        bannerUrl = animeDetails.bannerUrl ?: currentTop.bannerUrl,
                                        coverUrl = if (currentTop.coverUrl.isBlank()) animeDetails.coverUrl ?: currentTop.coverUrl else currentTop.coverUrl,
                                        communityScore = animeDetails.averageScore ?: currentTop.communityScore,
                                        totalProgress = if (currentTop.totalProgress == null || currentTop.totalProgress == 0.0) {
                                            animeDetails.totalEpisodes?.toDouble() ?: animeDetails.totalChapters?.toDouble() ?: currentTop.totalProgress
                                        } else currentTop.totalProgress
                                    )
                                    _previewItem.value = enriched
                                    _previewStack.update { stack ->
                                        if (stack.isNotEmpty()) stack.dropLast(1) + enriched else listOf(enriched)
                                    }
                                }
                            }
                        }
                        persistAuxCache()
                    }
                } catch (_: Exception) {}
            }

            // Keyless AniList / Jikan Recommendations, Cast & Reviews for Anime / Manga
            if (resolvedAniListId != null || item.malId != null) {
                if (resolvedAniListId != null) {
                    try {
                        val query = """
                            query (${'$'}id: Int) {
                                Media(id: ${'$'}id) {
                                    recommendations(perPage: 10, sort: RATING_DESC) {
                                        nodes {
                                            mediaRecommendation {
                                                id
                                                idMal
                                                title {
                                                    userPreferred
                                                }
                                                coverImage {
                                                    extraLarge
                                                    large
                                                }
                                                bannerImage
                                                description
                                                averageScore
                                                startDate {
                                                    year
                                                }
                                            }
                                        }
                                    }
                                    characters(sort: ROLE, perPage: 12) {
                                        edges {
                                            role
                                            node {
                                                name { userPreferred }
                                                image { medium }
                                            }
                                        }
                                    }
                                    reviews(perPage: 6, sort: RATING_DESC) {
                                        nodes {
                                            user {
                                                name
                                            }
                                            summary
                                            body
                                            score
                                        }
                                    }
                                    trailer {
                                        id
                                        site
                                    }
                                }
                            }
                        """.trimIndent()

                        val escapedQuery = query.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        val bodyJson = """{"query":"$escapedQuery","variables":{"id":$resolvedAniListId}}"""

                        val req = Request.Builder()
                            .url("https://graphql.anilist.co")
                            .post(bodyJson.toRequestBody("application/json".toMediaType()))
                            .build()

                        val client = OkHttpClient()
                        client.newCall(req).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                if (body != null) {
                                    val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                    val mediaObj = root["data"]?.jsonObject?.get("Media")?.jsonObject

                                    // AniList Characters (Cast)
                                    val charEdges = mediaObj?.get("characters")?.jsonObject?.get("edges")?.jsonArray
                                    if (charEdges != null && castMembers.isEmpty()) {
                                        for (elem in charEdges) {
                                            val edgeObj = elem.jsonObject
                                            val node = edgeObj["node"]?.jsonObject
                                            val charName = node?.get("name")?.jsonObject?.get("userPreferred")?.jsonPrimitive?.content ?: continue
                                            val charImg = node["image"]?.jsonObject?.get("medium")?.jsonPrimitive?.content
                                            val role = edgeObj["role"]?.jsonPrimitive?.content
                                            castMembers.add(
                                                CastMember(
                                                    name = charName,
                                                    character = role,
                                                    avatarUrl = charImg
                                                )
                                            )
                                        }
                                    }

                                    // AniList Recs
                                    val recNodes = mediaObj?.get("recommendations")?.jsonObject?.get("nodes")?.jsonArray
                                    if (recNodes != null) {
                                        for (elem in recNodes) {
                                            val recMedia = elem.jsonObject["mediaRecommendation"]?.jsonObject ?: continue
                                            val title = recMedia["title"]?.jsonObject?.get("userPreferred")?.jsonPrimitive?.content ?: continue
                                            val cover = recMedia["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.content
                                                ?: recMedia["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content ?: continue
                                            val banner = recMedia["bannerImage"]?.jsonPrimitive?.content
                                            val synopsis = recMedia["description"]?.jsonPrimitive?.content?.replace(Regex("<[^>]*>"), "")
                                            val rating = recMedia["averageScore"]?.jsonPrimitive?.content?.toFloatOrNull()?.div(10f)
                                            val year = recMedia["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.content
                                            val alId = recMedia["id"]?.jsonPrimitive?.content?.toLongOrNull()
                                            val mId = recMedia["idMal"]?.jsonPrimitive?.content?.toLongOrNull()

                                            recs.add(
                                                RecommendationItem(
                                                    title = title,
                                                    coverUrl = cover,
                                                    bannerUrl = banner,
                                                    releaseYear = year,
                                                    rating = rating,
                                                    mediaType = item.mediaType,
                                                    synopsis = synopsis,
                                                    anilistId = alId,
                                                    malId = mId
                                                )
                                            )
                                        }
                                    }

                                    // AniList Trailer
                                    val trailerObj = mediaObj?.get("trailer")?.jsonObject
                                    val tId = trailerObj?.get("id")?.jsonPrimitive?.content
                                    val tSite = trailerObj?.get("site")?.jsonPrimitive?.content
                                    if (tSite.equals("youtube", ignoreCase = true) && !tId.isNullOrBlank()) {
                                        trailerList.add(MediaTrailer(title = "Official Anime Trailer", youtubeKey = tId, type = "Trailers"))
                                    }

                                    // AniList Reviews
                                    val reviewNodes = mediaObj?.get("reviews")?.jsonObject?.get("nodes")?.jsonArray
                                    if (reviewNodes != null) {
                                        for (elem in reviewNodes) {
                                            val revObj = elem.jsonObject
                                            val author = revObj["user"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "AniList User"
                                            val summary = revObj["summary"]?.jsonPrimitive?.content ?: ""
                                            val reviewBody = revObj["body"]?.jsonPrimitive?.content ?: ""
                                            val score = revObj["score"]?.jsonPrimitive?.content?.toFloatOrNull()?.div(10f)
                                            val text = if (summary.isNotBlank()) "$summary\n\n$reviewBody" else reviewBody
                                            if (text.isNotBlank()) {
                                                revs.add(
                                                    CommunityReview(
                                                        author = author,
                                                        username = author,
                                                        source = "AniList",
                                                        rating = score,
                                                        comment = sanitizeCommunityComment(text),
                                                        isReview = true
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            _recommendations.value = recs.distinctBy { it.title }
            _reviews.value = revs.distinctBy { it.author + it.source }
            _cast.value = castMembers.distinctBy { it.name }
            _trailers.value = trailerList.distinctBy { it.youtubeKey }
            if (compList.isNotEmpty()) {
                _companies.value = compList.distinctBy { it.name }
            }
            _collectionName.value = fetchedCollName
            _collectionItems.value = fetchedCollParts
            persistAuxCache()
        }
    }

    private fun launchSeriesFetch(item: MediaItemEntity) {
        seriesFetchJob?.cancel()
        seriesFetchJob = viewModelScope.launch(Dispatchers.IO) {
            val tmdbKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
            var resolvedTmdbId = item.tmdbId
            var resolvedImdbId = item.imdbId ?: SeriesContentMemoryCache.resolvedImdbIdCache[item.id]
            val isShowOrAnime = item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME
            if (resolvedTmdbId == null && isShowOrAnime) {
                resolvedTmdbId = SeriesContentMemoryCache.resolvedTmdbIdCache[item.id]
                if (resolvedTmdbId == null && item.title.isNotBlank()) {
                    val found = enrichmentRepository.searchTmdbForAnime(item.title, tmdbKey, item.nativeTitle)
                    if (found != null) {
                        resolvedTmdbId = found.first
                        SeriesContentMemoryCache.resolvedTmdbIdCache[item.id] = resolvedTmdbId
                        if (item.id > 0) {
                            val cur = repository.getMediaById(item.id).firstOrNull()
                            if (cur != null && cur.tmdbId == null) {
                                repository.updateMedia(cur.copy(tmdbId = resolvedTmdbId))
                            }
                        }
                    }
                }
            }

            if (resolvedTmdbId != null && isShowOrAnime) {
                try {
                    val tmdbId = resolvedTmdbId
                    if (resolvedImdbId == null) {
                        resolvedImdbId = enrichmentRepository.fetchTmdbImdbId(tmdbId, false, tmdbKey)
                        if (resolvedImdbId != null) {
                            SeriesContentMemoryCache.resolvedImdbIdCache[item.id] = resolvedImdbId
                            SeriesContentMemoryCache.resolvedImdbIdCache[tmdbId] = resolvedImdbId
                        }
                    }

                    // Instant 0ms cache retrieval
                    val cachedSeasons = SeriesContentMemoryCache.seasonsCache[tmdbId] ?: SeriesContentMemoryCache.seasonsCache[item.id]
                    val cachedGrouped = SeriesContentMemoryCache.episodesBySeasonCache[tmdbId] ?: SeriesContentMemoryCache.episodesBySeasonCache[item.id]
                    if (cachedSeasons != null && cachedSeasons.isNotEmpty()) {
                        val validCached = cachedSeasons.filter { it.seasonNumber > 0 || it.episodeCount > 0 }
                        _realSeasons.value = validCached
                        if (!cachedGrouped.isNullOrEmpty()) {
                            _episodesBySeason.value = cachedGrouped
                            val target = if (cachedGrouped.containsKey(_selectedSeasonNumber.value)) _selectedSeasonNumber.value
                            else validCached.firstOrNull { it.seasonNumber > 0 }?.seasonNumber ?: validCached.first().seasonNumber
                            _selectedSeasonNumber.value = target
                            _realEpisodes.value = cachedGrouped[target].orEmpty()
                            refreshNextAiringEpisode(item)
                            return@launch
                        }
                    }

                    val seasons = enrichmentRepository.fetchTvSeasons(tmdbId, tmdbKey, item.title, resolvedImdbId)
                    if (seasons.isNotEmpty()) {
                        val validSeasons = seasons.filter { it.seasonNumber > 0 || it.episodeCount > 0 }
                        val sortedSeasons = validSeasons.sortedWith(compareBy({ if (it.seasonNumber == 0) Int.MAX_VALUE else it.seasonNumber }))
                        SeriesContentMemoryCache.seasonsCache[tmdbId] = sortedSeasons
                        SeriesContentMemoryCache.seasonsCache[item.id] = sortedSeasons
                        _realSeasons.value = sortedSeasons

                        val currentSelected = _selectedSeasonNumber.value
                        val seasonToSelect = if (sortedSeasons.any { it.seasonNumber == currentSelected }) {
                            currentSelected
                        } else {
                            val defaultSeason = sortedSeasons.firstOrNull { it.seasonNumber > 0 } ?: sortedSeasons.first()
                            _selectedSeasonNumber.value = defaultSeason.seasonNumber
                            defaultSeason.seasonNumber
                        }

                        // 1. FAST PATH: Fetch & emit the SELECTED season's episodes IMMEDIATELY (0-lag open!)
                        val selectedCached = SeriesContentMemoryCache.episodesCache["$tmdbId:$seasonToSelect"]
                        val selectedEps = if (!selectedCached.isNullOrEmpty()) selectedCached
                        else enrichmentRepository.fetchTvSeasonEpisodes(tmdbId, seasonToSelect, tmdbKey, resolvedImdbId, item.title)
                        if (selectedEps.isNotEmpty()) {
                            SeriesContentMemoryCache.episodesCache["$tmdbId:$seasonToSelect"] = selectedEps
                            val cur = _episodesBySeason.value.toMutableMap()
                            cur[seasonToSelect] = selectedEps
                            _episodesBySeason.value = cur
                            _realEpisodes.value = selectedEps
                            refreshNextAiringEpisode(item)
                        }

                        // 2. BACKGROUND PATH: Progressively load remaining seasons without blocking UI!
                        launch(Dispatchers.IO) {
                            val remaining = sortedSeasons.filter { it.seasonNumber != seasonToSelect }
                            remaining.forEach { s ->
                                val cached = SeriesContentMemoryCache.episodesCache["$tmdbId:${s.seasonNumber}"]
                                val eps = if (!cached.isNullOrEmpty()) cached
                                else enrichmentRepository.fetchTvSeasonEpisodes(tmdbId, s.seasonNumber, tmdbKey, resolvedImdbId, item.title)
                                if (eps.isNotEmpty()) {
                                    SeriesContentMemoryCache.episodesCache["$tmdbId:${s.seasonNumber}"] = eps
                                    val cur = _episodesBySeason.value.toMutableMap()
                                    cur[s.seasonNumber] = eps
                                    _episodesBySeason.value = cur
                                }
                            }
                            SeriesContentMemoryCache.episodesBySeasonCache[tmdbId] = _episodesBySeason.value
                            SeriesContentMemoryCache.episodesBySeasonCache[item.id] = _episodesBySeason.value
                            refreshNextAiringEpisode(item)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun refreshNextAiringEpisode(item: MediaItemEntity? = null) {
        val allEpisodes = _episodesBySeason.value.values.flatten()
        if (allEpisodes.isEmpty()) return

        val todayIso = AirDateUtils.todayIsoDate()
        val futureEpisodes = allEpisodes.filter { ep ->
            val date = ep.airDate ?: return@filter false
            val days = AirDateUtils.daysUntilExplicitRelease(todayIso, date)
            days != null && days >= 0
        }.sortedWith(compareBy(
            { AirDateUtils.daysUntilExplicitRelease(todayIso, it.airDate) ?: Int.MAX_VALUE },
            { it.seasonNumber },
            { it.episodeNumber }
        ))

        val candidate = futureEpisodes.firstOrNull()
        if (candidate != null) {
            val daysUntil = AirDateUtils.daysUntilExplicitRelease(todayIso, candidate.airDate) ?: 0
            _nextAiringEpisode.value = NextAiringEpisode(
                seasonNumber = candidate.seasonNumber,
                episodeNumber = candidate.episodeNumber,
                title = candidate.title.takeIf { it.isNotBlank() },
                airDate = candidate.airDate,
                timeUntilAiringSec = daysUntil.toLong() * 86400L
            )
        }

        val targetItem = item ?: _previewItem.value
        if (targetItem != null && targetItem.mediaType == MediaType.ANIME) {
            viewModelScope.launch(Dispatchers.IO) {
                val aniListNext = enrichmentRepository.fetchAniListNextAiring(targetItem.title, candidate?.seasonNumber)
                if (aniListNext != null) {
                    _nextAiringEpisode.value = _nextAiringEpisode.value?.copy(
                        airingAtEpochSec = aniListNext.airingAtEpochSec,
                        timeUntilAiringSec = aniListNext.timeUntilAiringSec,
                        title = _nextAiringEpisode.value?.title ?: aniListNext.title
                    ) ?: aniListNext
                }
            }
        }
    }

    private var seasonEpisodesJob: kotlinx.coroutines.Job? = null

    fun selectSeason(seasonNumber: Int) {
        _selectedSeasonNumber.value = seasonNumber
        val inMemory = _episodesBySeason.value[seasonNumber]
        if (!inMemory.isNullOrEmpty()) {
            _realEpisodes.value = inMemory
            return
        }

        val previewItem = _previewItem.value
        val tmdbId = previewItem?.tmdbId
        if (tmdbId != null) {
            SeriesContentMemoryCache.episodesCache["$tmdbId:$seasonNumber"]?.let { cached ->
                _realEpisodes.value = cached
                _episodesBySeason.value = _episodesBySeason.value + (seasonNumber to cached)
                return
            }
        }
        seasonEpisodesJob?.cancel()
        seasonEpisodesJob = viewModelScope.launch(Dispatchers.IO) {
            val resolvedTmdbId = tmdbId
                ?: SeriesContentMemoryCache.resolvedTmdbIdCache[_mediaId.value]
                ?: repository.getMediaById(_mediaId.value).firstOrNull()?.tmdbId
                ?: return@launch
            val tmdbKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
            val currentTitle = previewItem?.title
                ?: repository.getMediaById(_mediaId.value).firstOrNull()?.title
            val resolvedImdbId = previewItem?.imdbId
                ?: repository.getMediaById(_mediaId.value).firstOrNull()?.imdbId
            val eps = enrichmentRepository.fetchTvSeasonEpisodes(resolvedTmdbId, seasonNumber, tmdbKey, resolvedImdbId, currentTitle)
            if (eps.isNotEmpty()) {
                SeriesContentMemoryCache.episodesCache["$resolvedTmdbId:$seasonNumber"] = eps
                _episodesBySeason.value = _episodesBySeason.value + (seasonNumber to eps)
                _realEpisodes.value = eps
            }
        }
    }

    fun toggleEpisodeWatched(episode: MediaEpisode) {
        val mediaId = _mediaId.value
        if (mediaId <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val currentProgress = repository.getEpisodeProgress(mediaId).firstOrNull().orEmpty()
            val isCurrentlyWatched = currentProgress.any { it.seasonNumber == episode.seasonNumber && it.episodeNumber == episode.episodeNumber && it.isWatched }
            if (!isCurrentlyWatched) {
                // When marking as watched: if user taps Ep 4, mark all prior episodes in this season (1..4) as watched
                val epsToMark = _realEpisodes.value.filter {
                    it.seasonNumber == episode.seasonNumber && it.episodeNumber <= episode.episodeNumber
                }
                val pairs = if (epsToMark.isNotEmpty()) {
                    epsToMark.map { it.seasonNumber to it.episodeNumber }
                } else {
                    (1..episode.episodeNumber).map { episode.seasonNumber to it }
                }
                repository.setMultipleEpisodesWatched(mediaId, pairs, true)
            } else {
                // When unwatching: if user unmarks Ep 3, unmark all episodes from 3 upwards in this season
                val epsToUnmark = _realEpisodes.value.filter {
                    it.seasonNumber == episode.seasonNumber && it.episodeNumber >= episode.episodeNumber
                }
                val pairs = if (epsToUnmark.isNotEmpty()) {
                    epsToUnmark.map { it.seasonNumber to it.episodeNumber }
                } else {
                    currentProgress.filter { it.seasonNumber == episode.seasonNumber && it.episodeNumber >= episode.episodeNumber }
                        .map { it.seasonNumber to it.episodeNumber }
                }
                repository.setMultipleEpisodesWatched(mediaId, pairs, false)
            }
            val updatedProgress = repository.getEpisodeProgress(mediaId).firstOrNull().orEmpty()
            val targetProgress = if (!isCurrentlyWatched) {
                episode.episodeNumber.toDouble()
            } else {
                (episode.episodeNumber - 1).toDouble().coerceAtLeast(0.0)
            }
            val item = repository.getMediaById(mediaId).firstOrNull()
            if (item != null) {
                val newStatus = if (item.totalProgress != null && targetProgress >= item.totalProgress) {
                    TrackingStatus.COMPLETED
                } else if (targetProgress > 0.0 && item.status == TrackingStatus.PLAN_TO_TRACK) {
                    TrackingStatus.IN_PROGRESS
                } else {
                    item.status
                }
                val updated = item.copy(currentProgress = targetProgress, status = newStatus)
                repository.updateMedia(updated)
                trackerSyncEngine?.enqueueProgressUpdate(updated, targetProgress)
                trackerSyncEngine?.drainQueue()
            }
        }
    }

    fun markPreviousEpisodesWatched(episode: MediaEpisode) {
        val mediaId = _mediaId.value
        if (mediaId <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val epsToMark = _realEpisodes.value.filter { it.episodeNumber <= episode.episodeNumber }
            val pairs = if (epsToMark.isNotEmpty()) {
                epsToMark.map { it.seasonNumber to it.episodeNumber }
            } else {
                (1..episode.episodeNumber).map { episode.seasonNumber to it }
            }
            repository.setMultipleEpisodesWatched(mediaId, pairs, true)
            val updatedProgress = repository.getEpisodeProgress(mediaId).firstOrNull().orEmpty()
            val targetProgress = episode.episodeNumber.toDouble()
            val item = repository.getMediaById(mediaId).firstOrNull()
            if (item != null) {
                val newStatus = if (item.totalProgress != null && targetProgress >= item.totalProgress) {
                    TrackingStatus.COMPLETED
                } else if (targetProgress > 0.0 && item.status == TrackingStatus.PLAN_TO_TRACK) {
                    TrackingStatus.IN_PROGRESS
                } else {
                    item.status
                }
                val updated = item.copy(currentProgress = targetProgress, status = newStatus)
                repository.updateMedia(updated)
                trackerSyncEngine?.enqueueProgressUpdate(updated, targetProgress)
                trackerSyncEngine?.drainQueue()
            }
        }
    }

    fun toggleSeasonWatched(seasonNumber: Int) {
        val mediaId = _mediaId.value
        if (mediaId <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val currentProgress = repository.getEpisodeProgress(mediaId).firstOrNull().orEmpty()
            val seasonEps = _realEpisodes.value.filter { it.seasonNumber == seasonNumber }
            val allWatched = seasonEps.isNotEmpty() && seasonEps.all { ep ->
                currentProgress.any { it.seasonNumber == ep.seasonNumber && it.episodeNumber == ep.episodeNumber && it.isWatched }
            }
            val pairs = seasonEps.map { it.seasonNumber to it.episodeNumber }
            repository.setMultipleEpisodesWatched(mediaId, pairs, !allWatched)
            val updatedProgress = repository.getEpisodeProgress(mediaId).firstOrNull().orEmpty()
            val watchedCount = updatedProgress.count { it.isWatched }
            val item = repository.getMediaById(mediaId).firstOrNull()
            if (item != null) {
                val targetProgress = watchedCount.toDouble()
                val newStatus = if (item.totalProgress != null && targetProgress >= item.totalProgress) {
                    TrackingStatus.COMPLETED
                } else if (targetProgress > 0.0 && item.status == TrackingStatus.PLAN_TO_TRACK) {
                    TrackingStatus.IN_PROGRESS
                } else {
                    item.status
                }
                val updated = item.copy(currentProgress = targetProgress, status = newStatus)
                repository.updateMedia(updated)
                trackerSyncEngine?.enqueueProgressUpdate(updated, targetProgress)
                trackerSyncEngine?.drainQueue()
            }
        }
    }

    fun navigateToPrevious(): Boolean {
        viewModelScope.launch {
            val currentId = _mediaId.value
            val siblings = _stableSiblingIds.value.ifEmpty {
                ActiveSiblingHolder.activeSiblingIds
            }
            if (siblings.isEmpty()) return@launch
            val currentIndex = siblings.indexOf(currentId)
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else siblings.size - 1
            val prevId = siblings[prevIndex]
            val prevItem = repository.getMediaById(prevId).firstOrNull() ?: return@launch
            _mediaId.value = prevId
            _previewItem.value = null
            updateSiblings(prevId)
            val cachedSeasons = SeriesContentMemoryCache.seasonsCache[prevItem.tmdbId ?: prevId] ?: SeriesContentMemoryCache.seasonsCache[prevId]
            val cachedGrouped = SeriesContentMemoryCache.episodesBySeasonCache[prevItem.tmdbId ?: prevId] ?: SeriesContentMemoryCache.episodesBySeasonCache[prevId]
            if (cachedSeasons != null) {
                _realSeasons.value = cachedSeasons
                _episodesBySeason.value = cachedGrouped.orEmpty()
                _selectedSeasonNumber.value = 1
                _realEpisodes.value = cachedGrouped?.get(1).orEmpty()
            } else {
                _realSeasons.value = emptyList()
                _episodesBySeason.value = emptyMap()
                _realEpisodes.value = emptyList()
                _selectedSeasonNumber.value = 1
            }
            val cachedAux = DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getCacheKey(prevItem)]
                ?: prevItem.tmdbId?.let { DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getTmdbCacheKey(if (prevItem.mediaType == MediaType.MOVIE) "movie" else "tv", it)] }
                ?: DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getCacheKey(prevId)]
            if (cachedAux != null) {
                _recommendations.value = cachedAux.recommendations
                _reviews.value = cachedAux.reviews
                _cast.value = cachedAux.cast
                _trailers.value = cachedAux.trailers
                _companies.value = cachedAux.companies
                _collectionName.value = cachedAux.collectionName
                _collectionItems.value = cachedAux.collectionItems
                _animeCharacters.value = cachedAux.animeCharacters
                _animeStaff.value = cachedAux.animeStaff
                _animeRelations.value = cachedAux.animeRelations
                _animeAiring.value = cachedAux.animeAiring
                _nextAiringEpisode.value = cachedAux.nextAiringEpisode
            }
            loadRecommendationsAndReviews(prevItem)
        }
        return true
    }

    fun navigateToNext(): Boolean {
        viewModelScope.launch {
            val currentId = _mediaId.value
            val siblings = _stableSiblingIds.value.ifEmpty {
                ActiveSiblingHolder.activeSiblingIds
            }
            if (siblings.isEmpty()) return@launch
            val currentIndex = siblings.indexOf(currentId)
            val nextIndex = if (currentIndex in 0 until siblings.size - 1) currentIndex + 1 else 0
            val nextId = siblings[nextIndex]
            val nextItem = repository.getMediaById(nextId).firstOrNull() ?: return@launch
            _mediaId.value = nextId
            _previewItem.value = null
            updateSiblings(nextId)
            val nextCachedSeasons = SeriesContentMemoryCache.seasonsCache[nextItem.tmdbId ?: nextId] ?: SeriesContentMemoryCache.seasonsCache[nextId]
            val nextCachedGrouped = SeriesContentMemoryCache.episodesBySeasonCache[nextItem.tmdbId ?: nextId] ?: SeriesContentMemoryCache.episodesBySeasonCache[nextId]
            if (nextCachedSeasons != null) {
                _realSeasons.value = nextCachedSeasons
                _episodesBySeason.value = nextCachedGrouped.orEmpty()
                _selectedSeasonNumber.value = 1
                _realEpisodes.value = nextCachedGrouped?.get(1).orEmpty()
            } else {
                _realSeasons.value = emptyList()
                _episodesBySeason.value = emptyMap()
                _realEpisodes.value = emptyList()
                _selectedSeasonNumber.value = 1
            }
            val nextCachedAux = DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getCacheKey(nextItem)]
                ?: nextItem.tmdbId?.let { DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getTmdbCacheKey(if (nextItem.mediaType == MediaType.MOVIE) "movie" else "tv", it)] }
                ?: DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getCacheKey(nextId)]
            if (nextCachedAux != null) {
                _recommendations.value = nextCachedAux.recommendations
                _reviews.value = nextCachedAux.reviews
                _cast.value = nextCachedAux.cast
                _trailers.value = nextCachedAux.trailers
                _companies.value = nextCachedAux.companies
                _collectionName.value = nextCachedAux.collectionName
                _collectionItems.value = nextCachedAux.collectionItems
                _animeCharacters.value = nextCachedAux.animeCharacters
                _animeStaff.value = nextCachedAux.animeStaff
                _animeRelations.value = nextCachedAux.animeRelations
                _animeAiring.value = nextCachedAux.animeAiring
                _nextAiringEpisode.value = nextCachedAux.nextAiringEpisode
            }
            loadRecommendationsAndReviews(nextItem)
        }
        return true
    }

    fun updateProgress(newProgress: Double) {
        viewModelScope.launch {
            val item = repository.getMediaById(_mediaId.value).firstOrNull() ?: uiState.value.item ?: return@launch
            val max = item.totalProgress ?: Double.MAX_VALUE
            val clamped = newProgress.coerceIn(0.0, max)
            val newStatus = if (item.totalProgress != null && clamped >= item.totalProgress) {
                TrackingStatus.COMPLETED
            } else if (clamped > 0.0 && item.status == TrackingStatus.PLAN_TO_TRACK) {
                TrackingStatus.IN_PROGRESS
            } else {
                item.status
            }
            val updated = item.copy(currentProgress = clamped, status = newStatus)
            repository.updateMedia(updated)
            trackerSyncEngine?.enqueueProgressUpdate(updated, clamped)
            trackerSyncEngine?.drainQueue()
        }
    }

    fun updatePlaytime(hours: Double) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.updateMedia(item.copy(playtimeHours = hours.coerceAtLeast(0.0)))
        }
    }

    fun togglePlatform(platform: String) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            val current = item.platforms?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toMutableSet()
                ?: item.platform?.let { mutableSetOf(it) }
                ?: mutableSetOf()
            if (current.contains(platform)) {
                current.remove(platform)
            } else {
                current.add(platform)
            }
            val joined = current.joinToString(",")
            repository.updateMedia(item.copy(platforms = joined, platform = current.firstOrNull()))
        }
    }

    fun updateCover(newCoverUrl: String) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.updateCover(item.id, newCoverUrl)
            repository.updateCustomCover(item.id, null)
        }
    }

    fun updateBanner(newBannerUrl: String) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.updateBanner(item.id, newBannerUrl)
            repository.updateCustomBanner(item.id, null)
        }
    }

    fun updateCustomCoverPath(path: String?, context: android.content.Context? = null) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            if (!item.customCoverPath.isNullOrBlank() && item.customCoverPath != path) {
                try { java.io.File(item.customCoverPath).delete() } catch (_: Exception) {}
            }
            repository.updateMedia(item.copy(customCoverPath = path))
        }
    }

    fun updateCustomBannerPath(path: String?, context: android.content.Context? = null) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            if (!item.customBannerPath.isNullOrBlank() && item.customBannerPath != path) {
                try { java.io.File(item.customBannerPath).delete() } catch (_: Exception) {}
            }
            repository.updateMedia(item.copy(customBannerPath = path))
        }
    }

    fun resetCover(context: android.content.Context? = null) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            if (!item.customCoverPath.isNullOrBlank()) {
                try { java.io.File(item.customCoverPath).delete() } catch (_: Exception) {}
            }
            repository.updateMedia(item.copy(customCoverPath = null))
        }
    }

    fun resetBanner(context: android.content.Context? = null) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            if (!item.customBannerPath.isNullOrBlank()) {
                try { java.io.File(item.customBannerPath).delete() } catch (_: Exception) {}
            }
            repository.updateMedia(item.copy(bannerUrl = null, customBannerPath = null))
        }
    }

    fun toggleDisableBanner() {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            val newRole = if (item.artworkRole == "DISABLE_BANNER") "DEFAULT" else "DISABLE_BANNER"
            repository.updateMedia(item.copy(artworkRole = newRole))
        }
    }

    fun updateArtworkRole(role: String) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.updateMedia(item.copy(artworkRole = role))
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

    fun updateMediaItem(item: MediaItemEntity) {
        viewModelScope.launch {
            repository.updateMedia(item)
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
            preferenceManager: PreferenceManager? = null,
            trackerSyncEngine: TrackerSyncEngine? = null
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DetailViewModel(repository, mediaId, enrichmentRepository, preferenceManager, trackerSyncEngine) as T
                }
            }
        }
    }
}

fun sanitizeCommunityComment(raw: String): String {
    return raw
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace(Regex("""(?i)\[/?spoiler\]"""), "")
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]*>"""), "")
        .replace(Regex("""(?m)^#{1,6}\s*"""), "")
        .replace(Regex("""\[(.*?)\]\(.*?\)"""), "$1")
        .replace(Regex("""(?s)\*{3}(.*?)\*{3}"""), "$1")
        .replace(Regex("""(?s)_{3}(.*?)_{3}"""), "$1")
        .replace(Regex("""(?s)\*{2}(.*?)\*{2}"""), "$1")
        .replace(Regex("""(?s)_{2}(.*?)_{2}"""), "$1")
        .replace(Regex("""(?s)\*(.*?)\*"""), "$1")
        .replace(Regex("""(?s)_(.*?)_"""), "$1")
        .replace(Regex("""(?s)~~(.*?)~~"""), "$1")
        .replace(Regex("""(?m)^[-*_]{3,}\s*$"""), "")
        .replace(Regex("""_{2,}"""), "")
        .replace(Regex("""\*{2,}"""), "")
        .replace(Regex("""(?<=\s|^)_|_(?=\s|$)"""), "")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}

internal object SeriesContentMemoryCache {
    val seasonsCache = java.util.concurrent.ConcurrentHashMap<Long, List<com.omnitrack.app.data.model.MediaSeason>>()
    val episodesCache = java.util.concurrent.ConcurrentHashMap<String, List<com.omnitrack.app.data.model.MediaEpisode>>()
    val episodesBySeasonCache = java.util.concurrent.ConcurrentHashMap<Long, Map<Int, List<com.omnitrack.app.data.model.MediaEpisode>>>()
    val resolvedTmdbIdCache = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    val resolvedImdbIdCache = java.util.concurrent.ConcurrentHashMap<Long, String>()
}

data class CachedDetailAux(
    val recommendations: List<RecommendationItem> = emptyList(),
    val reviews: List<CommunityReview> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val trailers: List<MediaTrailer> = emptyList(),
    val companies: List<NetworkProductionCompany> = emptyList(),
    val collectionName: String? = null,
    val collectionItems: List<RecommendationItem> = emptyList(),
    val nextAiringEpisode: NextAiringEpisode? = null,
    val animeCharacters: List<AnimeCharacterItem> = emptyList(),
    val animeStaff: List<AnimeStaffItem> = emptyList(),
    val animeRelations: List<AnimeRelationItem> = emptyList(),
    val animeAiring: AnimeAiringSchedule? = null
)

internal object DetailAuxMemoryCache {
    val cache = java.util.concurrent.ConcurrentHashMap<String, CachedDetailAux>()

    fun getCacheKey(item: MediaItemEntity): String {
        return "${item.mediaType}_${item.id}_${item.tmdbId}_${item.anilistId}_${item.malId}"
    }

    fun getCacheKey(mediaId: Long): String {
        return "id_$mediaId"
    }

    fun getTmdbCacheKey(type: String, tmdbId: Long): String {
        return "tmdb_${type}_$tmdbId"
    }
}
