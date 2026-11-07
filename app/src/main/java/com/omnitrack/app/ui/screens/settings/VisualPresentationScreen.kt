package com.omnitrack.app.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.preferences.BannerSizing
import com.omnitrack.app.data.preferences.DetailsBackdropStyle
import com.omnitrack.app.data.preferences.DetailsPagePresentation
import com.omnitrack.app.data.preferences.FallbackBackgroundStyle
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun VisualPresentationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefManager = remember { PreferenceManager(context) }
    val scope = rememberCoroutineScope()
    val presentationSettings by prefManager.presentationSettings.collectAsState(initial = PresentationSettings())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar: Circle-free pure white back arrow + Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
            ) {
                Text(
                    text = "Visual Presentation",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "Interactive layout styles & atmospheric canvas options",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Live Interactive Preview Mockup Box
            LivePresentationMockup(settings = presentationSettings)

            // Section 1: Details Page Presentation
            PresentationSectionTitle("DETAILS PAGE LAYOUT")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isHero = presentationSettings.detailsPagePresentation == DetailsPagePresentation.HERO
                PresentationChoiceCard(
                    title = "Hero Layout",
                    description = "Full-bleed edge-to-edge banner behind status bar, zero-box AMOLED canvas.",
                    isSelected = isHero,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            prefManager.updateDetailsPagePresentation(DetailsPagePresentation.HERO)
                        }
                    }
                )

                val isClassic = presentationSettings.detailsPagePresentation == DetailsPagePresentation.CLASSIC
                PresentationChoiceCard(
                    title = "Classic Poster",
                    description = "Overlaid portrait card with ambient glow, adjacent title and floating stats.",
                    isSelected = isClassic,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            prefManager.updateDetailsPagePresentation(DetailsPagePresentation.CLASSIC)
                        }
                    }
                )
            }

            // Section 2: Banner Sizing
            PresentationSectionTitle("HERO BANNER SIZING")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isFullBleed = presentationSettings.bannerSizing == BannerSizing.FULL_BLEED
                PresentationChoiceCard(
                    title = "Full-Bleed Hero",
                    description = "Tall cinematic backdrop dissolving with smoothstep curve into pure black.",
                    isSelected = isFullBleed,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            prefManager.updateBannerSizing(BannerSizing.FULL_BLEED)
                        }
                    }
                )

                val isCinema = presentationSettings.bannerSizing == BannerSizing.CINEMA_SHOWCASE
                PresentationChoiceCard(
                    title = "Cinema Showcase",
                    description = "Widescreen 16:9 card with ambient atmospheric aura.",
                    isSelected = isCinema,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            prefManager.updateBannerSizing(BannerSizing.CINEMA_SHOWCASE)
                        }
                    }
                )
            }

            // Section 3: Fallback Ambient Atmosphere
            PresentationSectionTitle("FALLBACK AMBIENT AURA")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isBlur = presentationSettings.fallbackBackgroundStyle == FallbackBackgroundStyle.CINEMATIC_BLUR
                PresentationChoiceCard(
                    title = "Cinematic Blur",
                    description = "Vibrant atmospheric aura sampled from artwork, bathing the background in color.",
                    isSelected = isBlur,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            prefManager.updateFallbackBackgroundStyle(FallbackBackgroundStyle.CINEMATIC_BLUR)
                        }
                    }
                )

                val isSolidBlack = presentationSettings.fallbackBackgroundStyle == FallbackBackgroundStyle.SOLID_BLACK
                PresentationChoiceCard(
                    title = "Minimalist OLED",
                    description = "Pure pitch-black #000000 canvas with zero light bleed. Maximum battery savings.",
                    isSelected = isSolidBlack,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            prefManager.updateFallbackBackgroundStyle(FallbackBackgroundStyle.SOLID_BLACK)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnimeArtworkCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Cosmic cyberpunk twilight sky
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0B0F19),
                    Color(0xFF1E1B4B),
                    Color(0xFF312E81),
                    Color(0xFF4338CA),
                    Color(0xFF0284C7)
                )
            )
        )

        // 2. Glowing moon / neon disc in upper right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFDE047).copy(alpha = 0.95f),
                    Color(0xFFF59E0B).copy(alpha = 0.50f),
                    Color.Transparent
                ),
                center = Offset(w * 0.76f, h * 0.32f),
                radius = h * 0.38f
            ),
            radius = h * 0.38f,
            center = Offset(w * 0.76f, h * 0.32f)
        )

        // 3. Cyberpunk distant mountain silhouettes
        val mountainPath = Path().apply {
            moveTo(0f, h * 0.70f)
            lineTo(w * 0.22f, h * 0.38f)
            lineTo(w * 0.45f, h * 0.60f)
            lineTo(w * 0.68f, h * 0.32f)
            lineTo(w * 0.88f, h * 0.52f)
            lineTo(w, h * 0.42f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = mountainPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1E1B4B).copy(alpha = 0.95f),
                    Color(0xFF0B0F19)
                ),
                startY = h * 0.32f,
                endY = h
            )
        )

        // 4. Foreground neon skyline ridge
        val ridgePath = Path().apply {
            moveTo(0f, h * 0.82f)
            lineTo(w * 0.28f, h * 0.62f)
            lineTo(w * 0.55f, h * 0.75f)
            lineTo(w * 0.82f, h * 0.58f)
            lineTo(w, h * 0.70f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = ridgePath,
            color = Color(0xFF050811)
        )

        // 5. Subtle neon horizon glow line
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF38BDF8).copy(alpha = 0.1f),
                    Color(0xFF38BDF8).copy(alpha = 0.8f),
                    Color(0xFFA855F7).copy(alpha = 0.8f),
                    Color(0xFF38BDF8).copy(alpha = 0.1f)
                )
            ),
            start = Offset(0f, h * 0.78f),
            end = Offset(w, h * 0.78f),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

