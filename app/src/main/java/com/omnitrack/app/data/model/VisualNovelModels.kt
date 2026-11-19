package com.omnitrack.app.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class VnSpoilerLevel(val level: Int, val displayName: String, val description: String) {
    NONE(0, "None", "Hide minor & major plot spoilers by default"),
    MINOR(1, "Minor", "Show minor tags & traits; mask major spoilers"),
    ALL(2, "All", "Show all spoiler tags, routes, and traits unmasked");

    companion object {
        fun fromLevel(level: Int?): VnSpoilerLevel = entries.firstOrNull { it.level == level } ?: MINOR
    }
}

@Serializable
enum class VnNsfwFilterMode(val displayName: String, val description: String) {
    SAFE_HIDE("Safe (Hide)", "Exclude NSFW covers, CGs, and Ero tags completely"),
    BLUR_TAP("Blur (Tap to Reveal)", "Blur sensitive CGs & covers until tapped"),
    SHOW_ALL("Show All", "Display all VNDB images and Ero tags unblurred");

    companion object {
        fun fromName(name: String?): VnNsfwFilterMode = when (name?.uppercase()) {
            "SAFE_HIDE" -> SAFE_HIDE
            "SHOW_ALL" -> SHOW_ALL
            else -> BLUR_TAP
        }
    }
}

@Immutable
@Serializable
data class VnScreenshotItem(
    val url: String,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sexual: Float = 0f,
    val violence: Float = 0f,
    val releaseTitle: String? = null
) {
    val isSensitive: Boolean
        get() = sexual > 0.45f || violence > 0.9f
}

@Immutable
@Serializable
data class VnReleaseItem(
    val id: String,
    val title: String,
    val released: String? = null,
    val isOfficial: Boolean = true,
    val isPatch: Boolean = false,
    val languages: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val producers: List<String> = emptyList(),
    val voicedLevel: Int? = null,
    val minAge: Int? = null,
    val engine: String? = null,
    val resolution: String? = null,
    val primaryUrl: String? = null,
    val steamAppId: Long? = null
)

@Immutable
@Serializable
data class VnTraitItem(
    val id: String,
    val name: String,
    val groupName: String? = null,
    val spoilerLevel: Int = 0
)

@Immutable
@Serializable
data class VnCharacterProfile(
    val vndbCharId: String,
    val name: String,
    val nativeName: String? = null,
    val imageUrl: String? = null,
    val sexual: Float = 0f,
    val violence: Float = 0f,
    val role: String = "main",
    val description: String? = null,
    val sex: String? = null,
    val age: Int? = null,
    val birthday: String? = null,
    val bloodType: String? = null,
    val heightCm: Int? = null,
    val weightKg: Int? = null,
    val voiceActorName: String? = null,
    val voiceActorId: Long? = null,
    val traits: List<VnTraitItem> = emptyList()
)

@Immutable
data class VnDetailMeta(
    val vndbScore: Float? = null,
    val vndbVotes: Int? = null,
    val lengthMinutes: Int? = null,
    val lengthVotes: Int? = null,
    val lengthTier: String? = null,
    val devStatus: String? = null,
    val steamAppId: Long? = null,
    val steamPositivePct: Int? = null,
    val steamReviewDesc: String? = null,
    val voicedLevel: Int? = null,
    val minAge: Int? = null,
    val engine: String? = null,
    val screenshots: List<VnScreenshotItem> = emptyList(),
    val releases: List<VnReleaseItem> = emptyList(),
    val characterProfiles: Map<String, VnCharacterProfile> = emptyMap(),
    val coverSexual: Float = 0f,
    val coverViolence: Float = 0f
)

fun formatVndbVoicedLabel(level: Int?): String? = when (level) {
    1 -> "Unvoiced"
    2 -> "Only Ero Voiced"
    3 -> "Partially Voiced"
    4 -> "Fully Voiced"
    else -> null
}

