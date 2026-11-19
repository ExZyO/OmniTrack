package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import coil3.request.crossfade
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
