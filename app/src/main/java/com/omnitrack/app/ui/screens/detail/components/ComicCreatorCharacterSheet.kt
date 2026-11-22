package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.enrichment.ComicEnrichmentSource
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * Interactive profile popup for Comic Creators & Characters.
 * Inherits Settings > Visual Presentation backdrop style and queries live comic appearances.
 */
@Composable
fun ComicCreatorCharacterSheet(
    name: String,
    role: String,
    avatarUrl: String?,
    description: String?,
    presentationSettings: PresentationSettings? = null,
    backdropBitmap: ImageBitmap? = null,
    backdropTargetUrl: String? = null,
    onSelectComic: (RecommendationItem) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    var relatedComics by remember(name) { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var isLoading by remember(name) { mutableStateOf(true) }

    LaunchedEffect(name) {
        isLoading = true
        relatedComics = runCatching {
            ComicEnrichmentSource().searchComics(name)
                .distinctBy { it.title.lowercase() }
                .take(16)
        }.getOrDefault(emptyList())
        isLoading = false
    }

    InWindowBottomSheet(
        onDismiss = onDismiss,
        isAtTop = { scrollState.value == 0 },
        wrapHeightMax = 720.dp,
        showDragHandle = false
    ) {
        DetailSheetBackdrop(
            presentationSettings = presentationSettings,
            backdropBitmap = backdropBitmap,
            backdropTargetUrl = backdropTargetUrl,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(max = 720.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 22.dp, vertical = 18.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.24f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(82.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = name.trim().take(1).uppercase().ifEmpty { "?" },
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGold
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = role.uppercase(),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = name,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!description.isNullOrBlank()) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                    Text(
                        text = description,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = TextSecondary
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

                Text(
                    text = "Notable Comic Runs & Appearances",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = AccentGold,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Loading comic bibliography...", fontSize = 12.5.sp, color = TextSecondary)
                        }
                    }
                    relatedComics.isEmpty() -> {
                        Text(
                            text = "No additional indexed runs returned for \"$name\".",
                            fontSize = 12.5.sp,
                            color = TextSecondary
                        )
                    }
                    else -> {
                        LazyRow(
                            contentPadding = PaddingValues(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = relatedComics,
                                key = { idx, c -> "${c.title}_${c.comicVineId ?: c.metronId ?: idx}" }
                            ) { _, comic ->
                                Column(
                                    modifier = Modifier
                                        .width(108.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onSelectComic(
                                                RecommendationItem(
                                                    title = comic.title,
                                                    coverUrl = comic.coverUrl.orEmpty(),
                                                    releaseYear = comic.releaseYear,
                                                    rating = comic.communityScore,
                                                    mediaType = MediaType.COMIC,
                                                    synopsis = comic.synopsis,
                                                    bannerUrl = comic.bannerUrl
                                                )
                                            )
                                        },
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.67f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                    ) {
                                        if (!comic.coverUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = comic.coverUrl,
                                                contentDescription = comic.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    Text(
                                        text = comic.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary,
                                        maxLines = 2,
                                        lineHeight = 15.sp,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = comic.comicPublisher ?: comic.releaseYear
                                    if (!sub.isNullOrBlank()) {
                                        Text(
                                            text = sub,
                                            fontSize = 10.5.sp,
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

                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
