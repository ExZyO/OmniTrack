package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.omnitrack.app.data.preferences.EditionsSwitcherStyle
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CrossMediumEditionsSection(
    currentItem: MediaItemEntity,
    editions: List<MediaItemEntity>,
    isPreviewMode: Boolean,
    onOpenEdition: (MediaItemEntity) -> Unit,
    onOpenLinkPicker: () -> Unit,
    onUnlinkEdition: (MediaItemEntity) -> Unit,
    switcherStyle: EditionsSwitcherStyle = EditionsSwitcherStyle.SMART_LABELS,
    customLabels: Map<Long, String> = emptyMap(),
    onSaveCustomLabel: (MediaItemEntity, String?) -> Unit = { _, _ -> }
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

    var editingLabelItem by remember { mutableStateOf<MediaItemEntity?>(null) }

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

        when (switcherStyle) {
            EditionsSwitcherStyle.SMART_LABELS -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    sortedEditions.forEach { edition ->
                        val isSelected = edition.id == currentItem.id
                        val (primaryLabel, mediumSub) = resolveEditionSmartLabel(edition, sortedEditions, customLabels)
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .combinedClickable(
                                    onClick = { if (!isSelected) onOpenEdition(edition) },
                                    onLongClick = { editingLabelItem = edition }
                                )
                                .padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = primaryLabel,
                                color = if (isSelected) TextPrimary else TextMuted,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 148.dp)
                            )
                            if (mediumSub != null) {
                                Text(
                                    text = mediumSub,
                                    color = if (isSelected) AccentGold.copy(alpha = 0.9f) else TextMuted.copy(alpha = 0.7f),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(if (isSelected) AccentGold else Color.Transparent)
                            )
                        }
                    }

                    LinkEditionButton(onClick = onOpenLinkPicker)
                }
            }

            EditionsSwitcherStyle.THUMBNAIL_CARDS -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sortedEditions.forEach { edition ->
                        val isSelected = edition.id == currentItem.id
                        val (primaryLabel, mediumSub) = resolveEditionSmartLabel(edition, sortedEditions, customLabels)
                        Column(
                            modifier = Modifier
                                .width(78.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AccentGold.copy(alpha = 0.10f) else DarkSurfaceElevated.copy(alpha = 0.65f))
                                .border(
                                    width = if (isSelected) 1.4.dp else 0.7.dp,
                                    color = if (isSelected) AccentGold else GlassBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .combinedClickable(
                                    onClick = { if (!isSelected) onOpenEdition(edition) },
                                    onLongClick = { editingLabelItem = edition }
                                )
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AsyncImage(
                                model = edition.customCoverPath ?: edition.coverUrl,
                                contentDescription = edition.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(AmoledBlack)
                            )
                            Text(
                                text = primaryLabel,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = mediumSub ?: edition.mediaType.displayName,
                                color = if (isSelected) AccentGold else TextMuted,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(68.dp)
                            .height(136.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(0.7.dp, GlassBorder, RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenLinkPicker),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Link another medium edition",
                                tint = AccentGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Link",
                                color = AccentGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            EditionsSwitcherStyle.GROUPED_DROPDOWN -> {
                val groupedByMedia = remember(sortedEditions, currentItem.mediaType) {
                    val linkedGroups = sortedEditions.groupBy { it.mediaType }
                    linkedGroups.entries.sortedWith(
                        compareByDescending<Map.Entry<MediaType, List<MediaItemEntity>>> { it.key == currentItem.mediaType }
                            .thenBy { it.key.displayName }
                    )
                }
                var expandedMediaGroup by remember { mutableStateOf<MediaType?>(null) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    groupedByMedia.forEach { (mediaType, groupItems) ->
                        val isGroupSelected = mediaType == currentItem.mediaType
                        if (groupItems.size == 1) {
                            val single = groupItems.first()
                            val label = customLabels[single.id]?.takeIf { it.isNotBlank() } ?: mediaType.displayName
                            Column(
                                modifier = Modifier
                                    .width(IntrinsicSize.Max)
                                    .combinedClickable(
                                        onClick = { if (single.id != currentItem.id) onOpenEdition(single) },
                                        onLongClick = { editingLabelItem = single }
                                    )
                                    .padding(top = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = label,
                                    color = if (isGroupSelected) TextPrimary else TextMuted,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isGroupSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(if (isGroupSelected) AccentGold else Color.Transparent)
                                )
                            }
                        } else {
                            Box {
                                Column(
                                    modifier = Modifier
                                        .width(IntrinsicSize.Max)
                                        .clickable { expandedMediaGroup = mediaType }
                                        .padding(top = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "${mediaType.displayName} (${groupItems.size})",
                                            color = if (isGroupSelected) TextPrimary else TextMuted,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (isGroupSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = if (isGroupSelected) AccentGold else TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(if (isGroupSelected) AccentGold else Color.Transparent)
                                    )
                                }

                                DropdownMenu(
                                    expanded = expandedMediaGroup == mediaType,
                                    onDismissRequest = { expandedMediaGroup = null },
                                    containerColor = DarkSurfaceElevated
                                ) {
                                    groupItems.forEach { edition ->
                                        val isActive = edition.id == currentItem.id
                                        val (labelText, _) = resolveEditionSmartLabel(edition, sortedEditions, customLabels)
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier.widthIn(min = 180.dp, max = 260.dp)
                                                ) {
                                                    AsyncImage(
                                                        model = edition.customCoverPath ?: edition.coverUrl,
                                                        contentDescription = edition.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .width(28.dp)
                                                            .aspectRatio(2f / 3f)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(AmoledBlack)
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = labelText,
                                                            color = if (isActive) AccentGold else TextPrimary,
                                                            fontSize = 13.sp,
                                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = listOfNotNull(
                                                                edition.releaseYear?.takeIf { it.isNotBlank() },
                                                                edition.status.displayName
                                                            ).joinToString(" • "),
                                                            color = TextMuted,
                                                            fontSize = 10.5.sp,
                                                            maxLines = 1
                                                        )
                                                    }
                                                    if (isActive) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = AccentGold,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                expandedMediaGroup = null
                                                if (!isActive) onOpenEdition(edition)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LinkEditionButton(onClick = onOpenLinkPicker)
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
    }

    editingLabelItem?.let { target ->
        var labelInput by remember(target.id) { mutableStateOf(customLabels[target.id].orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingLabelItem = null },
            containerColor = DarkSurfaceElevated,
            title = {
                Text(
                    text = "Custom Edition Label",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = target.title,
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        singleLine = true,
                        placeholder = { Text("e.g. Season 1, Spinoff, OVA", color = TextMuted, fontSize = 13.sp) },
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveCustomLabel(target, labelInput.trim().takeIf { it.isNotBlank() })
                        editingLabelItem = null
                    }
                ) {
                    Text("Save", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onSaveCustomLabel(target, null)
                        editingLabelItem = null
                    }
                ) {
                    Text("Reset", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun LinkEditionButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(bottom = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(0.7.dp, GlassBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
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

private fun resolveEditionSmartLabel(
    edition: MediaItemEntity,
    allEditions: List<MediaItemEntity>,
    customLabels: Map<Long, String>
): Pair<String, String?> {
    val custom = customLabels[edition.id]?.trim()?.takeIf { it.isNotBlank() }
    val distinctMediaCount = allEditions.map { it.mediaType }.toSet().size
    val sameMediumCount = allEditions.count { it.mediaType == edition.mediaType }
    if (custom != null) {
        val sub = if (distinctMediaCount > 1) edition.mediaType.displayName else null
        return custom to sub
    }
    if (sameMediumCount <= 1) {
        return edition.mediaType.displayName to null
    }
    val sub = if (distinctMediaCount > 1) edition.mediaType.displayName else null
    return edition.title.trim() to sub
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
