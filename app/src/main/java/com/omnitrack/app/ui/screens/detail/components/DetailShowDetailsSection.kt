package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

@Composable
fun DetailShowDetailsSection(
    item: MediaItemEntity,
    modifier: Modifier = Modifier
) {
    val detailsList = mutableListOf<Pair<String, String>>()
    if (!item.airStatus.isNullOrBlank()) {
        detailsList.add("Status" to item.airStatus)
    }
    if (!item.releaseYear.isNullOrBlank()) {
        detailsList.add("Release Year" to item.releaseYear)
    }
    if (item.runtimeMinutes != null && item.runtimeMinutes > 0) {
        val hrs = item.runtimeMinutes / 60
        val mins = item.runtimeMinutes % 60
        val formatted = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
        detailsList.add("Runtime" to formatted)
    }
    if (!item.contentRating.isNullOrBlank()) {
        detailsList.add("Content Rating" to item.contentRating)
    }
    if (!item.studioOrNetwork.isNullOrBlank()) {
        detailsList.add("Studio / Network" to item.studioOrNetwork)
    }

    if (detailsList.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Details & Production",
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF121212).copy(alpha = 0.75f))
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                detailsList.forEachIndexed { index, (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = TextMuted,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = value,
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (index < detailsList.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.05f))
                        )
                    }
                }
            }
        }
    }
}
