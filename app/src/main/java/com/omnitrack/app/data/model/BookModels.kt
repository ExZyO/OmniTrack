package com.omnitrack.app.data.model

import androidx.compose.runtime.Immutable
import com.omnitrack.app.ui.screens.detail.components.RecommendationItem
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.roundToInt

@Serializable
enum class BookFormat(val label: String) {
    HARDCOVER("Hardcover"),
    PAPERBACK("Paperback"),
    EBOOK("E-book"),
    AUDIOBOOK("Audiobook"),
    ANNOTATED("Annotated"),
    BILINGUAL("Bilingual");

    companion object {
        fun fromName(raw: String?): BookFormat = when (raw?.uppercase()?.trim()) {
            "HARDCOVER" -> HARDCOVER
            "PAPERBACK" -> PAPERBACK
            "EBOOK", "E-BOOK", "KINDLE" -> EBOOK
            "AUDIOBOOK", "AUDIO" -> AUDIOBOOK
            "ANNOTATED" -> ANNOTATED
            "BILINGUAL" -> BILINGUAL
            else -> HARDCOVER
        }
    }
}

@Serializable
enum class BookLiteraryForm(val label: String) {
    NOVEL("Novel"),
    EPIC_POEM("Epic Poem"),
    POETRY("Poetry"),
    VERSE_PLAY("Verse Tragedy"),
    PLAY("Play / Drama"),
    NON_FICTION("Non-Fiction"),
    PHILOSOPHY("Philosophy"),
    ESSAYS("Essays"),
    SHORT_STORIES("Short Stories"),
    MEMOIR("Memoir");

    companion object {
        fun fromName(raw: String?): BookLiteraryForm = when (raw?.uppercase()?.trim()) {
            "EPIC_POEM" -> EPIC_POEM
            "POETRY" -> POETRY
            "VERSE_PLAY" -> VERSE_PLAY
            "PLAY" -> PLAY
            "NON_FICTION" -> NON_FICTION
            "PHILOSOPHY" -> PHILOSOPHY
            "ESSAYS" -> ESSAYS
            "SHORT_STORIES" -> SHORT_STORIES
            "MEMOIR" -> MEMOIR
            else -> NOVEL
        }
    }
}

@Serializable
enum class BookEntryType(val label: String) {
    BOOK_PART("Book / Part"),
    CANTO("Canto"),
    ACT("Act / Scene"),
    CHAPTER("Chapter / Poem"),
    REREAD("Reread"),
    TRANSLATION("Edition / Translation");

    companion object {
        fun fromName(raw: String?): BookEntryType = when (raw?.uppercase()?.trim()) {
            "CANTO" -> CANTO
            "ACT" -> ACT
            "CHAPTER" -> CHAPTER
            "REREAD" -> REREAD
            "TRANSLATION" -> TRANSLATION
            else -> BOOK_PART
        }
    }
}

@Serializable
enum class BookTrackingMode(val label: String) {
    PAGES("Pages"),
    PERCENT("%"),
    AUDIO_MINUTES("Audiobook");

    companion object {
        fun fromName(raw: String?): BookTrackingMode = when (raw?.uppercase()?.trim()) {
            "PERCENT" -> PERCENT
            "AUDIO_MINUTES", "AUDIOBOOK", "AUDIO" -> AUDIO_MINUTES
            else -> PAGES
        }
    }
}

@Immutable
@Serializable
data class BookEditionItem(
    val title: String,
    val publisher: String? = null,
    val publishYear: String? = null,
    val translator: String? = null,
    val format: String? = null,
    val pageCount: Int? = null,
    val isbn13: String? = null,
    val coverUrl: String? = null,
    val openLibraryEditionKey: String? = null
)

@Immutable
@Serializable
data class BookIllustrationAsset(
    val url: String,
    val thumbnailUrl: String? = null,
    val caption: String? = null,
    val isWideHero: Boolean = false
)

@Immutable
data class BookAuthorProfile(
    val openLibraryKey: String? = null,
    val name: String,
    val bio: String? = null,
    val birthDeath: String? = null,
    val photoUrl: String? = null,
    val works: List<RecommendationItem> = emptyList()
)

@Immutable
data class BookDetailMeta(
    val openLibraryWorkId: String? = null,
    val hardcoverId: String? = null,
    val isbn13: String? = null,
    val wikidataId: String? = null,
    val authorName: String? = null,
    val authorProfile: BookAuthorProfile? = null,
    val literaryForm: BookLiteraryForm? = null,
    val originalLanguage: String? = null,
    val publisher: String? = null,
    val translator: String? = null,
    val editionName: String? = null,
    val pageCount: Int? = null,
    val lineCount: Int? = null,
    val audioDurationMinutes: Int? = null,
    val audiobookNarrator: String? = null,
    val seriesName: String? = null,
    val seriesPosition: String? = null,
    val seriesTotal: Int? = null,
    val openLibraryRating: Float? = null,
    val openLibraryRatingCount: Int? = null,
    val hardcoverRating: Float? = null,
    val hardcoverRatingCount: Int? = null,
    val appleBooksRating: Float? = null,
    val appleBooksRatingCount: Int? = null,
    val wantToReadCount: Int? = null,
    val alreadyReadCount: Int? = null,
    val moods: List<String> = emptyList(),
    val awards: List<String> = emptyList(),
    val contentWarnings: List<String> = emptyList(),
    val editions: List<BookEditionItem> = emptyList(),
    val illustrations: List<BookIllustrationAsset> = emptyList(),
    val poemSampleLines: List<String> = emptyList(),
    val gutenbergUrl: String? = null,
    val standardEbooksUrl: String? = null
)

