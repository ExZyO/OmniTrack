package com.omnitrack.app.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.backup.OmniBackupManager
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniTrackApplication
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // System File Picker for Restoring .omnibk
    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            errorMessage = null
            statusMessage = "Verifying SHA-256 and restoring database..."
            scope.launch {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    if (stream != null) {
                        val result = OmniBackupManager.restoreBackup(
                            context = context,
                            database = app.database,
                            coverCache = app.coverCache,
                            inputStream = stream
                        )
                        if (result.success) {
                            statusMessage = "Successfully restored ${result.mediaCount} media items & ${result.categoryCount} categories!"
                            Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
                        } else {
                            errorMessage = result.errorMessage
                        }
                    } else {
                        errorMessage = "Could not open selected backup file"
                    }
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Failed to read backup file"
                } finally {
                    isProcessing = false
                }
            }
        }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AccentArctic.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                        tint = AccentArctic,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "100% Fidelity App Backup (.omnibk)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Complete database, preferences, tokens & custom covers",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            // Status or Error Indicators
            if (isProcessing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceElevated)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = AccentArctic,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = statusMessage ?: "Processing backup operation...",
                        fontSize = 12.5.sp,
                        color = TextPrimary
                    )
                }
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f))
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
                        .background(AccentJade.copy(alpha = 0.15f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentJade,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = statusMessage!!,
                        fontSize = 12.5.sp,
                        color = AccentJade
                    )
                }
            }

            // Action: Create Backup
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceElevated)
                    .clickable(enabled = !isProcessing) {
                        isProcessing = true
                        errorMessage = null
                        statusMessage = "Creating .omnibk compressed archive..."
                        scope.launch {
                            val result = OmniBackupManager.createBackup(
                                context = context,
                                database = app.database,
                                coverCache = app.coverCache
                            )
                            isProcessing = false
                            if (result.success && result.backupFile != null) {
                                statusMessage = "Backup created: ${result.backupFile.name} (${result.mediaCount} items)"
                                Toast.makeText(context, "Saved to ${result.backupFile.name}", Toast.LENGTH_LONG).show()
                            } else {
                                errorMessage = result.errorMessage
                            }
                        }
                    }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = AccentArctic,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Create Full Backup (.omnibk)",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "Exports all 7 verticals, tracking history, presentation settings, encrypted tokens, and all custom cover art into a portable GZIP ZIP bundle.",
                    fontSize = 11.5.sp,
                    color = TextMuted,
                    lineHeight = 16.sp
                )
            }

            // Action: Restore Backup
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceElevated)
                    .clickable(enabled = !isProcessing) {
                        restoreFileLauncher.launch(arrayOf("*/*"))
                    }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = AccentJade,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Restore from .omnibk Backup",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "Select a previously created .omnibk archive. Verifies SHA-256 integrity and performs an atomic transactional restore with rollback safety.",
                    fontSize = 11.5.sp,
                    color = TextMuted,
                    lineHeight = 16.sp
                )
            }

            // Local Backups List
            val backupsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")
            val existingBackups = remember {
                backupsDir.listFiles()?.filter { it.extension == "omnibk" }?.sortedByDescending { it.lastModified() } ?: emptyList()
            }

            if (existingBackups.isNotEmpty()) {
                Text(
                    text = "DEVICE BACKUP ARCHIVES (${existingBackups.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    existingBackups.take(4).forEach { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated.copy(alpha = 0.6f))
                                .clickable(enabled = !isProcessing) {
                                    isProcessing = true
                                    errorMessage = null
                                    statusMessage = "Restoring ${file.name}..."
                                    scope.launch {
                                        val result = OmniBackupManager.restoreBackup(
                                            context = context,
                                            database = app.database,
                                            coverCache = app.coverCache,
                                            inputStream = file.inputStream()
                                        )
                                        isProcessing = false
                                        if (result.success) {
                                            statusMessage = "Restored ${result.mediaCount} items from ${file.name}!"
                                            Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
                                        } else {
                                            errorMessage = result.errorMessage
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
                                    text = "${file.length() / 1024} KB • Tap to restore",
                                    fontSize = 11.sp,
                                    color = TextMuted
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

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
