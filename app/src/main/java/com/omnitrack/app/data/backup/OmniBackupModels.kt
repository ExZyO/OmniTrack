package com.omnitrack.app.data.backup

import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.SyncQueueEntity
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class OmniBackupManifest(
    val version: Int = 1,
    val appVersion: String,
    val versionCode: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val device: String,
    val mediaCount: Int,
    val categoryCount: Int,
    val databaseChecksumSha256: String
)

@Serializable
data class OmniDatabaseDump(
    val mediaItems: List<MediaItemEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val syncQueue: List<SyncQueueEntity> = emptyList()
)

@Serializable
data class OmniPreferencesDump(
    val entries: Map<String, String> = emptyMap()
)

@Serializable
data class OmniTrackerTokensDump(
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
    val errorMessage: String? = null
)
