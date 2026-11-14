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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.data.model.AnimeStaffItem
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.SurfaceCard
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * 100% unboxed AMOLED horizontal rail displaying anime production staff
 * (Director, Original Creator, Music, Character Designer, etc.).
 */
@Composable
fun DetailAnimeStaffSection(
    staff: List<AnimeStaffItem>,
    modifier: Modifier = Modifier,
    onStaffClick: ((AnimeStaffItem) -> Unit)? = null
) {
    if (staff.isEmpty()) return
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
                text = "Staff",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            Text(
                text = "${staff.size} staff",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(staff, key = { index, item -> "${item.staffId}_${item.role}_$index" }) { _, item ->
                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .then(if (onStaffClick != null) Modifier.clickable { onStaffClick(item) } else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(SurfaceCard)
                            .border(1.dp, GlassBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!item.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(item.imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                        } else {
                            Text(
                                text = item.name.take(1).uppercase(),
                                color = TextMuted,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = item.name,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = item.role,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
