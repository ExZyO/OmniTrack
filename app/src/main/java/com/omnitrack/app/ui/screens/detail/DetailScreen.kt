package com.omnitrack.app.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.BannerSizing
import com.omnitrack.app.data.preferences.DetailsBackdropStyle
import com.omnitrack.app.data.preferences.DetailsPagePresentation
import com.omnitrack.app.data.preferences.FallbackBackgroundStyle
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.ui.components.ArtworkBackdrop
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.omnitrack.app.ui.components.ArtworkPickerBottomSheet
import com.omnitrack.app.ui.components.BackgroundMode
import com.omnitrack.app.ui.components.ChapterStepper
import com.omnitrack.app.ui.components.CoverGlowBox
import com.omnitrack.app.ui.components.GameProgressCard
import com.omnitrack.app.ui.components.featherArtwork
import com.omnitrack.app.ui.components.prefetchArtworkBackdrop
import com.omnitrack.app.ui.components.rememberArtworkBackdrop
import com.omnitrack.app.ui.components.rememberCoverDominantColor
import com.omnitrack.app.ui.screens.detail.components.CastMember
import com.omnitrack.app.ui.screens.detail.components.ChimanewProgressBottomSheet
import com.omnitrack.app.ui.screens.detail.components.ChimanewStatusBottomSheet
import com.omnitrack.app.ui.screens.detail.components.DetailCastSection
import com.omnitrack.app.ui.screens.detail.components.DetailCollectionSection
import com.omnitrack.app.ui.screens.detail.components.DetailGeneralInfoSection
import com.omnitrack.app.ui.screens.detail.components.DetailExternalLinksSection
import com.omnitrack.app.ui.screens.detail.components.DetailGenresTagsSection
import com.omnitrack.app.ui.screens.detail.components.DetailNamesSynonymsSection
import com.omnitrack.app.ui.screens.detail.components.DetailNetworksSection
import com.omnitrack.app.ui.screens.detail.components.DetailSeriesDialogs
import com.omnitrack.app.ui.screens.detail.components.DetailShowDetailsSection
import com.omnitrack.app.ui.screens.detail.components.DetailTrailersSection
import com.omnitrack.app.ui.screens.detail.components.detailSeriesItems
import com.omnitrack.app.ui.screens.detail.components.rememberDetailSeriesState
import com.omnitrack.app.ui.screens.detail.components.MultiTrackerSheet
import com.omnitrack.app.ui.screens.detail.components.NetworkProductionCompany
import com.omnitrack.app.ui.screens.detail.components.NuvioRatingsRow
import com.omnitrack.app.ui.screens.detail.components.RecommendationsSection
import com.omnitrack.app.ui.screens.detail.components.ReviewsSection
import com.omnitrack.app.ui.screens.detail.components.UnifiedDiscussionSection
import com.omnitrack.app.ui.screens.detail.components.SeasonEpisodesSection
import com.omnitrack.app.ui.screens.detail.components.ScoreRatingSheet
import com.omnitrack.app.ui.screens.detail.components.statusColor
import com.omnitrack.app.data.model.AnimeCharacterItem
import com.omnitrack.app.data.model.AnimeStaffItem
import com.omnitrack.app.data.preferences.DetailSectionKey
import com.omnitrack.app.ui.screens.detail.components.AnimeCharacterSheet
import com.omnitrack.app.ui.screens.detail.components.AnimeStaffSheet
import com.omnitrack.app.ui.screens.detail.components.UserProfileSheet
import com.omnitrack.app.ui.screens.detail.components.DetailAiringStatusLine
import com.omnitrack.app.ui.screens.detail.components.DetailAnimeCastSection
import com.omnitrack.app.ui.screens.detail.components.DetailAnimeStaffSection
import com.omnitrack.app.ui.screens.detail.components.DetailFranchiseSection
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.launch

sealed class DetailSheetTarget {
    data class UserProfile(val username: String?, val userId: Int?) : DetailSheetTarget()
    data class AnimeCharacter(val character: AnimeCharacterItem) : DetailSheetTarget()
    data class AnimeStaff(val staff: AnimeStaffItem) : DetailSheetTarget()
    data class Person(val person: CastMember) : DetailSheetTarget()
    data class Company(val company: NetworkProductionCompany) : DetailSheetTarget()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefManager = remember { PreferenceManager(context) }
    val presentationSettings by prefManager.presentationSettings.collectAsState(initial = PresentationSettings())
    val episodeLayoutMode by prefManager.episodeLayoutMode.collectAsState(initial = com.omnitrack.app.data.preferences.EpisodeLayoutMode.HORIZONTAL)
    val episodeLayoutScale by prefManager.episodeLayoutScale.collectAsState(initial = 1.0f)
    val seasonViewMode by prefManager.seasonViewMode.collectAsState(initial = "Posters")

    val uiState by viewModel.uiState.collectAsState()
    val isCommentsLoading by viewModel.isCommentsLoading.collectAsState()
    val item = uiState.item
    val effectiveBanner = item?.customBannerPath ?: item?.bannerUrl
    val isCinematicBlur = presentationSettings.fallbackBackgroundStyle == FallbackBackgroundStyle.CINEMATIC_BLUR
    val backdropBitmap = rememberArtworkBackdrop(
        imageUrl = effectiveBanner ?: item?.coverUrl,
        enabled = isCinematicBlur
    )

    val coroutineScope = rememberCoroutineScope()
    var showArtworkPicker by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    var showProgressEditSheet by remember { mutableStateOf(false) }
    var showStatusPickerSheet by remember { mutableStateOf(false) }
    var showScoreRatingSheet by remember { mutableStateOf(false) }
    var showMultiTrackerSheet by remember { mutableStateOf(false) }
    val sheetBackStack = remember { mutableStateListOf<DetailSheetTarget>() }

