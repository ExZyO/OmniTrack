package com.omnitrack.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.ui.components.glass.GlassBarSurface
import com.omnitrack.app.ui.components.jelly.JellyMotion
import com.omnitrack.app.ui.components.jelly.drawJellyGlow
import com.omnitrack.app.ui.components.jelly.drawJellyPill
import com.omnitrack.app.ui.components.jelly.jellyPillPath
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.TextMuted
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.max

enum class NavDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    LIBRARY("Library", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    DISCOVER("Discover", Icons.Filled.Explore, Icons.Outlined.Explore),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

/**
 * Scroll-responsive state for OmniTrack adaptive navigation dock.
 * Adapts between expanded mode (64dp height, labels visible, 28dp padding)
 * and compact mode (48dp height, icons only, 58dp padding) on scroll.
 * NEVER translates offscreen — content smoothly scrolls through and behind it.
 */
@Stable
class OmniNavBarScrollState {
    var labelVisibility by mutableFloatStateOf(1f)
        private set

    private var accumulatedDelta = 0f

    fun expand() {
        labelVisibility = 1f
        accumulatedDelta = 0f
    }

    fun collapse() {
        labelVisibility = 0f
        accumulatedDelta = 0f
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val deltaY = available.y
            if (deltaY == 0f) return Offset.Zero

            accumulatedDelta += deltaY

            if (accumulatedDelta < -SCROLL_THRESHOLD && labelVisibility != 0f) {
                // Scrolling down -> snap collapse
                labelVisibility = 0f
                accumulatedDelta = 0f
            } else if (accumulatedDelta > SCROLL_THRESHOLD && labelVisibility != 1f) {
                // Scrolling up -> snap expand
                labelVisibility = 1f
                accumulatedDelta = 0f
            }

            if (deltaY < 0f && accumulatedDelta > 0f) accumulatedDelta = deltaY
            if (deltaY > 0f && accumulatedDelta < 0f) accumulatedDelta = deltaY

            return Offset.Zero
        }
    }

    companion object {
        private const val SCROLL_THRESHOLD = 50f
    }
}

@Composable
fun rememberOmniNavBarScrollState(): OmniNavBarScrollState {
    return remember { OmniNavBarScrollState() }
}

/**
 * Nuvio-style Floating Liquid Glass Navigation Bar for OmniTrack.
 * Features:
 * - Dynamic adaptive morphing: 64dp expanded (with labels) vs 48dp compact (icons only).
 * - Anchored at BottomCenter with zero off-screen hiding, enabling full liquid glass scroll-through.
 * - AGSL runtime shader specular refraction + cyan glass sheen + specular highlight.
 * - Glass glow ON: radial ambient glow + specular rim lighting + active jelly pill halo.
 * - Organic Jelly Spring physics with mass-spring-damper squish, drag, and inertia.
 * - Active tab double-tap / re-tap detection (380ms threshold) for scroll-to-top and search toggle.
 */
