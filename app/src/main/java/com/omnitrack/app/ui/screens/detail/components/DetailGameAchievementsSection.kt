package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.GameDetailMeta
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DetailGameAchievementsSection(
    item: MediaItemEntity,
    gameMeta: GameDetailMeta,
    onUpdateManualAchievements: (unlocked: Int, total: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val achievements = gameMeta.achievements
    val unlockedCount = gameMeta.achievementsUnlocked ?: item.steamAchievementsUnlocked ?: 0
    val totalCount = gameMeta.achievementsTotal ?: item.steamAchievementsTotal ?: achievements.size

    var selectedTab by remember { mutableStateOf("ALL") }
    var showAll by remember { mutableStateOf(false) }
    var revealedHiddenApiNames by remember(item.id) { mutableStateOf<Set<String>>(emptySet()) }
    var showManualDialog by remember { mutableStateOf(false) }
    var manualUnlockedText by remember(unlockedCount) { mutableStateOf(unlockedCount.toString()) }
    var manualTotalText by remember(totalCount) { mutableStateOf(totalCount.toString()) }

    val filteredAchievements = remember(achievements, selectedTab) {
        when (selectedTab) {
            "UNLOCKED" -> achievements.filter { it.isUnlocked }
            "LOCKED" -> achievements.filter { !it.isUnlocked }
            "RARE" -> achievements.filter { (it.globalRarityPct ?: 100f) < 15f }
            else -> achievements
        }
    }
    val visibleAchievements = if (showAll) filteredAchievements else filteredAchievements.take(10)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Achievements & Trophies",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp
                )
                if (totalCount > 0) {
                    val pct = ((unlockedCount * 100.0) / totalCount).roundToInt().coerceIn(0, 100)
                    Text(
                        text = "$unlockedCount / $totalCount ($pct%)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pct >= 100) AccentJade else AccentGold
                    )
                }
            }

            Text(
                text = if (totalCount > 0) "Edit" else "Log Manually",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.78f),
                modifier = Modifier
                    .clickable {
                        manualUnlockedText = unlockedCount.toString()
                        manualTotalText = totalCount.toString()
                        showManualDialog = true
                    }
                    .padding(vertical = 2.dp)
            )
        }

        if (totalCount > 0) {
            val fraction = (unlockedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.14f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .background(if (fraction >= 1f) AccentJade else AccentGold)
                )
            }
        }

        if (achievements.isNotEmpty()) {
            val rareCount = achievements.count { (it.globalRarityPct ?: 100f) < 15f }
            val tabs = buildList {
                add("ALL" to "All (${achievements.size})")
                add("UNLOCKED" to "Unlocked (${achievements.count { it.isUnlocked }})")
                add("LOCKED" to "Locked (${achievements.count { !it.isUnlocked }})")
                if (rareCount > 0) add("RARE" to "Rare <15% ($rareCount)")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                tabs.forEach { (key, label) ->
                    val selected = selectedTab == key
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .clickable {
                                selectedTab = key
                                showAll = false
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.48f),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (selected) Color.White else Color.Transparent)
                        )
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                visibleAchievements.forEachIndexed { idx, ach ->
                    val isMaskedHidden = ach.isHidden && !ach.isUnlocked && ach.apiName !in revealedHiddenApiNames
                    val iconModel = if (ach.isUnlocked) {
                        ach.iconUrl ?: ach.iconGrayUrl
                    } else {
                        ach.iconGrayUrl ?: ach.iconUrl
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isMaskedHidden) {
                                revealedHiddenApiNames = revealedHiddenApiNames + ach.apiName
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!iconModel.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(iconModel)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = ach.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .alpha(if (ach.isUnlocked) 1f else 0.45f)
                                    .then(if (isMaskedHidden) Modifier.blur(6.dp) else Modifier)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = if (ach.isUnlocked) 0.14f else 0.06f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (ach.isUnlocked) "★" else "☆",
                                    color = if (ach.isUnlocked) AccentGold else Color.White.copy(alpha = 0.45f),
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isMaskedHidden) "Hidden Achievement (Tap to reveal)" else ach.displayName,
                                color = if (ach.isUnlocked) Color.White else Color.White.copy(alpha = 0.72f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val descText = ach.description?.takeIf { it.isNotBlank() }
                            if (descText != null) {
                                Text(
                                    text = descText,
                                    color = Color.White.copy(alpha = 0.58f),
                                    fontSize = 11.5.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = if (isMaskedHidden) Modifier.blur(6.dp) else Modifier
                                )
                            }
                        }

                        ach.globalRarityPct?.let { pct ->
                            val isRare = pct < 15f
                            Text(
                                text = String.format(Locale.US, "%.1f%%", pct),
                                color = if (isRare) AccentGold else Color.White.copy(alpha = 0.55f),
                                fontSize = 11.5.sp,
                                fontWeight = if (isRare) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        }
                    }

                    if (idx < visibleAchievements.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                    }
                }
            }

            if (filteredAchievements.size > 10) {
                Text(
                    text = if (showAll) "Show less" else "Show all ${filteredAchievements.size} achievements",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { showAll = !showAll }
                        .padding(vertical = 2.dp)
                )
            }
        }
    }

    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            containerColor = AmoledBlack,
            title = {
                Text("Achievements / Trophies", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = manualUnlockedText,
                        onValueChange = { next ->
                            if (next.length <= 5 && next.all { it.isDigit() }) manualUnlockedText = next
                        },
                        label = { Text("Unlocked", color = Color.White.copy(alpha = 0.65f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                    OutlinedTextField(
                        value = manualTotalText,
                        onValueChange = { next ->
                            if (next.length <= 5 && next.all { it.isDigit() }) manualTotalText = next
                        },
                        label = { Text("Total Available", color = Color.White.copy(alpha = 0.65f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = manualUnlockedText.toIntOrNull() ?: 0
                    val t = manualTotalText.toIntOrNull() ?: u
                    onUpdateManualAchievements(u, t)
                    showManualDialog = false
                }) {
                    Text("Save", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}
