package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

/**
 * Chimanew-Style Instant Status Selection Bottom Sheet.
 * Completely replaces the buggy radial gesture overlay with instant, 100% reliable tap selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChimanewStatusBottomSheet(
    currentStatus: TrackingStatus,
    onStatusSelected: (TrackingStatus) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    val statuses = listOf(
        Triple(TrackingStatus.IN_PROGRESS, "Watching / Reading", AccentArctic),
        Triple(TrackingStatus.COMPLETED, "Completed", AccentJade),
        Triple(TrackingStatus.PLAN_TO_TRACK, "Plan to Watch / Read", AccentGold),
        Triple(TrackingStatus.ON_HOLD, "On Hold", Color(0xFFF59E0B)),
        Triple(TrackingStatus.DROPPED, "Dropped", Color(0xFFEF4444))
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
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Update Status",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            // Status List (Full-Width Stadium Pills)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                statuses.forEach { (status, label, accent) ->
                    val isSelected = status == currentStatus
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSelected) accent.copy(alpha = 0.22f) else DarkSurfaceElevated)
                            .border(1.dp, if (isSelected) accent else GlassBorder, RoundedCornerShape(999.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStatusSelected(status)
                                onDismiss()
                            }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSelected) accent else TextPrimary
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * Chimanew-Style Instant Progress Numeric Stepper Bottom Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChimanewProgressBottomSheet(
    currentProgress: Double,
    totalProgress: Double?,
    onProgressConfirmed: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputStr by remember { mutableStateOf(currentProgress.toInt().toString()) }
    val maxProgress = totalProgress ?: 0.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Episode / Chapter Progress", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Total: ${if (maxProgress > 0) maxProgress.toInt() else "Unknown"}", fontSize = 11.5.sp, color = TextMuted)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            // Numeric input display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = inputStr,
                    onValueChange = { inputStr = it.filter { ch -> ch.isDigit() } },
                    textStyle = TextStyle(
                        color = AccentArctic,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    ),
                    cursorBrush = SolidColor(AccentArctic),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val parsed = inputStr.toDoubleOrNull() ?: currentProgress
                        val bounded = if (maxProgress > 0) parsed.coerceIn(0.0, maxProgress) else parsed.coerceAtLeast(0.0)
                        onProgressConfirmed(bounded)
                        onDismiss()
                    }),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "/ ${if (maxProgress > 0) maxProgress.toInt() else "∞"}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
            }

            // Quick stepper pills: -5, -1, +1, +5
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(-5, -1, 1, 5).forEach { delta ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(DarkSurfaceElevated)
                            .clickable {
                                val current = inputStr.toIntOrNull() ?: currentProgress.toInt()
                                val next = (current + delta).coerceAtLeast(0)
                                val bounded = if (maxProgress > 0) next.coerceAtMost(maxProgress.toInt()) else next
                                inputStr = bounded.toString()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (delta > 0) "+$delta" else "$delta",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (delta > 0) AccentArctic else TextPrimary
                        )
                    }
                }
            }

            // Confirm Button
            Button(
                onClick = {
                    val parsed = inputStr.toDoubleOrNull() ?: currentProgress
                    val bounded = if (maxProgress > 0) parsed.coerceIn(0.0, maxProgress) else parsed.coerceAtLeast(0.0)
                    onProgressConfirmed(bounded)
                    onDismiss()
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentArctic),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirm Progress", color = AmoledBlack, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
