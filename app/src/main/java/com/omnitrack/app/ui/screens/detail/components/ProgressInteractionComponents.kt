package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgressEditSheet(
    initialProgress: Double,
    totalProgress: Double?,
    mediaType: MediaType,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDecimal = mediaType == MediaType.GAME || mediaType == MediaType.VISUAL_NOVEL
    val unitLabel = mediaType.unitLabel

    var progressValue by remember { mutableDoubleStateOf(initialProgress) }
    var textInput by remember {
        mutableStateOf(if (isDecimal) String.format("%.1f", initialProgress) else initialProgress.toInt().toString())
    }

    val maxSlider = (totalProgress ?: (if (initialProgress > 50) initialProgress * 1.5 else 100.0)).toFloat().coerceAtLeast(10f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Edit Progress",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${mediaType.displayName} • $unitLabel tracked",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            // Central Value Display & Manual Keypad Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val step = if (isDecimal) 0.5 else 1.0
                        progressValue = (progressValue - step).coerceAtLeast(0.0)
                        textInput = if (isDecimal) String.format("%.1f", progressValue) else progressValue.toInt().toString()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Minus", tint = TextPrimary)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = textInput,
                        onValueChange = { input ->
                            textInput = input
                            val parsed = input.toDoubleOrNull()
                            if (parsed != null) {
                                val clamped = if (totalProgress != null) parsed.coerceIn(0.0, totalProgress) else parsed.coerceAtLeast(0.0)
                                progressValue = clamped
                            }
                        },
                        textStyle = TextStyle(
                            color = AccentArctic,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number),
                        cursorBrush = SolidColor(AccentArctic),
                        singleLine = true,
                        modifier = Modifier.width(120.dp)
                    )

                    if (totalProgress != null) {
                        Text(
                            text = " / ${if (isDecimal) String.format("%.1f", totalProgress) else totalProgress.toInt().toString()}",
                            color = TextMuted,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val step = if (isDecimal) 0.5 else 1.0
                        val next = progressValue + step
                        progressValue = if (totalProgress != null) next.coerceAtMost(totalProgress) else next
                        textInput = if (isDecimal) String.format("%.1f", progressValue) else progressValue.toInt().toString()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AccentArctic.copy(alpha = 0.2f))
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Plus", tint = AccentArctic)
                }
            }

            // Continuous Slider
            Slider(
                value = progressValue.toFloat().coerceIn(0f, maxSlider),
                onValueChange = { newVal ->
                    progressValue = if (isDecimal) (newVal * 2).roundToInt() / 2.0 else newVal.roundToInt().toDouble()
                    textInput = if (isDecimal) String.format("%.1f", progressValue) else progressValue.toInt().toString()
                },
                valueRange = 0f..maxSlider,
                colors = SliderDefaults.colors(
                    thumbColor = AccentArctic,
                    activeTrackColor = AccentArctic,
                    inactiveTrackColor = DarkSurfaceElevated
                )
            )

            // Quick Stepper Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val steppers = if (isDecimal) {
                    listOf(-5.0, -1.0, 0.5, 1.0, 5.0, 10.0)
                } else {
                    listOf(-10.0, -5.0, -1.0, 1.0, 5.0, 10.0)
                }

                steppers.forEach { step ->
                    val isPositive = step > 0
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPositive) AccentArctic.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f))
                            .clickable {
                                val next = (progressValue + step).coerceAtLeast(0.0)
                                progressValue = if (totalProgress != null) next.coerceAtMost(totalProgress) else next
                                textInput = if (isDecimal) String.format("%.1f", progressValue) else progressValue.toInt().toString()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isPositive) "+${if (isDecimal && step % 1.0 != 0.0) step else step.toInt()}" else "${step.toInt()}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) AccentArctic else TextMuted
                        )
                    }
                }

                if (totalProgress != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentJade.copy(alpha = 0.18f))
                            .clickable {
                                progressValue = totalProgress
                                textInput = if (isDecimal) String.format("%.1f", totalProgress) else totalProgress.toInt().toString()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "MAX",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Black,
                            color = AccentJade
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Cancel", color = TextMuted, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        onConfirm(progressValue)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentArctic),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Save Progress", color = AmoledBlack, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Dual-Speed Precision Jog-Wheel Radial Gesture Overlay.
 * Triggered on 200ms Press-and-Hold on the Progress pill.
 * - Inner zone (< 90px radius): Coarse scrubbing (+-10 or +-50 chapters per turn).
 * - Outer zone (>= 90px radius): Fine scrubbing (+-1 tick per angular sector with haptics).
 */
@Composable
fun DualSpeedJogWheelOverlay(
    initialProgress: Double,
    totalProgress: Double?,
    mediaType: MediaType,
    centerAnchor: Offset,
    onDismiss: () -> Unit,
    onCommit: (Double) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current.density
    val isDecimal = mediaType == MediaType.GAME || mediaType == MediaType.VISUAL_NOVEL

    var currentProgress by remember { mutableDoubleStateOf(initialProgress) }
    var touchPos by remember { mutableStateOf(centerAnchor) }
    var lastAngle by remember { mutableFloatStateOf(0f) }
    var accumulatedFineAngle by remember { mutableFloatStateOf(0f) }
    var isCoarseZone by remember { mutableStateOf(false) }

    val innerZoneRadiusPx = 90f * density
    val outerWheelRadiusPx = 175f * density

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack.copy(alpha = 0.82f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        touchPos = offset
                        val dx = offset.x - centerAnchor.x
                        val dy = offset.y - centerAnchor.y
                        lastAngle = atan2(dy, dx)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        touchPos = change.position
                        val dx = change.position.x - centerAnchor.x
                        val dy = change.position.y - centerAnchor.y
                        val dist = sqrt(dx * dx + dy * dy)
                        val angle = atan2(dy, dx)

                        var angleDiff = angle - lastAngle
                        if (angleDiff > Math.PI) angleDiff -= (2 * Math.PI).toFloat()
                        if (angleDiff < -Math.PI) angleDiff += (2 * Math.PI).toFloat()
                        lastAngle = angle

                        isCoarseZone = dist < innerZoneRadiusPx

                        if (isCoarseZone) {
                            // Coarse scrub: 1 full turn (2*PI) = 50 units (or 25 hrs)
                            val unitsPerRadian = if (isDecimal) 4.0 else 8.0
                            val delta = angleDiff * unitsPerRadian
                            val next = (currentProgress + delta).coerceAtLeast(0.0)
                            currentProgress = if (totalProgress != null) next.coerceAtMost(totalProgress) else next
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else {
                            // Fine scrub: 1 tick per 15 degrees (~0.26 radians)
                            accumulatedFineAngle += angleDiff
                            val tickThreshold = 0.24f
                            if (accumulatedFineAngle > tickThreshold) {
                                val step = if (isDecimal) 0.5 else 1.0
                                val next = currentProgress + step
                                currentProgress = if (totalProgress != null) next.coerceAtMost(totalProgress) else next
                                accumulatedFineAngle = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (accumulatedFineAngle < -tickThreshold) {
                                val step = if (isDecimal) 0.5 else 1.0
                                currentProgress = (currentProgress - step).coerceAtLeast(0.0)
                                accumulatedFineAngle = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                    onDragEnd = {
                        onCommit(currentProgress)
                        onDismiss()
                    },
                    onDragCancel = {
                        onCommit(currentProgress)
                        onDismiss()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = centerAnchor

            // Outer fine jog ring
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = outerWheelRadiusPx,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Inner coarse zone ring
            drawCircle(
                color = if (isCoarseZone) AccentArctic.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f),
                radius = innerZoneRadiusPx,
                center = center,
                style = Stroke(width = if (isCoarseZone) 3.dp.toPx() else 1.5.dp.toPx())
            )

            // Radial tick marks around the fine perimeter
            val numTicks = 24
            for (i in 0 until numTicks) {
                val a = (i * (2 * Math.PI) / numTicks).toFloat()
                val r1 = outerWheelRadiusPx - 10.dp.toPx()
                val r2 = outerWheelRadiusPx
                val p1 = Offset(center.x + r1 * cos(a), center.y + r1 * sin(a))
                val p2 = Offset(center.x + r2 * cos(a), center.y + r2 * sin(a))
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = p1,
                    end = p2,
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Finger connection ray
            drawLine(
                color = if (isCoarseZone) AccentArctic else AccentGold,
                start = center,
                end = touchPos,
                strokeWidth = 2.dp.toPx()
            )

            // Finger touch orb
            drawCircle(
                color = if (isCoarseZone) AccentArctic else AccentGold,
                radius = 14.dp.toPx(),
                center = touchPos
            )
        }

        // Center HUD Readout
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurfaceElevated.copy(alpha = 0.95f))
                .border(1.dp, if (isCoarseZone) AccentArctic else AccentGold, RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isCoarseZone) "⚡ COARSE SCRUB" else "🎯 FINE TICK SCRUB",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = if (isCoarseZone) AccentArctic else AccentGold,
                letterSpacing = 1.sp
            )

            val displayVal = if (isDecimal) String.format("%.1f", currentProgress) else currentProgress.toInt().toString()
            Text(
                text = "$displayVal ${mediaType.unitLabel}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )

            val delta = currentProgress - initialProgress
            val deltaStr = if (delta >= 0) "+${if (isDecimal) String.format("%.1f", delta) else delta.toInt().toString()}"
                           else if (isDecimal) String.format("%.1f", delta) else delta.toInt().toString()
            Text(
                text = "($deltaStr from ${if (isDecimal) String.format("%.1f", initialProgress) else initialProgress.toInt()})",
                fontSize = 12.sp,
                color = if (delta >= 0) AccentJade else Color(0xFFEF4444),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Release finger to commit",
                fontSize = 10.5.sp,
                color = TextMuted
            )
        }
    }
}
