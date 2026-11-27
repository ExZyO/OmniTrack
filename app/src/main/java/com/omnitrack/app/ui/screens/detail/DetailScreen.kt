package com.omnitrack.app.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.ComicDetailMeta
import com.omnitrack.app.data.model.ComicFormat
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.BannerSizing
import com.omnitrack.app.data.preferences.DetailsPagePresentation
import com.omnitrack.app.data.preferences.FallbackBackgroundStyle
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.ui.components.ArtworkBackdrop
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.omnitrack.app.ui.components.CustomEntryEditorSheet
import com.omnitrack.app.ui.components.FanficTypographicCover
import com.omnitrack.app.ui.components.FanficWebViewSheet
import com.omnitrack.app.ui.components.FullScreenCoverViewerDialog
import com.omnitrack.app.ui.components.featherArtwork
import com.omnitrack.app.ui.components.focusedAlignment
import com.omnitrack.app.ui.components.prefetchArtworkBackdrop
import com.omnitrack.app.ui.components.rememberArtworkBackdrop
import com.omnitrack.app.ui.components.rememberBackdropFocusState
import com.omnitrack.app.ui.components.rememberCoverDominantColor
import com.omnitrack.app.ui.components.revealAlpha
import com.omnitrack.app.ui.components.shouldBlurFanficCover
import com.omnitrack.app.ui.screens.detail.components.CastMember
import com.omnitrack.app.ui.screens.detail.components.ChimanewProgressBottomSheet
import com.omnitrack.app.ui.screens.detail.components.ChimanewStatusBottomSheet
import com.omnitrack.app.ui.screens.detail.components.DetailCastSection
import com.omnitrack.app.ui.screens.detail.components.DetailCollectionSection
import com.omnitrack.app.ui.screens.detail.components.ComicCreatorCharacterSheet
import com.omnitrack.app.ui.screens.detail.components.DetailComicCreatorsCharactersSection
import com.omnitrack.app.ui.screens.detail.components.DetailComicTrackingSection
import com.omnitrack.app.ui.screens.detail.components.DetailFanficTrackingSection
import com.omnitrack.app.ui.screens.detail.components.DetailGeneralInfoSection
import com.omnitrack.app.ui.screens.detail.components.DetailExternalLinksSection
import com.omnitrack.app.ui.screens.detail.components.DetailGenresTagsSection
import com.omnitrack.app.ui.screens.detail.components.DetailNamesSynonymsSection
import com.omnitrack.app.ui.screens.detail.components.DetailNetworksSection
import com.omnitrack.app.ui.screens.detail.components.DetailPersonalNotesSection
import com.omnitrack.app.data.artwork.ArtworkModelResolver
import com.omnitrack.app.data.preferences.CustomSeriesSeasonConfig
import com.omnitrack.app.ui.screens.detail.components.DetailSeriesDialogs
import com.omnitrack.app.ui.screens.detail.components.DetailShowDetailsSection
import com.omnitrack.app.ui.screens.detail.components.DetailTrailersSection
import com.omnitrack.app.ui.screens.detail.components.SeasonMergeCustomizeSheet
import com.omnitrack.app.ui.screens.detail.components.detailSeriesItems
import com.omnitrack.app.ui.screens.detail.components.rememberDetailSeriesState
import com.omnitrack.app.ui.screens.detail.components.MultiTrackerSheet
import com.omnitrack.app.ui.screens.detail.components.NetworkProductionCompany
import com.omnitrack.app.ui.screens.detail.components.NuvioRatingsRow
import com.omnitrack.app.ui.screens.detail.components.RatingBadgeData
import com.omnitrack.app.ui.screens.detail.components.collectRatingBadgesForEntry
import com.omnitrack.app.ui.screens.detail.components.ratingSourceDisplayName
import com.omnitrack.app.ui.screens.detail.components.RecommendationItem
import com.omnitrack.app.ui.screens.detail.components.RecommendationsSection
import com.omnitrack.app.ui.screens.detail.components.ReviewsSection
import com.omnitrack.app.ui.screens.detail.components.UnifiedDiscussionSection
import com.omnitrack.app.ui.screens.detail.components.SeasonEpisodesSection
import com.omnitrack.app.ui.screens.detail.components.ScoreRatingSheet
import com.omnitrack.app.ui.screens.detail.components.statusColor
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import com.omnitrack.app.data.model.AnimeCharacterItem
import com.omnitrack.app.data.model.AnimeRelationItem
import com.omnitrack.app.data.model.AnimeStaffItem
import com.omnitrack.app.data.model.BookAuthorProfile
import com.omnitrack.app.data.model.BookDetailMeta
import com.omnitrack.app.data.model.BookFormat
import com.omnitrack.app.data.model.BookTrackingMode
import com.omnitrack.app.data.model.GameDetailMeta
import com.omnitrack.app.data.model.VnDetailMeta
import com.omnitrack.app.data.model.VnNsfwFilterMode
import com.omnitrack.app.data.model.VnSpoilerLevel
import com.omnitrack.app.data.model.formatBookDuration
import com.omnitrack.app.data.model.parseVndbBbCodeSegments
import com.omnitrack.app.data.preferences.DetailSectionKey
import com.omnitrack.app.data.preferences.EditionsSwitcherStyle
import com.omnitrack.app.data.enrichment.AwardWinnerEntry
import com.omnitrack.app.ui.components.FormattedRichText
import com.omnitrack.app.ui.components.LocalPerformanceSettings
import com.omnitrack.app.ui.components.heroScrollBackdropAlpha
import com.omnitrack.app.ui.components.heroScrollBackdropScale
import com.omnitrack.app.ui.components.heroStretchHeight
import com.omnitrack.app.ui.components.rememberHeroStretchState
import com.omnitrack.app.ui.screens.detail.components.AnimeCharacterSheet
import com.omnitrack.app.ui.screens.detail.components.AnimeStaffSheet
import com.omnitrack.app.ui.screens.detail.components.AuthorProfileBottomSheet
import com.omnitrack.app.ui.screens.detail.components.BookEntryDetailSheet
import com.omnitrack.app.ui.screens.detail.components.formatSmartRating
import com.omnitrack.app.ui.screens.detail.components.DetailBookAuthorSection
import com.omnitrack.app.ui.screens.detail.components.DetailBookEditionsSection
import com.omnitrack.app.ui.screens.detail.components.DetailBookGallerySection
import com.omnitrack.app.ui.screens.detail.components.DetailBookQuotesSection
import com.omnitrack.app.ui.screens.detail.components.DetailBookTrackingSection
import com.omnitrack.app.ui.screens.detail.components.DetailGameAchievementsSection
import com.omnitrack.app.ui.screens.detail.components.DetailGameGallerySection
import com.omnitrack.app.ui.screens.detail.components.DetailGameTrackingSection
import com.omnitrack.app.ui.screens.detail.components.DetailScreenshotsGallerySection
import com.omnitrack.app.ui.screens.detail.components.DetailVnReleasesSection
import com.omnitrack.app.ui.screens.detail.components.DetailVnRoutesSection
import com.omnitrack.app.ui.screens.detail.components.GameEntryDetailSheet
import com.omnitrack.app.ui.screens.detail.components.UserMediaListOverlay
import com.omnitrack.app.ui.screens.detail.components.UserProfileSheet
import com.omnitrack.app.ui.screens.detail.components.VnRouteDetailSheet
import com.omnitrack.app.ui.screens.detail.components.VolumeChapterDetailSheet
import com.omnitrack.app.ui.screens.detail.components.DetailAiringStatusLine
import com.omnitrack.app.ui.screens.detail.components.DetailAnimeCastSection
import com.omnitrack.app.ui.screens.detail.components.DetailAnimeStaffSection
import com.omnitrack.app.ui.screens.detail.components.DetailAwardsSection
import com.omnitrack.app.ui.screens.detail.components.DetailFranchiseSection
import com.omnitrack.app.ui.screens.detail.components.DetailWhereToWatchSection
import com.omnitrack.app.ui.screens.detail.components.AwardWinnersBrowserSheet
import com.omnitrack.app.ui.screens.detail.components.CrossMediumEditionsSection
import com.omnitrack.app.ui.screens.detail.components.LinkCrossMediumEditionSheet
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.launch

sealed class DetailSheetTarget {
    data class UserProfile(val username: String?, val userId: Int?) : DetailSheetTarget()
    data class UserMediaList(val userId: Int, val username: String, val isAnime: Boolean) : DetailSheetTarget()
    data class AnimeCharacter(val character: AnimeCharacterItem) : DetailSheetTarget()
    data class AnimeStaff(val staff: AnimeStaffItem) : DetailSheetTarget()
    data class Person(val person: CastMember) : DetailSheetTarget()
    data class Company(val company: NetworkProductionCompany) : DetailSheetTarget()
    data class VolumeChapterDetail(val seasonNumber: Int, val episodeNumber: Int) : DetailSheetTarget()
    data class VnRoute(val routeId: Long) : DetailSheetTarget()
    data class GameEntry(val entryId: Long) : DetailSheetTarget()
    data class BookEntry(val entryId: Long) : DetailSheetTarget()
    data class BookAuthor(val profile: BookAuthorProfile) : DetailSheetTarget()
    data class ComicCreatorOrCharacter(
        val name: String,
        val role: String,
        val imageUrl: String?,
        val description: String? = null
    ) : DetailSheetTarget()
}

private sealed class DetailNavStep {
    data class Sheet(val target: DetailSheetTarget) : DetailNavStep()
    data class Preview(val token: Long) : DetailNavStep()
    data class LibraryEntry(val previousItem: MediaItemEntity) : DetailNavStep()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onSearchInDiscover: (String) -> Unit = {},
    onSearchInLibrary: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val app = remember(context) {
        context.applicationContext as com.omnitrack.app.OmniTrackApplication
    }
    val prefManager = app.preferenceManager
    val allCategories by app.repository.getAllCategories().collectAsState(initial = emptyList())
    val allLibrarySummaries by app.repository.getAllMediaSummaries().collectAsState(initial = emptyList())
    val categoryAssignments by prefManager.categoryAssignments.collectAsState(initial = emptyMap())
    val presentationSettings by prefManager.presentationSettings.collectAsState(initial = PreferenceManager.cachedPresentationSettings)
    val episodeLayoutMode by prefManager.episodeLayoutMode.collectAsState(initial = com.omnitrack.app.data.preferences.EpisodeLayoutMode.HORIZONTAL)
    val episodeLayoutScale by prefManager.episodeLayoutScale.collectAsState(initial = 1.0f)
    val seasonViewMode by prefManager.seasonViewMode.collectAsState(initial = "Posters")
    val customPosterPattern by prefManager.customPosterUrlPattern.collectAsState(initial = "")
    val vnSpoilerLevel by prefManager.vnSpoilerLevel.collectAsState(initial = VnSpoilerLevel.MINOR)
    val vnNsfwFilterMode by prefManager.vnNsfwFilterMode.collectAsState(initial = VnNsfwFilterMode.BLUR_TAP)
    val hiddenRatingSourcesMap by prefManager.hiddenRatingSourcesMap.collectAsState(initial = emptyMap())
    var showVnPlaytimeDialog by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val fanficAuxState by viewModel.fanficAuxState.collectAsState()
    var activeFanficWebView by remember {
        mutableStateOf<Triple<String, com.omnitrack.app.data.model.FanficSite, com.omnitrack.app.data.db.entity.FanficChapterEntity?>?>(null)
    }
    val isCommentsLoading by viewModel.isCommentsLoading.collectAsState()
    val previewEpisodeMeta by viewModel.previewEpisodeMeta.collectAsState()
    val ranobeBookDetails by viewModel.ranobeBookDetails.collectAsState()
    val isTransitionLoading by viewModel.isTransitionLoading.collectAsState()
    val revealGeneration by viewModel.revealGeneration.collectAsState()
    val seasonMetaMap by viewModel.seasonMetaMap.collectAsState()
    val crossMediumEditions by viewModel.crossMediumEditions.collectAsState()
    val allLibraryMedia by viewModel.allLibraryMedia.collectAsState()
    val watchProviders by viewModel.watchProviders.collectAsState()
    val isLoadingWatchProviders by viewModel.isLoadingWatchProviders.collectAsState()
    val awards by viewModel.awards.collectAsState()
    val awardWinnersSheetState by viewModel.awardWinnersSheetState.collectAsState()
    val watchRegionPref by prefManager.watchProviderRegion.collectAsState(initial = PreferenceManager.cachedWatchProviderRegion)
    val editionsSwitcherStyle by prefManager.editionsSwitcherStyle.collectAsState(initial = EditionsSwitcherStyle.SMART_LABELS)
    val customEditionLabels by prefManager.customEditionLabels.collectAsState(initial = emptyMap())
    val customSeriesSeasonConfigs by prefManager.customSeriesSeasonConfigs.collectAsState(initial = emptyMap())
    val siblingSeasonEntities by viewModel.siblingSeasonEntities.collectAsState()
    val mergedEpisodeMeta = remember(uiState.episodeMetaMap, previewEpisodeMeta) {
        uiState.episodeMetaMap + previewEpisodeMeta
    }

    val item = uiState.item
    LaunchedEffect(item?.id, item?.tmdbId, item?.imdbId, item?.title, watchRegionPref) {
        item?.let { viewModel.loadWatchProvidersAndAwards(it, watchRegionPref) }
    }
    val customCoverUrl = remember(item, customPosterPattern) {
        if (item != null && customPosterPattern.isNotBlank()) {
            com.omnitrack.app.data.poster.CustomPosterUrlResolver.resolveForMediaItem(item, customPosterPattern, isLandscape = false)
        } else null
    }
    var customCoverFailed by remember(customCoverUrl) { mutableStateOf(false) }

    val customBannerUrl = remember(item, customPosterPattern) {
        if (item != null && customPosterPattern.isNotBlank() && "{shape}" in customPosterPattern) {
            com.omnitrack.app.data.poster.CustomPosterUrlResolver.resolveForMediaItem(item, customPosterPattern, isLandscape = true)
        } else null
    }
    var customBannerFailed by remember(customBannerUrl) { mutableStateOf(false) }

    // Dynamic Artwork Role Resolution (Manual user artwork selection strictly overrides templates)
    val effectiveCover = remember(item?.customCoverPath, item?.artworkRole, item?.coverUrl, item?.bannerUrl, customCoverUrl, customCoverFailed) {
        if (item == null) "" else {
            item.customCoverPath
                ?: when (item.artworkRole) {
                    "BANNER_AS_COVER" -> item.bannerUrl ?: item.coverUrl
                    else -> if (item.coverUrl.isNotBlank()) item.coverUrl else (if (!customCoverFailed) customCoverUrl ?: "" else "")
                }
        }
    }
    val effectiveBanner = remember(item?.customBannerPath, item?.artworkRole, item?.coverUrl, item?.bannerUrl, customBannerUrl, customBannerFailed) {
        when {
            item == null -> null
            item.customBannerPath == "NONE" || item.artworkRole == "DISABLE_BANNER" -> null
            !item.customBannerPath.isNullOrBlank() -> item.customBannerPath
            !customBannerFailed && customBannerUrl != null -> customBannerUrl
            item.artworkRole == "COVER_AS_BANNER" -> item.coverUrl
            item.artworkRole == "BANNER_AS_COVER" -> item.bannerUrl?.takeUnless { ArtworkModelResolver.isAutoScreenshotUrl(it) }
            else -> item.bannerUrl?.takeUnless { ArtworkModelResolver.isAutoScreenshotUrl(it) }
        }
    }
    val rootSeasonId = remember(item?.id, item?.linkedParentMediaId) {
        item?.let { viewModel.rootSeasonGroupId(it) } ?: -1L
    }
    val activeSeasonConfig = remember(customSeriesSeasonConfigs, rootSeasonId) {
        if (rootSeasonId > 0L) customSeriesSeasonConfigs[rootSeasonId] ?: CustomSeriesSeasonConfig()
        else CustomSeriesSeasonConfig()
    }
    val allCustomizedSeasons = remember(uiState.realSeasons, activeSeasonConfig) {
        val raw = uiState.realSeasons
        if (raw.isEmpty()) emptyList()
        else {
            val mapped = raw.map { s ->
                val cName = activeSeasonConfig.customNames[s.seasonNumber]?.takeIf { it.isNotBlank() }
                val cPoster = activeSeasonConfig.customPosters[s.seasonNumber]?.takeIf { it.isNotBlank() }
                s.copy(
                    name = cName ?: s.name,
                    posterUrl = cPoster ?: s.posterUrl
                )
            }
            if (activeSeasonConfig.seasonOrder.isEmpty()) {
                mapped
            } else {
                val orderMap = activeSeasonConfig.seasonOrder.withIndex().associate { (i, num) -> num to i }
                mapped.sortedWith(
                    compareBy<com.omnitrack.app.data.model.MediaSeason> { orderMap[it.seasonNumber] ?: Int.MAX_VALUE }
                        .thenBy { if (it.seasonNumber == 0) Int.MAX_VALUE else it.seasonNumber }
                )
            }
        }
    }
    val visibleSeasons = remember(allCustomizedSeasons, activeSeasonConfig.hiddenSeasons) {
        if (activeSeasonConfig.hiddenSeasons.isEmpty()) allCustomizedSeasons
        else allCustomizedSeasons.filter { it.seasonNumber !in activeSeasonConfig.hiddenSeasons }
            .ifEmpty { allCustomizedSeasons.take(1) }
    }
    val hiddenSeasonsCount = remember(allCustomizedSeasons, visibleSeasons) {
        (allCustomizedSeasons.size - visibleSeasons.size).coerceAtLeast(0)
    }
    val canCustomizeSeasons = !uiState.isPreviewMode &&
        (item?.id ?: 0L) > 0L &&
        item?.mediaType in setOf(MediaType.ANIME, MediaType.SHOW, MediaType.MOVIE)

