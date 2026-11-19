package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.R
import com.omnitrack.app.data.db.entity.GameEntryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.GameCompletionTier
import com.omnitrack.app.data.model.GameDetailMeta
import com.omnitrack.app.data.model.GameEntryType
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import java.util.Locale
import kotlin.math.roundToInt

private data class GamePlatformOption(
    val label: String,
    val iconRes: Int
)

private val GAME_PLATFORM_OPTIONS = listOf(
    GamePlatformOption("PC", R.drawable.ic_platform_pc),
    GamePlatformOption("Steam Deck", R.drawable.ic_platform_steam_deck),
    GamePlatformOption("PS5", R.drawable.ic_platform_ps5),
    GamePlatformOption("Switch", R.drawable.ic_platform_switch),
    GamePlatformOption("Xbox", R.drawable.ic_platform_xbox),
    GamePlatformOption("Mobile", R.drawable.ic_platform_mobile),
    GamePlatformOption("Retro", R.drawable.ic_platform_retro)
)

private val COMPLETION_TIER_TABS = listOf(
    null to "Playing",
    GameCompletionTier.MAIN.name to "Main Story",
    GameCompletionTier.MAIN_PLUS.name to "Main + Sides",
    GameCompletionTier.COMPLETIONIST.name to "100%",
    GameCompletionTier.PLATINUM.name to "Platinum",
    GameCompletionTier.ENDLESS.name to "Endless"
)

private fun formatHltbHours(hours: Double?): String {
    if (hours == null || hours <= 0.0) return "—"
    return if (hours >= 100.0) "${hours.roundToInt()}h" else String.format(Locale.US, "%.1fh", hours)
}

internal fun formatGameEntryTypeLabel(rawType: String): String = GameEntryType.fromName(rawType).label

private fun gameEntryShortCode(rawType: String): String = when (GameEntryType.fromName(rawType)) {
    GameEntryType.BASE_GAME -> "BASE"
    GameEntryType.EXPANSION -> "EXP"
    GameEntryType.DLC -> "DLC"
    GameEntryType.MILESTONE -> "100%"
    GameEntryType.PLAYTHROUGH -> "RUN"
}

