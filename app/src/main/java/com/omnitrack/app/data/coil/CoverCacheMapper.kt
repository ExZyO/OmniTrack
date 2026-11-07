package com.omnitrack.app.data.coil

import coil3.map.Mapper
import coil3.request.Options
import com.omnitrack.app.data.cache.CoverCache

/**
 * Maps remote cover URLs to local cached cover files when available in CoverCache.
 * This completely bypasses network dispatch, DNS lookup, and HTTP conditional checks,
 * loading local covers in under 2ms.
 */
class CoverCacheMapper(private val coverCache: CoverCache) : Mapper<String, Any> {
    override fun map(data: String, options: Options): Any? {
        if (!data.startsWith("http://") && !data.startsWith("https://")) return null
        val cachedFile = coverCache.getCoverFile(data)
        return if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
            cachedFile
        } else {
            null
        }
    }
}
