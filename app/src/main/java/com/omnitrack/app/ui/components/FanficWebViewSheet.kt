package com.omnitrack.app.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.fanfic.FanficFetchResult
import com.omnitrack.app.data.fanfic.FanficParsedWork
import com.omnitrack.app.data.fanfic.FanficSiteEngine
import com.omnitrack.app.data.model.FanficSite
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Interactive full-screen in-app WebView dialog for fanfiction sites.
 *
 * Used strictly as a user-operated browser for:
 *  - Signing into AO3, SpaceBattles, Sufficient Velocity, Questionable Questing, or FF.net
 *    and storing resulting session cookies inside Keystore-encrypted TrackerTokenManager.
 *  - Completing user-visible site checks (e.g., FF.net / forum Cloudflare checks) and
 *    tapping "Import Page" to parse the visible DOM snapshot.
 *  - Reading chapters in-app with an instant "Mark Read" action.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FanficWebViewSheet(
    initialUrl: String,
    initialSite: FanficSite = FanficSite.fromUrl(initialUrl),
    helperBanner: String? = null,
    showImportButton: Boolean = true,
    chapterReadLabel: String? = null,
    onMarkChapterRead: (() -> Unit)? = null,
    onSessionSaved: (FanficSite, String) -> Unit = { _, _ -> },
    onPageImported: (FanficParsedWork) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as OmniTrackApplication }
    val scope = rememberCoroutineScope()

    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf(initialSite.displayName) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isParsingSnapshot by remember { mutableStateOf(false) }

    val webViewHolder = remember { mutableStateOf<WebView?>(null) }

    fun persistCookiesForCurrentUrl(toastOnEmpty: Boolean): String? {
        val cm = CookieManager.getInstance()
        cm.flush()
        val detectedSite = FanficSite.fromUrl(currentUrl).let {
            if (it == FanficSite.OTHER) initialSite else it
        }
        val urlCookie = cm.getCookie(currentUrl).orEmpty()
        val baseCookie = detectedSite.baseUrl.takeIf { it.isNotBlank() }?.let { cm.getCookie(it).orEmpty() }.orEmpty()
        val combined = listOf(urlCookie, baseCookie)
            .filter { it.isNotBlank() }
            .flatMap { it.split(";") }
            .map { it.trim() }
            .filter { it.contains("=") }
            .distinctBy { it.substringBefore("=").trim() }
            .joinToString("; ")

        if (combined.isBlank()) {
            if (toastOnEmpty) {
                Toast.makeText(context, "No cookies found yet for ${detectedSite.shortBadge}", Toast.LENGTH_SHORT).show()
            }
            return null
        }

        scope.launch {
            app.trackerTokenManager.saveFanficCookie(detectedSite, combined)
        }
        onSessionSaved(detectedSite, combined)
        return combined
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler {
            val wv = webViewHolder.value
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            } else {
                onDismiss()
            }
        }

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
            ) {
                // Top toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceCard)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val wv = webViewHolder.value
                        if (wv != null && wv.canGoBack()) wv.goBack() else onDismiss()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }

                    val activeSite = FanficSite.fromUrl(currentUrl).let {
                        if (it == FanficSite.OTHER) initialSite else it
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(activeSite.accentHex).copy(alpha = 0.85f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = activeSite.shortBadge,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pageTitle.ifBlank { activeSite.displayName },
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentUrl,
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = { webViewHolder.value?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = TextPrimary)
                    }

                    IconButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in Browser",
                            tint = TextPrimary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }

                if (isLoading && loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = AccentGold,
                        trackColor = DarkSurfaceCard
                    )
                }

                if (!helperBanner.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1F1606))
                            .border(0.5.dp, AccentGold.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = AccentGold, modifier = Modifier.size(15.dp))
                        Text(
                            text = helperBanner,
                            color = Color(0xFFFDE68A),
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Interactive Android WebView
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                    isLoading = newProgress < 100
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) pageTitle = title
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    if (!url.isNullOrBlank()) currentUrl = url
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    if (!url.isNullOrBlank()) currentUrl = url
                                    isLoading = false
                                    CookieManager.getInstance().flush()
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val uri = request?.url ?: return false
                                    val scheme = uri.scheme?.lowercase() ?: return false
                                    if (scheme == "http" || scheme == "https") return false
                                    runCatching {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }
                                    return true
                                }
                            }

                            loadUrl(initialUrl)
                            webViewHolder.value = this
                        }
                    }
                )

                // Bottom action bar: Save Session + Import Page / Mark Read
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceCard)
                        .border(0.6.dp, Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val saved = persistCookiesForCurrentUrl(toastOnEmpty = true)
                            if (saved != null) {
                                val site = FanficSite.fromUrl(currentUrl).let {
                                    if (it == FanficSite.OTHER) initialSite else it
                                }
                                Toast.makeText(
                                    context,
                                    "Saved ${site.shortBadge} encrypted session cookies",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("Save Session", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }

                    if (onMarkChapterRead != null) {
                        Button(
                            onClick = {
                                onMarkChapterRead()
                                Toast.makeText(context, "Chapter marked read", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1.1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentJade,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = chapterReadLabel ?: "Mark Read",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (showImportButton) {
                        Button(
                            enabled = !isParsingSnapshot,
                            onClick = {
                                val wv = webViewHolder.value ?: return@Button
                                isParsingSnapshot = true
                                persistCookiesForCurrentUrl(toastOnEmpty = false)
                                wv.evaluateJavascript(
                                    "(function(){ return document.documentElement ? document.documentElement.outerHTML : ''; })();"
                                ) { rawJson ->
                                    isParsingSnapshot = false
                                    val html = runCatching {
                                        if (rawJson.isNullOrBlank() || rawJson == "null") "" else Json.decodeFromString<String>(rawJson)
                                    }.getOrDefault("")

                                    if (html.isBlank()) {
                                        Toast.makeText(context, "Page still loading — try again in a second", Toast.LENGTH_SHORT).show()
                                        return@evaluateJavascript
                                    }

                                    when (val res = FanficSiteEngine.parseHtmlSnapshot(currentUrl, html)) {
                                        is FanficFetchResult.Success -> {
                                            onPageImported(res.work)
                                        }
                                        is FanficFetchResult.RequiresWebView -> {
                                            Toast.makeText(context, res.reason, Toast.LENGTH_LONG).show()
                                        }
                                        is FanficFetchResult.Error -> {
                                            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGold,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (isParsingSnapshot) "Reading..." else "Import Page",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewHolder.value?.apply {
                stopLoading()
                destroy()
            }
            webViewHolder.value = null
        }
    }
}
