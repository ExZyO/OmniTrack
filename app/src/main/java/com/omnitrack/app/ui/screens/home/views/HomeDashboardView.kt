package com.omnitrack.app.ui.screens.home.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.R
import com.omnitrack.app.data.artwork.ArtworkModelResolver
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.enrichment.CalendarReleaseItem
import com.omnitrack.app.data.model.BookTrackingMode
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.model.formatBookDuration
import com.omnitrack.app.data.preferences.CachedMultiRatings
import com.omnitrack.app.data.preferences.ContinueTrackingStyle
import com.omnitrack.app.data.preferences.HomeHeroSettings
import com.omnitrack.app.data.preferences.HomeHeroSource
import com.omnitrack.app.data.preferences.HomeRowItem
import com.omnitrack.app.data.preferences.HomeRowKey
import com.omnitrack.app.data.preferences.PosterCardStyle
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.preferences.heroRatingsCacheKey
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.ui.components.FanficTypographicCover
import com.omnitrack.app.ui.components.focusedAlignment
import com.omnitrack.app.ui.components.HERO_BACKGROUND_PARALLAX
import com.omnitrack.app.ui.components.HERO_CONTENT_PARALLAX
import com.omnitrack.app.ui.components.HeroPageLayer
import com.omnitrack.app.ui.components.LocalPerformanceSettings
import com.omnitrack.app.ui.components.NuvioRailPosterCard
import com.omnitrack.app.ui.components.rememberBackdropFocusState
import com.omnitrack.app.ui.components.revealAlpha
import kotlin.math.roundToInt
import com.omnitrack.app.ui.components.glassCardDepth
import com.omnitrack.app.ui.components.heroBackgroundOverscan
import com.omnitrack.app.ui.components.heroPageForItem
import com.omnitrack.app.ui.components.heroScrollBackdropAlpha
import com.omnitrack.app.ui.components.heroScrollBackdropScale
import com.omnitrack.app.ui.components.heroScrollForegroundAlpha
import com.omnitrack.app.ui.components.heroStretchHeight
import com.omnitrack.app.ui.components.heroStretchZoom
import com.omnitrack.app.ui.components.heroVisibleLayers
import com.omnitrack.app.ui.components.homeHeroPagerGesture
import com.omnitrack.app.ui.screens.detail.EphemeralPreviewHolder
import com.omnitrack.app.ui.screens.detail.components.allowedRatingSourcesFor
import com.omnitrack.app.ui.screens.detail.components.formatSmartRating
import com.omnitrack.app.ui.screens.home.HomeViewModel
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private sealed interface HomeHeroSlide {
    data class LocalSlide(val item: MediaCardSummary) : HomeHeroSlide
    data class TrendingSlide(val item: SearchResultItem) : HomeHeroSlide
}

