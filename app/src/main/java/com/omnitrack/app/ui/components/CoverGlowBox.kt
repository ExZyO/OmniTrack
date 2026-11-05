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
 * Seamlessly transitions into pure AMOLED Black (#000000) using continuous multi-stop alpha decay
 * with zero visible boundary lines or edge cutoffs.
 */
@Composable
fun CoverGlowBox(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    glowHeight: Dp = 680.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val extractedColor = rememberCoverDominantColor(imageUrl)
    val animatedGlowColor by animateColorAsState(
        targetValue = extractedColor.copy(alpha = 0.28f),
        animationSpec = tween(600),
        label = "ambientGlowColor"
    )

    Box(modifier = modifier.background(AmoledBlack)) {
        // Continuous, borderless ambient bloom centered behind poster zone
        // Guaranteed zero sharp borders: starts at 0.0 alpha, peaks smoothly, fades to 0.0 alpha
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(glowHeight)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.18f to Color.Transparent,
                            0.32f to animatedGlowColor.copy(alpha = 0.08f),
                            0.44f to animatedGlowColor,
                            0.60f to animatedGlowColor.copy(alpha = 0.16f),
                            0.78f to animatedGlowColor.copy(alpha = 0.04f),
                            1.00f to Color.Transparent
                        )
                    )
                )
        )

        content()
    }
}
