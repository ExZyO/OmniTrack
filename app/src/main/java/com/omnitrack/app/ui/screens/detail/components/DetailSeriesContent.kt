package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.R
import com.omnitrack.app.data.model.MediaEpisode
import com.omnitrack.app.data.model.MediaSeason
import com.omnitrack.app.data.preferences.EpisodeLayoutMode
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * 1:1 Nuvio Reference Parity: Seasons & Episode Tracking Grid.
 *
 * Supports:
 * 1. Mode 1: Horizontal Carousel (16:9 cards, metadata inside image).
 * 2. Mode 2: Compact List (Thumbnail on left, metadata on right).
 * 3. Mode 3: Vertical Hero Cards (Full-width vertical stack of 16:9 cards with in-image metadata).
 * 4. 1:1 Nuvio Animated Watched Badge (compact 20dp circle with jade gradient, hidden when unwatched).
 * 5. Official IMDb Rating Badges (with official logo asset).
 * 6. 1:1 Season Posters Rail vs Text Chips selector with "Posters" / "Text" button.
 * 7. Scalable episode card dimensions based on user setting.
 */
@Composable
fun DetailSeriesContent(
    seasons: List<MediaSeason>,
    episodes: List<MediaEpisode>,
    watchedEpisodes: Set<Pair<Int, Int>>, // Set of (seasonNumber, episodeNumber)
    selectedSeasonNumber: Int,
    fallbackBackdropUrl: String?,
    onSelectSeason: (Int) -> Unit,
    onToggleEpisodeWatched: (MediaEpisode) -> Unit,
    onMarkPreviousWatched: (MediaEpisode) -> Unit,
    onToggleSeasonWatched: (Int) -> Unit,
    modifier: Modifier = Modifier,
    episodesBySeason: Map<Int, List<MediaEpisode>> = emptyMap(),
    layoutMode: EpisodeLayoutMode = EpisodeLayoutMode.LIST,
    layoutScale: Float = 1.0f,
    initialSeasonViewMode: String = "Posters",
    onSeasonViewModeChanged: ((String) -> Unit)? = null,
    onLayoutModeChanged: ((EpisodeLayoutMode) -> Unit)? = null
) {
    if (seasons.isEmpty() && episodes.isEmpty() && episodesBySeason.isEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(3) {
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .height(168.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                    )
                }
            }
        }
        return
    }

    var activeActionSheetEpisode by remember { mutableStateOf<MediaEpisode?>(null) }
    var currentSeasonViewMode by remember(initialSeasonViewMode) { mutableStateOf(initialSeasonViewMode) }
    var currentLayoutMode by remember(layoutMode) { mutableStateOf(layoutMode) }
    val hasSeasonPosters = remember(seasons) { seasons.any { !it.posterUrl.isNullOrBlank() } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Seasons Section Header with 1:1 Nuvio "Posters" / "Text" Button and Smooth 280ms Height Animation
        if (seasons.size > 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(280)),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Seasons",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )

                    if (hasSeasonPosters) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    val next = if (currentSeasonViewMode == "Posters") "Text" else "Posters"
                                    currentSeasonViewMode = next
                                    onSeasonViewModeChanged?.invoke(next)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (currentSeasonViewMode == "Posters") "Posters" else "Text",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // 2. Season Selector (Posters Rail vs Text Chips with 1:1 Nuvio Fluid Transition)
                Crossfade(
                    targetState = currentSeasonViewMode,
                    animationSpec = tween(220),
                    label = "season_view_mode"
                ) { mode ->
                    if (mode == "Posters" && hasSeasonPosters) {
                        SeasonPosterScrollRow(
                            seasons = seasons,
                            selectedSeasonNumber = selectedSeasonNumber,
                            episodes = episodes,
                            episodesBySeason = episodesBySeason,
                            watchedEpisodes = watchedEpisodes,
                            fallbackImageUrl = fallbackBackdropUrl,
                            onSelectSeason = onSelectSeason
                        )
                    } else {
                        SeasonTextChipScrollRow(
                            seasons = seasons,
                            selectedSeasonNumber = selectedSeasonNumber,
                            episodes = episodes,
                            episodesBySeason = episodesBySeason,
                            watchedEpisodes = watchedEpisodes,
                            onSelectSeason = onSelectSeason
                        )
                    }
                }
            }
        }

        // 3. Episodes Header with Layout Switcher (Horizontal / List / Vertical Cards)
        val seasonEpisodes = episodesBySeason[selectedSeasonNumber] ?: episodes.filter { it.seasonNumber == selectedSeasonNumber }
        val currentSeason = seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber }
        val totalInSeason = currentSeason?.episodeCount ?: seasonEpisodes.size
        val watchedInSeason = seasonEpisodes.count { (it.seasonNumber to it.episodeNumber) in watchedEpisodes }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Episodes",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "$watchedInSeason of $totalInSeason watched",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Layout Mode Switcher Icon Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        val nextMode = when (currentLayoutMode) {
                            EpisodeLayoutMode.HORIZONTAL -> EpisodeLayoutMode.LIST
                            EpisodeLayoutMode.LIST -> EpisodeLayoutMode.VERTICAL_CARD
                            EpisodeLayoutMode.VERTICAL_CARD -> EpisodeLayoutMode.HORIZONTAL
                        }
                        currentLayoutMode = nextMode
                        onLayoutModeChanged?.invoke(nextMode)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    val iconVector = when (currentLayoutMode) {
                        EpisodeLayoutMode.HORIZONTAL -> Icons.Default.ViewStream
                        EpisodeLayoutMode.LIST -> Icons.Default.ViewAgenda
                        EpisodeLayoutMode.VERTICAL_CARD -> Icons.Default.GridView
                    }
                    Icon(
                        imageVector = iconVector,
                        contentDescription = "Switch Layout",
                        tint = AccentJade,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // 4. Render Active Episode Layout Mode with 1:1 Nuvio Directional Slide & Instant Switching
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(tween(250))
        ) {
            AnimatedContent(
                targetState = selectedSeasonNumber to currentLayoutMode,
                transitionSpec = {
                    val fromSeason = initialState.first
                    val toSeason = targetState.first
                    val fromIdx = seasons.indexOfFirst { it.seasonNumber == fromSeason }.takeIf { it >= 0 } ?: 0
                    val toIdx = seasons.indexOfFirst { it.seasonNumber == toSeason }.takeIf { it >= 0 } ?: 0
                    val dir = if (toIdx >= fromIdx) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { dir * it / 5 })
                        .togetherWith(
                            fadeOut(tween(170)) + slideOutHorizontally(tween(170)) { -dir * it / 5 }
                        )
                },
                label = "season_episodes_transition"
            ) { (seasonNum, mode) ->
                val activeSeasonEps = episodesBySeason[seasonNum] ?: episodes.filter { it.seasonNumber == seasonNum }
                if (activeSeasonEps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Season in production • Episodes will appear as broadcast dates are announced",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    when (mode) {
                        EpisodeLayoutMode.HORIZONTAL -> {
                            EpisodeHorizontalRow(
                                episodes = activeSeasonEps,
                                watchedEpisodes = watchedEpisodes,
                                fallbackImageUrl = fallbackBackdropUrl,
                                scale = layoutScale,
                                onToggleWatched = onToggleEpisodeWatched,
                                onLongPress = { activeActionSheetEpisode = it }
                            )
                        }

                        EpisodeLayoutMode.LIST -> {
                            EpisodeListColumn(
                                episodes = activeSeasonEps,
                                watchedEpisodes = watchedEpisodes,
                                fallbackImageUrl = fallbackBackdropUrl,
                                scale = layoutScale,
                                onToggleWatched = onToggleEpisodeWatched,
                                onLongPress = { activeActionSheetEpisode = it }
                            )
                        }

                        EpisodeLayoutMode.VERTICAL_CARD -> {
                            EpisodeVerticalCardColumn(
                                episodes = activeSeasonEps,
                                watchedEpisodes = watchedEpisodes,
                                fallbackImageUrl = fallbackBackdropUrl,
                                scale = layoutScale,
                                onToggleWatched = onToggleEpisodeWatched,
                                onLongPress = { activeActionSheetEpisode = it }
                            )
                        }
                    }
                }
            }
        }
    }

    // Watched Action Sheet
    activeActionSheetEpisode?.let { ep ->
        val currentSeasonObj = seasons.firstOrNull { it.seasonNumber == ep.seasonNumber }
        val seasonLabel = currentSeasonObj?.name ?: "Season ${ep.seasonNumber}"
        val isWatched = (ep.seasonNumber to ep.episodeNumber) in watchedEpisodes
        val seasonEps = episodes.filter { it.seasonNumber == ep.seasonNumber }
        val isSeasonWatched = seasonEps.isNotEmpty() && seasonEps.all { (it.seasonNumber to it.episodeNumber) in watchedEpisodes }

        EpisodeWatchedActionSheet(
            episode = ep,
            seasonLabel = seasonLabel,
            isEpisodeWatched = isWatched,
            canMarkPreviousEpisodes = ep.episodeNumber > 1,
            isSeasonWatched = isSeasonWatched,
            onDismiss = { activeActionSheetEpisode = null },
            onToggleWatched = { onToggleEpisodeWatched(ep) },
            onMarkPreviousWatched = { onMarkPreviousWatched(ep) },
            onToggleSeasonWatched = { onToggleSeasonWatched(ep.seasonNumber) }
        )
    }
}

