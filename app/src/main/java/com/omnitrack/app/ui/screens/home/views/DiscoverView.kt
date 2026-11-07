package com.omnitrack.app.ui.screens.home.views

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import com.omnitrack.app.data.preferences.PosterCardStyle
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.search.OmniSearchRepository
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.ui.components.MediaCard
import com.omnitrack.app.ui.screens.detail.EphemeralPreviewHolder
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
import kotlinx.coroutines.launch

@Composable
fun DiscoverView(
    allMedia: List<MediaCardSummary>,
    onItemClick: (Long) -> Unit,
    onAddMedia: (SearchResultItem) -> Unit,
    modifier: Modifier = Modifier,
    searchRepository: OmniSearchRepository = remember { OmniSearchRepository() },
    aiEngine: AiQueryEngine = remember { AiQueryEngine() }
) {
    val context = LocalContext.current
    val prefManager = remember { PreferenceManager(context) }
    val scope = rememberCoroutineScope()

    val geminiKey by prefManager.geminiApiKey.collectAsState(initial = "")
    val openRouterKey by prefManager.openRouterApiKey.collectAsState(initial = "")
    val tmdbKey by prefManager.tmdbApiKey.collectAsState(initial = "")
    val aiSearchSetting by prefManager.aiSemanticSearchEnabled.collectAsState(initial = false)
    val posterStyle by prefManager.posterCardStyle.collectAsState(initial = PosterCardStyle())

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(MediaType.ALL) }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var aiResolutionBanner by remember { mutableStateOf<String?>(null) }
    var useAiSearch by remember(aiSearchSetting) { mutableStateOf(aiSearchSetting) }

    // Multi-Vertical OmniSearch Trigger
    LaunchedEffect(searchQuery, selectedFilter, useAiSearch) {
        val trimmed = searchQuery.trim()
        if (trimmed.length >= 2) {
            delay(400)
            isSearching = true
            aiResolutionBanner = null
            try {
                var queryToRun = trimmed
                var filterToRun = selectedFilter

                if (useAiSearch) {
                    val aiResult = aiEngine.resolveQuery(
                        rawQuery = trimmed,
                        geminiApiKey = geminiKey.ifBlank { null },
                        openRouterApiKey = openRouterKey.ifBlank { null }
                    )
                    if (aiResult.isAiResolved) {
                        queryToRun = aiResult.resolvedTitle
                        aiResolutionBanner = "AI Resolved: \"${aiResult.resolvedTitle}\"" +
                            (aiResult.detectedType?.let { " (${it.displayName})" } ?: "") +
                            (aiResult.explanation?.let { " • $it" } ?: "")
                        if (selectedFilter == MediaType.ALL && aiResult.detectedType != null) {
                            filterToRun = aiResult.detectedType
                        }
                    } else if (aiResult.resolvedTitle != trimmed) {
                        queryToRun = aiResult.resolvedTitle
                        if (selectedFilter == MediaType.ALL && aiResult.detectedType != null) {
                            filterToRun = aiResult.detectedType
                        }
                    }
                }

                val results = searchRepository.searchAll(
                    query = queryToRun,
                    filter = filterToRun,
                    tmdbApiKey = tmdbKey.ifBlank { null }
                )
                searchResults = results
            } catch (_: Exception) {
                searchResults = emptyList()
            } finally {
                isSearching = false
            }
        } else {
            searchResults = emptyList()
            aiResolutionBanner = null
            isSearching = false
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
                onValueChange = { searchQuery = it },
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

            // Inline AI Toggle Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (useAiSearch) AccentArctic.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
                    .clickable { useAiSearch = !useAiSearch }
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
                    text = "AI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (useAiSearch) AccentArctic else TextMuted
                )
            }

            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { searchQuery = "" }
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
                        .clickable { selectedFilter = type }
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

        if (isSearching) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentArctic, strokeWidth = 2.dp)
            }
        } else if (searchResults.isNotEmpty()) {
            Text(
                text = "Results (${searchResults.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val effectiveDiscoverCardWidth = if (posterStyle.isLandscape) {
                (posterStyle.widthDp * (180f / 110f)).dp
            } else {
                posterStyle.widthDp.dp
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = effectiveDiscoverCardWidth),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 110.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(searchResults, key = { "${it.sourceProvider}_${it.mediaType}_${it.title}_${it.anilistId ?: it.bangumiId ?: it.tmdbId ?: it.igdbId ?: it.vndbId}" }) { item ->
                    val existingInLibrary = allMedia.firstOrNull { summary ->
                        summary.mediaType == item.mediaType && (
                            summary.title.equals(item.title, ignoreCase = true) ||
                            (item.nativeTitle != null && summary.nativeTitle.equals(item.nativeTitle, ignoreCase = true))
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
                        lastUpdated = System.currentTimeMillis()
                    )

                    MediaCard(
                        item = summary,
                        cornerRadiusDp = posterStyle.cornerRadiusDp,
                        isLandscape = posterStyle.isLandscape,
                        hideLabels = posterStyle.hideLabels,
                        badgeText = if (existingInLibrary != null) "IN LIBRARY" else item.sourceProvider,
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
        } else if (searchQuery.length >= 2) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No media found matching \"$searchQuery\"",
                    fontSize = 13.5.sp,
                    color = TextMuted
                )
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
            val effectiveDiscoverCardWidth = if (posterStyle.isLandscape) {
                (posterStyle.widthDp * (180f / 110f)).dp
            } else {
                posterStyle.widthDp.dp
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = effectiveDiscoverCardWidth),
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
                        onCardClick = { onItemClick(item.id) },
                        onQuickProgress = { onItemClick(item.id) }
                    )
                }
            }
        }
    }
}
