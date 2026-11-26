package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.preferences.EpisodeLayoutMode
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.ui.components.EpisodeLayoutWireframe
import com.omnitrack.app.ui.components.SeasonSelectorWireframe
import com.omnitrack.app.ui.components.VisualOptionTile
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeLayoutSheet(
    preferenceManager: PreferenceManager,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val currentMode by preferenceManager.episodeLayoutMode.collectAsState(initial = EpisodeLayoutMode.HORIZONTAL)
    val currentScale by preferenceManager.episodeLayoutScale.collectAsState(initial = 1.0f)
    val seasonViewMode by preferenceManager.seasonViewMode.collectAsState(initial = "Posters")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                )
            }

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Episode Layout & Sizing",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Customize series & anime tracking presentation",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Section 1: Episode Layout Mode (2x2 Visual Wireframes)
            Text(
                text = "LAYOUT STYLE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = AccentGold
            )

            val modeEntries = listOf(
                Triple(EpisodeLayoutMode.HORIZONTAL, "Horizontal Carousel", "16:9 widescreen cards"),
                Triple(EpisodeLayoutMode.LIST, "Compact List", "Thumbnail & title rows"),
                Triple(EpisodeLayoutMode.VERTICAL_CARD, "Hero Cards", "Full-width 16:9 stack"),
                Triple(EpisodeLayoutMode.POSTER_GRID, "Poster Grid", "3-column cover grid"),
                Triple(EpisodeLayoutMode.TEXT_ONLY, "Text Checklist", "Clean text-only rows (Books style)")
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                modeEntries.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { (mode, title, subtitle) ->
                            VisualOptionTile(
                                title = title,
                                subtitle = subtitle,
                                isSelected = currentMode == mode,
                                modifier = Modifier.weight(1f),
                                previewHeight = 64.dp,
                                onClick = {
                                    scope.launch { preferenceManager.updateEpisodeLayoutMode(mode) }
                                }
                            ) {
                                EpisodeLayoutWireframe(mode = mode)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                thickness = 0.6.dp
            )

            // Section 2: Card Size Scaling
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EPISODE CARD SIZE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = AccentGold
                )
                Text(
                    text = "${(currentScale * 100).roundToInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold
                )
            }

            // Size presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Compact" to 0.85f,
                    "Standard" to 1.0f,
                    "Expanded" to 1.15f,
                    "Large" to 1.30f
                ).forEach { (label, scaleVal) ->
                    val isSelected = kotlin.math.abs(currentScale - scaleVal) < 0.05f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) AccentGold.copy(alpha = 0.16f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .clickable {
                                scope.launch { preferenceManager.updateEpisodeLayoutScale(scaleVal) }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) AccentGold else TextSecondary
                        )
                    }
                }
            }

            Slider(
                value = currentScale,
                onValueChange = {
                    scope.launch { preferenceManager.updateEpisodeLayoutScale(it) }
                },
                valueRange = 0.80f..1.35f,
                colors = SliderDefaults.colors(
                    thumbColor = AccentGold,
                    activeTrackColor = AccentGold,
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                )
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                thickness = 0.6.dp
            )

            // Section 3: Seasons View Mode (2-column Visual Wireframe Picker)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "SEASON SELECTOR DEFAULT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = AccentGold
                )
                Text(
                    text = "Display season poster artwork rail or compact text pills",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    Triple("Posters", "Poster Rail", "Visual season covers"),
                    Triple("Text", "Compact Chips", "Clean horizontal labels")
                ).forEach { (mode, title, subtitle) ->
                    VisualOptionTile(
                        title = title,
                        subtitle = subtitle,
                        isSelected = seasonViewMode == mode,
                        modifier = Modifier.weight(1f),
                        previewHeight = 58.dp,
                        onClick = {
                            scope.launch { preferenceManager.updateSeasonViewMode(mode) }
                        }
                    ) {
                        SeasonSelectorWireframe(isPosters = (mode == "Posters"))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
