package com.omnitrack.app.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.components.ChapterStepper
import com.omnitrack.app.ui.components.CoverGlowBox
import com.omnitrack.app.ui.components.EpisodeAccordion
import com.omnitrack.app.ui.components.GameProgressCard
import com.omnitrack.app.ui.components.GlassmorphicSurface
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
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
            CoverGlowBox(
                imageUrl = item.bannerUrl ?: item.coverUrl,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Top bar (overlaid)
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )

                    // Hero section: Cover + Info
                    var heroVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { delay(100); heroVisible = true }

                    AnimatedVisibility(
                        visible = heroVisible,
                        enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                                slideInVertically(
                                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                                    initialOffsetY = { it / 4 }
                                )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Cover art
                            AsyncImage(
                                model = item.coverUrl,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .width(130.dp)
                                    .height(195.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )

                            // Info column
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    color = TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 24.sp
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

                                // Status chip
                                GlassmorphicSurface(
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = item.status.displayName,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        color = AccentArctic,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Rating
                                if (item.userRating != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = AccentGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = String.format("%.1f", item.userRating),
                                            color = AccentGold,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Media type + platform
                                Text(
                                    text = buildString {
                                        append(item.mediaType.displayName)
                                        if (item.platform != null) {
                                            append(" · ${item.platform}")
                                        }
                                    },
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Synopsis
                    if (item.synopsis != null) {
                        var synopsisVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { delay(250); synopsisVisible = true }

                        AnimatedVisibility(
                            visible = synopsisVisible,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
                        ) {
                            GlassmorphicSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = item.synopsis,
                                    modifier = Modifier.padding(14.dp),
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Adaptive content based on media type
                    var contentVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { delay(400); contentVisible = true }

                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn() + slideInVertically(
                            spring(dampingRatio = Spring.DampingRatioLowBouncy),
                            initialOffsetY = { it / 3 }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (item.mediaType) {
                                MediaType.ANIME, MediaType.SHOW -> {
                                    // Episode accordions
                                    if (uiState.seasons.isNotEmpty()) {
                                        ChapterStepper(
                                            label = "EPISODES WATCHED",
                                            unitLabel = item.mediaType.unitLabel,
                                            currentProgress = item.currentProgress,
                                            totalProgress = item.totalProgress,
                                            onProgressChanged = { viewModel.updateProgress(it) }
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        EpisodeAccordion(
                                            seasons = uiState.seasons,
                                            onEpisodeToggle = { _, epIdx ->
                                                // Toggle up to this episode
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
                                    // Movies just show status — no progress stepper needed
                                    GlassmorphicSurface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = if (item.status == com.omnitrack.app.data.model.TrackingStatus.COMPLETED)
                                                "✓ Watched" else "Not yet watched",
                                            modifier = Modifier.padding(16.dp),
                                            color = if (item.status == com.omnitrack.app.data.model.TrackingStatus.COMPLETED)
                                                com.omnitrack.app.ui.theme.AccentJade else TextMuted,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                else -> { }
                            }

                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}
