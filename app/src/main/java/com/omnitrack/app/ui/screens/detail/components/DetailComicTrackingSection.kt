package com.omnitrack.app.ui.screens.detail.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.OpenInBrowser
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Frameless Book-style Comics Tracking & Issue Checklist Section.
 * Zero boxy cards or opaque colored borders — crisp underline tabs, large numeric readout,
 * slim 3.dp bar, hairline spec strip, translucent actions, and hairline issue rows.
 */
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
    val context = LocalContext.current
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
    val pct = if (tot != null && tot > 0) ((cur.toDouble() / tot) * 100.0).roundToInt().coerceIn(0, 100) else null

    val publisherText = comicDetailMeta.publisher ?: item.comicPublisher ?: "—"
    val startYearText = comicDetailMeta.startYear?.takeIf { it.isNotBlank() }
        ?: item.releaseDate?.take(4)?.takeIf { it.isNotBlank() }
        ?: "—"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Borderless Mode Switcher (Issues | TPB / Volumes | Omnibus)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ComicTrackingMode.entries.forEach { mode ->
                val selected = activeMode == mode
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onSwitchMode(mode) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = mode.label,
                        color = if (selected) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) AccentGold else Color.Transparent)
                    )
                }
            }
        }

        // 2. Hero Numeric Readout & Typographic Steppers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showExactDialog = true }
            ) {
                val mainReadout = buildString {
                    append("$cur")
                    if (tot != null) append(" / $tot")
                    append(" ${unitSingular}s")
                    if (pct != null) append(" ($pct%)")
                }
                Text(
                    text = mainReadout,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap to set exact progress",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(-1 to "−1", 1 to "+1", 5 to "+5").forEachIndexed { idx, (delta, label) ->
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            delta < 0 -> Color.White.copy(alpha = 0.72f)
                            idx == 1 -> AccentGold
                            else -> Color.White
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onStepProgress(delta) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 3. Slim 3.dp Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.14f))
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(AccentGold)
                )
            }
        }

        // 4. 3-Column Hairline Spec Strip (Publisher / Format / Era)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PUBLISHER",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = publisherText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.10f))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = "FORMAT",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = activeFormat.label,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.10f))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = if (tot != null) "TOTAL" else "LAUNCHED",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (tot != null) "$tot $unitSingular" else startYearText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 5. Edition Format Underline Selector (Single Issue | TPB | Hardcover | Omnibus | Webcomic)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ComicFormat.entries.forEach { fmt ->
                val selected = activeFormat == fmt
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onUpdateFormat(fmt) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = fmt.label,
                        color = if (selected) Color.White else TextSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) AccentGold else Color.Transparent)
                    )
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

        // 6. Issue / Volume Checklist Header (Frameless + League of Comic Geeks link)
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            runCatching {
                                val url = "https://leagueofcomicgeeks.com/search?keyword=${Uri.encode(item.title)}"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "LoCG",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                if (issues.isEmpty() && (tot ?: 0) > 0) {
                    Text(
                        text = "Seed 1..${(tot ?: 12).coerceAtMost(50)}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AccentGold.copy(alpha = 0.14f))
                            .clickable { onSeedIssues((tot ?: 12).coerceAtMost(50)) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { showAddIssueDialog = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Add",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold
                    )
                }
            }
        }

        if (issues.isEmpty()) {
            Text(
                text = "Tap + Add or Seed to start tracking issues, or connect Comic Vine / Metron for automatic run metadata.",
                fontSize = 12.5.sp,
                color = TextSecondary
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                issues.forEachIndexed { idx, issue ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleIssueRead(issue) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!issue.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = issue.coverUrl,
                                contentDescription = "#${issue.issueNumber}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(34.dp)
                                    .aspectRatio(0.68f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .width(34.dp)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.07f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "#${issue.issueNumber}",
                                    fontSize = 10.5.sp,
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
                                color = if (issue.isRead) TextSecondary else TextPrimary,
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
                                tint = TextSecondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(if (issue.isRead) AccentGold else Color.White.copy(alpha = 0.10f))
                                .clickable { onToggleIssueRead(issue) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (issue.isRead) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Read",
                                    tint = AmoledBlack,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                    if (idx < issues.lastIndex) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
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
            containerColor = AmoledBlack,
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
                            unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
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
                            unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
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
            containerColor = AmoledBlack,
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
                            unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
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
                            unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
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
                            unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
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
