package com.omnitrack.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentViolet
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

data class GamePlatform(
    val name: String,
    val isSelected: Boolean = false
)

val defaultPlatforms = listOf(
    GamePlatform("PC"),
    GamePlatform("PS5"),
    GamePlatform("Switch"),
    GamePlatform("Xbox"),
    GamePlatform("Steam Deck"),
    GamePlatform("Mobile")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameProgressCard(
    hoursPlayed: Double,
    selectedPlatform: String?,
    onHoursChanged: (Double) -> Unit,
    onPlatformSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hours stepper
        ChapterStepper(
            label = "HOURS PLAYED",
            unitLabel = "hrs",
            currentProgress = hoursPlayed,
            totalProgress = null,
            onProgressChanged = onHoursChanged,
            isDecimal = true,
            step = 0.5
        )

        // Platform chips
        GlassmorphicSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "PLATFORM",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    defaultPlatforms.forEach { platform ->
                        val isSelected = platform.name == selectedPlatform

                        FilterChip(
                            selected = isSelected,
                            onClick = { onPlatformSelected(platform.name) },
                            label = {
                                Text(
                                    text = platform.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkSurfaceElevated,
                                labelColor = TextMuted,
                                selectedContainerColor = AccentViolet.copy(alpha = 0.15f),
                                selectedLabelColor = AccentViolet
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = GlassBorder,
                                selectedBorderColor = AccentViolet.copy(alpha = 0.4f),
                                enabled = true,
                                selected = isSelected
                            )
                        )
                    }
                }
            }
        }
    }
}
