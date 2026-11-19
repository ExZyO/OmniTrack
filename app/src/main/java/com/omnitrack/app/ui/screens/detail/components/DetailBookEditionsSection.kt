package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.omnitrack.app.data.model.BookEditionItem
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade

@Composable
fun DetailBookEditionsSection(
    editions: List<BookEditionItem>,
    activeEditionLabel: String?,
    activeIsbn: String?,
    onAdoptEdition: (BookEditionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (editions.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "EDITIONS & TRANSLATIONS (${editions.size})",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.60f),
            letterSpacing = 1.1.sp
        )

        editions.forEachIndexed { idx, ed ->
            val isAdopted = (!ed.isbn13.isNullOrBlank() && ed.isbn13 == activeIsbn) ||
                (!activeEditionLabel.isNullOrBlank() && ed.title.equals(activeEditionLabel, ignoreCase = true))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAdoptEdition(ed) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(62.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!ed.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ed.coverUrl,
                            contentDescription = ed.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Text(
                            text = ed.title.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = ed.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                    val metaLine = listOfNotNull(
                        ed.publisher?.takeIf { it.isNotBlank() },
                        ed.publishYear?.takeIf { it.isNotBlank() },
                        ed.format?.takeIf { it.isNotBlank() },
                        ed.pageCount?.takeIf { it > 0 }?.let { "$it pg" }
                    ).joinToString(" · ")
                    if (metaLine.isNotBlank()) {
                        Text(
                            text = metaLine,
                            color = Color.White.copy(alpha = 0.56f),
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                    if (!ed.translator.isNullOrBlank() || !ed.isbn13.isNullOrBlank()) {
                        Text(
                            text = listOfNotNull(
                                ed.translator?.takeIf { it.isNotBlank() },
                                ed.isbn13?.let { "ISBN $it" }
                            ).joinToString(" · "),
                            color = Color.White.copy(alpha = 0.42f),
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }
                }

                if (isAdopted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AccentJade, modifier = Modifier.size(14.dp))
                        Text("Active", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentJade)
                    }
                } else {
                    Text(
                        text = "Use Edition",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold
                    )
                }
            }
            if (idx < editions.lastIndex) {
                HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.07f))
            }
        }
    }
}