/**
 * 1:1 Nuvio Animated Watched Badge:
 * Solid 24dp circular badge filled with vibrant emerald accent (0xFF22D37C) and crisp white checkmark icon (14dp).
 * Fades and scales smoothly on toggle.
 */
@Composable
fun NuvioAnimatedWatchedBadge(
    isWatched: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isWatched,
        enter = fadeIn(tween(150)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 400f)),
        exit = fadeOut(tween(150)) + scaleOut(tween(150)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFE52E5E), Color(0xFFC2185B)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Watched",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * Official IMDb Rating Badge for episodes.
 */
@Composable
private fun ImdbEpisodeRatingBadge(
    rating: Float,
    modifier: Modifier = Modifier,
    logoWidth: Dp = 24.dp,
    logoHeight: Dp = 12.dp,
    textSize: androidx.compose.ui.unit.TextUnit = 12.sp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.rating_imdb),
            contentDescription = "IMDb",
            modifier = Modifier
                .width(logoWidth)
                .height(logoHeight),
            contentScale = ContentScale.Fit
        )
        Text(
            text = String.format(java.util.Locale.US, "%.1f", rating),
            fontSize = textSize,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF5C518)
        )
    }
}

/**
 * 1:1 Nuvio Floating Episode Code Badge ("S1 E1" / "E1" / "SP 1")
 */
