package com.omnitrack.app.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.ui.components.DiagnosticsSheet
import com.omnitrack.app.ui.components.MediaCard
import com.omnitrack.app.ui.components.MediaTypeTabRow
import com.omnitrack.app.ui.components.StatusFilterChips
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDiagnostics by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .statusBarsPadding()
    ) {
        // Blended Header: Title + Ambient Actions (Search + Diagnostics)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "OmniTrack",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Universal Media Library",
                    fontSize = 11.5.sp,
                    color = TextMuted,
                    letterSpacing = 0.2.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Diagnostics Sheet trigger
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated)
                        .clickable { showDiagnostics = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Diagnostics",
                        tint = AccentArctic,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Search trigger
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Media type tabs (fluid row)
        MediaTypeTabRow(
            selectedType = uiState.selectedMediaType,
            onTypeSelected = { viewModel.selectMediaType(it) },
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        // Status filter chips (fluid row)
        StatusFilterChips(
            selectedStatus = uiState.selectedStatus,
            onStatusSelected = { viewModel.selectStatus(it) },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Content
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = AccentArctic,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp
                )
            }
        } else if (uiState.mediaItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VideoLibrary,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No items in this category",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // High-density, borderless, seamless poster grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(
                    items = uiState.mediaItems,
                    key = { _, item -> item.id }
                ) { index, item ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(item.id) {
                        delay(index * 35L)
                        visible = true
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(250)) + slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialOffsetY = { it / 4 }
                        )
                    ) {
                        MediaCard(
                            item = item,
                            onCardClick = {
                                AppLogger.i("HomeScreen", "Tapped card: ${item.title} (id: ${item.id})")
                                onItemClick(item.id)
                            },
                            onQuickProgress = {
                                AppLogger.i("HomeScreen", "+1 progress tapped for: ${item.title}")
                                viewModel.incrementProgress(item)
                            }
                        )
                    }
                }
            }
        }

        // Live Diagnostics & System Logs Sheet
        if (showDiagnostics) {
            DiagnosticsSheet(onDismiss = { showDiagnostics = false })
        }
    }
}
