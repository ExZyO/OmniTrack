package com.omnitrack.app.ui.components

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.sync.TrackerTokens
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
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
    var syncStatusText by remember { mutableStateOf<String?>(null) }

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AccentJade.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = AccentJade,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "Tracker Accounts & Live Sync",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Connect AniList, MAL, Trakt, SIMKL, Comick, VNDB & Steam",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            // Sync / Import Remote Libraries Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, if (isSyncing) AccentArctic else GlassBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Two-Way Library Sync & Import",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = syncStatusText ?: "Flushes queued changes & imports remote libraries into Room",
                            fontSize = 11.5.sp,
                            color = if (isSyncing) AccentArctic else TextMuted
                        )
                    }

                    Button(
                        onClick = {
                            if (!isSyncing) {
                                isSyncing = true
                                scope.launch {
                                    try {
                                        val count = syncEngine.pullRemoteLibraries { status ->
                                            syncStatusText = status
                                        }
                                        Toast.makeText(context, "Sync complete! $count items updated.", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        syncStatusText = "Sync failed: ${e.message}"
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentJade),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                color = AmoledBlack,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = AmoledBlack,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sync Now",
                                color = AmoledBlack,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 1. AniList
            TrackerConfigCard(
                title = "AniList",
                subtitle = "GraphQL OAuth • Anime, Manga & Japanese LNs",
                accentColor = Color(0xFF02A9FF),
                initialToken = tokens.anilistToken.orEmpty(),
                oauthUrl = "https://anilist.co/api/v2/oauth/authorize?client_id=50688&response_type=token",
                onSave = { scope.launch { tokenManager.saveAnilistToken(it); Toast.makeText(context, "AniList token saved", Toast.LENGTH_SHORT).show() } },
                onDisconnect = { scope.launch { tokenManager.saveAnilistToken(null); Toast.makeText(context, "AniList disconnected", Toast.LENGTH_SHORT).show() } }
            )

            // 2. MyAnimeList
            TrackerConfigCard(
                title = "MyAnimeList",
                subtitle = "REST API v2 • Anime & Manga status sync",
                accentColor = Color(0xFF2E51A2),
                initialToken = tokens.malToken.orEmpty(),
                oauthUrl = "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=3342466cfb744b714350df413002f5ef&code_challenge=omnitrack_challenge",
                onSave = { scope.launch { tokenManager.saveMalToken(it); Toast.makeText(context, "MAL token saved", Toast.LENGTH_SHORT).show() } },
                onDisconnect = { scope.launch { tokenManager.saveMalToken(null); Toast.makeText(context, "MAL disconnected", Toast.LENGTH_SHORT).show() } }
            )

            // 3. Trakt.tv
            TrackerConfigCard(
                title = "Trakt.tv",
                subtitle = "REST OAuth v2 • Shows & Movies watched history",
                accentColor = Color(0xFFED1C24),
                initialToken = tokens.traktToken.orEmpty(),
                oauthUrl = "https://trakt.tv/oauth/authorize?response_type=code&client_id=d6981fae6206d203ddb5bc77583647864bbffb5be03816a75f8f533a110034a7&redirect_uri=omnitrack://auth/trakt",
                onSave = { scope.launch { tokenManager.saveTraktToken(it); Toast.makeText(context, "Trakt token saved", Toast.LENGTH_SHORT).show() } },
                onDisconnect = { scope.launch { tokenManager.saveTraktToken(null); Toast.makeText(context, "Trakt disconnected", Toast.LENGTH_SHORT).show() } }
            )

            // 4. SIMKL
            TrackerConfigCard(
                title = "SIMKL",
                subtitle = "Unified API • Anime, Shows & Movies tracker",
                accentColor = Color(0xFF00E676),
                initialToken = tokens.simklToken.orEmpty(),
                oauthUrl = "https://simkl.com/oauth/authorize?response_type=code&client_id=457b98d9f43058fbeffcf31c77ce8d7b38cb7ffc76ff8b7fc93bf8fa0cb41fe2&redirect_uri=omnitrack://auth/simkl",
                onSave = { scope.launch { tokenManager.saveSimklToken(it); Toast.makeText(context, "SIMKL token saved", Toast.LENGTH_SHORT).show() } },
                onDisconnect = { scope.launch { tokenManager.saveSimklToken(null); Toast.makeText(context, "SIMKL disconnected", Toast.LENGTH_SHORT).show() } }
            )

            // 5. Comick
            TrackerConfigCard(
                title = "Comick",
                subtitle = "Manga & Webtoon chapter progress bridge",
                accentColor = Color(0xFFF43F5E),
                initialToken = tokens.comickToken.orEmpty(),
                oauthUrl = null,
                onSave = { scope.launch { tokenManager.saveComickToken(it); Toast.makeText(context, "Comick token saved", Toast.LENGTH_SHORT).show() } },
                onDisconnect = { scope.launch { tokenManager.saveComickToken(null); Toast.makeText(context, "Comick disconnected", Toast.LENGTH_SHORT).show() } }
            )

            // 6. VNDB
            TrackerConfigCard(
                title = "VNDB",
                subtitle = "Kana REST API • Visual Novels list & votes",
                accentColor = Color(0xFF3B82F6),
                initialToken = tokens.vndbToken.orEmpty(),
                oauthUrl = "https://vndb.org/u/tokens",
                onSave = { scope.launch { tokenManager.saveVndbToken(it); Toast.makeText(context, "VNDB token saved", Toast.LENGTH_SHORT).show() } },
                onDisconnect = { scope.launch { tokenManager.saveVndbToken(null); Toast.makeText(context, "VNDB disconnected", Toast.LENGTH_SHORT).show() } }
            )

            // 7. Steam
            TrackerConfigCard(
                title = "Steam",
                subtitle = "Web API • Video Games playtime sync",
                accentColor = Color(0xFF66C0F4),
                initialToken = tokens.steamApiKey.orEmpty(),
                oauthUrl = "https://steamcommunity.com/dev/apikey",
                onSave = { scope.launch { tokenManager.saveSteamApiKey(it); Toast.makeText(context, "Steam API key saved", Toast.LENGTH_SHORT).show() } },
                onDisconnect = { scope.launch { tokenManager.saveSteamApiKey(null); Toast.makeText(context, "Steam disconnected", Toast.LENGTH_SHORT).show() } }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TrackerConfigCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    initialToken: String,
    oauthUrl: String? = null,
    onSave: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    var textValue by remember(initialToken) { mutableStateOf(initialToken) }
    var isRevealed by remember { mutableStateOf(false) }
    val isConnected = initialToken.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isConnected) AccentJade.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isConnected) "CONNECTED" else "NOT CONNECTED",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isConnected) AccentJade else TextMuted
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AmoledBlack)
                .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = textValue,
                onValueChange = { textValue = it },
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                cursorBrush = SolidColor(accentColor),
                visualTransformation = if (isRevealed) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (textValue.isEmpty()) {
                        Text(
                            text = "Paste token or key...",
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (oauthUrl != null && !isConnected) {
                Button(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "1-Tap Log In", color = AmoledBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (isConnected) {
                Button(
                    onClick = {
                        textValue = ""
                        onDisconnect()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "Disconnect", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Button(
                onClick = { onSave(textValue.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Save Token", color = AmoledBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
