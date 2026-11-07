package com.omnitrack.app.ui.screens.detail.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class TrackerDef(
    val key: String,
    val name: String,
    val accentColor: Color,
    val boundId: String?,
    val scoreFormat: String,
    val browserUrlTemplate: ((String) -> String)? = null,
    val onUpdateBoundId: (String?) -> Unit
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

    // 1. Auto Status Switching (Nuvio-style)
    var autoStatusEnabled by remember { mutableStateOf(true) }

    // 2. Category Switcher & Anime Movie Bridge
    val isProbableAnimeMovie = remember(item) {
        item.mediaType == MediaType.MOVIE && (
            item.synopsis?.contains("anime", ignoreCase = true) == true ||
            item.title.contains("Movie", ignoreCase = true) ||
            item.anilistId != null ||
            item.malId != null
        )
    }

    val categoryOptions = listOf(
        "Anime Series",
        "Anime Movie (Bridge)",
        "Shows & Movies",
        "Manga & Novels",
        "Games",
        "Visual Novels"
    )

    var selectedCategory by remember {
        mutableStateOf(
            when {
                isProbableAnimeMovie -> "Anime Movie (Bridge)"
                item.mediaType == MediaType.ANIME -> "Anime Series"
                item.mediaType == MediaType.SHOW || item.mediaType == MediaType.MOVIE -> "Shows & Movies"
                item.mediaType == MediaType.MANGA || item.mediaType == MediaType.NOVEL -> "Manga & Novels"
                item.mediaType == MediaType.GAME -> "Games"
                item.mediaType == MediaType.VISUAL_NOVEL -> "Visual Novels"
                else -> "Anime Movie (Bridge)"
            }
        )
    }

    // 3. Build Active Trackers for Selected Category
    val availableTrackers = remember(item, selectedCategory) {
        val list = mutableListOf<TrackerDef>()

        fun addAnilist() = list.add(
            TrackerDef("ANILIST", "AniList", Color(0xFF02A9FF), item.anilistId?.toString(), "100-Point",
                browserUrlTemplate = { "https://anilist.co/anime/$it" }) {
                onUpdateItem(item.copy(anilistId = it?.toLongOrNull()))
            }
        )
        fun addMal() = list.add(
            TrackerDef("MAL", "MyAnimeList", Color(0xFF2E51A2), item.malId?.toString(), "10-Point",
                browserUrlTemplate = { "https://myanimelist.net/anime/$it" }) {
                onUpdateItem(item.copy(malId = it?.toLongOrNull()))
            }
        )
        fun addSimkl(isAnime: Boolean = true) = list.add(
            TrackerDef("SIMKL", "SIMKL", Color(0xFF00E676), item.simklId?.toString(), "10-Point",
                browserUrlTemplate = { "https://simkl.com/${if (isAnime) "anime" else "movies"}/$it" }) {
                onUpdateItem(item.copy(simklId = it?.toLongOrNull()))
            }
        )
        fun addBangumi() = list.add(
            TrackerDef("BANGUMI", "Bangumi", Color(0xFFF09199), item.bangumiId?.toString(), "10-Point",
                browserUrlTemplate = { "https://bgm.tv/subject/$it" }) {
                onUpdateItem(item.copy(bangumiId = it?.toLongOrNull()))
            }
        )
        fun addTrakt() = list.add(
            TrackerDef("TRAKT", "Trakt.tv", Color(0xFFED1C24), item.traktId?.toString(), "10-Point",
                browserUrlTemplate = { "https://trakt.tv/search/trakt/$it?id_type=movie" }) {
                onUpdateItem(item.copy(traktId = it?.toLongOrNull()))
            }
        )
        fun addTmdb() = list.add(
            TrackerDef("TMDB", "TMDb", Color(0xFF01D277), item.tmdbId?.toString(), "10-Point (Half-Star)",
                browserUrlTemplate = { "https://www.themoviedb.org/movie/$it" }) {
                onUpdateItem(item.copy(tmdbId = it?.toLongOrNull()))
            }
        )
        fun addComick() = list.add(
            TrackerDef("COMICK", "Comick", Color(0xFFF43F5E), item.comickId, "Chapters",
                browserUrlTemplate = { "https://comick.io/comic/$it" }) {
                onUpdateItem(item.copy(comickId = it))
            }
        )
        fun addSteam() = list.add(
            TrackerDef("STEAM", "Steam", Color(0xFF66C0F4), item.platform, "Playtime Sync",
                browserUrlTemplate = { "https://store.steampowered.com/app/$it" }) {
                onUpdateItem(item.copy(platform = it))
            }
        )
        fun addIgdb() = list.add(
            TrackerDef("IGDB", "IGDB", Color(0xFF9146FF), item.igdbId?.toString(), "100-Point",
                browserUrlTemplate = { "https://www.igdb.com/games/$it" }) {
                onUpdateItem(item.copy(igdbId = it?.toLongOrNull()))
            }
        )
        fun addVndb() = list.add(
            TrackerDef("VNDB", "VNDB", Color(0xFF3B82F6), item.vndbId, "10-100 Votes",
                browserUrlTemplate = { "https://vndb.org/$it" }) {
                onUpdateItem(item.copy(vndbId = it))
            }
        )

        when (selectedCategory) {
            "Anime Movie (Bridge)" -> {
                addAnilist(); addMal(); addBangumi(); addSimkl(isAnime = true); addTrakt(); addTmdb()
            }
            "Anime Series" -> {
                addAnilist(); addMal(); addSimkl(isAnime = true); addBangumi()
            }
            "Shows & Movies" -> {
                addTrakt(); addSimkl(isAnime = false); addTmdb()
            }
            "Manga & Novels" -> {
                addAnilist(); addMal(); addBangumi(); addComick()
            }
            "Games" -> {
                addSteam(); addIgdb()
            }
            "Visual Novels" -> {
                addVndb(); addBangumi(); addAnilist()
            }
        }
        list
    }

    // 4. Tab Navigation (ALL + Individual Trackers)
    val tabOptions = remember(availableTrackers) {
        listOf("ALL") + availableTrackers.map { it.name }
    }
    var activeTab by remember { mutableStateOf("ALL") }

    // If current tab is not in options, reset to ALL
    LaunchedEffect(tabOptions) {
        if (activeTab !in tabOptions) {
            activeTab = "ALL"
        }
    }

    // Expanded state per tracker card in ALL tab
    val expandedCards = remember { mutableStateMapOf<String, Boolean>() }

    // Chimanew-Style Search & Rebind Sheet State
    var searchRebindTracker by remember { mutableStateOf<TrackerDef?>(null) }

    // Handlers for Progress & Auto-Status switching
    fun updateProgressWithAutoStatus(newProgress: Double) {
        val total = item.totalProgress ?: 0.0
        val bounded = if (total > 0.0) newProgress.coerceIn(0.0, total) else newProgress.coerceAtLeast(0.0)

        var newStatus = item.status
        if (autoStatusEnabled) {
            if (bounded > 0.0 && item.status == TrackingStatus.PLAN_TO_TRACK) {
                newStatus = TrackingStatus.IN_PROGRESS
            } else if (total > 0.0 && bounded >= total) {
                newStatus = TrackingStatus.COMPLETED
            }
        }
        onUpdateItem(item.copy(currentProgress = bounded, status = newStatus))
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
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Header: Title, Bound Count, Auto-Status Toggle & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(AccentArctic.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = AccentArctic, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "Multi-Tracker Hub",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "${availableTrackers.count { !it.boundId.isNullOrBlank() }} of ${availableTrackers.size} services active",
                        fontSize = 11.5.sp,
                        color = AccentJade,
                        modifier = Modifier.padding(start = 42.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Auto-Status Switching Pill (Nuvio-style)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (autoStatusEnabled) AccentArctic.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
                            .clickable { autoStatusEnabled = !autoStatusEnabled }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (autoStatusEnabled) "⚡ Auto Status" else "✋ Manual",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (autoStatusEnabled) AccentArctic else TextMuted
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Category Switcher Chips (Squircle stadium pills)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categoryOptions.forEach { cat ->
                    val isCatSelected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isCatSelected) AccentJade else DarkSurfaceElevated)
                            .clickable {
                                selectedCategory = cat
                                activeTab = "ALL"
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = cat,
                            fontSize = 12.sp,
                            fontWeight = if (isCatSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isCatSelected) AmoledBlack else TextSecondary
                        )
                    }
                }
            }

            // Tab Switcher Row (ALL + Individual Tracker Tabs)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabOptions.forEach { tabName ->
                    val isTabActive = tabName == activeTab
                    val trackerMatch = availableTrackers.find { it.name == tabName }
                    val tabAccent = trackerMatch?.accentColor ?: AccentArctic

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isTabActive) tabAccent else DarkSurfaceElevated)
                            .clickable { activeTab = tabName }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = tabName,
                            fontSize = 12.5.sp,
                            fontWeight = if (isTabActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isTabActive) AmoledBlack else TextPrimary
                        )
                    }
                }
            }

            // UNIVERSAL CONTROLS BAR: ALWAYS VISIBLE IN ALL TABS & INDIVIDUAL TABS
            UniversalSyncControlsBar(
                item = item,
                onUpdateProgress = { updateProgressWithAutoStatus(it) },
                onUpdateStatus = { onUpdateItem(item.copy(status = it)) },
                onUpdateRating = { onUpdateItem(item.copy(userRating = it)) }
            )

            // Push Local Changes to Remote Cloud Button
            Button(
                onClick = {
                    if (!isSyncingNow) {
                        isSyncingNow = true
                        scope.launch {
                            try {
                                syncEngine.enqueueProgressUpdate(item, item.currentProgress)
                                syncEngine.drainQueue()
                                Toast.makeText(context, "Pushed ${item.title} to active trackers!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Sync error: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSyncingNow = false
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentJade),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSyncingNow
            ) {
                if (isSyncingNow) {
                    CircularProgressIndicator(color = AmoledBlack, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Push Changes to Remote Trackers", color = AmoledBlack, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // CONTENT: "ALL" Tab vs. Individual Tracker Tab
            if (activeTab == "ALL") {
                // ALL Tab: Clean Collapsible Squircle Accordion Cards
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    availableTrackers.forEach { tracker ->
                        val isExpanded = expandedCards[tracker.key] ?: true
                        CollapsibleTrackerCard(
                            tracker = tracker,
                            item = item,
                            tokens = tokens,
                            isExpanded = isExpanded,
                            onToggleExpand = { expandedCards[tracker.key] = !isExpanded },
                            onOpenBrowser = {
                                tracker.boundId?.let { id ->
                                    tracker.browserUrlTemplate?.let { template ->
                                        val url = template(id)
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                }
                            },
                            onSearchRebind = { searchRebindTracker = tracker },
                            onUntrack = {
                                tracker.onUpdateBoundId(null)
                                Toast.makeText(context, "Untracked from ${tracker.name}", Toast.LENGTH_SHORT).show()
                            },
                            onDeleteItem = {
                                tracker.onUpdateBoundId(null)
                                Toast.makeText(context, "Removed from ${tracker.name} list", Toast.LENGTH_SHORT).show()
                            },
                            onUpdateItem = onUpdateItem
                        )
                    }
                }
            } else {
                // Dedicated Individual Tracker Tab
                val tracker = availableTrackers.find { it.name == activeTab }
                if (tracker != null) {
                    SingleTrackerFocusedView(
                        tracker = tracker,
                        item = item,
                        tokens = tokens,
                        onOpenBrowser = {
                            tracker.boundId?.let { id ->
                                tracker.browserUrlTemplate?.let { template ->
                                    val url = template(id)
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        },
                        onSearchRebind = { searchRebindTracker = tracker },
                        onUntrack = {
                            tracker.onUpdateBoundId(null)
                            Toast.makeText(context, "Untracked from ${tracker.name}", Toast.LENGTH_SHORT).show()
                        },
                        onDeleteItem = {
                            tracker.onUpdateBoundId(null)
                            Toast.makeText(context, "Removed from ${tracker.name} list", Toast.LENGTH_SHORT).show()
                        },
                        onUpdateItem = onUpdateItem
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Chimanew-Style Search & Rebind Modal Bottom Sheet
    searchRebindTracker?.let { targetTracker ->
        SearchAndRebindBottomSheet(
            tracker = targetTracker,
            item = item,
            app = app,
            onDismiss = { searchRebindTracker = null },
            onSelectResult = { selectedId ->
                targetTracker.onUpdateBoundId(selectedId)
                Toast.makeText(context, "Bound #${selectedId} to ${targetTracker.name}!", Toast.LENGTH_SHORT).show()
                searchRebindTracker = null
            }
        )
    }
}

/**
 * Universal controls bar with Status pills, Nuvio-style smooth continuous progress line scrubber,
 * and 1.0–10.0 Rating slider.
 * ALWAYS VISIBLE across all tabs and individual tracker screens.
 */
@Composable
private fun UniversalSyncControlsBar(
    item: MediaItemEntity,
    onUpdateProgress: (Double) -> Unit,
    onUpdateStatus: (TrackingStatus) -> Unit,
    onUpdateRating: (Float?) -> Unit
) {
    val totalProgress = item.totalProgress ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, GlassBorder, RoundedCornerShape(26.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Status Selector Stadium Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tracking Status", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Text(item.status.displayName, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = AccentJade)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val statuses = listOf(
                TrackingStatus.IN_PROGRESS,
                TrackingStatus.COMPLETED,
                TrackingStatus.PLAN_TO_TRACK,
                TrackingStatus.ON_HOLD,
                TrackingStatus.DROPPED
            )

            statuses.forEach { st ->
                val isSelected = item.status == st
                val label = when (st) {
                    TrackingStatus.IN_PROGRESS -> "Watching / Reading"
                    TrackingStatus.PLAN_TO_TRACK -> "Plan to Watch"
                    else -> st.displayName
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) AccentJade else AmoledBlack)
                        .clickable { onUpdateStatus(st) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) AmoledBlack else TextSecondary
                    )
                }
            }
        }

        // 2. Progress Header with Fraction & Percentage Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Progress", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${item.currentProgress.toInt()} / ${if (totalProgress > 0) totalProgress.toInt() else "∞"}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentArctic
                )
                if (totalProgress > 0.0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AccentArctic.copy(alpha = 0.18f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        val pct = ((item.currentProgress / totalProgress) * 100).toInt().coerceIn(0, 100)
                        Text(
                            text = "$pct%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = AccentArctic
                        )
                    }
                }
            }
        }

        // Stepper Buttons and Continuous Progress Scrubber Line
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Decrement Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AmoledBlack)
                    .clickable { onUpdateProgress((item.currentProgress - 1.0).coerceAtLeast(0.0)) },
                contentAlignment = Alignment.Center
            ) {
                Text("-1", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            // Continuous Scrubber Line Track
            Box(modifier = Modifier.weight(1f)) {
                ProgressScrubberLine(
                    currentProgress = item.currentProgress,
                    totalProgress = item.totalProgress,
                    onProgressChanged = onUpdateProgress
                )
            }

            // Increment Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AccentArctic)
                    .clickable { onUpdateProgress(item.currentProgress + 1.0) },
                contentAlignment = Alignment.Center
            ) {
                Text("+1", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AmoledBlack)
            }
        }

        // 3. Master Score (1.0 to 10.0 with 0.1 precision)
        val score = item.userRating ?: 0f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Master Rating (0.1 Precision)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Text(
                text = if (score > 0f) "★ ${String.format("%.1f", score)} / 10.0" else "Unrated",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AccentGold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AmoledBlack)
                    .clickable {
                        val next = ((score - 0.1f) * 10f).roundToInt() / 10f
                        onUpdateRating(if (next <= 0f) null else next.coerceIn(1.0f, 10.0f))
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("-0.1", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }

            Slider(
                value = score,
                onValueChange = { raw ->
                    val rounded = ((raw * 10f).roundToInt() / 10f).coerceIn(1.0f, 10.0f)
                    onUpdateRating(rounded)
                },
                valueRange = 1.0f..10.0f,
                steps = 89, // (10.0 - 1.0) / 0.1 - 1 = 89 steps
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = AccentGold,
                    activeTrackColor = AccentGold,
                    inactiveTrackColor = AmoledBlack
                )
            )

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AccentGold)
                    .clickable {
                        val next = if (score <= 0f) 1.0f else ((score + 0.1f) * 10f).roundToInt() / 10f
                        onUpdateRating(next.coerceIn(1.0f, 10.0f))
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("+0.1", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmoledBlack)
            }
        }
    }
}

