package com.omnitrack.app.ui.screens.detail.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.model.VnReleaseItem
import com.omnitrack.app.data.model.formatVndbVoicedLabel

@Composable
fun DetailVnReleasesSection(
    releases: List<VnReleaseItem>,
    modifier: Modifier = Modifier
) {
    if (releases.isEmpty()) return
    val uriHandler = LocalUriHandler.current

    val defaultTab = remember(releases) {
        when {
            releases.any { it.isOfficial && !it.isPatch && "en" in it.languages } -> "EN_OFFICIAL"
            releases.any { it.isPatch && "en" in it.languages } -> "FAN_PATCH"
            else -> "ALL"
        }
    }
    var selectedTab by remember(defaultTab) { mutableStateOf(defaultTab) }

    val filtered = remember(releases, selectedTab) {
        when (selectedTab) {
            "EN_OFFICIAL" -> releases.filter { it.isOfficial && !it.isPatch && "en" in it.languages }
            "FAN_PATCH" -> releases.filter { it.isPatch }
            "JP" -> releases.filter { "ja" in it.languages && !it.isPatch }
            else -> releases
        }.take(20)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Releases & Translations",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        // Underline typographic tabs (Zero pills)
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf(
                    "EN_OFFICIAL" to "Official EN",
                    "FAN_PATCH" to "Fan Patch",
                    "JP" to "Japanese",
                    "ALL" to "All (${releases.size})"
                ).forEach { (key, label) ->
                    val active = selectedTab == key
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .clickable { selectedTab = key },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            color = if (active) Color.White else Color.White.copy(alpha = 0.48f),
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (active) Color.White else Color.Transparent)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }

        if (filtered.isEmpty()) {
            Text(
                text = "No matching releases in this tab",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { rel ->
                    Column(
                        modifier = Modifier
                            .width(242.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .clickable(enabled = !rel.primaryUrl.isNullOrBlank()) {
                                rel.primaryUrl?.let { url ->
                                    try { uriHandler.openUri(url) } catch (_: Exception) {}
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = rel.title,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val pubLine = listOfNotNull(
                            rel.released?.takeIf { it.isNotBlank() },
                            rel.producers.take(2).joinToString(" / ").takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        if (pubLine.isNotBlank()) {
                            Text(
                                text = pubLine,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val specs = buildList {
                            rel.platforms.take(3).joinToString("/").takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                            if (rel.isPatch) add("Fan Patch")
                            formatVndbVoicedLabel(rel.voicedLevel)?.let(::add)
                            rel.minAge?.let { add(if (it == 0) "All Ages" else "${it}+") }
                            rel.engine?.takeIf { it.isNotBlank() }?.let(::add)
                        }.joinToString(" · ")
                        if (specs.isNotBlank()) {
                            Text(
                                text = specs,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.88f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
