package com.omnitrack.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.cache.CoverCache
import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.VnRouteEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.model.VnDetailMeta
import com.omnitrack.app.data.preferences.DEFAULT_MEDIA_TYPE_ORDER
import com.omnitrack.app.ui.screens.detail.components.VN_ROUTE_TYPES
import com.omnitrack.app.ui.screens.detail.components.VnRoutePhotoChoice
import com.omnitrack.app.ui.screens.detail.components.VnRoutePhotoPickerSheet
import com.omnitrack.app.ui.screens.detail.components.VnRoutePhotoSelection
import com.omnitrack.app.ui.screens.detail.components.VnRoutePhotoThumb
import com.omnitrack.app.ui.screens.detail.components.formatRouteTypeLabel
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Immutable
data class CustomVnRouteDraft(
    val existingId: Long = 0L,
    val routeName: String,
    val routeType: String = "HEROINE",
    val imageUrl: String? = null,
    val imageFocalX: Float = 0f,
    val imageFocalY: Float = 0f,
    val imageZoom: Float = 1f,
    val playtimeHours: Double? = null,
    val isCompleted: Boolean = false,
    val isSpoiler: Boolean = false,
    val userRating: Float? = null,
    val userNote: String? = null,
    val isBookmarked: Boolean = false
)

/**
 * Option A Frameless AMOLED Manual Entry / Custom Editor Sheet:
 * - Wrapped FlowRow displaying visible media categories in user's preferred order
 * - Smart Dual Creator fields tailored per media vertical (Mangaka, Original Author, Director, Studio, Publisher, Fandom)
 * - Custom Library Categories multi-select chips with inline "+ New" quick creation
 * - Optional Visual Novel Routes & Endings builder (hours strictly optional, with route photo & crop)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomEntryEditorSheet(
    existingItem: MediaItemEntity? = null,
    initialTitle: String = "",
    initialMediaType: MediaType = MediaType.NOVEL,
    visibleMediaTypes: List<MediaType> = DEFAULT_MEDIA_TYPE_ORDER,
    availableCategories: List<CategoryEntity> = emptyList(),
    linkableSeries: List<MediaCardSummary> = emptyList(),
    initialCategoryIds: Set<Long> = emptySet(),
    initialVnRoutes: List<VnRouteEntity> = emptyList(),
    vnMeta: VnDetailMeta? = null,
    onCreateCategory: ((String, (Long) -> Unit) -> Unit)? = null,
    onSaveFull: ((MediaItemEntity, Set<Long>, List<CustomVnRouteDraft>) -> Unit)? = null,
    onSaveWithCategories: ((MediaItemEntity, Set<Long>) -> Unit)? = null,
    onSave: (MediaItemEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coverCache = remember {
        (context.applicationContext as? OmniTrackApplication)?.coverCache ?: CoverCache(context)
    }
    val artSaveKey = remember(existingItem?.id) {
        existingItem?.id?.takeIf { it > 0L } ?: System.currentTimeMillis()
    }

    val selectableTypes = remember(visibleMediaTypes, existingItem?.mediaType) {
        val base = visibleMediaTypes.filter { it != MediaType.ALL }.ifEmpty { DEFAULT_MEDIA_TYPE_ORDER }
        val existingType = existingItem?.mediaType?.takeIf { it != MediaType.ALL }
        if (existingType != null && existingType !in base) base + existingType else base
    }

    var selectedType by remember(existingItem?.id, initialMediaType, selectableTypes) {
        val preferred = existingItem?.mediaType?.takeIf { it != MediaType.ALL }
            ?: initialMediaType.takeIf { it != MediaType.ALL && it in selectableTypes }
            ?: selectableTypes.firstOrNull()
            ?: MediaType.NOVEL
        mutableStateOf(preferred)
    }
    var title by remember(existingItem?.id, initialTitle) {
        mutableStateOf(existingItem?.title ?: initialTitle)
    }
    var nativeTitle by remember(existingItem?.id) {
        mutableStateOf(existingItem?.nativeTitle.orEmpty())
    }
    var originCountryCode by remember(existingItem?.id) {
        mutableStateOf(existingItem?.originCountry?.trim()?.uppercase().orEmpty())
    }
    var primaryCreator by remember(existingItem?.id) {
        mutableStateOf(existingItem?.bookAuthor.orEmpty())
    }
    var secondaryCreator by remember(existingItem?.id) {
        mutableStateOf(
            existingItem?.comicPublisher?.takeIf { it.isNotBlank() }
                ?: existingItem?.fanficPrimaryFandom?.takeIf { it.isNotBlank() }
                ?: existingItem?.studioOrNetwork.orEmpty()
        )
    }
    var releaseYear by remember(existingItem?.id) {
        mutableStateOf(existingItem?.releaseYear.orEmpty())
    }
    var formatSubtype by remember(existingItem?.id) {
        mutableStateOf(
            existingItem?.bookFormat?.takeIf { it.isNotBlank() }
                ?: existingItem?.comicFormat?.takeIf { it.isNotBlank() }
                ?: existingItem?.platform.orEmpty()
        )
    }
    var selectedStatus by remember(existingItem?.id) {
        mutableStateOf(existingItem?.status ?: TrackingStatus.IN_PROGRESS)
    }
    var currentProgressStr by remember(existingItem?.id) {
        mutableStateOf(
            existingItem?.currentProgress?.let {
                if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
            } ?: "0"
        )
    }
    var totalProgressStr by remember(existingItem?.id) {
        mutableStateOf(
            existingItem?.totalProgress?.let {
                if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
            }.orEmpty()
        )
    }
    var userRatingStr by remember(existingItem?.id) {
        mutableStateOf(
            existingItem?.userRating?.let {
                if (it % 1.0f == 0f) it.toInt().toString() else it.toString()
            }.orEmpty()
        )
    }
    var categoryNames by remember(existingItem?.id) {
        mutableStateOf(existingItem?.categoryNames.orEmpty())
    }
    var selectedCustomCatIds by remember(existingItem?.id, initialCategoryIds) {
        mutableStateOf(initialCategoryIds)
    }
    var showNewCatInline by remember { mutableStateOf(false) }
    var newCatNameDraft by remember { mutableStateOf("") }

    var synopsis by remember(existingItem?.id) {
        mutableStateOf(existingItem?.synopsis.orEmpty())
    }
    var coverUrlInput by remember(existingItem?.id) {
        mutableStateOf(existingItem?.coverUrl.orEmpty())
    }
    var customCoverPath by remember(existingItem?.id) {
        mutableStateOf(existingItem?.customCoverPath)
    }
    var bannerUrlInput by remember(existingItem?.id) {
        mutableStateOf(existingItem?.bannerUrl.orEmpty())
    }
    var customBannerPath by remember(existingItem?.id) {
        mutableStateOf(existingItem?.customBannerPath?.takeIf { it != "NONE" })
    }
    var manualProtected by remember(existingItem?.id) {
        mutableStateOf(existingItem?.isCustomEntry ?: true)
    }
    var linkedParentMediaId by remember(existingItem?.id) {
        mutableStateOf(existingItem?.linkedParentMediaId)
    }
    var linkedSeasonNumberStr by remember(existingItem?.id) {
        mutableStateOf(existingItem?.linkedSeasonNumber?.toString().orEmpty())
    }
    var showParentPickerDialog by remember { mutableStateOf(false) }
    var parentPickerQuery by remember { mutableStateOf("") }

    var vnRouteDrafts by remember(existingItem?.id, initialVnRoutes) {
        mutableStateOf(
            initialVnRoutes.map { r ->
                CustomVnRouteDraft(
                    existingId = r.id,
                    routeName = r.routeName,
                    routeType = r.routeType,
                    imageUrl = r.characterImageUrl,
                    imageFocalX = r.imageFocalX,
                    imageFocalY = r.imageFocalY,
                    imageZoom = r.imageZoom,
                    playtimeHours = r.playtimeHours,
                    isCompleted = r.isCompleted,
                    isSpoiler = r.isSpoiler,
                    userRating = r.userRating,
                    userNote = r.userNote,
                    isBookmarked = r.isBookmarked
                )
            }
        )
    }
    var showAddRouteComposer by remember { mutableStateOf(false) }
    var draftRouteName by remember { mutableStateOf("") }
    var draftRouteType by remember { mutableStateOf("HEROINE") }
    var draftRouteHours by remember { mutableStateOf("") }
    var draftRouteCompleted by remember { mutableStateOf(false) }
    var draftRouteSpoiler by remember { mutableStateOf(false) }
    var draftRoutePhoto by remember { mutableStateOf(VnRoutePhotoSelection(null)) }
    var showDraftRoutePhotoPicker by remember { mutableStateOf(false) }
    var editingDraftPhotoIndex by remember { mutableStateOf<Int?>(null) }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val saved = coverCache.saveCustomCoverUri(artSaveKey, uri)
                customCoverPath = saved ?: uri.toString()
            }
        }
    }

    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val saved = coverCache.saveCustomBannerUri(artSaveKey, uri)
                customBannerPath = saved ?: uri.toString()
            }
        }
    }

    val (primaryCreatorLabel, secondaryCreatorLabel) = when (selectedType) {
        MediaType.MANGA -> "Mangaka / Story Author" to "Artist / Publisher"
        MediaType.ANIME -> "Original Author / Mangaka" to "Animation Studio"
        MediaType.SHOW -> "Creator / Showrunner" to "Network / Studio"
        MediaType.MOVIE -> "Director / Writer" to "Studio / Distributor"
        MediaType.BOOK -> "Author" to "Publisher / Narrator"
        MediaType.NOVEL -> "Author / Web Novel Writer" to "Publisher / Platform"
        MediaType.FANFICTION -> "Author" to "Primary Fandom"
        MediaType.COMIC -> "Writer / Artist" to "Publisher"
        MediaType.GAME -> "Developer / Director" to "Publisher"
        MediaType.VISUAL_NOVEL -> "Scenario Writer / Dev" to "Publisher"
        else -> "Creator / Author" to "Studio / Publisher"
    }
    val formatPlaceholder = when (selectedType) {
        MediaType.NOVEL -> "e.g. WEB_NOVEL, LIGHT_NOVEL"
        MediaType.BOOK -> "e.g. HARDCOVER, EBOOK, AUDIOBOOK"
        MediaType.COMIC -> "e.g. Issue, TPB, Graphic Novel"
        MediaType.GAME -> "e.g. PC, PS5, Switch"
        MediaType.MANGA -> "e.g. Manga, Manhwa, Manhua"
        MediaType.FANFICTION -> "e.g. AO3, FFN, SpaceBattles"
        else -> "e.g. TV, OVA, Film"
    }

    val previewCoverModel: Any? = remember(customCoverPath, coverUrlInput) {
        customCoverPath?.let { if (it.startsWith("http") || it.startsWith("content:")) it else File(it) }
            ?: coverUrlInput.trim().takeIf { it.isNotBlank() }
    }
    val previewBannerModel: Any? = remember(customBannerPath, bannerUrlInput) {
        customBannerPath?.let { if (it.startsWith("http") || it.startsWith("content:")) it else File(it) }
            ?: bannerUrlInput.trim().takeIf { it.isNotBlank() }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AccentGold,
        unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedLabelColor = AccentGold,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentGold
    )

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
                .fillMaxHeight(0.92f)
                .background(AmoledBlack)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 14.dp)
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
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (existingItem == null) "Create Manual Entry" else "Edit Entry Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (existingItem == null) "All categories supported \u00B7 custom creators & metadata"
                        else "Customize metadata, covers, categories & manual lock",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Wrapped Category FlowRow (All 10 verticals visible at once!)
                Text(
                    text = "MEDIA CATEGORY",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.9.sp
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    selectableTypes.forEach { type ->
                        val selected = selectedType == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                                .clickable { selectedType = type }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = type.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) AccentGold else TextSecondary
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                // Cover + Banner Live Artwork Strip
                Text(
                    text = "ARTWORK",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.9.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .clickable {
                                coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewCoverModel != null) {
                            AsyncImage(
                                model = previewCoverModel,
                                contentDescription = "Cover Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null, tint = TextSecondary)
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable {
                                        coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = AccentGold, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Cover File", fontSize = 11.5.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable {
                                        bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = AccentGold, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = if (previewBannerModel != null) "Change Banner" else "Banner File",
                                    fontSize = 11.5.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        OutlinedTextField(
                            value = coverUrlInput,
                            onValueChange = { coverUrlInput = it },
                            label = { Text("Cover URL (optional)") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = bannerUrlInput,
                    onValueChange = { bannerUrlInput = it },
                    label = { Text("Wide Banner URL (optional)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = nativeTitle,
                    onValueChange = { nativeTitle = it },
                    label = { Text("Alternative / Native Title") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "ORIGIN / LANGUAGE (OPTIONAL)",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.9.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf(
                        "" to "Auto",
                        "CN" to "Chinese",
                        "JP" to "Japanese",
                        "KR" to "Korean",
                        "US" to "English",
                        "FR" to "French",
                        "ES" to "Spanish",
                        "DE" to "German",
                        "RU" to "Russian"
                    ).forEach { (code, label) ->
                        val selected = originCountryCode == code
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                                .clickable { originCountryCode = code }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) AccentGold else TextSecondary
                            )
                        }
                    }
                }

                // Smart Dual Creator Fields (e.g. Mangaka + Artist/Publisher, Original Author + Studio, Director + Studio)
                Text(
                    text = "CREATORS & STUDIO",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.9.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = primaryCreator,
                        onValueChange = { primaryCreator = it },
                        label = { Text(primaryCreatorLabel) },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = secondaryCreator,
                        onValueChange = { secondaryCreator = it },
                        label = { Text(secondaryCreatorLabel) },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = formatSubtype,
                        onValueChange = { formatSubtype = it },
                        label = { Text("Format ($formatPlaceholder)") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = releaseYear,
                        onValueChange = { releaseYear = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                        label = { Text("Year") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors,
                        modifier = Modifier.weight(0.75f)
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                // Tracking Status
                Text(
                    text = "TRACKING STATUS",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.9.sp
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf(
                        TrackingStatus.IN_PROGRESS,
                        TrackingStatus.PLAN_TO_TRACK,
                        TrackingStatus.COMPLETED,
                        TrackingStatus.ON_HOLD,
                        TrackingStatus.DROPPED
                    ).forEach { st ->
                        val selected = selectedStatus == st
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                                .clickable { selectedStatus = st }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = st.labelFor(selectedType),
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) AccentGold else TextSecondary
                            )
                        }
                    }
                }

                // Progress / Total / Rating
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = currentProgressStr,
                        onValueChange = { currentProgressStr = it },
                        label = { Text("Progress (${selectedType.unitLabel})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = totalProgressStr,
                        onValueChange = { totalProgressStr = it },
                        label = { Text("Total") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = userRatingStr,
                        onValueChange = { userRatingStr = it },
                        label = { Text("Score /10") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = fieldColors,
                        modifier = Modifier.weight(0.9f)
                    )
                }

                // Visual Novel Routes & Endings (hours strictly optional)
                if (selectedType == MediaType.VISUAL_NOVEL) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "ROUTES & ENDINGS",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.9.sp
                            )
                            if (vnRouteDrafts.isNotEmpty()) {
                                Text(
                                    text = "(${vnRouteDrafts.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentGold
                                )
                            }
                        }
                        Text(
                            text = if (showAddRouteComposer) "Close" else "+ Add Route",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold,
                            modifier = Modifier
                                .clickable { showAddRouteComposer = !showAddRouteComposer }
                                .padding(vertical = 2.dp)
                        )
                    }

                    AnimatedVisibility(visible = showAddRouteComposer) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier.clickable { showDraftRoutePhotoPicker = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!draftRoutePhoto.imageUrl.isNullOrBlank()) {
                                        VnRoutePhotoThumb(
                                            imageUrl = draftRoutePhoto.imageUrl,
                                            focalX = draftRoutePhoto.focalX,
                                            focalY = draftRoutePhoto.focalY,
                                            zoom = draftRoutePhoto.zoom,
                                            contentDescription = "Route Photo",
                                            size = 48.dp,
                                            cornerRadius = 10.dp
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White.copy(alpha = 0.06f))
                                                .border(0.8.dp, AccentGold.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AddPhotoAlternate,
                                                contentDescription = "Add route photo",
                                                tint = AccentGold,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = draftRouteName,
                                    onValueChange = { draftRouteName = it },
                                    label = { Text("Route / Ending Name") },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                VN_ROUTE_TYPES.forEach { (key, label) ->
                                    val selected = draftRouteType == key
                                    Column(
                                        modifier = Modifier
                                            .width(IntrinsicSize.Max)
                                            .clickable { draftRouteType = key },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) AccentGold else TextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(2.dp)
                                                .background(if (selected) AccentGold else Color.Transparent)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = draftRouteHours,
                                    onValueChange = { next ->
                                        if (next.length <= 6 && next.all { it.isDigit() || it == '.' }) {
                                            draftRouteHours = next
                                        }
                                    },
                                    label = { Text("Hours (optional)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = fieldColors,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier
                                        .clickable { draftRouteCompleted = !draftRouteCompleted }
                                        .padding(vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(if (draftRouteCompleted) AccentGold else Color.Transparent)
                                            .border(
                                                1.2.dp,
                                                if (draftRouteCompleted) AccentGold else TextSecondary,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (draftRouteCompleted) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = OnAccentColor, modifier = Modifier.size(11.dp))
                                        }
                                    }
                                    Text("Done", fontSize = 12.sp, color = if (draftRouteCompleted) AccentGold else TextSecondary, fontWeight = FontWeight.SemiBold)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier
                                        .clickable { draftRouteSpoiler = !draftRouteSpoiler }
                                        .padding(vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(if (draftRouteSpoiler) AccentGold else Color.Transparent)
                                            .border(
                                                1.2.dp,
                                                if (draftRouteSpoiler) AccentGold else TextSecondary,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (draftRouteSpoiler) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = OnAccentColor, modifier = Modifier.size(11.dp))
                                        }
                                    }
                                    Text("Spoiler", fontSize = 12.sp, color = if (draftRouteSpoiler) AccentGold else TextSecondary, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            val canAddRoute = draftRouteName.trim().isNotBlank()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        val cleanName = draftRouteName.trim()
                                        if (cleanName.isNotEmpty()) {
                                            vnRouteDrafts = vnRouteDrafts + CustomVnRouteDraft(
                                                routeName = cleanName,
                                                routeType = draftRouteType,
                                                imageUrl = draftRoutePhoto.imageUrl,
                                                imageFocalX = draftRoutePhoto.focalX,
                                                imageFocalY = draftRoutePhoto.focalY,
                                                imageZoom = draftRoutePhoto.zoom,
                                                playtimeHours = draftRouteHours.trim().toDoubleOrNull()?.takeIf { it > 0.0 },
                                                isCompleted = draftRouteCompleted,
                                                isSpoiler = draftRouteSpoiler
                                            )
                                            draftRouteName = ""
                                            draftRouteHours = ""
                                            draftRouteCompleted = false
                                            draftRouteSpoiler = false
                                            draftRoutePhoto = VnRoutePhotoSelection(null)
                                        }
                                    },
                                    enabled = canAddRoute,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentGold,
                                        contentColor = OnAccentColor
                                    ),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Route", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (vnRouteDrafts.isEmpty() && !showAddRouteComposer) {
                        Text(
                            text = "No routes added \u00B7 Tap + Add Route (hours optional)",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAddRouteComposer = true }
                                .padding(vertical = 4.dp)
                        )
                    } else if (vnRouteDrafts.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            vnRouteDrafts.forEachIndexed { idx, draft ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(if (draft.isCompleted) AccentGold else Color.Transparent)
                                            .border(
                                                1.3.dp,
                                                if (draft.isCompleted) AccentGold else Color.White.copy(alpha = 0.5f),
                                                CircleShape
                                            )
                                            .clickable {
                                                vnRouteDrafts = vnRouteDrafts.mapIndexed { i, itemDraft ->
                                                    if (i == idx) itemDraft.copy(isCompleted = !itemDraft.isCompleted) else itemDraft
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (draft.isCompleted) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = OnAccentColor, modifier = Modifier.size(12.dp))
                                        }
                                    }

                                    Box(
                                        modifier = Modifier.clickable { editingDraftPhotoIndex = idx },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        VnRoutePhotoThumb(
                                            imageUrl = draft.imageUrl,
                                            focalX = draft.imageFocalX,
                                            focalY = draft.imageFocalY,
                                            zoom = draft.imageZoom,
                                            contentDescription = draft.routeName,
                                            size = 42.dp,
                                            cornerRadius = 8.dp,
                                            fallbackText = draft.routeName.take(2).uppercase()
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = draft.routeName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val metaParts = buildList {
                                            add(formatRouteTypeLabel(draft.routeType))
                                            draft.playtimeHours?.takeIf { it > 0.0 }?.let { h ->
                                                add(if (h % 1.0 == 0.0) "${h.toInt()}h" else String.format(Locale.US, "%.1fh", h))
                                            }
                                            if (draft.isSpoiler) add("Spoiler")
                                        }
                                        Text(
                                            text = metaParts.joinToString(" \u00B7 "),
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove route",
                                        tint = TextMuted,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                vnRouteDrafts = vnRouteDrafts.filterIndexed { i, _ -> i != idx }
                                            }
                                    )
                                }
                                if (idx < vnRouteDrafts.lastIndex) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.6.dp)
                                }
                            }
                        }
                    }
                }

                // Custom Library Categories Multi-Select Section
                if (availableCategories.isNotEmpty() || onCreateCategory != null) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                    Text(
                        text = "LIBRARY CATEGORIES",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.9.sp
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        availableCategories.forEach { cat ->
                            val picked = cat.id in selectedCustomCatIds
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (picked) AccentGold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                                    .clickable {
                                        selectedCustomCatIds = if (picked) selectedCustomCatIds - cat.id else selectedCustomCatIds + cat.id
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (picked) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = AccentGold, modifier = Modifier.size(13.dp))
                                }
                                Text(
                                    text = cat.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium,
                                    color = if (picked) AccentGold else TextSecondary
                                )
                            }
                        }
                        if (onCreateCategory != null) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable { showNewCatInline = !showNewCatInline }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = AccentGold, modifier = Modifier.size(13.dp))
                                Text("New", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGold)
                            }
                        }
                    }

                    AnimatedVisibility(visible = showNewCatInline && onCreateCategory != null) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BasicTextField(
                                    value = newCatNameDraft,
                                    onValueChange = { newCatNameDraft = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                                    cursorBrush = SolidColor(AccentGold),
                                    decorationBox = { inner ->
                                        if (newCatNameDraft.isEmpty()) Text("New category name...", color = TextMuted, fontSize = 13.5.sp)
                                        inner()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 6.dp)
                                )
                                Text(
                                    text = "Add",
                                    color = if (newCatNameDraft.trim().isNotEmpty()) AccentGold else TextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable(enabled = newCatNameDraft.trim().isNotEmpty()) {
                                            val name = newCatNameDraft.trim()
                                            onCreateCategory?.invoke(name) { createdId ->
                                                if (createdId > 0L) {
                                                    selectedCustomCatIds = selectedCustomCatIds + createdId
                                                }
                                            }
                                            newCatNameDraft = ""
                                            showNewCatInline = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            HorizontalDivider(color = AccentGold.copy(alpha = 0.45f), thickness = 0.7.dp)
                        }
                    }
                }

                OutlinedTextField(
                    value = categoryNames,
                    onValueChange = { categoryNames = it },
                    label = { Text("Genres / Tags (comma-separated)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = synopsis,
                    onValueChange = { synopsis = it },
                    label = { Text("Synopsis / Notes") },
                    minLines = 3,
                    maxLines = 6,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                val candidateParents = remember(linkableSeries, existingItem?.id) {
                    linkableSeries.filter { it.id > 0L && it.id != existingItem?.id }
                }
                if (candidateParents.isNotEmpty() || linkedParentMediaId != null) {
                    val linkedParentSummary = remember(candidateParents, linkedParentMediaId) {
                        candidateParents.firstOrNull { it.id == linkedParentMediaId }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Link to Existing Series (Optional)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .clickable { showParentPickerDialog = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = linkedParentSummary?.title
                                        ?: linkedParentMediaId?.let { "Linked Series #$it" }
                                        ?: "Tap to pick a parent series in your library",
                                    color = if (linkedParentMediaId != null) TextPrimary else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (linkedParentMediaId != null) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (linkedParentSummary != null) {
                                    Text(
                                        text = "${linkedParentSummary.mediaType.displayName} • Appears in parent's seasons",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if (linkedParentMediaId != null) {
                                TextButton(onClick = {
                                    linkedParentMediaId = null
                                    linkedSeasonNumberStr = ""
                                }) {
                                    Text("Unlink", color = Color(0xFFEF4444), fontSize = 12.sp)
                                }
                            }
                        }
                        if (linkedParentMediaId != null) {
                            OutlinedTextField(
                                value = linkedSeasonNumberStr,
                                onValueChange = { raw -> linkedSeasonNumberStr = raw.filter { it.isDigit() } },
                                label = { Text("Season / Part # (e.g. 2)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = fieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (existingItem != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { manualProtected = !manualProtected }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Manual Protection (Lock Details)",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Prevents background enrichment from overwriting custom fields",
                                fontSize = 11.5.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = manualProtected,
                            onCheckedChange = { manualProtected = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnAccentColor,
                                checkedTrackColor = AccentGold,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val canSave = title.trim().isNotBlank()
            Button(
                onClick = {
                    val curProg = currentProgressStr.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val totProg = totalProgressStr.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
                    val score = userRatingStr.trim().toFloatOrNull()?.coerceIn(0f, 10f)?.takeIf { it > 0f }
                    val primaryTrim = primaryCreator.trim().takeIf { it.isNotBlank() }
                    val secondaryTrim = secondaryCreator.trim().takeIf { it.isNotBlank() }
                    val fmtTrim = formatSubtype.trim().takeIf { it.isNotBlank() }
                    val base = existingItem ?: MediaItemEntity(
                        title = title.trim(),
                        coverUrl = "",
                        mediaType = selectedType
                    )
                    val resolvedCoverUrl = coverUrlInput.trim().ifBlank {
                        customCoverPath ?: base.coverUrl
                    }
                    val resolvedBannerUrl = bannerUrlInput.trim().takeIf { it.isNotBlank() }
                    val updated = base.copy(
                        title = title.trim(),
                        nativeTitle = nativeTitle.trim().takeIf { it.isNotBlank() },
                        originCountry = originCountryCode.takeIf { it.isNotBlank() },
                        synopsis = synopsis.trim().takeIf { it.isNotBlank() },
                        coverUrl = resolvedCoverUrl,
                        bannerUrl = resolvedBannerUrl,
                        customCoverPath = customCoverPath ?: base.customCoverPath,
                        customBannerPath = customBannerPath ?: base.customBannerPath,
                        mediaType = selectedType,
                        status = selectedStatus,
                        currentProgress = curProg,
                        playtimeHours = if (selectedType == MediaType.VISUAL_NOVEL || selectedType == MediaType.GAME) curProg else base.playtimeHours,
                        totalProgress = totProg,
                        userRating = score,
                        releaseYear = releaseYear.trim().takeIf { it.isNotBlank() },
                        categoryNames = categoryNames.trim(),
                        bookAuthor = primaryTrim,
                        comicPublisher = if (selectedType == MediaType.COMIC) secondaryTrim else base.comicPublisher,
                        fanficPrimaryFandom = if (selectedType == MediaType.FANFICTION) secondaryTrim else base.fanficPrimaryFandom,
                        studioOrNetwork = if (selectedType != MediaType.COMIC && selectedType != MediaType.FANFICTION) secondaryTrim else base.studioOrNetwork,
                        bookFormat = if (selectedType == MediaType.BOOK) fmtTrim else base.bookFormat,
                        comicFormat = if (selectedType == MediaType.COMIC) fmtTrim else base.comicFormat,
                        platform = if (selectedType != MediaType.BOOK && selectedType != MediaType.COMIC) fmtTrim else base.platform,
                        isCustomEntry = manualProtected,
                        linkedParentMediaId = linkedParentMediaId,
                        linkedSeasonNumber = if (linkedParentMediaId != null) {
                            linkedSeasonNumberStr.trim().toIntOrNull()?.coerceAtLeast(1) ?: 2
                        } else null,
                        lastUpdated = System.currentTimeMillis()
                    )
                    val finalRoutes = if (selectedType == MediaType.VISUAL_NOVEL) {
                        val pendingName = draftRouteName.trim()
                        if (showAddRouteComposer && pendingName.isNotEmpty()) {
                            vnRouteDrafts + CustomVnRouteDraft(
                                routeName = pendingName,
                                routeType = draftRouteType,
                                imageUrl = draftRoutePhoto.imageUrl,
                                imageFocalX = draftRoutePhoto.focalX,
                                imageFocalY = draftRoutePhoto.focalY,
                                imageZoom = draftRoutePhoto.zoom,
                                playtimeHours = draftRouteHours.trim().toDoubleOrNull()?.takeIf { it > 0.0 },
                                isCompleted = draftRouteCompleted,
                                isSpoiler = draftRouteSpoiler
                            )
                        } else {
                            vnRouteDrafts
                        }
                    } else {
                        emptyList()
                    }
                    when {
                        onSaveFull != null -> onSaveFull(updated, selectedCustomCatIds, finalRoutes)
                        onSaveWithCategories != null -> onSaveWithCategories(updated, selectedCustomCatIds)
                        else -> onSave(updated)
                    }
                },
                enabled = canSave,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGold,
                    contentColor = OnAccentColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = if (existingItem == null) "Save Manual Entry" else "Save Changes",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    val draftPhotoExtraChoices = remember(customCoverPath, coverUrlInput, customBannerPath, bannerUrlInput) {
        buildList {
            customBannerPath?.takeIf { it.isNotBlank() && it != "NONE" }?.let {
                add(VnRoutePhotoChoice(it, "Entry Banner", "Current"))
            }
            bannerUrlInput.trim().takeIf { it.isNotBlank() }?.let {
                add(VnRoutePhotoChoice(it, "Banner URL", "Current"))
            }
            customCoverPath?.takeIf { it.isNotBlank() }?.let {
                add(VnRoutePhotoChoice(it, "Entry Cover", "Current"))
            }
            coverUrlInput.trim().takeIf { it.isNotBlank() }?.let {
                add(VnRoutePhotoChoice(it, "Cover URL", "Current"))
            }
        }
    }

    val activeDraftEditIdx = editingDraftPhotoIndex
    if (showDraftRoutePhotoPicker || activeDraftEditIdx != null) {
        val editingDraft = activeDraftEditIdx?.let { vnRouteDrafts.getOrNull(it) }
        val initialSel = if (editingDraft != null) {
            VnRoutePhotoSelection(
                imageUrl = editingDraft.imageUrl,
                focalX = editingDraft.imageFocalX,
                focalY = editingDraft.imageFocalY,
                zoom = editingDraft.imageZoom
            )
        } else {
            draftRoutePhoto
        }
        val sheetTitle = editingDraft?.routeName ?: draftRouteName.ifBlank { "New Route" }
        val previewEntity = remember(existingItem, title, selectedType, coverUrlInput, bannerUrlInput, customCoverPath, customBannerPath) {
            existingItem ?: title.trim().takeIf { it.isNotBlank() }?.let { t ->
                MediaItemEntity(
                    title = t,
                    coverUrl = coverUrlInput.trim(),
                    bannerUrl = bannerUrlInput.trim().takeIf { it.isNotBlank() },
                    customCoverPath = customCoverPath,
                    customBannerPath = customBannerPath,
                    mediaType = selectedType
                )
            }
        }
        VnRoutePhotoPickerSheet(
            routeTitle = sheetTitle,
            initialSelection = initialSel,
            mediaItem = previewEntity,
            vnMeta = vnMeta,
            extraImageCandidates = draftPhotoExtraChoices,
            onApply = { sel ->
                if (activeDraftEditIdx != null && activeDraftEditIdx in vnRouteDrafts.indices) {
                    vnRouteDrafts = vnRouteDrafts.mapIndexed { i, d ->
                        if (i == activeDraftEditIdx) {
                            d.copy(
                                imageUrl = sel.imageUrl,
                                imageFocalX = sel.focalX,
                                imageFocalY = sel.focalY,
                                imageZoom = sel.zoom
                            )
                        } else d
                    }
                } else {
                    draftRoutePhoto = sel
                }
            },
            onDismiss = {
                showDraftRoutePhotoPicker = false
                editingDraftPhotoIndex = null
            }
        )
    }

    if (showParentPickerDialog) {
        val dialogParents = remember(linkableSeries, existingItem?.id, parentPickerQuery) {
            val q = parentPickerQuery.trim()
            linkableSeries.filter { s ->
                s.id > 0L &&
                    s.id != existingItem?.id &&
                    s.linkedParentMediaId == null &&
                    (q.isEmpty() || s.title.contains(q, ignoreCase = true) || s.mediaType.displayName.contains(q, ignoreCase = true))
            }.take(80)
        }
        AlertDialog(
            onDismissRequest = { showParentPickerDialog = false },
            containerColor = Color(0xFF141418),
            title = {
                Text(
                    text = "Select Parent Series",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = parentPickerQuery,
                        onValueChange = { parentPickerQuery = it },
                        placeholder = { Text("Search your library...", color = TextMuted) },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (dialogParents.isEmpty()) {
                            Text(
                                text = "No matching library titles found",
                                color = TextMuted,
                                fontSize = 12.5.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            dialogParents.forEach { candidate ->
                                val isChosen = candidate.id == linkedParentMediaId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isChosen) AccentGold.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
                                        .clickable {
                                            linkedParentMediaId = candidate.id
                                            if (linkedSeasonNumberStr.isBlank()) {
                                                linkedSeasonNumberStr = "2"
                                            }
                                            showParentPickerDialog = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = candidate.title,
                                            color = if (isChosen) AccentGold else TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = candidate.mediaType.displayName,
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (isChosen) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = AccentGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showParentPickerDialog = false }) {
                    Text("Close", color = AccentGold)
                }
            }
        )
    }
}
