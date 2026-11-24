package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.omnitrack.app.ui.components.MultiSelectBottomActionDock
import com.omnitrack.app.ui.components.MultiSelectCheckBadge
import com.omnitrack.app.ui.components.MultiSelectTopBar
import com.omnitrack.app.ui.components.shouldBlurFanficTags
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Option A — Frameless Nuvio Editorial Fanfiction Tracking Section.
 * Pure tracker-first UI matching Books & Comics (zero boxed cards, zero opaque card borders,
 * cumulative chapter checkmarks, external browser by default, word-count reading time estimate,
 * chapter filtering/sorting/jumping/bookmarks/notes, and clickable tags).
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
    onMarkAllChaptersRead: (Boolean) -> Unit = {},
    onToggleChapterBookmark: (FanficChapterEntity) -> Unit = {},
    onUpdateChapterNote: (FanficChapterEntity, String?, Float?) -> Unit = { _, _, _ -> },
    onUpdateQuickMetadata: (
        currentCh: Double,
        totalCh: Double?,
        wordCount: Int?,
        author: String?,
        primaryFandom: String?,
        ratingCode: String,
        workStatus: FanficWorkStatus
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onTagClick: (String) -> Unit = {},
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
    val blurSensitive = remember(item.fanficRating) { shouldBlurFanficTags(item.fanficRating) }

    var revealSensitiveTags by remember(item.id, blurSensitive) { mutableStateOf(!blurSensitive) }
    var expandAllFreeform by remember(item.id) { mutableStateOf(false) }

    // Dialog states
    var showAddMirrorDialog by remember { mutableStateOf(false) }
    var mirrorUrlInput by remember { mutableStateOf("") }
    var showAddChapterDialog by remember { mutableStateOf(false) }
    var manualChapNumber by remember(chapters.size) {
        mutableStateOf(((chapters.maxOfOrNull { it.chapterNumber } ?: 0) + 1).toString())
    }
    var manualChapTitle by remember { mutableStateOf("") }
    var manualChapUrl by remember { mutableStateOf("") }
    var manualChapCategory by remember { mutableStateOf(FanficThreadmarkCategory.MAIN) }

    var showMetadataEditorDialog by remember { mutableStateOf(false) }
    var showJumpChapterDialog by remember { mutableStateOf(false) }
    var jumpChapterInput by remember { mutableStateOf("") }
    var editingNoteChapter by remember { mutableStateOf<FanficChapterEntity?>(null) }
    var isChapterMultiSelect by remember { mutableStateOf(false) }
    var selectedChapterIds by remember { mutableStateOf(setOf<Long>()) }

    // Chapter filter & sort states
    var chapterFilterMode by remember { mutableStateOf("ALL") } // "ALL", "UNREAD", "BOOKMARKED"
    var sortNewestFirst by remember { mutableStateOf(false) }
    var highlightedChapterNumber by remember { mutableIntStateOf(-1) }

    // Dynamic underline tabs: Main Chapters + any present XenForo categories + Sources
    val availableCategories = remember(chapters) {
        val present = chapters.map { FanficThreadmarkCategory.fromRaw(it.category) }.toSet()
        listOf(FanficThreadmarkCategory.MAIN) +
            FanficThreadmarkCategory.entries.filter { it != FanficThreadmarkCategory.MAIN && it in present }
    }
    // activeTabIndex: 0 until availableCategories.size -> category tab; availableCategories.size -> Sources tab
    var activeTabIndex by remember(availableCategories.size) { mutableIntStateOf(0) }
    val isSourcesTab = activeTabIndex >= availableCategories.size
    val selectedCategory = availableCategories.getOrElse(activeTabIndex) { FanficThreadmarkCategory.MAIN }

    val categoryChapters = remember(chapters, selectedCategory) {
        chapters.filter { FanficThreadmarkCategory.fromRaw(it.category) == selectedCategory }
    }
    val filteredChapters = remember(categoryChapters, chapterFilterMode, sortNewestFirst) {
        val base = when (chapterFilterMode) {
            "UNREAD" -> categoryChapters.filter { !it.isRead }
            "BOOKMARKED" -> categoryChapters.filter { it.isBookmarked || !it.userNote.isNullOrBlank() }
            else -> categoryChapters
        }
        if (sortNewestFirst) {
            base.sortedWith(compareByDescending<FanficChapterEntity> { it.sortOrder }.thenByDescending { it.chapterNumber })
        } else {
            base.sortedWith(compareBy<FanficChapterEntity> { it.sortOrder }.thenBy { it.chapterNumber })
        }
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

    // Word-count progress & reading time calculation (~250 wpm)
    val mainChapters = remember(chapters) {
        chapters.filter { FanficThreadmarkCategory.fromRaw(it.category) == FanficThreadmarkCategory.MAIN }
    }
    val curChInt = item.currentProgress.toInt().coerceAtLeast(0)
    val totChInt = item.totalProgress?.toInt()?.takeIf { it > 0 }
        ?: mainChapters.size.takeIf { it > 0 }
    val progressFraction = remember(curChInt, totChInt) {
        if (totChInt != null && totChInt > 0) {
            (curChInt.toFloat() / totChInt.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }
    val totalWords = item.fanficWordCount?.takeIf { it > 0 }
        ?: mainChapters.sumOf { it.wordCount ?: 0 }.takeIf { it > 0 }
    val wordsReadEstimate = remember(totalWords, mainChapters, curChInt, progressFraction) {
        val explicitReadWords = mainChapters.filter { it.isRead }.sumOf { it.wordCount ?: 0 }
        when {
            explicitReadWords > 0 -> explicitReadWords
            totalWords != null && totChInt != null && totChInt > 0 -> (totalWords * progressFraction).roundToInt()
            else -> null
        }
    }
    val readingTimeSubtitle = remember(progressFraction, totalWords, wordsReadEstimate, totChInt) {
        val parts = mutableListOf<String>()
        if (totChInt != null && totChInt > 0) {
            parts.add("${(progressFraction * 100f).roundToInt()}%")
        }
        if (totalWords != null && totalWords > 0) {
            val readFmt = formatWordCount(wordsReadEstimate ?: 0).ifBlank { "0w" }.removeSuffix(" words")
            val totFmt = formatWordCount(totalWords)
            parts.add("~$readFmt of $totFmt read")
            val remainingWords = (totalWords - (wordsReadEstimate ?: 0)).coerceAtLeast(0)
            if (remainingWords > 0) {
                val minsLeft = (remainingWords / 250.0).roundToInt().coerceAtLeast(1)
                val timeStr = if (minsLeft >= 60) {
                    val h = minsLeft / 60
                    val m = minsLeft % 60
                    if (m > 0) "~${h}h ${m}m left" else "~${h}h left"
                } else {
                    "~${minsLeft}m left"
                }
                parts.add(timeStr)
            } else if (curChInt > 0) {
                parts.add("Caught up")
            }
        }
        parts.joinToString(" \u00B7 ")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. FRAMELESS HERO PROGRESS READOUT & STEPPERS (Matches Books & Comics 1:1)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showMetadataEditorDialog = true }
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (totChInt != null && totChInt > 0) "$curChInt / $totChInt Ch" else "$curChInt Ch",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Progress & Story Info",
                        tint = TextSecondary.copy(alpha = 0.65f),
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .size(14.dp)
                    )
                }
                if (readingTimeSubtitle.isNotBlank()) {
                    Text(
                        text = readingTimeSubtitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.07f))
                        .repeatingClickable(
                            enabled = item.currentProgress > 0.0,
                            onClick = { onStepProgress(-1.0) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "-1 Chapter",
                        tint = if (item.currentProgress > 0.0) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(17.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(AccentGold.copy(alpha = 0.18f))
                        .repeatingClickable(onClick = { onStepProgress(1.0) }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "+1 Chapter",
                        tint = AccentGold,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // Slim 3dp frameless gold progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(AccentGold)
            )
        }

        // 2. FRAMELESS WORK STATUS SELECTOR & QUICK ACTION LINKS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FanficWorkStatus.entries.forEach { st ->
                val sel = workStatus == st
                val stColor = when (st) {
                    FanficWorkStatus.COMPLETE -> AccentJade
                    FanficWorkStatus.ONGOING -> AccentGold
                    FanficWorkStatus.HIATUS -> Color(0xFFF59E0B)
                    FanficWorkStatus.DEAD -> Color(0xFFEF4444)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (sel) stColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                        .clickable { onUpdateWorkStatus(st) }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = st.label,
                        color = if (sel) stColor else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Frameless Check Updates action
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentGold.copy(alpha = 0.14f))
                    .clickable(enabled = !isCheckingUpdates && mirrorList.isNotEmpty(), onClick = onCheckUpdates)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isCheckingUpdates) {
                        CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.6.dp, color = AccentGold)
                        Text("Checking...", color = AccentGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentGold, modifier = Modifier.size(12.dp))
                        Text("Check Updates", color = AccentGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Frameless Fandom Art lookup
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable(onClick = onSearchFandomArt)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                    Text("Fandom Art", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (!updateBannerMessage.isNullOrBlank()) {
            Text(
                text = updateBannerMessage,
                color = AccentJade,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // 3. HAIRLINE EDITORIAL SPEC STRIP (Site, Rating, Kudos, Bookmarks, Hits, Updated Date)
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.6.dp)
            val specItems = remember(primarySite, rating, item.fanficIsCrossover, meta, item.bookAuthor) {
                buildList {
                    add(primarySite.shortBadge)
                    add(rating.label)
                    if (item.fanficIsCrossover) add("Crossover")
                    meta.kudosCount?.takeIf { it > 0 }?.let { add("\u2665 $it kudos") }
                    meta.bookmarksCount?.takeIf { it > 0 }?.let { add("\u2691 $it bookmarks") }
                    meta.hitsCount?.takeIf { it > 0 }?.let { add("$it hits") }
                    meta.updatedDate?.takeIf { it.isNotBlank() }?.let { add("Updated $it") }
                    meta.language?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
            Text(
                text = specItems.joinToString("  \u00B7  "),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.6.dp)
        }

        // 4. CLEAN INLINE TYPOGRAPHIC FANDOMS, WARNINGS, SHIPS, CHARACTERS & TAGS (All Clickable!)
        val allFandoms = remember(meta.fandoms, item.fanficPrimaryFandom) {
            if (meta.fandoms.isNotEmpty()) meta.fandoms
            else listOfNotNull(item.fanficPrimaryFandom?.takeIf { it.isNotBlank() })
        }
        val hasTagData = allFandoms.isNotEmpty() || meta.warnings.isNotEmpty() ||
            meta.relationships.isNotEmpty() || meta.characters.isNotEmpty() || meta.freeformTags.isNotEmpty()

        if (hasTagData) {
            if (!revealSensitiveTags) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .clickable { revealSensitiveTags = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = AccentGold, modifier = Modifier.size(15.dp))
                    Text(
                        text = "Tap to reveal ${rating.label} warnings, ships & tags",
                        color = AccentGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (allFandoms.isNotEmpty()) {
                        FramelessClickableTagGroup(
                            label = "FANDOM",
                            tags = allFandoms,
                            tagColor = Color(0xFF93C5FD),
                            onTagClick = onTagClick
                        )
                    }
                    if (meta.warnings.isNotEmpty()) {
                        FramelessClickableTagGroup(
                            label = "WARNINGS",
                            tags = meta.warnings,
                            tagColor = Color(0xFFFCA5A5),
                            onTagClick = onTagClick
                        )
                    }
                    if (meta.relationships.isNotEmpty()) {
                        FramelessClickableTagGroup(
                            label = "SHIPS",
                            tags = meta.relationships,
                            tagColor = AccentGold,
                            onTagClick = onTagClick
                        )
                    }
                    if (meta.characters.isNotEmpty()) {
                        FramelessClickableTagGroup(
                            label = "CHARACTERS",
                            tags = meta.characters,
                            tagColor = Color(0xFF6EE7B7),
                            onTagClick = onTagClick
                        )
                    }
                    if (meta.freeformTags.isNotEmpty()) {
                        val shownTags = if (expandAllFreeform) meta.freeformTags else meta.freeformTags.take(10)
                        FramelessClickableTagGroup(
                            label = "TAGS",
                            tags = shownTags,
                            tagColor = TextSecondary,
                            trailingActionLabel = if (meta.freeformTags.size > 10) {
                                if (expandAllFreeform) "Show less" else "+${meta.freeformTags.size - 10} more"
                            } else null,
                            onTrailingActionClick = { expandAllFreeform = !expandAllFreeform },
                            onTagClick = onTagClick
                        )
                    }
                }
            }
        }

        // 5. FRAMELESS UNDERLINE TABS: CHAPTERS (N) | [XENFORO CATEGORIES] | SOURCES (M)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                availableCategories.forEachIndexed { idx, cat ->
                    val count = chapters.count { FanficThreadmarkCategory.fromRaw(it.category) == cat }
                    val selected = activeTabIndex == idx
                    val tabTitle = if (cat == FanficThreadmarkCategory.MAIN) "Chapters ($count)" else "${cat.label} ($count)"
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .clickable { activeTabIndex = idx },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = tabTitle,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) AccentGold else TextSecondary,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (selected) AccentGold else Color.Transparent)
                        )
                    }
                }

                // Sources Tab
                val sourcesSelected = isSourcesTab
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { activeTabIndex = availableCategories.size },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sources (${mirrorList.size})",
                        fontSize = 13.sp,
                        fontWeight = if (sourcesSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (sourcesSelected) AccentGold else TextSecondary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (sourcesSelected) AccentGold else Color.Transparent)
                    )
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
        }

        if (isSourcesTab) {
            // SOURCES & MIRRORS TAB (Frameless Hairline List)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Primary & Linked Mirror URLs",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "+ Link Mirror",
                            color = AccentGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showAddMirrorDialog = true }
                        )
                        if (mirrorList.isNotEmpty()) {
                            Text(
                                text = if (isCheckingUpdates) "Checking..." else "\u21BB Check All",
                                color = AccentJade,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(enabled = !isCheckingUpdates, onClick = onCheckUpdates)
                            )
                        }
                    }
                }

                if (mirrorList.isEmpty()) {
                    Text(
                        text = "No source URL linked yet. Tap '+ Link Mirror' above to attach AO3, FF.net, SpaceBattles, Sufficient Velocity, or QQ.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    mirrorList.forEach { link ->
                        val linkSite = FanficSite.fromCode(link.site)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenExternalUrl(link.url) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(linkSite.accentHex))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = linkSite.shortBadge,
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = link.url,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sub = listOfNotNull(
                                    if (link.isPrimary) "Primary Source" else "Mirror",
                                    link.lastChapterCount?.let { "$it ch" },
                                    link.lastUpdatedLabel?.takeIf { it.isNotBlank() }
                                ).joinToString(" \u00B7 ")
                                Text(sub, color = TextSecondary, fontSize = 10.5.sp)
                            }
                            Text(
                                text = "WebView",
                                color = AccentGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(AccentGold.copy(alpha = 0.14f))
                                    .clickable { onOpenChapterInWebView(link.url, linkSite, null) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            IconButton(
                                onClick = { onOpenExternalUrl(link.url) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open in External Browser",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            if (!link.isPrimary) {
                                IconButton(
                                    onClick = { onRemoveMirror(link) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Mirror",
                                        tint = TextMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                    }
                }
            }
        } else {
            // CHAPTERS / THREADMARKS TAB (Frameless Hairline List + QoL Toolbar)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Chapter Toolbar: All / Unread / ★ Bookmarked | ⇅ Oldest/Newest | Jump to Ch. | Mark All / Reset | + Add
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "ALL" to "All (${categoryChapters.size})",
                        "UNREAD" to "Unread (${categoryChapters.count { !it.isRead }})",
                        "BOOKMARKED" to "\u2605 Saved (${categoryChapters.count { it.isBookmarked || !it.userNote.isNullOrBlank() }})"
                    ).forEach { (mode, label) ->
                        val active = chapterFilterMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (active) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                                .clickable { chapterFilterMode = mode }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) AccentGold else TextSecondary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .clickable { sortNewestFirst = !sortNewestFirst }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (sortNewestFirst) "\u21C5 Newest" else "\u21C5 Oldest",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }

                    if (categoryChapters.size > 5) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .clickable { showJumpChapterDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Jump to Ch.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }
                    }

                    if (selectedCategory == FanficThreadmarkCategory.MAIN && categoryChapters.isNotEmpty()) {
                        val allRead = categoryChapters.all { it.isRead }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .clickable { onMarkAllChaptersRead(!allRead) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (allRead) "Reset All" else "Mark All Read",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (allRead) TextSecondary else AccentJade
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isChapterMultiSelect) AccentGold.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f))
                            .clickable {
                                isChapterMultiSelect = !isChapterMultiSelect
                                if (!isChapterMultiSelect) selectedChapterIds = emptySet()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isChapterMultiSelect) "Done (${selectedChapterIds.size})" else "Select",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isChapterMultiSelect) AccentGold else TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(AccentGold.copy(alpha = 0.14f))
                            .clickable {
                                manualChapCategory = selectedCategory
                                showAddChapterDialog = true
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+ Add",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold
                        )
                    }
                }

                if (isChapterMultiSelect) {
                    MultiSelectTopBar(
                        selectedCount = selectedChapterIds.size,
                        totalCount = filteredChapters.size,
                        onSelectAll = { selectedChapterIds = filteredChapters.map { it.id }.toSet() },
                        onInvertSelection = {
                            val allIds = filteredChapters.map { it.id }.toSet()
                            selectedChapterIds = allIds - selectedChapterIds
                        },
                        onClearSelection = { selectedChapterIds = emptySet() },
                        onExitMultiSelect = {
                            isChapterMultiSelect = false
                            selectedChapterIds = emptySet()
                        }
                    )
                    MultiSelectBottomActionDock(
                        selectedCount = selectedChapterIds.size,
                        onMarkCompleted = {
                            val targets = filteredChapters.filter { it.id in selectedChapterIds && !it.isRead }
                            val maxTarget = targets.maxByOrNull { it.chapterNumber }
                            if (maxTarget != null) {
                                onToggleChapterRead(maxTarget)
                            }
                            isChapterMultiSelect = false
                            selectedChapterIds = emptySet()
                        },
                        onMarkUnread = {
                            val targets = filteredChapters.filter { it.id in selectedChapterIds && it.isRead }
                            targets.forEach { ch -> onToggleChapterRead(ch) }
                            isChapterMultiSelect = false
                            selectedChapterIds = emptySet()
                        },
                        isInline = true
                    )
                }

                if (filteredChapters.isEmpty()) {
                    Text(
                        text = when (chapterFilterMode) {
                            "UNREAD" -> "All chapters in this tab are marked read."
                            "BOOKMARKED" -> "No bookmarked or annotated chapters yet — tap \u2606 on any chapter to save it."
                            else -> "No chapters synced yet. Pull down to refresh or tap '+ Add' to add chapters manually."
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                } else {
                    filteredChapters.forEach { ch ->
                        val chUrl = ch.chapterUrl?.takeIf { it.isNotBlank() } ?: item.fanficSourceUrl
                        val isHighlighted = highlightedChapterNumber == ch.chapterNumber
                        val isChapSelected = ch.id in selectedChapterIds
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        isChapSelected -> AccentGold.copy(alpha = 0.12f)
                                        isHighlighted -> AccentGold.copy(alpha = 0.08f)
                                        else -> Color.Transparent
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChapterMultiSelect) {
                                            selectedChapterIds = if (isChapSelected) selectedChapterIds - ch.id else selectedChapterIds + ch.id
                                        } else if (!chUrl.isNullOrBlank()) {
                                            onOpenExternalUrl(chUrl)
                                        } else {
                                            onToggleChapterRead(ch)
                                        }
                                    }
                                    .padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isChapterMultiSelect) {
                                    MultiSelectCheckBadge(selected = isChapSelected)
                                }
                                // Cumulative read checkmark (tapping unread Ch. 7 marks Ch. 1..7 read!)
                                Box(
                                    modifier = Modifier
                                        .size(21.dp)
                                        .clip(CircleShape)
                                        .background(if (ch.isRead) AccentJade else Color.Transparent)
                                        .border(
                                            1.3.dp,
                                            if (ch.isRead) AccentJade else Color.White.copy(alpha = 0.25f),
                                            CircleShape
                                        )
                                        .clickable { onToggleChapterRead(ch) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (ch.isRead) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Read",
                                            tint = Color.Black,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${ch.chapterNumber}. ${ch.title}",
                                        color = if (ch.isRead) TextSecondary.copy(alpha = 0.72f) else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (ch.isRead) FontWeight.Medium else FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subParts = listOfNotNull(
                                        formatWordCount(ch.wordCount).takeIf { it.isNotBlank() },
                                        ch.publishedDate?.takeIf { it.isNotBlank() },
                                        ch.userRating?.takeIf { it > 0f }?.let { "\u2605 ${String.format("%.1f", it)}" }
                                    )
                                    if (subParts.isNotEmpty()) {
                                        Text(
                                            text = subParts.joinToString(" \u00B7 "),
                                            color = TextMuted,
                                            fontSize = 10.5.sp
                                        )
                                    }
                                    if (!ch.userNote.isNullOrBlank()) {
                                        Text(
                                            text = "\u201C${ch.userNote}\u201D",
                                            color = AccentGold.copy(alpha = 0.9f),
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }

                                // Bookmark star ★
                                IconButton(
                                    onClick = { onToggleChapterBookmark(ch) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (ch.isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Bookmark Chapter",
                                        tint = if (ch.isBookmarked) AccentGold else TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Chapter note / rating editor
                                IconButton(
                                    onClick = { editingNoteChapter = ch },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Chapter Note",
                                        tint = if (!ch.userNote.isNullOrBlank()) AccentGold else TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // External browser indicator
                                if (!chUrl.isNullOrBlank()) {
                                    IconButton(
                                        onClick = { onOpenExternalUrl(chUrl) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Open in External Browser",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onDeleteChapter(ch.id) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Chapter",
                                        tint = TextMuted.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    // DIALOG 1: Exact Progress & Story Metadata Editor (opened by tapping the chapter progress number)
    if (showMetadataEditorDialog) {
        var curInput by remember(item.currentProgress) { mutableStateOf(item.currentProgress.toInt().toString()) }
        var totInput by remember(item.totalProgress) { mutableStateOf(item.totalProgress?.toInt()?.toString().orEmpty()) }
        var wordsInput by remember(item.fanficWordCount) { mutableStateOf(item.fanficWordCount?.toString().orEmpty()) }
        var authorInput by remember(item.bookAuthor) { mutableStateOf(item.bookAuthor.orEmpty()) }
        var fandomInput by remember(item.fanficPrimaryFandom) { mutableStateOf(item.fanficPrimaryFandom.orEmpty()) }
        var selectedRating by remember(rating) { mutableStateOf(rating) }
        var selectedWorkSt by remember(workStatus) { mutableStateOf(workStatus) }

        AlertDialog(
            onDismissRequest = { showMetadataEditorDialog = false },
            containerColor = DarkSurfaceCard,
            title = {
                Text("Edit Progress & Story Info", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = curInput,
                            onValueChange = { curInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Read Ch.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = totInput,
                            onValueChange = { totInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Total Ch.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = wordsInput,
                        onValueChange = { wordsInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Total Word Count") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = authorInput,
                        onValueChange = { authorInput = it },
                        label = { Text("Author") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fandomInput,
                        onValueChange = { fandomInput = it },
                        label = { Text("Primary Fandom") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Rating", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FanficRating.entries.forEach { r ->
                            val sel = selectedRating == r
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) AccentGold.copy(alpha = 0.22f) else DarkSurfaceElevated)
                                    .clickable { selectedRating = r }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(r.label, fontSize = 11.sp, color = if (sel) AccentGold else TextSecondary)
                            }
                        }
                    }
                    Text("Work Status", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FanficWorkStatus.entries.forEach { st ->
                            val sel = selectedWorkSt == st
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) AccentJade.copy(alpha = 0.22f) else DarkSurfaceElevated)
                                    .clickable { selectedWorkSt = st }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(st.label, fontSize = 11.sp, color = if (sel) AccentJade else TextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cur = curInput.toDoubleOrNull() ?: item.currentProgress
                        val tot = totInput.toDoubleOrNull()?.takeIf { it > 0.0 }
                        val wc = wordsInput.toIntOrNull()?.takeIf { it > 0 }
                        onUpdateQuickMetadata(cur, tot, wc, authorInput, fandomInput, selectedRating.code, selectedWorkSt)
                        showMetadataEditorDialog = false
                    }
                ) {
                    Text("Save", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMetadataEditorDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // DIALOG 2: Jump to Chapter
    if (showJumpChapterDialog) {
        AlertDialog(
            onDismissRequest = { showJumpChapterDialog = false },
            containerColor = DarkSurfaceCard,
            title = { Text("Jump to Chapter", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a chapter number to open it in your browser or mark chapters read up to it:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = jumpChapterInput,
                        onValueChange = { jumpChapterInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Chapter Number (1 - ${categoryChapters.maxOfOrNull { it.chapterNumber } ?: 1})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            val num = jumpChapterInput.toIntOrNull()
                            val target = categoryChapters.firstOrNull { it.chapterNumber == num }
                            if (target != null) {
                                highlightedChapterNumber = target.chapterNumber
                                onMarkReadUpTo(target)
                            }
                            showJumpChapterDialog = false
                        }
                    ) {
                        Text("Mark Read Up To", color = AccentJade, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            val num = jumpChapterInput.toIntOrNull()
                            val target = categoryChapters.firstOrNull { it.chapterNumber == num }
                            if (target != null) {
                                highlightedChapterNumber = target.chapterNumber
                                val u = target.chapterUrl?.takeIf { it.isNotBlank() } ?: item.fanficSourceUrl
                                if (!u.isNullOrBlank()) onOpenExternalUrl(u)
                            }
                            showJumpChapterDialog = false
                        }
                    ) {
                        Text("Open Ch.", color = AccentGold, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpChapterDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // DIALOG 3: Per-Chapter Note & Rating Editor
    editingNoteChapter?.let { targetCh ->
        var noteInput by remember(targetCh.id) { mutableStateOf(targetCh.userNote.orEmpty()) }
        var ratingInput by remember(targetCh.id) { mutableStateOf(targetCh.userRating?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingNoteChapter = null },
            containerColor = DarkSurfaceCard,
            title = {
                Text(
                    text = "Ch. ${targetCh.chapterNumber}: ${targetCh.title}",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Personal Chapter Note / Favorite Quote") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ratingInput,
                        onValueChange = { ratingInput = it },
                        label = { Text("Chapter Rating (0 - 10, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val r = ratingInput.toFloatOrNull()?.coerceIn(0f, 10f)
                        onUpdateChapterNote(targetCh, noteInput, r)
                        editingNoteChapter = null
                    }
                ) {
                    Text("Save Note", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingNoteChapter = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // DIALOG 4: Link Mirror URL
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

    // DIALOG 5: Add Manual Chapter
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
private fun FramelessClickableTagGroup(
    label: String,
    tags: List<String>,
    tagColor: Color,
    trailingActionLabel: String? = null,
    onTrailingActionClick: (() -> Unit)? = null,
    onTagClick: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "$label:",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextMuted,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(end = 2.dp, top = 1.dp)
        )
        tags.forEachIndexed { idx, tag ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tag,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tagColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onTagClick(tag) }
                        .padding(horizontal = 2.dp)
                )
                if (idx < tags.lastIndex) {
                    Text(
                        text = " \u00B7 ",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        }
        if (trailingActionLabel != null && onTrailingActionClick != null) {
            Text(
                text = trailingActionLabel,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clickable(onClick = onTrailingActionClick)
            )
        }
    }
}
