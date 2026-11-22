package com.omnitrack.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.ui.screens.detail.components.formatSmartRating
import com.omnitrack.app.ui.theme.AccentGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Option 1 Auto-Hiding Gold Fast-Scroller with Adaptive Obsidian & Gold Bubble
 * for navigating large 1,000+ item libraries smoothly in Grid or List mode.
 */
@Composable
fun BoxScope.LibraryFastScroller(
    items: List<MediaCardSummary>,
    sortMode: String,
    gridState: LazyGridState? = null,
    listState: LazyListState? = null,
    columnsCount: Int = 3,
    modifier: Modifier = Modifier
) {
    if (items.size < 20 || (gridState == null && listState == null)) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale.US) }
    val zoneId = remember { ZoneId.systemDefault() }

    val isListScrolling by remember(gridState, listState) {
        derivedStateOf {
            gridState?.isScrollInProgress ?: listState?.isScrollInProgress ?: false
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var dragIndex by remember { mutableIntStateOf(0) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var isVisible by remember { mutableStateOf(false) }
    var lastBubbleLabel by remember { mutableStateOf("") }

    LaunchedEffect(isListScrolling, isDragging) {
        if (isListScrolling || isDragging) {
            isVisible = true
        } else {
            delay(1200L)
            isVisible = false
        }
    }

    val containerAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "libraryFastScrollerAlpha"
    )

    val scrollFraction by remember(gridState, listState, items.size, columnsCount) {
        derivedStateOf {
            val total = items.size
            if (total <= 1) return@derivedStateOf 0f
            if (gridState != null) {
                val info = gridState.layoutInfo
                val vis = info.visibleItemsInfo.size.coerceAtLeast(columnsCount)
                val maxTop = (total - vis).coerceAtLeast(1)
                (gridState.firstVisibleItemIndex.toFloat() / maxTop.toFloat()).coerceIn(0f, 1f)
            } else if (listState != null) {
                val info = listState.layoutInfo
                val vis = info.visibleItemsInfo.size.coerceAtLeast(1)
                val maxTop = (total - vis).coerceAtLeast(1)
                (listState.firstVisibleItemIndex.toFloat() / maxTop.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    val activeFraction = if (isDragging) dragFraction else scrollFraction
    val activeIndex = if (isDragging) {
        dragIndex.coerceIn(0, items.lastIndex)
    } else {
        (gridState?.firstVisibleItemIndex ?: listState?.firstVisibleItemIndex ?: 0).coerceIn(0, items.lastIndex)
    }

    val activeItem = items.getOrNull(activeIndex)
    val bubbleLabel = remember(activeItem, sortMode) {
        if (activeItem == null) {
            ""
        } else when (sortMode) {
            "TITLE" -> {
                val ch = activeItem.title.trim().firstOrNull()
                if (ch != null && ch.isLetter()) ch.uppercaseChar().toString() else "#"
            }
            "RATING" -> {
                val r = activeItem.userRating ?: activeItem.communityScore
                if (r != null && r > 0f) "\u2605 ${formatSmartRating(r.toDouble())}" else "Unrated"
            }
            else -> {
                val ts = activeItem.lastUpdated
                if (ts > 0L) {
                    Instant.ofEpochMilli(ts).atZone(zoneId).format(dateFormatter)
                } else {
                    "Recent"
                }
            }
        }
    }

    val thumbWidth by animateDpAsState(if (isDragging) 9.dp else 6.dp, label = "thumbW")
    val thumbHeightDp = if (isDragging) 52.dp else 44.dp
    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
    val latestBubbleLabel by rememberUpdatedState(bubbleLabel)

    val usablePx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
    val thumbTopPx = (activeFraction * usablePx).roundToInt()

    Box(
        modifier = modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(top = 8.dp, bottom = 114.dp)
            .alpha(containerAlpha)
    ) {
        // Floating adaptive Obsidian & Gold preview bubble while dragging
        AnimatedVisibility(
            visible = isDragging && bubbleLabel.isNotBlank(),
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 36.dp)
                .offset {
                    val centered = (thumbTopPx + (thumbHeightPx / 2f) - 20f).roundToInt().coerceAtLeast(0)
                    IntOffset(0, centered)
                }
        ) {
            Box(
                modifier = Modifier
                    .shadow(10.dp, RoundedCornerShape(999.dp), ambientColor = AccentGold, spotColor = AccentGold)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF1C1915))
                    .border(1.5.dp, AccentGold.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bubbleLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentGold
                )
            }
        }

        // Wide 28.dp touch-capture strip that isolates vertical scrub from HorizontalPager
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(28.dp)
                .fillMaxHeight()
                .onSizeChanged { trackHeightPx = it.height.toFloat() }
                .pointerInput(items.size, columnsCount, sortMode) {
                    fun updateFromY(posY: Float) {
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val frac = ((posY - (thumbHeightPx / 2f)) / (h - thumbHeightPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        dragFraction = frac
                        val idx = (frac * (items.size - 1)).roundToInt().coerceIn(0, items.lastIndex)
                        dragIndex = idx
                        val snappedRowIdx = if (gridState != null) (idx / columnsCount) * columnsCount else idx
                        scope.launch {
                            gridState?.scrollToItem(snappedRowIdx)
                            listState?.scrollToItem(idx)
                        }
                    }

                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            lastBubbleLabel = ""
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            updateFromY(offset.y)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            updateFromY(change.position.y)
                            val curLabel = latestBubbleLabel
                            if (curLabel != lastBubbleLabel && curLabel.isNotBlank()) {
                                lastBubbleLabel = curLabel
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 5.dp)
                    .offset { IntOffset(0, thumbTopPx) }
                    .width(thumbWidth)
                    .height(thumbHeightDp)
                    .shadow(6.dp, RoundedCornerShape(999.dp), ambientColor = AccentGold, spotColor = AccentGold)
                    .clip(RoundedCornerShape(999.dp))
                    .background(AccentGold)
            )
        }
    }
}
