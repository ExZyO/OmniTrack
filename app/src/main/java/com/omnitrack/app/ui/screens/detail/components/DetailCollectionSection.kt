package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.ui.components.NuvioRailPosterCard
import com.omnitrack.app.ui.theme.TextPrimary

/**
 * 1:1 Nuvio Franchise Movie Collection Rail (e.g. "The Dark Knight Collection", "Harry Potter Collection").
 * Placed strictly between Details and More Like This on the movie detail page.
 */
@Composable
fun DetailCollectionSection(
    collectionName: String,
    items: List<RecommendationItem>,
    onSelectItem: (RecommendationItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = collectionName,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "${items.size} movies",
                fontSize = 11.5.sp,
                color = Color.White
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items.forEach { item ->
                NuvioRailPosterCard(
                    title = item.title,
                    coverUrl = item.coverUrl,
                    subtitle = item.releaseYear,
                    rating = item.rating,
                    onClick = { onSelectItem(item) },
                    cardWidth = 135.dp,
                    cardHeight = 202.dp
                )
            }
        }
    }
}
