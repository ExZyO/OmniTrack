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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.preferences.BannerSizing
import com.omnitrack.app.data.preferences.DetailsPagePresentation
import com.omnitrack.app.data.preferences.DiscoverLayoutStyle
import com.omnitrack.app.data.preferences.FallbackBackgroundStyle
import com.omnitrack.app.data.preferences.HeroBannerBlurLevel
import com.omnitrack.app.data.preferences.PerformanceSettings
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.ui.components.AmbientAuraWireframe
import com.omnitrack.app.ui.components.BannerSizingWireframe
import com.omnitrack.app.ui.components.DetailsLayoutWireframe
import com.omnitrack.app.ui.components.DiscoverLayoutWireframe
import com.omnitrack.app.ui.components.PerformanceSettingsContent
import com.omnitrack.app.ui.components.VisualOptionTile
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.OmniThemeSettings
import com.omnitrack.app.ui.theme.OmniThemeState
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun VisualPresentationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefManager = remember(context) { (context.applicationContext as OmniTrackApplication).preferenceManager }
    val scope = rememberCoroutineScope()
    val presentationSettings by prefManager.presentationSettings.collectAsState(initial = PresentationSettings())
    val discoverLayoutStyle by prefManager.discoverLayoutStyle.collectAsState(initial = DiscoverLayoutStyle.EDITORIAL_SPOTLIGHT)
    val themeSettings by prefManager.themeSettings.collectAsState(initial = OmniThemeSettings())

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
                    text = "Visual Presentation & Colors",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "Theme accents, layout styles & atmospheric canvas options",
                    color = TextSecondary,
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

            // App Theme & Custom HSV/Hex Accent Colors
            PresentationSectionTitle("THEME & ACCENT COLORS")
            ThemeColorPickerSection(
                themeSettings = themeSettings,
                onSelectTheme = { theme -> scope.launch { prefManager.updateAppTheme(theme) } },
                onToggleAmoledBlack = { enabled -> scope.launch { prefManager.updateAmoledBlack(enabled) } },
                onCustomFirstColorChange = { hex -> scope.launch { prefManager.updateCustomThemeFirstHex(hex) } },
                onCustomSecondColorChange = { hex -> scope.launch { prefManager.updateCustomThemeSecondHex(hex) } }
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

            // Section 1: Details Page Presentation (Pure visual wireframes — no text labels)
            PresentationSectionTitle("DETAILS PAGE LAYOUT")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    DetailsPagePresentation.HERO,
                    DetailsPagePresentation.CENTERED_COVER,
                    DetailsPagePresentation.CLASSIC
                ).forEach { mode ->
                    val isSelected = presentationSettings.detailsPagePresentation == mode
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) AccentGold else Color.White.copy(alpha = 0.12f),
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "detailsLayoutTileBorder"
                    )
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) AccentGold.copy(alpha = 0.08f) else DarkSurfaceElevated.copy(alpha = 0.72f),
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "detailsLayoutTileBg"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(112.dp)
                            .semantics { contentDescription = mode.displayName }
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .border(
                                width = if (isSelected) 1.5.dp else 0.8.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                scope.launch {
                                    prefManager.updateDetailsPagePresentation(mode)
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(7.dp))
                                .background(AmoledBlack)
                                .border(0.6.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(7.dp))
                        ) {
                            DetailsLayoutWireframe(presentation = mode)
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(5.dp)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(AccentGold),
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
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        scope.launch {
                            prefManager.updateAutoCenterCoverWhenNoBanner(!presentationSettings.autoCenterCoverWhenNoBanner)
                        }
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-center cover when no banner",
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Automatically center cover art when a title has no wide banner",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = presentationSettings.autoCenterCoverWhenNoBanner,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            prefManager.updateAutoCenterCoverWhenNoBanner(enabled)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnAccentColor,
                        checkedTrackColor = AccentGold,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.14f)
                    )
                )
            }

            // Section 1B: Discover Tab Layout (Layout A / B / C)
            PresentationSectionTitle("DISCOVER TAB LAYOUT")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiscoverLayoutStyle.entries.forEach { style ->
                    val shortTitle = when (style) {
                        DiscoverLayoutStyle.EDITORIAL_SPOTLIGHT -> "Spotlight"
                        DiscoverLayoutStyle.HERO_CAROUSEL -> "Carousel"
                        DiscoverLayoutStyle.MINIMAL_RAILS -> "Minimal"
                    }
                    val shortSub = when (style) {
                        DiscoverLayoutStyle.EDITORIAL_SPOTLIGHT -> "Hero + shelves"
                        DiscoverLayoutStyle.HERO_CAROUSEL -> "Top carousel"
                        DiscoverLayoutStyle.MINIMAL_RAILS -> "Search first"
                    }
                    VisualOptionTile(
                        title = shortTitle,
                        subtitle = shortSub,
                        isSelected = discoverLayoutStyle == style,
                        modifier = Modifier.weight(1f),
                        previewHeight = 68.dp,
                        onClick = {
                            scope.launch {
                                prefManager.updateDiscoverLayoutStyle(style)
                            }
                        }
                    ) {
                        DiscoverLayoutWireframe(style = style)
                    }
                }
            }

            // Section 2: Banner Sizing
            PresentationSectionTitle("HERO BANNER SIZING")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isFullBleed = presentationSettings.bannerSizing == BannerSizing.FULL_BLEED
                VisualOptionTile(
                    title = "Full-Bleed Hero",
                    subtitle = "Tall backdrop fading cleanly to black",
                    isSelected = isFullBleed,
                    modifier = Modifier.weight(1f),
                    previewHeight = 76.dp,
                    onClick = {
                        scope.launch {
                            prefManager.updateBannerSizing(BannerSizing.FULL_BLEED)
                        }
                    }
                ) {
                    BannerSizingWireframe(isFullBleed = true)
                }

                val isCinema = presentationSettings.bannerSizing == BannerSizing.CINEMA_SHOWCASE
                VisualOptionTile(
                    title = "Cinema Showcase",
                    subtitle = "Floating 16:9 frame with ambient glow",
                    isSelected = isCinema,
                    modifier = Modifier.weight(1f),
                    previewHeight = 76.dp,
                    onClick = {
                        scope.launch {
                            prefManager.updateBannerSizing(BannerSizing.CINEMA_SHOWCASE)
                        }
                    }
                ) {
                    BannerSizingWireframe(isFullBleed = false)
                }
            }

            // Section 3: Fallback Ambient Atmosphere
            PresentationSectionTitle("FALLBACK AMBIENT AURA")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isBlur = presentationSettings.fallbackBackgroundStyle == FallbackBackgroundStyle.CINEMATIC_BLUR
                VisualOptionTile(
                    title = "Cinematic Blur",
                    subtitle = "Soft ambient artwork aura backdrop",
                    isSelected = isBlur,
                    modifier = Modifier.weight(1f),
                    previewHeight = 68.dp,
                    onClick = {
                        scope.launch {
                            prefManager.updateFallbackBackgroundStyle(FallbackBackgroundStyle.CINEMATIC_BLUR)
                        }
                    }
                ) {
                    AmbientAuraWireframe(isBlur = true)
                }

                val isSolidBlack = presentationSettings.fallbackBackgroundStyle == FallbackBackgroundStyle.SOLID_BLACK
                VisualOptionTile(
                    title = "Minimalist OLED",
                    subtitle = "Pure #000000 canvas, zero bleed",
                    isSelected = isSolidBlack,
                    modifier = Modifier.weight(1f),
                    previewHeight = 68.dp,
                    onClick = {
                        scope.launch {
                            prefManager.updateFallbackBackgroundStyle(FallbackBackgroundStyle.SOLID_BLACK)
                        }
                    }
                ) {
                    AmbientAuraWireframe(isBlur = false)
                }
            }

            // Section 3B: Wide Hero Banner Blur (Universal + Per-Category Overrides)
            PresentationSectionTitle("WIDE HERO BANNER BLUR (HOME, DISCOVER & DETAILS)")
            var selectedBlurCategory by remember { mutableStateOf(MediaType.ANIME) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Universal Banner Blur",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Default blur applied to wide hero banners unless overridden by a category",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                    if (presentationSettings.categoryBannerBlurOverrides.isNotEmpty()) {
                        Text(
                            text = "Clear Overrides (${presentationSettings.categoryBannerBlurOverrides.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentGold.copy(alpha = 0.14f))
                                .clickable {
                                    scope.launch { prefManager.clearAllCategoryHeroBannerBlurOverrides() }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HeroBannerBlurLevel.entries.forEach { level ->
                        val active = presentationSettings.universalBannerBlur == level
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AccentGold.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                                .border(
                                    0.8.dp,
                                    if (active) AccentGold else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    scope.launch { prefManager.updateUniversalHeroBannerBlur(level) }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = level.shortLabel,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) AccentGold else TextPrimary
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.6.dp)

                Text(
                    text = "Per-Category Override",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MediaType.filterTabs.filter { it != MediaType.ALL }.forEach { cat ->
                        val hasOverride = presentationSettings.categoryBannerBlurOverrides.containsKey(cat.name)
                        val isSelected = selectedBlurCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        isSelected -> AccentGold.copy(alpha = 0.20f)
                                        hasOverride -> AccentGold.copy(alpha = 0.08f)
                                        else -> Color.White.copy(alpha = 0.05f)
                                    }
                                )
                                .border(
                                    0.7.dp,
                                    when {
                                        isSelected -> AccentGold
                                        hasOverride -> AccentGold.copy(alpha = 0.45f)
                                        else -> Color.Transparent
                                    },
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { selectedBlurCategory = cat }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            val overrideLvl = presentationSettings.categoryBannerBlurOverrides[cat.name]
                            Text(
                                text = if (overrideLvl != null) "${cat.displayName} \u00B7 ${overrideLvl.shortLabel}" else cat.displayName,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected || hasOverride) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected || hasOverride) AccentGold else TextSecondary
                            )
                        }
                    }
                }

                val currentCatOverride = presentationSettings.categoryBannerBlurOverrides[selectedBlurCategory.name]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val inheritActive = currentCatOverride == null
                    Box(
                        modifier = Modifier
                            .weight(1.15f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (inheritActive) AccentGold.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                            .border(
                                0.8.dp,
                                if (inheritActive) AccentGold else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                scope.launch { prefManager.updateCategoryHeroBannerBlurOverride(selectedBlurCategory, null) }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Inherit (${presentationSettings.universalBannerBlur.shortLabel})",
                            fontSize = 11.sp,
                            fontWeight = if (inheritActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (inheritActive) AccentGold else TextPrimary
                        )
                    }
                    HeroBannerBlurLevel.entries.forEach { level ->
                        val active = currentCatOverride == level
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AccentGold.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                                .border(
                                    0.8.dp,
                                    if (active) AccentGold else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    scope.launch { prefManager.updateCategoryHeroBannerBlurOverride(selectedBlurCategory, level) }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = level.shortLabel,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) AccentGold else TextPrimary
                            )
                        }
                    }
                }
            }

            // Section 4: Preferred Title Localization
            PresentationSectionTitle("PREFERRED TITLE LOCALIZATION")
            val titlePref by prefManager.preferredTitleLanguage.collectAsState(initial = com.omnitrack.app.data.preferences.PreferredTitleLanguage.ROMAJI)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.omnitrack.app.data.preferences.PreferredTitleLanguage.values().forEach { lang ->
                    PresentationChoiceCard(
                        title = lang.displayName,
                        description = lang.description,
                        isSelected = titlePref == lang,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                prefManager.updatePreferredTitleLanguage(lang)
                            }
                        }
                    )
                }
            }

            // Section 5: Visual Novels (Spoilers & NSFW)
            PresentationSectionTitle("VISUAL NOVELS (SPOILERS & NSFW)")
            val vnSpoilerLevel by prefManager.vnSpoilerLevel.collectAsState(initial = com.omnitrack.app.data.model.VnSpoilerLevel.MINOR)
            val vnNsfwMode by prefManager.vnNsfwFilterMode.collectAsState(initial = com.omnitrack.app.data.model.VnNsfwFilterMode.BLUR_TAP)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Default Spoiler Visibility", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                com.omnitrack.app.data.model.VnSpoilerLevel.entries.forEach { level ->
                    PresentationChoiceCard(
                        title = level.displayName,
                        description = level.description,
                        isSelected = vnSpoilerLevel == level,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch { prefManager.updateVnSpoilerLevel(level) }
                        }
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NSFW CG & Artwork Filter", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                com.omnitrack.app.data.model.VnNsfwFilterMode.entries.forEach { mode ->
                    PresentationChoiceCard(
                        title = mode.displayName,
                        description = mode.description,
                        isSelected = vnNsfwMode == mode,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch { prefManager.updateVnNsfwFilterMode(mode) }
                        }
                    )
                }
            }

            // Section 6: Fanfiction (Tab Visibility & Mature/Explicit NSFW Filter)
            PresentationSectionTitle("FANFICTION (TAB & NSFW)")
            val mediaCategoryPrefs by prefManager.mediaCategoryPrefs.collectAsState(
                initial = com.omnitrack.app.data.preferences.MediaCategoryPrefs()
            )
            val fanfictionEnabled = !mediaCategoryPrefs.isHidden(MediaType.FANFICTION)
            val fanficNsfwMode by prefManager.fanficNsfwFilterMode.collectAsState(initial = com.omnitrack.app.data.model.VnNsfwFilterMode.BLUR_TAP)
            PresentationChoiceCard(
                title = if (fanfictionEnabled) "Fanfiction Tab Visible" else "Fanfiction Tab Hidden",
                description = "Show or hide the Fanfiction category tab, Discover shelf, and dashboard rail.",
                isSelected = fanfictionEnabled,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch { prefManager.updateFanfictionEnabled(!fanfictionEnabled) }
                }
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mature / Explicit Cover & Tag Filter", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                com.omnitrack.app.data.model.VnNsfwFilterMode.entries.forEach { mode ->
                    PresentationChoiceCard(
                        title = mode.displayName,
                        description = mode.description,
                        isSelected = fanficNsfwMode == mode,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch { prefManager.updateFanficNsfwFilterMode(mode) }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

            // Section 7: Performance Mode & Advanced Optimizations
            PresentationSectionTitle("PERFORMANCE & SMOOTHNESS")
            val perfSettings by prefManager.performanceSettings.collectAsState(initial = PerformanceSettings())
            PerformanceSettingsContent(
                preferenceManager = prefManager,
                settings = perfSettings,
                initiallyExpanded = false
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnimeArtworkCanvas(modifier: Modifier = Modifier) {
    val accent = AccentGold
    val variant = OmniThemeState.accentVariant
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Deep theme-tinted twilight sky
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    AmoledBlack,
                    DarkSurfaceElevated,
                    variant.copy(alpha = 0.24f),
                    accent.copy(alpha = 0.16f)
                )
            )
        )

        // 2. Soft theme-tinted disc in upper right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = 0.85f),
                    variant.copy(alpha = 0.35f),
                    Color.Transparent
                ),
                center = Offset(w * 0.76f, h * 0.32f),
                radius = h * 0.36f
            ),
            radius = h * 0.36f,
            center = Offset(w * 0.76f, h * 0.32f)
        )

        // 3. Distant silhouette ridges
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
                    DarkSurfaceElevated.copy(alpha = 0.95f),
                    AmoledBlack
                ),
                startY = h * 0.32f,
                endY = h
            )
        )

        // 4. Foreground silhouette ridge
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
            color = AmoledBlack
        )

        // 5. Subtle theme-reactive horizon glow line
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    accent.copy(alpha = 0.08f),
                    accent.copy(alpha = 0.72f),
                    variant.copy(alpha = 0.62f),
                    accent.copy(alpha = 0.08f)
                )
            ),
            start = Offset(0f, h * 0.78f),
            end = Offset(w, h * 0.78f),
            strokeWidth = 1.2.dp.toPx()
        )
    }
}