@Composable
fun OmniFloatingNavBar(
    selectedDestination: NavDestination,
    onDestinationSelected: (NavDestination) -> Unit,
    scrollState: OmniNavBarScrollState,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    glowEnabled: Boolean = true,
    onActiveTabReSelected: ((NavDestination) -> Unit)? = null
) {
    val density = LocalDensity.current.density
    val items = remember { NavDestination.entries }
    val selectedIndex = remember(selectedDestination) { items.indexOf(selectedDestination) }

    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val visualSelectedIndex = visualNavIndex(selectedIndex, items.size, isRtl)

    val motion = remember(items.size, isRtl) { JellyMotion(visualSelectedIndex, items.size) }
    val currentItems by rememberUpdatedState(items)
    val currentIsRtl by rememberUpdatedState(isRtl)
    val currentSelectedDest by rememberUpdatedState(selectedDestination)

    LaunchedEffect(visualSelectedIndex, items.size) {
        motion.select(visualSelectedIndex)
    }

    LaunchedEffect(motion.running) {
        if (!motion.running) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (motion.running) {
            withFrameNanos { now ->
                motion.advance((now - previous) / 1_000_000_000.0)
                previous = now
            }
        }
    }

    val glowStrength by animateFloatAsState(
        targetValue = if (glowEnabled) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "nav_glow_strength"
    )

    val labelFraction by animateFloatAsState(
        targetValue = scrollState.labelVisibility,
        animationSpec = spring(stiffness = 380f, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "jelly_labels"
    )

    val trackHeight = 48.dp + 16.dp * labelFraction
    val horizontalPadding = 58.dp - 30.dp * labelFraction
    val selectedSurface = AccentArctic.copy(alpha = 0.18f)

    // Double-tap & re-tap detection (380ms window)
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTappedDest by remember { mutableStateOf<NavDestination?>(null) }

    val handleTabClick: (NavDestination) -> Unit = { destination ->
        val now = System.currentTimeMillis()
        val isDoubleTap = (now - lastTapTime < 380L) && (lastTappedDest == destination)
        val isReTapOnActive = (destination == currentSelectedDest)

        lastTapTime = now
        lastTappedDest = destination

        if (isReTapOnActive || isDoubleTap) {
            scrollState.expand()
            onActiveTabReSelected?.invoke(destination)
        } else {
            onDestinationSelected(destination)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .height(trackHeight)
                .onSizeChanged {
                    motion.resize(it.width / density, it.height / density, items.size)
                }
                .pointerInput(motion, density, items.size, isRtl) {
                    detectJellyTabGestures(
                        motion = motion,
                        density = density,
                        currentItems = { currentItems },
                        isRtl = { currentIsRtl },
                        selectedDest = { currentSelectedDest },
                        onTabClicked = { logicalIndex ->
                            currentItems.getOrNull(logicalIndex)?.let { handleTabClick(it) }
                        }
                    )
                }
        ) {
            // Track scale transform
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val frame = motion.frame
                        scaleX = frame.trackScale
                        scaleY = frame.trackScale
                    }
            ) {
                // Secondary squish and touch-origin translation
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val frame = motion.frame
                            transformOrigin = TransformOrigin(
                                if (size.width > 0) frame.originX * density / size.width else 0.5f,
                                0.5f
                            )
                            scaleX = frame.trackScaleX
                            translationY = frame.trackOffsetY * density
                        }
                ) {
                    // Inertial drag panel offset
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                translationX = motion.frame.panelOffset * density
                            }
                    ) {
                        // 1. Liquid Glass Surface with Refraction & Ambient Cyan Glow
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(50))
                                .drawWithContent {
                                    drawContent()
                                    drawJellyGlow(motion.frame, AccentArctic.copy(alpha = glowStrength))
                                }
                        ) {
                            GlassBarSurface(hazeState, Modifier.matchParentSize(), glowStrength)
                        }

                        // 2. Inactive Tab Row (clipped outside the active pill)
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .drawWithContent {
                                    if (selectedIndex >= 0) {
                                        clipPath(jellyPillPath(motion.frame, items.size), ClipOp.Difference) {
                                            this@drawWithContent.drawContent()
                                        }
                                    } else {
                                        drawContent()
                                    }
                                }
                        ) {
                            JellyTabRow(
                                destinations = items,
                                selectedDestination = selectedDestination,
                                labelFraction = labelFraction,
                                motion = motion,
                                active = false,
                                modifier = Modifier.matchParentSize()
                            )
                        }

                        // 3. Active Tab Row (clipped inside active jelly pill with glowing background)
                        if (selectedIndex >= 0) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clearAndSetSemantics {}
                                    .drawWithContent {
                                        drawJellyPill(
                                            motion.frame,
                                            items.size,
                                            selectedSurface,
                                            AccentArctic.copy(alpha = glowStrength)
                                        ) { drawContent() }
                                    }
                            ) {
                                JellyTabRow(
                                    destinations = items,
                                    selectedDestination = selectedDestination,
                                    labelFraction = labelFraction,
                                    motion = motion,
                                    active = true,
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                        }

                        // 4. Accessibility Tab Targets
                        JellyTabTargets(
                            destinations = items,
                            selectedDestination = selectedDestination,
                            motion = motion,
                            onTabClicked = handleTabClick,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JellyTabRow(
    destinations: List<NavDestination>,
    selectedDestination: NavDestination,
    labelFraction: Float,
    motion: JellyMotion,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (active) AccentArctic else TextMuted
    val iconSize = 24.dp
    val labelHeight = 15.dp
    val iconTint = if (active) Color.White else color

    Row(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        destinations.forEach { destination ->
            val isCurrent = destination == selectedDestination
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val scale = if (active) motion.frame.contentScale else 1f
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(iconSize)
                            .graphicsLayer {
                                translationY = 2.dp.toPx() * labelFraction
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCurrent) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = iconTint
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(labelHeight * labelFraction)
                            .fillMaxWidth()
                            .clipToBounds()
                            .alpha(labelFraction)
                    ) {
                        Text(
                            text = destination.title,
                            color = color,
                            style = TextStyle(
                                fontSize = 11.5.sp,
                                lineHeight = 14.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JellyTabTargets(
    destinations: List<NavDestination>,
    selectedDestination: NavDestination,
    motion: JellyMotion,
    onTabClicked: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(modifier = modifier.padding(horizontal = 4.dp).selectableGroup()) {
        destinations.forEachIndexed { index, destination ->
            val isSelected = destination == selectedDestination
            val visualIndex = visualNavIndex(index, destinations.size, isRtl)
            val onClick = {
                motion.select(visualIndex)
                onTabClicked(destination)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                    .clearAndSetSemantics {
                        role = Role.Tab
                        selected = isSelected
                        contentDescription = destination.title
                        onClick { onClick(); true }
                    }
            )
        }
    }
}

private fun visualNavIndex(logicalIndex: Int, count: Int, isRtl: Boolean): Int =
    if (logicalIndex in 0 until count && isRtl) count - 1 - logicalIndex else logicalIndex

private fun logicalNavIndex(visualIndex: Int, count: Int, isRtl: Boolean): Int =
    if (visualIndex in 0 until count && isRtl) count - 1 - visualIndex else visualIndex

private suspend fun PointerInputScope.detectJellyTabGestures(
    motion: JellyMotion,
    density: Float,
    currentItems: () -> List<NavDestination>,
    isRtl: () -> Boolean,
    selectedDest: () -> NavDestination,
    onTabClicked: (Int) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        motion.begin(down.position.x / density, down.position.y / density)
        var claimed = false
        var finished = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!motion.dragging) {
                    finished = true
                    break
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.count { it.pressed } > 1) break
                val delta = change.position - down.position
                if (max(abs(delta.x), abs(delta.y)) > viewConfiguration.touchSlop) claimed = true
                if (claimed) change.consume()
                awaitPointerEvent(PointerEventPass.Main)
                if (!motion.dragging) {
                    finished = true
                    break
                }
                if (change.isConsumed && !claimed) break
                motion.drag(delta.x / density, delta.y / density)
                if (!change.pressed) {
                    val visualIndex = motion.finish()
                    val items = currentItems()
                    val logicalIndex = logicalNavIndex(visualIndex, items.size, isRtl())
                    finished = true
                    onTabClicked(logicalIndex)
                    change.consume()
                    break
                }
            }
        } finally {
            if (!finished) {
                val items = currentItems()
                val selectedIndex = items.indexOf(selectedDest())
                motion.cancel(visualNavIndex(selectedIndex, items.size, isRtl()))
            }
        }
    }
}
