package com.omnitrack.app.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.roundToInt

@Serializable
enum class GameEntryType(val label: String) {
    BASE_GAME("Base Game"),
    EXPANSION("Expansion"),
    DLC("DLC"),
    PLAYTHROUGH("Playthrough"),
    MILESTONE("Milestone");

    companion object {
        fun fromName(raw: String?): GameEntryType = when (raw?.uppercase()) {
            "BASE_GAME" -> BASE_GAME
            "EXPANSION" -> EXPANSION
            "DLC" -> DLC
            "MILESTONE" -> MILESTONE
            else -> PLAYTHROUGH
        }
    }
}

@Serializable
enum class GameCompletionTier(val label: String) {
    NONE("Not Set"),
    MAIN("Main Story"),
    MAIN_PLUS("Main + Sides"),
    COMPLETIONIST("100%"),
    PLATINUM("Platinum"),
    ENDLESS("Endless");

    companion object {
        fun fromName(raw: String?): GameCompletionTier = when (raw?.uppercase()) {
            "MAIN" -> MAIN
            "MAIN_PLUS" -> MAIN_PLUS
            "COMPLETIONIST" -> COMPLETIONIST
            "PLATINUM" -> PLATINUM
            "ENDLESS" -> ENDLESS
            else -> NONE
        }
    }
}

@Immutable
@Serializable
data class GameHltbTimes(
    val hastilySeconds: Int? = null,
    val normallySeconds: Int? = null,
    val completelySeconds: Int? = null,
    val count: Int? = null
) {
    val mainHours: Double?
        get() = hastilySeconds?.takeIf { it > 0 }?.let { ((it / 360.0).roundToInt() / 10.0) }

    val extraHours: Double?
        get() = normallySeconds?.takeIf { it > 0 }?.let { ((it / 360.0).roundToInt() / 10.0) }

    val completeHours: Double?
        get() = completelySeconds?.takeIf { it > 0 }?.let { ((it / 360.0).roundToInt() / 10.0) }

    val mainMinutes: Int?
        get() = hastilySeconds?.takeIf { it > 0 }?.let { (it / 60).coerceAtLeast(1) }

    val hasAny: Boolean
        get() = mainHours != null || extraHours != null || completeHours != null
}

@Immutable
@Serializable
data class GameAchievementItem(
    val apiName: String,
    val displayName: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val iconGrayUrl: String? = null,
    val isUnlocked: Boolean = false,
    val unlockTimeSec: Long? = null,
    val isHidden: Boolean = false,
    val globalRarityPct: Float? = null
)

@Immutable
@Serializable
data class GameMediaAsset(
    val url: String,
    val thumbnailUrl: String? = null,
    val label: String? = null,
    val isScreenshot: Boolean = false
)

@Immutable
data class GameDetailMeta(
    val igdbId: Long? = null,
    val steamAppId: Long? = null,
    val igdbUserRating: Float? = null,
    val igdbUserCount: Int? = null,
    val igdbCriticRating: Int? = null,
    val igdbCriticCount: Int? = null,
    val metacriticScore: Int? = null,
    val steamPositivePct: Int? = null,
    val steamReviewDesc: String? = null,
    val bangumiScore: Float? = null,
    val storyline: String? = null,
    val hltb: GameHltbTimes = GameHltbTimes(),
    val steamDeckCategory: Int? = null,
    val protonDbTier: String? = null,
    val protonDbTotal: Int? = null,
    val gameEngine: String? = null,
    val perspectives: List<String> = emptyList(),
    val gameModes: List<String> = emptyList(),
    val ageRatings: List<String> = emptyList(),
    val supportedPlatforms: List<String> = emptyList(),
    val mediaGallery: List<GameMediaAsset> = emptyList(),
    val achievements: List<GameAchievementItem> = emptyList(),
    val achievementsUnlocked: Int? = null,
    val achievementsTotal: Int? = null,
    val steamSyncedAtMs: Long? = null,
    val expansionSeeds: List<Pair<String, Long?>> = emptyList()
)

