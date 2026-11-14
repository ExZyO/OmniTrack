package com.omnitrack.app.data.network

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Host-level Rate Limiter & Request Coalescer.
 * Enforces strict single-permit semaphores and minimum pacing intervals to avoid HTTP 429 penalties.
 * Coalesces duplicate in-flight requests for identical keys into a shared Deferred.
 */
object NetworkPacer {

    private val aniListSemaphore = Semaphore(4)
    private var lastAniListCallTime = 0L
    private const val ANILIST_MIN_INTERVAL_MS = 50L // Parallel GraphQL queries with fast burst

    private val mangaDexSemaphore = Semaphore(2)
    private var lastMangaDexCallTime = 0L
    private const val MANGADEX_MIN_INTERVAL_MS = 100L

    private val tmdbSemaphore = Semaphore(8) // Parallel HTTP/2 multiplexing (Chimanew / Nuvio parity)
    private var lastTmdbCallTime = 0L
    private const val TMDB_MIN_INTERVAL_MS = 0L // TMDb developer API handles parallel requests without artificial delay

    private val steamSemaphore = Semaphore(1)
    private var lastSteamCallTime = 0L
    private const val STEAM_MIN_INTERVAL_MS = 1500L

    private val bangumiSemaphore = Semaphore(1)
    private var lastBangumiCallTime = 0L
    private const val BANGUMI_MIN_INTERVAL_MS = 500L

    private val vndbSemaphore = Semaphore(1)
    private var lastVndbCallTime = 0L
    private const val VNDB_MIN_INTERVAL_MS = 600L

    private val generalSemaphore = Semaphore(2)

    private val coalescingMutex = Mutex()
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<Any?>>()

    suspend fun <T> withAniListPacing(block: suspend () -> T): T = aniListSemaphore.withPermit {
        if (ANILIST_MIN_INTERVAL_MS > 0L) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastAniListCallTime
            if (elapsed < ANILIST_MIN_INTERVAL_MS) {
                delay(ANILIST_MIN_INTERVAL_MS - elapsed)
            }
        }
        try {
            block()
        } finally {
            lastAniListCallTime = System.currentTimeMillis()
        }
    }

    suspend fun <T> withMangaDexPacing(block: suspend () -> T): T = mangaDexSemaphore.withPermit {
        val now = System.currentTimeMillis()
        val elapsed = now - lastMangaDexCallTime
        if (elapsed < MANGADEX_MIN_INTERVAL_MS) {
            delay(MANGADEX_MIN_INTERVAL_MS - elapsed)
        }
        try {
            block()
        } finally {
            lastMangaDexCallTime = System.currentTimeMillis()
        }
    }

    suspend fun <T> withTmdbPacing(block: suspend () -> T): T = tmdbSemaphore.withPermit {
        if (TMDB_MIN_INTERVAL_MS > 0L) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastTmdbCallTime
            if (elapsed < TMDB_MIN_INTERVAL_MS) {
                delay(TMDB_MIN_INTERVAL_MS - elapsed)
            }
        }
        try {
            block()
        } finally {
            lastTmdbCallTime = System.currentTimeMillis()
        }
    }

    suspend fun <T> withSteamPacing(block: suspend () -> T): T = steamSemaphore.withPermit {
        val now = System.currentTimeMillis()
        val elapsed = now - lastSteamCallTime
        if (elapsed < STEAM_MIN_INTERVAL_MS) {
            delay(STEAM_MIN_INTERVAL_MS - elapsed)
        }
        try {
            block()
        } finally {
            lastSteamCallTime = System.currentTimeMillis()
        }
    }

    suspend fun <T> withBangumiPacing(block: suspend () -> T): T = bangumiSemaphore.withPermit {
        val now = System.currentTimeMillis()
        val elapsed = now - lastBangumiCallTime
        if (elapsed < BANGUMI_MIN_INTERVAL_MS) {
            delay(BANGUMI_MIN_INTERVAL_MS - elapsed)
        }
        try {
            block()
        } finally {
            lastBangumiCallTime = System.currentTimeMillis()
        }
    }

    suspend fun <T> withVndbPacing(block: suspend () -> T): T = vndbSemaphore.withPermit {
        val now = System.currentTimeMillis()
        val elapsed = now - lastVndbCallTime
        if (elapsed < VNDB_MIN_INTERVAL_MS) {
            delay(VNDB_MIN_INTERVAL_MS - elapsed)
        }
        try {
            block()
        } finally {
            lastVndbCallTime = System.currentTimeMillis()
        }
    }

    suspend fun <T> withGeneralPacing(block: suspend () -> T): T = generalSemaphore.withPermit {
        block()
    }

    suspend fun <T> withHostPacing(url: String, block: suspend () -> T): T {
        val lower = url.lowercase()
        return when {
            "anilist" in lower -> withAniListPacing(block)
            "mangadex" in lower -> withMangaDexPacing(block)
            "themoviedb" in lower || "tmdb" in lower -> withTmdbPacing(block)
            "steam" in lower -> withSteamPacing(block)
            "bgm.tv" in lower || "bangumi" in lower -> withBangumiPacing(block)
            "vndb" in lower -> withVndbPacing(block)
            else -> withGeneralPacing(block)
        }
    }

    /**
     * Executes the block or joins an existing in-flight execution for the same cacheKey.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> coalesce(cacheKey: String, block: suspend () -> T): T = coroutineScope {
        val deferred = coalescingMutex.withLock {
            inFlightRequests.getOrPut(cacheKey) {
                async {
                    try {
                        block()
                    } finally {
                        inFlightRequests.remove(cacheKey)
                    }
                }
            }
        }
        deferred.await() as T
    }
}
