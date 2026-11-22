package com.omnitrack.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.BuildConfig
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import com.omnitrack.app.updater.AppRelease
import com.omnitrack.app.updater.AppUpdateChecker
import com.omnitrack.app.util.AppLogger
import com.omnitrack.app.util.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LogcatLine(
    val timestamp: String,
    val level: Char,
    val tag: String,
    val message: String,
    val raw: String
)

private val THREADTIME_REGEX = Regex(
    "^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+\\d+\\s+\\d+\\s+([VDIWEFA])\\s+([^:]+):\\s*(.*)$"
)

private fun readProcessLogcat(maxLines: Int = 1500): List<LogcatLine> {
    fun execCmd(cmd: Array<String>): List<String> {
        var proc: java.lang.Process? = null
        return try {
            proc = Runtime.getRuntime().exec(cmd)
            val tail = ArrayDeque<String>(maxLines + 8)
            proc.inputStream.bufferedReader().useLines { seq ->
                seq.forEach { line ->
                    if (line.isNotBlank()) {
                        if (tail.size >= maxLines) tail.removeFirst()
                        tail.addLast(line)
                    }
                }
            }
            tail.toList()
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { proc?.destroy() }
        }
    }

    val pid = Process.myPid()
    var rawLines = execCmd(arrayOf("logcat", "-d", "-v", "threadtime", "--pid=$pid"))
    if (rawLines.isEmpty()) {
        rawLines = execCmd(arrayOf("logcat", "-d", "-v", "threadtime", "-t", maxLines.toString()))
    }

    var lastLevel = 'D'
    var lastTag = "System"
    return rawLines.map { line ->
        val match = THREADTIME_REGEX.find(line)
        if (match != null) {
            val ts = match.groupValues[1].substringAfter(' ')
            val lvl = match.groupValues[2].firstOrNull() ?: 'D'
            val tag = match.groupValues[3].trim()
            val msg = match.groupValues[4]
            lastLevel = lvl
            lastTag = tag
            LogcatLine(ts, lvl, tag, msg, line)
        } else {
            LogcatLine("", lastLevel, lastTag, line, line)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(
    onDismiss: () -> Unit,
    onUpdateFound: (AppRelease) -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLoggingEnabled by AppLogger.isLoggingEnabled.collectAsState()
    val isLiveLogcatEnabled by AppLogger.isLiveLogcatEnabled.collectAsState()
    val autoRefresh by AppLogger.isLiveLogcatAutoRefresh.collectAsState()
    val appEventLogs by AppLogger.logs.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Live Logcat, 1 = App Events
    var logcatLines by remember { mutableStateOf<List<LogcatLine>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf("ALL") } // ALL, E, W, I, D
    var refreshSignal by remember { mutableIntStateOf(0) }

    val logcatListState = rememberLazyListState()
    val eventsListState = rememberLazyListState()

    LaunchedEffect(activeTab, refreshSignal, isLiveLogcatEnabled) {
        if (activeTab == 0 && isLiveLogcatEnabled) {
            logcatLines = withContext(Dispatchers.IO) { readProcessLogcat(1500) }
        } else if (!isLiveLogcatEnabled) {
            logcatLines = emptyList()
        }
    }

    LaunchedEffect(activeTab, autoRefresh, isLiveLogcatEnabled) {
        if (activeTab == 0 && isLiveLogcatEnabled && autoRefresh) {
            while (isActive) {
                logcatLines = withContext(Dispatchers.IO) { readProcessLogcat(1500) }
                delay(2000L)
            }
        }
    }

    val filteredLogcat = remember(logcatLines, searchQuery, levelFilter) {
        val q = searchQuery.trim()
        logcatLines.filter { entry ->
            val lvlOk = when (levelFilter) {
                "E" -> entry.level == 'E' || entry.level == 'F' || entry.level == 'A'
                "W" -> entry.level == 'W'
                "I" -> entry.level == 'I'
                "D" -> entry.level == 'D' || entry.level == 'V'
                else -> true
            }
            val qOk = q.isEmpty() ||
                entry.tag.contains(q, ignoreCase = true) ||
                entry.message.contains(q, ignoreCase = true)
            lvlOk && qOk
        }
    }

    val filteredEvents = remember(appEventLogs, searchQuery, levelFilter) {
        val q = searchQuery.trim()
        appEventLogs.filter { entry ->
            val lvlOk = when (levelFilter) {
                "E" -> entry.level == LogLevel.ERROR
                "W" -> entry.level == LogLevel.WARN
                "I" -> entry.level == LogLevel.INFO
                "D" -> entry.level == LogLevel.DEBUG
                else -> true
            }
            val qOk = q.isEmpty() ||
                entry.tag.contains(q, ignoreCase = true) ||
                entry.message.contains(q, ignoreCase = true)
            lvlOk && qOk
        }
    }

    fun buildExportText(): String = if (activeTab == 0) {
        filteredLogcat.joinToString("\n") { it.raw }
    } else {
        filteredEvents.joinToString("\n") { it.toFormattedString() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .background(AmoledBlack)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Frameless Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Diagnostics & Logcat",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (activeTab == 0) {
                            "${filteredLogcat.size} live process logcat lines (PID ${Process.myPid()})"
                        } else {
                            "${filteredEvents.size} structured app events"
                        },
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                }

                IconButton(onClick = { refreshSignal++ }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = AccentGold,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Option A Underline Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Live Logcat (100%)", "App Events").forEachIndexed { idx, label ->
                    val selected = activeTab == idx
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .clickable { activeTab = idx },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) TextPrimary else TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (selected) AccentGold else Color.Transparent)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Flat Inline Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(AccentGold),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Filter by tag or log message...",
                                color = TextSecondary.copy(alpha = 0.65f),
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Filter",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(15.dp)
                            .clickable { searchQuery = "" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Level Filters + Auto 2s Refresh / Logging Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("ALL" to "All", "E" to "Err", "W" to "Warn", "I" to "Info", "D" to "Debug").forEach { (key, label) ->
                        val active = levelFilter == key
                        Text(
                            text = label,
                            fontSize = 11.5.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) AccentGold else TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AccentGold.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable { levelFilter = key }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (activeTab == 0 && isLiveLogcatEnabled) {
                        Text(
                            text = if (autoRefresh) "2s: ON" else "2s: OFF",
                            color = if (autoRefresh) AccentGold else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (autoRefresh) AccentGold.copy(alpha = 0.14f)
                                    else Color.White.copy(alpha = 0.06f)
                                )
                                .clickable { AppLogger.setLiveLogcatAutoRefresh(!autoRefresh) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = if (activeTab == 0) "Logcat" else "Events",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Switch(
                        checked = if (activeTab == 0) isLiveLogcatEnabled else isLoggingEnabled,
                        onCheckedChange = { checked ->
                            if (activeTab == 0) AppLogger.setLiveLogcatEnabled(checked)
                            else AppLogger.setLoggingEnabled(checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnAccentColor,
                            checkedTrackColor = AccentGold,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Row (Flat 6dp Option A style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("OmniTrack Logs", buildExportText()))
                        Toast.makeText(context, "Copied logs to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                    colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Copy", fontSize = 12.sp)
                }

                TextButton(
                    onClick = {
                        val text = buildExportText()
                        if (text.isNotBlank()) {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share OmniTrack Diagnostics"))
                        } else {
                            Toast.makeText(context, "No logs to share", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                    colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Share", fontSize = 12.sp)
                }

                TextButton(
                    onClick = {
                        scope.launch {
                            Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
                            val release = AppUpdateChecker().checkForUpdate(context)
                            if (release != null) {
                                onUpdateFound(release)
                                sheetState.hide()
                                onDismiss()
                            } else {
                                Toast.makeText(
                                    context,
                                    "You're on the latest version (v${BuildConfig.VERSION_NAME})",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentGold.copy(alpha = 0.16f)),
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentGold)
                ) {
                    Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching { Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor() }
                            }
                            AppLogger.clearLogs()
                            logcatLines = emptyList()
                            refreshSignal++
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Clear", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

            // Frameless Console Stream
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 6.dp)
            ) {
                if (activeTab == 0) {
                    if (!isLiveLogcatEnabled) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Live Logcat is off — toggle Logcat switch above to capture",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else if (filteredLogcat.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No process logcat lines match current filters",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        LazyColumn(
                            state = logcatListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            itemsIndexed(
                                items = filteredLogcat,
                                key = { idx, entry -> "${idx}_${entry.raw.hashCode()}" }
                            ) { _, entry ->
                                val levelColor = when (entry.level) {
                                    'E', 'F', 'A' -> Color(0xFFEF4444)
                                    'W' -> Color(0xFFF59E0B)
                                    'I' -> AccentGold
                                    else -> TextSecondary
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (entry.timestamp.isNotEmpty()) {
                                        Text(
                                            text = entry.timestamp,
                                            color = TextSecondary.copy(alpha = 0.65f),
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = "${entry.level}/${entry.tag}:",
                                        color = levelColor,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = entry.message,
                                        color = TextPrimary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (filteredEvents.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (!isLoggingEnabled) "App Events logging is off — toggle Events switch above to capture" else "No structured app events logged",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        LazyColumn(
                            state = eventsListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(
                                items = filteredEvents,
                                key = { idx, entry -> "ev_${idx}_${entry.timestamp}_${entry.tag}" }
                            ) { _, entry ->
                                val levelColor = when (entry.level) {
                                    LogLevel.ERROR -> Color(0xFFEF4444)
                                    LogLevel.WARN -> Color(0xFFF59E0B)
                                    LogLevel.INFO -> AccentGold
                                    LogLevel.DEBUG -> TextSecondary
                                }
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = entry.timestamp,
                                            color = TextSecondary.copy(alpha = 0.65f),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "[${entry.tag}]",
                                            color = levelColor,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = entry.message,
                                            color = TextPrimary,
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    if (entry.stackTrace != null) {
                                        Text(
                                            text = entry.stackTrace,
                                            color = Color(0xFFEF4444).copy(alpha = 0.85f),
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
