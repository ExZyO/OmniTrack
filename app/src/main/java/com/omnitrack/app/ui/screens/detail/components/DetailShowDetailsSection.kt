package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType

/**
 * 1:1 Nuvio Unboxed Show Details Section:
 * Clean vertical list with hairline dividers, zero rectangular enclosing box.
 * Matches Nuvio's exact fields: Status, Release Info, Runtime, Certification, Origin Country, Original Language, and Production.
 */
@Composable
fun DetailShowDetailsSection(
    item: MediaItemEntity,
    modifier: Modifier = Modifier
) {
    val isSeries = item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME
    val title = when (item.mediaType) {
        MediaType.SHOW, MediaType.ANIME -> "Show Details"
        MediaType.MOVIE -> "Movie Details"
        MediaType.GAME -> "Game Details"
        MediaType.VISUAL_NOVEL -> "Visual Novel Details"
        MediaType.BOOK -> "Book Details"
        MediaType.MANGA, MediaType.NOVEL -> "Reading Details"
        else -> "Details"
    }

    val rows = buildList {
        // 1. Status
        if (!item.airStatus.isNullOrBlank()) {
            add("Status" to item.airStatus)
        }

        // 2. Release Info
        if (item.totalProgress != null && item.totalProgress > 0 && isSeries) {
            add("Total Episodes" to "${item.totalProgress.toInt()} Episodes")
        }

        // 3. Runtime
        if (item.runtimeMinutes != null && item.runtimeMinutes > 0) {
            val hrs = item.runtimeMinutes / 60
            val mins = item.runtimeMinutes % 60
            val formatted = if (hrs > 0 && mins > 0) "${hrs}h ${mins}m" else if (hrs > 0) "${hrs}h" else "${mins}m"
            add("Runtime" to formatted)
        }

        // 4. Certification
        if (!item.contentRating.isNullOrBlank()) {
            add("Certification" to item.contentRating)
        }

        // 5. Origin Country / Platform
        if (!item.platforms.isNullOrBlank()) {
            add("Platform" to item.platforms)
        }

        // 6. Production / Network
        if (!item.studioOrNetwork.isNullOrBlank()) {
            add("Production" to item.studioOrNetwork)
        }
    }

    if (rows.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = value,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        fontSize = 14.sp,
                        color = Color.White,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (index < rows.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.10f))
                    )
                }
            }
        }
    }
}
