package com.omnitrack.app.ui.screens.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.preferences.DiscoverCustomizationSettings
import com.omnitrack.app.data.preferences.DiscoverLayoutStyle
import com.omnitrack.app.data.preferences.DiscoverShelfCatalog
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Full "Disable Anything in Discovery" + Layout A/B/C customizer sheet.
 * Allows hiding individual UI widgets, whole category filter tabs, and any individual
 * Discover shelf rail (with zero network calls fired for disabled shelves).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverCustomizerSheet(
    preferenceManager: PreferenceManager,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val settings by preferenceManager.discoverCustomizationSettings.collectAsState(
        initial = DiscoverCustomizationSettings()
    )
    var selectedTab by remember { mutableIntStateOf(0) }

    val allCategories = remember {
        listOf(
            MediaType.ALL,
            MediaType.ANIME,
            MediaType.MANGA,
            MediaType.COMIC,
            MediaType.NOVEL,
            MediaType.BOOK,
            MediaType.SHOW,
            MediaType.MOVIE,
            MediaType.GAME,
            MediaType.VISUAL_NOVEL
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AmoledBlack)
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 14.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.24f))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Customize Discover",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Layout A/B/C & disable any banner, category, or shelf",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { scope.launch { preferenceManager.resetDiscoverDefaults() } },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Defaults",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Done",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Borderless underline tab bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Layout & UI", "Categories", "Shelves").forEachIndexed { idx, label ->
                    val selected = selectedTab == idx
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .clickable { selectedTab = idx },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) TextPrimary else TextSecondary
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        item(key = "disc_layout_header") {
                            Text(
                                text = "DISCOVER PRESENTATION STYLE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.9.sp
                            )
                        }
                        items(DiscoverLayoutStyle.entries, key = { "layout_${it.name}" }) { style ->
                            val isSelected = settings.layoutStyle == style
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) AccentGold.copy(alpha = 0.09f) else Color.Transparent)
                                        .clickable { scope.launch { preferenceManager.updateDiscoverLayoutStyle(style) } }
                                        .padding(horizontal = 8.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = style.displayName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) AccentGold else TextPrimary
                                        )
                                        Text(
                                            text = style.description,
                                            fontSize = 11.5.sp,
                                            color = TextSecondary,
                                            lineHeight = 15.sp
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = AccentGold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                            }
                        }

                        item(key = "disc_ui_header") {
                            Column(modifier = Modifier.padding(top = 6.dp)) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "SHOW OR HIDE DISCOVER ELEMENTS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    letterSpacing = 0.9.sp
                                )
                            }
                        }

                        val uiToggles = listOf(
                            Triple("Spotlight / Hero Banner", "Featured visual showcase above shelves (Layouts A & B)", settings.showSpotlightBanner to { v: Boolean -> preferenceManager.setDiscoverShowSpotlight(v) }),
                            Triple("Quick-Search Inspiration Chips", "Curated one-tap search pills below category tabs", settings.showQuickSearchChips to { v: Boolean -> preferenceManager.setDiscoverShowQuickChips(v) }),
                            Triple("Secondary Sub-Filter Bars", "Format, origin, publisher & platform sub-filters", settings.showSubFilters to { v: Boolean -> preferenceManager.setDiscoverShowSubFilters(v) }),
                            Triple("\"In Your Library\" Spotlight Rail", "Bottom rail highlighting entries you already track", settings.showInYourLibraryShelf to { v: Boolean -> preferenceManager.setDiscoverShowInLibrary(v) }),
                            Triple("\"Create Custom Entry\" Card", "Manual entry fallback inside search results", settings.showCustomEntryCard to { v: Boolean -> preferenceManager.setDiscoverShowCustomEntry(v) }),
                            Triple("AI Smart Search Toggle Pill", "Inline AI natural-language search switch in search bar", settings.showAiToggle to { v: Boolean -> preferenceManager.setDiscoverShowAiToggle(v) })
                        )
                        items(uiToggles, key = { "uitog_${it.first}" }) { (title, subtitle, statePair) ->
                            val (checked, setter) = statePair
                            DiscoverSwitchRow(
                                title = title,
                                subtitle = subtitle,
                                checked = checked,
                                onCheckedChange = { next -> scope.launch { setter(next) } }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
                        }
                    }

                    1 -> {
                        item(key = "disc_cat_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "VISIBLE CATEGORY TABS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary,
                                        letterSpacing = 0.9.sp
                                    )
                                    Text(
                                        text = "Hidden verticals are removed from Discover chips & All Verticals overview",
                                        fontSize = 11.5.sp,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = "Enable All",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { scope.launch { preferenceManager.setDiscoverAllCategoriesEnabled() } }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        val activeCount = allCategories.count { it.name !in settings.disabledCategories }
                        items(allCategories, key = { "cat_${it.name}" }) { cat ->
                            val isEnabled = cat.name !in settings.disabledCategories
                            val isLastActive = isEnabled && activeCount <= 1
                            DiscoverSwitchRow(
                                title = if (cat == MediaType.ALL) "All Verticals" else cat.displayName,
                                subtitle = if (isLastActive) "At least 1 tab stays visible" else if (isEnabled) "Shown in Discover" else "Hidden",
                                checked = isEnabled,
                                onCheckedChange = { next ->
                                    if (!next && activeCount <= 1) return@DiscoverSwitchRow
                                    scope.launch { preferenceManager.setDiscoverCategoryEnabled(cat, next) }
                                }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
                        }
                    }

                    else -> {
                        item(key = "disc_shelf_intro") {
                            Text(
                                text = "Toggle individual Discover rails on or off. Disabled shelves won't load in the background.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        allCategories.forEach { cat ->
                            val catShelves = DiscoverShelfCatalog.shelvesForCategory(cat)
                            if (catShelves.isNotEmpty()) {
                                val anyEnabled = catShelves.any { it.id !in settings.disabledShelves }
                                item(key = "shelf_grp_${cat.name}") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (cat == MediaType.ALL) "ALL VERTICALS OVERVIEW" else cat.displayName.uppercase(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentGold,
                                            letterSpacing = 0.9.sp
                                        )
                                        Text(
                                            text = if (anyEnabled) "Turn All Off" else "Turn All On",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextSecondary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    scope.launch {
                                                        preferenceManager.setDiscoverVerticalShelvesEnabled(cat, !anyEnabled)
                                                    }
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                items(catShelves, key = { "shf_${it.id}" }) { def ->
                                    val enabled = def.id !in settings.disabledShelves
                                    DiscoverSwitchRow(
                                        title = def.title,
                                        subtitle = null,
                                        checked = enabled,
                                        onCheckedChange = { next ->
                                            scope.launch { preferenceManager.setDiscoverShelfEnabled(def.id, next) }
                                        }
                                    )
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
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
private fun DiscoverSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (checked) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 14.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnAccentColor,
                checkedTrackColor = AccentGold,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}
