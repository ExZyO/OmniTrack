package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.VnRouteEntity
import com.omnitrack.app.data.model.VnDetailMeta
import com.omnitrack.app.data.preferences.FallbackBackgroundStyle
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.ui.components.ArtworkBackdrop
import com.omnitrack.app.ui.components.CoverGlowBox
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.OnAccentColor
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun VnRouteDetailSheet(
    route: VnRouteEntity,
    mediaItem: MediaItemEntity? = null,
    vnMeta: VnDetailMeta? = null,
    presentationSettings: PresentationSettings? = null,
    backdropBitmap: ImageBitmap? = null,
    backdropTargetUrl: String? = null,
    onSave: (VnRouteEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current

    var editedName by remember(route.id) { mutableStateOf(route.routeName) }
    var editedType by remember(route.id) { mutableStateOf(route.routeType) }
    var photoSelection by remember(route.id) {
        mutableStateOf(
            VnRoutePhotoSelection(
                imageUrl = route.characterImageUrl,
                focalX = route.imageFocalX,
                focalY = route.imageFocalY,
                zoom = route.imageZoom
            )
        )
    }
    var showPhotoPicker by remember { mutableStateOf(false) }
    var isCompleted by remember(route.id) { mutableStateOf(route.isCompleted) }
    var isBookmarked by remember(route.id) { mutableStateOf(route.isBookmarked) }
    var isSpoiler by remember(route.id) { mutableStateOf(route.isSpoiler) }
    var note by remember(route.id) { mutableStateOf(route.userNote ?: "") }
    var hoursText by remember(route.id) {
        mutableStateOf(
            route.playtimeHours?.takeIf { it > 0.0 }?.let { h ->
                if (h % 1.0 == 0.0) h.toInt().toString() else String.format(Locale.US, "%.1f", h)
            }.orEmpty()
        )
    }
    var sliderValue by remember(route.id) {
        mutableFloatStateOf(route.userRating?.coerceIn(1.0f, 10.0f) ?: 0.9f)
    }

    val isUnrated = sliderValue < 0.95f
    val currentRating: Float? = if (isUnrated) null else sliderValue

    fun applySlider(next: Float) {
        sliderValue = if (next < 0.95f) 0.9f
        else ((next.coerceIn(1.0f, 10.0f) * 10f).roundToInt()) / 10f
    }

    InWindowBottomSheet(
        onDismiss = onDismiss,
        isAtTop = { scrollState.value == 0 },
        wrapHeightMax = 680.dp,
        showDragHandle = false
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(max = 680.dp)
        ) {
            when (presentationSettings?.fallbackBackgroundStyle) {
                FallbackBackgroundStyle.CINEMATIC_BLUR -> {
                    ArtworkBackdrop(image = backdropBitmap, modifier = Modifier.matchParentSize())
                    Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.56f)))
                }
                FallbackBackgroundStyle.DOMINANT_COLOR -> {
                    CoverGlowBox(imageUrl = backdropTargetUrl, modifier = Modifier.matchParentSize()) {}
                    Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.56f)))
                }
                else -> {
                    Box(modifier = Modifier.matchParentSize().background(AmoledBlack))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 22.dp, vertical = 18.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Drag handle
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.24f))
                    )
                }

                // Header with cropped route photo thumbnail & edit action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VnRoutePhotoThumb(
                        imageUrl = photoSelection.imageUrl,
                        focalX = photoSelection.focalX,
                        focalY = photoSelection.focalY,
                        zoom = photoSelection.zoom,
                        contentDescription = editedName,
                        size = 64.dp,
                        cornerRadius = 12.dp,
                        fallbackText = "+",
                        modifier = Modifier.clickable { showPhotoPicker = true }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = editedName.ifBlank { "Route Details" },
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatRouteTypeLabel(editedType)} Route",
                                color = Color.White.copy(alpha = 0.68f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (photoSelection.imageUrl.isNullOrBlank()) "+ Add Photo" else "Edit Photo",
                                color = AccentGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { showPhotoPicker = true }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                    if (!isUnrated || route.userRating != null) {
                        Text(
                            text = "Remove Rating",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { applySlider(0.9f) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Exact Borderless Score Hero + Unrated..10.0 Slider (matches ScoreRatingSheet / clean_native_rating_ui.html)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RepeatingHoldButton(
                        onStep = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val cur = currentRating ?: 1.0f
                            applySlider(if (cur <= 1.04f) 0.9f else cur - 0.1f)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.09f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease Rating",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (isUnrated) Color.White.copy(alpha = 0.25f) else AccentGold,
                            modifier = Modifier
                                .size(26.dp)
                                .padding(bottom = 5.dp)
                        )
                        Text(
                            text = if (isUnrated) "Unrated" else String.format(Locale.US, "%.1f", currentRating),
                            color = if (isUnrated) Color.White.copy(alpha = 0.45f) else Color.White,
                            fontSize = if (isUnrated) 30.sp else 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.2).sp
                        )
                        if (!isUnrated) {
                            Text(
                                text = "/ 10",
                                color = Color.White.copy(alpha = 0.36f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }

                    RepeatingHoldButton(
                        onStep = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val next = if (currentRating == null) 1.0f else currentRating + 0.1f
                            applySlider(next)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AccentGold.copy(alpha = 0.18f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase Rating",
                            tint = AccentGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { applySlider(it) },
                        valueRange = 0.9f..10.0f,
                        steps = 90,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = if (isUnrated) Color.White.copy(alpha = 0.25f) else AccentGold,
                            inactiveTrackColor = Color.White.copy(alpha = 0.14f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("Unrated" to 0.9f, "1.0" to 1.0f, "5.0" to 5.0f, "10.0" to 10.0f).forEach { (label, v) ->
                            Text(
                                text = label,
                                color = Color.White.copy(alpha = 0.60f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { applySlider(v) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(-1.0f, -0.5f, -0.1f, 0.1f, 0.5f, 1.0f).forEach { d ->
                        val isPos = d > 0f
                        val label = if (isPos) "+${String.format(Locale.US, "%.1f", d)}" else String.format(Locale.US, "%.1f", d)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isPos) AccentGold.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f))
                                .clickable {
                                    val base = currentRating
                                    val next = if (base == null) (if (d > 0f) 1.0f else 0.9f) else base + d
                                    applySlider(if (next < 1.0f) 0.9f else next)
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isPos) AccentGold else Color.White.copy(alpha = 0.80f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Route Type Underline Selector (Zero Pills)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    VN_ROUTE_TYPES.forEach { (key, label) ->
                        val selected = editedType.equals(key, ignoreCase = true)
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .clickable { editedType = key },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                color = if (selected) AccentGold else Color.White.copy(alpha = 0.50f),
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

                // Native Android Hairline Switches (Zero pill buttons)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                    RouteSwitchRow("Mark Route Completed", isCompleted) { isCompleted = it }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                    RouteSwitchRow("Bookmark Route", isBookmarked) { isBookmarked = it }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                    RouteSwitchRow("Mask Route Name as Spoiler", isSpoiler) { isSpoiler = it }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                }

                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Route Name", color = Color.White.copy(alpha = 0.65f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentGold,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.14f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Optional Route Hours (leave blank to hide hours beside this route)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { next ->
                            if (next.length <= 6 && next.all { it.isDigit() || it == '.' }) {
                                hoursText = next
                            }
                        },
                        label = { Text("Route Hours (optional)", color = Color.White.copy(alpha = 0.65f)) },
                        placeholder = { Text("Leave blank to hide", color = Color.White.copy(alpha = 0.38f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.14f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "+0.5h",
                        color = AccentGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                val next = (hoursText.trim().toDoubleOrNull() ?: 0.0) + 0.5
                                hoursText = String.format(Locale.US, "%.1f", next)
                            }
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                    Text(
                        text = "+1h",
                        color = AccentGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                val next = (hoursText.trim().toDoubleOrNull() ?: 0.0) + 1.0
                                hoursText = String.format(Locale.US, "%.1f", next)
                            }
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                    if (hoursText.isNotEmpty()) {
                        Text(
                            text = "Clear",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable { hoursText = "" }
                                .padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Add personal thoughts on this route or ending...", color = Color.White.copy(alpha = 0.45f)) },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentGold,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.14f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onDelete(route.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.09f),
                            contentColor = Color(0xFFEF4444)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            onSave(
                                route.copy(
                                    routeName = editedName.trim().ifBlank { route.routeName },
                                    routeType = editedType,
                                    characterImageUrl = photoSelection.imageUrl,
                                    imageFocalX = photoSelection.focalX,
                                    imageFocalY = photoSelection.focalY,
                                    imageZoom = photoSelection.zoom,
                                    isCompleted = isCompleted,
                                    completedAtMs = if (isCompleted) (route.completedAtMs ?: System.currentTimeMillis()) else null,
                                    userRating = currentRating,
                                    userNote = note.trim().takeIf { it.isNotBlank() },
                                    isBookmarked = isBookmarked,
                                    isSpoiler = isSpoiler,
                                    playtimeHours = hoursText.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
                                )
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGold,
                            contentColor = OnAccentColor
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Text("Save Route", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }

    if (showPhotoPicker) {
        VnRoutePhotoPickerSheet(
            routeTitle = editedName.ifBlank { route.routeName },
            initialSelection = photoSelection,
            mediaItem = mediaItem,
            vnMeta = vnMeta,
            onApply = { sel -> photoSelection = sel },
            onDismiss = { showPhotoPicker = false }
        )
    }
}

@Composable
private fun RouteSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnAccentColor,
                checkedTrackColor = AccentGold,
                uncheckedThumbColor = Color.White.copy(alpha = 0.78f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.16f)
            )
        )
    }
}
