package com.omnitrack.app.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.ui.components.ArtworkBackdrop
import com.omnitrack.app.ui.components.ArtworkPickerBottomSheet
import com.omnitrack.app.ui.components.BackgroundMode
import com.omnitrack.app.ui.components.ChapterStepper
import com.omnitrack.app.ui.components.CoverGlowBox
import com.omnitrack.app.ui.components.EpisodeAccordion
import com.omnitrack.app.ui.components.GameProgressCard
import com.omnitrack.app.ui.components.featherArtwork
import com.omnitrack.app.ui.components.rememberArtworkBackdrop
import com.omnitrack.app.ui.components.rememberCoverDominantColor
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import com.omnitrack.app.util.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundMode: BackgroundMode = BackgroundMode.CINEMATIC
) {
    val uiState by viewModel.uiState.collectAsState()
    val item = uiState.item
    var showArtworkPicker by remember { mutableStateOf(false) }
    var synopsisExpanded by remember(item?.id) { mutableStateOf(false) }
    var canExpandSynopsis by remember(item?.id) { mutableStateOf(false) }

    // Poweramp kinematic drag physics: swipe between entries & swipe-to-back
    var dragOffsetX by remember { mutableStateOf(0f) }
    val animatedDragOffsetX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "powerampSwipe"
    )

    LaunchedEffect(item?.id) {
        if (item != null) {
            AppLogger.i("DetailScreen", "Viewing detail for: ${item.title} [id=${item.id}, type=${item.mediaType.displayName}]")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .graphicsLayer {
                translationX = animatedDragOffsetX
                val scale = (1f - (kotlin.math.abs(animatedDragOffsetX) / 1000f * 0.08f)).coerceIn(0.92f, 1f)
                scaleX = scale
                scaleY = scale
                alpha = (1f - (kotlin.math.abs(animatedDragOffsetX) / 1000f * 0.4f)).coerceIn(0.6f, 1f)
            }
            .pointerInput(uiState.hasPreviousEntry, uiState.hasNextEntry) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragOffsetX > 240f -> onBack()
                            dragOffsetX > 140f && uiState.hasPreviousEntry -> {
                                dragOffsetX = 0f
                                viewModel.navigateToPrevious()
                            }
                            dragOffsetX < -140f && uiState.hasNextEntry -> {
                                dragOffsetX = 0f
                                viewModel.navigateToNext()
                            }
                            else -> dragOffsetX = 0f
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        dragOffsetX += dragAmount
                        change.consume()
                    }
                )
            }
    ) {
        if (uiState.isLoading || item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentArctic, strokeWidth = 2.dp)
            }
        } else {
            val losslessCover = remember(item.coverUrl) { LosslessCoverMapper.toLosslessUrl(item.coverUrl) }
            val dominantAccent = rememberCoverDominantColor(losslessCover)
            val backdropTargetUrl = item.bannerUrl ?: losslessCover
            val backdropBitmap = rememberArtworkBackdrop(
                imageUrl = backdropTargetUrl,
                enabled = backgroundMode == BackgroundMode.CINEMATIC
            )

            // Background presentation layer
            when (backgroundMode) {
                BackgroundMode.CINEMATIC -> {
                    ArtworkBackdrop(
                        image = backdropBitmap,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                BackgroundMode.DOMINANT_COLOR -> {
                    CoverGlowBox(
                        imageUrl = backdropTargetUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                BackgroundMode.NORMAL -> {
                    // Pure AMOLED black canvas
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Hero Banner with zero vignette and zero-seam feathering
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    // Sharp uncompressed hero banner
                    AsyncImage(
                        model = backdropTargetUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (backgroundMode == BackgroundMode.CINEMATIC) {
                                    Modifier.featherArtwork(startYRatio = 0.55f)
                                } else {
                                    Modifier
                                }
                            ),
                        contentScale = ContentScale.Crop
                    )

                    // Scrim gradient ONLY when not in Cinematic mode
                    if (backgroundMode != BackgroundMode.CINEMATIC) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.00f to Color.Transparent,
                                            0.16f to AmoledBlack.copy(alpha = 0.04f),
                                            0.32f to AmoledBlack.copy(alpha = 0.14f),
                                            0.50f to AmoledBlack.copy(alpha = 0.34f),
                                            0.68f to AmoledBlack.copy(alpha = 0.62f),
                                            0.84f to AmoledBlack.copy(alpha = 0.84f),
                                            1.00f to AmoledBlack
                                        )
                                    )
                                )
                        )
                    }

                    // Floating top navigation bar (Back & Change Cover)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AmoledBlack.copy(alpha = 0.55f))
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Quick Change Cover Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(AmoledBlack.copy(alpha = 0.55f))
                                .clickable { showArtworkPicker = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = dominantAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Cover",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Blended Hero: Poster overlapping banner with seamless metadata layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .offset(y = (-50).dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Poster Artwork with specular shadow & tap-to-change
                    Box(
                        modifier = Modifier
                            .width(125.dp)
                            .aspectRatio(0.68f)
                            .shadow(18.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showArtworkPicker = true }
                    ) {
                        AsyncImage(
                            model = losslessCover,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Seamless flowing metadata (No boxed cards!)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.title,
                            color = TextPrimary,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 26.sp
                        )

                        if (item.nativeTitle != null) {
                            Text(
                                text = item.nativeTitle,
                                color = TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Inline Status & Metric Pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            val statusBgColor by animateColorAsState(
                                targetValue = dominantAccent.copy(alpha = 0.18f),
                                label = "statusBg"
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(statusBgColor)
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = item.status.displayName,
                                    color = dominantAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (item.userRating != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurfaceElevated)
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = AccentGold,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = String.format("%.1f", item.userRating),
                                        color = AccentGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = item.mediaType.displayName,
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Seamless Synopsis with bidirectional overflow-aware toggle
                if (!item.synopsis.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .animateContentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = canExpandSynopsis,
                                onClick = { synopsisExpanded = !synopsisExpanded }
                            )
                    ) {
                        Text(
                            text = item.synopsis,
                            color = TextSecondary,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { textLayoutResult ->
                                if (!synopsisExpanded) {
                                    canExpandSynopsis = textLayoutResult.hasVisualOverflow || textLayoutResult.lineCount > 3
                                }
                            }
                        )
                        if (canExpandSynopsis) {
                            Text(
                                text = if (synopsisExpanded) "Show less" else "Read more",
                                color = dominantAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Progress Section (100% Unboxed flowing controls)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (item.mediaType) {
                        MediaType.ANIME, MediaType.SHOW -> {
                            ChapterStepper(
                                unitLabel = item.mediaType.unitLabel,
                                currentProgress = item.currentProgress,
                                totalProgress = item.totalProgress,
                                onProgressChanged = { viewModel.updateProgress(it) }
                            )

                            if (uiState.seasons.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                EpisodeAccordion(
                                    seasons = uiState.seasons,
                                    onEpisodeToggle = { _, epIdx ->
                                        viewModel.updateProgress((epIdx + 1).toDouble())
                                    }
                                )
                            }
                        }

                        MediaType.MANGA, MediaType.NOVEL -> {
                            ChapterStepper(
                                unitLabel = item.mediaType.unitLabel,
                                currentProgress = item.currentProgress,
                                totalProgress = item.totalProgress,
                                onProgressChanged = { viewModel.updateProgress(it) }
                            )
                        }

                        MediaType.GAME -> {
                            GameProgressCard(
                                hoursPlayed = item.currentProgress,
                                selectedPlatform = item.platform,
                                onHoursChanged = { viewModel.updateProgress(it) },
                                onPlatformSelected = { viewModel.updatePlatform(it) }
                            )
                        }

                        MediaType.VISUAL_NOVEL -> {
                            ChapterStepper(
                                unitLabel = item.mediaType.unitLabel,
                                currentProgress = item.currentProgress,
                                totalProgress = item.totalProgress,
                                onProgressChanged = { viewModel.updateProgress(it) },
                                isDecimal = true,
                                step = 0.5
                            )
                        }

                        MediaType.MOVIE -> {
                            val isWatched = item.status == TrackingStatus.COMPLETED
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateStatus(
                                            if (isWatched) TrackingStatus.PLAN_TO_TRACK else TrackingStatus.COMPLETED
                                        )
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(if (isWatched) AccentJade else Color(0x22FFFFFF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isWatched) {
                                            Text("✓", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(
                                        text = if (isWatched) "Watched" else "Mark as Watched",
                                        color = if (isWatched) AccentJade else TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                }

                                Text(
                                    text = if (isWatched) "Completed" else "Plan to Watch",
                                    color = if (isWatched) AccentJade else TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        else -> {}
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }

            // Cover discovery bottom sheet
            if (showArtworkPicker) {
                ArtworkPickerBottomSheet(
                    title = item.title,
                    mediaType = item.mediaType,
                    currentCoverUrl = losslessCover,
                    onDismiss = { showArtworkPicker = false },
                    onCoverSelected = { newUrl ->
                        viewModel.updateCover(newUrl)
                        showArtworkPicker = false
                    }
                )
            }
        }
    }
}
