package com.omnitrack.app.ui.screens.detail.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.AnimeTagItem
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextSecondary

private fun formatCompactNumber(n: Int): String {
    return when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000f)
        n >= 1_000 -> String.format("%.1fK", n / 1_000f)
        else -> "$n"
    }
}

/**
 * General Overview & Statistics Section:
 * Status, Total Episodes, Runtime, Popularity, Favorites, Certification, Production.
 */
@Composable
fun DetailGeneralInfoSection(
    item: MediaItemEntity,
    popularity: Int? = null,
    favourites: Int? = null,
    durationMinutes: Int? = null,
    modifier: Modifier = Modifier
) {
    val isSeries = item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME
    val title = when (item.mediaType) {
        MediaType.SHOW, MediaType.ANIME -> "Show Details"
        MediaType.MOVIE -> "Movie Details"
        MediaType.GAME -> "Game Details"
        MediaType.VISUAL_NOVEL -> "Visual Novel Details"
        MediaType.BOOK -> "Book Details"
        MediaType.MANGA, MediaType.NOVEL -> "Reading Details"
        else -> "Details"
    }

    val rows = buildList {
        if (!item.airStatus.isNullOrBlank()) {
            add("Status" to item.airStatus)
        }
        if (item.totalProgress != null && item.totalProgress > 0 && isSeries) {
            add("Total Episodes" to "${item.totalProgress.toInt()} Episodes")
        }
        val effRuntime = durationMinutes ?: item.runtimeMinutes
        if (effRuntime != null && effRuntime > 0) {
            val hrs = effRuntime / 60
            val mins = effRuntime % 60
            val formatted = if (hrs > 0 && mins > 0) "${hrs}h ${mins}m" else if (hrs > 0) "${hrs}h" else "${mins}m"
            add("Runtime" to formatted)
        }
        if (popularity != null && popularity > 0) {
            add("Popularity" to formatCompactNumber(popularity))
        }
        if (favourites != null && favourites > 0) {
            add("Favorites" to formatCompactNumber(favourites))
        }
        if (!item.contentRating.isNullOrBlank()) {
            add("Certification" to item.contentRating)
        }
        if (!item.platforms.isNullOrBlank()) {
            add("Platform" to item.platforms)
        }
        if (!item.studioOrNetwork.isNullOrBlank()) {
            add("Production" to item.studioOrNetwork)
        }
    }

    if (rows.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = value,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        fontSize = 14.sp,
                        color = Color.White,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (index < rows.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.10f))
                    )
                }
            }
        }
    }
}

/**
 * Alternative Names & Synonyms Section:
 * Romaji, English, Japanese/Native titles, plus copyable Synonym tags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailNamesSynonymsSection(
    item: MediaItemEntity,
    englishTitle: String? = null,
    romajiTitle: String? = null,
    nativeTitle: String? = null,
    synonyms: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val nameRows = buildList {
        val effectiveRomaji = romajiTitle?.ifBlank { null } ?: item.nativeTitle?.takeIf { !it.any { c -> c.code > 127 } }
        val effectiveEnglish = englishTitle?.ifBlank { null }
        val effectiveNative = nativeTitle?.ifBlank { null } ?: item.nativeTitle?.takeIf { it.any { c -> c.code > 127 } }

        if (!effectiveRomaji.isNullOrBlank()) {
            add("Romaji" to effectiveRomaji)
        }
        if (!effectiveEnglish.isNullOrBlank() && !effectiveEnglish.equals(effectiveRomaji, ignoreCase = true)) {
            add("English" to effectiveEnglish)
        }
        if (!effectiveNative.isNullOrBlank() && !effectiveNative.equals(effectiveRomaji, ignoreCase = true) && !effectiveNative.equals(effectiveEnglish, ignoreCase = true)) {
            add("Japanese" to effectiveNative)
        }
    }

    if (nameRows.isEmpty() && synonyms.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Alternative Names Table
        if (nameRows.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Names",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    nameRows.forEachIndexed { index, (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.5.sp,
                                color = Color.White.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = value,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp),
                                fontSize = 13.5.sp,
                                color = Color.White,
                                textAlign = TextAlign.End,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (index < nameRows.size - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.08f))
                            )
                        }
                    }
                }
            }
        }

        // Synonyms Chip FlowRow
        if (synonyms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Synonyms",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    synonyms.forEach { syn ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, GlassBorder, RoundedCornerShape(999.dp))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(syn))
                                    Toast.makeText(context, "Copied \"$syn\"", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = syn,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clickable Genres & Tags Section:
 * Allows user to tap any genre or anime tag to filter and search other titles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailGenresTagsSection(
    item: MediaItemEntity,
    genres: List<String> = emptyList(),
    tags: List<AnimeTagItem> = emptyList(),
    onGenreOrTagClick: ((name: String, isTag: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val effectiveGenres = genres.ifEmpty {
        item.categoryNames.split(",", "/").map { it.trim() }.filter { it.isNotBlank() }
    }
    if (effectiveGenres.isEmpty() && tags.isEmpty()) return

    var showAllTags by remember { mutableStateOf(false) }
    val visibleTags = if (showAllTags) tags else tags.take(12)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Genres & Tags",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Genres (Pure White)
            effectiveGenres.forEach { genre ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AccentArctic.copy(alpha = 0.12f))
                        .border(1.dp, AccentArctic.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                        .clickable {
                            onGenreOrTagClick?.invoke(genre, false)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = genre,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Tags (Pure White with rank %)
            visibleTags.forEach { tagItem ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(999.dp))
                        .clickable {
                            onGenreOrTagClick?.invoke(tagItem.name, true)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Label,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (tagItem.rank != null) "${tagItem.name} ${tagItem.rank}%" else tagItem.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (tags.size > 12) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { showAllTags = !showAllTags }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (showAllTags) "Show Less" else "+${tags.size - 12} More",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 1:1 Dartotsu & Nuvio Unboxed Details Section (Composite wrapper for all 3):
 */
@Composable
fun DetailShowDetailsSection(
    item: MediaItemEntity,
    englishTitle: String? = null,
    romajiTitle: String? = null,
    nativeTitle: String? = null,
    synonyms: List<String> = emptyList(),
    genres: List<String> = emptyList(),
    tags: List<AnimeTagItem> = emptyList(),
    popularity: Int? = null,
    favourites: Int? = null,
    durationMinutes: Int? = null,
    onGenreOrTagClick: ((name: String, isTag: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        DetailGeneralInfoSection(
            item = item,
            popularity = popularity,
            favourites = favourites,
            durationMinutes = durationMinutes
        )
        DetailNamesSynonymsSection(
            item = item,
            englishTitle = englishTitle,
            romajiTitle = romajiTitle,
            nativeTitle = nativeTitle,
            synonyms = synonyms
        )
        DetailGenresTagsSection(
            item = item,
            genres = genres,
            tags = tags,
            onGenreOrTagClick = onGenreOrTagClick
        )
    }
}
