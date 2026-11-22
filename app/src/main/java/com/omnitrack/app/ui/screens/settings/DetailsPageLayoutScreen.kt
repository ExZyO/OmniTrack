package com.omnitrack.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.omnitrack.app.data.preferences.DetailSectionItem
import com.omnitrack.app.data.preferences.DetailsPageLayoutDefaults
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Dedicated Fullscreen Details Page Layout & Sections Customizer.
 * Reorder sections with fluid drag-and-drop (no arrow buttons) and toggle visibility.
 */
@Composable
fun DetailsPageLayoutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefManager = remember { PreferenceManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val savedSections by prefManager.detailsPageSectionOrder.collectAsState(initial = null)

    val currentSections = remember { mutableStateListOf<DetailSectionItem>() }
    var initialized by remember { mutableStateOf(false) }
    var isDraggingAny by remember { mutableStateOf(false) }

    LaunchedEffect(savedSections) {
        val loaded = savedSections ?: return@LaunchedEffect
        if (!initialized) {
            currentSections.clear()
            currentSections.addAll(loaded)
            initialized = true
        } else if (!isDraggingAny && currentSections.toList() != loaded) {
            currentSections.clear()
            currentSections.addAll(loaded)
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = currentSections.indexOfFirst { it.key.name == from.key }
        val toIdx = currentSections.indexOfFirst { it.key.name == to.key }
        if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
            currentSections.apply { add(toIdx, removeAt(fromIdx)) }
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Fullscreen Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = "Details Page Layout",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Section Order & Visibility",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = {
                    currentSections.clear()
                    currentSections.addAll(DetailsPageLayoutDefaults.defaultSections)
                    coroutineScope.launch {
                        prefManager.setDetailsPageSectionOrder(DetailsPageLayoutDefaults.defaultSections)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset to Defaults",
                    tint = TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Subtitle Instructions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "Drag ≡ on the right to reorder. Tap the eye to toggle visibility on detail pages.",
                color = TextMuted,
                fontSize = 11.5.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (!initialized) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
        // Reorderable LazyColumn
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(
                items = currentSections,
                key = { _, item -> item.key.name }
            ) { index, item ->
                val sectionKey = item.key.name
                ReorderableItem(reorderableState, key = sectionKey) { isBeingDragged ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isBeingDragged) 10f else 1f)
                            .then(
                                if (isBeingDragged) {
                                    Modifier
                                        .shadow(12.dp, RoundedCornerShape(14.dp))
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(DarkSurfaceElevated)
                                        .border(1.5.dp, AccentGold, RoundedCornerShape(14.dp))
                                } else {
                                    Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (item.isEnabled) DarkSurfaceElevated else Color.White.copy(alpha = 0.03f))
                                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Order Badge
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (item.isEnabled) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (item.isEnabled) AccentGold else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title & Description
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = item.key.displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.isEnabled) TextPrimary else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.key.description,
                                fontSize = 11.5.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Eye Visibility Toggle
                        IconButton(
                            onClick = {
                                val updated = item.copy(isEnabled = !item.isEnabled)
                                currentSections[index] = updated
                                coroutineScope.launch {
                                    prefManager.setDetailsPageSectionOrder(currentSections.toList())
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (item.isEnabled) "Hide Section" else "Show Section",
                                tint = if (item.isEnabled) AccentGold else TextMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Fluid Drag Handle (≡)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .draggableHandle(
                                    onDragStarted = {
                                        isDraggingAny = true
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        isDraggingAny = false
                                        coroutineScope.launch {
                                            prefManager.setDetailsPageSectionOrder(currentSections.toList())
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = if (isBeingDragged) AccentGold else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
