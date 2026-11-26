package com.omnitrack.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val HERO_STRETCH_MAX = 180.dp
private const val HERO_STRETCH_DRAG_RESISTANCE = 0.55f
private const val HERO_STRETCH_RELEASE_ABSORB = 0.35f
private const val HERO_STRETCH_FRAME_TO_VELOCITY = 60f

const val HERO_BACKGROUND_PARALLAX = 0.045f
const val HERO_BACKGROUND_SCALE = 1.0f
const val HERO_CONTENT_PARALLAX = 0.18f
private const val HERO_SWIPE_THRESHOLD_FRACTION = 0.16f
private const val HERO_SWIPE_VELOCITY_THRESHOLD = 300f

fun heroBackgroundOverscan(pageOffset: Float): Float =
    1f + abs(pageOffset).coerceIn(0f, 1f) * 0.05f

private val HeroStretchReleaseSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 320f
)

private val HeroStretchBounceSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 110f
)

private val HeroSwipeSettleSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

@Immutable
data class HeroPageLayer(
    val itemIndex: Int,
    val visibility: Float,
    val offset: Float
)

fun heroPageOffset(
    pagerState: PagerState,
    page: Int
): Float = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

fun heroVisibleLayers(
    pagerState: PagerState,
    itemCount: Int
): List<HeroPageLayer> {
    if (itemCount <= 0) return emptyList()
    if (itemCount == 1) return listOf(HeroPageLayer(0, 1f, 0f))
    val currentPage = pagerState.currentPage
    val maxPage = (pagerState.pageCount - 1).coerceAtLeast(0)
    return listOf(
        currentPage,
        (currentPage - 1).coerceAtLeast(0),
        (currentPage + 1).coerceAtMost(maxPage)
    ).distinct()
        .mapNotNull { index ->
            val pageOffset = heroPageOffset(pagerState, index)
            val visibility = (1f - abs(pageOffset)).coerceIn(0f, 1f)
            if (visibility <= 0f) {
                null
            } else {
                HeroPageLayer(
                    itemIndex = ((index % itemCount) + itemCount) % itemCount,
                    visibility = visibility,
                    offset = pageOffset
                )
            }
        }
        .sortedBy(HeroPageLayer::visibility)
}

fun heroPageForItem(currentPage: Int, itemIndex: Int, itemCount: Int): Int {
    if (itemCount <= 1) return 0
    val base = currentPage.toLong() - ((currentPage % itemCount + itemCount) % itemCount) + itemIndex
    return listOf(base - itemCount, base, base + itemCount)
        .filter { it in 0L until Int.MAX_VALUE.toLong() }
        .minBy { abs(it - currentPage) }
        .toInt()
}

/**
 * Nuvio-style horizontal swipe gesture driver for stacked in-place hero crossfades.
 * Yields immediately on vertical drags so parent LazyColumn / PullToRefresh stays silky.
 */
