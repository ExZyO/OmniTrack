package com.omnitrack.app.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class FanficSite(
    val code: String,
    val displayName: String,
    val shortBadge: String,
    val baseUrl: String,
    val loginUrl: String,
    val accentHex: Long
) {
    AO3(
        code = "AO3",
        displayName = "Archive of Our Own",
        shortBadge = "AO3",
        baseUrl = "https://archiveofourown.org",
        loginUrl = "https://archiveofourown.org/users/login",
        accentHex = 0xFF990000L
    ),
    FFN(
        code = "FFN",
        displayName = "FanFiction.net",
        shortBadge = "FFN",
        baseUrl = "https://www.fanfiction.net",
        loginUrl = "https://www.fanfiction.net",
        accentHex = 0xFF336699L
    ),
    SB(
        code = "SB",
        displayName = "SpaceBattles",
        shortBadge = "SB",
        baseUrl = "https://forums.spacebattles.com",
        loginUrl = "https://forums.spacebattles.com/login/",
        accentHex = 0xFF2B6CB0L
    ),
    SV(
        code = "SV",
        displayName = "Sufficient Velocity",
        shortBadge = "SV",
        baseUrl = "https://forums.sufficientvelocity.com",
        loginUrl = "https://forums.sufficientvelocity.com/login/",
        accentHex = 0xFF6B46C1L
    ),
    QQ(
        code = "QQ",
        displayName = "Questionable Questing",
        shortBadge = "QQ",
        baseUrl = "https://forum.questionablequesting.com",
        loginUrl = "https://forum.questionablequesting.com/login/",
        accentHex = 0xFFD97706L
    ),
    OTHER(
        code = "OTHER",
        displayName = "Web Fanfiction",
        shortBadge = "FIC",
        baseUrl = "",
        loginUrl = "",
        accentHex = 0xFF64748BL
    );

    companion object {
        fun fromCode(code: String?): FanficSite = when (code?.trim()?.uppercase()) {
            "AO3", "ARCHIVEOFOURON", "ARCHIVE OF OUR OWN" -> AO3
            "FFN", "FANFICTION.NET", "FANFICTION" -> FFN
            "SB", "SPACEBATTLES" -> SB
            "SV", "SUFFICIENTVELOCITY", "SUFFICIENT VELOCITY" -> SV
            "QQ", "QUESTIONABLEQUESTING", "QUESTIONABLE QUESTING" -> QQ
            else -> OTHER
        }

        fun fromUrl(url: String?): FanficSite {
            val u = url.orEmpty().lowercase()
            return when {
                "archiveofourown.org" in u || "ao3.org" in u -> AO3
                "fanfiction.net" in u || "fictionpress.com" in u -> FFN
                "spacebattles.com" in u -> SB
                "sufficientvelocity.com" in u -> SV
                "questionablequesting.com" in u -> QQ
                else -> OTHER
            }
        }
    }
}

@Serializable
enum class FanficRating(
    val code: String,
    val label: String,
    val isNsfw: Boolean = false
) {
    GENERAL("G", "General", isNsfw = false),
    TEEN("T", "Teen & Up", isNsfw = false),
    MATURE("M", "Mature", isNsfw = true),
    EXPLICIT("E", "Explicit", isNsfw = true),
    NOT_RATED("NR", "Not Rated", isNsfw = false);

    companion object {
        fun fromRaw(raw: String?): FanficRating {
            val r = raw.orEmpty().trim().uppercase()
            return when {
                r == "G" || r.startsWith("GENERAL") || r == "K" || r == "K+" -> GENERAL
                r == "T" || r.startsWith("TEEN") -> TEEN
                r == "M" || r.startsWith("MATURE") || r.contains("NSFW") -> MATURE
                r == "E" || r.startsWith("EXPLICIT") || r == "MA" -> EXPLICIT
                else -> NOT_RATED
            }
        }
    }
}

@Serializable
enum class FanficWorkStatus(val label: String) {
    ONGOING("Ongoing"),
    COMPLETE("Complete"),
    HIATUS("Hiatus"),
    DEAD("Abandoned / Dead");

    companion object {
        fun fromRaw(raw: String?): FanficWorkStatus = when (raw?.trim()?.uppercase()) {
            "COMPLETE", "COMPLETED", "FINISHED" -> COMPLETE
            "HIATUS", "ON_HIATUS", "DORMANT" -> HIATUS
            "DEAD", "ABANDONED", "CANCELLED" -> DEAD
            else -> ONGOING
        }
    }
}

@Serializable
enum class FanficThreadmarkCategory(val label: String) {
    MAIN("Chapters / Threadmarks"),
    SIDESTORY("Sidestories"),
    OMAKE("Omakes / Apocrypha"),
    INFO("Informational"),
    MEDIA("Media");

    companion object {
        fun fromRaw(raw: String?): FanficThreadmarkCategory {
            val r = raw.orEmpty().trim().uppercase()
            return when {
                "SIDE" in r || "NON-CANON" in r -> SIDESTORY
                "OMAKE" in r || "APOCRYPHA" in r -> OMAKE
                "INFO" in r || "INDEX" in r || "GLOSSARY" in r || "WORLD" in r -> INFO
                "MEDIA" in r || "ART" in r || "FANART" in r -> MEDIA
                else -> MAIN
            }
        }
    }
}

