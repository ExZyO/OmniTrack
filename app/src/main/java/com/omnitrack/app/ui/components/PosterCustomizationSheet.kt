package com.omnitrack.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.omnitrack.app.data.preferences.PosterCardStyle
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private data class PresetOption<T>(val label: String, val value: T)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PosterCustomizationSheet(
    preferenceManager: PreferenceManager,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val style by preferenceManager.posterCardStyle.collectAsState(initial = PosterCardStyle())

    var draftEdge by remember(style.cardDepthEdgeStrength) { mutableFloatStateOf(style.cardDepthEdgeStrength.toFloat()) }
    var draftSheen by remember(style.cardDepthSheenStrength) { mutableFloatStateOf(style.cardDepthSheenStrength.toFloat()) }
    var draftCoverage by remember(style.cardDepthEdgeCoverage) { mutableFloatStateOf(style.cardDepthEdgeCoverage.toFloat()) }

    val widthPresets = listOf(
        PresetOption("Compact", 104),
        PresetOption("Dense", 114),
        PresetOption("Standard", 126),
        PresetOption("Comfort", 134),
        PresetOption("Large", 144),
        PresetOption("X-Large", 164),
        PresetOption("Max", 185),
        PresetOption("Ultra", 205),
        PresetOption("Widescreen", 230)
    )

    val radiusPresets = listOf(
        PresetOption("Sharp", 0),
        PresetOption("Subtle", 4),
        PresetOption("Classic", 8),
        PresetOption("Rounded", 12),
        PresetOption("Pill", 16),
        PresetOption("Smooth", 20),
        PresetOption("High", 24)
    )

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
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AccentArctic.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewModule,
                            contentDescription = null,
                            tint = AccentArctic,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Poster Card Style",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Customize grid sizing, corner curvature & layout",
                            fontSize = 11.5.sp,
                            color = TextMuted
                        )
                    }
                }

                IconButton(
                    onClick = {
                        scope.launch { preferenceManager.resetPosterStyle() }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset to Defaults",
                        tint = TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Live Interactive Preview Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "LIVE PREVIEW • ${style.widthDp}dp × ${if (style.isLandscape) "16:9" else "2:3"} • ${style.cornerRadiusDp}dp radius",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentArctic,
                    letterSpacing = 0.5.sp
                )

                val previewCornerRadius by animateDpAsState(
                    targetValue = style.cornerRadiusDp.dp,
                    label = "previewCornerRadius"
                )

                val effectivePreviewWidth = if (style.isLandscape) {
                    (style.widthDp * (180f / 110f)).dp
                } else {
                    style.widthDp.dp
                }

                Box(
                    modifier = Modifier
                        .width(effectivePreviewWidth)
                        .aspectRatio(if (style.isLandscape) 16f / 9f else 0.68f)
                        .shadow(12.dp, RoundedCornerShape(previewCornerRadius))
                        .clip(RoundedCornerShape(previewCornerRadius))
                        .glassCardDepth(
                            shape = RoundedCornerShape(previewCornerRadius),
                            edgeStrength = draftEdge,
                            sheenStrength = draftSheen,
                            edgeCoverage = draftCoverage,
                            enabled = style.cardDepthEnabled
                        )
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1E293B),
                                    Color(0xFF0F172A),
                                    Color(0xFF020617)
                                )
                            )
                        )
                ) {
                    // Ambient gradient aura
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.5f to Color.Transparent,
                                    1f to AmoledBlack.copy(alpha = 0.85f)
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "OmniTrack",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!style.hideLabels) {
                            Text(
                                text = "Sample Entry",
                                color = TextMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // Section 1: Card Width / Grid Density
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Card Width",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${style.widthDp} dp",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentArctic
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    widthPresets.forEach { preset ->
                        val isSelected = style.widthDp == preset.value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AccentArctic else DarkSurfaceElevated)
                                .border(1.dp, if (isSelected) AccentArctic else GlassBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    scope.launch { preferenceManager.updatePosterWidthDp(preset.value) }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = preset.label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) AmoledBlack else TextSecondary
                            )
                        }
                    }
                }

                Slider(
                    value = style.widthDp.toFloat(),
                    onValueChange = { scope.launch { preferenceManager.updatePosterWidthDp(it.toInt()) } },
                    valueRange = 90f..240f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentArctic,
                        activeTrackColor = AccentArctic,
                        inactiveTrackColor = DarkSurfaceElevated
                    )
                )
            }

            // Section 2: Corner Curvature
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Corner Radius",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${style.cornerRadiusDp} dp",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    radiusPresets.forEach { preset ->
                        val isSelected = style.cornerRadiusDp == preset.value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AccentGold else DarkSurfaceElevated)
                                .border(1.dp, if (isSelected) AccentGold else GlassBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    scope.launch { preferenceManager.updatePosterCornerRadiusDp(preset.value) }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = preset.label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) AmoledBlack else TextSecondary
                            )
                        }
                    }
                }

                Slider(
                    value = style.cornerRadiusDp.toFloat(),
                    onValueChange = { scope.launch { preferenceManager.updatePosterCornerRadiusDp(it.toInt()) } },
                    valueRange = 0f..24f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentGold,
                        activeTrackColor = AccentGold,
                        inactiveTrackColor = DarkSurfaceElevated
                    )
                )
            }

            // Section 3: Layout Options Toggles
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Landscape mode toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "16:9 Landscape Mode",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Switch grid cards from vertical posters to widescreen banners",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = style.isLandscape,
                        onCheckedChange = { scope.launch { preferenceManager.updatePosterLandscape(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AmoledBlack,
                            checkedTrackColor = AccentJade
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // Hide labels toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hide Card Labels",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Hide titles & metadata below posters for a pure clean gallery",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = style.hideLabels,
                        onCheckedChange = { scope.launch { preferenceManager.updatePosterHideLabels(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AmoledBlack,
                            checkedTrackColor = AccentJade
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // Always Backdrop with Logo in Landscape
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Always Backdrop with Title Logo",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Overlay transparent clearart title logos on landscape 16:9 cards",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = style.alwaysShowLandscapeClearlogo,
                        onCheckedChange = { scope.launch { preferenceManager.updateAlwaysShowLandscapeClearlogo(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AmoledBlack,
                            checkedTrackColor = AccentJade
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // Card Depth Effect
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Card Depth & Specular Top Sheen",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "1dp specular edge light gradient and curved glass top-sheen reflection",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = style.cardDepthEnabled,
                        onCheckedChange = { scope.launch { preferenceManager.updateCardDepthEnabled(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AmoledBlack,
                            checkedTrackColor = AccentJade
                        )
                    )
                }

                if (style.cardDepthEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Depth & Sheen 2D Canvas Pad",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentArctic
                            )
                            Text(
                                text = "Edge: ${draftEdge.roundToInt()}% • Sheen: ${draftSheen.roundToInt()}%",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        CardDepthTuningPad(
                            edgeStrength = draftEdge,
                            sheenStrength = draftSheen,
                            onChange = { edge, sheen ->
                                draftEdge = edge
                                draftSheen = sheen
                            },
                            onCommit = { edge, sheen ->
                                scope.launch {
                                    preferenceManager.updateCardDepthSettings(
                                        edge.roundToInt(),
                                        sheen.roundToInt()
                                    )
                                }
                            }
                        )

                        // Quick Presets
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "Default" to (50 to 35),
                                "Subtle" to (28 to 10),
                                "Balanced" to (42 to 15),
                                "Bold" to (56 to 22),
                                "Glassy" to (70 to 25)
                            ).forEach { (label, values) ->
                                val isSelected = draftEdge.roundToInt() == values.first && draftSheen.roundToInt() == values.second
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AccentJade.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                        .border(
                                            1.dp,
                                            if (isSelected) AccentJade else GlassBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            draftEdge = values.first.toFloat()
                                            draftSheen = values.second.toFloat()
                                            scope.launch {
                                                preferenceManager.updateCardDepthSettings(values.first, values.second)
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) AccentJade else TextSecondary
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.08f))
                        )

                        // Section: Edge Light Coverage
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Edge Light Coverage",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentArctic
                            )
                            Text(
                                text = "${draftCoverage.roundToInt()}%",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "Top (0%)" to 0,
                                "Half (50%)" to 50,
                                "Full (100%)" to 100
                            ).forEach { (label, value) ->
                                val isSelected = draftCoverage.roundToInt() == value
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AccentArctic.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                        .border(
                                            1.dp,
                                            if (isSelected) AccentArctic else GlassBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            draftCoverage = value.toFloat()
                                            scope.launch { preferenceManager.updateCardDepthCoverage(value) }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) AccentArctic else TextSecondary
                                    )
                                }
                            }
                        }

                        Slider(
                            value = draftCoverage,
                            onValueChange = { draftCoverage = it },
                            onValueChangeFinished = {
                                scope.launch { preferenceManager.updateCardDepthCoverage(draftCoverage.roundToInt()) }
                            },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentArctic,
                                activeTrackColor = AccentArctic,
                                inactiveTrackColor = DarkSurfaceElevated
                            )
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.08f))
                        )

                        // Section: Apply Card Depth To (Surfaces)
                        Text(
                            text = "Apply Card Depth To",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )

                        listOf(
                            "Posters & Grid Cards" to ("posters" to style.cardDepthPostersEnabled),
                            "Continue Watching Rails" to ("continue_watching" to style.cardDepthContinueWatchingEnabled),
                            "Episode Cards & Rows" to ("episodes" to style.cardDepthEpisodesEnabled),
                            "Cast & Crew Avatars" to ("cast" to style.cardDepthCastEnabled),
                            "Trailers & Videos" to ("trailers" to style.cardDepthTrailersEnabled)
                        ).forEach { (title, pair) ->
                            val (surfaceKey, isEnabled) = pair
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            preferenceManager.updateCardDepthSurface(surfaceKey, enabled)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AmoledBlack,
                                        checkedTrackColor = AccentJade
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Interactive 2D Graph Tuning Pad:
 * Lets the user slide across X (Edge Light Strength 0..100) and Y (Specular Sheen Strength 0..100)
 * with live crosshairs, glow thumb, and real-time haptic/visual feedback.
 */
@Composable
fun CardDepthTuningPad(
    edgeStrength: Float,
    sheenStrength: Float,
    onChange: (edge: Float, sheen: Float) -> Unit,
    onCommit: (edge: Float, sheen: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentEdge by rememberUpdatedState(edgeStrength)
    val currentSheen by rememberUpdatedState(sheenStrength)
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnCommit by rememberUpdatedState(onCommit)

    var padWidth by remember { mutableFloatStateOf(1f) }
    var padHeight by remember { mutableFloatStateOf(1f) }

    fun valuesAt(x: Float, y: Float): Pair<Float, Float> {
        val edge = (x / padWidth * 100f).coerceIn(0f, 100f)
        val sheen = ((padHeight - y) / padHeight * 100f).coerceIn(0f, 100f)
        return edge to sheen
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF090D14))
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .onSizeChanged {
                padWidth = it.width.toFloat().coerceAtLeast(1f)
                padHeight = it.height.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val (edge, sheen) = valuesAt(offset.x, offset.y)
                    currentOnChange(edge, sheen)
                    currentOnCommit(edge, sheen)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val (edge, sheen) = valuesAt(offset.x, offset.y)
                        currentOnChange(edge, sheen)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val (edge, sheen) = valuesAt(change.position.x, change.position.y)
                        currentOnChange(edge, sheen)
                    },
                    onDragEnd = {
                        currentOnCommit(currentEdge, currentSheen)
                    },
                    onDragCancel = {
                        currentOnCommit(currentEdge, currentSheen)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Grid lines (25%, 50%, 75%)
            val gridColor = Color.White.copy(alpha = 0.05f)
            listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                drawLine(
                    color = gridColor,
                    start = Offset(w * fraction, 0f),
                    end = Offset(w * fraction, h),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, h * fraction),
                    end = Offset(w, h * fraction),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Crosshair guidelines to thumb
            val thumbX = (edgeStrength / 100f) * w
            val thumbY = ((100f - sheenStrength) / 100f) * h

            val guideColor = AccentJade.copy(alpha = 0.35f)
            drawLine(
                color = guideColor,
                start = Offset(thumbX, 0f),
                end = Offset(thumbX, h),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = guideColor,
                start = Offset(0f, thumbY),
                end = Offset(w, thumbY),
                strokeWidth = 1.5.dp.toPx()
            )

            // Thumb Outer Glow
            drawCircle(
                color = AccentJade.copy(alpha = 0.25f),
                radius = 16.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            // Thumb Accent Ring
            drawCircle(
                color = AccentJade,
                radius = 9.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            // Thumb Center Core
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
        }

        // Top Left: Sheen Y-axis indicator
        Text(
            text = "Sheen: ${sheenStrength.roundToInt()}% ↑",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 8.dp)
        )

        // Bottom Right: Edge Light X-axis indicator
        Text(
            text = "Edge Light: ${edgeStrength.roundToInt()}% →",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 8.dp)
        )
    }
}
