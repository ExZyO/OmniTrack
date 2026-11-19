package com.omnitrack.app.ui.screens.home.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.ai.AiQueryEngine
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.DEFAULT_GEMINI_MODELS
import com.omnitrack.app.data.preferences.PosterCardStyle
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.search.OmniSearchRepository
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.ui.components.GeminiModelSelectorSection
import com.omnitrack.app.ui.components.MediaCard
import com.omnitrack.app.ui.screens.detail.EphemeralPreviewHolder
import com.omnitrack.app.ui.screens.home.HomeViewModel
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DiscoverView(
    allMedia: List<MediaCardSummary>,
    onItemClick: (Long) -> Unit,
    onAddMedia: (SearchResultItem) -> Unit,
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel? = null,
    searchRepository: OmniSearchRepository = remember { OmniSearchRepository() },
    aiEngine: AiQueryEngine = remember { AiQueryEngine() }
) {
    val context = LocalContext.current
    val prefManager = remember { PreferenceManager(context) }
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

    val queryFlow = remember(homeViewModel) { homeViewModel?.discoverSearchQuery ?: MutableStateFlow("") }
    val filterFlow = remember(homeViewModel) { homeViewModel?.discoverSelectedFilter ?: MutableStateFlow(MediaType.ALL) }
    val isSearchingFlow = remember(homeViewModel) { homeViewModel?.discoverIsSearching ?: MutableStateFlow(false) }
    val resultsFlow = remember(homeViewModel) { homeViewModel?.discoverSearchResults ?: MutableStateFlow<List<SearchResultItem>>(emptyList()) }
    val bannerFlow = remember(homeViewModel) { homeViewModel?.discoverAiResolutionBanner ?: MutableStateFlow<String?>(null) }
    val useAiFlow = remember(homeViewModel) { homeViewModel?.discoverUseAiSearch ?: MutableStateFlow<Boolean?>(null) }

    val searchQuery by queryFlow.collectAsState()
    val selectedFilter by filterFlow.collectAsState()
    val isSearching by isSearchingFlow.collectAsState()
    val searchResults by resultsFlow.collectAsState()
    val aiResolutionBanner by bannerFlow.collectAsState()
    val storedUseAi by useAiFlow.collectAsState()
    val useAiSearch = storedUseAi ?: aiSearchSetting

    LaunchedEffect(aiSearchSetting) {
        if (useAiFlow.value == null) {
            useAiFlow.value = aiSearchSetting
        }
    }

    var localLastDebounceQuery by remember { mutableStateOf(homeViewModel?.discoverLastDebounceQuery) }
    var localLastExecutedQuery by remember { mutableStateOf(homeViewModel?.discoverLastExecutedQuery) }
    var localLastExecutedFilter by remember { mutableStateOf(homeViewModel?.discoverLastExecutedFilter) }
    var localLastExecutedAi by remember { mutableStateOf(homeViewModel?.discoverLastExecutedAi) }
    var localLastExecutedModel by remember { mutableStateOf<String?>(null) }
    var localLastEffectiveQuery by remember { mutableStateOf(homeViewModel?.discoverLastEffectiveQuery) }
    var localLastEffectiveFilter by remember { mutableStateOf(homeViewModel?.discoverLastEffectiveFilter) }
    var searchGen by remember { mutableStateOf(0L) }
    var selectedMangaSubFilter by remember { mutableStateOf("ALL") }
    var selectedNovelSubFilter by remember { mutableStateOf("ALL") }
    var selectedVnOriginSubFilter by remember { mutableStateOf("ALL") }
    var selectedVnLengthSubFilter by remember { mutableStateOf("ALL") }
    var selectedGamePlatformSubFilter by remember { mutableStateOf("ALL") }
    var gameShelves by remember { mutableStateOf<Map<String, List<SearchResultItem>>>(emptyMap()) }
    var isLoadingGameShelves by remember { mutableStateOf(false) }
    var selectedBookFormatSubFilter by remember { mutableStateOf("ALL") }
    var selectedBookCategorySubFilter by remember { mutableStateOf<String?>(null) }
    var bookShelves by remember { mutableStateOf<Map<String, List<SearchResultItem>>>(emptyMap()) }
    var isLoadingBookShelves by remember { mutableStateOf(false) }

    LaunchedEffect(selectedFilter) {
        if (selectedFilter == MediaType.GAME && gameShelves.isEmpty() && !isLoadingGameShelves) {
            isLoadingGameShelves = true
            try {
                val trending = searchRepository.fetchDiscoverGamesShelves("trending")
                val topRated = searchRepository.fetchDiscoverGamesShelves("top_rated")
                val upcoming = searchRepository.fetchDiscoverGamesShelves("upcoming")
                gameShelves = mapOf(
                    "Trending Now" to trending,
                    "Top Critically Acclaimed" to topRated,
                    "Upcoming Releases" to upcoming
                ).filterValues { it.isNotEmpty() }
            } catch (_: Exception) {
            } finally {
                isLoadingGameShelves = false
            }
        }
        if (selectedFilter == MediaType.BOOK && bookShelves.isEmpty() && !isLoadingBookShelves) {
            isLoadingBookShelves = true
            try {
                val trending = searchRepository.fetchDiscoverBooksShelves("trending")
                val classics = searchRepository.fetchDiscoverBooksShelves("classics_poetry")
                val scifi = searchRepository.fetchDiscoverBooksShelves("scifi_fantasy")
                val philosophy = searchRepository.fetchDiscoverBooksShelves("philosophy_nonfiction")
                val audiobooks = searchRepository.fetchDiscoverBooksShelves("audiobooks")
                bookShelves = mapOf(
                    "Trending This Week" to trending,
                    "Classics & Epic Poetry" to classics,
                    "Sci-Fi & Fantasy" to scifi,
                    "Philosophy & Non-Fiction" to philosophy,
                    "Audiobooks" to audiobooks
                ).filterValues { it.isNotEmpty() }
            } catch (_: Exception) {
            } finally {
                isLoadingBookShelves = false
            }
        }
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

    // Multi-Vertical OmniSearch Trigger
    LaunchedEffect(searchQuery, selectedFilter, useAiSearch, activeGeminiModel) {
        val myGen = ++searchGen
        val trimmed = searchQuery.trim()
        val prevQuery = homeViewModel?.discoverLastExecutedQuery ?: localLastExecutedQuery
        val prevFilter = homeViewModel?.discoverLastExecutedFilter ?: localLastExecutedFilter
        val prevAi = homeViewModel?.discoverLastExecutedAi ?: localLastExecutedAi
        val prevDebounceQuery = homeViewModel?.discoverLastDebounceQuery ?: localLastDebounceQuery

        if (trimmed.length >= 2) {
            val modelChanged = useAiSearch && localLastExecutedModel != null && localLastExecutedModel != activeGeminiModel
            if (!modelChanged && resultsFlow.value.isNotEmpty() && trimmed == prevQuery && selectedFilter == prevFilter && useAiSearch == prevAi) {
                // Preserved search results restored from previous navigation! Don't re-trigger.
                return@LaunchedEffect
            }
            localLastExecutedModel = activeGeminiModel
            val filterChanged = prevFilter != null && selectedFilter != prevFilter
            if (!filterChanged && !modelChanged && trimmed != prevDebounceQuery) {
                delay(400)
                localLastDebounceQuery = trimmed
                homeViewModel?.discoverLastDebounceQuery = trimmed
            } else if (trimmed != prevDebounceQuery) {
                localLastDebounceQuery = trimmed
                homeViewModel?.discoverLastDebounceQuery = trimmed
            }

            var queryToRun = trimmed
            var filterToRun = selectedFilter
            var resolvedBanner: String? = null

            if (useAiSearch) {
                val aiResult = aiEngine.resolveQuery(
                    rawQuery = trimmed,
                    geminiApiKey = geminiKey.ifBlank { null },
                    openRouterApiKey = openRouterKey.ifBlank { null },
                    geminiModel = activeGeminiModel
                )
                if (aiResult.isAiResolved) {
                    queryToRun = aiResult.resolvedTitle
                    resolvedBanner = "AI [$activeGeminiModel]: \"${aiResult.resolvedTitle}\"" +
                        (aiResult.detectedType?.let { " (${it.displayName})" } ?: "") +
                        (aiResult.explanation?.let { " • $it" } ?: "")
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
                    if (geminiKey.isBlank() && openRouterKey.isBlank()) {
                        resolvedBanner = if (aiResult.resolvedTitle != trimmed || aiResult.detectedType != null) {
                            "Smart Match: \"${aiResult.resolvedTitle}\"" +
                                (aiResult.detectedType?.let { " (${it.displayName})" } ?: "") +
                                " • Add a Gemini/OpenRouter key in Settings for full AI"
                        } else {
                            "AI Smart Mode (No API key in Settings) — Try natural phrases like 'anime about alchemy', or add a Gemini key."
                        }
                    } else {
                        resolvedBanner = "AI Mode [$activeGeminiModel]: searching exact title \"$queryToRun\""
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
                        if (myGen == searchGen && filterFlow.value == selectedFilter) {
                            resultsFlow.value = if (filterToRun == MediaType.ALL) partial else partial.filter { it.mediaType == filterToRun }
                        }
                    }
                )
                if (myGen == searchGen && filterFlow.value == selectedFilter) {
                    resultsFlow.value = if (filterToRun == MediaType.ALL) results else results.filter { it.mediaType == filterToRun }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (myGen == searchGen) {
                    resultsFlow.value = emptyList()
                }
            } finally {
                if (myGen == searchGen) {
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        // Header
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Text(
                text = "Universal OmniSearch",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = TextPrimary
            )
            Text(
                text = "Live parallel search across AniList, MangaDex, Bangumi, TMDb, Steam & VNDB",
                fontSize = 12.sp,
                color = TextMuted
            )
        }

        // Search Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurfaceElevated)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (useAiSearch) AccentArctic else TextMuted,
                modifier = Modifier.size(20.dp)
            )
            BasicTextField(
                value = searchQuery,
                onValueChange = { queryFlow.value = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(AccentArctic),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = if (useAiSearch) "Ask AI or describe (e.g. 'anime with slime')" else "Search anime, manga, novels, games, shows...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                    innerTextField()
                }
            )

            // Inline AI Toggle & Quick Model Picker Pill
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
                    .background(if (useAiSearch) AccentArctic.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
                    .combinedClickable(
                        onClick = {
                            val nextAi = !useAiSearch
                            useAiFlow.value = nextAi
                            scope.launch { prefManager.updateAiSemanticSearchEnabled(nextAi) }
                            if (nextAi) {
                                if (geminiKey.isBlank() && openRouterKey.isBlank()) {
                                    bannerFlow.value = "AI Smart Mode enabled (Local heuristics) — Describe what you're looking for, or add a Gemini / OpenRouter key in Settings for full AI search."
                                }
                            } else {
                                bannerFlow.value = null
                            }
                        },
                        onLongClick = {
                            showModelPickerSheet = true
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Mode",
                    tint = if (useAiSearch) AccentArctic else TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (useAiSearch) "AI · $shortModelLabel" else "AI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (useAiSearch) AccentArctic else TextMuted
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Select AI Model",
                    tint = if (useAiSearch) AccentArctic else TextMuted,
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .clickable { showModelPickerSheet = true }
                )
            }

            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { queryFlow.value = "" }
                )
            }
        }

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val categories = listOf(
                MediaType.ALL,
                MediaType.ANIME,
                MediaType.MANGA,
                MediaType.NOVEL,
                MediaType.BOOK,
                MediaType.SHOW,
                MediaType.MOVIE,
                MediaType.GAME,
                MediaType.VISUAL_NOVEL
            )
            categories.forEach { type ->
                val isSelected = selectedFilter == type
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) AccentArctic else DarkSurfaceElevated)
                        .clickable { filterFlow.value = type }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (type == MediaType.ALL) "All Verticals" else type.displayName,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) AmoledBlack else TextSecondary
                    )
                }
            }
        }

        if (selectedFilter == MediaType.MANGA) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("ALL" to "All", "MANGA" to "Manga", "MANHWA" to "Manhwa", "MANHUA" to "Manhua").forEach { (key, label) ->
                    val isSubSelected = selectedMangaSubFilter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSubSelected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isSubSelected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .clickable { selectedMangaSubFilter = key }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSubSelected) Color.White else Color.White.copy(alpha = 0.60f)
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
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("ALL" to "All", "LIGHT_NOVEL" to "Light Novel", "WEB_NOVEL" to "Web Novel").forEach { (key, label) ->
                    val isSubSelected = selectedNovelSubFilter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSubSelected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isSubSelected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .clickable { selectedNovelSubFilter = key }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSubSelected) Color.White else Color.White.copy(alpha = 0.60f)
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
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (selectedFilter == MediaType.GAME) {
            com.omnitrack.app.ui.screens.home.GameUnderlineFilterBar(
                selectedPlatform = selectedGamePlatformSubFilter,
                selectedCompletion = null,
                onPlatformSelected = { selectedGamePlatformSubFilter = it },
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (selectedFilter == MediaType.BOOK) {
            com.omnitrack.app.ui.screens.home.BookUnderlineFilterBar(
                selectedFormat = selectedBookFormatSubFilter,
                selectedCategory = selectedBookCategorySubFilter,
                onFormatSelected = { selectedBookFormatSubFilter = it },
                onCategorySelected = { selectedBookCategorySubFilter = it },
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // AI Resolution Banner
        if (aiResolutionBanner != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentArctic.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AccentArctic,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = aiResolutionBanner!!,
                    fontSize = 11.5.sp,
                    color = AccentArctic,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val visibleResults = remember(searchResults, selectedFilter, selectedMangaSubFilter, selectedNovelSubFilter, selectedVnOriginSubFilter, selectedVnLengthSubFilter, selectedGamePlatformSubFilter, selectedBookFormatSubFilter, selectedBookCategorySubFilter) {
            val base = if (selectedFilter == MediaType.ALL) searchResults else searchResults.filter { it.mediaType == selectedFilter }
            when {
                selectedFilter == MediaType.MANGA && selectedMangaSubFilter != "ALL" -> base.filter {
                    com.omnitrack.app.ui.screens.home.inferMangaSubtype(it.originCountry, it.nativeTitle) == selectedMangaSubFilter
                }
                selectedFilter == MediaType.NOVEL && selectedNovelSubFilter != "ALL" -> base.filter {
                    com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                        it.platform, it.originCountry, it.anilistId, it.malId, it.totalProgress, it.title
                    ) == selectedNovelSubFilter
                }
                selectedFilter == MediaType.VISUAL_NOVEL -> base.filter {
                    (selectedVnOriginSubFilter == "ALL" || com.omnitrack.app.data.model.inferVnOriginGroup(it.originCountry, it.nativeTitle) == selectedVnOriginSubFilter) &&
                        (selectedVnLengthSubFilter == "ALL" || com.omnitrack.app.data.model.inferVnLengthGroup(it.runtimeMinutes) == selectedVnLengthSubFilter)
                }
                selectedFilter == MediaType.GAME && selectedGamePlatformSubFilter != "ALL" -> base.filter {
                    val groups = com.omnitrack.app.data.model.normalizeGamePlatformGroup(it.platform, null)
                    groups.isEmpty() || selectedGamePlatformSubFilter in groups
                }
                selectedFilter == MediaType.BOOK -> base.filter { item ->
                    val fmtOk = selectedBookFormatSubFilter == "ALL" ||
                        com.omnitrack.app.data.model.normalizeBookFormatGroup(item.bookFormat) == selectedBookFormatSubFilter
                    val catOk = selectedBookCategorySubFilter == null ||
                        selectedBookCategorySubFilter == "ALL" ||
                        com.omnitrack.app.data.model.inferBookCategoryGroup(item.title, item.platform, item.synopsis) == selectedBookCategorySubFilter
                    fmtOk && catOk
                }
                else -> base
            }
        }

        if (isSearching && visibleResults.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentArctic, strokeWidth = 2.dp)
            }
        } else if (visibleResults.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Results (${visibleResults.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
                if (isSearching) {
                    CircularProgressIndicator(
                        color = AccentArctic,
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
                    contentPadding = PaddingValues(bottom = 110.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visibleResults, key = { "${it.sourceProvider}_${it.mediaType}_${it.platform ?: ""}_${it.title}_${it.anilistId ?: it.bangumiId ?: it.malId ?: it.tmdbId ?: it.igdbId ?: it.steamAppId ?: it.vndbId ?: it.openLibraryId ?: it.hardcoverId ?: 0L}" }) { item ->
                    val itemNovelSub = if (item.mediaType == MediaType.NOVEL) {
                        com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                            item.platform, item.originCountry, item.anilistId, item.malId, item.totalProgress, item.title
                        )
                    } else null

                    val existingInLibrary = allMedia.firstOrNull { summary ->
                        summary.mediaType == item.mediaType && (
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
                        lastUpdated = System.currentTimeMillis()
                    )

                    val cardBadgeText = if (existingInLibrary != null) {
                        "IN LIBRARY"
                    } else if (itemNovelSub != null) {
                        val tag = if (itemNovelSub == "LIGHT_NOVEL") "LN" else "WN"
                        "$tag · ${item.sourceProvider}"
                    } else {
                        item.sourceProvider
                    }

                    MediaCard(
                        item = summary,
                        cornerRadiusDp = posterStyle.cornerRadiusDp,
                        isLandscape = posterStyle.isLandscape,
                        hideLabels = posterStyle.hideLabels,
                        badgeText = cardBadgeText,
                        badgeColor = if (existingInLibrary != null) AccentJade else AccentArctic,
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
            }
            }
        } else if (searchQuery.length >= 2) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No media found matching \"$searchQuery\"",
                    fontSize = 13.5.sp,
                    color = TextMuted
                )
            }
        } else if (selectedFilter == MediaType.GAME) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (isLoadingGameShelves && gameShelves.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentArctic, strokeWidth = 2.dp)
                    }
                }
                gameShelves.forEach { (shelfTitle, shelfItems) ->
                    val filteredShelf = if (selectedGamePlatformSubFilter == "ALL") shelfItems else shelfItems.filter { item ->
                        val groups = com.omnitrack.app.data.model.normalizeGamePlatformGroup(item.platform, null)
                        groups.isEmpty() || selectedGamePlatformSubFilter in groups
                    }
                    if (filteredShelf.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = shelfTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                lazyRowItems(
                                    items = filteredShelf,
                                    key = { "${shelfTitle}_${it.igdbId ?: it.steamAppId ?: it.title}" }
                                ) { game ->
                                    val inLib = allMedia.firstOrNull { summary ->
                                        summary.mediaType == MediaType.GAME && (
                                            (game.igdbId != null && summary.title.equals(game.title, true)) ||
                                                (game.steamAppId != null && summary.steamAppId == game.steamAppId) ||
                                                summary.title.equals(game.title, ignoreCase = true)
                                            )
                                    }
                                    com.omnitrack.app.ui.components.NuvioRailPosterCard(
                                        title = game.title,
                                        coverUrl = inLib?.customCoverPath ?: inLib?.coverUrl ?: game.coverUrl,
                                        subtitle = game.releaseYear ?: game.platform,
                                        rating = game.communityScore,
                                        onClick = {
                                            if (inLib != null) {
                                                onItemClick(inLib.id)
                                            } else {
                                                EphemeralPreviewHolder.previewItem = game.toMediaItemEntity()
                                                onItemClick(-1L)
                                            }
                                        },
                                        cardWidth = 112.dp
                                    )
                                }
                            }
                        }
                    }
                }
                val gameSpotlight = allMedia.filter { it.mediaType == MediaType.GAME }.take(12)
                if (gameSpotlight.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "In Your Games Library",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            lazyRowItems(gameSpotlight, key = { "lib_game_${it.id}" }) { summary ->
                                com.omnitrack.app.ui.components.NuvioRailPosterCard(
                                    title = summary.title,
                                    coverUrl = summary.customCoverPath ?: summary.coverUrl,
                                    subtitle = com.omnitrack.app.data.model.formatGameCardBadge(summary.platforms, summary.platform, summary.playtimeHours, summary.runtimeMinutes),
                                    rating = summary.userRating ?: summary.communityScore,
                                    onClick = { onItemClick(summary.id) },
                                    cardWidth = 112.dp
                                )
                            }
                        }
                    }
                }
            }
        } else if (selectedFilter == MediaType.BOOK) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (isLoadingBookShelves && bookShelves.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentArctic, strokeWidth = 2.dp)
                    }
                }
                bookShelves.forEach { (shelfTitle, shelfItems) ->
                    val filteredShelf = shelfItems.filter { item ->
                        val fmtOk = selectedBookFormatSubFilter == "ALL" ||
                            com.omnitrack.app.data.model.normalizeBookFormatGroup(item.bookFormat) == selectedBookFormatSubFilter
                        val catOk = selectedBookCategorySubFilter == null ||
                            selectedBookCategorySubFilter == "ALL" ||
                            com.omnitrack.app.data.model.inferBookCategoryGroup(item.title, item.platform, item.synopsis) == selectedBookCategorySubFilter
                        fmtOk && catOk
                    }
                    if (filteredShelf.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = shelfTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                lazyRowItems(
                                    items = filteredShelf,
                                    key = { "${shelfTitle}_${it.openLibraryId ?: it.hardcoverId ?: it.title}_${it.bookAuthor ?: ""}" }
                                ) { book ->
                                    val inLib = allMedia.firstOrNull { summary ->
                                        summary.mediaType == MediaType.BOOK && (
                                            (book.openLibraryId != null && summary.openLibraryId == book.openLibraryId) ||
                                                summary.title.equals(book.title, ignoreCase = true)
                                            )
                                    }
                                    com.omnitrack.app.ui.components.NuvioRailPosterCard(
                                        title = book.title,
                                        coverUrl = inLib?.customCoverPath ?: inLib?.coverUrl ?: book.coverUrl,
                                        subtitle = book.bookAuthor ?: book.releaseYear,
                                        rating = book.communityScore,
                                        onClick = {
                                            if (inLib != null) {
                                                onItemClick(inLib.id)
                                            } else {
                                                EphemeralPreviewHolder.previewItem = book.toMediaItemEntity()
                                                onItemClick(-1L)
                                            }
                                        },
                                        cardWidth = 112.dp
                                    )
                                }
                            }
                        }
                    }
                }
                val bookSpotlight = allMedia.filter { it.mediaType == MediaType.BOOK }.take(12)
                if (bookSpotlight.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "In Your Books Library",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            lazyRowItems(bookSpotlight, key = { "lib_book_${it.id}" }) { summary ->
                                com.omnitrack.app.ui.components.NuvioRailPosterCard(
                                    title = summary.title,
                                    coverUrl = summary.customCoverPath ?: summary.coverUrl,
                                    subtitle = com.omnitrack.app.data.model.formatBookCardBadge(
                                        currentProgress = summary.currentProgress,
                                        totalProgress = summary.totalProgress,
                                        trackingMode = summary.bookTrackingMode,
                                        audioMinutes = summary.bookAudioMinutes,
                                        totalAudioMinutes = summary.bookTotalAudioMinutes,
                                        bookFormat = summary.bookFormat,
                                        author = summary.bookAuthor
                                    ),
                                    rating = summary.userRating ?: summary.communityScore,
                                    onClick = { onItemClick(summary.id) },
                                    cardWidth = 112.dp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Spotlight from library when search box is empty
            Text(
                text = "Spotlight Titles in Library",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val spotlight = allMedia.take(12)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isPhone = maxWidth < 600.dp
                val discoverColumns = if (posterStyle.isLandscape) {
                    if (isPhone) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 340.dp)
                } else {
                    GridCells.Adaptive(minSize = posterStyle.widthDp.dp)
                }

                LazyVerticalGrid(
                    columns = discoverColumns,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 110.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(spotlight, key = { it.id }) { item ->
                    MediaCard(
                        item = item,
                        cornerRadiusDp = posterStyle.cornerRadiusDp,
                        isLandscape = posterStyle.isLandscape,
                        hideLabels = posterStyle.hideLabels,
                        onCardClick = {
                            com.omnitrack.app.ui.screens.detail.ActiveSiblingHolder.setSiblings(spotlight.map { it.id })
                            onItemClick(item.id)
                        },
                        onQuickProgress = { onItemClick(item.id) }
                    )
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
                            tint = AccentArctic,
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
                            scope.launch {
                                prefManager.updateUseCustomGeminiModel(true)
                            }
                        },
                        onCustomModelChange = { valTrim ->
                            scope.launch {
                                prefManager.updateCustomGeminiModel(valTrim)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
