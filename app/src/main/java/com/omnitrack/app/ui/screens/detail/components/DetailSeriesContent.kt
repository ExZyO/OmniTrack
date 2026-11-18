package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import com.omnitrack.app.util.AirDateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.R
import com.omnitrack.app.data.model.MediaEpisode
import com.omnitrack.app.data.model.MediaSeason
import com.omnitrack.app.data.preferences.EpisodeLayoutMode
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * 1:1 Nuvio Reference Parity: Seasons & Episode Tracking Grid.
 *
 * Supports:
 * 1. Mode 1: Horizontal Carousel (16:9 cards, metadata inside image).
 * 2. Mode 2: Compact List (Thumbnail on left, metadata on right).
 * 3. Mode 3: Vertical Hero Cards (Full-width vertical stack of 16:9 cards with in-image metadata).
 * 4. 1:1 Nuvio Animated Watched Badge (compact 20dp circle with jade gradient, hidden when unwatched).
 * 5. Official IMDb Rating Badges (with official logo asset).
 * 6. 1:1 Season Posters Rail vs Text Chips selector with "Posters" / "Text" button.
 * 7. Scalable episode card dimensions based on user setting.
 */
@Composable
fun DetailSeriesContent(
    seasons: List<MediaSeason>,
    episodes: List<MediaEpisode>,
    watchedEpisodes: Set<Pair<Int, Int>>, // Set of (seasonNumber, episodeNumber)
    selectedSeasonNumber: Int,
    fallbackBackdropUrl: String?,
    fallbackCoverUrl: String? = null,
    onSelectSeason: (Int) -> Unit,
    onToggleEpisodeWatched: (MediaEpisode) -> Unit,
    onMarkPreviousWatched: (MediaEpisode) -> Unit,
    onToggleSeasonWatched: (Int) -> Unit,
    modifier: Modifier = Modifier,
    episodesBySeason: Map<Int, List<MediaEpisode>> = emptyMap(),
    layoutMode: EpisodeLayoutMode = EpisodeLayoutMode.LIST,
    layoutScale: Float = 1.0f,
    initialSeasonViewMode: String = "Posters",
    onSeasonViewModeChanged: ((String) -> Unit)? = null,
    onLayoutModeChanged: ((EpisodeLayoutMode) -> Unit)? = null,
    isFillerSkipEnabled: Boolean = false,
    onToggleSkipFillers: (() -> Unit)? = null,
    onMarkWatchedChoice: ((MediaEpisode, Boolean) -> Unit)? = null,
    isMangaMode: Boolean = false,
    isMangaSeasonGrouped: Boolean = false,
    isLightNovelVolumeMode: Boolean = false,
    maxTotalProgress: Int? = null,
    currentTotalProgress: Int? = null,
    onStepProgress: ((Int) -> Unit)? = null,
    onJumpToProgress: ((Int) -> Unit)? = null
) {
    if (seasons.isEmpty() && episodes.isEmpty() && episodesBySeason.isEmpty()) {
        return
    }

    var activeActionSheetEpisode by remember { mutableStateOf<MediaEpisode?>(null) }
    var fillerPromptEpisode by remember { mutableStateOf<MediaEpisode?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var lnShowPosterGrid by remember { mutableStateOf(false) }
    var currentSeasonViewMode by remember(initialSeasonViewMode) { mutableStateOf(initialSeasonViewMode) }
    var currentLayoutMode by remember(layoutMode) { mutableStateOf(layoutMode) }
    val hasSeasonPosters = remember(seasons) { seasons.any { !it.posterUrl.isNullOrBlank() } }
    val hasDistinctBookPosters = remember(seasons, fallbackCoverUrl, isMangaMode, isMangaSeasonGrouped) {
        if (isMangaMode && isMangaSeasonGrouped) {
            seasons.mapNotNull { it.posterUrl?.takeIf { u -> u.isNotBlank() } }.distinct().size > 1
        } else {
            hasSeasonPosters
        }
    }
    val totalItemsAcrossSeasons = remember(episodesBySeason, episodes) {
        val byMap = episodesBySeason.values.sumOf { it.size }
        if (byMap > 0) byMap else episodes.size
    }
    val useProgressAsWatchedTruth = isMangaMode && (
        totalItemsAcrossSeasons > 200 ||
            (watchedEpisodes.isEmpty() && (currentTotalProgress ?: 0) > 0)
        )
    fun isEpWatched(ep: MediaEpisode): Boolean {
        return if (useProgressAsWatchedTruth) {
            ep.episodeNumber <= (currentTotalProgress ?: 0)
        } else {
            (ep.seasonNumber to ep.episodeNumber) in watchedEpisodes
        }
    }

    val handleEpisodeClick: (MediaEpisode) -> Unit = { ep ->
        val isWatched = isEpWatched(ep)
        if (!isWatched && isFillerSkipEnabled) {
            val sEps = episodesBySeason[selectedSeasonNumber] ?: episodes.filter { it.seasonNumber == selectedSeasonNumber }
            val precedingFillers = sEps.filter {
                it.episodeNumber < ep.episodeNumber &&
                it.isFiller &&
                (it.seasonNumber to it.episodeNumber) !in watchedEpisodes
            }
            if (precedingFillers.isNotEmpty()) {
                fillerPromptEpisode = ep
            } else {
                onToggleEpisodeWatched(ep)
            }
        } else {
            onToggleEpisodeWatched(ep)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Seasons Section Header with 1:1 Nuvio "Posters" / "Text" Button and Smooth 280ms Height Animation
        if (seasons.size > 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(280)),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val seasonSectionTitle = when {
                        isLightNovelVolumeMode -> "Volumes"
                        isMangaMode && isMangaSeasonGrouped && seasons.any { it.name.startsWith("Book", ignoreCase = true) } -> "Books & Arcs"
                        isMangaMode && isMangaSeasonGrouped -> "Seasons"
                        isMangaMode -> "Volumes"
                        else -> "Seasons"
                    }
                    Text(
                        text = seasonSectionTitle,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )

                    if (hasDistinctBookPosters) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    val next = if (currentSeasonViewMode == "Posters") "Text" else "Posters"
                                    currentSeasonViewMode = next
                                    onSeasonViewModeChanged?.invoke(next)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (currentSeasonViewMode == "Posters") "Posters" else "Text",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // 2. Season Selector (Posters Rail vs Text Chips with 1:1 Nuvio Fluid Transition)
                Crossfade(
                    targetState = if (hasDistinctBookPosters) currentSeasonViewMode else "Text",
                    animationSpec = tween(220),
                    label = "season_view_mode"
                ) { mode ->
                    if (mode == "Posters" && hasDistinctBookPosters) {
                        SeasonPosterScrollRow(
                            seasons = seasons,
                            selectedSeasonNumber = selectedSeasonNumber,
                            episodes = episodes,
                            episodesBySeason = episodesBySeason,
                            watchedEpisodes = watchedEpisodes,
                            fallbackImageUrl = fallbackCoverUrl,
                            onSelectSeason = onSelectSeason
                        )
                    } else {
                        SeasonTextChipScrollRow(
                            seasons = seasons,
                            selectedSeasonNumber = selectedSeasonNumber,
                            episodes = episodes,
                            episodesBySeason = episodesBySeason,
                            watchedEpisodes = watchedEpisodes,
                            onSelectSeason = onSelectSeason
                        )
                    }
                }
            }
        }

        // 3. Episodes Header with Layout Switcher (Horizontal / List / Vertical Cards)
        val seasonEpisodes = episodesBySeason[selectedSeasonNumber] ?: episodes.filter { it.seasonNumber == selectedSeasonNumber }
        val currentSeason = seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber }
        val totalInSeason = currentSeason?.episodeCount ?: seasonEpisodes.size
        val watchedInSeason = if (useProgressAsWatchedTruth) {
            seasonEpisodes.count { it.episodeNumber <= (currentTotalProgress ?: 0) }
        } else {
            seasonEpisodes.count { (it.seasonNumber to it.episodeNumber) in watchedEpisodes }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isLightNovelVolumeMode) "Volumes" else if (isMangaMode) "Chapters" else "Episodes",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = if (isMangaMode) "$watchedInSeason of $totalInSeason read" else "$watchedInSeason of $totalInSeason watched",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Layout Mode Switcher Icon Button, Manga/Novel Stepper, LN Cards/Posters Toggle & Skip Fillers Chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isMangaMode && onStepProgress != null) {
                    val chValue = currentTotalProgress ?: watchedEpisodes.size
                    val unitPrefix = if (isLightNovelVolumeMode) "Vol." else "Ch."
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clickable { onStepProgress(-1) }
                                .padding(horizontal = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "−1",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(16.dp)
                                .background(Color.White.copy(alpha = 0.10f))
                        )
                        Row(
                            modifier = Modifier
                                .height(28.dp)
                                .then(
                                    if (onJumpToProgress != null) Modifier.clickable { showJumpDialog = true }
                                    else Modifier
                                )
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "$unitPrefix $chValue",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (onJumpToProgress != null) {
                                Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = "Jump",
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(16.dp)
                                .background(Color.White.copy(alpha = 0.10f))
                        )
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clickable { onStepProgress(1) }
                                .padding(horizontal = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+1",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
                if (isLightNovelVolumeMode) {
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { lnShowPosterGrid = !lnShowPosterGrid }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (lnShowPosterGrid) "Posters" else "Cards",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
                val hasFillersInSeason = seasonEpisodes.any { it.isFiller }
                if (hasFillersInSeason) {
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isFillerSkipEnabled) AccentJade.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
                            .clickable { onToggleSkipFillers?.invoke() }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isFillerSkipEnabled) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AccentJade,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = "Skip fillers",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isFillerSkipEnabled) AccentJade else Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        if (lnShowPosterGrid) lnShowPosterGrid = false
                        val nextMode = when (currentLayoutMode) {
                            EpisodeLayoutMode.HORIZONTAL -> EpisodeLayoutMode.LIST
                            EpisodeLayoutMode.LIST -> EpisodeLayoutMode.VERTICAL_CARD
                            EpisodeLayoutMode.VERTICAL_CARD -> EpisodeLayoutMode.HORIZONTAL
                        }
                        currentLayoutMode = nextMode
                        onLayoutModeChanged?.invoke(nextMode)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    val iconVector = when (currentLayoutMode) {
                        EpisodeLayoutMode.HORIZONTAL -> Icons.Default.ViewStream
                        EpisodeLayoutMode.LIST -> Icons.Default.ViewAgenda
                        EpisodeLayoutMode.VERTICAL_CARD -> Icons.Default.GridView
                    }
                    Icon(
                        imageVector = iconVector,
                        contentDescription = "Switch Layout",
                        tint = AccentJade,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // 4. Render Active Episode / Volume / Chapter Layout Mode with 1:1 Nuvio Directional Slide & Instant Switching
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(tween(250))
        ) {
            AnimatedContent(
                targetState = Triple(selectedSeasonNumber, currentLayoutMode, lnShowPosterGrid),
                transitionSpec = {
                    val fromSeason = initialState.first
                    val toSeason = targetState.first
                    val fromIdx = seasons.indexOfFirst { it.seasonNumber == fromSeason }.takeIf { it >= 0 } ?: 0
                    val toIdx = seasons.indexOfFirst { it.seasonNumber == toSeason }.takeIf { it >= 0 } ?: 0
                    val dir = if (toIdx >= fromIdx) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { dir * it / 5 })
                        .togetherWith(
                            fadeOut(tween(170)) + slideOutHorizontally(tween(170)) { -dir * it / 5 }
                        )
                },
                label = "season_episodes_transition"
            ) { (seasonNum, mode, showLnGrid) ->
                val activeSeasonEps = episodesBySeason[seasonNum] ?: episodes.filter { it.seasonNumber == seasonNum }
                if (activeSeasonEps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isMangaMode) "No entries listed yet" else "Season in production • Episodes will appear as broadcast dates are announced",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (isLightNovelVolumeMode && showLnGrid) {
                    LightNovelVolumePosterGrid(
                        volumes = activeSeasonEps,
                        isEpWatched = ::isEpWatched,
                        fallbackCoverUrl = fallbackCoverUrl,
                        onToggleWatched = handleEpisodeClick,
                        onLongPress = { activeActionSheetEpisode = it }
                    )
                } else {
                    when (mode) {
                        EpisodeLayoutMode.HORIZONTAL -> {
                            EpisodeHorizontalRow(
                                episodes = activeSeasonEps,
                                watchedEpisodes = watchedEpisodes,
                                fallbackImageUrl = if (isMangaMode) fallbackCoverUrl else fallbackBackdropUrl,
                                scale = layoutScale,
                                onToggleWatched = handleEpisodeClick,
                                onLongPress = { activeActionSheetEpisode = it },
                                useProgressAsWatchedTruth = useProgressAsWatchedTruth,
                                currentTotalProgress = currentTotalProgress,
                                isMangaMode = isMangaMode,
                                isLightNovelVolumeMode = isLightNovelVolumeMode
                            )
                        }

                        EpisodeLayoutMode.LIST -> {
                            EpisodeListColumn(
                                episodes = activeSeasonEps,
                                watchedEpisodes = watchedEpisodes,
                                fallbackImageUrl = if (isMangaMode) fallbackCoverUrl else fallbackBackdropUrl,
                                scale = layoutScale,
                                onToggleWatched = handleEpisodeClick,
                                onLongPress = { activeActionSheetEpisode = it },
                                useProgressAsWatchedTruth = useProgressAsWatchedTruth,
                                currentTotalProgress = currentTotalProgress,
                                isMangaMode = isMangaMode,
                                isLightNovelVolumeMode = isLightNovelVolumeMode
                            )
                        }

                        EpisodeLayoutMode.VERTICAL_CARD -> {
                            EpisodeVerticalCardColumn(
                                episodes = activeSeasonEps,
                                watchedEpisodes = watchedEpisodes,
                                fallbackImageUrl = if (isMangaMode) fallbackCoverUrl else fallbackBackdropUrl,
                                scale = layoutScale,
                                onToggleWatched = handleEpisodeClick,
                                onLongPress = { activeActionSheetEpisode = it },
                                useProgressAsWatchedTruth = useProgressAsWatchedTruth,
                                currentTotalProgress = currentTotalProgress,
                                isMangaMode = isMangaMode,
                                isLightNovelVolumeMode = isLightNovelVolumeMode
                            )
                        }
                    }
                }
            }
        }
    }

    // Watched Action Sheet
    activeActionSheetEpisode?.let { ep ->
        val currentSeasonObj = seasons.firstOrNull { it.seasonNumber == ep.seasonNumber }
        val seasonLabel = currentSeasonObj?.name ?: "Season ${ep.seasonNumber}"
        val isWatched = isEpWatched(ep)
        val seasonEps = episodes.filter { it.seasonNumber == ep.seasonNumber }
        val isSeasonWatched = seasonEps.isNotEmpty() && seasonEps.all { isEpWatched(it) }

        EpisodeWatchedActionSheet(
            episode = ep,
            seasonLabel = seasonLabel,
            isEpisodeWatched = isWatched,
            canMarkPreviousEpisodes = ep.episodeNumber > 1 || ep.seasonNumber > 1,
            isSeasonWatched = isSeasonWatched,
            onDismiss = { activeActionSheetEpisode = null },
            onToggleWatched = { handleEpisodeClick(ep) },
            onMarkPreviousWatched = { onMarkPreviousWatched(ep) },
            onToggleSeasonWatched = { onToggleSeasonWatched(ep.seasonNumber) }
        )
    }

    // Instant Keypad Jump Dialog for Chapters & Volumes
    if (showJumpDialog && onJumpToProgress != null) {
        val currentCh = currentTotalProgress ?: 0
        val unitName = if (isLightNovelVolumeMode) "Volume" else "Chapter"
        var inputText by remember(showJumpDialog) { mutableStateOf(currentCh.toString()) }
        val parsedVal = inputText.trim().toIntOrNull()
        val maxVal = maxTotalProgress?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            containerColor = Color(0xFF121215),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            title = {
                Text(
                    text = "Jump to $unitName",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (maxVal != null) "Enter $unitName number (0 – $maxVal)" else "Enter $unitName number",
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { raw ->
                            inputText = raw.filter { it.isDigit() }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text(unitName, color = Color.White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentJade,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = AccentJade
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 50, 100).forEach { delta ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable {
                                        val base = inputText.toIntOrNull() ?: currentCh
                                        val bumped = (base + delta).let { if (maxVal != null) it.coerceAtMost(maxVal) else it }
                                        inputText = bumped.toString()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "+$delta",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = (parsedVal ?: currentCh).coerceAtLeast(0).let {
                            if (maxVal != null) it.coerceAtMost(maxVal) else it
                        }
                        showJumpDialog = false
                        onJumpToProgress(target)
                    }
                ) {
                    Text(
                        text = "Jump & Mark Read",
                        color = AccentJade,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.70f))
                }
            }
        )
    }

    // Skip Fillers Confirmation Dialog
    if (fillerPromptEpisode != null) {
        val targetEp = fillerPromptEpisode!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { fillerPromptEpisode = null },
            containerColor = Color(0xFF141416),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            title = {
                Text(
                    text = "Skipping Filler Episodes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "You have skipped filler episodes before Episode ${targetEp.episodeNumber}. Would you like to mark those filler episodes as watched on your trackers, or keep them skipped?",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val ep = targetEp
                        fillerPromptEpisode = null
                        if (onMarkWatchedChoice != null) {
                            onMarkWatchedChoice(ep, true)
                        } else {
                            onToggleEpisodeWatched(ep)
                        }
                    }
                ) {
                    Text(
                        text = "Mark All Watched",
                        color = AccentJade,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val ep = targetEp
                        fillerPromptEpisode = null
                        if (onMarkWatchedChoice != null) {
                            onMarkWatchedChoice(ep, false)
                        } else {
                            onToggleEpisodeWatched(ep)
                        }
                    }
                ) {
                    Text(
                        text = "Keep Fillers Skipped",
                        color = Color.White.copy(alpha = 0.70f)
                    )
                }
            }
        )
    }
}