@Composable
private fun EpisodeCodeBadge(
    text: String,
    modifier: Modifier = Modifier,
    textSize: androidx.compose.ui.unit.TextUnit = 10.sp,
    radius: Dp = 4.dp,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 2.dp,
    backgroundAlpha: Float = 0.45f
) {
    if (text.isBlank()) return
    val badgeShape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(badgeShape)
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .border(0.5.dp, Color.Black.copy(alpha = 0.60f), badgeShape)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = textSize,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.90f),
            letterSpacing = 0.sp,
            maxLines = 1
        )
    }
}

private fun formatEpisodeAirDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        val trimmed = raw.trim().take(10)
        val parsed = java.time.LocalDate.parse(trimmed)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.US)
        formatter.format(parsed)
    } catch (_: Exception) {
        raw
    }
}

private fun formatEpisodeBadge(seasonNumber: Int?, episodeNumber: Int?): String {
    return when {
        seasonNumber != null && seasonNumber > 0 && episodeNumber != null -> "S$seasonNumber E$episodeNumber"
        seasonNumber != null && seasonNumber <= 0 && episodeNumber != null -> "SP $episodeNumber"
        episodeNumber != null -> "E$episodeNumber"
        else -> ""
    }
}

private fun formatSeasonLabel(seasonNumber: Int): String {
    return if (seasonNumber <= 0) "Specials" else "Season $seasonNumber"
}

