package com.omnitrack.app.ui.screens.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.ChimanewLibrarySettings
import com.omnitrack.app.data.preferences.DEFAULT_MEDIA_TYPE_ORDER
import com.omnitrack.app.data.preferences.LibraryDisplayMode
import com.omnitrack.app.data.preferences.LibraryGroupMode
import com.omnitrack.app.data.preferences.LibrarySortOption
import com.omnitrack.app.data.preferences.TriStateFilter
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Context-aware per-category sub-filters hosted cleanly inside the Filter sheet
 * (replaces stacked inline filter bars on Games, VNs, Manga, Novels, Books, Comics & Fanfic).
 */
data class LibraryCategorySubFilters(
    val activeMediaTabType: MediaType? = null,
    val isNovelTabSplit: Boolean = false,
    val mangaSubtype: String = "ALL",
    val onMangaSubtype: (String) -> Unit = {},
    val novelSubtype: String = "ALL",
    val onNovelSubtype: (String) -> Unit = {},
    val vnOrigin: String = "ALL",
    val onVnOrigin: (String) -> Unit = {},
    val vnLength: String = "ALL",
    val onVnLength: (String) -> Unit = {},
    val gamePlatform: String = "ALL",
    val onGamePlatform: (String) -> Unit = {},
    val gameCompletion: String = "ALL",
    val onGameCompletion: (String) -> Unit = {},
    val bookFormat: String = "ALL",
    val onBookFormat: (String) -> Unit = {},
    val bookCategory: String = "ALL",
    val onBookCategory: (String) -> Unit = {},
    val comicPublisher: String = "ALL",
    val onComicPublisher: (String) -> Unit = {},
    val comicFormat: String = "ALL",
    val onComicFormat: (String) -> Unit = {},
    val fanficSites: Set<String> = emptySet(),
    val onToggleFanficSite: (String) -> Unit = {},
    val onClearFanficSites: () -> Unit = {},
    val fanficRating: String = "ALL",
    val onFanficRating: (String) -> Unit = {},
    val fanficStatus: String = "ALL",
    val onFanficStatus: (String) -> Unit = {},
    val fanficMinWords: Int = 0,
    val onFanficMinWords: (Int) -> Unit = {},
    val fanficCrossoverOnly: Boolean = false,
    val onFanficCrossoverToggle: () -> Unit = {},
    val fanficTagFilter: String = "",
    val onFanficTagFilter: (String) -> Unit = {},
    val onOpenFanficImport: () -> Unit = {}
)