/**
 * Smooth, scrollable/draggable continuous progress line like in Nuvio Enhanced.
 * Features live drag tracking, glow indicators, percentage fill, and haptic feedback.
 */
@Composable
private fun ProgressScrubberLine(
    currentProgress: Double,
    totalProgress: Double?,
    onProgressChanged: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val total = totalProgress ?: 0.0
    val isBounded = total > 0.0
    var trackWidthPx by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgressValue by remember(currentProgress) { androidx.compose.runtime.mutableDoubleStateOf(currentProgress) }
    var lastHapticInt by remember { androidx.compose.runtime.mutableIntStateOf(currentProgress.toInt()) }

    val activeValue = if (isDragging) dragProgressValue else currentProgress
    val progressFraction = if (isBounded) (activeValue / total).coerceIn(0.0, 1.0).toFloat() else 0f

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Scrubber line track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(totalProgress) {
                    detectTapGestures { offset ->
                        if (isBounded) {
                            val ratio = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                            val newVal = (ratio * total).roundToInt().toDouble()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onProgressChanged(newVal)
                        }
                    }
                }
                .pointerInput(totalProgress) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            if (isBounded) {
                                val ratio = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                                dragProgressValue = (ratio * total).roundToInt().toDouble()
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            onProgressChanged(dragProgressValue)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (isBounded) {
                                val deltaRatio = dragAmount / trackWidthPx
                                val next = (dragProgressValue + deltaRatio * total).coerceIn(0.0, total)
                                val rounded = next.roundToInt().toDouble()
                                if (rounded.toInt() != lastHapticInt) {
                                    lastHapticInt = rounded.toInt()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                dragProgressValue = rounded
                                onProgressChanged(rounded)
                            } else {
                                val delta = (dragAmount / 12f).toDouble()
                                val next = (dragProgressValue + delta).coerceAtLeast(0.0)
                                val rounded = next.roundToInt().toDouble()
                                if (rounded.toInt() != lastHapticInt) {
                                    lastHapticInt = rounded.toInt()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                dragProgressValue = rounded
                                onProgressChanged(rounded)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Background track (hollow line)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            )

            // Active progress line (glowing filled track)
            if (isBounded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progressFraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(AccentArctic.copy(alpha = 0.7f), AccentArctic)
                            )
                        )
                )

                // Draggable thumb pill
                Box(
                    modifier = Modifier
                        .offset(x = androidx.compose.ui.unit.max(0.dp, (trackWidthPx * progressFraction / androidx.compose.ui.platform.LocalDensity.current.density).dp - 10.dp))
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(AccentArctic)
                        .border(2.dp, AmoledBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(AmoledBlack)
                    )
                }
            } else {
                // Unlimited/ongoing scrubber indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(AccentArctic.copy(alpha = 0.35f))
                )
            }
        }

        // Quick jump percentage pills (Nuvio Enhanced style)
        if (isBounded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0, 25, 50, 75, 100).forEach { pct ->
                    val targetVal = ((pct / 100.0) * total).roundToInt().toDouble()
                    val isCurrent = activeValue.toInt() == targetVal.toInt()
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isCurrent) AccentArctic else AmoledBlack)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onProgressChanged(targetVal)
                            }
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$pct%",
                            fontSize = 9.5.sp,
                            fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                            color = if (isCurrent) AmoledBlack else TextMuted
                        )
                    }
                }
            }
        }
    }
}