@Composable
fun DetailGameTrackingSection(
    item: MediaItemEntity,
    gameMeta: GameDetailMeta,
    entries: List<GameEntryEntity>,
    onStepPlaytime: (Double) -> Unit,
    onSetExactPlaytime: (Double) -> Unit,
    onSelectCompletionTier: (String?) -> Unit,
    onTogglePlatform: (String) -> Unit,
    onToggleEntryCompleted: (GameEntryEntity) -> Unit,
    onAddCustomEntry: (name: String, entryType: String) -> Unit,
    onOpenEntryDetail: (GameEntryEntity) -> Unit,
    onSyncSteam: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showAddBar by remember { mutableStateOf(false) }
    var newEntryName by remember { mutableStateOf("") }
    var newEntryType by remember { mutableStateOf(GameEntryType.PLAYTHROUGH.name) }
    var showHoursDialog by remember { mutableStateOf(false) }
    var hoursInput by remember(item.playtimeHours) {
        mutableStateOf(String.format(Locale.US, "%.1f", item.playtimeHours))
    }

    val hltb = gameMeta.hltb
    val hltbTargetHours = hltb.extraHours ?: hltb.mainHours ?: hltb.completeHours ?: 0.0
    val selectedPlatformsSet = remember(item.platforms, item.platform) {
        val fromCsv = item.platforms?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val fromSingle = item.platform?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        (fromCsv + fromSingle).toSet()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Playtime & Time-to-Beat Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Playtime & Time to Beat",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.3).sp
            )
            if (onSyncSteam != null && (item.steamAppId != null || gameMeta.steamAppId != null)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSyncSteam() }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync Steam",
                        tint = Color(0xFF66C0F4),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Sync Steam",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF66C0F4)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.clickable {
                    hoursInput = String.format(Locale.US, "%.1f", item.playtimeHours)
                    showHoursDialog = true
                },
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.1fh", item.playtimeHours),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                if (hltbTargetHours > 0.0) {
                    val pct = ((item.playtimeHours / hltbTargetHours) * 100.0).roundToInt().coerceAtLeast(0)
                    Text(
                        text = String.format(Locale.US, "/ ~%.1fh avg (%d%%)", hltbTargetHours, pct),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                } else {
                    Text(
                        text = "logged · tap to edit",
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
            }

            // Bare typographic steppers
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "−1h",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.80f),
                    modifier = Modifier
                        .clickable { onStepPlaytime(-1.0) }
                        .padding(vertical = 2.dp)
                )
                Text(
                    text = "+1h",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clickable { onStepPlaytime(1.0) }
                        .padding(vertical = 2.dp)
                )
                Text(
                    text = "+5h",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold,
                    modifier = Modifier
                        .clickable { onStepPlaytime(5.0) }
                        .padding(vertical = 2.dp)
                )
            }
        }

        if (hltbTargetHours > 0.0) {
            val fraction = (item.playtimeHours / hltbTargetHours).toFloat().coerceIn(0f, 1f)
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

        // 2. 3-Column Hairline HLTB Strip
        if (hltb.hasAny) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HltbMetricColumn(label = "MAIN STORY", value = formatHltbHours(hltb.mainHours), modifier = Modifier.weight(1f))
                Box(modifier = Modifier.width(1.dp).height(26.dp).background(Color.White.copy(alpha = 0.10f)))
                HltbMetricColumn(label = "MAIN + SIDES", value = formatHltbHours(hltb.extraHours), modifier = Modifier.weight(1f))
                Box(modifier = Modifier.width(1.dp).height(26.dp).background(Color.White.copy(alpha = 0.10f)))
                HltbMetricColumn(label = "100% COMP", value = formatHltbHours(hltb.completeHours), modifier = Modifier.weight(1f))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }

        // 3. Completion Tier Typographic Underline Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            COMPLETION_TIER_TABS.forEach { (tierKey, label) ->
                val isSelected = (item.gameCompletionTier?.takeIf { it.isNotBlank() && it != GameCompletionTier.NONE.name } == tierKey) ||
                    (tierKey == null && (item.gameCompletionTier.isNullOrBlank() || item.gameCompletionTier == GameCompletionTier.NONE.name))
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onSelectCompletionTier(tierKey) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.48f),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (isSelected) Color.White else Color.Transparent)
                    )
                }
            }
        }

        // 4. Inline Monochrome Platform Icon Selector (Zero pills)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GAME_PLATFORM_OPTIONS.forEach { plat ->
                val isSelected = selectedPlatformsSet.any { it.equals(plat.label, ignoreCase = true) }
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onTogglePlatform(plat.label) }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Image(
                            painter = painterResource(plat.iconRes),
                            contentDescription = plat.label,
                            colorFilter = ColorFilter.tint(
                                if (isSelected) Color.White else Color.White.copy(alpha = 0.42f)
                            ),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = plat.label,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.48f),
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (isSelected) AccentJade else Color.Transparent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 5. DLCs, Expansions & Playthroughs Checklist
        val completedCount = entries.count { it.isCompleted }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "DLCs & Playthroughs",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp
                )
                if (entries.isNotEmpty()) {
                    Text(
                        text = "($completedCount / ${entries.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }
            Text(
                text = if (showAddBar) "Close" else "+ Log Entry",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clickable { showAddBar = !showAddBar }
                    .padding(vertical = 2.dp)
            )
        }

        if (showAddBar) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    GameEntryType.entries.forEach { t ->
                        val selected = newEntryType == t.name
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .clickable { newEntryType = t.name },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = t.label,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.50f),
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(if (selected) Color.White else Color.Transparent)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = newEntryName,
                            onValueChange = { newEntryName = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val trimmed = newEntryName.trim()
                                if (trimmed.isNotEmpty()) {
                                    onAddCustomEntry(trimmed, newEntryType)
                                    newEntryName = ""
                                    showAddBar = false
                                }
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            decorationBox = { inner ->
                                if (newEntryName.isEmpty()) {
                                    Text(
                                        text = "Add expansion, NG+ run, or milestone...",
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 13.sp
                                    )
                                }
                                inner()
                            }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.24f))
                        )
                    }
                    Text(
                        text = "Add",
                        color = AccentJade,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                val trimmed = newEntryName.trim()
                                if (trimmed.isNotEmpty()) {
                                    onAddCustomEntry(trimmed, newEntryType)
                                    newEntryName = ""
                                    showAddBar = false
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenEntryDetail(entry) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = gameEntryShortCode(entry.entryType),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White.copy(alpha = 0.80f),
                            letterSpacing = 0.4.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.entryName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        val subParts = buildList {
                            add(formatGameEntryTypeLabel(entry.entryType))
                            val tier = GameCompletionTier.fromName(entry.completionTier)
                            if (tier != GameCompletionTier.NONE) add(tier.label)
                            entry.hoursSpent?.takeIf { it > 0.0 }?.let {
                                add(String.format(Locale.US, "%.1fh", it))
                            }
                            entry.difficulty?.takeIf { it.isNotBlank() }?.let { add(it) }
                            entry.userRating?.let { add(String.format(Locale.US, "★ %.1f", it)) }
                            if (entry.isBookmarked) add("Bookmarked")
                            if (!entry.userNote.isNullOrBlank()) add("Note")
                        }
                        Text(
                            text = subParts.joinToString(" · "),
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (entry.isCompleted) AccentJade.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.22f))
                            .border(
                                width = 1.4.dp,
                                color = if (entry.isCompleted) AccentJade else Color.White.copy(alpha = 0.72f),
                                shape = CircleShape
                            )
                            .clickable { onToggleEntryCompleted(entry) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (entry.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = AccentJade,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (index < entries.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                }
            }
        }
    }

    if (showHoursDialog) {
        AlertDialog(
            onDismissRequest = { showHoursDialog = false },
            containerColor = AmoledBlack,
            title = {
                Text("Playtime (Hours)", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = hoursInput,
                    onValueChange = { next ->
                        if (next.length <= 7 && next.all { it.isDigit() || it == '.' }) {
                            hoursInput = next
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = hoursInput.toDoubleOrNull()
                    if (parsed != null) onSetExactPlaytime(parsed)
                    showHoursDialog = false
                }) {
                    Text("Save", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHoursDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

@Composable
private fun HltbMetricColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
