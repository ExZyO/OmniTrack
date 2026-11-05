package com.omnitrack.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.alpha
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
 * Features scroll-aware collapse, liquid glass translucent surface,
 * spring-based fluid animations, and double-tap/re-tap actions (e.g. quick search trigger).
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
        animationSpec = spring(stiffness = 300f),
        label = "nav_label_fraction"
    )

    // Dynamic horizontal padding: pill shrinks from 24dp to 64dp padding when labels collapse
    val horizontalPadding by animateDpAsState(
        targetValue = (24 + (40 * (1f - labelFraction))).dp,
        animationSpec = spring(stiffness = 300f),
        label = "nav_pill_padding"
    )

    val pillHeight by animateDpAsState(
        targetValue = (50 + (12 * labelFraction)).dp,
        animationSpec = spring(stiffness = 300f),
        label = "nav_pill_height"
    )

    // Double-tap & re-tap timestamp tracker (380ms window)
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTappedDest by remember { mutableStateOf<NavDestination?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Liquid Glass Pill Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pillHeight)
                .shadow(elevation = 20.dp, shape = CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(Color(0xE612141C)) // Liquid translucent dark surface
                .border(width = 1.dp, color = Color(0x28FFFFFF), shape = CircleShape)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavDestination.entries.forEach { destination ->
                    val isSelected = destination == selectedDestination

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

                                    // If already active or rapid double tap, fire the re-select action (e.g. search trigger)
                                    if (isReTapOnActive || isDoubleTap) {
                                        scrollState.expand()
                                        onActiveTabReSelected?.invoke(destination)
                                    } else {
                                        onDestinationSelected(destination)
                                    }
                                }
                            )
                            .padding(horizontal = (10 * labelFraction + 8).dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = destination.title,
                            tint = iconTint,
                            modifier = Modifier.size(19.dp)
                        )

                        // Smooth label fade and collapse
                        if (labelFraction > 0.15f) {
                            Text(
                                text = destination.title,
                                color = if (isSelected) AccentArctic else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.alpha(labelFraction)
                            )
                        }
                    }
                }
            }
        }
    }
}
