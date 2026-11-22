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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.ContinueTrackingStyle
import com.omnitrack.app.data.preferences.HomeHeroSettings
import com.omnitrack.app.data.preferences.HomeHeroSource
import com.omnitrack.app.data.preferences.HomeLayoutDefaults
import com.omnitrack.app.data.preferences.HomeRowItem
import com.omnitrack.app.data.preferences.HomeShelfPosterSize
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeCustomizerSheet(
    preferenceManager: PreferenceManager,
    onDismiss: () -> Unit
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
        containerColor = DarkSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
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
                        text = "Configure shelves, Trending feeds, card styles & hero banner",
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
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Defaults",
                        tint = AccentGold,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            // Tab Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Shelves & Display", "Hero Banner").forEachIndexed { idx, label ->
                    val selected = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) AccentGold else DarkSurfaceElevated)
                            .border(1.dp, if (selected) AccentGold else GlassBorder, RoundedCornerShape(12.dp))
                            .clickable { selectedTab = idx }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) AmoledBlack else TextPrimary
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "home_display_controls") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkSurfaceElevated)
                                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CustomizerSectionLabel("CONTINUE TRACKING STYLE")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ContinueTrackingStyle.entries.forEach { style ->
                                    CustomizerChoiceChip(
                                        label = style.displayName,
                                        selected = heroSettings.continueTrackingStyle == style,
                                        onClick = {
                                            coroutineScope.launch { preferenceManager.setContinueTrackingStyle(style) }
                                        }
                                    )
                                }
                            }

                            HorizontalDivider(color = GlassBorder, thickness = 1.dp)

                            CustomizerSectionLabel("SHELF POSTER SIZE")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HomeShelfPosterSize.entries.forEach { size ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        CustomizerChoiceChip(
                                            label = size.displayName,
                                            selected = heroSettings.shelfPosterSize == size,
                                            fullWidth = true,
                                            onClick = {
                                                coroutineScope.launch { preferenceManager.setHomeShelfPosterSize(size) }
                                            }
                                        )
                                    }
                                }
                            }

                            CustomizerSectionLabel("ITEMS PER SHELF")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(8, 12, 20).forEach { limit ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        CustomizerChoiceChip(
                                            label = "$limit Items",
                                            selected = heroSettings.shelfItemLimit == limit,
                                            fullWidth = true,
                                            onClick = {
                                                coroutineScope.launch { preferenceManager.setHomeShelfItemLimit(limit) }
                                            }
                                        )
                                    }
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
                        }
                    }

                    item(key = "home_shelves_section_header") {
                        Text(
                            text = "SHELVES & DISCOVER FEEDS (DRAG \u2261 TO REORDER · TOGGLE EYE)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
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
                                    .then(
                                        if (isBeingDragged) {
                                            Modifier
                                                .shadow(12.dp, RoundedCornerShape(14.dp))
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(DarkSurfaceElevated)
                                                .border(1.5.dp, AccentGold, RoundedCornerShape(14.dp))
                                        } else {
                                            Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (item.isEnabled) DarkSurfaceElevated
                                                    else Color.White.copy(alpha = 0.03f)
                                                )
                                                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (item.isEnabled) AccentGold.copy(alpha = 0.16f)
                                            else Color.White.copy(alpha = 0.06f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
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
                                            color = if (item.isEnabled) TextPrimary else TextSecondary,
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
                                        color = TextSecondary,
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
                                        tint = if (item.isEnabled) AccentGold else TextSecondary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
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
                // Hero Banner Customization Tab
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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

                        HorizontalDivider(color = GlassBorder, thickness = 1.dp)

                        CustomizerSwitchRow(
                            title = "Show Hero Carousel",
                            subtitle = "Display featured dual-artwork hero showcase at top of Home",
                            checked = heroSettings.showHero,
                            onCheckedChange = { v -> coroutineScope.launch { preferenceManager.setHomeHeroEnabled(v) } }
                        )
                        CustomizerSwitchRow(
                            title = "Auto-Rotate Slides (6s)",
                            subtitle = "Smoothly advances through featured slides automatically",
                            checked = heroSettings.autoRotate,
                            onCheckedChange = { v -> coroutineScope.launch { preferenceManager.setHomeHeroAutoRotate(v) } }
                        )
                        CustomizerSwitchRow(
                            title = "Dim Backdrop Layer",
                            subtitle = "Improves foreground poster and typography contrast",
                            checked = heroSettings.dimBanner,
                            onCheckedChange = { v -> coroutineScope.launch { preferenceManager.setHomeHeroDimBanner(v) } }
                        )
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
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CustomizerSectionLabel("HERO METADATA LAYOUT")
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                0 to "Clean Separation",
                                1 to "Stacked Rows",
                                2 to "Compact Single-Line",
                                3 to "Button-Integrated"
                            ).forEach { (idx, label) ->
                                CustomizerChoiceChip(
                                    label = label,
                                    selected = heroSettings.metadataLayout == idx,
                                    onClick = {
                                        coroutineScope.launch { preferenceManager.setHomeHeroMetadataLayout(idx) }
                                    }
                                )
                            }
                        }

                        HorizontalDivider(color = GlassBorder, thickness = 1.dp)

                        CustomizerSwitchRow(
                            title = "Show Hero Ratings",
                            subtitle = "Display personal or community score pill on hero card",
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
                            subtitle = "Show +N remaining episodes/chapters/pages badge",
                            checked = heroSettings.showUnreadBadge,
                            onCheckedChange = { v -> coroutineScope.launch { preferenceManager.setHomeHeroShowUnreadBadge(v) } }
                        )

                        HorizontalDivider(color = GlassBorder, thickness = 1.dp)

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
        fontSize = 11.sp,
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
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentGold else DarkSurface)
            .border(1.dp, if (selected) AccentGold else GlassBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) AmoledBlack else TextPrimary,
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
            .padding(vertical = 2.dp),
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
                checkedThumbColor = AmoledBlack,
                checkedTrackColor = AccentGold,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = DarkSurface,
                uncheckedBorderColor = GlassBorder
            )
        )
    }
}
