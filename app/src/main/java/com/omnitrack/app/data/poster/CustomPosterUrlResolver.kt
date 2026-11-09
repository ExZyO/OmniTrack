package com.omnitrack.app.data.poster

import com.omnitrack.app.data.db.dto.MediaCardSummary
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import java.util.concurrent.ConcurrentHashMap

/**
 * 1:1 Nuvio Custom Poster URL Resolver:
 * Replaces placeholders in custom poster patterns with actual content IDs.
 *
 * Supported placeholders:
 * - {imdb_id}    - IMDb ID (e.g. "tt0137523")
 * - {tmdb_id}    - TMDB numeric ID (e.g. "1396")
 * - {tvdb_id}    - TVDB numeric ID
 * - {kitsu_id}   - Kitsu numeric ID
 * - {anilist_id} - AniList numeric ID
 * - {mal_id}     - MyAnimeList numeric ID
 * - {id}         - full or numeric ID
 * - {id_type}    - "imdb", "tmdb", "kitsu", "anilist", "mal"
 * - {typed_id}   - RPDB style ("tt0137523" for IMDb, "movie-1396" / "series-1396" for TMDB)
 * - {type}       - "movie" or "series"
 * - {shape}      - "poster", "landscape", or "square"
 *
 * Supports RPDB-family auto-fallback (imdb -> tmdb -> tvdb) for:
 * ratingposterdb.com, aioratings.com, top-posters.com, btttr.cc
 */
object CustomPosterUrlResolver {

    data class ContentIds(
        val id: String,
        val imdbId: String? = null,
        val tmdbId: String? = null,
        val tvdbId: String? = null,
        val kitsuId: String? = null,
        val anilistId: String? = null,
        val malId: String? = null,
        val anidbId: String? = null
    )

    fun resolve(
        pattern: String,
        ids: ContentIds,
        type: String,
        shape: String = "poster"
    ): String? {
        if (pattern.isBlank()) return null
        val prepared = preparedFor(pattern)
        if (prepared.tokens.isEmpty()) return prepared.decoded

        return if (prepared.rpdb) {
            resolveRpdbWithFallback(prepared.decoded, prepared.tokens, ids, type, shape)
        } else {
            fill(prepared.decoded, prepared.tokens, ids, type, shape)
        }
    }

    /**
     * Resolves custom poster URL for a MediaItemEntity.
     * CONTRACT: Manual cover (customCoverPath) or banner (customBannerPath)
     * STRICTLY OVERRIDES and takes priority over any custom poster pattern or default.
     */
    fun resolveForMediaItem(
        item: MediaItemEntity,
        pattern: String?,
        isLandscape: Boolean
    ): String? {
        if (pattern.isNullOrBlank()) return null
        if (isLandscape && !item.customBannerPath.isNullOrBlank()) return null
        if (!isLandscape && !item.customCoverPath.isNullOrBlank()) return null

        val contentType = when (item.mediaType) {
            MediaType.MOVIE -> "movie"
            MediaType.SHOW, MediaType.ANIME -> "series"
            else -> "series"
        }
        val shape = if (isLandscape) "landscape" else "poster"

        val ids = ContentIds(
            id = item.imdbId ?: item.tmdbId?.toString() ?: item.anilistId?.toString() ?: item.id.toString(),
            imdbId = item.imdbId,
            tmdbId = item.tmdbId?.toString(),
            anilistId = item.anilistId?.toString(),
            malId = item.malId?.toString()
        )

        return resolve(pattern, ids, contentType, shape)
    }

    /**
     * Resolves custom poster URL for a MediaCardSummary.
     * CONTRACT: Manual cover/banner strictly overrides.
     */
    fun resolveForSummary(
        item: MediaCardSummary,
        pattern: String?,
        isLandscape: Boolean
    ): String? {
        if (pattern.isNullOrBlank()) return null
        if (isLandscape && !item.customBannerPath.isNullOrBlank()) return null
        if (!isLandscape && !item.customCoverPath.isNullOrBlank()) return null

        val contentType = when (item.mediaType) {
            MediaType.MOVIE -> "movie"
            MediaType.SHOW, MediaType.ANIME -> "series"
            else -> "series"
        }
        val shape = if (isLandscape) "landscape" else "poster"

        val ids = ContentIds(
            id = item.imdbId ?: item.tmdbId?.toString() ?: item.anilistId?.toString() ?: item.id.toString(),
            imdbId = item.imdbId,
            tmdbId = item.tmdbId?.toString(),
            anilistId = item.anilistId?.toString(),
            malId = item.malId?.toString()
        )

        return resolve(pattern, ids, contentType, shape)
    }

