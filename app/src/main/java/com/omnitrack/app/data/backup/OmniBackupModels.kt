package com.omnitrack.app.data.backup

import com.omnitrack.app.data.db.entity.BookEntryEntity
import com.omnitrack.app.data.db.entity.BookQuoteEntity
import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.db.entity.ComicIssueEntity
import com.omnitrack.app.data.db.entity.EpisodeProgressEntity
import com.omnitrack.app.data.db.entity.GameEntryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.SyncQueueEntity
import com.omnitrack.app.data.db.entity.VnRouteEntity
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class BackupModuleOptions(
    val includeLibrary: Boolean = true,
    val includeSubEntries: Boolean = true,
    val includeSettings: Boolean = true,
    val includeCredentials: Boolean = false,
    val includeCustomArtwork: Boolean = true,
    val includeCachedCovers: Boolean = false
)

data class BackupInspectionResult(
    val valid: Boolean,
    val errorMessage: String? = null,
    val manifestVersion: Int = 1,
    val appVersion: String = "",
    val device: String = "",
    val createdAt: Long = 0L,
    val mediaCount: Int = 0,
    val categoryCount: Int = 0,
    val episodeProgressCount: Int = 0,
    val comicIssueCount: Int = 0,
    val subEntriesCount: Int = 0,
    val hasDatabase: Boolean = false,
    val hasSettings: Boolean = false,
    val settingsCount: Int = 0,
    val hasCredentials: Boolean = false,
    val credentialsCount: Int = 0,
    val credentialsRequirePassword: Boolean = false,
    val customArtworkCount: Int = 0,
    val cachedArtworkCount: Int = 0
)

@Serializable
data class OmniBackupManifest(
    val version: Int = 2,
    val appVersion: String,
    val versionCode: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val device: String,
    val mediaCount: Int = 0,
    val categoryCount: Int = 0,
    val databaseChecksumSha256: String = "",
    val episodeProgressCount: Int = 0,
    val comicIssueCount: Int = 0,
    val subEntriesCount: Int = 0,
    val settingsCount: Int = 0,
    val credentialsCount: Int = 0,
    val customArtworkCount: Int = 0,
    val cachedArtworkCount: Int = 0,
    val includedModules: BackupModuleOptions? = null
)

@Serializable
data class OmniDatabaseDump(
    val mediaItems: List<MediaItemEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val syncQueue: List<SyncQueueEntity> = emptyList(),
    val vnRoutes: List<VnRouteEntity> = emptyList(),
    val gameEntries: List<GameEntryEntity> = emptyList(),
    val bookEntries: List<BookEntryEntity> = emptyList(),
    val bookQuotes: List<BookQuoteEntity> = emptyList(),
    val episodeProgress: List<EpisodeProgressEntity> = emptyList(),
    val comicIssues: List<ComicIssueEntity> = emptyList()
)

@Serializable
data class OmniPrefEntry(
    val key: String,
    val type: String, // "INT", "FLOAT", "BOOLEAN", "STRING", "LONG", "STRING_SET"
    val value: String = "",
    val stringSet: List<String>? = null
)

@Serializable
data class OmniPreferencesDump(
    val entries: Map<String, String> = emptyMap(),
    val typedEntries: List<OmniPrefEntry> = emptyList()
)

@Serializable
data class OmniTrackerTokensDump(
    val formatVersion: Int = 1,
    val encryptedPayload: String? = null,
    val encryptedTokens: Map<String, String> = emptyMap()
)

data class BackupResult(
    val success: Boolean,
    val backupFile: File? = null,
    val mediaCount: Int = 0,
    val errorMessage: String? = null
)

data class RestoreResult(
    val success: Boolean,
    val mediaCount: Int = 0,
    val categoryCount: Int = 0,
    val errorMessage: String? = null,
    val episodeProgressCount: Int = 0,
    val comicIssueCount: Int = 0,
    val restoredSettings: Boolean = false,
    val restoredCredentials: Boolean = false,
    val restoredArtworkCount: Int = 0
)
