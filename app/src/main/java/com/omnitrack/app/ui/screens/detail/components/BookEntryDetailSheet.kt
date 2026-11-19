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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.db.entity.BookEntryEntity
import com.omnitrack.app.data.model.BookEntryType
import com.omnitrack.app.data.preferences.FallbackBackgroundStyle
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.ui.components.ArtworkBackdrop
import com.omnitrack.app.ui.components.CoverGlowBox
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BookEntryDetailSheet(
    entry: BookEntryEntity,
    presentationSettings: PresentationSettings? = null,
    backdropBitmap: ImageBitmap? = null,
    backdropTargetUrl: String? = null,
    onSave: (BookEntryEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current

    var editedName by remember(entry.id) { mutableStateOf(entry.entryName) }
    var editedType by remember(entry.id) { mutableStateOf(entry.entryType) }
    var editedPages by remember(entry.id) { mutableStateOf(entry.pagesOrLines?.toString() ?: "") }
    var editedTranslator by remember(entry.id) { mutableStateOf(entry.translator ?: "") }
    var editedEdition by remember(entry.id) { mutableStateOf(entry.editionLabel ?: "") }
    var isCompleted by remember(entry.id) { mutableStateOf(entry.isCompleted) }
    var isBookmarked by remember(entry.id) { mutableStateOf(entry.isBookmarked) }
    var note by remember(entry.id) { mutableStateOf(entry.userNote ?: "") }
    var sliderValue by remember(entry.id) {
        mutableFloatStateOf(entry.userRating?.coerceIn(1.0f, 10.0f) ?: 0.9f)
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.24f))
                    )
                }

                // Header + Rating Readout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = editedName.ifBlank { "Reading Entry" },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = if (isUnrated) Color.White.copy(alpha = 0.35f) else AccentGold, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (isUnrated) "Unrated" else String.format(Locale.US, "%.1f", sliderValue),
                            color = if (isUnrated) Color.White.copy(alpha = 0.55f) else AccentGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Rating Slider + Nudgers
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Decrease rating",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(26.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                applySlider(sliderValue - 0.1f)
                            }
                            .padding(3.dp)
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { applySlider(it) },
                        valueRange = 0.9f..10.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentGold,
                            activeTrackColor = AccentGold,
                            inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Increase rating",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(26.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                applySlider(if (isUnrated) 5.0f else sliderValue + 0.1f)
                            }
                            .padding(3.dp)
                    )
                }

                // Entry Type Underline Tabs
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    BookEntryType.entries.forEach { t ->
                        val selected = editedType.equals(t.name, ignoreCase = true)
                        Column(
                            modifier = Modifier.width(IntrinsicSize.Max).clickable { editedType = t.name },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = t.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.48f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) AccentGold else Color.Transparent))
                        }
                    }
                }

                val tfColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AccentGold,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                    focusedLabelColor = AccentGold,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.55f)
                )

                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Part / Canto / Edition Name") },
                    singleLine = true,
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = editedPages,
                        onValueChange = { editedPages = it },
                        label = { Text("Pages or Lines") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = tfColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = editedTranslator,
                        onValueChange = { editedTranslator = it },
                        label = { Text("Translator") },
                        singleLine = true,
                        colors = tfColors,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = editedEdition,
                    onValueChange = { editedEdition = it },
                    label = { Text("Edition / Imprint Label") },
                    singleLine = true,
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Completed", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = isCompleted,
                        onCheckedChange = { isCompleted = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = AmoledBlack, checkedTrackColor = AccentJade)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bookmarked / Favorite", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = isBookmarked,
                        onCheckedChange = { isBookmarked = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = AmoledBlack, checkedTrackColor = AccentGold)
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Reading notes & reflections") },
                    minLines = 3,
                    maxLines = 6,
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            onDelete(entry.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444).copy(alpha = 0.16f),
                            contentColor = Color(0xFFFCA5A5)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val updated = entry.copy(
                                entryName = editedName.trim().ifBlank { "Untitled Entry" },
                                entryType = editedType,
                                pagesOrLines = editedPages.trim().toIntOrNull(),
                                translator = editedTranslator.trim().takeIf { it.isNotBlank() },
                                editionLabel = editedEdition.trim().takeIf { it.isNotBlank() },
                                isCompleted = isCompleted,
                                completedAtMs = if (isCompleted) (entry.completedAtMs ?: System.currentTimeMillis()) else null,
                                userRating = currentRating,
                                isBookmarked = isBookmarked,
                                userNote = note.trim().takeIf { it.isNotBlank() }
                            )
                            onSave(updated)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGold,
                            contentColor = AmoledBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(2f)
                    ) {
                        Text("Save Entry", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
