package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlaylistAddCheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonWatchedActionSheet(
    seasonLabel: String,
    episodeCount: Int,
    watchedCount: Int,
    isSeasonWatched: Boolean,
    canMarkPreviousSeasons: Boolean,
    isReadingMode: Boolean = false,
    initialRating: Float? = null,
    initialNote: String? = null,
    onSaveSeasonMeta: ((Float?, String?) -> Unit)? = null,
    onDismiss: () -> Unit,
    onToggleSeasonWatched: () -> Unit,
    onMarkPreviousSeasonsWatched: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val verbPos = if (isReadingMode) "Read" else "Watched"
    val verbNeg = if (isReadingMode) "Unread" else "Unwatched"
    val pluralUnits = if (isReadingMode) "Volumes" else "Seasons"
    val unitSingular = if (isReadingMode) "Volume" else "Season"

    // 0.9f = Unrated (< 0.95f), 1.0f..10.0f = 0.1 steps
    var sliderValue by remember(seasonLabel, initialRating) {
        mutableStateOf(
            if (initialRating == null || initialRating <= 0f) 0.9f
            else initialRating.coerceIn(1.0f, 10.0f)
        )
    }
    var noteText by remember(seasonLabel, initialNote) {
        mutableStateOf(initialNote.orEmpty())
    }
    val isUnrated = sliderValue < 0.95f
    fun updateScore(raw: Float) {
        if (raw < 0.95f) {
            sliderValue = 0.9f
        } else {
            val snapped = ((raw * 10f).roundToInt() / 10f).coerceIn(1.0f, 10.0f)
            sliderValue = snapped
        }
    }
    val effectiveRating: Float? = if (isUnrated) null else ((sliderValue * 10f).roundToInt() / 10f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = seasonLabel.uppercase(),
                        color = AccentJade,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (episodeCount > 0) {
                            "$watchedCount of $episodeCount ${if (isReadingMode) "read" else "watched"}"
                        } else {
                            "Actions & notes for $seasonLabel"
                        },
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!isUnrated && onSaveSeasonMeta != null) {
                    Text(
                        text = "Remove Rating",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { sliderValue = 0.9f }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (onSaveSeasonMeta != null) {
                HorizontalDivider(color = GlassBorder, thickness = 0.8.dp)

                // Season Rating Slider Section (0.1 precision + circular hold steppers + delta buttons)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${unitSingular.uppercase()} RATING",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RepeatingHoldButton(
                            onStep = {
                                if (sliderValue <= 1.05f) sliderValue = 0.9f
                                else updateScore(sliderValue - 0.1f)
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceElevated)
                                .border(0.8.dp, GlassBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease season rating",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isUnrated) TextMuted else AccentGold,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (isUnrated) {
                                    "Unrated"
                                } else {
                                    "${String.format(Locale.US, "%.1f", sliderValue)} / 10"
                                },
                                color = if (isUnrated) TextMuted else TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        RepeatingHoldButton(
                            onStep = {
                                if (isUnrated) updateScore(5.0f)
                                else updateScore(sliderValue + 0.1f)
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceElevated)
                                .border(0.8.dp, GlassBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase season rating",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Slider(
                        value = sliderValue,
                        onValueChange = { updateScore(it) },
                        valueRange = 0.9f..10.0f,
                        steps = 90,
                        colors = SliderDefaults.colors(
                            thumbColor = if (isUnrated) TextMuted else AccentGold,
                            activeTrackColor = if (isUnrated) TextMuted.copy(alpha = 0.35f) else AccentGold,
                            inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("Unrated" to 0.9f, "1.0" to 1.0f, "5.0" to 5.0f, "10.0" to 10.0f).forEach { (label, target) ->
                            Text(
                                text = label,
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { updateScore(target) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // 6 Precision Delta Buttons
                    val deltas = listOf(-1.0f, -0.5f, -0.1f, 0.1f, 0.5f, 1.0f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        deltas.forEach { d ->
                            val sign = if (d > 0) "+" else ""
                            OutlinedButton(
                                onClick = {
                                    val base = if (isUnrated) 5.0f else sliderValue
                                    updateScore(base + d)
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                border = androidx.compose.foundation.BorderStroke(0.7.dp, GlassBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Text(
                                    text = "$sign${String.format(Locale.US, "%.1f", d)}",
                                    color = if (d > 0) AccentGold else TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Personal Season Note
                    Text(
                        text = "PERSONAL ${unitSingular.uppercase()} NOTE",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = {
                            Text(
                                text = "Thoughts on pacing, arcs, animation, standout episodes...",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        },
                        maxLines = 4,
                        minLines = 2,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = Color(0xFF0A0A0A),
                            unfocusedContainerColor = Color(0xFF0A0A0A)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val trimmedNote = noteText.trim().takeIf { it.isNotBlank() }
                            onSaveSeasonMeta(effectiveRating, trimmedNote)
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGold,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "Save $unitSingular Rating & Note",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(color = GlassBorder, thickness = 0.8.dp)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EpisodeActionRow(
                    icon = Icons.Default.PlaylistAddCheckCircle,
                    iconTint = if (isSeasonWatched) Color(0xFFEF4444) else AccentJade,
                    title = if (isSeasonWatched) "Mark $seasonLabel as $verbNeg" else "Mark $seasonLabel as $verbPos",
                    subtitle = if (isSeasonWatched) {
                        "Clear ${verbPos.lowercase()} state for $seasonLabel"
                    } else {
                        "Mark all items in $seasonLabel as ${verbPos.lowercase()}"
                    },
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onToggleSeasonWatched()
                            onDismiss()
                        }
                    }
                )

                if (canMarkPreviousSeasons) {
                    EpisodeActionRow(
                        icon = Icons.Default.DoneAll,
                        iconTint = AccentJade,
                        title = "Mark Previous $pluralUnits as $verbPos",
                        subtitle = "Mark all earlier ${pluralUnits.lowercase()} before $seasonLabel as ${verbPos.lowercase()}",
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onMarkPreviousSeasonsWatched()
                                onDismiss()
                            }
                        }
                    )
                }
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
