package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType

data class NetworkProductionCompany(
    val id: Long? = null,
    val name: String,
    val logoUrl: String? = null,
    val kind: String = "network" // "network" or "company"
)

/**
 * 1:1 Nuvio Networks & Production Section:
 * Displays network/studio logos or typography chips that open the filmography catalog browser.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailNetworksSection(
    item: MediaItemEntity,
    modifier: Modifier = Modifier,
    companies: List<NetworkProductionCompany> = emptyList(),
    onCompanyClick: ((NetworkProductionCompany) -> Unit)? = null
) {
    val isSeries = item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME
    val sectionTitle = if (isSeries) "Networks" else "Production"

    val displayItems = rememberDisplayCompanies(item, companies)
    if (displayItems.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = sectionTitle,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayItems.forEach { company ->
                ProductionChip(
                    item = company,
                    onClick = if (onCompanyClick != null) {
                        { onCompanyClick(company) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun ProductionChip(
    item: NetworkProductionCompany,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE6F5F5F5))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!item.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.logoUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .widthIn(min = 36.dp, max = 74.dp)
                    .height(22.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = item.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF222222),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun rememberDisplayCompanies(
    item: MediaItemEntity,
    passedCompanies: List<NetworkProductionCompany>
): List<NetworkProductionCompany> {
    if (passedCompanies.isNotEmpty()) return passedCompanies.take(6)

    val studio = item.studioOrNetwork?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
    return studio.split(",").map { name ->
        NetworkProductionCompany(
            name = name.trim(),
            kind = if (item.mediaType == MediaType.SHOW) "network" else "company"
        )
    }.take(6)
}
