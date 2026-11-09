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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.preferences.PosterCardStyle
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.ui.components.DiagnosticsSheet
import com.omnitrack.app.ui.components.MediaCard
import com.omnitrack.app.ui.components.MediaTypeTabRow
import com.omnitrack.app.ui.components.NavDestination
import com.omnitrack.app.ui.components.OmniFloatingNavBar
import com.omnitrack.app.ui.components.StatusFilterChips
import com.omnitrack.app.ui.components.UpdateBottomSheet
import com.omnitrack.app.ui.components.rememberOmniNavBarScrollState
import com.omnitrack.app.ui.screens.home.views.DiscoverView
import com.omnitrack.app.ui.screens.home.views.HistoryView
import com.omnitrack.app.ui.screens.home.views.SettingsView
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.updater.AppRelease
import com.omnitrack.app.updater.AppUpdateChecker
import com.omnitrack.app.util.AppLogger
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onItemClick: (Long) -> Unit,
    onNavigateToArtworkPriority: () -> Unit = {},
    onNavigateToVisualPresentation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefManager = remember { PreferenceManager(context) }
    val posterStyle by prefManager.posterCardStyle.collectAsState(initial = PosterCardStyle())
    val customPosterPattern by prefManager.customPosterUrlPattern.collectAsState(initial = "")
    val uiState by viewModel.uiState.collectAsState()
    val navScrollState = rememberOmniNavBarScrollState()
    val hazeState = remember { HazeState() }
    val gridStates = remember { List(MediaType.filterTabs.size) { androidx.compose.foundation.lazy.grid.LazyGridState() } }

    var currentNavDestination by rememberSaveable { mutableStateOf(NavDestination.LIBRARY) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppRelease?>(null) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    // Automatic background update check
    LaunchedEffect(Unit) {
        val release = AppUpdateChecker().checkForUpdate(context)
        if (release != null) {
            availableUpdate = release
            showUpdateSheet = true
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
    // 3. If on Library with search closed -> BackHandler disabled, exits app naturally
    BackHandler(enabled = isSearchVisible || currentNavDestination != NavDestination.LIBRARY) {
        if (isSearchVisible) {
            isSearchVisible = false
            searchQuery = ""
        } else if (currentNavDestination != NavDestination.LIBRARY) {
            currentNavDestination = NavDestination.LIBRARY
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        com.omnitrack.app.ui.components.LocalCustomPosterPattern provides customPosterPattern
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(AmoledBlack)
                .statusBarsPadding()
                .nestedScroll(navScrollState.nestedScrollConnection)
        ) {
        // Fluid Tab Content Switcher across destinations (Library, Discover, History, Settings)
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
                NavDestination.LIBRARY -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header: Title + Ambient Actions (Update Pill + Search + Diagnostics)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "OmniTrack",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = TextPrimary,
                                    letterSpacing = (-0.5).sp
                                )
                                Text(
                                    text = "Universal Media Library",
                                    fontSize = 11.5.sp,
                                    color = TextMuted,
                                    letterSpacing = 0.2.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Update Available Pill
                                if (availableUpdate != null) {
                                    Box(
                                        modifier = Modifier
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(19.dp))
                                            .background(AccentJade.copy(alpha = 0.2f))
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
                                                tint = AccentJade,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "v${availableUpdate?.version}",
                                                color = AccentJade,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Diagnostics Sheet trigger
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
                                        tint = AccentArctic,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Search trigger
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isSearchVisible) AccentArctic.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                        .clickable {
                                            isSearchVisible = !isSearchVisible
                                            if (!isSearchVisible) searchQuery = ""
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = if (isSearchVisible) AccentArctic else TextPrimary,
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
                                    cursorBrush = SolidColor(AccentArctic),
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

                        Spacer(modifier = Modifier.height(6.dp))

                        // Main Content: Horizontal Pager for Category Swiping
                        if (uiState.isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = AccentArctic,
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

                                val displayedItems = remember(uiState.allMediaSummaries, uiState.selectedStatus, searchQuery, pageMediaType) {
                                    uiState.allMediaSummaries.filter { item ->
                                        (pageMediaType == MediaType.ALL || item.mediaType == pageMediaType) &&
                                        (uiState.selectedStatus == com.omnitrack.app.data.model.TrackingStatus.ALL || item.status == uiState.selectedStatus) &&
                                        (searchQuery.isBlank() ||
                                            item.title.contains(searchQuery, ignoreCase = true) ||
                                            item.nativeTitle?.contains(searchQuery, ignoreCase = true) == true)
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
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.VideoLibrary,
                                                    contentDescription = null,
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Text(
                                                    text = if (searchQuery.isNotEmpty()) "No results matching \"$searchQuery\"" else "No items in ${pageMediaType.displayName}",
                                                    color = TextMuted,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    } else {
                                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                            val isPhoneScreen = maxWidth < 600.dp
                                            val gridColumns = if (posterStyle.isLandscape) {
                                                if (isPhoneScreen) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 340.dp)
                                            } else {
                                                GridCells.Adaptive(minSize = posterStyle.widthDp.dp)
                                            }
                                            LazyVerticalGrid(
                                                state = gridStates[pageIndex],
                                                columns = gridColumns,
                                                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 96.dp),
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
                                                        cardDepthEnabled = posterStyle.cardDepthEnabled,
                                                        cardDepthEdgeStrength = posterStyle.cardDepthEdgeStrength.toFloat(),
                                                        cardDepthSheenStrength = posterStyle.cardDepthSheenStrength.toFloat(),
                                                        cardDepthEdgeCoverage = posterStyle.cardDepthEdgeCoverage.toFloat(),
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
                        onAddMedia = { viewModel.addMediaFromSearch(it) }
                    )
                }

                NavDestination.HISTORY -> {
                    HistoryView(
                        allMedia = uiState.allMediaSummaries,
                        onItemClick = onItemClick,
                        onQuickProgress = { viewModel.incrementProgress(it) }
                    )
                }

                NavDestination.SETTINGS -> {
                    SettingsView(
                        allMedia = uiState.allMediaSummaries,
                        onCheckUpdate = {
                            coroutineScope.launch {
                                val release = AppUpdateChecker().checkForUpdate(context)
                                if (release != null) {
                                    availableUpdate = release
                                    showUpdateSheet = true
                                }
                            }
                        },
                        onOpenDiagnostics = { showDiagnostics = true },
                        onNavigateToArtworkPriority = onNavigateToArtworkPriority,
                        onNavigateToVisualPresentation = onNavigateToVisualPresentation
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

        // In-App One-Tap Update Bottom Sheet
        if (showUpdateSheet && availableUpdate != null) {
            UpdateBottomSheet(
                release = availableUpdate!!,
                onDismiss = { showUpdateSheet = false }
            )
        }
    }
    }
}
