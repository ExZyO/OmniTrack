package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.db.entity.BookQuoteEntity
import com.omnitrack.app.ui.theme.AccentGold

@Composable
fun DetailBookQuotesSection(
    quotes: List<BookQuoteEntity>,
    poemSampleLines: List<String>,
    onAddQuote: (String, String?, String?) -> Unit,
    onSaveQuote: (BookQuoteEntity) -> Unit,
    onToggleFavorite: (BookQuoteEntity) -> Unit,
    onDeleteQuote: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingQuote by remember { mutableStateOf<BookQuoteEntity?>(null) }
    var showPoemLines by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (quotes.isNotEmpty()) "QUOTES & JOURNAL (${quotes.size})" else "QUOTES & JOURNAL",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.60f),
                letterSpacing = 1.1.sp
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showAddDialog = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = AccentGold, modifier = Modifier.size(15.dp))
                Text("Save Quote", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGold)
            }
        }

        // Collapsible Opening Verse Preview from PoetryDB
        if (poemSampleLines.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPoemLines = !showPoemLines }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showPoemLines) "Hide Opening Stanzas" else "Read Opening Lines (${poemSampleLines.size} lines)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentGold
                    )
                    Icon(
                        imageVector = if (showPoemLines) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (showPoemLines) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.24f))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = poemSampleLines.joinToString("\n"),
                            fontFamily = FontFamily.Serif,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                    HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.08f))
                }
            }
        }

        if (quotes.isEmpty()) {
            Text(
                text = "Save memorable lines, canto notes, or personal reflections here.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.45f)
            )
        } else {
            quotes.forEachIndexed { idx, q ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingQuote = q }
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(if (q.isFavorite) AccentGold else Color.White.copy(alpha = 0.30f))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "“${q.quoteText}”",
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontSize = 14.5.sp,
                                lineHeight = 22.sp,
                                color = Color.White
                            )
                            if (!q.locationRef.isNullOrBlank()) {
                                Text(
                                    text = q.locationRef,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentGold
                                )
                            }
                            if (!q.personalNote.isNullOrBlank()) {
                                Text(
                                    text = q.personalNote,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                    color = Color.White.copy(alpha = 0.68f)
                                )
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (q.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Toggle favorite",
                                tint = if (q.isFavorite) AccentGold else Color.White.copy(alpha = 0.38f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { onToggleFavorite(q) }
                            )
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete quote",
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onDeleteQuote(q.id) }
                            )
                        }
                    }
                }
                if (idx < quotes.lastIndex) {
                    HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.07f))
                }
            }
        }
    }

    if (showAddDialog || editingQuote != null) {
        val current = editingQuote
        var quoteInput by remember(current?.id) { mutableStateOf(current?.quoteText.orEmpty()) }
        var locInput by remember(current?.id) { mutableStateOf(current?.locationRef.orEmpty()) }
        var noteInput by remember(current?.id) { mutableStateOf(current?.personalNote.orEmpty()) }
        val tfColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = AccentGold,
            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
        )
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingQuote = null
            },
            containerColor = Color(0xFF141417),
            title = {
                Text(
                    text = if (current == null) "Save Book Quote" else "Edit Book Quote",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = quoteInput,
                        onValueChange = { quoteInput = it },
                        label = { Text("Quote or passage") },
                        minLines = 3,
                        maxLines = 6,
                        colors = tfColors
                    )
                    OutlinedTextField(
                        value = locInput,
                        onValueChange = { locInput = it },
                        label = { Text("Location (e.g. Book I, p. 42, 3h 14m)") },
                        singleLine = true,
                        colors = tfColors
                    )
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Personal note (optional)") },
                        minLines = 2,
                        maxLines = 4,
                        colors = tfColors
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (quoteInput.isNotBlank()) {
                            if (current == null) {
                                onAddQuote(quoteInput, locInput, noteInput)
                            } else {
                                onSaveQuote(
                                    current.copy(
                                        quoteText = quoteInput,
                                        locationRef = locInput,
                                        personalNote = noteInput
                                    )
                                )
                            }
                        }
                        showAddDialog = false
                        editingQuote = null
                    }
                ) {
                    Text("Save", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        editingQuote = null
                    }
                ) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.65f))
                }
            }
        )
    }
}
