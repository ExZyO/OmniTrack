package com.omnitrack.app.ui.screens.home.views

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.ContinueTrackingStyle
import com.omnitrack.app.data.preferences.HeroBannerBlurLevel
import com.omnitrack.app.data.preferences.HomeHeroSettings
import com.omnitrack.app.data.preferences.HomeHeroSource
import com.omnitrack.app.data.preferences.HomeLayoutDefaults
import com.omnitrack.app.data.preferences.HomeRowItem
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.ui.components.ContinueStyleWireframe
import com.omnitrack.app.ui.components.HeroHeightWireframe
import com.omnitrack.app.ui.components.HeroMetadataLayoutWireframe
import com.omnitrack.app.ui.components.HomeLayoutWireframe
import com.omnitrack.app.ui.components.VisualOptionTile
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeCustomizerSheet(
    preferenceManager: PreferenceManager,
    onDismiss: () -> Unit,
    customCategories: List<CategoryEntity> = emptyList(),
    onOpenManageCategories: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    // Initial null avoids phantom animated jump from defaults to saved order on open
    val savedRowsNullable by preferenceManager.homeRowOrder.collectAsState(initial = null)
    val heroSettings by preferenceManager.homeHeroSettings.collectAsState(initial = HomeHeroSettings())
    var selectedTab by remember { mutableIntStateOf(0) }

    val currentRows = remember { mutableStateListOf<HomeRowItem>() }
    var rowsInitialized by remember { mutableStateOf(false) }
    var isDraggingAny by remember { mutableStateOf(false) }

    LaunchedEffect(savedRowsNullable) {
        val rows = savedRowsNullable ?: return@LaunchedEffect
        if (!rowsInitialized) {
            currentRows.clear()
            currentRows.addAll(rows)
            rowsInitialized = true
        } else if (!isDraggingAny && currentRows.toList() != rows) {
            currentRows.clear()
            currentRows.addAll(rows)
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = currentRows.indexOfFirst { it.key.name == from.key }
        val toIdx = currentRows.indexOfFirst { it.key.name == to.key }
        if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
            currentRows.apply { add(toIdx, removeAt(fromIdx)) }
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

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
                .fillMaxHeight(0.88f)
                .background(AmoledBlack)
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                )
            }

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Home & Hero Customizer",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Configure layout, shelves, card styles & hero banner",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                IconButton(
                    onClick = {
                        currentRows.clear()
                        currentRows.addAll(HomeLayoutDefaults.defaultRows)
                        coroutineScope.launch {
                            preferenceManager.resetHomeAndHeroDefaults()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Defaults",
                        tint = TextSecondary,
                        modifier = Modifier.size(19.dp)
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

            // Option A Underline Tab Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Shelves & Display", "Hero Banner").forEachIndexed { idx, label ->
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

            if (selectedTab == 0) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "home_display_controls") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CustomizerSectionLabel("HOME LAYOUT STYLE")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf(
                                    Triple("EDITORIAL", "Editorial (Option A)", "Hero showcase + poster shelves"),
                                    Triple("COMPACT", "Dense Timeline (C)", "Compact feed + tight rows")
                                ).forEach { (styleKey, label, subtitle) ->
                                    VisualOptionTile(
                                        title = label,
                                        subtitle = subtitle,
                                        isSelected = heroSettings.homeDashboardStyle.equals(styleKey, ignoreCase = true),
                                        modifier = Modifier.weight(1f),
                                        previewHeight = 62.dp,
                                        onClick = {
                                            coroutineScope.launch { preferenceManager.setHomeDashboardStyle(styleKey) }
                                        }
                                    ) {
                                        HomeLayoutWireframe(isEditorial = styleKey == "EDITORIAL")
                                    }
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                            CustomizerSectionLabel("CONTINUE TRACKING STYLE")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ContinueTrackingStyle.entries.forEach { style ->
                                    val shortTitle = when (style) {
                                        ContinueTrackingStyle.CARD -> "16:9 Card"
                                        ContinueTrackingStyle.WIDE -> "Wide Split"
                                        ContinueTrackingStyle.POSTER -> "Poster"
                                    }
                                    VisualOptionTile(
                                        title = shortTitle,
                                        subtitle = null,
                                        isSelected = heroSettings.continueTrackingStyle == style,
                                        modifier = Modifier.weight(1f),
                                        previewHeight = 56.dp,
                                        onClick = {
                                            coroutineScope.launch { preferenceManager.setContinueTrackingStyle(style) }
                                        }
                                    ) {
                                        ContinueStyleWireframe(style = style)
                                    }
                                }
                            }

                            CustomizerSwitchRow(
                                title = "Show Quick +1 on Continue Cards",
                                subtitle = "Quickly log next episode, chapter, issue, or completion from Home cards",
                                checked = heroSettings.showQuickPlusOnContinue,
                                onCheckedChange = { v ->
                                    coroutineScope.launch { preferenceManager.setHomeContinueQuickPlus(v) }
                                }
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                            var sliderWidthDp by remember(heroSettings.shelfPosterWidthDp) {
                                mutableFloatStateOf(heroSettings.shelfPosterWidthDp.toFloat())
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CustomizerSectionLabel("HOME COVER SIZE")
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${sliderWidthDp.roundToInt()}dp",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentGold
                                    )
                                    if (sliderWidthDp.roundToInt() != 114) {
                                        Text(
                                            text = "Reset",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AccentGold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable {
                                                    sliderWidthDp = 114f
                                                    coroutineScope.launch { preferenceManager.setHomeShelfPosterWidthDp(114) }
                                                }
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Slider(
                                    value = sliderWidthDp,
                                    onValueChange = { sliderWidthDp = it },
                                    onValueChangeFinished = {
                                        coroutineScope.launch {
                                            preferenceManager.setHomeShelfPosterWidthDp(sliderWidthDp.roundToInt())
                                        }
                                    },
                                    valueRange = 84f..168f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AccentGold,
                                        activeTrackColor = AccentGold,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.14f)
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Compact (84dp)", fontSize = 10.5.sp, color = TextSecondary)
                                    Text("Default (114dp)", fontSize = 10.5.sp, color = TextSecondary)
                                    Text("Large (168dp)", fontSize = 10.5.sp, color = TextSecondary)
                                }
                            }

                            CustomizerSectionLabel("ITEMS PER SHELF")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(0 to "\u221E Unlimited", 8 to "8 Items", 12 to "12 Items", 20 to "20 Items").forEach { (limit, label) ->
                                    CustomizerChoiceChip(
                                        label = label,
                                        selected = heroSettings.shelfItemLimit == limit,
                                        onClick = {
                                            coroutineScope.launch { preferenceManager.setHomeShelfItemLimit(limit) }
                                        }
                                    )
                                }
                            }

                            CustomizerSectionLabel("DEFAULT HOME CATEGORY")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MediaType.filterTabs.forEach { type ->
                                    CustomizerChoiceChip(
                                        label = if (type == MediaType.ALL) "All Media" else type.displayName,
                                        selected = heroSettings.defaultCategory == type,
                                        onClick = {
                                            coroutineScope.launch { preferenceManager.setHomeDefaultCategory(type) }
                                        }
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                        }
                    }

                    item(key = "home_shelves_section_header") {
                        Text(
                            text = "SHELVES & DISCOVER FEEDS (DRAG \u2261 TO REORDER \u00B7 TOGGLE EYE)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }

                    itemsIndexed(
                        items = currentRows,
                        key = { _, item -> item.key.name }
                    ) { index, item ->
                        ReorderableItem(reorderableState, key = item.key.name) { isBeingDragged ->
                            val isDiscoverRow = item.key.name.startsWith("DISCOVER_")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isBeingDragged) 10f else 1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isBeingDragged) DarkSurfaceElevated else Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (isBeingDragged) AccentGold.copy(alpha = 0.65f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .drawBehind {
                                        if (!isBeingDragged) {
                                            drawLine(
                                                color = Color.White.copy(alpha = 0.08f),
                                                start = Offset(0f, size.height),
                                                end = Offset(size.width, size.height),
                                                strokeWidth = 0.6.dp.toPx()
                                            )
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (item.isEnabled) AccentGold.copy(alpha = 0.16f)
                                            else Color.White.copy(alpha = 0.05f)
                                        )
                                        .padding(horizontal = 7.dp, vertical = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (item.isEnabled) "#${index + 1}" else "OFF",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (item.isEnabled) AccentGold else TextSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = item.key.displayName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (item.isEnabled) TextPrimary else TextSecondary.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isDiscoverRow) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(AccentGold.copy(alpha = 0.16f))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "DISCOVER",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentGold
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = item.key.description,
                                        fontSize = 11.5.sp,
                                        color = TextSecondary.copy(alpha = if (item.isEnabled) 0.78f else 0.45f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        currentRows[index] = item.copy(isEnabled = !item.isEnabled)
                                        coroutineScope.launch {
                                            preferenceManager.setHomeRowOrder(currentRows.toList())
                                        }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (item.isEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (item.isEnabled) "Hide Row" else "Show Row",
                                        tint = if (item.isEnabled) AccentGold else TextSecondary.copy(alpha = 0.55f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .draggableHandle(
                                            onDragStarted = {
                                                isDraggingAny = true
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragStopped = {
                                                isDraggingAny = false
                                                coroutineScope.launch {
                                                    preferenceManager.setHomeRowOrder(currentRows.toList())
                                                }
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = if (isBeingDragged) AccentGold else TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Hero Banner Customization Tab (Frameless Option A)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CustomizerSectionLabel("HERO CAROUSEL SOURCE")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HomeHeroSource.entries.forEach { source ->
                            CustomizerChoiceChip(
                                label = source.displayName,
                                selected = heroSettings.heroSource == source,
                                onClick = {
                                    coroutineScope.launch { preferenceManager.setHomeHeroSource(source) }
                                }
                            )
                        }
                    }

                    AnimatedVisibility(visible = heroSettings.heroSource == HomeHeroSource.ALL_MIXED) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CustomizerSectionLabel("MIXED POOLS TO INCLUDE")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "CONTINUE" to "Continue",
                                    "FAVORITES" to "Favorites",
                                    "RECENT" to "Recent",
                                    "PLAN_TO_TRACK" to "Plan to Track",
                                    "TRENDING" to "Trending"
                                ).forEach { (key, label) ->
                                    val checked = key in heroSettings.heroSources
                                    CustomizerChoiceChip(
                                        label = label,
                                        selected = checked,
                                        onClick = {
                                            val next = heroSettings.heroSources.toMutableSet()
                                            if (checked && next.size > 1) next.remove(key) else next.add(key)
                                            coroutineScope.launch { preferenceManager.setHomeHeroSources(next) }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                    CustomizerSectionLabel("HERO CATEGORY SCOPE")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "ALL" to "All Verticals",
                            "FOLLOW_HOME" to "Follow Home Filter",
                            "CUSTOM" to "Pick Verticals",
                            "CUSTOM_CATEGORIES" to "Custom Categories"
                        ).forEach { (mode, label) ->
                            CustomizerChoiceChip(
                                label = label,
                                selected = heroSettings.heroCategoryMode.equals(mode, ignoreCase = true),
                                onClick = {
                                    coroutineScope.launch { preferenceManager.setHomeHeroCategoryMode(mode) }
                                }
                            )
                        }
                    }

                    AnimatedVisibility(visible = heroSettings.heroCategoryMode.equals("CUSTOM", ignoreCase = true)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MediaType.filterTabs.filter { it != MediaType.ALL }.forEach { type ->
                                val active = type.name in heroSettings.heroCategories
                                CustomizerChoiceChip(
                                    label = type.displayName,
                                    selected = active,
                                    onClick = {
                                        val next = heroSettings.heroCategories.toMutableSet()
                                        if (active) next.remove(type.name) else next.add(type.name)
                                        coroutineScope.launch { preferenceManager.setHomeHeroCategories(next) }
                                    }
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = heroSettings.heroCategoryMode.equals("CUSTOM_CATEGORIES", ignoreCase = true)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (customCategories.isEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "No custom categories created yet",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "Manage Categories \u2192",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentGold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                onDismiss()
                                                onOpenManageCategories()
                                            }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    customCategories.forEach { cat ->
                                        val active = cat.id in heroSettings.heroCustomCategoryIds
                                        CustomizerChoiceChip(
                                            label = if (cat.isVisible) cat.name else "${cat.name} (Hidden)",
                                            selected = active,
                                            onClick = {
                                                val next = heroSettings.heroCustomCategoryIds.toMutableSet()
                                                if (active) next.remove(cat.id) else next.add(cat.id)
                                                coroutineScope.launch { preferenceManager.setHomeHeroCustomCategoryIds(next) }
                                            }
                                        )
                                    }
                                    CustomizerChoiceChip(
                                        label = "Manage \u2192",
                                        selected = false,
                                        onClick = {
                                            onDismiss()
                                            onOpenManageCategories()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                    CustomizerSectionLabel("SLIDE COUNT & ORDER")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5 to "5", 8 to "8", 12 to "12", 20 to "20", 0 to "\u221E All").forEach { (cnt, label) ->
                            CustomizerChoiceChip(
                                label = label,
                                selected = heroSettings.slideCount == cnt,
                                onClick = {
                                    coroutineScope.launch { preferenceManager.setHomeHeroSlideCount(cnt) }
                                }
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "RECENT" to "Recent",
                            "INTERLEAVED" to "Interleaved",
                            "TOP_RATED" to "Top Rated",
                            "SHUFFLE" to "Shuffle"
                        ).forEach { (ord, label) ->
                            CustomizerChoiceChip(
                                label = label,
                                selected = heroSettings.slideOrder.equals(ord, ignoreCase = true),
                                onClick = {
                                    coroutineScope.launch { preferenceManager.setHomeHeroSlideOrder(ord) }
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                    CustomizerSectionLabel("HERO HEIGHT & AUTO-ROTATE TIMER")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("COMPACT" to "Compact", "STANDARD" to "Standard", "TALL" to "Tall").forEach { (h, label) ->
                            VisualOptionTile(
                                title = label,
                                subtitle = null,
                                isSelected = heroSettings.heroHeightPreset.equals(h, ignoreCase = true),
                                modifier = Modifier.weight(1f),
                                previewHeight = 52.dp,
                                onClick = {
                                    coroutineScope.launch { preferenceManager.setHomeHeroHeightPreset(h) }
                                }
                            ) {
                                HeroHeightWireframe(preset = h)
                            }
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0 to "Off", 4 to "4s", 6 to "6s", 9 to "9s", 12 to "12s").forEach { (sec, label) ->
                            CustomizerChoiceChip(
                                label = label,
                                selected = if (!heroSettings.autoRotate || heroSettings.autoRotateSeconds <= 0) sec == 0 else heroSettings.autoRotateSeconds == sec,
                                onClick = {
                                    coroutineScope.launch {
                                        preferenceManager.setHomeHeroRotateSeconds(sec)
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                    CustomizerSwitchRow(
                        title = "Show Hero Carousel",
                        subtitle = "Display featured dual-artwork hero showcase at top of Home",
                        checked = heroSettings.showHero,
                        onCheckedChange = { v -> coroutineScope.launch { preferenceManager.setHomeHeroEnabled(v) } }
                    )
                    CustomizerSwitchRow(
                        title = "Dim Backdrop Layer",
                        subtitle = "Improves foreground poster and typography contrast",
                        checked = heroSettings.dimBanner,
                        onCheckedChange = { v -> coroutineScope.launch { preferenceManager.setHomeHeroDimBanner(v) } }
                    )

                    CustomizerSectionLabel("WIDE HERO BANNER BLUR (UNIVERSAL & PER-CATEGORY)")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HeroBannerBlurLevel.entries.forEach { level ->
                            val active = heroSettings.universalBannerBlur == level
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                                    .border(
                                        0.7.dp,
                                        if (active) AccentGold else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        coroutineScope.launch { preferenceManager.updateUniversalHeroBannerBlur(level) }
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = level.shortLabel,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) AccentGold else TextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    var selectedHomeBlurCat by remember { mutableStateOf(MediaType.ANIME) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MediaType.filterTabs.filter { it != MediaType.ALL }.forEach { cat ->
                            val ov = heroSettings.categoryBannerBlurOverrides[cat.name]
                            val isSel = selectedHomeBlurCat == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                                    .border(
                                        0.7.dp,
                                        when {
                                            isSel -> AccentGold
                                            ov != null -> AccentGold.copy(alpha = 0.40f)
                                            else -> Color.White.copy(alpha = 0.07f)
                                        },
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedHomeBlurCat = cat }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (ov != null) "${cat.displayName} \u00B7 ${ov.shortLabel}" else cat.displayName,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSel || ov != null) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel || ov != null) AccentGold else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val currentOv = heroSettings.categoryBannerBlurOverrides[selectedHomeBlurCat.name]
                        val inheritActive = currentOv == null
                        Box(
                            modifier = Modifier
                                .weight(1.15f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (inheritActive) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                                .border(
                                    0.7.dp,
                                    if (inheritActive) AccentGold else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    coroutineScope.launch {
                                        preferenceManager.updateCategoryHeroBannerBlurOverride(selectedHomeBlurCat, null)
                                    }
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Inherit (${heroSettings.universalBannerBlur.shortLabel})",
                                fontSize = 11.sp,
                                fontWeight = if (inheritActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (inheritActive) AccentGold else TextPrimary,
                                maxLines = 1
                            )
                        }
                        HeroBannerBlurLevel.entries.forEach { level ->
                            val active = currentOv == level
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                                    .border(
                                        0.7.dp,
                                        if (active) AccentGold else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            preferenceManager.updateCategoryHeroBannerBlurOverride(selectedHomeBlurCat, level)
                                        }
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = level.shortLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) AccentGold else TextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    CustomizerSwitchRow(
                        title = "Allow Cropped Cover Fallback",
                        subtitle = "If wide banner is missing, blur portrait cover instead of Ambient Glow",
                        checked = heroSettings.allowCroppedFallback,
                        onCheckedChange = { v -> coroutineScope.launch { preferenceManager.setHomeHeroAllowCroppedFallback(v) } }
                    )
                    CustomizerSwitchRow(
                        title = "Strict Mode Filter",
                        subtitle = "Filter full candidate pool to require authentic artwork before display",
                        checked = heroSettings.strictMode,
                        onCheckedChange = { v -> coroutineScope.launch { preferenceManager.setHomeHeroStrictMode(v) } }
                    )

                    AnimatedVisibility(visible = heroSettings.strictMode) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CustomizerSectionLabel("STRICT REQUIREMENTS")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("banner" to "Require Wide Banner", "cover" to "Require Cover").forEach { (key, label) ->
                                    val selected = key in heroSettings.strictRequirements
                                    CustomizerChoiceChip(
                                        label = label,
                                        selected = selected,
                                        onClick = {
                                            val next = heroSettings.strictRequirements.toMutableSet()
                                            if (selected) next.remove(key) else next.add(key)
                                            coroutineScope.launch { preferenceManager.setHomeHeroStrictRequirements(next) }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                    CustomizerSectionLabel("HERO METADATA LAYOUT")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple(0, "Clean Separation", "Spacious title & tags"),
                            Triple(1, "Stacked Rows", "Eyebrow, title & progress"),
                            Triple(2, "Compact Line", "Inline single-band specs"),
                            Triple(3, "Minimal Inline", "Title & inline bottom strip")
                        ).chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { (idx, label, sub) ->
                                    VisualOptionTile(
                                        title = label,
                                        subtitle = sub,
                                        isSelected = heroSettings.metadataLayout == idx,
                                        modifier = Modifier.weight(1f),
                                        previewHeight = 56.dp,
                                        onClick = {
                                            coroutineScope.launch { preferenceManager.setHomeHeroMetadataLayout(idx) }
                                        }
                                    ) {
                                        HeroMetadataLayoutWireframe(layoutIndex = idx)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                    CustomizerSwitchRow(
                        title = "Show Hero Ratings",
                        subtitle = "Display personal or community score tag on hero card",
                        checked = heroSettings.showRatings,
                        onCheckedChange = { v -> coroutineScope.launch { preferenceManager.setHomeHeroShowRatings(v) } }
                    )

                    AnimatedVisibility(visible = heroSettings.showRatings) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CustomizerSectionLabel("RATING FORMAT")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    0 to "Golden Star (\u2605)",
                                    1 to "Percentage (%)",
                                    2 to "Star + Source",
                                    3 to "Multi-Source"
                                ).forEach { (idx, label) ->
                                    CustomizerChoiceChip(
                                        label = label,
                                        selected = heroSettings.ratingFormat == idx,
                                        onClick = {
                                            coroutineScope.launch { preferenceManager.setHomeHeroRatingFormat(idx) }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    CustomizerSwitchRow(
                        title = "Show Remaining Count Badge",
                        subtitle = "Show +N remaining episodes/chapters/pages tag",
                        checked = heroSettings.showUnreadBadge,
                        onCheckedChange = { v -> coroutineScope.launch { preferenceManager.setHomeHeroShowUnreadBadge(v) } }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                    CustomizerSectionLabel("STATUS FILTERS (EMPTY = ALL)")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            TrackingStatus.IN_PROGRESS,
                            TrackingStatus.PLAN_TO_TRACK,
                            TrackingStatus.COMPLETED,
                            TrackingStatus.ON_HOLD,
                            TrackingStatus.DROPPED
                        ).forEach { st ->
                            val active = st.name in heroSettings.statusFilters
                            CustomizerChoiceChip(
                                label = st.displayName,
                                selected = active,
                                onClick = {
                                    val next = heroSettings.statusFilters.toMutableSet()
                                    if (active) next.remove(st.name) else next.add(st.name)
                                    coroutineScope.launch { preferenceManager.setHomeHeroStatusFilters(next) }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun CustomizerSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        letterSpacing = 0.9.sp
    )
}

@Composable
private fun CustomizerChoiceChip(
    label: String,
    selected: Boolean,
    fullWidth: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) AccentGold else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CustomizerSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = TextSecondary
            )
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
