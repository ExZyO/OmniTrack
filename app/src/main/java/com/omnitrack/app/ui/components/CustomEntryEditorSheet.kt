package com.omnitrack.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.cache.CoverCache
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurface
import com.omnitrack.app.ui.theme.DarkSurfaceCard
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomEntryEditorSheet(
    existingItem: MediaItemEntity? = null,
    initialTitle: String = "",
    initialMediaType: MediaType = MediaType.NOVEL,
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
    var creator by remember(existingItem?.id) {
        mutableStateOf(
            existingItem?.bookAuthor?.takeIf { it.isNotBlank() }
                ?: existingItem?.comicPublisher?.takeIf { it.isNotBlank() }
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

    val creatorFieldLabel = when (selectedType) {
        MediaType.BOOK, MediaType.NOVEL -> "Author"
        MediaType.COMIC -> "Publisher / Writer"
        MediaType.GAME, MediaType.VISUAL_NOVEL -> "Developer / Studio"
        else -> "Studio / Network / Director"
    }
    val formatPlaceholder = when (selectedType) {
        MediaType.NOVEL -> "e.g. WEB_NOVEL, LIGHT_NOVEL"
        MediaType.BOOK -> "e.g. HARDCOVER, EBOOK, AUDIOBOOK"
        MediaType.COMIC -> "e.g. Issue, TPB, Graphic Novel"
        MediaType.GAME -> "e.g. PC, PS5, Switch"
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
        unfocusedBorderColor = GlassBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedLabelColor = AccentGold,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentGold
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DarkSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (existingItem == null) "Create Custom Entry" else "Edit Entry Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (existingItem == null) "Add anything manually — never overwritten by scrapers"
                        else "Customize metadata, covers, progress & manual protection",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category Selection
                Text(
                    text = "CATEGORY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.9.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectableTypes.forEach { type ->
                        val selected = selectedType == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) AccentGold else DarkSurfaceElevated)
                                .border(1.dp, if (selected) AccentGold else GlassBorder, RoundedCornerShape(10.dp))
                                .clickable { selectedType = type }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = type.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) AmoledBlack else TextPrimary
                            )
                        }
                    }
                }

                // Cover + Banner Live Artwork Strip
                Text(
                    text = "ARTWORK",
                    fontSize = 11.sp,
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
                            .width(72.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceCard)
                            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
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
                            FilledTonalButton(
                                onClick = {
                                    coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = DarkSurfaceElevated,
                                    contentColor = AccentGold
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cover File", fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            FilledTonalButton(
                                onClick = {
                                    bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = DarkSurfaceElevated,
                                    contentColor = AccentGold
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (previewBannerModel != null) "Change Banner" else "Banner File",
                                    fontSize = 11.5.sp,
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = creator,
                        onValueChange = { creator = it },
                        label = { Text(creatorFieldLabel) },
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
                        modifier = Modifier.weight(0.8f)
                    )
                }

                OutlinedTextField(
                    value = formatSubtype,
                    onValueChange = { formatSubtype = it },
                    label = { Text("Format / Platform ($formatPlaceholder)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                // Tracking Status
                Text(
                    text = "TRACKING STATUS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.9.sp
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) AccentGold else DarkSurfaceElevated)
                                .border(1.dp, if (selected) AccentGold else GlassBorder, RoundedCornerShape(10.dp))
                                .clickable { selectedStatus = st }
                                .padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = st.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) AmoledBlack else TextPrimary
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                            .clickable { manualProtected = !manualProtected }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                checkedThumbColor = AmoledBlack,
                                checkedTrackColor = AccentGold,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = DarkSurface
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
                    val creatorTrim = creator.trim().takeIf { it.isNotBlank() }
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
                        bookAuthor = if (selectedType == MediaType.BOOK || selectedType == MediaType.NOVEL) creatorTrim else base.bookAuthor,
                        comicPublisher = if (selectedType == MediaType.COMIC) creatorTrim else base.comicPublisher,
                        studioOrNetwork = if (selectedType !in setOf(MediaType.BOOK, MediaType.NOVEL, MediaType.COMIC)) creatorTrim else base.studioOrNetwork,
                        bookFormat = if (selectedType == MediaType.BOOK) fmtTrim else base.bookFormat,
                        comicFormat = if (selectedType == MediaType.COMIC) fmtTrim else base.comicFormat,
                        platform = if (selectedType != MediaType.BOOK && selectedType != MediaType.COMIC) fmtTrim else base.platform,
                        isCustomEntry = manualProtected,
                        lastUpdated = System.currentTimeMillis()
                    )
                    onSave(updated)
                },
                enabled = canSave,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGold,
                    contentColor = AmoledBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = if (existingItem == null) "Save Custom Entry" else "Save Changes",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
