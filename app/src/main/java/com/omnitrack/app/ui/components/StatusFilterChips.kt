package com.omnitrack.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary

@Composable
fun StatusFilterChips(
    selectedStatus: TrackingStatus,
    onStatusSelected: (TrackingStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(TrackingStatus.filterChips) { status ->
            val isSelected = status == selectedStatus

            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) AccentArctic.copy(alpha = 0.12f)
                else DarkSurfaceCard.copy(alpha = 0.6f),
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "chipBg"
            )

            val textColor by animateColorAsState(
                targetValue = if (isSelected) AccentArctic else TextMuted,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "chipText"
            )

            GlassmorphicSurface(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onStatusSelected(status) }
                    ),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = backgroundColor,
                borderColor = if (isSelected) AccentArctic.copy(alpha = 0.3f) else GlassBorder
            ) {
                Text(
                    text = status.displayName,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}