private fun HomeHeroSlide.slideKey(): String = when (this) {
    is HomeHeroSlide.LocalSlide -> "L_${item.id}"
    is HomeHeroSlide.TrendingSlide -> "T_${item.mediaType.name}_${item.title.lowercase()}"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeDashboardView(
    allMedia: List<MediaCardSummary>,
    trendingItems: List<SearchResultItem>,
    calendarReleases: List<CalendarReleaseItem>,
    watchedTimestamps90d: List<Long>,
    homeRows: List<HomeRowItem>,
    heroSettings: HomeHeroSettings,
    continueTrackingStyle: ContinueTrackingStyle,
    categoryDiscoverShelves: Map<HomeRowKey, List<SearchResultItem>>,
    hasUpdateAvailable: Boolean,
    onItemClick: (Long) -> Unit,
    onPreviewSearchItem: (SearchResultItem) -> Unit,
    onQuickProgress: (MediaCardSummary) -> Unit,
    onMarkCompleted: (MediaCardSummary) -> Unit,
    onSetStatus: (MediaCardSummary, TrackingStatus) -> Unit,
    onAddSearchItem: (SearchResultItem) -> Unit,
    onRequestDiscoverShelf: (HomeRowKey) -> Unit,
    onLoadMoreDiscoverShelf: (HomeRowKey) -> Unit = {},
    onOpenCalendar: () -> Unit,
    onOpenCustomizer: () -> Unit,
    onOpenHeatmap: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenUpdate: () -> Unit,
    onNavigateToLibraryCategory: (MediaType) -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier,
    homeListState: LazyListState = rememberLazyListState(),
    posterStyle: PosterCardStyle = PosterCardStyle(),
    isInitialLoading: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    fanfictionEnabled: Boolean = true,
    visibleFilterTabs: List<MediaType> = MediaType.filterTabs,
    fanficNsfwMode: com.omnitrack.app.data.model.VnNsfwFilterMode = com.omnitrack.app.data.model.VnNsfwFilterMode.BLUR_TAP,
    customCategories: List<CategoryEntity> = emptyList(),
    categoryAssignments: Map<Long, Set<Long>> = emptyMap(),
    onNavigateToCustomCategory: (Long) -> Unit = {},
    heroMultiRatingsCache: Map<String, CachedMultiRatings> = emptyMap(),
    onPrefetchLocalHeroRatings: (MediaCardSummary) -> Unit = {},
    onPrefetchSearchHeroRatings: (SearchResultItem) -> Unit = {},
    homeViewModel: HomeViewModel? = null,
    tmdbApiKey: String = ""
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val perf = LocalPerformanceSettings.current
    val heroContext = LocalContext.current
    val heroPrefManager = remember(heroContext) {
        (heroContext.applicationContext as? com.omnitrack.app.OmniTrackApplication)?.preferenceManager
    }
    val hiddenRatingSourcesMap by remember(heroPrefManager) {
        heroPrefManager?.hiddenRatingSourcesMap ?: kotlinx.coroutines.flow.flowOf(emptyMap())
    }.collectAsState(initial = emptyMap())
    var heroRefreshNonce by remember { mutableIntStateOf(0) }
    var heroSessionSeed by rememberSaveable { mutableLongStateOf(System.nanoTime() xor (System.currentTimeMillis() shl 16)) }
    var lastVisibleHeroKey by remember { mutableStateOf<String?>(null) }
    var avoidFirstHeroKeyOnRefresh by remember { mutableStateOf<String?>(null) }
    var lastHandledRefreshNonce by remember { mutableIntStateOf(0) }
    var localRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    var selectedCategory by remember { mutableStateOf(heroSettings.defaultCategory) }
    LaunchedEffect(heroSettings.defaultCategory) {
        selectedCategory = heroSettings.defaultCategory
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var actionSheetItem by remember { mutableStateOf<MediaCardSummary?>(null) }
    var showHomeSwipeQueueSheet by remember { mutableStateOf(false) }

    val watchRegionPref by remember(heroPrefManager) {
        heroPrefManager?.watchProviderRegion ?: kotlinx.coroutines.flow.flowOf(PreferenceManager.cachedWatchProviderRegion)
    }.collectAsState(initial = PreferenceManager.cachedWatchProviderRegion)
    val streamingProviders by remember(homeViewModel) {
        homeViewModel?.streamingProviders ?: MutableStateFlow(emptyList())
    }.collectAsState()
    val selectedStreamingProviderId by remember(homeViewModel) {
        homeViewModel?.selectedStreamingProviderId ?: MutableStateFlow(null)
    }.collectAsState()
    val streamingServiceTitles by remember(homeViewModel) {
        homeViewModel?.streamingServiceTitles ?: MutableStateFlow(emptyList())
    }.collectAsState()
    val isLoadingStreamingTitles by remember(homeViewModel) {
        homeViewModel?.isLoadingStreamingTitles ?: MutableStateFlow(false)
    }.collectAsState()
    val top10TodayItems by remember(homeViewModel) {
        homeViewModel?.top10TodayItems ?: MutableStateFlow(emptyList())
    }.collectAsState()
    val tasteMatchedSeed by remember(homeViewModel) {
        homeViewModel?.tasteMatchedSeed ?: MutableStateFlow(null)
    }.collectAsState()
    val tasteMatchedItems by remember(homeViewModel) {
        homeViewModel?.tasteMatchedItems ?: MutableStateFlow(emptyList())
    }.collectAsState()
    val selectedCuratedRowKey by remember(homeViewModel) {
        homeViewModel?.selectedCuratedRowKey ?: MutableStateFlow("under_90")
    }.collectAsState()
    val curatedRowItems by remember(homeViewModel) {
        homeViewModel?.curatedRowItems ?: MutableStateFlow(emptyList())
    }.collectAsState()
    val isLoadingCuratedRow by remember(homeViewModel) {
        homeViewModel?.isLoadingCuratedRow ?: MutableStateFlow(false)
    }.collectAsState()
    val dailyCriticsPick by remember(homeViewModel) {
        homeViewModel?.dailyCriticsPick ?: MutableStateFlow(null)
    }.collectAsState()
    val skippedSwipeKeys by remember(homeViewModel) {
        homeViewModel?.skippedSwipeKeys ?: MutableStateFlow(emptySet())
    }.collectAsState()

    val anyHighlightRowEnabled = remember(homeRows) {
        homeRows.any { row ->
            row.isEnabled && row.key in setOf(
                HomeRowKey.DISCOVER_TASTE_MATCHED,
                HomeRowKey.DISCOVER_TOP_10,
                HomeRowKey.DISCOVER_CRITICS_PICK,
                HomeRowKey.DISCOVER_SWIPE_QUEUE,
                HomeRowKey.DISCOVER_STREAMING,
                HomeRowKey.DISCOVER_CURATED
            )
        }
    }
    LaunchedEffect(anyHighlightRowEnabled, selectedCategory, watchRegionPref, tmdbApiKey, allMedia.size) {
        if (anyHighlightRowEnabled) {
            homeViewModel?.loadDiscoverHighlights(
                mediaFilter = selectedCategory,
                regionPref = watchRegionPref,
                tmdbApiKey = tmdbApiKey
            )
        }
    }

    val isUnlimitedShelf = heroSettings.shelfItemLimit <= 0
    val shelfLimit = if (isUnlimitedShelf) Int.MAX_VALUE else heroSettings.shelfItemLimit.coerceIn(8, 24)
    val shelfWidthInt = heroSettings.shelfPosterWidthDp.coerceIn(84, 168)
    val posterWidthDp = shelfWidthInt.dp
    val posterHeightDp = (shelfWidthInt * 1.5f).roundToInt().dp
    val coverScale = shelfWidthInt / 114f

    val visibleAllMedia = remember(allMedia, fanfictionEnabled, fanficNsfwMode) {
        allMedia.filterNot { item ->
            item.mediaType == MediaType.FANFICTION &&
                (!fanfictionEnabled || com.omnitrack.app.ui.components.shouldHideFanficItem(item.fanficRating, fanficNsfwMode))
        }
    }
    val visibleTrending = remember(trendingItems, fanfictionEnabled, fanficNsfwMode) {
        trendingItems.filterNot { item ->
            item.mediaType == MediaType.FANFICTION &&
                (!fanfictionEnabled || com.omnitrack.app.ui.components.shouldHideFanficItem(item.fanficRating, fanficNsfwMode))
        }
    }

    val categoryMedia = remember(visibleAllMedia, selectedCategory) {
        if (selectedCategory == MediaType.ALL) visibleAllMedia else visibleAllMedia.filter { it.mediaType == selectedCategory }
    }
    val categoryTrending = remember(visibleTrending, selectedCategory) {
        if (selectedCategory == MediaType.ALL) visibleTrending else visibleTrending.filter { it.mediaType == selectedCategory }
    }

    val allContinueItems = remember(categoryMedia) {
        categoryMedia.filter { it.status == TrackingStatus.IN_PROGRESS }
            .sortedByDescending { it.lastUpdated }
    }
    val allRecentItems = remember(categoryMedia) {
        categoryMedia.sortedByDescending { it.lastUpdated }
    }
    val allPlanItems = remember(categoryMedia) {
        categoryMedia.filter { it.status == TrackingStatus.PLAN_TO_TRACK }
            .sortedByDescending { it.lastUpdated }
    }
    val allFavoriteItems = remember(categoryMedia) {
        categoryMedia
            .filter { (it.userRating ?: it.communityScore ?: 0f) > 0f }
            .sortedWith(
                compareByDescending<MediaCardSummary> { it.userRating ?: 0f }
                    .thenByDescending { it.communityScore ?: 0f }
            )
    }

    val continueItems = remember(allContinueItems, shelfLimit) {
        if (shelfLimit == Int.MAX_VALUE) allContinueItems else allContinueItems.take(shelfLimit)
    }
    val recentItems = remember(allRecentItems, shelfLimit) {
        if (shelfLimit == Int.MAX_VALUE) allRecentItems else allRecentItems.take(shelfLimit)
    }
    val planItems = remember(allPlanItems, shelfLimit) {
        if (shelfLimit == Int.MAX_VALUE) allPlanItems else allPlanItems.take(shelfLimit)
    }
    val favoriteItems = remember(allFavoriteItems, shelfLimit) {
        if (shelfLimit == Int.MAX_VALUE) allFavoriteItems else allFavoriteItems.take(shelfLimit)
    }
    val completedItems = remember(categoryMedia, shelfLimit) {
        val list = categoryMedia.filter { it.status == TrackingStatus.COMPLETED }.sortedByDescending { it.lastUpdated }
        if (shelfLimit == Int.MAX_VALUE) list else list.take(shelfLimit)
    }
    val onHoldItems = remember(categoryMedia, shelfLimit) {
        val list = categoryMedia.filter { it.status == TrackingStatus.ON_HOLD }.sortedByDescending { it.lastUpdated }
        if (shelfLimit == Int.MAX_VALUE) list else list.take(shelfLimit)
    }
    val droppedItems = remember(categoryMedia, shelfLimit) {
        val list = categoryMedia.filter { it.status == TrackingStatus.DROPPED }.sortedByDescending { it.lastUpdated }
        if (shelfLimit == Int.MAX_VALUE) list else list.take(shelfLimit)
    }

    val upNextReleases = remember(calendarReleases, shelfLimit) {
        val nowSec = System.currentTimeMillis() / 1000L
        val upcoming = calendarReleases.filter { it.airingAtEpochSec >= nowSec - 3600L }
        val libFirst = upcoming.filter { it.localMediaId != null }
        val others = upcoming.filter { it.localMediaId == null }
        val combined = libFirst + others
        if (shelfLimit == Int.MAX_VALUE) combined else combined.take(shelfLimit)
    }

    val heroSlides: List<HomeHeroSlide> = remember(
        heroSettings.heroSource,
        heroSettings.heroSources,
        heroSettings.heroCategoryMode,
        heroSettings.heroCategories,
        heroSettings.heroCustomCategoryIds,
        heroSettings.slideCount,
        heroSettings.slideOrder,
        heroSettings.strictMode,
        heroSettings.strictRequirements,
        heroSettings.statusFilters,
        selectedCategory,
        visibleTrending,
        visibleAllMedia,
        customCategories,
        categoryAssignments,
        heroRefreshNonce,
        heroSessionSeed
    ) {
        fun matchesHeroCategory(type: MediaType, mediaId: Long? = null): Boolean = when (heroSettings.heroCategoryMode.uppercase()) {
            "FOLLOW_HOME" -> selectedCategory == MediaType.ALL || type == selectedCategory
            "CUSTOM" -> heroSettings.heroCategories.isEmpty() || type.name in heroSettings.heroCategories
            "CUSTOM_CATEGORIES" -> {
                if (mediaId == null) false
                else {
                    val assigned = categoryAssignments[mediaId].orEmpty()
                    if (heroSettings.heroCustomCategoryIds.isNotEmpty()) {
                        assigned.any { it in heroSettings.heroCustomCategoryIds }
                    } else {
                        val visibleCatIds = customCategories.filter { it.isVisible }.map { it.id }.toSet()
                        assigned.any { it in visibleCatIds }
                    }
                }
            }
            else -> true // "ALL"
        }

        fun findLocalMatch(s: SearchResultItem): MediaCardSummary? = ArtworkModelResolver.findLibraryMatch(
            library = allMedia,
            mediaType = s.mediaType,
            anilistId = s.anilistId,
            malId = s.malId,
            tmdbId = s.tmdbId,
            imdbId = s.imdbId,
            steamAppId = s.steamAppId,
            comicVineId = s.comicVineId,
            metronId = s.metronId,
            openLibraryId = s.openLibraryId,
            fanficWorkId = s.fanficWorkId,
            fanficSourceUrl = s.fanficSourceUrl,
            title = s.title,
            nativeTitle = s.nativeTitle
        )

        val scopedMedia = visibleAllMedia.filter { matchesHeroCategory(it.mediaType, it.id) }
        val scopedTrending = visibleTrending.filter { t ->
            if (heroSettings.heroCategoryMode.equals("CUSTOM_CATEGORIES", ignoreCase = true)) {
                val m = findLocalMatch(t)
                m != null && matchesHeroCategory(m.mediaType, m.id)
            } else {
                matchesHeroCategory(t.mediaType, null)
            }
        }

        val scopedContinue = scopedMedia.filter { it.status == TrackingStatus.IN_PROGRESS }.sortedByDescending { it.lastUpdated }
        val scopedRecent = scopedMedia.sortedByDescending { it.lastUpdated }
        val scopedPlan = scopedMedia.filter { it.status == TrackingStatus.PLAN_TO_TRACK }.sortedByDescending { it.lastUpdated }
        val scopedFavorites = scopedMedia
            .filter { (it.userRating ?: it.communityScore ?: 0f) > 0f }
            .sortedWith(compareByDescending<MediaCardSummary> { it.userRating ?: 0f }.thenByDescending { it.communityScore ?: 0f })

        fun hasAuthenticBanner(slide: HomeHeroSlide): Boolean = when (slide) {
            is HomeHeroSlide.LocalSlide -> ArtworkModelResolver.preferredBanner(slide.item) != null
            is HomeHeroSlide.TrendingSlide -> {
                val matched = findLocalMatch(slide.item)
                if (matched != null) {
                    ArtworkModelResolver.preferredBanner(matched) != null
                } else {
                    !slide.item.bannerUrl.isNullOrBlank() && slide.item.bannerUrl != slide.item.coverUrl
                }
            }
        }

        fun hasValidCover(slide: HomeHeroSlide): Boolean = when (slide) {
            is HomeHeroSlide.LocalSlide -> ArtworkModelResolver.preferredCover(slide.item).isNotBlank()
            is HomeHeroSlide.TrendingSlide -> {
                val matched = findLocalMatch(slide.item)
                if (matched != null) ArtworkModelResolver.preferredCover(matched).isNotBlank()
                else slide.item.coverUrl.isNotBlank()
            }
        }

        fun matchesFilters(slide: HomeHeroSlide): Boolean {
            if (heroSettings.statusFilters.isNotEmpty()) {
                val statusOk = when (slide) {
                    is HomeHeroSlide.LocalSlide -> slide.item.status.name in heroSettings.statusFilters
                    is HomeHeroSlide.TrendingSlide -> findLocalMatch(slide.item)?.status?.name in heroSettings.statusFilters
                }
                if (!statusOk) return false
            }
            if (heroSettings.strictMode) {
                val reqs = heroSettings.strictRequirements
                if ("banner" in reqs && !hasAuthenticBanner(slide)) return false
                if ("cover" in reqs && !hasValidCover(slide)) return false
            }
            return true
        }

        fun poolForSourceKey(sourceKey: String): List<HomeHeroSlide> = when (sourceKey.uppercase()) {
            "CONTINUE" -> scopedContinue.map { HomeHeroSlide.LocalSlide(it) }
            "FAVORITES" -> scopedFavorites.map { HomeHeroSlide.LocalSlide(it) }
            "PLAN_TO_TRACK" -> scopedPlan.map { HomeHeroSlide.LocalSlide(it) }
            "RECENT" -> scopedRecent.map { HomeHeroSlide.LocalSlide(it) }
            "TRENDING" -> scopedTrending.map { HomeHeroSlide.TrendingSlide(it) }
            else -> emptyList()
        }

        val activePools: List<List<HomeHeroSlide>> = if (heroSettings.heroSource == HomeHeroSource.ALL_MIXED) {
            val enabledKeys = heroSettings.heroSources.ifEmpty { setOf("CONTINUE", "FAVORITES", "TRENDING") }
            listOf("CONTINUE", "FAVORITES", "RECENT", "PLAN_TO_TRACK", "TRENDING")
                .filter { it in enabledKeys }
                .map { poolForSourceKey(it).filter(::matchesFilters) }
                .filter { it.isNotEmpty() }
        } else {
            listOf(poolForSourceKey(heroSettings.heroSource.name).filter(::matchesFilters))
        }

        val mergedRaw: List<HomeHeroSlide> = if (heroSettings.slideOrder.equals("INTERLEAVED", ignoreCase = true)) {
            val poolsToMix = if (activePools.size > 1) {
                activePools
            } else {
                val single = activePools.firstOrNull().orEmpty()
                single.groupBy { slide ->
                    when (slide) {
                        is HomeHeroSlide.LocalSlide -> slide.item.mediaType
                        is HomeHeroSlide.TrendingSlide -> slide.item.mediaType
                    }
                }.values.toList()
            }
            val out = mutableListOf<HomeHeroSlide>()
            val maxLen = poolsToMix.maxOfOrNull { it.size } ?: 0
            for (i in 0 until maxLen) {
                poolsToMix.forEach { pool -> if (i < pool.size) out.add(pool[i]) }
            }
            out.distinctBy { it.slideKey() }
        } else {
            activePools.flatten().distinctBy { it.slideKey() }
        }

        val resolved = if (mergedRaw.isNotEmpty()) {
            mergedRaw
        } else {
            listOf(
                scopedContinue.map { HomeHeroSlide.LocalSlide(it) },
                scopedRecent.map { HomeHeroSlide.LocalSlide(it) },
                scopedTrending.map { HomeHeroSlide.TrendingSlide(it) },
                scopedMedia.map { HomeHeroSlide.LocalSlide(it) }
            ).firstOrNull { pool ->
                pool.any(::matchesFilters)
            }?.filter(::matchesFilters)?.distinctBy { it.slideKey() }
                ?: if (heroSettings.strictMode || heroSettings.statusFilters.isNotEmpty()) emptyList()
                else scopedRecent.map { HomeHeroSlide.LocalSlide(it) }
        }

        fun List<HomeHeroSlide>.shuffleFresh(seed: Long, avoidFirstKey: String?): List<HomeHeroSlide> {
            if (size <= 1) return this
            val out = shuffled(java.util.Random(seed))
            return if (avoidFirstKey != null && out.first().slideKey() == avoidFirstKey) {
                out.drop(1) + out.take(1)
            } else {
                out
            }
        }

        val ordered = when (heroSettings.slideOrder.uppercase()) {
            "RECENT" -> {
                val base = resolved.sortedWith(
                    compareByDescending<HomeHeroSlide> { slide ->
                        when (slide) {
                            is HomeHeroSlide.LocalSlide -> slide.item.lastUpdated
                            is HomeHeroSlide.TrendingSlide -> 0L
                        }
                    }
                )
                if (heroRefreshNonce > 0) base.shuffleFresh(heroSessionSeed, avoidFirstHeroKeyOnRefresh) else base
            }
            "INTERLEAVED" -> {
                if (heroRefreshNonce > 0) resolved.shuffleFresh(heroSessionSeed, avoidFirstHeroKeyOnRefresh) else resolved
            }
            "TOP_RATED" -> {
                val base = resolved.sortedByDescending { slide ->
                    when (slide) {
                        is HomeHeroSlide.LocalSlide -> slide.item.userRating ?: slide.item.communityScore ?: 0f
                        is HomeHeroSlide.TrendingSlide -> slide.item.communityScore ?: 0f
                    }
                }
                if (heroRefreshNonce > 0) base.shuffleFresh(heroSessionSeed, avoidFirstHeroKeyOnRefresh) else base
            }
            "SHUFFLE" -> {
                resolved.shuffleFresh(
                    seed = heroSessionSeed,
                    avoidFirstKey = if (heroRefreshNonce > 0) avoidFirstHeroKeyOnRefresh else null
                )
            }
            else -> {
                if (heroRefreshNonce > 0) resolved.shuffleFresh(heroSessionSeed, avoidFirstHeroKeyOnRefresh) else resolved
            }
        }

        if (heroSettings.slideCount <= 0) ordered else ordered.take(heroSettings.slideCount.coerceIn(1, 50))
    }

    val statsSnapshot = remember(allMedia, watchedTimestamps90d) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val combinedTs = (watchedTimestamps90d + allMedia.map { it.lastUpdated }).distinct()
        val activeDates = combinedTs.mapNotNull { ts ->
            if (ts > 0L) Instant.ofEpochMilli(ts).atZone(zone).toLocalDate() else null
        }.toSet()
        var streak = 0
        var cursor = if (today in activeDates) today else today.minusDays(1)
        while (streak < 90 && cursor in activeDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        Triple(
            allMedia.count { it.status == TrackingStatus.IN_PROGRESS },
            streak,
            allMedia.count { it.status == TrackingStatus.COMPLETED }
        )
    }

    val heroSlidesState = rememberUpdatedState(heroSlides)
    val heroSettingsState = rememberUpdatedState(heroSettings)
    val allMediaState = rememberUpdatedState(allMedia)
    val heroRatingsCacheState = rememberUpdatedState(heroMultiRatingsCache)
    val isRefreshingState = rememberUpdatedState(isRefreshing)
    val upNextReleasesState = rememberUpdatedState(upNextReleases)

    PullToRefreshBox(
        isRefreshing = isRefreshing || localRefreshing,
        state = pullState,
        onRefresh = {
            localRefreshing = true
            avoidFirstHeroKeyOnRefresh = lastVisibleHeroKey
            heroSessionSeed = System.nanoTime() xor ((heroRefreshNonce + 1).toLong() * 2654435761L)
            heroRefreshNonce++
            onRefresh()
            scope.launch {
                delay(550L)
                localRefreshing = false
            }
        },
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = isRefreshing || localRefreshing,
                containerColor = DarkSurfaceElevated,
                color = AccentGold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 52.dp)
            )
        },
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        LazyColumn(
            state = homeListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 118.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            if (heroSettings.showHero) {
                item(key = "home_hero_carousel") {
                    val activeHeroSlides = heroSlidesState.value
                    val activeHeroSettings = heroSettingsState.value
                    val activeAllMedia = allMediaState.value
                    val activeHeroRatingsCache = heroRatingsCacheState.value
                    val activeIsRefreshing = isRefreshingState.value
                    val heroHeightDp = when (activeHeroSettings.heroHeightPreset.uppercase()) {
                        "COMPACT" -> 336.dp
                        "TALL" -> 472.dp
                        else -> 404.dp
                    }

                    if (activeHeroSlides.isNotEmpty()) {
                        val animateHero = !perf.disableCardAnimations
                        val density = LocalDensity.current
                        val heroHeightPx = with(density) { heroHeightDp.toPx() }
                        val heroScrollPx: () -> Float = {
                            if (!animateHero) 0f
                            else if (homeListState.firstVisibleItemIndex > 0) heroHeightPx.coerceAtLeast(1400f)
                            else homeListState.firstVisibleItemScrollOffset.toFloat()
                        }
                        val pullStretchPx: () -> Float = {
                            if (
                                !animateHero ||
                                activeIsRefreshing ||
                                localRefreshing ||
                                homeListState.firstVisibleItemIndex > 0 ||
                                homeListState.firstVisibleItemScrollOffset > 0
                            ) {
                                0f
                            } else {
                                (pullState.distanceFraction.coerceIn(0f, 1.2f) * 56f * density.density)
                                    .coerceIn(0f, 72f * density.density)
                            }
                        }
                        val isLooping = activeHeroSlides.size > 1
                        val virtualCount = if (isLooping) 100_000 else activeHeroSlides.size
                        val startIndex = remember(activeHeroSlides.size) {
                            if (isLooping) {
                                val mid = 50_000
                                mid - (mid % activeHeroSlides.size)
                            } else 0
                        }
                        val heroKeys = remember(activeHeroSlides) { activeHeroSlides.map { it.slideKey() } }
                        val pagerState = androidx.compose.runtime.key(isLooping, activeHeroSlides.size) {
                            rememberPagerState(
                                initialPage = startIndex,
                                pageCount = { virtualCount }
                            )
                        }
                        var isDraggingHero by remember { mutableStateOf(false) }
                        var lastHeroDragEndMs by remember { mutableLongStateOf(0L) }

                        LaunchedEffect(isLooping, activeHeroSlides.size) {
                            if (isLooping && pagerState.currentPage < 1000) {
                                runCatching { pagerState.scrollToPage(startIndex) }
                            }
                        }

                        LaunchedEffect(heroKeys, heroRefreshNonce) {
                            if (activeHeroSlides.isEmpty()) return@LaunchedEffect
                            if (heroRefreshNonce != lastHandledRefreshNonce) {
                                lastHandledRefreshNonce = heroRefreshNonce
                                runCatching { pagerState.scrollToPage(startIndex) }
                                return@LaunchedEffect
                            }
                            if (!isDraggingHero && !pagerState.isScrollInProgress) {
                                val preservedIdx = lastVisibleHeroKey?.let { key -> heroKeys.indexOf(key) }?.takeIf { it >= 0 }
                                if (preservedIdx != null) {
                                    val target = if (isLooping) heroPageForItem(pagerState.currentPage, preservedIdx, activeHeroSlides.size) else preservedIdx
                                    if (pagerState.currentPage != target) {
                                        runCatching { pagerState.scrollToPage(target) }
                                    }
                                } else {
                                    val curMod = if (isLooping) pagerState.currentPage % activeHeroSlides.size else 0
                                    val target = if (isLooping) startIndex + curMod else 0
                                    if (pagerState.currentPage != target) {
                                        runCatching { pagerState.scrollToPage(target) }
                                    }
                                }
                            }
                        }

                        LaunchedEffect(pagerState) {
                            snapshotFlow {
                                Triple(isDraggingHero, pagerState.isScrollInProgress, pagerState.currentPageOffsetFraction)
                            }.collect { (dragging, scrolling, offset) ->
                                if (!dragging && !scrolling && kotlin.math.abs(offset) > 0.002f) {
                                    delay(120L)
                                    if (!isDraggingHero && !pagerState.isScrollInProgress && kotlin.math.abs(pagerState.currentPageOffsetFraction) > 0.002f) {
                                        runCatching { pagerState.animateScrollToPage(pagerState.currentPage) }
                                    }
                                }
                            }
                        }

                        LaunchedEffect(pagerState, activeHeroSlides.size, activeHeroSettings.autoRotate, activeHeroSettings.autoRotateSeconds, lastHeroDragEndMs) {
                            val sec = activeHeroSettings.autoRotateSeconds
                            if (!isLooping || !activeHeroSettings.autoRotate || sec <= 0) return@LaunchedEffect
                            while (isActive) {
                                delay(sec * 1000L)
                                if (!isDraggingHero && !pagerState.isScrollInProgress) {
                                    runCatching { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                            }
                        }

                        val currentActualIndex = if (isLooping) {
                            pagerState.currentPage % activeHeroSlides.size
                        } else {
                            pagerState.currentPage.coerceIn(0, activeHeroSlides.lastIndex)
                        }

                        LaunchedEffect(currentActualIndex, activeHeroSlides, activeHeroSettings.showRatings) {
                            if (!activeHeroSettings.showRatings || activeHeroSlides.isEmpty()) return@LaunchedEffect
                            val targetIndices = if (activeHeroSlides.size > 1) {
                                listOf(currentActualIndex, (currentActualIndex + 1) % activeHeroSlides.size)
                            } else {
                                listOf(currentActualIndex)
                            }
                            targetIndices.forEach { targetIdx ->
                                when (val targetSlide = activeHeroSlides.getOrNull(targetIdx)) {
                                    is HomeHeroSlide.LocalSlide -> onPrefetchLocalHeroRatings(targetSlide.item)
                                    is HomeHeroSlide.TrendingSlide -> {
                                        val match = ArtworkModelResolver.findLibraryMatch(
                                            library = activeAllMedia,
                                            mediaType = targetSlide.item.mediaType,
                                            anilistId = targetSlide.item.anilistId,
                                            malId = targetSlide.item.malId,
                                            tmdbId = targetSlide.item.tmdbId,
                                            imdbId = targetSlide.item.imdbId,
                                            steamAppId = targetSlide.item.steamAppId,
                                            comicVineId = targetSlide.item.comicVineId,
                                            metronId = targetSlide.item.metronId,
                                            openLibraryId = targetSlide.item.openLibraryId,
                                            title = targetSlide.item.title,
                                            nativeTitle = targetSlide.item.nativeTitle
                                        )
                                        if (match != null) onPrefetchLocalHeroRatings(match)
                                        else onPrefetchSearchHeroRatings(targetSlide.item)
                                    }
                                    null -> Unit
                                }
                            }
                        }

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .homeHeroPagerGesture(
                                    pagerState = pagerState,
                                    itemCount = activeHeroSlides.size,
                                    coroutineScope = scope,
                                    onDragStateChanged = { dragging ->
                                        isDraggingHero = dragging
                                        if (!dragging) {
                                            lastHeroDragEndMs = System.currentTimeMillis()
                                        }
                                    }
                                )
                        ) {
                            val heroWidthPx = with(density) { maxWidth.toPx() }
                            val visibleLayers = if (animateHero) {
                                heroVisibleLayers(pagerState, activeHeroSlides.size)
                            } else {
                                listOf(HeroPageLayer(currentActualIndex, 1f, 0f))
                            }
                            val activeSlideIndex = visibleLayers.lastOrNull()?.itemIndex ?: currentActualIndex
                            val activeSlide = activeHeroSlides.getOrNull(activeSlideIndex)
                            val activeSlideKey = activeSlide?.slideKey()
                            LaunchedEffect(activeSlideKey) {
                                if (!activeSlideKey.isNullOrBlank()) {
                                    lastVisibleHeroKey = activeSlideKey
                                }
                            }
                            val activeLibMatch = remember(activeSlide, activeAllMedia) {
                                when (activeSlide) {
                                    is HomeHeroSlide.LocalSlide -> activeSlide.item
                                    is HomeHeroSlide.TrendingSlide -> ArtworkModelResolver.findLibraryMatch(
                                        library = activeAllMedia,
                                        mediaType = activeSlide.item.mediaType,
                                        anilistId = activeSlide.item.anilistId,
                                        malId = activeSlide.item.malId,
                                        tmdbId = activeSlide.item.tmdbId,
                                        imdbId = activeSlide.item.imdbId,
                                        steamAppId = activeSlide.item.steamAppId,
                                        comicVineId = activeSlide.item.comicVineId,
                                        metronId = activeSlide.item.metronId,
                                        openLibraryId = activeSlide.item.openLibraryId,
                                        title = activeSlide.item.title,
                                        nativeTitle = activeSlide.item.nativeTitle
                                    )
                                    null -> null
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (animateHero) Modifier.heroStretchHeight(heroHeightDp, pullStretchPx)
                                        else Modifier.height(heroHeightDp)
                                    )
                                    .clipToBounds()
                                    .clickable(
                                        enabled = activeSlide != null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (isDraggingHero || System.currentTimeMillis() - lastHeroDragEndMs <= 280L) {
                                            return@clickable
                                        }
                                        if (activeLibMatch != null) {
                                            onItemClick(activeLibMatch.id)
                                        } else if (activeSlide is HomeHeroSlide.TrendingSlide) {
                                            onPreviewSearchItem(activeSlide.item)
                                        }
                                    }
                            ) {
                                // Invisible state-driver pager (Nuvio pattern: zero sliding card seam)
                                HorizontalPager(
                                    state = pagerState,
                                    userScrollEnabled = false,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = 0.01f }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize())
                                }

                                // 1. Stacked Backdrops (seamless in-place crossfade + horizontal parallax + vertical scroll motion + pull stretch)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(heroHeightDp)
                                        .then(if (animateHero) Modifier.heroStretchZoom(pullStretchPx) else Modifier)
                                ) {
                                    visibleLayers.forEach { layer ->
                                        val slide = activeHeroSlides.getOrNull(layer.itemIndex) ?: return@forEach
                                        androidx.compose.runtime.key("${layer.itemIndex}_${slide.slideKey()}") {
                                            val resolved = rememberResolvedHomeHeroSlide(
                                                slide = slide,
                                                allMedia = activeAllMedia,
                                                heroMultiRatingsCache = activeHeroRatingsCache,
                                                heroSettings = activeHeroSettings
                                            )
                                            ChimanewHeroBackdropLayer(
                                                data = resolved,
                                                heroSettings = activeHeroSettings,
                                                visibility = layer.visibility,
                                                pageOffset = layer.offset,
                                                heroWidthPx = heroWidthPx,
                                                animateHero = animateHero,
                                                scrollOffsetPx = heroScrollPx
                                            )
                                        }
                                    }
                                }

                                // 2. Stationary Top Bar Scrim
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(92.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(AmoledBlack.copy(alpha = 0.65f), Color.Transparent)
                                            )
                                        )
                                )

                                // 3. Stationary Bottom Vignette into pure AMOLED black
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                0.0f to Color.Transparent,
                                                0.40f to Color.Transparent,
                                                0.80f to AmoledBlack.copy(alpha = 0.86f),
                                                1.0f to AmoledBlack
                                            )
                                        )
                                )

                                // 4. Stacked Foreground Content (seamless in-place crossfade + 18% horizontal parallax)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
                                    contentAlignment = Alignment.BottomStart
                                ) {
                                    visibleLayers.forEach { layer ->
                                        val slide = activeHeroSlides.getOrNull(layer.itemIndex) ?: return@forEach
                                        androidx.compose.runtime.key("${layer.itemIndex}_${slide.slideKey()}") {
                                            val resolved = rememberResolvedHomeHeroSlide(
                                                slide = slide,
                                                allMedia = activeAllMedia,
                                                heroMultiRatingsCache = activeHeroRatingsCache,
                                                heroSettings = activeHeroSettings
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .graphicsLayer {
                                                        val sPx = heroScrollPx()
                                                        alpha = layer.visibility * heroScrollForegroundAlpha(sPx, heroHeightPx)
                                                        translationX = if (animateHero) -layer.offset * heroWidthPx * HERO_CONTENT_PARALLAX else 0f
                                                        translationY = if (animateHero) -sPx * 0.10f else 0f
                                                    }
                                            ) {
                                                ChimanewHeroForegroundRow(
                                                    data = resolved,
                                                    heroSettings = activeHeroSettings,
                                                    hiddenSources = hiddenRatingSourcesMap[resolved.ratingKey] ?: emptySet()
                                                )
                                            }
                                        }
                                    }
                                }

                                // 5. Interactive Indicator Pills
                                if (isLooping && activeHeroSettings.slideCount > 0 && activeHeroSlides.size <= 15) {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 10.dp)
                                            .graphicsLayer {
                                                alpha = heroScrollForegroundAlpha(heroScrollPx(), heroHeightPx)
                                            },
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        repeat(activeHeroSlides.size) { index ->
                                            val activeFraction = visibleLayers
                                                .firstOrNull { it.itemIndex == index }
                                                ?.visibility ?: 0f
                                            Box(
                                                modifier = Modifier
                                                    .clickable {
                                                        scope.launch {
                                                            runCatching {
                                                                pagerState.animateScrollToPage(
                                                                    heroPageForItem(pagerState.currentPage, index, activeHeroSlides.size)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    .width((6f + 14f * activeFraction).dp)
                                                    .height(5.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (activeFraction > 0.5f) AccentGold.copy(alpha = 0.45f + 0.55f * activeFraction)
                                                        else Color.White.copy(alpha = 0.35f + 0.50f * activeFraction)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (!isInitialLoading) {
                        EmptyHomeHeroCard(onDiscoverClick = onNavigateToDiscover)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroHeightDp)
                                .background(AmoledBlack)
                        )
                    }
                }
            } else {
                item(key = "home_top_inset") {
                    Spacer(
                        modifier = Modifier
                            .statusBarsPadding()
                            .height(58.dp)
                    )
                }
            }

            // Customizable Ordered Home Shelves (respects globally hidden categories)
            val visibleTypesSet = visibleFilterTabs.toSet()
            homeRows.filter { row ->
                if (!row.isEnabled) return@filter false
                when (row.key) {
                    HomeRowKey.DISCOVER_ANIME -> MediaType.ANIME in visibleTypesSet
                    HomeRowKey.DISCOVER_SHOWS -> MediaType.SHOW in visibleTypesSet
                    HomeRowKey.DISCOVER_MOVIES -> MediaType.MOVIE in visibleTypesSet
                    HomeRowKey.DISCOVER_MANGA -> MediaType.MANGA in visibleTypesSet
                    HomeRowKey.DISCOVER_NOVELS -> MediaType.NOVEL in visibleTypesSet
                    HomeRowKey.DISCOVER_VNS -> MediaType.VISUAL_NOVEL in visibleTypesSet
                    HomeRowKey.DISCOVER_GAMES -> MediaType.GAME in visibleTypesSet
                    HomeRowKey.DISCOVER_BOOKS -> MediaType.BOOK in visibleTypesSet
                    HomeRowKey.DISCOVER_COMICS -> MediaType.COMIC in visibleTypesSet
                    HomeRowKey.DISCOVER_FANFICTION -> fanfictionEnabled && MediaType.FANFICTION in visibleTypesSet
                    else -> true
                }
            }.forEach { row ->
                when (row.key) {
                    HomeRowKey.CONTINUE -> {
                        if (continueItems.isNotEmpty()) {
                            item(key = "row_continue") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HomeShelfHeader(
                                        title = "Continue Tracking",
                                        actionLabel = "Library",
                                        onActionClick = { onNavigateToLibraryCategory(selectedCategory) }
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(continueItems, key = { "cont_${it.id}" }) { item ->
                                            ContinueTrackingCard(
                                                item = item,
                                                style = continueTrackingStyle,
                                                posterStyle = posterStyle,
                                                coverScale = coverScale,
                                                posterWidthDp = posterWidthDp,
                                                showQuickPlus = heroSettings.showQuickPlusOnContinue,
                                                onClick = { onItemClick(item.id) },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    actionSheetItem = item
                                                },
                                                onQuickProgress = { onQuickProgress(item) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HomeRowKey.UP_NEXT -> {
                        if (upNextReleases.isNotEmpty()) {
                            item(key = "row_up_next_${heroSettings.homeDashboardStyle}") {
                                val activeUpNext = upNextReleasesState.value
                                val activeHeroSettings = heroSettingsState.value
                                val openRelease: (CalendarReleaseItem) -> Unit = { release ->
                                    if (release.localMediaId != null) {
                                        onItemClick(release.localMediaId)
                                    } else {
                                        val safeSource = when (release.mediaType) {
                                            MediaType.COMIC -> "Metron"
                                            MediaType.SHOW -> "TVMaze"
                                            MediaType.MOVIE -> "TMDb"
                                            MediaType.MANGA -> "MangaDex"
                                            MediaType.NOVEL -> if (release.anilistId != null) "AniList" else "RanobeDB"
                                            MediaType.BOOK -> "Hardcover"
                                            MediaType.GAME -> "Steam"
                                            MediaType.VISUAL_NOVEL -> "VNDB"
                                            MediaType.FANFICTION -> "AO3"
                                            else -> "AniList"
                                        }
                                        onPreviewSearchItem(
                                            SearchResultItem(
                                                title = release.title,
                                                coverUrl = release.coverUrl,
                                                mediaType = release.mediaType,
                                                sourceProvider = safeSource,
                                                anilistId = release.anilistId,
                                                malId = release.malId,
                                                tmdbId = release.tmdbId,
                                                imdbId = release.imdbId,
                                                vndbId = release.vndbId,
                                                steamAppId = release.steamAppId,
                                                igdbId = release.igdbId,
                                                hardcoverId = release.hardcoverId,
                                                mangadexId = release.mangadexId,
                                                metronId = release.metronId,
                                                comicPublisher = if (release.mediaType == MediaType.COMIC) {
                                                    release.formatLabel.takeIf { it.isNotBlank() && it != "COMIC" }
                                                } else null
                                            )
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HomeShelfHeader(
                                        title = "Up Next & Airing",
                                        actionLabel = "Calendar",
                                        onActionClick = onOpenCalendar
                                    )
                                    if (activeHeroSettings.homeDashboardStyle.equals("COMPACT", ignoreCase = true)) {
                                        // Option C — Dense Timeline vertical hairline guide
                                        val compactSlice = remember(activeUpNext) { activeUpNext.take(5) }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp)
                                        ) {
                                            compactSlice.forEachIndexed { idx, release ->
                                                UpNextCompactRow(
                                                    release = release,
                                                    onClick = { openRelease(release) }
                                                )
                                                if (idx < compactSlice.lastIndex) {
                                                    HorizontalDivider(
                                                        color = Color.White.copy(alpha = 0.08f),
                                                        thickness = 0.6.dp
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // Option A — Frameless Editorial horizontal shelf
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 20.dp),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            items(activeUpNext, key = { "up_${it.id}" }) { release ->
                                                UpNextCalendarCard(
                                                    release = release,
                                                    onClick = { openRelease(release) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HomeRowKey.STATS -> {
                        item(key = "row_stats_${heroSettings.homeDashboardStyle}") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                HomeShelfHeader(
                                    title = "Overview",
                                    actionLabel = "90d Map",
                                    onActionClick = onOpenHeatmap
                                )
                                if (heroSettings.homeDashboardStyle.equals("COMPACT", ignoreCase = true)) {
                                    // Option C — Dense inline strip
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp)
                                            .clickable(onClick = onOpenHeatmap)
                                    ) {
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${statsSnapshot.first} Active",
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary
                                                )
                                                Text("\u00B7", fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.45f))
                                                Text(
                                                    text = "${statsSnapshot.second}d Streak",
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentGold
                                                )
                                                Text("\u00B7", fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.45f))
                                                Text(
                                                    text = "${statsSnapshot.third} Done",
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary
                                                )
                                            }
                                            Text(
                                                text = "History \u203A",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AccentGold,
                                                modifier = Modifier.clickable(onClick = onNavigateToHistory)
                                            )
                                        }
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                                    }
                                } else {
                                    // Option A — Frameless 3-column typographic hairline strip
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp)
                                            .clickable(onClick = onOpenHeatmap)
                                    ) {
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CompactStatCell(
                                                value = "${statsSnapshot.first}",
                                                label = "Active",
                                                modifier = Modifier.weight(1f)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(0.6.dp)
                                                    .height(26.dp)
                                                    .background(Color.White.copy(alpha = 0.08f))
                                            )
                                            CompactStatCell(
                                                value = "${statsSnapshot.second}d",
                                                label = "Streak",
                                                highlight = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(0.6.dp)
                                                    .height(26.dp)
                                                    .background(Color.White.copy(alpha = 0.08f))
                                            )
                                            CompactStatCell(
                                                value = "${statsSnapshot.third}",
                                                label = "Completed",
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                                    }
                                }
                            }
                        }
                    }

                    HomeRowKey.RECENT -> {
                        if (recentItems.isNotEmpty()) {
                            item(key = "row_recent") {
                                MediaSummaryRail(
                                    title = "Recently Updated",
                                    actionLabel = "View All",
                                    items = recentItems,
                                    cardWidth = posterWidthDp,
                                    cardHeight = posterHeightDp,
                                    onActionClick = { onNavigateToLibraryCategory(selectedCategory) },
                                    onItemClick = onItemClick
                                )
                            }
                        }
                    }

                    HomeRowKey.PLAN -> {
                        if (planItems.isNotEmpty()) {
                            item(key = "row_plan") {
                                MediaSummaryRail(
                                    title = TrackingStatus.PLAN_TO_TRACK.labelFor(selectedCategory),
                                    actionLabel = "Library",
                                    items = planItems,
                                    cardWidth = posterWidthDp,
                                    cardHeight = posterHeightDp,
                                    onActionClick = { onNavigateToLibraryCategory(selectedCategory) },
                                    onItemClick = onItemClick
                                )
                            }
                        }
                    }

                    HomeRowKey.FAVORITES -> {
                        if (favoriteItems.isNotEmpty()) {
                            item(key = "row_favorites") {
                                MediaSummaryRail(
                                    title = "Favorites & Top Rated",
                                    actionLabel = "Library",
                                    items = favoriteItems,
                                    cardWidth = posterWidthDp,
                                    cardHeight = posterHeightDp,
                                    onActionClick = { onNavigateToLibraryCategory(selectedCategory) },
                                    onItemClick = onItemClick
                                )
                            }
                        }
                    }

                    HomeRowKey.COMPLETED -> {
                        if (completedItems.isNotEmpty()) {
                            item(key = "row_completed") {
                                MediaSummaryRail(
                                    title = "Completed",
                                    actionLabel = "Library",
                                    items = completedItems,
                                    cardWidth = posterWidthDp,
                                    cardHeight = posterHeightDp,
                                    onActionClick = { onNavigateToLibraryCategory(selectedCategory) },
                                    onItemClick = onItemClick
                                )
                            }
                        }
                    }

                    HomeRowKey.ON_HOLD -> {
                        if (onHoldItems.isNotEmpty()) {
                            item(key = "row_on_hold") {
                                MediaSummaryRail(
                                    title = "On Hold",
                                    actionLabel = "Library",
                                    items = onHoldItems,
                                    cardWidth = posterWidthDp,
                                    cardHeight = posterHeightDp,
                                    onActionClick = { onNavigateToLibraryCategory(selectedCategory) },
                                    onItemClick = onItemClick
                                )
                            }
                        }
                    }

                    HomeRowKey.DROPPED -> {
                        if (droppedItems.isNotEmpty()) {
                            item(key = "row_dropped") {
                                MediaSummaryRail(
                                    title = "Dropped",
                                    actionLabel = "Library",
                                    items = droppedItems,
                                    cardWidth = posterWidthDp,
                                    cardHeight = posterHeightDp,
                                    onActionClick = { onNavigateToLibraryCategory(selectedCategory) },
                                    onItemClick = onItemClick
                                )
                            }
                        }
                    }

                    HomeRowKey.CUSTOM_CATEGORIES -> {
                        customCategories.filter { it.isVisible }.forEach { cat ->
                            val rawCatItems = categoryMedia.filter { cat.id in categoryAssignments[it.id].orEmpty() }
                                .sortedByDescending { it.lastUpdated }
                            val catItems = if (shelfLimit == Int.MAX_VALUE) rawCatItems else rawCatItems.take(shelfLimit)
                            if (catItems.isNotEmpty()) {
                                item(key = "row_custom_cat_${cat.id}") {
                                    MediaSummaryRail(
                                        title = cat.name,
                                        actionLabel = "Open",
                                        items = catItems,
                                        cardWidth = posterWidthDp,
                                        cardHeight = posterHeightDp,
                                        onActionClick = { onNavigateToCustomCategory(cat.id) },
                                        onItemClick = onItemClick
                                    )
                                }
                            }
                        }
                    }

                    HomeRowKey.DISCOVER_TASTE_MATCHED -> {
                        if (tasteMatchedSeed != null && tasteMatchedItems.isNotEmpty()) {
                            item(key = "row_discover_taste_matched") {
                                TasteMatchedDiscoverSection(
                                    seed = tasteMatchedSeed,
                                    items = tasteMatchedItems,
                                    allMedia = allMedia,
                                    onItemClick = onItemClick,
                                    horizontalPadding = 20.dp
                                )
                            }
                        }
                    }

                    HomeRowKey.DISCOVER_TOP_10 -> {
                        if (top10TodayItems.isNotEmpty()) {
                            item(key = "row_discover_top_10") {
                                Top10TodayDiscoverSection(
                                    items = top10TodayItems,
                                    allMedia = allMedia,
                                    onItemClick = onItemClick,
                                    horizontalPadding = 20.dp
                                )
                            }
                        }
                    }

                    HomeRowKey.DISCOVER_CRITICS_PICK -> {
                        val pick = dailyCriticsPick
                        if (pick != null) {
                            item(key = "row_discover_critics_pick") {
                                val libMatch = remember(allMedia, pick) {
                                    ArtworkModelResolver.findLibraryMatch(
                                        library = allMedia,
                                        mediaType = pick.mediaType,
                                        anilistId = pick.anilistId,
                                        malId = pick.malId,
                                        tmdbId = pick.tmdbId,
                                        openLibraryId = pick.openLibraryId,
                                        title = pick.title
                                    )
                                }
                                DailyCriticsPickCard(
                                    item = pick,
                                    libraryMatch = libMatch,
                                    onOpenDetails = {
                                        if (libMatch != null) onItemClick(libMatch.id)
                                        else onPreviewSearchItem(pick)
                                    },
                                    onAddToLibrary = { onAddSearchItem(pick) },
                                    horizontalPadding = 20.dp
                                )
                            }
                        }
                    }

                    HomeRowKey.DISCOVER_SWIPE_QUEUE -> {
                        if (homeViewModel != null) {
                            val queuePreview = remember(
                                allMedia,
                                top10TodayItems,
                                tasteMatchedItems,
                                curatedRowItems,
                                streamingServiceTitles,
                                skippedSwipeKeys
                            ) {
                                homeViewModel.buildSwipeQueue(allMedia)
                            }
                            item(key = "row_discover_swipe_queue") {
                                DiscoveryQueueLauncherCard(
                                    queueItems = queuePreview,
                                    onStartSwiping = { showHomeSwipeQueueSheet = true },
                                    horizontalPadding = 20.dp
                                )
                            }
                        }
                    }

                    HomeRowKey.DISCOVER_STREAMING -> {
                        val showStreamingOnHome = selectedCategory == MediaType.ALL ||
                            selectedCategory == MediaType.MOVIE ||
                            selectedCategory == MediaType.SHOW ||
                            selectedCategory == MediaType.ANIME
                        if (showStreamingOnHome && (streamingProviders.isNotEmpty() || streamingServiceTitles.isNotEmpty())) {
                            item(key = "row_discover_streaming") {
                                StreamingServiceDiscoverSection(
                                    providers = streamingProviders,
                                    selectedProviderId = selectedStreamingProviderId,
                                    regionPref = watchRegionPref,
                                    titles = streamingServiceTitles,
                                    isLoadingTitles = isLoadingStreamingTitles,
                                    allMedia = allMedia,
                                    onSelectProvider = { provId ->
                                        homeViewModel?.selectStreamingProvider(provId, watchRegionPref, selectedCategory, tmdbApiKey)
                                    },
                                    onSelectRegionPref = { chosen ->
                                        scope.launch { heroPrefManager?.setWatchProviderRegion(chosen) }
                                    },
                                    onItemClick = onItemClick,
                                    horizontalPadding = 20.dp
                                )
                            }
                        }
                    }

                    HomeRowKey.DISCOVER_CURATED -> {
                        val showCuratedOnHome = selectedCategory == MediaType.ALL ||
                            selectedCategory == MediaType.MOVIE ||
                            selectedCategory == MediaType.SHOW
                        if (showCuratedOnHome) {
                            item(key = "row_discover_curated") {
                                CuratedFilterDiscoverSection(
                                    selectedRowKey = selectedCuratedRowKey,
                                    items = curatedRowItems,
                                    isLoading = isLoadingCuratedRow,
                                    allMedia = allMedia,
                                    onSelectRow = { rowKey ->
                                        homeViewModel?.selectCuratedRow(rowKey, tmdbApiKey)
                                    },
                                    onItemClick = onItemClick,
                                    horizontalPadding = 20.dp
                                )
                            }
                        }
                    }

                    HomeRowKey.TRENDING -> {
                        val shownTrending = if (shelfLimit == Int.MAX_VALUE) categoryTrending else categoryTrending.take(shelfLimit)
                        if (shownTrending.isNotEmpty()) {
                            item(key = "row_trending") {
                                HomeDiscoverSearchShelf(
                                    title = "Trending Now",
                                    actionLabel = "Discover",
                                    items = shownTrending,
                                    allMedia = allMedia,
                                    cardWidth = posterWidthDp,
                                    cardHeight = posterHeightDp,
                                    onActionClick = onNavigateToDiscover,
                                    onItemClick = onItemClick,
                                    onPreviewSearchItem = onPreviewSearchItem
                                )
                            }
                        }
                    }

                    HomeRowKey.DISCOVER_ANIME,
                    HomeRowKey.DISCOVER_MANGA,
                    HomeRowKey.DISCOVER_COMICS,
                    HomeRowKey.DISCOVER_NOVELS,
                    HomeRowKey.DISCOVER_BOOKS,
                    HomeRowKey.DISCOVER_SHOWS,
                    HomeRowKey.DISCOVER_MOVIES,
                    HomeRowKey.DISCOVER_GAMES,
                    HomeRowKey.DISCOVER_VNS,
                    HomeRowKey.DISCOVER_FANFICTION -> {
                        item(key = "row_${row.key.name}") {
                            LaunchedEffect(row.key) {
                                onRequestDiscoverShelf(row.key)
                            }
                            val rawShelfItems = categoryDiscoverShelves[row.key].orEmpty().filterNot { item ->
                                item.mediaType == MediaType.FANFICTION &&
                                    com.omnitrack.app.ui.components.shouldHideFanficItem(item.fanficRating, fanficNsfwMode)
                            }
                            val shelfItems = if (shelfLimit == Int.MAX_VALUE) rawShelfItems else rawShelfItems.take(shelfLimit)
                            if (shelfItems.isEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HomeShelfHeader(
                                        title = row.key.displayName,
                                        actionLabel = "Discover",
                                        onActionClick = onNavigateToDiscover
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(4, key = { "hskel_${row.key.name}_$it" }) {
                                            Box(
                                                modifier = Modifier
                                                    .width(posterWidthDp)
                                                    .height(posterHeightDp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White.copy(alpha = 0.06f))
                                            )
                                        }
                                    }
                                }
                            } else {
                                HomeDiscoverSearchShelf(
                                    title = row.key.displayName,
                                    actionLabel = "Discover",
                                    items = shelfItems,
                                    allMedia = allMedia,
                                    cardWidth = posterWidthDp,
                                    cardHeight = posterHeightDp,
                                    onActionClick = onNavigateToDiscover,
                                    onItemClick = onItemClick,
                                    onPreviewSearchItem = onPreviewSearchItem,
                                    onLoadMore = if (isUnlimitedShelf) ({ onLoadMoreDiscoverShelf(row.key) }) else null
                                )
                            }
                        }
                    }
                }
            }
        }

        // Translucent Floating Top Header
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AmoledBlack.copy(alpha = 0.86f),
                            AmoledBlack.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.48f))
                        .clickable { categoryMenuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (selectedCategory == MediaType.ALL) "All Media" else selectedCategory.displayName,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Filter Category",
                        tint = AccentGold,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false },
                    containerColor = AmoledBlack
                ) {
                    visibleFilterTabs.filter { fanfictionEnabled || it != MediaType.FANFICTION }.forEach { type ->
                        val selected = type == selectedCategory
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (type == MediaType.ALL) "All Media" else type.displayName,
                                        color = if (selected) AccentGold else TextPrimary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.5.sp
                                    )
                                }
                            },
                            onClick = {
                                selectedCategory = type
                                categoryMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasUpdateAvailable) {
                    HeroHeaderIconButton(
                        icon = Icons.Default.SystemUpdate,
                        contentDescription = "App Update Available",
                        tint = AccentGold,
                        onClick = onOpenUpdate
                    )
                }
                HeroHeaderIconButton(
                    icon = Icons.Default.CalendarMonth,
                    contentDescription = "Release Calendar",
                    tint = TextPrimary,
                    onClick = onOpenCalendar
                )
                HeroHeaderIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextPrimary,
                    onClick = onOpenSearch
                )
                HeroHeaderIconButton(
                    icon = Icons.Default.Tune,
                    contentDescription = "Customize Home Layout",
                    tint = AccentGold,
                    onClick = onOpenCustomizer
                )
            }
        }

        // Long-press quick-action sheet for Continue Tracking cards
        val sheetTarget = actionSheetItem
        if (sheetTarget != null) {
            val coverThumb = remember(sheetTarget.customCoverPath, sheetTarget.coverUrl) {
                ArtworkModelResolver.toCoilModel(ArtworkModelResolver.preferredCover(sheetTarget))
            }
            ModalBottomSheet(
                onDismissRequest = { actionSheetItem = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = AmoledBlack,
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AmoledBlack)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(width = 38.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f))
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(64.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceCard)
                        ) {
                            if (coverThumb != null) {
                                AsyncImage(
                                    model = coverThumb,
                                    contentDescription = sheetTarget.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (sheetTarget.mediaType == MediaType.FANFICTION) {
                                FanficTypographicCover(
                                    title = sheetTarget.title,
                                    author = sheetTarget.bookAuthor,
                                    primaryFandom = sheetTarget.fanficPrimaryFandom,
                                    sourceSite = sheetTarget.fanficSourceSite,
                                    ratingCode = sheetTarget.fanficRating,
                                    wordCount = sheetTarget.fanficWordCount,
                                    isCrossover = sheetTarget.fanficIsCrossover,
                                    compact = true,
                                    isBlurred = false
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = sheetTarget.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatConciseProgress(sheetTarget).ifBlank { sheetTarget.mediaType.displayName },
                                fontSize = 12.5.sp,
                                color = AccentGold,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                    ContinueActionSheetRow(
                        icon = Icons.Default.Info,
                        label = "Go to Details",
                        onClick = {
                            actionSheetItem = null
                            onItemClick(sheetTarget.id)
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
                    ContinueActionSheetRow(
                        icon = Icons.Default.Add,
                        label = heroPrimaryCtaLabel(sheetTarget),
                        accent = true,
                        onClick = {
                            actionSheetItem = null
                            onQuickProgress(sheetTarget)
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
                    ContinueActionSheetRow(
                        icon = Icons.Default.Check,
                        label = "Mark Completed",
                        onClick = {
                            actionSheetItem = null
                            onMarkCompleted(sheetTarget)
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
                    ContinueActionSheetRow(
                        icon = Icons.Default.Pause,
                        label = "Move to On Hold",
                        onClick = {
                            actionSheetItem = null
                            onSetStatus(sheetTarget, TrackingStatus.ON_HOLD)
                        }
                    )
                }
            }
        }

        if (showHomeSwipeQueueSheet && homeViewModel != null) {
            val homeQueue = remember(
                allMedia,
                top10TodayItems,
                tasteMatchedItems,
                curatedRowItems,
                streamingServiceTitles,
                skippedSwipeKeys
            ) {
                homeViewModel.buildSwipeQueue(allMedia)
            }
            SwipeDiscoveryQueueSheet(
                queueItems = homeQueue,
                onAddPlanToWatch = { picked ->
                    onAddSearchItem(picked)
                    homeViewModel.skipSwipeQueueItem(picked)
                },
                onSkip = { skipped ->
                    homeViewModel.skipSwipeQueueItem(skipped)
                },
                onUndoSkip = { restored ->
                    homeViewModel.unskipSwipeQueueItem(restored)
                },
                onUndoAdd = { undone ->
                    homeViewModel.undoAddSwipeQueueItem(undone)
                },
                onOpenDetails = { picked ->
                    val inLib = ArtworkModelResolver.findLibraryMatch(
                        library = allMedia,
                        mediaType = picked.mediaType,
                        anilistId = picked.anilistId,
                        malId = picked.malId,
                        tmdbId = picked.tmdbId,
                        openLibraryId = picked.openLibraryId,
                        title = picked.title
                    )
                    if (inLib != null) {
                        onItemClick(inLib.id)
                    } else {
                        onPreviewSearchItem(picked)
                    }
                },
                onDismiss = { showHomeSwipeQueueSheet = false }
            )
        }
    }
}

@Composable
private fun ContinueActionSheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) AccentGold else TextPrimary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (accent) AccentGold else TextPrimary
        )
    }
}

@Composable
private fun HeroHeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.48f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun heroPrimaryCtaLabel(item: MediaCardSummary): String = when (item.mediaType) {
    MediaType.BOOK -> when (BookTrackingMode.fromName(item.bookTrackingMode)) {
        BookTrackingMode.AUDIO_MINUTES -> "+15m Listen"
        BookTrackingMode.PERCENT -> "+5% Read"
        BookTrackingMode.PAGES -> "+10 Pages"
    }
    MediaType.GAME, MediaType.VISUAL_NOVEL -> "+0.5 Hrs"
    MediaType.MOVIE -> "Mark Watched"
    else -> "+1 ${item.mediaType.unitLabel}"
}

private data class ResolvedHomeHeroSlide(
    val title: String,
    val categoryBadge: String,
    val posterModel: Any?,
    val posterFallback: (@Composable () -> Unit)? = null,
    val authenticBannerModel: Any?,
    val forceCoverBackdrop: Boolean = false,
    val bannerFocalX: Float = 0f,
    val bannerFocalY: Float = 0f,
    val bannerZoom: Float = 1f,
    val bannerBlurDp: Int = 0,
    val userRating: Float? = null,
    val communityScore: Float? = null,
    val imdbScore: Float? = null,
    val rtScore: Int? = null,
    val metacriticScore: Int? = null,
    val traktScore: Int? = null,
    val audienceScore: Int? = null,
    val cachedRatings: CachedMultiRatings? = null,
    val mediaType: MediaType = MediaType.ALL,
    val hasAnilist: Boolean = false,
    val hasMal: Boolean = false,
    val hasTmdb: Boolean = false,
    val hasVndb: Boolean = false,
    val hasBangumi: Boolean = false,
    val hasIgdb: Boolean = false,
    val hasSteam: Boolean = false,
    val progressLabel: String = "",
    val remainingCount: Int = 0,
    val ratingKey: String = ""
)

@Composable
private fun rememberResolvedHomeHeroSlide(
    slide: HomeHeroSlide,
    allMedia: List<MediaCardSummary>,
    heroMultiRatingsCache: Map<String, CachedMultiRatings>,
    heroSettings: HomeHeroSettings
): ResolvedHomeHeroSlide {
    val localItem: MediaCardSummary? = remember(slide, allMedia) {
        when (slide) {
            is HomeHeroSlide.LocalSlide -> slide.item
            is HomeHeroSlide.TrendingSlide -> ArtworkModelResolver.findLibraryMatch(
                library = allMedia,
                mediaType = slide.item.mediaType,
                anilistId = slide.item.anilistId,
                malId = slide.item.malId,
                tmdbId = slide.item.tmdbId,
                imdbId = slide.item.imdbId,
                steamAppId = slide.item.steamAppId,
                comicVineId = slide.item.comicVineId,
                metronId = slide.item.metronId,
                openLibraryId = slide.item.openLibraryId,
                title = slide.item.title,
                nativeTitle = slide.item.nativeTitle
            )
        }
    }
    return if (localItem != null) {
        val coverModel: Any? = remember(localItem.customCoverPath, localItem.coverUrl) {
            ArtworkModelResolver.toCoilModel(ArtworkModelResolver.preferredCover(localItem))
        }
        val authenticBannerModel = remember(localItem.customBannerPath, localItem.bannerUrl, localItem.artworkRole, localItem.coverUrl) {
            ArtworkModelResolver.toCoilModel(ArtworkModelResolver.preferredBanner(localItem))
        }
        val remainingCount = remember(localItem.totalProgress, localItem.currentProgress, localItem.mediaType) {
            val tot = localItem.totalProgress?.toInt() ?: 0
            val cur = localItem.currentProgress.toInt()
            if (localItem.mediaType != MediaType.GAME && localItem.mediaType != MediaType.VISUAL_NOVEL && tot > cur) {
                (tot - cur).coerceAtLeast(0)
            } else 0
        }
        val progressLabel = remember(localItem) { formatConciseProgress(localItem) }
        val cachedRatings = heroMultiRatingsCache[heroRatingsCacheKey(localItem.id, localItem.mediaType, localItem.title)]
        ResolvedHomeHeroSlide(
            title = localItem.title,
            categoryBadge = "${localItem.mediaType.displayName.uppercase()} \u00B7 ${localItem.status.labelFor(localItem.mediaType).uppercase()}",
            posterModel = coverModel,
            posterFallback = if (localItem.mediaType == MediaType.FANFICTION && coverModel == null) {
                {
                    FanficTypographicCover(
                        title = localItem.title,
                        author = localItem.bookAuthor,
                        primaryFandom = localItem.fanficPrimaryFandom,
                        sourceSite = localItem.fanficSourceSite,
                        ratingCode = localItem.fanficRating,
                        wordCount = localItem.fanficWordCount,
                        isCrossover = localItem.fanficIsCrossover,
                        compact = true,
                        isBlurred = false
                    )
                }
            } else null,
            authenticBannerModel = authenticBannerModel,
            forceCoverBackdrop = localItem.mediaType == MediaType.BOOK && !ArtworkModelResolver.isBannerDisabled(localItem),
            bannerFocalX = localItem.bannerFocalX,
            bannerFocalY = localItem.bannerFocalY,
            bannerZoom = localItem.bannerZoom,
            bannerBlurDp = heroSettings.resolveBannerBlurDp(localItem.mediaType),
            userRating = localItem.userRating,
            communityScore = localItem.communityScore,
            imdbScore = localItem.imdbScore,
            rtScore = localItem.rtScore,
            metacriticScore = localItem.metacriticScore,
            traktScore = localItem.traktScore,
            audienceScore = localItem.audienceScore,
            cachedRatings = cachedRatings,
            mediaType = localItem.mediaType,
            hasAnilist = localItem.anilistId != null,
            hasMal = localItem.malId != null,
            hasTmdb = localItem.tmdbId != null,
            hasVndb = localItem.vndbId != null,
            hasBangumi = localItem.bangumiId != null,
            hasIgdb = localItem.igdbId != null,
            hasSteam = localItem.steamAppId != null,
            progressLabel = progressLabel,
            remainingCount = remainingCount,
            ratingKey = PreferenceManager.ratingVisibilityKey(localItem)
        )
    } else {
        val item = (slide as HomeHeroSlide.TrendingSlide).item
        val coverModel: Any? = remember(item.coverUrl) { ArtworkModelResolver.toCoilModel(item.coverUrl) }
        val authenticBannerModel = remember(item.bannerUrl, item.coverUrl) {
            item.bannerUrl?.takeIf { it.isNotBlank() && it != item.coverUrl }?.let { LosslessCoverMapper.toLosslessUrl(it) }
        }
        val progressHint = remember(item) {
            listOfNotNull(item.releaseYear, item.bookAuthor).joinToString(" \u00B7 ")
        }
        val cachedRatings = heroMultiRatingsCache[heroRatingsCacheKey(null, item.mediaType, item.title)]
        ResolvedHomeHeroSlide(
            title = item.title,
            categoryBadge = "TRENDING \u00B7 ${item.mediaType.displayName.uppercase()}",
            posterModel = coverModel,
            posterFallback = if (item.mediaType == MediaType.FANFICTION && coverModel == null) {
                {
                    FanficTypographicCover(
                        title = item.title,
                        author = item.bookAuthor,
                        primaryFandom = item.fanficPrimaryFandom,
                        sourceSite = item.fanficSourceSite,
                        ratingCode = item.fanficRating,
                        wordCount = item.fanficWordCount,
                        isCrossover = item.fanficIsCrossover,
                        compact = true,
                        isBlurred = false
                    )
                }
            } else null,
            authenticBannerModel = authenticBannerModel,
            forceCoverBackdrop = item.mediaType == MediaType.BOOK,
            bannerFocalX = 0f,
            bannerFocalY = 0f,
            bannerZoom = 1f,
            bannerBlurDp = heroSettings.resolveBannerBlurDp(item.mediaType),
            userRating = null,
            communityScore = item.communityScore,
            cachedRatings = cachedRatings,
            mediaType = item.mediaType,
            hasAnilist = item.anilistId != null,
            hasMal = item.malId != null,
            hasTmdb = item.tmdbId != null,
            hasVndb = item.vndbId != null,
            hasBangumi = item.bangumiId != null,
            hasIgdb = false,
            hasSteam = item.steamAppId != null,
            progressLabel = progressHint,
            remainingCount = 0,
            ratingKey = PreferenceManager.ratingVisibilityKeyForSearch(item)
        )
    }
}

@Composable
private fun ChimanewHeroBackdropLayer(
    data: ResolvedHomeHeroSlide,
    heroSettings: HomeHeroSettings,
    visibility: Float,
    pageOffset: Float,
    heroWidthPx: Float,
    animateHero: Boolean,
    scrollOffsetPx: () -> Float
) {
    val context = LocalContext.current
    val overscanScale = if (animateHero) heroBackgroundOverscan(pageOffset) else 1f
    if (data.authenticBannerModel != null) {
        val baseAlpha = if (heroSettings.dimBanner) 0.56f else 0.94f
        val hasManualFraming = kotlin.math.abs(data.bannerFocalX) > 0.01f ||
            kotlin.math.abs(data.bannerFocalY) > 0.01f ||
            kotlin.math.abs(data.bannerZoom - 1f) > 0.01f
        val focusKey = (data.authenticBannerModel as? String) ?: (data.authenticBannerModel as? File)?.absolutePath
        val focusState = rememberBackdropFocusState(cacheKey = focusKey, enabled = !hasManualFraming)
        val bannerRequest = remember(data.authenticBannerModel, context) {
            ImageRequest.Builder(context)
                .data(data.authenticBannerModel)
                .crossfade(false)
                .build()
        }
        val effectiveAlignment = if (hasManualFraming) {
            BiasAlignment(data.bannerFocalX.coerceIn(-1f, 1f), data.bannerFocalY.coerceIn(-1f, 1f))
        } else {
            focusState.focusedAlignment(Alignment.Center)
        }
        AsyncImage(
            model = bannerRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = effectiveAlignment,
            onSuccess = { state -> if (!hasManualFraming) focusState.onImageLoaded(state.result.image) },
            onError = { focusState.onImageFailed() },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val fx = data.bannerFocalX.coerceIn(-1f, 1f)
                    val fy = data.bannerFocalY.coerceIn(-1f, 1f)
                    val effZoom = data.bannerZoom.coerceIn(1f, 2.5f)
                    val sPx = scrollOffsetPx()
                    val scrollScale = if (animateHero) heroScrollBackdropScale(sPx) else 1f
                    val totalZoom = effZoom * overscanScale * scrollScale
                    val focusAlpha = if (hasManualFraming) 1f else focusState.revealAlpha
                    scaleX = totalZoom
                    scaleY = totalZoom
                    translationX = -fx * size.width * (effZoom - 1f) * 0.5f -
                        (if (animateHero) pageOffset * heroWidthPx * HERO_BACKGROUND_PARALLAX else 0f)
                    translationY = -fy * size.height * (effZoom - 1f) * 0.5f +
                        (if (animateHero) sPx * 0.34f else 0f)
                    alpha = baseAlpha * visibility * heroScrollBackdropAlpha(sPx, size.height) * focusAlpha
                }
                .then(if (data.bannerBlurDp > 0) Modifier.blur(data.bannerBlurDp.dp) else Modifier)
        )
    } else if ((heroSettings.allowCroppedFallback || data.forceCoverBackdrop) && data.posterModel != null) {
        val fallbackBlur = if (data.bannerBlurDp > 0) maxOf(data.bannerBlurDp, 16) else 20
        val baseAlpha = if (heroSettings.dimBanner) 0.38f else 0.76f
        AsyncImage(
            model = data.posterModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val sPx = scrollOffsetPx()
                    val scrollScale = if (animateHero) heroScrollBackdropScale(sPx) else 1f
                    val totalZoom = overscanScale * scrollScale
                    scaleX = totalZoom
                    scaleY = totalZoom
                    translationX = if (animateHero) -pageOffset * heroWidthPx * HERO_BACKGROUND_PARALLAX else 0f
                    translationY = if (animateHero) sPx * 0.34f else 0f
                    alpha = baseAlpha * visibility * heroScrollBackdropAlpha(sPx, size.height)
                }
                .blur(fallbackBlur.dp)
        )
    } else {
        val auraAlpha = if (heroSettings.dimBanner) 0.24f else 0.42f
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val sPx = scrollOffsetPx()
                    translationY = if (animateHero) sPx * 0.25f else 0f
                    alpha = visibility * heroScrollBackdropAlpha(sPx, size.height)
                }
        ) {
            drawRect(color = Color(0xFF0D0C0B))
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentGold.copy(alpha = auraAlpha),
                        AccentGold.copy(alpha = auraAlpha * 0.30f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.82f, size.height * 0.24f),
                    radius = size.width * 0.88f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFB4692E).copy(alpha = auraAlpha * 0.55f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.16f, size.height * 0.72f),
                    radius = size.width * 0.72f
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChimanewHeroForegroundRow(
    data: ResolvedHomeHeroSlide,
    heroSettings: HomeHeroSettings,
    hiddenSources: Set<String> = emptySet()
) {
    val showUnread = heroSettings.showUnreadBadge && data.remainingCount > 0
    val title = data.title
    val categoryBadge = data.categoryBadge
    val posterModel = data.posterModel
    val posterFallback = data.posterFallback
    val userRating = data.userRating
    val communityScore = data.communityScore
    val imdbScore = data.imdbScore
    val rtScore = data.rtScore
    val metacriticScore = data.metacriticScore
    val traktScore = data.traktScore
    val audienceScore = data.audienceScore
    val cachedRatings = data.cachedRatings
    val mediaType = data.mediaType
    val hasAnilist = data.hasAnilist
    val hasMal = data.hasMal
    val hasTmdb = data.hasTmdb
    val hasVndb = data.hasVndb
    val hasBangumi = data.hasBangumi
    val hasIgdb = data.hasIgdb
    val hasSteam = data.hasSteam
    val progressLabel = data.progressLabel
    val remainingCount = data.remainingCount

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
            Box(
                modifier = Modifier
                    .width(114.dp)
                    .height(170.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceCard)
                    .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
            ) {
                if (posterModel != null) {
                    AsyncImage(
                        model = posterModel,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (posterFallback != null) {
                    posterFallback()
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Top Tier: Category Eyebrow (+ Progress if Clean Separation layout 0)
                if (heroSettings.metadataLayout != 3) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = categoryBadge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold,
                            letterSpacing = 0.6.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.85f),
                                    blurRadius = 4f
                                )
                            )
                        )
                        if (heroSettings.metadataLayout == 0 && progressLabel.isNotBlank()) {
                            Text(
                                text = "\u2022 $progressLabel",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        if (heroSettings.metadataLayout == 0 && showUnread) {
                            RemainingCountBadge(count = remainingCount)
                        }
                    }
                }

                // Title
                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.85f),
                            blurRadius = 6f
                        )
                    )
                )

                // Middle Tier (Ratings & Progress per metadataLayout 0..3)
                when (heroSettings.metadataLayout) {
                    0 -> {
                        if (heroSettings.showRatings) {
                            HeroRatingStrip(
                                userRating = userRating,
                                communityScore = communityScore,
                                imdbScore = imdbScore,
                                rtScore = rtScore,
                                metacriticScore = metacriticScore,
                                traktScore = traktScore,
                                audienceScore = audienceScore,
                                cachedRatings = cachedRatings,
                                mediaType = mediaType,
                                hasAnilist = hasAnilist,
                                hasMal = hasMal,
                                hasTmdb = hasTmdb,
                                hasVndb = hasVndb,
                                hasBangumi = hasBangumi,
                                hasIgdb = hasIgdb,
                                hasSteam = hasSteam,
                                hiddenSources = hiddenSources,
                                format = heroSettings.ratingFormat
                            )
                        }
                    }
                    1 -> {
                        if (heroSettings.showRatings) {
                            HeroRatingStrip(
                                userRating = userRating,
                                communityScore = communityScore,
                                imdbScore = imdbScore,
                                rtScore = rtScore,
                                metacriticScore = metacriticScore,
                                traktScore = traktScore,
                                audienceScore = audienceScore,
                                cachedRatings = cachedRatings,
                                mediaType = mediaType,
                                hasAnilist = hasAnilist,
                                hasMal = hasMal,
                                hasTmdb = hasTmdb,
                                hasVndb = hasVndb,
                                hasBangumi = hasBangumi,
                                hasIgdb = hasIgdb,
                                hasSteam = hasSteam,
                                hiddenSources = hiddenSources,
                                format = heroSettings.ratingFormat
                            )
                        }
                        if (progressLabel.isNotBlank() || showUnread) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (progressLabel.isNotBlank()) {
                                    Text(
                                        text = progressLabel,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (showUnread) RemainingCountBadge(count = remainingCount)
                            }
                        }
                    }
                    2 -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (heroSettings.showRatings) {
                                HeroRatingStrip(
                                    userRating = userRating,
                                    communityScore = communityScore,
                                    imdbScore = imdbScore,
                                    rtScore = rtScore,
                                    metacriticScore = metacriticScore,
                                    traktScore = traktScore,
                                    audienceScore = audienceScore,
                                    cachedRatings = cachedRatings,
                                    mediaType = mediaType,
                                    hasAnilist = hasAnilist,
                                    hasMal = hasMal,
                                    hasTmdb = hasTmdb,
                                    hasVndb = hasVndb,
                                    hasBangumi = hasBangumi,
                                    hasIgdb = hasIgdb,
                                    hasSteam = hasSteam,
                                    hiddenSources = hiddenSources,
                                    format = heroSettings.ratingFormat,
                                    compact = true
                                )
                            }
                            if (progressLabel.isNotBlank()) {
                                Text(
                                    text = progressLabel,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            if (showUnread) RemainingCountBadge(count = remainingCount)
                        }
                    }
                    3 -> {
                        // Minimal Inline: No eyebrow line above title; single crisp inline spec & ratings band below title
                        if (heroSettings.showRatings) {
                            HeroRatingStrip(
                                userRating = userRating,
                                communityScore = communityScore,
                                imdbScore = imdbScore,
                                rtScore = rtScore,
                                metacriticScore = metacriticScore,
                                traktScore = traktScore,
                                audienceScore = audienceScore,
                                cachedRatings = cachedRatings,
                                mediaType = mediaType,
                                hasAnilist = hasAnilist,
                                hasMal = hasMal,
                                hasTmdb = hasTmdb,
                                hasVndb = hasVndb,
                                hasBangumi = hasBangumi,
                                hasIgdb = hasIgdb,
                                hasSteam = hasSteam,
                                hiddenSources = hiddenSources,
                                format = heroSettings.ratingFormat,
                                compact = true
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = categoryBadge.substringBefore(" \u00B7 "),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGold
                            )
                            if (progressLabel.isNotBlank()) {
                                Text(
                                    text = "\u00B7 $progressLabel",
                                    fontSize = 11.sp,
                                    color = TextPrimary.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (showUnread) RemainingCountBadge(count = remainingCount)
                        }
                    }
                }
            }
        }
    }