/**
 * 1:1 Nuvio Animated Watched Badge:
 * Delicate stroked ring circle (hollow center letting underlying thumbnail artwork shine through)
 * with a crisp centered white checkmark icon. Fades and scales smoothly on toggle.
 */
@Composable
fun NuvioAnimatedWatchedBadge(
    isWatched: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val bgColor = if (isWatched) Color(0xFF00E054) else Color.Black.copy(alpha = 0.42f)
    val borderColor = if (isWatched) Color(0xFF00E054) else Color.White.copy(alpha = 0.55f)
    val iconColor = if (isWatched) Color.Black else Color.White.copy(alpha = 0.38f)

    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = if (isWatched) 1.4.dp else 1.3.dp,
                color = borderColor,
                shape = CircleShape
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = if (isWatched) "Watched" else "Mark Watched",
            tint = iconColor,
            modifier = Modifier.size(13.dp)
        )
    }
}

/**
 * Official IMDb Rating Badge for episodes.
 */
@Composable
private fun ImdbEpisodeRatingBadge(
    rating: Float,
    modifier: Modifier = Modifier,
    logoWidth: Dp = 24.dp,
    logoHeight: Dp = 12.dp,
    textSize: androidx.compose.ui.unit.TextUnit = 12.sp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.rating_imdb),
            contentDescription = "IMDb",
            modifier = Modifier
                .width(logoWidth)
                .height(logoHeight),
            contentScale = ContentScale.Fit
        )
        Text(
            text = String.format(java.util.Locale.US, "%.1f", rating),
            fontSize = textSize,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF5C518)
        )
    }
}

