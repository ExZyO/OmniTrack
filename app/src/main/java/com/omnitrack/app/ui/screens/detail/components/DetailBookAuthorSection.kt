package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
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
import coil.compose.AsyncImage
import com.omnitrack.app.data.model.BookAuthorProfile
import com.omnitrack.app.ui.components.NuvioRailPosterCard
import com.omnitrack.app.ui.theme.AccentGold

@Composable
fun DetailBookAuthorSection(
    authorProfile: BookAuthorProfile,
    onSelectWork: (RecommendationItem) -> Unit,
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
            color = Color.White.copy(alpha = 0.60f),
            letterSpacing = 1.1.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
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
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = authorProfile.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2
                )
                authorProfile.birthDeath?.takeIf { it.isNotBlank() }?.let { dates ->
                    Text(
                        text = dates,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.56f)
                    )
                }
            }
        }

        authorProfile.bio?.takeIf { it.isNotBlank() }?.let { bio ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clickable { bioExpanded = !bioExpanded },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = bio,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    color = Color.White.copy(alpha = 0.78f),
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
                color = Color.White.copy(alpha = 0.52f),
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
                        cardWidth = 108.dp
                    )
                }
            }
        }
    }
}
