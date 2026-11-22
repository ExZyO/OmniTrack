package com.omnitrack.app.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import coil3.compose.AsyncImage
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.preferences.ContinueTrackingStyle
import com.omnitrack.app.data.preferences.HomeHeroSettings
import com.omnitrack.app.data.preferences.HomeLayoutDefaults
import com.omnitrack.app.data.preferences.PosterCardStyle
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.ui.components.CustomEntryEditorSheet
import com.omnitrack.app.ui.components.DiagnosticsSheet
import com.omnitrack.app.ui.components.LibraryFastScroller
import com.omnitrack.app.ui.components.MediaCard
import com.omnitrack.app.ui.components.MediaTypeTabRow
import com.omnitrack.app.ui.components.NavDestination
import com.omnitrack.app.ui.components.OmniFloatingNavBar
import com.omnitrack.app.ui.components.StatusFilterChips
import com.omnitrack.app.ui.components.UpdateBottomSheet
import com.omnitrack.app.ui.components.rememberOmniNavBarScrollState
import com.omnitrack.app.ui.screens.detail.EphemeralPreviewHolder
import com.omnitrack.app.ui.screens.detail.components.formatSmartRating
import com.omnitrack.app.ui.screens.home.views.ActivityHeatmapSheet
import com.omnitrack.app.ui.screens.home.views.DiscoverCustomizerSheet
import com.omnitrack.app.ui.screens.home.views.DiscoverView
import com.omnitrack.app.ui.screens.home.views.HistoryView
import com.omnitrack.app.ui.screens.home.views.HomeCustomizerSheet
import com.omnitrack.app.ui.screens.home.views.HomeDashboardView
import com.omnitrack.app.ui.screens.home.views.LibraryFilterSortSheet
import com.omnitrack.app.ui.screens.home.views.ReleaseCalendarSheet
import com.omnitrack.app.ui.screens.home.views.SettingsView
import com.omnitrack.app.ui.screens.home.views.formatConciseProgress
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import java.io.File
import com.omnitrack.app.updater.AppRelease
import com.omnitrack.app.updater.AppUpdateChecker
import com.omnitrack.app.util.AppLogger
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import android.widget.Toast
import com.omnitrack.app.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onItemClick: (Long) -> Unit,
    onNavigateToArtworkPriority: () -> Unit = {},
    onNavigateToVisualPresentation: () -> Unit = {},
    onNavigateToDetailsPageLayout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefManager = remember(context) { (context.applicationContext as com.omnitrack.app.OmniTrackApplication).preferenceManager }
    val posterStyle by prefManager.posterCardStyle.collectAsState(initial = PosterCardStyle())
    val customPosterPattern by prefManager.customPosterUrlPattern.collectAsState(initial = "")
    val uiState by viewModel.uiState.collectAsState()
    val navScrollState = rememberOmniNavBarScrollState()
    val hazeState = remember { HazeState() }
    val gridStates = remember { List(MediaType.filterTabs.size) { androidx.compose.foundation.lazy.grid.LazyGridState() } }
    val listStates = remember { List(MediaType.filterTabs.size) { LazyListState() } }

    var currentNavDestination by rememberSaveable { mutableStateOf(NavDestination.HOME) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppRelease?>(null) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    var showHomeCustomizerSheet by remember { mutableStateOf(false) }
    var showDiscoverCustomizerSheet by remember { mutableStateOf(false) }
    var showReleaseCalendarSheet by remember { mutableStateOf(false) }
    var showActivityHeatmapSheet by remember { mutableStateOf(false) }
    var showLibraryFilterSheet by remember { mutableStateOf(false) }
    var showCustomEntrySheet by remember { mutableStateOf(false) }
    var customEntryInitTitle by remember { mutableStateOf("") }
    var customEntryInitType by remember { mutableStateOf(MediaType.NOVEL) }
    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val homeRows by prefManager.homeRowOrder.collectAsState(initial = HomeLayoutDefaults.defaultRows)
    val heroSettings by prefManager.homeHeroSettings.collectAsState(initial = HomeHeroSettings())
    val continueTrackingStyle by prefManager.continueTrackingStyle.collectAsState(initial = ContinueTrackingStyle.CARD)
    val tmdbKey by prefManager.tmdbApiKey.collectAsState(initial = "")
    val libraryViewMode by prefManager.libraryViewMode.collectAsState(initial = "GRID_3")
    val librarySortMode by prefManager.librarySortMode.collectAsState(initial = "RECENT")
    val calendarReleases by viewModel.calendarReleases.collectAsState()
    val isLoadingCalendar by viewModel.isLoadingCalendar.collectAsState()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsState()
    val trendingItems by viewModel.trendingItems.collectAsState()
    val categoryDiscoverShelves by viewModel.categoryDiscoverShelves.collectAsState()
    val watchedTimestamps90d by viewModel.watchedTimestamps90d.collectAsState()
    val undoEvent by viewModel.undoEvent.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadHomeTrending()
        viewModel.loadReleaseCalendar()
    }

    LaunchedEffect(undoEvent?.createdAtMs) {
        val active = undoEvent ?: return@LaunchedEffect
        delay(4500L)
        if (viewModel.undoEvent.value?.createdAtMs == active.createdAtMs) {
            viewModel.dismissUndoEvent()
        }
    }
    var selectedMangaSubtype by rememberSaveable { mutableStateOf("ALL") }
    var selectedNovelSubtype by rememberSaveable { mutableStateOf("ALL") }
    var selectedVnOriginSubtype by rememberSaveable { mutableStateOf("ALL") }
    var selectedVnLengthSubtype by rememberSaveable { mutableStateOf("ALL") }
    var selectedGamePlatform by rememberSaveable { mutableStateOf("ALL") }
    var selectedGameCompletion by rememberSaveable { mutableStateOf("ALL") }
    var selectedBookFormat by rememberSaveable { mutableStateOf("ALL") }
    var selectedBookCategory by rememberSaveable { mutableStateOf("ALL") }
    var selectedComicPublisher by rememberSaveable { mutableStateOf("ALL") }
    var selectedComicFormat by rememberSaveable { mutableStateOf("ALL") }
    val searchFocusRequester = remember { FocusRequester() }

    // Automatic background update check (detects ready releases & active CI builds)
    LaunchedEffect(Unit) {
        val release = AppUpdateChecker().checkForUpdate(context)
        if (release != null) {
            availableUpdate = release
            if (!release.isBuilding) {
                showUpdateSheet = true
            }
        }
    }

    // Background CI build auto-poller while GitHub Actions is compiling a newer APK
    LaunchedEffect(availableUpdate?.isBuilding, availableUpdate?.version) {
        val cur = availableUpdate
        if (cur == null || !cur.isBuilding) return@LaunchedEffect
        val checker = AppUpdateChecker()
        while (isActive) {
            delay(15_000L)
            val updated = checker.checkForUpdate(context)
            if (updated != null && updated != cur) {
                availableUpdate = updated
                if (!updated.isBuilding) {
                    showUpdateSheet = true
                    break
                }
            }
        }
    }

    // Kinematic category pager
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { MediaType.filterTabs.size }
    )

    // Sync pager swipe with viewmodel tab selection
    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectMediaType(MediaType.filterTabs[pagerState.currentPage])
    }

    // Auto-focus search text field when opened
    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            delay(120)
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // System Back Gesture handling:
    // 1. If search is open -> close search
    // 2. If on another tab (Discover, History, Settings) -> return to Library (Home page)
    BackHandler(enabled = isSearchVisible || currentNavDestination != NavDestination.HOME) {
        if (isSearchVisible) {
            isSearchVisible = false
            searchQuery = ""
        } else if (currentNavDestination != NavDestination.HOME) {
            currentNavDestination = NavDestination.HOME
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        com.omnitrack.app.ui.components.LocalCustomPosterPattern provides customPosterPattern
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(AmoledBlack)
                .nestedScroll(navScrollState.nestedScrollConnection)
        ) {
        AnimatedContent(
            targetState = currentNavDestination,
            transitionSpec = {
                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "navDestinationSwitch",
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        ) { destination ->
            when (destination) {
                NavDestination.HOME -> {
                    HomeDashboardView(
                        allMedia = uiState.allMediaSummaries,
                        trendingItems = trendingItems,
                        calendarReleases = calendarReleases,
                        watchedTimestamps90d = watchedTimestamps90d,
                        homeRows = homeRows,
                        heroSettings = heroSettings,
                        continueTrackingStyle = continueTrackingStyle,
                        categoryDiscoverShelves = categoryDiscoverShelves,
                        hasUpdateAvailable = availableUpdate != null,
                        onItemClick = onItemClick,
                        onPreviewSearchItem = { searchItem ->
                            EphemeralPreviewHolder.previewItem = searchItem.toMediaItemEntity()
                            onItemClick(-1L)
                        },
                        onQuickProgress = { viewModel.incrementProgress(it) },
                        onMarkCompleted = { viewModel.markCompleted(it) },
                        onSetStatus = { summary, status -> viewModel.setMediaStatus(summary, status) },
                        onAddSearchItem = { viewModel.addMediaFromSearch(it) },
                        onRequestDiscoverShelf = { rowKey ->
                            viewModel.loadHomeDiscoverShelf(rowKey, tmdbKey)
                        },
                        onLoadMoreDiscoverShelf = { rowKey ->
                            viewModel.loadMoreHomeDiscoverShelf(rowKey, tmdbKey)
                        },
                        onOpenCalendar = {
                            viewModel.loadReleaseCalendar()
                            showReleaseCalendarSheet = true
                        },
                        onOpenCustomizer = { showHomeCustomizerSheet = true },
                        onOpenHeatmap = { showActivityHeatmapSheet = true },
                        onOpenSearch = {
                            currentNavDestination = NavDestination.LIBRARY
                            isSearchVisible = true
                        },
                        onOpenUpdate = {
                            if (availableUpdate != null) {
                                showUpdateSheet = true
                            }
                        },
                        onNavigateToLibraryCategory = { type ->
                            val targetPage = MediaType.filterTabs.indexOf(type).coerceAtLeast(0)
                            viewModel.selectMediaType(type)
                            coroutineScope.launch { pagerState.scrollToPage(targetPage) }
                            currentNavDestination = NavDestination.LIBRARY
                        },
                        onNavigateToDiscover = { currentNavDestination = NavDestination.DISCOVER },
                        onNavigateToHistory = { currentNavDestination = NavDestination.HISTORY },
                        posterStyle = posterStyle,
                        isRefreshing = isLoadingTrending || isLoadingCalendar,
                        onRefresh = {
                            viewModel.loadHomeTrending(force = true)
                            viewModel.loadReleaseCalendar(force = true)
                        }
                    )
                }

                NavDestination.LIBRARY -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        // Header: Title + Actions (Update Pill + Diagnostics + Filter/Sort + Search)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Library",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = TextPrimary,
                                    letterSpacing = (-0.5).sp
                                )
                                Text(
                                    text = "${uiState.allMediaSummaries.size} titles tracked",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    letterSpacing = 0.2.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Update Available Tag
                                val upd = availableUpdate
                                if (upd != null) {
                                    Box(
                                        modifier = Modifier
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AccentGold.copy(alpha = 0.16f))
                                            .clickable { showUpdateSheet = true }
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SystemUpdate,
                                                contentDescription = "Update Available",
                                                tint = AccentGold,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (upd.isBuilding) "Building b${upd.buildRunNumber}" else "v${upd.version}",
                                                color = AccentGold,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Diagnostics button
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(DarkSurfaceElevated)
                                        .clickable { showDiagnostics = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BugReport,
                                        contentDescription = "Diagnostics",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Add Custom Entry button
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(DarkSurfaceElevated)
                                        .clickable {
                                            customEntryInitTitle = ""
                                            customEntryInitType = if (uiState.selectedMediaType == MediaType.ALL) MediaType.NOVEL else uiState.selectedMediaType
                                            showCustomEntrySheet = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Custom Entry",
                                        tint = AccentGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Filter & Sort Sheet Trigger
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (uiState.selectedStatus != com.omnitrack.app.data.model.TrackingStatus.ALL ||
                                                librarySortMode != "RECENT" ||
                                                libraryViewMode != "GRID_3"
                                            ) AccentGold.copy(alpha = 0.18f) else DarkSurfaceElevated
                                        )
                                        .clickable { showLibraryFilterSheet = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Filter & Sort",
                                        tint = AccentGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Search trigger
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isSearchVisible) AccentGold.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                        .clickable {
                                            isSearchVisible = !isSearchVisible
                                            if (!isSearchVisible) searchQuery = ""
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = if (isSearchVisible) AccentGold else TextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Animated In-Library Search Field
                        AnimatedVisibility(visible = isSearchVisible) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurfaceElevated)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(searchFocusRequester),
                                    singleLine = true,
                                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                                    cursorBrush = SolidColor(AccentGold),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text("Filter library titles, native names...", color = TextMuted, fontSize = 13.5.sp)
                                        }
                                        innerTextField()
                                    }
                                )
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = TextMuted,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { searchQuery = "" }
                                    )
                                }
                            }
                        }

                        // Media type tabs with fluid click animation to slide pager
                        MediaTypeTabRow(
                            selectedType = uiState.selectedMediaType,
                            onTypeSelected = { type ->
                                val targetPage = MediaType.filterTabs.indexOf(type)
                                if (targetPage >= 0 && targetPage != pagerState.currentPage) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(
                                            page = targetPage,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )

                        // Status filter chips (fluid row)
                        StatusFilterChips(
                            selectedStatus = uiState.selectedStatus,
                            onStatusSelected = { viewModel.selectStatus(it) },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )

                        AnimatedVisibility(visible = uiState.selectedMediaType == MediaType.MANGA) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("ALL" to "All", "MANGA" to "Manga", "MANHWA" to "Manhwa", "MANHUA" to "Manhua").forEach { (key, label) ->
                                    val isSelected = selectedMangaSubtype == key
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (isSelected) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f),
                                                shape = RoundedCornerShape(999.dp)
                                            )
                                            .clickable { selectedMangaSubtype = key }
                                            .padding(horizontal = 12.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.60f)
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = uiState.selectedMediaType == MediaType.NOVEL) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("ALL" to "All", "LIGHT_NOVEL" to "Light Novel", "WEB_NOVEL" to "Web Novel").forEach { (key, label) ->
                                    val isSelected = selectedNovelSubtype == key
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (isSelected) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f),
                                                shape = RoundedCornerShape(999.dp)
                                            )
                                            .clickable { selectedNovelSubtype = key }
                                            .padding(horizontal = 12.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.60f)
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = uiState.selectedMediaType == MediaType.VISUAL_NOVEL) {
                            VnUnderlineFilterBar(
                                selectedOrigin = selectedVnOriginSubtype,
                                selectedLength = selectedVnLengthSubtype,
                                onOriginSelected = { selectedVnOriginSubtype = it },
                                onLengthSelected = { selectedVnLengthSubtype = it },
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }

                        AnimatedVisibility(visible = uiState.selectedMediaType == MediaType.GAME) {
                            GameUnderlineFilterBar(
                                selectedPlatform = selectedGamePlatform,
                                selectedCompletion = selectedGameCompletion,
                                onPlatformSelected = { selectedGamePlatform = it },
                                onCompletionSelected = { selectedGameCompletion = it },
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }

                        AnimatedVisibility(visible = uiState.selectedMediaType == MediaType.BOOK) {
                            BookUnderlineFilterBar(
                                selectedFormat = selectedBookFormat,
                                selectedCategory = selectedBookCategory,
                                onFormatSelected = { selectedBookFormat = it },
                                onCategorySelected = { selectedBookCategory = it },
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }

                        AnimatedVisibility(visible = uiState.selectedMediaType == MediaType.COMIC) {
                            ComicUnderlineFilterBar(
                                selectedPublisher = selectedComicPublisher,
                                selectedFormat = selectedComicFormat,
                                onPublisherSelected = { selectedComicPublisher = it },
                                onFormatSelected = { selectedComicFormat = it },
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Main Content: Horizontal Pager for Category Swiping
                        if (uiState.isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = AccentGold,
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { pageIndex ->
                                val pageMediaType = MediaType.filterTabs[pageIndex]

                                val displayedItems = remember(uiState.allMediaSummaries, uiState.selectedStatus, searchQuery, pageMediaType, selectedMangaSubtype, selectedNovelSubtype, selectedVnOriginSubtype, selectedVnLengthSubtype, selectedGamePlatform, selectedGameCompletion, selectedBookFormat, selectedBookCategory, selectedComicPublisher, selectedComicFormat, librarySortMode) {
                                    val filtered = uiState.allMediaSummaries.filter { item ->
                                        (pageMediaType == MediaType.ALL || item.mediaType == pageMediaType) &&
                                        (pageMediaType != MediaType.MANGA || selectedMangaSubtype == "ALL" || inferMangaSubtype(item.originCountry, item.nativeTitle) == selectedMangaSubtype) &&
                                        (pageMediaType != MediaType.NOVEL || selectedNovelSubtype == "ALL" || inferNovelSubtype(item.platform, item.originCountry, item.anilistId, item.malId, item.totalProgress, item.title) == selectedNovelSubtype) &&
                                        (pageMediaType != MediaType.VISUAL_NOVEL || selectedVnOriginSubtype == "ALL" || com.omnitrack.app.data.model.inferVnOriginGroup(item.originCountry, item.nativeTitle) == selectedVnOriginSubtype) &&
                                        (pageMediaType != MediaType.VISUAL_NOVEL || selectedVnLengthSubtype == "ALL" || com.omnitrack.app.data.model.inferVnLengthGroup(item.runtimeMinutes) == selectedVnLengthSubtype) &&
                                        (pageMediaType != MediaType.GAME || selectedGamePlatform == "ALL" || selectedGamePlatform in com.omnitrack.app.data.model.normalizeGamePlatformGroup(item.platforms, item.platform)) &&
                                        (pageMediaType != MediaType.GAME || selectedGameCompletion == "ALL" || when (selectedGameCompletion) {
                                            "MAIN" -> item.gameCompletionTier.equals("MAIN", true) || item.gameCompletionTier.equals("MAIN_PLUS", true)
                                            "COMP" -> item.gameCompletionTier.equals("COMPLETIONIST", true) || item.gameCompletionTier.equals("PLATINUM", true)
                                            "BEATEN" -> item.status == com.omnitrack.app.data.model.TrackingStatus.COMPLETED
                                            "BACKLOG" -> item.status == com.omnitrack.app.data.model.TrackingStatus.PLAN_TO_TRACK
                                            else -> true
                                        }) &&
                                        (pageMediaType != MediaType.BOOK || selectedBookFormat == "ALL" || com.omnitrack.app.data.model.normalizeBookFormatGroup(item.bookFormat) == selectedBookFormat) &&
                                        (pageMediaType != MediaType.BOOK || selectedBookCategory == "ALL" || com.omnitrack.app.data.model.inferBookCategoryGroup(item.title, item.platform, null) == selectedBookCategory) &&
                                        (pageMediaType != MediaType.COMIC || selectedComicPublisher == "ALL" || com.omnitrack.app.data.model.normalizeComicPublisherGroup(item.comicPublisher, item.platform) == selectedComicPublisher) &&
                                        (pageMediaType != MediaType.COMIC || selectedComicFormat == "ALL" || com.omnitrack.app.data.model.normalizeComicFormatGroup(item.comicFormat) == selectedComicFormat) &&
                                        (uiState.selectedStatus == com.omnitrack.app.data.model.TrackingStatus.ALL || item.status == uiState.selectedStatus) &&
                                        (searchQuery.isBlank() ||
                                            item.title.contains(searchQuery, ignoreCase = true) ||
                                            item.nativeTitle?.contains(searchQuery, ignoreCase = true) == true ||
                                            item.bookAuthor?.contains(searchQuery, ignoreCase = true) == true ||
                                            item.comicPublisher?.contains(searchQuery, ignoreCase = true) == true)
                                    }
                                    when (librarySortMode) {
                                        "RATING" -> filtered.sortedWith(
                                            compareByDescending<com.omnitrack.app.data.db.dto.MediaCardSummary> { it.userRating ?: it.communityScore ?: 0f }
                                                .thenByDescending { it.lastUpdated }
                                        )
                                        "TITLE" -> filtered.sortedBy { it.title.lowercase() }
                                        else -> filtered.sortedByDescending { it.lastUpdated }
                                    }
                                }

                                // Kinematic Layer: Scale decay and gentle parallax during finger drag (RenderNode layer only — zero recomposition)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                                            val kinematicScale = (1f - (kotlin.math.abs(pageOffset) * 0.05f)).coerceIn(0.95f, 1f)
                                            val kinematicAlpha = (1f - (kotlin.math.abs(pageOffset) * 0.35f)).coerceIn(0.65f, 1f)
                                            scaleX = kinematicScale
                                            scaleY = kinematicScale
                                            alpha = kinematicAlpha
                                        }
                                ) {
                                    if (displayedItems.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(horizontal = 32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.VideoLibrary,
                                                    contentDescription = null,
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Text(
                                                    text = when {
                                                        searchQuery.isNotEmpty() -> "No results matching \"$searchQuery\""
                                                        uiState.allMediaSummaries.isEmpty() -> "Your library is empty"
                                                        else -> "No items in ${pageMediaType.displayName}"
                                                    },
                                                    color = TextPrimary.copy(alpha = 0.85f),
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (searchQuery.isEmpty() && uiState.allMediaSummaries.isEmpty()) {
                                                    Text(
                                                        text = "Add titles from Discover or import from a tracker in Settings",
                                                        color = TextMuted,
                                                        fontSize = 12.5.sp,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    } else if (libraryViewMode == "LIST") {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            LazyColumn(
                                                state = listStates[pageIndex],
                                                contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 6.dp, bottom = 112.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                items(displayedItems, key = { "lib_list_${it.id}" }) { item ->
                                                    LibraryListRow(
                                                        item = item,
                                                        showQuickProgressButton = posterStyle.showQuickProgressOnCards,
                                                        onClick = {
                                                            AppLogger.i("HomeScreen", "Tapped list item: ${item.title} (id: ${item.id})")
                                                            com.omnitrack.app.ui.screens.detail.ActiveSiblingHolder.setSiblings(displayedItems.map { it.id })
                                                            onItemClick(item.id)
                                                        },
                                                        onQuickProgress = {
                                                            AppLogger.i("HomeScreen", "+ progress tapped for: ${item.title}")
                                                            viewModel.incrementProgress(item)
                                                        }
                                                    )
                                                }
                                            }
                                            LibraryFastScroller(
                                                items = displayedItems,
                                                sortMode = librarySortMode,
                                                listState = listStates[pageIndex],
                                                columnsCount = 1
                                            )
                                        }
                                    } else {
                                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                            val isPhoneScreen = maxWidth < 600.dp
                                            val gridColumns = when {
                                                posterStyle.isLandscape -> if (isPhoneScreen) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 340.dp)
                                                libraryViewMode == "GRID_2" -> GridCells.Fixed(2)
                                                else -> GridCells.Adaptive(minSize = posterStyle.widthDp.dp)
                                            }
                                            val approxCols = when {
                                                posterStyle.isLandscape -> if (isPhoneScreen) 1 else (maxWidth / 340.dp).toInt().coerceAtLeast(1)
                                                libraryViewMode == "GRID_2" -> 2
                                                else -> (maxWidth / posterStyle.widthDp.dp).toInt().coerceAtLeast(2)
                                            }
                                            LazyVerticalGrid(
                                                state = gridStates[pageIndex],
                                                columns = gridColumns,
                                                contentPadding = PaddingValues(start = 14.dp, end = 16.dp, top = 6.dp, bottom = 112.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                itemsIndexed(
                                                    items = displayedItems,
                                                    key = { _, item -> item.id }
                                                ) { _, item ->
                                                    MediaCard(
                                                        item = item,
                                                        cornerRadiusDp = posterStyle.cornerRadiusDp,
                                                        isLandscape = posterStyle.isLandscape,
                                                        hideLabels = posterStyle.hideLabels,
                                                        cardDepthEnabled = posterStyle.cardDepthEnabled && posterStyle.cardDepthPostersEnabled,
                                                        cardDepthEdgeStrength = posterStyle.cardDepthEdgeStrength.toFloat(),
                                                        cardDepthSheenStrength = posterStyle.cardDepthSheenStrength.toFloat(),
                                                        cardDepthEdgeCoverage = posterStyle.cardDepthEdgeCoverage.toFloat(),
                                                        showQuickProgressButton = posterStyle.showQuickProgressOnCards,
                                                        showLandscapeClearlogo = posterStyle.alwaysShowLandscapeClearlogo,
                                                        onCardClick = {
                                                            AppLogger.i("HomeScreen", "Tapped card: ${item.title} (id: ${item.id})")
                                                            com.omnitrack.app.ui.screens.detail.ActiveSiblingHolder.setSiblings(displayedItems.map { it.id })
                                                            onItemClick(item.id)
                                                        },
                                                        onQuickProgress = {
                                                            AppLogger.i("HomeScreen", "+1 progress tapped for: ${item.title}")
                                                            viewModel.incrementProgress(item)
                                                        }
                                                    )
                                                }
                                            }
                                            LibraryFastScroller(
                                                items = displayedItems,
                                                sortMode = librarySortMode,
                                                gridState = gridStates[pageIndex],
                                                columnsCount = approxCols
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                NavDestination.DISCOVER -> {
                    DiscoverView(
                        allMedia = uiState.allMediaSummaries,
                        onItemClick = onItemClick,
                        onAddMedia = { viewModel.addMediaFromSearch(it) },
                        onCreateCustomEntry = { initialTitle, suggestedType ->
                            customEntryInitTitle = initialTitle
                            customEntryInitType = suggestedType
                            showCustomEntrySheet = true
                        },
                        onOpenCustomizer = { showDiscoverCustomizerSheet = true },
                        modifier = Modifier.statusBarsPadding(),
                        homeViewModel = viewModel
                    )
                }

                NavDestination.HISTORY -> {
                    HistoryView(
                        allMedia = uiState.allMediaSummaries,
                        onItemClick = onItemClick,
                        onQuickProgress = { viewModel.incrementProgress(it) },
                        onMarkCompleted = { viewModel.markCompleted(it) },
                        onSetStatus = { summary, status -> viewModel.setMediaStatus(summary, status) },
                        onRemoveFromHistory = { summary -> viewModel.removeFromHistory(summary.id) },
                        onDeleteItem = { summary -> viewModel.deleteMediaItem(summary.id) },
                        watchedTimestamps90d = watchedTimestamps90d,
                        onOpenHeatmap = { showActivityHeatmapSheet = true }
                    )
                }

                NavDestination.SETTINGS -> {
                    SettingsView(
                        allMedia = uiState.allMediaSummaries,
                        onCheckUpdate = {
                            coroutineScope.launch {
                                Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
                                val release = AppUpdateChecker().checkForUpdate(context)
                                if (release != null) {
                                    availableUpdate = release
                                    showUpdateSheet = true
                                    if (release.isBuilding) {
                                        Toast.makeText(
                                            context,
                                            "Build v${release.version} is compiling on GitHub Actions...",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "You're on the latest version (v${BuildConfig.VERSION_NAME})",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onOpenDiagnostics = { showDiagnostics = true },
                        onNavigateToArtworkPriority = onNavigateToArtworkPriority,
                        onNavigateToVisualPresentation = onNavigateToVisualPresentation,
                        onNavigateToDetailsPageLayout = onNavigateToDetailsPageLayout,
                        onOpenHomeCustomizer = { showHomeCustomizerSheet = true },
                        onOpenDiscoverCustomizer = { showDiscoverCustomizerSheet = true }
                    )
                }
            }
        }

        // Floating Liquid Glass Navigation Dock
        OmniFloatingNavBar(
            selectedDestination = currentNavDestination,
            onDestinationSelected = { destination ->
                currentNavDestination = destination
            },
            scrollState = navScrollState,
            hazeState = hazeState,
            onActiveTabReSelected = { destination ->
                if (destination == NavDestination.LIBRARY) {
                    val activeGridState = gridStates.getOrNull(pagerState.currentPage)
                    coroutineScope.launch {
                        activeGridState?.animateScrollToItem(0)
                    }
                    isSearchVisible = !isSearchVisible
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Live Diagnostics & System Logs Sheet
        if (showDiagnostics) {
            DiagnosticsSheet(
                onDismiss = { showDiagnostics = false },
                onUpdateFound = {
                    availableUpdate = it
                    showUpdateSheet = true
                }
            )
        }

        // Floating Undo Progress Toast
        AnimatedVisibility(
            visible = undoEvent != null,
            enter = fadeIn(animationSpec = tween(160)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(animationSpec = tween(160)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 104.dp)
        ) {
            val activeUndo = undoEvent
            if (activeUndo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurface)
                        .border(1.dp, AccentGold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${activeUndo.title} (${activeUndo.deltaLabel})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "UNDO",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentGold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentGold.copy(alpha = 0.14f))
                            .clickable { viewModel.undoLastProgress() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { viewModel.dismissUndoEvent() }
                    )
                }
            }
        }

        // In-App One-Tap Update Bottom Sheet
        if (showUpdateSheet && availableUpdate != null) {
            UpdateBottomSheet(
                release = availableUpdate!!,
                onDismiss = { showUpdateSheet = false },
                onUpdateStateChanged = { availableUpdate = it }
            )
        }

        if (showHomeCustomizerSheet) {
            HomeCustomizerSheet(
                preferenceManager = prefManager,
                onDismiss = { showHomeCustomizerSheet = false }
            )
        }

        if (showDiscoverCustomizerSheet) {
            DiscoverCustomizerSheet(
                preferenceManager = prefManager,
                onDismiss = { showDiscoverCustomizerSheet = false }
            )
        }

        if (showReleaseCalendarSheet) {
            ReleaseCalendarSheet(
                releases = calendarReleases,
                isLoading = isLoadingCalendar,
                onRefresh = { viewModel.loadReleaseCalendar(force = true) },
                onOpenLocalMedia = { localId ->
                    showReleaseCalendarSheet = false
                    onItemClick(localId)
                },
                onPreviewRelease = { rel ->
                    showReleaseCalendarSheet = false
                    EphemeralPreviewHolder.previewItem = com.omnitrack.app.data.search.SearchResultItem(
                        title = rel.title,
                        coverUrl = rel.coverUrl,
                        mediaType = rel.mediaType,
                        sourceProvider = calendarSourceLabel(rel.mediaType),
                        anilistId = rel.anilistId,
                        malId = rel.malId,
                        tmdbId = rel.tmdbId,
                        imdbId = rel.imdbId,
                        vndbId = rel.vndbId,
                        steamAppId = rel.steamAppId,
                        igdbId = rel.igdbId,
                        hardcoverId = rel.hardcoverId,
                        mangadexId = rel.mangadexId,
                        metronId = rel.metronId,
                        comicPublisher = if (rel.mediaType == MediaType.COMIC) rel.formatLabel else null
                    ).toMediaItemEntity()
                    onItemClick(-1L)
                },
                onAddRelease = { rel ->
                    viewModel.addMediaFromSearch(
                        com.omnitrack.app.data.search.SearchResultItem(
                            title = rel.title,
                            coverUrl = rel.coverUrl,
                            mediaType = rel.mediaType,
                            sourceProvider = calendarSourceLabel(rel.mediaType),
                            anilistId = rel.anilistId,
                            malId = rel.malId,
                            tmdbId = rel.tmdbId,
                            imdbId = rel.imdbId,
                            vndbId = rel.vndbId,
                            steamAppId = rel.steamAppId,
                            igdbId = rel.igdbId,
                            hardcoverId = rel.hardcoverId,
                            mangadexId = rel.mangadexId,
                            metronId = rel.metronId,
                            comicPublisher = if (rel.mediaType == MediaType.COMIC) rel.formatLabel else null
                        )
                    )
                },
                onDismiss = { showReleaseCalendarSheet = false }
            )
        }

        if (showActivityHeatmapSheet) {
            ActivityHeatmapSheet(
                watchedTimestamps90d = watchedTimestamps90d,
                allMedia = uiState.allMediaSummaries,
                onDismiss = { showActivityHeatmapSheet = false }
            )
        }

        if (showLibraryFilterSheet) {
            LibraryFilterSortSheet(
                selectedStatus = uiState.selectedStatus,
                sortMode = librarySortMode,
                viewMode = libraryViewMode,
                onSelectStatus = { viewModel.selectStatus(it) },
                onSelectSortMode = { mode ->
                    coroutineScope.launch { prefManager.setLibrarySortMode(mode) }
                },
                onSelectViewMode = { mode ->
                    coroutineScope.launch { prefManager.setLibraryViewMode(mode) }
                },
                onResetFilters = {
                    viewModel.selectStatus(com.omnitrack.app.data.model.TrackingStatus.ALL)
                    coroutineScope.launch {
                        prefManager.setLibrarySortMode("RECENT")
                        prefManager.setLibraryViewMode("GRID_3")
                    }
                },
                onDismiss = { showLibraryFilterSheet = false }
            )
        }

        if (showCustomEntrySheet) {
            CustomEntryEditorSheet(
                existingItem = null,
                initialTitle = customEntryInitTitle,
                initialMediaType = customEntryInitType,
                onSave = { entity ->
                    showCustomEntrySheet = false
                    viewModel.createCustomMediaEntry(entity) { newId ->
                        if (newId > 0L) onItemClick(newId)
                    }
                },
                onDismiss = { showCustomEntrySheet = false }
            )
        }
    }
    }
}

@Composable
private fun LibraryListRow(
    item: com.omnitrack.app.data.db.dto.MediaCardSummary,
    showQuickProgressButton: Boolean = false,
    onClick: () -> Unit,
    onQuickProgress: () -> Unit
) {
    val coverModel = remember(item.customCoverPath, item.coverUrl) {
        item.customCoverPath?.let { if (it.startsWith("http")) it else File(it) }
            ?: LosslessCoverMapper.toLosslessUrl(item.coverUrl)
    }
    val progressLabel = remember(item) { formatConciseProgress(item) }
    val rating = item.userRating ?: item.communityScore
    val fraction = remember(item) {
        val cur = if (item.mediaType == MediaType.GAME || item.mediaType == MediaType.VISUAL_NOVEL) {
            (if (item.playtimeHours > 0.0) item.playtimeHours else item.currentProgress).toFloat()
        } else {
            item.currentProgress.toFloat()
        }
        val tot = item.totalProgress?.toFloat() ?: 0f
        if (tot > 0f) (cur / tot).coerceIn(0f, 1f) else 0f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = coverModel,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(50.dp)
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceCard)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    item.mediaType.displayName,
                    progressLabel.takeIf { it.isNotBlank() },
                    rating?.takeIf { it > 0f }?.let { "\u2605 ${formatSmartRating(it.toDouble())}" }
                ).joinToString(" \u00B7 "),
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(DarkSurfaceCard)
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

        if (showQuickProgressButton) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, CircleShape)
                    .clickable(onClick = onQuickProgress),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.mediaType == MediaType.MOVIE) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = "Quick Log",
                    tint = AccentGold,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private val HANGUL_REGEX = Regex("[\\uAC00-\\uD7AF\\u1100-\\u11FF]")
private val KANA_REGEX = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF]")
private val CJK_REGEX = Regex("[\\u4E00-\\u9FFF]")

internal fun inferMangaSubtype(originCountry: String?, nativeTitle: String?): String {
    return when (originCountry?.trim()?.uppercase()) {
        "KR" -> "MANHWA"
        "CN", "TW", "HK" -> "MANHUA"
        "JP" -> "MANGA"
        else -> {
            val nat = nativeTitle.orEmpty()
            when {
                HANGUL_REGEX.containsMatchIn(nat) -> "MANHWA"
                KANA_REGEX.containsMatchIn(nat) -> "MANGA"
                CJK_REGEX.containsMatchIn(nat) -> "MANHUA"
                else -> "MANGA"
            }
        }
    }
}

internal fun inferNovelSubtype(
    platform: String?,
    originCountry: String?,
    anilistId: Long?,
    malId: Long?,
    totalProgress: Double?,
    title: String
): String {
    val normPlatform = platform?.trim()?.uppercase()
    if (normPlatform == "LIGHT_NOVEL") return "LIGHT_NOVEL"
    if (normPlatform == "WEB_NOVEL") return "WEB_NOVEL"

    val lowerTitle = title.lowercase()
    if ("(light novel)" in lowerTitle || "[light novel]" in lowerTitle || "(ln)" in lowerTitle) {
        return "LIGHT_NOVEL"
    }
    if ("(web novel)" in lowerTitle || "[web novel]" in lowerTitle || "(wn)" in lowerTitle) {
        return "WEB_NOVEL"
    }

    val upperOrigin = originCountry?.trim()?.uppercase()
    val hasJpPrintSignal = (anilistId != null || malId != null) && (upperOrigin == null || upperOrigin == "JP")
    return if (hasJpPrintSignal && (totalProgress == null || totalProgress <= 80.0)) {
        "LIGHT_NOVEL"
    } else {
        "WEB_NOVEL"
    }
}

@Composable
internal fun BookUnderlineFilterBar(
    selectedFormat: String,
    selectedCategory: String?,
    onFormatSelected: (String) -> Unit,
    onCategorySelected: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        UnderlineFilterGroup(
            options = listOf(
                "ALL" to "All Formats",
                "PRINT" to "Print",
                "EBOOK" to "E-book",
                "AUDIO" to "Audiobook"
            ),
            selectedKey = selectedFormat,
            onSelected = onFormatSelected
        )
        if (selectedCategory != null && onCategorySelected != null) {
            UnderlineFilterGroup(
                options = listOf(
                    "ALL" to "All Books",
                    "FICTION" to "Fiction",
                    "NON_FICTION" to "Non-Fiction",
                    "CLASSICS_POETRY" to "Classics & Poetry",
                    "PLAYS" to "Plays"
                ),
                selectedKey = selectedCategory,
                onSelected = onCategorySelected
            )
        }
    }
}

@Composable
internal fun GameUnderlineFilterBar(
    selectedPlatform: String,
    selectedCompletion: String?,
    onPlatformSelected: (String) -> Unit,
    onCompletionSelected: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        UnderlineFilterGroup(
            options = listOf(
                "ALL" to "All",
                "PC" to "PC",
                "PS" to "PlayStation",
                "SWI" to "Switch",
                "XBOX" to "Xbox",
                "DECK" to "Steam Deck",
                "MOBILE" to "Mobile",
                "RETRO" to "Retro"
            ),
            selectedKey = selectedPlatform,
            onSelected = onPlatformSelected
        )
        if (selectedCompletion != null && onCompletionSelected != null) {
            UnderlineFilterGroup(
                options = listOf(
                    "ALL" to "All Tiers",
                    "MAIN" to "Main / Main+",
                    "COMP" to "100% / Plat",
                    "BEATEN" to "Completed",
                    "BACKLOG" to "Backlog"
                ),
                selectedKey = selectedCompletion,
                onSelected = onCompletionSelected
            )
        }
    }
}

@Composable
internal fun VnUnderlineFilterBar(
    selectedOrigin: String,
    selectedLength: String,
    onOriginSelected: (String) -> Unit,
    onLengthSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        UnderlineFilterGroup(
            options = listOf("ALL" to "All", "JP" to "Japanese", "EN" to "English / OELVN", "CNKR" to "CN / KR"),
            selectedKey = selectedOrigin,
            onSelected = onOriginSelected
        )
        UnderlineFilterGroup(
            options = listOf("ALL" to "Any Length", "SHORT" to "Short (<10h)", "MEDIUM" to "Medium", "LONG" to "Long", "VLONG" to "Very Long (50h+)"),
            selectedKey = selectedLength,
            onSelected = onLengthSelected
        )
    }
}

@Composable
internal fun ComicUnderlineFilterBar(
    selectedPublisher: String,
    selectedFormat: String?,
    onPublisherSelected: (String) -> Unit,
    onFormatSelected: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        UnderlineFilterGroup(
            options = listOf(
                "ALL" to "All Publishers",
                "MARVEL" to "Marvel",
                "DC" to "DC",
                "IMAGE" to "Image",
                "DARK_HORSE" to "Dark Horse",
                "IDW" to "IDW",
                "INDIE" to "Indie"
            ),
            selectedKey = selectedPublisher,
            onSelected = onPublisherSelected
        )
        if (selectedFormat != null && onFormatSelected != null) {
            UnderlineFilterGroup(
                options = listOf(
                    "ALL" to "All Formats",
                    "ISSUES" to "Single Issues",
                    "TPB" to "TPB / HC",
                    "OMNIBUS" to "Omnibus",
                    "DIGITAL" to "Digital"
                ),
                selectedKey = selectedFormat,
                onSelected = onFormatSelected
            )
        }
    }
}

@Composable
private fun UnderlineFilterGroup(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (key, label) ->
            val isSelected = selectedKey == key
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clickable { onSelected(key) }
                    .padding(top = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) TextPrimary else TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (isSelected) AccentGold else androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
    }
}

private fun calendarSourceLabel(type: MediaType): String = when (type) {
    MediaType.COMIC -> "Metron"
    MediaType.SHOW -> "TVMaze"
    MediaType.MOVIE -> "TMDb"
    MediaType.MANGA -> "MangaDex"
    MediaType.NOVEL -> "AniList"
    MediaType.BOOK -> "Goodreads"
    MediaType.GAME -> "Steam"
    MediaType.VISUAL_NOVEL -> "VNDB"
    else -> "AniList"
}
