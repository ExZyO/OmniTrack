package com.omnitrack.app.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.enrichment.CalendarReleaseItem
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.CachedMultiRatings
import com.omnitrack.app.data.preferences.ChimanewLibrarySettings
import com.omnitrack.app.data.preferences.ContinueTrackingStyle
import com.omnitrack.app.data.preferences.HomeHeroSettings
import com.omnitrack.app.data.preferences.HomeLayoutDefaults
import com.omnitrack.app.data.preferences.HomeRowItem
import com.omnitrack.app.data.preferences.HomeRowKey
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.data.preferences.LibraryDisplayMode
import com.omnitrack.app.data.preferences.LibraryGroupMode
import com.omnitrack.app.data.preferences.LibrarySortOption
import com.omnitrack.app.data.preferences.MediaTabKey
import com.omnitrack.app.data.preferences.PosterCardStyle
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.preferences.TriStateFilter
import com.omnitrack.app.data.preferences.heroRatingsCacheKey
import com.omnitrack.app.ui.components.ArtworkPickerBottomSheet
import com.omnitrack.app.ui.components.CustomEntryEditorSheet
import com.omnitrack.app.ui.components.DiagnosticsSheet
import com.omnitrack.app.ui.components.LibraryFastScroller
import com.omnitrack.app.ui.components.LocalPerformanceSettings
import com.omnitrack.app.ui.components.MediaCard
import com.omnitrack.app.ui.components.MultiSelectBottomActionDock
import com.omnitrack.app.ui.components.MultiSelectCategoriesSheet
import com.omnitrack.app.ui.components.MultiSelectCheckBadge
import com.omnitrack.app.ui.components.MultiSelectTopBar
import com.omnitrack.app.ui.components.NavDestination
import com.omnitrack.app.ui.components.OmniFloatingNavBar
import com.omnitrack.app.ui.components.StatusFilterChips
import com.omnitrack.app.ui.components.UpdateBottomSheet
import com.omnitrack.app.ui.components.rememberOmniNavBarScrollState
import com.omnitrack.app.ui.screens.detail.EphemeralPreviewHolder
import com.omnitrack.app.ui.screens.detail.components.formatSmartRating
import com.omnitrack.app.ui.screens.home.views.ActivityHeatmapSheet
import com.omnitrack.app.ui.screens.home.views.ChimanewLibrarySheet
import com.omnitrack.app.ui.screens.home.views.DiscoverCustomizerSheet
import com.omnitrack.app.ui.screens.home.views.DiscoverView
import com.omnitrack.app.ui.screens.home.views.HistoryView
import com.omnitrack.app.ui.screens.home.views.HomeCustomizerSheet
import com.omnitrack.app.ui.screens.home.views.HomeDashboardView
import com.omnitrack.app.ui.screens.home.views.LibraryCategorySubFilters
import com.omnitrack.app.ui.screens.home.views.ReleaseCalendarSheet
import com.omnitrack.app.ui.screens.home.views.SettingsView
import com.omnitrack.app.ui.screens.home.views.formatConciseProgress
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.OnAccentColor
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

