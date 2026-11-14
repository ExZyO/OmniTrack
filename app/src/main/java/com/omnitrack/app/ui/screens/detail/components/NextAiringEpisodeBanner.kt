package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.model.NextAiringEpisode
import com.omnitrack.app.util.AirDateUtils

/**
 * 1:1 Nuvio Upstream Next Airing Episode Countdown Banner.
 * Displays a glassmorphic card showcasing upcoming broadcast timing,
 * season/episode numbers, and relative air date countdowns.
 */
@Composable
fun NextAiringEpisodeBanner(
    nextAiring: NextAiringEpisode,
    modifier: Modifier = Modifier,
    backdropUrl: String? = null,
    onBannerClick: () -> Unit = {}
) {
    val cardShape = RoundedCornerShape(16.dp)
    val cyanAccent = Color(0xFF00D2FF)

    val relativeText = remember(nextAiring.timeUntilAiringSec, nextAiring.airDate) {
        if (nextAiring.timeUntilAiringSec != null && nextAiring.timeUntilAiringSec > 0) {
            "Airs in " + AirDateUtils.formatCountdown(nextAiring.timeUntilAiringSec)
        } else {
            AirDateUtils.computeAirDateBadgeText(nextAiring.airDate) ?: "Upcoming"
        }
    }

    val fullDate = remember(nextAiring.airDate) {
        AirDateUtils.formatAiringFullDate(nextAiring.airDate)
    }

    val epCode = remember(nextAiring.seasonNumber, nextAiring.episodeNumber) {
        if (nextAiring.seasonNumber != null && nextAiring.seasonNumber > 0) {
            "S${nextAiring.seasonNumber} E${nextAiring.episodeNumber}"
        } else {
            "Episode ${nextAiring.episodeNumber}"
        }
    }

    val displayTitle = remember(epCode, nextAiring.title) {
        if (!nextAiring.title.isNullOrBlank()) {
            "$epCode • ${nextAiring.title}"
        } else {
            epCode
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(cardShape)
            .background(Color(0xFF0D1017))
            .border(
                BorderStroke(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            cyanAccent.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                ),
                shape = cardShape
            )
            .clickable(onClick = onBannerClick)
    ) {
        // Optional Ambient Backdrop Wash
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.16f
            )
        }

        // Multi-Stop Glass Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xFF0C111C).copy(alpha = 0.92f),
                        0.55f to Color(0xFF0F1422).copy(alpha = 0.88f),
                        1f to Color(0xFF0A0D14).copy(alpha = 0.95f)
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            // Left Glowing Clock Icon Box
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(cyanAccent.copy(alpha = 0.14f))
                    .border(1.dp, cyanAccent.copy(alpha = 0.30f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = "Upcoming Episode",
                    tint = cyanAccent,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Middle Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "NEXT AIRING EPISODE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = cyanAccent,
                        letterSpacing = 1.1.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = cyanAccent.copy(alpha = 0.18f),
                        border = BorderStroke(0.8.dp, cyanAccent.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = relativeText,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = cyanAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                }

                Text(
                    text = displayTitle,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!fullDate.isNullOrBlank()) {
                    Text(
                        text = fullDate,
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Forward Chevron
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Go to season",
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
