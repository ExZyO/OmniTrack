package com.omnitrack.app.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.backup.BackupInspectionResult
import com.omnitrack.app.data.backup.BackupModuleOptions
import com.omnitrack.app.data.backup.OmniBackupManager
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniTrackApplication
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Create, 1 = Restore
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Create options
    var createOptions by remember { mutableStateOf(BackupModuleOptions()) }

    // Restore inspection state
    var selectedSourceName by remember { mutableStateOf<String?>(null) }
    var selectedRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var selectedRestoreFile by remember { mutableStateOf<File?>(null) }
    var inspectedArchive by remember { mutableStateOf<BackupInspectionResult?>(null) }
    var restoreOptions by remember { mutableStateOf(BackupModuleOptions()) }

    fun applyInspection(name: String, uri: Uri?, file: File?, result: BackupInspectionResult) {
        selectedSourceName = name
        selectedRestoreUri = uri
        selectedRestoreFile = file
        inspectedArchive = result
        if (result.valid) {
            restoreOptions = BackupModuleOptions(
                includeLibrary = result.hasDatabase && (result.mediaCount > 0 || result.categoryCount > 0),
                includeSubEntries = result.hasDatabase && (result.episodeProgressCount > 0 || result.comicIssueCount > 0 || result.subEntriesCount > 0),
                includeSettings = result.hasSettings,
                includeCredentials = result.hasCredentials,
                includeCustomArtwork = result.customArtworkCount > 0,
                includeCachedCovers = result.cachedArtworkCount > 0
            )
            errorMessage = null
        } else {
            errorMessage = result.errorMessage
        }
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            errorMessage = null
            statusMessage = "Creating modular .omnibk backup..."
            scope.launch {
                try {
                    val stream = context.contentResolver.openOutputStream(uri)
                    if (stream != null) {
                        val result = stream.use { out ->
                            OmniBackupManager.createBackup(
                                context = context,
                                database = app.database,
                                coverCache = app.coverCache,
                                outputStream = out,
                                options = createOptions
                            )
                        }
                        if (result.success) {
                            val summary = buildCreateSummary(createOptions, result.mediaCount)
                            statusMessage = summary
                            Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
                        } else {
                            errorMessage = result.errorMessage
                            statusMessage = null
                        }
                    } else {
                        errorMessage = "Could not open selected destination for writing"
                        statusMessage = null
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    errorMessage = e.message ?: "Failed to export backup"
                    statusMessage = null
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    val inspectUriLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            errorMessage = null
            statusMessage = "Inspecting .omnibk archive..."
            scope.launch {
                try {
                    val inspection = context.contentResolver.openInputStream(uri)?.use { stream ->
                        OmniBackupManager.inspectBackup(stream)
                    } ?: BackupInspectionResult(valid = false, errorMessage = "Could not open selected backup file")
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected .omnibk"
                    applyInspection(name, uri, null, inspection)
                    statusMessage = null
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    errorMessage = e.message ?: "Failed to inspect backup file"
                    statusMessage = null
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    val anyCreateSelected = createOptions.includeLibrary ||
        createOptions.includeSubEntries ||
        createOptions.includeSettings ||
        createOptions.includeCredentials ||
        createOptions.includeCustomArtwork ||
        createOptions.includeCachedCovers

    val anyRestoreSelected = restoreOptions.includeLibrary ||
        restoreOptions.includeSubEntries ||
        restoreOptions.includeSettings ||
        restoreOptions.includeCredentials ||
        restoreOptions.includeCustomArtwork ||
        restoreOptions.includeCachedCovers

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 780.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AccentGold.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Backup & Restore",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Modular .omnibk archives with SHA-256 verification",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2-Tab Segmented Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Create Backup", "Restore Backup").forEachIndexed { idx, label ->
                    val active = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (active) AccentGold else Color.Transparent)
                            .clickable(enabled = !isProcessing) {
                                selectedTab = idx
                                errorMessage = null
                                statusMessage = null
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) AmoledBlack else TextSecondary
                        )
                    }
                }
            }

            // Status / Error Banner
            if (isProcessing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = AccentGold,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = statusMessage ?: "Processing backup...",
                        fontSize = 12.5.sp,
                        color = TextPrimary
                    )
                }
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        fontSize = 12.5.sp,
                        color = Color(0xFFEF4444)
                    )
                }
            } else if (statusMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentGold.copy(alpha = 0.14f))
                        .border(1.dp, AccentGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = statusMessage!!,
                        fontSize = 12.5.sp,
                        color = TextPrimary
                    )
                }
            }

            if (selectedTab == 0) {
                // Quick Presets Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PRESETS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = TextSecondary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f)
                    )
                    PresetPill(
                        label = "Full (All)",
                        isSelected = createOptions.includeLibrary &&
                            createOptions.includeSubEntries &&
                            createOptions.includeSettings &&
                            createOptions.includeCredentials &&
                            createOptions.includeCustomArtwork &&
                            createOptions.includeCachedCovers,
                        onClick = {
                            createOptions = BackupModuleOptions(
                                includeLibrary = true,
                                includeSubEntries = true,
                                includeSettings = true,
                                includeCredentials = true,
                                includeCustomArtwork = true,
                                includeCachedCovers = true
                            )
                        }
                    )
                    PresetPill(
                        label = "Essential",
                        isSelected = createOptions.includeLibrary &&
                            createOptions.includeSubEntries &&
                            createOptions.includeSettings &&
                            createOptions.includeCredentials &&
                            createOptions.includeCustomArtwork &&
                            !createOptions.includeCachedCovers,
                        onClick = {
                            createOptions = BackupModuleOptions(
                                includeLibrary = true,
                                includeSubEntries = true,
                                includeSettings = true,
                                includeCredentials = true,
                                includeCustomArtwork = true,
                                includeCachedCovers = false
                            )
                        }
                    )
                }

                // Create Module Toggles
                BackupModuleList(
                    options = createOptions,
                    onChange = { createOptions = it }
                )

                Button(
                    onClick = {
                        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
                        exportFileLauncher.launch("omnitrack_backup_$timestamp.omnibk")
                    },
                    enabled = !isProcessing && anyCreateSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGold,
                        contentColor = AmoledBlack
                    )
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create .omnibk Backup",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Restore Tab: File Picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                        .clickable(enabled = !isProcessing) {
                            inspectUriLauncher.launch(arrayOf("*/*"))
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pick .omnibk File",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Inspect contents first, then choose which modules to restore",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Inspected Archive Card & Modular Toggles
                inspectedArchive?.takeIf { it.valid }?.let { info ->
                    val formattedDate = if (info.createdAt > 0L) {
                        SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(info.createdAt))
                    } else "Unknown date"

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurfaceCard)
                            .border(1.dp, AccentGold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = selectedSourceName ?: "Selected Archive",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${info.device.ifBlank { "Device" }} • v${info.appVersion.ifBlank { "?" }} • $formattedDate",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "${info.mediaCount} items • ${info.episodeProgressCount + info.comicIssueCount + info.subEntriesCount} sub-entries • ${info.settingsCount} prefs • ${info.credentialsCount} keys • ${info.customArtworkCount + info.cachedArtworkCount} artwork files",
                            fontSize = 11.sp,
                            color = AccentGold
                        )
                    }

                    BackupModuleList(
                        options = restoreOptions,
                        inspection = info,
                        onChange = { restoreOptions = it }
                    )

                    Button(
                        onClick = {
                            if (!isProcessing && (selectedRestoreUri != null || selectedRestoreFile != null)) {
                                isProcessing = true
                                errorMessage = null
                                statusMessage = "Restoring selected modules..."
                                scope.launch {
                                    try {
                                        val result = when {
                                            selectedRestoreUri != null -> {
                                                context.contentResolver.openInputStream(selectedRestoreUri!!)?.use { stream ->
                                                    OmniBackupManager.restoreBackup(
                                                        context = context,
                                                        database = app.database,
                                                        coverCache = app.coverCache,
                                                        inputStream = stream,
                                                        options = restoreOptions
                                                    )
                                                }
                                            }
                                            selectedRestoreFile != null -> {
                                                selectedRestoreFile!!.inputStream().use { stream ->
                                                    OmniBackupManager.restoreBackup(
                                                        context = context,
                                                        database = app.database,
                                                        coverCache = app.coverCache,
                                                        inputStream = stream,
                                                        options = restoreOptions
                                                    )
                                                }
                                            }
                                            else -> null
                                        }
                                        if (result == null) {
                                            errorMessage = "Could not open selected archive stream"
                                            statusMessage = null
                                        } else if (result.success) {
                                            val summary = buildRestoreSummary(result)
                                            statusMessage = summary
                                            Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
                                        } else {
                                            errorMessage = result.errorMessage
                                            statusMessage = null
                                        }
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        errorMessage = e.message ?: "Failed to restore archive"
                                        statusMessage = null
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing && anyRestoreSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGold,
                            contentColor = AmoledBlack
                        )
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Restore Selected Modules",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Local Device Backups List
                val backupsDir = remember(context) {
                    File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")
                }
                var existingBackups by remember { mutableStateOf<List<File>>(emptyList()) }
                LaunchedEffect(backupsDir, statusMessage) {
                    existingBackups = withContext(Dispatchers.IO) {
                        backupsDir.listFiles()
                            ?.filter { it.extension == "omnibk" }
                            ?.sortedByDescending { it.lastModified() }
                            ?: emptyList()
                    }
                }

                if (existingBackups.isNotEmpty()) {
                    Text(
                        text = "DEVICE BACKUP ARCHIVES (${existingBackups.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        existingBackups.take(5).forEach { file ->
                            val isSelectedFile = selectedRestoreFile?.absolutePath == file.absolutePath
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(
                                        1.dp,
                                        if (isSelectedFile) AccentGold else GlassBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable(enabled = !isProcessing) {
                                        isProcessing = true
                                        errorMessage = null
                                        statusMessage = "Inspecting ${file.name}..."
                                        scope.launch {
                                            try {
                                                val inspection = file.inputStream().use { stream ->
                                                    OmniBackupManager.inspectBackup(stream)
                                                }
                                                applyInspection(file.name, null, file, inspection)
                                                statusMessage = null
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                errorMessage = e.message ?: "Could not inspect ${file.name}"
                                                statusMessage = null
                                            } finally {
                                                isProcessing = false
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${(file.length() / 1024).coerceAtLeast(1)} KB • Tap to inspect & select modules",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = AccentGold,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun PresetPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentGold.copy(alpha = 0.16f) else DarkSurfaceElevated)
            .border(1.dp, if (isSelected) AccentGold else GlassBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) AccentGold else TextSecondary
        )
    }
}

@Composable
private fun BackupModuleList(
    options: BackupModuleOptions,
    inspection: BackupInspectionResult? = null,
    onChange: (BackupModuleOptions) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        BackupModuleRow(
            title = "Library & Categories",
            subtitle = if (inspection != null) {
                "${inspection.mediaCount} entries, ${inspection.categoryCount} categories"
            } else "All tracked media entries, categories & sync queue",
            checked = options.includeLibrary,
            enabled = inspection == null || (inspection.hasDatabase && (inspection.mediaCount > 0 || inspection.categoryCount > 0)),
            onCheckedChange = { onChange(options.copy(includeLibrary = it)) }
        )
        BackupModuleRow(
            title = "Episodes, Comics & Sub-Entries",
            subtitle = if (inspection != null) {
                "${inspection.episodeProgressCount} episodes, ${inspection.comicIssueCount} comics, ${inspection.subEntriesCount} notes/routes"
            } else "Watched episodes, read issues, VN routes, books & quotes",
            checked = options.includeSubEntries,
            enabled = inspection == null || (inspection.hasDatabase && (inspection.episodeProgressCount > 0 || inspection.comicIssueCount > 0 || inspection.subEntriesCount > 0)),
            onCheckedChange = { onChange(options.copy(includeSubEntries = it)) }
        )
        BackupModuleRow(
            title = "App Settings & Layouts",
            subtitle = if (inspection != null) {
                "${inspection.settingsCount} preference entries"
            } else "Home builder, Hero banner, Details layout & appearance prefs",
            checked = options.includeSettings,
            enabled = inspection == null || inspection.hasSettings,
            onCheckedChange = { onChange(options.copy(includeSettings = it)) }
        )
        BackupModuleRow(
            title = "Tracker Tokens & API Keys",
            subtitle = if (inspection != null) {
                "${inspection.credentialsCount} signed-in accounts / keys"
            } else "Signed-in tracker sessions & API keys (stored separately)",
            checked = options.includeCredentials,
            enabled = inspection == null || inspection.hasCredentials,
            onCheckedChange = { onChange(options.copy(includeCredentials = it)) }
        )
        BackupModuleRow(
            title = "Custom Cover & Banner Art",
            subtitle = if (inspection != null) {
                "${inspection.customArtworkCount} custom image files"
            } else "Manual & custom entry artwork stored on device",
            checked = options.includeCustomArtwork,
            enabled = inspection == null || inspection.customArtworkCount > 0,
            onCheckedChange = { onChange(options.copy(includeCustomArtwork = it)) }
        )
        BackupModuleRow(
            title = "Cached Artwork (Large)",
            subtitle = if (inspection != null) {
                "${inspection.cachedArtworkCount} cached cover files"
            } else "Offline cached posters (increases archive size)",
            checked = options.includeCachedCovers,
            enabled = inspection == null || inspection.cachedArtworkCount > 0,
            onCheckedChange = { onChange(options.copy(includeCachedCovers = it)) }
        )
    }
}

@Composable
private fun BackupModuleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.55f)
            )
            Text(
                text = if (enabled) subtitle else "Not present in archive",
                fontSize = 11.5.sp,
                color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.45f)
            )
        }
        Switch(
            checked = checked && enabled,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AmoledBlack,
                checkedTrackColor = AccentGold,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = DarkSurfaceCard
            )
        )
    }
}

private fun buildCreateSummary(options: BackupModuleOptions, mediaCount: Int): String {
    val parts = buildList {
        if (options.includeLibrary) add("$mediaCount items")
        if (options.includeSubEntries) add("sub-entries")
        if (options.includeSettings) add("settings")
        if (options.includeCredentials) add("keys")
        if (options.includeCustomArtwork || options.includeCachedCovers) add("artwork")
    }
    return "Backup exported (${parts.joinToString(", ")})"
}

private fun buildRestoreSummary(result: com.omnitrack.app.data.backup.RestoreResult): String {
    val parts = buildList {
        if (result.mediaCount > 0) add("${result.mediaCount} items")
        if (result.episodeProgressCount > 0) add("${result.episodeProgressCount} episodes")
        if (result.comicIssueCount > 0) add("${result.comicIssueCount} comics")
        if (result.restoredSettings) add("settings")
        if (result.restoredCredentials) add("keys")
        if (result.restoredArtworkCount > 0) add("${result.restoredArtworkCount} artwork")
    }
    return if (parts.isEmpty()) "Selected backup modules restored" else "Restored ${parts.joinToString(", ")}"
}
