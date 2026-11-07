package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * Nuvio-style horizontal row of branded AMOLED glass rating badges:
 * - IMDb (Gold Star ⭐)
 * - Rotten Tomatoes (Critic 🍅 & Audience 🍿)
 * - Metacritic (Green/Yellow square)
 * - TMDb (Green percentage circle)
 * - Trakt (Red heart ❤️)
 * - AniList (Arctic Diamond 💠)
 * - MyAnimeList (Blue Star ★)
 * - Steam (Blue Thumbs Up 👍)
 * - VNDB (Visual Novel score)
 */
@Composable
fun NuvioRatingsRow(
    item: MediaItemEntity,
    modifier: Modifier = Modifier
) {
    val hasAnyRatings = item.imdbScore != null ||
            item.rtScore != null ||
            item.metacriticScore != null ||
            item.communityScore != null

    if (!hasAnyRatings) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. IMDb Pill (Gold)
        item.imdbScore?.let { score ->
            BrandedRatingPill(
                icon = "⭐",
                label = "IMDb",
                value = String.format("%.1f", score),
                accentColor = Color(0xFFF5C518),
                badgeColor = Color(0xFFF5C518).copy(alpha = 0.16f)
            )
        }

        // 2. Rotten Tomatoes (Critic)
        item.rtScore?.let { rt ->
            val isFresh = rt >= 60
            BrandedRatingPill(
                icon = if (isFresh) "🍅" else "🟢",
                label = "Rotten Tomatoes",
                value = "$rt%",
                accentColor = if (isFresh) Color(0xFFFA320A) else Color(0xFF589741),
                badgeColor = if (isFresh) Color(0xFFFA320A).copy(alpha = 0.16f) else Color(0xFF589741).copy(alpha = 0.16f)
            )
        }

        // 3. Metacritic
        item.metacriticScore?.let { meta ->
            val metaColor = when {
                meta >= 61 -> Color(0xFF66CC33)
                meta >= 40 -> Color(0xFFFFCC33)
                else -> Color(0xFFFF0000)
            }
            BrandedRatingPill(
                icon = "M",
                label = "Metacritic",
                value = "$meta",
                accentColor = metaColor,
                badgeColor = metaColor.copy(alpha = 0.16f)
            )
        }

        // 4. TMDb or Trakt Community Score
        if (item.mediaType == MediaType.SHOW || item.mediaType == MediaType.MOVIE) {
            item.communityScore?.let { score ->
                BrandedRatingPill(
                    icon = "TM",
                    label = "TMDb",
                    value = "${(score * 10).toInt()}%",
                    accentColor = Color(0xFF01D277),
                    badgeColor = Color(0xFF01D277).copy(alpha = 0.16f)
                )
            }
        }

        // 5. AniList Community Score
        if (item.mediaType == MediaType.ANIME || item.mediaType == MediaType.MANGA || item.mediaType == MediaType.NOVEL) {
            item.communityScore?.let { score ->
                BrandedRatingPill(
                    icon = "AL",
                    label = "AniList",
                    value = "${score.toInt()}%",
                    accentColor = Color(0xFF02A9FF),
                    badgeColor = Color(0xFF02A9FF).copy(alpha = 0.16f)
                )
            }
        }

        // 6. MyAnimeList Score
        if (item.malId != null && (item.mediaType == MediaType.ANIME || item.mediaType == MediaType.MANGA)) {
            item.communityScore?.let { score ->
                BrandedRatingPill(
                    icon = "★",
                    label = "MAL",
                    value = String.format("%.2f", if (score > 10f) score / 10f else score),
                    accentColor = Color(0xFF2E51A2),
                    badgeColor = Color(0xFF2E51A2).copy(alpha = 0.16f)
                )
            }
        }

        // 7. Steam Positive Review % (Games)
        if (item.mediaType == MediaType.GAME) {
            item.communityScore?.let { score ->
                BrandedRatingPill(
                    icon = "👍",
                    label = "Steam",
                    value = "${score.toInt()}%",
                    accentColor = Color(0xFF66C0F4),
                    badgeColor = Color(0xFF66C0F4).copy(alpha = 0.16f)
                )
            }
        }

        // 8. VNDB Rating (Visual Novels)
        if (item.mediaType == MediaType.VISUAL_NOVEL) {
            item.communityScore?.let { score ->
                BrandedRatingPill(
                    icon = "VN",
                    label = "VNDB",
                    value = String.format("%.2f", if (score > 10f) score / 10f else score),
                    accentColor = Color(0xFF3B82F6),
                    badgeColor = Color(0xFF3B82F6).copy(alpha = 0.16f)
                )
            }
        }
    }
}

@Composable
private fun BrandedRatingPill(
    icon: String,
    label: String,
    value: String,
    accentColor: Color,
    badgeColor: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, GlassBorder, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Icon / Logo mark
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(badgeColor)
                .padding(horizontal = 5.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Black,
                color = accentColor
            )
        }

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )

        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
    }
}
