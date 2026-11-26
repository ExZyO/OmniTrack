package com.omnitrack.app.data.backup

import android.content.Context
import com.omnitrack.app.data.db.OmniTrackDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LibraryRepairReport(
    val totalMediaCount: Int = 0,
    val protectedCustomEntries: Int = 0,
    val orphanedSubEntriesCount: Int = 0,
    val brokenLocalArtworkPathsCount: Int = 0,
    val staleCrossMediumGroupsCount: Int = 0,
    val sqliteIntegrityOk: Boolean = true,
    val sqliteIntegrityDetail: String = "ok"
) {
    val totalFixableIssues: Int
        get() = orphanedSubEntriesCount + brokenLocalArtworkPathsCount + staleCrossMediumGroupsCount
}

data class LibraryRepairOutcome(
    val removedOrphans: Int,
    val clearedBrokenArtworkPaths: Int,
    val cleanedSingletonGroups: Int,
    val postScan: LibraryRepairReport
)

object LibraryRepairEngine {

    private fun isMissingLocalPath(path: String?): Boolean {
        val raw = path?.trim()?.takeIf { it.isNotBlank() && it != "NONE" } ?: return false
        if (raw.startsWith("http://", true) || raw.startsWith("https://", true) || raw.startsWith("content://", true)) {
            return false
        }
        val filePath = if (raw.startsWith("file://", true)) raw.removePrefix("file://") else raw
        return !File(filePath).exists()
    }

    suspend fun scan(
        @Suppress("UNUSED_PARAMETER") context: Context,
        database: OmniTrackDatabase
    ): LibraryRepairReport = withContext(Dispatchers.IO) {
        val allMedia = database.mediaDao().getAllMediaOnce()
        val protectedCustom = allMedia.count { it.isCustomEntry }

        val orphans = runCatching {
            database.episodeProgressDao().countOrphaned() +
                database.vnRouteDao().countOrphaned() +
                database.gameEntryDao().countOrphaned() +
                database.bookEntryDao().countOrphaned() +
                database.bookQuoteDao().countOrphaned() +
                database.comicIssueDao().countOrphaned() +
                database.fanficChapterDao().countOrphaned() +
                database.seasonMetaDao().countOrphaned()
        }.getOrDefault(0)

        val brokenArtwork = allMedia.count { item ->
            isMissingLocalPath(item.customCoverPath) || isMissingLocalPath(item.customBannerPath)
        }

        val groupCounts = allMedia
            .mapNotNull { it.crossMediumGroupId?.takeIf { g -> g.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
        val staleGroups = groupCounts.count { (_, cnt) -> cnt <= 1 }

        var integrityOk = true
        var integrityDetail = "ok"
        runCatching {
            database.openHelper.readableDatabase.query("PRAGMA quick_check").use { cursor ->
                if (cursor.moveToFirst()) {
                    val res = cursor.getString(0)?.trim().orEmpty()
                    integrityDetail = res.ifBlank { "ok" }
                    integrityOk = integrityDetail.equals("ok", ignoreCase = true)
                }
            }
        }

        LibraryRepairReport(
            totalMediaCount = allMedia.size,
            protectedCustomEntries = protectedCustom,
            orphanedSubEntriesCount = orphans,
            brokenLocalArtworkPathsCount = brokenArtwork,
            staleCrossMediumGroupsCount = staleGroups,
            sqliteIntegrityOk = integrityOk,
            sqliteIntegrityDetail = integrityDetail
        )
    }

    suspend fun runSafeRepair(
        context: Context,
        database: OmniTrackDatabase
    ): LibraryRepairOutcome = withContext(Dispatchers.IO) {
        val removedOrphans = runCatching {
            database.episodeProgressDao().deleteOrphaned() +
                database.vnRouteDao().deleteOrphaned() +
                database.gameEntryDao().deleteOrphaned() +
                database.bookEntryDao().deleteOrphaned() +
                database.bookQuoteDao().deleteOrphaned() +
                database.comicIssueDao().deleteOrphaned() +
                database.fanficChapterDao().deleteOrphaned() +
                database.seasonMetaDao().deleteOrphaned()
        }.getOrDefault(0)

        val allMedia = database.mediaDao().getAllMediaOnce()
        val groupCounts = allMedia
            .mapNotNull { it.crossMediumGroupId?.takeIf { g -> g.isNotBlank() } }
            .groupingBy { it }
            .eachCount()

        var clearedArtCount = 0
        var cleanedGroupCount = 0

        for (item in allMedia) {
            val coverBroken = isMissingLocalPath(item.customCoverPath)
            val bannerBroken = isMissingLocalPath(item.customBannerPath)
            val singletonGroup = item.crossMediumGroupId?.let { (groupCounts[it] ?: 0) <= 1 } ?: false
            if (coverBroken || bannerBroken || singletonGroup) {
                database.mediaDao().updateMedia(
                    item.copy(
                        customCoverPath = if (coverBroken) null else item.customCoverPath,
                        customBannerPath = if (bannerBroken) null else item.customBannerPath,
                        crossMediumGroupId = if (singletonGroup) null else item.crossMediumGroupId
                    )
                )
                if (coverBroken || bannerBroken) clearedArtCount += 1
                if (singletonGroup) cleanedGroupCount += 1
            }
        }

        runCatching {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        }

        val post = scan(context, database)
        LibraryRepairOutcome(
            removedOrphans = removedOrphans,
            clearedBrokenArtworkPaths = clearedArtCount,
            cleanedSingletonGroups = cleanedGroupCount,
            postScan = post
        )
    }
}
