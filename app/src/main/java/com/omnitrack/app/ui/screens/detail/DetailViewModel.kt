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
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.data.sync.TrackerSyncEngine
import com.omnitrack.app.data.sync.TrackerTokenManager
import com.omnitrack.app.ui.components.Episode
import com.omnitrack.app.ui.components.Season
import com.omnitrack.app.util.AirDateUtils
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

import com.omnitrack.app.data.model.AnimeTagItem
import com.omnitrack.app.data.preferences.PreferredTitleLanguage
import com.omnitrack.app.data.repository.DantotsuCommentsRepository
import com.omnitrack.app.ui.screens.detail.components.DiscussionComment
import com.omnitrack.app.ui.screens.detail.components.createSampleDiscussionComments
import com.omnitrack.app.ui.screens.detail.components.CastMember
import com.omnitrack.app.ui.screens.detail.components.CommunityReview
import com.omnitrack.app.ui.screens.detail.components.MediaTrailer
import com.omnitrack.app.ui.screens.detail.components.NetworkProductionCompany
import com.omnitrack.app.ui.screens.detail.components.RecommendationItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request

import com.omnitrack.app.data.model.ExternalLinkItem

data class NovelDetailMeta(
    val resolvedMode: Boolean? = null,
    val anilistScore: Float? = null,
    val malScore: Float? = null,
    val kitsuScore: Float? = null,
    val mangaUpdatesScore: Float? = null,
    val bangumiScore: Float? = null
)

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
    val sectionOrder: List<DetailSectionItem> = DetailsPageLayoutDefaults.defaultSections,
    val discussionComments: List<DiscussionComment> = emptyList(),
    val englishTitle: String? = null,
    val romajiTitle: String? = null,
    val anilistAuthToken: String? = null,
    val currentUsername: String? = null,
    val synonyms: List<String> = emptyList(),
    val tags: List<AnimeTagItem> = emptyList(),
    val popularity: Int? = null,
    val favourites: Int? = null,
    val durationMinutes: Int? = null,
    val preferredTitleLanguage: PreferredTitleLanguage = PreferredTitleLanguage.ROMAJI,
    val format: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val source: String? = null,
    val countryOfOrigin: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val externalLinks: List<ExternalLinkItem> = emptyList(),
    val heroClearLogoUrl: String? = null,
    val isFillerSkipEnabled: Boolean = false,
    val hasFillers: Boolean = false,
    val isMangaSeasonGrouped: Boolean = false,
    val isLightNovelMode: Boolean = false,
    val novelDetailMeta: NovelDetailMeta = NovelDetailMeta()
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
    val sectionOrder: List<DetailSectionItem> = DetailsPageLayoutDefaults.defaultSections,
    val discussionComments: List<DiscussionComment> = emptyList(),
    val englishTitle: String? = null,
    val romajiTitle: String? = null,
    val anilistAuthToken: String? = null,
    val currentUsername: String? = null,
    val synonyms: List<String> = emptyList(),
    val tags: List<AnimeTagItem> = emptyList(),
    val popularity: Int? = null,
    val favourites: Int? = null,
    val durationMinutes: Int? = null,
    val format: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val source: String? = null,
    val countryOfOrigin: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val externalLinks: List<ExternalLinkItem> = emptyList(),
    val heroClearLogoUrl: String? = null,
    val isFillerSkipEnabled: Boolean = false,
    val isMangaSeasonGrouped: Boolean = false,
    val novelDetailMeta: NovelDetailMeta = NovelDetailMeta()
)

