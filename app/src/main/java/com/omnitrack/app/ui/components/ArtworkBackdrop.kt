package com.omnitrack.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.toBitmap
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.ui.theme.AmoledBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class BackgroundMode(val displayName: String) {
    CINEMATIC("Cinematic Blur"),
    DOMINANT_COLOR("Ambient Glow"),
    NORMAL("Solid AMOLED")
}

private val backdropCache = linkedMapOf<String, ImageBitmap>()

private val backdropScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.34f),
    0.55f to Color.Black.copy(alpha = 0.48f),
    1f to Color.Black.copy(alpha = 0.64f)
)

/**
 * Loads a micro-thumbnail of the artwork, executes a 3-pass fast box-blur across raw pixel data,
 * and caches the resulting ImageBitmap in an LRU memory cache.
 *
 * CRITICAL: explicitly specifies allowHardware(false) so bitmap.getPixels() never crashes on Config.HARDWARE.
 */
@Composable
fun rememberArtworkBackdrop(imageUrl: String?, enabled: Boolean = true): ImageBitmap? {
    val context = LocalContext.current
    val losslessUrl = remember(imageUrl) { imageUrl?.let { LosslessCoverMapper.toLosslessUrl(it) } }
    var image by remember(losslessUrl) { mutableStateOf(losslessUrl?.let { backdropCache[it] }) }

    LaunchedEffect(losslessUrl, enabled, context) {
        if (!enabled || losslessUrl.isNullOrBlank() || image != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(losslessUrl)
            .size(128)
            .precision(Precision.EXACT)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
            ?: return@LaunchedEffect
        val blurred = withContext(Dispatchers.Default) {
            runCatching {
                val srcBitmap = result.image.toBitmap()
                val softwareBitmap = if (srcBitmap.config == Bitmap.Config.HARDWARE || !srcBitmap.isMutable) {
                    srcBitmap.copy(Bitmap.Config.ARGB_8888, true)
                } else {
                    srcBitmap
                }
                val pixels = IntArray(softwareBitmap.width * softwareBitmap.height)
                softwareBitmap.getPixels(pixels, 0, softwareBitmap.width, 0, 0, softwareBitmap.width, softwareBitmap.height)
                blurBackdropPixels(pixels, softwareBitmap.width, softwareBitmap.height)
                softwareBitmap.setPixels(pixels, 0, softwareBitmap.width, 0, 0, softwareBitmap.width, softwareBitmap.height)
                softwareBitmap.asImageBitmap()
            }.getOrNull()
        } ?: return@LaunchedEffect

        backdropCache[losslessUrl] = blurred
        if (backdropCache.size > 8) {
            val oldestKey = backdropCache.keys.firstOrNull()
            if (oldestKey != null) backdropCache.remove(oldestKey)
        }
        image = blurred
    }

    return image
}

/**
 * Full-screen backdrop canvas rendering the blurred ImageBitmap with a gentle vertical scrim.
 * Sits directly behind all content without causing boundary lines or clipping seams.
 */
@Composable
fun ArtworkBackdrop(
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center
) {
    Box(modifier = modifier.fillMaxSize().background(AmoledBlack)) {
        Crossfade(
            targetState = image,
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            label = "artwork_backdrop"
        ) { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    alignment = alignment,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Box(Modifier.fillMaxSize().background(backdropScrim))
    }
}

/**
 * Direct pixel alpha masking using a 17-stop quintic smoothstep curve and DstIn compositing.
 * Feathers the lower portion of the hero banner directly into the blurred background canvas
 * with zero top/side shading and zero vignette.
 */
fun Modifier.featherArtwork(startYRatio: Float = 0.58f): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithCache {
    val fade = Brush.verticalGradient(
        colorStops = Array(17) { index ->
            val t = index / 16f
            val progress = t * t * t * (t * (t * 6f - 15f) + 10f)
            t to Color.Black.copy(alpha = 1f - progress)
        },
        startY = size.height * startYRatio,
        endY = size.height
    )
    onDrawWithContent {
        drawContent()
        drawRect(fade, blendMode = BlendMode.DstIn)
    }
}

/**
 * High-performance 3-pass box blur across raw pixel arrays in linear time O(N).
 */
internal fun blurBackdropPixels(pixels: IntArray, width: Int, height: Int) {
    require(width > 0 && height > 0 && pixels.size == width * height)
    val scratch = IntArray(pixels.size)
    val radius = (minOf(width, height) / 12).coerceAtLeast(2)
    repeat(3) {
        blurBackdropPass(pixels, scratch, width, height, radius, horizontal = true)
        blurBackdropPass(scratch, pixels, width, height, radius, horizontal = false)
    }
}

private fun blurBackdropPass(
    source: IntArray,
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean
) {
    val length = if (horizontal) width else height
    val lines = if (horizontal) height else width
    val stride = if (horizontal) 1 else width
    val count = radius * 2 + 1

    repeat(lines) { line ->
        val start = if (horizontal) line * width else line
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val pixel = source[start + offset.coerceIn(0, length - 1) * stride]
            alpha += pixel ushr 24
            red += (pixel ushr 16) and 255
            green += (pixel ushr 8) and 255
            blue += pixel and 255
        }
        repeat(length) { position ->
            target[start + position * stride] = ((alpha / count) shl 24) or
                ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
            val leaving = source[start + (position - radius).coerceAtLeast(0) * stride]
            val entering = source[start + (position + radius + 1).coerceAtMost(length - 1) * stride]
            alpha += (entering ushr 24) - (leaving ushr 24)
            red += ((entering ushr 16) and 255) - ((leaving ushr 16) and 255)
            green += ((entering ushr 8) and 255) - ((leaving ushr 8) and 255)
            blue += (entering and 255) - (leaving and 255)
        }
    }
}
