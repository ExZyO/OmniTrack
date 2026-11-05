package com.omnitrack.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.omnitrack.app.data.db.OmniTrackDatabase
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.util.AppLogger
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class OmniTrackApplication : Application(), SingletonImageLoader.Factory {

    val database by lazy { OmniTrackDatabase.getDatabase(this) }
    val repository by lazy { MediaRepository(database.mediaDao(), database.categoryDao()) }

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        com.omnitrack.app.updater.AppUpdateNotifier.createNotificationChannel(this)
        AppLogger.i("Application", "OmniTrack launched successfully.")
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
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
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .build()
    }
}
