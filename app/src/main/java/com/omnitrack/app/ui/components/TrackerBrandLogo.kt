package com.omnitrack.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.R
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextSecondary

@DrawableRes
fun trackerBrandDrawableRes(serviceKey: String): Int? = when (serviceKey.trim().lowercase()) {
    "anilist", "anilist graphql", "al" -> R.drawable.brand_anilist
    "mal", "myanimelist" -> R.drawable.brand_myanimelist
    "bangumi", "bgm" -> R.drawable.brand_bangumi
    "comick", "ck", "cm" -> R.drawable.brand_comick
    "trakt", "tr" -> R.drawable.rating_trakt
    "simkl", "sk" -> R.drawable.ic_tracker_simkl
    "tmdb", "tm" -> R.drawable.rating_tmdb
    "steam", "st" -> R.drawable.brand_steam
    "steamgriddb", "sgdb" -> R.drawable.brand_steamgriddb
    "vndb", "vndb kana", "vn" -> R.drawable.brand_vndb
    "hardcover", "hc" -> R.drawable.brand_hardcover
    "kitsu" -> R.drawable.brand_kitsu
    "mangadex", "mangadex api" -> R.drawable.brand_mangadex
    "mangaupdates" -> R.drawable.brand_mangaupdates
    "igdb" -> R.drawable.brand_igdb
    "openlibrary", "open library", "open_library" -> R.drawable.brand_openlibrary
    "applebooks", "apple books", "apple_books" -> R.drawable.brand_apple_books
    "thetvdb", "tvdb" -> R.drawable.brand_thetvdb
    "tvmaze" -> R.drawable.brand_tvmaze
    "rpdb" -> R.drawable.brand_rpdb
    "comic vine", "comicvine" -> R.drawable.brand_comicvine
    "metron" -> R.drawable.brand_metron
    "bookwalker" -> R.drawable.brand_bookwalker
    "naver", "naver webtoon" -> R.drawable.brand_naver
    "kuaikan" -> R.drawable.brand_kuaikan
    "ranobedb" -> R.drawable.brand_ranobedb
    "wikimedia commons", "wikimedia" -> R.drawable.brand_wikimedia
    "google books", "googlebooks" -> R.drawable.brand_google_books
    "novelupdates" -> R.drawable.brand_novelupdates
    else -> null
}

@Composable
fun TrackerBrandLogo(
    serviceKey: String,
    size: Dp = 32.dp,
    cornerRadius: Dp = 6.dp,
    innerPadding: Dp = 0.dp,
    containerColor: Color = DarkSurfaceCard,
    borderColor: Color = GlassBorder,
    showContainer: Boolean = false,
    alpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    val iconRes = trackerBrandDrawableRes(serviceKey)
    if (iconRes != null && !showContainer) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = serviceKey,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .alpha(alpha)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(containerColor)
                .border(0.8.dp, borderColor, RoundedCornerShape(cornerRadius))
                .padding(innerPadding)
                .alpha(alpha),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = serviceKey,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = serviceKey.take(2).uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
            }
        }
    }
}
