package com.omnitrack.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ArtworkPriorityScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefManager = remember(context) {
        (context.applicationContext as OmniTrackApplication).preferenceManager
    }
    val coroutineScope = rememberCoroutineScope()

    val categories = remember {
        listOf(
            MediaType.ANIME,
            MediaType.MANGA,
            MediaType.COMIC,
            MediaType.NOVEL,
            MediaType.FANFICTION,
            MediaType.BOOK,
            MediaType.SHOW,
            MediaType.MOVIE,
            MediaType.GAME,
            MediaType.VISUAL_NOVEL
        )
    }

    val pagerState = rememberPagerState(initialPage = 0) { categories.size }
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar: Circle-free pure white back arrow + Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
            ) {
                Text(
                    text = "Artwork & Metadata Sources",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "Dynamic waterfall & uncompressed cover priority",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            Text(
                text = "Reset",
                color = AccentGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        val activeCat = categories[pagerState.currentPage]
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        coroutineScope.launch {
                            prefManager.resetArtSourcePriority(activeCat)
                            Toast.makeText(
                                context,
                                "Reset ${activeCat.displayName} art sources to recommended order",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        // Horizontal Category Tabs (Synchronized auto-scroll with pager swiping)
        val tabListState = rememberLazyListState()

        // Auto-scroll the tab bar when pager swipes so current tab and next tab remain in view
        LaunchedEffect(pagerState.currentPage) {
            tabListState.animateScrollToItem(
                index = pagerState.currentPage.coerceAtLeast(0),
                scrollOffset = 0
            )
        }

        LazyRow(
            state = tabListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(
                items = categories,
                key = { _, mediaType -> mediaType.name }
            ) { index, mediaType ->
                val isSelected = pagerState.currentPage == index
                val tabTextColor by animateColorAsState(
                    targetValue = if (isSelected) TextPrimary else TextMuted,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "tabText"
                )
                val indicatorColor by animateColorAsState(
                    targetValue = if (isSelected) AccentGold else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "tabIndicator"
                )

                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = mediaType.displayName,
                        color = tabTextColor,
                        fontSize = 13.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(indicatorColor)
                    )
                }
            }
        }
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            thickness = 0.6.dp
        )

        // Subtitle instructions row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "Drag ≡ on the right to reorder. Tap the eye to enable or disable fallbacks.",
                color = TextMuted,
                fontSize = 11.5.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Horizontal Category Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val category = categories[page]
            CategoryWaterfallList(
                category = category,
                prefManager = prefManager
            )
        }
    }
}

