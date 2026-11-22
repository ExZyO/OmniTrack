package com.omnitrack.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnitrack.app.data.db.entity.BookEntryEntity
import com.omnitrack.app.data.db.entity.BookQuoteEntity
import com.omnitrack.app.data.db.entity.EpisodeProgressEntity
import com.omnitrack.app.data.db.entity.GameEntryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.VnRouteEntity
import com.omnitrack.app.data.enrichment.BookEnrichmentSource
import com.omnitrack.app.data.enrichment.EnrichmentRepository
import com.omnitrack.app.data.enrichment.GameEnrichmentSource
import com.omnitrack.app.data.enrichment.VisualNovelEnrichmentSource
import com.omnitrack.app.data.model.BookDetailMeta
import com.omnitrack.app.data.model.BookEditionItem
import com.omnitrack.app.data.model.BookEntryType
import com.omnitrack.app.data.model.BookFormat
import com.omnitrack.app.data.model.BookLiteraryForm
import com.omnitrack.app.data.model.BookTrackingMode
import com.omnitrack.app.data.model.GameCompletionTier
import com.omnitrack.app.data.model.GameDetailMeta
import com.omnitrack.app.data.model.GameEntryType
import com.omnitrack.app.data.model.VnCharacterProfile
import com.omnitrack.app.data.model.VnDetailMeta
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
import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.isActive
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
    val novelDetailMeta: NovelDetailMeta = NovelDetailMeta(),
    val vnDetailMeta: VnDetailMeta = VnDetailMeta(),
    val vnRoutes: List<VnRouteEntity> = emptyList(),
    val gameDetailMeta: GameDetailMeta = GameDetailMeta(),
    val gameEntries: List<GameEntryEntity> = emptyList(),
    val bookDetailMeta: BookDetailMeta = BookDetailMeta(),
    val bookEntries: List<BookEntryEntity> = emptyList(),
    val bookQuotes: List<BookQuoteEntity> = emptyList(),
    val episodeMetaMap: Map<Pair<Int, Int>, EpisodeProgressEntity> = emptyMap()
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
    val novelDetailMeta: NovelDetailMeta = NovelDetailMeta(),
    val vnDetailMeta: VnDetailMeta = VnDetailMeta(),
    val gameDetailMeta: GameDetailMeta = GameDetailMeta(),
    val bookDetailMeta: BookDetailMeta = BookDetailMeta()
)

