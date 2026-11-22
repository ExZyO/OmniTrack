package com.omnitrack.app.ui.screens.detail.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * Details-Synced Frameless Status Selection Bottom Sheet.
 * Inherits Settings > Visual Presentation > Background Mode with hairline rows and auto-sync to unlocked trackers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChimanewStatusBottomSheet(
    currentStatus: TrackingStatus,
    onStatusSelected: (TrackingStatus) -> Unit,
    onDismiss: () -> Unit,
    mediaType: MediaType = MediaType.ANIME,
    presentationSettings: PresentationSettings? = null,
    backdropBitmap: ImageBitmap? = null,
    backdropTargetUrl: String? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    val activeVerb = when (mediaType) {
        MediaType.MANGA, MediaType.COMIC, MediaType.NOVEL, MediaType.BOOK -> "Reading"
        MediaType.GAME, MediaType.VISUAL_NOVEL -> "Playing"
        else -> "Watching"
    }
    val planVerb = when (mediaType) {
        MediaType.MANGA, MediaType.COMIC, MediaType.NOVEL, MediaType.BOOK -> "Plan to Read"
        MediaType.GAME, MediaType.VISUAL_NOVEL -> "Plan to Play"
        else -> "Plan to Watch"
    }

    val statuses = listOf(
        TrackingStatus.IN_PROGRESS to activeVerb,
        TrackingStatus.COMPLETED to "Completed",
        TrackingStatus.PLAN_TO_TRACK to planVerb,
        TrackingStatus.ON_HOLD to "On Hold",
        TrackingStatus.DROPPED to "Dropped"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        BackHandler { onDismiss() }
        DetailSheetBackdrop(
            presentationSettings = presentationSettings,
            backdropBitmap = backdropBitmap,
            backdropTargetUrl = backdropTargetUrl,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f))
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Update Status",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Auto-syncs to unlocked trackers",
                            fontSize = 11.5.sp,
                            color = AccentGold.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    statuses.forEachIndexed { idx, (status, label) ->
                        val isSelected = status == currentStatus
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) AccentGold.copy(alpha = 0.14f) else Color.Transparent
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStatusSelected(status)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) AccentGold else Color.White.copy(alpha = 0.28f))
                                )
                                Text(
                                    text = label,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) AccentGold else TextPrimary
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = AccentGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (idx < statuses.lastIndex) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Details-Synced Frameless Progress Stepper Bottom Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChimanewProgressBottomSheet(
    currentProgress: Double,
    totalProgress: Double?,
    onProgressConfirmed: (Double) -> Unit,
    onDismiss: () -> Unit,
    unitLabel: String = "Episode / Chapter",
    presentationSettings: PresentationSettings? = null,
    backdropBitmap: ImageBitmap? = null,
    backdropTargetUrl: String? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputStr by remember { mutableStateOf(currentProgress.toInt().toString()) }
    val maxProgress = totalProgress ?: 0.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        BackHandler { onDismiss() }
        DetailSheetBackdrop(
            presentationSettings = presentationSettings,
            backdropBitmap = backdropBitmap,
            backdropTargetUrl = backdropTargetUrl,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("$unitLabel Progress", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Total: ${if (maxProgress > 0) maxProgress.toInt() else "Unknown"}", fontSize = 12.sp, color = TextSecondary)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                // Numeric input display (translucent glass without ugly black border)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = inputStr,
                        onValueChange = { inputStr = it.filter { ch -> ch.isDigit() } },
                        textStyle = TextStyle(
                            color = AccentGold,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black
                        ),
                        cursorBrush = SolidColor(AccentGold),
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
                        color = TextSecondary
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
                                .background(if (delta > 0) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f))
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
                                color = if (delta > 0) AccentGold else TextPrimary
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
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = AmoledBlack),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Confirm Progress", color = AmoledBlack, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
