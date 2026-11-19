package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.R
import com.omnitrack.app.data.db.entity.BookEntryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.BookDetailMeta
import com.omnitrack.app.data.model.BookEntryType
import com.omnitrack.app.data.model.BookFormat
import com.omnitrack.app.data.model.BookTrackingMode
import com.omnitrack.app.data.model.formatBookDuration
import com.omnitrack.app.data.model.formatEstimatedReadTime
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import java.util.Locale
import kotlin.math.roundToInt

private fun iconForBookFormat(format: BookFormat): Int? = when (format) {
    BookFormat.HARDCOVER, BookFormat.PAPERBACK, BookFormat.ANNOTATED, BookFormat.BILINGUAL -> R.drawable.ic_book_hardcover
    BookFormat.EBOOK -> R.drawable.ic_book_ebook
    BookFormat.AUDIOBOOK -> R.drawable.ic_book_audiobook
}

@Composable
fun DetailBookTrackingSection(
    item: MediaItemEntity,
    bookDetailMeta: BookDetailMeta,
    entries: List<BookEntryEntity>,
    onStepProgress: (Int) -> Unit,
    onSetProgressExact: (Double, Double?) -> Unit,
    onSwitchMode: (BookTrackingMode) -> Unit,
    onUpdateFormat: (BookFormat) -> Unit,
    onUpdateAudioMinutes: (Int, Int?) -> Unit,
    onToggleEntryCompleted: (BookEntryEntity) -> Unit,
    onAddEntry: (String, BookEntryType) -> Unit,
    onOpenEntryDetail: (BookEntryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeMode = remember(item.bookTrackingMode) { BookTrackingMode.fromName(item.bookTrackingMode) }
    val activeFormat = remember(item.bookFormat) { BookFormat.fromName(item.bookFormat) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var showAddEntryDialog by remember { mutableStateOf(false) }

    val totalPages = item.totalProgress?.takeIf { it > 0.0 } ?: bookDetailMeta.pageCount?.toDouble()
    val totalAudioMins = item.bookTotalAudioMinutes ?: bookDetailMeta.audioDurationMinutes
    val currentAudioMins = item.bookAudioMinutes ?: 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Mode Switcher (Pages | % | Audiobook)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookTrackingMode.entries.forEach { mode ->
                val selected = activeMode == mode
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onSwitchMode(mode) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = mode.label,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.48f),
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) AccentGold else Color.Transparent)
                    )
                }
            }
        }

        // 2. Hero Numeric Readout & Typographic Steppers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showProgressDialog = true }
            ) {
                when (activeMode) {
                    BookTrackingMode.AUDIO_MINUTES -> {
                        val curStr = formatBookDuration(currentAudioMins) ?: "0m"
                        val totStr = formatBookDuration(totalAudioMins)
                        Text(
                            text = if (totStr != null) "$curStr / $totStr" else curStr,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap to edit time listened",
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.50f)
                        )
                    }
                    BookTrackingMode.PERCENT -> {
                        val pct = when {
                            totalPages != null && totalPages > 0.0 ->
                                ((item.currentProgress / totalPages) * 100.0).roundToInt().coerceIn(0, 100)
                            else -> item.currentProgress.roundToInt().coerceIn(0, 100)
                        }
                        Text(
                            text = if (totalPages != null && totalPages > 0.0) {
                                "$pct% · ${item.currentProgress.toInt()} / ${totalPages.toInt()} pg"
                            } else {
                                "$pct% read"
                            },
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap to set exact percentage",
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.50f)
                        )
                    }
                    BookTrackingMode.PAGES -> {
                        val cur = item.currentProgress.toInt()
                        val tot = totalPages?.toInt()
                        val pctSuffix = if (tot != null && tot > 0) " (${((cur.toDouble() / tot) * 100.0).roundToInt().coerceIn(0, 100)}%)" else ""
                        Text(
                            text = if (tot != null && tot > 0) "$cur / $tot pages$pctSuffix" else "$cur pages",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap to enter exact page",
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.50f)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val steps = when (activeMode) {
                    BookTrackingMode.AUDIO_MINUTES -> listOf(-15 to "−15m", 15 to "+15m", 60 to "+1h")
                    BookTrackingMode.PERCENT -> listOf(-5 to "−5%", 5 to "+5%", 10 to "+10%")
                    BookTrackingMode.PAGES -> listOf(-1 to "−1", 10 to "+10", 25 to "+25")
                }
                steps.forEachIndexed { idx, (delta, label) ->
                    Text(
                        text = label,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (idx == steps.lastIndex) AccentGold else Color.White.copy(alpha = if (delta < 0) 0.72f else 1f),
                        modifier = Modifier
                            .clickable { onStepProgress(delta) }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }
            }
        }

        // 3. Slim Progress Bar
        val fraction = when (activeMode) {
            BookTrackingMode.AUDIO_MINUTES -> {
                if (totalAudioMins != null && totalAudioMins > 0) (currentAudioMins.toFloat() / totalAudioMins.toFloat()).coerceIn(0f, 1f) else 0f
            }
            BookTrackingMode.PERCENT -> {
                if (totalPages != null && totalPages > 0.0) (item.currentProgress / totalPages).toFloat().coerceIn(0f, 1f)
                else (item.currentProgress / 100.0).toFloat().coerceIn(0f, 1f)
            }
            BookTrackingMode.PAGES -> {
                if (totalPages != null && totalPages > 0.0) (item.currentProgress / totalPages).toFloat().coerceIn(0f, 1f) else 0f
            }
        }
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
                    .fillMaxHeight()
                    .background(if (fraction >= 1f) AccentJade else AccentGold)
            )
        }

        // 4. 3-Column Hairline Spec Strip (Read Pace / Length / Audio or Edition)
        val col1Val = formatEstimatedReadTime(totalPages?.toInt()) ?: "—"
        val col2Val = when {
            bookDetailMeta.lineCount != null && bookDetailMeta.lineCount > 0 ->
                "${String.format(Locale.US, "%,d", bookDetailMeta.lineCount)} lines"
            totalPages != null && totalPages > 0 -> "${totalPages.toInt()} pg"
            else -> "—"
        }
        val col3Val = formatBookDuration(totalAudioMins)
            ?: item.bookTranslator?.take(16)
            ?: bookDetailMeta.originalLanguage
            ?: "—"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("EST. READ TIME", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.45f), letterSpacing = 0.8.sp)
                Spacer(Modifier.height(2.dp))
                Text(col1Val, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.10f)))
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    if (bookDetailMeta.lineCount != null) "VERSE LINES" else "LENGTH",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.45f),
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(col2Val, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.10f)))
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    when {
                        totalAudioMins != null -> "AUDIOBOOK"
                        !item.bookTranslator.isNullOrBlank() -> "TRANSLATOR"
                        else -> "ORIGIN"
                    },
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.45f),
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(col3Val, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // 5. Edition Format Underline Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookFormat.entries.forEach { fmt ->
                val selected = activeFormat == fmt
                val iconRes = iconForBookFormat(fmt)
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onUpdateFormat(fmt) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (iconRes != null) {
                            Image(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                colorFilter = ColorFilter.tint(
                                    if (selected) Color.White else Color.White.copy(alpha = 0.45f)
                                )
                            )
                        }
                        Text(
                            text = fmt.label,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.48f),
                            fontSize = 12.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) Color.White else Color.Transparent)
                    )
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.08f))

        // 6. Parts, Cantos, Acts & Rereads Checklist
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val doneCount = entries.count { it.isCompleted }
            Text(
                text = if (entries.isNotEmpty()) {
                    "PARTS, CANTOS & REREADS ($doneCount/${entries.size})"
                } else {
                    "PARTS, CANTOS & REREADS"
                },
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.60f),
                letterSpacing = 1.1.sp
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showAddEntryDialog = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = AccentGold, modifier = Modifier.size(15.dp))
                Text("Log Entry", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGold)
            }
        }

        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenEntryDetail(entry) }
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (entry.isCompleted) AccentJade else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (entry.isCompleted) AccentJade else Color.White.copy(alpha = 0.34f),
                            CircleShape
                        )
                        .clickable { onToggleEntryCompleted(entry) },
                    contentAlignment = Alignment.Center
                ) {
                    if (entry.isCompleted) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(14.dp))
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = entry.entryName,
                        color = if (entry.isCompleted) Color.White.copy(alpha = 0.68f) else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                    val metaLine = listOfNotNull(
                        BookEntryType.fromName(entry.entryType).label,
                        entry.pagesOrLines?.takeIf { it > 0 }?.let { "$it pg/ln" },
                        entry.translator?.takeIf { it.isNotBlank() },
                        entry.editionLabel?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    Text(
                        text = metaLine,
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 11.5.sp,
                        maxLines = 2
                    )
                }

                entry.userRating?.let { r ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = AccentGold, modifier = Modifier.size(13.dp))
                        Text(String.format(Locale.US, "%.1f", r), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGold)
                    }
                }
            }
        }
    }

    if (showProgressDialog) {
        var primaryInput by remember(activeMode, item.currentProgress, currentAudioMins, totalPages) {
            mutableStateOf(
                when (activeMode) {
                    BookTrackingMode.AUDIO_MINUTES -> (currentAudioMins / 60).toString()
                    BookTrackingMode.PERCENT -> {
                        val pct = if (totalPages != null && totalPages > 0.0) {
                            ((item.currentProgress / totalPages) * 100.0).roundToInt().coerceIn(0, 100)
                        } else item.currentProgress.roundToInt().coerceIn(0, 100)
                        pct.toString()
                    }
                    BookTrackingMode.PAGES -> item.currentProgress.toInt().toString()
                }
            )
        }
        var secondaryInput by remember(activeMode, currentAudioMins, totalPages) {
            mutableStateOf(
                when (activeMode) {
                    BookTrackingMode.AUDIO_MINUTES -> (currentAudioMins % 60).toString()
                    else -> totalPages?.toInt()?.toString().orEmpty()
                }
            )
        }
        var tertiaryInput by remember(totalAudioMins) {
            mutableStateOf(totalAudioMins?.let { String.format(Locale.US, "%.1f", it / 60.0) }.orEmpty())
        }

        AlertDialog(
            onDismissRequest = { showProgressDialog = false },
            containerColor = Color(0xFF141417),
            title = {
                Text(
                    text = when (activeMode) {
                        BookTrackingMode.AUDIO_MINUTES -> "Update Audiobook Time"
                        BookTrackingMode.PERCENT -> "Set Progress (%)"
                        BookTrackingMode.PAGES -> "Update Page Progress"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = primaryInput,
                        onValueChange = { primaryInput = it },
                        label = {
                            Text(
                                when (activeMode) {
                                    BookTrackingMode.AUDIO_MINUTES -> "Hours listened"
                                    BookTrackingMode.PERCENT -> "Percentage (0–100)"
                                    BookTrackingMode.PAGES -> "Current page"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                    OutlinedTextField(
                        value = secondaryInput,
                        onValueChange = { secondaryInput = it },
                        label = {
                            Text(
                                when (activeMode) {
                                    BookTrackingMode.AUDIO_MINUTES -> "Minutes (0–59)"
                                    else -> "Total pages (optional)"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                    if (activeMode == BookTrackingMode.AUDIO_MINUTES) {
                        OutlinedTextField(
                            value = tertiaryInput,
                            onValueChange = { tertiaryInput = it },
                            label = { Text("Total audiobook hours (optional)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentGold,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (activeMode) {
                            BookTrackingMode.AUDIO_MINUTES -> {
                                val h = primaryInput.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
                                val m = secondaryInput.trim().toIntOrNull()?.coerceIn(0, 59) ?: 0
                                val totM = tertiaryInput.trim().toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 60.0).roundToInt() }
                                onUpdateAudioMinutes(h * 60 + m, totM)
                            }
                            else -> {
                                val v = primaryInput.trim().toDoubleOrNull() ?: 0.0
                                val t = secondaryInput.trim().toDoubleOrNull()?.takeIf { it > 0 }
                                onSetProgressExact(v, t)
                            }
                        }
                        showProgressDialog = false
                    }
                ) {
                    Text("Save", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProgressDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.65f))
                }
            }
        )
    }

    if (showAddEntryDialog) {
        var newEntryTitle by remember { mutableStateOf("") }
        var newEntryType by remember { mutableStateOf(BookEntryType.BOOK_PART) }
        AlertDialog(
            onDismissRequest = { showAddEntryDialog = false },
            containerColor = Color(0xFF141417),
            title = { Text("Add Book Section / Reread", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newEntryTitle,
                        onValueChange = { newEntryTitle = it },
                        label = { Text("Title (e.g. Canto IV, Book II, Reread #2)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        BookEntryType.entries.forEach { t ->
                            val sel = newEntryType == t
                            Text(
                                text = t.label,
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                color = if (sel) AccentGold else Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.clickable { newEntryType = t }.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newEntryTitle.isNotBlank()) {
                            onAddEntry(newEntryTitle, newEntryType)
                        }
                        showAddEntryDialog = false
                    }
                ) {
                    Text("Add", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEntryDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.65f))
                }
            }
        )
    }
}
