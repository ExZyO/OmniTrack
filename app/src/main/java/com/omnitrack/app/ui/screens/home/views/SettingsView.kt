package com.omnitrack.app.ui.screens.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.BuildConfig
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.preferences.PerformanceSettings
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.data.sync.TrackerTokens
import com.omnitrack.app.ui.components.ApiKeyConfigSheet
import com.omnitrack.app.ui.components.BackupRestoreSheet
import com.omnitrack.app.ui.components.PerformanceSettingsSheet
import com.omnitrack.app.ui.components.PosterCustomizationSheet
import com.omnitrack.app.ui.components.TrackerAccountsSheet
import com.omnitrack.app.ui.screens.detail.components.EpisodeLayoutSheet
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

private data class SettingRowEntry(
    val icon: ImageVector,
    val title: String,
    val keywords: String,
    val trailingText: String? = null,
    val accentIcon: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun SettingsView(
    allMedia: List<MediaCardSummary>,
    onCheckUpdate: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onNavigateToArtworkPriority: () -> Unit = {},
    onNavigateToVisualPresentation: () -> Unit = {},
    onNavigateToDetailsPageLayout: () -> Unit = {},
    onNavigateToManageCategories: () -> Unit = {},
    onOpenHomeCustomizer: () -> Unit = {},
    onOpenDiscoverCustomizer: () -> Unit = {},
    onOpenFanficSites: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as com.omnitrack.app.OmniTrackApplication }
    val prefManager = app.preferenceManager
    val tokenManager = app.trackerTokenManager
    val presentationSettings by prefManager.presentationSettings.collectAsState(initial = PresentationSettings())
    val perfSettings by prefManager.performanceSettings.collectAsState(initial = PerformanceSettings())
    val tokens by tokenManager.tokens.collectAsState(initial = TrackerTokens())
    val fanficConnectedSites by tokenManager.fanficConnectedSites.collectAsState(initial = emptySet())

    val connectedCount = remember(tokens) {
        listOf(
            tokens.anilistToken,
            tokens.malToken,
            tokens.traktToken,
            tokens.simklToken,
            tokens.comickToken,
            tokens.vndbToken,
            tokens.steamApiKey ?: tokens.steamId64,
            tokens.bangumiToken,
            tokens.tmdbToken,
            tokens.igdbToken,
            tokens.hardcoverToken
        ).count { !it.isNullOrBlank() }
    }

    var searchQuery by remember { mutableStateOf("") }
    var showApiConfigSheet by remember { mutableStateOf(false) }
    var showBackupRestoreSheet by remember { mutableStateOf(false) }
    var showTrackerAccountsSheet by remember { mutableStateOf(false) }
    var showPosterCustomizationSheet by remember { mutableStateOf(false) }
    var showEpisodeLayoutSheet by remember { mutableStateOf(false) }
    var showPerformanceSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val accountRows = listOf(
        SettingRowEntry(
            icon = Icons.Default.CloudSync,
            title = "Tracker Accounts",
            keywords = "anilist mal trakt simkl bangumi vndb steam hardcover sync import login",
            trailingText = if (connectedCount > 0) "$connectedCount linked" else null,
            accentIcon = connectedCount > 0,
            onClick = { showTrackerAccountsSheet = true }
        ),
        SettingRowEntry(
            icon = Icons.Default.Public,
            title = "Fanfiction Sites & Import",
            keywords = "fanfic ao3 ffn fanfiction spacebattles sufficient velocity qq cookies bookmarks",
            trailingText = if (fanficConnectedSites.isNotEmpty()) "${fanficConnectedSites.size} sites" else null,
            accentIcon = fanficConnectedSites.isNotEmpty(),
            onClick = onOpenFanficSites
        ),
        SettingRowEntry(
            icon = Icons.Default.Key,
            title = "API Keys & Integrations",
            keywords = "api key tmdb igdb gemini steamgriddb secret comic vine metron",
            onClick = { showApiConfigSheet = true }
        ),
        SettingRowEntry(
            icon = Icons.Default.Archive,
            title = "Backup & Restore",
            keywords = "backup restore export import json csv xml artwork",
            onClick = { showBackupRestoreSheet = true }
        )
    )

    val appearanceRows = listOf(
        SettingRowEntry(
            icon = Icons.Default.Home,
            title = "Home & Hero Layout",
            keywords = "home hero carousel customize reorder shelves layout timeline editorial",
            accentIcon = true,
            onClick = onOpenHomeCustomizer
        ),
        SettingRowEntry(
            icon = Icons.Default.Explore,
            title = "Discover Layout & Sections",
            keywords = "discover layout customize hide disable shelves categories banner",
            accentIcon = true,
            onClick = onOpenDiscoverCustomizer
        ),
        SettingRowEntry(
            icon = Icons.Default.Palette,
            title = "Visual Presentation & Colors",
            keywords = "theme colors accent hue hex amoled presentation hero classic backdrop blur",
            trailingText = presentationSettings.detailsPagePresentation.displayName,
            onClick = onNavigateToVisualPresentation
        ),
        SettingRowEntry(
            icon = Icons.Default.ViewCarousel,
            title = "Details Page Layout",
            keywords = "details layout sections reorder drag",
            onClick = onNavigateToDetailsPageLayout
        ),
        SettingRowEntry(
            icon = Icons.Default.ViewModule,
            title = "Poster Card Customization",
            keywords = "poster card size corner radius depth landscape clearlogo",
            onClick = { showPosterCustomizationSheet = true }
        ),
        SettingRowEntry(
            icon = Icons.Default.Image,
            title = "Artwork Priority",
            keywords = "artwork priority cover banner provider anilist tmdb openlibrary",
            onClick = onNavigateToArtworkPriority
        ),
        SettingRowEntry(
            icon = Icons.Default.Folder,
            title = "Categories & Media Types",
            keywords = "categories media types hide disable reorder games anime comics fanfiction folders tabs library chimanew order visibility",
            onClick = onNavigateToManageCategories
        ),
        SettingRowEntry(
            icon = Icons.Default.ViewCarousel,
            title = "Episode & Volume Layout",
            keywords = "episode chapter volume layout grid card list scale",
            onClick = { showEpisodeLayoutSheet = true }
        )
    )

    val systemRows = listOf(
        SettingRowEntry(
            icon = Icons.Default.Speed,
            title = "Performance & Smoothness",
            keywords = "performance mode lag fps blur gpu animation cache memory optimization smooth",
            trailingText = if (perfSettings.performanceModeEnabled) "Perf Mode" else "Balanced",
            accentIcon = perfSettings.performanceModeEnabled,
            onClick = { showPerformanceSheet = true }
        ),
        SettingRowEntry(
            icon = Icons.Default.SystemUpdate,
            title = "Check for Updates",
            keywords = "update version release github apk actions build",
            trailingText = "v${BuildConfig.VERSION_NAME}",
            accentIcon = true,
            onClick = onCheckUpdate
        ),
        SettingRowEntry(
            icon = Icons.Default.BugReport,
            title = "Diagnostics & Live Logcat",
            keywords = "diagnostics logs logcat debug crash cache",
            onClick = onOpenDiagnostics
        ),
        SettingRowEntry(
            icon = Icons.Default.Info,
            title = "About OmniTrack",
            keywords = "about schema version database library count",
            trailingText = "v24",
            onClick = { showAboutDialog = true }
        )
    )

    val q = searchQuery.trim()
    val filteredGroups = remember(q, accountRows, appearanceRows, systemRows) {
        val raw = listOf(
            "ACCOUNTS & SYNC" to accountRows,
            "APPEARANCE & LAYOUT" to appearanceRows,
            "SYSTEM & UPDATES" to systemRows
        )
        if (q.isEmpty()) raw
        else raw.mapNotNull { (header, rows) ->
            val matched = rows.filter { row ->
                row.title.contains(q, ignoreCase = true) ||
                    row.keywords.contains(q, ignoreCase = true)
            }
            if (matched.isNotEmpty()) header to matched else null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Column(modifier = Modifier.padding(top = 14.dp)) {
            Text(
                text = "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "${allMedia.size} tracked \u00B7 $connectedCount connected",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Clean Flat Search Bar (Option A)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = AccentGold,
                modifier = Modifier.size(16.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(AccentGold),
                    modifier = Modifier.fillMaxWidth()
                )
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search settings...",
                        color = TextSecondary.copy(alpha = 0.65f),
                        fontSize = 13.5.sp
                    )
                }
            }
            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { searchQuery = "" }
                )
            }
        }

        // Frameless Option A Hairline Groups
        if (filteredGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching settings for \"$searchQuery\"",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
        } else {
            filteredGroups.forEach { (sectionTitle, rows) ->
                SettingsHairlineGroup(
                    title = sectionTitle,
                    rows = rows
                )
            }
        }
    }

    if (showApiConfigSheet) {
        ApiKeyConfigSheet(
            preferenceManager = prefManager,
            onDismiss = { showApiConfigSheet = false }
        )
    }

    if (showBackupRestoreSheet) {
        BackupRestoreSheet(
            onDismiss = { showBackupRestoreSheet = false }
        )
    }

    if (showTrackerAccountsSheet) {
        TrackerAccountsSheet(
            onDismiss = { showTrackerAccountsSheet = false }
        )
    }

    if (showPosterCustomizationSheet) {
        PosterCustomizationSheet(
            preferenceManager = prefManager,
            onDismiss = { showPosterCustomizationSheet = false }
        )
    }

    if (showEpisodeLayoutSheet) {
        EpisodeLayoutSheet(
            preferenceManager = prefManager,
            onDismiss = { showEpisodeLayoutSheet = false }
        )
    }

    if (showPerformanceSheet) {
        PerformanceSettingsSheet(
            preferenceManager = prefManager,
            settings = perfSettings,
            onDismiss = { showPerformanceSheet = false }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = AmoledBlack,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "About OmniTrack",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                        fontSize = 13.5.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Room Database Schema v24",
                        fontSize = 13.sp,
                        color = AccentGold,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tracking ${allMedia.size} titles across 10 media categories.",
                        fontSize = 12.5.sp,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(
                        text = "Close",
                        color = AccentGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }
}

@Composable
private fun SettingsHairlineGroup(
    title: String,
    rows: List<SettingRowEntry>
) {
    Column {
        Text(
            text = title,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary.copy(alpha = 0.78f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = row.onClick)
                    .padding(vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = row.icon,
                    contentDescription = null,
                    tint = if (row.accentIcon) AccentGold else TextPrimary,
                    modifier = Modifier.size(19.dp)
                )

                Text(
                    text = row.title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (!row.trailingText.isNullOrBlank()) {
                    Text(
                        text = row.trailingText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp)
                )
            }
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                thickness = 0.6.dp
            )
        }
    }
}