    // -- Internal Parser & Cache --

    private class Token(
        val start: Int,
        val end: Int,
        val optional: Boolean,
        val keys: List<String>,
    )

    private class Prepared(
        val decoded: String,
        val tokens: List<Token>,
        val rpdb: Boolean,
    )

    private val preparedCache = ConcurrentHashMap<String, Prepared>()

    private fun preparedFor(pattern: String): Prepared {
        preparedCache[pattern]?.let { return it }
        val decoded = decodePatternPlaceholders(pattern)
        val prepared = Prepared(decoded, scanTokens(decoded), isRpdbFamily(decoded))
        if (preparedCache.size > 32) preparedCache.clear()
        preparedCache[pattern] = prepared
        return prepared
    }

    private fun fill(
        decoded: String,
        tokens: List<Token>,
        ids: ContentIds,
        type: String,
        shape: String,
    ): String? {
        if (tokens.isEmpty()) return decoded
        val (rawId, rawIdType) = extractRawIdAndType(ids)
        val typedId = formatTypedId(rawId, rawIdType, type)
        val url = StringBuilder(decoded.length + 16)
        var cursor = 0
        for (token in tokens) {
            url.append(decoded, cursor, token.start)
            if (!appendToken(url, decoded, token, ids, rawId, rawIdType, typedId, type, shape)) {
                return null
            }
            cursor = token.end
        }
        url.append(decoded, cursor, decoded.length)
        return url.toString()
    }

    private fun appendToken(
        url: StringBuilder,
        decoded: String,
        token: Token,
        ids: ContentIds,
        rawId: String,
        rawIdType: String,
        typedId: String,
        type: String,
        shape: String,
    ): Boolean {
        if (token.keys.size > 1) {
            for (key in token.keys) {
                val value = lookup(key, ids, rawId, rawIdType, typedId, type, shape) ?: continue
                if (value.isNotEmpty()) {
                    url.append(value)
                    return true
                }
            }
            return false
        }
        val value = lookup(token.keys[0], ids, rawId, rawIdType, typedId, type, shape)
        if (value == null) {
            url.append(decoded, token.start, token.end)
            return true
        }
        if (value.isEmpty() && !token.optional) return false
        url.append(value)
        return true
    }

    private fun lookup(
        key: String,
        ids: ContentIds,
        rawId: String,
        rawIdType: String,
        typedId: String,
        type: String,
        shape: String,
    ): String? = when (key) {
        "id" -> ids.id
        "id_type" -> rawIdType
        "typed_id" -> typedId
        "type" -> type
        "shape" -> shape
        "imdb_id" -> ids.imdbId.orEmpty()
        "tmdb_id" -> ids.tmdbId.orEmpty()
        "tvdb_id" -> ids.tvdbId.orEmpty()
        "kitsu_id" -> ids.kitsuId.orEmpty()
        "anilist_id" -> ids.anilistId.orEmpty()
        "mal_id" -> ids.malId.orEmpty()
        "anidb_id" -> ids.anidbId.orEmpty()
        else -> null
    }

    private fun scanTokens(decoded: String): List<Token> {
        if ('{' !in decoded) return emptyList()
        val tokens = ArrayList<Token>(4)
        var index = 0
        while (index < decoded.length) {
            val open = decoded.indexOf('{', index)
            if (open < 0) break
            val close = decoded.indexOf('}', open + 1)
            if (close < 0) break
            val token = parseToken(decoded, open, close)
            if (token != null) {
                tokens += token
                index = close + 1
            } else {
                index = open + 1
            }
        }
        return tokens
    }

