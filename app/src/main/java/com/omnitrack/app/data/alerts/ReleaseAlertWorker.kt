package com.omnitrack.app.data.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.enrichment.ReleaseCalendarRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

object ReleaseAlertScheduler {
    private const val UNIQUE_WORK_NAME = "omnitrack_release_alerts_periodic"

    fun syncSchedule(context: Context, enabled: Boolean) {
        runCatching {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE_WORK_NAME)
            } else {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val req = PeriodicWorkRequestBuilder<ReleaseAlertWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()
                wm.enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    req
                )
            }
        }
    }
}

class ReleaseAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val app = applicationContext as? OmniTrackApplication ?: return@runCatching Result.success()
            val settings = app.preferenceManager.releaseAlertSettings.first()
            if (!settings.enabled) return@runCatching Result.success()
            val hasDestination = settings.discordWebhookUrl.isNotBlank() ||
                (settings.telegramBotToken.isNotBlank() && settings.telegramChatId.isNotBlank())
            if (!hasDestination) return@runCatching Result.success()

            val library = app.repository.getAllMedia().first()
            if (library.isEmpty()) return@runCatching Result.success()

            val schedule = ReleaseCalendarRepository().fetchWeeklyAiringSchedule(force = false, daysAhead = 14)
            val nowSec = System.currentTimeMillis() / 1000L
            val windowSec = 6L * 3600L

            val dueReleases = schedule.filter { rel ->
                val matchesLibrary = library.any { lib ->
                    (rel.anilistId != null && lib.anilistId == rel.anilistId) ||
                        (rel.malId != null && lib.malId == rel.malId) ||
                        (rel.tmdbId != null && lib.tmdbId == rel.tmdbId && lib.mediaType == rel.mediaType) ||
                        (rel.steamAppId != null && lib.steamAppId == rel.steamAppId) ||
                        (rel.vndbId != null && lib.vndbId == rel.vndbId) ||
                        (rel.mediaType == lib.mediaType && lib.title.equals(rel.title, ignoreCase = true))
                }
                if (!matchesLibrary) return@filter false
                val delta = kotlin.math.abs(rel.airingAtEpochSec - nowSec)
                val key = "${rel.id}_${rel.episodeNumber ?: 0}_${rel.airingAtEpochSec / 3600L}"
                delta <= windowSec && key !in settings.notifiedReleaseKeys
            }

            if (dueReleases.isNotEmpty()) {
                val sent = ReleaseAlertDispatcher.dispatchReleaseAlerts(settings, dueReleases.take(10))
                if (sent > 0) {
                    val sentKeys = dueReleases.take(10).map {
                        "${it.id}_${it.episodeNumber ?: 0}_${it.airingAtEpochSec / 3600L}"
                    }.toSet()
                    app.preferenceManager.recordNotifiedReleaseKeys(sentKeys)
                }
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