    LaunchedEffect(visibleSeasons, uiState.selectedSeasonNumber) {
        if (visibleSeasons.isNotEmpty() && visibleSeasons.none { it.seasonNumber == uiState.selectedSeasonNumber }) {
            viewModel.selectSeason(visibleSeasons.first().seasonNumber)
        }
    }
    val isReadingType = item?.mediaType == MediaType.MANGA || item?.mediaType == MediaType.NOVEL
    val isLightNovelVolumeMode = item?.mediaType == MediaType.NOVEL && uiState.isLightNovelMode
    val fallbackSeriesCover = effectiveCover.takeIf { it.isNotBlank() } ?: item?.coverUrl

    val isBannerExplicitlyDisabled = item?.customBannerPath == "NONE" || item?.artworkRole == "DISABLE_BANNER"
    val isCinematicBlur = presentationSettings.fallbackBackgroundStyle == FallbackBackgroundStyle.CINEMATIC_BLUR
    val backdropBitmap = rememberArtworkBackdrop(
        imageUrl = if (isBannerExplicitlyDisabled) null else (effectiveBanner ?: item?.coverUrl),
        enabled = isCinematicBlur && !isBannerExplicitlyDisabled
    )

    val coroutineScope = rememberCoroutineScope()
    var showArtworkPicker by remember { mutableStateOf(false) }
    var showFullScreenCover by remember { mutableStateOf(false) }
    var showTitleActionSheet by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showEditEntrySheet by remember { mutableStateOf(false) }

    var showProgressEditSheet by remember { mutableStateOf(false) }
    var showStatusPickerSheet by remember { mutableStateOf(false) }
    var showScoreRatingSheet by remember { mutableStateOf(false) }
    var showMultiTrackerSheet by remember { mutableStateOf(false) }
    var pendingHideRatingBadge by remember { mutableStateOf<RatingBadgeData?>(null) }
    var showManageRatingsSheet by remember { mutableStateOf(false) }
    var showLinkEditionSheet by remember { mutableStateOf(false) }
    var showManageSeasonsSheet by remember { mutableStateOf(false) }
    val entryRatingKey = remember(
        item?.id, item?.mediaType, item?.title, item?.tmdbId, item?.anilistId, item?.malId,
        item?.igdbId, item?.steamAppId, item?.vndbId, item?.hardcoverId, item?.comicVineId
    ) {
        item?.let { PreferenceManager.ratingVisibilityKey(it) } ?: ""
    }
    val hiddenRatingSources = hiddenRatingSourcesMap[entryRatingKey] ?: emptySet()
    val navHistory = remember { mutableStateListOf<DetailNavStep>() }

