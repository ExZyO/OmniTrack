package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.model.MediaEpisode
import com.omnitrack.app.data.model.MediaSeason
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * 1:1 Nuvio Parity: Seasons & Episode Tracking Grid
 * Horizontal season selector chips, 16:9 episode cards with circular check badges,
 * and bilateral/action-sheet integration.
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
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
            val currentSeason = seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber }
            val totalInSeason = currentSeason?.episodeCount ?: episodes.size
            val watchedInSeason = episodes.count { it.seasonNumber == selectedSeasonNumber && (it.seasonNumber to it.episodeNumber) in watchedEpisodes }
            Text(
                text = "$watchedInSeason of $totalInSeason watched",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Horizontal Season Selector Chips
        if (seasons.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(seasons, key = { it.seasonNumber }) { season ->
                    val isSelected = season.seasonNumber == selectedSeasonNumber
                    val watchedCount = episodes.count { it.seasonNumber == season.seasonNumber && (it.seasonNumber to it.episodeNumber) in watchedEpisodes }
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

        // Episode List for the Selected Season
        val seasonEpisodes = remember(episodes, selectedSeasonNumber) {
            episodes.filter { it.seasonNumber == selectedSeasonNumber }
        }

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

    val watchedBadgeColor by animateColorAsState(
        targetValue = if (isWatched) AccentJade else AmoledBlack.copy(alpha = 0.70f),
        label = "watched_color"
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

            // Top-Right Circular Watched Badge (1:1 Nuvio Parity)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .scale(watchedBadgeScale)
                    .clip(CircleShape)
                    .background(watchedBadgeColor)
                    .border(1.dp, if (isWatched) AccentJade else Color.White.copy(alpha = 0.25f), CircleShape)
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
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.4f))
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
