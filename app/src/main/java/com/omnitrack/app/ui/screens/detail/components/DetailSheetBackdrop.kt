package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.omnitrack.app.data.preferences.FallbackBackgroundStyle
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.ui.components.ArtworkBackdrop
import com.omnitrack.app.ui.components.CoverGlowBox
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack

/**
 * Unified Details-Screen Backdrop Container for bottom sheets & overlays opened from Details.
 * Automatically inherits the user's Settings > Visual Presentation > Background Style
 * (Cinematic Blur, Ambient Palette Glow, or Solid AMOLED Black) so every popup blends
 * seamlessly with the Details screen.
 */
@Composable
fun DetailSheetBackdrop(
    presentationSettings: PresentationSettings?,
    backdropBitmap: ImageBitmap?,
    backdropTargetUrl: String?,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = 0.62f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.background(AmoledBlack)
    ) {
        when (presentationSettings?.fallbackBackgroundStyle) {
            FallbackBackgroundStyle.CINEMATIC_BLUR -> {
                if (backdropBitmap != null) {
                    ArtworkBackdrop(
                        image = backdropBitmap,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = scrimAlpha))
                    )
                } else {
                    CoverGlowBox(
                        imageUrl = backdropTargetUrl,
                        modifier = Modifier.matchParentSize()
                    ) {}
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = scrimAlpha))
                    )
                }
            }
            FallbackBackgroundStyle.DOMINANT_COLOR -> {
                CoverGlowBox(
                    imageUrl = backdropTargetUrl,
                    modifier = Modifier.matchParentSize()
                ) {}
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                )
            }
            else -> {
                // Solid AMOLED with ultra-subtle warm top radial aura
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    AccentGold.copy(alpha = 0.05f),
                                    AmoledBlack,
                                    AmoledBlack
                                )
                            )
                        )
                )
            }
        }
        content()
    }
}
