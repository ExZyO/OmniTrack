package com.omnitrack.app.ui.screens.detail.components

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

data class TrackerServiceBinding(
    val name: String,
    val typeKey: String,
    val accentColor: Color,
    val boundId: String?,
    val remoteScoreFormat: String,
    val onUpdateId: (String?) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiTrackerSheet(
    item: MediaItemEntity,
    onDismiss: () -> Unit,
    onUpdateItem: (MediaItemEntity) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniTrackApplication
    val tokenManager = app.trackerTokenManager
    val syncEngine = app.trackerSyncEngine
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val tokens by tokenManager.tokens.collectAsState(initial = com.omnitrack.app.data.sync.TrackerTokens())
    var isSyncingNow by remember { mutableStateOf(false) }

    // Build relevant tracker services list based on media type
    val trackers = remember(item) {
        val list = mutableListOf<TrackerServiceBinding>()
        when (item.mediaType) {
            MediaType.ANIME -> {
                list.add(TrackerServiceBinding("AniList", "ANILIST", Color(0xFF02A9FF), item.anilistId?.toString(), "100-Point") {
                    onUpdateItem(item.copy(anilistId = it?.toLongOrNull()))
                })
                list.add(TrackerServiceBinding("MyAnimeList", "MAL", Color(0xFF2E51A2), item.malId?.toString(), "10-Point") {
                    onUpdateItem(item.copy(malId = it?.toLongOrNull()))
                })
                list.add(TrackerServiceBinding("SIMKL", "SIMKL", Color(0xFF00E676), item.simklId?.toString(), "10-Point") {
                    onUpdateItem(item.copy(simklId = it?.toLongOrNull()))
                })
                list.add(TrackerServiceBinding("Bangumi", "BANGUMI", Color(0xFFF09199), item.bangumiId?.toString(), "10-Point") {
                    onUpdateItem(item.copy(bangumiId = it?.toLongOrNull()))
                })
            }
            MediaType.MANGA, MediaType.NOVEL -> {
                list.add(TrackerServiceBinding("AniList", "ANILIST", Color(0xFF02A9FF), item.anilistId?.toString(), "100-Point") {
                    onUpdateItem(item.copy(anilistId = it?.toLongOrNull()))
                })
                list.add(TrackerServiceBinding("MyAnimeList", "MAL", Color(0xFF2E51A2), item.malId?.toString(), "10-Point") {
                    onUpdateItem(item.copy(malId = it?.toLongOrNull()))
                })
                list.add(TrackerServiceBinding("Bangumi", "BANGUMI", Color(0xFFF09199), item.bangumiId?.toString(), "10-Point") {
                    onUpdateItem(item.copy(bangumiId = it?.toLongOrNull()))
                })
                list.add(TrackerServiceBinding("Comick", "COMICK", Color(0xFFF43F5E), item.comickId, "Chapters") {
                    onUpdateItem(item.copy(comickId = it))
                })
            }
            MediaType.SHOW, MediaType.MOVIE -> {
                list.add(TrackerServiceBinding("Trakt.tv", "TRAKT", Color(0xFFED1C24), item.traktId?.toString(), "10-Point") {
                    onUpdateItem(item.copy(traktId = it?.toLongOrNull()))
                })
                list.add(TrackerServiceBinding("SIMKL", "SIMKL", Color(0xFF00E676), item.simklId?.toString(), "10-Point") {
                    onUpdateItem(item.copy(simklId = it?.toLongOrNull()))
                })
                list.add(TrackerServiceBinding("TMDb", "TMDB", Color(0xFF01D277), item.tmdbId?.toString(), "10-Point") {
                    onUpdateItem(item.copy(tmdbId = it?.toLongOrNull()))
                })
            }
            MediaType.GAME -> {
                list.add(TrackerServiceBinding("Steam", "STEAM", Color(0xFF66C0F4), item.platform, "Hours & Stats") {
                    onUpdateItem(item.copy(platform = it))
                })
                list.add(TrackerServiceBinding("IGDB", "IGDB", Color(0xFF9146FF), item.igdbId?.toString(), "100-Point") {
                    onUpdateItem(item.copy(igdbId = it?.toLongOrNull()))
                })
            }
            MediaType.VISUAL_NOVEL -> {
                list.add(TrackerServiceBinding("VNDB", "VNDB", Color(0xFF3B82F6), item.vndbId, "10-Point / 100-Point") {
                    onUpdateItem(item.copy(vndbId = it))
                })
                list.add(TrackerServiceBinding("Bangumi", "BANGUMI", Color(0xFFF09199), item.bangumiId?.toString(), "10-Point") {
                    onUpdateItem(item.copy(bangumiId = it?.toLongOrNull()))
                })
            }
            MediaType.ALL -> {
                list.add(TrackerServiceBinding("AniList", "ANILIST", Color(0xFF02A9FF), item.anilistId?.toString(), "100-Point") {
                    onUpdateItem(item.copy(anilistId = it?.toLongOrNull()))
                })
            }
        }
        list
    }

    val boundCount = trackers.count { !it.boundId.isNullOrBlank() }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AccentJade.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = AccentJade,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Trackers & Cloud Sync",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "$boundCount of ${trackers.size} services bound for this entry",
                            fontSize = 12.sp,
                            color = if (boundCount > 0) AccentJade else TextMuted
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            // Push Changes Now Button
            if (boundCount > 0) {
                Button(
                    onClick = {
                        if (!isSyncingNow) {
                            isSyncingNow = true
                            scope.launch {
                                try {
                                    syncEngine.enqueueProgressUpdate(item, item.currentProgress)
                                    syncEngine.drainQueue()
                                    Toast.makeText(context, "Synced ${item.title} to remote trackers!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSyncingNow = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentJade),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncingNow
                ) {
                    if (isSyncingNow) {
                        CircularProgressIndicator(color = AmoledBlack, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Push Local Progress to Remote Trackers", color = AmoledBlack, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Tracker Service Cards
            trackers.forEach { tracker ->
                TrackerServiceCard(
                    tracker = tracker,
                    item = item,
                    tokens = tokens
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TrackerServiceCard(
    tracker: TrackerServiceBinding,
    item: MediaItemEntity,
    tokens: com.omnitrack.app.data.sync.TrackerTokens
) {
    val isBound = !tracker.boundId.isNullOrBlank()
    var isEditingId by remember { mutableStateOf(false) }
    var idInput by remember(tracker.boundId) { mutableStateOf(tracker.boundId.orEmpty()) }

    val hasServiceAccount = when (tracker.typeKey) {
        "ANILIST" -> !tokens.anilistToken.isNullOrBlank()
        "MAL" -> !tokens.malToken.isNullOrBlank()
        "TRAKT" -> !tokens.traktToken.isNullOrBlank()
        "SIMKL" -> !tokens.simklToken.isNullOrBlank()
        "VNDB" -> !tokens.vndbToken.isNullOrBlank()
        "STEAM" -> !tokens.steamApiKey.isNullOrBlank()
        else -> true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, if (isBound) tracker.accentColor.copy(alpha = 0.5f) else GlassBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(tracker.accentColor)
                )
                Text(
                    text = tracker.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isBound) tracker.accentColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isBound) "BOUND #${tracker.boundId}" else if (hasServiceAccount) "READY TO BIND" else "NO ACCOUNT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isBound) tracker.accentColor else TextMuted
                )
            }
        }

        // Score format conversion preview
        if (item.userRating != null) {
            val localScore = item.userRating
            val convertedScoreStr = when (tracker.remoteScoreFormat) {
                "100-Point" -> "${(localScore * 10).roundToInt()} / 100"
                "10-Point" -> "${String.format("%.1f", localScore)} / 10"
                "5-Star" -> "${String.format("%.1f", localScore / 2f)} ★"
                else -> "${String.format("%.1f", localScore)} / 10"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rating Translation:",
                    fontSize = 11.5.sp,
                    color = TextMuted
                )
                Text(
                    text = "Local ${String.format("%.1f", localScore)}  ➔  Remote $convertedScoreStr",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGold
                )
            }
        }

        // Bound ID input / display
        if (isEditingId) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AmoledBlack)
                    .border(1.dp, tracker.accentColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = idInput,
                    onValueChange = { idInput = it },
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(tracker.accentColor),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (idInput.isEmpty()) {
                            Text(text = "Enter ${tracker.name} ID...", color = TextMuted, fontSize = 12.5.sp)
                        }
                        inner()
                    }
                )

                IconButton(
                    onClick = {
                        val clean = idInput.trim().ifBlank { null }
                        tracker.onUpdateId(clean)
                        isEditingId = false
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Save", tint = tracker.accentColor, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBound) "Remote ID: ${tracker.boundId}" else "Not bound to ${tracker.name}",
                    fontSize = 12.sp,
                    color = if (isBound) TextPrimary else TextMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isBound) {
                        IconButton(
                            onClick = {
                                tracker.onUpdateId(null)
                                idInput = ""
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.LinkOff, contentDescription = "Unbind", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        }
                    }

                    IconButton(
                        onClick = { isEditingId = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(imageVector = if (isBound) Icons.Default.Edit else Icons.Default.Link, contentDescription = "Edit ID", tint = tracker.accentColor, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
