package com.omnitrack.app.ui.screens.detail.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.data.enrichment.EnrichmentRepository
import com.omnitrack.app.data.model.AnimeCharacterDetail
import com.omnitrack.app.data.model.AnimeCharacterItem
import com.omnitrack.app.data.model.AnimeCharacterVoiceActor
import com.omnitrack.app.data.model.AnimeStaffItem
import com.omnitrack.app.data.model.AnimeVoiceActorDetail
import com.omnitrack.app.ui.components.AniListMarkdownText
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.SurfaceCard
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

private fun formatCompactNumber(num: Int?): String? {
    if (num == null || num <= 0) return null
    return if (num >= 1000) String.format("%.1fk", num / 1000f) else num.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeCharacterSheet(
    character: AnimeCharacterItem?,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismissRequest: () -> Unit,
    onOpenMediaPreview: ((RecommendationItem) -> Unit)? = null
) {
    if (character == null) return
    val context = LocalContext.current
    val enrichmentRepo = remember { EnrichmentRepository() }

    var showingVoiceActor by remember { mutableStateOf(false) }
    var selectedStaffId by remember { mutableStateOf<Long?>(null) }
    var selectedStaffFallbackName by remember { mutableStateOf<String?>(null) }
    var selectedStaffFallbackImage by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = showingVoiceActor) {
        showingVoiceActor = false
    }

    var isLoading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<AnimeCharacterDetail?>(null) }

    LaunchedEffect(character.characterId) {
        isLoading = true
        showingVoiceActor = false
        detail = enrichmentRepo.fetchAnimeCharacterDetail(character.characterId)
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null,
        modifier = Modifier.navigationBarsPadding()
    ) {
        if (showingVoiceActor && (selectedStaffId != null || character.voiceActorId != null)) {
            StaffDetailContent(
                staffId = selectedStaffId ?: character.voiceActorId ?: 0L,
                fallbackName = selectedStaffFallbackName ?: character.voiceActorName ?: "Voice Actor",
                fallbackImageUrl = selectedStaffFallbackImage ?: character.voiceActorImageUrl,
                onBack = { showingVoiceActor = false },
                onOpenMedia = { item ->
                    onDismissRequest()
                    onOpenMediaPreview?.invoke(item)
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Character Header (100% Unboxed)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(detail?.imageUrl ?: character.characterImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = detail?.name ?: character.characterName,
                        modifier = Modifier
                            .width(84.dp)
                            .height(112.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = detail?.name ?: character.characterName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val nativeName = detail?.nativeName
                        if (!nativeName.isNullOrBlank()) {
                            Text(
                                text = nativeName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = TextSecondary
                            )
                        }

                        // Unboxed Role Label
                        Text(
                            text = if (character.characterRole == "MAIN") "MAIN CHARACTER" else "SUPPORTING CHARACTER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (character.characterRole == "MAIN") AccentArctic else TextMuted,
                            letterSpacing = 0.4.sp
                        )
                    }
                }

                // 100% Unboxed Vitals (NO BOXES / NO CHIPS)
                val vitalsList = buildList {
                    detail?.age?.takeIf { it.isNotBlank() }?.let { add("$it yrs") }
                    detail?.gender?.takeIf { it.isNotBlank() }?.let { add(it) }
                    detail?.dateOfBirth?.takeIf { it.isNotBlank() }?.let { add(it) }
                    detail?.bloodType?.takeIf { it.isNotBlank() }?.let { add("Blood $it") }
                    formatCompactNumber(detail?.favourites)?.let { add("$it ♥") }
                }
                if (vitalsList.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = vitalsList.joinToString("  •  "),
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                        val altNames = detail?.alternativeNames.orEmpty()
                        if (altNames.isNotEmpty()) {
                            Text(
                                text = "AKA: ${altNames.take(4).joinToString(", ")}",
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Voice Actors Section (Supports multiple languages or fallback VA)
                val voiceActors = detail?.voiceActors.orEmpty()
                val hasVa = voiceActors.isNotEmpty() || !character.voiceActorName.isNullOrBlank()

                if (voiceActors.size > 1) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Voice Actors",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(voiceActors, key = { it.staffId }) { va ->
                                Row(
                                    modifier = Modifier
                                        .clickable {
                                            selectedStaffId = va.staffId
                                            selectedStaffFallbackName = va.name
                                            selectedStaffFallbackImage = va.imageUrl
                                            showingVoiceActor = true
                                        }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(va.imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = va.name,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, GlassBorder, CircleShape),
                                        contentScale = ContentScale.Crop
                                    )

                                    Column {
                                        Text(
                                            text = va.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = va.language,
                                            fontSize = 11.5.sp,
                                            color = AccentArctic,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (hasVa) {
                    val singleVa = voiceActors.firstOrNull()
                    val vaName = singleVa?.name ?: character.voiceActorName!!
                    val vaImage = singleVa?.imageUrl ?: character.voiceActorImageUrl
                    val vaLang = singleVa?.language ?: character.voiceActorLanguage
                    val vaId = singleVa?.staffId ?: character.voiceActorId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = vaId != null) {
                                selectedStaffId = vaId
                                selectedStaffFallbackName = vaName
                                selectedStaffFallbackImage = vaImage
                                showingVoiceActor = true
                            }
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(vaImage)
                                .crossfade(true)
                                .build(),
                            contentDescription = vaName,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .border(1.dp, GlassBorder, CircleShape),
                            contentScale = ContentScale.Crop
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = vaName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Voice Actor • $vaLang",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }

                        if (vaId != null) {
                            Text(
                                text = "›",
                                fontSize = 22.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }
                }

                // Loading Spinner while detail is loading
                if (isLoading && detail == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AccentArctic,
                            strokeWidth = 2.dp
                        )
                    }
                }

                // Biography Section with Clickable Markdown / Spoilers
                val biography = detail?.biography
                if (!biography.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "About",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        AniListMarkdownText(
                            text = biography,
                            collapsedMaxLines = 4
                        )
                    }
                }

                // Appearances Rail (100% Unboxed Poster Art)
                val appearances = detail?.appearances.orEmpty()
                if (appearances.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Appearances",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(appearances, key = { index, it -> "${it.id}_${it.title}_$index" }) { _, appItem ->
                                Column(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .clickable {
                                            onDismissRequest()
                                            onOpenMediaPreview?.invoke(
                                                RecommendationItem(
                                                    title = appItem.title,
                                                    coverUrl = appItem.coverUrl,
                                                    bannerUrl = appItem.bannerUrl,
                                                    mediaType = appItem.mediaType,
                                                    rating = appItem.score,
                                                    tmdbId = null,
                                                    anilistId = appItem.id,
                                                    malId = appItem.malId,
                                                    releaseYear = if (!appItem.releaseYear.isNullOrBlank()) "${appItem.releaseYear} • ${appItem.characterRole}" else appItem.characterRole
                                                )
                                            )
                                        },
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(2f / 3f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(appItem.coverUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = appItem.title,
                                            modifier = Modifier.matchParentSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    Text(
                                        text = appItem.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    val yearRole = if (!appItem.releaseYear.isNullOrBlank()) "${appItem.releaseYear} • ${appItem.format}" else appItem.format
                                    Text(
                                        text = yearRole,
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Standalone BottomSheet for viewing any Staff member (Director, Music, Creator, Voice Actor).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeStaffSheet(
    staff: AnimeStaffItem?,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismissRequest: () -> Unit,
    onOpenMediaPreview: ((RecommendationItem) -> Unit)? = null
) {
    if (staff == null) return

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null,
        modifier = Modifier.navigationBarsPadding()
    ) {
        StaffDetailContent(
            staffId = staff.staffId,
            fallbackName = staff.name,
            fallbackImageUrl = staff.imageUrl,
            onBack = null,
            onOpenMedia = { item ->
                onDismissRequest()
                onOpenMediaPreview?.invoke(item)
            }
        )
    }
}

/**
 * 100% Unboxed AMOLED Staff Profile Content (both standalone & in-character transition).
 */
@Composable
private fun StaffDetailContent(
    staffId: Long,
    fallbackName: String,
    fallbackImageUrl: String?,
    onBack: (() -> Unit)? = null,
    onOpenMedia: (RecommendationItem) -> Unit
) {
    val context = LocalContext.current
    val enrichmentRepo = remember { EnrichmentRepository() }
    var isLoading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<AnimeVoiceActorDetail?>(null) }

    LaunchedEffect(staffId) {
        isLoading = true
        detail = enrichmentRepo.fetchVoiceActorDetails(staffId)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Back Navigation Button (if transitioning from character)
        if (onBack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBack() }
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "‹",
                    fontSize = 22.sp,
                    color = AccentArctic,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Back to Character",
                    fontSize = 14.sp,
                    color = AccentArctic,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Staff Profile Header (100% Unboxed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(detail?.imageUrl ?: fallbackImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = detail?.name ?: fallbackName,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, GlassBorder, CircleShape),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = detail?.name ?: fallbackName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val nativeName = detail?.nativeName
                if (!nativeName.isNullOrBlank()) {
                    Text(
                        text = nativeName,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                val primaryOcc = detail?.primaryOccupations.orEmpty()
                if (primaryOcc.isNotEmpty()) {
                    Text(
                        text = primaryOcc.joinToString(" • "),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentArctic
                    )
                }
            }
        }

        // 100% Unboxed Vitals (NO BOXES / NO CHIPS)
        val vitalsList = buildList {
            detail?.age?.let { add("$it yrs") }
            detail?.gender?.takeIf { it.isNotBlank() }?.let { add(it) }
            detail?.dateOfBirth?.takeIf { it.isNotBlank() }?.let { add("Born $it") }
            detail?.homeTown?.takeIf { it.isNotBlank() }?.let { add(it) }
            detail?.yearsActive?.takeIf { it.isNotBlank() }?.let { add("Active $it") }
            detail?.bloodType?.takeIf { it.isNotBlank() }?.let { add("Blood $it") }
            formatCompactNumber(detail?.favourites)?.let { add("$it ♥") }
        }
        if (vitalsList.isNotEmpty()) {
            Text(
                text = vitalsList.joinToString("  •  "),
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // Loading spinner while details load
        if (isLoading && detail == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = AccentArctic,
                    strokeWidth = 2.dp
                )
            }
        }

        // Biography with Clickable Links
        val bioText = detail?.biography
        if (!bioText.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Biography",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                AniListMarkdownText(
                    text = bioText,
                    collapsedMaxLines = 4
                )
            }
        }

        // Production Credits Rail (Director, Music, Creator, etc.)
        val productions = detail?.productionCredits.orEmpty()
        if (productions.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Production Credits",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(productions, key = { index, it -> "${it.mediaId}_${it.staffRole}_$index" }) { _, prodItem ->
                        Column(
                            modifier = Modifier
                                .width(96.dp)
                                .clickable {
                                    val rec = RecommendationItem(
                                        title = prodItem.mediaTitle,
                                        coverUrl = prodItem.mediaCoverUrl ?: "",
                                        bannerUrl = prodItem.mediaCoverUrl,
                                        mediaType = prodItem.mediaType,
                                        rating = null,
                                        tmdbId = null,
                                        anilistId = prodItem.mediaId,
                                        malId = null,
                                        releaseYear = prodItem.mediaYear?.toString()
                                    )
                                    onOpenMedia(rec)
                                },
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                            ) {
                                if (!prodItem.mediaCoverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(prodItem.mediaCoverUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = prodItem.mediaTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.matchParentSize()
                                    )
                                }
                            }

                            Text(
                                text = prodItem.mediaTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = prodItem.staffRole,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentArctic,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            val metaStr = buildList {
                                prodItem.mediaFormat?.let { add(it) }
                                prodItem.mediaYear?.let { add(it.toString()) }
                            }.joinToString(" • ")

                            if (metaStr.isNotBlank()) {
                                Text(
                                    text = metaStr,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Voiced Characters Rail (Unboxed with Paired Art)
        val roles = detail?.roles.orEmpty()
        if (roles.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Voiced Characters",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(roles, key = { index, it -> "${it.mediaId}_${it.characterId}_$index" }) { _, roleItem ->
                        Column(
                            modifier = Modifier
                                .width(96.dp)
                                .clickable {
                                    val rec = RecommendationItem(
                                        title = roleItem.mediaTitle,
                                        coverUrl = roleItem.mediaCoverUrl ?: "",
                                        bannerUrl = roleItem.mediaCoverUrl,
                                        mediaType = roleItem.mediaType,
                                        rating = null,
                                        tmdbId = null,
                                        anilistId = roleItem.mediaId,
                                        malId = null,
                                        releaseYear = roleItem.mediaYear?.toString()
                                    )
                                    onOpenMedia(rec)
                                },
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(138.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceCard)
                                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                ) {
                                    if (!roleItem.mediaCoverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(roleItem.mediaCoverUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = roleItem.mediaTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.matchParentSize()
                                        )
                                    }
                                }

                                if (!roleItem.characterImageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(roleItem.characterImageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = roleItem.characterName,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .align(Alignment.BottomEnd)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, Color(0xFF000000), CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Text(
                                text = roleItem.characterName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = roleItem.mediaTitle,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