/**
 * Collapsible Squircle Accordion Card for ALL Tab
 */
@Composable
private fun CollapsibleTrackerCard(
    tracker: TrackerDef,
    item: MediaItemEntity,
    tokens: com.omnitrack.app.data.sync.TrackerTokens,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSearchRebind: () -> Unit,
    onUntrack: () -> Unit,
    onDeleteItem: () -> Unit,
    onUpdateItem: (MediaItemEntity) -> Unit
) {
    val isBound = !tracker.boundId.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, if (isBound) tracker.accentColor.copy(alpha = 0.35f) else GlassBorder, RoundedCornerShape(26.dp))
            .animateContentSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card Header (Tap to expand/collapse)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleExpand
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(tracker.accentColor)
                )
                Column {
                    Text(
                        text = tracker.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isBound) "Bound #${tracker.boundId}" else "Not Connected",
                        fontSize = 11.sp,
                        color = if (isBound) tracker.accentColor else TextMuted
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Score conversion preview badge
                if (item.userRating != null) {
                    val converted = getRemoteConvertedScore(tracker.scoreFormat, item.userRating)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AmoledBlack)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(converted, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentGold)
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Expanded Card Details
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Action Stadium Pills Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isBound) {
                        ActionStadiumPill("Open", Icons.Default.OpenInBrowser, tracker.accentColor, onOpenBrowser)
                    }
                    ActionStadiumPill("Change", Icons.Default.Search, AccentArctic, onSearchRebind)
                    if (isBound) {
                        ActionStadiumPill("Untrack", Icons.Default.LinkOff, TextMuted, onUntrack)
                        ActionStadiumPill("Delete", Icons.Default.Delete, Color(0xFFEF4444), onDeleteItem)
                    }
                }

                // Tracker Specific Fields
                when (tracker.key) {
                    "SIMKL" -> SimklSpecificControls(item, onUpdateItem)
                    "MAL" -> MalSpecificControls(item, onUpdateItem)
                    "ANILIST" -> AnilistSpecificControls(item, onUpdateItem)
                    "BANGUMI" -> BangumiSpecificControls(item, onUpdateItem)
                    else -> {
                        // Standard generic info
                        Text(
                            text = "Scale: ${tracker.scoreFormat} • Auto-Sync on playback",
                            fontSize = 11.5.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

/**
 * Focused view for Individual Tracker Tabs
 */
@Composable
private fun SingleTrackerFocusedView(
    tracker: TrackerDef,
    item: MediaItemEntity,
    tokens: com.omnitrack.app.data.sync.TrackerTokens,
    onOpenBrowser: () -> Unit,
    onSearchRebind: () -> Unit,
    onUntrack: () -> Unit,
    onDeleteItem: () -> Unit,
    onUpdateItem: (MediaItemEntity) -> Unit
) {
    val isBound = !tracker.boundId.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, tracker.accentColor.copy(alpha = 0.4f), RoundedCornerShape(26.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(tracker.accentColor)
                )
                Text(
                    text = "${tracker.name} Sync Profile",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isBound) tracker.accentColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isBound) "BOUND #${tracker.boundId}" else "UNLINKED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isBound) tracker.accentColor else TextMuted
                )
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isBound) {
                ActionStadiumPill("Open on ${tracker.name}", Icons.Default.OpenInBrowser, tracker.accentColor, onOpenBrowser)
            }
            ActionStadiumPill("Search & Rebind Season (Chimanew)", Icons.Default.Search, AccentArctic, onSearchRebind)
            if (isBound) {
                ActionStadiumPill("Untrack", Icons.Default.LinkOff, TextMuted, onUntrack)
                ActionStadiumPill("Delete from Remote", Icons.Default.Delete, Color(0xFFEF4444), onDeleteItem)
            }
        }

        // Translation pill
        if (item.userRating != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AmoledBlack)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Remote Rating Translation:", fontSize = 11.5.sp, color = TextMuted)
                Text(
                    text = "Local ${String.format("%.1f", item.userRating)} ➔ ${getRemoteConvertedScore(tracker.scoreFormat, item.userRating)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold
                )
            }
        }

        // Specific controls per tracker
        when (tracker.key) {
            "SIMKL" -> SimklSpecificControls(item, onUpdateItem)
            "MAL" -> MalSpecificControls(item, onUpdateItem)
            "ANILIST" -> AnilistSpecificControls(item, onUpdateItem)
            "BANGUMI" -> BangumiSpecificControls(item, onUpdateItem)
            else -> {
                Text(
                    text = "Status & progress automatically scrobble to ${tracker.name} on playback.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * SIMKL Specific Controls:
 * - Public/Private Memo Toggle
 * - Strictly 140 Character Limit Note Box (Official SIMKL limit)
 */
@Composable
private fun SimklSpecificControls(
    item: MediaItemEntity,
    onUpdateItem: (MediaItemEntity) -> Unit
) {
    var memoText by remember(item.simklMemo) { mutableStateOf(item.simklMemo ?: "") }
    var isPrivate by remember(item.simklPrivate) { mutableStateOf(item.simklPrivate) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AmoledBlack)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SIMKL Personal Memo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            // Public / Private Memo Toggle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isPrivate) Color.White.copy(alpha = 0.12f) else AccentJade.copy(alpha = 0.18f))
                    .clickable {
                        val next = !isPrivate
                        isPrivate = next
                        onUpdateItem(item.copy(simklPrivate = next))
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isPrivate) TextMuted else AccentJade,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (isPrivate) "Private" else "Public",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPrivate) TextMuted else AccentJade
                    )
                }
            }
        }

        // Memo input with 140 character limit
        BasicTextField(
            value = memoText,
            onValueChange = {
                if (it.length <= 140) {
                    memoText = it
                    onUpdateItem(item.copy(simklMemo = it))
                }
            },
            textStyle = TextStyle(color = TextPrimary, fontSize = 12.5.sp),
            cursorBrush = SolidColor(AccentJade),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (memoText.isEmpty()) {
                    Text("Add personal SIMKL memo (e.g. favorite scene)...", color = TextMuted, fontSize = 12.sp)
                }
                inner()
            }
        )

        // Character limit display (unboxed, clean)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "${memoText.length} / 140",
                fontSize = 10.5.sp,
                color = if (memoText.length >= 135) Color(0xFFEF4444) else TextMuted
            )
        }
    }
}

