package com.omnitrack.app.data.network

import android.content.Context
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shared High-Capacity OkHttpClient inspired by Chimanew / EaZy Nuvio+.
 *
 * 1. ConnectionPool(30, 5, TimeUnit.MINUTES): Retains 30 open HTTP/2 sockets for 5 minutes,
 *    eliminating TLS/TCP handshake latency on repeated TMDb, AniList, and Cinemeta requests.
 * 2. Dispatcher(maxRequests = 64, maxRequestsPerHost = 20): Enables parallel multiplexed
 *    queries without queue bottlenecking.
 * 3. 64 MiB Disk Cache: Instant local cache retrieval for HTTP-cached endpoints.
 */
object NetworkClient {

    @Volatile
    private var instance: OkHttpClient? = null

    val sharedClient: OkHttpClient
        get() = getClient()

    fun getClient(context: Context? = null): OkHttpClient {
        return instance ?: synchronized(this) {
            instance ?: run {
                val cacheDir = context?.cacheDir?.let { File(it, "network_cache") }
                val cache = cacheDir?.let { Cache(it, 64L * 1024 * 1024) } // 64 MiB
                val builder = OkHttpClient.Builder()
                    .dispatcher(
                        Dispatcher().apply {
                            maxRequests = 64
                            maxRequestsPerHost = 20
                        }
                    )
                    .connectionPool(ConnectionPool(30, 5, TimeUnit.MINUTES))
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", "OmniTrack/1.0 (Android; Linux; S24 Ultra)")
                            .build()
                        chain.proceed(request)
                    }

                if (cache != null) {
                    builder.cache(cache)
                }

                builder.build().also { instance = it }
            }
        }
    }
}
