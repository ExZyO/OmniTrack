package com.omnitrack.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.preferences.ChimanewLibrarySettings
import com.omnitrack.app.data.preferences.HomeHeroSettings
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Fullscreen Custom Categories Manager styled 1:1 with ArtworkPriorityScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoriesScreen(
    repository: MediaRepository,
    preferenceManager: PreferenceManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val dbCategories by repository.getAllCategories().collectAsState(initial = null)
    val categoryAssignments by preferenceManager.categoryAssignments.collectAsState(initial = emptyMap())
    val libSettings by preferenceManager.chimanewLibrarySettings.collectAsState(initial = ChimanewLibrarySettings())
    val heroSettings by preferenceManager.homeHeroSettings.collectAsState(initial = HomeHeroSettings())

    val currentCategories = remember { mutableStateListOf<CategoryEntity>() }
    var initialized by remember { mutableStateOf(false) }
    var isDraggingAny by remember { mutableStateOf(false) }

    var showInlineCreator by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<CategoryEntity?>(null) }

    LaunchedEffect(dbCategories) {
        val loaded = dbCategories ?: return@LaunchedEffect
        if (!initialized) {
            currentCategories.clear()
            currentCategories.addAll(loaded)
            initialized = true
        } else if (!isDraggingAny && currentCategories.toList() != loaded) {
            currentCategories.clear()
            currentCategories.addAll(loaded)
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = currentCategories.indexOfFirst { it.id == from.key }
        val toIdx = currentCategories.indexOfFirst { it.id == to.key }
        if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
            currentCategories.apply { add(toIdx, removeAt(fromIdx)) }
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val countsByCatId = remember(categoryAssignments) {
        val counts = HashMap<Long, Int>()
        categoryAssignments.values.forEach { catSet ->
            catSet.forEach { catId ->
                counts[catId] = (counts[catId] ?: 0) + 1
            }
        }
        counts
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar: 1:1 with ArtworkPriorityScreen (circle-free white back arrow + Title + right gold action)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
            ) {
                Text(
                    text = "Manage Categories",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "Reorder library tabs, visibility & defaults",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            Text(
                text = if (showInlineCreator) "Close" else "+ Add",
                color = AccentGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        showInlineCreator = !showInlineCreator
                        if (!showInlineCreator) newCategoryName = ""
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            thickness = 0.6.dp
        )

        // Subtitle instructions row (1:1 ArtworkPriorityScreen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
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
                text = "Drag \u2261 on the right to reorder. Tap the eye to show or hide in Library tabs.",
                color = TextMuted,
                fontSize = 11.5.sp
            )
        }

        // Inline Category Creator (Option A frameless hairline strip)
        AnimatedVisibility(visible = showInlineCreator) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BasicTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(AccentGold),
                        decorationBox = { inner ->
                            if (newCategoryName.isEmpty()) {
                                Text("Category name (e.g. Peak Fiction, Reading Queue)...", color = TextMuted, fontSize = 14.sp)
                            }
                            inner()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                    )
                    Text(
                        text = "Create",
                        color = if (newCategoryName.trim().isNotEmpty()) AccentGold else TextMuted,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = newCategoryName.trim().isNotEmpty()) {
                                val trimmed = newCategoryName.trim()
                                val nextOrder = (currentCategories.maxOfOrNull { it.orderIndex } ?: -1) + 1
                                coroutineScope.launch {
                                    repository.insertCategory(
                                        CategoryEntity(
                                            name = trimmed,
                                            orderIndex = nextOrder,
                                            isVisible = true
                                        )
                                    )
                                    newCategoryName = ""
                                    showInlineCreator = false
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                HorizontalDivider(color = AccentGold.copy(alpha = 0.55f), thickness = 0.8.dp)
            }
        }

        if (!initialized) {
            Box(modifier = Modifier.fillMaxSize())
        } else if (currentCategories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "No custom categories yet",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Create categories to group your library into custom shelves, tabs, and Hero Banner rotation.",
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentGold.copy(alpha = 0.16f))
                            .clickable { showInlineCreator = true }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "+ Create First Category",
                            color = AccentGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = currentCategories,
                    key = { _, cat -> cat.id }
                ) { idx, cat ->
                    val isEnabled = cat.isVisible
                    val displayRank = when {
                        !isEnabled -> "OFF"
                        idx == 0 -> "#1"
                        else -> "#${idx + 1}"
                    }
                    val count = countsByCatId[cat.id] ?: 0
                    val isDefault = libSettings.defaultCategoryId == cat.id
                    val isInHero = heroSettings.heroCategoryMode.equals("CUSTOM_CATEGORIES", ignoreCase = true) &&
                        cat.id in heroSettings.heroCustomCategoryIds

                    val subtitle = if (!isEnabled) {
                        "Hidden \u2014 bypassed in Library tabs"
                    } else {
                        buildList {
                            add("$count ${if (count == 1) "title" else "titles"}")
                            if (isDefault) add("Default")
                            if (isInHero) add("Shown in Hero")
                        }.joinToString(" \u00B7 ")
                    }

                    ReorderableItem(reorderableState, key = cat.id) { isDragging ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 10f else 1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDragging) DarkSurfaceElevated else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (isDragging) AccentGold.copy(alpha = 0.65f) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .drawBehind {
                                    if (!isDragging) {
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.08f),
                                            start = Offset(0f, size.height),
                                            end = Offset(size.width, size.height),
                                            strokeWidth = 0.6.dp.toPx()
                                        )
                                    }
                                }
                                .clickable { editingCategory = cat }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Eye Icon Toggle (Show / Hide in Library tabs)
                            IconButton(
                                onClick = {
                                    val updated = cat.copy(isVisible = !cat.isVisible)
                                    currentCategories[idx] = updated
                                    coroutineScope.launch { repository.updateCategory(updated) }
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = if (isEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isEnabled) "Hide Category" else "Show Category",
                                    tint = if (isEnabled) AccentGold else TextMuted.copy(alpha = 0.45f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Rank Badge Pill (#1 / OFF)
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            !isEnabled -> Color.White.copy(alpha = 0.04f)
                                            idx == 0 -> AccentGold.copy(alpha = 0.18f)
                                            isDragging -> AccentGold.copy(alpha = 0.24f)
                                            else -> Color.White.copy(alpha = 0.06f)
                                        }
                                    )
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = displayRank,
                                    color = when {
                                        !isEnabled -> TextMuted
                                        idx == 0 || isDragging -> AccentGold
                                        else -> TextSecondary
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            // Center: Category Name & Metadata Subtitle
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = cat.name,
                                    color = if (isEnabled) TextPrimary else TextMuted.copy(alpha = 0.65f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = subtitle,
                                    color = if (isEnabled) TextSecondary else TextMuted.copy(alpha = 0.50f),
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Right: Drag Handle
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .draggableHandle(
                                        onDragStarted = {
                                            isDraggingAny = true
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            isDraggingAny = false
                                            coroutineScope.launch {
                                                val persisted = currentCategories.mapIndexed { order, c ->
                                                    c.copy(orderIndex = order)
                                                }
                                                repository.insertAllCategories(persisted)
                                            }
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Reorder",
                                    tint = if (isDragging) AccentGold else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Category Edit / Options Bottom Sheet (Option A Frameless AMOLED)
    val sheetCat = editingCategory
    if (sheetCat != null) {
        var renameDraft by remember(sheetCat.id) { mutableStateOf(sheetCat.name) }
        val isDefaultCat = libSettings.defaultCategoryId == sheetCat.id
        val isHeroIncluded = sheetCat.id in heroSettings.heroCustomCategoryIds

        ModalBottomSheet(
            onDismissRequest = { editingCategory = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AmoledBlack,
            contentColor = TextPrimary,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f))
                    )
                }

                Text(
                    text = "Category Options",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BasicTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(AccentGold),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                    )
                    val changed = renameDraft.trim().isNotBlank() && renameDraft.trim() != sheetCat.name
                    Text(
                        text = "Rename",
                        color = if (changed) AccentGold else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = changed) {
                                val nextName = renameDraft.trim()
                                val updated = sheetCat.copy(name = nextName)
                                coroutineScope.launch {
                                    repository.updateCategory(updated)
                                }
                                editingCategory = updated
                                Toast.makeText(context, "Renamed to $nextName", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                CategoryOptionSwitchRow(
                    title = "Default Category for New Entries",
                    subtitle = "Automatically assign newly added titles here",
                    checked = isDefaultCat,
                    onCheckedChange = { enable ->
                        coroutineScope.launch {
                            preferenceManager.setDefaultLibraryCategoryId(if (enable) sheetCat.id else null)
                        }
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                CategoryOptionSwitchRow(
                    title = "Include in Home Hero Banner",
                    subtitle = if (heroSettings.heroCategoryMode.equals("CUSTOM_CATEGORIES", ignoreCase = true)) {
                        "Featured in Home Hero Custom Categories rotation"
                    } else {
                        "Also activates Custom Categories mode on Home Hero"
                    },
                    checked = isHeroIncluded,
                    onCheckedChange = { enable ->
                        coroutineScope.launch {
                            val nextSet = if (enable) {
                                heroSettings.heroCustomCategoryIds + sheetCat.id
                            } else {
                                heroSettings.heroCustomCategoryIds - sheetCat.id
                            }
                            preferenceManager.setHomeHeroCustomCategoryIds(nextSet)
                            if (enable && !heroSettings.heroCategoryMode.equals("CUSTOM_CATEGORIES", ignoreCase = true)) {
                                preferenceManager.setHomeHeroCategoryMode("CUSTOM_CATEGORIES")
                            }
                        }
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                CategoryOptionSwitchRow(
                    title = "Show in Library Tabs",
                    subtitle = "Visible when grouping Library by Custom Category",
                    checked = sheetCat.isVisible,
                    onCheckedChange = { vis ->
                        val updated = sheetCat.copy(isVisible = vis)
                        editingCategory = updated
                        coroutineScope.launch { repository.updateCategory(updated) }
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingCategory = null
                            deleteConfirmTarget = sheetCat
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Delete Category",
                        color = Color(0xFFF87171),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    val delCat = deleteConfirmTarget
    if (delCat != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            containerColor = AmoledBlack,
            title = {
                Text("Delete \"${delCat.name}\"?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "This removes the category label only. Your tracked library items stay safe in your Library.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmTarget = null
                        coroutineScope.launch {
                            repository.deleteCategory(delCat)
                            preferenceManager.removeCategoryFromAllMedia(delCat.id)
                        }
                    }
                ) {
                    Text("Delete", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun CategoryOptionSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = TextSecondary, fontSize = 11.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnAccentColor,
                checkedTrackColor = AccentGold,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}
