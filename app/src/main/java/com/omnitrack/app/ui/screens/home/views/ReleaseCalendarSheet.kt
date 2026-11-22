package com.omnitrack.app.ui.screens.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.enrichment.CalendarReleaseItem
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseCalendarSheet(
    releases: List<CalendarReleaseItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenLocalMedia: (Long) -> Unit,
    onPreviewRelease: (CalendarReleaseItem) -> Unit,
    onAddRelease: (CalendarReleaseItem) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val weekDays = remember(today) { (0..6).map { today.plusDays(it.toLong()) } }

    val hasLibraryMatches = remember(releases) { releases.any { it.localMediaId != null } }
    var onlyLibrary by remember(hasLibraryMatches) { mutableStateOf(hasLibraryMatches) }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // "ALL", "ANIME", "COMIC"
    var selectedDate by remember { mutableStateOf(today) }

    val scopeFilteredReleases = remember(releases, onlyLibrary, selectedTypeFilter) {
        releases.filter { item ->
            (!onlyLibrary || item.localMediaId != null) &&
                (selectedTypeFilter == "ALL" ||
                    (selectedTypeFilter == "ANIME" && item.mediaType == MediaType.ANIME) ||
                    (selectedTypeFilter == "COMIC" && item.mediaType == MediaType.COMIC))
        }
    }

    val datesWithEvents = remember(scopeFilteredReleases, zone) {
        scopeFilteredReleases.map { item ->
            Instant.ofEpochSecond(item.airingAtEpochSec).atZone(zone).toLocalDate()
        }.toSet()
    }

    val dayReleases = remember(scopeFilteredReleases, selectedDate, zone) {
        scopeFilteredReleases
            .filter { Instant.ofEpochSecond(it.airingAtEpochSec).atZone(zone).toLocalDate() == selectedDate }
            .sortedBy { it.airingAtEpochSec }
    }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.US) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Header + Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Release Calendar",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Live 7-day airing & comic pull list schedule",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                IconButton(onClick = onRefresh) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = AccentGold,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Schedule",
                            tint = TextSecondary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Chips: All / Anime / Comics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val animeCount = releases.count { it.mediaType == MediaType.ANIME && (!onlyLibrary || it.localMediaId != null) }
                val comicCount = releases.count { it.mediaType == MediaType.COMIC && (!onlyLibrary || it.localMediaId != null) }
                val allCount = animeCount + comicCount
                listOf(
                    "ALL" to "All ($allCount)",
                    "ANIME" to "Anime ($animeCount)",
                    "COMIC" to "Comics ($comicCount)"
                ).forEach { (key, label) ->
                    val selected = selectedTypeFilter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) AccentGold else DarkSurfaceElevated)
                            .border(
                                width = 1.dp,
                                color = if (selected) AccentGold else GlassBorder,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .clickable { selectedTypeFilter = key }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) AmoledBlack else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // My Library vs All Airing segmented toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CalendarToggleTab(
                    label = "My Library (${releases.count { it.localMediaId != null }})",
                    selected = onlyLibrary,
                    onClick = { onlyLibrary = true },
                    modifier = Modifier.weight(1f)
                )
                CalendarToggleTab(
                    label = "All Airing (${releases.size})",
                    selected = !onlyLibrary,
                    onClick = { onlyLibrary = false },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 7-Day Interactive Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                weekDays.forEach { day ->
                    val isSelected = day == selectedDate
                    val isToday = day == today
                    val hasEvent = day in datesWithEvents
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) AccentGold else DarkSurfaceElevated)
                            .border(
                                width = 1.dp,
                                color = when {
                                    isSelected -> AccentGold
                                    isToday -> AccentGold.copy(alpha = 0.5f)
                                    else -> GlassBorder
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedDate = day }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = if (isToday) "TODAY" else day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) AmoledBlack else TextSecondary
                        )
                        Text(
                            text = day.dayOfMonth.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) AmoledBlack else TextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        hasEvent && isSelected -> AmoledBlack
                                        hasEvent -> AccentGold
                                        else -> DarkSurfaceElevated.copy(alpha = 0f)
                                    }
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Releases for Selected Day
            if (isLoading && releases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentGold, strokeWidth = 2.dp)
                }
            } else if (dayReleases.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        text = if (onlyLibrary) "No tracked shows airing on this day" else "No episodes scheduled for this day",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    if (onlyLibrary && releases.isNotEmpty()) {
                        Text(
                            text = "Switch to All Airing to discover upcoming shows.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dayReleases, key = { it.id }) { item ->
                        val localId = item.localMediaId
                        val localTime = remember(item.airingAtEpochSec) {
                            Instant.ofEpochSecond(item.airingAtEpochSec).atZone(zone).format(timeFormatter)
                        }
                        val countdown = remember(item.airingAtEpochSec) {
                            formatCountdown(item.airingAtEpochSec)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceElevated)
                                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    if (localId != null) onOpenLocalMedia(localId) else onPreviewRelease(item)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AsyncImage(
                                model = item.coverUrl,
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
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        if (item.episodeNumber != null) {
                                            append(if (item.mediaType == MediaType.COMIC) "Issue #${item.episodeNumber}" else "Ep ${item.episodeNumber}")
                                        }
                                        if (item.formatLabel.isNotBlank()) {
                                            if (isNotEmpty()) append(" \u00B7 ")
                                            append(item.formatLabel)
                                        }
                                    },
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "$localTime \u00B7 $countdown",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentGold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (localId != null) DarkSurfaceCard
                                        else AccentGold
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (localId != null) GlassBorder else AccentGold,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (localId != null) onOpenLocalMedia(localId) else onAddRelease(item)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (localId != null) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = null,
                                        tint = if (localId != null) AccentGold else AmoledBlack,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (localId != null) "In Library" else "Add",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (localId != null) TextPrimary else AmoledBlack
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarToggleTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) AccentGold else DarkSurfaceElevated)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) AmoledBlack else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatCountdown(airingAtEpochSec: Long): String {
    val diffSec = airingAtEpochSec - (System.currentTimeMillis() / 1000L)
    if (diffSec <= 0L) return "Aired"
    val mins = diffSec / 60L
    val days = mins / (60L * 24L)
    val hours = (mins % (60L * 24L)) / 60L
    val remMins = mins % 60L
    return when {
        days > 0L -> "In ${days}d ${hours}h"
        hours > 0L -> "In ${hours}h ${remMins}m"
        else -> "In ${remMins}m"
    }
}
