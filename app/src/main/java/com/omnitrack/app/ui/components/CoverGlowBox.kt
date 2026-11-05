package com.omnitrack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Renders an ambient glow effect behind cover art by rendering a blurred,
 * scaled-up copy of the image behind the main content.
 */
@Composable
fun CoverGlowBox(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    glowAlpha: Float = 0.35f,
    glowBlur: Dp = 80.dp,
    glowScale: Float = 1.3f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        // Glow layer — blurred, scaled copy
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .scale(glowScale)
                    .blur(glowBlur)
                    .offset(y = (-40).dp),
                contentScale = ContentScale.Crop,
                alpha = glowAlpha
            )
        }

        // Actual content on top
        content()
    }
}
