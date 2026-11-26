package com.omnitrack.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.preferences.LibraryDisplayMode
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

fun MediaItemEntity.toSummary(): MediaCardSummary = MediaCardSummary(
    id = id,
    title = title,
    nativeTitle = nativeTitle,
    coverUrl = coverUrl,
    bannerUrl = bannerUrl,
    clearartUrl = clearartUrl,
    mediaType = mediaType,
    status = status,
    currentProgress = currentProgress,
    totalProgress = totalProgress,
    userRating = userRating,
    customCoverPath = customCoverPath,
    customBannerPath = customBannerPath,
    artworkRole = artworkRole,
    communityScore = communityScore,
    lastUpdated = lastUpdated,
    imdbId = imdbId,
    tmdbId = tmdbId,
    anilistId = anilistId,
    malId = malId,
    originCountry = originCountry,
    platform = platform,
    runtimeMinutes = runtimeMinutes,
    playtimeHours = playtimeHours,
    platforms = platforms,
    steamAppId = steamAppId,
    gameCompletionTier = gameCompletionTier,
    openLibraryId = openLibraryId,
    bookAuthor = bookAuthor,
    bookFormat = bookFormat,
    bookTrackingMode = bookTrackingMode,
    bookAudioMinutes = bookAudioMinutes,
    bookTotalAudioMinutes = bookTotalAudioMinutes,
    comicVineId = comicVineId,
    metronId = metronId,
    comicPublisher = comicPublisher,
    comicFormat = comicFormat,
    comicTrackingMode = comicTrackingMode,
    fanficSourceSite = fanficSourceSite,
    fanficSourceUrl = fanficSourceUrl,
    fanficWorkId = fanficWorkId,
    fanficPrimaryFandom = fanficPrimaryFandom,
    fanficWordCount = fanficWordCount,
    fanficRating = fanficRating,
    fanficWorkStatus = fanficWorkStatus,
    fanficIsCrossover = fanficIsCrossover,
    fanficTagsSearch = fanficTagsSearch,
    isCustomEntry = isCustomEntry,
    bannerFocalX = bannerFocalX,
    bannerFocalY = bannerFocalY,
    bannerZoom = bannerZoom,
    imdbScore = imdbScore,
    rtScore = rtScore,
    metacriticScore = metacriticScore,
    traktScore = traktScore,
    audienceScore = audienceScore,
    traktId = traktId,
    simklId = simklId,
    vndbId = vndbId,
    hardcoverId = hardcoverId,
    bangumiId = bangumiId,
    comickId = comickId,
    studioOrNetwork = studioOrNetwork,
    isFavorite = isFavorite,
    igdbId = igdbId,
    linkedParentMediaId = linkedParentMediaId,
    linkedSeasonNumber = linkedSeasonNumber
)