class DetailViewModel(
    private val repository: MediaRepository,
    mediaId: Long,
    private val enrichmentRepository: EnrichmentRepository = EnrichmentRepository(),
    private val preferenceManager: PreferenceManager? = null,
    private val trackerSyncEngine: TrackerSyncEngine? = null,
    private val trackerTokenManager: TrackerTokenManager? = null
) : ViewModel() {

    private val _mediaId = MutableStateFlow(mediaId)
    private val _previewStack = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    private val _previewItem = MutableStateFlow<MediaItemEntity?>(null)
    private val _recommendations = MutableStateFlow<List<RecommendationItem>>(emptyList())
    private val _reviews = MutableStateFlow<List<CommunityReview>>(emptyList())
    private val _discussionComments = MutableStateFlow<List<DiscussionComment>>(emptyList())
    private val _isCommentsLoading = MutableStateFlow(false)
    val isCommentsLoading: StateFlow<Boolean> = _isCommentsLoading.asStateFlow()
    private val _englishTitle = MutableStateFlow<String?>(null)
    private val _romajiTitle = MutableStateFlow<String?>(null)
    private val _anilistAuthToken = MutableStateFlow<String?>(null)
    private val _currentUsername = MutableStateFlow<String?>(null)
    private val dantotsuCommentsRepository = DantotsuCommentsRepository()
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
    private val _synonyms = MutableStateFlow<List<String>>(emptyList())
    private val _tags = MutableStateFlow<List<AnimeTagItem>>(emptyList())
    private val _popularity = MutableStateFlow<Int?>(null)
    private val _favourites = MutableStateFlow<Int?>(null)
    private val _durationMinutes = MutableStateFlow<Int?>(null)
    private val _preferredTitleLanguage = MutableStateFlow(
        com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
    )
    private val _animeRelations = MutableStateFlow<List<AnimeRelationItem>>(emptyList())
    private val _animeAiring = MutableStateFlow<AnimeAiringSchedule?>(null)
    private val _sectionOrder = MutableStateFlow<List<DetailSectionItem>>(DetailsPageLayoutDefaults.defaultSections)
    private val _stableSiblingIds = MutableStateFlow<List<Long>>(emptyList())
    private val enrichedKeys = mutableSetOf<String>()
    private val _activeDiscussionAniListId = MutableStateFlow<Long?>(null)
    private val _activeDiscussionTag = MutableStateFlow<Int?>(null)
    private val _activeDiscussionSort = MutableStateFlow("newest")
    private val _seasonAniListIds = MutableStateFlow<Map<Int, Long>>(emptyMap())
    private val _seasonMalIds = MutableStateFlow<Map<Int, Long>>(emptyMap())
    private val _format = MutableStateFlow<String?>(null)
    private val _season = MutableStateFlow<String?>(null)
    private val _seasonYear = MutableStateFlow<Int?>(null)
    private val _source = MutableStateFlow<String?>(null)
    private val _countryOfOrigin = MutableStateFlow<String?>(null)
    private val _startDate = MutableStateFlow<String?>(null)
    private val _endDate = MutableStateFlow<String?>(null)
    private val _externalLinks = MutableStateFlow<List<ExternalLinkItem>>(emptyList())
    private val _heroClearLogoUrl = MutableStateFlow<String?>(null)
    private val _isFillerSkipEnabled = MutableStateFlow(false)
    private val _isMangaSeasonGrouped = MutableStateFlow(false)
    private val _novelDetailMeta = MutableStateFlow(NovelDetailMeta())
    private val _fillerInfoMap = MutableStateFlow<Map<Long, Map<Int, com.omnitrack.app.data.model.EpisodeFillerInfo>>>(emptyMap())
    private val allSeasonCommentsCache = java.util.concurrent.ConcurrentHashMap<Long, List<DiscussionComment>>()
    private var rootAniListId: Long? = null
    private var discussionFetchJob: kotlinx.coroutines.Job? = null
    private var enrichmentFetchJob: kotlinx.coroutines.Job? = null
    private var activeSeriesFetchKey: String? = null
    private val latestRoomItemCache = java.util.concurrent.ConcurrentHashMap<Long, MediaItemEntity>()
    private val latestEpisodeProgressCache = java.util.concurrent.ConcurrentHashMap<Long, List<com.omnitrack.app.data.db.entity.EpisodeProgressEntity>>()
    private val discussionCache = java.util.concurrent.ConcurrentHashMap<String, List<DiscussionComment>>()
    private val scrollOffsetStack = mutableListOf<Pair<Int, Int>>()

    fun saveScrollOffset(index: Int, offset: Int) {
        scrollOffsetStack.add(index to offset)
    }

    fun popScrollOffset(): Pair<Int, Int>? {
        return if (scrollOffsetStack.isNotEmpty()) scrollOffsetStack.removeAt(scrollOffsetStack.lastIndex) else null
    }

    fun selectDiscussionTag(tag: Int?) {
        viewModelScope.launch {
            val targetAniListId = _activeDiscussionAniListId.value
                ?: _previewItem.value?.anilistId
                ?: (if (_mediaId.value > 0) repository.getMediaById(_mediaId.value).firstOrNull()?.anilistId else null)
                ?: return@launch
            _activeDiscussionTag.value = tag
            loadDiscussionComments(targetAniListId, tag, _activeDiscussionSort.value)
        }
    }

    fun changeDiscussionSort(sort: String) {
        viewModelScope.launch {
            val anilistId = _activeDiscussionAniListId.value
                ?: _previewItem.value?.anilistId
                ?: (if (_mediaId.value > 0) repository.getMediaById(_mediaId.value).firstOrNull()?.anilistId else null)
                ?: return@launch
            loadDiscussionComments(anilistId, _activeDiscussionTag.value, sort)
        }
    }

    fun loadCommentReplies(commentId: String) {
        val cid = commentId.toLongOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentItem = _previewItem.value ?: (if (_mediaId.value > 0) repository.getMediaById(_mediaId.value).firstOrNull() else null)
                val isManga = currentItem?.mediaType == MediaType.MANGA || currentItem?.mediaType == MediaType.NOVEL
                val tagPrefix = if (isManga) "Ch" else "Ep"
                val anilistToken = trackerTokenManager?.tokens?.firstOrNull()?.anilistToken
                val replies = dantotsuCommentsRepository.getReplies(
                    parentCommentId = cid,
                    page = 1,
                    tagPrefix = tagPrefix,
                    anilistToken = anilistToken
                )
                if (replies.isNotEmpty()) {
                    _discussionComments.update { current ->
                        current.map { topComment ->
                            if (topComment.id == commentId) {
                                topComment.copy(replies = replies)
                            } else {
                                topComment
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadDiscussionComments(anilistId: Long, tag: Int? = null, sort: String = "newest") {
        if (anilistId <= 0L) {
            _discussionComments.value = emptyList()
            _isCommentsLoading.value = false
            return
        }
        if (rootAniListId == null) {
            rootAniListId = anilistId
        }
        _activeDiscussionAniListId.value = anilistId
        _activeDiscussionTag.value = tag
        _activeDiscussionSort.value = sort

        val cacheKey = "${anilistId}_${tag ?: -1}_$sort"
        val cached = discussionCache[cacheKey]
        if (cached != null && cached.isNotEmpty()) {
            _discussionComments.value = cached
            _isCommentsLoading.value = false
            return
        }

        val isMangaPreview = _previewItem.value?.let { it.mediaType == MediaType.MANGA || it.mediaType == MediaType.NOVEL } ?: false

        // If filtering by episode/chapter tag, extract already loaded matching comments from the season's "All" comments cache
        val localTagged = if (tag != null && tag > 0) {
            val allForSeason = allSeasonCommentsCache[anilistId].orEmpty()
            allForSeason.filter { comment ->
                val tagStr = comment.tag?.trim()
                val commentTagNum = tagStr?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
                val matchDirect = commentTagNum != null && commentTagNum == tag
                val matchString = tagStr != null && (
                    tagStr.equals("Ep $tag", ignoreCase = true) ||
                    tagStr.equals("Ch $tag", ignoreCase = true) ||
                    tagStr.endsWith(" $tag") ||
                    tagStr == "$tag"
                )
                val matchReply = comment.replies.any { r ->
                    val rNum = r.tag?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
                    rNum == tag || r.tag?.contains("$tag") == true
                }
                matchDirect || matchString || matchReply
            }
        } else {
            emptyList()
        }

        if (localTagged.isNotEmpty()) {
            _discussionComments.value = localTagged
        }

        _isCommentsLoading.value = true
        discussionFetchJob?.cancel()
        discussionFetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentItem = _previewItem.value
                    ?: (if (_mediaId.value > 0) repository.getMediaById(_mediaId.value).firstOrNull() else null)
                val isManga = currentItem?.let { it.mediaType == MediaType.MANGA || it.mediaType == MediaType.NOVEL } ?: isMangaPreview
                val tagPrefix = if (isManga) "Ch" else "Ep"

                val anilistToken = trackerTokenManager?.tokens?.firstOrNull()?.anilistToken
                if (!anilistToken.isNullOrBlank()) {
                    dantotsuCommentsRepository.authenticate(anilistToken)
                }

                val comments = dantotsuCommentsRepository.getComments(
                    anilistId = anilistId,
                    tag = tag,
                    tagPrefix = tagPrefix,
                    sort = sort,
                    anilistToken = anilistToken
                )

                if (tag == null && comments.isNotEmpty()) {
                    allSeasonCommentsCache[anilistId] = comments
                }

                val finalComments = if (comments.isNotEmpty()) {
                    if (localTagged.isNotEmpty()) {
                        (comments + localTagged).distinctBy { it.id }
                    } else {
                        comments
                    }
                } else if (localTagged.isNotEmpty()) {
                    localTagged
                } else {
                    emptyList()
                }

                if (_activeDiscussionAniListId.value == anilistId && _activeDiscussionTag.value == tag && _activeDiscussionSort.value == sort) {
                    if (finalComments.isNotEmpty()) {
                        discussionCache[cacheKey] = finalComments
                    }
                    _discussionComments.value = finalComments
                }
            } catch (_: Exception) {
                if (_activeDiscussionAniListId.value == anilistId && _activeDiscussionTag.value == tag) {
                    if (localTagged.isNotEmpty()) {
                        _discussionComments.value = localTagged
                    } else {
                        _discussionComments.value = emptyList()
                    }
                }
            } finally {
                _isCommentsLoading.value = false
            }
        }
    }

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
                viewModelScope.launch(Dispatchers.IO) {
                    val existing = repository.findExistingMedia(
                        anilistId = ephemeral.anilistId,
                        malId = ephemeral.malId,
                        tmdbId = ephemeral.tmdbId,
                        title = ephemeral.title,
                        mediaType = ephemeral.mediaType
                    )
                    if (existing != null && existing.id > 0L) {
                        withContext(Dispatchers.Main) {
                            EphemeralPreviewHolder.previewItem = null
                            _previewStack.value = emptyList()
                            _previewItem.value = null
                            _mediaId.value = existing.id
                        }
                    }
                }
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
            restoreSeriesFromMemoryCache(latestRoomItemCache[mediaId], mediaId)
        } else if (mediaId == -1L) {
            val ephemeral = EphemeralPreviewHolder.previewItem
            if (ephemeral != null) {
                restoreSeriesFromMemoryCache(ephemeral, -1L)
            }
        }

        // 0ms Detail Aux Synchronous Cache Hit (Cast, Trailers, Recommendations, Reviews, Companies, Collection):
        val cachedAux = if (mediaId > 0L) {
            DetailAuxMemoryCache.getCacheKey(mediaId)?.let { DetailAuxMemoryCache.cache[it] }
                ?: SeriesContentMemoryCache.resolvedTmdbIdCache[mediaId]?.let {
                    DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getTmdbCacheKey("tv", it)]
                        ?: DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getTmdbCacheKey("movie", it)]
                }
        } else if (mediaId == -1L) {
            val ephemeral = EphemeralPreviewHolder.previewItem
            if (ephemeral != null) {
                DetailAuxMemoryCache.getCacheKey(ephemeral)?.let { DetailAuxMemoryCache.cache[it] }
                    ?: ephemeral.tmdbId?.takeIf { ephemeral.mediaType != MediaType.ANIME || ephemeral.anilistId == null || ephemeral.anilistId <= 0L }?.let {
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
            if (cachedAux.englishTitle != null) _englishTitle.value = cachedAux.englishTitle
            if (cachedAux.romajiTitle != null) _romajiTitle.value = cachedAux.romajiTitle
        }

        if (trackerTokenManager != null) {
            viewModelScope.launch(Dispatchers.IO) {
                trackerTokenManager.tokens.collect { tokens ->
                    _anilistAuthToken.value = tokens.anilistToken
                    _currentUsername.value = dantotsuCommentsRepository.getCurrentUsername()
                    if (!tokens.anilistToken.isNullOrBlank()) {
                        dantotsuCommentsRepository.authenticate(tokens.anilistToken)
                        if (_currentUsername.value.isNullOrBlank()) {
                            _currentUsername.value = dantotsuCommentsRepository.getCurrentUsername()
                        }
                    }
                }
            }
        }

        if (preferenceManager != null) {
            viewModelScope.launch {
                preferenceManager.preferredTitleLanguage.collect { langPref ->
                    applyPreferredTitleLanguage(langPref)
                }
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
        val cacheKey = if (item.id > 0) "id_${item.id}" else "preview_${item.mediaType}_${item.anilistId ?: item.tmdbId ?: item.title}"
        if (!force && enrichedKeys.contains(cacheKey)) return
        enrichedKeys.add(cacheKey)

        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        val itemHasAllowedClearart = !item.clearartUrl.isNullOrBlank() &&
            (!item.clearartUrl.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE)
        if (itemHasAllowedClearart && item.clearartUrl != null) {
            EnrichmentRepository.putCachedClearart(item, item.clearartUrl)
        }

        val isVideoMedia = item.mediaType == MediaType.SHOW || item.mediaType == MediaType.MOVIE || item.mediaType == MediaType.ANIME
        val hasStaleRatings = isVideoMedia && (item.imdbScore == 6.7f || (item.imdbScore != null && item.traktScore == null))
        val hasStaleTvdbLogo = isVideoMedia &&
            item.clearartUrl?.contains("thetvdb.com") == true &&
            pref != com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
        val needsEnrichment = force || (
            (isVideoMedia && (item.clearartUrl.isNullOrBlank() || hasStaleTvdbLogo)) ||
            item.releaseYear == null ||
            item.contentRating == null ||
            (isVideoMedia && item.runtimeMinutes == null) ||
            (isVideoMedia && item.traktScore == null) ||
            hasStaleRatings
        )
        if (!needsEnrichment) return

        enrichmentFetchJob?.cancel()
        enrichmentFetchJob = viewModelScope.launch(Dispatchers.IO) {
            val fanartKey = preferenceManager?.fanartApiKey?.firstOrNull()?.ifBlank { null }
            val tmdbKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
            val mdbKey = preferenceManager?.mdblistApiKey?.firstOrNull()?.ifBlank { null }

            val enrichment = enrichmentRepository.enrich(
                item = item,
                fanartApiKey = fanartKey,
                tmdbApiKey = tmdbKey,
                mdblistApiKey = mdbKey,
                onClearartResolved = { earlyLogo, _ ->
                    val currentPref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
                    val isAllowedLogo = !earlyLogo.isNullOrBlank() && (!earlyLogo.contains("thetvdb.com") || currentPref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE)
                    if (isAllowedLogo) {
                        EnrichmentRepository.putCachedClearart(item, earlyLogo)
                    }
                    if (item.id > 0) {
                        if (_mediaId.value == item.id && isAllowedLogo) {
                            _heroClearLogoUrl.value = earlyLogo
                        }
                        val curr = repository.getMediaById(item.id).firstOrNull() ?: item
                        if (curr.clearartUrl != earlyLogo && isAllowedLogo) {
                            repository.updateMedia(curr.copy(clearartUrl = earlyLogo))
                        }
                    } else {
                        val currentPreview = _previewItem.value
                        val matchesPreview = currentPreview != null && (currentPreview.id == item.id || currentPreview.title == item.title || (currentPreview.anilistId != null && currentPreview.anilistId == item.anilistId))
                        if (matchesPreview && currentPreview != null) {
                            val updated = if (isAllowedLogo) currentPreview.copy(clearartUrl = earlyLogo) else currentPreview
                            _previewItem.value = updated
                            if (isAllowedLogo) {
                                _heroClearLogoUrl.value = earlyLogo
                            }
                            _previewStack.update { stack ->
                                if (stack.isNotEmpty()) stack.dropLast(1) + updated else listOf(updated)
                            }
                        }
                    }
                }
            )

            if (item.id > 0) {
                if (_mediaId.value != item.id) return@launch
                // Fetch the latest state from repository so we never overwrite user-changed cover or banner
                val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                val currentPref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
                val latestHasStaleTvdb = latest.clearartUrl?.contains("thetvdb.com") == true &&
                    currentPref != com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
                val nextClearartUrl = enrichment.clearartUrl ?: if (latestHasStaleTvdb) null else latest.clearartUrl
                val updated = latest.copy(
                    tmdbId = enrichment.resolvedTmdbId ?: latest.tmdbId,
                    imdbId = enrichment.resolvedImdbId ?: latest.imdbId,
                    clearartUrl = nextClearartUrl,
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
                val currentPreview = _previewItem.value
                val matchesPreview = currentPreview != null && (currentPreview.id == item.id || currentPreview.title == item.title || (currentPreview.anilistId != null && currentPreview.anilistId == item.anilistId))
                if (!matchesPreview) return@launch

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
                val isStillCurrent = if (item.id > 0) _mediaId.value == item.id else (_previewItem.value?.id == item.id || _previewItem.value?.title == item.title)
                if (isStillCurrent) {
                    _trailers.value = enrichment.videos
                }
            }
            if (enrichment.belongsToCollectionId != null && _collectionItems.value.isEmpty()) {
                val collData = enrichmentRepository.fetchTmdbCollection(enrichment.belongsToCollectionId, tmdbKey)
                val isStillCurrent = if (item.id > 0) _mediaId.value == item.id else (_previewItem.value?.id == item.id || _previewItem.value?.title == item.title)
                if (collData != null && isStillCurrent) {
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
            _sectionOrder,
            _discussionComments,
            _englishTitle,
            _romajiTitle,
            _anilistAuthToken,
            _currentUsername,
            _synonyms,
            _tags,
            _popularity,
            _favourites,
            _durationMinutes,
            _preferredTitleLanguage,
            _format,
            _season,
            _seasonYear,
            _source,
            _countryOfOrigin,
            _startDate,
            _endDate,
            _externalLinks,
            _heroClearLogoUrl,
            _isFillerSkipEnabled,
            _isMangaSeasonGrouped,
            _novelDetailMeta
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
            sectionOrder = args[19] as List<DetailSectionItem>,
            discussionComments = args[20] as List<DiscussionComment>,
            englishTitle = args[21] as? String,
            romajiTitle = args[22] as? String,
            anilistAuthToken = args[23] as? String,
            currentUsername = args[24] as? String,
            synonyms = args[25] as List<String>,
            tags = args[26] as List<AnimeTagItem>,
            popularity = args[27] as? Int,
            favourites = args[28] as? Int,
            durationMinutes = args[29] as? Int,
            format = args[31] as? String,
            season = args[32] as? String,
            seasonYear = args[33] as? Int,
            source = args[34] as? String,
            countryOfOrigin = args[35] as? String,
            startDate = args[36] as? String,
            endDate = args[37] as? String,
            externalLinks = (args[38] as? List<ExternalLinkItem>).orEmpty(),
            heroClearLogoUrl = args[39] as? String,
            isFillerSkipEnabled = (args[40] as? Boolean) ?: false,
            isMangaSeasonGrouped = (args[41] as? Boolean) ?: false,
            novelDetailMeta = (args[42] as? NovelDetailMeta) ?: NovelDetailMeta()
        )
    }.flatMapLatest { aux ->
        val hasFillers = aux.realEpisodes.any { it.isFiller } || (aux.episodesBySeason[aux.selectedSeasonNumber]?.any { it.isFiller } == true)
        val pref = _preferredTitleLanguage.value
        if (aux.previewItem != null) {
            val resolvedPreviewTitle = when (pref) {
                PreferredTitleLanguage.ENGLISH -> aux.englishTitle ?: aux.previewItem.title
                PreferredTitleLanguage.ROMAJI -> aux.romajiTitle ?: aux.previewItem.title
                PreferredTitleLanguage.NATIVE -> aux.previewItem.nativeTitle ?: aux.romajiTitle ?: aux.previewItem.title
            }
            val resolvedPreviewNative = if (resolvedPreviewTitle == aux.previewItem.nativeTitle) {
                aux.englishTitle ?: aux.romajiTitle ?: aux.previewItem.title
            } else {
                aux.previewItem.nativeTitle
            }
            val displayPreviewItem = aux.previewItem.copy(title = resolvedPreviewTitle, nativeTitle = resolvedPreviewNative)
            val progressFlow = if (aux.previewItem.id > 0L) {
                repository.getEpisodeProgress(aux.previewItem.id)
                    .onStart { emit(latestEpisodeProgressCache[aux.previewItem.id] ?: emptyList()) }
                    .distinctUntilChanged()
            } else {
                flowOf(emptyList())
            }
            progressFlow.map { progressList ->
                val watchedSet = progressList.filter { it.isWatched }
                    .map { it.seasonNumber to it.episodeNumber }.toSet()
                DetailUiState(
                    item = displayPreviewItem,
                    previousItem = null,
                    nextItem = null,
                    seasons = emptyList(),
                    realSeasons = aux.realSeasons,
                    realEpisodes = aux.realEpisodes,
                    selectedSeasonNumber = aux.selectedSeasonNumber,
                    episodesBySeason = aux.episodesBySeason,
                    watchedEpisodes = watchedSet,
                    recommendations = aux.recommendations,
                    reviews = aux.reviews,
                    cast = aux.cast,
                    trailers = aux.trailers,
                    companies = aux.companies,
                    collectionName = aux.collectionName,
                    collectionItems = aux.collectionItems,
                    isPreviewMode = aux.previewItem.id <= 0L,
                    isLoading = false,
                    hasPreviousEntry = false,
                    hasNextEntry = false,
                    nextAiringEpisode = aux.nextAiringEpisode,
                    animeCharacters = aux.animeCharacters,
                    animeStaff = aux.animeStaff,
                    animeRelations = aux.animeRelations,
                    animeAiring = aux.animeAiring,
                    sectionOrder = aux.sectionOrder,
                    discussionComments = aux.discussionComments,
                    englishTitle = aux.englishTitle,
                    romajiTitle = aux.romajiTitle,
                    anilistAuthToken = aux.anilistAuthToken,
                    currentUsername = aux.currentUsername,
                    synonyms = aux.synonyms,
                    tags = aux.tags,
                    popularity = aux.popularity,
                    favourites = aux.favourites,
                    durationMinutes = aux.durationMinutes,
                    preferredTitleLanguage = _preferredTitleLanguage.value,
                    format = aux.format,
                    season = aux.season,
                    seasonYear = aux.seasonYear,
                    source = aux.source,
                    countryOfOrigin = aux.countryOfOrigin,
                    startDate = aux.startDate,
                    endDate = aux.endDate,
                    externalLinks = aux.externalLinks,
                    heroClearLogoUrl = aux.heroClearLogoUrl,
                    isFillerSkipEnabled = aux.isFillerSkipEnabled,
                    hasFillers = hasFillers,
                    isMangaSeasonGrouped = aux.isMangaSeasonGrouped,
                    isLightNovelMode = if (displayPreviewItem.mediaType == MediaType.NOVEL) {
                        aux.novelDetailMeta.resolvedMode ?: (com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                            displayPreviewItem.platform,
                            displayPreviewItem.originCountry,
                            displayPreviewItem.anilistId,
                            displayPreviewItem.malId,
                            displayPreviewItem.totalProgress,
                            displayPreviewItem.title
                        ) == "LIGHT_NOVEL")
                    } else false,
                    novelDetailMeta = aux.novelDetailMeta
                )
            }
        } else {
            _mediaId.flatMapLatest { currentId ->
                repository.getMediaById(currentId)
                    .onStart {
                        val cached = aux.previewItem?.takeIf { it.id == currentId }
                            ?: latestRoomItemCache[currentId]
                        if (cached != null) emit(cached)
                    }
                    .distinctUntilChanged()
                    .flatMapLatest { currentItem ->
                        val effectiveItem = currentItem ?: aux.previewItem?.takeIf { it.id == currentId }
                        if (effectiveItem != null) {
                            if (effectiveItem.id > 0L) {
                                latestRoomItemCache[effectiveItem.id] = effectiveItem
                            }
                            maybeEnrich(effectiveItem)
                            val auxKey = "${effectiveItem.id}_${effectiveItem.anilistId}_${effectiveItem.tmdbId}_${effectiveItem.title}"
                            if (loadedAuxKeys.add(auxKey)) {
                                loadRecommendationsAndReviews(effectiveItem)
                            }
                            updateSiblings(effectiveItem.id)
                            repository.getEpisodeProgress(effectiveItem.id)
                                .onStart {
                                    emit(latestEpisodeProgressCache[effectiveItem.id] ?: emptyList())
                                }
                                .distinctUntilChanged()
                                .map { progressList ->
                                    if (effectiveItem.id > 0L) {
                                        latestEpisodeProgressCache[effectiveItem.id] = progressList
                                    }
                            val siblings = _stableSiblingIds.value.ifEmpty {
                                ActiveSiblingHolder.activeSiblingIds.ifEmpty { listOf(effectiveItem.id) }
                            }
                            val currentIndex = siblings.indexOf(effectiveItem.id)
                            val watchedSet = progressList.filter { it.isWatched }
                                .map { it.seasonNumber to it.episodeNumber }.toSet()
                            val resolvedTitle = when (pref) {
                                PreferredTitleLanguage.ENGLISH -> aux.englishTitle ?: effectiveItem.title
                                PreferredTitleLanguage.ROMAJI -> aux.romajiTitle ?: effectiveItem.title
                                PreferredTitleLanguage.NATIVE -> effectiveItem.nativeTitle ?: aux.romajiTitle ?: effectiveItem.title
                            }
                            val resolvedNative = if (resolvedTitle == effectiveItem.nativeTitle) {
                                aux.englishTitle ?: aux.romajiTitle ?: effectiveItem.title
                            } else {
                                effectiveItem.nativeTitle
                            }
                            val displayItem = effectiveItem.copy(title = resolvedTitle, nativeTitle = resolvedNative)
                            DetailUiState(
                                item = displayItem,
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
                                sectionOrder = aux.sectionOrder,
                                discussionComments = aux.discussionComments,
                                englishTitle = aux.englishTitle,
                                romajiTitle = aux.romajiTitle,
                                anilistAuthToken = aux.anilistAuthToken,
                                currentUsername = aux.currentUsername,
                                synonyms = aux.synonyms,
                                tags = aux.tags,
                                popularity = aux.popularity,
                                favourites = aux.favourites,
                                durationMinutes = aux.durationMinutes,
                                preferredTitleLanguage = _preferredTitleLanguage.value,
                                format = aux.format,
                                season = aux.season,
                                seasonYear = aux.seasonYear,
                                source = aux.source,
                                countryOfOrigin = aux.countryOfOrigin,
                                startDate = aux.startDate,
                                endDate = aux.endDate,
                                externalLinks = aux.externalLinks,
                                heroClearLogoUrl = aux.heroClearLogoUrl,
                                isFillerSkipEnabled = aux.isFillerSkipEnabled,
                                hasFillers = hasFillers,
                                isMangaSeasonGrouped = aux.isMangaSeasonGrouped,
                                isLightNovelMode = if (displayItem.mediaType == MediaType.NOVEL) {
                                    aux.novelDetailMeta.resolvedMode ?: (com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                                        displayItem.platform,
                                        displayItem.originCountry,
                                        displayItem.anilistId,
                                        displayItem.malId,
                                        displayItem.totalProgress,
                                        displayItem.title
                                    ) == "LIGHT_NOVEL")
                                } else false,
                                novelDetailMeta = aux.novelDetailMeta
                            )
                        }
                    } else {
                        flowOf(DetailUiState(isLoading = false))
                    }
                }
            }
        }
    }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = run {
            val pre = if (mediaId == -1L) EphemeralPreviewHolder.previewItem else null
            if (pre != null) DetailUiState(item = pre, isLoading = false) else DetailUiState()
        }
    )

    private val loadedAuxKeys = Collections.synchronizedSet(mutableSetOf<String>())

    private fun resetAuxFields() {
        loadedAuxKeys.clear()
        discussionFetchJob?.cancel()
        seriesFetchJob?.cancel()
        activeSeriesFetchKey = null
        rootAniListId = null
        _activeDiscussionAniListId.value = null
        _activeDiscussionTag.value = null
        _activeDiscussionSort.value = "newest"
        _seasonAniListIds.value = emptyMap()
        _seasonMalIds.value = emptyMap()
        allSeasonCommentsCache.clear()
        _collectionName.value = null
        _collectionItems.value = emptyList()
        _realSeasons.value = emptyList()
        _realEpisodes.value = emptyList()
        _episodesBySeason.value = emptyMap()
        _recommendations.value = emptyList()
        _reviews.value = emptyList()
        _discussionComments.value = emptyList()
        _englishTitle.value = null
        _romajiTitle.value = null
        _cast.value = emptyList()
        _trailers.value = emptyList()
        _companies.value = emptyList()
        _animeCharacters.value = emptyList()
        _animeStaff.value = emptyList()
        _animeRelations.value = emptyList()
        _animeAiring.value = null
        _nextAiringEpisode.value = null
        _synonyms.value = emptyList()
        _tags.value = emptyList()
        _popularity.value = null
        _favourites.value = null
        _durationMinutes.value = null
        _format.value = null
        _season.value = null
        _seasonYear.value = null
        _source.value = null
        _countryOfOrigin.value = null
        _startDate.value = null
        _endDate.value = null
        _externalLinks.value = emptyList()
        _fillerInfoMap.value = emptyMap()
        _heroClearLogoUrl.value = null
        _isMangaSeasonGrouped.value = false
        _novelDetailMeta.value = NovelDetailMeta()
    }

    fun isPreviewActive(): Boolean =
        _previewStack.value.isNotEmpty() || _previewItem.value != null || EphemeralPreviewHolder.previewItem != null

    fun openPreview(rec: RecommendationItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.findExistingMedia(
                anilistId = rec.anilistId,
                malId = rec.malId,
                tmdbId = rec.tmdbId,
                title = rec.title,
                mediaType = rec.mediaType
            )
            val entityToOpen = existing ?: MediaItemEntity(
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
            withContext(Dispatchers.Main) {
                openPreviewInternal(entityToOpen)
            }
        }
    }

    fun openPreview(previewEntity: MediaItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val target = if (previewEntity.id <= 0L) {
                repository.findExistingMedia(
                    anilistId = previewEntity.anilistId,
                    malId = previewEntity.malId,
                    tmdbId = previewEntity.tmdbId,
                    title = previewEntity.title,
                    mediaType = previewEntity.mediaType
                ) ?: previewEntity
            } else {
                previewEntity
            }
            withContext(Dispatchers.Main) {
                openPreviewInternal(target)
            }
        }
    }

    fun openRelationPreview(relation: AnimeRelationItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val format = relation.format.uppercase()
            val targetType = when {
                format == "MANGA" -> MediaType.MANGA
                format == "NOVEL" -> MediaType.NOVEL
                format == "MOVIE" -> MediaType.MOVIE
                else -> MediaType.ANIME
            }
            val existing = repository.findExistingMedia(
                anilistId = relation.id,
                title = relation.title,
                mediaType = targetType
            )
            val entityToOpen = existing ?: MediaItemEntity(
                id = -1L,
                title = relation.title,
                coverUrl = relation.coverUrl ?: "",
                bannerUrl = relation.bannerUrl,
                mediaType = targetType,
                anilistId = relation.id,
                releaseYear = relation.year?.toString(),
                status = TrackingStatus.PLAN_TO_TRACK
            )
            withContext(Dispatchers.Main) {
                openPreviewInternal(entityToOpen)
            }
        }
    }

    private fun openPreviewInternal(previewEntity: MediaItemEntity) {
        resetAuxFields()
        restoreSeriesFromMemoryCache(previewEntity, previewEntity.id)
        _previewStack.update { it + previewEntity }
        _previewItem.value = previewEntity
        loadRecommendationsAndReviews(previewEntity)
        maybeEnrich(previewEntity, force = true)
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
            restoreSeriesFromMemoryCache(newTop, newTop.id)
            loadRecommendationsAndReviews(newTop)
            maybeEnrich(newTop)
            return true
        }
        val currentId = _mediaId.value
        if (currentId > 0L) {
            _previewStack.value = emptyList()
            _previewItem.value = null
            EphemeralPreviewHolder.previewItem = null
            resetAuxFields()
            viewModelScope.launch(Dispatchers.IO) {
                val item = repository.getMediaById(currentId).firstOrNull()
                if (item != null) {
                    loadRecommendationsAndReviews(item)
                }
            }
            return true
        }
        // Root ephemeral preview: keep _previewItem intact during NavHost popExitTransition so screen never flashes black
        EphemeralPreviewHolder.previewItem = null
        _previewStack.value = emptyList()
        return false
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
        if (_activeDiscussionAniListId.value == null) {
            _discussionComments.value = emptyList()
        }
        if (item.anilistId != null && item.anilistId > 0 && _activeDiscussionAniListId.value == null) {
            loadDiscussionComments(item.anilistId)
        }
        val isShowOrAnime = item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME
        val isMangaSeries = item.mediaType == MediaType.MANGA
        val isNovelSeries = item.mediaType == MediaType.NOVEL

        if (isShowOrAnime || isMangaSeries || isNovelSeries) {
            val seriesKey = "${item.mediaType}_${item.id.takeIf { it > 0 } ?: item.anilistId ?: item.malId ?: item.tmdbId ?: "${item.platform ?: ""}_${item.title.lowercase().trim()}"}"
            val alreadyActiveOrLoaded = activeSeriesFetchKey == seriesKey &&
                (seriesFetchJob?.isActive == true || (_realSeasons.value.isNotEmpty() && _realEpisodes.value.isNotEmpty()))
            if (!alreadyActiveOrLoaded) {
                activeSeriesFetchKey = seriesKey
                when {
                    isMangaSeries -> launchMangaSeriesFetch(item)
                    isNovelSeries -> launchNovelSeriesFetch(item)
                    else -> launchSeriesFetch(item)
                }
            }
        }

        val cacheKey = DetailAuxMemoryCache.getCacheKey(item)
        val allowTmdbAuxKey = item.mediaType != MediaType.ANIME || item.anilistId == null || item.anilistId <= 0L
        val itemTmdbKey = if (allowTmdbAuxKey) {
            item.tmdbId?.let { DetailAuxMemoryCache.getTmdbCacheKey(if (item.mediaType == MediaType.MOVIE) "movie" else "tv", it) }
        } else null
        val idKey = if (item.id > 0L) DetailAuxMemoryCache.getCacheKey(item.id) else null

        // 1. Instant 0ms Memory Cache Hit
        val existingCached = (cacheKey?.let { DetailAuxMemoryCache.cache[it] })
            ?: (itemTmdbKey?.let { DetailAuxMemoryCache.cache[it] })
            ?: (idKey?.let { DetailAuxMemoryCache.cache[it] })

        // Reset or populate hero clear logo immediately so previous entry logo never sticks
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        val isAllowedLogo = existingCached?.heroClearLogoUrl?.takeIf {
            !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
        } ?: item.clearartUrl?.takeIf {
            !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
        }
        _heroClearLogoUrl.value = isAllowedLogo

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
            if (existingCached.englishTitle != null) _englishTitle.value = existingCached.englishTitle
            if (existingCached.romajiTitle != null) _romajiTitle.value = existingCached.romajiTitle
            if (existingCached.seasonAniListIds.isNotEmpty()) _seasonAniListIds.value = existingCached.seasonAniListIds
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
                if (it.englishTitle != null) _englishTitle.value = it.englishTitle
                if (it.romajiTitle != null) _romajiTitle.value = it.romajiTitle
                if (it.seasonAniListIds.isNotEmpty()) _seasonAniListIds.value = it.seasonAniListIds
                if (it.format != null) _format.value = it.format
                if (it.season != null) _season.value = it.season
                if (it.seasonYear != null) _seasonYear.value = it.seasonYear
                if (it.source != null) _source.value = it.source
                if (it.countryOfOrigin != null) _countryOfOrigin.value = it.countryOfOrigin
                if (it.startDate != null) _startDate.value = it.startDate
                if (it.endDate != null) _endDate.value = it.endDate
                if (it.externalLinks.isNotEmpty()) _externalLinks.value = it.externalLinks
                if (it.seasonMalIds.isNotEmpty()) _seasonMalIds.value = it.seasonMalIds
                if (it.heroClearLogoUrl != null) _heroClearLogoUrl.value = it.heroClearLogoUrl
                _isMangaSeasonGrouped.value = it.isMangaSeasonGrouped
                _novelDetailMeta.value = it.novelDetailMeta
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
                    animeAiring = _animeAiring.value,
                    englishTitle = _englishTitle.value,
                    romajiTitle = _romajiTitle.value,
                    seasonAniListIds = _seasonAniListIds.value,
                    format = _format.value,
                    season = _season.value,
                    seasonYear = _seasonYear.value,
                    source = _source.value,
                    countryOfOrigin = _countryOfOrigin.value,
                    startDate = _startDate.value,
                    endDate = _endDate.value,
                    externalLinks = _externalLinks.value,
                    seasonMalIds = _seasonMalIds.value,
                    heroClearLogoUrl = _heroClearLogoUrl.value,
                    isMangaSeasonGrouped = _isMangaSeasonGrouped.value,
                    novelDetailMeta = _novelDetailMeta.value
                )
                if (cacheKey != null) DetailAuxMemoryCache.cache[cacheKey] = aux
                if (itemTmdbKey != null) DetailAuxMemoryCache.cache[itemTmdbKey] = aux
                if (idKey != null) DetailAuxMemoryCache.cache[idKey] = aux
            }

            if (item.tmdbId != null) {
                val type = if (item.mediaType == com.omnitrack.app.data.model.MediaType.MOVIE) "movie" else "tv"
                val client = NetworkClient.sharedClient

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

                                val tmdbOverview = root["overview"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { !it.equals("null", ignoreCase = true) }
                                if (!tmdbOverview.isNullOrBlank() && item.id <= 0 && _previewItem.value != null) {
                                    val cur = _previewItem.value!!
                                    if (cur.synopsis.isNullOrBlank()) {
                                        val updated = cur.copy(synopsis = tmdbOverview)
                                        _previewItem.value = updated
                                        _previewStack.update { stack ->
                                            if (stack.isNotEmpty()) stack.dropLast(1) + updated else listOf(updated)
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

            // Anime, Manga & Novel Enrichment: Characters, Staff/Mangaka/Authors, Franchise Relations, Studios/Publishers
            var resolvedAniListId = item.anilistId
            if (item.mediaType == MediaType.ANIME || item.mediaType == MediaType.MANGA || item.mediaType == MediaType.NOVEL || item.anilistId != null) {
                try {
                    val animeDetails = enrichmentRepository.fetchAnimeDetails(
                        anilistId = item.anilistId,
                        malId = item.malId,
                        title = item.title,
                        existingSeasonsCount = _realSeasons.value.size.coerceAtLeast(1),
                        mediaType = item.mediaType
                    )
                    val isStillCurrentAnime = if (item.id > 0) _mediaId.value == item.id else (_previewItem.value?.id == item.id && _previewItem.value?.title == item.title)
                    if (animeDetails != null && isStillCurrentAnime) {
                        if (!animeDetails.englishTitle.isNullOrBlank()) _englishTitle.value = animeDetails.englishTitle
                        if (!animeDetails.romajiTitle.isNullOrBlank()) _romajiTitle.value = animeDetails.romajiTitle
                        if (animeDetails.characters.isNotEmpty()) _animeCharacters.value = animeDetails.characters
                        if (animeDetails.staff.isNotEmpty()) _animeStaff.value = animeDetails.staff
                        if (animeDetails.relations.isNotEmpty()) _animeRelations.value = animeDetails.relations
                        if (animeDetails.seasonAniListIds.isNotEmpty()) {
                            _seasonAniListIds.update { it + animeDetails.seasonAniListIds }
                        }
                        if (animeDetails.seasonMalIds.isNotEmpty()) {
                            _seasonMalIds.update { it + animeDetails.seasonMalIds }
                        }
                        _animeAiring.value = animeDetails.nextAiring
                        _synonyms.value = animeDetails.synonyms
                        if (animeDetails.tags.isNotEmpty()) _tags.value = animeDetails.tags
                        _popularity.value = animeDetails.popularity
                        _favourites.value = animeDetails.favourites
                        _durationMinutes.value = animeDetails.durationMinutes
                        if (!animeDetails.format.isNullOrBlank()) _format.value = animeDetails.format
                        if (!animeDetails.season.isNullOrBlank()) _season.value = animeDetails.season
                        if (animeDetails.seasonYear != null) _seasonYear.value = animeDetails.seasonYear
                        if (!animeDetails.source.isNullOrBlank()) _source.value = animeDetails.source
                        if (!animeDetails.countryOfOrigin.isNullOrBlank()) _countryOfOrigin.value = animeDetails.countryOfOrigin
                        if (!animeDetails.startDate.isNullOrBlank()) _startDate.value = animeDetails.startDate
                        if (!animeDetails.endDate.isNullOrBlank()) _endDate.value = animeDetails.endDate
                        if (animeDetails.externalLinks.isNotEmpty()) _externalLinks.value = animeDetails.externalLinks
                        if (animeDetails.averageScore != null) {
                            _novelDetailMeta.update { it.copy(anilistScore = animeDetails.averageScore) }
                        }

                        if (animeDetails.animationStudios.isNotEmpty()) {
                            val studioEntries = animeDetails.animationStudios.map { studioName ->
                                val matchedProd = compList.firstOrNull { it.name.equals(studioName, ignoreCase = true) }
                                NetworkProductionCompany(
                                    id = matchedProd?.id,
                                    name = studioName,
                                    logoUrl = matchedProd?.logoUrl,
                                    kind = "anime_studio"
                                )
                            }
                            val remaining = compList.filter { c ->
                                studioEntries.none { s -> s.name.equals(c.name, ignoreCase = true) }
                            }
                            compList.clear()
                            compList.addAll(studioEntries)
                            compList.addAll(remaining)
                            _companies.value = compList.distinctBy { it.name.lowercase().trim() }
                        }

                        if (animeDetails.anilistId != null) {
                            resolvedAniListId = animeDetails.anilistId
                            if (_heroClearLogoUrl.value.isNullOrBlank()) {
                                viewModelScope.launch(Dispatchers.IO) {
                                    val logo = enrichmentRepository.fetchAniZipClearlogo(animeDetails.anilistId, tmdbKey)
                                    val isCurrent = if (item.id > 0) _mediaId.value == item.id else (_previewItem.value?.id == item.id || _previewItem.value?.title == item.title)
                                    if (!logo.isNullOrBlank() && isCurrent) {
                                        _heroClearLogoUrl.value = logo
                                        persistAuxCache()
                                    }
                                }
                            }
                        }

                        val rootMalId = animeDetails.malId ?: item.malId ?: animeDetails.seasonMalIds[1]
                        val targetMalId = animeDetails.seasonMalIds[_selectedSeasonNumber.value] ?: rootMalId
                        if (rootMalId != null && rootMalId > 0) {
                            loadFillersForMalId(rootMalId, item.title)
                        }
                        if (targetMalId != null && targetMalId > 0 && targetMalId != rootMalId) {
                            loadFillersForMalId(targetMalId, item.title)
                        }
                        val alTotalProg = if (item.mediaType == MediaType.NOVEL) {
                            animeDetails.totalVolumes?.toDouble() ?: animeDetails.totalChapters?.toDouble()
                        } else {
                            animeDetails.totalEpisodes?.toDouble() ?: animeDetails.totalChapters?.toDouble()
                        }
                        if (item.id <= 0 && _previewItem.value != null) {
                            val currentTop = _previewItem.value!!
                            val currentSyn = currentTop.synopsis?.trim()
                            val needsSynopsisRepair = currentSyn.isNullOrBlank() ||
                                currentSyn.endsWith("(") ||
                                currentSyn.endsWith("[") ||
                                (animeDetails.synopsis != null && animeDetails.synopsis.length > (currentSyn?.length ?: 0))
                            val enriched = currentTop.copy(
                                anilistId = animeDetails.anilistId ?: currentTop.anilistId,
                                malId = animeDetails.malId ?: currentTop.malId,
                                originCountry = animeDetails.countryOfOrigin ?: currentTop.originCountry,
                                synopsis = if (needsSynopsisRepair && !animeDetails.synopsis.isNullOrBlank()) animeDetails.synopsis else currentTop.synopsis,
                                bannerUrl = animeDetails.bannerUrl ?: currentTop.bannerUrl,
                                coverUrl = if (currentTop.coverUrl.isBlank()) animeDetails.coverUrl ?: currentTop.coverUrl else currentTop.coverUrl,
                                communityScore = animeDetails.averageScore ?: currentTop.communityScore,
                                totalProgress = if (currentTop.totalProgress == null || currentTop.totalProgress == 0.0) {
                                    alTotalProg ?: currentTop.totalProgress
                                } else currentTop.totalProgress
                            )
                            _previewItem.value = enriched
                            _previewStack.update { stack ->
                                if (stack.isNotEmpty()) stack.dropLast(1) + enriched else listOf(enriched)
                            }
                            if (enriched.mediaType == MediaType.MANGA && (item.anilistId == null || _realSeasons.value.isEmpty())) {
                                launchMangaSeriesFetch(enriched)
                            }
                        } else if (item.id > 0) {
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
                                        malId = currentItem.malId ?: animeDetails.malId,
                                        originCountry = currentItem.originCountry ?: animeDetails.countryOfOrigin,
                                        totalProgress = if (currentItem.totalProgress == null || currentItem.totalProgress == 0.0) {
                                            alTotalProg ?: currentItem.totalProgress
                                        } else currentItem.totalProgress,
                                        synopsis = if (needsSynopsisRepair && !animeDetails.synopsis.isNullOrBlank()) animeDetails.synopsis else currentItem.synopsis
                                    )
                                    if (updated != currentItem) {
                                        repository.updateMedia(updated)
                                    }
                                    if (updated.mediaType == MediaType.MANGA && (currentItem.anilistId == null || _realSeasons.value.isEmpty())) {
                                        launchMangaSeriesFetch(updated)
                                    }
                                }
                            }
                        }
                        persistAuxCache()
                    }

                    // Web Novel Adaptation Bridge: When CN/KR/EN Web Novels lack a direct AniList NOVEL entry or banner/characters,
                    // harvest 16:9 hero banner, character portraits, original author, and Donghua/Manhwa adaptation relations!
                    val isStillCurrentBridge = if (item.id > 0) _mediaId.value == item.id else (_previewItem.value?.id == item.id && _previewItem.value?.title == item.title)
                    if (isStillCurrentBridge && item.mediaType == MediaType.NOVEL &&
                        (animeDetails == null || animeDetails.bannerUrl.isNullOrBlank() || animeDetails.characters.isEmpty())
                    ) {
                        val bridge = enrichmentRepository.fetchAniListAdaptationBridge(item.title, item.nativeTitle)
                        val stillActiveAfter = if (item.id > 0) _mediaId.value == item.id else (_previewItem.value?.id == item.id && _previewItem.value?.title == item.title)
                        if (bridge != null && stillActiveAfter) {
                            if (_animeCharacters.value.isEmpty() && bridge.characters.isNotEmpty()) {
                                _animeCharacters.value = bridge.characters
                            }
                            if (_animeStaff.value.isEmpty() && bridge.staff.isNotEmpty()) {
                                _animeStaff.value = bridge.staff
                            }
                            if (bridge.relations.isNotEmpty()) {
                                _animeRelations.update { existing -> (existing + bridge.relations).distinctBy { it.id } }
                            }
                            if (_tags.value.isEmpty() && bridge.tags.isNotEmpty()) {
                                _tags.value = bridge.tags
                            }
                            if (!bridge.bannerUrl.isNullOrBlank()) {
                                if (item.id <= 0 && _previewItem.value != null && _previewItem.value?.bannerUrl.isNullOrBlank()) {
                                    val updatedPrev = _previewItem.value!!.copy(bannerUrl = bridge.bannerUrl)
                                    _previewItem.value = updatedPrev
                                    _previewStack.update { s -> if (s.isNotEmpty()) s.dropLast(1) + updatedPrev else listOf(updatedPrev) }
                                } else if (item.id > 0) {
                                    viewModelScope.launch(Dispatchers.IO) {
                                        val latest = repository.getMediaById(item.id).firstOrNull()
                                        if (latest != null && latest.bannerUrl.isNullOrBlank()) {
                                            repository.updateMedia(latest.copy(bannerUrl = bridge.bannerUrl))
                                        }
                                    }
                                }
                            }
                            persistAuxCache()
                        }
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

                        val client = NetworkClient.sharedClient
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

            if (resolvedAniListId != null) {
                val currentSeason = _selectedSeasonNumber.value
                if (currentSeason > 1) {
                    reloadCommentsForSeason(currentSeason)
                } else {
                    if (_activeDiscussionAniListId.value != resolvedAniListId || _discussionComments.value.isEmpty()) {
                        loadDiscussionComments(resolvedAniListId)
                    }
                }
            } else {
                if (_activeDiscussionAniListId.value == null) {
                    _discussionComments.value = emptyList()
                }
            }

            persistAuxCache()
        }
    }

    private fun launchMangaSeriesFetch(item: MediaItemEntity) {
        seriesFetchJob?.cancel()
        seriesFetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = enrichmentRepository.fetchMangaVolumesAndChapters(
                    title = item.title,
                    anilistId = item.anilistId,
                    malId = item.malId,
                    mangadexId = item.mangadexId,
                    knownTotalChapters = item.totalProgress?.toInt(),
                    fallbackCoverUrl = item.coverUrl
                )
                val isStillCurrent = if (item.id > 0) _mediaId.value == item.id else (_previewItem.value?.id == item.id || _previewItem.value?.title == item.title)
                if (!isStillCurrent) return@launch

                if (content.seasons.isNotEmpty()) {
                    _isMangaSeasonGrouped.value = content.isSeasonGrouped
                    _realSeasons.value = content.seasons
                    _episodesBySeason.value = content.episodesBySeason

                    // Pick active volume/season containing user's current chapter progress
                    val currentCh = item.currentProgress.toInt()
                    val targetSeason = if (currentCh > 0) {
                        content.episodesBySeason.entries.firstOrNull { (_, eps) ->
                            eps.any { it.episodeNumber >= currentCh }
                        }?.key ?: content.seasons.first().seasonNumber
                    } else {
                        content.seasons.first().seasonNumber
                    }
                    _selectedSeasonNumber.value = targetSeason
                    _realEpisodes.value = content.episodesBySeason[targetSeason].orEmpty()

                    SeriesContentMemoryCache.putSeasons(item, null, content.seasons, item.anilistId)
                    SeriesContentMemoryCache.putEpisodesBySeason(item, null, content.episodesBySeason, item.anilistId)
                }

                if (content.publishers.isNotEmpty()) {
                    _companies.update { existing ->
                        val pubEntries = content.publishers.map { pubName ->
                            NetworkProductionCompany(id = null, name = pubName, logoUrl = null, kind = "publisher")
                        }
                        (existing + pubEntries).distinctBy { it.name.lowercase().trim() }
                    }
                }

                if (item.id > 0L) {
                    val latest = repository.getMediaById(item.id).firstOrNull()
                    if (latest != null) {
                        val nextMdId = latest.mangadexId ?: content.resolvedMangaDexId
                        val nextTotal = if (latest.totalProgress == null || latest.totalProgress == 0.0) {
                            content.totalChapters?.toDouble() ?: latest.totalProgress
                        } else latest.totalProgress
                        val updated = latest.copy(mangadexId = nextMdId, totalProgress = nextTotal)
                        if (updated != latest) {
                            repository.updateMedia(updated)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    private fun launchNovelSeriesFetch(item: MediaItemEntity) {
        seriesFetchJob?.cancel()
        seriesFetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = enrichmentRepository.fetchNovelVolumesAndChapters(
                    item = item,
                    knownTotalChapters = item.totalProgress?.toInt(),
                    knownTotalVolumes = null
                )
                val isStillCurrent = if (item.id > 0) _mediaId.value == item.id else (_previewItem.value?.id == item.id && _previewItem.value?.title == item.title)
                if (!isStillCurrent) return@launch

                _novelDetailMeta.update { curr ->
                    curr.copy(
                        resolvedMode = content.isLightNovel,
                        kitsuScore = content.kitsuDetails?.score ?: curr.kitsuScore,
                        mangaUpdatesScore = content.mangaUpdatesRating ?: curr.mangaUpdatesScore,
                        bangumiScore = content.bangumiScore ?: curr.bangumiScore
                    )
                }

                if (_format.value.isNullOrBlank()) {
                    _format.value = if (content.isLightNovel) "NOVEL" else "WEB_NOVEL"
                }

                if (content.seasons.isNotEmpty()) {
                    _isMangaSeasonGrouped.value = content.hasBookSplits
                    _realSeasons.value = content.seasons
                    _episodesBySeason.value = content.episodesBySeason

                    val currentProg = item.currentProgress.toInt()
                    val targetSeason = if (currentProg > 0) {
                        content.episodesBySeason.entries.firstOrNull { (_, eps) ->
                            eps.any { it.episodeNumber >= currentProg }
                        }?.key ?: content.seasons.first().seasonNumber
                    } else {
                        content.seasons.first().seasonNumber
                    }
                    _selectedSeasonNumber.value = targetSeason
                    _realEpisodes.value = content.episodesBySeason[targetSeason].orEmpty()

                    if (item.id > 0L) {
                        SeriesContentMemoryCache.putSeasons(item, null, content.seasons, item.anilistId)
                        SeriesContentMemoryCache.putEpisodesBySeason(item, null, content.episodesBySeason, item.anilistId)
                    }
                }

                if (content.publishers.isNotEmpty()) {
                    _companies.update { existing ->
                        val pubEntries = content.publishers.map { pubName ->
                            NetworkProductionCompany(id = null, name = pubName, logoUrl = null, kind = "publisher")
                        }
                        (existing + pubEntries).distinctBy { it.name.lowercase().trim() }
                    }
                }

                if (_animeStaff.value.isEmpty() && content.ranobeDetails?.staff?.isNotEmpty() == true) {
                    _animeStaff.value = content.ranobeDetails.staff
                }

                if (_tags.value.isEmpty() && content.kitsuDetails?.categories?.isNotEmpty() == true) {
                    _tags.value = content.kitsuDetails.categories.take(16).mapIndexed { i, cat ->
                        AnimeTagItem(id = i.toLong(), name = cat)
                    }
                }

                val targetPlatform = if (content.isLightNovel) "LIGHT_NOVEL" else "WEB_NOVEL"
                val repairSynopsis = content.ranobeDetails?.synopsis ?: content.kitsuDetails?.synopsis
                val resolvedMalId = item.malId ?: content.ranobeDetails?.malId
                val resolvedAnilistId = item.anilistId ?: content.ranobeDetails?.anilistId

                if (item.id > 0L) {
                    val latest = repository.getMediaById(item.id).firstOrNull()
                    if (latest != null) {
                        val nextTotal = when {
                            latest.totalProgress == null || latest.totalProgress == 0.0 -> content.totalCount?.toDouble()
                            !content.isLightNovel && (content.totalCount ?: 0) > latest.totalProgress.toInt() -> content.totalCount?.toDouble()
                            else -> latest.totalProgress
                        }
                        val nextSyn = if (latest.synopsis.isNullOrBlank() && !repairSynopsis.isNullOrBlank()) {
                            repairSynopsis
                        } else latest.synopsis
                        val nextBanner = latest.bannerUrl ?: content.kitsuDetails?.bannerUrl
                        val updated = latest.copy(
                            platform = targetPlatform,
                            originCountry = latest.originCountry ?: content.originCountry,
                            anilistId = latest.anilistId ?: resolvedAnilistId,
                            malId = latest.malId ?: resolvedMalId,
                            totalProgress = nextTotal,
                            synopsis = nextSyn,
                            bannerUrl = nextBanner
                        )
                        if (updated != latest) {
                            repository.updateMedia(updated)
                        }
                    }
                } else if (_previewItem.value != null) {
                    val prev = _previewItem.value!!
                    val updatedPrev = prev.copy(
                        platform = targetPlatform,
                        originCountry = prev.originCountry ?: content.originCountry,
                        anilistId = prev.anilistId ?: resolvedAnilistId,
                        malId = prev.malId ?: resolvedMalId,
                        totalProgress = if (prev.totalProgress == null || prev.totalProgress == 0.0) {
                            content.totalCount?.toDouble() ?: prev.totalProgress
                        } else prev.totalProgress,
                        synopsis = if (prev.synopsis.isNullOrBlank() && !repairSynopsis.isNullOrBlank()) repairSynopsis else prev.synopsis,
                        bannerUrl = prev.bannerUrl ?: content.kitsuDetails?.bannerUrl
                    )
                    _previewItem.value = updatedPrev
                    _previewStack.update { s -> if (s.isNotEmpty()) s.dropLast(1) + updatedPrev else listOf(updatedPrev) }
                }

                // Fetch MAL/Jikan LN reviews or Kitsu Web Novel reactions if reviews list is still empty
                if (_reviews.value.isEmpty()) {
                    val novelRevs = mutableListOf<CommunityReview>()
                    val kitsuId = content.kitsuDetails?.kitsuId
                    if (!kitsuId.isNullOrBlank()) {
                        enrichmentRepository.fetchKitsuNovelReactions(kitsuId).forEach { r ->
                            novelRevs.add(
                                CommunityReview(
                                    author = r.author,
                                    username = r.author,
                                    source = r.source,
                                    rating = r.rating,
                                    comment = sanitizeCommunityComment(r.comment),
                                    hasSpoiler = r.isSpoiler,
                                    isReview = r.comment.length > 150
                                )
                            )
                        }
                    }
                    if (novelRevs.isEmpty() && resolvedMalId != null && resolvedMalId > 0) {
                        enrichmentRepository.fetchJikanMangaReviews(resolvedMalId).forEach { r ->
                            novelRevs.add(
                                CommunityReview(
                                    author = r.author,
                                    username = r.author,
                                    source = r.source,
                                    rating = r.rating,
                                    comment = sanitizeCommunityComment(r.comment),
                                    hasSpoiler = r.isSpoiler,
                                    isReview = true
                                )
                            )
                        }
                    }
                    if (novelRevs.isNotEmpty() && (if (item.id > 0) _mediaId.value == item.id else (_previewItem.value?.id == item.id && _previewItem.value?.title == item.title))) {
                        _reviews.value = novelRevs
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    private fun launchSeriesFetch(item: MediaItemEntity) {
        seriesFetchJob?.cancel()
        seriesFetchJob = viewModelScope.launch(Dispatchers.IO) {
            val tmdbKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
            var resolvedTmdbId = item.tmdbId
            var resolvedImdbId = item.imdbId ?: (if (item.id > 0L) SeriesContentMemoryCache.resolvedImdbIdCache[item.id] else null)
            val isShowOrAnime = item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME

            var effectiveAnilistId = item.anilistId
            if (effectiveAnilistId == null && item.mediaType == MediaType.ANIME && item.title.isNotBlank()) {
                effectiveAnilistId = enrichmentRepository.searchAniListForAnime(item.title, item.nativeTitle)
            }

            if (resolvedTmdbId == null && isShowOrAnime) {
                if (item.id > 0L) {
                    resolvedTmdbId = SeriesContentMemoryCache.resolvedTmdbIdCache[item.id]
                }
                if (resolvedTmdbId == null && effectiveAnilistId != null && effectiveAnilistId > 0) {
                    resolvedTmdbId = enrichmentRepository.fetchAniZipTmdbId(effectiveAnilistId)
                    if (resolvedTmdbId != null && item.id > 0L) {
                        SeriesContentMemoryCache.resolvedTmdbIdCache[item.id] = resolvedTmdbId
                    }
                }
                if (resolvedTmdbId == null && item.title.isNotBlank()) {
                    val found = enrichmentRepository.searchTmdbForAnime(item.title, tmdbKey, item.nativeTitle)
                    if (found != null) {
                        resolvedTmdbId = found.first
                        if (item.id > 0L) {
                            SeriesContentMemoryCache.resolvedTmdbIdCache[item.id] = resolvedTmdbId
                            val cur = repository.getMediaById(item.id).firstOrNull()
                            if (cur != null && cur.tmdbId == null) {
                                repository.updateMedia(cur.copy(tmdbId = resolvedTmdbId))
                            }
                        } else if (_previewItem.value != null && _previewItem.value?.tmdbId == null) {
                            val cur = _previewItem.value!!
                            val updated = cur.copy(tmdbId = resolvedTmdbId)
                            _previewItem.value = updated
                            _previewStack.update { stack ->
                                if (stack.isNotEmpty()) stack.dropLast(1) + updated else listOf(updated)
                            }
                        }
                    }
                }
            }

            if (resolvedTmdbId != null && isShowOrAnime) {
                try {
                    val tmdbId = resolvedTmdbId
                    if (resolvedImdbId == null && effectiveAnilistId != null && effectiveAnilistId > 0) {
                        resolvedImdbId = enrichmentRepository.fetchAniZipImdbId(effectiveAnilistId)
                    }
                    if (resolvedImdbId == null) {
                        resolvedImdbId = enrichmentRepository.fetchTmdbImdbId(tmdbId, false, tmdbKey)
                    }
                    if (resolvedImdbId != null) {
                        if (item.id > 0L) {
                            SeriesContentMemoryCache.resolvedImdbIdCache[item.id] = resolvedImdbId
                        }
                        SeriesContentMemoryCache.resolvedImdbIdCache[tmdbId] = resolvedImdbId
                    }

                    // Instant 0ms cache retrieval
                    val cachedSeasons = SeriesContentMemoryCache.findSeasons(item, tmdbId, item.id, effectiveAnilistId)
                    val cachedGrouped = SeriesContentMemoryCache.findEpisodesBySeason(item, tmdbId, item.id, effectiveAnilistId)
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

                    val targetSeasonToPreload = if (_selectedSeasonNumber.value > 0) _selectedSeasonNumber.value else 1

                    // Parallel Fast Path (Chimanew / EaZy Nuvio+ Architecture):
                    // Concurrently resolve seasons and prefetch target season episodes over multiplexed HTTP/2 sockets
                    val (seasons, preloadedEps) = coroutineScope {
                        val seasonsDeferred = async(Dispatchers.IO) {
                            enrichmentRepository.fetchTvSeasons(tmdbId, tmdbKey, item.title, resolvedImdbId)
                        }
                        val epsDeferred = async(Dispatchers.IO) {
                            val cached = SeriesContentMemoryCache.findSeasonEpisodes(item, tmdbId, targetSeasonToPreload, item.id, effectiveAnilistId)
                            if (!cached.isNullOrEmpty()) cached
                            else enrichmentRepository.fetchTvSeasonEpisodes(tmdbId, targetSeasonToPreload, tmdbKey, resolvedImdbId, item.title)
                        }
                        seasonsDeferred.await() to epsDeferred.await()
                    }

                    currentCoroutineContext().ensureActive()
                    if (seasons.isNotEmpty()) {
                        val validSeasons = seasons.filter { it.seasonNumber > 0 || it.episodeCount > 0 }
                        val sortedSeasons = validSeasons.sortedWith(compareBy({ if (it.seasonNumber == 0) Int.MAX_VALUE else it.seasonNumber }))
                        SeriesContentMemoryCache.putSeasons(item, tmdbId, sortedSeasons, effectiveAnilistId)
                        currentCoroutineContext().ensureActive()
                        _realSeasons.value = sortedSeasons

                        val currentSelected = _selectedSeasonNumber.value
                        val seasonToSelect = if (sortedSeasons.any { it.seasonNumber == currentSelected }) {
                            currentSelected
                        } else {
                            val defaultSeason = sortedSeasons.firstOrNull { it.seasonNumber > 0 } ?: sortedSeasons.first()
                            _selectedSeasonNumber.value = defaultSeason.seasonNumber
                            defaultSeason.seasonNumber
                        }

                        // Use the concurrently preloaded episodes if target matches (0ms delay!)
                        var selectedEps = if (seasonToSelect == targetSeasonToPreload && preloadedEps.isNotEmpty()) {
                            preloadedEps
                        } else {
                            val selectedCached = SeriesContentMemoryCache.findSeasonEpisodes(item, tmdbId, seasonToSelect, item.id, effectiveAnilistId)
                            if (!selectedCached.isNullOrEmpty()) selectedCached
                            else enrichmentRepository.fetchTvSeasonEpisodes(tmdbId, seasonToSelect, tmdbKey, resolvedImdbId, item.title)
                        }

                        // Bleach / Anime large season fallback: if TMDb episodes are empty, query AniZip
                        if (selectedEps.isEmpty() && effectiveAnilistId != null && effectiveAnilistId > 0) {
                            val aniZipAll = enrichmentRepository.fetchAniZipEpisodes(effectiveAnilistId)
                            val aniZipBySeason = aniZipAll.groupBy { it.seasonNumber }
                            val matched = aniZipBySeason[seasonToSelect] ?: aniZipAll
                            if (matched.isNotEmpty()) {
                                selectedEps = matched
                            }
                        }

                        currentCoroutineContext().ensureActive()
                        if (selectedEps.isNotEmpty()) {
                            SeriesContentMemoryCache.putSeasonEpisodes(item, tmdbId, seasonToSelect, selectedEps, effectiveAnilistId)
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
                                currentCoroutineContext().ensureActive()
                                val cached = SeriesContentMemoryCache.findSeasonEpisodes(item, tmdbId, s.seasonNumber, item.id, effectiveAnilistId)
                                val eps = if (!cached.isNullOrEmpty()) cached
                                else enrichmentRepository.fetchTvSeasonEpisodes(tmdbId, s.seasonNumber, tmdbKey, resolvedImdbId, item.title)
                                if (eps.isNotEmpty()) {
                                    currentCoroutineContext().ensureActive()
                                    SeriesContentMemoryCache.putSeasonEpisodes(item, tmdbId, s.seasonNumber, eps, effectiveAnilistId)
                                    val cur = _episodesBySeason.value.toMutableMap()
                                    cur[s.seasonNumber] = eps
                                    _episodesBySeason.value = cur
                                }
                            }
                            currentCoroutineContext().ensureActive()
                            SeriesContentMemoryCache.putEpisodesBySeason(item, tmdbId, _episodesBySeason.value, effectiveAnilistId)
                            refreshNextAiringEpisode(item)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }

            currentCoroutineContext().ensureActive()

            // 3. AniZip Anime Fallback (for Anime Specials, OVAs, and Side-Stories missing TMDb TV mappings)
            if ((_realSeasons.value.isEmpty() || _realEpisodes.value.isEmpty()) && item.mediaType == MediaType.ANIME && effectiveAnilistId != null && effectiveAnilistId > 0) {
                try {
                    val aniZipEps = enrichmentRepository.fetchAniZipEpisodes(effectiveAnilistId)
                    currentCoroutineContext().ensureActive()
                    if (aniZipEps.isNotEmpty()) {
                        val grouped = aniZipEps.groupBy { it.seasonNumber }
                        if (_realSeasons.value.isEmpty()) {
                            val constructedSeasons = grouped.map { (sNum, eps) ->
                                com.omnitrack.app.data.model.MediaSeason(
                                    seasonNumber = sNum,
                                    name = if (sNum == 0) "Specials" else "Season $sNum",
                                    episodeCount = eps.size,
                                    posterUrl = null,
                                    airDate = eps.firstOrNull()?.airDate
                                )
                            }.sortedWith(compareBy({ if (it.seasonNumber == 0) Int.MAX_VALUE else it.seasonNumber }))

                            _realSeasons.value = constructedSeasons
                            _episodesBySeason.value = grouped
                            val defaultSeason = constructedSeasons.firstOrNull { it.seasonNumber > 0 }?.seasonNumber
                                ?: constructedSeasons.first().seasonNumber
                            _selectedSeasonNumber.value = defaultSeason
                            _realEpisodes.value = grouped[defaultSeason].orEmpty()
                            SeriesContentMemoryCache.putSeasons(item, resolvedTmdbId, constructedSeasons, effectiveAnilistId)
                            SeriesContentMemoryCache.putEpisodesBySeason(item, resolvedTmdbId, grouped, effectiveAnilistId)
                        } else if (_realEpisodes.value.isEmpty()) {
                            val selSeason = _selectedSeasonNumber.value
                            val fallbackSeasonEps = grouped[selSeason] ?: grouped.values.flatten()
                            if (fallbackSeasonEps.isNotEmpty()) {
                                val cur = _episodesBySeason.value.toMutableMap()
                                cur[selSeason] = fallbackSeasonEps
                                _episodesBySeason.value = cur
                                _realEpisodes.value = fallbackSeasonEps
                            }
                        }
                        refreshNextAiringEpisode(item)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    AppLogger.d("DetailViewModel", "AniZip fallback episodes error: ${e.message}")
                }
            }

            currentCoroutineContext().ensureActive()

            // 4. Synthesize episodes from total progress only for short <= 26 episode shows missing all metadata
            if (_realSeasons.value.isEmpty() && _realEpisodes.value.isEmpty() && (item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME)) {
                val totalCount = item.totalProgress?.toInt()
                if (totalCount != null && totalCount > 0 && totalCount <= 26) {
                    val synEps = (1..totalCount).map { epNum ->
                        com.omnitrack.app.data.model.MediaEpisode(
                            id = epNum.toLong(),
                            episodeNumber = epNum,
                            seasonNumber = 1,
                            title = "Episode $epNum",
                            overview = null,
                            stillUrl = null,
                            airDate = null
                        )
                    }
                    val synSeason = com.omnitrack.app.data.model.MediaSeason(
                        seasonNumber = 1,
                        name = "Season 1",
                        episodeCount = totalCount,
                        posterUrl = item.coverUrl,
                        airDate = item.releaseYear
                    )
                    _realSeasons.value = listOf(synSeason)
                    _episodesBySeason.value = mapOf(1 to synEps)
                    _selectedSeasonNumber.value = 1
                    _realEpisodes.value = synEps
                }
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

    private fun decorateEpisodesWithFillers(
        seasonNumber: Int,
        episodes: List<MediaEpisode>,
        malIdForSeason: Long?
    ): List<MediaEpisode> {
        val rootMalId = _seasonMalIds.value[1] ?: _previewItem.value?.malId ?: latestRoomItemCache[_mediaId.value]?.malId
        val rootFillers = rootMalId?.let { _fillerInfoMap.value[it] }.orEmpty()
        val seasonFillers = malIdForSeason?.let { _fillerInfoMap.value[it] }.orEmpty()
        if (rootFillers.isEmpty() && seasonFillers.isEmpty()) return episodes
        val offset = getCumulativeOffsetForSeason(seasonNumber)
        return episodes.map { ep ->
            val absoluteNum = offset + ep.episodeNumber
            val info = (if (seasonNumber > 1 && rootFillers.containsKey(absoluteNum)) rootFillers[absoluteNum] else null)
                ?: seasonFillers[ep.episodeNumber]
                ?: rootFillers[absoluteNum]
                ?: seasonFillers[absoluteNum]
            if (info != null) ep.copy(isFiller = info.isFiller, isRecap = info.isRecap) else ep
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _selectedSeasonNumber.value = seasonNumber
        reloadCommentsForSeason(seasonNumber)

        val previewItem = _previewItem.value
        val activeItem = previewItem ?: latestRoomItemCache[_mediaId.value]
        val rootMalId = _seasonMalIds.value[1] ?: activeItem?.malId
        val malIdForSeason = _seasonMalIds.value[seasonNumber] ?: rootMalId
        if (rootMalId != null && rootMalId > 0 && !_fillerInfoMap.value.containsKey(rootMalId)) {
            loadFillersForMalId(rootMalId, activeItem?.title)
        }
        if (malIdForSeason != null && malIdForSeason > 0 && malIdForSeason != rootMalId && !_fillerInfoMap.value.containsKey(malIdForSeason)) {
            loadFillersForMalId(malIdForSeason, activeItem?.title)
        }

        val inMemory = _episodesBySeason.value[seasonNumber]
        if (!inMemory.isNullOrEmpty()) {
            val decorated = decorateEpisodesWithFillers(seasonNumber, inMemory, malIdForSeason)
            _realEpisodes.value = decorated
            if (decorated != inMemory) {
                _episodesBySeason.value = _episodesBySeason.value + (seasonNumber to decorated)
            }
            return
        }

        val tmdbId = activeItem?.tmdbId ?: SeriesContentMemoryCache.resolvedTmdbIdCache[_mediaId.value]
        SeriesContentMemoryCache.findSeasonEpisodes(activeItem, tmdbId, seasonNumber, _mediaId.value)?.let { cached ->
            val decorated = decorateEpisodesWithFillers(seasonNumber, cached, malIdForSeason)
            _realEpisodes.value = decorated
            _episodesBySeason.value = _episodesBySeason.value + (seasonNumber to decorated)
            return
        }
        seasonEpisodesJob?.cancel()
        seasonEpisodesJob = viewModelScope.launch(Dispatchers.IO) {
            val dbItem = activeItem ?: repository.getMediaById(_mediaId.value).firstOrNull()
            val resolvedTmdbId = tmdbId
                ?: dbItem?.tmdbId
                ?: return@launch
            val tmdbKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
            val currentTitle = dbItem?.title
            val resolvedImdbId = dbItem?.imdbId
            val eps = enrichmentRepository.fetchTvSeasonEpisodes(resolvedTmdbId, seasonNumber, tmdbKey, resolvedImdbId, currentTitle)
            if (eps.isNotEmpty()) {
                val enrichedEps = decorateEpisodesWithFillers(seasonNumber, eps, malIdForSeason)
                SeriesContentMemoryCache.putSeasonEpisodes(dbItem, resolvedTmdbId, seasonNumber, enrichedEps)
                _episodesBySeason.value = _episodesBySeason.value + (seasonNumber to enrichedEps)
                _realEpisodes.value = enrichedEps
            }
        }
    }

    fun toggleSkipFillers() {
        _isFillerSkipEnabled.update { !it }
    }

    private suspend fun ensureLibraryMediaId(): Long {
        val existingId = _mediaId.value
        if (existingId > 0L) return existingId
        val preview = _previewItem.value ?: return -1L
        val found = repository.findExistingMedia(
            anilistId = preview.anilistId,
            malId = preview.malId,
            tmdbId = preview.tmdbId,
            title = preview.title,
            mediaType = preview.mediaType
        )
        val resolvedId = found?.id?.takeIf { it > 0L } ?: repository.insertMedia(
            preview.copy(
                id = 0L,
                status = if (preview.status == TrackingStatus.PLAN_TO_TRACK) TrackingStatus.IN_PROGRESS else preview.status
            )
        )
        if (resolvedId > 0L) {
            withContext(Dispatchers.Main) {
                EphemeralPreviewHolder.previewItem = null
                _previewStack.value = emptyList()
                _previewItem.value = null
                _mediaId.value = resolvedId
            }
        }
        return resolvedId
    }

    fun markWatchedUpToWithFillerChoice(episode: MediaEpisode, markFillersWatched: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0) return@launch
            val pairs = getEpisodesToMarkWatchedUpTo(episode.seasonNumber, episode.episodeNumber, skipFillers = !markFillersWatched)
            repository.setMultipleEpisodesWatched(mediaId, pairs, true)
            val targetProgress = calculateCumulativeProgress(episode.seasonNumber, episode.episodeNumber)
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

    private fun getCumulativeOffsetForSeason(seasonNumber: Int): Int {
        val seasons = _realSeasons.value
        if (seasons.isEmpty() || seasonNumber <= 1) return 0
        return seasons
            .filter { it.seasonNumber in 1 until seasonNumber }
            .sumOf { it.episodeCount }
    }

    private fun loadFillersForMalId(malId: Long, showTitle: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val titleForLookup = showTitle ?: _previewItem.value?.title ?: latestRoomItemCache[_mediaId.value]?.title
            val fillers = enrichmentRepository.fetchEpisodeFillers(malId, titleForLookup)
            if (fillers.isNotEmpty()) {
                _fillerInfoMap.update { it + (malId to fillers) }
                val activeItem = _previewItem.value ?: latestRoomItemCache[_mediaId.value]
                val tmdbId = activeItem?.tmdbId ?: SeriesContentMemoryCache.resolvedTmdbIdCache[_mediaId.value]
                val currentSeason = _selectedSeasonNumber.value
                val seasonMalId = _seasonMalIds.value[currentSeason] ?: malId
                _realEpisodes.update { list ->
                    decorateEpisodesWithFillers(currentSeason, list, seasonMalId)
                }
                _episodesBySeason.update { map ->
                    map.mapValues { (sNum, eps) ->
                        val sMal = _seasonMalIds.value[sNum] ?: malId
                        val decorated = decorateEpisodesWithFillers(sNum, eps, sMal)
                        SeriesContentMemoryCache.putSeasonEpisodes(activeItem, tmdbId, sNum, decorated)
                        decorated
                    }
                }
            }
        }
    }

    fun stepChapterProgress(delta: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0) return@launch
            val item = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val currentCh = item.currentProgress.toInt().coerceAtLeast(0)
            val maxCap = item.totalProgress?.toInt()?.takeIf { it > 0 }
            val nextCh = (currentCh + delta).coerceAtLeast(0).let { n ->
                if (maxCap != null) n.coerceAtMost(maxCap) else n
            }
            applyReadingProgressTarget(mediaId, item, nextCh)
        }
    }

    fun jumpToChapterOrVolume(target: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0) return@launch
            val item = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val maxCap = item.totalProgress?.toInt()?.takeIf { it > 0 }
            val clamped = target.coerceAtLeast(0).let { n ->
                if (maxCap != null) n.coerceAtMost(maxCap) else n
            }
            applyReadingProgressTarget(mediaId, item, clamped)
        }
    }

    private suspend fun applyReadingProgressTarget(
        mediaId: Long,
        item: MediaItemEntity,
        nextCh: Int
    ) {
        val allEps = _episodesBySeason.value
        val totalEpCount = allEps.values.sumOf { it.size }
        if (allEps.isNotEmpty() && totalEpCount <= 200) {
            val markTrue = mutableListOf<Pair<Int, Int>>()
            val markFalse = mutableListOf<Pair<Int, Int>>()
            for ((sNum, eps) in allEps) {
                for (ep in eps) {
                    if (ep.episodeNumber <= nextCh) {
                        markTrue.add(sNum to ep.episodeNumber)
                    } else {
                        markFalse.add(sNum to ep.episodeNumber)
                    }
                }
            }
            if (markTrue.isNotEmpty()) repository.setMultipleEpisodesWatched(mediaId, markTrue, true)
            if (markFalse.isNotEmpty()) repository.setMultipleEpisodesWatched(mediaId, markFalse, false)
        }

        val targetProgress = nextCh.toDouble()
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

    private fun calculateCumulativeProgress(targetSeason: Int, targetEpisode: Int): Double {
        val activeItem = _previewItem.value ?: latestRoomItemCache[_mediaId.value]
        if (activeItem?.mediaType == MediaType.MANGA || activeItem?.mediaType == MediaType.NOVEL) {
            return targetEpisode.coerceAtLeast(0).toDouble()
        }
        var cumulative = 0
        val sortedSeasons = _realSeasons.value.filter { it.seasonNumber > 0 }.sortedBy { it.seasonNumber }
        for (s in sortedSeasons) {
            if (s.seasonNumber < targetSeason) {
                val count = _episodesBySeason.value[s.seasonNumber]?.size ?: s.episodeCount
                cumulative += count
            } else if (s.seasonNumber == targetSeason) {
                cumulative += targetEpisode
                break
            }
        }
        return if (cumulative > 0) cumulative.toDouble() else targetEpisode.coerceAtLeast(0).toDouble()
    }

    private fun getEpisodesToMarkWatchedUpTo(targetSeason: Int, targetEpisode: Int, skipFillers: Boolean = false): List<Pair<Int, Int>> {
        val pairs = mutableListOf<Pair<Int, Int>>()
        val sortedSeasons = _realSeasons.value.filter { it.seasonNumber > 0 }.sortedBy { it.seasonNumber }
        for (s in sortedSeasons) {
            if (s.seasonNumber < targetSeason) {
                val eps = _episodesBySeason.value[s.seasonNumber]
                if (!eps.isNullOrEmpty()) {
                    val toAdd = if (skipFillers) eps.filter { !it.isFiller } else eps
                    pairs.addAll(toAdd.map { s.seasonNumber to it.episodeNumber })
                } else {
                    val count = s.episodeCount.coerceAtLeast(1)
                    pairs.addAll((1..count).map { s.seasonNumber to it })
                }
            } else if (s.seasonNumber == targetSeason) {
                val eps = _episodesBySeason.value[s.seasonNumber] ?: _realEpisodes.value
                var filtered = eps.filter { it.seasonNumber == targetSeason && it.episodeNumber <= targetEpisode }
                if (skipFillers) {
                    filtered = filtered.filter { !it.isFiller || it.episodeNumber == targetEpisode }
                }
                if (filtered.isNotEmpty()) {
                    pairs.addAll(filtered.map { targetSeason to it.episodeNumber })
                } else {
                    pairs.addAll((1..targetEpisode).map { targetSeason to it })
                }
                break
            }
        }
        if (pairs.isEmpty()) {
            pairs.addAll((1..targetEpisode).map { targetSeason to it })
        }
        return pairs.distinct()
    }

    fun toggleEpisodeWatched(episode: MediaEpisode) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0) return@launch
            val item = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val totalEps = _episodesBySeason.value.values.sumOf { it.size }.coerceAtLeast(_realEpisodes.value.size)
            val isLargeReadingEntry = (item.mediaType == MediaType.NOVEL || item.mediaType == MediaType.MANGA) && totalEps > 200
            if (isLargeReadingEntry) {
                val currentlyRead = episode.episodeNumber <= item.currentProgress.toInt()
                val targetCh = if (currentlyRead) (episode.episodeNumber - 1).coerceAtLeast(0) else episode.episodeNumber
                applyReadingProgressTarget(mediaId, item, targetCh)
                return@launch
            }

            val currentProgress = repository.getEpisodeProgress(mediaId).firstOrNull().orEmpty()
            val isCurrentlyWatched = currentProgress.any { it.seasonNumber == episode.seasonNumber && it.episodeNumber == episode.episodeNumber && it.isWatched }
            if (!isCurrentlyWatched) {
                // When marking as watched: mark all previous episodes in earlier seasons AND in this season up to episode
                val pairs = getEpisodesToMarkWatchedUpTo(episode.seasonNumber, episode.episodeNumber, skipFillers = _isFillerSkipEnabled.value)
                repository.setMultipleEpisodesWatched(mediaId, pairs, true)
            } else {
                // When unwatching: unmark from this episode upwards in this season
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
            val targetProgress = if (!isCurrentlyWatched) {
                calculateCumulativeProgress(episode.seasonNumber, episode.episodeNumber)
            } else {
                calculateCumulativeProgress(episode.seasonNumber, episode.episodeNumber - 1).coerceAtLeast(0.0)
            }
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

    fun markPreviousEpisodesWatched(episode: MediaEpisode) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0) return@launch
            val item = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val totalEps = _episodesBySeason.value.values.sumOf { it.size }.coerceAtLeast(_realEpisodes.value.size)
            val isLargeReadingEntry = (item.mediaType == MediaType.NOVEL || item.mediaType == MediaType.MANGA) && totalEps > 200
            if (isLargeReadingEntry) {
                applyReadingProgressTarget(mediaId, item, episode.episodeNumber)
                return@launch
            }

            val pairs = getEpisodesToMarkWatchedUpTo(episode.seasonNumber, episode.episodeNumber)
            repository.setMultipleEpisodesWatched(mediaId, pairs, true)
            val targetProgress = calculateCumulativeProgress(episode.seasonNumber, episode.episodeNumber)
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

    fun toggleSeasonWatched(seasonNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0) return@launch
            val item = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val seasonEps = _episodesBySeason.value[seasonNumber] ?: _realEpisodes.value.filter { it.seasonNumber == seasonNumber }
            val totalEps = _episodesBySeason.value.values.sumOf { it.size }.coerceAtLeast(_realEpisodes.value.size)
            val isLargeReadingEntry = (item.mediaType == MediaType.NOVEL || item.mediaType == MediaType.MANGA) && totalEps > 200
            if (isLargeReadingEntry) {
                val lastCh = seasonEps.maxOfOrNull { it.episodeNumber } ?: 0
                val firstCh = seasonEps.minOfOrNull { it.episodeNumber } ?: 1
                val allBookRead = lastCh > 0 && item.currentProgress.toInt() >= lastCh
                val nextTarget = if (allBookRead) (firstCh - 1).coerceAtLeast(0) else lastCh
                applyReadingProgressTarget(mediaId, item, nextTarget)
                return@launch
            }

            val currentProgress = repository.getEpisodeProgress(mediaId).firstOrNull().orEmpty()
            val allWatched = seasonEps.isNotEmpty() && seasonEps.all { ep ->
                currentProgress.any { it.seasonNumber == ep.seasonNumber && it.episodeNumber == ep.episodeNumber && it.isWatched }
            }
            val pairs = seasonEps.map { it.seasonNumber to it.episodeNumber }
            repository.setMultipleEpisodesWatched(mediaId, pairs, !allWatched)
            val updatedProgress = repository.getEpisodeProgress(mediaId).firstOrNull().orEmpty()
            val watchedCount = updatedProgress.count { it.isWatched }
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

    fun postDiscussionComment(content: String, tag: String?, parentId: String?) {
        val currentItem = _previewItem.value ?: uiState.value.item
        val targetTag = tag ?: "General"
        val authorName = dantotsuCommentsRepository.getCurrentUsername() ?: "You"
        val authorAvatar = dantotsuCommentsRepository.getCurrentUserAvatar()
        val newComment = DiscussionComment(
            id = "comm_${System.currentTimeMillis()}",
            author = authorName,
            authorAvatarUrl = authorAvatar,
            tag = targetTag,
            content = content,
            timeAgo = "Just now",
            upvotes = 1,
            downvotes = 0,
            parentId = parentId
        )

        _discussionComments.update { current ->
            val baseList = if (current.isEmpty() && currentItem != null) {
                createSampleDiscussionComments(currentItem.mediaType, currentItem.totalProgress?.toInt() ?: 24)
            } else {
                current
            }
            if (parentId != null) {
                insertNestedReplyHelper(baseList, parentId, newComment)
            } else {
                listOf(newComment) + baseList
            }
        }

        val anilistId = _activeDiscussionAniListId.value ?: currentItem?.anilistId
        if (anilistId != null) {
            val tagNum = targetTag.replace("Ep ", "").replace("Ch ", "").toIntOrNull()
            viewModelScope.launch(Dispatchers.IO) {
                dantotsuCommentsRepository.postComment(
                    anilistId = anilistId,
                    content = content,
                    tag = tagNum,
                    parentCommentId = parentId?.toLongOrNull()
                )
            }
        }
    }

    fun voteDiscussionComment(commentId: String, voteType: Int) {
        val cid = commentId.toLongOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dantotsuCommentsRepository.vote(cid, voteType)
        }
    }

    fun editDiscussionComment(commentId: String, newContent: String) {
        val cid = commentId.toLongOrNull()
        _discussionComments.update { current ->
            updateCommentContentHelper(current, commentId, newContent)
        }
        if (cid != null) {
            viewModelScope.launch(Dispatchers.IO) {
                dantotsuCommentsRepository.editComment(cid, newContent)
            }
        }
    }

    fun deleteDiscussionComment(commentId: String) {
        val cid = commentId.toLongOrNull()
        _discussionComments.update { current ->
            deleteCommentHelper(current, commentId)
        }
        if (cid != null) {
            viewModelScope.launch(Dispatchers.IO) {
                dantotsuCommentsRepository.deleteComment(cid)
            }
        }
    }

    fun reloadCommentsForSeason(seasonNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentItem = _previewItem.value
                ?: (if (_mediaId.value > 0) repository.getMediaById(_mediaId.value).firstOrNull() else null)
            val baseAniListId = rootAniListId
                ?: _activeDiscussionAniListId.value
                ?: currentItem?.anilistId
                ?: return@launch

            _activeDiscussionTag.value = null

            val currentTitle = currentItem?.title ?: ""

            // 1. Check pre-extracted per-season map (from raw relations before Safeguard 3 filtering)
            var targetAniListId = _seasonAniListIds.value[seasonNumber]

            // 2. If season 1, use baseAniListId
            if (targetAniListId == null && seasonNumber == 1) {
                targetAniListId = baseAniListId
            }

            // 3. Check relations in _animeRelations
            if (targetAniListId == null) {
                if (seasonNumber <= 0) {
                    val match = _animeRelations.value.firstOrNull { it.relationType == "SIDE_STORY" || it.relationType == "SPECIAL" }
                    targetAniListId = match?.id
                } else {
                    val relations = _animeRelations.value
                    val match = relations.firstOrNull { rel ->
                        rel.title.contains("Season $seasonNumber", ignoreCase = true) ||
                        rel.title.contains("${seasonNumber}nd Season", ignoreCase = true) ||
                        rel.title.contains("${seasonNumber}rd Season", ignoreCase = true) ||
                        rel.title.contains("${seasonNumber}th Season", ignoreCase = true) ||
                        (seasonNumber == 2 && rel.relationType == "SEQUEL")
                    }
                    targetAniListId = match?.id
                }
            }

            // 4. If still null and seasonNumber > 1, resolve via AniList GraphQL
            if (targetAniListId == null && seasonNumber > 1 && currentTitle.isNotBlank()) {
                val resolvedId = enrichmentRepository.fetchAniListSeasonId(currentTitle, seasonNumber, baseAniListId)
                if (resolvedId != null) {
                    targetAniListId = resolvedId
                    _seasonAniListIds.update { it + (seasonNumber to resolvedId) }
                }
            }

            // 5. Final fallback if anime has no separate sequel ID: use baseAniListId
            if (targetAniListId == null) {
                targetAniListId = baseAniListId
            }

            loadDiscussionComments(targetAniListId, tag = null)
        }
    }

    fun applyPreferredTitleLanguage(pref: PreferredTitleLanguage) {
        _preferredTitleLanguage.value = pref
    }

    fun togglePreferredTitle() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentItem = _previewItem.value
                ?: (if (_mediaId.value > 0) repository.getMediaById(_mediaId.value).firstOrNull() else null)
                ?: return@launch
            val eng = _englishTitle.value
            val rom = _romajiTitle.value ?: currentItem.nativeTitle
            if (eng.isNullOrBlank() && rom.isNullOrBlank()) return@launch

            val newTitle = if (!eng.isNullOrBlank() && currentItem.title.equals(eng, ignoreCase = true)) {
                rom ?: currentItem.title
            } else {
                eng ?: currentItem.title
            }

            if (newTitle == currentItem.title) return@launch

            val newNative = if (newTitle == eng) (rom ?: currentItem.nativeTitle) else (eng ?: currentItem.nativeTitle)

            if (_previewItem.value != null) {
                val updated = currentItem.copy(title = newTitle, nativeTitle = newNative)
                _previewItem.value = updated
                _previewStack.update { stack ->
                    if (stack.isNotEmpty()) stack.dropLast(1) + updated else listOf(updated)
                }
            } else if (currentItem.id > 0) {
                val updated = currentItem.copy(title = newTitle, nativeTitle = newNative)
                repository.updateMedia(updated)
            }
        }
    }

    private fun updateCommentContentHelper(
        list: List<DiscussionComment>,
        commentId: String,
        newContent: String
    ): List<DiscussionComment> {
        return list.map { item ->
            if (item.id == commentId) {
                item.copy(content = newContent)
            } else if (item.replies.isNotEmpty()) {
                item.copy(replies = updateCommentContentHelper(item.replies, commentId, newContent))
            } else {
                item
            }
        }
    }

    private fun deleteCommentHelper(
        list: List<DiscussionComment>,
        commentId: String
    ): List<DiscussionComment> {
        return list.filter { it.id != commentId }.map { item ->
            if (item.replies.isNotEmpty()) {
                item.copy(replies = deleteCommentHelper(item.replies, commentId))
            } else {
                item
            }
        }
    }

    private fun insertNestedReplyHelper(
        list: List<DiscussionComment>,
        parentId: String,
        newReply: DiscussionComment
    ): List<DiscussionComment> {
        return list.map { item ->
            if (item.id == parentId) {
                item.copy(replies = item.replies + newReply.copy(depth = item.depth + 1))
            } else if (item.replies.isNotEmpty()) {
                item.copy(replies = insertNestedReplyHelper(item.replies, parentId, newReply))
            } else {
                item
            }
        }
    }

    private fun applyCachedAux(cached: CachedDetailAux?, fallbackLogo: String?) {
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        val cachedLogo = cached?.heroClearLogoUrl?.takeIf {
            !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
        }
        _heroClearLogoUrl.value = cachedLogo ?: fallbackLogo
        if (cached == null) return

        _recommendations.value = cached.recommendations
        _reviews.value = cached.reviews
        _cast.value = cached.cast
        _trailers.value = cached.trailers
        _companies.value = cached.companies
        _collectionName.value = cached.collectionName
        _collectionItems.value = cached.collectionItems
        _animeCharacters.value = cached.animeCharacters
        _animeStaff.value = cached.animeStaff
        _animeRelations.value = cached.animeRelations
        _animeAiring.value = cached.animeAiring
        _nextAiringEpisode.value = cached.nextAiringEpisode
        _englishTitle.value = cached.englishTitle
        _romajiTitle.value = cached.romajiTitle
        _seasonAniListIds.value = cached.seasonAniListIds
        _seasonMalIds.value = cached.seasonMalIds
        _format.value = cached.format
        _season.value = cached.season
        _seasonYear.value = cached.seasonYear
        _source.value = cached.source
        _countryOfOrigin.value = cached.countryOfOrigin
        _startDate.value = cached.startDate
        _endDate.value = cached.endDate
        _externalLinks.value = cached.externalLinks
    }

    private fun restoreSeriesFromMemoryCache(targetItem: MediaItemEntity?, targetId: Long) {
        val resolvedTmdb = targetItem?.tmdbId ?: (if (targetId > 0L) SeriesContentMemoryCache.resolvedTmdbIdCache[targetId] else null)
        val cachedSeasons = SeriesContentMemoryCache.findSeasons(targetItem, resolvedTmdb, targetId)
        val cachedGrouped = SeriesContentMemoryCache.findEpisodesBySeason(targetItem, resolvedTmdb, targetId)
        if (!cachedSeasons.isNullOrEmpty()) {
            val valid = cachedSeasons.filter { it.seasonNumber > 0 || it.episodeCount > 0 }
            if (valid.isNotEmpty()) {
                _realSeasons.value = valid
                _episodesBySeason.value = cachedGrouped.orEmpty()
                val defaultSeason = valid.firstOrNull { it.seasonNumber > 0 }?.seasonNumber
                    ?: valid.first().seasonNumber
                _selectedSeasonNumber.value = defaultSeason
                _realEpisodes.value = cachedGrouped?.get(defaultSeason).orEmpty()
                if (!cachedGrouped.isNullOrEmpty()) {
                    refreshNextAiringEpisode(targetItem)
                }
            }
        }
    }

    private fun resolveAuxCache(targetId: Long, item: MediaItemEntity?): CachedDetailAux? {
        return DetailAuxMemoryCache.getCacheKey(targetId)?.let { DetailAuxMemoryCache.cache[it] }
            ?: item?.let { DetailAuxMemoryCache.getCacheKey(it) }?.let { DetailAuxMemoryCache.cache[it] }
            ?: item?.tmdbId?.takeIf { item.mediaType != MediaType.ANIME || item.anilistId == null || item.anilistId <= 0L }?.let {
                DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getTmdbCacheKey(if (item.mediaType == MediaType.MOVIE) "movie" else "tv", it)]
            }
    }

    private fun navigateToSibling(targetId: Long, immediate: MediaItemEntity?): Boolean {
        // Cancel previous in-flight asynchronous operations
        recommendationFetchJob?.cancel()
        seriesFetchJob?.cancel()
        discussionFetchJob?.cancel()
        enrichmentFetchJob?.cancel()

        if (immediate != null) {
            latestRoomItemCache[targetId] = immediate
        }
        resetAuxFields()
        _mediaId.value = targetId
        _previewItem.value = null

        val cachedAux = resolveAuxCache(targetId, immediate)
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        val immediateLogo = immediate?.clearartUrl?.takeIf {
            !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
        }
        applyCachedAux(cachedAux, immediateLogo)
        if (immediate != null) {
            restoreSeriesFromMemoryCache(immediate, targetId)
        }
        updateSiblings(targetId)

        viewModelScope.launch {
            val targetItem = immediate ?: repository.getMediaById(targetId).firstOrNull() ?: return@launch
            if (_mediaId.value != targetId) return@launch
            latestRoomItemCache[targetId] = targetItem

            if (cachedAux == null) {
                val resolvedAux = resolveAuxCache(targetId, targetItem)
                val targetLogo = targetItem.clearartUrl?.takeIf {
                    !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
                }
                applyCachedAux(resolvedAux, targetLogo)
            }
            if (_realSeasons.value.isEmpty()) {
                restoreSeriesFromMemoryCache(targetItem, targetId)
            }
            loadRecommendationsAndReviews(targetItem)
        }
        return true
    }

    fun navigateToPrevious(): Boolean {
        val siblings = _stableSiblingIds.value.ifEmpty {
            ActiveSiblingHolder.activeSiblingIds
        }
        if (siblings.isEmpty()) return false
        val currentId = _mediaId.value
        val currentIndex = siblings.indexOf(currentId)
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else siblings.size - 1
        val prevId = siblings[prevIndex]
        if (prevId == currentId) return false
        return navigateToSibling(prevId, _previousItem.value?.takeIf { it.id == prevId })
    }

    fun navigateToNext(): Boolean {
        val siblings = _stableSiblingIds.value.ifEmpty {
            ActiveSiblingHolder.activeSiblingIds
        }
        if (siblings.isEmpty()) return false
        val currentId = _mediaId.value
        val currentIndex = siblings.indexOf(currentId)
        val nextIndex = if (currentIndex in 0 until siblings.size - 1) currentIndex + 1 else 0
        val nextId = siblings[nextIndex]
        if (nextId == currentId) return false
        return navigateToSibling(nextId, _nextItem.value?.takeIf { it.id == nextId })
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
            val item = _previewItem.value ?: uiState.value.item ?: return@launch
            if (item.id > 0) {
                repository.updateCover(item.id, newCoverUrl)
                repository.updateCustomCover(item.id, newCoverUrl)
            }
            if (_previewItem.value != null) {
                _previewItem.value = _previewItem.value?.copy(coverUrl = newCoverUrl, customCoverPath = newCoverUrl)
            }
        }
    }

    fun updateBanner(newBannerUrl: String) {
        viewModelScope.launch {
            val item = _previewItem.value ?: uiState.value.item ?: return@launch
            if (item.id > 0) {
                repository.updateBanner(item.id, newBannerUrl)
                repository.updateCustomBanner(item.id, newBannerUrl)
            }
            if (_previewItem.value != null) {
                _previewItem.value = _previewItem.value?.copy(bannerUrl = newBannerUrl, customBannerPath = newBannerUrl)
            }
        }
    }

    fun updateCustomCoverPath(path: String?, context: android.content.Context? = null) {
        viewModelScope.launch {
            val item = _previewItem.value ?: uiState.value.item ?: return@launch
            if (!item.customCoverPath.isNullOrBlank() && item.customCoverPath != path) {
                try { java.io.File(item.customCoverPath).delete() } catch (_: Exception) {}
            }
            if (item.id > 0) {
                repository.updateMedia(item.copy(customCoverPath = path))
            }
            if (_previewItem.value != null) {
                _previewItem.value = _previewItem.value?.copy(customCoverPath = path)
            }
        }
    }

    fun updateCustomBannerPath(path: String?, context: android.content.Context? = null) {
        viewModelScope.launch {
            val item = _previewItem.value ?: uiState.value.item ?: return@launch
            if (!item.customBannerPath.isNullOrBlank() && item.customBannerPath != path) {
                try { java.io.File(item.customBannerPath).delete() } catch (_: Exception) {}
            }
            if (item.id > 0) {
                repository.updateMedia(item.copy(customBannerPath = path))
            }
            if (_previewItem.value != null) {
                _previewItem.value = _previewItem.value?.copy(customBannerPath = path)
            }
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
            latestRoomItemCache.remove(item.id)
            latestEpisodeProgressCache.remove(item.id)
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
            trackerSyncEngine: TrackerSyncEngine? = null,
            trackerTokenManager: TrackerTokenManager? = null
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DetailViewModel(repository, mediaId, enrichmentRepository, preferenceManager, trackerSyncEngine, trackerTokenManager) as T
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
    val seasonsCache = java.util.concurrent.ConcurrentHashMap<String, List<com.omnitrack.app.data.model.MediaSeason>>()
    val episodesCache = java.util.concurrent.ConcurrentHashMap<String, List<com.omnitrack.app.data.model.MediaEpisode>>()
    val episodesBySeasonCache = java.util.concurrent.ConcurrentHashMap<String, Map<Int, List<com.omnitrack.app.data.model.MediaEpisode>>>()
    val resolvedTmdbIdCache = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    val resolvedImdbIdCache = java.util.concurrent.ConcurrentHashMap<Long, String>()

    fun keysFor(
        item: MediaItemEntity?,
        tmdbId: Long? = item?.tmdbId,
        fallbackMediaId: Long = item?.id ?: 0L,
        effectiveAnilistId: Long? = item?.anilistId
    ): List<String> = buildList {
        val idToUse = item?.id?.takeIf { it > 0L } ?: fallbackMediaId.takeIf { it > 0L }
        if (idToUse != null) add("id_$idToUse")
        val alId = effectiveAnilistId?.takeIf { it > 0L }
        if (alId != null) add("al_$alId")
        val tid = tmdbId?.takeIf { it > 0L }
        if (tid != null && (item?.mediaType != MediaType.ANIME || alId == null)) {
            add("tmdb_$tid")
        }
        val titleNorm = item?.title?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (tid != null && titleNorm != null && item?.mediaType == MediaType.ANIME) {
            add("tmdb_${tid}_$titleNorm")
        }
    }

    fun findSeasons(
        item: MediaItemEntity?,
        tmdbId: Long? = item?.tmdbId,
        fallbackMediaId: Long = item?.id ?: 0L,
        effectiveAnilistId: Long? = item?.anilistId
    ): List<com.omnitrack.app.data.model.MediaSeason>? {
        for (k in keysFor(item, tmdbId, fallbackMediaId, effectiveAnilistId)) {
            seasonsCache[k]?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    fun findEpisodesBySeason(
        item: MediaItemEntity?,
        tmdbId: Long? = item?.tmdbId,
        fallbackMediaId: Long = item?.id ?: 0L,
        effectiveAnilistId: Long? = item?.anilistId
    ): Map<Int, List<com.omnitrack.app.data.model.MediaEpisode>>? {
        for (k in keysFor(item, tmdbId, fallbackMediaId, effectiveAnilistId)) {
            episodesBySeasonCache[k]?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    fun findSeasonEpisodes(
        item: MediaItemEntity?,
        tmdbId: Long?,
        seasonNumber: Int,
        fallbackMediaId: Long = item?.id ?: 0L,
        effectiveAnilistId: Long? = item?.anilistId
    ): List<com.omnitrack.app.data.model.MediaEpisode>? {
        for (k in keysFor(item, tmdbId, fallbackMediaId, effectiveAnilistId)) {
            episodesCache["$k:$seasonNumber"]?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    fun putSeasons(
        item: MediaItemEntity?,
        tmdbId: Long?,
        seasons: List<com.omnitrack.app.data.model.MediaSeason>,
        effectiveAnilistId: Long? = item?.anilistId
    ) {
        for (k in keysFor(item, tmdbId, effectiveAnilistId = effectiveAnilistId)) {
            seasonsCache[k] = seasons
        }
    }

    fun putEpisodesBySeason(
        item: MediaItemEntity?,
        tmdbId: Long?,
        map: Map<Int, List<com.omnitrack.app.data.model.MediaEpisode>>,
        effectiveAnilistId: Long? = item?.anilistId
    ) {
        for (k in keysFor(item, tmdbId, effectiveAnilistId = effectiveAnilistId)) {
            episodesBySeasonCache[k] = map
        }
    }

    fun putSeasonEpisodes(
        item: MediaItemEntity?,
        tmdbId: Long?,
        seasonNumber: Int,
        eps: List<com.omnitrack.app.data.model.MediaEpisode>,
        effectiveAnilistId: Long? = item?.anilistId
    ) {
        for (k in keysFor(item, tmdbId, effectiveAnilistId = effectiveAnilistId)) {
            episodesCache["$k:$seasonNumber"] = eps
        }
    }
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
    val animeAiring: AnimeAiringSchedule? = null,
    val englishTitle: String? = null,
    val romajiTitle: String? = null,
    val seasonAniListIds: Map<Int, Long> = emptyMap(),
    val format: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val source: String? = null,
    val countryOfOrigin: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val externalLinks: List<ExternalLinkItem> = emptyList(),
    val seasonMalIds: Map<Int, Long> = emptyMap(),
    val heroClearLogoUrl: String? = null,
    val isMangaSeasonGrouped: Boolean = false,
    val novelDetailMeta: NovelDetailMeta = NovelDetailMeta()
)

internal object DetailAuxMemoryCache {
    val cache = java.util.concurrent.ConcurrentHashMap<String, CachedDetailAux>()

    fun getCacheKey(item: MediaItemEntity): String? {
        if (item.id > 0L) return "id_${item.id}"
        if (item.mediaType == MediaType.ANIME && item.anilistId != null && item.anilistId > 0L) return "anilist_${item.anilistId}"
        if (item.tmdbId != null && item.tmdbId > 0L) return "tmdb_${if (item.mediaType == MediaType.MOVIE) "movie" else "tv"}_${item.tmdbId}"
        if (item.anilistId != null && item.anilistId > 0L) return "anilist_${item.anilistId}"
        if (item.malId != null && item.malId > 0L) return "mal_${item.malId}"
        return null
    }

    fun getCacheKey(mediaId: Long): String? {
        return if (mediaId > 0L) "id_$mediaId" else null
    }

    fun getTmdbCacheKey(type: String, tmdbId: Long): String {
        return "tmdb_${type}_$tmdbId"
    }
}
