package com.omnitrack.app.ui.screens.detail.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

data class MediaTrailer(
    val title: String,
    val youtubeKey: String,
    val type: String = "Trailer"
)

/**
 * 1:1 Nuvio Trailers & Bonus Content Section:
 * Categorized video carousel supporting Trailers, Behind the Scenes, Clips, Featurettes, and Teasers.
 * Interactive category selector pill with dropdown menu, 16:9 AMOLED depth thumbnail cards, and native playback.
 */
@Composable
fun DetailTrailersSection(
    trailers: List<MediaTrailer>,
    modifier: Modifier = Modifier
) {
    if (trailers.isEmpty()) return
    val context = LocalContext.current

    // Group videos by category (Trailers, Behind the Scenes, Clips, Featurettes, Teasers)
    val grouped = remember(trailers) {
        linkedMapOf<String, MutableList<MediaTrailer>>().apply {
            trailers.forEach { trailer ->
                val category = when (trailer.type.trim().lowercase()) {
                    "trailer" -> "Trailers"
                    "teaser" -> "Teasers"
                    "clip" -> "Clips"
                    "behind the scenes" -> "Behind the Scenes"
                    "featurette" -> "Featurettes"
                    "blooper", "bloopers" -> "Bloopers"
                    else -> trailer.type.ifBlank { "Trailers" }
                }
                getOrPut(category) { mutableListOf() }.add(trailer)
            }
        }
    }

    if (grouped.isEmpty()) return

    val initialCategory = remember(grouped) {
        grouped.keys.firstOrNull { it.equals("Trailers", ignoreCase = true) }
            ?: grouped.keys.first()
    }
    var selectedCategory by remember(grouped) { mutableStateOf(initialCategory) }
    var menuExpanded by remember { mutableStateOf(false) }

    val selectedTrailers = grouped[selectedCategory].orEmpty()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header with Category Pill Selector (Aligned next to "Trailers", 1:1 Nuvio Parity)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trailers",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )

            // Category Selector Dropdown Pill (Clean category text, number count in submenu only)
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(999.dp))
                        .clickable { menuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = selectedCategory,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Select Category",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .background(DarkSurfaceElevated)
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                ) {
                    grouped.keys.forEach { cat ->
                        val count = grouped[cat]?.size ?: 0
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "$cat ($count)",
                                    fontSize = 13.sp,
                                    fontWeight = if (cat == selectedCategory) FontWeight.Bold else FontWeight.Normal,
                                    color = if (cat == selectedCategory) Color(0xFF00E676) else TextPrimary
                                )
                            },
                            onClick = {
                                selectedCategory = cat
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 16:9 Thumbnail Cards Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(selectedTrailers, key = { "${it.youtubeKey}_${it.title}" }) { trailer ->
                val thumbUrl = "https://img.youtube.com/vi/${trailer.youtubeKey}/hqdefault.jpg"

                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.youtube.com/watch?v=${trailer.youtubeKey}")
                            )
                            context.startActivity(intent)
                        },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF141414))
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(thumbUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = trailer.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Dark scrim
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                        )

                        // Play Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.88f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = AmoledBlack,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Text(
                        text = trailer.title,
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
