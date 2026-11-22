package com.omnitrack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.artwork.ArtworkModelResolver
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * Unified crisp rail poster card used across Home, Discover, Recommendations,
 * Person filmography, and Network/Studio browse rails.
 * Defaults to a crisp 2:3 aspect ratio derived from [cardWidth] when [cardHeight] is unspecified,
 * and cleanly supports 1:1 square artwork (e.g. Audiobooks) when [cardHeight] == [cardWidth].
 */
@Composable
fun NuvioRailPosterCard(
    title: String,
    coverUrl: String?,
    subtitle: String? = null,
    rating: Float? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 135.dp,
    cardHeight: Dp = Dp.Unspecified,
    cornerRadius: Dp = 14.dp,
    inLibraryBadge: Boolean = false,
    isFanfic: Boolean = false,
    fanficAuthor: String? = null,
    fanficFandom: String? = null,
    fanficSiteCode: String? = null,
    fanficRating: String? = null,
    fanficWordCount: Int? = null,
    fanficIsCrossover: Boolean = false
) {
    val resolvedHeight = if (cardHeight == Dp.Unspecified) cardWidth * 1.5f else cardHeight

    Column(
        modifier = modifier
            .width(cardWidth)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(resolvedHeight)
                .shadow(10.dp, RoundedCornerShape(cornerRadius))
                .clip(RoundedCornerShape(cornerRadius))
                .background(DarkSurfaceElevated)
                .border(0.6.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(cornerRadius))
        ) {
            val imageModel = remember(coverUrl) { ArtworkModelResolver.toCoilModel(coverUrl) }
            var unblurredByTap by remember(coverUrl, title) { androidx.compose.runtime.mutableStateOf(false) }
            val isBlurred = isFanfic && !unblurredByTap &&
                shouldBlurFanficCover(fanficRating, hasActualImageCover = imageModel != null)
            if (isFanfic && imageModel == null) {
                FanficTypographicCover(
                    title = title,
                    author = fanficAuthor,
                    primaryFandom = fanficFandom,
                    sourceSite = fanficSiteCode,
                    ratingCode = fanficRating,
                    wordCount = fanficWordCount,
                    isCrossover = fanficIsCrossover,
                    compact = true,
                    isBlurred = false
                )
            } else if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isBlurred) Modifier.blur(16.dp) else Modifier),
                    contentScale = ContentScale.Crop
                )
                if (isBlurred) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { unblurredByTap = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .border(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${com.omnitrack.app.data.model.FanficRating.fromRaw(fanficRating).label.uppercase()} \u00B7 TAP",
                                color = Color(0xFFFBBF24),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                }
            }

            if (inLibraryBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AmoledBlack.copy(alpha = 0.85f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LIB",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentGold,
                        letterSpacing = 0.4.sp
                    )
                }
            }

            // Rating badge overlay
            if (rating != null && rating > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AmoledBlack.copy(alpha = 0.85f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "\u2605 ${com.omnitrack.app.ui.screens.detail.components.formatSmartRating(rating.toDouble())}",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold
                    )
                }
            }
        }

        // Title and Subtitle
        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
