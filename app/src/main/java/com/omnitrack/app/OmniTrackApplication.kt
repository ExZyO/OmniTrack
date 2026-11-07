package com.omnitrack.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.omnitrack.app.data.cache.CoverCache
import com.omnitrack.app.data.coil.CoverCacheMapper
import com.omnitrack.app.data.db.OmniTrackDatabase
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.util.AppLogger
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class OmniTrackApplication : Application(), SingletonImageLoader.Factory {

    val database by lazy { OmniTrackDatabase.getDatabase(this) }
    val repository by lazy { MediaRepository(database.mediaDao(), database.categoryDao()) }
    val coverCache by lazy { CoverCache(this) }

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        com.omnitrack.app.updater.AppUpdateNotifier.createNotificationChannel(this)
        AppLogger.i("Application", "OmniTrack launched successfully.")
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val httpCacheDir = File(cacheDir, "http_cache")
        val okHttpClient = OkHttpClient.Builder()
            .cache(Cache(httpCacheDir, 100L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(CoverCacheMapper(coverCache))
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
