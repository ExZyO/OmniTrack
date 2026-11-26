package com.omnitrack.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
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
import com.omnitrack.app.ui.theme.OmniThemeState
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class OmniTrackApplication : Application(), SingletonImageLoader.Factory {

    companion object {
        lateinit var instance: OmniTrackApplication
            private set
    }

    val database by lazy { OmniTrackDatabase.getDatabase(this) }
    val repository by lazy { MediaRepository(database.mediaDao(), database.categoryDao(), database.syncQueueDao(), database.episodeProgressDao(), database.vnRouteDao(), database.gameEntryDao(), database.bookEntryDao(), database.bookQuoteDao(), database.comicIssueDao(), database.fanficChapterDao(), database.seasonMetaDao()) }
    val coverCache by lazy { CoverCache(this) }
    val preferenceManager by lazy { PreferenceManager(this) }
    val omniSearchRepository by lazy { OmniSearchRepository() }
    val enrichmentRepository by lazy { EnrichmentRepository() }
    val aiQueryEngine by lazy { AiQueryEngine() }
    val trackerTokenManager by lazy { TrackerTokenManager(this) }
    val trackerSyncEngine by lazy { TrackerSyncEngine(repository, database.syncQueueDao(), trackerTokenManager) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLogger.init(this)

        // Chimanew / Tachiyomi Startup Preloading: Warm up Dispatchers.Main service loader ahead of time
        kotlin.concurrent.thread(name = "MainDispatcherPreload") { Dispatchers.Main }

        com.omnitrack.app.updater.AppUpdateNotifier.createNotificationChannel(this)

        val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        appScope.launch {
            // Asynchronously warm up SQLite database engine and Network client off the main thread
            database.openHelper.writableDatabase
            com.omnitrack.app.data.network.NetworkClient.getClient(this@OmniTrackApplication)
            runCatching { trackerTokenManager.migrateLegacyTokensToKeystore() }

            launch { preferenceManager.customTraktClientId.collect { TrackerAccountVerifier.customTraktClientId = it } }
            launch { preferenceManager.customTraktClientSecret.collect { TrackerAccountVerifier.customTraktClientSecret = it } }
            launch { preferenceManager.customMalClientId.collect { TrackerAccountVerifier.customMalClientId = it } }
            launch { preferenceManager.customSimklClientId.collect { TrackerAccountVerifier.customSimklClientId = it } }
            launch { preferenceManager.customSimklClientSecret.collect { TrackerAccountVerifier.customSimklClientSecret = it } }
            launch { preferenceManager.vnSpoilerLevel.collect { PreferenceManager.cachedVnSpoilerLevel = it } }
            launch { preferenceManager.vnNsfwFilterMode.collect { PreferenceManager.cachedVnNsfwFilterMode = it } }
            launch { preferenceManager.fanficNsfwFilterMode.collect { PreferenceManager.cachedFanficNsfwFilterMode = it } }
            launch { preferenceManager.tmdbApiKey.collect { PreferenceManager.cachedTmdbApiKey = it } }
            launch { preferenceManager.mdblistApiKey.collect { PreferenceManager.cachedMdblistApiKey = it } }
            launch { preferenceManager.omdbApiKey.collect { PreferenceManager.cachedOmdbApiKey = it } }
            launch { preferenceManager.steamGridDbApiKey.collect { PreferenceManager.cachedSteamGridDbApiKey = it } }
            launch { preferenceManager.igdbClientId.collect { PreferenceManager.cachedIgdbClientId = it } }
            launch { preferenceManager.igdbClientSecret.collect { PreferenceManager.cachedIgdbClientSecret = it } }
            launch { preferenceManager.comicVineApiKey.collect { PreferenceManager.cachedComicVineApiKey = it } }
            launch { preferenceManager.metronUsername.collect { PreferenceManager.cachedMetronUsername = it } }
            launch { preferenceManager.metronPassword.collect { PreferenceManager.cachedMetronPassword = it } }
            launch { preferenceManager.presentationSettings.collect { } }
            launch { preferenceManager.homeHeroSettings.collect { } }
            launch { preferenceManager.homeRowOrder.collect { } }
            launch { preferenceManager.performanceSettings.collect { } }
            launch { preferenceManager.mediaCategoryPrefs.collect { } }
            launch { preferenceManager.chimanewLibrarySettings.collect { } }
            launch { preferenceManager.watchProviderRegion.collect { } }
            launch {
                preferenceManager.releaseAlertSettings.collect { alertCfg ->
                    com.omnitrack.app.data.alerts.ReleaseAlertScheduler.syncSchedule(
                        this@OmniTrackApplication,
                        alertCfg.enabled
                    )
                }
            }
            launch { trackerTokenManager.tokens.collect { TrackerTokenManager.cachedHardcoverToken = it.hardcoverToken.orEmpty() } }
            launch {
                preferenceManager.themeSettings.collect { themeCfg ->
                    withContext(Dispatchers.Main.immediate) {
                        OmniThemeState.apply(themeCfg)
                    }
                }
            }
        }

        AppLogger.i("Application", "OmniTrack launched successfully.")
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val sharedClient = com.omnitrack.app.data.network.NetworkClient.getClient(this)

        return ImageLoader.Builder(context)
            .components {
                add(CoverCacheMapper(coverCache))
                add(OkHttpNetworkFetcherFactory(callFactory = { sharedClient }))
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil3.gif.AnimatedImageDecoder.Factory())
                } else {
                    add(coil3.gif.GifDecoder.Factory())
                }
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
            .allowHardware(true)
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(12))
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(4))
            .build()
    }
}
