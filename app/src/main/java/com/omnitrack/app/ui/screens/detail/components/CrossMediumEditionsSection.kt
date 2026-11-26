package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

@Composable
fun CrossMediumEditionsSection(
    currentItem: MediaItemEntity,
    editions: List<MediaItemEntity>,
    isPreviewMode: Boolean,
    onOpenEdition: (MediaItemEntity) -> Unit,
    onOpenLinkPicker: () -> Unit,
    onUnlinkEdition: (MediaItemEntity) -> Unit
) {
    if (isPreviewMode || currentItem.id <= 0L) return

    val sortedEditions = remember(currentItem.id, editions) {
        val base = editions.ifEmpty { listOf(currentItem) }
        base.distinctBy { it.id }.sortedWith(
            compareByDescending<MediaItemEntity> { it.id == currentItem.id }
                .thenBy { it.mediaType.displayName }
                .thenBy { it.title }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EDITIONS & ADAPTATIONS",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            if (sortedEditions.size > 1) {
                Text(
                    text = "Unlink this edition",
                    color = Color(0xFFEF4444).copy(alpha = 0.85f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onUnlinkEdition(currentItem) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Option A underline tab switcher across linked mediums + [+ Link]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            sortedEditions.forEach { edition ->
                val isSelected = edition.id == currentItem.id
                val mediumLabel = edition.mediaType.displayName
                Column(
                    modifier = Modifier
                        .clickable {
                            if (!isSelected) onOpenEdition(edition)
                        }
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = mediumLabel,
                        color = if (isSelected) TextPrimary else TextMuted,
                        fontSize = 13.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .height(2.5.dp)
                            .background(if (isSelected) AccentGold else Color.Transparent)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(0.7.dp, GlassBorder, RoundedCornerShape(6.dp))
                    .clickable { onOpenLinkPicker() }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Link another medium edition",
                    tint = AccentGold,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Link",
                    color = AccentGold,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkCrossMediumEditionSheet(
    currentItem: MediaItemEntity,
    allLibraryItems: List<MediaItemEntity>,
    currentlyLinked: List<MediaItemEntity>,
    onLinkTarget: (MediaItemEntity) -> Unit,
    onUnlinkTarget: (MediaItemEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val linkedIds = remember(currentlyLinked) { currentlyLinked.map { it.id }.toSet() }

    val candidates = remember(allLibraryItems, currentItem.id, query, linkedIds) {
        val q = query.trim().lowercase()
        val raw = allLibraryItems.filter { it.id != currentItem.id }
        val filtered = if (q.isBlank()) {
            val baseWord = currentItem.title.split(" ").firstOrNull()?.lowercase().orEmpty()
            raw.sortedWith(
                compareByDescending<MediaItemEntity> { it.id in linkedIds }
                    .thenByDescending { baseWord.length >= 3 && it.title.lowercase().contains(baseWord) }
                    .thenBy { it.title }
            )
        } else {
            raw.filter {
                it.title.lowercase().contains(q) ||
                    it.mediaType.displayName.lowercase().contains(q) ||
                    (it.bookAuthor ?: "").lowercase().contains(q)
            }
        }
        filtered.take(60)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LINK CROSS-MEDIUM EDITIONS",
                        color = AccentGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Combine ${currentItem.title} with its other formats",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text("Search your library (Manga, Anime, Novel, Game...)", color = TextMuted, fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGold,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = Color(0xFF0A0A0A),
                    unfocusedContainerColor = Color(0xFF0A0A0A)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                items(candidates, key = { it.id }) { candidate ->
                    val isLinked = candidate.id in linkedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isLinked) onUnlinkTarget(candidate) else onLinkTarget(candidate)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = candidate.customCoverPath ?: candidate.coverUrl,
                            contentDescription = candidate.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(38.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceElevated)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = candidate.title,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${candidate.mediaType.displayName} • ${candidate.status.displayName}",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        if (isLinked) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LinkOff,
                                    contentDescription = "Unlink",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("Unlink", color = Color(0xFFEF4444), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(0.7.dp, AccentGold.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = AccentGold, modifier = Modifier.size(14.dp))
                                Text("Link", color = AccentGold, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                }
            }
        }
    }
}
