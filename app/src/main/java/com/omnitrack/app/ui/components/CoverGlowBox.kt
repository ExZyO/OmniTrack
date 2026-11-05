package com.omnitrack.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnitrack.app.ui.theme.AmoledBlack

/**
 * Creates an immersive dynamic ambient glow behind media screens.
 * Seamlessly transitions into pure AMOLED Black (#000000) using multi-stop non-linear alpha decay.
 */
@Composable
fun CoverGlowBox(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    glowHeight: Dp = 620.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val extractedColor = rememberCoverDominantColor(imageUrl)
    val animatedGlowColor by animateColorAsState(
        targetValue = extractedColor.copy(alpha = 0.22f),
        animationSpec = tween(600),
        label = "ambientGlowColor"
    )

    Box(modifier = modifier.background(AmoledBlack)) {
        // Soft ambient bloom with multi-stop exponential decay — zero harsh line boundaries!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(glowHeight)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to animatedGlowColor,
                            0.20f to animatedGlowColor.copy(alpha = 0.16f),
                            0.45f to animatedGlowColor.copy(alpha = 0.08f),
                            0.70f to animatedGlowColor.copy(alpha = 0.03f),
                            0.90f to Color.Transparent,
                            1.00f to Color.Transparent
                        )
                    )
                )
        )

        // Radial specular highlight centered under the top-third
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(glowHeight * 0.7f)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to animatedGlowColor.copy(alpha = 0.20f),
                            0.35f to animatedGlowColor.copy(alpha = 0.10f),
                            0.70f to animatedGlowColor.copy(alpha = 0.02f),
                            1.00f to Color.Transparent
                        ),
                        radius = 1200f
                    )
                )
        )

        content()
    }
}
