package com.omnitrack.app.ui.screens.detail.components

import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.blur
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
import com.omnitrack.app.data.model.BookDetailMeta
import com.omnitrack.app.data.model.ComicDetailMeta
import com.omnitrack.app.data.model.ComicFormat
import com.omnitrack.app.data.model.GameDetailMeta
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.VnDetailMeta
import com.omnitrack.app.data.model.VnNsfwFilterMode
import com.omnitrack.app.data.model.VnSpoilerLevel
import com.omnitrack.app.data.model.formatBookDuration
import com.omnitrack.app.data.model.formatProtonDbLabel
import com.omnitrack.app.data.model.formatSteamDeckLabel
import com.omnitrack.app.data.model.formatVndbVoicedLabel
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextSecondary
import java.util.Locale

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
    vnDetailMeta: VnDetailMeta? = null,
    gameDetailMeta: GameDetailMeta? = null,
    bookDetailMeta: BookDetailMeta? = null,
    comicDetailMeta: ComicDetailMeta? = null,
    modifier: Modifier = Modifier
) {
    val isSeries = item.mediaType == MediaType.SHOW || item.mediaType == MediaType.ANIME
    val title = when (item.mediaType) {
        MediaType.SHOW, MediaType.ANIME -> "Show Details"
        MediaType.MOVIE -> "Movie Details"
        MediaType.GAME -> "Game Details"
        MediaType.VISUAL_NOVEL -> "Visual Novel Details"
        MediaType.BOOK -> "Book Details"
        MediaType.COMIC -> "Comic Details"
        MediaType.FANFICTION -> "Fanfiction Details"
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
        "NOVEL" -> if (item.platform == "WEB_NOVEL") "Web Novel" else "Light Novel"
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
            val dateLabel = when (item.mediaType) {
                MediaType.MANGA, MediaType.NOVEL, MediaType.BOOK, MediaType.COMIC, MediaType.FANFICTION -> "Published"
                MediaType.VISUAL_NOVEL, MediaType.GAME -> "Released"
                else -> "Aired"
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

        // 8. Runtime / VN Length
        val effRuntime = durationMinutes ?: item.runtimeMinutes
        if (item.mediaType == MediaType.VISUAL_NOVEL) {
            val vnMins = vnDetailMeta?.lengthMinutes ?: effRuntime
            if (vnMins != null && vnMins > 0) {
                val lengthFormatted = buildString {
                    append("~")
                    append(String.format(Locale.US, "%.1f", vnMins / 60.0))
                    append("h")
                    vnDetailMeta?.lengthTier?.takeIf { it.isNotBlank() }?.let { tier ->
                        append(" · ")
                        append(tier)
                    }
                    vnDetailMeta?.lengthVotes?.takeIf { it > 0 }?.let { votes ->
                        append(" · ")
                        append(String.format(Locale.US, "%,d", votes))
                        append(" votes")
                    }
                }
                add("Length" to lengthFormatted)
            }
            formatVndbVoicedLabel(vnDetailMeta?.voicedLevel)?.let { voiced ->
                add("Voice Acting" to voiced)
            }
            vnDetailMeta?.engine?.takeIf { it.isNotBlank() }?.let { eng ->
                add("Engine" to eng)
            }
            vnDetailMeta?.devStatus?.takeIf { it.isNotBlank() }?.let { dev ->
                add("Dev Status" to dev)
            }
        } else if (item.mediaType == MediaType.GAME) {
            val hltb = gameDetailMeta?.hltb
            if (hltb != null && hltb.hasAny) {
                val hltbParts = buildList {
                    hltb.mainHours?.let { add("~${String.format(Locale.US, "%.1f", it)}h Main") }
                    hltb.extraHours?.let { add("~${String.format(Locale.US, "%.1f", it)}h Extra") }
                    hltb.completeHours?.let { add("~${String.format(Locale.US, "%.1f", it)}h 100%") }
                }
                if (hltbParts.isNotEmpty()) {
                    add("Time to Beat" to hltbParts.joinToString(" · "))
                }
            }
            formatSteamDeckLabel(gameDetailMeta?.steamDeckCategory)?.let { deckLabel ->
                add("Steam Deck" to deckLabel)
            }
            formatProtonDbLabel(gameDetailMeta?.protonDbTier)?.let { protonLabel ->
                val withCount = gameDetailMeta?.protonDbTotal?.takeIf { it > 0 }?.let { total ->
                    "$protonLabel ($total reports)"
                } ?: protonLabel
                add("ProtonDB" to withCount)
            }
            gameDetailMeta?.ageRatings?.takeIf { it.isNotEmpty() }?.let { ratings ->
                add("Age Rating" to ratings.joinToString(" · "))
            }
            gameDetailMeta?.gameEngine?.takeIf { it.isNotBlank() }?.let { eng ->
                add("Engine" to eng)
            }
            gameDetailMeta?.gameModes?.takeIf { it.isNotEmpty() }?.let { modes ->
                add("Modes" to modes.joinToString(", "))
            }
            gameDetailMeta?.perspectives?.takeIf { it.isNotEmpty() }?.let { persp ->
                add("Perspective" to persp.joinToString(", "))
            }
            gameDetailMeta?.supportedPlatforms?.takeIf { it.isNotEmpty() }?.let { plats ->
                add("Supported Platforms" to plats.take(6).joinToString(", "))
            }
        } else if (item.mediaType == MediaType.BOOK) {
            (bookDetailMeta?.authorName ?: item.bookAuthor)?.takeIf { it.isNotBlank() }?.let {
                add("Author" to it)
            }
            bookDetailMeta?.literaryForm?.label?.takeIf { it.isNotBlank() }?.let {
                add("Literary Form" to it)
            }
            bookDetailMeta?.originalLanguage?.takeIf { it.isNotBlank() }?.let {
                add("Original Language" to it)
            }
            bookDetailMeta?.seriesName?.takeIf { it.isNotBlank() }?.let { sName ->
                val pos = bookDetailMeta.seriesPosition?.takeIf { it.isNotBlank() }?.let { " #$it" }.orEmpty()
                val tot = bookDetailMeta.seriesTotal?.takeIf { it > 0 }?.let { " (of $it)" }.orEmpty()
                add("Series" to "$sName$pos$tot")
            }
            (item.bookTranslator ?: bookDetailMeta?.translator)?.takeIf { it.isNotBlank() }?.let {
                add("Translator" to it)
            }
            (item.bookEditionLabel ?: bookDetailMeta?.editionName)?.takeIf { it.isNotBlank() }?.let {
                add("Edition" to it)
            }
            (bookDetailMeta?.publisher ?: item.studioOrNetwork)?.takeIf { it.isNotBlank() }?.let {
                add("Publisher" to it)
            }
            val effPages = item.totalProgress?.toInt()?.takeIf { it > 0 } ?: bookDetailMeta?.pageCount
            effPages?.takeIf { it > 0 }?.let { pg ->
                add("Pages" to "$pg pages")
            }
            bookDetailMeta?.lineCount?.takeIf { it > 0 }?.let { lc ->
                add("Verse Length" to "${String.format(Locale.US, "%,d", lc)} lines")
            }
            formatBookDuration(item.bookTotalAudioMinutes ?: bookDetailMeta?.audioDurationMinutes)?.let { dur ->
                val narrator = bookDetailMeta?.audiobookNarrator?.takeIf { it.isNotBlank() }?.let { " · Narrated by $it" }.orEmpty()
                add("Audiobook" to "$dur$narrator")
            }
            (item.isbn ?: bookDetailMeta?.isbn13)?.takeIf { it.isNotBlank() }?.let {
                add("ISBN" to it)
            }
            bookDetailMeta?.awards?.takeIf { it.isNotEmpty() }?.let {
                add("Awards" to it.joinToString(", "))
            }
            bookDetailMeta?.contentWarnings?.takeIf { it.isNotEmpty() }?.let {
                add("Content Warnings" to it.joinToString(", "))
            }
        } else if (item.mediaType == MediaType.COMIC) {
            (comicDetailMeta?.publisher ?: item.comicPublisher)?.takeIf { it.isNotBlank() }?.let {
                add("Publisher" to it)
            }
            item.comicFormat?.takeIf { it.isNotBlank() }?.let {
                add("Format" to ComicFormat.fromName(it).label)
            }
            val issueTot = item.totalProgress?.toInt()?.takeIf { it > 0 } ?: comicDetailMeta?.issueCount
            issueTot?.takeIf { it > 0 }?.let { count ->
                add("Issues" to "$count issues")
            }
            (comicDetailMeta?.startYear ?: item.releaseYear)?.takeIf { it.isNotBlank() }?.let {
                add("Run Started" to it)
            }
            if (!comicDetailMeta?.storyArcs.isNullOrEmpty()) {
                add("Story Arcs" to comicDetailMeta!!.storyArcs.take(4).joinToString(", "))
            }
        } else if (effRuntime != null && effRuntime > 0) {
            val hrs = effRuntime / 60
            val mins = effRuntime % 60
            val formatted = if (hrs > 0 && mins > 0) "${hrs}h ${mins}m" else if (hrs > 0) "${hrs}h" else "${mins}m"
            add("Runtime" to formatted)
        }

        // 9. Origin Country
        val effectiveOrigin = countryOfOrigin?.takeIf { it.isNotBlank() } ?: item.originCountry?.takeIf { it.isNotBlank() }
        val countryDisplay = when (effectiveOrigin?.uppercase()) {
            "JP", "JA" -> "Japan (JP)"
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
        if (!item.contentRating.isNullOrBlank() && (item.mediaType != MediaType.GAME || gameDetailMeta?.ageRatings.isNullOrEmpty())) {
            add("Certification" to item.contentRating)
        }
        if (!item.platforms.isNullOrBlank() && (item.mediaType != MediaType.GAME || gameDetailMeta?.supportedPlatforms.isNullOrEmpty())) {
            add("Platform" to item.platforms)
        }
        // Primary Creator / Author / Mangaka / Director (for non-Book entries where bookAuthor is set)
        if (item.mediaType != MediaType.BOOK && !item.bookAuthor.isNullOrBlank()) {
            val creatorLabel = when (item.mediaType) {
                MediaType.MANGA -> "Mangaka / Author"
                MediaType.ANIME -> "Original Author / Mangaka"
                MediaType.SHOW -> "Creator / Showrunner"
                MediaType.MOVIE -> "Director / Writer"
                MediaType.COMIC -> "Writer / Artist"
                MediaType.GAME -> "Developer / Director"
                MediaType.VISUAL_NOVEL -> "Scenario Writer"
                else -> "Author"
            }
            add(creatorLabel to item.bookAuthor)
        }
        if (!item.studioOrNetwork.isNullOrBlank() && !isSeries && item.mediaType != MediaType.BOOK && item.mediaType != MediaType.COMIC) {
            val companyLabel = when (item.mediaType) {
                MediaType.VISUAL_NOVEL, MediaType.GAME -> "Developer"
                MediaType.MANGA, MediaType.NOVEL -> "Serialization / Publisher"
                MediaType.FANFICTION -> "Site / Forum"
                else -> "Production / Studio"
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
                    verticalAlignment = Alignment.Top
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
                        lineHeight = 19.sp,
                        color = Color.White,
                        textAlign = TextAlign.End,
                        maxLines = 4,
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
    vnSpoilerLevel: VnSpoilerLevel = VnSpoilerLevel.MINOR,
    vnNsfwFilterMode: VnNsfwFilterMode = VnNsfwFilterMode.BLUR_TAP,
    gameDetailMeta: GameDetailMeta? = null,
    bookDetailMeta: BookDetailMeta? = null,
    onGenreOrTagClick: ((name: String, isTag: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val effectiveGenres = genres.ifEmpty {
        item.categoryNames.split(",", "/").map { it.trim() }.filter { it.isNotBlank() }
    }
    val isVnCategorized = item.mediaType == MediaType.VISUAL_NOVEL && tags.any { !it.category.isNullOrBlank() }

    if (isVnCategorized) {
        val filteredByNsfw = remember(tags, vnNsfwFilterMode) {
            if (vnNsfwFilterMode == VnNsfwFilterMode.SAFE_HIDE) {
                tags.filterNot { it.category.equals("ero", ignoreCase = true) }
            } else {
                tags
            }
        }
        if (effectiveGenres.isEmpty() && filteredByNsfw.isEmpty()) return

        var selectedCategory by remember { mutableStateOf("ALL") }
        var revealAllSpoilers by remember { mutableStateOf(false) }
        var revealedTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
        var showAllTags by remember { mutableStateOf(false) }

        val hasEroCategory = remember(filteredByNsfw) {
            filteredByNsfw.any { it.category.equals("ero", ignoreCase = true) }
        }
        val categories = remember(hasEroCategory) {
            buildList {
                add("ALL" to "All")
                add("cont" to "Content")
                add("tech" to "Technical")
                if (hasEroCategory) add("ero" to "Ero")
            }
        }
        val categoryTags = remember(filteredByNsfw, selectedCategory) {
            if (selectedCategory == "ALL") filteredByNsfw
            else filteredByNsfw.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
        val hasHiddenSpoilers = remember(categoryTags, vnSpoilerLevel) {
            categoryTags.any { it.spoilerLevel > vnSpoilerLevel.level }
        }
        val visibleVnTags = if (showAllTags) categoryTags else categoryTags.take(18)

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Genres & Tags",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                if (hasHiddenSpoilers) {
                    Text(
                        text = if (revealAllSpoilers) "Hide Spoilers" else "Reveal Spoilers",
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { revealAllSpoilers = !revealAllSpoilers }
                            .padding(vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                categories.forEach { (key, label) ->
                    val selected = selectedCategory == key
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .clickable {
                                selectedCategory = key
                                showAllTags = false
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            color = if (selected) Color.White else TextSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (selected) Color.White else Color.Transparent)
                        )
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (selectedCategory == "ALL") {
                    effectiveGenres.forEach { genre ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.09f))
                                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                                .clickable { onGenreOrTagClick?.invoke(genre, false) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = genre,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                visibleVnTags.forEach { tagItem ->
                    val isSpoilerMasked = tagItem.spoilerLevel > vnSpoilerLevel.level &&
                        !revealAllSpoilers &&
                        tagItem.id !in revealedTagIds
                    val scoreSuffix = tagItem.rating?.let { " ${String.format(Locale.US, "%.1f", it)}" }
                        ?: tagItem.rank?.let { " $it%" }
                        ?: ""

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.09f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                            .clickable {
                                if (isSpoilerMasked) {
                                    revealedTagIds = revealedTagIds + tagItem.id
                                } else {
                                    onGenreOrTagClick?.invoke(tagItem.name, true)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "${tagItem.name}$scoreSuffix",
                            color = if (isSpoilerMasked) Color.White.copy(alpha = 0.68f) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = if (isSpoilerMasked) Modifier.blur(6.dp) else Modifier
                        )
                    }
                }
            }

            if (categoryTags.size > 18) {
                Text(
                    text = if (showAllTags) "Show less" else "+${categoryTags.size - 18} more",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { showAllTags = !showAllTags }
                        .padding(vertical = 2.dp)
                )
            }
        }
        return
    }

    if (item.mediaType == MediaType.GAME) {
        val combinedGameTags = remember(tags, effectiveGenres, gameDetailMeta) {
            buildList {
                addAll(tags)
                effectiveGenres.forEachIndexed { idx, g ->
                    if (none { it.name.equals(g, ignoreCase = true) }) {
                        add(AnimeTagItem(id = 10_000L + idx, name = g, category = "genre"))
                    }
                }
                gameDetailMeta?.gameModes.orEmpty().forEachIndexed { idx, m ->
                    if (none { it.name.equals(m, ignoreCase = true) }) {
                        add(AnimeTagItem(id = 20_000L + idx, name = m, category = "mode"))
                    }
                }
                gameDetailMeta?.perspectives.orEmpty().forEachIndexed { idx, p ->
                    if (none { it.name.equals(p, ignoreCase = true) }) {
                        add(AnimeTagItem(id = 30_000L + idx, name = p, category = "mode"))
                    }
                }
            }.distinctBy { it.name.lowercase() }
        }
        if (combinedGameTags.isEmpty()) return

        var selectedCategory by remember { mutableStateOf("ALL") }
        var showAllTags by remember { mutableStateOf(false) }

        val categories = remember(combinedGameTags) {
            buildList {
                add("ALL" to "All")
                if (combinedGameTags.any { it.category.equals("genre", ignoreCase = true) }) add("genre" to "Genres")
                if (combinedGameTags.any { it.category.equals("theme", ignoreCase = true) }) add("theme" to "Themes")
                if (combinedGameTags.any { it.category.equals("mode", ignoreCase = true) }) add("mode" to "Modes")
            }
        }
        val filteredGameTags = remember(combinedGameTags, selectedCategory) {
            if (selectedCategory == "ALL") combinedGameTags
            else combinedGameTags.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
        val visibleGameTags = if (showAllTags) filteredGameTags else filteredGameTags.take(18)

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

            if (categories.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    categories.forEach { (key, label) ->
                        val selected = selectedCategory == key
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .clickable {
                                    selectedCategory = key
                                    showAllTags = false
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.50f),
                                fontSize = 12.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(if (selected) Color.White else Color.Transparent)
                            )
                        }
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                visibleGameTags.forEach { tagItem ->
                    val isGenre = tagItem.category.equals("genre", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.09f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                            .clickable { onGenreOrTagClick?.invoke(tagItem.name, !isGenre) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = tagItem.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isGenre) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            if (filteredGameTags.size > 18) {
                Text(
                    text = if (showAllTags) "Show less" else "+${filteredGameTags.size - 18} more",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { showAllTags = !showAllTags }
                        .padding(vertical = 2.dp)
                )
            }
        }
        return
    }

    if (item.mediaType == MediaType.BOOK) {
        val combinedBookTags = remember(tags, effectiveGenres, bookDetailMeta) {
            buildList {
                addAll(tags)
                effectiveGenres.forEachIndexed { idx, g ->
                    if (none { it.name.equals(g, ignoreCase = true) }) {
                        add(AnimeTagItem(id = 40_000L + idx, name = g, category = "genre"))
                    }
                }
                bookDetailMeta?.moods.orEmpty().forEachIndexed { idx, m ->
                    if (none { it.name.equals(m, ignoreCase = true) }) {
                        add(AnimeTagItem(id = 50_000L + idx, name = m, category = "mood"))
                    }
                }
                bookDetailMeta?.awards.orEmpty().forEachIndexed { idx, a ->
                    if (none { it.name.equals(a, ignoreCase = true) }) {
                        add(AnimeTagItem(id = 60_000L + idx, name = a, category = "award"))
                    }
                }
            }.distinctBy { it.name.lowercase() }
        }
        if (combinedBookTags.isEmpty()) return

        var selectedCategory by remember { mutableStateOf("ALL") }
        var showAllTags by remember { mutableStateOf(false) }

        val categories = remember(combinedBookTags) {
            buildList {
                add("ALL" to "All")
                if (combinedBookTags.any { it.category.equals("genre", ignoreCase = true) }) add("genre" to "Genres")
                if (combinedBookTags.any { it.category.equals("mood", ignoreCase = true) }) add("mood" to "Moods")
                if (combinedBookTags.any { it.category.equals("award", ignoreCase = true) }) add("award" to "Awards")
            }
        }
        val filteredBookTags = remember(combinedBookTags, selectedCategory) {
            if (selectedCategory == "ALL") combinedBookTags
            else combinedBookTags.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
        val visibleBookTags = if (showAllTags) filteredBookTags else filteredBookTags.take(18)

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Genres, Moods & Awards",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            if (categories.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    categories.forEach { (key, label) ->
                        val selected = selectedCategory == key
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .clickable {
                                    selectedCategory = key
                                    showAllTags = false
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                color = if (selected) Color.White else TextSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(if (selected) Color.White else Color.Transparent)
                            )
                        }
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                visibleBookTags.forEach { tagItem ->
                    val isGenre = tagItem.category.equals("genre", ignoreCase = true)
                    val isAward = tagItem.category.equals("award", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isAward) AccentGold.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.09f)
                            )
                            .border(
                                1.dp,
                                if (isAward) AccentGold.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.16f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onGenreOrTagClick?.invoke(tagItem.name, !isGenre) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = tagItem.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isGenre || isAward) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            if (filteredBookTags.size > 18) {
                Text(
                    text = if (showAllTags) "Show less" else "+${filteredBookTags.size - 18} more",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { showAllTags = !showAllTags }
                        .padding(vertical = 2.dp)
                )
            }
        }
        return
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
            // Genres (Neutral Warm Charcoal + White Text)
            effectiveGenres.forEach { genre ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.09f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
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

            // Tags (Neutral Surface with rank %)
            visibleTags.forEach { tagItem ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.09f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
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
    vnDetailMeta: VnDetailMeta? = null,
    gameDetailMeta: GameDetailMeta? = null,
    bookDetailMeta: BookDetailMeta? = null,
    vnSpoilerLevel: VnSpoilerLevel = VnSpoilerLevel.MINOR,
    vnNsfwFilterMode: VnNsfwFilterMode = VnNsfwFilterMode.BLUR_TAP,
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
            durationMinutes = durationMinutes,
            vnDetailMeta = vnDetailMeta,
            gameDetailMeta = gameDetailMeta,
            bookDetailMeta = bookDetailMeta
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
            vnSpoilerLevel = vnSpoilerLevel,
            vnNsfwFilterMode = vnNsfwFilterMode,
            gameDetailMeta = gameDetailMeta,
            bookDetailMeta = bookDetailMeta,
            onGenreOrTagClick = onGenreOrTagClick
        )
    }
}
