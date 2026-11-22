package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.model.BookAuthorProfile
import com.omnitrack.app.ui.components.FormattedRichText
import com.omnitrack.app.ui.components.NuvioRailPosterCard
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

@Composable
fun DetailBookAuthorSection(
    authorProfile: BookAuthorProfile,
    onSelectWork: (RecommendationItem) -> Unit,
    onOpenAuthorProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var bioExpanded by remember(authorProfile.name) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "ABOUT THE AUTHOR",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.1.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.09f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                .clickable { onOpenAuthorProfile() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                if (!authorProfile.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = authorProfile.photoUrl,
                        contentDescription = authorProfile.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Text(
                        text = authorProfile.name.take(1).uppercase(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = authorProfile.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2
                )
                authorProfile.birthDeath?.takeIf { it.isNotBlank() }?.let { dates ->
                    Text(
                        text = dates,
                        fontSize = 12.5.sp,
                        color = TextSecondary
                    )
                }
                Text(
                    text = "View full profile & works",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = AccentGold
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open author profile",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        authorProfile.bio?.takeIf { it.isNotBlank() }?.let { bio ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clickable { bioExpanded = !bioExpanded },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FormattedRichText(
                    rawText = bio,
                    color = TextSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    maxLines = if (bioExpanded) 18 else 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (bioExpanded) "Show less" else "Read biography",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGold
                )
            }
        }

        if (authorProfile.works.isNotEmpty()) {
            Text(
                text = "MORE BY ${authorProfile.name.uppercase()}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.9.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = authorProfile.works,
                    key = { "${it.openLibraryId ?: ""}_${it.title}" }
                ) { work ->
                    NuvioRailPosterCard(
                        title = work.title,
                        coverUrl = work.coverUrl,
                        subtitle = work.releaseYear,
                        rating = work.rating,
                        onClick = { onSelectWork(work) },
                        cardWidth = 116.dp,
                        cardHeight = 174.dp
                    )
                }
            }
        }
    }
}
