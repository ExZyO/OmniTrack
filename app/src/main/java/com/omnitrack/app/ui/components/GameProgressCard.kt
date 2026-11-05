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
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
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

/**
 * Unboxed Nuvio-style Game Progress Section:
 * Floating hours typography, quick +/- steppers, and platform pills without heavy card boxes.
 */
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
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Floating Hours Stepper Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .clickable { onHoursChanged((hoursPlayed - 1.0).coerceAtLeast(0.0)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = null,
                    tint = AccentArctic,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Unboxed Hours typography
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedContent(
                    targetState = hoursPlayed,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInVertically { -it } + fadeIn()) togetherWith
                                    (slideOutVertically { it } + fadeOut())
                        } else {
                            (slideInVertically { it } + fadeIn()) togetherWith
                                    (slideOutVertically { -it } + fadeOut())
                        }
                    },
                    label = "gameHoursAnim"
                ) { hours ->
                    Text(
                        text = String.format("%.1f", hours),
                        color = TextPrimary,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                }

                Text(
                    text = "hrs played",
                    color = TextMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .clickable { onHoursChanged(hoursPlayed + 1.0) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = AccentArctic,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Floating Platform Chips (Clean pills, no container box)
        Text(
            text = "PLATFORM",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentViolet.copy(alpha = 0.25f),
                        selectedLabelColor = AccentViolet,
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextMuted
                    ),
                    border = null,
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
    }
}
