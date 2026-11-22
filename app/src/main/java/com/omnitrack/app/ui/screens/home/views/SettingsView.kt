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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
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
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.data.sync.TrackerTokenManager
import com.omnitrack.app.data.sync.TrackerTokens
import com.omnitrack.app.ui.components.ApiKeyConfigSheet
import com.omnitrack.app.ui.components.BackupRestoreSheet
import com.omnitrack.app.ui.components.PosterCustomizationSheet
import com.omnitrack.app.ui.components.TrackerAccountsSheet
import com.omnitrack.app.ui.screens.detail.components.EpisodeLayoutSheet
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

private data class SettingRowEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
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
    onOpenHomeCustomizer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefManager = remember { PreferenceManager(context) }
    val tokenManager = remember { TrackerTokenManager(context) }
    val presentationSettings by prefManager.presentationSettings.collectAsState(initial = PresentationSettings())
    val tokens by tokenManager.tokens.collectAsState(initial = TrackerTokens())

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
    var showAboutDialog by remember { mutableStateOf(false) }

    val accountRows = listOf(
        SettingRowEntry(
            icon = Icons.Default.CloudSync,
            title = "Tracker Accounts",
            subtitle = "Connect AniList, MAL, Trakt, Simkl, Bangumi & more",
            keywords = "anilist mal trakt simkl bangumi vndb steam hardcover sync import login",
            trailingText = "$connectedCount linked",
            accentIcon = connectedCount > 0,
            onClick = { showTrackerAccountsSheet = true }
        ),
        SettingRowEntry(
            icon = Icons.Default.Key,
            title = "API Keys & Integrations",
            subtitle = "TMDb, IGDB, Gemini AI & SteamGridDB keys",
            keywords = "api key tmdb igdb gemini steamgriddb secret",
            onClick = { showApiConfigSheet = true }
        ),
        SettingRowEntry(
            icon = Icons.Default.Archive,
            title = "Backup & Restore",
            subtitle = "Export library JSON, progress & custom artwork links",
            keywords = "backup restore export import json csv xml",
            onClick = { showBackupRestoreSheet = true }
        )
    )

    val appearanceRows = listOf(
        SettingRowEntry(
            icon = Icons.Default.Home,
            title = "Home & Hero Layout",
            subtitle = "Choose hero source, reorder 7 shelves & toggle rows",
            keywords = "home hero carousel customize reorder shelves layout",
            accentIcon = true,
            onClick = onOpenHomeCustomizer
        ),
        SettingRowEntry(
            icon = Icons.Default.Palette,
            title = "Visual Presentation",
            subtitle = "${presentationSettings.detailsPagePresentation.displayName} \u00B7 Backdrop & blur depth",
            keywords = "theme presentation hero classic backdrop blur",
            onClick = onNavigateToVisualPresentation
        ),
        SettingRowEntry(
            icon = Icons.Default.ViewCarousel,
            title = "Details Page Layout",
            subtitle = "Reorder sections and toggle visibility on detail pages",
            keywords = "details layout sections reorder drag",
            onClick = onNavigateToDetailsPageLayout
        ),
        SettingRowEntry(
            icon = Icons.Default.ViewModule,
            title = "Poster Card Customization",
            subtitle = "Card dimensions, corners, landscape mode & sheen",
            keywords = "poster card size corner radius depth landscape",
            onClick = { showPosterCustomizationSheet = true }
        ),
        SettingRowEntry(
            icon = Icons.Default.Image,
            title = "Artwork Priority",
            subtitle = "Configure preferred cover & banner sources per vertical",
            keywords = "artwork priority cover banner provider anilist tmdb openlibrary",
            onClick = onNavigateToArtworkPriority
        ),
        SettingRowEntry(
            icon = Icons.Default.ViewCarousel,
            title = "Episode & Volume Layout",
            subtitle = "List, horizontal strip, vertical cards & volume grid",
            keywords = "episode chapter volume layout grid card list",
            onClick = { showEpisodeLayoutSheet = true }
        )
    )

    val systemRows = listOf(
        SettingRowEntry(
            icon = Icons.Default.SystemUpdate,
            title = "Check for Updates",
            subtitle = "Installed: v${BuildConfig.VERSION_NAME} (b${BuildConfig.VERSION_CODE})",
            keywords = "update version release github apk",
            accentIcon = true,
            onClick = onCheckUpdate
        ),
        SettingRowEntry(
            icon = Icons.Default.BugReport,
            title = "Diagnostics & Logs",
            subtitle = "Inspect network status, cache footprint & logs",
            keywords = "diagnostics logs debug crash cache",
            onClick = onOpenDiagnostics
        ),
        SettingRowEntry(
            icon = Icons.Default.Info,
            title = "About OmniTrack",
            subtitle = "Version ${BuildConfig.VERSION_NAME} \u00B7 Room Schema v19",
            keywords = "about schema version database library count",
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
                    row.subtitle.contains(q, ignoreCase = true) ||
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
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header
        Column(modifier = Modifier.padding(top = 14.dp)) {
            Text(
                text = "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "${allMedia.size} tracked items \u00B7 $connectedCount connected trackers",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Inline Search Settings Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceElevated)
                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
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
                        color = TextSecondary.copy(alpha = 0.7f),
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

        // Grouped Sections
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
                SettingsCardGroup(
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

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = DarkSurface,
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
                        text = "Room Database Schema v18",
                        fontSize = 13.sp,
                        color = AccentGold,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tracking ${allMedia.size} titles across 9 media categories.",
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
private fun SettingsCardGroup(
    title: String,
    rows: List<SettingRowEntry>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.1.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
        ) {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = row.onClick)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = row.icon,
                        contentDescription = null,
                        tint = if (row.accentIcon) AccentGold else TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = row.title,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = row.subtitle,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

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
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (index < rows.lastIndex) {
                    HorizontalDivider(
                        color = GlassBorder,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 46.dp)
                    )
                }
            }
        }
    }
}