@Composable
private fun LivePresentationMockup(
    settings: PresentationSettings,
    modifier: Modifier = Modifier
) {
    val isHero = settings.detailsPagePresentation == DetailsPagePresentation.HERO
    val isFullBleed = settings.bannerSizing == BannerSizing.FULL_BLEED
    val isSolidBlack = settings.fallbackBackgroundStyle == FallbackBackgroundStyle.SOLID_BLACK

    val posterAlpha by animateFloatAsState(
        targetValue = if (isHero) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "posterAlpha"
    )

    val posterTranslateY by animateDpAsState(
        targetValue = if (isHero) 20.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "posterTransY"
    )

    val titlePaddingStart by animateDpAsState(
        targetValue = if (isHero) 16.dp else 84.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "titleStart"
    )

    val containerBg = if (isSolidBlack) AmoledBlack else Color(0xFF080C16)
    val bottomRowBg = if (isSolidBlack) AmoledBlack else Color.Black.copy(alpha = 0.45f)
    val dissolveTarget = if (isSolidBlack) AmoledBlack else Color.Black.copy(alpha = 0.50f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(containerBg)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
    ) {
        // Atmospheric Ambient Aura Backdrop (Toggles on in Cinematic Blur, completely gone in Minimalist OLED)
        if (!isSolidBlack) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF0284C7).copy(alpha = 0.60f),
                                Color(0xFF38BDF8).copy(alpha = 0.35f),
                                Color.Transparent
                            ),
                            center = Offset(120f, 90f),
                            radius = 420f
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF8B5CF6).copy(alpha = 0.55f),
                                Color(0xFFD946EF).copy(alpha = 0.30f),
                                Color.Transparent
                            ),
                            center = Offset(700f, 220f),
                            radius = 480f
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.20f))
            )
        }

        // Banner Sizing Mockup Area
        if (isFullBleed) {
            // MODE A: Full-Bleed Edge-to-Edge Banner dissolving via smoothstep
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                AnimeArtworkCanvas(modifier = Modifier.fillMaxSize())

                // 17-Stop Quintic Smoothstep Dissolve Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.15f to Color.Transparent,
                                0.60f to dissolveTarget.copy(alpha = 0.55f),
                                1.00f to dissolveTarget
                            )
                        )
                )

                // Full-Bleed Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.70f))
                        .border(0.8.dp, AccentArctic.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = "FULL-BLEED HERO (EDGE-TO-EDGE)",
                        fontSize = 8.5.sp,
                        color = AccentArctic,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // MODE B: Cinema Showcase Floating 16:9 Widescreen Card with Ambient Colored Aura
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .height(112.dp)
            ) {
                // Ambient backlight aura radiating around and behind the floating card
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.06f
                            scaleY = 1.20f
                            alpha = 0.75f
                        }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF0284C7).copy(alpha = 0.70f),
                                    Color(0xFF7C3AED).copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // The floating 16:9 widescreen card itself
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                ) {
                    AnimeArtworkCanvas(modifier = Modifier.fillMaxSize())

                    // Cinema card subtle vignette
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                                )
                            )
                    )

                    // Cinema Showcase Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(0.8.dp, AccentGold.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "16:9 CINEMA SHOWCASE CARD",
                            fontSize = 8.5.sp,
                            color = AccentGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Atmosphere Mode Pill in Top-Left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.70f))
                .padding(horizontal = 6.dp, vertical = 2.5.dp)
        ) {
            Text(
                text = if (isSolidBlack) "AMOLED #000000" else "CINEMATIC BLUR AURA",
                fontSize = 8.5.sp,
                color = if (isSolidBlack) TextMuted else AccentJade,
                fontWeight = FontWeight.Bold
            )
        }

        // Mock Poster (Fades in when Classic layout is active)
        if (posterAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 65.dp)
                    .graphicsLayer {
                        alpha = posterAlpha
                        translationY = posterTranslateY.toPx()
                    }
                    .width(54.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF38BDF8), Color(0xFF6366F1), Color(0xFFA855F7))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
                    .shadow(elevation = 14.dp, shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "POSTER",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Title Row Mock
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(bottom = 54.dp)
                .padding(start = titlePaddingStart, end = 16.dp)
        ) {
            Text(
                text = "Cyberpunk: Edgerunners",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text(
                text = "ANIME • 2022 • STUDIO TRIGGER",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        // Unboxed Stats Row Mock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .drawBehind {
                    val strokeW = 1.dp.toPx()
                    drawLine(
                        Color.White.copy(alpha = 0.08f),
                        Offset(0f, 0f),
                        Offset(size.width, 0f),
                        strokeWidth = strokeW
                    )
                }
                .background(bottomRowBg)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(AccentArctic.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+1", color = AccentArctic, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
                Text("10 / 10 EP", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AccentJade)
                )
                Text("COMPLETED", color = AccentJade, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(12.dp)
                )
                Text("9.5", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PresentationSectionTitle(title: String) {
    Text(
        text = title,
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp)
    )
}

@Composable
private fun PresentationChoiceCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) AccentArctic.copy(alpha = 0.12f) else Color(0xFF0F1420),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) AccentArctic.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.08f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardBorder"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = if (isSelected) AccentArctic else TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(AccentArctic),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.Black,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
            Text(
                text = description,
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}
