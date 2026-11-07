package com.omnitrack.app.data.cache

import android.content.Context
import coil3.disk.DiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Dedicated two-tiered cover cache engine inspired by reference manga/media trackers.
 * Stores library covers permanently in the application's files directory,
 * isolated from ephemeral HTTP / LRU cache eviction so that library artwork is 100% offline-first.
 */
class CoverCache(private val context: Context) {

    companion object {
        private const val COVERS_DIR = "covers"
        private const val CUSTOM_COVERS_DIR = "covers/custom"
    }

    val cacheDir: File = getStorageDir(COVERS_DIR)
    val customCoverDir: File = getStorageDir(CUSTOM_COVERS_DIR)

    /**
     * Returns the permanent cover cache file for a given thumbnail URL.
     */
    fun getCoverFile(thumbnailUrl: String?): File? {
        if (thumbnailUrl.isNullOrBlank()) return null
        return File(cacheDir, hashKey(thumbnailUrl))
    }

    /**
     * Returns the custom cover file for a specific media item.
     * Looks for timestamped files first (${mediaId}_*.jpg).
     */
    fun getCustomCoverFile(mediaId: Long): File {
        val matching = customCoverDir.listFiles()?.filter {
            it.isFile && (it.name.startsWith("${mediaId}_") || it.name == "$mediaId.jpg")
        }?.maxByOrNull { it.lastModified() }

        return matching ?: File(customCoverDir, "${mediaId}_${System.currentTimeMillis()}.jpg")
    }

    /**
     * Checks if a custom cover override exists for this media item.
     */
    fun hasCustomCover(mediaId: Long): Boolean {
        return customCoverDir.listFiles()?.any {
            it.isFile && (it.name.startsWith("${mediaId}_") || it.name == "$mediaId.jpg")
        } == true
    }

    /**
     * Imports a user-selected local image Uri via ActivityResultContracts into covers/custom/.
     * Decodes with allowHardware=false (ARGB_8888 software bitmap) to prevent compression crashes.
     * Cleans up previous versions and saves with a fresh timestamp for zero-syscall cache busting.
     */
    suspend fun saveCustomCoverUri(mediaId: Long, uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val options = android.graphics.BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream, null, options) ?: return@withContext null
            inputStream.close()

            // Clean up previous custom cover versions
            customCoverDir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.startsWith("${mediaId}_") || file.name == "$mediaId.jpg")) {
                    file.delete()
                }
            }

            val timestampedFile = File(customCoverDir, "${mediaId}_cover_${System.currentTimeMillis()}.webp")
            timestampedFile.parentFile?.mkdirs()
            java.io.FileOutputStream(timestampedFile).use { out ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 95, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 95, out)
                }
            }
            bitmap.recycle()
            com.omnitrack.app.util.AppLogger.i("CoverCache", "Saved custom cover for media $mediaId -> ${timestampedFile.absolutePath}")
            timestampedFile.absolutePath
        } catch (e: Exception) {
            com.omnitrack.app.util.AppLogger.e("CoverCache", "Failed to save custom cover for $mediaId", e)
            null
        }
    }

    /**
     * Copies an input stream to a new timestamped custom cover file (${mediaId}_${timestamp}.jpg).
     * Deletes previous custom cover files for this media item so old files don't accumulate.
     */
    fun setCustomCover(mediaId: Long, inputStream: InputStream): File {
        // Clean up previous custom cover versions for this item
        customCoverDir.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.startsWith("${mediaId}_") || file.name == "$mediaId.jpg")) {
                file.delete()
            }
        }

        // Save with fresh timestamp in filename for zero-cost cache busting
        val timestampedFile = File(customCoverDir, "${mediaId}_${System.currentTimeMillis()}.jpg")
        timestampedFile.parentFile?.mkdirs()
        timestampedFile.outputStream().use { out ->
            inputStream.copyTo(out)
        }
        return timestampedFile
    }

    /**
     * Promotes an image from Coil's DiskCache snapshot into permanent CoverCache.
     */
    fun promoteFromDiskCache(thumbnailUrl: String, diskCache: DiskCache?): File? {
        if (diskCache == null || thumbnailUrl.isBlank()) return null
        val targetFile = getCoverFile(thumbnailUrl) ?: return null
        if (targetFile.exists() && targetFile.length() > 0) return targetFile
        val snapshot = diskCache.openSnapshot(thumbnailUrl) ?: return null
        return try {
            snapshot.use { snap ->
                val sourceFile = snap.data.toFile()
                if (sourceFile.exists() && sourceFile.length() > 0) {
                    targetFile.parentFile?.mkdirs()
                    sourceFile.inputStream().use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    targetFile
                } else null
            }
        } catch (_: Exception) {
            targetFile.delete()
            null
        }
    }

    /**
     * Promotes an image from Coil's DiskCache snapshot into permanent CoverCache.
     */
    fun promoteFromDiskCache(thumbnailUrl: String, snapshotFile: File): File? {
        val targetFile = getCoverFile(thumbnailUrl) ?: return null
        if (targetFile.exists() && targetFile.length() > 0) return targetFile
        return try {
            targetFile.parentFile?.mkdirs()
            snapshotFile.inputStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } catch (_: Exception) {
            targetFile.delete()
            null
        }
    }

    /**
     * Saves raw image bytes for a thumbnail URL into the permanent cache directory.
     */
    fun saveCover(thumbnailUrl: String, inputStream: InputStream): File? {
        val file = getCoverFile(thumbnailUrl) ?: return null
        return try {
            file.parentFile?.mkdirs()
            file.outputStream().use { out ->
                inputStream.copyTo(out)
            }
            file
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    /**
     * Deletes a cached cover from disk.
     */
    fun deleteCover(thumbnailUrl: String?): Boolean {
        return getCoverFile(thumbnailUrl)?.let { if (it.exists()) it.delete() else false } ?: false
    }

    /**
     * Deletes a custom cover from disk.
     */
    fun deleteCustomCover(mediaId: Long): Boolean {
        var deleted = false
        customCoverDir.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.startsWith("${mediaId}_") || file.name == "$mediaId.jpg")) {
                if (file.delete()) deleted = true
            }
        }
        return deleted
    }

    /**
     * Prunes orphaned cover files that are no longer referenced by any item in the library.
     * Prevents unbounded storage growth over time. Runs strictly on Dispatchers.IO.
     */
    suspend fun pruneOrphans(activeCoverUrls: Set<String>, activeMediaIds: Set<Long>) = withContext(Dispatchers.IO) {
        val activeHashes = activeCoverUrls.map { hashKey(it) }.toSet()
        // Clean covers/
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in activeHashes) {
                file.delete()
            }
        }
        // Clean covers/custom/
        customCoverDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val idStr = file.name.substringBefore("_").substringBefore(".jpg")
                val id = idStr.toLongOrNull()
                if (id == null || id !in activeMediaIds) {
                    file.delete()
                }
            }
        }
    }

    /**
     * Calculates total disk space occupied by permanent covers.
     */
    fun getTotalCacheSizeBytes(): Long {
        return getDirectorySize(cacheDir)
    }

    private fun getStorageDir(dir: String): File {
        return context.getExternalFilesDir(dir)
            ?: File(context.filesDir, dir).also { it.mkdirs() }
    }

    private fun getDirectorySize(dir: File): Long {
        var size: Long = 0
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirectorySize(file) else file.length()
        }
        return size
    }

    private fun hashKey(key: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(key.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
