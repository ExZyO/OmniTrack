package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.model.AnimeAiringSchedule
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DetailAiringStatusLine(
    schedule: AnimeAiringSchedule?,
    modifier: Modifier = Modifier,
    onReminderClick: ((Boolean) -> Unit)? = null
) {
    if (schedule == null) return

    var currentEpochSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    var isSubscribed by remember { mutableStateOf(false) }

    // Live ticking countdown
    LaunchedEffect(schedule.airingAtEpochSeconds) {
        while (true) {
            currentEpochSec = System.currentTimeMillis() / 1000L
            delay(1000L)
        }
    }

    val remainingSec = maxOf(0L, schedule.airingAtEpochSeconds - currentEpochSec)
    if (remainingSec <= 0L) return

    val days = remainingSec / 86400L
    val hours = (remainingSec % 86400L) / 3600L
    val minutes = (remainingSec % 3600L) / 60L

    val countdownText = when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }

    // Format broadcast day & time in user's device local timezone (Safeguard 5)
    val localDateTime = remember(schedule.airingAtEpochSeconds) {
        Instant.ofEpochSecond(schedule.airingAtEpochSeconds)
            .atZone(ZoneId.systemDefault())
    }
    val dayOfWeek = localDateTime.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
    val timeFormatted = localDateTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

    // Subtle pulsing dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Pulse dot
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(AccentArctic.copy(alpha = pulseAlpha))
            )
            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Episode ${schedule.episodeNumber} in $countdownText",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp
            )

            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "•",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "$dayOfWeek at $timeFormatted",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            )
        }

        // Notification Reminder Bell
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable {
                    val next = !isSubscribed
                    isSubscribed = next
                    onReminderClick?.invoke(next)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSubscribed) Icons.Outlined.NotificationsActive else Icons.Outlined.Notifications,
                contentDescription = "Remind",
                tint = if (isSubscribed) AccentArctic else TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
