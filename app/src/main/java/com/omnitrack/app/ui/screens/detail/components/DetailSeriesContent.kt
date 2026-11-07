package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.model.MediaEpisode
import com.omnitrack.app.data.model.MediaSeason
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
 * 1:1 Reference Parity: Seasons & Episode Tracking Grid
 * Supporting Season Posters Rail, Text Chips selector, mode toggle button,
 * 16:9 episode cards with exact circular checkmark badge, and action sheet integration.
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
    modifier: Modifier = Modifier
) {
    if (seasons.isEmpty() && episodes.isEmpty()) return

    var activeActionSheetEpisode by remember { mutableStateOf<MediaEpisode?>(null) }
    var seasonViewMode by remember { mutableStateOf("Posters") }
    val hasSeasonPosters = remember(seasons) { seasons.any { !it.posterUrl.isNullOrBlank() } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Seasons Section Header with "Posters" / "Text" Toggle
        if (seasons.size > 1) {
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
                                seasonViewMode = if (seasonViewMode == "Posters") "Text" else "Posters"
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (seasonViewMode == "Posters") "Posters" else "Text",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                }
            }

            // 2. Season Selector (Posters Rail vs Text Chips)
            Crossfade(
                targetState = seasonViewMode,
                animationSpec = tween(250),
                label = "season_view_mode"
            ) { mode ->
                if (mode == "Posters" && hasSeasonPosters) {
                    SeasonPosterScrollRow(
                        seasons = seasons,
                        selectedSeasonNumber = selectedSeasonNumber,
                        episodes = episodes,
                        watchedEpisodes = watchedEpisodes,
                        fallbackImageUrl = fallbackBackdropUrl,
                        onSelectSeason = onSelectSeason
                    )
                } else {
                    SeasonTextChipScrollRow(
                        seasons = seasons,
                        selectedSeasonNumber = selectedSeasonNumber,
                        episodes = episodes,
                        watchedEpisodes = watchedEpisodes,
                        onSelectSeason = onSelectSeason
                    )
                }
            }
        }

        // 3. Episodes Header
        val seasonEpisodes = remember(episodes, selectedSeasonNumber) {
            episodes.filter { it.seasonNumber == selectedSeasonNumber }
        }
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
            Text(
                text = "Episodes",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            Text(
                text = "$watchedInSeason of $totalInSeason watched",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 4. Episode List for the Selected Season
        AnimatedContent(
            targetState = selectedSeasonNumber,
            transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith fadeOut() },
            label = "season_switch"
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                seasonEpisodes.forEach { episode ->
                    val isWatched = (episode.seasonNumber to episode.episodeNumber) in watchedEpisodes
                    EpisodeCard(
                        episode = episode,
                        isWatched = isWatched,
                        fallbackImageUrl = fallbackBackdropUrl,
                        onToggleWatched = { onToggleEpisodeWatched(episode) },
                        onLongPress = { activeActionSheetEpisode = episode }
                    )
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

@Composable
private fun SeasonPosterScrollRow(
    seasons: List<MediaSeason>,
    selectedSeasonNumber: Int,
    episodes: List<MediaEpisode>,
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
            val seasonEps = episodes.filter { it.seasonNumber == season.seasonNumber }
            val watchedCount = seasonEps.count { (it.seasonNumber to it.episodeNumber) in watchedEpisodes }
            val allWatched = season.episodeCount > 0 && watchedCount >= season.episodeCount

            Column(
                modifier = Modifier
                    .width(105.dp)
                    .clickable { onSelectSeason(season.seasonNumber) },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceElevated)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) AccentJade else GlassBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                ) {
                    val posterModel = season.posterUrl ?: fallbackImageUrl
                    if (!posterModel.isNullOrBlank()) {
                        AsyncImage(
                            model = posterModel,
                            contentDescription = season.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (allWatched) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E054)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = AmoledBlack,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Text(
                    text = season.name,
                    color = if (isSelected) Color.White else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    watchedEpisodes: Set<Pair<Int, Int>>,
    onSelectSeason: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(seasons, key = { it.seasonNumber }) { season ->
            val isSelected = season.seasonNumber == selectedSeasonNumber
            val seasonEps = episodes.filter { it.seasonNumber == season.seasonNumber }
            val watchedCount = seasonEps.count { (it.seasonNumber to it.episodeNumber) in watchedEpisodes }
            val allWatched = season.episodeCount > 0 && watchedCount >= season.episodeCount

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) Color.White else DarkSurfaceElevated)
                    .border(1.dp, if (isSelected) Color.White else GlassBorder, RoundedCornerShape(999.dp))
                    .clickable { onSelectSeason(season.seasonNumber) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (allWatched) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isSelected) AmoledBlack else AccentJade,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = season.name,
                    color = if (isSelected) AmoledBlack else TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeCard(
    episode: MediaEpisode,
    isWatched: Boolean,
    fallbackImageUrl: String?,
    onToggleWatched: () -> Unit,
    onLongPress: () -> Unit
) {
    var expandedOverview by remember { mutableStateOf(false) }

    val watchedBadgeScale by animateFloatAsState(
        targetValue = if (isWatched) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "watched_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onToggleWatched,
                onLongClick = onLongPress
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 16:9 Thumbnail Still with Floating Circular Watched Badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceCard)
        ) {
            val imgModel = episode.stillUrl ?: fallbackImageUrl
            if (imgModel != null) {
                AsyncImage(
                    model = imgModel,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Subtle dark bottom gradient inside the thumbnail
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.50f to Color.Transparent,
                            1f to AmoledBlack.copy(alpha = 0.70f)
                        )
                    )
            )

            // Episode Code Badge at bottom start of thumbnail (e.g. S01E04)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AmoledBlack.copy(alpha = 0.75f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "S%02dE%02d".format(episode.seasonNumber, episode.episodeNumber),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentJade
                )
            }

            // Top-Right Circular Watched Badge (1:1 Reference Parity)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .scale(watchedBadgeScale)
                    .clip(CircleShape)
                    .background(if (isWatched) Color(0xFF00E054) else AmoledBlack.copy(alpha = 0.55f))
                    .border(
                        width = 1.5.dp,
                        color = if (isWatched) Color(0xFF00E054) else Color.White.copy(alpha = 0.35f),
                        shape = CircleShape
                    )
                    .clickable(onClick = onToggleWatched),
                contentAlignment = Alignment.Center
            ) {
                if (isWatched) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Watched",
                        tint = AmoledBlack,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Episode Metadata & Title
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EPISODE ${episode.episodeNumber}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentJade,
                    letterSpacing = 0.8.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (episode.voteAverage != null && episode.voteAverage > 0) {
                        Text(
                            text = "★ " + String.format(java.util.Locale.US, "%.1f", episode.voteAverage),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold
                        )
                    }
                    if (episode.runtimeMinutes != null && episode.runtimeMinutes > 0) {
                        Text(
                            text = "${episode.runtimeMinutes}m",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    if (!episode.airDate.isNullOrBlank()) {
                        Text(
                            text = "• ${episode.airDate}",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            Text(
                text = episode.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!episode.overview.isNullOrBlank()) {
                Text(
                    text = episode.overview,
                    fontSize = 12.5.sp,
                    color = TextSecondary,
                    maxLines = if (expandedOverview) 10 else 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                    modifier = Modifier
                        .animateContentSize()
                        .clickable { expandedOverview = !expandedOverview }
                )
            }
        }
    }
}
