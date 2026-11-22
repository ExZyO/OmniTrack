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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.ui.components.NuvioRailPosterCard
import com.omnitrack.app.ui.screens.detail.components.formatSmartRating
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    onOpenCalendar: () -> Unit,
    onOpenCustomizer: () -> Unit,
    onOpenHeatmap: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenUpdate: () -> Unit,
    onNavigateToLibraryCategory: (MediaType) -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var selectedCategory by remember { mutableStateOf(heroSettings.defaultCategory) }
    LaunchedEffect(heroSettings.defaultCategory) {
        selectedCategory = heroSettings.defaultCategory
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var actionSheetItem by remember { mutableStateOf<MediaCardSummary?>(null) }

    val shelfLimit = heroSettings.shelfItemLimit.coerceIn(8, 24)
    val posterWidthDp = heroSettings.shelfPosterSize.widthDp.dp
    val posterHeightDp = heroSettings.shelfPosterSize.heightDp.dp

    val categoryMedia = remember(allMedia, selectedCategory) {
        if (selectedCategory == MediaType.ALL) allMedia else allMedia.filter { it.mediaType == selectedCategory }
    }
    val categoryTrending = remember(trendingItems, selectedCategory) {
        if (selectedCategory == MediaType.ALL) trendingItems else trendingItems.filter { it.mediaType == selectedCategory }
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

    val continueItems = remember(allContinueItems, shelfLimit) { allContinueItems.take(shelfLimit) }
    val recentItems = remember(allRecentItems, shelfLimit) { allRecentItems.take(shelfLimit) }
    val planItems = remember(allPlanItems, shelfLimit) { allPlanItems.take(shelfLimit) }
    val favoriteItems = remember(allFavoriteItems, shelfLimit) { allFavoriteItems.take(shelfLimit) }

    val upNextReleases = remember(calendarReleases, shelfLimit) {
        val nowSec = System.currentTimeMillis() / 1000L
        val upcoming = calendarReleases.filter { it.airingAtEpochSec >= nowSec - 3600L }
        val libFirst = upcoming.filter { it.localMediaId != null }
        val others = upcoming.filter { it.localMediaId == null }
        (libFirst + others).take(shelfLimit)
    }

    val heroSlides: List<HomeHeroSlide> = remember(
        heroSettings.heroSource,
        heroSettings.strictMode,
        heroSettings.strictRequirements,
        heroSettings.statusFilters,
        allContinueItems,
        allFavoriteItems,
        allPlanItems,
        allRecentItems,
        categoryTrending,
        allMedia
    ) {
        fun findLocalMatch(s: SearchResultItem): MediaCardSummary? = allMedia.firstOrNull { local ->
            local.mediaType == s.mediaType && (
                (s.anilistId != null && local.anilistId == s.anilistId) ||
                    (s.tmdbId != null && local.tmdbId == s.tmdbId) ||
                    local.title.equals(s.title, ignoreCase = true)
                )
        }

        fun hasAuthenticBanner(slide: HomeHeroSlide): Boolean = when (slide) {
            is HomeHeroSlide.LocalSlide -> {
                val i = slide.item
                (!i.customBannerPath.isNullOrBlank() && i.customBannerPath != "NONE") ||
                    (!i.bannerUrl.isNullOrBlank() && i.bannerUrl != i.coverUrl)
            }
            is HomeHeroSlide.TrendingSlide -> {
                !slide.item.bannerUrl.isNullOrBlank() && slide.item.bannerUrl != slide.item.coverUrl
            }
        }

        fun hasValidCover(slide: HomeHeroSlide): Boolean = when (slide) {
            is HomeHeroSlide.LocalSlide -> !slide.item.customCoverPath.isNullOrBlank() || slide.item.coverUrl.isNotBlank()
            is HomeHeroSlide.TrendingSlide -> slide.item.coverUrl.isNotBlank()
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

        val primary: List<HomeHeroSlide> = when (heroSettings.heroSource) {
            HomeHeroSource.CONTINUE -> allContinueItems.map { HomeHeroSlide.LocalSlide(it) }
            HomeHeroSource.FAVORITES -> allFavoriteItems.map { HomeHeroSlide.LocalSlide(it) }
            HomeHeroSource.PLAN_TO_TRACK -> allPlanItems.map { HomeHeroSlide.LocalSlide(it) }
            HomeHeroSource.TRENDING -> categoryTrending.map { HomeHeroSlide.TrendingSlide(it) }
        }
        val filteredPrimary = primary.filter(::matchesFilters).distinctBy { it.slideKey() }
        val resolved = if (filteredPrimary.isNotEmpty()) {
            filteredPrimary
        } else {
            listOf(
                allContinueItems.map { HomeHeroSlide.LocalSlide(it) },
                allRecentItems.map { HomeHeroSlide.LocalSlide(it) },
                categoryTrending.map { HomeHeroSlide.TrendingSlide(it) },
                allMedia.map { HomeHeroSlide.LocalSlide(it) }
            ).firstOrNull { pool ->
                pool.any(::matchesFilters)
            }?.filter(::matchesFilters)?.distinctBy { it.slideKey() }
                ?: if (heroSettings.strictMode || heroSettings.statusFilters.isNotEmpty()) emptyList()
                else primary.ifEmpty { allRecentItems.map { HomeHeroSlide.LocalSlide(it) } }
        }
        // Filter full candidate pool BEFORE capping to 8 slides
        resolved.take(8)
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

    Box(
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

                        LaunchedEffect(heroKeys) {
                            if (heroSlides.isNotEmpty() && !pagerState.isScrollInProgress) {
                                val curMod = if (isLooping) pagerState.currentPage % heroSlides.size else 0
                                val target = if (isLooping) startIndex + curMod else 0
                                runCatching { pagerState.scrollToPage(target) }
                            }
                        }

                        LaunchedEffect(pagerState, heroSlides.size, heroSettings.autoRotate) {
                            if (!isLooping || !heroSettings.autoRotate) return@LaunchedEffect
                            while (isActive) {
                                delay(6000L)
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
                                        onQuickProgress = { onQuickProgress(slide.item) },
                                        onDetails = { onItemClick(slide.item.id) }
                                    )
                                    is HomeHeroSlide.TrendingSlide -> {
                                        val libMatch = allMedia.firstOrNull { local ->
                                            local.mediaType == slide.item.mediaType && (
                                                (slide.item.anilistId != null && local.anilistId == slide.item.anilistId) ||
                                                    (slide.item.tmdbId != null && local.tmdbId == slide.item.tmdbId) ||
                                                    local.title.equals(slide.item.title, ignoreCase = true)
                                                )
                                        }
                                        if (libMatch != null) {
                                            LocalMediaHeroCard(
                                                item = libMatch,
                                                heroSettings = heroSettings,
                                                onQuickProgress = { onQuickProgress(libMatch) },
                                                onDetails = { onItemClick(libMatch.id) }
                                            )
                                        } else {
                                            TrendingMediaHeroCard(
                                                item = slide.item,
                                                heroSettings = heroSettings,
                                                onAdd = { onAddSearchItem(slide.item) },
                                                onDetails = { onPreviewSearchItem(slide.item) }
                                            )
                                        }
                                    }
                                    null -> Unit
                                }
                            }

                            if (isLooping) {
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
            homeRows.filter { it.isEnabled }.forEach { row ->
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
                            item(key = "row_up_next") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HomeShelfHeader(
                                        title = "Up Next & Airing",
                                        actionLabel = "Calendar",
                                        onActionClick = onOpenCalendar
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(upNextReleases, key = { "up_${it.id}" }) { release ->
                                            UpNextCalendarCard(
                                                release = release,
                                                onClick = {
                                                    if (release.localMediaId != null) {
                                                        onItemClick(release.localMediaId)
                                                    } else {
                                                        onPreviewSearchItem(
                                                            SearchResultItem(
                                                                title = release.title,
                                                                coverUrl = release.coverUrl,
                                                                mediaType = release.mediaType,
                                                                sourceProvider = if (release.mediaType == MediaType.COMIC) "Metron" else "AniList",
                                                                anilistId = release.anilistId,
                                                                malId = release.malId,
                                                                metronId = release.metronId,
                                                                comicPublisher = if (release.mediaType == MediaType.COMIC) release.formatLabel.takeIf { it.isNotBlank() && it != "COMIC" } else null
                                                            )
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HomeRowKey.STATS -> {
                        item(key = "row_stats") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HomeShelfHeader(
                                    title = "Overview",
                                    actionLabel = "History",
                                    onActionClick = onNavigateToHistory
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(DarkSurface)
                                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                                        .clickable(onClick = onOpenHeatmap)
                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CompactStatCell(
                                        value = "${statsSnapshot.first}",
                                        label = "Active",
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(modifier = Modifier.width(1.dp).height(26.dp).background(GlassBorder))
                                    CompactStatCell(
                                        value = "${statsSnapshot.second}d",
                                        label = "Streak",
                                        highlight = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(modifier = Modifier.width(1.dp).height(26.dp).background(GlassBorder))
                                    CompactStatCell(
                                        value = "${statsSnapshot.third}",
                                        label = "Completed",
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "90d Map",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentGold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Open Heatmap",
                                            tint = AccentGold,
                                            modifier = Modifier.size(16.dp)
                                        )
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
                        val shownTrending = categoryTrending.take(shelfLimit)
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
                    HomeRowKey.DISCOVER_VNS -> {
                        item(key = "row_${row.key.name}") {
                            LaunchedEffect(row.key) {
                                onRequestDiscoverShelf(row.key)
                            }
                            val shelfItems = categoryDiscoverShelves[row.key].orEmpty().take(shelfLimit)
                            if (shelfItems.isEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HomeShelfHeader(
                                        title = row.key.displayName,
                                        actionLabel = "Discover",
                                        onActionClick = onNavigateToDiscover
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .padding(horizontal = 20.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(DarkSurface)
                                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = AccentGold,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(22.dp)
                                        )
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
                                    onPreviewSearchItem = onPreviewSearchItem
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
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkSurface.copy(alpha = 0.88f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                        .clickable { categoryMenuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
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
                    containerColor = DarkSurfaceElevated
                ) {
                    MediaType.filterTabs.forEach { type ->
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
                containerColor = DarkSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                                .clip(RoundedCornerShape(8.dp))
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

                    HorizontalDivider(color = GlassBorder, thickness = 1.dp)

                    ContinueActionSheetRow(
                        icon = Icons.Default.Info,
                        label = "Go to Details",
                        onClick = {
                            actionSheetItem = null
                            onItemClick(sheetTarget.id)
                        }
                    )
                    ContinueActionSheetRow(
                        icon = Icons.Default.Add,
                        label = heroPrimaryCtaLabel(sheetTarget),
                        accent = true,
                        onClick = {
                            actionSheetItem = null
                            onQuickProgress(sheetTarget)
                        }
                    )
                    ContinueActionSheetRow(
                        icon = Icons.Default.Check,
                        label = "Mark Completed",
                        onClick = {
                            actionSheetItem = null
                            onMarkCompleted(sheetTarget)
                        }
                    )
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
            .clip(RoundedCornerShape(12.dp))
            .background(if (accent) AccentGold.copy(alpha = 0.14f) else DarkSurfaceElevated)
            .border(1.dp, if (accent) AccentGold.copy(alpha = 0.45f) else GlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
            .size(36.dp)
            .clip(CircleShape)
            .background(DarkSurface.copy(alpha = 0.85f))
            .border(1.dp, GlassBorder, CircleShape)
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
    onQuickProgress: () -> Unit,
    onDetails: () -> Unit
) {
    val coverModel = remember(item.customCoverPath, item.coverUrl) {
        item.customCoverPath?.let { if (it.startsWith("http")) it else File(it) }
            ?: LosslessCoverMapper.toLosslessUrl(item.coverUrl)
    }
    val authenticBannerModel = remember(item.customBannerPath, item.bannerUrl, item.coverUrl) {
        item.customBannerPath?.takeIf { it != "NONE" && it.isNotBlank() }?.let { if (it.startsWith("http")) it else File(it) }
            ?: item.bannerUrl?.takeIf { it.isNotBlank() && it != item.coverUrl }?.let { LosslessCoverMapper.toLosslessUrl(it) }
    }
    val remainingCount = remember(item.totalProgress, item.currentProgress, item.mediaType) {
        val tot = item.totalProgress?.toInt() ?: 0
        val cur = item.currentProgress.toInt()
        if (item.mediaType != MediaType.GAME && item.mediaType != MediaType.VISUAL_NOVEL && tot > cur) {
            (tot - cur).coerceAtLeast(0)
        } else 0
    }
    val progressLabel = remember(item) { formatConciseProgress(item) }
    val primaryCta = remember(item.mediaType, item.bookTrackingMode) { heroPrimaryCtaLabel(item) }

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
        primaryCta = primaryCta,
        heroSettings = heroSettings,
        onPrimaryClick = onQuickProgress,
        onDetailsClick = onDetails
    )
}

@Composable
private fun TrendingMediaHeroCard(
    item: SearchResultItem,
    heroSettings: HomeHeroSettings,
    onAdd: () -> Unit,
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
        primaryCta = "+ Add",
        heroSettings = heroSettings,
        onPrimaryClick = onAdd,
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
    primaryCta: String,
    heroSettings: HomeHeroSettings,
    onPrimaryClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    val showUnread = heroSettings.showUnreadBadge && remainingCount > 0
    val effectiveCta = if (heroSettings.metadataLayout == 3 && progressLabel.isNotBlank()) {
        "$primaryCta \u2022 $progressLabel"
    } else {
        primaryCta
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(404.dp)
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.Black.copy(alpha = 0.58f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = categoryBadge,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AccentGold)
                            .clickable(onClick = onPrimaryClick)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = effectiveCta,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmoledBlack,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(20.dp))
                            .clickable(onClick = onDetailsClick)
                            .padding(horizontal = 13.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Details",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                    if (heroSettings.metadataLayout == 3 && showUnread) {
                        RemainingCountBadge(count = remainingCount)
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
            .clip(RoundedCornerShape(10.dp))
            .background(AccentGold.copy(alpha = 0.20f))
            .border(1.dp, AccentGold.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
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
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.52f))
            .border(1.dp, AccentGold.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
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
                .clip(RoundedCornerShape(10.dp))
                .background(AccentGold)
                .clickable(onClick = onDiscoverClick)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Explore Discover",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = AmoledBlack
            )
        }
    }
}

@Composable
private fun HeroActionButtonsRow(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(AccentGold)
                .clickable(onClick = onPrimaryClick)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                text = primaryLabel,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = AmoledBlack
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceElevated.copy(alpha = 0.88f))
                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                .clickable(onClick = onDetailsClick)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Details",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
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

    when (style) {
        ContinueTrackingStyle.CARD -> {
            // Nuvio 16:9 Full-Bleed Overlay Card
            Box(
                modifier = Modifier
                    .width(210.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceCard)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            ) {
                AsyncImage(
                    model = bannerModel ?: coverModel,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    alignment = if (hasWideBanner) BiasAlignment(item.bannerFocalX, item.bannerFocalY) else Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.42f to AmoledBlack.copy(alpha = 0.25f),
                                0.78f to AmoledBlack.copy(alpha = 0.80f),
                                1.0f to AmoledBlack.copy(alpha = 0.96f)
                            )
                        )
                )

                // Top-left status/type pill
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.mediaType.displayName.uppercase(),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                }

                // Top-right quick +1 action
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(AccentGold)
                        .clickable(onClick = onQuickProgress),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.mediaType == MediaType.MOVIE) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = "Increment Progress",
                        tint = AmoledBlack,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = item.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(999.dp))
                                .background(AccentGold)
                        )
                    }
                }
            }
        }

        ContinueTrackingStyle.WIDE -> {
            // Nuvio Wide Split Card
            Row(
                modifier = Modifier
                    .width(308.dp)
                    .height(112.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            ) {
                Box(
                    modifier = Modifier
                        .width(76.dp)
                        .fillMaxHeight()
                        .background(DarkSurfaceCard)
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
                        .fillMaxHeight()
                        .padding(11.dp),
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
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(AccentGold)
                                    .clickable(onClick = onQuickProgress),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.mediaType == MediaType.MOVIE) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = "Increment Progress",
                                    tint = AmoledBlack,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Text(
                            text = badgeText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
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
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.14f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(999.dp))
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
        }

        ContinueTrackingStyle.POSTER -> {
            // Nuvio Vertical 2:3 Poster Card
            Column(
                modifier = Modifier.width(118.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceCard)
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                ) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize()
                    )

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
                            tint = AmoledBlack,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(DarkSurface.copy(alpha = 0.92f))
                                .padding(horizontal = 6.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(86.dp)
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.16f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(AccentGold)
                                )
                            }
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

@Composable
private fun UpNextCalendarCard(
    release: CalendarReleaseItem,
    onClick: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val dayTime = remember(release.airingAtEpochSec) {
        val dt = Instant.ofEpochSecond(release.airingAtEpochSec).atZone(zone)
        val dayFmt = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)
        dt.format(dayFmt)
    }

    Row(
        modifier = Modifier
            .width(214.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = release.coverUrl,
            contentDescription = release.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(42.dp)
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceCard)
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
                text = if (release.episodeNumber != null) {
                    if (release.mediaType == MediaType.COMIC) "Issue #${release.episodeNumber}" else "Episode ${release.episodeNumber}"
                } else release.formatLabel,
                fontSize = 11.5.sp,
                color = TextSecondary
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
private fun HomeDiscoverSearchShelf(
    title: String,
    actionLabel: String,
    items: List<SearchResultItem>,
    allMedia: List<MediaCardSummary>,
    cardWidth: Dp,
    cardHeight: Dp,
    onActionClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    onPreviewSearchItem: (SearchResultItem) -> Unit
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
                    cardHeight = cardHeight
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
                    cardHeight = cardHeight
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