@Immutable
@Serializable
data class FanficSourceLink(
    val site: String,
    val url: String,
    val externalId: String? = null,
    val isPrimary: Boolean = false,
    val lastChapterCount: Int? = null,
    val lastWordCount: Int? = null,
    val lastCheckedMs: Long = 0L,
    val lastUpdatedLabel: String? = null
)

@Immutable
@Serializable
data class FanficMetaPayload(
    val authorUrl: String? = null,
    val fandoms: List<String> = emptyList(),
    val relationships: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val freeformTags: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val language: String? = null,
    val kudosCount: Int? = null,
    val bookmarksCount: Int? = null,
    val hitsCount: Int? = null,
    val commentsCount: Int? = null,
    val publishedDate: String? = null,
    val updatedDate: String? = null,
    val isArchiveLocked: Boolean = false,
    val mirrorSources: List<FanficSourceLink> = emptyList()
)

private val fanficMetaJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

fun encodeFanficMeta(meta: FanficMetaPayload): String = fanficMetaJson.encodeToString(meta)

fun decodeFanficMeta(jsonStr: String?): FanficMetaPayload {
    if (jsonStr.isNullOrBlank()) return FanficMetaPayload()
    return runCatching { fanficMetaJson.decodeFromString<FanficMetaPayload>(jsonStr) }
        .getOrDefault(FanficMetaPayload())
}

fun formatWordCount(words: Int?): String {
    if (words == null || words <= 0) return ""
    return when {
        words >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM words", words / 1_000_000f)
        words >= 1_000 -> String.format(java.util.Locale.US, "%.1fk words", words / 1_000f).replace(".0k", "k")
        else -> "$words words"
    }
}

fun formatWordCountShort(words: Int?): String {
    if (words == null || words <= 0) return ""
    return when {
        words >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", words / 1_000_000f)
        words >= 1_000 -> String.format(java.util.Locale.US, "%.0fk", words / 1_000f)
        else -> "${words}w"
    }
}

/**
 * Normalizes AO3, FF.net, SpaceBattles, Sufficient Velocity, and QQ URLs to a canonical form
 * and derives a stable work ID key such as `ao3:123456`, `ffn:78910`, `sb:112233`.
 */
fun canonicalizeFanficUrl(rawUrl: String): Triple<FanficSite, String, String?> {
    val trimmed = rawUrl.trim()
    val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed
    } else {
        "https://$trimmed"
    }
    val site = FanficSite.fromUrl(withScheme)
    return when (site) {
        FanficSite.AO3 -> {
            val workId = Regex("""/works/(\d+)""").find(withScheme)?.groupValues?.getOrNull(1)
            val canonical = if (workId != null) "https://archiveofourown.org/works/$workId" else withScheme
            Triple(FanficSite.AO3, canonical, workId?.let { "ao3:$it" })
        }
        FanficSite.FFN -> {
            val storyId = Regex("""/s/(\d+)""").find(withScheme)?.groupValues?.getOrNull(1)
            val canonical = if (storyId != null) "https://www.fanfiction.net/s/$storyId/1/" else withScheme
            Triple(FanficSite.FFN, canonical, storyId?.let { "ffn:$it" })
        }
        FanficSite.SB, FanficSite.SV, FanficSite.QQ -> {
            val threadId = Regex("""/threads/(?:[^/?#]+\.)?(\d+)""").find(withScheme)?.groupValues?.getOrNull(1)
                ?: Regex("""thread=(\d+)""").find(withScheme)?.groupValues?.getOrNull(1)
            val slugAndId = Regex("""/threads/([^/?#]+\.\d+|\d+)""").find(withScheme)?.groupValues?.getOrNull(1)
            val canonical = when {
                slugAndId != null -> "${site.baseUrl}/threads/$slugAndId/"
                threadId != null -> "${site.baseUrl}/threads/$threadId/"
                else -> withScheme.substringBefore("#")
            }
            val prefix = site.code.lowercase()
            Triple(site, canonical, threadId?.let { "$prefix:$it" })
        }
        FanficSite.OTHER -> Triple(FanficSite.OTHER, withScheme, null)
    }
}

private val URL_EXTRACT_REGEX = Regex("""https?://[^\s<>"'\]\)]+""", RegexOption.IGNORE_CASE)

fun extractFirstFanficUrl(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val urls = URL_EXTRACT_REGEX.findAll(text).map { it.value.trimEnd('.', ',', ';', '!', '?') }.toList()
    if (urls.isEmpty()) return null
    return urls.firstOrNull { FanficSite.fromUrl(it) != FanficSite.OTHER } ?: urls.firstOrNull()
}

object FanficTagFilterBus {
    private val _pendingTag = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val pendingTag: kotlinx.coroutines.flow.StateFlow<String?> = _pendingTag

    fun emitTag(tag: String) {
        _pendingTag.value = tag.trim().takeIf { it.isNotBlank() }
    }

    fun consume() {
        _pendingTag.value = null
    }
}