@Composable
private fun RemainingCountBadge(count: Int) {
    Text(
        text = "+$count left",
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = AccentGold,
        style = TextStyle(
            shadow = Shadow(color = Color.Black.copy(alpha = 0.85f), blurRadius = 4f)
        )
    )
}

private data class HeroRatingSourceEntry(
    val sourceLabel: String,
    val valueText: String,
    val pctValue: Int,
    val scale10Value: Float,
    val color: Color,
    val iconRes: Int? = null
)

private fun formatHeroTwoDecimals(value: Double, stripTrailingZeros: Boolean): String {
    val roundedTwo = (value * 100.0).roundToInt() / 100.0
    val formatted = String.format(Locale.US, "%.2f", roundedTwo)
    if (!stripTrailingZeros) return formatted
    return formatted.trimEnd('0').trimEnd('.')
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroRatingStrip(
    userRating: Float?,
    communityScore: Float?,
    imdbScore: Float? = null,
    rtScore: Int? = null,
    metacriticScore: Int? = null,
    traktScore: Int? = null,
    audienceScore: Int? = null,
    cachedRatings: CachedMultiRatings? = null,
    mediaType: MediaType = MediaType.ALL,
    hasAnilist: Boolean = false,
    hasMal: Boolean = false,
    hasTmdb: Boolean = false,
    hasVndb: Boolean = false,
    hasBangumi: Boolean = false,
    hasIgdb: Boolean = false,
    hasSteam: Boolean = false,
    hiddenSources: Set<String> = emptySet(),
    format: Int,
    compact: Boolean = false
) {
    val entries = remember(
        userRating, communityScore, imdbScore, rtScore, metacriticScore, traktScore, audienceScore,
        cachedRatings, mediaType, hasAnilist, hasMal, hasTmdb, hasVndb, hasBangumi, hasIgdb, hasSteam, hiddenSources
    ) {
        val allowed = allowedRatingSourcesFor(mediaType)
        val normHidden = hiddenSources.map { it.trim().lowercase() }.toSet()
        fun isSourceVisible(key: String): Boolean = key in allowed && key !in normHidden

        val effImdb = imdbScore?.takeIf { it > 0f } ?: cachedRatings?.imdbScore?.takeIf { it > 0f }
        val effTrakt = traktScore?.takeIf { it > 0 } ?: cachedRatings?.traktScore?.takeIf { it > 0 }
        val effAudience = audienceScore?.takeIf { it > 0 } ?: cachedRatings?.audienceScore?.takeIf { it > 0 }
        val effRt = rtScore?.takeIf { it > 0 } ?: cachedRatings?.rtScore?.takeIf { it > 0 }
        val effMetacritic = metacriticScore?.takeIf { it > 0 } ?: cachedRatings?.metacriticScore?.takeIf { it > 0 }
        val effSteamPct = cachedRatings?.steamPositivePct?.takeIf { it > 0 }

        buildList {
            // 1. IMDb
            if (isSourceVisible("imdb")) {
                effImdb?.let { score ->
                    val norm10 = if (score > 10f) score / 10f else score
                    add(
                        HeroRatingSourceEntry(
                            sourceLabel = "IMDb",
                            valueText = formatSmartRating(norm10.toDouble()),
                            pctValue = (norm10 * 10f).roundToInt(),
                            scale10Value = norm10,
                            color = Color(0xFFF5C518),
                            iconRes = R.drawable.rating_imdb
                        )
                    )
                }
            }

            // 2. TMDB (Shows, Movies, and Anime with TMDb mapping)
            if (isSourceVisible("tmdb") && (mediaType == MediaType.SHOW || mediaType == MediaType.MOVIE ||
                (mediaType == MediaType.ANIME && (cachedRatings?.tmdbScore != null || hasTmdb)))
            ) {
                val effTmdb = cachedRatings?.tmdbScore?.takeIf { it > 0f } ?: communityScore?.takeIf { it > 0f }
                effTmdb?.let { score ->
                    val isTenScale = score <= 10.05f
                    val norm10 = if (isTenScale) score else score / 10f
                    val pct = if (isTenScale) (score * 10f).roundToInt() else score.roundToInt()
                    add(
                        HeroRatingSourceEntry(
                            sourceLabel = "TMDB",
                            valueText = "$pct",
                            pctValue = pct,
                            scale10Value = norm10,
                            color = Color(0xFF01B4E4),
                            iconRes = R.drawable.rating_tmdb
                        )
                    )
                }
            }

            // 3. Trakt
            if (isSourceVisible("trakt")) {
                effTrakt?.let { t ->
                    add(
                        HeroRatingSourceEntry(
                            sourceLabel = "Trakt",
                            valueText = "$t%",
                            pctValue = t,
                            scale10Value = t / 10f,
                            color = Color(0xFFED1C24),
                            iconRes = R.drawable.rating_trakt
                        )
                    )
                }
            }

            // 4. Rotten Tomatoes Audience / Critic
            if (isSourceVisible("audience")) {
                effAudience?.let { aud ->
                    add(
                        HeroRatingSourceEntry(
                            sourceLabel = "Audience",
                            valueText = "$aud%",
                            pctValue = aud,
                            scale10Value = aud / 10f,
                            color = Color(0xFFFA320A),
                            iconRes = when {
                                aud >= 75 -> R.drawable.rating_audience_verified_hot
                                aud >= 60 -> R.drawable.rating_audience_score
                                else -> R.drawable.rating_audience_stale
                            }
                        )
                    )
                }
            }
            if (isSourceVisible("tomatoes")) {
                effRt?.takeIf { effAudience == null }?.let { rt ->
                    add(
                        HeroRatingSourceEntry(
                            sourceLabel = "RT",
                            valueText = "$rt%",
                            pctValue = rt,
                            scale10Value = rt / 10f,
                            color = Color(0xFFFA320A),
                            iconRes = when {
                                rt >= 75 -> R.drawable.rating_rotten_tomatoes_certified
                                rt >= 60 -> R.drawable.rating_rotten_tomatoes
                                else -> R.drawable.rating_rotten_tomatoes_rotten
                            }
                        )
                    )
                }
            }

            // 5. Video Games: IGDB User & Critics
            if (mediaType == MediaType.GAME) {
                if (isSourceVisible("igdb")) {
                    val effIgdbUser = cachedRatings?.igdbUserScore?.takeIf { it > 0f }
                        ?: if (hasIgdb || effSteamPct == null) communityScore?.takeIf { it > 0f } else null
                    effIgdbUser?.let { score ->
                        val norm10 = if (score > 10f) score / 10f else score
                        add(
                            HeroRatingSourceEntry(
                                sourceLabel = "IGDB",
                                valueText = formatSmartRating(norm10.toDouble()),
                                pctValue = (norm10 * 10f).roundToInt(),
                                scale10Value = norm10,
                                color = Color(0xFFA78BFA),
                                iconRes = R.drawable.brand_igdb
                            )
                        )
                    }
                }
                if (isSourceVisible("igdb_critic")) {
                    cachedRatings?.igdbCriticScore?.takeIf { it > 0 }?.let { critic ->
                        add(
                            HeroRatingSourceEntry(
                                sourceLabel = "Critics",
                                valueText = "$critic",
                                pctValue = critic,
                                scale10Value = critic / 10f,
                                color = Color(0xFFF5C518),
                                iconRes = R.drawable.ic_critic_score
                            )
                        )
                    }
                }
            }

            // 6. Metacritic
            if (isSourceVisible("metacritic")) {
                effMetacritic?.let { mc ->
                    add(
                        HeroRatingSourceEntry(
                            sourceLabel = "Metacritic",
                            valueText = "$mc",
                            pctValue = mc,
                            scale10Value = mc / 10f,
                            color = if (mc >= 61) Color(0xFF66CC33) else if (mc >= 40) Color(0xFFFFCC33) else Color(0xFFFF4D4D),
                            iconRes = R.drawable.rating_metacritic
                        )
                    )
                }
            }

            // 7. Anime / Manga / Novel: AniList, MyAnimeList
            if (mediaType in listOf(MediaType.ANIME, MediaType.MANGA, MediaType.NOVEL)) {
                if (isSourceVisible("anilist")) {
                    val effAni = cachedRatings?.anilistScore?.takeIf { it > 0f } ?: if (
                        hasAnilist ||
                        (!hasMal && !hasBangumi && cachedRatings?.malScore == null && cachedRatings?.bangumiScore == null)
                    ) communityScore?.takeIf { it > 0f } else null
                    effAni?.let { score ->
                        val isTenScale = score <= 10.05f
                        val norm10 = if (isTenScale) score else score / 10f
                        val pct = if (isTenScale) (score * 10f).roundToInt() else score.roundToInt()
                        add(
                            HeroRatingSourceEntry(
                                sourceLabel = "AniList",
                                valueText = "$pct%",
                                pctValue = pct,
                                scale10Value = norm10,
                                color = Color(0xFF02A9FF),
                                iconRes = R.drawable.brand_anilist
                            )
                        )
                    }
                }

                if (isSourceVisible("mal")) {
                    val effMal = cachedRatings?.malScore?.takeIf { it > 0f }
                        ?: if (hasMal && cachedRatings?.anilistScore == null && !hasAnilist) communityScore?.takeIf { it > 0f } else null
                    effMal?.let { score ->
                        val norm10 = if (score > 10f) score / 10f else score
                        add(
                            HeroRatingSourceEntry(
                                sourceLabel = "MAL",
                                valueText = formatHeroTwoDecimals(norm10.toDouble(), stripTrailingZeros = true),
                                pctValue = (norm10 * 10f).roundToInt(),
                                scale10Value = norm10,
                                color = Color(0xFF5B8BFF),
                                iconRes = R.drawable.rating_mal
                            )
                        )
                    }
                }
            }

            // 8. Bangumi (Strict allowed category gate)
            if (isSourceVisible("bangumi")) {
                val effBgm = cachedRatings?.bangumiScore?.takeIf { it > 0f }
                    ?: if (hasBangumi && isEmpty()) communityScore?.takeIf { it > 0f } else null
                effBgm?.let { score ->
                    val norm10 = if (score > 10f) score / 10f else score
                    add(
                        HeroRatingSourceEntry(
                            sourceLabel = "Bangumi",
                            valueText = formatSmartRating(norm10.toDouble()),
                            pctValue = (norm10 * 10f).roundToInt(),
                            scale10Value = norm10,
                            color = Color(0xFFF09199),
                            iconRes = R.drawable.brand_bangumi
                        )
                    )
                }
            }

            // 9. Kitsu & MangaUpdates
            if (isSourceVisible("kitsu")) {
                cachedRatings?.kitsuScore?.takeIf { it > 0f }?.let { score ->
                    val isTenScale = score <= 10.05f
                    val norm10 = if (isTenScale) score else score / 10f
                    val pct = if (isTenScale) (score * 10f).roundToInt() else score.roundToInt()
                    add(
                        HeroRatingSourceEntry(
                            sourceLabel = "Kitsu",
                            valueText = "$pct%",
                            pctValue = pct,
                            scale10Value = norm10,
                            color = Color(0xFFF75239),
                            iconRes = R.drawable.brand_kitsu
                        )
                    )
                }
            }
            if (isSourceVisible("mangaupdates")) {
                cachedRatings?.mangaUpdatesScore?.takeIf { it > 0f }?.let { score ->
                    val norm10 = if (score > 10f) score / 10f else score
                    add(
                        HeroRatingSourceEntry(
                            sourceLabel = "MangaUpdates",
                            valueText = formatSmartRating(norm10.toDouble()),
                            pctValue = (norm10 * 10f).roundToInt(),
                            scale10Value = norm10,
                            color = Color(0xFF64B5F6),
                            iconRes = R.drawable.brand_mangaupdates
                        )
                    )
                }
            }

            // 10. Steam % (Games & VNs)
            if ((mediaType == MediaType.GAME || mediaType == MediaType.VISUAL_NOVEL) && isSourceVisible("steam")) {
                effSteamPct?.let { pct ->
                    add(
                        HeroRatingSourceEntry(
                            sourceLabel = "Steam",
                            valueText = "$pct%",
                            pctValue = pct,
                            scale10Value = pct / 10f,
                            color = Color(0xFF66C0F4),
                            iconRes = R.drawable.brand_steam
                        )
                    )
                }
            }

            // 11. VNDB (Visual Novels)
            if (mediaType == MediaType.VISUAL_NOVEL && isSourceVisible("vndb")) {
                val effVn = cachedRatings?.vndbScore?.takeIf { it > 0f } ?: communityScore?.takeIf { it > 0f }
                effVn?.let { score ->
                    val norm10 = if (score > 10f) score / 10f else score
                    add(
                        HeroRatingSourceEntry(
                            sourceLabel = "VNDB",
                            valueText = formatHeroTwoDecimals(norm10.toDouble(), stripTrailingZeros = false),
                            pctValue = (norm10 * 10f).roundToInt(),
                            scale10Value = norm10,
                            color = Color(0xFF6BA4E8),
                            iconRes = R.drawable.brand_vndb
                        )
                    )
                }
            }

            // 12. Books & Comics (Hardcover, Open Library, Apple Books on 5-star scale)
            if (mediaType == MediaType.BOOK || mediaType == MediaType.COMIC) {
                if (mediaType == MediaType.BOOK && isSourceVisible("hardcover")) {
                    cachedRatings?.hardcoverRating?.takeIf { it > 0f }?.let { rawHc ->
                        val hc5 = if (rawHc > 5f) rawHc / 2f else rawHc
                        add(
                            HeroRatingSourceEntry(
                                sourceLabel = "Hardcover",
                                valueText = "${formatSmartRating(hc5.toDouble())}/5",
                                pctValue = (hc5 * 20f).roundToInt(),
                                scale10Value = hc5 * 2f,
                                color = Color(0xFFF59E0B),
                                iconRes = R.drawable.brand_hardcover
                            )
                        )
                    }
                }
                if (isSourceVisible("openlibrary")) {
                    val effOl5 = (
                        cachedRatings?.openLibraryRating?.takeIf { it > 0f }
                            ?: if (cachedRatings?.hardcoverRating == null && cachedRatings?.appleBooksRating == null) {
                                communityScore?.takeIf { it > 0f }
                            } else null
                        )?.let { if (it > 5f) it / 2f else it }
                    effOl5?.let { ol5 ->
                        add(
                            HeroRatingSourceEntry(
                                sourceLabel = "Open Library",
                                valueText = "${formatSmartRating(ol5.toDouble())}/5",
                                pctValue = (ol5 * 20f).roundToInt(),
                                scale10Value = ol5 * 2f,
                                color = Color(0xFF38BDF8),
                                iconRes = R.drawable.brand_openlibrary
                            )
                        )
                    }
                }
                if (isSourceVisible("applebooks")) {
                    cachedRatings?.appleBooksRating?.takeIf { it > 0f }
                        ?.let { if (it > 5f) it / 2f else it }
                        ?.let { ab5 ->
                            add(
                                HeroRatingSourceEntry(
                                    sourceLabel = "Apple Books",
                                    valueText = "${formatSmartRating(ab5.toDouble())}/5",
                                    pctValue = (ab5 * 20f).roundToInt(),
                                    scale10Value = ab5 * 2f,
                                    color = Color(0xFFFB923C),
                                    iconRes = R.drawable.brand_apple_books
                                )
                            )
                        }
                }
            }

            // Fallback generic Score only if no tracker entry resolved and nothing else is hidden
            if (isEmpty() && isSourceVisible("score")) {
                communityScore?.takeIf { it > 0f }?.let { score ->
                    val isTenScale = score <= 10.05f
                    val norm10 = if (isTenScale) score else score / 10f
                    val pct = if (isTenScale) (score * 10f).roundToInt() else score.roundToInt()
                    add(
                        HeroRatingSourceEntry(
                            sourceLabel = "Score",
                            valueText = formatSmartRating(norm10.toDouble()),
                            pctValue = pct,
                            scale10Value = norm10,
                            color = AccentGold,
                            iconRes = null
                        )
                    )
                }
            }

            // Optional trailing personal rating ("You")
            if (isSourceVisible("you")) {
                userRating?.takeIf { it > 0f }?.let { u ->
                    add(
                        HeroRatingSourceEntry(
                            sourceLabel = "You",
                            valueText = formatSmartRating(u.toDouble()),
                            pctValue = (u * 10f).roundToInt(),
                            scale10Value = u,
                            color = AccentGold,
                            iconRes = null
                        )
                    )
                }
            }
        }
    }
    if (entries.isEmpty()) return

    val primary = entries.lastOrNull { it.sourceLabel == "You" } ?: entries.first()
    val primaryTracker = entries.firstOrNull { it.sourceLabel != "You" } ?: entries.first()
    val textShadow = Shadow(color = Color.Black.copy(alpha = 0.85f), blurRadius = 5f)

    when (format) {
        1 -> {
            // Unboxed Percentage
            Text(
                text = "${primary.pctValue}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold,
                maxLines = 1,
                style = TextStyle(shadow = textShadow)
            )
        }
        2 -> {
            // Unboxed Star + Primary Tracker Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                primaryTracker.iconRes?.let { res ->
                    Image(
                        painter = painterResource(id = res),
                        contentDescription = primaryTracker.sourceLabel,
                        modifier = Modifier.size(width = if (res == R.drawable.rating_imdb) 24.dp else 14.dp, height = 14.dp)
                    )
                } ?: Text(
                    text = "\u2605",
                    fontSize = 12.sp,
                    color = AccentGold,
                    style = TextStyle(shadow = textShadow)
                )
                Text(
                    text = if (primaryTracker.iconRes != null) primaryTracker.valueText else "${formatSmartRating(primaryTracker.scale10Value.toDouble())} \u00B7 ${primaryTracker.sourceLabel}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTracker.color,
                    maxLines = 1,
                    style = TextStyle(shadow = textShadow)
                )
            }
        }
        3 -> {
            // Unboxed Multi-Source Strip (1:1 NuvioRatingsRow icons + optional trailing "You")
            val trackerOnly = entries.filter { it.sourceLabel != "You" }
            val youEntry = entries.lastOrNull { it.sourceLabel == "You" }
            val maxTrackers = if (compact) 3 else 5
            val shown = trackerOnly.take(maxTrackers) + listOfNotNull(youEntry)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                shown.forEach { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (entry.iconRes != null) {
                            Image(
                                painter = painterResource(id = entry.iconRes),
                                contentDescription = entry.sourceLabel,
                                modifier = Modifier.size(
                                    width = if (entry.iconRes == R.drawable.rating_imdb) 24.dp else 13.dp,
                                    height = 13.dp
                                )
                            )
                            Text(
                                text = entry.valueText,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                style = TextStyle(shadow = textShadow)
                            )
                        } else {
                            Text(
                                text = if (entry.sourceLabel == "You") "\u2605 ${entry.valueText} You" else "\u2605 ${entry.valueText}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = entry.color,
                                maxLines = 1,
                                style = TextStyle(shadow = textShadow)
                            )
                        }
                    }
                }
            }
        }
        else -> {
            // 0 = Unboxed Golden Star
            Text(
                text = "\u2605 ${formatSmartRating(primary.scale10Value.toDouble())}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold,
                maxLines = 1,
                style = TextStyle(shadow = textShadow)
            )
        }
    }
}

