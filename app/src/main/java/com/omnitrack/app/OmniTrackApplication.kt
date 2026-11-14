package com.omnitrack.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import com.omnitrack.app.data.ai.AiQueryEngine
import com.omnitrack.app.data.cache.CoverCache
import com.omnitrack.app.data.coil.CoverCacheMapper
import com.omnitrack.app.data.db.OmniTrackDatabase
import com.omnitrack.app.data.enrichment.EnrichmentRepository
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.data.search.OmniSearchRepository
import com.omnitrack.app.data.sync.TrackerAccountVerifier
import com.omnitrack.app.data.sync.TrackerSyncEngine
import com.omnitrack.app.data.sync.TrackerTokenManager
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class OmniTrackApplication : Application(), SingletonImageLoader.Factory {

    val database by lazy { OmniTrackDatabase.getDatabase(this) }
    val repository by lazy { MediaRepository(database.mediaDao(), database.categoryDao(), database.syncQueueDao(), database.episodeProgressDao()) }
    val coverCache by lazy { CoverCache(this) }
    val preferenceManager by lazy { PreferenceManager(this) }
    val omniSearchRepository by lazy { OmniSearchRepository() }
    val enrichmentRepository by lazy { EnrichmentRepository() }
    val aiQueryEngine by lazy { AiQueryEngine() }
    val trackerTokenManager by lazy { TrackerTokenManager(this) }
    val trackerSyncEngine by lazy { TrackerSyncEngine(repository, database.syncQueueDao(), trackerTokenManager) }

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        com.omnitrack.app.data.network.NetworkClient.getClient(this)
        com.omnitrack.app.updater.AppUpdateNotifier.createNotificationChannel(this)

        val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        appScope.launch {
            launch { preferenceManager.customTraktClientId.collect { TrackerAccountVerifier.customTraktClientId = it } }
            launch { preferenceManager.customTraktClientSecret.collect { TrackerAccountVerifier.customTraktClientSecret = it } }
            launch { preferenceManager.customMalClientId.collect { TrackerAccountVerifier.customMalClientId = it } }
            launch { preferenceManager.customSimklClientId.collect { TrackerAccountVerifier.customSimklClientId = it } }
            launch { preferenceManager.customSimklClientSecret.collect { TrackerAccountVerifier.customSimklClientSecret = it } }
        }

        AppLogger.i("Application", "OmniTrack launched successfully.")
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val sharedClient = com.omnitrack.app.data.network.NetworkClient.getClient(this)

        return ImageLoader.Builder(context)
            .components {
                add(CoverCacheMapper(coverCache))
                add(OkHttpNetworkFetcherFactory(callFactory = { sharedClient }))
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
            .allowRgb565(true)
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(16))
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(6))
            .build()
    }
}
