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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.TextMuted

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
    onActiveTabReSelected: ((NavDestination) -> Unit)? = null
) {
    val labelFraction by animateFloatAsState(
        targetValue = scrollState.labelVisibility,
        animationSpec = spring(stiffness = 350f),
        label = "nav_label_fraction"
    )

    val pillHeight by animateDpAsState(
        targetValue = 54.dp,
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
        // Liquid Glass Pill Surface
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .height(pillHeight)
                .shadow(elevation = 20.dp, shape = CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(Color(0xE612141C)) // Liquid translucent dark surface
                .border(width = 1.dp, color = Color(0x28FFFFFF), shape = CircleShape)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavDestination.entries.forEach { destination ->
                    val isSelected = destination == selectedDestination
                    val showLabel = isSelected && labelFraction > 0.5f

                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) AccentArctic else TextMuted,
                        label = "iconTint"
                    )

                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) AccentArctic.copy(alpha = 0.18f) else Color.Transparent,
                        label = "pillBg"
                    )

                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(pillBg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    val isDoubleTap = (now - lastTapTime < 380L) && (lastTappedDest == destination)
                                    val isReTapOnActive = isSelected

                                    lastTapTime = now
                                    lastTappedDest = destination

                                    if (isReTapOnActive || isDoubleTap) {
                                        scrollState.expand()
                                        onActiveTabReSelected?.invoke(destination)
                                    } else {
                                        onDestinationSelected(destination)
                                    }
                                }
                            )
                            .padding(horizontal = if (showLabel) 14.dp else 12.dp)
                            .animateContentSize(animationSpec = spring(stiffness = 400f)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = destination.title,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )

                        // Only the active tab expands its label when dock is expanded
                        if (showLabel) {
                            Text(
                                text = destination.title,
                                color = AccentArctic,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
