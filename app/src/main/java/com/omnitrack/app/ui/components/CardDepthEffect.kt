package com.omnitrack.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * Glass Card Depth & Specular Sheen Effect:
 * Simulates physical glass thickness and depth across pure AMOLED canvases using:
 * 1. 1dp specular top-to-bottom vertical gradient edge light (brighter at top, falling off smoothly).
 * 2. Soft curved glass top-sheen reflection highlight over the upper 22% of the card clipped to shape.
 */
fun Modifier.glassCardDepth(
    shape: Shape,
    edgeStrength: Float = 50f,
    sheenStrength: Float = 35f,
    edgeCoverage: Float = 100f,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this

    val edgeTop = edgeStrength.coerceIn(0f, 100f) / 100f
    val sheen = sheenStrength.coerceIn(0f, 100f) / 100f
    val coverage = edgeCoverage.coerceIn(0f, 100f) / 100f

    val withEdge = if (edgeTop > 0f) {
        border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = edgeTop * 0.40f),
                    Color.White.copy(alpha = edgeTop * 0.22f * coverage),
                    Color.White.copy(alpha = edgeTop * 0.08f * coverage),
                )
            ),
            shape = shape,
        )
    } else {
        this
    }

    return if (sheen > 0f) {
        withEdge.drawWithContent {
            drawContent()
            val sheenHeight = size.height * 0.22f
            if (sheenHeight > 0f) {
                val outline = shape.createOutline(size, layoutDirection, this)
                val shapePath = outline.toPath()
                clipPath(shapePath) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = sheen * 0.35f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = sheenHeight,
                        ),
                        size = Size(size.width, sheenHeight),
                    )
                }
            }
        }
    } else {
        withEdge
    }
}
