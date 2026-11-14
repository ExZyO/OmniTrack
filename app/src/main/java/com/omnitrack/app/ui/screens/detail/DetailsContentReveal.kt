package com.omnitrack.app.ui.screens.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 1:1 upstream port of Nuvio's DetailsContentReveal.
 * Delays opacity rendering by 1 frame (via withFrameNanos) to ensure
 * composition and layout passes finish before smoothly fading in the content
 * with a 350ms cubic-bezier transition, eliminating navigation entrance stutter.
 */
@Composable
fun Modifier.detailsContentReveal(enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val opacity = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        opacity.animateTo(1f, tween(350, easing = CubicBezierEasing(0.5f, 0f, 0.14f, 1f)))
    }
    return graphicsLayer {
        alpha = opacity.value
    }
}
