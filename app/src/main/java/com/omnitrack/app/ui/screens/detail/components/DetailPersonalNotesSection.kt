package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.components.repeatingClickable
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Compact borderless "Personal Notes & Log" section shown directly below Overview/Synopsis
 * on DetailsScreen and mirrored bidirectionally with the Tracking Studio & cloud trackers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailPersonalNotesSection(
    item: MediaItemEntity,
    onUpdatePersonalLog: (
        notes: String?,
        isFavorite: Boolean,
        startedAtMs: Long?,
        finishedAtMs: Long?,
        rewatchCount: Int
    ) -> Unit,
    onOpenTrackingStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val resolvedNote = remember(item.personalNotes, item.anilistNotes, item.malComments, item.bangumiComment, item.simklMemo) {
        item.personalNotes?.takeIf { it.isNotBlank() }
            ?: item.anilistNotes?.takeIf { it.isNotBlank() }
            ?: item.malComments?.takeIf { it.isNotBlank() }
            ?: item.bangumiComment?.takeIf { it.isNotBlank() }
            ?: item.simklMemo?.takeIf { it.isNotBlank() }
            ?: ""
    }
    var draftNote by remember(item.id, resolvedNote) { mutableStateOf(resolvedNote) }
    var isEditingNote by remember(item.id) { mutableStateOf(false) }
    var pickingDateTarget by remember { mutableStateOf<String?>(null) } // "START" or "FINISH"

    val hasLinkedTrackers = remember(item) {
        item.anilistId != null || item.malId != null || item.bangumiId != null ||
            item.traktId != null || item.simklId != null || !item.vndbId.isNullOrBlank() ||
            !item.hardcoverId.isNullOrBlank()
    }
    val rewatchLabel = when (item.mediaType) {
        MediaType.MANGA, MediaType.COMIC, MediaType.NOVEL, MediaType.BOOK -> "Rereads"
        MediaType.GAME, MediaType.VISUAL_NOVEL -> "Replays"
        else -> "Rewatches"
    }
    val isFav = item.isFavorite || item.anilistFavorite

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Personal Notes & Log",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (hasLinkedTrackers) {
                Text(
                    text = "Mirrored to Trackers",
                    color = AccentGold.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onOpenTrackingStudio)
                )
            }
        }

        // Balanced 50/50 Split Option-A Action Row (Favorite & Add/Edit Note)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isFav) AccentGold.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f)
                    )
                    .clickable {
                        onUpdatePersonalLog(
                            draftNote.trim().takeIf { it.isNotEmpty() },
                            !isFav,
                            item.startedAtMs,
                            item.finishedAtMs,
                            item.rewatchCount
                        )
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFav) AccentGold else TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = if (isFav) "Favorited" else "Favorite",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFav) AccentGold else TextPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isEditingNote) AccentGold.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f)
                    )
                    .clickable { isEditingNote = !isEditingNote }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Note",
                    tint = if (isEditingNote) AccentGold else TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (isEditingNote) "Done" else if (draftNote.isBlank()) "Add Note" else "Edit Note",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEditingNote) AccentGold else TextPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Compact 3-Column Dates & Rewatches Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { pickingDateTarget = "START" }
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("STARTED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 0.7.sp)
                Text(
                    text = formatLogDate(item.startedAtMs),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.startedAtMs != null) TextPrimary else TextSecondary.copy(alpha = 0.65f)
                )
            }

            Box(modifier = Modifier.width(1.dp).padding(vertical = 6.dp).background(Color.White.copy(alpha = 0.08f)))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { pickingDateTarget = "FINISH" }
                    .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("FINISHED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 0.7.sp)
                Text(
                    text = formatLogDate(item.finishedAtMs),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.finishedAtMs != null) TextPrimary else TextSecondary.copy(alpha = 0.65f)
                )
            }

            Column(
                modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(rewatchLabel.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 0.7.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "-",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .repeatingClickable {
                                val next = (item.rewatchCount - 1).coerceAtLeast(0)
                                onUpdatePersonalLog(
                                    draftNote.trim().takeIf { it.isNotEmpty() },
                                    isFav,
                                    item.startedAtMs,
                                    item.finishedAtMs,
                                    next
                                )
                            }
                            .padding(horizontal = 6.dp)
                    )
                    Text(
                        text = "${item.rewatchCount}",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.rewatchCount > 0) AccentGold else TextPrimary
                    )
                    Text(
                        text = "+",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .repeatingClickable {
                                val next = item.rewatchCount + 1
                                onUpdatePersonalLog(
                                    draftNote.trim().takeIf { it.isNotEmpty() },
                                    isFav,
                                    item.startedAtMs,
                                    item.finishedAtMs,
                                    next
                                )
                            }
                            .padding(horizontal = 6.dp)
                    )
                }
            }
        }

        // Inline Note Readout or Editor (Frameless)
        AnimatedVisibility(visible = isEditingNote) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = draftNote,
                        onValueChange = { draftNote = it },
                        textStyle = TextStyle(color = TextPrimary, fontSize = 13.5.sp, lineHeight = 20.sp),
                        cursorBrush = SolidColor(AccentGold),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            onUpdatePersonalLog(
                                draftNote.trim().takeIf { it.isNotEmpty() },
                                isFav,
                                item.startedAtMs,
                                item.finishedAtMs,
                                item.rewatchCount
                            )
                            isEditingNote = false
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (draftNote.isEmpty()) {
                        Text(
                            text = "Write a personal note or thought (syncs cleanly to your trackers)...",
                            fontSize = 13.sp,
                            color = TextSecondary.copy(alpha = 0.6f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${draftNote.length} chars",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Cancel",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier
                                .clickable {
                                    draftNote = resolvedNote
                                    isEditingNote = false
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Text(
                            text = "Save & Sync",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentGold.copy(alpha = 0.16f))
                                .clickable {
                                    focusManager.clearFocus()
                                    onUpdatePersonalLog(
                                        draftNote.trim().takeIf { it.isNotEmpty() },
                                        isFav,
                                        item.startedAtMs,
                                        item.finishedAtMs,
                                        item.rewatchCount
                                    )
                                    isEditingNote = false
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        if (!isEditingNote && draftNote.isNotBlank()) {
            Text(
                text = draftNote,
                color = TextSecondary,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isEditingNote = true }
                    .padding(vertical = 2.dp)
            )
        } else if (!isEditingNote) {
            Text(
                text = "Tap to add a personal note, started/finished date, or favorite...",
                color = TextSecondary.copy(alpha = 0.6f),
                fontSize = 12.5.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isEditingNote = true }
                    .padding(vertical = 2.dp)
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
    }

    if (pickingDateTarget != null) {
        val initialMs = if (pickingDateTarget == "START") item.startedAtMs else item.finishedAtMs
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMs ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { pickingDateTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = pickerState.selectedDateMillis
                    if (pickingDateTarget == "START") {
                        onUpdatePersonalLog(draftNote.trim().takeIf { it.isNotEmpty() }, isFav, chosen, item.finishedAtMs, item.rewatchCount)
                    } else {
                        onUpdatePersonalLog(draftNote.trim().takeIf { it.isNotEmpty() }, isFav, item.startedAtMs, chosen, item.rewatchCount)
                    }
                    pickingDateTarget = null
                }) {
                    Text("Set Date", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (pickingDateTarget == "START") {
                        onUpdatePersonalLog(draftNote.trim().takeIf { it.isNotEmpty() }, isFav, null, item.finishedAtMs, item.rewatchCount)
                    } else {
                        onUpdatePersonalLog(draftNote.trim().takeIf { it.isNotEmpty() }, isFav, item.startedAtMs, null, item.rewatchCount)
                    }
                    pickingDateTarget = null
                }) {
                    Text("Clear", color = TextSecondary)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun formatLogDate(ms: Long?): String {
    if (ms == null || ms <= 0L) return "Not set"
    return runCatching {
        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
        d.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
    }.getOrDefault("Not set")
}