/**
 * MyAnimeList (MAL) Specific Controls:
 * - 1,000 Character Limit Comments
 * - Unboxed counter: 1/1000 (NOT in a box!)
 * - Rewatches Stepper
 */
@Composable
private fun MalSpecificControls(
    item: MediaItemEntity,
    onUpdateItem: (MediaItemEntity) -> Unit
) {
    var malComment by remember(item.malComments) { mutableStateOf(item.malComments ?: "") }
    var rewatches by remember(item.malRewatches) { mutableStateOf(item.malRewatches) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AmoledBlack)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MAL Notes & Comments", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            // Total Rewatches Stepper
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Rewatches:", fontSize = 11.sp, color = TextMuted)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated)
                        .clickable {
                            if (rewatches > 0) {
                                val next = rewatches - 1
                                rewatches = next
                                onUpdateItem(item.copy(malRewatches = next))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("-", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Text("$rewatches", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated)
                        .clickable {
                            val next = rewatches + 1
                            rewatches = next
                            onUpdateItem(item.copy(malRewatches = next))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
        }

        // MAL comments text input (1000 char limit)
        BasicTextField(
            value = malComment,
            onValueChange = {
                if (it.length <= 1000) {
                    malComment = it
                    onUpdateItem(item.copy(malComments = it))
                }
            },
            textStyle = TextStyle(color = TextPrimary, fontSize = 12.5.sp),
            cursorBrush = SolidColor(Color(0xFF2E51A2)),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (malComment.isEmpty()) {
                    Text("MyAnimeList comments & tags...", color = TextMuted, fontSize = 12.sp)
                }
                inner()
            }
        )

        // Unboxed Character Counter (Exact user request: "dont put it in a box like (1 / 1000)")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "${malComment.length} / 1000",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (malComment.length >= 950) Color(0xFFEF4444) else TextMuted
            )
        }
    }
}

