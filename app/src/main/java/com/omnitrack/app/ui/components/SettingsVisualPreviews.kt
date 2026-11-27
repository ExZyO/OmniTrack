package com.omnitrack.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.preferences.ContinueTrackingStyle
import com.omnitrack.app.data.preferences.DetailsPagePresentation
import com.omnitrack.app.data.preferences.DiscoverLayoutStyle
import com.omnitrack.app.data.preferences.EditionsSwitcherStyle
import com.omnitrack.app.data.preferences.EpisodeLayoutMode
import com.omnitrack.app.data.preferences.HomeShelfPosterSize
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.OmniThemeState
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * Clean Option-A frameless wireframe option tile for Settings & customizer sheets.
 * Displays an abstract architectural preview alongside concise title/subtitle copy.
 */
@Composable
fun VisualOptionTile(
    title: String,
    subtitle: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewHeight: Dp = 66.dp,
    preview: @Composable () -> Unit
) {
    val accent = AccentGold
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accent else Color.White.copy(alpha = 0.08f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "visualOptionBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accent.copy(alpha = 0.08f) else DarkSurfaceElevated.copy(alpha = 0.42f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "visualOptionBg"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 0.8.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(AmoledBlack)
                .border(0.6.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(6.dp))
        ) {
            preview()
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = OnAccentColor,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = if (isSelected) accent else TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 13.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DetailsLayoutWireframe(
    presentation: DetailsPagePresentation,
    modifier: Modifier = Modifier
) {
    val accent = AccentGold
    val variant = OmniThemeState.accentVariant
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = 6.dp.toPx()

        when (presentation) {
            DetailsPagePresentation.HERO -> {
                // Full-bleed banner fade into canvas (no cover art, centered logo + text)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(variant.copy(alpha = 0.38f), AmoledBlack),
                        startY = 0f,
                        endY = h * 0.62f
                    ),
                    size = Size(w, h * 0.62f)
                )
                // Centered clearart/title bar
                val titleW = w * 0.58f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.88f),
                    topLeft = Offset((w - titleW) / 2f, h * 0.48f),
                    size = Size(titleW, 5.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                // Centered subtitle bar
                val subW = w * 0.36f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.34f),
                    topLeft = Offset((w - subW) / 2f, h * 0.62f),
                    size = Size(subW, 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                // Centered stats hairline + accents
                drawLine(
                    color = Color.White.copy(alpha = 0.14f),
                    start = Offset(pad, h * 0.75f),
                    end = Offset(w - pad, h * 0.75f),
                    strokeWidth = 0.8.dp.toPx()
                )
                val statW = w * 0.46f
                drawRoundRect(
                    color = accent.copy(alpha = 0.85f),
                    topLeft = Offset((w - statW) / 2f, h * 0.83f),
                    size = Size(statW, 3.5.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }

            DetailsPagePresentation.CENTERED_COVER -> {
                // Soft ambient radial glow (no wide banner)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(variant.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.30f),
                        radius = w * 0.48f
                    ),
                    radius = w * 0.48f,
                    center = Offset(w * 0.5f, h * 0.30f)
                )
                // Centered 2:3 portrait cover card
                val posterH = h * 0.45f
                val posterW = posterH * (2f / 3f)
                val posterX = (w - posterW) / 2f
                val posterY = h * 0.10f
                drawRoundRect(
                    color = DarkSurfaceElevated,
                    topLeft = Offset(posterX, posterY),
                    size = Size(posterW, posterH),
                    cornerRadius = CornerRadius(3.5.dp.toPx())
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.55f),
                    topLeft = Offset(posterX, posterY),
                    size = Size(posterW, posterH),
                    cornerRadius = CornerRadius(3.5.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
                // Centered title + subtitle + stat line below cover
                val titleW = w * 0.56f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.88f),
                    topLeft = Offset((w - titleW) / 2f, h * 0.63f),
                    size = Size(titleW, 4.5.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                val subW = w * 0.34f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.34f),
                    topLeft = Offset((w - subW) / 2f, h * 0.74f),
                    size = Size(subW, 2.8.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                val statW = w * 0.44f
                drawRoundRect(
                    color = accent.copy(alpha = 0.82f),
                    topLeft = Offset((w - statW) / 2f, h * 0.85f),
                    size = Size(statW, 3.2.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }

            DetailsPagePresentation.CLASSIC -> {
                // Top backdrop strip + left overlapping poster + right text
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(variant.copy(alpha = 0.34f), AmoledBlack),
                        startY = 0f,
                        endY = h * 0.45f
                    ),
                    size = Size(w, h * 0.45f)
                )
                val posterW = h * 0.34f
                val posterH = h * 0.52f
                drawRoundRect(
                    color = DarkSurfaceElevated,
                    topLeft = Offset(pad, h * 0.26f),
                    size = Size(posterW, posterH),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.48f),
                    topLeft = Offset(pad, h * 0.26f),
                    size = Size(posterW, posterH),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
                val textX = pad + posterW + 6.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.86f),
                    topLeft = Offset(textX, h * 0.48f),
                    size = Size((w - textX - pad).coerceAtLeast(12f) * 0.88f, 4.5.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.32f),
                    topLeft = Offset(textX, h * 0.62f),
                    size = Size((w - textX - pad).coerceAtLeast(12f) * 0.58f, 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.82f),
                    topLeft = Offset(textX, h * 0.75f),
                    size = Size((w - textX - pad).coerceAtLeast(12f) * 0.40f, 3.5.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun DiscoverLayoutWireframe(
    style: DiscoverLayoutStyle,
    modifier: Modifier = Modifier
) {
    val accent = AccentGold
    val variant = OmniThemeState.accentVariant
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = 5.dp.toPx()
        val innerW = w - pad * 2

        when (style) {
            DiscoverLayoutStyle.EDITORIAL_SPOTLIGHT -> {
                // Wide spotlight banner card
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(variant.copy(alpha = 0.35f), accent.copy(alpha = 0.22f))
                    ),
                    topLeft = Offset(pad, pad),
                    size = Size(innerW, h * 0.42f),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                // Mini poster inside spotlight
                drawRoundRect(
                    color = accent.copy(alpha = 0.75f),
                    topLeft = Offset(pad + 4.dp.toPx(), pad + 4.dp.toPx()),
                    size = Size(h * 0.20f, h * 0.30f),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                // Bottom 2:3 shelf posters row
                val cardW = (innerW - 6.dp.toPx()) / 3f
                val cardTop = h * 0.56f
                val cardH = h - cardTop - pad
                repeat(3) { idx ->
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (idx == 0) 0.22f else 0.12f),
                        topLeft = Offset(pad + idx * (cardW + 3.dp.toPx()), cardTop),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }

            DiscoverLayoutStyle.HERO_CAROUSEL -> {
                // Full-width carousel hero occupying upper 58%
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.32f), variant.copy(alpha = 0.15f))
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, h * 0.56f),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                // Carousel pager indicator bar
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(w * 0.36f, h * 0.48f),
                    size = Size(w * 0.14f, 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = 1.3.dp.toPx(),
                    center = Offset(w * 0.56f, h * 0.49f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = 1.3.dp.toPx(),
                    center = Offset(w * 0.63f, h * 0.49f)
                )
                // Shelf posters below
                val cardW = (innerW - 6.dp.toPx()) / 3f
                val cardTop = h * 0.64f
                val cardH = h - cardTop - pad
                repeat(3) { idx ->
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.14f),
                        topLeft = Offset(pad + idx * (cardW + 3.dp.toPx()), cardTop),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }

            DiscoverLayoutStyle.MINIMAL_RAILS -> {
                // Top search bar
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(pad, pad),
                    size = Size(innerW, 9.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                // Filter tabs line
                drawRoundRect(
                    color = accent.copy(alpha = 0.78f),
                    topLeft = Offset(pad, pad + 13.dp.toPx()),
                    size = Size(innerW * 0.25f, 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.22f),
                    topLeft = Offset(pad + innerW * 0.32f, pad + 13.dp.toPx()),
                    size = Size(innerW * 0.22f, 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                // Two shelf rails filling the remaining space
                val cardW = (innerW - 6.dp.toPx()) / 3f
                val rail1Top = h * 0.44f
                val railH = h - rail1Top - pad
                repeat(3) { idx ->
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.16f),
                        topLeft = Offset(pad + idx * (cardW + 3.dp.toPx()), rail1Top),
                        size = Size(cardW, railH),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun BannerSizingWireframe(
    isFullBleed: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = AccentGold
    val variant = OmniThemeState.accentVariant
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (isFullBleed) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = 0.35f), variant.copy(alpha = 0.15f), AmoledBlack)
                )
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(8.dp.toPx(), h * 0.68f),
                size = Size(w * 0.50f, 4.5.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.32f),
                topLeft = Offset(8.dp.toPx(), h * 0.80f),
                size = Size(w * 0.34f, 3.dp.toPx()),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )
        } else {
            val insetX = 10.dp.toPx()
            val insetY = 8.dp.toPx()
            // Ambient aura glow behind floating 16:9 card
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.42f),
                    radius = w * 0.55f
                ),
                topLeft = Offset.Zero,
                size = Size(w, h)
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(variant.copy(alpha = 0.42f), accent.copy(alpha = 0.25f))
                ),
                topLeft = Offset(insetX, insetY),
                size = Size(w - insetX * 2, h * 0.58f),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.30f),
                topLeft = Offset(insetX, insetY),
                size = Size(w - insetX * 2, h * 0.58f),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(0.8.dp.toPx())
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.78f),
                topLeft = Offset(insetX, h * 0.78f),
                size = Size(w * 0.45f, 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}

