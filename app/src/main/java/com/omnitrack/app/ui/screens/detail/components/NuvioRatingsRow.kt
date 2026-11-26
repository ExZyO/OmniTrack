package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.R
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.BookDetailMeta
import com.omnitrack.app.data.model.ComicDetailMeta
import com.omnitrack.app.data.model.GameDetailMeta
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.VnDetailMeta
import kotlin.math.roundToInt

data class RatingBadgeData(
    val source: String,
    val displayName: String,
    val iconRes: Int,
    val iconWidth: Dp = 16.dp,
    val iconHeight: Dp = 16.dp,
    val valueText: String,
    val valueColor: Color
)

fun allowedRatingSourcesFor(mediaType: MediaType): Set<String> = when (mediaType) {
    MediaType.ANIME -> setOf("anilist", "mal", "bangumi", "kitsu", "imdb", "tmdb", "trakt", "tomatoes", "audience", "metacritic", "score", "you")
    MediaType.SHOW, MediaType.MOVIE -> setOf("imdb", "tmdb", "trakt", "tomatoes", "audience", "metacritic", "bangumi", "score", "you")
    MediaType.MANGA -> setOf("anilist", "mal", "mangaupdates", "kitsu", "bangumi", "score", "you")
    MediaType.NOVEL -> setOf("anilist", "mal", "mangaupdates", "kitsu", "bangumi", "hardcover", "openlibrary", "applebooks", "score", "you")
    MediaType.BOOK -> setOf("hardcover", "openlibrary", "applebooks", "score", "you")
    MediaType.COMIC -> setOf("comicvine", "metron", "hardcover", "openlibrary", "applebooks", "score", "you")
    MediaType.GAME -> setOf("igdb", "igdb_critic", "steam", "metacritic", "bangumi", "score", "you")
    MediaType.VISUAL_NOVEL -> setOf("vndb", "steam", "bangumi", "anilist", "score", "you")
    MediaType.FANFICTION -> setOf("score", "you")
    MediaType.ALL -> setOf("imdb", "tmdb", "trakt", "tomatoes", "audience", "metacritic", "anilist", "mal", "bangumi", "kitsu", "mangaupdates", "steam", "igdb", "igdb_critic", "vndb", "hardcover", "openlibrary", "applebooks", "score", "you")
}

fun ratingSourceDisplayName(source: String): String = when (source.trim().lowercase()) {
    "imdb" -> "IMDb"
    "tmdb" -> "TMDB"
    "trakt" -> "Trakt"
    "audience" -> "Audience Score"
    "tomatoes" -> "Rotten Tomatoes"
    "metacritic" -> "Metacritic"
    "igdb" -> "IGDB"
    "igdb_critic" -> "IGDB Critics"
    "anilist" -> "AniList"
    "mal" -> "MyAnimeList"
    "bangumi" -> "Bangumi"
    "kitsu" -> "Kitsu"
    "mangaupdates" -> "MangaUpdates"
    "steam" -> "Steam"
    "vndb" -> "VNDB"
    "hardcover" -> "Hardcover"
    "openlibrary" -> "Open Library"
    "applebooks" -> "Apple Books"
    else -> source.replaceFirstChar { it.uppercase() }
}