/**
 * AniList Specific Controls:
 * - Heart / Favorite Toggle
 * - Private Entry Toggle
 * - 2,000 Character Limit Notes
 */
@Composable
private fun AnilistSpecificControls(
    item: MediaItemEntity,
    onUpdateItem: (MediaItemEntity) -> Unit
) {
    var anilistNotes by remember(item.anilistNotes) { mutableStateOf(item.anilistNotes ?: "") }
    var isFavorite by remember(item.anilistFavorite) { mutableStateOf(item.anilistFavorite) }
    var isPrivate by remember(item.anilistPrivate) { mutableStateOf(item.anilistPrivate) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AmoledBlack)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AniList Custom Notes", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Heart / Favorite Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isFavorite) Color(0xFFEF4444).copy(alpha = 0.2f) else DarkSurfaceElevated)
                        .clickable {
                            val next = !isFavorite
                            isFavorite = next
                            onUpdateItem(item.copy(anilistFavorite = next))
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFEF4444) else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Private Entry Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isPrivate) AccentArctic.copy(alpha = 0.2f) else DarkSurfaceElevated)
                        .clickable {
                            val next = !isPrivate
                            isPrivate = next
                            onUpdateItem(item.copy(anilistPrivate = next))
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isPrivate) "Private" else "Public",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPrivate) AccentArctic else TextMuted
                    )
                }
            }
        }

        BasicTextField(
            value = anilistNotes,
            onValueChange = {
                if (it.length <= 2000) {
                    anilistNotes = it
                    onUpdateItem(item.copy(anilistNotes = it))
                }
            },
            textStyle = TextStyle(color = TextPrimary, fontSize = 12.5.sp),
            cursorBrush = SolidColor(Color(0xFF02A9FF)),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (anilistNotes.isEmpty()) {
                    Text("AniList personal notes & markdown...", color = TextMuted, fontSize = 12.sp)
                }
                inner()
            }
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "${anilistNotes.length} / 2000",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

