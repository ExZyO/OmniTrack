package com.omnitrack.app.data.artwork

import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.model.MediaType
import java.io.File

/**
 * Resolves user-selected custom artwork paths/URLs and matches library items
 * so manually chosen covers and banners appear consistently across the app.
 */
object ArtworkModelResolver {

    fun toCoilModel(pathOrUrl: String?): Any? {
        val raw = pathOrUrl?.trim()?.takeIf { it.isNotBlank() && it != "NONE" } ?: return null
        return when {
            raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) ->
                LosslessCoverMapper.toLosslessUrl(raw)
            raw.startsWith("content://", ignoreCase = true) || raw.startsWith("file://", ignoreCase = true) ->
                raw
            else -> File(raw)
        }
    }

    fun preferredCover(summary: MediaCardSummary): String =
        summary.customCoverPath?.takeIf { it.isNotBlank() } ?: summary.coverUrl

    /**
     * Rejects VN gameplay screenshots or Steam store screenshot stills as automatic
     * hero/Discover banners. Manual custom banners explicitly chosen by the user bypass this.
     */
    fun isAutoScreenshotUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.contains("t.vndb.org/sf/") ||
            lower.contains("t.vndb.org/st/") ||
            (lower.contains("steamstatic.com") && lower.contains("ss_"))
    }

    fun isBannerDisabled(summary: MediaCardSummary): Boolean =
        summary.customBannerPath == "NONE" || summary.artworkRole == "DISABLE_BANNER"

    fun preferredBanner(summary: MediaCardSummary): String? {
        if (isBannerDisabled(summary)) return null
        return summary.customBannerPath?.takeIf { it.isNotBlank() }
            ?: when (summary.artworkRole) {
                "COVER_AS_BANNER" -> summary.coverUrl.takeIf { it.isNotBlank() }
                else -> summary.bannerUrl?.takeIf {
                    it.isNotBlank() && it != summary.coverUrl && !isAutoScreenshotUrl(it)
                }
            }
    }

    fun findLibraryMatch(
        library: List<MediaCardSummary>,
        mediaType: MediaType? = null,
        anilistId: Long? = null,
        malId: Long? = null,
        tmdbId: Long? = null,
        imdbId: String? = null,
        vndbId: String? = null,
        steamAppId: Long? = null,
        comicVineId: Long? = null,
        metronId: Long? = null,
        openLibraryId: String? = null,
        fanficWorkId: String? = null,
        fanficSourceUrl: String? = null,
        title: String? = null,
        nativeTitle: String? = null
    ): MediaCardSummary? {
        val cleanImdb = imdbId?.trim()?.takeIf { it.isNotBlank() }
        val cleanVndb = vndbId?.trim()?.takeIf { it.isNotBlank() }
        val cleanOpenLib = openLibraryId?.trim()?.takeIf { it.isNotBlank() }
        val cleanFanficWork = fanficWorkId?.trim()?.takeIf { it.isNotBlank() }
        val cleanFanficUrl = fanficSourceUrl?.trim()?.takeIf { it.isNotBlank() }
        val cleanTitle = title?.trim()?.takeIf { it.isNotBlank() }
        val cleanNative = nativeTitle?.trim()?.takeIf { it.isNotBlank() }

        return library.firstOrNull { summary ->
            val typeMatches = mediaType == null || mediaType == MediaType.ALL || summary.mediaType == mediaType
            typeMatches && (
                (cleanFanficWork != null && summary.fanficWorkId == cleanFanficWork) ||
                    (cleanFanficUrl != null && summary.fanficSourceUrl == cleanFanficUrl) ||
                    (anilistId != null && summary.anilistId == anilistId) ||
                    (malId != null && summary.malId == malId) ||
                    (tmdbId != null && summary.tmdbId == tmdbId) ||
                    (cleanImdb != null && summary.imdbId?.trim()?.equals(cleanImdb, ignoreCase = true) == true) ||
                    (cleanVndb != null && summary.vndbId?.trim()?.equals(cleanVndb, ignoreCase = true) == true) ||
                    (steamAppId != null && summary.steamAppId == steamAppId) ||
                    (comicVineId != null && summary.comicVineId == comicVineId) ||
                    (metronId != null && summary.metronId == metronId) ||
                    (cleanOpenLib != null && summary.openLibraryId?.trim()?.equals(cleanOpenLib, ignoreCase = true) == true) ||
                    (cleanTitle != null && summary.title.trim().equals(cleanTitle, ignoreCase = true)) ||
                    (cleanNative != null && summary.nativeTitle?.trim()?.equals(cleanNative, ignoreCase = true) == true)
                )
        }
    }
}
