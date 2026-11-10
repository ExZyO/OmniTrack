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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.omnitrack.app.ui.components.rememberArtworkBackdrop
import com.omnitrack.app.ui.components.rememberCoverDominantColor
import com.omnitrack.app.ui.screens.detail.components.CastMember
import com.omnitrack.app.ui.screens.detail.components.ChimanewProgressBottomSheet
import com.omnitrack.app.ui.screens.detail.components.ChimanewStatusBottomSheet
import com.omnitrack.app.ui.screens.detail.components.DetailCastSection
import com.omnitrack.app.ui.screens.detail.components.DetailCollectionSection
import com.omnitrack.app.ui.screens.detail.components.DetailNetworksSection
import com.omnitrack.app.ui.screens.detail.components.DetailSeriesContent
import com.omnitrack.app.ui.screens.detail.components.DetailShowDetailsSection
import com.omnitrack.app.ui.screens.detail.components.DetailTrailersSection
import com.omnitrack.app.ui.screens.detail.components.MultiTrackerSheet
import com.omnitrack.app.ui.screens.detail.components.NetworkProductionCompany
import com.omnitrack.app.ui.screens.detail.components.NuvioRatingsRow
import com.omnitrack.app.ui.screens.detail.components.RecommendationsSection
import com.omnitrack.app.ui.screens.detail.components.ReviewsSection
import com.omnitrack.app.ui.screens.detail.components.SeasonEpisodesSection
import com.omnitrack.app.ui.screens.detail.components.ScoreRatingSheet
import com.omnitrack.app.ui.screens.detail.components.statusColor
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
    val item = uiState.item
    val coroutineScope = rememberCoroutineScope()
    var showArtworkPicker by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    var showProgressEditSheet by remember { mutableStateOf(false) }
    var showStatusPickerSheet by remember { mutableStateOf(false) }
    var showScoreRatingSheet by remember { mutableStateOf(false) }
    var showMultiTrackerSheet by remember { mutableStateOf(false) }
    var selectedPerson by remember { mutableStateOf<CastMember?>(null) }
    var selectedCompany by remember { mutableStateOf<NetworkProductionCompany?>(null) }

    BackHandler(enabled = selectedPerson != null) {
        selectedPerson = null
    }
    BackHandler(enabled = selectedCompany != null) {
        selectedCompany = null
    }

    if (selectedPerson != null && selectedPerson!!.tmdbPersonId != null) {
        val tmdbKey = "844dba0bfd8f3a4f3799f6130ef9e335"
        PersonDetailScreen(
            personId = selectedPerson!!.tmdbPersonId!!,
            personName = selectedPerson!!.name,
            initialAvatarUrl = selectedPerson!!.avatarUrl,
            apiKey = tmdbKey,
            onBack = { selectedPerson = null },
            onOpenMediaPreview = { recItem ->
                selectedPerson = null
                viewModel.openPreview(recItem)
            }
        )
        return
    }

    if (selectedCompany != null) {
        val tmdbKey = "844dba0bfd8f3a4f3799f6130ef9e335"
        TmdbEntityBrowseScreen(
            entityId = selectedCompany!!.id,
            entityName = selectedCompany!!.name,
            entityKind = selectedCompany!!.kind,
            apiKey = tmdbKey,
            onBack = { selectedCompany = null },
            onOpenMediaPreview = { recItem ->
                selectedCompany = null
                viewModel.openPreview(recItem)
            }
        )
        return
    }

    val scrollState = rememberScrollState()

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
                // If scrollState > 0, this gesture is scrolling content, so disarm pull-down
                if (scrollState.value > 0) {
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
                // Arm pull-down ONLY if zero scroll was consumed in this gesture AND scrollState is at 0
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput &&
                    scrollConsumedInGesture == 0f &&
                    scrollState.value == 0 &&
                    available.y > 0
                ) {
                    isPullDownArmed = true
                }

                // Only accumulate dismiss drag if gesture strictly started at scrollState == 0 with zero prior scroll
                if (isPullDownArmed && available.y > 0 && scrollState.value == 0) {
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

    LaunchedEffect(item?.id) {
        animatableOffsetX.snapTo(0f)
        dragOffsetY = 0f
        scrollState.scrollTo(0)
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
        val currentOffsetX = animatableOffsetX.value

        // 1. PREVIOUS SIBLING PREVIEW (Slides in from the left when user drags right)
        if (uiState.hasPreviousEntry && uiState.previousItem != null && currentOffsetX > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = currentOffsetX - screenWidthPx
                        translationY = animatedDragOffsetY.coerceAtLeast(0f)
                    }
            ) {
                SiblingDetailPreview(
                    item = uiState.previousItem!!,
                    presentationSettings = presentationSettings
                )
            }
        }

        // 2. NEXT SIBLING PREVIEW (Slides in from the right when user drags left)
        if (uiState.hasNextEntry && uiState.nextItem != null && currentOffsetX < 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = currentOffsetX + screenWidthPx
                        translationY = animatedDragOffsetY.coerceAtLeast(0f)
                    }
            ) {
                SiblingDetailPreview(
                    item = uiState.nextItem!!,
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
                                        viewModel.navigateToPrevious()
                                        animatableOffsetX.snapTo(0f)
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
                                        viewModel.navigateToNext()
                                        animatableOffsetX.snapTo(0f)
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentArctic, strokeWidth = 2.dp)
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
                item.customCoverPath ?: if (!customCoverFailed && customCoverUrl != null) customCoverUrl
                else when (item.artworkRole) {
                    "BANNER_AS_COVER" -> item.bannerUrl ?: item.coverUrl
                    else -> item.coverUrl
                }
            }
            val effectiveBanner = remember(item.customBannerPath, item.artworkRole, item.coverUrl, item.bannerUrl, customBannerUrl, customBannerFailed) {
                when {
                    item.customBannerPath == "NONE" || item.artworkRole == "DISABLE_BANNER" -> null
                    item.customBannerPath != null -> item.customBannerPath
                    !customBannerFailed && customBannerUrl != null -> customBannerUrl
                    item.artworkRole == "COVER_AS_BANNER" -> item.coverUrl
                    item.artworkRole == "BANNER_AS_COVER" -> item.bannerUrl ?: item.coverUrl
                    else -> item.bannerUrl ?: item.coverUrl
                }
            }

            val coverCache = remember { (context.applicationContext as? com.omnitrack.app.OmniTrackApplication)?.coverCache }
            val coverImageModel = remember(effectiveCover, item.customCoverPath) {
                item.customCoverPath?.let { java.io.File(it) } ?: LosslessCoverMapper.toLosslessUrl(effectiveCover)
            }

            val losslessCover = remember(effectiveCover) { LosslessCoverMapper.toLosslessUrl(effectiveCover) }
            val dominantAccent = rememberCoverDominantColor(losslessCover)
            val backdropTargetUrl = effectiveBanner

            val isCinematicBlur = presentationSettings.fallbackBackgroundStyle == FallbackBackgroundStyle.CINEMATIC_BLUR
            val backdropBitmap = rememberArtworkBackdrop(
                imageUrl = backdropTargetUrl,
                enabled = isCinematicBlur
            )

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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // PRESENTATION MODE 1 or 2
                if (presentationSettings.detailsPagePresentation == DetailsPagePresentation.HERO) {
                    DetailHeroHeaderSection(
                        item = item,
                        backdropTargetUrl = backdropTargetUrl,
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

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Actions / Progress Section (100% Unboxed flowing controls)
                DetailProgressSection(
                    item = item,
                    onUpdateProgress = { viewModel.updateProgress(it) },
                    onUpdatePlaytime = { viewModel.updatePlaytime(it) },
                    onTogglePlatform = { viewModel.togglePlatform(it) },
                    onUpdateStatus = { viewModel.updateStatus(it) }
                )
                Spacer(modifier = Modifier.height(14.dp))

                // 2. Overview (Seamless Synopsis with bidirectional overflow-aware toggle)
                if (!item.synopsis.isNullOrBlank()) {
                    DetailSynopsisSection(
                        synopsis = item.synopsis,
                        dominantAccent = dominantAccent
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // 3. Production / Networks & Companies Section (Clickable)
                DetailNetworksSection(
                    item = item,
                    companies = uiState.companies,
                    onCompanyClick = { company ->
                        selectedCompany = company
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))

                // 4. Cast Section (Avatars + character names)
                if (uiState.cast.isNotEmpty()) {
                    DetailCastSection(
                        cast = uiState.cast,
                        onCastMemberClick = { member ->
                            selectedPerson = member
                        }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // 5. Comments Section (Trakt & Jikan Community Reviews Carousel)
                if (uiState.reviews.isNotEmpty()) {
                    ReviewsSection(reviews = uiState.reviews)
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // 6. Trailers Section (Trailer video cards)
                if (uiState.trailers.isNotEmpty()) {
                    DetailTrailersSection(
                        trailers = uiState.trailers,
                        contentTitle = item.title
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // 7. Episodes Section (1:1 Nuvio Seasons & Episode Tracking Grid)
                if (item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME) {
                    DetailSeriesContent(
                        seasons = uiState.realSeasons,
                        episodes = uiState.realEpisodes,
                        episodesBySeason = uiState.episodesBySeason,
                        watchedEpisodes = uiState.watchedEpisodes,
                        selectedSeasonNumber = uiState.selectedSeasonNumber,
                        fallbackBackdropUrl = item.bannerUrl,
                        layoutMode = episodeLayoutMode,
                        layoutScale = episodeLayoutScale,
                        initialSeasonViewMode = seasonViewMode,
                        onSeasonViewModeChanged = { next ->
                            coroutineScope.launch { prefManager.updateSeasonViewMode(next) }
                        },
                        onLayoutModeChanged = { next ->
                            coroutineScope.launch { prefManager.updateEpisodeLayoutMode(next) }
                        },
                        onSelectSeason = { viewModel.selectSeason(it) },
                        onToggleEpisodeWatched = { viewModel.toggleEpisodeWatched(it) },
                        onMarkPreviousWatched = { viewModel.markPreviousEpisodesWatched(it) },
                        onToggleSeasonWatched = { viewModel.toggleSeasonWatched(it) }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // 8. Details Section (Show & Production Details)
                DetailShowDetailsSection(item = item)
                Spacer(modifier = Modifier.height(20.dp))

                // 8.5. Movie Franchise Collection (1:1 Nuvio Parity: strictly between Details and More Like This)
                if (item.mediaType == MediaType.MOVIE && uiState.collectionItems.isNotEmpty()) {
                    DetailCollectionSection(
                        collectionName = uiState.collectionName ?: "Collection",
                        items = uiState.collectionItems,
                        onSelectItem = { viewModel.openPreview(it) }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // 9. More Like This & Recommendations Section
                if (uiState.recommendations.isNotEmpty()) {
                    RecommendationsSection(
                        recommendations = uiState.recommendations,
                        onSelectRecommendation = { viewModel.openPreview(it) }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // PREVIEW MODE HERO ACTION: + Add to Library
                if (uiState.isPreviewMode) {
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

                Spacer(modifier = Modifier.height(48.dp))
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
            val headerAlpha = ((scrollState.value - 40f) / 160f).coerceIn(0f, 1f)
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
                        if (uiState.isPreviewMode || item?.id == -1L) {
                            viewModel.closePreview()
                        }
                        onBack()
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
                    var headerClearartFailed by remember(item?.clearartUrl) { mutableStateOf(false) }
                    val clearartUrl = item?.clearartUrl
                    if (!clearartUrl.isNullOrBlank() && !headerClearartFailed) {
                        AsyncImage(
                            model = clearartUrl,
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
                                if (item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME) {
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
                    onDismiss = { showArtworkPicker = false },
                    onCoverSelected = { newUrl ->
                        if (newUrl.startsWith("/") || newUrl.startsWith("file:")) {
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
    val effectiveCover = when (item.artworkRole) {
        "BANNER_AS_COVER" -> item.bannerUrl ?: item.coverUrl
        else -> item.coverUrl
    }
    val effectiveBanner = when {
        item.customBannerPath == "NONE" || item.artworkRole == "DISABLE_BANNER" -> null
        item.customBannerPath != null -> item.customBannerPath
        item.artworkRole == "COVER_AS_BANNER" -> item.coverUrl
        item.artworkRole == "BANNER_AS_COVER" -> item.bannerUrl ?: item.coverUrl
        else -> item.bannerUrl ?: item.coverUrl
    }
    val backdropTargetUrl = effectiveBanner

    val isCinematicBlur = presentationSettings.fallbackBackgroundStyle == FallbackBackgroundStyle.CINEMATIC_BLUR
    val backdropBitmap = rememberArtworkBackdrop(
        imageUrl = backdropTargetUrl,
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
                    onOptionsClick = {},
                    onEditProgress = {},
                    onEditStatus = {},
                    onEditScore = {},
                    onOpenTrackers = {}
                )
            } else {
                DetailClassicHeaderSection(
                    item = item,
                    coverImageModel = LosslessCoverMapper.toLosslessUrl(effectiveCover),
                    backdropTargetUrl = backdropTargetUrl,
                    onCoverClick = {},
                    onCoverLoaded = {},
                    onOptionsClick = {},
                    onEditProgress = {},
                    onEditStatus = {},
                    onEditScore = {},
                    onOpenTrackers = {}
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            DetailProgressSection(
                item = item,
                onUpdateProgress = {},
                onUpdatePlaytime = {},
                onTogglePlatform = {},
                onUpdateStatus = {}
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (!item.synopsis.isNullOrBlank()) {
                DetailSynopsisSection(
                    synopsis = item.synopsis,
                    dominantAccent = dominantAccent
                )
                Spacer(modifier = Modifier.height(20.dp))
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
            Text(
                text = item.mediaType.unitLabel.uppercase(),
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
            AsyncImage(
                model = backdropTargetUrl,
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
                var clearartFailed by remember(item.clearartUrl) { mutableStateOf(false) }
                val hasClearart = !item.clearartUrl.isNullOrBlank() && !clearartFailed

                if (hasClearart) {
                    AsyncImage(
                        model = item.clearartUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .height(52.dp)
                            .padding(bottom = 6.dp),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        onError = { clearartFailed = true }
                    )
                }
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = if (hasClearart) 20.sp else 24.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = if (hasClearart) 25.sp else 29.sp
                )
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
                    Text(
                        text = item.mediaType.displayName.uppercase(),
                        color = AccentArctic,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    if (item.platform != null) {
                        Text(text = " • ", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                        Text(text = item.platform, color = Color.White, fontSize = 11.5.sp)
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
        NuvioRatingsRow(item = item)
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
            AsyncImage(
                model = backdropTargetUrl,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 27.sp
                    )
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
            Text(
                text = item.mediaType.displayName.uppercase(),
                color = AccentArctic,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Black
            )
        }
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .offset(y = statsOffset)
    ) {
        Column {
            DetailNuvioMetaRow(item = item)
            NuvioRatingsRow(item = item)
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
    var synopsisExpanded by remember { mutableStateOf(false) }
    var canExpandSynopsis by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .animateContentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = canExpandSynopsis,
                onClick = { synopsisExpanded = !synopsisExpanded }
            )
    ) {
        Text(
            text = synopsis,
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
        if (canExpandSynopsis) {
            Text(
                text = if (synopsisExpanded) "Show less" else "Read more",
                color = dominantAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
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
