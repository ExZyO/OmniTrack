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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
    onNavigateToDetailsPageLayout: () -> Unit = {},
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
    var selectedMangaSubtype by rememberSaveable { mutableStateOf("ALL") }
    var selectedNovelSubtype by rememberSaveable { mutableStateOf("ALL") }
    var selectedVnOriginSubtype by rememberSaveable { mutableStateOf("ALL") }
    var selectedVnLengthSubtype by rememberSaveable { mutableStateOf("ALL") }
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

                                val displayedItems = remember(uiState.allMediaSummaries, uiState.selectedStatus, searchQuery, pageMediaType, selectedMangaSubtype, selectedNovelSubtype, selectedVnOriginSubtype, selectedVnLengthSubtype) {
                                    uiState.allMediaSummaries.filter { item ->
                                        (pageMediaType == MediaType.ALL || item.mediaType == pageMediaType) &&
                                        (pageMediaType != MediaType.MANGA || selectedMangaSubtype == "ALL" || inferMangaSubtype(item.originCountry, item.nativeTitle) == selectedMangaSubtype) &&
                                        (pageMediaType != MediaType.NOVEL || selectedNovelSubtype == "ALL" || inferNovelSubtype(item.platform, item.originCountry, item.anilistId, item.malId, item.totalProgress, item.title) == selectedNovelSubtype) &&
                                        (pageMediaType != MediaType.VISUAL_NOVEL || selectedVnOriginSubtype == "ALL" || com.omnitrack.app.data.model.inferVnOriginGroup(item.originCountry, item.nativeTitle) == selectedVnOriginSubtype) &&
                                        (pageMediaType != MediaType.VISUAL_NOVEL || selectedVnLengthSubtype == "ALL" || com.omnitrack.app.data.model.inferVnLengthGroup(item.runtimeMinutes) == selectedVnLengthSubtype) &&
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
                        onAddMedia = { viewModel.addMediaFromSearch(it) },
                        homeViewModel = viewModel
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
                        onNavigateToVisualPresentation = onNavigateToVisualPresentation,
                        onNavigateToDetailsPageLayout = onNavigateToDetailsPageLayout
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
                    color = if (isSelected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.50f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (isSelected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
    }
}
