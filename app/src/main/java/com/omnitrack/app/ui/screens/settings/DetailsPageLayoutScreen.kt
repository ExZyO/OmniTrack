package com.omnitrack.app.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.omnitrack.app.data.preferences.DetailSectionItem
import com.omnitrack.app.data.preferences.DetailSectionKey
import com.omnitrack.app.data.preferences.DetailsPageLayoutDefaults
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * State manager for smooth, multi-row drag-and-drop reordering inside LazyColumn.
 * Neutralizes layout jumps and animates settle on release.
 */
private class SectionDragDropState(
    val lazyListState: LazyListState,
    private val coroutineScope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit,
    private val onDragFinished: () -> Unit
) {
    var draggedDistance by mutableFloatStateOf(0f)
        private set

    var draggingItemKey by mutableStateOf<String?>(null)
        private set

    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    val settleAnimatable = Animatable(0f)
    var isSettling by mutableStateOf(false)
        private set

    val isDragging: Boolean
        get() = draggingItemKey != null || isSettling

    private val currentElement: LazyListItemInfo?
        get() = draggingItemKey?.let { key ->
            lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        }

    fun onStartDrag(key: String, initialIndex: Int) {
        if (isSettling) return
        draggingItemKey = key
        draggingItemIndex = initialIndex
        draggedDistance = 0f
    }

    fun onDrag(changeAmount: Float) {
        if (isSettling) return
        val currentKey = draggingItemKey ?: return
        val currentDraggingIdx = draggingItemIndex ?: return
        draggedDistance += changeAmount

        val currentElem = currentElement ?: return

        val startOffset = currentElem.offset + draggedDistance
        val itemCenter = startOffset + currentElem.size / 2f

        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val firstItem = visibleItems.firstOrNull()
        val lastItem = visibleItems.lastOrNull()

        val targetElement = when {
            firstItem != null && itemCenter < firstItem.offset + firstItem.size / 2f -> {
                if (currentDraggingIdx > firstItem.index) firstItem else null
            }
            lastItem != null && itemCenter > lastItem.offset + lastItem.size / 2f -> {
                if (currentDraggingIdx < lastItem.index) lastItem else null
            }
            else -> {
                visibleItems.find { item ->
                    item.key != currentKey &&
                        item.index != currentDraggingIdx &&
                        item.offset <= itemCenter &&
                        (item.offset + item.size) >= itemCenter
                }
            }
        }

        if (targetElement != null) {
            val fromIndex = currentDraggingIdx
            val toIndex = targetElement.index
            if (fromIndex != toIndex) {
                onMove(fromIndex, toIndex)
                draggingItemIndex = toIndex
                draggedDistance += currentElem.offset - targetElement.offset
            }
        }
    }

    fun onStopDrag() {
        if (draggingItemKey == null || isSettling) return

        val currentDistance = draggedDistance
        if (currentDistance != 0f) {
            isSettling = true
            coroutineScope.launch {
                try {
                    settleAnimatable.snapTo(currentDistance)
                    settleAnimatable.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                } finally {
                    draggedDistance = 0f
                    draggingItemKey = null
                    draggingItemIndex = null
                    isSettling = false
                    onDragFinished()
                }
            }
        } else {
            draggingItemKey = null
            draggingItemIndex = null
            onDragFinished()
        }
    }
}

/**
 * Dedicated Fullscreen Details Page Layout & Sections Customizer.
 * Reorder sections with fluid drag-and-drop (no arrow buttons) and toggle visibility.
 * Grounded in the exact design of the Artwork & Metadata Sources page.
 */
@Composable
fun DetailsPageLayoutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefManager = remember { PreferenceManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val savedSections by prefManager.detailsPageSectionOrder.collectAsState(initial = DetailsPageLayoutDefaults.defaultSections)

    val currentSections = remember {
        mutableStateListOf<DetailSectionItem>().apply { addAll(savedSections) }
    }

    LaunchedEffect(savedSections) {
        if (currentSections.isEmpty() || currentSections.map { it.key } != savedSections.map { it.key }) {
            currentSections.clear()
            currentSections.addAll(savedSections)
        }
    }

    val lazyListState = rememberLazyListState()

    val dragDropState = remember(lazyListState) {
        SectionDragDropState(
            lazyListState = lazyListState,
            coroutineScope = coroutineScope,
            onMove = { fromIndex, toIndex ->
                if (fromIndex in currentSections.indices && toIndex in currentSections.indices && fromIndex != toIndex) {
                    val moved = currentSections.removeAt(fromIndex)
                    currentSections.add(toIndex, moved)
                }
            },
            onDragFinished = {
                coroutineScope.launch {
                    prefManager.setDetailsPageSectionOrder(currentSections.toList())
                }
            }
        )
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
                    text = "Section Order & Visibility",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = {
                    currentSections.clear()
                    currentSections.addAll(DetailsPageLayoutDefaults.defaultSections)
                    coroutineScope.launch {
                        prefManager.setDetailsPageSectionOrder(DetailsPageLayoutDefaults.defaultSections)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset to Defaults",
                    tint = TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Subtitle Instructions Row
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
                text = "Drag ≡ on the right to reorder. Tap the eye to toggle visibility on detail pages.",
                color = TextMuted,
                fontSize = 11.5.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Reorderable LazyColumn
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(
                items = currentSections,
                key = { _, item -> item.key.name }
            ) { index, item ->
                val sectionKey = item.key.name
                val isBeingDragged = dragDropState.draggingItemKey == sectionKey
                val isSettlingThis = dragDropState.isSettling && dragDropState.draggingItemKey == sectionKey

                val dragDistance = if (isBeingDragged) {
                    dragDropState.draggedDistance
                } else if (isSettlingThis) {
                    dragDropState.settleAnimatable.value
                } else {
                    0f
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isBeingDragged || isSettlingThis) 10f else 1f)
                        .graphicsLayer {
                            translationY = dragDistance
                        }
                        .then(
                            if (isBeingDragged) {
                                Modifier
                                    .shadow(12.dp, RoundedCornerShape(14.dp))
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(1.5.dp, AccentArctic, RoundedCornerShape(14.dp))
                            } else {
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (item.isEnabled) DarkSurfaceElevated else Color.White.copy(alpha = 0.03f))
                                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Order Badge
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (item.isEnabled) AccentArctic.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = if (item.isEnabled) AccentArctic else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title & Description
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = item.key.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (item.isEnabled) TextPrimary else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.key.description,
                            fontSize = 11.5.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Eye Visibility Toggle
                    IconButton(
                        onClick = {
                            val updated = item.copy(isEnabled = !item.isEnabled)
                            currentSections[index] = updated
                            coroutineScope.launch {
                                prefManager.setDetailsPageSectionOrder(currentSections.toList())
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (item.isEnabled) "Hide Section" else "Show Section",
                            tint = if (item.isEnabled) AccentArctic else TextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Fluid Drag Handle (≡) - No arrow buttons!
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .pointerInput(sectionKey) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                                    dragDropState.onStartDrag(sectionKey, index)
                                    val pointerId = down.id
                                    while (true) {
                                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                        if (change.isConsumed) break
                                        val delta = change.positionChange()
                                        dragDropState.onDrag(delta.y)
                                        change.consume()
                                        if (event.changes.all { !it.pressed }) break
                                    }
                                    dragDropState.onStopDrag()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = if (isBeingDragged) AccentArctic else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
