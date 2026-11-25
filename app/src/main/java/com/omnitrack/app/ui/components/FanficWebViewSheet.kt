package com.omnitrack.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Home
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
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Interactive full-screen in-app WebView dialog for fanfiction sites.
 *
 * Used strictly as a user-operated browser for:
 *  - Signing into AO3, Wattpad, SpaceBattles, Sufficient Velocity, Questionable Questing, or FF.net
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

    val safeInitialUrl = remember(initialUrl) {
        when {
            initialUrl.contains("fanfiction.net/login.php", ignoreCase = true) -> "https://www.fanfiction.net"
            initialUrl.contains("://m.fanfiction.net", ignoreCase = true) ->
                initialUrl.replace(Regex("(?i)://m\\.fanfiction\\.net"), "://www.fanfiction.net")
            else -> initialUrl
        }
    }

    var currentUrl by remember { mutableStateOf(safeInitialUrl) }
    var pageTitle by remember { mutableStateOf(initialSite.displayName) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isParsingSnapshot by remember { mutableStateOf(false) }
    var isDesktopMode by remember(initialSite, safeInitialUrl) {
        mutableStateOf(initialSite == FanficSite.FFN || safeInitialUrl.contains("fanfiction.net", ignoreCase = true))
    }
    var wattpadSignedIn by remember { mutableStateOf(false) }
    var whiteScreenRecoveryAttempts by remember { mutableIntStateOf(0) }
    var whiteScreenCheckJob by remember { mutableStateOf<Job?>(null) }

    val chromeMobileUa = remember {
        runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
            ?.replace("; wv)", ")")
            ?.replace("Version/4.0 ", "")
            ?.takeIf { it.isNotBlank() }
            ?: "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
    val chromeDesktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    // Firefox UA strings avoid Cloudflare / OAuth mismatches on FF.net
    val firefoxDesktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0"
    val firefoxMobileUa = "Mozilla/5.0 (Android 14; Mobile; rv:132.0) Gecko/132.0 Firefox/132.0"

    fun resolveActiveSite(url: String? = currentUrl): FanficSite =
        FanficSite.fromUrl(url).let { if (it == FanficSite.OTHER) initialSite else it }

    fun resolveUserAgent(site: FanficSite, desktop: Boolean): String = when (site) {
        FanficSite.FFN -> if (desktop) firefoxDesktopUa else firefoxMobileUa
        else -> if (desktop) chromeDesktopUa else chromeMobileUa
    }

    val containerHolder = remember { mutableStateOf<FrameLayout?>(null) }
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }
    val popupWebViewHolder = remember { mutableStateOf<WebView?>(null) }

    fun mergeCookieStrings(vararg rawHeaders: String?): String = rawHeaders
        .filterNotNull()
        .filter { it.isNotBlank() }
        .flatMap { it.split(";") }
        .map { it.trim() }
        .filter { it.contains("=") }
        .distinctBy { it.substringBefore("=").trim() }
        .joinToString("; ")

    fun collectWattpadCookies(): String {
        val cm = CookieManager.getInstance()
        return mergeCookieStrings(
            cm.getCookie("https://wattpad.com"),
            cm.getCookie("https://www.wattpad.com"),
            cm.getCookie("https://api.wattpad.com")
        )
    }

    fun hasWattpadAuthCookie(): Boolean {
        val merged = collectWattpadCookies()
        if (merged.isBlank()) return false
        return merged.split(";").any { part ->
            val eqIdx = part.indexOf('=')
            if (eqIdx <= 0) return@any false
            val name = part.substring(0, eqIdx).trim()
            val value = part.substring(eqIdx + 1).trim()
            val validVal = value.isNotBlank() &&
                !value.equals("deleted", ignoreCase = true) &&
                !value.equals("null", ignoreCase = true) &&
                value != "\"\""
            validVal && (name == "token" || name == "wp_id")
        }
    }

    fun persistCookiesForCurrentUrl(toastOnEmpty: Boolean): String? {
        val cm = CookieManager.getInstance()
        cm.flush()
        val detectedSite = resolveActiveSite(currentUrl)
        val urlCookie = cm.getCookie(currentUrl)
        val baseCookie = detectedSite.baseUrl.takeIf { it.isNotBlank() }?.let { cm.getCookie(it) }
        val combined = if (detectedSite == FanficSite.WATTPAD) {
            mergeCookieStrings(
                urlCookie,
                baseCookie,
                cm.getCookie("https://wattpad.com"),
                cm.getCookie("https://www.wattpad.com"),
                cm.getCookie("https://api.wattpad.com")
            )
        } else {
            mergeCookieStrings(urlCookie, baseCookie)
        }

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

    fun destroyWebViewSafely(wv: WebView?) {
        if (wv == null) return
        runCatching {
            wv.stopLoading()
            wv.loadUrl("about:blank")
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.removeAllViews()
            wv.destroy()
        }
    }

    fun checkWattpadPostLogin(view: WebView?, url: String?, checkBlankDom: Boolean) {
        CookieManager.getInstance().flush()
        val site = resolveActiveSite(url ?: currentUrl)
        if (site != FanficSite.WATTPAD) return

        val authed = hasWattpadAuthCookie()
        if (authed && !wattpadSignedIn) {
            wattpadSignedIn = true
            persistCookiesForCurrentUrl(toastOnEmpty = false)
            AppLogger.i("FanficWebView", "Wattpad session auto-saved on ${url ?: currentUrl}")
        }

        if (checkBlankDom && authed && whiteScreenRecoveryAttempts < 2 && view != null) {
            val checkUrl = url.orEmpty()
            val shouldInspectDom = checkUrl.isBlank() ||
                checkUrl == "about:blank" ||
                checkUrl.contains("/login", ignoreCase = true) ||
                checkUrl.contains("/auth", ignoreCase = true) ||
                checkUrl.contains("wattpad.com", ignoreCase = true)
            if (shouldInspectDom) {
                whiteScreenCheckJob?.cancel()
                whiteScreenCheckJob = scope.launch {
                    delay(900L)
                    if (view !== webViewHolder.value) return@launch
                    val cur = view.url.orEmpty()
                    if (cur == "about:blank" && whiteScreenRecoveryAttempts < 2) {
                        whiteScreenRecoveryAttempts++
                        AppLogger.w(
                            "FanficWebView",
                            "Recovering Wattpad blank URL ($cur) -> /home (attempt $whiteScreenRecoveryAttempts)"
                        )
                        view.loadUrl("https://www.wattpad.com/home")
                        return@launch
                    }
                    view.evaluateJavascript(
                        "(function(){var b=document.body;if(!b)return '0|0';return (b.innerText||'').trim().length+'|'+(b.children?b.children.length:0);})();"
                    ) { raw ->
                        val decoded = runCatching {
                            if (raw.isNullOrBlank() || raw == "null") "" else Json.decodeFromString<String>(raw)
                        }.getOrDefault("")
                        val parts = decoded.split('|')
                        val textLen = parts.getOrNull(0)?.toIntOrNull() ?: -1
                        val childCount = parts.getOrNull(1)?.toIntOrNull() ?: -1
                        if (textLen == 0 && childCount <= 1 && whiteScreenRecoveryAttempts < 2) {
                            whiteScreenRecoveryAttempts++
                            AppLogger.w(
                                "FanficWebView",
                                "Recovering blank Wattpad post-login DOM ($cur, $decoded) -> /home (attempt $whiteScreenRecoveryAttempts)"
                            )
                            view.loadUrl("https://www.wattpad.com/home")
                        }
                    }
                }
            }
        }
    }

    fun closePopupWebView() {
        val popup = popupWebViewHolder.value ?: return
        popupWebViewHolder.value = null
        destroyWebViewSafely(popup)
        CookieManager.getInstance().flush()
        val mainWv = webViewHolder.value
        checkWattpadPostLogin(mainWv, mainWv?.url ?: currentUrl, checkBlankDom = true)
    }

    fun configureCommonSettings(wv: WebView, site: FanficSite, desktop: Boolean) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(wv, true)
        wv.setBackgroundColor(android.graphics.Color.parseColor("#121212"))
        wv.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = resolveUserAgent(site, desktop)
        }
    }

    fun handleOverrideUrl(ctx: Context, view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val raw = uri.toString()
        val site = resolveActiveSite(currentUrl)

        if (scheme == "http" || scheme == "https") {
            if (isDesktopMode && raw.contains("://m.fanfiction.net", ignoreCase = true)) {
                view?.loadUrl(raw.replace(Regex("(?i)://m\\.fanfiction\\.net"), "://www.fanfiction.net"))
                return true
            }
            return false
        }

        if (scheme == "intent") {
            val fallbackUrl = runCatching {
                Intent.parseUri(raw, Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
            }.getOrNull()
            if (!fallbackUrl.isNullOrBlank() &&
                (fallbackUrl.startsWith("http://") || fallbackUrl.startsWith("https://"))
            ) {
                view?.loadUrl(fallbackUrl)
                return true
            }
            if (site == FanficSite.WATTPAD || raw.contains("wattpad", ignoreCase = true)) {
                AppLogger.w("FanficWebView", "Blocked Wattpad intent hijack: $raw")
                return true
            }
        }

        if (scheme == "wattpad" || (site == FanficSite.WATTPAD && scheme == "market")) {
            AppLogger.w("FanficWebView", "Blocked Wattpad deep-link redirect: $raw")
            return true
        }

        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
        return true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler {
            val popup = popupWebViewHolder.value
            if (popup != null) {
                if (popup.canGoBack()) popup.goBack() else closePopupWebView()
            } else {
                val wv = webViewHolder.value
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    onDismiss()
                }
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
                        val popup = popupWebViewHolder.value
                        if (popup != null) {
                            if (popup.canGoBack()) popup.goBack() else closePopupWebView()
                        } else {
                            val wv = webViewHolder.value
                            if (wv != null && wv.canGoBack()) wv.goBack() else onDismiss()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }

                    val activeSite = resolveActiveSite(currentUrl)
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

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDesktopMode) AccentGold.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f))
                            .border(
                                0.7.dp,
                                if (isDesktopMode) AccentGold else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                val next = !isDesktopMode
                                isDesktopMode = next
                                val siteNow = resolveActiveSite(currentUrl)
                                webViewHolder.value?.let { wv ->
                                    wv.settings.userAgentString = resolveUserAgent(siteNow, next)
                                    val reloadUrl = if (next && currentUrl.contains("://m.fanfiction.net", ignoreCase = true)) {
                                        currentUrl.replace(Regex("(?i)://m\\.fanfiction\\.net"), "://www.fanfiction.net")
                                    } else currentUrl
                                    wv.loadUrl(reloadUrl)
                                }
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isDesktopMode) "PC" else "Mobile",
                            color = if (isDesktopMode) AccentGold else TextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = { (popupWebViewHolder.value ?: webViewHolder.value)?.reload() }) {
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

                if (wattpadSignedIn) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0B291E))
                            .border(0.5.dp, AccentJade.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AccentJade, modifier = Modifier.size(15.dp))
                        Text(
                            text = "Signed in to Wattpad — session saved!",
                            color = Color(0xFFA7F3D0),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable {
                                    closePopupWebView()
                                    webViewHolder.value?.loadUrl("https://www.wattpad.com/home")
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(13.dp))
                            Text("Home", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = "Done",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentJade)
                                .clickable {
                                    persistCookiesForCurrentUrl(toastOnEmpty = false)
                                    onDismiss()
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Interactive Android WebView container (supports stacked OAuth popups)
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        val container = FrameLayout(ctx).apply {
                            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
                        }
                        containerHolder.value = container

                        val mainWebView = WebView(ctx).apply {
                            configureCommonSettings(this, resolveActiveSite(safeInitialUrl), isDesktopMode)

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                    isLoading = newProgress < 100
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) pageTitle = title
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean {
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                    closePopupWebView()

                                    val popup = WebView(ctx).apply {
                                        configureCommonSettings(this, resolveActiveSite(currentUrl), isDesktopMode)
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(v: WebView?, newProgress: Int) {
                                                loadProgress = newProgress
                                                isLoading = newProgress < 100
                                            }

                                            override fun onReceivedTitle(v: WebView?, title: String?) {
                                                if (!title.isNullOrBlank()) pageTitle = title
                                            }

                                            override fun onCloseWindow(window: WebView?) {
                                                closePopupWebView()
                                            }
                                        }
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                                                if (!url.isNullOrBlank()) currentUrl = url
                                                isLoading = true
                                            }

                                            override fun onPageFinished(v: WebView?, url: String?) {
                                                if (!url.isNullOrBlank()) currentUrl = url
                                                isLoading = false
                                                CookieManager.getInstance().flush()
                                                val targetUrl = url.orEmpty()
                                                if (resolveActiveSite(targetUrl) == FanficSite.WATTPAD && hasWattpadAuthCookie()) {
                                                    checkWattpadPostLogin(webViewHolder.value, targetUrl, checkBlankDom = false)
                                                    if (targetUrl.contains("wattpad.com/home", ignoreCase = true) ||
                                                        targetUrl.contains("wattpad.com/library", ignoreCase = true) ||
                                                        targetUrl.contains("wattpad.com/stories", ignoreCase = true)
                                                    ) {
                                                        closePopupWebView()
                                                        webViewHolder.value?.loadUrl(targetUrl)
                                                    }
                                                }
                                            }

                                            override fun shouldOverrideUrlLoading(
                                                v: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean = handleOverrideUrl(ctx, v, request)

                                            override fun onRenderProcessGone(
                                                v: WebView?,
                                                detail: RenderProcessGoneDetail?
                                            ): Boolean {
                                                AppLogger.w("FanficWebView", "Popup WebView render process gone")
                                                closePopupWebView()
                                                return true
                                            }
                                        }
                                    }

                                    popupWebViewHolder.value = popup
                                    container.addView(
                                        popup,
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                    transport.webView = popup
                                    resultMsg.sendToTarget()
                                    return true
                                }

                                override fun onCloseWindow(window: WebView?) {
                                    if (window === popupWebViewHolder.value) {
                                        closePopupWebView()
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    if (!url.isNullOrBlank()) currentUrl = url
                                    isLoading = true
                                }

                                override fun doUpdateVisitedHistory(
                                    view: WebView?,
                                    url: String?,
                                    isReload: Boolean
                                ) {
                                    if (!url.isNullOrBlank()) currentUrl = url
                                    checkWattpadPostLogin(view, url, checkBlankDom = false)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    if (!url.isNullOrBlank()) currentUrl = url
                                    isLoading = false
                                    checkWattpadPostLogin(view, url, checkBlankDom = true)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = handleOverrideUrl(ctx, view, request)

                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: RenderProcessGoneDetail?
                                ): Boolean {
                                    AppLogger.w(
                                        "FanficWebView",
                                        "Main WebView render process gone (didCrash=${detail?.didCrash()})"
                                    )
                                    val target = if (resolveActiveSite(currentUrl) == FanficSite.WATTPAD && hasWattpadAuthCookie()) {
                                        "https://www.wattpad.com/home"
                                    } else {
                                        currentUrl.ifBlank { safeInitialUrl }
                                    }
                                    runCatching { view?.loadUrl(target) }
                                    return true
                                }
                            }

                            loadUrl(safeInitialUrl)
                            webViewHolder.value = this
                        }

                        container.addView(
                            mainWebView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        container
                    }
                )

                // Bottom action bar: Save Session + Import Page / Mark Read
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceCard)
                        .border(0.6.dp, Color.White.copy(alpha = 0.08f))
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val saved = persistCookiesForCurrentUrl(toastOnEmpty = true)
                            if (saved != null) {
                                val site = resolveActiveSite(currentUrl)
                                if (site == FanficSite.WATTPAD && hasWattpadAuthCookie()) {
                                    wattpadSignedIn = true
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
            whiteScreenCheckJob?.cancel()
            whiteScreenCheckJob = null
            destroyWebViewSafely(popupWebViewHolder.value)
            popupWebViewHolder.value = null
            destroyWebViewSafely(webViewHolder.value)
            webViewHolder.value = null
            containerHolder.value = null
        }
    }
}