class DetailViewModel(
    private val repository: MediaRepository,
    mediaId: Long,
    private val enrichmentRepository: EnrichmentRepository = EnrichmentRepository(),
    private val preferenceManager: PreferenceManager? = null,
    private val trackerSyncEngine: TrackerSyncEngine? = null,
    private val trackerTokenManager: TrackerTokenManager? = null
) : ViewModel() {

    private val visualNovelEnrichmentSource = VisualNovelEnrichmentSource()
    private val gameEnrichmentSource = GameEnrichmentSource()
    private val bookEnrichmentSource = BookEnrichmentSource()
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
    private val _vnDetailMeta = MutableStateFlow(VnDetailMeta())
    private val _gameDetailMeta = MutableStateFlow(GameDetailMeta())
    private val _bookDetailMeta = MutableStateFlow(BookDetailMeta())
    private val _fillerInfoMap = MutableStateFlow<Map<Long, Map<Int, com.omnitrack.app.data.model.EpisodeFillerInfo>>>(emptyMap())
    private val allSeasonCommentsCache = java.util.concurrent.ConcurrentHashMap<Long, List<DiscussionComment>>()
    private var rootAniListId: Long? = null
    private var discussionFetchJob: kotlinx.coroutines.Job? = null
    private var enrichmentFetchJob: kotlinx.coroutines.Job? = null
    private var activeSeriesFetchKey: String? = null
    private val latestRoomItemCache = java.util.concurrent.ConcurrentHashMap<Long, MediaItemEntity>()
    private val latestEpisodeProgressCache = java.util.concurrent.ConcurrentHashMap<Long, List<EpisodeProgressEntity>>()
    private val latestVnRoutesCache = java.util.concurrent.ConcurrentHashMap<Long, List<VnRouteEntity>>()
    private val vnAutoSeededMediaIds = Collections.synchronizedSet(mutableSetOf<Long>())
    private val latestGameEntriesCache = java.util.concurrent.ConcurrentHashMap<Long, List<GameEntryEntity>>()
    private val gameAutoSeededMediaIds = Collections.synchronizedSet(mutableSetOf<Long>())
    private val latestBookEntriesCache = java.util.concurrent.ConcurrentHashMap<Long, List<BookEntryEntity>>()
    private val latestBookQuotesCache = java.util.concurrent.ConcurrentHashMap<Long, List<BookQuoteEntity>>()
    private val bookAutoSeededMediaIds = Collections.synchronizedSet(mutableSetOf<Long>())
    private val previewMetaCache = java.util.concurrent.ConcurrentHashMap<String, Map<Pair<Int, Int>, EpisodeProgressEntity>>()
    private val _previewEpisodeMeta = MutableStateFlow<Map<Pair<Int, Int>, EpisodeProgressEntity>>(emptyMap())
    val previewEpisodeMeta: StateFlow<Map<Pair<Int, Int>, EpisodeProgressEntity>> = _previewEpisodeMeta.asStateFlow()
    private val _ranobeBookDetails = MutableStateFlow<Map<Long, EnrichmentRepository.RanobeDbBookDetail>>(emptyMap())
    val ranobeBookDetails: StateFlow<Map<Long, EnrichmentRepository.RanobeDbBookDetail>> = _ranobeBookDetails.asStateFlow()
    private val _isTransitionLoading = MutableStateFlow(false)
    val isTransitionLoading: StateFlow<Boolean> = _isTransitionLoading.asStateFlow()
    private val _revealGeneration = MutableStateFlow(0)
    val revealGeneration: StateFlow<Int> = _revealGeneration.asStateFlow()
    private var transitionLoadingJob: kotlinx.coroutines.Job? = null
    private var transitionGraceJob: kotlinx.coroutines.Job? = null
    @Volatile private var activeTransitionGen: Long = 0L
    @Volatile private var initialPreArmGen: Long = 0L
    @Volatile private var skipTransitionArmForId: Long = -1L
    @Volatile private var lastLoadedLibraryId: Long = -1L
    @Volatile private var transitionAuxReady: Boolean = false
    @Volatile private var transitionSeriesReady: Boolean = false
    @Volatile private var transitionEnrichReady: Boolean = false
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
                        vndbId = ephemeral.vndbId,
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
            val cachedRoomItem = latestRoomItemCache[mediaId]
            val allowTmdbCacheFallback = cachedRoomItem != null && (
                cachedRoomItem.mediaType == MediaType.SHOW ||
                cachedRoomItem.mediaType == MediaType.MOVIE ||
                (cachedRoomItem.mediaType == MediaType.ANIME && (cachedRoomItem.anilistId == null || cachedRoomItem.anilistId <= 0L))
            )
            DetailAuxMemoryCache.getCacheKey(mediaId)?.let { DetailAuxMemoryCache.cache[it] }
                ?: cachedRoomItem?.let { DetailAuxMemoryCache.getCacheKey(it) }?.let { DetailAuxMemoryCache.cache[it] }
                ?: if (allowTmdbCacheFallback) {
                    SeriesContentMemoryCache.resolvedTmdbIdCache[mediaId]?.let {
                        DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getTmdbCacheKey("tv", it)]
                            ?: DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getTmdbCacheKey("movie", it)]
                    }
                } else null
        } else if (mediaId == -1L) {
            val ephemeral = EphemeralPreviewHolder.previewItem
            if (ephemeral != null) {
                val allowTmdbCacheFallback = ephemeral.mediaType == MediaType.SHOW ||
                    ephemeral.mediaType == MediaType.MOVIE ||
                    (ephemeral.mediaType == MediaType.ANIME && (ephemeral.anilistId == null || ephemeral.anilistId <= 0L))
                DetailAuxMemoryCache.getCacheKey(ephemeral)?.let { DetailAuxMemoryCache.cache[it] }
                    ?: ephemeral.tmdbId?.takeIf { allowTmdbCacheFallback }?.let {
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
            if (cachedAux.animeStaff.isNotEmpty()) _animeStaff.value = cachedAux.animeStaff
            if (cachedAux.animeRelations.isNotEmpty()) _animeRelations.value = cachedAux.animeRelations
            if (cachedAux.animeAiring != null) _animeAiring.value = cachedAux.animeAiring
            if (cachedAux.nextAiringEpisode != null) _nextAiringEpisode.value = cachedAux.nextAiringEpisode
            if (cachedAux.englishTitle != null) _englishTitle.value = cachedAux.englishTitle
            if (cachedAux.romajiTitle != null) _romajiTitle.value = cachedAux.romajiTitle
            if (cachedAux.synonyms.isNotEmpty()) _synonyms.value = cachedAux.synonyms
            if (cachedAux.tags.isNotEmpty()) _tags.value = cachedAux.tags
            _popularity.value = cachedAux.popularity
            _favourites.value = cachedAux.favourites
            _durationMinutes.value = cachedAux.durationMinutes
            if (cachedAux.seasonAniListIds.isNotEmpty()) _seasonAniListIds.value = cachedAux.seasonAniListIds
            if (cachedAux.seasonMalIds.isNotEmpty()) _seasonMalIds.value = cachedAux.seasonMalIds
            _format.value = cachedAux.format
            _season.value = cachedAux.season
            _seasonYear.value = cachedAux.seasonYear
            _source.value = cachedAux.source
            _countryOfOrigin.value = cachedAux.countryOfOrigin
            _startDate.value = cachedAux.startDate
            _endDate.value = cachedAux.endDate
            if (cachedAux.externalLinks.isNotEmpty()) _externalLinks.value = cachedAux.externalLinks
            val prefInit = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
            val cachedInitLogo = cachedAux.heroClearLogoUrl?.takeIf {
                !it.contains("thetvdb.com") || prefInit == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
            }
            if (!cachedInitLogo.isNullOrBlank()) _heroClearLogoUrl.value = cachedInitLogo
            _isMangaSeasonGrouped.value = cachedAux.isMangaSeasonGrouped
            _novelDetailMeta.value = cachedAux.novelDetailMeta
            _vnDetailMeta.value = cachedAux.vnDetailMeta
            _gameDetailMeta.value = cachedAux.gameDetailMeta
            _bookDetailMeta.value = cachedAux.bookDetailMeta
            if (cachedAux.vnDiscussionComments.isNotEmpty() && _discussionComments.value.isEmpty()) {
                _discussionComments.value = cachedAux.vnDiscussionComments
            }
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
                beginTransitionLoadingIfUncached(ephemeral)
                _previewStack.value = listOf(ephemeral)
                _previewItem.value = ephemeral
                syncPreviewEpisodeMeta(ephemeral)
                loadRecommendationsAndReviews(ephemeral)
                maybeEnrich(ephemeral)
            }
        } else if (mediaId > 0L) {
            val preCachedAux = DetailAuxMemoryCache.getCacheKey(mediaId)?.let { DetailAuxMemoryCache.cache[it] }
                ?: latestRoomItemCache[mediaId]?.let { DetailAuxMemoryCache.getCacheKey(it) }?.let { DetailAuxMemoryCache.cache[it] }
            if (preCachedAux == null) {
                activeTransitionGen += 1L
                initialPreArmGen = activeTransitionGen
                transitionAuxReady = false
                transitionSeriesReady = false
                transitionEnrichReady = false
                _isTransitionLoading.value = true
                launchTransitionSafetyTimeout(activeTransitionGen)
            }
            viewModelScope.launch(Dispatchers.IO) {
                val item = repository.getMediaById(mediaId).firstOrNull()
                withContext(Dispatchers.Main.immediate) {
                    if (item != null) {
                        onLibraryItemReady(item)
                    } else {
                        finishTransitionLoading(triggerReveal = false)
                    }
                }
            }
        }
    }

    private fun onLibraryItemReady(item: MediaItemEntity) {
        if (item.id <= 0L || _previewItem.value != null || _mediaId.value != item.id) return
        latestRoomItemCache[item.id] = item
        val auxKey = "${item.id}_${item.anilistId}_${item.tmdbId}_${item.title}"
        if (loadedAuxKeys.add(auxKey)) {
            val isSameLibraryItem = item.id == lastLoadedLibraryId || skipTransitionArmForId == item.id
            if (_isTransitionLoading.value && activeTransitionGen == initialPreArmGen && initialPreArmGen > 0L) {
                initialPreArmGen = 0L
                syncTransitionFlagsForItem(item)
            } else if (!isSameLibraryItem && !_isTransitionLoading.value) {
                beginTransitionLoadingIfUncached(item)
            }
            lastLoadedLibraryId = item.id
            if (hasCachedSeries(item)) {
                restoreSeriesFromMemoryCache(item, item.id)
            }
            maybeEnrich(item)
            loadRecommendationsAndReviews(item)
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
        val gen = activeTransitionGen
        if (item.mediaType == MediaType.VISUAL_NOVEL || item.mediaType == MediaType.GAME || item.mediaType == MediaType.BOOK) {
            markTransitionEnrichReady(gen)
            return
        }
        if (!needsEnrichment) {
            markTransitionEnrichReady(gen)
            return
        }

        enrichmentFetchJob?.cancel()
        enrichmentFetchJob = viewModelScope.launch(Dispatchers.IO) {
            fun isStillCurrentItem(): Boolean {
                if (!isActive) return false
                return if (item.id > 0L) {
                    _previewItem.value == null && _mediaId.value == item.id
                } else {
                    val cur = _previewItem.value
                    cur != null && cur.id == item.id && cur.title == item.title && cur.mediaType == item.mediaType
                }
            }
            try {
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
                        if (isStillCurrentItem() && isAllowedLogo) {
                            _heroClearLogoUrl.value = earlyLogo
                        }
                        val curr = repository.getMediaById(item.id).firstOrNull() ?: item
                        if (curr.clearartUrl != earlyLogo && isAllowedLogo) {
                            repository.updateMedia(curr.copy(clearartUrl = earlyLogo))
                        }
                    } else if (isStillCurrentItem()) {
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
                    markTransitionEnrichReady(gen)
                }
            )

            if (item.id > 0) {
                if (!isStillCurrentItem()) return@launch
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
                if (isStillCurrentItem() && _recommendations.value.isEmpty() && enrichment.resolvedTmdbId != null && latest.tmdbId == null) {
                    loadRecommendationsAndReviews(updated)
                }
            } else {
                if (!isStillCurrentItem()) return@launch
                val currentPreview = _previewItem.value ?: return@launch

                val updated = currentPreview.copy(
                    tmdbId = enrichment.resolvedTmdbId ?: currentPreview.tmdbId,
                    imdbId = enrichment.resolvedImdbId ?: currentPreview.imdbId,
                    clearartUrl = enrichment.clearartUrl ?: currentPreview.clearartUrl,
                    communityScore = enrichment.communityScore ?: currentPreview.communityScore,
                    imdbScore = enrichment.imdbScore ?: currentPreview.imdbScore,
                    rtScore = enrichment.rtScore ?: currentPreview.rtScore,
                    metacriticScore = enrichment.metacriticScore ?: currentPreview.metacriticScore,
                    traktScore = enrichment.traktScore ?: currentPreview.traktScore,
                    audienceScore = enrichment.audienceScore ?: currentPreview.audienceScore,
                    releaseYear = enrichment.releaseYear ?: currentPreview.releaseYear,
                    trailerYoutubeKey = enrichment.trailerYoutubeKey ?: currentPreview.trailerYoutubeKey,
                    studioOrNetwork = enrichment.studioOrNetwork ?: currentPreview.studioOrNetwork,
                    runtimeMinutes = enrichment.runtimeMinutes ?: currentPreview.runtimeMinutes,
                    contentRating = enrichment.contentRating ?: currentPreview.contentRating,
                    airStatus = enrichment.airStatus ?: currentPreview.airStatus,
                    totalProgress = if (currentPreview.totalProgress == null || currentPreview.totalProgress == 0.0) enrichment.totalEpisodes?.toDouble() ?: currentPreview.totalProgress else currentPreview.totalProgress
                )
                _previewItem.value = updated
                _previewStack.update { stack ->
                    if (stack.isNotEmpty()) stack.dropLast(1) + updated else listOf(updated)
                }
                if (_recommendations.value.isEmpty() && enrichment.resolvedTmdbId != null && currentPreview.tmdbId == null) {
                    loadRecommendationsAndReviews(updated)
                }
            }
            if (enrichment.videos.isNotEmpty() && isStillCurrentItem()) {
                _trailers.value = enrichment.videos
            }
            if (enrichment.belongsToCollectionId != null && _collectionItems.value.isEmpty() && isStillCurrentItem()) {
                val collData = enrichmentRepository.fetchTmdbCollection(enrichment.belongsToCollectionId, tmdbKey)
                if (collData != null && isStillCurrentItem()) {
                    _collectionName.value = collData.name
                    _collectionItems.value = collData.parts
                }
            }
            } finally {
                markTransitionEnrichReady(gen)
            }
        }
    }

    private data class ActiveRoomSnapshot(
        val currentId: Long,
        val isPreview: Boolean,
        val item: MediaItemEntity?,
        val progressList: List<EpisodeProgressEntity>,
        val vnRoutes: List<VnRouteEntity> = emptyList(),
        val gameEntries: List<GameEntryEntity> = emptyList(),
        val bookEntries: List<BookEntryEntity> = emptyList(),
        val bookQuotes: List<BookQuoteEntity> = emptyList()
    )

    private val auxFlow: Flow<PreviewAndAux> = combine(
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
            _novelDetailMeta,
            _vnDetailMeta,
            _gameDetailMeta,
            _bookDetailMeta
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
            novelDetailMeta = (args[42] as? NovelDetailMeta) ?: NovelDetailMeta(),
            vnDetailMeta = (args[43] as? VnDetailMeta) ?: VnDetailMeta(),
            gameDetailMeta = (args[44] as? GameDetailMeta) ?: GameDetailMeta(),
            bookDetailMeta = (args[45] as? BookDetailMeta) ?: BookDetailMeta()
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val activeRoomDataFlow: Flow<ActiveRoomSnapshot> = combine(_mediaId, _previewItem) { id, preview ->
        if (preview != null) (preview.id to true) else (id to false)
    }.distinctUntilChanged().flatMapLatest { (currentId, isPreview) ->
        if (isPreview) {
            if (currentId > 0L) {
                combine(
                    repository.getEpisodeProgress(currentId)
                        .onStart { emit(latestEpisodeProgressCache[currentId] ?: emptyList()) }
                        .distinctUntilChanged(),
                    repository.getVnRoutes(currentId)
                        .onStart { emit(latestVnRoutesCache[currentId] ?: emptyList()) }
                        .distinctUntilChanged(),
                    repository.getGameEntries(currentId)
                        .onStart { emit(latestGameEntriesCache[currentId] ?: emptyList()) }
                        .distinctUntilChanged(),
                    repository.getBookEntries(currentId)
                        .onStart { emit(latestBookEntriesCache[currentId] ?: emptyList()) }
                        .distinctUntilChanged(),
                    repository.getBookQuotes(currentId)
                        .onStart { emit(latestBookQuotesCache[currentId] ?: emptyList()) }
                        .distinctUntilChanged()
                ) { progressList, vnRoutes, gameEntries, bookEntries, bookQuotes ->
                    latestEpisodeProgressCache[currentId] = progressList
                    latestVnRoutesCache[currentId] = vnRoutes
                    latestGameEntriesCache[currentId] = gameEntries
                    latestBookEntriesCache[currentId] = bookEntries
                    latestBookQuotesCache[currentId] = bookQuotes
                    ActiveRoomSnapshot(
                        currentId = currentId,
                        isPreview = true,
                        item = null,
                        progressList = progressList,
                        vnRoutes = vnRoutes,
                        gameEntries = gameEntries,
                        bookEntries = bookEntries,
                        bookQuotes = bookQuotes
                    )
                }
            } else {
                flowOf(
                    ActiveRoomSnapshot(
                        currentId = currentId,
                        isPreview = true,
                        item = null,
                        progressList = emptyList(),
                        vnRoutes = emptyList(),
                        gameEntries = emptyList(),
                        bookEntries = emptyList(),
                        bookQuotes = emptyList()
                    )
                )
            }
        } else {
            repository.getMediaById(currentId)
                .onStart {
                    val cached = latestRoomItemCache[currentId]
                    if (cached != null) emit(cached)
                }
                .distinctUntilChanged()
                .flatMapLatest { currentItem ->
                    if (currentItem != null) {
                        onLibraryItemReady(currentItem)
                        updateSiblings(currentItem.id)
                        combine(
                            repository.getEpisodeProgress(currentItem.id)
                                .onStart {
                                    emit(latestEpisodeProgressCache[currentItem.id] ?: emptyList())
                                }
                                .distinctUntilChanged(),
                            repository.getVnRoutes(currentItem.id)
                                .onStart {
                                    emit(latestVnRoutesCache[currentItem.id] ?: emptyList())
                                }
                                .distinctUntilChanged(),
                            repository.getGameEntries(currentItem.id)
                                .onStart {
                                    emit(latestGameEntriesCache[currentItem.id] ?: emptyList())
                                }
                                .distinctUntilChanged(),
                            repository.getBookEntries(currentItem.id)
                                .onStart {
                                    emit(latestBookEntriesCache[currentItem.id] ?: emptyList())
                                }
                                .distinctUntilChanged(),
                            repository.getBookQuotes(currentItem.id)
                                .onStart {
                                    emit(latestBookQuotesCache[currentItem.id] ?: emptyList())
                                }
                                .distinctUntilChanged()
                        ) { progressList, vnRoutes, gameEntries, bookEntries, bookQuotes ->
                            if (currentItem.id > 0L) {
                                latestEpisodeProgressCache[currentItem.id] = progressList
                                latestVnRoutesCache[currentItem.id] = vnRoutes
                                latestGameEntriesCache[currentItem.id] = gameEntries
                                latestBookEntriesCache[currentItem.id] = bookEntries
                                latestBookQuotesCache[currentItem.id] = bookQuotes
                            }
                            ActiveRoomSnapshot(
                                currentId = currentItem.id,
                                isPreview = false,
                                item = currentItem,
                                progressList = progressList,
                                vnRoutes = vnRoutes,
                                gameEntries = gameEntries,
                                bookEntries = bookEntries,
                                bookQuotes = bookQuotes
                            )
                        }
                    } else {
                        flowOf(
                            ActiveRoomSnapshot(
                                currentId = currentId,
                                isPreview = false,
                                item = null,
                                progressList = emptyList(),
                                vnRoutes = emptyList(),
                                gameEntries = emptyList(),
                                bookEntries = emptyList(),
                                bookQuotes = emptyList()
                            )
                        )
                    }
                }
        }
    }

    val uiState: StateFlow<DetailUiState> = combine(auxFlow, activeRoomDataFlow) { aux, room ->
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
            val progressList = when {
                aux.previewItem.id > 0L && room.isPreview && room.currentId == aux.previewItem.id -> room.progressList
                aux.previewItem.id > 0L -> latestEpisodeProgressCache[aux.previewItem.id] ?: emptyList()
                else -> emptyList()
            }
            val watchedSet = progressList.filter { it.isWatched }
                .map { it.seasonNumber to it.episodeNumber }.toSet()
            val metaMap = progressList.associateBy { it.seasonNumber to it.episodeNumber }
            val previewRoutes = if (aux.previewItem.id > 0L && room.isPreview && room.currentId == aux.previewItem.id) {
                room.vnRoutes
            } else if (aux.previewItem.id > 0L) {
                latestVnRoutesCache[aux.previewItem.id] ?: emptyList()
            } else {
                emptyList()
            }
            val previewGameEntries = if (aux.previewItem.id > 0L && room.isPreview && room.currentId == aux.previewItem.id) {
                room.gameEntries
            } else if (aux.previewItem.id > 0L) {
                latestGameEntriesCache[aux.previewItem.id] ?: emptyList()
            } else {
                emptyList()
            }
            val previewBookEntries = if (aux.previewItem.id > 0L && room.isPreview && room.currentId == aux.previewItem.id) {
                room.bookEntries
            } else if (aux.previewItem.id > 0L) {
                latestBookEntriesCache[aux.previewItem.id] ?: emptyList()
            } else {
                emptyList()
            }
            val previewBookQuotes = if (aux.previewItem.id > 0L && room.isPreview && room.currentId == aux.previewItem.id) {
                room.bookQuotes
            } else if (aux.previewItem.id > 0L) {
                latestBookQuotesCache[aux.previewItem.id] ?: emptyList()
            } else {
                emptyList()
            }
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
                episodeMetaMap = metaMap,
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
                novelDetailMeta = aux.novelDetailMeta,
                vnDetailMeta = aux.vnDetailMeta,
                vnRoutes = previewRoutes,
                gameDetailMeta = aux.gameDetailMeta,
                gameEntries = previewGameEntries,
                bookDetailMeta = aux.bookDetailMeta,
                bookEntries = previewBookEntries,
                bookQuotes = previewBookQuotes
            )
        } else {
            val targetId = _mediaId.value
            val effectiveItem = room.item?.takeIf { !room.isPreview && it.id == targetId }
                ?: latestRoomItemCache[targetId]
            if (effectiveItem != null) {
                val progressList = if (!room.isPreview && room.currentId == effectiveItem.id) {
                    room.progressList
                } else {
                    latestEpisodeProgressCache[effectiveItem.id] ?: emptyList()
                }
                val itemRoutes = if (!room.isPreview && room.currentId == effectiveItem.id) {
                    room.vnRoutes
                } else {
                    latestVnRoutesCache[effectiveItem.id] ?: emptyList()
                }
                val itemGameEntries = if (!room.isPreview && room.currentId == effectiveItem.id) {
                    room.gameEntries
                } else {
                    latestGameEntriesCache[effectiveItem.id] ?: emptyList()
                }
                val itemBookEntries = if (!room.isPreview && room.currentId == effectiveItem.id) {
                    room.bookEntries
                } else {
                    latestBookEntriesCache[effectiveItem.id] ?: emptyList()
                }
                val itemBookQuotes = if (!room.isPreview && room.currentId == effectiveItem.id) {
                    room.bookQuotes
                } else {
                    latestBookQuotesCache[effectiveItem.id] ?: emptyList()
                }
                val siblings = _stableSiblingIds.value.ifEmpty {
                    ActiveSiblingHolder.activeSiblingIds.ifEmpty { listOf(effectiveItem.id) }
                }
                val watchedSet = progressList.filter { it.isWatched }
                    .map { it.seasonNumber to it.episodeNumber }.toSet()
                val metaMap = progressList.associateBy { it.seasonNumber to it.episodeNumber }
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
                    episodeMetaMap = metaMap,
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
                    novelDetailMeta = aux.novelDetailMeta,
                    vnDetailMeta = aux.vnDetailMeta,
                    vnRoutes = itemRoutes,
                    gameDetailMeta = aux.gameDetailMeta,
                    gameEntries = itemGameEntries,
                    bookDetailMeta = aux.bookDetailMeta,
                    bookEntries = itemBookEntries,
                    bookQuotes = itemBookQuotes
                )
            } else {
                DetailUiState(isLoading = false)
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
        _vnDetailMeta.value = VnDetailMeta()
        _gameDetailMeta.value = GameDetailMeta()
        _bookDetailMeta.value = BookDetailMeta()
    }

    fun isPreviewActive(): Boolean =
        _previewStack.value.isNotEmpty() || _previewItem.value != null || EphemeralPreviewHolder.previewItem != null

    private fun quickHasAuxCacheForPreview(
        anilistId: Long?,
        tmdbId: Long?,
        mediaType: MediaType,
        igdbId: Long? = null,
        steamAppId: Long? = null,
        openLibraryId: String? = null
    ): Boolean {
        if (mediaType == MediaType.BOOK && !openLibraryId.isNullOrBlank()) {
            if (DetailAuxMemoryCache.cache.containsKey("ol_$openLibraryId")) return true
        }
        if (mediaType == MediaType.GAME && igdbId != null && igdbId > 0L) {
            if (DetailAuxMemoryCache.cache.containsKey("igdb_$igdbId")) return true
        }
        if (mediaType == MediaType.GAME && steamAppId != null && steamAppId > 0L) {
            if (DetailAuxMemoryCache.cache.containsKey("steam_$steamAppId")) return true
        }
        if (anilistId != null && anilistId > 0L) {
            if (DetailAuxMemoryCache.cache.containsKey("anilist_${mediaType}_$anilistId")) return true
        }
        val allowTmdbKey = mediaType == MediaType.SHOW || mediaType == MediaType.MOVIE ||
            (mediaType == MediaType.ANIME && (anilistId == null || anilistId <= 0L))
        if (allowTmdbKey && tmdbId != null && tmdbId > 0L) {
            val tmdbKey = DetailAuxMemoryCache.getTmdbCacheKey(if (mediaType == MediaType.MOVIE) "movie" else "tv", tmdbId)
            if (DetailAuxMemoryCache.cache.containsKey(tmdbKey)) return true
        }
        return false
    }

    private fun preArmPreviewLoadingIfUncached(
        anilistId: Long?,
        tmdbId: Long?,
        mediaType: MediaType,
        igdbId: Long? = null,
        steamAppId: Long? = null,
        openLibraryId: String? = null
    ) {
        if (!quickHasAuxCacheForPreview(anilistId, tmdbId, mediaType, igdbId, steamAppId, openLibraryId)) {
            transitionLoadingJob?.cancel()
            transitionGraceJob?.cancel()
            activeTransitionGen += 1L
            transitionAuxReady = false
            transitionSeriesReady = !isSeriesMediaType(mediaType)
            transitionEnrichReady = mediaType !in listOf(MediaType.SHOW, MediaType.MOVIE, MediaType.ANIME)
            _isTransitionLoading.value = true
            launchTransitionSafetyTimeout(activeTransitionGen)
        }
    }

    fun openPreview(rec: RecommendationItem) {
        preArmPreviewLoadingIfUncached(
            rec.anilistId,
            rec.tmdbId,
            rec.mediaType,
            igdbId = rec.igdbId,
            openLibraryId = rec.openLibraryId
        )
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.findExistingMedia(
                anilistId = rec.anilistId,
                malId = rec.malId,
                tmdbId = rec.tmdbId,
                igdbId = rec.igdbId,
                openLibraryId = rec.openLibraryId,
                title = rec.title,
                mediaType = rec.mediaType
            )
            val entityToOpen = existing ?: MediaItemEntity(
                id = -1L,
                title = rec.title,
                coverUrl = rec.coverUrl,
                bannerUrl = rec.bannerUrl,
                mediaType = rec.mediaType,
                releaseYear = rec.releaseYear,
                synopsis = rec.synopsis,
                communityScore = rec.rating,
                tmdbId = rec.tmdbId,
                anilistId = rec.anilistId,
                malId = rec.malId,
                igdbId = rec.igdbId,
                openLibraryId = rec.openLibraryId,
                bookAuthor = rec.bookAuthor,
                status = TrackingStatus.PLAN_TO_TRACK
            )
            withContext(Dispatchers.Main.immediate) {
                openPreviewInternal(entityToOpen)
            }
        }
    }

    fun openPreview(previewEntity: MediaItemEntity) {
        val hasDirectIdCache = previewEntity.id > 0L && DetailAuxMemoryCache.getCacheKey(previewEntity)?.let { DetailAuxMemoryCache.cache.containsKey(it) } == true
        if (!hasDirectIdCache) {
            preArmPreviewLoadingIfUncached(
                anilistId = previewEntity.anilistId,
                tmdbId = previewEntity.tmdbId,
                mediaType = previewEntity.mediaType,
                igdbId = previewEntity.igdbId,
                steamAppId = previewEntity.steamAppId,
                openLibraryId = previewEntity.openLibraryId
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val target = if (previewEntity.id <= 0L) {
                repository.findExistingMedia(
                    anilistId = previewEntity.anilistId,
                    malId = previewEntity.malId,
                    tmdbId = previewEntity.tmdbId,
                    vndbId = previewEntity.vndbId,
                    igdbId = previewEntity.igdbId,
                    steamAppId = previewEntity.steamAppId,
                    openLibraryId = previewEntity.openLibraryId,
                    hardcoverId = previewEntity.hardcoverId,
                    isbn = previewEntity.isbn,
                    title = previewEntity.title,
                    mediaType = previewEntity.mediaType
                ) ?: previewEntity
            } else {
                previewEntity
            }
            withContext(Dispatchers.Main.immediate) {
                openPreviewInternal(target)
            }
        }
    }

    fun openRelationPreview(relation: AnimeRelationItem) {
        val format = relation.format.uppercase()
        val targetType = when {
            relation.mediaType == MediaType.BOOK || format == "BOOK" -> MediaType.BOOK
            relation.mediaType == MediaType.GAME || format == "GAME" || format == "DLC" || format == "EXPANSION" || format == "REMAKE" || format == "REMASTER" -> MediaType.GAME
            relation.mediaType == MediaType.VISUAL_NOVEL || !relation.vndbId.isNullOrBlank() || format == "VN" || format == "VISUAL_NOVEL" -> MediaType.VISUAL_NOVEL
            relation.mediaType == MediaType.SHOW || format == "TV" || format == "SHOW" || format == "TV_SERIES" || format == "MINISERIES" -> MediaType.SHOW
            relation.mediaType == MediaType.MOVIE || format == "MOVIE" -> MediaType.MOVIE
            format == "MANGA" || format == "ONE_SHOT" -> MediaType.MANGA
            format == "NOVEL" -> MediaType.NOVEL
            else -> MediaType.ANIME
        }
        val anilistIdForLookup = relation.id.takeIf {
            it > 0L && (targetType == MediaType.ANIME || targetType == MediaType.MANGA || targetType == MediaType.NOVEL)
        }
        val igdbIdForLookup = relation.id.takeIf { it > 0L && targetType == MediaType.GAME }
        val tmdbIdForLookup = relation.tmdbId?.takeIf { it > 0L }
            ?: relation.id.takeIf { it > 0L && (targetType == MediaType.MOVIE || targetType == MediaType.SHOW) }
        val openLibraryIdForLookup = relation.openLibraryId?.takeIf { it.isNotBlank() }
        preArmPreviewLoadingIfUncached(
            anilistId = anilistIdForLookup,
            tmdbId = tmdbIdForLookup,
            mediaType = targetType,
            igdbId = igdbIdForLookup,
            openLibraryId = openLibraryIdForLookup
        )
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.findExistingMedia(
                anilistId = anilistIdForLookup,
                tmdbId = tmdbIdForLookup,
                vndbId = relation.vndbId,
                igdbId = igdbIdForLookup,
                openLibraryId = openLibraryIdForLookup,
                title = relation.title,
                mediaType = targetType
            )
            val entityToOpen = existing ?: MediaItemEntity(
                id = -1L,
                title = relation.title,
                coverUrl = relation.coverUrl ?: "",
                bannerUrl = relation.bannerUrl,
                mediaType = targetType,
                anilistId = anilistIdForLookup,
                tmdbId = tmdbIdForLookup,
                vndbId = relation.vndbId,
                igdbId = igdbIdForLookup,
                openLibraryId = openLibraryIdForLookup,
                releaseYear = relation.year?.toString(),
                status = TrackingStatus.PLAN_TO_TRACK
            )
            withContext(Dispatchers.Main.immediate) {
                openPreviewInternal(entityToOpen)
            }
        }
    }

    private fun previewMetaKey(item: MediaItemEntity): String =
        "${item.mediaType}_${item.anilistId ?: item.malId ?: item.tmdbId ?: item.title.lowercase().trim()}"

    private fun syncPreviewEpisodeMeta(item: MediaItemEntity?) {
        _previewEpisodeMeta.value = if (item == null || item.id > 0L) {
            emptyMap()
        } else {
            previewMetaCache[previewMetaKey(item)].orEmpty()
        }
    }

    private fun isSeriesMediaType(type: MediaType): Boolean =
        type == MediaType.SHOW || type == MediaType.ANIME || type == MediaType.MANGA || type == MediaType.NOVEL

    private fun hasCachedSeries(entity: MediaItemEntity): Boolean {
        if (!isSeriesMediaType(entity.mediaType)) return true
        return SeriesContentMemoryCache.findSeasons(entity, entity.tmdbId, entity.id, entity.anilistId)?.isNotEmpty() == true
    }

    private fun isVideoLogoNeeded(entity: MediaItemEntity): Boolean {
        val isVideoMedia = entity.mediaType == MediaType.SHOW || entity.mediaType == MediaType.MOVIE || entity.mediaType == MediaType.ANIME
        if (!isVideoMedia) return false
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        val existingLogo = entity.clearartUrl?.takeIf {
            it.isNotBlank() && (!it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE)
        } ?: EnrichmentRepository.getCachedClearart(entity)?.takeIf {
            !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
        }
        return existingLogo.isNullOrBlank()
    }

    private fun launchTransitionSafetyTimeout(gen: Long) {
        transitionLoadingJob?.cancel()
        transitionLoadingJob = viewModelScope.launch {
            delay(2400L)
            if (gen == activeTransitionGen && _isTransitionLoading.value) {
                finishTransitionLoading(triggerReveal = true)
            }
        }
    }

    private fun syncTransitionFlagsForItem(entity: MediaItemEntity) {
        val hasAux = resolveAuxCache(entity.id, entity) != null
        val hasSeries = hasCachedSeries(entity)
        if (hasAux && hasSeries) {
            finishTransitionLoading(triggerReveal = false)
            return
        }
        transitionAuxReady = hasAux
        transitionSeriesReady = hasSeries
        transitionEnrichReady = !isVideoLogoNeeded(entity)
        evaluateTransitionBarrier(activeTransitionGen)
    }

    private fun beginTransitionLoadingIfUncached(entity: MediaItemEntity) {
        val hasAux = resolveAuxCache(entity.id, entity) != null
        val hasSeries = hasCachedSeries(entity)
        if (hasAux && hasSeries) {
            finishTransitionLoading(triggerReveal = false)
            return
        }
        transitionLoadingJob?.cancel()
        transitionGraceJob?.cancel()
        activeTransitionGen += 1L
        val gen = activeTransitionGen
        transitionAuxReady = hasAux
        transitionSeriesReady = hasSeries
        transitionEnrichReady = !isVideoLogoNeeded(entity)
        _isTransitionLoading.value = true
        launchTransitionSafetyTimeout(gen)
        evaluateTransitionBarrier(gen)
    }

    private fun markTransitionAuxReady(gen: Long) {
        if (gen != activeTransitionGen || transitionAuxReady) return
        transitionAuxReady = true
        if (_isTransitionLoading.value) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                evaluateTransitionBarrier(gen)
            }
        }
    }

    private fun markTransitionSeriesReady(gen: Long) {
        if (gen != activeTransitionGen || transitionSeriesReady) return
        transitionSeriesReady = true
        if (_isTransitionLoading.value) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                evaluateTransitionBarrier(gen)
            }
        }
    }

    private fun markTransitionEnrichReady(gen: Long) {
        if (gen != activeTransitionGen || transitionEnrichReady) return
        transitionEnrichReady = true
        if (_isTransitionLoading.value) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                evaluateTransitionBarrier(gen)
            }
        }
    }

    private fun evaluateTransitionBarrier(gen: Long) {
        if (gen != activeTransitionGen || !_isTransitionLoading.value) return
        if (transitionAuxReady && transitionSeriesReady && transitionEnrichReady) {
            finishTransitionLoading(triggerReveal = true)
        } else if (transitionAuxReady && transitionSeriesReady && !transitionEnrichReady && transitionGraceJob?.isActive != true) {
            transitionGraceJob = viewModelScope.launch {
                delay(350L)
                if (gen == activeTransitionGen && _isTransitionLoading.value) {
                    finishTransitionLoading(triggerReveal = true)
                }
            }
        }
    }

    private fun finishTransitionLoading(triggerReveal: Boolean = false) {
        transitionLoadingJob?.cancel()
        transitionLoadingJob = null
        transitionGraceJob?.cancel()
        transitionGraceJob = null
        if (_isTransitionLoading.value) {
            if (triggerReveal) {
                _revealGeneration.update { it + 1 }
            }
            _isTransitionLoading.value = false
        }
    }

    fun updateEpisodeMetadata(
        seasonNumber: Int,
        episodeNumber: Int,
        userRating: Float?,
        userNote: String?,
        isBookmarked: Boolean,
        customLabel: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val activePreview = _previewItem.value
            val targetMediaId = when {
                activePreview != null && activePreview.id > 0L -> activePreview.id
                activePreview != null -> -1L
                else -> _mediaId.value
            }
            if (targetMediaId > 0L) {
                repository.updateEpisodeMetadata(
                    mediaId = targetMediaId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    userRating = userRating,
                    userNote = userNote,
                    isBookmarked = isBookmarked,
                    customLabel = customLabel
                )
            } else if (activePreview != null) {
                val key = previewMetaKey(activePreview)
                val existing = previewMetaCache[key].orEmpty().toMutableMap()
                val prev = existing[seasonNumber to episodeNumber]
                existing[seasonNumber to episodeNumber] = EpisodeProgressEntity(
                    mediaId = -1L,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    isWatched = prev?.isWatched ?: false,
                    watchedAtMs = prev?.watchedAtMs,
                    userRating = userRating,
                    userNote = userNote?.trim()?.takeIf { it.isNotBlank() },
                    isBookmarked = isBookmarked,
                    customLabel = customLabel?.trim()?.takeIf { it.isNotBlank() }
                )
                previewMetaCache[key] = existing
                _previewEpisodeMeta.value = existing
            }
        }
    }

    fun loadRanobeBookDetail(bookId: Long, adjacentBookIds: List<Long> = emptyList()) {
        if (bookId <= 0L) return
        enrichmentRepository.getCachedRanobeBookDetail(bookId)?.let { cached ->
            _ranobeBookDetails.update { it + (bookId to cached) }
            applyRanobeBookRatingToEpisodes(bookId, cached.ratingScore)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val detail = enrichmentRepository.fetchRanobeDbBookDetails(bookId)
            if (detail != null) {
                _ranobeBookDetails.update { it + (bookId to detail) }
                applyRanobeBookRatingToEpisodes(bookId, detail.ratingScore)
            }
            adjacentBookIds.filter { it > 0L && it != bookId }.distinct().take(2).forEach { adjId ->
                if (enrichmentRepository.getCachedRanobeBookDetail(adjId) == null) {
                    val adjDetail = enrichmentRepository.fetchRanobeDbBookDetails(adjId)
                    if (adjDetail != null) {
                        _ranobeBookDetails.update { it + (adjId to adjDetail) }
                        applyRanobeBookRatingToEpisodes(adjId, adjDetail.ratingScore)
                    }
                }
            }
        }
    }

    private fun applyRanobeBookRatingToEpisodes(bookId: Long, score: Float?) {
        if (score == null || score <= 0f) return
        _realEpisodes.update { list ->
            var changed = false
            val updated = list.map { ep ->
                if (ep.ranobeBookId == bookId && ep.voteAverage != score) {
                    changed = true
                    ep.copy(voteAverage = score)
                } else ep
            }
            if (changed) updated else list
        }
        _episodesBySeason.update { map ->
            var changed = false
            val updated = map.mapValues { (_, eps) ->
                eps.map { ep ->
                    if (ep.ranobeBookId == bookId && ep.voteAverage != score) {
                        changed = true
                        ep.copy(voteAverage = score)
                    } else ep
                }
            }
            if (changed) updated else map
        }
    }

    private fun openPreviewInternal(previewEntity: MediaItemEntity) {
        recommendationFetchJob?.cancel()
        seriesFetchJob?.cancel()
        discussionFetchJob?.cancel()
        enrichmentFetchJob?.cancel()

        skipTransitionArmForId = -1L
        lastLoadedLibraryId = -1L
        loadedAuxKeys.clear()
        activeSeriesFetchKey = null
        rootAniListId = null
        allSeasonCommentsCache.clear()

        beginTransitionLoadingIfUncached(previewEntity)
        val cachedAux = resolveAuxCache(previewEntity.id, previewEntity)
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        val previewLogo = previewEntity.clearartUrl?.takeIf {
            !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
        }
        replaceAuxStateForTarget(cachedAux, previewLogo, previewEntity, previewEntity.id)
        syncPreviewEpisodeMeta(previewEntity)
        _previewStack.update { it + previewEntity }
        _previewItem.value = previewEntity
        loadRecommendationsAndReviews(previewEntity)
        maybeEnrich(previewEntity, force = true)
    }

    fun closePreview(): Boolean {
        activeTransitionGen += 1L
        finishTransitionLoading(triggerReveal = false)
        if (_previewStack.value.isEmpty() && _previewItem.value == null && EphemeralPreviewHolder.previewItem == null) {
            return false
        }
        if (_previewStack.value.size > 1) {
            recommendationFetchJob?.cancel()
            seriesFetchJob?.cancel()
            discussionFetchJob?.cancel()
            enrichmentFetchJob?.cancel()
            loadedAuxKeys.clear()
            activeSeriesFetchKey = null
            rootAniListId = null
            allSeasonCommentsCache.clear()

            _previewStack.update { it.dropLast(1) }
            val newTop = _previewStack.value.last()
            val cachedAux = resolveAuxCache(newTop.id, newTop)
            val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
            val topLogo = newTop.clearartUrl?.takeIf {
                !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
            }
            replaceAuxStateForTarget(cachedAux, topLogo, newTop, newTop.id)
            _previewItem.value = newTop
            syncPreviewEpisodeMeta(newTop)
            loadRecommendationsAndReviews(newTop)
            maybeEnrich(newTop)
            return true
        }
        val currentId = _mediaId.value
        if (currentId > 0L) {
            recommendationFetchJob?.cancel()
            seriesFetchJob?.cancel()
            discussionFetchJob?.cancel()
            enrichmentFetchJob?.cancel()

            skipTransitionArmForId = currentId
            lastLoadedLibraryId = currentId
            loadedAuxKeys.clear()
            activeSeriesFetchKey = null
            rootAniListId = null
            allSeasonCommentsCache.clear()

            val targetItem = latestRoomItemCache[currentId]
            val cachedAux = resolveAuxCache(currentId, targetItem)
            val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
            val targetLogo = targetItem?.clearartUrl?.takeIf {
                !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
            }

            _previewStack.value = emptyList()
            _previewItem.value = null
            EphemeralPreviewHolder.previewItem = null
            syncPreviewEpisodeMeta(null)
            replaceAuxStateForTarget(cachedAux, targetLogo, targetItem, currentId)
            viewModelScope.launch(Dispatchers.IO) {
                val item = targetItem ?: repository.getMediaById(currentId).firstOrNull()
                if (item != null && _previewItem.value == null && _mediaId.value == currentId) {
                    withContext(Dispatchers.Main.immediate) {
                        onLibraryItemReady(item)
                    }
                }
            }
            return true
        }
        // Root ephemeral preview: keep _previewItem intact during NavHost popExitTransition so screen never flashes black
        EphemeralPreviewHolder.previewItem = null
        _previewStack.value = emptyList()
        return false
    }

    fun clearAllPreviews() {
        activeTransitionGen += 1L
        finishTransitionLoading(triggerReveal = false)
        recommendationFetchJob?.cancel()
        seriesFetchJob?.cancel()
        discussionFetchJob?.cancel()
        enrichmentFetchJob?.cancel()
        scrollOffsetStack.clear()
        EphemeralPreviewHolder.previewItem = null
        _previewStack.value = emptyList()
        if (_mediaId.value > 0L) {
            _previewItem.value = null
        }
        syncPreviewEpisodeMeta(null)
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
        val allowTmdbAuxKey = item.mediaType == MediaType.SHOW || item.mediaType == MediaType.MOVIE ||
            (item.mediaType == MediaType.ANIME && (item.anilistId == null || item.anilistId <= 0L))
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
            if (existingCached.synonyms.isNotEmpty()) _synonyms.value = existingCached.synonyms
            if (existingCached.tags.isNotEmpty()) _tags.value = existingCached.tags
            _popularity.value = existingCached.popularity
            _favourites.value = existingCached.favourites
            _durationMinutes.value = existingCached.durationMinutes
            _vnDetailMeta.value = existingCached.vnDetailMeta
            _gameDetailMeta.value = existingCached.gameDetailMeta
            if ((item.mediaType == MediaType.VISUAL_NOVEL || item.mediaType == MediaType.GAME) && existingCached.vnDiscussionComments.isNotEmpty()) {
                _discussionComments.value = existingCached.vnDiscussionComments
            }
        }

        val auxGen = activeTransitionGen
        recommendationFetchJob?.cancel()
        recommendationFetchJob = viewModelScope.launch(Dispatchers.IO) {
            fun isStillCurrentItem(): Boolean {
                if (!isActive) return false
                return if (item.id > 0L) {
                    _previewItem.value == null && _mediaId.value == item.id
                } else {
                    val cur = _previewItem.value
                    cur != null && cur.id == item.id && cur.title == item.title && cur.mediaType == item.mediaType
                }
            }
            try {
            if (!_isTransitionLoading.value) {
                delay(250) // Let navigation entrance transition finish smoothly at locked 120 FPS
            }
            if (!isStillCurrentItem()) return@launch

            val tmdbKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
            val recs = mutableListOf<RecommendationItem>()
            val revs = mutableListOf<CommunityReview>()
            val castMembers = mutableListOf<CastMember>()
            val trailerList = mutableListOf<MediaTrailer>()
            val compList = mutableListOf<NetworkProductionCompany>()
            var fetchedCollName: String? = null
            var fetchedCollParts: List<RecommendationItem> = emptyList()

            existingCached?.let {
                if (!isStillCurrentItem()) return@launch
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
                if (it.synonyms.isNotEmpty()) _synonyms.value = it.synonyms
                if (it.tags.isNotEmpty()) _tags.value = it.tags
                _popularity.value = it.popularity
                _favourites.value = it.favourites
                _durationMinutes.value = it.durationMinutes
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
                _vnDetailMeta.value = it.vnDetailMeta
                _gameDetailMeta.value = it.gameDetailMeta
                _bookDetailMeta.value = it.bookDetailMeta
                if ((item.mediaType == MediaType.VISUAL_NOVEL || item.mediaType == MediaType.GAME || item.mediaType == MediaType.BOOK) && it.vnDiscussionComments.isNotEmpty()) {
                    _discussionComments.value = it.vnDiscussionComments
                }
            }

            fun persistAuxCache() {
                if (!isStillCurrentItem()) return
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
                    synonyms = _synonyms.value,
                    tags = _tags.value,
                    popularity = _popularity.value,
                    favourites = _favourites.value,
                    durationMinutes = _durationMinutes.value,
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
                    novelDetailMeta = _novelDetailMeta.value,
                    vnDetailMeta = _vnDetailMeta.value,
                    gameDetailMeta = _gameDetailMeta.value,
                    bookDetailMeta = _bookDetailMeta.value,
                    vnDiscussionComments = if (item.mediaType == MediaType.VISUAL_NOVEL || item.mediaType == MediaType.GAME || item.mediaType == MediaType.BOOK) _discussionComments.value else emptyList()
                )
                if (!isStillCurrentItem()) return
                if (cacheKey != null) DetailAuxMemoryCache.cache[cacheKey] = aux
                if (itemTmdbKey != null) DetailAuxMemoryCache.cache[itemTmdbKey] = aux
                if (idKey != null) DetailAuxMemoryCache.cache[idKey] = aux
            }

            if (item.mediaType == MediaType.VISUAL_NOVEL) {
                val sgdbKey = preferenceManager?.steamGridDbApiKey?.firstOrNull()?.ifBlank { null }
                val igdbClientId = preferenceManager?.igdbClientId?.firstOrNull()?.ifBlank { null }
                val igdbClientSecret = preferenceManager?.igdbClientSecret?.firstOrNull()?.ifBlank { null }
                val vnData = visualNovelEnrichmentSource.fetchFullVisualNovelData(
                    item = item,
                    steamGridDbApiKey = sgdbKey,
                    igdbClientId = igdbClientId,
                    igdbClientSecret = igdbClientSecret
                )
                if (!isStillCurrentItem()) return@launch
                if (vnData != null) {
                    _vnDetailMeta.value = vnData.vnDetailMeta
                    _animeCharacters.value = vnData.characters
                    _animeStaff.value = vnData.staff
                    _animeRelations.value = vnData.relations
                    _companies.value = vnData.companies
                    _tags.value = vnData.tags
                    _synonyms.value = vnData.synonyms
                    _externalLinks.value = vnData.externalLinks
                    _trailers.value = vnData.trailers
                    if (vnData.discussionComments.isNotEmpty()) {
                        _discussionComments.value = vnData.discussionComments
                    }
                    vnData.englishTitle?.let { _englishTitle.value = it }
                    vnData.romajiTitle?.let { _romajiTitle.value = it }
                    vnData.originCountry?.let { _countryOfOrigin.value = it }
                    vnData.startDate?.let { _startDate.value = it }
                    vnData.runtimeMinutes?.let { _durationMinutes.value = it }
                    _format.value = "VISUAL_NOVEL"
                    if (vnData.bangumiScore != null) {
                        _novelDetailMeta.value = _novelDetailMeta.value.copy(bangumiScore = vnData.bangumiScore)
                    }
                    if (!vnData.clearLogoUrl.isNullOrBlank()) {
                        EnrichmentRepository.putCachedClearart(item, vnData.clearLogoUrl)
                        _heroClearLogoUrl.value = vnData.clearLogoUrl
                    }

                    val prefLang = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
                    val preferredTitle = when (prefLang) {
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.ENGLISH ->
                            vnData.englishTitle ?: vnData.romajiTitle ?: vnData.nativeTitle
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.ROMAJI ->
                            vnData.romajiTitle ?: vnData.englishTitle ?: vnData.nativeTitle
                        com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE ->
                            vnData.nativeTitle ?: vnData.romajiTitle ?: vnData.englishTitle
                    }
                    val knownTitles = listOfNotNull(vnData.englishTitle, vnData.romajiTitle, vnData.nativeTitle)
                    fun resolveUpdatedTitle(currentTitle: String): String {
                        val trimmed = currentTitle.trim()
                        return if (preferredTitle != null && knownTitles.any { it.equals(trimmed, ignoreCase = true) }) {
                            preferredTitle
                        } else currentTitle
                    }

                    if (item.id > 0L) {
                        val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                        val excluded = latest.excludedArtSet()
                        val bannerDisabled = latest.artworkRole == "DISABLE_BANNER" || latest.customBannerPath == "NONE"
                        val allowedVnBanner = vnData.bannerUrl?.takeUnless { isArtSourceExcluded(it, excluded, "VNDB") }
                        val shouldUpgradeBanner = !bannerDisabled && latest.customBannerPath.isNullOrBlank() &&
                            allowedVnBanner != null && (
                            latest.bannerUrl.isNullOrBlank() ||
                                (latest.bannerUrl.contains("t.vndb.org/sf/") && !allowedVnBanner.contains("t.vndb.org/sf/"))
                            )
                        val updated = latest.copy(
                            title = resolveUpdatedTitle(latest.title),
                            vndbId = vnData.resolvedVndbId ?: latest.vndbId,
                            bangumiId = vnData.resolvedBangumiId ?: latest.bangumiId,
                            nativeTitle = latest.nativeTitle ?: vnData.nativeTitle,
                            originCountry = vnData.originCountry ?: latest.originCountry,
                            runtimeMinutes = vnData.runtimeMinutes ?: latest.runtimeMinutes,
                            contentRating = vnData.contentRating ?: latest.contentRating,
                            releaseYear = vnData.releaseYear ?: latest.releaseYear,
                            airStatus = vnData.airStatus ?: latest.airStatus,
                            studioOrNetwork = vnData.studioOrNetwork ?: latest.studioOrNetwork,
                            platforms = vnData.platformsSummary ?: latest.platforms,
                            clearartUrl = vnData.clearLogoUrl ?: latest.clearartUrl,
                            communityScore = vnData.communityScore ?: latest.communityScore,
                            metacriticScore = vnData.metacriticScore ?: latest.metacriticScore,
                            synopsis = latest.synopsis?.takeIf { it.isNotBlank() } ?: vnData.synopsis,
                            bannerUrl = if (shouldUpgradeBanner) (allowedVnBanner ?: latest.bannerUrl) else latest.bannerUrl
                        )
                        if (isStillCurrentItem() && updated != latest) {
                            repository.updateMedia(updated)
                        }
                        if (isStillCurrentItem()) {
                            autoSeedVnRoutesIfEmpty(latest.id, vnData.characters)
                        }
                    } else if (isStillCurrentItem()) {
                        val currentPreview = _previewItem.value
                        if (currentPreview != null) {
                            val shouldUpgradePreviewBanner = currentPreview.bannerUrl.isNullOrBlank() ||
                                (currentPreview.bannerUrl.contains("t.vndb.org/sf/") && !vnData.bannerUrl.isNullOrBlank() && !vnData.bannerUrl.contains("t.vndb.org/sf/"))
                            val updatedPreview = currentPreview.copy(
                                title = resolveUpdatedTitle(currentPreview.title),
                                vndbId = vnData.resolvedVndbId ?: currentPreview.vndbId,
                                bangumiId = vnData.resolvedBangumiId ?: currentPreview.bangumiId,
                                nativeTitle = currentPreview.nativeTitle ?: vnData.nativeTitle,
                                originCountry = vnData.originCountry ?: currentPreview.originCountry,
                                runtimeMinutes = vnData.runtimeMinutes ?: currentPreview.runtimeMinutes,
                                contentRating = vnData.contentRating ?: currentPreview.contentRating,
                                releaseYear = vnData.releaseYear ?: currentPreview.releaseYear,
                                airStatus = vnData.airStatus ?: currentPreview.airStatus,
                                studioOrNetwork = vnData.studioOrNetwork ?: currentPreview.studioOrNetwork,
                                platforms = vnData.platformsSummary ?: currentPreview.platforms,
                                clearartUrl = vnData.clearLogoUrl ?: currentPreview.clearartUrl,
                                communityScore = vnData.communityScore ?: currentPreview.communityScore,
                                metacriticScore = vnData.metacriticScore ?: currentPreview.metacriticScore,
                                synopsis = currentPreview.synopsis?.takeIf { it.isNotBlank() } ?: vnData.synopsis,
                                bannerUrl = if (shouldUpgradePreviewBanner) (vnData.bannerUrl ?: currentPreview.bannerUrl) else currentPreview.bannerUrl
                            )
                            _previewItem.value = updatedPreview
                            _previewStack.update { stack ->
                                if (stack.isNotEmpty()) stack.dropLast(1) + updatedPreview else listOf(updatedPreview)
                            }
                        }
                    }
                }
                markTransitionAuxReady(auxGen)
                persistAuxCache()
                return@launch
            }

            if (item.mediaType == MediaType.GAME) {
                val igdbClientId = preferenceManager?.igdbClientId?.firstOrNull()?.ifBlank { null }
                    ?: PreferenceManager.cachedIgdbClientId.ifBlank { null }
                val igdbClientSecret = preferenceManager?.igdbClientSecret?.firstOrNull()?.ifBlank { null }
                    ?: PreferenceManager.cachedIgdbClientSecret.ifBlank { null }
                val sgdbKey = preferenceManager?.steamGridDbApiKey?.firstOrNull()?.ifBlank { null }
                    ?: PreferenceManager.cachedSteamGridDbApiKey.ifBlank { null }
                val tokens = trackerTokenManager?.tokens?.firstOrNull()
                val steamApiKey = tokens?.steamApiKey?.ifBlank { null }
                val rawSteamId64 = tokens?.steamId64?.ifBlank { null }
                val steamId64 = if (steamApiKey != null && rawSteamId64 != null) {
                    com.omnitrack.app.data.sync.TrackerAccountVerifier.resolveSteamId(rawSteamId64, steamApiKey) ?: rawSteamId64
                } else null

                val gameData = gameEnrichmentSource.fetchFullGameData(
                    item = item,
                    igdbClientId = igdbClientId,
                    igdbClientSecret = igdbClientSecret,
                    steamGridDbApiKey = sgdbKey,
                    steamApiKey = steamApiKey,
                    steamId64 = steamId64
                )
                if (!isStillCurrentItem()) return@launch

                if (gameData != null) {
                    _gameDetailMeta.value = gameData.gameDetailMeta
                    _companies.value = gameData.companies
                    _animeRelations.value = gameData.relations
                    _recommendations.value = gameData.similarGames
                    _tags.value = gameData.tags
                    _synonyms.value = gameData.synonyms
                    _externalLinks.value = gameData.externalLinks
                    _trailers.value = gameData.trailers
                    gameData.title?.let { _englishTitle.value = it }
                    gameData.startDate?.let { _startDate.value = it }
                    gameData.runtimeMinutes?.let { _durationMinutes.value = it }
                    _format.value = "GAME"
                    if (gameData.discussionComments.isNotEmpty()) {
                        _discussionComments.value = gameData.discussionComments
                    }
                    if (!gameData.clearLogoUrl.isNullOrBlank()) {
                        EnrichmentRepository.putCachedClearart(item, gameData.clearLogoUrl)
                        _heroClearLogoUrl.value = gameData.clearLogoUrl
                    }

                    if (item.id > 0L) {
                        val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                        val excluded = latest.excludedArtSet()
                        val bannerDisabled = latest.artworkRole == "DISABLE_BANNER" || latest.customBannerPath == "NONE"
                        val allowedGameBanner = gameData.bannerUrl?.takeUnless { isArtSourceExcluded(it, excluded) }
                        val allowedGameCover = gameData.coverUrl?.takeUnless { isArtSourceExcluded(it, excluded) }
                        val shouldUpgradeBanner = !bannerDisabled && latest.customBannerPath.isNullOrBlank() &&
                            allowedGameBanner != null && (
                            latest.bannerUrl.isNullOrBlank() ||
                                (latest.bannerUrl.contains("header.jpg") && !allowedGameBanner.contains("header.jpg"))
                            )
                        val shouldUpgradeCover = latest.customCoverPath.isNullOrBlank() &&
                            (latest.coverUrl.isBlank() || latest.coverUrl.contains("placeholder")) &&
                            !allowedGameCover.isNullOrBlank()
                        val nextPlaytime = if (gameData.steamPlaytimeHours != null && gameData.steamPlaytimeHours > latest.playtimeHours) {
                            gameData.steamPlaytimeHours
                        } else latest.playtimeHours
                        val nextProgress = maxOf(latest.currentProgress, nextPlaytime)
                        val updated = latest.copy(
                            igdbId = gameData.resolvedIgdbId ?: latest.igdbId,
                            steamAppId = gameData.resolvedSteamAppId ?: latest.steamAppId,
                            synopsis = latest.synopsis?.takeIf { it.isNotBlank() } ?: gameData.synopsis,
                            coverUrl = if (shouldUpgradeCover) allowedGameCover!! else latest.coverUrl,
                            bannerUrl = if (shouldUpgradeBanner) (allowedGameBanner ?: latest.bannerUrl) else latest.bannerUrl,
                            clearartUrl = gameData.clearLogoUrl ?: latest.clearartUrl,
                            communityScore = gameData.communityScore ?: latest.communityScore,
                            metacriticScore = gameData.metacriticScore ?: latest.metacriticScore,
                            releaseYear = gameData.releaseYear ?: latest.releaseYear,
                            contentRating = gameData.contentRating ?: latest.contentRating,
                            runtimeMinutes = gameData.runtimeMinutes ?: latest.runtimeMinutes,
                            studioOrNetwork = gameData.studioOrNetwork ?: latest.studioOrNetwork,
                            platforms = latest.platforms?.takeIf { it.isNotBlank() } ?: gameData.platformsSummary,
                            steamAchievementsUnlocked = gameData.gameDetailMeta.achievementsUnlocked ?: latest.steamAchievementsUnlocked,
                            steamAchievementsTotal = gameData.gameDetailMeta.achievementsTotal ?: latest.steamAchievementsTotal,
                            playtimeHours = nextPlaytime,
                            currentProgress = nextProgress
                        )
                        if (isStillCurrentItem() && updated != latest) {
                            repository.updateMedia(updated)
                        }
                        if (isStillCurrentItem()) {
                            autoSeedGameEntriesIfEmpty(latest.id, gameData.gameDetailMeta.expansionSeeds)
                        }
                    } else if (isStillCurrentItem()) {
                        val currentPreview = _previewItem.value
                        if (currentPreview != null) {
                            val shouldUpgradePreviewBanner = currentPreview.bannerUrl.isNullOrBlank() ||
                                (currentPreview.bannerUrl.contains("header.jpg") && !gameData.bannerUrl.isNullOrBlank() && !gameData.bannerUrl.contains("header.jpg"))
                            val updatedPreview = currentPreview.copy(
                                igdbId = gameData.resolvedIgdbId ?: currentPreview.igdbId,
                                steamAppId = gameData.resolvedSteamAppId ?: currentPreview.steamAppId,
                                synopsis = currentPreview.synopsis?.takeIf { it.isNotBlank() } ?: gameData.synopsis,
                                bannerUrl = if (shouldUpgradePreviewBanner) (gameData.bannerUrl ?: currentPreview.bannerUrl) else currentPreview.bannerUrl,
                                clearartUrl = gameData.clearLogoUrl ?: currentPreview.clearartUrl,
                                communityScore = gameData.communityScore ?: currentPreview.communityScore,
                                metacriticScore = gameData.metacriticScore ?: currentPreview.metacriticScore,
                                releaseYear = gameData.releaseYear ?: currentPreview.releaseYear,
                                contentRating = gameData.contentRating ?: currentPreview.contentRating,
                                runtimeMinutes = gameData.runtimeMinutes ?: currentPreview.runtimeMinutes,
                                studioOrNetwork = gameData.studioOrNetwork ?: currentPreview.studioOrNetwork,
                                platforms = currentPreview.platforms?.takeIf { it.isNotBlank() } ?: gameData.platformsSummary,
                                steamAchievementsUnlocked = gameData.gameDetailMeta.achievementsUnlocked ?: currentPreview.steamAchievementsUnlocked,
                                steamAchievementsTotal = gameData.gameDetailMeta.achievementsTotal ?: currentPreview.steamAchievementsTotal
                            )
                            _previewItem.value = updatedPreview
                            _previewStack.update { stack ->
                                if (stack.isNotEmpty()) stack.dropLast(1) + updatedPreview else listOf(updatedPreview)
                            }
                        }
                    }
                }
                markTransitionAuxReady(auxGen)
                persistAuxCache()
                return@launch
            }

            if (item.mediaType == MediaType.BOOK) {
                val effectiveTmdbKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null }
                    ?: "844dba0bfd8f3a4f3799f6130ef9e335"
                val bookData = bookEnrichmentSource.fetchFullBookData(
                    item = item,
                    hardcoverToken = com.omnitrack.app.data.sync.TrackerTokenManager.cachedHardcoverToken,
                    tmdbApiKey = effectiveTmdbKey
                )
                if (!isStillCurrentItem()) return@launch
                if (bookData != null) {
                    _bookDetailMeta.value = bookData.bookDetailMeta
                    if (bookData.tags.isNotEmpty()) _tags.value = bookData.tags
                    if (bookData.relations.isNotEmpty()) _animeRelations.value = bookData.relations
                    if (bookData.similarBooks.isNotEmpty()) _recommendations.value = bookData.similarBooks
                    if (bookData.externalLinks.isNotEmpty()) _externalLinks.value = bookData.externalLinks
                    _format.value = bookData.literaryFormLabel
                    _countryOfOrigin.value = bookData.bookDetailMeta.originalLanguage
                    if (bookData.discussionComments.isNotEmpty() && isStillCurrentItem()) {
                        _discussionComments.value = bookData.discussionComments
                    }
                    if (item.id > 0L) {
                        val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                        val excluded = latest.excludedArtSet()
                        val bannerDisabled = latest.artworkRole == "DISABLE_BANNER" || latest.customBannerPath == "NONE"
                        val allowedBookBanner = bookData.bannerUrl?.takeUnless { isArtSourceExcluded(it, excluded, "WIKIMEDIA COMMONS") }
                        val allowedBookCover = bookData.coverUrl?.takeUnless { isArtSourceExcluded(it, excluded) }
                        val nextBanner = if (!bannerDisabled && latest.customBannerPath.isNullOrBlank() && latest.bannerUrl.isNullOrBlank()) {
                            allowedBookBanner
                        } else {
                            latest.bannerUrl
                        }
                        val nextCover = latest.coverUrl.takeIf { it.isNotBlank() } ?: allowedBookCover.orEmpty()
                        val updated = latest.copy(
                            openLibraryId = latest.openLibraryId?.takeIf { it.isNotBlank() } ?: bookData.resolvedOpenLibraryId,
                            hardcoverId = latest.hardcoverId?.takeIf { it.isNotBlank() } ?: bookData.resolvedHardcoverId,
                            isbn = latest.isbn?.takeIf { it.isNotBlank() } ?: bookData.resolvedIsbn,
                            bookAuthor = latest.bookAuthor?.takeIf { it.isNotBlank() } ?: bookData.authorName,
                            bookTranslator = latest.bookTranslator?.takeIf { it.isNotBlank() } ?: bookData.translator,
                            bookEditionLabel = latest.bookEditionLabel?.takeIf { it.isNotBlank() } ?: bookData.editionName,
                            studioOrNetwork = latest.studioOrNetwork?.takeIf { it.isNotBlank() } ?: bookData.publisher ?: bookData.authorName,
                            synopsis = latest.synopsis?.takeIf { it.isNotBlank() && it.length >= (bookData.synopsis?.length ?: 0) }
                                ?: bookData.synopsis
                                ?: latest.synopsis,
                            coverUrl = nextCover,
                            bannerUrl = nextBanner,
                            releaseYear = latest.releaseYear?.takeIf { it.isNotBlank() } ?: bookData.releaseYear,
                            totalProgress = latest.totalProgress ?: bookData.totalPages?.toDouble(),
                            bookTotalAudioMinutes = latest.bookTotalAudioMinutes ?: bookData.totalAudioMinutes,
                            communityScore = bookData.communityScore ?: latest.communityScore
                        )
                        if (isStillCurrentItem() && updated != latest) {
                            repository.updateMedia(updated)
                        }
                        if (isStillCurrentItem()) {
                            autoSeedBookEntriesIfEmpty(
                                mediaId = latest.id,
                                sectionSeedTitles = bookData.sectionSeedTitles,
                                form = bookData.bookDetailMeta.literaryForm
                            )
                        }
                    } else if (isStillCurrentItem()) {
                        val currentPreview = _previewItem.value
                        if (currentPreview != null) {
                            val updatedPreview = currentPreview.copy(
                                openLibraryId = currentPreview.openLibraryId ?: bookData.resolvedOpenLibraryId,
                                hardcoverId = currentPreview.hardcoverId ?: bookData.resolvedHardcoverId,
                                isbn = currentPreview.isbn ?: bookData.resolvedIsbn,
                                bookAuthor = currentPreview.bookAuthor ?: bookData.authorName,
                                bookTranslator = currentPreview.bookTranslator ?: bookData.translator,
                                bookEditionLabel = currentPreview.bookEditionLabel ?: bookData.editionName,
                                studioOrNetwork = currentPreview.studioOrNetwork ?: bookData.publisher ?: bookData.authorName,
                                synopsis = currentPreview.synopsis?.takeIf { it.isNotBlank() } ?: bookData.synopsis,
                                coverUrl = currentPreview.coverUrl.takeIf { it.isNotBlank() } ?: bookData.coverUrl.orEmpty(),
                                bannerUrl = currentPreview.bannerUrl?.takeIf { it.isNotBlank() } ?: bookData.bannerUrl,
                                releaseYear = currentPreview.releaseYear ?: bookData.releaseYear,
                                totalProgress = currentPreview.totalProgress ?: bookData.totalPages?.toDouble(),
                                bookTotalAudioMinutes = currentPreview.bookTotalAudioMinutes ?: bookData.totalAudioMinutes,
                                communityScore = bookData.communityScore ?: currentPreview.communityScore
                            )
                            _previewItem.value = updatedPreview
                            _previewStack.update { stack ->
                                if (stack.isNotEmpty()) stack.dropLast(1) + updatedPreview else listOf(updatedPreview)
                            }
                        }
                    }
                }
                markTransitionAuxReady(auxGen)
                persistAuxCache()
                return@launch
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
                                if (!isStillCurrentItem()) return@use
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
                                    if (newCast.isNotEmpty() && isStillCurrentItem()) {
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
                                    if (newTrailers.isNotEmpty() && isStillCurrentItem()) {
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
                                    if (newRecs.isNotEmpty() && isStillCurrentItem()) {
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
                                if (compList.isNotEmpty() && isStillCurrentItem()) {
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
                                            if (collData != null && isStillCurrentItem()) {
                                                _collectionName.value = collData.name
                                                _collectionItems.value = collData.parts
                                                persistAuxCache()
                                            }
                                        }
                                    }
                                }

                                val tmdbOverview = root["overview"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { !it.equals("null", ignoreCase = true) }
                                if (!tmdbOverview.isNullOrBlank() && item.id <= 0 && isStillCurrentItem() && _previewItem.value != null) {
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
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }

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
                                    if (traktRevs.isNotEmpty() && isStillCurrentItem()) {
                                        revs.clear()
                                        revs.addAll(traktRevs)
                                        _reviews.value = revs
                                        persistAuxCache()
                                    }
                                }
                            }
                        }

                        // Fallback to TMDb Community Reviews if Trakt is empty
                        if (revs.isEmpty() && isStillCurrentItem()) {
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
                                            if (tmdbRevs.isNotEmpty() && isStillCurrentItem()) {
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
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
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
                    if (animeDetails != null && isStillCurrentItem()) {
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

                        if (animeDetails.anilistId != null && item.mediaType == MediaType.ANIME) {
                            resolvedAniListId = animeDetails.anilistId
                            if (_heroClearLogoUrl.value.isNullOrBlank()) {
                                launch(Dispatchers.IO) {
                                    val logo = enrichmentRepository.fetchAniZipClearlogo(animeDetails.anilistId, tmdbKey)
                                    if (!logo.isNullOrBlank() && isStillCurrentItem()) {
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
                        if (item.id <= 0 && _previewItem.value != null && isStillCurrentItem()) {
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
                            launch(Dispatchers.IO) {
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
                                    if (isStillCurrentItem() && updated.mediaType == MediaType.MANGA && (currentItem.anilistId == null || _realSeasons.value.isEmpty())) {
                                        launchMangaSeriesFetch(updated)
                                    }
                                }
                            }
                        }
                        persistAuxCache()
                    }

                    // Web Novel Adaptation Bridge: When CN/KR/EN Web Novels lack a direct AniList NOVEL entry or banner/characters,
                    // harvest 16:9 hero banner, character portraits, original author, and Donghua/Manhwa adaptation relations!
                    if (isStillCurrentItem() && item.mediaType == MediaType.NOVEL &&
                        (animeDetails == null || animeDetails.bannerUrl.isNullOrBlank() || animeDetails.characters.isEmpty())
                    ) {
                        val bridge = enrichmentRepository.fetchAniListAdaptationBridge(item.title, item.nativeTitle)
                        if (bridge != null && isStillCurrentItem()) {
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
                                    launch(Dispatchers.IO) {
                                        val latest = repository.getMediaById(item.id).firstOrNull()
                                        if (latest != null && latest.bannerUrl.isNullOrBlank() &&
                                            latest.artworkRole != "DISABLE_BANNER" &&
                                            latest.customBannerPath.isNullOrBlank() &&
                                            !isArtSourceExcluded(bridge.bannerUrl, latest.excludedArtSet(), "KITSU")
                                        ) {
                                            repository.updateMedia(latest.copy(bannerUrl = bridge.bannerUrl))
                                        }
                                    }
                                }
                            }
                            persistAuxCache()
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
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
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                }
            }

            if (!isStillCurrentItem()) return@launch

            _recommendations.value = recs.distinctBy { it.title }
            _reviews.value = revs.distinctBy { it.author + it.source }
            _cast.value = castMembers.distinctBy { it.name }
            _trailers.value = trailerList.distinctBy { it.youtubeKey }
            if (compList.isNotEmpty()) {
                _companies.value = compList.distinctBy { it.name }
            }
            _collectionName.value = fetchedCollName
            _collectionItems.value = fetchedCollParts
            markTransitionAuxReady(auxGen)

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
            } finally {
                markTransitionAuxReady(auxGen)
            }
        }
    }

    private fun launchMangaSeriesFetch(item: MediaItemEntity) {
        val seriesGen = activeTransitionGen
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
            } finally {
                markTransitionSeriesReady(seriesGen)
            }
        }
    }

    private fun launchNovelSeriesFetch(item: MediaItemEntity) {
        val seriesGen = activeTransitionGen
        seriesFetchJob?.cancel()
        seriesFetchJob = viewModelScope.launch(Dispatchers.IO) {
            fun isStillCurrentItem(): Boolean {
                if (!isActive) return false
                return if (item.id > 0L) {
                    _previewItem.value == null && _mediaId.value == item.id
                } else {
                    val cur = _previewItem.value
                    cur != null && cur.id == item.id && cur.title == item.title && cur.mediaType == item.mediaType
                }
            }
            fun updateNovelAuxCache() {
                if (!isStillCurrentItem()) return
                val cacheKeys = buildList {
                    DetailAuxMemoryCache.getCacheKey(item)?.let { add(it) }
                    val alId = item.anilistId
                    if (alId != null && alId > 0L) add("anilist_${item.mediaType}_$alId")
                    val mId = item.malId
                    if (mId != null && mId > 0L) add("mal_${item.mediaType}_$mId")
                }.distinct()
                cacheKeys.forEach { key ->
                    val existing = DetailAuxMemoryCache.cache[key]
                    DetailAuxMemoryCache.cache[key] = (existing ?: CachedDetailAux()).copy(
                        reviews = _reviews.value.ifEmpty { existing?.reviews.orEmpty() },
                        companies = _companies.value.ifEmpty { existing?.companies.orEmpty() },
                        animeStaff = _animeStaff.value.ifEmpty { existing?.animeStaff.orEmpty() },
                        tags = _tags.value.ifEmpty { existing?.tags.orEmpty() },
                        format = _format.value ?: existing?.format,
                        isMangaSeasonGrouped = _isMangaSeasonGrouped.value,
                        novelDetailMeta = _novelDetailMeta.value
                    )
                }
            }
            try {
                val content = enrichmentRepository.fetchNovelVolumesAndChapters(
                    item = item,
                    knownTotalChapters = item.totalProgress?.toInt(),
                    knownTotalVolumes = null
                )
                if (!isStillCurrentItem()) return@launch

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

                if (content.seasons.isNotEmpty() && isStillCurrentItem()) {
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

                if (content.publishers.isNotEmpty() && isStillCurrentItem()) {
                    _companies.update { existing ->
                        val pubEntries = content.publishers.map { pubName ->
                            NetworkProductionCompany(id = null, name = pubName, logoUrl = null, kind = "publisher")
                        }
                        (existing + pubEntries).distinctBy { it.name.lowercase().trim() }
                    }
                }

                if (isStillCurrentItem() && _animeStaff.value.isEmpty() && content.ranobeDetails?.staff?.isNotEmpty() == true) {
                    _animeStaff.value = content.ranobeDetails.staff
                }

                if (isStillCurrentItem() && _tags.value.isEmpty() && content.kitsuDetails?.categories?.isNotEmpty() == true) {
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
                        val bannerDisabled = latest.artworkRole == "DISABLE_BANNER" || latest.customBannerPath == "NONE"
                        val allowedKitsuBanner = content.kitsuDetails?.bannerUrl?.takeUnless {
                            isArtSourceExcluded(it, latest.excludedArtSet(), "KITSU")
                        }
                        val nextBanner = latest.bannerUrl ?: if (!bannerDisabled && latest.customBannerPath.isNullOrBlank()) allowedKitsuBanner else null
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
                } else if (isStillCurrentItem() && _previewItem.value != null) {
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
                updateNovelAuxCache()
                markTransitionSeriesReady(seriesGen)

                // Fetch MAL/Jikan LN reviews or Kitsu Web Novel reactions if reviews list is still empty
                if (isStillCurrentItem() && _reviews.value.isEmpty()) {
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
                    if (novelRevs.isNotEmpty() && isStillCurrentItem()) {
                        _reviews.value = novelRevs
                        updateNovelAuxCache()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                markTransitionSeriesReady(seriesGen)
            }
        }
    }

    private fun launchSeriesFetch(item: MediaItemEntity) {
        val seriesGen = activeTransitionGen
        seriesFetchJob?.cancel()
        seriesFetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
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
                            markTransitionSeriesReady(seriesGen)
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
                        markTransitionSeriesReady(seriesGen)

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
            } finally {
                markTransitionSeriesReady(seriesGen)
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
            vndbId = preview.vndbId,
            igdbId = preview.igdbId,
            steamAppId = preview.steamAppId,
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

    private fun replaceAuxStateForTarget(
        cached: CachedDetailAux?,
        fallbackLogo: String?,
        targetItem: MediaItemEntity?,
        targetId: Long
    ) {
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        val cachedLogo = cached?.heroClearLogoUrl?.takeIf {
            !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
        }
        val repoCachedLogo = targetItem?.let {
            EnrichmentRepository.getCachedClearart(it)?.takeIf { url ->
                !url.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
            }
        }
        _heroClearLogoUrl.value = cachedLogo ?: fallbackLogo ?: repoCachedLogo

        _activeDiscussionAniListId.value = null
        _activeDiscussionTag.value = null
        _activeDiscussionSort.value = "newest"
        _discussionComments.value = if ((targetItem?.mediaType == MediaType.VISUAL_NOVEL || targetItem?.mediaType == MediaType.GAME || targetItem?.mediaType == MediaType.BOOK) && cached != null) {
            cached.vnDiscussionComments
        } else {
            emptyList()
        }
        _fillerInfoMap.value = emptyMap()

        _recommendations.value = cached?.recommendations.orEmpty()
        _reviews.value = cached?.reviews.orEmpty()
        _cast.value = cached?.cast.orEmpty()
        _trailers.value = cached?.trailers.orEmpty()
        _companies.value = cached?.companies.orEmpty()
        _collectionName.value = cached?.collectionName
        _collectionItems.value = cached?.collectionItems.orEmpty()
        _animeCharacters.value = cached?.animeCharacters.orEmpty()
        _animeStaff.value = cached?.animeStaff.orEmpty()
        _animeRelations.value = cached?.animeRelations.orEmpty()
        _animeAiring.value = cached?.animeAiring
        _nextAiringEpisode.value = cached?.nextAiringEpisode
        _englishTitle.value = cached?.englishTitle
        _romajiTitle.value = cached?.romajiTitle
        _seasonAniListIds.value = cached?.seasonAniListIds.orEmpty()
        _seasonMalIds.value = cached?.seasonMalIds.orEmpty()
        _synonyms.value = cached?.synonyms.orEmpty()
        _tags.value = cached?.tags.orEmpty()
        _popularity.value = cached?.popularity
        _favourites.value = cached?.favourites
        _durationMinutes.value = cached?.durationMinutes
        _format.value = cached?.format
        _season.value = cached?.season
        _seasonYear.value = cached?.seasonYear
        _source.value = cached?.source
        _countryOfOrigin.value = cached?.countryOfOrigin
        _startDate.value = cached?.startDate
        _endDate.value = cached?.endDate
        _externalLinks.value = cached?.externalLinks.orEmpty()
        _isMangaSeasonGrouped.value = cached?.isMangaSeasonGrouped ?: false
        _novelDetailMeta.value = cached?.novelDetailMeta ?: NovelDetailMeta()
        _vnDetailMeta.value = cached?.vnDetailMeta ?: VnDetailMeta()
        _gameDetailMeta.value = cached?.gameDetailMeta ?: GameDetailMeta()
        _bookDetailMeta.value = cached?.bookDetailMeta ?: BookDetailMeta()

        val resolvedTmdb = targetItem?.tmdbId ?: (if (targetId > 0L) SeriesContentMemoryCache.resolvedTmdbIdCache[targetId] else null)
        val cachedSeasons = SeriesContentMemoryCache.findSeasons(targetItem, resolvedTmdb, targetId, targetItem?.anilistId)
        val cachedGrouped = SeriesContentMemoryCache.findEpisodesBySeason(targetItem, resolvedTmdb, targetId, targetItem?.anilistId)
        val validSeasons = cachedSeasons?.filter { it.seasonNumber > 0 || it.episodeCount > 0 }.orEmpty()
        if (validSeasons.isNotEmpty()) {
            _realSeasons.value = validSeasons
            _episodesBySeason.value = cachedGrouped.orEmpty()
            val defaultSeason = validSeasons.firstOrNull { it.seasonNumber > 0 }?.seasonNumber
                ?: validSeasons.first().seasonNumber
            _selectedSeasonNumber.value = defaultSeason
            _realEpisodes.value = cachedGrouped?.get(defaultSeason).orEmpty()
            if (!cachedGrouped.isNullOrEmpty()) {
                refreshNextAiringEpisode(targetItem)
            }
        } else {
            _realSeasons.value = emptyList()
            _episodesBySeason.value = emptyMap()
            _realEpisodes.value = emptyList()
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
        _synonyms.value = cached.synonyms
        _tags.value = cached.tags
        _popularity.value = cached.popularity
        _favourites.value = cached.favourites
        _durationMinutes.value = cached.durationMinutes
        _format.value = cached.format
        _season.value = cached.season
        _seasonYear.value = cached.seasonYear
        _source.value = cached.source
        _countryOfOrigin.value = cached.countryOfOrigin
        _startDate.value = cached.startDate
        _endDate.value = cached.endDate
        _externalLinks.value = cached.externalLinks
        _isMangaSeasonGrouped.value = cached.isMangaSeasonGrouped
        _novelDetailMeta.value = cached.novelDetailMeta
        _vnDetailMeta.value = cached.vnDetailMeta
        _gameDetailMeta.value = cached.gameDetailMeta
        _bookDetailMeta.value = cached.bookDetailMeta
        if (cached.vnDiscussionComments.isNotEmpty() && _discussionComments.value.isEmpty()) {
            _discussionComments.value = cached.vnDiscussionComments
        }
    }

    private fun restoreSeriesFromMemoryCache(targetItem: MediaItemEntity?, targetId: Long) {
        val resolvedTmdb = targetItem?.tmdbId ?: (if (targetId > 0L) SeriesContentMemoryCache.resolvedTmdbIdCache[targetId] else null)
        val cachedSeasons = SeriesContentMemoryCache.findSeasons(targetItem, resolvedTmdb, targetId, targetItem?.anilistId)
        val cachedGrouped = SeriesContentMemoryCache.findEpisodesBySeason(targetItem, resolvedTmdb, targetId, targetItem?.anilistId)
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
        val targetItem = item ?: latestRoomItemCache[targetId]
        val allowTmdbFallback = targetItem != null && (
            targetItem.mediaType == MediaType.SHOW ||
            targetItem.mediaType == MediaType.MOVIE ||
            (targetItem.mediaType == MediaType.ANIME && (targetItem.anilistId == null || targetItem.anilistId <= 0L))
        )
        return DetailAuxMemoryCache.getCacheKey(targetId)?.let { DetailAuxMemoryCache.cache[it] }
            ?: targetItem?.let { DetailAuxMemoryCache.getCacheKey(it) }?.let { DetailAuxMemoryCache.cache[it] }
            ?: targetItem?.tmdbId?.takeIf { allowTmdbFallback }?.let {
                DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getTmdbCacheKey(if (targetItem.mediaType == MediaType.MOVIE) "movie" else "tv", it)]
            }
    }

    private fun navigateToSibling(targetId: Long, immediate: MediaItemEntity?): Boolean {
        // Cancel previous in-flight asynchronous operations
        recommendationFetchJob?.cancel()
        seriesFetchJob?.cancel()
        discussionFetchJob?.cancel()
        enrichmentFetchJob?.cancel()

        skipTransitionArmForId = targetId
        lastLoadedLibraryId = targetId
        activeTransitionGen += 1L
        initialPreArmGen = 0L
        finishTransitionLoading(triggerReveal = false)

        loadedAuxKeys.clear()
        activeSeriesFetchKey = null
        rootAniListId = null
        allSeasonCommentsCache.clear()

        val targetItem = immediate ?: latestRoomItemCache[targetId]
        if (targetItem != null) {
            latestRoomItemCache[targetId] = targetItem
        }
        val cachedAux = resolveAuxCache(targetId, targetItem)
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        val immediateLogo = targetItem?.clearartUrl?.takeIf {
            !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
        }
        replaceAuxStateForTarget(cachedAux, immediateLogo, targetItem, targetId)
        _previewItem.value = null
        _mediaId.value = targetId
        updateSiblings(targetId)
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

    private fun MediaItemEntity.excludedArtSet(): Set<String> =
        excludedArtSources
            ?.split(",")
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

    private fun inferArtSourceFromUrl(url: String?, fallbackSource: String? = null): String? {
        val u = url.orEmpty().lowercase()
        return when {
            "wikimedia.org" in u || "wikipedia.org" in u -> "WIKIMEDIA COMMONS"
            "openlibrary.org" in u -> "OPEN LIBRARY"
            "hardcover" in u -> "HARDCOVER"
            "mzstatic.com" in u || "apple.com" in u -> "APPLE BOOKS"
            "googleapis.com/books" in u || "books.google" in u -> "GOOGLE BOOKS"
            "steamgriddb" in u -> "STEAMGRIDDB"
            "steamstatic.com" in u || "steampowered.com" in u -> "STEAM"
            "igdb.com" in u -> "IGDB"
            "vndb.org" in u -> "VNDB"
            "tmdb.org" in u -> "TMDB"
            "fanart.tv" in u -> "FANART.TV"
            "anilist.co" in u -> "ANILIST"
            "myanimelist.net" in u -> "MYANIMELIST"
            "kitsu" in u -> "KITSU"
            "bgmtv" in u || "bgm.tv" in u -> "BANGUMI"
            else -> fallbackSource?.trim()?.uppercase()
        }
    }

    private fun isArtSourceExcluded(
        url: String?,
        excluded: Set<String>,
        fallbackSource: String? = null
    ): Boolean {
        if (excluded.isEmpty()) return false
        if ("ALL" in excluded) return true
        val inferred = inferArtSourceFromUrl(url, fallbackSource) ?: return false
        return inferred in excluded
    }

    private fun updatePreviewEntity(transform: (MediaItemEntity) -> MediaItemEntity) {
        val current = _previewItem.value ?: return
        val updated = transform(current)
        _previewItem.value = updated
        _previewStack.update { stack ->
            if (stack.isNotEmpty()) stack.dropLast(1) + updated else listOf(updated)
        }
    }

    private fun deleteLocalArtFileIfNeeded(path: String?) {
        if (path.isNullOrBlank() || path == "NONE" || path.startsWith("http://") || path.startsWith("https://")) return
        try {
            java.io.File(path).delete()
        } catch (_: Exception) {
        }
    }

    fun updateCover(newCoverUrl: String) {
        viewModelScope.launch {
            val item = _previewItem.value ?: uiState.value.item ?: return@launch
            if (!item.customCoverPath.isNullOrBlank() && item.customCoverPath != newCoverUrl) {
                deleteLocalArtFileIfNeeded(item.customCoverPath)
            }
            val nextRole = if (item.artworkRole == "BANNER_AS_COVER") "DEFAULT" else item.artworkRole
            if (item.id > 0) {
                val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                repository.updateMedia(
                    latest.copy(
                        coverUrl = newCoverUrl,
                        customCoverPath = newCoverUrl,
                        artworkRole = nextRole
                    )
                )
            }
            updatePreviewEntity {
                it.copy(
                    coverUrl = newCoverUrl,
                    customCoverPath = newCoverUrl,
                    artworkRole = nextRole
                )
            }
        }
    }

    fun updateBanner(newBannerUrl: String) {
        viewModelScope.launch {
            val item = _previewItem.value ?: uiState.value.item ?: return@launch
            if (!item.customBannerPath.isNullOrBlank() && item.customBannerPath != newBannerUrl) {
                deleteLocalArtFileIfNeeded(item.customBannerPath)
            }
            val nextRole = if (item.artworkRole == "DISABLE_BANNER" || item.artworkRole == "COVER_AS_BANNER") {
                "DEFAULT"
            } else {
                item.artworkRole
            }
            if (item.id > 0) {
                val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                repository.updateMedia(
                    latest.copy(
                        bannerUrl = newBannerUrl,
                        customBannerPath = newBannerUrl,
                        artworkRole = nextRole
                    )
                )
            }
            updatePreviewEntity {
                it.copy(
                    bannerUrl = newBannerUrl,
                    customBannerPath = newBannerUrl,
                    artworkRole = nextRole
                )
            }
        }
    }

    fun updateCustomCoverPath(path: String?, context: android.content.Context? = null) {
        viewModelScope.launch {
            val item = _previewItem.value ?: uiState.value.item ?: return@launch
            if (!item.customCoverPath.isNullOrBlank() && item.customCoverPath != path) {
                deleteLocalArtFileIfNeeded(item.customCoverPath)
            }
            val nextRole = if (path != null && item.artworkRole == "BANNER_AS_COVER") "DEFAULT" else item.artworkRole
            if (item.id > 0) {
                val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                repository.updateMedia(latest.copy(customCoverPath = path, artworkRole = nextRole))
            }
            updatePreviewEntity { it.copy(customCoverPath = path, artworkRole = nextRole) }
        }
    }

    fun updateCustomBannerPath(path: String?, context: android.content.Context? = null) {
        viewModelScope.launch {
            val item = _previewItem.value ?: uiState.value.item ?: return@launch
            if (!item.customBannerPath.isNullOrBlank() && item.customBannerPath != path) {
                deleteLocalArtFileIfNeeded(item.customBannerPath)
            }
            val nextRole = when {
                path == "NONE" -> "DISABLE_BANNER"
                path != null && (item.artworkRole == "DISABLE_BANNER" || item.artworkRole == "COVER_AS_BANNER") -> "DEFAULT"
                path == null && item.artworkRole == "DISABLE_BANNER" -> "DEFAULT"
                else -> item.artworkRole
            }
            if (item.id > 0) {
                val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                repository.updateMedia(latest.copy(customBannerPath = path, artworkRole = nextRole))
            }
            updatePreviewEntity { it.copy(customBannerPath = path, artworkRole = nextRole) }
        }
    }

    fun resetCover(context: android.content.Context? = null) {
        viewModelScope.launch {
            val item = _previewItem.value ?: uiState.value.item ?: return@launch
            deleteLocalArtFileIfNeeded(item.customCoverPath)
            val nextRole = if (item.artworkRole == "BANNER_AS_COVER") "DEFAULT" else item.artworkRole
            if (item.id > 0) {
                val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                repository.updateMedia(latest.copy(customCoverPath = null, artworkRole = nextRole))
            }
            updatePreviewEntity { it.copy(customCoverPath = null, artworkRole = nextRole) }
        }
    }

    fun resetBanner(context: android.content.Context? = null) {
        viewModelScope.launch {
            val item = _previewItem.value ?: uiState.value.item ?: return@launch
            deleteLocalArtFileIfNeeded(item.customBannerPath)
            val nextRole = if (item.artworkRole == "DISABLE_BANNER" || item.artworkRole == "COVER_AS_BANNER") {
                "DEFAULT"
            } else {
                item.artworkRole
            }
            if (item.id > 0) {
                val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                repository.updateMedia(latest.copy(bannerUrl = null, customBannerPath = null, artworkRole = nextRole))
            }
            updatePreviewEntity { it.copy(bannerUrl = null, customBannerPath = null, artworkRole = nextRole) }
        }
    }

    fun toggleDisableBanner() {
        viewModelScope.launch {
            val item = _previewItem.value ?: uiState.value.item ?: return@launch
            val currentlyDisabled = item.artworkRole == "DISABLE_BANNER" || item.customBannerPath == "NONE"
            val newRole = if (currentlyDisabled) "DEFAULT" else "DISABLE_BANNER"
            val newCustomBanner = if (currentlyDisabled) {
                item.customBannerPath?.takeUnless { it == "NONE" }
            } else {
                "NONE"
            }
            if (item.id > 0) {
                val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                repository.updateMedia(latest.copy(artworkRole = newRole, customBannerPath = newCustomBanner))
            }
            updatePreviewEntity { it.copy(artworkRole = newRole, customBannerPath = newCustomBanner) }
        }
    }

    fun updateArtworkRole(role: String) {
        viewModelScope.launch {
            val item = _previewItem.value ?: uiState.value.item ?: return@launch
            val normalizedBannerPath = when {
                role == "DISABLE_BANNER" -> "NONE"
                item.customBannerPath == "NONE" -> null
                else -> item.customBannerPath
            }
            if (item.id > 0) {
                val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                repository.updateMedia(latest.copy(artworkRole = role, customBannerPath = normalizedBannerPath))
            }
            updatePreviewEntity { it.copy(artworkRole = role, customBannerPath = normalizedBannerPath) }
        }
    }

    fun updateExcludedArtSources(sources: Set<String>) {
        viewModelScope.launch {
            val item = _previewItem.value ?: uiState.value.item ?: return@launch
            val serialized = sources
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .joinToString(",")
                .takeIf { it.isNotBlank() }
            if (item.id > 0) {
                val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                repository.updateMedia(latest.copy(excludedArtSources = serialized))
            }
            updatePreviewEntity { it.copy(excludedArtSources = serialized) }
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
            latestVnRoutesCache.remove(item.id)
            latestGameEntriesCache.remove(item.id)
            repository.deleteMedia(item)
        }
    }

    private suspend fun autoSeedVnRoutesIfEmpty(
        mediaId: Long,
        characters: List<AnimeCharacterItem>
    ) {
        if (mediaId <= 0L) return
        if (!vnAutoSeededMediaIds.add(mediaId)) return
        val existing = repository.getVnRoutesOnce(mediaId)
        if (existing.isNotEmpty()) return

        val seeded = mutableListOf<VnRouteEntity>()
        var order = 0
        seeded.add(
            VnRouteEntity(
                mediaId = mediaId,
                routeName = "Common Route",
                routeType = "COMMON",
                sortOrder = order++
            )
        )
        characters.filter { it.characterRole == "MAIN" }.take(6).forEach { heroine ->
            seeded.add(
                VnRouteEntity(
                    mediaId = mediaId,
                    routeName = "${heroine.characterName} Route",
                    routeType = "HEROINE",
                    characterId = heroine.vndbCharacterId,
                    characterImageUrl = heroine.characterImageUrl,
                    isSpoiler = heroine.spoilerLevel > 0,
                    sortOrder = order++
                )
            )
        }
        seeded.add(
            VnRouteEntity(
                mediaId = mediaId,
                routeName = "True Ending",
                routeType = "TRUE",
                isSpoiler = true,
                sortOrder = order
            )
        )
        repository.insertVnRoutes(seeded)
    }

    fun stepVnPlaytime(deltaHours: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val next = kotlin.math.round(((latest.playtimeHours + deltaHours).coerceAtLeast(0.0)) * 10.0) / 10.0
            val nextStatus = if (next > 0.0 && latest.status == TrackingStatus.PLAN_TO_TRACK && latest.autoStatusEnabled) {
                TrackingStatus.IN_PROGRESS
            } else latest.status
            repository.updateMedia(latest.copy(playtimeHours = next, currentProgress = next, status = nextStatus))
        }
    }

    fun setVnPlaytimeExact(hours: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val next = kotlin.math.round((hours.coerceAtLeast(0.0)) * 10.0) / 10.0
            val nextStatus = if (next > 0.0 && latest.status == TrackingStatus.PLAN_TO_TRACK && latest.autoStatusEnabled) {
                TrackingStatus.IN_PROGRESS
            } else latest.status
            repository.updateMedia(latest.copy(playtimeHours = next, currentProgress = next, status = nextStatus))
        }
    }

    fun toggleVnRouteCompleted(route: VnRouteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val nextDone = !route.isCompleted
            val updated = route.copy(
                isCompleted = nextDone,
                completedAtMs = if (nextDone) (route.completedAtMs ?: System.currentTimeMillis()) else null
            )
            repository.updateVnRoute(updated)
        }
    }

    fun addCustomVnRoute(routeName: String, routeType: String = "CUSTOM") {
        val clean = routeName.trim()
        if (clean.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val existing = repository.getVnRoutesOnce(mediaId)
            val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
            repository.insertVnRoute(
                VnRouteEntity(
                    mediaId = mediaId,
                    routeName = clean,
                    routeType = routeType,
                    sortOrder = nextOrder
                )
            )
        }
    }

    fun saveVnRouteDetails(route: VnRouteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val sanitized = route.copy(
                routeName = route.routeName.trim().ifBlank { "Untitled Route" },
                userNote = route.userNote?.trim()?.takeIf { it.isNotBlank() }
            )
            repository.updateVnRoute(sanitized)
        }
    }

    fun deleteVnRoute(routeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteVnRouteById(routeId)
        }
    }

    fun setCgAsHeroBanner(url: String) {
        updateBanner(url)
    }

    private suspend fun autoSeedGameEntriesIfEmpty(
        mediaId: Long,
        expansionSeeds: List<Pair<String, Long?>>
    ) {
        if (mediaId <= 0L) return
        if (!gameAutoSeededMediaIds.add(mediaId)) return
        val existing = repository.getGameEntriesOnce(mediaId)
        if (existing.isNotEmpty()) return

        val seeded = mutableListOf<GameEntryEntity>()
        var order = 0
        seeded.add(
            GameEntryEntity(
                mediaId = mediaId,
                entryName = "Base Game",
                entryType = GameEntryType.BASE_GAME.name,
                sortOrder = order++
            )
        )
        expansionSeeds.take(8).forEach { (expName, expId) ->
            seeded.add(
                GameEntryEntity(
                    mediaId = mediaId,
                    entryName = expName,
                    entryType = GameEntryType.EXPANSION.name,
                    igdbExpansionId = expId,
                    sortOrder = order++
                )
            )
        }
        repository.insertGameEntries(seeded)
    }

    fun stepGamePlaytime(deltaHours: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val next = kotlin.math.round(((latest.playtimeHours + deltaHours).coerceAtLeast(0.0)) * 10.0) / 10.0
            val nextStatus = if (next > 0.0 && latest.status == TrackingStatus.PLAN_TO_TRACK && latest.autoStatusEnabled) {
                TrackingStatus.IN_PROGRESS
            } else latest.status
            repository.updateMedia(latest.copy(playtimeHours = next, currentProgress = next, status = nextStatus))
        }
    }

    fun setGamePlaytimeExact(hours: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val next = kotlin.math.round((hours.coerceAtLeast(0.0)) * 10.0) / 10.0
            val nextStatus = if (next > 0.0 && latest.status == TrackingStatus.PLAN_TO_TRACK && latest.autoStatusEnabled) {
                TrackingStatus.IN_PROGRESS
            } else latest.status
            repository.updateMedia(latest.copy(playtimeHours = next, currentProgress = next, status = nextStatus))
        }
    }

    fun updateGameCompletionTier(tier: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val cleanTier = tier?.takeIf { it.isNotBlank() && it != GameCompletionTier.NONE.name }
            val nextStatus = if (
                cleanTier != null &&
                latest.status == TrackingStatus.PLAN_TO_TRACK &&
                latest.autoStatusEnabled
            ) {
                if (cleanTier == GameCompletionTier.COMPLETIONIST.name || cleanTier == GameCompletionTier.PLATINUM.name) {
                    TrackingStatus.COMPLETED
                } else {
                    TrackingStatus.IN_PROGRESS
                }
            } else latest.status
            repository.updateMedia(latest.copy(gameCompletionTier = cleanTier, status = nextStatus))
        }
    }

    fun toggleGameEntryCompleted(entry: GameEntryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val nextDone = !entry.isCompleted
            val nextTier = if (nextDone && entry.completionTier == GameCompletionTier.NONE.name) {
                GameCompletionTier.MAIN.name
            } else {
                entry.completionTier
            }
            val updated = entry.copy(
                isCompleted = nextDone,
                completionTier = nextTier,
                completedAtMs = if (nextDone) (entry.completedAtMs ?: System.currentTimeMillis()) else null
            )
            repository.updateGameEntry(updated)
        }
    }

    fun addCustomGameEntry(entryName: String, entryType: String = GameEntryType.PLAYTHROUGH.name) {
        val clean = entryName.trim()
        if (clean.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val existing = repository.getGameEntriesOnce(mediaId)
            val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
            repository.insertGameEntry(
                GameEntryEntity(
                    mediaId = mediaId,
                    entryName = clean,
                    entryType = entryType,
                    sortOrder = nextOrder
                )
            )
        }
    }

    fun saveGameEntryDetails(entry: GameEntryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val sanitized = entry.copy(
                entryName = entry.entryName.trim().ifBlank { "Untitled Entry" },
                difficulty = entry.difficulty?.trim()?.takeIf { it.isNotBlank() },
                platform = entry.platform?.trim()?.takeIf { it.isNotBlank() },
                userNote = entry.userNote?.trim()?.takeIf { it.isNotBlank() }
            )
            repository.updateGameEntry(sanitized)
        }
    }

    fun deleteGameEntry(entryId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteGameEntryById(entryId)
        }
    }

    fun updateManualAchievements(unlocked: Int, total: Int) {
        val safeTotal = total.coerceAtLeast(0)
        val safeUnlocked = unlocked.coerceIn(0, safeTotal.coerceAtLeast(unlocked))
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            repository.updateMedia(
                latest.copy(
                    steamAchievementsUnlocked = safeUnlocked,
                    steamAchievementsTotal = safeTotal
                )
            )
            _gameDetailMeta.update { meta ->
                meta.copy(
                    achievementsUnlocked = safeUnlocked,
                    achievementsTotal = safeTotal
                )
            }
        }
    }

    fun syncSteamForCurrentGame(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = uiState.value.item
            if (item == null) {
                withContext(Dispatchers.Main) { onResult("No active game selected") }
                return@launch
            }
            val tokens = trackerTokenManager?.tokens?.firstOrNull()
            val apiKey = tokens?.steamApiKey?.trim()
            val rawId = tokens?.steamId64?.trim()
            if (apiKey.isNullOrBlank() || rawId.isNullOrBlank()) {
                withContext(Dispatchers.Main) { onResult("Connect Steam API Key & SteamID64 in Tracker Accounts first") }
                return@launch
            }
            val resolvedSteamUserId = com.omnitrack.app.data.sync.TrackerAccountVerifier.resolveSteamId(rawId, apiKey) ?: rawId
            val result = gameEnrichmentSource.syncSteamStatsOnly(
                title = item.title,
                knownSteamAppId = item.steamAppId ?: _gameDetailMeta.value.steamAppId,
                steamApiKey = apiKey,
                steamId64 = resolvedSteamUserId
            )
            if (result.error != null) {
                withContext(Dispatchers.Main) { onResult(result.error) }
                return@launch
            }

            _gameDetailMeta.update { meta ->
                meta.copy(
                    steamAppId = result.steamAppId ?: meta.steamAppId,
                    achievements = if (result.achievements.isNotEmpty()) result.achievements else meta.achievements,
                    achievementsUnlocked = result.achievementsUnlocked ?: meta.achievementsUnlocked,
                    achievementsTotal = result.achievementsTotal ?: meta.achievementsTotal,
                    steamSyncedAtMs = System.currentTimeMillis()
                )
            }

            if (item.id > 0L) {
                val latest = repository.getMediaById(item.id).firstOrNull() ?: item
                val nextPlaytime = if (result.playtimeHours != null && result.playtimeHours > latest.playtimeHours) {
                    result.playtimeHours
                } else latest.playtimeHours
                val updated = latest.copy(
                    steamAppId = result.steamAppId ?: latest.steamAppId,
                    playtimeHours = nextPlaytime,
                    currentProgress = maxOf(latest.currentProgress, nextPlaytime),
                    steamAchievementsUnlocked = result.achievementsUnlocked ?: latest.steamAchievementsUnlocked,
                    steamAchievementsTotal = result.achievementsTotal ?: latest.steamAchievementsTotal
                )
                if (updated != latest) {
                    repository.updateMedia(updated)
                }
            }

            val summaryParts = buildList {
                result.playtimeHours?.let { add(String.format(java.util.Locale.US, "%.1fh playtime", it)) }
                if (result.achievementsUnlocked != null && result.achievementsTotal != null) {
                    add("${result.achievementsUnlocked}/${result.achievementsTotal} achievements")
                }
            }
            val msg = if (summaryParts.isNotEmpty()) {
                "Steam synced: ${summaryParts.joinToString(" · ")}"
            } else {
                "Steam check complete"
            }
            withContext(Dispatchers.Main) { onResult(msg) }
        }
    }

    /**
     * Generate mock season/episode data from the item's progress
     */
    private suspend fun autoSeedBookEntriesIfEmpty(
        mediaId: Long,
        sectionSeedTitles: List<String>,
        form: BookLiteraryForm?
    ) {
        if (mediaId <= 0L) return
        if (!bookAutoSeededMediaIds.add(mediaId)) return
        val existing = repository.getBookEntriesOnce(mediaId)
        if (existing.isNotEmpty()) return
        val titles = if (sectionSeedTitles.isNotEmpty()) sectionSeedTitles else listOf("Read #1")
        val seeded = titles.mapIndexed { idx, entryName ->
            val type = when {
                entryName.startsWith("Act ", ignoreCase = true) ||
                    form == BookLiteraryForm.PLAY || form == BookLiteraryForm.VERSE_PLAY -> BookEntryType.ACT.name
                entryName.startsWith("Canto ", ignoreCase = true) ||
                    "Cantos" in entryName -> BookEntryType.CANTO.name
                entryName.startsWith("Read #", ignoreCase = true) -> BookEntryType.REREAD.name
                else -> BookEntryType.BOOK_PART.name
            }
            BookEntryEntity(
                mediaId = mediaId,
                entryName = entryName,
                entryType = type,
                sortOrder = idx
            )
        }
        repository.insertBookEntries(seeded)
    }

    fun setBookIllustrationAsHeroBanner(url: String) {
        updateBanner(url)
    }

    fun stepBookProgress(delta: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val mode = BookTrackingMode.fromName(latest.bookTrackingMode)
            val updated = when (mode) {
                BookTrackingMode.AUDIO_MINUTES -> {
                    val cur = latest.bookAudioMinutes ?: 0
                    val maxMins = latest.bookTotalAudioMinutes?.takeIf { it > 0 }
                    val next = (cur + delta).let { if (maxMins != null) it.coerceIn(0, maxMins) else it.coerceAtLeast(0) }
                    val autoStatus = if (latest.autoStatusEnabled) {
                        when {
                            maxMins != null && next >= maxMins -> TrackingStatus.COMPLETED
                            next > 0 && latest.status == TrackingStatus.PLAN_TO_TRACK -> TrackingStatus.IN_PROGRESS
                            else -> latest.status
                        }
                    } else latest.status
                    latest.copy(bookAudioMinutes = next, status = autoStatus)
                }
                BookTrackingMode.PERCENT -> {
                    val total = latest.totalProgress?.takeIf { it > 0.0 }
                    val curPct = if (total != null) ((latest.currentProgress / total) * 100.0) else latest.currentProgress
                    val nextPct = (curPct + delta).coerceIn(0.0, 100.0)
                    val nextVal = if (total != null) kotlin.math.round((nextPct / 100.0) * total) else nextPct
                    val autoStatus = if (latest.autoStatusEnabled) {
                        when {
                            nextPct >= 100.0 -> TrackingStatus.COMPLETED
                            nextPct > 0.0 && latest.status == TrackingStatus.PLAN_TO_TRACK -> TrackingStatus.IN_PROGRESS
                            else -> latest.status
                        }
                    } else latest.status
                    latest.copy(currentProgress = nextVal, status = autoStatus)
                }
                BookTrackingMode.PAGES -> {
                    val total = latest.totalProgress?.takeIf { it > 0.0 }
                    val next = (latest.currentProgress + delta).let { if (total != null) it.coerceIn(0.0, total) else it.coerceAtLeast(0.0) }
                    val autoStatus = if (latest.autoStatusEnabled) {
                        when {
                            total != null && next >= total -> TrackingStatus.COMPLETED
                            next > 0.0 && latest.status == TrackingStatus.PLAN_TO_TRACK -> TrackingStatus.IN_PROGRESS
                            else -> latest.status
                        }
                    } else latest.status
                    latest.copy(currentProgress = next, status = autoStatus)
                }
            }
            repository.updateMedia(updated)
            trackerSyncEngine?.enqueueProgressUpdate(updated, updated.currentProgress)
        }
    }

    fun setBookProgressExact(value: Double, totalOverride: Double? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val mode = BookTrackingMode.fromName(latest.bookTrackingMode)
            val updated = when (mode) {
                BookTrackingMode.AUDIO_MINUTES -> {
                    val maxMins = totalOverride?.toInt()?.takeIf { it > 0 } ?: latest.bookTotalAudioMinutes?.takeIf { it > 0 }
                    val next = value.toInt().let { if (maxMins != null) it.coerceIn(0, maxMins) else it.coerceAtLeast(0) }
                    val autoStatus = if (latest.autoStatusEnabled) {
                        when {
                            maxMins != null && next >= maxMins -> TrackingStatus.COMPLETED
                            next > 0 && latest.status == TrackingStatus.PLAN_TO_TRACK -> TrackingStatus.IN_PROGRESS
                            else -> latest.status
                        }
                    } else latest.status
                    latest.copy(
                        bookAudioMinutes = next,
                        bookTotalAudioMinutes = maxMins ?: latest.bookTotalAudioMinutes,
                        status = autoStatus
                    )
                }
                BookTrackingMode.PERCENT -> {
                    val total = totalOverride?.takeIf { it > 0.0 } ?: latest.totalProgress?.takeIf { it > 0.0 }
                    val pct = value.coerceIn(0.0, 100.0)
                    val nextVal = if (total != null) kotlin.math.round((pct / 100.0) * total) else pct
                    val autoStatus = if (latest.autoStatusEnabled) {
                        when {
                            pct >= 100.0 -> TrackingStatus.COMPLETED
                            pct > 0.0 && latest.status == TrackingStatus.PLAN_TO_TRACK -> TrackingStatus.IN_PROGRESS
                            else -> latest.status
                        }
                    } else latest.status
                    latest.copy(currentProgress = nextVal, totalProgress = total ?: latest.totalProgress, status = autoStatus)
                }
                BookTrackingMode.PAGES -> {
                    val total = totalOverride?.takeIf { it > 0.0 } ?: latest.totalProgress?.takeIf { it > 0.0 }
                    val next = value.let { if (total != null) it.coerceIn(0.0, total) else it.coerceAtLeast(0.0) }
                    val autoStatus = if (latest.autoStatusEnabled) {
                        when {
                            total != null && next >= total -> TrackingStatus.COMPLETED
                            next > 0.0 && latest.status == TrackingStatus.PLAN_TO_TRACK -> TrackingStatus.IN_PROGRESS
                            else -> latest.status
                        }
                    } else latest.status
                    latest.copy(currentProgress = next, totalProgress = total ?: latest.totalProgress, status = autoStatus)
                }
            }
            repository.updateMedia(updated)
            trackerSyncEngine?.enqueueProgressUpdate(updated, updated.currentProgress)
        }
    }

    fun switchBookTrackingMode(mode: BookTrackingMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val nextTotal = if (mode == BookTrackingMode.AUDIO_MINUTES) {
                latest.bookTotalAudioMinutes
            } else null
            repository.updateMedia(
                latest.copy(
                    bookTrackingMode = mode.name,
                    bookTotalAudioMinutes = nextTotal ?: latest.bookTotalAudioMinutes
                )
            )
        }
    }

    fun updateBookFormat(format: BookFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            val meta = _bookDetailMeta.value
            val matchedEdition = meta.editions.firstOrNull { ed ->
                val fmt = ed.format.orEmpty().lowercase()
                when (format) {
                    BookFormat.HARDCOVER -> "hardcover" in fmt || "hardback" in fmt
                    BookFormat.PAPERBACK -> "paperback" in fmt || "softcover" in fmt || "mass market" in fmt || "trade" in fmt
                    BookFormat.EBOOK -> "ebook" in fmt || "e-book" in fmt || "kindle" in fmt || "epub" in fmt || "digital" in fmt
                    BookFormat.AUDIOBOOK -> "audio" in fmt
                }
            }
            fun applyFormatTo(entity: MediaItemEntity): MediaItemEntity {
                val nextMode = when (format) {
                    BookFormat.AUDIOBOOK -> BookTrackingMode.AUDIO_MINUTES.name
                    BookFormat.EBOOK -> BookTrackingMode.PERCENT.name
                    BookFormat.HARDCOVER, BookFormat.PAPERBACK -> BookTrackingMode.PAGES.name
                }
                val nextEdition = matchedEdition?.let { ed ->
                    listOfNotNull(ed.publisher?.takeIf { it.isNotBlank() }, ed.publishYear?.takeIf { it.isNotBlank() })
                        .joinToString(" · ")
                        .takeIf { it.isNotBlank() }
                        ?: ed.title
                } ?: entity.bookEditionLabel
                val nextTotal = if (format != BookFormat.AUDIOBOOK) {
                    matchedEdition?.pageCount?.takeIf { it > 0 }?.toDouble()
                        ?: entity.totalProgress
                        ?: meta.pageCount?.takeIf { it > 0 }?.toDouble()
                } else {
                    entity.totalProgress
                }
                val nextAudioMinutes = if (format == BookFormat.AUDIOBOOK) {
                    entity.bookTotalAudioMinutes ?: meta.audiobookDurationMinutes
                } else {
                    entity.bookTotalAudioMinutes
                }
                return entity.copy(
                    bookFormat = format.name,
                    bookTrackingMode = nextMode,
                    bookEditionLabel = nextEdition,
                    totalProgress = nextTotal,
                    isbn = entity.isbn?.takeIf { it.isNotBlank() } ?: matchedEdition?.isbn13,
                    bookTotalAudioMinutes = nextAudioMinutes
                )
            }

            val preview = _previewItem.value
            if (preview != null && preview.id <= 0L) {
                updatePreviewEntity(::applyFormatTo)
                return@launch
            }
            val mediaId = preview?.id?.takeIf { it > 0L } ?: ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val updated = applyFormatTo(latest)
            repository.updateMedia(updated)
            if (preview != null) {
                updatePreviewEntity { updated }
            }
        }
    }

    fun updateBookAudioMinutes(currentMinutes: Int, totalMinutes: Int? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val safeTotal = totalMinutes?.takeIf { it > 0 } ?: latest.bookTotalAudioMinutes
            val safeCur = currentMinutes.coerceAtLeast(0).let { c ->
                if (safeTotal != null && safeTotal > 0) c.coerceAtMost(safeTotal) else c
            }
            val nextStatus = if (latest.autoStatusEnabled) {
                when {
                    safeTotal != null && safeTotal > 0 && safeCur >= safeTotal -> TrackingStatus.COMPLETED
                    safeCur > 0 && latest.status == TrackingStatus.PLAN_TO_TRACK -> TrackingStatus.IN_PROGRESS
                    else -> latest.status
                }
            } else latest.status
            repository.updateMedia(
                latest.copy(
                    bookAudioMinutes = safeCur,
                    bookTotalAudioMinutes = safeTotal,
                    bookTrackingMode = BookTrackingMode.AUDIO_MINUTES.name,
                    status = nextStatus
                )
            )
        }
    }

    fun adoptBookEdition(edition: BookEditionItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val latest = repository.getMediaById(mediaId).firstOrNull() ?: return@launch
            val edFormat = when {
                edition.format?.contains("audio", true) == true -> BookFormat.AUDIOBOOK.name
                edition.format?.contains("ebook", true) == true || edition.format?.contains("kindle", true) == true -> BookFormat.EBOOK.name
                edition.format?.contains("paperback", true) == true -> BookFormat.PAPERBACK.name
                else -> latest.bookFormat
            }
            val edCover = if (latest.customCoverPath.isNullOrBlank() && !edition.coverUrl.isNullOrBlank()) {
                edition.coverUrl
            } else {
                latest.coverUrl
            }
            val updated = latest.copy(
                bookTranslator = edition.translator ?: latest.bookTranslator,
                bookEditionLabel = edition.title,
                totalProgress = edition.pageCount?.toDouble() ?: latest.totalProgress,
                isbn = edition.isbn13 ?: latest.isbn,
                bookFormat = edFormat,
                coverUrl = edCover,
                studioOrNetwork = edition.publisher ?: latest.studioOrNetwork
            )
            repository.updateMedia(updated)
            _bookDetailMeta.update { meta ->
                meta.copy(
                    publisher = edition.publisher ?: meta.publisher,
                    translator = edition.translator ?: meta.translator,
                    editionName = edition.title,
                    pageCount = edition.pageCount ?: meta.pageCount,
                    isbn13 = edition.isbn13 ?: meta.isbn13
                )
            }
        }
    }

    fun toggleBookEntryCompleted(entry: BookEntryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val nextDone = !entry.isCompleted
            val updated = entry.copy(
                isCompleted = nextDone,
                completedAtMs = if (nextDone) (entry.completedAtMs ?: System.currentTimeMillis()) else null
            )
            repository.updateBookEntry(updated)
        }
    }

    fun addCustomBookEntry(name: String, type: BookEntryType = BookEntryType.BOOK_PART) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            val existing = repository.getBookEntriesOnce(mediaId)
            val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
            repository.insertBookEntry(
                BookEntryEntity(
                    mediaId = mediaId,
                    entryName = clean,
                    entryType = type.name,
                    sortOrder = nextOrder
                )
            )
        }
    }

    fun saveBookEntryDetails(entry: BookEntryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val sanitized = entry.copy(
                entryName = entry.entryName.trim().ifBlank { "Untitled Entry" },
                translator = entry.translator?.trim()?.takeIf { it.isNotBlank() },
                editionLabel = entry.editionLabel?.trim()?.takeIf { it.isNotBlank() },
                userNote = entry.userNote?.trim()?.takeIf { it.isNotBlank() }
            )
            repository.updateBookEntry(sanitized)
        }
    }

    fun deleteBookEntry(entryId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBookEntryById(entryId)
        }
    }

    fun addBookQuote(quoteText: String, locationRef: String? = null, personalNote: String? = null) {
        val cleanQuote = quoteText.trim()
        if (cleanQuote.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val mediaId = ensureLibraryMediaId()
            if (mediaId <= 0L) return@launch
            repository.insertBookQuote(
                BookQuoteEntity(
                    mediaId = mediaId,
                    quoteText = cleanQuote,
                    locationRef = locationRef?.trim()?.takeIf { it.isNotBlank() },
                    personalNote = personalNote?.trim()?.takeIf { it.isNotBlank() }
                )
            )
        }
    }

    fun saveBookQuote(quote: BookQuoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val clean = quote.quoteText.trim()
            if (clean.isBlank()) return@launch
            repository.updateBookQuote(
                quote.copy(
                    quoteText = clean,
                    locationRef = quote.locationRef?.trim()?.takeIf { it.isNotBlank() },
                    personalNote = quote.personalNote?.trim()?.takeIf { it.isNotBlank() }
                )
            )
        }
    }

    fun deleteBookQuote(quoteId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBookQuoteById(quoteId)
        }
    }

    fun toggleBookQuoteFavorite(quote: BookQuoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateBookQuote(quote.copy(isFavorite = !quote.isFavorite))
        }
    }

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
    val seasonMalIds: Map<Int, Long> = emptyMap(),
    val heroClearLogoUrl: String? = null,
    val isMangaSeasonGrouped: Boolean = false,
    val novelDetailMeta: NovelDetailMeta = NovelDetailMeta(),
    val vnDetailMeta: VnDetailMeta = VnDetailMeta(),
    val vnDiscussionComments: List<DiscussionComment> = emptyList(),
    val gameDetailMeta: GameDetailMeta = GameDetailMeta(),
    val bookDetailMeta: BookDetailMeta = BookDetailMeta()
)

