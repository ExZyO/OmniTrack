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
import com.omnitrack.app.data.model.GameDetailMeta
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.VnDetailMeta
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
 * Follows Nuvio's exact hierarchy: IMDb -> TMDb -> Trakt -> Rotten Tomatoes / Audience -> Metacritic
 */
@Composable
fun NuvioRatingsRow(
    item: MediaItemEntity,
    modifier: Modifier = Modifier,
    anilistScore: Float? = null,
    malScore: Float? = null,
    kitsuScore: Float? = null,
    mangaUpdatesScore: Float? = null,
    bangumiScore: Float? = null,
    vnDetailMeta: VnDetailMeta? = null,
    gameDetailMeta: GameDetailMeta? = null
) {
    val badges = remember(item, anilistScore, malScore, kitsuScore, mangaUpdatesScore, bangumiScore, vnDetailMeta, gameDetailMeta) {
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

        // 2. TMDb Score (Shows, Movies, & Anime) - rendered as integer (e.g. 84)
        if (item.mediaType == MediaType.SHOW || item.mediaType == MediaType.MOVIE || (item.mediaType == MediaType.ANIME && item.tmdbId != null)) {
            item.communityScore?.let { score ->
                if (score > 0) {
                    val tmdbVal = if (score <= 10f) (score * 10).roundToInt() else score.roundToInt()
                    list.add(
                        RatingBadgeData(
                            source = "tmdb",
                            displayName = "TMDB",
                            iconRes = R.drawable.rating_tmdb,
                            iconWidth = 16.dp,
                            iconHeight = 16.dp,
                            valueText = "$tmdbVal",
                            valueColor = Color(0xFF01B4E4)
                        )
                    )
                }
            }
        }

        // 3. Trakt Community Score (e.g. 83)
        item.traktScore?.let { trakt ->
            if (trakt > 0) {
                list.add(
                    RatingBadgeData(
                        source = "trakt",
                        displayName = "Trakt",
                        iconRes = R.drawable.rating_trakt,
                        iconWidth = 16.dp,
                        iconHeight = 16.dp,
                        valueText = "$trakt",
                        valueColor = Color(0xFFED1C24)
                    )
                )
            }
        }

        // 4. Rotten Tomatoes Audience (Popcorn bucket 🍿, e.g. 90%)
        item.audienceScore?.let { aud ->
            if (aud > 0) {
                val iconRes = when {
                    aud >= 75 -> R.drawable.rating_audience_verified_hot
                    aud >= 60 -> R.drawable.rating_audience_score
                    else -> R.drawable.rating_audience_stale
                }
                list.add(
                    RatingBadgeData(
                        source = "audience",
                        displayName = "Audience Score",
                        iconRes = iconRes,
                        iconWidth = 16.dp,
                        iconHeight = 16.dp,
                        valueText = "$aud%",
                        valueColor = Color(0xFFFA320A)
                    )
                )
            }
        }

        // 5. Rotten Tomatoes Critic (Tomato 🍅)
        item.rtScore?.let { rt ->
            if (rt > 0 && item.audienceScore == null) {
                val iconRes = when {
                    rt >= 75 -> R.drawable.rating_rotten_tomatoes_certified
                    rt >= 60 -> R.drawable.rating_rotten_tomatoes
                    else -> R.drawable.rating_rotten_tomatoes_rotten
                }
                val iconHeight = if (rt >= 75) 20.dp else 16.dp
                val iconWidth = if (rt >= 75) 20.dp else 16.dp
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

        // 5b. IGDB User & Critic Scores (Video Games)
        if (item.mediaType == MediaType.GAME) {
            val effIgdbUser = gameDetailMeta?.igdbUserRating ?: if (item.igdbId != null || gameDetailMeta?.steamPositivePct == null) item.communityScore else null
            effIgdbUser?.let { score ->
                if (score > 0f) {
                    val userVal = if (score > 10f) score / 10.0 else score.toDouble()
                    list.add(
                        RatingBadgeData(
                            source = "igdb",
                            displayName = "IGDB",
                            iconRes = R.drawable.brand_igdb,
                            iconWidth = 16.dp,
                            iconHeight = 16.dp,
                            valueText = formatOneDecimal(userVal),
                            valueColor = Color(0xFFA78BFA)
                        )
                    )
                }
            }
            gameDetailMeta?.igdbCriticRating?.let { critic ->
                if (critic > 0) {
                    list.add(
                        RatingBadgeData(
                            source = "igdb_critic",
                            displayName = "Critics",
                            iconRes = R.drawable.ic_critic_score,
                            iconWidth = 16.dp,
                            iconHeight = 16.dp,
                            valueText = "$critic",
                            valueColor = Color(0xFFF5C518)
                        )
                    )
                }
            }
        }

        // 6. Metacritic (e.g. 65)
        (item.metacriticScore ?: gameDetailMeta?.metacriticScore)?.let { meta ->
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
                        iconWidth = 16.dp,
                        iconHeight = 16.dp,
                        valueText = "$meta",
                        valueColor = metaColor
                    )
                )
            }
        }

        // 7. AniList Community Score (100-point % scale, e.g. 84% in cyan #02A9FF)
        val effAniScore = anilistScore ?: if (
            item.anilistId != null ||
            ((item.mediaType == MediaType.ANIME || item.mediaType == MediaType.MANGA || item.mediaType == MediaType.NOVEL) && item.malId == null && item.bangumiId == null && kitsuScore == null && mangaUpdatesScore == null)
        ) item.communityScore else null
        effAniScore?.let { score ->
            if (score > 0 && item.mediaType in listOf(MediaType.ANIME, MediaType.MANGA, MediaType.NOVEL)) {
                val aniVal = if (score <= 10f) (score * 10).roundToInt() else score.roundToInt()
                list.add(
                    RatingBadgeData(
                        source = "anilist",
                        displayName = "AniList",
                        iconRes = R.drawable.brand_anilist,
                        valueText = "$aniVal%",
                        valueColor = Color(0xFF02A9FF)
                    )
                )
            }
        }

        // 8. MyAnimeList Score (10-point decimal scale, e.g. 8.45 / 8.5 in MAL blue #2E51A2)
        val effMalScore = malScore ?: if (item.malId != null && item.mediaType in listOf(MediaType.ANIME, MediaType.MANGA, MediaType.NOVEL)) {
            item.communityScore
        } else null
        effMalScore?.let { score ->
            if (score > 0) {
                val malVal = if (score > 10f) score / 10.0 else score.toDouble()
                list.add(
                    RatingBadgeData(
                        source = "mal",
                        displayName = "MyAnimeList",
                        iconRes = R.drawable.rating_mal,
                        valueText = formatMalScore(malVal),
                        valueColor = Color(0xFF5B8BFF)
                    )
                )
            }
        }

        // 9. Bangumi Score (10-point decimal scale, e.g. 8.2 in pink #F09199)
        val effBgmScore = bangumiScore ?: gameDetailMeta?.bangumiScore ?: if (item.bangumiId != null) item.communityScore else null
        effBgmScore?.let { score ->
            if (score > 0) {
                val bgmVal = if (score > 10f) score / 10.0 else score.toDouble()
                list.add(
                    RatingBadgeData(
                        source = "bangumi",
                        displayName = "Bangumi",
                        iconRes = R.drawable.brand_bangumi,
                        valueText = formatOneDecimal(bgmVal),
                        valueColor = Color(0xFFF09199)
                    )
                )
            }
        }

        // 9b. Kitsu Community Score (% scale, e.g. 85% in coral #F75239)
        kitsuScore?.let { score ->
            if (score > 0) {
                val kitsuVal = if (score <= 10f) (score * 10).roundToInt() else score.roundToInt()
                list.add(
                    RatingBadgeData(
                        source = "kitsu",
                        displayName = "Kitsu",
                        iconRes = R.drawable.brand_kitsu,
                        valueText = "$kitsuVal%",
                        valueColor = Color(0xFFF75239)
                    )
                )
            }
        }

        // 9c. MangaUpdates Score (10-point decimal scale in arctic blue #64B5F6)
        mangaUpdatesScore?.let { score ->
            if (score > 0) {
                val muVal = if (score > 10f) score / 10.0 else score.toDouble()
                list.add(
                    RatingBadgeData(
                        source = "mangaupdates",
                        displayName = "MangaUpdates",
                        iconRes = R.drawable.brand_mangaupdates,
                        valueText = formatOneDecimal(muVal),
                        valueColor = Color(0xFF64B5F6)
                    )
                )
            }
        }

        // 10. Steam Positive % (Games or Visual Novels with bridged Steam AppID)
        val effSteamPct = when {
            item.mediaType == MediaType.GAME -> gameDetailMeta?.steamPositivePct
            item.mediaType == MediaType.VISUAL_NOVEL -> vnDetailMeta?.steamPositivePct
            else -> null
        }
        effSteamPct?.let { steamVal ->
            if (steamVal > 0) {
                list.add(
                    RatingBadgeData(
                        source = "steam",
                        displayName = "Steam",
                        iconRes = R.drawable.brand_steam,
                        valueText = "$steamVal%",
                        valueColor = Color(0xFF66C0F4)
                    )
                )
            }
        }

        // 11. VNDB Rating (Visual Novels, e.g. 9.02 in VNDB blue #6BA4E8)
        if (item.mediaType == MediaType.VISUAL_NOVEL) {
            val effVn = vnDetailMeta?.vndbScore ?: item.communityScore
            effVn?.let { score ->
                if (score > 0) {
                    val vnVal = if (score > 10f) score / 10.0 else score.toDouble()
                    list.add(
                        RatingBadgeData(
                            source = "vndb",
                            displayName = "VNDB",
                            iconRes = R.drawable.brand_vndb,
                            valueText = formatTwoDecimals(vnVal),
                            valueColor = Color(0xFF6BA4E8)
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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                Spacer(modifier = Modifier.width(5.dp))
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

private fun formatMalScore(value: Double): String {
    val roundedTwo = (value * 100.0).roundToInt() / 100.0
    val formatted = String.format(java.util.Locale.US, "%.2f", roundedTwo)
    return if (formatted.endsWith("0")) {
        String.format(java.util.Locale.US, "%.1f", roundedTwo)
    } else {
        formatted
    }
}

private fun formatTwoDecimals(value: Double): String {
    return String.format(java.util.Locale.US, "%.2f", value)
}
