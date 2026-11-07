package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.model.TrackingStatus
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
import kotlin.math.sin
import kotlin.math.sqrt

fun TrackingStatus.statusColor(): Color = when (this) {
    TrackingStatus.IN_PROGRESS -> AccentArctic
    TrackingStatus.COMPLETED -> AccentJade
    TrackingStatus.PLAN_TO_TRACK -> AccentGold
    TrackingStatus.ON_HOLD -> Color(0xFFF59E0B)
    TrackingStatus.DROPPED -> Color(0xFFEF4444)
    else -> AccentGold
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusPickerSheet(
    currentStatus: TrackingStatus,
    onDismiss: () -> Unit,
    onStatusSelected: (TrackingStatus) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val statuses = listOf(
        TrackingStatus.IN_PROGRESS,
        TrackingStatus.COMPLETED,
        TrackingStatus.ON_HOLD,
        TrackingStatus.DROPPED,
        TrackingStatus.PLAN_TO_TRACK
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
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Update Status",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            statuses.forEach { status ->
                val isSelected = status == currentStatus
                val color = status.statusColor()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) color.copy(alpha = 0.16f) else DarkSurfaceElevated)
                        .border(1.dp, if (isSelected) color else GlassBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            onStatusSelected(status)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Text(
                            text = status.displayName,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) color else TextPrimary
                        )
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = color,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Attached Contiguous Fan Arc Overlay with 0px gap between sectors.
 * Triggered on 200ms Press-and-Hold on Status pill.
 * Radiates above thumb with contiguous pie sectors. Sweeping finger highlights sector with haptics.
 * Releasing finger commits hovered status.
 */
@Composable
fun AttachedStatusFanArcOverlay(
    currentStatus: TrackingStatus,
    thumbAnchor: Offset,
    onDismiss: () -> Unit,
    onCommit: (TrackingStatus) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current.density
    val statuses = remember {
        listOf(
            TrackingStatus.PLAN_TO_TRACK,
            TrackingStatus.DROPPED,
            TrackingStatus.ON_HOLD,
            TrackingStatus.IN_PROGRESS,
            TrackingStatus.COMPLETED
        )
    }

    var selectedIndex by remember {
        val initialIdx = statuses.indexOf(currentStatus).takeIf { it >= 0 } ?: 3
        mutableIntStateOf(initialIdx)
    }
    var touchPos by remember { mutableStateOf(thumbAnchor) }

    val innerRadiusPx = 50f * density
    val outerRadiusPx = 160f * density
    // Fan spreads across upper half: -160 deg to -20 deg (140 deg total arc)
    val startAngleDeg = -160f
    val endAngleDeg = -20f
    val sectorAngleDeg = (endAngleDeg - startAngleDeg) / statuses.size // 28 deg per sector

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack.copy(alpha = 0.85f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        touchPos = offset
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        touchPos = change.position
                        val dx = change.position.x - thumbAnchor.x
                        val dy = change.position.y - thumbAnchor.y
                        val dist = sqrt(dx * dx + dy * dy)

                        if (dist > 30f * density) {
                            var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            if (angleDeg > 0) angleDeg -= 360f

                            val clamped = angleDeg.coerceIn(startAngleDeg, endAngleDeg)
                            val idx = ((clamped - startAngleDeg) / sectorAngleDeg).toInt().coerceIn(0, statuses.size - 1)
                            if (idx != selectedIndex) {
                                selectedIndex = idx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                    onDragEnd = {
                        onCommit(statuses[selectedIndex])
                        onDismiss()
                    },
                    onDragCancel = {
                        onCommit(statuses[selectedIndex])
                        onDismiss()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = thumbAnchor

            // Draw Contiguous 0px-gap Sectors
            statuses.forEachIndexed { i, status ->
                val sAngle = startAngleDeg + (i * sectorAngleDeg)
                val eAngle = sAngle + sectorAngleDeg
                val isHighlighted = i == selectedIndex
                val color = status.statusColor()

                val path = Path().apply {
                    val rIn = innerRadiusPx
                    val rOut = if (isHighlighted) outerRadiusPx + 16.dp.toPx() else outerRadiusPx
                    val sRad = Math.toRadians(sAngle.toDouble())
                    val eRad = Math.toRadians(eAngle.toDouble())

                    moveTo(
                        (center.x + rIn * cos(sRad)).toFloat(),
                        (center.y + rIn * sin(sRad)).toFloat()
                    )
                    lineTo(
                        (center.x + rOut * cos(sRad)).toFloat(),
                        (center.y + rOut * sin(sRad)).toFloat()
                    )
                    arcTo(
                        rect = Rect(center.x - rOut, center.y - rOut, center.x + rOut, center.y + rOut),
                        startAngleDegrees = sAngle,
                        sweepAngleDegrees = sectorAngleDeg,
                        forceMoveTo = false
                    )
                    lineTo(
                        (center.x + rIn * cos(eRad)).toFloat(),
                        (center.y + rIn * sin(eRad)).toFloat()
                    )
                    arcTo(
                        rect = Rect(center.x - rIn, center.y - rIn, center.x + rIn, center.y + rIn),
                        startAngleDegrees = eAngle,
                        sweepAngleDegrees = -sectorAngleDeg,
                        forceMoveTo = false
                    )
                    close()
                }

                // Sector fill
                drawPath(
                    path = path,
                    color = if (isHighlighted) color.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f),
                    style = Fill
                )

                // 0px seam separator line
                drawPath(
                    path = path,
                    color = if (isHighlighted) color else Color.White.copy(alpha = 0.25f),
                    style = Stroke(width = if (isHighlighted) 2.5.dp.toPx() else 1.dp.toPx())
                )
            }

            // Finger touch position
            drawCircle(
                color = statuses[selectedIndex].statusColor(),
                radius = 12.dp.toPx(),
                center = touchPos
            )
        }

        // Active Hover HUD
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(18.dp))
                .background(DarkSurfaceElevated.copy(alpha = 0.95f))
                .border(1.dp, statuses[selectedIndex].statusColor(), RoundedCornerShape(18.dp))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statuses[selectedIndex].statusColor())
            )
            Text(
                text = statuses[selectedIndex].displayName.uppercase(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = statuses[selectedIndex].statusColor()
            )
            Text(
                text = "Release finger to set status",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}