@Composable
private fun EmptyHomeHeroCard(onDiscoverClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(
                Brush.verticalGradient(listOf(DarkSurfaceElevated, AmoledBlack))
            )
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "WELCOME TO OMNITRACK",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AccentGold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Start building your universal library",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(AccentGold)
                .clickable(onClick = onDiscoverClick)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Explore Discover",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = OnAccentColor
            )
        }
    }
}

@Composable
private fun HomeShelfHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        if (actionLabel != null && onActionClick != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGold
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueTrackingCard(
    item: MediaCardSummary,
    style: ContinueTrackingStyle,
    posterStyle: PosterCardStyle = PosterCardStyle(),
    coverScale: Float = 1f,
    posterWidthDp: Dp = 114.dp,
    showQuickPlus: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickProgress: () -> Unit
) {
    val perf = LocalPerformanceSettings.current
    val coverModel = remember(item.customCoverPath, item.coverUrl) {
        ArtworkModelResolver.toCoilModel(ArtworkModelResolver.preferredCover(item))
    }
    val bannerModel = remember(item.customBannerPath, item.bannerUrl, item.artworkRole, item.coverUrl) {
        ArtworkModelResolver.toCoilModel(ArtworkModelResolver.preferredBanner(item))
    }
    val hasWideBanner = bannerModel != null
    val progressLabel = remember(item) { formatConciseProgress(item) }
    val fraction = remember(item) { computeProgressFraction(item) }
    val badgeText = progressLabel.ifBlank { item.mediaType.displayName }
    val applyDepth = !perf.disableCardDepthEffects && posterStyle.cardDepthEnabled && posterStyle.cardDepthContinueWatchingEnabled

    when (style) {
        ContinueTrackingStyle.CARD -> {
            // Option A Frameless Editorial 16:9 Artwork + Clean Label Below
            val artShape = RoundedCornerShape(8.dp)
            val cardWidth = (224f * coverScale).roundToInt().coerceIn(168, 330).dp
            Column(
                modifier = Modifier
                    .width(cardWidth)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(artShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .glassCardDepth(
                            shape = artShape,
                            edgeStrength = posterStyle.cardDepthEdgeStrength.toFloat(),
                            sheenStrength = posterStyle.cardDepthSheenStrength.toFloat(),
                            edgeCoverage = posterStyle.cardDepthEdgeCoverage.toFloat(),
                            enabled = applyDepth
                        )
                        .then(
                            if (!applyDepth) Modifier.border(0.5.dp, Color.White.copy(alpha = 0.08f), artShape) else Modifier
                        )
                ) {
                    val cardArtModel = bannerModel ?: coverModel
                    if (cardArtModel != null) {
                        AsyncImage(
                            model = cardArtModel,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            alignment = if (hasWideBanner) BiasAlignment(item.bannerFocalX, item.bannerFocalY) else Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (item.mediaType == MediaType.FANFICTION) {
                        FanficTypographicCover(
                            title = item.title,
                            author = item.bookAuthor,
                            primaryFandom = item.fanficPrimaryFandom,
                            sourceSite = item.fanficSourceSite,
                            ratingCode = item.fanficRating,
                            wordCount = item.fanficWordCount,
                            isCrossover = item.fanficIsCrossover,
                            compact = true,
                            isBlurred = false
                        )
                    }

                    if (showQuickPlus) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(7.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(AccentGold)
                                .clickable(onClick = onQuickProgress),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (item.mediaType == MediaType.MOVIE) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = "Increment Progress",
                                tint = OnAccentColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .background(Color.White.copy(alpha = 0.22f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(AccentGold)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = item.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = badgeText,
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        ContinueTrackingStyle.WIDE -> {
            // Frameless Editorial Wide Split Row (zero outer container/border)
            val posterShape = RoundedCornerShape(6.dp)
            val wideTotalW = (286f * coverScale).roundToInt().coerceIn(220, 360).dp
            val wideThumbW = (64f * coverScale).roundToInt().coerceIn(48, 96).dp
            val wideThumbH = (96f * coverScale).roundToInt().coerceIn(72, 144).dp
            Column(
                modifier = Modifier
                    .width(wideTotalW)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(wideThumbW)
                            .height(wideThumbH)
                            .clip(posterShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .glassCardDepth(
                                shape = posterShape,
                                edgeStrength = posterStyle.cardDepthEdgeStrength.toFloat(),
                                sheenStrength = posterStyle.cardDepthSheenStrength.toFloat(),
                                edgeCoverage = posterStyle.cardDepthEdgeCoverage.toFloat(),
                                enabled = applyDepth
                            )
                    ) {
                        if (coverModel != null) {
                            AsyncImage(
                                model = coverModel,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (item.mediaType == MediaType.FANFICTION) {
                            FanficTypographicCover(
                                title = item.title,
                                author = item.bookAuthor,
                                primaryFandom = item.fanficPrimaryFandom,
                                sourceSite = item.fanficSourceSite,
                                ratingCode = item.fanficRating,
                                wordCount = item.fanficWordCount,
                                isCrossover = item.fanficIsCrossover,
                                compact = true,
                                isBlurred = false
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = (wideThumbH - 4.dp).coerceAtLeast(78.dp)),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (showQuickPlus) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(AccentGold)
                                            .clickable(onClick = onQuickProgress),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (item.mediaType == MediaType.MOVIE) Icons.Default.Check else Icons.Default.Add,
                                            contentDescription = "Increment Progress",
                                            tint = OnAccentColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = badgeText,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentGold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val meta = item.bookAuthor?.takeIf { it.isNotBlank() }
                                ?: item.comicPublisher?.takeIf { it.isNotBlank() }
                                ?: item.mediaType.displayName
                            Text(
                                text = meta,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (fraction > 0f || (item.totalProgress != null && item.totalProgress > 0.0)) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.14f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(AccentGold)
                                    )
                                }
                                Text(
                                    text = "${(fraction * 100f).roundToInt()}%",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )
            }
        }

        ContinueTrackingStyle.POSTER -> {
            // Frameless 2:3 Poster Card with Flush Bottom Progress Bar (no pill wrapper)
            val posterShape = RoundedCornerShape(8.dp)
            Column(
                modifier = Modifier
                    .width(posterWidthDp)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(posterShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .glassCardDepth(
                            shape = posterShape,
                            edgeStrength = posterStyle.cardDepthEdgeStrength.toFloat(),
                            sheenStrength = posterStyle.cardDepthSheenStrength.toFloat(),
                            edgeCoverage = posterStyle.cardDepthEdgeCoverage.toFloat(),
                            enabled = applyDepth
                        )
                        .then(
                            if (!applyDepth) Modifier.border(0.5.dp, Color.White.copy(alpha = 0.08f), posterShape) else Modifier
                        )
                ) {
                    if (coverModel != null) {
                        AsyncImage(
                            model = coverModel,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (item.mediaType == MediaType.FANFICTION) {
                        FanficTypographicCover(
                            title = item.title,
                            author = item.bookAuthor,
                            primaryFandom = item.fanficPrimaryFandom,
                            sourceSite = item.fanficSourceSite,
                            ratingCode = item.fanficRating,
                            wordCount = item.fanficWordCount,
                            isCrossover = item.fanficIsCrossover,
                            compact = true,
                            isBlurred = false
                        )
                    }

                    if (showQuickPlus) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(AccentGold)
                                .clickable(onClick = onQuickProgress),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (item.mediaType == MediaType.MOVIE) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = "Increment Progress",
                                tint = OnAccentColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .background(Color.White.copy(alpha = 0.22f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(AccentGold)
                            )
                        }
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 2.dp)) {
                    Text(
                        text = item.title,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatUpNextSubLabel(release: CalendarReleaseItem): String = when {
    release.mediaType == MediaType.SHOW && release.seasonNumber != null && release.episodeNumber != null ->
        "S%02dE%02d".format(release.seasonNumber, release.episodeNumber)
    release.mediaType == MediaType.COMIC && release.episodeNumber != null ->
        "Issue #${release.episodeNumber}"
    release.mediaType == MediaType.MANGA && !release.chapterLabel.isNullOrBlank() ->
        "Ch. ${release.chapterLabel}"
    release.episodeNumber != null ->
        "Episode ${release.episodeNumber}"
    else -> release.formatLabel.ifBlank { release.mediaType.displayName }
}

private fun calendarFallbackIcon(mediaType: MediaType) = when (mediaType) {
    MediaType.MOVIE -> Icons.Default.Movie
    MediaType.SHOW -> Icons.Default.Tv
    MediaType.GAME -> Icons.Default.SportsEsports
    MediaType.BOOK, MediaType.COMIC -> Icons.Default.MenuBook
    MediaType.MANGA, MediaType.NOVEL, MediaType.FANFICTION -> Icons.Default.AutoStories
    else -> Icons.Default.PlayCircleOutline
}

@Composable
private fun UpNextCalendarCard(
    release: CalendarReleaseItem,
    onClick: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val dayTime = remember(release.airingAtEpochSec, release.isDateOnly) {
        val dt = Instant.ofEpochSecond(release.airingAtEpochSec).atZone(zone)
        val pattern = if (release.isDateOnly) "EEE, MMM d" else "EEE h:mm a"
        dt.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    }
    val subLabel = remember(release) { formatUpNextSubLabel(release) }

    var useFallbackCover by remember(release.id, release.coverUrl, release.fallbackCoverUrl) { mutableStateOf(false) }
    var coverFailed by remember(release.id, release.coverUrl, release.fallbackCoverUrl) { mutableStateOf(false) }
    val activeCoverUrl = remember(release.coverUrl, release.fallbackCoverUrl, useFallbackCover) {
        val primary = release.coverUrl.trim()
        val secondary = release.fallbackCoverUrl?.trim().orEmpty()
        when {
            !useFallbackCover && primary.isNotBlank() -> primary
            secondary.isNotBlank() -> secondary
            else -> ""
        }
    }

    Row(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick)
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 0.6.dp.toPx()
                )
            }
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            if (activeCoverUrl.isNotBlank() && !coverFailed) {
                AsyncImage(
                    model = remember(activeCoverUrl) { ArtworkModelResolver.toCoilModel(activeCoverUrl) },
                    contentDescription = release.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = {
                        if (!useFallbackCover && !release.fallbackCoverUrl.isNullOrBlank()) {
                            useFallbackCover = true
                        } else {
                            coverFailed = true
                        }
                    }
                )
            } else {
                Icon(
                    imageVector = calendarFallbackIcon(release.mediaType),
                    contentDescription = null,
                    tint = TextMuted.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = release.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subLabel,
                fontSize = 11.5.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dayTime,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold
            )
        }
    }
}

@Composable
private fun UpNextCompactRow(
    release: CalendarReleaseItem,
    onClick: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val dayTime = remember(release.airingAtEpochSec, release.isDateOnly) {
        val dt = Instant.ofEpochSecond(release.airingAtEpochSec).atZone(zone)
        val pattern = if (release.isDateOnly) "EEE, MMM d" else "EEE h:mm a"
        dt.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    }
    val subLabel = remember(release) { formatUpNextSubLabel(release) }

    var useFallbackCover by remember(release.id, release.coverUrl, release.fallbackCoverUrl) { mutableStateOf(false) }
    var coverFailed by remember(release.id, release.coverUrl, release.fallbackCoverUrl) { mutableStateOf(false) }
    val activeCoverUrl = remember(release.coverUrl, release.fallbackCoverUrl, useFallbackCover) {
        val primary = release.coverUrl.trim()
        val secondary = release.fallbackCoverUrl?.trim().orEmpty()
        when {
            !useFallbackCover && primary.isNotBlank() -> primary
            secondary.isNotBlank() -> secondary
            else -> ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            if (activeCoverUrl.isNotBlank() && !coverFailed) {
                AsyncImage(
                    model = remember(activeCoverUrl) { ArtworkModelResolver.toCoilModel(activeCoverUrl) },
                    contentDescription = release.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = {
                        if (!useFallbackCover && !release.fallbackCoverUrl.isNullOrBlank()) {
                            useFallbackCover = true
                        } else {
                            coverFailed = true
                        }
                    }
                )
            } else {
                Icon(
                    imageVector = calendarFallbackIcon(release.mediaType),
                    contentDescription = null,
                    tint = TextMuted.copy(alpha = 0.6f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = release.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subLabel,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = dayTime,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AccentGold
        )
    }
}

@Composable
private fun HomeDiscoverSearchShelf(
    title: String,
    actionLabel: String,
    items: List<SearchResultItem>,
    allMedia: List<MediaCardSummary>,
    cardWidth: Dp,
    cardHeight: Dp,
    onActionClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    onPreviewSearchItem: (SearchResultItem) -> Unit,
    onLoadMore: (() -> Unit)? = null
) {
    val rowState = rememberLazyListState()
    LaunchedEffect(rowState, items.size, onLoadMore) {
        if (onLoadMore == null || items.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            if (lastVisible >= (items.lastIndex - 3).coerceAtLeast(0)) {
                onLoadMore()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeShelfHeader(
            title = title,
            actionLabel = actionLabel,
            onActionClick = onActionClick
        )
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = items,
                key = { "hdisc_${title}_${it.mediaType}_${it.sourceProvider}_${it.anilistId ?: it.steamAppId ?: it.openLibraryId ?: it.comicVineId ?: it.metronId ?: it.vndbId ?: it.title}" }
            ) { item ->
                val libMatch = allMedia.firstOrNull { local ->
                    local.mediaType == item.mediaType && (
                        (item.anilistId != null && local.anilistId == item.anilistId) ||
                            (item.tmdbId != null && local.tmdbId == item.tmdbId) ||
                            (item.steamAppId != null && local.steamAppId == item.steamAppId) ||
                            (item.openLibraryId != null && local.openLibraryId == item.openLibraryId) ||
                            local.title.equals(item.title, ignoreCase = true)
                        )
                }
                NuvioRailPosterCard(
                    title = item.title,
                    coverUrl = libMatch?.customCoverPath ?: libMatch?.coverUrl ?: item.coverUrl,
                    subtitle = item.bookAuthor ?: item.comicPublisher ?: (item.mediaType.displayName + (item.releaseYear?.let { " \u00B7 $it" } ?: "")),
                    rating = libMatch?.userRating ?: libMatch?.communityScore ?: item.communityScore,
                    onClick = {
                        if (libMatch != null) onItemClick(libMatch.id) else onPreviewSearchItem(item)
                    },
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    isFanfic = item.mediaType == MediaType.FANFICTION,
                    fanficAuthor = item.bookAuthor,
                    fanficFandom = item.fanficPrimaryFandom,
                    fanficSiteCode = item.fanficSourceSite,
                    fanficRating = item.fanficRating,
                    fanficWordCount = item.fanficWordCount,
                    fanficIsCrossover = item.fanficIsCrossover
                )
            }
        }
    }
}

@Composable
private fun MediaSummaryRail(
    title: String,
    actionLabel: String,
    items: List<MediaCardSummary>,
    cardWidth: Dp = 114.dp,
    cardHeight: Dp = 170.dp,
    onActionClick: () -> Unit,
    onItemClick: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeShelfHeader(
            title = title,
            actionLabel = actionLabel,
            onActionClick = onActionClick
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { "${title}_${it.id}" }) { summary ->
                val cover = remember(summary.customCoverPath, summary.coverUrl) {
                    ArtworkModelResolver.preferredCover(summary)
                }
                val sub = remember(summary) { formatConciseProgress(summary).ifBlank { summary.mediaType.displayName } }
                NuvioRailPosterCard(
                    title = summary.title,
                    coverUrl = cover,
                    subtitle = sub,
                    rating = summary.userRating ?: summary.communityScore,
                    onClick = { onItemClick(summary.id) },
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    isFanfic = summary.mediaType == MediaType.FANFICTION,
                    fanficAuthor = summary.bookAuthor,
                    fanficFandom = summary.fanficPrimaryFandom,
                    fanficSiteCode = summary.fanficSourceSite,
                    fanficRating = summary.fanficRating,
                    fanficWordCount = summary.fanficWordCount,
                    fanficIsCrossover = summary.fanficIsCrossover
                )
            }
        }
    }
}

@Composable
private fun CompactStatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) AccentGold else TextPrimary
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}

internal fun formatConciseProgress(item: MediaCardSummary): String {
    if (item.mediaType == MediaType.BOOK) {
        return when (BookTrackingMode.fromName(item.bookTrackingMode)) {
            BookTrackingMode.AUDIO_MINUTES -> {
                val cur = formatBookDuration(item.bookAudioMinutes) ?: "0m"
                val tot = formatBookDuration(item.bookTotalAudioMinutes)
                if (tot != null) "$cur / $tot" else cur
            }
            BookTrackingMode.PERCENT -> {
                val pct = if (item.totalProgress != null && item.totalProgress > 0.0) {
                    kotlin.math.round((item.currentProgress / item.totalProgress) * 100.0).toInt().coerceIn(0, 100)
                } else {
                    item.currentProgress.toInt().coerceIn(0, 100)
                }
                "$pct%"
            }
            BookTrackingMode.PAGES -> {
                val cur = item.currentProgress.toInt()
                val tot = item.totalProgress?.toInt()
                when {
                    tot != null && tot > 0 -> "$cur / $tot p"
                    cur > 0 -> "$cur Pages"
                    else -> ""
                }
            }
        }
    }
    if (item.mediaType == MediaType.MOVIE) {
        return if (item.status == TrackingStatus.COMPLETED || item.currentProgress > 0) "Watched" else "Queued"
    }
    if (item.mediaType == MediaType.GAME || item.mediaType == MediaType.VISUAL_NOVEL) {
        val eff = if (item.playtimeHours > 0.0) item.playtimeHours else item.currentProgress
        val cur = String.format(Locale.US, "%.1f", eff)
        return if (item.totalProgress != null && item.totalProgress > 0) {
            "$cur / ${String.format(Locale.US, "%.1f", item.totalProgress)} hrs"
        } else if (eff > 0.0) "$cur hrs" else ""
    }
    val unit = if (item.mediaType == MediaType.NOVEL && item.platform == "LIGHT_NOVEL") "Vol" else item.mediaType.unitLabel
    val cur = item.currentProgress.toInt()
    val tot = item.totalProgress?.toInt()
    return when {
        tot != null && tot > 0 -> "$unit $cur / $tot"
        cur > 0 -> "$unit $cur"
        else -> ""
    }
}

private fun computeProgressFraction(item: MediaCardSummary): Float {
    if (item.mediaType == MediaType.BOOK && BookTrackingMode.fromName(item.bookTrackingMode) == BookTrackingMode.AUDIO_MINUTES) {
        val cur = (item.bookAudioMinutes ?: 0).toFloat()
        val tot = (item.bookTotalAudioMinutes ?: 0).toFloat()
        return if (tot > 0f) (cur / tot).coerceIn(0f, 1f) else 0f
    }
    val cur = if (item.mediaType == MediaType.GAME || item.mediaType == MediaType.VISUAL_NOVEL) {
        (if (item.playtimeHours > 0.0) item.playtimeHours else item.currentProgress).toFloat()
    } else {
        item.currentProgress.toFloat()
    }
    val tot = if (item.mediaType == MediaType.BOOK && BookTrackingMode.fromName(item.bookTrackingMode) == BookTrackingMode.PERCENT) {
        100f
    } else {
        item.totalProgress?.toFloat() ?: 0f
    }
    return if (tot > 0f) (cur / tot).coerceIn(0f, 1f) else 0f
}
