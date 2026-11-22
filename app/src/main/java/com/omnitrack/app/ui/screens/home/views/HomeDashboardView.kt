package com.omnitrack.app.ui.screens.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface HomeHeroSlide {
    data class LocalSlide(val item: MediaCardSummary) : HomeHeroSlide
    data class TrendingSlide(val item: SearchResultItem) : HomeHeroSlide
}

@Composable
fun HomeDashboardView(
    allMedia: List<MediaCardSummary>,
    trendingItems: List<SearchResultItem>,
    calendarReleases: List<CalendarReleaseItem>,
    watchedTimestamps90d: List<Long>,
    homeRows: List<HomeRowItem>,
    heroSource: HomeHeroSource,
    hasUpdateAvailable: Boolean,
    onItemClick: (Long) -> Unit,
    onPreviewSearchItem: (SearchResultItem) -> Unit,
    onQuickProgress: (MediaCardSummary) -> Unit,
    onAddSearchItem: (SearchResultItem) -> Unit,
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
    var selectedCategory by remember { mutableStateOf(MediaType.ALL) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    val categoryMedia = remember(allMedia, selectedCategory) {
        if (selectedCategory == MediaType.ALL) allMedia else allMedia.filter { it.mediaType == selectedCategory }
    }
    val categoryTrending = remember(trendingItems, selectedCategory) {
        if (selectedCategory == MediaType.ALL) trendingItems else trendingItems.filter { it.mediaType == selectedCategory }
    }

    val continueItems = remember(categoryMedia) {
        categoryMedia.filter { it.status == TrackingStatus.IN_PROGRESS }
            .sortedByDescending { it.lastUpdated }
            .take(15)
    }
    val recentItems = remember(categoryMedia) {
        categoryMedia.sortedByDescending { it.lastUpdated }.take(15)
    }
    val planItems = remember(categoryMedia) {
        categoryMedia.filter { it.status == TrackingStatus.PLAN_TO_TRACK }
            .sortedByDescending { it.lastUpdated }
            .take(15)
    }
    val favoriteItems = remember(categoryMedia) {
        categoryMedia
            .filter { (it.userRating ?: it.communityScore ?: 0f) > 0f }
            .sortedWith(
                compareByDescending<MediaCardSummary> { it.userRating ?: 0f }
                    .thenByDescending { it.communityScore ?: 0f }
            )
            .take(15)
    }

    val upNextReleases = remember(calendarReleases) {
        val nowSec = System.currentTimeMillis() / 1000L
        val upcoming = calendarReleases.filter { it.airingAtEpochSec >= nowSec - 3600L }
        val libFirst = upcoming.filter { it.localMediaId != null }
        val others = upcoming.filter { it.localMediaId == null }
        (libFirst + others).take(14)
    }

    val heroSlides: List<HomeHeroSlide> = remember(
        heroSource,
        continueItems,
        favoriteItems,
        planItems,
        recentItems,
        categoryTrending,
        allMedia
    ) {
        val primary: List<HomeHeroSlide> = when (heroSource) {
            HomeHeroSource.CONTINUE -> continueItems.map { HomeHeroSlide.LocalSlide(it) }
            HomeHeroSource.FAVORITES -> favoriteItems.map { HomeHeroSlide.LocalSlide(it) }
            HomeHeroSource.PLAN_TO_TRACK -> planItems.map { HomeHeroSlide.LocalSlide(it) }
            HomeHeroSource.TRENDING -> categoryTrending.map { HomeHeroSlide.TrendingSlide(it) }
        }
        val resolved = when {
            primary.isNotEmpty() -> primary
            continueItems.isNotEmpty() -> continueItems.map { HomeHeroSlide.LocalSlide(it) }
            recentItems.isNotEmpty() -> recentItems.map { HomeHeroSlide.LocalSlide(it) }
            categoryTrending.isNotEmpty() -> categoryTrending.map { HomeHeroSlide.TrendingSlide(it) }
            else -> allMedia.take(6).map { HomeHeroSlide.LocalSlide(it) }
        }
        resolved.take(6)
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
            // Adaptive Hero Carousel
            item(key = "home_hero_carousel") {
                if (heroSlides.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { heroSlides.size })
                    Box(modifier = Modifier.fillMaxWidth()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth()
                        ) { pageIdx ->
                            when (val slide = heroSlides[pageIdx]) {
                                is HomeHeroSlide.LocalSlide -> LocalMediaHeroCard(
                                    item = slide.item,
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
                                            onQuickProgress = { onQuickProgress(libMatch) },
                                            onDetails = { onItemClick(libMatch.id) }
                                        )
                                    } else {
                                        TrendingMediaHeroCard(
                                            item = slide.item,
                                            onAdd = { onAddSearchItem(slide.item) },
                                            onDetails = { onPreviewSearchItem(slide.item) }
                                        )
                                    }
                                }
                            }
                        }

                        if (heroSlides.size > 1) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(heroSlides.size) { index ->
                                    val active = index == pagerState.currentPage
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
                                                onClick = { onItemClick(item.id) },
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
                                                                sourceProvider = "AniList",
                                                                anilistId = release.anilistId,
                                                                malId = release.malId
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
                                    onActionClick = { onNavigateToLibraryCategory(selectedCategory) },
                                    onItemClick = onItemClick
                                )
                            }
                        }
                    }

                    HomeRowKey.TRENDING -> {
                        if (categoryTrending.isNotEmpty()) {
                            item(key = "row_trending") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HomeShelfHeader(
                                        title = "Trending Now",
                                        actionLabel = "Discover",
                                        onActionClick = onNavigateToDiscover
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(
                                            items = categoryTrending,
                                            key = { "tr_${it.mediaType}_${it.anilistId ?: it.steamAppId ?: it.openLibraryId ?: it.title}" }
                                        ) { item ->
                                            val libMatch = allMedia.firstOrNull { local ->
                                                local.mediaType == item.mediaType && (
                                                    (item.anilistId != null && local.anilistId == item.anilistId) ||
                                                        (item.steamAppId != null && local.steamAppId == item.steamAppId) ||
                                                        local.title.equals(item.title, ignoreCase = true)
                                                    )
                                            }
                                            NuvioRailPosterCard(
                                                title = item.title,
                                                coverUrl = libMatch?.customCoverPath ?: libMatch?.coverUrl ?: item.coverUrl,
                                                subtitle = item.mediaType.displayName + (item.releaseYear?.let { " \u00B7 $it" } ?: ""),
                                                rating = libMatch?.userRating ?: libMatch?.communityScore ?: item.communityScore,
                                                onClick = {
                                                    if (libMatch != null) onItemClick(libMatch.id) else onPreviewSearchItem(item)
                                                },
                                                cardWidth = 120.dp,
                                                cardHeight = 180.dp
                                            )
                                        }
                                    }
                                }
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

@Composable
private fun LocalMediaHeroCard(
    item: MediaCardSummary,
    onQuickProgress: () -> Unit,
    onDetails: () -> Unit
) {
    val coverModel = remember(item.customCoverPath, item.coverUrl) {
        item.customCoverPath?.let { if (it.startsWith("http")) it else File(it) }
            ?: LosslessCoverMapper.toLosslessUrl(item.coverUrl)
    }
    val bannerModel = remember(item.customBannerPath, item.bannerUrl) {
        item.customBannerPath?.let { if (it.startsWith("http")) it else File(it) }
            ?: item.bannerUrl?.takeIf { it.isNotBlank() }?.let { LosslessCoverMapper.toLosslessUrl(it) }
    }
    val useEditorialBookJacket = item.customBannerPath.isNullOrBlank() &&
        (item.mediaType == MediaType.BOOK || item.bannerUrl.isNullOrBlank())

    val ratingValue = item.userRating ?: item.communityScore
    val progressLabel = remember(item) { formatConciseProgress(item) }
    val primaryCta = remember(item.mediaType, item.bookTrackingMode) {
        when (item.mediaType) {
            MediaType.BOOK -> when (BookTrackingMode.fromName(item.bookTrackingMode)) {
                BookTrackingMode.AUDIO_MINUTES -> "+15m Listen"
                BookTrackingMode.PERCENT -> "+5% Read"
                BookTrackingMode.PAGES -> "+10 Pages"
            }
            MediaType.GAME, MediaType.VISUAL_NOVEL -> "+0.5 Hrs"
            MediaType.MOVIE -> "Mark Watched"
            else -> "+1 ${item.mediaType.unitLabel}"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp)
            .clickable(onClick = onDetails)
    ) {
        if (useEditorialBookJacket) {
            AsyncImage(
                model = coverModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.24f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                AmoledBlack.copy(alpha = 0.65f),
                                AmoledBlack.copy(alpha = 0.72f),
                                AmoledBlack
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 54.dp, bottom = 28.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(132.dp)
                        .height(198.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = buildString {
                        append(item.mediaType.displayName.uppercase())
                        append(" \u00B7 ")
                        append(item.status.displayName.uppercase())
                        if (ratingValue != null && ratingValue > 0f) {
                            append(" \u00B7 \u2605 ")
                            append(formatSmartRating(ratingValue.toDouble()))
                        }
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold,
                    letterSpacing = 0.9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = listOfNotNull(
                    item.bookAuthor?.takeIf { it.isNotBlank() },
                    progressLabel.takeIf { it.isNotBlank() }
                ).joinToString(" \u00B7 ")
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                HeroActionButtonsRow(
                    primaryLabel = primaryCta,
                    onPrimaryClick = onQuickProgress,
                    onDetailsClick = onDetails
                )
            }
        } else {
            AsyncImage(
                model = bannerModel ?: coverModel,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                AmoledBlack.copy(alpha = 0.45f),
                                Color.Transparent,
                                AmoledBlack.copy(alpha = 0.85f),
                                AmoledBlack
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = buildString {
                        append(item.mediaType.displayName.uppercase())
                        append(" \u00B7 ")
                        append(item.status.displayName.uppercase())
                        if (ratingValue != null && ratingValue > 0f) {
                            append(" \u00B7 \u2605 ")
                            append(formatSmartRating(ratingValue.toDouble()))
                        }
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold,
                    letterSpacing = 0.9.sp
                )
                if (!item.clearartUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = LosslessCoverMapper.toLosslessUrl(item.clearartUrl),
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(64.dp)
                            .fillMaxWidth(0.72f),
                        alignment = Alignment.CenterStart
                    )
                } else {
                    Text(
                        text = item.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (progressLabel.isNotBlank()) {
                    Text(
                        text = progressLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                HeroActionButtonsRow(
                    primaryLabel = primaryCta,
                    onPrimaryClick = onQuickProgress,
                    onDetailsClick = onDetails
                )
            }
        }
    }
}

@Composable
private fun TrendingMediaHeroCard(
    item: SearchResultItem,
    onAdd: () -> Unit,
    onDetails: () -> Unit
) {
    val coverModel = remember(item.coverUrl) { LosslessCoverMapper.toLosslessUrl(item.coverUrl) }
    val bannerModel = remember(item.bannerUrl) { item.bannerUrl?.takeIf { it.isNotBlank() }?.let { LosslessCoverMapper.toLosslessUrl(it) } }
    val useEditorial = bannerModel == null || item.mediaType == MediaType.BOOK

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp)
            .clickable(onClick = onDetails)
    ) {
        AsyncImage(
            model = if (useEditorial) coverModel else bannerModel,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (useEditorial) 0.24f else 1f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AmoledBlack.copy(alpha = 0.55f),
                            AmoledBlack.copy(alpha = 0.25f),
                            AmoledBlack.copy(alpha = 0.85f),
                            AmoledBlack
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(if (useEditorial) Alignment.BottomCenter else Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = if (useEditorial) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (useEditorial) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(126.dp)
                        .height(188.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                )
            }
            Text(
                text = buildString {
                    append("TRENDING \u00B7 ")
                    append(item.mediaType.displayName.uppercase())
                    item.releaseYear?.let { append(" \u00B7 $it") }
                    item.communityScore?.takeIf { it > 0f }?.let {
                        append(" \u00B7 \u2605 ")
                        append(formatSmartRating(it.toDouble()))
                    }
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold,
                letterSpacing = 0.9.sp
            )
            Text(
                text = item.title,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = if (useEditorial) TextAlign.Center else TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            HeroActionButtonsRow(
                primaryLabel = "+ Add to Library",
                onPrimaryClick = onAdd,
                onDetailsClick = onDetails
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

@Composable
private fun ContinueTrackingCard(
    item: MediaCardSummary,
    onClick: () -> Unit,
    onQuickProgress: () -> Unit
) {
    val thumbModel = remember(item.customBannerPath, item.bannerUrl, item.customCoverPath, item.coverUrl) {
        item.customBannerPath?.let { if (it.startsWith("http")) it else File(it) }
            ?: item.bannerUrl?.takeIf { it.isNotBlank() && item.mediaType != MediaType.BOOK }?.let { LosslessCoverMapper.toLosslessUrl(it) }
            ?: item.customCoverPath?.let { if (it.startsWith("http")) it else File(it) }
            ?: LosslessCoverMapper.toLosslessUrl(item.coverUrl)
    }
    val progressLabel = remember(item) { formatConciseProgress(item) }
    val fraction = remember(item) { computeProgressFraction(item) }

    Column(
        modifier = Modifier
            .width(176.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(98.dp)
                .background(DarkSurfaceCard)
        ) {
            AsyncImage(
                model = thumbModel,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, AmoledBlack.copy(alpha = 0.72f))
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(AccentGold)
                    .clickable(onClick = onQuickProgress),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.mediaType == MediaType.MOVIE) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = "Increment Progress",
                    tint = AmoledBlack,
                    modifier = Modifier.size(17.dp)
                )
            }
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.16f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .background(AccentGold)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (progressLabel.isNotBlank()) progressLabel else item.mediaType.displayName,
                fontSize = 11.5.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                text = if (release.episodeNumber != null) "Episode ${release.episodeNumber}" else release.formatLabel,
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
private fun MediaSummaryRail(
    title: String,
    actionLabel: String,
    items: List<MediaCardSummary>,
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
                    cardWidth = 120.dp,
                    cardHeight = 180.dp
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
