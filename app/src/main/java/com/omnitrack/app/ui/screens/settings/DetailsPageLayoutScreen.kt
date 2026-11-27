package com.omnitrack.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.preferences.DetailSectionItem
import com.omnitrack.app.data.preferences.DetailsPageLayoutDefaults
import com.omnitrack.app.data.preferences.EditionsSwitcherStyle
import com.omnitrack.app.data.preferences.MediaCategoryPrefs
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.ui.components.EditionsStyleWireframe
import com.omnitrack.app.ui.components.VisualOptionTile
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Per-medium Details Page Layout customizer with Editions switcher wireframe picker
 * and conflict-free drag reordering per category tab.
 */
@Composable
fun DetailsPageLayoutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefManager = remember(context) {
        (context.applicationContext as OmniTrackApplication).preferenceManager
    }
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val mediaCategoryPrefs by prefManager.mediaCategoryPrefs.collectAsState(initial = MediaCategoryPrefs())
    val sectionOrdersByMedia by prefManager.detailsPageSectionOrdersByMedia.collectAsState(initial = emptyMap())
    val editionsSwitcherStyle by prefManager.editionsSwitcherStyle.collectAsState(initial = EditionsSwitcherStyle.SMART_LABELS)

    val categories = remember(mediaCategoryPrefs) {
        mediaCategoryPrefs.visibleFilterTabs(includeAll = false)
            .ifEmpty { MediaType.filterTabs.filter { it != MediaType.ALL } }
    }
    val pagerState = rememberPagerState(initialPage = 0) { categories.size }
    val tabListState = rememberLazyListState()
    var isDraggingAnyRow by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage, categories.size) {
        if (categories.isNotEmpty()) {
            tabListState.animateScrollToItem(
                index = pagerState.currentPage.coerceIn(0, categories.lastIndex),
                scrollOffset = 0
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Fullscreen Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = "Details Page Layout",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Per-category section order & editions style",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = {
                    val activeType = categories.getOrNull(pagerState.currentPage) ?: MediaType.ANIME
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    coroutineScope.launch {
                        prefManager.resetDetailsPageSectionOrderForMedia(activeType)
                        Toast.makeText(
                            context,
                            "Reset ${activeType.displayName} layout to default",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Category Layout",
                    tint = TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Visual Wireframe Picker for Editions & Adaptations Switcher Style (no long descriptions)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "EDITIONS SWITCHER STYLE",
                color = TextMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditionsSwitcherStyle.entries.forEach { style ->
                    VisualOptionTile(
                        title = style.displayName,
                        subtitle = null,
                        isSelected = editionsSwitcherStyle == style,
                        previewHeight = 48.dp,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            coroutineScope.launch { prefManager.setEditionsSwitcherStyle(style) }
                        }
                    ) {
                        EditionsStyleWireframe(style = style)
                    }
                }
            }
        }

        // Horizontal Option A Category Underline Tabs
        LazyRow(
            state = tabListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(
                items = categories,
                key = { _, mediaType -> mediaType.name }
            ) { index, mediaType ->
                val isSelected = pagerState.currentPage == index
                val tabTextColor by animateColorAsState(
                    targetValue = if (isSelected) TextPrimary else TextMuted,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "detailsTabText"
                )
                val indicatorColor by animateColorAsState(
                    targetValue = if (isSelected) AccentGold else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "detailsTabIndicator"
                )

                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable {
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        }
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = mediaType.displayName,
                        color = tabTextColor,
                        fontSize = 13.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(7.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(indicatorColor)
                    )
                }
            }
        }
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            thickness = 0.6.dp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "Drag ≡ on the right to reorder. Tap the eye to hide sections for this category.",
                color = TextMuted,
                fontSize = 11.5.sp
            )
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isDraggingAnyRow,
            beyondViewportPageCount = 0,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val mediaType = categories[page]
            key(mediaType) {
                MediaDetailsSectionList(
                    mediaType = mediaType,
                    savedSections = sectionOrdersByMedia[mediaType]
                        ?: DetailsPageLayoutDefaults.defaultSectionsFor(mediaType),
                    prefManager = prefManager,
                    onDraggingChange = { isDraggingAnyRow = it }
                )
            }
        }
    }
}

@Composable
private fun MediaDetailsSectionList(
    mediaType: MediaType,
    savedSections: List<DetailSectionItem>,
    prefManager: PreferenceManager,
    onDraggingChange: (Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val currentSections = remember(mediaType) { mutableStateListOf<DetailSectionItem>() }
    var initialized by remember(mediaType) { mutableStateOf(false) }
    var pendingLocalCommit by remember(mediaType) { mutableStateOf<List<DetailSectionItem>?>(null) }
    var isDraggingAny by remember(mediaType) { mutableStateOf(false) }

    LaunchedEffect(savedSections, mediaType) {
        val pending = pendingLocalCommit
        if (pending != null && savedSections == pending) {
            pendingLocalCommit = null
        }
        if (!initialized) {
            currentSections.clear()
            currentSections.addAll(savedSections)
            initialized = true
        } else if (!isDraggingAny && pendingLocalCommit == null && currentSections.toList() != savedSections) {
            currentSections.clear()
            currentSections.addAll(savedSections)
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = currentSections.indexOfFirst { "${mediaType.name}_${it.key.name}" == from.key }
        val toIdx = currentSections.indexOfFirst { "${mediaType.name}_${it.key.name}" == to.key }
        if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
            currentSections.apply { add(toIdx, removeAt(fromIdx)) }
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    if (!initialized) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(
            items = currentSections,
            key = { _, item -> "${mediaType.name}_${item.key.name}" }
        ) { index, item ->
            val itemKey = "${mediaType.name}_${item.key.name}"
            ReorderableItem(reorderableState, key = itemKey) { isBeingDragged ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isBeingDragged) 10f else 1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isBeingDragged) DarkSurfaceElevated else AmoledBlack)
                        .border(
                            width = 1.dp,
                            color = if (isBeingDragged) AccentGold.copy(alpha = 0.65f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .drawBehind {
                            if (!isBeingDragged && index < currentSections.lastIndex) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.08f),
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 0.6.dp.toPx()
                                )
                            }
                        }
                        .padding(
                            horizontal = if (isBeingDragged) 12.dp else 0.dp,
                            vertical = 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (item.isEnabled) AccentGold.copy(alpha = 0.14f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (item.isEnabled) "#${index + 1}" else "OFF",
                            color = if (item.isEnabled) AccentGold else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = DetailsPageLayoutDefaults.displayNameFor(item.key, mediaType),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (item.isEnabled) TextPrimary else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.key.description,
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    IconButton(
                        onClick = {
                            val updated = item.copy(isEnabled = !item.isEnabled)
                            currentSections[index] = updated
                            val committed = currentSections.toList()
                            pendingLocalCommit = committed
                            coroutineScope.launch {
                                prefManager.setDetailsPageSectionOrderForMedia(mediaType, committed)
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (item.isEnabled) "Hide Section" else "Show Section",
                            tint = if (item.isEnabled) AccentGold else TextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .draggableHandle(
                                onDragStarted = {
                                    isDraggingAny = true
                                    onDraggingChange(true)
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    isDraggingAny = false
                                    onDraggingChange(false)
                                    val committed = currentSections.toList()
                                    pendingLocalCommit = committed
                                    coroutineScope.launch {
                                        prefManager.setDetailsPageSectionOrderForMedia(mediaType, committed)
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
}
