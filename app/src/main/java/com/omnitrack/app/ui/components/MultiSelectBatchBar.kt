package com.omnitrack.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * Top selection bar for Chimanew-style Multi-Select mode:
 * Shows selected count + Select All, Invert, Deselect All (Clear), and Close.
 */
@Composable
fun MultiSelectTopBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onExitMultiSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceCard)
            .border(0.6.dp, AccentGold.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onExitMultiSelect,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit Selection",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = "$selectedCount / $totalCount Selected",
                color = AccentGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MultiSelectPillButton(
                label = "All",
                active = selectedCount == totalCount && totalCount > 0,
                onClick = onSelectAll
            )
            MultiSelectPillButton(
                label = "Invert",
                active = false,
                onClick = onInvertSelection
            )
            MultiSelectPillButton(
                label = "Clear",
                active = false,
                onClick = onClearSelection
            )
        }
    }
}

@Composable
private fun MultiSelectPillButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AccentGold.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.07f))
            .border(
                0.7.dp,
                if (active) AccentGold else Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (active) AccentGold else TextPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Floating bottom Batch Action Dock for Library & History multi-select mode.
 */
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
    modifier: Modifier = Modifier
) {
    var showStatusRow by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AmoledBlack.copy(alpha = 0.96f))
            .border(1.dp, AccentGold.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(visible = showStatusRow && onSetStatus != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    TrackingStatus.IN_PROGRESS to "Active",
                    TrackingStatus.COMPLETED to "Completed",
                    TrackingStatus.PLAN_TO_TRACK to "Planned",
                    TrackingStatus.ON_HOLD to "On Hold",
                    TrackingStatus.DROPPED to "Dropped"
                ).forEach { (st, lbl) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentGold.copy(alpha = 0.16f))
                            .border(0.7.dp, AccentGold.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .clickable {
                                showStatusRow = false
                                onSetStatus?.invoke(st)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = lbl,
                            color = AccentGold,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BatchActionChip(
                icon = Icons.Default.DoneAll,
                label = "Mark Read",
                tint = AccentJade,
                enabled = selectedCount > 0,
                onClick = onMarkCompleted
            )
            BatchActionChip(
                icon = Icons.Default.RemoveDone,
                label = "Mark Unread",
                tint = TextPrimary,
                enabled = selectedCount > 0,
                onClick = onMarkUnread
            )
            if (onIncrementProgress != null) {
                BatchActionChip(
                    icon = Icons.Default.Add,
                    label = "+1 Progress",
                    tint = AccentGold,
                    enabled = selectedCount > 0,
                    onClick = onIncrementProgress
                )
            }
            if (onSetStatus != null) {
                BatchActionChip(
                    icon = Icons.Default.Tune,
                    label = "Status",
                    tint = if (showStatusRow) AccentGold else TextPrimary,
                    enabled = selectedCount > 0,
                    onClick = { showStatusRow = !showStatusRow }
                )
            }
            if (onSyncTrackers != null) {
                BatchActionChip(
                    icon = Icons.Default.Sync,
                    label = "Sync",
                    tint = AccentGold,
                    enabled = selectedCount > 0,
                    onClick = onSyncTrackers
                )
            }
            if (onHideFromHistory != null) {
                BatchActionChip(
                    icon = Icons.Default.VisibilityOff,
                    label = "Hide History",
                    tint = TextSecondary,
                    enabled = selectedCount > 0,
                    onClick = onHideFromHistory
                )
            }
            if (onDeleteSelected != null) {
                BatchActionChip(
                    icon = Icons.Default.DeleteOutline,
                    label = "Delete",
                    tint = Color(0xFFF87171),
                    enabled = selectedCount > 0,
                    onClick = onDeleteSelected
                )
            }
        }
    }
}

@Composable
private fun BatchActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceElevated.copy(alpha = if (enabled) 1f else 0.45f))
            .border(0.7.dp, tint.copy(alpha = if (enabled) 0.45f else 0.15f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else TextSecondary.copy(alpha = 0.45f),
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            color = if (enabled) tint else TextSecondary.copy(alpha = 0.45f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
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
            .size(24.dp)
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
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
