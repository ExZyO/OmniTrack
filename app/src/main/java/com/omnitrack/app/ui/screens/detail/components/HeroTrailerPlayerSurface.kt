package com.omnitrack.app.ui.screens.detail.components

import android.content.Context
import android.graphics.Matrix
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * 1:1 Nuvio In-App Hero Trailer Player Surface:
 * Renders cinematic backdrop trailer previews using ExoPlayer on a hardware-accelerated
 * TextureView. Provides smooth center-crop aspect filling, lifecycle awareness,
 * and zero-leak resource disposal.
 */
@OptIn(UnstableApi::class)
@Composable
fun HeroTrailerPlayerSurface(
    sourceUrl: String,
    playWhenReady: Boolean,
    muted: Boolean,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {},
    onEnded: () -> Unit = {},
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestPlayWhenReady = rememberUpdatedState(playWhenReady)
    val latestOnReady = rememberUpdatedState(onReady)
    val latestOnEnded = rememberUpdatedState(onEnded)
    val latestOnError = rememberUpdatedState(onError)
    var playerContainer by remember { mutableStateOf<HeroTrailerTextureContainer?>(null) }

    val exoPlayer = remember(sourceUrl, context) {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(sourceUrl))
                repeatMode = Player.REPEAT_MODE_OFF
                volume = if (muted) 0f else 1f
                prepare()
            }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        fun detachVideoSurface() {
            playerContainer?.detachPlayer(exoPlayer)
            playerContainer?.alpha = 0f
        }

        val listener = object : Player.Listener {
            private var readyReported = false
            private var endedReported = false

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (!readyReported) {
                            readyReported = true
                            latestOnReady.value()
                        }
                    }
                    Player.STATE_ENDED -> {
                        if (!endedReported) {
                            endedReported = true
                            latestOnEnded.value()
                        }
                    }
                    else -> Unit
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                playerContainer?.setVideoSize(videoSize)
            }

            override fun onPlayerError(error: PlaybackException) {
                detachVideoSurface()
                latestOnError.value()
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (latestPlayWhenReady.value && exoPlayer.playbackState != Player.STATE_ENDED) {
                        playerContainer?.attachPlayer(exoPlayer)
                        playerContainer?.alpha = 1f
                        exoPlayer.play()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                    detachVideoSurface()
                }
                else -> Unit
            }
        }

        exoPlayer.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.removeListener(listener)
            detachVideoSurface()
            exoPlayer.stop()
            exoPlayer.release()
            playerContainer = null
        }
    }

    LaunchedEffect(exoPlayer, playWhenReady) {
        if (exoPlayer.playbackState == Player.STATE_ENDED) return@LaunchedEffect
        exoPlayer.playWhenReady = playWhenReady
        if (playWhenReady) {
            playerContainer?.attachPlayer(exoPlayer)
            playerContainer?.alpha = 1f
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    LaunchedEffect(exoPlayer, muted) {
        exoPlayer.volume = if (muted) 0f else 1f
    }

    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { viewContext ->
            HeroTrailerTextureContainer(viewContext).apply {
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                attachPlayer(exoPlayer)
                playerContainer = this
            }
        },
        update = { container ->
            playerContainer = container
            if (playWhenReady) {
                container.attachPlayer(exoPlayer)
            }
        }
    )
}

/**
 * Custom TextureView container that applies aspect-fill matrix transformations to ensure
 * the video completely covers the container without black bars, matching Nuvio's cinematic hero.
 */
private class HeroTrailerTextureContainer(
    context: Context
) : FrameLayout(context) {
    val textureView = TextureView(context)
    private val textureTransform = Matrix()
    private var videoAspectRatio = 16f / 9f
    private var attachedPlayer: ExoPlayer? = null

    init {
        clipChildren = true
        clipToPadding = true
        isFocusable = false
        addView(
            textureView,
            LayoutParams(MATCH_PARENT, MATCH_PARENT)
        )
        textureView.isFocusable = false
        textureView.isClickable = false
    }

    fun attachPlayer(player: ExoPlayer) {
        if (attachedPlayer === player) return
        attachedPlayer?.clearVideoTextureView(textureView)
        attachedPlayer = player
        player.setVideoTextureView(textureView)
    }

    fun detachPlayer(player: ExoPlayer) {
        if (attachedPlayer === player) {
            player.clearVideoTextureView(textureView)
            attachedPlayer = null
        }
    }

    fun setVideoSize(videoSize: VideoSize) {
        if (videoSize.width <= 0 || videoSize.height <= 0) return
        videoAspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
        updateTextureTransform()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateTextureTransform()
    }

    private fun updateTextureTransform() {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f || videoAspectRatio <= 0f) return

        val viewAspectRatio = viewWidth / viewHeight
        textureTransform.reset()
        if (viewAspectRatio > videoAspectRatio) {
            val scaleY = viewAspectRatio / videoAspectRatio
            textureTransform.setScale(1f, scaleY, viewWidth / 2f, viewHeight / 2f)
        } else {
            val scaleX = videoAspectRatio / viewAspectRatio
            textureTransform.setScale(scaleX, 1f, viewWidth / 2f, viewHeight / 2f)
        }
        textureView.setTransform(textureTransform)
    }
}
