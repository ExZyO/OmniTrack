package com.omnitrack.app.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AppUpdateInstaller {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Downloads APK file and triggers native package installer.
     * @param onProgress Callback with progress from 0.0f to 1.0f
     */
    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            AppLogger.i("UpdateInstaller", "Starting download from: $apkUrl")

            val request = Request.Builder()
                .url(apkUrl)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed with HTTP ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength()

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "OmniTrack-update.apk")
            if (apkFile.exists()) apkFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloadedBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                            onProgress(progress)
                            AppUpdateNotifier.showDownloadProgress(context, progress)
                        }
                    }
                    output.flush()
                }
            }

            AppLogger.i("UpdateInstaller", "APK downloaded successfully (${apkFile.length()} bytes)")
            onProgress(1.0f)
            AppUpdateNotifier.showInstallPrompt(context, apkFile)

            // Trigger system installer on main thread
            withContext(Dispatchers.Main) {
                launchPackageInstaller(context, apkFile)
            }
        }
    }

    private fun launchPackageInstaller(context: Context, apkFile: File) {
        val authority = "${context.packageName}.provider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

        AppLogger.i("UpdateInstaller", "Launching package installer with URI: $apkUri")

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
