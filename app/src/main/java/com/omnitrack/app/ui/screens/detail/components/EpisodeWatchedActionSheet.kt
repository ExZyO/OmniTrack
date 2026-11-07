package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlaylistAddCheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.model.MediaEpisode
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeWatchedActionSheet(
    episode: MediaEpisode,
    seasonLabel: String,
    isEpisodeWatched: Boolean,
    canMarkPreviousEpisodes: Boolean,
    isSeasonWatched: Boolean,
    onDismiss: () -> Unit,
    onToggleWatched: () -> Unit,
    onMarkPreviousWatched: () -> Unit,
    onToggleSeasonWatched: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${seasonLabel.uppercase()} • EPISODE ${episode.episodeNumber}",
                    color = AccentJade,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = episode.title,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = GlassBorder, thickness = 0.8.dp)

            // Actions List
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 1. Mark Single Episode Watched / Unwatched
                EpisodeActionRow(
                    icon = Icons.Default.CheckCircle,
                    iconTint = if (isEpisodeWatched) Color(0xFFEF4444) else AccentJade,
                    title = if (isEpisodeWatched) "Mark as Unwatched" else "Mark as Watched",
                    subtitle = if (isEpisodeWatched) "Remove from watched history" else "Mark Episode ${episode.episodeNumber} as watched",
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onToggleWatched()
                            onDismiss()
                        }
                    }
                )

                // 2. Mark Previous Episodes as Watched
                if (canMarkPreviousEpisodes) {
                    EpisodeActionRow(
                        icon = Icons.Default.DoneAll,
                        iconTint = AccentJade,
                        title = "Mark Previous Episodes Watched",
                        subtitle = "Mark Episodes 1 to ${episode.episodeNumber} as watched",
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onMarkPreviousWatched()
                                onDismiss()
                            }
                        }
                    )
                }

                // 3. Mark Season Watched / Unwatched
                EpisodeActionRow(
                    icon = Icons.Default.PlaylistAddCheckCircle,
                    iconTint = if (isSeasonWatched) Color(0xFFEF4444) else AccentJade,
                    title = if (isSeasonWatched) "Mark Season Unwatched" else "Mark Season Watched",
                    subtitle = if (isSeasonWatched) "Clear watched state for $seasonLabel" else "Mark all episodes in $seasonLabel as watched",
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onToggleSeasonWatched()
                            onDismiss()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EpisodeActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = TextMuted
            )
        }
    }
}
