package com.omnitrack.app.data.artwork

/**
 * Normalizes and upgrades image URLs to full lossless resolution
 * by stripping CDN thumbnail parameters and downscaling suffixes.
 */
object LosslessCoverMapper {

    fun toLosslessUrl(url: String): String {
        var clean = url.trim()

        // Enforce HTTPS across all remote artwork to prevent Android cleartext network blocks
        if (clean.startsWith("http://")) {
            clean = clean.replaceFirst("http://", "https://")
        }

        // MangaDex: strip .512.jpg or .256.jpg
        clean = clean.replace(Regex("\\.(256|512)\\.jpg$"), ".jpg")
            .replace(Regex("\\.(256|512)\\.png$"), ".png")

        // AniList: upgrade /cover/medium/ to /cover/large/ if present
        if (clean.contains("anilist.co") && clean.contains("/cover/medium/")) {
            clean = clean.replace("/cover/medium/", "/cover/large/")
        }

        // TMDB: upgrade w500 or w300 to original
        if (clean.contains("image.tmdb.org/t/p/")) {
            clean = clean.replace(Regex("/t/p/w[0-9]+/"), "/t/p/original/")
        }

        // IGDB: upgrade t_cover_big or t_thumb to t_1080p
        if (clean.contains("images.igdb.com/igdb/image/upload/")) {
            clean = clean.replace(Regex("/t_[a-z0-9_]+/"), "/t_1080p/")
        }

        // Bangumi: upgrade small/medium to large
        if (clean.contains("lain.bgm.tv/pic/cover/")) {
            clean = clean.replace("/pic/cover/c/", "/pic/cover/l/")
                .replace("/pic/cover/m/", "/pic/cover/l/")
                .replace("/pic/cover/s/", "/pic/cover/l/")
        }

        return clean
    }
}
