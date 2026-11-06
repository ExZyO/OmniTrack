package com.omnitrack.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    mediaType = mediaType,
    status = status,
    currentProgress = currentProgress,
    totalProgress = totalProgress,
    userRating = userRating,
    lastUpdated = lastUpdated
)

@Composable
fun MediaCard(
    item: MediaCardSummary,
    onCardClick: () -> Unit,
    onQuickProgress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val bounceScale = remember { Animatable(1f) }
    var showPlusOne by remember { mutableStateOf(false) }
    val losslessCover = remember(item.coverUrl) { LosslessCoverMapper.toLosslessUrl(item.coverUrl) }

    Column(
        modifier = modifier
            .scale(bounceScale.value)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCardClick
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Full-bleed Poster Container with dynamic shadow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .shadow(10.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
        ) {
            AsyncImage(
                model = losslessCover,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Bottom glass scrim for readable progress overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(65.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, AmoledBlack.copy(alpha = 0.88f))
                        )
                    )
            )

            // Progress text overlay
            val progressText = formatProgress(item)
            if (progressText.isNotEmpty()) {
                Text(
                    text = progressText,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 7.dp),
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                )
            }

            // Tactile +1 Quick Action floating pill
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AmoledBlack.copy(alpha = 0.75f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            scope.launch {
                                showPlusOne = true
                                bounceScale.animateTo(
                                    targetValue = 0.94f,
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
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "+1 Progress",
                    tint = AccentJade,
                    modifier = Modifier.size(16.dp)
                )
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

        // Title text directly on dark background
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

@Composable
fun MediaCard(
    item: MediaItemEntity,
    onCardClick: () -> Unit,
    onQuickProgress: () -> Unit,
    modifier: Modifier = Modifier
) {
    MediaCard(
        item = item.toSummary(),
        onCardClick = onCardClick,
        onQuickProgress = onQuickProgress,
        modifier = modifier
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
