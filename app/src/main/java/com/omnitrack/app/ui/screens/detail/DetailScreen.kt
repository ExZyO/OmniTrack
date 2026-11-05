package com.omnitrack.app.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.ui.components.ArtworkPickerBottomSheet
import com.omnitrack.app.ui.components.ChapterStepper
import com.omnitrack.app.ui.components.CoverGlowBox
import com.omnitrack.app.ui.components.EpisodeAccordion
import com.omnitrack.app.ui.components.GameProgressCard
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val item = uiState.item
    var showArtworkPicker by remember { mutableStateOf(false) }
    var synopsisExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        if (uiState.isLoading || item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentArctic, strokeWidth = 2.dp)
            }
        } else {
            val losslessCover = remember(item.coverUrl) { LosslessCoverMapper.toLosslessUrl(item.coverUrl) }
            val dominantAccent = rememberCoverDominantColor(losslessCover)

            // Dynamic ambient bloom on the deep AMOLED canvas
            CoverGlowBox(
                imageUrl = item.bannerUrl ?: losslessCover,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Full-bleed Banner with Parallax Gradient Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(290.dp)
                    ) {
                        // Banner background image
                        AsyncImage(
                            model = item.bannerUrl ?: losslessCover,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Multi-stop gradient blend into pure AMOLED black
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            AmoledBlack.copy(alpha = 0.4f),
                                            AmoledBlack.copy(alpha = 0.2f),
                                            AmoledBlack.copy(alpha = 0.7f),
                                            AmoledBlack
                                        )
                                    )
                                )
                        )

                        // Floating top bar (edge-to-edge, status bar padded)
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
                            .padding(top = (-50).dp),
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

                            // Inline Status & Metric Pills (blended seamlessly into background)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                // Status Pill
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

                                // Rating pill
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

                                // Type
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

                    // Seamless Synopsis (Typography-first, no chunky box!)
                    if (!item.synopsis.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .animateContentSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { synopsisExpanded = !synopsisExpanded }
                                )
                        ) {
                            Text(
                                text = item.synopsis,
                                color = TextSecondary,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (synopsisExpanded) "Show less" else "Read more",
                                color = dominantAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    // Progress Section (Integrated fluid tracker, no nested box clutter)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (item.mediaType) {
                            MediaType.ANIME, MediaType.SHOW -> {
                                ChapterStepper(
                                    label = "EPISODES TRACKER",
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
                                    label = "CHAPTERS READ",
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
                                    label = "HOURS PLAYED",
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
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(DarkSurfaceElevated)
                                        .clickable {
                                            viewModel.updateStatus(
                                                if (isWatched) TrackingStatus.PLAN_TO_TRACK else TrackingStatus.COMPLETED
                                            )
                                        }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isWatched) "Completed & Watched" else "Mark as Watched",
                                        color = if (isWatched) AccentJade else TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = if (isWatched) "✓ Watched" else "Tap to check",
                                        color = if (isWatched) AccentJade else TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            else -> {}
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))
                }
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
