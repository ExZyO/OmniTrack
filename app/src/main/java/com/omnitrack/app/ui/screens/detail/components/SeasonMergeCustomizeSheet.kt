package com.omnitrack.app.ui.screens.detail.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.omnitrack.app.data.artwork.ArtworkModelResolver
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.AnimeRelationItem
import com.omnitrack.app.data.model.MediaSeason
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.preferences.CustomSeriesSeasonConfig
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonMergeCustomizeSheet(
    currentItem: MediaItemEntity,
    rootMediaId: Long,
    allCustomizedSeasons: List<MediaSeason>,
    rawSeasons: List<MediaSeason>,
    customConfig: CustomSeriesSeasonConfig,
    siblingSeasonEntities: Map<Int, MediaItemEntity>,
    allLibraryItems: List<MediaItemEntity>,
    animeRelations: List<AnimeRelationItem>,
    fallbackCoverUrl: String?,
    onUpdateConfig: ((CustomSeriesSeasonConfig) -> CustomSeriesSeasonConfig) -> Unit,
    onResetConfig: () -> Unit,
    onMergeLibraryEntry: (MediaItemEntity) -> Unit,
    onMergeOnlineResult: (SearchResultItem) -> Unit,
    onMergeRelation: (AnimeRelationItem) -> Unit,
    onUnlinkSeason: (Int) -> Unit,
    onOpenLinkedEntry: (MediaItemEntity) -> Unit,
    onSearchOnline: suspend (String) -> List<SearchResultItem>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 = Customize, 1 = Merge / Add
    var editingSeasonNum by remember { mutableStateOf<Int?>(null) }
    var isDraggingAny by remember { mutableStateOf(false) }

    val orderedSeasons = remember { mutableStateListOf<MediaSeason>() }
    LaunchedEffect(allCustomizedSeasons, isDraggingAny) {
        if (!isDraggingAny) {
            orderedSeasons.clear()
            orderedSeasons.addAll(allCustomizedSeasons)
        }
    }
    val customizeListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(customizeListState) { from, to ->
        val fromIdx = orderedSeasons.indexOfFirst { it.seasonNumber == (from.key as? Int) }
        val toIdx = orderedSeasons.indexOfFirst { it.seasonNumber == (to.key as? Int) }
        if (fromIdx in orderedSeasons.indices && toIdx in orderedSeasons.indices && fromIdx != toIdx) {
            val moved = orderedSeasons.removeAt(fromIdx)
            orderedSeasons.add(toIdx, moved)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val visibleCount = remember(allCustomizedSeasons, customConfig.hiddenSeasons) {
        allCustomizedSeasons.count { it.seasonNumber !in customConfig.hiddenSeasons }
    }
    val hasCustomizations = remember(customConfig) { !customConfig.isEmpty() }

    // Linked / merged member IDs for "already merged" status badges in Merge / Add tab
    val mergedEntityIds = remember(currentItem.id, rootMediaId, siblingSeasonEntities) {
        buildSet {
            add(currentItem.id)
            if (rootMediaId > 0L) add(rootMediaId)
            siblingSeasonEntities.values.forEach { if (it.id > 0L) add(it.id) }
        }
    }
    val mergedAnilistIds = remember(currentItem, siblingSeasonEntities) {
        buildSet {
            currentItem.anilistId?.let { add(it) }
            siblingSeasonEntities.values.forEach { it.anilistId?.let { id -> add(id) } }
        }
    }
    val mergedTmdbIds = remember(currentItem, siblingSeasonEntities) {
        buildSet {
            currentItem.tmdbId?.let { add(it) }
            siblingSeasonEntities.values.forEach { it.tmdbId?.let { id -> add(id) } }
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
                .navigationBarsPadding()
                .padding(top = 18.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SEASONS STUDIO",
                        color = AccentGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = currentItem.title,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (hasCustomizations) {
                        Text(
                            text = "Reset",
                            color = Color(0xFFEF4444).copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onResetConfig() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }
            }

            // Option A Underline Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                listOf("Customize", "Merge / Add").forEachIndexed { idx, title ->
                    val selected = selectedTabIndex == idx
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .clickable { selectedTabIndex = idx }
                            .padding(top = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title,
                            color = if (selected) TextPrimary else TextMuted,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(7.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (selected) AccentGold else Color.Transparent)
                        )
                    }
                }
            }
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                thickness = 0.6.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            if (selectedTabIndex == 0) {
                if (orderedSeasons.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Only one entry right now",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Switch to Merge / Add to attach sequels, OVAs, or movies as seasons.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Hold and drag the handle to reorder seasons. Tap Edit to rename or change cover.",
                        color = TextMuted,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    LazyColumn(
                        state = customizeListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 430.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(orderedSeasons, key = { it.seasonNumber }) { season ->
                            ReorderableItem(reorderableState, key = season.seasonNumber) { isDragging ->
                                val sNum = season.seasonNumber
                                val isHidden = sNum in customConfig.hiddenSeasons
                                val linkedEntity = siblingSeasonEntities[sNum]
                                val isManuallyMerged = linkedEntity != null &&
                                    linkedEntity.id > 0L &&
                                    linkedEntity.id != currentItem.id &&
                                    linkedEntity.linkedParentMediaId == rootMediaId
                                val canOpenLinked = linkedEntity != null &&
                                    linkedEntity.id > 0L &&
                                    linkedEntity.id != currentItem.id
                                val canHideToggle = isHidden || visibleCount > 1
                                val posterModel = remember(season.posterUrl, fallbackCoverUrl) {
                                    ArtworkModelResolver.toCoilModel(season.posterUrl ?: fallbackCoverUrl)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (isDragging) 10f else 1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isDragging) DarkSurfaceElevated else Color.Transparent)
                                        .alpha(if (isHidden) 0.52f else 1f)
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Reorder season",
                                        tint = if (isDragging) AccentGold else TextMuted,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .draggableHandle(
                                                onDragStarted = {
                                                    isDraggingAny = true
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragStopped = {
                                                    isDraggingAny = false
                                                    val newOrder = orderedSeasons.map { it.seasonNumber }
                                                    onUpdateConfig { cfg -> cfg.copy(seasonOrder = newOrder) }
                                                }
                                            )
                                    )
                                    AsyncImage(
                                        model = posterModel,
                                        contentDescription = season.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(36.dp)
                                            .aspectRatio(2f / 3f)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(DarkSurfaceElevated)
                                            .clickable { editingSeasonNum = sNum }
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { editingSeasonNum = sNum }
                                    ) {
                                        Text(
                                            text = season.name.ifBlank { if (sNum <= 0) "Specials" else "Season $sNum" },
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val metaParts = buildList {
                                            add("${season.episodeCount} eps")
                                            if (isManuallyMerged) add("Merged")
                                            if (isHidden) add("Hidden")
                                        }
                                        Text(
                                            text = metaParts.joinToString(" • "),
                                            color = if (isManuallyMerged) AccentJade else TextMuted,
                                            fontSize = 11.5.sp,
                                            maxLines = 1
                                        )
                                    }

                                    IconButton(
                                        onClick = { editingSeasonNum = sNum },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename or change poster",
                                            tint = AccentGold,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    IconButton(
                                        enabled = canHideToggle,
                                        onClick = {
                                            onUpdateConfig { cfg ->
                                                val nextHidden = if (sNum in cfg.hiddenSeasons) {
                                                    cfg.hiddenSeasons - sNum
                                                } else {
                                                    cfg.hiddenSeasons + sNum
                                                }
                                                cfg.copy(hiddenSeasons = nextHidden)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isHidden) "Show season" else "Hide season",
                                            tint = if (isHidden) TextMuted else TextSecondary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    if (canOpenLinked) {
                                        IconButton(
                                            onClick = {
                                                onDismiss()
                                                onOpenLinkedEntry(linkedEntity!!)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = "Open season entry page",
                                                tint = AccentJade,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }

                                    if (isManuallyMerged) {
                                        IconButton(
                                            onClick = { onUnlinkSeason(sNum) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LinkOff,
                                                contentDescription = "Unlink season",
                                                tint = Color(0xFFEF4444).copy(alpha = 0.9f),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            } else {
                // Tab 1: Merge / Add (Library + Suggestions + Online Search)
                var searchQuery by remember { mutableStateOf("") }
                var onlineResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
                var isSearchingOnline by remember { mutableStateOf(false) }
                var mergingOnlineKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

                LaunchedEffect(searchQuery) {
                    val trimmed = searchQuery.trim()
                    if (trimmed.length < 2) {
                        onlineResults = emptyList()
                        isSearchingOnline = false
                        return@LaunchedEffect
                    }
                    delay(400)
                    isSearchingOnline = true
                    onlineResults = runCatching { onSearchOnline(trimmed) }.getOrDefault(emptyList())
                    isSearchingOnline = false
                }

                val mergeEligibleLibrary = remember(allLibraryItems, mergedEntityIds) {
                    allLibraryItems.filter {
                        it.id !in mergedEntityIds &&
                            it.mediaType in setOf(MediaType.ANIME, MediaType.SHOW, MediaType.MOVIE)
                    }
                }

                val franchisePrefix = remember(currentItem.title) {
                    currentItem.title.split(":", "-", "–").firstOrNull()?.trim()?.lowercase().orEmpty()
                }

                val suggestedLibrary = remember(mergeEligibleLibrary, franchisePrefix, currentItem.tmdbId, animeRelations) {
                    val relationAnilistIds = animeRelations.map { it.id }.toSet()
                    val firstWord = currentItem.title.trim().split(" ").firstOrNull()?.lowercase().orEmpty()
                    mergeEligibleLibrary.filter { cand ->
                        (currentItem.tmdbId != null && cand.tmdbId == currentItem.tmdbId) ||
                            (cand.anilistId != null && cand.anilistId in relationAnilistIds) ||
                            (franchisePrefix.length >= 4 && cand.title.lowercase().contains(franchisePrefix)) ||
                            (firstWord.length >= 5 && cand.title.lowercase().contains(firstWord))
                    }.take(12)
                }

                val suggestedOnlineRelations = remember(animeRelations, mergedAnilistIds, mergedTmdbIds, mergeEligibleLibrary) {
                    val libAnilist = mergeEligibleLibrary.mapNotNull { it.anilistId }.toSet()
                    animeRelations.filter { rel ->
                        val isVideo = rel.mediaType in setOf(MediaType.ANIME, MediaType.SHOW, MediaType.MOVIE) ||
                            rel.format.uppercase() in setOf("TV", "MOVIE", "OVA", "ONA", "SPECIAL", "TV_SHORT")
                        val alreadyMerged = rel.id in mergedAnilistIds ||
                            (rel.tmdbId != null && rel.tmdbId in mergedTmdbIds) ||
                            rel.id in libAnilist
                        isVideo && !alreadyMerged && rel.relationType in setOf(
                            "SEQUEL", "PREQUEL", "SIDE_STORY", "SPIN_OFF", "PARENT", "ALTERNATIVE"
                        )
                    }.take(16)
                }

                val filteredLibrary = remember(mergeEligibleLibrary, searchQuery) {
                    val q = searchQuery.trim().lowercase()
                    if (q.isBlank()) mergeEligibleLibrary.take(30)
                    else mergeEligibleLibrary.filter {
                        it.title.lowercase().contains(q) ||
                            (it.nativeTitle?.lowercase()?.contains(q) == true)
                    }.take(30)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search library or online (seasons, movies, OVAs)...",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = Color(0xFF0A0A0A),
                            unfocusedContainerColor = Color(0xFF0A0A0A)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 410.dp)
                    ) {
                        if (searchQuery.isBlank()) {
                            if (suggestedLibrary.isNotEmpty() || suggestedOnlineRelations.isNotEmpty()) {
                                item(key = "hdr_suggested") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f, fill = false),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentGold, modifier = Modifier.size(14.dp))
                                            Text(
                                                text = "SUGGESTED SEQUELS, MOVIES & OVAS",
                                                color = AccentGold,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 0.8.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (suggestedLibrary.size + suggestedOnlineRelations.size >= 2) {
                                            Text(
                                                text = "Merge All",
                                                color = AccentGold,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(AccentGold.copy(alpha = 0.14f))
                                                    .clickable {
                                                        suggestedLibrary.forEach { onMergeLibraryEntry(it) }
                                                        suggestedOnlineRelations.forEach { onMergeRelation(it) }
                                                    }
                                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                items(suggestedLibrary, key = { "sug_lib_${it.id}" }) { cand ->
                                    MergeCandidateRow(
                                        coverUrl = ArtworkModelResolver.toCoilModel(cand.customCoverPath ?: cand.coverUrl),
                                        title = cand.title,
                                        subtitle = listOfNotNull(
                                            cand.mediaType.displayName,
                                            cand.releaseYear,
                                            "In Library"
                                        ).joinToString(" • "),
                                        actionLabel = "Merge",
                                        isAdded = false,
                                        onClick = { onMergeLibraryEntry(cand) }
                                    )
                                }

                                items(suggestedOnlineRelations, key = { "sug_rel_${it.id}_${it.format}" }) { rel ->
                                    MergeCandidateRow(
                                        coverUrl = ArtworkModelResolver.toCoilModel(rel.coverUrl),
                                        title = rel.title,
                                        subtitle = listOfNotNull(
                                            rel.format.ifBlank { rel.mediaType.displayName },
                                            rel.year?.toString(),
                                            rel.relationType.replace('_', ' ')
                                        ).joinToString(" • "),
                                        actionLabel = "Add & Merge",
                                        isAdded = false,
                                        onClick = { onMergeRelation(rel) }
                                    )
                                }
                            }

                            if (filteredLibrary.isNotEmpty()) {
                                item(key = "hdr_lib_browse") {
                                    Text(
                                        text = "FROM YOUR LIBRARY",
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                items(filteredLibrary, key = { "lib_all_${it.id}" }) { cand ->
                                    MergeCandidateRow(
                                        coverUrl = ArtworkModelResolver.toCoilModel(cand.customCoverPath ?: cand.coverUrl),
                                        title = cand.title,
                                        subtitle = listOfNotNull(
                                            cand.mediaType.displayName,
                                            cand.releaseYear,
                                            cand.totalProgress?.toInt()?.let { "$it eps" }
                                        ).joinToString(" • "),
                                        actionLabel = "Merge",
                                        isAdded = false,
                                        onClick = { onMergeLibraryEntry(cand) }
                                    )
                                }
                            }
                        } else {
                            if (filteredLibrary.isNotEmpty()) {
                                item(key = "hdr_search_lib") {
                                    Text(
                                        text = "IN YOUR LIBRARY",
                                        color = AccentJade,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(filteredLibrary, key = { "lib_q_${it.id}" }) { cand ->
                                    MergeCandidateRow(
                                        coverUrl = ArtworkModelResolver.toCoilModel(cand.customCoverPath ?: cand.coverUrl),
                                        title = cand.title,
                                        subtitle = listOfNotNull(
                                            cand.mediaType.displayName,
                                            cand.releaseYear,
                                            cand.totalProgress?.toInt()?.let { "$it eps" }
                                        ).joinToString(" • "),
                                        actionLabel = "Merge",
                                        isAdded = false,
                                        onClick = { onMergeLibraryEntry(cand) }
                                    )
                                }
                            }

                            item(key = "hdr_search_online") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ONLINE RESULTS",
                                        color = AccentGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                    if (isSearchingOnline) {
                                        CircularProgressIndicator(
                                            color = AccentGold,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            val dedupedOnline = onlineResults.filter { res ->
                                val alreadyByAl = res.anilistId != null && res.anilistId in mergedAnilistIds
                                val alreadyByTmdb = res.tmdbId != null && res.tmdbId in mergedTmdbIds
                                !alreadyByAl && !alreadyByTmdb &&
                                    res.mediaType in setOf(MediaType.ANIME, MediaType.SHOW, MediaType.MOVIE)
                            }
                            if (!isSearchingOnline && dedupedOnline.isEmpty()) {
                                item(key = "online_empty") {
                                    Text(
                                        text = "No matching online results yet.",
                                        color = TextMuted,
                                        fontSize = 12.5.sp,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            } else {
                                items(
                                    dedupedOnline,
                                    key = { "online_${it.mediaType}_${it.anilistId ?: it.tmdbId ?: it.malId ?: it.title}" }
                                ) { res ->
                                    val rKey = "${res.mediaType}_${res.anilistId ?: res.tmdbId ?: res.title}"
                                    val isAdded = rKey in mergingOnlineKeys
                                    MergeCandidateRow(
                                        coverUrl = ArtworkModelResolver.toCoilModel(res.coverUrl),
                                        title = res.title,
                                        subtitle = listOfNotNull(
                                            res.mediaType.displayName,
                                            res.releaseYear,
                                            res.totalProgress?.toInt()?.let { "$it eps" },
                                            res.sourceProvider
                                        ).joinToString(" • "),
                                        actionLabel = if (isAdded) "Merged" else "Add & Merge",
                                        isAdded = isAdded,
                                        onClick = {
                                            if (!isAdded) {
                                                mergingOnlineKeys = mergingOnlineKeys + rKey
                                                onMergeOnlineResult(res)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Name & Custom Season Cover Dialog
    editingSeasonNum?.let { targetSeasonNum ->
        val rawSeason = rawSeasons.firstOrNull { it.seasonNumber == targetSeasonNum }
        val defaultName = rawSeason?.name?.takeIf { it.isNotBlank() }
            ?: if (targetSeasonNum <= 0) "Specials" else "Season $targetSeasonNum"
        var nameInput by remember(targetSeasonNum) {
            mutableStateOf(customConfig.customNames[targetSeasonNum].orEmpty())
        }
        var posterInput by remember(targetSeasonNum) {
            mutableStateOf(customConfig.customPosters[targetSeasonNum].orEmpty())
        }

        val quickPosterChoices = remember(rawSeasons, siblingSeasonEntities, currentItem.coverUrl, currentItem.customCoverPath) {
            buildList {
                (currentItem.customCoverPath ?: currentItem.coverUrl).takeIf { it.isNotBlank() }?.let { add(it) }
                rawSeasons.mapNotNull { it.posterUrl?.takeIf { u -> u.isNotBlank() } }.forEach { add(it) }
                siblingSeasonEntities.values.mapNotNull { (it.customCoverPath ?: it.coverUrl).takeIf { u -> u.isNotBlank() } }.forEach { add(it) }
            }.distinct()
        }

        val galleryPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null && rootMediaId > 0L) {
                scope.launch {
                    val copiedPath = runCatching {
                        val dir = File(context.filesDir, "custom_season_posters")
                        if (!dir.exists()) dir.mkdirs()
                        val dest = File(dir, "season_${rootMediaId}_${targetSeasonNum}_${System.currentTimeMillis()}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(dest).use { output -> input.copyTo(output) }
                        }
                        dest.absolutePath
                    }.getOrNull()
                    if (!copiedPath.isNullOrBlank()) {
                        posterInput = copiedPath
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { editingSeasonNum = null },
            containerColor = DarkSurfaceElevated,
            title = {
                Text(
                    text = "Edit $defaultName",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "SEASON NAME",
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        placeholder = { Text(defaultName, color = TextMuted, fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "SEASON POSTER",
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(0.7.dp, AccentGold.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = AccentGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery", color = AccentGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (posterInput.isNotBlank()) {
                            TextButton(onClick = { posterInput = "" }) {
                                Text("Reset Poster", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = posterInput,
                        onValueChange = { posterInput = it },
                        placeholder = { Text("Or paste poster image URL...", color = TextMuted, fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (quickPosterChoices.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickPosterChoices.forEach { choice ->
                                val isChosen = posterInput == choice
                                AsyncImage(
                                    model = ArtworkModelResolver.toCoilModel(choice),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(44.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(
                                            width = if (isChosen) 2.dp else 0.6.dp,
                                            color = if (isChosen) AccentGold else GlassBorder,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { posterInput = choice }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmedName = nameInput.trim().takeIf { it.isNotBlank() && it != defaultName }
                        val trimmedPoster = posterInput.trim().takeIf { it.isNotBlank() }
                        onUpdateConfig { cfg ->
                            val nextNames = cfg.customNames.toMutableMap()
                            if (trimmedName == null) nextNames.remove(targetSeasonNum)
                            else nextNames[targetSeasonNum] = trimmedName

                            val nextPosters = cfg.customPosters.toMutableMap()
                            if (trimmedPoster == null) nextPosters.remove(targetSeasonNum)
                            else nextPosters[targetSeasonNum] = trimmedPoster

                            cfg.copy(customNames = nextNames, customPosters = nextPosters)
                        }
                        editingSeasonNum = null
                    }
                ) {
                    Text("Save", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onUpdateConfig { cfg ->
                            cfg.copy(
                                customNames = cfg.customNames - targetSeasonNum,
                                customPosters = cfg.customPosters - targetSeasonNum
                            )
                        }
                        editingSeasonNum = null
                    }
                ) {
                    Text("Reset", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun MergeCandidateRow(
    coverUrl: Any?,
    title: String,
    subtitle: String,
    actionLabel: String,
    isAdded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isAdded, onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(38.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurfaceElevated)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isAdded) AccentJade.copy(alpha = 0.16f) else Color.Transparent)
                .border(
                    width = 0.7.dp,
                    color = if (isAdded) AccentJade.copy(alpha = 0.5f) else AccentGold.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = if (isAdded) Icons.Default.Check else Icons.Default.Add,
                contentDescription = null,
                tint = if (isAdded) AccentJade else AccentGold,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = actionLabel,
                color = if (isAdded) AccentJade else AccentGold,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
}