fun inferBookLiteraryForm(
    title: String,
    subjects: List<String> = emptyList(),
    description: String? = null
): BookLiteraryForm {
    val combined = (listOf(title) + subjects + listOfNotNull(description)).joinToString(" ").lowercase()
    return when {
        "epic poem" in combined || "epic poetry" in combined ||
            "paradise lost" in combined || "divine comedy" in combined ||
            "iliad" in combined || "odyssey" in combined || "aeneid" in combined ||
            "beowulf" in combined || "metamorphoses" in combined -> BookLiteraryForm.EPIC_POEM
        "closet drama" in combined || "verse drama" in combined || "verse tragedy" in combined ||
            "faust" in combined -> BookLiteraryForm.VERSE_PLAY
        "poetry" in combined || "poems" in combined || "sonnets" in combined || "lyric poetry" in combined ->
            BookLiteraryForm.POETRY
        "drama" in combined || "tragedy" in combined || "tragedies" in combined || "theatrical play" in combined ->
            BookLiteraryForm.PLAY
        "philosophy" in combined || "stoic" in combined || "metaphysics" in combined || "ethics" in combined ->
            BookLiteraryForm.PHILOSOPHY
        "essays" in combined -> BookLiteraryForm.ESSAYS
        "short stories" in combined || "stories" in combined -> BookLiteraryForm.SHORT_STORIES
        "memoir" in combined || "autobiography" in combined -> BookLiteraryForm.MEMOIR
        "non-fiction" in combined || "nonfiction" in combined || "biography" in combined || "history" in combined ->
            BookLiteraryForm.NON_FICTION
        else -> BookLiteraryForm.NOVEL
    }
}

fun formatBookDuration(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) return null
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${String.format(Locale.US, "%02d", m)}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

fun formatEstimatedReadTime(pages: Int?): String? {
    if (pages == null || pages <= 0) return null
    // ~40 pages per hour calibrated reading pace
    val hours = pages / 40.0
    return if (hours >= 100.0) "~${hours.roundToInt()}h" else String.format(Locale.US, "~%.1fh", hours)
}

fun normalizeBookFormatGroup(bookFormat: String?): String = when (bookFormat?.uppercase()?.trim()) {
    "AUDIOBOOK", "AUDIO" -> "AUDIO"
    "EBOOK", "E-BOOK" -> "EBOOK"
    else -> "PRINT"
}

fun inferBookCategoryGroup(title: String, platform: String?, categoryNames: String?): String {
    val combined = listOfNotNull(title, platform, categoryNames).joinToString(" ").lowercase()
    return when {
        "epic" in combined || "poem" in combined || "poetry" in combined || "classic" in combined ||
            "paradise lost" in combined || "faust" in combined || "odyssey" in combined || "iliad" in combined -> "CLASSICS_POETRY"
        "verse_play" in combined || "play" in combined || "drama" in combined || "tragedy" in combined -> "PLAYS"
        "non_fiction" in combined || "non-fiction" in combined || "philosophy" in combined || "essay" in combined || "memoir" in combined || "history" in combined -> "NON_FICTION"
        else -> "FICTION"
    }
}

fun formatBookCardBadge(
    currentProgress: Double,
    totalProgress: Double?,
    trackingMode: String?,
    audioMinutes: Int?,
    totalAudioMinutes: Int?,
    bookFormat: String?,
    author: String? = null
): String {
    val mode = BookTrackingMode.fromName(trackingMode)
    val prog = when (mode) {
        BookTrackingMode.AUDIO_MINUTES -> {
            val cur = formatBookDuration(audioMinutes)
            val tot = formatBookDuration(totalAudioMinutes)
            when {
                cur != null && tot != null -> "$cur / $tot"
                cur != null -> cur
                tot != null -> tot
                else -> null
            }
        }
        BookTrackingMode.PERCENT -> {
            val pct = when {
                totalProgress != null && totalProgress > 0.0 -> ((currentProgress / totalProgress) * 100.0).roundToInt().coerceIn(0, 100)
                currentProgress > 0.0 -> currentProgress.roundToInt().coerceIn(0, 100)
                else -> 0
            }
            if (pct > 0) "$pct%" else null
        }
        BookTrackingMode.PAGES -> {
            val c = currentProgress.toInt()
            val t = totalProgress?.toInt()
            when {
                t != null && t > 0 -> "$c / $t pg"
                c > 0 -> "$c pg"
                else -> null
            }
        }
    }
    val fmtShort = when (BookFormat.fromName(bookFormat)) {
        BookFormat.AUDIOBOOK -> "AUDIO"
        BookFormat.EBOOK -> "EBOOK"
        BookFormat.BILINGUAL -> "BILING"
        BookFormat.ANNOTATED -> "ANNOT"
        else -> null
    }
    val lead = fmtShort ?: author?.split(",")?.firstOrNull()?.trim()?.take(18)?.takeIf { it.isNotBlank() }
    return listOfNotNull(lead, prog).joinToString(" · ").ifBlank { "BOOK" }
}