fun formatSteamDeckLabel(category: Int?): String? = when (category) {
    3 -> "Steam Deck Verified"
    2 -> "Steam Deck Playable"
    1 -> "Steam Deck Unsupported"
    else -> null
}

fun formatProtonDbLabel(tier: String?): String? = when (tier?.trim()?.lowercase()) {
    "platinum" -> "ProtonDB Platinum"
    "gold" -> "ProtonDB Gold"
    "silver" -> "ProtonDB Silver"
    "bronze" -> "ProtonDB Bronze"
    "borked" -> "ProtonDB Borked"
    "native" -> "Linux Native"
    else -> tier?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }?.let { "ProtonDB $it" }
}

/**
 * Normalizes stored platform strings into canonical Library/Discover filter keys:
 * PC, PS, SWI, XBOX, DECK, MOBILE, RETRO.
 */
fun normalizeGamePlatformGroup(platformsCsv: String?, fallbackPlatform: String?): Set<String> {
    val combined = listOfNotNull(platformsCsv, fallbackPlatform).joinToString(",")
    if (combined.isBlank()) return emptySet()
    val groups = LinkedHashSet<String>()
    combined.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { token ->
        val low = token.lowercase()
        when {
            "deck" in low -> groups.add("DECK")
            "pc" in low || "win" in low || "mac" in low || "linux" in low || "steam" == low -> groups.add("PC")
            "ps" in low || "playstation" in low || "vita" in low || "psp" in low -> groups.add("PS")
            "switch" in low || "swi" in low || "wii" in low || "3ds" in low || "ds" in low || "nintendo" in low -> {
                if ("switch" in low || "swi" in low) groups.add("SWI") else groups.add("RETRO")
            }
            "xbox" in low || "xb" in low -> groups.add("XBOX")
            "android" in low || "ios" in low || "mobile" in low -> groups.add("MOBILE")
            "retro" in low || "snes" in low || "nes" in low || "gba" in low || "gc" in low || "n64" in low ||
                "dreamcast" in low || "genesis" in low || "saturn" in low || "arcade" in low -> groups.add("RETRO")
        }
    }
    return groups
}

private fun shortPlatformCode(platformsCsv: String?, fallbackPlatform: String?): String? {
    val firstToken = platformsCsv?.split(",")?.map { it.trim() }?.firstOrNull { it.isNotBlank() }
        ?: fallbackPlatform?.trim()?.takeIf { it.isNotBlank() }
        ?: return null
    val low = firstToken.lowercase()
    return when {
        "deck" in low -> "DECK"
        "ps5" in low -> "PS5"
        "ps4" in low -> "PS4"
        "playstation" in low || low == "ps" -> "PS"
        "switch" in low || low == "swi" -> "SWI"
        "xbox" in low -> "XBOX"
        "pc" in low || "win" in low || "linux" in low || "mac" in low -> "PC"
        "android" in low || "ios" in low || "mobile" in low -> "MOB"
        else -> firstToken.uppercase().take(5)
    }
}

fun formatGameCardBadge(
    platformsCsv: String?,
    fallbackPlatform: String?,
    playtimeHours: Double,
    runtimeMinutes: Int?
): String {
    val plat = shortPlatformCode(platformsCsv, fallbackPlatform)
    val hrsLabel = when {
        playtimeHours > 0.0 -> String.format(Locale.US, "%.1fh", playtimeHours)
        runtimeMinutes != null && runtimeMinutes > 0 -> {
            val h = runtimeMinutes / 60.0
            if (h >= 1.0) "~${h.roundToInt()}h" else "~${runtimeMinutes}m"
        }
        else -> null
    }
    return listOfNotNull(plat ?: "GAME", hrsLabel).joinToString(" · ")
}
