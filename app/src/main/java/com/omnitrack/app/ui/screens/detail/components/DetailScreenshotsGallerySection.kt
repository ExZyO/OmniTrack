package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.omnitrack.app.data.model.BookIllustrationAsset
import coil3.request.crossfade
import com.omnitrack.app.data.model.GameMediaAsset
import com.omnitrack.app.data.model.VnNsfwFilterMode
import com.omnitrack.app.data.model.VnScreenshotItem
import com.omnitrack.app.ui.theme.AccentGold

@Composable
fun DetailScreenshotsGallerySection(
    screenshots: List<VnScreenshotItem>,
    nsfwFilterMode: VnNsfwFilterMode,
    onSetAsHeroBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleShots = remember(screenshots, nsfwFilterMode) {
        if (nsfwFilterMode == VnNsfwFilterMode.SAFE_HIDE) {
            screenshots.filter { !it.isSensitive }
        } else {
            screenshots
        }
    }
    if (visibleShots.isEmpty()) return

    val context = LocalContext.current
    var unblurredIndices by remember(screenshots, nsfwFilterMode) { mutableStateOf<Set<Int>>(emptySet()) }
    var lightboxInitialIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Screenshots & CGs",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.3).sp
            )
            Text(
                text = "Tap to preview · Set Banner",
                fontSize = 11.5.sp,
                color = Color.White.copy(alpha = 0.65f)
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(visibleShots, key = { idx, shot -> "${shot.url}_$idx" }) { idx, shot ->
                val isBlurred = shot.isSensitive &&
                    nsfwFilterMode == VnNsfwFilterMode.BLUR_TAP &&
                    idx !in unblurredIndices

                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(101.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable {
                            if (isBlurred) {
                                unblurredIndices = unblurredIndices + idx
                            } else {
                                lightboxInitialIndex = idx
                            }
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(shot.thumbnailUrl ?: shot.url)
                            .crossfade(true)
                            .build(),
                        contentDescription = shot.releaseTitle ?: "Screenshot ${idx + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .then(if (isBlurred) Modifier.blur(16.dp) else Modifier)
                    )
                    if (isBlurred) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.32f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Mature CG\nTap to unblur",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    val activeIdx = lightboxInitialIndex
    if (activeIdx != null && activeIdx in visibleShots.indices) {
        val pagerState = rememberPagerState(
            initialPage = activeIdx,
            pageCount = { visibleShots.size }
        )
        Dialog(
            onDismissRequest = { lightboxInitialIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .navigationBarsPadding()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val currentShot = visibleShots[page]
                    val pageBlurred = currentShot.isSensitive &&
                        nsfwFilterMode == VnNsfwFilterMode.BLUR_TAP &&
                        page !in unblurredIndices
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                if (pageBlurred) {
                                    unblurredIndices = unblurredIndices + page
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(currentShot.url)
                                .crossfade(true)
                                .build(),
                            contentDescription = "CG Fullscreen ${page + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (pageBlurred) Modifier.blur(20.dp) else Modifier)
                        )
                        if (pageBlurred) {
                            Text(
                                text = "Mature CG · Tap to unblur",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = "${pagerState.currentPage + 1} / ${visibleShots.size}",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 42.dp)
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { lightboxInitialIndex = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            onSetAsHeroBanner(visibleShots[pagerState.currentPage].url)
                            lightboxInitialIndex = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGold,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Text("Set as Hero Banner", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailGameGallerySection(
    assets: List<GameMediaAsset>,
    onSetAsHeroBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (assets.isEmpty()) return

    val context = LocalContext.current
    val hasCleanArt = remember(assets) { assets.any { !it.isScreenshot } }
    val hasScreenshots = remember(assets) { assets.any { it.isScreenshot } }
    var selectedFilter by remember(hasCleanArt) {
        mutableStateOf(if (hasCleanArt) "CLEAN" else "ALL")
    }
    val visibleAssets = remember(assets, selectedFilter) {
        when (selectedFilter) {
            "CLEAN" -> assets.filter { !it.isScreenshot }.ifEmpty { assets }
            "SCREENSHOTS" -> assets.filter { it.isScreenshot }.ifEmpty { assets }
            else -> assets
        }
    }
    var lightboxInitialIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Artwork & Screenshots",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.3).sp
            )
            Text(
                text = "Tap to preview · Set Banner",
                fontSize = 11.5.sp,
                color = Color.White.copy(alpha = 0.65f)
            )
        }

        if (hasCleanArt && hasScreenshots) {
            val tabs = listOf(
                "CLEAN" to "Clean Art",
                "SCREENSHOTS" to "Screenshots",
                "ALL" to "All (${assets.size})"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                tabs.forEach { (key, label) ->
                    val selected = selectedFilter == key
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .clickable { selectedFilter = key },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.48f),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (selected) Color.White else Color.Transparent)
                        )
                    }
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(visibleAssets, key = { idx, asset -> "${asset.url}_$idx" }) { idx, asset ->
                Box(
                    modifier = Modifier
                        .width(188.dp)
                        .height(106.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable { lightboxInitialIndex = idx }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(asset.thumbnailUrl ?: asset.url)
                            .crossfade(true)
                            .build(),
                        contentDescription = asset.label ?: "Game Media ${idx + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        }
    }

    val activeIdx = lightboxInitialIndex
    if (activeIdx != null && activeIdx in visibleAssets.indices) {
        val pagerState = rememberPagerState(
            initialPage = activeIdx,
            pageCount = { visibleAssets.size }
        )
        Dialog(
            onDismissRequest = { lightboxInitialIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .navigationBarsPadding()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val currentAsset = visibleAssets[page]
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(currentAsset.url)
                                .crossfade(true)
                                .build(),
                            contentDescription = currentAsset.label ?: "Game Asset ${page + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 42.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${visibleAssets.size}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    visibleAssets[pagerState.currentPage].label?.takeIf { it.isNotBlank() }?.let { lbl ->
                        Text(
                            text = lbl,
                            color = Color.White.copy(alpha = 0.58f),
                            fontSize = 11.5.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { lightboxInitialIndex = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            onSetAsHeroBanner(visibleAssets[pagerState.currentPage].url)
                            lightboxInitialIndex = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGold,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Text("Set as Hero Banner", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailBookGallerySection(
    illustrations: List<BookIllustrationAsset>,
    onSetAsHeroBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (illustrations.isEmpty()) return
    val context = LocalContext.current
    var lightboxInitialIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "ARTWORK & ILLUSTRATIONS (${illustrations.size})",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.60f),
            letterSpacing = 1.1.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(illustrations) { index, asset ->
                Column(
                    modifier = Modifier
                        .width(196.dp)
                        .clickable { lightboxInitialIndex = index },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(asset.thumbnailUrl ?: asset.url)
                            .crossfade(true)
                            .build(),
                        contentDescription = asset.caption ?: "Illustration ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(124.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                    )
                    asset.caption?.takeIf { it.isNotBlank() }?.let { cap ->
                        Text(
                            text = cap,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.5.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }

    lightboxInitialIndex?.let { startIdx ->
        Dialog(
            onDismissRequest = { lightboxInitialIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val pagerState = rememberPagerState(initialPage = startIdx.coerceIn(0, illustrations.lastIndex)) {
                illustrations.size
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f))
                    .navigationBarsPadding()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val current = illustrations[page]
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(current.url)
                                .crossfade(true)
                                .build(),
                            contentDescription = current.caption ?: "Illustration ${page + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 42.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${illustrations.size}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    illustrations[pagerState.currentPage].caption?.takeIf { it.isNotBlank() }?.let { cap ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = cap,
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { lightboxInitialIndex = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            onSetAsHeroBanner(illustrations[pagerState.currentPage].url)
                            lightboxInitialIndex = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGold,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Text("Set as Hero Banner", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
