package com.omnitrack.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.sync.BookCsvImporter
import com.omnitrack.app.data.sync.BookImportSummary
import com.omnitrack.app.data.sync.PkceUtil
import com.omnitrack.app.data.sync.TrackerAccountVerifier
import com.omnitrack.app.data.sync.TrackerImportResult
import com.omnitrack.app.data.sync.TrackerTokens
import com.omnitrack.app.data.sync.VerificationState
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TrackerServiceId(
    val code: String,
    val title: String,
    val subtitle: String,
    val categoryGroup: String
) {
    ANILIST("AL", "AniList", "Anime, Manga & Light Novels", "ANIME & MANGA"),
    MAL("MAL", "MyAnimeList", "Anime & Manga status list", "ANIME & MANGA"),
    BANGUMI("BGM", "Bangumi", "Anime, Manga & Games collection", "ANIME & MANGA"),
    COMICK("CM", "Comick", "Manga & Webtoon reading bridge", "ANIME & MANGA"),
    TRAKT("TR", "Trakt.tv", "Movies & Shows watched history & watchlist", "MOVIES & TV"),
    SIMKL("SK", "SIMKL", "Unified TV, Movies & Anime tracker", "MOVIES & TV"),
    TMDB("TM", "TMDb", "Movie & TV metadata & ratings API key", "MOVIES & TV"),
    STEAM("ST", "Steam", "PC games library & playtime", "GAMES & VISUAL NOVELS"),
    VNDB("VN", "VNDB", "Visual Novels tracking & votes", "GAMES & VISUAL NOVELS"),
    HARDCOVER("HC", "Hardcover", "Bookshelf, ratings & reading moods", "BOOKS")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerAccountsSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniTrackApplication
    val tokenManager = app.trackerTokenManager
    val syncEngine = app.trackerSyncEngine
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val tokens by tokenManager.tokens.collectAsState(initial = TrackerTokens())
    val customTraktClientId by app.preferenceManager.customTraktClientId.collectAsState(initial = "")
    val customTraktClientSecret by app.preferenceManager.customTraktClientSecret.collectAsState(initial = "")
    val customMalClientId by app.preferenceManager.customMalClientId.collectAsState(initial = "")

    var selectedService by remember { mutableStateOf<TrackerServiceId?>(null) }
    var isSyncingAll by remember { mutableStateOf(false) }
    var syncStatusNote by remember { mutableStateOf<String?>(null) }
    var showApiKeyConfigSheet by remember { mutableStateOf(false) }
    var showBackupRestoreSheet by remember { mutableStateOf(false) }

    // SIMKL PIN Code State
    var showSimklPinDialog by remember { mutableStateOf(false) }
    var simklPinInfo by remember { mutableStateOf<TrackerAccountVerifier.SimklPinCode?>(null) }
    var isPollingSimkl by remember { mutableStateOf(false) }

    // Trakt Device Code State
    var showTraktDeviceDialog by remember { mutableStateOf(false) }
    var traktDeviceInfo by remember { mutableStateOf<TrackerAccountVerifier.TraktDeviceCode?>(null) }
    var isPollingTrakt by remember { mutableStateOf(false) }

    // Verification username cache for connected services
    var verifiedNames by remember { mutableStateOf<Map<TrackerServiceId, VerificationState>>(emptyMap()) }

    fun refreshVerification(service: TrackerServiceId, tokenSnapshot: TrackerTokens) {
        scope.launch {
            val state = when (service) {
                TrackerServiceId.ANILIST -> tokenSnapshot.anilistToken?.takeIf { it.isNotBlank() }?.let { TrackerAccountVerifier.verifyAnilist(it) }
                TrackerServiceId.MAL -> tokenSnapshot.malToken?.takeIf { it.isNotBlank() }?.let { TrackerAccountVerifier.verifyMal(it) }
                TrackerServiceId.BANGUMI -> tokenSnapshot.bangumiToken?.takeIf { it.isNotBlank() }?.let { TrackerAccountVerifier.verifyBangumi(it) }
                TrackerServiceId.COMICK -> tokenSnapshot.comickToken?.takeIf { it.isNotBlank() }?.let { TrackerAccountVerifier.verifyComick(it) }
                TrackerServiceId.TRAKT -> tokenSnapshot.traktToken?.takeIf { it.isNotBlank() }?.let { TrackerAccountVerifier.verifyTrakt(it, customTraktClientId) }
                TrackerServiceId.SIMKL -> tokenSnapshot.simklToken?.takeIf { it.isNotBlank() }?.let { TrackerAccountVerifier.verifySimkl(it) }
                TrackerServiceId.TMDB -> tokenSnapshot.tmdbToken?.takeIf { it.isNotBlank() }?.let { TrackerAccountVerifier.verifyTmdb(it) }
                TrackerServiceId.VNDB -> tokenSnapshot.vndbToken?.takeIf { it.isNotBlank() }?.let { TrackerAccountVerifier.verifyVndb(it) }
                TrackerServiceId.HARDCOVER -> tokenSnapshot.hardcoverToken?.takeIf { it.isNotBlank() }?.let { TrackerAccountVerifier.verifyHardcover(it) }
                TrackerServiceId.STEAM -> if (!tokenSnapshot.steamApiKey.isNullOrBlank() && !tokenSnapshot.steamId64.isNullOrBlank()) {
                    TrackerAccountVerifier.verifySteam(tokenSnapshot.steamId64, tokenSnapshot.steamApiKey)
                } else null
            } ?: VerificationState.Idle
            verifiedNames = verifiedNames + (service to state)
        }
    }

    LaunchedEffect(tokens) {
        TrackerServiceId.entries.forEach { svc ->
            if (isServiceConnected(svc, tokens)) {
                val existing = verifiedNames[svc]
                if (existing !is VerificationState.Verified) {
                    refreshVerification(svc, tokens)
                }
            } else {
                verifiedNames = verifiedNames - svc
            }
        }
    }

    // Local Importers (CSV + XML)
    var isImportingLocal by remember { mutableStateOf(false) }

    val goodreadsCsvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !isImportingLocal) {
            scope.launch {
                isImportingLocal = true
                val summary = withContext(Dispatchers.IO) {
                    try {
                        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }.orEmpty()
                        BookCsvImporter.importCsv(text, app.repository)
                    } catch (e: Exception) {
                        BookImportSummary(0, 0, e.message ?: "CSV read failed")
                    }
                }
                isImportingLocal = false
                val msg = summary.error ?: "Books CSV: ${summary.imported} added, ${summary.updated} updated"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val letterboxdCsvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !isImportingLocal) {
            scope.launch {
                isImportingLocal = true
                val summary = withContext(Dispatchers.IO) {
                    try {
                        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }.orEmpty()
                        BookCsvImporter.importLetterboxdCsv(text, app.repository)
                    } catch (e: Exception) {
                        BookImportSummary(0, 0, e.message ?: "Letterboxd CSV failed")
                    }
                }
                isImportingLocal = false
                val msg = summary.error ?: "Letterboxd: ${summary.imported} added, ${summary.updated} updated"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val malXmlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !isImportingLocal) {
            scope.launch {
                isImportingLocal = true
                val summary = withContext(Dispatchers.IO) {
                    try {
                        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }.orEmpty()
                        BookCsvImporter.importMalXml(text, app.repository)
                    } catch (e: Exception) {
                        BookImportSummary(0, 0, e.message ?: "MAL XML read failed")
                    }
                }
                isImportingLocal = false
                val msg = summary.error ?: "MAL XML: ${summary.imported} added, ${summary.updated} updated"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 780.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val activeService = selectedService
            if (activeService == null) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tracker Accounts",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = syncStatusNote ?: "Sign in to sync libraries & pull your progress",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { showApiKeyConfigSheet = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated)
                                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "AI & Artwork Boosters",
                                tint = AccentGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Button(
                            onClick = {
                                if (!isSyncingAll) {
                                    isSyncingAll = true
                                    syncStatusNote = "Syncing connected accounts..."
                                    scope.launch {
                                        try {
                                            val count = syncEngine.pullRemoteLibraries { status ->
                                                syncStatusNote = status
                                            }
                                            Toast.makeText(context, "Sync complete ($count updated/imported)", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            syncStatusNote = "Sync error: ${e.message}"
                                            Toast.makeText(context, "Sync error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSyncingAll = false
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGold,
                                contentColor = AmoledBlack
                            ),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isSyncingAll
                        ) {
                            if (isSyncingAll) {
                                CircularProgressIndicator(
                                    color = AmoledBlack,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = AmoledBlack,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Sync All",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Grouped Service List
                val groups = listOf("ANIME & MANGA", "MOVIES & TV", "GAMES & VISUAL NOVELS", "BOOKS")
                groups.forEach { groupName ->
                    val services = TrackerServiceId.entries.filter { it.categoryGroup == groupName }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = groupName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = TextSecondary
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(DarkSurfaceElevated)
                                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                        ) {
                            services.forEachIndexed { index, svc ->
                                val connected = isServiceConnected(svc, tokens)
                                val verState = verifiedNames[svc]
                                val statusSubtitle = when {
                                    !connected -> "Not connected"
                                    verState is VerificationState.Verified -> "Connected · ${verState.username}"
                                    verState is VerificationState.Checking -> "Verifying account..."
                                    verState is VerificationState.Failed -> "Needs attention · ${verState.message}"
                                    else -> "Connected"
                                }
                                TrackerGroupedRow(
                                    code = svc.code,
                                    title = svc.title,
                                    subtitle = statusSubtitle,
                                    isConnected = connected,
                                    onClick = { selectedService = svc }
                                )
                                if (index < services.lastIndex) {
                                    HorizontalDivider(color = GlassBorder, thickness = 0.5.dp, modifier = Modifier.padding(start = 60.dp))
                                }
                            }
                        }
                    }
                }

                // Local Backups & File Imports
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "LOCAL BACKUPS & IMPORTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = TextSecondary
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    ) {
                        TrackerGroupedRow(
                            code = "CSV",
                            title = "Goodreads & StoryGraph CSV",
                            subtitle = if (isImportingLocal) "Importing..." else "Import books, shelves, ratings & pages",
                            isConnected = false,
                            onClick = { goodreadsCsvPicker.launch(arrayOf("text/*", "text/csv", "application/csv", "*/*")) }
                        )
                        HorizontalDivider(color = GlassBorder, thickness = 0.5.dp, modifier = Modifier.padding(start = 60.dp))
                        TrackerGroupedRow(
                            code = "LBX",
                            title = "Letterboxd CSV",
                            subtitle = "Import movies & ratings from Letterboxd export",
                            isConnected = false,
                            onClick = { letterboxdCsvPicker.launch(arrayOf("text/*", "text/csv", "application/csv", "*/*")) }
                        )
                        HorizontalDivider(color = GlassBorder, thickness = 0.5.dp, modifier = Modifier.padding(start = 60.dp))
                        TrackerGroupedRow(
                            code = "XML",
                            title = "MyAnimeList XML Export",
                            subtitle = "Import exported MAL anime or manga list",
                            isConnected = false,
                            onClick = { malXmlPicker.launch(arrayOf("text/xml", "application/xml", "text/*", "*/*")) }
                        )
                        HorizontalDivider(color = GlassBorder, thickness = 0.5.dp, modifier = Modifier.padding(start = 60.dp))
                        TrackerGroupedRow(
                            code = "BAK",
                            title = "OmniTrack Backup (.omnibk)",
                            subtitle = "Full compressed library, art & settings restore",
                            isConnected = false,
                            onClick = { showBackupRestoreSheet = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            } else {
                // Per-Account Detail View
                TrackerAccountDetailView(
                    service = activeService,
                    tokens = tokens,
                    verificationState = verifiedNames[activeService] ?: VerificationState.Idle,
                    onBack = { selectedService = null },
                    onRefreshCheck = { refreshVerification(activeService, tokens) },
                    onLaunchOAuth = {
                        when (activeService) {
                            TrackerServiceId.ANILIST -> {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://anilist.co/api/v2/oauth/authorize?client_id=50688&response_type=token")
                                    )
                                )
                            }
                            TrackerServiceId.MAL -> {
                                scope.launch {
                                    val verifier = PkceUtil.generateCodeVerifier()
                                    TrackerAccountVerifier.pendingMalVerifier = verifier
                                    tokenManager.saveMalVerifier(verifier)
                                    val customMal = customMalClientId.takeIf { it.isNotBlank() && it != "2db7880d919b9231be3031ec80e77f15" }
                                    val effectiveClientId = customMal ?: TrackerAccountVerifier.MAL_CLIENT_ID
                                    val redirectUri = if (customMal != null) "omnitrack://auth/mal" else "chimahon://myanimelist-auth"
                                    val url = "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=$effectiveClientId&code_challenge=$verifier&code_challenge_method=plain&redirect_uri=$redirectUri"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                            TrackerServiceId.TRAKT -> {
                                scope.launch {
                                    val effectiveClientId = customTraktClientId.takeIf { it.isNotBlank() } ?: TrackerAccountVerifier.TRAKT_CLIENT_ID
                                    val verifier = TrackerAccountVerifier.generatePkceVerifier()
                                    val challenge = TrackerAccountVerifier.generatePkceS256Challenge(verifier)
                                    TrackerAccountVerifier.pendingTraktVerifier = verifier
                                    tokenManager.saveTraktVerifier(verifier)
                                    val authUri = Uri.parse(
                                        "https://trakt.tv/oauth/authorize?response_type=code&client_id=$effectiveClientId&redirect_uri=omnitrack://auth/trakt&code_challenge=$challenge&code_challenge_method=S256"
                                    )
                                    context.startActivity(Intent(Intent.ACTION_VIEW, authUri))
                                }
                            }
                            TrackerServiceId.SIMKL -> {
                                scope.launch {
                                    val codeInfo = TrackerAccountVerifier.requestSimklPinCode()
                                    if (codeInfo != null) {
                                        simklPinInfo = codeInfo
                                        showSimklPinDialog = true
                                    } else {
                                        Toast.makeText(context, "Failed to generate SIMKL PIN", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            TrackerServiceId.BANGUMI -> {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://bgm.tv/oauth/authorize?client_id=bgm31586666817a5d03b&response_type=code&redirect_uri=komikku://bangumi-auth")
                                    )
                                )
                            }
                            TrackerServiceId.COMICK -> {
                                scope.launch {
                                    val pkce = PkceUtil.generateS256Codes()
                                    TrackerAccountVerifier.pendingComickVerifier = pkce.codeVerifier
                                    tokenManager.saveComickVerifier(pkce.codeVerifier)
                                    val url = "https://comick.dev/api/auth/oauth2/authorize?client_id=SGrUojDvbgqfhSCQbwfCjUPIccYMJdBH&redirect_uri=app.mihon://comick-auth&response_type=code&scope=library:read%20library:write%20offline_access&resource=https://api.comick.dev/integrations/v1&code_challenge=${pkce.codeChallenge}&code_challenge_method=S256"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                            else -> {}
                        }
                    },
                    onTraktDeviceCode = {
                        scope.launch {
                            val codeInfo = TrackerAccountVerifier.requestTraktDeviceCode(customTraktClientId)
                            if (codeInfo != null) {
                                traktDeviceInfo = codeInfo
                                showTraktDeviceDialog = true
                            } else {
                                Toast.makeText(context, "Could not request Trakt TV code", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onSaveManualToken = { tokenInput ->
                        scope.launch {
                            val clean = tokenInput.trim()
                            when (activeService) {
                                TrackerServiceId.VNDB -> tokenManager.saveVndbToken(clean)
                                TrackerServiceId.TMDB -> tokenManager.saveTmdbToken(clean)
                                TrackerServiceId.HARDCOVER -> tokenManager.saveHardcoverToken(clean)
                                TrackerServiceId.ANILIST -> tokenManager.saveAnilistToken(clean)
                                TrackerServiceId.MAL -> tokenManager.saveMalToken(clean)
                                TrackerServiceId.TRAKT -> tokenManager.saveTraktToken(clean)
                                TrackerServiceId.SIMKL -> tokenManager.saveSimklToken(clean)
                                TrackerServiceId.BANGUMI -> tokenManager.saveBangumiToken(clean)
                                TrackerServiceId.COMICK -> tokenManager.saveComickToken(clean)
                                TrackerServiceId.STEAM -> {}
                            }
                            Toast.makeText(context, "${activeService.title} token saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSaveSteam = { apiKey, steamIdInput ->
                        scope.launch {
                            val resolved = TrackerAccountVerifier.resolveSteamId(steamIdInput, apiKey) ?: steamIdInput.trim()
                            tokenManager.saveSteamApiKey(apiKey.trim())
                            tokenManager.saveSteamId64(resolved)
                            Toast.makeText(context, "Steam credentials verified & saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onImportLibrary = {
                        when (activeService) {
                            TrackerServiceId.ANILIST -> syncEngine.importAniListLibrary {}
                            TrackerServiceId.MAL -> syncEngine.importMalLibrary {}
                            TrackerServiceId.TRAKT -> syncEngine.importTraktLibrary {}
                            TrackerServiceId.SIMKL -> syncEngine.importSimklLibrary {}
                            TrackerServiceId.BANGUMI -> syncEngine.importBangumiLibrary {}
                            TrackerServiceId.VNDB -> syncEngine.importVndbLibrary {}
                            TrackerServiceId.HARDCOVER -> syncEngine.importHardcoverLibraryDetailed {}
                            TrackerServiceId.STEAM -> syncEngine.importSteamLibraryDetailed {}
                            TrackerServiceId.COMICK, TrackerServiceId.TMDB -> null
                        }
                    },
                    onSyncNow = {
                        scope.launch {
                            try {
                                syncEngine.drainQueue()
                                Toast.makeText(context, "Synced queued changes", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Sync error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDisconnect = {
                        scope.launch {
                            when (activeService) {
                                TrackerServiceId.ANILIST -> tokenManager.saveAnilistToken(null)
                                TrackerServiceId.MAL -> tokenManager.saveMalToken(null)
                                TrackerServiceId.TRAKT -> tokenManager.saveTraktToken(null)
                                TrackerServiceId.SIMKL -> tokenManager.saveSimklToken(null)
                                TrackerServiceId.BANGUMI -> tokenManager.saveBangumiToken(null)
                                TrackerServiceId.COMICK -> tokenManager.saveComickToken(null)
                                TrackerServiceId.VNDB -> tokenManager.saveVndbToken(null)
                                TrackerServiceId.TMDB -> tokenManager.saveTmdbToken(null)
                                TrackerServiceId.HARDCOVER -> tokenManager.saveHardcoverToken(null)
                                TrackerServiceId.STEAM -> {
                                    tokenManager.saveSteamApiKey(null)
                                    tokenManager.saveSteamId64(null)
                                }
                            }
                            Toast.makeText(context, "${activeService.title} disconnected", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    // SIMKL PIN Code Dialog
    if (showSimklPinDialog && simklPinInfo != null) {
        val info = simklPinInfo!!
        LaunchedEffect(info) {
            isPollingSimkl = true
            while (isPollingSimkl) {
                delay(info.intervalSeconds * 1000L)
                val token = TrackerAccountVerifier.pollSimklPinToken(info.userCode, info.deviceCode)
                if (!token.isNullOrBlank()) {
                    tokenManager.saveSimklToken(token)
                    Toast.makeText(context, "SIMKL connected", Toast.LENGTH_LONG).show()
                    isPollingSimkl = false
                    showSimklPinDialog = false
                    break
                }
            }
        }

        PinAuthorizeDialog(
            title = "Authorize on SIMKL",
            instruction = "Enter this code at simkl.com/pin:",
            codeText = info.userCode,
            verifyUrl = info.verificationUrl,
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("SIMKL PIN", info.userCode))
                Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
            },
            onOpenUrl = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.verificationUrl))) },
            onDismiss = {
                isPollingSimkl = false
                showSimklPinDialog = false
            }
        )
    }

    // Trakt Device Code Dialog
    if (showTraktDeviceDialog && traktDeviceInfo != null) {
        val info = traktDeviceInfo!!
        LaunchedEffect(info) {
            isPollingTrakt = true
            while (isPollingTrakt) {
                delay(info.intervalSeconds * 1000L)
                val token = TrackerAccountVerifier.pollTraktDeviceToken(
                    deviceCode = info.deviceCode,
                    customClientId = customTraktClientId,
                    customClientSecret = customTraktClientSecret
                )
                if (!token.isNullOrBlank()) {
                    tokenManager.saveTraktToken(token)
                    Toast.makeText(context, "Trakt.tv connected", Toast.LENGTH_LONG).show()
                    isPollingTrakt = false
                    showTraktDeviceDialog = false
                    break
                }
            }
        }

        PinAuthorizeDialog(
            title = "Authorize on Trakt.tv",
            instruction = "Enter this 8-character code at trakt.tv/activate:",
            codeText = info.userCode,
            verifyUrl = info.verificationUrl,
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Trakt Code", info.userCode))
                Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
            },
            onOpenUrl = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.verificationUrl))) },
            onDismiss = {
                isPollingTrakt = false
                showTraktDeviceDialog = false
            }
        )
    }

    if (showApiKeyConfigSheet) {
        ApiKeyConfigSheet(
            preferenceManager = app.preferenceManager,
            onDismiss = { showApiKeyConfigSheet = false }
        )
    }

    if (showBackupRestoreSheet) {
        BackupRestoreSheet(
            onDismiss = { showBackupRestoreSheet = false }
        )
    }
}

private fun isServiceConnected(service: TrackerServiceId, tokens: TrackerTokens): Boolean = when (service) {
    TrackerServiceId.ANILIST -> !tokens.anilistToken.isNullOrBlank()
    TrackerServiceId.MAL -> !tokens.malToken.isNullOrBlank()
    TrackerServiceId.BANGUMI -> !tokens.bangumiToken.isNullOrBlank()
    TrackerServiceId.COMICK -> !tokens.comickToken.isNullOrBlank()
    TrackerServiceId.TRAKT -> !tokens.traktToken.isNullOrBlank()
    TrackerServiceId.SIMKL -> !tokens.simklToken.isNullOrBlank()
    TrackerServiceId.TMDB -> !tokens.tmdbToken.isNullOrBlank()
    TrackerServiceId.STEAM -> !tokens.steamApiKey.isNullOrBlank() && !tokens.steamId64.isNullOrBlank()
    TrackerServiceId.VNDB -> !tokens.vndbToken.isNullOrBlank()
    TrackerServiceId.HARDCOVER -> !tokens.hardcoverToken.isNullOrBlank()
}

@Composable
private fun TrackerGroupedRow(
    code: String,
    title: String,
    subtitle: String,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceCard)
                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = code,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isConnected) AccentGold else TextSecondary
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isConnected) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(AccentGold)
                    )
                }
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun TrackerAccountDetailView(
    service: TrackerServiceId,
    tokens: TrackerTokens,
    verificationState: VerificationState,
    onBack: () -> Unit,
    onRefreshCheck: () -> Unit,
    onLaunchOAuth: () -> Unit,
    onTraktDeviceCode: () -> Unit,
    onSaveManualToken: (String) -> Unit,
    onSaveSteam: (String, String) -> Unit,
    onImportLibrary: suspend () -> TrackerImportResult?,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connected = isServiceConnected(service, tokens)

    var isImporting by remember(service) { mutableStateOf(false) }
    var lastImportResult by remember(service) { mutableStateOf<TrackerImportResult?>(null) }

    var manualTokenInput by remember(service) { mutableStateOf("") }
    var showTokenText by remember { mutableStateOf(false) }

    var steamKeyInput by remember(service, tokens.steamApiKey) { mutableStateOf(tokens.steamApiKey.orEmpty()) }
    var steamIdInput by remember(service, tokens.steamId64) { mutableStateOf(tokens.steamId64.orEmpty()) }
    var showSteamKey by remember { mutableStateOf(false) }

    // Back Header
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = service.title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = service.subtitle,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
        if (connected) {
            IconButton(onClick = onRefreshCheck, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Re-check connection",
                    tint = AccentGold,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    // Connected Summary Panel
    if (connected) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurfaceElevated)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AccentGold)
                )
                val statusLabel = when (verificationState) {
                    is VerificationState.Verified -> "Signed in as ${verificationState.username}"
                    is VerificationState.Checking -> "Verifying account..."
                    is VerificationState.Failed -> "Verification warning: ${verificationState.message}"
                    VerificationState.Idle -> "Connected"
                }
                Text(
                    text = statusLabel,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            if (service == TrackerServiceId.TMDB || service == TrackerServiceId.COMICK) {
                Text(
                    text = if (service == TrackerServiceId.TMDB) {
                        "Used automatically for Movie & Show metadata, ratings and screen adaptations."
                    } else {
                        "Connected for chapter progress syncing while reading Manga."
                    },
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            } else {
                Button(
                    onClick = {
                        if (!isImporting) {
                            scope.launch {
                                isImporting = true
                                lastImportResult = onImportLibrary()
                                isImporting = false
                            }
                        }
                    },
                    enabled = !isImporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGold,
                        contentColor = AmoledBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            color = AmoledBlack,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importing ${service.title} Library...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Full Library", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            lastImportResult?.let { res ->
                val summaryMsg = res.error
                    ?: "Imported ${res.imported} new · Updated ${res.updated} · Up to date ${res.skipped}"
                Text(
                    text = summaryMsg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (res.error != null) Color(0xFFFCA5A5) else AccentGold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSyncNow,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sync Now", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sign Out", color = Color(0xFFFCA5A5), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Connect / Credentials Form
    if (service == TrackerServiceId.STEAM) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurfaceElevated)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (connected) "Update Steam Credentials" else "Connect Steam",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Make sure Steam Profile & Game Details are public so owned games and playtime can import.",
                fontSize = 12.sp,
                color = TextSecondary
            )

            DetailTextInputRow(
                label = "Steam Web API Key",
                value = steamKeyInput,
                onValueChange = { steamKeyInput = it },
                placeholder = "32-character key from steamcommunity.com/dev/apikey",
                isMasked = !showSteamKey,
                onToggleMask = { showSteamKey = !showSteamKey }
            )

            DetailTextInputRow(
                label = "SteamID64 or Profile URL",
                value = steamIdInput,
                onValueChange = { steamIdInput = it },
                placeholder = "76561198... or custom profile name",
                isMasked = false,
                onToggleMask = null
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://steamcommunity.com/dev/apikey")))
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Get API Key", color = TextPrimary, fontSize = 12.sp)
                }
                Button(
                    onClick = { onSaveSteam(steamKeyInput, steamIdInput) },
                    enabled = steamKeyInput.isNotBlank() && steamIdInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = AmoledBlack),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save & Verify", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else if (!connected) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurfaceElevated)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val supportsBrowserSignIn = service in listOf(
                TrackerServiceId.ANILIST,
                TrackerServiceId.MAL,
                TrackerServiceId.TRAKT,
                TrackerServiceId.SIMKL,
                TrackerServiceId.BANGUMI,
                TrackerServiceId.COMICK
            )

            if (supportsBrowserSignIn) {
                Button(
                    onClick = onLaunchOAuth,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = AmoledBlack),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text(
                        text = if (service == TrackerServiceId.SIMKL) "Connect with SIMKL PIN" else "Sign in with ${service.title}",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (service == TrackerServiceId.TRAKT) {
                    OutlinedButton(
                        onClick = onTraktDeviceCode,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use 8-Character TV Activation Code", color = TextPrimary, fontSize = 12.5.sp)
                    }
                }
            } else {
                val tokenUrl = when (service) {
                    TrackerServiceId.VNDB -> "https://vndb.org/u/tokens"
                    TrackerServiceId.TMDB -> "https://www.themoviedb.org/settings/api"
                    TrackerServiceId.HARDCOVER -> "https://hardcover.app/account/api"
                    else -> ""
                }
                DetailTextInputRow(
                    label = "${service.title} Personal Token",
                    value = manualTokenInput,
                    onValueChange = { manualTokenInput = it },
                    placeholder = "Paste personal API token",
                    isMasked = !showTokenText,
                    onToggleMask = { showTokenText = !showTokenText }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (tokenUrl.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tokenUrl))) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, GlassBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Create Token", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = { onSaveManualToken(manualTokenInput) },
                        enabled = manualTokenInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = AmoledBlack),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save & Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
}

@Composable
private fun DetailTextInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isMasked: Boolean,
    onToggleMask: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceCard)
                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = if (isMasked) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(AccentGold),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, color = TextSecondary.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    inner()
                }
            )
            if (onToggleMask != null) {
                IconButton(onClick = onToggleMask, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (isMasked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PinAuthorizeDialog(
    title: String,
    instruction: String,
    codeText: String,
    verifyUrl: String,
    onCopy: () -> Unit,
    onOpenUrl: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurfaceElevated)
                .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "$instruction ($verifyUrl)",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AmoledBlack)
                    .border(1.dp, AccentGold.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = codeText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentGold,
                    letterSpacing = 4.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy", color = TextPrimary, fontSize = 12.sp)
                }
                Button(
                    onClick = onOpenUrl,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = AmoledBlack),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Link", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
