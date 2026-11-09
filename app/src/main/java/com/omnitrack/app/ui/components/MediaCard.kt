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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
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
    malId = malId
)

val LocalCustomPosterPattern = androidx.compose.runtime.compositionLocalOf { "" }

@Composable
fun MediaCard(
    item: MediaCardSummary,
    onCardClick: () -> Unit,
    onQuickProgress: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadiusDp: Int = 12,
    isLandscape: Boolean = false,
    hideLabels: Boolean = false,
    cardDepthEnabled: Boolean = true,
    cardDepthEdgeStrength: Float = 50f,
    cardDepthSheenStrength: Float = 35f,
    cardDepthEdgeCoverage: Float = 100f,
    badgeText: String? = null,
    badgeColor: Color = AccentArctic
) {
    val scope = rememberCoroutineScope()
    val bounceScale = remember { Animatable(1f) }
    var showPlusOne by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val coverCache = remember { (context.applicationContext as? com.omnitrack.app.OmniTrackApplication)?.coverCache }
    val inheritedPattern = LocalCustomPosterPattern.current

    val customPosterUrl = remember(item, inheritedPattern, isLandscape) {
        if (inheritedPattern.isNotBlank()) {
            com.omnitrack.app.data.poster.CustomPosterUrlResolver.resolveForSummary(item, inheritedPattern, isLandscape)
        } else null
    }
    var customPosterFailed by remember(customPosterUrl) { mutableStateOf(false) }

    // In landscape mode, prioritize bannerUrl (16:9 backdrop) over portrait cover
    // CONTRACT: Manual customBannerPath / customCoverPath strictly takes top priority
    val activeModel = remember(isLandscape, item.customBannerPath, item.bannerUrl, item.customCoverPath, item.coverUrl, customPosterUrl, customPosterFailed) {
        if (isLandscape) {
            item.customBannerPath?.let { java.io.File(it) }
                ?: if (!customPosterFailed && customPosterUrl != null) customPosterUrl
                else (item.bannerUrl?.takeIf { it.isNotBlank() } ?: item.coverUrl).let { LosslessCoverMapper.toLosslessUrl(it) }
        } else {
            item.customCoverPath?.let { java.io.File(it) }
                ?: if (!customPosterFailed && customPosterUrl != null) customPosterUrl
                else LosslessCoverMapper.toLosslessUrl(item.coverUrl)
        }
    }

    val isInitiallyInLibrary = badgeText == "IN LIBRARY" || (item.id != 0L && badgeText != null)
    var isAddedToLibrary by remember(item.id, badgeText) { mutableStateOf(isInitiallyInLibrary) }
    val isDiscoverAdd = !isInitiallyInLibrary

    val pillBg by androidx.compose.animation.animateColorAsState(
        targetValue = if (isAddedToLibrary && isDiscoverAdd) Color(0xFF1B3D2F) else AmoledBlack.copy(alpha = 0.75f),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 250),
        label = "pillBgColor"
    )

    val shape = RoundedCornerShape(cornerRadiusDp.dp)

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = bounceScale.value
                scaleY = bounceScale.value
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCardClick
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Full-bleed Poster Container with dynamic shadow and Specular Glass Card Depth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isLandscape) 16f / 9f else 0.68f)
                .shadow(10.dp, shape)
                .clip(shape)
                .glassCardDepth(
                    shape = shape,
                    edgeStrength = cardDepthEdgeStrength,
                    sheenStrength = cardDepthSheenStrength,
                    edgeCoverage = cardDepthEdgeCoverage,
                    enabled = cardDepthEnabled
                )
        ) {
            AsyncImage(
                model = activeModel,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = {
                    if (customPosterUrl != null && !customPosterFailed) {
                        customPosterFailed = true
                    }
                },
                onSuccess = {
                    if (activeModel is String && coverCache != null) {
                        val diskCache = coil3.SingletonImageLoader.get(context).diskCache
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            coverCache.promoteFromDiskCache(activeModel, diskCache)
                        }
                    }
                }
            )

            // Landscape transparent title logo overlay (1:1 with Nuvio)
            val hasClearart = isLandscape && !item.clearartUrl.isNullOrBlank()
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

            // Optional Top-Start Badge (e.g. Discover source provider)
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
            }


            // Progress text overlay - positioned neatly at Top-End away from bottom logo
            val progressText = formatProgress(item)
            if (progressText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AmoledBlack.copy(alpha = 0.82f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = progressText,
                        color = Color(0xFFE2E8F0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            // Tactile +1 / Add to Library Quick Action floating pill
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
                            } else {
                                showPlusOne = true
                            }
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
                            onQuickProgress()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isAddedToLibrary,
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
                            tint = Color(0xFF00E054),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = if (isDiscoverAdd) "Add to Library" else "+1 Progress",
                            tint = AccentJade,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Animated "+1" particle feedback
            androidx.compose.animation.AnimatedVisibility(
                visible = showPlusOne,
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 1.3f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = "+1",
                    color = AccentJade,
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
                    color = if (progress >= 1f) AccentJade else AccentArctic,
                    trackColor = DarkSurfaceCard.copy(alpha = 0.5f),
                )
            }
        }

        // Title text directly on dark background (hidden if hideLabels enabled)
        if (!hideLabels) {
            Text(
                text = item.title,
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
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
    badgeColor: Color = AccentArctic
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
    val unit = item.mediaType.unitLabel
    val current = if (item.mediaType == MediaType.GAME || item.mediaType == MediaType.VISUAL_NOVEL) {
        String.format("%.1f", item.currentProgress)
    } else {
        item.currentProgress.toInt().toString()
    }

    return if (item.totalProgress != null && item.totalProgress > 0) {
        val total = if (item.mediaType == MediaType.GAME || item.mediaType == MediaType.VISUAL_NOVEL) {
            String.format("%.1f", item.totalProgress)
        } else {
            item.totalProgress.toInt().toString()
        }
        "$current / $total $unit"
    } else if (item.currentProgress > 0) {
        "$current $unit"
    } else {
        ""
    }
}
