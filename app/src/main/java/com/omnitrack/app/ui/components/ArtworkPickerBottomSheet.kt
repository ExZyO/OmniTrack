package com.omnitrack.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.omnitrack.app.data.artwork.ArtworkCandidate
import com.omnitrack.app.data.artwork.ArtworkWaterfallRepository
import com.omnitrack.app.data.cache.CoverCache
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun formatArtSourceBadgeLabel(sourceName: String): String = when {
    sourceName.contains("Wikimedia", ignoreCase = true) -> "Wikimedia"
    sourceName.contains("Open Library", ignoreCase = true) -> "OpenLib"
    sourceName.equals("SteamGridDB", ignoreCase = true) -> "SGDB"
    sourceName.equals("Google Books", ignoreCase = true) -> "Google"
    sourceName.equals("Apple Books", ignoreCase = true) -> "Apple"
    sourceName.equals("MyAnimeList", ignoreCase = true) -> "MAL"
    else -> sourceName.take(12)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkPickerBottomSheet(
    title: String,
    mediaType: MediaType,
    currentCoverUrl: String,
    currentBannerUrl: String? = null,
    currentCustomBannerPath: String? = null,
    mediaId: Long = 0,
    currentRole: String = "DEFAULT",
    anilistId: Long? = null,
    malId: Long? = null,
    mangadexId: String? = null,
    nativeTitle: String? = null,
    romajiTitle: String? = null,
    originCountry: String? = null,
    defaultCoverUrl: String? = null,
    vndbId: String? = null,
    englishTitle: String? = null,
    steamAppId: Long? = null,
    openLibraryId: String? = null,
    bookAuthor: String? = null,
    initialCandidates: List<ArtworkCandidate> = emptyList(),
    initialExcludedSources: Set<String> = emptySet(),
    onExcludedSourcesChanged: (Set<String>) -> Unit = {},
    currentBannerFocalX: Float = 0f,
    currentBannerFocalY: Float = 0f,
    currentBannerZoom: Float = 1f,
    onBannerFramingChanged: (Float, Float, Float) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit,
    onCoverSelected: (String) -> Unit,
    onBannerSelected: (String) -> Unit = {},
    onResetCover: () -> Unit = {},
    onResetBanner: () -> Unit = {},
    onRoleSelected: ((String) -> Unit)? = null,
    repository: ArtworkWaterfallRepository? = null,
    @Suppress("UNUSED_PARAMETER")
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val prefManager = remember { (context.applicationContext as? com.omnitrack.app.OmniTrackApplication)?.preferenceManager }
    val effectiveRepository = repository ?: remember(prefManager) { ArtworkWaterfallRepository(preferenceManager = prefManager) }
    val scope = rememberCoroutineScope()
    val coverCache = remember { (context.applicationContext as? com.omnitrack.app.OmniTrackApplication)?.coverCache ?: CoverCache(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var allCandidates by remember(title, mediaType, anilistId, malId, mangadexId, vndbId, steamAppId, openLibraryId, bookAuthor) { mutableStateOf(initialCandidates) }
    var isLoading by remember { mutableStateOf(false) }
    var excludedSources by remember(mediaId, title, initialExcludedSources) {
        mutableStateOf(initialExcludedSources.map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet())
    }
    var showDirectUrlInput by remember { mutableStateOf(false) }
    var customUrlInput by remember { mutableStateOf("") }

    // Cover custom picker launcher
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedPath = coverCache.saveCustomCoverUri(mediaId, uri)
                if (savedPath != null) {
                    onCoverSelected(savedPath)
                } else {
                    onCoverSelected(uri.toString())
                }
                onDismiss()
            }
        }
    }

    // Banner custom picker launcher
    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedPath = coverCache.saveCustomBannerUri(mediaId, uri)
                if (savedPath != null) {
                    onBannerSelected(savedPath)
                } else {
                    onBannerSelected(uri.toString())
                }
                onDismiss()
            }
        }
    }

    val fetchCandidates: (Boolean) -> Unit = { forceRefresh ->
        scope.launch {
            isLoading = true
            try {
                val finalResults = effectiveRepository.searchArtworkCandidates(
                    title = title,
                    mediaType = mediaType,
                    anilistId = anilistId,
                    malId = malId,
                    mangadexId = mangadexId,
                    nativeTitle = nativeTitle,
                    romajiTitle = romajiTitle,
                    originCountry = originCountry,
                    defaultCoverUrl = defaultCoverUrl,
                    forceRefresh = forceRefresh,
                    vndbId = vndbId,
                    englishTitle = englishTitle,
                    steamAppId = steamAppId,
                    openLibraryId = openLibraryId,
                    bookAuthor = bookAuthor,
                    initialCandidates = initialCandidates,
                    onPartialResults = { partial ->
                        scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                            allCandidates = partial.distinctBy { "${it.isBanner}:${it.url}" }
                        }
                    }
                )
                allCandidates = finalResults.distinctBy { "${it.isBanner}:${it.url}" }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(title, mediaType, anilistId, malId, mangadexId, vndbId, steamAppId, openLibraryId, bookAuthor) {
        fetchCandidates(false)
    }

    // Filter covers and banners according to exclusions and VN NSFW settings
    val hideNsfwCandidates = mediaType == MediaType.VISUAL_NOVEL &&
        com.omnitrack.app.data.preferences.PreferenceManager.cachedVnNsfwFilterMode ==
        com.omnitrack.app.data.model.VnNsfwFilterMode.SAFE_HIDE

    val displayedCovers = remember(allCandidates, excludedSources, hideNsfwCandidates) {
        val raw = if ("ALL" in excludedSources) {
            allCandidates.filter { !it.isBanner && it.sourceName.equals("Default", ignoreCase = true) && (!hideNsfwCandidates || !it.isNsfw) }
        } else {
            allCandidates.filter { !it.isBanner && it.sourceName.uppercase() !in excludedSources && (!hideNsfwCandidates || !it.isNsfw) }
        }
        raw.distinctBy { it.url }
    }

    val displayedBanners = remember(allCandidates, excludedSources, hideNsfwCandidates) {
        if ("ALL" in excludedSources) {
            emptyList()
        } else {
            allCandidates
                .filter { it.isBanner && it.sourceName.uppercase() !in excludedSources && (!hideNsfwCandidates || !it.isNsfw) }
                .distinctBy { it.url }
        }
    }

    var showFramingEditor by remember { mutableStateOf(false) }
    var draftFocalX by remember(currentBannerFocalX) { mutableFloatStateOf(currentBannerFocalX.coerceIn(-1f, 1f)) }
    var draftFocalY by remember(currentBannerFocalY) { mutableFloatStateOf(currentBannerFocalY.coerceIn(-1f, 1f)) }
    var draftZoom by remember(currentBannerZoom) { mutableFloatStateOf(currentBannerZoom.coerceIn(1f, 2.5f)) }

    val isBannerDisabled = currentCustomBannerPath == "NONE" || currentRole == "DISABLE_BANNER"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AmoledBlack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Customize Artwork",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = title,
                        color = TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { fetchCandidates(true) },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Candidates",
                            tint = if (isLoading) TextMuted else AccentGold
                        )
                    }
                    IconButton(
                        onClick = onDismiss
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3 Tabs: Covers, Banners, Exclusions
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = AccentGold,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = AccentGold
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = if (displayedCovers.isNotEmpty()) "Covers (${displayedCovers.size})" else "Covers",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.5.sp,
                            color = if (selectedTab == 0) AccentGold else TextMuted
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = if (displayedBanners.isNotEmpty()) "Banners (${displayedBanners.size})" else "Banners",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.5.sp,
                            color = if (selectedTab == 1) AccentGold else TextMuted
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "Exclusions",
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.5.sp,
                            color = if (selectedTab == 2) AccentGold else TextMuted
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading && allCandidates.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(bottom = 0.dp),
                    color = AccentGold,
                    trackColor = Color.Transparent
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isLoading && allCandidates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AccentGold,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        val loadingText = when (mediaType) {
                            MediaType.NOVEL -> "Querying RanobeDB, AniList, Kitsu, BookWalker, Naver, Kuaikan & Bangumi..."
                            MediaType.MANGA -> "Querying AniList, MangaDex, BookWalker, Naver, Kuaikan, Bangumi & Kitsu..."
                            MediaType.ANIME -> "Querying AniList, TMDb, Bangumi, Kitsu & MangaDex..."
                            MediaType.SHOW, MediaType.MOVIE -> "Querying TMDb, TVMaze & Bangumi..."
                            MediaType.GAME -> "Querying Steam, IGDB & SteamGridDB..."
                            MediaType.VISUAL_NOVEL -> "Querying VNDB, Steam, AniList, IGDB, SteamGridDB & Bangumi..."
                            MediaType.BOOK -> "Querying Open Library, Hardcover, Apple Books, Google Books & Wikimedia..."
                            MediaType.COMIC -> "Querying Comic Vine, Metron, Apple Books & Open Library..."
                            MediaType.FANFICTION -> "Querying AniList, TMDb, Bangumi, SteamGridDB & Open Library for fandom art..."
                            else -> "Querying artwork providers..."
                        }
                        Text(
                            text = loadingText,
                            color = TextMuted,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                when (selectedTab) {
                    // TAB 0: COVERS
                    0 -> {
                        val latestLnCover = remember(displayedCovers, mediaType) {
                            if (mediaType != MediaType.NOVEL) null
                            else displayedCovers.filter { it.sourceName.equals("RanobeDB", ignoreCase = true) }
                                .mapNotNull { option ->
                                    val volStr = option.description?.substringAfter("Vol. ", "")?.substringBefore(" ")?.trim()
                                    val volNum = volStr?.toDoubleOrNull()
                                    if (volNum != null) volNum to option else null
                                }
                                .maxByOrNull { it.first }?.second
                                ?: displayedCovers.firstOrNull { it.sourceName.equals("RanobeDB", ignoreCase = true) }
                        }
                        val latestMdCover = remember(displayedCovers) {
                            displayedCovers.filter { it.sourceName.equals("MangaDex", ignoreCase = true) }
                                .mapNotNull { option ->
                                    val volStr = option.description?.substringAfter("Vol. ", "")?.substringBefore(" ")?.trim()
                                    val volNum = volStr?.toDoubleOrNull()
                                    if (volNum != null) volNum to option else null
                                }
                                .maxByOrNull { it.first }?.second
                                ?: displayedCovers.firstOrNull { it.sourceName.equals("MangaDex", ignoreCase = true) }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            // Quick Action Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (latestLnCover != null) {
                                    Button(
                                        onClick = {
                                            onCoverSelected(latestLnCover.url)
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentGold,
                                            contentColor = AmoledBlack
                                        )
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = latestLnCover.description
                                                ?.takeIf { it.isNotBlank() }
                                                ?.let { "Latest LN ($it)" }
                                                ?: "Latest LN Vol",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (latestMdCover != null) {
                                    Button(
                                        onClick = {
                                            onCoverSelected(latestMdCover.url)
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentGold,
                                            contentColor = AmoledBlack
                                        )
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = latestMdCover.description
                                                ?.takeIf { it.isNotBlank() && it != "Official Cover" }
                                                ?.let { "Latest MD ($it)" }
                                                ?: "Latest MD Vol",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                FilledTonalButton(
                                    onClick = {
                                        coverPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = DarkSurfaceElevated,
                                        contentColor = AccentGold
                                    )
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick File", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        onResetCover()
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextSecondary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reset Auto", fontSize = 12.sp, color = TextSecondary)
                                }

                                OutlinedButton(
                                    onClick = { showDirectUrlInput = !showDirectUrlInput },
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (showDirectUrlInput) AccentGold else GlassBorder
                                    )
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(15.dp), tint = if (showDirectUrlInput) AccentGold else TextSecondary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Direct URL", fontSize = 12.sp, color = if (showDirectUrlInput) AccentGold else TextSecondary)
                                }
                            }

                            // Optional Direct URL input
                            AnimatedVisibility(visible = showDirectUrlInput) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customUrlInput,
                                        onValueChange = { customUrlInput = it },
                                        placeholder = { Text("https://.../cover.jpg", color = TextMuted, fontSize = 12.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AccentGold,
                                            unfocusedBorderColor = GlassBorder,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            if (customUrlInput.isNotBlank()) {
                                                onCoverSelected(customUrlInput.trim())
                                                onDismiss()
                                            }
                                        },
                                        enabled = customUrlInput.isNotBlank(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                                    ) {
                                        Text("Apply", color = AmoledBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (displayedCovers.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No candidate covers found online.\nUse 'Pick File' to choose custom artwork.",
                                        color = TextMuted,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 104.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    gridItemsIndexed(displayedCovers, key = { idx, item -> "${idx}_${item.url}" }) { _, candidate ->
                                        val isCurrent = candidate.url == currentCoverUrl

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(2f / 3f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(
                                                    width = if (isCurrent) 2.5.dp else 1.dp,
                                                    color = if (isCurrent) AccentGold else GlassBorder,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable {
                                                    onCoverSelected(candidate.url)
                                                    onDismiss()
                                                }
                                        ) {
                                            AsyncImage(
                                                model = candidate.url,
                                                contentDescription = candidate.description,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            // Compact source badge
                                            Box(
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .align(Alignment.TopStart)
                                                    .background(
                                                        color = Color(0xD912110E),
                                                        shape = RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = formatArtSourceBadgeLabel(candidate.sourceName),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            if (!candidate.description.isNullOrBlank()) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .fillMaxWidth()
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(Color.Transparent, AmoledBlack.copy(alpha = 0.88f))
                                                            )
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 5.dp)
                                                ) {
                                                    Text(
                                                        text = candidate.description,
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = TextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            if (isCurrent) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(AccentGold),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = AmoledBlack,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 1: BANNERS (Full-screen spacious scrollable workbench + live banner switch)
                    1 -> {
                        val bannerListState = rememberLazyListState()
                        var userPickedBannerFilter by remember { mutableStateOf<String?>(null) }
                        val supportsCleanBannerFilter = mediaType == MediaType.VISUAL_NOVEL || mediaType == MediaType.GAME || mediaType == MediaType.BOOK
                        val cleanBannerCount = remember(displayedBanners) { displayedBanners.count { !it.isScreenshot } }
                        val shotBannerCount = remember(displayedBanners) { displayedBanners.count { it.isScreenshot } }
                        val activeVnBannerFilter = userPickedBannerFilter ?: if (cleanBannerCount > 0) "CLEAN" else "ALL"
                        val visibleBanners = remember(displayedBanners, supportsCleanBannerFilter, activeVnBannerFilter) {
                            if (!supportsCleanBannerFilter) {
                                displayedBanners
                            } else when (activeVnBannerFilter) {
                                "CLEAN" -> displayedBanners.filter { !it.isScreenshot }
                                "SHOTS" -> displayedBanners.filter { it.isScreenshot }
                                else -> displayedBanners
                            }
                        }

                        LazyColumn(
                            state = bannerListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item(key = "banner_actions") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            bannerPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = DarkSurfaceElevated,
                                            contentColor = AccentGold
                                        )
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pick File", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            onBannerSelected(currentCoverUrl)
                                            if (showFramingEditor) {
                                                draftFocalX = 0f
                                                draftFocalY = 0f
                                                draftZoom = 1f
                                            } else {
                                                onDismiss()
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextSecondary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Use Cover", fontSize = 12.sp, color = TextSecondary)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            onBannerSelected("NONE")
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isBannerDisabled) AccentGold else GlassBorder
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Block,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = if (isBannerDisabled) AccentGold else TextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isBannerDisabled) "Enable Banner" else "Disable Banner",
                                            fontSize = 12.sp,
                                            color = if (isBannerDisabled) AccentGold else TextSecondary
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            onResetBanner()
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextSecondary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reset Auto", fontSize = 12.sp, color = TextSecondary)
                                    }

                                    OutlinedButton(
                                        onClick = { showDirectUrlInput = !showDirectUrlInput },
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (showDirectUrlInput) AccentGold else GlassBorder
                                        )
                                    ) {
                                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(15.dp), tint = if (showDirectUrlInput) AccentGold else TextSecondary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Direct URL", fontSize = 12.sp, color = if (showDirectUrlInput) AccentGold else TextSecondary)
                                    }

                                    if (!isBannerDisabled) {
                                        OutlinedButton(
                                            onClick = { showFramingEditor = !showFramingEditor },
                                            shape = RoundedCornerShape(10.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (showFramingEditor) AccentGold else GlassBorder
                                            )
                                        ) {
                                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp), tint = if (showFramingEditor) AccentGold else TextSecondary)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Reposition & Zoom", fontSize = 12.sp, color = if (showFramingEditor) AccentGold else TextPrimary)
                                        }
                                    }
                                }
                            }

                            if (showFramingEditor && !isBannerDisabled) {
                                item(key = "framing_editor") {
                                    val framingPreviewModel: Any? = remember(currentCustomBannerPath, currentBannerUrl, currentCoverUrl) {
                                        currentCustomBannerPath?.takeIf { it != "NONE" && it.isNotBlank() }?.let { path ->
                                            if (path.startsWith("http") || path.startsWith("content:")) path else java.io.File(path)
                                        } ?: currentBannerUrl?.takeIf { it.isNotBlank() } ?: currentCoverUrl.takeIf { it.isNotBlank() }
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(DarkSurfaceElevated)
                                            .border(0.8.dp, GlassBorder, RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(184.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clipToBounds()
                                                .background(DarkSurfaceCard)
                                                .border(1.dp, AccentGold.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                .pointerInput(Unit) {
                                                    awaitEachGesture {
                                                        val down = awaitFirstDown(requireUnconsumed = false)
                                                        down.consume()
                                                        do {
                                                            val event = awaitPointerEvent()
                                                            val zoomChange = event.calculateZoom()
                                                            val pan = event.calculatePan()
                                                            if (zoomChange != 1f || pan != Offset.Zero) {
                                                                val nextZoom = (draftZoom * zoomChange).coerceIn(1f, 2.5f)
                                                                val w = size.width.toFloat().coerceAtLeast(1f)
                                                                val h = size.height.toFloat().coerceAtLeast(1f)
                                                                val effZoom = nextZoom.coerceAtLeast(1f)
                                                                val sens = 1.8f / effZoom
                                                                draftZoom = nextZoom
                                                                draftFocalX = (draftFocalX - (pan.x / w) * sens).coerceIn(-1f, 1f)
                                                                draftFocalY = (draftFocalY - (pan.y / h) * sens).coerceIn(-1f, 1f)
                                                            }
                                                            event.changes.forEach { it.consume() }
                                                        } while (event.changes.any { it.pressed })
                                                    }
                                                }
                                        ) {
                                            if (framingPreviewModel != null) {
                                                AsyncImage(
                                                    model = framingPreviewModel,
                                                    contentDescription = "Banner Framing Preview",
                                                    contentScale = ContentScale.Crop,
                                                    alignment = BiasAlignment(draftFocalX.coerceIn(-1f, 1f), draftFocalY.coerceIn(-1f, 1f)),
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer {
                                                            val fx = draftFocalX.coerceIn(-1f, 1f)
                                                            val fy = draftFocalY.coerceIn(-1f, 1f)
                                                            val effZoom = draftZoom.coerceIn(1f, 2.5f)
                                                            scaleX = effZoom
                                                            scaleY = effZoom
                                                            translationX = -fx * size.width * (effZoom - 1f) * 0.5f
                                                            translationY = -fy * size.height * (effZoom - 1f) * 0.5f
                                                        }
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(8.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(AmoledBlack.copy(alpha = 0.78f))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "Drag to pan · Pinch to zoom",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = TextPrimary
                                                )
                                            }
                                        }

                                        // Synced X / Y controls
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(34.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("X", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AccentGold, modifier = Modifier.width(16.dp))
                                            Slider(
                                                value = draftFocalX,
                                                onValueChange = { draftFocalX = it },
                                                valueRange = -1f..1f,
                                                colors = SliderDefaults.colors(thumbColor = AccentGold, activeTrackColor = AccentGold),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text("Y", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AccentGold, modifier = Modifier.width(16.dp))
                                            Slider(
                                                value = draftFocalY,
                                                onValueChange = { draftFocalY = it },
                                                valueRange = -1f..1f,
                                                colors = SliderDefaults.colors(thumbColor = AccentGold, activeTrackColor = AccentGold),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(34.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Zoom", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                            Slider(
                                                value = draftZoom,
                                                onValueChange = { draftZoom = it },
                                                valueRange = 1f..2.5f,
                                                colors = SliderDefaults.colors(thumbColor = AccentGold, activeTrackColor = AccentGold),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = String.format(java.util.Locale.US, "%.1fx", draftZoom),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AccentGold
                                            )
                                        }

                                        // Quick snaps
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            listOf(
                                                "Left" to Triple(-1f, draftFocalY, draftZoom),
                                                "Center" to Triple(0f, 0f, draftZoom),
                                                "Right" to Triple(1f, draftFocalY, draftZoom),
                                                "Top" to Triple(draftFocalX, -1f, draftZoom),
                                                "Bottom" to Triple(draftFocalX, 1f, draftZoom)
                                            ).forEach { (lbl, target) ->
                                                OutlinedButton(
                                                    onClick = {
                                                        draftFocalX = target.first
                                                        draftFocalY = target.second
                                                        draftZoom = target.third
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                                                ) {
                                                    Text(lbl, fontSize = 11.sp, color = TextPrimary)
                                                }
                                            }
                                        }

                                        // Reset & Apply Framing row
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    draftFocalX = 0f
                                                    draftFocalY = 0f
                                                    draftZoom = 1f
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Reset", fontSize = 12.sp, color = TextPrimary)
                                            }
                                            Button(
                                                onClick = {
                                                    onBannerFramingChanged(draftFocalX, draftFocalY, draftZoom)
                                                    showFramingEditor = false
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = AmoledBlack),
                                                modifier = Modifier.weight(1.6f)
                                            ) {
                                                Text("Apply Framing", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            if (showDirectUrlInput) {
                                item(key = "direct_url") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customUrlInput,
                                            onValueChange = { customUrlInput = it },
                                            placeholder = { Text("https://.../banner.jpg", color = TextMuted, fontSize = 12.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AccentGold,
                                                unfocusedBorderColor = GlassBorder,
                                                focusedTextColor = TextPrimary,
                                                unfocusedTextColor = TextPrimary
                                            )
                                        )
                                        Button(
                                            onClick = {
                                                if (customUrlInput.isNotBlank()) {
                                                    onBannerSelected(customUrlInput.trim())
                                                    if (showFramingEditor) {
                                                        draftFocalX = 0f
                                                        draftFocalY = 0f
                                                        draftZoom = 1f
                                                    } else {
                                                        onDismiss()
                                                    }
                                                }
                                            },
                                            enabled = customUrlInput.isNotBlank(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                                        ) {
                                            Text("Apply", color = AmoledBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            if ((supportsCleanBannerFilter && displayedBanners.isNotEmpty()) || showFramingEditor) {
                                item(key = "banner_filter_tabs") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (supportsCleanBannerFilter && displayedBanners.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                                            ) {
                                                listOf(
                                                    "CLEAN" to if (mediaType == MediaType.BOOK) "Wide Art ($cleanBannerCount)" else "Clean Art ($cleanBannerCount)",
                                                    "ALL" to "All (${displayedBanners.size})",
                                                    "SHOTS" to if (mediaType == MediaType.BOOK) "Illustrations ($shotBannerCount)" else "Screenshots ($shotBannerCount)"
                                                ).forEach { (key, label) ->
                                                    val selected = activeVnBannerFilter == key
                                                    Column(
                                                        modifier = Modifier
                                                            .width(IntrinsicSize.Max)
                                                            .clickable { userPickedBannerFilter = key },
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            color = if (selected) Color.White else Color.White.copy(alpha = 0.50f),
                                                            fontSize = 12.sp,
                                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(2.dp)
                                                                .background(if (selected) Color.White else Color.Transparent)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (showFramingEditor && visibleBanners.isNotEmpty()) {
                                            Text(
                                                text = "More Banners (${visibleBanners.size}) · Tap to preview in reframe editor",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AccentGold
                                            )
                                        }
                                    }
                                }
                            }

                            if (visibleBanners.isEmpty()) {
                                item(key = "banner_empty") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (supportsCleanBannerFilter && displayedBanners.isNotEmpty()) {
                                                    "No clean widescreen art found — try the Screenshots tab."
                                                } else {
                                                    "No dedicated widescreen banners found online."
                                                },
                                                color = TextMuted,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = "You can tap 'Use Cover' to generate a banner or 'Pick File' for custom fanart.",
                                                color = TextSecondary,
                                                fontSize = 11.5.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                itemsIndexed(visibleBanners, key = { idx, item -> "${idx}_${item.url}" }) { _, candidate ->
                                    val isCurrent = candidate.url == currentBannerUrl

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 7f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                width = if (isCurrent) 2.5.dp else 1.dp,
                                                color = if (isCurrent) AccentGold else GlassBorder,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                if (showFramingEditor) {
                                                    onBannerSelected(candidate.url)
                                                    draftFocalX = 0f
                                                    draftFocalY = 0f
                                                    draftZoom = 1f
                                                    scope.launch { bannerListState.animateScrollToItem(1) }
                                                } else {
                                                    onBannerSelected(candidate.url)
                                                    onDismiss()
                                                }
                                            }
                                    ) {
                                        AsyncImage(
                                            model = candidate.url,
                                            contentDescription = candidate.description,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Compact source badge
                                        Box(
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .align(Alignment.TopStart)
                                                .background(
                                                    color = Color(0xD912110E),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = formatArtSourceBadgeLabel(candidate.sourceName),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (!candidate.description.isNullOrBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(Color.Transparent, AmoledBlack.copy(alpha = 0.86f))
                                                        )
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = candidate.description,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        if (isCurrent) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(AccentGold),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = AmoledBlack,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 2: EXCLUSIONS
                    2 -> {
                        val availableSources = remember(allCandidates) {
                            val standard = listOf(
                                "Open Library", "Hardcover", "Apple Books", "Wikimedia Commons", "Google Books",
                                "RanobeDB", "AniList", "MangaDex", "BookWalker", "Naver", "Kuaikan",
                                "Bangumi", "Kitsu", "MyAnimeList", "Comick", "TMDb",
                                "Steam", "Steam Hero", "Steam Background", "SteamGridDB",
                                "IGDB", "IGDB Artwork", "TVMaze", "VNDB", "VNDB Edition", "VNDB Screenshot"
                            )
                            val discovered = allCandidates
                                .map { it.sourceName }
                                .filter { it.isNotBlank() && !it.equals("Default", ignoreCase = true) }
                            val merged = (standard + discovered)
                                .distinctBy { it.uppercase() }
                            listOf("ALL" to "All Remote Sources (Keep Default only)") +
                                merged.map { it.uppercase() to it }
                        }
                        val allExcluded = "ALL" in excludedSources
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Artwork Sources for this entry",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Toggle off any provider to hide its covers and banners from discovery.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(availableSources, key = { it.first }) { (sourceKey, displayLabel) ->
                                    val isMasterAll = sourceKey == "ALL"
                                    val isExcluded = if (isMasterAll) {
                                        allExcluded
                                    } else {
                                        allExcluded || sourceKey in excludedSources
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(DarkSurfaceElevated)
                                            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = displayLabel,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = if (isMasterAll) FontWeight.SemiBold else FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Switch(
                                            checked = if (isMasterAll) allExcluded else !isExcluded,
                                            enabled = isMasterAll || !allExcluded,
                                            onCheckedChange = { checked ->
                                                val next = excludedSources.toMutableSet()
                                                if (isMasterAll) {
                                                    if (checked) next.add("ALL") else next.remove("ALL")
                                                } else {
                                                    if (checked) next.remove(sourceKey) else next.add(sourceKey)
                                                }
                                                excludedSources = next
                                                onExcludedSourcesChanged(next)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = AmoledBlack,
                                                checkedTrackColor = AccentGold,
                                                uncheckedThumbColor = TextSecondary,
                                                uncheckedTrackColor = DarkSurfaceCard
                                            )
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
}
}
