package com.omnitrack.app.ui.screens.detail.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.omnitrack.app.data.trailer.InAppYouTubeExtractor
import com.omnitrack.app.data.trailer.TrailerPlaybackSource
import com.omnitrack.app.data.trailer.YoutubeChunkedDataSourceFactory
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextPrimary

/**
 * In-App Trailer Player powered by native InAppYouTubeExtractor + Media3 ExoPlayer
 * (chunked DataSource + MergingMediaSource / HlsMediaSource), eliminating YouTube 152-4 iframe blocks.
 * Falls back gracefully to a fixed-Referer WebView + Open in YouTube button if extraction fails.
 */
@OptIn(UnstableApi::class)
@SuppressLint("SetJavaScriptEnabled")
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrailerPlayerPopup(
    visible: Boolean,
    trailerTitle: String,
    trailerType: String,
    contentTitle: String,
    youtubeKey: String,
    onDismiss: () -> Unit
) {
    if (!visible || youtubeKey.isBlank()) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isFullscreen by remember { mutableStateOf(false) }

    var isExtracting by remember(youtubeKey) { mutableStateOf(true) }
    var playbackSource by remember(youtubeKey) { mutableStateOf<TrailerPlaybackSource?>(null) }
    var useWebFallback by remember(youtubeKey) { mutableStateOf(false) }

    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customVideoCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val extractor = remember { InAppYouTubeExtractor() }

    LaunchedEffect(youtubeKey) {
        isExtracting = true
        useWebFallback = false
        val extracted = extractor.extractPlaybackSource(youtubeKey)
        playbackSource = extracted
        isExtracting = false
        if (extracted == null) {
            useWebFallback = true
        }
    }

    val exitFullscreen: () -> Unit = {
        customVideoCallback?.onCustomViewHidden()
        customVideoView = null
        customVideoCallback = null
        isFullscreen = false
    }

    val subtitle = buildList {
        if (trailerTitle.isNotBlank()) add(trailerTitle)
        if (contentTitle.isNotBlank()) add(contentTitle)
    }.joinToString(" \u2022 ")

    val openInYouTubeApp = {
        try {
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$youtubeKey"))
            context.startActivity(appIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$youtubeKey"))
            context.startActivity(webIntent)
        }
    }

    // Native ExoPlayer bound strictly to playbackSource when active
    val activeSource = playbackSource
    val exoPlayer = remember(activeSource) {
        if (activeSource == null || useWebFallback) {
            null
        } else {
            val dsFactory = YoutubeChunkedDataSourceFactory()
            val mediaSourceFactory = DefaultMediaSourceFactory(dsFactory)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    when {
                        activeSource.isHls -> {
                            val hls = HlsMediaSource.Factory(dsFactory)
                                .createMediaSource(MediaItem.fromUri(activeSource.videoUrl))
                            setMediaSource(hls)
                        }
                        !activeSource.audioUrl.isNullOrBlank() -> {
                            setMediaSource(
                                MergingMediaSource(
                                    mediaSourceFactory.createMediaSource(MediaItem.fromUri(activeSource.videoUrl)),
                                    mediaSourceFactory.createMediaSource(MediaItem.fromUri(activeSource.audioUrl))
                                )
                            )
                        }
                        else -> setMediaItem(MediaItem.fromUri(activeSource.videoUrl))
                    }
                    playWhenReady = true
                    prepare()
                }
        }
    }

    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                useWebFallback = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Fixed-Referer WebView fallback ONLY initialized if native extraction/ExoPlayer fails
    val fallbackWebView = remember(youtubeKey, useWebFallback, context) {
        if (!useWebFallback) return@remember null
        val appOrigin = "https://com.omnitrack.app"
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; background: #000000; }
                    html, body { width: 100%; height: 100%; overflow: hidden; background: #000000; }
                    iframe { width: 100%; height: 100%; border: 0; }
                </style>
            </head>
            <body>
                <iframe
                    src="https://www.youtube-nocookie.com/embed/$youtubeKey?autoplay=1&playsinline=1&rel=0&modestbranding=1&controls=1&origin=$appOrigin"
                    referrerpolicy="strict-origin-when-cross-origin"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                    allowfullscreen>
                </iframe>
            </body>
            </html>
        """.trimIndent()
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    customVideoView = view?.apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    customVideoCallback = callback
                    isFullscreen = true
                }

                override fun onHideCustomView() {
                    customVideoView = null
                    customVideoCallback?.onCustomViewHidden()
                    customVideoCallback = null
                    isFullscreen = false
                }
            }
            webViewClient = WebViewClient()
            loadDataWithBaseURL(appOrigin, html, "text/html", "UTF-8", null)
        }
    }

    DisposableEffect(fallbackWebView) {
        onDispose {
            exitFullscreen()
            fallbackWebView?.let { wv ->
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.loadUrl("about:blank")
                wv.stopLoading()
                wv.destroy()
            }
        }
    }

    @Composable
    fun PlayerViewportContent() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when {
                customVideoView != null -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { _ ->
                            (customVideoView?.parent as? ViewGroup)?.removeView(customVideoView)
                            customVideoView ?: View(context)
                        }
                    )
                }
                exoPlayer != null && !useWebFallback -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = true
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                setFullscreenButtonClickListener { _ ->
                                    isFullscreen = !isFullscreen
                                }
                                player = exoPlayer
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { pv ->
                            if (pv.player !== exoPlayer) {
                                pv.player = exoPlayer
                            }
                        },
                        onRelease = { pv ->
                            if (pv.player === exoPlayer) {
                                pv.player = null
                            }
                        }
                    )
                }
                fallbackWebView != null -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { _ ->
                            (fallbackWebView.parent as? ViewGroup)?.removeView(fallbackWebView)
                            fallbackWebView.apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        }
                    )
                }
                isExtracting -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AccentGold,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Loading HD trailer stream...",
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentGold)
                            .clickable(onClick = openInYouTubeApp)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Open in YouTube App",
                            color = OnAccentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    BackHandler(enabled = isFullscreen) { exitFullscreen() }

    val parentComposition = rememberCompositionContext()
    val currentTrailerType by rememberUpdatedState(trailerType)
    val currentSubtitle by rememberUpdatedState(subtitle)
    val currentOpenInYouTubeApp by rememberUpdatedState(openInYouTubeApp)
    val currentExitFullscreen by rememberUpdatedState(exitFullscreen)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    DisposableEffect(isFullscreen) {
        if (!isFullscreen) {
            return@DisposableEffect onDispose {}
        }

        val activity = context.findActivity()
        val contentRoot = activity?.findViewById<ViewGroup>(android.R.id.content)
        val window = activity?.window
        val prevCutoutMode = if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode
        } else {
            0
        }

        if (activity != null && window != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val fullscreenView = if (activity != null && contentRoot != null) {
            ComposeView(activity).apply {
                setParentCompositionContext(parentComposition)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setContent {
                    BackHandler { currentExitFullscreen() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        PlayerViewportContent()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 18.dp)
                                .align(Alignment.TopCenter),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentTrailerType.ifBlank { "Trailer" },
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (currentSubtitle.isNotBlank()) {
                                    Text(
                                        text = currentSubtitle,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { currentOpenInYouTubeApp() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Open in YouTube",
                                        tint = Color.Red,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { currentExitFullscreen() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FullscreenExit,
                                        contentDescription = "Exit Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        currentExitFullscreen()
                                        currentOnDismiss()
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }.also { view ->
                contentRoot.addView(view)
            }
        } else {
            null
        }

        onDispose {
            fullscreenView?.let { view ->
                view.disposeComposition()
                (view.parent as? ViewGroup)?.removeView(view)
            }
            if (window != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = prevCutoutMode
                    }
                }
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    if (!isFullscreen) {
        ModalBottomSheet(
            onDismissRequest = {
                exitFullscreen()
                onDismiss()
            },
            sheetState = sheetState,
            containerColor = AmoledBlack,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trailerType.ifBlank { "Trailer" },
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.80f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = openInYouTubeApp,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Open in YouTube",
                                tint = Color(0xFFFF0000),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = { isFullscreen = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                exitFullscreen()
                                onDismiss()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceElevated)
                        .border(0.6.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    PlayerViewportContent()
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
