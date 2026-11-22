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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import com.omnitrack.app.ui.theme.AccentGold
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
    onCreateCustomEntry: (String, MediaType) -> Unit = { _, _ -> },
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
    var selectedComicPublisherSubFilter by remember { mutableStateOf("ALL") }
    var selectedComicFormatSubFilter by remember { mutableStateOf("ALL") }
    var comicShelves by remember { mutableStateOf<Map<String, List<SearchResultItem>>>(emptyMap()) }
    var isLoadingComicShelves by remember { mutableStateOf(false) }
    var animeShelves by remember { mutableStateOf<Map<String, List<SearchResultItem>>>(emptyMap()) }
    var mangaShelves by remember { mutableStateOf<Map<String, List<SearchResultItem>>>(emptyMap()) }
    var novelShelves by remember { mutableStateOf<Map<String, List<SearchResultItem>>>(emptyMap()) }
    var showShelves by remember { mutableStateOf<Map<String, List<SearchResultItem>>>(emptyMap()) }
    var movieShelves by remember { mutableStateOf<Map<String, List<SearchResultItem>>>(emptyMap()) }
    var vnShelves by remember { mutableStateOf<Map<String, List<SearchResultItem>>>(emptyMap()) }
    var allVerticalShelves by remember { mutableStateOf<Map<String, List<SearchResultItem>>>(emptyMap()) }
    var isLoadingCategoryShelves by remember { mutableStateOf(false) }

    LaunchedEffect(selectedFilter, tmdbKey) {
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
        if (selectedFilter == MediaType.COMIC && comicShelves.isEmpty() && !isLoadingComicShelves) {
            isLoadingComicShelves = true
            try {
                val comicSrc = com.omnitrack.app.data.enrichment.ComicEnrichmentSource()
                val trendingRuns = comicSrc.fetchDiscoverComicsShelves("superhero")
                val pullList = comicSrc.fetchDiscoverComicsShelves("pull_list")
                val iconicGns = comicSrc.fetchDiscoverComicsShelves("indie")
                comicShelves = mapOf(
                    "Trending & Acclaimed Runs" to trendingRuns,
                    "New This Week (Pull List)" to pullList,
                    "Iconic Graphic Novels" to iconicGns
                ).filterValues { it.isNotEmpty() }
            } catch (_: Exception) {
            } finally {
                isLoadingComicShelves = false
            }
        }
        val needsCategoryLoad = when (selectedFilter) {
            MediaType.ANIME -> animeShelves.isEmpty()
            MediaType.MANGA -> mangaShelves.isEmpty()
            MediaType.NOVEL -> novelShelves.isEmpty()
            MediaType.SHOW -> showShelves.isEmpty()
            MediaType.MOVIE -> movieShelves.isEmpty()
            MediaType.VISUAL_NOVEL -> vnShelves.isEmpty()
            MediaType.ALL -> allVerticalShelves.isEmpty()
            else -> false
        }
        if (needsCategoryLoad && !isLoadingCategoryShelves) {
            isLoadingCategoryShelves = true
            try {
                coroutineScope {
                    when (selectedFilter) {
                        MediaType.ANIME -> {
                            val a = async { searchRepository.fetchDiscoverAnimeShelves("trending") }
                            val b = async { searchRepository.fetchDiscoverAnimeShelves("popular") }
                            val c = async { searchRepository.fetchDiscoverAnimeShelves("top_rated") }
                            animeShelves = mapOf(
                                "Trending Anime Now" to a.await(),
                                "All-Time Popular" to b.await(),
                                "Top Rated Anime" to c.await()
                            ).filterValues { it.isNotEmpty() }
                        }
                        MediaType.MANGA -> {
                            val a = async { searchRepository.fetchDiscoverMangaShelves("trending") }
                            val b = async { searchRepository.fetchDiscoverMangaShelves("manhwa") }
                            val c = async { searchRepository.fetchDiscoverMangaShelves("top_rated") }
                            mangaShelves = mapOf(
                                "Trending Manga" to a.await(),
                                "Popular Manhwa (Korea)" to b.await(),
                                "Top Rated Manga" to c.await()
                            ).filterValues { it.isNotEmpty() }
                        }
                        MediaType.NOVEL -> {
                            val lnT = async { searchRepository.fetchDiscoverNovelsShelves("trending_ln") }
                            val wn = async { searchRepository.fetchDiscoverNovelsShelves("top_webnovels") }
                            val lnTop = async { searchRepository.fetchDiscoverNovelsShelves("top_rated_ln") }
                            novelShelves = mapOf(
                                "Popular Web Novels" to wn.await(),
                                "Trending Light Novels" to lnT.await(),
                                "Top Rated Light Novels" to lnTop.await()
                            ).filterValues { it.isNotEmpty() }
                        }
                        MediaType.SHOW -> {
                            val t = async { searchRepository.fetchDiscoverShowsShelves("trending", tmdbKey) }
                            val r = async { searchRepository.fetchDiscoverShowsShelves("top_rated", tmdbKey) }
                            showShelves = mapOf(
                                "Trending TV Shows" to t.await(),
                                "Top Rated Series" to r.await()
                            ).filterValues { it.isNotEmpty() }
                        }
                        MediaType.MOVIE -> {
                            val t = async { searchRepository.fetchDiscoverMoviesShelves("trending", tmdbKey) }
                            val r = async { searchRepository.fetchDiscoverMoviesShelves("top_rated", tmdbKey) }
                            movieShelves = mapOf(
                                "Trending Movies" to t.await(),
                                "Top Rated Movies" to r.await()
                            ).filterValues { it.isNotEmpty() }
                        }
                        MediaType.VISUAL_NOVEL -> {
                            val p = async { searchRepository.fetchDiscoverVisualNovelsShelves("popular") }
                            val r = async { searchRepository.fetchDiscoverVisualNovelsShelves("top_rated") }
                            vnShelves = mapOf(
                                "Popular Visual Novels" to p.await(),
                                "Highest Rated Visual Novels" to r.await()
                            ).filterValues { it.isNotEmpty() }
                        }
                        MediaType.ALL -> {
                            val ani = async { searchRepository.fetchDiscoverAnimeShelves("trending").take(12) }
                            val man = async { searchRepository.fetchDiscoverMangaShelves("trending").take(12) }
                            val nov = async { searchRepository.fetchDiscoverNovelsShelves("top_webnovels").take(12) }
                            val sho = async { searchRepository.fetchDiscoverShowsShelves("trending", tmdbKey).take(12) }
                            val mov = async { searchRepository.fetchDiscoverMoviesShelves("trending", tmdbKey).take(12) }
                            val bk = async { searchRepository.fetchDiscoverBooksShelves("trending").take(12) }
                            val gm = async { searchRepository.fetchDiscoverGamesShelves("trending").take(12) }
                            allVerticalShelves = mapOf(
                                "Trending Anime" to ani.await(),
                                "Trending Manga" to man.await(),
                                "Popular Web Novels" to nov.await(),
                                "Trending Shows" to sho.await(),
                                "Trending Movies" to mov.await(),
                                "Trending Books" to bk.await(),
                                "Trending Games" to gm.await()
                            ).filterValues { it.isNotEmpty() }
                        }
                        else -> Unit
                    }
                }
            } catch (_: Exception) {
            } finally {
                isLoadingCategoryShelves = false
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
                tint = if (useAiSearch) AccentGold else TextMuted,
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
                    .background(if (useAiSearch) AccentGold.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
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
                    tint = if (useAiSearch) AccentGold else TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (useAiSearch) "AI · $shortModelLabel" else "AI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (useAiSearch) AccentGold else TextMuted
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Select AI Model",
                    tint = if (useAiSearch) AccentGold else TextMuted,
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
                MediaType.COMIC,
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
                        .background(if (isSelected) AccentGold else DarkSurfaceElevated)
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

        if (selectedFilter == MediaType.COMIC) {
            com.omnitrack.app.ui.screens.home.ComicUnderlineFilterBar(
                selectedPublisher = selectedComicPublisherSubFilter,
                selectedFormat = selectedComicFormatSubFilter,
                onPublisherSelected = { selectedComicPublisherSubFilter = it },
                onFormatSelected = { selectedComicFormatSubFilter = it },
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

        Spacer(modifier = Modifier.height(14.dp))

        val visibleResults = remember(searchResults, selectedFilter, selectedMangaSubFilter, selectedNovelSubFilter, selectedVnOriginSubFilter, selectedVnLengthSubFilter, selectedGamePlatformSubFilter, selectedBookFormatSubFilter, selectedBookCategorySubFilter, selectedComicPublisherSubFilter, selectedComicFormatSubFilter) {
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
                selectedFilter == MediaType.COMIC -> base.filter { item ->
                    val pubOk = selectedComicPublisherSubFilter == "ALL" ||
                        com.omnitrack.app.data.model.normalizeComicPublisherGroup(item.comicPublisher, item.platform) == selectedComicPublisherSubFilter
                    val fmtOk = selectedComicFormatSubFilter == "ALL" ||
                        com.omnitrack.app.data.model.normalizeComicFormatGroup(item.comicFormat) == selectedComicFormatSubFilter
                    pubOk && fmtOk
                }
                else -> base
            }
        }

        if (isSearching && visibleResults.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentGold, strokeWidth = 2.dp)
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
                    contentPadding = PaddingValues(bottom = 110.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visibleResults, key = { "${it.sourceProvider}_${it.mediaType}_${it.platform ?: ""}_${it.title}_${it.anilistId ?: it.bangumiId ?: it.malId ?: it.tmdbId ?: it.igdbId ?: it.steamAppId ?: it.vndbId ?: it.openLibraryId ?: it.hardcoverId ?: it.comicVineId ?: it.metronId ?: 0L}" }) { item ->
                    val itemNovelSub = if (item.mediaType == MediaType.NOVEL) {
                        com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                            item.platform, item.originCountry, item.anilistId, item.malId, item.totalProgress, item.title
                        )
                    } else null

                    val existingInLibrary = allMedia.firstOrNull { summary ->
                        summary.mediaType == item.mediaType && (
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
                        badgeColor = AccentGold,
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
        } else if (searchQuery.length >= 2) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "No media found matching \"$searchQuery\"",
                        fontSize = 13.5.sp,
                        color = TextPrimary
                    )
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
                        CircularProgressIndicator(color = AccentGold, strokeWidth = 2.dp)
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
                        CircularProgressIndicator(color = AccentGold, strokeWidth = 2.dp)
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
        } else if (selectedFilter == MediaType.COMIC) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (isLoadingComicShelves && comicShelves.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentGold, strokeWidth = 2.dp)
                    }
                }
                comicShelves.forEach { (shelfTitle, shelfItems) ->
                    val filteredShelf = shelfItems.filter { item ->
                        val pubOk = selectedComicPublisherSubFilter == "ALL" ||
                            com.omnitrack.app.data.model.normalizeComicPublisherGroup(item.comicPublisher, item.platform) == selectedComicPublisherSubFilter
                        val fmtOk = selectedComicFormatSubFilter == "ALL" ||
                            com.omnitrack.app.data.model.normalizeComicFormatGroup(item.comicFormat) == selectedComicFormatSubFilter
                        pubOk && fmtOk
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
                                    key = { "${shelfTitle}_${it.comicVineId ?: it.metronId ?: it.openLibraryId ?: it.title}" }
                                ) { comic ->
                                    val inLib = allMedia.firstOrNull { summary ->
                                        summary.mediaType == MediaType.COMIC && (
                                            (comic.comicVineId != null && summary.comicVineId == comic.comicVineId) ||
                                                (comic.metronId != null && summary.metronId == comic.metronId) ||
                                                summary.title.equals(comic.title, ignoreCase = true)
                                            )
                                    }
                                    com.omnitrack.app.ui.components.NuvioRailPosterCard(
                                        title = comic.title,
                                        coverUrl = inLib?.customCoverPath ?: inLib?.coverUrl ?: comic.coverUrl,
                                        subtitle = comic.comicPublisher ?: comic.releaseYear,
                                        rating = comic.communityScore,
                                        onClick = {
                                            if (inLib != null) {
                                                onItemClick(inLib.id)
                                            } else {
                                                EphemeralPreviewHolder.previewItem = comic.toMediaItemEntity()
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
                val comicSpotlight = allMedia.filter { it.mediaType == MediaType.COMIC }.take(12)
                if (comicSpotlight.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "In Your Comics Library",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            lazyRowItems(comicSpotlight, key = { "lib_comic_${it.id}" }) { summary ->
                                com.omnitrack.app.ui.components.NuvioRailPosterCard(
                                    title = summary.title,
                                    coverUrl = summary.customCoverPath ?: summary.coverUrl,
                                    subtitle = com.omnitrack.app.data.model.formatComicCardBadge(
                                        publisher = summary.comicPublisher,
                                        format = summary.comicFormat,
                                        currentProgress = summary.currentProgress,
                                        totalProgress = summary.totalProgress,
                                        trackingMode = summary.comicTrackingMode
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
            // Live per-category Discover shelves + strictly filtered Library Spotlight (Friend Bug #1 fixed!)
            val activeShelves: Map<String, List<SearchResultItem>> = when (selectedFilter) {
                MediaType.ANIME -> animeShelves
                MediaType.MANGA -> when (selectedMangaSubFilter) {
                    "MANHWA" -> mangaShelves.filterKeys { it.contains("Manhwa", ignoreCase = true) || it.contains("Trending", ignoreCase = true) }
                        .mapValues { (k, list) ->
                            if (k.contains("Manhwa", ignoreCase = true)) list
                            else list.filter { com.omnitrack.app.ui.screens.home.inferMangaSubtype(it.originCountry, it.nativeTitle) == "MANHWA" }
                        }
                    "MANHUA" -> mangaShelves.mapValues { (_, list) ->
                        list.filter { com.omnitrack.app.ui.screens.home.inferMangaSubtype(it.originCountry, it.nativeTitle) == "MANHUA" }
                    }
                    "MANGA" -> mangaShelves.mapValues { (_, list) ->
                        list.filter { com.omnitrack.app.ui.screens.home.inferMangaSubtype(it.originCountry, it.nativeTitle) == "MANGA" }
                    }
                    else -> mangaShelves
                }
                MediaType.NOVEL -> when (selectedNovelSubFilter) {
                    "WEB_NOVEL" -> novelShelves.filterKeys { it.contains("Web", ignoreCase = true) }
                    "LIGHT_NOVEL" -> novelShelves.filterKeys { it.contains("Light", ignoreCase = true) }
                    else -> novelShelves
                }
                MediaType.SHOW -> showShelves
                MediaType.MOVIE -> movieShelves
                MediaType.VISUAL_NOVEL -> vnShelves
                else -> allVerticalShelves
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
                            it.platform, it.originCountry, it.anilistId, it.malId, it.totalProgress, it.title
                        ) == selectedNovelSubFilter
                    }
                    selectedFilter == MediaType.VISUAL_NOVEL -> byType.filter {
                        (selectedVnOriginSubFilter == "ALL" || com.omnitrack.app.data.model.inferVnOriginGroup(it.originCountry, it.nativeTitle) == selectedVnOriginSubFilter) &&
                            (selectedVnLengthSubFilter == "ALL" || com.omnitrack.app.data.model.inferVnLengthGroup(it.runtimeMinutes) == selectedVnLengthSubFilter)
                    }
                    else -> byType
                }.take(12)
            }
            val quickChips = when (selectedFilter) {
                MediaType.ANIME -> listOf("Frieren", "Dandadan", "Bleach", "Solo Leveling", "Steins;Gate")
                MediaType.MANGA -> listOf("Berserk", "Vagabond", "One Piece", "Omniscient Reader", "Kagurabachi")
                MediaType.NOVEL -> listOf("The Hundred Reigns", "Shadow Slave", "Lord of the Mysteries", "The Perfect Run", "86", "Re:Zero")
                MediaType.SHOW -> listOf("Severance", "Andor", "Breaking Bad", "Arcane", "Shogun")
                MediaType.MOVIE -> listOf("Dune", "Oppenheimer", "Interstellar", "Spider-Verse", "Parasite")
                MediaType.VISUAL_NOVEL -> listOf("Steins;Gate", "Clannad", "The House in Fata Morgana", "Umineko", "Muv-Luv")
                else -> listOf("The Hundred Reigns", "Audiobook", "Invincible", "Dune", "Frieren", "Elden Ring")
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    quickChips.forEach { chip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(DarkSurfaceElevated)
                                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                                .clickable { queryFlow.value = chip }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
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

                if (isLoadingCategoryShelves && activeShelves.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentGold, strokeWidth = 2.dp)
                    }
                }

                activeShelves.forEach { (shelfTitle, shelfItems) ->
                    if (shelfItems.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = shelfTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                lazyRowItems(
                                    items = shelfItems,
                                    key = { "${shelfTitle}_${it.mediaType}_${it.sourceProvider}_${it.anilistId ?: it.vndbId ?: it.tmdbId ?: it.steamAppId ?: it.openLibraryId ?: it.title}" }
                                ) { item ->
                                    val inLib = allMedia.firstOrNull { summary ->
                                        summary.mediaType == item.mediaType && (
                                            (item.anilistId != null && summary.anilistId == item.anilistId) ||
                                                (item.tmdbId != null && summary.tmdbId == item.tmdbId) ||
                                                (item.openLibraryId != null && summary.openLibraryId == item.openLibraryId) ||
                                                summary.title.equals(item.title, ignoreCase = true)
                                            )
                                    }
                                    com.omnitrack.app.ui.components.NuvioRailPosterCard(
                                        title = item.title,
                                        coverUrl = inLib?.customCoverPath ?: inLib?.coverUrl ?: item.coverUrl,
                                        subtitle = item.bookAuthor ?: item.releaseYear ?: item.mediaType.displayName,
                                        rating = item.communityScore,
                                        onClick = {
                                            if (inLib != null) {
                                                onItemClick(inLib.id)
                                            } else {
                                                EphemeralPreviewHolder.previewItem = item.toMediaItemEntity()
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

                if (categorySpotlight.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (selectedFilter == MediaType.ALL) {
                                "In Your Library"
                            } else {
                                "In Your ${selectedFilter.displayName} Library"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            lazyRowItems(categorySpotlight, key = { "lib_spot_${it.id}" }) { summary ->
                                com.omnitrack.app.ui.components.NuvioRailPosterCard(
                                    title = summary.title,
                                    coverUrl = summary.customCoverPath ?: summary.coverUrl,
                                    subtitle = summary.bookAuthor ?: summary.mediaType.displayName,
                                    rating = summary.userRating ?: summary.communityScore,
                                    onClick = {
                                        com.omnitrack.app.ui.screens.detail.ActiveSiblingHolder.setSiblings(categorySpotlight.map { it.id })
                                        onItemClick(summary.id)
                                    },
                                    cardWidth = 112.dp
                                )
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

@Composable
private fun DiscoverCustomEntryCard(
    query: String,
    suggestedType: MediaType,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, AccentGold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AccentGold.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = AccentGold,
                modifier = Modifier.size(20.dp)
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
}
