package com.omnitrack.app.ui.screens.detail.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreRatingSheet(
    initialScore: Float?,
    onDismiss: () -> Unit,
    onScoreConfirmed: (Float?) -> Unit,
    presentationSettings: PresentationSettings? = null,
    backdropBitmap: ImageBitmap? = null,
    backdropTargetUrl: String? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 0.9f = Unrated (< 0.95f), 1.0f..10.0f = 0.1 steps (uniform 90 discrete steps -> zero dead zone)
    var sliderValue by remember(initialScore) {
        mutableFloatStateOf(initialScore?.coerceIn(1.0f, 10.0f) ?: 0.9f)
    }
    var textInput by remember(initialScore) {
        mutableStateOf(if (initialScore != null && initialScore >= 1.0f) String.format(Locale.US, "%.1f", initialScore) else "")
    }

    val isUnrated = sliderValue < 0.95f

    fun updateScore(newScore: Float) {
        if (newScore < 0.95f) {
            sliderValue = 0.9f
            textInput = ""
        } else {
            val clamped = ((newScore.coerceIn(1.0f, 10.0f) * 10f).roundToInt()) / 10f
            sliderValue = clamped
            textInput = String.format(Locale.US, "%.1f", clamped)
        }
    }

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
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f))
                )
            }

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Score & Rating",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Slide Unrated or 1.0 to 10.0 with 0.1 precision",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }

                if (!isUnrated || initialScore != null) {
                    Text(
                        text = "Remove Rating",
                        color = Color(0xFFEF4444),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                updateScore(0.9f)
                                onScoreConfirmed(null)
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                } else {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }
            }

            // 1. Unboxed Hero Score (No outlines, no box: open space typography flanked by circular steppers)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RepeatingHoldButton(
                    onStep = {
                        if (!isUnrated) {
                            if (sliderValue <= 1.05f) {
                                updateScore(0.9f)
                            } else {
                                updateScore(sliderValue - 0.1f)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Minus 0.1", tint = TextPrimary)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (isUnrated) Color.White.copy(alpha = 0.35f) else AccentGold,
                        modifier = Modifier.size(28.dp)
                    )

                    if (isUnrated) {
                        Text(
                            text = "Unrated",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { updateScore(7.0f) }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    } else {
                        BasicTextField(
                            value = textInput,
                            onValueChange = { input ->
                                textInput = input
                                val parsed = input.toFloatOrNull()
                                if (parsed != null) {
                                    if (parsed < 0.95f) {
                                        sliderValue = 0.9f
                                    } else {
                                        sliderValue = ((parsed.coerceIn(1.0f, 10.0f) * 10f).roundToInt()) / 10f
                                    }
                                }
                            },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            cursorBrush = SolidColor(AccentGold),
                            singleLine = true,
                            modifier = Modifier.width(110.dp)
                        )

                        Text(
                            text = "/ 10",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                RepeatingHoldButton(
                    onStep = {
                        if (isUnrated) {
                            updateScore(1.0f)
                        } else {
                            updateScore(sliderValue + 0.1f)
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(AccentGold.copy(alpha = 0.20f))
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Plus 0.1", tint = AccentGold)
                }
            }

            // 2. Smooth Continuous Slider (0.9 = Unrated, 1.0 to 10.0 in uniform 0.1 steps)
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderValue,
                    onValueChange = { updateScore(it) },
                    valueRange = 0.9f..10.0f,
                    steps = 90,
                    colors = SliderDefaults.colors(
                        thumbColor = if (isUnrated) Color.White.copy(alpha = 0.65f) else Color.White,
                        activeTrackColor = if (isUnrated) Color.White.copy(alpha = 0.18f) else AccentGold,
                        inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Unrated",
                        fontSize = 11.sp,
                        fontWeight = if (isUnrated) FontWeight.Bold else FontWeight.Medium,
                        color = if (isUnrated) AccentGold else Color.White.copy(alpha = 0.80f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { updateScore(0.9f) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "1.0",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.80f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { updateScore(1.0f) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "5.0",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.80f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { updateScore(5.0f) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "10.0",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.80f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { updateScore(10.0f) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // 3. Clean 6 Borderless Delta Stepper Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val deltas = listOf(-1.0f, -0.5f, -0.1f, 0.1f, 0.5f, 1.0f)
                deltas.forEach { delta ->
                    val isPos = delta > 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isPos) AccentGold.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.08f))
                            .clickable {
                                if (isUnrated) {
                                    if (isPos) updateScore(1.0f + delta)
                                } else {
                                    val next = sliderValue + delta
                                    if (next < 0.95f) updateScore(0.9f) else updateScore(next)
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isPos) "+${String.format(Locale.US, "%.1f", delta)}" else String.format(Locale.US, "%.1f", delta),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPos) AccentGold else Color.White.copy(alpha = 0.90f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 4. Action Buttons (Clean & Borderless)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Cancel", color = Color.White.copy(alpha = 0.70f), fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        val finalScore = if (isUnrated) {
                            null
                        } else {
                            val parsed = textInput.toFloatOrNull() ?: sliderValue
                            if (parsed < 0.95f) null
                            else ((parsed.coerceIn(1.0f, 10.0f) * 10f).roundToInt()) / 10f
                        }
                        onScoreConfirmed(finalScore)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Save Score", color = AmoledBlack, fontWeight = FontWeight.Bold)
                }
            }
        }
        }
    }
}

@Composable
fun RepeatingHoldButton(
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    initialDelayMs: Long = 320L,
    repeatIntervalMs: Long = 90L,
    content: @Composable () -> Unit
) {
    val currentOnStep by rememberUpdatedState(onStep)
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnStep()
                    val job = coroutineScope.launch {
                        delay(initialDelayMs)
                        while (isActive) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnStep()
                            delay(repeatIntervalMs)
                        }
                    }
                    try {
                        tryAwaitRelease()
                    } finally {
                        job.cancel()
                    }
                }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