/**
 * 1:1 Nuvio Floating Episode Code Badge ("S1 E1" / "E1" / "SP 1")
 */
@Composable
private fun EpisodeCodeBadge(
    text: String,
    modifier: Modifier = Modifier,
    textSize: androidx.compose.ui.unit.TextUnit = 10.sp,
    radius: Dp = 4.dp,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 2.dp,
    backgroundAlpha: Float = 0.45f
) {
    if (text.isBlank()) return
    val badgeShape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(badgeShape)
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .border(0.5.dp, Color.Black.copy(alpha = 0.60f), badgeShape)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = textSize,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.90f),
            letterSpacing = 0.sp,
            maxLines = 1
        )
    }
}

private fun formatEpisodeAirDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        val trimmed = raw.trim().take(10)
        val parsed = java.time.LocalDate.parse(trimmed)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.US)
        formatter.format(parsed)
    } catch (_: Exception) {
        raw
    }
}

private fun formatEpisodeBadge(seasonNumber: Int?, episodeNumber: Int?): String {
    return when {
        seasonNumber != null && seasonNumber > 0 && episodeNumber != null -> "S$seasonNumber E$episodeNumber"
        seasonNumber != null && seasonNumber <= 0 && episodeNumber != null -> "SP $episodeNumber"
        episodeNumber != null -> "E$episodeNumber"
        else -> ""
    }
}

