package com.omnitrack.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * Pure unboxed Nuvio Noir progress stepper:
 * Floating typography, rolling mechanical tally counter, and tactile floating controls.
 * Zero chunky card borders or boxed containers.
 */
@Composable
fun ChapterStepper(
    unitLabel: String,
    currentProgress: Double,
    totalProgress: Double?,
    onProgressChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
    isDecimal: Boolean = false,
    step: Double = 1.0
) {
    val focusManager = LocalFocusManager.current
    var showDirectInput by remember { mutableStateOf(false) }
    var textInput by remember(currentProgress) {
        mutableStateOf(
            if (isDecimal) String.format("%.1f", currentProgress)
            else currentProgress.toInt().toString()
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Unboxed Stepper Row: Floating [ - ]    845 / 1432 Chapters    [ + ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Minimal floating minus button
            UnboxedStepperButton(
                icon = Icons.Default.Remove,
                onClick = {
                    val newVal = (currentProgress - step).coerceAtLeast(0.0)
                    onProgressChanged(newVal)
                }
            )

            // Center: Big floating typography-first number (tappable for numeric keyboard)
            if (showDirectInput) {
                TextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.width(130.dp),
                    textStyle = MaterialTheme.typography.headlineLarge.copy(
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val parsed = textInput.toDoubleOrNull()
                            if (parsed != null) {
                                val clamped = if (totalProgress != null) parsed.coerceIn(0.0, totalProgress)
                                else parsed.coerceAtLeast(0.0)
                                onProgressChanged(clamped)
                            }
                            showDirectInput = false
                            focusManager.clearFocus()
                        }
                    ),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = AccentArctic,
                        focusedIndicatorColor = AccentArctic,
                        unfocusedIndicatorColor = Color(0x33FFFFFF)
                    )
                )
            } else {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showDirectInput = true }
                    )
                ) {
                    // Rolling tally animated number
                    AnimatedContent(
                        targetState = currentProgress,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInVertically { -it } + fadeIn()) togetherWith
                                        (slideOutVertically { it } + fadeOut())
                            } else {
                                (slideInVertically { it } + fadeIn()) togetherWith
                                        (slideOutVertically { -it } + fadeOut())
                            }
                        },
                        label = "progressAnim"
                    ) { progress ->
                        val displayValue = if (isDecimal) String.format("%.1f", progress)
                        else progress.toInt().toString()

                        Text(
                            text = displayValue,
                            color = TextPrimary,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    // Total progress suffix (flowing text, no badge box)
                    if (totalProgress != null) {
                        Text(
                            text = "/ ${if (isDecimal) String.format("%.1f", totalProgress) else totalProgress.toInt()} $unitLabel",
                            color = TextMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    } else if (unitLabel.isNotBlank()) {
                        Text(
                            text = unitLabel,
                            color = TextMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                }
            }

            // Minimal floating plus button
            UnboxedStepperButton(
                icon = Icons.Default.Add,
                onClick = {
                    val newVal = if (totalProgress != null)
                        (currentProgress + step).coerceAtMost(totalProgress)
                    else currentProgress + step
                    onProgressChanged(newVal)
                }
            )
        }

        // Unboxed scrub slider (seamless thin line directly on AMOLED canvas)
        if (totalProgress != null && totalProgress > 0) {
            val sliderProgress by animateFloatAsState(
                targetValue = (currentProgress / totalProgress).toFloat().coerceIn(0f, 1f),
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "slider"
            )

            Slider(
                value = sliderProgress,
                onValueChange = { fraction ->
                    val newProgress = (fraction * totalProgress)
                    val rounded = if (isDecimal) {
                        (newProgress * 10).toLong() / 10.0
                    } else {
                        newProgress.toLong().toDouble()
                    }
                    onProgressChanged(rounded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = AccentArctic,
                    activeTrackColor = AccentArctic,
                    inactiveTrackColor = Color(0x22FFFFFF)
                )
            )
        }
    }
}

@Composable
private fun UnboxedStepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0x1AFFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentArctic,
            modifier = Modifier.size(22.dp)
        )
    }
}
