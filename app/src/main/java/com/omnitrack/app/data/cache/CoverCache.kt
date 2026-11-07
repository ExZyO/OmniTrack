package com.omnitrack.app.data.cache

import android.content.Context
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

    private val cacheDir: File = getStorageDir(COVERS_DIR)
    private val customCoverDir: File = getStorageDir(CUSTOM_COVERS_DIR)

    /**
     * Returns the permanent cover cache file for a given thumbnail URL.
     */
    fun getCoverFile(thumbnailUrl: String?): File? {
        if (thumbnailUrl.isNullOrBlank()) return null
        return File(cacheDir, hashKey(thumbnailUrl))
    }

    /**
     * Returns the custom cover file for a specific media item.
     */
    fun getCustomCoverFile(mediaId: Long): File {
        return File(customCoverDir, "$mediaId.jpg")
    }

    /**
     * Checks if a custom cover override exists for this media item.
     */
    fun hasCustomCover(mediaId: Long): Boolean {
        return getCustomCoverFile(mediaId).exists()
    }

    /**
     * Copies an input stream to the custom cover file for this media item.
     */
    fun setCustomCover(mediaId: Long, inputStream: InputStream): File {
        val file = getCustomCoverFile(mediaId)
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            inputStream.copyTo(out)
        }
        return file
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
        val file = getCustomCoverFile(mediaId)
        return if (file.exists()) file.delete() else false
    }

    /**
     * Prunes orphaned cover files that are no longer referenced by any item in the library.
     * Prevents unbounded storage growth over time.
     */
    fun pruneOrphans(activeCoverUrls: Set<String>, activeMediaIds: Set<Long>) {
        val activeHashes = activeCoverUrls.map { hashKey(it) }.toSet()
        // Clean covers/
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in activeHashes) {
                file.delete()
            }
        }
        // Clean covers/custom/
        val activeCustomFileNames = activeMediaIds.map { "$it.jpg" }.toSet()
        customCoverDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in activeCustomFileNames) {
                file.delete()
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
