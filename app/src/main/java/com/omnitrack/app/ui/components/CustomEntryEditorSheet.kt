package com.omnitrack.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

/**
 * Option A Frameless AMOLED Manual Entry / Custom Editor Sheet:
 * - Wrapped FlowRow displaying all 10 media categories simultaneously
 * - Smart Dual Creator fields tailored per media vertical (Mangaka, Original Author, Director, Studio, Publisher, Fandom)
 * - Custom Library Categories multi-select chips with inline "+ New" quick creation
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomEntryEditorSheet(
    existingItem: MediaItemEntity? = null,
    initialTitle: String = "",
    initialMediaType: MediaType = MediaType.NOVEL,
    availableCategories: List<CategoryEntity> = emptyList(),
    initialCategoryIds: Set<Long> = emptySet(),
    onCreateCategory: ((String, (Long) -> Unit) -> Unit)? = null,
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

    val selectableTypes = remember {
        listOf(
            MediaType.ANIME,
            MediaType.MANGA,
            MediaType.COMIC,
            MediaType.NOVEL,
            MediaType.FANFICTION,
            MediaType.BOOK,
            MediaType.SHOW,
            MediaType.MOVIE,
            MediaType.GAME,
            MediaType.VISUAL_NOVEL
        )
    }

    var selectedType by remember(existingItem?.id, initialMediaType) {
        mutableStateOf(
            existingItem?.mediaType?.takeIf { it != MediaType.ALL }
                ?: initialMediaType.takeIf { it != MediaType.ALL }
                ?: MediaType.NOVEL
        )
    }
    var title by remember(existingItem?.id, initialTitle) {
        mutableStateOf(existingItem?.title ?: initialTitle)
    }
    var nativeTitle by remember(existingItem?.id) {
        mutableStateOf(existingItem?.nativeTitle.orEmpty())
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
                                text = st.displayName,
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
                        synopsis = synopsis.trim().takeIf { it.isNotBlank() },
                        coverUrl = resolvedCoverUrl,
                        bannerUrl = resolvedBannerUrl,
                        customCoverPath = customCoverPath ?: base.customCoverPath,
                        customBannerPath = customBannerPath ?: base.customBannerPath,
                        mediaType = selectedType,
                        status = selectedStatus,
                        currentProgress = curProg,
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
                        lastUpdated = System.currentTimeMillis()
                    )
                    if (onSaveWithCategories != null) {
                        onSaveWithCategories(updated, selectedCustomCatIds)
                    } else {
                        onSave(updated)
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
}