@Composable
fun AmbientAuraWireframe(
    isBlur: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = AccentGold
    val variant = OmniThemeState.accentVariant
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (isBlur) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(variant.copy(alpha = 0.52f), Color.Transparent),
                    center = Offset(w * 0.25f, h * 0.32f),
                    radius = w * 0.55f
                ),
                center = Offset(w * 0.25f, h * 0.32f),
                radius = w * 0.55f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.40f), Color.Transparent),
                    center = Offset(w * 0.76f, h * 0.68f),
                    radius = w * 0.50f
                ),
                center = Offset(w * 0.76f, h * 0.68f),
                radius = w * 0.50f
            )
        } else {
            drawRect(AmoledBlack)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.16f),
                topLeft = Offset(8.dp.toPx(), h * 0.44f),
                size = Size(w * 0.52f, 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.65f),
                topLeft = Offset(8.dp.toPx(), h * 0.58f),
                size = Size(w * 0.28f, 3.dp.toPx()),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )
        }
    }
}

@Composable
fun EpisodeLayoutWireframe(
    mode: EpisodeLayoutMode,
    modifier: Modifier = Modifier
) {
    val accent = AccentGold
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = 5.dp.toPx()
        val innerW = w - pad * 2
        val innerH = h - pad * 2

        when (mode) {
            EpisodeLayoutMode.HORIZONTAL -> {
                val cardW = innerW * 0.58f
                repeat(2) { idx ->
                    val x = pad + idx * (cardW + 5.dp.toPx())
                    val cardH = innerH * 0.68f
                    drawRoundRect(
                        color = if (idx == 0) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                        topLeft = Offset(x, pad),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.55f),
                        topLeft = Offset(x, pad + cardH + 4.dp.toPx()),
                        size = Size(cardW * 0.7f, 3.dp.toPx()),
                        cornerRadius = CornerRadius(1.5.dp.toPx())
                    )
                }
            }

            EpisodeLayoutMode.LIST -> {
                val rowH = (innerH - 8.dp.toPx()) / 3f
                repeat(3) { idx ->
                    val y = pad + idx * (rowH + 4.dp.toPx())
                    drawRoundRect(
                        color = if (idx == 0) accent.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.12f),
                        topLeft = Offset(pad, y),
                        size = Size(rowH * 1.45f, rowH),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                    val tx = pad + rowH * 1.45f + 5.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.62f),
                        topLeft = Offset(tx, y + rowH * 0.22f),
                        size = Size((w - tx - pad) * 0.78f, 3.dp.toPx()),
                        cornerRadius = CornerRadius(1.5.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.26f),
                        topLeft = Offset(tx, y + rowH * 0.62f),
                        size = Size((w - tx - pad) * 0.45f, 2.2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx())
                    )
                }
            }

            EpisodeLayoutMode.VERTICAL_CARD -> {
                val cardH = (innerH - 4.dp.toPx()) / 2f
                repeat(2) { idx ->
                    val y = pad + idx * (cardH + 4.dp.toPx())
                    drawRoundRect(
                        color = if (idx == 0) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.09f),
                        topLeft = Offset(pad, y),
                        size = Size(innerW, cardH),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.70f),
                        topLeft = Offset(pad + 4.dp.toPx(), y + cardH - 6.dp.toPx()),
                        size = Size(innerW * 0.48f, 2.8.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx())
                    )
                }
            }

            EpisodeLayoutMode.POSTER_GRID -> {
                val cols = 3
                val gap = 4.dp.toPx()
                val cellW = (innerW - gap * 2) / cols
                repeat(cols) { c ->
                    val x = pad + c * (cellW + gap)
                    drawRoundRect(
                        color = if (c == 0) accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f),
                        topLeft = Offset(x, pad),
                        size = Size(cellW, innerH),
                        cornerRadius = CornerRadius(2.5.dp.toPx())
                    )
                }
            }

            EpisodeLayoutMode.TEXT_ONLY -> {
                val rowH = (innerH - 8.dp.toPx()) / 3f
                repeat(3) { idx ->
                    val y = pad + idx * (rowH + 4.dp.toPx())
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.06f),
                        topLeft = Offset(pad, y),
                        size = Size(innerW, rowH),
                        cornerRadius = CornerRadius(2.5.dp.toPx())
                    )
                    drawRoundRect(
                        color = if (idx == 0) accent.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.48f),
                        topLeft = Offset(pad + 4.dp.toPx(), y + rowH * 0.34f),
                        size = Size(innerW * 0.58f, 3.dp.toPx()),
                        cornerRadius = CornerRadius(1.5.dp.toPx())
                    )
                    drawCircle(
                        color = if (idx == 0) accent else Color.White.copy(alpha = 0.25f),
                        radius = rowH * 0.26f,
                        center = Offset(w - pad - 6.dp.toPx(), y + rowH * 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun SeasonSelectorWireframe(
    isPosters: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = AccentGold
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = 6.dp.toPx()
        val innerW = w - pad * 2
        if (isPosters) {
            val gap = 4.dp.toPx()
            val cardW = (innerW - gap * 2) / 3f
            repeat(3) { idx ->
                val x = pad + idx * (cardW + gap)
                drawRoundRect(
                    color = if (idx == 0) accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.11f),
                    topLeft = Offset(x, pad),
                    size = Size(cardW, h - pad * 2 - 7.dp.toPx()),
                    cornerRadius = CornerRadius(2.5.dp.toPx())
                )
                drawRoundRect(
                    color = if (idx == 0) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(x, h - pad - 4.dp.toPx()),
                    size = Size(cardW * 0.75f, 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            }
        } else {
            val pillW = (innerW - 8.dp.toPx()) / 3f
            val y = (h - 14.dp.toPx()) / 2f
            repeat(3) { idx ->
                val x = pad + idx * (pillW + 4.dp.toPx())
                drawRoundRect(
                    color = if (idx == 0) accent.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
                    topLeft = Offset(x, y),
                    size = Size(pillW, 14.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                drawRoundRect(
                    color = if (idx == 0) accent else Color.White.copy(alpha = 0.50f),
                    topLeft = Offset(x + 4.dp.toPx(), y + 5.5.dp.toPx()),
                    size = Size(pillW - 8.dp.toPx(), 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun HomeLayoutWireframe(
    isEditorial: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = AccentGold
    val variant = OmniThemeState.accentVariant
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = 5.dp.toPx()
        val innerW = w - pad * 2
        if (isEditorial) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.30f), variant.copy(alpha = 0.14f))),
                topLeft = Offset(pad, pad),
                size = Size(innerW, h * 0.44f),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
            val cardW = (innerW - 6.dp.toPx()) / 3f
            val top = h * 0.56f
            repeat(3) { idx ->
                drawRoundRect(
                    color = Color.White.copy(alpha = if (idx == 0) 0.20f else 0.12f),
                    topLeft = Offset(pad + idx * (cardW + 3.dp.toPx()), top),
                    size = Size(cardW, h - top - pad),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        } else {
            val rowH = (h - pad * 2 - 8.dp.toPx()) / 3f
            repeat(3) { idx ->
                val y = pad + idx * (rowH + 4.dp.toPx())
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.08f),
                    topLeft = Offset(pad, y),
                    size = Size(innerW, rowH),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.72f),
                    topLeft = Offset(pad + 3.dp.toPx(), y + rowH * 0.30f),
                    size = Size(innerW * (0.58f - idx * 0.08f), 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun ContinueStyleWireframe(
    style: ContinueTrackingStyle,
    modifier: Modifier = Modifier
) {
    val accent = AccentGold
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = 5.dp.toPx()
        val innerW = w - pad * 2
        val innerH = h - pad * 2

        when (style) {
            ContinueTrackingStyle.CARD -> {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.14f),
                    topLeft = Offset(pad, pad),
                    size = Size(innerW, innerH),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(pad, h - pad - 4.dp.toPx()),
                    size = Size(innerW * 0.65f, 2.5.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            }

            ContinueTrackingStyle.WIDE -> {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.08f),
                    topLeft = Offset(pad, pad),
                    size = Size(innerW, innerH),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                val thumbW = innerW * 0.34f
                drawRoundRect(
                    color = accent.copy(alpha = 0.28f),
                    topLeft = Offset(pad + 3.dp.toPx(), pad + 3.dp.toPx()),
                    size = Size(thumbW, innerH - 6.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                val tx = pad + thumbW + 7.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.68f),
                    topLeft = Offset(tx, h * 0.32f),
                    size = Size((w - tx - pad) * 0.78f, 3.5.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(tx, h * 0.62f),
                    size = Size((w - tx - pad) * 0.56f, 2.5.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            }

            ContinueTrackingStyle.POSTER -> {
                val pw = innerH * 0.56f
                val px = pad
                drawRoundRect(
                    color = accent.copy(alpha = 0.26f),
                    topLeft = Offset(px, pad),
                    size = Size(pw, innerH * 0.80f),
                    cornerRadius = CornerRadius(2.5.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.11f),
                    topLeft = Offset(px + pw + 6.dp.toPx(), pad),
                    size = Size(pw, innerH * 0.80f),
                    cornerRadius = CornerRadius(2.5.dp.toPx())
                )
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(px, h - pad - 3.dp.toPx()),
                    size = Size(pw, 2.5.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun ShelfPosterSizeWireframe(
    posterSize: HomeShelfPosterSize,
    modifier: Modifier = Modifier
) {
    val accent = AccentGold
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = 5.dp.toPx()
        val innerW = w - pad * 2
        val innerH = h - pad * 2
        val cols = when (posterSize) {
            HomeShelfPosterSize.COMPACT -> 4
            HomeShelfPosterSize.STANDARD -> 3
            HomeShelfPosterSize.LARGE -> 2
        }
        val gap = 4.dp.toPx()
        val cardW = (innerW - gap * (cols - 1)) / cols
        repeat(cols) { idx ->
            drawRoundRect(
                color = if (idx == 0) accent.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.12f),
                topLeft = Offset(pad + idx * (cardW + gap), pad),
                size = Size(cardW, innerH),
                cornerRadius = CornerRadius(2.5.dp.toPx())
            )
        }
    }
}

@Composable
fun HeroHeightWireframe(
    preset: String,
    modifier: Modifier = Modifier
) {
    val accent = AccentGold
    val variant = OmniThemeState.accentVariant
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val fraction = when (preset.uppercase()) {
            "COMPACT" -> 0.40f
            "TALL" -> 0.82f
            else -> 0.60f
        }
        drawRect(
            brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.38f), variant.copy(alpha = 0.14f))),
            size = Size(w, h * fraction)
        )
        drawLine(
            color = accent.copy(alpha = 0.85f),
            start = Offset(0f, h * fraction),
            end = Offset(w, h * fraction),
            strokeWidth = 1.dp.toPx()
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.12f),
            topLeft = Offset(6.dp.toPx(), h * fraction + 4.dp.toPx()),
            size = Size(w * 0.44f, 3.dp.toPx()),
            cornerRadius = CornerRadius(1.5.dp.toPx())
        )
    }
}

@Composable
fun HeroMetadataLayoutWireframe(
    layoutIndex: Int,
    modifier: Modifier = Modifier
) {
    val accent = AccentGold
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = 6.dp.toPx()
        // Subtle banner gradient top
        drawRect(
            brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), AmoledBlack)),
            size = Size(w, h * 0.55f)
        )
        when (layoutIndex) {
            0 -> {
                // Clean Separation: title left, chips below with generous breathing space
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.86f),
                    topLeft = Offset(pad, h * 0.44f),
                    size = Size(w * 0.58f, 4.5.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.78f),
                    topLeft = Offset(pad, h * 0.64f),
                    size = Size(w * 0.24f, 3.5.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.28f),
                    topLeft = Offset(pad + w * 0.28f, h * 0.64f),
                    size = Size(w * 0.24f, 3.5.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            }
            1 -> {
                // Stacked Rows: eyebrow + title + progress track
                drawRoundRect(
                    color = accent.copy(alpha = 0.85f),
                    topLeft = Offset(pad, h * 0.34f),
                    size = Size(w * 0.26f, 2.5.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.86f),
                    topLeft = Offset(pad, h * 0.48f),
                    size = Size(w * 0.62f, 4.5.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.30f),
                    topLeft = Offset(pad, h * 0.64f),
                    size = Size(w * 0.44f, 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.75f),
                    topLeft = Offset(pad, h * 0.78f),
                    size = Size(w * 0.48f, 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }
            2 -> {
                // Compact Single-Line: title + inline dot specs in one tight band
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.86f),
                    topLeft = Offset(pad, h * 0.62f),
                    size = Size(w * 0.44f, 4.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                drawCircle(
                    color = accent,
                    radius = 1.8.dp.toPx(),
                    center = Offset(pad + w * 0.50f, h * 0.66f)
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.78f),
                    topLeft = Offset(pad + w * 0.55f, h * 0.62f),
                    size = Size(w * 0.26f, 3.5.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            }
            else -> {
                // Minimal Inline: centered/clean bottom title + subtle underline action
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(pad, h * 0.54f),
                    size = Size(w * 0.50f, 4.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(w - pad - w * 0.24f, h * 0.72f),
                    size = Size(w * 0.24f, 7.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.30f),
                    topLeft = Offset(pad, h * 0.74f),
                    size = Size(w * 0.34f, 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun EditionsStyleWireframe(
    style: EditionsSwitcherStyle,
    modifier: Modifier = Modifier
) {
    val accent = AccentGold
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = 7.dp.toPx()
        val usableW = w - pad * 2

        when (style) {
            EditionsSwitcherStyle.SMART_LABELS -> {
                val yText = h * 0.44f
                val tab1W = usableW * 0.28f
                val tab2W = usableW * 0.26f
                val tab3W = usableW * 0.22f
                val gap = usableW * 0.08f
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(pad, yText),
                    size = Size(tab1W, 4.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                drawRect(
                    color = accent,
                    topLeft = Offset(pad, yText + 7.dp.toPx()),
                    size = Size(tab1W, 2.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.32f),
                    topLeft = Offset(pad + tab1W + gap, yText),
                    size = Size(tab2W, 4.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.24f),
                    topLeft = Offset(pad + tab1W + gap + tab2W + gap, yText),
                    size = Size(tab3W, 4.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(pad, yText + 9.5.dp.toPx()),
                    size = Size(usableW, 0.7.dp.toPx())
                )
            }
            EditionsSwitcherStyle.THUMBNAIL_CARDS -> {
                val cardW = usableW * 0.24f
                val cardH = h - pad * 2
                val gap = (usableW - cardW * 3) / 2f
                repeat(3) { idx ->
                    val x = pad + idx * (cardW + gap)
                    drawRoundRect(
                        color = if (idx == 0) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.09f),
                        topLeft = Offset(x, pad),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                    drawRoundRect(
                        color = if (idx == 0) accent else Color.White.copy(alpha = 0.30f),
                        topLeft = Offset(x + 2.5.dp.toPx(), h - pad - 4.5.dp.toPx()),
                        size = Size(cardW - 5.dp.toPx(), 2.2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx())
                    )
                }
            }
            EditionsSwitcherStyle.GROUPED_DROPDOWN -> {
                val topY = h * 0.24f
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(pad, topY),
                    size = Size(usableW * 0.34f, 4.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.28f),
                    topLeft = Offset(pad + usableW * 0.42f, topY),
                    size = Size(usableW * 0.22f, 4.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                val panelTop = h * 0.46f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.11f),
                    topLeft = Offset(pad, panelTop),
                    size = Size(usableW * 0.68f, h - panelTop - pad),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.78f),
                    topLeft = Offset(pad + 4.dp.toPx(), panelTop + 4.dp.toPx()),
                    size = Size(usableW * 0.42f, 2.5.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.30f),
                    topLeft = Offset(pad + 4.dp.toPx(), panelTop + 9.5.dp.toPx()),
                    size = Size(usableW * 0.34f, 2.5.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }
        }
    }
}