fun collectRatingBadgesForEntry(
    item: MediaItemEntity,
    anilistScore: Float? = null,
    malScore: Float? = null,
    kitsuScore: Float? = null,
    mangaUpdatesScore: Float? = null,
    bangumiScore: Float? = null,
    vnDetailMeta: VnDetailMeta? = null,
    gameDetailMeta: GameDetailMeta? = null,
    bookDetailMeta: BookDetailMeta? = null,
    comicDetailMeta: ComicDetailMeta? = null
): List<RatingBadgeData> {
    val allowed = allowedRatingSourcesFor(item.mediaType)
    val list = mutableListOf<RatingBadgeData>()

    // 1. IMDb (Video & Anime only)
    if ("imdb" in allowed) {
        item.imdbScore?.let { score ->
            if (score > 0f) {
                list.add(
                    RatingBadgeData(
                        source = "imdb",
                        displayName = "IMDb",
                        iconRes = R.drawable.rating_imdb,
                        iconWidth = 30.dp,
                        iconHeight = 16.dp,
                        valueText = formatSmartRating(score.toDouble()),
                        valueColor = Color(0xFFF5C518)
                    )
                )
            }
        }
    }

    // 2. TMDb Score (Shows, Movies, & Anime with TMDb ID)
    if ("tmdb" in allowed && (item.mediaType == MediaType.SHOW || item.mediaType == MediaType.MOVIE || (item.mediaType == MediaType.ANIME && item.tmdbId != null))) {
        item.communityScore?.let { score ->
            if (score > 0f) {
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

    // 3. Trakt Community Score (Video & Anime only)
    if ("trakt" in allowed) {
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
    }

    // 4. Rotten Tomatoes Audience (Video & Anime only)
    if ("audience" in allowed) {
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
    }

    // 5. Rotten Tomatoes Critic (Video & Anime only)
    if ("tomatoes" in allowed) {
        item.rtScore?.let { rt ->
            if (rt > 0 && item.audienceScore == null) {
                val iconRes = when {
                    rt >= 75 -> R.drawable.rating_rotten_tomatoes_certified
                    rt >= 60 -> R.drawable.rating_rotten_tomatoes
                    else -> R.drawable.rating_rotten_tomatoes_rotten
                }
                val iconSize = if (rt >= 75) 20.dp else 16.dp
                list.add(
                    RatingBadgeData(
                        source = "tomatoes",
                        displayName = "Rotten Tomatoes",
                        iconRes = iconRes,
                        iconWidth = iconSize,
                        iconHeight = iconSize,
                        valueText = "$rt%",
                        valueColor = Color(0xFFFA320A)
                    )
                )
            }
        }
    }

    // 5b. IGDB User & Critic Scores (Video Games)
    if (item.mediaType == MediaType.GAME) {
        val effIgdbUser = gameDetailMeta?.igdbUserRating
            ?: if (item.igdbId != null || gameDetailMeta?.steamPositivePct == null) item.communityScore else null
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
                        valueText = formatSmartRating(userVal),
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
                        displayName = "IGDB Critics",
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

    // 6. Metacritic (Video, Anime & Games only)
    if ("metacritic" in allowed) {
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
    }

    // 7. AniList Community Score
    if ("anilist" in allowed) {
        val effAniScore = anilistScore ?: if (
            item.anilistId != null ||
            ((item.mediaType == MediaType.ANIME || item.mediaType == MediaType.MANGA || item.mediaType == MediaType.NOVEL) &&
                item.malId == null && item.bangumiId == null && kitsuScore == null && mangaUpdatesScore == null)
        ) item.communityScore else null
        effAniScore?.let { score ->
            if (score > 0f && item.mediaType in listOf(MediaType.ANIME, MediaType.MANGA, MediaType.NOVEL)) {
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
    }

    // 8. MyAnimeList Score
    if ("mal" in allowed) {
        val effMalScore = malScore ?: if (item.malId != null && item.mediaType in listOf(MediaType.ANIME, MediaType.MANGA, MediaType.NOVEL)) {
            item.communityScore
        } else null
        effMalScore?.let { score ->
            if (score > 0f) {
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
    }

    // 9. Bangumi Score
    if ("bangumi" in allowed) {
        val effBgmScore = bangumiScore ?: gameDetailMeta?.bangumiScore ?: if (item.bangumiId != null) item.communityScore else null
        effBgmScore?.let { score ->
            if (score > 0f) {
                val bgmVal = if (score > 10f) score / 10.0 else score.toDouble()
                list.add(
                    RatingBadgeData(
                        source = "bangumi",
                        displayName = "Bangumi",
                        iconRes = R.drawable.brand_bangumi,
                        valueText = formatSmartRating(bgmVal),
                        valueColor = Color(0xFFF09199)
                    )
                )
            }
        }
    }

    // 9b. Kitsu Community Score
    if ("kitsu" in allowed) {
        kitsuScore?.let { score ->
            if (score > 0f) {
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
    }

    // 9c. MangaUpdates Score
    if ("mangaupdates" in allowed) {
        mangaUpdatesScore?.let { score ->
            if (score > 0f) {
                val muVal = if (score > 10f) score / 10.0 else score.toDouble()
                list.add(
                    RatingBadgeData(
                        source = "mangaupdates",
                        displayName = "MangaUpdates",
                        iconRes = R.drawable.brand_mangaupdates,
                        valueText = formatSmartRating(muVal),
                        valueColor = Color(0xFF64B5F6)
                    )
                )
            }
        }
    }

    // 10. Steam Positive %
    if ("steam" in allowed) {
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
    }

    // 11. VNDB Rating
    if (item.mediaType == MediaType.VISUAL_NOVEL && "vndb" in allowed) {
        val effVn = vnDetailMeta?.vndbScore ?: item.communityScore
        effVn?.let { score ->
            if (score > 0f) {
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

    // 12. Books & Comics Ratings (Hardcover, Open Library, Apple Books on crisp 5-star scale)
    if (item.mediaType == MediaType.BOOK || item.mediaType == MediaType.COMIC) {
        if (item.mediaType == MediaType.BOOK && "hardcover" in allowed) {
            bookDetailMeta?.hardcoverRating?.takeIf { it > 0f }?.let { hc ->
                list.add(
                    RatingBadgeData(
                        source = "hardcover",
                        displayName = "Hardcover",
                        iconRes = R.drawable.brand_hardcover,
                        valueText = "${formatSmartRating(hc.toDouble())}/5",
                        valueColor = Color(0xFFA5B4FC)
                    )
                )
            }
        }
        if ("openlibrary" in allowed) {
            val effOl5 = (if (item.mediaType == MediaType.COMIC) comicDetailMeta?.openLibraryRating else bookDetailMeta?.openLibraryRating)?.takeIf { it > 0f }
                ?: item.communityScore?.takeIf {
                    it > 0f &&
                        bookDetailMeta?.hardcoverRating == null &&
                        bookDetailMeta?.appleBooksRating == null &&
                        comicDetailMeta?.appleBooksRating == null
                }?.let { if (it > 5f) it / 2f else it }
            effOl5?.let { ol ->
                list.add(
                    RatingBadgeData(
                        source = "openlibrary",
                        displayName = "Open Library",
                        iconRes = R.drawable.brand_openlibrary,
                        valueText = "${formatSmartRating(ol.toDouble())}/5",
                        valueColor = Color(0xFF38BDF8)
                    )
                )
            }
        }
        if ("applebooks" in allowed) {
            val effApple5 = (if (item.mediaType == MediaType.COMIC) comicDetailMeta?.appleBooksRating else bookDetailMeta?.appleBooksRating)?.takeIf { it > 0f }
            effApple5?.let { ab ->
                list.add(
                    RatingBadgeData(
                        source = "applebooks",
                        displayName = "Apple Books",
                        iconRes = R.drawable.brand_apple_books,
                        valueText = "${formatSmartRating(ab.toDouble())}/5",
                        valueColor = Color(0xFFFB923C)
                    )
                )
            }
        }
    }

    return list
}

/**
 * 1:1 Nuvio Unboxed Ratings Row:
 * Zero pills, zero emojis, exact official brand vector/raster logos with bold typography.
 * Supports category-strict rating isolation and per-entry user hidden rating sources.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    gameDetailMeta: GameDetailMeta? = null,
    bookDetailMeta: BookDetailMeta? = null,
    comicDetailMeta: ComicDetailMeta? = null,
    hiddenSources: Set<String> = emptySet(),
    onLongPressBadge: ((RatingBadgeData) -> Unit)? = null,
    centered: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val badges = remember(
        item, anilistScore, malScore, kitsuScore, mangaUpdatesScore, bangumiScore,
        vnDetailMeta, gameDetailMeta, bookDetailMeta, comicDetailMeta, hiddenSources
    ) {
        val normHidden = hiddenSources.map { it.trim().lowercase() }.toSet()
        collectRatingBadgesForEntry(
            item = item,
            anilistScore = anilistScore,
            malScore = malScore,
            kitsuScore = kitsuScore,
            mangaUpdatesScore = mangaUpdatesScore,
            bangumiScore = bangumiScore,
            vnDetailMeta = vnDetailMeta,
            gameDetailMeta = gameDetailMeta,
            bookDetailMeta = bookDetailMeta,
            comicDetailMeta = comicDetailMeta
        ).filter { it.source.lowercase() !in normHidden }
    }

    if (badges.isEmpty()) return

    val rowContent: @Composable () -> Unit = {
        val ratingTextStyle = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp
        )

        badges.forEach { badge ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (onLongPressBadge != null) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPressBadge(badge)
                        }
                    )
                } else {
                    Modifier
                }
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

    if (centered) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowContent()
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            rowContent()
        }
    }
}

fun formatSmartRating(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if ((kotlin.math.round(rounded * 10.0).toLong() % 10L) == 0L) {
        "${kotlin.math.round(rounded).toInt()}"
    } else {
        String.format(java.util.Locale.US, "%.1f", rounded)
    }
}

private fun formatMalScore(value: Double): String {
    val roundedTwo = (value * 100.0).roundToInt() / 100.0
    val formatted = String.format(java.util.Locale.US, "%.2f", roundedTwo)
    return when {
        formatted.endsWith(".00") -> "${kotlin.math.round(roundedTwo).toInt()}"
        formatted.endsWith("0") -> String.format(java.util.Locale.US, "%.1f", roundedTwo)
        else -> formatted
    }
}

private fun formatTwoDecimals(value: Double): String {
    return formatMalScore(value)
}
