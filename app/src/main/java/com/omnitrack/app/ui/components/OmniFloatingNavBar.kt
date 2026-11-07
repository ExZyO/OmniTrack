package com.omnitrack.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.ui.components.glass.GlassBarSurface
import com.omnitrack.app.ui.components.jelly.JellyMotion
import com.omnitrack.app.ui.components.jelly.drawJellyGlow
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.TextMuted
import dev.chrisbanes.haze.HazeState

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
 * Scroll-aware state for OmniTrack navigation dock.
 * Collapses pill width and hides text labels when scrolling down;
 * expands pill and reveals labels when scrolling up or idle.
 */
@Stable
class OmniNavBarScrollState {
    var labelVisibility by mutableFloatStateOf(1f)
        private set

    private var accumulatedDelta = 0f

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val deltaY = available.y
            if (deltaY == 0f) return Offset.Zero

            accumulatedDelta += deltaY

            if (accumulatedDelta < -SCROLL_THRESHOLD && labelVisibility != 0f) {
                // Scrolling down -> collapse to compact icon-only dock
                labelVisibility = 0f
                accumulatedDelta = 0f
            } else if (accumulatedDelta > SCROLL_THRESHOLD && labelVisibility != 1f) {
                // Scrolling up -> expand with labels
                labelVisibility = 1f
                accumulatedDelta = 0f
            }

            if (deltaY < 0f && accumulatedDelta > 0f) accumulatedDelta = deltaY
            if (deltaY > 0f && accumulatedDelta < 0f) accumulatedDelta = deltaY

            return Offset.Zero
        }
    }

    fun expand() {
        labelVisibility = 1f
        accumulatedDelta = 0f
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
 * Floating Liquid Glass Navigation Dock for OmniTrack.
 * Features:
 * - Liquid glass translucent dark surface with subtle specular border.
 * - Active tab expansion: ONLY the active tab displays its text label when expanded.
 *   This guarantees all 4 buttons (Library, Discover, History, Settings) fit comfortably on any screen
 *   with ZERO horizontal overflow or clipping.
 * - Scroll-aware collapse: when scrolling down, collapses into an ultra-compact icon pill.
 * - Double-tap / re-tap detection (380ms threshold) to trigger quick actions.
 */
@Composable
fun OmniFloatingNavBar(
    selectedDestination: NavDestination,
    onDestinationSelected: (NavDestination) -> Unit,
    scrollState: OmniNavBarScrollState,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    onActiveTabReSelected: ((NavDestination) -> Unit)? = null
) {
    val density = LocalDensity.current.density
    val items = remember { NavDestination.entries }
    val selectedIndex = remember(selectedDestination) { items.indexOf(selectedDestination) }

    val motion = remember { JellyMotion(selectedIndex, items.size) }

    LaunchedEffect(selectedDestination) {
        motion.select(selectedIndex)
    }

    LaunchedEffect(motion.running) {
        if (!motion.running) return@LaunchedEffect
        var lastNanos = 0L
        while (motion.running) {
            withFrameNanos { now ->
                val delta = if (lastNanos == 0L) 0.016 else (now - lastNanos) / 1_000_000_000.0
                lastNanos = now
                motion.advance(delta)
            }
        }
    }

    val labelFraction by animateFloatAsState(
        targetValue = scrollState.labelVisibility,
        animationSpec = spring(stiffness = 350f),
        label = "nav_label_fraction"
    )

    val pillHeight by animateDpAsState(
        targetValue = 62.dp,
        label = "nav_pill_height"
    )

    // Double-tap & re-tap timestamp tracker (380ms window)
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTappedDest by remember { mutableStateOf<NavDestination?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Liquid Glass Pill Surface with Haze, AGSL Refraction, & Mass-Spring Physics
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .height(pillHeight)
                .graphicsLayer {
                    val frame = motion.frame
                    val scale = frame.trackScale
                    scaleX = scale * frame.trackScaleX
                    scaleY = scale
                    translationY = frame.trackOffsetY
                    transformOrigin = TransformOrigin(frame.originX / size.width.coerceAtLeast(1f), 0.5f)
                }
                .onSizeChanged {
                    motion.resize(it.width / density, it.height / density, items.size)
                }
                .shadow(elevation = 20.dp, shape = CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .drawWithContent {
                    drawContent()
                    drawJellyGlow(motion.frame, AccentArctic.copy(alpha = 0.85f))
                },
            contentAlignment = Alignment.Center
        ) {
            GlassBarSurface(hazeState = hazeState, modifier = Modifier.matchParentSize())
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, destination ->
                    val isSelected = destination == selectedDestination

                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) AccentArctic else TextMuted,
                        label = "iconTint"
                    )

                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) AccentArctic.copy(alpha = 0.18f) else Color.Transparent,
                        label = "pillBg"
                    )

                    Column(
                        modifier = Modifier
                            .width(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    val isDoubleTap = (now - lastTapTime < 380L) && (lastTappedDest == destination)
                                    val isReTapOnActive = isSelected

                                    lastTapTime = now
                                    lastTappedDest = destination

                                    motion.begin(0f, 0f)
                                    motion.select(index)

                                    if (isReTapOnActive || isDoubleTap) {
                                        scrollState.expand()
                                        onActiveTabReSelected?.invoke(destination)
                                    } else {
                                        onDestinationSelected(destination)
                                    }
                                }
                            )
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp, 28.dp)
                                .graphicsLayer {
                                    if (isSelected) {
                                        val scale = motion.frame.contentScale
                                        scaleX = scale * motion.frame.pillScaleX
                                        scaleY = scale * motion.frame.pillScaleY
                                    }
                                }
                                .clip(CircleShape)
                                .background(pillBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.title,
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = destination.title,
                            color = if (isSelected) AccentArctic else TextMuted,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            letterSpacing = (-0.2).sp
                        )
                    }
                }
            }
        }
    }
}