/**
 * Bangumi Specific Controls:
 * - 380 Character Limit Short Comment (bgm.tv API cap)
 * - Privacy Toggle
 */
@Composable
private fun BangumiSpecificControls(
    item: MediaItemEntity,
    onUpdateItem: (MediaItemEntity) -> Unit
) {
    var bgmComment by remember(item.bangumiComment) { mutableStateOf(item.bangumiComment ?: "") }
    var isPrivate by remember(item.bangumiPrivate) { mutableStateOf(item.bangumiPrivate) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AmoledBlack)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bangumi Short Comment", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isPrivate) Color(0xFFF09199).copy(alpha = 0.2f) else DarkSurfaceElevated)
                    .clickable {
                        val next = !isPrivate
                        isPrivate = next
                        onUpdateItem(item.copy(bangumiPrivate = next))
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isPrivate) "Friends Only" else "Public",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPrivate) Color(0xFFF09199) else TextMuted
                )
            }
        }

        BasicTextField(
            value = bgmComment,
            onValueChange = {
                if (it.length <= 380) {
                    bgmComment = it
                    onUpdateItem(item.copy(bangumiComment = it))
                }
            },
            textStyle = TextStyle(color = TextPrimary, fontSize = 12.5.sp),
            cursorBrush = SolidColor(Color(0xFFF09199)),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (bgmComment.isEmpty()) {
                    Text("Bangumi吐槽 / Short comment...", color = TextMuted, fontSize = 12.sp)
                }
                inner()
            }
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "${bgmComment.length} / 380",
                fontSize = 11.sp,
                color = if (bgmComment.length >= 360) Color(0xFFEF4444) else TextMuted
            )
        }
    }
}
                color = if (bgmComment.length >= 360) Color(0xFFEF4444) else TextMuted
            )
        }
    }
}

