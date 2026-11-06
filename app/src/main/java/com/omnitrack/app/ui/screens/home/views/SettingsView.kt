package com.omnitrack.app.ui.screens.home.views

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.BuildConfig
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.TrackingStatus
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

@Composable
fun SettingsView(
    allMedia: List<MediaItemEntity>,
    onCheckUpdate: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
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
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround
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

        // Section: Presentation & Display
        SettingsSection(title = "VISUAL PRESENTATION & HERO") {
            GroupedSettingRow(
                icon = Icons.Default.Image,
                iconTint = AccentArctic,
                title = "Backdrop Style",
                subtitle = "Cinematic Blur (Gentle ambient glow + zero vignette)",
                badge = "ACTIVE"
            )
            SectionDivider()
            GroupedSettingRow(
                icon = Icons.Default.Settings,
                iconTint = AccentJade,
                title = "Details Presentation",
                subtitle = "Nuvio Hero (Unblocked banner + centered info pills)"
            )
            SectionDivider()
            GroupedSettingRow(
                icon = Icons.Default.Image,
                iconTint = AccentGold,
                title = "Banner Sizing",
                subtitle = "Cinema Showcase Card (Widescreen + Ambient Aura)"
            )
            SectionDivider()
            GroupedSettingRow(
                icon = Icons.Default.Image,
                iconTint = AccentArctic,
                title = "Backdrop Transition",
                subtitle = "Cinematic Fade (17-stop quintic smoothstep curve)"
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
                subtitle = "Room 2.6.1 with SQLite Write-Ahead Logging (WAL)"
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 1.1.sp,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurfaceCard)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp)
            .height(0.6.dp)
            .background(Color(0x12FFFFFF))
    )
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .height(32.dp)
            .width(0.8.dp)
            .background(Color(0x16FFFFFF))
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
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.14f)),
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
                    .background(iconTint.copy(alpha = 0.18f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
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