fun Modifier.homeHeroPagerGesture(
    pagerState: PagerState,
    itemCount: Int,
    coroutineScope: CoroutineScope,
    onDragStateChanged: (Boolean) -> Unit = {}
): Modifier {
    if (itemCount <= 1) return this

    return pointerInput(pagerState, itemCount) {
        var settleJob: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            val widthPx = size.width.toFloat().takeIf { it > 0f } ?: return@awaitEachGesture
            val velocityTracker = VelocityTracker().apply {
                addPosition(down.uptimeMillis, down.position)
            }
            val maxPage = (pagerState.pageCount - 1).coerceAtLeast(0)
            val anchorPage = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .roundToInt()
                .coerceIn(0, maxPage)
            var totalDx = 0f
            var totalDy = 0f
            var appliedDragPx = 0f
            var dragging = false
            var snapped = false

            try {
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    velocityTracker.addPosition(change.uptimeMillis, change.position)

                    if (!change.pressed) {
                        if (dragging) {
                            change.consume()
                            snapped = true
                            val targetPage = resolveHeroTargetPage(
                                anchorPage = anchorPage,
                                currentContinuousPage = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                                pageCount = pagerState.pageCount,
                                totalDx = totalDx,
                                velocityX = velocityTracker.calculateVelocity().x,
                                widthPx = widthPx
                            )
                            settleJob?.cancel()
                            settleJob = coroutineScope.launch {
                                runCatching {
                                    pagerState.animateScrollToPage(
                                        page = targetPage,
                                        animationSpec = HeroSwipeSettleSpring
                                    )
                                }
                            }
                        }
                        break
                    }

                    val delta = change.position - change.previousPosition
                    totalDx += delta.x
                    totalDy += delta.y

                    if (!dragging) {
                        val horizontalDrag =
                            abs(totalDx) > viewConfiguration.touchSlop && abs(totalDx) > abs(totalDy)
                        val verticalDrag =
                            abs(totalDy) > viewConfiguration.touchSlop && abs(totalDy) > abs(totalDx)

                        when {
                            verticalDrag -> break
                            horizontalDrag -> {
                                settleJob?.cancel()
                                dragging = true
                                onDragStateChanged(true)
                                val clampedInit = (-totalDx).coerceIn(-widthPx, widthPx)
                                appliedDragPx = clampedInit
                                if (clampedInit != 0f) {
                                    pagerState.dispatchRawDelta(clampedInit)
                                }
                                change.consume()
                                continue
                            }
                            else -> continue
                        }
                    }

                    val nextApplied = (appliedDragPx - delta.x).coerceIn(-widthPx, widthPx)
                    val clampedStep = nextApplied - appliedDragPx
                    appliedDragPx = nextApplied
                    if (clampedStep != 0f) {
                        pagerState.dispatchRawDelta(clampedStep)
                    }
                    change.consume()
                }
            } finally {
                if (dragging && !snapped) {
                    val targetPage = resolveHeroTargetPage(
                        anchorPage = anchorPage,
                        currentContinuousPage = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                        pageCount = pagerState.pageCount,
                        totalDx = totalDx,
                        velocityX = runCatching { velocityTracker.calculateVelocity().x }.getOrDefault(0f),
                        widthPx = widthPx
                    )
                    settleJob?.cancel()
                    settleJob = coroutineScope.launch {
                        runCatching {
                            pagerState.animateScrollToPage(
                                page = targetPage,
                                animationSpec = HeroSwipeSettleSpring
                            )
                        }
                    }
                }
                if (dragging) {
                    onDragStateChanged(false)
                }
            }
        }
    }
}

private fun resolveHeroTargetPage(
    anchorPage: Int,
    currentContinuousPage: Float,
    pageCount: Int,
    totalDx: Float,
    velocityX: Float,
    widthPx: Float
): Int {
    val maxPage = (pageCount - 1).coerceAtLeast(0)
    val clampedAnchor = anchorPage.coerceIn(0, maxPage)
    return when {
        abs(velocityX) > HERO_SWIPE_VELOCITY_THRESHOLD -> {
            if (velocityX > 0f) (clampedAnchor - 1).coerceAtLeast(0)
            else (clampedAnchor + 1).coerceAtMost(maxPage)
        }
        abs(totalDx) > widthPx * HERO_SWIPE_THRESHOLD_FRACTION -> {
            if (totalDx > 0f) (clampedAnchor - 1).coerceAtLeast(0)
            else (clampedAnchor + 1).coerceAtMost(maxPage)
        }
        else -> currentContinuousPage.roundToInt().coerceIn(0, maxPage)
    }
}