/**
 * Chimanew-Style Search & Rebind Modal Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAndRebindBottomSheet(
    tracker: TrackerDef,
    item: MediaItemEntity,
    app: OmniTrackApplication,
    onDismiss: () -> Unit,
    onSelectResult: (String) -> Unit
) {
    val searchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf(item.title) }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun runSearch(query: String) {
        if (query.isBlank()) return
        isSearching = true
        scope.launch {
            try {
                val results = app.omniSearchRepository.searchAll(query, item.mediaType)
                searchResults = results
            } catch (_: Exception) {
                searchResults = emptyList()
            } finally {
                isSearching = false
            }
        }
    }

    LaunchedEffect(Unit) {
        runSearch(item.title)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = searchSheetState,
        containerColor = AmoledBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Search & Bind ${tracker.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Select season or correct edition for ${item.title}", fontSize = 11.5.sp, color = TextMuted)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                }
            }

            // Search text field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(tracker.accentColor),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch(searchQuery) })
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Results List
            if (isSearching) {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = tracker.accentColor, strokeWidth = 2.dp)
                }
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    Text("No results found. Try alternate title keywords.", fontSize = 12.sp, color = TextMuted)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    searchResults.forEach { res ->
                        val targetId = when (tracker.key) {
                            "ANILIST" -> res.anilistId?.toString()
                            "MAL" -> res.malId?.toString()
                            "BANGUMI" -> res.bangumiId?.toString()
                            "TMDB" -> res.tmdbId?.toString()
                            "VNDB" -> res.vndbId
                            else -> res.anilistId?.toString()
                        } ?: res.title.hashCode().toString()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(DarkSurfaceElevated)
                                .clickable { onSelectResult(targetId) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = res.coverUrl,
                                contentDescription = res.title,
                                modifier = Modifier
                                    .width(42.dp)
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(res.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (res.releaseYear != null) {
                                        Text(res.releaseYear, fontSize = 11.sp, color = TextMuted)
                                    }
                                    Text("• ID #$targetId", fontSize = 11.sp, color = tracker.accentColor, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(tracker.accentColor)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Bind", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AmoledBlack)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionStadiumPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

private fun getRemoteConvertedScore(format: String, localRating: Float?): String {
    if (localRating == null) return "Unrated"
    return when (format) {
        "100-Point" -> "${(localRating * 10f).roundToInt()} / 100"
        "10-Point" -> "${localRating.roundToInt()} / 10"
        "10-Point (Half-Star)" -> "${String.format("%.1f", ((localRating * 2f).roundToInt() / 2f))} ★"
        "10-100 Votes" -> "${(localRating * 10f).roundToInt()} / 100"
        "Playtime Sync" -> "Synced (hrs)"
        "Chapters" -> "Ch. Sync"
        else -> "${localRating.roundToInt()} / 10"
    }
}
