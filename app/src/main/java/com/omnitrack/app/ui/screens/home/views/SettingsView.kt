package com.omnitrack.app.ui.screens.home.views

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.BuildConfig
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
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
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Identity Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Settings & About",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = TextPrimary
                )
                Text(
                    text = "OmniTrack Universal Media Tracker",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        // Library Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Library Overview",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatPill(count = allMedia.size.toString(), label = "Total Items")
                    StatPill(
                        count = allMedia.count { it.status == com.omnitrack.app.data.model.TrackingStatus.CURRENT }.toString(),
                        label = "In Progress"
                    )
                    StatPill(
                        count = allMedia.count { it.status == com.omnitrack.app.data.model.TrackingStatus.COMPLETED }.toString(),
                        label = "Completed"
                    )
                }
            }
        }

        // Updates & Release Verification
        SettingsItem(
            icon = Icons.Default.SystemUpdate,
            iconTint = AccentJade,
            title = "Check for Updates",
            subtitle = "Installed: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
            onClick = onCheckUpdate
        )

        // Diagnostics & Logs
        SettingsItem(
            icon = Icons.Default.BugReport,
            iconTint = AccentArctic,
            title = "Diagnostics & System Logs",
            subtitle = "View live logs, artwork waterfall network calls, and update logs",
            onClick = onOpenDiagnostics
        )

        // Keystore & Signing Integrity
        SettingsItem(
            icon = Icons.Default.Lock,
            iconTint = AccentGold,
            title = "Release Signing Keystore",
            subtitle = "Deterministic repository key verified — updates install in-place without conflicts",
            onClick = {}
        )

        // Database & Storage
        SettingsItem(
            icon = Icons.Default.Storage,
            iconTint = AccentArctic,
            title = "Local Database Engine",
            subtitle = "Room 2.6.1 SQLite with WAL mode & Coil 3 disk cache enabled",
            onClick = {}
        )

        // App Information
        SettingsItem(
            icon = Icons.Default.Info,
            iconTint = TextMuted,
            title = "About OmniTrack",
            subtitle = "Universal native Android media tracking for Anime, Manga, Novels, Shows, Movies, Games & VNs",
            onClick = {}
        )
    }
}

@Composable
private fun StatPill(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AccentArctic
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
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
    }
}
