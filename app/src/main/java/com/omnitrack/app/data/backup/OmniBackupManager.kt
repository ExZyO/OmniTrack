package com.omnitrack.app.data.backup

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.room.withTransaction
import com.omnitrack.app.BuildConfig
import com.omnitrack.app.data.cache.CoverCache
import com.omnitrack.app.data.db.OmniTrackDatabase
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.preferences.dataStore
import com.omnitrack.app.data.sync.TrackerAccountVerifier
import com.omnitrack.app.data.sync.TrackerTokenManager
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 100% Fidelity Full-App & Modular Backup + Atomic Restore Engine (.omnibk).
 * Bundles selected modules (Room database, sub-entries, DataStore preferences,
 * tracker tokens/API keys, custom artwork, cached covers) into a portable archive.
 */
object OmniBackupManager {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        encodeDefaults = true
    }

    private fun normalizeZipEntryName(rawName: String): String =
        rawName.replace('\\', '/').trimStart('/').removePrefix("./")

    private fun isSensitiveCredentialKey(key: String): Boolean {
        val k = key.lowercase(Locale.US)
        return k.startsWith("token_") ||
            k.startsWith("pkce_") ||
            k.startsWith("fanfic_cookie_") ||
            k.contains("tmdb_pending") ||
            k.contains("api_key") ||
            k.contains("client_secret") ||
            k.contains("client_id") ||
            k.endsWith("_password") ||
            k.contains("_password") ||
            k.endsWith("_username") ||
            k.contains("_username") ||
            k.endsWith("_token")
    }

    suspend fun createBackup(
        context: Context,
        database: OmniTrackDatabase,
        coverCache: CoverCache,
        outputStream: OutputStream? = null,
        options: BackupModuleOptions = BackupModuleOptions(),
        credentialsPassword: String? = null
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
            val backupsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")
            if (outputStream == null && !backupsDir.exists()) backupsDir.mkdirs()

            val localFile = if (outputStream == null) File(backupsDir, "omnitrack_backup_$timestamp.omnibk") else null
            val targetStream = outputStream ?: FileOutputStream(localFile!!)
            AppLogger.i(
                "BackupManager",
                "createBackup start: lib=${options.includeLibrary}, sub=${options.includeSubEntries}, settings=${options.includeSettings}, creds=${options.includeCredentials}, customArt=${options.includeCustomArtwork}, cachedArt=${options.includeCachedCovers}"
            )

            val mediaItems = if (options.includeLibrary) database.mediaDao().getAllMedia().first() else emptyList()
            val categories = if (options.includeLibrary) database.categoryDao().getAllCategories().first() else emptyList()
            val syncQueue = if (options.includeLibrary) database.syncQueueDao().getPendingTasksSnapshot() else emptyList()

            val vnRoutes = if (options.includeSubEntries) database.vnRouteDao().getAllRoutesOnce() else emptyList()
            val gameEntries = if (options.includeSubEntries) database.gameEntryDao().getAllEntriesOnce() else emptyList()
            val bookEntries = if (options.includeSubEntries) database.bookEntryDao().getAllEntriesOnce() else emptyList()
            val bookQuotes = if (options.includeSubEntries) database.bookQuoteDao().getAllQuotesOnce() else emptyList()
            val episodeProgress = if (options.includeSubEntries) database.episodeProgressDao().getAllProgressOnce() else emptyList()
            val comicIssues = if (options.includeSubEntries) database.comicIssueDao().getAllIssuesOnce() else emptyList()
            val fanficChapters = if (options.includeSubEntries) database.fanficChapterDao().getAllChaptersOnce() else emptyList()
            val seasonMeta = if (options.includeSubEntries) database.seasonMetaDao().getAllOnce() else emptyList()

            val includeDbDump = options.includeLibrary || options.includeSubEntries
            val dbDumpJson = if (includeDbDump) {
                json.encodeToString(
                    OmniDatabaseDump(
                        mediaItems = mediaItems,
                        categories = categories,
                        syncQueue = syncQueue,
                        vnRoutes = vnRoutes,
                        gameEntries = gameEntries,
                        bookEntries = bookEntries,
                        bookQuotes = bookQuotes,
                        episodeProgress = episodeProgress,
                        comicIssues = comicIssues,
                        fanficChapters = fanficChapters,
                        seasonMeta = seasonMeta
                    )
                )
            } else null
            val checksum = dbDumpJson?.let { sha256(it) }.orEmpty()

            val rawPrefs = if (options.includeSettings || options.includeCredentials) {
                context.dataStore.data.first().asMap()
            } else emptyMap()

            val settingsEntries = if (options.includeSettings) {
                rawPrefs.filterKeys { !isSensitiveCredentialKey(it.name) }
            } else emptyMap()

            val prefsDumpJson = if (options.includeSettings) {
                val prefsMap = settingsEntries.mapKeys { it.key.name }.mapValues { it.value.toString() }
                val typedList = settingsEntries.map { (key, value) ->
                    when (value) {
                        is Int -> OmniPrefEntry(key = key.name, type = "INT", value = value.toString())
                        is Float -> OmniPrefEntry(key = key.name, type = "FLOAT", value = value.toString())
                        is Boolean -> OmniPrefEntry(key = key.name, type = "BOOLEAN", value = value.toString())
                        is Long -> OmniPrefEntry(key = key.name, type = "LONG", value = value.toString())
                        is Set<*> -> OmniPrefEntry(
                            key = key.name,
                            type = "STRING_SET",
                            value = "",
                            stringSet = value.map { it.toString() }
                        )
                        else -> OmniPrefEntry(key = key.name, type = "STRING", value = value.toString())
                    }
                }
                json.encodeToString(OmniPreferencesDump(entries = prefsMap, typedEntries = typedList))
            } else null

            val rawCredentials = if (options.includeCredentials) {
                rawPrefs.filterKeys { isSensitiveCredentialKey(it.name) }
                    .mapKeys { it.key.name }
                    .mapValues { (key, value) ->
                        val strVal = value.toString()
                        if (TrackerTokenManager.isEncryptedTokenKey(key)) {
                            TrackerTokenManager.decrypt(strVal) ?: ""
                        } else {
                            strVal
                        }
                    }
                    .filterValues { it.isNotEmpty() }
            } else emptyMap()

            val tokensDumpJson = if (options.includeCredentials) {
                val pw = credentialsPassword?.trim().orEmpty()
                if (pw.length < 6) {
                    return@withContext BackupResult(
                        success = false,
                        errorMessage = "Enter a backup password (6+ characters) to encrypt credentials"
                    )
                }
                val plainMapJson = json.encodeToString(rawCredentials)
                val encryptedBlob = TrackerTokenManager.encryptWithPassword(plainMapJson, pw.toCharArray())
                json.encodeToString(OmniTrackerTokensDump(formatVersion = 2, encryptedPayload = encryptedBlob))
            } else null

            val customFiles = if (options.includeCustomArtwork && coverCache.customCoverDir.exists()) {
                coverCache.customCoverDir.listFiles()?.filter { it.isFile }.orEmpty()
            } else emptyList()

            val cachedFiles = if (options.includeCachedCovers && coverCache.cacheDir.exists()) {
                val allCacheFiles = coverCache.cacheDir.listFiles()?.filter { it.isFile }.orEmpty()
                if (mediaItems.isNotEmpty()) {
                    val activeHashes = mediaItems.flatMap { item ->
                        listOfNotNull(
                            coverCache.getCoverFile(item.coverUrl)?.name,
                            coverCache.getCoverFile(item.bannerUrl)?.name
                        )
                    }.toSet()
                    allCacheFiles.filter { it.name in activeHashes }
                } else {
                    allCacheFiles
                }
            } else emptyList()

            val subEntriesCount = vnRoutes.size + gameEntries.size + bookEntries.size + bookQuotes.size + fanficChapters.size
            val manifest = OmniBackupManifest(
                version = 2,
                appVersion = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                mediaCount = mediaItems.size,
                categoryCount = categories.size,
                databaseChecksumSha256 = checksum,
                episodeProgressCount = episodeProgress.size,
                comicIssueCount = comicIssues.size,
                subEntriesCount = subEntriesCount,
                settingsCount = settingsEntries.size,
                credentialsCount = rawCredentials.size,
                customArtworkCount = customFiles.size,
                cachedArtworkCount = cachedFiles.size,
                includedModules = options
            )
            val manifestJson = json.encodeToString(manifest)

            ZipOutputStream(BufferedOutputStream(targetStream)).use { zipOut ->
                zipOut.setLevel(Deflater.DEFAULT_COMPRESSION)

                writeZipEntry(zipOut, "manifest.json", manifestJson.toByteArray(StandardCharsets.UTF_8))

                if (dbDumpJson != null) {
                    writeZipEntry(zipOut, "database_dump.json", dbDumpJson.toByteArray(StandardCharsets.UTF_8))
                }
                if (prefsDumpJson != null) {
                    writeZipEntry(zipOut, "preferences.json", prefsDumpJson.toByteArray(StandardCharsets.UTF_8))
                }
                if (tokensDumpJson != null) {
                    writeZipEntry(zipOut, "tracker_tokens.json", tokensDumpJson.toByteArray(StandardCharsets.UTF_8))
                }

                zipOut.setLevel(Deflater.BEST_SPEED)

                for (coverFile in customFiles) {
                    zipOut.putNextEntry(ZipEntry("artwork/custom/${coverFile.name}"))
                    FileInputStream(coverFile).use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }

                for (cachedFile in cachedFiles) {
                    zipOut.putNextEntry(ZipEntry("artwork/cache/${cachedFile.name}"))
                    FileInputStream(cachedFile).use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }

            AppLogger.i(
                "BackupManager",
                "createBackup completed: items=${mediaItems.size}, cats=${categories.size}, customArt=${customFiles.size}, cachedArt=${cachedFiles.size}"
            )
            BackupResult(
                success = true,
                backupFile = localFile,
                mediaCount = mediaItems.size
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("BackupManager", "createBackup failed: ${e.message}", e)
            BackupResult(
                success = false,
                errorMessage = e.message ?: "Failed to generate .omnibk archive"
            )
        }
    }

    suspend fun inspectBackup(
        inputStream: InputStream,
        sourceName: String? = null,
        fileSizeBytes: Long? = null
    ): BackupInspectionResult = withContext(Dispatchers.IO) {
        try {
            var manifestBytes: ByteArray? = null
            var dbDumpBytes: ByteArray? = null
            var prefsBytes: ByteArray? = null
            var tokensBytes: ByteArray? = null
            var customCount = 0
            var cachedCount = 0
            var entryCount = 0
            var isApk = false
            val sampleEntries = mutableListOf<String>()
            var skippedArtworkScan = false

            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    entryCount++
                    val name = normalizeZipEntryName(entry.name)
                    if (sampleEntries.size < 8) {
                        sampleEntries.add(name)
                    }
                    if (name == "AndroidManifest.xml" || name == "classes.dex") {
                        isApk = true
                    }
                    if (name.startsWith("artwork/") && manifestBytes != null) {
                        skippedArtworkScan = true
                        zipIn.closeEntry()
                        break
                    }
                    when {
                        name == "manifest.json" || (manifestBytes == null && name.endsWith("/manifest.json")) ->
                            manifestBytes = zipIn.readBytes()
                        name == "database_dump.json" || (dbDumpBytes == null && name.endsWith("/database_dump.json")) ->
                            dbDumpBytes = zipIn.readBytes()
                        name == "preferences.json" || (prefsBytes == null && name.endsWith("/preferences.json")) ->
                            prefsBytes = zipIn.readBytes()
                        name == "tracker_tokens.json" || (tokensBytes == null && name.endsWith("/tracker_tokens.json")) ->
                            tokensBytes = zipIn.readBytes()
                        name.startsWith("artwork/custom/") && !entry.isDirectory -> customCount++
                        name.startsWith("artwork/cache/") && !entry.isDirectory -> cachedCount++
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            if (manifestBytes == null) {
                AppLogger.w(
                    "BackupManager",
                    "inspectBackup missing manifest.json: file=$sourceName size=$fileSizeBytes entries=$entryCount isApk=$isApk sample=$sampleEntries"
                )
                val errorMsg = when {
                    isApk -> "Selected file '${sourceName ?: "archive"}' is an Android APK, not an .omnibk backup. Pick an omnitrack_backup_*.omnibk file."
                    entryCount == 0 -> "Selected file '${sourceName ?: "archive"}'${fileSizeBytes?.let { " ($it bytes)" }.orEmpty()} is empty or not a valid .omnibk ZIP archive."
                    else -> "Invalid .omnibk archive ('${sourceName ?: "archive"}'): missing manifest.json ($entryCount entries found)"
                }
                return@withContext BackupInspectionResult(
                    valid = false,
                    errorMessage = errorMsg
                )
            }

            val manifest = json.decodeFromString<OmniBackupManifest>(String(manifestBytes!!, StandardCharsets.UTF_8))
            val dump = if (dbDumpBytes != null) {
                runCatching {
                    json.decodeFromString<OmniDatabaseDump>(String(dbDumpBytes!!, StandardCharsets.UTF_8))
                }.getOrNull()
            } else null

            val prefsDump = if (prefsBytes != null) {
                runCatching {
                    json.decodeFromString<OmniPreferencesDump>(String(prefsBytes!!, StandardCharsets.UTF_8))
                }.getOrNull()
            } else null

            val tokensDump = if (tokensBytes != null) {
                runCatching {
                    json.decodeFromString<OmniTrackerTokensDump>(String(tokensBytes!!, StandardCharsets.UTF_8))
                }.getOrNull()
            } else null

            val mediaCount = dump?.mediaItems?.size ?: manifest.mediaCount
            val categoryCount = dump?.categories?.size ?: manifest.categoryCount
            val episodeCount = dump?.episodeProgress?.size ?: manifest.episodeProgressCount
            val comicCount = dump?.comicIssues?.size ?: manifest.comicIssueCount
            val subCount = dump?.let {
                it.vnRoutes.size + it.gameEntries.size + it.bookEntries.size + it.bookQuotes.size + it.fanficChapters.size
            } ?: manifest.subEntriesCount

            val nonSensitivePrefCount = prefsDump?.let { p ->
                val fromTyped = p.typedEntries.count { !isSensitiveCredentialKey(it.key) }
                if (fromTyped > 0) fromTyped else p.entries.keys.count { !isSensitiveCredentialKey(it) }
            } ?: manifest.settingsCount

            val legacyCredCount = prefsDump?.let { p ->
                val fromTyped = p.typedEntries.count { isSensitiveCredentialKey(it.key) }
                if (fromTyped > 0) fromTyped else p.entries.keys.count { isSensitiveCredentialKey(it) }
            } ?: 0
            val requiresPw = tokensDump?.formatVersion == 2 && !tokensDump.encryptedPayload.isNullOrBlank()
            val totalCredCount = (tokensDump?.encryptedTokens?.size ?: 0) + legacyCredCount

            val resolvedCustomCount = if (skippedArtworkScan) {
                manifest.customArtworkCount
            } else {
                maxOf(customCount, manifest.customArtworkCount.takeIf { customCount > 0 } ?: 0)
            }
            val resolvedCachedCount = if (skippedArtworkScan) {
                manifest.cachedArtworkCount
            } else {
                maxOf(cachedCount, manifest.cachedArtworkCount.takeIf { cachedCount > 0 } ?: 0)
            }

            AppLogger.i(
                "BackupManager",
                "inspectBackup valid: file=$sourceName v=${manifest.version} app=${manifest.appVersion} items=$mediaCount fastScan=$skippedArtworkScan"
            )

            BackupInspectionResult(
                valid = true,
                manifestVersion = manifest.version,
                appVersion = manifest.appVersion,
                device = manifest.device,
                createdAt = manifest.createdAt,
                mediaCount = mediaCount,
                categoryCount = categoryCount,
                episodeProgressCount = episodeCount,
                comicIssueCount = comicCount,
                subEntriesCount = subCount,
                hasDatabase = dbDumpBytes != null,
                hasSettings = prefsBytes != null,
                settingsCount = nonSensitivePrefCount,
                hasCredentials = tokensBytes != null || legacyCredCount > 0,
                credentialsCount = if (requiresPw) manifest.credentialsCount.coerceAtLeast(1) else maxOf(totalCredCount, manifest.credentialsCount.takeIf { tokensBytes != null } ?: 0),
                credentialsRequirePassword = requiresPw,
                customArtworkCount = resolvedCustomCount,
                cachedArtworkCount = resolvedCachedCount
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("BackupManager", "inspectBackup failed for file=$sourceName: ${e.message}", e)
            BackupInspectionResult(
                valid = false,
                errorMessage = e.message ?: "Unable to read .omnibk archive"
            )
        }
    }

    suspend fun restoreBackup(
        context: Context,
        database: OmniTrackDatabase,
        coverCache: CoverCache,
        inputStream: InputStream,
        options: BackupModuleOptions = BackupModuleOptions(),
        credentialsPassword: String? = null
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(
                "BackupManager",
                "restoreBackup start: lib=${options.includeLibrary}, sub=${options.includeSubEntries}, settings=${options.includeSettings}, creds=${options.includeCredentials}, customArt=${options.includeCustomArtwork}, cachedArt=${options.includeCachedCovers}"
            )
            var manifestBytes: ByteArray? = null
            var dbDumpBytes: ByteArray? = null
            var prefsBytes: ByteArray? = null
            var tokensBytes: ByteArray? = null
            val customArtworks = mutableMapOf<String, ByteArray>()
            val cacheArtworks = mutableMapOf<String, ByteArray>()

            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    val name = normalizeZipEntryName(entry.name)
                    when {
                        name == "manifest.json" || (manifestBytes == null && name.endsWith("/manifest.json")) ->
                            manifestBytes = zipIn.readBytes()
                        name == "database_dump.json" || (dbDumpBytes == null && name.endsWith("/database_dump.json")) ->
                            dbDumpBytes = zipIn.readBytes()
                        name == "preferences.json" || (prefsBytes == null && name.endsWith("/preferences.json")) ->
                            prefsBytes = zipIn.readBytes()
                        name == "tracker_tokens.json" || (tokensBytes == null && name.endsWith("/tracker_tokens.json")) ->
                            tokensBytes = zipIn.readBytes()
                        name.contains("artwork/custom/") && !entry.isDirectory -> {
                            val fileName = File(name.substringAfter("artwork/custom/")).name
                            if (fileName.isNotBlank()) {
                                customArtworks[fileName] = zipIn.readBytes()
                            }
                        }
                        name.contains("artwork/cache/") && !entry.isDirectory -> {
                            val fileName = File(name.substringAfter("artwork/cache/")).name
                            if (fileName.isNotBlank()) {
                                cacheArtworks[fileName] = zipIn.readBytes()
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            if (manifestBytes == null) {
                AppLogger.w("BackupManager", "restoreBackup missing manifest.json")
                return@withContext RestoreResult(
                    success = false,
                    errorMessage = "Invalid .omnibk backup file: manifest.json missing"
                )
            }

            val manifest = json.decodeFromString<OmniBackupManifest>(String(manifestBytes!!, StandardCharsets.UTF_8))

            var dump: OmniDatabaseDump? = null
            if (dbDumpBytes != null && (options.includeLibrary || options.includeSubEntries)) {
                val dbDumpStr = String(dbDumpBytes!!, StandardCharsets.UTF_8)
                val calculatedChecksum = sha256(dbDumpStr)
                if (manifest.databaseChecksumSha256.isNotBlank() && manifest.databaseChecksumSha256 != calculatedChecksum) {
                    return@withContext RestoreResult(
                        success = false,
                        errorMessage = "Integrity check failed: database checksum does not match manifest"
                    )
                }
                dump = json.decodeFromString<OmniDatabaseDump>(dbDumpStr)
            }

            var restoredMediaCount = 0
            var restoredCategoryCount = 0
            var restoredEpisodeCount = 0
            var restoredComicCount = 0

            if (dump != null && (options.includeLibrary || options.includeSubEntries)) {
                database.withTransaction {
                    if (options.includeLibrary) {
                        database.mediaDao().deleteAllMedia()
                        database.categoryDao().deleteAllCategories()
                        database.syncQueueDao().clearQueue()

                        if (dump.categories.isNotEmpty()) {
                            database.categoryDao().insertAll(dump.categories)
                            restoredCategoryCount = dump.categories.size
                        }
                        if (dump.mediaItems.isNotEmpty()) {
                            database.mediaDao().insertAll(dump.mediaItems)
                            restoredMediaCount = dump.mediaItems.size
                        }
                        for (task in dump.syncQueue) {
                            database.syncQueueDao().enqueue(task)
                        }
                    }

                    if (options.includeSubEntries) {
                        database.episodeProgressDao().deleteAllProgress()
                        database.comicIssueDao().deleteAllIssues()
                        database.fanficChapterDao().deleteAllChapters()
                        database.vnRouteDao().deleteAllRoutes()
                        database.gameEntryDao().deleteAllEntries()
                        database.bookEntryDao().deleteAllEntries()
                        database.bookQuoteDao().deleteAllQuotes()
                        database.seasonMetaDao().deleteAll()

                        if (dump.episodeProgress.isNotEmpty()) {
                            database.episodeProgressDao().insertOrUpdateAll(dump.episodeProgress)
                            restoredEpisodeCount = dump.episodeProgress.size
                        }
                        if (dump.comicIssues.isNotEmpty()) {
                            database.comicIssueDao().insertIssues(dump.comicIssues)
                            restoredComicCount = dump.comicIssues.size
                        }
                        if (dump.fanficChapters.isNotEmpty()) {
                            database.fanficChapterDao().insertChapters(dump.fanficChapters)
                        }
                        if (dump.vnRoutes.isNotEmpty()) {
                            database.vnRouteDao().insertRoutes(dump.vnRoutes)
                        }
                        if (dump.gameEntries.isNotEmpty()) {
                            database.gameEntryDao().insertEntries(dump.gameEntries)
                        }
                        if (dump.bookEntries.isNotEmpty()) {
                            database.bookEntryDao().insertEntries(dump.bookEntries)
                        }
                        if (dump.bookQuotes.isNotEmpty()) {
                            database.bookQuoteDao().insertQuotes(dump.bookQuotes)
                        }
                        if (dump.seasonMeta.isNotEmpty()) {
                            database.seasonMetaDao().upsertAll(dump.seasonMeta)
                        }
                    }
                }
            }

            var restoredArtworkCount = 0
            if (options.includeCustomArtwork && customArtworks.isNotEmpty()) {
                val customDir = coverCache.customCoverDir
                if (!customDir.exists()) customDir.mkdirs()
                for ((fileName, bytes) in customArtworks) {
                    File(customDir, fileName).writeBytes(bytes)
                    restoredArtworkCount++
                }
            }

            if (options.includeCachedCovers && cacheArtworks.isNotEmpty()) {
                val cacheDir = coverCache.cacheDir
                if (!cacheDir.exists()) cacheDir.mkdirs()
                for ((fileName, bytes) in cacheArtworks) {
                    File(cacheDir, fileName).writeBytes(bytes)
                    restoredArtworkCount++
                }
            }

            val prefsDump = if (prefsBytes != null) {
                runCatching {
                    json.decodeFromString<OmniPreferencesDump>(String(prefsBytes!!, StandardCharsets.UTF_8))
                }.getOrNull()
            } else null

            val tokensDump = if (tokensBytes != null) {
                runCatching {
                    json.decodeFromString<OmniTrackerTokensDump>(String(tokensBytes!!, StandardCharsets.UTF_8))
                }.getOrNull()
            } else null

            var restoredSettings = false
            var restoredCredentials = false

            if ((options.includeSettings && prefsDump != null) || (options.includeCredentials && (tokensDump != null || prefsDump != null))) {
                val knownBooleanKeys = setOf(
                    "ai_semantic_search_enabled",
                    "poster_card_landscape",
                    "poster_card_hide_labels",
                    "poster_always_landscape_clearlogo",
                    "poster_card_depth_enabled",
                    "poster_card_depth_posters",
                    "poster_card_depth_continue_watching",
                    "poster_card_depth_episodes",
                    "poster_card_depth_cast",
                    "poster_card_depth_trailers"
                )
                val knownIntKeys = setOf(
                    "poster_card_width_dp",
                    "poster_card_corner_radius_dp",
                    "poster_card_depth_edge_strength",
                    "poster_card_depth_sheen_strength",
                    "poster_card_depth_edge_coverage",
                    "vn_spoiler_level"
                )
                val knownFloatKeys = setOf("episode_layout_scale")

                context.dataStore.edit { prefs ->
                    if (options.includeSettings && prefsDump != null) {
                        prefs.asMap().keys.toList().forEach { k ->
                            if (!isSensitiveCredentialKey(k.name)) {
                                prefs.remove(k)
                            }
                        }
                        if (prefsDump.typedEntries.isNotEmpty()) {
                            for (entry in prefsDump.typedEntries) {
                                val key = entry.key
                                if (isSensitiveCredentialKey(key)) continue
                                when {
                                    entry.type == "STRING_SET" -> {
                                        prefs[stringSetPreferencesKey(key)] = (entry.stringSet ?: emptyList()).toSet()
                                    }
                                    key in knownBooleanKeys || entry.type == "BOOLEAN" -> {
                                        prefs[booleanPreferencesKey(key)] = entry.value.toBoolean()
                                    }
                                    key in knownIntKeys || entry.type == "INT" -> {
                                        entry.value.toIntOrNull()?.let { prefs[intPreferencesKey(key)] = it }
                                    }
                                    key in knownFloatKeys || entry.type == "FLOAT" -> {
                                        entry.value.toFloatOrNull()?.let { prefs[floatPreferencesKey(key)] = it }
                                    }
                                    entry.type == "LONG" -> {
                                        entry.value.toLongOrNull()?.let { prefs[longPreferencesKey(key)] = it }
                                    }
                                    else -> {
                                        prefs[stringPreferencesKey(key)] = entry.value
                                    }
                                }
                            }
                        } else {
                            for ((key, value) in prefsDump.entries) {
                                if (isSensitiveCredentialKey(key)) continue
                                when {
                                    key in knownBooleanKeys || value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true) -> {
                                        prefs[booleanPreferencesKey(key)] = value.toBoolean()
                                    }
                                    key in knownIntKeys || key.endsWith("_dp") || key.endsWith("_strength") || key.endsWith("_coverage") -> {
                                        value.toIntOrNull()?.let { prefs[intPreferencesKey(key)] = it }
                                            ?: run { prefs[stringPreferencesKey(key)] = value }
                                    }
                                    key in knownFloatKeys || key.endsWith("_scale") -> {
                                        value.toFloatOrNull()?.let { prefs[floatPreferencesKey(key)] = it }
                                            ?: run { prefs[stringPreferencesKey(key)] = value }
                                    }
                                    else -> {
                                        prefs[stringPreferencesKey(key)] = value
                                    }
                                }
                            }
                        }
                        restoredSettings = true
                    }

                    if (options.includeCredentials) {
                        val legacyTyped = prefsDump?.typedEntries?.filter { isSensitiveCredentialKey(it.key) }.orEmpty()
                        val legacyMap = prefsDump?.entries?.filterKeys { isSensitiveCredentialKey(it) }.orEmpty()
                        val v2Payload = tokensDump?.takeIf { it.formatVersion == 2 }?.encryptedPayload

                        val decryptedV2Map: Map<String, String>? = if (!v2Payload.isNullOrBlank()) {
                            val pw = credentialsPassword?.trim().orEmpty()
                            if (pw.isBlank()) {
                                throw IllegalArgumentException("Enter the backup password to decrypt credentials")
                            }
                            val decryptedJson = TrackerTokenManager.decryptWithPassword(v2Payload, pw.toCharArray())
                                ?: throw IllegalArgumentException("Incorrect backup password — could not decrypt credentials")
                            json.decodeFromString<Map<String, String>>(decryptedJson)
                        } else null

                        val tokenEntries = tokensDump?.encryptedTokens.orEmpty()
                        if (decryptedV2Map != null || tokenEntries.isNotEmpty() || legacyTyped.isNotEmpty() || legacyMap.isNotEmpty()) {
                            prefs.asMap().keys.toList().forEach { k ->
                                if (isSensitiveCredentialKey(k.name)) {
                                    prefs.remove(k)
                                }
                            }
                            fun storeRestoredCredential(k: String, rawVal: String, isAlreadyPlain: Boolean) {
                                val finalVal = if (TrackerTokenManager.isEncryptedTokenKey(k)) {
                                    val plain = if (isAlreadyPlain) rawVal else TrackerTokenManager.decrypt(rawVal)
                                    if (!plain.isNullOrBlank()) TrackerTokenManager.encrypt(plain) else null
                                } else rawVal
                                if (!finalVal.isNullOrBlank()) {
                                    prefs[stringPreferencesKey(k)] = finalVal
                                }
                            }
                            for ((key, value) in legacyMap) {
                                storeRestoredCredential(key, value, isAlreadyPlain = false)
                            }
                            for (entry in legacyTyped) {
                                storeRestoredCredential(entry.key, entry.value, isAlreadyPlain = false)
                            }
                            for ((key, value) in tokenEntries) {
                                storeRestoredCredential(key, value, isAlreadyPlain = false)
                            }
                            decryptedV2Map?.forEach { (key, plainVal) ->
                                storeRestoredCredential(key, plainVal, isAlreadyPlain = true)
                            }
                            restoredCredentials = true
                        }
                    }
                }

                rehydrateCaches(context)
            }

            AppLogger.i(
                "BackupManager",
                "restoreBackup completed: items=$restoredMediaCount cats=$restoredCategoryCount eps=$restoredEpisodeCount comics=$restoredComicCount settings=$restoredSettings creds=$restoredCredentials art=$restoredArtworkCount"
            )
            RestoreResult(
                success = true,
                mediaCount = restoredMediaCount,
                categoryCount = restoredCategoryCount,
                episodeProgressCount = restoredEpisodeCount,
                comicIssueCount = restoredComicCount,
                restoredSettings = restoredSettings,
                restoredCredentials = restoredCredentials,
                restoredArtworkCount = restoredArtworkCount
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("BackupManager", "restoreBackup failed: ${e.message}", e)
            RestoreResult(
                success = false,
                errorMessage = e.message ?: "Failed to restore .omnibk backup"
            )
        }
    }

    private suspend fun rehydrateCaches(context: Context) {
        val pm = PreferenceManager(context)
        PreferenceManager.cachedPreferredTitleLanguage = pm.preferredTitleLanguage.first()
        PreferenceManager.cachedVnSpoilerLevel = pm.vnSpoilerLevel.first()
        PreferenceManager.cachedVnNsfwFilterMode = pm.vnNsfwFilterMode.first()
        PreferenceManager.cachedTmdbApiKey = pm.tmdbApiKey.first()
        PreferenceManager.cachedSteamGridDbApiKey = pm.steamGridDbApiKey.first()
        PreferenceManager.cachedIgdbClientId = pm.igdbClientId.first()
        PreferenceManager.cachedIgdbClientSecret = pm.igdbClientSecret.first()
        PreferenceManager.cachedComicVineApiKey = pm.comicVineApiKey.first()
        PreferenceManager.cachedMetronUsername = pm.metronUsername.first()
        PreferenceManager.cachedMetronPassword = pm.metronPassword.first()

        TrackerAccountVerifier.customTraktClientId = pm.customTraktClientId.first()
        TrackerAccountVerifier.customTraktClientSecret = pm.customTraktClientSecret.first()
        TrackerAccountVerifier.customMalClientId = pm.customMalClientId.first()
        TrackerAccountVerifier.customSimklClientId = pm.customSimklClientId.first()
        TrackerAccountVerifier.customSimklClientSecret = pm.customSimklClientSecret.first()

        TrackerTokenManager.cachedHardcoverToken =
            TrackerTokenManager(context).tokens.first().hardcoverToken.orEmpty().trim()
    }

    private fun writeZipEntry(zipOut: ZipOutputStream, name: String, data: ByteArray) {
        val entry = ZipEntry(name)
        zipOut.putNextEntry(entry)
        zipOut.write(data)
        zipOut.closeEntry()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
