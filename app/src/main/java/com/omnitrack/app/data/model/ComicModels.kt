package com.omnitrack.app.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class ComicFormat(val label: String) {
    SINGLE_ISSUES("Single Issues"),
    TPB("Trade Paperback"),
    HARDCOVER("Hardcover"),
    OMNIBUS("Omnibus"),
    DIGITAL("Digital");

    companion object {
        fun fromName(raw: String?): ComicFormat = when (raw?.uppercase()?.trim()) {
            "TPB", "TRADE_PAPERBACK", "TRADE PAPERBACK", "PAPERBACK" -> TPB
            "HARDCOVER", "HC" -> HARDCOVER
            "OMNIBUS", "ABSOLUTE", "COMPENDIUM" -> OMNIBUS
            "DIGITAL", "WEBCOMIC" -> DIGITAL
            else -> SINGLE_ISSUES
        }
    }
}

@Serializable
enum class ComicTrackingMode(val label: String) {
    ISSUES("Issues"),
    TPB_VOLUMES("Volumes / TPB"),
    COLLECTED("Collected");

    companion object {
        fun fromName(raw: String?): ComicTrackingMode = when (raw?.uppercase()?.trim()) {
            "TPB_VOLUMES", "VOLUMES", "TPB" -> TPB_VOLUMES
            "COLLECTED", "OMNIBUS" -> COLLECTED
            else -> ISSUES
        }
    }
}

@Serializable
enum class ComicEntryType(val label: String) {
    ISSUE("Issue"),
    ANNUAL("Annual"),
    TPB("TPB"),
    OMNIBUS("Omnibus"),
    SPECIAL("Special");

    companion object {
        fun fromName(raw: String?): ComicEntryType = when (raw?.uppercase()?.trim()) {
            "ANNUAL" -> ANNUAL
            "TPB", "TRADE_PAPERBACK" -> TPB
            "OMNIBUS" -> OMNIBUS
            "SPECIAL" -> SPECIAL
            else -> ISSUE
        }
    }
}

@Immutable
@Serializable
data class ComicCreatorItem(
    val name: String,
    val role: String,
    val imageUrl: String? = null
)

@Immutable
@Serializable
data class ComicCharacterItem(
    val name: String,
    val role: String? = null,
    val imageUrl: String? = null
)

@Immutable
@Serializable
data class ComicVariantCover(
    val url: String,
    val label: String,
    val artist: String? = null
)

@Immutable
data class ComicDetailMeta(
    val publisher: String? = null,
    val imprint: String? = null,
    val startYear: String? = null,
    val endYear: String? = null,
    val issueCount: Int? = null,
    val storyArcs: List<String> = emptyList(),
    val creators: List<ComicCreatorItem> = emptyList(),
    val characters: List<ComicCharacterItem> = emptyList(),
    val variantCovers: List<ComicVariantCover> = emptyList(),
    val appleBooksRating: Float? = null,
    val openLibraryRating: Float? = null,
    val comicVineUrl: String? = null,
    val metronUrl: String? = null
)

fun normalizeComicPublisherGroup(publisher: String?, platform: String?): String {
    val raw = "${publisher.orEmpty()} ${platform.orEmpty()}".lowercase()
    return when {
        "marvel" in raw -> "MARVEL"
        "dc comics" in raw || "dc entertainment" in raw || raw.trim() == "dc" ||
            "vertigo" in raw || "black label" in raw || "wildstorm" in raw -> "DC"
        "image" in raw -> "IMAGE"
        "dark horse" in raw -> "DARK_HORSE"
        "idw" in raw -> "IDW"
        else -> "INDIE"
    }
}

fun normalizeComicFormatGroup(format: String?): String = when (format?.uppercase()?.trim()) {
    "TPB", "TRADE_PAPERBACK", "HARDCOVER", "HC" -> "TPB"
    "OMNIBUS", "ABSOLUTE", "COMPENDIUM" -> "OMNIBUS"
    "DIGITAL", "WEBCOMIC" -> "DIGITAL"
    else -> "ISSUES"
}

fun formatComicCardBadge(
    publisher: String?,
    format: String?,
    currentProgress: Double,
    totalProgress: Double?,
    trackingMode: String? = null
): String {
    val cur = currentProgress.toInt()
    val tot = totalProgress?.toInt()
    val unit = when (ComicTrackingMode.fromName(trackingMode)) {
        ComicTrackingMode.TPB_VOLUMES -> "Vol"
        ComicTrackingMode.COLLECTED -> "Ed"
        ComicTrackingMode.ISSUES -> "Issue"
    }
    val progressPart = when {
        tot != null && tot > 0 -> "$unit $cur/$tot"
        cur > 0 -> "$unit $cur"
        else -> format?.takeIf { it.isNotBlank() }?.let { ComicFormat.fromName(it).label }
    }
    val pub = publisher?.takeIf { it.isNotBlank() }
    return listOfNotNull(pub, progressPart).joinToString(" · ").ifBlank { "Comics" }
}
