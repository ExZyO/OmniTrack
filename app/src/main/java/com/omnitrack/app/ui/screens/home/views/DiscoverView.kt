package com.omnitrack.app.ui.screens.home.views

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed as lazyRowItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.ai.AiQueryEngine
import com.omnitrack.app.data.artwork.ArtworkModelResolver
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.DiscoverCustomizationSettings
import com.omnitrack.app.data.preferences.DiscoverLayoutStyle
import com.omnitrack.app.data.preferences.DiscoverShelfCatalog
import com.omnitrack.app.data.preferences.DiscoverShelfDef
import com.omnitrack.app.data.preferences.PosterCardStyle
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.search.OmniSearchRepository
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.ui.components.FanficTypographicCover
import com.omnitrack.app.ui.components.GeminiModelSelectorSection
import com.omnitrack.app.ui.components.HeroPageLayer
import com.omnitrack.app.ui.components.MediaCard
import com.omnitrack.app.ui.components.NuvioRailPosterCard
import com.omnitrack.app.ui.components.heroPageForItem
import com.omnitrack.app.ui.components.heroPageOffset
import com.omnitrack.app.ui.components.heroVisibleLayers
import com.omnitrack.app.ui.screens.detail.ActiveSiblingHolder
import com.omnitrack.app.ui.screens.detail.EphemeralPreviewHolder
import com.omnitrack.app.ui.screens.detail.components.formatSmartRating
import com.omnitrack.app.ui.screens.home.DiscoverShelfState
import com.omnitrack.app.ui.screens.home.HomeViewModel
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DiscoverView(
    allMedia: List<MediaCardSummary>,
    onItemClick: (Long) -> Unit,
    onAddMedia: (SearchResultItem) -> Unit,
    onCreateCustomEntry: (String, MediaType) -> Unit = { _, _ -> },
    onOpenCustomizer: () -> Unit = {},
    onOpenFanficImport: (initialUrl: String, initialTab: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel? = null,
    initialSearchQuery: String = "",
    onBack: (() -> Unit)? = null,
    searchRepository: OmniSearchRepository = remember { OmniSearchRepository() },
    aiEngine: AiQueryEngine = remember { AiQueryEngine() }
) {
    val context = LocalContext.current
    val prefManager = remember(context) {
        (context.applicationContext as OmniTrackApplication).preferenceManager
    }
    val scope = rememberCoroutineScope()

    val geminiKey by prefManager.geminiApiKey.collectAsState(initial = "")
    val geminiModel by prefManager.geminiModel.collectAsState(initial = "gemini-2.5-flash")
    val customGeminiModel by prefManager.customGeminiModel.collectAsState(initial = "")
    val useCustomGeminiModel by prefManager.useCustomGeminiModel.collectAsState(initial = false)
    val activeGeminiModel by prefManager.activeGeminiModel.collectAsState(initial = "gemini-2.5-flash")
    var showModelPickerSheet by remember { mutableStateOf(false) }
    val openRouterKey by prefManager.openRouterApiKey.collectAsState(initial = "")
    val tmdbKey by prefManager.tmdbApiKey.collectAsState(initial = "")
    val aiSearchSetting by prefManager.aiSemanticSearchEnabled.collectAsState(initial = false)
    val posterStyle by prefManager.posterCardStyle.collectAsState(initial = PosterCardStyle())
    val discoverSettings by prefManager.discoverCustomizationSettings.collectAsState(
        initial = DiscoverCustomizationSettings()
    )
    val mediaCategoryPrefs by prefManager.mediaCategoryPrefs.collectAsState(
        initial = com.omnitrack.app.data.preferences.MediaCategoryPrefs()
    )
    val fanfictionEnabled = !mediaCategoryPrefs.isHidden(MediaType.FANFICTION)
    val fanficNsfwMode by prefManager.fanficNsfwFilterMode.collectAsState(
        initial = com.omnitrack.app.data.model.VnNsfwFilterMode.BLUR_TAP
    )

    val queryFlow = remember(homeViewModel, initialSearchQuery) {
        homeViewModel?.discoverSearchQuery ?: MutableStateFlow(initialSearchQuery)
    }
    val filterFlow = remember(homeViewModel) { homeViewModel?.discoverSelectedFilter ?: MutableStateFlow(MediaType.ALL) }
    val isSearchingFlow = remember(homeViewModel) { homeViewModel?.discoverIsSearching ?: MutableStateFlow(false) }
    val resultsFlow = remember(homeViewModel) { homeViewModel?.discoverSearchResults ?: MutableStateFlow<List<SearchResultItem>>(emptyList()) }
    val bannerFlow = remember(homeViewModel) { homeViewModel?.discoverAiResolutionBanner ?: MutableStateFlow<String?>(null) }
    val useAiFlow = remember(homeViewModel) { homeViewModel?.discoverUseAiSearch ?: MutableStateFlow<Boolean?>(null) }
    val shelvesFlow = remember(homeViewModel) {
        homeViewModel?.discoverShelves ?: MutableStateFlow<Map<String, DiscoverShelfState>>(emptyMap())
    }

    val searchQuery by queryFlow.collectAsState()
    val selectedFilter by filterFlow.collectAsState()
    val isSearching by isSearchingFlow.collectAsState()
    val searchResults by resultsFlow.collectAsState()
    val aiResolutionBanner by bannerFlow.collectAsState()
    val storedUseAi by useAiFlow.collectAsState()
    val discoverShelves by shelvesFlow.collectAsState()
    val useAiSearch = storedUseAi ?: aiSearchSetting
    val watchRegionPref by prefManager.watchProviderRegion.collectAsState(
        initial = PreferenceManager.cachedWatchProviderRegion
    )
    val streamingProvidersFlow = remember(homeViewModel) { homeViewModel?.streamingProviders ?: MutableStateFlow(emptyList()) }
    val selectedStreamingProviderIdFlow = remember(homeViewModel) { homeViewModel?.selectedStreamingProviderId ?: MutableStateFlow(null) }
    val streamingServiceTitlesFlow = remember(homeViewModel) { homeViewModel?.streamingServiceTitles ?: MutableStateFlow(emptyList()) }
    val isLoadingStreamingTitlesFlow = remember(homeViewModel) { homeViewModel?.isLoadingStreamingTitles ?: MutableStateFlow(false) }
    val top10TodayFlow = remember(homeViewModel) { homeViewModel?.top10TodayItems ?: MutableStateFlow(emptyList()) }
    val tasteMatchedSeedFlow = remember(homeViewModel) { homeViewModel?.tasteMatchedSeed ?: MutableStateFlow(null) }
    val tasteMatchedItemsFlow = remember(homeViewModel) { homeViewModel?.tasteMatchedItems ?: MutableStateFlow(emptyList()) }
    val selectedCuratedRowFlow = remember(homeViewModel) { homeViewModel?.selectedCuratedRowKey ?: MutableStateFlow("under_90") }
    val curatedRowItemsFlow = remember(homeViewModel) { homeViewModel?.curatedRowItems ?: MutableStateFlow(emptyList()) }
    val isLoadingCuratedRowFlow = remember(homeViewModel) { homeViewModel?.isLoadingCuratedRow ?: MutableStateFlow(false) }
    val dailyCriticsPickFlow = remember(homeViewModel) { homeViewModel?.dailyCriticsPick ?: MutableStateFlow(null) }
    val skippedSwipeKeysFlow = remember(homeViewModel) { homeViewModel?.skippedSwipeKeys ?: MutableStateFlow(emptySet()) }

    val streamingProviders by streamingProvidersFlow.collectAsState()
    val selectedStreamingProviderId by selectedStreamingProviderIdFlow.collectAsState()
    val streamingServiceTitles by streamingServiceTitlesFlow.collectAsState()
    val isLoadingStreamingTitles by isLoadingStreamingTitlesFlow.collectAsState()
    val top10TodayItems by top10TodayFlow.collectAsState()
    val tasteMatchedSeed by tasteMatchedSeedFlow.collectAsState()
    val tasteMatchedItems by tasteMatchedItemsFlow.collectAsState()
    val selectedCuratedRowKey by selectedCuratedRowFlow.collectAsState()
    val curatedRowItems by curatedRowItemsFlow.collectAsState()
    val isLoadingCuratedRow by isLoadingCuratedRowFlow.collectAsState()
    val dailyCriticsPick by dailyCriticsPickFlow.collectAsState()
    val skippedSwipeKeys by skippedSwipeKeysFlow.collectAsState()
    var showSwipeQueueSheet by remember { mutableStateOf(false) }

    LaunchedEffect(aiSearchSetting) {
        if (useAiFlow.value == null) {
            useAiFlow.value = aiSearchSetting
        }
    }

    val allCategories = remember(mediaCategoryPrefs) {
        mediaCategoryPrefs.visibleFilterTabs(includeAll = true)
    }
    val visibleCategories = remember(allCategories, discoverSettings.disabledCategories) {
        allCategories.filter { it.name !in discoverSettings.disabledCategories }.ifEmpty { listOf(MediaType.ALL) }
    }
    LaunchedEffect(visibleCategories, selectedFilter) {
        if (selectedFilter !in visibleCategories) {
            filterFlow.value = visibleCategories.first()
        }
    }

    var localLastDebounceQuery by remember { mutableStateOf(homeViewModel?.discoverLastDebounceQuery) }
    var localLastExecutedQuery by remember { mutableStateOf(homeViewModel?.discoverLastExecutedQuery) }
    var localLastExecutedFilter by remember { mutableStateOf(homeViewModel?.discoverLastExecutedFilter) }
    var localLastExecutedAi by remember { mutableStateOf(homeViewModel?.discoverLastExecutedAi) }
    var localLastExecutedModel by remember { mutableStateOf<String?>(null) }
    var localLastEffectiveQuery by remember { mutableStateOf(homeViewModel?.discoverLastEffectiveQuery) }
    var localLastEffectiveFilter by remember { mutableStateOf(homeViewModel?.discoverLastEffectiveFilter) }
    val searchGenRef = remember { AtomicLong(0L) }

    var selectedMangaSubFilter by remember { mutableStateOf("ALL") }
    var selectedNovelSubFilter by remember { mutableStateOf("ALL") }
    var selectedVnOriginSubFilter by remember { mutableStateOf("ALL") }
    var selectedVnLengthSubFilter by remember { mutableStateOf("ALL") }
    var selectedGamePlatformSubFilter by remember { mutableStateOf("ALL") }
    var selectedBookFormatSubFilter by remember { mutableStateOf("ALL") }
    var selectedBookCategorySubFilter by remember { mutableStateOf<String?>(null) }
    var selectedComicPublisherSubFilter by remember { mutableStateOf("ALL") }
    var selectedComicFormatSubFilter by remember { mutableStateOf("ALL") }
    var selectedFanficRatingSubFilter by remember { mutableStateOf("ALL") }
    var selectedFanficStatusSubFilter by remember { mutableStateOf("ALL") }
    var selectedFanficCrossoverOnly by remember { mutableStateOf(false) }

    // Hoisted parallel Discover category shelf loading (skips disabled shelves/categories)
    LaunchedEffect(
        selectedFilter,
        tmdbKey,
        discoverSettings.disabledCategories,
        discoverSettings.disabledShelves,
        watchRegionPref
    ) {
        homeViewModel?.loadDiscoverCategoryShelves(
            filter = selectedFilter,
            tmdbApiKey = tmdbKey,
            disabledCategories = discoverSettings.disabledCategories,
            disabledShelves = discoverSettings.disabledShelves
        )
        homeViewModel?.loadDiscoverHighlights(
            mediaFilter = selectedFilter,
            regionPref = watchRegionPref,
            tmdbApiKey = tmdbKey
        )
    }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = homeViewModel?.discoverGridScrollIndex ?: 0,
        initialFirstVisibleItemScrollOffset = homeViewModel?.discoverGridScrollOffset ?: 0
    )

    DisposableEffect(gridState) {
        onDispose {
            homeViewModel?.discoverGridScrollIndex = gridState.firstVisibleItemIndex
            homeViewModel?.discoverGridScrollOffset = gridState.firstVisibleItemScrollOffset
        }
    }

    // Multi-Vertical OmniSearch Trigger (strictly guarded by monotonic searchGenRef)
    LaunchedEffect(searchQuery, selectedFilter, useAiSearch, activeGeminiModel, discoverSettings.showAiToggle) {
        val myGen = searchGenRef.incrementAndGet()
        val trimmed = searchQuery.trim()
        val prevQuery = homeViewModel?.discoverLastExecutedQuery ?: localLastExecutedQuery
        val prevFilter = homeViewModel?.discoverLastExecutedFilter ?: localLastExecutedFilter
        val prevAi = homeViewModel?.discoverLastExecutedAi ?: localLastExecutedAi
        val prevDebounceQuery = homeViewModel?.discoverLastDebounceQuery ?: localLastDebounceQuery

        if (trimmed.length >= 2) {
            val modelChanged = useAiSearch && localLastExecutedModel != null && localLastExecutedModel != activeGeminiModel
            if (!modelChanged && resultsFlow.value.isNotEmpty() && trimmed == prevQuery && selectedFilter == prevFilter && useAiSearch == prevAi) {
                return@LaunchedEffect
            }
            localLastExecutedModel = activeGeminiModel
            val filterChanged = prevFilter != null && selectedFilter != prevFilter
            if (!filterChanged && !modelChanged && trimmed != prevDebounceQuery) {
                delay(380)
                if (searchGenRef.get() != myGen) return@LaunchedEffect
                localLastDebounceQuery = trimmed
                homeViewModel?.discoverLastDebounceQuery = trimmed
            } else if (trimmed != prevDebounceQuery) {
                localLastDebounceQuery = trimmed
                homeViewModel?.discoverLastDebounceQuery = trimmed
            }

            var queryToRun = trimmed
            var filterToRun = selectedFilter
            var resolvedBanner: String? = null

            if (useAiSearch && discoverSettings.showAiToggle) {
                val aiResult = aiEngine.resolveQuery(
                    rawQuery = trimmed,
                    geminiApiKey = geminiKey.ifBlank { null },
                    openRouterApiKey = openRouterKey.ifBlank { null },
                    geminiModel = activeGeminiModel
                )
                if (searchGenRef.get() != myGen) return@LaunchedEffect
                if (aiResult.isAiResolved) {
                    queryToRun = aiResult.resolvedTitle
                    resolvedBanner = "AI [$activeGeminiModel]: \"${aiResult.resolvedTitle}\"" +
                        (aiResult.detectedType?.let { " (${it.displayName})" } ?: "") +
                        (aiResult.explanation?.let { " \u2022 $it" } ?: "")
                    if (selectedFilter == MediaType.ALL && aiResult.detectedType != null) {
                        filterToRun = aiResult.detectedType
                    }
                } else {
                    if (aiResult.resolvedTitle != trimmed) {
                        queryToRun = aiResult.resolvedTitle
                    }
                    if (selectedFilter == MediaType.ALL && aiResult.detectedType != null) {
                        filterToRun = aiResult.detectedType
                    }
                    resolvedBanner = if (geminiKey.isBlank() && openRouterKey.isBlank()) {
                        if (aiResult.resolvedTitle != trimmed || aiResult.detectedType != null) {
                            "Smart Match: \"${aiResult.resolvedTitle}\"" +
                                (aiResult.detectedType?.let { " (${it.displayName})" } ?: "") +
                                " \u2022 Add a Gemini/OpenRouter key in Settings for full AI"
                        } else {
                            "AI Smart Mode (No API key in Settings) \u2014 Try natural phrases or add a Gemini key."
                        }
                    } else {
                        "AI Mode [$activeGeminiModel]: searching \"$queryToRun\""
                    }
                }
            }
            bannerFlow.value = resolvedBanner

            val lastEffQuery = homeViewModel?.discoverLastEffectiveQuery ?: localLastEffectiveQuery
            val lastEffFilter = homeViewModel?.discoverLastEffectiveFilter ?: localLastEffectiveFilter
            if (resultsFlow.value.isNotEmpty() && queryToRun == lastEffQuery && filterToRun == lastEffFilter) {
                localLastExecutedQuery = trimmed
                localLastExecutedFilter = selectedFilter
                localLastExecutedAi = useAiSearch
                homeViewModel?.discoverLastExecutedQuery = trimmed
                homeViewModel?.discoverLastExecutedFilter = selectedFilter
                homeViewModel?.discoverLastExecutedAi = useAiSearch
                isSearchingFlow.value = false
                return@LaunchedEffect
            }

            if (queryToRun != lastEffQuery) {
                resultsFlow.value = emptyList()
            } else if (filterToRun != MediaType.ALL) {
                resultsFlow.value = resultsFlow.value.filter { it.mediaType == filterToRun }
            }

            localLastExecutedQuery = trimmed
            localLastExecutedFilter = selectedFilter
            localLastExecutedAi = useAiSearch
            localLastEffectiveQuery = queryToRun
            localLastEffectiveFilter = filterToRun
            homeViewModel?.discoverLastExecutedQuery = trimmed
            homeViewModel?.discoverLastExecutedFilter = selectedFilter
            homeViewModel?.discoverLastExecutedAi = useAiSearch
            homeViewModel?.discoverLastEffectiveQuery = queryToRun
            homeViewModel?.discoverLastEffectiveFilter = filterToRun

            isSearchingFlow.value = true
            try {
                val results = searchRepository.searchAll(
                    query = queryToRun,
                    filter = filterToRun,
                    tmdbApiKey = tmdbKey.ifBlank { null },
                    onPartialResults = { partial ->
                        if (myGen == searchGenRef.get() && filterFlow.value == selectedFilter) {
                            resultsFlow.value = if (filterToRun == MediaType.ALL) partial else partial.filter { it.mediaType == filterToRun }
                        }
                    }
                )
                if (myGen == searchGenRef.get() && filterFlow.value == selectedFilter) {
                    resultsFlow.value = if (filterToRun == MediaType.ALL) results else results.filter { it.mediaType == filterToRun }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (myGen == searchGenRef.get()) {
                    resultsFlow.value = emptyList()
                }
            } finally {
                if (myGen == searchGenRef.get()) {
                    isSearchingFlow.value = false
                }
            }
        } else {
            localLastDebounceQuery = null
            localLastExecutedQuery = null
            localLastExecutedFilter = null
            localLastExecutedAi = null
            localLastEffectiveQuery = null
            localLastEffectiveFilter = null
            homeViewModel?.discoverLastDebounceQuery = null
            homeViewModel?.discoverLastExecutedQuery = null
            homeViewModel?.discoverLastExecutedFilter = null
            homeViewModel?.discoverLastExecutedAi = null
            homeViewModel?.discoverLastEffectiveQuery = null
            homeViewModel?.discoverLastEffectiveFilter = null
            resultsFlow.value = emptyList()
            if (!useAiSearch) {
                bannerFlow.value = null
            }
            isSearchingFlow.value = false
        }
    }

    fun filterBySubfilters(items: List<SearchResultItem>, targetType: MediaType): List<SearchResultItem> {
        val nsfwSafe = items.filterNot { item ->
            item.mediaType == MediaType.FANFICTION &&
                (!fanfictionEnabled || com.omnitrack.app.ui.components.shouldHideFanficItem(item.fanficRating, fanficNsfwMode))
        }
        return when {
            targetType == MediaType.MANGA && selectedMangaSubFilter != "ALL" -> nsfwSafe.filter {
                com.omnitrack.app.ui.screens.home.inferMangaSubtype(it.originCountry, it.nativeTitle) == selectedMangaSubFilter
            }
            targetType == MediaType.NOVEL && selectedNovelSubFilter != "ALL" -> nsfwSafe.filter {
                com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                    it.platform, it.originCountry, it.anilistId, it.malId, it.totalProgress, it.title
                ) == selectedNovelSubFilter
            }
            targetType == MediaType.VISUAL_NOVEL -> nsfwSafe.filter {
                (selectedVnOriginSubFilter == "ALL" || com.omnitrack.app.data.model.inferVnOriginGroup(it.originCountry, it.nativeTitle) == selectedVnOriginSubFilter) &&
                    (selectedVnLengthSubFilter == "ALL" || com.omnitrack.app.data.model.inferVnLengthGroup(it.runtimeMinutes) == selectedVnLengthSubFilter)
            }
            targetType == MediaType.GAME && selectedGamePlatformSubFilter != "ALL" -> nsfwSafe.filter {
                val groups = com.omnitrack.app.data.model.normalizeGamePlatformGroup(it.platform, null)
                groups.isEmpty() || selectedGamePlatformSubFilter in groups
            }
            targetType == MediaType.BOOK -> nsfwSafe.filter { item ->
                val fmtOk = selectedBookFormatSubFilter == "ALL" ||
                    com.omnitrack.app.data.model.normalizeBookFormatGroup(item.bookFormat) == selectedBookFormatSubFilter
                val catOk = selectedBookCategorySubFilter == null ||
                    selectedBookCategorySubFilter == "ALL" ||
                    com.omnitrack.app.data.model.inferBookCategoryGroup(item.title, item.platform, item.synopsis) == selectedBookCategorySubFilter
                fmtOk && catOk
            }
            targetType == MediaType.COMIC -> nsfwSafe.filter { item ->
                val pubOk = selectedComicPublisherSubFilter == "ALL" ||
                    com.omnitrack.app.data.model.normalizeComicPublisherGroup(item.comicPublisher, item.platform) == selectedComicPublisherSubFilter
                val fmtOk = selectedComicFormatSubFilter == "ALL" ||
                    com.omnitrack.app.data.model.normalizeComicFormatGroup(item.comicFormat) == selectedComicFormatSubFilter
                pubOk && fmtOk
            }
            targetType == MediaType.FANFICTION -> nsfwSafe.filter { item ->
                val ratingOk = selectedFanficRatingSubFilter == "ALL" ||
                    com.omnitrack.app.data.model.FanficRating.fromRaw(item.fanficRating).code == selectedFanficRatingSubFilter
                val statusOk = selectedFanficStatusSubFilter == "ALL" ||
                    com.omnitrack.app.data.model.FanficWorkStatus.fromRaw(item.fanficWorkStatus).name == selectedFanficStatusSubFilter
                val xoOk = !selectedFanficCrossoverOnly || item.fanficIsCrossover
                ratingOk && statusOk && xoOk
            }
            else -> nsfwSafe
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // Editorial Header + Back button (when pushed from Details title tap) + Customize button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (onBack != null) 6.dp else 16.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (onBack != null) "Search Discover" else "Discover",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = if (onBack != null) "Cross-source lookup \u00B7 tap Back to return to Details" else "Live search & curated rails across all verticals",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
            }
            if (discoverSettings.showSwipeQueueButton && homeViewModel != null) {
                Row(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { showSwipeQueueSheet = true }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewCarousel,
                        contentDescription = "Swipe Discovery Queue",
                        tint = AccentGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Queue",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
            if (fanfictionEnabled) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentGold.copy(alpha = 0.14f))
                        .clickable { onOpenFanficImport("", 0) }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Import Fanfic URL",
                        tint = AccentGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Import URL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold
                    )
                }
            }
            IconButton(
                onClick = onOpenCustomizer,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Customize Discover",
                    tint = AccentGold,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        // Clean Flat Search Bar (Option A)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (useAiSearch) AccentGold else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            BasicTextField(
                value = searchQuery,
                onValueChange = { queryFlow.value = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(AccentGold),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = if (useAiSearch && discoverSettings.showAiToggle) {
                                "Ask AI or describe (e.g. 'dark fantasy web novel')"
                            } else {
                                "Search anime, comics, web novels, games, books..."
                            },
                            color = TextSecondary.copy(alpha = 0.72f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            )

            if (discoverSettings.showAiToggle) {
                val shortModelLabel = remember(activeGeminiModel) {
                    activeGeminiModel
                        .removePrefix("gemini-")
                        .replace("-preview", "")
                        .replace("flash-lite", "FL")
                        .replace("flash", "F")
                        .take(12)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (useAiSearch) AccentGold.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
                        .combinedClickable(
                            onClick = {
                                val nextAi = !useAiSearch
                                useAiFlow.value = nextAi
                                scope.launch { prefManager.updateAiSemanticSearchEnabled(nextAi) }
                                if (nextAi && geminiKey.isBlank() && openRouterKey.isBlank()) {
                                    bannerFlow.value = "AI Smart Mode (Local) \u2014 Add a Gemini or OpenRouter key in Settings for full AI"
                                } else if (!nextAi) {
                                    bannerFlow.value = null
                                }
                            },
                            onLongClick = { showModelPickerSheet = true }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Mode",
                        tint = if (useAiSearch) AccentGold else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (useAiSearch) "AI \u00B7 $shortModelLabel" else "AI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (useAiSearch) AccentGold else TextSecondary
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Select AI Model",
                        tint = if (useAiSearch) AccentGold else TextSecondary,
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .clickable { showModelPickerSheet = true }
                    )
                }
            }

            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { queryFlow.value = "" }
                )
            }
        }

        // Option A Underline Category Tabs (respects disabledCategories)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            lazyRowItemsIndexed(visibleCategories, key = { _, t -> "dcat_${t.name}" }) { _, type ->
                val isSelected = selectedFilter == type
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { filterFlow.value = type },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (type == MediaType.ALL) "All Verticals" else type.displayName,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) TextPrimary else TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (isSelected) AccentGold else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

        // Optional Sub-Filter Bars (respects showSubFilters)
        if (discoverSettings.showSubFilters) {
            if (selectedFilter == MediaType.MANGA) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("ALL" to "All", "MANGA" to "Manga", "MANHWA" to "Manhwa", "MANHUA" to "Manhua").forEach { (key, label) ->
                        val isSubSelected = selectedMangaSubFilter == key
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .clickable { selectedMangaSubFilter = key },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSubSelected) AccentGold else TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.5.dp)
                                    .background(if (isSubSelected) AccentGold else Color.Transparent)
                            )
                        }
                    }
                }
            }

            if (selectedFilter == MediaType.NOVEL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("ALL" to "All", "LIGHT_NOVEL" to "Light Novel", "WEB_NOVEL" to "Web Novel").forEach { (key, label) ->
                        val isSubSelected = selectedNovelSubFilter == key
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .clickable { selectedNovelSubFilter = key },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSubSelected) AccentGold else TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.5.dp)
                                    .background(if (isSubSelected) AccentGold else Color.Transparent)
                            )
                        }
                    }
                }
            }

            if (selectedFilter == MediaType.VISUAL_NOVEL) {
                com.omnitrack.app.ui.screens.home.VnUnderlineFilterBar(
                    selectedOrigin = selectedVnOriginSubFilter,
                    selectedLength = selectedVnLengthSubFilter,
                    onOriginSelected = { selectedVnOriginSubFilter = it },
                    onLengthSelected = { selectedVnLengthSubFilter = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            if (selectedFilter == MediaType.GAME) {
                com.omnitrack.app.ui.screens.home.GameUnderlineFilterBar(
                    selectedPlatform = selectedGamePlatformSubFilter,
                    selectedCompletion = null,
                    onPlatformSelected = { selectedGamePlatformSubFilter = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            if (selectedFilter == MediaType.BOOK) {
                com.omnitrack.app.ui.screens.home.BookUnderlineFilterBar(
                    selectedFormat = selectedBookFormatSubFilter,
                    selectedCategory = selectedBookCategorySubFilter,
                    onFormatSelected = { selectedBookFormatSubFilter = it },
                    onCategorySelected = { selectedBookCategorySubFilter = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            if (selectedFilter == MediaType.COMIC) {
                com.omnitrack.app.ui.screens.home.ComicUnderlineFilterBar(
                    selectedPublisher = selectedComicPublisherSubFilter,
                    selectedFormat = selectedComicFormatSubFilter,
                    onPublisherSelected = { selectedComicPublisherSubFilter = it },
                    onFormatSelected = { selectedComicFormatSubFilter = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            if (selectedFilter == MediaType.FANFICTION) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AccentGold.copy(alpha = 0.18f))
                            .clickable { onOpenFanficImport("", 0) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = AccentGold, modifier = Modifier.size(13.dp))
                        Text("Import URL / Sites", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentGold)
                    }
                    listOf("ALL" to "Any Rating", "G" to "G", "T" to "T", "M" to "M", "E" to "E").forEach { (k, lbl) ->
                        val sel = selectedFanficRatingSubFilter == k
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (sel) AccentGold.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                                .clickable { selectedFanficRatingSubFilter = k }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(lbl, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) AccentGold else TextSecondary)
                        }
                    }
                    listOf("ALL" to "Any Status", "COMPLETE" to "Complete", "ONGOING" to "Ongoing").forEach { (k, lbl) ->
                        val sel = selectedFanficStatusSubFilter == k
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (sel) AccentGold.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                                .clickable { selectedFanficStatusSubFilter = k }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(lbl, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) AccentGold else TextSecondary)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selectedFanficCrossoverOnly) AccentGold.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                            .clickable { selectedFanficCrossoverOnly = !selectedFanficCrossoverOnly }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text("Crossovers", fontSize = 11.sp, fontWeight = if (selectedFanficCrossoverOnly) FontWeight.Bold else FontWeight.Medium, color = if (selectedFanficCrossoverOnly) AccentGold else TextSecondary)
                    }
                }
            }
        }

        // AI Resolution Banner
        if (aiResolutionBanner != null && discoverSettings.showAiToggle) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentGold.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = aiResolutionBanner!!,
                    fontSize = 11.5.sp,
                    color = AccentGold,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val visibleResults = remember(
            searchResults,
            selectedFilter,
            selectedMangaSubFilter,
            selectedNovelSubFilter,
            selectedVnOriginSubFilter,
            selectedVnLengthSubFilter,
            selectedGamePlatformSubFilter,
            selectedBookFormatSubFilter,
            selectedBookCategorySubFilter,
            selectedComicPublisherSubFilter,
            selectedComicFormatSubFilter,
            selectedFanficRatingSubFilter,
            selectedFanficStatusSubFilter,
            selectedFanficCrossoverOnly,
            fanficNsfwMode
        ) {
            val base = if (selectedFilter == MediaType.ALL) searchResults else searchResults.filter { it.mediaType == selectedFilter }
            filterBySubfilters(base, selectedFilter)
        }

        val detectedFanficUrl = remember(searchQuery, fanfictionEnabled) {
            val extracted = com.omnitrack.app.data.model.extractFirstFanficUrl(searchQuery) ?: return@remember null
            val site = com.omnitrack.app.data.model.FanficSite.fromUrl(extracted)
            if (site == com.omnitrack.app.data.model.FanficSite.WATTPAD || fanfictionEnabled) extracted else null
        }
        if (detectedFanficUrl != null) {
            val detectedSite = com.omnitrack.app.data.model.FanficSite.fromUrl(detectedFanficUrl)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentGold.copy(alpha = 0.16f))
                    .clickable { onOpenFanficImport(detectedFanficUrl, 0) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${detectedSite.displayName} link detected",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap to fetch metadata, chapters & track in OmniTrack",
                        color = AccentGold,
                        fontSize = 11.5.sp
                    )
                }
                Text("Import", color = AccentGold, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        if (searchQuery.trim().length >= 2) {
            when {
                isSearching && visibleResults.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentGold, strokeWidth = 2.dp)
                    }
                }
                visibleResults.isNotEmpty() -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Results (${visibleResults.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        if (isSearching) {
                            CircularProgressIndicator(
                                color = AccentGold,
                                strokeWidth = 1.8.dp,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isPhone = maxWidth < 600.dp
                        val discoverColumns = if (posterStyle.isLandscape) {
                            if (isPhone) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 340.dp)
                        } else {
                            GridCells.Adaptive(minSize = posterStyle.widthDp.dp)
                        }
                        LazyVerticalGrid(
                            state = gridState,
                            columns = discoverColumns,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 110.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                visibleResults,
                                key = {
                                    "${it.sourceProvider}_${it.mediaType}_${it.platform ?: ""}_${it.title}_${it.anilistId ?: it.bangumiId ?: it.malId ?: it.tmdbId ?: it.igdbId ?: it.steamAppId ?: it.vndbId ?: it.openLibraryId ?: it.hardcoverId ?: it.comicVineId ?: it.metronId ?: 0L}"
                                },
                                contentType = { "discover_grid_card" }
                            ) { item ->
                                val itemNovelSub = if (item.mediaType == MediaType.NOVEL) {
                                    com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                                        item.platform, item.originCountry, item.anilistId, item.malId, item.totalProgress, item.title
                                    )
                                } else null

                                val existingInLibrary = allMedia.firstOrNull { summary ->
                                    summary.mediaType == item.mediaType && (
                                        (item.fanficWorkId != null && summary.fanficWorkId == item.fanficWorkId) ||
                                            (item.fanficSourceUrl != null && summary.fanficSourceUrl.equals(item.fanficSourceUrl, ignoreCase = true)) ||
                                            (item.comicVineId != null && summary.comicVineId == item.comicVineId) ||
                                            (item.metronId != null && summary.metronId == item.metronId) ||
                                            (item.openLibraryId != null && summary.openLibraryId == item.openLibraryId) ||
                                            summary.title.equals(item.title, ignoreCase = true) ||
                                            (item.nativeTitle != null && summary.nativeTitle.equals(item.nativeTitle, ignoreCase = true))
                                        ) && (
                                        itemNovelSub == null ||
                                            com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                                                summary.platform, summary.originCountry, summary.anilistId, summary.malId, summary.totalProgress, summary.title
                                            ) == itemNovelSub
                                        )
                                }

                                val summary = existingInLibrary ?: MediaCardSummary(
                                    id = 0L,
                                    title = item.title,
                                    nativeTitle = item.nativeTitle,
                                    coverUrl = item.coverUrl,
                                    bannerUrl = item.bannerUrl,
                                    mediaType = item.mediaType,
                                    status = TrackingStatus.PLAN_TO_TRACK,
                                    currentProgress = 0.0,
                                    totalProgress = item.totalProgress?.toDouble(),
                                    userRating = item.communityScore,
                                    customCoverPath = null,
                                    customBannerPath = null,
                                    artworkRole = "DEFAULT",
                                    originCountry = item.originCountry,
                                    platform = item.platform,
                                    runtimeMinutes = item.runtimeMinutes,
                                    openLibraryId = item.openLibraryId,
                                    bookAuthor = item.bookAuthor,
                                    bookFormat = item.bookFormat,
                                    comicVineId = item.comicVineId,
                                    metronId = item.metronId,
                                    comicPublisher = item.comicPublisher,
                                    comicFormat = item.comicFormat,
                                    fanficSourceSite = item.fanficSourceSite,
                                    fanficSourceUrl = item.fanficSourceUrl,
                                    fanficWorkId = item.fanficWorkId,
                                    fanficPrimaryFandom = item.fanficPrimaryFandom,
                                    fanficWordCount = item.fanficWordCount,
                                    fanficRating = item.fanficRating,
                                    fanficWorkStatus = item.fanficWorkStatus,
                                    fanficIsCrossover = item.fanficIsCrossover,
                                    fanficTagsSearch = item.fanficTagsSearch,
                                    lastUpdated = System.currentTimeMillis()
                                )

                                val cardBadgeText = when {
                                    existingInLibrary != null -> "IN LIBRARY"
                                    itemNovelSub != null -> {
                                        val tag = if (itemNovelSub == "LIGHT_NOVEL") "LN" else "WN"
                                        "$tag \u00B7 ${item.sourceProvider}"
                                    }
                                    else -> item.sourceProvider
                                }

                                MediaCard(
                                    item = summary,
                                    cornerRadiusDp = posterStyle.cornerRadiusDp,
                                    isLandscape = posterStyle.isLandscape,
                                    hideLabels = posterStyle.hideLabels,
                                    cardDepthEnabled = posterStyle.cardDepthEnabled && posterStyle.cardDepthPostersEnabled,
                                    cardDepthEdgeStrength = posterStyle.cardDepthEdgeStrength.toFloat(),
                                    cardDepthSheenStrength = posterStyle.cardDepthSheenStrength.toFloat(),
                                    cardDepthEdgeCoverage = posterStyle.cardDepthEdgeCoverage.toFloat(),
                                    badgeText = cardBadgeText,
                                    badgeColor = AccentGold,
                                    isDiscoverCard = true,
                                    showLandscapeClearlogo = posterStyle.alwaysShowLandscapeClearlogo,
                                    onCardClick = {
                                        if (existingInLibrary != null) {
                                            onItemClick(existingInLibrary.id)
                                        } else {
                                            EphemeralPreviewHolder.previewItem = item.toMediaItemEntity()
                                            onItemClick(-1L)
                                        }
                                    },
                                    onQuickProgress = {
                                        if (existingInLibrary != null) {
                                            onItemClick(existingInLibrary.id)
                                        } else {
                                            onAddMedia(item)
                                        }
                                    }
                                )
                            }
                            if (discoverSettings.showCustomEntryCard) {
                                item(
                                    key = "discover_custom_entry_footer",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    DiscoverCustomEntryCard(
                                        query = searchQuery.trim(),
                                        suggestedType = if (selectedFilter == MediaType.ALL) MediaType.NOVEL else selectedFilter,
                                        onClick = {
                                            onCreateCustomEntry(
                                                searchQuery.trim(),
                                                if (selectedFilter == MediaType.ALL) MediaType.NOVEL else selectedFilter
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "No media found matching \"${searchQuery.trim()}\"",
                                fontSize = 13.5.sp,
                                color = TextPrimary
                            )
                            if (discoverSettings.showCustomEntryCard) {
                                DiscoverCustomEntryCard(
                                    query = searchQuery.trim(),
                                    suggestedType = if (selectedFilter == MediaType.ALL) MediaType.NOVEL else selectedFilter,
                                    onClick = {
                                        onCreateCustomEntry(
                                            searchQuery.trim(),
                                            if (selectedFilter == MediaType.ALL) MediaType.NOVEL else selectedFilter
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val browseListState = rememberLazyListState()
            LaunchedEffect(selectedFilter, discoverSettings.layoutStyle) {
                browseListState.scrollToItem(0)
                gridState.scrollToItem(0)
            }

            // Polished Browse Mode with Layout A / B / C, Infinite Shelves & Customize Filtering
            val activeDefs: List<DiscoverShelfDef> = remember(
                selectedFilter,
                discoverSettings.disabledCategories,
                discoverSettings.disabledShelves,
                selectedMangaSubFilter,
                selectedNovelSubFilter
            ) {
                DiscoverShelfCatalog.shelvesForCategory(selectedFilter).filter { def ->
                    if (def.id in discoverSettings.disabledShelves) return@filter false
                    if (selectedFilter == MediaType.ALL) {
                        val mappedCat = when (def.id) {
                            "ALL:anime_trending" -> MediaType.ANIME
                            "ALL:manga_trending" -> MediaType.MANGA
                            "ALL:comic_trending" -> MediaType.COMIC
                            "ALL:novel_webnovels" -> MediaType.NOVEL
                            "ALL:show_trending" -> MediaType.SHOW
                            "ALL:movie_trending" -> MediaType.MOVIE
                            "ALL:book_trending" -> MediaType.BOOK
                            "ALL:game_trending" -> MediaType.GAME
                            "ALL:vn_popular" -> MediaType.VISUAL_NOVEL
                            "ALL:fanfic_trending" -> MediaType.FANFICTION
                            else -> null
                        }
                        if (mappedCat == MediaType.FANFICTION && !fanfictionEnabled) {
                            return@filter false
                        }
                        if (mappedCat != null && mappedCat.name in discoverSettings.disabledCategories) {
                            return@filter false
                        }
                    }
                    if (selectedFilter == MediaType.MANGA && selectedMangaSubFilter == "MANHWA" && def.id == "MANGA:top_rated") {
                        return@filter false
                    }
                    if (selectedFilter == MediaType.NOVEL) {
                        when (selectedNovelSubFilter) {
                            "WEB_NOVEL" -> if (def.id !in setOf("NOVEL:top_webnovels", "NOVEL:progression_webnovels")) return@filter false
                            "LIGHT_NOVEL" -> if (def.id !in setOf("NOVEL:trending_ln", "NOVEL:top_rated_ln")) return@filter false
                        }
                    }
                    true
                }
            }

            val heroPool = remember(activeDefs, discoverShelves, allMedia) {
                val raw = activeDefs.flatMap { def ->
                    discoverShelves[def.id]?.items?.take(4).orEmpty()
                }.distinctBy { "${it.mediaType}_${it.title.lowercase()}" }
                val withBanner = raw.filter { item ->
                    findLibraryMatch(allMedia, item)?.let { ArtworkModelResolver.preferredBanner(it) != null }
                        ?: (!item.bannerUrl.isNullOrBlank() && item.bannerUrl != item.coverUrl)
                }
                val withoutBanner = raw.filterNot { item -> item in withBanner }
                (withBanner + withoutBanner).take(6)
            }
            val isAnyActiveShelfLoading = remember(activeDefs, discoverShelves) {
                activeDefs.any { discoverShelves[it.id] == null || discoverShelves[it.id]?.isLoading == true }
            }
            LaunchedEffect(selectedFilter, heroPool.isNotEmpty()) {
                if (heroPool.isNotEmpty() && browseListState.firstVisibleItemIndex <= 1) {
                    browseListState.scrollToItem(0)
                }
            }

            val categorySpotlight = remember(
                allMedia,
                selectedFilter,
                selectedMangaSubFilter,
                selectedNovelSubFilter,
                selectedVnOriginSubFilter,
                selectedVnLengthSubFilter
            ) {
                val byType = if (selectedFilter == MediaType.ALL) {
                    allMedia
                } else {
                    allMedia.filter { it.mediaType == selectedFilter }
                }
                when {
                    selectedFilter == MediaType.MANGA && selectedMangaSubFilter != "ALL" -> byType.filter {
                        com.omnitrack.app.ui.screens.home.inferMangaSubtype(it.originCountry, it.nativeTitle) == selectedMangaSubFilter
                    }
                    selectedFilter == MediaType.NOVEL && selectedNovelSubFilter != "ALL" -> byType.filter {
                        com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                            it.platform, it.originCountry, it.anilistId, it.malId, it.totalProgress, it.title, it.fanficSourceSite
                        ) == selectedNovelSubFilter
                    }
                    selectedFilter == MediaType.VISUAL_NOVEL -> byType.filter {
                        (selectedVnOriginSubFilter == "ALL" || com.omnitrack.app.data.model.inferVnOriginGroup(it.originCountry, it.nativeTitle) == selectedVnOriginSubFilter) &&
                            (selectedVnLengthSubFilter == "ALL" || com.omnitrack.app.data.model.inferVnLengthGroup(it.runtimeMinutes) == selectedVnLengthSubFilter)
                    }
                    else -> byType
                }.take(16)
            }

            val quickChips = when (selectedFilter) {
                MediaType.ANIME -> listOf("Frieren", "Dandadan", "Bleach", "Solo Leveling", "Steins;Gate")
                MediaType.MANGA -> listOf("Berserk", "Vagabond", "One Piece", "Omniscient Reader", "Kagurabachi")
                MediaType.COMIC -> listOf("Batman", "Invincible", "Saga", "Ultimate Spider-Man", "Hellboy")
                MediaType.NOVEL -> listOf("The Hundred Reigns", "Shadow Slave", "Lord of the Mysteries", "The Perfect Run", "Mother of Learning", "86")
                MediaType.BOOK -> listOf("Dune", "The Iliad", "Meditations", "Project Hail Mary", "The Hobbit")
                MediaType.SHOW -> listOf("Severance", "Andor", "Breaking Bad", "Arcane", "Shogun")
                MediaType.MOVIE -> listOf("Dune", "Oppenheimer", "Interstellar", "Spider-Verse", "Parasite")
                MediaType.GAME -> listOf("Elden Ring", "Baldur's Gate 3", "Hades II", "Hollow Knight", "Disco Elysium")
                MediaType.VISUAL_NOVEL -> listOf("Steins;Gate", "Clannad", "The House in Fata Morgana", "Umineko", "Muv-Luv")
                MediaType.FANFICTION -> listOf("Worm", "Harry Potter", "Naruto", "My Hero Academia", "One Piece", "Crossover")
                else -> listOf("The Hundred Reigns", "Audiobook", "Invincible", "Dune", "Frieren", "Elden Ring")
            }

            LazyColumn(
                state = browseListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 114.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Top Spotlight or Hero Carousel based on Layout A / B / C setting
                if (discoverSettings.showSpotlightBanner &&
                    discoverSettings.layoutStyle != DiscoverLayoutStyle.MINIMAL_RAILS &&
                    (heroPool.isNotEmpty() || isAnyActiveShelfLoading)
                ) {
                    item(key = "discover_top_showcase") {
                        if (heroPool.isEmpty()) {
                            DiscoverHeroSkeletonBanner()
                        } else {
                            when (discoverSettings.layoutStyle) {
                                DiscoverLayoutStyle.EDITORIAL_SPOTLIGHT -> {
                                    val lead = heroPool.first()
                                    val match = findLibraryMatch(allMedia, lead)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                    ) {
                                        DiscoverSpotlightBanner(
                                            item = lead,
                                            libraryMatch = match,
                                            dimBanner = discoverSettings.dimHeroBanner,
                                            bannerBlurDp = discoverSettings.resolveBannerBlurDp(lead.mediaType),
                                            onDetails = {
                                                if (match != null) {
                                                    onItemClick(match.id)
                                                } else {
                                                    EphemeralPreviewHolder.previewItem = lead.toMediaItemEntity()
                                                    onItemClick(-1L)
                                                }
                                            },
                                            onAdd = { onAddMedia(lead) }
                                        )
                                    }
                                }
                                DiscoverLayoutStyle.HERO_CAROUSEL -> {
                                    DiscoverHeroCarousel(
                                        items = heroPool,
                                        allMedia = allMedia,
                                        categoryKey = "${selectedFilter.name}_${selectedMangaSubFilter}_${selectedNovelSubFilter}",
                                        dimBanner = discoverSettings.dimHeroBanner,
                                        resolveBlurDp = { type -> discoverSettings.resolveBannerBlurDp(type) },
                                        onItemClick = onItemClick,
                                        onAddMedia = onAddMedia
                                    )
                                }
                                DiscoverLayoutStyle.MINIMAL_RAILS -> Unit
                            }
                        }
                    }
                }

                // Quick-Search Chips (Option A flat 6dp)
                if (discoverSettings.showQuickSearchChips) {
                    item(key = "discover_quick_chips") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            lazyRowItemsIndexed(quickChips, key = { idx, ch -> "qchip_${idx}_$ch" }) { _, chip ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .clickable { queryFlow.value = chip }
                                        .padding(horizontal = 11.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = chip,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Daily Critics' Pick editorial card
                if (discoverSettings.showDailyCriticsPick && dailyCriticsPick != null) {
                    item(key = "discover_daily_critics_pick") {
                        val pick = dailyCriticsPick!!
                        val libMatch = findLibraryMatch(allMedia, pick)
                        DailyCriticsPickCard(
                            item = pick,
                            libraryMatch = libMatch,
                            onOpenDetails = {
                                if (libMatch != null) {
                                    onItemClick(libMatch.id)
                                } else {
                                    EphemeralPreviewHolder.previewItem = pick.toMediaItemEntity()
                                    onItemClick(-1L)
                                }
                            },
                            onAddToLibrary = { onAddMedia(pick) }
                        )
                    }
                }

                // Browse by Streaming Service logo rail + live catalog shelf
                val showStreamingBar = discoverSettings.showStreamingServices &&
                    (selectedFilter == MediaType.ALL || selectedFilter == MediaType.MOVIE || selectedFilter == MediaType.SHOW || selectedFilter == MediaType.ANIME)
                if (showStreamingBar && (streamingProviders.isNotEmpty() || streamingServiceTitles.isNotEmpty())) {
                    item(key = "discover_streaming_services") {
                        StreamingServiceDiscoverSection(
                            providers = streamingProviders,
                            selectedProviderId = selectedStreamingProviderId,
                            regionPref = watchRegionPref,
                            titles = streamingServiceTitles,
                            isLoadingTitles = isLoadingStreamingTitles,
                            allMedia = allMedia,
                            onSelectProvider = { provId ->
                                homeViewModel?.selectStreamingProvider(provId, watchRegionPref, selectedFilter, tmdbKey)
                            },
                            onSelectRegionPref = { chosen ->
                                scope.launch { prefManager.setWatchProviderRegion(chosen) }
                            },
                            onItemClick = onItemClick
                        )
                    }
                }

                // Top 10 Today oversized editorial numbered row
                if (discoverSettings.showTop10Today && top10TodayItems.isNotEmpty()) {
                    item(key = "discover_top_10_today") {
                        Top10TodayDiscoverSection(
                            items = top10TodayItems,
                            allMedia = allMedia,
                            onItemClick = onItemClick
                        )
                    }
                }

                // Taste-Matched "Because You Liked X"
                if (discoverSettings.showTasteMatched && tasteMatchedSeed != null && tasteMatchedItems.isNotEmpty()) {
                    item(key = "discover_taste_matched") {
                        TasteMatchedDiscoverSection(
                            seed = tasteMatchedSeed,
                            items = tasteMatchedItems,
                            allMedia = allMedia,
                            onItemClick = onItemClick
                        )
                    }
                }

                // Curated Filter Rows (Under 90 Min, Hidden Gems, Limited Series, By Decade)
                val showCurated = discoverSettings.showCuratedRows &&
                    (selectedFilter == MediaType.ALL || selectedFilter == MediaType.MOVIE || selectedFilter == MediaType.SHOW)
                if (showCurated) {
                    item(key = "discover_curated_filter_rows") {
                        CuratedFilterDiscoverSection(
                            selectedRowKey = selectedCuratedRowKey,
                            items = curatedRowItems,
                            isLoading = isLoadingCuratedRow,
                            allMedia = allMedia,
                            onSelectRow = { rowKey ->
                                homeViewModel?.selectCuratedRow(rowKey, tmdbKey)
                            },
                            onItemClick = onItemClick
                        )
                    }
                }

                if (activeDefs.isEmpty()) {
                    item(key = "discover_all_disabled") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "All shelves are hidden in this category.",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Customize Discover",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentGold.copy(alpha = 0.14f))
                                    .clickable(onClick = onOpenCustomizer)
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // Curated & Infinitely Paginated Horizontal Poster Rails
                activeDefs.forEach { def ->
                    val state = discoverShelves[def.id]
                    val rawItems = state?.items.orEmpty()
                    val filteredItems = filterBySubfilters(rawItems, if (def.category == MediaType.ALL) selectedFilter else def.category)
                    val isAudiobooks = def.id == "BOOK:audiobooks"

                    if (state == null || (state.isLoading && rawItems.isEmpty())) {
                        item(key = "skel_${def.id}") {
                            DiscoverShelfSkeletonRow(
                                title = def.title,
                                isSquare = isAudiobooks,
                                onHideShelf = {
                                    scope.launch { prefManager.setDiscoverShelfEnabled(def.id, false) }
                                }
                            )
                        }
                    } else if (filteredItems.isNotEmpty()) {
                        item(key = "shelf_${def.id}") {
                            val rowState = rememberLazyListState()
                            LaunchedEffect(rowState, filteredItems.size, state.hasMore, state.isLoading) {
                                snapshotFlow {
                                    rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                                }.collect { lastIdx ->
                                    if (state.hasMore && !state.isLoading && lastIdx >= (filteredItems.lastIndex - 3).coerceAtLeast(0)) {
                                        homeViewModel?.loadMoreDiscoverShelf(def.id, tmdbKey)
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = def.title,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = "Hide Shelf",
                                        tint = TextSecondary.copy(alpha = 0.55f),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                scope.launch { prefManager.setDiscoverShelfEnabled(def.id, false) }
                                            }
                                    )
                                }

                                LazyRow(
                                    state = rowState,
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    lazyRowItemsIndexed(
                                        items = filteredItems,
                                        key = { idx, item ->
                                            "${def.id}_${idx}_${item.mediaType}_${item.sourceProvider}_${item.anilistId ?: item.vndbId ?: item.tmdbId ?: item.steamAppId ?: item.openLibraryId ?: item.comicVineId ?: item.metronId ?: item.title}"
                                        }
                                    ) { _, item ->
                                        val inLib = findLibraryMatch(allMedia, item)
                                        NuvioRailPosterCard(
                                            title = item.title,
                                            coverUrl = inLib?.customCoverPath ?: inLib?.coverUrl ?: item.coverUrl,
                                            subtitle = item.bookAuthor ?: item.comicPublisher ?: item.releaseYear ?: item.mediaType.displayName,
                                            rating = item.communityScore,
                                            inLibraryBadge = inLib != null,
                                            isFanfic = item.mediaType == MediaType.FANFICTION,
                                            fanficAuthor = item.bookAuthor,
                                            fanficFandom = item.fanficPrimaryFandom,
                                            fanficSiteCode = item.fanficSourceSite,
                                            fanficRating = item.fanficRating,
                                            fanficWordCount = item.fanficWordCount,
                                            fanficIsCrossover = item.fanficIsCrossover,
                                            onClick = {
                                                if (inLib != null) {
                                                    onItemClick(inLib.id)
                                                } else {
                                                    EphemeralPreviewHolder.previewItem = item.toMediaItemEntity()
                                                    onItemClick(-1L)
                                                }
                                            },
                                            cardWidth = 114.dp,
                                            cardHeight = if (isAudiobooks) 114.dp else androidx.compose.ui.unit.Dp.Unspecified
                                        )
                                    }

                                    if (state.isLoading) {
                                        item(key = "tail_load_${def.id}") {
                                            Box(
                                                modifier = Modifier
                                                    .width(48.dp)
                                                    .height(if (isAudiobooks) 114.dp else 171.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = AccentGold,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // "In Your Library" Rail (respects showInYourLibraryShelf)
                if (discoverSettings.showInYourLibraryShelf && categorySpotlight.isNotEmpty()) {
                    item(key = "discover_lib_spotlight") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (selectedFilter == MediaType.ALL) "In Your Library" else "In Your ${selectedFilter.displayName} Library",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                lazyRowItemsIndexed(categorySpotlight, key = { idx, s -> "lib_spot_${idx}_${s.id}" }) { _, summary ->
                                    NuvioRailPosterCard(
                                        title = summary.title,
                                        coverUrl = summary.customCoverPath ?: summary.coverUrl,
                                        subtitle = summary.bookAuthor ?: summary.comicPublisher ?: summary.mediaType.displayName,
                                        rating = summary.userRating ?: summary.communityScore,
                                        isFanfic = summary.mediaType == MediaType.FANFICTION,
                                        fanficAuthor = summary.bookAuthor,
                                        fanficFandom = summary.fanficPrimaryFandom,
                                        fanficSiteCode = summary.fanficSourceSite,
                                        fanficRating = summary.fanficRating,
                                        fanficWordCount = summary.fanficWordCount,
                                        fanficIsCrossover = summary.fanficIsCrossover,
                                        onClick = {
                                            ActiveSiblingHolder.setSiblings(categorySpotlight.map { it.id })
                                            onItemClick(summary.id)
                                        },
                                        cardWidth = 114.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showModelPickerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showModelPickerSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = AmoledBlack
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Search AI Model",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    GeminiModelSelectorSection(
                        geminiModel = geminiModel,
                        customGeminiModel = customGeminiModel,
                        useCustomGeminiModel = useCustomGeminiModel,
                        onSelectPreset = { modelId ->
                            scope.launch {
                                prefManager.updateUseCustomGeminiModel(false)
                                prefManager.updateGeminiModel(modelId)
                            }
                        },
                        onSelectCustomMode = {
                            scope.launch { prefManager.updateUseCustomGeminiModel(true) }
                        },
                        onCustomModelChange = { valTrim ->
                            scope.launch { prefManager.updateCustomGeminiModel(valTrim) }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        if (showSwipeQueueSheet && homeViewModel != null) {
            val queue = remember(
                allMedia,
                top10TodayItems,
                tasteMatchedItems,
                curatedRowItems,
                streamingServiceTitles,
                discoverShelves,
                skippedSwipeKeys
            ) {
                homeViewModel.buildSwipeQueue(allMedia)
            }
            SwipeDiscoveryQueueSheet(
                queueItems = queue,
                onAddPlanToWatch = { picked ->
                    onAddMedia(picked)
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
                    val inLib = findLibraryMatch(allMedia, picked)
                    if (inLib != null) {
                        onItemClick(inLib.id)
                    } else {
                        EphemeralPreviewHolder.previewItem = picked.toMediaItemEntity()
                        onItemClick(-1L)
                    }
                },
                onDismiss = { showSwipeQueueSheet = false }
            )
        }
    }
}

private fun findLibraryMatch(allMedia: List<MediaCardSummary>, item: SearchResultItem): MediaCardSummary? =
    ArtworkModelResolver.findLibraryMatch(
        library = allMedia,
        mediaType = item.mediaType,
        anilistId = item.anilistId,
        malId = item.malId,
        tmdbId = item.tmdbId,
        imdbId = item.imdbId,
        steamAppId = item.steamAppId,
        comicVineId = item.comicVineId,
        metronId = item.metronId,
        openLibraryId = item.openLibraryId,
        fanficWorkId = item.fanficWorkId,
        fanficSourceUrl = item.fanficSourceUrl,
        title = item.title,
        nativeTitle = item.nativeTitle
    )

@Composable
private fun DiscoverHeroSkeletonBanner() {
    val pulse = rememberInfiniteTransition(label = "discover_hero_skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero_skel_alpha"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(224.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = alpha))
    )
}

@Composable
private fun DiscoverSpotlightBanner(
    item: SearchResultItem,
    libraryMatch: MediaCardSummary? = null,
    dimBanner: Boolean = true,
    bannerBlurDp: Int = 0,
    onDetails: () -> Unit,
    onAdd: () -> Unit
) {
    val bannerDisabled = libraryMatch != null && ArtworkModelResolver.isBannerDisabled(libraryMatch)
    val coverModel = remember(libraryMatch?.customCoverPath, libraryMatch?.coverUrl, item.coverUrl) {
        ArtworkModelResolver.toCoilModel(libraryMatch?.let { ArtworkModelResolver.preferredCover(it) } ?: item.coverUrl)
    }
    val bannerModel = remember(
        libraryMatch?.customBannerPath,
        libraryMatch?.bannerUrl,
        libraryMatch?.artworkRole,
        item.bannerUrl,
        item.coverUrl,
        bannerDisabled
    ) {
        if (bannerDisabled) null
        else ArtworkModelResolver.toCoilModel(
            libraryMatch?.let { ArtworkModelResolver.preferredBanner(it) }
                ?: item.bannerUrl?.takeIf { it.isNotBlank() && it != item.coverUrl }
        )
    }
    val focalX = libraryMatch?.bannerFocalX ?: 0f
    val focalY = libraryMatch?.bannerFocalY ?: 0f
    val zoom = (libraryMatch?.bannerZoom ?: 1f).coerceIn(1f, 2.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(224.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0E0E12))
            .clickable(onClick = onDetails)
    ) {
        if (bannerModel != null) {
            AsyncImage(
                model = bannerModel,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(focalX.coerceIn(-1f, 1f), focalY.coerceIn(-1f, 1f)),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fx = focalX.coerceIn(-1f, 1f)
                        val fy = focalY.coerceIn(-1f, 1f)
                        val effZoom = zoom.coerceIn(1f, 2.5f)
                        scaleX = effZoom
                        scaleY = effZoom
                        translationX = -fx * size.width * (effZoom - 1f) * 0.5f
                        translationY = -fy * size.height * (effZoom - 1f) * 0.5f
                    }
                    .then(if (bannerBlurDp > 0) Modifier.blur(bannerBlurDp.dp) else Modifier)
            )
        } else if (!bannerDisabled && coverModel != null) {
            val fallbackBlur = maxOf(bannerBlurDp, if (dimBanner) 18 else 10)
            AsyncImage(
                model = coverModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(fallbackBlur.dp)
                    .alpha(if (dimBanner) 0.45f else 0.84f)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AccentGold.copy(alpha = 0.22f), Color(0xFF0E0E12))
                        )
                    )
            )
        }
        if (dimBanner) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.42f to AmoledBlack.copy(alpha = 0.35f),
                            0.78f to AmoledBlack.copy(alpha = 0.82f),
                            1.0f to AmoledBlack.copy(alpha = 0.96f)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.0f to AmoledBlack.copy(alpha = 0.62f),
                            0.48f to AmoledBlack.copy(alpha = 0.18f),
                            1.0f to Color.Transparent
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.60f to Color.Transparent,
                            0.86f to AmoledBlack.copy(alpha = 0.46f),
                            1.0f to AmoledBlack.copy(alpha = 0.76f)
                        )
                    )
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                if (coverModel != null) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (item.mediaType == MediaType.FANFICTION) {
                    FanficTypographicCover(
                        title = item.title,
                        author = libraryMatch?.bookAuthor ?: item.bookAuthor,
                        primaryFandom = libraryMatch?.fanficPrimaryFandom ?: item.fanficPrimaryFandom,
                        sourceSite = libraryMatch?.fanficSourceSite ?: item.fanficSourceSite,
                        ratingCode = libraryMatch?.fanficRating ?: item.fanficRating,
                        wordCount = libraryMatch?.fanficWordCount ?: item.fanficWordCount,
                        isCrossover = libraryMatch?.fanficIsCrossover ?: item.fanficIsCrossover,
                        compact = true,
                        isBlurred = false
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "TRENDING SPOTLIGHT \u00B7 ${item.mediaType.displayName.uppercase()}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold,
                    letterSpacing = 0.9.sp
                )
                Text(
                    text = item.title,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    item.releaseYear,
                    item.communityScore?.takeIf { it > 0f }?.let { "\u2605 ${formatSmartRating(it.toDouble())}" },
                    item.sourceProvider
                ).joinToString(" \u00B7 ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (libraryMatch != null) "In Library" else "+ Library",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentGold.copy(alpha = 0.18f))
                            .clickable(onClick = if (libraryMatch != null) onDetails else onAdd)
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverHeroCarousel(
    items: List<SearchResultItem>,
    allMedia: List<MediaCardSummary>,
    categoryKey: String = "",
    dimBanner: Boolean,
    resolveBlurDp: (MediaType?) -> Int = { 0 },
    onItemClick: (Long) -> Unit,
    onAddMedia: (SearchResultItem) -> Unit
) {
    if (items.isEmpty()) return
    val isLooping = items.size > 1
    val virtualCount = if (isLooping) 100_000 else items.size
    val startIndex = remember(categoryKey, items.size) {
        if (isLooping) 50_000 - (50_000 % items.size) else 0
    }
    val pagerState = androidx.compose.runtime.key(categoryKey, isLooping, items.size) {
        rememberPagerState(initialPage = startIndex, pageCount = { virtualCount })
    }
    LaunchedEffect(categoryKey, isLooping, items.size) {
        if (isLooping && pagerState.currentPage < 1000) {
            runCatching { pagerState.scrollToPage(startIndex) }
        }
    }
    val perf = com.omnitrack.app.ui.components.LocalPerformanceSettings.current
    val animateHero = !perf.disableCardAnimations
    val scope = rememberCoroutineScope()
    val currentActualIdx = if (isLooping) {
        ((pagerState.currentPage % items.size) + items.size) % items.size
    } else {
        pagerState.currentPage.coerceIn(0, items.lastIndex)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = isLooping,
            beyondViewportPageCount = 1,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(1)
            ),
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val idx = if (isLooping) {
                ((page % items.size) + items.size) % items.size
            } else {
                page.coerceIn(0, items.lastIndex)
            }
            val item = items[idx]
            val match = findLibraryMatch(allMedia, item)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (animateHero) {
                            val absOffset = kotlin.math.abs(heroPageOffset(pagerState, page)).coerceIn(0f, 1f)
                            val scale = 1f - 0.04f * absOffset
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - 0.18f * absOffset
                        }
                    }
            ) {
                DiscoverSpotlightBanner(
                    item = item,
                    libraryMatch = match,
                    dimBanner = dimBanner,
                    bannerBlurDp = resolveBlurDp(item.mediaType),
                    onDetails = {
                        if (match != null) onItemClick(match.id) else {
                            EphemeralPreviewHolder.previewItem = item.toMediaItemEntity()
                            onItemClick(-1L)
                        }
                    },
                    onAdd = { onAddMedia(item) }
                )
            }
        }
        if (items.size > 1) {
            val visibleForDots = if (animateHero) {
                heroVisibleLayers(pagerState, items.size)
            } else {
                listOf(HeroPageLayer(currentActualIdx, 1f, 0f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(items.size) { idx ->
                    val frac = visibleForDots.firstOrNull { it.itemIndex == idx }?.visibility ?: 0f
                    Box(
                        modifier = Modifier
                            .clickable {
                                scope.launch {
                                    runCatching {
                                        pagerState.animateScrollToPage(
                                            heroPageForItem(
                                                pagerState.currentPage, idx, items.size
                                            )
                                        )
                                    }
                                }
                            }
                            .padding(horizontal = 3.dp)
                            .width((5f + 13f * frac).dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(AccentGold.copy(alpha = 0.30f + 0.70f * frac))
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverShelfSkeletonRow(
    title: String,
    isSquare: Boolean,
    onHideShelf: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "discover_skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = "Hide Shelf",
                tint = TextSecondary.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onHideShelf)
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(5) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(114.dp)
                            .height(if (isSquare) 114.dp else 171.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = alpha))
                    )
                    Box(
                        modifier = Modifier
                            .width(86.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverCustomEntryCard(
    query: String,
    suggestedType: MediaType,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentGold.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(19.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (query.isNotBlank()) "Can't find \"$query\"?" else "Create Custom Entry",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Add manually as ${suggestedType.displayName} with custom cover, synopsis & progress",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Text(
                text = "Create",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold
            )
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
    }
}
