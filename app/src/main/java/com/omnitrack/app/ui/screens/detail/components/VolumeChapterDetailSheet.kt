package com.omnitrack.app.ui.screens.detail.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    var hasEditedRating by remember(ep.seasonNumber, ep.episodeNumber) {
        mutableStateOf(false)
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
            if (!hasEditedRating && personalRating == null && currentMeta.userRating != null) {
                personalRating = currentMeta.userRating
            }
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

                // 4. Exact Matching Clean Organic Rating Control (Unrated, 1.0 – 10.0 in 0.1 steps)
                VolumeChapterRatingSection(
                    itemKey = ep.seasonNumber to ep.episodeNumber,
                    rating = personalRating,
                    onLiveRatingChange = { live ->
                        hasEditedRating = true
                        personalRating = live
                    },
                    onRatingChanged = { committed ->
                        hasEditedRating = true
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

@Composable
private fun VolumeChapterRatingSection(
    itemKey: Any,
    rating: Float?,
    onLiveRatingChange: (Float?) -> Unit,
    onRatingChanged: (Float?) -> Unit
) {
    // 0.9f = Unrated (< 0.95f), 1.0f..10.0f = 0.1 steps (uniform 90 discrete steps -> zero dead zone)
    var sliderValue by remember(itemKey) {
        mutableFloatStateOf(rating?.coerceIn(1.0f, 10.0f) ?: 0.9f)
    }
    var textInput by remember(itemKey) {
        mutableStateOf(if (rating != null && rating >= 1.0f) String.format(Locale.US, "%.1f", rating) else "")
    }
    var isDraggingSlider by remember(itemKey) { mutableStateOf(false) }

    LaunchedEffect(itemKey, rating) {
        if (!isDraggingSlider) {
            val targetVal = rating?.coerceIn(1.0f, 10.0f) ?: 0.9f
            if (kotlin.math.abs(targetVal - sliderValue) > 0.04f) {
                sliderValue = targetVal
                textInput = if (rating != null && rating >= 1.0f) String.format(Locale.US, "%.1f", rating) else ""
            }
        }
    }

    val isUnrated = sliderValue < 0.95f

    fun updateLocalOnly(newScore: Float) {
        if (newScore < 0.95f) {
            sliderValue = 0.9f
            textInput = ""
            onLiveRatingChange(null)
        } else {
            val clamped = ((newScore.coerceIn(1.0f, 10.0f) * 10f).roundToInt()) / 10f
            sliderValue = clamped
            textInput = String.format(Locale.US, "%.1f", clamped)
            onLiveRatingChange(clamped)
        }
    }

    fun commitScore(newScore: Float) {
        if (newScore < 0.95f) {
            sliderValue = 0.9f
            textInput = ""
            onLiveRatingChange(null)
            onRatingChanged(null)
        } else {
            val clamped = ((newScore.coerceIn(1.0f, 10.0f) * 10f).roundToInt()) / 10f
            sliderValue = clamped
            textInput = String.format(Locale.US, "%.1f", clamped)
            onLiveRatingChange(clamped)
            onRatingChanged(clamped)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    text = if (isUnrated) "Unrated · Slide Unrated or 1.0 to 10.0" else "Personal rating (${String.format(Locale.US, "%.1f", sliderValue)} / 10.0)",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.90f)
                )
            }

            if (!isUnrated) {
                Text(
                    text = "Clear",
                    color = Color(0xFFEF4444),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { commitScore(0.9f) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // 1. Unboxed Hero Score (No outlines, no box: open space typography flanked by circular steppers)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RepeatingHoldButton(
                onStep = {
                    if (!isUnrated) {
                        if (sliderValue <= 1.05f) {
                            commitScore(0.9f)
                        } else {
                            commitScore(sliderValue - 0.1f)
                        }
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Minus 0.1", tint = Color.White)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (isUnrated) Color.White.copy(alpha = 0.45f) else AccentGold,
                    modifier = Modifier.size(26.dp)
                )

                if (isUnrated) {
                    Text(
                        text = "Unrated",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { commitScore(7.0f) }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                } else {
                    BasicTextField(
                        value = textInput,
                        onValueChange = { input ->
                            textInput = input
                            val parsed = input.toFloatOrNull()
                            if (parsed != null) {
                                if (parsed < 0.95f) {
                                    sliderValue = 0.9f
                                    onLiveRatingChange(null)
                                    onRatingChanged(null)
                                } else {
                                    val clamped = ((parsed.coerceIn(1.0f, 10.0f) * 10f).roundToInt()) / 10f
                                    sliderValue = clamped
                                    onLiveRatingChange(clamped)
                                    onRatingChanged(clamped)
                                }
                            }
                        },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        cursorBrush = SolidColor(AccentGold),
                        singleLine = true,
                        modifier = Modifier.width(100.dp)
                    )

                    Text(
                        text = "/ 10",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            RepeatingHoldButton(
                onStep = {
                    if (isUnrated) {
                        commitScore(1.0f)
                    } else {
                        commitScore(sliderValue + 0.1f)
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentGold.copy(alpha = 0.20f))
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Plus 0.1", tint = AccentGold)
            }
        }

        // 2. Smooth Continuous Slider (0.9 = Unrated, 1.0 to 10.0 in uniform 0.1 steps)
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = sliderValue,
                onValueChange = {
                    isDraggingSlider = true
                    updateLocalOnly(it)
                },
                onValueChangeFinished = {
                    isDraggingSlider = false
                    commitScore(sliderValue)
                },
                valueRange = 0.9f..10.0f,
                steps = 90,
                colors = SliderDefaults.colors(
                    thumbColor = if (isUnrated) Color.White.copy(alpha = 0.70f) else Color.White,
                    activeTrackColor = if (isUnrated) Color.White.copy(alpha = 0.18f) else AccentGold,
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                ),
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            // Borderless Scale Labels beneath the slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Unrated",
                    fontSize = 11.sp,
                    fontWeight = if (isUnrated) FontWeight.Bold else FontWeight.Medium,
                    color = if (isUnrated) AccentGold else Color.White.copy(alpha = 0.90f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { commitScore(0.9f) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                Text(
                    text = "1.0",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { commitScore(1.0f) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                Text(
                    text = "5.0",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { commitScore(5.0f) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                Text(
                    text = "10.0",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { commitScore(10.0f) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // 3. Clean 6 Borderless Delta Stepper Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val deltas = listOf(-1.0f, -0.5f, -0.1f, 0.1f, 0.5f, 1.0f)
            deltas.forEach { delta ->
                val isPos = delta > 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isPos) AccentGold.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.08f))
                        .clickable {
                            if (isUnrated) {
                                if (isPos) commitScore(1.0f + delta)
                            } else {
                                val next = sliderValue + delta
                                if (next < 0.95f) commitScore(0.9f) else commitScore(next)
                            }
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPos) "+${String.format(Locale.US, "%.1f", delta)}" else String.format(Locale.US, "%.1f", delta),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPos) AccentGold else Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }
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
