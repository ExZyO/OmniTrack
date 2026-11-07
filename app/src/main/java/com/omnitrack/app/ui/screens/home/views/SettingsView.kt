package com.omnitrack.app.ui.screens.home.views

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.BuildConfig
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
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
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsView(
    allMedia: List<MediaCardSummary>,
    onCheckUpdate: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onNavigateToArtworkPriority: () -> Unit = {},
    onNavigateToVisualPresentation: () -> Unit = {},
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // App Identity Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = TextPrimary
            )
            Text(
                text = "OmniTrack Universal Media Tracker",
                fontSize = 12.5.sp,
                color = TextMuted
            )
        }

        // Section: Library Overview
        SettingsSection(title = "LIBRARY OVERVIEW") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatMetric(
                    count = allMedia.size.toString(),
                    label = "Total Items",
                    accentColor = AccentArctic
                )
                StatDivider()
                StatMetric(
                    count = allMedia.count { it.status == TrackingStatus.IN_PROGRESS }.toString(),
                    label = "In Progress",
                    accentColor = AccentJade
                )
                StatDivider()
                StatMetric(
                    count = allMedia.count { it.status == TrackingStatus.COMPLETED }.toString(),
                    label = "Completed",
                    accentColor = AccentGold
                )
            }
        }

        // Section: Visual Presentation & Themes (Dedicated Screen with Live Mockup)
        SettingsSection(title = "VISUAL PRESENTATION & THEMES") {
            GroupedSettingRow(
                icon = Icons.Default.ViewCarousel,
                iconTint = AccentGold,
                title = "Visual Presentation & Themes",
                subtitle = "Interactive preview & customization for hero layouts, banner sizing & ambient auras",
                badge = "CUSTOMIZE",
                onClick = onNavigateToVisualPresentation
            )
        }

        // Section: Artwork & Metadata Sources (Dedicated Fullscreen Waterfall)
        SettingsSection(title = "ARTWORK & METADATA SOURCES") {
            GroupedSettingRow(
                icon = Icons.Default.Palette,
                iconTint = AccentArctic,
                title = "Artwork Priority & Waterfall",
                subtitle = "Fluid drag-to-reorder & toggle upstream fallbacks per category",
                badge = "OPEN",
                onClick = onNavigateToArtworkPriority
            )
        }

        // Section: Updates & Maintenance
        SettingsSection(title = "UPDATES & MAINTENANCE") {
            GroupedSettingRow(
                icon = Icons.Default.SystemUpdate,
                iconTint = AccentJade,
                title = "Check for Updates",
                subtitle = "Installed: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                badge = "CHECK",
                onClick = onCheckUpdate
            )
            SectionDivider()
            GroupedSettingRow(
                icon = Icons.Default.BugReport,
                iconTint = AccentArctic,
                title = "Diagnostics & System Logs",
                subtitle = "Inspect live logs, HTTP requests, and update logs",
                onClick = onOpenDiagnostics
            )
        }

        // Section: Engine & Security Integrity
        SettingsSection(title = "SYSTEM & ENGINE INTEGRITY") {
            GroupedSettingRow(
                icon = Icons.Default.Lock,
                iconTint = AccentGold,
                title = "Release Keystore Signing",
                subtitle = "Deterministic GitHub Actions keystore verified"
            )
            SectionDivider()
            GroupedSettingRow(
                icon = Icons.Default.Storage,
                iconTint = AccentArctic,
                title = "Local Database Engine",
                subtitle = "Room 2.6.1 with SQLite Write-Ahead Logging (WAL) & Schema v3"
            )
            SectionDivider()
            GroupedSettingRow(
                icon = Icons.Default.Info,
                iconTint = TextMuted,
                title = "About OmniTrack",
                subtitle = "Unified native media tracker for Anime, Manga, Novels, Shows, Movies, Games & VNs"
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.3.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun SectionDivider() {
    Spacer(modifier = Modifier.height(2.dp))
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(0.8.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}

@Composable
private fun StatMetric(
    count: String,
    label: String,
    accentColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun GroupedSettingRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = TextMuted,
                lineHeight = 16.sp
            )
        }

        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(iconTint.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = iconTint
                )
            }
        }
    }
}
