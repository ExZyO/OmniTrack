package com.omnitrack.app.ui.screens.home.views

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class HistoryBucket(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    EARLIER("Earlier")
}

@Composable
fun HistoryView(
    allMedia: List<MediaCardSummary>,
    onItemClick: (Long) -> Unit,
    onQuickProgress: (MediaCardSummary) -> Unit,
    watchedTimestamps90d: List<Long> = emptyList(),
    onOpenHeatmap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(MediaType.ALL) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val yesterday = remember(today) { today.minusDays(1) }
    val weekAgo = remember(today) { today.minusDays(6) }

    val allActiveHistory = remember(allMedia) {
        allMedia.filter { item ->
            item.currentProgress > 0.0 ||
                item.playtimeHours > 0.0 ||
                (item.bookAudioMinutes ?: 0) > 0 ||
                item.status == TrackingStatus.COMPLETED ||
                item.status == TrackingStatus.IN_PROGRESS
        }.sortedByDescending { it.lastUpdated }
    }

    val filteredHistory = remember(allActiveHistory, selectedCategory, searchQuery) {
        val byCat = if (selectedCategory == MediaType.ALL) {
            allActiveHistory
        } else {
            allActiveHistory.filter { it.mediaType == selectedCategory }
        }
        val q = searchQuery.trim()
        if (q.isEmpty()) byCat
        else byCat.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.nativeTitle?.contains(q, ignoreCase = true) == true ||
                it.bookAuthor?.contains(q, ignoreCase = true) == true
        }
    }

    val groupedHistory = remember(filteredHistory, today, yesterday, weekAgo, zone) {
        filteredHistory.groupBy { item ->
            val date = Instant.ofEpochMilli(item.lastUpdated).atZone(zone).toLocalDate()
            when {
                !date.isBefore(today) -> HistoryBucket.TODAY
                date == yesterday -> HistoryBucket.YESTERDAY
                !date.isBefore(weekAgo) -> HistoryBucket.THIS_WEEK
                else -> HistoryBucket.EARLIER
            }
        }
    }

    val historyStats = remember(allMedia, watchedTimestamps90d, today, weekAgo, zone) {
        val combinedTs = (watchedTimestamps90d + allMedia.map { it.lastUpdated }).distinct()
        val weekUpdates = combinedTs.count { ts ->
            ts > 0L && !Instant.ofEpochMilli(ts).atZone(zone).toLocalDate().isBefore(weekAgo)
        }
        val activeDates = combinedTs.mapNotNull { ts ->
            if (ts > 0L) Instant.ofEpochMilli(ts).atZone(zone).toLocalDate() else null
        }.toSet()
        var streak = 0
        var cursor = if (today in activeDates) today else today.minusDays(1)
        while (streak < 90 && cursor in activeDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        val finished = allMedia.count { it.status == TrackingStatus.COMPLETED }
        Triple(weekUpdates, streak, finished)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .statusBarsPadding()
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "History",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${filteredHistory.size} tracked entries",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (searchVisible) AccentGold else DarkSurfaceElevated)
                    .border(1.dp, if (searchVisible) AccentGold else GlassBorder, CircleShape)
                    .clickable {
                        searchVisible = !searchVisible
                        if (!searchVisible) searchQuery = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search History",
                    tint = if (searchVisible) AmoledBlack else TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Collapsible Inline Search Input
        AnimatedVisibility(visible = searchVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(16.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AccentGold),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Filter history by title or author...",
                            color = TextSecondary.copy(alpha = 0.7f),
                            fontSize = 13.5.sp
                        )
                    }
                }
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { searchQuery = "" }
                    )
                }
            }
        }

        // Category Underline Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaType.filterTabs.forEach { type ->
                val selected = selectedCategory == type
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { selectedCategory = type }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = type.displayName,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) TextPrimary else TextSecondary
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) AccentGold else AmoledBlack)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Compact Clickable 90-Day Stats Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenHeatmap)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HistoryStatMetric(
                value = "${historyStats.first}",
                label = "This Week",
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(GlassBorder))
            HistoryStatMetric(
                value = "${historyStats.second}d",
                label = "Streak",
                highlight = true,
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(GlassBorder))
            HistoryStatMetric(
                value = "${historyStats.third}",
                label = "Finished",
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "90d Map",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open 90-day Activity Map",
                    tint = AccentGold,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = "No activity yet",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Tap + on any card in Home or Library to log your latest progress.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 116.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HistoryBucket.entries.forEach { bucket ->
                    val bucketItems = groupedHistory[bucket].orEmpty()
                    if (bucketItems.isNotEmpty()) {
                        item(key = "hdr_${bucket.name}") {
                            Text(
                                text = bucket.label.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }
                        items(bucketItems, key = { "hist_${it.id}" }) { item ->
                            HistoryTimelineRow(
                                item = item,
                                zone = zone,
                                onClick = { onItemClick(item.id) },
                                onQuickProgress = { onQuickProgress(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryStatMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) AccentGold else TextPrimary
        )
        Text(
            text = label,
            fontSize = 10.5.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun HistoryTimelineRow(
    item: MediaCardSummary,
    zone: ZoneId,
    onClick: () -> Unit,
    onQuickProgress: () -> Unit
) {
    val thumbModel = remember(item.customCoverPath, item.coverUrl) {
        item.customCoverPath?.let { if (it.startsWith("http")) it else File(it) }
            ?: LosslessCoverMapper.toLosslessUrl(item.coverUrl)
    }
    val progressLabel = remember(item) { formatConciseProgress(item) }
    val timeLabel = remember(item.lastUpdated) { formatRelativeTimestamp(item.lastUpdated, zone) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = thumbModel,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(46.dp)
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceCard)
        )

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
                    progressLabel.takeIf { it.isNotBlank() },
                    timeLabel
                ).joinToString(" \u00B7 "),
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(DarkSurfaceElevated)
                .border(1.dp, GlassBorder, CircleShape)
                .clickable(onClick = onQuickProgress),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.mediaType == MediaType.MOVIE) Icons.Default.Check else Icons.Default.Add,
                contentDescription = "Log Progress",
                tint = AccentGold,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatRelativeTimestamp(epochMs: Long, zone: ZoneId): String {
    if (epochMs <= 0L) return ""
    val diffMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    val hours = mins / 60L
    val days = hours / 24L
    return when {
        mins < 2L -> "Just now"
        mins < 60L -> "${mins}m ago"
        hours < 24L -> "${hours}h ago"
        days < 7L -> "${days}d ago"
        else -> {
            val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
            Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().format(fmt)
        }
    }
}
