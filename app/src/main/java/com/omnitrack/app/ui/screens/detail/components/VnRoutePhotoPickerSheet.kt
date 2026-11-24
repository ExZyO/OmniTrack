package com.omnitrack.app.ui.screens.detail.components

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.artwork.ArtworkModelResolver
import com.omnitrack.app.data.artwork.ArtworkWaterfallRepository
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.VnDetailMeta
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Immutable
data class VnRoutePhotoChoice(
    val url: String,
    val label: String,
    val category: String // "Current", "Characters", "CGs", "Banners", "Covers"
)

@Immutable
data class VnRoutePhotoSelection(
    val imageUrl: String?,
    val focalX: Float = 0f,
    val focalY: Float = 0f,
    val zoom: Float = 1f
)

/**
 * Renders a VN route photo with saved non-destructive pan/zoom framing (focalX, focalY, zoom).
 */
@Composable
fun VnRoutePhotoThumb(
    imageUrl: String?,
    focalX: Float = 0f,
    focalY: Float = 0f,
    zoom: Float = 1f,
    contentDescription: String?,
    size: Dp = 52.dp,
    cornerRadius: Dp = 10.dp,
    isBlurred: Boolean = false,
    fallbackText: String = "R",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedModel = remember(imageUrl) { ArtworkModelResolver.toCoilModel(imageUrl) }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .clipToBounds()
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.7.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        if (resolvedModel != null) {
            val fx = focalX.coerceIn(-1f, 1f)
            val fy = focalY.coerceIn(-1f, 1f)
            val effZoom = zoom.coerceIn(1f, 4f)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedModel)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(fx, fy),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = effZoom
                        scaleY = effZoom
                        translationX = -fx * this.size.width * (effZoom - 1f) * 0.5f
                        translationY = -fy * this.size.height * (effZoom - 1f) * 0.5f
                    }
                    .let { if (isBlurred) it.blur(12.dp) else it }
            )
            if (isBlurred) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }
        } else {
            Text(
                text = fallbackText,
                fontSize = (size.value * 0.28f).coerceIn(11f, 18f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.72f),
                modifier = if (isBlurred) Modifier.blur(6.dp) else Modifier
            )
        }
    }
}

private suspend fun copyUriToRoutePhotoFile(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.filesDir, "vn_route_photos").apply { if (!exists()) mkdirs() }
        val outFile = File(dir, "route_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return@withContext null
        outFile.absolutePath
    }.getOrNull()
}