@Stable
class HeroStretchState internal constructor(
    private val scope: CoroutineScope,
    private val maxStretchPx: Float,
    private val isAtTop: () -> Boolean
) {
    private val stretchAnim = Animatable(0f)
    private var settleJob: Job? = null
    private var flingAbsorbed = false

    val stretchPx: Float get() = stretchAnim.value

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) {
                flingAbsorbed = false
            }
            val stretch = stretchAnim.value
            if (available.y < 0f && stretch > 0f) {
                val consumedY = available.y.coerceAtLeast(-stretch)
                snapTo(stretch + consumedY)
                return Offset(0f, consumedY)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (available.y <= 0f || !isAtTop()) return Offset.Zero

            if (source == NestedScrollSource.UserInput) {
                val stretch = stretchAnim.value
                val resistance = HERO_STRETCH_DRAG_RESISTANCE *
                    (1f - stretch / maxStretchPx).coerceIn(0f, 1f)
                snapTo(stretch + available.y * resistance)
            } else if (!flingAbsorbed) {
                flingAbsorbed = true
                if (settleJob?.isActive != true) {
                    settle(available.y * HERO_STRETCH_FRAME_TO_VELOCITY, HeroStretchBounceSpring)
                }
            }
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (stretchAnim.value <= 0.5f) return Velocity.Zero
            if (settleJob?.isActive != true) {
                settle(available.y * HERO_STRETCH_RELEASE_ABSORB, HeroStretchReleaseSpring)
            }
            return Velocity(0f, available.y)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            flingAbsorbed = false
            if (stretchAnim.value > 0.5f && settleJob?.isActive != true) {
                settle(0f, HeroStretchReleaseSpring)
            }
            return if (available.y > 0f && isAtTop()) {
                Velocity(0f, available.y)
            } else {
                Velocity.Zero
            }
        }
    }

    private fun snapTo(value: Float) {
        scope.launch { stretchAnim.snapTo(value.coerceIn(0f, maxStretchPx)) }
    }

    private fun settle(initialVelocity: Float, spec: SpringSpec<Float>) {
        settleJob = scope.launch {
            stretchAnim.animateTo(
                targetValue = 0f,
                animationSpec = spec,
                initialVelocity = initialVelocity
            )
        }
    }
}

@Composable
fun rememberHeroStretchState(listState: LazyListState): HeroStretchState {
    val scope = rememberCoroutineScope()
    val maxStretchPx = with(LocalDensity.current) { HERO_STRETCH_MAX.toPx() }
    return remember(listState, maxStretchPx) {
        HeroStretchState(scope, maxStretchPx) { !listState.canScrollBackward }
    }
}

fun Modifier.heroStretchHeight(baseHeight: Dp, stretchPx: () -> Float): Modifier =
    layout { measurable, constraints ->
        val extra = stretchPx().coerceAtLeast(0f).roundToInt()
        val height = baseHeight.roundToPx() + extra
        val placeable = measurable.measure(
            constraints.copy(minHeight = height, maxHeight = height)
        )
        layout(placeable.width, height) { placeable.place(0, 0) }
    }

fun Modifier.heroStretchZoom(stretchPx: () -> Float): Modifier = graphicsLayer {
    val zoom = 1f + stretchPx().coerceAtLeast(0f) / size.height.coerceAtLeast(1f)
    transformOrigin = TransformOrigin(0.5f, 0f)
    scaleX = zoom
    scaleY = zoom
}

fun heroScrollBackdropScale(scrollOffsetPx: Float): Float =
    (1f + scrollOffsetPx.coerceAtLeast(0f) * 0.00008f).coerceAtMost(1.06f)

fun heroScrollBackdropAlpha(scrollOffsetPx: Float, heroHeightPx: Float): Float {
    val denom = (heroHeightPx * 0.88f).coerceAtLeast(1f)
    val t = (scrollOffsetPx.coerceAtLeast(0f) / denom).coerceIn(0f, 1f)
    return 1f - t * 0.90f
}

fun heroScrollForegroundAlpha(scrollOffsetPx: Float, heroHeightPx: Float): Float {
    val denom = (heroHeightPx * 0.55f).coerceAtLeast(1f)
    val t = (scrollOffsetPx.coerceAtLeast(0f) / denom).coerceIn(0f, 1f)
    return 1f - t
}
