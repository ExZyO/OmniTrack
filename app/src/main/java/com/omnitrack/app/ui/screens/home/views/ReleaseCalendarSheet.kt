package com.omnitrack.app.ui.screens.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.artwork.ArtworkModelResolver
import com.omnitrack.app.data.enrichment.CalendarReleaseItem
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextMuted
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
    val windowDays = remember(today) { (0..13).map { today.plusDays(it.toLong()) } }

    val hasLibraryMatches = remember(releases) { releases.any { it.localMediaId != null } }
    var onlyLibrary by remember(hasLibraryMatches) { mutableStateOf(hasLibraryMatches) }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // "ALL" or MediaType.name
    var selectedDate by remember { mutableStateOf(today) }

    val scopeFilteredReleases = remember(releases, onlyLibrary, selectedTypeFilter) {
        releases.filter { item ->
            (!onlyLibrary || item.localMediaId != null) &&
                (selectedTypeFilter == "ALL" || item.mediaType.name == selectedTypeFilter)
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
        containerColor = AmoledBlack,
        contentColor = TextPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 14.dp, bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

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
                        text = "14-day schedule across Anime, TV, Movies, Comics, Manga, Novels, Books & Games",
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

            // Dynamic Category Filter Chips across all present MediaTypes
            val categoryChips = remember(releases, onlyLibrary) {
                val base = releases.filter { !onlyLibrary || it.localMediaId != null }
                val presentTypes = listOf(
                    MediaType.ANIME,
                    MediaType.SHOW,
                    MediaType.MOVIE,
                    MediaType.COMIC,
                    MediaType.MANGA,
                    MediaType.NOVEL,
                    MediaType.BOOK,
                    MediaType.GAME,
                    MediaType.VISUAL_NOVEL
                )
                val out = mutableListOf("ALL" to "All (${base.size})")
                presentTypes.forEach { t ->
                    val count = base.count { it.mediaType == t }
                    if (count > 0 || selectedTypeFilter == t.name) {
                        out.add(t.name to "${t.displayName} ($count)")
                    }
                }
                out
            }
            // Category Underline Tabs (Option A)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                categoryChips.forEach { (key, label) ->
                    val selected = selectedTypeFilter == key
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .clickable { selectedTypeFilter = key }
                            .padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) TextPrimary else TextMuted,
                            maxLines = 1,
                            softWrap = false
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

            // My Library vs All Airing underline tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // 14-Day Scrollable Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                windowDays.forEach { day ->
                    val isSelected = day == selectedDate
                    val isToday = day == today
                    val hasEvent = day in datesWithEvents
                    Column(
                        modifier = Modifier
                            .width(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isSelected -> AccentGold
                                    isToday -> AccentGold.copy(alpha = 0.12f)
                                    else -> Color.White.copy(alpha = 0.04f)
                                }
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
                            color = if (isSelected) OnAccentColor else TextSecondary
                        )
                        Text(
                            text = day.dayOfMonth.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) OnAccentColor else TextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        hasEvent && isSelected -> OnAccentColor
                                        hasEvent -> AccentGold
                                        else -> Color.Transparent
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
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(dayReleases, key = { idx, it -> "${it.id}_$idx" }) { index, item ->
                        val localId = item.localMediaId
                        val localTime = remember(item.airingAtEpochSec) {
                            Instant.ofEpochSecond(item.airingAtEpochSec).atZone(zone).format(timeFormatter)
                        }
                        val countdown = remember(item.airingAtEpochSec) {
                            formatCountdown(item.airingAtEpochSec)
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (localId != null) onOpenLocalMedia(localId) else onPreviewRelease(item)
                                    }
                                    .padding(vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                var useFallbackCover by remember(item.id, item.coverUrl, item.fallbackCoverUrl) { mutableStateOf(false) }
                                var coverFailed by remember(item.id, item.coverUrl, item.fallbackCoverUrl) { mutableStateOf(false) }
                                val activeCoverUrl = remember(item.coverUrl, item.fallbackCoverUrl, useFallbackCover) {
                                    val primary = item.coverUrl.trim()
                                    val secondary = item.fallbackCoverUrl?.trim().orEmpty()
                                    when {
                                        !useFallbackCover && primary.isNotBlank() -> primary
                                        secondary.isNotBlank() -> secondary
                                        else -> ""
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .width(46.dp)
                                        .aspectRatio(0.68f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceCard),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (activeCoverUrl.isNotBlank() && !coverFailed) {
                                        AsyncImage(
                                            model = remember(activeCoverUrl) { ArtworkModelResolver.toCoilModel(activeCoverUrl) },
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                            onError = {
                                                if (!useFallbackCover && !item.fallbackCoverUrl.isNullOrBlank()) {
                                                    useFallbackCover = true
                                                } else {
                                                    coverFailed = true
                                                }
                                            }
                                        )
                                    } else {
                                        Icon(
                                            imageVector = when (item.mediaType) {
                                                MediaType.MOVIE -> Icons.Default.Movie
                                                MediaType.SHOW -> Icons.Default.Tv
                                                MediaType.GAME -> Icons.Default.SportsEsports
                                                MediaType.BOOK, MediaType.COMIC -> Icons.Default.MenuBook
                                                MediaType.MANGA, MediaType.NOVEL, MediaType.FANFICTION -> Icons.Default.AutoStories
                                                else -> Icons.Default.PlayCircleOutline
                                            },
                                            contentDescription = null,
                                            tint = TextMuted.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

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
                                        when {
                                            item.mediaType == MediaType.SHOW && item.seasonNumber != null && item.episodeNumber != null ->
                                                append("S%02dE%02d".format(item.seasonNumber, item.episodeNumber))
                                            item.mediaType == MediaType.COMIC && item.episodeNumber != null ->
                                                append("Issue #${item.episodeNumber}")
                                            item.mediaType == MediaType.MANGA && !item.chapterLabel.isNullOrBlank() ->
                                                append("Ch. ${item.chapterLabel}")
                                            item.episodeNumber != null ->
                                                append("Ep ${item.episodeNumber}")
                                            else -> append(item.mediaType.displayName)
                                        }
                                        if (item.formatLabel.isNotBlank()) {
                                            append(" \u00B7 ")
                                            append(item.formatLabel)
                                        }
                                    },
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = if (item.isDateOnly) countdown else "$localTime \u00B7 $countdown",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentGold
                                )
                            }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (localId != null) Color.White.copy(alpha = 0.06f)
                                            else AccentGold
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
                                            tint = if (localId != null) AccentGold else OnAccentColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (localId != null) "In Library" else "Add",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (localId != null) TextPrimary else OnAccentColor
                                        )
                                    }
                                }
                            }
                            if (index < dayReleases.lastIndex) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.08f),
                                    thickness = 0.6.dp
                                )
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
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) TextPrimary else TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) AccentGold else Color.Transparent)
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
