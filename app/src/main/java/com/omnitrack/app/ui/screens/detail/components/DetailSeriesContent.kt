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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.omnitrack.app.data.db.entity.EpisodeProgressEntity
import com.omnitrack.app.data.model.MediaEpisode
import com.omnitrack.app.data.model.MediaSeason
import com.omnitrack.app.data.preferences.EpisodeLayoutMode
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

private fun toCoilImageModel(raw: String?): Any? {
    val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return if (trimmed.startsWith("/")) java.io.File(trimmed) else trimmed
}

private fun extractVolumeBadgeLabel(title: String, fallbackNum: Int): String {
    val enMatch = Regex("""(?:Vol\.?|Volume)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(title)
    if (enMatch != null) return "Vol. ${enMatch.groupValues[1]}"
    val jpMatch = Regex("""(?:年生編|第)\s*(\d+(?:\.\d+)?)""").find(title)
    if (jpMatch != null) return "Vol. ${jpMatch.groupValues[1]}"
    return "Vol. $fallbackNum"
}

@Stable
class DetailSeriesUiState(
    initialSeasonViewMode: String,
    initialLayoutMode: EpisodeLayoutMode
) {
    var activeActionSheetEpisode by mutableStateOf<MediaEpisode?>(null)
    var activeActionSheetSeason by mutableStateOf<MediaSeason?>(null)
    var fillerPromptEpisode by mutableStateOf<MediaEpisode?>(null)
    var showJumpDialog by mutableStateOf(false)
    var activeFilter by mutableStateOf("ALL")
    var currentSeasonViewMode by mutableStateOf(initialSeasonViewMode)
    var currentLayoutMode by mutableStateOf(initialLayoutMode)
}

@Composable
fun rememberDetailSeriesState(
    itemKey: Any?,
    initialSeasonViewMode: String,
    layoutMode: EpisodeLayoutMode
): DetailSeriesUiState {
    val state = remember(itemKey) { DetailSeriesUiState(initialSeasonViewMode, layoutMode) }
    LaunchedEffect(initialSeasonViewMode) {
        state.currentSeasonViewMode = initialSeasonViewMode
    }
    LaunchedEffect(layoutMode) {
        state.currentLayoutMode = layoutMode
    }
    return state
}

/**
 * 1:1 Nuvio Reference Parity: Seasons & Episode Tracking Items emitted directly into the parent
 * LazyColumn so 1,000+ chapters/volumes scroll at locked 120Hz with zero "Show More" slicing.
 */
fun LazyListScope.detailSeriesItems(
    seriesState: DetailSeriesUiState,
    seasons: List<MediaSeason>,
    episodes: List<MediaEpisode>,
    watchedEpisodes: Set<Pair<Int, Int>>, // Set of (seasonNumber, episodeNumber)
    selectedSeasonNumber: Int,
    fallbackBackdropUrl: String?,
    fallbackCoverUrl: String? = null,
    onSelectSeason: (Int) -> Unit,
    onToggleEpisodeWatched: (MediaEpisode) -> Unit,
    onToggleSeasonWatched: (Int) -> Unit,
    onOpenEpisodeDetail: (MediaEpisode) -> Unit,
    episodeMetaMap: Map<Pair<Int, Int>, EpisodeProgressEntity> = emptyMap(),
    seasonMetaMap: Map<Int, com.omnitrack.app.data.db.entity.SeasonMetaEntity> = emptyMap(),
    episodesBySeason: Map<Int, List<MediaEpisode>> = emptyMap(),
    layoutScale: Float = 1.0f,
    onSeasonViewModeChanged: ((String) -> Unit)? = null,
    onLayoutModeChanged: ((EpisodeLayoutMode) -> Unit)? = null,
    isFillerSkipEnabled: Boolean = false,
    onToggleSkipFillers: (() -> Unit)? = null,
    isMangaMode: Boolean = false,
    isMangaSeasonGrouped: Boolean = false,
    isLightNovelVolumeMode: Boolean = false,
    currentTotalProgress: Int? = null,
    onStepProgress: ((Int) -> Unit)? = null,
    onJumpToProgress: ((Int) -> Unit)? = null
) {
    if (seasons.isEmpty() && episodes.isEmpty() && episodesBySeason.isEmpty()) {
        return
    }

    val hasSeasonPosters = seasons.any { !it.posterUrl.isNullOrBlank() }
    val hasDistinctBookPosters = if (isMangaMode && isMangaSeasonGrouped) {
        seasons.mapNotNull { it.posterUrl?.takeIf { u -> u.isNotBlank() } }.distinct().size > 1
    } else {
        hasSeasonPosters
    }
    val totalItemsAcrossSeasons = episodesBySeason.values.sumOf { it.size }.takeIf { it > 0 } ?: episodes.size
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

    val seasonEpisodes = episodesBySeason[selectedSeasonNumber]
        ?: episodes.filter { it.seasonNumber == selectedSeasonNumber }

    val starredCount = seasonEpisodes.count { episodeMetaMap[it.seasonNumber to it.episodeNumber]?.isBookmarked == true }
    val notedCount = seasonEpisodes.count {
        val m = episodeMetaMap[it.seasonNumber to it.episodeNumber]
        !m?.userNote.isNullOrBlank() || m?.userRating != null || !m?.customLabel.isNullOrBlank()
    }
    val effectiveFilter = when {
        seriesState.activeFilter == "STARRED" && starredCount > 0 -> "STARRED"
        seriesState.activeFilter == "NOTED" && notedCount > 0 -> "NOTED"
        else -> "ALL"
    }
    val filteredSeasonEpisodes = when (effectiveFilter) {
        "STARRED" -> seasonEpisodes.filter { episodeMetaMap[it.seasonNumber to it.episodeNumber]?.isBookmarked == true }
        "NOTED" -> seasonEpisodes.filter {
            val m = episodeMetaMap[it.seasonNumber to it.episodeNumber]
            !m?.userNote.isNullOrBlank() || m?.userRating != null || !m?.customLabel.isNullOrBlank()
        }
        else -> seasonEpisodes
    }

    val handleEpisodeClick: (MediaEpisode) -> Unit = { ep ->
        val isWatched = isEpWatched(ep)
        if (!isWatched && isFillerSkipEnabled) {
            val precedingFillers = seasonEpisodes.filter {
                it.episodeNumber < ep.episodeNumber &&
                    it.isFiller &&
                    (it.seasonNumber to it.episodeNumber) !in watchedEpisodes
            }
            if (precedingFillers.isNotEmpty()) {
                seriesState.fillerPromptEpisode = ep
            } else {
                onToggleEpisodeWatched(ep)
            }
        } else {
            onToggleEpisodeWatched(ep)
        }
    }

    // 1. Seasons Section Header & Poster/Text Selector
    if (seasons.size > 1) {
        item(key = "series_seasons_selector", contentType = "series_seasons") {
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
                                    val next = if (seriesState.currentSeasonViewMode == "Posters") "Text" else "Posters"
                                    seriesState.currentSeasonViewMode = next
                                    onSeasonViewModeChanged?.invoke(next)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (seriesState.currentSeasonViewMode == "Posters") "Posters" else "Text",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                Crossfade(
                    targetState = if (hasDistinctBookPosters) seriesState.currentSeasonViewMode else "Text",
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
                            seasonMetaMap = seasonMetaMap,
                            fallbackImageUrl = fallbackCoverUrl,
                            useProgressAsWatchedTruth = useProgressAsWatchedTruth,
                            currentTotalProgress = currentTotalProgress,
                            onSelectSeason = onSelectSeason,
                            onLongPressSeason = { seriesState.activeActionSheetSeason = it }
                        )
                    } else {
                        SeasonTextChipScrollRow(
                            seasons = seasons,
                            selectedSeasonNumber = selectedSeasonNumber,
                            episodes = episodes,
                            episodesBySeason = episodesBySeason,
                            watchedEpisodes = watchedEpisodes,
                            seasonMetaMap = seasonMetaMap,
                            useProgressAsWatchedTruth = useProgressAsWatchedTruth,
                            currentTotalProgress = currentTotalProgress,
                            onSelectSeason = onSelectSeason,
                            onLongPressSeason = { seriesState.activeActionSheetSeason = it }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 2. Episodes / Chapters / Volumes Header + Controls Bar + Dynamic Filter Pills
    item(key = "series_controls_header", contentType = "series_controls") {
        val currentSeason = seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber }
        val totalInSeason = currentSeason?.episodeCount ?: seasonEpisodes.size
        val watchedInSeason = if (useProgressAsWatchedTruth) {
            seasonEpisodes.count { it.episodeNumber <= (currentTotalProgress ?: 0) }
        } else {
            seasonEpisodes.count { (it.seasonNumber to it.episodeNumber) in watchedEpisodes }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                                        if (onJumpToProgress != null) Modifier.clickable { seriesState.showJumpDialog = true }
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
                                    fontWeight = FontWeight.Bold,
                                    color = com.omnitrack.app.ui.theme.AccentGold
                                )
                            }
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
                            val nextMode = if (isLightNovelVolumeMode) {
                                when (seriesState.currentLayoutMode) {
                                    EpisodeLayoutMode.HORIZONTAL -> EpisodeLayoutMode.LIST
                                    EpisodeLayoutMode.LIST -> EpisodeLayoutMode.VERTICAL_CARD
                                    EpisodeLayoutMode.VERTICAL_CARD -> EpisodeLayoutMode.POSTER_GRID
                                    EpisodeLayoutMode.POSTER_GRID -> EpisodeLayoutMode.TEXT_ONLY
                                    EpisodeLayoutMode.TEXT_ONLY -> EpisodeLayoutMode.HORIZONTAL
                                }
                            } else {
                                when (seriesState.currentLayoutMode) {
                                    EpisodeLayoutMode.HORIZONTAL -> EpisodeLayoutMode.LIST
                                    EpisodeLayoutMode.LIST -> EpisodeLayoutMode.VERTICAL_CARD
                                    EpisodeLayoutMode.VERTICAL_CARD, EpisodeLayoutMode.POSTER_GRID -> EpisodeLayoutMode.TEXT_ONLY
                                    EpisodeLayoutMode.TEXT_ONLY -> EpisodeLayoutMode.HORIZONTAL
                                }
                            }
                            seriesState.currentLayoutMode = nextMode
                            onLayoutModeChanged?.invoke(nextMode)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        val displayMode = if (!isLightNovelVolumeMode && seriesState.currentLayoutMode == EpisodeLayoutMode.POSTER_GRID) {
                            EpisodeLayoutMode.LIST
                        } else {
                            seriesState.currentLayoutMode
                        }
                        val iconVector = when (displayMode) {
                            EpisodeLayoutMode.HORIZONTAL -> Icons.Default.ViewStream
                            EpisodeLayoutMode.LIST -> Icons.Default.ViewAgenda
                            EpisodeLayoutMode.VERTICAL_CARD -> Icons.Default.ViewCarousel
                            EpisodeLayoutMode.POSTER_GRID -> Icons.Default.GridView
                            EpisodeLayoutMode.TEXT_ONLY -> Icons.AutoMirrored.Filled.FormatListBulleted
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

            if (starredCount > 0 || notedCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    @Composable
                    fun FilterChipPill(key: String, label: String, activeColor: Color) {
                        val isSelected = effectiveFilter == key
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isSelected) activeColor.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f))
                                .clickable { seriesState.activeFilter = key }
                                .padding(horizontal = 11.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) activeColor else Color.White.copy(alpha = 0.72f)
                            )
                        }
                    }

                    FilterChipPill("ALL", "All (${seasonEpisodes.size})", Color.White)
                    if (starredCount > 0) {
                        FilterChipPill("STARRED", "★ Starred ($starredCount)", AccentGold)
                    }
                    if (notedCount > 0) {
                        FilterChipPill("NOTED", "Notes & Ratings ($notedCount)", AccentArctic)
                    }
                }
            }
        }
    }

    // 3. Virtualized Episode / Volume / Chapter Items (Zero "Show More" / "Show Earlier" Buttons)
    val effectiveRenderMode = if (!isLightNovelVolumeMode && seriesState.currentLayoutMode == EpisodeLayoutMode.POSTER_GRID) {
        EpisodeLayoutMode.LIST
    } else {
        seriesState.currentLayoutMode
    }

    if (filteredSeasonEpisodes.isEmpty()) {
        item(key = "series_empty_$selectedSeasonNumber", contentType = "series_empty") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (effectiveFilter != "ALL") "No matching starred or noted entries" else if (isMangaMode) "No entries listed yet" else "Season in production • Episodes will appear as broadcast dates are announced",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else if (isLightNovelVolumeMode && effectiveRenderMode == EpisodeLayoutMode.POSTER_GRID) {
        val rows = filteredSeasonEpisodes.chunked(3)
        itemsIndexed(
            items = rows,
            key = { rowIdx, row -> "ln_grid_${selectedSeasonNumber}_${effectiveFilter}_${rowIdx}_${row.firstOrNull()?.episodeNumber}" },
            contentType = { _, _ -> "ln_grid_row" }
        ) { _, rowItems ->
            LightNovelVolumePosterRow(
                rowItems = rowItems,
                isEpWatched = ::isEpWatched,
                fallbackCoverUrl = fallbackCoverUrl,
                episodeMetaMap = episodeMetaMap,
                onToggleWatched = handleEpisodeClick,
                onOpenDetail = onOpenEpisodeDetail,
                onLongPress = { seriesState.activeActionSheetEpisode = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }
    } else {
        when (effectiveRenderMode) {
            EpisodeLayoutMode.HORIZONTAL -> {
                item(key = "series_horiz_${selectedSeasonNumber}_$effectiveFilter", contentType = "series_horiz") {
                    EpisodeHorizontalRow(
                        episodes = filteredSeasonEpisodes,
                        watchedEpisodes = watchedEpisodes,
                        fallbackImageUrl = if (isMangaMode) fallbackCoverUrl else fallbackBackdropUrl,
                        scale = layoutScale,
                        onToggleWatched = handleEpisodeClick,
                        onOpenDetail = onOpenEpisodeDetail,
                        onLongPress = { seriesState.activeActionSheetEpisode = it },
                        episodeMetaMap = episodeMetaMap,
                        useProgressAsWatchedTruth = useProgressAsWatchedTruth,
                        currentTotalProgress = currentTotalProgress,
                        isMangaMode = isMangaMode,
                        isLightNovelVolumeMode = isLightNovelVolumeMode
                    )
                }
            }

            EpisodeLayoutMode.LIST -> {
                val listCardHeight = if (isLightNovelVolumeMode) (116.dp * layoutScale).coerceAtLeast(98.dp) else (120.dp * layoutScale).coerceAtLeast(96.dp)
                val listImageWidth = if (isLightNovelVolumeMode) (78.dp * layoutScale).coerceAtLeast(66.dp) else (120.dp * layoutScale).coerceAtLeast(96.dp)
                val effectiveFallback = if (isMangaMode) fallbackCoverUrl else fallbackBackdropUrl
                itemsIndexed(
                    items = filteredSeasonEpisodes,
                    key = { idx, ep -> "ep_list_${ep.seasonNumber}_${ep.episodeNumber}_${ep.id}_$idx" },
                    contentType = { _, _ -> "ep_list_card" }
                ) { _, episode ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        EpisodeListCardItem(
                            episode = episode,
                            isWatched = isEpWatched(episode),
                            fallbackImageUrl = effectiveFallback,
                            cardHeight = listCardHeight,
                            imageWidth = listImageWidth,
                            onToggleWatched = { handleEpisodeClick(episode) },
                            onOpenDetail = { onOpenEpisodeDetail(episode) },
                            onLongPress = { seriesState.activeActionSheetEpisode = episode },
                            meta = episodeMetaMap[episode.seasonNumber to episode.episodeNumber],
                            isMangaMode = isMangaMode,
                            isLightNovelVolumeMode = isLightNovelVolumeMode
                        )
                    }
                }
            }

            EpisodeLayoutMode.VERTICAL_CARD, EpisodeLayoutMode.POSTER_GRID -> {
                val effectiveFallback = if (isMangaMode) fallbackCoverUrl else fallbackBackdropUrl
                itemsIndexed(
                    items = filteredSeasonEpisodes,
                    key = { idx, ep -> "ep_vcard_${ep.seasonNumber}_${ep.episodeNumber}_${ep.id}_$idx" },
                    contentType = { _, _ -> "ep_vcard" }
                ) { _, episode ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 7.dp)
                    ) {
                        EpisodeVerticalCardItem(
                            episode = episode,
                            isWatched = isEpWatched(episode),
                            fallbackImageUrl = effectiveFallback,
                            scale = layoutScale,
                            onToggleWatched = { handleEpisodeClick(episode) },
                            onOpenDetail = { onOpenEpisodeDetail(episode) },
                            onLongPress = { seriesState.activeActionSheetEpisode = episode },
                            meta = episodeMetaMap[episode.seasonNumber to episode.episodeNumber],
                            isMangaMode = isMangaMode,
                            isLightNovelVolumeMode = isLightNovelVolumeMode
                        )
                    }
                }
            }

            EpisodeLayoutMode.TEXT_ONLY -> {
                itemsIndexed(
                    items = filteredSeasonEpisodes,
                    key = { idx, ep -> "ep_text_${ep.seasonNumber}_${ep.episodeNumber}_${ep.id}_$idx" },
                    contentType = { _, _ -> "ep_text" }
                ) { idx, episode ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        EpisodeTextOnlyChecklistRow(
                            episode = episode,
                            isWatched = isEpWatched(episode),
                            onToggleWatched = { handleEpisodeClick(episode) },
                            onOpenDetail = { onOpenEpisodeDetail(episode) },
                            onLongPress = { seriesState.activeActionSheetEpisode = episode },
                            meta = episodeMetaMap[episode.seasonNumber to episode.episodeNumber],
                            isMangaMode = isMangaMode,
                            isLightNovelVolumeMode = isLightNovelVolumeMode
                        )
                        if (idx < filteredSeasonEpisodes.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = Color.White.copy(alpha = 0.08f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }

    item(key = "series_end_spacer", contentType = "spacer") {
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun DetailSeriesDialogs(
    seriesState: DetailSeriesUiState,
    seasons: List<MediaSeason>,
    episodes: List<MediaEpisode>,
    episodesBySeason: Map<Int, List<MediaEpisode>>,
    watchedEpisodes: Set<Pair<Int, Int>>,
    selectedSeasonNumber: Int,
    isFillerSkipEnabled: Boolean,
    isMangaMode: Boolean,
    isLightNovelVolumeMode: Boolean,
    maxTotalProgress: Int?,
    currentTotalProgress: Int?,
    onToggleEpisodeWatched: (MediaEpisode) -> Unit,
    onMarkPreviousWatched: (MediaEpisode) -> Unit,
    onToggleSeasonWatched: (Int) -> Unit,
    onMarkPreviousSeasonsWatched: ((Int) -> Unit)? = null,
    onMarkWatchedChoice: ((MediaEpisode, Boolean) -> Unit)? = null,
    onJumpToProgress: ((Int) -> Unit)? = null,
    seasonMetaMap: Map<Int, com.omnitrack.app.data.db.entity.SeasonMetaEntity> = emptyMap(),
    onSaveSeasonMeta: ((Int, Float?, String?) -> Unit)? = null
) {
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

    // Hold-a-Season Action Sheet (1:1 Nuvio Parity)
    seriesState.activeActionSheetSeason?.let { season ->
        val seasonLabel = season.name.ifBlank { formatSeasonLabel(season.seasonNumber) }
        val seasonEps = episodesBySeason[season.seasonNumber]
            ?: episodes.filter { it.seasonNumber == season.seasonNumber }
        val effectiveCount = maxOf(season.episodeCount, seasonEps.size)
        val watchedCount = if (seasonEps.isNotEmpty()) {
            seasonEps.count { isEpWatched(it) }
        } else {
            watchedEpisodes.count { it.first == season.seasonNumber }
        }
        val isSeasonWatched = effectiveCount > 0 && watchedCount >= effectiveCount
        val canMarkPrevious = seasons.any { it.seasonNumber in 1 until season.seasonNumber }

        val existingMeta = seasonMetaMap[season.seasonNumber]
        SeasonWatchedActionSheet(
            seasonLabel = seasonLabel,
            episodeCount = effectiveCount,
            watchedCount = watchedCount,
            isSeasonWatched = isSeasonWatched,
            canMarkPreviousSeasons = canMarkPrevious,
            isReadingMode = isMangaMode,
            initialRating = existingMeta?.userRating,
            initialNote = existingMeta?.userNote,
            onSaveSeasonMeta = onSaveSeasonMeta?.let { cb ->
                { rating, note -> cb(season.seasonNumber, rating, note) }
            },
            onDismiss = { seriesState.activeActionSheetSeason = null },
            onToggleSeasonWatched = { onToggleSeasonWatched(season.seasonNumber) },
            onMarkPreviousSeasonsWatched = { onMarkPreviousSeasonsWatched?.invoke(season.seasonNumber) }
        )
    }

    // Watched Action Sheet
    seriesState.activeActionSheetEpisode?.let { ep ->
        val currentSeasonObj = seasons.firstOrNull { it.seasonNumber == ep.seasonNumber }
        val seasonLabel = currentSeasonObj?.name ?: "Season ${ep.seasonNumber}"
        val isWatched = isEpWatched(ep)
        val seasonEps = episodesBySeason[ep.seasonNumber] ?: episodes.filter { it.seasonNumber == ep.seasonNumber }
        val isSeasonWatched = seasonEps.isNotEmpty() && seasonEps.all { isEpWatched(it) }

        EpisodeWatchedActionSheet(
            episode = ep,
            seasonLabel = seasonLabel,
            isEpisodeWatched = isWatched,
            canMarkPreviousEpisodes = ep.episodeNumber > 1 || ep.seasonNumber > 1,
            isSeasonWatched = isSeasonWatched,
            onDismiss = { seriesState.activeActionSheetEpisode = null },
            onToggleWatched = {
                if (!isWatched && isFillerSkipEnabled) {
                    val activeEps = episodesBySeason[selectedSeasonNumber]
                        ?: episodes.filter { it.seasonNumber == selectedSeasonNumber }
                    val precedingFillers = activeEps.filter {
                        it.episodeNumber < ep.episodeNumber &&
                            it.isFiller &&
                            (it.seasonNumber to it.episodeNumber) !in watchedEpisodes
                    }
                    if (precedingFillers.isNotEmpty()) {
                        seriesState.fillerPromptEpisode = ep
                    } else {
                        onToggleEpisodeWatched(ep)
                    }
                } else {
                    onToggleEpisodeWatched(ep)
                }
            },
            onMarkPreviousWatched = { onMarkPreviousWatched(ep) },
            onToggleSeasonWatched = { onToggleSeasonWatched(ep.seasonNumber) }
        )
    }

    // Instant Keypad Jump Dialog for Chapters & Volumes
    if (seriesState.showJumpDialog && onJumpToProgress != null) {
        val currentCh = currentTotalProgress ?: 0
        val unitName = if (isLightNovelVolumeMode) "Volume" else "Chapter"
        var inputText by remember(seriesState.showJumpDialog) { mutableStateOf(currentCh.toString()) }
        val parsedVal = inputText.trim().toIntOrNull()
        val maxVal = maxTotalProgress?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { seriesState.showJumpDialog = false },
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
                        seriesState.showJumpDialog = false
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
                TextButton(onClick = { seriesState.showJumpDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.70f))
                }
            }
        )
    }

    // Skip Fillers Confirmation Dialog
    if (seriesState.fillerPromptEpisode != null) {
        val targetEp = seriesState.fillerPromptEpisode!!
        AlertDialog(
            onDismissRequest = { seriesState.fillerPromptEpisode = null },
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
                TextButton(
                    onClick = {
                        val ep = targetEp
                        seriesState.fillerPromptEpisode = null
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
                TextButton(
                    onClick = {
                        val ep = targetEp
                        seriesState.fillerPromptEpisode = null
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
    onOpenDetail: (MediaEpisode) -> Unit,
    onLongPress: (MediaEpisode) -> Unit,
    episodeMetaMap: Map<Pair<Int, Int>, EpisodeProgressEntity> = emptyMap(),
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
        itemsIndexed(episodes, key = { idx, it -> "${it.seasonNumber}_${it.episodeNumber}_${it.id}_$idx" }) { _, episode ->
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
                onOpenDetail = { onOpenDetail(episode) },
                onLongPress = { onLongPress(episode) },
                meta = episodeMetaMap[episode.seasonNumber to episode.episodeNumber],
                isMangaMode = isMangaMode,
                isLightNovelVolumeMode = isLightNovelVolumeMode
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LightNovelVolumePosterRow(
    rowItems: List<MediaEpisode>,
    isEpWatched: (MediaEpisode) -> Boolean,
    fallbackCoverUrl: String?,
    episodeMetaMap: Map<Pair<Int, Int>, EpisodeProgressEntity>,
    onToggleWatched: (MediaEpisode) -> Unit,
    onOpenDetail: (MediaEpisode) -> Unit,
    onLongPress: (MediaEpisode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rowItems.forEach { vol ->
            val isRead = isEpWatched(vol)
            val meta = episodeMetaMap[vol.seasonNumber to vol.episodeNumber]
            val primaryUrl = vol.stillUrl?.takeIf { it.isNotBlank() }
            val fallbackUrl = fallbackCoverUrl?.takeIf { it.isNotBlank() && it != primaryUrl }
            var primaryFailed by remember(primaryUrl) { mutableStateOf(false) }
            var fallbackFailed by remember(fallbackUrl) { mutableStateOf(false) }
            val activeRaw = when {
                primaryUrl != null && !primaryFailed -> primaryUrl
                fallbackUrl != null && !fallbackFailed -> fallbackUrl
                else -> null
            }
            val coverModel = remember(activeRaw) { toCoilImageModel(activeRaw) }
            val volBadge = remember(vol.title, vol.episodeNumber) {
                extractVolumeBadgeLabel(vol.title, vol.episodeNumber)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = { onOpenDetail(vol) },
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
                    if (coverModel != null) {
                        AsyncImage(
                            model = coverModel,
                            contentDescription = vol.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onError = {
                                if (activeRaw == primaryUrl) primaryFailed = true else fallbackFailed = true
                            }
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
                    if (meta?.isBookmarked == true || !meta?.userNote.isNullOrBlank() || meta?.userRating != null) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (meta?.isBookmarked == true) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Starred",
                                    tint = AccentGold,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            if (!meta?.userNote.isNullOrBlank()) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Has Note",
                                    tint = AccentArctic,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    NuvioAnimatedWatchedBadge(
                        isWatched = isRead,
                        onClick = { onToggleWatched(vol) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 7.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = volBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        val scoreToShow = meta?.userRating ?: vol.voteAverage
                        if (scoreToShow != null && scoreToShow > 0f) {
                            Text(
                                text = "★${String.format(java.util.Locale.US, "%.1f", scoreToShow)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (meta?.userRating != null) AccentJade else AccentGold
                            )
                        }
                    }
                }
                val formattedDate = remember(vol.airDate) { formatEpisodeAirDate(vol.airDate) }
                Text(
                    text = meta?.customLabel?.takeIf { it.isNotBlank() } ?: formattedDate ?: vol.title,
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
    val primaryUrl = episode.stillUrl?.takeIf { it.isNotBlank() }
    val fallbackUrl = fallbackCoverUrl?.takeIf { it.isNotBlank() && it != primaryUrl }
    var primaryFailed by remember(primaryUrl) { mutableStateOf(false) }
    var fallbackFailed by remember(fallbackUrl) { mutableStateOf(false) }

    val activeRaw = when {
        primaryUrl != null && !primaryFailed -> primaryUrl
        fallbackUrl != null && !fallbackFailed -> fallbackUrl
        else -> null
    }
    val isUsingFallback = activeRaw != null && activeRaw == fallbackUrl && !isMangaMode && !isLightNovelVolumeMode
    val activeModel = remember(activeRaw) { toCoilImageModel(activeRaw) }

    if (activeModel != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = activeModel,
                contentDescription = episode.title,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (isUsingFallback) 0.42f else 1f),
                contentScale = ContentScale.Crop,
                onError = {
                    if (activeRaw == primaryUrl) primaryFailed = true else fallbackFailed = true
                }
            )
            if (isUsingFallback) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = formatEpisodeBadge(episode.seasonNumber, episode.episodeNumber),
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(placeholderBg),
            contentAlignment = Alignment.Center
        ) {
            val badgeTxt = when {
                isLightNovelVolumeMode -> extractVolumeBadgeLabel(episode.title, episode.episodeNumber)
                isMangaMode -> "Ch.${episode.episodeNumber}"
                else -> formatEpisodeBadge(episode.seasonNumber, episode.episodeNumber)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeTxt,
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
    onOpenDetail: () -> Unit,
    onLongPress: () -> Unit,
    meta: EpisodeProgressEntity? = null,
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
                onClick = onOpenDetail,
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

        // Bookmark / Note / Filler Top-Start Indicators
        if (episode.isFiller || episode.isRecap || meta?.isBookmarked == true || !meta?.userNote.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (episode.isFiller || episode.isRecap) {
                    Box(
                        modifier = Modifier
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
                if (meta?.isBookmarked == true) {
                    Icon(Icons.Default.Star, contentDescription = "Starred", tint = AccentGold, modifier = Modifier.size(13.dp))
                }
                if (!meta?.userNote.isNullOrBlank()) {
                    Icon(Icons.Default.Edit, contentDescription = "Has Note", tint = AccentArctic, modifier = Modifier.size(12.dp))
                }
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
            val epBadge = remember(episode.seasonNumber, episode.episodeNumber) {
                formatEpisodeBadge(episode.seasonNumber, episode.episodeNumber)
            }
            if ((!isMangaMode && !isLightNovelVolumeMode && epBadge.isNotBlank()) || !formattedDate.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!isMangaMode && !isLightNovelVolumeMode && epBadge.isNotBlank()) {
                        EpisodeCodeBadge(
                            text = epBadge,
                            textSize = 10.sp,
                            backgroundAlpha = 0.58f
                        )
                    }
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
                }
            }

            val titleText = meta?.customLabel?.takeIf { it.isNotBlank() }
                ?: episode.title.ifBlank { "Episode ${episode.episodeNumber}" }
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

            // Bottom Metadata Line: Runtime, IMDb/Community Rating, User Rating, Air Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (episode.runtimeMinutes != null && episode.runtimeMinutes > 0) {
                    Text(
                        text = "${episode.runtimeMinutes}m",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
                if (meta?.userRating != null) {
                    Text(
                        text = "★ ${String.format(java.util.Locale.US, "%.1f", meta.userRating)}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentJade
                    )
                } else if (episode.voteAverage != null && episode.voteAverage > 0) {
                    if (isMangaMode) {
                        Text(
                            text = "★ ${String.format(java.util.Locale.US, "%.1f", episode.voteAverage)}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold
                        )
                    } else {
                        ImdbEpisodeRatingBadge(
                            rating = episode.voteAverage,
                            logoWidth = 22.dp,
                            logoHeight = 11.dp,
                            textSize = 10.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                val bottomDate = remember(episode.airDate) { formatEpisodeAirDate(episode.airDate) }
                if (!bottomDate.isNullOrBlank()) {
                    Text(
                        text = bottomDate,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeTextOnlyChecklistRow(
    episode: MediaEpisode,
    isWatched: Boolean,
    onToggleWatched: () -> Unit,
    onOpenDetail: () -> Unit,
    onLongPress: () -> Unit,
    meta: EpisodeProgressEntity? = null,
    isMangaMode: Boolean = false,
    isLightNovelVolumeMode: Boolean = false
) {
    val numberPrefix = when {
        isLightNovelVolumeMode -> "Vol. ${episode.episodeNumber}"
        isMangaMode -> "Ch. ${episode.episodeNumber}"
        episode.seasonNumber > 0 -> "S${episode.seasonNumber} E${episode.episodeNumber}"
        else -> "E${episode.episodeNumber}"
    }
    val rawTitle = meta?.customLabel?.takeIf { it.isNotBlank() } ?: episode.title.trim()
    val defaultUnitTitle = if (isLightNovelVolumeMode) "Volume ${episode.episodeNumber}" else if (isMangaMode) "Chapter ${episode.episodeNumber}" else "Episode ${episode.episodeNumber}"
    val displayTitle = if (rawTitle.isBlank() || rawTitle.equals(defaultUnitTitle, ignoreCase = true)) {
        numberPrefix
    } else {
        "$numberPrefix · $rawTitle"
    }
    val formattedDate = remember(episode.airDate) { formatEpisodeAirDate(episode.airDate) }
    val subtitleParts = buildList {
        if (!formattedDate.isNullOrBlank()) add(formattedDate)
        episode.runtimeMinutes?.takeIf { it > 0 }?.let { add("${it}m") }
        if (!isMangaMode && episode.voteAverage != null && episode.voteAverage > 0) {
            add("★ ${String.format(java.util.Locale.US, "%.1f", episode.voteAverage)}")
        }
        if (episode.isRecap) add("RECAP") else if (episode.isFiller) add("FILLER")
        meta?.userNote?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpenDetail,
                onLongClick = onLongPress
            )
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isWatched) AccentJade else Color.Transparent)
                .border(
                    1.5.dp,
                    if (isWatched) AccentJade else Color.White.copy(alpha = 0.34f),
                    CircleShape
                )
                .clickable { onToggleWatched() },
            contentAlignment = Alignment.Center
        ) {
            if (isWatched) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = displayTitle,
                color = if (isWatched) Color.White.copy(alpha = 0.65f) else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitleParts.isNotBlank()) {
                Text(
                    text = subtitleParts,
                    color = if (!meta?.userNote.isNullOrBlank()) AccentArctic.copy(alpha = 0.85f) else TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (meta?.isBookmarked == true) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Starred",
                tint = AccentGold,
                modifier = Modifier.size(14.dp)
            )
        }
        if (meta?.userRating != null) {
            Text(
                text = String.format(java.util.Locale.US, "%.1f", meta.userRating),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = AccentJade
            )
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
    onOpenDetail: () -> Unit,
    onLongPress: () -> Unit,
    meta: EpisodeProgressEntity? = null,
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
                onClick = onOpenDetail,
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

                // 1:1 Nuvio Episode Code Badge ("S1 E1") pinned at Top Start
                val epBadge = remember(episode.seasonNumber, episode.episodeNumber) {
                    formatEpisodeBadge(episode.seasonNumber, episode.episodeNumber)
                }
                if (!isMangaMode && !isLightNovelVolumeMode && epBadge.isNotBlank()) {
                    EpisodeCodeBadge(
                        text = epBadge,
                        textSize = 9.5.sp,
                        backgroundAlpha = 0.82f,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 8.dp, start = 8.dp)
                    )
                }

                // Solid White FILLER / RECAP Badge (below TopStart if epBadge present)
                if (episode.isFiller || episode.isRecap) {
                    Box(
                        modifier = Modifier
                            .align(if (!isMangaMode && !isLightNovelVolumeMode && epBadge.isNotBlank()) Alignment.BottomStart else Alignment.TopStart)
                            .padding(8.dp)
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
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
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
                val displayTitle = meta?.customLabel?.takeIf { it.isNotBlank() }
                    ?: episode.title.ifBlank { "Episode ${episode.episodeNumber}" }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp,
                        letterSpacing = 0.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (meta?.isBookmarked == true) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Starred",
                            tint = AccentGold,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (!meta?.userNote.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Has Note",
                            tint = AccentArctic,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (meta?.userRating != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentJade.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f", meta.userRating),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentJade
                            )
                        }
                    }
                }

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
                            if (isMangaMode) {
                                Text(
                                    text = "★ ${String.format(java.util.Locale.US, "%.1f", episode.voteAverage)}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentGold
                                )
                            } else {
                                ImdbEpisodeRatingBadge(
                                    rating = episode.voteAverage,
                                    logoWidth = 24.dp,
                                    logoHeight = 12.dp,
                                    textSize = 12.sp
                                )
                            }
                        }
                    }
                }

                val notePreview = meta?.userNote?.takeIf { it.isNotBlank() } ?: episode.overview
                if (!notePreview.isNullOrBlank()) {
                    Text(
                        text = notePreview,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = if (!meta?.userNote.isNullOrBlank()) AccentArctic.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.60f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
    onOpenDetail: () -> Unit,
    onLongPress: () -> Unit,
    meta: EpisodeProgressEntity? = null,
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
                onClick = onOpenDetail,
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

        // Bookmark / Note / Filler Top-Start Indicators
        if (episode.isFiller || episode.isRecap || meta?.isBookmarked == true || !meta?.userNote.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (episode.isFiller || episode.isRecap) {
                    Box(
                        modifier = Modifier
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
                if (meta?.isBookmarked == true) {
                    Icon(Icons.Default.Star, contentDescription = "Starred", tint = AccentGold, modifier = Modifier.size(14.dp))
                }
                if (!meta?.userNote.isNullOrBlank()) {
                    Icon(Icons.Default.Edit, contentDescription = "Has Note", tint = AccentArctic, modifier = Modifier.size(13.dp))
                }
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
            val epBadge = remember(episode.seasonNumber, episode.episodeNumber) {
                formatEpisodeBadge(episode.seasonNumber, episode.episodeNumber)
            }
            if ((!isMangaMode && !isLightNovelVolumeMode && epBadge.isNotBlank()) || !formattedDate.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!isMangaMode && !isLightNovelVolumeMode && epBadge.isNotBlank()) {
                        EpisodeCodeBadge(
                            text = epBadge,
                            textSize = 10.sp,
                            backgroundAlpha = 0.58f
                        )
                    }
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
                }
            }

            val titleText = meta?.customLabel?.takeIf { it.isNotBlank() }
                ?: episode.title.ifBlank { "Episode ${episode.episodeNumber}" }
            Text(
                text = titleText,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val overviewOrNote = meta?.userNote?.takeIf { it.isNotBlank() } ?: episode.overview
            if (!overviewOrNote.isNullOrBlank()) {
                Text(
                    text = overviewOrNote,
                    fontSize = 12.sp,
                    color = if (!meta?.userNote.isNullOrBlank()) AccentArctic.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.86f),
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
                if (meta?.userRating != null) {
                    Text(
                        text = "You: ★ ${String.format(java.util.Locale.US, "%.1f", meta.userRating)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentJade
                    )
                } else if (episode.voteAverage != null && episode.voteAverage > 0) {
                    if (isMangaMode) {
                        Text(
                            text = "★ ${String.format(java.util.Locale.US, "%.1f", episode.voteAverage)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentGold
                        )
                    } else {
                        ImdbEpisodeRatingBadge(rating = episode.voteAverage)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                val bottomDate = remember(episode.airDate) { formatEpisodeAirDate(episode.airDate) }
                if (!bottomDate.isNullOrBlank()) {
                    Text(text = bottomDate, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeasonPosterScrollRow(
    seasons: List<MediaSeason>,
    selectedSeasonNumber: Int,
    episodes: List<MediaEpisode>,
    episodesBySeason: Map<Int, List<MediaEpisode>>,
    watchedEpisodes: Set<Pair<Int, Int>>,
    seasonMetaMap: Map<Int, com.omnitrack.app.data.db.entity.SeasonMetaEntity> = emptyMap(),
    fallbackImageUrl: String?,
    useProgressAsWatchedTruth: Boolean = false,
    currentTotalProgress: Int? = null,
    onSelectSeason: (Int) -> Unit,
    onLongPressSeason: ((MediaSeason) -> Unit)? = null
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
            val watchedCount = if (seasonEps.isNotEmpty()) {
                if (useProgressAsWatchedTruth) {
                    seasonEps.count { it.episodeNumber <= (currentTotalProgress ?: 0) }
                } else {
                    seasonEps.count { (it.seasonNumber to it.episodeNumber) in watchedEpisodes }
                }
            } else {
                watchedEpisodes.count { it.first == season.seasonNumber }
            }
            val effectiveTotal = maxOf(season.episodeCount, seasonEps.size)
            val allWatched = effectiveTotal > 0 && watchedCount >= effectiveTotal
            val label = season.name.ifBlank { formatSeasonLabel(season.seasonNumber) }

            Column(
                modifier = Modifier
                    .width(100.dp)
                    .combinedClickable(
                        onClick = { onSelectSeason(season.seasonNumber) },
                        onLongClick = { onLongPressSeason?.invoke(season) }
                    ),
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
                    val effectivePosterModel = remember(effectivePoster) { toCoilImageModel(effectivePoster) }
                    if (effectivePosterModel != null) {
                        AsyncImage(
                            model = effectivePosterModel,
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
                    if (allWatched) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(AccentJade),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    val sMeta = seasonMetaMap[season.seasonNumber]
                    val sRating = sMeta?.userRating?.takeIf { it > 0f }
                    val hasNote = !sMeta?.userNote.isNullOrBlank()
                    if (sRating != null || hasNote) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.80f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (sRating != null) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Star,
                                    contentDescription = null,
                                    tint = com.omnitrack.app.ui.theme.AccentGold,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f", sRating),
                                    color = com.omnitrack.app.ui.theme.AccentGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (hasNote) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Edit,
                                    contentDescription = "Season Note",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(10.dp)
                                )
                            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeasonTextChipScrollRow(
    seasons: List<MediaSeason>,
    selectedSeasonNumber: Int,
    episodes: List<MediaEpisode>,
    episodesBySeason: Map<Int, List<MediaEpisode>>,
    watchedEpisodes: Set<Pair<Int, Int>>,
    seasonMetaMap: Map<Int, com.omnitrack.app.data.db.entity.SeasonMetaEntity> = emptyMap(),
    useProgressAsWatchedTruth: Boolean = false,
    currentTotalProgress: Int? = null,
    onSelectSeason: (Int) -> Unit,
    onLongPressSeason: ((MediaSeason) -> Unit)? = null
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
            val watchedCount = if (seasonEps.isNotEmpty()) {
                if (useProgressAsWatchedTruth) {
                    seasonEps.count { it.episodeNumber <= (currentTotalProgress ?: 0) }
                } else {
                    seasonEps.count { (it.seasonNumber to it.episodeNumber) in watchedEpisodes }
                }
            } else {
                watchedEpisodes.count { it.first == season.seasonNumber }
            }
            val effectiveTotal = maxOf(season.episodeCount, seasonEps.size)
            val allWatched = effectiveTotal > 0 && watchedCount >= effectiveTotal
            val label = season.name.ifBlank { formatSeasonLabel(season.seasonNumber) }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent
                    )
                    .combinedClickable(
                        onClick = { onSelectSeason(season.seasonNumber) },
                        onLongClick = { onLongPressSeason?.invoke(season) }
                    )
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
                val sMeta = seasonMetaMap[season.seasonNumber]
                val sRating = sMeta?.userRating?.takeIf { it > 0f }
                val hasNote = !sMeta?.userNote.isNullOrBlank()
                if (sRating != null) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Star,
                        contentDescription = null,
                        tint = com.omnitrack.app.ui.theme.AccentGold,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", sRating),
                        color = com.omnitrack.app.ui.theme.AccentGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (hasNote) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Edit,
                        contentDescription = "Season Note",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
