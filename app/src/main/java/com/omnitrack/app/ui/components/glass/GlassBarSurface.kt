package com.omnitrack.app.ui.components.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

private val GlassSurfaceColor = Color(0xFF12110E)

@Composable
fun GlassBarSurface(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    glowStrength: Float = 1f,
    pauseLiveBlur: Boolean = false
) {
    val activeHaze = if (pauseLiveBlur) null else hazeState
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activeHaze != null && glowStrength > 0f) {
        RefractedGlassBar(activeHaze, modifier, glowStrength)
    } else {
        Box(
            modifier
                .then(if (activeHaze != null) Modifier.barBackdrop(activeHaze) else Modifier)
                .drawWithCache {
                    val fill = GlassSurfaceColor.copy(alpha = if (activeHaze != null) 0.55f else 0.86f)
                    val edge = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f))
                    )
                    val strokeW = 1.dp.toPx()
                    onDrawBehind {
                        drawRect(fill)
                        drawRoundRect(
                            brush = edge,
                            topLeft = Offset(strokeW / 2, strokeW / 2),
                            size = Size(size.width - strokeW, size.height - strokeW),
                            cornerRadius = CornerRadius((size.height - strokeW) / 2),
                            style = Stroke(strokeW),
                            alpha = glowStrength
                        )
                    }
                }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun RefractedGlassBar(hazeState: HazeState, modifier: Modifier, glowStrength: Float) {
    val shader = remember { RuntimeShader(GlassBarShader) }
    Box(
        modifier
            .layout { measurable, constraints ->
                val outset = 24.dp.roundToPx()
                val placeable = measurable.measure(constraints.offset(outset * 2, outset * 2))
                layout(placeable.width - outset * 2, placeable.height - outset * 2) {
                    placeable.place(-outset, -outset)
                }
            }
            .graphicsLayer {
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("density", density)
                shader.setFloatUniform("outset", 24.dp.roundToPx().toFloat())
                shader.setFloatUniform("glowStrength", glowStrength)
                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop").asComposeRenderEffect()
            }
            .barBackdrop(hazeState)
    )
}

private fun Modifier.barBackdrop(hazeState: HazeState): Modifier = hazeEffect(state = hazeState) {
    blurRadius = 24.dp
    backgroundColor = GlassSurfaceColor
    tints = listOf(HazeTint(Color.Transparent))
    noiseFactor = 0f
}
