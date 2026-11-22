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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.db.entity.ComicIssueEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.ComicDetailMeta
import com.omnitrack.app.data.model.ComicEntryType
import com.omnitrack.app.data.model.ComicFormat
import com.omnitrack.app.data.model.ComicTrackingMode
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

@Composable
fun DetailComicTrackingSection(
    item: MediaItemEntity,
    comicDetailMeta: ComicDetailMeta,
    issues: List<ComicIssueEntity>,
    onStepProgress: (Int) -> Unit,
    onSetProgressExact: (Double, Double?) -> Unit,
    onSwitchMode: (ComicTrackingMode) -> Unit,
    onUpdateFormat: (ComicFormat) -> Unit,
    onToggleIssueRead: (ComicIssueEntity) -> Unit,
    onAddCustomIssue: (String, String?, ComicEntryType, String?) -> Unit,
    onDeleteIssue: (Long) -> Unit,
    onSeedIssues: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeFormat = remember(item.comicFormat) { ComicFormat.fromName(item.comicFormat) }
    val activeMode = remember(item.comicTrackingMode) { ComicTrackingMode.fromName(item.comicTrackingMode) }
    val unitSingular = when (activeMode) {
        ComicTrackingMode.TPB_VOLUMES -> "Vol"
        ComicTrackingMode.COLLECTED -> "Edition"
        ComicTrackingMode.ISSUES -> "Issue"
    }

    var showExactDialog by remember { mutableStateOf(false) }
    var showAddIssueDialog by remember { mutableStateOf(false) }

    val cur = item.currentProgress.toInt().coerceAtLeast(0)
    val tot = (item.totalProgress?.toInt() ?: comicDetailMeta.issueCount)?.takeIf { it > 0 }
    val fraction = if (tot != null && tot > 0) (cur.toFloat() / tot.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Issues & Format Tracking",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        // Format Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ComicFormat.entries.forEach { fmt ->
                val selected = activeFormat == fmt
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) AccentGold else DarkSurfaceElevated)
                        .border(1.dp, if (selected) AccentGold else GlassBorder, RoundedCornerShape(10.dp))
                        .clickable { onUpdateFormat(fmt) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = fmt.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) AmoledBlack else TextSecondary
                    )
                }
            }
        }

        // Tracking Mode Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ComicTrackingMode.entries.forEach { mode ->
                val selected = activeMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) AccentGold else DarkSurface)
                        .clickable { onSwitchMode(mode) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) AmoledBlack else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Progress Stepper Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (tot != null) "$unitSingular $cur / $tot" else "$unitSingular $cur",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (!comicDetailMeta.publisher.isNullOrBlank() || !item.comicPublisher.isNullOrBlank()) {
                        Text(
                            text = comicDetailMeta.publisher ?: item.comicPublisher.orEmpty(),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
                Text(
                    text = "Edit Exact",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentGold.copy(alpha = 0.14f))
                        .clickable { showExactDialog = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(DarkSurfaceCard)
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(5.dp)
                            .background(AccentGold)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(-1 to "-1", 1 to "+1 $unitSingular", 5 to "+5").forEach { (delta, label) ->
                    val isPrimary = delta == 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPrimary) AccentGold else DarkSurfaceElevated)
                            .border(1.dp, if (isPrimary) AccentGold else GlassBorder, RoundedCornerShape(10.dp))
                            .clickable { onStepProgress(delta) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPrimary) AmoledBlack else TextPrimary
                        )
                    }
                }
            }
        }

        // Issue / Volume Checklist Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Issue Checklist (${issues.count { it.isRead }}/${issues.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (issues.isEmpty() && (tot ?: 0) > 0) {
                    Text(
                        text = "Seed 1..${(tot ?: 12).coerceAtMost(50)}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                            .clickable { onSeedIssues((tot ?: 12).coerceAtMost(50)) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                        .clickable { showAddIssueDialog = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Add Issue",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold
                    )
                }
            }
        }

        if (issues.isEmpty()) {
            Text(
                text = "Tap + Add Issue or connect a Comic Vine / Metron key to auto-populate full issue runs & store release dates.",
                fontSize = 12.5.sp,
                color = TextSecondary
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            ) {
                issues.forEachIndexed { idx, issue ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleIssueRead(issue) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!issue.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = issue.coverUrl,
                                contentDescription = "#${issue.issueNumber}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(36.dp)
                                    .aspectRatio(0.68f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceCard)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "#${issue.issueNumber}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGold
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = buildString {
                                    append("#${issue.issueNumber}")
                                    if (!issue.issueTitle.isNullOrBlank()) append(" · ${issue.issueTitle}")
                                },
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sub = listOfNotNull(
                                issue.storyArc?.takeIf { it.isNotBlank() },
                                issue.storeDate?.takeIf { it.isNotBlank() } ?: issue.coverDate?.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) {
                                Text(
                                    text = sub,
                                    fontSize = 11.5.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = { onDeleteIssue(issue.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (issue.isRead) AccentGold else DarkSurfaceElevated)
                                .border(1.dp, if (issue.isRead) AccentGold else GlassBorder, CircleShape)
                                .clickable { onToggleIssueRead(issue) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (issue.isRead) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Read",
                                    tint = AmoledBlack,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    if (idx < issues.lastIndex) {
                        HorizontalDivider(color = GlassBorder, thickness = 1.dp)
                    }
                }
            }
        }
    }

    if (showExactDialog) {
        var curText by remember(cur) { mutableStateOf(cur.toString()) }
        var totText by remember(tot) { mutableStateOf(tot?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { showExactDialog = false },
            containerColor = DarkSurface,
            title = { Text("Update Progress", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = curText,
                        onValueChange = { curText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Current $unitSingular", color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = totText,
                        onValueChange = { totText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Total $unitSingular (Optional)", color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nextCur = curText.toDoubleOrNull() ?: cur.toDouble()
                        val nextTot = totText.toDoubleOrNull()
                        onSetProgressExact(nextCur, nextTot)
                        showExactDialog = false
                    }
                ) {
                    Text("Save", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExactDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showAddIssueDialog) {
        var num by remember { mutableStateOf((issues.size + 1).toString()) }
        var title by remember { mutableStateOf("") }
        var arc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddIssueDialog = false },
            containerColor = DarkSurface,
            title = { Text("Add Issue / Volume", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = num,
                        onValueChange = { num = it },
                        label = { Text("Issue / Vol #", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Issue Title (Optional)", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = arc,
                        onValueChange = { arc = it },
                        label = { Text("Story Arc (Optional)", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (num.isNotBlank()) {
                            onAddCustomIssue(
                                num.trim(),
                                title.trim().ifBlank { null },
                                if (activeMode == ComicTrackingMode.TPB_VOLUMES) ComicEntryType.TPB else ComicEntryType.ISSUE,
                                arc.trim().ifBlank { null }
                            )
                        }
                        showAddIssueDialog = false
                    }
                ) {
                    Text("Add", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddIssueDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
