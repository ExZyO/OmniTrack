package com.omnitrack.app.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Multi-Tier persistent disk cache and custom artwork storage for OmniTrack.
 * Caches high-resolution uncompressed covers into app-private storage (files/covers/)
 * and stores user-imported local art into (files/custom_art/).
 * All bitmaps are decoded and compressed as software ARGB_8888 WebP (95% quality).
 */
class CoverStore(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val coversDir: File = File(context.filesDir, "covers").apply { if (!exists()) mkdirs() }
    private val customArtDir: File = File(context.filesDir, "custom_art").apply { if (!exists()) mkdirs() }

    /**
     * Imports a user-selected local image Uri via ActivityResultContracts into files/custom_art/.
     * Decodes with allowHardware=false (ARGB_8888 software bitmap) to prevent WebP compression crashes.
     */
    suspend fun saveCustomCover(mediaId: Long, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options) ?: return@withContext null
            inputStream.close()

            val targetFile = File(customArtDir, "${mediaId}_cover.webp")
            FileOutputStream(targetFile).use { out ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 95, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 95, out)
                }
            }
            bitmap.recycle()
            AppLogger.i("CoverStore", "Saved custom cover for media $mediaId -> ${targetFile.absolutePath}")
            targetFile.absolutePath
        } catch (e: Exception) {
            AppLogger.e("CoverStore", "Failed to save custom cover for $mediaId", e)
            null
        }
    }

    /**
     * Resolves and caches a remote lossless cover URL into persistent disk storage.
     * Offline-first: returns local file URI if already cached.
     */
    suspend fun getOrCacheCover(mediaId: Long, rawUrl: String): String = withContext(Dispatchers.IO) {
        val losslessUrl = LosslessCoverMapper.toLosslessUrl(rawUrl)
        val cachedFile = File(coversDir, "${mediaId}.webp")
        if (cachedFile.exists() && cachedFile.length() > 0) {
            return@withContext cachedFile.absolutePath
        }

        try {
            val response = NetworkPacer.coalesce("cover_$mediaId") {
                NetworkPacer.withHostPacing(losslessUrl) {
                    val request = Request.Builder().url(losslessUrl).build()
                    client.newCall(request).execute()
                }
            }

            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    if (bitmap != null) {
                        FileOutputStream(cachedFile).use { out ->
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 95, out)
                            } else {
                                @Suppress("DEPRECATION")
                                bitmap.compress(Bitmap.CompressFormat.WEBP, 95, out)
                            }
                        }
                        bitmap.recycle()
                        return@withContext cachedFile.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w("CoverStore", "Failed to download cover for $mediaId from $losslessUrl: ${e.message}")
        }

        losslessUrl
    }

    fun getLocalCustomCover(mediaId: Long): File? {
        val file = File(customArtDir, "${mediaId}_cover.webp")
        return if (file.exists()) file else null
    }
}
