package com.omnitrack.app.ui.screens.detail.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.R
import com.omnitrack.app.data.db.entity.EpisodeProgressEntity
import com.omnitrack.app.data.enrichment.EnrichmentRepository
import com.omnitrack.app.data.model.MediaEpisode
import com.omnitrack.app.data.preferences.FallbackBackgroundStyle
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.ui.components.ArtworkBackdrop
import com.omnitrack.app.ui.components.CoverGlowBox
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Rich AMOLED Volume / Chapter / Episode Detail Sheet.
 * Supports:
 * - On-demand RanobeDB per-volume details (community rating, official synopsis, JP/EN dates, page counts, ISBN, links)
 * - Personal 1.0–10.0 decimal rating, bookmark star, custom chapter/arc label, and reading/watching notes
 * - Smooth left/right swipe & arrow navigation between adjacent volumes/chapters
 * - In-window rendering + Poweramp pull-down-to-step-back gesture
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VolumeChapterDetailSheet(
    episodes: List<MediaEpisode>,
    initialSeasonNumber: Int,
    initialEpisodeNumber: Int,
    seasonLabel: String,
    fallbackCoverUrl: String?,
    isMangaMode: Boolean,
    isLightNovelVolumeMode: Boolean,
    watchedEpisodes: Set<Pair<Int, Int>>,
    useProgressAsWatchedTruth: Boolean,
    currentTotalProgress: Int?,
    episodeMetaMap: Map<Pair<Int, Int>, EpisodeProgressEntity>,
    ranobeBookDetails: Map<Long, EnrichmentRepository.RanobeDbBookDetail>,
    onRequestRanobeBook: (Long, List<Long>) -> Unit,
    onToggleWatched: (MediaEpisode) -> Unit,
    onMarkPreviousWatched: (MediaEpisode) -> Unit,
    onToggleSeasonWatched: (Int) -> Unit,
    onSaveMetadata: (
        seasonNumber: Int,
        episodeNumber: Int,
        userRating: Float?,
        userNote: String?,
        isBookmarked: Boolean,
        customLabel: String?
    ) -> Unit,
    onDismiss: () -> Unit,
    presentationSettings: PresentationSettings? = null,
    backdropBitmap: ImageBitmap? = null,
    backdropTargetUrl: String? = null
) {
    if (episodes.isEmpty()) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val startIdx = remember(episodes, initialSeasonNumber, initialEpisodeNumber) {
        episodes.indexOfFirst {
            it.seasonNumber == initialSeasonNumber && it.episodeNumber == initialEpisodeNumber
        }.takeIf { it >= 0 } ?: 0
    }
    var currentIndex by remember(episodes, initialSeasonNumber, initialEpisodeNumber) {
        mutableIntStateOf(startIdx)
    }
    val ep = episodes.getOrNull(currentIndex.coerceIn(episodes.indices)) ?: return
    val scrollState = rememberScrollState()

    val currentMeta = episodeMetaMap[ep.seasonNumber to ep.episodeNumber]
    val bookId = ep.ranobeBookId
    val ranobeBook = bookId?.let { ranobeBookDetails[it] }

    var personalRating by remember(ep.seasonNumber, ep.episodeNumber) {
        mutableStateOf(currentMeta?.userRating)
    }
    var noteDraft by remember(ep.seasonNumber, ep.episodeNumber) {
        mutableStateOf(currentMeta?.userNote.orEmpty())
    }
    var customLabelDraft by remember(ep.seasonNumber, ep.episodeNumber) {
        mutableStateOf(currentMeta?.customLabel.orEmpty())
    }
    var isBookmarkedState by remember(ep.seasonNumber, ep.episodeNumber) {
        mutableStateOf(currentMeta?.isBookmarked ?: false)
    }
    var noteSavedFeedback by remember(ep.seasonNumber, ep.episodeNumber) { mutableStateOf(false) }

    // Sync if external DB update arrives when user hasn't typed
    LaunchedEffect(currentMeta) {
        if (currentMeta != null) {
            if (personalRating == null && currentMeta.userRating != null) personalRating = currentMeta.userRating
            if (noteDraft.isEmpty() && !currentMeta.userNote.isNullOrEmpty()) noteDraft = currentMeta.userNote
            if (customLabelDraft.isEmpty() && !currentMeta.customLabel.isNullOrEmpty()) customLabelDraft = currentMeta.customLabel
            isBookmarkedState = currentMeta.isBookmarked
        }
    }

    fun persistCurrentEdits() {
        val trimmedNote = noteDraft.trim().takeIf { it.isNotBlank() }
        val trimmedLabel = customLabelDraft.trim().takeIf { it.isNotBlank() }
        val origNote = currentMeta?.userNote?.trim()?.takeIf { it.isNotBlank() }
        val origLabel = currentMeta?.customLabel?.trim()?.takeIf { it.isNotBlank() }
        val changed = personalRating != currentMeta?.userRating ||
            isBookmarkedState != (currentMeta?.isBookmarked ?: false) ||
            trimmedNote != origNote ||
            trimmedLabel != origLabel
        if (changed) {
            onSaveMetadata(
                ep.seasonNumber,
                ep.episodeNumber,
                personalRating,
                trimmedNote,
                isBookmarkedState,
                trimmedLabel
            )
        }
    }

    DisposableEffect(ep.seasonNumber, ep.episodeNumber) {
        onDispose {
            persistCurrentEdits()
        }
    }

    // Request RanobeDB per-book details + rolling ±1 prefetch
    LaunchedEffect(currentIndex, bookId) {
        scrollState.scrollTo(0)
        if (bookId != null && bookId > 0L) {
            val prevBookId = episodes.getOrNull(currentIndex - 1)?.ranobeBookId
            val nextBookId = episodes.getOrNull(currentIndex + 1)?.ranobeBookId
            onRequestRanobeBook(bookId, listOfNotNull(nextBookId, prevBookId))
        }
    }

    val isWatched = if (useProgressAsWatchedTruth) {
        ep.episodeNumber <= (currentTotalProgress ?: 0)
    } else {
        (ep.seasonNumber to ep.episodeNumber) in watchedEpisodes
    }
    val isSeasonWatched = episodes.isNotEmpty() && episodes.all { item ->
        if (useProgressAsWatchedTruth) item.episodeNumber <= (currentTotalProgress ?: 0)
        else (item.seasonNumber to item.episodeNumber) in watchedEpisodes
    }

    InWindowBottomSheet(
        onDismiss = {
            persistCurrentEdits()
            onDismiss()
        },
        isAtTop = { scrollState.value == 0 },
        maxHeightFraction = 0.90f,
        backgroundContent = {
            when (presentationSettings?.fallbackBackgroundStyle) {
                FallbackBackgroundStyle.CINEMATIC_BLUR -> {
                    ArtworkBackdrop(
                        image = backdropBitmap,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                }
                FallbackBackgroundStyle.DOMINANT_COLOR -> {
                    CoverGlowBox(
                        imageUrl = backdropTargetUrl,
                        modifier = Modifier.fillMaxSize()
                    ) {}
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize().background(AmoledBlack))
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(episodes.size, currentIndex) {
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            if (totalDragX < -55f && currentIndex < episodes.size - 1) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                persistCurrentEdits()
                                currentIndex++
                            } else if (totalDragX > 55f && currentIndex > 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                persistCurrentEdits()
                                currentIndex--
                            }
                            totalDragX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (!change.isConsumed) {
                                change.consume()
                                totalDragX += dragAmount
                            }
                        }
                    )
                }
        ) {
            // Top Navigation Strip (Previous / Badge / Next / Close)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeLabel = when {
                    isLightNovelVolumeMode -> "Volume ${ep.episodeNumber} of ${episodes.size}"
                    isMangaMode -> "Chapter ${ep.episodeNumber} of ${episodes.size}"
                    else -> "S${ep.seasonNumber} E${ep.episodeNumber}"
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = seasonLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = badgeLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val canPrev = currentIndex > 0
                    val canNext = currentIndex < episodes.size - 1
                    IconButton(
                        onClick = {
                            if (canPrev) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                persistCurrentEdits()
                                currentIndex--
                            }
                        },
                        enabled = canPrev,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Previous",
                            tint = if (canPrev) Color.White else Color.White.copy(alpha = 0.28f)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (canNext) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                persistCurrentEdits()
                                currentIndex++
                            }
                        },
                        enabled = canNext,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Next",
                            tint = if (canNext) Color.White else Color.White.copy(alpha = 0.28f)
                        )
                    }
                    IconButton(
                        onClick = {
                            persistCurrentEdits()
                            onDismiss()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Cover / Still + Headline Metadata Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    val coverRaw = ep.stillUrl?.takeIf { it.isNotBlank() } ?: fallbackCoverUrl
                    val coverModel = remember(coverRaw) {
                        val trimmed = coverRaw?.trim()?.takeIf { it.isNotBlank() }
                        when {
                            trimmed == null -> null
                            trimmed.startsWith("/") -> File(trimmed)
                            else -> trimmed
                        }
                    }
                    val thumbWidth = when {
                        isLightNovelVolumeMode -> 92.dp
                        isMangaMode -> 86.dp
                        else -> 124.dp
                    }
                    val thumbHeight = when {
                        isLightNovelVolumeMode -> 134.dp
                        isMangaMode -> 118.dp
                        else -> 76.dp
                    }

                    Box(
                        modifier = Modifier
                            .width(thumbWidth)
                            .height(thumbHeight)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverModel != null) {
                            AsyncImage(
                                model = coverModel,
                                contentDescription = ep.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = if (isLightNovelVolumeMode) "Vol.${ep.episodeNumber}" else if (isMangaMode) "Ch.${ep.episodeNumber}" else "E${ep.episodeNumber}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val headline = customLabelDraft.takeIf { it.isNotBlank() } ?: ep.title
                        Text(
                            text = headline,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 22.sp,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (customLabelDraft.isNotBlank() && customLabelDraft != ep.title) {
                            Text(
                                text = ep.title,
                                fontSize = 12.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ranobeBook?.titleOrig?.takeIf { it.isNotBlank() && it != headline }?.let { orig ->
                            Text(
                                text = orig,
                                fontSize = 11.5.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Dates & Pages line
                        val dateInfoParts = buildList {
                            val jpDate = formatVolumeDate(ranobeBook?.jpReleaseDate ?: ep.airDate)
                            val enDate = formatVolumeDate(ranobeBook?.enReleaseDate)
                            if (enDate != null && jpDate != null && enDate != jpDate) {
                                add("JP $jpDate")
                                add("EN $enDate")
                            } else if (jpDate != null) {
                                add(jpDate)
                            }
                            val pages = ranobeBook?.printPages ?: ranobeBook?.digitalPages
                            if (pages != null && pages > 0) {
                                add("$pages pp.")
                            }
                            if (ep.runtimeMinutes != null && ep.runtimeMinutes > 0) {
                                add("${ep.runtimeMinutes}m")
                            }
                        }
                        if (dateInfoParts.isNotEmpty()) {
                            Text(
                                text = dateInfoParts.joinToString("  •  "),
                                fontSize = 11.5.sp,
                                color = Color.White,
                                lineHeight = 15.sp
                            )
                        }

                        // Community Rating + User Rating Row
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val commScore = ranobeBook?.ratingScore ?: ep.voteAverage
                            if (commScore != null && commScore > 0f) {
                                val countSuffix = ranobeBook?.ratingCount?.takeIf { it > 0 }?.let { " ($it)" } ?: ""
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.30f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (!isMangaMode) {
                                        Image(
                                            painter = painterResource(R.drawable.rating_imdb),
                                            contentDescription = "IMDb",
                                            modifier = Modifier.size(width = 20.dp, height = 10.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = AccentGold,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", commScore)}$countSuffix",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF5C518)
                                    )
                                }
                            }
                            if (personalRating != null) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AccentJade.copy(alpha = 0.22f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = AccentJade,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "You: ${String.format(Locale.US, "%.1f", personalRating)}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentJade
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Quick Actions Row (translucent glass pills)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val readVerb = if (isMangaMode) "Read" else "Watched"
                    SheetActionPill(
                        icon = Icons.Default.Check,
                        label = if (isWatched) readVerb else "Mark $readVerb",
                        isActive = isWatched,
                        activeColor = AccentJade,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleWatched(ep)
                        }
                    )
                    SheetActionPill(
                        icon = Icons.Default.Bookmark,
                        label = if (isBookmarkedState) "Starred" else "Star",
                        isActive = isBookmarkedState,
                        activeColor = AccentGold,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isBookmarkedState = !isBookmarkedState
                            onSaveMetadata(
                                ep.seasonNumber,
                                ep.episodeNumber,
                                personalRating,
                                noteDraft.trim().takeIf { it.isNotBlank() },
                                isBookmarkedState,
                                customLabelDraft.trim().takeIf { it.isNotBlank() }
                            )
                        }
                    )
                    if (ep.episodeNumber > 1 || ep.seasonNumber > 1) {
                        SheetActionPill(
                            icon = Icons.Default.Check,
                            label = "$readVerb Up To Here",
                            isActive = false,
                            activeColor = AccentArctic,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMarkPreviousWatched(ep)
                            }
                        )
                    }
                    SheetActionPill(
                        icon = Icons.Default.Check,
                        label = if (isSeasonWatched) "Unmark All" else "All $readVerb",
                        isActive = isSeasonWatched,
                        activeColor = AccentArctic,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleSeasonWatched(ep.seasonNumber)
                        }
                    )
                }

                currentMeta?.watchedAtMs?.let { ts ->
                    val dateStr = remember(ts) {
                        SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(ts))
                    }
                    Text(
                        text = "${if (isMangaMode) "Read" else "Watched"} on $dateStr",
                        fontSize = 11.5.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 3. Official Volume/Episode Synopsis & RanobeDB Edition Metadata
                val synopsisText = ranobeBook?.synopsis ?: ep.overview?.takeIf { it.isNotBlank() }
                val isFetchingBook = bookId != null && bookId > 0L && ranobeBook == null
                if (isFetchingBook || !synopsisText.isNullOrBlank() || ranobeBook != null) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isLightNovelVolumeMode) "Volume Synopsis & Details" else "Synopsis",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (isFetchingBook) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 1.8.dp,
                                    color = AccentArctic
                                )
                            }
                        }

                        if (!synopsisText.isNullOrBlank()) {
                            var expanded by remember(ep.episodeNumber) { mutableStateOf(false) }
                            Text(
                                text = synopsisText,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = Color.White,
                                maxLines = if (expanded) Int.MAX_VALUE else 5,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { expanded = !expanded }
                            )
                        }

                        if (ranobeBook != null) {
                            val editionDetails = buildList {
                                if (ranobeBook.publishers.isNotEmpty()) {
                                    add("Publisher: ${ranobeBook.publishers.joinToString(", ")}")
                                }
                                val artist = ranobeBook.staff.firstOrNull { it.role == "Illustrator" }?.name
                                if (!artist.isNullOrBlank()) add("Art: $artist")
                                val translator = ranobeBook.staff.firstOrNull { it.role == "Translator" }?.name
                                if (!translator.isNullOrBlank()) add("Translator: $translator")
                                if (!ranobeBook.isbn13.isNullOrBlank()) add("ISBN: ${ranobeBook.isbn13}")
                            }
                            if (editionDetails.isNotEmpty()) {
                                Text(
                                    text = editionDetails.joinToString("  •  "),
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = Color.White
                                )
                            }
                            if (ranobeBook.links.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ranobeBook.links.forEach { link ->
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black.copy(alpha = 0.30f))
                                                .clickable {
                                                    try {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                                                    } catch (_: Exception) {}
                                                }
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = link.label,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = AccentArctic
                                            )
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = null,
                                                tint = AccentArctic,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))

                // 4. Precision Scrollable Rating Ruler (Unrated, 1.0 – 10.0 in 0.1 steps)
                PrecisionRatingRuler(
                    itemKey = ep.seasonNumber to ep.episodeNumber,
                    rating = personalRating,
                    onLiveRatingChange = { live -> personalRating = live },
                    onRatingCommitted = { committed ->
                        personalRating = committed
                        onSaveMetadata(
                            ep.seasonNumber,
                            ep.episodeNumber,
                            committed,
                            noteDraft.trim().takeIf { it.isNotBlank() },
                            isBookmarkedState,
                            customLabelDraft.trim().takeIf { it.isNotBlank() }
                        )
                    }
                )

                // 5. Custom Chapter / Arc Label + Personal Notes / Quotes
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isMangaMode && !isLightNovelVolumeMode) {
                        OutlinedTextField(
                            value = customLabelDraft,
                            onValueChange = {
                                customLabelDraft = it
                                noteSavedFeedback = false
                            },
                            singleLine = true,
                            label = { Text("Custom Chapter / Arc Label (Optional)", color = Color.White, fontSize = 12.sp) },
                            placeholder = { Text("e.g. Arc 3: Traveler / Gehrman Sparrow", color = Color.White, fontSize = 12.5.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentArctic,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = AccentArctic
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = AccentArctic,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Personal Notes & Quotes",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (noteSavedFeedback) AccentJade.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.30f))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    persistCurrentEdits()
                                    onSaveMetadata(
                                        ep.seasonNumber,
                                        ep.episodeNumber,
                                        personalRating,
                                        noteDraft.trim().takeIf { it.isNotBlank() },
                                        isBookmarkedState,
                                        customLabelDraft.trim().takeIf { it.isNotBlank() }
                                    )
                                    noteSavedFeedback = true
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (noteSavedFeedback) "Saved ✓" else "Save",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (noteSavedFeedback) AccentJade else Color.White
                            )
                        }
                    }

                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = {
                            noteDraft = it
                            noteSavedFeedback = false
                        },
                        minLines = 3,
                        maxLines = 6,
                        placeholder = {
                            Text(
                                text = "Write thoughts, favorite quotes, plot twists, or cliffhanger notes...",
                                color = Color.White,
                                fontSize = 12.5.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentJade,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = AccentJade
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun rulerIndexToRating(idx: Int): Float? =
    if (idx <= 0) null else (9 + idx.coerceIn(1, 91)) / 10f

private fun ratingToRulerIndex(rating: Float?): Int =
    if (rating == null || rating < 1.0f) 0
    else (((rating.coerceIn(1.0f, 10.0f) - 1.0f) * 10f).roundToInt() + 1).coerceIn(1, 91)

@Composable
private fun PrecisionRatingRuler(
    itemKey: Any,
    rating: Float?,
    onLiveRatingChange: (Float?) -> Unit,
    onRatingCommitted: (Float?) -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val tickSpacing = 12.dp
    val tickSpacingPx = remember(density) { with(density) { tickSpacing.toPx() } }
    val rulerScrollState = rememberScrollState()

    var lastSyncedItemKey by remember { mutableStateOf<Any?>(null) }
    var isProgrammaticSync by remember { mutableStateOf(true) }
    var lastObservedIndex by remember { mutableIntStateOf(ratingToRulerIndex(rating)) }

    var isEditingText by remember(itemKey) { mutableStateOf(false) }
    var ratingInputDraft by remember(itemKey) {
        mutableStateOf(rating?.let { String.format(Locale.US, "%.1f", it) }.orEmpty())
    }

    LaunchedEffect(itemKey, rating, tickSpacingPx) {
        val targetIdx = ratingToRulerIndex(rating)
        val targetPx = (targetIdx * tickSpacingPx).roundToInt()
        if (!rulerScrollState.isScrollInProgress && kotlin.math.abs(rulerScrollState.value - targetPx) > 1) {
            isProgrammaticSync = true
            try {
                lastObservedIndex = targetIdx
                if (lastSyncedItemKey != itemKey) {
                    lastSyncedItemKey = itemKey
                    rulerScrollState.scrollTo(targetPx)
                } else {
                    rulerScrollState.animateScrollTo(targetPx)
                }
            } finally {
                isProgrammaticSync = false
            }
        } else {
            lastSyncedItemKey = itemKey
            lastObservedIndex = targetIdx
            isProgrammaticSync = false
        }
    }

    val liveIndex by remember(tickSpacingPx) {
        derivedStateOf { (rulerScrollState.value / tickSpacingPx).roundToInt().coerceIn(0, 91) }
    }

    LaunchedEffect(liveIndex) {
        if (!isProgrammaticSync && rulerScrollState.isScrollInProgress && liveIndex != lastObservedIndex) {
            lastObservedIndex = liveIndex
            val liveRating = rulerIndexToRating(liveIndex)
            onLiveRatingChange(liveRating)
            if (liveIndex == 0 || (liveIndex - 1) % 5 == 0) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    LaunchedEffect(rulerScrollState, tickSpacingPx) {
        var wasScrolling = false
        snapshotFlow { rulerScrollState.isScrollInProgress }.collect { scrolling ->
            if (wasScrolling && !scrolling && !isProgrammaticSync) {
                val snapIdx = (rulerScrollState.value / tickSpacingPx).roundToInt().coerceIn(0, 91)
                val snapPx = (snapIdx * tickSpacingPx).roundToInt()
                if (kotlin.math.abs(rulerScrollState.value - snapPx) > 1) {
                    isProgrammaticSync = true
                    try {
                        rulerScrollState.animateScrollTo(snapPx)
                    } finally {
                        isProgrammaticSync = false
                    }
                }
                val committed = rulerIndexToRating(snapIdx)
                lastObservedIndex = snapIdx
                onLiveRatingChange(committed)
                onRatingCommitted(committed)
            }
            wasScrolling = scrolling
        }
    }

    fun submitTypedRating() {
        val trimmed = ratingInputDraft.trim()
        val parsed = trimmed.toFloatOrNull()
        val resolved = when {
            trimmed.isEmpty() || parsed == null -> rating
            parsed <= 0f -> null
            else -> ((parsed.coerceIn(1.0f, 10.0f) * 10f).roundToInt()) / 10f
        }
        isEditingText = false
        focusManager.clearFocus()
        onRatingCommitted(resolved)
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    val preMeasuredLabels = remember(textMeasurer, labelStyle) {
        buildMap {
            put(0, textMeasurer.measure("—", style = labelStyle))
            for (whole in 1..10) {
                val idx = 1 + (whole - 1) * 10
                put(idx, textMeasurer.measure(whole.toString(), style = labelStyle))
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Your Rating",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Slide Unrated · 1.0 – 10.0 (tap score to type)",
                    fontSize = 11.sp,
                    color = Color.White
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isEditingText) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.42f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BasicTextField(
                            value = ratingInputDraft,
                            onValueChange = { input ->
                                if (input.length <= 4 && input.all { it.isDigit() || it == '.' }) {
                                    ratingInputDraft = input
                                }
                            },
                            textStyle = TextStyle(
                                color = AccentGold,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submitTypedRating() }),
                            cursorBrush = SolidColor(AccentGold),
                            singleLine = true,
                            modifier = Modifier.width(42.dp)
                        )
                        Text(
                            text = "Set",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentJade,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentJade.copy(alpha = 0.18f))
                                .clickable { submitTypedRating() }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.34f))
                            .clickable {
                                ratingInputDraft = rating?.let { String.format(Locale.US, "%.1f", it) }.orEmpty()
                                isEditingText = true
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (rating != null) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.1f", rating)} / 10",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGold
                            )
                        } else {
                            Text(
                                text = "Unrated",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                if (rating != null) {
                    Text(
                        text = "Clear",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.30f))
                            .clickable {
                                isEditingText = false
                                onRatingCommitted(null)
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.34f))
        ) {
            val rulerWidth = maxWidth
            // Layer 1: GPU-drawn visible ruler ticks (zero recompositions during 120Hz scroll)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scrollPx = rulerScrollState.value.toFloat()
                val centerX = size.width / 2f
                val startIdx = ((scrollPx - centerX) / tickSpacingPx).toInt().coerceAtLeast(0)
                val endIdx = ((scrollPx + centerX) / tickSpacingPx).toInt().coerceAtMost(91)

                val topPad = 12.dp.toPx()
                val majorTickH = 22.dp.toPx()
                val halfTickH = 15.dp.toPx()
                val minorTickH = 9.dp.toPx()
                val majorStroke = 2.dp.toPx()
                val halfStroke = 1.5.dp.toPx()
                val minorStroke = 1.1.dp.toPx()
                val labelTop = topPad + majorTickH + 5.dp.toPx()

                for (i in startIdx..endIdx) {
                    val x = centerX + i * tickSpacingPx - scrollPx
                    val isUnratedMark = i == 0
                    val isWhole = i >= 1 && (i - 1) % 10 == 0
                    val isHalf = i >= 1 && (i - 1) % 10 == 5
                    val isCentered = kotlin.math.abs(x - centerX) <= tickSpacingPx * 0.45f

                    val tickH = when {
                        isUnratedMark || isWhole -> majorTickH
                        isHalf -> halfTickH
                        else -> minorTickH
                    }
                    val strokeW = when {
                        isUnratedMark || isWhole -> majorStroke
                        isHalf -> halfStroke
                        else -> minorStroke
                    }
                    val tickColor = when {
                        isCentered && i >= 1 -> AccentGold
                        isUnratedMark || isWhole -> Color.White
                        isHalf -> Color.White.copy(alpha = 0.85f)
                        else -> Color.White.copy(alpha = 0.55f)
                    }

                    drawLine(
                        color = tickColor,
                        start = Offset(x, topPad + (majorTickH - tickH) * 0.5f),
                        end = Offset(x, topPad + (majorTickH + tickH) * 0.5f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )

                    preMeasuredLabels[i]?.let { measured ->
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x - measured.size.width / 2f,
                                labelTop
                            )
                        )
                    }
                }

                // Fixed Center Indicator Needle
                val needleW = 3.dp.toPx()
                val needleColor = if (rating != null) AccentGold else Color.White
                drawRoundRect(
                    color = needleColor,
                    topLeft = Offset(centerX - needleW / 2f, 4.dp.toPx()),
                    size = Size(needleW, 34.dp.toPx()),
                    cornerRadius = CornerRadius(needleW / 2f, needleW / 2f)
                )
            }

            // Layer 2: Soft Edge Fade
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.0f to Color.Black.copy(alpha = 0.45f),
                            0.10f to Color.Transparent,
                            0.90f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.45f)
                        )
                    )
            )

            // Layer 3: Native Touch Scroll Layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rulerScrollState)
            ) {
                Spacer(modifier = Modifier.width(rulerWidth + tickSpacing * 91))
            }
        }
    }
}

@Composable
private fun SheetActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isActive) activeColor.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.30f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) activeColor else Color.White,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) activeColor else Color.White
        )
    }
}

private fun formatVolumeDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        val parsed = LocalDate.parse(raw.trim().take(10))
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(parsed)
    } catch (_: Exception) {
        raw.trim().take(10)
    }
}
