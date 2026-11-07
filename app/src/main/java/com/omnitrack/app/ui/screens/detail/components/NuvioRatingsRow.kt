package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.R
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private data class RatingBadgeData(
    val source: String,
    val displayName: String,
    val iconRes: Int,
    val iconWidth: Dp = 16.dp,
    val iconHeight: Dp = 16.dp,
    val valueText: String,
    val valueColor: Color
)

/**
 * 1:1 Nuvio Unboxed Ratings Row:
 * Zero pills, zero emojis, exact official brand vector/raster logos with bold typography.
 */
@Composable
fun NuvioRatingsRow(
    item: MediaItemEntity,
    modifier: Modifier = Modifier
) {
    val badges = remember(item) {
        val list = mutableListOf<RatingBadgeData>()

        // 1. IMDb (Gold Logo 30x16dp)
        item.imdbScore?.let { score ->
            if (score > 0) {
                list.add(
                    RatingBadgeData(
                        source = "imdb",
                        displayName = "IMDb",
                        iconRes = R.drawable.rating_imdb,
                        iconWidth = 30.dp,
                        iconHeight = 16.dp,
                        valueText = formatOneDecimal(score.toDouble()),
                        valueColor = Color(0xFFF5C518)
                    )
                )
            }
        }

        // 2. Rotten Tomatoes (Critic)
        item.rtScore?.let { rt ->
            if (rt > 0) {
                val iconRes = when {
                    rt >= 75 -> R.drawable.rating_rotten_tomatoes_certified
                    rt >= 60 -> R.drawable.rating_rotten_tomatoes
                    else -> R.drawable.rating_rotten_tomatoes_rotten
                }
                val iconHeight = if (rt >= 75) 24.dp else 16.dp
                val iconWidth = if (rt >= 75) 24.dp else 16.dp
                list.add(
                    RatingBadgeData(
                        source = "tomatoes",
                        displayName = "Rotten Tomatoes",
                        iconRes = iconRes,
                        iconWidth = iconWidth,
                        iconHeight = iconHeight,
                        valueText = "$rt%",
                        valueColor = Color(0xFFFA320A)
                    )
                )
            }
        }

        // 3. Metacritic
        item.metacriticScore?.let { meta ->
            if (meta > 0) {
                val metaColor = when {
                    meta >= 61 -> Color(0xFF66CC33)
                    meta >= 40 -> Color(0xFFFFCC33)
                    else -> Color(0xFFFF0000)
                }
                list.add(
                    RatingBadgeData(
                        source = "metacritic",
                        displayName = "Metacritic",
                        iconRes = R.drawable.rating_metacritic,
                        valueText = "$meta",
                        valueColor = metaColor
                    )
                )
            }
        }

        // 4. TMDb Score (Shows & Movies)
        if (item.mediaType == MediaType.SHOW || item.mediaType == MediaType.MOVIE) {
            item.communityScore?.let { score ->
                if (score > 0) {
                    list.add(
                        RatingBadgeData(
                            source = "tmdb",
                            displayName = "TMDB",
                            iconRes = R.drawable.rating_tmdb,
                            valueText = "${(score * 10).roundToInt()}",
                            valueColor = Color(0xFF01B4E4)
                        )
                    )
                }
            }
        }

        // 5. Trakt Community (when traktId is bound)
        if (item.traktId != null) {
            item.communityScore?.let { score ->
                if (score > 0) {
                    list.add(
                        RatingBadgeData(
                            source = "trakt",
                            displayName = "Trakt",
                            iconRes = R.drawable.rating_trakt,
                            valueText = "${(score * 10).roundToInt()}",
                            valueColor = Color(0xFFED1C24)
                        )
                    )
                }
            }
        }

        // 6. AniList Community Score
        if (item.anilistId != null || (item.mediaType == MediaType.ANIME || item.mediaType == MediaType.MANGA || item.mediaType == MediaType.NOVEL)) {
            item.communityScore?.let { score ->
                if (score > 0) {
                    list.add(
                        RatingBadgeData(
                            source = "anilist",
                            displayName = "AniList",
                            iconRes = R.drawable.brand_anilist,
                            valueText = "${score.roundToInt()}%",
                            valueColor = Color(0xFF02A9FF)
                        )
                    )
                }
            }
        }

        // 7. MyAnimeList Score
        if (item.malId != null) {
            item.communityScore?.let { score ->
                if (score > 0) {
                    val malVal = if (score > 10f) score / 10.0 else score.toDouble()
                    list.add(
                        RatingBadgeData(
                            source = "mal",
                            displayName = "MyAnimeList",
                            iconRes = R.drawable.rating_mal,
                            valueText = formatOneDecimal(malVal),
                            valueColor = Color(0xFF2E51A2)
                        )
                    )
                }
            }
        }

        // 8. Steam Positive % (Games)
        if (item.mediaType == MediaType.GAME) {
            item.communityScore?.let { score ->
                if (score > 0) {
                    list.add(
                        RatingBadgeData(
                            source = "steam",
                            displayName = "Steam",
                            iconRes = R.drawable.brand_steam,
                            valueText = "${score.roundToInt()}%",
                            valueColor = Color(0xFF66C0F4)
                        )
                    )
                }
            }
        }

        // 9. VNDB Rating (Visual Novels)
        if (item.mediaType == MediaType.VISUAL_NOVEL) {
            item.communityScore?.let { score ->
                if (score > 0) {
                    val vnVal = if (score > 10f) score / 10.0 else score.toDouble()
                    list.add(
                        RatingBadgeData(
                            source = "vndb",
                            displayName = "VNDB",
                            iconRes = R.drawable.brand_vndb,
                            valueText = formatOneDecimal(vnVal),
                            valueColor = Color(0xFF3B82F6)
                        )
                    )
                }
            }
        }

        list
    }

    if (badges.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ratingTextStyle = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp
        )

        badges.forEach { badge ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(badge.iconRes),
                    contentDescription = badge.displayName,
                    modifier = Modifier.size(width = badge.iconWidth, height = badge.iconHeight)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = badge.valueText,
                    style = ratingTextStyle,
                    color = badge.valueColor
                )
            }
        }
    }
}

private fun formatOneDecimal(value: Double): String {
    val rounded = (value * 10.0).roundToInt()
    val whole = rounded / 10
    val decimal = (rounded % 10).absoluteValue
    return "$whole.$decimal"
}
