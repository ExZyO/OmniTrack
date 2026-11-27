package com.omnitrack.app.ui.screens.home.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed as lazyRowItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.artwork.ArtworkModelResolver
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.enrichment.WatchProvidersAndAwardsClient
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.data.search.StreamingProviderChip
import com.omnitrack.app.ui.components.NuvioRailPosterCard
import com.omnitrack.app.ui.screens.detail.EphemeralPreviewHolder
import com.omnitrack.app.ui.screens.detail.components.WatchRegionPickerSheet
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

@Composable
fun DailyCriticsPickCard(
    item: SearchResultItem,
    libraryMatch: MediaCardSummary?,
    onOpenDetails: () -> Unit,
    onAddToLibrary: () -> Unit,
    mediumLabel: String? = null,
    horizontalPadding: Dp = 16.dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .height(176.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceElevated)
            .border(0.7.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .clickable { onOpenDetails() }
    ) {
        val coverModel = remember(libraryMatch?.customCoverPath, libraryMatch?.coverUrl, item.coverUrl) {
            ArtworkModelResolver.toCoilModel(libraryMatch?.let { ArtworkModelResolver.preferredCover(it) } ?: item.coverUrl)
        }
        val bannerDisabled = libraryMatch != null && ArtworkModelResolver.isBannerDisabled(libraryMatch)
        val backdrop = remember(
            libraryMatch?.customBannerPath,
            libraryMatch?.bannerUrl,
            libraryMatch?.artworkRole,
            libraryMatch?.coverUrl,
            item.bannerUrl,
            item.coverUrl,
            bannerDisabled
        ) {
            if (bannerDisabled) null
            else ArtworkModelResolver.toCoilModel(
                libraryMatch?.let { ArtworkModelResolver.preferredBanner(it) }
                    ?: item.bannerUrl?.takeIf { it.isNotBlank() && it != item.coverUrl && !ArtworkModelResolver.isAutoScreenshotUrl(it) }
                    ?: libraryMatch?.let { ArtworkModelResolver.preferredCover(it) }
                    ?: item.coverUrl
            )
        }
        AsyncImage(
            model = backdrop,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.45f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.94f)
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = coverModel,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(74.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(DarkSurfaceElevated)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (!mediumLabel.isNullOrBlank()) "DAILY CRITICS' PICK · ${mediumLabel.uppercase(Locale.US)}" else "DAILY CRITICS' PICK",
                        color = AccentGold,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    item.communityScore?.let { sc ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = AccentGold, modifier = Modifier.size(12.dp))
                            Text(
                                text = String.format(Locale.US, "%.1f", sc),
                                color = TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.synopsis?.let { syn ->
                    Text(
                        text = syn,
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (libraryMatch != null) Color.White.copy(alpha = 0.14f) else AccentGold)
                    .clickable {
                        if (libraryMatch != null) onOpenDetails() else onAddToLibrary()
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    text = if (libraryMatch != null) "Open" else "+ Library",
                    color = if (libraryMatch != null) TextPrimary else OnAccentColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun DiscoveryQueueLauncherCard(
    queueItems: List<SearchResultItem>,
    onStartSwiping: () -> Unit,
    horizontalPadding: Dp = 20.dp
) {
    val stack = queueItems.take(3)
    val backdropUrl = stack.firstOrNull()?.let { first ->
        first.bannerUrl?.takeIf { it.isNotBlank() && it != first.coverUrl && !ArtworkModelResolver.isAutoScreenshotUrl(it) }
            ?: first.coverUrl
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceElevated)
            .border(0.7.dp, AccentGold.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable(onClick = onStartSwiping)
    ) {
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.22f)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.72f),
                            1f to Color.Black.copy(alpha = 0.90f)
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(74.dp)
                    .height(66.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (stack.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 62.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(0.6.dp, GlassBorder, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewCarousel,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    stack.reversed().forEachIndexed { revIdx, pick ->
                        val frontOffsetIdx = stack.lastIndex - revIdx
                        val rotation = when (frontOffsetIdx) {
                            0 -> -6f
                            1 -> 2f
                            else -> 9f
                        }
                        val xDp = (frontOffsetIdx * 13).dp
                        AsyncImage(
                            model = pick.coverUrl.ifBlank { pick.bannerUrl },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(start = xDp)
                                .size(width = 42.dp, height = 60.dp)
                                .graphicsLayer {
                                    rotationZ = rotation
                                    alpha = if (frontOffsetIdx == 0) 1f else 0.78f
                                }
                                .clip(RoundedCornerShape(6.dp))
                                .background(AmoledBlack)
                                .border(0.7.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "DISCOVERY QUEUE",
                    color = AccentGold,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Quick-Sort New Picks",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (queueItems.isNotEmpty()) {
                        "${queueItems.size} tailored titles ready to swipe"
                    } else {
                        "Swipe recommendations right to queue"
                    },
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentGold)
                    .clickable(onClick = onStartSwiping)
                    .padding(horizontal = 11.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Start Swiping",
                    color = OnAccentColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun StreamingServiceDiscoverSection(
    providers: List<StreamingProviderChip>,
    selectedProviderId: Int?,
    regionPref: String,
    titles: List<SearchResultItem>,
    isLoadingTitles: Boolean,
    allMedia: List<MediaCardSummary>,
    onSelectProvider: (Int) -> Unit,
    onSelectRegionPref: (String) -> Unit,
    onItemClick: (Long) -> Unit,
    horizontalPadding: Dp = 16.dp
) {
    if (providers.isEmpty() && titles.isEmpty()) return
    var showRegionPicker by remember { mutableStateOf(false) }

    val effectiveRegion = if (regionPref.equals("AUTO", ignoreCase = true) || regionPref.isBlank()) {
        PreferenceManager.resolveEffectiveWatchRegion()
    } else {
        regionPref.trim().uppercase(Locale.US)
    }
    val flag = WatchProvidersAndAwardsClient.flagForCountry(effectiveRegion)
    val regionLabel = if (regionPref.equals("AUTO", ignoreCase = true) || regionPref.isBlank()) {
        "$flag Auto ($effectiveRegion)"
    } else {
        "$flag $effectiveRegion"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Browse by Streaming Service",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(0.7.dp, GlassBorder, RoundedCornerShape(6.dp))
                    .clickable { showRegionPicker = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(regionLabel, color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
            }
        }

        if (providers.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                lazyRowItemsIndexed(providers, key = { _, p -> "prov_${p.providerId}" }) { _, prov ->
                    val isSelected = prov.providerId == selectedProviderId
                    Column(
                        modifier = Modifier
                            .width(56.dp)
                            .clickable { onSelectProvider(prov.providerId) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated)
                                .border(
                                    width = if (isSelected) 1.8.dp else 0.6.dp,
                                    color = if (isSelected) AccentGold else Color.White.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = prov.logoUrl,
                                contentDescription = prov.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                        Text(
                            text = prov.name,
                            color = if (isSelected) AccentGold else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (isLoadingTitles && titles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentGold, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        } else if (titles.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                lazyRowItemsIndexed(
                    titles,
                    key = { idx, item -> "stream_${selectedProviderId}_${idx}_${item.tmdbId}_${item.title}" }
                ) { _, item ->
                    val inLib = ArtworkModelResolver.findLibraryMatch(
                        library = allMedia,
                        mediaType = item.mediaType,
                        anilistId = item.anilistId,
                        malId = item.malId,
                        tmdbId = item.tmdbId,
                        vndbId = item.vndbId,
                        openLibraryId = item.openLibraryId,
                        title = item.title
                    )
                    NuvioRailPosterCard(
                        title = item.title,
                        coverUrl = inLib?.let { ArtworkModelResolver.preferredCover(it) } ?: item.coverUrl,
                        subtitle = item.releaseYear ?: item.mediaType.displayName,
                        rating = item.communityScore,
                        inLibraryBadge = inLib != null,
                        onClick = {
                            if (inLib != null) {
                                onItemClick(inLib.id)
                            } else {
                                EphemeralPreviewHolder.previewItem = item.toMediaItemEntity()
                                onItemClick(-1L)
                            }
                        },
                        cardWidth = 114.dp
                    )
                }
            }
        }
    }

    if (showRegionPicker) {
        WatchRegionPickerSheet(
            selectedRegionPref = regionPref,
            onSelectRegion = { chosen ->
                onSelectRegionPref(chosen)
                showRegionPicker = false
            },
            onDismiss = { showRegionPicker = false }
        )
    }
}

@Composable
fun Top10TodayDiscoverSection(
    items: List<SearchResultItem>,
    allMedia: List<MediaCardSummary>,
    onItemClick: (Long) -> Unit,
    horizontalPadding: Dp = 16.dp
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Top 10 Today",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            lazyRowItemsIndexed(items.take(10), key = { idx, item -> "top10_${idx}_${item.tmdbId}_${item.title}" }) { idx, item ->
                val inLib = ArtworkModelResolver.findLibraryMatch(
                    library = allMedia,
                    mediaType = item.mediaType,
                    anilistId = item.anilistId,
                    malId = item.malId,
                    tmdbId = item.tmdbId,
                    openLibraryId = item.openLibraryId,
                    title = item.title
                )
                Box(modifier = Modifier.width(134.dp)) {
                    Box(modifier = Modifier.padding(start = 20.dp)) {
                        NuvioRailPosterCard(
                            title = item.title,
                            coverUrl = inLib?.customCoverPath ?: inLib?.coverUrl ?: item.coverUrl,
                            subtitle = item.releaseYear ?: item.mediaType.displayName,
                            rating = item.communityScore,
                            inLibraryBadge = inLib != null,
                            onClick = {
                                if (inLib != null) {
                                    onItemClick(inLib.id)
                                } else {
                                    EphemeralPreviewHolder.previewItem = item.toMediaItemEntity()
                                    onItemClick(-1L)
                                }
                            },
                            cardWidth = 114.dp
                        )
                    }
                    Text(
                        text = "${idx + 1}",
                        color = if (idx == 0) AccentGold else TextPrimary.copy(alpha = 0.92f),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 36.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TasteMatchedDiscoverSection(
    seed: MediaCardSummary?,
    items: List<SearchResultItem>,
    allMedia: List<MediaCardSummary>,
    onItemClick: (Long) -> Unit,
    horizontalPadding: Dp = 16.dp
) {
    if (seed == null || items.isEmpty()) return
    val scoreText = seed.userRating?.takeIf { it > 0f }?.let {
        " • ★ ${String.format(Locale.US, "%.1f", it)}"
    }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Because You Liked ${seed.title}$scoreText",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            lazyRowItemsIndexed(items, key = { idx, item -> "taste_${seed.id}_${idx}_${item.tmdbId}_${item.anilistId}_${item.title}" }) { _, item ->
                val inLib = ArtworkModelResolver.findLibraryMatch(
                    library = allMedia,
                    mediaType = item.mediaType,
                    anilistId = item.anilistId,
                    malId = item.malId,
                    tmdbId = item.tmdbId,
                    vndbId = item.vndbId,
                    openLibraryId = item.openLibraryId,
                    title = item.title
                )
                NuvioRailPosterCard(
                    title = item.title,
                    coverUrl = inLib?.let { ArtworkModelResolver.preferredCover(it) } ?: item.coverUrl,
                    subtitle = item.releaseYear ?: item.mediaType.displayName,
                    rating = item.communityScore,
                    inLibraryBadge = inLib != null,
                    onClick = {
                        if (inLib != null) {
                            onItemClick(inLib.id)
                        } else {
                            EphemeralPreviewHolder.previewItem = item.toMediaItemEntity()
                            onItemClick(-1L)
                        }
                    },
                    cardWidth = 114.dp
                )
            }
        }
    }
}

@Composable
fun CuratedFilterDiscoverSection(
    selectedRowKey: String,
    items: List<SearchResultItem>,
    isLoading: Boolean,
    allMedia: List<MediaCardSummary>,
    onSelectRow: (String) -> Unit,
    onItemClick: (Long) -> Unit,
    horizontalPadding: Dp = 16.dp
) {
    val tabs = remember {
        listOf(
            "under_90" to "Under 90 Min",
            "hidden_gems" to "Hidden Gems",
            "limited_series" to "Limited Series",
            "decade_2020s" to "2020s",
            "decade_2010s" to "2010s",
            "decade_2000s" to "2000s",
            "decade_1990s" to "90s"
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Curated Spotlight Lists",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            tabs.forEach { (key, label) ->
                val selected = key == selectedRowKey
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onSelectRow(key) }
                        .padding(top = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        color = if (selected) TextPrimary else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) AccentGold else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            thickness = 0.6.dp,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )

        if (isLoading && items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentGold, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        } else if (items.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                lazyRowItemsIndexed(items, key = { idx, item -> "curated_${selectedRowKey}_${idx}_${item.tmdbId}_${item.title}" }) { _, item ->
                    val inLib = ArtworkModelResolver.findLibraryMatch(
                        library = allMedia,
                        mediaType = item.mediaType,
                        anilistId = item.anilistId,
                        malId = item.malId,
                        tmdbId = item.tmdbId,
                        vndbId = item.vndbId,
                        openLibraryId = item.openLibraryId,
                        title = item.title
                    )
                    NuvioRailPosterCard(
                        title = item.title,
                        coverUrl = inLib?.let { ArtworkModelResolver.preferredCover(it) } ?: item.coverUrl,
                        subtitle = item.releaseYear ?: item.mediaType.displayName,
                        rating = item.communityScore,
                        inLibraryBadge = inLib != null,
                        onClick = {
                            if (inLib != null) {
                                onItemClick(inLib.id)
                            } else {
                                EphemeralPreviewHolder.previewItem = item.toMediaItemEntity()
                                onItemClick(-1L)
                            }
                        },
                        cardWidth = 114.dp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeDiscoveryQueueSheet(
    queueItems: List<SearchResultItem>,
    onAddPlanToWatch: (SearchResultItem) -> Unit,
    onSkip: (SearchResultItem) -> Unit,
    onOpenDetails: (SearchResultItem) -> Unit,
    onDismiss: () -> Unit,
    onUndoSkip: ((SearchResultItem) -> Unit)? = null,
    onUndoAdd: ((SearchResultItem) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { 105.dp.toPx() }
    val exitOffsetPx = with(density) { 460.dp.toPx() }
    val historyStack = remember { mutableStateListOf<Pair<SearchResultItem, Boolean>>() }

    val currentItem = queueItems.firstOrNull()
    val nextItem = queueItems.getOrNull(1)
    val itemKey = currentItem?.let {
        "${it.mediaType.name}_${it.tmdbId}_${it.anilistId}_${it.title.trim().lowercase()}"
    } ?: "empty"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DISCOVERY QUEUE",
                        color = AccentGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (queueItems.isNotEmpty()) "${queueItems.size} picks matched for you • Swipe left or right" else "Queue caught up",
                        color = TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            if (currentItem == null) {
                Text(
                    text = "You've reviewed all queued recommendations right now. Browse categories or change region to refresh.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 28.dp)
                )
                if (historyStack.isNotEmpty() && (onUndoSkip != null || onUndoAdd != null)) {
                    OutlinedButton(
                        onClick = {
                            val (lastItem, wasAdded) = historyStack.removeAt(historyStack.lastIndex)
                            if (wasAdded) {
                                onUndoAdd?.invoke(lastItem) ?: onUndoSkip?.invoke(lastItem)
                            } else {
                                onUndoSkip?.invoke(lastItem)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, GlassBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = AccentGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Undo Last Choice", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                key(itemKey) {
                    val offsetX = remember { Animatable(0f) }
                    var isCommitting by remember { mutableStateOf(false) }

                    fun triggerSwipe(dir: Int) {
                        if (isCommitting) return
                        isCommitting = true
                        scope.launch {
                            try {
                                offsetX.animateTo(
                                    targetValue = if (dir > 0) exitOffsetPx else -exitOffsetPx,
                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                )
                                historyStack.add(currentItem to (dir > 0))
                                if (dir > 0) onAddPlanToWatch(currentItem) else onSkip(currentItem)
                            } finally {
                                isCommitting = false
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(376.dp)
                    ) {
                        if (nextItem != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val progress = (abs(offsetX.value) / thresholdPx).coerceIn(0f, 1f)
                                        val s = 0.93f + 0.07f * progress
                                        scaleX = s
                                        scaleY = s
                                        translationY = (1f - progress) * 14.dp.toPx()
                                        alpha = 0.65f + 0.35f * progress
                                    }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(0.8.dp, GlassBorder, RoundedCornerShape(10.dp))
                            ) {
                                SwipeQueueDeckCardContent(item = nextItem)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = offsetX.value
                                    rotationZ = (offsetX.value / 28f).coerceIn(-12f, 12f)
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated)
                                .border(0.8.dp, GlassBorder, RoundedCornerShape(10.dp))
                                .pointerInput(itemKey) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            if (isCommitting) return@detectHorizontalDragGestures
                                            when {
                                                offsetX.value > thresholdPx -> triggerSwipe(1)
                                                offsetX.value < -thresholdPx -> triggerSwipe(-1)
                                                else -> scope.launch {
                                                    offsetX.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            if (!isCommitting) {
                                                scope.launch {
                                                    offsetX.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            if (!isCommitting) {
                                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                    offsetX.snapTo(offsetX.value + dragAmount)
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            SwipeQueueDeckCardContent(item = currentItem)

                            val addAlpha = (offsetX.value / thresholdPx).coerceIn(0f, 1f)
                            val skipAlpha = (-offsetX.value / thresholdPx).coerceIn(0f, 1f)

                            if (addAlpha > 0.02f) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(18.dp)
                                        .graphicsLayer {
                                            alpha = addAlpha
                                            rotationZ = -8f
                                        }
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.72f))
                                        .border(1.8.dp, AccentJade, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "PLAN TO WATCH",
                                        color = AccentJade,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }

                            if (skipAlpha > 0.02f) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(18.dp)
                                        .graphicsLayer {
                                            alpha = skipAlpha
                                            rotationZ = 8f
                                        }
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.72f))
                                        .border(1.8.dp, Color(0xFFEF4444), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "SKIP",
                                        color = Color(0xFFEF4444),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { triggerSwipe(-1) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, GlassBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Skip", color = TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (onUndoSkip != null || onUndoAdd != null) {
                            OutlinedButton(
                                onClick = {
                                    if (!isCommitting && historyStack.isNotEmpty()) {
                                        val (lastItem, wasAdded) = historyStack.removeAt(historyStack.lastIndex)
                                        if (wasAdded) {
                                            onUndoAdd?.invoke(lastItem) ?: onUndoSkip?.invoke(lastItem)
                                        } else {
                                            onUndoSkip?.invoke(lastItem)
                                        }
                                    }
                                },
                                enabled = historyStack.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (historyStack.isNotEmpty()) GlassBorder else Color.White.copy(alpha = 0.04f)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = if (historyStack.isNotEmpty()) TextPrimary else TextMuted.copy(alpha = 0.35f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenDetails(currentItem)
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, GlassBorder),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Details", tint = TextPrimary, modifier = Modifier.size(16.dp))
                        }
                        Button(
                            onClick = { triggerSwipe(1) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = OnAccentColor),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Plan to Watch", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeQueueDeckCardContent(item: SearchResultItem) {
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = item.coverUrl.ifBlank { item.bannerUrl },
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.48f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.94f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.mediaType.displayName.uppercase(Locale.US),
                    color = AccentGold,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
                item.releaseYear?.let {
                    Text(text = it, color = TextSecondary, fontSize = 11.5.sp)
                }
                item.communityScore?.let {
                    Text(
                        text = "★ ${String.format(Locale.US, "%.1f", it)}",
                        color = AccentGold,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = item.title,
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            item.synopsis?.let {
                Text(
                    text = it,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