@Composable
private fun CategoryWaterfallList(
    category: MediaType,
    prefManager: PreferenceManager
) {
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val initialSources by prefManager.getArtSourcePriority(category)
        .collectAsState(initial = null)
    val disabledSources by prefManager.getDisabledArtSources(category)
        .collectAsState(initial = emptySet())

    val currentSources = remember(category) { mutableStateListOf<String>() }
    var initialized by remember(category) { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    var isDraggingAny by remember { mutableStateOf(false) }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = currentSources.indexOfFirst { it == from.key }
        val toIdx = currentSources.indexOfFirst { it == to.key }
        if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
            currentSources.apply { add(toIdx, removeAt(fromIdx)) }
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    LaunchedEffect(initialSources) {
        val loaded = initialSources ?: return@LaunchedEffect
        if (!initialized) {
            currentSources.clear()
            currentSources.addAll(loaded)
            initialized = true
        } else if (!isDraggingAny && currentSources.toList() != loaded) {
            currentSources.clear()
            currentSources.addAll(loaded)
        }
    }

    if (!initialized) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(
            items = currentSources,
            key = { _, source -> source }
        ) { idx, source ->
            val isEnabled = source !in disabledSources
            val displayRank = when {
                !isEnabled -> "OFF"
                idx == 0 -> "#1"
                else -> "#${idx + 1}"
            }

            ReorderableItem(reorderableState, key = source) { isDragging ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 10f else 1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDragging) DarkSurfaceElevated else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (isDragging) AccentGold.copy(alpha = 0.65f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .drawBehind {
                            if (!isDragging) {
                                val strokeW = 1.dp.toPx()
                                drawLine(
                                    color = Color.White.copy(alpha = 0.08f),
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = strokeW
                                )
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Eye Icon Toggle (Enable / Disable in waterfall)
                    IconButton(
                        onClick = {
                            val newDisabled = if (isEnabled) {
                                disabledSources + source
                            } else {
                                disabledSources - source
                            }
                            coroutineScope.launch {
                                prefManager.updateDisabledArtSources(category, newDisabled)
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isEnabled) "Disable Source" else "Enable Source",
                            tint = if (isEnabled) AccentGold else TextMuted.copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Rank Badge Pill (Primary vs Fallback)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    !isEnabled -> Color.White.copy(alpha = 0.04f)
                                    idx == 0 -> AccentGold.copy(alpha = 0.18f)
                                    isDragging -> AccentGold.copy(alpha = 0.24f)
                                    else -> Color.White.copy(alpha = 0.06f)
                                }
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = displayRank,
                            color = when {
                                !isEnabled -> TextMuted
                                idx == 0 || isDragging -> AccentGold
                                else -> TextSecondary
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Center: Source Title & Provider Description
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = source,
                            color = if (isEnabled) TextPrimary else TextMuted.copy(alpha = 0.65f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (!isEnabled) {
                                "Disabled — bypassed in artwork waterfall"
                            } else {
                                getSourceDescription(source)
                            },
                            color = if (isEnabled) TextSecondary else TextMuted.copy(alpha = 0.50f),
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Right: ≡ Drag Handle
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .draggableHandle(
                                onDragStarted = {
                                    isDraggingAny = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    isDraggingAny = false
                                    coroutineScope.launch {
                                        prefManager.updateArtSourcePriority(category, currentSources.toList())
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Reorder",
                            tint = if (isDragging) AccentGold else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun getSourceDescription(source: String): String = when (source) {
    "MangaDex API" -> "Lossless original manga tankōbon & magazine artwork"
    "AniList GraphQL" -> "High-definition key visuals & widescreen banners"
    "Bangumi" -> "Comprehensive Japanese anime/manga/game community catalog"
    "Comick" -> "Webtoon, manhwa, and manga high-res covers"
    "BookWalker" -> "Official high-resolution Japanese digital storefront covers"
    "Naver" -> "Official Korean webtoon vertical covers & widescreen banners"
    "Kuaikan" -> "Official Chinese manhua covers & banners"
    "RanobeDB" -> "Light novel edition cover art & Japanese volume database"
    "MyAnimeList" -> "Classic anime & manga cover archive"
    "TMDb" -> "Cinema posters, official backdrops, and TV series banners"
    "TVMaze" -> "Episodic metadata and episodic stills"
    "TheTVDB" -> "Season posters, fanart backdrops, and character art"
    "RPDB" -> "High-resolution posters with rating badge overlays"
    "Apple Books" -> "1200×1200 high-resolution epub & comic store cover art"
    "Hardcover" -> "Modern reader-curated high-res book cover artwork"
    "Open Library" -> "Archive edition covers across print runs"
    "Wikimedia Commons" -> "Public-domain classic illustrations & literary artwork"
    "Google Books" -> "Fallback book edition covers"
    "Comic Vine" -> "Official comic issue covers & series key visuals"
    "Metron" -> "Structured DC/Marvel/Indie comic issue cover database"
    "SteamGridDB" -> "Custom 2:3 grids, widescreen hero art & logos"
    "IGDB" -> "Twitch/IGDB high-resolution game box art & screenshots"
    "Steam" -> "Steam Store official headers, hero artwork & 2x library art"
    "VNDB Kana" -> "Visual Novel Database official visual novel CGs & covers"
    "NovelUpdates" -> "Light novel and web novel translated cover catalog"
    "Kitsu" -> "Alternative anime/manga fallback database"
    else -> "Upstream uncompressed artwork provider"
}
