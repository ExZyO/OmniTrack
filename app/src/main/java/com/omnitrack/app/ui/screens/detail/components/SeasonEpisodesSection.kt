package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.ui.components.Episode
import com.omnitrack.app.ui.components.Season
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * Nuvio-style Season Poster Selector + Episode Checklist
 * for TV Shows and Anime series.
 */
@Composable
fun SeasonEpisodesSection(
    seasons: List<Season>,
    currentProgress: Double,
    onToggleEpisodeWatched: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (seasons.isEmpty()) return

    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    val currentSeason = seasons.getOrNull(selectedSeasonIndex) ?: seasons.first()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Seasons & Episodes",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "${currentSeason.episodes.size} episodes",
                fontSize = 11.5.sp,
                color = TextMuted
            )
        }

        // Season Poster Selector Tabs (Horizontal)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            seasons.forEachIndexed { index, season ->
                val isSelected = index == selectedSeasonIndex
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) AccentJade else DarkSurfaceElevated)
                        .clickable { selectedSeasonIndex = index }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = season.name,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (isSelected) AmoledBlack else TextPrimary
                    )
                }
            }
        }

        // Episode Checklist Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            currentSeason.episodes.forEach { episode ->
                val isWatched = episode.number <= currentProgress.toInt()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, if (isWatched) AccentJade.copy(alpha = 0.25f) else GlassBorder, RoundedCornerShape(20.dp))
                        .clickable { onToggleEpisodeWatched(episode.number, !isWatched) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Episode Number / Thumbnail box
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isWatched) AccentJade.copy(alpha = 0.18f) else AmoledBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "E${episode.number}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isWatched) AccentJade else TextSecondary
                        )
                    }

                    // Title & Runtime
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = episode.title,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWatched) TextMuted else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Episode ${episode.number}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            if (!episode.watchDate.isNullOrBlank()) {
                                Text("• ${episode.watchDate}", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }

                    // 1-Tap Checkmark Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isWatched) AccentJade else AmoledBlack)
                            .border(1.dp, if (isWatched) AccentJade else GlassBorder, CircleShape),
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
            }
        }
    }
}
