package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.VnRouteEntity
import com.omnitrack.app.data.model.VnDetailMeta
import com.omnitrack.app.data.model.VnSpoilerLevel
import com.omnitrack.app.ui.components.repeatingClickable
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import java.util.Locale
import kotlin.math.roundToInt

val VN_ROUTE_TYPES: List<Pair<String, String>> = listOf(
    "HEROINE" to "Heroine",
    "TRUE" to "True End",
    "COMMON" to "Common",
    "NORMAL" to "Normal",
    "BAD" to "Bad End",
    "EXTRA" to "Extra",
    "CUSTOM" to "Custom"
)

@Composable
fun DetailVnRoutesSection(
    item: MediaItemEntity,
    vnMeta: VnDetailMeta,
    routes: List<VnRouteEntity>,
    spoilerLevel: VnSpoilerLevel,
    onStepPlaytime: (Double) -> Unit,
    onSetExactPlaytime: (Double) -> Unit,
    onToggleRouteCompleted: (VnRouteEntity) -> Unit,
    onAddCustomRoute: (routeName: String, routeType: String, playtimeHours: Double?, isSpoiler: Boolean, photo: VnRoutePhotoSelection) -> Unit,
    onUpdateRoutePhoto: ((VnRouteEntity, VnRoutePhotoSelection) -> Unit)? = null,
    onOpenRouteDetail: (VnRouteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPlaytimeBlock by remember(item.id) { mutableStateOf(true) }
    var showAddBar by remember { mutableStateOf(false) }
    var newRouteName by remember { mutableStateOf("") }
    var newRouteType by remember { mutableStateOf("HEROINE") }
    var newRouteHours by remember { mutableStateOf("") }
    var newRouteSpoiler by remember { mutableStateOf(false) }
    var newRoutePhoto by remember { mutableStateOf(VnRoutePhotoSelection(null)) }
    var showNewRoutePhotoPicker by remember { mutableStateOf(false) }
    var editingPhotoRoute by remember { mutableStateOf<VnRouteEntity?>(null) }
    var showHoursDialog by remember { mutableStateOf(false) }
    var hoursInput by remember(item.playtimeHours) {
        mutableStateOf(String.format(Locale.US, "%.1f", item.playtimeHours))
    }
    var revealedSpoilerRouteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val estimatedMinutes = vnMeta.lengthMinutes ?: item.runtimeMinutes ?: 0
    val estimatedHours = if (estimatedMinutes > 0) estimatedMinutes / 60.0 else 0.0
    val pctText = if (estimatedHours > 0.0) {
        val pct = ((item.playtimeHours / estimatedHours) * 100.0).roundToInt().coerceIn(0, 100)
        val votesStr = vnMeta.lengthVotes?.let { " \u00B7 $it votes" }.orEmpty()
        "$pct%$votesStr"
    } else ""

    fun submitNewRoute() {
        val trimmed = newRouteName.trim()
        if (trimmed.isEmpty()) return
        val parsedHours = newRouteHours.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
        onAddCustomRoute(trimmed, newRouteType, parsedHours, newRouteSpoiler, newRoutePhoto)
        newRouteName = ""
        newRouteHours = ""
        newRouteSpoiler = false
        newRoutePhoto = VnRoutePhotoSelection(null)
        showAddBar = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Playtime & Length (Optional / Collapsible directly on blurred backdrop, zero box)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Playtime & Length",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp
                )
                if (!showPlaytimeBlock && item.playtimeHours > 0.0) {
                    Text(
                        text = String.format(Locale.US, "%.1fh", item.playtimeHours),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentGold
                    )
                } else if (showPlaytimeBlock && pctText.isNotEmpty()) {
                    Text(
                        text = pctText,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Text(
                text = if (showPlaytimeBlock) "Hide" else "Show",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier
                    .clickable { showPlaytimeBlock = !showPlaytimeBlock }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        if (showPlaytimeBlock) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.clickable {
                        hoursInput = String.format(Locale.US, "%.1f", item.playtimeHours)
                        showHoursDialog = true
                    },
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1fh", item.playtimeHours),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    if (estimatedHours > 0.0) {
                        Text(
                            text = String.format(Locale.US, "/ ~%.1fh avg", estimatedHours),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }

                // Bare typographic steppers (zero pill buttons)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\u22120.5h",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .repeatingClickable { onStepPlaytime(-0.5) }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "+0.5h",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.90f),
                        modifier = Modifier
                            .repeatingClickable { onStepPlaytime(0.5) }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "+1h",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold,
                        modifier = Modifier
                            .repeatingClickable { onStepPlaytime(1.0) }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
            }

            if (estimatedHours > 0.0) {
                val fraction = (item.playtimeHours / estimatedHours).toFloat().coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .background(if (fraction >= 1f) AccentGold else Color.White)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. Routes & Endings Checklist (Hairline open rows, hollow Nuvio check ring)
        val completedCount = routes.count { it.isCompleted }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Routes & Endings",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp
                )
                if (routes.isNotEmpty()) {
                    Text(
                        text = "($completedCount / ${routes.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }
            Text(
                text = if (showAddBar) "Close" else "+ Add Route",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold,
                modifier = Modifier
                    .clickable { showAddBar = !showAddBar }
                    .padding(vertical = 2.dp)
            )
        }

        if (showAddBar) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VnRoutePhotoThumb(
                        imageUrl = newRoutePhoto.imageUrl,
                        focalX = newRoutePhoto.focalX,
                        focalY = newRoutePhoto.focalY,
                        zoom = newRoutePhoto.zoom,
                        contentDescription = "Route Photo",
                        size = 44.dp,
                        cornerRadius = 10.dp,
                        fallbackText = "+",
                        modifier = Modifier.clickable { showNewRoutePhotoPicker = true }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = newRouteName,
                            onValueChange = { newRouteName = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                            cursorBrush = SolidColor(AccentGold),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitNewRoute() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            decorationBox = { inner ->
                                if (newRouteName.isEmpty()) {
                                    Text(
                                        text = "Route or ending name...",
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 13.sp
                                    )
                                }
                                inner()
                            }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.8.dp)
                                .background(Color.White.copy(alpha = 0.24f))
                        )
                    }
                    Text(
                        text = "Add",
                        color = if (newRouteName.isNotBlank()) AccentGold else Color.White.copy(alpha = 0.35f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(enabled = newRouteName.isNotBlank()) { submitNewRoute() }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }

                // Underline Route Type Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    VN_ROUTE_TYPES.forEach { (key, label) ->
                        val selected = newRouteType == key
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .clickable { newRouteType = key },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                color = if (selected) AccentGold else Color.White.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(if (selected) AccentGold else Color.Transparent)
                            )
                        }
                    }
                }

                // Optional hours + Spoiler toggle (strictly optional, hidden when blank)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = newRouteHours,
                            onValueChange = { next ->
                                if (next.length <= 6 && next.all { it.isDigit() || it == '.' }) {
                                    newRouteHours = next
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                            cursorBrush = SolidColor(AccentGold),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submitNewRoute() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            decorationBox = { inner ->
                                if (newRouteHours.isEmpty()) {
                                    Text(
                                        text = "Hours (optional, leave blank to hide)",
                                        color = Color.White.copy(alpha = 0.42f),
                                        fontSize = 12.sp
                                    )
                                }
                                inner()
                            }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.6.dp)
                                .background(Color.White.copy(alpha = 0.16f))
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clickable { newRouteSpoiler = !newRouteSpoiler }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(if (newRouteSpoiler) AccentGold.copy(alpha = 0.22f) else Color.Transparent)
                                .border(
                                    width = 1.2.dp,
                                    color = if (newRouteSpoiler) AccentGold else Color.White.copy(alpha = 0.48f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (newRouteSpoiler) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AccentGold,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                        Text(
                            text = "Spoiler",
                            fontSize = 12.sp,
                            fontWeight = if (newRouteSpoiler) FontWeight.Bold else FontWeight.Medium,
                            color = if (newRouteSpoiler) AccentGold else Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
            }
        }

        if (routes.isEmpty() && !showAddBar) {
            Text(
                text = "No routes added yet \u00B7 Tap + Add Route to track routes & endings",
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.56f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAddBar = true }
                    .padding(vertical = 6.dp)
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            routes.forEachIndexed { index, route ->
                val isSpoilerHidden = route.isSpoiler &&
                    spoilerLevel.level < VnSpoilerLevel.ALL.level &&
                    route.id !in revealedSpoilerRouteIds

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSpoilerHidden) {
                                revealedSpoilerRouteIds = revealedSpoilerRouteIds + route.id
                            } else {
                                onOpenRouteDetail(route)
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VnRoutePhotoThumb(
                        imageUrl = route.characterImageUrl,
                        focalX = route.imageFocalX,
                        focalY = route.imageFocalY,
                        zoom = route.imageZoom,
                        contentDescription = route.routeName,
                        size = 52.dp,
                        cornerRadius = 10.dp,
                        isBlurred = isSpoilerHidden,
                        fallbackText = formatRouteTypeLabel(route.routeType).firstOrNull()?.toString() ?: "R",
                        modifier = Modifier.clickable(enabled = !isSpoilerHidden && onUpdateRoutePhoto != null) {
                            editingPhotoRoute = route
                        }
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSpoilerHidden) "${route.routeName} (Tap to reveal)" else route.routeName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (isSpoilerHidden) Modifier.blur(7.dp) else Modifier
                        )

                        val subParts = buildList {
                            add(formatRouteTypeLabel(route.routeType))
                            route.playtimeHours?.takeIf { it > 0.0 }?.let { h ->
                                val hStr = if (h % 1.0 == 0.0) "${h.toInt()}h" else String.format(Locale.US, "%.1fh", h)
                                add(hStr)
                            }
                            route.userRating?.let { add(String.format(Locale.US, "\u2605 %.1f", it)) }
                            if (route.isBookmarked) add("Bookmarked")
                            if (!route.userNote.isNullOrBlank()) add("Note")
                        }
                        Text(
                            text = subParts.joinToString(" \u00B7 "),
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (isSpoilerHidden) Modifier.blur(6.dp) else Modifier
                        )
                    }

                    // Nuvio hollow stroked checkmark ring
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (route.isCompleted) AccentGold.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.22f))
                            .border(
                                width = 1.4.dp,
                                color = if (route.isCompleted) AccentGold else Color.White.copy(alpha = 0.72f),
                                shape = CircleShape
                            )
                            .clickable { onToggleRouteCompleted(route) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (route.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = AccentGold,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (index < routes.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.6.dp)
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                }
            }
        }
    }

    if (showHoursDialog) {
        AlertDialog(
            onDismissRequest = { showHoursDialog = false },
            containerColor = AmoledBlack,
            title = {
                Text("Playtime (Hours)", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = hoursInput,
                    onValueChange = { next ->
                        if (next.length <= 7 && next.all { it.isDigit() || it == '.' }) {
                            hoursInput = next
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentGold,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = hoursInput.toDoubleOrNull()
                    if (parsed != null) onSetExactPlaytime(parsed)
                    showHoursDialog = false
                }) {
                    Text("Save", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHoursDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    if (showNewRoutePhotoPicker) {
        VnRoutePhotoPickerSheet(
            routeTitle = newRouteName.ifBlank { "New Route" },
            initialSelection = newRoutePhoto,
            mediaItem = item,
            vnMeta = vnMeta,
            onApply = { sel -> newRoutePhoto = sel },
            onDismiss = { showNewRoutePhotoPicker = false }
        )
    }

    val photoTarget = editingPhotoRoute
    if (photoTarget != null && onUpdateRoutePhoto != null) {
        VnRoutePhotoPickerSheet(
            routeTitle = photoTarget.routeName,
            initialSelection = VnRoutePhotoSelection(
                imageUrl = photoTarget.characterImageUrl,
                focalX = photoTarget.imageFocalX,
                focalY = photoTarget.imageFocalY,
                zoom = photoTarget.imageZoom
            ),
            mediaItem = item,
            vnMeta = vnMeta,
            onApply = { sel -> onUpdateRoutePhoto(photoTarget, sel) },
            onDismiss = { editingPhotoRoute = null }
        )
    }
}

fun formatRouteTypeLabel(type: String): String = when (type.uppercase()) {
    "COMMON" -> "Common"
    "HEROINE" -> "Heroine"
    "TRUE" -> "True End"
    "NORMAL" -> "Normal"
    "BAD" -> "Bad End"
    "EXTRA" -> "Extra"
    else -> "Custom"
}
