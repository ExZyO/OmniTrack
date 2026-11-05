package com.omnitrack.app.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun MediaCard(
    item: MediaItemEntity,
    onCardClick: () -> Unit,
    onQuickProgress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val bounceScale = remember { Animatable(1f) }
    var showPlusOne by remember { mutableStateOf(false) }

    GlassmorphicSurface(
        modifier = modifier
            .scale(bounceScale.value)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Cover image with gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f)
            ) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Bottom gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, AmoledBlack.copy(alpha = 0.85f))
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
                            .padding(start = 8.dp, bottom = 6.dp),
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp
                    )
                }

                // +1 Quick action button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(AccentArctic.copy(alpha = 0.9f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                scope.launch {
                                    showPlusOne = true
                                    bounceScale.animateTo(
                                        targetValue = 0.92f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessHigh
                                        )
                                    )
                                    bounceScale.animateTo(
                                        targetValue = 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                    showPlusOne = false
                                }
                                onQuickProgress()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "+1",
                        tint = AmoledBlack,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Animated +1 indicator
                androidx.compose.animation.AnimatedVisibility(
                    visible = showPlusOne,
                    enter = scaleIn(spring(dampingRatio = 0.4f)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = "+1",
                        color = AccentJade,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Progress bar
            if (item.totalProgress != null && item.totalProgress > 0) {
                val progress = (item.currentProgress / item.totalProgress).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = if (progress >= 1f) AccentJade else AccentArctic,
                    trackColor = DarkSurfaceCard,
                )
            }

            // Title area
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

private fun formatProgress(item: MediaItemEntity): String {
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
