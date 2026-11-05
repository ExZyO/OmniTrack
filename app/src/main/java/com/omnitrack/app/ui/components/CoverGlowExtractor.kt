package com.omnitrack.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.ui.theme.AccentArctic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts the primary vibrant/dominant tint color from an image URL using AndroidX Palette.
 * Returns a reactive state that updates gracefully when the image loads.
 */
@Composable
fun rememberCoverDominantColor(
    imageUrl: String?,
    fallbackColor: Color = AccentArctic
): Color {
    val context = LocalContext.current
    val colorState = remember(imageUrl) { mutableStateOf(fallbackColor) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) {
            colorState.value = fallbackColor
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val lossless = LosslessCoverMapper.toLosslessUrl(imageUrl)
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(lossless)
                    .build()

                val result = loader.execute(request)
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
                        withContext(Dispatchers.Main) {
                            colorState.value = Color(targetRgb)
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