fun formatVndbLengthTier(length: Int?, minutes: Int?): String? {
    val tier = when (length) {
        1 -> "Very Short"
        2 -> "Short"
        3 -> "Medium"
        4 -> "Long"
        5 -> "Very Long"
        else -> when {
            minutes == null || minutes <= 0 -> null
            minutes < 120 -> "Very Short"
            minutes < 600 -> "Short"
            minutes < 1800 -> "Medium"
            minutes < 3000 -> "Long"
            else -> "Very Long"
        }
    }
    return tier
}

fun inferVnOriginGroup(originCountry: String?, nativeTitle: String? = null): String {
    val norm = originCountry?.trim()?.uppercase()
    return when {
        norm == "JP" || norm == "JA" -> "JP"
        norm == "EN" || norm == "US" || norm == "GB" || norm == "UK" || norm == "CA" || norm == "AU" -> "EN"
        norm == "CN" || norm == "ZH" || norm == "TW" || norm == "HK" || norm == "KR" || norm == "KO" -> "CNKR"
        !nativeTitle.isNullOrBlank() -> {
            val hasKana = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF]").containsMatchIn(nativeTitle)
            val hasHangulOrCjk = Regex("[\\uAC00-\\uD7AF\\u4E00-\\u9FFF]").containsMatchIn(nativeTitle)
            when {
                hasKana -> "JP"
                hasHangulOrCjk -> "CNKR"
                else -> "EN"
            }
        }
        else -> "JP"
    }
}

fun inferVnLengthGroup(runtimeMinutes: Int?): String {
    if (runtimeMinutes == null || runtimeMinutes <= 0) return "UNKNOWN"
    return when {
        runtimeMinutes < 600 -> "SHORT"
        runtimeMinutes < 1800 -> "MEDIUM"
        runtimeMinutes < 3000 -> "LONG"
        else -> "VLONG"
    }
}

fun formatVnCardBadge(originCountry: String?, runtimeMinutes: Int?): String {
    val origin = when (inferVnOriginGroup(originCountry)) {
        "EN" -> "EN"
        "CNKR" -> originCountry?.trim()?.uppercase()?.take(2) ?: "CN"
        else -> "JP"
    }
    val hoursText = runtimeMinutes?.takeIf { it > 0 }?.let { mins ->
        val h = kotlin.math.round(mins / 60.0).toInt().coerceAtLeast(1)
        " · ~${h}h"
    }.orEmpty()
    return "VN · $origin$hoursText"
}

private val VNDB_SPOILER_TAG_REGEX = Regex("\\[spoiler](.*?)\\[/spoiler]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val VNDB_URL_TAG_REGEX = Regex("\\[url=[^]]*](.*?)\\[/url]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val VNDB_OTHER_BBCODE_REGEX = Regex("\\[/?(?:b|i|u|s|code|quote|raw)]", RegexOption.IGNORE_CASE)

private fun stripNonSpoilerBbCode(text: String): String {
    return text
        .replace(VNDB_URL_TAG_REGEX) { it.groupValues[1] }
        .replace(VNDB_OTHER_BBCODE_REGEX, "")
}

/**
 * Splits a VNDB description into ordered (text, isSpoiler) segments while cleaning BBCode markup.
 */
fun parseVndbBbCodeSegments(raw: String): List<Pair<String, Boolean>> {
    if (raw.isBlank()) return emptyList()
    val segments = mutableListOf<Pair<String, Boolean>>()
    var cursor = 0
    for (match in VNDB_SPOILER_TAG_REGEX.findAll(raw)) {
        if (match.range.first > cursor) {
            val plain = stripNonSpoilerBbCode(raw.substring(cursor, match.range.first))
            if (plain.isNotEmpty()) segments.add(plain to false)
        }
        val spoilerBody = stripNonSpoilerBbCode(match.groupValues[1]).trim()
        if (spoilerBody.isNotEmpty()) {
            segments.add(spoilerBody to true)
        }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
        val tail = stripNonSpoilerBbCode(raw.substring(cursor))
        if (tail.isNotEmpty()) segments.add(tail to false)
    }
    return if (segments.isEmpty()) listOf(stripNonSpoilerBbCode(raw) to false) else segments
}