internal object DetailAuxMemoryCache {
    val cache = java.util.concurrent.ConcurrentHashMap<String, CachedDetailAux>()

    fun getCacheKey(item: MediaItemEntity): String? {
        if (item.id > 0L) return "id_${item.id}"
        if (item.mediaType == MediaType.BOOK && !item.openLibraryId.isNullOrBlank()) return "ol_${item.openLibraryId}"
        if (item.mediaType == MediaType.BOOK && !item.hardcoverId.isNullOrBlank()) return "hc_${item.hardcoverId}"
        if (item.mediaType == MediaType.GAME && item.igdbId != null && item.igdbId > 0L) return "igdb_${item.igdbId}"
        if (item.mediaType == MediaType.GAME && item.steamAppId != null && item.steamAppId > 0L) return "steam_${item.steamAppId}"
        if (!item.vndbId.isNullOrBlank()) return "vndb_${item.vndbId}"
        if (item.mediaType == MediaType.ANIME && item.anilistId != null && item.anilistId > 0L) {
            return "anilist_${item.mediaType}_${item.anilistId}"
        }
        val allowTmdbKey = item.mediaType == MediaType.SHOW || item.mediaType == MediaType.MOVIE ||
            (item.mediaType == MediaType.ANIME && (item.anilistId == null || item.anilistId <= 0L))
        if (allowTmdbKey && item.tmdbId != null && item.tmdbId > 0L) {
            return "tmdb_${if (item.mediaType == MediaType.MOVIE) "movie" else "tv"}_${item.tmdbId}"
        }
        if (item.anilistId != null && item.anilistId > 0L) return "anilist_${item.mediaType}_${item.anilistId}"
        if (item.malId != null && item.malId > 0L) return "mal_${item.mediaType}_${item.malId}"
        return null
    }

    fun getCacheKey(mediaId: Long): String? {
        return if (mediaId > 0L) "id_$mediaId" else null
    }

    fun getTmdbCacheKey(type: String, tmdbId: Long): String {
        return "tmdb_${type}_$tmdbId"
    }
}
