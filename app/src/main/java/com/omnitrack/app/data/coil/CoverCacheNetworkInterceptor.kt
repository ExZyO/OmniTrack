package com.omnitrack.app.data.coil

import com.omnitrack.app.data.cache.CoverCache
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Automatically persists downloaded image streams into permanent CoverCache.
 * Uses response.peekBody() so the primary response stream to Coil remains untouched.
 */
class CoverCacheNetworkInterceptor(
    private val coverCache: CoverCache
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val url = request.url.toString()
        if (response.isSuccessful && (url.startsWith("http://") || url.startsWith("https://"))) {
            val contentType = response.header("Content-Type")
            if (contentType == null || contentType.startsWith("image/")) {
                val cacheFile = coverCache.getCoverFile(url)
                if (cacheFile != null && !cacheFile.exists()) {
                    try {
                        val peeked = response.peekBody(25L * 1024 * 1024) // up to 25MB max
                        cacheFile.parentFile?.mkdirs()
                        cacheFile.outputStream().use { out ->
                            peeked.byteStream().copyTo(out)
                        }
                    } catch (_: Exception) {
                        cacheFile.delete()
                    }
                }
            }
        }

        return response
    }
}
