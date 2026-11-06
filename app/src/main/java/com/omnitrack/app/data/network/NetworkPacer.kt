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

    private val aniListSemaphore = Semaphore(1)
    private var lastAniListCallTime = 0L
    private const val ANILIST_MIN_INTERVAL_MS = 650L // <= 90 req/min

    private val mangaDexSemaphore = Semaphore(1)
    private var lastMangaDexCallTime = 0L
    private const val MANGADEX_MIN_INTERVAL_MS = 220L // <= 5 req/sec

    private val tmdbSemaphore = Semaphore(1)
    private var lastTmdbCallTime = 0L
    private const val TMDB_MIN_INTERVAL_MS = 250L // <= 40 req/10s

    private val steamSemaphore = Semaphore(1)
    private var lastSteamCallTime = 0L
    private const val STEAM_MIN_INTERVAL_MS = 1500L

    private val generalSemaphore = Semaphore(2)

    private val coalescingMutex = Mutex()
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<Any?>>()

    suspend fun <T> withAniListPacing(block: suspend () -> T): T = aniListSemaphore.withPermit {
        val now = System.currentTimeMillis()
        val elapsed = now - lastAniListCallTime
        if (elapsed < ANILIST_MIN_INTERVAL_MS) {
            delay(ANILIST_MIN_INTERVAL_MS - elapsed)
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
        val now = System.currentTimeMillis()
        val elapsed = now - lastTmdbCallTime
        if (elapsed < TMDB_MIN_INTERVAL_MS) {
            delay(TMDB_MIN_INTERVAL_MS - elapsed)
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

    suspend fun <T> withGeneralPacing(block: suspend () -> T): T = generalSemaphore.withPermit {
        block()
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