private fun formatSeasonLabel(seasonNumber: Int): String {
    return if (seasonNumber <= 0) "Specials" else "Season $seasonNumber"
}

/**
 * Mode 1: Nuvio Horizontal Carousel Row (1:1 Nuvio Phone Metrics: 296dp x 184dp, 12dp spacing, 12dp corners)
 */
@Composable
private fun EpisodeHorizontalRow(
    episodes: List<MediaEpisode>,
    watchedEpisodes: Set<Pair<Int, Int>>,
    fallbackImageUrl: String?,
    scale: Float,
    onToggleWatched: (MediaEpisode) -> Unit,
    onLongPress: (MediaEpisode) -> Unit,
    useProgressAsWatchedTruth: Boolean = false,
    currentTotalProgress: Int? = null,
    isMangaMode: Boolean = false,
    isLightNovelVolumeMode: Boolean = false
) {
    val cardWidth = if (isLightNovelVolumeMode) {
        (146.dp * scale).coerceAtLeast(124.dp)
    } else {
        (296.dp * scale).coerceAtLeast(220.dp)
    }
    val cardHeight = if (isLightNovelVolumeMode) {
        (214.dp * scale).coerceAtLeast(180.dp)
    } else {
        (184.dp * scale).coerceAtLeast(136.dp)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(episodes, currentTotalProgress) {
        val ch = currentTotalProgress ?: 0
        if (episodes.size > 10 && ch > 0) {
            val idx = episodes.indexOfFirst { it.episodeNumber >= ch }.takeIf { it >= 0 } ?: 0
            listState.scrollToItem(idx)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(episodes, key = { "${it.seasonNumber}_${it.episodeNumber}_${it.id}" }) { episode ->
            val isWatched = if (useProgressAsWatchedTruth) {
                episode.episodeNumber <= (currentTotalProgress ?: 0)
            } else {
                (episode.seasonNumber to episode.episodeNumber) in watchedEpisodes
            }
            EpisodeHorizontalCard(
                episode = episode,
                isWatched = isWatched,
                fallbackImageUrl = fallbackImageUrl,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                onToggleWatched = { onToggleWatched(episode) },
                onLongPress = { onLongPress(episode) },
                isMangaMode = isMangaMode,
                isLightNovelVolumeMode = isLightNovelVolumeMode
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LightNovelVolumePosterGrid(
    volumes: List<MediaEpisode>,
    isEpWatched: (MediaEpisode) -> Boolean,
    fallbackCoverUrl: String?,
    onToggleWatched: (MediaEpisode) -> Unit,
    onLongPress: (MediaEpisode) -> Unit
) {
    val rows = remember(volumes) { volumes.chunked(3) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { vol ->
                    val isRead = isEpWatched(vol)
                    val cover = vol.stillUrl?.takeIf { it.isNotBlank() } ?: fallbackCoverUrl
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = { onToggleWatched(vol) },
                                onLongClick = { onLongPress(vol) }
                            ),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF141416))
                                .then(
                                    if (isRead) Modifier.border(1.5.dp, AccentJade.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                                    else Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                )
                        ) {
                            if (!cover.isNullOrBlank()) {
                                AsyncImage(
                                    model = cover,
                                    contentDescription = vol.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.55f to Color.Transparent,
                                            1f to Color.Black.copy(alpha = 0.88f)
                                        )
                                    )
                            )
                            NuvioAnimatedWatchedBadge(
                                isWatched = isRead,
                                onClick = { onToggleWatched(vol) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                            )
                            Text(
                                text = "Vol. ${vol.episodeNumber}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 7.dp, vertical = 6.dp)
                            )
                        }
                        val formattedDate = remember(vol.airDate) { formatEpisodeAirDate(vol.airDate) }
                        Text(
                            text = formattedDate ?: vol.title,
                            fontSize = 10.5.sp,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EpisodeAvailabilityBadge(
    episode: MediaEpisode,
    modifier: Modifier = Modifier
) {
    val isUnaired = remember(episode.airDate) {
        val days = AirDateUtils.daysUntilExplicitRelease(AirDateUtils.todayIsoDate(), episode.airDate)
        days != null && days >= 0
    }
    if (!isUnaired) return

    Text(
        text = "UNAVAILABLE",
        modifier = modifier
            .background(Color(0xCC5D4037), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun EpisodeThumbnailLayer(
    episode: MediaEpisode,
    placeholderBg: Color = Color(0xFF141416),
    fallbackCoverUrl: String? = null,
    isMangaMode: Boolean = false,
    isLightNovelVolumeMode: Boolean = false
) {
    val stillUrl = episode.stillUrl?.takeIf { it.isNotBlank() }
        ?: if (isMangaMode) fallbackCoverUrl?.takeIf { it.isNotBlank() } else null
    var loadFailed by remember(stillUrl) { mutableStateOf(false) }
    if (!stillUrl.isNullOrBlank() && !loadFailed) {
        AsyncImage(
            model = stillUrl,
            contentDescription = episode.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onError = { loadFailed = true }
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(placeholderBg),
            contentAlignment = Alignment.Center
        ) {
            val badgeTxt = when {
                isLightNovelVolumeMode -> "Vol.${episode.episodeNumber}"
                isMangaMode -> "Ch.${episode.episodeNumber}"
                else -> formatEpisodeBadge(episode.seasonNumber, episode.episodeNumber)
            }
            Text(
                text = badgeTxt,
                color = Color.White.copy(alpha = 0.28f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeHorizontalCard(
    episode: MediaEpisode,
    isWatched: Boolean,
    fallbackImageUrl: String?,
    cardWidth: Dp,
    cardHeight: Dp,
    onToggleWatched: () -> Unit,
    onLongPress: () -> Unit,
    isMangaMode: Boolean = false,
    isLightNovelVolumeMode: Boolean = false
) {
    val cardShape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(cardShape)
            .background(DarkSurfaceElevated)
            .combinedClickable(
                onClick = onToggleWatched,
                onLongClick = onLongPress
            )
    ) {
        EpisodeThumbnailLayer(
            episode = episode,
            fallbackCoverUrl = fallbackImageUrl,
            isMangaMode = isMangaMode,
            isLightNovelVolumeMode = isLightNovelVolumeMode
        )

        // 1:1 Nuvio Multi-Stop Dark Bottom Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.42f to Color.Transparent,
                        0.56f to Color.Black.copy(alpha = 0.20f),
                        0.70f to Color.Black.copy(alpha = 0.45f),
                        0.84f to Color.Black.copy(alpha = 0.68f),
                        1f to Color.Black.copy(alpha = 0.92f)
                    )
                )
        )

        // Always-Visible Watched Badge Circle on Top Right
        NuvioAnimatedWatchedBadge(
            isWatched = isWatched,
            onClick = onToggleWatched,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
        )

        // Solid White FILLER / RECAP Badge at Top Start
        if (episode.isFiller || episode.isRecap) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (episode.isRecap) "RECAP" else "FILLER",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // 1:1 Nuvio Availability Badge ("UNAVAILABLE" for unaired episodes)
        EpisodeAvailabilityBadge(
            episode = episode,
            modifier = Modifier
                .align(if (episode.isFiller || episode.isRecap) Alignment.BottomEnd else Alignment.TopStart)
                .padding(8.dp)
        )

        // In-Image Content Overlay at Bottom Start (1:1 Nuvio Phone Metrics)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val formattedDate = remember(episode.airDate) { formatEpisodeAirDate(episode.airDate) }
            if (!formattedDate.isNullOrBlank()) {
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.70f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val titleText = episode.title.ifBlank { "Episode ${episode.episodeNumber}" }
            Text(
                text = titleText,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 19.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!episode.overview.isNullOrBlank()) {
                Text(
                    text = episode.overview,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Bottom Metadata Line: Runtime, IMDb Rating, Air Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (episode.runtimeMinutes != null && episode.runtimeMinutes > 0) {
                    Text(
                        text = "${episode.runtimeMinutes}m",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
                if (episode.voteAverage != null && episode.voteAverage > 0) {
                    ImdbEpisodeRatingBadge(
                        rating = episode.voteAverage,
                        logoWidth = 22.dp,
                        logoHeight = 11.dp,
                        textSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                val formattedDate = remember(episode.airDate) { formatEpisodeAirDate(episode.airDate) }
                if (!formattedDate.isNullOrBlank()) {
                    Text(
                        text = formattedDate,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
            }
        }
    }
}

/**
 * Mode 2: Nuvio Compact List (1:1 Nuvio Phone Metrics: 120x120dp square thumbnail with badge on top-left, watched on top-right, metadata on right)
 */
@Composable
private fun EpisodeListColumn(
    episodes: List<MediaEpisode>,
    watchedEpisodes: Set<Pair<Int, Int>>,
    fallbackImageUrl: String?,
    scale: Float,
    onToggleWatched: (MediaEpisode) -> Unit,
    onLongPress: (MediaEpisode) -> Unit,
    useProgressAsWatchedTruth: Boolean = false,
    currentTotalProgress: Int? = null,
    isMangaMode: Boolean = false,
    isLightNovelVolumeMode: Boolean = false
) {
    val cardHeight = if (isLightNovelVolumeMode) (116.dp * scale).coerceAtLeast(98.dp) else (120.dp * scale).coerceAtLeast(96.dp)
    val imageWidth = if (isLightNovelVolumeMode) (78.dp * scale).coerceAtLeast(66.dp) else (120.dp * scale).coerceAtLeast(96.dp)

    val totalCount = episodes.size
    val initialAnchor = remember(episodes, currentTotalProgress) {
        if (totalCount <= 40) 0
        else {
            val ch = currentTotalProgress ?: 0
            val idx = if (ch > 0) episodes.indexOfFirst { it.episodeNumber >= ch }.takeIf { it >= 0 } ?: 0 else 0
            (idx - 10).coerceIn(0, (totalCount - 25).coerceAtLeast(0))
        }
    }
    var windowStart by remember(episodes, initialAnchor) { mutableStateOf(initialAnchor) }
    var windowEnd by remember(episodes, initialAnchor) { mutableStateOf((initialAnchor + 25).coerceAtMost(totalCount)) }
    val visibleEpisodes = if (totalCount > 40) episodes.subList(windowStart, windowEnd) else episodes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (totalCount > 40 && windowStart > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { windowStart = (windowStart - 25).coerceAtLeast(0) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↑ Show Earlier ($windowStart remaining)",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        visibleEpisodes.forEach { episode ->
            val isWatched = if (useProgressAsWatchedTruth) {
                episode.episodeNumber <= (currentTotalProgress ?: 0)
            } else {
                (episode.seasonNumber to episode.episodeNumber) in watchedEpisodes
            }
            EpisodeListCardItem(
                episode = episode,
                isWatched = isWatched,
                fallbackImageUrl = fallbackImageUrl,
                cardHeight = cardHeight,
                imageWidth = imageWidth,
                onToggleWatched = { onToggleWatched(episode) },
                onLongPress = { onLongPress(episode) },
                isMangaMode = isMangaMode,
                isLightNovelVolumeMode = isLightNovelVolumeMode
            )
        }
        if (totalCount > 40 && windowEnd < totalCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { windowEnd = (windowEnd + 25).coerceAtMost(totalCount) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↓ Show More (${totalCount - windowEnd} remaining)",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeListCardItem(
    episode: MediaEpisode,
    isWatched: Boolean,
    fallbackImageUrl: String?,
    cardHeight: Dp,
    imageWidth: Dp,
    onToggleWatched: () -> Unit,
    onLongPress: () -> Unit,
    isMangaMode: Boolean = false,
    isLightNovelVolumeMode: Boolean = false
) {
    val cardShape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clip(cardShape)
            .background(Color(0xFF222224).copy(alpha = 0.45f))
            .combinedClickable(
                onClick = onToggleWatched,
                onLongClick = onLongPress
            )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left: Thumbnail Still / Volume Cover with Top-Right Watched Circle
            Box(
                modifier = Modifier
                    .width(imageWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(Color(0xFF0D0D0D))
            ) {
                EpisodeThumbnailLayer(
                    episode = episode,
                    fallbackCoverUrl = fallbackImageUrl,
                    isMangaMode = isMangaMode,
                    isLightNovelVolumeMode = isLightNovelVolumeMode
                )

                // Always-Visible Top-Right Watched Circle
                NuvioAnimatedWatchedBadge(
                    isWatched = isWatched,
                    onClick = onToggleWatched,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                )

                // Solid White FILLER / RECAP Badge at Top Start
                if (episode.isFiller || episode.isRecap) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 8.dp, start = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (episode.isRecap) "RECAP" else "FILLER",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // 1:1 Nuvio Availability Badge ("UNAVAILABLE" for unaired episodes)
                EpisodeAvailabilityBadge(
                    episode = episode,
                    modifier = Modifier
                        .align(if (isWatched || episode.isFiller || episode.isRecap) Alignment.BottomStart else Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp, start = 8.dp, bottom = 8.dp)
                )
            }

            // Right: Episode Metadata Column (1:1 Nuvio Metrics: 15sp title, 12sp subtitle row, 13sp overview)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val titleText = episode.title.ifBlank { "Episode ${episode.episodeNumber}" }
                Text(
                    text = titleText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val formattedDate = remember(episode.airDate) { formatEpisodeAirDate(episode.airDate) }
                if (!formattedDate.isNullOrBlank() || (episode.voteAverage != null && episode.voteAverage > 0)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!formattedDate.isNullOrBlank()) {
                            Text(
                                text = formattedDate,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (episode.voteAverage != null && episode.voteAverage > 0) {
                            ImdbEpisodeRatingBadge(
                                rating = episode.voteAverage,
                                logoWidth = 24.dp,
                                logoHeight = 12.dp,
                                textSize = 12.sp
                            )
                        }
                    }
                }

                if (!episode.overview.isNullOrBlank()) {
                    Text(
                        text = episode.overview,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color.White.copy(alpha = 0.60f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Mode 3: Vertical Hero Cards (Full-Width 16:9 vertical cards with in-image metadata)
 * The third setting requested: looks like the horizontal card, but arranged vertically.
 */
@Composable
private fun EpisodeVerticalCardColumn(
    episodes: List<MediaEpisode>,
    watchedEpisodes: Set<Pair<Int, Int>>,
    fallbackImageUrl: String?,
    scale: Float,
    onToggleWatched: (MediaEpisode) -> Unit,
    onLongPress: (MediaEpisode) -> Unit,
    useProgressAsWatchedTruth: Boolean = false,
    currentTotalProgress: Int? = null,
    isMangaMode: Boolean = false,
    isLightNovelVolumeMode: Boolean = false
) {
    val totalCount = episodes.size
    val initialAnchor = remember(episodes, currentTotalProgress) {
        if (totalCount <= 40) 0
        else {
            val ch = currentTotalProgress ?: 0
            val idx = if (ch > 0) episodes.indexOfFirst { it.episodeNumber >= ch }.takeIf { it >= 0 } ?: 0 else 0
            (idx - 10).coerceIn(0, (totalCount - 25).coerceAtLeast(0))
        }
    }
    var windowStart by remember(episodes, initialAnchor) { mutableStateOf(initialAnchor) }
    var windowEnd by remember(episodes, initialAnchor) { mutableStateOf((initialAnchor + 25).coerceAtMost(totalCount)) }
    val visibleEpisodes = if (totalCount > 40) episodes.subList(windowStart, windowEnd) else episodes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (totalCount > 40 && windowStart > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { windowStart = (windowStart - 25).coerceAtLeast(0) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↑ Show Earlier ($windowStart remaining)",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        visibleEpisodes.forEach { episode ->
            val isWatched = if (useProgressAsWatchedTruth) {
                episode.episodeNumber <= (currentTotalProgress ?: 0)
            } else {
                (episode.seasonNumber to episode.episodeNumber) in watchedEpisodes
            }
            EpisodeVerticalCardItem(
                episode = episode,
                isWatched = isWatched,
                fallbackImageUrl = fallbackImageUrl,
                scale = scale,
                onToggleWatched = { onToggleWatched(episode) },
                onLongPress = { onLongPress(episode) },
                isMangaMode = isMangaMode,
                isLightNovelVolumeMode = isLightNovelVolumeMode
            )
        }
        if (totalCount > 40 && windowEnd < totalCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { windowEnd = (windowEnd + 25).coerceAtMost(totalCount) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↓ Show More (${totalCount - windowEnd} remaining)",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeVerticalCardItem(
    episode: MediaEpisode,
    isWatched: Boolean,
    fallbackImageUrl: String?,
    scale: Float,
    onToggleWatched: () -> Unit,
    onLongPress: () -> Unit,
    isMangaMode: Boolean = false,
    isLightNovelVolumeMode: Boolean = false
) {
    val cardShape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(cardShape)
            .background(DarkSurfaceElevated)
            .combinedClickable(
                onClick = onToggleWatched,
                onLongClick = onLongPress
            )
    ) {
        EpisodeThumbnailLayer(
            episode = episode,
            fallbackCoverUrl = fallbackImageUrl,
            isMangaMode = isMangaMode,
            isLightNovelVolumeMode = isLightNovelVolumeMode
        )

        // Multi-Stop Gradient inside image
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Transparent,
                        0.55f to Color.Black.copy(alpha = 0.25f),
                        0.72f to Color.Black.copy(alpha = 0.55f),
                        0.88f to Color.Black.copy(alpha = 0.80f),
                        1f to Color.Black.copy(alpha = 0.95f)
                    )
                )
        )

        // Always-Visible Top-Right Watched Circle
        NuvioAnimatedWatchedBadge(
            isWatched = isWatched,
            onClick = onToggleWatched,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
        )

        // Solid White FILLER / RECAP Badge at Top Start
        if (episode.isFiller || episode.isRecap) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (episode.isRecap) "RECAP" else "FILLER",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // 1:1 Nuvio Availability Badge ("UNAVAILABLE" for unaired episodes)
        EpisodeAvailabilityBadge(
            episode = episode,
            modifier = Modifier
                .align(if (isWatched || episode.isFiller || episode.isRecap) Alignment.BottomEnd else Alignment.TopEnd)
                .padding(8.dp)
        )

        // In-Image Metadata Overlay at Bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val formattedDate = remember(episode.airDate) { formatEpisodeAirDate(episode.airDate) }
            if (!formattedDate.isNullOrBlank()) {
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.70f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val titleText = episode.title.ifBlank { "Episode ${episode.episodeNumber}" }
            Text(
                text = titleText,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!episode.overview.isNullOrBlank()) {
                Text(
                    text = episode.overview,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (episode.runtimeMinutes != null && episode.runtimeMinutes > 0) {
                    Text(text = "${episode.runtimeMinutes}m", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                }
                if (episode.voteAverage != null && episode.voteAverage > 0) {
                    ImdbEpisodeRatingBadge(rating = episode.voteAverage)
                }
                Spacer(modifier = Modifier.weight(1f))
                val formattedDate = remember(episode.airDate) { formatEpisodeAirDate(episode.airDate) }
                if (!formattedDate.isNullOrBlank()) {
                    Text(text = formattedDate, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@Composable
private fun SeasonPosterScrollRow(
    seasons: List<MediaSeason>,
    selectedSeasonNumber: Int,
    episodes: List<MediaEpisode>,
    episodesBySeason: Map<Int, List<MediaEpisode>>,
    watchedEpisodes: Set<Pair<Int, Int>>,
    fallbackImageUrl: String?,
    onSelectSeason: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedSeasonNumber) {
        val idx = seasons.indexOfFirst { it.seasonNumber == selectedSeasonNumber }
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(seasons, key = { it.seasonNumber }) { season ->
            val isSelected = season.seasonNumber == selectedSeasonNumber
            val seasonEps = episodesBySeason[season.seasonNumber] ?: episodes.filter { it.seasonNumber == season.seasonNumber }
            val watchedCount = seasonEps.count { (it.seasonNumber to it.episodeNumber) in watchedEpisodes }
            val allWatched = season.episodeCount > 0 && watchedCount >= season.episodeCount
            val label = season.name.ifBlank { formatSeasonLabel(season.seasonNumber) }

            Column(
                modifier = Modifier
                    .width(100.dp)
                    .clickable { onSelectSeason(season.seasonNumber) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceElevated)
                        .then(
                            if (isSelected) Modifier.border(2.dp, Color(0xFFE52E5E), RoundedCornerShape(8.dp))
                            else Modifier
                        )
                ) {
                    val episodeStillFallback = seasonEps.firstOrNull { !it.stillUrl.isNullOrBlank() }?.stillUrl
                    val posterModel = season.posterUrl?.takeIf { it.isNotBlank() }
                        ?: fallbackImageUrl?.takeIf { it.isNotBlank() }
                        ?: episodeStillFallback
                    var loadFailed by remember(posterModel) { mutableStateOf(false) }
                    val effectivePoster = if (loadFailed) {
                        fallbackImageUrl?.takeIf { it.isNotBlank() && it != posterModel } ?: episodeStillFallback
                    } else {
                        posterModel
                    }
                    if (!effectivePoster.isNullOrBlank()) {
                        AsyncImage(
                            model = effectivePoster,
                            contentDescription = label,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onError = { loadFailed = true }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Text(
                    text = label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.60f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SeasonTextChipScrollRow(
    seasons: List<MediaSeason>,
    selectedSeasonNumber: Int,
    episodes: List<MediaEpisode>,
    episodesBySeason: Map<Int, List<MediaEpisode>>,
    watchedEpisodes: Set<Pair<Int, Int>>,
    onSelectSeason: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedSeasonNumber) {
        val idx = seasons.indexOfFirst { it.seasonNumber == selectedSeasonNumber }
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(seasons, key = { it.seasonNumber }) { season ->
            val isSelected = season.seasonNumber == selectedSeasonNumber
            val seasonEps = episodesBySeason[season.seasonNumber] ?: episodes.filter { it.seasonNumber == season.seasonNumber }
            val watchedCount = seasonEps.count { (it.seasonNumber to it.episodeNumber) in watchedEpisodes }
            val allWatched = season.episodeCount > 0 && watchedCount >= season.episodeCount
            val label = season.name.ifBlank { formatSeasonLabel(season.seasonNumber) }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent
                    )
                    .clickable { onSelectSeason(season.seasonNumber) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (allWatched) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = AccentJade,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                )
            }
        }
    }
}
