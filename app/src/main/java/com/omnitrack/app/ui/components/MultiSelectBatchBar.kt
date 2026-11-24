package com.omnitrack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * Option A Frameless AMOLED Top Selection Bar:
 * Replaces Library / History header directly: [X] [Count] on left, monochrome Select All & Invert on right, 0.6dp bottom hairline.
 */
@Composable
fun MultiSelectTopBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onClearSelection: () -> Unit = {},
    onExitMultiSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allSelected = totalCount > 0 && selectedCount >= totalCount
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AmoledBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onExitMultiSelect,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit Selection",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "$selectedCount",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { if (allSelected) onClearSelection() else onSelectAll() },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = if (allSelected) "Deselect All" else "Select All",
                        tint = if (allSelected) AccentGold else TextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
                IconButton(
                    onClick = onInvertSelection,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipToBack,
                        contentDescription = "Invert Selection",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            thickness = 0.6.dp
        )
    }
}

/**
 * Option A Flush #000000 Hairline Bottom Action Bar for Multi-Select mode:
 * - Zero gold border box, zero horizontal scroll, zero pill buttons.
 * - Opens crisp pure-#000000 frameless hairline sheets for Status, Categories, and More actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectBottomActionDock(
    selectedCount: Int,
    onMarkCompleted: () -> Unit,
    onMarkUnread: () -> Unit,
    onIncrementProgress: (() -> Unit)? = null,
    onSetStatus: ((TrackingStatus) -> Unit)? = null,
    onSyncTrackers: (() -> Unit)? = null,
    onHideFromHistory: (() -> Unit)? = null,
    onDeleteSelected: (() -> Unit)? = null,
    onOpenCategories: (() -> Unit)? = null,
    onOpenDetails: (() -> Unit)? = null,
    isInline: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showStatusSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    val enabled = selectedCount > 0
    val isFullDock = onOpenCategories != null || onSetStatus != null || onIncrementProgress != null || onDeleteSelected != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AmoledBlack)
            .then(if (!isInline) Modifier.navigationBarsPadding() else Modifier)
    ) {
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            thickness = 0.6.dp
        )

        if (!isFullDock) {
            // Inline 2-action strip for Episode / Fanfic Chapter multi-select
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlushBottomBarAction(
                    icon = Icons.Default.DoneAll,
                    label = "Mark Read",
                    tint = AccentGold,
                    enabled = enabled,
                    onClick = onMarkCompleted,
                    modifier = Modifier.weight(1f)
                )
                FlushBottomBarAction(
                    icon = Icons.Default.RemoveDone,
                    label = "Mark Unread",
                    tint = TextPrimary,
                    enabled = enabled,
                    onClick = onMarkUnread,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // Full 5-action flush AMOLED bottom bar matching Option A preview 1:1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onOpenCategories != null) {
                    FlushBottomBarAction(
                        icon = Icons.Default.Folder,
                        label = "Categories",
                        tint = TextPrimary,
                        enabled = enabled,
                        onClick = onOpenCategories,
                        modifier = Modifier.weight(1f)
                    )
                } else if (onHideFromHistory != null) {
                    FlushBottomBarAction(
                        icon = Icons.Default.VisibilityOff,
                        label = "Hide",
                        tint = TextPrimary,
                        enabled = enabled,
                        onClick = onHideFromHistory,
                        modifier = Modifier.weight(1f)
                    )
                }

                FlushBottomBarAction(
                    icon = Icons.Default.DoneAll,
                    label = "Read",
                    tint = AccentGold,
                    enabled = enabled,
                    onClick = onMarkCompleted,
                    modifier = Modifier.weight(1f)
                )

                if (onSetStatus != null) {
                    FlushBottomBarAction(
                        icon = Icons.Default.Tune,
                        label = "Status",
                        tint = TextPrimary,
                        enabled = enabled,
                        onClick = { showStatusSheet = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (onIncrementProgress != null) {
                    FlushBottomBarAction(
                        icon = Icons.Default.Add,
                        label = "+1",
                        tint = TextPrimary,
                        enabled = enabled,
                        onClick = onIncrementProgress,
                        modifier = Modifier.weight(1f)
                    )
                }

                FlushBottomBarAction(
                    icon = Icons.Default.MoreVert,
                    label = "More",
                    tint = TextPrimary,
                    enabled = enabled,
                    onClick = { showMoreSheet = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Pure #000000 Frameless Status Bottom Sheet
    if (showStatusSheet && onSetStatus != null) {
        ModalBottomSheet(
            onDismissRequest = { showStatusSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AmoledBlack,
            contentColor = TextPrimary,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
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
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Set Status ($selectedCount selected)",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                val statusOptions = listOf(
                    TrackingStatus.IN_PROGRESS,
                    TrackingStatus.COMPLETED,
                    TrackingStatus.PLAN_TO_TRACK,
                    TrackingStatus.ON_HOLD,
                    TrackingStatus.DROPPED
                )
                statusOptions.forEachIndexed { idx, st ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showStatusSheet = false
                                onSetStatus(st)
                            }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = st.displayName,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (idx < statusOptions.lastIndex) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                    }
                }
            }
        }
    }

    // Pure #000000 Frameless More Actions Bottom Sheet
    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AmoledBlack,
            contentColor = TextPrimary,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
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
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Batch Actions ($selectedCount selected)",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (selectedCount == 1 && onOpenDetails != null) {
                    SheetActionRow(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        label = "Open Details Page",
                        tint = AccentGold,
                        onClick = {
                            showMoreSheet = false
                            onOpenDetails()
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                }

                SheetActionRow(
                    icon = Icons.Default.RemoveDone,
                    label = "Mark Unread (Reset to 0)",
                    tint = TextPrimary,
                    onClick = {
                        showMoreSheet = false
                        onMarkUnread()
                    }
                )

                if (onSyncTrackers != null) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                    SheetActionRow(
                        icon = Icons.Default.Sync,
                        label = "Sync to Trackers",
                        tint = TextPrimary,
                        onClick = {
                            showMoreSheet = false
                            onSyncTrackers()
                        }
                    )
                }

                if (onOpenCategories != null && onHideFromHistory != null) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                    SheetActionRow(
                        icon = Icons.Default.VisibilityOff,
                        label = "Hide from History",
                        tint = TextSecondary,
                        onClick = {
                            showMoreSheet = false
                            onHideFromHistory()
                        }
                    )
                }

                if (onDeleteSelected != null) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                    SheetActionRow(
                        icon = Icons.Default.DeleteOutline,
                        label = "Delete Selected",
                        tint = Color(0xFFF87171),
                        onClick = {
                            showMoreSheet = false
                            onDeleteSelected()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FlushBottomBarAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTint = if (enabled) tint else TextMuted.copy(alpha = 0.35f)
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = activeTint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = if (enabled) (if (tint == AccentGold) AccentGold else TextSecondary) else TextMuted.copy(alpha = 0.35f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Pure #000000 Frameless Multi-Select Set-Categories Sheet (matches Chimanew / Option A).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectCategoriesSheet(
    selectedMediaIds: Set<Long>,
    categories: List<CategoryEntity>,
    categoryAssignments: Map<Long, Set<Long>>,
    onCreateCategory: (String) -> Unit,
    onApplyChanges: (addIds: Set<Long>, removeIds: Set<Long>) -> Unit,
    onOpenManageCategories: () -> Unit,
    onDismiss: () -> Unit
) {
    // 0 = Unchecked, 1 = Checked, 2 = Partial (unchanged)
    val states = remember(selectedMediaIds, categories, categoryAssignments) {
        val map = mutableStateMapOf<Long, Int>()
        categories.forEach { cat ->
            val withCat = selectedMediaIds.count { id -> cat.id in categoryAssignments[id].orEmpty() }
            map[cat.id] = when {
                withCat == 0 -> 0
                withCat == selectedMediaIds.size && selectedMediaIds.isNotEmpty() -> 1
                else -> 2
            }
        }
        map
    }
    var showNewInput by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Set Categories (${selectedMediaIds.size})",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage",
                    color = AccentGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            onDismiss()
                            onOpenManageCategories()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (categories.isEmpty() && !showNewInput) {
                Text(
                    text = "No custom categories yet. Create one below.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    categories.forEachIndexed { idx, cat ->
                        val st = states[cat.id] ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    states[cat.id] = if (st == 1) 0 else 1
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when (st) {
                                            1 -> AccentGold
                                            2 -> AccentGold.copy(alpha = 0.35f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = 1.4.dp,
                                        color = if (st == 0) TextMuted else AccentGold,
                                        shape = RoundedCornerShape(4.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (st == 1) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = OnAccentColor, modifier = Modifier.size(14.dp))
                                } else if (st == 2) {
                                    Box(modifier = Modifier.width(10.dp).height(2.dp).background(TextPrimary))
                                }
                            }
                            Text(
                                text = cat.name,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (idx < categories.lastIndex) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                        }
                    }
                }
            }

            if (showNewInput) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AccentGold),
                        decorationBox = { inner ->
                            if (newName.isEmpty()) Text("New category name...", color = TextMuted, fontSize = 13.5.sp)
                            inner()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp)
                    )
                    Text(
                        text = "Add",
                        color = if (newName.trim().isNotEmpty()) AccentGold else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(enabled = newName.trim().isNotEmpty()) {
                                onCreateCategory(newName.trim())
                                newName = ""
                                showNewInput = false
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                HorizontalDivider(color = AccentGold.copy(alpha = 0.45f), thickness = 0.7.dp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showNewInput) "Cancel" else "+ New Category",
                    color = AccentGold,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            showNewInput = !showNewInput
                            if (!showNewInput) newName = ""
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text("Cancel", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentGold)
                            .clickable {
                                val addIds = states.filterValues { it == 1 }.keys.toSet()
                                val removeIds = states.filterValues { it == 0 }.keys.toSet()
                                onApplyChanges(addIds, removeIds)
                                onDismiss()
                            }
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Text("Apply", color = OnAccentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Corner selection checkmark badge rendered over cards/rows in Multi-Select mode.
 */
@Composable
fun MultiSelectCheckBadge(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) AccentGold else AmoledBlack.copy(alpha = 0.72f))
            .border(
                width = 1.5.dp,
                color = if (selected) AccentGold else Color.White.copy(alpha = 0.65f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = OnAccentColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
