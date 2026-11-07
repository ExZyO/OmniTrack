package com.omnitrack.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.R
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AccentViolet
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

data class PlatformMeta(
    val id: String,
    val displayName: String,
    val iconRes: Int
)

val availableGamePlatforms = listOf(
    PlatformMeta("PC", "PC", R.drawable.ic_platform_pc),
    PlatformMeta("Steam Deck", "Steam Deck", R.drawable.ic_platform_steam_deck),
    PlatformMeta("PS5", "PlayStation", R.drawable.ic_platform_ps5),
    PlatformMeta("Switch", "Switch", R.drawable.ic_platform_switch),
    PlatformMeta("Xbox", "Xbox", R.drawable.ic_platform_xbox)
)

/**
 * Unified "All 3" Game Progress Dashboard & Justified Platform Chips:
 * 1. Playtime (hrs stepper + Steam sync)
 * 2. Achievements (x/y + visual progress bar)
 * 3. Story / Chapter Progress Stepper
 * Plus real vector brand logos with multi-select platform support and Steam store link.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameProgressCard(
    hoursPlayed: Double,
    storyProgress: Double,
    totalStoryChapters: Double?,
    achievementsUnlocked: Int?,
    achievementsTotal: Int?,
    selectedPlatforms: Set<String>,
    steamAppId: String? = null,
    onHoursChanged: (Double) -> Unit,
    onStoryProgressChanged: (Double) -> Unit,
    onPlatformToggled: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Metric 1: Playtime (Hours Stepper)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF121212).copy(alpha = 0.75f))
                .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .clickable { onHoursChanged((hoursPlayed - 1.0).coerceAtLeast(0.0)) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Minus 1 hr",
                        tint = AccentArctic,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    Text(
                        text = "HOURS PLAYED",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .clickable { onHoursChanged(hoursPlayed + 1.0) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Plus 1 hr",
                        tint = AccentArctic,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Metric 2: Achievements (if available or Steam connected)
        if (achievementsTotal != null && achievementsTotal > 0) {
            val unlocked = achievementsUnlocked ?: 0
            val pct = (unlocked.toFloat() / achievementsTotal.toFloat()).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF121212).copy(alpha = 0.75f))
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, tint = AccentGold, modifier = Modifier.size(16.dp))
                            Text("Achievements", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Text(
                            text = "$unlocked / $achievementsTotal (${(pct * 100).toInt()}%)",
                            color = AccentGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = AccentGold,
                        trackColor = Color.White.copy(alpha = 0.08f)
                    )
                }
            }
        }

        // Metric 3: Story / Chapter Stepper
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121212).copy(alpha = 0.75f))
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Story / Act Progress",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Chapter ${storyProgress.toInt()}${totalStoryChapters?.let { " of ${it.toInt()}" } ?: ""}",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x1AFFFFFF))
                            .clickable { onStoryProgressChanged((storyProgress - 1.0).coerceAtLeast(0.0)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AccentArctic)
                            .clickable { onStoryProgressChanged(storyProgress + 1.0) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Platform Chips with Real Brand Logos (Multi-Select & Justified)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PLAYING ON PLATFORMS",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )

                if (!steamAppId.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://store.steampowered.com/app/$steamAppId")
                                )
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Open on Steam", color = Color(0xFF66C0F4), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFF66C0F4), modifier = Modifier.size(12.dp))
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableGamePlatforms.forEach { platform ->
                    val isSelected = selectedPlatforms.contains(platform.id)

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isSelected) AccentViolet.copy(alpha = 0.28f)
                                else Color(0xFF141414)
                            )
                            .border(
                                1.dp,
                                if (isSelected) AccentViolet else GlassBorder,
                                RoundedCornerShape(999.dp)
                            )
                            .clickable { onPlatformToggled(platform.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = platform.iconRes),
                            contentDescription = platform.displayName,
                            tint = if (isSelected) AccentViolet else TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = platform.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) TextPrimary else TextSecondary
                        )
                    }
                }
            }
        }
    }
}