/**
 * Mode 1: Nuvio Horizontal Carousel Row (1:1 Nuvio Phone Metrics: 296dp x 184dp, 12dp spacing, 12dp corners)
 */
@Composable
private fun EpisodeHorizontalRow(
    episodes: List<MediaEpisode>,
    watchedEpisodes: Set<Pair<Int, Int>>,
    fallbackImageUrl: String?,
    scale: Float,
    onToggleWatched: (MediaEpisode) -> Unit,
    onLongPress: (MediaEpisode) -> Unit
) {
    val cardWidth = (296.dp * scale).coerceAtLeast(220.dp)
    val cardHeight = (184.dp * scale).coerceAtLeast(136.dp)

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(episodes, key = { "${it.seasonNumber}_${it.episodeNumber}_${it.id}" }) { episode ->
            val isWatched = (episode.seasonNumber to episode.episodeNumber) in watchedEpisodes
            EpisodeHorizontalCard(
                episode = episode,
                isWatched = isWatched,
                fallbackImageUrl = fallbackImageUrl,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                onToggleWatched = { onToggleWatched(episode) },
                onLongPress = { onLongPress(episode) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeHorizontalCard(
    episode: MediaEpisode,
    isWatched: Boolean,
    fallbackImageUrl: String?,
    cardWidth: Dp,
    cardHeight: Dp,
    onToggleWatched: () -> Unit,
    onLongPress: () -> Unit
) {
    val cardShape = RoundedCornerShape(12.dp)
    val imgModel = episode.stillUrl ?: fallbackImageUrl

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(cardShape)
            .background(DarkSurfaceElevated)
            .border(1.dp, GlassBorder, cardShape)
            .combinedClickable(
                onClick = onToggleWatched,
                onLongClick = onLongPress
            )
    ) {
        var loadFailed by remember(imgModel) { mutableStateOf(false) }
        val effectiveUrl = if (loadFailed && !fallbackImageUrl.isNullOrBlank()) fallbackImageUrl else (imgModel ?: fallbackImageUrl)
        if (!effectiveUrl.isNullOrBlank()) {
            AsyncImage(
                model = effectiveUrl,
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true }
            )
        }

        // 1:1 Nuvio Multi-Stop Dark Bottom Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.42f to Color.Transparent,
                        0.56f to Color.Black.copy(alpha = 0.20f),
                        0.70f to Color.Black.copy(alpha = 0.45f),
                        0.84f to Color.Black.copy(alpha = 0.68f),
                        1f to Color.Black.copy(alpha = 0.92f)
                    )
                )
        )

        // 1:1 Nuvio Watched Badge Circle on Top Right (hidden when unwatched)
        NuvioAnimatedWatchedBadge(
            isWatched = isWatched,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
        )

        // In-Image Content Overlay at Bottom Start (1:1 Nuvio Phone Metrics)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val badgeText = formatEpisodeBadge(episode.seasonNumber, episode.episodeNumber)
            if (badgeText.isNotBlank()) {
                EpisodeCodeBadge(
                    text = badgeText,
                    textSize = 10.sp,
                    radius = 4.dp,
                    horizontalPadding = 6.dp,
                    verticalPadding = 2.dp,
                    backgroundAlpha = 0.45f
                )
            }

            val titleText = episode.title.ifBlank { "Episode ${episode.episodeNumber}" }
            Text(
                text = titleText,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 19.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!episode.overview.isNullOrBlank()) {
                Text(
                    text = episode.overview,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Bottom Metadata Line: Runtime, IMDb Rating, Air Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (episode.runtimeMinutes != null && episode.runtimeMinutes > 0) {
                    Text(
                        text = "${episode.runtimeMinutes}m",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
                if (episode.voteAverage != null && episode.voteAverage > 0) {
                    ImdbEpisodeRatingBadge(
                        rating = episode.voteAverage,
                        logoWidth = 22.dp,
                        logoHeight = 11.dp,
                        textSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                val formattedDate = remember(episode.airDate) { formatEpisodeAirDate(episode.airDate) }
                if (!formattedDate.isNullOrBlank()) {
                    Text(
                        text = formattedDate,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
            }
        }
    }
}

/**
 * Mode 2: Nuvio Compact List (1:1 Nuvio Phone Metrics: 120x120dp square thumbnail with badge on top-left, watched on top-right, metadata on right)
 */
@Composable
private fun EpisodeListColumn(
    episodes: List<MediaEpisode>,
    watchedEpisodes: Set<Pair<Int, Int>>,
    fallbackImageUrl: String?,
    scale: Float,
    onToggleWatched: (MediaEpisode) -> Unit,
    onLongPress: (MediaEpisode) -> Unit
) {
    val cardHeight = (120.dp * scale).coerceAtLeast(96.dp)
    val imageWidth = (120.dp * scale).coerceAtLeast(96.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        episodes.forEach { episode ->
            val isWatched = (episode.seasonNumber to episode.episodeNumber) in watchedEpisodes
            EpisodeListCardItem(
                episode = episode,
                isWatched = isWatched,
                fallbackImageUrl = fallbackImageUrl,
                cardHeight = cardHeight,
                imageWidth = imageWidth,
                onToggleWatched = { onToggleWatched(episode) },
                onLongPress = { onLongPress(episode) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeListCardItem(
    episode: MediaEpisode,
    isWatched: Boolean,
    fallbackImageUrl: String?,
    cardHeight: Dp,
    imageWidth: Dp,
    onToggleWatched: () -> Unit,
    onLongPress: () -> Unit
) {
    val cardShape = RoundedCornerShape(8.dp)
    val imgModel = episode.stillUrl ?: fallbackImageUrl

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clip(cardShape)
            .background(Color(0xFF222224).copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape)
            .combinedClickable(
                onClick = onToggleWatched,
                onLongClick = onLongPress
            )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left: Square Thumbnail Still with 1:1 Nuvio Top-Left Code Badge & Top-Right Watched Circle
            Box(
                modifier = Modifier
                    .width(imageWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(Color(0xFF0D0D0D))
            ) {
                var loadFailed by remember(imgModel) { mutableStateOf(false) }
                val effectiveUrl = if (loadFailed && !fallbackImageUrl.isNullOrBlank()) fallbackImageUrl else (imgModel ?: fallbackImageUrl)
                if (!effectiveUrl.isNullOrBlank() && !loadFailed) {
                    AsyncImage(
                        model = effectiveUrl,
                        contentDescription = episode.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { loadFailed = true }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF141416)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatEpisodeBadge(episode.seasonNumber, episode.episodeNumber),
                            color = Color.White.copy(alpha = 0.30f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 1:1 Nuvio Top-Left Episode Code Badge
                val badgeText = formatEpisodeBadge(episode.seasonNumber, episode.episodeNumber)
                if (badgeText.isNotBlank()) {
                    EpisodeCodeBadge(
                        text = badgeText,
                        textSize = 10.sp,
                        radius = 4.dp,
                        horizontalPadding = 6.dp,
                        verticalPadding = 2.dp,
                        backgroundAlpha = 0.45f,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 8.dp)
                    )
                }

                // 1:1 Nuvio Top-Right Watched Circle (solid crimson/magenta with white checkmark)
                NuvioAnimatedWatchedBadge(
                    isWatched = isWatched,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                )
            }

            // Right: Episode Metadata Column (1:1 Nuvio Metrics: 15sp title, 12sp subtitle row, 13sp overview)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val titleText = episode.title.ifBlank { "Episode ${episode.episodeNumber}" }
                Text(
                    text = titleText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val formattedDate = remember(episode.airDate) { formatEpisodeAirDate(episode.airDate) }
                if (!formattedDate.isNullOrBlank() || (episode.voteAverage != null && episode.voteAverage > 0)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!formattedDate.isNullOrBlank()) {
                            Text(
                                text = formattedDate,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (episode.voteAverage != null && episode.voteAverage > 0) {
                            ImdbEpisodeRatingBadge(
                                rating = episode.voteAverage,
                                logoWidth = 24.dp,
                                logoHeight = 12.dp,
                                textSize = 12.sp
                            )
                        }
                    }
                }

                if (!episode.overview.isNullOrBlank()) {
                    Text(
                        text = episode.overview,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color.White.copy(alpha = 0.60f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Mode 3: Vertical Hero Cards (Full-Width 16:9 vertical cards with in-image metadata)
 * The third setting requested: looks like the horizontal card, but arranged vertically.
 */
@Composable
private fun EpisodeVerticalCardColumn(
    episodes: List<MediaEpisode>,
    watchedEpisodes: Set<Pair<Int, Int>>,
    fallbackImageUrl: String?,
    scale: Float,
    onToggleWatched: (MediaEpisode) -> Unit,
    onLongPress: (MediaEpisode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        episodes.forEach { episode ->
            val isWatched = (episode.seasonNumber to episode.episodeNumber) in watchedEpisodes
            EpisodeVerticalCardItem(
                episode = episode,
                isWatched = isWatched,
                fallbackImageUrl = fallbackImageUrl,
                scale = scale,
                onToggleWatched = { onToggleWatched(episode) },
                onLongPress = { onLongPress(episode) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeVerticalCardItem(
    episode: MediaEpisode,
    isWatched: Boolean,
    fallbackImageUrl: String?,
    scale: Float,
    onToggleWatched: () -> Unit,
    onLongPress: () -> Unit
) {
    val cardShape = RoundedCornerShape(16.dp)
    val imgModel = episode.stillUrl ?: fallbackImageUrl

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(cardShape)
            .background(DarkSurfaceElevated)
            .border(1.dp, GlassBorder, cardShape)
            .combinedClickable(
                onClick = onToggleWatched,
                onLongClick = onLongPress
            )
    ) {
        var loadFailed by remember(imgModel) { mutableStateOf(false) }
        val effectiveUrl = if (loadFailed && !fallbackImageUrl.isNullOrBlank()) fallbackImageUrl else (imgModel ?: fallbackImageUrl)
        if (!effectiveUrl.isNullOrBlank()) {
            AsyncImage(
                model = effectiveUrl,
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true }
            )
        }

        // Multi-Stop Gradient inside image
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Transparent,
                        0.55f to Color.Black.copy(alpha = 0.25f),
                        0.72f to Color.Black.copy(alpha = 0.55f),
                        0.88f to Color.Black.copy(alpha = 0.80f),
                        1f to Color.Black.copy(alpha = 0.95f)
                    )
                )
        )

        // Top-Right Watched Circle (hidden when unwatched)
        NuvioAnimatedWatchedBadge(
            isWatched = isWatched,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
        )

        // In-Image Metadata Overlay at Bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val badgeText = formatEpisodeBadge(episode.seasonNumber, episode.episodeNumber)
            if (badgeText.isNotBlank()) {
                EpisodeCodeBadge(
                    text = badgeText,
                    textSize = 10.sp,
                    radius = 4.dp,
                    horizontalPadding = 6.dp,
                    verticalPadding = 2.dp,
                    backgroundAlpha = 0.45f
                )
            }

            val titleText = episode.title.ifBlank { "Episode ${episode.episodeNumber}" }
            Text(
                text = titleText,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!episode.overview.isNullOrBlank()) {
                Text(
                    text = episode.overview,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (episode.runtimeMinutes != null && episode.runtimeMinutes > 0) {
                    Text(text = "${episode.runtimeMinutes}m", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                }
                if (episode.voteAverage != null && episode.voteAverage > 0) {
                    ImdbEpisodeRatingBadge(rating = episode.voteAverage)
                }
                Spacer(modifier = Modifier.weight(1f))
                val formattedDate = remember(episode.airDate) { formatEpisodeAirDate(episode.airDate) }
                if (!formattedDate.isNullOrBlank()) {
                    Text(text = formattedDate, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@Composable
private fun SeasonPosterScrollRow(
    seasons: List<MediaSeason>,
    selectedSeasonNumber: Int,
    episodes: List<MediaEpisode>,
    episodesBySeason: Map<Int, List<MediaEpisode>>,
    watchedEpisodes: Set<Pair<Int, Int>>,
    fallbackImageUrl: String?,
    onSelectSeason: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedSeasonNumber) {
        val idx = seasons.indexOfFirst { it.seasonNumber == selectedSeasonNumber }
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(seasons, key = { it.seasonNumber }) { season ->
            val isSelected = season.seasonNumber == selectedSeasonNumber
            val seasonEps = episodesBySeason[season.seasonNumber] ?: episodes.filter { it.seasonNumber == season.seasonNumber }
            val watchedCount = seasonEps.count { (it.seasonNumber to it.episodeNumber) in watchedEpisodes }
            val allWatched = season.episodeCount > 0 && watchedCount >= season.episodeCount
            val label = formatSeasonLabel(season.seasonNumber)

            Column(
                modifier = Modifier
                    .width(100.dp)
                    .clickable { onSelectSeason(season.seasonNumber) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceElevated)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFFE52E5E) else Color.White.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    val posterModel = season.posterUrl ?: fallbackImageUrl
                    var loadFailed by remember(posterModel) { mutableStateOf(false) }
                    val effectivePoster = if (loadFailed && !fallbackImageUrl.isNullOrBlank()) fallbackImageUrl else posterModel
                    if (!effectivePoster.isNullOrBlank()) {
                        AsyncImage(
                            model = effectivePoster,
                            contentDescription = label,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onError = { loadFailed = true }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Text(
                    text = label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.60f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SeasonTextChipScrollRow(
    seasons: List<MediaSeason>,
    selectedSeasonNumber: Int,
    episodes: List<MediaEpisode>,
    episodesBySeason: Map<Int, List<MediaEpisode>>,
    watchedEpisodes: Set<Pair<Int, Int>>,
    onSelectSeason: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedSeasonNumber) {
        val idx = seasons.indexOfFirst { it.seasonNumber == selectedSeasonNumber }
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(seasons, key = { it.seasonNumber }) { season ->
            val isSelected = season.seasonNumber == selectedSeasonNumber
            val seasonEps = episodesBySeason[season.seasonNumber] ?: episodes.filter { it.seasonNumber == season.seasonNumber }
            val watchedCount = seasonEps.count { (it.seasonNumber to it.episodeNumber) in watchedEpisodes }
            val allWatched = season.episodeCount > 0 && watchedCount >= season.episodeCount
            val label = formatSeasonLabel(season.seasonNumber)

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent
                    )
                    .clickable { onSelectSeason(season.seasonNumber) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (allWatched) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = AccentJade,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                )
            }
        }
    }
}
