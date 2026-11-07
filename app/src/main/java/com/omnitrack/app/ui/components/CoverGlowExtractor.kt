package com.omnitrack.app.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.toBitmap
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.ui.theme.AccentArctic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Process-level cache for extracted dominant colors to avoid re-extracting palettes
private val dominantColorCache = mutableMapOf<String, Color>()

/**
 * Extracts the primary vibrant/dominant tint color from an image URL using AndroidX Palette.
 * Returns a reactive state that updates gracefully when the image loads.
 * Optimized with in-memory caching and downsampled 128px decoding for ultra-fast generation.
 */
@Composable
fun rememberCoverDominantColor(
    imageUrl: String?,
    fallbackColor: Color = AccentArctic
): Color {
    val context = LocalContext.current
    val lossless = remember(imageUrl) { imageUrl?.let { LosslessCoverMapper.toLosslessUrl(it) } }
    val colorState = remember(lossless) {
        mutableStateOf(lossless?.let { dominantColorCache[it] } ?: fallbackColor)
    }

    LaunchedEffect(lossless) {
        if (lossless.isNullOrBlank()) {
            colorState.value = fallbackColor
            return@LaunchedEffect
        }

        dominantColorCache[lossless]?.let { cached ->
            colorState.value = cached
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(lossless)
                    .size(128)
                    .precision(Precision.INEXACT)
                    .build()

                val result = SingletonImageLoader.get(context).execute(request)
                if (result is SuccessResult) {
                    val rawBitmap = result.image.toBitmap()
                    val softwareBitmap = if (rawBitmap.config == Bitmap.Config.HARDWARE) {
                        rawBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        rawBitmap
                    }

                    val palette = Palette.from(softwareBitmap)
                        .maximumColorCount(16)
                        .generate()

                    val targetRgb = palette.vibrantSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: palette.mutedSwatch?.rgb

                    if (targetRgb != null) {
                        val extractedColor = Color(targetRgb)
                        dominantColorCache[lossless] = extractedColor
                        withContext(Dispatchers.Main) {
                            colorState.value = extractedColor
                        }
                    }
                }
            } catch (_: Exception) {
                // Gracefully retain fallback color on network or decode failures
            }
        }
    }

    return colorState.value
}
