package com.omnitrack.app.ui.screens.home.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.artwork.ArtworkModelResolver
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.ui.components.FanficTypographicCover
import com.omnitrack.app.ui.screens.detail.ActiveSiblingHolder
import com.omnitrack.app.ui.screens.detail.components.formatSmartRating
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class HistoryWindowFilter(val label: String, val days: Int?) {
    ALL("All Time", null),
    DAYS_7("7d", 7),
    DAYS_30("30d", 30),
    DAYS_90("90d", 90)
}

private enum class HistorySortMode(val label: String) {
    RECENT("Recent"),
    TITLE("A-Z"),
    RATING("Rating")
}

/**
 * Frameless Pure-AMOLED History Log View with inline Search, Multi-Facet Filters,
 * Hairline Timeline Rows, and Long-Press Context Action Sheet.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryView(
    allMedia: List<MediaCardSummary>,
    onItemClick: (Long) -> Unit,
    onQuickProgress: (MediaCardSummary) -> Unit,
    onMarkCompleted: (MediaCardSummary) -> Unit = {},
    onSetStatus: (MediaCardSummary, TrackingStatus) -> Unit = { _, _ -> },
    onRemoveFromHistory: (MediaCardSummary) -> Unit = {},
    onDeleteItem: (MediaCardSummary) -> Unit = {},
    watchedTimestamps90d: List<Long> = emptyList(),
    onOpenHeatmap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val yesterday = remember(today) { today.minusDays(1) }
    val weekAgo = remember(today) { today.minusDays(7) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(MediaType.ALL) }
    var selectedStatus by remember { mutableStateOf<TrackingStatus?>(null) }
    var selectedWindow by remember { mutableStateOf(HistoryWindowFilter.ALL) }
    var selectedSort by remember { mutableStateOf(HistorySortMode.RECENT) }
    var actionTarget by remember { mutableStateOf<MediaCardSummary?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<MediaCardSummary?>(null) }

    val activeHistoryMedia = remember(allMedia) {
        allMedia.filter { it.lastUpdated > 0L }
    }

    val streakAndCount = remember(activeHistoryMedia, watchedTimestamps90d, today) {
        val allDates = (watchedTimestamps90d + activeHistoryMedia.map { it.lastUpdated })
            .mapNotNull { ts -> if (ts > 0L) Instant.ofEpochMilli(ts).atZone(zone).toLocalDate() else null }
            .toSet()
        var streak = 0
        var cursor = if (today in allDates) today else today.minusDays(1)
        while (streak < 90 && cursor in allDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        val activeToday = activeHistoryMedia.count {
            Instant.ofEpochMilli(it.lastUpdated).atZone(zone).toLocalDate() == today
        }
        streak to activeToday
    }

    val filteredHistory = remember(
        activeHistoryMedia,
        searchQuery,
        selectedType,
        selectedStatus,
        selectedWindow,
        selectedSort
    ) {
        val q = searchQuery.trim()
        val nowMs = System.currentTimeMillis()
        val windowCutoffMs = selectedWindow.days?.let { days ->
            nowMs - days.toLong() * 24L * 60L * 60L * 1000L
        }
        val matching = activeHistoryMedia.filter { item ->
            (q.isEmpty() || item.title.contains(q, ignoreCase = true) ||
                item.bookAuthor?.contains(q, ignoreCase = true) == true ||
                item.comicPublisher?.contains(q, ignoreCase = true) == true) &&
                (selectedType == MediaType.ALL || item.mediaType == selectedType) &&
                (selectedStatus == null || item.status == selectedStatus) &&
                (windowCutoffMs == null || item.lastUpdated >= windowCutoffMs)
        }
        when (selectedSort) {
            HistorySortMode.RECENT -> matching.sortedByDescending { it.lastUpdated }
            HistorySortMode.TITLE -> matching.sortedBy { it.title.lowercase() }
            HistorySortMode.RATING -> matching.sortedWith(
                compareByDescending<MediaCardSummary> { it.userRating ?: it.communityScore ?: 0f }
                    .thenByDescending { it.lastUpdated }
            )
        }
    }

    val groupedByBucket = remember(filteredHistory, selectedSort, zone, today, yesterday, weekAgo) {
        if (selectedSort != HistorySortMode.RECENT) {
            listOf("${selectedSort.label} (${filteredHistory.size})" to filteredHistory)
        } else {
            val todayList = mutableListOf<MediaCardSummary>()
            val yesterdayList = mutableListOf<MediaCardSummary>()
            val weekList = mutableListOf<MediaCardSummary>()
            val earlierList = mutableListOf<MediaCardSummary>()
            filteredHistory.forEach { item ->
                val d = Instant.ofEpochMilli(item.lastUpdated).atZone(zone).toLocalDate()
                when {
                    d == today -> todayList.add(item)
                    d == yesterday -> yesterdayList.add(item)
                    d.isAfter(weekAgo) -> weekList.add(item)
                    else -> earlierList.add(item)
                }
            }
            listOf(
                "TODAY" to todayList,
                "YESTERDAY" to yesterdayList,
                "THIS WEEK" to weekList,
                "EARLIER" to earlierList
            ).filter { it.second.isNotEmpty() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .statusBarsPadding()
    ) {
        // Frameless Editorial Header + Streak / Heatmap link
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "History",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "${streakAndCount.first}d streak \u00B7 ${streakAndCount.second} logged today \u00B7 ${activeHistoryMedia.size} tracked",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            IconButton(
                onClick = onOpenHeatmap,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = "Activity Heatmap",
                    tint = AccentGold,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        // Clean Flat Inline Search (Option A)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(17.dp)
            )
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.5.sp),
                cursorBrush = SolidColor(AccentGold),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Filter history by title, author or publisher...",
                            fontSize = 13.sp,
                            color = TextSecondary.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            )
            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear Search",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { searchQuery = "" }
                )
            }
        }

        // Option A Underline Category Tabs
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(MediaType.filterTabs, key = { _, t -> "hcat_${t.name}" }) { _, type ->
                val selected = selectedType == type
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { selectedType = type },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (type == MediaType.ALL) "All" else type.displayName,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) TextPrimary else TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
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

        // Secondary Facet Filters: Window + Status + Sort
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistoryWindowFilter.entries.forEach { wf ->
                val active = selectedWindow == wf
                HistoryCompactChip(
                    label = wf.label,
                    active = active,
                    onClick = { selectedWindow = wf }
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.14f))
            )

            listOf(
                null to "Any Status",
                TrackingStatus.IN_PROGRESS to "Active",
                TrackingStatus.COMPLETED to "Completed",
                TrackingStatus.PLAN_TO_TRACK to "Planned",
                TrackingStatus.ON_HOLD to "On Hold",
                TrackingStatus.DROPPED to "Dropped"
            ).forEach { (st, lbl) ->
                val active = selectedStatus == st
                HistoryCompactChip(
                    label = lbl,
                    active = active,
                    onClick = { selectedStatus = if (selectedStatus == st) null else st }
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.14f))
            )

            HistorySortMode.entries.forEach { sm ->
                val active = selectedSort == sm
                HistoryCompactChip(
                    label = "Sort: ${sm.label}",
                    active = active,
                    onClick = { selectedSort = sm }
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

        if (filteredHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.55f),
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedStatus != null || selectedWindow != HistoryWindowFilter.ALL || selectedType != MediaType.ALL) {
                            "No history entries match your filters"
                        } else {
                            "No tracking activity yet"
                        },
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 114.dp)
            ) {
                groupedByBucket.forEach { (bucketTitle, bucketItems) ->
                    item(key = "hgrp_$bucketTitle") {
                        Text(
                            text = bucketTitle,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)
                        )
                    }
                    itemsIndexed(
                        items = bucketItems,
                        key = { _, item -> "hist_${item.id}" }
                    ) { index, item ->
                        HistoryHairlineRow(
                            item = item,
                            zone = zone,
                            onClick = {
                                ActiveSiblingHolder.setSiblings(filteredHistory.map { it.id })
                                onItemClick(item.id)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                actionTarget = item
                            }
                        )
                        if (index < bucketItems.lastIndex) {
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.07f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 78.dp, end = 20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Long-Press Action Sheet (Frameless AMOLED)
    val sheetTarget = actionTarget
    if (sheetTarget != null) {
        val coverModel = remember(sheetTarget.customCoverPath, sheetTarget.coverUrl) {
            ArtworkModelResolver.toCoilModel(ArtworkModelResolver.preferredCover(sheetTarget))
        }
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AmoledBlack,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AmoledBlack)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.24f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(46.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        if (coverModel != null) {
                            AsyncImage(
                                model = coverModel,
                                contentDescription = sheetTarget.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (sheetTarget.mediaType == MediaType.FANFICTION) {
                            FanficTypographicCover(
                                title = sheetTarget.title,
                                author = sheetTarget.bookAuthor,
                                primaryFandom = sheetTarget.fanficPrimaryFandom,
                                sourceSite = sheetTarget.fanficSourceSite,
                                ratingCode = sheetTarget.fanficRating,
                                wordCount = sheetTarget.fanficWordCount,
                                isCrossover = sheetTarget.fanficIsCrossover,
                                compact = true,
                                isBlurred = false
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = sheetTarget.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOfNotNull(
                                sheetTarget.mediaType.displayName,
                                sheetTarget.status.displayName,
                                formatConciseProgress(sheetTarget).takeIf { it.isNotBlank() }
                            ).joinToString(" \u00B7 "),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                HistorySheetActionRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    label = "Open Details Page",
                    tint = TextPrimary,
                    onClick = {
                        val id = sheetTarget.id
                        actionTarget = null
                        onItemClick(id)
                    }
                )
                HistorySheetActionRow(
                    icon = Icons.Default.Add,
                    label = "Log +1 Progress",
                    tint = AccentGold,
                    onClick = {
                        actionTarget = null
                        onQuickProgress(sheetTarget)
                    }
                )
                HistorySheetActionRow(
                    icon = Icons.Default.Check,
                    label = "Mark Completed",
                    tint = AccentGold,
                    onClick = {
                        actionTarget = null
                        onMarkCompleted(sheetTarget)
                    }
                )

                Text(
                    text = "CHANGE STATUS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.9.sp
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        TrackingStatus.IN_PROGRESS,
                        TrackingStatus.COMPLETED,
                        TrackingStatus.PLAN_TO_TRACK,
                        TrackingStatus.ON_HOLD,
                        TrackingStatus.DROPPED
                    ).forEach { st ->
                        val active = sheetTarget.status == st
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AccentGold else Color.White.copy(alpha = 0.06f))
                                .clickable {
                                    actionTarget = null
                                    onSetStatus(sheetTarget, st)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = st.displayName,
                                fontSize = 11.5.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) OnAccentColor else TextPrimary
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                HistorySheetActionRow(
                    icon = Icons.Default.VisibilityOff,
                    label = "Hide from History",
                    tint = TextSecondary,
                    onClick = {
                        actionTarget = null
                        onRemoveFromHistory(sheetTarget)
                    }
                )
                HistorySheetActionRow(
                    icon = Icons.Default.DeleteOutline,
                    label = "Delete from Library",
                    tint = Color(0xFFF87171),
                    onClick = {
                        val target = sheetTarget
                        actionTarget = null
                        deleteConfirmTarget = target
                    }
                )
            }
        }
    }

    val confirmTarget = deleteConfirmTarget
    if (confirmTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            containerColor = AmoledBlack,
            title = {
                Text("Delete \"${confirmTarget.title}\"?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "This removes the entry, local notes, and chapter/episode history from your OmniTrack Library.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = confirmTarget
                        deleteConfirmTarget = null
                        onDeleteItem(target)
                    }
                ) {
                    Text("Delete", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun HistoryCompactChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (active) AccentGold.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) AccentGold else TextSecondary
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryHairlineRow(
    item: MediaCardSummary,
    zone: ZoneId,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val coverModel = remember(item.customCoverPath, item.coverUrl) {
        ArtworkModelResolver.toCoilModel(ArtworkModelResolver.preferredCover(item))
    }
    val progressStr = remember(item) { formatConciseProgress(item) }
    val rating = item.userRating ?: item.communityScore
    val timeLabel = remember(item.lastUpdated) {
        val dt = Instant.ofEpochMilli(item.lastUpdated).atZone(zone)
        dt.format(DateTimeFormatter.ofPattern("MMM d \u00B7 h:mm a", Locale.US))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            if (coverModel != null) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (item.mediaType == MediaType.FANFICTION) {
                FanficTypographicCover(
                    title = item.title,
                    author = item.bookAuthor,
                    primaryFandom = item.fanficPrimaryFandom,
                    sourceSite = item.fanficSourceSite,
                    ratingCode = item.fanficRating,
                    wordCount = item.fanficWordCount,
                    isCrossover = item.fanficIsCrossover,
                    compact = true,
                    isBlurred = false
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    item.mediaType.displayName,
                    progressStr.takeIf { it.isNotBlank() },
                    rating?.takeIf { it > 0f }?.let { "\u2605 ${formatSmartRating(it.toDouble())}" }
                ).joinToString(" \u00B7 "),
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.status.displayName} \u00B7 $timeLabel",
                fontSize = 11.sp,
                color = TextSecondary.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HistorySheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = tint
        )
    }
}
