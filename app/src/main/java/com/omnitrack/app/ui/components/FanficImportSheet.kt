package com.omnitrack.app.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.fanfic.FanficFetchResult
import com.omnitrack.app.data.fanfic.FanficParsedWork
import com.omnitrack.app.data.fanfic.FanficSiteEngine
import com.omnitrack.app.data.model.FanficSite
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.model.extractFirstFanficUrl
import com.omnitrack.app.data.model.formatWordCount
import com.omnitrack.app.data.model.isLikelyWattpadFanfic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Full-screen sheet for:
 *  - Tab 0: Importing any Fanfiction URL (AO3, FF.net, SpaceBattles, Sufficient Velocity, QQ)
 *    with automatic FicHub / direct HTTP fetch, interactive WebView fallback, preview, and Add.
 *  - Tab 1: Managing encrypted per-site cookies (AO3, FF.net, SB, SV, QQ) + bulk-importing
 *    AO3 Bookmarks / Subscriptions and SB / SV / QQ Watched Threads.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FanficImportSheet(
    initialTab: Int = 0,
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onImported: (MediaItemEntity) -> Unit = {},
    onOpenDetail: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as OmniTrackApplication }
    val scope = rememberCoroutineScope()

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var urlInput by remember(initialUrl) { mutableStateOf(initialUrl) }
    var selectedStatus by remember { mutableStateOf(TrackingStatus.IN_PROGRESS) }
    var targetMediaType by remember { mutableStateOf(MediaType.FANFICTION) }
    var isFetching by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var parsedWork by remember { mutableStateOf<FanficParsedWork?>(null) }
    var webViewPrompt by remember { mutableStateOf<FanficFetchResult.RequiresWebView?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Interactive WebView sub-dialog state
    var webViewTarget by remember { mutableStateOf<Triple<FanficSite, String, String?>?>(null) }

    // Tab 1 state (accounts & bulk import)
    val connectedSites by app.trackerTokenManager.fanficConnectedSites.collectAsState(initial = emptySet())
    var ao3Username by remember { mutableStateOf("") }
    var wattpadUsername by remember { mutableStateOf("") }
    var bulkImportStatus by remember { mutableStateOf<String?>(null) }
    var isBulkImporting by remember { mutableStateOf(false) }

    fun defaultCategoryForWork(work: FanficParsedWork): MediaType {
        return if (work.site == FanficSite.WATTPAD && !isLikelyWattpadFanfic(work.freeformTags, work.summary, work.title)) {
            MediaType.NOVEL
        } else {
            MediaType.FANFICTION
        }
    }

    LaunchedEffect(Unit) {
        val savedUser = app.trackerTokenManager.getAo3Username()
        if (!savedUser.isNullOrBlank()) {
            ao3Username = savedUser
        }
        val savedWp = app.trackerTokenManager.getWattpadUsername()
        if (!savedWp.isNullOrBlank()) {
            wattpadUsername = savedWp
        }
    }

    fun triggerUrlFetch(targetUrl: String) {
        val trimmed = extractFirstFanficUrl(targetUrl) ?: targetUrl.trim()
        if (trimmed.isBlank()) {
            errorMessage = "Paste a fanfiction or Wattpad story URL first"
            return
        }
        urlInput = trimmed
        scope.launch {
            isFetching = true
            errorMessage = null
            webViewPrompt = null
            parsedWork = null
            when (val res = FanficSiteEngine.fetchFromUrl(trimmed) { site -> app.trackerTokenManager.getFanficCookie(site) }) {
                is FanficFetchResult.Success -> {
                    parsedWork = res.work
                    targetMediaType = defaultCategoryForWork(res.work)
                }
                is FanficFetchResult.RequiresWebView -> {
                    webViewPrompt = res
                }
                is FanficFetchResult.Error -> {
                    errorMessage = res.message
                }
            }
            isFetching = false
        }
    }

    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank() && selectedTab == 0) {
            triggerUrlFetch(initialUrl)
        }
    }

    val detectedSite = remember(urlInput) { FanficSite.fromUrl(urlInput) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(AmoledBlack),
            color = AmoledBlack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceCard)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Fanfiction & Wattpad Hub",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AO3 • FF.net • Wattpad • SpaceBattles • SV • QQ",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkSurfaceCard,
                    contentColor = AccentGold,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = AccentGold
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Import by URL", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Sites & Bulk Import", fontWeight = FontWeight.SemiBold) }
                    )
                }

                if (selectedTab == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Story or Thread URL") },
                            placeholder = { Text("https://www.wattpad.com/story/... or ao3 / ffn / sb") },
                            singleLine = true,
                            trailingIcon = {
                                Row {
                                    if (urlInput.isNotBlank()) {
                                        IconButton(onClick = {
                                            urlInput = ""
                                            parsedWork = null
                                            webViewPrompt = null
                                            errorMessage = null
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                                        }
                                    }
                                    IconButton(onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                        val extracted = extractFirstFanficUrl(clipText) ?: clipText?.trim()
                                        if (!extracted.isNullOrBlank()) {
                                            urlInput = extracted
                                            triggerUrlFetch(extracted)
                                        } else {
                                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = AccentGold)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGold,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor = AccentGold,
                                unfocusedLabelColor = TextSecondary
                            )
                        )

                        // Detected site badge + quick open in WebView button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(detectedSite.accentHex).copy(alpha = 0.85f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = detectedSite.shortBadge,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Text(
                                    text = detectedSite.displayName,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            if (urlInput.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val site = if (detectedSite == FanficSite.OTHER) FanficSite.fromUrl(urlInput) else detectedSite
                                        val target = extractFirstFanficUrl(urlInput) ?: urlInput.trim()
                                        webViewTarget = Triple(site, target, "Navigate to the story page and tap Import Page")
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("WebView", fontSize = 12.sp)
                                }
                            }
                        }

                        Button(
                            onClick = { triggerUrlFetch(urlInput) },
                            enabled = !isFetching && urlInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGold,
                                contentColor = Color.Black
                            )
                        ) {
                            if (isFetching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                                Spacer(Modifier.width(8.dp))
                                Text("Fetching Story Metadata...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Fetch Story", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2B1115))
                                    .border(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(errorMessage.orEmpty(), color = Color(0xFFFCA5A5), fontSize = 12.5.sp)
                            }
                        }

                        webViewPrompt?.let { prompt ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E170A))
                                    .border(0.8.dp, AccentGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "${prompt.site.displayName} Requires Browser Session",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = prompt.reason,
                                    color = Color(0xFFFDE68A),
                                    fontSize = 12.5.sp
                                )
                                Button(
                                    onClick = {
                                        webViewTarget = Triple(
                                            prompt.site,
                                            prompt.url,
                                            "Sign in or complete verification, then tap Import Page below"
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open in WebView & Import", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        parsedWork?.let { work ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(DarkSurfaceCard)
                                    .border(0.8.dp, AccentJade.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(86.dp)
                                            .height(126.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    ) {
                                        if (!work.coverUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = work.coverUrl,
                                                contentDescription = work.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            FanficTypographicCover(
                                                title = work.title,
                                                author = work.author,
                                                primaryFandom = work.fandoms.firstOrNull(),
                                                sourceSite = work.site.code,
                                                ratingCode = work.rating.code,
                                                wordCount = work.wordCount,
                                                isCrossover = work.isCrossover,
                                                compact = true,
                                                isBlurred = false
                                            )
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = work.title,
                                            color = TextPrimary,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "by ${work.author}",
                                            color = AccentGold,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        val mainChaps = work.chapters.size.takeIf { it > 0 } ?: work.totalChapters
                                        val statsLine = listOfNotNull(
                                            work.rating.label,
                                            work.workStatus.label,
                                            formatWordCount(work.wordCount).takeIf { it.isNotBlank() },
                                            mainChaps?.let { "$it Ch" }
                                        ).joinToString(" • ")
                                        Text(statsLine, color = TextSecondary, fontSize = 11.5.sp)
                                        if (work.fandoms.isNotEmpty()) {
                                            Text(
                                                text = work.fandoms.joinToString(", "),
                                                color = Color(0xFF93C5FD),
                                                fontSize = 11.5.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                if (!work.summary.isNullOrBlank()) {
                                    Text(
                                        text = work.summary,
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (work.site == FanficSite.WATTPAD) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Save as:",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        listOf(
                                            MediaType.FANFICTION to "Fanfiction",
                                            MediaType.NOVEL to "Web Novel"
                                        ).forEach { (mt, label) ->
                                            FilterChip(
                                                selected = targetMediaType == mt,
                                                onClick = { targetMediaType = mt },
                                                label = { Text(label, fontSize = 12.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = AccentGold.copy(alpha = 0.22f),
                                                    selectedLabelColor = AccentGold
                                                )
                                            )
                                        }
                                    }
                                }

                                // Status picker chips
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        TrackingStatus.IN_PROGRESS to "Reading",
                                        TrackingStatus.PLAN_TO_TRACK to "Plan to Read",
                                        TrackingStatus.ON_HOLD to "On Hold",
                                        TrackingStatus.COMPLETED to "Completed"
                                    ).forEach { (st, label) ->
                                        FilterChip(
                                            selected = selectedStatus == st,
                                            onClick = { selectedStatus = st },
                                            label = { Text(label, fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = AccentGold.copy(alpha = 0.22f),
                                                selectedLabelColor = AccentGold
                                            )
                                        )
                                    }
                                }

                                Button(
                                    enabled = !isSaving,
                                    onClick = {
                                        scope.launch {
                                            isSaving = true
                                            val chosenType = if (work.site == FanficSite.WATTPAD) targetMediaType else MediaType.FANFICTION
                                            val (saved, created) = app.repository.saveOrUpdateFanficWork(work, selectedStatus, chosenType)
                                            isSaving = false
                                            val destLabel = if (saved.mediaType == MediaType.NOVEL) "Web Novels" else "Fanfiction"
                                            Toast.makeText(
                                                context,
                                                if (created) "Added \"${saved.title}\" to $destLabel" else "Updated existing story \"${saved.title}\"",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onImported(saved)
                                            onOpenDetail(saved.id)
                                            onDismiss()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentJade, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isSaving) "Saving..." else "Add to Library & Open",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(36.dp))
                    }
                } else {
                    // Tab 1: Sites & Bulk Import
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "SITE SESSIONS (KEYSTORE ENCRYPTED)",
                            color = AccentGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Sign in using the built-in browser to access archive-locked AO3 fics, Questionable Questing NSFW sections, FF.net snapshots, and bulk account imports.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        listOf(
                            FanficSite.AO3,
                            FanficSite.WATTPAD,
                            FanficSite.FFN,
                            FanficSite.SB,
                            FanficSite.SV,
                            FanficSite.QQ
                        ).forEach { site ->
                            val isConnected = site.code in connectedSites
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurfaceCard)
                                    .border(
                                        0.8.dp,
                                        if (isConnected) AccentJade.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(site.accentHex))
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Text(site.shortBadge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text(site.displayName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = when {
                                                isConnected && site == FanficSite.FFN -> "Cloudflare session saved (Encrypted)"
                                                isConnected -> "Session saved (Encrypted)"
                                                site == FanficSite.FFN -> "No login needed (Public) • Optional Cloudflare session"
                                                site == FanficSite.WATTPAD -> "Optional login for Private Library & logged-in reading"
                                                else -> "Not signed in"
                                            },
                                            color = if (isConnected) AccentJade else TextSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (isConnected) {
                                        IconButton(onClick = {
                                            scope.launch {
                                                app.trackerTokenManager.saveFanficCookie(site, null)
                                                Toast.makeText(context, "Cleared ${site.shortBadge} session", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Disconnect", tint = TextSecondary)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val helper = if (site == FanficSite.FFN) {
                                                "No FF.net account login is needed! Once the page loads past Cloudflare, tap 'Save Session' below (FF.net's own login/signup page has a server-side 404 bug)."
                                            } else {
                                                "Log into ${site.displayName}, then tap Save Session at bottom left"
                                            }
                                            webViewTarget = Triple(
                                                site,
                                                site.loginUrl.ifBlank { site.baseUrl },
                                                helper
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isConnected -> "Refresh"
                                                site == FanficSite.FFN -> "Verify CF"
                                                else -> "Sign In"
                                            },
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "BULK IMPORT FROM ACCOUNTS",
                            color = AccentGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        // AO3 Bookmarks & Subscriptions import card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceCard)
                                .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("AO3 Bookmarks & Subscriptions", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = ao3Username,
                                onValueChange = { ao3Username = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("AO3 Username") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentGold,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("bookmarks" to "Import Bookmarks", "subscriptions" to "Import Subscriptions").forEach { (mode, label) ->
                                    Button(
                                        enabled = !isBulkImporting && FanficSite.AO3.code in connectedSites && ao3Username.isNotBlank(),
                                        onClick = {
                                            scope.launch {
                                                isBulkImporting = true
                                                bulkImportStatus = "Fetching AO3 $mode for $ao3Username..."
                                                app.trackerTokenManager.saveAo3Username(ao3Username)
                                                val cookie = app.trackerTokenManager.getFanficCookie(FanficSite.AO3).orEmpty()
                                                val res = FanficSiteEngine.fetchAo3AccountWorks(ao3Username, mode, cookie, maxPages = 3)
                                                res.onSuccess { works ->
                                                    var added = 0
                                                    var updated = 0
                                                    works.forEach { w ->
                                                        val (_, created) = app.repository.saveOrUpdateFanficWork(w, TrackingStatus.PLAN_TO_TRACK)
                                                        if (created) added++ else updated++
                                                    }
                                                    bulkImportStatus = "AO3 $mode: imported $added new, updated $updated existing works."
                                                }.onFailure { err ->
                                                    bulkImportStatus = err.message ?: "Failed to fetch AO3 $mode"
                                                }
                                                isBulkImporting = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = AccentGold)
                                    ) {
                                        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        // Wattpad Reading Lists (public) & Private Library import card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceCard)
                                .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Wattpad Reading Lists & Library", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Public reading lists only need a username. Private Library uses your signed-in Wattpad session above.",
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                            OutlinedTextField(
                                value = wattpadUsername,
                                onValueChange = { wattpadUsername = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Wattpad Username") },
                                placeholder = { Text("username (without @)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentGold,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    enabled = !isBulkImporting && wattpadUsername.isNotBlank(),
                                    onClick = {
                                        scope.launch {
                                            isBulkImporting = true
                                            val cleanU = wattpadUsername.trim().removePrefix("@")
                                            bulkImportStatus = "Fetching public Wattpad reading lists for @$cleanU..."
                                            app.trackerTokenManager.saveWattpadUsername(cleanU)
                                            val cookie = app.trackerTokenManager.getFanficCookie(FanficSite.WATTPAD)
                                            val res = FanficSiteEngine.fetchWattpadUserReadingLists(
                                                username = cleanU,
                                                cookie = cookie,
                                                onProgress = { status ->
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                                                        bulkImportStatus = status
                                                    }
                                                }
                                            )
                                            res.onSuccess { works ->
                                                var added = 0
                                                var updated = 0
                                                var novelCount = 0
                                                var fanficCount = 0
                                                works.forEachIndexed { idx, w ->
                                                    val destType = defaultCategoryForWork(w)
                                                    if (destType == MediaType.NOVEL) novelCount++ else fanficCount++
                                                    val (_, created) = app.repository.saveOrUpdateFanficWork(w, TrackingStatus.PLAN_TO_TRACK, destType)
                                                    if (created) added++ else updated++
                                                    if ((idx + 1) % 10 == 0 || idx == 0 || idx + 1 == works.size) {
                                                        bulkImportStatus = "Saving Wattpad Reading Lists (${idx + 1}/${works.size})..."
                                                    }
                                                }
                                                val summary = "Wattpad Reading Lists: ${works.size} fetched ($added new, $updated updated — $novelCount Web Novels, $fanficCount Fanfiction)."
                                                bulkImportStatus = summary
                                                com.omnitrack.app.util.AppLogger.i("FanficImport", summary)
                                            }.onFailure { err ->
                                                val errMsg = err.message ?: "Failed to fetch Wattpad reading lists"
                                                bulkImportStatus = errMsg
                                                com.omnitrack.app.util.AppLogger.w("FanficImport", errMsg)
                                            }
                                            isBulkImporting = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = AccentGold)
                                ) {
                                    Text("Public Lists", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                Button(
                                    enabled = !isBulkImporting && FanficSite.WATTPAD.code in connectedSites,
                                    onClick = {
                                        scope.launch {
                                            isBulkImporting = true
                                            val cleanU = wattpadUsername.trim().removePrefix("@")
                                            if (cleanU.isNotBlank()) app.trackerTokenManager.saveWattpadUsername(cleanU)
                                            bulkImportStatus = "Fetching Wattpad Private Library..."
                                            val cookie = app.trackerTokenManager.getFanficCookie(FanficSite.WATTPAD).orEmpty()
                                            val res = FanficSiteEngine.fetchWattpadPrivateLibrary(
                                                username = cleanU,
                                                cookie = cookie,
                                                onProgress = { status ->
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                                                        bulkImportStatus = status
                                                    }
                                                }
                                            )
                                            res.onSuccess { works ->
                                                var added = 0
                                                var updated = 0
                                                var novelCount = 0
                                                var fanficCount = 0
                                                works.forEachIndexed { idx, w ->
                                                    val destType = defaultCategoryForWork(w)
                                                    if (destType == MediaType.NOVEL) novelCount++ else fanficCount++
                                                    val (_, created) = app.repository.saveOrUpdateFanficWork(w, TrackingStatus.IN_PROGRESS, destType)
                                                    if (created) added++ else updated++
                                                    if ((idx + 1) % 10 == 0 || idx == 0 || idx + 1 == works.size) {
                                                        bulkImportStatus = "Saving Wattpad Private Library (${idx + 1}/${works.size})..."
                                                    }
                                                }
                                                val summary = "Wattpad Private Library: ${works.size} fetched ($added new, $updated updated — $novelCount Web Novels, $fanficCount Fanfiction)."
                                                bulkImportStatus = summary
                                                com.omnitrack.app.util.AppLogger.i("FanficImport", summary)
                                            }.onFailure { err ->
                                                val errMsg = err.message ?: "Failed to fetch Wattpad Private Library"
                                                bulkImportStatus = errMsg
                                                com.omnitrack.app.util.AppLogger.w("FanficImport", errMsg)
                                            }
                                            isBulkImporting = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(FanficSite.WATTPAD.accentHex), contentColor = Color.White)
                                ) {
                                    Text("Private Library", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        // Forum Watched Threads import
                        val connectedForums = listOf(FanficSite.SB, FanficSite.SV, FanficSite.QQ)
                            .filter { it.code in connectedSites }
                        if (connectedForums.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurfaceCard)
                                    .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Forum Watched Threads", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    connectedForums.forEach { forum ->
                                        Button(
                                            enabled = !isBulkImporting,
                                            onClick = {
                                                scope.launch {
                                                    isBulkImporting = true
                                                    bulkImportStatus = "Scanning ${forum.displayName} watched threads..."
                                                    val cookie = app.trackerTokenManager.getFanficCookie(forum).orEmpty()
                                                    val listRes = FanficSiteEngine.fetchXenForoWatchedThreads(forum, cookie)
                                                    listRes.onSuccess { links ->
                                                        var added = 0
                                                        var updated = 0
                                                        var skipped = 0
                                                        for ((idx, pair) in links.take(30).withIndex()) {
                                                            bulkImportStatus = "Importing ${forum.shortBadge} (${idx + 1}/${links.take(30).size}): ${pair.second}"
                                                            when (val fetched = FanficSiteEngine.fetchFromUrl(pair.first) { app.trackerTokenManager.getFanficCookie(it) }) {
                                                                is FanficFetchResult.Success -> {
                                                                    val (_, created) = app.repository.saveOrUpdateFanficWork(fetched.work, TrackingStatus.IN_PROGRESS)
                                                                    if (created) added++ else updated++
                                                                }
                                                                else -> skipped++
                                                            }
                                                        }
                                                        bulkImportStatus = "${forum.shortBadge}: imported $added new, updated $updated, skipped $skipped."
                                                    }.onFailure { err ->
                                                        bulkImportStatus = err.message ?: "Failed to fetch watched threads"
                                                    }
                                                    isBulkImporting = false
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(forum.accentHex), contentColor = Color.White)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text("Import ${forum.shortBadge} Watched Threads", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        if (!bulkImportStatus.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceElevated)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isBulkImporting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentGold)
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = AccentJade, modifier = Modifier.size(16.dp))
                                }
                                Text(bulkImportStatus.orEmpty(), color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(36.dp))
                    }
                }
            }
        }
    }

    webViewTarget?.let { (site, url, banner) ->
        FanficWebViewSheet(
            initialUrl = url,
            initialSite = site,
            helperBanner = banner,
            showImportButton = (selectedTab == 0),
            onSessionSaved = { _, _ -> },
            onPageImported = { work ->
                parsedWork = work
                targetMediaType = defaultCategoryForWork(work)
                webViewPrompt = null
                errorMessage = null
                selectedTab = 0
                webViewTarget = null
            },
            onDismiss = { webViewTarget = null }
        )
    }
}
