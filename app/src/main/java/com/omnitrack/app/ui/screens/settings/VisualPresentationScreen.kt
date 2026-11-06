package com.omnitrack.app.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
                val isUnblocked = presentationSettings.detailsPagePresentation == DetailsPagePresentation.UNBLOCKED_HERO
                PresentationChoiceCard(
                    title = "Unblocked Hero",
                    description = "Full-bleed edge-to-edge banner behind status bar, zero-box AMOLED canvas.",
                    isSelected = isUnblocked,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            prefManager.updateDetailsPagePresentation(DetailsPagePresentation.UNBLOCKED_HERO)
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
                    description = "Tall 350dp cinematic backdrop dissolving with smoothstep curve into pure black.",
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
                    description = "Soft, deep atmospheric Gaussian glow drawn from cover artwork.",
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
                    description = "Pure #000000 AMOLED plane with zero color bleed. Maximum power efficiency.",
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
private fun LivePresentationMockup(
    settings: PresentationSettings,
    modifier: Modifier = Modifier
) {
    val isUnblocked = settings.detailsPagePresentation == DetailsPagePresentation.UNBLOCKED_HERO
    val isFullBleed = settings.bannerSizing == BannerSizing.FULL_BLEED
    val isSolidBlack = settings.fallbackBackgroundStyle == FallbackBackgroundStyle.SOLID_BLACK

    val bannerHeight by animateDpAsState(
        targetValue = if (isFullBleed) 160.dp else 125.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bannerH"
    )

    val posterAlpha by animateFloatAsState(
        targetValue = if (isUnblocked) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "posterAlpha"
    )

    val posterTranslateY by animateDpAsState(
        targetValue = if (isUnblocked) 20.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "posterTransY"
    )

    val titlePaddingStart by animateDpAsState(
        targetValue = if (isUnblocked) 16.dp else 84.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "titleStart"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSolidBlack) AmoledBlack else Color(0xFF0F1420))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
    ) {
        // Banner Backdrop Mock
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1E3A8A).copy(alpha = 0.85f),
                            Color(0xFF0F172A).copy(alpha = 0.5f),
                            AmoledBlack
                        )
                    )
                )
        )

        // Mock Poster (Fades in when Classic layout is active)
        if (posterAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 56.dp)
                    .graphicsLayer {
                        alpha = posterAlpha
                        translationY = posterTranslateY.toPx()
                    }
                    .width(56.dp)
                    .height(82.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AccentArctic, Color(0xFF6366F1))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(8.dp)),
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
                .background(AmoledBlack)
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