    private fun parseToken(decoded: String, open: Int, close: Int): Token? {
        val bodyEnd = close
        if (bodyEnd <= open + 1) return null
        val optional = decoded[bodyEnd - 1] == '?'
        val nameEnd = if (optional) bodyEnd - 1 else bodyEnd
        if (nameEnd <= open + 1) return null
        val keys = ArrayList<String>(1)
        var partStart = open + 1
        for (cursor in partStart..nameEnd) {
            val atEnd = cursor == nameEnd
            val pipe = !atEnd && decoded[cursor] == '|'
            if (!atEnd && !pipe && !isPlaceholderChar(decoded[cursor])) return null
            if (atEnd || pipe) {
                if (cursor == partStart) return null
                keys += decoded.substring(partStart, cursor)
                partStart = cursor + 1
            }
        }
        if (keys.isEmpty()) return null
        return Token(open, close + 1, optional, keys)
    }

    private fun isPlaceholderChar(char: Char): Boolean =
        char in 'a'..'z' || char == '_'

    private fun extractRawIdAndType(ids: ContentIds): Pair<String, String> {
        val metaId = ids.id
        return when {
            metaId.startsWith("tt") -> metaId to "imdb"
            metaId.contains(":") -> {
                val colonIndex = metaId.indexOf(':')
                val prefix = metaId.substring(0, colonIndex)
                val rawId = metaId.substring(colonIndex + 1)
                rawId to prefix
            }
            else -> metaId to "unknown"
        }
    }

    private fun formatTypedId(rawId: String, idType: String, contentType: String): String {
        if (idType == "imdb") return rawId
        if (idType == "tmdb" || idType == "tvdb") {
            val prefix = if (contentType == "movie") "movie" else "series"
            return "$prefix-$rawId"
        }
        return rawId
    }

    // -- RPDB Family Fallback (ratingposterdb.com, btttr.cc, top-posters.com, aioratings.com) --

    private val RPDB_DOMAINS = listOf(
        "ratingposterdb.com",
        "aioratings.com",
        "top-posters.com",
        "btttr.cc"
    )

    private fun isRpdbFamily(pattern: String): Boolean =
        RPDB_DOMAINS.any { domain -> domain in pattern }

    private fun decodePatternPlaceholders(pattern: String): String {
        if ('%' !in pattern) return pattern
        return pattern
            .replace("%7B", "{", ignoreCase = true)
            .replace("%7D", "}", ignoreCase = true)
            .replace("%7C", "|", ignoreCase = true)
    }

    private fun resolveRpdbWithFallback(
        pattern: String,
        tokens: List<Token>,
        ids: ContentIds,
        type: String,
        shape: String
    ): String? {
        val direct = fill(pattern, tokens, ids, type, shape)
        if (direct != null) return direct

        val typePrefix = if (type == "movie") "movie" else "series"
        val fallbacks = mutableListOf<String>()

        if ("{imdb_id}" in pattern) {
            if (!ids.tmdbId.isNullOrBlank()) {
                fallbacks += pattern
                    .replace("/imdb/", "/tmdb/")
                    .replace("{imdb_id}", "$typePrefix-${ids.tmdbId}")
            }
            if (!ids.tvdbId.isNullOrBlank()) {
                fallbacks += pattern
                    .replace("/imdb/", "/tvdb/")
                    .replace("{imdb_id}", "$typePrefix-${ids.tvdbId}")
            }
        } else if ("{tmdb_id}" in pattern || "{type}-{tmdb_id}" in pattern) {
            if (!ids.imdbId.isNullOrBlank()) {
                fallbacks += pattern
                    .replace("/tmdb/", "/imdb/")
                    .replace("$typePrefix-{tmdb_id}", ids.imdbId)
                    .replace("{tmdb_id}", ids.imdbId)
            }
            if (!ids.tvdbId.isNullOrBlank()) {
                fallbacks += pattern
                    .replace("/tmdb/", "/tvdb/")
                    .replace("{tmdb_id}", ids.tvdbId)
            }
        }

        for (fb in fallbacks) {
            val resolved = fill(fb, scanTokens(fb), ids, type, shape)
            if (resolved != null) return resolved
        }

        return null
    }
}
