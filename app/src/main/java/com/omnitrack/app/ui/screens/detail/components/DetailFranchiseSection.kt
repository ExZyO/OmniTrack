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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.data.model.AnimeRelationItem
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.SurfaceCard
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

@Composable
fun DetailFranchiseSection(
    relations: List<AnimeRelationItem>,
    modifier: Modifier = Modifier,
    onRelationClick: (AnimeRelationItem) -> Unit
) {
    if (relations.isEmpty()) return
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Franchise & Adaptations",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            Text(
                text = "${relations.size} entries",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(relations, key = { index, item -> "${item.id}_${item.relationType}_$index" }) { _, item ->
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .clickable { onRelationClick(item) },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(138.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    ) {
                        if (!item.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(item.coverUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }

                    Text(
                        text = item.title,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val typeLabel = when (item.relationType.uppercase()) {
                        "MANGA" -> "Manga"
                        "NOVEL" -> "Light Novel"
                        "SEQUEL" -> "Sequel"
                        "PREQUEL" -> "Prequel"
                        "SIDE_STORY" -> "Side Story"
                        "SPIN_OFF" -> "Spin-Off"
                        "ALTERNATIVE" -> "Alt Version"
                        else -> item.format.lowercase().replaceFirstChar { it.uppercase() }
                    }

                    val subText = if (item.year != null) "$typeLabel • ${item.year}" else typeLabel

                    Text(
                        text = subText,
                        color = TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