/**
 * 4-Tab Chimanew Library Settings Sheet (Filter, Sort, Display, Group) in Option A Frameless AMOLED style.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChimanewLibrarySheet(
    settings: ChimanewLibrarySettings,
    selectedStatus: TrackingStatus,
    categories: List<CategoryEntity>,
    fanfictionEnabled: Boolean,
    visibleMediaTypes: List<MediaType> = DEFAULT_MEDIA_TYPE_ORDER.filter { fanfictionEnabled || it != MediaType.FANFICTION },
    subFilters: LibraryCategorySubFilters = LibraryCategorySubFilters(),
    onSelectStatus: (TrackingStatus) -> Unit,
    onToggleTriStateFilter: (String, TriStateFilter) -> Unit,
    onSetFilterMediaTypes: (Set<String>) -> Unit,
    onSetFilterTrackers: (Set<String>) -> Unit,
    onSetCategoryFilterSets: (Set<Long>, Set<Long>) -> Unit,
    onSelectSort: (LibrarySortOption, Boolean) -> Unit,
    onSelectDisplayMode: (LibraryDisplayMode) -> Unit,
    onSetPortraitColumns: (Int) -> Unit,
    onSetLandscapeColumns: (Int) -> Unit,
    onToggleOverlay: (String, Boolean) -> Unit,
    onSelectGroupMode: (LibraryGroupMode) -> Unit,
    onOpenManageCategories: () -> Unit,
    onResetFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeTab by remember { mutableIntStateOf(0) }

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
                .fillMaxHeight(0.84f)
                .background(AmoledBlack)
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                )
            }

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Reset Filters",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onResetFilters)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Option A 4-Tab Underline Strip (Filter / Sort / Display / Group)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Filter", "Sort", "Display", "Group").forEachIndexed { idx, label ->
                    val selected = activeTab == idx
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .clickable { activeTab = idx },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) TextPrimary else TextSecondary
                        )
                        Spacer(modifier = Modifier.height(7.dp))
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (activeTab) {
                    0 -> {
                        // Contextual per-category sub-filters right at top when inside a specific media tab
                        val activeCat = subFilters.activeMediaTabType
                        if (activeCat != null &&
                            activeCat != MediaType.ALL &&
                            activeCat !in setOf(MediaType.ANIME, MediaType.SHOW, MediaType.MOVIE) &&
                            !(activeCat == MediaType.NOVEL && subFilters.isNovelTabSplit)
                        ) {
                            CategorySubFiltersBlock(activeCat = activeCat, subFilters = subFilters)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                        }

                        // TAB 0: FILTER (Tri-state Quick Filters + Status + Categories + Media Types + Trackers)
                        SheetSectionLabel("QUICK FILTERS (TAP TO CYCLE INCLUDE / EXCLUDE)")
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple("STARTED", "Started", settings.filterStarted),
                                Triple("COMPLETED", "Completed", settings.filterCompleted),
                                Triple("UNREAD", "Unread Left", settings.filterUnread),
                                Triple("RATED", "Rated", settings.filterRated),
                                Triple("FAVORITE", "Favorites", settings.filterFavorite)
                            ).forEach { (key, label, state) ->
                                TriStateChip(
                                    label = label,
                                    state = state,
                                    onClick = { onToggleTriStateFilter(key, state.next()) }
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                        SheetSectionLabel("TRACKING STATUS")
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TrackingStatus.filterChips.forEach { st ->
                                LibChoiceChip(
                                    label = st.labelFor(subFilters.activeMediaTabType),
                                    selected = selectedStatus == st,
                                    onClick = { onSelectStatus(st) }
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SheetSectionLabel("CUSTOM CATEGORIES")
                            Text(
                                text = "Manage \u2192",
                                color = AccentGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        onDismiss()
                                        onOpenManageCategories()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (categories.isEmpty()) {
                            Text(
                                text = "No custom categories created yet. Tap Manage \u2192 to create one.",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                categories.forEach { cat ->
                                    val catState = when {
                                        cat.id in settings.filterIncludeCategoryIds -> TriStateFilter.INCLUDE
                                        cat.id in settings.filterExcludeCategoryIds -> TriStateFilter.EXCLUDE
                                        else -> TriStateFilter.OFF
                                    }
                                    TriStateChip(
                                        label = cat.name,
                                        state = catState,
                                        onClick = {
                                            val inc = settings.filterIncludeCategoryIds.toMutableSet()
                                            val exc = settings.filterExcludeCategoryIds.toMutableSet()
                                            when (catState) {
                                                TriStateFilter.OFF -> {
                                                    exc.remove(cat.id)
                                                    inc.add(cat.id)
                                                }
                                                TriStateFilter.INCLUDE -> {
                                                    inc.remove(cat.id)
                                                    exc.add(cat.id)
                                                }
                                                TriStateFilter.EXCLUDE -> {
                                                    inc.remove(cat.id)
                                                    exc.remove(cat.id)
                                                }
                                            }
                                            onSetCategoryFilterSets(inc, exc)
                                        }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SheetSectionLabel("MEDIA VERTICALS (EMPTY = ALL)")
                            Text(
                                text = "Hide / Reorder \u2192",
                                color = AccentGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        onDismiss()
                                        onOpenManageCategories()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            visibleMediaTypes
                                .filter { it != MediaType.ALL }
                                .forEach { mt ->
                                    val sel = mt.name in settings.filterMediaTypes
                                    LibChoiceChip(
                                        label = mt.displayName,
                                        selected = sel,
                                        onClick = {
                                            val next = settings.filterMediaTypes.toMutableSet()
                                            if (sel) next.remove(mt.name) else next.add(mt.name)
                                            onSetFilterMediaTypes(next)
                                        }
                                    )
                                }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                        SheetSectionLabel("CONNECTED TRACKERS (EMPTY = ALL)")
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "ANILIST" to "AniList",
                                "MAL" to "MAL",
                                "TMDB" to "TMDb",
                                "TRAKT" to "Trakt",
                                "SIMKL" to "Simkl",
                                "STEAM" to "Steam",
                                "VNDB" to "VNDB",
                                "HARDCOVER" to "Hardcover",
                                "LOCAL" to "Local Only"
                            ).forEach { (key, label) ->
                                val sel = key in settings.filterTrackers
                                LibChoiceChip(
                                    label = label,
                                    selected = sel,
                                    onClick = {
                                        val next = settings.filterTrackers.toMutableSet()
                                        if (sel) next.remove(key) else next.add(key)
                                        onSetFilterTrackers(next)
                                    }
                                )
                            }
                        }
                    }

                    1 -> {
                        // TAB 1: SORT (Frameless hairline rows + Asc/Desc arrow)
                        SheetSectionLabel("SORT LIBRARY BY (TAP AGAIN TO FLIP ORDER)")
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LibrarySortOption.entries.forEachIndexed { idx, opt ->
                                val active = settings.sortOption == opt
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (active) {
                                                onSelectSort(opt, !settings.sortAscending)
                                            } else {
                                                val defaultAsc = opt == LibrarySortOption.ALPHA
                                                onSelectSort(opt, defaultAsc)
                                            }
                                        }
                                        .padding(vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = opt.displayName,
                                        color = if (active) AccentGold else TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                    )
                                    if (active) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = if (settings.sortAscending) "Asc" else "Desc",
                                                color = AccentGold,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(
                                                imageVector = if (settings.sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = AccentGold,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                if (idx < LibrarySortOption.entries.lastIndex) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                                }
                            }
                        }
                    }

                    2 -> {
                        // TAB 2: DISPLAY (Mode tiles + Grid columns sliders + Overlays + Tabs toggles)
                        SheetSectionLabel("DISPLAY MODE")
                        val modes = LibraryDisplayMode.entries
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            modes.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { mode ->
                                        LibChoiceChip(
                                            label = mode.displayName,
                                            selected = settings.displayMode == mode,
                                            onClick = { onSelectDisplayMode(mode) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                        // Portrait Grid Size Slider (Auto = 0 or 2..6 columns)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Items per row (Portrait)", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (settings.portraitColumns == 0) "Auto" else "${settings.portraitColumns} per row",
                                    color = AccentGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val sliderPos = if (settings.portraitColumns == 0) 0f else (settings.portraitColumns - 1).toFloat()
                            Slider(
                                value = sliderPos,
                                onValueChange = { raw ->
                                    val step = raw.roundToInt().coerceIn(0, 5)
                                    val cols = if (step == 0) 0 else step + 1
                                    onSetPortraitColumns(cols)
                                },
                                valueRange = 0f..5f,
                                steps = 4,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentGold,
                                    activeTrackColor = AccentGold,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                                )
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Items per row (Landscape / Tablet)", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${settings.landscapeColumns} per row",
                                    color = AccentGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = settings.landscapeColumns.toFloat(),
                                onValueChange = { onSetLandscapeColumns(it.roundToInt().coerceIn(4, 8)) },
                                valueRange = 4f..8f,
                                steps = 3,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentGold,
                                    activeTrackColor = AccentGold,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                                )
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                        SheetSectionLabel("OVERLAY BADGES")
                        LibSwitchRow("Remaining Count Badge", "Show +N left count badge on library covers", settings.showRemainingBadge) {
                            onToggleOverlay("REMAINING", it)
                        }
                        LibSwitchRow("Media Type Tag", "Show ANIME / MANGA / SHOW / BOOK tag", settings.showTypeBadge) {
                            onToggleOverlay("TYPE", it)
                        }
                        LibSwitchRow("Score Badge", "Show your rating or community score on cards", settings.showScoreBadge) {
                            onToggleOverlay("SCORE", it)
                        }
                        LibSwitchRow("Quick +1 Button", "Show quick progress button on library cards", settings.showQuickContinue) {
                            onToggleOverlay("CONTINUE", it)
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                        SheetSectionLabel("TABS & CATEGORIES")
                        LibSwitchRow("Show Category Tabs", "Display top swipeable group tabs", settings.showCategoryTabs) {
                            onToggleOverlay("TABS", it)
                        }
                        LibSwitchRow("Show Item Counts on Tabs", "Display count next to each tab label", settings.showTabItemCounts) {
                            onToggleOverlay("COUNTS", it)
                        }
                        LibSwitchRow("Show Hidden Categories", "Include eye-disabled custom categories as tabs", settings.showHiddenCategories) {
                            onToggleOverlay("HIDDEN", it)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onOpenManageCategories()
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Hide / Reorder Media Tabs",
                                    color = AccentGold,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Show, hide, or reorder Library categories & custom folders",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    3 -> {
                        // TAB 3: GROUP (Frameless hairline radio rows)
                        SheetSectionLabel("GROUP LIBRARY TABS BY")
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LibraryGroupMode.entries.forEachIndexed { idx, mode ->
                                val selected = settings.groupMode == mode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectGroupMode(mode) }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = if (selected) 5.dp else 1.5.dp,
                                                color = if (selected) AccentGold else TextMuted,
                                                shape = CircleShape
                                            )
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = mode.displayName,
                                            color = if (selected) AccentGold else TextPrimary,
                                            fontSize = 14.5.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                                        )
                                        Text(
                                            text = mode.subtitle,
                                            color = TextSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                        SheetSectionLabel("MULTI-SEASON GROUPING")
                        LibSwitchRow(
                            title = "Group Anime Seasons into 1 Card",
                            subtitle = "Collapse multi-season sequels & linked entries into a single root card in Library",
                            checked = settings.groupAnimeSeasonsInLibrary,
                            onCheckedChange = { onToggleOverlay("GROUP_ANIME_SEASONS", it) }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onOpenManageCategories()
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Manage & Reorder Categories",
                                color = AccentGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        letterSpacing = 0.9.sp
    )
}

@Composable
private fun LibChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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
private fun TriStateChip(
    label: String,
    state: TriStateFilter,
    onClick: () -> Unit
) {
    val bg = when (state) {
        TriStateFilter.INCLUDE -> AccentGold.copy(alpha = 0.16f)
        TriStateFilter.EXCLUDE -> Color(0xFFF87171).copy(alpha = 0.16f)
        TriStateFilter.OFF -> Color.White.copy(alpha = 0.04f)
    }
    val fg = when (state) {
        TriStateFilter.INCLUDE -> AccentGold
        TriStateFilter.EXCLUDE -> Color(0xFFF87171)
        TriStateFilter.OFF -> TextSecondary
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (state == TriStateFilter.INCLUDE) {
            Icon(Icons.Default.Check, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
        } else if (state == TriStateFilter.EXCLUDE) {
            Icon(Icons.Default.Close, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (state != TriStateFilter.OFF) FontWeight.Bold else FontWeight.Medium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LibSwitchRow(
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
            Text(text = title, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = TextSecondary, fontSize = 11.5.sp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySubFiltersBlock(
    activeCat: MediaType,
    subFilters: LibraryCategorySubFilters
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (activeCat) {
            MediaType.MANGA -> {
                SheetSectionLabel("MANGA SUBTYPE")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL" to "All", "MANGA" to "Manga", "MANHWA" to "Manhwa", "MANHUA" to "Manhua").forEach { (k, lbl) ->
                        LibChoiceChip(label = lbl, selected = subFilters.mangaSubtype == k, onClick = { subFilters.onMangaSubtype(k) })
                    }
                }
            }
            MediaType.NOVEL -> {
                if (!subFilters.isNovelTabSplit) {
                    SheetSectionLabel("NOVEL SUBTYPE")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("ALL" to "All", "LIGHT_NOVEL" to "Light Novel", "WEB_NOVEL" to "Web Novel").forEach { (k, lbl) ->
                            LibChoiceChip(label = lbl, selected = subFilters.novelSubtype == k, onClick = { subFilters.onNovelSubtype(k) })
                        }
                    }
                }
            }
            MediaType.VISUAL_NOVEL -> {
                SheetSectionLabel("VISUAL NOVEL \u00B7 ORIGIN")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL" to "All", "JP" to "Japanese", "EN" to "English / OELVN", "CNKR" to "CN / KR").forEach { (k, lbl) ->
                        LibChoiceChip(label = lbl, selected = subFilters.vnOrigin == k, onClick = { subFilters.onVnOrigin(k) })
                    }
                }
                SheetSectionLabel("VISUAL NOVEL \u00B7 LENGTH")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL" to "Any Length", "SHORT" to "Short (<10h)", "MEDIUM" to "Medium", "LONG" to "Long", "VLONG" to "Very Long (50h+)").forEach { (k, lbl) ->
                        LibChoiceChip(label = lbl, selected = subFilters.vnLength == k, onClick = { subFilters.onVnLength(k) })
                    }
                }
            }
            MediaType.GAME -> {
                SheetSectionLabel("GAMES \u00B7 PLATFORM")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to "All",
                        "PC" to "PC",
                        "PS" to "PlayStation",
                        "SWI" to "Switch",
                        "XBOX" to "Xbox",
                        "DECK" to "Steam Deck",
                        "MOBILE" to "Mobile",
                        "RETRO" to "Retro"
                    ).forEach { (k, lbl) ->
                        LibChoiceChip(label = lbl, selected = subFilters.gamePlatform == k, onClick = { subFilters.onGamePlatform(k) })
                    }
                }
                SheetSectionLabel("GAMES \u00B7 COMPLETION TIER")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to "All Tiers",
                        "MAIN" to "Main / Main+",
                        "COMP" to "100% / Plat",
                        "BEATEN" to "Completed",
                        "BACKLOG" to "Backlog"
                    ).forEach { (k, lbl) ->
                        LibChoiceChip(label = lbl, selected = subFilters.gameCompletion == k, onClick = { subFilters.onGameCompletion(k) })
                    }
                }
            }
            MediaType.BOOK -> {
                SheetSectionLabel("BOOKS \u00B7 FORMAT")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to "All Formats",
                        "PAPERBACK" to "Paperback",
                        "HARDCOVER" to "Hardcover",
                        "EBOOK" to "Ebook",
                        "AUDIOBOOK" to "Audiobook"
                    ).forEach { (k, lbl) ->
                        LibChoiceChip(label = lbl, selected = subFilters.bookFormat == k, onClick = { subFilters.onBookFormat(k) })
                    }
                }
                SheetSectionLabel("BOOKS \u00B7 CATEGORY")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to "All",
                        "FICTION" to "Fiction",
                        "NONFICTION" to "Non-Fiction",
                        "FANTASY" to "Fantasy",
                        "SCIFI" to "Sci-Fi",
                        "MYSTERY" to "Mystery",
                        "CLASSIC" to "Classics"
                    ).forEach { (k, lbl) ->
                        LibChoiceChip(label = lbl, selected = subFilters.bookCategory == k, onClick = { subFilters.onBookCategory(k) })
                    }
                }
            }
            MediaType.COMIC -> {
                SheetSectionLabel("COMICS \u00B7 PUBLISHER")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to "All Publishers",
                        "MARVEL" to "Marvel",
                        "DC" to "DC",
                        "IMAGE" to "Image",
                        "DARKHORSE" to "Dark Horse",
                        "INDIE" to "Indie / Other"
                    ).forEach { (k, lbl) ->
                        LibChoiceChip(label = lbl, selected = subFilters.comicPublisher == k, onClick = { subFilters.onComicPublisher(k) })
                    }
                }
                SheetSectionLabel("COMICS \u00B7 FORMAT")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to "All Formats",
                        "SINGLE_ISSUE" to "Issues",
                        "TPB" to "TPB",
                        "OMNIBUS" to "Omnibus",
                        "GRAPHIC_NOVEL" to "Graphic Novel"
                    ).forEach { (k, lbl) ->
                        LibChoiceChip(label = lbl, selected = subFilters.comicFormat == k, onClick = { subFilters.onComicFormat(k) })
                    }
                }
            }
            MediaType.FANFICTION -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SheetSectionLabel("FANFICTION \u00B7 SITES (MULTI-SELECT)")
                    Text(
                        text = "+ Import URL",
                        color = AccentGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = subFilters.onOpenFanficImport)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibChoiceChip(
                        label = "All Sites",
                        selected = subFilters.fanficSites.isEmpty(),
                        onClick = subFilters.onClearFanficSites
                    )
                    listOf(
                        "AO3" to "AO3",
                        "WATTPAD" to "Wattpad",
                        "FFN" to "FF.net",
                        "SB" to "SpaceBattles",
                        "SV" to "SV",
                        "QQ" to "QQ"
                    ).forEach { (k, lbl) ->
                        LibChoiceChip(
                            label = lbl,
                            selected = k in subFilters.fanficSites,
                            onClick = { subFilters.onToggleFanficSite(k) }
                        )
                    }
                }
                SheetSectionLabel("RATING & WORK STATUS")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL" to "Any Rating", "G" to "G", "T" to "T", "M" to "M", "E" to "E").forEach { (k, lbl) ->
                        LibChoiceChip(label = lbl, selected = subFilters.fanficRating == k, onClick = { subFilters.onFanficRating(k) })
                    }
                    listOf("ALL" to "Any Status", "COMPLETE" to "Complete", "ONGOING" to "Ongoing", "HIATUS" to "Hiatus").forEach { (k, lbl) ->
                        LibChoiceChip(label = lbl, selected = subFilters.fanficStatus == k, onClick = { subFilters.onFanficStatus(k) })
                    }
                    LibChoiceChip(
                        label = "Crossovers",
                        selected = subFilters.fanficCrossoverOnly,
                        onClick = subFilters.onFanficCrossoverToggle
                    )
                }
                SheetSectionLabel("MIN WORDS")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0 to "Any", 10_000 to "10k+", 50_000 to "50k+", 100_000 to "100k+").forEach { (w, lbl) ->
                        LibChoiceChip(label = lbl, selected = subFilters.fanficMinWords == w, onClick = { subFilters.onFanficMinWords(w) })
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicTextField(
                        value = subFilters.fanficTagFilter,
                        onValueChange = subFilters.onFanficTagFilter,
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                        cursorBrush = SolidColor(AccentGold),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (subFilters.fanficTagFilter.isEmpty()) {
                                Text("Filter by fandom, ship, character, or tag...", color = TextMuted, fontSize = 12.5.sp)
                            }
                            inner()
                        }
                    )
                    if (subFilters.fanficTagFilter.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear tag",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { subFilters.onFanficTagFilter("") }
                        )
                    }
                }
            }
            else -> Unit
        }
    }
}
