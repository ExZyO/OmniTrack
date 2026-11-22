package com.omnitrack.app.ui.screens.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.ui.theme.AccentGold
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityHeatmapSheet(
    watchedTimestamps90d: List<Long>,
    allMedia: List<MediaCardSummary>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val startDate = remember(today) { today.minusDays(89) }
    val allDates = remember(startDate) { (0 until 90).map { startDate.plusDays(it.toLong()) } }

    val dailyCounts = remember(watchedTimestamps90d, allMedia, startDate, today, zone) {
        val combinedTimestamps = (watchedTimestamps90d + allMedia.map { it.lastUpdated }).distinct()
        val counts = HashMap<LocalDate, Int>()
        combinedTimestamps.forEach { ts ->
            if (ts > 0L) {
                val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
                if (!date.isBefore(startDate) && !date.isAfter(today)) {
                    counts[date] = (counts[date] ?: 0) + 1
                }
            }
        }
        counts
    }

    val activeDaysCount = remember(dailyCounts) { dailyCounts.values.count { it > 0 } }
    val totalUpdatesCount = remember(dailyCounts) { dailyCounts.values.sum() }
    val completedCount = remember(allMedia) { allMedia.count { it.status == TrackingStatus.COMPLETED } }
    val currentStreak = remember(dailyCounts, today) {
        var streak = 0
        var cursor = if ((dailyCounts[today] ?: 0) > 0) today else today.minusDays(1)
        while (streak < 90 && (dailyCounts[cursor] ?: 0) > 0) {
            streak++
            cursor = cursor.minusDays(1)
        }
        streak
    }

    var selectedDay by remember { mutableStateOf(today) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US) }

    val typeCounts = remember(allMedia) {
        MediaType.filterTabs
            .filter { it != MediaType.ALL }
            .map { type -> type to allMedia.count { it.mediaType == type } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
    }
    val maxTypeCount = remember(typeCounts) { typeCounts.maxOfOrNull { it.second } ?: 1 }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = "Activity & 90-Day Insights",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Tap any square to inspect daily tracking activity",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // 4 Summary Metric Boxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeatmapMetricCard(
                    value = "$activeDaysCount",
                    label = "Active Days",
                    modifier = Modifier.weight(1f)
                )
                HeatmapMetricCard(
                    value = "${currentStreak}d",
                    label = "Streak",
                    modifier = Modifier.weight(1f),
                    accentValue = true
                )
                HeatmapMetricCard(
                    value = "$totalUpdatesCount",
                    label = "Updates",
                    modifier = Modifier.weight(1f)
                )
                HeatmapMetricCard(
                    value = "$completedCount",
                    label = "Completed",
                    modifier = Modifier.weight(1f)
                )
            }

            // 15x6 Heatmap Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LAST 90 DAYS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    val selectedCount = dailyCounts[selectedDay] ?: 0
                    Text(
                        text = "${selectedDay.format(dateFormatter)} \u00B7 $selectedCount update${if (selectedCount == 1) "" else "s"}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentGold
                    )
                }

                // 6 rows x 15 columns
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (rowIdx in 0 until 6) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (colIdx in 0 until 15) {
                                val cellIndex = colIdx * 6 + rowIdx
                                val date = allDates[cellIndex]
                                val count = dailyCounts[date] ?: 0
                                val cellColor = heatmapCellColor(count)
                                val isSelected = date == selectedDay
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(cellColor)
                                        .then(
                                            if (isSelected) Modifier.border(1.2.dp, TextPrimary, RoundedCornerShape(3.dp))
                                            else Modifier
                                        )
                                        .clickable { selectedDay = date }
                                )
                            }
                        }
                    }
                }

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Less",
                        fontSize = 10.5.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    listOf(0, 1, 2, 4, 7).forEach { levelCount ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(11.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(heatmapCellColor(levelCount))
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "More",
                        fontSize = 10.5.sp,
                        color = TextSecondary
                    )
                }
            }

            // Library Category Distribution
            if (typeCounts.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "LIBRARY BREAKDOWN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    typeCounts.forEach { (type, count) ->
                        val ratio = (count.toFloat() / maxTypeCount.toFloat()).coerceIn(0.05f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = type.displayName,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "$count",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(DarkSurfaceCard)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(ratio)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(AccentGold)
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
private fun HeatmapMetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accentValue: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (accentValue) AccentGold else TextPrimary
        )
        Text(
            text = label,
            fontSize = 10.5.sp,
            color = TextSecondary
        )
    }
}

private fun heatmapCellColor(count: Int): Color = when {
    count <= 0 -> Color(0xFF1B1914)
    count == 1 -> Color(0xFF4D4023)
    count in 2..3 -> Color(0xFF8A7139)
    count in 4..6 -> Color(0xFFBFA054)
    else -> AccentGold
}
