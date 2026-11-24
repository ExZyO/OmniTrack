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

        // MangaDex thumbnails append .256.jpg / .512.jpg AFTER the original extension (e.g. foo.jpg.512.jpg or foo.png.512.jpg).
        // Strip only the trailing thumbnail suffix so the original extension is preserved (avoiding .jpg.jpg / .png.jpg 404s).
        clean = clean.replace(
            Regex("(\\.(?:jpe?g|png|webp))\\.(?:256|512)\\.(?:jpe?g|png)$", RegexOption.IGNORE_CASE),
            "$1"
        )

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

        // Kitsu: upgrade small/medium/large to original
        if (clean.contains("kitsu.io")) {
            clean = clean.replace("/large/", "/original/")
                .replace("/medium/", "/original/")
                .replace("/small/", "/original/")
        }

        // Kuaikan Manhua: strip trailing width/format compression suffix
        if (clean.contains("kkmh.com") || clean.contains("kuaikanmanhua.com")) {
            clean = clean.replace(Regex("-w\\d+(\\.w)?$"), "")
        }

        // Webtoons Global: upgrade cropped thumbnails to q90
        if (clean.contains("webtoons") || clean.contains("pstatic.net")) {
            clean = clean.replace(Regex("\\?type=crop\\d+_\\d+"), "?type=q90")
        }

        return clean
    }

    /**
     * Grid-friendly URL normalization: keeps covers sharp for 2-6 column grids
     * without downloading multi-MB 4K `/original/` posters.
     */
    fun toGridThumbnailUrl(url: String): String {
        var clean = url.trim()
        if (clean.startsWith("http://")) {
            clean = clean.replaceFirst("http://", "https://")
        }
        // MangaDex: use balanced .512.jpg instead of uncompressed multi-MB masters
        if (clean.contains("uploads.mangadex.org")) {
            clean = clean.replace(".256.jpg", ".512.jpg")
        }
        // AniList: /large/ is ~460px wide — crisp & lightweight for grids
        if (clean.contains("anilist.co") && clean.contains("/cover/medium/")) {
            clean = clean.replace("/cover/medium/", "/cover/large/")
        }
        // TMDb: w500 is ideal for mobile grid posters vs /original/ (often 2000x3000+)
        if (clean.contains("image.tmdb.org/t/p/")) {
            clean = clean.replace(Regex("/t/p/(?:w(?:92|154|185|300|342)|original)/"), "/t/p/w500/")
        }
        // IGDB: t_cover_big is crisp for grids vs t_1080p
        if (clean.contains("images.igdb.com/igdb/image/upload/")) {
            clean = clean.replace(Regex("/t_(?:thumb|cover_small|micro|1080p)/"), "/t_cover_big/")
        }
        // Bangumi: medium/small -> large
        if (clean.contains("lain.bgm.tv/pic/cover/")) {
            clean = clean.replace("/pic/cover/s/", "/pic/cover/m/")
                .replace("/pic/cover/c/", "/pic/cover/m/")
        }
        // Kitsu: medium/large instead of multi-MB original
        if (clean.contains("kitsu.io")) {
            clean = clean.replace("/small/", "/medium/")
                .replace("/tiny/", "/medium/")
        }
        return clean
    }
}
