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
 * Format, Status, Season & Year, Source, Aired Range, Broadcast Time, Total Episodes, Runtime, Popularity, Favorites, Certification.
 */
@Composable
fun DetailGeneralInfoSection(
    item: MediaItemEntity,
    popularity: Int? = null,
    favourites: Int? = null,
    durationMinutes: Int? = null,
    format: String? = null,
    season: String? = null,
    seasonYear: Int? = null,
    source: String? = null,
    countryOfOrigin: String? = null,
    startDate: String? = null,
    endDate: String? = null,
    nextAiringEpochSeconds: Long? = null,
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

    fun formatMediaFormat(raw: String): String = when (raw.uppercase()) {
        "TV" -> "TV"
        "TV_SHORT" -> "TV Short"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "MUSIC" -> "Music Video"
        "MANGA" -> "Manga"
        "NOVEL" -> "Light Novel"
        "ONE_SHOT" -> "One Shot"
        else -> raw.lowercase().replaceFirstChar { it.uppercase() }
    }

    fun formatStatus(raw: String): String = when (raw.uppercase()) {
        "FINISHED" -> "Finished"
        "RELEASING" -> "Releasing"
        "NOT_YET_RELEASED" -> "Not Yet Released"
        "CANCELLED" -> "Cancelled"
        "HIATUS" -> "Hiatus"
        else -> raw.lowercase().replaceFirstChar { it.uppercase() }
    }

    fun formatSource(raw: String): String = when (raw.uppercase()) {
        "ORIGINAL" -> "Original"
        "MANGA" -> "Manga"
        "LIGHT_NOVEL" -> "Light Novel"
        "VISUAL_NOVEL" -> "Visual Novel"
        "VIDEO_GAME", "GAME" -> "Video Game"
        "WEB_NOVEL" -> "Web Novel"
        "LIVE_ACTION" -> "Live Action"
        "COMIC" -> "Comic"
        "MULTIMEDIA_PROJECT" -> "Multimedia Project"
        "PICTURE_BOOK" -> "Picture Book"
        "DOUJINSHI" -> "Doujinshi"
        "NOVEL" -> "Novel"
        else -> raw.lowercase().replaceFirstChar { it.uppercase() }
    }

    val rows = buildList {
        // 1. Format
        if (!format.isNullOrBlank()) {
            add("Format" to formatMediaFormat(format))
        }

        // 2. Status
        val effStatus = item.airStatus?.takeIf { it.isNotBlank() }
        if (!effStatus.isNullOrBlank()) {
            add("Status" to formatStatus(effStatus))
        }

        // 3. Season & Year
        if (!season.isNullOrBlank()) {
            val formattedSeason = when (season.uppercase()) {
                "WINTER" -> "Winter"
                "SPRING" -> "Spring"
                "SUMMER" -> "Summer"
                "FALL" -> "Fall"
                else -> season.lowercase().replaceFirstChar { it.uppercase() }
            }
            val seasonDisplay = if (seasonYear != null && seasonYear > 0) "$formattedSeason $seasonYear" else formattedSeason
            add("Season" to seasonDisplay)
        }

        // 4. Source Material
        if (!source.isNullOrBlank()) {
            add("Source Material" to formatSource(source))
        }

        // 5. Aired Range
        val airedStr = when {
            !startDate.isNullOrBlank() && !endDate.isNullOrBlank() -> "$startDate to $endDate"
            !startDate.isNullOrBlank() && (effStatus?.equals("RELEASING", ignoreCase = true) == true) -> "$startDate to Present"
            !startDate.isNullOrBlank() -> startDate
            item.releaseYear != null -> "${item.releaseYear}"
            else -> null
        }
        if (!airedStr.isNullOrBlank()) {
            val dateLabel = if (item.mediaType == MediaType.MANGA || item.mediaType == MediaType.NOVEL || item.mediaType == MediaType.BOOK) {
                "Published"
            } else {
                "Aired"
            }
            add(dateLabel to airedStr)
        }

        // 6. Dual Broadcast Time (JST + User Local Time)
        if (nextAiringEpochSeconds != null && nextAiringEpochSeconds > 0) {
            try {
                val instant = java.time.Instant.ofEpochSecond(nextAiringEpochSeconds)
                val jstZone = java.time.ZoneId.of("Asia/Tokyo")
                val jstZoned = instant.atZone(jstZone)
                val jstDay = jstZoned.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                val jstTime = java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(jstZoned)

                val localZone = java.time.ZoneId.systemDefault()
                val localZoned = instant.atZone(localZone)
                val localTime = java.time.format.DateTimeFormatter.ofPattern("h:mm a").format(localZoned)

                add("Broadcast" to "${jstDay}s at $jstTime JST ($localTime local)")
            } catch (_: Exception) {}
        }

        // 7. Total Episodes / Chapters
        if (item.totalProgress != null && item.totalProgress > 0) {
            if (isSeries) {
                add("Total Episodes" to "${item.totalProgress.toInt()} Episodes")
            } else if (item.mediaType == MediaType.MANGA) {
                add("Total Chapters" to "${item.totalProgress.toInt()} Chapters")
            }
        }

        // 8. Runtime
        val effRuntime = durationMinutes ?: item.runtimeMinutes
        if (effRuntime != null && effRuntime > 0) {
            val hrs = effRuntime / 60
            val mins = effRuntime % 60
            val formatted = if (hrs > 0 && mins > 0) "${hrs}h ${mins}m" else if (hrs > 0) "${hrs}h" else "${mins}m"
            add("Runtime" to formatted)
        }

        // 9. Origin Country
        val effectiveOrigin = countryOfOrigin?.takeIf { it.isNotBlank() } ?: item.originCountry?.takeIf { it.isNotBlank() }
        val countryDisplay = when (effectiveOrigin?.uppercase()) {
            "JP" -> "Japan (JP)"
            "CN" -> "China (CN)"
            "KR" -> "South Korea (KR)"
            "US" -> "United States (US)"
            "GB", "UK" -> "United Kingdom (UK)"
            null -> null
            else -> effectiveOrigin
        }
        if (!countryDisplay.isNullOrBlank()) {
            add("Origin Country" to countryDisplay)
        }

        // 10. Popularity & Favorites
        if (popularity != null && popularity > 0) {
            add("Popularity" to formatCompactNumber(popularity))
        }
        if (favourites != null && favourites > 0) {
            add("Favorites" to formatCompactNumber(favourites))
        }

        // 11. Certification & Platform & Production (for non-anime or fallback)
        if (!item.contentRating.isNullOrBlank()) {
            add("Certification" to item.contentRating)
        }
        if (!item.platforms.isNullOrBlank()) {
            add("Platform" to item.platforms)
        }
        if (!item.studioOrNetwork.isNullOrBlank() && !isSeries) {
            val companyLabel = if (item.mediaType == MediaType.MANGA || item.mediaType == MediaType.NOVEL) {
                "Serialization"
            } else {
                "Production"
            }
            add(companyLabel to item.studioOrNetwork)
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
