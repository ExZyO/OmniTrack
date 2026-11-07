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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.launch

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
    onDismiss: () -> Unit,
    onCoverSelected: (String) -> Unit,
    onBannerSelected: (String) -> Unit = {},
    onResetCover: () -> Unit = {},
    onResetBanner: () -> Unit = {},
    onRoleSelected: ((String) -> Unit)? = null,
    repository: ArtworkWaterfallRepository = remember { ArtworkWaterfallRepository() },
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coverCache = remember { (context.applicationContext as? com.omnitrack.app.OmniTrackApplication)?.coverCache ?: CoverCache(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var allCandidates by remember { mutableStateOf<List<ArtworkCandidate>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var excludedSources by remember { mutableStateOf<Set<String>>(emptySet()) }
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
                sheetState.hide()
            }.invokeOnCompletion { onDismiss() }
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
                sheetState.hide()
            }.invokeOnCompletion { onDismiss() }
        }
    }

    val fetchCandidates = {
        scope.launch {
            isLoading = true
            allCandidates = repository.searchArtworkCandidates(title, mediaType)
            isLoading = false
        }
    }

    LaunchedEffect(title, mediaType) {
        fetchCandidates()
    }

    // Filter covers and banners according to exclusions
    val displayedCovers = remember(allCandidates, excludedSources) {
        allCandidates.filter { !it.isBanner && it.sourceName.uppercase() !in excludedSources }
    }

    val displayedBanners = remember(allCandidates, excludedSources) {
        allCandidates.filter { it.isBanner && it.sourceName.uppercase() !in excludedSources }
    }

    val isBannerDisabled = currentCustomBannerPath == "NONE"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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
                        onClick = { fetchCandidates() },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Candidates",
                            tint = if (isLoading) TextMuted else AccentArctic
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                        }
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
                contentColor = AccentArctic,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = AccentArctic
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
                            color = if (selectedTab == 0) AccentArctic else TextMuted
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
                            color = if (selectedTab == 1) AccentArctic else TextMuted
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
                            color = if (selectedTab == 2) AccentArctic else TextMuted
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
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
                            color = AccentArctic,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Querying AniList, MangaDex, Bangumi & Steam...",
                            color = TextMuted,
                            fontSize = 12.5.sp
                        )
                    }
                }
            } else {
                when (selectedTab) {
                    // TAB 0: COVERS
                    0 -> {
                        Column(modifier = Modifier.weight(1f)) {
                            // Quick Action Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        coverPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = DarkSurfaceElevated,
                                        contentColor = AccentArctic
                                    )
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick File", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        onResetCover()
                                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
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
                                        if (showDirectUrlInput) AccentArctic else GlassBorder
                                    )
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(15.dp), tint = if (showDirectUrlInput) AccentArctic else TextSecondary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Direct URL", fontSize = 12.sp, color = if (showDirectUrlInput) AccentArctic else TextSecondary)
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
                                            focusedBorderColor = AccentArctic,
                                            unfocusedBorderColor = GlassBorder,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            if (customUrlInput.isNotBlank()) {
                                                onCoverSelected(customUrlInput.trim())
                                                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                                            }
                                        },
                                        enabled = customUrlInput.isNotBlank(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentArctic)
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
                                    items(displayedCovers, key = { it.url }) { candidate ->
                                        val isCurrent = candidate.url == currentCoverUrl

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(2f / 3f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(
                                                    width = if (isCurrent) 2.5.dp else 1.dp,
                                                    color = if (isCurrent) AccentJade else GlassBorder,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable {
                                                    onCoverSelected(candidate.url)
                                                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                                                }
                                        ) {
                                            AsyncImage(
                                                model = candidate.url,
                                                contentDescription = candidate.description,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            // Top pill source badge
                                            Box(
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .align(Alignment.TopStart)
                                                    .background(
                                                        color = AmoledBlack.copy(alpha = 0.76f),
                                                        shape = RoundedCornerShape(999.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = candidate.sourceName,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                            }

                                            if (isCurrent) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(AccentJade),
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

                    // TAB 1: BANNERS
                    1 -> {
                        Column(modifier = Modifier.weight(1f)) {
                            // Quick Action Chips for Banners
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
                                        contentColor = AccentArctic
                                    )
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick File", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        onBannerSelected(currentCoverUrl)
                                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
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
                                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
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
                                        text = if (isBannerDisabled) "Banner Disabled" else "Disable Banner",
                                        fontSize = 12.sp,
                                        color = if (isBannerDisabled) AccentGold else TextSecondary
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        onResetBanner()
                                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
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
                                        if (showDirectUrlInput) AccentArctic else GlassBorder
                                    )
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(15.dp), tint = if (showDirectUrlInput) AccentArctic else TextSecondary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Direct URL", fontSize = 12.sp, color = if (showDirectUrlInput) AccentArctic else TextSecondary)
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
                                        placeholder = { Text("https://.../banner.jpg", color = TextMuted, fontSize = 12.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AccentArctic,
                                            unfocusedBorderColor = GlassBorder,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            if (customUrlInput.isNotBlank()) {
                                                onBannerSelected(customUrlInput.trim())
                                                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                                            }
                                        },
                                        enabled = customUrlInput.isNotBlank(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentArctic)
                                    ) {
                                        Text("Apply", color = AmoledBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (displayedBanners.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "No dedicated widescreen banners found online.",
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
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(bottom = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(displayedBanners, key = { it.url }) { candidate ->
                                        val isCurrent = candidate.url == currentBannerUrl

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 7f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(
                                                    width = if (isCurrent) 2.5.dp else 1.dp,
                                                    color = if (isCurrent) AccentArctic else GlassBorder,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    onBannerSelected(candidate.url)
                                                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                                                }
                                        ) {
                                            AsyncImage(
                                                model = candidate.url,
                                                contentDescription = candidate.description,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            // Top pill source badge
                                            Box(
                                                modifier = Modifier
                                                    .padding(8.dp)
                                                    .align(Alignment.TopStart)
                                                    .background(
                                                        color = AmoledBlack.copy(alpha = 0.76f),
                                                        shape = RoundedCornerShape(999.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "${candidate.sourceName} Banner",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                            }

                                            if (isCurrent) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(8.dp)
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(AccentArctic),
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
                    }

                    // TAB 2: EXCLUSIONS
                    2 -> {
                        val availableSources = listOf("AniList", "MangaDex", "Bangumi", "Kitsu", "Steam", "TVMaze", "VNDB")
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
                                color = TextMuted,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(availableSources) { source ->
                                    val isExcluded = source.uppercase() in excludedSources

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
                                            text = source,
                                            color = if (isExcluded) TextMuted else TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Switch(
                                            checked = !isExcluded,
                                            onCheckedChange = { isEnabled ->
                                                val next = excludedSources.toMutableSet()
                                                if (isEnabled) {
                                                    next.remove(source.uppercase())
                                                } else {
                                                    next.add(source.uppercase())
                                                }
                                                excludedSources = next
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = AmoledBlack,
                                                checkedTrackColor = AccentArctic,
                                                uncheckedThumbColor = TextMuted,
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
