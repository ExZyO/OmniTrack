package com.omnitrack.app.ui.screens.detail.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.artwork.ArtworkModelResolver
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.data.sync.RemoteTrackerEntrySnapshot
import com.omnitrack.app.data.sync.TrackerMetadataCodec
import com.omnitrack.app.data.sync.TrackerOverrideConfig
import com.omnitrack.app.data.sync.TrackerSyncEngine
import com.omnitrack.app.data.sync.TrackerSyncOutcome
import com.omnitrack.app.data.sync.TrackerTokens
import com.omnitrack.app.ui.components.repeatingClickable
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class TrackerDef(
    val key: String,
    val name: String,
    val boundId: String?,
    val scoreFormat: String,
    val noteCap: Int,
    val supportsStatusLock: Boolean,
    val supportsPush: Boolean,
    val isSignedIn: Boolean,
    val browserUrlTemplate: ((String) -> String)? = null,
    val onUpdateBoundId: (String?) -> MediaItemEntity
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiTrackerSheet(
    item: MediaItemEntity,
    onDismiss: () -> Unit,
    onUpdateItem: (MediaItemEntity) -> Unit,
    onDeleteLocalEntry: () -> Unit = {},
    presentationSettings: PresentationSettings? = null,
    backdropBitmap: ImageBitmap? = null,
    backdropTargetUrl: String? = null,
    onPullFromTracker: ((String, MediaItemEntity, (MediaItemEntity?, TrackerSyncOutcome) -> Unit) -> Unit)? = null,
    onPushToTracker: ((MediaItemEntity, String?, Set<String>?, (List<TrackerSyncOutcome>) -> Unit) -> Unit)? = null,
    onDeleteFromRemote: ((String, Boolean, MediaItemEntity, (TrackerSyncOutcome, Boolean) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val app = context.applicationContext as OmniTrackApplication
    val tokenManager = app.trackerTokenManager
    val syncEngine = app.trackerSyncEngine
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val tokens by tokenManager.tokens.collectAsState(initial = TrackerTokens())
    var localItem by remember(item.id, item.lastUpdated, item.matchedTrackerMetaJson) { mutableStateOf(item) }
    LaunchedEffect(item) { localItem = item }

    var showAllTrackers by remember { mutableStateOf(false) }
    var isSyncingAll by remember { mutableStateOf(false) }
    var isRefreshingMeta by remember { mutableStateOf(false) }
    var syncStatusBanner by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var rebindTargetTracker by remember { mutableStateOf<TrackerDef?>(null) }
    var deleteDialogTracker by remember { mutableStateOf<TrackerDef?>(null) }
    var pullPromptAfterBind by remember { mutableStateOf<String?>(null) }
    var datePickTarget by remember { mutableStateOf<String?>(null) }
    var showExactProgressDialog by remember { mutableStateOf(false) }

    val matchedSnapshots = remember(localItem.matchedTrackerMetaJson) {
        TrackerMetadataCodec.decodeMatchedSnapshots(localItem.matchedTrackerMetaJson)
    }
    val overridesMap = remember(localItem.trackerOverridesJson) {
        TrackerMetadataCodec.decodeOverrides(localItem.trackerOverridesJson)
    }

    val primaryKeys = remember(localItem.mediaType, localItem.anilistId, localItem.malId) {
        val isAnimeMovie = localItem.mediaType == MediaType.MOVIE &&
            (localItem.anilistId != null || localItem.malId != null || localItem.synopsis?.contains("anime", true) == true)
        when {
            isAnimeMovie -> listOf("ANILIST", "MAL", "SIMKL", "BANGUMI", "TRAKT", "TMDB")
            localItem.mediaType == MediaType.ANIME -> listOf("ANILIST", "MAL", "BANGUMI", "SIMKL", "TRAKT", "TMDB")
            localItem.mediaType == MediaType.MANGA -> listOf("ANILIST", "MAL", "BANGUMI", "COMICK")
            localItem.mediaType == MediaType.NOVEL -> listOf("ANILIST", "MAL", "BANGUMI", "HARDCOVER", "COMICK")
            localItem.mediaType == MediaType.BOOK -> listOf("HARDCOVER")
            localItem.mediaType == MediaType.COMIC -> listOf("HARDCOVER")
            localItem.mediaType == MediaType.SHOW || localItem.mediaType == MediaType.MOVIE -> listOf("TRAKT", "SIMKL", "TMDB")
            localItem.mediaType == MediaType.GAME -> listOf("STEAM", "IGDB")
            localItem.mediaType == MediaType.VISUAL_NOVEL -> listOf("VNDB", "BANGUMI")
            else -> listOf("ANILIST", "MAL", "SIMKL", "TRAKT", "TMDB")
        }
    }

    val allOrderedKeys = remember(primaryKeys) {
        val everything = listOf("ANILIST", "MAL", "BANGUMI", "SIMKL", "TRAKT", "TMDB", "HARDCOVER", "VNDB", "COMICK", "STEAM", "IGDB")
        (primaryKeys + everything).distinct()
    }

    fun buildTrackerDef(key: String, src: MediaItemEntity): TrackerDef = when (key) {
        "ANILIST" -> TrackerDef(
            key = "ANILIST",
            name = "AniList",
            boundId = src.anilistId?.toString(),
            scoreFormat = "100-Point",
            noteCap = 2000,
            supportsStatusLock = true,
            supportsPush = true,
            isSignedIn = !tokens.anilistToken.isNullOrBlank(),
            browserUrlTemplate = { id ->
                val seg = if (src.mediaType == MediaType.MANGA || src.mediaType == MediaType.NOVEL || src.mediaType == MediaType.COMIC) "manga" else "anime"
                "https://anilist.co/$seg/$id"
            },
            onUpdateBoundId = { id -> src.copy(anilistId = id?.toLongOrNull()) }
        )
        "MAL" -> TrackerDef(
            key = "MAL",
            name = "MyAnimeList",
            boundId = src.malId?.toString(),
            scoreFormat = "10-Point Int",
            noteCap = 1000,
            supportsStatusLock = true,
            supportsPush = true,
            isSignedIn = !tokens.malToken.isNullOrBlank(),
            browserUrlTemplate = { id ->
                val seg = if (src.mediaType == MediaType.MANGA || src.mediaType == MediaType.NOVEL) "manga" else "anime"
                "https://myanimelist.net/$seg/$id"
            },
            onUpdateBoundId = { id -> src.copy(malId = id?.toLongOrNull()) }
        )
        "BANGUMI" -> TrackerDef(
            key = "BANGUMI",
            name = "Bangumi",
            boundId = src.bangumiId?.toString(),
            scoreFormat = "10-Point Int",
            noteCap = 380,
            supportsStatusLock = true,
            supportsPush = true,
            isSignedIn = !tokens.bangumiToken.isNullOrBlank(),
            browserUrlTemplate = { id -> "https://bgm.tv/subject/$id" },
            onUpdateBoundId = { id -> src.copy(bangumiId = id?.toLongOrNull()) }
        )
        "SIMKL" -> TrackerDef(
            key = "SIMKL",
            name = "SIMKL",
            boundId = src.simklId?.toString(),
            scoreFormat = "10-Point Int",
            noteCap = 140,
            supportsStatusLock = true,
            supportsPush = true,
            isSignedIn = !tokens.simklToken.isNullOrBlank(),
            browserUrlTemplate = { id ->
                val seg = when (src.mediaType) {
                    MediaType.MOVIE -> "movies"
                    MediaType.ANIME -> "anime"
                    else -> "tv"
                }
                "https://simkl.com/$seg/$id"
            },
            onUpdateBoundId = { id -> src.copy(simklId = id?.toLongOrNull()) }
        )
        "TRAKT" -> TrackerDef(
            key = "TRAKT",
            name = "Trakt",
            boundId = src.traktId?.toString(),
            scoreFormat = "10-Point Int",
            noteCap = 0,
            supportsStatusLock = true,
            supportsPush = true,
            isSignedIn = !tokens.traktToken.isNullOrBlank(),
            browserUrlTemplate = { id ->
                val t = if (src.mediaType == MediaType.MOVIE) "movie" else "show"
                "https://trakt.tv/search/trakt/$id?id_type=$t"
            },
            onUpdateBoundId = { id -> src.copy(traktId = id?.toLongOrNull()) }
        )
        "TMDB" -> TrackerDef(
            key = "TMDB",
            name = "TMDb",
            boundId = src.tmdbId?.toString(),
            scoreFormat = "Half-Star (0.5..10)",
            noteCap = 0,
            supportsStatusLock = false,
            supportsPush = true,
            isSignedIn = !tokens.tmdbToken.isNullOrBlank(),
            browserUrlTemplate = { id ->
                val seg = if (src.mediaType == MediaType.MOVIE) "movie" else "tv"
                "https://www.themoviedb.org/$seg/$id"
            },
            onUpdateBoundId = { id -> src.copy(tmdbId = id?.toLongOrNull()) }
        )
        "HARDCOVER" -> TrackerDef(
            key = "HARDCOVER",
            name = "Hardcover",
            boundId = src.hardcoverId,
            scoreFormat = "5-Star (0.5 Step)",
            noteCap = 0,
            supportsStatusLock = false,
            supportsPush = true,
            isSignedIn = !tokens.hardcoverToken.isNullOrBlank(),
            browserUrlTemplate = { id -> "https://hardcover.app/books/$id" },
            onUpdateBoundId = { id -> src.copy(hardcoverId = id) }
        )
        "VNDB" -> TrackerDef(
            key = "VNDB",
            name = "VNDB",
            boundId = src.vndbId,
            scoreFormat = "100-Point (1.0..10)",
            noteCap = 2000,
            supportsStatusLock = false,
            supportsPush = true,
            isSignedIn = !tokens.vndbToken.isNullOrBlank(),
            browserUrlTemplate = { id -> "https://vndb.org/$id" },
            onUpdateBoundId = { id -> src.copy(vndbId = id) }
        )
        "COMICK" -> TrackerDef(
            key = "COMICK",
            name = "Comick",
            boundId = src.comickId,
            scoreFormat = "Chapters",
            noteCap = 0,
            supportsStatusLock = true,
            supportsPush = false,
            isSignedIn = !tokens.comickToken.isNullOrBlank(),
            browserUrlTemplate = { id -> "https://comick.io/comic/$id" },
            onUpdateBoundId = { id -> src.copy(comickId = id) }
        )
        "STEAM" -> TrackerDef(
            key = "STEAM",
            name = "Steam",
            boundId = src.steamAppId?.toString(),
            scoreFormat = "Playtime & Achievements",
            noteCap = 0,
            supportsStatusLock = false,
            supportsPush = false,
            isSignedIn = !tokens.steamApiKey.isNullOrBlank() && !tokens.steamId64.isNullOrBlank(),
            browserUrlTemplate = { id -> "https://store.steampowered.com/app/$id" },
            onUpdateBoundId = { id ->
                src.copy(
                    steamAppId = id?.toLongOrNull(),
                    platform = src.platform ?: if (id != null) "PC" else null
                )
            }
        )
        else -> TrackerDef(
            key = "IGDB",
            name = "IGDB",
            boundId = src.igdbId?.toString(),
            scoreFormat = "100-Point",
            noteCap = 0,
            supportsStatusLock = false,
            supportsPush = false,
            isSignedIn = com.omnitrack.app.data.preferences.PreferenceManager.cachedIgdbClientId.isNotBlank(),
            browserUrlTemplate = { _ -> "https://www.igdb.com/search?q=${Uri.encode(src.title)}" },
            onUpdateBoundId = { id -> src.copy(igdbId = id?.toLongOrNull()) }
        )
    }

    val displayedKeys = remember(primaryKeys, allOrderedKeys, showAllTrackers, localItem) {
        if (showAllTrackers) {
            allOrderedKeys
        } else {
            val alsoBoundCross = allOrderedKeys.filter { key ->
                key !in primaryKeys && buildTrackerDef(key, localItem).boundId != null
            }
            (primaryKeys + alsoBoundCross).distinct()
        }
    }

    val trackers = remember(displayedKeys, localItem, tokens) {
        displayedKeys.map { buildTrackerDef(it, localItem) }
    }

    val allEligibleUniversalKeys = remember(localItem, tokens) {
        allOrderedKeys.map { buildTrackerDef(it, localItem) }
            .filter { it.boundId != null && it.isSignedIn && it.supportsPush }
            .map { it.key }
    }
    var universalTargetKeys by remember(item.id, allEligibleUniversalKeys) {
        mutableStateOf(allEligibleUniversalKeys.toSet())
    }
    var selectedTrackerKey by remember(item.id) { mutableStateOf("UNIVERSAL") }
    val isUniversalMode = selectedTrackerKey == "UNIVERSAL"
    val activeTracker = trackers.firstOrNull { it.key == selectedTrackerKey }
        ?: trackers.first()

    // Live-refresh matched remote posters & diff snapshots for bound trackers
    LaunchedEffect(localItem.id, displayedKeys, localItem.anilistId, localItem.malId, localItem.hardcoverId, localItem.tmdbId, localItem.traktId, localItem.simklId, localItem.bangumiId, localItem.vndbId) {
        val boundKeys = displayedKeys.filter { buildTrackerDef(it, localItem).boundId != null }
        if (boundKeys.isEmpty()) return@LaunchedEffect
        val missingOrStale = boundKeys.filter { key ->
            val snap = matchedSnapshots[key]
            snap == null || (System.currentTimeMillis() - snap.lastSyncedMs) > 120_000L
        }
        if (missingOrStale.isNotEmpty() && !isRefreshingMeta) {
            isRefreshingMeta = true
            try {
                val updatedMap = matchedSnapshots.toMutableMap()
                var changed = false
                coroutineScope {
                    missingOrStale.map { k ->
                        async {
                            val snap = syncEngine.fetchMatchedTrackerSnapshot(k, localItem)
                            if (snap != null) {
                                updatedMap[k] = snap
                                changed = true
                            }
                        }
                    }.awaitAll()
                }
                if (changed) {
                    val nextJson = TrackerMetadataCodec.encodeMatchedSnapshots(updatedMap)
                    val next = localItem.copy(matchedTrackerMetaJson = nextJson)
                    localItem = next
                    onUpdateItem(next)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                isRefreshingMeta = false
            }
        }
    }

    fun commitLocalUpdate(next: MediaItemEntity) {
        localItem = next
        onUpdateItem(next)
    }

    fun triggerPush(
        targetKey: String?,
        itemToPush: MediaItemEntity = localItem,
        selectedKeys: Set<String>? = if (targetKey == null) universalTargetKeys else null
    ) {
        if (isSyncingAll) return
        isSyncingAll = true
        val runner: (MediaItemEntity, String?, Set<String>?, (List<TrackerSyncOutcome>) -> Unit) -> Unit =
            onPushToTracker ?: { itm, k, filterKeys, cb ->
                scope.launch {
                    val res = syncEngine.pushItemToTrackers(itm, k, filterKeys)
                    cb(res)
                }
            }
        runner(itemToPush, targetKey, selectedKeys) { outcomes ->
            isSyncingAll = false
            val failed = outcomes.filter { !it.success }
            val ok = outcomes.filter { it.success }
            syncStatusBanner = if (failed.isEmpty()) {
                true to if (ok.size == 1) ok.first().message else "Synced ${ok.size} tracker(s): ${ok.joinToString(", ") { it.trackerKey }}"
            } else {
                false to failed.joinToString(" · ") { it.message }
            }
        }
    }

    fun triggerPull(targetKey: String) {
        if (isSyncingAll) return
        isSyncingAll = true
        val runner: (String, MediaItemEntity, (MediaItemEntity?, TrackerSyncOutcome) -> Unit) -> Unit =
            onPullFromTracker ?: { k, itm, cb ->
                scope.launch {
                    val (updated, out) = syncEngine.pullFromTracker(k, itm)
                    cb(updated, out)
                }
            }
        runner(targetKey, localItem) { updated, outcome ->
            isSyncingAll = false
            if (updated != null) localItem = updated
            syncStatusBanner = outcome.success to outcome.message
        }
    }

    val linkedCount = remember(trackers) { trackers.count { it.boundId != null } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        BackHandler {
            when {
                rebindTargetTracker != null -> rebindTargetTracker = null
                deleteDialogTracker != null -> deleteDialogTracker = null
                else -> onDismiss()
            }
        }

        DetailSheetBackdrop(
            presentationSettings = presentationSettings,
            backdropBitmap = backdropBitmap,
            backdropTargetUrl = backdropTargetUrl,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Drag Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f))
                    )
                }

                // 1. Borderless Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = localItem.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$linkedCount Linked · ${if (localItem.autoStatusEnabled) "Auto-Sync On" else "Manual Sync"}",
                            fontSize = 11.5.sp,
                            color = AccentGold
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentGold)
                            .clickable(enabled = !isSyncingAll) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                triggerPush(null)
                            }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isSyncingAll) {
                            CircularProgressIndicator(
                                color = AmoledBlack,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(13.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = AmoledBlack,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = if (isSyncingAll) "Syncing" else "Sync All",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmoledBlack
                        )
                    }
                }

                // Live Sync Outcome Banner
                AnimatedVisibility(visible = syncStatusBanner != null) {
                    syncStatusBanner?.let { (ok, msg) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (ok) AccentGold.copy(alpha = 0.14f)
                                    else Color(0xFFEF4444).copy(alpha = 0.18f)
                                )
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (ok) AccentGold else Color(0xFFFCA5A5),
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { syncStatusBanner = null }
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = pullPromptAfterBind != null) {
                    pullPromptAfterBind?.let { boundKey ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AccentGold.copy(alpha = 0.12f))
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Bound $boundKey! Pull remote progress & rating?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentGold,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Skip",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.clickable { pullPromptAfterBind = null }
                                )
                                Text(
                                    text = "Pull Now",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGold,
                                    modifier = Modifier.clickable {
                                        pullPromptAfterBind = null
                                        triggerPull(boundKey)
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. Horizontal Matched-Artwork Switcher Strip (Universal first!)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "UNIVERSAL_TILE") {
                        val readyCount = universalTargetKeys.size
                        Column(
                            modifier = Modifier
                                .width(66.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isUniversalMode) AccentGold.copy(alpha = 0.16f)
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .clickable { selectedTrackerKey = "UNIVERSAL" }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(46.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(DarkSurfaceCard),
                                contentAlignment = Alignment.Center
                            ) {
                                val uThumb = ArtworkModelResolver.toCoilModel(
                                    localItem.customCoverPath?.takeIf { it.isNotBlank() }
                                        ?: localItem.coverUrl.takeIf { it.isNotBlank() }
                                )
                                if (uThumb != null) {
                                    AsyncImage(
                                        model = uThumb,
                                        contentDescription = "Universal",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(AmoledBlack.copy(alpha = 0.45f))
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Universal Sync",
                                    tint = AccentGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Universal",
                                fontSize = 10.5.sp,
                                fontWeight = if (isUniversalMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (isUniversalMode) AccentGold else TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = if (readyCount > 0) "$readyCount Ready" else "Setup",
                                fontSize = 9.sp,
                                color = if (readyCount > 0) AccentGold else TextSecondary,
                                maxLines = 1
                            )
                        }
                    }

                    itemsIndexed(trackers, key = { _, t -> t.key }) { _, trk ->
                        val selected = !isUniversalMode && trk.key == activeTracker.key
                        val snap = matchedSnapshots[trk.key]
                        val rawThumb = if (trk.boundId != null) {
                            localItem.customCoverPath?.takeIf { it.isNotBlank() }
                                ?: snap?.coverUrl?.takeIf { it.isNotBlank() }
                                ?: localItem.coverUrl.takeIf { it.isNotBlank() }
                        } else {
                            snap?.coverUrl?.takeIf { it.isNotBlank() }
                        }
                        val thumbModel = ArtworkModelResolver.toCoilModel(rawThumb)
                        val statusDotColor = when {
                            trk.boundId == null -> Color.White.copy(alpha = 0.25f)
                            !trk.isSignedIn && trk.supportsPush -> Color(0xFFEF4444)
                            snap?.remoteProgress != null && snap.remoteProgress.toInt() != localItem.currentProgress.toInt() -> Color(0xFFF59E0B)
                            else -> AccentGold
                        }
                        val statusWord = when {
                            trk.boundId == null -> "Unlinked"
                            !trk.isSignedIn && trk.supportsPush -> "Signed Out"
                            snap?.remoteProgress != null && snap.remoteProgress.toInt() != localItem.currentProgress.toInt() -> "Diff"
                            else -> "Linked"
                        }

                        Column(
                            modifier = Modifier
                                .width(66.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) AccentGold.copy(alpha = 0.16f)
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .clickable { selectedTrackerKey = trk.key }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(46.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(DarkSurfaceCard),
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumbModel != null) {
                                    AsyncImage(
                                        model = thumbModel,
                                        contentDescription = trk.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    com.omnitrack.app.ui.components.TrackerBrandLogo(
                                        serviceKey = trk.key,
                                        size = 16.dp,
                                        cornerRadius = 4.dp,
                                        innerPadding = 2.dp,
                                        containerColor = AmoledBlack.copy(alpha = 0.85f),
                                        borderColor = Color.White.copy(alpha = 0.16f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(3.dp)
                                    )
                                } else {
                                    com.omnitrack.app.ui.components.TrackerBrandLogo(
                                        serviceKey = trk.key,
                                        size = 30.dp,
                                        cornerRadius = 8.dp,
                                        innerPadding = 4.dp,
                                        containerColor = Color.White.copy(alpha = 0.04f),
                                        borderColor = Color.White.copy(alpha = 0.10f),
                                        alpha = 0.78f
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(statusDotColor)
                                )
                            }
                            Text(
                                text = trk.name,
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) AccentGold else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = statusWord,
                                fontSize = 9.sp,
                                color = statusDotColor,
                                maxLines = 1
                            )
                        }
                    }

                    // Trailing "+ All" cross-category expander tile
                    item(key = "all_trackers_toggle") {
                        Column(
                            modifier = Modifier
                                .width(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (showAllTrackers) AccentGold.copy(alpha = 0.18f)
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .clickable { showAllTrackers = !showAllTrackers }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(46.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color.White.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (showAllTrackers) Icons.Default.Close else Icons.Default.Add,
                                    contentDescription = "All Trackers",
                                    tint = AccentGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = if (showAllTrackers) "Relevant" else "+ All",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGold
                            )
                            Text(
                                text = "11 Apps",
                                fontSize = 9.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                // 3. Focused Borderless Tracker Studio
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val activeSnap = matchedSnapshots[activeTracker.key]

                    if (isUniversalMode) {
                        val readyTrackers = trackers.filter { it.boundId != null && it.isSignedIn && it.supportsPush }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "UNIVERSAL SYNC TARGETS (ADAPTS & PUSHES ALL AT ONCE)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.8.sp
                            )
                            if (readyTrackers.isEmpty()) {
                                val boundButSignedOut = trackers.filter { it.boundId != null && !it.isSignedIn && it.supportsPush }
                                Text(
                                    text = if (boundButSignedOut.isNotEmpty()) {
                                        "Sign in to ${boundButSignedOut.joinToString(", ") { it.name }} in Settings → Tracker Accounts to push, or tap any service tile above to link accounts."
                                    } else {
                                        "No connected trackers linked yet. Tap any tracker tile above (AniList, MAL, Trakt, Simkl, etc.) to bind it, then push everything together here."
                                    },
                                    fontSize = 12.5.sp,
                                    color = TextSecondary,
                                    lineHeight = 18.sp
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    readyTrackers.forEach { trk ->
                                        val enabled = trk.key in universalTargetKeys
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (enabled) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                                                .clickable {
                                                    universalTargetKeys = if (enabled) universalTargetKeys - trk.key else universalTargetKeys + trk.key
                                                }
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            com.omnitrack.app.ui.components.TrackerBrandLogo(
                                                serviceKey = trk.key,
                                                size = 16.dp,
                                                cornerRadius = 4.dp,
                                                innerPadding = 2.dp
                                            )
                                            Text(
                                                text = trk.name,
                                                fontSize = 12.sp,
                                                fontWeight = if (enabled) FontWeight.Bold else FontWeight.Medium,
                                                color = if (enabled) AccentGold else TextSecondary
                                            )
                                            if (enabled) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = AccentGold,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 3a. Matched Remote Entry Showcase (with bigger 2:3 art & diff)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val rawHeroCover = if (activeTracker.boundId != null) {
                                localItem.customCoverPath?.takeIf { it.isNotBlank() }
                                    ?: activeSnap?.coverUrl?.takeIf { it.isNotBlank() }
                                    ?: localItem.coverUrl.takeIf { it.isNotBlank() }
                            } else {
                                activeSnap?.coverUrl?.takeIf { it.isNotBlank() }
                            }
                            val heroCoverModel = ArtworkModelResolver.toCoilModel(rawHeroCover)
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceCard),
                                contentAlignment = Alignment.Center
                            ) {
                                if (heroCoverModel != null) {
                                    AsyncImage(
                                        model = heroCoverModel,
                                        contentDescription = activeTracker.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    com.omnitrack.app.ui.components.TrackerBrandLogo(
                                        serviceKey = activeTracker.key,
                                        size = 20.dp,
                                        cornerRadius = 5.dp,
                                        innerPadding = 3.dp,
                                        containerColor = AmoledBlack.copy(alpha = 0.85f),
                                        borderColor = Color.White.copy(alpha = 0.16f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(4.dp)
                                    )
                                } else {
                                    com.omnitrack.app.ui.components.TrackerBrandLogo(
                                        serviceKey = activeTracker.key,
                                        size = 38.dp,
                                        cornerRadius = 9.dp,
                                        innerPadding = 5.dp,
                                        containerColor = Color.White.copy(alpha = 0.04f),
                                        borderColor = Color.White.copy(alpha = 0.10f)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = activeSnap?.title ?: if (activeTracker.boundId != null) localItem.title else "Not bound on ${activeTracker.name}",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val metaLine = listOfNotNull(
                                    activeTracker.boundId?.let { "ID #$it" },
                                    activeSnap?.format,
                                    activeSnap?.year,
                                    if (!activeTracker.isSignedIn && activeTracker.supportsPush) "Signed Out" else null
                                ).joinToString(" · ")
                                if (metaLine.isNotBlank()) {
                                    Text(
                                        text = metaLine,
                                        fontSize = 11.5.sp,
                                        color = if (!activeTracker.isSignedIn && activeTracker.supportsPush) Color(0xFFFCA5A5) else TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (activeSnap?.remoteProgress != null || activeSnap?.remoteStatus != null) {
                                    val rProg = activeSnap.remoteProgress?.toInt()?.toString() ?: "0"
                                    val rTot = activeSnap.remoteTotal?.toInt()?.let { "/$it" }.orEmpty()
                                    val rSt = activeSnap.remoteStatus?.labelFor(localItem.mediaType) ?: "Remote"
                                    Text(
                                        text = "Remote: $rSt · $rProg$rTot (Local ${localItem.currentProgress.toInt()})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentGold
                                    )
                                }
                            }
                        }

                        // 3b. Action Strip: Rebind · Pull · Push · Open in Browser (+ LOCG for Comics)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StudioActionPill(
                                icon = Icons.Default.Search,
                                label = if (activeTracker.boundId == null) "Search & Bind" else "Rebind",
                                accent = activeTracker.boundId == null,
                                onClick = { rebindTargetTracker = activeTracker }
                            )
                            if (activeTracker.boundId != null) {
                                StudioActionPill(
                                    icon = Icons.Default.CloudDownload,
                                    label = "Pull",
                                    onClick = { triggerPull(activeTracker.key) }
                                )
                                if (activeTracker.supportsPush) {
                                    StudioActionPill(
                                        icon = Icons.Default.CloudUpload,
                                        label = "Push",
                                        accent = true,
                                        onClick = { triggerPush(activeTracker.key) }
                                    )
                                }
                                activeTracker.browserUrlTemplate?.let { urlGen ->
                                    StudioActionPill(
                                        icon = Icons.Default.OpenInBrowser,
                                        label = "Open ↗",
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlGen(activeTracker.boundId))))
                                            }
                                        }
                                    )
                                }
                            }
                            if (localItem.mediaType == MediaType.COMIC) {
                                StudioActionPill(
                                    icon = Icons.Default.OpenInBrowser,
                                    label = "League of Comic Geeks ↗",
                                    onClick = {
                                        val url = "https://leagueofcomicgeeks.com/search?keyword=${Uri.encode(localItem.title)}"
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    // 3c. Status + Lock Status
                    val (isLockedForTracker, lockedStatusForTracker) = if (isUniversalMode) {
                        false to null
                    } else when (activeTracker.key) {
                        "ANILIST" -> localItem.isAnilistStatusLocked to localItem.anilistStatus
                        "MAL" -> localItem.isMalStatusLocked to localItem.malStatus
                        "BANGUMI" -> localItem.isBangumiStatusLocked to localItem.bangumiStatus
                        "TRAKT" -> localItem.isTraktStatusLocked to localItem.traktStatus
                        "SIMKL" -> localItem.isSimklStatusLocked to localItem.simklStatus
                        "COMICK" -> localItem.isComickStatusLocked to localItem.comickStatus
                        else -> false to null
                    }
                    val activeEffectiveStatus = if (isLockedForTracker && lockedStatusForTracker != null) {
                        lockedStatusForTracker
                    } else {
                        localItem.status
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STATUS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.8.sp
                            )
                            if (!isUniversalMode && activeTracker.supportsStatusLock) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isLockedForTracker) AccentGold.copy(alpha = 0.16f)
                                            else Color.White.copy(alpha = 0.06f)
                                        )
                                        .clickable {
                                            val nextLocked = !isLockedForTracker
                                            val updated = when (activeTracker.key) {
                                                "ANILIST" -> localItem.copy(isAnilistStatusLocked = nextLocked, anilistStatus = if (nextLocked) localItem.status else null)
                                                "MAL" -> localItem.copy(isMalStatusLocked = nextLocked, malStatus = if (nextLocked) localItem.status else null)
                                                "BANGUMI" -> localItem.copy(isBangumiStatusLocked = nextLocked, bangumiStatus = if (nextLocked) localItem.status else null)
                                                "TRAKT" -> localItem.copy(isTraktStatusLocked = nextLocked, traktStatus = if (nextLocked) localItem.status else null)
                                                "SIMKL" -> localItem.copy(isSimklStatusLocked = nextLocked, simklStatus = if (nextLocked) localItem.status else null)
                                                "COMICK" -> localItem.copy(isComickStatusLocked = nextLocked, comickStatus = if (nextLocked) localItem.status else null)
                                                else -> localItem
                                            }
                                            commitLocalUpdate(updated)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isLockedForTracker) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Lock Status",
                                        tint = if (isLockedForTracker) AccentGold else TextSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (isLockedForTracker) "Status Locked to ${activeTracker.name}" else "Sync Status Globally",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isLockedForTracker) AccentGold else TextSecondary
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                TrackingStatus.IN_PROGRESS,
                                TrackingStatus.PLAN_TO_TRACK,
                                TrackingStatus.COMPLETED,
                                TrackingStatus.ON_HOLD,
                                TrackingStatus.DROPPED
                            ).forEach { st ->
                                val selected = activeEffectiveStatus == st
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) AccentGold else Color.White.copy(alpha = 0.08f))
                                        .clickable {
                                            val updated = if (isLockedForTracker) {
                                                when (activeTracker.key) {
                                                    "ANILIST" -> localItem.copy(anilistStatus = st)
                                                    "MAL" -> localItem.copy(malStatus = st)
                                                    "BANGUMI" -> localItem.copy(bangumiStatus = st)
                                                    "TRAKT" -> localItem.copy(traktStatus = st)
                                                    "SIMKL" -> localItem.copy(simklStatus = st)
                                                    "COMICK" -> localItem.copy(comickStatus = st)
                                                    else -> localItem.copy(status = st)
                                                }
                                            } else {
                                                val now = System.currentTimeMillis()
                                                localItem.copy(
                                                    status = st,
                                                    startedAtMs = localItem.startedAtMs ?: if (st == TrackingStatus.IN_PROGRESS) now else null,
                                                    finishedAtMs = localItem.finishedAtMs ?: if (st == TrackingStatus.COMPLETED) now else null
                                                )
                                            }
                                            commitLocalUpdate(updated)
                                        }
                                        .padding(horizontal = 13.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = st.labelFor(localItem.mediaType),
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) AmoledBlack else TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    // 3d. Progress + Volumes + Split-Cour Season Offset
                    val curOverride = overridesMap[activeTracker.key] ?: TrackerOverrideConfig()
                    val isDecimalHours = localItem.mediaType == MediaType.VISUAL_NOVEL || localItem.mediaType == MediaType.GAME
                    val currentProgressDisplayVal = if (isDecimalHours) {
                        maxOf(localItem.playtimeHours, localItem.currentProgress)
                    } else {
                        localItem.currentProgress
                    }
                    fun applyProgressValue(rawVal: Double) {
                        val clampedMin = rawVal.coerceAtLeast(0.0)
                        val maxT = localItem.totalProgress
                        val clamped = if (maxT != null && maxT > 0.0) clampedMin.coerceAtMost(maxT) else clampedMin
                        val finalVal = if (isDecimalHours) (clamped * 10.0).roundToInt() / 10.0 else clamped
                        commitLocalUpdate(
                            if (isDecimalHours) {
                                localItem.copy(currentProgress = finalVal, playtimeHours = finalVal)
                            } else {
                                localItem.copy(currentProgress = finalVal)
                            }
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (isDecimalHours) "PLAYTIME & PROGRESS (HOLD −/+ TO REPEAT · TAP NUMBER)" else "PROGRESS & SPLIT-COUR OFFSET (HOLD −/+ TO REPEAT)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.8.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showExactProgressDialog = true }
                                    .padding(vertical = 2.dp, horizontal = 2.dp)
                            ) {
                                val totStr = localItem.totalProgress?.takeIf { it > 0.0 }?.let { t ->
                                    if (isDecimalHours) " / ${String.format(Locale.US, "%.1f", t)}" else " / ${t.toInt()}"
                                }.orEmpty()
                                val valStr = if (isDecimalHours) {
                                    String.format(Locale.US, "%.1f", currentProgressDisplayVal)
                                } else {
                                    currentProgressDisplayVal.toInt().toString()
                                }
                                Text(
                                    text = "${localItem.mediaType.unitLabel}: $valStr$totStr",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                if (curOverride.episodeOffset != 0) {
                                    val pushed = (localItem.currentProgress.toInt() + curOverride.episodeOffset).coerceAtLeast(0)
                                    Text(
                                        text = "${activeTracker.name} pushes: $pushed (offset ${if (curOverride.episodeOffset > 0) "+" else ""}${curOverride.episodeOffset})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentGold
                                    )
                                } else {
                                    Text(
                                        text = "Tap number to enter exact value",
                                        fontSize = 10.5.sp,
                                        color = TextSecondary.copy(alpha = 0.72f)
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallStepCircle(Icons.Default.Remove) {
                                    val step = if (isDecimalHours) 0.5 else 1.0
                                    applyProgressValue(currentProgressDisplayVal - step)
                                }
                                SmallStepCircle(Icons.Default.Add, accent = true) {
                                    val step = if (isDecimalHours) 0.5 else 1.0
                                    applyProgressValue(currentProgressDisplayVal + step)
                                }
                            }
                        }

                        // Volumes stepper for Manga / Novels / Comics
                        if (localItem.mediaType == MediaType.MANGA || localItem.mediaType == MediaType.NOVEL || localItem.mediaType == MediaType.COMIC || activeTracker.key in listOf("ANILIST", "MAL", "BANGUMI")) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Volumes Read: ${localItem.volumesProgress}",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SmallStepCircle(Icons.Default.Remove) {
                                        commitLocalUpdate(localItem.copy(volumesProgress = (localItem.volumesProgress - 1).coerceAtLeast(0)))
                                    }
                                    SmallStepCircle(Icons.Default.Add, accent = true) {
                                        commitLocalUpdate(localItem.copy(volumesProgress = localItem.volumesProgress + 1))
                                    }
                                }
                            }
                        }

                        // Steam Playtime & Achievements read/edit row
                        if (activeTracker.key == "STEAM" || localItem.mediaType == MediaType.GAME) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Playtime: ${String.format(Locale.US, "%.1f", localItem.playtimeHours)}h" +
                                        (localItem.steamAchievementsUnlocked?.let { u ->
                                            val t = localItem.steamAchievementsTotal ?: 0
                                            " · $u/$t Achievements"
                                        }.orEmpty()),
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Split-cour offset controls for episode-indexed trackers (only shown per-tracker)
                        if (!isUniversalMode && activeTracker.key in listOf("ANILIST", "MAL", "BANGUMI", "SIMKL", "TRAKT")) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Offset:", fontSize = 11.5.sp, color = TextSecondary)
                                listOf(-12, -1, 0, 1, 12).forEach { delta ->
                                    val label = if (delta == 0) "Reset (0)" else if (delta > 0) "+$delta" else "$delta"
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .clickable {
                                                val nextOffset = if (delta == 0) 0 else curOverride.episodeOffset + delta
                                                val nextMap = overridesMap.toMutableMap()
                                                nextMap[activeTracker.key] = curOverride.copy(episodeOffset = nextOffset)
                                                commitLocalUpdate(
                                                    localItem.copy(
                                                        trackerOverridesJson = TrackerMetadataCodec.encodeOverrides(nextMap)
                                                    )
                                                )
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (delta == 0) AccentGold else TextPrimary)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    // 3e. Rating & Native Scale Conversion
                    var sliderVal by remember(localItem.id, localItem.userRating) {
                        mutableFloatStateOf(localItem.userRating?.coerceIn(0f, 10f) ?: 0f)
                    }
                    val nativePreview = remember(sliderVal, activeTracker.key, isUniversalMode, universalTargetKeys) {
                        if (sliderVal < 0.5f) "Unrated"
                        else if (isUniversalMode) {
                            val parts = universalTargetKeys.map { k ->
                                when (k) {
                                    "ANILIST" -> "AL ${(sliderVal * 10f).roundToInt()}/100"
                                    "MAL" -> "MAL ${sliderVal.roundToInt()}/10"
                                    "BANGUMI" -> "BGM ${sliderVal.roundToInt()}/10"
                                    "TRAKT" -> "Trakt ${sliderVal.roundToInt()}/10"
                                    "SIMKL" -> "Simkl ${sliderVal.roundToInt()}/10"
                                    "TMDB" -> "TMDb ${((sliderVal * 2f).roundToInt() / 2.0)}/10"
                                    "HARDCOVER" -> "HC ${((sliderVal / 2f) * 2f).roundToInt() / 2.0}★"
                                    "VNDB" -> "VNDB ${(sliderVal * 10f).roundToInt()}/100"
                                    else -> k
                                }
                            }
                            if (parts.isEmpty()) "No targets" else parts.joinToString(" · ")
                        } else when (activeTracker.key) {
                            "ANILIST", "VNDB", "IGDB" -> "${(sliderVal * 10f).roundToInt()}/100"
                            "HARDCOVER" -> "${((sliderVal / 2f) * 2f).roundToInt() / 2.0}★ / 5"
                            "TMDB" -> "${((sliderVal * 2f).roundToInt() / 2.0)}/10"
                            else -> "${sliderVal.roundToInt()}/10"
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isUniversalMode) "UNIVERSAL RATING (AUTO-ADAPTED)" else "RATING · ${activeTracker.scoreFormat.uppercase()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = if (sliderVal < 0.5f) "Unrated" else "${String.format(Locale.US, "%.1f", sliderVal)}/10",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGold
                            )
                        }
                        if (sliderVal >= 0.5f) {
                            Text(
                                text = nativePreview,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Slider(
                            value = sliderVal,
                            onValueChange = { sliderVal = ((it * 10f).roundToInt()) / 10f },
                            onValueChangeFinished = {
                                val nextRating = if (sliderVal < 0.95f) null else sliderVal.coerceIn(1.0f, 10.0f)
                                commitLocalUpdate(localItem.copy(userRating = nextRating))
                            },
                            valueRange = 0f..10f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentGold,
                                activeTrackColor = AccentGold,
                                inactiveTrackColor = Color.White.copy(alpha = 0.14f)
                            )
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    // 3f. Dates & Rewatches 3-Column Row
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { datePickTarget = "START" },
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("STARTED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Text(formatSheetDate(localItem.startedAtMs), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { datePickTarget = "FINISH" },
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("FINISHED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Text(formatSheetDate(localItem.finishedAtMs), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("REWATCHES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "-", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
                                    modifier = Modifier.repeatingClickable {
                                        val nextR = (localItem.rewatchCount - 1).coerceAtLeast(0)
                                        commitLocalUpdate(localItem.copy(rewatchCount = nextR, malRewatches = nextR))
                                    }.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                Text("${localItem.rewatchCount}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentGold)
                                Text(
                                    "+", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentGold,
                                    modifier = Modifier.repeatingClickable {
                                        val nextR = localItem.rewatchCount + 1
                                        commitLocalUpdate(localItem.copy(rewatchCount = nextR, malRewatches = nextR))
                                    }.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    // 3g. Unified Personal Note + Custom Per-Tracker Override + Real Cap Counter
                    val noteCap = if (isUniversalMode) 2000 else activeTracker.noteCap
                    val isCustomOverride = !isUniversalMode && curOverride.useCustomNote
                    val activeNoteText = if (isCustomOverride) {
                        curOverride.customNote.orEmpty()
                    } else {
                        localItem.personalNotes ?: when (if (isUniversalMode) "ALL" else activeTracker.key) {
                            "ANILIST" -> localItem.anilistNotes.orEmpty()
                            "MAL" -> localItem.malComments.orEmpty()
                            "BANGUMI" -> localItem.bangumiComment.orEmpty()
                            "SIMKL" -> localItem.simklMemo.orEmpty()
                            else -> ""
                        }
                    }
                    var draftNote by remember(localItem.id, selectedTrackerKey, isCustomOverride, activeNoteText) {
                        mutableStateOf(activeNoteText)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PERSONAL NOTE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    letterSpacing = 0.8.sp
                                )
                                val capStr = if (noteCap > 0) "${draftNote.length}/$noteCap chars" else "${draftNote.length} chars"
                                Text(
                                    text = if (isUniversalMode) "Auto-adapted per service limit · $capStr"
                                    else if (isCustomOverride) "Custom for ${activeTracker.name} · $capStr"
                                    else "Mirrored with Details · $capStr",
                                    fontSize = 10.5.sp,
                                    color = if (noteCap > 0 && draftNote.length > noteCap) Color(0xFFF59E0B) else AccentGold.copy(alpha = 0.85f)
                                )
                            }
                            if (!isUniversalMode) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCustomOverride) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f))
                                        .clickable {
                                            val nextOv = curOverride.copy(
                                                useCustomNote = !isCustomOverride,
                                                customNote = if (!isCustomOverride && curOverride.customNote.isNullOrBlank()) draftNote else curOverride.customNote
                                            )
                                            val nextMap = overridesMap.toMutableMap()
                                            nextMap[activeTracker.key] = nextOv
                                            commitLocalUpdate(localItem.copy(trackerOverridesJson = TrackerMetadataCodec.encodeOverrides(nextMap)))
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isCustomOverride) "Custom Override" else "Global Note",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCustomOverride) AccentGold else TextSecondary
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            BasicTextField(
                                value = draftNote,
                                onValueChange = { next ->
                                    draftNote = next
                                    val clean = next.takeIf { it.isNotEmpty() }
                                    if (isCustomOverride) {
                                        val nextMap = overridesMap.toMutableMap()
                                        nextMap[activeTracker.key] = curOverride.copy(useCustomNote = true, customNote = clean)
                                        commitLocalUpdate(localItem.copy(trackerOverridesJson = TrackerMetadataCodec.encodeOverrides(nextMap)))
                                    } else {
                                        commitLocalUpdate(
                                            localItem.copy(
                                                personalNotes = clean,
                                                anilistNotes = clean?.take(2000),
                                                malComments = clean?.take(1000),
                                                bangumiComment = clean?.take(380),
                                                simklMemo = clean?.take(140)
                                            )
                                        )
                                    }
                                },
                                textStyle = TextStyle(color = TextPrimary, fontSize = 13.5.sp, lineHeight = 19.sp),
                                cursorBrush = SolidColor(AccentGold),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (draftNote.isEmpty()) {
                                Text(
                                    text = if (isUniversalMode) "Write a unified personal note..." else "Write a note for ${activeTracker.name}...",
                                    fontSize = 13.sp,
                                    color = TextSecondary.copy(alpha = 0.55f)
                                )
                            }
                        }
                    }

                    if (isUniversalMode) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                triggerPush(targetKey = null, itemToPush = localItem, selectedKeys = universalTargetKeys)
                            },
                            enabled = !isSyncingAll && universalTargetKeys.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGold,
                                contentColor = AmoledBlack,
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                disabledContentColor = TextSecondary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (universalTargetKeys.isEmpty()) "No Linked & Signed-In Trackers Selected"
                                else "Save & Sync All Connected (${universalTargetKeys.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }

                    // 3h. Native Tracker Extras (per individual tracker)
                    if (!isUniversalMode) when (activeTracker.key) {
                        "ANILIST" -> {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("ANILIST EXTRAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 0.8.sp)
                                TrackerToggleRow(
                                    label = "Favorite on AniList",
                                    leadingIcon = if (localItem.anilistFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    leadingIconTint = if (localItem.anilistFavorite) AccentGold else TextSecondary,
                                    checked = localItem.anilistFavorite,
                                    onToggle = { v ->
                                        commitLocalUpdate(localItem.copy(anilistFavorite = v, isFavorite = v || localItem.isFavorite))
                                    }
                                )
                                TrackerToggleRow(
                                    label = "Private Entry",
                                    checked = localItem.anilistPrivate,
                                    onToggle = { v -> commitLocalUpdate(localItem.copy(anilistPrivate = v)) }
                                )
                                TrackerToggleRow(
                                    label = "Hide from Status Lists",
                                    checked = localItem.anilistHiddenFromStatus,
                                    onToggle = { v -> commitLocalUpdate(localItem.copy(anilistHiddenFromStatus = v)) }
                                )
                            }
                        }
                        "MAL" -> {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("MYANIMELIST EXTRAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 0.8.sp)
                                TrackerToggleRow(
                                    label = "Currently Rewatching / Rereading",
                                    checked = localItem.malRewatching,
                                    onToggle = { v -> commitLocalUpdate(localItem.copy(malRewatching = v)) }
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Priority:", fontSize = 12.5.sp, color = TextSecondary)
                                    listOf(0 to "Low", 1 to "Medium", 2 to "High").forEach { (pVal, pLbl) ->
                                        val sel = localItem.malPriority == pVal
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (sel) AccentGold else Color.White.copy(alpha = 0.08f))
                                                .clickable { commitLocalUpdate(localItem.copy(malPriority = pVal)) }
                                                .padding(horizontal = 11.dp, vertical = 5.dp)
                                        ) {
                                            Text(pLbl, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (sel) AmoledBlack else TextPrimary)
                                        }
                                    }
                                }
                            }
                        }
                        "BANGUMI" -> {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("BANGUMI EXTRAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 0.8.sp)
                                TrackerToggleRow(
                                    label = "Visible to Friends Only (Private)",
                                    checked = localItem.bangumiPrivate,
                                    onToggle = { v -> commitLocalUpdate(localItem.copy(bangumiPrivate = v)) }
                                )
                            }
                        }
                        "SIMKL" -> {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("SIMKL EXTRAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 0.8.sp)
                                TrackerToggleRow(
                                    label = "Private Memo",
                                    checked = localItem.simklPrivate,
                                    onToggle = { v -> commitLocalUpdate(localItem.copy(simklPrivate = v)) }
                                )
                            }
                        }
                        "HARDCOVER" -> {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("HARDCOVER EXTRAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 0.8.sp)
                                TrackerToggleRow(
                                    label = "Private Shelf Entry",
                                    checked = localItem.anilistPrivate,
                                    onToggle = { v -> commitLocalUpdate(localItem.copy(anilistPrivate = v)) }
                                )
                            }
                        }
                    }

                    // 3i. Danger Footer: Unlink Locally | Delete from Tracker
                    if (!isUniversalMode && activeTracker.boundId != null) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.07f))
                                    .clickable {
                                        val unlinked = activeTracker.onUpdateBoundId(null)
                                        val nextMap = matchedSnapshots.toMutableMap().apply { remove(activeTracker.key) }
                                        val cleaned = unlinked.copy(matchedTrackerMetaJson = TrackerMetadataCodec.encodeMatchedSnapshots(nextMap))
                                        commitLocalUpdate(cleaned)
                                        syncStatusBanner = true to "Unlinked ${activeTracker.name} locally"
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Unlink Locally", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.16f))
                                    .clickable { deleteDialogTracker = activeTracker }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Delete Remote", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFCA5A5))
                            }
                        }
                    }
                }
            }
        }
    }

    // Native Per-Tracker Rebind Sheet
    rebindTargetTracker?.let { trk ->
        SearchAndRebindBottomSheet(
            tracker = trk,
            initialQuery = localItem.title,
            mediaType = localItem.mediaType,
            syncEngine = syncEngine,
            presentationSettings = presentationSettings,
            backdropBitmap = backdropBitmap,
            backdropTargetUrl = backdropTargetUrl,
            onBound = { snap ->
                val withId = trk.onUpdateBoundId(snap.remoteId)
                val nextSnaps = TrackerMetadataCodec.decodeMatchedSnapshots(withId.matchedTrackerMetaJson).toMutableMap()
                nextSnaps[trk.key] = snap
                val updated = withId.copy(matchedTrackerMetaJson = TrackerMetadataCodec.encodeMatchedSnapshots(nextSnaps))
                commitLocalUpdate(updated)
                rebindTargetTracker = null
                pullPromptAfterBind = trk.key
            },
            onDismiss = { rebindTargetTracker = null }
        )
    }

    // 1-Tap Confirmation Dialog: Remove from <Tracker> Only OR Remove & Delete Local Entry
    deleteDialogTracker?.let { trk ->
        AlertDialog(
            onDismissRequest = { deleteDialogTracker = null },
            containerColor = DarkSurface,
            title = {
                Text("Delete from ${trk.name}?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Choose whether to remove this entry only from your ${trk.name} account, or delete both the remote entry and your local OmniTrack entry.",
                    color = TextSecondary,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            deleteDialogTracker = null
                            val unlinked = trk.onUpdateBoundId(null)
                            val nextSnaps = matchedSnapshots.toMutableMap().apply { remove(trk.key) }
                            val cleaned = unlinked.copy(matchedTrackerMetaJson = TrackerMetadataCodec.encodeMatchedSnapshots(nextSnaps))
                            val runner: (String, Boolean, MediaItemEntity, (TrackerSyncOutcome, Boolean) -> Unit) -> Unit =
                                onDeleteFromRemote ?: { k, alsoLocal, u, cb ->
                                    scope.launch {
                                        val out = syncEngine.deleteRemoteEntry(k, localItem)
                                        if (!alsoLocal) commitLocalUpdate(u)
                                        cb(out, alsoLocal && out.success)
                                    }
                                }
                            runner(trk.key, false, cleaned) { out, _ ->
                                if (out.success) localItem = cleaned
                                syncStatusBanner = out.success to out.message
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.22f), contentColor = Color(0xFFFCA5A5)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remove from ${trk.name} Only", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            deleteDialogTracker = null
                            val unlinked = trk.onUpdateBoundId(null)
                            val runner: (String, Boolean, MediaItemEntity, (TrackerSyncOutcome, Boolean) -> Unit) -> Unit =
                                onDeleteFromRemote ?: { k, alsoLocal, u, cb ->
                                    scope.launch {
                                        val out = syncEngine.deleteRemoteEntry(k, localItem)
                                        cb(out, alsoLocal && out.success)
                                    }
                                }
                            runner(trk.key, true, unlinked) { out, deletedLocal ->
                                if (deletedLocal) {
                                    onDeleteLocalEntry()
                                    onDismiss()
                                } else {
                                    syncStatusBanner = out.success to out.message
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remove from ${trk.name} & Delete Local", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogTracker = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Start / Finish Date Picker
    datePickTarget?.let { target ->
        val initMs = if (target == "START") localItem.startedAtMs else localItem.finishedAtMs
        val state = rememberDatePickerState(initialSelectedDateMillis = initMs ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { datePickTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = state.selectedDateMillis
                    commitLocalUpdate(
                        if (target == "START") localItem.copy(startedAtMs = chosen) else localItem.copy(finishedAtMs = chosen)
                    )
                    datePickTarget = null
                }) {
                    Text("Set Date", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    commitLocalUpdate(
                        if (target == "START") localItem.copy(startedAtMs = null) else localItem.copy(finishedAtMs = null)
                    )
                    datePickTarget = null
                }) {
                    Text("Clear", color = TextSecondary)
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showExactProgressDialog) {
        val isDecimalHours = localItem.mediaType == MediaType.VISUAL_NOVEL || localItem.mediaType == MediaType.GAME
        val initialNum = if (isDecimalHours) maxOf(localItem.playtimeHours, localItem.currentProgress) else localItem.currentProgress
        var exactInput by remember(showExactProgressDialog) {
            mutableStateOf(
                if (isDecimalHours) String.format(Locale.US, "%.1f", initialNum)
                else initialNum.toInt().toString()
            )
        }
        AlertDialog(
            onDismissRequest = { showExactProgressDialog = false },
            containerColor = AmoledBlack,
            title = {
                Text(
                    text = "Set ${localItem.mediaType.unitLabel}",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = exactInput,
                    onValueChange = { exactInput = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isDecimalHours) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    label = { Text(localItem.mediaType.unitLabel, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGold,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentGold
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = exactInput.trim().toDoubleOrNull()
                        if (parsed != null) {
                            val clampedMin = parsed.coerceAtLeast(0.0)
                            val maxT = localItem.totalProgress
                            val clamped = if (maxT != null && maxT > 0.0) clampedMin.coerceAtMost(maxT) else clampedMin
                            val finalVal = if (isDecimalHours) (clamped * 10.0).roundToInt() / 10.0 else clamped
                            commitLocalUpdate(
                                if (isDecimalHours) {
                                    localItem.copy(currentProgress = finalVal, playtimeHours = finalVal)
                                } else {
                                    localItem.copy(currentProgress = finalVal)
                                }
                            )
                        }
                        showExactProgressDialog = false
                    }
                ) {
                    Text("Apply", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExactProgressDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndRebindBottomSheet(
    tracker: TrackerDef,
    initialQuery: String,
    mediaType: MediaType,
    syncEngine: TrackerSyncEngine,
    presentationSettings: PresentationSettings?,
    backdropBitmap: ImageBitmap?,
    backdropTargetUrl: String?,
    onBound: (RemoteTrackerEntrySnapshot) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<RemoteTrackerEntrySnapshot>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(tracker.key, query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        try {
            kotlinx.coroutines.delay(250)
            results = syncEngine.searchTrackerDirectly(tracker.key, trimmed, mediaType)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            results = emptyList()
        } finally {
            isSearching = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        BackHandler { onDismiss() }
        DetailSheetBackdrop(
            presentationSettings = presentationSettings,
            backdropBitmap = backdropBitmap,
            backdropTargetUrl = backdropTargetUrl,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Search & Bind · ${tracker.name}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Direct lookup on ${tracker.name} catalog", fontSize = 11.5.sp, color = TextSecondary)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                // Direct Search Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AccentGold),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.weight(1f)
                    )
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { query = "" }
                        )
                    }
                }

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentGold, strokeWidth = 2.dp)
                    }
                } else if (results.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("No matches on ${tracker.name} for \"$query\"", fontSize = 13.sp, color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(results, key = { idx, r -> "${r.trackerKey}_${r.remoteId}_$idx" }) { idx, hit ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onBound(hit) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceCard)
                                ) {
                                    if (!hit.coverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = hit.coverUrl,
                                            contentDescription = hit.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(hit.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val sub = listOfNotNull(hit.format, hit.year, "ID #${hit.remoteId}").joinToString(" · ")
                                    Text(sub, fontSize = 11.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    text = "Bind",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmoledBlack,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(AccentGold)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            if (idx < results.lastIndex) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (accent) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (accent) AccentGold else TextPrimary, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (accent) AccentGold else TextPrimary)
    }
}

@Composable
private fun SmallStepCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (accent) AccentGold.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
            .repeatingClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (accent) AccentGold else TextPrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun TrackerToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    leadingIconTint: Color = TextSecondary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = leadingIconTint,
                    modifier = Modifier.size(17.dp)
                )
            }
            Text(label, fontSize = 13.5.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AmoledBlack,
                checkedTrackColor = AccentGold,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f)
            )
        )
    }
}

private fun formatSheetDate(ms: Long?): String {
    if (ms == null || ms <= 0L) return "Not set"
    return runCatching {
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM d, yy", Locale.US))
    }.getOrDefault("Not set")
}