    // 1:1 Nuvio Parity: Defer heavy off-screen sections (Cast, Trailers, Reviews, Recommendations)
    // by 200ms until navigation entrance transition finishes, eliminating frame drops & stutter.
    // Once Nuvio transition loading completes, reveal all sections together in a single cohesive fade-in.
    var deferredSectionsAllowed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        deferredSectionsAllowed = true
    }
    val sectionsReady = deferredSectionsAllowed || revealGeneration > 0

    val lazyListState = rememberLazyListState()
    val isAtTopOfList = {
        lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
    }
    val latestOnBack by rememberUpdatedState(onBack)

    val pushSheet: (DetailSheetTarget) -> Unit = { target ->
        viewModel.saveScrollOffset(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
        navHistory.add(DetailNavStep.Sheet(target))
    }

    val pushPreview: (RecommendationItem) -> Unit = { recItem ->
        viewModel.saveScrollOffset(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
        navHistory.add(DetailNavStep.Preview(System.nanoTime()))
        viewModel.openPreview(recItem)
        coroutineScope.launch { lazyListState.scrollToItem(0) }
    }

    val pushRelationPreview: (AnimeRelationItem) -> Unit = { relation ->
        viewModel.saveScrollOffset(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
        navHistory.add(DetailNavStep.Preview(System.nanoTime()))
        viewModel.openRelationPreview(relation)
        coroutineScope.launch { lazyListState.scrollToItem(0) }
    }

    val pushLibraryEntry: (MediaItemEntity) -> Unit = { target ->
        val current = uiState.item
        if (current != null && current.id > 0L && current.id != target.id) {
            viewModel.saveScrollOffset(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
            navHistory.add(DetailNavStep.LibraryEntry(current))
        }
        viewModel.switchToCrossMediumEdition(target)
        coroutineScope.launch { lazyListState.scrollToItem(0) }
    }

    val stepBackOneLevel: () -> Unit = {
        if (navHistory.isNotEmpty()) {
            when (val step = navHistory.removeAt(navHistory.lastIndex)) {
                is DetailNavStep.Sheet -> {
                    val restoredOffset = viewModel.popScrollOffset()
                    if (restoredOffset != null) {
                        coroutineScope.launch { lazyListState.scrollToItem(restoredOffset.first, restoredOffset.second) }
                    }
                }
                is DetailNavStep.Preview -> {
                    val handled = viewModel.closePreview()
                    if (!handled) {
                        latestOnBack()
                    } else {
                        val restoredOffset = viewModel.popScrollOffset()
                        if (restoredOffset != null) {
                            coroutineScope.launch { lazyListState.scrollToItem(restoredOffset.first, restoredOffset.second) }
                        }
                    }
                }
                is DetailNavStep.LibraryEntry -> {
                    viewModel.switchToCrossMediumEdition(step.previousItem)
                    val restoredOffset = viewModel.popScrollOffset()
                    if (restoredOffset != null) {
                        coroutineScope.launch { lazyListState.scrollToItem(restoredOffset.first, restoredOffset.second) }
                    } else {
                        coroutineScope.launch { lazyListState.scrollToItem(0) }
                    }
                }
            }
        } else if (uiState.isPreviewMode || viewModel.isPreviewActive()) {
            val handled = viewModel.closePreview()
            if (!handled) {
                latestOnBack()
            } else {
                val restoredOffset = viewModel.popScrollOffset()
                if (restoredOffset != null) {
                    coroutineScope.launch { lazyListState.scrollToItem(restoredOffset.first, restoredOffset.second) }
                }
            }
        } else {
            latestOnBack()
        }
    }

    val exitAllToRoot: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        navHistory.clear()
        viewModel.clearAllPreviews()
        latestOnBack()
    }

    val latestStepBack by rememberUpdatedState(stepBackOneLevel)

    BackHandler(enabled = navHistory.isNotEmpty() || uiState.isPreviewMode || viewModel.isPreviewActive()) {
        stepBackOneLevel()
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
                    latestStepBack()
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
        val sibBanner = if (sibling.customBannerPath == "NONE" || sibling.artworkRole == "DISABLE_BANNER") null
        else (sibling.customBannerPath?.takeIf { it.isNotBlank() } ?: sibling.bannerUrl?.takeUnless { ArtworkModelResolver.isAutoScreenshotUrl(it) })
        val sibCover = sibling.customCoverPath ?: if (sibling.artworkRole == "BANNER_AS_COVER") (sibling.bannerUrl ?: sibling.coverUrl) else sibling.coverUrl
        rememberArtworkBackdrop(imageUrl = sibBanner ?: sibCover, enabled = isCinematicBlur)
        rememberCoverDominantColor(LosslessCoverMapper.toLosslessUrl(sibCover))
        rememberCoverDominantColor(sibBanner)
    }

    LaunchedEffect(uiState.previousItem?.id, uiState.nextItem?.id, isCinematicBlur) {
        val loader = coil3.SingletonImageLoader.get(context)
        val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
        listOfNotNull(uiState.previousItem, uiState.nextItem).forEach { sibling ->
            val sibBanner = if (sibling.customBannerPath == "NONE" || sibling.artworkRole == "DISABLE_BANNER") null
            else (sibling.customBannerPath?.takeIf { it.isNotBlank() } ?: sibling.bannerUrl?.takeUnless { ArtworkModelResolver.isAutoScreenshotUrl(it) })
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

    // Chimanew optimization: pre-decode active cover & banner immediately (including while Nuvio spinner runs)
    LaunchedEffect(item?.id, effectiveCover, effectiveBanner) {
        if (item == null) return@LaunchedEffect
        val loader = coil3.SingletonImageLoader.get(context)
        if (!effectiveBanner.isNullOrBlank()) {
            val bannerData: Any = if (effectiveBanner.startsWith("/") || effectiveBanner.startsWith("content:") || effectiveBanner.startsWith("file:")) {
                if (effectiveBanner.startsWith("/")) java.io.File(effectiveBanner) else effectiveBanner
            } else effectiveBanner
            loader.enqueue(ImageRequest.Builder(context).data(bannerData).crossfade(false).build())
        }
        if (effectiveCover.isNotBlank()) {
            val coverData: Any = item.customCoverPath?.let {
                if (it.startsWith("http://") || it.startsWith("https://")) it else java.io.File(it)
            } ?: LosslessCoverMapper.toLosslessUrl(effectiveCover)
            loader.enqueue(ImageRequest.Builder(context).data(coverData).crossfade(false).build())
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
                    presentationSettings = presentationSettings,
                    customPosterPattern = customPosterPattern
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
                    presentationSettings = presentationSettings,
                    customPosterPattern = customPosterPattern
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
                                // Swiping right past threshold when at start of single-entry list: step back one level
                                currentX > 180f && !uiState.hasPreviousEntry -> {
                                    coroutineScope.launch {
                                        animatableOffsetX.animateTo(
                                            targetValue = screenWidthPx,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                        latestStepBack()
                                        animatableOffsetX.snapTo(0f)
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
            if ((uiState.isLoading && item == null) || isTransitionLoading) {
                Box(Modifier.fillMaxSize().background(AmoledBlack)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        com.omnitrack.app.ui.components.NuvioLoadingIndicator(size = 44.dp)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { stepBackOneLevel() },
                                onLongClick = { exitAllToRoot() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else if (item != null) {
            val coverCache = remember { (context.applicationContext as? com.omnitrack.app.OmniTrackApplication)?.coverCache }
            val coverImageModel = remember(effectiveCover, item.customCoverPath) {
                item.customCoverPath?.let { if (it.startsWith("http://") || it.startsWith("https://")) it else java.io.File(it) }
                    ?: LosslessCoverMapper.toLosslessUrl(effectiveCover)
            }

            val losslessCover = remember(effectiveCover) { LosslessCoverMapper.toLosslessUrl(effectiveCover) }
            val dominantAccent = rememberCoverDominantColor(losslessCover)
            val backdropTargetUrl = effectiveBanner

            val currentClearart = item.clearartUrl?.takeIf { it.isNotBlank() }
                ?: com.omnitrack.app.data.enrichment.EnrichmentRepository.getCachedClearart(item)
            LaunchedEffect(currentClearart) {
                if (!currentClearart.isNullOrBlank()) {
                    val req = ImageRequest.Builder(context).data(currentClearart).crossfade(false).build()
                    coil3.SingletonImageLoader.get(context).enqueue(req)
                }
            }

            val seriesUiState = rememberDetailSeriesState(
                itemKey = item.id.takeIf { it > 0 } ?: item.title,
                initialSeasonViewMode = seasonViewMode,
                layoutMode = episodeLayoutMode
            )

            Box(
                Modifier.fillMaxSize().detailsContentReveal(
                    enabled = revealGeneration > 0,
                    revealKey = revealGeneration
                )
            ) {
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

            val perfSettings = LocalPerformanceSettings.current
            val heroStretchState = rememberHeroStretchState(lazyListState)
            val animateHeroScroll = !perfSettings.disableCardAnimations
            val detailScrollPx: () -> Float = {
                if (!animateHeroScroll) 0f
                else if (lazyListState.firstVisibleItemIndex > 0) 1400f
                else lazyListState.firstVisibleItemScrollOffset.toFloat()
            }
            val supportsFanficPullRefresh = item.mediaType == MediaType.FANFICTION ||
                (item.mediaType == MediaType.NOVEL && !item.fanficSourceUrl.isNullOrBlank())
            val detailStretchPx: () -> Float = {
                if (!animateHeroScroll || supportsFanficPullRefresh) 0f else heroStretchState.stretchPx
            }

            val detailScrollBody: @Composable () -> Unit = {
                val hasAuthenticBanner = hasAuthenticDetailBanner(item, effectiveCover, backdropTargetUrl)
                val effectiveDetailLayout = when {
                    presentationSettings.detailsPagePresentation == DetailsPagePresentation.CENTERED_COVER -> DetailsPagePresentation.CENTERED_COVER
                    presentationSettings.autoCenterCoverWhenNoBanner && !hasAuthenticBanner -> DetailsPagePresentation.CENTERED_COVER
                    else -> presentationSettings.detailsPagePresentation
                }
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                item(key = "detail_top_header", contentType = "top_header") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // PRESENTATION MODES: HERO (centered, no cover), CENTERED_COVER (centered cover, no banner), CLASSIC (left poster + right text)
                        when (effectiveDetailLayout) {
                            DetailsPagePresentation.CENTERED_COVER -> {
                                DetailCenteredCoverHeroSection(
                                    item = item,
                                    coverImageModel = coverImageModel,
                                    novelDetailMeta = uiState.novelDetailMeta,
                                    vnDetailMeta = uiState.vnDetailMeta,
                                    gameDetailMeta = uiState.gameDetailMeta,
                                    bookDetailMeta = uiState.bookDetailMeta,
                                    comicDetailMeta = uiState.comicDetailMeta,
                                    hiddenRatingSources = hiddenRatingSources,
                                    onLongPressRatingBadge = { pendingHideRatingBadge = it },
                                    showInlineOptionsButton = false,
                                    onCoverClick = { showFullScreenCover = true },
                                    onCoverLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showArtworkPicker = true
                                    },
                                    onToggleTitle = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showTitleActionSheet = true
                                    },
                                    onSearchInDiscover = onSearchInDiscover,
                                    onOptionsClick = { showOptionsMenu = true },
                                    onEditProgress = {
                                        if (item.mediaType == MediaType.VISUAL_NOVEL || item.mediaType == MediaType.GAME) {
                                            showVnPlaytimeDialog = true
                                        } else {
                                            showProgressEditSheet = true
                                        }
                                    },
                                    onEditStatus = { showStatusPickerSheet = true },
                                    onEditScore = { showScoreRatingSheet = true },
                                    onOpenTrackers = { showMultiTrackerSheet = true },
                                    isPreviewMode = uiState.isPreviewMode,
                                    onAddToLibrary = {
                                        navHistory.clear()
                                        viewModel.addToLibrary(item)
                                    }
                                )
                            }
                            DetailsPagePresentation.HERO -> {
                                DetailHeroHeaderSection(
                                    item = item,
                                    backdropTargetUrl = backdropTargetUrl,
                                    bannerSizing = presentationSettings.bannerSizing,
                                    bannerBlurDp = presentationSettings.resolveBannerBlurDp(item.mediaType),
                                    englishTitle = uiState.englishTitle,
                                    romajiTitle = uiState.romajiTitle,
                                    heroClearLogoUrl = uiState.heroClearLogoUrl,
                                    novelDetailMeta = uiState.novelDetailMeta,
                                    vnDetailMeta = uiState.vnDetailMeta,
                                    gameDetailMeta = uiState.gameDetailMeta,
                                    bookDetailMeta = uiState.bookDetailMeta,
                                    comicDetailMeta = uiState.comicDetailMeta,
                                    hiddenRatingSources = hiddenRatingSources,
                                    onLongPressRatingBadge = { pendingHideRatingBadge = it },
                                    scrollOffsetPx = detailScrollPx,
                                    stretchPx = detailStretchPx,
                                    animateOnScroll = animateHeroScroll,
                                    showInlineOptionsButton = false,
                                    onToggleTitle = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showTitleActionSheet = true
                                    },
                                    onSearchInDiscover = onSearchInDiscover,
                                    onOptionsClick = { showOptionsMenu = true },
                                    onEditProgress = {
                                        if (item.mediaType == MediaType.VISUAL_NOVEL || item.mediaType == MediaType.GAME) {
                                            showVnPlaytimeDialog = true
                                        } else {
                                            showProgressEditSheet = true
                                        }
                                    },
                                    onEditStatus = { showStatusPickerSheet = true },
                                    onEditScore = { showScoreRatingSheet = true },
                                    onOpenTrackers = { showMultiTrackerSheet = true },
                                    isPreviewMode = uiState.isPreviewMode,
                                    onAddToLibrary = {
                                        navHistory.clear()
                                        viewModel.addToLibrary(item)
                                    }
                                )
                            }
                            DetailsPagePresentation.CLASSIC -> {
                                DetailClassicHeaderSection(
                                    item = item,
                                    coverImageModel = coverImageModel,
                                    backdropTargetUrl = backdropTargetUrl,
                                    bannerBlurDp = presentationSettings.resolveBannerBlurDp(item.mediaType),
                                    englishTitle = uiState.englishTitle,
                                    romajiTitle = uiState.romajiTitle,
                                    heroClearLogoUrl = uiState.heroClearLogoUrl,
                                    novelDetailMeta = uiState.novelDetailMeta,
                                    vnDetailMeta = uiState.vnDetailMeta,
                                    gameDetailMeta = uiState.gameDetailMeta,
                                    bookDetailMeta = uiState.bookDetailMeta,
                                    comicDetailMeta = uiState.comicDetailMeta,
                                    hiddenRatingSources = hiddenRatingSources,
                                    onLongPressRatingBadge = { pendingHideRatingBadge = it },
                                    scrollOffsetPx = detailScrollPx,
                                    stretchPx = detailStretchPx,
                                    animateOnScroll = animateHeroScroll,
                                    onToggleTitle = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showTitleActionSheet = true
                                    },
                                    onSearchInDiscover = onSearchInDiscover,
                                    onCoverClick = { showFullScreenCover = true },
                                    onCoverLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showArtworkPicker = true
                                    },
                                    onCoverLoaded = { url ->
                                        if (coverCache != null) {
                                            val diskCache = coil3.SingletonImageLoader.get(context).diskCache
                                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                coverCache.promoteFromDiskCache(url, diskCache)
                                            }
                                        }
                                    },
                                    onOptionsClick = { showOptionsMenu = true },
                                    onEditProgress = {
                                        if (item.mediaType == MediaType.VISUAL_NOVEL || item.mediaType == MediaType.GAME) {
                                            showVnPlaytimeDialog = true
                                        } else {
                                            showProgressEditSheet = true
                                        }
                                    },
                                    onEditStatus = { showStatusPickerSheet = true },
                                    onEditScore = { showScoreRatingSheet = true },
                                    onOpenTrackers = { showMultiTrackerSheet = true },
                                    isPreviewMode = uiState.isPreviewMode,
                                    onAddToLibrary = {
                                        navHistory.clear()
                                        viewModel.addToLibrary(item)
                                    }
                                )
                            }
                        }

                        // 1. Actions / Progress Section (Only rendered for tracked media types with top progress cards)
                        val hasProgressControls = !uiState.isPreviewMode && item.mediaType == MediaType.MOVIE
                        val hasBackdrop = backdropTargetUrl != null && effectiveDetailLayout != DetailsPagePresentation.CENTERED_COVER
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
                            Spacer(modifier = Modifier.height(if (hasBackdrop) (-12).dp else 8.dp))
                        }

                        // 2. Overview (Seamless Synopsis with bidirectional overflow-aware toggle)
                        if (!item.synopsis.isNullOrBlank()) {
                            DetailSynopsisSection(
                                synopsis = item.synopsis,
                                dominantAccent = dominantAccent,
                                isVisualNovel = item.mediaType == MediaType.VISUAL_NOVEL,
                                vnSpoilerLevel = vnSpoilerLevel
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Collapsible IGDB Storyline (for Games)
                        val gameStoryline = uiState.gameDetailMeta.storyline?.takeIf { it.isNotBlank() }
                        if (item.mediaType == MediaType.GAME && gameStoryline != null) {
                            var showStoryline by remember(item.id, gameStoryline) { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (showStoryline) "Hide Storyline ▴" else "Show Storyline ▾",
                                    color = AccentGold,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { showStoryline = !showStoryline }
                                        .padding(vertical = 2.dp)
                                )
                                if (showStoryline) {
                                    Text(
                                        text = gameStoryline,
                                        color = TextSecondary,
                                        fontSize = 13.5.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }

                // 3. Dynamic Reorderable Sections (configured in Settings > Layout > Details Page)
                val enabledSections = uiState.sectionOrder.filter { it.isEnabled }
                enabledSections.forEach { sectionItem ->
                    when (sectionItem.key) {
                        DetailSectionKey.PERSONAL_NOTES -> {
                            item(key = "section_personal_notes", contentType = "personal_notes") {
                                DetailPersonalNotesSection(
                                    item = item,
                                    onUpdatePersonalLog = { notes, isFav, started, finished, rewatch ->
                                        viewModel.updatePersonalLog(notes, isFav, started, finished, rewatch)
                                    },
                                    onOpenTrackingStudio = { showMultiTrackerSheet = true },
                                    modifier = Modifier.padding(bottom = 18.dp)
                                )
                            }
                        }
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
                            if (item.mediaType == MediaType.VISUAL_NOVEL) {
                                item(key = "section_vn_routes", contentType = "vn_routes") {
                                    Column {
                                        DetailVnRoutesSection(
                                            item = item,
                                            vnMeta = uiState.vnDetailMeta,
                                            routes = uiState.vnRoutes,
                                            spoilerLevel = vnSpoilerLevel,
                                            onStepPlaytime = { viewModel.stepVnPlaytime(it) },
                                            onSetExactPlaytime = { viewModel.setVnPlaytimeExact(it) },
                                            onToggleRouteCompleted = { viewModel.toggleVnRouteCompleted(it) },
                                            onAddCustomRoute = { name, type, hours, spoiler, photo ->
                                                viewModel.addCustomVnRoute(name, type, hours, spoiler, photo)
                                            },
                                            onUpdateRoutePhoto = { route, photo ->
                                                viewModel.saveVnRouteDetails(
                                                    route.copy(
                                                        characterImageUrl = photo.imageUrl,
                                                        imageFocalX = photo.focalX,
                                                        imageFocalY = photo.focalY,
                                                        imageZoom = photo.zoom
                                                    )
                                                )
                                            },
                                            onOpenRouteDetail = { route ->
                                                pushSheet(DetailSheetTarget.VnRoute(route.id))
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            } else if (item.mediaType == MediaType.GAME) {
                                item(key = "section_game_tracking", contentType = "game_tracking") {
                                    Column {
                                        DetailGameTrackingSection(
                                            item = item,
                                            gameMeta = uiState.gameDetailMeta,
                                            entries = uiState.gameEntries,
                                            onStepPlaytime = { viewModel.stepGamePlaytime(it) },
                                            onSetExactPlaytime = { viewModel.setGamePlaytimeExact(it) },
                                            onSelectCompletionTier = { viewModel.updateGameCompletionTier(it) },
                                            onTogglePlatform = { viewModel.togglePlatform(it) },
                                            onToggleEntryCompleted = { viewModel.toggleGameEntryCompleted(it) },
                                            onAddCustomEntry = { name, type ->
                                                viewModel.addCustomGameEntry(name, type)
                                            },
                                            onOpenEntryDetail = { entry ->
                                                pushSheet(DetailSheetTarget.GameEntry(entry.id))
                                            },
                                            onSyncSteam = {
                                                viewModel.syncSteamForCurrentGame { msg ->
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            } else if (item.mediaType == MediaType.BOOK) {
                                item(key = "section_book_tracking", contentType = "book_tracking") {
                                    Column {
                                        DetailBookTrackingSection(
                                            item = item,
                                            bookDetailMeta = uiState.bookDetailMeta,
                                            entries = uiState.bookEntries,
                                            onStepProgress = { viewModel.stepBookProgress(it) },
                                            onSetProgressExact = { valNum, totOpt ->
                                                viewModel.setBookProgressExact(valNum, totOpt)
                                            },
                                            onSwitchMode = { viewModel.switchBookTrackingMode(it) },
                                            onUpdateFormat = { viewModel.updateBookFormat(it) },
                                            onUpdateAudioMinutes = { curM, totM ->
                                                viewModel.updateBookAudioMinutes(curM, totM)
                                            },
                                            onToggleEntryCompleted = { viewModel.toggleBookEntryCompleted(it) },
                                            onAddEntry = { name, type ->
                                                viewModel.addCustomBookEntry(name, type)
                                            },
                                            onOpenEntryDetail = { entry ->
                                                pushSheet(DetailSheetTarget.BookEntry(entry.id))
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            } else if (item.mediaType == MediaType.COMIC) {
                                item(key = "section_comic_tracking", contentType = "comic_tracking") {
                                    Column {
                                        DetailComicTrackingSection(
                                            item = item,
                                            comicDetailMeta = uiState.comicDetailMeta,
                                            issues = uiState.comicIssues,
                                            onStepProgress = { viewModel.stepComicProgress(it) },
                                            onSetProgressExact = { curVal, totOpt ->
                                                viewModel.setComicProgressExact(curVal, totOpt)
                                            },
                                            onSwitchMode = { viewModel.switchComicTrackingMode(it) },
                                            onUpdateFormat = { viewModel.updateComicFormat(it) },
                                            onToggleIssueRead = { viewModel.toggleComicIssueRead(it) },
                                            onAddCustomIssue = { num, ttl, typ, arc ->
                                                viewModel.addCustomComicIssue(num, ttl, typ, arc)
                                            },
                                            onDeleteIssue = { viewModel.deleteComicIssue(it) },
                                            onSeedIssues = { count -> viewModel.seedComicIssuesRange(count) },
                                            episodeLayoutMode = episodeLayoutMode,
                                            onCycleLayoutMode = {
                                                val nextMode = if (episodeLayoutMode == com.omnitrack.app.data.preferences.EpisodeLayoutMode.TEXT_ONLY) {
                                                    com.omnitrack.app.data.preferences.EpisodeLayoutMode.LIST
                                                } else {
                                                    com.omnitrack.app.data.preferences.EpisodeLayoutMode.TEXT_ONLY
                                                }
                                                coroutineScope.launch {
                                                    prefManager.updateEpisodeLayoutMode(nextMode)
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            } else if (item.mediaType == MediaType.FANFICTION ||
                                (item.mediaType == MediaType.NOVEL && !item.fanficSourceUrl.isNullOrBlank())
                            ) {
                                item(key = "section_fanfic_tracking", contentType = "fanfic_tracking") {
                                    val effectiveChapters = if (item.id > 0L) uiState.fanficChapters else fanficAuxState.previewChapters
                                    Column {
                                        DetailFanficTrackingSection(
                                            item = item,
                                            chapters = effectiveChapters,
                                            isCheckingUpdates = fanficAuxState.isCheckingUpdates,
                                            updateBannerMessage = fanficAuxState.updateMessage,
                                            onCheckUpdates = { viewModel.checkFanficUpdates() },
                                            onLinkMirrorUrl = { viewModel.linkFanficMirrorUrl(it) },
                                            onRemoveMirror = { viewModel.removeFanficMirror(it) },
                                            onToggleChapterRead = { viewModel.toggleFanficChapterRead(it) },
                                            onMarkReadUpTo = { viewModel.markFanficChaptersReadUpTo(it) },
                                            onMarkAllChaptersRead = { viewModel.markAllFanficChaptersRead(it) },
                                            onToggleChapterBookmark = { viewModel.toggleFanficChapterBookmark(it) },
                                            onUpdateChapterNote = { ch, note, rating ->
                                                viewModel.updateFanficChapterNote(ch, note, rating)
                                            },
                                            onUpdateQuickMetadata = { cur, tot, wc, auth, fan, rat, st ->
                                                viewModel.updateFanficQuickMetadata(cur, tot, wc, auth, fan, rat, st)
                                            },
                                            onTagClick = { tag ->
                                                com.omnitrack.app.data.model.FanficTagFilterBus.emitTag(tag)
                                                onBack()
                                            },
                                            onAddManualChapter = { num, title, cat, url ->
                                                viewModel.addManualFanficChapter(num, title, cat, url)
                                            },
                                            onDeleteChapter = { viewModel.deleteFanficChapter(it) },
                                            onStepProgress = { viewModel.stepFanficProgress(it) },
                                            onUpdateWorkStatus = { viewModel.updateFanficWorkStatus(it) },
                                            onOpenChapterInWebView = { url, site, ch ->
                                                activeFanficWebView = Triple(url, site, ch)
                                            },
                                            onOpenExternalUrl = { url ->
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                }
                                            },
                                            onSearchFandomArt = { showArtworkPicker = true }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            } else if (
                                item.mediaType == MediaType.SHOW ||
                                item.mediaType == MediaType.ANIME ||
                                item.mediaType == MediaType.MANGA ||
                                item.mediaType == MediaType.NOVEL ||
                                (item.mediaType == MediaType.MOVIE && allCustomizedSeasons.isNotEmpty())
                            ) {
                                detailSeriesItems(
                                    seriesState = seriesUiState,
                                    seasons = visibleSeasons,
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
                                    onOpenEpisodeDetail = { ep ->
                                        pushSheet(DetailSheetTarget.VolumeChapterDetail(ep.seasonNumber, ep.episodeNumber))
                                    },
                                    episodeMetaMap = mergedEpisodeMeta,
                                    seasonMetaMap = seasonMetaMap,
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
                                    } else null,
                                    hiddenSeasonsCount = hiddenSeasonsCount,
                                    onOpenManageSeasons = if (canCustomizeSeasons) {
                                        { showManageSeasonsSheet = true }
                                    } else null,
                                    linkedSeasonEntities = siblingSeasonEntities,
                                    currentMediaId = item.id
                                )
                            }
                        }
                        DetailSectionKey.ACHIEVEMENTS -> {
                            if (sectionsReady && item.mediaType == MediaType.GAME) {
                                item(key = "section_game_achievements", contentType = "game_achievements") {
                                    Column {
                                        DetailGameAchievementsSection(
                                            item = item,
                                            gameMeta = uiState.gameDetailMeta,
                                            onUpdateManualAchievements = { u, t ->
                                                viewModel.updateManualAchievements(u, t)
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.QUOTES -> {
                            if (sectionsReady && item.mediaType == MediaType.BOOK) {
                                item(key = "section_book_quotes", contentType = "book_quotes") {
                                    Column {
                                        DetailBookQuotesSection(
                                            quotes = uiState.bookQuotes,
                                            poemSampleLines = uiState.bookDetailMeta.poemSampleLines,
                                            onAddQuote = { q, loc, note -> viewModel.addBookQuote(q, loc, note) },
                                            onSaveQuote = { viewModel.saveBookQuote(it) },
                                            onToggleFavorite = { viewModel.toggleBookQuoteFavorite(it) },
                                            onDeleteQuote = { viewModel.deleteBookQuote(it) }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.SCREENSHOTS -> {
                            if (sectionsReady && item.mediaType == MediaType.VISUAL_NOVEL && uiState.vnDetailMeta.screenshots.isNotEmpty()) {
                                item(key = "section_vn_screenshots", contentType = "vn_screenshots") {
                                    Column {
                                        DetailScreenshotsGallerySection(
                                            screenshots = uiState.vnDetailMeta.screenshots,
                                            nsfwFilterMode = vnNsfwFilterMode,
                                            onSetAsHeroBanner = { url ->
                                                viewModel.setCgAsHeroBanner(url)
                                                Toast.makeText(context, "Hero banner updated", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            } else if (sectionsReady && item.mediaType == MediaType.GAME && uiState.gameDetailMeta.mediaGallery.isNotEmpty()) {
                                item(key = "section_game_gallery", contentType = "game_gallery") {
                                    Column {
                                        DetailGameGallerySection(
                                            assets = uiState.gameDetailMeta.mediaGallery,
                                            onSetAsHeroBanner = { url ->
                                                viewModel.setCgAsHeroBanner(url)
                                                Toast.makeText(context, "Hero banner updated", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            } else if (sectionsReady && item.mediaType == MediaType.BOOK && uiState.bookDetailMeta.illustrations.isNotEmpty()) {
                                item(key = "section_book_gallery", contentType = "book_gallery") {
                                    Column {
                                        DetailBookGallerySection(
                                            illustrations = uiState.bookDetailMeta.illustrations,
                                            onSetAsHeroBanner = { url ->
                                                viewModel.setBookIllustrationAsHeroBanner(url)
                                                Toast.makeText(context, "Hero banner updated", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.RELEASES -> {
                            if (sectionsReady && item.mediaType == MediaType.VISUAL_NOVEL && uiState.vnDetailMeta.releases.isNotEmpty()) {
                                item(key = "section_vn_releases", contentType = "vn_releases") {
                                    Column {
                                        DetailVnReleasesSection(
                                            releases = uiState.vnDetailMeta.releases
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.EDITIONS -> {
                            if (sectionsReady && item.mediaType == MediaType.BOOK && uiState.bookDetailMeta.editions.isNotEmpty()) {
                                item(key = "section_book_editions", contentType = "book_editions") {
                                    Column {
                                        DetailBookEditionsSection(
                                            editions = uiState.bookDetailMeta.editions,
                                            activeEditionLabel = item.bookEditionLabel,
                                            activeIsbn = item.isbn,
                                            onAdoptEdition = { ed ->
                                                viewModel.adoptBookEdition(ed)
                                                Toast.makeText(context, "Edition applied: ${ed.title}", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.FRANCHISE -> {
                            if (sectionsReady && uiState.animeRelations.isNotEmpty()) {
                                item(key = "section_franchise", contentType = "franchise") {
                                    Column {
                                        DetailFranchiseSection(
                                            relations = uiState.animeRelations,
                                            onRelationClick = { relation ->
                                                pushRelationPreview(relation)
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.CAST -> {
                            if (sectionsReady) {
                                val isAnimeLike = item.mediaType == MediaType.ANIME ||
                                    item.mediaType == MediaType.MANGA ||
                                    item.mediaType == MediaType.NOVEL ||
                                    item.mediaType == MediaType.VISUAL_NOVEL
                                val hasAnimeCast = isAnimeLike && (uiState.animeCharacters.isNotEmpty() || uiState.animeStaff.isNotEmpty())
                                val hasStdCast = !isAnimeLike && item.mediaType != MediaType.BOOK && uiState.cast.isNotEmpty()
                                val bookAuthorProfile = if (item.mediaType == MediaType.BOOK) uiState.bookDetailMeta.authorProfile else null
                                if (hasAnimeCast || hasStdCast) {
                                    item(key = "section_cast", contentType = "cast") {
                                        Column {
                                            if (isAnimeLike) {
                                                if (uiState.animeCharacters.isNotEmpty()) {
                                                    DetailAnimeCastSection(
                                                        characters = uiState.animeCharacters,
                                                        isMangaOrNovel = isReadingType,
                                                        spoilerThreshold = if (item.mediaType == MediaType.VISUAL_NOVEL) vnSpoilerLevel.level else Int.MAX_VALUE,
                                                        onCharacterClick = { character ->
                                                            pushSheet(DetailSheetTarget.AnimeCharacter(character))
                                                        },
                                                        onVoiceActorClick = { char ->
                                                            if (item.mediaType != MediaType.VISUAL_NOVEL && char.voiceActorId != null) {
                                                                pushSheet(
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
                                                            if (item.mediaType != MediaType.VISUAL_NOVEL) {
                                                                pushSheet(DetailSheetTarget.AnimeStaff(staff))
                                                            }
                                                        }
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(20.dp))
                                            } else {
                                                DetailCastSection(
                                                    cast = uiState.cast,
                                                    onCastMemberClick = { member ->
                                                        if (member.tmdbPersonId != null) {
                                                            pushSheet(DetailSheetTarget.Person(member))
                                                        }
                                                    }
                                                )
                                                Spacer(modifier = Modifier.height(20.dp))
                                            }
                                        }
                                    }
                                } else if (bookAuthorProfile != null) {
                                    item(key = "section_book_author", contentType = "book_author") {
                                        Column {
                                            DetailBookAuthorSection(
                                                authorProfile = bookAuthorProfile,
                                                onSelectWork = { rec -> pushPreview(rec) },
                                                onOpenAuthorProfile = {
                                                    pushSheet(DetailSheetTarget.BookAuthor(bookAuthorProfile))
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(20.dp))
                                        }
                                    }
                                } else if (item.mediaType == MediaType.COMIC && (
                                        uiState.comicDetailMeta.creators.isNotEmpty() ||
                                        uiState.comicDetailMeta.characters.isNotEmpty() ||
                                        uiState.comicDetailMeta.storyArcs.isNotEmpty() ||
                                        uiState.comicDetailMeta.variantCovers.size > 1
                                    )
                                ) {
                                    item(key = "section_comic_creators", contentType = "comic_creators") {
                                        Column {
                                            DetailComicCreatorsCharactersSection(
                                                comicDetailMeta = uiState.comicDetailMeta,
                                                onCreatorClick = { name, role, avatarUrl ->
                                                    pushSheet(
                                                        DetailSheetTarget.ComicCreatorOrCharacter(
                                                            name = name,
                                                            role = role,
                                                            imageUrl = avatarUrl
                                                        )
                                                    )
                                                },
                                                onCharacterClick = { name, role, avatarUrl ->
                                                    pushSheet(
                                                        DetailSheetTarget.ComicCreatorOrCharacter(
                                                            name = name,
                                                            role = role,
                                                            imageUrl = avatarUrl
                                                        )
                                                    )
                                                },
                                                onSetAsCover = { url ->
                                                    viewModel.setComicCoverAsArtwork(url, asBanner = false)
                                                    Toast.makeText(context, "Cover updated", Toast.LENGTH_SHORT).show()
                                                },
                                                onSetAsBanner = { url ->
                                                    viewModel.setComicCoverAsArtwork(url, asBanner = true)
                                                    Toast.makeText(context, "Banner updated", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                        DetailSectionKey.NETWORKS -> {
                            if (sectionsReady) {
                                item(key = "section_networks", contentType = "networks") {
                                    Column {
                                        DetailNetworksSection(
                                            item = item,
                                            companies = uiState.companies,
                                            onCompanyClick = { company ->
                                                pushSheet(DetailSheetTarget.Company(company))
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.GENRES_TAGS -> {
                            if (sectionsReady && item.mediaType != MediaType.FANFICTION) {
                                item(key = "section_genres_tags", contentType = "genres_tags") {
                                    Column {
                                        DetailGenresTagsSection(
                                            item = item,
                                            tags = uiState.tags,
                                            vnSpoilerLevel = vnSpoilerLevel,
                                            vnNsfwFilterMode = vnNsfwFilterMode,
                                            gameDetailMeta = if (item.mediaType == MediaType.GAME) uiState.gameDetailMeta else null,
                                            bookDetailMeta = if (item.mediaType == MediaType.BOOK) uiState.bookDetailMeta else null,
                                            onGenreOrTagClick = { name, isTag ->
                                                pushSheet(
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
                            if (sectionsReady) {
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
                            if (sectionsReady) {
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
                                            nextAiringEpochSeconds = uiState.animeAiring?.airingAtEpochSeconds,
                                            vnDetailMeta = if (item.mediaType == MediaType.VISUAL_NOVEL) uiState.vnDetailMeta else null,
                                            gameDetailMeta = if (item.mediaType == MediaType.GAME) uiState.gameDetailMeta else null,
                                            bookDetailMeta = if (item.mediaType == MediaType.BOOK) uiState.bookDetailMeta else null,
                                            comicDetailMeta = if (item.mediaType == MediaType.COMIC) uiState.comicDetailMeta else null
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.EXTERNAL_LINKS -> {
                            if (sectionsReady && uiState.externalLinks.isNotEmpty() && item.mediaType != MediaType.FANFICTION) {
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
                            if (sectionsReady && uiState.trailers.isNotEmpty()) {
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
                            if (sectionsReady) {
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
                                                pushSheet(DetailSheetTarget.UserProfile(username, anilistUserId))
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
                            if (sectionsReady && item.mediaType == MediaType.MOVIE && uiState.collectionItems.isNotEmpty()) {
                                item(key = "section_collection", contentType = "collection") {
                                    Column {
                                        DetailCollectionSection(
                                            collectionName = uiState.collectionName ?: "Collection",
                                            items = uiState.collectionItems,
                                            onSelectItem = {
                                                pushPreview(it)
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.RECOMMENDATIONS -> {
                            if (sectionsReady && uiState.recommendations.isNotEmpty()) {
                                item(key = "section_recommendations", contentType = "recommendations") {
                                    Column {
                                        RecommendationsSection(
                                            recommendations = uiState.recommendations,
                                            onSelectRecommendation = {
                                                pushPreview(it)
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.CROSS_MEDIUM_EDITIONS -> {
                            if (!uiState.isPreviewMode && item.id > 0L) {
                                item(key = "section_cross_medium_editions", contentType = "cross_medium_editions") {
                                    Column {
                                        CrossMediumEditionsSection(
                                            currentItem = item,
                                            editions = crossMediumEditions,
                                            isPreviewMode = uiState.isPreviewMode,
                                            onOpenEdition = { edition ->
                                                pushLibraryEntry(edition)
                                            },
                                            onOpenLinkPicker = { showLinkEditionSheet = true },
                                            onUnlinkEdition = { edition -> viewModel.unlinkCrossMediumEdition(edition) },
                                            switcherStyle = editionsSwitcherStyle,
                                            customLabels = customEditionLabels,
                                            onSaveCustomLabel = { edition, label ->
                                                coroutineScope.launch {
                                                    prefManager.setCustomEditionLabel(edition.id, label)
                                                }
                                            },
                                            onChangeSwitcherStyle = { nextStyle ->
                                                coroutineScope.launch {
                                                    prefManager.setEditionsSwitcherStyle(nextStyle)
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.WHERE_TO_WATCH -> {
                            val showWhereToWatch = sectionsReady &&
                                (item.mediaType == MediaType.MOVIE || item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME) &&
                                item.tmdbId != null && item.tmdbId > 0L
                            if (showWhereToWatch) {
                                item(key = "section_where_to_watch", contentType = "where_to_watch") {
                                    Column {
                                        DetailWhereToWatchSection(
                                            bundle = watchProviders,
                                            configuredRegionPref = watchRegionPref,
                                            isLoading = isLoadingWatchProviders,
                                            onSelectRegionPref = { chosen ->
                                                coroutineScope.launch {
                                                    prefManager.setWatchProviderRegion(chosen)
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                        DetailSectionKey.AWARDS -> {
                            if (sectionsReady && awards.isNotEmpty()) {
                                item(key = "section_awards", contentType = "awards") {
                                    Column {
                                        DetailAwardsSection(
                                            awards = awards,
                                            onAwardClick = { honor ->
                                                viewModel.openAwardWinners(honor, item.mediaType)
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "bottom_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(48.dp))
                }
                }
            }

            if (supportsFanficPullRefresh) {
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = fanficAuxState.isCheckingUpdates,
                    onRefresh = { viewModel.checkFanficUpdates() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    detailScrollBody()
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (animateHeroScroll) {
                                Modifier.nestedScroll(heroStretchState.nestedScrollConnection)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    detailScrollBody()
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
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = { stepBackOneLevel() },
                            onLongClick = { exitAllToRoot() }
                        ),
                    contentAlignment = Alignment.Center
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
                val hasAuthenticTopBarBanner = item != null && hasAuthenticDetailBanner(item, effectiveCover, backdropTargetUrl)
                val topBarEffectiveLayout = when {
                    presentationSettings.detailsPagePresentation == DetailsPagePresentation.CENTERED_COVER -> DetailsPagePresentation.CENTERED_COVER
                    presentationSettings.autoCenterCoverWhenNoBanner && !hasAuthenticTopBarBanner -> DetailsPagePresentation.CENTERED_COVER
                    else -> presentationSettings.detailsPagePresentation
                }
                val showTopBarOptions = topBarEffectiveLayout == DetailsPagePresentation.HERO ||
                    topBarEffectiveLayout == DetailsPagePresentation.CENTERED_COVER
                if (item != null && showTopBarOptions) {
                    Spacer(modifier = Modifier.size(44.dp))
                }

                // Center Title / Clearart Logo (smooth fade-in + slide down as user scrolls down to seasons & content)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .padding(horizontal = 8.dp)
                        .graphicsLayer {
                            alpha = headerAlpha
                            translationY = lerp((-12).dp, 0.dp, headerAlpha).toPx()
                        },
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

                // Top-Right Watched / Completion Checkmark + Options Menu (Symmetrical Header Support)
                if (item != null) {
                    val isCompleted = item.status == TrackingStatus.COMPLETED
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val nextStatus = if (isCompleted) {
                                    if (item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME || item.mediaType == MediaType.MANGA || item.mediaType == MediaType.NOVEL || item.mediaType == MediaType.FANFICTION) {
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
                        if (showTopBarOptions) {
                            IconButton(
                                onClick = { showOptionsMenu = true },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .shadow(elevation = 8.dp, shape = CircleShape)
                                )
                            }
                        }
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
                    isCustomEntry = item.isCustomEntry,
                    canLinkEditions = !uiState.isPreviewMode && item.id > 0L,
                    canManageSeasons = canCustomizeSeasons,
                    onDismiss = { showOptionsMenu = false },
                    onManageSeasons = {
                        showOptionsMenu = false
                        showManageSeasonsSheet = true
                    },
                    onLinkEditions = {
                        showOptionsMenu = false
                        showLinkEditionSheet = true
                    },
                    onToggleTitleLanguage = {
                        showOptionsMenu = false
                        viewModel.togglePreferredTitle()
                    },
                    onEditDetails = {
                        showOptionsMenu = false
                        showEditEntrySheet = true
                    },
                    onMatchAndEnrich = {
                        showOptionsMenu = false
                        viewModel.forceMatchAndEnrich()
                    },
                    onChangeArtwork = {
                        showOptionsMenu = false
                        showArtworkPicker = true
                    },
                    onToggleLayout = {
                        val newMode = when (presentationSettings.detailsPagePresentation) {
                            DetailsPagePresentation.HERO -> DetailsPagePresentation.CENTERED_COVER
                            DetailsPagePresentation.CENTERED_COVER -> DetailsPagePresentation.CLASSIC
                            DetailsPagePresentation.CLASSIC -> DetailsPagePresentation.HERO
                        }
                        coroutineScope.launch {
                            prefManager.updateDetailsPagePresentation(newMode)
                        }
                        showOptionsMenu = false
                    },
                    onManageRatings = {
                        showOptionsMenu = false
                        showManageRatingsSheet = true
                    },
                    onRemoveFromLibrary = {
                        showOptionsMenu = false
                        viewModel.deleteItem()
                        onBack()
                    }
                )
            }

            pendingHideRatingBadge?.let { badge ->
                AlertDialog(
                    onDismissRequest = { pendingHideRatingBadge = null },
                    containerColor = DarkSurface,
                    title = {
                        Text(
                            text = "Hide ${badge.displayName} Rating?",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "Hide ${badge.displayName} from this entry's details and Home showcase banner. You can turn it back on anytime in Options → Manage Ratings.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val src = badge.source
                                pendingHideRatingBadge = null
                                if (entryRatingKey.isNotBlank()) {
                                    coroutineScope.launch {
                                        prefManager.hideRatingSourceForEntry(entryRatingKey, src)
                                    }
                                }
                            }
                        ) {
                            Text("Hide Rating", color = AccentGold, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingHideRatingBadge = null }) {
                            Text("Keep", color = TextSecondary)
                        }
                    }
                )
            }

            if (showManageRatingsSheet) {
                val availableBadges = remember(
                    item, uiState.novelDetailMeta, uiState.vnDetailMeta,
                    uiState.gameDetailMeta, uiState.bookDetailMeta, uiState.comicDetailMeta
                ) {
                    collectRatingBadgesForEntry(
                        item = item,
                        anilistScore = uiState.novelDetailMeta.anilistScore,
                        malScore = uiState.novelDetailMeta.malScore,
                        kitsuScore = uiState.novelDetailMeta.kitsuScore,
                        mangaUpdatesScore = uiState.novelDetailMeta.mangaUpdatesScore,
                        bangumiScore = uiState.novelDetailMeta.bangumiScore,
                        vnDetailMeta = uiState.vnDetailMeta,
                        gameDetailMeta = uiState.gameDetailMeta,
                        bookDetailMeta = uiState.bookDetailMeta,
                        comicDetailMeta = uiState.comicDetailMeta
                    )
                }
                val badgeBySource = remember(availableBadges) { availableBadges.associateBy { it.source.lowercase() } }
                val managedSources = remember(availableBadges, hiddenRatingSources) {
                    (availableBadges.map { it.source.lowercase() } + hiddenRatingSources.map { it.lowercase() }).distinct()
                }
                ModalBottomSheet(
                    onDismissRequest = { showManageRatingsSheet = false },
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
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Manage Entry Ratings",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Applies to both this Details page and the Home showcase banner",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            if (hiddenRatingSources.isNotEmpty() && entryRatingKey.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            prefManager.resetHiddenRatingSourcesForEntry(entryRatingKey)
                                        }
                                    }
                                ) {
                                    Text("Restore All", color = AccentGold, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (managedSources.isEmpty()) {
                            Text(
                                text = "No external community ratings are currently linked for this entry.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            managedSources.forEach { srcKey ->
                                val badge = badgeBySource[srcKey]
                                val isShown = srcKey !in hiddenRatingSources
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DarkSurfaceCard)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (badge != null) {
                                            androidx.compose.foundation.Image(
                                                painter = androidx.compose.ui.res.painterResource(badge.iconRes),
                                                contentDescription = badge.displayName,
                                                modifier = Modifier.size(width = badge.iconWidth, height = badge.iconHeight)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = badge?.displayName ?: ratingSourceDisplayName(srcKey),
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            badge?.let {
                                                Text(
                                                    text = it.valueText,
                                                    color = it.valueColor,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Switch(
                                        checked = isShown,
                                        onCheckedChange = { show ->
                                            if (entryRatingKey.isNotBlank()) {
                                                coroutineScope.launch {
                                                    if (show) {
                                                        prefManager.unhideRatingSourceForEntry(entryRatingKey, srcKey)
                                                    } else {
                                                        prefManager.hideRatingSourceForEntry(entryRatingKey, srcKey)
                                                    }
                                                }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = AmoledBlack,
                                            checkedTrackColor = AccentGold,
                                            uncheckedThumbColor = TextSecondary,
                                            uncheckedTrackColor = DarkSurfaceElevated
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            if (showEditEntrySheet) {
                CustomEntryEditorSheet(
                    existingItem = item,
                    availableCategories = allCategories,
                    linkableSeries = allLibrarySummaries,
                    initialCategoryIds = categoryAssignments[item.id].orEmpty(),
                    initialVnRoutes = uiState.vnRoutes,
                    vnMeta = uiState.vnDetailMeta,
                    onCreateCategory = { name, onCreated ->
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            coroutineScope.launch {
                                val nextOrder = (allCategories.maxOfOrNull { it.orderIndex } ?: -1) + 1
                                val id = app.repository.insertCategory(
                                    CategoryEntity(name = trimmed, orderIndex = nextOrder, isVisible = true)
                                )
                                if (id > 0L) onCreated(id)
                            }
                        }
                    },
                    onSaveFull = { updated, selectedCatIds, vnDrafts ->
                        showEditEntrySheet = false
                        viewModel.updateCustomEntryDetails(updated)
                        if (updated.mediaType == MediaType.VISUAL_NOVEL && item.id > 0L) {
                            viewModel.syncCustomVnRoutes(item.id, vnDrafts)
                        }
                        coroutineScope.launch {
                            prefManager.setMediaCategories(item.id, selectedCatIds)
                        }
                    },
                    onSaveWithCategories = { updated, selectedCatIds ->
                        showEditEntrySheet = false
                        viewModel.updateCustomEntryDetails(updated)
                        coroutineScope.launch {
                            prefManager.setMediaCategories(item.id, selectedCatIds)
                        }
                    },
                    onSave = { updated ->
                        showEditEntrySheet = false
                        viewModel.updateCustomEntryDetails(updated)
                    },
                    onDismiss = { showEditEntrySheet = false }
                )
            }

            // Cover & Banner artwork customization bottom sheet (Option A multi-tab)
            if (showArtworkPicker) {
                val effectiveGameSteamAppId = item.steamAppId ?: uiState.gameDetailMeta.steamAppId
                val initialArtCandidates = remember(item.id, losslessCover, item.bannerUrl, uiState.vnDetailMeta, uiState.gameDetailMeta, uiState.bookDetailMeta, effectiveGameSteamAppId) {
                    buildList {
                        val effectiveInitCover = losslessCover.takeIf { it.isNotBlank() } ?: item.coverUrl
                        if (effectiveInitCover.isNotBlank()) {
                            add(
                                com.omnitrack.app.data.artwork.ArtworkCandidate(
                                    url = effectiveInitCover,
                                    sourceName = "Default",
                                    description = "Current Cover",
                                    isBanner = false
                                )
                            )
                        }
                        item.bannerUrl?.takeIf { it.isNotBlank() }?.let { b ->
                            add(
                                com.omnitrack.app.data.artwork.ArtworkCandidate(
                                    url = b,
                                    sourceName = "Default",
                                    description = "Current Banner",
                                    isBanner = true,
                                    isScreenshot = b.contains("t.vndb.org/sf/")
                                )
                            )
                        }
                        val seedSteamAppId = uiState.vnDetailMeta.steamAppId ?: effectiveGameSteamAppId
                        seedSteamAppId?.let { appId ->
                            add(
                                com.omnitrack.app.data.artwork.ArtworkCandidate(
                                    url = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/library_600x900_2x.jpg",
                                    sourceName = "Steam",
                                    description = "Steam 600x900 Capsule",
                                    isBanner = false
                                )
                            )
                            add(
                                com.omnitrack.app.data.artwork.ArtworkCandidate(
                                    url = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/library_hero.jpg",
                                    sourceName = "Steam Hero",
                                    description = "Steam Hero Banner",
                                    isBanner = true,
                                    isScreenshot = false
                                )
                            )
                        }
                        uiState.vnDetailMeta.screenshots.forEachIndexed { idx, shot ->
                            add(
                                com.omnitrack.app.data.artwork.ArtworkCandidate(
                                    url = shot.url,
                                    sourceName = "VNDB Screenshot",
                                    description = shot.releaseTitle ?: "VNDB CG ${idx + 1}",
                                    isBanner = true,
                                    isNsfw = shot.isSensitive,
                                    isScreenshot = true
                                )
                            )
                        }
                        uiState.gameDetailMeta.mediaGallery.forEachIndexed { idx, asset ->
                            add(
                                com.omnitrack.app.data.artwork.ArtworkCandidate(
                                    url = asset.url,
                                    sourceName = if (asset.isScreenshot) "Game Screenshot" else "Game Artwork",
                                    description = asset.label ?: "Game Art ${idx + 1}",
                                    isBanner = true,
                                    isScreenshot = asset.isScreenshot
                                )
                            )
                        }
                        if (item.mediaType == MediaType.BOOK) {
                            uiState.bookDetailMeta.illustrations.forEachIndexed { idx, ill ->
                                add(
                                    com.omnitrack.app.data.artwork.ArtworkCandidate(
                                        url = ill.url,
                                        sourceName = "Wikimedia Commons",
                                        description = ill.caption ?: "Book Illustration ${idx + 1}",
                                        isBanner = ill.isWideHero,
                                        isScreenshot = !ill.isWideHero
                                    )
                                )
                            }
                            uiState.bookDetailMeta.editions.forEach { ed ->
                                ed.coverUrl?.takeIf { it.isNotBlank() }?.let { cov ->
                                    add(
                                        com.omnitrack.app.data.artwork.ArtworkCandidate(
                                            url = cov,
                                            sourceName = "Open Library",
                                            description = ed.title,
                                            isBanner = false
                                        )
                                    )
                                }
                            }
                        }
                    }.distinctBy { "${it.isBanner}:${it.url}" }
                }
                ArtworkPickerBottomSheet(
                    title = if (item.mediaType == MediaType.FANFICTION) {
                        item.fanficPrimaryFandom?.takeIf { it.isNotBlank() } ?: item.title
                    } else item.title,
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
                    vndbId = item.vndbId,
                    englishTitle = uiState.englishTitle,
                    steamAppId = effectiveGameSteamAppId,
                    openLibraryId = item.openLibraryId,
                    bookAuthor = item.bookAuthor ?: uiState.bookDetailMeta.authorName,
                    initialCandidates = initialArtCandidates,
                    initialExcludedSources = remember(item.excludedArtSources) {
                        item.excludedArtSources
                            ?.split(",")
                            ?.map { it.trim().uppercase() }
                            ?.filter { it.isNotBlank() }
                            ?.toSet()
                            ?: emptySet()
                    },
                    onExcludedSourcesChanged = { excluded ->
                        viewModel.updateExcludedArtSources(excluded)
                    },
                    currentBannerFocalX = item.bannerFocalX,
                    currentBannerFocalY = item.bannerFocalY,
                    currentBannerZoom = item.bannerZoom,
                    onBannerFramingChanged = { fx, fy, z ->
                        viewModel.updateBannerFraming(fx, fy, z)
                    },
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
                    onDismiss = { showProgressEditSheet = false },
                    unitLabel = item.mediaType.unitLabel,
                    presentationSettings = presentationSettings,
                    backdropBitmap = backdropBitmap,
                    backdropTargetUrl = effectiveBanner
                )
            }

            if (showVnPlaytimeDialog) {
                var hoursText by remember(item.id, item.playtimeHours) {
                    mutableStateOf(String.format(java.util.Locale.US, "%.1f", item.playtimeHours))
                }
                AlertDialog(
                    onDismissRequest = { showVnPlaytimeDialog = false },
                    containerColor = AmoledBlack,
                    title = {
                        Text("Playtime (Hours)", color = Color.White, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        OutlinedTextField(
                            value = hoursText,
                            onValueChange = { next ->
                                if (next.length <= 7 && next.all { it.isDigit() || it == '.' }) {
                                    hoursText = next
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val parsed = hoursText.toDoubleOrNull()
                            if (parsed != null) {
                                if (item.mediaType == MediaType.GAME) {
                                    viewModel.setGamePlaytimeExact(parsed)
                                } else {
                                    viewModel.setVnPlaytimeExact(parsed)
                                }
                            }
                            showVnPlaytimeDialog = false
                        }) {
                            Text("Save", color = AccentGold, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showVnPlaytimeDialog = false }) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                )
            }

            // Chimanew Instant Status Bottom Sheet
            if (showStatusPickerSheet) {
                ChimanewStatusBottomSheet(
                    currentStatus = item.status,
                    onStatusSelected = { newStatus ->
                        viewModel.updateStatus(newStatus) { outcomes ->
                            val failed = outcomes.filter { !it.success }
                            if (failed.isNotEmpty()) {
                                val first = failed.first()
                                Toast.makeText(
                                    context,
                                    "Saved locally; ${first.trackerKey} sync: ${first.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onDismiss = { showStatusPickerSheet = false },
                    mediaType = item.mediaType,
                    presentationSettings = presentationSettings,
                    backdropBitmap = backdropBitmap,
                    backdropTargetUrl = effectiveBanner
                )
            }

            // Tenth-Decimal Score & Rating Sheet (1.0 to 10.0, 0.1 precision)
            if (showScoreRatingSheet) {
                ScoreRatingSheet(
                    initialScore = item.userRating,
                    onDismiss = { showScoreRatingSheet = false },
                    onScoreConfirmed = { viewModel.updateRating(it) },
                    presentationSettings = presentationSettings,
                    backdropBitmap = backdropBitmap,
                    backdropTargetUrl = effectiveBanner
                )
            }

            // Fanfiction In-App WebView (Reading Chapters or Browser Session / Verification)
            val promptWebView = fanficAuxState.webViewPrompt
            val activeWv = activeFanficWebView
            if (activeWv != null) {
                val (wvUrl, wvSite, wvChapter) = activeWv
                FanficWebViewSheet(
                    initialUrl = wvUrl,
                    initialSite = wvSite,
                    showImportButton = true,
                    chapterReadLabel = wvChapter?.let { "Mark Ch.${it.chapterNumber} Read" },
                    onMarkChapterRead = wvChapter?.let { ch -> { viewModel.toggleFanficChapterRead(ch) } },
                    onPageImported = { work ->
                        viewModel.applyWebViewParsedFanfic(work)
                        activeFanficWebView = null
                    },
                    onDismiss = { activeFanficWebView = null }
                )
            } else if (promptWebView != null) {
                FanficWebViewSheet(
                    initialUrl = promptWebView.second,
                    initialSite = promptWebView.first,
                    helperBanner = "Complete browser sign-in or site verification, then tap Import Page",
                    showImportButton = true,
                    onPageImported = { work -> viewModel.applyWebViewParsedFanfic(work) },
                    onDismiss = { viewModel.dismissFanficWebViewPrompt() }
                )
            }

            // Chimanew-Style Multi-Tracker Sheet
            if (showMultiTrackerSheet) {
                MultiTrackerSheet(
                    item = item,
                    onDismiss = { showMultiTrackerSheet = false },
                    onUpdateItem = { updatedItem ->
                        viewModel.updateMediaItem(updatedItem)
                    },
                    onDeleteLocalEntry = {
                        showMultiTrackerSheet = false
                        viewModel.deleteItem()
                        onBack()
                    },
                    presentationSettings = presentationSettings,
                    backdropBitmap = backdropBitmap,
                    backdropTargetUrl = effectiveBanner,
                    onPullFromTracker = { trackerKey, overrideItem, cb ->
                        viewModel.pullFromTracker(trackerKey, overrideItem, cb)
                    },
                    onPushToTracker = { overrideItem, trackerKey, selectedKeys, cb ->
                        viewModel.pushToTracker(overrideItem, trackerKey, selectedKeys, cb)
                    },
                    onDeleteFromRemote = { trackerKey, alsoDeleteLocal, unlinkedItem, cb ->
                        viewModel.deleteFromRemoteTracker(trackerKey, alsoDeleteLocal, unlinkedItem) { outcome, removedLocal ->
                            cb(outcome, removedLocal)
                            if (removedLocal) {
                                showMultiTrackerSheet = false
                                onBack()
                            }
                        }
                    }
                )
            }

            if (showTitleActionSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showTitleActionSheet = false },
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
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.title,
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showTitleActionSheet = false
                                    onSearchInLibrary(item.title)
                                }
                                .padding(vertical = 13.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Library Search",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Filter your Library for this title",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.6.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showTitleActionSheet = false
                                    onSearchInDiscover(item.title)
                                }
                                .padding(vertical = 13.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Global Search",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Search across all Discover sources",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.6.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Title", item.title))
                                    Toast.makeText(context, "Copied title to clipboard", Toast.LENGTH_SHORT).show()
                                    showTitleActionSheet = false
                                }
                                .padding(vertical = 13.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Copy to Clipboard",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Copy full title text",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            DetailSeriesDialogs(
                seriesState = seriesUiState,
                seasons = visibleSeasons,
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
                onMarkPreviousSeasonsWatched = { seasonNum ->
                    viewModel.markPreviousSeasonsWatched(seasonNum, visibleSeasons.map { it.seasonNumber })
                },
                onMarkWatchedChoice = { ep, markFillers -> viewModel.markWatchedUpToWithFillerChoice(ep, markFillers) },
                onJumpToProgress = if (isReadingType) {
                    { target -> viewModel.jumpToChapterOrVolume(target) }
                } else null,
                seasonMetaMap = seasonMetaMap,
                onSaveSeasonMeta = { seasonNum, rating, note ->
                    viewModel.saveSeasonMeta(seasonNum, rating, note)
                },
                siblingSeasonEntities = siblingSeasonEntities,
                currentMediaId = item.id,
                onOpenLinkedSeasonEntry = { linked ->
                    pushLibraryEntry(linked)
                },
                onOpenManageSeasons = if (canCustomizeSeasons) {
                    { showManageSeasonsSheet = true }
                } else null
            )
        }
    }

        // 4. UNIFIED IN-WINDOW SHEETS & DRILL-DOWN OVERLAYS (Render above DetailScreen preserving scroll)
        val topSheet = (navHistory.lastOrNull() as? DetailNavStep.Sheet)?.target
        BackHandler(enabled = topSheet != null) {
            stepBackOneLevel()
        }
        when (topSheet) {
            is DetailSheetTarget.UserProfile -> {
                UserProfileSheet(
                    username = topSheet.username,
                    anilistUserId = topSheet.userId,
                    userAuthToken = uiState.anilistAuthToken,
                    currentUsername = uiState.currentUsername,
                    presentationSettings = presentationSettings,
                    backdropBitmap = backdropBitmap,
                    backdropTargetUrl = effectiveBanner,
                    onOpenMediaPreview = { recItem -> pushPreview(recItem) },
                    onOpenCharacter = { charItem -> pushSheet(DetailSheetTarget.AnimeCharacter(charItem)) },
                    onOpenStaff = { staffItem -> pushSheet(DetailSheetTarget.AnimeStaff(staffItem)) },
                    onOpenMediaList = { uid, uname, isAnime ->
                        pushSheet(DetailSheetTarget.UserMediaList(uid, uname, isAnime))
                    },
                    onDismiss = { stepBackOneLevel() }
                )
            }
            is DetailSheetTarget.UserMediaList -> {
                UserMediaListOverlay(
                    userId = topSheet.userId,
                    username = topSheet.username,
                    isAnime = topSheet.isAnime,
                    presentationSettings = presentationSettings,
                    backdropBitmap = backdropBitmap,
                    backdropTargetUrl = effectiveBanner,
                    onBack = { stepBackOneLevel() },
                    onOpenMediaPreview = { recItem -> pushPreview(recItem) }
                )
            }
            is DetailSheetTarget.AnimeCharacter -> {
                AnimeCharacterSheet(
                    character = topSheet.character,
                    presentationSettings = presentationSettings,
                    backdropBitmap = backdropBitmap,
                    backdropTargetUrl = effectiveBanner,
                    vnProfile = topSheet.character.vndbCharacterId?.let { uiState.vnDetailMeta.characterProfiles[it] },
                    vnSpoilerLevel = vnSpoilerLevel,
                    onDismissRequest = { stepBackOneLevel() },
                    onOpenMediaPreview = { recItem -> pushPreview(recItem) },
                    onOpenStaff = { staffItem ->
                        if (item?.mediaType != MediaType.VISUAL_NOVEL) {
                            pushSheet(DetailSheetTarget.AnimeStaff(staffItem))
                        }
                    }
                )
            }
            is DetailSheetTarget.AnimeStaff -> {
                AnimeStaffSheet(
                    staff = topSheet.staff,
                    presentationSettings = presentationSettings,
                    backdropBitmap = backdropBitmap,
                    backdropTargetUrl = effectiveBanner,
                    onDismissRequest = { stepBackOneLevel() },
                    onOpenMediaPreview = { recItem -> pushPreview(recItem) }
                )
            }
            is DetailSheetTarget.Person -> {
                val personId = topSheet.person.tmdbPersonId
                if (personId != null) {
                    val tmdbKey = "844dba0bfd8f3a4f3799f6130ef9e335"
                    PersonDetailScreen(
                        personId = personId,
                        personName = topSheet.person.name,
                        initialAvatarUrl = topSheet.person.avatarUrl,
                        apiKey = tmdbKey,
                        presentationSettings = presentationSettings,
                        backdropBitmap = backdropBitmap,
                        backdropTargetUrl = effectiveBanner,
                        onBack = { stepBackOneLevel() },
                        onOpenMediaPreview = { recItem -> pushPreview(recItem) }
                    )
                }
            }
            is DetailSheetTarget.Company -> {
                val tmdbKey = "844dba0bfd8f3a4f3799f6130ef9e335"
                TmdbEntityBrowseScreen(
                    entityId = topSheet.company.id,
                    entityName = topSheet.company.name,
                    entityKind = topSheet.company.kind,
                    apiKey = tmdbKey,
                    presentationSettings = presentationSettings,
                    backdropBitmap = backdropBitmap,
                    backdropTargetUrl = effectiveBanner,
                    onBack = { stepBackOneLevel() },
                    onOpenMediaPreview = { recItem -> pushPreview(recItem) }
                )
            }
            is DetailSheetTarget.VolumeChapterDetail -> {
                if (item != null) {
                    val seasonEpisodesForSheet = uiState.episodesBySeason[topSheet.seasonNumber]
                        ?: uiState.realEpisodes.filter { it.seasonNumber == topSheet.seasonNumber }
                            .ifEmpty { uiState.realEpisodes }
                    val totalItemsAcrossSeasons = uiState.episodesBySeason.values
                        .sumOf { it.size }
                        .takeIf { it > 0 } ?: uiState.realEpisodes.size
                    val useProgressAsWatchedTruth = isReadingType && (
                        totalItemsAcrossSeasons > 200 ||
                            (uiState.watchedEpisodes.isEmpty() && item.currentProgress.toInt() > 0)
                        )
                    val seasonLabel = uiState.realSeasons
                        .firstOrNull { it.seasonNumber == topSheet.seasonNumber }
                        ?.name
                        ?: if (isLightNovelVolumeMode) {
                            "Volumes"
                        } else if (isReadingType) {
                            if (uiState.isMangaSeasonGrouped) "Volume ${topSheet.seasonNumber}" else "Chapters"
                        } else {
                            "Season ${topSheet.seasonNumber}"
                        }
                    VolumeChapterDetailSheet(
                        episodes = seasonEpisodesForSheet,
                        initialSeasonNumber = topSheet.seasonNumber,
                        initialEpisodeNumber = topSheet.episodeNumber,
                        seasonLabel = seasonLabel,
                        fallbackCoverUrl = fallbackSeriesCover,
                        isMangaMode = isReadingType,
                        isLightNovelVolumeMode = isLightNovelVolumeMode,
                        watchedEpisodes = uiState.watchedEpisodes,
                        useProgressAsWatchedTruth = useProgressAsWatchedTruth,
                        currentTotalProgress = item.currentProgress.toInt(),
                        episodeMetaMap = mergedEpisodeMeta,
                        ranobeBookDetails = ranobeBookDetails,
                        onRequestRanobeBook = { bookId, adj -> viewModel.loadRanobeBookDetail(bookId, adj) },
                        onToggleWatched = { ep -> viewModel.toggleEpisodeWatched(ep) },
                        onMarkPreviousWatched = { ep -> viewModel.markPreviousEpisodesWatched(ep) },
                        onToggleSeasonWatched = { seasonNum -> viewModel.toggleSeasonWatched(seasonNum) },
                        onSaveMetadata = { sNum, eNum, rating, note, bookmarked, label ->
                            viewModel.updateEpisodeMetadata(sNum, eNum, rating, note, bookmarked, label)
                        },
                        onDismiss = { stepBackOneLevel() },
                        presentationSettings = presentationSettings,
                        backdropBitmap = backdropBitmap,
                        backdropTargetUrl = effectiveBanner
                    )
                }
            }
            is DetailSheetTarget.VnRoute -> {
                val activeRoute = uiState.vnRoutes.firstOrNull { it.id == topSheet.routeId }
                if (activeRoute != null) {
                    VnRouteDetailSheet(
                        route = activeRoute,
                        mediaItem = item,
                        vnMeta = uiState.vnDetailMeta,
                        presentationSettings = presentationSettings,
                        backdropBitmap = backdropBitmap,
                        backdropTargetUrl = effectiveBanner,
                        onSave = { updated -> viewModel.saveVnRouteDetails(updated) },
                        onDelete = { routeId -> viewModel.deleteVnRoute(routeId) },
                        onDismiss = { stepBackOneLevel() }
                    )
                }
            }
            is DetailSheetTarget.GameEntry -> {
                val activeEntry = uiState.gameEntries.firstOrNull { it.id == topSheet.entryId }
                if (activeEntry != null) {
                    GameEntryDetailSheet(
                        entry = activeEntry,
                        presentationSettings = presentationSettings,
                        backdropBitmap = backdropBitmap,
                        backdropTargetUrl = effectiveBanner,
                        onSave = { updated -> viewModel.saveGameEntryDetails(updated) },
                        onDelete = { entryId -> viewModel.deleteGameEntry(entryId) },
                        onDismiss = { stepBackOneLevel() }
                    )
                }
            }
            is DetailSheetTarget.BookEntry -> {
                val activeEntry = uiState.bookEntries.firstOrNull { it.id == topSheet.entryId }
                if (activeEntry != null) {
                    BookEntryDetailSheet(
                        entry = activeEntry,
                        presentationSettings = presentationSettings,
                        backdropBitmap = backdropBitmap,
                        backdropTargetUrl = effectiveBanner,
                        onSave = { updated -> viewModel.saveBookEntryDetails(updated) },
                        onDelete = { entryId -> viewModel.deleteBookEntry(entryId) },
                        onDismiss = { stepBackOneLevel() }
                    )
                }
            }
            is DetailSheetTarget.BookAuthor -> {
                AuthorProfileBottomSheet(
                    authorProfile = topSheet.profile,
                    presentationSettings = presentationSettings,
                    backdropBitmap = backdropBitmap,
                    backdropTargetUrl = effectiveBanner,
                    onSelectWork = { rec ->
                        stepBackOneLevel()
                        pushPreview(rec)
                    },
                    onDismiss = { stepBackOneLevel() }
                )
            }
            is DetailSheetTarget.ComicCreatorOrCharacter -> {
                ComicCreatorCharacterSheet(
                    name = topSheet.name,
                    role = topSheet.role,
                    avatarUrl = topSheet.imageUrl,
                    description = topSheet.description,
                    presentationSettings = presentationSettings,
                    backdropBitmap = backdropBitmap,
                    backdropTargetUrl = effectiveBanner,
                    onSelectComic = { rec ->
                        stepBackOneLevel()
                        pushPreview(rec)
                    },
                    onDismiss = { stepBackOneLevel() }
                )
            }
            null -> {}
        }
}
        if (showFullScreenCover && item != null) {
            val fullScreenCoverModel = remember(effectiveCover, item.customCoverPath) {
                item.customCoverPath?.let { if (it.startsWith("http://") || it.startsWith("https://")) it else java.io.File(it) }
                    ?: LosslessCoverMapper.toLosslessUrl(effectiveCover)
            }
            FullScreenCoverViewerDialog(
                coverModel = fullScreenCoverModel,
                title = item.title,
                onDismiss = { showFullScreenCover = false },
                onCustomizeArtwork = {
                    showFullScreenCover = false
                    showArtworkPicker = true
                }
            )
        }
        if (showLinkEditionSheet && item != null && item.id > 0L) {
            LinkCrossMediumEditionSheet(
                currentItem = item,
                allLibraryItems = allLibraryMedia,
                currentlyLinked = crossMediumEditions,
                onLinkTarget = { target -> viewModel.linkCrossMediumEdition(target) },
                onUnlinkTarget = { target -> viewModel.unlinkCrossMediumEdition(target) },
                onDismiss = { showLinkEditionSheet = false }
            )
        }
        if (showManageSeasonsSheet && item != null && item.id > 0L && rootSeasonId > 0L) {
            SeasonMergeCustomizeSheet(
                currentItem = item,
                rootMediaId = rootSeasonId,
                allCustomizedSeasons = allCustomizedSeasons,
                rawSeasons = uiState.realSeasons,
                customConfig = activeSeasonConfig,
                siblingSeasonEntities = siblingSeasonEntities,
                allLibraryItems = allLibraryMedia,
                animeRelations = uiState.animeRelations,
                fallbackCoverUrl = fallbackSeriesCover,
                onUpdateConfig = { transform ->
                    coroutineScope.launch {
                        prefManager.updateCustomSeriesSeasonConfig(rootSeasonId, transform)
                    }
                },
                onResetConfig = {
                    coroutineScope.launch {
                        prefManager.clearCustomSeriesSeasonConfig(rootSeasonId)
                    }
                },
                onMergeLibraryEntry = { target -> viewModel.mergeLibraryEntryAsSeason(target) },
                onMergeOnlineResult = { res -> viewModel.mergeOnlineSearchResultAsSeason(res) },
                onMergeRelation = { rel -> viewModel.mergeAnimeRelationAsSeason(rel) },
                onUnlinkSeason = { sNum -> viewModel.unlinkMergedSeason(sNum) },
                onOpenLinkedEntry = { linked -> pushLibraryEntry(linked) },
                onSearchOnline = { query -> viewModel.searchOnlineForSeasonMerge(query) },
                onDismiss = { showManageSeasonsSheet = false }
            )
        }
        awardWinnersSheetState?.let { winnersState ->
            AwardWinnersBrowserSheet(
                awardTitle = winnersState.awardTitle,
                winners = winnersState.winners,
                isLoading = winnersState.isLoading,
                onOpenWinner = { winner ->
                    viewModel.closeAwardWinners()
                    pushPreview(
                        RecommendationItem(
                            title = winner.title,
                            coverUrl = winner.posterUrl.orEmpty(),
                            releaseYear = winner.year?.toString(),
                            rating = null,
                            mediaType = winner.mediaType,
                            synopsis = null,
                            tmdbId = winner.tmdbId
                        )
                    )
                },
                onAddWinner = { winner ->
                    viewModel.addAwardWinnerToLibrary(winner)
                },
                onDismiss = { viewModel.closeAwardWinners() },
                resolveLibraryMatch = { winner ->
                    findAwardWinnerLibraryMatch(allLibraryMedia, winner)
                },
                onOpenLibraryItem = { match ->
                    viewModel.closeAwardWinners()
                    pushLibraryEntry(match)
                }
            )
        }
}

private fun hasAuthenticDetailBanner(
    item: MediaItemEntity,
    effectiveCover: String,
    backdropTargetUrl: String?
): Boolean {
    val raw = backdropTargetUrl?.trim()?.takeIf { it.isNotBlank() } ?: return false
    val normBanner = LosslessCoverMapper.toLosslessUrl(raw)
    val normEffCover = effectiveCover.trim().takeIf { it.isNotBlank() }?.let { LosslessCoverMapper.toLosslessUrl(it) }
    val normItemCover = item.coverUrl.trim().takeIf { it.isNotBlank() }?.let { LosslessCoverMapper.toLosslessUrl(it) }
    return normBanner != normEffCover && normBanner != normItemCover
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
    customPosterPattern: String = "",
    modifier: Modifier = Modifier
) {
    val cachedAux = remember(item.id, item.mediaType, item.tmdbId, item.anilistId, item.malId, item.igdbId, item.steamAppId) {
        val allowTmdbFallback = item.mediaType == MediaType.SHOW || item.mediaType == MediaType.MOVIE ||
            (item.mediaType == MediaType.ANIME && (item.anilistId == null || item.anilistId <= 0L))
        DetailAuxMemoryCache.getCacheKey(item)?.let { DetailAuxMemoryCache.cache[it] }
            ?: item.tmdbId?.takeIf { allowTmdbFallback }?.let {
                DetailAuxMemoryCache.cache[DetailAuxMemoryCache.getTmdbCacheKey(if (item.mediaType == MediaType.MOVIE) "movie" else "tv", it)]
            }
    }
    val pref = com.omnitrack.app.data.preferences.PreferenceManager.cachedPreferredTitleLanguage
    val siblingClearLogo = cachedAux?.heroClearLogoUrl?.takeIf {
        !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
    } ?: item.clearartUrl?.takeIf {
        !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
    } ?: com.omnitrack.app.data.enrichment.EnrichmentRepository.getCachedClearart(item)?.takeIf {
        !it.contains("thetvdb.com") || pref == com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE
    }

    val resolvedTitle = when (pref) {
        com.omnitrack.app.data.preferences.PreferredTitleLanguage.ENGLISH -> cachedAux?.englishTitle ?: item.title
        com.omnitrack.app.data.preferences.PreferredTitleLanguage.ROMAJI -> cachedAux?.romajiTitle ?: item.title
        com.omnitrack.app.data.preferences.PreferredTitleLanguage.NATIVE -> item.nativeTitle ?: cachedAux?.romajiTitle ?: item.title
    }
    val resolvedNative = if (resolvedTitle == item.nativeTitle) {
        cachedAux?.englishTitle ?: cachedAux?.romajiTitle ?: item.title
    } else {
        item.nativeTitle
    }
    val displayItem = remember(item, resolvedTitle, resolvedNative) {
        item.copy(title = resolvedTitle, nativeTitle = resolvedNative)
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
    val siblingCustomBannerUrl = remember(item, customPosterPattern) {
        if (customPosterPattern.isNotBlank() && "{shape}" in customPosterPattern) {
            com.omnitrack.app.data.poster.CustomPosterUrlResolver.resolveForMediaItem(item, customPosterPattern, isLandscape = true)
        } else null
    }
    val isBannerExplicitlyDisabled = item.customBannerPath == "NONE" || item.artworkRole == "DISABLE_BANNER"
    val effectiveBanner = when {
        isBannerExplicitlyDisabled -> null
        !item.customBannerPath.isNullOrBlank() -> item.customBannerPath
        siblingCustomBannerUrl != null -> siblingCustomBannerUrl
        item.artworkRole == "COVER_AS_BANNER" -> item.coverUrl
        item.artworkRole == "BANNER_AS_COVER" -> item.bannerUrl?.takeUnless { ArtworkModelResolver.isAutoScreenshotUrl(it) }
        else -> item.bannerUrl?.takeUnless { ArtworkModelResolver.isAutoScreenshotUrl(it) }
    }
    val backdropTargetUrl = effectiveBanner

    val isCinematicBlur = presentationSettings.fallbackBackgroundStyle == FallbackBackgroundStyle.CINEMATIC_BLUR
    val backdropBitmap = rememberArtworkBackdrop(
        imageUrl = if (isBannerExplicitlyDisabled) null else (backdropTargetUrl ?: item.coverUrl),
        enabled = isCinematicBlur && !isBannerExplicitlyDisabled
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
            val sibHasBanner = hasAuthenticDetailBanner(displayItem, effectiveCover, backdropTargetUrl)
            val sibLayout = when {
                presentationSettings.detailsPagePresentation == DetailsPagePresentation.CENTERED_COVER -> DetailsPagePresentation.CENTERED_COVER
                presentationSettings.autoCenterCoverWhenNoBanner && !sibHasBanner -> DetailsPagePresentation.CENTERED_COVER
                else -> presentationSettings.detailsPagePresentation
            }
            val sibBannerBlurDp = presentationSettings.resolveBannerBlurDp(displayItem.mediaType)
            when (sibLayout) {
                DetailsPagePresentation.CENTERED_COVER -> {
                    DetailCenteredCoverHeroSection(
                        item = displayItem,
                        coverImageModel = coverImageModel,
                        novelDetailMeta = cachedAux?.novelDetailMeta ?: NovelDetailMeta(),
                        vnDetailMeta = cachedAux?.vnDetailMeta ?: VnDetailMeta(),
                        gameDetailMeta = cachedAux?.gameDetailMeta ?: GameDetailMeta(),
                        bookDetailMeta = cachedAux?.bookDetailMeta ?: BookDetailMeta(),
                        comicDetailMeta = cachedAux?.comicDetailMeta ?: ComicDetailMeta(),
                        showInlineOptionsButton = false,
                        onCoverClick = {},
                        onOptionsClick = {},
                        onEditProgress = {},
                        onEditStatus = {},
                        onEditScore = {},
                        onOpenTrackers = {}
                    )
                }
                DetailsPagePresentation.HERO -> {
                    DetailHeroHeaderSection(
                        item = displayItem,
                        backdropTargetUrl = backdropTargetUrl,
                        bannerSizing = presentationSettings.bannerSizing,
                        bannerBlurDp = sibBannerBlurDp,
                        englishTitle = cachedAux?.englishTitle,
                        romajiTitle = cachedAux?.romajiTitle,
                        heroClearLogoUrl = siblingClearLogo,
                        novelDetailMeta = cachedAux?.novelDetailMeta ?: NovelDetailMeta(),
                        vnDetailMeta = cachedAux?.vnDetailMeta ?: VnDetailMeta(),
                        gameDetailMeta = cachedAux?.gameDetailMeta ?: GameDetailMeta(),
                        bookDetailMeta = cachedAux?.bookDetailMeta ?: BookDetailMeta(),
                        comicDetailMeta = cachedAux?.comicDetailMeta ?: ComicDetailMeta(),
                        showInlineOptionsButton = false,
                        onOptionsClick = {},
                        onEditProgress = {},
                        onEditStatus = {},
                        onEditScore = {},
                        onOpenTrackers = {}
                    )
                }
                DetailsPagePresentation.CLASSIC -> {
                    DetailClassicHeaderSection(
                        item = displayItem,
                        coverImageModel = coverImageModel,
                        backdropTargetUrl = backdropTargetUrl,
                        bannerBlurDp = sibBannerBlurDp,
                        englishTitle = cachedAux?.englishTitle,
                        romajiTitle = cachedAux?.romajiTitle,
                        heroClearLogoUrl = siblingClearLogo,
                        novelDetailMeta = cachedAux?.novelDetailMeta ?: NovelDetailMeta(),
                        vnDetailMeta = cachedAux?.vnDetailMeta ?: VnDetailMeta(),
                        gameDetailMeta = cachedAux?.gameDetailMeta ?: GameDetailMeta(),
                        bookDetailMeta = cachedAux?.bookDetailMeta ?: BookDetailMeta(),
                        comicDetailMeta = cachedAux?.comicDetailMeta ?: ComicDetailMeta(),
                        onCoverClick = {},
                        onCoverLoaded = {},
                        onOptionsClick = {},
                        onEditProgress = {},
                        onEditStatus = {},
                        onEditScore = {},
                        onOpenTrackers = {}
                    )
                }
            }

            val hasProgressControls = displayItem.mediaType == MediaType.MOVIE
            val hasBackdrop = backdropTargetUrl != null && sibLayout != DetailsPagePresentation.CENTERED_COVER
            if (hasProgressControls) {
                Spacer(modifier = Modifier.height(if (hasBackdrop) (-10).dp else 4.dp))
                DetailProgressSection(
                    item = displayItem,
                    onUpdateProgress = {},
                    onUpdatePlaytime = {},
                    onTogglePlatform = {},
                    onUpdateStatus = {}
                )
                Spacer(modifier = Modifier.height(4.dp))
            } else {
                Spacer(modifier = Modifier.height(if (hasBackdrop) (-12).dp else 8.dp))
            }

            if (!displayItem.synopsis.isNullOrBlank()) {
                DetailSynopsisSection(
                    synopsis = displayItem.synopsis,
                    dominantAccent = dominantAccent,
                    isVisualNovel = displayItem.mediaType == MediaType.VISUAL_NOVEL
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            DetailShowDetailsSection(
                item = displayItem,
                vnDetailMeta = if (displayItem.mediaType == MediaType.VISUAL_NOVEL) cachedAux?.vnDetailMeta else null,
                gameDetailMeta = if (displayItem.mediaType == MediaType.GAME) cachedAux?.gameDetailMeta else null,
                bookDetailMeta = if (displayItem.mediaType == MediaType.BOOK) cachedAux?.bookDetailMeta else null
            )
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
            item.igdbId,
            item.steamAppId,
            item.openLibraryId,
            item.hardcoverId
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
            val progressNumber = when {
                item.mediaType == MediaType.GAME || item.mediaType == MediaType.VISUAL_NOVEL ->
                    String.format("%.1f hrs", item.playtimeHours)
                item.mediaType == MediaType.BOOK && BookTrackingMode.fromName(item.bookTrackingMode) == BookTrackingMode.AUDIO_MINUTES ->
                    formatBookDuration(item.bookAudioMinutes ?: 0) ?: "0m"
                item.mediaType == MediaType.BOOK && BookTrackingMode.fromName(item.bookTrackingMode) == BookTrackingMode.PERCENT -> {
                    val tot = item.totalProgress?.takeIf { it > 0.0 }
                    val pct = if (tot != null) ((item.currentProgress / tot) * 100.0).toInt().coerceIn(0, 100) else item.currentProgress.toInt().coerceIn(0, 100)
                    "$pct%"
                }
                else -> "${item.currentProgress.toInt()}${item.totalProgress?.let { " / ${it.toInt()}" } ?: ""}"
            }
            Text(
                text = progressNumber,
                color = TextPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.2).sp
            )
            val statsUnitLabel = when {
                item.mediaType == MediaType.BOOK && BookTrackingMode.fromName(item.bookTrackingMode) == BookTrackingMode.AUDIO_MINUTES -> "AUDIOBOOK"
                item.mediaType == MediaType.BOOK && BookTrackingMode.fromName(item.bookTrackingMode) == BookTrackingMode.PERCENT -> "READ"
                item.mediaType == MediaType.NOVEL && com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                    item.platform, item.originCountry, item.anilistId, item.malId, item.totalProgress, item.title
                ) == "LIGHT_NOVEL" -> "VOLUMES"
                else -> item.mediaType.unitLabel.uppercase()
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
                    text = item.status.labelFor(item.mediaType),
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
                    text = item.userRating?.let { formatSmartRating(it.toDouble()) } ?: "Rate",
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
private fun DetailNuvioMetaRow(
    item: com.omnitrack.app.data.db.entity.MediaItemEntity,
    centered: Boolean = false
) {
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
            horizontalArrangement = if (centered) {
                Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
            } else {
                Arrangement.spacedBy(14.dp)
            },
            modifier = Modifier
                .then(if (centered) Modifier.fillMaxWidth() else Modifier)
                .padding(vertical = 4.dp)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailHeroHeaderSection(
    item: com.omnitrack.app.data.db.entity.MediaItemEntity,
    backdropTargetUrl: String?,
    bannerSizing: BannerSizing = BannerSizing.FULL_BLEED,
    bannerBlurDp: Int = 0,
    englishTitle: String? = null,
    romajiTitle: String? = null,
    heroClearLogoUrl: String? = null,
    novelDetailMeta: NovelDetailMeta = NovelDetailMeta(),
    vnDetailMeta: VnDetailMeta = VnDetailMeta(),
    gameDetailMeta: GameDetailMeta = GameDetailMeta(),
    bookDetailMeta: BookDetailMeta = BookDetailMeta(),
    comicDetailMeta: ComicDetailMeta = ComicDetailMeta(),
    hiddenRatingSources: Set<String> = emptySet(),
    onLongPressRatingBadge: ((RatingBadgeData) -> Unit)? = null,
    scrollOffsetPx: () -> Float = { 0f },
    stretchPx: () -> Float = { 0f },
    animateOnScroll: Boolean = true,
    showInlineOptionsButton: Boolean = false,
    onToggleTitle: (() -> Unit)? = null,
    onSearchInDiscover: (String) -> Unit = {},
    onOptionsClick: () -> Unit,
    onEditProgress: () -> Unit,
    onEditStatus: () -> Unit,
    onEditScore: () -> Unit,
    onOpenTrackers: () -> Unit,
    isPreviewMode: Boolean = false,
    onAddToLibrary: () -> Unit = {}
) {
    val isCinemaShowcase = bannerSizing == BannerSizing.CINEMA_SHOWCASE
    if (backdropTargetUrl != null) {
        val heroContext = LocalContext.current
        val hasManualFraming = kotlin.math.abs(item.bannerFocalX) > 0.01f ||
            kotlin.math.abs(item.bannerFocalY) > 0.01f ||
            kotlin.math.abs(item.bannerZoom - 1f) > 0.01f
        val focusState = rememberBackdropFocusState(cacheKey = backdropTargetUrl, enabled = !hasManualFraming)
        val bannerRequest = remember(backdropTargetUrl, heroContext) {
            ImageRequest.Builder(heroContext)
                .data(backdropTargetUrl)
                .crossfade(false)
                .build()
        }
        val effectiveBannerAlignment = if (hasManualFraming) {
            BiasAlignment(item.bannerFocalX.coerceIn(-1f, 1f), item.bannerFocalY.coerceIn(-1f, 1f))
        } else {
            focusState.focusedAlignment(Alignment.Center)
        }
        if (isCinemaShowcase) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 20.dp, end = 20.dp, bottom = 14.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .graphicsLayer {
                        if (animateOnScroll) {
                            val sPx = scrollOffsetPx()
                            val stretch = stretchPx()
                            translationY = sPx * 0.16f + stretch * 0.25f
                            alpha = (1f - sPx / (size.height.coerceAtLeast(1f) * 1.15f)).coerceIn(0.15f, 1f)
                        }
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .border(0.6.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            ) {
                AsyncImage(
                    model = bannerRequest,
                    contentDescription = item.title,
                    alignment = effectiveBannerAlignment,
                    onSuccess = { state -> if (!hasManualFraming) focusState.onImageLoaded(state.result.image) },
                    onError = { focusState.onImageFailed() },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val fx = item.bannerFocalX.coerceIn(-1f, 1f)
                            val fy = item.bannerFocalY.coerceIn(-1f, 1f)
                            val effZoom = item.bannerZoom.coerceIn(1f, 2.5f)
                            val stretchZoom = if (animateOnScroll) {
                                1f + stretchPx().coerceAtLeast(0f) / size.height.coerceAtLeast(1f)
                            } else 1f
                            val totalZoom = effZoom * stretchZoom
                            scaleX = totalZoom
                            scaleY = totalZoom
                            translationX = -fx * size.width * (effZoom - 1f) * 0.5f
                            translationY = -fy * size.height * (effZoom - 1f) * 0.5f
                            if (!hasManualFraming) alpha = focusState.revealAlpha
                        }
                        .then(if (bannerBlurDp > 0) Modifier.blur(bannerBlurDp.dp) else Modifier),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (animateOnScroll) Modifier.heroStretchHeight(350.dp, stretchPx)
                        else Modifier.height(350.dp)
                    )
                    .clipToBounds()
                    .featherArtwork(startYRatio = 0.50f)
            ) {
                AsyncImage(
                    model = bannerRequest,
                    contentDescription = item.title,
                    alignment = effectiveBannerAlignment,
                    onSuccess = { state -> if (!hasManualFraming) focusState.onImageLoaded(state.result.image) },
                    onError = { focusState.onImageFailed() },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val fx = item.bannerFocalX.coerceIn(-1f, 1f)
                            val fy = item.bannerFocalY.coerceIn(-1f, 1f)
                            val effZoom = item.bannerZoom.coerceIn(1f, 2.5f)
                            val focusAlpha = if (hasManualFraming) 1f else focusState.revealAlpha
                            if (animateOnScroll) {
                                val sPx = scrollOffsetPx()
                                val stretch = stretchPx().coerceAtLeast(0f)
                                val stretchZoom = 1f + stretch / size.height.coerceAtLeast(1f)
                                val scrollScale = heroScrollBackdropScale(sPx)
                                val totalZoom = effZoom * stretchZoom * scrollScale
                                if (stretch > 0f) {
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                }
                                scaleX = totalZoom
                                scaleY = totalZoom
                                translationX = -fx * size.width * (effZoom - 1f) * 0.5f
                                translationY = -fy * size.height * (effZoom - 1f) * 0.5f + sPx * 0.48f
                                alpha = heroScrollBackdropAlpha(sPx, size.height) * focusAlpha
                            } else {
                                scaleX = effZoom
                                scaleY = effZoom
                                translationX = -fx * size.width * (effZoom - 1f) * 0.5f
                                translationY = -fy * size.height * (effZoom - 1f) * 0.5f
                                alpha = focusAlpha
                            }
                        }
                        .then(if (bannerBlurDp > 0) Modifier.blur(bannerBlurDp.dp) else Modifier),
                    contentScale = ContentScale.Crop
                )
            }
        }
    } else {
        Spacer(modifier = Modifier.height(72.dp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .let { mod -> if (isCinemaShowcase) mod else mod.offset(y = (-8).dp) },
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (showInlineOptionsButton) Modifier.padding(horizontal = 40.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
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
                        .combinedClickable(
                            onClick = { onSearchInDiscover(item.title) },
                            onLongClick = { onToggleTitle?.invoke() }
                        ),
                    contentAlignment = Alignment.Center
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
                            alignment = Alignment.Center,
                            onError = { clearartFailed = true }
                        )
                    } else {
                        Text(
                            text = item.title,
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 29.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (item.nativeTitle != null) {
                    Text(
                        text = item.nativeTitle,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center
                    )
                }
                if (!item.bookAuthor.isNullOrBlank() && item.mediaType !in setOf(MediaType.BOOK, MediaType.FANFICTION)) {
                    Text(
                        text = "by ${item.bookAuthor}",
                        color = AccentGold.copy(alpha = 0.92f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                ) {
                    val heroTypeBadge = when (item.mediaType) {
                        MediaType.MANGA -> com.omnitrack.app.ui.screens.home.inferMangaSubtype(item.originCountry, item.nativeTitle).uppercase()
                        MediaType.NOVEL -> {
                            val sub = com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                                item.platform, item.originCountry, item.anilistId, item.malId, item.totalProgress, item.title
                            )
                            if (sub == "LIGHT_NOVEL") "LIGHT NOVEL" else "WEB NOVEL"
                        }
                        MediaType.COMIC -> item.comicFormat?.let { ComicFormat.fromName(it).label.uppercase() } ?: "COMICS"
                        MediaType.FANFICTION -> "${com.omnitrack.app.data.model.FanficSite.fromCode(item.fanficSourceSite).shortBadge} FANFIC"
                        else -> item.mediaType.displayName.uppercase()
                    }
                    Text(
                        text = heroTypeBadge,
                        color = AccentGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    val heroSubInfo = when (item.mediaType) {
                        MediaType.NOVEL -> item.originCountry?.uppercase()
                        MediaType.BOOK -> listOfNotNull(
                            bookDetailMeta.literaryForm?.label?.uppercase(),
                            item.releaseYear?.takeIf { it.isNotBlank() },
                            bookDetailMeta.authorName ?: item.bookAuthor
                        ).joinToString(" · ").takeIf { it.isNotBlank() }
                        MediaType.COMIC -> listOfNotNull(
                            comicDetailMeta.publisher ?: item.comicPublisher,
                            item.releaseYear?.takeIf { it.isNotBlank() }
                        ).joinToString(" · ").takeIf { it.isNotBlank() }
                        MediaType.FANFICTION -> listOfNotNull(
                            item.fanficPrimaryFandom,
                            com.omnitrack.app.data.model.formatWordCountShort(item.fanficWordCount).takeIf { it.isNotBlank() },
                            item.bookAuthor
                        ).joinToString(" · ").takeIf { it.isNotBlank() }
                        else -> item.platform
                    }
                    if (!heroSubInfo.isNullOrBlank()) {
                        Text(text = " • ", color = TextSecondary, fontSize = 11.sp)
                        Text(text = heroSubInfo, color = TextPrimary, fontSize = 11.5.sp)
                    }
                }
            }

            if (showInlineOptionsButton) {
                IconButton(
                    onClick = onOptionsClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (item.mediaType != MediaType.BOOK && item.mediaType != MediaType.COMIC && item.mediaType != MediaType.FANFICTION) {
            DetailNuvioMetaRow(item = item, centered = true)
        }
        NuvioRatingsRow(
            item = item,
            anilistScore = novelDetailMeta.anilistScore,
            malScore = novelDetailMeta.malScore,
            kitsuScore = novelDetailMeta.kitsuScore,
            mangaUpdatesScore = novelDetailMeta.mangaUpdatesScore,
            bangumiScore = novelDetailMeta.bangumiScore,
            vnDetailMeta = vnDetailMeta,
            gameDetailMeta = gameDetailMeta,
            bookDetailMeta = bookDetailMeta,
            comicDetailMeta = comicDetailMeta,
            hiddenSources = hiddenRatingSources,
            onLongPressBadge = onLongPressRatingBadge,
            centered = true
        )
        if (isPreviewMode) {
            DetailPreviewAddToLibraryBar(
                onAddToLibrary = onAddToLibrary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        } else {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailCenteredCoverHeroSection(
    item: com.omnitrack.app.data.db.entity.MediaItemEntity,
    coverImageModel: Any?,
    novelDetailMeta: NovelDetailMeta = NovelDetailMeta(),
    vnDetailMeta: VnDetailMeta = VnDetailMeta(),
    gameDetailMeta: GameDetailMeta = GameDetailMeta(),
    bookDetailMeta: BookDetailMeta = BookDetailMeta(),
    comicDetailMeta: ComicDetailMeta = ComicDetailMeta(),
    hiddenRatingSources: Set<String> = emptySet(),
    onLongPressRatingBadge: ((RatingBadgeData) -> Unit)? = null,
    showInlineOptionsButton: Boolean = false,
    onCoverClick: () -> Unit,
    onCoverLongClick: (() -> Unit)? = null,
    onToggleTitle: (() -> Unit)? = null,
    onSearchInDiscover: (String) -> Unit = {},
    onOptionsClick: () -> Unit,
    onEditProgress: () -> Unit,
    onEditStatus: () -> Unit,
    onEditScore: () -> Unit,
    onOpenTrackers: () -> Unit,
    isPreviewMode: Boolean = false,
    onAddToLibrary: () -> Unit = {}
) {
    val context = LocalContext.current
    val isEditorialBookLike = item.mediaType in setOf(MediaType.BOOK, MediaType.COMIC, MediaType.FANFICTION)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(44.dp))

        // Centered 2:3 Cover Art with Subtle Radial Ambient Glow
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp, 260.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                AccentGold.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .width(158.dp)
                    .height(238.dp)
                    .shadow(24.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .combinedClickable(
                        onClick = { onCoverClick() },
                        onLongClick = { onCoverLongClick?.invoke() }
                    )
            ) {
                val hasJacketImg = coverImageModel != null && (coverImageModel !is String || coverImageModel.isNotBlank())
                if (item.mediaType == MediaType.FANFICTION && !hasJacketImg) {
                    FanficTypographicCover(
                        title = item.title,
                        author = item.bookAuthor,
                        primaryFandom = item.fanficPrimaryFandom,
                        sourceSite = item.fanficSourceSite,
                        ratingCode = item.fanficRating,
                        wordCount = item.fanficWordCount,
                        isCrossover = item.fanficIsCrossover,
                        compact = false,
                        isBlurred = false
                    )
                } else {
                    val centeredCoverReq = remember(coverImageModel, context) {
                        ImageRequest.Builder(context)
                            .data(coverImageModel)
                            .crossfade(false)
                            .build()
                    }
                    AsyncImage(
                        model = centeredCoverReq,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (isEditorialBookLike) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    0.0f to Color.White.copy(alpha = 0.24f),
                                    0.04f to Color.White.copy(alpha = 0.06f),
                                    0.10f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.10f)
                                )
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                )
            }
        }

        // Centered Title, Native Title, Author/Studio, Metadata & Ratings
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (showInlineOptionsButton) Modifier.padding(horizontal = 40.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 28.sp,
                    maxLines = 3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onSearchInDiscover(item.title) },
                            onLongClick = { onToggleTitle?.invoke() }
                        )
                )
                if (!item.nativeTitle.isNullOrBlank()) {
                    Text(
                        text = item.nativeTitle,
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                val creditSub = when (item.mediaType) {
                    MediaType.COMIC -> comicDetailMeta.publisher ?: item.comicPublisher ?: item.bookAuthor
                    MediaType.FANFICTION -> item.bookAuthor?.let { "by $it" } ?: item.fanficPrimaryFandom
                    MediaType.BOOK -> (bookDetailMeta.authorName ?: item.bookAuthor)?.let { "by $it" }
                    else -> item.bookAuthor?.let { "by $it" } ?: item.studioOrNetwork
                }
                if (!creditSub.isNullOrBlank()) {
                    Text(
                        text = creditSub,
                        color = AccentGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
                val centeredTypeBadge = when (item.mediaType) {
                    MediaType.MANGA -> com.omnitrack.app.ui.screens.home.inferMangaSubtype(item.originCountry, item.nativeTitle).uppercase()
                    MediaType.NOVEL -> {
                        val sub = com.omnitrack.app.ui.screens.home.inferNovelSubtype(
                            item.platform, item.originCountry, item.anilistId, item.malId, item.totalProgress, item.title
                        )
                        if (sub == "LIGHT_NOVEL") "LIGHT NOVEL" else "WEB NOVEL"
                    }
                    MediaType.COMIC -> item.comicFormat?.let { ComicFormat.fromName(it).label.uppercase() } ?: "COMICS"
                    MediaType.FANFICTION -> com.omnitrack.app.data.model.FanficSite.fromCode(item.fanficSourceSite).displayName.uppercase()
                    MediaType.BOOK -> bookDetailMeta.literaryForm?.label?.uppercase() ?: "BOOK"
                    else -> item.mediaType.displayName.uppercase()
                }
                val metaParts = when (item.mediaType) {
                    MediaType.COMIC -> listOfNotNull(
                        centeredTypeBadge,
                        comicDetailMeta.startYear ?: item.releaseYear?.takeIf { it.isNotBlank() },
                        (item.totalProgress?.toInt() ?: comicDetailMeta.issueCount)?.takeIf { it > 0 }?.let { "$it ISSUES" }
                    )
                    MediaType.FANFICTION -> listOfNotNull(
                        centeredTypeBadge,
                        item.fanficPrimaryFandom?.uppercase(),
                        com.omnitrack.app.data.model.formatWordCountShort(item.fanficWordCount).takeIf { it.isNotBlank() }?.uppercase(),
                        item.totalProgress?.toInt()?.takeIf { it > 0 }?.let { "$it CH" }
                    )
                    MediaType.BOOK -> listOfNotNull(
                        centeredTypeBadge,
                        item.releaseYear?.takeIf { it.isNotBlank() },
                        item.totalProgress?.toInt()?.takeIf { it > 0 }?.let { "$it PAGES" }
                            ?: bookDetailMeta.pageCount?.takeIf { it > 0 }?.let { "$it PAGES" }
                            ?: bookDetailMeta.lineCount?.takeIf { it > 0 }?.let { "$it LINES" }
                    )
                    MediaType.NOVEL -> listOfNotNull(
                        centeredTypeBadge,
                        item.originCountry?.uppercase(),
                        item.releaseYear?.takeIf { it.isNotBlank() }
                    )
                    else -> listOfNotNull(
                        centeredTypeBadge,
                        item.platform?.takeIf { it.isNotBlank() }?.uppercase()
                    )
                }.joinToString(" · ")
                Text(
                    text = metaParts,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (showInlineOptionsButton) {
                IconButton(
                    onClick = onOptionsClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (!isEditorialBookLike) {
            DetailNuvioMetaRow(item = item, centered = true)
        }
        NuvioRatingsRow(
            item = item,
            anilistScore = novelDetailMeta.anilistScore,
            malScore = novelDetailMeta.malScore,
            kitsuScore = novelDetailMeta.kitsuScore,
            mangaUpdatesScore = novelDetailMeta.mangaUpdatesScore,
            bangumiScore = novelDetailMeta.bangumiScore,
            vnDetailMeta = vnDetailMeta,
            gameDetailMeta = gameDetailMeta,
            bookDetailMeta = bookDetailMeta,
            comicDetailMeta = comicDetailMeta,
            hiddenSources = hiddenRatingSources,
            onLongPressBadge = onLongPressRatingBadge,
            centered = true
        )
        if (isPreviewMode) {
            DetailPreviewAddToLibraryBar(
                onAddToLibrary = onAddToLibrary,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        } else {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailClassicHeaderSection(
    item: com.omnitrack.app.data.db.entity.MediaItemEntity,
    coverImageModel: Any?,
    backdropTargetUrl: String?,
    bannerBlurDp: Int = 0,
    englishTitle: String? = null,
    romajiTitle: String? = null,
    heroClearLogoUrl: String? = null,
    novelDetailMeta: NovelDetailMeta = NovelDetailMeta(),
    vnDetailMeta: VnDetailMeta = VnDetailMeta(),
    gameDetailMeta: GameDetailMeta = GameDetailMeta(),
    bookDetailMeta: BookDetailMeta = BookDetailMeta(),
    comicDetailMeta: ComicDetailMeta = ComicDetailMeta(),
    hiddenRatingSources: Set<String> = emptySet(),
    onLongPressRatingBadge: ((RatingBadgeData) -> Unit)? = null,
    scrollOffsetPx: () -> Float = { 0f },
    stretchPx: () -> Float = { 0f },
    animateOnScroll: Boolean = true,
    onToggleTitle: (() -> Unit)? = null,
    onSearchInDiscover: (String) -> Unit = {},
    onCoverClick: () -> Unit,
    onCoverLongClick: (() -> Unit)? = null,
    onCoverLoaded: (String) -> Unit,
    onOptionsClick: () -> Unit,
    onEditProgress: () -> Unit,
    onEditStatus: () -> Unit,
    onEditScore: () -> Unit,
    onOpenTrackers: () -> Unit,
    isPreviewMode: Boolean = false,
    onAddToLibrary: () -> Unit = {}
) {
    val hasBackdrop = backdropTargetUrl != null
    val posterOffset = if (hasBackdrop) (-50).dp else 0.dp
    val statsOffset = if (hasBackdrop) (-30).dp else 0.dp

    if (hasBackdrop) {
        val hasManualFraming = kotlin.math.abs(item.bannerFocalX) > 0.01f ||
            kotlin.math.abs(item.bannerFocalY) > 0.01f ||
            kotlin.math.abs(item.bannerZoom - 1f) > 0.01f
        val focusState = rememberBackdropFocusState(cacheKey = backdropTargetUrl, enabled = !hasManualFraming)
        val effectiveBannerAlignment = if (hasManualFraming) {
            BiasAlignment(item.bannerFocalX.coerceIn(-1f, 1f), item.bannerFocalY.coerceIn(-1f, 1f))
        } else {
            focusState.focusedAlignment(Alignment.Center)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (animateOnScroll) Modifier.heroStretchHeight(350.dp, stretchPx)
                    else Modifier.height(350.dp)
                )
                .clipToBounds()
                .featherArtwork(startYRatio = 0.50f)
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
                alignment = effectiveBannerAlignment,
                onSuccess = { state -> if (!hasManualFraming) focusState.onImageLoaded(state.result.image) },
                onError = { focusState.onImageFailed() },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fx = item.bannerFocalX.coerceIn(-1f, 1f)
                        val fy = item.bannerFocalY.coerceIn(-1f, 1f)
                        val effZoom = item.bannerZoom.coerceIn(1f, 2.5f)
                        val focusAlpha = if (hasManualFraming) 1f else focusState.revealAlpha
                        if (animateOnScroll) {
                            val sPx = scrollOffsetPx()
                            val stretch = stretchPx().coerceAtLeast(0f)
                            val stretchZoom = 1f + stretch / size.height.coerceAtLeast(1f)
                            val scrollScale = heroScrollBackdropScale(sPx)
                            val totalZoom = effZoom * stretchZoom * scrollScale
                            if (stretch > 0f) {
                                transformOrigin = TransformOrigin(0.5f, 0f)
                            }
                            scaleX = totalZoom
                            scaleY = totalZoom
                            translationX = -fx * size.width * (effZoom - 1f) * 0.5f
                            translationY = -fy * size.height * (effZoom - 1f) * 0.5f + sPx * 0.48f
                            alpha = heroScrollBackdropAlpha(sPx, size.height) * focusAlpha
                        } else {
                            scaleX = effZoom
                            scaleY = effZoom
                            translationX = -fx * size.width * (effZoom - 1f) * 0.5f
                            translationY = -fy * size.height * (effZoom - 1f) * 0.5f
                            alpha = focusAlpha
                        }
                    }
                    .then(if (bannerBlurDp > 0) Modifier.blur(bannerBlurDp.dp) else Modifier),
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
                .combinedClickable(
                    onClick = { onCoverClick() },
                    onLongClick = { onCoverLongClick?.invoke() }
                )
        ) {
            val hasClassicCoverImg = coverImageModel != null && (coverImageModel !is String || coverImageModel.isNotBlank())
            if (item.mediaType == MediaType.FANFICTION && !hasClassicCoverImg) {
                FanficTypographicCover(
                    title = item.title,
                    author = item.bookAuthor,
                    primaryFandom = item.fanficPrimaryFandom,
                    sourceSite = item.fanficSourceSite,
                    ratingCode = item.fanficRating,
                    wordCount = item.fanficWordCount,
                    isCrossover = item.fanficIsCrossover,
                    compact = true,
                    isBlurred = false
                )
            } else {
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
                            .combinedClickable(
                                onClick = { onSearchInDiscover(item.title) },
                                onLongClick = { onToggleTitle?.invoke() }
                            ),
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
                    if (!item.bookAuthor.isNullOrBlank() && item.mediaType !in setOf(MediaType.BOOK, MediaType.FANFICTION)) {
                        Text(
                            text = "by ${item.bookAuthor}",
                            color = AccentGold.copy(alpha = 0.92f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
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
                    MediaType.COMIC -> item.comicFormat?.let { ComicFormat.fromName(it).label.uppercase() } ?: "COMICS"
                    MediaType.FANFICTION -> "${com.omnitrack.app.data.model.FanficSite.fromCode(item.fanficSourceSite).shortBadge} FANFIC"
                    else -> item.mediaType.displayName.uppercase()
                }
                Text(
                    text = compactTypeBadge,
                    color = AccentGold,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                val compactSubInfo = when (item.mediaType) {
                    MediaType.NOVEL -> item.originCountry?.uppercase()
                    MediaType.BOOK -> listOfNotNull(
                        bookDetailMeta.literaryForm?.label?.uppercase(),
                        item.releaseYear?.takeIf { it.isNotBlank() },
                        bookDetailMeta.authorName ?: item.bookAuthor
                    ).joinToString(" · ").takeIf { it.isNotBlank() }
                    MediaType.COMIC -> listOfNotNull(
                        comicDetailMeta.publisher ?: item.comicPublisher,
                        item.releaseYear?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ").takeIf { it.isNotBlank() }
                    MediaType.FANFICTION -> listOfNotNull(
                        item.fanficPrimaryFandom,
                        com.omnitrack.app.data.model.formatWordCountShort(item.fanficWordCount).takeIf { it.isNotBlank() },
                        item.bookAuthor
                    ).joinToString(" · ").takeIf { it.isNotBlank() }
                    else -> item.platform
                }
                if (!compactSubInfo.isNullOrBlank()) {
                    Text(text = " • ", color = TextSecondary, fontSize = 10.5.sp)
                    Text(text = compactSubInfo, color = TextPrimary, fontSize = 11.sp)
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
            if (item.mediaType != MediaType.BOOK && item.mediaType != MediaType.COMIC) {
                DetailNuvioMetaRow(item = item)
            }
            NuvioRatingsRow(
                item = item,
                anilistScore = novelDetailMeta.anilistScore,
                malScore = novelDetailMeta.malScore,
                kitsuScore = novelDetailMeta.kitsuScore,
                mangaUpdatesScore = novelDetailMeta.mangaUpdatesScore,
                bangumiScore = novelDetailMeta.bangumiScore,
                vnDetailMeta = vnDetailMeta,
                gameDetailMeta = gameDetailMeta,
                bookDetailMeta = bookDetailMeta,
                comicDetailMeta = comicDetailMeta,
                hiddenSources = hiddenRatingSources,
                onLongPressBadge = onLongPressRatingBadge
            )
            if (isPreviewMode) {
                DetailPreviewAddToLibraryBar(
                    onAddToLibrary = onAddToLibrary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
                )
            } else {
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
}

@Composable
private fun DetailPreviewAddToLibraryBar(
    onAddToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onAddToLibrary,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentGold,
            contentColor = OnAccentColor
        )
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = OnAccentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Add to Library",
            color = OnAccentColor,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun findAwardWinnerLibraryMatch(
    library: List<MediaItemEntity>,
    winner: AwardWinnerEntry
): MediaItemEntity? {
    val winnerImdb = winner.imdbId?.trim()?.takeIf { it.isNotBlank() }
    if (winnerImdb != null) {
        library.firstOrNull { it.imdbId?.trim()?.equals(winnerImdb, ignoreCase = true) == true }?.let { return it }
    }
    val winnerTmdb = winner.tmdbId?.takeIf { it > 0L }
    if (winnerTmdb != null) {
        val isWinnerMovie = winner.mediaType == MediaType.MOVIE
        library.firstOrNull {
            it.tmdbId == winnerTmdb && (it.mediaType == MediaType.MOVIE) == isWinnerMovie
        }?.let { return it }
    }
    fun normalizeAwardTitle(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]"), "")
    val normWinnerTitle = normalizeAwardTitle(winner.title)
    if (normWinnerTitle.isBlank()) return null
    library.firstOrNull {
        it.mediaType == winner.mediaType && normalizeAwardTitle(it.title) == normWinnerTitle
    }?.let { return it }
    val winnerYear = winner.year?.toString()
    if (winnerYear != null) {
        library.firstOrNull {
            it.releaseYear == winnerYear && normalizeAwardTitle(it.title) == normWinnerTitle
        }?.let { return it }
    }
    return library.firstOrNull { normalizeAwardTitle(it.title) == normWinnerTitle }
}

@Composable
private fun DetailSynopsisSection(
    synopsis: String,
    dominantAccent: Color,
    isVisualNovel: Boolean = false,
    vnSpoilerLevel: VnSpoilerLevel = VnSpoilerLevel.MINOR
) {
    val context = LocalContext.current
    var synopsisExpanded by remember { mutableStateOf(false) }
    var canExpandSynopsis by remember { mutableStateOf(false) }
    var revealedSpoilerIndices by remember(synopsis) { mutableStateOf<Set<Int>>(emptySet()) }
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsInstance by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }

    val baseClean = remember(synopsis) {
        synopsis
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]*>"""), "")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .replace(Regex("""\s*[\(\[]\s*$"""), "")
            .trim()
    }
    val vnSegments = remember(baseClean, isVisualNovel) {
        if (isVisualNovel) parseVndbBbCodeSegments(baseClean) else emptyList()
    }
    val hasInlineVnSpoilers = remember(vnSegments) { vnSegments.any { it.second } }
    val cleanText = remember(baseClean, vnSegments, hasInlineVnSpoilers, isVisualNovel) {
        when {
            hasInlineVnSpoilers -> vnSegments.filter { !it.second }.joinToString(" ") { it.first.trim() }.trim()
            isVisualNovel -> vnSegments.joinToString("") { it.first }.trim()
            else -> baseClean
        }
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

    if (cleanText.isBlank() && !hasInlineVnSpoilers) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp)
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
                    enabled = canExpandSynopsis && (!hasInlineVnSpoilers || !synopsisExpanded),
                    onClick = { synopsisExpanded = !synopsisExpanded }
                )
        ) {
            if (isVisualNovel && hasInlineVnSpoilers && synopsisExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vnSegments.forEachIndexed { idx, (segText, isSpoilerSeg) ->
                        if (isSpoilerSeg && vnSpoilerLevel != VnSpoilerLevel.ALL && idx !in revealedSpoilerIndices) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(6.dp))
                                    .clickable { revealedSpoilerIndices = revealedSpoilerIndices + idx }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "Spoiler — tap to reveal",
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Text(
                                text = segText.trim(),
                                color = Color.White.copy(alpha = 0.90f),
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            } else {
                val previewText = if (hasInlineVnSpoilers && cleanText.isBlank()) {
                    "[Contains plot spoiler — tap to expand]"
                } else {
                    cleanText
                }
                FormattedRichText(
                    rawText = previewText,
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textLayoutResult ->
                        if (!synopsisExpanded) {
                            canExpandSynopsis = textLayoutResult.hasVisualOverflow ||
                                textLayoutResult.lineCount > 3 ||
                                hasInlineVnSpoilers
                        }
                    }
                )
            }
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
            MediaType.MANGA, MediaType.NOVEL, MediaType.COMIC, MediaType.FANFICTION -> {
                ChapterStepper(
                    unitLabel = item.mediaType.unitLabel,
                    currentProgress = item.currentProgress,
                    totalProgress = item.totalProgress,
                    onProgressChanged = onUpdateProgress
                )
            }

            MediaType.GAME -> {
                // Retired legacy boxed GameProgressCard; rendered by borderless DetailGameTrackingSection
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
    isCustomEntry: Boolean = false,
    canLinkEditions: Boolean = false,
    canManageSeasons: Boolean = false,
    onDismiss: () -> Unit,
    onManageSeasons: () -> Unit = {},
    onLinkEditions: () -> Unit = {},
    onToggleTitleLanguage: () -> Unit = {},
    onEditDetails: () -> Unit = {},
    onMatchAndEnrich: () -> Unit = {},
    onChangeArtwork: () -> Unit,
    onToggleLayout: () -> Unit,
    onManageRatings: () -> Unit = {},
    onRemoveFromLibrary: () -> Unit
) {
    val optionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = optionsSheetState,
        containerColor = AmoledBlack,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Entry Options",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // 1. Edit Details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onEditDetails() }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Edit Details",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Modify title, author, synopsis, progress & metadata lock",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // 2. Match & Enrich Online (shown for custom / manual-locked entries)
            if (isCustomEntry) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onMatchAndEnrich() }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Match & Enrich Online",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "One-time fetch of online trailers, reviews & metadata",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 3. Change Artwork
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
                    tint = AccentGold,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Change Artwork, Framing & Roles",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Pick covers/banners, pan & zoom banner focal point, swap roles",
                        color = TextSecondary,
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
                        text = when (presentationSettings.detailsPagePresentation) {
                            DetailsPagePresentation.HERO -> "Current: Banner Only (Tap for Centered Cover)"
                            DetailsPagePresentation.CENTERED_COVER -> "Current: Centered Cover (Tap for Classic)"
                            DetailsPagePresentation.CLASSIC -> "Current: Classic Poster (Tap for Banner Only)"
                        },
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            // Switch Title Language
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleTitleLanguage() }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Switch Title Language",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Cycle between English, Romaji, and native title",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            // Manage Ratings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onManageRatings() }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Manage Ratings",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Show or hide individual rating badges on Details & Home banner",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            if (canManageSeasons) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onManageSeasons() }
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
                            text = "Merge & Customize Seasons",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Add sequels, movies, OVAs, reorder, rename or customize covers",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (canLinkEditions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onLinkEditions() }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Link / Combine Edition",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Connect Anime, Manga, Novel, Book, or Game adaptations together",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
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
