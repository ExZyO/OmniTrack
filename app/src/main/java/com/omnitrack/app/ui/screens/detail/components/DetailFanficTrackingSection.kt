package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.db.entity.FanficChapterEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.FanficRating
import com.omnitrack.app.data.model.FanficSite
import com.omnitrack.app.data.model.FanficSourceLink
import com.omnitrack.app.data.model.FanficThreadmarkCategory
import com.omnitrack.app.data.model.FanficWorkStatus
import com.omnitrack.app.data.model.decodeFanficMeta
import com.omnitrack.app.data.model.formatWordCount
import com.omnitrack.app.ui.components.repeatingClickable
import com.omnitrack.app.ui.components.shouldBlurFanficCover
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * Tracker-first Detail screen vertical for Fanfiction works across AO3, FF.net,
 * SpaceBattles, Sufficient Velocity, and Questionable Questing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailFanficTrackingSection(
    item: MediaItemEntity,
    chapters: List<FanficChapterEntity>,
    isCheckingUpdates: Boolean,
    updateBannerMessage: String?,
    onCheckUpdates: () -> Unit,
    onLinkMirrorUrl: (String) -> Unit,
    onRemoveMirror: (FanficSourceLink) -> Unit,
    onToggleChapterRead: (FanficChapterEntity) -> Unit,
    onMarkReadUpTo: (FanficChapterEntity) -> Unit,
    onAddManualChapter: (Int, String, String, String?) -> Unit,
    onDeleteChapter: (Long) -> Unit,
    onStepProgress: (Double) -> Unit,
    onUpdateWorkStatus: (FanficWorkStatus) -> Unit,
    onOpenChapterInWebView: (url: String, site: FanficSite, chapter: FanficChapterEntity?) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onSearchFandomArt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val meta = remember(item.fanficMetaJson) { decodeFanficMeta(item.fanficMetaJson) }
    val primarySite = remember(item.fanficSourceSite) { FanficSite.fromCode(item.fanficSourceSite) }
    val rating = remember(item.fanficRating) { FanficRating.fromRaw(item.fanficRating) }
    val workStatus = remember(item.fanficWorkStatus) { FanficWorkStatus.fromRaw(item.fanficWorkStatus) }
    val blurSensitive = remember(item.fanficRating) { shouldBlurFanficCover(item.fanficRating) }

    var revealSensitiveTags by remember(item.id) { mutableStateOf(!blurSensitive) }
    var expandTags by remember(item.id) { mutableStateOf(false) }
    var expandAllFreeform by remember(item.id) { mutableStateOf(false) }
    var showAddMirrorDialog by remember { mutableStateOf(false) }
    var mirrorUrlInput by remember { mutableStateOf("") }
    var showAddChapterDialog by remember { mutableStateOf(false) }
    var manualChapNumber by remember(chapters.size) { mutableStateOf(((chapters.maxOfOrNull { it.chapterNumber } ?: 0) + 1).toString()) }
    var manualChapTitle by remember { mutableStateOf("") }
    var manualChapUrl by remember { mutableStateOf("") }
    var manualChapCategory by remember { mutableStateOf(FanficThreadmarkCategory.MAIN) }

    val availableCategories = remember(chapters) {
        val present = chapters.map { FanficThreadmarkCategory.fromRaw(it.category) }.toSet()
        listOf(FanficThreadmarkCategory.MAIN) +
            FanficThreadmarkCategory.entries.filter { it != FanficThreadmarkCategory.MAIN && it in present }
    }
    var selectedCategory by remember(availableCategories) { mutableStateOf(FanficThreadmarkCategory.MAIN) }
    val visibleChapters = remember(chapters, selectedCategory) {
        chapters.filter { FanficThreadmarkCategory.fromRaw(it.category) == selectedCategory }
    }

    val mirrorList = remember(item.fanficSourceUrl, item.fanficSourceSite, meta.mirrorSources) {
        val primary = item.fanficSourceUrl?.takeIf { it.isNotBlank() }?.let { u ->
            FanficSourceLink(
                site = item.fanficSourceSite ?: FanficSite.fromUrl(u).code,
                url = u,
                externalId = item.fanficWorkId,
                isPrimary = true
            )
        }
        buildList {
            if (primary != null) add(primary)
            addAll(meta.mirrorSources.filterNot { m ->
                primary != null && m.site == primary.site && m.url.equals(primary.url, ignoreCase = true)
            })
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Progress & Quick Work Stats Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurfaceCard)
                .border(0.8.dp, GlassBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(primarySite.accentHex))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(primarySite.shortBadge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    val ratingColor = when (rating) {
                        FanficRating.EXPLICIT -> Color(0xFFEF4444)
                        FanficRating.MATURE -> Color(0xFFF59E0B)
                        FanficRating.TEEN -> Color(0xFF38BDF8)
                        FanficRating.GENERAL -> Color(0xFF22C55E)
                        FanficRating.NOT_RATED -> TextSecondary
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ratingColor.copy(alpha = 0.18f))
                            .border(0.7.dp, ratingColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(rating.label, color = ratingColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (item.fanficIsCrossover) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentGold.copy(alpha = 0.16f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text("Crossover", color = AccentGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedButton(
                    onClick = onSearchFandomArt,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentGold)
                    Spacer(Modifier.width(5.dp))
                    Text("Fandom Art", fontSize = 11.sp, color = AccentGold, fontWeight = FontWeight.SemiBold)
                }
            }

            // Metrics bar (Words + AO3 stats)
            val metricParts = buildList {
                formatWordCount(item.fanficWordCount).takeIf { it.isNotBlank() }?.let { add(it) }
                meta.kudosCount?.takeIf { it > 0 }?.let { add("$it kudos") }
                meta.bookmarksCount?.takeIf { it > 0 }?.let { add("$it bookmarks") }
                meta.hitsCount?.takeIf { it > 0 }?.let { add("$it hits") }
                meta.language?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            if (metricParts.isNotEmpty()) {
                Text(
                    text = metricParts.joinToString(" • "),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Hold-to-repeat Chapter Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val cur = item.currentProgress.toInt()
                    val tot = item.totalProgress?.toInt()
                    Text(
                        text = if (tot != null && tot > 0) "Chapter $cur / $tot" else "Chapter $cur",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val pct = if (tot != null && tot > 0) ((cur.toFloat() / tot) * 100f).toInt().coerceIn(0, 100) else null
                    if (pct != null) {
                        Text("$pct% read", color = AccentGold, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated)
                            .border(0.8.dp, GlassBorder, CircleShape)
                            .repeatingClickable(onClick = { onStepProgress(-1.0) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "-1 Chapter", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentGold.copy(alpha = 0.2f))
                            .border(0.8.dp, AccentGold.copy(alpha = 0.6f), CircleShape)
                            .repeatingClickable(onClick = { onStepProgress(1.0) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "+1 Chapter", tint = AccentGold, modifier = Modifier.size(18.dp))
                    }
                }
            }

            val total = item.totalProgress?.toFloat()
            if (total != null && total > 0f) {
                LinearProgressIndicator(
                    progress = { (item.currentProgress.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = AccentGold,
                    trackColor = DarkSurfaceElevated
                )
            }

            // Work Publication Status Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FanficWorkStatus.entries.forEach { st ->
                    val sel = workStatus == st
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) AccentJade.copy(alpha = 0.22f) else DarkSurfaceElevated)
                            .border(
                                0.7.dp,
                                if (sel) AccentJade.copy(alpha = 0.6f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onUpdateWorkStatus(st) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = st.label,
                            color = if (sel) AccentJade else TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 2. Fandoms, Relationships, Characters, Warnings & Tags
        val hasTagData = meta.fandoms.isNotEmpty() || meta.warnings.isNotEmpty() ||
            meta.relationships.isNotEmpty() || meta.characters.isNotEmpty() || meta.freeformTags.isNotEmpty()
        if (hasTagData) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceCard)
                    .border(0.8.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandTags = !expandTags },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Fandoms & AO3 Tags", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        val fandomSummary = meta.fandoms.joinToString(", ").ifBlank { item.fanficPrimaryFandom.orEmpty() }
                        if (fandomSummary.isNotBlank()) {
                            Text(fandomSummary, color = AccentGold, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(
                        imageVector = if (expandTags) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }

                AnimatedVisibility(visible = expandTags || !blurSensitive) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!revealSensitiveTags) {
                            OutlinedButton(
                                onClick = { revealSensitiveTags = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp), tint = AccentGold)
                                Spacer(Modifier.width(6.dp))
                                Text("Reveal ${rating.label} Warnings & Tags", fontSize = 12.sp, color = AccentGold)
                            }
                        } else {
                            if (meta.warnings.isNotEmpty()) {
                                TagGroupRow("Warnings", meta.warnings, Color(0xFFEF4444))
                            }
                            if (meta.fandoms.isNotEmpty()) {
                                TagGroupRow("Fandoms", meta.fandoms, Color(0xFF60A5FA))
                            }
                            if (meta.relationships.isNotEmpty()) {
                                TagGroupRow("Relationships", meta.relationships, Color(0xFFF472B6))
                            }
                            if (meta.characters.isNotEmpty()) {
                                TagGroupRow("Characters", meta.characters, Color(0xFF34D399))
                            }
                            if (meta.freeformTags.isNotEmpty()) {
                                val visibleFreeform = if (expandAllFreeform) meta.freeformTags else meta.freeformTags.take(12)
                                TagGroupRow("Additional Tags", visibleFreeform, TextSecondary)
                                if (meta.freeformTags.size > 12) {
                                    Text(
                                        text = if (expandAllFreeform) "Show fewer tags" else "Show all ${meta.freeformTags.size} tags",
                                        color = AccentGold,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { expandAllFreeform = !expandAllFreeform }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Linked Sources & Mirrors (Update Checking + Open WebView/Browser)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurfaceCard)
                .border(0.8.dp, GlassBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sources & Update Check", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showAddMirrorDialog = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Mirror", fontSize = 11.5.sp)
                    }
                    Button(
                        enabled = !isCheckingUpdates && mirrorList.isNotEmpty(),
                        onClick = onCheckUpdates,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = Color.Black)
                    ) {
                        if (isCheckingUpdates) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.Black)
                            Spacer(Modifier.width(5.dp))
                            Text("Checking", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Check", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (!updateBannerMessage.isNullOrBlank()) {
                Text(
                    text = updateBannerMessage,
                    color = AccentJade,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (mirrorList.isEmpty()) {
                Text(
                    text = "No source URL linked yet — tap Mirror above to attach AO3, FF.net, SB, SV, or QQ.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            } else {
                mirrorList.forEach { link ->
                    val linkSite = FanficSite.fromCode(link.site)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceElevated)
                            .clickable { onOpenChapterInWebView(link.url, linkSite, null) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(linkSite.accentHex))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(linkSite.shortBadge, color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = link.url,
                                color = TextPrimary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sub = listOfNotNull(
                                if (link.isPrimary) "Primary" else "Mirror",
                                link.lastChapterCount?.let { "$it ch" },
                                link.lastUpdatedLabel?.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")
                            Text(sub, color = TextSecondary, fontSize = 10.5.sp)
                        }
                        IconButton(onClick = { onOpenExternalUrl(link.url) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open External", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                        if (!link.isPrimary) {
                            IconButton(onClick = { onRemoveMirror(link) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Mirror", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // 4. Chapters & XenForo Threadmark Checklist
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurfaceCard)
                .border(0.8.dp, GlassBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chapters & Threadmarks (${chapters.size})",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { showAddChapterDialog = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", fontSize = 11.5.sp)
                }
            }

            if (availableCategories.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableCategories.forEach { cat ->
                        val count = chapters.count { FanficThreadmarkCategory.fromRaw(it.category) == cat }
                        val sel = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) AccentGold.copy(alpha = 0.22f) else DarkSurfaceElevated)
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "${cat.label} ($count)",
                                color = if (sel) AccentGold else TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (visibleChapters.isEmpty()) {
                Text(
                    text = "No chapters synced yet. Tap Check above (or Add) to track chapters.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            } else {
                visibleChapters.forEachIndexed { idx, ch ->
                    val chSite = FanficSite.fromCode(ch.sourceSite)
                    val chUrl = ch.chapterUrl?.takeIf { it.isNotBlank() } ?: item.fanficSourceUrl
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (ch.isRead) DarkSurface else DarkSurfaceElevated.copy(alpha = 0.65f))
                            .clickable {
                                if (!chUrl.isNullOrBlank()) {
                                    onOpenChapterInWebView(chUrl, chSite, ch)
                                } else {
                                    onToggleChapterRead(ch)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (ch.isRead) AccentJade else Color.Transparent)
                                .border(1.2.dp, if (ch.isRead) AccentJade else TextMuted, CircleShape)
                                .clickable { onToggleChapterRead(ch) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (ch.isRead) {
                                Icon(Icons.Default.Check, contentDescription = "Read", tint = Color.Black, modifier = Modifier.size(14.dp))
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${ch.chapterNumber}. ${ch.title}",
                                color = if (ch.isRead) TextSecondary else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subParts = listOfNotNull(
                                formatWordCount(ch.wordCount).takeIf { it.isNotBlank() },
                                ch.publishedDate?.takeIf { it.isNotBlank() }
                            )
                            if (subParts.isNotEmpty()) {
                                Text(subParts.joinToString(" • "), color = TextMuted, fontSize = 10.5.sp)
                            }
                        }

                        if (!ch.isRead && selectedCategory == FanficThreadmarkCategory.MAIN) {
                            IconButton(
                                onClick = { onMarkReadUpTo(ch) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Mark read up to here",
                                    tint = AccentGold,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (!chUrl.isNullOrBlank()) {
                            IconButton(
                                onClick = { onOpenExternalUrl(chUrl) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = "Open Chapter in Browser",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { onDeleteChapter(ch.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    if (idx < visibleChapters.lastIndex) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.04f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    if (showAddMirrorDialog) {
        AlertDialog(
            onDismissRequest = { showAddMirrorDialog = false },
            containerColor = DarkSurfaceCard,
            title = { Text("Link Mirror or Source URL", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = mirrorUrlInput,
                    onValueChange = { mirrorUrlInput = it },
                    singleLine = true,
                    label = { Text("AO3 / FF.net / SB / SV / QQ URL") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGold,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (mirrorUrlInput.isNotBlank()) {
                            onLinkMirrorUrl(mirrorUrlInput.trim())
                            mirrorUrlInput = ""
                        }
                        showAddMirrorDialog = false
                    }
                ) {
                    Text("Link & Check", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMirrorDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showAddChapterDialog) {
        AlertDialog(
            onDismissRequest = { showAddChapterDialog = false },
            containerColor = DarkSurfaceCard,
            title = { Text("Add Chapter / Threadmark", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualChapNumber,
                        onValueChange = { manualChapNumber = it.filter { c -> c.isDigit() } },
                        label = { Text("Chapter Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualChapTitle,
                        onValueChange = { manualChapTitle = it },
                        label = { Text("Chapter / Threadmark Title") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualChapUrl,
                        onValueChange = { manualChapUrl = it },
                        label = { Text("Optional Direct URL") },
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FanficThreadmarkCategory.entries.forEach { cat ->
                            val sel = manualChapCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) AccentGold.copy(alpha = 0.22f) else DarkSurfaceElevated)
                                    .clickable { manualChapCategory = cat }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(cat.label, fontSize = 11.sp, color = if (sel) AccentGold else TextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val num = manualChapNumber.toIntOrNull() ?: ((chapters.maxOfOrNull { it.chapterNumber } ?: 0) + 1)
                        val title = manualChapTitle.trim().ifBlank { "Chapter $num" }
                        onAddManualChapter(num, title, manualChapCategory.name, manualChapUrl.trim().ifBlank { null })
                        manualChapTitle = ""
                        manualChapUrl = ""
                        showAddChapterDialog = false
                    }
                ) {
                    Text("Add", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddChapterDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroupRow(
    label: String,
    tags: List<String>,
    chipColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tags.forEach { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(chipColor.copy(alpha = 0.14f))
                        .border(0.6.dp, chipColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = tag,
                        color = if (chipColor == TextSecondary) TextPrimary else chipColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