private data class LibraryGroupTab(
    val key: String,
    val label: String,
    val mediaTypeFilter: MediaType? = null,
    val novelSubtypeFilter: String? = null,
    val categoryId: Long? = null,
    val isUncategorized: Boolean = false,
    val statusFilter: TrackingStatus? = null,
    val trackerKey: String? = null
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onItemClick: (Long) -> Unit,
    onNavigateToArtworkPriority: () -> Unit = {},
    onNavigateToVisualPresentation: () -> Unit = {},
    onNavigateToDetailsPageLayout: () -> Unit = {},
    onNavigateToManageCategories: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val prefManager = remember(context) { (context.applicationContext as com.omnitrack.app.OmniTrackApplication).preferenceManager }
    val posterStyle by prefManager.posterCardStyle.collectAsState(initial = PosterCardStyle())
    val customPosterPattern by prefManager.customPosterUrlPattern.collectAsState(initial = "")
    val mediaCategoryPrefs by prefManager.mediaCategoryPrefs.collectAsState(
        initial = PreferenceManager.cachedMediaCategoryPrefs
    )
    val fanfictionEnabled = !mediaCategoryPrefs.isHidden(MediaType.FANFICTION)
    val fanficNsfwMode by prefManager.fanficNsfwFilterMode.collectAsState(initial = com.omnitrack.app.data.model.VnNsfwFilterMode.BLUR_TAP)
    val visibleFilterTabs = remember(mediaCategoryPrefs) {
        mediaCategoryPrefs.visibleFilterTabs(includeAll = true)
    }
    val visibleMediaTypes = remember(mediaCategoryPrefs) {
        mediaCategoryPrefs.visibleMediaTypes()
    }
    val sharedFanficUrl by com.omnitrack.app.SharedFanficUrlBus.pendingUrl.collectAsState()
    var showFanficImportSheet by remember { mutableStateOf(false) }
    var fanficImportInitialTab by remember { mutableIntStateOf(0) }
    var fanficImportInitialUrl by remember { mutableStateOf("") }
    LaunchedEffect(sharedFanficUrl) {
        val shared = sharedFanficUrl ?: return@LaunchedEffect
        fanficImportInitialTab = 0
        fanficImportInitialUrl = shared
        showFanficImportSheet = true
        com.omnitrack.app.SharedFanficUrlBus.consume()
    }

    val uiState by viewModel.uiState.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val libSettings by prefManager.chimanewLibrarySettings.collectAsState(initial = PreferenceManager.cachedChimanewLibrarySettings)
    val perfSettings = LocalPerformanceSettings.current
    val categoryAssignments by prefManager.categoryAssignments.collectAsState(initial = emptyMap())
    val homeRows by prefManager.homeRowOrder.collectAsState(initial = PreferenceManager.cachedHomeRows)
    val heroSettings by prefManager.homeHeroSettings.collectAsState(initial = PreferenceManager.cachedHomeHeroSettings)
    val continueTrackingStyle by prefManager.continueTrackingStyle.collectAsState(initial = ContinueTrackingStyle.CARD)
    val tmdbKey by prefManager.tmdbApiKey.collectAsState(initial = "")
    val heroMultiRatingsCache by prefManager.heroMultiRatingsCache.collectAsState(initial = emptyMap())
    val calendarReleases by viewModel.calendarReleases.collectAsState()
    val isLoadingCalendar by viewModel.isLoadingCalendar.collectAsState()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsState()
    val trendingItems by viewModel.trendingItems.collectAsState()
    val categoryDiscoverShelves by viewModel.categoryDiscoverShelves.collectAsState()
    val watchedTimestamps90d by viewModel.watchedTimestamps90d.collectAsState()
    val homeListState = rememberLazyListState()
    val navScrollState = rememberOmniNavBarScrollState()
    val hazeState = remember { HazeState() }
    val tabGridStates = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.foundation.lazy.grid.LazyGridState>() }
    val tabListStates = remember { androidx.compose.runtime.mutableStateMapOf<String, LazyListState>() }

    var currentNavDestination by rememberSaveable { mutableStateOf(NavDestination.HOME) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppRelease?>(null) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    var showHomeCustomizerSheet by remember { mutableStateOf(false) }
    var showDiscoverCustomizerSheet by remember { mutableStateOf(false) }
    var showReleaseCalendarSheet by remember { mutableStateOf(false) }
    var showActivityHeatmapSheet by remember { mutableStateOf(false) }
    var showLibraryFilterSheet by remember { mutableStateOf(false) }
    var showMultiSelectCategoriesSheet by remember { mutableStateOf(false) }
    var showCustomEntrySheet by remember { mutableStateOf(false) }
    var customEntryInitTitle by remember { mutableStateOf("") }
    var customEntryInitType by remember { mutableStateOf(MediaType.NOVEL) }
    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQueryField by remember { mutableStateOf(TextFieldValue("")) }
    val searchQuery = searchQueryField.text
    var isLibraryMultiSelect by remember { mutableStateOf(false) }
    var isHistoryMultiSelect by remember { mutableStateOf(false) }
    var selectedLibraryIds by remember { mutableStateOf(setOf<Long>()) }
    var artworkPickerMediaId by remember { mutableStateOf<Long?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var pendingLibraryTabKey by remember { mutableStateOf<String?>(null) }
    var savedLibraryTabKey by rememberSaveable { mutableStateOf<String?>(null) }
    var reopenFilterSheetOnReturn by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (reopenFilterSheetOnReturn) {
            reopenFilterSheetOnReturn = false
            showLibraryFilterSheet = true
        }
    }

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
    var selectedFanficSitesRaw by rememberSaveable { mutableStateOf("") }
    val selectedFanficSites = remember(selectedFanficSitesRaw) {
        selectedFanficSitesRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    var selectedFanficRating by rememberSaveable { mutableStateOf("ALL") }
    var selectedFanficStatus by rememberSaveable { mutableStateOf("ALL") }
    var selectedFanficMinWords by rememberSaveable { mutableIntStateOf(0) }
    var fanficCrossoverOnly by rememberSaveable { mutableStateOf(false) }
    var fanficTagFilter by rememberSaveable { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    // Automatic background update check (detects ready releases & active CI builds)
    LaunchedEffect(Unit) {
        val release = AppUpdateChecker().checkForUpdateIfDue(context)
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

    // Dynamic Chimanew Library Group Tabs
    val dynamicTabs = remember(
        libSettings.groupMode,
        libSettings.showHiddenCategories,
        mediaCategoryPrefs,
        allCategories,
        uiState.allMediaSummaries,
        categoryAssignments
    ) {
        when (libSettings.groupMode) {
            LibraryGroupMode.BY_MEDIA_TYPE -> buildList {
                add(LibraryGroupTab(key = "MT_ALL", label = "All", mediaTypeFilter = MediaType.ALL))
                mediaCategoryPrefs.visibleTabKeys().forEach { tabKey ->
                    val key = when (tabKey) {
                        MediaTabKey.LIGHT_NOVEL -> "MT_NOVEL_LN"
                        MediaTabKey.WEB_NOVEL -> "MT_NOVEL_WN"
                        else -> "MT_${tabKey.mediaType.name}"
                    }
                    add(
                        LibraryGroupTab(
                            key = key,
                            label = tabKey.displayName,
                            mediaTypeFilter = tabKey.mediaType,
                            novelSubtypeFilter = tabKey.novelSubtypeFilter
                        )
                    )
                }
            }
            LibraryGroupMode.BY_CATEGORY -> buildList {
                add(LibraryGroupTab(key = "CAT_ALL", label = "All"))
                allCategories
                    .filter { libSettings.showHiddenCategories || it.isVisible }
                    .forEach { cat ->
                        add(LibraryGroupTab(key = "CAT_${cat.id}", label = cat.name, categoryId = cat.id))
                    }
                val hasUncategorized = uiState.allMediaSummaries.any { categoryAssignments[it.id].isNullOrEmpty() }
                if (hasUncategorized && allCategories.isNotEmpty()) {
                    add(LibraryGroupTab(key = "CAT_NONE", label = "Uncategorized", isUncategorized = true))
                }
            }
            LibraryGroupMode.BY_STATUS -> buildList {
                add(LibraryGroupTab(key = "ST_ALL", label = "All", statusFilter = TrackingStatus.ALL))
                listOf(
                    TrackingStatus.IN_PROGRESS,
                    TrackingStatus.COMPLETED,
                    TrackingStatus.PLAN_TO_TRACK,
                    TrackingStatus.ON_HOLD,
                    TrackingStatus.DROPPED
                ).forEach { st ->
                    add(LibraryGroupTab(key = "ST_${st.name}", label = st.displayName, statusFilter = st))
                }
            }
            LibraryGroupMode.BY_TRACKER -> listOf(
                LibraryGroupTab(key = "TR_ALL", label = "All"),
                LibraryGroupTab(key = "TR_ANILIST", label = "AniList", trackerKey = "ANILIST"),
                LibraryGroupTab(key = "TR_MAL", label = "MAL", trackerKey = "MAL"),
                LibraryGroupTab(key = "TR_TMDB", label = "TMDb", trackerKey = "TMDB"),
                LibraryGroupTab(key = "TR_TRAKT", label = "Trakt", trackerKey = "TRAKT"),
                LibraryGroupTab(key = "TR_SIMKL", label = "Simkl", trackerKey = "SIMKL"),
                LibraryGroupTab(key = "TR_STEAM", label = "Steam", trackerKey = "STEAM"),
                LibraryGroupTab(key = "TR_VNDB", label = "VNDB", trackerKey = "VNDB"),
                LibraryGroupTab(key = "TR_HARDCOVER", label = "Hardcover", trackerKey = "HARDCOVER"),
                LibraryGroupTab(key = "TR_LOCAL", label = "Local Only", trackerKey = "LOCAL")
            )
            LibraryGroupMode.UNGROUPED -> listOf(LibraryGroupTab(key = "UNGROUPED_ALL", label = "All"))
        }
    }

    // Kinematic library pager
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { dynamicTabs.size.coerceAtLeast(1) }
    )

    LaunchedEffect(dynamicTabs.size, libSettings.groupMode) {
        if (pagerState.currentPage >= dynamicTabs.size) {
            pagerState.scrollToPage(0)
        }
    }
    LaunchedEffect(pendingLibraryTabKey, savedLibraryTabKey, dynamicTabs) {
        val targetKey = pendingLibraryTabKey
        if (targetKey != null) {
            var targetIdx = dynamicTabs.indexOfFirst { it.key == targetKey }
            if (targetIdx < 0 && targetKey == "MT_NOVEL") {
                targetIdx = dynamicTabs.indexOfFirst { it.key == "MT_NOVEL_LN" || it.key == "MT_NOVEL_WN" }
            }
            if (targetIdx >= 0) {
                runCatching { pagerState.scrollToPage(targetIdx) }
                savedLibraryTabKey = dynamicTabs[targetIdx].key
                pendingLibraryTabKey = null
            }
        } else {
            val savedKey = savedLibraryTabKey
            if (savedKey != null) {
                val savedIdx = dynamicTabs.indexOfFirst { it.key == savedKey }
                if (savedIdx >= 0 && pagerState.currentPage != savedIdx) {
                    runCatching { pagerState.scrollToPage(savedIdx) }
                }
            }
        }
    }
    LaunchedEffect(pagerState) {
        var wasScrolling = false
        androidx.compose.runtime.snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
            .collect { (scrolling, settled) ->
                if (scrolling) {
                    wasScrolling = true
                } else if (wasScrolling) {
                    wasScrolling = false
                    dynamicTabs.getOrNull(settled)?.key?.let { key ->
                        savedLibraryTabKey = key
                    }
                }
            }
    }
    LaunchedEffect(pagerState.currentPage, dynamicTabs) {
        val activeTab = dynamicTabs.getOrNull(pagerState.currentPage)
        viewModel.selectMediaType(activeTab?.mediaTypeFilter ?: MediaType.ALL)
    }

    val pendingFanficTag by com.omnitrack.app.data.model.FanficTagFilterBus.pendingTag.collectAsState()
    LaunchedEffect(pendingFanficTag, dynamicTabs) {
        val tag = pendingFanficTag ?: return@LaunchedEffect
        fanficTagFilter = tag
        if (libSettings.groupMode != LibraryGroupMode.BY_MEDIA_TYPE) {
            prefManager.setChimanewGroupMode(LibraryGroupMode.BY_MEDIA_TYPE)
        }
        val targetPage = dynamicTabs.indexOfFirst { it.mediaTypeFilter == MediaType.FANFICTION }.coerceAtLeast(0)
        viewModel.selectMediaType(MediaType.FANFICTION)
        runCatching { pagerState.scrollToPage(targetPage) }
        pendingLibraryTabKey = "MT_FANFICTION"
        currentNavDestination = NavDestination.LIBRARY
        com.omnitrack.app.data.model.FanficTagFilterBus.consume()
    }

    val pendingLibSearch by com.omnitrack.app.data.model.LibrarySearchBus.pendingQuery.collectAsState()
    LaunchedEffect(pendingLibSearch) {
        val q = pendingLibSearch ?: return@LaunchedEffect
        isLibraryMultiSelect = false
        selectedLibraryIds = emptySet()
        searchQueryField = TextFieldValue(text = q, selection = TextRange(q.length))
        isSearchVisible = true
        currentNavDestination = NavDestination.LIBRARY
        runCatching { pagerState.scrollToPage(0) }
        com.omnitrack.app.data.model.LibrarySearchBus.consume()
    }

    // Auto-focus search text field when opened with cursor anchored at end of text
    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            val txt = searchQueryField.text
            searchQueryField = TextFieldValue(text = txt, selection = TextRange(txt.length))
            delay(120)
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // System Back Gesture handling:
    // 1. If search is open -> close search
    // 2. If on another tab (Discover, History, Settings) -> return to Library (Home page)
    BackHandler(enabled = isLibraryMultiSelect || isSearchVisible || currentNavDestination != NavDestination.HOME) {
        if (isLibraryMultiSelect) {
            isLibraryMultiSelect = false
            selectedLibraryIds = emptySet()
        } else if (isSearchVisible) {
            isSearchVisible = false
            searchQueryField = TextFieldValue("")
        } else if (currentNavDestination != NavDestination.HOME) {
            currentNavDestination = NavDestination.HOME
        }
    }

    val enableLiveNavBlur = perfSettings.liveNavBarBlur && !perfSettings.performanceModeEnabled
    val isActiveLibraryScrolling by remember(
        perfSettings.autoPauseBlurOnScroll,
        enableLiveNavBlur,
        currentNavDestination,
        pagerState,
        dynamicTabs
    ) {
        derivedStateOf {
            if (!enableLiveNavBlur || !perfSettings.autoPauseBlurOnScroll || currentNavDestination != NavDestination.LIBRARY) {
                false
            } else {
                val activeKey = dynamicTabs.getOrNull(pagerState.currentPage)?.key
                val gridScrolling = activeKey != null && tabGridStates[activeKey]?.isScrollInProgress == true
                val listScrolling = activeKey != null && tabListStates[activeKey]?.isScrollInProgress == true
                gridScrolling || listScrolling || pagerState.isScrollInProgress
            }
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
                .then(if (enableLiveNavBlur) Modifier.hazeSource(hazeState) else Modifier)
        ) { destination ->
            when (destination) {
                NavDestination.HOME -> {
                    HomeTabContent(
                        viewModel = viewModel,
                        prefManager = prefManager,
                        allMedia = uiState.allMediaSummaries,
                        allCategories = allCategories,
                        categoryAssignments = categoryAssignments,
                        homeRows = homeRows,
                        heroSettings = heroSettings,
                        continueTrackingStyle = continueTrackingStyle,
                        tmdbKey = tmdbKey,
                        heroMultiRatingsCache = heroMultiRatingsCache,
                        calendarReleases = calendarReleases,
                        isLoadingCalendar = isLoadingCalendar,
                        isLoadingTrending = isLoadingTrending,
                        isInitialLoading = uiState.isLoading,
                        trendingItems = trendingItems,
                        categoryDiscoverShelves = categoryDiscoverShelves,
                        watchedTimestamps90d = watchedTimestamps90d,
                        homeListState = homeListState,
                        hasUpdateAvailable = availableUpdate != null,
                        posterStyle = posterStyle,
                        fanfictionEnabled = fanfictionEnabled,
                        visibleFilterTabs = visibleFilterTabs,
                        fanficNsfwMode = fanficNsfwMode,
                        onItemClick = onItemClick,
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
                            coroutineScope.launch {
                                prefManager.setChimanewGroupMode(LibraryGroupMode.BY_MEDIA_TYPE)
                            }
                            pendingLibraryTabKey = "MT_${type.name}"
                            viewModel.selectMediaType(type)
                            currentNavDestination = NavDestination.LIBRARY
                        },
                        onNavigateToCustomCategory = { catId ->
                            coroutineScope.launch {
                                prefManager.setChimanewGroupMode(LibraryGroupMode.BY_CATEGORY)
                            }
                            pendingLibraryTabKey = "CAT_$catId"
                            currentNavDestination = NavDestination.LIBRARY
                        },
                        onNavigateToDiscover = { currentNavDestination = NavDestination.DISCOVER },
                        onNavigateToHistory = { currentNavDestination = NavDestination.HISTORY }
                    )
                }

                NavDestination.LIBRARY -> {
                    val activeTab = dynamicTabs.getOrNull(pagerState.currentPage)
                    val activeTabFilteredItems = remember(
                        uiState.allMediaSummaries,
                        uiState.selectedStatus,
                        searchQuery,
                        activeTab,
                        libSettings,
                        categoryAssignments,
                        selectedMangaSubtype,
                        selectedNovelSubtype,
                        selectedVnOriginSubtype,
                        selectedVnLengthSubtype,
                        selectedGamePlatform,
                        selectedGameCompletion,
                        selectedBookFormat,
                        selectedBookCategory,
                        selectedComicPublisher,
                        selectedComicFormat,
                        selectedFanficSites,
                        selectedFanficRating,
                        selectedFanficStatus,
                        selectedFanficMinWords,
                        fanficCrossoverOnly,
                        fanficTagFilter,
                        fanficNsfwMode
                    ) {
                        if (activeTab == null) emptyList()
                        else computeLibraryTabItems(
                            allSummaries = uiState.allMediaSummaries,
                            tab = activeTab,
                            selectedStatus = uiState.selectedStatus,
                            searchQuery = searchQuery,
                            libSettings = libSettings,
                            categoryAssignments = categoryAssignments,
                            fanficNsfwMode = fanficNsfwMode,
                            selectedMangaSubtype = selectedMangaSubtype,
                            selectedNovelSubtype = selectedNovelSubtype,
                            selectedVnOriginSubtype = selectedVnOriginSubtype,
                            selectedVnLengthSubtype = selectedVnLengthSubtype,
                            selectedGamePlatform = selectedGamePlatform,
                            selectedGameCompletion = selectedGameCompletion,
                            selectedBookFormat = selectedBookFormat,
                            selectedBookCategory = selectedBookCategory,
                            selectedComicPublisher = selectedComicPublisher,
                            selectedComicFormat = selectedComicFormat,
                            selectedFanficSites = selectedFanficSites,
                            selectedFanficRating = selectedFanficRating,
                            selectedFanficStatus = selectedFanficStatus,
                            selectedFanficMinWords = selectedFanficMinWords,
                            fanficCrossoverOnly = fanficCrossoverOnly,
                            fanficTagFilter = fanficTagFilter
                        )
                    }
                    val activeMediaTabType = activeTab?.mediaTypeFilter
                    val activeNovelTabSplit = activeTab?.novelSubtypeFilter != null
                    val activeSubFilterPills = remember(
                        activeMediaTabType,
                        activeNovelTabSplit,
                        selectedMangaSubtype,
                        selectedNovelSubtype,
                        selectedVnOriginSubtype,
                        selectedVnLengthSubtype,
                        selectedGamePlatform,
                        selectedGameCompletion,
                        selectedBookFormat,
                        selectedBookCategory,
                        selectedComicPublisher,
                        selectedComicFormat,
                        selectedFanficSites,
                        selectedFanficRating,
                        selectedFanficStatus,
                        selectedFanficMinWords,
                        fanficCrossoverOnly,
                        fanficTagFilter
                    ) {
                        buildList<Pair<String, () -> Unit>> {
                            when (activeMediaTabType) {
                                MediaType.MANGA -> {
                                    if (selectedMangaSubtype != "ALL") add(selectedMangaSubtype.lowercase().replaceFirstChar { it.uppercase() } to { selectedMangaSubtype = "ALL" })
                                }
                                MediaType.NOVEL -> {
                                    if (!activeNovelTabSplit && selectedNovelSubtype != "ALL") {
                                        add((if (selectedNovelSubtype == "LIGHT_NOVEL") "Light Novel" else "Web Novel") to { selectedNovelSubtype = "ALL" })
                                    }
                                }
                                MediaType.VISUAL_NOVEL -> {
                                    if (selectedVnOriginSubtype != "ALL") add(selectedVnOriginSubtype to { selectedVnOriginSubtype = "ALL" })
                                    if (selectedVnLengthSubtype != "ALL") add(selectedVnLengthSubtype to { selectedVnLengthSubtype = "ALL" })
                                }
                                MediaType.GAME -> {
                                    if (selectedGamePlatform != "ALL") add(selectedGamePlatform to { selectedGamePlatform = "ALL" })
                                    if (selectedGameCompletion != "ALL") add(selectedGameCompletion to { selectedGameCompletion = "ALL" })
                                }
                                MediaType.BOOK -> {
                                    if (selectedBookFormat != "ALL") add(selectedBookFormat to { selectedBookFormat = "ALL" })
                                    if (selectedBookCategory != "ALL") add(selectedBookCategory to { selectedBookCategory = "ALL" })
                                }
                                MediaType.COMIC -> {
                                    if (selectedComicPublisher != "ALL") add(selectedComicPublisher to { selectedComicPublisher = "ALL" })
                                    if (selectedComicFormat != "ALL") add(selectedComicFormat.replace("_", " ") to { selectedComicFormat = "ALL" })
                                }
                                MediaType.FANFICTION -> {
                                    selectedFanficSites.sorted().forEach { site ->
                                        add(
                                            site to {
                                                selectedFanficSitesRaw = (selectedFanficSites - site).joinToString(",")
                                            }
                                        )
                                    }
                                    if (selectedFanficRating != "ALL") add("Rated $selectedFanficRating" to { selectedFanficRating = "ALL" })
                                    if (selectedFanficStatus != "ALL") add(selectedFanficStatus to { selectedFanficStatus = "ALL" })
                                    if (selectedFanficMinWords > 0) add("${selectedFanficMinWords / 1000}k+ words" to { selectedFanficMinWords = 0 })
                                    if (fanficCrossoverOnly) add("Crossovers" to { fanficCrossoverOnly = false })
                                    if (fanficTagFilter.isNotBlank()) add("#${fanficTagFilter.trim()}" to { fanficTagFilter = "" })
                                }
                                else -> Unit
                            }
                        }
                    }
                    val hasActiveChimanewFilters = remember(libSettings, uiState.selectedStatus, activeSubFilterPills) {
                        uiState.selectedStatus != TrackingStatus.ALL ||
                            activeSubFilterPills.isNotEmpty() ||
                            libSettings.filterStarted != TriStateFilter.OFF ||
                            libSettings.filterCompleted != TriStateFilter.OFF ||
                            libSettings.filterUnread != TriStateFilter.OFF ||
                            libSettings.filterRated != TriStateFilter.OFF ||
                            libSettings.filterFavorite != TriStateFilter.OFF ||
                            libSettings.filterMediaTypes.isNotEmpty() ||
                            libSettings.filterTrackers.isNotEmpty() ||
                            libSettings.filterIncludeCategoryIds.isNotEmpty() ||
                            libSettings.filterExcludeCategoryIds.isNotEmpty()
                    }
                    val scrollerSortMode = remember(libSettings.sortOption) {
                        when (libSettings.sortOption) {
                            LibrarySortOption.ALPHA -> "TITLE"
                            LibrarySortOption.RATING -> "RATING"
                            else -> "RECENT"
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        // Top Header: Directly replaced by Option A MultiSelectTopBar during Multi-Select!
                        if (isLibraryMultiSelect) {
                            MultiSelectTopBar(
                                selectedCount = selectedLibraryIds.size,
                                totalCount = activeTabFilteredItems.size,
                                onSelectAll = {
                                    selectedLibraryIds = activeTabFilteredItems.map { it.id }.toSet()
                                },
                                onInvertSelection = {
                                    val currentIds = activeTabFilteredItems.map { it.id }.toSet()
                                    selectedLibraryIds = (selectedLibraryIds - currentIds) + (currentIds - selectedLibraryIds)
                                },
                                onClearSelection = {
                                    selectedLibraryIds = emptySet()
                                },
                                onExitMultiSelect = {
                                    isLibraryMultiSelect = false
                                    selectedLibraryIds = emptySet()
                                }
                            )
                        } else {
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

                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(DarkSurfaceElevated)
                                            .clickable {
                                                if (uiState.selectedMediaType == MediaType.FANFICTION) {
                                                    fanficImportInitialTab = 0
                                                    fanficImportInitialUrl = ""
                                                    showFanficImportSheet = true
                                                } else {
                                                    customEntryInitTitle = ""
                                                    customEntryInitType = if (uiState.selectedMediaType == MediaType.ALL) MediaType.NOVEL else uiState.selectedMediaType
                                                    showCustomEntrySheet = true
                                                }
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

                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (hasActiveChimanewFilters) AccentGold.copy(alpha = 0.18f) else DarkSurfaceElevated)
                                            .clickable { showLibraryFilterSheet = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Library Settings",
                                            tint = AccentGold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isSearchVisible) AccentGold.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                            .clickable {
                                                val nextVisible = !isSearchVisible
                                                if (nextVisible) {
                                                    val txt = searchQueryField.text
                                                    searchQueryField = TextFieldValue(text = txt, selection = TextRange(txt.length))
                                                } else {
                                                    searchQueryField = TextFieldValue("")
                                                }
                                                isSearchVisible = nextVisible
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
                        }

                        // Animated In-Library Search Field
                        AnimatedVisibility(visible = isSearchVisible && !isLibraryMultiSelect) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
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
                                    value = searchQueryField,
                                    onValueChange = { searchQueryField = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(searchFocusRequester),
                                    singleLine = true,
                                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                                    cursorBrush = SolidColor(AccentGold),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text("Filter library titles, native names, creators...", color = TextMuted, fontSize = 13.5.sp)
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
                                            .clickable { searchQueryField = TextFieldValue("") }
                                    )
                                }
                            }
                        }

                        // Dynamic Chimanew Underline Group Tabs (driven by LibraryGroupMode)
                        val tabCounts = remember(
                            dynamicTabs,
                            uiState.allMediaSummaries,
                            uiState.selectedStatus,
                            searchQuery,
                            libSettings,
                            categoryAssignments,
                            selectedFanficSites,
                            fanficNsfwMode
                        ) {
                            if (!libSettings.showTabItemCounts) emptyMap()
                            else dynamicTabs.associate { tab ->
                                tab.key to computeLibraryTabItems(
                                    allSummaries = uiState.allMediaSummaries,
                                    tab = tab,
                                    selectedStatus = uiState.selectedStatus,
                                    searchQuery = searchQuery,
                                    libSettings = libSettings,
                                    categoryAssignments = categoryAssignments,
                                    fanficNsfwMode = fanficNsfwMode,
                                    selectedFanficSites = selectedFanficSites,
                                    sort = false
                                ).size
                            }
                        }

                        if (libSettings.showCategoryTabs && dynamicTabs.size > 1) {
                            ChimanewLibraryGroupTabRow(
                                tabs = dynamicTabs,
                                selectedPage = pagerState.currentPage.coerceIn(0, dynamicTabs.lastIndex),
                                showCounts = libSettings.showTabItemCounts,
                                countsByKey = tabCounts,
                                onTabSelected = { idx ->
                                    dynamicTabs.getOrNull(idx)?.key?.let { savedLibraryTabKey = it }
                                    if (idx != pagerState.currentPage) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(
                                                page = idx,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        }
                                    }
                                }
                            )
                        }

                        if (libSettings.groupMode != LibraryGroupMode.BY_STATUS && !isLibraryMultiSelect) {
                            StatusFilterChips(
                                selectedStatus = uiState.selectedStatus,
                                onStatusSelected = { viewModel.selectStatus(it) },
                                mediaType = activeMediaTabType,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }

                        // Compact dismissible pills ONLY when contextual category sub-filters are active
                        AnimatedVisibility(visible = activeSubFilterPills.isNotEmpty() && !isLibraryMultiSelect) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 3.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                activeSubFilterPills.forEach { (label, onClear) ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(AccentGold.copy(alpha = 0.16f))
                                            .clickable(onClick = onClear)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentGold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove filter",
                                            tint = AccentGold,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Clear",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary,
                                    modifier = Modifier
                                        .clickable {
                                            when (activeMediaTabType) {
                                                MediaType.MANGA -> selectedMangaSubtype = "ALL"
                                                MediaType.NOVEL -> selectedNovelSubtype = "ALL"
                                                MediaType.VISUAL_NOVEL -> {
                                                    selectedVnOriginSubtype = "ALL"
                                                    selectedVnLengthSubtype = "ALL"
                                                }
                                                MediaType.GAME -> {
                                                    selectedGamePlatform = "ALL"
                                                    selectedGameCompletion = "ALL"
                                                }
                                                MediaType.BOOK -> {
                                                    selectedBookFormat = "ALL"
                                                    selectedBookCategory = "ALL"
                                                }
                                                MediaType.COMIC -> {
                                                    selectedComicPublisher = "ALL"
                                                    selectedComicFormat = "ALL"
                                                }
                                                MediaType.FANFICTION -> {
                                                    selectedFanficSitesRaw = ""
                                                    selectedFanficRating = "ALL"
                                                    selectedFanficStatus = "ALL"
                                                    selectedFanficMinWords = 0
                                                    fanficCrossoverOnly = false
                                                    fanficTagFilter = ""
                                                }
                                                else -> activeSubFilterPills.forEach { it.second() }
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Main Content: Horizontal Pager across dynamic tabs
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
                                key = { idx -> dynamicTabs.getOrNull(idx)?.key ?: "page_$idx" },
                                modifier = Modifier.fillMaxSize()
                            ) { pageIndex ->
                                if (!perfSettings.prefetchAdjacentTabs && pageIndex !in ((pagerState.currentPage - 1)..(pagerState.currentPage + 1))) {
                                    Box(modifier = Modifier.fillMaxSize())
                                    return@HorizontalPager
                                }
                                val pageTab = dynamicTabs.getOrNull(pageIndex) ?: LibraryGroupTab("ALL", "All")
                                val pageGridState = remember(pageTab.key) {
                                    tabGridStates.getOrPut(pageTab.key) { androidx.compose.foundation.lazy.grid.LazyGridState() }
                                }
                                val pageListState = remember(pageTab.key) {
                                    tabListStates.getOrPut(pageTab.key) { LazyListState() }
                                }

                                val displayedItems = remember(
                                    uiState.allMediaSummaries,
                                    uiState.selectedStatus,
                                    searchQuery,
                                    pageTab,
                                    libSettings,
                                    categoryAssignments,
                                    selectedMangaSubtype,
                                    selectedNovelSubtype,
                                    selectedVnOriginSubtype,
                                    selectedVnLengthSubtype,
                                    selectedGamePlatform,
                                    selectedGameCompletion,
                                    selectedBookFormat,
                                    selectedBookCategory,
                                    selectedComicPublisher,
                                    selectedComicFormat,
                                    selectedFanficSites,
                                    selectedFanficRating,
                                    selectedFanficStatus,
                                    selectedFanficMinWords,
                                    fanficCrossoverOnly,
                                    fanficTagFilter,
                                    fanficNsfwMode
                                ) {
                                    computeLibraryTabItems(
                                        allSummaries = uiState.allMediaSummaries,
                                        tab = pageTab,
                                        selectedStatus = uiState.selectedStatus,
                                        searchQuery = searchQuery,
                                        libSettings = libSettings,
                                        categoryAssignments = categoryAssignments,
                                        fanficNsfwMode = fanficNsfwMode,
                                        selectedMangaSubtype = selectedMangaSubtype,
                                        selectedNovelSubtype = selectedNovelSubtype,
                                        selectedVnOriginSubtype = selectedVnOriginSubtype,
                                        selectedVnLengthSubtype = selectedVnLengthSubtype,
                                        selectedGamePlatform = selectedGamePlatform,
                                        selectedGameCompletion = selectedGameCompletion,
                                        selectedBookFormat = selectedBookFormat,
                                        selectedBookCategory = selectedBookCategory,
                                        selectedComicPublisher = selectedComicPublisher,
                                        selectedComicFormat = selectedComicFormat,
                                        selectedFanficSites = selectedFanficSites,
                                        selectedFanficRating = selectedFanficRating,
                                        selectedFanficStatus = selectedFanficStatus,
                                        selectedFanficMinWords = selectedFanficMinWords,
                                        fanficCrossoverOnly = fanficCrossoverOnly,
                                        fanficTagFilter = fanficTagFilter
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (!perfSettings.disableCardAnimations) {
                                                Modifier.graphicsLayer {
                                                    val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                                                    val kinematicScale = (1f - (kotlin.math.abs(pageOffset) * 0.05f)).coerceIn(0.95f, 1f)
                                                    val kinematicAlpha = (1f - (kotlin.math.abs(pageOffset) * 0.35f)).coerceIn(0.65f, 1f)
                                                    scaleX = kinematicScale
                                                    scaleY = kinematicScale
                                                    alpha = kinematicAlpha
                                                }
                                            } else Modifier
                                        )
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
                                                        else -> "No items in ${pageTab.label}"
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
                                    } else if (libSettings.displayMode == LibraryDisplayMode.LIST) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            LazyColumn(
                                                state = pageListState,
                                                contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 6.dp, bottom = if (isLibraryMultiSelect) 128.dp else 112.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                items(
                                                    items = displayedItems,
                                                    key = { "lib_list_${it.id}" },
                                                    contentType = { "library_list_row" }
                                                ) { item ->
                                                    val isItemSelected = item.id in selectedLibraryIds
                                                    LibraryListRow(
                                                        item = item,
                                                        showQuickProgressButton = libSettings.showQuickContinue && !isLibraryMultiSelect,
                                                        showRemainingBadge = libSettings.showRemainingBadge,
                                                        showTypeBadge = libSettings.showTypeBadge,
                                                        showScoreBadge = libSettings.showScoreBadge,
                                                        isSelectionMode = isLibraryMultiSelect,
                                                        isSelected = isItemSelected,
                                                        onClick = {
                                                            if (isLibraryMultiSelect) {
                                                                selectedLibraryIds = if (isItemSelected) selectedLibraryIds - item.id else selectedLibraryIds + item.id
                                                            } else {
                                                                AppLogger.i("HomeScreen", "Tapped list item: ${item.title} (id: ${item.id})")
                                                                com.omnitrack.app.ui.screens.detail.ActiveSiblingHolder.setSiblings(displayedItems.map { it.id })
                                                                onItemClick(item.id)
                                                            }
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            isLibraryMultiSelect = true
                                                            selectedLibraryIds = if (isItemSelected) selectedLibraryIds - item.id else selectedLibraryIds + item.id
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
                                                sortMode = scrollerSortMode,
                                                listState = pageListState,
                                                columnsCount = 1
                                            )
                                        }
                                    } else {
                                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                            val isLandscapeScreen = maxWidth >= 600.dp
                                            val resolvedCols = when {
                                                posterStyle.isLandscape -> if (isLandscapeScreen) 2 else 1
                                                isLandscapeScreen -> libSettings.landscapeColumns.coerceIn(4, 8)
                                                libSettings.portraitColumns > 0 -> libSettings.portraitColumns.coerceIn(2, 6)
                                                else -> (maxWidth / posterStyle.widthDp.dp).toInt().coerceAtLeast(2)
                                            }
                                            val isDense = resolvedCols >= 4 && !posterStyle.isLandscape
                                            LazyVerticalGrid(
                                                state = pageGridState,
                                                columns = GridCells.Fixed(resolvedCols),
                                                contentPadding = PaddingValues(
                                                    start = if (isDense) 12.dp else 14.dp,
                                                    end = if (isDense) 14.dp else 16.dp,
                                                    top = 6.dp,
                                                    bottom = if (isLibraryMultiSelect) 128.dp else 112.dp
                                                ),
                                                horizontalArrangement = Arrangement.spacedBy(if (isDense) 7.dp else 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(if (isDense) 10.dp else 14.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                itemsIndexed(
                                                    items = displayedItems,
                                                    key = { _, item -> item.id },
                                                    contentType = { _, _ -> "library_grid_card" }
                                                ) { _, item ->
                                                    val isItemSelected = item.id in selectedLibraryIds
                                                    MediaCard(
                                                        item = item,
                                                        isSelectionMode = isLibraryMultiSelect,
                                                        isSelected = isItemSelected,
                                                        cornerRadiusDp = if (isDense) minOf(posterStyle.cornerRadiusDp, 8) else posterStyle.cornerRadiusDp,
                                                        isLandscape = posterStyle.isLandscape,
                                                        hideLabels = posterStyle.hideLabels,
                                                        cardDepthEnabled = posterStyle.cardDepthEnabled && posterStyle.cardDepthPostersEnabled,
                                                        cardDepthEdgeStrength = posterStyle.cardDepthEdgeStrength.toFloat(),
                                                        cardDepthSheenStrength = posterStyle.cardDepthSheenStrength.toFloat(),
                                                        cardDepthEdgeCoverage = posterStyle.cardDepthEdgeCoverage.toFloat(),
                                                        showQuickProgressButton = libSettings.showQuickContinue && !isLibraryMultiSelect,
                                                        showLandscapeClearlogo = posterStyle.alwaysShowLandscapeClearlogo,
                                                        displayMode = libSettings.displayMode,
                                                        showRemainingBadge = libSettings.showRemainingBadge,
                                                        showTypeBadge = libSettings.showTypeBadge,
                                                        showScoreBadge = libSettings.showScoreBadge,
                                                        isDenseGrid = isDense,
                                                        onCardClick = {
                                                            if (isLibraryMultiSelect) {
                                                                selectedLibraryIds = if (isItemSelected) selectedLibraryIds - item.id else selectedLibraryIds + item.id
                                                            } else {
                                                                AppLogger.i("HomeScreen", "Tapped card: ${item.title} (id: ${item.id})")
                                                                com.omnitrack.app.ui.screens.detail.ActiveSiblingHolder.setSiblings(displayedItems.map { it.id })
                                                                onItemClick(item.id)
                                                            }
                                                        },
                                                        onCardLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            isLibraryMultiSelect = true
                                                            selectedLibraryIds = if (isItemSelected) selectedLibraryIds - item.id else selectedLibraryIds + item.id
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
                                                sortMode = scrollerSortMode,
                                                gridState = pageGridState,
                                                columnsCount = resolvedCols
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
                        onOpenFanficImport = { url, tab ->
                            fanficImportInitialUrl = url
                            fanficImportInitialTab = tab
                            showFanficImportSheet = true
                        },
                        modifier = Modifier.statusBarsPadding(),
                        homeViewModel = viewModel
                    )
                }

                NavDestination.HISTORY -> {
                    val watchedTimestamps90d by viewModel.watchedTimestamps90d.collectAsState()
                    HistoryView(
                        allMedia = uiState.allMediaSummaries,
                        onItemClick = onItemClick,
                        onQuickProgress = { viewModel.incrementProgress(it) },
                        onMarkCompleted = { viewModel.markCompleted(it) },
                        onSetStatus = { summary, status -> viewModel.setMediaStatus(summary, status) },
                        onRemoveFromHistory = { summary -> viewModel.removeFromHistory(summary.id) },
                        onDeleteItem = { summary -> viewModel.deleteMediaItem(summary.id) },
                        onBatchMarkCompleted = { ids -> viewModel.batchMarkCompleted(ids) },
                        onBatchMarkUnread = { ids -> viewModel.batchMarkUnread(ids) },
                        onBatchIncrementProgress = { summaries -> viewModel.batchIncrementProgress(summaries) },
                        onBatchSetStatus = { ids, status -> viewModel.batchSetStatus(ids, status) },
                        onBatchSyncTrackers = { ids ->
                            Toast.makeText(context, "Syncing ${ids.size} items...", Toast.LENGTH_SHORT).show()
                            viewModel.batchSyncToTrackers(ids) { count ->
                                Toast.makeText(context, "Synced $count / ${ids.size} items to connected trackers", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onBatchRemoveFromHistory = { ids -> viewModel.batchRemoveFromHistory(ids) },
                        onBatchDeleteItems = { ids -> viewModel.batchDeleteItems(ids) },
                        watchedTimestamps90d = watchedTimestamps90d,
                        visibleFilterTabs = visibleFilterTabs,
                        onOpenHeatmap = { showActivityHeatmapSheet = true },
                        onMultiSelectChanged = { isHistoryMultiSelect = it }
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
                        onNavigateToManageCategories = onNavigateToManageCategories,
                        onOpenHomeCustomizer = { showHomeCustomizerSheet = true },
                        onOpenDiscoverCustomizer = { showDiscoverCustomizerSheet = true },
                        onOpenFanficSites = {
                            fanficImportInitialUrl = ""
                            fanficImportInitialTab = 1
                            showFanficImportSheet = true
                        }
                    )
                }
            }
        }

        // Floating Liquid Glass Navigation Dock (automatically hidden when flush Multi-Select bottom bar is active)
        val hideNavForMultiSelect = (isLibraryMultiSelect && currentNavDestination == NavDestination.LIBRARY) ||
            (isHistoryMultiSelect && currentNavDestination == NavDestination.HISTORY)
        if (!hideNavForMultiSelect) {
            OmniFloatingNavBar(
                selectedDestination = currentNavDestination,
                onDestinationSelected = { destination ->
                    currentNavDestination = destination
                },
                scrollState = navScrollState,
                hazeState = if (enableLiveNavBlur) hazeState else null,
                pauseLiveBlur = isActiveLibraryScrolling,
                onActiveTabReSelected = { destination ->
                    if (destination == NavDestination.LIBRARY) {
                        val activeKey = dynamicTabs.getOrNull(pagerState.currentPage)?.key
                        val activeGridState = activeKey?.let { tabGridStates[it] }
                        coroutineScope.launch {
                            activeGridState?.animateScrollToItem(0)
                        }
                        val nextVisible = !isSearchVisible
                        if (nextVisible) {
                            val txt = searchQueryField.text
                            searchQueryField = TextFieldValue(text = txt, selection = TextRange(txt.length))
                        }
                        isSearchVisible = nextVisible
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

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
                onDismiss = { showHomeCustomizerSheet = false },
                customCategories = allCategories,
                onOpenManageCategories = {
                    showHomeCustomizerSheet = false
                    onNavigateToManageCategories()
                }
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
                onLoadMonthWindow = { viewModel.loadReleaseCalendar(force = false, daysAhead = 35) },
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
            val activeSheetTab = dynamicTabs.getOrNull(pagerState.currentPage)
            val activeSheetMediaTab = activeSheetTab?.mediaTypeFilter
            ChimanewLibrarySheet(
                settings = libSettings,
                selectedStatus = uiState.selectedStatus,
                categories = allCategories,
                fanfictionEnabled = fanfictionEnabled,
                visibleMediaTypes = visibleMediaTypes,
                subFilters = LibraryCategorySubFilters(
                    activeMediaTabType = activeSheetMediaTab,
                    isNovelTabSplit = activeSheetTab?.novelSubtypeFilter != null,
                    mangaSubtype = selectedMangaSubtype,
                    onMangaSubtype = { selectedMangaSubtype = it },
                    novelSubtype = selectedNovelSubtype,
                    onNovelSubtype = { selectedNovelSubtype = it },
                    vnOrigin = selectedVnOriginSubtype,
                    onVnOrigin = { selectedVnOriginSubtype = it },
                    vnLength = selectedVnLengthSubtype,
                    onVnLength = { selectedVnLengthSubtype = it },
                    gamePlatform = selectedGamePlatform,
                    onGamePlatform = { selectedGamePlatform = it },
                    gameCompletion = selectedGameCompletion,
                    onGameCompletion = { selectedGameCompletion = it },
                    bookFormat = selectedBookFormat,
                    onBookFormat = { selectedBookFormat = it },
                    bookCategory = selectedBookCategory,
                    onBookCategory = { selectedBookCategory = it },
                    comicPublisher = selectedComicPublisher,
                    onComicPublisher = { selectedComicPublisher = it },
                    comicFormat = selectedComicFormat,
                    onComicFormat = { selectedComicFormat = it },
                    fanficSites = selectedFanficSites,
                    onToggleFanficSite = { site ->
                        val next = if (site in selectedFanficSites) {
                            selectedFanficSites - site
                        } else {
                            selectedFanficSites + site
                        }
                        selectedFanficSitesRaw = next.joinToString(",")
                    },
                    onClearFanficSites = { selectedFanficSitesRaw = "" },
                    fanficRating = selectedFanficRating,
                    onFanficRating = { selectedFanficRating = it },
                    fanficStatus = selectedFanficStatus,
                    onFanficStatus = { selectedFanficStatus = it },
                    fanficMinWords = selectedFanficMinWords,
                    onFanficMinWords = { selectedFanficMinWords = it },
                    fanficCrossoverOnly = fanficCrossoverOnly,
                    onFanficCrossoverToggle = { fanficCrossoverOnly = !fanficCrossoverOnly },
                    fanficTagFilter = fanficTagFilter,
                    onFanficTagFilter = { fanficTagFilter = it },
                    onOpenFanficImport = {
                        showLibraryFilterSheet = false
                        fanficImportInitialTab = 0
                        fanficImportInitialUrl = ""
                        showFanficImportSheet = true
                    }
                ),
                onSelectStatus = { viewModel.selectStatus(it) },
                onToggleTriStateFilter = { key, next ->
                    coroutineScope.launch { prefManager.setChimanewTriStateFilter(key, next) }
                },
                onSetFilterMediaTypes = { types ->
                    coroutineScope.launch { prefManager.setChimanewFilterMediaTypes(types) }
                },
                onSetFilterTrackers = { trackers ->
                    coroutineScope.launch { prefManager.setChimanewFilterTrackers(trackers) }
                },
                onSetCategoryFilterSets = { inc, exc ->
                    coroutineScope.launch { prefManager.setChimanewFilterCategorySets(inc, exc) }
                },
                onSelectSort = { opt, asc ->
                    coroutineScope.launch { prefManager.setChimanewSort(opt, asc) }
                },
                onSelectDisplayMode = { mode ->
                    coroutineScope.launch { prefManager.setChimanewDisplayMode(mode) }
                },
                onSetPortraitColumns = { cols ->
                    coroutineScope.launch { prefManager.setChimanewPortraitColumns(cols) }
                },
                onSetLandscapeColumns = { cols ->
                    coroutineScope.launch { prefManager.setChimanewLandscapeColumns(cols) }
                },
                onToggleOverlay = { key, enabled ->
                    coroutineScope.launch { prefManager.setChimanewOverlayToggle(key, enabled) }
                },
                onSelectGroupMode = { mode ->
                    coroutineScope.launch { prefManager.setChimanewGroupMode(mode) }
                },
                onOpenManageCategories = {
                    reopenFilterSheetOnReturn = true
                    showLibraryFilterSheet = false
                    onNavigateToManageCategories()
                },
                onResetFilters = {
                    viewModel.selectStatus(com.omnitrack.app.data.model.TrackingStatus.ALL)
                    selectedMangaSubtype = "ALL"
                    selectedNovelSubtype = "ALL"
                    selectedVnOriginSubtype = "ALL"
                    selectedVnLengthSubtype = "ALL"
                    selectedGamePlatform = "ALL"
                    selectedGameCompletion = "ALL"
                    selectedBookFormat = "ALL"
                    selectedBookCategory = "ALL"
                    selectedComicPublisher = "ALL"
                    selectedComicFormat = "ALL"
                    selectedFanficSitesRaw = ""
                    selectedFanficRating = "ALL"
                    selectedFanficStatus = "ALL"
                    selectedFanficMinWords = 0
                    fanficCrossoverOnly = false
                    fanficTagFilter = ""
                    coroutineScope.launch { prefManager.resetChimanewLibraryFilters() }
                },
                onDismiss = { showLibraryFilterSheet = false }
            )
        }

        if (showMultiSelectCategoriesSheet && selectedLibraryIds.isNotEmpty()) {
            MultiSelectCategoriesSheet(
                selectedMediaIds = selectedLibraryIds,
                categories = allCategories,
                categoryAssignments = categoryAssignments,
                onCreateCategory = { name -> viewModel.createCategory(name) },
                onApplyChanges = { addIds, removeIds ->
                    val targetIds = selectedLibraryIds
                    coroutineScope.launch {
                        prefManager.assignCategoriesToMediaBatch(targetIds, addIds, removeIds)
                    }
                    Toast.makeText(context, "Updated categories for ${targetIds.size} items", Toast.LENGTH_SHORT).show()
                },
                onOpenManageCategories = {
                    showMultiSelectCategoriesSheet = false
                    onNavigateToManageCategories()
                },
                onDismiss = { showMultiSelectCategoriesSheet = false }
            )
        }

        if (showCustomEntrySheet) {
            CustomEntryEditorSheet(
                existingItem = null,
                initialTitle = customEntryInitTitle,
                initialMediaType = customEntryInitType,
                availableCategories = allCategories,
                linkableSeries = uiState.allMediaSummaries,
                visibleMediaTypes = visibleMediaTypes,
                initialCategoryIds = libSettings.defaultCategoryId?.let { setOf(it) } ?: emptySet(),
                onCreateCategory = { name, onCreated ->
                    viewModel.createCategory(name, onCreated)
                },
                onSaveFull = { entity, catIds, vnRoutes ->
                    showCustomEntrySheet = false
                    viewModel.createCustomMediaEntry(
                        entity = entity,
                        categoryIds = catIds,
                        prefManager = prefManager,
                        vnRoutes = vnRoutes
                    ) { newId ->
                        if (newId > 0L) onItemClick(newId)
                    }
                },
                onSaveWithCategories = { entity, catIds ->
                    showCustomEntrySheet = false
                    viewModel.createCustomMediaEntry(
                        entity = entity,
                        categoryIds = catIds,
                        prefManager = prefManager
                    ) { newId ->
                        if (newId > 0L) onItemClick(newId)
                    }
                },
                onSave = { entity ->
                    showCustomEntrySheet = false
                    viewModel.createCustomMediaEntry(entity, prefManager = prefManager) { newId ->
                        if (newId > 0L) onItemClick(newId)
                    }
                },
                onDismiss = { showCustomEntrySheet = false }
            )
        }

        val singleSelectedType = remember(selectedLibraryIds, uiState.allMediaSummaries) {
            if (selectedLibraryIds.size != 1) null
            else uiState.allMediaSummaries.firstOrNull { it.id in selectedLibraryIds }?.mediaType
        }

        // Flush #000000 Option A Hairline Multi-Select Bottom Bar for Library
        AnimatedVisibility(
            visible = isLibraryMultiSelect && currentNavDestination == NavDestination.LIBRARY,
            enter = fadeIn(animationSpec = tween(160)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(animationSpec = tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MultiSelectBottomActionDock(
                selectedCount = selectedLibraryIds.size,
                singleMediaType = singleSelectedType,
                onOpenCategories = {
                    showMultiSelectCategoriesSheet = true
                },
                onOpenDetails = {
                    val singleId = selectedLibraryIds.firstOrNull()
                    if (singleId != null && selectedLibraryIds.size == 1) {
                        isLibraryMultiSelect = false
                        selectedLibraryIds = emptySet()
                        onItemClick(singleId)
                    }
                },
                onCustomizeArtwork = {
                    val singleId = selectedLibraryIds.firstOrNull()
                    if (singleId != null && selectedLibraryIds.size == 1) {
                        isLibraryMultiSelect = false
                        selectedLibraryIds = emptySet()
                        artworkPickerMediaId = singleId
                    }
                },
                onMarkCompleted = {
                    val ids = selectedLibraryIds
                    viewModel.batchMarkCompleted(ids)
                    Toast.makeText(context, "Marked ${ids.size} items completed", Toast.LENGTH_SHORT).show()
                    isLibraryMultiSelect = false
                    selectedLibraryIds = emptySet()
                },
                onMarkUnread = {
                    val ids = selectedLibraryIds
                    viewModel.batchMarkUnread(ids)
                    Toast.makeText(context, "Reset ${ids.size} items to unread (0)", Toast.LENGTH_SHORT).show()
                    isLibraryMultiSelect = false
                    selectedLibraryIds = emptySet()
                },
                onIncrementProgress = {
                    val ids = selectedLibraryIds
                    val targets = uiState.allMediaSummaries.filter { it.id in ids }
                    viewModel.batchIncrementProgress(targets)
                    Toast.makeText(context, "Incremented progress on ${targets.size} items", Toast.LENGTH_SHORT).show()
                },
                onSetStatus = { st ->
                    val ids = selectedLibraryIds
                    viewModel.batchSetStatus(ids, st)
                    Toast.makeText(context, "Updated ${ids.size} items to ${st.labelFor(singleSelectedType)}", Toast.LENGTH_SHORT).show()
                    isLibraryMultiSelect = false
                    selectedLibraryIds = emptySet()
                },
                onSyncTrackers = {
                    val ids = selectedLibraryIds
                    Toast.makeText(context, "Syncing ${ids.size} items...", Toast.LENGTH_SHORT).show()
                    viewModel.batchSyncToTrackers(ids) { count ->
                        Toast.makeText(context, "Synced $count / ${ids.size} items to trackers", Toast.LENGTH_SHORT).show()
                    }
                    isLibraryMultiSelect = false
                    selectedLibraryIds = emptySet()
                },
                onDeleteSelected = {
                    if (selectedLibraryIds.isNotEmpty()) {
                        showBatchDeleteConfirm = true
                    }
                }
            )
        }

        val targetArtMediaId = artworkPickerMediaId
        if (targetArtMediaId != null) {
            val artItemState by remember(targetArtMediaId) {
                viewModel.observeMediaById(targetArtMediaId)
            }.collectAsState(initial = null)
            val artItem = artItemState
            if (artItem != null) {
                val losslessCover = remember(artItem.customCoverPath, artItem.coverUrl) {
                    LosslessCoverMapper.toLosslessUrl(artItem.customCoverPath ?: artItem.coverUrl)
                }
                ArtworkPickerBottomSheet(
                    title = if (artItem.mediaType == MediaType.FANFICTION) {
                        artItem.fanficPrimaryFandom?.takeIf { it.isNotBlank() } ?: artItem.title
                    } else artItem.title,
                    mediaType = artItem.mediaType,
                    currentCoverUrl = losslessCover,
                    currentBannerUrl = artItem.bannerUrl,
                    currentCustomBannerPath = artItem.customBannerPath,
                    mediaId = artItem.id,
                    currentRole = artItem.artworkRole,
                    anilistId = artItem.anilistId,
                    malId = artItem.malId,
                    mangadexId = artItem.mangadexId,
                    nativeTitle = artItem.nativeTitle,
                    originCountry = artItem.originCountry,
                    defaultCoverUrl = artItem.coverUrl,
                    vndbId = artItem.vndbId,
                    steamAppId = artItem.steamAppId,
                    openLibraryId = artItem.openLibraryId,
                    bookAuthor = artItem.bookAuthor,
                    initialExcludedSources = remember(artItem.excludedArtSources) {
                        artItem.excludedArtSources
                            ?.split(",")
                            ?.map { it.trim().uppercase() }
                            ?.filter { it.isNotBlank() }
                            ?.toSet()
                            ?: emptySet()
                    },
                    onExcludedSourcesChanged = { excluded ->
                        viewModel.updateItemExcludedArtSources(artItem.id, excluded)
                    },
                    currentBannerFocalX = artItem.bannerFocalX,
                    currentBannerFocalY = artItem.bannerFocalY,
                    currentBannerZoom = artItem.bannerZoom,
                    onBannerFramingChanged = { fx, fy, z ->
                        viewModel.updateItemBannerFraming(artItem.id, fx, fy, z)
                    },
                    onDismiss = { artworkPickerMediaId = null },
                    onCoverSelected = { newUrl ->
                        if (newUrl.startsWith("/") || newUrl.startsWith("file:") || newUrl.startsWith("content:")) {
                            viewModel.updateItemCustomCoverPath(artItem.id, newUrl)
                        } else {
                            viewModel.updateItemCover(artItem.id, newUrl)
                        }
                    },
                    onBannerSelected = { newBanner ->
                        if (newBanner == "NONE") {
                            viewModel.toggleItemDisableBanner(artItem.id)
                        } else if (newBanner.startsWith("/") || newBanner.startsWith("file:")) {
                            viewModel.updateItemCustomBannerPath(artItem.id, newBanner)
                        } else {
                            viewModel.updateItemBanner(artItem.id, newBanner)
                        }
                    },
                    onResetCover = { viewModel.resetItemCover(artItem.id) },
                    onResetBanner = { viewModel.resetItemBanner(artItem.id) },
                    onRoleSelected = { newRole -> viewModel.updateItemArtworkRole(artItem.id, newRole) }
                )
            }
        }

        if (showBatchDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteConfirm = false },
                containerColor = AmoledBlack,
                title = {
                    Text("Delete ${selectedLibraryIds.size} Selected Items?", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = "This permanently removes the selected entries and their local notes/history from your OmniTrack Library.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val ids = selectedLibraryIds
                            showBatchDeleteConfirm = false
                            viewModel.batchDeleteItems(ids)
                            Toast.makeText(context, "Deleted ${ids.size} items", Toast.LENGTH_SHORT).show()
                            isLibraryMultiSelect = false
                            selectedLibraryIds = emptySet()
                        }
                    ) {
                        Text("Delete All", color = androidx.compose.ui.graphics.Color(0xFFF87171), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteConfirm = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        if (showFanficImportSheet) {
            com.omnitrack.app.ui.components.FanficImportSheet(
                initialTab = fanficImportInitialTab,
                initialUrl = fanficImportInitialUrl,
                onDismiss = {
                    showFanficImportSheet = false
                    fanficImportInitialUrl = ""
                },
                onOpenDetail = { id ->
                    showFanficImportSheet = false
                    fanficImportInitialUrl = ""
                    onItemClick(id)
                }
            )
        }
    }
    }
}

@Composable
private fun ChimanewLibraryGroupTabRow(
    tabs: List<LibraryGroupTab>,
    selectedPage: Int,
    showCounts: Boolean,
    countsByKey: Map<String, Int>,
    onTabSelected: (Int) -> Unit
) {
    val tabListState = rememberLazyListState()
    LaunchedEffect(selectedPage) {
        tabListState.animateScrollToItem(selectedPage.coerceAtLeast(0))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.lazy.LazyRow(
            state = tabListState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(tabs.size, key = { idx -> tabs[idx].key }) { idx ->
                val tab = tabs[idx]
                val isSelected = selectedPage == idx
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) TextPrimary else TextMuted,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "libTabText"
                )
                val barColor by animateColorAsState(
                    targetValue = if (isSelected) AccentGold else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "libTabBar"
                )
                val countSuffix = if (showCounts) {
                    countsByKey[tab.key]?.let { " $it" }.orEmpty()
                } else ""

                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onTabSelected(idx) }
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = tab.label,
                            color = textColor,
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (countSuffix.isNotEmpty()) {
                            Text(
                                text = countSuffix,
                                color = if (isSelected) AccentGold else TextMuted.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(barColor)
                    )
                }
            }
        }
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            thickness = 0.6.dp
        )
    }
}

private fun itemMatchesTrackerKey(
    item: com.omnitrack.app.data.db.dto.MediaCardSummary,
    trackerKey: String
): Boolean = when (trackerKey.uppercase()) {
    "ANILIST" -> item.anilistId != null && item.anilistId > 0L
    "MAL" -> item.malId != null && item.malId > 0L
    "TMDB" -> item.tmdbId != null && item.tmdbId > 0L
    "TRAKT" -> item.traktId != null && item.traktId > 0L
    "SIMKL" -> item.simklId != null && item.simklId > 0L
    "STEAM" -> item.steamAppId != null && item.steamAppId > 0L
    "VNDB" -> !item.vndbId.isNullOrBlank()
    "HARDCOVER" -> !item.hardcoverId.isNullOrBlank()
    "LOCAL" -> item.anilistId == null && item.malId == null && item.tmdbId == null &&
        item.traktId == null && item.simklId == null && item.steamAppId == null &&
        item.vndbId.isNullOrBlank() && item.hardcoverId.isNullOrBlank() &&
        item.comicVineId == null && item.metronId == null
    else -> true
}

@Composable
private fun HomeTabContent(
    viewModel: HomeViewModel,
    prefManager: PreferenceManager,
    allMedia: List<com.omnitrack.app.data.db.dto.MediaCardSummary>,
    allCategories: List<com.omnitrack.app.data.db.entity.CategoryEntity>,
    categoryAssignments: Map<Long, Set<Long>>,
    homeRows: List<HomeRowItem>,
    heroSettings: HomeHeroSettings,
    continueTrackingStyle: ContinueTrackingStyle,
    tmdbKey: String,
    heroMultiRatingsCache: Map<String, CachedMultiRatings>,
    calendarReleases: List<CalendarReleaseItem>,
    isLoadingCalendar: Boolean,
    isLoadingTrending: Boolean,
    isInitialLoading: Boolean = false,
    trendingItems: List<SearchResultItem>,
    categoryDiscoverShelves: Map<HomeRowKey, List<SearchResultItem>>,
    watchedTimestamps90d: List<Long>,
    homeListState: LazyListState,
    hasUpdateAvailable: Boolean,
    posterStyle: PosterCardStyle,
    fanfictionEnabled: Boolean,
    visibleFilterTabs: List<MediaType>,
    fanficNsfwMode: com.omnitrack.app.data.model.VnNsfwFilterMode,
    onItemClick: (Long) -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenCustomizer: () -> Unit,
    onOpenHeatmap: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenUpdate: () -> Unit,
    onNavigateToLibraryCategory: (MediaType) -> Unit,
    onNavigateToCustomCategory: (Long) -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    HomeDashboardView(
        allMedia = allMedia,
        trendingItems = trendingItems,
        calendarReleases = calendarReleases,
        watchedTimestamps90d = watchedTimestamps90d,
        homeRows = homeRows,
        heroSettings = heroSettings,
        continueTrackingStyle = continueTrackingStyle,
        categoryDiscoverShelves = categoryDiscoverShelves,
        hasUpdateAvailable = hasUpdateAvailable,
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
        onOpenCalendar = onOpenCalendar,
        onOpenCustomizer = onOpenCustomizer,
        onOpenHeatmap = onOpenHeatmap,
        onOpenSearch = onOpenSearch,
        onOpenUpdate = onOpenUpdate,
        onNavigateToLibraryCategory = onNavigateToLibraryCategory,
        onNavigateToDiscover = onNavigateToDiscover,
        onNavigateToHistory = onNavigateToHistory,
        homeListState = homeListState,
        posterStyle = posterStyle,
        isInitialLoading = isInitialLoading,
        isRefreshing = isLoadingTrending || isLoadingCalendar,
        onRefresh = {
            viewModel.loadHomeTrending(force = true)
            viewModel.loadReleaseCalendar(force = true)
        },
        fanfictionEnabled = fanfictionEnabled,
        visibleFilterTabs = visibleFilterTabs,
        fanficNsfwMode = fanficNsfwMode,
        customCategories = allCategories,
        categoryAssignments = categoryAssignments,
        onNavigateToCustomCategory = onNavigateToCustomCategory,
        heroMultiRatingsCache = heroMultiRatingsCache,
        onPrefetchLocalHeroRatings = { heroItem ->
            val cached = heroMultiRatingsCache[heroRatingsCacheKey(heroItem.id, heroItem.mediaType, heroItem.title)]
            viewModel.prefetchLocalHeroRatings(heroItem, cached, prefManager)
        },
        onPrefetchSearchHeroRatings = { searchItem ->
            val cached = heroMultiRatingsCache[heroRatingsCacheKey(null, searchItem.mediaType, searchItem.title)]
            viewModel.prefetchSearchHeroRatings(searchItem, cached, prefManager)
        }
    )
}

private fun computeLibraryTabItems(
    allSummaries: List<com.omnitrack.app.data.db.dto.MediaCardSummary>,
    tab: LibraryGroupTab,
    selectedStatus: TrackingStatus,
    searchQuery: String,
    libSettings: ChimanewLibrarySettings,
    categoryAssignments: Map<Long, Set<Long>>,
    fanficNsfwMode: com.omnitrack.app.data.model.VnNsfwFilterMode,
    selectedMangaSubtype: String = "ALL",
    selectedNovelSubtype: String = "ALL",
    selectedVnOriginSubtype: String = "ALL",
    selectedVnLengthSubtype: String = "ALL",
    selectedGamePlatform: String = "ALL",
    selectedGameCompletion: String = "ALL",
    selectedBookFormat: String = "ALL",
    selectedBookCategory: String = "ALL",
    selectedComicPublisher: String = "ALL",
    selectedComicFormat: String = "ALL",
    selectedFanficSites: Set<String> = emptySet(),
    selectedFanficRating: String = "ALL",
    selectedFanficStatus: String = "ALL",
    selectedFanficMinWords: Int = 0,
    fanficCrossoverOnly: Boolean = false,
    fanficTagFilter: String = "",
    sort: Boolean = true
): List<com.omnitrack.app.data.db.dto.MediaCardSummary> {
    val pageMediaType = tab.mediaTypeFilter
    val filtered = allSummaries.filter { item ->
        if (item.mediaType == MediaType.FANFICTION && com.omnitrack.app.ui.components.shouldHideFanficItem(item.fanficRating, fanficNsfwMode)) {
            return@filter false
        }
        // Group tab predicate
        val tabMatches = when {
            tab.mediaTypeFilter != null -> {
                val typeMatch = tab.mediaTypeFilter == MediaType.ALL || item.mediaType == tab.mediaTypeFilter
                val novelSplitMatch = tab.novelSubtypeFilter == null ||
                    (item.mediaType == MediaType.NOVEL &&
                        inferNovelSubtype(item.platform, item.originCountry, item.anilistId, item.malId, item.totalProgress, item.title, item.fanficSourceSite) == tab.novelSubtypeFilter)
                typeMatch && novelSplitMatch
            }
            tab.categoryId != null -> tab.categoryId in categoryAssignments[item.id].orEmpty()
            tab.isUncategorized -> categoryAssignments[item.id].isNullOrEmpty()
            tab.statusFilter != null -> tab.statusFilter == TrackingStatus.ALL || item.status == tab.statusFilter
            tab.trackerKey != null -> itemMatchesTrackerKey(item, tab.trackerKey)
            else -> true
        }
        if (!tabMatches) return@filter false

        // Media-specific subtype bars (when viewing a MediaType tab)
        if (pageMediaType != null) {
            val subtypeOk = (pageMediaType != MediaType.MANGA || selectedMangaSubtype == "ALL" || inferMangaSubtype(item.originCountry, item.nativeTitle) == selectedMangaSubtype) &&
                (pageMediaType != MediaType.NOVEL || tab.novelSubtypeFilter != null || selectedNovelSubtype == "ALL" || inferNovelSubtype(item.platform, item.originCountry, item.anilistId, item.malId, item.totalProgress, item.title, item.fanficSourceSite) == selectedNovelSubtype) &&
                (pageMediaType != MediaType.VISUAL_NOVEL || selectedVnOriginSubtype == "ALL" || com.omnitrack.app.data.model.inferVnOriginGroup(item.originCountry, item.nativeTitle) == selectedVnOriginSubtype) &&
                (pageMediaType != MediaType.VISUAL_NOVEL || selectedVnLengthSubtype == "ALL" || com.omnitrack.app.data.model.inferVnLengthGroup(item.runtimeMinutes) == selectedVnLengthSubtype) &&
                (pageMediaType != MediaType.GAME || selectedGamePlatform == "ALL" || selectedGamePlatform in com.omnitrack.app.data.model.normalizeGamePlatformGroup(item.platforms, item.platform)) &&
                (pageMediaType != MediaType.GAME || selectedGameCompletion == "ALL" || when (selectedGameCompletion) {
                    "MAIN" -> item.gameCompletionTier.equals("MAIN", true) || item.gameCompletionTier.equals("MAIN_PLUS", true)
                    "COMP" -> item.gameCompletionTier.equals("COMPLETIONIST", true) || item.gameCompletionTier.equals("PLATINUM", true)
                    "BEATEN" -> item.status == TrackingStatus.COMPLETED
                    "BACKLOG" -> item.status == TrackingStatus.PLAN_TO_TRACK
                    else -> true
                }) &&
                (pageMediaType != MediaType.BOOK || selectedBookFormat == "ALL" || com.omnitrack.app.data.model.normalizeBookFormatGroup(item.bookFormat) == selectedBookFormat) &&
                (pageMediaType != MediaType.BOOK || selectedBookCategory == "ALL" || com.omnitrack.app.data.model.inferBookCategoryGroup(item.title, item.platform, null) == selectedBookCategory) &&
                (pageMediaType != MediaType.COMIC || selectedComicPublisher == "ALL" || com.omnitrack.app.data.model.normalizeComicPublisherGroup(item.comicPublisher, item.platform) == selectedComicPublisher) &&
                (pageMediaType != MediaType.COMIC || selectedComicFormat == "ALL" || com.omnitrack.app.data.model.normalizeComicFormatGroup(item.comicFormat) == selectedComicFormat) &&
                (pageMediaType != MediaType.FANFICTION || selectedFanficSites.isEmpty() || selectedFanficSites.any { site -> item.fanficSourceSite.equals(site, ignoreCase = true) }) &&
                (pageMediaType != MediaType.FANFICTION || selectedFanficRating == "ALL" || com.omnitrack.app.data.model.FanficRating.fromRaw(item.fanficRating).code == selectedFanficRating) &&
                (pageMediaType != MediaType.FANFICTION || selectedFanficStatus == "ALL" || com.omnitrack.app.data.model.FanficWorkStatus.fromRaw(item.fanficWorkStatus).name == selectedFanficStatus) &&
                (pageMediaType != MediaType.FANFICTION || selectedFanficMinWords <= 0 || (item.fanficWordCount ?: 0) >= selectedFanficMinWords) &&
                (pageMediaType != MediaType.FANFICTION || !fanficCrossoverOnly || item.fanficIsCrossover) &&
                (pageMediaType != MediaType.FANFICTION || fanficTagFilter.isBlank() ||
                    item.fanficPrimaryFandom?.contains(fanficTagFilter, ignoreCase = true) == true ||
                    item.fanficTagsSearch?.contains(fanficTagFilter, ignoreCase = true) == true)
            if (!subtypeOk) return@filter false
        }

        // Top quick status strip
        if (selectedStatus != TrackingStatus.ALL && item.status != selectedStatus) return@filter false

        // Chimanew Tri-State filters
        val isStarted = item.currentProgress > 0.0 || item.playtimeHours > 0.0 || item.status == TrackingStatus.IN_PROGRESS
        when (libSettings.filterStarted) {
            TriStateFilter.INCLUDE -> if (!isStarted) return@filter false
            TriStateFilter.EXCLUDE -> if (isStarted) return@filter false
            TriStateFilter.OFF -> Unit
        }
        val isCompleted = item.status == TrackingStatus.COMPLETED
        when (libSettings.filterCompleted) {
            TriStateFilter.INCLUDE -> if (!isCompleted) return@filter false
            TriStateFilter.EXCLUDE -> if (isCompleted) return@filter false
            TriStateFilter.OFF -> Unit
        }
        val hasUnreadLeft = (item.totalProgress != null && item.totalProgress > item.currentProgress) ||
            (item.status != TrackingStatus.COMPLETED && item.currentProgress == 0.0)
        when (libSettings.filterUnread) {
            TriStateFilter.INCLUDE -> if (!hasUnreadLeft) return@filter false
            TriStateFilter.EXCLUDE -> if (hasUnreadLeft) return@filter false
            TriStateFilter.OFF -> Unit
        }
        val isRated = (item.userRating ?: 0f) > 0f
        when (libSettings.filterRated) {
            TriStateFilter.INCLUDE -> if (!isRated) return@filter false
            TriStateFilter.EXCLUDE -> if (isRated) return@filter false
            TriStateFilter.OFF -> Unit
        }
        when (libSettings.filterFavorite) {
            TriStateFilter.INCLUDE -> if (!item.isFavorite) return@filter false
            TriStateFilter.EXCLUDE -> if (item.isFavorite) return@filter false
            TriStateFilter.OFF -> Unit
        }

        // Chimanew Multi-Vertical & Multi-Tracker & Custom Category filters
        if (libSettings.filterMediaTypes.isNotEmpty() && item.mediaType.name !in libSettings.filterMediaTypes) {
            return@filter false
        }
        if (libSettings.filterTrackers.isNotEmpty() && libSettings.filterTrackers.none { itemMatchesTrackerKey(item, it) }) {
            return@filter false
        }
        val itemCats = categoryAssignments[item.id].orEmpty()
        if (libSettings.filterIncludeCategoryIds.isNotEmpty() && itemCats.none { it in libSettings.filterIncludeCategoryIds }) {
            return@filter false
        }
        if (libSettings.filterExcludeCategoryIds.isNotEmpty() && itemCats.any { it in libSettings.filterExcludeCategoryIds }) {
            return@filter false
        }

        // Search bar predicate
        if (searchQuery.isNotBlank()) {
            val matched = item.title.contains(searchQuery, ignoreCase = true) ||
                item.nativeTitle?.contains(searchQuery, ignoreCase = true) == true ||
                item.bookAuthor?.contains(searchQuery, ignoreCase = true) == true ||
                item.studioOrNetwork?.contains(searchQuery, ignoreCase = true) == true ||
                item.comicPublisher?.contains(searchQuery, ignoreCase = true) == true ||
                item.fanficPrimaryFandom?.contains(searchQuery, ignoreCase = true) == true ||
                item.fanficTagsSearch?.contains(searchQuery, ignoreCase = true) == true ||
                item.fanficSourceSite?.contains(searchQuery, ignoreCase = true) == true
            if (!matched) return@filter false
        }
        true
    }

    val groupedItems = if (libSettings.groupAnimeSeasonsInLibrary && searchQuery.isBlank()) {
        collapseGroupedSeasonsInLibrary(filtered)
    } else {
        filtered
    }

    if (!sort) return groupedItems

    val asc = libSettings.sortAscending
    return when (libSettings.sortOption) {
        LibrarySortOption.ALPHA -> {
            val base = groupedItems.sortedBy { it.title.lowercase() }
            if (asc) base else base.reversed()
        }
        LibrarySortOption.RATING -> {
            if (asc) {
                groupedItems.sortedWith(compareBy<com.omnitrack.app.data.db.dto.MediaCardSummary> { it.userRating ?: it.communityScore ?: 0f }.thenByDescending { it.lastUpdated })
            } else {
                groupedItems.sortedWith(compareByDescending<com.omnitrack.app.data.db.dto.MediaCardSummary> { it.userRating ?: it.communityScore ?: 0f }.thenByDescending { it.lastUpdated })
            }
        }
        LibrarySortOption.TOTAL -> {
            if (asc) groupedItems.sortedBy { it.totalProgress ?: 0.0 }
            else groupedItems.sortedByDescending { it.totalProgress ?: 0.0 }
        }
        LibrarySortOption.REMAINING -> {
            val rem: (com.omnitrack.app.data.db.dto.MediaCardSummary) -> Double = {
                val tot = it.totalProgress ?: 0.0
                if (tot > it.currentProgress) tot - it.currentProgress else 0.0
            }
            if (asc) groupedItems.sortedBy(rem) else groupedItems.sortedByDescending(rem)
        }
        LibrarySortOption.PROGRESS -> {
            val frac: (com.omnitrack.app.data.db.dto.MediaCardSummary) -> Double = {
                val tot = it.totalProgress ?: 0.0
                if (tot > 0.0) (it.currentProgress / tot).coerceIn(0.0, 1.0) else 0.0
            }
            if (asc) groupedItems.sortedBy(frac) else groupedItems.sortedByDescending(frac)
        }
        LibrarySortOption.RANDOM -> groupedItems.shuffled(java.util.Random(groupedItems.size.toLong() * 1337L + if (asc) 1L else 2L))
        LibrarySortOption.RECENT -> {
            if (asc) groupedItems.sortedBy { it.lastUpdated } else groupedItems.sortedByDescending { it.lastUpdated }
        }
    }
}

private val ANIME_SEASON_SUFFIX_REGEX = Regex(
    """(?i)(?:\s*[:\-–]?\s*(?:season\s+\d+|\d+(?:st|nd|rd|th)\s+season|part\s+\d+|cour\s+\d+|s\d+|(?:ii|iii|iv|v))|\s+(?:2|3|4|5))\s*$"""
)

private fun parseAnimeFranchiseKey(rawTitle: String): Pair<String, Boolean> {
    var working = rawTitle.trim()
    var hadSuffix = false
    while (true) {
        val next = working.replace(ANIME_SEASON_SUFFIX_REGEX, "").trim()
        if (next == working || next.length < 2) break
        working = next
        hadSuffix = true
    }
    val key = working.lowercase().replace(Regex("[^\\p{L}\\p{Nd}]"), "")
    return key to hadSuffix
}

private fun collapseGroupedSeasonsInLibrary(
    items: List<com.omnitrack.app.data.db.dto.MediaCardSummary>
): List<com.omnitrack.app.data.db.dto.MediaCardSummary> {
    if (items.size <= 1) return items
    val byId = items.associateBy { it.id }
    val childrenByParent = mutableMapOf<Long, MutableList<com.omnitrack.app.data.db.dto.MediaCardSummary>>()
    val collapsedIds = HashSet<Long>()

    // 1. Collapse explicitly linked custom entries into their parent when parent is visible
    for (item in items) {
        val parentId = item.linkedParentMediaId
        if (parentId != null && parentId != item.id && parentId in byId) {
            childrenByParent.getOrPut(parentId) { mutableListOf() }.add(item)
            collapsedIds.add(item.id)
        }
    }

    // 2. Non-destructively collapse numbered Anime season sequels into their root S1 Anime card
    val animeRoots = mutableMapOf<String, com.omnitrack.app.data.db.dto.MediaCardSummary>()
    for (item in items) {
        if (item.id in collapsedIds || item.mediaType != MediaType.ANIME) continue
        val (baseKey, isSequel) = parseAnimeFranchiseKey(item.title)
        if (!isSequel && baseKey.length >= 2) {
            val prev = animeRoots[baseKey]
            if (prev == null || item.id < prev.id) {
                animeRoots[baseKey] = item
            }
        }
    }
    for (item in items) {
        if (item.id in collapsedIds || item.mediaType != MediaType.ANIME) continue
        val (baseKey, isSequel) = parseAnimeFranchiseKey(item.title)
        val root = animeRoots[baseKey]
        if (isSequel && root != null && root.id != item.id) {
            childrenByParent.getOrPut(root.id) { mutableListOf() }.add(item)
            collapsedIds.add(item.id)
        }
    }

    if (collapsedIds.isEmpty()) return items

    return items.mapNotNull { item ->
        if (item.id in collapsedIds) return@mapNotNull null
        val children = childrenByParent[item.id] ?: return@mapNotNull item
        val allParts = listOf(item) + children
        val combinedCurrent = allParts.sumOf { it.currentProgress }
        val combinedTotal = if (allParts.all { (it.totalProgress ?: 0.0) > 0.0 }) {
            allParts.sumOf { it.totalProgress ?: 0.0 }
        } else {
            item.totalProgress
        }
        val combinedStatus = when {
            allParts.all { it.status == TrackingStatus.COMPLETED } -> TrackingStatus.COMPLETED
            allParts.any { it.status == TrackingStatus.IN_PROGRESS } || combinedCurrent > 0.0 -> TrackingStatus.IN_PROGRESS
            else -> item.status
        }
        item.copy(
            currentProgress = combinedCurrent,
            totalProgress = combinedTotal,
            status = combinedStatus,
            lastUpdated = allParts.maxOf { it.lastUpdated }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryListRow(
    item: com.omnitrack.app.data.db.dto.MediaCardSummary,
    showQuickProgressButton: Boolean = false,
    showRemainingBadge: Boolean = true,
    showTypeBadge: Boolean = false,
    showScoreBadge: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onQuickProgress: () -> Unit
) {
    val perf = LocalPerformanceSettings.current
    val context = LocalContext.current
    val coverModel = remember(item.customCoverPath, item.coverUrl, perf.downsampleGridCovers) {
        item.customCoverPath?.let { if (it.startsWith("http")) it else File(it) }
            ?: if (perf.downsampleGridCovers) LosslessCoverMapper.toGridThumbnailUrl(item.coverUrl)
            else LosslessCoverMapper.toLosslessUrl(item.coverUrl)
    }
    val rowImageRequest = remember(coverModel, perf.downsampleGridCovers, perf.hardwareBitmaps, perf.disableCardAnimations) {
        ImageRequest.Builder(context)
            .data(coverModel)
            .apply {
                if (perf.downsampleGridCovers) {
                    size(180, 265)
                    precision(Precision.INEXACT)
                }
            }
            .allowHardware(perf.hardwareBitmaps)
            .crossfade(!perf.disableCardAnimations)
            .build()
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
            .background(if (isSelected) AccentGold.copy(alpha = 0.08f) else Color.Transparent)
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 0.6.dp.toPx()
                )
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isSelectionMode) {
            MultiSelectCheckBadge(selected = isSelected)
        }
        val hasThumbImg = coverModel != null && (coverModel !is String || coverModel.isNotBlank())
        Box(
            modifier = Modifier
                .width(50.dp)
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceCard)
        ) {
            if (item.mediaType == MediaType.FANFICTION && !hasThumbImg) {
                com.omnitrack.app.ui.components.FanficTypographicCover(
                    title = item.title,
                    author = item.bookAuthor,
                    primaryFandom = item.fanficPrimaryFandom,
                    sourceSite = item.fanficSourceSite,
                    ratingCode = item.fanficRating,
                    wordCount = item.fanficWordCount,
                    isCrossover = item.fanficIsCrossover,
                    compact = true
                )
            } else {
                AsyncImage(
                    model = rowImageRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

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
            val remLeft = remember(item.totalProgress, item.currentProgress, item.mediaType) {
                val tot = item.totalProgress?.toInt() ?: 0
                val cur = item.currentProgress.toInt()
                if (item.mediaType != MediaType.GAME && item.mediaType != MediaType.VISUAL_NOVEL && tot > cur) {
                    (tot - cur).coerceAtLeast(0)
                } else 0
            }
            val subParts = buildList {
                if (showTypeBadge) add(item.mediaType.displayName)
                progressLabel.takeIf { it.isNotBlank() }?.let { add(it) }
                if (showRemainingBadge && remLeft > 0) add("+$remLeft left")
                if (showScoreBadge) rating?.takeIf { it > 0f }?.let { add("\u2605 ${formatSmartRating(it.toDouble())}") }
            }
            if (subParts.isNotEmpty()) {
                Text(
                    text = subParts.joinToString(" \u00B7 "),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AccentGold)
                    .clickable(onClick = onQuickProgress),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.mediaType == MediaType.MOVIE) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = "Quick Log",
                    tint = OnAccentColor,
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
    title: String,
    fanficSourceSite: String? = null
): String {
    if (!fanficSourceSite.isNullOrBlank()) return "WEB_NOVEL"

    val normPlatform = platform?.trim()?.uppercase()
    if (normPlatform == "LIGHT_NOVEL" || normPlatform == "LIGHT NOVEL" || normPlatform == "LN") {
        return "LIGHT_NOVEL"
    }
    if (
        normPlatform == "WEB_NOVEL" ||
        normPlatform == "WEB NOVEL" ||
        normPlatform == "WN" ||
        normPlatform in setOf("WATTPAD", "AO3", "FFN", "SB", "SV", "QQ", "ROYALROAD", "ROYAL ROAD", "SYOSETU", "KAKUYOMU", "SCRIBBLEHUB", "WEBNOVEL", "NOVELUPDATES") ||
        (normPlatform != null && ("WATTPAD" in normPlatform || "WEB NOVEL" in normPlatform))
    ) {
        return "WEB_NOVEL"
    }

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
internal fun FanficUnderlineFilterBar(
    selectedSite: String,
    selectedRating: String,
    selectedStatus: String,
    minWords: Int,
    crossoverOnly: Boolean,
    tagFilter: String,
    onSiteSelected: (String) -> Unit,
    onRatingSelected: (String) -> Unit,
    onStatusSelected: (String) -> Unit,
    onMinWordsSelected: (Int) -> Unit,
    onCrossoverToggle: () -> Unit,
    onTagFilterChanged: (String) -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        UnderlineFilterGroup(
            options = listOf(
                "ALL" to "All Sites",
                "AO3" to "AO3",
                "FFN" to "FF.net",
                "SB" to "SpaceBattles",
                "SV" to "SV",
                "QQ" to "QQ"
            ),
            selectedKey = selectedSite,
            onSelected = onSiteSelected
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(AccentGold.copy(alpha = 0.18f))
                    .border(0.7.dp, AccentGold.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                    .clickable(onClick = onOpenImport)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("+ Import URL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentGold)
            }
            listOf("ALL" to "Any Rating", "G" to "G", "T" to "T", "M" to "M", "E" to "E", "NR" to "NR").forEach { (k, lbl) ->
                val sel = selectedRating == k
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (sel) AccentGold.copy(alpha = 0.22f) else DarkSurfaceElevated)
                        .clickable { onRatingSelected(k) }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(lbl, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) AccentGold else TextSecondary)
                }
            }
            listOf("ALL" to "Any Status", "ONGOING" to "Ongoing", "COMPLETE" to "Complete", "HIATUS" to "Hiatus").forEach { (k, lbl) ->
                val sel = selectedStatus == k
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (sel) AccentGold.copy(alpha = 0.22f) else DarkSurfaceElevated)
                        .clickable { onStatusSelected(k) }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(lbl, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) AccentGold else TextSecondary)
                }
            }
            listOf(0 to "Any Length", 10_000 to "10k+", 50_000 to "50k+", 100_000 to "100k+").forEach { (w, lbl) ->
                val sel = minWords == w
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (sel) AccentGold.copy(alpha = 0.22f) else DarkSurfaceElevated)
                        .clickable { onMinWordsSelected(w) }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(lbl, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) AccentGold else TextSecondary)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (crossoverOnly) AccentGold.copy(alpha = 0.22f) else DarkSurfaceElevated)
                    .clickable(onClick = onCrossoverToggle)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text("Crossovers", fontSize = 11.sp, fontWeight = if (crossoverOnly) FontWeight.Bold else FontWeight.Medium, color = if (crossoverOnly) AccentGold else TextSecondary)
            }
            Row(
                modifier = Modifier
                    .width(145.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(DarkSurfaceElevated)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = tagFilter,
                    onValueChange = onTagFilterChanged,
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 11.sp),
                    cursorBrush = SolidColor(AccentGold),
                    decorationBox = { inner ->
                        if (tagFilter.isBlank()) {
                            Text("Fandom / ship / tag...", color = TextMuted, fontSize = 11.sp)
                        }
                        inner()
                    },
                    modifier = Modifier.weight(1f)
                )
                if (tagFilter.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = TextMuted,
                        modifier = Modifier
                            .size(12.dp)
                            .clickable { onTagFilterChanged("") }
                    )
                }
            }
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
    MediaType.FANFICTION -> "AO3"
    else -> "AniList"
}
