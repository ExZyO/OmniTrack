package com.omnitrack.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
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
 * Seamlessly transitions into pure AMOLED Black (#000000) using radial & vertical gradient scrims.
 */
@Composable
fun CoverGlowBox(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    glowHeight: Dp = 460.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val extractedColor = rememberCoverDominantColor(imageUrl)
    val animatedGlowColor by animateColorAsState(
        targetValue = extractedColor.copy(alpha = 0.28f),
        animationSpec = tween(600),
        label = "ambientGlowColor"
    )

    Box(modifier = modifier.background(AmoledBlack)) {
        // Ambient radial light bloom that bleeds softly into the dark backdrop
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(glowHeight)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            animatedGlowColor,
                            animatedGlowColor.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Radial specular highlight for extra depth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(glowHeight * 0.75f)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedGlowColor.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        radius = 800f
                    )
                )
        )

        content()
    }
}
