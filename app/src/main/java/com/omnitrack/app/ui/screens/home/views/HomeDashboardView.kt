package com.omnitrack.app.ui.screens.home.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.artwork.ArtworkModelResolver
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.enrichment.CalendarReleaseItem
import com.omnitrack.app.data.model.BookTrackingMode
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.model.formatBookDuration
import com.omnitrack.app.data.preferences.ContinueTrackingStyle
import com.omnitrack.app.data.preferences.HomeHeroSettings
import com.omnitrack.app.data.preferences.HomeHeroSource
import com.omnitrack.app.data.preferences.HomeRowItem
import com.omnitrack.app.data.preferences.HomeRowKey
import com.omnitrack.app.data.preferences.PosterCardStyle
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.ui.components.NuvioRailPosterCard
import com.omnitrack.app.ui.components.glassCardDepth
import com.omnitrack.app.ui.screens.detail.components.formatSmartRating
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
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
    posterStyle: PosterCardStyle = PosterCardStyle(),
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    fanfictionEnabled: Boolean = true,
    fanficNsfwMode: com.omnitrack.app.data.model.VnNsfwFilterMode = com.omnitrack.app.data.model.VnNsfwFilterMode.BLUR_TAP
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var heroRefreshNonce by remember { mutableIntStateOf(0) }
    var localRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    var selectedCategory by remember { mutableStateOf(heroSettings.defaultCategory) }
    LaunchedEffect(heroSettings.defaultCategory) {
        selectedCategory = heroSettings.defaultCategory
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var actionSheetItem by remember { mutableStateOf<MediaCardSummary?>(null) }

    val isUnlimitedShelf = heroSettings.shelfItemLimit <= 0
    val shelfLimit = if (isUnlimitedShelf) Int.MAX_VALUE else heroSettings.shelfItemLimit.coerceIn(8, 24)
    val posterWidthDp = heroSettings.shelfPosterSize.widthDp.dp
    val posterHeightDp = heroSettings.shelfPosterSize.heightDp.dp

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
        heroSettings.slideCount,
        heroSettings.slideOrder,
        heroSettings.strictMode,
        heroSettings.strictRequirements,
        heroSettings.statusFilters,
        selectedCategory,
        visibleTrending,
        visibleAllMedia,
        heroRefreshNonce
    ) {
        fun matchesHeroCategory(type: MediaType): Boolean = when (heroSettings.heroCategoryMode.uppercase()) {
            "FOLLOW_HOME" -> selectedCategory == MediaType.ALL || type == selectedCategory
            "CUSTOM" -> heroSettings.heroCategories.isEmpty() || type.name in heroSettings.heroCategories
            else -> true // "ALL"
        }

        val scopedMedia = visibleAllMedia.filter { matchesHeroCategory(it.mediaType) }
        val scopedTrending = visibleTrending.filter { matchesHeroCategory(it.mediaType) }

        val scopedContinue = scopedMedia.filter { it.status == TrackingStatus.IN_PROGRESS }.sortedByDescending { it.lastUpdated }
        val scopedRecent = scopedMedia.sortedByDescending { it.lastUpdated }
        val scopedPlan = scopedMedia.filter { it.status == TrackingStatus.PLAN_TO_TRACK }.sortedByDescending { it.lastUpdated }
        val scopedFavorites = scopedMedia
            .filter { (it.userRating ?: it.communityScore ?: 0f) > 0f }
            .sortedWith(compareByDescending<MediaCardSummary> { it.userRating ?: 0f }.thenByDescending { it.communityScore ?: 0f })

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

        val mergedRaw: List<HomeHeroSlide> = if (heroSettings.slideOrder.equals("INTERLEAVED", ignoreCase = true) || heroSettings.heroSource == HomeHeroSource.ALL_MIXED) {
            val out = mutableListOf<HomeHeroSlide>()
            val maxLen = activePools.maxOfOrNull { it.size } ?: 0
            for (i in 0 until maxLen) {
                activePools.forEach { pool -> if (i < pool.size) out.add(pool[i]) }
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

        val ordered = when (heroSettings.slideOrder.uppercase()) {
            "TOP_RATED" -> {
                val base = resolved.sortedByDescending { slide ->
                    when (slide) {
                        is HomeHeroSlide.LocalSlide -> slide.item.userRating ?: slide.item.communityScore ?: 0f
                        is HomeHeroSlide.TrendingSlide -> slide.item.communityScore ?: 0f
                    }
                }
                if (heroRefreshNonce > 0 && base.size > 1) {
                    val shift = java.util.Random(heroRefreshNonce.toLong() * 31L).nextInt(base.size - 1) + 1
                    base.drop(shift) + base.take(shift)
                } else base
            }
            "SHUFFLE" -> {
                if (heroRefreshNonce > 0) {
                    resolved.shuffled(java.util.Random(heroRefreshNonce.toLong() * 1009L + 42L))
                } else {
                    resolved.shuffled(java.util.Random(42L))
                }
            }
            else -> {
                if (heroRefreshNonce > 0 && resolved.size > 1) {
                    resolved.shuffled(java.util.Random(heroRefreshNonce.toLong() * 7919L + 17L))
                } else resolved
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

    PullToRefreshBox(
        isRefreshing = isRefreshing || localRefreshing,
        state = pullState,
        onRefresh = {
            localRefreshing = true
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 118.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            if (heroSettings.showHero) {
                item(key = "home_hero_carousel") {
                    if (heroSlides.isNotEmpty()) {
                        val isLooping = heroSlides.size > 1
                        val virtualCount = if (isLooping) 100_000 else heroSlides.size
                        val startIndex = remember(heroSlides.size) {
                            if (isLooping) {
                                val mid = 50_000
                                mid - (mid % heroSlides.size)
                            } else 0
                        }
                        val heroKeys = remember(heroSlides) { heroSlides.map { it.slideKey() } }
                        val pagerState = rememberPagerState(
                            initialPage = startIndex,
                            pageCount = { virtualCount }
                        )

                        LaunchedEffect(heroRefreshNonce) {
                            if (heroRefreshNonce > 0 && heroSlides.isNotEmpty()) {
                                runCatching { pagerState.scrollToPage(startIndex) }
                            }
                        }

                        LaunchedEffect(heroKeys) {
                            if (heroSlides.isNotEmpty() && !pagerState.isScrollInProgress) {
                                val curMod = if (isLooping) pagerState.currentPage % heroSlides.size else 0
                                val target = if (isLooping) startIndex + curMod else 0
                                runCatching { pagerState.scrollToPage(target) }
                            }
                        }

                        LaunchedEffect(pagerState, heroSlides.size, heroSettings.autoRotate, heroSettings.autoRotateSeconds) {
                            val sec = heroSettings.autoRotateSeconds
                            if (!isLooping || !heroSettings.autoRotate || sec <= 0) return@LaunchedEffect
                            while (isActive) {
                                delay(sec * 1000L)
                                if (!pagerState.isScrollInProgress) {
                                    runCatching { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                            }
                        }

                        val currentActualIndex = if (isLooping) {
                            pagerState.currentPage % heroSlides.size
                        } else {
                            pagerState.currentPage.coerceIn(0, heroSlides.lastIndex)
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            HorizontalPager(
                                state = pagerState,
                                key = { page ->
                                    val idx = if (isLooping) page % heroSlides.size else page
                                    val slide = heroSlides.getOrNull(idx)
                                    "${slide?.slideKey() ?: idx}_$page"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { page ->
                                val idx = if (isLooping) page % heroSlides.size else page
                                when (val slide = heroSlides.getOrNull(idx)) {
                                    is HomeHeroSlide.LocalSlide -> LocalMediaHeroCard(
                                        item = slide.item,
                                        heroSettings = heroSettings,
                                        onDetails = { onItemClick(slide.item.id) }
                                    )
                                    is HomeHeroSlide.TrendingSlide -> {
                                        val libMatch = ArtworkModelResolver.findLibraryMatch(
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
                                        if (libMatch != null) {
                                            LocalMediaHeroCard(
                                                item = libMatch,
                                                heroSettings = heroSettings,
                                                onDetails = { onItemClick(libMatch.id) }
                                            )
                                        } else {
                                            TrendingMediaHeroCard(
                                                item = slide.item,
                                                heroSettings = heroSettings,
                                                onDetails = { onPreviewSearchItem(slide.item) }
                                            )
                                        }
                                    }
                                    null -> Unit
                                }
                            }

                            if (isLooping && heroSettings.slideCount > 0 && heroSlides.size <= 15) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(heroSlides.size) { index ->
                                        val active = index == currentActualIndex
                                        Box(
                                            modifier = Modifier
                                                .width(if (active) 18.dp else 6.dp)
                                                .height(5.dp)
                                                .clip(CircleShape)
                                                .background(if (active) AccentGold else Color.White.copy(alpha = 0.35f))
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        EmptyHomeHeroCard(onDiscoverClick = onNavigateToDiscover)
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

            // Customizable Ordered Home Shelves
            homeRows.filter { it.isEnabled && (fanfictionEnabled || it.key != HomeRowKey.DISCOVER_FANFICTION) }.forEach { row ->
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
                                val openRelease: (CalendarReleaseItem) -> Unit = { release ->
                                    if (release.localMediaId != null) {
                                        onItemClick(release.localMediaId)
                                    } else {
                                        val safeSource = when (release.mediaType) {
                                            MediaType.COMIC -> "Metron"
                                            MediaType.SHOW -> "TVMaze"
                                            MediaType.MOVIE -> "TMDb"
                                            MediaType.MANGA -> "MangaDex"
                                            MediaType.NOVEL -> "RanobeDB"
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
                                    if (heroSettings.homeDashboardStyle.equals("COMPACT", ignoreCase = true)) {
                                        // Option C — Dense Timeline vertical hairline guide
                                        val compactSlice = remember(upNextReleases) { upNextReleases.take(5) }
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
                                            items(upNextReleases, key = { "up_${it.id}" }) { release ->
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
                                    title = "Plan to Track",
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
                    (if (fanfictionEnabled) MediaType.filterTabs else MediaType.filterTabs.filter { it != MediaType.FANFICTION }).forEach { type ->
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
                sheetTarget.customCoverPath?.let { if (it.startsWith("http")) it else File(it) }
                    ?: LosslessCoverMapper.toLosslessUrl(sheetTarget.coverUrl)
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
                        AsyncImage(
                            model = coverThumb,
                            contentDescription = sheetTarget.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(44.dp)
                                .height(64.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceCard)
                        )
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

@Composable
private fun LocalMediaHeroCard(
    item: MediaCardSummary,
    heroSettings: HomeHeroSettings,
    onDetails: () -> Unit
) {
    val coverModel: Any = remember(item.customCoverPath, item.coverUrl) {
        ArtworkModelResolver.toCoilModel(ArtworkModelResolver.preferredCover(item)) ?: ""
    }
    val authenticBannerModel = remember(item.customBannerPath, item.bannerUrl, item.artworkRole, item.coverUrl) {
        ArtworkModelResolver.toCoilModel(ArtworkModelResolver.preferredBanner(item))
    }
    val remainingCount = remember(item.totalProgress, item.currentProgress, item.mediaType) {
        val tot = item.totalProgress?.toInt() ?: 0
        val cur = item.currentProgress.toInt()
        if (item.mediaType != MediaType.GAME && item.mediaType != MediaType.VISUAL_NOVEL && tot > cur) {
            (tot - cur).coerceAtLeast(0)
        } else 0
    }
    val progressLabel = remember(item) { formatConciseProgress(item) }

    ChimanewComboHeroCard(
        title = item.title,
        categoryBadge = "${item.mediaType.displayName.uppercase()} \u00B7 ${item.status.displayName.uppercase()}",
        posterModel = coverModel,
        authenticBannerModel = authenticBannerModel,
        bannerFocalX = item.bannerFocalX,
        bannerFocalY = item.bannerFocalY,
        bannerZoom = item.bannerZoom,
        userRating = item.userRating,
        communityScore = item.communityScore,
        progressLabel = progressLabel,
        remainingCount = remainingCount,
        heroSettings = heroSettings,
        onDetailsClick = onDetails
    )
}

@Composable
private fun TrendingMediaHeroCard(
    item: SearchResultItem,
    heroSettings: HomeHeroSettings,
    onDetails: () -> Unit
) {
    val coverModel = remember(item.coverUrl) { LosslessCoverMapper.toLosslessUrl(item.coverUrl) }
    val authenticBannerModel = remember(item.bannerUrl, item.coverUrl) {
        item.bannerUrl?.takeIf { it.isNotBlank() && it != item.coverUrl }?.let { LosslessCoverMapper.toLosslessUrl(it) }
    }
    val progressHint = remember(item) {
        listOfNotNull(item.releaseYear, item.bookAuthor).joinToString(" \u00B7 ")
    }

    ChimanewComboHeroCard(
        title = item.title,
        categoryBadge = "TRENDING \u00B7 ${item.mediaType.displayName.uppercase()}",
        posterModel = coverModel,
        authenticBannerModel = authenticBannerModel,
        bannerFocalX = 0f,
        bannerFocalY = 0f,
        bannerZoom = 1f,
        userRating = null,
        communityScore = item.communityScore,
        progressLabel = progressHint,
        remainingCount = 0,
        heroSettings = heroSettings,
        onDetailsClick = onDetails
    )
}

@Composable
private fun ChimanewComboHeroCard(
    title: String,
    categoryBadge: String,
    posterModel: Any,
    authenticBannerModel: Any?,
    bannerFocalX: Float,
    bannerFocalY: Float,
    bannerZoom: Float,
    userRating: Float?,
    communityScore: Float?,
    progressLabel: String,
    remainingCount: Int,
    heroSettings: HomeHeroSettings,
    onDetailsClick: () -> Unit
) {
    val showUnread = heroSettings.showUnreadBadge && remainingCount > 0

    val heroHeightDp = when (heroSettings.heroHeightPreset.uppercase()) {
        "COMPACT" -> 336.dp
        "TALL" -> 472.dp
        else -> 404.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeightDp)
            .clipToBounds()
            .clickable(onClick = onDetailsClick)
    ) {
        // 1. Backdrop Layer: Authentic Wide Banner -> Cropped/Blurred Cover -> Warm Obsidian Procedural Ambient Glow
        if (authenticBannerModel != null) {
            AsyncImage(
                model = authenticBannerModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(bannerFocalX.coerceIn(-1f, 1f), bannerFocalY.coerceIn(-1f, 1f)),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = bannerZoom.coerceIn(1f, 2.5f),
                        scaleY = bannerZoom.coerceIn(1f, 2.5f)
                    )
                    .alpha(if (heroSettings.dimBanner) 0.56f else 0.94f)
            )
        } else if (heroSettings.allowCroppedFallback) {
            AsyncImage(
                model = posterModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
                    .alpha(if (heroSettings.dimBanner) 0.34f else 0.72f)
            )
        } else {
            // Default Gentle Ambient Glow procedural canvas (never crops portrait covers)
            val auraAlpha = if (heroSettings.dimBanner) 0.24f else 0.42f
            Canvas(modifier = Modifier.fillMaxSize()) {
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

        // Top bar scrim
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

        // Bottom vignette into pure black
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

        // Foreground: Chimanew Dual-Artwork Row (Crisp 2:3 Portrait Poster + Right Info Stack)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
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
                AsyncImage(
                    model = posterModel,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Top Tier: Category Pill (+ Progress if Clean Separation layout 0)
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
                        if (showUnread) {
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
                            HeroRatingStrip(userRating, communityScore, heroSettings.ratingFormat)
                        }
                    }
                    1 -> {
                        if (heroSettings.showRatings) {
                            HeroRatingStrip(userRating, communityScore, heroSettings.ratingFormat)
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
                                HeroRatingStrip(userRating, communityScore, heroSettings.ratingFormat, compact = true)
                            }
                            if (progressLabel.isNotBlank()) {
                                Text(
                                    text = progressLabel,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (showUnread) RemainingCountBadge(count = remainingCount)
                        }
                    }
                    3 -> {
                        if (heroSettings.showRatings) {
                            HeroRatingStrip(userRating, communityScore, heroSettings.ratingFormat)
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
                }
            }
        }
    }
}

@Composable
private fun RemainingCountBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AccentGold.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 1.5.dp)
    ) {
        Text(
            text = "+$count",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = AccentGold
        )
    }
}

@Composable
private fun HeroRatingStrip(
    userRating: Float?,
    communityScore: Float?,
    format: Int,
    compact: Boolean = false
) {
    val u = userRating?.takeIf { it > 0f }
    val c = communityScore?.takeIf { it > 0f }
    if (u == null && c == null) return

    fun formatSingle(score: Float, sourceLabel: String?): String = when (format) {
        1 -> "${(score * 10f).roundToInt()}%"
        2 -> if (sourceLabel != null) "\u2605 ${formatSmartRating(score.toDouble())} \u00B7 $sourceLabel" else "\u2605 ${formatSmartRating(score.toDouble())}"
        else -> "\u2605 ${formatSmartRating(score.toDouble())}"
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (format == 3) {
            if (u != null) {
                HeroRatingPill(text = "\u2605 You ${formatSmartRating(u.toDouble())}")
            }
            if (c != null && (!compact || u == null)) {
                HeroRatingPill(text = "\u2605 ${formatSmartRating(c.toDouble())} Score")
            }
        } else {
            val primaryScore = u ?: c!!
            val label = if (u != null) "Yours" else "Score"
            HeroRatingPill(text = formatSingle(primaryScore, label))
        }
    }
}

@Composable
private fun HeroRatingPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.52f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AccentGold,
            maxLines = 1
        )
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
    showQuickPlus: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickProgress: () -> Unit
) {
    val coverModel = remember(item.customCoverPath, item.coverUrl) {
        item.customCoverPath?.let { if (it.startsWith("http")) it else File(it) }
            ?: LosslessCoverMapper.toLosslessUrl(item.coverUrl)
    }
    val bannerModel = remember(item.customBannerPath, item.bannerUrl, item.coverUrl) {
        item.customBannerPath?.takeIf { it != "NONE" && it.isNotBlank() }?.let { if (it.startsWith("http")) it else File(it) }
            ?: item.bannerUrl?.takeIf { it.isNotBlank() && it != item.coverUrl && item.mediaType != MediaType.BOOK }?.let { LosslessCoverMapper.toLosslessUrl(it) }
    }
    val hasWideBanner = bannerModel != null
    val progressLabel = remember(item) { formatConciseProgress(item) }
    val fraction = remember(item) { computeProgressFraction(item) }
    val badgeText = progressLabel.ifBlank { item.mediaType.displayName }
    val applyDepth = posterStyle.cardDepthEnabled && posterStyle.cardDepthContinueWatchingEnabled

    when (style) {
        ContinueTrackingStyle.CARD -> {
            // Option A Frameless Editorial 16:9 Artwork + Clean Label Below
            val artShape = RoundedCornerShape(8.dp)
            Column(
                modifier = Modifier
                    .width(224.dp)
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
                    AsyncImage(
                        model = bannerModel ?: coverModel,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        alignment = if (hasWideBanner) BiasAlignment(item.bannerFocalX, item.bannerFocalY) else Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    )

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
            Column(
                modifier = Modifier
                    .width(286.dp)
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
                            .width(64.dp)
                            .height(96.dp)
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
                        AsyncImage(
                            model = coverModel,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(92.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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

                        if (fraction > 0f) {
                            Row(
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
                    .width(118.dp)
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
                    AsyncImage(
                        model = coverModel,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize()
                    )

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
        AsyncImage(
            model = remember(release.coverUrl) { ArtworkModelResolver.toCoilModel(release.coverUrl) },
            contentDescription = release.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(46.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.06f))
        )
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = remember(release.coverUrl) { ArtworkModelResolver.toCoilModel(release.coverUrl) },
            contentDescription = release.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(34.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.06f))
        )
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
                    summary.customCoverPath ?: LosslessCoverMapper.toLosslessUrl(summary.coverUrl)
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
