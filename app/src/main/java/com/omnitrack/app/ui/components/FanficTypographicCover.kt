package com.omnitrack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.model.FanficRating
import com.omnitrack.app.data.model.FanficSite
import com.omnitrack.app.data.model.VnNsfwFilterMode
import com.omnitrack.app.data.model.formatWordCountShort
import com.omnitrack.app.data.preferences.PreferenceManager
import kotlin.math.abs

private data class FanficSpinePalette(
    val topColor: Color,
    val bottomColor: Color,
    val accentColor: Color
)

private val FANFIC_PALETTES = listOf(
    FanficSpinePalette(Color(0xFF28121B), Color(0xFF10070B), Color(0xFFE05263)),
    FanficSpinePalette(Color(0xFF121C2E), Color(0xFF070C16), Color(0xFF5C9EAD)),
    FanficSpinePalette(Color(0xFF16251F), Color(0xFF080F0C), Color(0xFF4CAF88)),
    FanficSpinePalette(Color(0xFF21172D), Color(0xFF0D0814), Color(0xFFA78BFA)),
    FanficSpinePalette(Color(0xFF2A1D12), Color(0xFF110B06), Color(0xFFE5A93C)),
    FanficSpinePalette(Color(0xFF14222A), Color(0xFF070E12), Color(0xFF60A5FA))
)

fun shouldBlurFanficCover(
    ratingCode: String?,
    hasActualImageCover: Boolean = false,
    mode: VnNsfwFilterMode = PreferenceManager.cachedFanficNsfwFilterMode
): Boolean {
    if (!hasActualImageCover) return false
    if (mode != VnNsfwFilterMode.BLUR_TAP) return false
    return FanficRating.fromRaw(ratingCode).isNsfw
}

fun shouldBlurFanficTags(
    ratingCode: String?,
    mode: VnNsfwFilterMode = PreferenceManager.cachedFanficNsfwFilterMode
): Boolean {
    if (mode != VnNsfwFilterMode.BLUR_TAP) return false
    return FanficRating.fromRaw(ratingCode).isNsfw
}

fun shouldHideFanficItem(ratingCode: String?, mode: VnNsfwFilterMode = PreferenceManager.cachedFanficNsfwFilterMode): Boolean {
    if (mode != VnNsfwFilterMode.SAFE_HIDE) return false
    return FanficRating.fromRaw(ratingCode).isNsfw
}

/**
 * Editorial book-spine cover rendered automatically for Fanfiction items when no custom
 * image or fetched fandom artwork is present. Purely typographic covers are never blurred
 * so the work title, author, and fandom remain legible at all ratings.
 */
@Composable
fun FanficTypographicCover(
    title: String,
    author: String? = null,
    primaryFandom: String? = null,
    sourceSite: String? = null,
    ratingCode: String? = null,
    wordCount: Int? = null,
    isCrossover: Boolean = false,
    compact: Boolean = false,
    isBlurred: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = remember(primaryFandom, title) {
        val seed = (primaryFandom?.takeIf { it.isNotBlank() } ?: title).hashCode()
        FANFIC_PALETTES[abs(seed) % FANFIC_PALETTES.size]
    }
    val site = remember(sourceSite) { FanficSite.fromCode(sourceSite) }
    val rating = remember(ratingCode) { FanficRating.fromRaw(ratingCode) }
    val siteColor = Color(site.accentHex)
    val wordsShort = remember(wordCount) { formatWordCountShort(wordCount) }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(palette.topColor, palette.bottomColor)
                    )
                )
        ) {
            // Left book-spine crease highlight
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                palette.accentColor.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Subtle inner frame rule
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (compact) 6.dp else 9.dp)
                    .border(
                        width = 0.75.dp,
                        color = palette.accentColor.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(6.dp)
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (compact) 8.dp else 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top metadata row: Source site + Rating + Crossover
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(siteColor.copy(alpha = 0.85f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = site.shortBadge,
                                color = Color.White,
                                fontSize = if (compact) 8.sp else 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        if (isCrossover) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.accentColor.copy(alpha = 0.24f))
                                    .border(0.6.dp, palette.accentColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "XO",
                                    color = Color.White,
                                    fontSize = if (compact) 7.5.sp else 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    val ratingColor = when (rating) {
                        FanficRating.EXPLICIT -> Color(0xFFEF4444)
                        FanficRating.MATURE -> Color(0xFFF59E0B)
                        FanficRating.TEEN -> Color(0xFF38BDF8)
                        FanficRating.GENERAL -> Color(0xFF22C55E)
                        FanficRating.NOT_RATED -> Color(0xFF94A3B8)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ratingColor.copy(alpha = 0.22f))
                            .border(0.7.dp, ratingColor.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = rating.code,
                            color = ratingColor,
                            fontSize = if (compact) 8.sp else 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Center editorial title + author
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!primaryFandom.isNullOrBlank()) {
                        Text(
                            text = primaryFandom.uppercase(),
                            color = palette.accentColor,
                            fontSize = if (compact) 8.sp else 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = title,
                        color = Color(0xFFF8FAFC),
                        fontSize = if (compact) 12.sp else 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        lineHeight = if (compact) 15.sp else 18.5.sp,
                        maxLines = if (compact) 3 else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!author.isNullOrBlank()) {
                        Text(
                            text = "by $author",
                            color = Color(0xFFCBD5E1),
                            fontSize = if (compact) 9.sp else 10.sp,
                            fontStyle = FontStyle.Italic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Bottom spine footer: thin accent rule + word count
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(palette.accentColor.copy(alpha = 0.35f))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = wordsShort.ifBlank { site.displayName },
                            color = Color(0xFFCBD5E1),
                            fontSize = if (compact) 8.5.sp else 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
        }
    }
}
