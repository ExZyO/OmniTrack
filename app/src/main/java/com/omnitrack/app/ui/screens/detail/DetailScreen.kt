package com.omnitrack.app.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.BannerSizing
import com.omnitrack.app.data.preferences.DetailsBackdropStyle
import com.omnitrack.app.data.preferences.DetailsPagePresentation
import com.omnitrack.app.data.preferences.FallbackBackgroundStyle
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.preferences.PresentationSettings
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefManager = remember { PreferenceManager(context) }
    val presentationSettings by prefManager.presentationSettings.collectAsState(initial = PresentationSettings())

    val uiState by viewModel.uiState.collectAsState()
    val item = uiState.item
    var showArtworkPicker by remember { mutableStateOf(false) }
    var synopsisExpanded by remember(item?.id) { mutableStateOf(false) }
    var canExpandSynopsis by remember(item?.id) { mutableStateOf(false) }

    // Kinematic horizontal swipe physics (swipe between entries & swipe-to-back)
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
            // Role Swapping Resolution (Chimanew Parity)
            val effectiveCover = remember(item.customCoverPath, item.artworkRole, item.coverUrl, item.bannerUrl) {
                item.customCoverPath ?: when (item.artworkRole) {
                    "BANNER_AS_COVER" -> item.bannerUrl ?: item.coverUrl
                    "SYNCHRONIZED" -> item.bannerUrl ?: item.coverUrl
                    else -> item.coverUrl
                }
            }
            val effectiveBanner = remember(item.customBannerPath, item.artworkRole, item.coverUrl, item.bannerUrl) {
                item.customBannerPath ?: when (item.artworkRole) {
                    "COVER_AS_BANNER" -> item.coverUrl
                    "SYNCHRONIZED" -> item.coverUrl
                    else -> item.bannerUrl ?: item.coverUrl
                }
            }

            val losslessCover = remember(effectiveCover) { LosslessCoverMapper.toLosslessUrl(effectiveCover) }
            val dominantAccent = rememberCoverDominantColor(losslessCover)
            val backdropTargetUrl = effectiveBanner

            val isCinematicBlur = presentationSettings.fallbackBackgroundStyle == FallbackBackgroundStyle.CINEMATIC_BLUR
            val backdropBitmap = rememberArtworkBackdrop(
                imageUrl = backdropTargetUrl,
                enabled = isCinematicBlur
            )

            // Background presentation layer
            when (presentationSettings.fallbackBackgroundStyle) {
                FallbackBackgroundStyle.CINEMATIC_BLUR -> {
                    ArtworkBackdrop(
                        image = backdropBitmap,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                FallbackBackgroundStyle.DOMINANT_COLOR -> {
                    CoverGlowBox(
                        imageUrl = backdropTargetUrl,
                        modifier = Modifier.fillMaxSize()
                    ) {}
                }
                FallbackBackgroundStyle.SOLID_BLACK -> {
                    // Pure AMOLED black canvas
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // PRESENTATION MODE 1: Nuvio Hero (Unblocked Banner + 3 Centered Floating Glass Tiles)
                if (presentationSettings.detailsPagePresentation == DetailsPagePresentation.NUVIO_HERO) {
                    // Top Bar (Back & Artwork Customization)
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
                                .background(AmoledBlack.copy(alpha = 0.65f))
                                .border(1.dp, GlassBorder, CircleShape)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Customize Artwork Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(AmoledBlack.copy(alpha = 0.65f))
                                .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
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
                                    text = "Artwork",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Unblocked Cinema Showcase Card (Widescreen 16:9 + Ambient Aura)
                    if (presentationSettings.bannerSizing == BannerSizing.CINEMA_SHOWCASE) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .aspectRatio(16f / 9f)
                        ) {
                            // Ambient Aura behind the card
                            AsyncImage(
                                model = backdropTargetUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = 1.06f
                                        scaleY = 1.06f
                                        alpha = 0.45f
                                    },
                                contentScale = ContentScale.Crop
                            )

                            // Widescreen Card
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shadow(18.dp, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                                    .clickable { showArtworkPicker = true }
                            ) {
                                AsyncImage(
                                    model = backdropTargetUrl,
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    } else {
                        // Full-Bleed unblocked hero
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            AsyncImage(
                                model = backdropTargetUrl,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .featherArtwork(startYRatio = 0.55f),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // The 3 Centered Floating Glass Tiles (Progress, Status, Rating)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tile 1: Progress Stepper
                        CenteredGlassTile(
                            modifier = Modifier.weight(1f),
                            title = if (item.mediaType == MediaType.GAME || item.mediaType == MediaType.VISUAL_NOVEL) {
                                String.format("%.1f hrs", item.currentProgress)
                            } else {
                                "${item.currentProgress.toInt()}${item.totalProgress?.let { " / ${it.toInt()}" } ?: ""}"
                            },
                            subtitle = item.mediaType.unitLabel,
                            icon = Icons.Default.Add,
                            iconTint = AccentJade,
                            onClick = {
                                val inc = if (item.mediaType == MediaType.GAME || item.mediaType == MediaType.VISUAL_NOVEL) 0.5 else 1.0
                                val newProg = item.currentProgress + inc
                                val clamped = if (item.totalProgress != null) newProg.coerceAtMost(item.totalProgress) else newProg
                                viewModel.updateProgress(clamped)
                            }
                        )

                        // Tile 2: Tracking Status Pill
                        CenteredGlassTile(
                            modifier = Modifier.weight(1f),
                            title = item.status.displayName,
                            subtitle = "Status",
                            icon = Icons.Default.CheckCircle,
                            iconTint = dominantAccent,
                            onClick = {
                                val next = when (item.status) {
                                    TrackingStatus.PLAN_TO_TRACK -> TrackingStatus.IN_PROGRESS
                                    TrackingStatus.IN_PROGRESS -> TrackingStatus.COMPLETED
                                    TrackingStatus.COMPLETED -> TrackingStatus.ON_HOLD
                                    TrackingStatus.ON_HOLD -> TrackingStatus.DROPPED
                                    TrackingStatus.DROPPED -> TrackingStatus.PLAN_TO_TRACK
                                    else -> TrackingStatus.IN_PROGRESS
                                }
                                viewModel.updateStatus(next)
                            }
                        )

                        // Tile 3: User Rating & Aggregate
                        CenteredGlassTile(
                            modifier = Modifier.weight(1f),
                            title = item.userRating?.let { String.format("%.1f", it) } ?: "Rate",
                            subtitle = "Rating",
                            icon = Icons.Default.Star,
                            iconTint = AccentGold,
                            onClick = {
                                val current = item.userRating ?: 7.0f
                                val next = if (current >= 10.0f) 5.0f else current + 1.0f
                                viewModel.updateRating(next)
                            }
                        )
                    }

                    // Clean Full-Width Title and Meta Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.title,
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 27.sp
                        )
                        if (item.nativeTitle != null) {
                            Text(
                                text = item.nativeTitle,
                                color = TextMuted,
                                fontSize = 12.5.sp
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentArctic.copy(alpha = 0.15f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.mediaType.displayName,
                                    color = AccentArctic,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (item.platform != null) {
                                Text(
                                    text = item.platform,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                } else {
                    // PRESENTATION MODE 2: Classic Poster Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    ) {
                        AsyncImage(
                            model = backdropTargetUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .featherArtwork(startYRatio = 0.55f),
                            contentScale = ContentScale.Crop
                        )

                        // Floating top bar
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

                    // Overlapping Poster Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .offset(y = (-50).dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
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
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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

            // Cover discovery & role customization bottom sheet
            if (showArtworkPicker) {
                ArtworkPickerBottomSheet(
                    title = item.title,
                    mediaType = item.mediaType,
                    currentCoverUrl = losslessCover,
                    mediaId = item.id,
                    currentRole = item.artworkRole,
                    onDismiss = { showArtworkPicker = false },
                    onCoverSelected = { newUrl ->
                        viewModel.updateCover(newUrl)
                        showArtworkPicker = false
                    },
                    onRoleSelected = { newRole ->
                        viewModel.updateArtworkRole(newRole)
                    }
                )
            }
        }
    }
}

@Composable
private fun CenteredGlassTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceElevated.copy(alpha = 0.82f))
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