@Composable
private fun LivePresentationMockup(
    settings: PresentationSettings,
    modifier: Modifier = Modifier
) {
    val accent = AccentGold
    val variant = OmniThemeState.accentVariant
    val mode = settings.detailsPagePresentation
    val isCenteredCover = mode == DetailsPagePresentation.CENTERED_COVER
    val isClassic = mode == DetailsPagePresentation.CLASSIC
    val isFullBleed = settings.bannerSizing == BannerSizing.FULL_BLEED
    val isSolidBlack = settings.fallbackBackgroundStyle == FallbackBackgroundStyle.SOLID_BLACK

    val posterAlpha by animateFloatAsState(
        targetValue = if (mode == DetailsPagePresentation.HERO) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "posterAlpha"
    )

    val posterTranslateY by animateDpAsState(
        targetValue = if (mode == DetailsPagePresentation.HERO) 20.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "posterTransY"
    )

    val titlePaddingStart by animateDpAsState(
        targetValue = if (isClassic) 84.dp else 16.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "titleStart"
    )

    val containerBg = if (isSolidBlack) AmoledBlack else DarkSurfaceElevated
    val bottomRowBg = if (isSolidBlack) AmoledBlack else AmoledBlack.copy(alpha = 0.55f)
    val dissolveTarget = if (isSolidBlack) AmoledBlack else AmoledBlack.copy(alpha = 0.65f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(228.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(containerBg)
                .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
        ) {
            // Theme-reactive Atmospheric Ambient Aura Backdrop (toggles on in Cinematic Blur)
            if (!isSolidBlack) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    variant.copy(alpha = 0.42f),
                                    accent.copy(alpha = 0.24f),
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
                                    accent.copy(alpha = 0.34f),
                                    variant.copy(alpha = 0.18f),
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
                        .background(Color.Black.copy(alpha = 0.24f))
                )
            }

            // Banner Sizing Mockup Area (hidden in CENTERED_COVER mode)
            if (!isCenteredCover) {
                if (isFullBleed) {
                    // MODE A: Full-Bleed Edge-to-Edge Banner dissolving via smoothstep
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(144.dp)
                    ) {
                        AnimeArtworkCanvas(modifier = Modifier.fillMaxSize())

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.15f to Color.Transparent,
                                        0.60f to dissolveTarget.copy(alpha = 0.58f),
                                        1.00f to dissolveTarget
                                    )
                                )
                        )
                    }
                } else {
                    // MODE B: Cinema Showcase Floating 16:9 Widescreen Card with Theme Ambient Aura
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .height(108.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = 1.06f
                                    scaleY = 1.18f
                                    alpha = 0.65f
                                }
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            accent.copy(alpha = 0.48f),
                                            variant.copy(alpha = 0.30f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp))
                                .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                        ) {
                            AnimeArtworkCanvas(modifier = Modifier.fillMaxSize())
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                                        )
                                    )
                            )
                        }
                    }
                }
            } else {
                // Soft centered ambient glow behind the centered poster
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(126.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.22f),
                                    variant.copy(alpha = 0.14f),
                                    Color.Transparent
                                ),
                                radius = 260f
                            )
                        )
                )
            }

            // Mock Poster (Top-center in CENTERED_COVER, left-overlap in CLASSIC, hidden in HERO)
            if (posterAlpha > 0.01f) {
                if (isCenteredCover) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 18.dp)
                            .graphicsLayer {
                                alpha = posterAlpha
                            }
                            .width(60.dp)
                            .height(90.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(variant.copy(alpha = 0.85f), accent.copy(alpha = 0.90f))
                                )
                            )
                            .border(0.8.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                            .shadow(elevation = 10.dp, shape = RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "COVER",
                            color = OnAccentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp, top = 58.dp)
                            .graphicsLayer {
                                alpha = posterAlpha
                                translationY = posterTranslateY.toPx()
                            }
                            .width(54.dp)
                            .height(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(variant.copy(alpha = 0.85f), accent.copy(alpha = 0.90f))
                                )
                            )
                            .border(0.8.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                            .shadow(elevation = 10.dp, shape = RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "POSTER",
                            color = OnAccentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Title Row Mock (Centered in HERO & CENTERED_COVER, Left-aligned next to poster in CLASSIC)
            val isCenteredText = !isClassic
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(bottom = 50.dp)
                    .padding(start = titlePaddingStart, end = 16.dp),
                horizontalAlignment = if (isCenteredText) Alignment.CenterHorizontally else Alignment.Start
            ) {
                Text(
                    text = "Cyberpunk: Edgerunners",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    textAlign = if (isCenteredText) TextAlign.Center else TextAlign.Start
                )
                Text(
                    text = "ANIME · 2022 · STUDIO TRIGGER",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    textAlign = if (isCenteredText) TextAlign.Center else TextAlign.Start
                )
            }

            // Unboxed Stats Row Mock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .drawBehind {
                        val strokeW = 0.8.dp.toPx()
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
                            .background(accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+1", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
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
                            .background(accent)
                    )
                    Text("COMPLETED", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(12.dp)
                    )
                    Text("9.5", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Clean non-overlapping caption bar below preview frame
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSolidBlack) "Minimalist OLED (#000000)" else "Cinematic Blur Aura",
                fontSize = 10.5.sp,
                color = if (isSolidBlack) TextSecondary else accent,
                fontWeight = FontWeight.SemiBold
            )
            if (!isCenteredCover) {
                Text(
                    text = if (isFullBleed) "Full-Bleed" else "16:9 Cinema",
                    fontSize = 10.5.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
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
        targetValue = if (isSelected) AccentGold.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardBg"
    )
    val lineColor by animateColorAsState(
        targetValue = if (isSelected) AccentGold else Color.White.copy(alpha = 0.08f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardLine"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = if (isSelected) AccentGold else TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(AccentGold),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = OnAccentColor,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
        Text(
            text = description,
            color = TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 15.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isSelected) 1.5.dp else 0.6.dp)
                .background(lineColor)
        )
    }
}
