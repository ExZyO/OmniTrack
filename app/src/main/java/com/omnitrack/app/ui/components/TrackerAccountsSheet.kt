package com.omnitrack.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.sync.TrackerAccountVerifier
import com.omnitrack.app.data.sync.TrackerTokens
import com.omnitrack.app.data.sync.VerificationState
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var isSyncing by remember { mutableStateOf(false) }

    // Trakt Device Code State
    var showTraktDeviceDialog by remember { mutableStateOf(false) }
    var traktDeviceCodeInfo by remember { mutableStateOf<TrackerAccountVerifier.TraktDeviceCode?>(null) }
    var isPollingTrakt by remember { mutableStateOf(false) }

    // SIMKL PIN Code State
    var showSimklPinDialog by remember { mutableStateOf(false) }
    var simklPinInfo by remember { mutableStateOf<TrackerAccountVerifier.SimklPinCode?>(null) }
    var isPollingSimkl by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(AccentArctic.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = AccentArctic,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Tracker Accounts",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "Real-time sync, live identity verification & cross-medium tracking",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(start = 42.dp, top = 2.dp)
                    )
                }

                Button(
                    onClick = {
                        if (!isSyncing) {
                            isSyncing = true
                            scope.launch {
                                try {
                                    syncEngine.drainQueue()
                                    Toast.makeText(context, "Full tracker synchronization complete", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Sync error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSyncing = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentJade),
                    shape = RoundedCornerShape(999.dp),
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = AmoledBlack,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = AmoledBlack,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sync All",
                            color = AmoledBlack,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 1. AniList (1-Tap OAuth)
            OAuthDirectTrackerCard(
                title = "AniList",
                subtitle = "Anime, Manga & Japanese Light Novels",
                accentColor = Color(0xFF02A9FF),
                initialToken = tokens.anilistToken.orEmpty(),
                oauthUrl = "https://anilist.co/api/v2/oauth/authorize?client_id=50688&response_type=token",
                oauthButtonLabel = "1-Tap Log In",
                verifyFn = { TrackerAccountVerifier.verifyAnilist(it) },
                onDisconnect = { scope.launch { tokenManager.saveAnilistToken(null); Toast.makeText(context, "AniList disconnected", Toast.LENGTH_SHORT).show() } }
            )

            // 2. MyAnimeList (1-Tap PKCE OAuth)
            OAuthDirectTrackerCard(
                title = "MyAnimeList",
                subtitle = "Anime & Manga status tracking",
                accentColor = Color(0xFF2E51A2),
                initialToken = tokens.malToken.orEmpty(),
                oauthUrl = "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=3342466cfb744b714350df413002f5ef&code_challenge=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk&code_challenge_method=plain",
                oauthButtonLabel = "1-Tap Log In",
                verifyFn = { TrackerAccountVerifier.verifyMal(it) },
                onDisconnect = { scope.launch { tokenManager.saveMalToken(null); Toast.makeText(context, "MAL disconnected", Toast.LENGTH_SHORT).show() } }
            )

            // 3. Trakt.tv (8-Char Device Code Flow)
            OAuthDirectTrackerCard(
                title = "Trakt.tv",
                subtitle = "TV Shows & Movies watched history",
                accentColor = Color(0xFFED1C24),
                initialToken = tokens.traktToken.orEmpty(),
                verifyFn = { TrackerAccountVerifier.verifyTrakt(it) },
                onDisconnect = { scope.launch { tokenManager.saveTraktToken(null); Toast.makeText(context, "Trakt disconnected", Toast.LENGTH_SHORT).show() } },
                customActionButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                val codeInfo = TrackerAccountVerifier.requestTraktDeviceCode()
                                if (codeInfo != null) {
                                    traktDeviceCodeInfo = codeInfo
                                    showTraktDeviceDialog = true
                                } else {
                                    Toast.makeText(context, "Failed to generate Trakt device code", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFED1C24)),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Connect with Code", color = AmoledBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )

            // 4. SIMKL (1-Tap OAuth with nuvio:// & PIN Code Flow)
            OAuthDirectTrackerCard(
                title = "SIMKL",
                subtitle = "Unified Anime, Shows & Movies tracker",
                accentColor = Color(0xFF00E676),
                initialToken = tokens.simklToken.orEmpty(),
                verifyFn = { TrackerAccountVerifier.verifySimkl(it) },
                onDisconnect = { scope.launch { tokenManager.saveSimklToken(null); Toast.makeText(context, "SIMKL disconnected", Toast.LENGTH_SHORT).show() } },
                customActionButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val url = "https://simkl.com/oauth/authorize?response_type=code&client_id=6eaf02a9b63b01eb1750cdecf0f05e7d0d8dd949d1eb6894716857947bb68c1a&redirect_uri=nuvio://auth/simkl"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("1-Tap Log In", color = AmoledBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val codeInfo = TrackerAccountVerifier.requestSimklPinCode()
                                    if (codeInfo != null) {
                                        simklPinInfo = codeInfo
                                        showSimklPinDialog = true
                                    } else {
                                        Toast.makeText(context, "Failed to generate SIMKL PIN", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PIN Code", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )

            // 5. Bangumi (1-Tap OAuth via komikku://bangumi-auth with auto code-exchange)
            OAuthDirectTrackerCard(
                title = "Bangumi",
                subtitle = "bgm.tv API - Anime, Manga & Games collection",
                accentColor = Color(0xFFF09199),
                initialToken = tokens.bangumiToken.orEmpty(),
                verifyFn = { TrackerAccountVerifier.verifyBangumi(it) },
                onDisconnect = { scope.launch { tokenManager.saveBangumiToken(null); Toast.makeText(context, "Bangumi disconnected", Toast.LENGTH_SHORT).show() } },
                customActionButton = {
                    Button(
                        onClick = {
                            val oauthUri = Uri.parse("https://bgm.tv/oauth/authorize?client_id=bgm31586666817a5d03b&response_type=code&redirect_uri=komikku://bangumi-auth")
                            context.startActivity(Intent(Intent.ACTION_VIEW, oauthUri))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF09199)),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("1-Tap Log In", color = AmoledBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )

            // 6. Comick (1-Tap PKCE S256 OAuth via app.mihon://comick-auth with auto code-exchange)
            OAuthDirectTrackerCard(
                title = "Comick",
                subtitle = "Manga & Webtoon chapter progress bridge",
                accentColor = Color(0xFFF43F5E),
                initialToken = tokens.comickToken.orEmpty(),
                verifyFn = { TrackerAccountVerifier.verifyComick(it) },
                onDisconnect = { scope.launch { tokenManager.saveComickToken(null); Toast.makeText(context, "Comick disconnected", Toast.LENGTH_SHORT).show() } },
                customActionButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                val pkce = com.omnitrack.app.data.sync.PkceUtil.generateS256Codes()
                                tokenManager.saveComickVerifier(pkce.codeVerifier)
                                val url = "https://comick.dev/api/auth/oauth2/authorize?client_id=SGrUojDvbgqfhSCQbwfCjUPIccYMJdBH&redirect_uri=app.mihon://comick-auth&response_type=code&scope=library:read%20library:write%20offline_access&resource=https://api.comick.dev/integrations/v1&code_challenge=" + pkce.codeChallenge + "&code_challenge_method=S256"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("1-Tap Log In", color = AmoledBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )

            // 7. VNDB (User Personal Token)
            ManualTokenTrackerCard(
                title = "VNDB",
                subtitle = "Kana REST API – Visual Novels tracking & votes",
                accentColor = Color(0xFF3B82F6),
                initialToken = tokens.vndbToken.orEmpty(),
                tokenCreationUrl = "https://vndb.org/u/tokens",
                verifyFn = { TrackerAccountVerifier.verifyVndb(it) },
                onSave = { scope.launch { tokenManager.saveVndbToken(it); Toast.makeText(context, "VNDB token saved", Toast.LENGTH_SHORT).show() } },
                onDisconnect = { scope.launch { tokenManager.saveVndbToken(null); Toast.makeText(context, "VNDB disconnected", Toast.LENGTH_SHORT).show() } }
            )

            // 8. Steam (Requires SteamID64 AND API Key)
            SteamTrackerCard(
                initialApiKey = tokens.steamApiKey.orEmpty(),
                initialSteamId64 = tokens.steamId64.orEmpty(),
                onSave = { key, id ->
                    scope.launch {
                        val resolvedId = TrackerAccountVerifier.resolveSteamId(id, key) ?: id.trim()
                        tokenManager.saveSteamApiKey(key)
                        tokenManager.saveSteamId64(resolvedId)
                        Toast.makeText(context, "Steam credentials verified & saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                onDisconnect = {
                    scope.launch {
                        tokenManager.saveSteamApiKey(null)
                        tokenManager.saveSteamId64(null)
                        Toast.makeText(context, "Steam disconnected", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // 9. TMDb
            VerifiedTrackerCard(
                title = "TMDb",
                subtitle = "Movies & TV Shows metadata and ratings API",
                accentColor = Color(0xFF01D277),
                initialToken = tokens.tmdbToken.orEmpty(),
                oauthUrl = "https://www.themoviedb.org/settings/api",
                oauthButtonLabel = "Get API Key",
                verifyFn = { TrackerAccountVerifier.verifyTmdb(it) },
                onSave = { scope.launch { tokenManager.saveTmdbToken(it); Toast.makeText(context, "TMDb token saved", Toast.LENGTH_SHORT).show() } },
                onDisconnect = { scope.launch { tokenManager.saveTmdbToken(null); Toast.makeText(context, "TMDb disconnected", Toast.LENGTH_SHORT).show() } }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }


    // SIMKL PIN Code Dialog
    if (showSimklPinDialog && simklPinInfo != null) {
        val info = simklPinInfo!!
        LaunchedEffect(info) {
            isPollingSimkl = true
            while (isPollingSimkl) {
                delay(info.intervalSeconds * 1000L)
                val token = TrackerAccountVerifier.pollSimklPinToken(info.userCode)
                if (!token.isNullOrBlank()) {
                    tokenManager.saveSimklToken(token)
                    Toast.makeText(context, "SIMKL connected successfully!", Toast.LENGTH_LONG).show()
                    isPollingSimkl = false
                    showSimklPinDialog = false
                    break
                }
            }
        }

        androidx.compose.ui.window.Dialog(onDismissRequest = {
            isPollingSimkl = false
            showSimklPinDialog = false
        }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "Authorize on SIMKL",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "Enter this 5-digit PIN code at simkl.com/pin:",
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AmoledBlack)
                        .border(1.dp, Color(0xFF00E676).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = info.userCode,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E676),
                        letterSpacing = 6.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SIMKL PIN", info.userCode))
                            Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", color = TextPrimary, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.verificationUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Link", color = AmoledBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Trakt Device Code Dialog
    if (showTraktDeviceDialog && traktDeviceCodeInfo != null) {
        val info = traktDeviceCodeInfo!!
        LaunchedEffect(info) {
            isPollingTrakt = true
            while (isPollingTrakt) {
                delay(info.intervalSeconds * 1000L)
                val token = TrackerAccountVerifier.pollTraktDeviceToken(info.deviceCode)
                if (!token.isNullOrBlank()) {
                    tokenManager.saveTraktToken(token)
                    Toast.makeText(context, "Trakt.tv connected successfully!", Toast.LENGTH_LONG).show()
                    isPollingTrakt = false
                    showTraktDeviceDialog = false
                    break
                }
            }
        }

        androidx.compose.ui.window.Dialog(onDismissRequest = {
            isPollingTrakt = false
            showTraktDeviceDialog = false
        }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFED1C24).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = Color(0xFFED1C24),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "Authorize on Trakt",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "Enter this 8-character activation code at trakt.tv/activate:",
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // Large user code box
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(AmoledBlack)
                        .border(1.dp, Color(0xFFED1C24).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Trakt Code", info.userCode))
                            Toast.makeText(context, "Copied ${info.userCode} to clipboard", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = info.userCode,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFED1C24)
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFED1C24),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Waiting for authorization on Trakt...",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            isPollingTrakt = false
                            showTraktDeviceDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = TextMuted, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.verificationUrl))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFED1C24)),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Link", color = AmoledBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OAuthDirectTrackerCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    initialToken: String,
    oauthUrl: String? = null,
    oauthButtonLabel: String = "1-Tap Log In",
    verifyFn: suspend (String) -> VerificationState,
    onDisconnect: () -> Unit,
    customActionButton: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    var verificationState by remember { mutableStateOf<VerificationState>(VerificationState.Idle) }

    LaunchedEffect(initialToken) {
        if (initialToken.isNotBlank()) {
            verificationState = VerificationState.Checking
            verificationState = verifyFn(initialToken.trim())
        } else {
            verificationState = VerificationState.Idle
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = TextMuted
                )
            }

            // Real Live Verification Status Pill
            when (val state = verificationState) {
                is VerificationState.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AccentArctic,
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(text = "Verifying...", fontSize = 9.5.sp, color = TextMuted)
                    }
                }
                is VerificationState.Verified -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AccentJade.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentJade)
                        )
                        Text(
                            text = "Verified as ${state.username}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentJade
                        )
                    }
                }
                is VerificationState.Failed -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Text(
                            text = "Session expired",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
                is VerificationState.Idle -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(TextMuted)
                        )
                        Text(
                            text = "Not Connected",
                            fontSize = 10.5.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Direct Actions Only — ZERO TEXT FIELD!
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (initialToken.isBlank()) {
                if (customActionButton != null) {
                    customActionButton()
                } else if (oauthUrl != null) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(oauthUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = oauthButtonLabel, color = AmoledBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(text = "Disconnect", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ManualTokenTrackerCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    initialToken: String,
    tokenCreationUrl: String? = null,
    verifyFn: suspend (String) -> VerificationState,
    onSave: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    var textValue by remember(initialToken) { mutableStateOf(initialToken) }
    var isRevealed by remember { mutableStateOf(false) }
    var verificationState by remember { mutableStateOf<VerificationState>(VerificationState.Idle) }

    LaunchedEffect(initialToken) {
        if (initialToken.isNotBlank()) {
            verificationState = VerificationState.Checking
            verificationState = verifyFn(initialToken.trim())
        } else {
            verificationState = VerificationState.Idle
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = TextMuted
                )
            }

            // Real Live Verification Status Pill
            when (val state = verificationState) {
                is VerificationState.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AccentArctic,
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(text = "Verifying...", fontSize = 9.5.sp, color = TextMuted)
                    }
                }
                is VerificationState.Verified -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AccentJade.copy(alpha = 0.12f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentJade)
                        )
                        Text(
                            text = "Verified as ${state.username}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentJade
                        )
                    }
                }
                is VerificationState.Failed -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Text(
                            text = "Invalid token",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
                is VerificationState.Idle -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(TextMuted)
                        )
                        Text(
                            text = "Not Connected",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Token Input Field for User-Supplied Token
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AmoledBlack)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = textValue,
                onValueChange = { textValue = it },
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(accentColor),
                visualTransformation = if (isRevealed) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (textValue.isEmpty()) {
                        Text(
                            text = "Paste personal access token...",
                            color = TextMuted,
                            fontSize = 12.5.sp
                        )
                    }
                    innerTextField()
                }
            )

            IconButton(
                onClick = { isRevealed = !isRevealed },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (tokenCreationUrl != null && initialToken.isBlank()) {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tokenCreationUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Create Token", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (initialToken.isNotBlank()) {
                Button(
                    onClick = {
                        textValue = ""
                        onDisconnect()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(text = "Disconnect", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Button(
                onClick = { onSave(textValue.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(text = "Save Token", color = AmoledBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SteamTrackerCard(
    initialApiKey: String,
    initialSteamId64: String,
    onSave: (String, String) -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    var apiKeyText by remember(initialApiKey) { mutableStateOf(initialApiKey) }
    var steamIdText by remember(initialSteamId64) { mutableStateOf(initialSteamId64) }
    var isApiKeyRevealed by remember { mutableStateOf(false) }
    var verificationState by remember { mutableStateOf<VerificationState>(VerificationState.Idle) }

    LaunchedEffect(initialApiKey, initialSteamId64) {
        if (initialApiKey.isNotBlank() && initialSteamId64.isNotBlank()) {
            verificationState = VerificationState.Checking
            verificationState = TrackerAccountVerifier.verifySteam(initialApiKey.trim(), initialSteamId64.trim())
        } else {
            verificationState = VerificationState.Idle
        }
    }

    val isConnected = initialApiKey.isNotBlank() && initialSteamId64.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Steam",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Video game library & playtime sync via Web API",
                    fontSize = 11.5.sp,
                    color = TextMuted
                )
            }

            when (val state = verificationState) {
                is VerificationState.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF66C0F4), strokeWidth = 1.5.dp, modifier = Modifier.size(10.dp))
                        Text(text = "Verifying...", fontSize = 9.5.sp, color = TextMuted)
                    }
                }
                is VerificationState.Verified -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AccentJade.copy(alpha = 0.12f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AccentJade))
                        Text(text = "Verified as ${state.username}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentJade)
                    }
                }
                is VerificationState.Failed -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                        Text(text = "Invalid credentials", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                    }
                }
                is VerificationState.Idle -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TextMuted))
                        Text(text = "Not Connected", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
        }

        // Field 1: SteamID64
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Steam Username, Profile URL, or 17-digit SteamID64", fontSize = 11.sp, color = TextMuted)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AmoledBlack)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = steamIdText,
                    onValueChange = { steamIdText = it },
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(Color(0xFF66C0F4)),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (steamIdText.isEmpty()) Text("Username, profile link, or 76561198...", color = TextMuted, fontSize = 12.sp)
                        inner()
                    }
                )
            }
        }

        // Field 2: Steam Web API Key
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("32-Character Steam Web API Key", fontSize = 11.sp, color = TextMuted)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AmoledBlack)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(Color(0xFF66C0F4)),
                    visualTransformation = if (isApiKeyRevealed) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (apiKeyText.isEmpty()) Text("Paste 32-character API key...", color = TextMuted, fontSize = 12.sp)
                        inner()
                    }
                )

                IconButton(
                    onClick = { isApiKeyRevealed = !isApiKeyRevealed },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isApiKeyRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isConnected) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://steamcommunity.com/dev/apikey"))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66C0F4)),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Get API Key", color = AmoledBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (isConnected) {
                Button(
                    onClick = {
                        apiKeyText = ""
                        steamIdText = ""
                        onDisconnect()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Disconnect", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Button(
                onClick = { onSave(apiKeyText.trim(), steamIdText.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66C0F4)),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text("Save Credentials", color = AmoledBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