    // 1:1 Nuvio Parity: Defer heavy off-screen sections (Cast, Trailers, Reviews, Recommendations)
    // by 200ms until navigation entrance transition finishes, eliminating frame drops & stutter.
    var deferredSectionsAllowed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        deferredSectionsAllowed = true
    }

    val lazyListState = rememberLazyListState()
    val isAtTopOfList = {
        lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
    }

    BackHandler(enabled = sheetBackStack.isNotEmpty() || uiState.isPreviewMode || viewModel.isPreviewActive()) {
        if (sheetBackStack.isNotEmpty()) {
            sheetBackStack.removeAt(sheetBackStack.lastIndex)
            val restoredOffset = viewModel.popScrollOffset()
            if (restoredOffset != null) {
                coroutineScope.launch { lazyListState.scrollToItem(restoredOffset.first, restoredOffset.second) }
            }
        } else if (uiState.isPreviewMode || viewModel.isPreviewActive()) {
            val handled = viewModel.closePreview()
            if (!handled) {
                onBack()
            } else {
                val restoredOffset = viewModel.popScrollOffset()
                if (restoredOffset != null) {
                    coroutineScope.launch { lazyListState.scrollToItem(restoredOffset.first, restoredOffset.second) }
                }
            }
        } else {
            onBack()
        }
    }

    val currentSheet = sheetBackStack.lastOrNull()
    when (currentSheet) {
        is DetailSheetTarget.UserProfile -> {
            UserProfileSheet(
                username = currentSheet.username,
                anilistUserId = currentSheet.userId,
                userAuthToken = uiState.anilistAuthToken,
                currentUsername = uiState.currentUsername,
                presentationSettings = presentationSettings,
                backdropBitmap = backdropBitmap,
                backdropTargetUrl = effectiveBanner,
                onOpenMediaPreview = { recItem ->
                    viewModel.saveScrollOffset(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
                    viewModel.openPreview(recItem)
                    coroutineScope.launch { lazyListState.scrollToItem(0) }
                },
                onOpenCharacter = { charItem ->
                    sheetBackStack.add(DetailSheetTarget.AnimeCharacter(charItem))
                },
                onOpenStaff = { staffItem ->
                    sheetBackStack.add(DetailSheetTarget.AnimeStaff(staffItem))
                },
                onDismiss = {
                    sheetBackStack.removeLastOrNull()
                }
            )
        }
        is DetailSheetTarget.AnimeCharacter -> {
            AnimeCharacterSheet(
                character = currentSheet.character,
                presentationSettings = presentationSettings,
                backdropBitmap = backdropBitmap,
                backdropTargetUrl = effectiveBanner,
                onDismissRequest = { sheetBackStack.removeLastOrNull() },
                onOpenMediaPreview = { recItem ->
                    viewModel.saveScrollOffset(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
                    viewModel.openPreview(recItem)
                    coroutineScope.launch { lazyListState.scrollToItem(0) }
                }
            )
        }
        is DetailSheetTarget.AnimeStaff -> {
            AnimeStaffSheet(
                staff = currentSheet.staff,
                presentationSettings = presentationSettings,
                backdropBitmap = backdropBitmap,
                backdropTargetUrl = effectiveBanner,
                onDismissRequest = { sheetBackStack.removeLastOrNull() },
                onOpenMediaPreview = { recItem ->
                    viewModel.saveScrollOffset(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
                    viewModel.openPreview(recItem)
                    coroutineScope.launch { lazyListState.scrollToItem(0) }
                }
            )
        }
        is DetailSheetTarget.Person -> {
            if (currentSheet.person.tmdbPersonId != null) {
                val tmdbKey = "844dba0bfd8f3a4f3799f6130ef9e335"
                PersonDetailScreen(
                    personId = currentSheet.person.tmdbPersonId!!,
                    personName = currentSheet.person.name,
                    initialAvatarUrl = currentSheet.person.avatarUrl,
                    apiKey = tmdbKey,
                    presentationSettings = presentationSettings,
                    backdropBitmap = backdropBitmap,
                    backdropTargetUrl = effectiveBanner,
                    onBack = { sheetBackStack.removeLastOrNull() },
                    onOpenMediaPreview = { recItem ->
                        viewModel.saveScrollOffset(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
                        viewModel.openPreview(recItem)
                    }
                )
                return
            }
        }
        is DetailSheetTarget.Company -> {
            val tmdbKey = "844dba0bfd8f3a4f3799f6130ef9e335"
            TmdbEntityBrowseScreen(
                entityId = currentSheet.company.id,
                entityName = currentSheet.company.name,
                entityKind = currentSheet.company.kind,
                apiKey = tmdbKey,
                presentationSettings = presentationSettings,
                backdropBitmap = backdropBitmap,
                backdropTargetUrl = effectiveBanner,
                onBack = { sheetBackStack.removeLastOrNull() },
                onOpenMediaPreview = { recItem ->
                    viewModel.saveScrollOffset(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
                    viewModel.openPreview(recItem)
                    coroutineScope.launch { lazyListState.scrollToItem(0) }
                }
            )
            return
        }
        null -> {}
    }

    // Kinematic horizontal & vertical swipe physics (bilateral entry switching & Poweramp pull-down to dismiss)
    val animatableOffsetX = remember { Animatable(0f) }
    var dragOffsetY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val animatedDragOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "verticalDismiss"
    )

    var isPullDownArmed by remember { mutableStateOf(false) }
    var scrollConsumedInGesture by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): Offset {
                // If not at top of list, this gesture is scrolling content, so disarm pull-down
                if (!isAtTopOfList()) {
                    isPullDownArmed = false
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): Offset {
                if (consumed.y != 0f) {
                    scrollConsumedInGesture += kotlin.math.abs(consumed.y)
                }
                // Arm pull-down ONLY if zero scroll was consumed in this gesture AND list is at top
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput &&
                    scrollConsumedInGesture == 0f &&
                    isAtTopOfList() &&
                    available.y > 0
                ) {
                    isPullDownArmed = true
                }

                // Only accumulate dismiss drag if gesture strictly started at top with zero prior scroll
                if (isPullDownArmed && available.y > 0 && isAtTopOfList()) {
                    dragOffsetY += available.y * 0.5f
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                val shouldDismiss = isPullDownArmed && dragOffsetY > 160f
                isPullDownArmed = false
                dragOffsetY = 0f
                scrollConsumedInGesture = 0f
                if (shouldDismiss) {
                    onBack()
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    var frozenPrevPreview by remember { mutableStateOf<MediaItemEntity?>(null) }
    var frozenNextPreview by remember { mutableStateOf<MediaItemEntity?>(null) }
    var gestureBaseItemId by remember { mutableStateOf(item?.id) }

    // Pre-warm adjacent siblings' blurred backdrops, dominant colors, and images before swipe starts
    listOfNotNull(uiState.previousItem, uiState.nextItem).forEach { sibling ->
        val sibBanner = if (sibling.customBannerPath == "NONE" || sibling.artworkRole == "DISABLE_BANNER") null else (sibling.customBannerPath ?: sibling.bannerUrl)
        val sibCover = sibling.customCoverPath ?: if (sibling.artworkRole == "BANNER_AS_COVER") (sibling.bannerUrl ?: sibling.coverUrl) else sibling.coverUrl
        rememberArtworkBackdrop(imageUrl = sibBanner ?: sibCover, enabled = isCinematicBlur)
        rememberCoverDominantColor(LosslessCoverMapper.toLosslessUrl(sibCover))
        rememberCoverDominantColor(sibBanner)
    }

    LaunchedEffect(uiState.previousItem?.id, uiState.nextItem?.id, isCinematicBlur) {
        val loader = coil3.SingletonImageLoader.get(context)
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        listOfNotNull(uiState.previousItem, uiState.nextItem).forEach { sibling ->
            val sibBanner = if (sibling.customBannerPath == "NONE" || sibling.artworkRole == "DISABLE_BANNER") null else (sibling.customBannerPath ?: sibling.bannerUrl)
            val sibCover = sibling.customCoverPath ?: if (sibling.artworkRole == "BANNER_AS_COVER") (sibling.bannerUrl ?: sibling.coverUrl) else sibling.coverUrl
            val sibClearart = sibling.clearartUrl?.takeIf {
                it.isNotBlank() && (!it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE)
            } ?: com.omnitrack.app.data.enrichment.EnrichmentRepository.getCachedClearart(sibling)?.takeIf {
                !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
            }
            if (!sibBanner.isNullOrBlank()) {
                loader.enqueue(ImageRequest.Builder(context).data(sibBanner).crossfade(false).build())
            }
            if (sibCover.isNotBlank()) {
                val coverData: Any = sibling.customCoverPath?.let {
                    if (it.startsWith("http://") || it.startsWith("https://")) it else java.io.File(it)
                } ?: LosslessCoverMapper.toLosslessUrl(sibCover)
                loader.enqueue(ImageRequest.Builder(context).data(coverData).crossfade(false).build())
            }
            if (!sibClearart.isNullOrBlank()) {
                loader.enqueue(ImageRequest.Builder(context).data(sibClearart).crossfade(false).build())
            }
            if (isCinematicBlur) {
                prefetchArtworkBackdrop(context, sibBanner ?: sibCover)
            }
        }
    }

    LaunchedEffect(uiState.previousItem, uiState.nextItem, item?.id) {
        if (animatableOffsetX.value == 0f || item?.id != gestureBaseItemId) {
            frozenPrevPreview = uiState.previousItem
            frozenNextPreview = uiState.nextItem
        }
    }

    LaunchedEffect(item?.id) {
        val switchedEntry = gestureBaseItemId != null && item?.id != gestureBaseItemId
        gestureBaseItemId = item?.id
        animatableOffsetX.snapTo(0f)
        dragOffsetY = 0f
        if (switchedEntry && !isAtTopOfList()) {
            lazyListState.scrollToItem(0)
        }
        frozenPrevPreview = uiState.previousItem
        frozenNextPreview = uiState.nextItem
        if (item != null) {
            AppLogger.i("DetailScreen", "Viewing detail for: ${item.title} [id=${item.id}, type=${item.mediaType.displayName}]")
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val currentOffsetX = if (item?.id == gestureBaseItemId) animatableOffsetX.value else 0f
        val activePrevPreview = frozenPrevPreview ?: uiState.previousItem
        val activeNextPreview = frozenNextPreview ?: uiState.nextItem

        // 1. PREVIOUS SIBLING PREVIEW (Slides in from the left when user drags right)
        if (uiState.hasPreviousEntry && activePrevPreview != null && currentOffsetX > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = currentOffsetX - screenWidthPx
                        translationY = animatedDragOffsetY.coerceAtLeast(0f)
                    }
            ) {
                SiblingDetailPreview(
                    item = activePrevPreview,
                    presentationSettings = presentationSettings
                )
            }
        }

        // 2. NEXT SIBLING PREVIEW (Slides in from the right when user drags left)
        if (uiState.hasNextEntry && activeNextPreview != null && currentOffsetX < 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = currentOffsetX + screenWidthPx
                        translationY = animatedDragOffsetY.coerceAtLeast(0f)
                    }
            ) {
                SiblingDetailPreview(
                    item = activeNextPreview,
                    presentationSettings = presentationSettings
                )
            }
        }

        // 3. CURRENT ACTIVE ENTRY
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = currentOffsetX
                    translationY = animatedDragOffsetY.coerceAtLeast(0f)
                    val dismissScale = (1f - (animatedDragOffsetY.coerceAtLeast(0f) / 1000f * 0.12f)).coerceIn(0.88f, 1f)
                    val dismissAlpha = (1f - (animatedDragOffsetY.coerceAtLeast(0f) / 800f * 0.35f)).coerceIn(0.65f, 1f)
                    scaleX = dismissScale
                    scaleY = dismissScale
                    alpha = dismissAlpha
                }
                .nestedScroll(nestedScrollConnection)
                .pointerInput(item?.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val currentX = animatableOffsetX.value
                            val threshold = screenWidthPx * 0.18f
                            when {
                                // Swiping right: navigate to previous sibling entry
                                currentX > threshold && uiState.hasPreviousEntry -> {
                                    coroutineScope.launch {
                                        animatableOffsetX.animateTo(
                                            targetValue = screenWidthPx,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                        if (!isAtTopOfList()) {
                                            lazyListState.scrollToItem(0)
                                        }
                                        val navigated = viewModel.navigateToPrevious()
                                        if (!navigated) {
                                            animatableOffsetX.snapTo(0f)
                                        }
                                    }
                                }
                                // Swiping left: navigate to next sibling entry
                                currentX < -threshold && uiState.hasNextEntry -> {
                                    coroutineScope.launch {
                                        animatableOffsetX.animateTo(
                                            targetValue = -screenWidthPx,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                        if (!isAtTopOfList()) {
                                            lazyListState.scrollToItem(0)
                                        }
                                        val navigated = viewModel.navigateToNext()
                                        if (!navigated) {
                                            animatableOffsetX.snapTo(0f)
                                        }
                                    }
                                }
                                // Swiping right past threshold when at start of single-entry list: return to library
                                currentX > 180f && !uiState.hasPreviousEntry -> {
                                    coroutineScope.launch {
                                        animatableOffsetX.animateTo(
                                            targetValue = screenWidthPx,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                        onBack()
                                    }
                                }
                                else -> {
                                    coroutineScope.launch {
                                        animatableOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                animatableOffsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            coroutineScope.launch {
                                animatableOffsetX.snapTo(animatableOffsetX.value + dragAmount)
                            }
                            change.consume()
                        }
                    )
                }
        ) {
            if (uiState.isLoading && item == null) {
                Box(Modifier.fillMaxSize().background(AmoledBlack), contentAlignment = Alignment.Center) {
                    com.omnitrack.app.ui.components.NuvioLoadingIndicator(size = 44.dp)
                }
            } else if (item != null) {
            val customPosterPattern by prefManager.customPosterUrlPattern.collectAsState(initial = "")

            val customCoverUrl = remember(item, customPosterPattern) {
                if (customPosterPattern.isNotBlank()) {
                    com.omnitrack.app.data.poster.CustomPosterUrlResolver.resolveForMediaItem(item, customPosterPattern, isLandscape = false)
                } else null
            }
            var customCoverFailed by remember(customCoverUrl) { mutableStateOf(false) }

            val customBannerUrl = remember(item, customPosterPattern) {
                if (customPosterPattern.isNotBlank() && "{shape}" in customPosterPattern) {
                    com.omnitrack.app.data.poster.CustomPosterUrlResolver.resolveForMediaItem(item, customPosterPattern, isLandscape = true)
                } else null
            }
            var customBannerFailed by remember(customBannerUrl) { mutableStateOf(false) }

            // Dynamic Artwork Role Resolution (Manual user artwork selection strictly overrides templates)
            val effectiveCover = remember(item.customCoverPath, item.artworkRole, item.coverUrl, item.bannerUrl, customCoverUrl, customCoverFailed) {
                item.customCoverPath
                    ?: when (item.artworkRole) {
                        "BANNER_AS_COVER" -> item.bannerUrl ?: item.coverUrl
                        else -> if (item.coverUrl.isNotBlank()) item.coverUrl else (if (!customCoverFailed) customCoverUrl ?: "" else "")
                    }
            }
            val effectiveBanner = remember(item.customBannerPath, item.artworkRole, item.coverUrl, item.bannerUrl, customBannerUrl, customBannerFailed) {
                when {
                    item.customBannerPath == "NONE" || item.artworkRole == "DISABLE_BANNER" -> null
                    item.customBannerPath != null -> item.customBannerPath
                    !customBannerFailed && customBannerUrl != null -> customBannerUrl
                    item.artworkRole == "COVER_AS_BANNER" -> item.coverUrl
                    item.artworkRole == "BANNER_AS_COVER" -> item.bannerUrl
                    else -> item.bannerUrl
                }
            }

            val coverCache = remember { (context.applicationContext as? com.omnitrack.app.OmniTrackApplication)?.coverCache }
            val coverImageModel = remember(effectiveCover, item.customCoverPath) {
                item.customCoverPath?.let { if (it.startsWith("http://") || it.startsWith("https://")) it else java.io.File(it) }
                    ?: LosslessCoverMapper.toLosslessUrl(effectiveCover)
            }

            val losslessCover = remember(effectiveCover) { LosslessCoverMapper.toLosslessUrl(effectiveCover) }
            val dominantAccent = rememberCoverDominantColor(losslessCover)
            val backdropTargetUrl = effectiveBanner

            val isCinematicBlur = presentationSettings.fallbackBackgroundStyle == FallbackBackgroundStyle.CINEMATIC_BLUR
            val backdropBitmap = rememberArtworkBackdrop(
                imageUrl = backdropTargetUrl ?: item.coverUrl,
                enabled = isCinematicBlur
            )

            val currentClearart = item.clearartUrl?.takeIf { it.isNotBlank() }
                ?: com.omnitrack.app.data.enrichment.EnrichmentRepository.getCachedClearart(item)
            LaunchedEffect(currentClearart) {
                if (!currentClearart.isNullOrBlank()) {
                    val req = ImageRequest.Builder(context).data(currentClearart).crossfade(false).build()
                    coil3.SingletonImageLoader.get(context).enqueue(req)
                }
            }

            Box(Modifier.fillMaxSize().detailsContentReveal(true)) {
            // Background presentation layer
            when (presentationSettings.fallbackBackgroundStyle) {
                FallbackBackgroundStyle.CINEMATIC_BLUR -> {
                    ArtworkBackdrop(
                        image = backdropBitmap,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                FallbackBackgroundStyle.DOMINANT_COLOR -> {
                    CoverGlowBox(
                        imageUrl = backdropTargetUrl,
                        modifier = Modifier.fillMaxSize()
                    ) {}
                }
                FallbackBackgroundStyle.SOLID_BLACK -> {
                    // Pure AMOLED black canvas
                }
            }

            val seriesUiState = rememberDetailSeriesState(
                itemKey = item.id.takeIf { it > 0 } ?: item.title,
                initialSeasonViewMode = seasonViewMode,
                layoutMode = episodeLayoutMode
            )
            val isReadingType = item.mediaType == MediaType.MANGA || item.mediaType == MediaType.NOVEL
            val isLightNovelVolumeMode = item.mediaType == MediaType.NOVEL && uiState.isLightNovelMode
            val fallbackSeriesCover = effectiveCover.takeIf { it.isNotBlank() } ?: item.coverUrl

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "detail_top_header", contentType = "top_header") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // PRESENTATION MODE 1 or 2
                        if (presentationSettings.detailsPagePresentation == DetailsPagePresentation.HERO) {
                            DetailHeroHeaderSection(
                                item = item,
                                backdropTargetUrl = backdropTargetUrl,
                                englishTitle = uiState.englishTitle,
                                romajiTitle = uiState.romajiTitle,
                                heroClearLogoUrl = uiState.heroClearLogoUrl,
                                novelDetailMeta = uiState.novelDetailMeta,
                                onToggleTitle = { viewModel.togglePreferredTitle() },
                                onOptionsClick = { showOptionsMenu = true },
                                onEditProgress = { showProgressEditSheet = true },
                                onEditStatus = { showStatusPickerSheet = true },
                                onEditScore = { showScoreRatingSheet = true },
                                onOpenTrackers = { showMultiTrackerSheet = true }
                            )
                        } else {
                            DetailClassicHeaderSection(
                                item = item,
                                coverImageModel = coverImageModel,
                                backdropTargetUrl = backdropTargetUrl,
                                englishTitle = uiState.englishTitle,
                                romajiTitle = uiState.romajiTitle,
                                heroClearLogoUrl = uiState.heroClearLogoUrl,
                                novelDetailMeta = uiState.novelDetailMeta,
                                onToggleTitle = { viewModel.togglePreferredTitle() },
                                onCoverClick = { showArtworkPicker = true },
                                onCoverLoaded = { url ->
                                    if (coverCache != null) {
                                        val diskCache = coil3.SingletonImageLoader.get(context).diskCache
                                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            coverCache.promoteFromDiskCache(url, diskCache)
                                        }
                                    }
                                },
                                onOptionsClick = { showOptionsMenu = true },
                                onEditProgress = { showProgressEditSheet = true },
                                onEditStatus = { showStatusPickerSheet = true },
                                onEditScore = { showScoreRatingSheet = true },
                                onOpenTrackers = { showMultiTrackerSheet = true }
                            )
                        }

                        // 1. Actions / Progress Section (Only rendered for media types with top progress cards)
                        val hasProgressControls = item.mediaType in listOf(MediaType.GAME, MediaType.VISUAL_NOVEL, MediaType.MOVIE)
                        val hasBackdrop = backdropTargetUrl != null
                        if (hasProgressControls) {
                            Spacer(modifier = Modifier.height(if (hasBackdrop) (-10).dp else 4.dp))
                            DetailProgressSection(
                                item = item,
                                onUpdateProgress = { viewModel.updateProgress(it) },
                                onUpdatePlaytime = { viewModel.updatePlaytime(it) },
                                onTogglePlatform = { viewModel.togglePlatform(it) },
                                onUpdateStatus = { viewModel.updateStatus(it) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        } else {
                            Spacer(modifier = Modifier.height(if (hasBackdrop) (-36).dp else (-8).dp))
                        }

                        // 2. Overview (Seamless Synopsis with bidirectional overflow-aware toggle)
                        if (!item.synopsis.isNullOrBlank()) {
                            DetailSynopsisSection(
                                synopsis = item.synopsis,
                                dominantAccent = dominantAccent
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }

                // 3. Dynamic Reorderable Sections (configured in Settings > Layout > Details Page)
                val enabledSections = uiState.sectionOrder.filter { it.isEnabled }
                enabledSections.forEach { sectionItem ->
                    when (sectionItem.key) {
                        DetailSectionKey.AIRING_STATUS -> {
                            val airing = uiState.animeAiring
                            if (airing != null) {
                                item(key = "section_airing_status", contentType = "airing_status") {
                                    DetailAiringStatusLine(
                                        schedule = airing,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                }
                            }
                        }
                        DetailSectionKey.EPISODES -> {
                            if (item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME || item.mediaType == MediaType.MANGA || item.mediaType == MediaType.NOVEL) {
                                detailSeriesItems(
                                    seriesState = seriesUiState,
                                    seasons = uiState.realSeasons,
                                    episodes = uiState.realEpisodes,
                                    episodesBySeason = uiState.episodesBySeason,
                                    watchedEpisodes = uiState.watchedEpisodes,
                                    selectedSeasonNumber = uiState.selectedSeasonNumber,
                                    fallbackBackdropUrl = effectiveBanner,
                                    fallbackCoverUrl = fallbackSeriesCover,
                                    layoutScale = episodeLayoutScale,
                                    onSeasonViewModeChanged = { next ->
                                        coroutineScope.launch { prefManager.updateSeasonViewMode(next) }
                                    },
                                    onLayoutModeChanged = { next ->
                                        coroutineScope.launch { prefManager.updateEpisodeLayoutMode(next) }
                                    },
                                    onSelectSeason = { viewModel.selectSeason(it) },
                                    onToggleEpisodeWatched = { viewModel.toggleEpisodeWatched(it) },
                                    onToggleSeasonWatched = { viewModel.toggleSeasonWatched(it) },
                                    isFillerSkipEnabled = uiState.isFillerSkipEnabled,
                                    onToggleSkipFillers = { viewModel.toggleSkipFillers() },
                                    isMangaMode = isReadingType,
                                    isMangaSeasonGrouped = uiState.isMangaSeasonGrouped,
                                    isLightNovelVolumeMode = isLightNovelVolumeMode,
                                    currentTotalProgress = item.currentProgress.toInt(),
                                    onStepProgress = if (isReadingType) {
                                        { delta -> viewModel.stepChapterProgress(delta) }
                                    } else null,
                                    onJumpToProgress = if (isReadingType) {
                                        { target -> viewModel.jumpToChapterOrVolume(target) }
                                    } else null
                                )
                            }
                        }
                        DetailSectionKey.FRANCHISE -> {
                            if (deferredSectionsAllowed && uiState.animeRelations.isNotEmpty()) {
                                item(key = "section_franchise", contentType = "franchise") {
                                    Column {
                                        DetailFranchiseSection(
                                            relations = uiState.animeRelations,
                                            onRelationClick = { relation ->
                                                viewModel.saveScrollOffset(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
                                                viewModel.openRelationPreview(relation)
                                                coroutineScope.launch { lazyListState.scrollToItem(0) }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.CAST -> {
                            if (deferredSectionsAllowed) {
                                val isAnimeLike = item.mediaType == MediaType.ANIME || item.mediaType == MediaType.MANGA || item.mediaType == MediaType.NOVEL
                                val hasAnimeCast = isAnimeLike && (uiState.animeCharacters.isNotEmpty() || uiState.animeStaff.isNotEmpty())
                                val hasStdCast = !isAnimeLike && uiState.cast.isNotEmpty()
                                if (hasAnimeCast || hasStdCast) {
                                    item(key = "section_cast", contentType = "cast") {
                                        Column {
                                            if (isAnimeLike) {
                                                if (uiState.animeCharacters.isNotEmpty()) {
                                                    DetailAnimeCastSection(
                                                        characters = uiState.animeCharacters,
                                                        isMangaOrNovel = isReadingType,
                                                        onCharacterClick = { character ->
                                                            sheetBackStack.add(DetailSheetTarget.AnimeCharacter(character))
                                                        },
                                                        onVoiceActorClick = { char ->
                                                            if (char.voiceActorId != null) {
                                                                sheetBackStack.add(
                                                                    DetailSheetTarget.AnimeStaff(
                                                                        AnimeStaffItem(
                                                                            staffId = char.voiceActorId,
                                                                            name = char.voiceActorName ?: "Voice Actor",
                                                                            role = "Voice Actor",
                                                                            imageUrl = char.voiceActorImageUrl
                                                                        )
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    )
                                                }
                                                if (uiState.animeStaff.isNotEmpty()) {
                                                    if (uiState.animeCharacters.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(16.dp))
                                                    }
                                                    DetailAnimeStaffSection(
                                                        staff = uiState.animeStaff,
                                                        isMangaOrNovel = isReadingType,
                                                        onStaffClick = { staff ->
                                                            sheetBackStack.add(DetailSheetTarget.AnimeStaff(staff))
                                                        }
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(20.dp))
                                            } else {
                                                DetailCastSection(
                                                    cast = uiState.cast,
                                                    onCastMemberClick = { member ->
                                                        sheetBackStack.add(DetailSheetTarget.Person(member))
                                                    }
                                                )
                                                Spacer(modifier = Modifier.height(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        DetailSectionKey.NETWORKS -> {
                            if (deferredSectionsAllowed) {
                                item(key = "section_networks", contentType = "networks") {
                                    Column {
                                        DetailNetworksSection(
                                            item = item,
                                            companies = uiState.companies,
                                            onCompanyClick = { company ->
                                                sheetBackStack.add(DetailSheetTarget.Company(company))
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.GENRES_TAGS -> {
                            if (deferredSectionsAllowed) {
                                item(key = "section_genres_tags", contentType = "genres_tags") {
                                    Column {
                                        DetailGenresTagsSection(
                                            item = item,
                                            tags = uiState.tags,
                                            onGenreOrTagClick = { name, isTag ->
                                                sheetBackStack.add(
                                                    DetailSheetTarget.Company(
                                                        NetworkProductionCompany(
                                                            id = null,
                                                            name = name,
                                                            logoUrl = null,
                                                            kind = if (isTag) "anime_tag" else "anime_genre"
                                                        )
                                                    )
                                                )
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.NAMES_SYNONYMS -> {
                            if (deferredSectionsAllowed) {
                                item(key = "section_names_synonyms", contentType = "names_synonyms") {
                                    Column {
                                        DetailNamesSynonymsSection(
                                            item = item,
                                            englishTitle = uiState.englishTitle,
                                            romajiTitle = uiState.romajiTitle,
                                            nativeTitle = item.nativeTitle,
                                            synonyms = uiState.synonyms
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.DETAILS_INFO -> {
                            if (deferredSectionsAllowed) {
                                item(key = "section_details_info", contentType = "details_info") {
                                    Column {
                                        DetailGeneralInfoSection(
                                            item = item,
                                            popularity = uiState.popularity,
                                            favourites = uiState.favourites,
                                            durationMinutes = uiState.durationMinutes,
                                            format = uiState.format,
                                            season = uiState.season,
                                            seasonYear = uiState.seasonYear,
                                            source = uiState.source,
                                            countryOfOrigin = uiState.countryOfOrigin,
                                            startDate = uiState.startDate,
                                            endDate = uiState.endDate,
                                            nextAiringEpochSeconds = uiState.animeAiring?.airingAtEpochSeconds
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.EXTERNAL_LINKS -> {
                            if (deferredSectionsAllowed && uiState.externalLinks.isNotEmpty()) {
                                item(key = "section_external_links", contentType = "external_links") {
                                    Column {
                                        DetailExternalLinksSection(
                                            links = uiState.externalLinks
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.TRAILERS -> {
                            if (deferredSectionsAllowed && uiState.trailers.isNotEmpty()) {
                                item(key = "section_trailers", contentType = "trailers") {
                                    Column {
                                        DetailTrailersSection(
                                            trailers = uiState.trailers,
                                            contentTitle = item.title
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.REVIEWS -> {
                            if (deferredSectionsAllowed) {
                                item(key = "section_reviews", contentType = "reviews") {
                                    Column {
                                        UnifiedDiscussionSection(
                                            mediaType = item.mediaType,
                                            mediaTitle = item.title,
                                            reviews = uiState.reviews,
                                            episodeOrChapterCount = item.totalProgress?.toInt() ?: uiState.realEpisodes.size.takeIf { it > 0 },
                                            comments = uiState.discussionComments,
                                            isLoading = isCommentsLoading,
                                            seasons = uiState.realSeasons,
                                            selectedSeasonNumber = uiState.selectedSeasonNumber,
                                            currentUsername = uiState.currentUsername,
                                            hasAniList = ((item.anilistId != null && item.anilistId > 0) || (uiState.item?.anilistId != null && (uiState.item?.anilistId ?: 0) > 0)),
                                            onSeasonSelect = { viewModel.selectSeason(it) },
                                            onTagSelect = { tag -> viewModel.selectDiscussionTag(tag) },
                                            onPostComment = { content, tag, parentId ->
                                                viewModel.postDiscussionComment(content, tag, parentId)
                                            },
                                            onEditComment = { commentId, newContent ->
                                                viewModel.editDiscussionComment(commentId, newContent)
                                            },
                                            onDeleteComment = { commentId ->
                                                viewModel.deleteDiscussionComment(commentId)
                                            },
                                            onVote = { commentId, voteType ->
                                                viewModel.voteDiscussionComment(commentId, voteType)
                                            },
                                            onUserClick = { username, anilistUserId ->
                                                sheetBackStack.add(DetailSheetTarget.UserProfile(username, anilistUserId))
                                            },
                                            onSortChange = { sort ->
                                                viewModel.changeDiscussionSort(sort)
                                            },
                                            onLoadReplies = { commentId ->
                                                viewModel.loadCommentReplies(commentId)
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.COLLECTION -> {
                            if (deferredSectionsAllowed && item.mediaType == MediaType.MOVIE && uiState.collectionItems.isNotEmpty()) {
                                item(key = "section_collection", contentType = "collection") {
                                    Column {
                                        DetailCollectionSection(
                                            collectionName = uiState.collectionName ?: "Collection",
                                            items = uiState.collectionItems,
                                            onSelectItem = {
                                                viewModel.saveScrollOffset(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
                                                viewModel.openPreview(it)
                                                coroutineScope.launch { lazyListState.scrollToItem(0) }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.RECOMMENDATIONS -> {
                            if (deferredSectionsAllowed && uiState.recommendations.isNotEmpty()) {
                                item(key = "section_recommendations", contentType = "recommendations") {
                                    Column {
                                        RecommendationsSection(
                                            recommendations = uiState.recommendations,
                                            onSelectRecommendation = {
                                                viewModel.saveScrollOffset(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
                                                viewModel.openPreview(it)
                                                coroutineScope.launch { lazyListState.scrollToItem(0) }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // PREVIEW MODE HERO ACTION: + Add to Library
                if (uiState.isPreviewMode) {
                    item(key = "preview_add_button", contentType = "preview_add") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.addToLibrary(item) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentJade)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Add to Library",
                                    color = AmoledBlack,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                item(key = "bottom_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }

            // Top Status Bar Gradient Scrim (so white back arrow never vanishes on bright banners)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.50f), Color.Transparent)
                        )
                    )
            )

            // 1:1 Nuvio Parity: Top Floating Bar with Back Arrow, Centered Logo/Title, and Watched Checkmark
            val headerAlpha by remember {
                derivedStateOf {
                    if (lazyListState.firstVisibleItemIndex > 0) 1f
                    else ((lazyListState.firstVisibleItemScrollOffset - 40f) / 160f).coerceIn(0f, 1f)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color(0xFF0D0D0D).copy(alpha = headerAlpha * 0.95f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (sheetBackStack.isNotEmpty()) {
                            sheetBackStack.removeAt(sheetBackStack.lastIndex)
                            val restoredOffset = viewModel.popScrollOffset()
                            if (restoredOffset != null) {
                                coroutineScope.launch { lazyListState.scrollToItem(restoredOffset.first, restoredOffset.second) }
                            }
                        } else if (uiState.isPreviewMode || viewModel.isPreviewActive()) {
                            val handled = viewModel.closePreview()
                            if (!handled) {
                                onBack()
                            } else {
                                val restoredOffset = viewModel.popScrollOffset()
                                if (restoredOffset != null) {
                                    coroutineScope.launch { lazyListState.scrollToItem(restoredOffset.first, restoredOffset.second) }
                                }
                            }
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .shadow(elevation = 8.dp, shape = CircleShape)
                    )
                }

                // Center Title / Clearart Logo (smooth fade-in as user scrolls down to seasons & content)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .padding(horizontal = 8.dp)
                        .graphicsLayer { alpha = headerAlpha },
                    contentAlignment = Alignment.Center
                ) {
                    val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
                    val effectiveHeaderClearart = uiState.heroClearLogoUrl?.takeIf { it.isNotBlank() }
                        ?: item?.clearartUrl?.takeIf { it.isNotBlank() && (!it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE) }
                        ?: item?.let { com.omnitrack.app.data.enrichment.EnrichmentRepository.getCachedClearart(it) }?.takeIf {
                            !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
                        }
                    var headerClearartFailed by remember(effectiveHeaderClearart) { mutableStateOf(false) }
                    if (!effectiveHeaderClearart.isNullOrBlank() && !headerClearartFailed) {
                        val headerLogoRequest = remember(effectiveHeaderClearart) {
                            ImageRequest.Builder(context)
                                .data(effectiveHeaderClearart)
                                .crossfade(false)
                                .build()
                        }
                        AsyncImage(
                            model = headerLogoRequest,
                            contentDescription = item?.title.orEmpty(),
                            modifier = Modifier
                                .height(32.dp)
                                .widthIn(max = 200.dp),
                            contentScale = ContentScale.Fit,
                            onError = { headerClearartFailed = true }
                        )
                    } else if (item != null) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Top-Right Watched / Completion Checkmark Button (1:1 Nuvio Parity)
                if (item != null) {
                    val isCompleted = item.status == TrackingStatus.COMPLETED
                    IconButton(
                        onClick = {
                            val nextStatus = if (isCompleted) {
                                if (item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME || item.mediaType == MediaType.MANGA || item.mediaType == MediaType.NOVEL) {
                                    TrackingStatus.IN_PROGRESS
                                } else {
                                    TrackingStatus.PLAN_TO_TRACK
                                }
                            } else {
                                TrackingStatus.COMPLETED
                            }
                            viewModel.updateStatus(nextStatus)
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Toggle Watched Status",
                            tint = if (isCompleted) Color(0xFFE52E5E) else Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .shadow(elevation = 8.dp, shape = CircleShape)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(44.dp))
                }
            }
            }

            // Entry Options Bottom Sheet (Triggered by '...' button beside Title)
            if (showOptionsMenu) {
                DetailOptionsMenuBottomSheet(
                    presentationSettings = presentationSettings,
                    onDismiss = { showOptionsMenu = false },
                    onChangeArtwork = {
                        showOptionsMenu = false
                        showArtworkPicker = true
                    },
                    onToggleLayout = {
                        val newMode = if (presentationSettings.detailsPagePresentation == DetailsPagePresentation.HERO) {
                            DetailsPagePresentation.CLASSIC
                        } else {
                            DetailsPagePresentation.HERO
                        }
                        coroutineScope.launch {
                            prefManager.updateDetailsPagePresentation(newMode)
                        }
                        showOptionsMenu = false
                    },
                    onRemoveFromLibrary = {
                        showOptionsMenu = false
                        viewModel.deleteItem()
                        onBack()
                    }
                )
            }

            // Cover & Banner artwork customization bottom sheet (Option A multi-tab)
            if (showArtworkPicker) {
                ArtworkPickerBottomSheet(
                    title = item.title,
                    mediaType = item.mediaType,
                    currentCoverUrl = losslessCover,
                    currentBannerUrl = item.bannerUrl,
                    currentCustomBannerPath = item.customBannerPath,
                    mediaId = item.id,
                    currentRole = item.artworkRole,
                    anilistId = item.anilistId,
                    malId = item.malId,
                    mangadexId = item.mangadexId,
                    nativeTitle = item.nativeTitle,
                    romajiTitle = uiState.romajiTitle,
                    originCountry = item.originCountry ?: uiState.countryOfOrigin,
                    defaultCoverUrl = item.coverUrl,
                    onDismiss = { showArtworkPicker = false },
                    onCoverSelected = { newUrl ->
                        if (newUrl.startsWith("/") || newUrl.startsWith("file:") || newUrl.startsWith("content:")) {
                            viewModel.updateCustomCoverPath(newUrl, context)
                        } else {
                            viewModel.updateCover(newUrl)
                        }
                    },
                    onBannerSelected = { newBanner ->
                        if (newBanner == "NONE") {
                            viewModel.toggleDisableBanner()
                        } else if (newBanner.startsWith("/") || newBanner.startsWith("file:")) {
                            viewModel.updateCustomBannerPath(newBanner, context)
                        } else {
                            if (item.customBannerPath == "NONE") {
                                viewModel.updateCustomBannerPath(null, context)
                            }
                            viewModel.updateBanner(newBanner)
                        }
                    },
                    onResetCover = {
                        viewModel.resetCover(context)
                    },
                    onResetBanner = {
                        viewModel.resetBanner(context)
                    },
                    onRoleSelected = { newRole ->
                        viewModel.updateArtworkRole(newRole)
                    }
                )
            }

            // Chimanew Instant Progress Bottom Sheet
            if (showProgressEditSheet) {
                ChimanewProgressBottomSheet(
                    currentProgress = item.currentProgress,
                    totalProgress = item.totalProgress,
                    onProgressConfirmed = { viewModel.updateProgress(it) },
                    onDismiss = { showProgressEditSheet = false }
                )
            }

            // Chimanew Instant Status Bottom Sheet
            if (showStatusPickerSheet) {
                ChimanewStatusBottomSheet(
                    currentStatus = item.status,
                    onStatusSelected = { viewModel.updateStatus(it) },
                    onDismiss = { showStatusPickerSheet = false }
                )
            }

            // Tenth-Decimal Score & Rating Sheet (1.0 to 10.0, 0.1 precision)
            if (showScoreRatingSheet) {
                ScoreRatingSheet(
                    initialScore = item.userRating,
                    onDismiss = { showScoreRatingSheet = false },
                    onScoreConfirmed = { viewModel.updateRating(it) }
                )
            }

            // Chimanew-Style Multi-Tracker Sheet
            if (showMultiTrackerSheet) {
                MultiTrackerSheet(
                    item = item,
                    onDismiss = { showMultiTrackerSheet = false },
                    onUpdateItem = { updatedItem ->
                        viewModel.updateMediaItem(updatedItem)
                    }
                )
            }

            DetailSeriesDialogs(
                seriesState = seriesUiState,
                seasons = uiState.realSeasons,
                episodes = uiState.realEpisodes,
                episodesBySeason = uiState.episodesBySeason,
                watchedEpisodes = uiState.watchedEpisodes,
                selectedSeasonNumber = uiState.selectedSeasonNumber,
                isFillerSkipEnabled = uiState.isFillerSkipEnabled,
                isMangaMode = isReadingType,
                isLightNovelVolumeMode = isLightNovelVolumeMode,
                maxTotalProgress = item.totalProgress?.toInt(),
                currentTotalProgress = item.currentProgress.toInt(),
                onToggleEpisodeWatched = { viewModel.toggleEpisodeWatched(it) },
                onMarkPreviousWatched = { viewModel.markPreviousEpisodesWatched(it) },
                onToggleSeasonWatched = { viewModel.toggleSeasonWatched(it) },
                onMarkWatchedChoice = { ep, markFillers -> viewModel.markWatchedUpToWithFillerChoice(ep, markFillers) },
                onJumpToProgress = if (isReadingType) {
                    { target -> viewModel.jumpToChapterOrVolume(target) }
                } else null
            )
        }
    }
}
}

/**
 * Zero-Black-Screen Sibling Detail Preview.
 * Preloads and renders adjacent library entries during horizontal swiping gestures,
 * ensuring continuous 120Hz physical transitions with zero black flashes.
 */
@Composable
private fun SiblingDetailPreview(
    item: com.omnitrack.app.data.db.entity.MediaItemEntity,
    presentationSettings: PresentationSettings,
    modifier: Modifier = Modifier
) {
    val cachedAux = remember(item.id, item.tmdbId, item.anilistId, item.malId) {
        DetailAuxMemoryCache.getCacheKey(item)?.let { DetailAuxMemoryCache.cache[it] }
            ?: item.tmdbId?.takeIf { item.mediaType != MediaType.ANIME || item.anilistId == null || item.anilistId <= 0L }?.let {
                DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getTmdbCacheKey(if (item.mediaType == MediaType.MOVIE) "movie" else "tv", it)]
            }
    }
    val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
    val siblingClearLogo = cachedAux?.heroClearLogoUrl?.takeIf {
        !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
    } ?: item.clearartUrl?.takeIf {
        !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
    }

    val effectiveCover = item.customCoverPath
        ?: when (item.artworkRole) {
            "BANNER_AS_COVER" -> item.bannerUrl ?: item.coverUrl
            else -> item.coverUrl
        }
    val coverImageModel = remember(effectiveCover, item.customCoverPath) {
        item.customCoverPath?.let { if (it.startsWith("http://") || it.startsWith("https://")) it else java.io.File(it) }
            ?: LosslessCoverMapper.toLosslessUrl(effectiveCover)
    }
    val effectiveBanner = when {
        item.customBannerPath == "NONE" || item.artworkRole == "DISABLE_BANNER" -> null
        item.customBannerPath != null -> item.customBannerPath
        item.artworkRole == "COVER_AS_BANNER" -> item.coverUrl
        item.artworkRole == "BANNER_AS_COVER" -> item.bannerUrl
        else -> item.bannerUrl
    }
    val backdropTargetUrl = effectiveBanner

    val isCinematicBlur = presentationSettings.fallbackBackgroundStyle == FallbackBackgroundStyle.CINEMATIC_BLUR
    val backdropBitmap = rememberArtworkBackdrop(
        imageUrl = backdropTargetUrl ?: item.coverUrl,
        enabled = isCinematicBlur
    )
    val losslessCover = remember(effectiveCover) { LosslessCoverMapper.toLosslessUrl(effectiveCover) }
    val dominantAccent = rememberCoverDominantColor(losslessCover)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        when (presentationSettings.fallbackBackgroundStyle) {
            FallbackBackgroundStyle.CINEMATIC_BLUR -> {
                ArtworkBackdrop(
                    image = backdropBitmap,
                    modifier = Modifier.fillMaxSize()
                )
            }
            FallbackBackgroundStyle.DOMINANT_COLOR -> {
                CoverGlowBox(
                    imageUrl = backdropTargetUrl,
                    modifier = Modifier.fillMaxSize()
                ) {}
            }
            FallbackBackgroundStyle.SOLID_BLACK -> {}
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (presentationSettings.detailsPagePresentation == DetailsPagePresentation.HERO) {
                DetailHeroHeaderSection(
                    item = item,
                    backdropTargetUrl = backdropTargetUrl,
                    englishTitle = cachedAux?.englishTitle,
                    romajiTitle = cachedAux?.romajiTitle,
                    heroClearLogoUrl = siblingClearLogo,
                    novelDetailMeta = cachedAux?.novelDetailMeta ?: NovelDetailMeta(),
                    onOptionsClick = {},
                    onEditProgress = {},
                    onEditStatus = {},
                    onEditScore = {},
                    onOpenTrackers = {}
                )
            } else {
                DetailClassicHeaderSection(
                    item = item,
                    coverImageModel = coverImageModel,
                    backdropTargetUrl = backdropTargetUrl,
                    englishTitle = cachedAux?.englishTitle,
                    romajiTitle = cachedAux?.romajiTitle,
                    heroClearLogoUrl = siblingClearLogo,
                    novelDetailMeta = cachedAux?.novelDetailMeta ?: NovelDetailMeta(),
                    onCoverClick = {},
                    onCoverLoaded = {},
                    onOptionsClick = {},
                    onEditProgress = {},
                    onEditStatus = {},
                    onEditScore = {},
                    onOpenTrackers = {}
                )
            }

            val hasProgressControls = item.mediaType in listOf(MediaType.GAME, MediaType.VISUAL_NOVEL, MediaType.MOVIE)
            val hasBackdrop = backdropTargetUrl != null
            if (hasProgressControls) {
                Spacer(modifier = Modifier.height(if (hasBackdrop) (-10).dp else 4.dp))
                DetailProgressSection(
                    item = item,
                    onUpdateProgress = {},
                    onUpdatePlaytime = {},
                    onTogglePlatform = {},
                    onUpdateStatus = {}
                )
                Spacer(modifier = Modifier.height(4.dp))
            } else {
                Spacer(modifier = Modifier.height(if (hasBackdrop) (-36).dp else (-8).dp))
            }

            if (!item.synopsis.isNullOrBlank()) {
                DetailSynopsisSection(
                    synopsis = item.synopsis,
                    dominantAccent = dominantAccent
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            DetailShowDetailsSection(item = item)
        }
    }
}

@Composable
private fun UnboxedStatsRow(
    item: com.omnitrack.app.data.db.entity.MediaItemEntity,
    onEditProgress: () -> Unit,
    onEditStatus: () -> Unit,
    onEditScore: () -> Unit,
    onOpenTrackers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val boundCount = remember(item) {
        listOfNotNull(
            item.anilistId,
            item.malId,
            item.traktId,
            item.simklId,
            item.tmdbId,
            item.vndbId,
            item.comickId,
            item.bangumiId,
            item.igdbId
        ).size
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeW = 1.dp.toPx()
                val dividerColor = Color.White.copy(alpha = 0.10f)
                drawLine(dividerColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = strokeW)
                drawLine(dividerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = strokeW)
            }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Section 1: Option A - Clean Minimalist Counter (Zero pencil icon, Zero +1 badge)
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onEditProgress() }
                .padding(vertical = 4.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val progressNumber = if (item.mediaType == MediaType.GAME || item.mediaType == MediaType.VISUAL_NOVEL) {
                String.format("%.1f hrs", item.playtimeHours)
            } else {
                "${item.currentProgress.toInt()}${item.totalProgress?.let { " / ${it.toInt()}" } ?: ""}"
            }
            Text(
                text = progressNumber,
                color = TextPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.2).sp
            )
            val statsUnitLabel = if (item.mediaType == MediaType.NOVEL && com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                    item.platform, item.originCountry, item.anilistId, item.malId, item.totalProgress, item.title
                ) == "LIGHT_NOVEL") {
                "VOLUMES"
            } else {
                item.mediaType.unitLabel.uppercase()
            }
            Text(
                text = statsUnitLabel,
                color = AccentArctic,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }

        // Vertical hairline
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )

        // Section 2: Status with Glowing Dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { onEditStatus() }
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(item.status.statusColor())
            )
            Column {
                Text(
                    text = item.status.displayName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "STATUS",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Vertical hairline
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )

        // Section 3: Rating Score
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { onEditScore() }
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = AccentGold,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = item.userRating?.let { String.format("%.1f", it) } ?: "Rate",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "SCORE",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Vertical hairline
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )

        // Section 4: Trackers Pill (Chimanew Option 2)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable { onOpenTrackers() }
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (boundCount > 0) AccentJade else Color.White.copy(alpha = 0.35f))
            )
            Column {
                Text(
                    text = if (boundCount > 0) "$boundCount Bound" else "None",
                    color = if (boundCount > 0) AccentJade else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "TRACKERS",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun DetailNuvioMetaRow(item: com.omnitrack.app.data.db.entity.MediaItemEntity) {
    val releaseYearText = item.releaseYear?.trim()?.takeIf { it.isNotBlank() }
    val genres = item.categoryNames.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString(", ")
        .takeIf { it.isNotBlank() }

    val releaseLine = when {
        releaseYearText != null -> releaseYearText
        else -> genres
    }

    val runtimeText = item.runtimeMinutes?.let { minutes ->
        val h = minutes / 60
        val m = minutes % 60
        when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    val ageBadge = item.contentRating?.trim()?.takeIf { it.isNotBlank() }

    if (releaseLine != null || runtimeText != null || ageBadge != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            releaseLine?.let { line ->
                Text(
                    text = line,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            runtimeText?.let { rt ->
                Text(
                    text = rt,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            ageBadge?.let { badge ->
                Box(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.60f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailHeroHeaderSection(
    item: com.omnitrack.app.data.db.entity.MediaItemEntity,
    backdropTargetUrl: String?,
    englishTitle: String? = null,
    romajiTitle: String? = null,
    heroClearLogoUrl: String? = null,
    novelDetailMeta: NovelDetailMeta = NovelDetailMeta(),
    onToggleTitle: (() -> Unit)? = null,
    onOptionsClick: () -> Unit,
    onEditProgress: () -> Unit,
    onEditStatus: () -> Unit,
    onEditScore: () -> Unit,
    onOpenTrackers: () -> Unit
) {
    if (backdropTargetUrl != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        ) {
            val heroContext = LocalContext.current
            val bannerRequest = remember(backdropTargetUrl, heroContext) {
                ImageRequest.Builder(heroContext)
                    .data(backdropTargetUrl)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = bannerRequest,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxSize()
                    .featherArtwork(startYRatio = 0.50f),
                contentScale = ContentScale.Crop
            )
        }
    } else {
        Spacer(modifier = Modifier.height(72.dp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .offset(y = (-8).dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
                val effectiveClearartUrl = heroClearLogoUrl?.takeIf { it.isNotBlank() }
                    ?: item.clearartUrl?.takeIf { it.isNotBlank() && (!it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE) }
                    ?: com.omnitrack.app.data.enrichment.EnrichmentRepository.getCachedClearart(item)?.takeIf {
                        !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
                    }
                var clearartFailed by remember(effectiveClearartUrl) { mutableStateOf(false) }
                val hasClearart = !effectiveClearartUrl.isNullOrBlank() && !clearartFailed
                val context = LocalContext.current

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = onToggleTitle != null) { onToggleTitle?.invoke() },
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (hasClearart) {
                        val logoRequest = remember(effectiveClearartUrl) {
                            ImageRequest.Builder(context)
                                .data(effectiveClearartUrl)
                                .crossfade(false)
                                .build()
                        }
                        AsyncImage(
                            model = logoRequest,
                            contentDescription = item.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(bottom = 4.dp),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            onError = { clearartFailed = true }
                        )
                    } else {
                        Text(
                            text = item.title,
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 29.sp
                        )
                    }
                }

                if (item.nativeTitle != null) {
                    Text(
                        text = item.nativeTitle,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.5.sp
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val heroTypeBadge = when (item.mediaType) {
                        MediaType.MANGA -> com.omnitrack.app.ui.screens.home.inferMangaSubtype(item.originCountry, item.nativeTitle).uppercase()
                        MediaType.NOVEL -> {
                            val sub = com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                                item.platform, item.originCountry, item.anilistId, item.malId, item.totalProgress, item.title
                            )
                            if (sub == "LIGHT_NOVEL") "LIGHT NOVEL" else "WEB NOVEL"
                        }
                        else -> item.mediaType.displayName.uppercase()
                    }
                    Text(
                        text = heroTypeBadge,
                        color = AccentArctic,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    val heroSubInfo = if (item.mediaType == MediaType.NOVEL) item.originCountry?.uppercase() else item.platform
                    if (!heroSubInfo.isNullOrBlank()) {
                        Text(text = " • ", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                        Text(text = heroSubInfo, color = Color.White, fontSize = 11.5.sp)
                    }
                }
            }

            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier
                    .size(40.dp)
                    .padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        DetailNuvioMetaRow(item = item)
        NuvioRatingsRow(
            item = item,
            anilistScore = novelDetailMeta.anilistScore,
            malScore = novelDetailMeta.malScore,
            kitsuScore = novelDetailMeta.kitsuScore,
            mangaUpdatesScore = novelDetailMeta.mangaUpdatesScore,
            bangumiScore = novelDetailMeta.bangumiScore
        )
        UnboxedStatsRow(
            item = item,
            onEditProgress = onEditProgress,
            onEditStatus = onEditStatus,
            onEditScore = onEditScore,
            onOpenTrackers = onOpenTrackers
        )
    }
}

@Composable
private fun DetailClassicHeaderSection(
    item: com.omnitrack.app.data.db.entity.MediaItemEntity,
    coverImageModel: Any?,
    backdropTargetUrl: String?,
    englishTitle: String? = null,
    romajiTitle: String? = null,
    heroClearLogoUrl: String? = null,
    novelDetailMeta: NovelDetailMeta = NovelDetailMeta(),
    onToggleTitle: (() -> Unit)? = null,
    onCoverClick: () -> Unit,
    onCoverLoaded: (String) -> Unit,
    onOptionsClick: () -> Unit,
    onEditProgress: () -> Unit,
    onEditStatus: () -> Unit,
    onEditScore: () -> Unit,
    onOpenTrackers: () -> Unit
) {
    val hasBackdrop = backdropTargetUrl != null
    val posterOffset = if (hasBackdrop) (-50).dp else 0.dp
    val statsOffset = if (hasBackdrop) (-30).dp else 0.dp

    if (hasBackdrop) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        ) {
            val classicContext = LocalContext.current
            val bannerRequest = remember(backdropTargetUrl, classicContext) {
                ImageRequest.Builder(classicContext)
                    .data(backdropTargetUrl)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = bannerRequest,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .featherArtwork(startYRatio = 0.50f),
                contentScale = ContentScale.Crop
            )
        }
    } else {
        Spacer(
            modifier = Modifier
                .statusBarsPadding()
                .height(56.dp + 16.dp)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .offset(y = posterOffset),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(0.68f)
                .shadow(18.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .clickable { onCoverClick() }
        ) {
            AsyncImage(
                model = coverImageModel,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = {
                    if (coverImageModel is String) {
                        onCoverLoaded(coverImageModel)
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
                    val effectiveClearartUrl = heroClearLogoUrl?.takeIf { it.isNotBlank() }
                        ?: item.clearartUrl?.takeIf { it.isNotBlank() && (!it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE) }
                        ?: com.omnitrack.app.data.enrichment.EnrichmentRepository.getCachedClearart(item)?.takeIf {
                            !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
                        }
                    var clearartFailed by remember(effectiveClearartUrl) { mutableStateOf(false) }
                    val hasClearart = !effectiveClearartUrl.isNullOrBlank() && !clearartFailed
                    val context = LocalContext.current

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = onToggleTitle != null) { onToggleTitle?.invoke() },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (hasClearart) {
                            val logoRequest = remember(effectiveClearartUrl) {
                                ImageRequest.Builder(context)
                                    .data(effectiveClearartUrl)
                                    .crossfade(false)
                                    .build()
                            }
                            AsyncImage(
                                model = logoRequest,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .padding(bottom = 2.dp),
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                                onError = { clearartFailed = true }
                            )
                        } else {
                            Text(
                                text = item.title,
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 27.sp
                            )
                        }
                    }

                    if (item.nativeTitle != null) {
                        Text(
                            text = item.nativeTitle,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onOptionsClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val compactTypeBadge = when (item.mediaType) {
                    MediaType.MANGA -> com.omnitrack.app.ui.screens.home.inferMangaSubtype(item.originCountry, item.nativeTitle).uppercase()
                    MediaType.NOVEL -> {
                        val sub = com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                            item.platform, item.originCountry, item.anilistId, item.malId, item.totalProgress, item.title
                        )
                        if (sub == "LIGHT_NOVEL") "LIGHT NOVEL" else "WEB NOVEL"
                    }
                    else -> item.mediaType.displayName.uppercase()
                }
                Text(
                    text = compactTypeBadge,
                    color = AccentArctic,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                val compactSubInfo = if (item.mediaType == MediaType.NOVEL) item.originCountry?.uppercase() else item.platform
                if (!compactSubInfo.isNullOrBlank()) {
                    Text(text = " • ", color = Color.White.copy(alpha = 0.85f), fontSize = 10.5.sp)
                    Text(text = compactSubInfo, color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .offset(y = statsOffset)
    ) {
        Column {
            DetailNuvioMetaRow(item = item)
            NuvioRatingsRow(
                item = item,
                anilistScore = novelDetailMeta.anilistScore,
                malScore = novelDetailMeta.malScore,
                kitsuScore = novelDetailMeta.kitsuScore,
                mangaUpdatesScore = novelDetailMeta.mangaUpdatesScore,
                bangumiScore = novelDetailMeta.bangumiScore
            )
            UnboxedStatsRow(
                item = item,
                onEditProgress = onEditProgress,
                onEditStatus = onEditStatus,
                onEditScore = onEditScore,
                onOpenTrackers = onOpenTrackers
            )
        }
    }
}

@Composable
private fun DetailSynopsisSection(
    synopsis: String,
    dominantAccent: Color
) {
    val context = LocalContext.current
    var synopsisExpanded by remember { mutableStateOf(false) }
    var canExpandSynopsis by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsInstance by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }

    val cleanText = remember(synopsis) {
        synopsis
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]*>"""), "")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .replace(Regex("""\s*[\(\[]\s*$"""), "")
            .trim()
    }

    LaunchedEffect(cleanText) {
        if (isSpeaking) {
            ttsInstance?.stop()
            isSpeaking = false
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            ttsInstance?.stop()
            ttsInstance?.shutdown()
            ttsInstance = null
        }
    }

    if (cleanText.isBlank()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Overview",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Dartotsu Synopsis SpeakButton
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSpeaking) dominantAccent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f))
                    .clickable {
                        if (isSpeaking) {
                            ttsInstance?.stop()
                            isSpeaking = false
                        } else {
                            val existing = ttsInstance
                            if (existing != null) {
                                existing.speak(cleanText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "synopsis_tts")
                                isSpeaking = true
                            } else {
                                isSpeaking = true
                                var createdTts: android.speech.tts.TextToSpeech? = null
                                createdTts = android.speech.tts.TextToSpeech(context) { status ->
                                    if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                                        createdTts?.language = java.util.Locale.ENGLISH
                                        createdTts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                            override fun onStart(utteranceId: String?) { isSpeaking = true }
                                            override fun onDone(utteranceId: String?) { isSpeaking = false }
                                            @Deprecated("Deprecated in Java")
                                            override fun onError(utteranceId: String?) { isSpeaking = false }
                                        })
                                        createdTts?.speak(cleanText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "synopsis_tts")
                                    } else {
                                        isSpeaking = false
                                    }
                                }
                                ttsInstance = createdTts
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.StopCircle else Icons.Default.VolumeUp,
                        contentDescription = if (isSpeaking) "Stop reading" else "Read synopsis",
                        tint = if (isSpeaking) dominantAccent else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isSpeaking) "Stop" else "Listen",
                        color = if (isSpeaking) dominantAccent else Color.White.copy(alpha = 0.85f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = canExpandSynopsis,
                    onClick = { synopsisExpanded = !synopsisExpanded }
                )
        ) {
            Text(
                text = cleanText,
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult ->
                    if (!synopsisExpanded) {
                        canExpandSynopsis = textLayoutResult.hasVisualOverflow || textLayoutResult.lineCount > 3
                    }
                }
            )
        }
        if (canExpandSynopsis) {
            Text(
                text = if (synopsisExpanded) "Show less" else "Read more",
                color = dominantAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { synopsisExpanded = !synopsisExpanded }
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun DetailProgressSection(
    item: com.omnitrack.app.data.db.entity.MediaItemEntity,
    onUpdateProgress: (Double) -> Unit,
    onUpdatePlaytime: (Double) -> Unit,
    onTogglePlatform: (String) -> Unit,
    onUpdateStatus: (TrackingStatus) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (item.mediaType) {
            MediaType.MANGA, MediaType.NOVEL -> {
                ChapterStepper(
                    unitLabel = item.mediaType.unitLabel,
                    currentProgress = item.currentProgress,
                    totalProgress = item.totalProgress,
                    onProgressChanged = onUpdateProgress
                )
            }

            MediaType.GAME -> {
                GameProgressCard(
                    hoursPlayed = item.playtimeHours,
                    storyProgress = item.currentProgress,
                    totalStoryChapters = item.totalProgress,
                    achievementsUnlocked = item.steamAchievementsUnlocked,
                    achievementsTotal = item.steamAchievementsTotal,
                    selectedPlatforms = item.platforms?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                        ?: item.platform?.let { setOf(it) }
                        ?: emptySet(),
                    steamAppId = null,
                    onHoursChanged = onUpdatePlaytime,
                    onStoryProgressChanged = onUpdateProgress,
                    onPlatformToggled = onTogglePlatform
                )
            }

            MediaType.VISUAL_NOVEL -> {
                ChapterStepper(
                    unitLabel = item.mediaType.unitLabel,
                    currentProgress = item.currentProgress,
                    totalProgress = item.totalProgress,
                    onProgressChanged = onUpdateProgress,
                    isDecimal = true,
                    step = 0.5
                )
            }

            MediaType.MOVIE -> {
                val isWatched = item.status == TrackingStatus.COMPLETED
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUpdateStatus(
                                if (isWatched) TrackingStatus.PLAN_TO_TRACK else TrackingStatus.COMPLETED
                            )
                        }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isWatched) AccentJade else Color(0x22FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isWatched) {
                                Text("✓", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = if (isWatched) "Watched" else "Mark as Watched",
                            color = if (isWatched) AccentJade else TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }

                    Text(
                        text = if (isWatched) "Completed" else "Plan to Watch",
                        color = if (isWatched) AccentJade else TextMuted,
                        fontSize = 13.sp
                    )
                }
            }

            else -> {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailOptionsMenuBottomSheet(
    presentationSettings: PresentationSettings,
    onDismiss: () -> Unit,
    onChangeArtwork: () -> Unit,
    onToggleLayout: () -> Unit,
    onRemoveFromLibrary: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AmoledBlack,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Entry Options",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // 1. Change Artwork
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onChangeArtwork() }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = AccentArctic,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Change Artwork & Roles",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Search online covers, pick custom image, swap roles",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            // 2. Toggle Layout (Hero vs Classic Poster)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleLayout() }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ViewCarousel,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Toggle Layout",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (presentationSettings.detailsPagePresentation == DetailsPagePresentation.HERO) {
                            "Current: Hero Layout (Tap for Classic Poster)"
                        } else {
                            "Current: Classic Poster (Tap for Hero Layout)"
                        },
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            // 3. Remove from Library
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onRemoveFromLibrary() }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Remove from Library",
                        color = Color(0xFFEF4444),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Delete this media entry and its local cache",
                        color = Color(0xFFEF4444).copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