val LocalCustomPosterPattern = androidx.compose.runtime.compositionLocalOf { "" }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCard(
    item: MediaCardSummary,
    onCardClick: () -> Unit,
    onQuickProgress: () -> Unit,
    modifier: Modifier = Modifier,
    onCardLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    cornerRadiusDp: Int = 12,
    isLandscape: Boolean = false,
    hideLabels: Boolean = false,
    cardDepthEnabled: Boolean = true,
    cardDepthEdgeStrength: Float = 50f,
    cardDepthSheenStrength: Float = 35f,
    cardDepthEdgeCoverage: Float = 100f,
    badgeText: String? = null,
    badgeColor: Color = AccentGold,
    showQuickProgressButton: Boolean = false,
    showLandscapeClearlogo: Boolean = true,
    displayMode: LibraryDisplayMode = LibraryDisplayMode.COMFORTABLE_GRID,
    showRemainingBadge: Boolean = false,
    showTypeBadge: Boolean = false,
    showScoreBadge: Boolean = false,
    isDenseGrid: Boolean = false,
    isDiscoverCard: Boolean = false
) {
    val perf = LocalPerformanceSettings.current
    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current
    val coverCache = remember(context) { (context.applicationContext as? com.omnitrack.app.OmniTrackApplication)?.coverCache }
    val inheritedPattern = LocalCustomPosterPattern.current

    val customPosterUrl = remember(item.id, item.tmdbId, item.imdbId, item.anilistId, item.malId, inheritedPattern, isLandscape) {
        if (inheritedPattern.isNotBlank()) {
            com.omnitrack.app.data.poster.CustomPosterUrlResolver.resolveForSummary(item, inheritedPattern, isLandscape)
        } else null
    }
    var customPosterFailed by remember(customPosterUrl) { mutableStateOf(false) }

    // In landscape mode, prioritize bannerUrl (16:9 backdrop) over portrait cover
    // CONTRACT: Manual customBannerPath / customCoverPath strictly takes top priority
    val downsampleCovers = perf.downsampleGridCovers
    val activeModel = remember(
        isLandscape,
        item.customBannerPath,
        item.bannerUrl,
        item.artworkRole,
        item.customCoverPath,
        item.coverUrl,
        customPosterUrl,
        customPosterFailed,
        downsampleCovers
    ) {
        if (isLandscape) {
            item.customBannerPath?.takeIf { it != "NONE" && it.isNotBlank() }?.let {
                com.omnitrack.app.data.artwork.ArtworkModelResolver.toCoilModel(it)
            }
                ?: if (!customPosterFailed && customPosterUrl != null) customPosterUrl
                else (item.bannerUrl?.takeIf { it.isNotBlank() } ?: item.coverUrl).let {
                    if (downsampleCovers) LosslessCoverMapper.toGridThumbnailUrl(it) else LosslessCoverMapper.toLosslessUrl(it)
                }
        } else {
            item.customCoverPath?.takeIf { it.isNotBlank() }?.let {
                com.omnitrack.app.data.artwork.ArtworkModelResolver.toCoilModel(it)
            }
                ?: if (!customPosterFailed && customPosterUrl != null) customPosterUrl
                else {
                    if (downsampleCovers) LosslessCoverMapper.toGridThumbnailUrl(item.coverUrl)
                    else LosslessCoverMapper.toLosslessUrl(item.coverUrl)
                }
        }
    }

    val isInitiallyInLibrary = badgeText == "IN LIBRARY" || (!isDiscoverCard && item.id > 0L)
    var isAddedToLibrary by remember(item.id, badgeText, isDiscoverCard) { mutableStateOf(isInitiallyInLibrary) }
    val isDiscoverAdd = isDiscoverCard && !isInitiallyInLibrary
    val showActionButton = isDiscoverAdd || showQuickProgressButton
    val showDiscoverCheck = isDiscoverAdd && isAddedToLibrary

    val bounceScale = if (showActionButton && !perf.disableCardAnimations) {
        remember { Animatable(1f) }
    } else null
    var showPlusOne by remember { mutableStateOf(false) }

    val pillBg = if (showDiscoverCheck) AccentGold.copy(alpha = 0.22f) else AccentGold

    val shape = RoundedCornerShape(cornerRadiusDp.dp)

    val cardRequest = remember(activeModel, isLandscape, isDenseGrid, downsampleCovers, perf.hardwareBitmaps, perf.disableCardAnimations) {
        if (activeModel == null) null
        else ImageRequest.Builder(context)
            .data(activeModel)
            .apply {
                if (downsampleCovers) {
                    val (wPx, hPx) = when {
                        isLandscape -> 640 to 360
                        isDenseGrid -> 300 to 440
                        else -> 440 to 650
                    }
                    size(wPx, hPx)
                    precision(Precision.INEXACT)
                }
            }
            .allowHardware(perf.hardwareBitmaps)
            .crossfade(!perf.disableCardAnimations)
            .build()
    }

    Column(
        modifier = modifier
            .then(
                if (bounceScale != null) {
                    Modifier.graphicsLayer {
                        val s = bounceScale.value
                        scaleX = s
                        scaleY = s
                    }
                } else Modifier
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCardClick,
                onLongClick = onCardLongClick
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Poster Container without expensive offscreen elevation shadow over AMOLED #000000
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isLandscape) 16f / 9f else 0.68f)
                .clip(shape)
                .background(DarkSurfaceCard)
                .then(
                    if (isSelected) Modifier.border(2.dp, AccentGold, shape) else Modifier
                )
                .glassCardDepth(
                    shape = shape,
                    edgeStrength = cardDepthEdgeStrength,
                    sheenStrength = cardDepthSheenStrength,
                    edgeCoverage = cardDepthEdgeCoverage,
                    enabled = cardDepthEnabled && !perf.disableCardDepthEffects
                )
        ) {
            var fanficArtFailed by remember(activeModel) { mutableStateOf(false) }
            val hasRenderableArt = when (activeModel) {
                is String -> activeModel.isNotBlank() && !fanficArtFailed
                null -> false
                else -> !fanficArtFailed
            }
            var unblurredByTap by remember(item.id, activeModel) { mutableStateOf(false) }
            val isFanficBlurred = item.mediaType == MediaType.FANFICTION &&
                !unblurredByTap &&
                shouldBlurFanficCover(item.fanficRating, hasActualImageCover = hasRenderableArt)

            if (item.mediaType == MediaType.FANFICTION && !hasRenderableArt) {
                FanficTypographicCover(
                    title = item.title,
                    author = item.bookAuthor,
                    primaryFandom = item.fanficPrimaryFandom,
                    sourceSite = item.fanficSourceSite,
                    ratingCode = item.fanficRating,
                    wordCount = item.fanficWordCount,
                    isCrossover = item.fanficIsCrossover,
                    compact = isLandscape,
                    isBlurred = false
                )
            } else {
                AsyncImage(
                    model = cardRequest ?: activeModel,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isFanficBlurred) Modifier.blur(16.dp) else Modifier),
                    contentScale = ContentScale.Crop,
                    onError = {
                        if (customPosterUrl != null && !customPosterFailed) {
                            customPosterFailed = true
                        } else {
                            fanficArtFailed = true
                        }
                    },
                    onSuccess = {
                        if (activeModel is String && activeModel.startsWith("http") &&
                            coverCache != null && !coverCache.hasCachedCoverFast(activeModel)
                        ) {
                            val diskCache = coil3.SingletonImageLoader.get(context).diskCache
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                coverCache.promoteFromDiskCache(activeModel, diskCache)
                            }
                        }
                    }
                )
                if (isFanficBlurred) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { unblurredByTap = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .border(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${com.omnitrack.app.data.model.FanficRating.fromRaw(item.fanficRating).label.uppercase()} \u00B7 TAP",
                                color = Color(0xFFFBBF24),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.7.sp
                            )
                        }
                    }
                }
            }

            // Landscape transparent title logo overlay (1:1 with Nuvio)
            if (isSelectionMode) {
                MultiSelectCheckBadge(
                    selected = isSelected,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }
            val hasClearart = isLandscape && showLandscapeClearlogo && !item.clearartUrl.isNullOrBlank()
            if (hasClearart) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.35f to Color.Transparent,
                                    0.70f to Color.Black.copy(alpha = 0.65f),
                                    1.0f to Color.Black.copy(alpha = 0.92f)
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 10.dp, end = 44.dp)
                ) {
                    AsyncImage(
                        model = item.clearartUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxWidth(0.70f)
                            .height(38.dp),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart
                    )
                }
            }

            // Optional Top-Start Badge (e.g. Discover source provider or Chimanew overlay tags)
            val currentBadgeText = if (isAddedToLibrary && isDiscoverAdd) "IN LIBRARY" else badgeText
            val currentBadgeColor = if (isAddedToLibrary && isDiscoverAdd) AccentJade else badgeColor
            if (currentBadgeText != null) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AmoledBlack.copy(alpha = 0.82f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = currentBadgeText.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentBadgeColor
                    )
                }
            } else if (!isSelectionMode && (showRemainingBadge || showTypeBadge)) {
                val tot = item.totalProgress?.toInt() ?: 0
                val cur = item.currentProgress.toInt()
                val remaining = if (showRemainingBadge && item.mediaType != MediaType.GAME && item.mediaType != MediaType.VISUAL_NOVEL && tot > cur) {
                    (tot - cur).coerceAtLeast(0)
                } else 0
                if (remaining > 0 || showTypeBadge) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (remaining > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AmoledBlack.copy(alpha = 0.80f))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "+$remaining",
                                    color = AccentGold,
                                    fontSize = if (isDenseGrid) 8.5.sp else 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (showTypeBadge) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AmoledBlack.copy(alpha = 0.78f))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = item.mediaType.displayName.take(5).uppercase(),
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = if (isDenseGrid) 8.sp else 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }


            // Progress text overlay - positioned neatly at Top-End away from bottom logo
            val progressText = formatProgress(item)
            if (progressText.isNotEmpty() && !(isDenseGrid && displayMode == LibraryDisplayMode.COVER_ONLY_GRID && showScoreBadge)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(if (isDenseGrid) 5.dp else 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AmoledBlack.copy(alpha = 0.80f))
                        .padding(horizontal = if (isDenseGrid) 4.dp else 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = progressText,
                        color = Color(0xFFE2E8F0),
                        fontSize = if (isDenseGrid) 8.5.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            if (showScoreBadge) {
                val dispScore = item.userRating?.takeIf { it > 0f } ?: item.communityScore?.takeIf { it > 0f }
                if (dispScore != null) {
                    val norm = if (dispScore > 10f) dispScore / 10f else dispScore
                    val formatted = String.format(java.util.Locale.US, "%.1f", norm)
                    val scoreAlign = if (displayMode == LibraryDisplayMode.COMPACT_GRID) Alignment.BottomEnd else Alignment.BottomStart
                    Box(
                        modifier = Modifier
                            .align(scoreAlign)
                            .padding(
                                start = 5.dp,
                                end = 5.dp,
                                bottom = if (showQuickProgressButton && scoreAlign == Alignment.BottomEnd) 36.dp else 6.dp
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .background(AmoledBlack.copy(alpha = 0.82f))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "\u2605 $formatted",
                            color = AccentGold,
                            fontSize = if (isDenseGrid) 8.5.sp else 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Chimanew Compact Grid bottom dark scrim + overlaid title
            if (displayMode == LibraryDisplayMode.COMPACT_GRID && !hideLabels && !hasClearart) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    AmoledBlack.copy(alpha = 0.65f),
                                    AmoledBlack.copy(alpha = 0.94f)
                                )
                            )
                        )
                )
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = if (isDenseGrid) 11.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (isDenseGrid) 13.5.sp else 15.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = 6.dp,
                            end = if (showQuickProgressButton) 34.dp else 6.dp,
                            bottom = 7.dp
                        )
                )
            }

            // Tactile +1 / Add to Library Quick Action floating pill
            if (showActionButton) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(pillBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (isDiscoverAdd) {
                                    isAddedToLibrary = true
                                } else if (!perf.disableCardAnimations) {
                                    showPlusOne = true
                                }
                                if (bounceScale != null) {
                                    scope.launch {
                                        bounceScale.animateTo(
                                            targetValue = 0.92f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                        bounceScale.animateTo(
                                            targetValue = 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                    }
                                }
                                onQuickProgress()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDiscoverAdd) {
                        AnimatedContent(
                            targetState = showDiscoverCheck,
                            transitionSpec = {
                                (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith
                                    (scaleOut(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeOut())
                            },
                            label = "addOrCheckAnimation"
                        ) { added ->
                            if (added) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "In Library",
                                    tint = AccentGold,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add to Library",
                                    tint = OnAccentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "+1 Progress",
                            tint = OnAccentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Animated "+1" particle feedback
            if (showActionButton && !perf.disableCardAnimations) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showPlusOne,
                    enter = fadeIn() + scaleIn(initialScale = 0.6f),
                    exit = fadeOut() + scaleOut(targetScale = 1.3f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = "+1",
                        color = AccentGold,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    LaunchedEffect(showPlusOne) {
                        if (showPlusOne) {
                            kotlinx.coroutines.delay(450)
                            showPlusOne = false
                        }
                    }
                }
            }

            // Progress bar if totalProgress is available
            val total = item.totalProgress
            if (total != null && total > 0) {
                val progress = (item.currentProgress / total).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = AccentGold,
                    trackColor = DarkSurfaceCard.copy(alpha = 0.5f),
                )
            }
        }

        // Title text directly on dark background (only shown in Comfortable Grid when not hidden)
        if (!hideLabels && displayMode == LibraryDisplayMode.COMFORTABLE_GRID) {
            Text(
                text = item.title,
                color = TextPrimary,
                fontSize = if (isDenseGrid) 11.5.sp else 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = if (isDenseGrid) 14.5.sp else 16.sp,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            if (item.mediaType == MediaType.VISUAL_NOVEL) {
                Text(
                    text = com.omnitrack.app.data.model.formatVnCardBadge(item.originCountry, item.runtimeMinutes),
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            } else if (item.mediaType == MediaType.GAME) {
                Text(
                    text = com.omnitrack.app.data.model.formatGameCardBadge(item.platforms, item.platform, item.playtimeHours, item.runtimeMinutes),
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            } else if (item.mediaType == MediaType.BOOK) {
                Text(
                    text = com.omnitrack.app.data.model.formatBookCardBadge(
                        currentProgress = item.currentProgress,
                        totalProgress = item.totalProgress,
                        trackingMode = item.bookTrackingMode,
                        audioMinutes = item.bookAudioMinutes,
                        totalAudioMinutes = item.bookTotalAudioMinutes,
                        bookFormat = item.bookFormat,
                        author = item.bookAuthor
                    ),
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            } else if (item.mediaType == MediaType.COMIC) {
                Text(
                    text = com.omnitrack.app.data.model.formatComicCardBadge(
                        publisher = item.comicPublisher,
                        format = item.comicFormat,
                        currentProgress = item.currentProgress,
                        totalProgress = item.totalProgress,
                        trackingMode = item.comicTrackingMode
                    ),
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            } else if (item.mediaType == MediaType.FANFICTION) {
                val siteBadge = com.omnitrack.app.data.model.FanficSite.fromCode(item.fanficSourceSite).shortBadge
                val wordsShort = com.omnitrack.app.data.model.formatWordCountShort(item.fanficWordCount)
                val badgeLine = listOfNotNull(
                    siteBadge,
                    wordsShort.takeIf { it.isNotBlank() },
                    item.fanficPrimaryFandom?.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                Text(
                    text = badgeLine.ifBlank { "Fanfiction" },
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

@Composable
fun MediaCard(
    item: MediaItemEntity,
    onCardClick: () -> Unit,
    onQuickProgress: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadiusDp: Int = 12,
    isLandscape: Boolean = false,
    hideLabels: Boolean = false,
    badgeText: String? = null,
    badgeColor: Color = AccentGold
) {
    MediaCard(
        item = item.toSummary(),
        onCardClick = onCardClick,
        onQuickProgress = onQuickProgress,
        modifier = modifier,
        cornerRadiusDp = cornerRadiusDp,
        isLandscape = isLandscape,
        hideLabels = hideLabels,
        badgeText = badgeText,
        badgeColor = badgeColor
    )
}

private fun formatProgress(item: MediaCardSummary): String {
    if (item.mediaType == MediaType.BOOK) {
        return when (com.omnitrack.app.data.model.BookTrackingMode.fromName(item.bookTrackingMode)) {
            com.omnitrack.app.data.model.BookTrackingMode.AUDIO_MINUTES -> {
                val cur = com.omnitrack.app.data.model.formatBookDuration(item.bookAudioMinutes)
                val tot = com.omnitrack.app.data.model.formatBookDuration(item.bookTotalAudioMinutes)
                when {
                    cur != null && tot != null -> "$cur / $tot"
                    cur != null -> cur
                    tot != null -> "0m / $tot"
                    else -> ""
                }
            }
            com.omnitrack.app.data.model.BookTrackingMode.PERCENT -> {
                val pct = if (item.totalProgress != null && item.totalProgress > 0.0) {
                    kotlin.math.round((item.currentProgress / item.totalProgress) * 100.0).toInt().coerceIn(0, 100)
                } else {
                    item.currentProgress.toInt().coerceIn(0, 100)
                }
                if (pct > 0) "$pct%" else ""
            }
            com.omnitrack.app.data.model.BookTrackingMode.PAGES -> {
                val cur = item.currentProgress.toInt()
                val tot = item.totalProgress?.toInt()
                when {
                    tot != null && tot > 0 -> "$cur / $tot Pages"
                    cur > 0 -> "$cur Pages"
                    else -> ""
                }
            }
        }
    }
    val unit = if (item.mediaType == MediaType.NOVEL && item.platform == "LIGHT_NOVEL") "Vol" else item.mediaType.unitLabel
    val effValue = if (item.mediaType == MediaType.GAME || item.mediaType == MediaType.VISUAL_NOVEL) {
        if (item.playtimeHours > 0.0) item.playtimeHours else item.currentProgress
    } else {
        item.currentProgress
    }
    val current = if (item.mediaType == MediaType.GAME || item.mediaType == MediaType.VISUAL_NOVEL) {
        com.omnitrack.app.ui.screens.detail.components.formatSmartRating(effValue)
    } else {
        effValue.toInt().toString()
    }

    return if (item.totalProgress != null && item.totalProgress > 0) {
        val total = if (item.mediaType == MediaType.GAME || item.mediaType == MediaType.VISUAL_NOVEL) {
            com.omnitrack.app.ui.screens.detail.components.formatSmartRating(item.totalProgress)
        } else {
            item.totalProgress.toInt().toString()
        }
        "$current / $total $unit"
    } else if (effValue > 0) {
        "$current $unit"
    } else {
        ""
    }
}
