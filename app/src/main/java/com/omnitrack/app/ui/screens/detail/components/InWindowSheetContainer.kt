package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.omnitrack.app.ui.theme.AmoledBlack

/**
 * Reusable Poweramp-style pull-down-to-step-back physics modifier.
 * Arms strictly when the child scroll container is at the top of its scroll range.
 */
fun Modifier.powerampPullDownToBack(
    isAtTop: () -> Boolean,
    onBack: () -> Unit,
    dismissThresholdPx: Float = 155f
): Modifier = composed {
    val latestIsAtTop by rememberUpdatedState(isAtTop)
    val latestOnBack by rememberUpdatedState(onBack)

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isPullDownArmed by remember { mutableStateOf(false) }
    var scrollConsumedInGesture by remember { mutableFloatStateOf(0f) }

    val animatedOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "powerampPullDownOffset"
    )

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!latestIsAtTop()) {
                    isPullDownArmed = false
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (consumed.y != 0f) {
                    scrollConsumedInGesture += kotlin.math.abs(consumed.y)
                }
                if (source == NestedScrollSource.UserInput &&
                    scrollConsumedInGesture == 0f &&
                    latestIsAtTop() &&
                    available.y > 0f
                ) {
                    isPullDownArmed = true
                }
                if (isPullDownArmed && available.y > 0f && latestIsAtTop()) {
                    dragOffsetY += available.y * 0.55f
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val shouldDismiss = isPullDownArmed && dragOffsetY > dismissThresholdPx
                isPullDownArmed = false
                dragOffsetY = 0f
                scrollConsumedInGesture = 0f
                if (shouldDismiss) {
                    latestOnBack()
                }
                return Velocity.Zero
            }
        }
    }

    this
        .graphicsLayer {
            val safeY = animatedOffsetY.coerceAtLeast(0f)
            translationY = safeY
            val scale = (1f - (safeY / 1100f * 0.10f)).coerceIn(0.90f, 1f)
            scaleX = scale
            scaleY = scale
            alpha = (1f - (safeY / 900f * 0.30f)).coerceIn(0.70f, 1f)
        }
        .nestedScroll(nestedScrollConnection)
}

/**
 * In-window bottom sheet overlay rendered inside the Activity window (NOT a separate Dialog/ModalBottomSheet window),
 * ensuring Android Back gestures, Detail navHistory stack, and Poweramp pull-down gestures all step back 1 layer cleanly.
 */
@Composable
fun InWindowBottomSheet(
    onDismiss: () -> Unit,
    isAtTop: () -> Boolean = { true },
    maxHeightFraction: Float = 0.92f,
    wrapHeightMax: Dp? = null,
    showDragHandle: Boolean = true,
    containerColor: Color = AmoledBlack,
    backgroundContent: (@Composable BoxScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    var handleDragY by remember { mutableFloatStateOf(0f) }
    val animatedHandleDragY by animateFloatAsState(
        targetValue = handleDragY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "sheetHandleDrag"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Translucent backdrop scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(220)
            ) + fadeIn(tween(180)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val sizeModifier = if (wrapHeightMax != null) {
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .heightIn(max = wrapHeightMax)
            } else {
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(maxHeightFraction)
            }

            val containerModifier = sizeModifier
                .graphicsLayer { translationY = animatedHandleDragY.coerceAtLeast(0f) }
                .powerampPullDownToBack(isAtTop = isAtTop, onBack = onDismiss)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(containerColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // absorb clicks so scrim behind doesn't trigger
                )

            Box(modifier = containerModifier) {
                backgroundContent?.invoke(this)
                val columnModifier = if (wrapHeightMax != null) {
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                } else {
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                }
                Column(modifier = columnModifier) {
                    if (showDragHandle) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            if (handleDragY > 140f) {
                                                handleDragY = 0f
                                                onDismiss()
                                            } else {
                                                handleDragY = 0f
                                            }
                                        },
                                        onDragCancel = { handleDragY = 0f },
                                        onVerticalDrag = { _, dragAmount ->
                                            handleDragY = (handleDragY + dragAmount * 0.7f).coerceAtLeast(0f)
                                        }
                                    )
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 38.dp, height = 4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.24f))
                            )
                        }
                    }
                    Box(
                        modifier = if (wrapHeightMax != null) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier.weight(1f).fillMaxWidth()
                        }
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