/**
 * Interactive VN Route Photo Picker & Pinch-to-Zoom / Drag-to-Pan Reframing Sheet.
 * Supports picking from Device Gallery, direct URL, VNDB Characters, VNDB CG Screenshots,
 * and live-fetched Covers & Banners across all artwork sources for this Visual Novel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VnRoutePhotoPickerSheet(
    routeTitle: String,
    initialSelection: VnRoutePhotoSelection,
    mediaItem: MediaItemEntity? = null,
    vnMeta: VnDetailMeta? = null,
    extraImageCandidates: List<VnRoutePhotoChoice> = emptyList(),
    onApply: (VnRoutePhotoSelection) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentUrl by remember(initialSelection.imageUrl) { mutableStateOf(initialSelection.imageUrl) }
    var draftFocalX by remember(initialSelection.imageUrl, initialSelection.focalX) {
        mutableFloatStateOf(initialSelection.focalX.coerceIn(-1f, 1f))
    }
    var draftFocalY by remember(initialSelection.imageUrl, initialSelection.focalY) {
        mutableFloatStateOf(initialSelection.focalY.coerceIn(-1f, 1f))
    }
    var draftZoom by remember(initialSelection.imageUrl, initialSelection.zoom) {
        mutableFloatStateOf(initialSelection.zoom.coerceIn(1f, 4f))
    }
    var showFineTuneSliders by remember { mutableStateOf(false) }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlDraft by remember { mutableStateOf("") }
    var isLoadingSources by remember { mutableStateOf(false) }
    var fetchedSourceArt by remember { mutableStateOf<List<VnRoutePhotoChoice>>(emptyList()) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedPath = copyUriToRoutePhotoFile(context, uri)
                if (savedPath != null) {
                    currentUrl = savedPath
                    draftFocalX = 0f
                    draftFocalY = 0f
                    draftZoom = 1f
                } else {
                    Toast.makeText(context, "Could not load selected photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Build immediate local artwork pool (Current cover/banner + VNDB characters + VNDB CG screenshots + extras)
    val immediateChoices = remember(mediaItem, vnMeta, extraImageCandidates) {
        buildList {
            extraImageCandidates.forEach { if (it.url.isNotBlank()) add(it) }
            mediaItem?.let { m ->
                m.customBannerPath?.takeIf { it.isNotBlank() && it != "NONE" }?.let {
                    add(VnRoutePhotoChoice(it, "Custom Banner", "Current"))
                }
                m.bannerUrl?.takeIf { it.isNotBlank() }?.let {
                    add(VnRoutePhotoChoice(it, "Hero Banner", "Current"))
                }
                m.customCoverPath?.takeIf { it.isNotBlank() }?.let {
                    add(VnRoutePhotoChoice(it, "Custom Cover", "Current"))
                }
                m.coverUrl.takeIf { it.isNotBlank() }?.let {
                    add(VnRoutePhotoChoice(it, "VN Cover", "Current"))
                }
            }
            vnMeta?.characterProfiles?.values?.forEach { ch ->
                val img = ch.imageUrl?.takeIf { it.isNotBlank() } ?: return@forEach
                add(VnRoutePhotoChoice(img, ch.name.ifBlank { "Character" }, "Characters"))
            }
            vnMeta?.screenshots?.forEachIndexed { idx, shot ->
                if (shot.url.isNotBlank()) {
                    add(VnRoutePhotoChoice(shot.url, "CG #${idx + 1}", "CGs"))
                }
            }
        }.distinctBy { it.url }
    }

    // Also fetch covers & banners from all artwork sources (VNDB, Steam, SteamGridDB, Bangumi, etc.)
    LaunchedEffect(mediaItem?.id, mediaItem?.title) {
        val m = mediaItem ?: return@LaunchedEffect
        if (m.title.isBlank()) return@LaunchedEffect
        isLoadingSources = true
        runCatching {
            val app = context.applicationContext as? OmniTrackApplication
            val repo = ArtworkWaterfallRepository(app?.preferenceManager)
            val candidates = withContext(Dispatchers.IO) {
                repo.searchArtworkCandidates(
                    title = m.title,
                    mediaType = m.mediaType,
                    anilistId = m.anilistId,
                    malId = m.malId,
                    mangadexId = m.mangadexId,
                    nativeTitle = m.nativeTitle,
                    defaultCoverUrl = m.coverUrl.takeIf { it.isNotBlank() },
                    vndbId = m.vndbId,
                    steamAppId = m.steamAppId,
                    openLibraryId = m.openLibraryId,
                    bookAuthor = m.bookAuthor,
                    onPartialResults = { partial ->
                        val mapped = partial
                            .filter { it.url.isNotBlank() }
                            .map { c ->
                                VnRoutePhotoChoice(
                                    url = c.url,
                                    label = c.sourceName,
                                    category = if (c.isBanner) "Banners" else "Covers"
                                )
                            }
                        if (mapped.isNotEmpty()) {
                            fetchedSourceArt = mapped
                        }
                    }
                )
            }
            fetchedSourceArt = candidates
                .filter { it.url.isNotBlank() }
                .map { c ->
                    VnRoutePhotoChoice(
                        url = c.url,
                        label = c.sourceName,
                        category = if (c.isBanner) "Banners" else "Covers"
                    )
                }
        }
        isLoadingSources = false
    }

    val allChoices = remember(immediateChoices, fetchedSourceArt) {
        (immediateChoices + fetchedSourceArt).distinctBy { it.url }
    }
    var selectedCategory by remember { mutableStateOf("ALL") }
    val categories = remember(allChoices) {
        val cats = allChoices.map { it.category }.distinct()
        if (cats.size > 1) listOf("ALL") + cats else cats
    }
    val visibleChoices = remember(allChoices, selectedCategory) {
        if (selectedCategory == "ALL") allChoices else allChoices.filter { it.category == selectedCategory }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AmoledBlack,
        contentColor = TextPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 740.dp)
                .background(AmoledBlack)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Route Photo",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = routeTitle.ifBlank { "Pinch to zoom \u00B7 Drag to position" },
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!currentUrl.isNullOrBlank()) {
                    Text(
                        text = "Reset Crop",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                draftFocalX = 0f
                                draftFocalY = 0f
                                draftZoom = 1f
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Gesture-First Interactive Square Crop Preview (Two-finger pinch up to 4x + One-finger drag!)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val previewModel = remember(currentUrl) { ArtworkModelResolver.toCoilModel(currentUrl) }
                    Box(
                        modifier = Modifier
                            .size(210.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clipToBounds()
                            .background(DarkSurfaceCard)
                            .border(
                                width = 1.5.dp,
                                color = if (previewModel != null) AccentGold.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .pointerInput(currentUrl) {
                                if (currentUrl.isNullOrBlank()) return@pointerInput
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    do {
                                        val event = awaitPointerEvent()
                                        val zoomChange = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        if (zoomChange != 1f || pan != Offset.Zero) {
                                            val nextZoom = (draftZoom * zoomChange).coerceIn(1f, 4f)
                                            val w = size.width.toFloat().coerceAtLeast(1f)
                                            val h = size.height.toFloat().coerceAtLeast(1f)
                                            val sens = 1.85f / nextZoom.coerceAtLeast(1f)
                                            draftZoom = nextZoom
                                            draftFocalX = (draftFocalX - (pan.x / w) * sens).coerceIn(-1f, 1f)
                                            draftFocalY = (draftFocalY - (pan.y / h) * sens).coerceIn(-1f, 1f)
                                        }
                                        event.changes.forEach { it.consume() }
                                    } while (event.changes.any { it.pressed })
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewModel != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(previewModel)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = "Route Photo Crop Preview",
                                contentScale = ContentScale.Crop,
                                alignment = BiasAlignment(draftFocalX.coerceIn(-1f, 1f), draftFocalY.coerceIn(-1f, 1f)),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val fx = draftFocalX.coerceIn(-1f, 1f)
                                        val fy = draftFocalY.coerceIn(-1f, 1f)
                                        val effZoom = draftZoom.coerceIn(1f, 4f)
                                        scaleX = effZoom
                                        scaleY = effZoom
                                        translationX = -fx * size.width * (effZoom - 1f) * 0.5f
                                        translationY = -fy * size.height * (effZoom - 1f) * 0.5f
                                    }
                            )
                            // Live zoom indicator pill
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AmoledBlack.copy(alpha = 0.78f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Pinch \u00B7 Drag \u00B7 ${String.format(Locale.US, "%.1fx", draftZoom)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clickable {
                                        galleryPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    .padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = AccentGold,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "Pick a photo below or from Gallery",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                // Collapsible Fine-Tune Sliders Fallback (X / Y / Zoom + quick snaps)
                if (!currentUrl.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showFineTuneSliders = !showFineTuneSliders }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = AccentGold, modifier = Modifier.size(15.dp))
                            Text(
                                text = "Fine-tune X / Y / Zoom sliders",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                        }
                        Icon(
                            imageVector = if (showFineTuneSliders) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = showFineTuneSliders) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("X", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AccentGold, modifier = Modifier.width(16.dp))
                                Slider(
                                    value = draftFocalX,
                                    onValueChange = { draftFocalX = it },
                                    valueRange = -1f..1f,
                                    colors = SliderDefaults.colors(thumbColor = AccentGold, activeTrackColor = AccentGold),
                                    modifier = Modifier.weight(1f)
                                )
                                Text("Y", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AccentGold, modifier = Modifier.width(16.dp))
                                Slider(
                                    value = draftFocalY,
                                    onValueChange = { draftFocalY = it },
                                    valueRange = -1f..1f,
                                    colors = SliderDefaults.colors(thumbColor = AccentGold, activeTrackColor = AccentGold),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Zoom", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Slider(
                                    value = draftZoom,
                                    onValueChange = { draftZoom = it },
                                    valueRange = 1f..4f,
                                    colors = SliderDefaults.colors(thumbColor = AccentGold, activeTrackColor = AccentGold),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1fx", draftZoom),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentGold
                                )
                            }
                        }
                    }
                }

                // Device Gallery + Direct URL quick buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            galleryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.16f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = AccentGold, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Gallery", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { showUrlInput = !showUrlInput },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.8.dp, if (showUrlInput) AccentGold else Color.White.copy(alpha = 0.16f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = AccentGold, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Image URL", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }

                AnimatedVisibility(visible = showUrlInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = urlDraft,
                            onValueChange = { urlDraft = it },
                            placeholder = { Text("https://...", color = TextMuted, fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGold,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val clean = urlDraft.trim()
                                if (clean.startsWith("http", ignoreCase = true)) {
                                    currentUrl = clean
                                    draftFocalX = 0f
                                    draftFocalY = 0f
                                    draftZoom = 1f
                                    showUrlInput = false
                                }
                            },
                            enabled = urlDraft.trim().startsWith("http", ignoreCase = true),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = OnAccentColor)
                        ) {
                            Text("Use", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Artwork from VN Sources (Banners, Covers, VNDB Characters & CG Screenshots)
                if (allChoices.isNotEmpty() || isLoadingSources) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VN ARTWORK & SOURCES (${visibleChoices.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.8.sp
                        )
                        if (isLoadingSources) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.8.dp,
                                color = AccentGold
                            )
                        }
                    }

                    if (categories.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { cat ->
                                val selected = selectedCategory == cat
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) AccentGold.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
                                        .clickable { selectedCategory = cat }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = if (cat == "ALL") "All" else cat,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) AccentGold else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(visibleChoices, key = { it.url }) { choice ->
                            val isSelected = currentUrl == choice.url
                            Column(
                                modifier = Modifier
                                    .width(96.dp)
                                    .clickable {
                                        currentUrl = choice.url
                                        draftFocalX = 0f
                                        draftFocalY = 0f
                                        draftZoom = 1f
                                    },
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(if (choice.category == "Banners" || choice.category == "CGs") 1.55f else 1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurfaceCard)
                                        .border(
                                            width = if (isSelected) 1.6.dp else 0.7.dp,
                                            color = if (isSelected) AccentGold else Color.White.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    AsyncImage(
                                        model = ArtworkModelResolver.toCoilModel(choice.url),
                                        contentDescription = choice.label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Text(
                                    text = choice.label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) AccentGold else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!currentUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            onApply(VnRoutePhotoSelection(imageUrl = null, focalX = 0f, focalY = 0f, zoom = 1f))
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.16f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Remove", fontSize = 12.5.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = {
                        onApply(
                            VnRoutePhotoSelection(
                                imageUrl = currentUrl?.trim()?.takeIf { it.isNotBlank() },
                                focalX = draftFocalX.coerceIn(-1f, 1f),
                                focalY = draftFocalY.coerceIn(-1f, 1f),
                                zoom = draftZoom.coerceIn(1f, 4f)
                            )
                        )
                        onDismiss()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = OnAccentColor),
                    modifier = Modifier.weight(1.5f)
                ) {
                    Text("Save Photo", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
