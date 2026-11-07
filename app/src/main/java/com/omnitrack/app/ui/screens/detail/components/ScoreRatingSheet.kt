package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScoreRatingSheet(
    initialScore: Float?,
    onDismiss: () -> Unit,
    onScoreConfirmed: (Float?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var scoreValue by remember { mutableFloatStateOf(initialScore ?: 7.5f) }
    var textInput by remember { mutableStateOf(String.format("%.1f", scoreValue)) }

    fun updateScore(newScore: Float) {
        val clamped = ((newScore.coerceIn(1.0f, 10.0f) * 10f).roundToInt()) / 10f
        scoreValue = clamped
        textInput = String.format("%.1f", clamped)
    }

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
                        text = "Score & Rating",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Scale strictly 1.0 to 10.0 with 0.1 precision",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            // Central Value Display & Manual Keypad Box with Smooth Repeating Steppers
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
                RepeatingHoldButton(
                    onStep = { updateScore(scoreValue - 0.1f) },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Minus 0.1", tint = TextPrimary)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(28.dp)
                    )

                    BasicTextField(
                        value = textInput,
                        onValueChange = { input ->
                            textInput = input
                            val parsed = input.toFloatOrNull()
                            if (parsed != null) {
                                scoreValue = parsed.coerceIn(1.0f, 10.0f)
                            }
                        },
                        textStyle = TextStyle(
                            color = AccentGold,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        cursorBrush = SolidColor(AccentGold),
                        singleLine = true,
                        modifier = Modifier.width(90.dp)
                    )

                    Text(
                        text = "/ 10.0",
                        color = TextMuted,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                RepeatingHoldButton(
                    onStep = { updateScore(scoreValue + 0.1f) },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AccentGold.copy(alpha = 0.2f))
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Plus 0.1", tint = AccentGold)
                }
            }

            // Decimal Slider (1.0 to 10.0, step 0.1)
            Slider(
                value = scoreValue,
                onValueChange = { updateScore(it) },
                valueRange = 1.0f..10.0f,
                steps = 89, // (10.0 - 1.0) / 0.1 - 1 = 89 discrete steps
                colors = SliderDefaults.colors(
                    thumbColor = AccentGold,
                    activeTrackColor = AccentGold,
                    inactiveTrackColor = DarkSurfaceElevated
                )
            )

            // Decimal Stepper Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val steppers = listOf(-1.0f, -0.5f, -0.1f, 0.1f, 0.5f, 1.0f)
                steppers.forEach { delta ->
                    val isPos = delta > 0
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPos) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
                            .clickable { updateScore(scoreValue + delta) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isPos) "+${String.format("%.1f", delta)}" else String.format("%.1f", delta),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPos) AccentGold else TextMuted
                        )
                    }
                }
            }

            // Quick Preset Score Pills - Stable Non-Flickering Style
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(5.0f, 6.0f, 7.0f, 8.0f, 8.5f, 9.0f, 9.5f, 10.0f)
                presets.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                            .clickable { updateScore(preset) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = String.format("%.1f", preset),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (initialScore != null) {
                    Button(
                        onClick = {
                            onScoreConfirmed(null)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Clear Score", color = Color(0xFFEF4444), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

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
                        val parsed = textInput.toFloatOrNull() ?: scoreValue
                        val finalClamped = ((parsed.coerceIn(1.0f, 10.0f) * 10f).roundToInt()) / 10f
                        onScoreConfirmed(finalClamped)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Save Score", color = AmoledBlack, fontWeight = FontWeight.Bold)
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

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnStep()
                    val job = coroutineScope {
                        launch {
                            delay(initialDelayMs)
                            while (isActive) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnStep()
                                delay(repeatIntervalMs)
                            }
                        }
                    }
                    tryAwaitRelease()
                    job.cancel()
                }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
